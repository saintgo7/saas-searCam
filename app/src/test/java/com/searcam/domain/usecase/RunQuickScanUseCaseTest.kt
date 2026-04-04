package com.searcam.domain.usecase

import app.cash.turbine.test
import com.searcam.domain.model.DeviceType
import com.searcam.domain.model.DiscoveryMethod
import com.searcam.domain.model.NetworkDevice
import com.searcam.domain.model.RiskLevel
import com.searcam.domain.model.ScanMode
import com.searcam.domain.repository.WifiScanRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * RunQuickScanUseCase 단위 테스트
 *
 * WifiScanRepository를 Mock하여 Flow 동작과 ScanReport 생성을 검증한다.
 */
class RunQuickScanUseCaseTest {

    private lateinit var wifiScanRepository: WifiScanRepository
    private lateinit var calculateRiskUseCase: CalculateRiskUseCase
    private lateinit var useCase: RunQuickScanUseCase

    @Before
    fun setUp() {
        wifiScanRepository = mockk()
        calculateRiskUseCase = CalculateRiskUseCase()
        useCase = RunQuickScanUseCase(wifiScanRepository, calculateRiskUseCase)
    }

    // ──────────────────────────────────────────────
    // 기본 Flow 동작
    // ──────────────────────────────────────────────

    @Test
    fun `invoke는 ScanReport를 정확히 1번 emit하고 완료된다`() = runTest {
        coEvery { wifiScanRepository.scanDevices() } returns Result.success(emptyList())
        every { wifiScanRepository.observeDevices() } returns flowOf(emptyList())

        useCase().test {
            val report = awaitItem()
            assertNotNull(report)
            awaitComplete()
        }
    }

    @Test
    fun `emit된 ScanReport는 QUICK 모드`() = runTest {
        coEvery { wifiScanRepository.scanDevices() } returns Result.success(emptyList())
        every { wifiScanRepository.observeDevices() } returns flowOf(emptyList())

        useCase().test {
            val report = awaitItem()
            assertEquals(ScanMode.QUICK, report.mode)
            awaitComplete()
        }
    }

    @Test
    fun `빈 기기 목록이면 riskScore는 0`() = runTest {
        coEvery { wifiScanRepository.scanDevices() } returns Result.success(emptyList())
        every { wifiScanRepository.observeDevices() } returns flowOf(emptyList())

        useCase().test {
            val report = awaitItem()
            assertEquals(0, report.riskScore)
            awaitComplete()
        }
    }

    @Test
    fun `빈 기기 목록이면 riskLevel은 SAFE`() = runTest {
        coEvery { wifiScanRepository.scanDevices() } returns Result.success(emptyList())
        every { wifiScanRepository.observeDevices() } returns flowOf(emptyList())

        useCase().test {
            val report = awaitItem()
            assertEquals(RiskLevel.SAFE, report.riskLevel)
            awaitComplete()
        }
    }

    // ──────────────────────────────────────────────
    // 기기 포함 케이스
    // ──────────────────────────────────────────────

    @Test
    fun `고위험 기기가 있으면 riskScore는 양수`() = runTest {
        val highRiskDevice = makeDevice(riskScore = 80, isCamera = true)
        coEvery { wifiScanRepository.scanDevices() } returns Result.success(listOf(highRiskDevice))
        every { wifiScanRepository.observeDevices() } returns flowOf(listOf(highRiskDevice))

        useCase().test {
            val report = awaitItem()
            // 단일 레이어 양성 → 보정 계수 0.7
            // Wi-Fi score=80, weight=0.5 → 80 * 0.5 * 0.7 = 28
            assertTrue("riskScore가 0보다 커야 함", report.riskScore > 0)
            awaitComplete()
        }
    }

    @Test
    fun `emit된 report에 기기 목록이 포함된다`() = runTest {
        val device = makeDevice(riskScore = 50)
        coEvery { wifiScanRepository.scanDevices() } returns Result.success(listOf(device))
        every { wifiScanRepository.observeDevices() } returns flowOf(listOf(device))

        useCase().test {
            val report = awaitItem()
            assertEquals(1, report.devices.size)
            awaitComplete()
        }
    }

    @Test
    fun `riskScore가 최대 기기 점수를 기반으로 계산된다`() = runTest {
        val lowRisk = makeDevice(riskScore = 20)
        val highRisk = makeDevice(riskScore = 80, ip = "192.168.1.200")
        coEvery { wifiScanRepository.scanDevices() } returns Result.success(listOf(lowRisk, highRisk))
        every { wifiScanRepository.observeDevices() } returns flowOf(listOf(lowRisk, highRisk))

        useCase().test {
            val report = awaitItem()
            // layerScore = max(20, 80) = 80 → 80 * 0.5 * 0.7 = 28
            assertEquals(28, report.riskScore)
            awaitComplete()
        }
    }

    // ──────────────────────────────────────────────
    // 실패 케이스
    // ──────────────────────────────────────────────

    @Test
    fun `Wi-Fi 스캔 실패 시 riskScore는 0`() = runTest {
        coEvery { wifiScanRepository.scanDevices() } returns Result.failure(RuntimeException("네트워크 오류"))
        every { wifiScanRepository.observeDevices() } returns flowOf(emptyList())

        useCase().test {
            val report = awaitItem()
            assertEquals(0, report.riskScore)
            awaitComplete()
        }
    }

    @Test
    fun `Wi-Fi 스캔 실패 시 기기 목록은 빈 리스트`() = runTest {
        coEvery { wifiScanRepository.scanDevices() } returns Result.failure(RuntimeException("타임아웃"))
        every { wifiScanRepository.observeDevices() } returns flowOf(emptyList())

        useCase().test {
            val report = awaitItem()
            assertTrue("실패 시 기기 목록은 비어야 함", report.devices.isEmpty())
            awaitComplete()
        }
    }

    // ──────────────────────────────────────────────
    // Report 필드 검증
    // ──────────────────────────────────────────────

    @Test
    fun `report ID는 비어 있지 않다`() = runTest {
        coEvery { wifiScanRepository.scanDevices() } returns Result.success(emptyList())
        every { wifiScanRepository.observeDevices() } returns flowOf(emptyList())

        useCase().test {
            val report = awaitItem()
            assertTrue("report ID가 비어 있으면 안 됨", report.id.isNotEmpty())
            awaitComplete()
        }
    }

    @Test
    fun `startedAt은 completedAt보다 이전이거나 같다`() = runTest {
        coEvery { wifiScanRepository.scanDevices() } returns Result.success(emptyList())
        every { wifiScanRepository.observeDevices() } returns flowOf(emptyList())

        useCase().test {
            val report = awaitItem()
            assertTrue("startedAt <= completedAt 이어야 함", report.startedAt <= report.completedAt)
            awaitComplete()
        }
    }

    @Test
    fun `correctionFactor는 1-0f (양성 레이어 없음)`() = runTest {
        coEvery { wifiScanRepository.scanDevices() } returns Result.success(emptyList())
        every { wifiScanRepository.observeDevices() } returns flowOf(emptyList())

        useCase().test {
            val report = awaitItem()
            assertEquals(1.0f, report.correctionFactor, 0.001f)
            awaitComplete()
        }
    }

    // ──────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────

    private fun makeDevice(
        ip: String = "192.168.1.100",
        riskScore: Int = 0,
        isCamera: Boolean = false,
    ) = NetworkDevice(
        ip = ip,
        mac = "AA:BB:CC:DD:EE:FF",
        hostname = null,
        vendor = null,
        deviceType = DeviceType.UNKNOWN,
        openPorts = emptyList(),
        services = emptyList(),
        riskScore = riskScore,
        isCamera = isCamera,
        discoveryMethod = DiscoveryMethod.ARP,
        discoveredAt = System.currentTimeMillis(),
    )
}
