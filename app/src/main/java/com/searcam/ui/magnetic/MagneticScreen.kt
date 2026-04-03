package com.searcam.ui.magnetic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.searcam.domain.model.EmfLevel
import com.searcam.domain.model.MagneticReading
import com.searcam.ui.components.RiskBadge
import com.searcam.ui.components.RiskGauge
import com.searcam.ui.theme.SearCamTheme

/**
 * 자기장 스캔 화면
 *
 * 실시간 μT 수치 + RiskGauge + 3축 그래프 + 캘리브레이션 버튼을 표시한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MagneticScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MagneticViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 화면 진입 시 자동으로 캘리브레이션 후 측정 시작
    DisposableEffect(Unit) {
        viewModel.calibrate()
        onDispose {
            viewModel.clearHistory()
        }
    }

    // 일회성 이벤트 처리
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is MagneticUiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                is MagneticUiEvent.CalibrationComplete -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("자기장 스캔") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { paddingValues ->
        MagneticContent(
            uiState = uiState,
            onCalibrate = viewModel::calibrate,
            modifier = Modifier.padding(paddingValues),
        )
    }
}

@Composable
private fun MagneticContent(
    uiState: MagneticUiState,
    onCalibrate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        when (uiState) {
            is MagneticUiState.Idle -> {
                Text(
                    text = "캘리브레이션을 시작하세요",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onCalibrate) {
                    Text("캘리브레이션 시작")
                }
            }

            is MagneticUiState.Calibrating -> {
                Text(
                    text = "캘리브레이션 중...",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            is MagneticUiState.Measuring -> {
                val riskLevel = when (uiState.emfLevel) {
                    EmfLevel.NORMAL -> com.searcam.domain.model.RiskLevel.SAFE
                    EmfLevel.INTEREST -> com.searcam.domain.model.RiskLevel.INTEREST
                    EmfLevel.CAUTION -> com.searcam.domain.model.RiskLevel.CAUTION
                    EmfLevel.SUSPECT -> com.searcam.domain.model.RiskLevel.DANGER
                    EmfLevel.STRONG_SUSPECT -> com.searcam.domain.model.RiskLevel.CRITICAL
                }

                // 위험도 게이지 (EMF 레벨 점수 기반)
                RiskGauge(
                    score = uiState.emfLevel.score,
                    riskLevel = riskLevel,
                    size = 160.dp,
                    animate = false,
                )
                Spacer(modifier = Modifier.height(12.dp))

                RiskBadge(riskLevel = riskLevel)
                Spacer(modifier = Modifier.height(24.dp))

                // 실시간 수치 카드
                MagneticReadingCard(reading = uiState.currentReading)
                Spacer(modifier = Modifier.height(24.dp))

                // 3축 라인 그래프
                if (uiState.history.isNotEmpty()) {
                    Text(
                        text = "실시간 자기장 그래프",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    )
                    MagneticGraph(
                        readings = uiState.history,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // 캘리브레이션 버튼
                OutlinedButton(
                    onClick = onCalibrate,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("현재 위치로 재캘리브레이션")
                }
            }

            is MagneticUiState.Error -> {
                Text(
                    text = uiState.message,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onCalibrate) { Text("다시 시도") }
            }
        }
    }
}

/**
 * 현재 자기장 측정값 상세 카드
 */
@Composable
private fun MagneticReadingCard(reading: MagneticReading) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "현재 자기장",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth(),
            ) {
                MagneticAxisValue(axis = "X", value = reading.x)
                MagneticAxisValue(axis = "Y", value = reading.y)
                MagneticAxisValue(axis = "Z", value = reading.z)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "크기: ${"%.1f".format(reading.magnitude)} μT",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "변화량: ${"%.1f".format(reading.delta)} μT (${reading.level.labelKo})",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 단일 축 값 표시 컴포넌트
 */
@Composable
private fun MagneticAxisValue(axis: String, value: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = axis,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "${"%.1f".format(value)}",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "μT",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MagneticScreenPreview() {
    SearCamTheme {
        MagneticContent(
            uiState = MagneticUiState.Measuring(
                currentReading = MagneticReading(
                    timestamp = System.currentTimeMillis(),
                    x = 23.4f,
                    y = -12.1f,
                    z = 41.7f,
                    magnitude = 49.2f,
                    delta = 8.3f,
                    level = EmfLevel.INTEREST,
                ),
                history = emptyList(),
                emfLevel = EmfLevel.INTEREST,
            ),
            onCalibrate = {},
        )
    }
}
