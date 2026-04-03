package com.searcam.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 탐지된 네트워크 기기 Room Entity
 *
 * 스캔 세션에서 발견된 기기를 scan_reports와 연결하여 저장한다.
 * reportId로 외래키를 설정해 리포트 삭제 시 관련 기기도 자동 삭제.
 */
@Entity(
    tableName = "devices",
    foreignKeys = [
        ForeignKey(
            entity = ScanReportEntity::class,
            parentColumns = ["id"],
            childColumns = ["report_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["report_id"])]
)
data class DeviceEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "device_pk")
    val devicePk: Long = 0,

    @ColumnInfo(name = "report_id")
    val reportId: String,

    @ColumnInfo(name = "ip_address")
    val ipAddress: String,

    @ColumnInfo(name = "mac_address")
    val macAddress: String,

    @ColumnInfo(name = "vendor")
    val vendor: String?,

    @ColumnInfo(name = "hostname")
    val hostname: String?,

    /** 카메라 의심 여부 */
    @ColumnInfo(name = "is_camera")
    val isCamera: Boolean,

    /** 개방된 포트 목록 (콤마 구분 문자열) */
    @ColumnInfo(name = "open_ports")
    val openPorts: String = ""
)
