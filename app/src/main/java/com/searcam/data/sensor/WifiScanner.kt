package com.searcam.data.sensor

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.searcam.domain.model.DiscoveryMethod
import com.searcam.domain.model.DeviceType
import com.searcam.domain.model.NetworkDevice
import com.searcam.util.Constants
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import timber.log.Timber
import java.io.BufferedReader
import java.io.FileReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.MulticastSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * Wi-Fi 네트워크 스캐너 — Layer 1 탐지
 *
 * 비유: 같은 아파트 인터폰 시스템처럼, 같은 Wi-Fi 네트워크 안에 연결된
 * 모든 기기를 세 가지 방법(ARP, mDNS, SSDP)으로 확인한다.
 *
 * 탐지 순서:
 *   1. ARP 테이블 (/proc/net/arp) — 가장 빠름, 현재 연결 기기
 *   2. mDNS 서비스 탐색 — 호스트명, 서비스 정보 수집
 *   3. SSDP M-SEARCH — UPnP 기기 탐지
 *
 * Android 12+ 스캔 쓰로틀링: 30초 캐시로 재스캔 방지
 */
class WifiScanner @Inject constructor(
    private val context: Context,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val nsdManager =
        context.getSystemService(Context.NSD_SERVICE) as NsdManager

    // 30초 캐시: Android 12+ Wi-Fi 스캔 쓰로틀링 대응
    @Volatile private var cachedDevices: List<NetworkDevice> = emptyList()
    private val lastScanTime = AtomicLong(0L)

    /**
     * 네트워크 스캔을 실행하고 발견된 기기 목록을 반환한다.
     *
     * ARP → mDNS → SSDP 순으로 실행 후 결과를 병합한다.
     * Wi-Fi 미연결 시 E2001 에러를 반환한다.
     *
     * @return 발견된 NetworkDevice 목록 (IP 기준 중복 제거)
     */
    suspend fun scan(): List<NetworkDevice> = withContext(Dispatchers.IO) {
        // Wi-Fi 연결 확인
        if (!isWifiConnected()) {
            Timber.e("[${Constants.ErrorCode.E2001}] Wi-Fi 미연결 상태")
            return@withContext emptyList()
        }

        // 30초 캐시 유효 시 즉시 반환
        val now = System.currentTimeMillis()
        val timeSinceLastScan = now - lastScanTime.get()
        if (timeSinceLastScan < CACHE_TTL_MS && cachedDevices.isNotEmpty()) {
            Timber.d("Wi-Fi 스캔 캐시 사용 (${timeSinceLastScan / 1000}초 경과)")
            return@withContext cachedDevices
        }

        // ARP + mDNS + SSDP 순차 실행 후 IP 기준 병합
        val deviceMap = mutableMapOf<String, NetworkDevice>()

        val arpDevices = scanArp()
        arpDevices.forEach { deviceMap[it.ip] = it }

        val mdnsDevices = scanMdns()
        mdnsDevices.forEach { existing ->
            val prev = deviceMap[existing.ip]
            deviceMap[existing.ip] = if (prev != null) {
                // mDNS 정보(호스트명, 서비스)로 기존 기기 보강
                prev.copy(
                    hostname = existing.hostname ?: prev.hostname,
                    services = (prev.services + existing.services).distinct(),
                    discoveryMethod = DiscoveryMethod.MDNS,
                )
            } else {
                existing
            }
        }

        val ssdpDevices = scanSsdp()
        ssdpDevices.forEach { existing ->
            val prev = deviceMap[existing.ip]
            deviceMap[existing.ip] = if (prev != null) {
                prev.copy(
                    hostname = existing.hostname ?: prev.hostname,
                    services = (prev.services + existing.services).distinct(),
                )
            } else {
                existing
            }
        }

        val result = deviceMap.values.toList()
        cachedDevices = result
        lastScanTime.set(System.currentTimeMillis())

        Timber.d("Wi-Fi 스캔 완료: ${result.size}개 기기 발견")
        result
    }

    /**
     * ARP 테이블을 파싱하여 현재 네트워크 기기 목록을 반환한다.
     *
     * /proc/net/arp 파일 형식:
     *   IP address       HW type  Flags  HW address          Mask  Device
     *   192.168.1.1      0x1      0x2    aa:bb:cc:dd:ee:ff   *     wlan0
     */
    internal suspend fun scanArp(): List<NetworkDevice> = withContext(Dispatchers.IO) {
        return@withContext try {
            val devices = mutableListOf<NetworkDevice>()
            BufferedReader(FileReader(Constants.ARP_TABLE_PATH)).use { reader ->
                // 첫 줄(헤더) 스킵
                reader.readLine()
                var line = reader.readLine()
                while (line != null) {
                    val parsed = parseArpLine(line)
                    if (parsed != null) devices.add(parsed)
                    line = reader.readLine()
                }
            }
            Timber.d("ARP 파싱 완료: ${devices.size}개 항목")
            devices
        } catch (e: Exception) {
            Timber.e(e, "[${Constants.ErrorCode.E2002}] ARP 테이블 읽기 실패")
            emptyList()
        }
    }

    /**
     * ARP 테이블 한 줄을 파싱하여 NetworkDevice로 변환한다.
     *
     * 공백 기준으로 분리: [IP, HWType, Flags, MAC, Mask, Device]
     * 무효 MAC("00:00:00:00:00:00")은 필터링한다.
     */
    private fun parseArpLine(line: String): NetworkDevice? {
        val parts = line.trim().split("\\s+".toRegex())
        if (parts.size < 4) return null

        val ip = parts[0]
        val mac = parts[3].uppercase()

        // 유효하지 않은 MAC 주소 필터링
        if (mac == "00:00:00:00:00:00" || mac.length != 17) return null
        // 유효한 IPv4 주소 확인 (간단 체크)
        if (!ip.matches(IPV4_REGEX)) return null

        return NetworkDevice(
            ip = ip,
            mac = mac,
            hostname = null,
            vendor = null,
            deviceType = DeviceType.UNKNOWN,
            openPorts = emptyList(),
            services = emptyList(),
            riskScore = 0,
            isCamera = false,
            discoveryMethod = DiscoveryMethod.ARP,
            discoveredAt = System.currentTimeMillis(),
        )
    }

    /**
     * mDNS 서비스 탐색으로 카메라 관련 서비스를 광고하는 기기를 찾는다.
     *
     * 탐색 대상 서비스 타입:
     *   _rtsp._tcp  — RTSP 스트리밍 카메라
     *   _http._tcp  — HTTP 서버
     *   _camera._tcp — 카메라 기기
     */
    internal suspend fun scanMdns(): List<NetworkDevice> {
        val discovered = CopyOnWriteArrayList<NetworkDevice>()

        val serviceTypes = listOf("_rtsp._tcp", "_http._tcp", "_camera._tcp")

        for (serviceType in serviceTypes) {
            val result = discoverMdnsService(serviceType)
            discovered.addAll(result)
        }

        Timber.d("mDNS 탐색 완료: ${discovered.size}개 서비스 발견")
        return discovered
    }

    /**
     * 특정 mDNS 서비스 타입을 탐색한다.
     *
     * NsdManager를 사용하여 타임아웃(5초) 내 응답을 수집한다.
     * 탐색 실패 시 E2003 에러를 기록하고 빈 목록을 반환한다.
     */
    private suspend fun discoverMdnsService(serviceType: String): List<NetworkDevice> {
        val devices = CopyOnWriteArrayList<NetworkDevice>()

        return try {
            withTimeoutOrNull(Constants.MDNS_DISCOVERY_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val listener = object : NsdManager.DiscoveryListener {
                        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                            Timber.e("[${Constants.ErrorCode.E2003}] mDNS 탐색 시작 실패: $serviceType (code=$errorCode)")
                            if (continuation.isActive) continuation.resume(Unit)
                        }

                        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                            Timber.w("mDNS 탐색 중지 실패: $serviceType (code=$errorCode)")
                        }

                        override fun onDiscoveryStarted(serviceType: String) {
                            Timber.d("mDNS 탐색 시작: $serviceType")
                        }

                        override fun onDiscoveryStopped(serviceType: String) {
                            Timber.d("mDNS 탐색 중지: $serviceType")
                        }

                        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                            resolveService(serviceInfo, serviceType, devices)
                        }

                        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                            Timber.d("mDNS 서비스 소실: ${serviceInfo.serviceName}")
                        }
                    }

                    continuation.invokeOnCancellation {
                        try {
                            nsdManager.stopServiceDiscovery(listener)
                        } catch (e: Exception) {
                            Timber.w("mDNS 탐색 취소 중 오류: ${e.message}")
                        }
                    }

                    nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
                }
            }
            devices
        } catch (e: Exception) {
            Timber.e(e, "[${Constants.ErrorCode.E2003}] mDNS 탐색 실패: $serviceType")
            emptyList()
        }
    }

    /**
     * NsdServiceInfo를 해석하여 NetworkDevice로 변환한다.
     */
    private fun resolveService(
        serviceInfo: NsdServiceInfo,
        discoveredServiceType: String,
        target: CopyOnWriteArrayList<NetworkDevice>,
    ) {
        try {
            nsdManager.resolveService(
                serviceInfo,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        Timber.w("mDNS 해석 실패: ${serviceInfo.serviceName} (code=$errorCode)")
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val ip = serviceInfo.host?.hostAddress ?: return
                        val device = NetworkDevice(
                            ip = ip,
                            mac = "",
                            hostname = serviceInfo.serviceName,
                            vendor = null,
                            deviceType = DeviceType.UNKNOWN,
                            openPorts = emptyList(),
                            services = listOf(discoveredServiceType),
                            riskScore = 0,
                            isCamera = false,
                            discoveryMethod = DiscoveryMethod.MDNS,
                            discoveredAt = System.currentTimeMillis(),
                        )
                        target.add(device)
                        Timber.d("mDNS 해석 완료: $ip / ${serviceInfo.serviceName}")
                    }
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "mDNS 서비스 해석 오류: ${serviceInfo.serviceName}")
        }
    }

    /**
     * SSDP M-SEARCH로 UPnP 기기를 탐지한다.
     *
     * 멀티캐스트 주소: 239.255.255.250:1900
     * 응답 타임아웃: 3초
     * 실패 시 E2003(SSDP 타임아웃) 에러를 기록한다.
     */
    internal suspend fun scanSsdp(): List<NetworkDevice> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<NetworkDevice>()

        return@withContext try {
            val socket = MulticastSocket(SSDP_PORT)
            socket.soTimeout = SSDP_TIMEOUT_MS.toInt()
            socket.timeToLive = 1 // 링크-로컬 범위 제한 (보안: 로컬 네트워크 외부 유출 방지)

            val groupAddress = InetAddress.getByName(SSDP_MULTICAST_ADDRESS)
            socket.joinGroup(groupAddress)

            // M-SEARCH 패킷 전송
            val searchMessage = buildSsdpMSearch()
            val sendPacket = DatagramPacket(
                searchMessage.toByteArray(),
                searchMessage.length,
                groupAddress,
                SSDP_PORT,
            )
            socket.send(sendPacket)
            Timber.d("SSDP M-SEARCH 전송 완료")

            // 응답 수신 (타임아웃까지 반복)
            val buffer = ByteArray(SSDP_BUFFER_SIZE)
            val receivePacket = DatagramPacket(buffer, buffer.size)

            try {
                while (true) {
                    socket.receive(receivePacket)
                    val responseIp = receivePacket.address.hostAddress ?: continue
                    val responseBody = String(receivePacket.data, 0, receivePacket.length)

                    val device = parseSsdpResponse(responseIp, responseBody)
                    if (device != null) devices.add(device)
                }
            } catch (_: java.net.SocketTimeoutException) {
                // 정상 종료 — 타임아웃
            }

            socket.leaveGroup(groupAddress)
            socket.close()

            Timber.d("SSDP 탐색 완료: ${devices.size}개 응답")
            devices
        } catch (e: Exception) {
            Timber.e(e, "[${Constants.ErrorCode.E2003}] SSDP 탐색 실패")
            emptyList()
        }
    }

    /**
     * SSDP M-SEARCH 요청 메시지를 생성한다.
     */
    private fun buildSsdpMSearch(): String = buildString {
        appendLine("M-SEARCH * HTTP/1.1")
        appendLine("HOST: $SSDP_MULTICAST_ADDRESS:$SSDP_PORT")
        appendLine("MAN: \"ssdp:discover\"")
        appendLine("MX: 3")
        appendLine("ST: ssdp:all")
        appendLine()
    }

    /**
     * SSDP 응답 메시지를 파싱하여 NetworkDevice로 변환한다.
     */
    private fun parseSsdpResponse(ip: String, response: String): NetworkDevice? {
        if (!ip.matches(IPV4_REGEX)) return null

        // LOCATION 헤더에서 호스트명 추출
        val locationLine = response.lines()
            .firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }
        val server = response.lines()
            .firstOrNull { it.startsWith("SERVER:", ignoreCase = true) }
            ?.removePrefix("SERVER:")?.trim()

        val rawHostname = server ?: locationLine?.substringAfter("LOCATION:")?.trim()
        val hostname = rawHostname?.take(128)?.replace(Regex("[\\x00-\\x1F\\x7F]"), "")

        return NetworkDevice(
            ip = ip,
            mac = "",
            hostname = hostname,
            vendor = null,
            deviceType = DeviceType.UNKNOWN,
            openPorts = emptyList(),
            services = listOf("_ssdp._udp"),
            riskScore = 0,
            isCamera = false,
            discoveryMethod = DiscoveryMethod.SSDP,
            discoveredAt = System.currentTimeMillis(),
        )
    }

    /**
     * 현재 Wi-Fi 연결 여부를 확인한다.
     */
    private fun isWifiConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    companion object {
        /** 30초 캐시 TTL (Android 12+ 스캔 쓰로틀링 대응) */
        private const val CACHE_TTL_MS = 30_000L

        /** SSDP 멀티캐스트 주소 */
        private const val SSDP_MULTICAST_ADDRESS = "239.255.255.250"

        /** SSDP 포트 */
        private const val SSDP_PORT = 1900

        /** SSDP 응답 타임아웃 (밀리초) */
        private const val SSDP_TIMEOUT_MS = 3_000L

        /** SSDP 응답 버퍼 크기 */
        private const val SSDP_BUFFER_SIZE = 4096

        /** IPv4 주소 유효성 검사 정규식 */
        private val IPV4_REGEX = Regex(
            "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$"
        )
    }
}
