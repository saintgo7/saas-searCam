package com.searcam.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.searcam.ui.theme.SearCamTheme

/**
 * 스캔 단계 상태 열거형
 */
enum class StepStatus {
    /** 대기 중 */
    WAITING,

    /** 진행 중 */
    IN_PROGRESS,

    /** 완료 */
    COMPLETED,

    /** 실패 */
    FAILED,
}

/**
 * 스캔 단계 모델
 *
 * @param label 단계 이름 (예: "Wi-Fi 스캔")
 * @param status 현재 단계 상태
 */
data class ScanStep(
    val label: String,
    val status: StepStatus,
)

/**
 * 스캔 진행 표시 컴포넌트
 *
 * LinearProgressIndicator + 레이어별 단계 아이콘으로 진행 상황을 시각화한다.
 * IN_PROGRESS 상태의 단계는 회전 아이콘으로 표시된다.
 *
 * @param progress 전체 진행률 (0.0 ~ 1.0)
 * @param steps 각 레이어 단계 목록
 */
@Composable
fun ScanProgress(
    progress: Float,
    steps: List<ScanStep>,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "progress_anim")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        // 전체 진행률 바
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )

        // 레이어별 단계 목록
        steps.forEach { step ->
            ScanStepRow(step = step, rotationAngle = rotationAngle)
        }
    }
}

/**
 * 단일 스캔 단계 행 컴포넌트
 */
@Composable
private fun ScanStepRow(
    step: ScanStep,
    rotationAngle: Float,
) {
    val (icon, tint) = when (step.status) {
        StepStatus.COMPLETED -> Icons.Default.CheckCircle to Color(0xFF22C55E)
        StepStatus.FAILED -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.error
        StepStatus.IN_PROGRESS -> Icons.Outlined.HourglassTop to MaterialTheme.colorScheme.primary
        StepStatus.WAITING -> Icons.Default.RadioButtonUnchecked to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = step.status.name,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = step.label,
            fontSize = 14.sp,
            fontWeight = if (step.status == StepStatus.IN_PROGRESS) FontWeight.SemiBold else FontWeight.Normal,
            color = when (step.status) {
                StepStatus.WAITING -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = when (step.status) {
                StepStatus.COMPLETED -> "완료"
                StepStatus.FAILED -> "실패"
                StepStatus.IN_PROGRESS -> "진행 중"
                StepStatus.WAITING -> "대기"
            },
            fontSize = 12.sp,
            color = tint,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ScanProgressPreview() {
    SearCamTheme {
        ScanProgress(
            progress = 0.4f,
            steps = listOf(
                ScanStep("Wi-Fi 스캔", StepStatus.COMPLETED),
                ScanStep("기기 분석", StepStatus.IN_PROGRESS),
                ScanStep("포트 확인", StepStatus.WAITING),
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}
