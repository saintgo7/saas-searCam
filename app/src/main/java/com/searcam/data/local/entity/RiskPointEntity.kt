package com.searcam.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 위험 포인트 Room Entity
 *
 * 렌즈 의심 포인트(Retroreflection), IR 포인트, 자기장 이상 지점을 통합 저장한다.
 * ScanReportEntity와 1:N 관계이며, 리포트 삭제 시 CASCADE로 함께 삭제된다.
 *
 * point_type 값:
 *   LENS_RETROREFLECTION — Retroreflection 기반 렌즈 의심
 *   IR_LED               — IR LED 의심
 *   EMF_ANOMALY          — 자기장 이상
 *
 * 좌표계: x, y는 0.0~1.0 정규화 화면 좌표 (실제 픽셀이 아님)
 */
@Entity(
    tableName = "risk_points",
    foreignKeys = [
        ForeignKey(
            entity = ScanReportEntity::class,
            parentColumns = ["id"],
            childColumns = ["report_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["report_id"]),
        Index(value = ["point_type"]),
        Index(value = ["score"], orders = [Index.Order.DESC]),
    ],
)
data class RiskPointEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** 연관된 스캔 리포트 ID (FK → scan_reports.id) */
    @ColumnInfo(name = "report_id")
    val reportId: String,

    /**
     * 포인트 유형
     * 값: LENS_RETROREFLECTION | IR_LED | EMF_ANOMALY
     */
    @ColumnInfo(name = "point_type")
    val pointType: String,

    /**
     * 정규화된 X 좌표 (0.0 ~ 1.0, 화면 가로 비율)
     * 렌즈/IR 포인트에만 유효. EMF는 null.
     */
    @ColumnInfo(name = "x")
    val x: Float? = null,

    /**
     * 정규화된 Y 좌표 (0.0 ~ 1.0, 화면 세로 비율)
     * 렌즈/IR 포인트에만 유효. EMF는 null.
     */
    @ColumnInfo(name = "y")
    val y: Float? = null,

    /** 위험 점수 (0 ~ 100) */
    @ColumnInfo(name = "score")
    val score: Int,

    /**
     * 렌즈 포인트 크기 (픽셀)
     * LENS_RETROREFLECTION에만 유효.
     */
    @ColumnInfo(name = "size_px")
    val sizePx: Int? = null,

    /**
     * 원형도 (0.0 ~ 1.0)
     * LENS_RETROREFLECTION에만 유효. 렌즈: > 0.8
     */
    @ColumnInfo(name = "circularity")
    val circularity: Float? = null,

    /**
     * 상대 밝기 (0 ~ 255)
     * 렌즈/IR 포인트에 유효.
     */
    @ColumnInfo(name = "brightness")
    val brightness: Float? = null,

    /**
     * 지속 시간 (ms)
     * IR_LED 포인트에 유효. 의심 기준: > 3,000ms
     */
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long? = null,

    /**
     * 플래시 검증 통과 여부 (0/1)
     * LENS_RETROREFLECTION에만 유효.
     * true = 플래시 OFF 시 소실 확인됨
     */
    @ColumnInfo(name = "flash_verified")
    val flashVerified: Boolean? = null,

    /**
     * 자기장 변화량 (μT, 마이크로테슬라)
     * EMF_ANOMALY에만 유효.
     */
    @ColumnInfo(name = "emf_delta")
    val emfDelta: Float? = null,

    /**
     * 자기장 이상 등급
     * EMF_ANOMALY에만 유효. 값: NORMAL | INTEREST | CAUTION | SUSPECT | STRONG_SUSPECT
     */
    @ColumnInfo(name = "emf_level")
    val emfLevel: String? = null,

    /** 감지 근거 텍스트 (한국어 설명) */
    @ColumnInfo(name = "evidence")
    val evidence: String,

    /** 기록 생성 시각 (Unix epoch millis) */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)
