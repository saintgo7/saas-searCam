package com.searcam.data.analysis

import com.searcam.domain.model.LayerType
import timber.log.Timber
import javax.inject.Inject

/**
 * CrossValidator 구현체
 *
 * LayerType 가중치를 단일 출처로 사용하는 가중 평균 방식으로 최종 위험도를 산출한다.
 * (LENS + IR 가중치 합산 = 0.35)
 *
 * EMF 센서 미지원 기기 처리:
 * EMF 가중치를 Wi-Fi와 렌즈(LENS+IR)에 비례 재분배하여 총합이 1.0이 되도록 보장.
 */
class CrossValidatorImpl @Inject constructor() : CrossValidator {

    /**
     * 가중 평균으로 최종 위험도 점수 산출
     *
     * 정상 모드 (EMF 사용 가능):
     *   score = wifi * 0.50 + lens * 0.35 + emf * 0.15
     *
     * EMF 미지원 모드 (가중치 비례 재분배):
     *   totalWithoutEmf = WIFI.weight + LENS.weight + IR.weight
     *   wifiAdjusted = WIFI.weight / totalWithoutEmf
     *   lensAdjusted = (LENS.weight + IR.weight) / totalWithoutEmf
     */
    override fun calculateRisk(
        wifiScore: Int,
        lensScore: Int,
        emfScore: Int,
        emfAvailable: Boolean
    ): Int {
        val wifiWeight = LayerType.WIFI.weight
        val lensWeight = LayerType.LENS.weight + LayerType.IR.weight // 0.20 + 0.15 = 0.35
        val emfWeight = LayerType.MAGNETIC.weight

        val rawScore = if (emfAvailable) {
            wifiScore * wifiWeight + lensScore * lensWeight + emfScore * emfWeight
        } else {
            // EMF 가중치를 Wi-Fi와 렌즈에 비례 재분배
            val totalWithoutEmf = wifiWeight + lensWeight
            val wifiAdjusted = wifiWeight / totalWithoutEmf
            val lensAdjusted = lensWeight / totalWithoutEmf
            wifiScore * wifiAdjusted + lensScore * lensAdjusted
        }

        val finalScore = rawScore.toInt().coerceIn(0, 100)

        Timber.d(
            "위험도 산출: wifi=$wifiScore, lens=$lensScore, emf=$emfScore" +
                "(사용가능=$emfAvailable) → $finalScore"
        )

        return finalScore
    }
}
