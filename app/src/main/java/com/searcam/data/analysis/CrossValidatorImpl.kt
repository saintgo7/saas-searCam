package com.searcam.data.analysis

import com.searcam.util.Constants
import timber.log.Timber
import javax.inject.Inject

/**
 * CrossValidator 구현체
 *
 * 3-Layer 가중 평균 방식으로 최종 위험도를 산출한다.
 *
 * EMF 센서 미지원 기기 처리:
 * EMF 가중치(15%)를 Wi-Fi(+9%)와 렌즈(+6%)로 비례 재분배한다.
 * 총합이 항상 100%가 되도록 보장.
 */
class CrossValidatorImpl @Inject constructor() : CrossValidator {

    /**
     * 가중 평균으로 최종 위험도 점수 산출
     *
     * 정상 모드 (EMF 사용 가능):
     *   score = wifi * 0.50 + lens * 0.35 + emf * 0.15
     *
     * EMF 미지원 모드 (가중치 재분배):
     *   score = wifi * 0.59 + lens * 0.41
     *   (Wi-Fi: 50 + 15*0.59 ≈ 59%, 렌즈: 35 + 15*0.41 ≈ 41%)
     */
    override fun calculateRisk(
        wifiScore: Int,
        lensScore: Int,
        emfScore: Int,
        emfAvailable: Boolean
    ): Int {
        val rawScore = if (emfAvailable) {
            wifiScore * Constants.WIFI_WEIGHT +
                lensScore * Constants.LENS_WEIGHT +
                emfScore * Constants.EMF_WEIGHT
        } else {
            // EMF 가중치를 Wi-Fi와 렌즈에 비례 재분배
            val totalWithoutEmf = Constants.WIFI_WEIGHT + Constants.LENS_WEIGHT
            val wifiAdjusted = Constants.WIFI_WEIGHT / totalWithoutEmf
            val lensAdjusted = Constants.LENS_WEIGHT / totalWithoutEmf
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
