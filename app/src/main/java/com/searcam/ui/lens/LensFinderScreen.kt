package com.searcam.ui.lens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.searcam.domain.model.RetroreflectionPoint
import com.searcam.ui.theme.SearCamTheme

/**
 * 렌즈 찾기 화면 (Retroreflection 모드)
 *
 * 후면 카메라 프리뷰 위에 Canvas 오버레이로 의심 포인트(빨간 원)를 표시한다.
 * 우하단 FAB으로 플래시 ON/OFF를 토글할 수 있다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LensFinderScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LensViewModel = hiltViewModel(),
) {
    val lensUiState by viewModel.lensUiState.collectAsStateWithLifecycle()
    val isFlashOn by viewModel.isFlashOn.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current

    // 화면 진입/이탈 시 탐지 시작/중단
    DisposableEffect(lifecycleOwner) {
        viewModel.startLensDetection(lifecycleOwner)
        onDispose {
            viewModel.stopLensDetection()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("렌즈 찾기") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleFlash) {
                        Icon(
                            imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = if (isFlashOn) "플래시 끄기" else "플래시 켜기",
                            tint = if (isFlashOn) Color.Yellow else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
            // CameraX 프리뷰 영역 — 실제 구현에서 PreviewView를 AndroidView로 래핑
            CameraPreviewPlaceholder()

            // 역반사 포인트 오버레이
            when (val state = lensUiState) {
                is LensUiState.Detecting -> {
                    RetroreflectionOverlay(retroPoints = state.retroPoints)
                }
                is LensUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = state.message,
                            color = Color.White,
                            fontSize = 14.sp,
                        )
                    }
                }
                else -> {}
            }

            // 하단 안내 텍스트
            BottomGuideText(
                pointCount = (lensUiState as? LensUiState.Detecting)?.retroPoints?.size ?: 0,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
            )
        }
    }
}

/**
 * CameraX PreviewView 자리 표시자
 *
 * 실제 구현에서는 AndroidView로 PreviewView를 포함해야 한다.
 * 여기서는 다크 배경으로 대체한다.
 */
@Composable
private fun CameraPreviewPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // 실제 구현:
        // AndroidView(
        //     factory = { context ->
        //         PreviewView(context).apply {
        //             scaleType = PreviewView.ScaleType.FILL_CENTER
        //         }
        //     },
        //     modifier = Modifier.fillMaxSize(),
        // )
        Text(
            text = "카메라 프리뷰",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 16.sp,
        )
    }
}

/**
 * 역반사 포인트 Canvas 오버레이
 *
 * 감지된 각 포인트에 빨간 원(펄스 없음)을 그린다.
 * 실제 좌표는 normalized (0~1) 값을 화면 크기에 곱하여 사용한다.
 *
 * @param retroPoints 역반사 포인트 목록
 */
@Composable
private fun RetroreflectionOverlay(retroPoints: List<RetroreflectionPoint>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        retroPoints.forEach { point ->
            // 포인트 좌표를 Canvas 크기로 변환 (프레임 pixel → Canvas pixel 비율)
            val px = point.x.toFloat()
            val py = point.y.toFloat()
            val radius = point.radius.coerceAtLeast(20f)

            // 빨간 원 (외곽선)
            drawCircle(
                color = Color(0xFFFF6B6B),
                radius = radius,
                center = Offset(px, py),
                style = Stroke(width = 3.dp.toPx()),
            )
            // 반투명 채우기
            drawCircle(
                color = Color(0x33FF6B6B),
                radius = radius,
                center = Offset(px, py),
            )
        }
    }
}

/**
 * 하단 안내 텍스트 — 발견 포인트 수 표시
 */
@Composable
private fun BottomGuideText(pointCount: Int, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        if (pointCount > 0) {
            Text(
                text = "의심 포인트 ${pointCount}개 발견",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF6B6B),
            )
        } else {
            Text(
                text = "카메라를 의심 지점으로 천천히 이동하세요",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "플래시를 켜고 사용하면 탐지 정확도가 높아집니다",
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.6f),
        )
    }
}

@Preview
@Composable
private fun LensFinderScreenPreview() {
    SearCamTheme {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp)) {
                Text("렌즈 찾기 화면 - 카메라 프리뷰 + 오버레이")
            }
        }
    }
}
