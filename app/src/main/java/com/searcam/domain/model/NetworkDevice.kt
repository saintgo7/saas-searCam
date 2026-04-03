package com.searcam.domain.model

/**
 * 네트워크 스캔에서 발견된 기기 정보
 *
 * ARP 테이블, mDNS, SSDP를 통해 수집된 네트워크 기기를 표현한다.
 * 불변 data class — 상태 변경 시 copy()를 사용한다.
 */
data class NetworkDevice(
    /** IPv4 주소 (예: "192.168.1.45") */
    val ip: String,

    /** MAC 주소 — 콜론 구분 대문자 형식 (예: "28:57:BE:11:22:33") */
    val mac: String,

    /** mDNS/SSDP로 확인된 호스트명, 확인 불가 시 null */
    val hostname: String?,

    /** OUI 데이터베이스 매칭 제조사명, 매칭 실패 시 null */
    val vendor: String?,

    /** OUI 및 포트 스캔 결과 기반 기기 분류 */
    val deviceType: DeviceType,

    /** TCP 포트 스캔으로 확인된 개방 포트 목록 (예: [554, 80, 8080]) */
    val openPorts: List<Int>,

    /** mDNS로 광고된 서비스 유형 목록 (예: ["_rtsp._tcp", "_http._tcp"]) */
    val services: List<String>,

    /** 기기별 종합 위험 점수 (0~100) */
    val riskScore: Int,

    /** 카메라 판정 여부 — riskScore >= 40 이거나 카메라 제조사 MAC 일치 시 true */
    val isCamera: Boolean,

    /** 발견된 방법 */
    val discoveryMethod: DiscoveryMethod,

    /** 발견 시각 (Unix epoch millis) */
    val discoveredAt: Long,
)

/**
 * 기기 발견 방법 열거형
 */
enum class DiscoveryMethod {
    /** ARP 테이블 조회 (/proc/net/arp) */
    ARP,

    /** mDNS 서비스 탐색 */
    MDNS,

    /** SSDP UPnP 응답 */
    SSDP,

    /** TCP 포트 스캔 직접 발견 */
    PORT_SCAN,
}
