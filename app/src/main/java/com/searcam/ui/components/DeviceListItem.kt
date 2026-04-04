package com.searcam.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.searcam.domain.model.DeviceType
import com.searcam.domain.model.DiscoveryMethod
import com.searcam.domain.model.NetworkDevice
import com.searcam.domain.model.RiskLevel
import com.searcam.ui.theme.SearCamTheme

/**
 * 네트워크 기기 목록 아이템 컴포넌트
 *
 * IP, MAC, 위험도 배지를 표시하며 카메라로 의심되는 기기는 강조 표시된다.
 * 오른쪽에 ScoreBadge로 점수를 표시한다.
 *
 * @param device 표시할 네트워크 기기 정보
 */
@Composable
fun DeviceListItem(
    device: NetworkDevice,
    modifier: Modifier = Modifier,
) {
    val riskLevel = RiskLevel.fromScore(device.riskScore)
    val isCamera = device.isCamera

    // 카메라 의심 기기는 배경 강조
    val backgroundModifier = if (isCamera) {
        modifier
            .fillMaxWidth()
            .background(
                color = riskLevelToContainerColor(riskLevel).copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(12.dp)
    } else {
        modifier
            .fillMaxWidth()
            .padding(12.dp)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = backgroundModifier,
    ) {
        // 기기 유형 아이콘
        Icon(
            imageVector = if (isCamera) Icons.Default.Videocam else Icons.Default.Router,
            contentDescription = if (isCamera) "카메라 의심 기기" else "일반 기기",
            tint = if (isCamera) riskLevelToColor(riskLevel) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))

        // 기기 정보 (IP, 벤더, MAC)
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = device.ip,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (isCamera) {
                    Text(
                        text = "카메라 의심",
                        fontSize = 10.sp,
                        color = riskLevelToColor(riskLevel),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Text(
                text = device.vendor ?: device.hostname ?: "알 수 없는 기기",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = device.mac,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Light,
            )
            // 개방 포트 표시
            if (device.openPorts.isNotEmpty()) {
                Text(
                    text = "포트: ${device.openPorts.joinToString(", ")}",
                    fontSize = 11.sp,
                    color = if (isCamera) riskLevelToColor(riskLevel)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 위험도 배지
        ScoreBadge(score = device.riskScore, riskLevel = riskLevel)
    }
}

@Preview(showBackground = true)
@Composable
private fun DeviceListItemPreview() {
    SearCamTheme {
        Column(modifier = Modifier.padding(8.dp)) {
            DeviceListItem(
                device = NetworkDevice(
                    ip = "192.168.1.45",
                    mac = "28:57:BE:11:22:33",
                    hostname = "hikvision-cam",
                    vendor = "Hikvision",
                    deviceType = DeviceType.IP_CAMERA,
                    openPorts = listOf(554, 80),
                    services = listOf("_rtsp._tcp"),
                    riskScore = 72,
                    isCamera = true,
                    discoveryMethod = DiscoveryMethod.ARP,
                    discoveredAt = System.currentTimeMillis(),
                ),
            )
            DeviceListItem(
                device = NetworkDevice(
                    ip = "192.168.1.1",
                    mac = "AA:BB:CC:DD:EE:FF",
                    hostname = "router",
                    vendor = "TP-Link",
                    deviceType = DeviceType.ROUTER,
                    openPorts = listOf(80, 443),
                    services = emptyList(),
                    riskScore = 5,
                    isCamera = false,
                    discoveryMethod = DiscoveryMethod.ARP,
                    discoveredAt = System.currentTimeMillis(),
                ),
            )
        }
    }
}
