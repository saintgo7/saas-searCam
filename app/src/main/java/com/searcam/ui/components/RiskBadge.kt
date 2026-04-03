package com.searcam.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.searcam.domain.model.RiskLevel
import com.searcam.ui.theme.SearCamTheme

/**
 * 위험 등급 뱃지 컴포넌트
 *
 * SAFE/INTEREST/CAUTION/DANGER/CRITICAL 각 등급에 대응하는 색상과 라벨로 표시된다.
 * 컨테이너 배경 + 텍스트 색상 조합으로 시인성을 보장한다.
 *
 * @param riskLevel 표시할 위험 등급
 * @param showIcon 경고 아이콘 표시 여부 (기본 true)
 */
@Composable
fun RiskBadge(
    riskLevel: RiskLevel,
    modifier: Modifier = Modifier,
    showIcon: Boolean = true,
) {
    val backgroundColor = riskLevelToContainerColor(riskLevel)
    val contentColor = riskLevelToColor(riskLevel)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        if (showIcon) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = riskLevel.labelKo,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
        )
    }
}

/**
 * 점수를 표시하는 소형 뱃지 — 리스트 아이템용
 *
 * @param score 위험도 점수 (0~100)
 * @param riskLevel 위험 등급
 */
@Composable
fun ScoreBadge(
    score: Int,
    riskLevel: RiskLevel,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = riskLevelToContainerColor(riskLevel)
    val contentColor = riskLevelToColor(riskLevel)

    Text(
        text = "$score",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = contentColor,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * Severity 색상 상수 — Finding 심각도 표시용
 */
fun severityColor(colorHex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(colorHex))
    } catch (e: IllegalArgumentException) {
        Color.Gray
    }
}

@Preview(showBackground = true)
@Composable
private fun RiskBadgePreview() {
    SearCamTheme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(8.dp),
        ) {
            RiskBadge(riskLevel = RiskLevel.SAFE)
            RiskBadge(riskLevel = RiskLevel.CAUTION)
            RiskBadge(riskLevel = RiskLevel.CRITICAL)
        }
    }
}
