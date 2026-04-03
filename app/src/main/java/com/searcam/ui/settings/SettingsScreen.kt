package com.searcam.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import com.searcam.ui.theme.SearCamTheme

/**
 * 설정 화면
 *
 * 감도 슬라이더, 알림/소리/진동 토글, 앱 버전 정보를 표시한다.
 * 모든 설정은 변경 즉시 SharedPreferences에 저장된다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 일회성 이벤트 처리
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsUiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("설정") })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { paddingValues ->
        when (val state = uiState) {
            is SettingsUiState.Loading -> {}

            is SettingsUiState.Ready -> {
                SettingsContent(
                    settings = state.settings,
                    onSensitivityChange = viewModel::updateSensitivity,
                    onNotificationToggle = viewModel::toggleNotification,
                    onSoundToggle = viewModel::toggleSound,
                    onVibrationToggle = viewModel::toggleVibration,
                    onAutoSaveToggle = viewModel::toggleAutoSaveReport,
                    onResetDefaults = viewModel::resetToDefaults,
                    modifier = Modifier.padding(paddingValues),
                )
            }
        }
    }
}

@Composable
private fun SettingsContent(
    settings: AppSettings,
    onSensitivityChange: (Float) -> Unit,
    onNotificationToggle: () -> Unit,
    onSoundToggle: () -> Unit,
    onVibrationToggle: () -> Unit,
    onAutoSaveToggle: () -> Unit,
    onResetDefaults: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        // 스캔 설정 섹션
        SettingsSectionTitle(text = "스캔 설정")
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "감도 조절",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = sensitivityLabel(settings.scanSensitivity),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = settings.scanSensitivity,
                    onValueChange = onSensitivityChange,
                    valueRange = 0f..1f,
                    steps = 4, // 낮음/보통/높음/매우 높음/최대 5단계
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("낮음", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.weight(1f))
                    Text("높음", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )

                // 자동 리포트 저장
                SettingsToggleRow(
                    title = "자동 리포트 저장",
                    description = "스캔 완료 시 자동으로 저장",
                    checked = settings.autoSaveReport,
                    onToggle = onAutoSaveToggle,
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        // 알림 설정 섹션
        SettingsSectionTitle(text = "알림 설정")
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingsToggleRow(
                    title = "알림",
                    description = "스캔 완료 시 푸시 알림",
                    checked = settings.isNotificationEnabled,
                    onToggle = onNotificationToggle,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                SettingsToggleRow(
                    title = "소리",
                    description = "탐지 소리 알림",
                    checked = settings.isSoundEnabled,
                    onToggle = onSoundToggle,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                SettingsToggleRow(
                    title = "진동",
                    description = "탐지 진동 알림",
                    checked = settings.isVibrationEnabled,
                    onToggle = onVibrationToggle,
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        // 앱 정보 섹션
        SettingsSectionTitle(text = "앱 정보")
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingsInfoRow(label = "버전", value = "1.0.0")
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                SettingsInfoRow(label = "빌드", value = "2026.04.04")
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                SettingsInfoRow(label = "최소 Android", value = "8.0 (API 26)")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        // 초기화 버튼
        OutlinedButton(
            onClick = onResetDefaults,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("설정 기본값으로 초기화")
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * 섹션 제목 컴포넌트
 */
@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

/**
 * 설정 토글 행 컴포넌트 — 제목 + 설명 + 스위치
 */
@Composable
private fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

/**
 * 설정 정보 행 컴포넌트 — 라벨 + 값
 */
@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 감도 값(0~1)을 한국어 라벨로 변환한다.
 */
private fun sensitivityLabel(value: Float): String = when {
    value < 0.2f -> "낮음 — 오탐 최소화"
    value < 0.4f -> "보통 이하 — 안정적 탐지"
    value < 0.6f -> "보통 — 권장 설정"
    value < 0.8f -> "높음 — 민감한 탐지"
    else -> "매우 높음 — 오탐 가능성 있음"
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    SearCamTheme {
        SettingsContent(
            settings = AppSettings(),
            onSensitivityChange = {},
            onNotificationToggle = {},
            onSoundToggle = {},
            onVibrationToggle = {},
            onAutoSaveToggle = {},
            onResetDefaults = {},
        )
    }
}
