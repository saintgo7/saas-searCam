package com.searcam.domain.model

/**
 * 위험도 등급 열거형
 *
 * 교차 검증 엔진이 산출한 0~100 점수를 5단계 등급으로 분류한다.
 * fromScore()로 점수 → 등급 변환이 가능하다.
 */
enum class RiskLevel(
    val minScore: Int,      // 최소 점수 (포함)
    val maxScore: Int,      // 최대 점수 (포함)
    val labelKo: String,    // 한국어 표시명
    val colorHex: String,   // UI 색상 (hex)
    val description: String, // 등급 설명
) {
    SAFE(0, 20, "안전", "#4CAF50", "탐지된 위협 없음. 안전한 환경으로 판단됨"),
    INTEREST(21, 40, "관심", "#8BC34A", "경미한 의심 징후. 추가 확인 권장"),
    CAUTION(41, 60, "주의", "#FFC107", "복수의 의심 징후 감지. 주의 깊게 점검 필요"),
    DANGER(61, 80, "위험", "#FF9800", "강한 의심 징후. 즉각적인 점검 권장"),
    CRITICAL(81, 100, "매우 위험", "#F44336", "몰래카메라 의심 강함. 즉시 신고 및 점검 권장");

    companion object {
        /**
         * 점수(0~100)를 위험 등급으로 변환한다.
         * 점수가 범위를 벗어나면 가장 가까운 등급(SAFE 또는 CRITICAL)을 반환한다.
         *
         * @param score 교차 검증 엔진이 산출한 위험도 점수 (0~100)
         * @return 해당 점수에 대응하는 RiskLevel
         */
        fun fromScore(score: Int): RiskLevel {
            val clamped = score.coerceIn(0, 100)
            return entries.firstOrNull { clamped in it.minScore..it.maxScore }
                ?: CRITICAL
        }
    }
}
