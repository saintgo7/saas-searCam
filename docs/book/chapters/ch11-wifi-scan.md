# Ch11: Wi-Fi 스캔 — 네트워크는 거짓말을 하지 않는다

> **이 장에서 배울 것**: 탐지 가중치의 50%를 차지하는 Wi-Fi 스캔 레이어의 원리와 구현을 배웁니다. ARP 테이블에서 OUI 식별, mDNS 탐색, 포트 스캔까지 — 같은 네트워크에 연결된 IP 카메라를 어떻게 찾는지 보여줍니다.

---

## 도입

범죄 수사에서 형사는 현장 주변 CCTV를 가장 먼저 확인합니다. 용의자가 직접 자백하길 기다리는 대신, 흔적을 추적하는 거죠. 몰래카메라도 마찬가지입니다. 카메라는 잘 숨길 수 있지만, 네트워크 흔적은 숨기기 어렵습니다.

Wi-Fi에 연결된 IP 카메라는 반드시 네트워크에 흔적을 남깁니다. MAC 주소, ARP 테이블 항목, 그리고 특정 포트(554 RTSP, 8080 HTTP)가 열려 있습니다. SearCam은 이 흔적을 쫓습니다.

---

## 11.1 Wi-Fi 스캔이 탐지의 핵심인 이유

### 가중치 50%의 근거

세 탐지 레이어 중 Wi-Fi가 가장 높은 비중을 차지하는 이유는 두 가지입니다.

첫째, **현대 몰래카메라의 대부분이 IP 카메라**입니다. SD 카드에만 저장하는 구식 장치와 달리, 요즘 범죄에 사용되는 카메라는 실시간 원격 모니터링을 위해 Wi-Fi에 연결됩니다. 네트워크 연결이 오히려 약점이 됩니다.

둘째, **오탐률이 낮습니다.** 자기장 센서는 일반 가전제품에도 반응하고, 렌즈 감지는 유리 반사에도 반응합니다. 하지만 카메라 제조사 OUI를 가진 기기가 카메라 포트를 열고 있다면 — 이건 구체적인 증거입니다.

### 탐지 가능 범위와 한계

```
Wi-Fi 스캔으로 탐지 가능:
  ✅ 같은 Wi-Fi 네트워크에 연결된 IP 카메라
  ✅ WPA/WPA2/WPA3 네트워크 내 ARP 등록 기기
  ✅ mDNS/SSDP로 광고하는 스마트 카메라

Wi-Fi 스캔으로 탐지 불가능:
  ❌ 다른 Wi-Fi 네트워크나 LTE로 송출하는 카메라
  ❌ Wi-Fi 꺼짐 상태의 배터리 방식 카메라
  ❌ 전원이 꺼진 카메라
  ❌ 사용자가 같은 네트워크에 연결하지 않은 경우
```

이 한계를 솔직하게 UI에 표시하는 것이 SearCam의 차별점입니다.

---

## 11.2 ARP 테이블 파싱 원리

### ARP가 무엇인가

ARP(Address Resolution Protocol)는 IP 주소를 MAC 주소로 변환하는 프로토콜입니다. 스마트폰이 라우터를 통해 같은 네트워크 기기와 통신할 때마다 ARP 교환이 일어나고, 그 결과가 `/proc/net/arp` 파일에 기록됩니다.

이 파일은 Android에서 root 권한 없이도 읽을 수 있습니다. 운영체제가 일반 앱에게도 ARP 테이블 조회를 허용하기 때문입니다.

```bash
# /proc/net/arp 파일 형식 예시
IP address       HW type     Flags  HW address            Mask     Device
192.168.1.1      0x1         0x2    aa:bb:cc:11:22:33     *        wlan0
192.168.1.105    0x1         0x2    00:12:bf:45:67:89     *        wlan0
192.168.1.110    0x1         0x2    fc:f5:c4:ab:cd:ef     *        wlan0
```

여기서 `00:12:bf` — MAC 앞 3바이트(OUI) — 를 제조사 데이터베이스와 대조하면 기기 제조사를 알 수 있습니다. `00:12:bf`는 Hikvision(IP 카메라 제조사)의 OUI입니다.

### ARP 파싱 구현

```kotlin
// data/sensor/WifiScanner.kt (ARP 파싱 부분)
class WifiScanner @Inject constructor(
    private val wifiManager: WifiManager,
    private val nsdManager: NsdManager,
    private val ouiDatabase: OuiDatabase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    // ARP 테이블에서 같은 네트워크 기기 목록을 읽어옵니다
    suspend fun readArpTable(): List<ArpEntry> = withContext(ioDispatcher) {
        try {
            File("/proc/net/arp")
                .readLines()
                .drop(1)  // 헤더 행 제거
                .mapNotNull { line -> parseArpLine(line) }
                .filter { entry -> entry.isValid() }
        } catch (e: IOException) {
            // ARP 읽기 실패 시 빈 목록 반환 — 앱이 크래시되지 않습니다
            emptyList()
        }
    }

    // "192.168.1.105  0x1  0x2  00:12:bf:45:67:89  *  wlan0" 형태를 파싱합니다
    private fun parseArpLine(line: String): ArpEntry? {
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size < 6) return null

        val ip = parts[0]
        val flags = parts[2]
        val mac = parts[3]

        // Flags 0x2 = 완전히 해석된(complete) 항목만 사용합니다
        if (flags != "0x2") return null
        // 00:00:00:00:00:00 같은 유효하지 않은 MAC 제외
        if (mac == "00:00:00:00:00:00") return null

        return ArpEntry(ip = ip, mac = mac.uppercase())
    }
}

// ARP 항목의 유효성 검사
data class ArpEntry(val ip: String, val mac: String) {
    fun isValid(): Boolean {
        val ipPattern = Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")
        val macPattern = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")
        return ip.matches(ipPattern) && mac.matches(macPattern)
    }
}
```

---

## 11.3 mDNS(NsdManager)와 SSDP 멀티캐스트 동작

### mDNS로 스마트 기기 탐색하기

mDNS(Multicast DNS)는 로컬 네트워크에서 서버 없이 서비스를 광고하고 탐색하는 프로토콜입니다. 애플 기기의 AirPrint, Chromecast가 mDNS를 사용합니다. IP 카메라도 `_rtsp._tcp.` 또는 `_http._tcp.` 서비스를 mDNS로 광고하는 경우가 많습니다.

Android의 `NsdManager`가 mDNS 탐색을 제공합니다.

```kotlin
// data/sensor/WifiScanner.kt (mDNS 탐색 부분)

// 카메라가 자주 광고하는 서비스 타입 목록
private val CAMERA_SERVICE_TYPES = listOf(
    "_rtsp._tcp.",    // 실시간 스트리밍 (IP 카메라 표준)
    "_http._tcp.",    // HTTP 웹 인터페이스
    "_dahua._tcp.",   // Dahua 카메라 전용 서비스
    "_hikvision._tcp.",  // Hikvision 카메라 전용 서비스
    "_onvif._tcp."    // ONVIF 표준 프로토콜
)

fun discoverMdnsServices(): Flow<NsdServiceInfo> = callbackFlow {
    val discoveryListeners = mutableListOf<NsdManager.DiscoveryListener>()

    CAMERA_SERVICE_TYPES.forEach { serviceType ->
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                // 서비스를 발견하면 채널로 전송합니다
                trySend(serviceInfo)
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                // 특정 서비스 타입 탐색 실패는 무시하고 계속합니다
            }

            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        nsdManager.discoverServices(
            serviceType,
            NsdManager.PROTOCOL_DNS_SD,
            listener
        )
        discoveryListeners.add(listener)
    }

    // Flow가 취소되면 모든 탐색을 중지합니다
    awaitClose {
        discoveryListeners.forEach { listener ->
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (e: IllegalArgumentException) {
                // 이미 중지된 탐색 — 무시합니다
            }
        }
    }
}
```

### SSDP 멀티캐스트로 UPnP 기기 탐색

SSDP(Simple Service Discovery Protocol)는 UPnP 기기(스마트 TV, IP 카메라 등)가 자신의 존재를 알리는 프로토콜입니다. 멀티캐스트 주소 `239.255.255.250:1900`에 M-SEARCH 패킷을 보내면 UPnP 기기들이 응답합니다.

```kotlin
// SSDP M-SEARCH 요청을 보내고 응답을 받습니다
suspend fun discoverSsdpDevices(): List<String> = withContext(ioDispatcher) {
    val foundDevices = mutableListOf<String>()

    // SSDP 검색 메시지 — 모든 UPnP 기기에게 "너 거기 있어?" 묻는 형식입니다
    val searchMessage = """
        M-SEARCH * HTTP/1.1
        HOST: 239.255.255.250:1900
        MAN: "ssdp:discover"
        MX: 3
        ST: ssdp:all
        
    """.trimIndent()

    try {
        val socket = DatagramSocket()
        socket.soTimeout = 3000  // 3초 응답 대기

        val group = InetAddress.getByName("239.255.255.250")
        val packet = DatagramPacket(
            searchMessage.toByteArray(),
            searchMessage.length,
            group,
            1900
        )
        socket.send(packet)

        // 응답 수신 루프
        val buffer = ByteArray(2048)
        val responsePacket = DatagramPacket(buffer, buffer.size)

        while (true) {
            try {
                socket.receive(responsePacket)
                val response = String(responsePacket.data, 0, responsePacket.length)
                val location = extractLocation(response)  // LOCATION 헤더 추출
                if (location != null) foundDevices.add(location)
            } catch (e: SocketTimeoutException) {
                break  // 타임아웃 = 더 이상 응답 없음
            }
        }

        socket.close()
    } catch (e: Exception) {
        // 네트워크 오류 — 빈 목록 반환
    }

    foundDevices
}
```

---

## 11.4 OUI 데이터베이스로 제조사 식별하기

### OUI란 무엇인가

MAC 주소(예: `00:12:BF:45:67:89`)에서 앞 3바이트(`00:12:BF`)가 OUI(Organizationally Unique Identifier)입니다. IEEE가 제조사에게 고유하게 할당합니다. `00:12:BF`는 Hikvision Digital Technology — 세계 최대 IP 카메라 제조사입니다.

SearCam은 약 500KB 크기의 OUI 데이터베이스를 앱에 번들로 포함합니다. 인터넷 없이 오프라인으로 제조사를 식별할 수 있습니다.

```kotlin
// data/analysis/OuiDatabase.kt
class OuiDatabase @Inject constructor(
    @ApplicationContext private val context: Context,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher
) {
    // 카메라 관련 OUI 목록 — 앱 assets에서 로드합니다
    private val cameraVendorOuis: Set<String> by lazy {
        loadCameraOuis()
    }

    // 카메라 제조사로 알려진 OUI 집합을 assets에서 로드합니다
    private fun loadCameraOuis(): Set<String> {
        return try {
            context.assets.open("oui_camera.json").use { stream ->
                val json = stream.bufferedReader().readText()
                // JSON 파싱 — Set으로 변환해 O(1) 검색 성능 확보
                parseOuiJson(json).toSet()
            }
        } catch (e: IOException) {
            emptySet()  // assets 로드 실패 시 탐지 없이 계속 진행
        }
    }

    // MAC 주소에서 OUI를 추출하고 카메라 제조사인지 판단합니다
    fun isCameraVendor(macAddress: String): Boolean {
        val oui = macAddress.take(8).uppercase()  // "AA:BB:CC" 형태
        return oui in cameraVendorOuis
    }

    // 제조사 이름을 반환합니다 (UI 표시용)
    fun getVendorName(macAddress: String): String? {
        val oui = macAddress.take(8).uppercase()
        return vendorMap[oui]  // null이면 알 수 없는 제조사
    }
}

// 알려진 카메라 제조사 OUI 예시 (실제는 수백 개)
val KNOWN_CAMERA_OUIS = mapOf(
    "00:12:BF" to "Hikvision",
    "BC:AD:28" to "Hikvision",
    "A4:14:37" to "Dahua Technology",
    "E0:50:8B" to "Dahua Technology",
    "00:40:8C" to "Axis Communications",
    "AC:CC:8E" to "Reolink",
    "D4:F5:27" to "TP-Link (카메라 라인)",
    "B0:A7:B9" to "Wyze Labs",
    "2C:AA:8E" to "Arlo Technologies"
)
```

---

## 11.5 포트 스캐닝 — 카메라 서비스 찾기

### 카메라가 여는 포트

IP 카메라는 특정 포트로 서비스를 제공합니다. 이 포트가 열려 있으면 IP 카메라일 가능성이 높아집니다.

| 포트 | 프로토콜 | 의미 |
|------|---------|------|
| 554 | RTSP | 실시간 스트리밍 (IP 카메라 표준) |
| 8554 | RTSP | RTSP 대체 포트 |
| 80 | HTTP | 웹 관리 인터페이스 |
| 8080 | HTTP | 대체 웹 포트 |
| 8888 | HTTP | 제조사별 대체 포트 |
| 37777 | Dahua | Dahua 카메라 전용 |
| 8000 | Hikvision | Hikvision SDK 포트 |

```kotlin
// data/sensor/PortScanner.kt
class PortScanner @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    companion object {
        // 카메라 관련 포트 목록 — 우선순위 순 정렬
        val CAMERA_PORTS = listOf(554, 8554, 80, 8080, 8888, 37777, 8000, 443)
        const val PORT_TIMEOUT_MS = 2000  // 포트당 2초 대기
    }

    // 특정 IP의 카메라 포트를 병렬로 스캔합니다
    suspend fun scanCameraPorts(ip: String): List<Int> =
        withContext(ioDispatcher) {
            // 포트들을 병렬로 스캔 — 순차 스캔이면 8포트 × 2초 = 16초 걸립니다
            CAMERA_PORTS.map { port ->
                async {
                    if (isPortOpen(ip, port)) port else null
                }
            }.awaitAll().filterNotNull()
        }

    // TCP 연결로 포트 개방 여부를 확인합니다
    private suspend fun isPortOpen(ip: String, port: Int): Boolean =
        withContext(ioDispatcher) {
            try {
                Socket().use { socket ->
                    socket.connect(
                        InetSocketAddress(ip, port),
                        PORT_TIMEOUT_MS
                    )
                    true  // 연결 성공 = 포트 열림
                }
            } catch (e: Exception) {
                false  // 연결 실패 = 포트 닫힘 또는 필터링
            }
        }

    // RTSP 포트가 열린 기기는 IP 카메라 가능성이 매우 높습니다
    fun hasRtspPort(openPorts: List<Int>): Boolean =
        openPorts.any { it == 554 || it == 8554 }
}
```

---

## 11.6 Android 12+ 스캔 쓰로틀링 해결 전략

### 쓰로틀링 문제

Android 8.0부터 Wi-Fi 스캔 횟수 제한이 도입되었고, Android 10 이상에서는 더 강화되었습니다. 앱이 2분에 4번 이상 `startScan()`을 호출하면 시스템이 캐시된 결과를 반환합니다(실제 스캔 안 함).

SearCam은 이 제한을 우회하는 게 아니라 다른 방식으로 접근합니다.

```kotlin
// Wi-Fi 스캔 쓰로틀링 해결 전략
class WifiScanRepositoryImpl @Inject constructor(
    private val wifiManager: WifiManager,
    private val wifiScanner: WifiScanner,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : WifiScanRepository {

    override suspend fun scanNetwork(): Flow<List<NetworkDevice>> = flow {
        // 전략 1: WifiManager.startScan() 대신 ARP 테이블 직접 읽기
        // ARP 테이블은 이미 연결된 기기를 보여주므로 스캔 횟수 제한 없음
        val arpDevices = wifiScanner.readArpTable()
        emit(arpDevices.map { it.toNetworkDevice() })

        // 전략 2: 연결된 AP의 DHCP 임대 목록 활용 (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val dhcpInfo = wifiManager.dhcpInfo
            // DHCP 서버 IP에서 네트워크 범위 계산
            val networkPrefix = getNetworkPrefix(dhcpInfo)
            emit(scanNetworkRange(networkPrefix))
        }

        // 전략 3: mDNS 패시브 리슨 — 스캔 없이 광고 패킷 수집
        wifiScanner.discoverMdnsServices()
            .collect { serviceInfo ->
                // mDNS로 발견된 기기를 기존 목록에 병합
                val device = serviceInfo.toNetworkDevice()
                emit(listOf(device))
            }
    }.flowOn(ioDispatcher)
}
```

핵심은 **능동적 스캔 의존도를 줄이는 것**입니다. ARP 테이블은 이미 네트워크에 참여한 기기의 기록이므로, Wi-Fi 스캔 없이도 대부분의 기기를 발견할 수 있습니다.

---

## 11.7 WifiScanRepository 구현 — 전체 흐름

```kotlin
// data/repository/WifiScanRepositoryImpl.kt
class WifiScanRepositoryImpl @Inject constructor(
    private val wifiScanner: WifiScanner,
    private val portScanner: PortScanner,
    private val ouiDatabase: OuiDatabase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : WifiScanRepository {

    // 전체 Wi-Fi 스캔 파이프라인을 실행합니다
    override suspend fun fullScan(): ScanResult = withContext(ioDispatcher) {
        val allDevices = mutableListOf<NetworkDevice>()

        // Step 1: ARP 테이블에서 기기 수집
        val arpEntries = wifiScanner.readArpTable()

        // Step 2: 각 기기에 OUI 매칭 및 포트 스캔을 병렬로 수행합니다
        val enrichedDevices = arpEntries.map { entry ->
            async {
                val vendorName = ouiDatabase.getVendorName(entry.mac)
                val isCameraVendor = ouiDatabase.isCameraVendor(entry.mac)
                val openPorts = portScanner.scanCameraPorts(entry.ip)

                NetworkDevice(
                    ip = entry.ip,
                    mac = entry.mac,
                    vendor = vendorName,
                    openPorts = openPorts,
                    // 카메라 제조사 OUI + RTSP 포트 = 높은 위험도
                    riskScore = calculateDeviceRisk(isCameraVendor, openPorts),
                    isCamera = isCameraVendor || portScanner.hasRtspPort(openPorts)
                )
            }
        }.awaitAll()

        allDevices.addAll(enrichedDevices)

        // Step 3: mDNS로 추가 기기 탐색 (5초 타임아웃)
        val mdnsDevices = wifiScanner.discoverMdnsServices()
            .take(30)  // 최대 30개 서비스
            .toList()
            .map { it.toNetworkDevice() }

        allDevices.addAll(mdnsDevices)

        // Step 4: 위험도 점수를 기반으로 결과 반환
        ScanResult(
            layerType = LayerType.WIFI,
            score = allDevices.maxOfOrNull { it.riskScore } ?: 0,
            maxScore = 100,
            findings = allDevices.filter { it.isCamera }
                .map { device -> Finding("카메라 의심 기기: ${device.ip} (${device.vendor ?: "알 수 없음"})") }
        )
    }

    // 기기 위험도 점수 계산 (0~100)
    private fun calculateDeviceRisk(
        isCameraVendor: Boolean,
        openPorts: List<Int>
    ): Int {
        var score = 0
        if (isCameraVendor) score += 40          // 카메라 제조사 OUI
        if (554 in openPorts) score += 40         // RTSP 포트
        if (8080 in openPorts || 80 in openPorts) score += 15  // HTTP 관리 포트
        if (37777 in openPorts || 8000 in openPorts) score += 25  // 특정 제조사 포트
        return score.coerceAtMost(100)
    }
}
```

---

## 실습

> **실습 11-1**: 집에서 `/proc/net/arp`를 읽어 연결된 모든 기기의 MAC OUI를 확인해보세요. 스마트TV, 라우터, 스마트폰의 OUI가 어떤 제조사로 나오는지 비교해보세요.

> **실습 11-2**: `PortScanner`를 구현하고, 자신의 컴퓨터 IP(127.0.0.1)에서 22(SSH), 80(HTTP), 554(RTSP) 포트를 스캔해보세요. 결과가 예상과 다르다면 이유를 분석해보세요.

---

## 핵심 정리

| 기법 | 역할 | 한계 |
|------|------|------|
| ARP 테이블 파싱 | 연결된 모든 기기 MAC 수집 | 같은 네트워크만 |
| OUI 매칭 | 카메라 제조사 식별 | MAC 스푸핑 불가 탐지 |
| mDNS/SSDP | 스마트 기기 서비스 광고 탐지 | mDNS 끈 기기 불탐지 |
| 포트 스캔 | RTSP/HTTP 서비스 확인 | 방화벽으로 숨긴 경우 |

- Wi-Fi 스캔은 가중치 50% — 가장 구체적인 증거를 제공하기 때문이다
- ARP 테이블은 root 권한 없이 읽을 수 있는 네트워크 흔적이다
- Android 스캔 쓰로틀링은 능동 스캔 대신 ARP/mDNS 패시브 방식으로 우회한다
- 포트 스캔은 기기당 병렬 실행으로 총 소요 시간을 줄인다

---

## 다음 장 예고

네트워크에서 IP 카메라를 찾았습니다. 하지만 Wi-Fi 없는 환경이라면? Ch12에서는 두 번째 탐지 레이어 — 빛의 역반사를 이용해 카메라 렌즈를 물리적으로 찾아내는 방법을 구현합니다.

---
*참고 문서: docs/02-TRD.md, docs/03-TDD.md, docs/04-system-architecture.md*
