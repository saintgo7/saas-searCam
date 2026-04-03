package com.searcam.domain.model

/**
 * 자기장 센서 측정값
 *
 * Android SensorManager TYPE_MAGNETIC_FIELD 센서에서 20Hz로 수집된다.
 * x, y, z 단위는 마이크로테슬라(uT)이며, magnitude는 벡터 크기다.
 * 불변 data class — 상태 변경 시 copy()를 사용한다.
 */
data class MagneticReading(
    /** 측정 시각 (Unix epoch millis) */
    val timestamp: Long,

    /** X축 자기장 강도 (uT), 범위: -200.0 ~ 200.0 */
    val x: Float,

    /** Y축 자기장 강도 (uT), 범위: -200.0 ~ 200.0 */
    val y: Float,

    /** Z축 자기장 강도 (uT), 범위: -200.0 ~ 200.0 */
    val z: Float,

    /** 벡터 크기 = sqrt(x² + y² + z²), 범위: 0 ~ 346.0 */
    val magnitude: Float,

    /** 보정값 대비 변화량 (uT), 캘리브레이션 기준점과의 차이 */
    val delta: Float,

    /** delta 크기에 따른 위험 등급 판정 */
    val level: EmfLevel,
)

/**
 * 자기장 변화량 기반 위험 등급
 *
 * delta(uT)를 기준으로 5단계로 분류한다.
 * score는 Layer 3 점수 산출 시 기여 값이다.
 */
enum class EmfLevel(
    val minDelta: Float,    // 최소 변화량 (uT, 포함)
    val maxDelta: Float,    // 최대 변화량 (uT, 미포함)
    val score: Int,         // Layer 3 기여 점수 (0~100)
    val labelKo: String,    // 한국어 표시명
) {
    /** 정상 범위 — 주변 자기장과 차이 없음 */
    NORMAL(minDelta = 0f, maxDelta = 5f, score = 0, labelKo = "정상"),

    /** 관심 — 미세한 변화, 배경 노이즈 수준 */
    INTEREST(minDelta = 5f, maxDelta = 15f, score = 20, labelKo = "관심"),

    /** 주의 — 전자기기 존재 가능성 */
    CAUTION(minDelta = 15f, maxDelta = 30f, score = 50, labelKo = "주의"),

    /** 의심 — 전자기기 존재 강하게 시사 */
    SUSPECT(minDelta = 30f, maxDelta = 50f, score = 75, labelKo = "의심"),

    /** 강한 의심 — 숨겨진 전자기기 가능성 매우 높음 */
    STRONG_SUSPECT(minDelta = 50f, maxDelta = Float.MAX_VALUE, score = 95, labelKo = "강한 의심");

    companion object {
        /**
         * 변화량(uT)을 EmfLevel로 변환한다.
         *
         * @param delta 자기장 변화량 (절대값, uT)
         * @return 해당 변화량에 대응하는 EmfLevel
         */
        fun fromDelta(delta: Float): EmfLevel {
            val absDelta = delta.coerceAtLeast(0f)
            return entries.firstOrNull { absDelta >= it.minDelta && absDelta < it.maxDelta }
                ?: STRONG_SUSPECT
        }
    }
}
