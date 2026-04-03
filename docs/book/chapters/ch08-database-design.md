# Ch08: 데이터베이스 설계 — Room + SQLCipher로 스캔 이력 보호

> **이 장에서 배울 것**: 몰래카메라 탐지 결과라는 민감한 데이터를 어떻게 안전하게 저장하는지 배웁니다. Room 엔티티 설계, TypeConverter 구현, SQLCipher와 Android Keystore 연동, DAO 패턴, 마이그레이션 전략까지 실제 구현 코드와 함께 설명합니다.

---

## 도입

호텔 금고를 떠올려보세요. 금고 안에는 여권, 현금, 귀중품이 들어있습니다. 금고가 없다면 누군가 방에 들어와 물건을 가져갈 수 있습니다. 금고가 있더라도 비밀번호가 "1234"라면 의미가 없습니다.

SearCam의 스캔 이력은 그 금고 안의 내용물과 같습니다. 사용자가 어느 숙소에 묵었는지, 어떤 기기를 발견했는지, 몇 시에 탐지를 실행했는지 — 이 데이터는 충분히 민감합니다. 단순한 SQLite 파일로 저장하면, adb backup 명령 한 줄로 누구든 내용을 열람할 수 있습니다.

SearCam의 답은 **Room + SQLCipher + Android Keystore** 조합입니다. Room이 SQL 보일러플레이트를 제거하고, SQLCipher가 데이터베이스 파일 자체를 AES-256으로 암호화하며, Android Keystore가 암호화 키를 하드웨어 수준에서 보호합니다. 이 세 층이 함께 작동할 때 비로소 "안전한 금고"가 완성됩니다.

---

## 8.1 데이터베이스 전체 구조

SearCam Phase 1은 서버 없이 **모든 데이터를 기기 로컬에만 저장**합니다. 프라이버시 최우선 설계입니다. 데이터가 서버로 나가지 않으니 유출 경로가 그만큼 줄어듭니다.

```
┌──────────────────────────────────────────────────┐
│            Room Database: searcam.db              │
│            Version: 1 / Encrypted: AES-256        │
├──────────────────────────────────────────────────┤
│                                                   │
│  ┌─────────────┐    1:N    ┌─────────────┐        │
│  │ScanReport   │──────────▶│Device       │        │
│  │Entity       │           │Entity       │        │
│  └──────┬──────┘           └─────────────┘        │
│         │                                          │
│         │ 1:N   ┌─────────────┐                   │
│         └──────▶│RiskPoint    │                   │
│                  │Entity       │                   │
│                  └─────────────┘                   │
│                                                   │
│  ┌─────────────┐    (독립)                         │
│  │Checklist    │                                   │
│  │Entity       │                                   │
│  └─────────────┘                                   │
│                                                   │
└──────────────────────────────────────────────────┘
```

테이블 관계를 한 문장으로 요약하면: **스캔 리포트 하나에 여러 기기와 여러 위험 포인트가 매달린다.** 체크리스트는 독립 테이블로 선택적으로 리포트에 연결됩니다.

### 테이블 목록

| 테이블 | 엔티티 클래스 | 역할 |
|--------|------------|------|
| `scan_reports` | `ScanReportEntity` | 스캔 세션 마스터 레코드 |
| `devices` | `DeviceEntity` | 발견된 네트워크 기기 |
| `risk_points` | `RiskPointEntity` | 렌즈/IR/EMF 의심 포인트 |
| `checklists` | `ChecklistEntity` | 육안 점검 체크리스트 |

---

## 8.2 엔티티 설계

### ScanReportEntity — 스캔 세션의 마스터 레코드

스캔 한 번의 결과 전체를 담는 테이블입니다. 실제 구현 코드를 보면 설계 의도가 명확하게 드러납니다.

```kotlin
// app/src/main/java/com/searcam/data/local/entity/ScanReportEntity.kt

@Entity(tableName = "scan_reports")
data class ScanReportEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,           // UUID — 자동 증가 정수 대신 UUID를 써서 충돌 방지

    @ColumnInfo(name = "mode")
    val mode: String,         // "QUICK" | "FULL" | "LENS" | "IR" | "EMF"

    @ColumnInfo(name = "started_at")
    val startedAt: Long,      // Unix epoch millis

    @ColumnInfo(name = "completed_at")
    val completedAt: Long,

    @ColumnInfo(name = "risk_score")
    val riskScore: Int,       // 0 ~ 100

    @ColumnInfo(name = "risk_level")
    val riskLevel: String,    // "SAFE" | "ATTENTION" | "CAUTION" | "DANGER" | "CRITICAL"

    @ColumnInfo(name = "devices_json")
    val devicesJson: String = "[]",    // 기기 목록 JSON

    @ColumnInfo(name = "findings_json")
    val findingsJson: String = "[]",   // 발견 사항 JSON

    @ColumnInfo(name = "location_note")
    val locationNote: String = "",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
```

여기서 주목할 설계 결정이 두 가지 있습니다.

**첫째, PK로 UUID 사용.** `autoGenerate = true`의 자동 증가 정수 대신 UUID 문자열을 PK로 씁니다. 이유는 간단합니다 — 오프라인 기기에서 생성된 레코드를 나중에 서버나 다른 기기와 동기화할 때, 정수 PK는 반드시 충돌이 납니다. UUID는 전역적으로 유일하므로 Phase 2에서 클라우드 동기화를 추가할 때 PK를 바꿀 필요가 없습니다.

**둘째, 복잡한 중첩 객체는 JSON으로 직렬화.** `devicesJson`, `findingsJson`처럼 리스트나 복합 객체는 JSON 문자열로 저장합니다. Room이 관계형 테이블로 관리하지 않는 이유는 조회 빈도와 복잡도의 균형 때문입니다. 스캔 결과 화면에서는 리포트 전체를 한 번에 읽으므로, JOIN보다 단일 레코드 조회가 더 효율적입니다.

설계 문서의 스키마와 실제 구현 간에는 일부 차이가 있습니다. 설계 문서는 `layer1_score`, `layer2_score` 등 레이어별 점수를 개별 컬럼으로 두지만, 실제 구현에서는 `findings_json`에 통합했습니다. 이는 MVP 속도를 우선한 실용적 선택입니다.

### DeviceEntity — 발견된 네트워크 기기

```kotlin
// app/src/main/java/com/searcam/data/local/entity/DeviceEntity.kt

@Entity(
    tableName = "devices",
    foreignKeys = [
        ForeignKey(
            entity = ScanReportEntity::class,
            parentColumns = ["id"],
            childColumns = ["report_id"],
            onDelete = ForeignKey.CASCADE   // 리포트 삭제 시 기기도 자동 삭제
        )
    ],
    indices = [Index(value = ["report_id"])]
)
data class DeviceEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "device_pk")
    val devicePk: Long = 0,

    @ColumnInfo(name = "report_id")
    val reportId: String,       // FK → scan_reports.id

    @ColumnInfo(name = "ip_address")
    val ipAddress: String,

    @ColumnInfo(name = "mac_address")
    val macAddress: String,

    @ColumnInfo(name = "vendor")
    val vendor: String?,        // OUI 매칭 결과 (없으면 null)

    @ColumnInfo(name = "hostname")
    val hostname: String?,      // mDNS 발견 호스트명

    @ColumnInfo(name = "is_camera")
    val isCamera: Boolean,      // 카메라 의심 여부

    @ColumnInfo(name = "open_ports")
    val openPorts: String = ""  // "554,80,8000" — 콤마 구분 문자열
)
```

`onDelete = ForeignKey.CASCADE`가 핵심입니다. 사용자가 스캔 리포트를 삭제하면, 그 리포트에 연결된 모든 기기 레코드도 자동으로 삭제됩니다. 앱 코드에서 별도로 "관련 기기도 삭제" 로직을 작성할 필요가 없습니다. 데이터베이스가 참조 무결성을 보장합니다.

`indices = [Index(value = ["report_id"])]` 선언도 빠뜨릴 수 없습니다. 외래키 컬럼에 인덱스가 없으면, "이 리포트의 모든 기기를 가져와"라는 쿼리가 전체 테이블 스캔(Full Table Scan)을 수행합니다. 기기가 수백 개 쌓이면 체감될 만큼 느려집니다.

### RiskPointEntity — 위험 포인트의 통합 저장

렌즈 의심 포인트(Retroreflection), IR LED 포인트, 자기장 이상 지점 — 세 종류의 데이터를 하나의 테이블에 담는 설계입니다.

```kotlin
// app/src/main/java/com/searcam/data/local/entity/RiskPointEntity.kt

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

    @ColumnInfo(name = "report_id")
    val reportId: String,

    // "LENS_RETROREFLECTION" | "IR_LED" | "EMF_ANOMALY"
    @ColumnInfo(name = "point_type")
    val pointType: String,

    // 정규화 좌표 (0.0~1.0) — EMF는 null
    @ColumnInfo(name = "x")
    val x: Float? = null,

    @ColumnInfo(name = "y")
    val y: Float? = null,

    @ColumnInfo(name = "score")
    val score: Int,                 // 0~100

    // 렌즈 포인트 전용 필드들
    @ColumnInfo(name = "size_px")
    val sizePx: Int? = null,

    @ColumnInfo(name = "circularity")
    val circularity: Float? = null, // 원형도, 렌즈: > 0.8

    @ColumnInfo(name = "brightness")
    val brightness: Float? = null,

    // IR 포인트 전용
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long? = null,   // 지속 시간, 의심 기준: >3,000ms

    // 렌즈 검증 결과
    @ColumnInfo(name = "flash_verified")
    val flashVerified: Boolean? = null, // 플래시 OFF 시 소실 확인 여부

    // EMF 포인트 전용
    @ColumnInfo(name = "emf_delta")
    val emfDelta: Float? = null,    // 자기장 변화량 (μT)

    @ColumnInfo(name = "emf_level")
    val emfLevel: String? = null,   // "NORMAL" | "INTEREST" | "CAUTION" | "SUSPECT"

    @ColumnInfo(name = "evidence")
    val evidence: String,           // 감지 근거 텍스트 (한국어)

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)
```

이 설계를 **단일 테이블 상속(Single Table Inheritance)** 패턴이라고 합니다. 세 종류의 포인트를 각각 별도 테이블(`lens_points`, `ir_points`, `emf_points`)로 나눌 수도 있었습니다. 그렇게 하면 각 테이블이 꼭 필요한 컬럼만 갖지만, JOIN이 늘어나고 쿼리가 복잡해집니다.

SearCam이 단일 테이블을 선택한 이유: "이 리포트의 모든 위험 포인트를 점수 내림차순으로 가져와"라는 쿼리가 한 번의 SELECT로 끝납니다. 대신 `pointType`에 따라 관련 없는 컬럼은 null이 됩니다. 이 트레이드오프는 MVP 단계에서 합리적입니다.

인덱스가 세 개나 선언된 점도 눈에 띕니다.
- `report_id` 인덱스: 특정 리포트의 포인트 조회 속도
- `point_type` 인덱스: 렌즈 포인트만, IR 포인트만 필터링
- `score DESC` 인덱스: 가장 위험한 포인트부터 정렬

### ChecklistEntity — 육안 점검 기록

```kotlin
// app/src/main/java/com/searcam/data/local/entity/ChecklistEntity.kt

@Entity(tableName = "checklists")
data class ChecklistEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    // "hotel" | "bathroom" | "fitting_room"
    @ColumnInfo(name = "template_id")
    val templateId: String,

    @ColumnInfo(name = "performed_at")
    val performedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "location_note")
    val locationNote: String = "",

    // {"연기 감지기 렌즈 확인": true, "시계 뒷면 확인": false, ...}
    @ColumnInfo(name = "items_json")
    val itemsJson: String = "{}",

    @ColumnInfo(name = "total_items")
    val totalItems: Int = 0,

    @ColumnInfo(name = "completed_items")
    val completedItems: Int = 0
)
```

체크리스트는 스캔 리포트와 외래키로 연결하지 않습니다. 독립적으로 존재할 수 있습니다 — 앱 스캔 없이 육안 점검만 수행할 수 있기 때문입니다. 대신 리포트 화면에서 연관 체크리스트를 보여줄 때는 `performed_at` 시간 기반으로 매칭합니다.

---

## 8.3 TypeConverter — Room이 모르는 타입을 가르치기

Room은 기본 타입(Int, String, Long, Boolean, Float)만 SQLite에 저장할 수 있습니다. `List<String>`, `RiskLevel` 같은 커스텀 타입은 저장 방법을 직접 알려줘야 합니다. 이 역할을 `TypeConverter`가 합니다.

```kotlin
// app/src/main/java/com/searcam/data/local/converter/TypeConverters.kt

class SearCamTypeConverters {

    // List<String> ↔ JSON 배열 문자열
    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        if (value.isNullOrEmpty()) return "[]"
        return value.joinToString(
            separator = ",",
            prefix = "[",
            postfix = "]",
            transform = { item -> "\"${item.replace("\"", "\\\"")}\"" },
        )
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrBlank() || value.trim() == "[]") return emptyList()
        return try {
            val trimmed = value.trim().removePrefix("[").removeSuffix("]")
            if (trimmed.isBlank()) return emptyList()
            parseStringArray(trimmed)
        } catch (e: Exception) {
            Timber.e(e, "TypeConverter: List<String> 파싱 실패 — value=$value")
            emptyList()
        }
    }

    // RiskLevel enum ↔ String
    @TypeConverter
    fun fromRiskLevel(value: RiskLevel?): String {
        return value?.name ?: RiskLevel.SAFE.name
    }

    @TypeConverter
    fun toRiskLevel(value: String?): RiskLevel {
        if (value.isNullOrBlank()) return RiskLevel.SAFE
        return try {
            RiskLevel.valueOf(value)
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "TypeConverter: 알 수 없는 RiskLevel — value=$value, 기본값 SAFE 반환")
            RiskLevel.SAFE
        }
    }

    // ScanMode enum ↔ String
    @TypeConverter
    fun fromScanMode(value: ScanMode?): String = value?.name ?: ScanMode.QUICK.name

    @TypeConverter
    fun toScanMode(value: String?): ScanMode {
        if (value.isNullOrBlank()) return ScanMode.QUICK
        return try {
            ScanMode.valueOf(value)
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "TypeConverter: 알 수 없는 ScanMode — value=$value, 기본값 QUICK 반환")
            ScanMode.QUICK
        }
    }
}
```

SearCam TypeConverter의 특징이 두 가지 있습니다.

**Gson 없는 직렬화.** 많은 프로젝트가 Gson 라이브러리를 TypeConverter에 씁니다. `gson.toJson(list)`, `gson.fromJson(str, type)` 한 줄이면 끝납니다. SearCam은 Gson 의존성을 피하기 위해 직접 파싱 로직을 구현했습니다. APK 크기를 줄이고 의존성 체인을 단순하게 유지하는 선택입니다.

**방어적 파싱.** `toRiskLevel()`에서 `try-catch`로 감싸고, 알 수 없는 값이 오면 `SAFE`를 기본값으로 반환합니다. DB에 "DANGER_EXTREME" 같은 예상 못한 값이 들어있더라도 앱이 크래시하지 않습니다. 앱 업데이트로 enum 값이 바뀐 경우에도 안전하게 동작합니다.

### TypeConverter 등록

TypeConverter는 `@Database` 어노테이션에 `@TypeConverters`를 추가해야 활성화됩니다.

```kotlin
// app/src/main/java/com/searcam/data/local/AppDatabase.kt

@Database(
    entities = [
        ScanReportEntity::class,
        DeviceEntity::class,
        ChecklistEntity::class,
        RiskPointEntity::class
    ],
    version = 1,
    exportSchema = true   // schemas/ 디렉토리에 스키마 JSON 저장
)
@TypeConverters(SearCamTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun reportDao(): ReportDao
    abstract fun deviceDao(): DeviceDao
    abstract fun checklistDao(): ChecklistDao
}
```

`exportSchema = true`는 빠뜨리기 쉬운 설정이지만 중요합니다. `true`로 설정하면 Room이 각 버전의 스키마를 `schemas/` 디렉토리에 JSON으로 기록합니다. 마이그레이션 테스트 작성 시 이 파일이 기준 스키마로 사용됩니다. 없으면 Room 자동 마이그레이션 기능을 사용할 수 없습니다.

---

## 8.4 SQLCipher + Android Keystore — 데이터 암호화

일반 Room 데이터베이스는 SQLite 파일을 평문으로 저장합니다. Android 기기를 루팅하거나 `adb backup`으로 앱 데이터를 추출하면 DB 파일을 읽을 수 있습니다. 스캔 이력처럼 민감한 데이터에는 적합하지 않습니다.

SQLCipher는 SQLite를 AES-256으로 암호화하는 오픈소스 라이브러리입니다. 패스프레이즈(비밀번호)를 DB에 적용하면, DB 파일을 꺼내도 패스프레이즈 없이는 내용을 볼 수 없습니다.

그런데 패스프레이즈를 어디에 저장해야 할까요? 앱 코드에 하드코딩하면 디컴파일로 노출됩니다. SharedPreferences에 저장해도 루팅 기기에서 읽힐 수 있습니다.

답은 **Android Keystore**입니다. 하드웨어 보안 모듈(HSM)에 암호화 키를 저장하고, 키의 원본 바이트는 앱 프로세스 밖으로 나오지 않습니다. Android 6.0 이상에서는 키가 TEE(Trusted Execution Environment)에 보호됩니다.

```kotlin
// app/src/main/java/com/searcam/di/DatabaseModule.kt

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        val passphrase = getDatabasePassphrase()
        val factory = SupportFactory(passphrase)    // SQLCipher 팩토리
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "searcam.db"
        )
            .openHelperFactory(factory)             // 일반 SQLite 대신 SQLCipher 사용
            .fallbackToDestructiveMigration()       // 개발 중 편의용 (프로덕션에서는 제거)
            .build()
    }

    /**
     * Android Keystore에서 DB 암호화 키를 가져온다.
     * 키가 없으면 새로 생성하여 Keystore에 안전하게 저장한다.
     */
    private fun getDatabasePassphrase(): ByteArray {
        val keyAlias = "searcam_db_key"
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        val secretKey: SecretKey = if (keyStore.containsAlias(keyAlias)) {
            // 기존 키 가져오기
            (keyStore.getEntry(keyAlias, null) as KeyStore.SecretKeyEntry).secretKey
        } else {
            // 키 신규 생성
            Timber.d("DB 암호화 키 신규 생성")
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
            )
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)        // AES-256
                    .build()
            )
            keyGenerator.generateKey()
        }
        return secretKey.encoded
    }

    @Provides
    fun provideReportDao(database: AppDatabase): ReportDao = database.reportDao()

    @Provides
    fun provideDeviceDao(database: AppDatabase): DeviceDao = database.deviceDao()

    @Provides
    fun provideChecklistDao(database: AppDatabase): ChecklistDao = database.checklistDao()
}
```

이 코드의 보안 흐름을 단계별로 설명합니다.

```
[앱 최초 설치]
  1. "searcam_db_key" alias로 Android Keystore 조회
  2. 키 없음 → KeyGenerator로 AES-256 키 생성
  3. Keystore에 안전하게 저장 (TEE 보호)
  4. secretKey.encoded → 패스프레이즈로 사용
  5. SQLCipher SupportFactory에 패스프레이즈 전달
  6. 암호화된 searcam.db 파일 생성

[이후 앱 실행]
  1. Keystore에서 기존 키 조회 (alias: "searcam_db_key")
  2. 동일한 패스프레이즈 추출
  3. DB 복호화하여 정상 접근
```

### 암호화 적용 전후 비교

| 항목 | 일반 Room | Room + SQLCipher |
|------|-----------|-----------------|
| DB 파일 형식 | 평문 SQLite | AES-256 암호화 |
| adb backup 탈취 | 내용 노출 | 암호문만 보임 |
| 루팅 기기 접근 | 내용 노출 | 키 없이 불가 |
| 성능 오버헤드 | 없음 | 약 5~15% 쓰기 증가 |
| 추가 의존성 | 없음 | `net.zetetic:android-database-sqlcipher` |

---

## 8.5 DAO 설계 — 필요한 것만, 정확하게

DAO(Data Access Object)는 SQL을 숨기고 코틀린 함수를 노출하는 인터페이스입니다. 잘 설계된 DAO는 "어떤 SQL을 쓰는지"가 아니라 "무엇을 원하는지"를 표현합니다.

### ReportDao

```kotlin
// app/src/main/java/com/searcam/data/local/dao/ReportDao.kt

@Dao
interface ReportDao {

    // 저장 (중복 시 덮어씀)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: ScanReportEntity)

    // 모든 리포트 실시간 구독 — Flow로 반환
    @Query("SELECT * FROM scan_reports ORDER BY started_at DESC")
    fun observeAll(): Flow<List<ScanReportEntity>>

    // ID로 단건 조회 — suspend fun (일회성)
    @Query("SELECT * FROM scan_reports WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ScanReportEntity?

    // 단건 삭제
    @Delete
    suspend fun delete(report: ScanReportEntity)

    // 전체 삭제
    @Query("DELETE FROM scan_reports")
    suspend fun deleteAll()

    // 개수 조회
    @Query("SELECT COUNT(*) FROM scan_reports")
    suspend fun count(): Int
}
```

DAO 메서드 반환 타입 선택 기준이 있습니다.

| 상황 | 반환 타입 | 이유 |
|------|----------|------|
| 목록 화면 (실시간 갱신 필요) | `Flow<List<T>>` | DB 변경 시 UI 자동 갱신 |
| 단건 조회 (한 번만) | `suspend fun T?` | 코루틴으로 비동기, 완료 후 결과 반환 |
| 저장/삭제 | `suspend fun` | IO 스레드에서 비동기 처리 |
| 개수 확인 | `suspend fun Int` | 즉시 결과 필요, 구독 불필요 |

`observeAll()`이 `Flow`를 반환하는 것이 핵심입니다. Room은 `@Query` + `Flow` 조합에서 해당 테이블이 변경될 때마다 새 데이터를 자동으로 emit합니다. 새 스캔을 저장하면 리포트 목록 화면이 자동으로 갱신됩니다. `notifyDataSetChanged()`를 호출하거나 화면을 수동으로 새로고침할 필요가 없습니다.

---

## 8.6 마이그레이션 전략 — 데이터는 사용자의 것

앱 업데이트로 DB 스키마가 바뀌면 어떻게 될까요? Room은 버전 번호로 변경을 감지합니다. 마이그레이션 경로를 제공하지 않으면 Room이 예외를 던집니다.

### 원칙

1. **`fallbackToDestructiveMigration()`은 개발 중에만.** 이 옵션은 마이그레이션 실패 시 DB 전체를 삭제하고 새로 만듭니다. 개발 편의를 위해 현재 코드에 사용되고 있지만, 프로덕션 릴리즈 전에는 반드시 제거해야 합니다. 사용자 스캔 이력이 모두 사라집니다.

2. **스키마 변경은 버전 증가 + Migration 클래스.** `@Database(version = 2)`로 올리고 `Migration(1, 2)`를 작성해 Room에 등록합니다.

3. **마이그레이션은 반드시 테스트.** `MigrationTestHelper`로 v1 → v2 마이그레이션이 실제로 동작하는지 검증합니다.

### 버전 계획

| DB 버전 | 변경 내용 | 예상 시기 |
|---------|----------|---------|
| 1 | 초기 스키마 (4개 테이블) | Phase 1 MVP |
| 2 | RiskPoint에 LiDAR 컬럼 추가 | Phase 3 |
| 3 | 커뮤니티 맵 테이블 추가 | Phase 3 |
| 4 | ML 학습 데이터 테이블 추가 | Phase 3 |

### 마이그레이션 예시

```kotlin
// v1 → v2: risk_points에 LiDAR 컬럼 추가
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE risk_points ADD COLUMN lidar_distance REAL")
        db.execSQL("ALTER TABLE risk_points ADD COLUMN lidar_confidence REAL")
    }
}

// DatabaseModule에 등록
Room.databaseBuilder(context, AppDatabase::class.java, "searcam.db")
    .openHelperFactory(factory)
    .addMigrations(MIGRATION_1_2)   // fallbackToDestructiveMigration 대신
    .build()
```

Room 2.4.0 이상에서는 단순한 컬럼 추가라면 `autoMigrations`를 쓸 수도 있습니다.

```kotlin
@Database(
    entities = [...],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2)  // 컬럼 추가/삭제만 가능
    ]
)
```

단, 컬럼 이름 변경, 테이블 분리/병합 같은 복잡한 변경은 여전히 수동 `Migration`이 필요합니다.

---

## 8.7 데이터 보존 정책

무료 사용자의 스캔 이력은 최근 10건으로 제한합니다. 리포트 저장 시 개수를 확인하고 초과분을 삭제합니다.

```kotlin
// ReportRepository 내부 구현 패턴
suspend fun saveReport(report: ScanReport) {
    val entity = report.toEntity()
    reportDao.insert(entity)

    // 무료 사용자: 10건 초과 시 오래된 것 삭제
    if (!isPremium) {
        val count = reportDao.count()
        if (count > MAX_FREE_REPORTS) {
            // 오래된 리포트 삭제 → CASCADE로 devices, risk_points 자동 삭제
            val oldest = reportDao.observeAll()
                .first()
                .drop(MAX_FREE_REPORTS)
            oldest.forEach { reportDao.delete(it) }
        }
    }
}

companion object {
    private const val MAX_FREE_REPORTS = 10
}
```

| 사용자 유형 | 저장 한도 | 예상 용량 |
|------------|---------|---------|
| 무료 | 최근 10건 | ~55KB |
| 프리미엄 (월 2,900원) | 무제한 | 월 30건 기준 ~2MB/년 |

---

## 핵심 정리

| 개념 | 결론 |
|------|------|
| PK 타입 | UUID 문자열 — 미래 동기화 대비 |
| 복합 타입 저장 | JSON 직렬화 — JOIN 최소화 |
| 암호화 | SQLCipher + Keystore — 파일 탈취 무력화 |
| 인덱스 | FK 컬럼에 필수 — 쿼리 성능 보장 |
| 마이그레이션 | 수동 Migration 클래스 — 데이터 손실 방지 |
| TypeConverter | 방어적 파싱 — 크래시 방지 |

- ✅ `exportSchema = true`로 스키마 버전 추적
- ✅ FK 컬럼에 반드시 `Index` 선언
- ✅ `onDelete = CASCADE`로 참조 무결성 유지
- ✅ 프로덕션에서 `fallbackToDestructiveMigration()` 제거
- ❌ DB 파일을 평문으로 저장하지 말 것
- ❌ 패스프레이즈를 코드에 하드코딩하지 말 것

---

## 다음 장 예고

데이터를 어떻게 저장하는지 배웠습니다. 이제 데이터가 어떻게 흘러가는지 — 센서에서 화면까지의 단방향 데이터 흐름을 Ch09에서 다룹니다.

---
*참고 자료: docs/07-db-schema.md, AppDatabase.kt, DatabaseModule.kt, TypeConverters.kt*
