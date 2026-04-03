package com.searcam.domain.model

/**
 * Retroreflection(역반사) 기반 렌즈 감지 포인트
 *
 * 플래시를 켰을 때 카메라 렌즈에서 빛이 되돌아오는 역반사 현상을 이용한다.
 * CameraX ImageAnalysis에서 프레임마다 추출된 고휘도 포인트를 표현한다.
 * 불변 data class — 상태 변경 시 copy()를 사용한다.
 */
data class RetroreflectionPoint(
    /** 프레임 내 X 좌표 (pixel) */
    val x: Int,

    /** 프레임 내 Y 좌표 (pixel) */
    val y: Int,

    /** 포인트 반지름 (pixel), 유효 범위: 1.0 ~ 10.0 */
    val radius: Float,

    /** 절대 밝기 (0~255) — 255에 가까울수록 강한 역반사 */
    val brightness: Float,

    /** 원형도 (0.0~1.0) — 렌즈는 > 0.8, 불규칙 형태는 낮음 */
    val circularity: Float,

    /** 2초간 위치 안정성 여부 — true이면 일시적 반사가 아닌 고정 렌즈 가능성 높음 */
    val isStable: Boolean,

    /** 플래시 OFF 시 소실 여부 — true이면 역반사 의심 강함 */
    val flashDependency: Boolean,

    /** 포인트별 위험 점수 (0~100) */
    val riskScore: Int,

    /** 감지된 프레임 타임스탬프 (Unix epoch millis) */
    val detectedAt: Long,
)
