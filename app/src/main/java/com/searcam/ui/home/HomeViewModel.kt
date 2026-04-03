package com.searcam.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.searcam.domain.model.ScanReport
import com.searcam.domain.repository.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 홈 화면 UI 상태
 */
sealed class HomeUiState {
    /** 초기 상태 — 리포트 로딩 전 */
    data object Loading : HomeUiState()

    /** 정상 상태 — 마지막 스캔 리포트 (없을 수도 있음) */
    data class Ready(val lastReport: ScanReport?) : HomeUiState()

    /** 오류 상태 */
    data class Error(val code: String, val message: String) : HomeUiState()
}

/**
 * 홈 화면 일회성 이벤트 — 네비게이션 등
 */
sealed class HomeUiEvent {
    data object NavigateToQuickScan : HomeUiEvent()
    data object NavigateToFullScan : HomeUiEvent()
    data object NavigateToLensFinder : HomeUiEvent()
    data object NavigateToIrCamera : HomeUiEvent()
    data object NavigateToMagnetic : HomeUiEvent()
    data class NavigateToReportDetail(val reportId: String) : HomeUiEvent()
    data class ShowSnackbar(val message: String) : HomeUiEvent()
}

/**
 * 홈 화면 ViewModel
 *
 * 마지막 스캔 리포트를 StateFlow로 관리하고,
 * 각 모드 진입 이벤트를 SharedFlow로 방출한다.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
) : ViewModel() {

    // UI 상태: 마지막 스캔 리포트 포함
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // 일회성 이벤트: 네비게이션, 스낵바 등
    private val _events = MutableSharedFlow<HomeUiEvent>()
    val events: SharedFlow<HomeUiEvent> = _events.asSharedFlow()

    init {
        // 리포트 목록을 관찰하여 마지막 스캔 결과를 반영
        observeLastReport()
    }

    /**
     * 리포트 저장소를 실시간으로 관찰하여 가장 최신 스캔 결과를 유지한다.
     */
    private fun observeLastReport() {
        viewModelScope.launch {
            reportRepository.observeReports()
                .map { reports -> reports.firstOrNull() }
                .catch { e ->
                    _uiState.value = HomeUiState.Error(
                        code = "E3001",
                        message = "리포트를 불러오는 중 오류가 발생했습니다: ${e.message}",
                    )
                }
                .collect { lastReport ->
                    _uiState.value = HomeUiState.Ready(lastReport = lastReport)
                }
        }
    }

    /** Quick Scan 화면으로 이동 */
    fun onQuickScanClick() {
        viewModelScope.launch {
            _events.emit(HomeUiEvent.NavigateToQuickScan)
        }
    }

    /** Full Scan 화면으로 이동 */
    fun onFullScanClick() {
        viewModelScope.launch {
            _events.emit(HomeUiEvent.NavigateToFullScan)
        }
    }

    /** 렌즈 찾기 화면으로 이동 */
    fun onLensFinderClick() {
        viewModelScope.launch {
            _events.emit(HomeUiEvent.NavigateToLensFinder)
        }
    }

    /** IR 카메라 화면으로 이동 */
    fun onIrCameraClick() {
        viewModelScope.launch {
            _events.emit(HomeUiEvent.NavigateToIrCamera)
        }
    }

    /** 자기장 스캔 화면으로 이동 */
    fun onMagneticClick() {
        viewModelScope.launch {
            _events.emit(HomeUiEvent.NavigateToMagnetic)
        }
    }

    /** 마지막 스캔 결과 카드 탭 — 리포트 상세로 이동 */
    fun onLastReportClick(reportId: String) {
        viewModelScope.launch {
            _events.emit(HomeUiEvent.NavigateToReportDetail(reportId))
        }
    }
}
