# SearCam 데이터 플로우 아키텍처

> 버전: v1.0
> 작성일: 2026-04-03
> 기반: project-plan.md v3.1, 04-system-architecture.md

---

## 1. 전체 데이터 플로우 개요

```
┌─────────────────────────────────────────────────────────────────┐
│                        데이터 흐름 전체도                        │
│                                                                  │
│  [하드웨어 센서]                                                 │
│       │                                                          │
│       ▼                                                          │
│  ┌──────────┐   Flow<Raw>    ┌────────────┐   Flow<Analyzed>    │
│  │  Sensor   │──────────────▶│  Analyzer   │──────────────────┐  │
│  │  Module   │               │  / Filter   │                  │  │
│  └──────────┘               └────────────┘                  │  │
│                                                              │  │
│                              ┌────────────┐   Flow<Domain>   │  │
│                              │ Repository  │◀─────────────────┘  │
│                              │   Impl     │                      │
│                              └─────┬──────┘                      │
│                                    │                              │
│                              ┌─────▼──────┐                      │
│                              │  UseCase    │                      │
│                              │ (교차검증)  │                      │
│                              └─────┬──────┘                      │
│                                    │                              │
│                              ┌─────▼──────┐                      │
│                              │ ViewModel   │                      │
│                              │ StateFlow   │                      │
│                              └─────┬──────┘                      │
│                                    │                              │
│                              ┌─────▼──────┐                      │
│                              │  Compose    │                      │
│                              │  Screen     │                      │
│                              └─────────────┘                      │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Kotlin Flow / Coroutine 기반 비동기 설계

### 2.1 Flow 스트림 타입 선택 기준

| 데이터 특성 | Flow 타입 | 사용처 |
|------------|----------|--------|
| 일회성 결과 (스캔 완료) | `suspend fun` | Wi-Fi 기기 목록 조회 |
| 연속 스트림 (센서 데이터) | `Flow<T>` | 자기장 20Hz 스트림 |
| 실시간 프레임 분석 | `Flow<T>` | 렌즈 감지 프레임별 결과 |
| UI 상태 | `StateFlow<T>` | ViewModel -> Composable |
| 일회성 이벤트 (에러, 네비게이션) | `SharedFlow<T>` | 에러 알림, 완료 이벤트 |
| 단계별 진행 상태 | `Flow<ScanProgress>` | 스캔 진행률 |

### 2.2 Coroutine Scope 설계

```
┌─ viewModelScope ─────────────────────────────────┐
│                                                    │
│  ┌─ scanJob (SupervisorJob) ──────────────────┐   │
│  │                                              │   │
│  │  ┌─ wifiScanJob ──────────┐                 │   │
│  │  │  Dispatchers.IO        │                 │   │
│  │  │  ARP + mDNS + 포트스캔  │                 │   │
│  │  └────────────────────────┘                 │   │
│  │                                              │   │
│  │  ┌─ lensScanJob ──────────┐                 │   │
│  │  │  Dispatchers.Default   │                 │   │
│  │  │  프레임 분석 루프       │                 │   │
│  │  └────────────────────────┘                 │   │
│  │                                              │   │
│  │  ┌─ magneticJob ──────────┐                 │   │
│  │  │  Dispatchers.Default   │                 │   │
│  │  │  20Hz 센서 스트림       │                 │   │
│  │  └────────────────────────┘                 │   │
│  │                                              │   │
│  └──────────────────────────────────────────────┘   │
│                                                      │
│  scanJob.cancel() -> 모든 자식 코루틴 일괄 취소      │
│  개별 Job 실패 -> SupervisorJob이므로 다른 Job 영향 없음 │
└──────────────────────────────────────────────────────┘
```

### 2.3 Flow 연산자 사용 패턴

| 연산자 | 용도 | 사용처 |
|--------|------|--------|
| `map` | 데이터 변환 | Raw -> Domain 모델 변환 |
| `filter` | 노이즈 제거 | 자기장 노이즈 필터링 |
| `debounce` | 빈도 제한 | UI 업데이트 100ms 단위 |
| `conflate` | 최신값 유지 | 프레임 분석 (밀림 방지) |
| `combine` | 복수 Flow 합류 | 교차 검증 (3개 레이어 합류) |
| `catch` | 에러 처리 | 센서 에러 -> 대체 경로 |
| `onStart` / `onCompletion` | 생명주기 | 진행률 시작/완료 처리 |
| `flowOn` | 디스패처 전환 | IO -> Default -> Main |
| `buffer` | 배압 관리 | 센서 데이터 버퍼링 |
| `sample` | 샘플링 | 고빈도 데이터 UI 반영 제한 |

---

## 3. Quick Scan 데이터 플로우

Quick Scan은 Wi-Fi 스캔만 수행하는 30초 이내 빠른 점검 모드이다.

### 3.1 시퀀스 다이어그램

```
사용자     HomeVM      QuickScanUC   WifiScanRepo   WifiScanner    OuiDB     PortScanner
  │          │             │              │              │            │            │
  │─ 탭 ──▶│             │              │              │            │            │
  │          │── run() ──▶│              │              │            │            │
  │          │             │── scan() ──▶│              │            │            │
  │          │             │              │── getArp()─▶│            │            │
  │          │             │              │◀─ arpList ──│            │            │
  │          │             │              │              │            │            │
  │          │             │              │── discover()▶│            │            │
  │          │             │              │◀─ mdnsList ─│            │            │
  │          │             │              │              │            │            │
  │          │             │              │── matchOui()────────────▶│            │
  │          │             │              │◀─ ouiResult ────────────│            │
  │          │             │              │              │            │            │
  │          │             │              │── scanPorts()───────────────────────▶│
  │          │             │              │◀─ portResult ───────────────────────│
  │          │             │              │              │            │            │
  │          │             │◀─ devices ──│              │            │            │
  │          │             │              │              │            │            │
  │          │             │── calcRisk() │              │            │            │
  │          │             │   (Layer 1)  │              │            │            │
  │          │             │              │              │            │            │
  │          │◀─ result ──│              │              │            │            │
  │◀─ UI ──│             │              │              │            │            │
```

### 3.2 단계별 데이터 변환

```
Step 1: ARP 테이블 조회
  Input:  (없음)
  Output: List<ArpEntry>
          ArpEntry(ip: String, mac: String, device: String)
  시간:   ~1초

Step 2: mDNS/SSDP 서비스 탐색
  Input:  (없음)
  Output: List<DiscoveredService>
          DiscoveredService(name: String, type: String, host: String, port: Int)
  시간:   ~5초 (탐색 대기)

Step 3: ARP + mDNS 병합 -> 기기 목록
  Input:  List<ArpEntry> + List<DiscoveredService>
  Output: List<RawDevice>
          RawDevice(ip: String, mac: String, services: List<String>)

Step 4: MAC OUI 매칭
  Input:  List<RawDevice>
  Output: List<DeviceWithOui>
          DeviceWithOui(device: RawDevice, vendor: String?, type: String?, risk: Float)

Step 5: 의심 기기 포트 스캔 (risk > 0.3인 기기만)
  Input:  List<DeviceWithOui> (filtered)
  Output: List<DeviceWithPorts>
          DeviceWithPorts(..., openPorts: List<Int>)
  시간:   의심 기기당 ~3초 (RTSP, HTTP, ONVIF, RTMP 동시 스캔)

Step 6: Layer 1 위험도 산출
  Input:  List<DeviceWithPorts>
  Output: List<NetworkDevice>
          NetworkDevice(ip, mac, vendor, riskScore: Int, evidence: List<String>)

Step 7: Quick Scan 결과 생성
  Input:  List<NetworkDevice>
  Output: ScanResult
          ScanResult(
            mode = QUICK,
            riskLevel: RiskLevel,
            overallScore: Int,
            devices: List<NetworkDevice>,
            timestamp: Long
          )
```

### 3.3 Flow 파이프라인 코드 구조

```
QuickScanUseCase
  │
  ├── wifiScanRepo.scanNetwork()
  │     ├── .onStart { emit(Progress(step=ARP, 0%)) }
  │     ├── arpScanner.getArpTable()
  │     ├── .map { mergeWithMdns(it) }
  │     ├── .map { matchOui(it) }
  │     ├── .flatMapConcat { scanSuspiciousPorts(it) }
  │     └── .onCompletion { emit(Progress(step=COMPLETE, 100%)) }
  │
  ├── riskCalculator.calculateLayer1(devices)
  │
  └── emit(ScanResult(mode=QUICK, ...))
```

---

## 4. Full Scan 데이터 플로우

Full Scan은 3개 레이어(Wi-Fi + 렌즈 + EMF)를 모두 실행하고 교차 검증하는 정밀 점검 모드이다.

### 4.1 병렬 실행 구조

```
┌─ Full Scan ──────────────────────────────────────────────┐
│                                                           │
│  Phase 1: Wi-Fi 스캔 (30초)                              │
│  ┌──────────────────────────────────────────────────┐    │
│  │ ARP -> mDNS -> OUI -> 포트 스캔                  │    │
│  │ 출력: Flow<Layer1Result>                         │    │
│  └──────────────────────────────────────────────────┘    │
│                │                                          │
│                ▼ (Wi-Fi 완료 후)                          │
│  Phase 2: 렌즈 + EMF 동시 (최대 120초)                   │
│  ┌──────────────────────┐  ┌──────────────────────┐     │
│  │ 렌즈 감지 (Stage A)  │  │ 자기장 감지          │     │
│  │ + IR 감지 (Stage B)   │  │ (캘리브+실시간)      │     │
│  │                       │  │                       │     │
│  │ 출력: Flow<Layer2Res> │  │ 출력: Flow<Layer3Res> │     │
│  └───────────┬───────────┘  └───────────┬───────────┘     │
│              │                           │                 │
│              └─────────┬─────────────────┘                 │
│                        ▼                                   │
│  Phase 3: 교차 검증 (즉시)                                │
│  ┌──────────────────────────────────────────────────┐    │
│  │ combine(layer1, layer2, layer3)                   │    │
│  │ -> CrossValidator -> RiskCalculator               │    │
│  │ 출력: ScanResult (종합 위험도 + 근거)             │    │
│  └──────────────────────────────────────────────────┘    │
│                                                           │
└───────────────────────────────────────────────────────────┘
```

### 4.2 Phase별 데이터 흐름 상세

#### Phase 1: Wi-Fi 스캔 (Quick Scan과 동일)

| 단계 | 입력 | 출력 | 소요 시간 |
|------|------|------|----------|
| ARP 조회 | 없음 | List<ArpEntry> | ~1초 |
| mDNS/SSDP 탐색 | 없음 | List<DiscoveredService> | ~5초 |
| 기기 목록 병합 | ARP + mDNS | List<RawDevice> | 즉시 |
| OUI 매칭 | List<RawDevice> | List<DeviceWithOui> | ~0.5초 |
| 포트 스캔 | 의심 기기 | List<DeviceWithPorts> | ~15초 |
| Layer 1 점수 산출 | 최종 기기 목록 | Layer1Result(score, devices) | 즉시 |

#### Phase 2a: 렌즈 감지

| 단계 | 입력 | 출력 | 소요 시간 |
|------|------|------|----------|
| 카메라 초기화 | 없음 | CameraSession | ~1초 |
| 플래시 ON | 없음 | FlashState.ON | 즉시 |
| 프레임 루프 (30fps) | CameraFrame | List<BrightPoint> | 연속 |
| Retroreflection 분석 | List<BrightPoint> | List<LensCandidate> | 프레임당 ~15ms |
| 시간축 안정성 검사 | 2초 윈도우 | List<LensPoint> | 2초마다 |
| 플래시 OFF 동적 검증 | LensPoint | LensPoint(verified) | 0.2초 |
| Layer 2 점수 산출 | 확정 LensPoint들 | Layer2Result(score, points) | 즉시 |

#### Phase 2b: 자기장 감지

| 단계 | 입력 | 출력 | 소요 시간 |
|------|------|------|----------|
| 캘리브레이션 | 60 samples (20Hz) | baseline, noise_floor | 3초 |
| 실시간 측정 | SensorEvent(x,y,z) | MagneticReading | 연속 (50ms 간격) |
| 노이즈 필터 | MagneticReading | FilteredReading | 즉시 |
| 이동 평균 | window=10 | SmoothedReading | 즉시 |
| 등급 판정 | delta(uT) | MagneticLevel(점수, 등급) | 즉시 |
| Layer 3 점수 산출 | 최대 delta 기록 | Layer3Result(score, readings) | 스캔 종료 시 |

#### Phase 3: 교차 검증

```
입력:
  layer1Result: Layer1Result  (Wi-Fi)
  layer2Result: Layer2Result  (렌즈)
  layer3Result: Layer3Result  (EMF)

처리:
  1. 가용 레이어 확인
     Wi-Fi 미연결 -> W1=0, 나머지 재분배
     IR 불가     -> Stage B 점수 = 0

  2. 가중치 적용
     weighted = W1*L1 + W2*L2 + W3*L3

  3. 보정 계수 적용
     양성 레이어 수 카운트 (score > 30 = 양성)
     보정 = corrections[양성수]

  4. 최종 점수
     finalScore = min(100, weighted * 보정)

  5. 등급 결정
     0~19  : SAFE
     20~39 : ATTENTION
     40~59 : CAUTION
     60~79 : DANGER
     80~100: CRITICAL

출력:
  ScanResult(
    mode: FULL,
    overallScore: Int,
    riskLevel: RiskLevel,
    layer1: Layer1Result,
    layer2: Layer2Result,
    layer3: Layer3Result,
    evidences: List<Evidence>,
    timestamp: Long
  )
```

### 4.3 Flow combine 패턴

```
// 3개 레이어 결과를 combine으로 합류
combine(
    layer1Flow,     // Flow<Layer1Result>
    layer2Flow,     // Flow<Layer2Result>
    layer3Flow      // Flow<Layer3Result>
) { l1, l2, l3 ->
    crossValidator.validate(l1, l2, l3)
}
.flowOn(Dispatchers.Default)
.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initial)
```

---

## 5. 렌즈 감지(Retroreflection) 프레임 처리 파이프라인

### 5.1 프레임 처리 흐름

```
CameraX ImageAnalysis (30fps)
         │
         ▼
┌─── Frame Pipeline ────────────────────────────────────────┐
│                                                            │
│  [1] 전처리                                               │
│  ┌──────────────────────────────────────────────────────┐ │
│  │ 해상도 축소: 1080p -> 720p (ImageProxy.toBitmap())   │ │
│  │ 그레이스케일 변환                                     │ │
│  │ 출력: GrayFrame (1280x720, 8bit)                     │ │
│  │ 소요: ~3ms                                            │ │
│  └───────────────────────────┬──────────────────────────┘ │
│                              │                             │
│  [2] 고휘도 포인트 추출                                    │
│  ┌───────────────────────────▼──────────────────────────┐ │
│  │ 적응형 임계값: 프레임 밝기 상위 0.1%                  │ │
│  │ Connected Component 분석                               │ │
│  │ 크기 필터: 1~10 pixel (렌즈 반사 크기 범위)           │ │
│  │ 출력: List<BrightPoint>(x, y, size, brightness, shape)│ │
│  │ 소요: ~5ms                                            │ │
│  └───────────────────────────┬──────────────────────────┘ │
│                              │                             │
│  [3] Retroreflection 특성 검사                             │
│  ┌───────────────────────────▼──────────────────────────┐ │
│  │ 원형도 검사: circularity > 0.8                        │ │
│  │ 밝기 대비: 주변 대비 상위 0.05%                       │ │
│  │ 색상 분석: RGB -> 순백 또는 붉은빛 판정               │ │
│  │ 출력: List<LensCandidate>(point, score, features)     │ │
│  │ 소요: ~3ms                                            │ │
│  └───────────────────────────┬──────────────────────────┘ │
│                              │                             │
│  [4] 시간축 분석 (2초 윈도우 = 60 프레임)                  │
│  ┌───────────────────────────▼──────────────────────────┐ │
│  │ ┌─────────────────────────────────────────────┐      │ │
│  │ │  프레임 버퍼 (Ring Buffer, size=60)          │      │ │
│  │ │  [F1][F2][F3]...[F58][F59][F60]             │      │ │
│  │ └─────────────────────────────────────────────┘      │ │
│  │                                                       │ │
│  │ 위치 안정성: 같은 좌표(+-5px) 40/60 프레임 이상      │ │
│  │ 깜빡임 검사: 연속 존재 -> 렌즈, 깜빡임 -> LED 제외   │ │
│  │ 이동 검사: 스마트폰 이동 시 위치 고정 여부             │ │
│  │                                                       │ │
│  │ 출력: List<SuspectPoint>(point, stability, duration)  │ │
│  │ 소요: ~2ms (윈도우 갱신만)                            │ │
│  └───────────────────────────┬──────────────────────────┘ │
│                              │                             │
│  [5] 동적 검증 (플래시 OFF/ON)                             │
│  ┌───────────────────────────▼──────────────────────────┐ │
│  │ 트리거: SuspectPoint 발견 시                          │ │
│  │ 플래시 OFF (0.2초) -> 포인트 소실 확인                │ │
│  │ 소실 = 플래시 의존 반사 = 렌즈 가능성 UP (+25점)     │ │
│  │ 유지 = 자체 발광 (LED) = 렌즈 가능성 DOWN            │ │
│  │ 플래시 ON 복구                                        │ │
│  │                                                       │ │
│  │ 출력: List<VerifiedLensPoint>(point, verified, score) │ │
│  │ 소요: ~200ms (플래시 토글 대기)                       │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                            │
│  총 프레임당 처리 시간: ~13ms (33ms 예산 이내)            │
│  동적 검증 시: 추가 200ms (비동기, 메인 파이프라인 비차단) │
└────────────────────────────────────────────────────────────┘
```

### 5.2 프레임 처리 성능 예산

| 단계 | 예산 | 실측 목표 |
|------|------|----------|
| 전처리 (리사이즈 + 그레이) | 5ms | 3ms |
| 고휘도 추출 | 8ms | 5ms |
| 특성 검사 | 5ms | 3ms |
| 시간축 분석 | 3ms | 2ms |
| **프레임당 합계** | **21ms** | **13ms** |
| **30fps 예산** | **33ms** | **여유 20ms** |

---

## 6. EMF 센서 데이터 스트림 파이프라인

### 6.1 데이터 스트림 흐름

```
SensorManager (TYPE_MAGNETIC_FIELD, SENSOR_DELAY_GAME)
         │
         │ onSensorChanged() @ 20Hz
         ▼
┌─── EMF Pipeline ──────────────────────────────────────────┐
│                                                            │
│  [1] 원시 데이터 수집                                     │
│  ┌──────────────────────────────────────────────────────┐ │
│  │ SensorEvent(x, y, z)                                 │ │
│  │ magnitude = sqrt(x^2 + y^2 + z^2)                   │ │
│  │ timestamp = event.timestamp (나노초)                  │ │
│  │                                                       │ │
│  │ 출력: RawMagnetic(magnitude, x, y, z, timestamp)     │ │
│  └───────────────────────────┬──────────────────────────┘ │
│                              │                             │
│  [2] 급변 필터                                            │
│  ┌───────────────────────────▼──────────────────────────┐ │
│  │ 이전 값과 비교:                                       │ │
│  │ if |current - previous| > 50uT AND 시간차 < 0.3초:   │ │
│  │   -> 자체 간섭 (스마트폰 이동) -> 무시               │ │
│  │ else:                                                 │ │
│  │   -> 유효 데이터로 전달                                │ │
│  │                                                       │ │
│  │ 출력: FilteredMagnetic (또는 null = 필터됨)           │ │
│  └───────────────────────────┬──────────────────────────┘ │
│                              │                             │
│  [3] 이동 평균 (Sliding Window)                            │
│  ┌───────────────────────────▼──────────────────────────┐ │
│  │ ┌────────────────────────────────┐                    │ │
│  │ │  Window Buffer (size=10)       │                    │ │
│  │ │  [v1][v2][v3]...[v8][v9][v10]  │                    │ │
│  │ └────────────────────────────────┘                    │ │
│  │                                                       │ │
│  │ smoothed = mean(window)                               │ │
│  │ 출력: SmoothedMagnetic(smoothed, delta_from_baseline) │ │
│  └───────────────────────────┬──────────────────────────┘ │
│                              │                             │
│  [4] 등급 판정                                            │
│  ┌───────────────────────────▼──────────────────────────┐ │
│  │                                                       │ │
│  │ delta = |smoothed - baseline|                         │ │
│  │                                                       │ │
│  │ if delta < noise_floor:                               │ │
│  │   level = NORMAL, score = 0                           │ │
│  │ elif delta < 5uT:                                     │ │
│  │   level = NORMAL, score = 0                           │ │
│  │ elif delta < 15uT:                                    │ │
│  │   level = INTEREST, score = 20                        │ │
│  │ elif delta < 30uT:                                    │ │
│  │   level = CAUTION, score = 50                         │ │
│  │ elif delta < 50uT:                                    │ │
│  │   level = SUSPECT, score = 75                         │ │
│  │ else:                                                 │ │
│  │   level = HIGH_SUSPECT, score = 95                    │ │
│  │                                                       │ │
│  │ 출력: MagneticReading(delta, level, score, timestamp) │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                            │
│  [5] UI 전달 (debounce 100ms)                             │
│  ┌──────────────────────────────────────────────────────┐ │
│  │ StateFlow<MagneticUiState>                            │ │
│  │ - currentDelta: Float                                 │ │
│  │ - level: MagneticLevel                                │ │
│  │ - graphData: List<Float> (최근 200개 = 10초)          │ │
│  │ - maxDelta: Float                                     │ │
│  │ - isCalibrated: Boolean                               │ │
│  └──────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────┘
```

### 6.2 윈도우 관리

```
캘리브레이션 윈도우:
  크기: 60 samples (3초 * 20Hz)
  용도: baseline + noise_floor 산출
  갱신: 스캔 시작 시 1회

이동 평균 윈도우:
  크기: 10 samples (0.5초)
  용도: 순간 노이즈 제거
  갱신: 매 샘플마다 슬라이딩

그래프 윈도우:
  크기: 200 samples (10초)
  용도: UI 실시간 그래프 표시
  갱신: 매 샘플마다 (오래된 데이터 제거)
```

---

## 7. 교차 검증 엔진 데이터 합류 지점

### 7.1 합류 다이어그램

```
Layer 1 (Wi-Fi)         Layer 2 (렌즈)        Layer 3 (EMF)
     │                       │                      │
     │ Layer1Result          │ Layer2Result         │ Layer3Result
     │ (score, devices)     │ (score, points)     │ (score, readings)
     │                       │                      │
     └───────────┬───────────┼──────────────────────┘
                 │           │
                 ▼           ▼
          ┌──────────────────────────┐
          │     combine() 합류점     │
          │──────────────────────────│
          │                          │
          │  입력: (L1, L2, L3)     │
          │                          │
          │  1. 가용성 확인          │
          │     L1 없음? W1=0       │
          │     L2 Stage B 없음?    │
          │     L3 미캘리브? W3=0   │
          │                          │
          │  2. 가중치 재분배        │
          │     정규화: sum(W)=1.0  │
          │                          │
          │  3. 가중 합산            │
          │     raw = W1*S1 +       │
          │           W2*S2 +       │
          │           W3*S3         │
          │                          │
          │  4. 보정 계수            │
          │     양성수 카운트       │
          │     raw *= correction   │
          │                          │
          │  5. 등급 결정            │
          │     finalScore ->       │
          │     RiskLevel           │
          │                          │
          │  6. 근거 수집            │
          │     각 레이어의 evidence │
          │     문장 생성            │
          └────────────┬─────────────┘
                       │
                       ▼
              ScanResult (최종)
```

### 7.2 가중치 재분배 매트릭스

| 시나리오 | W1 (Wi-Fi) | W2 (렌즈) | W3 (EMF) | 합계 |
|---------|:----------:|:---------:|:--------:|:----:|
| 전체 가용 | 0.50 | 0.35 | 0.15 | 1.00 |
| Wi-Fi 미연결 | 0.00 | 0.75 | 0.25 | 1.00 |
| IR 불가 (밝은 환경) | 0.55 | 0.30 | 0.15 | 1.00 |
| Wi-Fi 미연결 + 밝은 환경 | 0.00 | 0.80 | 0.20 | 1.00 |
| EMF 센서 없음 | 0.60 | 0.40 | 0.00 | 1.00 |
| Wi-Fi만 가용 | 1.00 | 0.00 | 0.00 | 1.00 |
| 렌즈만 가용 | 0.00 | 1.00 | 0.00 | 1.00 |

---

## 8. 에러/타임아웃 처리 플로우

### 8.1 센서별 타임아웃

| 센서 | 타임아웃 | 타임아웃 시 처리 |
|------|---------|-----------------|
| ARP 조회 | 5초 | 빈 목록 반환, mDNS만으로 계속 |
| mDNS 탐색 | 10초 | 현재까지 발견된 목록 사용 |
| 포트 스캔 (기기당) | 2초 | 해당 포트 미개방 처리 |
| 렌즈 감지 전체 | 120초 | 현재까지 결과로 점수 산출 |
| 자기장 캘리브레이션 | 5초 | 기본 baseline(45uT) 사용 |
| 자기장 스캔 전체 | 120초 | 현재까지 최대 delta로 산출 |

### 8.2 에러 시 대체 경로

```
┌─ Wi-Fi 스캔 에러 ──────────────────────────────┐
│                                                  │
│  Wi-Fi 미연결                                    │
│  └─ Layer 1 스킵 -> Layer 2 + 3만으로 산출      │
│                                                  │
│  ARP 접근 불가 (Android 향후 제한 가능)          │
│  └─ mDNS + SSDP만으로 기기 탐색                 │
│                                                  │
│  포트 스캔 타임아웃                               │
│  └─ OUI 매칭 결과만으로 위험도 산출              │
└──────────────────────────────────────────────────┘

┌─ 렌즈 감지 에러 ──────────────────────────────┐
│                                                  │
│  카메라 권한 거부                                │
│  └─ Layer 2 스킵 -> Layer 1 + 3만으로 산출      │
│                                                  │
│  카메라 초기화 실패                              │
│  └─ 3회 재시도 -> 실패 시 Layer 2 스킵          │
│                                                  │
│  플래시 제어 실패                                │
│  └─ Stage A 스킵 -> Stage B(IR)만 시도          │
└──────────────────────────────────────────────────┘

┌─ 자기장 에러 ────────────────────────────────┐
│                                                  │
│  자력계 센서 없음                                │
│  └─ Layer 3 스킵 -> Layer 1 + 2만으로 산출      │
│                                                  │
│  캘리브레이션 실패 (noise > 30uT)               │
│  └─ "자기장 간섭 환경" 안내, 기본 baseline 사용 │
│                                                  │
│  센서 이벤트 중단                                │
│  └─ 5초 대기 -> 재등록 -> 실패 시 Layer 3 스킵  │
└──────────────────────────────────────────────────┘
```

### 8.3 에러 전파 패턴

```kotlin
// Flow catch 패턴
wifiScanRepo.scanNetwork()
    .catch { error ->
        emit(Layer1Result.unavailable(reason = error.message))
        // 에러 로깅 (Timber)
        // UI에 "Wi-Fi 스캔 불가" 표시
        // 다른 레이어는 영향 없음 (SupervisorJob)
    }
```

---

## 9. 리포트 저장 데이터 플로우

```
ScanResult (메모리)
     │
     ▼
┌─ 저장 플로우 ────────────────────────────────┐
│                                                │
│  1. Domain -> Entity 변환                     │
│     ScanResult -> ScanReportEntity             │
│     NetworkDevice -> DeviceEntity (N개)        │
│     LensPoint/MagneticReading -> RiskPointEntity│
│                                                │
│  2. Room 트랜잭션                              │
│     @Transaction {                             │
│       reportDao.insert(reportEntity)           │
│       deviceDao.insertAll(deviceEntities)      │
│       riskPointDao.insertAll(pointEntities)    │
│     }                                          │
│                                                │
│  3. 무료 사용자: 10건 초과 시 오래된 리포트 삭제│
│     reportDao.deleteOldest(keepCount = 10)     │
│                                                │
│  4. PDF 생성 (프리미엄, 선택 시)               │
│     PdfGenerator.generate(scanResult) -> File  │
│     -> Share Intent 또는 로컬 저장             │
│                                                │
└────────────────────────────────────────────────┘
```

---

*본 문서는 project-plan.md v3.1 기반으로 작성되었으며, 모든 데이터 플로우는 Phase 1 (Android MVP) 범위를 다룹니다.*
