# SearCam 데이터베이스 스키마 설계서

> 버전: v1.0
> 작성일: 2026-04-03
> 기반: project-plan.md v3.1, 04-system-architecture.md

---

## 1. 데이터베이스 개요

SearCam Phase 1은 **Room (SQLite)** 기반 로컬 DB만 사용한다.
서버 DB는 존재하지 않으며, 모든 데이터는 사용자 기기에 저장된다.

```
┌──────────────────────────────────────────────────┐
│              Room Database: searcam_db            │
│              Version: 1                           │
│              Export Schema: true                  │
├──────────────────────────────────────────────────┤
│                                                   │
│  ┌─────────────┐    1:N    ┌─────────────┐       │
│  │ScanReport   │──────────▶│Device       │       │
│  │Entity       │           │Entity       │       │
│  └──────┬──────┘           └─────────────┘       │
│         │                                         │
│         │ 1:N   ┌─────────────┐                  │
│         └──────▶│RiskPoint    │                  │
│                  │Entity       │                  │
│                  └─────────────┘                  │
│                                                   │
│  ┌─────────────┐         ┌─────────────┐         │
│  │Checklist    │         │Settings     │         │
│  │Entity       │         │Entity       │         │
│  └─────────────┘         └─────────────┘         │
│                                                   │
└──────────────────────────────────────────────────┘
```

---

## 2. 엔티티 설계

### 2.1 ScanReportEntity

스캔 결과 리포트의 마스터 테이블이다.

```
┌──────────────────────────────────────────────────────────────┐
│                     scan_reports                              │
├────────────────┬──────────┬──────────────────────────────────┤
│ 컬럼           │ 타입     │ 제약조건                         │
├────────────────┼──────────┼──────────────────────────────────┤
│ id             │ INTEGER  │ PRIMARY KEY AUTOINCREMENT        │
│ scan_mode      │ TEXT     │ NOT NULL                         │
│ overall_score  │ INTEGER  │ NOT NULL, CHECK(0..100)          │
│ risk_level     │ TEXT     │ NOT NULL                         │
│ layer1_score   │ INTEGER  │ NULLABLE, CHECK(0..100)          │
│ layer2_score   │ INTEGER  │ NULLABLE, CHECK(0..100)          │
│ layer3_score   │ INTEGER  │ NULLABLE, CHECK(0..100)          │
│ layer2a_score  │ INTEGER  │ NULLABLE (Stage A: Retroreflect) │
│ layer2b_score  │ INTEGER  │ NULLABLE (Stage B: IR)           │
│ correction     │ REAL     │ NOT NULL, DEFAULT 1.0            │
│ evidences_json │ TEXT     │ NOT NULL (JSON 배열)             │
│ wifi_connected │ INTEGER  │ NOT NULL (0/1)                   │
│ total_devices  │ INTEGER  │ NOT NULL, DEFAULT 0              │
│ suspect_devices│ INTEGER  │ NOT NULL, DEFAULT 0              │
│ lens_points    │ INTEGER  │ NOT NULL, DEFAULT 0              │
│ ir_points      │ INTEGER  │ NOT NULL, DEFAULT 0              │
│ max_emf_delta  │ REAL     │ NULLABLE (uT)                   │
│ emf_sensitivity│ TEXT     │ NULLABLE                         │
│ location_name  │ TEXT     │ NULLABLE (사용자 입력)           │
│ memo           │ TEXT     │ NULLABLE                         │
│ duration_ms    │ INTEGER  │ NOT NULL                         │
│ created_at     │ INTEGER  │ NOT NULL (epoch ms)              │
├────────────────┴──────────┴──────────────────────────────────┤
│ 인덱스:                                                      │
│   idx_report_created_at ON (created_at DESC)                 │
│   idx_report_risk_level ON (risk_level)                      │
│   idx_report_scan_mode  ON (scan_mode)                       │
└──────────────────────────────────────────────────────────────┘
```

**scan_mode 값**: `QUICK`, `FULL`, `LENS`, `IR`, `EMF`
**risk_level 값**: `SAFE`, `ATTENTION`, `CAUTION`, `DANGER`, `CRITICAL`

### 2.2 DeviceEntity

스캔에서 발견된 네트워크 기기 정보를 저장한다.

```
┌──────────────────────────────────────────────────────────────┐
│                       devices                                 │
├────────────────┬──────────┬──────────────────────────────────┤
│ 컬럼           │ 타입     │ 제약조건                         │
├────────────────┼──────────┼──────────────────────────────────┤
│ id             │ INTEGER  │ PRIMARY KEY AUTOINCREMENT        │
│ report_id      │ INTEGER  │ NOT NULL, FK -> scan_reports(id) │
│ ip_address     │ TEXT     │ NOT NULL                         │
│ mac_address    │ TEXT     │ NOT NULL                         │
│ vendor         │ TEXT     │ NULLABLE                         │
│ device_type    │ TEXT     │ NOT NULL                         │
│ hostname       │ TEXT     │ NULLABLE                         │
│ risk_score     │ INTEGER  │ NOT NULL, CHECK(0..100)          │
│ open_ports_json│ TEXT     │ NOT NULL (JSON 배열)             │
│ services_json  │ TEXT     │ NOT NULL (JSON 배열)             │
│ evidence_json  │ TEXT     │ NOT NULL (JSON 배열)             │
│ is_suspicious  │ INTEGER  │ NOT NULL (0/1)                   │
│ created_at     │ INTEGER  │ NOT NULL (epoch ms)              │
├────────────────┴──────────┴──────────────────────────────────┤
│ 인덱스:                                                      │
│   idx_device_report_id ON (report_id)                        │
│   idx_device_suspicious ON (is_suspicious)                   │
│   idx_device_mac ON (mac_address)                            │
├──────────────────────────────────────────────────────────────┤
│ 외래키:                                                      │
│   report_id -> scan_reports(id) ON DELETE CASCADE            │
└──────────────────────────────────────────────────────────────┘
```

**device_type 값**: `IP_CAMERA`, `SMART_CAMERA`, `CONSUMER`, `ROUTER`, `PRINTER`, `TV`, `UNKNOWN`

### 2.3 RiskPointEntity

렌즈 의심 포인트, IR 포인트, 자기장 이상 지점을 통합 저장한다.

```
┌──────────────────────────────────────────────────────────────┐
│                      risk_points                              │
├────────────────┬──────────┬──────────────────────────────────┤
│ 컬럼           │ 타입     │ 제약조건                         │
├────────────────┼──────────┼──────────────────────────────────┤
│ id             │ INTEGER  │ PRIMARY KEY AUTOINCREMENT        │
│ report_id      │ INTEGER  │ NOT NULL, FK -> scan_reports(id) │
│ point_type     │ TEXT     │ NOT NULL                         │
│ x              │ REAL     │ NULLABLE (0.0~1.0, 화면 좌표)   │
│ y              │ REAL     │ NULLABLE (0.0~1.0, 화면 좌표)   │
│ score          │ INTEGER  │ NOT NULL, CHECK(0..100)          │
│ size_px        │ INTEGER  │ NULLABLE (렌즈 포인트 크기)      │
│ circularity    │ REAL     │ NULLABLE (렌즈 원형도)           │
│ brightness     │ REAL     │ NULLABLE (상대 밝기)             │
│ duration_ms    │ INTEGER  │ NULLABLE (지속 시간)             │
│ flash_verified │ INTEGER  │ NULLABLE (0/1, 플래시 검증 통과) │
│ emf_delta      │ REAL     │ NULLABLE (자기장 변화량 uT)      │
│ emf_level      │ TEXT     │ NULLABLE                         │
│ evidence       │ TEXT     │ NOT NULL                         │
│ created_at     │ INTEGER  │ NOT NULL (epoch ms)              │
├────────────────┴──────────┴──────────────────────────────────┤
│ 인덱스:                                                      │
│   idx_riskpoint_report_id ON (report_id)                     │
│   idx_riskpoint_type ON (point_type)                         │
│   idx_riskpoint_score ON (score DESC)                        │
├──────────────────────────────────────────────────────────────┤
│ 외래키:                                                      │
│   report_id -> scan_reports(id) ON DELETE CASCADE            │
└──────────────────────────────────────────────────────────────┘
```

**point_type 값**: `LENS_RETROREFLECTION`, `IR_LED`, `EMF_ANOMALY`

### 2.4 ChecklistEntity

육안 점검 체크리스트 완료 기록을 저장한다.

```
┌──────────────────────────────────────────────────────────────┐
│                      checklists                               │
├────────────────┬──────────┬──────────────────────────────────┤
│ 컬럼           │ 타입     │ 제약조건                         │
├────────────────┼──────────┼──────────────────────────────────┤
│ id             │ INTEGER  │ PRIMARY KEY AUTOINCREMENT        │
│ report_id      │ INTEGER  │ NULLABLE, FK -> scan_reports(id) │
│ checklist_type │ TEXT     │ NOT NULL                         │
│ items_json     │ TEXT     │ NOT NULL (JSON 배열)             │
│ checked_count  │ INTEGER  │ NOT NULL                         │
│ total_count    │ INTEGER  │ NOT NULL                         │
│ suspicious_items_json │ TEXT │ NOT NULL (JSON 배열)          │
│ completed      │ INTEGER  │ NOT NULL (0/1)                   │
│ created_at     │ INTEGER  │ NOT NULL (epoch ms)              │
├────────────────┴──────────┴──────────────────────────────────┤
│ 인덱스:                                                      │
│   idx_checklist_report_id ON (report_id)                     │
│   idx_checklist_type ON (checklist_type)                     │
├──────────────────────────────────────────────────────────────┤
│ 외래키:                                                      │
│   report_id -> scan_reports(id) ON DELETE SET NULL           │
└──────────────────────────────────────────────────────────────┘
```

**checklist_type 값**: `ACCOMMODATION` (숙소, 15항목), `RESTROOM` (화장실, 10항목), `FITTING_ROOM` (탈의실)

**items_json 구조**:
```json
[
  {"index": 0, "label": "연기 감지기 렌즈 확인", "checked": true, "suspicious": false},
  {"index": 1, "label": "시계 뒷면 확인", "checked": true, "suspicious": true},
  {"index": 2, "label": "TV 베젤 확인", "checked": false, "suspicious": false}
]
```

### 2.5 SettingsEntity

앱 설정을 Key-Value 형태로 저장한다.

```
┌──────────────────────────────────────────────────────────────┐
│                       settings                                │
├────────────────┬──────────┬──────────────────────────────────┤
│ 컬럼           │ 타입     │ 제약조건                         │
├────────────────┼──────────┼──────────────────────────────────┤
│ key            │ TEXT     │ PRIMARY KEY                      │
│ value          │ TEXT     │ NOT NULL                         │
│ updated_at     │ INTEGER  │ NOT NULL (epoch ms)              │
├────────────────┴──────────┴──────────────────────────────────┤
│ 인덱스: 없음 (PK가 인덱스 역할)                             │
└──────────────────────────────────────────────────────────────┘
```

**설정 키 목록**:

| key | 기본값 | 설명 |
|-----|--------|------|
| `emf_sensitivity` | `NORMAL` | 자기장 감도 (SENSITIVE/NORMAL/STABLE) |
| `sound_enabled` | `true` | 경고음 활성화 |
| `vibration_enabled` | `true` | 진동 활성화 |
| `is_premium` | `false` | 프리미엄 사용자 여부 |
| `onboarding_completed` | `false` | 온보딩 완료 여부 |
| `last_oui_update` | `0` | OUI DB 마지막 업데이트 (epoch ms) |
| `theme_mode` | `SYSTEM` | 테마 (LIGHT/DARK/SYSTEM) |
| `language` | `ko` | 언어 (ko/en/ja) |

---

## 3. DAO 인터페이스

### 3.1 ReportDao

```kotlin
@Dao
interface ReportDao {

    @Insert
    suspend fun insert(report: ScanReportEntity): Long

    @Query("SELECT * FROM scan_reports WHERE id = :id")
    suspend fun getById(id: Long): ScanReportEntity?

    @Query("SELECT * FROM scan_reports ORDER BY created_at DESC")
    fun getAll(): Flow<List<ScanReportEntity>>

    @Query("SELECT * FROM scan_reports ORDER BY created_at DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<ScanReportEntity>>

    @Query("SELECT * FROM scan_reports WHERE risk_level = :level ORDER BY created_at DESC")
    fun getByRiskLevel(level: String): Flow<List<ScanReportEntity>>

    @Query("SELECT * FROM scan_reports WHERE scan_mode = :mode ORDER BY created_at DESC")
    fun getByScanMode(mode: String): Flow<List<ScanReportEntity>>

    @Query("SELECT COUNT(*) FROM scan_reports")
    suspend fun getCount(): Int

    @Delete
    suspend fun delete(report: ScanReportEntity)

    @Query("DELETE FROM scan_reports WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("""
        DELETE FROM scan_reports 
        WHERE id NOT IN (
            SELECT id FROM scan_reports 
            ORDER BY created_at DESC 
            LIMIT :keepCount
        )
    """)
    suspend fun deleteOldest(keepCount: Int)

    @Query("SELECT * FROM scan_reports WHERE created_at BETWEEN :startMs AND :endMs ORDER BY created_at DESC")
    fun getByDateRange(startMs: Long, endMs: Long): Flow<List<ScanReportEntity>>
}
```

### 3.2 DeviceDao

```kotlin
@Dao
interface DeviceDao {

    @Insert
    suspend fun insertAll(devices: List<DeviceEntity>)

    @Query("SELECT * FROM devices WHERE report_id = :reportId")
    suspend fun getByReportId(reportId: Long): List<DeviceEntity>

    @Query("SELECT * FROM devices WHERE report_id = :reportId AND is_suspicious = 1")
    suspend fun getSuspiciousByReportId(reportId: Long): List<DeviceEntity>

    @Query("SELECT * FROM devices WHERE mac_address = :mac ORDER BY created_at DESC")
    suspend fun getByMac(mac: String): List<DeviceEntity>

    @Query("DELETE FROM devices WHERE report_id = :reportId")
    suspend fun deleteByReportId(reportId: Long)
}
```

### 3.3 RiskPointDao

```kotlin
@Dao
interface RiskPointDao {

    @Insert
    suspend fun insertAll(points: List<RiskPointEntity>)

    @Query("SELECT * FROM risk_points WHERE report_id = :reportId")
    suspend fun getByReportId(reportId: Long): List<RiskPointEntity>

    @Query("SELECT * FROM risk_points WHERE report_id = :reportId AND point_type = :type")
    suspend fun getByReportIdAndType(reportId: Long, type: String): List<RiskPointEntity>

    @Query("SELECT * FROM risk_points WHERE report_id = :reportId ORDER BY score DESC")
    suspend fun getByReportIdSortedByScore(reportId: Long): List<RiskPointEntity>

    @Query("DELETE FROM risk_points WHERE report_id = :reportId")
    suspend fun deleteByReportId(reportId: Long)
}
```

### 3.4 ChecklistDao

```kotlin
@Dao
interface ChecklistDao {

    @Insert
    suspend fun insert(checklist: ChecklistEntity): Long

    @Update
    suspend fun update(checklist: ChecklistEntity)

    @Query("SELECT * FROM checklists WHERE id = :id")
    suspend fun getById(id: Long): ChecklistEntity?

    @Query("SELECT * FROM checklists WHERE report_id = :reportId")
    suspend fun getByReportId(reportId: Long): ChecklistEntity?

    @Query("SELECT * FROM checklists ORDER BY created_at DESC")
    fun getAll(): Flow<List<ChecklistEntity>>

    @Delete
    suspend fun delete(checklist: ChecklistEntity)
}
```

### 3.5 SettingsDao

```kotlin
@Dao
interface SettingsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(setting: SettingsEntity)

    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT * FROM settings")
    fun getAll(): Flow<List<SettingsEntity>>

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun delete(key: String)
}
```

---

## 4. 관계(Relation) 정의

### 4.1 ER 다이어그램

```
scan_reports (1) ──────── (N) devices
     │
     │ (1) ──────── (N) risk_points
     │
     │ (1) ──────── (0..1) checklists
     │
settings (독립, 관계 없음)
```

### 4.2 Room Relation 정의

```kotlin
data class ScanReportWithDetails(
    @Embedded
    val report: ScanReportEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "report_id"
    )
    val devices: List<DeviceEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "report_id"
    )
    val riskPoints: List<RiskPointEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "report_id"
    )
    val checklist: ChecklistEntity?
)

// DAO에서 사용
@Transaction
@Query("SELECT * FROM scan_reports WHERE id = :id")
suspend fun getReportWithDetails(id: Long): ScanReportWithDetails?

@Transaction
@Query("SELECT * FROM scan_reports ORDER BY created_at DESC")
fun getAllWithDetails(): Flow<List<ScanReportWithDetails>>
```

---

## 5. 마이그레이션 전략

### 5.1 원칙

- Room의 `autoMigrations`를 우선 사용한다.
- 스키마 변경이 복잡하면 `Migration` 클래스를 수동 작성한다.
- 마이그레이션 실패 시 `fallbackToDestructiveMigration()`은 **사용하지 않는다**.
- 모든 마이그레이션은 반드시 테스트한다.

### 5.2 버전 계획

| DB 버전 | 변경 내용 | 시기 |
|---------|----------|------|
| 1 | 초기 스키마 (5개 테이블) | Phase 1 MVP |
| 2 | LiDAR 포인트 타입 추가 (risk_points 확장) | Phase 3 |
| 3 | 커뮤니티 맵 관련 테이블 추가 | Phase 3 |
| 4 | ML 학습 데이터 테이블 추가 | Phase 3 |

### 5.3 마이그레이션 예시 (v1 -> v2)

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // risk_points 테이블에 lidar 관련 컬럼 추가
        db.execSQL(
            "ALTER TABLE risk_points ADD COLUMN lidar_distance REAL"
        )
        db.execSQL(
            "ALTER TABLE risk_points ADD COLUMN lidar_confidence REAL"
        )
    }
}
```

---

## 6. 데이터 보존 정책

### 6.1 무료 vs 프리미엄

| 항목 | 무료 | 프리미엄 (월 2,900원) |
|------|------|---------------------|
| 리포트 저장 수 | **최근 10건** | **무제한** |
| PDF 내보내기 | 불가 | 가능 |
| 기기 상세 정보 | 기본 | 전체 |
| 리포트 검색 | 불가 | 날짜/등급 검색 |

### 6.2 정리 로직

```
무료 사용자 리포트 저장 시:
  1. reportDao.getCount() 확인
  2. count >= 10이면:
     reportDao.deleteOldest(keepCount = 10)
     (CASCADE로 devices, risk_points도 자동 삭제)
  3. 새 리포트 저장

프리미엄 사용자:
  제한 없이 저장
  사용자 수동 삭제만 가능
```

### 6.3 앱 삭제 시

Room DB는 앱 내부 저장소(`/data/data/com.searcam/databases/`)에 위치한다.
앱 삭제 시 자동으로 함께 삭제된다. 별도 백업 기능은 Phase 2에서 고려한다.

---

## 7. OUI 데이터베이스 스키마

OUI(Organizationally Unique Identifier) 데이터는 Room이 아닌 **JSON 파일**로 관리한다.
앱 assets에 내장하며, OTA 업데이트로 갱신 가능하다.

### 7.1 JSON 구조

```json
{
  "version": "2026-04-01",
  "total_entries": 1300,
  "camera_vendors": [
    {
      "oui": "28:57:BE",
      "vendor": "Hikvision Digital Technology",
      "type": "ip_camera",
      "risk": 0.95,
      "aliases": ["HiKVision", "HIKVISION"],
      "common_models": ["DS-2CD", "DS-7600"],
      "known_ports": [554, 80, 8000]
    },
    {
      "oui": "3C:EF:8C",
      "vendor": "Dahua Technology",
      "type": "ip_camera",
      "risk": 0.95,
      "aliases": ["DAHUA"],
      "common_models": ["IPC-HDW", "NVR"],
      "known_ports": [554, 80, 37777]
    },
    {
      "oui": "2C:AA:8E",
      "vendor": "Wyze Labs",
      "type": "smart_camera",
      "risk": 0.80,
      "aliases": ["Wyze"],
      "common_models": ["Cam v3", "Cam Pan"],
      "known_ports": [554, 80, 8080]
    }
  ],
  "safe_vendors": [
    {
      "oui": "AA:BB:CC",
      "vendor": "Apple",
      "type": "consumer",
      "risk": 0.05,
      "device_types": ["iPhone", "iPad", "MacBook", "Apple TV"]
    },
    {
      "oui": "DD:EE:FF",
      "vendor": "Samsung Electronics",
      "type": "consumer",
      "risk": 0.05,
      "device_types": ["Galaxy", "Smart TV"]
    }
  ],
  "network_infra": [
    {
      "oui": "11:22:33",
      "vendor": "TP-Link",
      "type": "router",
      "risk": 0.10,
      "device_types": ["Router", "Switch", "AP"]
    }
  ]
}
```

### 7.2 OUI 카테고리별 항목 수

| 카테고리 | 항목 수 | risk 범위 | 설명 |
|---------|---------|----------|------|
| camera_vendors | 300+ | 0.70 ~ 0.95 | IP 카메라, 스마트 카메라 제조사 |
| safe_vendors | 500+ | 0.01 ~ 0.10 | 소비자 기기 제조사 (화이트리스트) |
| network_infra | 200+ | 0.05 ~ 0.15 | 라우터, 스위치, AP 제조사 |
| iot_vendors | 200+ | 0.15 ~ 0.40 | IoT 기기 (스마트홈 등) |
| unknown | (매칭 실패 시) | 0.30 | OUI 미등록 기기 |

### 7.3 OUI 업데이트

```
Phase 1: 앱 업데이트 시 assets/oui_database.json 교체
Phase 2: Firebase Remote Config로 delta 업데이트
         - 마지막 업데이트 이후 변경분만 다운로드
         - 기기 로컬에 병합 저장
```

---

## 8. 저장 용량 추정

### 8.1 엔티티별 레코드 크기

| 엔티티 | 평균 레코드 크기 | 근거 |
|--------|----------------|------|
| ScanReportEntity | ~500 bytes | JSON 필드 포함 |
| DeviceEntity | ~300 bytes | 기기당 |
| RiskPointEntity | ~200 bytes | 포인트당 |
| ChecklistEntity | ~1 KB | items_json 포함 |
| SettingsEntity | ~50 bytes | key-value |

### 8.2 스캔 1회당 용량

| 시나리오 | 리포트 | 기기 | 포인트 | 합계 |
|---------|--------|------|--------|------|
| Quick Scan (기기 7대) | 500B | 2.1KB (7x300B) | 0 | ~2.6KB |
| Full Scan (기기 7대, 포인트 3개) | 500B | 2.1KB | 600B (3x200B) | ~3.2KB |
| Full Scan (의심 다수) | 500B | 3.0KB (10대) | 2.0KB (10포인트) | ~5.5KB |

### 8.3 누적 용량 추정

| 사용 패턴 | 저장 건수 | 예상 용량 |
|----------|----------|----------|
| 무료 사용자 (최대 10건) | 10건 | ~55KB |
| 프리미엄 (월 30건, 1년) | 360건 | ~2MB |
| 프리미엄 (주 1회, 3년) | ~156건 | ~860KB |

### 8.4 OUI 데이터베이스 용량

| 항목 | 용량 |
|------|------|
| oui_database.json (비압축) | ~500KB |
| oui_database.json (gzip) | ~80KB |
| assets 내장 방식 | APK 크기에 ~80KB 추가 |

### 8.5 전체 앱 저장 용량 추정

| 구성 요소 | 용량 |
|----------|------|
| APK (앱 코드 + 리소스) | ~15MB |
| OUI DB (assets) | ~500KB |
| Room DB (무료, 10건) | ~55KB |
| Room DB (프리미엄, 1년) | ~2MB |
| 캐시 (카메라 프레임 등) | 최대 50MB (자동 정리) |
| **총 용량 (일반)** | **~20MB** |

---

## 9. TypeConverter 정의

Room은 기본 타입(Int, String, Long 등)만 저장 가능하므로, 복합 타입은 TypeConverter가 필요하다.

```kotlin
class SearCamTypeConverters {

    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>): String =
        gson.toJson(value)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        gson.fromJson(value, object : TypeToken<List<String>>() {}.type)

    @TypeConverter
    fun fromIntList(value: List<Int>): String =
        gson.toJson(value)

    @TypeConverter
    fun toIntList(value: String): List<Int> =
        gson.fromJson(value, object : TypeToken<List<Int>>() {}.type)

    @TypeConverter
    fun fromChecklistItems(value: List<ChecklistItem>): String =
        gson.toJson(value)

    @TypeConverter
    fun toChecklistItems(value: String): List<ChecklistItem> =
        gson.fromJson(value, object : TypeToken<List<ChecklistItem>>() {}.type)

    @TypeConverter
    fun fromEvidenceList(value: List<Evidence>): String =
        gson.toJson(value)

    @TypeConverter
    fun toEvidenceList(value: String): List<Evidence> =
        gson.fromJson(value, object : TypeToken<List<Evidence>>() {}.type)
}
```

---

*본 문서는 project-plan.md v3.1 기반으로 작성되었으며, Phase 1 (Android MVP) DB 스키마를 정의합니다.*
*Phase 2~3에서 커뮤니티 맵, ML 학습 데이터 등 추가 테이블이 필요합니다.*
