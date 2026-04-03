package com.searcam.domain.model

/**
 * 종합 스캔 리포트
 *
 * 한 번의 스캔 세션에서 수집된 모든 탐지 레이어 결과를 집약한다.
 * 교차 검증 엔진의 최종 출력물이며, Room DB에 직렬화되어 저장된다.
 * 불변 data class — 상태 변경 시 copy()를 사용한다.
 */
data class ScanReport(
    /** 고유 식별자 (UUID) */
    val id: String,

    /** 스캔 모드 (QUICK/FULL/LENS_FINDER 등) */
    val mode: ScanMode,

    /** 스캔 시작 시각 (Unix epoch millis) */
    val startedAt: Long,

    /** 스캔 완료 시각 (Unix epoch millis) */
    val completedAt: Long,

    /** 교차 검증 엔진이 산출한 종합 위험도 점수 (0~100) */
    val riskScore: Int,

    /** 위험도 점수를 등급으로 변환한 결과 */
    val riskLevel: RiskLevel,

    /** 네트워크에서 발견된 기기 목록 (QUICK/FULL 모드에서만 채워짐) */
    val devices: List<NetworkDevice>,

    /** 모든 레이어의 발견 사항 통합 목록 */
    val findings: List<Finding>,

    /** 레이어별 개별 분석 결과 — 키: LayerType, 값: LayerResult */
    val layerResults: Map<LayerType, LayerResult>,

    /** 보정 계수 — 양성 레이어 수에 따라 0.7/1.2/1.5 적용됨 */
    val correctionFactor: Float,

    /** 사용자가 입력한 위치 메모 (예: "숙소 301호") */
    val locationNote: String,

    /** Retroreflection 렌즈 의심 포인트 목록 */
    val retroPoints: List<RetroreflectionPoint>,

    /** IR LED 의심 포인트 목록 */
    val irPoints: List<IrPoint>,

    /** 자기장 측정 기록 (Full Scan 시 수집, 최대 100개) */
    val magneticReadings: List<MagneticReading>,
) {
    /** 스캔 소요 시간 (ms) */
    val durationMs: Long get() = completedAt - startedAt

    /** 의심 기기 수 (isCamera = true 기기) */
    val suspiciousDeviceCount: Int get() = devices.count { it.isCamera }

    /** Wi-Fi 레이어가 실행된 여부 */
    val isWifiLayerRun: Boolean
        get() = layerResults[LayerType.WIFI]?.status == ScanStatus.COMPLETED
}
