package com.searcam.ui.scan

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * Quick Scan 진행 화면
 *
 * 스캔 시작 시 자동으로 실행되며, 진행 중 애니메이션과 실시간 기기 발견 카운트를 표시한다.
 * 완료 시 결과 화면으로 자동 이동한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickScanScreen(
    onNavigateBack: () -> Unit,
    onNavigateToResult: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 화면 진입 시 자동 스캔 시작
    LaunchedEffect(Unit) {
        viewModel.startQuickScan()
    }

    // 일회성 이벤트 처리
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ScanUiEvent.NavigateToResult -> onNavigateToResult(event.reportId)
                is ScanUiEvent.ShowSnackbar -> { /* 스낵바 처리 */ }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quick Scan") },
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
        QuickScanContent(
            uiState = uiState,
            onCancel = {
                viewModel.cancelScan()
                onNavigateBack()
            },
            onRetry = { viewModel.startQuickScan() },
            modifier = Modifier.padding(paddingValues),
        )
    }
}

@Composable
private fun QuickScanContent(
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
            is ScanUiState.Idle, is ScanUiState.Success -> {
                // 자동으로 상태 전환됨 — 빈 화면
            }

            is ScanUiState.Scanning -> {
                // 회전 레이더 애니메이션
                RadarAnimation()
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Wi-Fi 네트워크 스캔 중",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.currentStep,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(24.dp))

                // 레이어별 진행 단계
                ScanProgress(
                    progress = uiState.progress,
                    steps = listOf(
                        ScanStep("Wi-Fi 스캔", StepStatus.IN_PROGRESS),
                        ScanStep("기기 분석", StepStatus.WAITING),
                        ScanStep("포트 확인", StepStatus.WAITING),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(24.dp))

                // 발견된 기기 수
                Text(
                    text = "발견된 기기: ${uiState.foundDevices.size}대",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(32.dp))

                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Text("취소")
                }
            }

            is ScanUiState.Error -> {
                Text(
                    text = "스캔 중 오류가 발생했습니다",
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
                Button(onClick = onRetry) {
                    Text("다시 시도")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onCancel) {
                    Text("취소")
                }
            }
        }
    }
}

/**
 * 레이더 회전 원 애니메이션 컴포넌트
 */
@Composable
private fun RadarAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "radar_rotation",
    )
    val radarColor = MaterialTheme.colorScheme.primary

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
        Canvas(modifier = Modifier.size(120.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2 - 8.dp.toPx()

            // 외부 원
            drawCircle(
                color = radarColor.copy(alpha = 0.2f),
                radius = radius,
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
            // 내부 원
            drawCircle(
                color = radarColor.copy(alpha = 0.3f),
                radius = radius * 0.6f,
                center = center,
                style = Stroke(width = 1.5f.dp.toPx()),
            )
            // 회전 선
            val radians = Math.toRadians(rotation.toDouble())
            val endX = center.x + radius * kotlin.math.cos(radians).toFloat()
            val endY = center.y + radius * kotlin.math.sin(radians).toFloat()
            drawLine(
                color = radarColor,
                start = center,
                end = Offset(endX, endY),
                strokeWidth = 2.dp.toPx(),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun QuickScanScreenPreview() {
    SearCamTheme {
        QuickScanContent(
            uiState = ScanUiState.Scanning(
                elapsedSeconds = 8,
                foundDevices = emptyList(),
                progress = 0.3f,
                currentStep = "Wi-Fi 네트워크 스캔 중...",
            ),
            onCancel = {},
            onRetry = {},
        )
    }
}
