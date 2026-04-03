package com.searcam.data.analysis

import com.searcam.domain.model.NetworkDevice
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 네트워크 기기 위험도 계산기
 *
 * 비유: 공항 보안 검색대처럼, 여러 단서(제조사, 열린 포트, 호스트명)를
 * 조합하여 기기가 몰래카메라일 가능성을 0~100점으로 수치화한다.
 *
 * 점수 기준 (최대 100점):
 *   +40점 — 카메라 제조사 MAC (Hikvision, Dahua 등)
 *   +30점 — RTSP 포트(554) 개방
 *   +15점 — HTTP 스트리밍 포트(80, 8080, 8888) 개방
 *   +15점 — 호스트명에 카메라 키워드 포함
 *   +20점 — ONVIF 포트(3702) 개방 (STEP 7 명세 반영)
 *   +25점 — mDNS _rtsp 서비스 광고
 *
 * 불변성: 입력 NetworkDevice를 변경하지 않고 copy()로 새 객체를 반환한다.
 */
@Singleton
class RiskCalculator @Inject constructor(
    private val ouiDatabase: OuiDatabase,
) {

    /**
     * NetworkDevice의 위험도를 계산하고 riskScore가 갱신된 새 객체를 반환한다.
     *
     * @param device 위험도를 계산할 네트워크 기기
     * @return riskScore와 isCamera가 갱신된 불변 복사본
     */
    fun calculateDeviceRisk(device: NetworkDevice): NetworkDevice {
        var score = 0

        // 카메라 제조사 MAC 매칭 (+40점)
        if (device.mac.isNotEmpty() && ouiDatabase.isCameraVendor(device.mac)) {
            score += SCORE_CAMERA_VENDOR
            Timber.d("위험도 +$SCORE_CAMERA_VENDOR: 카메라 제조사 MAC (${device.mac})")
        }

        // RTSP 포트(554) 개방 (+30점)
        if (device.openPorts.contains(PORT_RTSP)) {
            score += SCORE_RTSP_PORT
            Timber.d("위험도 +$SCORE_RTSP_PORT: RTSP 포트 개방 (${device.ip})")
        }

        // HTTP 스트리밍 포트 개방 (+15점)
        val hasHttpPort = device.openPorts.any { it in HTTP_PORTS }
        if (hasHttpPort) {
            score += SCORE_HTTP_PORT
            Timber.d("위험도 +$SCORE_HTTP_PORT: HTTP 포트 개방 (${device.ip})")
        }

        // ONVIF 포트(3702) 개방 (+20점)
        if (device.openPorts.contains(PORT_ONVIF)) {
            score += SCORE_ONVIF_PORT
            Timber.d("위험도 +$SCORE_ONVIF_PORT: ONVIF 포트 개방 (${device.ip})")
        }

        // 호스트명에 카메라 키워드 포함 (+15점)
        val hostname = device.hostname?.lowercase() ?: ""
        if (CAMERA_HOSTNAME_KEYWORDS.any { hostname.contains(it) }) {
            score += SCORE_CAMERA_HOSTNAME
            Timber.d("위험도 +$SCORE_CAMERA_HOSTNAME: 카메라 키워드 호스트명 ($hostname)")
        }

        // mDNS _rtsp 서비스 광고 (+25점)
        if (device.services.any { it.contains("_rtsp", ignoreCase = true) }) {
            score += SCORE_MDNS_RTSP
            Timber.d("위험도 +$SCORE_MDNS_RTSP: mDNS RTSP 서비스 광고 (${device.ip})")
        }

        val finalScore = score.coerceIn(0, MAX_SCORE)
        val isCamera = finalScore >= CAMERA_THRESHOLD

        Timber.d("위험도 계산 완료: ${device.ip} → $finalScore점 (카메라=$isCamera)")

        // 불변성 준수: copy()로 새 객체 반환
        return device.copy(
            riskScore = finalScore,
            isCamera = isCamera,
        )
    }

    companion object {
        /** 최대 위험도 점수 */
        private const val MAX_SCORE = 100

        /** 카메라 판정 임계값 */
        private const val CAMERA_THRESHOLD = 40

        // 포트 번호 상수
        private const val PORT_RTSP = 554
        private const val PORT_ONVIF = 3702

        /** HTTP 스트리밍 포트 목록 */
        private val HTTP_PORTS = setOf(80, 8080, 8888)

        // 점수 배정
        private const val SCORE_CAMERA_VENDOR = 40
        private const val SCORE_RTSP_PORT = 30
        private const val SCORE_HTTP_PORT = 15
        private const val SCORE_ONVIF_PORT = 20
        private const val SCORE_CAMERA_HOSTNAME = 15
        private const val SCORE_MDNS_RTSP = 25

        /** 카메라 관련 호스트명 키워드 (소문자) */
        private val CAMERA_HOSTNAME_KEYWORDS = listOf(
            "cam", "ipc", "dvr", "nvr", "stream", "video",
            "hikvision", "dahua", "axis", "reolink", "ipcam",
        )
    }
}
