# Ch16: Room DB 구현 — 스캔 이력 영구 저장

> **이 장에서 배울 것**: SearCam이 스캔 이력을 앱 재시작 후에도 보존하는 방법을 배웁니다. Room 엔티티 → DAO → Repository 구현 패턴, Gson 없이 직접 만든 TypeConverters, SQLCipher와 Android Keystore로 DB를 암호화하는 전략, 실제 ReportRepositoryImpl 코드 해설, 스키마 변경 시 데이터를 잃지 않는 Migration 전략까지 — 안전하고 신뢰할 수 있는 로컬 저장소를 설계합니다.

---

## 도입

은행 금고를 생각해보세요. 금고는 세 가지 조건을 충족해야 합니다. 첫째, 물건을 넣고 뺄 수 있어야 합니다(CRUD). 둘째, 목록을 빠르게 조회할 수 있어야 합니다(인덱스). 셋째, 열쇠 없이는 아무것도 꺼낼 수 없어야 합니다(암호화).

SearCam의 스캔 이력 저장소가 바로 이 금고입니다. 사용자가 스캔할 때마다 결과가 기록되고, 다음에 앱을 켜도 이전 스캔 내역이 그대로 남아 있어야 합니다. 몰카 탐지 증거는 민감한 정보입니다 — 기기를 도난당하거나 포렌식 분석 대상이 되더라도 내용을 알 수 없어야 합니다.

이 장에서는 Room DB의 계층 구조를 처음부터 설계하고, 암호화까지 적용하는 전 과정을 따라갑니다.

---

## 16.1 Room 아키텍처 — 세 계층의 역할

### 추상화의 사다리

SQLite를 직접 사용하면 SQL 문자열을 하드코딩해야 합니다. 오타가 나도 컴파일 시점에 발견되지 않고 런타임에 앱이 크래시합니다. Room은 어노테이션 기반으로 SQL을 컴파일 시점에 검증합니다.

Room의 세 계층은 각자 명확한 책임을 가집니다.

```
[Domain Model]         → 순수 비즈니스 데이터 (Android 의존성 없음)
       ↕ 변환 (Mapper)
[Entity]               → SQLite 테이블 정의 (@Entity, @ColumnInfo)
       ↕ 접근
[DAO]                  → SQL 쿼리 정의 (@Query, @Insert, @Delete)
       ↕ 위임
[Repository 구현체]    → Domain Model ↔ Entity 변환 + DAO 호출
```

이 계층 분리가 중요한 이유: Domain Model은 데이터베이스가 어떻게 생겼는지 몰라야 합니다. 나중에 SQLite에서 DataStore로 바꾸거나 서버 API로 교체해도 Domain Model 코드를 전혀 건드리지 않습니다.

---

## 16.2 Entity 설계 — 테이블 구조 정의

### ScanReportEntity — 복잡한 객체를 테이블에 담기

스캔 결과(`ScanReport`)는 중첩 구조를 가집니다. `devices` 필드 하나에 여러 `NetworkDevice` 객체가 들어 있고, 각 `NetworkDevice`에는 또 개방 포트 목록이 있습니다. 이런 복잡한 중첩 구조를 SQLite 테이블에 어떻게 담을까요?

전략은 두 가지입니다.

**전략 A: 관계형 테이블 분리** — `NetworkDevice`를 별도 테이블로 만들고 외래키로 연결. 정규화 수준은 높지만 JOIN 쿼리가 복잡해집니다.

**전략 B: JSON 직렬화** — 중첩 리스트를 JSON 문자열로 직렬화해서 TEXT 컬럼 하나에 저장. 쿼리가 단순하지만 JSON 파싱 비용이 있습니다.

SearCam은 스캔 이력을 목록으로 보여주거나 특정 ID로 조회하는 패턴이 대부분입니다. 기기 정보를 독립적으로 필터링하는 복잡한 쿼리가 드물므로 전략 B를 선택했습니다.

```kotlin
// data/local/entity/ScanReportEntity.kt

@Entity(tableName = "scan_reports")
data class ScanReportEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,                  // UUID

    @ColumnInfo(name = "mode")
    val mode: String,                // enum → String (TypeConverter)

    @ColumnInfo(name = "started_at")
    val startedAt: Long,             // Unix epoch millis

    @ColumnInfo(name = "completed_at")
    val completedAt: Long,

    @ColumnInfo(name = "risk_score")
    val riskScore: Int,              // 0~100

    @ColumnInfo(name = "risk_level")
    val riskLevel: String,           // enum → String (TypeConverter)

    /** 기기 목록 — JSON 배열로 직렬화된 List<NetworkDevice> */
    @ColumnInfo(name = "devices_json")
    val devicesJson: String = "[]",

    /** 발견 사항 목록 — JSON 배열로 직렬화된 List<Finding> */
    @ColumnInfo(name = "findings_json")
    val findingsJson: String = "[]",

    @ColumnInfo(name = "location_note")
    val locationNote: String = "",

    /** 생성 시각 — 정렬 기준 */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
```

`@ColumnInfo(name = ...)`로 컬럼명을 명시하는 이유가 있습니다. Kotlin 프로퍼티 이름이 `riskScore`이지만 DB 컬럼명은 `risk_score`입니다. 스네이크 케이스 컬럼명은 SQL 관행이고, 나중에 프로퍼티 이름을 리팩토링해도 DB 컬럼명이 바뀌지 않습니다.

### 인덱스 추가 — 조회 성능 최적화

리포트 목록을 최신순으로 조회하는 쿼리가 자주 실행됩니다. `started_at` 컬럼에 인덱스를 추가하면 정렬 성능이 크게 개선됩니다.

```kotlin
@Entity(
    tableName = "scan_reports",
    indices = [
        Index(value = ["started_at"]),           // 정렬 최적화
        Index(value = ["risk_level"]),           // 위험도별 필터 최적화
    ]
)
data class ScanReportEntity( /* ... */ )
```

인덱스는 읽기 성능을 높이지만 쓰기 성능을 약간 낮춥니다. SearCam은 읽기(목록 조회)가 쓰기(스캔 저장)보다 훨씬 빈번하므로 인덱스가 적합합니다.

---

## 16.3 TypeConverters — Gson 없이 직접 변환

### Room이 저장할 수 없는 타입들

Room은 기본 타입(Int, Long, String, Boolean)과 byte array만 직접 저장할 수 있습니다. `RiskLevel` enum, `List<String>` 같은 타입은 `@TypeConverter`로 변환해야 합니다.

외부 라이브러리(Gson, Moshi) 없이 직접 TypeConverter를 구현하면 의존성이 줄고 변환 로직을 완전히 제어할 수 있습니다. SearCam의 `SearCamTypeConverters`가 이 역할을 합니다.

### List<String> ↔ JSON 변환

```kotlin
// data/local/converter/TypeConverters.kt

class SearCamTypeConverters {

    /**
     * List<String>을 JSON 배열 문자열로 직렬화한다.
     *
     * 예: ["서울","부산"] → `["서울","부산"]`
     */
    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        if (value.isNullOrEmpty()) return "[]"
        return value.joinToString(
            separator = ",",
            prefix = "[",
            postfix = "]",
            // 큰따옴표 안의 큰따옴표는 이스케이프 처리
            transform = { item -> "\"${item.replace("\"", "\\\"")}\"" },
        )
    }

    /**
     * JSON 배열 문자열을 List<String>으로 역직렬화한다.
     *
     * 파싱 실패 시 빈 리스트를 반환하고 오류를 로깅한다 (앱 크래시 방지).
     */
    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrBlank() || value.trim() == "[]") return emptyList()

        return try {
            val trimmed = value.trim().removePrefix("[").removeSuffix("]")
            if (trimmed.isBlank()) return emptyList()
            parseStringArray(trimmed)
        } catch (e: Exception) {
            Timber.e(e, "TypeConverter: List<String> 파싱 실패 — value=$value")
            emptyList()  // 파싱 실패 시 앱 크래시 대신 빈 리스트 반환
        }
    }
```

오류 처리가 중요합니다. TypeConverter에서 예외가 발생하면 Room이 DB 읽기 자체를 실패로 처리합니다. 결과적으로 스캔 목록 전체가 보이지 않는 치명적인 버그로 이어집니다. 파싱 실패 시 `emptyList()`를 반환하고 로그만 남기는 방어적 처리가 중요합니다.

### 문자열 배열 파서 — 이스케이프 처리

Gson이나 Moshi를 쓰지 않고 직접 JSON 배열을 파싱할 때 이스케이프된 큰따옴표 처리가 까다롭습니다. SearCam의 `parseStringArray`는 이 케이스를 정확히 처리합니다.

```kotlin
private fun parseStringArray(input: String): List<String> {
    val result = mutableListOf<String>()
    var i = 0

    while (i < input.length) {
        if (input[i] == '"') {
            val sb = StringBuilder()
            i++  // 여는 따옴표 건너뜀
            while (i < input.length && input[i] != '"') {
                if (input[i] == '\\' && i + 1 < input.length) {
                    i++  // 백슬래시 건너뜀
                    sb.append(input[i])  // 이스케이프된 문자 추가
                } else {
                    sb.append(input[i])
                }
                i++
            }
            result.add(sb.toString())
            i++  // 닫는 따옴표 건너뜀
        } else {
            i++
        }
    }

    return result
}
```

이 파서는 `["hello","world with \"quotes\""]` 같은 입력을 올바르게 처리합니다. `\"quotes\"`의 역슬래시를 보면 다음 문자를 그대로 포함합니다.

### enum TypeConverter — 안전한 역직렬화

```kotlin
@TypeConverter
fun fromRiskLevel(value: RiskLevel?): String {
    return value?.name ?: RiskLevel.SAFE.name  // null 시 기본값
}

@TypeConverter
fun toRiskLevel(value: String?): RiskLevel {
    if (value.isNullOrBlank()) return RiskLevel.SAFE

    return try {
        RiskLevel.valueOf(value)
    } catch (e: IllegalArgumentException) {
        // DB에 저장된 값이 현재 enum에 없을 때 (버전 업그레이드 시 가능)
        Timber.e(e, "알 수 없는 RiskLevel: $value, 기본값 SAFE 반환")
        RiskLevel.SAFE
    }
}
```

`RiskLevel.valueOf()`가 던지는 `IllegalArgumentException`을 잡는 이유: 앱 업데이트로 enum 값이 바뀌면 이전에 저장된 문자열이 현재 enum에 없을 수 있습니다. 예외 대신 기본값을 반환해서 앱이 안전하게 동작하도록 합니다.

### AppDatabase에 TypeConverter 등록

```kotlin
// data/local/AppDatabase.kt

@Database(
    entities = [
        ScanReportEntity::class,
        DeviceEntity::class,
        ChecklistEntity::class,
        RiskPointEntity::class,
    ],
    version = 1,
    exportSchema = true,  // schemas/ 디렉토리에 JSON 스키마 저장 (버전 관리용)
)
@TypeConverters(SearCamTypeConverters::class)  // 전체 DB에 적용
abstract class AppDatabase : RoomDatabase() {
    abstract fun reportDao(): ReportDao
    abstract fun deviceDao(): DeviceDao
    abstract fun checklistDao(): ChecklistDao
}
```

`exportSchema = true`로 설정하면 빌드 시 `schemas/` 디렉토리에 `{version}.json` 파일이 생성됩니다. 이 파일을 Git에 커밋하면 스키마 변경 이력을 추적할 수 있고, Migration 테스트에서도 활용합니다.

---

## 16.4 DAO — SQL을 코드로

### ReportDao — Flow로 실시간 업데이트

```kotlin
// data/local/dao/ReportDao.kt

@Dao
interface ReportDao {

    /**
     * 새 리포트를 저장하거나 기존 리포트를 덮어쓴다.
     *
     * REPLACE 전략: 동일 PK가 있으면 삭제 후 재삽입.
     * 중복 스캔 결과가 들어와도 안전합니다.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: ScanReportEntity)

    /**
     * 모든 리포트를 최신순으로 반환한다.
     *
     * Flow<List<...>>: DB가 바뀔 때마다 자동으로 새 목록을 emit합니다.
     * Room의 InvalidationTracker가 테이블 변경을 감지합니다.
     */
    @Query("SELECT * FROM scan_reports ORDER BY started_at DESC")
    fun observeAll(): Flow<List<ScanReportEntity>>

    /**
     * 특정 ID의 리포트를 한 번 조회한다.
     *
     * suspend fun: 코루틴에서 호출, IO 스레드에서 실행됩니다.
     * Flow가 아닌 단일 조회이므로 suspend fun을 씁니다.
     */
    @Query("SELECT * FROM scan_reports WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ScanReportEntity?

    @Delete
    suspend fun delete(report: ScanReportEntity)

    @Query("DELETE FROM scan_reports")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM scan_reports")
    suspend fun count(): Int
}
```

`observeAll()`이 반환하는 `Flow<List<ScanReportEntity>>`는 Room의 핵심 기능입니다. 새 리포트가 저장되거나 삭제되면 `Flow`가 자동으로 새 목록을 emit합니다. UI에서 이 Flow를 `collectAsStateWithLifecycle()`로 구독하면 DB 변경이 화면에 즉시 반영됩니다.

```kotlin
// ViewModel에서 Flow를 상태로 변환
val reports: StateFlow<List<ScanReport>> = reportRepository
    .observeAll()
    .map { entities -> entities.map(mapper::toDomain) }  // Entity → Domain 변환
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
```

`stateIn`의 `SharingStarted.WhileSubscribed(5_000)` 설정: 구독자가 없어진 후 5초간 Flow 수집을 유지합니다. 화면 회전 시 (약 2초) ViewModel이 재구성되는 동안 Flow가 재시작되지 않아 DB 재쿼리를 방지합니다.

---

## 16.5 Repository 구현체 — Domain ↔ Data 변환

### Mapper 패턴 — 계층 간 변환 책임 분리

```kotlin
// data/repository/ReportRepositoryImpl.kt

class ReportRepositoryImpl @Inject constructor(
    private val reportDao: ReportDao,
    private val mapper: ScanReportMapper,
    private val pdfGenerator: PdfGenerator,
    @ApplicationContext private val context: Context,
) : ReportRepository {

    override fun observeReports(): Flow<List<ScanReport>> {
        return reportDao.observeAll()
            .map { entities ->
                entities.map(mapper::toDomain)
            }
            .catch { e ->
                Timber.e(e, "리포트 목록 조회 실패")
                emit(emptyList())  // 오류 시 빈 목록으로 대체
            }
    }

    override suspend fun saveReport(report: ScanReport): Result<Unit> {
        return try {
            val entity = mapper.toEntity(report)
            reportDao.insert(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "리포트 저장 실패 — id: ${report.id}")
            Result.failure(e)
        }
    }

    override suspend fun getReport(reportId: String): Result<ScanReport> {
        return try {
            val entity = reportDao.findById(reportId)
                ?: return Result.failure(
                    NoSuchElementException("리포트를 찾을 수 없습니다: $reportId")
                )
            Result.success(mapper.toDomain(entity))
        } catch (e: Exception) {
            Timber.e(e, "리포트 조회 실패 — id: $reportId")
            Result.failure(e)
        }
    }

    override suspend fun exportToPdf(report: ScanReport, outputPath: String): Result<String> {
        val fullPath = buildOutputPath(outputPath)
        return pdfGenerator.generate(report, fullPath)
    }

    private fun buildOutputPath(fileName: String): String {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        return "${dir.absolutePath}/$fileName"
    }
}
```

`Result<T>`를 반환 타입으로 사용하는 패턴에 주목하세요. `try-catch`로 예외를 잡아 `Result.failure()`로 감싸면, 호출 측에서 예외 처리를 잊어도 컴파일러가 경고합니다. ViewModel에서 `result.onSuccess { }.onFailure { }`로 명시적으로 처리해야 합니다.

### Mapper 클래스 구현

```kotlin
// data/mapper/ScanReportMapper.kt

class ScanReportMapper @Inject constructor(
    private val gson: Gson,  // 또는 직접 구현한 직렬화기
) {

    fun toDomain(entity: ScanReportEntity): ScanReport {
        return ScanReport(
            id = entity.id,
            mode = ScanMode.valueOf(entity.mode),
            startedAt = entity.startedAt,
            completedAt = entity.completedAt,
            riskScore = entity.riskScore,
            riskLevel = RiskLevel.valueOf(entity.riskLevel),
            devices = parseDevices(entity.devicesJson),
            findings = parseFindings(entity.findingsJson),
            locationNote = entity.locationNote,
        )
    }

    fun toEntity(domain: ScanReport): ScanReportEntity {
        return ScanReportEntity(
            id = domain.id,
            mode = domain.mode.name,
            startedAt = domain.startedAt,
            completedAt = domain.completedAt,
            riskScore = domain.riskScore,
            riskLevel = domain.riskLevel.name,
            devicesJson = serializeDevices(domain.devices),
            findingsJson = serializeFindings(domain.findings),
            locationNote = domain.locationNote,
        )
    }

    // JSON 변환은 예외 처리와 함께
    private fun parseDevices(json: String): List<NetworkDevice> {
        return try {
            gson.fromJson(json, Array<NetworkDeviceDto>::class.java)
                ?.map { it.toDomain() }
                ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "기기 목록 파싱 실패")
            emptyList()
        }
    }
}
```

Mapper는 Entity와 Domain Model 사이의 통역사입니다. 이 클래스가 변환 책임을 단독으로 가지므로, 나중에 Entity 구조가 바뀌어도 Mapper만 수정하면 Domain Model은 그대로입니다.

---

## 16.6 SQLCipher 통합 — 데이터베이스 암호화

### 왜 암호화가 필요한가

Android 기기의 내부 저장소는 기본적으로 암호화되어 있습니다(Full-Disk Encryption 또는 File-Based Encryption). 그런데 루팅된 기기나 포렌식 도구를 사용하면 `/data/data/com.searcam/databases/` 파일에 직접 접근할 수 있습니다. SQLite 파일은 평문이므로 DB Browser for SQLite 같은 도구로 내용을 바로 볼 수 있습니다.

SQLCipher는 SQLite를 AES-256으로 암호화합니다. 파일을 복사해도 키 없이는 내용을 읽을 수 없습니다.

### Android Keystore + SQLCipher 통합

암호화 키를 앱 내부에 하드코딩하면 리버스 엔지니어링으로 키를 추출할 수 있습니다. Android Keystore System은 하드웨어 보안 모듈(HSM) 또는 TEE(Trusted Execution Environment)에 키를 저장해서 앱 프로세스 외부에서는 접근 불가능합니다.

```kotlin
// di/DatabaseModule.kt

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val DB_NAME = "searcam_db"
    private const val KEY_ALIAS = "searcam_db_key"

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase {
        val passphrase = getOrCreateDatabaseKey()
        val factory = SupportFactory(passphrase)

        return Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .openHelperFactory(factory)  // SQLCipher 팩토리 주입
            .fallbackToDestructiveMigration()  // 개발 중만 사용
            .build()
    }

    /**
     * Android Keystore에서 DB 암호화 키를 가져오거나 새로 생성한다.
     *
     * 키는 앱 설치 후 최초 1회 생성되며, 앱 제거 시 함께 삭제된다.
     * 기기의 보안 하드웨어(TEE/StrongBox)에 저장된다.
     */
    private fun getOrCreateDatabaseKey(): ByteArray {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        // 이미 키가 있으면 재사용
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            createDatabaseKey()
        }

        // AES 키로 랜덤 패스프레이즈 생성 후 Keystore 키로 암호화하여 SharedPreferences에 저장
        return loadEncryptedPassphrase()
    }

    private fun createDatabaseKey() {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        val keySpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        ).apply {
            setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            setKeySize(256)
            // 생체인증/PIN 인증 없이 접근 가능 (앱 자체 접근용)
            setUserAuthenticationRequired(false)
        }.build()

        keyGenerator.init(keySpec)
        keyGenerator.generateKey()
    }

    /**
     * 암호화된 패스프레이즈를 복호화하여 반환한다.
     *
     * 처음 호출 시: 랜덤 32바이트 생성 → Keystore 키로 암호화 → EncryptedSharedPreferences 저장
     * 이후 호출 시: EncryptedSharedPreferences에서 읽어 복호화하여 반환
     */
    private fun loadEncryptedPassphrase(): ByteArray {
        // EncryptedSharedPreferences는 Jetpack Security 라이브러리가 제공
        // 저장 자체도 암호화되어 이중 보호
        // 구현 생략 — Jetpack Security 라이브러리 활용
        TODO("구현체에서 처리")
    }
}
```

### SQLCipher 의존성 추가

```toml
# gradle/libs.versions.toml

[versions]
sqlcipher = "4.5.6"

[libraries]
sqlcipher-android = { group = "net.zetetic", name = "android-database-sqlcipher", version.ref = "sqlcipher" }
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(libs.sqlcipher.android)
    // SQLCipher는 Room과 함께 쓸 때 SupportFactory를 통해 통합
}
```

`SupportFactory(passphrase)`는 Room의 `openHelperFactory`에 전달하는 SQLCipher 팩토리입니다. Room은 내부적으로 `SupportSQLiteOpenHelper`를 사용하는데, SQLCipher가 이 인터페이스를 구현하여 암호화된 SQLite를 제공합니다. Room 코드 변경 없이 암호화가 적용되는 아름다운 설계입니다.

---

## 16.7 Migration 전략 — 데이터를 잃지 않고 스키마 바꾸기

### fallbackToDestructiveMigration은 개발 중에만

`fallbackToDestructiveMigration()`은 스키마 버전이 맞지 않으면 DB를 통째로 삭제하고 새로 만듭니다. 개발 중에는 편리하지만 프로덕션에서는 사용자 데이터를 날립니다. 절대 릴리즈 빌드에 포함하면 안 됩니다.

```kotlin
// 개발용 — 절대 프로덕션 사용 금지
Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
    .fallbackToDestructiveMigration()
    .build()

// 프로덕션용 — Migration 객체를 명시적으로 추가
Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
    .build()
```

### Migration 작성 방법

스키마를 변경할 때의 절차:

1. `AppDatabase`의 `version`을 올린다 (예: 1 → 2)
2. `Migration(from, to)` 객체를 작성한다
3. `addMigrations()`에 등록한다

```kotlin
// Migration 1 → 2: location_note 컬럼 추가
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // SQLite는 컬럼 추가만 가능 (삭제/변경은 테이블 재생성 필요)
        database.execSQL(
            "ALTER TABLE scan_reports ADD COLUMN location_note TEXT NOT NULL DEFAULT ''"
        )
    }
}

// Migration 2 → 3: 새 인덱스 추가
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_scan_reports_risk_level " +
            "ON scan_reports (risk_level)"
        )
    }
}
```

### MigrationTestHelper로 Migration 검증

Migration은 반드시 테스트해야 합니다. 프로덕션에서 Migration 실패는 사용자 데이터 손실로 이어집니다.

```kotlin
// test/MigrationTest.kt

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate1To2() {
        // 버전 1로 DB 생성
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO scan_reports (id, mode, started_at, completed_at, risk_score, risk_level) VALUES ('test-id', 'QUICK', 0, 0, 50, 'CAUTION')")
            close()
        }

        // 버전 2로 Migration
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        // location_note 컬럼이 추가되었는지 확인
        val cursor = db.query("SELECT location_note FROM scan_reports WHERE id = 'test-id'")
        assertTrue(cursor.moveToFirst())
        assertEquals("", cursor.getString(0))
        cursor.close()
    }
}
```

---

## 16.8 DatabaseModule과 Hilt 등록

### 싱글톤으로 DB 인스턴스 관리

Room DB는 앱 전체에서 하나의 인스턴스만 사용해야 합니다. 여러 인스턴스를 만들면 데이터 일관성 문제와 성능 저하가 발생합니다.

```kotlin
// di/DatabaseModule.kt

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "searcam_db",
        )
        // 프로덕션: .addMigrations(MIGRATION_1_2) 추가
        .fallbackToDestructiveMigration()
        .build()
    }

    // DAO는 DB 인스턴스에서 생성 — DB와 같은 싱글톤 범위
    @Provides
    @Singleton
    fun provideReportDao(db: AppDatabase): ReportDao = db.reportDao()

    @Provides
    @Singleton
    fun provideDeviceDao(db: AppDatabase): DeviceDao = db.deviceDao()

    @Provides
    @Singleton
    fun provideChecklistDao(db: AppDatabase): ChecklistDao = db.checklistDao()
}
```

DAO를 별도로 `@Provides`로 등록하는 이유: `ReportRepositoryImpl`이 `ReportDao`를 주입받을 때, `AppDatabase` 전체를 주입받을 필요가 없습니다. 필요한 DAO만 주입받으므로 의존성이 명확하고 테스트 시 DAO만 Mock 처리할 수 있습니다.

---

## 실습

> **실습 16-1**: `ScanReportEntity`에 `duration_ms` 컬럼을 추가하는 Migration을 작성해보세요. `MigrationTestHelper`로 이전 버전 데이터가 마이그레이션 후에도 보존되는지 확인하세요.

> **실습 16-2**: `SearCamTypeConverters`의 `toStringList()`에 `["hello","world with \"quotes\"","test"]` 입력을 테스트해보세요. 이스케이프된 따옴표가 올바르게 처리되는지 단위 테스트로 검증하세요.

> **실습 16-3**: Room의 `exportSchema = true` 설정으로 생성된 `schemas/1.json` 파일을 열어보세요. 각 테이블의 컬럼 정의와 인덱스가 어떻게 기록되는지 확인하세요.

---

## 핵심 정리

| 개념 | 핵심 |
|------|------|
| Entity | SQLite 테이블 정의, `@ColumnInfo`로 컬럼명과 프로퍼티명 분리 |
| TypeConverter | Room이 지원하지 않는 타입 변환, 오류 시 기본값 반환으로 크래시 방지 |
| DAO Flow | `Flow<List<T>>` 반환 시 DB 변경 자동 감지 및 emit |
| Mapper | Entity ↔ Domain 변환 책임 분리, 계층 간 의존성 차단 |
| SQLCipher | AES-256으로 DB 파일 암호화, Room에 `SupportFactory`로 통합 |
| Android Keystore | 암호화 키를 하드웨어 보안 영역에 저장, 앱 프로세스 외부 접근 불가 |
| Migration | `version` 증가 + `Migration(from, to)` 객체 필수, `MigrationTestHelper`로 검증 |
| exportSchema | 스키마 JSON을 Git으로 관리, Migration 이력 추적 |

- `fallbackToDestructiveMigration()`은 개발 빌드에만, 프로덕션은 반드시 Migration 필요
- TypeConverter 파싱 오류는 예외 대신 기본값으로 처리해야 앱이 안전하다
- Room의 Flow는 테이블 변경을 자동 감지한다 — 별도 새로고침 로직 불필요
- SQLCipher 패스프레이즈는 Android Keystore에 보관해야 역공학으로 추출 불가능하다

---

## 다음 장 예고

데이터가 영구 저장되었으니 이제 이 데이터를 PDF로 만들 차례입니다. Ch17에서는 Android 내장 `PdfDocument` API로 증거 문서를 생성하고, 경로 순회 공격을 방어하고, `FileProvider`로 다른 앱과 안전하게 파일을 공유하는 방법을 구현합니다.

---
*참고 문서: docs/07-db-schema.md, docs/14-security-design.md, docs/08-error-handling.md*
