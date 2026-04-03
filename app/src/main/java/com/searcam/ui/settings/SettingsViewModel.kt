package com.searcam.ui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 앱 설정 데이터 모델
 *
 * @param scanSensitivity 스캔 감도 (0.0 ~ 1.0, 기본 0.5)
 * @param isNotificationEnabled 스캔 완료 알림 활성 여부
 * @param isSoundEnabled 탐지 소리 알림 활성 여부
 * @param isVibrationEnabled 탐지 진동 알림 활성 여부
 * @param autoSaveReport 스캔 완료 시 자동 리포트 저장 여부
 */
data class AppSettings(
    val scanSensitivity: Float = 0.5f,
    val isNotificationEnabled: Boolean = true,
    val isSoundEnabled: Boolean = true,
    val isVibrationEnabled: Boolean = true,
    val autoSaveReport: Boolean = false,
)

/**
 * 설정 화면 UI 상태
 */
sealed class SettingsUiState {
    data object Loading : SettingsUiState()
    data class Ready(val settings: AppSettings) : SettingsUiState()
}

/**
 * 설정 화면 일회성 이벤트
 */
sealed class SettingsUiEvent {
    data class ShowSnackbar(val message: String) : SettingsUiEvent()
}

/**
 * 설정 화면 ViewModel
 *
 * SharedPreferences 기반으로 앱 설정을 영구 저장한다.
 * 모든 설정 변경은 즉시 저장된다.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsUiEvent>()
    val events: SharedFlow<SettingsUiEvent> = _events.asSharedFlow()

    init {
        loadSettings()
    }

    /**
     * SharedPreferences에서 설정을 읽어 UI 상태를 업데이트한다.
     */
    private fun loadSettings() {
        val settings = AppSettings(
            scanSensitivity = prefs.getFloat(KEY_SENSITIVITY, 0.5f),
            isNotificationEnabled = prefs.getBoolean(KEY_NOTIFICATION, true),
            isSoundEnabled = prefs.getBoolean(KEY_SOUND, true),
            isVibrationEnabled = prefs.getBoolean(KEY_VIBRATION, true),
            autoSaveReport = prefs.getBoolean(KEY_AUTO_SAVE, false),
        )
        _uiState.value = SettingsUiState.Ready(settings = settings)
    }

    /**
     * 스캔 감도를 변경한다.
     *
     * @param value 감도 값 (0.0 ~ 1.0)
     */
    fun updateSensitivity(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        prefs.edit().putFloat(KEY_SENSITIVITY, clamped).apply()
        updateSettings { it.copy(scanSensitivity = clamped) }
    }

    /**
     * 알림 활성 여부를 토글한다.
     */
    fun toggleNotification() {
        val current = currentSettings()?.isNotificationEnabled ?: true
        val updated = !current
        prefs.edit().putBoolean(KEY_NOTIFICATION, updated).apply()
        updateSettings { it.copy(isNotificationEnabled = updated) }
    }

    /**
     * 소리 알림 활성 여부를 토글한다.
     */
    fun toggleSound() {
        val current = currentSettings()?.isSoundEnabled ?: true
        val updated = !current
        prefs.edit().putBoolean(KEY_SOUND, updated).apply()
        updateSettings { it.copy(isSoundEnabled = updated) }
    }

    /**
     * 진동 알림 활성 여부를 토글한다.
     */
    fun toggleVibration() {
        val current = currentSettings()?.isVibrationEnabled ?: true
        val updated = !current
        prefs.edit().putBoolean(KEY_VIBRATION, updated).apply()
        updateSettings { it.copy(isVibrationEnabled = updated) }
    }

    /**
     * 자동 리포트 저장 여부를 토글한다.
     */
    fun toggleAutoSaveReport() {
        val current = currentSettings()?.autoSaveReport ?: false
        val updated = !current
        prefs.edit().putBoolean(KEY_AUTO_SAVE, updated).apply()
        updateSettings { it.copy(autoSaveReport = updated) }
    }

    /**
     * 모든 설정을 기본값으로 초기화한다.
     */
    fun resetToDefaults() {
        prefs.edit().clear().apply()
        loadSettings()
        viewModelScope.launch {
            _events.emit(SettingsUiEvent.ShowSnackbar("설정이 기본값으로 초기화되었습니다."))
        }
    }

    /** 현재 설정 값을 가져오는 헬퍼 함수 */
    private fun currentSettings(): AppSettings? =
        (_uiState.value as? SettingsUiState.Ready)?.settings

    /** 불변성 패턴으로 설정을 업데이트한다 */
    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val current = _uiState.value as? SettingsUiState.Ready ?: return
        _uiState.value = current.copy(settings = transform(current.settings))
    }

    companion object {
        private const val PREFS_NAME = "searcam_settings"
        private const val KEY_SENSITIVITY = "scan_sensitivity"
        private const val KEY_NOTIFICATION = "notification_enabled"
        private const val KEY_SOUND = "sound_enabled"
        private const val KEY_VIBRATION = "vibration_enabled"
        private const val KEY_AUTO_SAVE = "auto_save_report"
    }
}
