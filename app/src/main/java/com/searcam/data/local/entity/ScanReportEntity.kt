package com.searcam.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 스캔 리포트 Room Entity
 *
 * ScanReport 도메인 모델을 SQLite에 저장하기 위한 테이블 정의.
 * 복잡한 중첩 객체(devices, findings 등)는 JSON으로 직렬화하여 TEXT로 저장한다.
 */
@Entity(tableName = "scan_reports")
data class ScanReportEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "mode")
    val mode: String,

    @ColumnInfo(name = "started_at")
    val startedAt: Long,

    @ColumnInfo(name = "completed_at")
    val completedAt: Long,

    @ColumnInfo(name = "risk_score")
    val riskScore: Int,

    @ColumnInfo(name = "risk_level")
    val riskLevel: String,

    /** 기기 목록 JSON 직렬화 값 */
    @ColumnInfo(name = "devices_json")
    val devicesJson: String = "[]",

    /** 발견 사항 목록 JSON 직렬화 값 */
    @ColumnInfo(name = "findings_json")
    val findingsJson: String = "[]",

    /** 위치 메모 */
    @ColumnInfo(name = "location_note")
    val locationNote: String = "",

    /** 생성 시각 (정렬용) */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
