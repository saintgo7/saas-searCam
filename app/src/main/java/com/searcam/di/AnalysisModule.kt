package com.searcam.di

import android.content.Context
import com.searcam.data.analysis.CrossValidator
import com.searcam.data.analysis.CrossValidatorImpl
import com.searcam.data.analysis.OuiDatabase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 분석 엔진 DI 모듈
 *
 * OUI 데이터베이스, 교차 검증 엔진 의존성을 제공한다.
 *
 * @Provides: 생성자에 접근할 수 없는 서드파티 클래스나 복잡한 초기화에 사용
 * @Binds: 인터페이스 → 구현체 매핑. @Provides보다 효율적(추가 객체 생성 없음)
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AnalysisModule {

    /**
     * CrossValidator 인터페이스 → 구현체 바인딩
     *
     * 3-Layer 탐지 결과를 교차 검증하여 최종 위험도를 산출하는 엔진.
     * 가중치: Wi-Fi 50% + 렌즈 35% + EMF 15%
     */
    @Binds
    @Singleton
    abstract fun bindCrossValidator(
        impl: CrossValidatorImpl
    ): CrossValidator

    companion object {

        /**
         * OUI 데이터베이스 싱글톤
         *
         * assets/oui.json (약 500KB)에서 MAC 제조사 정보를 로드한다.
         * 초기화 비용이 크므로 앱 전역에서 하나만 유지.
         *
         * OUI(Organizationally Unique Identifier): MAC 주소 앞 3바이트로
         * 네트워크 기기 제조사를 식별한다. 카메라 제조사 MAC이 탐지되면
         * 위험도가 높아진다.
         */
        @Provides
        @Singleton
        fun provideOuiDatabase(
            @ApplicationContext context: Context
        ): OuiDatabase = OuiDatabase(context)
    }
}
