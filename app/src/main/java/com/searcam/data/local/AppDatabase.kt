package com.searcam.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.searcam.data.local.converter.SearCamTypeConverters
import com.searcam.data.local.dao.ChecklistDao
import com.searcam.data.local.dao.DeviceDao
import com.searcam.data.local.dao.ReportDao
import com.searcam.data.local.entity.ChecklistEntity
import com.searcam.data.local.entity.DeviceEntity
import com.searcam.data.local.entity.RiskPointEntity
import com.searcam.data.local.entity.ScanReportEntity

/**
 * SearCam Room 데이터베이스
 *
 * 앱 전역에서 사용하는 단일 SQLite 데이터베이스.
 * DatabaseModule에서 싱글톤으로 제공된다.
 *
 * 버전 관리:
 * - 스키마를 변경할 때마다 version을 올리고 Migration을 추가해야 한다.
 * - 개발 중에는 fallbackToDestructiveMigration()으로 자동 재생성.
 * - 프로덕션 릴리즈 전에 Migration 전략으로 교체 필요.
 *
 * @property version 데이터베이스 스키마 버전 (변경 시 반드시 증가)
 * @property entities Room이 관리할 Entity 클래스 목록
 * @property exportSchema 스키마를 schemas/ 디렉토리에 저장 (버전 관리 추적용)
 */
@Database(
    entities = [
        ScanReportEntity::class,
        DeviceEntity::class,
        ChecklistEntity::class,
        RiskPointEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(SearCamTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {

    /** 스캔 리포트 DAO */
    abstract fun reportDao(): ReportDao

    /** 네트워크 기기 DAO */
    abstract fun deviceDao(): DeviceDao

    /** 체크리스트 DAO */
    abstract fun checklistDao(): ChecklistDao
}
