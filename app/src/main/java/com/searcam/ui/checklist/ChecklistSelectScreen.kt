package com.searcam.ui.checklist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.searcam.ui.theme.SearCamTheme

/**
 * 체크리스트 장소 유형 선택 화면
 *
 * 숙소/화장실/탈의실/기타 4가지 유형 카드를 2×2 그리드로 표시한다.
 * 탭하면 해당 유형의 체크리스트 화면으로 이동한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistSelectScreen(
    onNavigateToChecklist: (PlaceType) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChecklistViewModel = hiltViewModel(),
) {
    // 일회성 이벤트 처리
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ChecklistUiEvent.NavigateToChecklist -> onNavigateToChecklist(event.placeType)
                is ChecklistUiEvent.ShowSnackbar -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("육안 점검 체크리스트") })
        },
        modifier = modifier,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                text = "어떤 장소를 점검하시겠습니까?",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "장소 유형에 맞는 체크리스트를 제공합니다",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))

            // 2×2 그리드 레이아웃
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                PlaceTypeCard(
                    placeType = PlaceType.ACCOMMODATION,
                    onClick = { viewModel.selectPlaceType(PlaceType.ACCOMMODATION) },
                    modifier = Modifier.weight(1f),
                )
                PlaceTypeCard(
                    placeType = PlaceType.BATHROOM,
                    onClick = { viewModel.selectPlaceType(PlaceType.BATHROOM) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                PlaceTypeCard(
                    placeType = PlaceType.CHANGING_ROOM,
                    onClick = { viewModel.selectPlaceType(PlaceType.CHANGING_ROOM) },
                    modifier = Modifier.weight(1f),
                )
                PlaceTypeCard(
                    placeType = PlaceType.OTHER,
                    onClick = { viewModel.selectPlaceType(PlaceType.OTHER) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * 장소 유형 선택 카드
 */
@Composable
private fun PlaceTypeCard(
    placeType: PlaceType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(20.dp),
        ) {
            Text(
                text = placeType.emoji,
                fontSize = 36.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = placeType.labelKo,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = placeType.description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChecklistSelectScreenPreview() {
    SearCamTheme {
        ChecklistSelectScreen(onNavigateToChecklist = {})
    }
}
