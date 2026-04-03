package com.searcam.ui.checklist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.searcam.ui.theme.SearCamTheme

/**
 * 육안 점검 체크리스트 화면
 *
 * 선택된 장소 유형에 맞는 체크리스트를 LazyColumn으로 표시한다.
 * 각 항목은 Checkbox로 체크하며, 진행률이 상단 바로 표시된다.
 * HIGH 우선순위 항목은 빨간 라벨로 강조된다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChecklistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // SelectingPlace 상태이면 이미 뒤로 이동했어야 함 — 안전을 위해 대기
    val checkingState = uiState as? ChecklistUiState.Checking ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "${checkingState.placeType.emoji} ${checkingState.placeType.labelKo} 점검"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.resetChecklist()
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        val items = checkingState.items
        val checkedCount = items.count { it.isChecked }
        val progress = if (items.isEmpty()) 0f else checkedCount.toFloat() / items.size

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // 진행률 바
            ChecklistProgressHeader(
                checkedCount = checkedCount,
                totalCount = items.size,
                progress = progress,
            )

            // 체크리스트 항목 목록
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            ) {
                // 중요 항목 먼저 표시
                val highItems = items.filter { it.priority == ChecklistItem.Priority.HIGH }
                val normalItems = items.filter { it.priority == ChecklistItem.Priority.NORMAL }

                if (highItems.isNotEmpty()) {
                    item {
                        Text(
                            text = "주요 확인 항목",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                        )
                    }
                    items(items = highItems, key = { it.id }) { item ->
                        ChecklistItemRow(
                            item = item,
                            onToggle = { viewModel.toggleItem(item.id) },
                        )
                        Divider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 0.5.dp,
                        )
                    }
                }

                if (normalItems.isNotEmpty()) {
                    item {
                        Text(
                            text = "일반 확인 항목",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                        )
                    }
                    items(items = normalItems, key = { it.id }) { item ->
                        ChecklistItemRow(
                            item = item,
                            onToggle = { viewModel.toggleItem(item.id) },
                        )
                        Divider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 0.5.dp,
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }

            // 처음부터 다시 버튼
            OutlinedButton(
                onClick = {
                    viewModel.resetChecklist()
                    onNavigateBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text("처음부터 다시 점검")
            }
        }
    }
}

/**
 * 체크리스트 진행률 헤더
 */
@Composable
private fun ChecklistProgressHeader(
    checkedCount: Int,
    totalCount: Int,
    progress: Float,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "완료: $checkedCount / $totalCount",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = if (progress >= 1f) Color(0xFF22C55E)
            else MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * 체크리스트 단일 항목 행
 *
 * Checkbox + 항목 설명 텍스트로 구성된다.
 * 체크된 항목은 취소선으로 표시된다.
 */
@Composable
private fun ChecklistItemRow(
    item: ChecklistItem,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Checkbox(
            checked = item.isChecked,
            onCheckedChange = { onToggle() },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.description,
                fontSize = 14.sp,
                color = if (item.isChecked) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
            )
            if (item.priority == ChecklistItem.Priority.HIGH) {
                Text(
                    text = "중요",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChecklistScreenPreview() {
    SearCamTheme {
        ChecklistScreen(onNavigateBack = {})
    }
}
