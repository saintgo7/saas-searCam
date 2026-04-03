package com.searcam.domain.usecase

import com.searcam.domain.model.LayerResult
import com.searcam.domain.model.LayerType
import com.searcam.domain.model.RiskLevel
import com.searcam.domain.model.ScanMode
import com.searcam.domain.model.ScanReport
import com.searcam.domain.model.ScanStatus
import com.searcam.domain.repository.WifiScanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import java.util.UUID

/**
 * 빠른 스캔(Quick Scan) UseCase
 *
 * Wi-Fi 네트워크 레이어만 분석하여 30초 안에 결과를 반환한다.
 * CalculateRiskUseCase에 위임하여 위험도를 산출한다.
 *
 * 흐름:
 *   1. Wi-Fi 네트워크 스캔 (30초 타임아웃)
 *   2. LayerResult 구성
 *   3. CalculateRiskUseCase로 위험도 산출
 *   4. ScanReport emit
 */
class RunQuickScanUseCase(
    private val wifiScanRepository: WifiScanRepository,
    private val calculateRiskUseCase: CalculateRiskUseCase,
) {

    /**
     * Quick Scan을 실행하고 최종 ScanReport를 Flow로 반환한다.
     *
     * ScanReport를 단 한 번 emit하고 종료된다.
     * 타임아웃(30초) 초과 시 그 시점까지의 결과로 리포트를 생성한다.
     *
     * @return ScanReport를 emit하는 Flow
     */
    operator fun invoke(): Flow<ScanReport> = flow {
        val reportId = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()

        // Wi-Fi 레이어 실행 (30초 타임아웃)
        val wifiLayerResult = runWifiLayer()

        val completedAt = System.currentTimeMillis()

        val layerResults = mapOf(LayerType.WIFI to wifiLayerResult)

        // 위험도 산출 — CalculateRiskUseCase에 위임
        val (finalScore, correctionFactor) = calculateRiskUseCase.invokeWithCorrection(layerResults)
        val riskLevel = RiskLevel.fromScore(finalScore)

        val report = ScanReport(
            id = reportId,
            mode = ScanMode.QUICK,
            startedAt = startedAt,
            completedAt = completedAt,
            riskScore = finalScore,
            riskLevel = riskLevel,
            devices = wifiLayerResult.devices,
            findings = wifiLayerResult.findings,
            layerResults = layerResults,
            correctionFactor = correctionFactor,
            locationNote = "",
            retroPoints = emptyList(),
            irPoints = emptyList(),
            magneticReadings = emptyList(),
        )

        emit(report)
    }

    /**
     * Wi-Fi 레이어를 실행하고 LayerResult를 반환한다.
     *
     * 30초 타임아웃 내에 완료되지 않으면 FAILED 상태로 반환된다.
     */
    private suspend fun runWifiLayer(): LayerResult {
        val layerStartAt = System.currentTimeMillis()

        return try {
            val devices = withTimeout(QUICK_SCAN_TIMEOUT_MS) {
                wifiScanRepository.scanDevices().getOrThrow()
            }
            val layerDuration = System.currentTimeMillis() - layerStartAt

            // 기기별 위험도를 기반으로 레이어 점수 산출 (최고 위험 기기 점수 사용)
            val layerScore = devices.maxOfOrNull { it.riskScore } ?: 0

            LayerResult(
                layerType = LayerType.WIFI,
                status = ScanStatus.COMPLETED,
                score = layerScore,
                devices = devices,
                durationMs = layerDuration,
                findings = emptyList(),
            )
        } catch (e: Exception) {
            val layerDuration = System.currentTimeMillis() - layerStartAt
            LayerResult(
                layerType = LayerType.WIFI,
                status = ScanStatus.FAILED,
                score = 0,
                devices = emptyList(),
                durationMs = layerDuration,
                findings = emptyList(),
            )
        }
    }

    companion object {
        /** Quick Scan 전체 타임아웃 (30초) */
        private const val QUICK_SCAN_TIMEOUT_MS = 30_000L
    }
}
