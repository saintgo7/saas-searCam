package com.searcam.di

import com.searcam.data.repository.IrDetectionRepositoryImpl
import com.searcam.data.repository.LensDetectionRepositoryImpl
import com.searcam.data.repository.MagneticRepositoryImpl
import com.searcam.data.repository.ReportRepositoryImpl
import com.searcam.data.repository.WifiScanRepositoryImpl
import com.searcam.domain.repository.IrDetectionRepository
import com.searcam.domain.repository.LensDetectionRepository
import com.searcam.domain.repository.MagneticRepository
import com.searcam.domain.repository.ReportRepository
import com.searcam.domain.repository.WifiScanRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Repository 인터페이스 → 구현체 바인딩 모듈
 *
 * Clean Architecture 원칙: domain 레이어는 repository 인터페이스만 알고,
 * data 레이어의 구현체를 직접 참조하지 않는다.
 * Hilt @Binds로 런타임에 인터페이스 → 구현체를 연결한다.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    /**
     * Wi-Fi 스캔 Repository 바인딩
     *
     * ARP 테이블 읽기, mDNS 서비스 검색, OUI 매칭, 포트 스캔을 담당.
     * Layer 1 탐지의 핵심 데이터 소스.
     */
    @Binds
    @Singleton
    abstract fun bindWifiScanRepository(
        impl: WifiScanRepositoryImpl
    ): WifiScanRepository

    /**
     * 자기장(EMF) 센서 Repository 바인딩
     *
     * TYPE_MAGNETIC_FIELD 3축 자력계 데이터를 Flow로 제공.
     * Layer 3 탐지의 데이터 소스.
     */
    @Binds
    @Singleton
    abstract fun bindMagneticRepository(
        impl: MagneticRepositoryImpl
    ): MagneticRepository

    /**
     * IR 감지 Repository 바인딩
     *
     * 전면 카메라로 적외선 LED 발광을 분석.
     * Layer 2 탐지(IR 채널)의 데이터 소스.
     */
    @Binds
    @Singleton
    abstract fun bindIrDetectionRepository(
        impl: IrDetectionRepositoryImpl
    ): IrDetectionRepository

    /**
     * 렌즈 감지 Repository 바인딩
     *
     * 후면 카메라 + 플래시 Retroreflection으로 렌즈 반사 패턴 분석.
     * Layer 2 탐지(렌즈 채널)의 데이터 소스.
     */
    @Binds
    @Singleton
    abstract fun bindLensDetectionRepository(
        impl: LensDetectionRepositoryImpl
    ): LensDetectionRepository

    /**
     * 리포트 Repository 바인딩
     *
     * Room DB에 스캔 결과를 저장하고 조회한다.
     * PDF 내보내기도 이 Repository를 통해 트리거된다.
     */
    @Binds
    @Singleton
    abstract fun bindReportRepository(
        impl: ReportRepositoryImpl
    ): ReportRepository
}
