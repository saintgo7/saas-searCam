package com.searcam.domain.model

/**
 * 탐지 레이어 유형 열거형
 *
 * SearCam은 3개 레이어(Wi-Fi, 렌즈, 자기장)를 교차 검증한다.
 * 렌즈 레이어는 Retroreflection(LENS)과 IR(IR) 두 단계로 세분화된다.
 *
 * 가중치:
 *   Wi-Fi 연결 시: WIFI=0.50, LENS=0.20, IR=0.15, MAGNETIC=0.15
 *   Wi-Fi 미연결 시: WIFI=0.00, LENS=0.45, IR=0.30, MAGNETIC=0.25
 */
enum class LayerType(
    val weight: Float,          // Wi-Fi 연결 시 기본 가중치
    val weightNoWifi: Float,    // Wi-Fi 미연결 시 가중치
    val labelKo: String,        // 한국어 표시명
) {
    /** Layer 1: Wi-Fi 네트워크 스캔 (OUI 매칭, 포트 스캔) */
    WIFI(weight = 0.50f, weightNoWifi = 0.00f, labelKo = "Wi-Fi 스캔"),

    /** Layer 2A: Retroreflection 기반 렌즈 감지 (플래시 역반사) */
    LENS(weight = 0.20f, weightNoWifi = 0.45f, labelKo = "렌즈 감지"),

    /** Layer 2B: IR LED 감지 (전면 카메라) */
    IR(weight = 0.15f, weightNoWifi = 0.30f, labelKo = "IR 감지"),

    /** Layer 3: 자기장(EMF) 이상 감지 */
    MAGNETIC(weight = 0.15f, weightNoWifi = 0.25f, labelKo = "자기장 감지"),
}
