package com.searcam.domain.model

// retroPoints, irPoints, magneticReadings 필드에서 사용
// (같은 패키지이므로 import 불필요)

/**
 * 스캔 관련 보조 타입 모음
 *
 * ScanReport에서 사용되는 Finding, FindingType, Severity, LayerResult를 정의한다.
 * ScanReport.kt의 줄 수 제한(800줄)을 위해 별도 파일로 분리했다.
 */

// ─────────────────────────────────────────────────────────
// Finding — 근거 기반 발견 사항
// ─────────────────────────────────────────────────────────

/**
 * 스캔에서 발견된 개별 의심 징후
 *
 * 각 레이어 분석 중 발견된 구체적 근거를 담는다.
 * 불변 data class — 상태 변경 시 copy()를 사용한다.
 */
data class Finding(
    /** 고유 식별자 (UUID) */
    val id: String,

    /** 발견 유형 */
    val type: FindingType,

    /** 심각도 */
    val severity: Severity,

    /** 한국어 설명 (예: "Hikvision 카메라 제조사 MAC 주소 감지") */
    val description: String,

    /** 분석 근거 (예: "MAC: 28:57:BE:11:22:33 → Hikvision") */
    val evidence: String,

    /** 이 발견이 종합 점수에 기여한 점수 (0~100) */
    val contributionScore: Int,
)

// ─────────────────────────────────────────────────────────
// FindingType — 발견 유형 분류
// ─────────────────────────────────────────────────────────

/**
 * 발견 유형 열거형
 *
 * 어떤 분석 방법으로 해당 징후를 발견했는지를 구분한다.
 */
enum class FindingType(
    val labelKo: String,    // 한국어 표시명
    val layer: LayerType,   // 해당 탐지 레이어
) {
    /** OUI 데이터베이스에서 카메라 제조사 MAC 주소 일치 */
    SUSPICIOUS_DEVICE(labelKo = "의심 기기 발견", layer = LayerType.WIFI),

    /** 네트워크에서 RTSP 포트(554) 개방 확인 */
    RTSP_PORT_OPEN(labelKo = "RTSP 포트 개방", layer = LayerType.WIFI),

    /** ONVIF(3702) 포트 개방 — IP 카메라 표준 프로토콜 */
    ONVIF_PORT_OPEN(labelKo = "ONVIF 포트 개방", layer = LayerType.WIFI),

    /** HTTP 스트리밍 포트(80/8080/8888) 개방 */
    HTTP_STREAM_PORT_OPEN(labelKo = "HTTP 스트림 포트 개방", layer = LayerType.WIFI),

    /** mDNS로 RTSP 서비스가 광고됨 */
    MDNS_CAMERA_SERVICE(labelKo = "mDNS 카메라 서비스", layer = LayerType.WIFI),

    /** Retroreflection 기반 렌즈 의심 포인트 감지 */
    LENS_DETECTED(labelKo = "렌즈 감지", layer = LayerType.LENS),

    /** IR LED 의심 포인트 감지 */
    IR_DETECTED(labelKo = "IR LED 감지", layer = LayerType.IR),

    /** 자기장 이상 변화 감지 */
    EMF_ANOMALY(labelKo = "자기장 이상", layer = LayerType.MAGNETIC),
}

// ─────────────────────────────────────────────────────────
// Severity — 발견 심각도
// ─────────────────────────────────────────────────────────

/**
 * 발견 심각도 열거형
 *
 * Finding의 위험 수준을 5단계로 분류한다.
 */
enum class Severity(
    val labelKo: String,
    val colorHex: String,
) {
    /** 참고 정보 — 위험과 무관한 일반 정보 */
    INFO(labelKo = "정보", colorHex = "#9E9E9E"),

    /** 낮음 — 단독으로는 위험하지 않으나 복합 시 주의 */
    LOW(labelKo = "낮음", colorHex = "#8BC34A"),

    /** 보통 — 추가 확인 권장 */
    MEDIUM(labelKo = "보통", colorHex = "#FFC107"),

    /** 높음 — 강한 의심 징후 */
    HIGH(labelKo = "높음", colorHex = "#FF9800"),

    /** 매우 높음 — 즉각적인 대응 권장 */
    CRITICAL(labelKo = "매우 높음", colorHex = "#F44336"),
}

// ─────────────────────────────────────────────────────────
// LayerResult — 단일 탐지 레이어 분석 결과
// ─────────────────────────────────────────────────────────

/**
 * 단일 탐지 레이어의 분석 결과
 *
 * CalculateRiskUseCase에 전달되어 교차 검증 점수 산출에 사용된다.
 * 불변 data class — 상태 변경 시 copy()를 사용한다.
 */
data class LayerResult(
    /** 분석한 탐지 레이어 */
    val layerType: LayerType,

    /** 레이어 실행 상태 */
    val status: ScanStatus,

    /** 레이어 점수 (0~100), status가 COMPLETED가 아니면 0 */
    val score: Int,

    /** 이 레이어에서 발견된 네트워크 기기 목록 (WIFI 레이어만 사용) */
    val devices: List<NetworkDevice>,

    /** 레이어 분석 소요 시간 (ms) */
    val durationMs: Long,

    /** 이 레이어에서 발견된 징후 목록 */
    val findings: List<Finding>,

    /** 역반사 감지 포인트 (LENS 레이어만 사용) */
    val retroPoints: List<RetroreflectionPoint> = emptyList(),

    /** IR LED 감지 포인트 (IR 레이어만 사용) */
    val irPoints: List<IrPoint> = emptyList(),

    /** 자기장 측정값 이력 (MAGNETIC 레이어만 사용) */
    val magneticReadings: List<MagneticReading> = emptyList(),
) {
    /** 레이어에서 양성(의심 징후 있음) 여부 — score > 0 이고 COMPLETED 상태 */
    val isPositive: Boolean get() = status == ScanStatus.COMPLETED && score > 0
}
