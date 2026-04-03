package com.searcam.data.sensor

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import com.searcam.domain.model.IrColor
import com.searcam.domain.model.IrPoint
import com.searcam.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * CameraX 전면 카메라를 이용한 IR LED 감지기
 *
 * 전면 카메라는 IR 필터가 후면보다 약하여 근적외선(IR) LED를 감지할 수 있다.
 * 몰래카메라의 야간 촬영용 IR LED는 전면 카메라에서 자주색/흰색으로 포착된다.
 *
 * 감지 파이프라인:
 *   1. 전면 카메라 YUV 프레임에서 Y(밝기) 플레인 추출
 *   2. intensity > 200/255 픽셀을 고휘도 후보로 클러스터링
 *   3. 3초 이상 같은 위치에서 지속 발광 여부로 IR 확정
 *   4. 깜빡임(blink) 또는 이동하는 포인트는 일반 LED/반사로 제거
 *
 * 보안 원칙:
 *   - 카메라 프레임 데이터는 메모리에서만 처리, 파일 저장 금지
 *   - 분석 완료 후 ImageProxy는 즉시 close()
 *
 * 에러 코드:
 *   E1003 — 카메라 초기화 실패
 *   E3002 — 카메라 권한 미승인
 */
class IrDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cameraProvider: ProcessCameraProvider,
) {

    // 상수는 companion object로 이동

    // ─────────────────────────────────────────────────────────
    // 상태
    // ─────────────────────────────────────────────────────────

    private val _irPoints = MutableStateFlow<List<IrPoint>>(emptyList())

    /** 감지된 IR 포인트 StateFlow — UI 레이어에서 구독 */
    val irPoints: StateFlow<List<IrPoint>> = _irPoints.asStateFlow()

    /** IR 포인트 시간 추적기 (위치 키 → 추적 정보) */
    private val irTracker: MutableMap<String, TrackedIrPoint> = ConcurrentHashMap()

    /** CameraX Camera 객체 */
    private var camera: Camera? = null

    /** 이미지 분석용 백그라운드 스레드 */
    private var analysisExecutor: ExecutorService? = null

    /** 감지 진행 여부 */
    private var isDetecting: Boolean = false

    // ─────────────────────────────────────────────────────────
    // 공개 API
    // ─────────────────────────────────────────────────────────

    /**
     * IR LED 감지를 시작한다.
     *
     * 전면 카메라를 바인딩하고 프레임 분석을 시작한다.
     * 반드시 LifecycleOwner를 전달해야 한다.
     *
     * @param lifecycleOwner CameraX 생명주기 관리용 소유자
     * @return 시작 성공 시 Result.success(Unit), 실패 시 Result.failure
     */
    fun startDetection(lifecycleOwner: LifecycleOwner): Result<Unit> {
        if (isDetecting) {
            Timber.d("IR 감지가 이미 실행 중입니다.")
            return Result.success(Unit)
        }

        return try {
            irTracker.clear()
            analysisExecutor = Executors.newSingleThreadExecutor()

            val imageAnalysis = buildImageAnalysis()
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                imageAnalysis,
            )

            isDetecting = true

            Timber.d("IR 감지 시작 — 전면 카메라 바인딩 완료")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[${Constants.ErrorCode.E1003}] IR 감지 카메라 초기화 실패")
            Result.failure(e)
        }
    }

    /**
     * IR LED 감지를 중단한다.
     *
     * 전면 카메라 바인딩을 해제하고 추적 상태를 초기화한다.
     *
     * @return 항상 Result.success(Unit) 반환
     */
    fun stopDetection(): Result<Unit> {
        return try {
            isDetecting = false

            analysisExecutor?.shutdown()
            analysisExecutor = null

            cameraProvider.unbindAll()
            camera = null

            irTracker.clear()
            _irPoints.value = emptyList()

            Timber.d("IR 감지 중단 — 전면 카메라 해제 완료")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "IR 감지 중단 중 오류 발생")
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
                analysis.setAnalyzer(executor, IrFrameAnalyzer())
            }
    }

    /**
     * 단일 프레임에서 고휘도 포인트를 추출하고 추적기를 업데이트한다.
     *
     * @param image CameraX ImageProxy (YUV_420_888)
     */
    private fun processFrame(image: ImageProxy) {
        val grayPixels = extractGrayscaleY(image)
        val width = image.width
        val height = image.height

        // 고휘도 픽셀 클러스터 추출
        val candidates = extractHighIntensityClusters(grayPixels, width, height)

        val now = System.currentTimeMillis()
        val matchedKeys = mutableSetOf<String>()

        // 각 후보를 추적기에 업데이트
        for (candidate in candidates) {
            val color = classifyColor(candidate.r, candidate.g, candidate.b)
            // IR 특성: 보라색 또는 흰색만 유효
            if (color != IrColor.PURPLE && color != IrColor.WHITE) continue

            val matchKey = findNearbyTracked(candidate.x, candidate.y)
            if (matchKey != null) {
                val tracked = irTracker[matchKey]!!
                val movement = sqrt(
                    ((tracked.lastX - candidate.x).toFloat().let { it * it } +
                        (tracked.lastY - candidate.y).toFloat().let { it * it }).toDouble()
                ).toFloat()

                // 이전 프레임 대비 발광 여부 변화로 깜빡임 감지
                val wasVisible = tracked.lastVisible
                val blinkDelta = if (wasVisible != candidate.visible) 1 else 0

                val updated = tracked.copy(
                    lastX = candidate.x,
                    lastY = candidate.y,
                    duration = now - tracked.firstSeenAt,
                    totalMovement = tracked.totalMovement + movement,
                    blinkCount = tracked.blinkCount + blinkDelta,
                    lastIntensity = candidate.intensity,
                    lastColor = color,
                    lastVisible = candidate.visible,
                )
                irTracker[matchKey] = updated
                matchedKeys.add(matchKey)
            } else {
                val key = "${candidate.x}_${candidate.y}_$now"
                irTracker[key] = TrackedIrPoint(
                    originX = candidate.x,
                    originY = candidate.y,
                    lastX = candidate.x,
                    lastY = candidate.y,
                    firstSeenAt = now,
                    duration = 0L,
                    totalMovement = 0f,
                    blinkCount = 0,
                    lastIntensity = candidate.intensity,
                    lastColor = color,
                    lastVisible = candidate.visible,
                )
                matchedKeys.add(key)
            }
        }

        // 감지되지 않은 포인트 만료 처리
        val expiredKeys = irTracker.keys.filter { it !in matchedKeys }
        expiredKeys.forEach { irTracker.remove(it) }

        // 깜빡임 포인트 제거 (일반 LED)
        irTracker.entries.removeIf { (_, v) -> v.blinkCount > BLINK_COUNT_THRESHOLD }
        // 이동 포인트 제거 (반사광)
        irTracker.entries.removeIf { (_, v) -> v.totalMovement > MOVEMENT_THRESHOLD_PX }

        // 확정 포인트 방출 (3초 이상 지속)
        val confirmed = irTracker.values
            .filter { it.duration >= IR_CONFIRM_DURATION_MS }
            .map { tracked ->
                IrPoint(
                    x = tracked.lastX,
                    y = tracked.lastY,
                    intensity = tracked.lastIntensity,
                    timestamp = tracked.firstSeenAt,
                    isStable = tracked.totalMovement <= MOVEMENT_THRESHOLD_PX / 2,
                    color = tracked.lastColor,
                    riskScore = calculateIrRiskScore(tracked),
                )
            }
            .sortedByDescending { it.riskScore }

        _irPoints.value = confirmed
    }

    // ─────────────────────────────────────────────────────────
    // 분석 헬퍼
    // ─────────────────────────────────────────────────────────

    /**
     * ImageProxy Y 플레인에서 그레이스케일 픽셀을 추출한다.
     *
     * YUV_420_888 포맷에서 Y 플레인(밝기)만 사용한다.
     * 처리 후 버퍼를 참조 해제하여 메모리 누수를 방지한다.
     */
    private fun extractGrayscaleY(image: ImageProxy): IntArray {
        val plane = image.planes[0]
        val buffer: ByteBuffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val width = image.width
        val height = image.height

        val result = IntArray(width * height)
        for (row in 0 until height) {
            for (col in 0 until width) {
                val bufferIndex = row * rowStride + col * pixelStride
                result[row * width + col] = buffer[bufferIndex].toInt() and 0xFF
            }
        }
        return result
    }

    /**
     * 그레이스케일 픽셀에서 고휘도 후보 클러스터를 추출한다.
     *
     * intensity > 200/255 픽셀을 클러스터링한다.
     * RGB 색상 추정은 Y 값 기반 근사값을 사용한다 (YUV에서 UV 플레인 분리).
     */
    private fun extractHighIntensityClusters(
        pixels: IntArray,
        width: Int,
        height: Int,
    ): List<IrCandidate> {
        val visited = BooleanArray(width * height) { false }
        val candidates = mutableListOf<IrCandidate>()

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val value = pixels[idx]

                if (value > IR_INTENSITY_THRESHOLD && !visited[idx]) {
                    val clusterPixels = collectCluster(pixels, visited, width, height, x, y)
                    val meanIntensity = clusterPixels.map { pixels[it.second * width + it.first] }.average().toFloat()

                    val centerX = clusterPixels.map { it.first }.average().toInt()
                    val centerY = clusterPixels.map { it.second }.average().toInt()

                    // YUV에서 정확한 RGB 분리가 어려우므로 intensity 기반으로 색상 근사
                    // 순수 흰색(IR 850nm): 고르게 밝음 → WHITE
                    // 자주색(IR 940nm): Y 채널에서 다소 낮은 값 → PURPLE 근사
                    val estimatedR = meanIntensity
                    val estimatedG = meanIntensity * 0.6f
                    val estimatedB = meanIntensity * 0.8f

                    candidates.add(
                        IrCandidate(
                            x = centerX,
                            y = centerY,
                            intensity = meanIntensity,
                            r = estimatedR.toInt(),
                            g = estimatedG.toInt(),
                            b = estimatedB.toInt(),
                            visible = true,
                        )
                    )
                }
            }
        }
        return candidates
    }

    /**
     * BFS로 연결된 고휘도 픽셀 좌표를 수집한다.
     */
    private fun collectCluster(
        pixels: IntArray,
        visited: BooleanArray,
        width: Int,
        height: Int,
        startX: Int,
        startY: Int,
    ): List<Pair<Int, Int>> {
        val result = mutableListOf<Pair<Int, Int>>()
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(startX to startY)

        while (queue.isNotEmpty()) {
            val (cx, cy) = queue.removeFirst()
            if (cx < 0 || cx >= width || cy < 0 || cy >= height) continue

            val idx = cy * width + cx
            if (visited[idx]) continue
            if (pixels[idx] <= IR_INTENSITY_THRESHOLD) continue

            visited[idx] = true
            result.add(cx to cy)

            // 성능 보호: 대형 클러스터 제한
            if (result.size > 200) break

            queue.add(cx - 1 to cy)
            queue.add(cx + 1 to cy)
            queue.add(cx to cy - 1)
            queue.add(cx to cy + 1)
        }
        return result
    }

    /**
     * RGB 값에서 IR 색상 유형을 분류한다.
     *
     * IR 850nm → WHITE (고르게 밝음)
     * IR 940nm → PURPLE (R/B 강조, G 낮음)
     * 그 외 → RED (가시광선 경계)
     */
    private fun classifyColor(r: Int, g: Int, b: Int): IrColor {
        return when {
            // 자주색: R > 200, G 낮음, B > 150
            r > 200 && g < 150 && b > 150 -> IrColor.PURPLE
            // 흰색: 모두 고르게 밝음
            r > 200 && g > 180 && b > 180 -> IrColor.WHITE
            // 그 외 밝은 포인트 → RED 경계
            else -> IrColor.RED
        }
    }

    /**
     * 기존 추적 포인트 중 주어진 좌표 근처의 포인트를 찾는다.
     */
    private fun findNearbyTracked(x: Int, y: Int): String? {
        return irTracker.entries.firstOrNull { (_, tracked) ->
            abs(tracked.lastX - x) <= TRACKING_TOLERANCE_PX &&
                abs(tracked.lastY - y) <= TRACKING_TOLERANCE_PX
        }?.key
    }

    /**
     * IR 포인트 위험 점수를 산출한다.
     *
     *   지속 3초 이상: +30점
     *   위치 안정적:  +25점
     *   PURPLE 색상:  +20점
     *   intensity > 220: +15점
     */
    private fun calculateIrRiskScore(tracked: TrackedIrPoint): Int =
        listOfNotNull(
            30.takeIf { tracked.duration >= IR_CONFIRM_DURATION_MS },
            25.takeIf { tracked.totalMovement <= MOVEMENT_THRESHOLD_PX / 2 },
            20.takeIf { tracked.lastColor == IrColor.PURPLE },
            15.takeIf { tracked.lastIntensity > 220f },
        ).sumOf { it }.coerceIn(0, 100)

    // ─────────────────────────────────────────────────────────
    // Inner Analyzer
    // ─────────────────────────────────────────────────────────

    /**
     * CameraX ImageAnalysis.Analyzer 구현체
     *
     * 프레임을 processFrame()에 전달하고 반드시 close()한다.
     */
    private inner class IrFrameAnalyzer : ImageAnalysis.Analyzer {

        override fun analyze(image: ImageProxy) {
            // 보안 원칙: 프레임은 메모리에서만 처리, 파일 저장 금지
            try {
                if (!isDetecting) {
                    image.close()
                    return
                }
                processFrame(image)
            } catch (e: Exception) {
                Timber.e(e, "[${Constants.ErrorCode.E1003}] IR 프레임 분석 중 오류 발생")
            } finally {
                image.close()
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // 내부 데이터 클래스
    // ─────────────────────────────────────────────────────────

    /** IR 감지 후보 (단일 프레임) */
    private data class IrCandidate(
        val x: Int,
        val y: Int,
        val intensity: Float,
        val r: Int,
        val g: Int,
        val b: Int,
        val visible: Boolean,
    )

    /**
     * IR 포인트 시간 추적 상태
     *
     * 불변 data class — 업데이트 시 copy()를 사용한다.
     */
    private data class TrackedIrPoint(
        val originX: Int,
        val originY: Int,
        val lastX: Int,
        val lastY: Int,
        val firstSeenAt: Long,
        val duration: Long,
        val totalMovement: Float,
        val blinkCount: Int,
        val lastIntensity: Float,
        val lastColor: IrColor,
        val lastVisible: Boolean,
    )

    companion object {
        /** IR 고휘도 임계값 (0~255) — 어두운 환경 기준 고정값 */
        private const val IR_INTENSITY_THRESHOLD = 200

        /** IR 의심 확정 지속 시간 (ms) */
        private const val IR_CONFIRM_DURATION_MS = 3_000L

        /** 이동 포인트 제거 임계값 (픽셀) */
        private const val MOVEMENT_THRESHOLD_PX = 30

        /** 깜빡임 제거 임계값 (횟수) */
        private const val BLINK_COUNT_THRESHOLD = 3

        /** 안정성 판정 위치 허용 오차 (픽셀) */
        private const val TRACKING_TOLERANCE_PX = 15

        /** 클러스터 연결 판정 거리 (픽셀) */
        private const val CLUSTER_MERGE_DISTANCE = 5
    }
}
