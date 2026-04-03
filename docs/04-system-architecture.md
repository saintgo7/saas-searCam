# SearCam 시스템 아키텍처 설계서

> 버전: v1.0
> 작성일: 2026-04-03
> 기반: project-plan.md v3.1

---

## 1. 아키텍처 개요

SearCam은 **Clean Architecture + MVVM** 패턴을 채택한다.
각 레이어는 단방향 의존성을 가지며, domain 레이어가 어떤 외부 프레임워크에도 의존하지 않도록 설계한다.

```
┌──────────────────────────────────────────────────────────────┐
│                     Presentation Layer                        │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    │
│  │HomeScreen│  │ScanScreen│  │LensScreen│  │ReportScr │    │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘    │
│       │              │              │              │          │
│  ┌────▼─────┐  ┌────▼─────┐  ┌────▼─────┐  ┌────▼─────┐    │
│  │HomeVM    │  │ScanVM    │  │LensVM    │  │ReportVM  │    │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘    │
├───────┼──────────────┼──────────────┼──────────────┼──────────┤
│       │         Domain Layer        │              │          │
│  ┌────▼──────────────▼──────────────▼──────────────▼─────┐   │
│  │                    UseCases                            │   │
│  │  RunQuickScan / RunFullScan / CalculateRisk / Export  │   │
│  └────────────────────────┬──────────────────────────────┘   │
│                           │                                   │
│  ┌────────────────────────▼──────────────────────────────┐   │
│  │               Repository Interfaces                    │   │
│  │  WifiScanRepo / MagneticRepo / IrDetectionRepo / ...  │   │
│  └────────────────────────┬──────────────────────────────┘   │
├───────────────────────────┼───────────────────────────────────┤
│                      Data Layer                               │
│  ┌────────────────────────▼──────────────────────────────┐   │
│  │              Repository Implementations                │   │
│  └───┬───────────┬───────────┬───────────┬───────────────┘   │
│      │           │           │           │                    │
│  ┌───▼───┐  ┌───▼───┐  ┌───▼───┐  ┌───▼───┐                │
│  │Sensor │  │Analysis│  │ Local │  │  PDF  │                │
│  │Module │  │Module  │  │  DB   │  │Module │                │
│  └───────┘  └────────┘  └───────┘  └───────┘                │
└──────────────────────────────────────────────────────────────┘
```

---

## 2. 레이어별 책임

### 2.1 Presentation Layer

| 구성요소 | 책임 | 의존 대상 |
|---------|------|----------|
| Screen (Composable) | UI 렌더링, 사용자 입력 수신 | ViewModel |
| ViewModel | UI 상태 관리, UseCase 호출 | UseCase (domain) |
| Navigation | 화면 전환 로직 | NavHost |
| Components | 재사용 UI 위젯 (RiskGauge 등) | 없음 (자체 완결) |

**규칙**: Screen은 ViewModel 외 다른 레이어를 직접 참조하지 않는다.

### 2.2 Domain Layer

| 구성요소 | 책임 | 의존 대상 |
|---------|------|----------|
| UseCase | 비즈니스 로직 오케스트레이션 | Repository 인터페이스 |
| Model | 비즈니스 엔티티 (ScanResult, RiskLevel 등) | 없음 (순수 Kotlin) |
| Repository Interface | 데이터 소스 추상화 | 없음 (인터페이스만) |

**규칙**: domain 패키지에는 Android 프레임워크 import가 존재하지 않는다.

### 2.3 Data Layer

| 구성요소 | 책임 | 의존 대상 |
|---------|------|----------|
| Repository Impl | Repository 인터페이스 구현 | Sensor, DB, Analysis |
| Sensor Module | 하드웨어 센서 접근 | Android SDK |
| Analysis Module | OUI DB, CrossValidator, NoiseFilter | 로컬 JSON |
| Local DB | Room 기반 리포트 저장 | Room, SQLite |
| PDF Module | 리포트 PDF 생성 | iText |

---

## 3. 모듈 간 의존성 그래프

```
                    ┌─────────┐
                    │  :app   │
                    └────┬────┘
                         │ depends on
              ┌──────────┼──────────┐
              │          │          │
        ┌─────▼───┐ ┌───▼────┐ ┌──▼──────┐
        │:feature- │ │:feature│ │:feature-│
        │  scan    │ │-report │ │settings │
        └────┬────┘ └───┬────┘ └────┬────┘
             │          │           │
             └──────┬───┘───────────┘
                    │ depends on
              ┌─────▼─────┐
              │  :domain   │
              └─────┬─────┘
                    │ depends on (interface만)
              ┌─────▼─────┐
              │   :data    │
              └──┬──┬──┬──┘
                 │  │  │
        ┌────────┘  │  └────────┐
   ┌────▼───┐  ┌───▼────┐ ┌───▼────┐
   │:sensor │  │:analysis│ │:local- │
   │        │  │         │ │  db    │
   └────────┘  └─────────┘ └────────┘
```

### 3.1 모듈 의존성 매트릭스

| 모듈 | :app | :feature-scan | :domain | :data | :sensor | :analysis | :local-db |
|------|:----:|:------------:|:-------:|:-----:|:-------:|:---------:|:---------:|
| :app | - | O | O | O | X | X | X |
| :feature-scan | X | - | O | X | X | X | X |
| :domain | X | X | - | X | X | X | X |
| :data | X | X | O | - | O | O | O |
| :sensor | X | X | X | X | - | X | X |
| :analysis | X | X | O | X | X | - | X |
| :local-db | X | X | O | X | X | X | - |

> O = 의존, X = 의존하지 않음
> domain은 아무 모듈에도 의존하지 않는다 (최상위 정책 레이어).

---

## 4. 패키지 구조 상세

```
app/src/main/java/com/searcam/
│
├── SearCamApp.kt                    # @HiltAndroidApp Application
├── MainActivity.kt                  # Single Activity, NavHost
│
├── di/                              # ── Hilt DI 모듈 ──
│   ├── AppModule.kt                 #   앱 전역 의존성
│   ├── SensorModule.kt              #   센서 바인딩
│   ├── DatabaseModule.kt            #   Room DB 제공
│   ├── AnalysisModule.kt            #   분석 엔진 바인딩
│   └── RepositoryModule.kt          #   Repository 바인딩
│
├── domain/                          # ── 도메인 레이어 (순수 Kotlin) ──
│   ├── model/
│   │   ├── ScanResult.kt            #   스캔 결과 (sealed class)
│   │   ├── RiskLevel.kt             #   위험 등급 enum (Safe..Critical)
│   │   ├── NetworkDevice.kt         #   네트워크 기기 모델
│   │   ├── MagneticReading.kt       #   자기장 측정값
│   │   ├── LensPoint.kt             #   렌즈 의심 포인트
│   │   ├── IrPoint.kt               #   IR 의심 포인트
│   │   ├── ScanReport.kt            #   리포트 모델
│   │   └── ScanMode.kt              #   스캔 모드 enum
│   ├── usecase/
│   │   ├── RunQuickScanUseCase.kt   #   Quick Scan 오케스트레이션
│   │   ├── RunFullScanUseCase.kt    #   Full Scan 오케스트레이션
│   │   ├── RunLensFinderUseCase.kt  #   렌즈 찾기 모드
│   │   ├── CalculateRiskUseCase.kt  #   교차 검증 + 위험도 산출
│   │   └── ExportReportUseCase.kt   #   리포트 내보내기
│   └── repository/
│       ├── WifiScanRepository.kt    #   Wi-Fi 스캔 인터페이스
│       ├── MagneticRepository.kt    #   자기장 센서 인터페이스
│       ├── IrDetectionRepository.kt #   IR 감지 인터페이스
│       ├── LensDetectionRepository.kt # 렌즈 감지 인터페이스
│       └── ReportRepository.kt      #   리포트 저장/조회 인터페이스
│
├── data/                            # ── 데이터 레이어 ──
│   ├── sensor/
│   │   ├── WifiScanner.kt           #   ARP + mDNS + SSDP
│   │   ├── MagneticSensor.kt        #   SensorManager 3축 자력계
│   │   ├── LensDetector.kt          #   CameraX + 플래시 Retroreflection
│   │   ├── IrDetector.kt            #   전면 카메라 IR 분석
│   │   └── PortScanner.kt           #   TCP 포트 스캔
│   ├── analysis/
│   │   ├── OuiDatabase.kt           #   MAC OUI JSON 로더 + 매칭
│   │   ├── CrossValidator.kt        #   3-Layer 교차 검증 엔진
│   │   ├── RiskCalculator.kt        #   가중치 기반 위험도 산출
│   │   ├── NoiseFilter.kt           #   자기장 노이즈 필터
│   │   └── RetroreflectionAnalyzer.kt # 렌즈 반사 패턴 분석
│   ├── repository/
│   │   ├── WifiScanRepositoryImpl.kt
│   │   ├── MagneticRepositoryImpl.kt
│   │   ├── IrDetectionRepositoryImpl.kt
│   │   ├── LensDetectionRepositoryImpl.kt
│   │   └── ReportRepositoryImpl.kt
│   ├── local/
│   │   ├── AppDatabase.kt           #   Room Database 정의
│   │   ├── dao/
│   │   │   ├── ReportDao.kt
│   │   │   ├── DeviceDao.kt
│   │   │   └── ChecklistDao.kt
│   │   ├── entity/
│   │   │   ├── ScanReportEntity.kt
│   │   │   ├── DeviceEntity.kt
│   │   │   ├── RiskPointEntity.kt
│   │   │   └── ChecklistEntity.kt
│   │   └── converter/
│   │       └── TypeConverters.kt
│   └── pdf/
│       └── PdfGenerator.kt
│
├── ui/                              # ── Presentation 레이어 ──
│   ├── navigation/
│   │   ├── SearCamNavHost.kt        #   NavHost 정의
│   │   └── Screen.kt                #   Route sealed class
│   ├── home/
│   │   ├── HomeScreen.kt
│   │   └── HomeViewModel.kt
│   ├── scan/
│   │   ├── QuickScanScreen.kt
│   │   ├── FullScanScreen.kt
│   │   ├── ScanResultScreen.kt
│   │   └── ScanViewModel.kt
│   ├── lens/
│   │   ├── LensFinderScreen.kt
│   │   ├── IrCameraScreen.kt
│   │   └── LensViewModel.kt
│   ├── magnetic/
│   │   ├── MagneticScreen.kt
│   │   ├── MagneticGraph.kt
│   │   └── MagneticViewModel.kt
│   ├── report/
│   │   ├── ReportListScreen.kt
│   │   ├── ReportDetailScreen.kt
│   │   └── ReportViewModel.kt
│   ├── checklist/
│   │   ├── ChecklistSelectScreen.kt
│   │   ├── ChecklistScreen.kt
│   │   └── ChecklistViewModel.kt
│   ├── settings/
│   │   ├── SettingsScreen.kt
│   │   └── SettingsViewModel.kt
│   ├── onboarding/
│   │   └── OnboardingScreen.kt
│   ├── theme/
│   │   ├── Color.kt
│   │   ├── Type.kt
│   │   └── Theme.kt
│   └── components/
│       ├── RiskGauge.kt             #   원형 위험도 게이지
│       ├── RiskBadge.kt             #   위험 등급 뱃지
│       ├── ScanProgress.kt          #   스캔 진행 표시
│       ├── DeviceListItem.kt        #   기기 목록 아이템
│       └── ScanModeCard.kt          #   스캔 모드 선택 카드
│
└── util/
    ├── SoundManager.kt              #   경고음 재생
    ├── VibrationManager.kt          #   진동 패턴
    ├── PermissionHelper.kt          #   런타임 권한 관리
    └── Constants.kt                 #   앱 상수
```

---

## 5. 센서 모듈 아키텍처

### 5.1 센서 모듈 클래스 다이어그램

```
                    <<interface>>
                ┌─────────────────┐
                │  SensorModule   │
                │─────────────────│
                │ + start(): Flow │
                │ + stop()        │
                │ + isAvailable() │
                └────────┬────────┘
                         │ implements
         ┌───────────────┼───────────────┬──────────────────┐
         │               │               │                  │
┌────────▼───────┐ ┌────▼────────┐ ┌────▼────────┐ ┌──────▼───────┐
│  WifiScanner   │ │LensDetector │ │ IrDetector  │ │MagneticSensor│
│────────────────│ │─────────────│ │─────────────│ │──────────────│
│ -wifiManager   │ │ -cameraX    │ │ -cameraX    │ │ -sensorMgr   │
│ -nsdManager    │ │ -flashCtrl  │ │ -frontCam   │ │ -magnetometer│
│ -arpReader     │ │ -analyzer   │ │ -irAnalyzer │ │ -noiseFilter │
│────────────────│ │─────────────│ │─────────────│ │──────────────│
│ +scanNetwork() │ │ +startScan()│ │ +startScan()│ │ +calibrate() │
│ +getArpTable() │ │ +analyze()  │ │ +analyze()  │ │ +measure()   │
│ +discoverSvcs()│ │ +getPoints()│ │ +getPoints()│ │ +getReading()│
│ +scanPorts()   │ │ +toggleFlsh│ │             │ │ +getDelta()  │
└────────────────┘ └─────────────┘ └─────────────┘ └──────────────┘
         │               │               │                  │
         ▼               ▼               ▼                  ▼
  ┌────────────┐  ┌────────────┐  ┌───────────┐   ┌──────────────┐
  │PortScanner │  │Retroreflect│  │(카메라SDK)│   │ NoiseFilter  │
  │            │  │ionAnalyzer │  │           │   │              │
  └────────────┘  └────────────┘  └───────────┘   └──────────────┘
```

### 5.2 각 센서 모듈 상세

#### WifiScanner

| 항목 | 상세 |
|------|------|
| Android API | WifiManager, NsdManager, /proc/net/arp |
| 출력 | `Flow<List<NetworkDevice>>` |
| 스캔 순서 | ARP 조회 -> mDNS/SSDP 탐색 -> OUI 매칭 -> 포트 스캔 |
| 타임아웃 | 전체 30초, 포트당 2초 |
| 스레드 | Dispatchers.IO |

#### LensDetector (Retroreflection)

| 항목 | 상세 |
|------|------|
| Android API | CameraX (후면 카메라), Camera2 Flash API |
| 출력 | `Flow<List<LensPoint>>` |
| 프레임 처리 | 30fps, 720p 다운스케일 -> 그레이스케일 -> 고휘도 추출 |
| 분석 파이프라인 | 포인트 크기 필터 -> 원형도 검사 -> 시간축 안정성 -> 플래시 OFF 동적 검증 |
| 스레드 | Dispatchers.Default (CPU 바운드) |

#### IrDetector

| 항목 | 상세 |
|------|------|
| Android API | CameraX (전면 카메라) |
| 출력 | `Flow<List<IrPoint>>` |
| 조건 | 암실 환경 (조도 10 lux 이하) |
| 분석 | 고휘도 포인트 추출 -> 보라색/백색 필터 -> 3초 지속성 확인 |
| 스레드 | Dispatchers.Default |

#### MagneticSensor

| 항목 | 상세 |
|------|------|
| Android API | SensorManager (TYPE_MAGNETIC_FIELD) |
| 출력 | `Flow<MagneticReading>` |
| 샘플링 | 20Hz (SENSOR_DELAY_GAME) |
| 캘리브레이션 | 3초간 60 샘플 -> baseline + noise_floor 산출 |
| 노이즈 필터 | 이동 평균(window=10), 급변 필터(0.3초 내 50uT 초과 제거) |
| 스레드 | Dispatchers.Default |

---

## 6. 데이터 흐름 아키텍처 (개요)

```
[센서 하드웨어]
     │
     ▼
[Sensor Module] ──── Flow<RawData> ────┐
     │                                  │
     ▼                                  ▼
[Noise Filter / Analyzer] ──── Flow<AnalyzedData> ──┐
     │                                               │
     ▼                                               ▼
[Repository Impl] ──── Flow<DomainModel> ──┐
     │                                      │
     ▼                                      ▼
[UseCase] ──── 교차 검증 + 위험도 산출 ──┐
     │                                    │
     ▼                                    ▼
[ViewModel] ──── StateFlow<UiState> ──┐
     │                                 │
     ▼                                 ▼
[Composable Screen] ──── UI 렌더링
```

> 상세 데이터 플로우는 `05-data-flow.md`를 참조한다.

---

## 7. Hilt DI 모듈 구조

### 7.1 모듈 구성도

```
┌─────────────────────────────────────────────┐
│               @HiltAndroidApp               │
│              SearCamApp.kt                  │
├─────────────────────────────────────────────┤
│                                              │
│  ┌───────────────┐  ┌───────────────────┐   │
│  │  AppModule    │  │  SensorModule     │   │
│  │  @Singleton   │  │  @Singleton       │   │
│  │───────────────│  │───────────────────│   │
│  │ Context       │  │ SensorManager     │   │
│  │ OuiDatabase   │  │ WifiScanner       │   │
│  │ Dispatchers   │  │ MagneticSensor    │   │
│  └───────────────┘  │ LensDetector      │   │
│                      │ IrDetector        │   │
│  ┌───────────────┐  │ PortScanner       │   │
│  │ DatabaseModule│  └───────────────────┘   │
│  │ @Singleton    │                           │
│  │───────────────│  ┌───────────────────┐   │
│  │ AppDatabase   │  │ AnalysisModule    │   │
│  │ ReportDao     │  │ @Singleton        │   │
│  │ DeviceDao     │  │───────────────────│   │
│  │ ChecklistDao  │  │ CrossValidator    │   │
│  └───────────────┘  │ RiskCalculator    │   │
│                      │ NoiseFilter       │   │
│  ┌───────────────┐  │ RetroreflAnalyzer │   │
│  │RepositoryMod. │  └───────────────────┘   │
│  │───────────────│                           │
│  │ @Binds:       │                           │
│  │ WifiScanRepo  │                           │
│  │ MagneticRepo  │                           │
│  │ IrDetectRepo  │                           │
│  │ LensDetectRepo│                           │
│  │ ReportRepo    │                           │
│  └───────────────┘                           │
└─────────────────────────────────────────────┘
```

### 7.2 각 모듈 바인딩 상세

| 모듈 | 제공 타입 | 스코프 | 설명 |
|------|----------|--------|------|
| AppModule | Context, CoroutineDispatchers | @Singleton | 앱 전역 유틸리티 |
| SensorModule | WifiScanner, MagneticSensor, LensDetector, IrDetector, PortScanner | @Singleton | 센서 인스턴스 (하드웨어 접근) |
| DatabaseModule | AppDatabase, ReportDao, DeviceDao, ChecklistDao | @Singleton | Room DB + DAO |
| AnalysisModule | CrossValidator, RiskCalculator, NoiseFilter, RetroreflectionAnalyzer | @Singleton | 분석 엔진 |
| RepositoryModule | WifiScanRepository, MagneticRepository 등 (인터페이스 -> Impl 바인딩) | @Singleton | @Binds로 인터페이스 바인딩 |

### 7.3 ViewModel 주입 패턴

```kotlin
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val runQuickScan: RunQuickScanUseCase,
    private val runFullScan: RunFullScanUseCase,
    private val calculateRisk: CalculateRiskUseCase,
) : ViewModel() {
    // UseCase만 주입, Repository/Sensor 직접 접근 금지
}
```

**규칙**: ViewModel은 UseCase만 주입받는다. Repository나 Sensor를 직접 참조하지 않는다.

---

## 8. 확장 포인트 설계

### 8.1 새 탐지 레이어 추가 절차

Phase 2~3에서 새 탐지 방식(예: LiDAR, ML 자기장 분류)을 추가할 때의 확장 절차이다.

```
Step 1: domain/model/에 새 데이터 모델 추가
        예: LidarPoint.kt

Step 2: domain/repository/에 새 Repository 인터페이스 추가
        예: LidarDetectionRepository.kt

Step 3: data/sensor/에 새 센서 모듈 구현
        예: LidarDetector.kt

Step 4: data/repository/에 Repository 구현체 추가
        예: LidarDetectionRepositoryImpl.kt

Step 5: di/SensorModule.kt에 새 센서 바인딩 추가
        di/RepositoryModule.kt에 새 Repository 바인딩 추가

Step 6: CrossValidator.kt에 새 레이어 가중치 추가
        기존: W1(Wi-Fi)=0.50, W2(렌즈)=0.35, W3(EMF)=0.15
        변경: W1=0.40, W2=0.30, W3=0.10, W4(LiDAR)=0.20

Step 7: RunFullScanUseCase에 새 레이어 통합

Step 8: UI에 새 레이어 결과 표시 추가
```

### 8.2 확장 인터페이스 (Strategy 패턴)

```
<<interface>>
┌───────────────────────────┐
│     DetectionLayer        │
│───────────────────────────│
│ + layerName: String       │
│ + defaultWeight: Float    │
│ + isAvailable(): Boolean  │
│ + scan(): Flow<LayerResult>│
│ + getScore(): Int         │
│ + getEvidence(): String   │
└───────────┬───────────────┘
            │ implements
    ┌───────┼───────┬───────────┐
    │       │       │           │
┌───▼──┐ ┌─▼───┐ ┌─▼───┐  ┌──▼────┐
│WiFi  │ │Lens │ │ EMF │  │LiDAR  │
│Layer │ │Layer│ │Layer│  │Layer  │
└──────┘ └─────┘ └─────┘  └───────┘
                           (Phase 3)
```

### 8.3 교차 검증 엔진 확장 구조

CrossValidator는 `List<DetectionLayer>`를 입력받아 동적으로 가중치를 조정한다.

```
CrossValidator
├── 입력: List<DetectionLayer>
├── 가중치 조정: 사용 가능한 레이어만으로 정규화
│   예: Wi-Fi 불가 시 -> 나머지 레이어 가중치 재분배
├── 보정 계수: 양성 레이어 수에 따른 보정
│   1개 양성: x0.7
│   2개 양성: x1.2
│   3개 양성: x1.5
│   4개+ 양성: x1.6 (Phase 3)
└── 출력: RiskLevel + 근거 목록
```

---

## 9. 스레드 모델

| 작업 | Dispatcher | 이유 |
|------|-----------|------|
| Wi-Fi 스캔 (ARP, mDNS, 포트) | Dispatchers.IO | 네트워크 I/O |
| 렌즈 감지 (프레임 분석) | Dispatchers.Default | CPU 연산 집약 |
| IR 감지 (프레임 분석) | Dispatchers.Default | CPU 연산 집약 |
| 자기장 측정 | Dispatchers.Default | 센서 콜백 처리 |
| Room DB 읽기/쓰기 | Dispatchers.IO | 디스크 I/O |
| PDF 생성 | Dispatchers.IO | 파일 I/O |
| UI 상태 업데이트 | Dispatchers.Main | UI 스레드 |

---

## 10. 보안 아키텍처

### 10.1 데이터 처리 원칙

```
┌──────────────────────────────────────────┐
│          모든 데이터는 로컬 처리          │
│                                          │
│  센서 데이터 -> 분석 -> 결과 -> Room DB  │
│                                          │
│  ❌ 서버 전송 없음 (Phase 1)             │
│  ❌ 사용자 위치 수집 없음                 │
│  ❌ 네트워크 기기 MAC 외부 전송 없음      │
└──────────────────────────────────────────┘
```

### 10.2 권한 최소화

| 권한 | 용도 | 필수 여부 |
|------|------|----------|
| ACCESS_FINE_LOCATION | Wi-Fi 스캔 (Android 요구사항) | Quick/Full Scan 시 |
| CAMERA | 렌즈 감지, IR 감지 | 렌즈/IR 스캔 시 |
| INTERNET | 포트 스캔 (로컬 네트워크) | Quick/Full Scan 시 |
| VIBRATE | 위험 감지 시 진동 | 선택 |

---

## 11. 테스트 아키텍처

| 레이어 | 테스트 방식 | 도구 |
|--------|-----------|------|
| Domain (UseCase, Model) | Unit Test | JUnit5, MockK |
| Data (Repository Impl) | Unit Test + Integration | JUnit5, MockK, Turbine |
| Sensor | Instrumented Test | Android Test, Espresso |
| ViewModel | Unit Test | JUnit5, Turbine, MockK |
| UI (Composable) | UI Test | Compose Testing |
| 교차 검증 엔진 | Unit Test (매트릭스) | JUnit5, 파라미터 테스트 |

### 11.1 테스트 커버리지 목표

| 대상 | 목표 커버리지 |
|------|-------------|
| Domain 레이어 | 90%+ |
| 교차 검증 엔진 | 95%+ (핵심 로직) |
| 위험도 산출 | 95%+ (핵심 로직) |
| Repository Impl | 80%+ |
| ViewModel | 80%+ |
| 전체 | 80%+ |

---

*본 문서는 project-plan.md v3.1 기반으로 작성되었으며, Phase 1 (Android MVP) 아키텍처를 정의합니다.*
