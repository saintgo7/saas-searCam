package com.searcam.di

import android.content.Context
import android.hardware.SensorManager
import android.net.wifi.WifiManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import java.util.concurrent.CountDownLatch
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.Executors
import javax.inject.Named
import javax.inject.Singleton

/**
 * 센서 하드웨어 DI 모듈
 *
 * SensorManager(자력계), WifiManager, CameraX 관련 의존성을 제공한다.
 * 하드웨어 접근 객체는 앱 전역에서 하나만 유지(SingletonComponent).
 */
@Module
@InstallIn(SingletonComponent::class)
object SensorModule {

    /**
     * Android SensorManager
     *
     * TYPE_MAGNETIC_FIELD(자력계), TYPE_ACCELEROMETER(가속도계) 접근에 사용.
     * Layer 3 EMF 탐지의 핵심 의존성.
     */
    @Provides
    @Singleton
    fun provideSensorManager(
        @ApplicationContext context: Context
    ): SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    /**
     * Android WifiManager
     *
     * Wi-Fi 스캔, ARP 테이블 읽기, mDNS 서비스 검색에 사용.
     * Layer 1 네트워크 탐지의 핵심 의존성.
     *
     * applicationContext를 사용해 메모리 누수 방지.
     */
    @Provides
    @Singleton
    fun provideWifiManager(
        @ApplicationContext context: Context
    ): WifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    /**
     * ProcessCameraProvider — CameraX 카메라 생명주기 관리자
     *
     * 앱 프로세스 범위의 싱글톤으로 제공된다.
     * LifecycleOwner 바인딩은 LensDetector/IrDetector가 담당한다.
     */
    @Provides
    @Singleton
    fun provideProcessCameraProvider(
        @ApplicationContext context: Context
    ): ProcessCameraProvider {
        var provider: ProcessCameraProvider? = null
        val latch = CountDownLatch(1)
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            provider = future.get()
            latch.countDown()
        }, ContextCompat.getMainExecutor(context))
        latch.await()
        return provider!!
    }

    /**
     * CameraX Preview UseCase
     *
     * 실시간 카메라 프리뷰를 렌즈 파인더 화면에 표시하는 데 사용.
     * 후면 카메라(DEFAULT_BACK_CAMERA) 기본 설정.
     */
    @Provides
    @Named("BackCameraPreview")
    fun provideBackCameraPreview(): Preview = Preview.Builder()
        .setTargetResolution(android.util.Size(1280, 720))
        .build()

    /**
     * 전면 카메라 Preview UseCase (IR 감지용)
     *
     * IR 필터가 약한 전면 카메라로 적외선 LED 감지.
     */
    @Provides
    @Named("FrontCameraPreview")
    fun provideFrontCameraPreview(): Preview = Preview.Builder()
        .setTargetResolution(android.util.Size(1280, 720))
        .build()

    /**
     * CameraX ImageAnalysis UseCase
     *
     * 카메라 프레임을 분석 파이프라인에 전달.
     * STRATEGY_KEEP_ONLY_LATEST: 처리 지연 시 최신 프레임만 유지(메모리 절약).
     * 카메라 프레임은 메모리에서만 처리하며 저장하지 않는다(보안 원칙).
     */
    @Provides
    @Singleton
    fun provideImageAnalysis(): ImageAnalysis = ImageAnalysis.Builder()
        .setTargetResolution(android.util.Size(1280, 720))
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
        .build()

    /**
     * 이미지 분석용 전용 Executor
     *
     * 단일 스레드로 프레임 분석을 순차 처리.
     * 멀티스레드로 인한 경쟁 조건(race condition)을 방지.
     */
    @Provides
    @Singleton
    @Named("CameraExecutor")
    fun provideCameraExecutor() = Executors.newSingleThreadExecutor()
}
