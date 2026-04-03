# SearCam CI/CD 파이프라인 설계서

> 버전: v1.0
> 작성일: 2026-04-03
> 대상: Phase 1 Android MVP

---

## 1. 개요

SearCam은 사용자 안전 앱이므로, 배포 전 **린트 → 테스트 → 빌드 → 서명 → 배포** 파이프라인을 자동화하여 품질 게이트를 통과하지 못한 코드가 프로덕션에 도달하지 않도록 한다.

---

## 2. 개발 워크플로우 (Git Flow)

### 2.1 브랜치 전략

```
main ─────────────────────────────────────────────────→
  │                                          ▲
  │                                          │ merge (release)
  ├── develop ──────────────────────────────→│
  │     │                          ▲         │
  │     ├── feature/wifi-scanner ──┤         │
  │     ├── feature/lens-detector ─┤         │
  │     ├── feature/emf-sensor ────┤         │
  │     └── feature/cross-validator┘         │
  │                                          │
  ├── release/1.0.0 ────────────────────────→│
  │                                          │
  └── hotfix/crash-fix ─────────────────────→│
```

### 2.2 브랜치 규칙

| 브랜치 | 용도 | 생성 기준 | 머지 대상 |
|--------|------|----------|----------|
| `main` | 프로덕션 릴리스 | - | - |
| `develop` | 개발 통합 | main에서 분기 | main (release 시) |
| `feature/*` | 기능 개발 | develop에서 분기 | develop |
| `release/*` | 릴리스 준비 | develop에서 분기 | main + develop |
| `hotfix/*` | 긴급 수정 | main에서 분기 | main + develop |

### 2.3 브랜치 네이밍 컨벤션

```
feature/[모듈]-[기능]
  예: feature/wifi-scanner
      feature/lens-retroreflection
      feature/report-pdf-export

bugfix/[이슈번호]-[설명]
  예: bugfix/42-emf-noise-filter

release/[버전]
  예: release/1.0.0

hotfix/[설명]
  예: hotfix/crash-on-scan
```

### 2.4 커밋 메시지 컨벤션

```
<type>(<scope>): <description>

<optional body>

<optional footer>
```

| Type | 용도 |
|------|------|
| feat | 새 기능 |
| fix | 버그 수정 |
| refactor | 리팩토링 (기능 변경 없음) |
| test | 테스트 추가/수정 |
| docs | 문서 변경 |
| chore | 빌드/설정 변경 |
| perf | 성능 개선 |
| ci | CI/CD 변경 |

**예시**:
```
feat(wifi): ARP 테이블 파싱 구현

- /proc/net/arp 파일 읽기
- MAC + IP 추출 및 파싱
- 중복 기기 필터링

Closes #12
```

---

## 3. GitHub Actions 파이프라인

### 3.1 PR 파이프라인

```yaml
# .github/workflows/pr-check.yml
name: PR Check

on:
  pull_request:
    branches: [develop, main]

concurrency:
  group: pr-${{ github.event.pull_request.number }}
  cancel-in-progress: true

jobs:
  lint:
    name: Lint
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Gradle Cache
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
      - name: ktlint Check
        run: ./gradlew ktlintCheck
      - name: detekt Check
        run: ./gradlew detekt

  unit-test:
    name: Unit Tests
    runs-on: ubuntu-latest
    needs: lint
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Run Unit Tests
        run: ./gradlew testDebugUnitTest
      - name: Upload Test Results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: unit-test-results
          path: app/build/reports/tests/
      - name: Upload Coverage
        run: ./gradlew jacocoTestReport
      - name: Check Coverage Threshold
        run: |
          # 80% 미만이면 실패
          ./gradlew jacocoTestCoverageVerification

  instrumented-test:
    name: Instrumented Tests
    runs-on: ubuntu-latest
    needs: unit-test
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Enable KVM
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
          sudo udevadm control --reload-rules
          sudo udevadm trigger --name-match=kvm
      - name: AVD Cache
        uses: actions/cache@v4
        with:
          path: |
            ~/.android/avd/*
            ~/.android/adb*
          key: avd-api-34
      - name: Run Instrumented Tests
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 34
          arch: x86_64
          script: ./gradlew connectedDebugAndroidTest

  build:
    name: Debug Build
    runs-on: ubuntu-latest
    needs: unit-test
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Build Debug APK
        run: ./gradlew assembleDebug
      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk
```

### 3.2 Release 파이프라인

```yaml
# .github/workflows/release.yml
name: Release

on:
  push:
    tags:
      - 'v*'

jobs:
  test:
    name: Full Test Suite
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Run All Tests
        run: ./gradlew test
      - name: Coverage Report
        run: ./gradlew jacocoTestReport

  build-and-sign:
    name: Build & Sign AAB
    runs-on: ubuntu-latest
    needs: test
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Decode Keystore
        env:
          KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
        run: |
          echo "$KEYSTORE_BASE64" | base64 -d > app/release.keystore
      - name: Build Release AAB
        env:
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: ./gradlew bundleRelease
      - name: Upload AAB
        uses: actions/upload-artifact@v4
        with:
          name: release-aab
          path: app/build/outputs/bundle/release/app-release.aab

  deploy:
    name: Deploy to Play Store
    runs-on: ubuntu-latest
    needs: build-and-sign
    steps:
      - uses: actions/checkout@v4
      - name: Download AAB
        uses: actions/download-artifact@v4
        with:
          name: release-aab
      - name: Deploy to Internal Testing
        uses: r0adkll/upload-google-play@v1
        with:
          serviceAccountJsonPlainText: ${{ secrets.PLAY_SERVICE_ACCOUNT_JSON }}
          packageName: com.searcam.app
          releaseFiles: app-release.aab
          track: internal
          status: completed
```

### 3.3 Nightly Build (선택)

```yaml
# .github/workflows/nightly.yml
name: Nightly Build

on:
  schedule:
    - cron: '0 0 * * *'  # 매일 자정 (UTC)

jobs:
  nightly:
    name: Nightly Full Test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          ref: develop
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Full Test + Coverage
        run: ./gradlew test jacocoTestReport
      - name: Build Debug
        run: ./gradlew assembleDebug
```

---

## 4. 린트 설정

### 4.1 ktlint

```kotlin
// build.gradle.kts (프로젝트 루트)
plugins {
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
}

ktlint {
    version.set("1.1.1")
    android.set(true)
    outputToConsole.set(true)
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.SARIF)
    }
    filter {
        exclude("**/generated/**")
    }
}
```

### 4.2 detekt

```yaml
# detekt.yml
complexity:
  LongMethod:
    threshold: 50        # 함수 50줄 제한
  LargeClass:
    threshold: 400       # 클래스 400줄 제한
  ComplexCondition:
    threshold: 4         # 복합 조건 4개 제한
  CyclomaticComplexMethod:
    threshold: 10        # 순환 복잡도 10 이하

style:
  MagicNumber:
    active: true
    ignoreNumbers:
      - '-1'
      - '0'
      - '1'
      - '2'
  MaxLineLength:
    maxLineLength: 120
  WildcardImport:
    active: true

naming:
  FunctionNaming:
    functionPattern: '[a-z][a-zA-Z0-9]*'
  VariableNaming:
    variablePattern: '[a-z][a-zA-Z0-9]*'

potential-bugs:
  UnsafeCast:
    active: true
  UselessPostfixExpression:
    active: true
```

### 4.3 Android Lint

```kotlin
// build.gradle.kts (app)
android {
    lint {
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = true
        htmlReport = true
        xmlReport = true
        disable += listOf("MissingTranslation")
        fatal += listOf("NewApi", "InlinedApi")
    }
}
```

---

## 5. 빌드 설정

### 5.1 Build Types

```kotlin
// build.gradle.kts (app)
android {
    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("Boolean", "ENABLE_LOGGING", "true")
        }
        create("staging") {
            isDebuggable = true
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            buildConfigField("Boolean", "ENABLE_LOGGING", "true")
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("Boolean", "ENABLE_LOGGING", "false")
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

### 5.2 Product Flavors

```kotlin
android {
    flavorDimensions += "distribution"
    productFlavors {
        create("playstore") {
            dimension = "distribution"
            buildConfigField("String", "STORE", "\"playstore\"")
        }
        create("internal") {
            dimension = "distribution"
            applicationIdSuffix = ".internal"
            buildConfigField("String", "STORE", "\"internal\"")
        }
    }
}
```

### 5.3 빌드 매트릭스

| Flavor | Build Type | Application ID | 용도 |
|--------|-----------|---------------|------|
| playstore + debug | debug | com.searcam.app.debug | 개발 |
| playstore + staging | staging | com.searcam.app.staging | QA 테스트 |
| playstore + release | release | com.searcam.app | 프로덕션 |
| internal + debug | debug | com.searcam.app.internal.debug | 내부 개발 |

---

## 6. APK/AAB 서명 전략

### 6.1 Keystore 관리

| 항목 | 설명 |
|------|------|
| 키스토어 파일 | `release.keystore` (로컬 보관, Git 제외) |
| 키 알고리즘 | RSA 4096 |
| 유효 기간 | 25년 |
| 보관 위치 | GitHub Secrets (Base64 인코딩) + 물리적 백업 2곳 |

### 6.2 Keystore 생성

```bash
keytool -genkey -v \
  -keystore release.keystore \
  -alias searcam-release \
  -keyalg RSA \
  -keysize 4096 \
  -validity 9125 \
  -storepass <password> \
  -keypass <password>
```

### 6.3 GitHub Secrets

| Secret 이름 | 내용 |
|------------|------|
| `KEYSTORE_BASE64` | Base64 인코딩된 keystore 파일 |
| `KEYSTORE_PASSWORD` | 키스토어 비밀번호 |
| `KEY_ALIAS` | 키 별칭 (`searcam-release`) |
| `KEY_PASSWORD` | 키 비밀번호 |
| `PLAY_SERVICE_ACCOUNT_JSON` | Google Play 서비스 계정 JSON |

### 6.4 서명 설정

```kotlin
// build.gradle.kts (app)
android {
    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
}
```

### 6.5 Play App Signing

- Google Play App Signing 활성화
- 업로드 키: 직접 관리하는 `release.keystore`
- 배포 키: Google이 관리 (Play Console에서 확인)
- 업로드 키 분실 시 Google에 요청하여 재설정 가능

---

## 7. 자동 버전 관리

### 7.1 버전 체계

```
versionName: <major>.<minor>.<patch>
versionCode: major * 10000 + minor * 100 + patch

예: 1.2.3 → versionCode = 10203
```

### 7.2 Git Tag 기반 자동화

```kotlin
// build.gradle.kts (app)
fun getVersionName(): String {
    val tag = providers.exec {
        commandLine("git", "describe", "--tags", "--abbrev=0")
    }.standardOutput.asText.get().trim()
    return tag.removePrefix("v")
}

fun getVersionCode(): Int {
    val parts = getVersionName().split(".")
    val major = parts.getOrElse(0) { "1" }.toInt()
    val minor = parts.getOrElse(1) { "0" }.toInt()
    val patch = parts.getOrElse(2) { "0" }.toInt()
    return major * 10000 + minor * 100 + patch
}

android {
    defaultConfig {
        versionName = getVersionName()
        versionCode = getVersionCode()
    }
}
```

### 7.3 버전 범프 프로세스

```bash
# 패치 릴리스
git tag v1.0.1
git push origin v1.0.1

# 마이너 릴리스
git tag v1.1.0
git push origin v1.1.0

# 메이저 릴리스
git tag v2.0.0
git push origin v2.0.0
```

---

## 8. Play Store 자동 배포

### 8.1 Gradle Play Publisher 설정

```kotlin
// build.gradle.kts (app)
plugins {
    id("com.github.triplet.play") version "3.9.0"
}

play {
    serviceAccountCredentials.set(file("play-service-account.json"))
    track.set("internal")  // internal → alpha → beta → production
    defaultToAppBundles.set(true)
    releaseStatus.set(ReleaseStatus.COMPLETED)
}
```

### 8.2 배포 트랙

| 트랙 | 대상 | 용도 |
|------|------|------|
| internal | 개발팀 (최대 100명) | 매 빌드 자동 배포 |
| alpha | 내부 테스터 | 주간 알파 |
| beta (closed) | 베타 테스터 (모집) | 격주 베타 |
| production | 전체 사용자 | 릴리스 시 |

### 8.3 배포 자동화 흐름

```
Git Tag Push (v1.0.0)
  → GitHub Actions (Release Workflow)
    → Test → Build AAB → Sign
    → Upload to Internal Track
    → Slack 알림

수동 프로모션 (Play Console):
  Internal → Alpha → Beta → Production (단계적)
```

---

## 9. 환경별 설정

### 9.1 환경 구분

| 환경 | Build Type | Firebase | 로깅 | AdMob |
|------|-----------|---------|------|-------|
| debug | debug | searcam-debug | Timber (전체) | 테스트 광고 |
| staging | staging | searcam-staging | Timber (WARNING+) | 테스트 광고 |
| release | release | searcam-prod | Crashlytics만 | 실제 광고 |

### 9.2 Firebase 프로젝트 분리

```
app/
├── src/
│   ├── debug/
│   │   └── google-services.json      # searcam-debug
│   ├── staging/
│   │   └── google-services.json      # searcam-staging
│   └── release/
│       └── google-services.json      # searcam-prod
```

### 9.3 환경별 상수

```kotlin
// BuildConfig를 통한 환경별 설정
object AppConfig {
    val isDebug: Boolean = BuildConfig.DEBUG
    val enableLogging: Boolean = BuildConfig.ENABLE_LOGGING
    val store: String = BuildConfig.STORE
}
```

---

## 10. 품질 게이트

### 10.1 PR 머지 조건 (필수)

| 게이트 | 기준 | 실패 시 |
|--------|------|--------|
| ktlint | 에러 0건 | PR 블록 |
| detekt | 에러 0건 | PR 블록 |
| Unit Test | 전부 통과 | PR 블록 |
| Test Coverage | 80% 이상 | PR 블록 |
| Build | 성공 | PR 블록 |
| Code Review | 1명 이상 승인 | PR 블록 |

### 10.2 Release 조건 (필수)

| 게이트 | 기준 | 실패 시 |
|--------|------|--------|
| 전체 테스트 | Unit + Instrumented 통과 | 릴리스 중단 |
| 커버리지 | 80% 이상 | 릴리스 중단 |
| ProGuard 빌드 | 에러 없음 | 릴리스 중단 |
| APK 크기 | < 30MB | 경고 |
| ANR 비율 (이전 버전) | < 0.5% | 릴리스 보류 |
| 크래시 비율 (이전 버전) | < 1% | 릴리스 보류 |

### 10.3 품질 대시보드

```
GitHub Actions Summary:
  ├── Lint:       PASS (0 errors, 3 warnings)
  ├── Unit Test:  PASS (142/142 passed)
  ├── Coverage:   83.5% (target: 80%)
  ├── Build:      PASS (APK: 18.2MB)
  └── Status:     READY TO MERGE
```

---

## 11. 보안 사항

### 11.1 시크릿 관리

- 모든 시크릿은 GitHub Secrets에만 저장
- 로컬 개발 시 `.env` 파일 사용 (`.gitignore`에 포함)
- 키스토어 파일은 Git에 절대 포함하지 않음
- `google-services.json`은 Firebase 프로젝트별로 분리

### 11.2 의존성 보안

```yaml
# .github/workflows/dependency-check.yml (주간 실행)
name: Dependency Check
on:
  schedule:
    - cron: '0 0 * * 1'  # 매주 월요일
jobs:
  check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Dependency Check
        run: ./gradlew dependencyCheckAnalyze
```

### 11.3 브랜치 보호 규칙

| 설정 | main | develop |
|------|------|---------|
| Require PR | O | O |
| Require reviews (1+) | O | O |
| Require status checks | O | O |
| Require linear history | O | X |
| Force push 금지 | O | O |
| Delete head branch | O | O |

---

*본 CI/CD 설계서는 Phase 1 Android MVP 기준이며, Phase 2 (iOS) 추가 시 Xcode Cloud 또는 GitHub Actions macOS runner를 추가합니다.*
*버전 v1.0 -- 2026-04-03*
