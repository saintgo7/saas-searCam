package com.searcam.domain.model

/**
 * 스캔 모드 열거형
 *
 * 사용자가 선택할 수 있는 스캔 방식을 정의한다.
 * estimatedDurationSec이 -1이면 사용자가 수동으로 종료하는 모드이다.
 */
enum class ScanMode(
    val labelKo: String,            // 한국어 표시명
    val description: String,        // 모드 설명
    val estimatedDurationSec: Int,  // 예상 소요 시간(초), -1 = 수동 종료
    val layers: Set<LayerType>,     // 활성화되는 탐지 레이어 목록
) {
    /**
     * 빠른 스캔 — Wi-Fi 네트워크만 분석, 30초 내 완료
     */
    QUICK(
        labelKo = "빠른 스캔",
        description = "Wi-Fi 네트워크를 30초 안에 스캔합니다",
        estimatedDurationSec = 30,
        layers = setOf(LayerType.WIFI),
    ),

    /**
     * 정밀 스캔 — 3개 레이어 전체 분석 (Wi-Fi + 렌즈 + IR + 자기장), 약 3분 소요
     */
    FULL(
        labelKo = "정밀 스캔",
        description = "모든 탐지 레이어를 순차 분석합니다 (약 3분)",
        estimatedDurationSec = 180,
        layers = setOf(LayerType.WIFI, LayerType.LENS, LayerType.IR, LayerType.MAGNETIC),
    ),

    /**
     * 렌즈 찾기 — Retroreflection 실시간 감지, 사용자가 직접 종료
     */
    LENS_FINDER(
        labelKo = "렌즈 찾기",
        description = "카메라 렌즈 역반사를 실시간으로 감지합니다",
        estimatedDurationSec = -1,
        layers = setOf(LayerType.LENS),
    ),

    /**
     * IR 카메라 — 전면 카메라로 IR LED를 실시간 감지
     */
    IR_ONLY(
        labelKo = "IR 카메라",
        description = "전면 카메라로 적외선 LED를 감지합니다",
        estimatedDurationSec = -1,
        layers = setOf(LayerType.IR),
    ),

    /**
     * 자기장 — 자기장 이상을 실시간으로 측정
     */
    EMF_ONLY(
        labelKo = "자기장",
        description = "숨겨진 전자 기기의 자기장 이상을 감지합니다",
        estimatedDurationSec = -1,
        layers = setOf(LayerType.MAGNETIC),
    ),
}
