package com.searcam.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.searcam.domain.model.ScanReport
import com.searcam.ui.components.DeviceListItem
import com.searcam.ui.components.RiskBadge
import com.searcam.ui.components.RiskGauge
import com.searcam.ui.theme.SearCamTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 리포트 상세 화면
 *
 * 특정 스캔 리포트의 전체 내용을 표시한다.
 * 위험도 게이지, 발견 기기 목록, 레이어별 결과, PDF 내보내기 버튼을 포함한다.
 *
 * @param reportId 표시할 리포트 UUID
 * @param onNavigateBack 뒤로가기
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDetailScreen(
    reportId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportViewModel = hiltViewModel(),
) {
    val detailUiState by viewModel.detailUiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 리포트 데이터 로드
    LaunchedEffect(reportId) {
        viewModel.loadReportDetail(reportId)
    }

    // 일회성 이벤트 처리
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ReportUiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                is ReportUiEvent.PdfExported -> {}
                is ReportUiEvent.DeleteConfirmed -> onNavigateBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("리포트 상세") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    // PDF 내보내기 버튼
                    if (detailUiState is ReportDetailUiState.Ready) {
                        IconButton(onClick = { viewModel.exportToPdf(reportId) }) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = "PDF로 내보내기",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { paddingValues ->
        when (val state = detailUiState) {
            is ReportDetailUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is ReportDetailUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = state.message, color = MaterialTheme.colorScheme.error)
                }
            }

            is ReportDetailUiState.Ready -> {
                ReportDetailContent(
                    report = state.report,
                    onExportPdf = { viewModel.exportToPdf(reportId) },
                    modifier = Modifier.padding(paddingValues),
                )
            }
        }
    }
}

@Composable
private fun ReportDetailContent(
    report: ScanReport,
    onExportPdf: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        // 스캔 메타데이터 (날짜, 모드, 소요 시간)
        ReportMetaCard(report = report)
        Spacer(modifier = Modifier.height(20.dp))

        // 위험도 게이지
        RiskGauge(
            score = report.riskScore,
            riskLevel = report.riskLevel,
            size = 140.dp,
            animate = true,
        )
        Spacer(modifier = Modifier.height(12.dp))
        RiskBadge(riskLevel = report.riskLevel)
        Spacer(modifier = Modifier.height(24.dp))

        // 발견된 기기 목록
        if (report.devices.isNotEmpty()) {
            SectionTitle(text = "발견된 기기 (${report.devices.size}대)")
            report.devices.forEach { device ->
                DeviceListItem(device = device)
                Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // 발견 사항(Findings) 요약
        if (report.findings.isNotEmpty()) {
            SectionTitle(text = "이상 징후 (${report.findings.size}건)")
            report.findings.take(5).forEach { finding ->
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.size(8.dp))
                    Column {
                        Text(
                            text = finding.type.labelKo,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = finding.evidence,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // 위치 메모
        if (report.locationNote.isNotBlank()) {
            SectionTitle(text = "위치 메모")
            Text(
                text = report.locationNote,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        // PDF 내보내기 버튼
        Button(
            onClick = onExportPdf,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Default.PictureAsPdf,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text("PDF로 내보내기")
        }
    }
}

/**
 * 리포트 메타데이터 카드 — 날짜, 모드, 소요 시간
 */
@Composable
private fun ReportMetaCard(report: ScanReport) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
        ) {
            Column {
                Text(
                    text = formatTimestamp(report.completedAt),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = report.mode.labelKo,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "소요 시간",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatDuration(report.durationMs),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * 섹션 제목 컴포넌트
 */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    )
}

/** Unix epoch millis → "yyyy/M/d HH:mm" 형식 */
private fun formatTimestamp(epochMs: Long): String {
    val sdf = SimpleDateFormat("yyyy/M/d HH:mm", Locale.KOREA)
    return sdf.format(Date(epochMs))
}

/** 밀리초 → "X분 Y초" 형식 */
private fun formatDuration(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return if (minutes > 0) "${minutes}분 ${seconds}초" else "${seconds}초"
}

@Preview(showBackground = true)
@Composable
private fun ReportDetailScreenPreview() {
    SearCamTheme {
        ReportDetailScreen(reportId = "preview", onNavigateBack = {})
    }
}
