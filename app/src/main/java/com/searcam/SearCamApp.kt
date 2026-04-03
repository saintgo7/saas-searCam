package com.searcam

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * SearCam 애플리케이션 진입점
 *
 * @HiltAndroidApp: Hilt DI 컨테이너 초기화.
 * 이 클래스가 없으면 Hilt 컴파일러가 코드 생성에 실패한다.
 */
@HiltAndroidApp
class SearCamApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initLogging()
    }

    /**
     * Timber 로깅 초기화
     *
     * DEBUG 빌드에서만 로그 출력. RELEASE에서는 자동으로 비활성화.
     * console.log/println 대신 Timber를 사용해야 한다.
     */
    private fun initLogging() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
