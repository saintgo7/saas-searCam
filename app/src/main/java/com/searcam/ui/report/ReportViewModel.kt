package com.searcam.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.searcam.domain.model.ScanReport
import com.searcam.domain.repository.ReportRepository
import com.searcam.domain.usecase.ExportReportUseCase
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
 * 리포트 목록 UI 상태
 */
sealed class ReportListUiState {
    data object Loading : ReportListUiState()
    data class Ready(val reports: List<ScanReport>) : ReportListUiState()
    data class Error(val code: String, val message: String) : ReportListUiState()
}

/**
 * 리포트 상세 UI 상태
 */
sealed class ReportDetailUiState {
    data object Loading : ReportDetailUiState()
    data class Ready(val report: ScanReport) : ReportDetailUiState()
    data class Error(val code: String, val message: String) : ReportDetailUiState()
}

/**
 * 리포트 화면 일회성 이벤트
 */
sealed class ReportUiEvent {
    data class ShowSnackbar(val message: String) : ReportUiEvent()
    data class PdfExported(val filePath: String) : ReportUiEvent()
    data object DeleteConfirmed : ReportUiEvent()
}

/**
 * 리포트 화면 ViewModel
 *
 * 리포트 목록을 실시간 Flow로 관리하고, 상세 보기 및 PDF 내보내기를 처리한다.
 */
@HiltViewModel
class ReportViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    private val exportReportUseCase: ExportReportUseCase,
) : ViewModel() {

    // 목록 상태
    private val _listUiState = MutableStateFlow<ReportListUiState>(ReportListUiState.Loading)
    val listUiState: StateFlow<ReportListUiState> = _listUiState.asStateFlow()

    // 상세 상태
    private val _detailUiState = MutableStateFlow<ReportDetailUiState>(ReportDetailUiState.Loading)
    val detailUiState: StateFlow<ReportDetailUiState> = _detailUiState.asStateFlow()

    // 일회성 이벤트
    private val _events = MutableSharedFlow<ReportUiEvent>()
    val events: SharedFlow<ReportUiEvent> = _events.asSharedFlow()

    init {
        // 리포트 목록 실시간 관찰
        observeReports()
    }

    /**
     * 리포트 목록을 실시간으로 관찰하여 상태를 유지한다.
     */
    private fun observeReports() {
        viewModelScope.launch {
            reportRepository.observeReports()
                .catch { e ->
                    _listUiState.value = ReportListUiState.Error(
                        code = "E3010",
                        message = "리포트 목록 로딩 오류: ${e.message}",
                    )
                }
                .collect { reports ->
                    _listUiState.value = ReportListUiState.Ready(reports = reports)
                }
        }
    }

    /**
     * 특정 리포트의 상세 정보를 불러온다.
     *
     * @param reportId 리포트 UUID
     */
    fun loadReportDetail(reportId: String) {
        viewModelScope.launch {
            _detailUiState.value = ReportDetailUiState.Loading

            val result = reportRepository.getReport(reportId)
            _detailUiState.value = if (result.isSuccess) {
                ReportDetailUiState.Ready(report = result.getOrThrow())
            } else {
                ReportDetailUiState.Error(
                    code = "E3011",
                    message = "리포트를 찾을 수 없습니다: ${result.exceptionOrNull()?.message}",
                )
            }
        }
    }

    /**
     * 리포트를 PDF로 내보낸다.
     *
     * @param reportId 내보낼 리포트 UUID
     */
    fun exportToPdf(reportId: String) {
        viewModelScope.launch {
            val result = exportReportUseCase.invoke(reportId)
            if (result.isSuccess) {
                _events.emit(ReportUiEvent.PdfExported(result.getOrThrow()))
                _events.emit(ReportUiEvent.ShowSnackbar("PDF로 저장되었습니다."))
            } else {
                _events.emit(
                    ReportUiEvent.ShowSnackbar(
                        "PDF 내보내기 실패: ${result.exceptionOrNull()?.message}",
                    )
                )
            }
        }
    }

    /**
     * 리포트를 삭제한다.
     *
     * @param reportId 삭제할 리포트 UUID
     */
    fun deleteReport(reportId: String) {
        viewModelScope.launch {
            val result = reportRepository.deleteReport(reportId)
            if (result.isSuccess) {
                _events.emit(ReportUiEvent.ShowSnackbar("리포트가 삭제되었습니다."))
            } else {
                _events.emit(
                    ReportUiEvent.ShowSnackbar(
                        "삭제 실패: ${result.exceptionOrNull()?.message}",
                    )
                )
            }
        }
    }
}
