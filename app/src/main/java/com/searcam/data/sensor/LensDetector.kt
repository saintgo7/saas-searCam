package com.searcam.data.sensor

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.searcam.data.analysis.RetroreflectionAnalyzer
import com.searcam.domain.model.RetroreflectionPoint
import com.searcam.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

/**
 * CameraX 후면 카메라 + 플래시를 이용한 Retroreflection 렌즈 감지기
 *
 * 플래시를 주기적으로 ON/OFF하여 역반사 패턴을 분석한다.
 * RetroreflectionAnalyzer에 프레임을 전달하고,
 * 결과를 StateFlow<List<RetroreflectionPoint>>로 방출한다.
 *
 * 보안 원칙:
 *   - 카메라 프레임 데이터는 메모리에서만 처리하며 파일 저장 금지
 *   - 분석 완료 후 ImageProxy는 즉시 close()
 *
 * 에러 코드:
 *   E1003 — 카메라 초기화 실패
 *   E1004 — 플래시(토치) 미지원 기기
 *   E3002 — 카메라 권한 미승인
 */
class LensDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cameraProvider: ProcessCameraProvider,
    private val analyzer: RetroreflectionAnalyzer,
) {

    // ─────────────────────────────────────────────────────────
    // 상태
    // ─────────────────────────────────────────────────────────

    private val _detectionPoints = MutableStateFlow<List<RetroreflectionPoint>>(emptyList())

    /** Retroreflection 포인트 목록 StateFlow — UI 레이어에서 구독 */
    val detectionPoints: StateFlow<List<RetroreflectionPoint>> = _detectionPoints.asStateFlow()

    /** 현재 플래시 상태 */
    private var isFlashOn: Boolean = false

    /** CameraX Camera 객체 (플래시 제어용) */
    private var camera: Camera? = null

    /** 이미지 분석용 백그라운드 스레드 */
    private var analysisExecutor: ExecutorService? = null

    /** 플래시 토글 + 분석 코루틴 스코프 */
    private var detectionScope: CoroutineScope? = null

    /** 감지 진행 여부 */
    private var isDetecting: Boolean = false

    // ─────────────────────────────────────────────────────────
    // 공개 API
    // ─────────────────────────────────────────────────────────

    /**
     * Retroreflection 렌즈 감지를 시작한다.
     *
     * 후면 카메라를 바인딩하고 플래시 ON/OFF 사이클과 함께 분석을 시작한다.
     * 반드시 LifecycleOwner(Activity/Fragment)를 전달해야 한다.
     *
     * @param lifecycleOwner CameraX 생명주기 관리용 소유자
     * @return 시작 성공 시 Result.success(Unit), 실패 시 Result.failure
     */
    fun startDetection(lifecycleOwner: LifecycleOwner): Result<Unit> {
        if (isDetecting) {
            Timber.d("렌즈 감지가 이미 실행 중입니다.")
            return Result.success(Unit)
        }

        return try {
            analyzer.reset()
            analysisExecutor = Executors.newSingleThreadExecutor()

            val imageAnalysis = buildImageAnalysis()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                imageAnalysis,
            )

            // 플래시 지원 여부 확인
            val hasFlash = camera?.cameraInfo?.hasFlashUnit() ?: false
            if (!hasFlash) {
                Timber.w("[${Constants.ErrorCode.E1004}] 이 기기는 플래시를 지원하지 않습니다.")
            }

            isDetecting = true
            startFlashCycle()

            Timber.d("렌즈 감지 시작 — 후면 카메라 바인딩 완료 (플래시: $hasFlash)")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[${Constants.ErrorCode.E1003}] 카메라 초기화 실패")
            Result.failure(e)
        }
    }

    /**
     * Retroreflection 렌즈 감지를 중단한다.
     *
     * 카메라 바인딩을 해제하고 플래시를 끈다.
     * 안정성 추적 상태도 초기화한다.
     *
     * @return 항상 Result.success(Unit) 반환
     */
    fun stopDetection(): Result<Unit> {
        return try {
            isDetecting = false

            // 플래시 끄기
            camera?.cameraControl?.enableTorch(false)
            isFlashOn = false

            // 코루틴 취소
            detectionScope?.cancel()
            detectionScope = null

            // 분석 스레드 종료
            analysisExecutor?.shutdown()
            analysisExecutor = null

            // 카메라 바인딩 해제
            cameraProvider.unbindAll()
            camera = null

            // 상태 초기화
            analyzer.reset()
            _detectionPoints.value = emptyList()

            Timber.d("렌즈 감지 중단 — 카메라 및 플래시 해제 완료")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "렌즈 감지 중단 중 오류 발생")
            Result.failure(e)
        }
    }

    // ─────────────────────────────────────────────────────────
    // 내부 구현
    // ─────────────────────────────────────────────────────────

    /**
     * CameraX ImageAnalysis 유스케이스를 생성한다.
     *
     * STRATEGY_KEEP_ONLY_LATEST로 최신 프레임만 분석한다.
     * 목표 해상도는 Constants의 CAMERA_ANALYSIS_WIDTH × HEIGHT.
     */
    private fun buildImageAnalysis(): ImageAnalysis {
        val executor = analysisExecutor
            ?: throw IllegalStateException("분석 스레드가 초기화되지 않았습니다.")

        return ImageAnalysis.Builder()
            .setTargetResolution(
                android.util.Size(
                    Constants.CAMERA_ANALYSIS_WIDTH,
                    Constants.CAMERA_ANALYSIS_HEIGHT,
                )
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(executor, FrameAnalyzer())
            }
    }

    /**
     * 플래시 ON/OFF 사이클을 주기적으로 실행한다.
     *
     * ON 지속: 2초, OFF 지속: 0.2초 (플래시 의존성 검사용 짧은 암전)
     * 플래시 상태는 FrameAnalyzer에 전달되어 역반사 판정에 사용된다.
     */
    private fun startFlashCycle() {
        detectionScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        detectionScope?.launch {
            while (isDetecting) {
                try {
                    // 플래시 ON
                    isFlashOn = true
                    camera?.cameraControl?.enableTorch(true)
                    delay(FLASH_ON_DURATION_MS)

                    if (!isDetecting) break

                    // 플래시 OFF (역반사 의존성 측정용)
                    isFlashOn = false
                    camera?.cameraControl?.enableTorch(false)
                    delay(FLASH_OFF_DURATION_MS)
                } catch (e: Exception) {
                    Timber.e(e, "플래시 사이클 중 오류 발생")
                    break
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // Inner Analyzer
    // ─────────────────────────────────────────────────────────

    /**
     * CameraX ImageAnalysis.Analyzer 구현체
     *
     * 각 프레임을 RetroreflectionAnalyzer에 전달하고 결과를 StateFlow로 방출한다.
     * 프레임 처리 후 반드시 ImageProxy.close()를 호출하여 메모리 누수를 방지한다.
     */
    private inner class FrameAnalyzer : ImageAnalysis.Analyzer {

        override fun analyze(image: ImageProxy) {
            // 보안 원칙: 프레임은 메모리에서만 처리, 파일 저장 금지
            try {
                if (!isDetecting) {
                    image.close()
                    return
                }

                val points = analyzer.analyze(image, isFlashOn)
                _detectionPoints.value = points

                if (points.isNotEmpty()) {
                    Timber.d("렌즈 후보 ${points.size}개 감지 (최고 위험도: ${points.maxOf { it.riskScore }})")
                }
            } catch (e: Exception) {
                Timber.e(e, "[${Constants.ErrorCode.E1003}] 프레임 분석 중 오류 발생")
            } finally {
                // 프레임 처리 완료 후 즉시 해제 (메모리 누수 방지)
                image.close()
            }
        }
    }

    companion object {
        /** 플래시 ON 지속 시간 (ms) */
        private const val FLASH_ON_DURATION_MS = 2_000L

        /** 플래시 OFF 지속 시간 (ms) — 역반사 의존성 측정용 암전 */
        private const val FLASH_OFF_DURATION_MS = 200L
    }
}
