package com.searcam.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 체크리스트 수행 기록 Room Entity
 *
 * 숙소 점검 체크리스트 수행 결과를 저장한다.
 */
@Entity(tableName = "checklists")
data class ChecklistEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** 체크리스트 템플릿 식별자 (예: "hotel", "bathroom") */
    @ColumnInfo(name = "template_id")
    val templateId: String,

    /** 수행 시각 (Unix epoch millis) */
    @ColumnInfo(name = "performed_at")
    val performedAt: Long = System.currentTimeMillis(),

    /** 위치 메모 */
    @ColumnInfo(name = "location_note")
    val locationNote: String = "",

    /** 체크 항목 결과 JSON (항목명 → 완료여부 Map) */
    @ColumnInfo(name = "items_json")
    val itemsJson: String = "{}",

    /** 전체 항목 수 */
    @ColumnInfo(name = "total_items")
    val totalItems: Int = 0,

    /** 완료된 항목 수 */
    @ColumnInfo(name = "completed_items")
    val completedItems: Int = 0
)
