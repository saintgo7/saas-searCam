package com.searcam.data.sensor

import com.searcam.util.Constants
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import timber.log.Timber
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject

/**
 * TCP 포트 스캐너 — 카메라 서비스 포트 개방 여부 확인
 *
 * 비유: 건물에서 열린 문을 확인하듯, 기기의 각 포트에 연결을 시도하여
 * 어떤 서비스가 실행 중인지 확인한다.
 *
 * 대상 포트 (Constants.CAMERA_PORTS):
 *   554  — RTSP (실시간 스트리밍, 카메라 핵심 지표)
 *   80   — HTTP
 *   8080 — HTTP 대체
 *   8888 — HTTP 대체
 *   3702 — WS-Discovery (ONVIF)
 *   1935 — RTMP (스트리밍)
 *   443  — HTTPS
 *   8443 — HTTPS 대체
 *
 * 병렬 스캔으로 전체 포트를 동시에 확인한다.
 */
class PortScanner @Inject constructor() {

    /**
     * 지정된 IP 주소의 카메라 관련 포트를 병렬로 스캔한다.
     *
     * coroutineScope + async로 모든 포트를 동시에 연결 시도한다.
     * 각 포트당 타임아웃은 Constants.PORT_SCAN_TIMEOUT_MS (기본 500ms)이다.
     *
     * @param ip 스캔 대상 IPv4 주소 (예: "192.168.1.45")
     * @return 개방된 포트 목록 (예: [554, 80])
     */
    suspend fun scanPorts(ip: String): List<Int> = withContext(Dispatchers.IO) {
        // 보안: 사설 IP 범위(RFC 1918)만 스캔 허용 — 외부 인터넷 스캔 방지
        if (!isPrivateIp(ip)) {
            Timber.w("[${Constants.ErrorCode.E2004}] 사설 IP 범위 외 스캔 차단: $ip")
            return@withContext emptyList()
        }
        return@withContext try {
            coroutineScope {
                Constants.CAMERA_PORTS
                    .map { port ->
                        async {
                            if (isPortOpen(ip, port)) port else null
                        }
                    }
                    .awaitAll()
                    .filterNotNull()
            }
        } catch (e: Exception) {
            Timber.e(e, "[${Constants.ErrorCode.E2004}] 포트 스캔 오류: $ip")
            emptyList()
        }
    }

    /**
     * 단일 포트의 개방 여부를 확인한다.
     *
     * TCP 소켓 연결 시도로 포트가 열려 있는지 판단한다.
     * 연결 거부(E2004) 또는 타임아웃(E2005) 시 false를 반환한다.
     *
     * @param ip 대상 IP 주소
     * @param port 확인할 포트 번호
     * @return 포트가 열려 있으면 true
     */
    /**
     * RFC 1918 사설 IP 범위 여부를 확인한다.
     * - 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 127.0.0.0/8(루프백)
     */
    internal fun isPrivateIp(ip: String): Boolean {
        return try {
            val parts = ip.split(".").map { it.toInt() }
            if (parts.size != 4) return false
            val (a, b, _, _) = parts
            a == 10 ||
                a == 127 ||
                (a == 172 && b in 16..31) ||
                (a == 192 && b == 168)
        } catch (e: Exception) {
            false
        }
    }

    internal suspend fun isPortOpen(ip: String, port: Int): Boolean {
        return withTimeoutOrNull(Constants.PORT_SCAN_TIMEOUT_MS) {
            try {
                Socket().use { socket ->
                    socket.connect(
                        InetSocketAddress(ip, port),
                        Constants.PORT_SCAN_TIMEOUT_MS.toInt(),
                    )
                    true
                }
            } catch (e: java.net.ConnectException) {
                // E2004: 연결 거부 — 포트 닫힘
                Timber.v("[${Constants.ErrorCode.E2004}] 연결 거부: $ip:$port")
                false
            } catch (e: java.net.SocketTimeoutException) {
                // E2005: 타임아웃 — 방화벽 또는 무응답
                Timber.v("[${Constants.ErrorCode.E2005}] 타임아웃: $ip:$port")
                false
            } catch (e: Exception) {
                Timber.v("포트 스캔 오류 $ip:$port — ${e.message}")
                false
            }
        } ?: false
    }
}
