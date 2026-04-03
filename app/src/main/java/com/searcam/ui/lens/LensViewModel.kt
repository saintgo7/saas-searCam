package com.searcam.ui.lens

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import com.searcam.domain.model.IrPoint
import com.searcam.domain.model.RetroreflectionPoint
import com.searcam.domain.repository.IrDetectionRepository
import com.searcam.domain.repository.LensDetectionRepository
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
 * 렌즈 탐지 UI 상태 — 실시간 역반사 포인트 목록
 */
sealed class LensUiState {
    data object Idle : LensUiState()
    data object Starting : LensUiState()
    data class Detecting(val retroPoints: List<RetroreflectionPoint>) : LensUiState()
    data class Error(val code: String, val message: String) : LensUiState()
}

/**
 * IR 탐지 UI 상태 — 실시간 IR 포인트 목록
 */
sealed class IrUiState {
    data object Idle : IrUiState()
    data object Starting : IrUiState()
    data class Detecting(val irPoints: List<IrPoint>) : IrUiState()
    data class Error(val code: String, val message: String) : IrUiState()
}

/**
 * 렌즈/IR 화면 일회성 이벤트
 */
sealed class LensUiEvent {
    data class ShowSnackbar(val message: String) : LensUiEvent()
}

/**
 * 렌즈 탐지 화면 ViewModel
 *
 * LensDetectionRepository (역반사)와 IrDetectionRepository (IR LED)를 함께 관리한다.
 * 각 탐지 모드는 별도 StateFlow로 분리되어 있다.
 */
@HiltViewModel
class LensViewModel @Inject constructor(
    private val lensDetectionRepository: LensDetectionRepository,
    private val irDetectionRepository: IrDetectionRepository,
) : ViewModel() {

    // 렌즈 역반사 탐지 상태
    private val _lensUiState = MutableStateFlow<LensUiState>(LensUiState.Idle)
    val lensUiState: StateFlow<LensUiState> = _lensUiState.asStateFlow()

    // IR LED 탐지 상태
    private val _irUiState = MutableStateFlow<IrUiState>(IrUiState.Idle)
    val irUiState: StateFlow<IrUiState> = _irUiState.asStateFlow()

    // 일회성 이벤트
    private val _events = MutableSharedFlow<LensUiEvent>()
    val events: SharedFlow<LensUiEvent> = _events.asSharedFlow()

    // 플래시 ON/OFF 상태 — 렌즈 찾기 모드에서만 사용
    private val _isFlashOn = MutableStateFlow(true)
    val isFlashOn: StateFlow<Boolean> = _isFlashOn.asStateFlow()

    /**
     * 렌즈 역반사 탐지 시작
     *
     * 후면 카메라 + 플래시를 켜고 역반사 포인트를 실시간으로 수신한다.
     */
    fun startLensDetection(lifecycleOwner: LifecycleOwner) {
        viewModelScope.launch {
            _lensUiState.value = LensUiState.Starting

            val startResult = lensDetectionRepository.startDetection(lifecycleOwner)
            if (startResult.isFailure) {
                _lensUiState.value = LensUiState.Error(
                    code = "E1001",
                    message = "카메라를 시작할 수 없습니다: ${startResult.exceptionOrNull()?.message}",
                )
                return@launch
            }

            // 역반사 포인트 실시간 수신
            lensDetectionRepository.observeRetroreflections()
                .catch { e ->
                    _lensUiState.value = LensUiState.Error(
                        code = "E1002",
                        message = "렌즈 탐지 중 오류: ${e.message}",
                    )
                }
                .collect { points ->
                    _lensUiState.value = LensUiState.Detecting(retroPoints = points)
                }
        }
    }

    /**
     * 렌즈 역반사 탐지 중단
     */
    fun stopLensDetection() {
        viewModelScope.launch {
            lensDetectionRepository.stopDetection()
            _lensUiState.value = LensUiState.Idle
        }
    }

    /**
     * IR LED 탐지 시작
     *
     * 전면 카메라를 열고 IR 포인트를 실시간으로 수신한다.
     */
    fun startIrDetection(lifecycleOwner: LifecycleOwner) {
        viewModelScope.launch {
            _irUiState.value = IrUiState.Starting

            val startResult = irDetectionRepository.startDetection(lifecycleOwner)
            if (startResult.isFailure) {
                _irUiState.value = IrUiState.Error(
                    code = "E1003",
                    message = "전면 카메라를 시작할 수 없습니다: ${startResult.exceptionOrNull()?.message}",
                )
                return@launch
            }

            // IR 포인트 실시간 수신
            irDetectionRepository.observeIrPoints()
                .catch { e ->
                    _irUiState.value = IrUiState.Error(
                        code = "E1004",
                        message = "IR 탐지 중 오류: ${e.message}",
                    )
                }
                .collect { points ->
                    _irUiState.value = IrUiState.Detecting(irPoints = points)
                }
        }
    }

    /**
     * IR LED 탐지 중단
     */
    fun stopIrDetection() {
        viewModelScope.launch {
            irDetectionRepository.stopDetection()
            _irUiState.value = IrUiState.Idle
        }
    }

    /**
     * 플래시 ON/OFF 토글
     */
    fun toggleFlash() {
        _isFlashOn.value = !_isFlashOn.value
    }

    override fun onCleared() {
        super.onCleared()
        // viewModelScope는 onCleared() 시점에 취소됨 — runBlocking으로 동기 해제 보장
        runBlocking {
            withTimeout(1_000L) {
                lensDetectionRepository.stopDetection()
                irDetectionRepository.stopDetection()
            }
        }
    }
}
