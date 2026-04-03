package com.searcam.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 위험도별 진동 패턴 관리자
 *
 * 비유: 스마트워치의 심박 알림처럼, 위험도에 따라 다른 리듬으로 진동한다.
 *
 * Android 12+(API 31): VibratorManager 사용
 * Android 11 이하: 레거시 Vibrator 사용
 *
 * 진동 패턴 형식: [대기, 진동, 대기, 진동, ...] (밀리초 단위)
 */
@Singleton
class VibrationManager @Inject constructor(
    private val context: Context,
    private val prefs: android.content.SharedPreferences
) {

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    /**
     * 위험도 점수에 따른 진동 패턴 재생
     *
     * SAFE     (0~29):  진동 없음
     * CAUTION  (30~59): 짧은 단진동 1회
     * DANGER   (60~79): 더블 진동
     * CRITICAL (80~100): 연속 트리플 진동 (강도 높음)
     *
     * @param riskScore 위험도 점수 (0~100)
     */
    fun vibrateForRisk(riskScore: Int) {
        if (!isVibrationEnabled()) return

        when {
            riskScore >= Constants.RISK_CRITICAL_MIN ->
                vibrate(PATTERN_CRITICAL, AMPLITUDE_STRONG)

            riskScore > Constants.RISK_CAUTION_MAX ->
                vibrate(PATTERN_DANGER, AMPLITUDE_MEDIUM)

            riskScore > Constants.RISK_INTEREST_MAX ->
                vibrate(PATTERN_CAUTION, AMPLITUDE_MEDIUM)

            riskScore > Constants.RISK_SAFE_MAX ->
                vibrate(PATTERN_SINGLE_SHORT, AMPLITUDE_LIGHT)

            else -> return // SAFE: 진동 없음
        }
    }

    /**
     * 스캔 시작 알림 진동 (짧은 단진동)
     */
    fun vibrateScanStart() {
        if (!isVibrationEnabled()) return
        vibrate(PATTERN_SINGLE_SHORT, AMPLITUDE_LIGHT)
    }

    /**
     * 스캔 완료 알림 진동 (더블 진동)
     */
    fun vibrateScanComplete() {
        if (!isVibrationEnabled()) return
        vibrate(PATTERN_DOUBLE, AMPLITUDE_MEDIUM)
    }

    /**
     * 실제 진동 실행
     *
     * @param pattern 진동 패턴 [대기ms, 진동ms, ...]
     * @param amplitude 진동 강도 (1~255, -1: 기기 기본값)
     */
    private fun vibrate(pattern: LongArray, amplitude: Int) {
        if (!vibrator.hasVibrator()) {
            Timber.d("이 기기는 진동을 지원하지 않음")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = if (vibrator.hasAmplitudeControl()) {
                    VibrationEffect.createWaveform(
                        pattern,
                        intArrayOf(0, amplitude, 0, amplitude, 0, amplitude),
                        NO_REPEAT
                    )
                } else {
                    // 진폭 조절 미지원 기기: 기본 강도로 재생
                    VibrationEffect.createWaveform(pattern, NO_REPEAT)
                }
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, NO_REPEAT)
            }
        } catch (e: Exception) {
            Timber.w(e, "진동 재생 실패: pattern=${pattern.toList()}")
        }
    }

    /**
     * 진동 활성화 여부 확인
     */
    private fun isVibrationEnabled(): Boolean =
        prefs.getBoolean(Constants.PrefKey.VIBRATION_ENABLED, DEFAULT_VIBRATION_ENABLED)

    /**
     * 진행 중인 진동 즉시 중단
     */
    fun cancel() {
        try {
            vibrator.cancel()
        } catch (e: Exception) {
            Timber.w(e, "진동 취소 중 오류")
        }
    }

    companion object {
        private const val NO_REPEAT = -1 // 반복 없음

        // 진폭 수준
        private const val AMPLITUDE_LIGHT = 80
        private const val AMPLITUDE_MEDIUM = 150
        private const val AMPLITUDE_STRONG = 255

        // 진동 패턴 (밀리초 배열: [대기, 진동, 대기, 진동, ...])
        /** CAUTION: 단진동 1회 (200ms) */
        private val PATTERN_CAUTION = longArrayOf(0, 200)

        /** DANGER: 더블 진동 */
        private val PATTERN_DANGER = longArrayOf(0, 200, 100, 200)

        /** CRITICAL: 트리플 진동 (강렬) */
        private val PATTERN_CRITICAL = longArrayOf(0, 400, 100, 400, 100, 400)

        /** 스캔 시작: 단진동 1회 (짧음) */
        private val PATTERN_SINGLE_SHORT = longArrayOf(0, 100)

        /** 스캔 완료: 더블 진동 (짧음) */
        private val PATTERN_DOUBLE = longArrayOf(0, 100, 80, 100)

        private const val DEFAULT_VIBRATION_ENABLED = true
    }
}
