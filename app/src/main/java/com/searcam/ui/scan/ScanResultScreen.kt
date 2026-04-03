package com.searcam.ui.scan

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.searcam.domain.model.Finding
import com.searcam.domain.model.LayerResult
import com.searcam.domain.model.LayerType
import com.searcam.domain.model.RiskLevel
import com.searcam.domain.model.ScanReport
import com.searcam.domain.model.ScanStatus
import com.searcam.ui.components.RiskBadge
import com.searcam.ui.components.RiskGauge
import com.searcam.ui.components.riskLevelToContainerColor
import com.searcam.ui.theme.SearCamTheme

/**
 * 스캔 결과 화면
 *
 * RiskGauge + 근거 목록 + 한계 고지 + 액션 버튼을 표시한다.
 * Quick Scan 결과에서는 Full Scan 유도 버튼도 표시한다.
 *
 * @param report 표시할 스캔 리포트
 * @param onNavigateBack 뒤로가기
 * @param onNavigateToFullScan Full Scan 화면으로 이동
 * @param onSaveReport 리포트 저장
 * @param onRescan 다시 스캔
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanResultScreen(
    report: ScanReport,
    onNavigateBack: () -> Unit,
    onNavigateToFullScan: () -> Unit,
    onSaveReport: () -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("스캔 결과") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // 위험도 게이지
            RiskGauge(
                score = report.riskScore,
                riskLevel = report.riskLevel,
                size = 160.dp,
                animate = true,
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 등급 배지
            RiskBadge(riskLevel = report.riskLevel)
            Spacer(modifier = Modifier.height(12.dp))

            // 안내 메시지 카드 (등급별 색상)
            RiskMessageCard(riskLevel = report.riskLevel)
            Spacer(modifier = Modifier.height(24.dp))

            // 레이어별 결과 목록
            Text(
                text = "탐지 결과 상세",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
            report.layerResults.forEach { (layerType, layerResult) ->
                LayerResultCard(layerType = layerType, layerResult = layerResult)
                Spacer(modifier = Modifier.height(8.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))

            // 근거 목록 (Findings)
            if (report.findings.isNotEmpty()) {
                FindingsSection(findings = report.findings)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Quick Scan 한계 고지
            LimitationNotice()
            Spacer(modifier = Modifier.height(24.dp))

            // 액션 버튼 그리드
            ActionButtons(
                isQuickScan = report.mode.layers.size == 1,
                onSaveReport = onSaveReport,
                onFullScan = onNavigateToFullScan,
                onRescan = onRescan,
            )
        }
    }
}

/**
 * 등급별 안내 메시지 카드
 */
@Composable
private fun RiskMessageCard(riskLevel: RiskLevel) {
    val message = when (riskLevel) {
        RiskLevel.SAFE -> "탐지된 위협이 없습니다. 안전한 환경으로 판단됩니다."
        RiskLevel.INTEREST -> "경미한 의심 징후가 있습니다. 추가 확인을 권장합니다."
        RiskLevel.CAUTION -> "복수의 의심 징후가 감지되었습니다. 주의 깊게 점검해 주세요."
        RiskLevel.DANGER -> "강한 의심 징후가 발견되었습니다. 즉각적인 점검을 권장합니다."
        RiskLevel.CRITICAL -> "몰래카메라 의심이 강합니다. 즉시 신고 및 전문가 점검을 받으세요."
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = riskLevelToContainerColor(riskLevel),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = message,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/**
 * 레이어별 분석 결과 카드
 */
@Composable
private fun LayerResultCard(layerType: LayerType, layerResult: LayerResult) {
    val layerLabel = when (layerType) {
        LayerType.WIFI -> "Wi-Fi 네트워크"
        LayerType.LENS -> "렌즈 역반사"
        LayerType.IR -> "IR LED 탐지"
        LayerType.MAGNETIC -> "자기장 분석"
    }

    val statusText = when (layerResult.status) {
        ScanStatus.COMPLETED -> "${layerResult.score}점"
        ScanStatus.FAILED -> "실패"
        ScanStatus.RUNNING -> "진행 중"
        ScanStatus.PENDING -> "대기 중"
        ScanStatus.SKIPPED -> "미실행"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = layerLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (layerResult.devices.isNotEmpty()) {
                    Text(
                        text = "기기 ${layerResult.devices.size}대 발견",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (layerResult.findings.isNotEmpty()) {
                    Text(
                        text = "이상 징후 ${layerResult.findings.size}건",
                        fontSize = 12.sp,
                        color = Color(0xFFF97316),
                    )
                }
            }
            Text(
                text = statusText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (layerResult.score > 0) Color(0xFFF97316)
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 발견 사항(Findings) 섹션
 */
@Composable
private fun FindingsSection(findings: List<Finding>) {
    Text(
        text = "발견된 이상 징후",
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    )
    findings.forEach { finding ->
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(android.graphics.Color.parseColor(finding.severity.colorHex)),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = finding.type.labelKo,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = finding.description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = finding.evidence,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 한계 고지 카드 — 결과 하단에 항상 표시
 */
@Composable
private fun LimitationNotice() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "이 앱은 전문 탐지 장비를 대체하지 않습니다. " +
                    "탐지되지 않았다고 해서 안전을 보장하지 않습니다. " +
                    "강하게 의심되는 경우 112에 신고해주세요.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 액션 버튼 그리드 (리포트 저장 / Full Scan / 다시 스캔)
 */
@Composable
private fun ActionButtons(
    isQuickScan: Boolean,
    onSaveReport: () -> Unit,
    onFullScan: () -> Unit,
    onRescan: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(
                onClick = onSaveReport,
                modifier = Modifier.weight(1f),
            ) {
                Text("리포트 저장")
            }
            if (isQuickScan) {
                Button(
                    onClick = onFullScan,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Full Scan")
                }
            }
        }
        OutlinedButton(
            onClick = onRescan,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("다시 스캔")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ScanResultScreenPreview() {
    SearCamTheme {
        ScanResultScreen(
            report = ScanReport(
                id = "preview",
                mode = com.searcam.domain.model.ScanMode.QUICK,
                startedAt = System.currentTimeMillis() - 15000,
                completedAt = System.currentTimeMillis(),
                riskScore = 72,
                riskLevel = RiskLevel.DANGER,
                devices = emptyList(),
                findings = emptyList(),
                layerResults = mapOf(
                    LayerType.WIFI to LayerResult(
                        layerType = LayerType.WIFI,
                        status = ScanStatus.COMPLETED,
                        score = 72,
                        devices = emptyList(),
                        durationMs = 12000,
                        findings = emptyList(),
                    )
                ),
                correctionFactor = 1.0f,
                locationNote = "",
                retroPoints = emptyList(),
                irPoints = emptyList(),
                magneticReadings = emptyList(),
            ),
            onNavigateBack = {},
            onNavigateToFullScan = {},
            onSaveReport = {},
            onRescan = {},
        )
    }
}
