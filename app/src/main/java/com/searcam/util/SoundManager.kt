package com.searcam.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 경고 비프음 재생 관리자
 *
 * 비유: 금속 탐지기의 경고음처럼, 위험도 수준에 따라 다른 신호음을 낸다.
 *
 * ToneGenerator를 사용해 외부 음원 파일 없이 경고음을 생성한다.
 * 사용자가 소리를 비활성화한 경우 모든 재생을 무시한다.
 */
@Singleton
class SoundManager @Inject constructor(
    private val context: Context,
    private val prefs: android.content.SharedPreferences
) {

    private var toneGenerator: ToneGenerator? = null

    /**
     * ToneGenerator 초기화
     *
     * 사용 전 반드시 호출. 경고음 볼륨을 최대 80%로 제한한다.
     */
    fun initialize() {
        try {
            toneGenerator = ToneGenerator(
                AudioManager.STREAM_ALARM,
                ALARM_VOLUME_PERCENT
            )
            Timber.d("SoundManager 초기화 완료")
        } catch (e: RuntimeException) {
            // ToneGenerator 생성 실패 시 음소거 모드로 동작
            Timber.w(e, "ToneGenerator 초기화 실패 — 음소거 모드로 동작")
        }
    }

    /**
     * 위험도에 따른 경고음 재생
     *
     * @param riskScore 위험도 점수 (0~100)
     */
    fun playRiskAlert(riskScore: Int) {
        if (!isSoundEnabled()) return

        val toneType = when {
            riskScore >= Constants.RISK_CRITICAL_MIN -> ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK
            riskScore > Constants.RISK_CAUTION_MAX -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
            riskScore > Constants.RISK_INTEREST_MAX -> ToneGenerator.TONE_PROP_BEEP2
            riskScore > Constants.RISK_SAFE_MAX -> ToneGenerator.TONE_PROP_BEEP
            else -> return // SAFE: 소리 없음
        }

        playTone(toneType, ALERT_DURATION_MS)
    }

    /**
     * 스캔 완료 비프음 (짧은 단음)
     */
    fun playScanComplete() {
        if (!isSoundEnabled()) return
        playTone(ToneGenerator.TONE_PROP_BEEP, BEEP_DURATION_MS)
    }

    /**
     * 스캔 시작 비프음 (더블 비프)
     */
    fun playScanStart() {
        if (!isSoundEnabled()) return
        playTone(ToneGenerator.TONE_PROP_BEEP2, BEEP_DURATION_MS)
    }

    /**
     * 실제 톤 재생
     *
     * @param toneType ToneGenerator.TONE_* 상수
     * @param durationMs 재생 시간 (밀리초)
     */
    private fun playTone(toneType: Int, durationMs: Int) {
        try {
            toneGenerator?.startTone(toneType, durationMs)
        } catch (e: Exception) {
            Timber.w(e, "비프음 재생 실패: toneType=$toneType")
        }
    }

    /**
     * 소리 활성화 여부 확인
     */
    private fun isSoundEnabled(): Boolean =
        prefs.getBoolean(Constants.PrefKey.SOUND_ENABLED, DEFAULT_SOUND_ENABLED)

    /**
     * 리소스 해제
     *
     * Activity/Fragment onDestroy에서 호출하거나 DI 소멸 시 자동 호출.
     */
    fun release() {
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            Timber.w(e, "ToneGenerator 해제 중 오류")
        }
    }

    companion object {
        private const val ALARM_VOLUME_PERCENT = 80
        private const val ALERT_DURATION_MS = 500
        private const val BEEP_DURATION_MS = 150
        private const val DEFAULT_SOUND_ENABLED = true
    }
}
