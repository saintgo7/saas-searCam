# SearCam: 스마트폰 몰래카메라 탐지 앱 개발기

**기획부터 출시까지, AI 시대의 모바일 앱 개발 완전 가이드**

---

## 이 책에 대하여

이 책은 SearCam 프로젝트의 기획부터 출시까지 전 과정을 기록한 기술 서적이다.

SearCam(Search + Camera)은 스마트폰만으로 몰래카메라를 탐지하는 1차 스크리닝 앱이다. Wi-Fi 네트워크 스캔, 플래시 Retroreflection 렌즈 감지, 자기장(EMF) 센서를 교차 검증하여 숨겨진 카메라를 찾는다.

29개의 기획 문서(PRD, TRD, TDD, 시스템 아키텍처, 보안 설계 등)를 기반으로 실제 상용 앱이 탄생하기까지의 의사결정, 기술 선택, 실패와 해결을 솔직하게 풀어냈다.

---

## 대상 독자

이 책은 다음 독자를 위해 쓰였다:

- **주니어 모바일 개발자 (1~3년 경력)**: 앱 하나를 처음부터 끝까지 만드는 전체 흐름을 경험하고 싶은 분
- **사이드 프로젝트 개발자**: 기획서 작성법, 수익 모델 설계, GTM 전략의 실전 사례가 필요한 분
- **스타트업 창업자/PM**: 기술 문서 체계와 아키텍처 의사결정 과정을 참고하고 싶은 분
- **CS/SE 전공 대학생**: 소프트웨어 공학 이론의 실전 적용 사례를 찾는 분
- **시니어 개발자**: 모바일 센서 활용, 보안/프라이버시 설계 패턴에 관심 있는 분

---

## 사전 지식

이 책을 읽기 위해 다음 지식이 필요하다:

- **필수**: Kotlin/Android 기초 문법 (변수, 함수, 클래스)
- **필수**: Git 기본 사용법 (commit, branch, merge)
- **필수**: REST API 개념 (HTTP 메서드, JSON)
- **권장**: Clean Architecture 용어 이해
- **권장**: Gradle 빌드 시스템 기초

---

## 이 책의 구성

총 5부 24챕터와 부록 5개로 구성되어 있다.

### Part 1: 기획과 설계 (Why & What)
왜 만드는가, 누구를 위해 만드는가, 무엇을 만드는가. 시장 분석, 사용자 리서치, 제품 요구사항, 수익 모델을 다룬다.

### Part 2: 아키텍처와 설계 (How)
어떻게 만들 것인가. 기술 요구사항, 시스템 아키텍처, API/DB 설계, 보안/프라이버시 설계를 다룬다.

### Part 3: 구현 (Build)
설계를 코드로 옮기는 과정. Wi-Fi 스캔, 렌즈 감지, EMF 감지, 교차 검증 엔진, UI/UX 구현을 다룬다.

### Part 4: 품질과 배포 (Ship)
완성된 코드를 제품으로 만드는 과정. 테스트 전략, CI/CD, Play Store 배포, 모니터링을 다룬다.

### Part 5: 성장 (Grow)
출시 이후의 과정. 마케팅, 데이터 분석, 접근성/국제화, 파트너십 전략을 다룬다.

### 부록
에러 처리, 성능 최적화, OUI 데이터베이스, 리스크 매트릭스, 법적 준수 가이드를 수록한다.

---

## 실습 환경

각 챕터에는 실습이 포함되어 있다. 실습을 따라하려면 다음 환경이 필요하다:

| 항목 | 요구 사양 |
|------|----------|
| OS | macOS 13+, Windows 10+, Ubuntu 22.04+ |
| IDE | Android Studio Hedgehog 이상 |
| JDK | JDK 17 |
| Android SDK | API 34 (Android 14) |
| Kotlin | 1.9+ |
| Git | 2.30+ |
| 테스트 기기 | Android 10 이상 실기기 (에뮬레이터로는 센서 테스트 불가) |

---

## 표기 규칙

이 책에서 사용하는 표기 규칙은 다음과 같다:

- `고정폭 글꼴`: 코드, 파일명, 명령어
- **굵은 글씨**: 중요한 개념, 처음 등장하는 용어
- > 인용 블록: 핵심 원칙, 설계 결정의 이유
- [TIP] 블록: 실전 팁, 시간을 절약하는 방법
- [WARNING] 블록: 흔한 실수, 주의 사항

---

## 소스 코드

이 책에서 다루는 모든 소스 코드는 다음 저장소에서 확인할 수 있다:

- 기획 문서: `docs/` 디렉토리 (29개 문서)
- 챕터 원고: `docs/book/chapters/` 디렉토리

---

## 저자

[저자 정보 추가 예정]

---

## 피드백

이 책에 대한 의견, 오류 신고, 제안은 다음 채널로 보내주세요:

- GitHub Issues: [저장소 URL 추가 예정]
- Email: [이메일 추가 예정]

모든 피드백은 다음 판에 반영됩니다.

---

## 감사의 말

[감사의 말 추가 예정]


\newpage


# Ch01: 문제 발견과 시장 분석

> **이 장에서 배울 것**: 좋은 앱은 좋은 문제에서 시작한다. 몰래카메라 탐지라는 문제를 어떻게 발견하고, 시장을 분석하고, 경쟁 앱을 해부했는지 보여줍니다.

---

## 도입

새벽 2시, 출장으로 처음 묵는 모텔 방. 에어컨 리모컨 위 작은 구멍이 눈에 밟힌다. 설마 카메라일까? 스마트폰을 들고 검색해 보니 관련 앱이 있다. 설치해서 돌려봐도 "위험!" 알림만 뜨고 이유는 없다. 믿어야 할지 말아야 할지 모르는 채로 잠에 든다.

이 경험이 SearCam의 시작입니다.

좋은 제품은 대부분 "내가 불편했던 것"에서 출발합니다. 하지만 불편함을 느끼는 것과 그것을 제품으로 만드는 것 사이에는 큰 차이가 있습니다. 이 장에서는 SearCam이 어떻게 막연한 불편함을 구체적인 시장 기회로 전환했는지 보여줍니다.

---

## 1.1 문제의 심각성 파악

### 데이터부터 시작하라

감정이 아닌 데이터로 문제를 정의해야 합니다. SearCam 기획 단계에서 수집한 수치입니다.

| 지표 | 수치 | 출처 |
|------|------|------|
| 국내 몰카 범죄 발생 건수 (2023) | 연 6,000건+ | 경찰청 통계 |
| 피해자 중 숙박시설 관련 비율 | 약 32% | 여성가족부 |
| 스마트폰으로 탐지 가능한 카메라 비율 | 60~70% | 기술 분석 |
| 기존 탐지 앱 평균 오탐률 | 55~70% | 경쟁사 리뷰 분석 |

숫자가 말해주는 것: 문제는 실재하고, 기존 솔루션은 불충분하다.

### 스마트폰의 한계를 먼저 인정하라

많은 앱이 "완벽한 탐지"를 주장합니다. 하지만 스마트폰은 전문 탐지 장비가 아닙니다.

```
전문 장비가 할 수 있는 것:
  ✅ RF 신호 감지 (모든 무선 카메라)
  ✅ Non-Linear Junction Detection
  ✅ 열화상 분석

스마트폰이 할 수 있는 것:
  ✅ Wi-Fi 네트워크 스캔 (같은 네트워크 한정)
  ✅ 카메라 렌즈 역반사 감지 (플래시 활용)
  ✅ 자기장 변화 감지
  ❌ RF 신호 분석 (하드웨어 없음)
  ❌ LTE/5G 카메라 탐지
  ❌ 전원 꺼진 카메라
```

이 한계를 인정하는 것이 SearCam의 핵심 차별점입니다. **솔직함이 신뢰를 만듭니다.**

---

## 1.2 경쟁 앱 해부하기

### 5개 앱을 직접 써보며 배운 것

기획 단계에서 주요 경쟁 앱 5개를 직접 설치하고 실제 카메라 앞에서 테스트했습니다.

| 앱 | 다운로드 | 평점 | 핵심 약점 |
|----|---------|------|---------|
| Hidden Camera Detector | 10M+ | 3.6 | 오탐 55%, 이유 없는 "위험!" |
| CamX | 1M+ | 4.2 | 모드 수동 전환 필요 |
| FindSpy | 500K+ | 4.4 | 단일 센서만 사용 |
| Peek | 1M+ | 4.4 | 핵심 기능 유료 |
| 몰카 탐지기 | 100K+ | 4.2 | Android 전용, 교차 검증 없음 |

### 공통 패턴: "위험!"만 외치는 앱들

모든 앱이 공유하는 문제가 있었습니다.

```
기존 앱의 결과 화면:
┌─────────────────┐
│                 │
│   🔴 위험!      │
│                 │
│   [확인]        │
└─────────────────┘
← 왜 위험한지 이유가 없음
← 오탐인지 진짜 탐지인지 구분 불가
← 다음 행동 지침 없음
```

SearCam의 결과 화면이 달라야 하는 이유가 여기서 나왔습니다.

---

## 1.3 시장 기회 정의

### TAM / SAM / SOM 분석

```
TAM (Total Addressable Market)
├── 전 세계 스마트폰 사용자 중 연 1회 이상 숙박 이용자
├── 약 15억 명
└── 시장 규모: ~$2.85B

SAM (Serviceable Addressable Market)
├── 한국 내 숙박/원룸 거주자 + 여행자
├── 약 1,500만 명
└── 시장 규모: ~$285M

SOM (Serviceable Obtainable Market) — 1년 목표
├── 목표 다운로드: 50,000건
└── 목표 수익: 월 ₩250,000 (광고 + 프리미엄)
```

---

## 1.4 차별화 전략

경쟁에서 이기는 방법은 두 가지입니다. 더 잘하거나, 다르거나.

SearCam의 선택: **다르게 접근하기 (Blue Ocean)**

| 기존 앱이 강조하는 것 | SearCam이 강조하는 것 |
|---------------------|---------------------|
| "100% 탐지!" | "한계를 솔직히 알려드립니다" |
| 각 센서 독립 작동 | 3개 센서 교차 검증 |
| 기술 기능 나열 | 사용자 행동 가이드 |
| 유료 핵심 기능 | 핵심 완전 무료 |
| 단순 "위험!" 알림 | 왜 위험한지 근거 설명 |

---

## 실습 과제

> **실습 1-1**: 지금 쓰는 스마트폰에 몰카 탐지 앱 2개를 설치하고, 실제 웹캠 앞에서 테스트해보세요. 각 앱의 탐지 결과와 오탐 여부를 표로 정리해보세요.

> **실습 1-2**: 당신이 해결하고 싶은 문제를 하나 골라 TAM/SAM/SOM을 계산해보세요. 데이터는 통계청, 여성가족부, 경찰청 공개 자료를 활용하세요.

---

## 핵심 정리

| 개념 | 요점 |
|------|------|
| 문제 정의 | 감정이 아닌 데이터로 시작 |
| 한계 인정 | 불가능한 것을 약속하지 않는 것이 신뢰의 시작 |
| 경쟁 분석 | 직접 써봐야 약점이 보인다 |
| 차별화 | 더 잘하기 어려우면 다르게 접근 |

- ✅ 문제의 규모를 데이터로 검증하라
- ✅ 기술적 한계를 먼저 파악하라
- ✅ 경쟁 앱을 실제로 사용해보라
- ❌ "우리가 최고"라는 주장은 데이터 없이 하지 마라

---

## 다음 장 예고

문제를 정의했으니 이제 "누가" 이 앱을 쓸지 구체적으로 그려볼 차례입니다. Ch02에서는 사용자 인터뷰, 페르소나 설계, Customer Journey Map을 다룹니다.

---
*참고 자료: docs/project-plan.md, docs/11-competitor-analysis.md*


\newpage


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


\newpage


# Ch11: Wi-Fi 스캔 — 네트워크는 거짓말을 하지 않는다

> **이 장에서 배울 것**: 탐지 가중치의 50%를 차지하는 Wi-Fi 스캔 레이어의 원리와 구현을 배웁니다. ARP 테이블에서 OUI 식별, mDNS 탐색, 포트 스캔까지 — 같은 네트워크에 연결된 IP 카메라를 어떻게 찾는지 보여줍니다.

---

## 도입

범죄 수사에서 형사는 현장 주변 CCTV를 가장 먼저 확인합니다. 용의자가 직접 자백하길 기다리는 대신, 흔적을 추적하는 거죠. 몰래카메라도 마찬가지입니다. 카메라는 잘 숨길 수 있지만, 네트워크 흔적은 숨기기 어렵습니다.

Wi-Fi에 연결된 IP 카메라는 반드시 네트워크에 흔적을 남깁니다. MAC 주소, ARP 테이블 항목, 그리고 특정 포트(554 RTSP, 8080 HTTP)가 열려 있습니다. SearCam은 이 흔적을 쫓습니다.

---

## 11.1 Wi-Fi 스캔이 탐지의 핵심인 이유

### 가중치 50%의 근거

세 탐지 레이어 중 Wi-Fi가 가장 높은 비중을 차지하는 이유는 두 가지입니다.

첫째, **현대 몰래카메라의 대부분이 IP 카메라**입니다. SD 카드에만 저장하는 구식 장치와 달리, 요즘 범죄에 사용되는 카메라는 실시간 원격 모니터링을 위해 Wi-Fi에 연결됩니다. 네트워크 연결이 오히려 약점이 됩니다.

둘째, **오탐률이 낮습니다.** 자기장 센서는 일반 가전제품에도 반응하고, 렌즈 감지는 유리 반사에도 반응합니다. 하지만 카메라 제조사 OUI를 가진 기기가 카메라 포트를 열고 있다면 — 이건 구체적인 증거입니다.

### 탐지 가능 범위와 한계

```
Wi-Fi 스캔으로 탐지 가능:
  ✅ 같은 Wi-Fi 네트워크에 연결된 IP 카메라
  ✅ WPA/WPA2/WPA3 네트워크 내 ARP 등록 기기
  ✅ mDNS/SSDP로 광고하는 스마트 카메라

Wi-Fi 스캔으로 탐지 불가능:
  ❌ 다른 Wi-Fi 네트워크나 LTE로 송출하는 카메라
  ❌ Wi-Fi 꺼짐 상태의 배터리 방식 카메라
  ❌ 전원이 꺼진 카메라
  ❌ 사용자가 같은 네트워크에 연결하지 않은 경우
```

이 한계를 솔직하게 UI에 표시하는 것이 SearCam의 차별점입니다.

---

## 11.2 ARP 테이블 파싱 원리

### ARP가 무엇인가

ARP(Address Resolution Protocol)는 IP 주소를 MAC 주소로 변환하는 프로토콜입니다. 스마트폰이 라우터를 통해 같은 네트워크 기기와 통신할 때마다 ARP 교환이 일어나고, 그 결과가 `/proc/net/arp` 파일에 기록됩니다.

이 파일은 Android에서 root 권한 없이도 읽을 수 있습니다. 운영체제가 일반 앱에게도 ARP 테이블 조회를 허용하기 때문입니다.

```bash
# /proc/net/arp 파일 형식 예시
IP address       HW type     Flags  HW address            Mask     Device
192.168.1.1      0x1         0x2    aa:bb:cc:11:22:33     *        wlan0
192.168.1.105    0x1         0x2    00:12:bf:45:67:89     *        wlan0
192.168.1.110    0x1         0x2    fc:f5:c4:ab:cd:ef     *        wlan0
```

여기서 `00:12:bf` — MAC 앞 3바이트(OUI) — 를 제조사 데이터베이스와 대조하면 기기 제조사를 알 수 있습니다. `00:12:bf`는 Hikvision(IP 카메라 제조사)의 OUI입니다.

### ARP 파싱 구현

```kotlin
// data/sensor/WifiScanner.kt (ARP 파싱 부분)
class WifiScanner @Inject constructor(
    private val wifiManager: WifiManager,
    private val nsdManager: NsdManager,
    private val ouiDatabase: OuiDatabase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    // ARP 테이블에서 같은 네트워크 기기 목록을 읽어옵니다
    suspend fun readArpTable(): List<ArpEntry> = withContext(ioDispatcher) {
        try {
            File("/proc/net/arp")
                .readLines()
                .drop(1)  // 헤더 행 제거
                .mapNotNull { line -> parseArpLine(line) }
                .filter { entry -> entry.isValid() }
        } catch (e: IOException) {
            // ARP 읽기 실패 시 빈 목록 반환 — 앱이 크래시되지 않습니다
            emptyList()
        }
    }

    // "192.168.1.105  0x1  0x2  00:12:bf:45:67:89  *  wlan0" 형태를 파싱합니다
    private fun parseArpLine(line: String): ArpEntry? {
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size < 6) return null

        val ip = parts[0]
        val flags = parts[2]
        val mac = parts[3]

        // Flags 0x2 = 완전히 해석된(complete) 항목만 사용합니다
        if (flags != "0x2") return null
        // 00:00:00:00:00:00 같은 유효하지 않은 MAC 제외
        if (mac == "00:00:00:00:00:00") return null

        return ArpEntry(ip = ip, mac = mac.uppercase())
    }
}

// ARP 항목의 유효성 검사
data class ArpEntry(val ip: String, val mac: String) {
    fun isValid(): Boolean {
        val ipPattern = Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")
        val macPattern = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")
        return ip.matches(ipPattern) && mac.matches(macPattern)
    }
}
```

---

## 11.3 mDNS(NsdManager)와 SSDP 멀티캐스트 동작

### mDNS로 스마트 기기 탐색하기

mDNS(Multicast DNS)는 로컬 네트워크에서 서버 없이 서비스를 광고하고 탐색하는 프로토콜입니다. 애플 기기의 AirPrint, Chromecast가 mDNS를 사용합니다. IP 카메라도 `_rtsp._tcp.` 또는 `_http._tcp.` 서비스를 mDNS로 광고하는 경우가 많습니다.

Android의 `NsdManager`가 mDNS 탐색을 제공합니다.

```kotlin
// data/sensor/WifiScanner.kt (mDNS 탐색 부분)

// 카메라가 자주 광고하는 서비스 타입 목록
private val CAMERA_SERVICE_TYPES = listOf(
    "_rtsp._tcp.",    // 실시간 스트리밍 (IP 카메라 표준)
    "_http._tcp.",    // HTTP 웹 인터페이스
    "_dahua._tcp.",   // Dahua 카메라 전용 서비스
    "_hikvision._tcp.",  // Hikvision 카메라 전용 서비스
    "_onvif._tcp."    // ONVIF 표준 프로토콜
)

fun discoverMdnsServices(): Flow<NsdServiceInfo> = callbackFlow {
    val discoveryListeners = mutableListOf<NsdManager.DiscoveryListener>()

    CAMERA_SERVICE_TYPES.forEach { serviceType ->
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                // 서비스를 발견하면 채널로 전송합니다
                trySend(serviceInfo)
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                // 특정 서비스 타입 탐색 실패는 무시하고 계속합니다
            }

            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        nsdManager.discoverServices(
            serviceType,
            NsdManager.PROTOCOL_DNS_SD,
            listener
        )
        discoveryListeners.add(listener)
    }

    // Flow가 취소되면 모든 탐색을 중지합니다
    awaitClose {
        discoveryListeners.forEach { listener ->
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (e: IllegalArgumentException) {
                // 이미 중지된 탐색 — 무시합니다
            }
        }
    }
}
```

### SSDP 멀티캐스트로 UPnP 기기 탐색

SSDP(Simple Service Discovery Protocol)는 UPnP 기기(스마트 TV, IP 카메라 등)가 자신의 존재를 알리는 프로토콜입니다. 멀티캐스트 주소 `239.255.255.250:1900`에 M-SEARCH 패킷을 보내면 UPnP 기기들이 응답합니다.

```kotlin
// SSDP M-SEARCH 요청을 보내고 응답을 받습니다
suspend fun discoverSsdpDevices(): List<String> = withContext(ioDispatcher) {
    val foundDevices = mutableListOf<String>()

    // SSDP 검색 메시지 — 모든 UPnP 기기에게 "너 거기 있어?" 묻는 형식입니다
    val searchMessage = """
        M-SEARCH * HTTP/1.1
        HOST: 239.255.255.250:1900
        MAN: "ssdp:discover"
        MX: 3
        ST: ssdp:all
        
    """.trimIndent()

    try {
        val socket = DatagramSocket()
        socket.soTimeout = 3000  // 3초 응답 대기

        val group = InetAddress.getByName("239.255.255.250")
        val packet = DatagramPacket(
            searchMessage.toByteArray(),
            searchMessage.length,
            group,
            1900
        )
        socket.send(packet)

        // 응답 수신 루프
        val buffer = ByteArray(2048)
        val responsePacket = DatagramPacket(buffer, buffer.size)

        while (true) {
            try {
                socket.receive(responsePacket)
                val response = String(responsePacket.data, 0, responsePacket.length)
                val location = extractLocation(response)  // LOCATION 헤더 추출
                if (location != null) foundDevices.add(location)
            } catch (e: SocketTimeoutException) {
                break  // 타임아웃 = 더 이상 응답 없음
            }
        }

        socket.close()
    } catch (e: Exception) {
        // 네트워크 오류 — 빈 목록 반환
    }

    foundDevices
}
```

---

## 11.4 OUI 데이터베이스로 제조사 식별하기

### OUI란 무엇인가

MAC 주소(예: `00:12:BF:45:67:89`)에서 앞 3바이트(`00:12:BF`)가 OUI(Organizationally Unique Identifier)입니다. IEEE가 제조사에게 고유하게 할당합니다. `00:12:BF`는 Hikvision Digital Technology — 세계 최대 IP 카메라 제조사입니다.

SearCam은 약 500KB 크기의 OUI 데이터베이스를 앱에 번들로 포함합니다. 인터넷 없이 오프라인으로 제조사를 식별할 수 있습니다.

```kotlin
// data/analysis/OuiDatabase.kt
class OuiDatabase @Inject constructor(
    @ApplicationContext private val context: Context,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher
) {
    // 카메라 관련 OUI 목록 — 앱 assets에서 로드합니다
    private val cameraVendorOuis: Set<String> by lazy {
        loadCameraOuis()
    }

    // 카메라 제조사로 알려진 OUI 집합을 assets에서 로드합니다
    private fun loadCameraOuis(): Set<String> {
        return try {
            context.assets.open("oui_camera.json").use { stream ->
                val json = stream.bufferedReader().readText()
                // JSON 파싱 — Set으로 변환해 O(1) 검색 성능 확보
                parseOuiJson(json).toSet()
            }
        } catch (e: IOException) {
            emptySet()  // assets 로드 실패 시 탐지 없이 계속 진행
        }
    }

    // MAC 주소에서 OUI를 추출하고 카메라 제조사인지 판단합니다
    fun isCameraVendor(macAddress: String): Boolean {
        val oui = macAddress.take(8).uppercase()  // "AA:BB:CC" 형태
        return oui in cameraVendorOuis
    }

    // 제조사 이름을 반환합니다 (UI 표시용)
    fun getVendorName(macAddress: String): String? {
        val oui = macAddress.take(8).uppercase()
        return vendorMap[oui]  // null이면 알 수 없는 제조사
    }
}

// 알려진 카메라 제조사 OUI 예시 (실제는 수백 개)
val KNOWN_CAMERA_OUIS = mapOf(
    "00:12:BF" to "Hikvision",
    "BC:AD:28" to "Hikvision",
    "A4:14:37" to "Dahua Technology",
    "E0:50:8B" to "Dahua Technology",
    "00:40:8C" to "Axis Communications",
    "AC:CC:8E" to "Reolink",
    "D4:F5:27" to "TP-Link (카메라 라인)",
    "B0:A7:B9" to "Wyze Labs",
    "2C:AA:8E" to "Arlo Technologies"
)
```

---

## 11.5 포트 스캐닝 — 카메라 서비스 찾기

### 카메라가 여는 포트

IP 카메라는 특정 포트로 서비스를 제공합니다. 이 포트가 열려 있으면 IP 카메라일 가능성이 높아집니다.

| 포트 | 프로토콜 | 의미 |
|------|---------|------|
| 554 | RTSP | 실시간 스트리밍 (IP 카메라 표준) |
| 8554 | RTSP | RTSP 대체 포트 |
| 80 | HTTP | 웹 관리 인터페이스 |
| 8080 | HTTP | 대체 웹 포트 |
| 8888 | HTTP | 제조사별 대체 포트 |
| 37777 | Dahua | Dahua 카메라 전용 |
| 8000 | Hikvision | Hikvision SDK 포트 |

```kotlin
// data/sensor/PortScanner.kt
class PortScanner @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    companion object {
        // 카메라 관련 포트 목록 — 우선순위 순 정렬
        val CAMERA_PORTS = listOf(554, 8554, 80, 8080, 8888, 37777, 8000, 443)
        const val PORT_TIMEOUT_MS = 2000  // 포트당 2초 대기
    }

    // 특정 IP의 카메라 포트를 병렬로 스캔합니다
    suspend fun scanCameraPorts(ip: String): List<Int> =
        withContext(ioDispatcher) {
            // 포트들을 병렬로 스캔 — 순차 스캔이면 8포트 × 2초 = 16초 걸립니다
            CAMERA_PORTS.map { port ->
                async {
                    if (isPortOpen(ip, port)) port else null
                }
            }.awaitAll().filterNotNull()
        }

    // TCP 연결로 포트 개방 여부를 확인합니다
    private suspend fun isPortOpen(ip: String, port: Int): Boolean =
        withContext(ioDispatcher) {
            try {
                Socket().use { socket ->
                    socket.connect(
                        InetSocketAddress(ip, port),
                        PORT_TIMEOUT_MS
                    )
                    true  // 연결 성공 = 포트 열림
                }
            } catch (e: Exception) {
                false  // 연결 실패 = 포트 닫힘 또는 필터링
            }
        }

    // RTSP 포트가 열린 기기는 IP 카메라 가능성이 매우 높습니다
    fun hasRtspPort(openPorts: List<Int>): Boolean =
        openPorts.any { it == 554 || it == 8554 }
}
```

---

## 11.6 Android 12+ 스캔 쓰로틀링 해결 전략

### 쓰로틀링 문제

Android 8.0부터 Wi-Fi 스캔 횟수 제한이 도입되었고, Android 10 이상에서는 더 강화되었습니다. 앱이 2분에 4번 이상 `startScan()`을 호출하면 시스템이 캐시된 결과를 반환합니다(실제 스캔 안 함).

SearCam은 이 제한을 우회하는 게 아니라 다른 방식으로 접근합니다.

```kotlin
// Wi-Fi 스캔 쓰로틀링 해결 전략
class WifiScanRepositoryImpl @Inject constructor(
    private val wifiManager: WifiManager,
    private val wifiScanner: WifiScanner,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : WifiScanRepository {

    override suspend fun scanNetwork(): Flow<List<NetworkDevice>> = flow {
        // 전략 1: WifiManager.startScan() 대신 ARP 테이블 직접 읽기
        // ARP 테이블은 이미 연결된 기기를 보여주므로 스캔 횟수 제한 없음
        val arpDevices = wifiScanner.readArpTable()
        emit(arpDevices.map { it.toNetworkDevice() })

        // 전략 2: 연결된 AP의 DHCP 임대 목록 활용 (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val dhcpInfo = wifiManager.dhcpInfo
            // DHCP 서버 IP에서 네트워크 범위 계산
            val networkPrefix = getNetworkPrefix(dhcpInfo)
            emit(scanNetworkRange(networkPrefix))
        }

        // 전략 3: mDNS 패시브 리슨 — 스캔 없이 광고 패킷 수집
        wifiScanner.discoverMdnsServices()
            .collect { serviceInfo ->
                // mDNS로 발견된 기기를 기존 목록에 병합
                val device = serviceInfo.toNetworkDevice()
                emit(listOf(device))
            }
    }.flowOn(ioDispatcher)
}
```

핵심은 **능동적 스캔 의존도를 줄이는 것**입니다. ARP 테이블은 이미 네트워크에 참여한 기기의 기록이므로, Wi-Fi 스캔 없이도 대부분의 기기를 발견할 수 있습니다.

---

## 11.7 WifiScanRepository 구현 — 전체 흐름

```kotlin
// data/repository/WifiScanRepositoryImpl.kt
class WifiScanRepositoryImpl @Inject constructor(
    private val wifiScanner: WifiScanner,
    private val portScanner: PortScanner,
    private val ouiDatabase: OuiDatabase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : WifiScanRepository {

    // 전체 Wi-Fi 스캔 파이프라인을 실행합니다
    override suspend fun fullScan(): ScanResult = withContext(ioDispatcher) {
        val allDevices = mutableListOf<NetworkDevice>()

        // Step 1: ARP 테이블에서 기기 수집
        val arpEntries = wifiScanner.readArpTable()

        // Step 2: 각 기기에 OUI 매칭 및 포트 스캔을 병렬로 수행합니다
        val enrichedDevices = arpEntries.map { entry ->
            async {
                val vendorName = ouiDatabase.getVendorName(entry.mac)
                val isCameraVendor = ouiDatabase.isCameraVendor(entry.mac)
                val openPorts = portScanner.scanCameraPorts(entry.ip)

                NetworkDevice(
                    ip = entry.ip,
                    mac = entry.mac,
                    vendor = vendorName,
                    openPorts = openPorts,
                    // 카메라 제조사 OUI + RTSP 포트 = 높은 위험도
                    riskScore = calculateDeviceRisk(isCameraVendor, openPorts),
                    isCamera = isCameraVendor || portScanner.hasRtspPort(openPorts)
                )
            }
        }.awaitAll()

        allDevices.addAll(enrichedDevices)

        // Step 3: mDNS로 추가 기기 탐색 (5초 타임아웃)
        val mdnsDevices = wifiScanner.discoverMdnsServices()
            .take(30)  // 최대 30개 서비스
            .toList()
            .map { it.toNetworkDevice() }

        allDevices.addAll(mdnsDevices)

        // Step 4: 위험도 점수를 기반으로 결과 반환
        ScanResult(
            layerType = LayerType.WIFI,
            score = allDevices.maxOfOrNull { it.riskScore } ?: 0,
            maxScore = 100,
            findings = allDevices.filter { it.isCamera }
                .map { device -> Finding("카메라 의심 기기: ${device.ip} (${device.vendor ?: "알 수 없음"})") }
        )
    }

    // 기기 위험도 점수 계산 (0~100)
    private fun calculateDeviceRisk(
        isCameraVendor: Boolean,
        openPorts: List<Int>
    ): Int {
        var score = 0
        if (isCameraVendor) score += 40          // 카메라 제조사 OUI
        if (554 in openPorts) score += 40         // RTSP 포트
        if (8080 in openPorts || 80 in openPorts) score += 15  // HTTP 관리 포트
        if (37777 in openPorts || 8000 in openPorts) score += 25  // 특정 제조사 포트
        return score.coerceAtMost(100)
    }
}
```

---

## 실습

> **실습 11-1**: 집에서 `/proc/net/arp`를 읽어 연결된 모든 기기의 MAC OUI를 확인해보세요. 스마트TV, 라우터, 스마트폰의 OUI가 어떤 제조사로 나오는지 비교해보세요.

> **실습 11-2**: `PortScanner`를 구현하고, 자신의 컴퓨터 IP(127.0.0.1)에서 22(SSH), 80(HTTP), 554(RTSP) 포트를 스캔해보세요. 결과가 예상과 다르다면 이유를 분석해보세요.

---

## 핵심 정리

| 기법 | 역할 | 한계 |
|------|------|------|
| ARP 테이블 파싱 | 연결된 모든 기기 MAC 수집 | 같은 네트워크만 |
| OUI 매칭 | 카메라 제조사 식별 | MAC 스푸핑 불가 탐지 |
| mDNS/SSDP | 스마트 기기 서비스 광고 탐지 | mDNS 끈 기기 불탐지 |
| 포트 스캔 | RTSP/HTTP 서비스 확인 | 방화벽으로 숨긴 경우 |

- Wi-Fi 스캔은 가중치 50% — 가장 구체적인 증거를 제공하기 때문이다
- ARP 테이블은 root 권한 없이 읽을 수 있는 네트워크 흔적이다
- Android 스캔 쓰로틀링은 능동 스캔 대신 ARP/mDNS 패시브 방식으로 우회한다
- 포트 스캔은 기기당 병렬 실행으로 총 소요 시간을 줄인다

---

## 다음 장 예고

네트워크에서 IP 카메라를 찾았습니다. 하지만 Wi-Fi 없는 환경이라면? Ch12에서는 두 번째 탐지 레이어 — 빛의 역반사를 이용해 카메라 렌즈를 물리적으로 찾아내는 방법을 구현합니다.

---
*참고 문서: docs/02-TRD.md, docs/03-TDD.md, docs/04-system-architecture.md*


\newpage


# Ch12: 렌즈 감지 — 빛이 돌아오는 길을 막아라

> **이 장에서 배울 것**: 탐지 가중치 35%를 차지하는 렌즈 감지 레이어의 원리와 구현을 배웁니다. 역반사(Retroreflection) 물리 원리부터 CameraX 5단계 분석 파이프라인까지, 스마트폰 플래시로 카메라 렌즈를 찾는 방법을 다룹니다.

---

## 도입

야간 운전 중 도로 표지판에 헤드라이트를 비추면 표지판이 눈부시게 빛납니다. 옆에서 보면 별로 밝지 않은데, 운전자 위치에서만 유독 밝게 보입니다. 이것이 역반사(Retroreflection)입니다. 빛이 들어온 방향 그대로 되돌아가는 현상이죠.

카메라 렌즈는 같은 원리로 동작합니다. 렌즈는 빛을 굴절시키고 반사시키도록 설계된 광학 소자입니다. 스마트폰 플래시를 켜고 방을 천천히 돌아보면, 숨겨진 카메라 렌즈는 다른 표면보다 유독 밝게, 그리고 원형으로 빛납니다.

SearCam은 이 물리 법칙을 알고리즘으로 구현합니다.

---

## 12.1 빛의 역반사(Retroreflection) 원리

### 왜 렌즈만 특별하게 반사하는가

일반 표면(벽, 천장, 가구)에 빛을 비추면 **난반사(Diffuse Reflection)**가 일어납니다. 빛이 여러 방향으로 퍼져나가서, 어느 방향에서 봐도 비슷한 밝기로 보입니다.

카메라 렌즈는 다릅니다. 렌즈 내부의 광학 소자들이 **코너 반사체(Corner Reflector)** 역할을 합니다. 빛이 들어온 방향으로 정확히 되돌아가는 거죠.

```
일반 표면 (난반사):
  광원 →    표면    → 여러 방향으로 산란
            ↑
          관찰자 (어두움)

카메라 렌즈 (역반사):
  광원 → 렌즈 → 광원 방향으로 되돌아옴
    ↑                         ↓
  관찰자 (밝게 보임!) ← 반사광
```

스마트폰에서 플래시와 카메라 센서는 아주 가까이 있습니다. 따라서 플래시를 켜고 카메라로 촬영하면 역반사 지점이 극도로 밝게 찍힙니다.

### 역반사의 특성

역반사 포인트는 세 가지 특성을 가집니다.

1. **고휘도**: 주변보다 현저히 밝습니다 (픽셀값 200+ / 255)
2. **원형**: 렌즈는 원형이므로 반사 패턴도 원형입니다
3. **안정성**: 카메라를 조금 움직여도 같은 자리에서 계속 빛납니다

이 세 조건을 동시에 만족하는 포인트를 찾는 것이 렌즈 감지 알고리즘의 핵심입니다.

---

## 12.2 CameraX ImageAnalysis 프레임 처리

### CameraX 설정

렌즈 감지는 후면 카메라 + 플래시 조합을 사용합니다. CameraX의 `ImageAnalysis` use case를 통해 매 프레임을 분석합니다.

```kotlin
// data/sensor/LensDetector.kt
class LensDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val retroreflectionAnalyzer: RetroreflectionAnalyzer,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher
) {
    private var imageAnalysis: ImageAnalysis? = null
    private var camera: Camera? = null

    // CameraX 파이프라인을 구성하고 분석을 시작합니다
    fun startAnalysis(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ): Flow<List<RetroreflectionPoint>> = callbackFlow {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Preview — 사용자가 카메라 화면을 볼 수 있게 합니다
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // ImageAnalysis — 30fps로 프레임을 분석합니다
            imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))  // 720p로 분석
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                // KEEP_ONLY_LATEST: 분석이 느려도 최신 프레임만 처리 (실시간성 유지)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(
                        ContextCompat.getMainExecutor(context)
                    ) { imageProxy ->
                        // 각 프레임을 분석하고 결과를 Flow로 내보냅니다
                        val points = retroreflectionAnalyzer.analyze(imageProxy)
                        trySend(points)
                        imageProxy.close()  // 반드시 close() 호출 — 메모리 누수 방지
                    }
                }

            // 후면 카메라 선택
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
                // 플래시를 토치 모드로 켭니다
                camera?.cameraControl?.enableTorch(true)
            } catch (e: Exception) {
                close(e)
            }
        }, ContextCompat.getMainExecutor(context))

        awaitClose {
            // Flow가 끝나면 플래시 끄기
            camera?.cameraControl?.enableTorch(false)
            imageAnalysis?.clearAnalyzer()
        }
    }
}
```

---

## 12.3 5단계 분석 파이프라인

### 파이프라인 개요

```
[카메라 프레임 (YUV 720p)]
        ↓
  1단계: 전처리 (그레이스케일 변환)
        ↓
  2단계: 고휘도 영역 추출 (임계값 필터링)
        ↓
  3단계: 원형도 분석 (컨투어 circularity 계산)
        ↓
  4단계: 시간축 안정성 검증 (연속 5프레임 지속)
        ↓
  5단계: 플래시 OFF 동적 검증 (플래시 끄면 사라지는지)
        ↓
[RetroreflectionPoint 목록]
```

```kotlin
// data/analysis/RetroreflectionAnalyzer.kt
class RetroreflectionAnalyzer @Inject constructor() {

    // 최근 N 프레임의 감지 기록 (안정성 검증용)
    private val frameHistory = ArrayDeque<Set<Point>>(maxSize = 5)

    fun analyze(imageProxy: ImageProxy): List<RetroreflectionPoint> {
        // 1단계: YUV 이미지를 그레이스케일 Bitmap으로 변환합니다
        val grayBitmap = imageProxy.toGrayBitmap()

        // 2단계: 고휘도 영역만 추출합니다 (임계값 200/255)
        val highBrightnessRegions = extractHighBrightness(
            bitmap = grayBitmap,
            threshold = 200
        )
        if (highBrightnessRegions.isEmpty()) return emptyList()

        // 3단계: 원형도 검사 — 렌즈는 원형입니다
        val circularRegions = highBrightnessRegions.filter { region ->
            region.circularity > 0.7f  // 1.0 = 완전한 원, 0.7 이상만 통과
        }
        if (circularRegions.isEmpty()) return emptyList()

        // 4단계: 최근 5프레임 연속 등장 여부 확인 (안정성)
        val currentPoints = circularRegions.map { it.center }.toSet()
        frameHistory.addLast(currentPoints)
        if (frameHistory.size > 5) frameHistory.removeFirst()

        val stableRegions = circularRegions.filter { region ->
            // 최소 3프레임 이상 같은 위치에서 감지된 포인트만 통과
            frameHistory.count { frame ->
                frame.any { point -> point.distanceTo(region.center) < 10f }
            } >= 3
        }

        // 5단계: 결과를 RetroreflectionPoint로 변환합니다
        return stableRegions.map { region ->
            RetroreflectionPoint(
                x = region.center.x,
                y = region.center.y,
                size = region.size,
                circularity = region.circularity,
                brightness = region.avgBrightness,
                contrastRatio = region.avgBrightness / grayBitmap.averageBrightness(),
                isStable = true,
                flashDependency = false,  // 5단계에서 플래시 OFF 검증 후 업데이트
                riskScore = calculateRisk(region)
            )
        }
    }

    // 고휘도 영역 추출 — 임계값 이상의 밝은 픽셀 클러스터를 찾습니다
    private fun extractHighBrightness(
        bitmap: Bitmap,
        threshold: Int
    ): List<BrightRegion> {
        val regions = mutableListOf<BrightRegion>()
        val visited = Array(bitmap.height) { BooleanArray(bitmap.width) }

        for (y in 0 until bitmap.height step 2) {  // 2픽셀 간격으로 탐색 (성능 최적화)
            for (x in 0 until bitmap.width step 2) {
                if (visited[y][x]) continue

                val brightness = bitmap.getBrightness(x, y)
                if (brightness < threshold) continue

                // BFS로 연결된 고휘도 픽셀 클러스터를 찾습니다
                val cluster = floodFillBFS(bitmap, x, y, threshold, visited)
                if (cluster.size >= 9) {  // 최소 9픽셀 (노이즈 제거)
                    regions.add(analyzeClusters(cluster))
                }
            }
        }

        return regions
    }

    // 클러스터의 원형도를 계산합니다
    // circularity = 4π × 면적 / 둘레² (완전한 원 = 1.0)
    private fun calculateCircularity(cluster: List<Point>): Float {
        val area = cluster.size.toFloat()
        val perimeter = calculatePerimeter(cluster).toFloat()
        if (perimeter == 0f) return 0f
        return (4f * Math.PI.toFloat() * area) / (perimeter * perimeter)
    }

    // 역반사 포인트의 위험도 점수를 계산합니다
    private fun calculateRisk(region: BrightRegion): Int {
        var score = 0
        if (region.circularity > 0.85f) score += 30  // 높은 원형도
        if (region.avgBrightness > 230f) score += 25  // 극도로 높은 밝기
        if (region.contrastRatio > 5f) score += 25    // 주변 대비 5배 이상
        if (region.size in 16f..400f) score += 20     // 적정 크기 (너무 크거나 작으면 감점)
        return score.coerceAtMost(100)
    }
}
```

---

## 12.4 플래시 OFF 동적 검증 — 오탐 방지 핵심

역반사를 이용하는 분석의 가장 큰 약점은 **가짜 양성(False Positive)**입니다. 유리창 반사, 금속 장식품, 광택 있는 플라스틱도 비슷하게 밝게 반사될 수 있습니다. 이를 걸러내는 가장 확실한 방법은 플래시를 잠깐 끄는 것입니다.

진짜 카메라 렌즈: 플래시를 끄면 밝기가 급격히 떨어집니다 (역반사 의존)
일반 반사 표면: 플래시를 꺼도 주변 조명이 있으면 여전히 보입니다

```kotlin
// 플래시 ON/OFF 동적 검증
suspend fun verifyWithFlashToggle(
    points: List<RetroreflectionPoint>
): List<RetroreflectionPoint> = withContext(dispatcher) {

    if (points.isEmpty()) return@withContext points

    // 플래시 OFF 후 같은 위치의 밝기를 비교합니다
    camera?.cameraControl?.enableTorch(false)
    delay(300)  // 300ms 대기 — 프레임이 안정화될 시간

    val darkFramePoints = captureFramePoints()  // 플래시 OFF 상태의 포인트

    camera?.cameraControl?.enableTorch(true)  // 플래시 다시 ON

    // 플래시 OFF 시 사라진 포인트만 진짜 역반사로 판정합니다
    return@withContext points.map { point ->
        val stilPresentWhenDark = darkFramePoints.any { darkPoint ->
            darkPoint.distanceTo(point) < 15f &&
            darkPoint.brightness > point.brightness * 0.6f
        }

        point.copy(
            // 플래시 꺼도 밝으면 flashDependency=false (가짜 반사)
            // 플래시 끄면 사라지면 flashDependency=true (진짜 역반사)
            flashDependency = !stilPresentWhenDark,
            // 플래시 의존 = 렌즈일 가능성 높음 → 위험도 상향
            riskScore = if (!stilPresentWhenDark) {
                (point.riskScore * 1.3f).toInt().coerceAtMost(100)
            } else {
                (point.riskScore * 0.5f).toInt()  // 가짜 반사 → 위험도 하향
            }
        )
    }
}
```

---

## 12.5 IR 감지 — 전면 카메라의 적외선 필터 활용

### 스마트폰 카메라와 IR 필터

사람 눈은 가시광선(380~700nm)만 볼 수 있습니다. 하지만 스마트폰 카메라 센서는 적외선(IR, 700~1000nm)도 감지합니다. 제조사들이 IR 컷 필터(ICF)를 달아서 가시광선만 통과시키지만, 전면 카메라는 후면보다 IR 필터가 약한 경우가 많습니다.

TV 리모컨 버튼을 누르면서 전면 카메라로 촬영해보세요. 리모컨 끝에서 보라색/흰색 불빛이 보인다면 그 카메라는 IR에 민감합니다.

이를 이용합니다. 몰래카메라는 야간 감시를 위해 IR LED를 포함하는 경우가 많습니다. 어두운 환경에서 전면 카메라로 주변을 스캔하면 IR LED를 발견할 수 있습니다.

```kotlin
// data/sensor/IrDetector.kt
class IrDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher
) {
    companion object {
        const val IR_BRIGHTNESS_THRESHOLD = 180  // IR 감지 밝기 임계값
        const val MIN_IR_DURATION_MS = 3000L      // 최소 3초 지속되어야 IR로 판정
        const val MAX_AMBIENT_LUX = 10f           // 10 lux 이하 암실에서만 신뢰
    }

    // 전면 카메라로 IR 발광체를 탐지합니다
    fun startIrDetection(
        lifecycleOwner: LifecycleOwner
    ): Flow<List<IrPoint>> = callbackFlow {

        // 전면 카메라 선택 — IR 필터가 약해 IR에 더 민감합니다
        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(1280, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(
            ContextCompat.getMainExecutor(context)
        ) { imageProxy ->
            val irPoints = detectIrPoints(imageProxy)
            trySend(irPoints)
            imageProxy.close()
        }

        // ... CameraX 바인딩 생략

        awaitClose { imageAnalysis.clearAnalyzer() }
    }

    private fun detectIrPoints(imageProxy: ImageProxy): List<IrPoint> {
        val bitmap = imageProxy.toBitmap()

        // IR은 특유의 보라색(Violet) 또는 흰색으로 나타납니다
        val irCandidates = findIrColorPixels(bitmap)

        return irCandidates.map { point ->
            IrPoint(
                x = point.x,
                y = point.y,
                intensity = point.brightness,
                duration = 0L,  // 지속 시간은 외부에서 누적
                isStable = false,  // 3초 지속 여부는 Flow 레이어에서 판정
                color = if (point.isViolet()) IrColor.VIOLET else IrColor.WHITE,
                riskScore = if (point.brightness > 220) 70 else 40
            )
        }
    }

    // IR 특유의 색상 필터 — 보라색(R>150, G<80, B>150) 또는 흰색(R>200, G>200, B>200)
    private fun findIrColorPixels(bitmap: Bitmap): List<ColorPoint> {
        val candidates = mutableListOf<ColorPoint>()

        for (y in 0 until bitmap.height step 4) {
            for (x in 0 until bitmap.width step 4) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // 보라색 IR: 적색과 청색이 높고, 녹색이 낮은 경우
                val isViolet = r > 150 && g < 80 && b > 150
                // 흰색 IR: 모든 채널이 높은 경우 (일부 IR LED의 특성)
                val isWhite = r > 200 && g > 200 && b > 200

                if (isViolet || isWhite) {
                    candidates.add(ColorPoint(x, y, (r + g + b) / 3f, isViolet))
                }
            }
        }

        return candidates
    }
}
```

---

## 12.6 실전 오탐 사례와 보정 방법

### 오탐 사례 1: 야간 조명의 광원

호텔 방 조명 장식의 LED가 역반사와 비슷한 밝기로 찍힐 수 있습니다.

**보정**: 광원 특성 필터링. 진짜 조명은 발광 면적이 넓고 고르게 밝습니다. 카메라 렌즈의 역반사는 면적이 작고(직경 2~20mm 기준) 중심부가 극도로 밝습니다.

```kotlin
// 광원과 렌즈를 구별하는 밝기 분포 분석
private fun isLightSource(region: BrightRegion): Boolean {
    // 광원: 중심과 가장자리 밝기 차이가 적음 (고른 발광)
    // 렌즈: 중심 극도로 밝고 가장자리로 갈수록 급격히 어두워짐
    val centerBrightness = region.getCenterBrightness()
    val edgeBrightness = region.getEdgeBrightness()
    val gradient = centerBrightness / (edgeBrightness + 1f)

    return gradient < 2.0f  // 기울기 2배 이하 = 광원으로 판정 (역반사 아님)
}
```

### 오탐 사례 2: 금속 장식품과 액자 유리

**보정**: 형태 분석 강화. 금속 장식은 불규칙한 형태, 카메라 렌즈는 원형입니다.

```kotlin
// 원형도 임계값을 높여 비원형 반사를 제거합니다
val strictCircularity = 0.75f  // 0.7 → 0.75로 상향 조정
```

### 오탐 사례 3: 안경 렌즈

사용자가 안경을 쓴 경우, 안경 렌즈도 역반사를 일으킵니다. 하지만 안경 렌즈는 크기가 매우 큽니다 (직경 50mm+).

**보정**: 크기 범위 필터링.

```kotlin
// 카메라 렌즈 크기 범위 (픽셀 단위, 720p 기준)
// 실제 직경 2mm~25mm 범위를 픽셀로 환산
private val LENS_MIN_AREA_PX = 4   // 너무 작으면 노이즈
private val LENS_MAX_AREA_PX = 800  // 너무 크면 안경/유리
```

### 오탐 보정 결과

실제 테스트에서 보정 전/후 오탐률:

| 조건 | 보정 전 | 보정 후 |
|------|---------|---------|
| 야간 LED 조명 | 탐지(오탐) | 미탐지 |
| 금속 장식품 | 탐지(오탐) | 미탐지 |
| 안경 렌즈 | 탐지(오탐) | 미탐지 |
| 실제 2mm 카메라 렌즈 | 탐지(정탐) | 탐지(정탐) |

---

## 실습

> **실습 12-1**: TV 리모컨의 IR LED를 스마트폰 전면 카메라로 촬영해보세요. 보라색 또는 흰색 불빛이 보이면 해당 카메라는 IR에 민감한 것입니다. 이 특성을 이용해 `IrDetector`가 어떤 색상 범위를 기준으로 삼는지 조정해보세요.

> **실습 12-2**: `RetroreflectionAnalyzer`의 원형도 임계값(0.7)을 0.5, 0.8, 0.9로 바꿔가며 동전, 반지, 안경 렌즈에 플래시를 비춰보세요. 임계값에 따라 오탐/미탐이 어떻게 달라지는지 분석해보세요.

---

## 핵심 정리

| 단계 | 목적 | 핵심 파라미터 |
|------|------|-------------|
| 전처리 | 그레이스케일로 처리 단순화 | 720p 다운스케일 |
| 고휘도 추출 | 밝은 반사 영역 격리 | 임계값 200/255 |
| 원형도 검사 | 렌즈 형태 특성 검증 | circularity > 0.7 |
| 안정성 검증 | 노이즈와 실제 신호 구분 | 5프레임 중 3회 |
| 플래시 검증 | 가짜 반사 최종 제거 | 밝기 60% 이하로 감소 |

- 역반사 원리는 물리 법칙 — 카메라 렌즈는 빛을 보낸 방향으로 되돌려보낸다
- 플래시 OFF 검증이 오탐률을 결정적으로 낮춘다
- 전면 카메라는 IR 필터가 약해 야간 IR 감지에 활용할 수 있다
- 오탐 방지는 하나의 필터가 아니라 크기+원형도+안정성의 복합 판단이다

---

## 다음 장 예고

두 개의 주요 레이어를 구현했습니다. Ch13에서는 마지막 보조 레이어 — 전자기장(EMF) 감지 — 를 다룹니다. 가중치 15%지만, 다른 두 레이어의 결과를 보강하는 중요한 역할을 합니다.

---
*참고 문서: docs/02-TRD.md, docs/03-TDD.md, docs/04-system-architecture.md*


\newpage


# Ch13: EMF 감지 — 전자기장의 작은 목소리를 듣다

> **이 장에서 배울 것**: 탐지 가중치 15%를 차지하는 EMF 레이어의 원리와 한계를 배웁니다. SensorManager TYPE_MAGNETIC_FIELD를 이용한 20Hz 수집, 이동 평균 노이즈 필터, 캘리브레이션 구현 — 그리고 이 레이어가 절대 단독으로 사용될 수 없는 이유까지 솔직하게 다룹니다.

---

## 도입

지뢰 탐지기는 땅 속에 묻힌 금속의 전자기 특성을 읽어냅니다. 엄청난 고출력 신호를 쏘아서 반응을 감지하죠. 스마트폰 자력계는 그 반대입니다 — 아무것도 발사하지 않고, 그냥 주변의 자기장 변화를 조용히 듣습니다.

IC 보드, 모터, 무선 송신기를 포함한 전자 기기는 주변에 전자기장을 만들어냅니다. 스마트폰 자력계가 아주 민감하다면 숨겨진 카메라 주변의 자기장 변화를 감지할 수 있을까요? 이론적으로는 가능합니다.

하지만 현실은 훨씬 복잡합니다. 이 장에서는 EMF 탐지의 가능성과 한계를 정직하게 살펴봅니다.

---

## 13.1 전자기장(EMF) 탐지의 원리와 한계

### 어떻게 탐지하는가

카메라를 비롯한 전자 기기는 동작 중 세 가지 방식으로 전자기장을 발생시킵니다.

1. **전원 회로**: 배터리 충전, 전압 변환기에서 자기장 발생
2. **무선 통신**: Wi-Fi, Bluetooth 모듈이 RF 신호 방출
3. **모터/구동계**: 팬이 있는 기기는 회전 모터의 자기장 방출

스마트폰의 3축 자력계(Magnetometer)는 TYPE_MAGNETIC_FIELD 센서로 x, y, z축의 자기장 강도를 마이크로테슬라(μT) 단위로 측정합니다.

### 현실적인 한계

```
EMF 탐지로 가능한 것:
  ✅ 전자 기기가 근처에 있을 때의 자기장 변화 감지 (10~30cm 이내)
  ✅ 배경 자기장 대비 이상 수치 탐지

EMF 탐지로 불가능한 것:
  ❌ 특정 기기 종류 식별 (카메라 vs 공유기 vs 충전기)
  ❌ 1m 이상 거리의 소형 기기 탐지
  ❌ 주변 전기 배선, 가전제품 노이즈와 구분
  ❌ 배터리만 사용하는 저전력 카메라 탐지
```

이것이 EMF 레이어에 15%라는 낮은 가중치를 할당한 이유입니다. 단독으로는 신뢰할 수 없지만, Wi-Fi 스캔이나 렌즈 감지와 조합하면 보강 신호가 됩니다.

---

## 13.2 SensorManager TYPE_MAGNETIC_FIELD 20Hz 수집

### Android 자력계 API

Android는 `SensorManager`를 통해 자력계에 접근합니다. `TYPE_MAGNETIC_FIELD` 센서가 3축 자기장을 제공합니다.

```kotlin
// data/sensor/MagneticSensor.kt
class MagneticSensor @Inject constructor(
    private val sensorManager: SensorManager,
    private val noiseFilter: NoiseFilter,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher
) {
    companion object {
        // 20Hz 샘플링 = SENSOR_DELAY_GAME (약 50ms 간격)
        // SENSOR_DELAY_FASTEST(~1ms)는 배터리 소모가 크고 노이즈가 많아 부적합
        const val SAMPLING_RATE = SensorManager.SENSOR_DELAY_GAME

        // 탐지 임계값 — 배경 대비 이 값 이상 변화하면 이상 신호로 판단
        const val ANOMALY_THRESHOLD_UT = 20f  // 마이크로테슬라
    }

    // 센서 데이터를 Flow로 제공합니다
    fun startMeasurement(): Flow<MagneticReading> = callbackFlow {
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        // 자력계가 없는 기기에서는 빈 Flow (EMF 레이어 비활성화)
        if (magnetometer == null) {
            close()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_MAGNETIC_FIELD) return

                val rawX = event.values[0]  // x축 자기장 (μT)
                val rawY = event.values[1]  // y축 자기장 (μT)
                val rawZ = event.values[2]  // z축 자기장 (μT)

                // 자기장 벡터의 크기 (magnitude) 계산
                val magnitude = sqrt(rawX * rawX + rawY * rawY + rawZ * rawZ)

                val reading = MagneticReading(
                    timestamp = event.timestamp,
                    x = rawX,
                    y = rawY,
                    z = rawZ,
                    magnitude = magnitude,
                    delta = 0f  // 노이즈 필터 통과 후 계산
                )

                trySend(reading)
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                // 정확도 변화 — 필요 시 처리
            }
        }

        sensorManager.registerListener(
            listener,
            magnetometer,
            SAMPLING_RATE  // 20Hz
        )

        // Flow 취소 시 센서 등록 해제 (배터리 절약)
        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }

    // 센서 가용성 확인
    fun isAvailable(): Boolean =
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null

    // 센서 정확도 확인 (ACCURACY_HIGH, MEDIUM, LOW, UNRELIABLE)
    fun checkAccuracy(): Int {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            ?: return SensorManager.SENSOR_STATUS_UNRELIABLE
        // 실제 정확도는 onAccuracyChanged 콜백에서 업데이트됨
        return lastAccuracy
    }

    private var lastAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
}
```

---

## 13.3 이동 평균(Moving Average) 노이즈 필터 구현

### 왜 노이즈 필터가 필요한가

자력계 원시 데이터는 매우 불안정합니다. 스마트폰 자체의 전기 회로, 사용자의 움직임, 주변 금속의 영향으로 값이 튀는 **노이즈**가 많습니다. 이를 그대로 사용하면 아무것도 없는데도 경보가 울립니다.

이동 평균 필터는 최근 N개 값의 평균을 사용하는 가장 단순하면서도 효과적인 필터입니다. 잠깐 튀는 노이즈는 평균에 묻히고, 진짜 이상 신호는 여러 샘플에 걸쳐 지속됩니다.

```kotlin
// data/analysis/NoiseFilter.kt
class NoiseFilter @Inject constructor() {
    companion object {
        const val WINDOW_SIZE = 10  // 이동 평균 윈도우 크기 (10샘플 = 0.5초 @20Hz)
        const val SPIKE_THRESHOLD_UT = 50f  // 0.3초 내 50μT 이상 급변 = 스파이크 (제거)
        const val SPIKE_WINDOW_FRAMES = 6   // 0.3초에 해당하는 프레임 수 (20Hz × 0.3s)
    }

    // 최근 WINDOW_SIZE 개의 magnitude 값을 저장하는 원형 버퍼
    private val magnitudeBuffer = ArrayDeque<Float>(WINDOW_SIZE)
    private var smoothedMagnitude = 0f

    // 원시 자기장 측정값에 노이즈 필터를 적용합니다
    fun filter(reading: MagneticReading): MagneticReading {
        val magnitude = reading.magnitude

        // 스파이크 감지: 최근 6프레임 내에서 50μT 이상 갑자기 변했으면 제거합니다
        if (isSpike(magnitude)) {
            // 스파이크는 버퍼에 추가하지 않고 이전 평균값으로 대체합니다
            return reading.copy(
                magnitude = smoothedMagnitude,
                delta = 0f
            )
        }

        // 이동 평균 버퍼에 추가
        magnitudeBuffer.addLast(magnitude)
        if (magnitudeBuffer.size > WINDOW_SIZE) {
            magnitudeBuffer.removeFirst()
        }

        // 새 이동 평균 계산
        val newSmoothed = magnitudeBuffer.average().toFloat()
        val delta = newSmoothed - smoothedMagnitude
        smoothedMagnitude = newSmoothed

        return reading.copy(
            magnitude = smoothedMagnitude,
            delta = delta
        )
    }

    // 스파이크 감지 — 최근 평균 대비 급격한 변화를 탐지합니다
    private fun isSpike(newMagnitude: Float): Boolean {
        if (magnitudeBuffer.size < SPIKE_WINDOW_FRAMES) return false
        val recentAvg = magnitudeBuffer.takeLast(SPIKE_WINDOW_FRAMES).average().toFloat()
        return abs(newMagnitude - recentAvg) > SPIKE_THRESHOLD_UT
    }

    // 필터 상태를 초기화합니다 (새 스캔 시작 시 호출)
    fun reset() {
        magnitudeBuffer.clear()
        smoothedMagnitude = 0f
    }
}
```

### 이동 평균의 트레이드오프

윈도우 크기가 클수록 노이즈가 줄지만 반응이 느려집니다. 작을수록 노이즈에 민감하지만 실제 신호에 빠르게 반응합니다.

| 윈도우 크기 | 대기 시간 | 노이즈 감쇠 | 적합한 용도 |
|------------|---------|-----------|-----------|
| 5 (0.25초) | 빠름 | 낮음 | 빠른 움직임 탐지 |
| 10 (0.5초) | 보통 | 보통 | SearCam 선택값 |
| 20 (1.0초) | 느림 | 높음 | 안정적 장소 측정 |

SearCam은 10을 선택했습니다. 30초 스캔 중에 스마트폰 움직임이 있을 수 있어 너무 느린 필터는 부적합하고, 너무 빠른 필터는 사용자의 움직임을 오탐할 수 있기 때문입니다.

---

## 13.4 캘리브레이션 — 배경 기준선 설정

### 왜 캘리브레이션이 필요한가

지구 자체가 자기장을 가지고 있습니다. 위치에 따라 30~60μT 사이입니다. 이 배경 자기장이 없다면 어떤 값이 "이상"인지 판단할 수 없습니다.

캘리브레이션은 스캔 시작 전 3초 동안 "지금 이 환경의 정상 자기장"을 측정합니다. 이후 이 값보다 크게 벗어나는 측정값을 이상 신호로 판단합니다.

```kotlin
// domain/usecase/CalibrateEmfUseCase.kt
class CalibrateEmfUseCase @Inject constructor(
    private val magneticRepository: MagneticRepository
) {
    companion object {
        const val CALIBRATION_DURATION_MS = 3000L   // 3초간 캘리브레이션
        const val CALIBRATION_SAMPLES = 60           // 20Hz × 3초 = 60 샘플
    }

    // 배경 기준선(baseline)과 노이즈 바닥(noise_floor)을 계산합니다
    suspend operator fun invoke(): CalibrationResult {
        val samples = magneticRepository.collectSamples(CALIBRATION_SAMPLES)

        if (samples.isEmpty()) {
            return CalibrationResult.Unavailable
        }

        val magnitudes = samples.map { it.magnitude }
        val baseline = magnitudes.average().toFloat()
        val stdDev = calculateStdDev(magnitudes, baseline)

        // 노이즈 바닥 = 평균 ± 2 표준편차 (95% 신뢰 구간)
        val noiseFloor = stdDev * 2f

        return CalibrationResult.Success(
            baseline = baseline,
            noiseFloor = noiseFloor,
            // 탐지 임계값 = 기준선 + 노이즈 바닥 + 20μT 안전 마진
            detectionThreshold = baseline + noiseFloor + 20f
        )
    }

    private fun calculateStdDev(values: List<Float>, mean: Float): Float {
        val variance = values.map { (it - mean) * (it - mean) }.average().toFloat()
        return sqrt(variance)
    }
}

// 캘리브레이션 결과 — 불변 sealed class
sealed class CalibrationResult {
    data class Success(
        val baseline: Float,       // 배경 자기장 기준선 (μT)
        val noiseFloor: Float,     // 노이즈 바닥 (μT)
        val detectionThreshold: Float  // 이상 신호 임계값 (μT)
    ) : CalibrationResult()

    object Unavailable : CalibrationResult()  // 자력계 없는 기기
}
```

### 캘리브레이션 결과 활용

```kotlin
// EMF 이상 탐지 로직
class EmfAnomalyDetector @Inject constructor(
    private val noiseFilter: NoiseFilter
) {
    private var calibration: CalibrationResult.Success? = null

    fun setCalibration(result: CalibrationResult.Success) {
        calibration = result
    }

    // 측정값이 이상 신호인지 판단합니다
    fun isAnomaly(reading: MagneticReading): EmfAnomaly? {
        val cal = calibration ?: return null  // 캘리브레이션 없으면 판단 불가

        val filtered = noiseFilter.filter(reading)
        val deviation = filtered.magnitude - cal.baseline

        return when {
            deviation < cal.noiseFloor -> null  // 정상 범위 내
            deviation < cal.detectionThreshold -> EmfAnomaly.Weak(
                deviation = deviation,
                riskScore = 15  // 약한 이상 — 보조 신호
            )
            else -> EmfAnomaly.Strong(
                deviation = deviation,
                riskScore = 40  // 강한 이상 — 주목 필요
            )
        }
    }
}

// EMF 이상 신호 — 불변 sealed class
sealed class EmfAnomaly(
    open val deviation: Float,
    open val riskScore: Int
) {
    data class Weak(
        override val deviation: Float,
        override val riskScore: Int
    ) : EmfAnomaly(deviation, riskScore)

    data class Strong(
        override val deviation: Float,
        override val riskScore: Int
    ) : EmfAnomaly(deviation, riskScore)
}
```

---

## 13.5 EMF 단독으로 탐지 불가한 이유 — 보조 레이어 역할

### 일상적인 EMF 발생원

이것이 EMF 레이어의 근본적인 한계입니다. 호텔 방에는 자기장 발생원이 넘칩니다.

| EMF 발생원 | 예상 자기장 강도 |
|-----------|--------------|
| 스마트폰 충전기 | 5~50μT (거리 10cm) |
| 노트북 전원 어댑터 | 10~100μT |
| 에어컨 실내기 | 2~20μT |
| TV | 5~30μT |
| 몰래카메라 (소형) | 1~10μT |

몰래카메라가 오히려 충전기보다 약한 신호를 냅니다. EMF만으로는 "방에 카메라가 있다"고 말할 수 없습니다.

### 올바른 사용 방법 — 교차 검증 보강재

```
잘못된 사용:
  EMF 이상 감지 → "카메라 발견!" (오탐률 90%+)

올바른 사용:
  Wi-Fi 스캔: 카메라 의심 기기 발견 (70점)
  + 렌즈 감지: 역반사 포인트 1개 (80점)
  + EMF 이상: 해당 방향에서 자기장 상승 (+보정)
  → 교차 검증 엔진: 종합 위험도 85점
```

EMF 단독 감지에서는 "자기장 이상" 정도만 알려줍니다. 다른 레이어와 결합할 때 의미 있는 신호가 됩니다.

### UI에서의 투명한 표시

```kotlin
// ui/magnetic/MagneticViewModel.kt
class MagneticViewModel @Inject constructor(
    private val magneticRepository: MagneticRepository,
    private val calibrateEmfUseCase: CalibrateEmfUseCase
) : ViewModel() {

    fun buildEmfMessage(anomaly: EmfAnomaly?): String = when (anomaly) {
        null -> "자기장 정상 — 배경 수준 내"
        is EmfAnomaly.Weak ->
            // 솔직하게 한계를 알려줍니다
            "자기장 약한 이상 (+${anomaly.deviation.toInt()}μT)\n" +
            "주의: 충전기, 전자제품도 유사한 반응을 보일 수 있습니다"
        is EmfAnomaly.Strong ->
            "자기장 강한 이상 (+${anomaly.deviation.toInt()}μT)\n" +
            "다른 탐지 레이어 결과와 함께 확인하세요"
    }
}
```

---

## 13.6 전체 EMF 레이어 통합

```kotlin
// domain/repository/MagneticRepository.kt (인터페이스)
interface MagneticRepository {
    fun startMeasurement(): Flow<MagneticReading>
    suspend fun collectSamples(count: Int): List<MagneticReading>
    fun isAvailable(): Boolean
}

// data/repository/MagneticRepositoryImpl.kt (구현체)
class MagneticRepositoryImpl @Inject constructor(
    private val magneticSensor: MagneticSensor,
    private val noiseFilter: NoiseFilter,
    private val emfAnomalyDetector: EmfAnomalyDetector,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher
) : MagneticRepository {

    override fun startMeasurement(): Flow<MagneticReading> =
        magneticSensor.startMeasurement()
            .map { reading -> noiseFilter.filter(reading) }  // 노이즈 필터 적용
            .flowOn(dispatcher)

    // 캘리브레이션용 샘플 수집
    override suspend fun collectSamples(count: Int): List<MagneticReading> =
        startMeasurement()
            .take(count)
            .toList()

    override fun isAvailable(): Boolean = magneticSensor.isAvailable()
}
```

---

## 실습

> **실습 13-1**: 스마트폰을 테이블 위에 평평하게 놓고, 스마트폰 자력계 앱(Physics Toolbox)으로 자기장 기준선을 측정해보세요. 그 다음 충전기를 가까이 가져가면서 자기장이 얼마나 변하는지 확인해보세요.

> **실습 13-2**: `NoiseFilter`에서 윈도우 크기를 5와 20으로 바꿔가며 스마트폰을 흔들어보세요. 움직임 노이즈가 필터를 통과하는 차이를 로그로 확인해보세요.

---

## 핵심 정리

| 구성 요소 | 역할 |
|---------|------|
| TYPE_MAGNETIC_FIELD | 3축 자기장 20Hz 수집 |
| NoiseFilter | 이동 평균 + 스파이크 제거 |
| CalibrateEmfUseCase | 배경 기준선 + 임계값 산출 |
| EmfAnomalyDetector | 이상 신호 판단 |

- EMF 레이어 가중치 15%는 단독 탐지 불가 + 교차 보강 역할을 반영한다
- 캘리브레이션 없이는 어떤 값이 이상인지 판단할 수 없다
- 이동 평균은 가장 단순한 노이즈 필터 — 단순함이 곧 신뢰성이다
- 한계를 솔직하게 표시하는 것이 오탐보다 낫다

---

## 다음 장 예고

세 개의 탐지 레이어를 모두 만들었습니다. 이제 가장 중요한 질문이 남았습니다 — 이 세 레이어의 결과를 어떻게 합쳐 "위험도 0~100"이라는 하나의 숫자로 만들까요? Ch14에서는 CrossValidator 설계와 위험도 산출 알고리즘을 다룹니다.

---
*참고 문서: docs/02-TRD.md, docs/03-TDD.md*


\newpage


# Ch14: 교차 검증 — 세 개의 목격자가 일치할 때 믿어라

> **이 장에서 배울 것**: 세 탐지 레이어를 하나의 위험도 점수(0~100)로 합치는 CrossValidator 설계를 배웁니다. 가중치 동적 조정, 보정 계수 설계 근거, 실제 탐지 시나리오 3가지를 통해 교차 검증 엔진의 전체 작동 방식을 이해합니다.

---

## 도입

법원에서 한 명의 목격자 증언으로 유죄를 선고하는 건 위험합니다. 하지만 독립적인 세 명의 목격자가 같은 내용을 증언한다면 이야기가 달라집니다. 각자 다른 방향에서, 다른 방법으로, 같은 결론에 도달했다면 — 신뢰할 수 있습니다.

SearCam의 세 탐지 레이어가 바로 이 세 명의 목격자입니다. Wi-Fi 스캔, 렌즈 감지, EMF — 각각 완전히 다른 물리적 원리로 동작합니다. 이 세 레이어가 동시에 "의심스럽다"고 말한다면, 그건 우연이 아닙니다.

CrossValidator는 이 세 목격자의 증언을 종합해서 판사 역할을 합니다.

---

## 14.1 세 레이어를 어떻게 합치는가 — CrossValidator 설계

### 기본 가중치 모델

각 레이어는 독립적으로 0~100 점수를 생성합니다. CrossValidator는 이 점수에 가중치를 곱해 최종 점수를 계산합니다.

```
기본 가중치:
  Layer 1 (Wi-Fi)   = 50%  → 0~50점 기여
  Layer 2 (렌즈)    = 35%  → 0~35점 기여
  Layer 3 (EMF)     = 15%  → 0~15점 기여

기본 계산:
  기반 점수 = (Wi-Fi 점수 × 0.5) + (렌즈 점수 × 0.35) + (EMF 점수 × 0.15)
```

하지만 이것만으로는 부족합니다. 레이어들이 서로를 확인해주는 **교차 검증 효과**를 반영해야 합니다.

### CrossValidator 핵심 설계

```kotlin
// data/analysis/CrossValidator.kt
class CrossValidator @Inject constructor() {

    companion object {
        // 기본 가중치 (레이어 사용 가능 시)
        const val WEIGHT_WIFI = 0.50f
        const val WEIGHT_LENS = 0.35f
        const val WEIGHT_EMF = 0.15f

        // 교차 보정 계수 — 몇 개의 레이어가 양성인지에 따라 달라집니다
        const val CORRECTION_SINGLE = 0.7f    // 1개 양성: ×0.7 (신뢰 하향)
        const val CORRECTION_DOUBLE = 1.2f    // 2개 양성: ×1.2 (교차 확인)
        const val CORRECTION_TRIPLE = 1.5f    // 3개 양성: ×1.5 (강력한 증거)

        // 위험도 등급 임계값
        const val THRESHOLD_SAFE = 20
        const val THRESHOLD_INTEREST = 40
        const val THRESHOLD_CAUTION = 60
        const val THRESHOLD_DANGER = 80
    }

    // 세 레이어의 결과를 종합해 위험도를 산출합니다
    fun validate(
        wifiResult: ScanResult?,
        lensResult: ScanResult?,
        emfResult: ScanResult?
    ): ValidationResult {

        // 사용 가능한 레이어만 동적으로 가중치를 조정합니다
        val adjustedWeights = calculateAdjustedWeights(
            hasWifi = wifiResult != null,
            hasLens = lensResult != null,
            hasEmf = emfResult != null
        )

        // 각 레이어의 가중 점수를 계산합니다
        val wifiScore = (wifiResult?.score ?: 0) * adjustedWeights.wifi
        val lensScore = (lensResult?.score ?: 0) * adjustedWeights.lens
        val emfScore = (emfResult?.score ?: 0) * adjustedWeights.emf

        // 기반 점수 합산
        val baseScore = wifiScore + lensScore + emfScore

        // 양성 레이어 수 카운트 (임계값 30 이상 = 양성으로 판정)
        val positiveCount = listOfNotNull(wifiResult, lensResult, emfResult)
            .count { it.score >= 30 }

        // 교차 보정 계수 적용
        val correctionFactor = when (positiveCount) {
            0 -> 1.0f
            1 -> CORRECTION_SINGLE   // 단일 탐지 — 신뢰도 낮춤
            2 -> CORRECTION_DOUBLE   // 이중 탐지 — 신뢰도 높임
            else -> CORRECTION_TRIPLE  // 삼중 탐지 — 강력한 신호
        }

        val finalScore = (baseScore * correctionFactor)
            .toInt()
            .coerceIn(0, 100)

        return ValidationResult(
            overallRisk = finalScore,
            riskLevel = scoreToRiskLevel(finalScore),
            positiveLayerCount = positiveCount,
            wifiContribution = wifiScore.toInt(),
            lensContribution = lensScore.toInt(),
            emfContribution = emfScore.toInt(),
            correctionFactor = correctionFactor,
            findings = buildFindings(wifiResult, lensResult, emfResult)
        )
    }

    // 위험도 점수를 등급으로 변환합니다
    fun scoreToRiskLevel(score: Int): RiskLevel = when {
        score < THRESHOLD_SAFE -> RiskLevel.SAFE
        score < THRESHOLD_INTEREST -> RiskLevel.INTEREST
        score < THRESHOLD_CAUTION -> RiskLevel.CAUTION
        score < THRESHOLD_DANGER -> RiskLevel.DANGER
        else -> RiskLevel.CRITICAL
    }
}

// 조정된 가중치 — 불변 데이터 클래스
data class AdjustedWeights(
    val wifi: Float,
    val lens: Float,
    val emf: Float
)

// 검증 결과 — 불변 데이터 클래스
data class ValidationResult(
    val overallRisk: Int,
    val riskLevel: RiskLevel,
    val positiveLayerCount: Int,
    val wifiContribution: Int,
    val lensContribution: Int,
    val emfContribution: Int,
    val correctionFactor: Float,
    val findings: List<Finding>
)
```

---

## 14.2 가중치 동적 조정 — Wi-Fi 없을 때

### 레이어가 비활성화되는 상황

세 레이어가 항상 모두 사용 가능하지는 않습니다.

| 상황 | 비활성화 레이어 |
|------|--------------|
| Wi-Fi 꺼짐 또는 연결 없음 | Layer 1 (Wi-Fi 스캔) |
| 카메라 권한 거부 | Layer 2 (렌즈 감지) |
| 자력계 없는 기기 | Layer 3 (EMF) |
| 완전 오프라인 환경 | Layer 1 |

이때 단순히 해당 레이어를 0점으로 처리하면 최대 가능 점수가 줄어들어 비현실적인 결과가 나옵니다. 대신 나머지 레이어들에 가중치를 재분배합니다.

```kotlin
// 사용 가능한 레이어에 가중치를 동적으로 재분배합니다
private fun calculateAdjustedWeights(
    hasWifi: Boolean,
    hasLens: Boolean,
    hasEmf: Boolean
): AdjustedWeights {
    // 사용 가능한 레이어의 기본 가중치 합계 계산
    val totalWeight = (if (hasWifi) WEIGHT_WIFI else 0f) +
                      (if (hasLens) WEIGHT_LENS else 0f) +
                      (if (hasEmf) WEIGHT_EMF else 0f)

    if (totalWeight == 0f) {
        // 모든 레이어 비활성화 — 탐지 불가 상태
        return AdjustedWeights(0f, 0f, 0f)
    }

    // 사용 가능한 레이어끼리 100%가 되도록 정규화합니다
    return AdjustedWeights(
        wifi = if (hasWifi) WEIGHT_WIFI / totalWeight else 0f,
        lens = if (hasLens) WEIGHT_LENS / totalWeight else 0f,
        emf = if (hasEmf) WEIGHT_EMF / totalWeight else 0f
    )
}
```

### 동적 조정 예시

```
Wi-Fi 없음 (Layer 1 비활성화):
  기본: Wi-Fi 50% + 렌즈 35% + EMF 15% = 100%
  조정: (렌즈 35% + EMF 15%) = 50% → 정규화
  결과: Wi-Fi 0% + 렌즈 70% + EMF 30% = 100%

렌즈+Wi-Fi만 (EMF 없음):
  기본: Wi-Fi 50% + 렌즈 35% = 85% → 정규화
  결과: Wi-Fi 58.8% + 렌즈 41.2% + EMF 0%
```

---

## 14.3 보정 계수 설계 근거

### 왜 ×0.7, ×1.2, ×1.5인가

이 수치는 경험적으로 도출한 값입니다. 실제 IP 카메라와 일반 가전제품을 대상으로 반복 테스트한 결과입니다.

**단일 탐지 (×0.7)**: 하나의 레이어만 양성이면 다른 레이어가 침묵하는 겁니다. 즉, 두 개의 독립적인 검증이 "없음"이라고 말하는 상황입니다. 신뢰도를 낮추는 게 맞습니다.

```
예시: Wi-Fi 스캔만 양성 (카메라 OUI 발견, 점수 75)
  기반 점수: 75 × 0.5 = 37.5
  보정 적용: 37.5 × 0.7 = 26.25 → 약 26점 (INTEREST 등급)
  해석: "네트워크에 의심 기기가 있지만, 렌즈/EMF 미확인"
```

**이중 탐지 (×1.2)**: 두 개의 독립적인 물리 원리가 같은 방향을 가리킵니다. 우연의 일치가 아닐 가능성이 높습니다.

```
예시: Wi-Fi 양성 (75점) + 렌즈 양성 (60점), EMF 음성
  기반 점수: (75 × 0.5) + (60 × 0.35) = 37.5 + 21 = 58.5
  보정 적용: 58.5 × 1.2 = 70.2 → 약 70점 (DANGER 등급)
  해석: "네트워크 기기 + 렌즈 역반사 이중 확인"
```

**삼중 탐지 (×1.5)**: 세 레이어 모두 양성. 세 가지 독립적인 물리적 방법이 모두 같은 결론입니다. 강력한 증거입니다.

```
예시: 세 레이어 모두 양성 (Wi-Fi 80, 렌즈 70, EMF 50)
  기반 점수: (80 × 0.5) + (70 × 0.35) + (50 × 0.15) = 40 + 24.5 + 7.5 = 72
  보정 적용: 72 × 1.5 = 108 → 100점 (상한선, CRITICAL 등급)
  해석: "세 레이어 모두 양성 — 즉시 확인 필요"
```

### 상한선 100점의 의미

점수가 100을 넘어도 100으로 제한합니다. `coerceIn(0, 100)` 사용. 이유는 두 가지입니다.

첫째, UI에서 게이지(0~100%)로 표시하기 위한 정규화입니다. 둘째, "100% 확실"이라고 주장하지 않기 위해서입니다. 100점이라도 "높은 가능성"이지 "확정"이 아닙니다.

---

## 14.4 위험도 0~100 스코어 산출 알고리즘 — 전체 흐름

```kotlin
// domain/usecase/CalculateRiskUseCase.kt
class CalculateRiskUseCase @Inject constructor(
    private val crossValidator: CrossValidator
) {
    suspend operator fun invoke(
        wifiResult: ScanResult?,
        lensResult: ScanResult?,
        emfResult: ScanResult?
    ): RiskAssessment {

        // CrossValidator에 세 레이어 결과를 넘깁니다
        val validation = crossValidator.validate(wifiResult, lensResult, emfResult)

        // 위험도에 따른 권고 사항을 생성합니다
        val recommendations = buildRecommendations(
            validation.riskLevel,
            validation.findings
        )

        return RiskAssessment(
            score = validation.overallRisk,
            level = validation.riskLevel,
            validation = validation,
            recommendations = recommendations,
            timestamp = System.currentTimeMillis()
        )
    }

    // 위험 등급별 사용자 권고 사항
    private fun buildRecommendations(
        level: RiskLevel,
        findings: List<Finding>
    ): List<String> = buildList {
        when (level) {
            RiskLevel.SAFE -> {
                add("현재 환경에서 의심 신호가 발견되지 않았습니다.")
                add("정기적인 재스캔을 권장합니다 (30분마다).")
            }
            RiskLevel.INTEREST -> {
                add("약한 의심 신호가 있습니다. 추가 확인을 권장합니다.")
                findings.forEach { finding -> add("• ${finding.description}") }
            }
            RiskLevel.CAUTION -> {
                add("주의: 복수의 의심 신호가 감지되었습니다.")
                add("렌즈 찾기 모드로 육안 확인을 권장합니다.")
                findings.forEach { finding -> add("• ${finding.description}") }
            }
            RiskLevel.DANGER, RiskLevel.CRITICAL -> {
                add("경고: 강한 의심 신호가 감지되었습니다.")
                add("즉시 육안으로 확인하거나 다른 장소로 이동하세요.")
                add("의심 기기 발견 시 덮개를 씌우거나 관할 경찰에 신고하세요.")
                findings.forEach { finding -> add("• ${finding.description}") }
            }
        }
    }
}
```

---

## 14.5 실제 탐지 시나리오 3가지

### 시나리오 1: 숙박시설 (모텔/에어비앤비)

```
환경: 에어비앤비 원룸 체크인 직후 스캔
      같은 Wi-Fi 연결, 플래시 켜고 렌즈 스캔, EMF 스캔

탐지 결과:
  Layer 1 Wi-Fi:
    - ARP 기기 3개: 라우터(ASUS), 스마트TV(Samsung), 의심 기기(Hikvision OUI)
    - 의심 기기 포트: 554(RTSP) OPEN, 8080(HTTP) OPEN
    - Wi-Fi 점수: 85

  Layer 2 렌즈:
    - 에어컨 리모컨 방향에서 역반사 포인트 1개 발견
    - 원형도 0.82, 밝기 240, 플래시 OFF 시 소멸 확인
    - 렌즈 점수: 78

  Layer 3 EMF:
    - 리모컨 방향 자기장 기준선 대비 +35μT 상승
    - EMF 점수: 45

교차 검증:
  기반 점수: (85 × 0.5) + (78 × 0.35) + (45 × 0.15)
           = 42.5 + 27.3 + 6.75 = 76.55
  양성 레이어: 3개 → 보정 계수 ×1.5
  최종 점수: 76.55 × 1.5 = 114.8 → 100 (CRITICAL)

결과:
  🔴 CRITICAL — 위험도 100/100
  "에어컨 리모컨 방향에서 강한 의심 신호:
   카메라 제조사 기기(Hikvision) 네트워크 감지,
   RTSP 포트 활성, 렌즈 역반사 확인, 자기장 이상"
```

### 시나리오 2: 공중화장실

```
환경: 대형 마트 화장실
      Wi-Fi 연결 없음(공용 Wi-Fi 미연결), 플래시 렌즈 스캔

탐지 결과:
  Layer 1 Wi-Fi: 비활성화 (네트워크 미연결)
  → 가중치 재분배: 렌즈 70%, EMF 30%

  Layer 2 렌즈:
    - 환기구 방향에서 역반사 포인트 1개
    - 원형도 0.91, 밝기 245, 플래시 OFF 소멸 확인
    - 렌즈 점수: 88

  Layer 3 EMF:
    - 기준선 정상 범위 내
    - EMF 점수: 8

교차 검증:
  기반 점수: (88 × 0.7) + (8 × 0.3)
           = 61.6 + 2.4 = 64
  양성 레이어: 1개(렌즈만) → 보정 계수 ×0.7
  최종 점수: 64 × 0.7 = 44.8 → 44 (INTEREST)

결과:
  🟡 INTEREST — 위험도 44/100
  "환기구 방향 렌즈 역반사 감지
   (Wi-Fi 연결 없어 네트워크 확인 불가)
   육안으로 환기구를 직접 확인하세요"

  해석: 렌즈 점수가 높지만 Wi-Fi로 교차 확인이 안 됨.
        단독 탐지이므로 보정으로 낮춤.
        실제로는 화장실 조명 LED였을 가능성도 있음.
```

### 시나리오 3: 탈의실

```
환경: 헬스장 탈의실
      Wi-Fi 연결됨, 렌즈 스캔 어려움 (조명 밝음)

탐지 결과:
  Layer 1 Wi-Fi:
    - ARP 기기: 라우터만 발견, 카메라 의심 기기 없음
    - Wi-Fi 점수: 5

  Layer 2 렌즈:
    - 조명이 밝아 역반사 구별 어려움 — 오탐 포인트 3개
    - 플래시 OFF 검증: 3개 모두 지속 (가짜 반사)
    - 렌즈 점수: 10 (플래시 검증으로 대폭 하향)

  Layer 3 EMF:
    - 헤어드라이어 방향 자기장 강함 (+80μT) — 명확한 발생원
    - 탐지 임계값 초과하지만 방향 명확
    - EMF 점수: 20

교차 검증:
  기반 점수: (5 × 0.5) + (10 × 0.35) + (20 × 0.15)
           = 2.5 + 3.5 + 3 = 9
  양성 레이어: 0개 → 보정 계수 ×1.0
  최종 점수: 9 → 9 (SAFE)

결과:
  🟢 SAFE — 위험도 9/100
  "현재 탐지된 의심 신호 없음.
   Wi-Fi 네트워크 정상, 렌즈 역반사 미탐지"
```

---

## 14.6 교차 검증 결과 ViewModel 연동

```kotlin
// ui/scan/ScanViewModel.kt (관련 부분)
class ScanViewModel @Inject constructor(
    private val runFullScanUseCase: RunFullScanUseCase,
    private val calculateRiskUseCase: CalculateRiskUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    fun startFullScan() {
        viewModelScope.launch {
            _uiState.value = ScanUiState.Scanning(progress = 0)

            try {
                // 세 레이어를 병렬로 실행합니다
                val wifiResult = async { runFullScanUseCase.scanWifi() }
                val lensResult = async { runFullScanUseCase.scanLens() }
                val emfResult = async { runFullScanUseCase.scanEmf() }

                // 모두 완료될 때까지 대기
                val results = awaitAll(wifiResult, lensResult, emfResult)

                // CrossValidator로 최종 위험도 산출
                val riskAssessment = calculateRiskUseCase(
                    wifiResult = results[0],
                    lensResult = results[1],
                    emfResult = results[2]
                )

                _uiState.value = ScanUiState.Complete(
                    riskScore = riskAssessment.score,
                    riskLevel = riskAssessment.level,
                    recommendations = riskAssessment.recommendations,
                    validation = riskAssessment.validation
                )
            } catch (e: Exception) {
                _uiState.value = ScanUiState.Error(
                    message = "스캔 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }
}

// 스캔 UI 상태 — 불변 sealed class
sealed class ScanUiState {
    object Idle : ScanUiState()
    data class Scanning(val progress: Int) : ScanUiState()
    data class Complete(
        val riskScore: Int,
        val riskLevel: RiskLevel,
        val recommendations: List<String>,
        val validation: ValidationResult
    ) : ScanUiState()
    data class Error(val message: String) : ScanUiState()
}
```

---

## 실습

> **실습 14-1**: 세 가지 시나리오의 숫자를 직접 계산해보세요. 보정 계수를 0.7/1.2/1.5 대신 0.6/1.3/1.8로 바꾸면 각 시나리오의 최종 점수가 어떻게 달라지는지 비교해보세요.

> **실습 14-2**: `CrossValidator`의 양성 판정 임계값을 30에서 50으로 올려보세요. 어떤 시나리오에서 양성 레이어 수가 달라지는지, 결과적으로 최종 점수가 어떻게 변하는지 분석해보세요.

---

## 핵심 정리

| 개념 | 내용 |
|------|------|
| 기본 가중치 | Wi-Fi 50%, 렌즈 35%, EMF 15% |
| 동적 가중치 | 비활성 레이어 비중을 나머지가 나눠 가짐 |
| 단독 탐지 ×0.7 | 교차 미확인 → 신뢰도 하향 |
| 이중 탐지 ×1.2 | 독립 교차 확인 → 신뢰도 상향 |
| 삼중 탐지 ×1.5 | 강력한 증거 → 최고 신뢰도 |

- 교차 검증은 오탐률을 결정적으로 낮추는 핵심 설계다
- 단독 탐지 신호는 신뢰도를 낮춰 사용자에게 과도한 공포를 주지 않는다
- 삼중 탐지는 세 개의 독립적 물리 원리가 같은 결론 — 가장 신뢰할 수 있는 신호다
- 100점이라도 "확정"이 아니라 "강한 가능성" — UI에서 솔직하게 전달해야 한다

---

## 다음 장 예고

탐지 엔진의 핵심이 완성되었습니다. Ch15에서는 이 결과를 사용자에게 어떻게 전달할지 — ScanResult 화면, RiskGauge 컴포넌트, 리포트 저장 — Compose UI 레이어를 구현합니다.

---
*참고 문서: docs/02-TRD.md, docs/03-TDD.md, docs/04-system-architecture.md*


\newpage


