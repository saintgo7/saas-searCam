package com.searcam.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.searcam.domain.model.RiskLevel
import com.searcam.ui.theme.SearCamTheme

/**
 * 원형 위험도 게이지 컴포넌트
 *
 * 0~100 점수를 반원 호(arc)로 표시하며, 점수에 따라 색상이 그라데이션으로 변화한다.
 * 점수는 진입 시 0에서 최종값까지 1초간 EaseOut 애니메이션으로 표시된다.
 *
 * @param score 위험도 점수 (0~100)
 * @param riskLevel 위험 등급 (색상 및 라벨 연동)
 * @param size 게이지 지름 (기본 160dp)
 * @param animate 진입 애니메이션 활성 여부
 */
@Composable
fun RiskGauge(
    score: Int,
    riskLevel: RiskLevel,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    animate: Boolean = true,
) {
    // 진입 애니메이션: 0 → score
    val animatedScore = remember { Animatable(0f) }
    LaunchedEffect(score) {
        if (animate) {
            animatedScore.animateTo(
                targetValue = score.toFloat(),
                animationSpec = tween(durationMillis = 1000, easing = EaseOut),
            )
        } else {
            animatedScore.snapTo(score.toFloat())
        }
    }

    // 위험도별 게이지 색상 — 점수 구간별 선형 보간
    val gaugeColor = riskLevelToColor(riskLevel)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size),
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokeWidth = (size.toPx() * 0.1f)
            val radius = (this.size.minDimension - strokeWidth) / 2f
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
            val arcSize = Size(radius * 2, radius * 2)

            // 배경 트랙 (회색 전체 호)
            drawArc(
                color = trackColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            // 점수 호 (그라데이션)
            val sweepAngle = (animatedScore.value / 100f) * 270f
            if (sweepAngle > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0xFF22C55E), // 안전
                            0.2f to Color(0xFF84CC16), // 관심
                            0.4f to Color(0xFFEAB308), // 주의
                            0.6f to Color(0xFFF97316), // 위험
                            1.0f to Color(0xFFEF4444), // 매우 위험
                        ),
                        center = center,
                    ),
                    startAngle = 135f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
        }

        // 중앙 텍스트 — 점수 + 등급 라벨
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = animatedScore.value.toInt().toString(),
                fontSize = (size.value * 0.22f).sp,
                fontWeight = FontWeight.Bold,
                color = gaugeColor,
            )
            Text(
                text = riskLevel.labelKo,
                fontSize = (size.value * 0.1f).sp,
                fontWeight = FontWeight.Medium,
                color = gaugeColor,
            )
        }
    }
}

/**
 * RiskLevel을 UI 색상으로 변환한다.
 */
fun riskLevelToColor(level: RiskLevel): Color = when (level) {
    RiskLevel.SAFE -> Color(0xFF22C55E)
    RiskLevel.INTEREST -> Color(0xFF84CC16)
    RiskLevel.CAUTION -> Color(0xFFEAB308)
    RiskLevel.DANGER -> Color(0xFFF97316)
    RiskLevel.CRITICAL -> Color(0xFFEF4444)
}

/**
 * RiskLevel의 컨테이너 배경색을 반환한다.
 */
fun riskLevelToContainerColor(level: RiskLevel): Color = when (level) {
    RiskLevel.SAFE -> Color(0xFFDCFCE7)
    RiskLevel.INTEREST -> Color(0xFFECFCCB)
    RiskLevel.CAUTION -> Color(0xFFFEF9C3)
    RiskLevel.DANGER -> Color(0xFFFFEDD5)
    RiskLevel.CRITICAL -> Color(0xFFFEE2E2)
}

@Preview(showBackground = true, backgroundColor = 0xFF111827)
@Composable
private fun RiskGaugePreview() {
    SearCamTheme {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RiskGauge(score = 72, riskLevel = RiskLevel.DANGER, animate = false)
            RiskGauge(score = 15, riskLevel = RiskLevel.SAFE, animate = false, size = 120.dp)
        }
    }
}
