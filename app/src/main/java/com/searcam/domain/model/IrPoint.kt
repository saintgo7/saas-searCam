package com.searcam.domain.model

/**
 * IR(적외선) LED 감지 포인트
 *
 * 전면 카메라는 일반 가시광 필터가 없어 IR LED를 인식할 수 있다.
 * 몰래카메라의 야간 촬영용 IR LED를 자주색/흰색 빛으로 포착한다.
 * 불변 data class — 상태 변경 시 copy()를 사용한다.
 */
data class IrPoint(
    /** 프레임 내 X 좌표 (pixel) */
    val x: Int,

    /** 프레임 내 Y 좌표 (pixel) */
    val y: Int,

    /** 밝기 강도 (0~255) — 255에 가까울수록 강한 IR 방출 */
    val intensity: Float,

    /** 지속 시간 (ms) — 의심 기준: > 3,000ms */
    val timestamp: Long,

    /** 위치 안정성 — true이면 일정 위치에서 반복 감지됨 */
    val isStable: Boolean,

    /** 감지된 색상 — 전면 카메라에서 포착된 IR 색상 */
    val color: IrColor,

    /** 포인트별 위험 점수 (0~100) */
    val riskScore: Int,
)

/**
 * 전면 카메라에서 포착되는 IR LED 색상 유형
 *
 * IR 빛은 사람 눈에 보이지 않지만, 스마트폰 전면 카메라는 감지할 수 있다.
 */
enum class IrColor(
    val labelKo: String,
    val description: String,
) {
    /** 자주색 — 가장 흔한 IR LED 포착 색상 (940nm 근적외선) */
    PURPLE(labelKo = "자주색", description = "IR 940nm 대역 — 카메라 렌즈 의심"),

    /** 흰색/밝은 색 — 850nm IR LED 또는 강한 IR 방출 */
    WHITE(labelKo = "흰색", description = "IR 850nm 대역 — 강한 IR 방출 의심"),

    /** 붉은색 — 가시광 경계(700nm) 근처 IR 또는 적색 LED */
    RED(labelKo = "붉은색", description = "근적외선 경계 대역"),
}
