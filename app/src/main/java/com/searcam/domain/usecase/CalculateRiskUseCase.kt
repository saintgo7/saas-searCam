package com.searcam.domain.usecase

import com.searcam.domain.model.LayerResult
import com.searcam.domain.model.LayerType
import com.searcam.domain.model.ScanStatus

/**
 * 교차 검증 기반 위험도 산출 UseCase
 *
 * 3개 레이어 결과를 가중치 합산한 뒤 보정 계수를 적용하여
 * 최종 위험도 점수(0~100)를 반환한다.
 *
 * 알고리즘:
 *   1. Wi-Fi 연결 여부에 따라 가중치를 동적 조정
 *   2. weighted = Σ(layer.weight × layer.score)
 *   3. 양성(score > 0) 레이어 수에 따른 보정 계수 적용
 *      - 1개: × 0.7  (단일 레이어만으로 확신 불가)
 *      - 2개: × 1.2  (복수 레이어 교차 확인)
 *      - 3개: × 1.5  (전 레이어 양성 — 고위험)
 *   4. 최종 점수 = clamp(weighted × correction, 0, 100)
 */
class CalculateRiskUseCase {

    /**
     * 레이어별 결과 Map을 받아 종합 위험도 점수(0~100)를 반환한다.
     *
     * @param layerResults 레이어 유형 → LayerResult 매핑
     * @return 교차 검증 후 보정이 적용된 최종 위험도 점수 (0~100)
     */
    operator fun invoke(layerResults: Map<LayerType, LayerResult>): Int =
        invokeWithCorrection(layerResults).first

    /**
     * 최종 점수와 함께 보정 계수도 반환하는 확장 버전
     *
     * ScanReport.correctionFactor 필드 저장 시 사용한다.
     *
     * @param layerResults 레이어 유형 → LayerResult 매핑
     * @return Pair(finalScore, correctionFactor)
     */
    fun invokeWithCorrection(layerResults: Map<LayerType, LayerResult>): Pair<Int, Float> {
        val completedLayers = layerResults.values.filter { it.status == ScanStatus.COMPLETED }

        if (completedLayers.isEmpty()) return Pair(0, 1.0f)

        val isWifiAvailable = layerResults[LayerType.WIFI]?.status == ScanStatus.COMPLETED

        val weightedScore = completedLayers.sumOf { layerResult ->
            val weight = if (isWifiAvailable) {
                layerResult.layerType.weight
            } else {
                layerResult.layerType.weightNoWifi
            }
            weight * layerResult.score
        }

        val positiveCount = completedLayers.count { it.isPositive }

        val correctionFactor = when (positiveCount) {
            0 -> 1.0f
            1 -> CORRECTION_SINGLE
            2 -> CORRECTION_DOUBLE
            else -> CORRECTION_ALL
        }

        val finalScore = (weightedScore * correctionFactor).toInt().coerceIn(0, 100)
        return Pair(finalScore, correctionFactor)
    }

    companion object {
        /** 양성 레이어 1개 — 신뢰도 하향 보정 */
        private const val CORRECTION_SINGLE = 0.7f

        /** 양성 레이어 2개 — 교차 확인 가중 보정 */
        private const val CORRECTION_DOUBLE = 1.2f

        /** 양성 레이어 3개 이상 — 전 레이어 양성 가중 보정 */
        private const val CORRECTION_ALL = 1.5f
    }
}
