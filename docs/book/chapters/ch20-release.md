# Ch20: 릴리즈와 배포 전략

> **이 장에서 배울 것**: 앱을 만드는 것과 앱을 출시하는 것은 다릅니다. Google Play 스토어 등록, 보안 앱 심사 대비, 단계적 출시(Staged Rollout)로 리스크를 줄이는 법, Git 태그 기반 버전 관리, 효과적인 릴리즈 노트 작성법을 배웁니다.

---

## 도입

영화 한 편을 만들었다고 관객이 보는 것이 아닙니다. 시사회, 심사위원회 검토, 등급 분류, 극장 배급 계약, 마케팅까지 해야 비로소 스크린에 올라갑니다. 그리고 처음에는 몇 개 극장에서만 시작하고, 반응이 좋으면 전국으로 확대합니다.

앱 출시도 같습니다. Google Play 스토어에 올리기까지 심사를 통과해야 하고, 처음에는 소수에게만 배포했다가 점차 확대하는 전략이 필요합니다. 특히 SearCam처럼 카메라와 Wi-Fi를 다루는 보안 앱은 심사 과정이 더 까다롭습니다.

---

## 20.1 Google Play Console 초기 설정

### 첫 등록 전에 준비할 것

Play Console에 앱을 등록하기 전에 다음 항목을 준비해야 합니다.

| 항목 | 규격 | 비고 |
|------|------|------|
| 앱 아이콘 | 512 × 512 px, PNG | 투명 배경 허용 |
| 피처드 이미지 | 1024 × 500 px, PNG/JPG | 스토어 상단 배너 |
| 스크린샷 (폰) | 최소 2장, 최대 8장 | 1080px 이상 권장 |
| 스크린샷 (태블릿) | 선택사항 | 7인치/10인치 |
| 짧은 설명 | 80자 이내 | 검색 결과에 표시 |
| 긴 설명 | 4000자 이내 | 키워드 포함 |
| 개인정보처리방침 URL | 필수 | 카메라 권한 때문에 |
| 콘텐츠 등급 | IARC 설문 완료 | 보안 카테고리 |

**카테고리 선택 전략:**

SearCam은 "도구(Tools)" 카테고리가 적합합니다. "보안(Security)"을 선택하면 더 적합해 보이지만, Google Play의 Security 카테고리는 안티바이러스, VPN 등에 집중되어 있어 몰래카메라 탐지 앱의 노출이 오히려 줄어들 수 있습니다.

---

## 20.2 보안 앱 심사 대비

### 심사관의 눈으로 보기

카메라와 위치 권한, Wi-Fi 스캔, 네트워크 포트 스캔을 사용하는 앱은 Google의 집중 심사 대상입니다. 심사관은 "이 앱이 악용될 수 있는가?"를 봅니다. SearCam의 경우 다음 세 가지를 명확히 해야 합니다.

**권한 사용 목적 명시:**

```xml
<!-- AndroidManifest.xml -->

<!-- 카메라: 렌즈 반사광 감지용 (사진 저장 없음) -->
<uses-permission android:name="android.permission.CAMERA" />

<!-- 위치: Wi-Fi 네트워크 스캔 (Android 권한 정책) -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
```

권한 요청 시 사용자에게 보여주는 설명문도 심사 항목입니다.

```kotlin
// 권한 요청 전 rationale 표시
@Composable
fun CameraPermissionRationale(onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("카메라 권한이 필요합니다") },
        text = {
            Text(
                "몰래카메라 렌즈의 반사광을 감지하기 위해 카메라를 사용합니다. " +
                "촬영된 이미지는 기기에 저장되지 않으며, " +
                "서버로 전송되지 않습니다."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("확인") }
        }
    )
}
```

**데이터 안전 섹션(Data Safety) 작성:**

Play Console의 "데이터 안전" 섹션은 수집/공유 데이터를 선언하는 곳입니다. SearCam은 다음과 같이 선언합니다.

| 데이터 유형 | 수집 | 공유 | 이유 |
|-----------|------|------|------|
| 카메라 데이터 | 아니오 | 아니오 | 메모리에서만 처리 |
| Wi-Fi 정보 | 아니오 | 아니오 | 기기에 저장 안 함 |
| 정확한 위치 | 아니오 | 아니오 | Wi-Fi 권한에만 필요 |
| 크래시 로그 | 예 | 아니오 | Crashlytics 자동 수집 |
| 기기 ID | 아니오 | 아니오 | 수집하지 않음 |

**심사 거절 사유 TOP 5 (사전 예방):**

1. 권한 남용: 불필요한 권한 요청 → 필요한 권한만 선언
2. 개인정보처리방침 미비 → 카메라/위치 처리 방침 명확히 기재
3. 오해를 유발하는 설명 → "100% 탐지" 같은 과장 문구 금지
4. 악성코드 의심 행위 → 포트 스캔 목적을 스토어 설명에 명시
5. 타 앱 사칭 → 독창적인 아이콘과 앱 이름 사용

---

## 20.3 단계적 출시 (Staged Rollout)

### 전쟁 전 정찰하기

신규 버전을 전체 사용자에게 한 번에 배포하는 것은 도박입니다. 1%의 기기에서 크래시가 발생해도, 100만 사용자라면 1만 명이 영향받습니다. 단계적 출시는 리스크를 분산하는 보험입니다.

**배포 트랙 전략:**

```
내부 테스트 (Internal)
  ├── 대상: 개발팀 (최대 100명)
  ├── 조건: 자동 배포 (CI/CD)
  └── 목적: 기능 검증, 기초 안정성 확인

클로즈드 테스트 알파 (Alpha)
  ├── 대상: 테스터 (50~200명)
  ├── 조건: 내부 테스트 1주 후 수동 프로모션
  └── 목적: 다양한 기기/OS 버전 호환성

오픈 테스트 베타 (Beta)
  ├── 대상: 관심 있는 일반 사용자 (자발적 참여)
  ├── 조건: 알파 2주 후 수동 프로모션
  └── 목적: 실사용 환경 피드백

프로덕션 (Production) — 단계적 출시
  ├── 1단계: 1% (약 1,000명 기준)
  ├── 2단계: 10% (24시간 후, 크래시율 < 1% 확인 시)
  ├── 3단계: 50% (48시간 후, ANR < 0.5% 확인 시)
  └── 4단계: 100% (72시간 후, 지표 안정적 시)
```

**Play Console에서 단계적 출시 설정:**

```
Production → Create new release
  → 롤아웃 비율: 1%
  → 저장 후 [검토] → [출시 시작]

이후 크래시율 모니터링:
  Android Vitals → 크래시율, ANR 비율 확인
  → 안전하면 [롤아웃 비율 늘리기] → 10% → 50% → 100%
  → 위험하면 [롤아웃 중지] → 즉시 배포 멈춤
```

**롤아웃 중지 기준:**

| 지표 | 중지 기준 |
|------|----------|
| 크래시율 (24h) | > 2% |
| ANR 비율 (24h) | > 1% |
| 별점 (신규 리뷰) | 3.0 미만 |
| 특정 기기 크래시 급증 | 동일 기기 50회/시간 이상 |

---

## 20.4 버전 관리: versionCode와 versionName

### 버전이 꼬이면 배포가 막힌다

Android 앱의 버전에는 두 가지 개념이 있습니다. versionName은 사용자에게 보이는 "1.2.3"입니다. versionCode는 Play Store가 업데이트 순서를 판단하는 숫자입니다. versionCode는 반드시 이전 배포보다 커야 합니다.

**버전 체계:**

```
versionName 형식: MAJOR.MINOR.PATCH
  MAJOR: 대규모 변경 (호환성 깨짐, UI 전면 개편)
  MINOR: 기능 추가 (이전 버전과 호환)
  PATCH: 버그 수정

versionCode 계산: MAJOR × 10000 + MINOR × 100 + PATCH
  1.0.0 → 10000
  1.1.0 → 10100
  1.1.3 → 10103
  2.0.0 → 20000
```

```kotlin
// app/build.gradle.kts
// Git 태그에서 자동으로 버전 추출 (수동 변경 불필요)
fun getVersionName(): String {
    return try {
        providers.exec {
            commandLine("git", "describe", "--tags", "--abbrev=0")
        }.standardOutput.asText.get().trim().removePrefix("v")
    } catch (e: Exception) {
        "0.0.1-dev"
    }
}

fun getVersionCode(): Int {
    val versionName = getVersionName().removeSuffix("-dev")
    val parts = versionName.split(".")
    val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    return major * 10_000 + minor * 100 + patch
}

android {
    defaultConfig {
        versionName = getVersionName()
        versionCode = getVersionCode()
    }
}
```

**버전 변경 절차:**

```bash
# 패치 릴리즈 (버그 수정)
git tag v1.0.1 && git push origin v1.0.1

# 마이너 릴리즈 (기능 추가)
git tag v1.1.0 && git push origin v1.1.0

# 메이저 릴리즈 (대규모 변경)
git tag v2.0.0 && git push origin v2.0.0

# 태그 푸시 → CI/CD 자동 실행 → Play Store 내부 배포
```

---

## 20.5 릴리즈 노트 작성

### 사용자와의 대화

릴리즈 노트는 개발자가 사용자에게 보내는 편지입니다. 기술적인 내용이 아닌, 사용자에게 의미 있는 변화를 설명합니다.

**나쁜 릴리즈 노트:**

```
v1.1.0
- RiskCalculator 가중치 알고리즘 업데이트
- NullPointerException 수정 (MainActivity line 142)
- ProGuard 규칙 추가
- Gradle 버전 업그레이드 (8.3 → 8.4)
```

**좋은 릴리즈 노트:**

```
v1.1.0 업데이트

새로운 기능
• 자기장(EMF) 감지 정확도 30% 향상: 스마트폰 자체 간섭을
  자동으로 걸러내도록 개선했습니다
• 스캔 속도 개선: Wi-Fi 포트 스캔이 최대 4기기를 동시에
  처리해 Quick Scan이 10초 빨라졌습니다

수정된 문제
• 일부 기기에서 스캔 시작 직후 앱이 종료되던 문제를 수정했습니다
• Galaxy A 시리즈에서 자기장 센서가 초기화되지 않던 문제를 수정했습니다

안정성 개선
• 오랜 시간 사용해도 메모리 사용량이 안정적으로 유지됩니다
```

**릴리즈 노트 파일 관리:**

Play Console은 최대 500자(언어별)의 릴리즈 노트를 지원합니다. 소스 코드와 함께 관리하는 것이 좋습니다.

```
fastlane/
└── metadata/
    └── android/
        ├── ko-KR/
        │   └── changelogs/
        │       ├── 10100.txt  # v1.1.0 한국어 노트
        │       └── 10103.txt  # v1.1.3 한국어 노트
        └── en-US/
            └── changelogs/
                ├── 10100.txt
                └── 10103.txt
```

```
# fastlane/metadata/android/ko-KR/changelogs/10100.txt (500자 이내)

새로운 기능
• 자기장 감지 정확도 30% 향상
• Wi-Fi 스캔 속도 개선 (Quick Scan 10초 단축)

수정된 문제
• 일부 기기 앱 종료 문제 수정
• Galaxy A 시리즈 자기장 센서 초기화 오류 수정
```

---

## 20.6 앱 스토어 최적화 (ASO)

### 검색에서 발견되는 앱 만들기

앱 스토어 최적화(App Store Optimization, ASO)는 검색 결과에서 SearCam을 발견하기 쉽게 만드는 작업입니다. SEO의 앱 버전이라고 보면 됩니다.

**핵심 키워드 전략:**

```
주요 키워드 (검색량 높음):
  - 몰래카메라 탐지
  - 도청기 탐지
  - 카메라 감지기
  - 숨겨진 카메라

부가 키워드 (경쟁 낮음):
  - Wi-Fi 기기 스캔
  - EMF 탐지
  - 보안 점검
  - 호텔 숙박 안전
```

**앱 제목 구조:**

```
SearCam — 몰래카메라 탐지 & 보안 스캔
(30자 이내 권장)
```

**짧은 설명 (80자 이내):**

```
30초 만에 몰래카메라를 1차 스크리닝. Wi-Fi 스캔 + 렌즈 감지 + EMF 탐지 3단계 분석.
```

---

## 20.7 앱 서명 관리: Play App Signing

### 열쇠를 Google에게도 맡기는 이유

Google Play App Signing은 Google이 배포 키를 안전하게 보관하는 서비스입니다. 개발자의 업로드 키가 유출되거나 분실되어도 Google의 배포 키로 앱을 계속 서비스할 수 있습니다.

**설정 방법:**

```
Play Console → 설정 → 앱 서명
  → 'Google에서 앱 서명 키 관리' 선택 (최초 한 번)

이후 흐름:
  개발자: upload key로 AAB 서명 (업로드만)
  Google: 보관 중인 배포 키로 재서명 후 사용자에게 배포
```

이 구조의 장점은 업로드 키 분실 시 Google에 요청하면 재설정이 가능합니다. 배포 키는 Google이 관리하므로 분실 위험이 없습니다.

---

## 20.8 긴급 대응: 핫픽스 배포

### 불이 났을 때 빨리 끄는 법

프로덕션에서 심각한 버그가 발견되면 일반 릴리즈 사이클을 기다릴 수 없습니다. 핫픽스 절차를 사전에 정해두어야 합니다.

```bash
# 핫픽스 브랜치 생성 (main에서)
git checkout -b hotfix/crash-on-scan main

# 수정 작업
# ... 코드 수정 ...

# PR 생성 + 긴급 리뷰 (1명으로 단축)
git push origin hotfix/crash-on-scan
gh pr create --title "hotfix: 스캔 시작 시 크래시 수정" --base main

# PR 승인 후 main으로 머지
# → 태그 생성
git tag v1.0.1
git push origin v1.0.1
# → CI/CD 자동 실행 → Play Store 내부 배포
```

핫픽스 배포 후에도 단계적 출시를 합니다. 1% → 10% → 100% 순서로, 각 단계에서 크래시율을 확인합니다.

---

## 정리

릴리즈는 코딩이 끝난 다음에 시작되는 별도의 전문 영역입니다. SearCam에서 적용한 핵심 전략을 요약합니다.

1. **심사 통과**: 권한 목적 명시, 개인정보처리방침 완비, Data Safety 섹션 정확히 기재
2. **단계적 출시**: 1% → 10% → 50% → 100%, 각 단계에서 크래시율 확인
3. **자동 버전 관리**: Git 태그가 versionCode/versionName의 유일한 원천
4. **사용자 중심 릴리즈 노트**: 기술 용어 대신 사용자에게 의미 있는 변화로 설명
5. **Play App Signing**: 배포 키 관리를 Google에 위임해 분실 리스크 제거

다음 장에서는 배포된 앱을 운영하는 모니터링과 성능 최적화를 다룹니다.
