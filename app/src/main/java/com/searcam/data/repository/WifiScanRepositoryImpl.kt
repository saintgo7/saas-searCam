package com.searcam.data.repository

import com.searcam.data.analysis.OuiDatabase
import com.searcam.data.analysis.RiskCalculator
import com.searcam.data.sensor.PortScanner
import com.searcam.data.sensor.WifiScanner
import com.searcam.domain.model.DeviceType
import com.searcam.domain.model.NetworkDevice
import com.searcam.domain.repository.WifiScanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wi-Fi 네트워크 스캔 저장소 구현체
 *
 * 비유: 빌딩 종합 안내 데스크처럼, 여러 탐지 채널(Wi-Fi Scanner,
 * Port Scanner, OUI Database, Risk Calculator)로부터 정보를 모아
 * 하나의 통합 결과를 제공한다.
 *
 * 실행 파이프라인:
 *   1. WifiScanner.scan()  — ARP + mDNS + SSDP로 기기 목록 수집
 *   2. OuiDatabase         — MAC OUI로 제조사 식별
 *   3. PortScanner         — 의심 기기 포트 스캔
 *   4. RiskCalculator      — 위험도 0~100 산출
 *   5. 결과 정렬           — 위험도 내림차순
 *
 * @param wifiScanner    ARP/mDNS/SSDP 스캐너
 * @param portScanner    TCP 포트 스캐너
 * @param ouiDatabase    OUI 제조사 데이터베이스
 * @param riskCalculator 위험도 계산기
 */
@Singleton
class WifiScanRepositoryImpl @Inject constructor(
    private val wifiScanner: WifiScanner,
    private val portScanner: PortScanner,
    private val ouiDatabase: OuiDatabase,
    private val riskCalculator: RiskCalculator,
) : WifiScanRepository {

    // 발견된 기기 목록 — StateFlow로 실시간 관찰 지원
    private val _devices = MutableStateFlow<List<NetworkDevice>>(emptyList())

    /**
     * 네트워크 스캔을 실행하고 발견된 기기 목록을 반환한다.
     *
     * ARP/mDNS/SSDP 탐색 → OUI 매칭 → 포트 스캔 → 위험도 계산 →
     * 위험도 내림차순 정렬 순으로 진행된다.
     *
     * @return Result.success(위험도 내림차순 기기 목록) 또는 Result.failure(exception)
     */
    override suspend fun scanDevices(): Result<List<NetworkDevice>> {
        return try {
            Timber.d("Wi-Fi 스캔 시작")

            // STEP 1: Wi-Fi 스캔 (ARP + mDNS + SSDP)
            val rawDevices = wifiScanner.scan()
            if (rawDevices.isEmpty()) {
                Timber.d("스캔 결과 없음 (Wi-Fi 미연결 또는 기기 없음)")
                _devices.value = emptyList()
                return Result.success(emptyList())
            }

            // STEP 2: OUI 매칭으로 제조사 및 기기 유형 식별
            val devicesWithVendor = rawDevices.map { device ->
                enrichWithOui(device)
            }

            // STEP 3: 의심 기기만 포트 스캔 (안전 기기는 스킵하여 성능 최적화)
            val devicesWithPorts = devicesWithVendor.map { device ->
                if (shouldScanPorts(device)) {
                    val openPorts = portScanner.scanPorts(device.ip)
                    Timber.d("포트 스캔 완료: ${device.ip} → 개방 포트 $openPorts")
                    device.copy(openPorts = openPorts)
                } else {
                    Timber.d("포트 스캔 스킵: ${device.ip} (안전 기기)")
                    device
                }
            }

            // STEP 4: 기기별 위험도 산출
            val devicesWithRisk = devicesWithPorts.map { device ->
                riskCalculator.calculateDeviceRisk(device)
            }

            // STEP 5: 위험도 내림차순 정렬
            val sorted = devicesWithRisk.sortedByDescending { it.riskScore }

            // StateFlow 업데이트
            _devices.value = sorted

            Timber.d(
                "스캔 완료: ${sorted.size}개 기기, " +
                    "카메라 의심: ${sorted.count { it.isCamera }}개"
            )
            Result.success(sorted)
        } catch (e: Exception) {
            Timber.e(e, "Wi-Fi 스캔 실패")
            Result.failure(e)
        }
    }

    /**
     * 네트워크 기기 목록을 실시간으로 관찰하는 Flow를 반환한다.
     *
     * scanDevices() 호출 시마다 업데이트된 전체 목록을 emit한다.
     *
     * @return 기기 목록 StateFlow
     */
    override fun observeDevices(): Flow<List<NetworkDevice>> {
        return _devices.asStateFlow()
    }

    /**
     * ARP 테이블만 직접 조회하여 기기 목록을 반환한다.
     *
     * mDNS/SSDP/포트 스캔 없이 ARP 결과만 반환하므로 scanDevices()보다 빠르다.
     *
     * @return Result.success(ARP 기기 목록) 또는 Result.failure(exception)
     */
    override suspend fun getArpTable(): Result<List<NetworkDevice>> {
        return try {
            val arpDevices = wifiScanner.scanArp()
            val enriched = arpDevices.map { enrichWithOui(it) }
            Timber.d("ARP 테이블 조회 완료: ${enriched.size}개")
            Result.success(enriched)
        } catch (e: Exception) {
            Timber.e(e, "ARP 테이블 조회 실패")
            Result.failure(e)
        }
    }

    /**
     * OUI 데이터베이스로 기기의 제조사와 타입을 보강한다.
     *
     * MAC 주소가 없거나 미등록 OUI인 경우 원본 기기를 그대로 반환한다.
     *
     * @param device 보강할 NetworkDevice
     * @return vendor와 deviceType이 갱신된 불변 복사본
     */
    private fun enrichWithOui(device: NetworkDevice): NetworkDevice {
        if (device.mac.isBlank()) return device

        val vendor = ouiDatabase.getVendor(device.mac)
        val deviceType = if (ouiDatabase.isCameraVendor(device.mac)) {
            DeviceType.IP_CAMERA
        } else {
            device.deviceType
        }

        return device.copy(
            vendor = vendor,
            deviceType = deviceType,
        )
    }

    /**
     * 기기에 대해 포트 스캔을 수행할지 결정한다.
     *
     * 카메라 제조사 MAC이거나 기기 유형이 미확인인 경우 스캔한다.
     * 명백한 안전 기기(스마트폰, TV 등)는 포트 스캔을 스킵한다.
     *
     * @param device 판단할 NetworkDevice
     * @return 포트 스캔을 해야 하면 true
     */
    private fun shouldScanPorts(device: NetworkDevice): Boolean {
        // 카메라 제조사 MAC이면 무조건 스캔
        if (device.deviceType == DeviceType.IP_CAMERA ||
            device.deviceType == DeviceType.SMART_CAMERA
        ) return true

        // mDNS로 RTSP 서비스가 발견된 기기는 스캔
        if (device.services.any { it.contains("_rtsp", ignoreCase = true) }) return true

        // 기기 유형 미식별이면 스캔
        if (device.deviceType == DeviceType.UNKNOWN) return true

        // 안전 기기(스마트폰, TV, 공유기 등)는 스킵
        return false
    }
}
