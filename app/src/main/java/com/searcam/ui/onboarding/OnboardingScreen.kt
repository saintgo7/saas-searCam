package com.searcam.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.searcam.ui.theme.SearCamTheme
import kotlinx.coroutines.launch

/**
 * 온보딩 페이지 데이터 모델
 */
private data class OnboardingPage(
    val emoji: String,
    val title: String,
    val description: String,
)

/**
 * 3개 온보딩 페이지 콘텐츠
 *
 * 1. 30초 안전 점검 소개
 * 2. 3중 교차 검증 작동 원리
 * 3. 앱 한계 고지 (솔직한 안내)
 */
private val onboardingPages = listOf(
    OnboardingPage(
        emoji = "📱",
        title = "30초 안전 점검",
        description = "숙소, 화장실, 탈의실을 스마트폰으로 빠르게 점검하세요.\n버튼 하나로 30초 안에 결과를 확인할 수 있습니다.",
    ),
    OnboardingPage(
        emoji = "🔍",
        title = "3중 교차 검증",
        description = "Wi-Fi 스캔 + 렌즈 역반사 감지 + 자기장 분석.\n세 가지 방법을 동시에 사용해 탐지 정확도를 높입니다.",
    ),
    OnboardingPage(
        emoji = "🛡️",
        title = "솔직한 안내",
        description = "이 앱은 전문 탐지 장비를 대체하지 않습니다.\n탐지되지 않았다고 안전을 보장하지 않습니다.\n강하게 의심되면 112에 신고해주세요.",
    ),
)

/**
 * 온보딩 화면
 *
 * HorizontalPager 3페이지 + 페이지 인디케이터 + 다음/시작/건너뛰기 버튼으로 구성된다.
 * 마지막 페이지에서 "시작하기"를 탭하면 권한 요청 화면(또는 홈)으로 이동한다.
 * 좌우 스와이프로 페이지 전환이 가능하다.
 *
 * @param onComplete 온보딩 완료 콜백
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // 건너뛰기 버튼 (우상단)
        TextButton(
            onClick = onComplete,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
        ) {
            Text(
                text = "건너뛰기",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(80.dp))

            // 페이지 콘텐츠 (Pager)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                OnboardingPageContent(page = onboardingPages[page])
            }

            // 페이지 인디케이터
            PageIndicator(
                pageCount = onboardingPages.size,
                currentPage = pagerState.currentPage,
            )
            Spacer(modifier = Modifier.height(32.dp))

            // 다음/시작하기 버튼
            Button(
                onClick = {
                    val isLastPage = pagerState.currentPage == onboardingPages.size - 1
                    if (isLastPage) {
                        onComplete()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (pagerState.currentPage == onboardingPages.size - 1) "시작하기"
                    else "다음",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * 개별 온보딩 페이지 콘텐츠
 *
 * 이모지 일러스트 + 제목 + 설명으로 구성된다.
 */
@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        // 일러스트 영역 (Lottie 대신 이모지 사용)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Text(
                text = page.emoji,
                fontSize = 80.sp,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(40.dp))

        // 제목
        Text(
            text = page.title,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))

        // 설명
        Text(
            text = page.description,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
        )
    }
}

/**
 * 페이지 인디케이터
 *
 * 현재 페이지 = Primary 색상 긴 원형, 나머지 = Gray 작은 원
 * 페이지 전환 시 200ms 애니메이션으로 크기와 색상이 변화한다.
 *
 * @param pageCount 전체 페이지 수
 * @param currentPage 현재 활성 페이지 인덱스
 */
@Composable
private fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        repeat(pageCount) { index ->
            val isActive = index == currentPage

            val width: Dp by animateDpAsState(
                targetValue = if (isActive) 24.dp else 6.dp,
                animationSpec = tween(durationMillis = 200),
                label = "indicator_width_$index",
            )
            val color: Color by animateColorAsState(
                targetValue = if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                animationSpec = tween(durationMillis = 200),
                label = "indicator_color_$index",
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(width = width, height = 6.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingScreenPreview() {
    SearCamTheme {
        OnboardingScreen(onComplete = {})
    }
}
