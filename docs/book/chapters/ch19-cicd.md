# Ch19: CI/CD 파이프라인 — 자동으로 빌드하고 배포하라

> **이 장에서 배울 것**: "내 컴퓨터에서는 됩니다"를 영원히 추방하는 방법. GitHub Actions로 PR마다 자동 테스트, 릴리즈 태그 하나로 Play Store 내부 배포까지. APK 서명 자동화, ProGuard 난독화, 품질 게이트 설정의 전 과정을 다룹니다.

---

## 도입

주방장이 새 요리를 만들었다고 상상해보세요. 혼자 맛을 보고 "맛있다"고 했습니다. 그런데 손님에게 나가기 전에 위생 검사, 영양 성분 확인, 알레르기 표시, 플레이팅 기준 확인이 필요합니다. 이 모든 체크가 끝나야 서빙됩니다.

소프트웨어도 마찬가지입니다. 개발자가 "된다"고 했다고 바로 사용자에게 배포하면 안 됩니다. 린트 검사, 단위 테스트, 빌드 확인, 서명, 배포까지 모든 단계가 자동으로, 반복 가능하게, 사람의 실수 없이 실행되어야 합니다. 이것이 CI/CD입니다.

---

## 19.1 전체 파이프라인 설계

### 두 개의 파이프라인

SearCam은 두 가지 시점에 파이프라인이 실행됩니다.

```
PR 생성/업데이트 시:
  Pull Request → Lint → Unit Test → Build → 머지 허용/차단

릴리즈 태그 푸시 시:
  v1.0.0 태그 → 전체 테스트 → 빌드 & 서명 → Play Store 내부 배포
```

두 파이프라인의 역할 분리가 중요합니다. PR 파이프라인은 빠른 피드백을 위해 5분 안에 완료되어야 합니다. 릴리즈 파이프라인은 철저한 검증을 위해 시간이 더 걸려도 됩니다.

---

## 19.2 PR 파이프라인: 빠른 피드백

### 코드를 제출하기 전 검문소

```yaml
# .github/workflows/pr-check.yml
name: PR Check

on:
  pull_request:
    branches: [develop, main]

# 같은 PR에서 새 커밋 시 이전 실행 취소 (비용 절약)
concurrency:
  group: pr-${{ github.event.pull_request.number }}
  cancel-in-progress: true

jobs:
  lint:
    name: Lint (ktlint + detekt)
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      # Gradle 캐시: 의존성 다운로드 시간 단축 (첫 실행 3분 → 재실행 30초)
      - name: Gradle 캐시
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
          restore-keys: gradle-

      - name: ktlint 검사
        run: ./gradlew ktlintCheck

      - name: detekt 검사
        run: ./gradlew detekt

      # 실패 시 리포트를 아티팩트로 업로드
      - name: detekt 리포트 업로드
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: detekt-report
          path: build/reports/detekt/

  unit-test:
    name: 단위 테스트 + 커버리지
    runs-on: ubuntu-latest
    needs: lint  # lint 통과 후 실행
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Gradle 캐시
        uses: actions/cache@v4
        with:
          path: ~/.gradle/caches
          key: gradle-${{ hashFiles('**/*.gradle*') }}

      - name: 단위 테스트 실행
        run: ./gradlew testDebugUnitTest

      - name: 커버리지 리포트 생성
        run: ./gradlew jacocoTestReport

      # 80% 미만이면 빌드 실패
      - name: 커버리지 기준 검증 (80%)
        run: ./gradlew jacocoTestCoverageVerification

      - name: 테스트 결과 업로드
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: unit-test-results
          path: app/build/reports/tests/

  build:
    name: 디버그 빌드
    runs-on: ubuntu-latest
    needs: unit-test
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: 디버그 APK 빌드
        run: ./gradlew assembleDebug

      - name: APK 업로드
        uses: actions/upload-artifact@v4
        with:
          name: debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk
          retention-days: 7
```

---

## 19.3 Lint 설정: 코드 스타일 강제하기

### 두 명의 코드 심사관

ktlint와 detekt는 서로 다른 역할을 합니다. ktlint는 형식(formatting) 심사관입니다. 들여쓰기, 공백, 괄호 위치 같은 스타일을 검사합니다. detekt는 품질(quality) 심사관입니다. 너무 긴 함수, 복잡한 조건, 매직 넘버 같은 설계 문제를 찾습니다.

```kotlin
// build.gradle.kts (루트)
plugins {
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.5"
}

ktlint {
    version.set("1.1.1")
    android.set(true)
    outputToConsole.set(true)
    filter {
        exclude("**/generated/**")  // 자동 생성 코드 제외
        exclude("**/*_Hilt*")
    }
}
```

```yaml
# detekt.yml — 프로젝트 품질 기준
complexity:
  LongMethod:
    threshold: 50        # 함수 50줄 초과 금지
  LargeClass:
    threshold: 400       # 클래스 400줄 초과 경고
  ComplexCondition:
    threshold: 4         # 복합 조건 4개 초과 금지
  CyclomaticComplexMethod:
    threshold: 10        # 순환 복잡도 10 이하

style:
  MagicNumber:
    active: true
    ignoreNumbers: ['-1', '0', '1', '2']
  MaxLineLength:
    maxLineLength: 120

potential-bugs:
  UnsafeCast:
    active: true
```

---

## 19.4 릴리즈 파이프라인: 태그 하나로 배포까지

### 키 하나로 열리는 자동화

Git 태그를 `v1.0.0` 형식으로 푸시하면 전체 릴리즈 파이프라인이 실행됩니다. 테스트 → 빌드 → APK 서명 → Play Store 업로드까지 사람이 개입하지 않습니다.

```yaml
# .github/workflows/release.yml
name: Release

on:
  push:
    tags:
      - 'v*'  # v1.0.0, v2.1.3 등 모든 버전 태그

jobs:
  test:
    name: 전체 테스트 스위트
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: 전체 테스트 실행
        run: ./gradlew test
      - name: 커버리지 리포트
        run: ./gradlew jacocoTestReport

  build-and-sign:
    name: AAB 빌드 & 서명
    runs-on: ubuntu-latest
    needs: test
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      # GitHub Secret에서 키스토어 복원
      - name: 키스토어 복원
        env:
          KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
        run: |
          echo "$KEYSTORE_BASE64" | base64 -d > app/release.keystore

      # ProGuard/R8 난독화 포함 릴리즈 AAB 빌드
      - name: 릴리즈 AAB 빌드
        env:
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: ./gradlew bundleRelease

      - name: AAB 업로드
        uses: actions/upload-artifact@v4
        with:
          name: release-aab
          path: app/build/outputs/bundle/release/app-release.aab

  deploy:
    name: Play Store 내부 테스트 배포
    runs-on: ubuntu-latest
    needs: build-and-sign
    steps:
      - uses: actions/checkout@v4

      - name: AAB 다운로드
        uses: actions/download-artifact@v4
        with:
          name: release-aab

      - name: Play Store 내부 테스트 트랙 업로드
        uses: r0adkll/upload-google-play@v1
        with:
          serviceAccountJsonPlainText: ${{ secrets.PLAY_SERVICE_ACCOUNT_JSON }}
          packageName: com.searcam.app
          releaseFiles: app-release.aab
          track: internal
          status: completed

      # 배포 완료 Slack 알림
      - name: Slack 배포 완료 알림
        if: success()
        uses: 8398a7/action-slack@v3
        with:
          status: success
          text: |
            SearCam ${{ github.ref_name }} 배포 완료
            Play Store 내부 테스트 트랙에서 확인 가능합니다.
        env:
          SLACK_WEBHOOK_URL: ${{ secrets.SLACK_WEBHOOK_URL }}

      - name: Slack 배포 실패 알림
        if: failure()
        uses: 8398a7/action-slack@v3
        with:
          status: failure
          text: "SearCam ${{ github.ref_name }} 배포 실패 — 즉시 확인 필요"
        env:
          SLACK_WEBHOOK_URL: ${{ secrets.SLACK_WEBHOOK_URL }}
```

---

## 19.5 APK 서명 자동화: Keystore 시크릿 관리

### 열쇠를 안전하게 보관하는 법

APK 서명은 "이 앱은 신뢰할 수 있는 개발자가 만들었다"는 증명입니다. Keystore 파일은 앱의 신원과 같습니다. 분실하면 같은 패키지명으로 업데이트를 올릴 수 없습니다. Git에 절대 커밋하지 않아야 하며, GitHub Secrets에 암호화된 형태로 보관합니다.

**Keystore 생성:**

```bash
keytool -genkey -v \
  -keystore release.keystore \
  -alias searcam-release \
  -keyalg RSA \
  -keysize 4096 \
  -validity 9125 \
  -storepass <strong-password> \
  -keypass <strong-password>
```

**Base64 인코딩 후 GitHub Secret 저장:**

```bash
# macOS / Linux
base64 -i release.keystore | pbcopy  # 클립보드에 복사

# GitHub Repository → Settings → Secrets and variables → Actions
# New repository secret
# Name: KEYSTORE_BASE64
# Value: (클립보드 내용 붙여넣기)
```

**등록해야 할 GitHub Secrets:**

| Secret 이름 | 내용 |
|------------|------|
| `KEYSTORE_BASE64` | Base64 인코딩된 keystore 파일 |
| `KEYSTORE_PASSWORD` | 키스토어 비밀번호 |
| `KEY_ALIAS` | 키 별칭 (예: `searcam-release`) |
| `KEY_PASSWORD` | 키 비밀번호 |
| `PLAY_SERVICE_ACCOUNT_JSON` | Google Play 서비스 계정 JSON |
| `SLACK_WEBHOOK_URL` | Slack 알림 웹훅 URL |

**Gradle에서 환경변수로 서명 설정:**

```kotlin
// app/build.gradle.kts
android {
    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
                ?: error("KEYSTORE_PASSWORD 환경변수가 설정되지 않았습니다")
            keyAlias = System.getenv("KEY_ALIAS")
                ?: error("KEY_ALIAS 환경변수가 설정되지 않았습니다")
            keyPassword = System.getenv("KEY_PASSWORD")
                ?: error("KEY_PASSWORD 환경변수가 설정되지 않았습니다")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

---

## 19.6 ProGuard/R8 난독화

### 코드를 읽을 수 없게 만드는 이유

보안 앱인 SearCam의 탐지 알고리즘이 리버스 엔지니어링으로 분석된다면 어떻게 될까요? 악의적인 공격자가 탐지 로직을 우회하도록 카메라를 설계할 수 있습니다. R8 난독화는 코드를 `a.b.c` 같은 무의미한 이름으로 바꿔 분석을 어렵게 만듭니다.

```
# proguard-rules.pro

# Kotlin 코루틴 (필수 유지)
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Hilt DI (생성 코드 유지)
-keepclasseswithmembernames class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}

# Room DB (DAO 인터페이스 유지)
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# Firebase Crashlytics (스택 트레이스 가독성 유지)
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# 도메인 모델 (직렬화 대상)
-keep class com.searcam.app.domain.model.** { *; }

# OUI DB 파싱 (JSON 필드명 유지)
-keep class com.searcam.app.data.local.oui.OuiEntry { *; }

# 탐지 알고리즘 핵심 클래스는 난독화 (이름 제거)
# → 기본적으로 R8가 모든 클래스를 난독화합니다
```

빌드 타입별 ProGuard 적용 전략입니다.

```kotlin
buildTypes {
    debug {
        isMinifyEnabled = false  // 디버그: 난독화 없음 (빠른 빌드)
        applicationIdSuffix = ".debug"
    }
    create("staging") {
        isMinifyEnabled = true   // 스테이징: 난독화 (릴리즈와 동일 환경)
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
        applicationIdSuffix = ".staging"
        signingConfig = signingConfigs.getByName("debug")  // 디버그 키로 서명
    }
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
        signingConfig = signingConfigs.getByName("release")
    }
}
```

---

## 19.7 자동 버전 관리: Git Tag 기반

### 버전 번호를 수동으로 바꾸지 마라

개발자가 `versionCode`를 잊어버리고 올리지 않으면 Play Store 업로드가 실패합니다. Git 태그를 진실의 원천으로 삼으면 이 문제가 사라집니다.

버전 체계는 다음 규칙을 따릅니다.

```
versionName: <major>.<minor>.<patch>  (예: 1.2.3)
versionCode: major * 10000 + minor * 100 + patch  (예: 10203)

→ versionCode는 항상 증가하며, Play Store가 이 숫자로 업데이트를 판단합니다
```

```kotlin
// app/build.gradle.kts
fun getVersionName(): String {
    return try {
        val tag = providers.exec {
            commandLine("git", "describe", "--tags", "--abbrev=0")
        }.standardOutput.asText.get().trim()
        tag.removePrefix("v")
    } catch (e: Exception) {
        "0.0.1"  // 태그 없는 개발 빌드 기본값
    }
}

fun getVersionCode(): Int {
    val parts = getVersionName().split(".")
    val major = parts.getOrElse(0) { "0" }.toIntOrNull() ?: 0
    val minor = parts.getOrElse(1) { "0" }.toIntOrNull() ?: 0
    val patch = parts.getOrElse(2) { "0" }.toIntOrNull() ?: 0
    return major * 10_000 + minor * 100 + patch
}

android {
    defaultConfig {
        versionName = getVersionName()
        versionCode = getVersionCode()
    }
}
```

릴리즈 절차는 단 두 줄입니다.

```bash
git tag v1.0.0
git push origin v1.0.0
# → GitHub Actions가 나머지를 자동으로 처리
```

---

## 19.8 품질 게이트: 통과하지 않으면 머지 불가

### 자동 검문소

PR이 머지되려면 모든 품질 게이트를 통과해야 합니다. GitHub Branch Protection Rules로 강제합니다.

```
GitHub Repository Settings:
  Branch protection rules → main, develop
    ✓ Require a pull request before merging
    ✓ Require status checks to pass before merging
        Required checks:
          - Lint (ktlint + detekt)
          - 단위 테스트 + 커버리지
          - 디버그 빌드
    ✓ Require at least 1 approval
    ✓ Dismiss stale reviews
    ✓ Do not allow bypassing the above settings
```

| 품질 게이트 | 기준 | 실패 시 |
|-----------|------|--------|
| ktlint | 에러 0건 | PR 머지 차단 |
| detekt | 에러 0건 | PR 머지 차단 |
| 단위 테스트 | 전부 통과 | PR 머지 차단 |
| 커버리지 | 80% 이상 | PR 머지 차단 |
| 빌드 성공 | exit 0 | PR 머지 차단 |
| 코드 리뷰 | 1명 이상 승인 | PR 머지 차단 |

---

## 19.9 Nightly Build: 야간 전체 검증

### 자는 동안 일하는 파이프라인

매일 자정(UTC), develop 브랜치의 최신 코드를 전체 테스트합니다. PR 파이프라인보다 느리지만 더 철저한 검증(Instrumented Test 포함)을 실행합니다.

```yaml
# .github/workflows/nightly.yml
name: Nightly Build

on:
  schedule:
    - cron: '0 15 * * *'  # 한국시간 자정 (UTC 15:00)

jobs:
  nightly-full-test:
    name: 야간 전체 테스트
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          ref: develop

      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: 전체 테스트 + 커버리지
        run: ./gradlew test jacocoTestReport

      - name: 디버그 빌드
        run: ./gradlew assembleDebug

      - name: 커버리지 리포트 아티팩트
        uses: actions/upload-artifact@v4
        with:
          name: nightly-coverage-report
          path: app/build/reports/jacoco/

      - name: 실패 시 Slack 알림
        if: failure()
        uses: 8398a7/action-slack@v3
        with:
          status: failure
          text: "Nightly Build 실패 — develop 브랜치 즉시 확인 필요"
        env:
          SLACK_WEBHOOK_URL: ${{ secrets.SLACK_WEBHOOK_URL }}
```

---

## 19.10 Firebase 환경 분리

### 개발/스테이징/프로덕션을 완전히 격리하기

Crashlytics 데이터, Analytics 이벤트, Performance 메트릭이 개발용과 프로덕션용이 섞이면 의미 없는 데이터가 됩니다. Firebase 프로젝트를 세 개로 분리하고, 빌드 타입별로 다른 `google-services.json`을 사용합니다.

```
app/
├── src/
│   ├── debug/
│   │   └── google-services.json      # searcam-debug 프로젝트
│   ├── staging/
│   │   └── google-services.json      # searcam-staging 프로젝트
│   └── release/
│       └── google-services.json      # searcam-prod 프로젝트
```

| 환경 | Firebase 프로젝트 | Crashlytics | 로깅 |
|------|-----------------|-------------|------|
| debug | searcam-debug | 비활성화 | Timber 전체 |
| staging | searcam-staging | 활성화 | WARNING+ |
| release | searcam-prod | 활성화 | Crashlytics만 |

---

## 정리

CI/CD 파이프라인의 가치는 "자동화"가 아니라 "일관성"에 있습니다. 어떤 개발자가, 어떤 시점에, 어떤 환경에서 배포하더라도 동일한 품질 기준을 통과해야 합니다.

SearCam의 파이프라인은 세 가지 원칙을 따릅니다.

1. **빠른 피드백**: PR 파이프라인은 5분 내 완료
2. **시크릿 격리**: Keystore와 서비스 계정은 GitHub Secrets에만 존재
3. **자동화 완결**: 태그 하나로 Play Store 배포까지

다음 장에서는 배포된 앱의 릴리즈 전략과 단계적 출시(Staged Rollout)를 다룹니다.
