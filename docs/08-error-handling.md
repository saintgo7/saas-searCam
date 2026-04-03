# SearCam 에러 처리 전략서

> 버전: v1.0
> 작성일: 2026-04-03
> 기반: project-plan.md v3.1, 04-system-architecture.md, 06-api-design.md

---

## 1. 에러 처리 철학

SearCam은 **안전 도구**이다. 에러로 인해 스캔이 완전히 실패하는 상황은 최대한 피해야 한다.
핵심 원칙은 다음과 같다.

```
┌──────────────────────────────────────────────────────┐
│                   에러 처리 원칙                      │
│                                                       │
│  1. 크래시는 절대 허용하지 않는다                     │
│  2. 하나의 센서 실패가 전체 스캔을 중단시키지 않는다  │
│  3. 사용자에게 기술 메시지를 그대로 노출하지 않는다   │
│  4. 가능한 대체 경로(fallback)를 제공한다            │
│  5. 에러 발생 시에도 부분 결과를 보여준다            │
└──────────────────────────────────────────────────────┘
```

---

## 2. 에러 분류 체계

### 2.1 에러 카테고리

```
SearCam Error Taxonomy
│
├── E1xxx: 센서 에러 ──────── 하드웨어/센서 접근 실패
│   ├── E10xx: Wi-Fi/네트워크 센서
│   ├── E11xx: 자기장 센서
│   ├── E12xx: 카메라 (렌즈 감지)
│   └── E13xx: 카메라 (IR 감지)
│
├── E2xxx: 네트워크 에러 ──── 네트워크 통신 실패
│   ├── E20xx: Wi-Fi 연결
│   ├── E21xx: 포트 스캔
│   └── E22xx: mDNS/SSDP 탐색
│
├── E3xxx: 권한 에러 ──────── 런타임 권한 거부
│   ├── E30xx: 위치 권한
│   ├── E31xx: 카메라 권한
│   └── E32xx: 저장소 권한
│
├── E4xxx: 시스템 에러 ────── 앱 내부 오류
│   ├── E40xx: 타임아웃
│   ├── E41xx: 데이터베이스 (Room)
│   ├── E42xx: PDF 생성
│   └── E43xx: 메모리 부족
│
└── E5xxx: 비즈니스 에러 ──── 제한/정책 관련
    ├── E50xx: 프리미엄 기능 제한
    └── E51xx: 데이터 보존 한도
```

---

## 3. 에러 코드 체계

### 3.1 센서 에러 (E1xxx)

| 코드 | 이름 | 기술 메시지 | 심각도 |
|------|------|-----------|--------|
| E1001 | WIFI_NOT_CONNECTED | Wi-Fi state: DISCONNECTED | MEDIUM |
| E1002 | WIFI_SCAN_FAILED | WifiManager.startScan() returned false | MEDIUM |
| E1003 | ARP_READ_FAILED | Cannot read /proc/net/arp | LOW |
| E1004 | WIFI_SSID_SCAN_FAILED | WifiManager.getScanResults() failed | LOW |
| E1101 | MAGNETOMETER_UNAVAILABLE | TYPE_MAGNETIC_FIELD sensor not found | MEDIUM |
| E1102 | MAGNETOMETER_UNRELIABLE | Sensor accuracy: UNRELIABLE | LOW |
| E1103 | CALIBRATION_FAILED | Noise floor > 30uT during calibration | LOW |
| E1104 | SENSOR_EVENT_STOPPED | No sensor events for 5 seconds | MEDIUM |
| E1201 | REAR_CAMERA_UNAVAILABLE | CameraX rear camera bind failed | HIGH |
| E1202 | FLASH_UNAVAILABLE | Camera flash not available | MEDIUM |
| E1203 | CAMERA_INIT_FAILED | CameraX initialization failed | HIGH |
| E1204 | FRAME_ANALYSIS_ERROR | ImageAnalysis callback exception | MEDIUM |
| E1301 | FRONT_CAMERA_UNAVAILABLE | CameraX front camera bind failed | HIGH |
| E1302 | AMBIENT_TOO_BRIGHT | Ambient light > 10 lux for IR scan | LOW |

### 3.2 네트워크 에러 (E2xxx)

| 코드 | 이름 | 기술 메시지 | 심각도 |
|------|------|-----------|--------|
| E2001 | WIFI_DISCONNECTED_DURING_SCAN | Wi-Fi disconnected mid-scan | MEDIUM |
| E2002 | NO_NETWORK_ACCESS | ConnectivityManager: no active network | MEDIUM |
| E2101 | PORT_SCAN_TIMEOUT | Socket connect timeout on port {port} | LOW |
| E2102 | PORT_SCAN_REFUSED | Connection refused on {ip}:{port} | LOW |
| E2103 | PORT_SCAN_UNREACHABLE | Host {ip} unreachable | LOW |
| E2201 | MDNS_DISCOVERY_TIMEOUT | NsdManager discovery timeout (10s) | LOW |
| E2202 | SSDP_SEND_FAILED | UDP multicast send failed | LOW |

### 3.3 권한 에러 (E3xxx)

| 코드 | 이름 | 기술 메시지 | 심각도 |
|------|------|-----------|--------|
| E3001 | LOCATION_PERMISSION_DENIED | ACCESS_FINE_LOCATION denied | HIGH |
| E3002 | LOCATION_PERMANENTLY_DENIED | Location permission permanently denied | HIGH |
| E3003 | LOCATION_SERVICE_DISABLED | GPS/Location service OFF | MEDIUM |
| E3101 | CAMERA_PERMISSION_DENIED | CAMERA permission denied | HIGH |
| E3102 | CAMERA_PERMANENTLY_DENIED | Camera permission permanently denied | HIGH |
| E3201 | STORAGE_PERMISSION_DENIED | WRITE_EXTERNAL_STORAGE denied | MEDIUM |

### 3.4 시스템 에러 (E4xxx)

| 코드 | 이름 | 기술 메시지 | 심각도 |
|------|------|-----------|--------|
| E4001 | SCAN_TIMEOUT_LAYER1 | Wi-Fi scan timeout (30s) | MEDIUM |
| E4002 | SCAN_TIMEOUT_LAYER2 | Lens/IR scan timeout (120s) | MEDIUM |
| E4003 | SCAN_TIMEOUT_LAYER3 | EMF scan timeout (120s) | LOW |
| E4101 | DB_INSERT_FAILED | Room insert exception | MEDIUM |
| E4102 | DB_QUERY_FAILED | Room query exception | MEDIUM |
| E4103 | DB_MIGRATION_FAILED | Room migration failed | CRITICAL |
| E4201 | PDF_GENERATION_FAILED | iText PDF creation exception | LOW |
| E4202 | PDF_STORAGE_FAILED | Cannot write PDF to storage | LOW |
| E4301 | OUT_OF_MEMORY | OOM during frame analysis | HIGH |

### 3.5 비즈니스 에러 (E5xxx)

| 코드 | 이름 | 기술 메시지 | 심각도 |
|------|------|-----------|--------|
| E5001 | PREMIUM_REQUIRED_PDF | PDF export requires premium | INFO |
| E5002 | PREMIUM_REQUIRED_UNLIMITED | Unlimited reports requires premium | INFO |
| E5101 | REPORT_LIMIT_REACHED | Free tier: 10 reports limit | INFO |

---

## 4. 사용자 메시지 매핑

### 4.1 에러 -> 사용자 메시지 변환 테이블

| 에러 코드 | 사용자 메시지 (한국어) | 복구 버튼 |
|----------|---------------------|----------|
| E1001 | "Wi-Fi에 연결되지 않았습니다. Wi-Fi에 연결 후 다시 시도해주세요." | [Wi-Fi 설정] [Wi-Fi 없이 계속] |
| E1002 | "Wi-Fi 스캔에 실패했습니다. 잠시 후 다시 시도해주세요." | [다시 시도] |
| E1003 | "네트워크 기기 목록을 가져올 수 없습니다. 서비스 탐색으로 계속합니다." | (자동 대체) |
| E1101 | "이 기기는 자기장 센서를 지원하지 않습니다. 다른 방법으로 계속합니다." | (자동 스킵) |
| E1102 | "자기장 센서 정확도가 낮습니다. 결과가 부정확할 수 있습니다." | [계속] [스킵] |
| E1103 | "주변 자기장 간섭이 심합니다. 다른 위치에서 캘리브레이션해주세요." | [다시 시도] [기본값 사용] |
| E1104 | "자기장 센서가 응답하지 않습니다. 다시 시도합니다." | (자동 재시도) |
| E1201 | "후면 카메라를 사용할 수 없습니다. 렌즈 감지를 건너뜁니다." | (자동 스킵) |
| E1202 | "플래시를 사용할 수 없습니다. IR 감지만 실행합니다." | (자동 대체) |
| E1203 | "카메라 초기화에 실패했습니다. 다른 앱이 카메라를 사용 중인지 확인해주세요." | [다시 시도] |
| E1301 | "전면 카메라를 사용할 수 없습니다. IR 감지를 건너뜁니다." | (자동 스킵) |
| E1302 | "IR 감지는 어두운 환경에서만 가능합니다. 방을 어둡게 해주세요." | [어둡게 했어요] [건너뛰기] |
| E3001 | "Wi-Fi 스캔에 위치 권한이 필요합니다." | [권한 허용] [건너뛰기] |
| E3002 | "위치 권한이 영구 거부되었습니다. 설정에서 직접 허용해주세요." | [설정 이동] |
| E3101 | "렌즈 감지에 카메라 권한이 필요합니다." | [권한 허용] [건너뛰기] |
| E3102 | "카메라 권한이 영구 거부되었습니다. 설정에서 직접 허용해주세요." | [설정 이동] |
| E4001 | "Wi-Fi 스캔 시간이 초과되었습니다. 현재까지의 결과를 표시합니다." | [결과 보기] |
| E4101 | "결과 저장에 실패했습니다. 다시 시도해주세요." | [다시 시도] |
| E4301 | "메모리가 부족합니다. 다른 앱을 종료 후 다시 시도해주세요." | [다시 시도] |
| E5001 | "PDF 내보내기는 프리미엄 기능입니다." | [프리미엄 안내] [닫기] |
| E5101 | "무료 사용자는 최근 10건까지 저장됩니다. 가장 오래된 리포트가 삭제됩니다." | [확인] [프리미엄] |

### 4.2 메시지 표시 방식

| 심각도 | 표시 방식 | 예시 |
|--------|----------|------|
| CRITICAL | 전체 화면 에러 + 앱 재시작 안내 | DB 마이그레이션 실패 |
| HIGH | 다이얼로그 (모달) | 카메라 초기화 실패 |
| MEDIUM | 스낵바 (하단) + 복구 버튼 | Wi-Fi 미연결 |
| LOW | 인라인 배너 (해당 섹션 내) | ARP 읽기 실패 |
| INFO | 토스트 또는 배너 | 프리미엄 기능 안내 |

---

## 5. 복구 전략

### 5.1 자동 재시도

| 에러 코드 | 재시도 횟수 | 간격 | 조건 |
|----------|-----------|------|------|
| E1002 (Wi-Fi 스캔 실패) | 3회 | 2초 | 마지막 실패 시 사용자 안내 |
| E1104 (센서 이벤트 중단) | 3회 | 1초 | 센서 재등록 |
| E1203 (카메라 초기화 실패) | 3회 | 1초 | CameraX unbind -> rebind |
| E2101 (포트 스캔 타임아웃) | 1회 | 0초 | 타임아웃 2초 -> 3초로 증가 |
| E4101 (DB 삽입 실패) | 2회 | 500ms | 트랜잭션 재시도 |

### 5.2 대체 경로 (Fallback)

```
┌─ 대체 경로 매트릭스 ───────────────────────────────────────┐
│                                                              │
│  [E1003] ARP 읽기 실패                                      │
│  ├── 대체: mDNS/SSDP 탐색만으로 기기 발견                   │
│  └── 영향: 일부 기기 누락 가능 (mDNS 미지원 기기)           │
│                                                              │
│  [E1202] 플래시 불가                                         │
│  ├── 대체: Stage A(Retroreflection) 스킵, Stage B(IR)만     │
│  └── 영향: 밝은 환경 렌즈 감지 불가                         │
│                                                              │
│  [E1302] 밝은 환경 (IR 불가)                                │
│  ├── 대체: Stage B 스킵, Stage A만 (밝은 환경 가능)         │
│  └── 영향: IR LED 카메라 탐지 불가                          │
│                                                              │
│  [E2201] mDNS 타임아웃                                      │
│  ├── 대체: ARP + 포트 스캔만으로 계속                       │
│  └── 영향: mDNS 서비스 광고 기기 누락                       │
│                                                              │
│  [E4001] Wi-Fi 스캔 타임아웃                                │
│  ├── 대체: 현재까지 수집된 부분 결과 사용                   │
│  └── 영향: 포트 스캔 미완료 기기 있을 수 있음               │
│                                                              │
│  [E4301] OOM (프레임 분석)                                  │
│  ├── 대체: 해상도 추가 축소 (720p -> 480p)                  │
│  └── 영향: 분석 정확도 약간 감소                            │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 5.3 사용자 안내 복구

| 에러 | 사용자 안내 행동 |
|------|---------------|
| 위치 권한 거부 | 권한 필요 이유 설명 + 설정 이동 버튼 |
| 카메라 권한 거부 | 권한 필요 이유 설명 + 설정 이동 버튼 |
| Wi-Fi 미연결 | Wi-Fi 설정 이동 + "Wi-Fi 없이 계속" 옵션 |
| 밝은 환경 (IR) | "방을 어둡게 해주세요" 안내 + 대기 |
| 캘리브레이션 실패 | "스마트폰을 공중에 들고 대기해주세요" 재안내 |

---

## 6. 센서 불가 시 Graceful Degradation

### 6.1 가용 센서 조합별 동작

```
┌─────────────────────────────────────────────────────────────┐
│                  Graceful Degradation 매트릭스               │
├──────────┬──────┬──────┬──────┬───────────────────────────┤
│ Wi-Fi    │ 렌즈 │  IR  │ EMF  │ 동작                      │
├──────────┼──────┼──────┼──────┼───────────────────────────┤
│    O     │  O   │  O   │  O   │ Full Scan (100% 기능)     │
│    O     │  O   │  X   │  O   │ 밝은환경 스캔 (IR 스킵)   │
│    O     │  X   │  X   │  O   │ Wi-Fi + EMF만 (렌즈 스킵) │
│    O     │  X   │  X   │  X   │ Wi-Fi만 (Quick Scan 동등) │
│    X     │  O   │  O   │  O   │ 오프라인 스캔 (렌즈+EMF)  │
│    X     │  O   │  X   │  O   │ 렌즈(Stage A) + EMF       │
│    X     │  X   │  O   │  O   │ IR + EMF (암실 전용)      │
│    X     │  X   │  X   │  O   │ EMF만 (최소 기능)         │
│    X     │  O   │  X   │  X   │ 렌즈만 (최소 기능)        │
│    X     │  X   │  X   │  X   │ 체크리스트만 안내 (스캔 불가)│
├──────────┴──────┴──────┴──────┴───────────────────────────┤
│ O = 사용 가능, X = 사용 불가                              │
└─────────────────────────────────────────────────────────────┘
```

### 6.2 센서별 가용성 확인 순서

```
앱 시작 시 (또는 스캔 시작 시):

1. Wi-Fi 확인
   ├── WifiManager.isWifiEnabled()
   ├── ConnectivityManager.activeNetwork
   └── 결과: wifiAvailable: Boolean

2. 카메라 확인
   ├── CameraManager.getCameraIdList()
   ├── 후면 카메라 존재 여부
   ├── 플래시 지원 여부
   ├── 전면 카메라 존재 여부
   └── 결과: rearCameraAvailable, flashAvailable, frontCameraAvailable

3. 자기장 센서 확인
   ├── SensorManager.getDefaultSensor(TYPE_MAGNETIC_FIELD)
   └── 결과: magnetometerAvailable: Boolean

4. 가용 모드 결정
   ├── Quick Scan: wifiAvailable 필요
   ├── Full Scan: 1개 이상 센서 가용
   ├── 렌즈 찾기: rearCameraAvailable + flashAvailable 필요
   ├── IR Only: frontCameraAvailable + 암실 필요
   └── EMF Only: magnetometerAvailable 필요
```

### 6.3 UI 표시 규칙

| 상황 | 홈 화면 표시 | 결과 화면 표시 |
|------|------------|-------------|
| 모든 센서 가용 | 모든 모드 활성화 | 전체 결과 표시 |
| Wi-Fi 미연결 | Quick Scan 비활성 + 안내 텍스트 | Layer 1 "미실행" 표시 |
| 카메라 권한 없음 | 렌즈/IR 모드에 잠금 아이콘 | Layer 2 "권한 필요" 표시 |
| 자력계 없음 | EMF 모드 비활성 + "미지원 기기" | Layer 3 "센서 미지원" 표시 |
| 전체 센서 불가 | "체크리스트로 육안 점검하세요" 안내 | 스캔 불가 안내 |

---

## 7. 크래시 방지 전략

### 7.1 전역 예외 핸들러

```
Application 레벨:
  Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
    1. Timber.e(exception, "Uncaught exception on ${thread.name}")
    2. 크래시 리포트 저장 (SharedPreferences)
    3. 사용자에게 "앱이 비정상 종료되었습니다" 안내 (다음 실행 시)
    4. Phase 2: Firebase Crashlytics에 전송
  }
```

### 7.2 Coroutine 예외 처리

```
CoroutineExceptionHandler 계층:

  viewModelScope (SupervisorJob 기본 포함)
    │
    ├── scanJob (SupervisorJob)
    │   ├── wifiScanJob   -> 실패해도 다른 Job 영향 없음
    │   ├── lensScanJob   -> 실패해도 다른 Job 영향 없음
    │   └── magneticJob   -> 실패해도 다른 Job 영향 없음
    │
    └── CoroutineExceptionHandler { _, exception ->
          Timber.e(exception, "Scan coroutine failed")
          _errorEvent.emit(ScanError.from(exception))
        }
```

### 7.3 위험 지점별 방어 코드

| 위험 지점 | 방어 방법 |
|----------|----------|
| CameraX 초기화 | try-catch + 3회 재시도 + 실패 시 레이어 스킵 |
| SensorManager 콜백 | null 체크 + 빈 이벤트 무시 |
| Room 트랜잭션 | try-catch + 2회 재시도 + 실패 시 메모리 결과만 표시 |
| JSON 파싱 (OUI DB) | try-catch + 기본값 반환 |
| PDF 생성 | try-catch + 사용자 안내 |
| 프레임 분석 (ImageProxy) | try-catch + proxy.close() 보장 |
| 네트워크 소켓 | withTimeout + 소켓 close() 보장 |
| 대용량 리스트 처리 | chunked() 사용 + OOM 방지 |

### 7.4 메모리 관리

```
카메라 프레임 처리:
  - ImageProxy는 분석 후 반드시 close()
  - conflate()로 프레임 밀림 방지
  - 분석용 Bitmap은 재사용 (object pool)

자기장 데이터:
  - Ring Buffer (size=200) 사용
  - 오래된 데이터 자동 폐기
  - 그래프 데이터는 최근 10초만 유지

네트워크 기기 목록:
  - 최대 255개 제한 (서브넷 범위)
  - 포트 스캔은 의심 기기만 (risk > 0.3)

OUI 데이터베이스:
  - 앱 시작 시 한 번만 로드
  - HashMap으로 O(1) 검색
  - 메모리 ~2MB
```

---

## 8. 로깅 전략 (Timber)

### 8.1 로깅 레벨

| 레벨 | 용도 | 예시 |
|------|------|------|
| VERBOSE | 센서 원시 데이터 (디버그 빌드만) | 자기장 매 샘플 |
| DEBUG | 스캔 단계 진행 | "ARP scan: found 7 devices" |
| INFO | 주요 이벤트 | "Full scan completed: score=72" |
| WARN | 비정상이지만 복구 가능 | "mDNS timeout, continuing with ARP only" |
| ERROR | 복구 불가능한 에러 | "Camera initialization failed after 3 retries" |

### 8.2 로깅 설정

```
Debug 빌드:
  - 모든 레벨 출력 (VERBOSE 이상)
  - Logcat 출력
  - 센서 원시 데이터 포함

Release 빌드:
  - WARN 이상만 출력
  - Logcat 미출력
  - Phase 2: Firebase Crashlytics로 WARN/ERROR 전송
  - 개인정보 마스킹 (MAC 주소: 28:57:BE:xx:xx:xx)
```

### 8.3 Timber 설정 코드 구조

```
SearCamApp.onCreate():
  if (BuildConfig.DEBUG) {
    Timber.plant(DebugTree())
  } else {
    Timber.plant(ReleaseTree())
    // Phase 2: Timber.plant(CrashlyticsTree())
  }

ReleaseTree:
  - WARN 이상만 로깅
  - MAC 주소 마스킹
  - IP 주소 마스킹
  - 사용자 입력 (메모, 위치명) 마스킹
```

### 8.4 로그 태그 규칙

| 모듈 | 태그 | 예시 |
|------|------|------|
| Wi-Fi 스캔 | `SearCam.Wifi` | `[SearCam.Wifi] ARP scan: 7 devices` |
| 렌즈 감지 | `SearCam.Lens` | `[SearCam.Lens] Suspect point at (0.3, 0.7)` |
| IR 감지 | `SearCam.IR` | `[SearCam.IR] IR point detected` |
| 자기장 | `SearCam.EMF` | `[SearCam.EMF] Delta: 25.3uT (CAUTION)` |
| 교차 검증 | `SearCam.Cross` | `[SearCam.Cross] Score: 72, Level: DANGER` |
| 데이터베이스 | `SearCam.DB` | `[SearCam.DB] Report saved: id=15` |
| 스캔 엔진 | `SearCam.Scan` | `[SearCam.Scan] Full scan started` |
| UI | `SearCam.UI` | `[SearCam.UI] Navigate to ResultScreen` |

---

## 9. 에러 처리 코드 패턴

### 9.1 UseCase 레벨

```
RunFullScanUseCase:
  flow {
    // Phase 1: Wi-Fi
    val layer1 = try {
      wifiScanRepo.scan()
    } catch (e: WifiNotConnectedException) {
      emit(FullScanState.Layer1Skipped(reason = "Wi-Fi 미연결"))
      null
    } catch (e: TimeoutCancellationException) {
      emit(FullScanState.Layer1Partial(currentResult))
      partialResult
    }

    // Phase 2: 렌즈 + EMF 병렬
    coroutineScope {
      val layer2Deferred = async {
        try {
          lensDetectionRepo.startRetroReflectionScan().toResult()
        } catch (e: CameraNotAvailableException) {
          Layer2Result.unavailable("카메라 불가")
        }
      }
      val layer3Deferred = async {
        try {
          magneticRepo.calibrate()
          magneticRepo.startMeasuring().toResult()
        } catch (e: SensorNotAvailableException) {
          Layer3Result.unavailable("자력계 없음")
        }
      }

      val layer2 = layer2Deferred.await()
      val layer3 = layer3Deferred.await()

      // Phase 3: 교차 검증 (null-safe)
      val result = calculateRisk(layer1, layer2, layer3)
      emit(FullScanState.Completed(result))
    }
  }
```

### 9.2 ViewModel 레벨

```
ScanViewModel:
  fun startFullScan(config: FullScanConfig) {
    scanJob?.cancel()
    scanJob = viewModelScope.launch(errorHandler) {
      runFullScan(config)
        .catch { error ->
          _uiState.value = ScanUiState.Error(
            error = ScanError.from(error),
            partialResult = _lastPartialResult
          )
        }
        .collect { state ->
          _uiState.value = state.toUiState()
        }
    }
  }

  private val errorHandler = CoroutineExceptionHandler { _, exception ->
    Timber.e(exception, "Scan failed unexpectedly")
    _uiState.value = ScanUiState.Error(
      error = ScanError.UnexpectedError(exception.message),
      partialResult = null
    )
  }
```

---

## 10. 에러 모니터링 계획

### 10.1 Phase 1 (로컬 모니터링)

| 항목 | 방법 |
|------|------|
| 크래시 | SharedPreferences에 마지막 크래시 기록 |
| 에러 빈도 | Timber 로그 (디버그 빌드) |
| 센서 가용성 | 로컬 통계 (어떤 센서가 자주 불가한지) |

### 10.2 Phase 2 (원격 모니터링)

| 항목 | 도구 |
|------|------|
| 크래시 리포트 | Firebase Crashlytics |
| 비정상 이벤트 | Firebase Analytics (custom event) |
| 에러 빈도 분석 | Crashlytics 대시보드 |
| 센서 호환성 맵 | 기기별 센서 가용성 통계 수집 (익명) |

---

*본 문서는 project-plan.md v3.1 기반으로 작성되었으며, Phase 1 (Android MVP) 에러 처리 전략을 정의합니다.*
*Phase 2에서 Firebase Crashlytics 연동 시 원격 모니터링 항목이 확장됩니다.*
