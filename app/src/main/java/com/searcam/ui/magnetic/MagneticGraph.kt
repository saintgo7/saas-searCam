package com.searcam.ui.magnetic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.searcam.domain.model.EmfLevel
import com.searcam.domain.model.MagneticReading
import com.searcam.ui.theme.SearCamTheme

// 그래프 축 색상 상수
private val AxisColorX = Color(0xFF00E5FF)  // 시안 (X축)
private val AxisColorY = Color(0xFFFF6B6B)  // 빨강 (Y축)
private val AxisColorZ = Color(0xFF69FF47)  // 초록 (Z축)

/**
 * Canvas 기반 실시간 3축(x/y/z) 자기장 라인 차트
 *
 * 최근 60개 포인트를 슬라이딩 윈도우로 표시한다.
 * X축: 시간, Y축: 자기장 강도 (μT)
 * 각 축은 다른 색상으로 구분된다 (X=시안, Y=빨강, Z=초록).
 *
 * @param readings 최근 N개 자기장 측정값 목록 (시간순)
 * @param modifier 크기 및 여백 설정
 */
@Composable
fun MagneticGraph(
    readings: List<MagneticReading>,
    modifier: Modifier = Modifier,
) {
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val gridLineColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier) {
        if (readings.isEmpty()) return@Canvas

        val width = size.width
        val height = size.height
        val padding = 8.dp.toPx()

        // 전체 값 범위 계산 (Y축 스케일)
        val allValues = readings.flatMap { listOf(it.x, it.y, it.z) }
        val minVal = allValues.minOrNull()?.coerceAtMost(-10f) ?: -100f
        val maxVal = allValues.maxOrNull()?.coerceAtLeast(10f) ?: 100f
        val valueRange = (maxVal - minVal).coerceAtLeast(1f)

        // 그리드 배경
        drawRect(color = surfaceVariantColor, alpha = 0.3f)

        // 수평 그리드 라인 (4개)
        repeat(4) { i ->
            val y = padding + (height - 2 * padding) * i / 3f
            drawLine(
                color = gridLineColor.copy(alpha = 0.5f),
                start = Offset(padding, y),
                end = Offset(width - padding, y),
                strokeWidth = 0.5.dp.toPx(),
            )
        }

        // 0축 기준선
        val zeroY = padding + (height - 2 * padding) * (1f - (0f - minVal) / valueRange)
        drawLine(
            color = Color.White.copy(alpha = 0.2f),
            start = Offset(padding, zeroY),
            end = Offset(width - padding, zeroY),
            strokeWidth = 1.dp.toPx(),
        )

        val n = readings.size
        val xStep = (width - 2 * padding) / n.coerceAtLeast(2).toFloat()

        /**
         * 단일 축 값 → Canvas Y 좌표 변환
         */
        fun valueToY(v: Float): Float =
            padding + (height - 2 * padding) * (1f - (v - minVal) / valueRange)

        /**
         * 주어진 축 값 목록으로 라인 Path를 그린다.
         */
        fun drawAxisLine(values: List<Float>, color: Color) {
            if (values.size < 2) return
            val path = Path()
            values.forEachIndexed { idx, v ->
                val x = padding + idx * xStep
                val y = valueToY(v)
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path = path, color = color, style = Stroke(width = 1.5.dp.toPx()))
        }

        // X, Y, Z 축 순서로 그리기
        drawAxisLine(readings.map { it.x }, AxisColorX)
        drawAxisLine(readings.map { it.y }, AxisColorY)
        drawAxisLine(readings.map { it.z }, AxisColorZ)
    }
}

/**
 * 그래프 범례 컴포넌트
 *
 * X/Y/Z 축 색상과 라벨을 표시한다.
 */
@Composable
fun MagneticGraphLegend(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        LegendItem(color = AxisColorX, label = "X축")
        Spacer(modifier = Modifier.width(12.dp))
        LegendItem(color = AxisColorY, label = "Y축")
        Spacer(modifier = Modifier.width(12.dp))
        LegendItem(color = AxisColorZ, label = "Z축")
    }
}

/**
 * 단일 범례 아이템
 */
@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = color,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF111827)
@Composable
private fun MagneticGraphPreview() {
    SearCamTheme {
        val sampleReadings = (0 until 60).map { i ->
            val t = i.toFloat() / 60f * 2 * Math.PI.toFloat()
            MagneticReading(
                timestamp = System.currentTimeMillis() + i * 50L,
                x = (20f * kotlin.math.sin(t)).toFloat(),
                y = (15f * kotlin.math.cos(t * 1.5f)).toFloat(),
                z = (30f * kotlin.math.sin(t * 0.7f)).toFloat(),
                magnitude = 30f,
                delta = 8f,
                level = EmfLevel.INTEREST,
            )
        }

        Column(modifier = Modifier.padding(16.dp)) {
            MagneticGraph(
                readings = sampleReadings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            MagneticGraphLegend()
        }
    }
}
