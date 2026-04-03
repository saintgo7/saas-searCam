# Ch10: 프로젝트 초기 설정 — 집을 짓기 전에 기초 공사부터

> **이 장에서 배울 것**: 좋은 앱은 좋은 기초에서 시작합니다. SearCam을 만들면서 선택한 기술 결정들 — Gradle Version Catalog, Hilt DI, Clean Architecture 패키지 구조, 권한 전략 — 이 선택들이 왜 그렇게 이루어졌는지 보여줍니다.

---

## 도입

건물을 지을 때 가장 중요한 작업은 기초 공사입니다. 눈에 보이지 않지만, 기초가 흔들리면 아무리 화려한 외벽도 소용없죠. 소프트웨어도 마찬가지입니다. 초기 프로젝트 설정에서 내린 결정들은 앱의 성장 내내 개발팀을 제약하거나 자유롭게 해줍니다.

SearCam을 처음 만들 때 저는 "나중에 고치면 되지"라는 생각을 의도적으로 금지했습니다. 기술 부채는 이자가 붙습니다. 초반에 0.1% 타협이 나중에는 30% 재작업으로 돌아옵니다. 이 장에서는 SearCam의 기초 공사 전 과정을 함께 따라가 봅니다.

---

## 10.1 Gradle Version Catalog 도입 배경

### 라이브러리 지옥에서 탈출하기

멀티모듈 프로젝트를 해본 개발자라면 이 상황을 알 겁니다. 모듈A의 `build.gradle.kts`에는 `compose_version = "1.5.4"`, 모듈B에는 `compose_version = "1.5.8"`. 버전이 조금씩 달라서 빌드가 깨지는데, 어디서 깨졌는지 찾는 데만 30분이 걸립니다.

Gradle Version Catalog는 이 문제를 중앙화로 해결합니다. `libs.versions.toml` 파일 하나가 모든 의존성의 진실의 원천(Single Source of Truth)이 됩니다.

```toml
# gradle/libs.versions.toml

[versions]
# 각 라이브러리 버전을 한 곳에서 관리합니다
agp = "8.3.0"
kotlin = "2.0.0"
compose-bom = "2024.04.01"
hilt = "2.51.1"
camerax = "1.3.3"
room = "2.6.1"
coroutines = "1.8.0"
timber = "5.0.1"

[libraries]
# 라이브러리를 별칭(alias)으로 정의합니다
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }

hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }

camerax-core = { group = "androidx.camera", name = "camera-core", version.ref = "camerax" }
camerax-camera2 = { group = "androidx.camera", name = "camera-camera2", version.ref = "camerax" }
camerax-lifecycle = { group = "androidx.camera", name = "camera-lifecycle", version.ref = "camerax" }

room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }

timber = { group = "com.jakewharton.timber", name = "timber", version.ref = "timber" }

[plugins]
# Gradle 플러그인도 Version Catalog로 관리합니다
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version = "2.0.0-1.0.22" }
```

이렇게 정의하면 각 모듈의 `build.gradle.kts`에서 타입 안전하게 참조할 수 있습니다.

```kotlin
// app/build.gradle.kts — 버전 숫자가 한 줄도 없습니다
dependencies {
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.timber)
}
```

버전을 올릴 때는 `libs.versions.toml` 한 파일만 수정하면 됩니다. 10개 모듈이 있어도 마찬가지입니다.

---

## 10.2 실제 build.gradle.kts 전체 구성

앱 모듈의 전체 빌드 설정을 살펴봅니다. 각 설정의 이유를 주석으로 달았습니다.

```kotlin
// app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)  // Room, Hilt 어노테이션 처리에 KSP 사용
}

android {
    namespace = "com.searcam"
    compileSdk = 34  // 최신 SDK로 컴파일 (새 API 사용 가능)

    defaultConfig {
        applicationId = "com.searcam"
        minSdk = 26  // Android 8.0 이상 (NsdManager 안정화 버전)
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "com.searcam.HiltTestRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true  // R8 코드 난독화 + 최적화
            isShrinkResources = true  // 미사용 리소스 제거
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            // 디버그 빌드는 최적화 없이 빠른 빌드
        }
    }

    buildFeatures {
        compose = true  // Jetpack Compose 활성화
        buildConfig = true  // BuildConfig 클래스 생성 (버전 정보 등)
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    // 자바 17 언어 기능 사용
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
```

---

## 10.3 Hilt DI 설계 — 왜 Hilt를 선택했는가

### 의존성 주입이 뭐길래

레스토랑 비유로 시작합니다. 요리사가 요리를 만들 때마다 직접 시장에 가서 재료를 사온다고 상상해보세요. 비효율적이죠. 대신 식재료 팀이 필요한 재료를 미리 준비해서 요리사에게 전달(주입)합니다. 요리사는 재료가 어디서 왔는지 알 필요가 없습니다.

코드에서 의존성 주입(DI)이 바로 이 역할입니다. `WifiScanner`가 `WifiManager`를 필요로 할 때, 직접 `getSystemService()`를 호출하는 대신 외부에서 주입받습니다. 덕분에 테스트 시 가짜(Mock) `WifiManager`를 주입할 수 있어 하드웨어 없이도 테스트가 가능합니다.

### Koin 대신 Hilt를 선택한 이유

| 기준 | Hilt | Koin |
|------|------|------|
| 컴파일 타임 검증 | O (오류를 빌드 시 발견) | X (런타임 오류 가능) |
| 성능 | 컴파일 타임 코드 생성 | 리플렉션 기반 (느림) |
| Android 통합 | 공식 지원 (Jetpack) | 서드파티 |
| 학습 곡선 | 가파름 | 완만함 |

SearCam은 탐지 신뢰성이 생명인 앱입니다. 런타임 DI 오류로 앱이 크래시 나면 사용자가 위험한 공간에 있을 때 도움을 못 받습니다. 컴파일 타임 안전성을 위해 Hilt를 선택했습니다.

### SearCam의 DI 모듈 구조

```kotlin
// di/AppModule.kt — 앱 전역 싱글톤을 제공합니다
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideApplicationContext(
        @ApplicationContext context: Context
    ): Context = context

    // IO 디스패처를 주입받아 테스트에서 교체 가능하게 합니다
    @Provides
    @Singleton
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
```

```kotlin
// di/SensorModule.kt — 센서 관련 시스템 서비스를 제공합니다
@Module
@InstallIn(SingletonComponent::class)
object SensorModule {

    @Provides
    @Singleton
    fun provideWifiManager(
        @ApplicationContext context: Context
    ): WifiManager =
        // Wi-Fi 서비스는 applicationContext로 가져와야 메모리 누수 방지
        context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager

    @Provides
    @Singleton
    fun provideSensorManager(
        @ApplicationContext context: Context
    ): SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    @Provides
    @Singleton
    fun provideNsdManager(
        @ApplicationContext context: Context
    ): NsdManager =
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
}
```

```kotlin
// di/RepositoryModule.kt — 인터페이스와 구현체를 연결합니다
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    // domain 레이어의 인터페이스를 data 레이어의 구현체에 바인딩합니다
    @Binds
    @Singleton
    abstract fun bindWifiScanRepository(
        impl: WifiScanRepositoryImpl
    ): WifiScanRepository

    @Binds
    @Singleton
    abstract fun bindMagneticRepository(
        impl: MagneticRepositoryImpl
    ): MagneticRepository

    @Binds
    @Singleton
    abstract fun bindLensDetectionRepository(
        impl: LensDetectionRepositoryImpl
    ): LensDetectionRepository
}
```

`@Binds`와 `@Provides`의 차이가 중요합니다. `@Binds`는 인터페이스와 구현체를 연결할 때, `@Provides`는 직접 객체를 생성해서 반환할 때 씁니다. 컴파일러가 `@Binds`를 더 효율적으로 처리합니다.

---

## 10.4 Clean Architecture 패키지 구조 결정 과정

### 처음 실수: 타입별 분류

처음에는 이렇게 했습니다.

```
com.searcam/
├── activities/
├── fragments/
├── viewmodels/
├── repositories/
├── models/
└── utils/
```

결과는 참담했습니다. Wi-Fi 스캔 관련 코드를 보려면 `repositories/`, `viewmodels/`, `models/`를 동시에 열어야 했습니다. 파일 네비게이션에 낭비하는 시간이 50%였습니다.

### 수정 방향: 레이어별 + 기능별 혼합

Clean Architecture는 레이어 분리가 핵심이지만, 같은 레이어 안에서는 기능별로 묶는 게 더 실용적입니다.

```
com.searcam/
│
├── di/           # 의존성 주입 (전역 설정)
│   ├── AppModule.kt
│   ├── SensorModule.kt
│   ├── DatabaseModule.kt
│   ├── AnalysisModule.kt
│   └── RepositoryModule.kt
│
├── domain/       # 순수 비즈니스 로직 (Android 의존성 0)
│   ├── model/    # 불변 데이터 클래스
│   ├── usecase/  # 비즈니스 유스케이스
│   └── repository/  # 인터페이스만 정의
│
├── data/         # 외부 시스템 연동
│   ├── sensor/   # 하드웨어 접근
│   ├── analysis/ # 탐지 알고리즘
│   ├── repository/  # 인터페이스 구현체
│   └── local/    # Room DB
│
├── ui/           # Compose 화면
│   ├── home/
│   ├── scan/
│   ├── lens/
│   ├── magnetic/
│   ├── report/
│   └── components/
│
└── util/         # 횡단 관심사
```

레이어 분리의 핵심 규칙: `domain/` 패키지에는 `android.` import가 단 하나도 없어야 합니다. 이걸 지키면 domain 로직을 순수 JVM 환경에서 테스트할 수 있습니다.

### 도메인 모델 예시 — 불변성 준수

```kotlin
// domain/model/NetworkDevice.kt
// Android import 없음. 순수 Kotlin data class입니다
data class NetworkDevice(
    val ip: String,
    val mac: String,
    val hostname: String? = null,
    val vendor: String? = null,        // OUI로 식별한 제조사
    val deviceType: DeviceType = DeviceType.UNKNOWN,
    val openPorts: List<Int> = emptyList(),
    val riskScore: Int = 0,
    val isCamera: Boolean = false
)

// 업데이트 시 불변성 유지 — 항상 새 객체를 반환합니다
fun NetworkDevice.withRiskScore(score: Int): NetworkDevice =
    copy(riskScore = score)

fun NetworkDevice.markAsCamera(): NetworkDevice =
    copy(isCamera = true, riskScore = maxOf(riskScore, 70))
```

---

## 10.5 AndroidManifest 권한 전략 — 최소 권한 원칙

### 왜 권한을 최소화해야 하는가

사용자는 이미 권한 요청에 피로해 있습니다. 앱을 설치하자마자 5개 권한을 한꺼번에 요청하면 "거부" 버튼을 누릅니다. 더 중요한 이유: 몰카 탐지 앱이 불필요한 권한(연락처, 위치 정밀 정보 등)을 요청하면 "이 앱이 오히려 내 정보를 수집하는 게 아닐까?" 하는 불신이 생깁니다.

SearCam의 권한 정책:
1. 기능에 실제로 필요한 권한만
2. 권한이 필요한 기능을 사용할 때만 요청 (런타임 권한)
3. 권한 거부 시 해당 레이어를 비활성화하되 앱은 계속 동작

```xml
<!-- AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Wi-Fi 스캔 — Layer 1 필수 -->
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
    <!-- 위치 권한: Android 8+ Wi-Fi 스캔에 필요합니다 (실제 위치 수집 안 함) -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <!-- Android 13+ 근처 Wi-Fi 기기 스캔 권한 -->
    <uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES"
        android:usesPermissionFlags="neverForLocation"
        tools:targetApi="tiramisu" />

    <!-- 카메라 — Layer 2 (렌즈/IR 감지) 필수 -->
    <uses-permission android:name="android.permission.CAMERA" />

    <!-- 인터넷 — 포트 스캔 (로컬 네트워크만, 인터넷 미사용) -->
    <uses-permission android:name="android.permission.INTERNET" />

    <!-- 권장 하드웨어 — 없어도 앱 설치 가능 -->
    <uses-feature
        android:name="android.hardware.camera.flash"
        android:required="false" />  <!-- 플래시 없으면 렌즈 감지 비활성화 -->
    <uses-feature
        android:name="android.hardware.sensor.compass"
        android:required="false" />  <!-- 자력계 없으면 EMF 레이어 비활성화 -->

    <!-- 위치 권한은 required="false" — Wi-Fi 연결 상태에서만 의미 있음 -->
    <uses-feature
        android:name="android.hardware.wifi"
        android:required="false" />

</manifest>
```

### 런타임 권한 요청 패턴

```kotlin
// util/PermissionHelper.kt
class PermissionHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // 필요한 권한을 기능별로 그룹화합니다
    val wifiScanPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val cameraPermissions = arrayOf(Manifest.permission.CAMERA)

    // 권한 상태를 체크합니다
    fun hasWifiPermissions(): Boolean =
        wifiScanPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        }

    fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
}
```

---

## 실습

> **실습 10-1**: 새 Android 프로젝트를 만들고 `libs.versions.toml`에 Hilt, CameraX, Room을 추가해보세요. `build.gradle.kts`에서 버전 숫자를 직접 쓰지 않고 `libs.` 접두사로만 참조하는지 확인하세요.

> **실습 10-2**: `di/RepositoryModule.kt`를 만들고 `WifiScanRepository` 인터페이스를 가짜 구현체(FakeWifiScanRepository)에 바인딩해보세요. Hilt 컴파일러가 오류를 어디서 잡아주는지 경험해보세요.

---

## 핵심 정리

| 결정 | 이유 |
|------|------|
| Version Catalog | 멀티모듈에서 버전 일관성 보장 |
| Hilt | 컴파일 타임 안전성, 테스트 용이성 |
| Clean Architecture | 레이어 독립성, domain 순수성 유지 |
| 최소 권한 | 사용자 신뢰, 불필요한 데이터 수집 방지 |

- 기술 부채는 초반에 차단할수록 비용이 적게 든다
- `domain/` 패키지에 Android import가 있다면 설계가 잘못된 것이다
- 권한은 사용하는 순간에, 이유와 함께 요청한다
- 런타임 DI 오류는 사용자에게 크래시로 돌아온다

---

## 다음 장 예고

기초 공사가 끝났으니 이제 첫 번째 탐지 레이어를 만들 차례입니다. Ch11에서는 탐지 가중치의 50%를 차지하는 Wi-Fi 스캔 시스템 — ARP 테이블 파싱부터 포트 스캐닝까지 — 을 구현합니다.

---
*참고 문서: docs/02-TRD.md, docs/03-TDD.md, docs/04-system-architecture.md*
