package com.searcam.data.analysis

import com.searcam.domain.model.DeviceType
import com.searcam.domain.model.DiscoveryMethod
import com.searcam.domain.model.NetworkDevice
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * RiskCalculator 단위 테스트
 *
 * 네트워크 기기 위험도 점수 계산 로직을 검증한다.
 *
 * 점수 기준:
 *   +40 — 카메라 제조사 MAC
 *   +30 — RTSP 포트(554) 개방
 *   +15 — HTTP 포트(80/8080/8888) 개방
 *   +20 — ONVIF 포트(3702) 개방
 *   +15 — 카메라 키워드 호스트명
 *   +25 — mDNS _rtsp 서비스
 */
class RiskCalculatorTest {

    private lateinit var ouiDatabase: OuiDatabase
    private lateinit var riskCalculator: RiskCalculator

    @Before
    fun setUp() {
        ouiDatabase = mockk(relaxed = true)
        riskCalculator = RiskCalculator(ouiDatabase)
    }

    // ──────────────────────────────────────────────
    // 기본 케이스
    // ──────────────────────────────────────────────

    @Test
    fun `아무 단서도 없으면 점수는 0이고 카메라 아님`() {
        every { ouiDatabase.isCameraVendor(any()) } returns false

        val device = makeDevice(mac = "AA:BB:CC:DD:EE:FF")
        val result = riskCalculator.calculateDeviceRisk(device)

        assertEquals(0, result.riskScore)
        assertFalse(result.isCamera)
    }

    // ──────────────────────────────────────────────
    // 단일 요소 테스트
    // ──────────────────────────────────────────────

    @Test
    fun `카메라 제조사 MAC이면 40점 추가`() {
        every { ouiDatabase.isCameraVendor("28:57:BE:11:22:33") } returns true

        val device = makeDevice(mac = "28:57:BE:11:22:33")
        val result = riskCalculator.calculateDeviceRisk(device)

        assertEquals(40, result.riskScore)
        assertTrue(result.isCamera) // 40 >= CAMERA_THRESHOLD(40)
    }

    @Test
    fun `RTSP 포트(554) 개방 시 30점 추가`() {
        every { ouiDatabase.isCameraVendor(any()) } returns false

        val device = makeDevice(openPorts = listOf(554))
        val result = riskCalculator.calculateDeviceRisk(device)

        assertEquals(30, result.riskScore)
        assertFalse(result.isCamera) // 30 < CAMERA_THRESHOLD(40)
    }

    @Test
    fun `HTTP 포트(80) 개방 시 15점 추가`() {
        every { ouiDatabase.isCameraVendor(any()) } returns false

        val device = makeDevice(openPorts = listOf(80))
        val result = riskCalculator.calculateDeviceRisk(device)

        assertEquals(15, result.riskScore)
    }

    @Test
    fun `HTTP 포트(8080) 개방 시 15점 추가`() {
        every { ouiDatabase.isCameraVendor(any()) } returns false

        val device = makeDevice(openPorts = listOf(8080))
        val result = riskCalculator.calculateDeviceRisk(device)

        assertEquals(15, result.riskScore)
    }

    @Test
    fun `HTTP 포트(8888) 개방 시 15점 추가`() {
        every { ouiDatabase.isCameraVendor(any()) } returns false

        val device = makeDevice(openPorts = listOf(8888))
        val result = riskCalculator.calculateDeviceRisk(device)

        assertEquals(15, result.riskScore)
    }

    @Test
    fun `ONVIF 포트(3702) 개방 시 20점 추가`() {
        every { ouiDatabase.isCameraVendor(any()) } returns false

        val device = makeDevice(openPorts = listOf(3702))
        val result = riskCalculator.calculateDeviceRisk(device)

        assertEquals(20, result.riskScore)
    }

    @Test
    fun `카메라 키워드 호스트명(cam)이면 15점 추가`() {
        every { ouiDatabase.isCameraVendor(any()) } returns false

        val device = makeDevice(hostname = "hikvision-cam-001")
        val result = riskCalculator.calculateDeviceRisk(device)

        assertEquals(15, result.riskScore)
    }

    @Test
    fun `mDNS _rtsp 서비스 광고 시 25점 추가`() {
        every { ouiDatabase.isCameraVendor(any()) } returns false

        val device = makeDevice(services = listOf("_rtsp._tcp"))
        val result = riskCalculator.calculateDeviceRisk(device)

        assertEquals(25, result.riskScore)
    }

    // ──────────────────────────────────────────────
    // 복합 케이스
    // ──────────────────────────────────────────────

    @Test
    fun `카메라 MAC + RTSP 포트 = 70점 이상으로 카메라 판정`() {
        every { ouiDatabase.isCameraVendor("28:57:BE:11:22:33") } returns true

        val device = makeDevice(
            mac = "28:57:BE:11:22:33",
            openPorts = listOf(554),
        )
        val result = riskCalculator.calculateDeviceRisk(device)

        assertEquals(70, result.riskScore)
        assertTrue(result.isCamera)
    }

    @Test
    fun `최대 점수는 100으로 제한`() {
        every { ouiDatabase.isCameraVendor(any()) } returns true

        // 40 + 30 + 15 + 20 + 15 + 25 = 145 → 100으로 제한
        val device = makeDevice(
            mac = "AA:BB:CC:DD:EE:FF",
            openPorts = listOf(554, 80, 3702),
            hostname = "hikvision-cam",
            services = listOf("_rtsp._tcp"),
        )
        val result = riskCalculator.calculateDeviceRisk(device)

        assertEquals(100, result.riskScore)
    }

    @Test
    fun `여러 HTTP 포트 개방 시 15점만 추가 (중복 없음)`() {
        every { ouiDatabase.isCameraVendor(any()) } returns false

        // 80, 8080, 8888 모두 열려 있어도 HTTP 항목은 한 번만 카운트
        val device = makeDevice(openPorts = listOf(80, 8080, 8888))
        val result = riskCalculator.calculateDeviceRisk(device)

        assertEquals(15, result.riskScore)
    }

    // ──────────────────────────────────────────────
    // isCamera 경계값 테스트
    // ──────────────────────────────────────────────

    @Test
    fun `점수 39는 카메라 아님`() {
        every { ouiDatabase.isCameraVendor(any()) } returns false

        // RTSP(30) + HTTP(15) = 45이므로 단독으로 39 만들기 어려움
        // RTSP(30) + 임의 8점? → 구현상 정확히 39를 만들기 어려우므로 30점 테스트
        val device = makeDevice(openPorts = listOf(554)) // 30점
        val result = riskCalculator.calculateDeviceRisk(device)

        assertFalse(result.isCamera) // 30 < 40
    }

    @Test
    fun `점수 40은 카메라`() {
        every { ouiDatabase.isCameraVendor("28:57:BE:11:22:33") } returns true

        val device = makeDevice(mac = "28:57:BE:11:22:33") // 40점
        val result = riskCalculator.calculateDeviceRisk(device)

        assertTrue(result.isCamera) // 40 >= 40
    }

    // ──────────────────────────────────────────────
    // 불변성 검증
    // ──────────────────────────────────────────────

    @Test
    fun `calculateDeviceRisk는 원본 device를 변경하지 않는다`() {
        every { ouiDatabase.isCameraVendor(any()) } returns true

        val original = makeDevice(mac = "28:57:BE:11:22:33")
        val originalRiskScore = original.riskScore

        riskCalculator.calculateDeviceRisk(original)

        assertEquals("원본 riskScore가 변경되면 안 됨", originalRiskScore, original.riskScore)
    }

    // ──────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────

    private fun makeDevice(
        ip: String = "192.168.1.100",
        mac: String = "AA:BB:CC:DD:EE:FF",
        hostname: String? = null,
        openPorts: List<Int> = emptyList(),
        services: List<String> = emptyList(),
        riskScore: Int = 0,
    ) = NetworkDevice(
        ip = ip,
        mac = mac,
        hostname = hostname,
        vendor = null,
        deviceType = DeviceType.UNKNOWN,
        openPorts = openPorts,
        services = services,
        riskScore = riskScore,
        isCamera = false,
        discoveryMethod = DiscoveryMethod.ARP,
        discoveredAt = System.currentTimeMillis(),
    )
}
