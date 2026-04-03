package com.searcam.ui.scan

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.searcam.domain.model.NetworkDevice
import com.searcam.domain.model.ScanReport
import com.searcam.domain.usecase.RunFullScanUseCase
import com.searcam.domain.usecase.RunQuickScanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 스캔 화면 UI 상태
 */
sealed class ScanUiState {
    /** 초기 대기 상태 */
    data object Idle : ScanUiState()

    /**
     * 스캔 진행 중
     *
     * @param elapsedSeconds 경과 시간 (초)
     * @param foundDevices 지금까지 발견된 기기 목록
     * @param progress 전체 진행률 (0.0 ~ 1.0)
     * @param currentStep 현재 단계 이름
     */
    data class Scanning(
        val elapsedSeconds: Int = 0,
        val foundDevices: List<NetworkDevice> = emptyList(),
        val progress: Float = 0f,
        val currentStep: String = "스캔 준비 중...",
    ) : ScanUiState()

    /**
     * 스캔 완료
     *
     * @param report 최종 스캔 리포트
     */
    data class Success(val report: ScanReport) : ScanUiState()

    /**
     * 스캔 오류
     *
     * @param code 오류 코드 (E1xxx: 센서, E2xxx: 네트워크, E3xxx: 권한)
     * @param message 사용자 표시 메시지
     */
    data class Error(val code: String, val message: String) : ScanUiState()
}

/**
 * 스캔 화면 일회성 이벤트
 */
sealed class ScanUiEvent {
    /** 결과 화면으로 이동 */
    data class NavigateToResult(val reportId: String) : ScanUiEvent()

    /** 스낵바 메시지 */
    data class ShowSnackbar(val message: String) : ScanUiEvent()
}

/**
 * 스캔 화면 ViewModel
 *
 * Quick Scan, Full Scan 두 가지 모드를 모두 처리한다.
 * 스캔 취소는 Job.cancel()로 즉시 처리된다.
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val runQuickScanUseCase: RunQuickScanUseCase,
    private val runFullScanUseCase: RunFullScanUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ScanUiEvent>()
    val events: SharedFlow<ScanUiEvent> = _events.asSharedFlow()

    // 취소 가능한 스캔 Job
    private var scanJob: Job? = null

    /**
     * Quick Scan 시작
     *
     * Wi-Fi 레이어만 분석, 최대 30초 후 자동 완료된다.
     */
    fun startQuickScan() {
        if (_uiState.value is ScanUiState.Scanning) return

        scanJob = viewModelScope.launch {
            _uiState.value = ScanUiState.Scanning(
                progress = 0f,
                currentStep = "Wi-Fi 네트워크 스캔 중...",
            )

            try {
                runQuickScanUseCase.invoke().collect { report ->
                    _uiState.value = ScanUiState.Success(report)
                    _events.emit(ScanUiEvent.NavigateToResult(report.id))
                }
            } catch (e: Exception) {
                _uiState.value = ScanUiState.Error(
                    code = "E2001",
                    message = "스캔 중 오류가 발생했습니다: ${e.message}",
                )
            }
        }
    }

    /**
     * Full Scan 시작
     *
     * 4개 레이어를 병렬로 실행한다.
     */
    fun startFullScan(lifecycleOwner: LifecycleOwner) {
        if (_uiState.value is ScanUiState.Scanning) return

        scanJob = viewModelScope.launch {
            _uiState.value = ScanUiState.Scanning(
                progress = 0f,
                currentStep = "전체 스캔 준비 중...",
            )

            try {
                runFullScanUseCase.invoke(lifecycleOwner).collect { report ->
                    _uiState.value = ScanUiState.Success(report)
                    _events.emit(ScanUiEvent.NavigateToResult(report.id))
                }
            } catch (e: Exception) {
                _uiState.value = ScanUiState.Error(
                    code = "E2001",
                    message = "Full Scan 중 오류가 발생했습니다: ${e.message}",
                )
            }
        }
    }

    /**
     * 스캔 취소
     *
     * 진행 중인 스캔 Job을 즉시 취소하고 Idle 상태로 복귀한다.
     */
    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.value = ScanUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
    }
}
