package com.searcam.domain.repository

import com.searcam.domain.model.NetworkDevice
import kotlinx.coroutines.flow.Flow

/**
 * Wi-Fi 네트워크 스캔 저장소 인터페이스 (Layer 1)
 *
 * ARP 테이블, mDNS, SSDP를 통해 같은 Wi-Fi 네트워크 내 기기를 탐지한다.
 * 구현체는 data 레이어의 WifiScanRepositoryImpl에 위치한다.
 *
 * 보안 주의: 스캔은 반드시 같은 Wi-Fi 서브넷 내로 제한해야 한다.
 */
interface WifiScanRepository {

    /**
     * 네트워크 스캔을 실행하고 발견된 기기 목록을 반환한다.
     *
     * ARP 테이블 조회 → mDNS 탐색 → SSDP 탐색 → OUI 매칭 → 포트 스캔 순으로 실행된다.
     * Wi-Fi 미연결 상태이면 빈 목록을 반환하고 성공으로 종료한다.
     *
     * @return Result.success(devices) 또는 Result.failure(exception)
     */
    suspend fun scanDevices(): Result<List<NetworkDevice>>

    /**
     * 네트워크 기기 목록을 실시간으로 관찰하는 Flow를 반환한다.
     *
     * 새 기기가 발견될 때마다 업데이트된 전체 목록을 emit한다.
     * 구독 취소 시 자동으로 스캔을 중단한다.
     *
     * @return 발견된 기기 목록을 실시간으로 emit하는 Flow
     */
    fun observeDevices(): Flow<List<NetworkDevice>>

    /**
     * ARP 테이블을 직접 조회하여 기기 목록을 반환한다.
     *
     * /proc/net/arp 파일에서 현재 캐싱된 ARP 항목을 읽는다.
     * scanDevices()보다 빠르지만 mDNS/SSDP 정보는 포함되지 않는다.
     *
     * @return Result.success(devices) 또는 Result.failure(exception)
     */
    suspend fun getArpTable(): Result<List<NetworkDevice>>
}
