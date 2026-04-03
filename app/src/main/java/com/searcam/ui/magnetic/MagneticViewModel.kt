package com.searcam.ui.magnetic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.searcam.domain.model.EmfLevel
import com.searcam.domain.model.MagneticReading
import com.searcam.domain.model.RiskLevel
import com.searcam.domain.repository.MagneticRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 자기장 스캔 UI 상태
 */
sealed class MagneticUiState {
    /** 초기 상태 — 캘리브레이션 전 */
    data object Idle : MagneticUiState()

    /** 캘리브레이션 진행 중 */
    data object Calibrating : MagneticUiState()

    /**
     * 측정 중
     *
     * @param currentReading 최신 자기장 측정값
     * @param history 최근 60개 측정값 (슬라이딩 윈도우)
     * @param emfLevel 현재 EMF 위험 등급
     */
    data class Measuring(
        val currentReading: MagneticReading,
        val history: List<MagneticReading>,
        val emfLevel: EmfLevel,
    ) : MagneticUiState()

    /** 오류 상태 */
    data class Error(val code: String, val message: String) : MagneticUiState()
}

/**
 * 자기장 화면 일회성 이벤트
 */
sealed class MagneticUiEvent {
    data class ShowSnackbar(val message: String) : MagneticUiEvent()
    data object CalibrationComplete : MagneticUiEvent()
}

/**
 * 자기장 스캔 화면 ViewModel
 *
 * MagneticRepository를 통해 자기장 측정값을 20Hz로 수신하고
 * 최근 60개 포인트를 슬라이딩 윈도우로 유지한다.
 * 캘리브레이션 버튼으로 현재 환경을 기준점으로 설정할 수 있다.
 */
@HiltViewModel
class MagneticViewModel @Inject constructor(
    private val magneticRepository: MagneticRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<MagneticUiState>(MagneticUiState.Idle)
    val uiState: StateFlow<MagneticUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MagneticUiEvent>()
    val events: SharedFlow<MagneticUiEvent> = _events.asSharedFlow()

    // 슬라이딩 윈도우 — 최근 60개 측정값 유지 (약 3초, 20Hz)
    private val historyWindow = ArrayDeque<MagneticReading>(HISTORY_SIZE)

    /**
     * 자기장 측정 시작
     *
     * 캘리브레이션 후 observeReadings()로 실시간 측정을 시작한다.
     */
    fun startMeasuring() {
        viewModelScope.launch {
            magneticRepository.observeReadings()
                .catch { e ->
                    _uiState.value = MagneticUiState.Error(
                        code = "E1010",
                        message = "자기장 센서 오류: ${e.message}",
                    )
                }
                .collect { reading ->
                    // 슬라이딩 윈도우 업데이트
                    if (historyWindow.size >= HISTORY_SIZE) {
                        historyWindow.removeFirst()
                    }
                    historyWindow.addLast(reading)

                    _uiState.value = MagneticUiState.Measuring(
                        currentReading = reading,
                        history = historyWindow.toList(),
                        emfLevel = reading.level,
                    )
                }
        }
    }

    /**
     * 캘리브레이션 실행
     *
     * 현재 자기장 값을 기준점으로 저장하여 이후 delta를 계산하는 데 사용한다.
     * 스캔 시작 전 또는 환경 변경 시 호출한다.
     */
    fun calibrate() {
        viewModelScope.launch {
            _uiState.value = MagneticUiState.Calibrating

            val result = magneticRepository.calibrate()
            if (result.isSuccess) {
                _events.emit(MagneticUiEvent.CalibrationComplete)
                _events.emit(MagneticUiEvent.ShowSnackbar("캘리브레이션 완료. 기준값이 설정되었습니다."))
                startMeasuring()
            } else {
                _uiState.value = MagneticUiState.Error(
                    code = "E1011",
                    message = "캘리브레이션 실패: ${result.exceptionOrNull()?.message}",
                )
            }
        }
    }

    /**
     * 히스토리 초기화
     */
    fun clearHistory() {
        historyWindow.clear()
    }

    /**
     * 현재 측정값에 대응하는 위험 등급을 RiskLevel로 변환한다.
     */
    fun emfLevelToRiskLevel(emfLevel: EmfLevel): RiskLevel = when (emfLevel) {
        EmfLevel.NORMAL -> RiskLevel.SAFE
        EmfLevel.INTEREST -> RiskLevel.INTEREST
        EmfLevel.CAUTION -> RiskLevel.CAUTION
        EmfLevel.SUSPECT -> RiskLevel.DANGER
        EmfLevel.STRONG_SUSPECT -> RiskLevel.CRITICAL
    }

    companion object {
        /** 그래프 슬라이딩 윈도우 크기 (약 3초, 20Hz 기준) */
        private const val HISTORY_SIZE = 60
    }
}
