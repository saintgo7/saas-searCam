package com.searcam.domain.usecase

import javax.inject.Inject

import androidx.lifecycle.LifecycleOwner
import com.searcam.domain.model.IrPoint
import com.searcam.domain.model.LayerResult
import com.searcam.domain.model.LayerType
import com.searcam.domain.model.MagneticReading
import com.searcam.domain.model.NetworkDevice
import com.searcam.domain.model.RetroreflectionPoint
import com.searcam.domain.model.RiskLevel
import com.searcam.domain.model.ScanMode
import com.searcam.domain.model.ScanReport
import com.searcam.domain.model.ScanStatus
import com.searcam.domain.repository.IrDetectionRepository
import com.searcam.domain.repository.LensDetectionRepository
import com.searcam.domain.repository.MagneticRepository
import com.searcam.domain.repository.WifiScanRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout
import java.util.UUID

/**
 * 정밀 스캔(Full Scan) UseCase
 *
 * 3개 레이어(Wi-Fi + 렌즈 + IR + 자기장)를 병렬로 실행하여
 * 교차 검증 위험도를 산출한 ScanReport를 반환한다.
 *
 * 병렬 전략:
 *   - coroutineScope + async를 사용하여 4개 레이어를 동시에 실행
 *   - 각 레이어에 개별 타임아웃 적용
 *   - 한 레이어 실패가 전체 스캔을 중단하지 않음
 *
 * 흐름:
 *   1. 4개 레이어 async 병렬 실행
 *   2. 각 레이어가 완료되는 순서대로 LayerResult 수집
 *   3. 모든 레이어 완료 후 CalculateRiskUseCase로 위험도 산출
 *   4. ScanReport emit
 */
class RunFullScanUseCase @Inject constructor(
    private val wifiScanRepository: WifiScanRepository,
    private val lensDetectionRepository: LensDetectionRepository,
    private val irDetectionRepository: IrDetectionRepository,
    private val magneticRepository: MagneticRepository,
    private val calculateRiskUseCase: CalculateRiskUseCase,
) {

    /**
     * Full Scan을 실행하고 최종 ScanReport를 Flow로 반환한다.
     *
     * ScanReport를 단 한 번 emit하고 종료된다.
     * 전체 타임아웃(180초) 초과 시 그 시점까지의 결과로 리포트를 생성한다.
     *
     * @param lifecycleOwner CameraX 렌즈/IR 레이어 바인딩용 LifecycleOwner
     * @return ScanReport를 emit하는 Flow
     */
    operator fun invoke(lifecycleOwner: LifecycleOwner): Flow<ScanReport> = flow {
        val reportId = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()

        // 4개 레이어 병렬 실행
        val layersParallel = runAllLayersInParallel(lifecycleOwner)
        val wifiResult = layersParallel.wifi
        val lensResult = layersParallel.lens
        val irResult = layersParallel.ir
        val magneticResult = layersParallel.magnetic

        val completedAt = System.currentTimeMillis()

        val layerResults = buildMap {
            put(LayerType.WIFI, wifiResult)
            put(LayerType.LENS, lensResult)
            put(LayerType.IR, irResult)
            put(LayerType.MAGNETIC, magneticResult)
        }

        // 위험도 산출 — CalculateRiskUseCase에 위임
        val (finalScore, correctionFactor) = calculateRiskUseCase.invokeWithCorrection(layerResults)
        val riskLevel = RiskLevel.fromScore(finalScore)

        // 레이어별 수집 데이터를 ScanReport로 통합
        val allDevices = wifiResult.devices
        val allFindings = layerResults.values.flatMap { it.findings }
        val retroPoints = lensResult.retroPoints
        val irPoints = irResult.irPoints
        val magneticReadings = magneticResult.magneticReadings

        val report = ScanReport(
            id = reportId,
            mode = ScanMode.FULL,
            startedAt = startedAt,
            completedAt = completedAt,
            riskScore = finalScore,
            riskLevel = riskLevel,
            devices = allDevices,
            findings = allFindings,
            layerResults = layerResults,
            correctionFactor = correctionFactor,
            locationNote = "",
            retroPoints = retroPoints,
            irPoints = irPoints,
            magneticReadings = magneticReadings,
        )

        emit(report)
    }

    /** 병렬 실행된 4개 레이어 결과를 이름으로 접근하기 위한 컨테이너 */
    private data class AllLayerResults(
        val wifi: LayerResult,
        val lens: LayerResult,
        val ir: LayerResult,
        val magnetic: LayerResult,
    )

    /**
     * 4개 레이어를 coroutineScope + async로 병렬 실행한다.
     *
     * @return 이름 기반 AllLayerResults (위치 기반 destructuring 오류 방지)
     */
    private suspend fun runAllLayersInParallel(lifecycleOwner: LifecycleOwner): AllLayerResults = coroutineScope {
        val wifiDeferred = async { runWifiLayer() }
        val lensDeferred = async { runLensLayer(lifecycleOwner) }
        val irDeferred = async { runIrLayer(lifecycleOwner) }
        val magneticDeferred = async { runMagneticLayer() }

        AllLayerResults(
            wifi = wifiDeferred.await(),
            lens = lensDeferred.await(),
            ir = irDeferred.await(),
            magnetic = magneticDeferred.await(),
        )
    }

    /**
     * Layer 1: Wi-Fi 네트워크 스캔
     */
    private suspend fun runWifiLayer(): LayerResult {
        val startAt = System.currentTimeMillis()
        return try {
            val devices = withTimeout(WIFI_LAYER_TIMEOUT_MS) {
                wifiScanRepository.scanDevices().getOrThrow()
            }
            val score = devices.maxOfOrNull { it.riskScore } ?: 0
            LayerResult(
                layerType = LayerType.WIFI,
                status = ScanStatus.COMPLETED,
                score = score,
                devices = devices,
                durationMs = System.currentTimeMillis() - startAt,
                findings = emptyList(),
            )
        } catch (e: Exception) {
            LayerResult(
                layerType = LayerType.WIFI,
                status = ScanStatus.FAILED,
                score = 0,
                devices = emptyList(),
                durationMs = System.currentTimeMillis() - startAt,
                findings = emptyList(),
            )
        }
    }

    /**
     * Layer 2A: Retroreflection 기반 렌즈 감지
     */
    private suspend fun runLensLayer(lifecycleOwner: LifecycleOwner): LayerResult {
        val startAt = System.currentTimeMillis()
        return try {
            lensDetectionRepository.startDetection(lifecycleOwner).getOrThrow()

            // LENS_SCAN_DURATION_MS 동안 최대 LENS_MAX_SAMPLES 샘플 수집
            val allPoints = withTimeout(LENS_LAYER_TIMEOUT_MS) {
                lensDetectionRepository
                    .observeRetroreflections()
                    .take(LENS_MAX_SAMPLES)
                    .toList()
                    .flatten()
            }

            lensDetectionRepository.stopDetection()

            val score = if (allPoints.isNotEmpty()) {
                allPoints.maxOf { it.riskScore }
            } else {
                0
            }

            LayerResult(
                layerType = LayerType.LENS,
                status = ScanStatus.COMPLETED,
                score = score,
                devices = emptyList(),
                durationMs = System.currentTimeMillis() - startAt,
                findings = emptyList(),
                retroPoints = allPoints,
            )
        } catch (e: Exception) {
            runCatching { lensDetectionRepository.stopDetection() }
            LayerResult(
                layerType = LayerType.LENS,
                status = ScanStatus.FAILED,
                score = 0,
                devices = emptyList(),
                durationMs = System.currentTimeMillis() - startAt,
                findings = emptyList(),
            )
        }
    }

    /**
     * Layer 2B: IR LED 감지
     */
    private suspend fun runIrLayer(lifecycleOwner: LifecycleOwner): LayerResult {
        val startAt = System.currentTimeMillis()
        return try {
            irDetectionRepository.startDetection(lifecycleOwner).getOrThrow()

            val allPoints = withTimeout(IR_LAYER_TIMEOUT_MS) {
                irDetectionRepository
                    .observeIrPoints()
                    .take(IR_MAX_SAMPLES)
                    .toList()
                    .flatten()
            }

            irDetectionRepository.stopDetection()

            val score = if (allPoints.isNotEmpty()) {
                allPoints.maxOf { it.riskScore }
            } else {
                0
            }

            LayerResult(
                layerType = LayerType.IR,
                status = ScanStatus.COMPLETED,
                score = score,
                devices = emptyList(),
                durationMs = System.currentTimeMillis() - startAt,
                findings = emptyList(),
                irPoints = allPoints,
            )
        } catch (e: Exception) {
            runCatching { irDetectionRepository.stopDetection() }
            LayerResult(
                layerType = LayerType.IR,
                status = ScanStatus.FAILED,
                score = 0,
                devices = emptyList(),
                durationMs = System.currentTimeMillis() - startAt,
                findings = emptyList(),
            )
        }
    }

    /**
     * Layer 3: 자기장(EMF) 이상 감지
     */
    private suspend fun runMagneticLayer(): LayerResult {
        val startAt = System.currentTimeMillis()
        return try {
            // 캘리브레이션 후 측정
            magneticRepository.calibrate().getOrThrow()

            val readings = withTimeout(MAGNETIC_LAYER_TIMEOUT_MS) {
                magneticRepository
                    .observeReadings()
                    .take(MAGNETIC_MAX_SAMPLES)
                    .toList()
            }

            // EMF 레벨 기여 점수의 최대값을 레이어 점수로 사용
            val score = readings.maxOfOrNull { it.level.score } ?: 0

            LayerResult(
                layerType = LayerType.MAGNETIC,
                status = ScanStatus.COMPLETED,
                score = score,
                devices = emptyList(),
                durationMs = System.currentTimeMillis() - startAt,
                findings = emptyList(),
            )
        } catch (e: Exception) {
            LayerResult(
                layerType = LayerType.MAGNETIC,
                status = ScanStatus.FAILED,
                score = 0,
                devices = emptyList(),
                durationMs = System.currentTimeMillis() - startAt,
                findings = emptyList(),
            )
        }
    }

    // ─────────────────────────────────────────────────────────
    // LayerResult에서 도메인 모델 추출 헬퍼
    // ─────────────────────────────────────────────────────────

    // retroPoints, irPoints, magneticReadings는 LayerResult에서 직접 읽음
    // (LensDetectionRepositoryImpl, IrDetectionRepositoryImpl, MagneticRepositoryImpl이 채워넣음)

    companion object {
        private const val WIFI_LAYER_TIMEOUT_MS = 30_000L
        private const val LENS_LAYER_TIMEOUT_MS = 60_000L
        private const val IR_LAYER_TIMEOUT_MS = 45_000L
        private const val MAGNETIC_LAYER_TIMEOUT_MS = 30_000L

        /** 렌즈 레이어에서 최대 수집할 Flow emit 횟수 */
        private const val LENS_MAX_SAMPLES = 100

        /** IR 레이어에서 최대 수집할 Flow emit 횟수 */
        private const val IR_MAX_SAMPLES = 100

        /** 자기장 레이어에서 최대 수집할 측정값 수 */
        private const val MAGNETIC_MAX_SAMPLES = 400
    }
}
