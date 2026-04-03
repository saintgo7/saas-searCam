package com.searcam.data.analysis

import com.searcam.domain.model.RiskLevel

/**
 * 3-Layer 교차 검증 엔진 인터페이스
 *
 * Wi-Fi(50%) + 렌즈(35%) + EMF(15%) 가중치로 최종 위험도를 산출한다.
 * 구현체는 CrossValidatorImpl에 위치한다.
 */
interface CrossValidator {

    /**
     * 3개 레이어 점수를 가중 평균하여 최종 위험도를 산출한다.
     *
     * @param wifiScore     Layer 1 Wi-Fi 스캔 점수 (0~100)
     * @param lensScore     Layer 2 렌즈 탐지 점수 (0~100)
     * @param emfScore      Layer 3 EMF 탐지 점수 (0~100, EMF 미지원 시 0)
     * @param emfAvailable  EMF 센서 사용 가능 여부 (미지원 시 가중치 Wi-Fi/렌즈로 재분배)
     * @return 최종 위험도 점수 (0~100, 정수)
     */
    fun calculateRisk(
        wifiScore: Int,
        lensScore: Int,
        emfScore: Int,
        emfAvailable: Boolean = true
    ): Int

    /**
     * 위험도 점수를 RiskLevel 등급으로 변환한다.
     *
     * @param score 0~100 위험도 점수
     * @return 해당 점수의 RiskLevel 등급
     */
    fun toRiskLevel(score: Int): RiskLevel = RiskLevel.fromScore(score)
}
