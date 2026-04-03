package com.searcam.ui.lens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.searcam.domain.model.IrPoint
import com.searcam.ui.theme.SearCamTheme

/**
 * IR 카메라 모드 화면
 *
 * 전면 카메라 프리뷰 위에 Canvas로 IR 포인트(노란 원)를 시각화한다.
 * 전면 카메라의 IR 차단 필터 취약점을 이용하여 IR LED를 포착한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IrCameraScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LensViewModel = hiltViewModel(),
) {
    val irUiState by viewModel.irUiState.collectAsStateWithLifecycle()

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // 화면 진입/이탈 시 IR 탐지 시작/중단
    DisposableEffect(lifecycleOwner) {
        viewModel.startIrDetection(lifecycleOwner)
        onDispose {
            viewModel.stopIrDetection()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("IR 카메라") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // 전면 카메라 프리뷰 자리 표시자
            // 실제 구현: AndroidView(factory = { PreviewView(it) })
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "전면 카메라 프리뷰",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 16.sp,
                )
            }

            // IR 포인트 오버레이
            when (val state = irUiState) {
                is IrUiState.Detecting -> {
                    IrPointOverlay(irPoints = state.irPoints)
                    IrStatusText(
                        pointCount = state.irPoints.size,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp),
                    )
                }
                is IrUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 14.sp,
                        )
                    }
                }
                else -> {
                    IrStatusText(
                        pointCount = 0,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp),
                    )
                }
            }
        }
    }
}

/**
 * IR 포인트 Canvas 오버레이
 *
 * 감지된 IR 포인트를 노란 원으로 표시한다.
 * 강도(intensity)에 따라 원의 투명도가 달라진다.
 *
 * @param irPoints 감지된 IR 포인트 목록
 */
@Composable
private fun IrPointOverlay(irPoints: List<IrPoint>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        irPoints.forEach { point ->
            val px = point.x.toFloat()
            val py = point.y.toFloat()
            val radius = (point.intensity / 255f * 30f + 15f)
            val alpha = (point.intensity / 255f * 0.8f + 0.2f).coerceIn(0f, 1f)

            // 노란 원 (외곽선) — IR 포인트 표시
            drawCircle(
                color = Color(0xFFFFEB3B).copy(alpha = alpha),
                radius = radius,
                center = Offset(px, py),
                style = Stroke(width = 2.5f.dp.toPx()),
            )
            // 반투명 채우기
            drawCircle(
                color = Color(0x44FFD600),
                radius = radius,
                center = Offset(px, py),
            )
        }
    }
}

/**
 * IR 탐지 상태 텍스트
 */
@Composable
private fun IrStatusText(pointCount: Int, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        if (pointCount > 0) {
            Text(
                text = "IR 포인트 ${pointCount}개 감지",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFEB3B),
            )
        } else {
            Text(
                text = "전면 카메라를 의심 기기 방향으로 향하세요",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "자주색/흰색 빛이 보이면 IR LED 의심 징후입니다",
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.6f),
        )
    }
}

@Preview
@Composable
private fun IrCameraScreenPreview() {
    SearCamTheme {
        Column {
            Text(
                text = "IR 카메라 화면 - 전면 카메라 + 노란 원 오버레이",
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
