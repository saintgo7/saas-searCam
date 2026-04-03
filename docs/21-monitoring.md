# SearCam 모니터링 및 관측성 계획서

> 버전: v1.0
> 작성일: 2026-04-03
> 대상: Phase 1 Android MVP

---

## 1. 개요

SearCam은 사용자 안전 앱이므로, 크래시와 성능 저하를 **분 단위**로 감지하고 대응해야 한다. 모든 모니터링 데이터는 Firebase 기반으로 수집하며, 개인 식별 정보(PII)는 절대 수집하지 않는다.

### 1.1 모니터링 스택

| 영역 | 도구 | 비용 |
|------|------|------|
| 크래시 리포팅 | Firebase Crashlytics | 무료 |
| 성능 모니터링 | Firebase Performance | 무료 |
| 사용 분석 | Firebase Analytics | 무료 |
| 원격 설정 | Firebase Remote Config | 무료 |
| 앱 크기 | Android App Bundle Explorer | 무료 |
| ANR | Firebase Crashlytics (ANR) | 무료 |

### 1.2 PII 수집 금지 원칙

```
절대 수집하지 않는 데이터:
- 사용자 이름, 이메일, 전화번호
- GPS 좌표 (스캔 위치)
- Wi-Fi SSID, 발견된 기기 MAC 주소
- 스캔 결과 상세 (위험도, 기기 목록)
- 카메라 촬영 이미지/프레임

수집하는 데이터 (비식별):
- 크래시 스택 트레이스
- 앱 성능 메트릭 (시간, 프레임)
- 이벤트 (scan_start, scan_complete 등)
- 기기 모델, OS 버전, 앱 버전
```

---

## 2. 크래시 리포팅 (Firebase Crashlytics)

### 2.1 설정

```kotlin
// build.gradle.kts (app)
dependencies {
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
}

// build.gradle.kts (project)
plugins {
    id("com.google.firebase.crashlytics") version "2.9.9"
}
```

```kotlin
// SearCamApp.kt
class SearCamApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Release에서만 Crashlytics 활성화
        FirebaseCrashlytics.getInstance()
            .setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
    }
}
```

### 2.2 크래시 분류

#### 치명적 크래시 (Fatal)

| 카테고리 | 설명 | 심각도 | 대응 |
|---------|------|--------|------|
| ANR | Application Not Responding | P0 | 24시간 내 수정 |
| OOM | OutOfMemoryError | P0 | 24시간 내 수정 |
| NPE | NullPointerException | P1 | 48시간 내 수정 |
| SecurityException | 권한 관련 크래시 | P1 | 48시간 내 수정 |
| IllegalStateException | 잘못된 상태 전환 | P1 | 48시간 내 수정 |

#### 비치명적 크래시 (Non-Fatal)

| 카테고리 | 설명 | 심각도 | 대응 |
|---------|------|--------|------|
| 센서 에러 | 자기장 센서 접근 실패 | P2 | 다음 릴리스 |
| 네트워크 에러 | Wi-Fi 스캔 실패 | P2 | 다음 릴리스 |
| DB 에러 | Room DB 쿼리 실패 | P2 | 다음 릴리스 |
| PDF 생성 에러 | PDF 렌더링 실패 | P3 | 다음 릴리스 |
| 파싱 에러 | OUI DB 파싱 실패 | P3 | 다음 릴리스 |

### 2.3 커스텀 키 설정

```kotlin
// 크래시 디버깅을 위한 컨텍스트 정보 (PII 제외)
object CrashlyticsHelper {
    fun setScanContext(scanType: ScanType) {
        FirebaseCrashlytics.getInstance().apply {
            setCustomKey("scan_type", scanType.name)
            setCustomKey("app_version", BuildConfig.VERSION_NAME)
        }
    }

    fun setDeviceContext() {
        FirebaseCrashlytics.getInstance().apply {
            setCustomKey("device_model", Build.MODEL)
            setCustomKey("os_version", Build.VERSION.SDK_INT.toString())
            setCustomKey("has_magnetic_sensor", hasMagneticSensor().toString())
        }
    }

    fun logNonFatal(tag: String, message: String, exception: Exception) {
        FirebaseCrashlytics.getInstance().apply {
            log("$tag: $message")
            recordException(exception)
        }
    }
}
```

### 2.4 알림 기준

| 지표 | 알림 기준 | 알림 채널 | 대응 시간 |
|------|----------|----------|----------|
| 크래시율 (24h) | > 1% | Slack + 이메일 | 즉시 확인 |
| 크래시율 (24h) | > 3% | Slack + 이메일 + 전화 | 1시간 내 |
| 새 크래시 유형 | 발생 즉시 | Slack | 4시간 내 분류 |
| 특정 크래시 급증 | 동일 이슈 100회+ / 시간 | Slack | 2시간 내 |
| ANR 비율 (24h) | > 0.5% | Slack + 이메일 | 즉시 확인 |

### 2.5 크래시 대응 프로세스

```
1. 알림 수신
   │
2. 심각도 판정
   ├─ P0 (크래시율 3%+, ANR): 즉시 대응
   ├─ P1 (크래시율 1~3%): 24시간 내 대응
   └─ P2/P3: 다음 릴리스 일정에 포함
   │
3. 원인 분석
   ├─ Crashlytics 스택 트레이스 확인
   ├─ 영향 기기/OS 패턴 확인
   ├─ 커스텀 키 (scan_type 등) 확인
   └─ 재현 시도
   │
4. 수정
   ├─ P0: hotfix 브랜치 → 긴급 배포
   └─ P1+: 정규 브랜치 → 다음 릴리스
   │
5. 검증
   ├─ 수정 후 크래시 재발 모니터링
   └─ 48시간 동안 동일 크래시 0건 확인
   │
6. 회고
   └─ 근본 원인 문서화 + 재발 방지책
```

### 2.6 크래시율 목표

| 기간 | 목표 크래시율 | 목표 ANR 비율 |
|------|-------------|-------------|
| Alpha | < 5% | < 2% |
| Beta | < 2% | < 1% |
| GA 출시 | < 1% | < 0.5% |
| GA 3개월 후 | < 0.5% | < 0.3% |
| GA 6개월 후 | < 0.3% | < 0.2% |

---

## 3. 성능 모니터링 (Firebase Performance)

### 3.1 설정

```kotlin
// build.gradle.kts (app)
dependencies {
    implementation("com.google.firebase:firebase-perf-ktx")
}

// build.gradle.kts (project)
plugins {
    id("com.google.firebase.firebase-perf") version "1.4.2"
}
```

### 3.2 자동 수집 메트릭

Firebase Performance가 자동으로 수집하는 항목:

| 메트릭 | 설명 | 목표 |
|--------|------|------|
| 앱 시작 시간 (콜드) | 프로세스 시작 ~ 첫 프레임 | < 2초 |
| 앱 시작 시간 (웜) | 백그라운드 복귀 ~ 첫 프레임 | < 1초 |
| 화면 렌더링 (느린 프레임) | 16ms 초과 프레임 비율 | < 5% |
| 화면 렌더링 (멈춘 프레임) | 700ms 초과 프레임 비율 | < 1% |
| HTTP 요청 시간 | 네트워크 요청 지연 | < 3초 |

### 3.3 커스텀 트레이스

```kotlin
// 커스텀 성능 트레이스 정의
object PerfTraces {

    // Quick Scan 전체 소요 시간
    fun traceQuickScan(): Trace {
        return Firebase.performance.newTrace("quick_scan_duration").apply {
            putAttribute("scan_type", "quick")
        }
    }

    // Full Scan 전체 소요 시간
    fun traceFullScan(): Trace {
        return Firebase.performance.newTrace("full_scan_duration").apply {
            putAttribute("scan_type", "full")
        }
    }

    // 개별 레이어 소요 시간
    fun traceLayer(layerName: String): Trace {
        return Firebase.performance.newTrace("layer_${layerName}_duration")
    }

    // OUI DB 로드 시간
    fun traceOuiDbLoad(): Trace {
        return Firebase.performance.newTrace("oui_db_load")
    }

    // PDF 생성 시간
    fun tracePdfGeneration(): Trace {
        return Firebase.performance.newTrace("pdf_generation")
    }

    // Room DB 쿼리 시간
    fun traceDbQuery(queryName: String): Trace {
        return Firebase.performance.newTrace("db_query_$queryName")
    }
}
```

### 3.4 사용 예시

```kotlin
class RunQuickScanUseCase @Inject constructor(
    private val wifiScanner: WifiScanner,
    private val ouiDatabase: OuiDatabase,
    private val portScanner: PortScanner,
    private val riskCalculator: RiskCalculator
) {
    suspend operator fun invoke(): ScanResult {
        val trace = PerfTraces.traceQuickScan()
        trace.start()

        try {
            val devices = wifiScanner.scan()
            trace.incrementMetric("devices_found", devices.size.toLong())

            val analyzed = ouiDatabase.matchAll(devices)
            val result = riskCalculator.calculate(analyzed)

            trace.putAttribute("risk_level", result.riskLevel.name)
            return result
        } finally {
            trace.stop()
        }
    }
}
```

### 3.5 성능 목표 및 알림

| 트레이스 | P50 목표 | P90 목표 | P99 목표 | 알림 기준 |
|---------|---------|---------|---------|----------|
| quick_scan_duration | < 15초 | < 25초 | < 30초 | P90 > 30초 |
| full_scan_duration | < 90초 | < 150초 | < 180초 | P90 > 180초 |
| layer_wifi_duration | < 10초 | < 20초 | < 25초 | P90 > 25초 |
| layer_lens_duration | < 60초 | < 90초 | < 120초 | P90 > 120초 |
| layer_emf_duration | < 30초 | < 45초 | < 60초 | P90 > 60초 |
| oui_db_load | < 50ms | < 80ms | < 100ms | P90 > 100ms |
| pdf_generation | < 500ms | < 1초 | < 2초 | P90 > 2초 |
| db_query_* | < 30ms | < 50ms | < 100ms | P90 > 100ms |

### 3.6 프레임 드롭 모니터링

| 화면 | 느린 프레임 목표 | 멈춘 프레임 목표 |
|------|---------------|---------------|
| HomeScreen | < 3% | < 0.5% |
| QuickScanScreen | < 5% | < 1% |
| FullScanScreen | < 5% | < 1% |
| MagneticScreen (그래프) | < 8% | < 2% |
| LensFinderScreen (카메라) | < 10% | < 3% |
| ReportListScreen | < 3% | < 0.5% |

---

## 4. 사용 분석 (Firebase Analytics)

### 4.1 이벤트 설계 원칙

- **행동 기반**: 사용자 행동을 추적하되, 결과 내용(위험도 등)은 추적하지 않음
- **PII 제외**: 기기 정보, 발견 기기 MAC 등 개인 식별 가능 데이터 미포함
- **최소 수집**: 비즈니스 의사 결정에 필요한 최소한의 이벤트만

### 4.2 이벤트 목록

#### 핵심 이벤트

| 이벤트 이름 | 설명 | 파라미터 |
|------------|------|---------|
| `app_open` | 앱 실행 | `source` (organic/push/deep_link) |
| `onboarding_complete` | 온보딩 완료 | `step_count` |
| `onboarding_skip` | 온보딩 스킵 | `skipped_at_step` |

#### 스캔 이벤트

| 이벤트 이름 | 설명 | 파라미터 |
|------------|------|---------|
| `scan_start` | 스캔 시작 | `scan_type` (quick/full/lens/ir/emf) |
| `scan_complete` | 스캔 완료 | `scan_type`, `duration_sec`, `risk_level` |
| `scan_cancel` | 스캔 취소 | `scan_type`, `cancelled_at_sec` |
| `scan_error` | 스캔 에러 | `scan_type`, `error_type` |
| `scan_permission_denied` | 권한 거부 | `permission_type` |

#### 결과 이벤트

| 이벤트 이름 | 설명 | 파라미터 |
|------------|------|---------|
| `risk_detected` | 위험 감지 (40+) | `risk_level` (주의/위험/매우위험) |
| `result_view` | 결과 화면 조회 | `scan_type` |
| `result_share` | 결과 공유 | `share_method` |
| `full_scan_prompted` | Full Scan 유도 표시 | - |
| `full_scan_accepted` | Full Scan 유도 수락 | - |

#### 리포트 이벤트

| 이벤트 이름 | 설명 | 파라미터 |
|------------|------|---------|
| `report_saved` | 리포트 저장 | `scan_type` |
| `report_viewed` | 리포트 조회 | - |
| `report_pdf_exported` | PDF 내보내기 | - |
| `report_deleted` | 리포트 삭제 | - |

#### 체크리스트 이벤트

| 이벤트 이름 | 설명 | 파라미터 |
|------------|------|---------|
| `checklist_started` | 체크리스트 시작 | `checklist_type` (숙소/화장실/사무실) |
| `checklist_completed` | 체크리스트 완료 | `checklist_type`, `checked_count` |
| `checklist_item_checked` | 항목 체크 | `item_index` |

#### 프리미엄 이벤트

| 이벤트 이름 | 설명 | 파라미터 |
|------------|------|---------|
| `premium_view` | 프리미엄 화면 조회 | `source` (report_limit/pdf/ad) |
| `premium_subscribe` | 프리미엄 구독 | `plan` (monthly) |
| `premium_cancel` | 프리미엄 해지 | `reason` |

#### 기타 이벤트

| 이벤트 이름 | 설명 | 파라미터 |
|------------|------|---------|
| `settings_changed` | 설정 변경 | `setting_name`, `new_value` |
| `sensitivity_changed` | EMF 감도 변경 | `sensitivity` (sensitive/normal/stable) |
| `disclaimer_viewed` | 면책 조항 조회 | - |
| `feedback_submitted` | 피드백 제출 | - |
| `false_positive_reported` | 오탐 신고 | `scan_type` |

### 4.3 이벤트 로깅 구현

```kotlin
object AnalyticsLogger {
    private val analytics = Firebase.analytics

    fun logScanStart(scanType: ScanType) {
        analytics.logEvent("scan_start") {
            param("scan_type", scanType.name.lowercase())
        }
    }

    fun logScanComplete(scanType: ScanType, durationSec: Long, riskLevel: RiskLevel) {
        analytics.logEvent("scan_complete") {
            param("scan_type", scanType.name.lowercase())
            param("duration_sec", durationSec)
            param("risk_level", riskLevel.name.lowercase())
        }
    }

    fun logRiskDetected(riskLevel: RiskLevel) {
        if (riskLevel.score >= 40) {
            analytics.logEvent("risk_detected") {
                param("risk_level", riskLevel.name.lowercase())
            }
        }
    }

    fun logReportSaved(scanType: ScanType) {
        analytics.logEvent("report_saved") {
            param("scan_type", scanType.name.lowercase())
        }
    }
}
```

### 4.4 퍼널 분석

#### 핵심 퍼널: 설치 → 프리미엄

```
설치 (Install)
  │ 목표: 100%
  ▼
앱 실행 (app_open)
  │ 목표: 80%
  ▼
온보딩 완료 (onboarding_complete)
  │ 목표: 70%
  ▼
첫 Quick Scan (scan_start, first_time)
  │ 목표: 50%
  ▼
Quick Scan 완료 (scan_complete)
  │ 목표: 40%
  ▼
Full Scan 시도 (scan_start, full)
  │ 목표: 20%
  ▼
리포트 저장 (report_saved)
  │ 목표: 15%
  ▼
프리미엄 화면 조회 (premium_view)
  │ 목표: 5%
  ▼
프리미엄 구독 (premium_subscribe)
  │ 목표: 2%
```

#### 스캔 완료 퍼널

```
scan_start
  │
  ├─ scan_complete  (목표: 85%+)
  ├─ scan_cancel    (목표: < 10%)
  └─ scan_error     (목표: < 5%)
```

### 4.5 사용자 속성

| 속성 | 설명 | 예시 |
|------|------|------|
| os_version | Android 버전 | "14" |
| device_model | 기기 모델 | "SM-S921N" |
| app_version | 앱 버전 | "1.0.0" |
| language | 앱 언어 | "ko" |
| is_premium | 프리미엄 여부 | "true" / "false" |
| total_scans | 누적 스캔 횟수 | "15" |
| first_scan_date | 첫 스캔 날짜 | "2026-04-15" |

### 4.6 코호트 분석

| 코호트 | 정의 | 추적 지표 |
|--------|------|----------|
| 신규 사용자 | 설치 후 7일 이내 | 첫 스캔률, 리텐션 |
| 활성 사용자 | 주 1회+ 스캔 | 스캔 빈도, 유형 분포 |
| 파워 유저 | 주 3회+ 스캔 | Full Scan 비율, 리포트율 |
| 이탈 사용자 | 30일+ 미접속 | 이탈 직전 행동 |
| 프리미엄 사용자 | 구독 중 | LTV, 사용 패턴 |

---

## 5. 앱 크기 모니터링

### 5.1 크기 목표

| 항목 | 목표 | 경고 |
|------|------|------|
| APK 크기 | < 25MB | > 30MB |
| AAB 다운로드 크기 | < 15MB | > 20MB |
| OUI DB (JSON) | < 500KB | > 1MB |
| 앱 설치 크기 | < 50MB | > 70MB |

### 5.2 크기 트래킹

```bash
# 빌드 후 APK 크기 확인 (CI에서 실행)
APK_SIZE=$(stat -f%z app/build/outputs/apk/release/app-release.apk)
echo "APK Size: $((APK_SIZE / 1024 / 1024))MB"

# 30MB 초과 시 경고
if [ $APK_SIZE -gt 31457280 ]; then
  echo "WARNING: APK size exceeds 30MB threshold"
  exit 1
fi
```

### 5.3 크기 최적화 전략

| 전략 | 효과 |
|------|------|
| R8 (ProGuard) 활성화 | 코드 30~50% 축소 |
| shrinkResources | 미사용 리소스 제거 |
| WebP 이미지 변환 | 이미지 30~50% 축소 |
| AAB 분할 (ABI/언어) | 기기별 최적 크기 |
| OUI DB 압축 (gzip) | DB 60~70% 축소 |

---

## 6. ANR 모니터링

### 6.1 ANR 정의

- 입력 이벤트 5초 이상 무응답
- BroadcastReceiver 10초 이상 무응답
- Service 전경 20초 이상 무응답

### 6.2 ANR 위험 지점

| 기능 | ANR 위험 | 예방 |
|------|---------|------|
| Wi-Fi 스캔 | 네트워크 I/O | Dispatchers.IO |
| 포트 스캔 | 소켓 타임아웃 | 코루틴 + 타임아웃 |
| ARP 파싱 | 파일 I/O | Dispatchers.IO |
| OUI DB 로드 | 대용량 JSON 파싱 | 앱 시작 시 백그라운드 로드 |
| 자기장 센서 | 고빈도 콜백 | Flow 버퍼링 |
| 카메라 프레임 | 이미지 처리 | Dispatchers.Default |
| PDF 생성 | 렌더링 | Dispatchers.IO |
| Room 쿼리 | DB I/O | Dispatchers.IO |

### 6.3 ANR 방지 패턴

```kotlin
// 메인 스레드 차단 감지 (Debug 빌드)
class SearCamApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .penaltyDeath()
                    .build()
            )
        }
    }
}
```

### 6.4 ANR 목표

| 기간 | ANR 비율 목표 |
|------|-------------|
| Alpha | < 2% |
| Beta | < 1% |
| GA | < 0.5% |
| GA 3개월 | < 0.3% |

---

## 7. 대시보드 설계

### 7.1 운영 대시보드 (일별 확인)

```
┌─────────────────────────────────────────────────────┐
│                SearCam 운영 대시보드                  │
├─────────────────────────────────────────────────────┤
│                                                      │
│  [ 핵심 지표 ]                                       │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐          │
│  │ 크래시율  │  │ ANR 비율  │  │ DAU      │          │
│  │ 0.42%    │  │ 0.15%    │  │ 1,523    │          │
│  │ ✅ 목표내 │  │ ✅ 목표내 │  │ ↑12%    │          │
│  └──────────┘  └──────────┘  └──────────┘          │
│                                                      │
│  [ 스캔 현황 (24h) ]                                │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐          │
│  │ Quick    │  │ Full     │  │ 완료율   │          │
│  │ 2,341    │  │ 567      │  │ 87%      │          │
│  └──────────┘  └──────────┘  └──────────┘          │
│                                                      │
│  [ 성능 (P90) ]                                      │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐          │
│  │ 앱 시작  │  │ Quick    │  │ Full     │          │
│  │ 1.8s     │  │ 22s      │  │ 142s     │          │
│  │ ✅       │  │ ✅       │  │ ✅       │          │
│  └──────────┘  └──────────┘  └──────────┘          │
│                                                      │
│  [ 크래시 Top 5 ]                                    │
│  1. NPE in PortScanner.kt:45  (23건)               │
│  2. OOM in LensDetector.kt:120 (12건)              │
│  3. SecurityException WiFi (8건)                    │
│  4. IllegalState ScanVM (5건)                       │
│  5. IOException Network (3건)                       │
│                                                      │
│  [ 롤아웃 상태 ]                                     │
│  v1.0.1: 50% 롤아웃 (Day 5) → 크래시율 0.3% ✅    │
│                                                      │
└─────────────────────────────────────────────────────┘
```

### 7.2 비즈니스 대시보드 (주간 확인)

```
┌─────────────────────────────────────────────────────┐
│              SearCam 비즈니스 대시보드                │
├─────────────────────────────────────────────────────┤
│                                                      │
│  [ 성장 지표 ]                                       │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐          │
│  │ 총 설치  │  │ WAU      │  │ 평점     │          │
│  │ 12,450   │  │ 3,200    │  │ 4.2 ★   │          │
│  │ ↑8%      │  │ ↑15%     │  │ ─        │          │
│  └──────────┘  └──────────┘  └──────────┘          │
│                                                      │
│  [ 퍼널 ]                                            │
│  설치 → 첫 스캔: 52% ↑3%                            │
│  Quick → Full: 24% ↑2%                              │
│  스캔 → 리포트: 18% ─                               │
│  리포트 → 프리미엄: 3.2% ↑0.5%                      │
│                                                      │
│  [ 리텐션 ]                                          │
│  D1: 45%  D7: 28%  D30: 15%                        │
│                                                      │
│  [ 스캔 유형 분포 ]                                  │
│  Quick: 68%  Full: 18%  렌즈: 8%  IR: 4%  EMF: 2% │
│                                                      │
│  [ 오탐 신고율 ]                                     │
│  이번 주: 8.5% (목표: 10% 이하) ✅                  │
│                                                      │
│  [ 수익 ]                                            │
│  프리미엄 구독: 42명 (₩121,800)                     │
│  광고 수익: ₩98,000                                 │
│  월 합계: ₩219,800                                  │
│                                                      │
└─────────────────────────────────────────────────────┘
```

### 7.3 대시보드 구현

| 도구 | 용도 | 데이터 소스 |
|------|------|-----------|
| Firebase Console | 크래시, 성능, 이벤트 | Crashlytics, Performance, Analytics |
| Google Analytics | 퍼널, 코호트, 리텐션 | Firebase Analytics 연동 |
| Google Data Studio | 커스텀 대시보드 | BigQuery Export |
| Slack 봇 | 알림 | Firebase Cloud Functions |

### 7.4 알림 채널 설정

| 알림 유형 | 채널 | 빈도 |
|----------|------|------|
| 크래시율 초과 | Slack #searcam-alerts | 실시간 |
| ANR 초과 | Slack #searcam-alerts | 실시간 |
| 새 크래시 유형 | Slack #searcam-crashes | 실시간 |
| 일일 요약 | Slack #searcam-daily | 매일 오전 9시 |
| 주간 리포트 | 이메일 | 매주 월요일 |
| 롤아웃 상태 | Slack #searcam-release | 단계 변경 시 |

---

## 8. Firebase Remote Config

### 8.1 원격 설정 항목

| 키 | 기본값 | 용도 |
|----|--------|------|
| `emf_threshold_normal` | 8 | EMF 보통 감도 임계값 (uT) |
| `emf_threshold_sensitive` | 3 | EMF 민감 감도 임계값 |
| `emf_threshold_stable` | 20 | EMF 안정 감도 임계값 |
| `quick_scan_timeout_sec` | 30 | Quick Scan 타임아웃 |
| `full_scan_timeout_sec` | 180 | Full Scan 타임아웃 |
| `weight_wifi` | 0.50 | Wi-Fi 레이어 가중치 |
| `weight_lens` | 0.35 | 렌즈 레이어 가중치 |
| `weight_emf` | 0.15 | EMF 레이어 가중치 |
| `free_report_limit` | 10 | 무료 리포트 저장 한도 |
| `enable_lens_detector` | true | 렌즈 감지 기능 활성화 |
| `oui_db_version` | "1.0" | OUI DB 최소 버전 |

### 8.2 A/B 테스트 항목

| 실험 | 변형 A | 변형 B | 목표 지표 |
|------|--------|--------|----------|
| EMF 기본 임계값 | 8 uT | 10 uT | 오탐 신고율 |
| Quick Scan 유도 문구 | "Full Scan 하기" | "정밀 점검" | Full Scan 전환율 |
| 프리미엄 가격 | ₩2,900/월 | ₩3,900/월 | 전환율 x ARPU |

---

## 9. 인시던트 관리

### 9.1 인시던트 심각도

| 등급 | 기준 | SLA |
|------|------|-----|
| SEV-1 | 크래시율 > 5% 또는 핵심 기능 불가 | 1시간 내 대응 시작 |
| SEV-2 | 크래시율 > 2% 또는 주요 기능 장애 | 4시간 내 대응 시작 |
| SEV-3 | 크래시율 > 1% 또는 부분 기능 장애 | 24시간 내 대응 시작 |
| SEV-4 | 경미한 이슈, 사용자 영향 미미 | 다음 스프린트 |

### 9.2 인시던트 대응 체크리스트

```
[ ] 인시던트 선언 (Slack 채널)
[ ] 심각도 판정 (SEV-1~4)
[ ] 담당자 지정
[ ] 영향 범위 파악 (기기/OS/버전)
[ ] 근본 원인 분석
[ ] 임시 조치 (롤아웃 중단 등)
[ ] 영구 수정 (핫픽스)
[ ] 검증 (배포 후 모니터링)
[ ] 포스트모템 작성
[ ] 재발 방지책 적용
```

### 9.3 포스트모템 템플릿

```
# 인시던트 포스트모템

## 개요
- 일시: YYYY-MM-DD HH:MM ~ HH:MM
- 심각도: SEV-X
- 영향: (영향 받은 사용자 수/비율)

## 타임라인
- HH:MM: 알림 수신
- HH:MM: 인시던트 선언
- HH:MM: 근본 원인 식별
- HH:MM: 핫픽스 배포
- HH:MM: 정상 확인

## 근본 원인
(기술적 원인 상세)

## 영향
(사용자 영향, 비즈니스 영향)

## 조치
- 임시 조치: (내용)
- 영구 수정: (내용)

## 재발 방지
- [ ] (방지책 1)
- [ ] (방지책 2)

## 교훈
(이번 인시던트에서 배운 점)
```

---

*본 모니터링 계획서는 Phase 1 Android MVP 기준이며, Phase 2 (iOS) 추가 시 Firebase iOS SDK 설정을 포함하여 업데이트합니다.*
*버전 v1.0 -- 2026-04-03*
