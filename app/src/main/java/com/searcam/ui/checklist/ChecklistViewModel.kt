package com.searcam.ui.checklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 장소 유형 열거형
 *
 * 각 유형마다 다른 체크리스트 항목을 제공한다.
 */
enum class PlaceType(val labelKo: String, val emoji: String, val description: String) {
    ACCOMMODATION("숙소", "🏨", "호텔, 펜션, 에어비앤비 등 숙박 시설"),
    BATHROOM("화장실", "🚽", "공용 화장실, 화장실 부스"),
    CHANGING_ROOM("탈의실", "👗", "수영장, 헬스장, 매장 피팅룸"),
    OTHER("기타", "📍", "기타 의심되는 장소"),
}

/**
 * 체크리스트 단일 항목
 */
data class ChecklistItem(
    val id: String,
    val description: String,
    val isChecked: Boolean = false,
    val priority: Priority = Priority.NORMAL,
) {
    enum class Priority { HIGH, NORMAL }
}

/**
 * 체크리스트 UI 상태
 */
sealed class ChecklistUiState {
    /** 장소 유형 선택 대기 */
    data object SelectingPlace : ChecklistUiState()

    /**
     * 체크리스트 진행 중
     *
     * @param placeType 선택된 장소 유형
     * @param items 체크리스트 항목 목록
     */
    data class Checking(
        val placeType: PlaceType,
        val items: List<ChecklistItem>,
    ) : ChecklistUiState()
}

/**
 * 체크리스트 화면 일회성 이벤트
 */
sealed class ChecklistUiEvent {
    data class NavigateToChecklist(val placeType: PlaceType) : ChecklistUiEvent()
    data class ShowSnackbar(val message: String) : ChecklistUiEvent()
}

/**
 * 체크리스트 화면 ViewModel
 *
 * 장소 유형 선택과 체크리스트 항목 상태를 관리한다.
 * 체크리스트 항목은 장소 유형에 따라 동적으로 생성된다.
 */
@HiltViewModel
class ChecklistViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow<ChecklistUiState>(ChecklistUiState.SelectingPlace)
    val uiState: StateFlow<ChecklistUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ChecklistUiEvent>()
    val events: SharedFlow<ChecklistUiEvent> = _events.asSharedFlow()

    /**
     * 장소 유형을 선택하고 해당 체크리스트를 로드한다.
     *
     * @param placeType 선택된 장소 유형
     */
    fun selectPlaceType(placeType: PlaceType) {
        val items = generateChecklistItems(placeType)
        _uiState.value = ChecklistUiState.Checking(
            placeType = placeType,
            items = items,
        )
        viewModelScope.launch {
            _events.emit(ChecklistUiEvent.NavigateToChecklist(placeType))
        }
    }

    /**
     * 체크리스트 항목의 체크 상태를 토글한다.
     *
     * @param itemId 토글할 항목 ID
     */
    fun toggleItem(itemId: String) {
        val current = _uiState.value
        if (current !is ChecklistUiState.Checking) return

        val updatedItems = current.items.map { item ->
            if (item.id == itemId) item.copy(isChecked = !item.isChecked)
            else item
        }
        _uiState.value = current.copy(items = updatedItems)
    }

    /**
     * 체크리스트를 초기화하고 장소 선택 상태로 돌아간다.
     */
    fun resetChecklist() {
        _uiState.value = ChecklistUiState.SelectingPlace
    }

    /**
     * 장소 유형에 따라 체크리스트 항목을 생성한다.
     *
     * 각 장소 유형마다 우선순위가 높은 항목과 일반 항목으로 구성된다.
     */
    private fun generateChecklistItems(placeType: PlaceType): List<ChecklistItem> {
        val commonItems = listOf(
            ChecklistItem("common_1", "전원 콘센트 주변 확인", priority = ChecklistItem.Priority.HIGH),
            ChecklistItem("common_2", "구멍이 있는 물체 확인 (벽, 가구)"),
            ChecklistItem("common_3", "스피커, 화재 감지기 확인"),
            ChecklistItem("common_4", "Wi-Fi 라우터가 아닌 의심 기기 확인"),
        )

        val typeItems = when (placeType) {
            PlaceType.ACCOMMODATION -> listOf(
                ChecklistItem("acc_1", "TV 방향 및 각도 확인", priority = ChecklistItem.Priority.HIGH),
                ChecklistItem("acc_2", "시계, 액자 등 장식품 이면 확인", priority = ChecklistItem.Priority.HIGH),
                ChecklistItem("acc_3", "옷걸이, 가방 걸이 확인"),
                ChecklistItem("acc_4", "에어컨 내부 확인"),
                ChecklistItem("acc_5", "화장실/샤워실 환기구 확인"),
            )
            PlaceType.BATHROOM -> listOf(
                ChecklistItem("bath_1", "천장 환기구 확인", priority = ChecklistItem.Priority.HIGH),
                ChecklistItem("bath_2", "벽 구멍 및 타일 사이 확인", priority = ChecklistItem.Priority.HIGH),
                ChecklistItem("bath_3", "수도꼭지 주변 확인"),
                ChecklistItem("bath_4", "화장지 거치대 주변 확인"),
            )
            PlaceType.CHANGING_ROOM -> listOf(
                ChecklistItem("change_1", "거울 뒤 확인 (반사 테스트)", priority = ChecklistItem.Priority.HIGH),
                ChecklistItem("change_2", "커튼 레일 및 고리 확인", priority = ChecklistItem.Priority.HIGH),
                ChecklistItem("change_3", "옷걸이 고리 및 못 확인"),
                ChecklistItem("change_4", "바닥 가방 보관대 확인"),
            )
            PlaceType.OTHER -> listOf(
                ChecklistItem("other_1", "비정상적인 전자기기 확인", priority = ChecklistItem.Priority.HIGH),
                ChecklistItem("other_2", "작은 구멍이 있는 물체 확인"),
            )
        }

        return typeItems + commonItems
    }
}
