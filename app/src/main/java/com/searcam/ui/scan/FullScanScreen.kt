package com.searcam.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.searcam.ui.components.ScanProgress
import com.searcam.ui.components.ScanStep
import com.searcam.ui.components.StepStatus
import com.searcam.ui.theme.SearCamTheme

/**
 * Full Scan 진행 화면
 *
 * 3개 레이어(Wi-Fi + 렌즈 + 자기장)의 진행률과 현재 단계를 표시한다.
 * 각 레이어는 병렬로 실행되며, 개별 완료 상태가 실시간으로 갱신된다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScanScreen(
    onNavigateBack: () -> Unit,
    onNavigateToResult: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // 화면 진입 시 자동 Full Scan 시작
    LaunchedEffect(Unit) {
        viewModel.startFullScan(lifecycleOwner)
    }

    // 일회성 이벤트 처리
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ScanUiEvent.NavigateToResult -> onNavigateToResult(event.reportId)
                is ScanUiEvent.ShowSnackbar -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Full Scan") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.cancelScan()
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        FullScanContent(
            uiState = uiState,
            onCancel = {
                viewModel.cancelScan()
                onNavigateBack()
            },
            onRetry = { viewModel.startFullScan(lifecycleOwner) },
            modifier = Modifier.padding(paddingValues),
        )
    }
}

@Composable
private fun FullScanContent(
    uiState: ScanUiState,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        when (uiState) {
            is ScanUiState.Idle, is ScanUiState.Success -> {}

            is ScanUiState.Scanning -> {
                CircularProgressIndicator(
                    progress = { uiState.progress },
                    modifier = Modifier.height(80.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "3중 교차 검증 중",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.currentStep,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(32.dp))

                // 3개 레이어 진행 단계 — Full Scan은 4개 레이어 병렬 실행
                ScanProgress(
                    progress = uiState.progress,
                    steps = listOf(
                        ScanStep("Layer 1 — Wi-Fi 네트워크 스캔", fullScanStepStatus(uiState.progress, 0)),
                        ScanStep("Layer 2 — 렌즈 역반사 감지", fullScanStepStatus(uiState.progress, 1)),
                        ScanStep("Layer 2B — IR LED 탐지", fullScanStepStatus(uiState.progress, 2)),
                        ScanStep("Layer 3 — 자기장 분석", fullScanStepStatus(uiState.progress, 3)),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(32.dp))

                OutlinedButton(onClick = onCancel) {
                    Text("취소")
                }
            }

            is ScanUiState.Error -> {
                Text(
                    text = "Full Scan 오류",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.message,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onRetry) { Text("다시 시도") }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onCancel) { Text("취소") }
            }
        }
    }
}

/**
 * 전체 진행률(0~1)과 레이어 인덱스(0~3)를 기반으로 개별 단계 상태를 결정한다.
 *
 * Full Scan 4개 레이어를 진행률 구간으로 매핑:
 *   - Layer 0 (Wi-Fi):   0.0 ~ 0.3
 *   - Layer 1 (Lens):    0.2 ~ 0.6
 *   - Layer 2 (IR):      0.3 ~ 0.7
 *   - Layer 3 (Magnetic): 0.5 ~ 1.0
 */
private fun fullScanStepStatus(progress: Float, layerIndex: Int): StepStatus {
    val (start, end) = when (layerIndex) {
        0 -> 0.0f to 0.3f
        1 -> 0.2f to 0.6f
        2 -> 0.3f to 0.7f
        else -> 0.5f to 1.0f
    }
    return when {
        progress >= end -> StepStatus.COMPLETED
        progress >= start -> StepStatus.IN_PROGRESS
        else -> StepStatus.WAITING
    }
}

@Preview(showBackground = true)
@Composable
private fun FullScanScreenPreview() {
    SearCamTheme {
        FullScanContent(
            uiState = ScanUiState.Scanning(
                progress = 0.5f,
                currentStep = "IR LED 탐지 중...",
            ),
            onCancel = {},
            onRetry = {},
        )
    }
}
