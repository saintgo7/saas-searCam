package com.searcam.domain.model

/**
 * 네트워크 기기 유형 열거형
 *
 * OUI 데이터베이스 매칭 및 포트 스캔 결과를 기반으로 분류된다.
 * isSuspicious = true 인 기기는 교차 검증 점수에 높은 가중치를 부여받는다.
 */
enum class DeviceType(
    val labelKo: String,        // 한국어 표시명
    val isSuspicious: Boolean,  // 몰래카메라 의심 여부 (true = 위험 기기)
) {
    /** IP 카메라 — Hikvision, Dahua 등 전문 IP 카메라 제조사 MAC 일치 */
    IP_CAMERA(labelKo = "IP 카메라", isSuspicious = true),

    /** 스마트 카메라 — Wyze, Tapo, Arlo 등 소비자용 스마트 카메라 */
    SMART_CAMERA(labelKo = "스마트 카메라", isSuspicious = true),

    /** 공유기/AP — 네트워크 인프라 기기 */
    ROUTER(labelKo = "공유기", isSuspicious = false),

    /** NAS — 네트워크 연결 스토리지 */
    NAS(labelKo = "NAS", isSuspicious = false),

    /** 프린터 */
    PRINTER(labelKo = "프린터", isSuspicious = false),

    /** 스마트 TV */
    SMART_TV(labelKo = "스마트 TV", isSuspicious = false),

    /** 스마트폰/태블릿 */
    PHONE(labelKo = "스마트폰", isSuspicious = false),

    /** IoT 스마트홈 기기 */
    SMART_HOME(labelKo = "스마트홈", isSuspicious = false),

    /** 미식별 — OUI 매칭 실패 또는 알 수 없는 기기 */
    UNKNOWN(labelKo = "알 수 없음", isSuspicious = false),
}
