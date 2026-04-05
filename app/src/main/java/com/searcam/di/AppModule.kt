package com.searcam.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Named
import javax.inject.Singleton

// 스캔 결과 업로드 API URL
private const val REPORT_API_URL = "https://cam.abada.co.kr"

/**
 * 앱 전역 의존성 DI 모듈
 *
 * Context, Coroutine Dispatcher, SharedPreferences 등
 * 앱 생명주기 동안 유지되는 싱글톤 의존성을 제공한다.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * IO Dispatcher: 네트워크, 파일, DB 작업에 사용
     * 블로킹 작업을 위해 최적화된 스레드 풀
     */
    @Provides
    @Named("IoDispatcher")
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    /**
     * Default Dispatcher: CPU 집약적 계산(위험도 산출, 이미지 분석)에 사용
     */
    @Provides
    @Named("DefaultDispatcher")
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    /**
     * Main Dispatcher: UI 업데이트에 사용
     */
    @Provides
    @Named("MainDispatcher")
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    /**
     * 앱 설정 저장용 SharedPreferences
     *
     * 온보딩 완료 여부, 사용자 설정, 마지막 스캔 타임스탬프 등을 저장한다.
     * 민감한 데이터(위치, 스캔 결과)는 Room DB에 별도 저장.
     */
    /**
     * 스캔 결과 업로드 API URL
     */
    @Provides
    @Named("reportApiUrl")
    fun provideReportApiUrl(): String = REPORT_API_URL

    @Provides
    @Singleton
    fun provideSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences = context.getSharedPreferences(
        "searcam_prefs",
        Context.MODE_PRIVATE
    )
}
