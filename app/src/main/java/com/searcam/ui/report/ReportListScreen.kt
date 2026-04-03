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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.searcam.domain.model.ScanMode
import com.searcam.domain.model.ScanReport
import com.searcam.ui.components.RiskBadge
import com.searcam.ui.theme.SearCamTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 리포트 목록 화면
 *
 * 저장된 스캔 리포트를 최신순으로 LazyColumn에 나열한다.
 * 각 아이템에 날짜/모드/위험도 배지를 표시한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportListScreen(
    onNavigateToDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportViewModel = hiltViewModel(),
) {
    val listUiState by viewModel.listUiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("리포트") })
        },
        modifier = modifier,
    ) { paddingValues ->
        when (val state = listUiState) {
            is ReportListUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is ReportListUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = state.message, color = MaterialTheme.colorScheme.error)
                }
            }

            is ReportListUiState.Ready -> {
                if (state.reports.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "저장된 리포트가 없습니다",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = paddingValues,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                        items(
                            items = state.reports,
                            key = { report -> report.id },
                        ) { report ->
                            ReportListItem(
                                report = report,
                                onClick = { onNavigateToDetail(report.id) },
                            )
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }
}

/**
 * 리포트 목록 아이템 카드
 *
 * 날짜, 스캔 모드, 위험도 배지, 기기 수를 표시한다.
 */
@Composable
private fun ReportListItem(
    report: ScanReport,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            // 날짜 + 모드 정보
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatTimestamp(report.completedAt),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = report.mode.labelKo,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (report.devices.isNotEmpty()) {
                    Text(
                        text = "기기 ${report.devices.size}대 발견",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 위험도 배지
            RiskBadge(riskLevel = report.riskLevel, showIcon = false)
            Spacer(modifier = Modifier.size(8.dp))
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = "상세 보기",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Unix epoch millis → "M/d HH:mm" 형식 */
private fun formatTimestamp(epochMs: Long): String {
    val sdf = SimpleDateFormat("yyyy/M/d HH:mm", Locale.KOREA)
    return sdf.format(Date(epochMs))
}

@Preview(showBackground = true)
@Composable
private fun ReportListScreenPreview() {
    SearCamTheme {
        ReportListScreen(onNavigateToDetail = {})
    }
}
