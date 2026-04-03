package com.searcam.ui.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.searcam.domain.model.ScanMode
import com.searcam.domain.model.ScanReport
import com.searcam.ui.components.RiskBadge
import com.searcam.ui.components.ScanModeCard
import com.searcam.ui.theme.SearCamTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 홈 화면
 *
 * 중앙 Quick Scan CTA 버튼(레이더 펄스 애니메이션)과 서브 모드 카드 4개,
 * 마지막 스캔 결과 카드를 표시한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToQuickScan: () -> Unit,
    onNavigateToFullScan: () -> Unit,
    onNavigateToLensFinder: () -> Unit,
    onNavigateToIrCamera: () -> Unit,
    onNavigateToMagnetic: () -> Unit,
    onNavigateToReport: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 일회성 이벤트 처리
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeUiEvent.NavigateToQuickScan -> onNavigateToQuickScan()
                is HomeUiEvent.NavigateToFullScan -> onNavigateToFullScan()
                is HomeUiEvent.NavigateToLensFinder -> onNavigateToLensFinder()
                is HomeUiEvent.NavigateToIrCamera -> onNavigateToIrCamera()
                is HomeUiEvent.NavigateToMagnetic -> onNavigateToMagnetic()
                is HomeUiEvent.NavigateToReportDetail -> onNavigateToReport(event.reportId)
                is HomeUiEvent.ShowSnackbar -> { /* 스낵바 처리 생략 — Scaffold에서 관리 */ }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("SearCam", fontWeight = FontWeight.Bold) })
        },
        modifier = modifier,
    ) { paddingValues ->
        HomeContent(
            uiState = uiState,
            onQuickScanClick = viewModel::onQuickScanClick,
            onFullScanClick = viewModel::onFullScanClick,
            onLensFinderClick = viewModel::onLensFinderClick,
            onIrCameraClick = viewModel::onIrCameraClick,
            onMagneticClick = viewModel::onMagneticClick,
            onLastReportClick = viewModel::onLastReportClick,
            modifier = Modifier.padding(paddingValues),
        )
    }
}

@Composable
private fun HomeContent(
    uiState: HomeUiState,
    onQuickScanClick: () -> Unit,
    onFullScanClick: () -> Unit,
    onLensFinderClick: () -> Unit,
    onIrCameraClick: () -> Unit,
    onMagneticClick: () -> Unit,
    onLastReportClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        is HomeUiState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is HomeUiState.Error -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = uiState.message, color = MaterialTheme.colorScheme.error)
            }
        }

        is HomeUiState.Ready -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Quick Scan CTA 버튼 (레이더 펄스 애니메이션)
                QuickScanButton(onClick = onQuickScanClick)
                Spacer(modifier = Modifier.height(32.dp))

                // 서브 모드 카드 2×2 그리드
                Text(
                    text = "세부 스캔 모드",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ScanModeCard(
                        icon = Icons.Default.Wifi,
                        title = "Full Scan",
                        description = "3중 교차 검증",
                        estimatedTime = "약 2분",
                        onClick = onFullScanClick,
                        modifier = Modifier.weight(1f),
                    )
                    ScanModeCard(
                        icon = Icons.Default.Camera,
                        title = "렌즈 찾기",
                        description = "역반사 감지",
                        estimatedTime = "제한 없음",
                        onClick = onLensFinderClick,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ScanModeCard(
                        icon = Icons.Default.Camera,
                        title = "IR Only",
                        description = "IR LED 탐지",
                        estimatedTime = "제한 없음",
                        onClick = onIrCameraClick,
                        modifier = Modifier.weight(1f),
                    )
                    ScanModeCard(
                        icon = Icons.Default.Sensors,
                        title = "EMF Only",
                        description = "자기장 분석",
                        estimatedTime = "제한 없음",
                        onClick = onMagneticClick,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))

                // 마지막 스캔 결과 카드
                LastReportCard(
                    lastReport = uiState.lastReport,
                    onReportClick = onLastReportClick,
                )
            }
        }
    }
}

/**
 * Quick Scan CTA 버튼 — 120dp 원형, 레이더 펄스 애니메이션
 */
@Composable
private fun QuickScanButton(onClick: () -> Unit) {
    // 레이더 펄스 애니메이션 (3초 주기, 원형 확장)
    val infiniteTransition = rememberInfiniteTransition(label = "radar_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse_scale",
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse_alpha",
    )

    Box(contentAlignment = Alignment.Center) {
        // 펄스 원
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)),
        )
        // 메인 버튼
        Button(
            onClick = onClick,
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = "Quick Scan",
                    modifier = Modifier.size(36.dp),
                    tint = Color.White,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Quick Scan",
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "30초 점검",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }
    }
}

/**
 * 마지막 스캔 결과 카드
 *
 * 스캔 이력이 없으면 안내 메시지를 표시한다.
 */
@Composable
private fun LastReportCard(
    lastReport: ScanReport?,
    onReportClick: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "마지막 스캔 결과",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        if (lastReport == null) {
            // 스캔 이력 없음 — 안내 카드
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "첫 스캔을 시작해보세요",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
        } else {
            // 마지막 스캔 카드
            Card(
                onClick = { onReportClick(lastReport.id) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = formatTimestamp(lastReport.completedAt),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = scanModeLabel(lastReport.mode),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    RiskBadge(riskLevel = lastReport.riskLevel)
                    Spacer(modifier = Modifier.size(8.dp))
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "상세 보기",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/** Unix epoch millis → "M/d HH:mm" 형식 */
private fun formatTimestamp(epochMs: Long): String {
    val sdf = SimpleDateFormat("M/d HH:mm", Locale.KOREA)
    return sdf.format(Date(epochMs))
}

/** ScanMode → 한국어 라벨 */
private fun scanModeLabel(mode: ScanMode): String = when (mode) {
    ScanMode.QUICK -> "Quick Scan"
    ScanMode.FULL -> "Full Scan"
    ScanMode.LENS_FINDER -> "렌즈 찾기"
    ScanMode.IR_ONLY -> "IR 카메라"
    ScanMode.EMF_ONLY -> "자기장 스캔"
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    SearCamTheme {
        HomeContent(
            uiState = HomeUiState.Ready(lastReport = null),
            onQuickScanClick = {},
            onFullScanClick = {},
            onLensFinderClick = {},
            onIrCameraClick = {},
            onMagneticClick = {},
            onLastReportClick = {},
        )
    }
}
