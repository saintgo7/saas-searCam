# SearCam 기술 설계 문서 (TDD)

> **버전**: v1.0
> **작성일**: 2026-04-03
> **기반 문서**: project-plan.md v3.1
> **플랫폼**: Android (Kotlin + Jetpack Compose)
> **아키텍처**: Clean Architecture + MVVM

---

## 1. 문서 정보

| 항목 | 내용 |
|------|------|
| 문서 유형 | Technical Design Document (기술 설계 문서) |
| 프로젝트 | SearCam - 몰래카메라 탐지 앱 |
| 버전 | v1.0 |
| 작성일 | 2026-04-03 |
| 대상 독자 | 개발자, 코드 리뷰어 |
| 범위 | Phase 1 Android MVP |

### 1.1 변경 이력

| 버전 | 날짜 | 변경 내용 |
|------|------|----------|
| v1.0 | 2026-04-03 | 초기 작성 |

### 1.2 용어 정의

| 용어 | 정의 |
|------|------|
| Retroreflection | 빛이 들어온 방향으로 되돌아가는 역반사 현상 |
| OUI | Organizationally Unique Identifier, MAC 주소 앞 3바이트 제조사 식별자 |
| EMF | Electromagnetic Field, 전자기장 |
| RTSP | Real Time Streaming Protocol, 실시간 스트리밍 프로토콜 |
| mDNS | Multicast DNS, 로컬 네트워크 서비스 탐색 프로토콜 |
| SSDP | Simple Service Discovery Protocol, UPnP 기기 탐색 |
| Cross-Validation | 복수 탐지 레이어 결과를 교차 검증하여 정확도를 높이는 방식 |

---

## 2. 아키텍처 개요

### 2.1 Clean Architecture 레이어 구조

SearCam은 3-Layer Clean Architecture를 따른다. 의존성은 반드시 **바깥에서 안쪽**으로만 향한다.

```
┌──────────────────────────────────────────────────────────────┐
│                       UI Layer (ui/)                         │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐            │
│  │ Screen     │  │ ViewModel  │  │ UiState    │            │
│  │ (Compose)  │──│ (AAC VM)   │──│ (data class)│           │
│  └────────────┘  └─────┬──────┘  └────────────┘            │
│                        │ UseCase 호출                        │
├────────────────────────┼─────────────────────────────────────┤
│                  Domain Layer (domain/)                       │
│  ┌────────────┐  ┌─────┴──────┐  ┌────────────┐            │
│  │ Model      │  │ UseCase    │  │ Repository  │            │
│  │ (entity)   │  │ (비즈니스) │  │ (interface) │            │
│  └────────────┘  └────────────┘  └─────┬──────┘            │
│                                        │ 인터페이스만 정의   │
├────────────────────────────────────────┼─────────────────────┤
│                   Data Layer (data/)    │                     │
│  ┌────────────┐  ┌────────────┐  ┌─────┴──────┐            │
│  │ Sensor     │  │ Analysis   │  │ Repository  │            │
│  │ (하드웨어) │  │ (알고리즘) │  │ (구현체)   │            │
│  └────────────┘  └────────────┘  └────────────┘            │
│  ┌────────────┐  ┌────────────┐                             │
│  │ Local DB   │  │ PDF Gen    │                             │
│  │ (Room)     │  │ (iText)    │                             │
│  └────────────┘  └────────────┘                             │
└──────────────────────────────────────────────────────────────┘
```

### 2.2 의존성 방향 규칙

```
UI Layer ──────→ Domain Layer ←────── Data Layer
  (알고 있다)       (아무것도        (Domain을
                     모른다)         구현한다)
```

**핵심 규칙**:
- Domain Layer는 Android Framework 의존성을 가지지 않는다
- Domain Layer의 Repository는 인터페이스만 정의한다
- Data Layer가 Repository 인터페이스를 구현한다
- UI Layer는 UseCase를 통해서만 Domain에 접근한다

### 2.3 MVVM 패턴 적용

```
┌──────────┐    observe    ┌──────────┐    invoke    ┌──────────┐
│  Screen  │ ◄──────────── │ ViewModel│ ───────────► │ UseCase  │
│ (Compose)│    StateFlow  │ (AAC)    │              │ (Domain) │
└──────────┘               └──────────┘              └──────────┘
     │                          │                         │
     │ 사용자 이벤트             │ UiState 관리            │ 비즈니스 로직
     ▼                          ▼                         ▼
  Intent/Event ──────► ViewModel.onEvent() ──► UseCase.invoke()
                                                      │
                                               Repository (interface)
                                                      │
                                               Data Source (impl)
```

**데이터 흐름**: 단방향 (Unidirectional Data Flow)
- Event: Screen → ViewModel
- State: ViewModel → Screen (StateFlow)
- Side Effect: ViewModel → Screen (SharedFlow, 일회성 이벤트)

---

## 3. 모듈 설계

### 3.1 모듈 책임 매트릭스

```
com.searcam/
├── di/          → Hilt DI 모듈 정의. 의존성 바인딩 및 스코프 관리
├── domain/      → 순수 비즈니스 로직. Android 의존성 없음
│   ├── model/   → 도메인 엔티티 (불변 data class)
│   ├── usecase/ → 비즈니스 유스케이스 (단일 책임)
│   └── repository/ → 데이터 소스 추상화 (인터페이스)
├── data/        → 외부 시스템 연동. 센서, DB, 파일
│   ├── sensor/  → 하드웨어 센서 접근 (Wi-Fi, 카메라, 자력계)
│   ├── analysis/→ 탐지 알고리즘 (OUI 매칭, 교차 검증, 노이즈 필터)
│   ├── local/   → Room DB (리포트 저장)
│   └── pdf/     → PDF 리포트 생성
├── ui/          → Jetpack Compose 화면 및 ViewModel
│   ├── home/    → 홈 화면
│   ├── scan/    → Quick Scan / Full Scan 화면
│   ├── lens/    → 렌즈 감지 (Retroreflection + IR) 화면
│   ├── magnetic/→ EMF 자기장 감지 화면
│   ├── report/  → 리포트 목록/상세 화면
│   ├── checklist/ → 육안 점검 체크리스트 화면
│   ├── settings/→ 설정 화면
│   └── components/ → 공통 UI 컴포넌트
└── util/        → 횡단 관심사 (소리, 진동, 권한)
```

### 3.2 모듈 간 인터페이스 관계

```
┌─────────┐
│   di/   │─── 모든 모듈의 의존성을 바인딩
└────┬────┘
     │ @Provides / @Binds
     ▼
┌─────────┐     ┌──────────┐     ┌──────────┐
│  ui/    │────►│ domain/  │◄────│  data/   │
│Screen  │     │ UseCase  │     │ RepoImpl │
│ VM      │     │ Repo(IF) │     │ Sensor   │
└─────────┘     └──────────┘     └──────────┘
     │                                │
     ▼                                ▼
┌─────────┐                    ┌──────────┐
│  util/  │                    │  local/  │
│ Sound   │                    │  Room DB │
│ Vibrate │                    └──────────┘
│ Perm    │
└─────────┘
```

### 3.3 각 모듈 상세

#### 3.3.1 di/ (의존성 주입)

| 파일 | 책임 |
|------|------|
| `AppModule.kt` | 앱 전역 싱글톤 (Context, SharedPreferences, Dispatchers) |
| `SensorModule.kt` | 센서 관련 바인딩 (SensorManager, WifiManager, CameraX) |
| `DatabaseModule.kt` | Room DB, DAO 바인딩 |

#### 3.3.2 domain/ (비즈니스 로직)

Android Framework 의존성 제로. 순수 Kotlin만 사용한다.

| 하위 | 파일 | 책임 |
|------|------|------|
| model/ | `ScanResult.kt` | 단일 탐지 레이어 결과 |
| model/ | `RiskLevel.kt` | 위험도 등급 열거형 |
| model/ | `NetworkDevice.kt` | 네트워크 기기 정보 |
| model/ | `MagneticReading.kt` | 자기장 측정값 |
| model/ | `IrPoint.kt` | IR/렌즈 감지 포인트 |
| model/ | `RetroreflectionPoint.kt` | Retroreflection 감지 포인트 |
| model/ | `ScanReport.kt` | 종합 스캔 리포트 |
| model/ | `ScanMode.kt` | 스캔 모드 열거형 |
| model/ | `DeviceType.kt` | 기기 유형 열거형 |
| usecase/ | `RunQuickScanUseCase.kt` | Quick Scan 실행 (Wi-Fi만) |
| usecase/ | `RunFullScanUseCase.kt` | Full Scan 실행 (전체 레이어) |
| usecase/ | `CalculateRiskUseCase.kt` | 교차 검증 위험도 산출 |
| usecase/ | `ExportReportUseCase.kt` | 리포트 PDF 내보내기 |
| repository/ | `WifiScanRepository.kt` | Wi-Fi 스캔 추상화 |
| repository/ | `MagneticRepository.kt` | 자기장 센서 추상화 |
| repository/ | `LensDetectionRepository.kt` | 렌즈 감지 추상화 |
| repository/ | `IrDetectionRepository.kt` | IR 감지 추상화 |
| repository/ | `ReportRepository.kt` | 리포트 저장소 추상화 |

#### 3.3.3 data/ (데이터 레이어)

| 하위 | 파일 | 책임 |
|------|------|------|
| sensor/ | `WifiScanner.kt` | ARP, mDNS, SSDP 네트워크 스캔 |
| sensor/ | `MagneticSensor.kt` | 3축 자력계 데이터 수집 (20Hz) |
| sensor/ | `LensDetector.kt` | 플래시 Retroreflection 렌즈 감지 |
| sensor/ | `IrDetector.kt` | IR LED 감지 (전면 카메라) |
| sensor/ | `PortScanner.kt` | TCP 포트 스캔 (RTSP, HTTP, ONVIF) |
| analysis/ | `OuiDatabase.kt` | MAC OUI → 제조사 매칭 |
| analysis/ | `CrossValidator.kt` | 3-Layer 교차 검증 엔진 |
| analysis/ | `RiskCalculator.kt` | 가중치 기반 위험도 산출 |
| analysis/ | `NoiseFilter.kt` | 센서 노이즈 필터 |
| local/ | `AppDatabase.kt` | Room Database 정의 |
| local/ | `ReportDao.kt` | 리포트 CRUD DAO |
| local/ | `ReportEntity.kt` | DB 엔티티 |
| pdf/ | `PdfGenerator.kt` | PDF 리포트 생성 |

#### 3.3.4 util/ (유틸리티)

| 파일 | 책임 |
|------|------|
| `SoundManager.kt` | 경고 비프음 재생 |
| `VibrationManager.kt` | 위험도 기반 진동 패턴 |
| `PermissionHelper.kt` | 런타임 권한 요청/상태 관리 |

---

## 4. 클래스 다이어그램

### 4.1 Domain Model 관계

```
┌──────────────────┐
│    ScanReport     │
├──────────────────┤
│ id: String        │
│ mode: ScanMode    │
│ timestamp: Long   │
│ overallRisk: Int  │      ┌──────────────────┐
│ riskLevel: Risk   │─────►│    RiskLevel      │
│   Level           │      ├──────────────────┤
│ wifiResult:       │      │ SAFE             │
│   ScanResult?     │      │ INTEREST         │
│ lensResult:       │      │ CAUTION          │
│   ScanResult?     │      │ DANGER           │
│ magneticResult:   │      │ CRITICAL         │
│   ScanResult?     │      └──────────────────┘
│ locationNote:     │
│   String          │      ┌──────────────────┐
│ devices: List<    │─────►│  NetworkDevice    │
│   NetworkDevice>  │      ├──────────────────┤
│ retroPoints:      │      │ ip: String        │
│   List<Retro..>   │      │ mac: String       │
│ irPoints:         │      │ hostname: String? │
│   List<IrPoint>   │      │ vendor: String?   │
│ magneticReadings: │      │ deviceType:       │
│   List<Magnetic   │      │   DeviceType      │
│   Reading>        │      │ openPorts: List   │
└──────────────────┘      │ riskScore: Int    │
                           │ isCamera: Boolean │
                           └──────────────────┘

┌──────────────────┐      ┌──────────────────┐
│   ScanResult      │      │  ScanMode         │
├──────────────────┤      ├──────────────────┤
│ layerType:        │      │ QUICK             │
│   LayerType       │      │ FULL              │
│ score: Int        │      │ LENS_FINDER       │
│ maxScore: Int     │      │ IR_ONLY           │
│ findings: List    │      │ EMF_ONLY          │
│   <Finding>       │      └──────────────────┘
│ status: ScanStatus│
└──────────────────┘      ┌──────────────────┐
                           │  DeviceType       │
┌──────────────────┐      ├──────────────────┤
│ MagneticReading   │      │ IP_CAMERA         │
├──────────────────┤      │ SMART_CAMERA      │
│ timestamp: Long   │      │ ROUTER            │
│ x: Float          │      │ SMART_TV          │
│ y: Float          │      │ PHONE             │
│ z: Float          │      │ PRINTER           │
│ magnitude: Float  │      │ SMART_HOME        │
│ delta: Float      │      │ UNKNOWN           │
└──────────────────┘      └──────────────────┘

┌──────────────────────┐  ┌──────────────────┐
│ RetroreflectionPoint  │  │    IrPoint        │
├──────────────────────┤  ├──────────────────┤
│ x: Int                │  │ x: Int            │
│ y: Int                │  │ y: Int            │
│ size: Float           │  │ intensity: Float  │
│ circularity: Float    │  │ duration: Long    │
│ brightness: Float     │  │ isStable: Boolean │
│ contrastRatio: Float  │  │ color: IrColor    │
│ isStable: Boolean     │  │ riskScore: Int    │
│ flashDependency: Bool │  └──────────────────┘
│ riskScore: Int        │
└──────────────────────┘
```

### 4.2 UseCase 관계도

```
┌─────────────────────┐
│ RunQuickScanUseCase  │
├─────────────────────┤       ┌──────────────────────┐
│ wifiRepo: WifiScan  │──────►│ WifiScanRepository   │
│   Repository        │       │ (interface)           │
│ riskCalc: Calculate  │──┐   └──────────────────────┘
│   RiskUseCase        │  │
└─────────────────────┘  │   ┌──────────────────────┐
                          ├──►│ CalculateRiskUseCase  │
┌─────────────────────┐  │   ├──────────────────────┤
│ RunFullScanUseCase   │  │   │ crossValidator:       │
├─────────────────────┤  │   │   CrossValidator      │
│ wifiRepo: WifiScan  │──┘   └──────────────────────┘
│   Repository        │
│ lensRepo: LensDet   │──────►┌──────────────────────┐
│   ectionRepository   │       │ LensDetectionRepo    │
│ irRepo: IrDetection │──────►│ (interface)           │
│   Repository        │       └──────────────────────┘
│ magneticRepo:       │
│   MagneticRepository│──────►┌──────────────────────┐
│ riskCalc: Calculate  │       │ MagneticRepository   │
│   RiskUseCase        │       │ (interface)           │
│ reportRepo: Report  │       └──────────────────────┘
│   Repository        │
└─────────────────────┘

┌─────────────────────┐       ┌──────────────────────┐
│ ExportReportUseCase  │──────►│ ReportRepository     │
├─────────────────────┤       │ (interface)           │
│ reportRepo: Report  │       └──────────────────────┘
│   Repository        │
│ pdfGenerator:       │──────►┌──────────────────────┐
│   PdfGenerator      │       │ PdfGenerator         │
└─────────────────────┘       └──────────────────────┘
```

### 4.3 ViewModel 관계도

```
┌────────────────────┐      ┌────────────────────┐
│   HomeViewModel     │      │   ScanViewModel     │
├────────────────────┤      ├────────────────────┤
│ - uiState:         │      │ - uiState:         │
│   StateFlow<Home   │      │   StateFlow<Scan   │
│   UiState>         │      │   UiState>         │
│ - lastReport:      │      │ - quickScan:       │
│   StateFlow<Scan   │      │   RunQuickScan     │
│   Report?>         │      │   UseCase          │
│                    │      │ - fullScan:        │
│ + onQuickScan()    │      │   RunFullScan      │
│ + onFullScan()     │      │   UseCase          │
│ + onLensFinder()   │      │                    │
│ + onIrOnly()       │      │ + startQuickScan() │
│ + onEmfOnly()      │      │ + startFullScan()  │
└────────────────────┘      │ + cancelScan()     │
                             └────────────────────┘

┌────────────────────┐      ┌────────────────────┐
│  LensViewModel      │      │ MagneticViewModel   │
├────────────────────┤      ├────────────────────┤
│ - uiState:         │      │ - uiState:         │
│   StateFlow<Lens   │      │   StateFlow<       │
│   UiState>         │      │   MagneticUiState> │
│ - lensRepo:        │      │ - magneticRepo:    │
│   LensDetection    │      │   MagneticRepo     │
│   Repository       │      │                    │
│ - irRepo:          │      │ + startCalibration │
│   IrDetection      │      │ + startScan()      │
│   Repository       │      │ + stopScan()       │
│                    │      │ + adjustSensitivity│
│ + startRetro       │      └────────────────────┘
│   reflection()     │
│ + startIrDetect()  │      ┌────────────────────┐
│ + stopDetection()  │      │  ReportViewModel    │
└────────────────────┘      ├────────────────────┤
                             │ - reports:         │
                             │   StateFlow<List   │
                             │   <ScanReport>>    │
                             │                    │
                             │ + loadReports()    │
                             │ + exportPdf(id)    │
                             │ + deleteReport(id) │
                             └────────────────────┘
```

---

## 5. 시퀀스 다이어그램

### 5.1 Quick Scan 전체 흐름

```
사용자          HomeScreen    ScanVM       QuickScanUC    WifiScanRepo    WifiScanner
  │                │            │              │              │              │
  │ Quick Scan 탭  │            │              │              │              │
  │───────────────►│            │              │              │              │
  │                │ onQuick    │              │              │              │
  │                │ Scan()     │              │              │              │
  │                │───────────►│              │              │              │
  │                │            │ start        │              │              │
  │                │            │ QuickScan()  │              │              │
  │                │            │─────────────►│              │              │
  │                │            │              │ scanNetwork  │              │
  │                │            │              │()            │              │
  │                │            │              │─────────────►│              │
  │                │            │              │              │ scanArp()    │
  │                │            │              │              │─────────────►│
  │                │            │              │              │  ARP 기기    │
  │                │            │              │              │◄─────────────│
  │                │            │              │              │ scanMdns()   │
  │                │            │              │              │─────────────►│
  │                │            │              │              │  서비스 기기 │
  │                │            │              │              │◄─────────────│
  │                │            │              │              │ matchOui()   │
  │                │            │              │              │─────────────►│
  │                │            │              │              │  OUI 결과   │
  │                │            │              │              │◄─────────────│
  │                │            │              │              │ scanPorts()  │
  │                │            │              │              │─────────────►│
  │                │            │              │              │  포트 결과   │
  │                │            │              │◄─────────────│◄─────────────│
  │                │            │              │              │              │
  │                │            │              │ Flow<List    │              │
  │                │            │              │ <Network     │              │
  │                │            │              │  Device>>    │              │
  │                │            │◄─────────────│              │              │
  │                │            │              │              │              │
  │                │            │ RiskCalcUC   │              │              │
  │                │            │.invoke()     │              │              │
  │                │            │──────┐       │              │              │
  │                │            │      │ 위험도│              │              │
  │                │            │      │ 산출  │              │              │
  │                │            │◄─────┘       │              │              │
  │                │            │              │              │              │
  │                │ UiState    │              │              │              │
  │                │ (결과)     │              │              │              │
  │                │◄───────────│              │              │              │
  │ 결과 화면      │            │              │              │              │
  │◄───────────────│            │              │              │              │
```

### 5.2 Full Scan 전체 흐름

```
사용자       ScanVM        FullScanUC     WifiRepo    LensRepo    MagRepo    CrossValidator
  │            │              │             │           │           │            │
  │ Full Scan  │              │             │           │           │            │
  │───────────►│              │             │           │           │            │
  │            │ startFull    │             │           │           │            │
  │            │ Scan()       │             │           │           │            │
  │            │─────────────►│             │           │           │            │
  │            │              │             │           │           │            │
  │            │              │── [병렬 실행] ─────────────────────►│            │
  │            │              │             │           │           │            │
  │            │              │ Layer1:     │           │           │            │
  │            │              │ scanNetwork │           │           │            │
  │            │              │────────────►│           │           │            │
  │            │              │ wifiResult  │           │           │            │
  │            │◄ 진행률 20% ─│◄────────────│           │           │            │
  │◄ UI 업데이트│             │             │           │           │            │
  │            │              │             │           │           │            │
  │            │              │ Layer2:     │           │           │            │
  │            │              │ detectLens  │           │           │            │
  │            │              │────────────────────────►│           │            │
  │            │              │ lensResult  │           │           │            │
  │            │◄ 진행률 60% ─│◄────────────────────────│           │            │
  │◄ UI 업데이트│             │             │           │           │            │
  │            │              │             │           │           │            │
  │            │              │ Layer3:     │           │           │            │
  │            │              │ scanEMF     │           │           │            │
  │            │              │──────────────────────────────────►│            │
  │            │              │ emfResult   │           │           │            │
  │            │◄ 진행률 85% ─│◄──────────────────────────────────│            │
  │◄ UI 업데이트│             │             │           │           │            │
  │            │              │             │           │           │            │
  │            │              │ crossValidate(wifi, lens, emf)    │            │
  │            │              │──────────────────────────────────────────────►│
  │            │              │ ScanReport (종합 위험도)           │            │
  │            │◄ 진행률 100%─│◄──────────────────────────────────────────────│
  │◄ 결과 화면 │              │             │           │           │            │
```

### 5.3 Retroreflection 렌즈 감지 흐름

```
사용자       LensScreen    LensVM     LensDetRepo   LensDetector    CameraX
  │            │            │            │              │              │
  │ 렌즈 찾기  │            │            │              │              │
  │───────────►│            │            │              │              │
  │            │ startRetro │            │              │              │
  │            │ reflection │            │              │              │
  │            │───────────►│            │              │              │
  │            │            │ startDetect│              │              │
  │            │            │ ion(RETRO) │              │              │
  │            │            │───────────►│              │              │
  │            │            │            │ openCamera   │              │
  │            │            │            │ + flashOn    │              │
  │            │            │            │─────────────►│              │
  │            │            │            │              │ bindCamera() │
  │            │            │            │              │─────────────►│
  │            │            │            │              │ preview +    │
  │            │            │            │              │ imageAnalysis│
  │            │            │            │              │◄─────────────│
  │            │            │            │              │              │
  │            │            │            │◄── Flow<Frame> ─────────────│
  │            │            │            │              │              │
  ┌──── 프레임 분석 루프 (30fps) ─────────────────────────────────────┐
  │            │            │            │              │              │
  │            │            │            │ 1. 전처리    │              │
  │            │            │            │ 2. 고휘도    │              │
  │            │            │            │    포인트    │              │
  │            │            │            │    추출      │              │
  │            │            │            │ 3. 원형도    │              │
  │            │            │            │    검사      │              │
  │            │            │            │ 4. 안정성    │              │
  │            │            │            │    추적 (2초)│              │
  │            │            │            │ 5. 플래시    │              │
  │            │            │            │    의존성    │              │
  │            │            │            │    검증      │              │
  │            │            │            │              │              │
  │            │ Flow<List  │◄───────────│              │              │
  │            │ <Retro     │            │              │              │
  │            │ Point>>    │            │              │              │
  │            │◄───────────│            │              │              │
  │            │            │            │              │              │
  │ 의심 포인트│            │            │              │              │
  │ 빨간 원    │            │            │              │              │
  │ + 진동     │            │            │              │              │
  │◄───────────│            │            │              │              │
  └──── /루프 ────────────────────────────────────────────────────────┘
  │            │            │            │              │              │
  │ 중지       │            │            │              │              │
  │───────────►│ stopDetect │            │              │              │
  │            │───────────►│ stop()     │              │              │
  │            │            │───────────►│ releaseCamera│              │
  │            │            │            │─────────────►│              │
```

### 5.4 교차 검증 엔진 흐름

```
FullScanUC        CrossValidator       RiskCalculator
   │                   │                    │
   │ crossValidate(    │                    │
   │   wifiResult,     │                    │
   │   lensResult,     │                    │
   │   magneticResult) │                    │
   │──────────────────►│                    │
   │                   │                    │
   │                   │ 1. 가용 레이어 확인│
   │                   │    Wi-Fi 연결 여부 │
   │                   │    조명 환경       │
   │                   │                    │
   │                   │ 2. 가중치 동적 조정│
   │                   │    ┌──────────────┐│
   │                   │    │ Wi-Fi 있음:  ││
   │                   │    │ W1=0.50      ││
   │                   │    │ W2=0.35      ││
   │                   │    │ W3=0.15      ││
   │                   │    │              ││
   │                   │    │ Wi-Fi 없음:  ││
   │                   │    │ W1=0.00      ││
   │                   │    │ W2=0.75      ││
   │                   │    │ W3=0.25      ││
   │                   │    └──────────────┘│
   │                   │                    │
   │                   │ 3. 레이어별 점수   │
   │                   │    calculateWeight │
   │                   │    edScore()       │
   │                   │───────────────────►│
   │                   │                    │
   │                   │    weighted =      │
   │                   │    W1*L1 + W2*L2   │
   │                   │    + W3*L3         │
   │                   │◄───────────────────│
   │                   │                    │
   │                   │ 4. 보정 계수 적용  │
   │                   │    양성 레이어 수  │
   │                   │    count 계산      │
   │                   │                    │
   │                   │    1개: ×0.7       │
   │                   │    2개: ×1.2       │
   │                   │    3개: ×1.5       │
   │                   │                    │
   │                   │ 5. 최종 위험도     │
   │                   │    = weighted      │
   │                   │      * correction  │
   │                   │    clamp(0, 100)   │
   │                   │                    │
   │                   │ 6. RiskLevel 판정  │
   │                   │    0~19: SAFE      │
   │                   │    20~39: INTEREST │
   │                   │    40~59: CAUTION  │
   │                   │    60~79: DANGER   │
   │                   │    80~100: CRITICAL│
   │                   │                    │
   │  ScanReport       │                    │
   │◄──────────────────│                    │
```

---

## 6. 데이터 모델 상세

### 6.1 ScanReport (종합 스캔 리포트)

```kotlin
data class ScanReport(
    val id: String,                           // UUID
    val mode: ScanMode,                       // 스캔 모드
    val timestamp: Long,                      // Unix epoch millis
    val durationMs: Long,                     // 스캔 소요 시간 (ms)
    val overallRisk: Int,                     // 종합 위험도 (0~100)
    val riskLevel: RiskLevel,                 // 위험 등급
    val wifiResult: ScanResult?,              // Layer 1 결과 (Wi-Fi 미연결 시 null)
    val lensResult: ScanResult?,              // Layer 2A 결과
    val irResult: ScanResult?,                // Layer 2B 결과
    val magneticResult: ScanResult?,          // Layer 3 결과
    val devices: List<NetworkDevice>,         // 발견된 네트워크 기기
    val retroPoints: List<RetroreflectionPoint>, // 렌즈 의심 포인트
    val irPoints: List<IrPoint>,              // IR 의심 포인트
    val magneticReadings: List<MagneticReading>, // 자기장 측정 기록
    val correctionFactor: Float,              // 적용된 보정 계수
    val locationNote: String,                 // 사용자 메모 (위치)
    val findings: List<Finding>,              // 근거 기반 발견 사항
)
```

### 6.2 RiskLevel (위험 등급)

```kotlin
enum class RiskLevel(
    val minScore: Int,      // 최소 점수 (포함)
    val maxScore: Int,      // 최대 점수 (포함)
    val labelKo: String,    // 한국어 표시
    val colorHex: String,   // UI 색상 (hex)
) {
    SAFE(0, 19, "안전", "#4CAF50"),
    INTEREST(20, 39, "관심", "#8BC34A"),
    CAUTION(40, 59, "주의", "#FFC107"),
    DANGER(60, 79, "위험", "#FF9800"),
    CRITICAL(80, 100, "매우 위험", "#F44336");

    companion object {
        fun fromScore(score: Int): RiskLevel =
            entries.first { score in it.minScore..it.maxScore }
    }
}
```

### 6.3 ScanResult (레이어별 결과)

```kotlin
data class ScanResult(
    val layerType: LayerType,    // WIFI, LENS, IR, MAGNETIC
    val score: Int,              // 0~100
    val maxScore: Int,           // 100 (정규화 기준)
    val findings: List<Finding>, // 발견 사항 목록
    val status: ScanStatus,      // COMPLETED, SKIPPED, FAILED
    val startedAt: Long,         // 시작 시각
    val completedAt: Long,       // 완료 시각
)

enum class LayerType { WIFI, LENS, IR, MAGNETIC }
enum class ScanStatus { PENDING, RUNNING, COMPLETED, SKIPPED, FAILED }
```

### 6.4 Finding (발견 사항)

```kotlin
data class Finding(
    val type: FindingType,       // 발견 유형
    val description: String,     // 한국어 설명
    val evidence: String,        // 근거 (예: "MAC: 28:57:BE - Hikvision")
    val score: Int,              // 기여 점수
    val severity: Severity,      // LOW, MEDIUM, HIGH, CRITICAL
)

enum class FindingType {
    CAMERA_VENDOR_MAC,           // 카메라 제조사 MAC 일치
    RTSP_PORT_OPEN,              // RTSP 포트 개방
    HTTP_STREAM_PORT_OPEN,       // HTTP 스트리밍 포트 개방
    ONVIF_PORT_OPEN,             // ONVIF 포트 개방
    CAMERA_HOSTNAME,             // 호스트명에 카메라 키워드
    MDNS_RTSP_SERVICE,           // mDNS RTSP 서비스 광고
    LENS_RETROREFLECTION,        // 렌즈 역반사 감지
    IR_LED_DETECTED,             // IR LED 감지
    EMF_ANOMALY,                 // 자기장 이상
}

enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }
```

### 6.5 NetworkDevice (네트워크 기기)

```kotlin
data class NetworkDevice(
    val ip: String,              // IPv4 주소 (예: "192.168.1.45")
    val mac: String,             // MAC 주소 (예: "28:57:BE:XX:XX:XX")
    val hostname: String?,       // 호스트명 (null 가능)
    val vendor: String?,         // OUI 매칭 제조사 (null: 미매칭)
    val deviceType: DeviceType,  // 기기 유형
    val openPorts: List<Int>,    // 개방된 포트 목록
    val services: List<String>,  // mDNS 서비스명 목록
    val riskScore: Int,          // 기기별 위험 점수 (0~100)
    val isCamera: Boolean,       // 카메라 판정 여부
    val discoveryMethod: DiscoveryMethod, // 발견 방법
)

enum class DeviceType {
    IP_CAMERA,         // IP 카메라
    SMART_CAMERA,      // 스마트 카메라 (Wyze, Tapo 등)
    ROUTER,            // 공유기
    SMART_TV,          // 스마트 TV
    PHONE,             // 스마트폰
    PRINTER,           // 프린터
    SMART_HOME,        // IoT 기기
    UNKNOWN,           // 미식별
}

enum class DiscoveryMethod { ARP, MDNS, SSDP, PORT_SCAN }
```

### 6.6 MagneticReading (자기장 측정값)

```kotlin
data class MagneticReading(
    val timestamp: Long,       // 측정 시각 (epoch millis)
    val x: Float,              // X축 (uT), 범위: -200.0 ~ 200.0
    val y: Float,              // Y축 (uT), 범위: -200.0 ~ 200.0
    val z: Float,              // Z축 (uT), 범위: -200.0 ~ 200.0
    val magnitude: Float,      // 크기 = sqrt(x^2+y^2+z^2), 범위: 0 ~ 346.0
    val delta: Float,          // 기준 대비 변화량 (uT), 범위: 0 ~ 346.0
    val level: EmfLevel,       // 판정 등급
)

enum class EmfLevel(
    val minDelta: Float,       // 최소 변화량 (uT, 포함)
    val maxDelta: Float,       // 최대 변화량 (uT, 미포함)
    val score: Int,            // 기여 점수
) {
    NORMAL(0f, 5f, 0),
    INTEREST(5f, 15f, 20),
    CAUTION(15f, 30f, 50),
    SUSPECT(30f, 50f, 75),
    STRONG_SUSPECT(50f, Float.MAX_VALUE, 95);
}
```

### 6.7 RetroreflectionPoint (Retroreflection 감지 포인트)

```kotlin
data class RetroreflectionPoint(
    val x: Int,                   // 프레임 내 X 좌표 (pixel)
    val y: Int,                   // 프레임 내 Y 좌표 (pixel)
    val size: Float,              // 포인트 크기 (pixel), 유효: 1.0~10.0
    val circularity: Float,       // 원형도 (0.0~1.0), 렌즈: > 0.8
    val brightness: Float,        // 절대 밝기 (0~255)
    val contrastRatio: Float,     // 주변 대비 밝기 비율 (1.0~)
    val isStable: Boolean,        // 2초간 위치 안정성
    val flashDependency: Boolean, // 플래시 OFF 시 소실 여부
    val riskScore: Int,           // 포인트별 위험 점수 (0~100)
    val frameTimestamp: Long,     // 프레임 타임스탬프
)
```

### 6.8 IrPoint (IR 감지 포인트)

```kotlin
data class IrPoint(
    val x: Int,                // 프레임 내 X 좌표
    val y: Int,                // 프레임 내 Y 좌표
    val intensity: Float,      // 밝기 강도 (0~255)
    val duration: Long,        // 지속 시간 (ms), 의심: > 3000
    val isStable: Boolean,     // 위치 안정성
    val color: IrColor,        // 감지 색상
    val riskScore: Int,        // 포인트별 위험 점수 (0~100)
)

enum class IrColor { PURPLE, WHITE, RED }
```

### 6.9 ScanMode (스캔 모드)

```kotlin
enum class ScanMode(
    val labelKo: String,
    val estimatedDurationSec: Int,
    val layers: Set<LayerType>,
) {
    QUICK("빠른 스캔", 30, setOf(LayerType.WIFI)),
    FULL("정밀 스캔", 180, setOf(LayerType.WIFI, LayerType.LENS, LayerType.IR, LayerType.MAGNETIC)),
    LENS_FINDER("렌즈 찾기", -1, setOf(LayerType.LENS)),        // 수동, 시간 제한 없음
    IR_ONLY("IR 카메라", -1, setOf(LayerType.IR)),               // 수동
    EMF_ONLY("자기장", -1, setOf(LayerType.MAGNETIC)),           // 수동
}
```

---

## 7. 알고리즘 명세

### 7.1 Wi-Fi 스캔 알고리즘

```
함수 scanWifiNetwork(): Flow<List<NetworkDevice>>

  입력: 없음 (시스템 Wi-Fi 연결 상태 사용)
  출력: Flow<List<NetworkDevice>>
  전제조건: Wi-Fi 연결 상태

  STEP 1: 네트워크 연결 확인
    isWifiConnected = connectivityManager.isWifiConnected()
    IF NOT isWifiConnected:
      emit(emptyList())
      RETURN

  STEP 2: ARP 테이블 조회
    arpEntries = readFile("/proc/net/arp")
    PARSE 각 줄 → (ip, mac) 쌍 추출
    FILTER mac != "00:00:00:00:00:00"  // 무효 항목 제거
    devices = arpEntries.map { (ip, mac) →
      NetworkDevice(ip=ip, mac=mac, discoveryMethod=ARP)
    }

  STEP 3: mDNS 서비스 탐색 (병렬)
    serviceTypes = ["_rtsp._tcp", "_http._tcp", "_onvif._tcp"]
    FOR EACH serviceType IN serviceTypes:
      nsdManager.discoverServices(serviceType)
      ON 발견: device를 devices에 병합 (IP 기준 중복 제거)
    TIMEOUT: 5초

  STEP 4: SSDP M-SEARCH (병렬)
    multicastSocket.send(SSDP_MSEARCH_PACKET)
    응답 수신 (TIMEOUT: 3초)
    ON 응답: device를 devices에 병합

  STEP 5: MAC OUI 매칭
    FOR EACH device IN devices:
      ouiPrefix = device.mac.substring(0, 8)  // "28:57:BE"
      match = ouiDatabase.lookup(ouiPrefix)
      IF match != null:
        device = device.copy(
          vendor = match.vendor,
          deviceType = match.type
        )

  STEP 6: 포트 스캔 (의심 기기만)
    suspiciousDevices = devices.filter { it.vendor 카메라 제조사 OR it.deviceType == UNKNOWN }
    targetPorts = [554, 80, 8080, 8888, 3702, 1935]
    FOR EACH device IN suspiciousDevices:
      FOR EACH port IN targetPorts:
        isOpen = tryConnect(device.ip, port, timeout=1000ms)
        IF isOpen: device.openPorts += port

  STEP 7: 호스트명 패턴 매칭
    cameraKeywords = ["cam", "ipcam", "dvr", "nvr", "stream", "video", "hikvision", "dahua"]
    FOR EACH device IN devices:
      hostname = resolveHostname(device.ip)
      IF hostname에 cameraKeywords 포함:
        device.riskScore += 10

  STEP 8: 기기별 위험도 산출
    FOR EACH device IN devices:
      score = 0
      IF device.vendor가 카메라 제조사: score += 40
      IF 554 IN device.openPorts: score += 30
      IF 80 OR 8080 IN device.openPorts: score += 15
      IF 3702 IN device.openPorts: score += 20
      IF hostname에 카메라 키워드: score += 10
      IF mDNS _rtsp 서비스: score += 25
      device.riskScore = min(100, score)
      device.isCamera = score >= 40

  emit(devices)
```

### 7.2 Retroreflection 렌즈 감지 알고리즘

```
함수 detectRetroreflection(cameraFrameFlow: Flow<ImageProxy>):
    Flow<List<RetroreflectionPoint>>

  상태 변수:
    stablePointTracker: Map<PointId, TrackedPoint>  // 2초 윈도우 추적
    flashState: Boolean = true                       // 플래시 상태

  FOR EACH frame IN cameraFrameFlow:

    STEP 1: 전처리
      grayFrame = frame.toGrayscale()
      scaledFrame = grayFrame.resize(width=720)      // 분석용 해상도 축소

    STEP 2: 고휘도 포인트 추출
      meanBrightness = scaledFrame.mean()
      threshold = max(meanBrightness * 3.0, 240)     // 적응형 임계값
      brightPixels = scaledFrame.filter { pixel → pixel.value > threshold }
      // 크기 필터: 1~10 pixel
      candidates = connectedComponents(brightPixels)
        .filter { comp → comp.area in 1.0..10.0 }

    STEP 3: Retroreflection 특성 검사
      FOR EACH candidate IN candidates:
        circularity = 4 * PI * candidate.area / (candidate.perimeter^2)
        IF circularity < 0.8: CONTINUE                // 원형이 아님

        contrastRatio = candidate.meanBrightness / surroundingMeanBrightness(candidate, radius=20)
        IF contrastRatio < 5.0: CONTINUE               // 대비 부족

        // RGB 색상 분석 (원본 프레임)
        rgb = getColorAt(frame, candidate.center)
        isWhiteOrReddish = (rgb.r > 200 AND rgb.g > 180 AND rgb.b > 180)
                           OR (rgb.r > 200 AND rgb.r > rgb.g * 1.3)
        IF NOT isWhiteOrReddish: CONTINUE

        point = RetroreflectionPoint(
          x = candidate.center.x,
          y = candidate.center.y,
          size = candidate.area,
          circularity = circularity,
          brightness = candidate.meanBrightness,
          contrastRatio = contrastRatio,
        )

    STEP 4: 시간축 안정성 추적 (2초 윈도우)
      FOR EACH point IN filteredCandidates:
        existingTrack = stablePointTracker.findNear(point, tolerance=10px)
        IF existingTrack != null:
          existingTrack.updateWith(point)
          IF existingTrack.duration >= 2000ms:         // 2초 이상 유지
            point.isStable = true
        ELSE:
          stablePointTracker.add(point)

      // 깜빡이는 포인트 제거
      stablePointTracker.removeIf { it.blinkCount > 2 }
      // 이동하는 포인트 제거
      stablePointTracker.removeIf { it.totalMovement > 20px }

    STEP 5: 동적 플래시 검증 (5초 주기)
      IF 현재 시각 % 5초 == 0:
        flashState = false                             // 플래시 OFF (0.2초)
        delay(200ms)
        captureWithoutFlash = getCurrentFrame()
        FOR EACH trackedPoint IN stablePointTracker:
          pointInDarkFrame = findNear(captureWithoutFlash, trackedPoint, tolerance=10px)
          IF pointInDarkFrame == null OR pointInDarkFrame.brightness < threshold * 0.3:
            trackedPoint.flashDependency = true         // 플래시 의존 = 렌즈 가능성 UP
          ELSE:
            trackedPoint.flashDependency = false        // 자체 발광 = 렌즈 아닐 가능성
        flashState = true                              // 플래시 복원

    STEP 6: 위험도 채점
      FOR EACH point IN stablePointTracker.stablePoints():
        score = 0
        IF point.size in 1.0..5.0 AND point.circularity > 0.8: score += 30
        IF point.isStable: score += 20
        IF point.flashDependency: score += 25
        IF point.contrastRatio > 20.0: score += 15
        IF isWhiteOrReddish(point): score += 10

        // 감점
        IF point.size > 15.0: score -= 30              // 넓은 면적 반사
        IF point이 스마트폰 이동과 함께 이동: score -= 40
        IF point 형태가 불규칙: score -= 20

        point.riskScore = max(0, score)

    emit(stablePointTracker.toRetroreflectionPoints())
```

### 7.3 IR LED 감지 알고리즘

```
함수 detectIrLed(frontCameraFrameFlow: Flow<ImageProxy>):
    Flow<List<IrPoint>>

  전제조건: 어두운 환경 (ambientLight < 10 lux)

  상태 변수:
    irTracker: Map<PointId, TrackedIrPoint>            // 3초 윈도우

  FOR EACH frame IN frontCameraFrameFlow:

    STEP 1: 환경 밝기 체크
      IF ambientLight > 10 lux:
        emit 사용자 안내("방을 어둡게 해주세요")
        CONTINUE

    STEP 2: 고휘도 포인트 추출
      grayFrame = frame.toGrayscale()
      threshold = 200                                  // 어두운 환경 고정 임계값
      brightPixels = grayFrame.filter { it.value > threshold }
      candidates = connectedComponents(brightPixels)

    STEP 3: IR LED 특성 필터
      FOR EACH candidate IN candidates:
        rgb = getColorAt(frame, candidate.center)
        color = classifyIrColor(rgb)
        // IR LED 특성: 보라색 또는 순백색
        IF color NOT IN [PURPLE, WHITE]: CONTINUE
        // 지속적 발광 (깜빡이지 않음)
        // → 시간축에서 확인

    STEP 4: 시간축 필터 (3초 윈도우)
      FOR EACH candidate:
        track = irTracker.findNear(candidate, tolerance=15px)
        IF track != null:
          track.update(candidate)
          IF track.duration >= 3000ms AND NOT track.isBlinking:
            candidate를 의심 포인트로 승격
        ELSE:
          irTracker.add(candidate)

      // 깜빡이는 포인트 = 일반 LED → 제거
      irTracker.removeIf { it.blinkCount > 3 }
      // 이동하는 포인트 = 반사 → 제거
      irTracker.removeIf { it.totalMovement > 30px }

    STEP 5: 위험도 채점
      FOR EACH point IN irTracker.confirmedPoints():
        score = 0
        IF point.duration >= 3000ms: score += 30
        IF point.isStable: score += 25
        IF point.color == PURPLE: score += 20
        IF point.intensity > 220: score += 15
        point.riskScore = min(100, score)

    emit(irTracker.toIrPoints())
```

### 7.4 EMF 자기장 감지 알고리즘

```
함수 scanMagnetic(): Flow<MagneticReading>

  상태 변수:
    baseline: Float = 0f                               // 기준 자기장
    noiseFloor: Float = 0f                             // 노이즈 바닥
    recentReadings: CircularBuffer<Float>(size=10)     // 이동 평균용
    calibrated: Boolean = false

  Phase 0: 캘리브레이션 (3초)
    안내: "스마트폰을 공중에 들고 잠시 대기해주세요"
    samples = collect 60 samples at 20Hz (3초)
    magnitudes = samples.map { sqrt(it.x^2 + it.y^2 + it.z^2) }
    baseline = magnitudes.mean()
    noiseFloor = magnitudes.stdDev() * 2
    calibrated = true
    안내: "캘리브레이션 완료. 벽면과 가구를 천천히 비춰주세요."

  Phase 1: 실시간 스캔 (sensorManager 20Hz)
    FOR EACH sensorEvent:
      x = event.values[0]
      y = event.values[1]
      z = event.values[2]
      magnitude = sqrt(x^2 + y^2 + z^2)
      delta = abs(magnitude - baseline)

      // 노이즈 필터
      IF delta < noiseFloor: delta = 0
      // 급변 필터 (0.3초 내 50uT 이상 변화 = 자체 간섭)
      IF delta - previousDelta > 50 AND timeDiff < 300ms:
        CONTINUE                                       // 스마트폰 자체 간섭 무시

      // 이동 평균 (window=10)
      recentReadings.add(delta)
      smoothedDelta = recentReadings.average()

      // 판정
      level = WHEN {
        smoothedDelta < 5  → NORMAL
        smoothedDelta < 15 → INTEREST
        smoothedDelta < 30 → CAUTION
        smoothedDelta < 50 → SUSPECT
        ELSE               → STRONG_SUSPECT
      }

      reading = MagneticReading(
        timestamp = currentTimeMillis(),
        x = x, y = y, z = z,
        magnitude = magnitude,
        delta = smoothedDelta,
        level = level,
      )

      emit(reading)

      // 경고 피드백
      IF level >= CAUTION:
        vibrate(intensity = level.score)
        playBeep(frequency = 440 + level.score * 5)
```

### 7.5 교차 검증 엔진 알고리즘

```
함수 crossValidate(
    wifiResult: ScanResult?,
    lensResult: ScanResult?,
    irResult: ScanResult?,
    magneticResult: ScanResult?,
    context: ScanContext,
): ScanReport

  STEP 1: 가용 레이어 확인
    hasWifi = wifiResult != null AND wifiResult.status == COMPLETED
    hasLens = lensResult != null AND lensResult.status == COMPLETED
    hasIr = irResult != null AND irResult.status == COMPLETED
    hasMagnetic = magneticResult != null AND magneticResult.status == COMPLETED

  STEP 2: 가중치 동적 조정
    w1, w2, w3 = WHEN {
      hasWifi AND hasLens AND hasMagnetic →
        (0.50, 0.35, 0.15)                            // 전체 가용
      NOT hasWifi AND context.isDark →
        (0.00, 0.75, 0.25)                            // Wi-Fi 없음 + 암실
      NOT hasWifi AND NOT context.isDark →
        (0.00, 0.80, 0.20)                            // Wi-Fi 없음 + 밝음 (Stage A만)
      hasWifi AND NOT hasIr →
        (0.55, 0.30, 0.15)                            // IR 불가 (밝은 환경)
      ELSE →
        (0.50, 0.35, 0.15)                            // 기본값
    }

  STEP 3: 레이어별 점수 추출
    l1Score = wifiResult?.score ?: 0
    l2Score = max(lensResult?.score ?: 0, irResult?.score ?: 0)  // A, B 중 높은 값
    l3Score = magneticResult?.score ?: 0

  STEP 4: 가중 합산
    weightedScore = (w1 * l1Score) + (w2 * l2Score) + (w3 * l3Score)

  STEP 5: 보정 계수 결정
    positiveCount = count(
      l1Score >= 40,
      l2Score >= 40,
      l3Score >= 40,
    )

    correctionFactor = WHEN {
      positiveCount == 1 → 0.7                         // 1개 레이어만 양성
      positiveCount == 2 → 1.2                         // 2개 양성
      positiveCount == 3 → 1.5                         // 3개 모두 양성
      ELSE → 1.0
    }

    // 특수 보정
    IF hasWifi AND hasIr AND 동일 방향 감지:
      correctionFactor = max(correctionFactor, 1.4)
    IF l1Score >= 40 AND l2Score < 20 AND l3Score < 20:
      correctionFactor = min(correctionFactor, 0.9)    // Wi-Fi만 양성
    IF l3Score >= 40 AND l1Score < 20 AND l2Score < 20:
      correctionFactor = min(correctionFactor, 0.5)    // 자기장만 양성 (오탐 높음)

  STEP 6: 최종 위험도 산출
    overallRisk = (weightedScore * correctionFactor).toInt().coerceIn(0, 100)
    riskLevel = RiskLevel.fromScore(overallRisk)

  STEP 7: 근거 리포트 생성
    findings = buildFindings(wifiResult, lensResult, irResult, magneticResult)

  RETURN ScanReport(
    id = UUID.randomUUID().toString(),
    overallRisk = overallRisk,
    riskLevel = riskLevel,
    correctionFactor = correctionFactor,
    findings = findings,
    ...
  )
```

---

## 8. 인터페이스 명세

### 8.1 Repository 인터페이스

```kotlin
// Wi-Fi 스캔
interface WifiScanRepository {
    fun scanNetwork(): Flow<WifiScanState>
    suspend fun getDeviceDetails(ip: String): NetworkDevice?
    fun isWifiConnected(): Boolean
}

sealed interface WifiScanState {
    data object Idle : WifiScanState
    data class Scanning(val phase: String, val progress: Float) : WifiScanState
    data class ArpComplete(val devices: List<NetworkDevice>) : WifiScanState
    data class MdnsComplete(val services: List<NetworkDevice>) : WifiScanState
    data class PortScanComplete(val devices: List<NetworkDevice>) : WifiScanState
    data class Complete(val result: ScanResult, val devices: List<NetworkDevice>) : WifiScanState
    data class Error(val error: ScanError) : WifiScanState
}

// 렌즈 감지
interface LensDetectionRepository {
    fun startDetection(): Flow<List<RetroreflectionPoint>>
    fun stopDetection()
    fun isFlashAvailable(): Boolean
}

// IR 감지
interface IrDetectionRepository {
    fun startDetection(): Flow<List<IrPoint>>
    fun stopDetection()
    fun isEnvironmentDark(): Boolean
}

// 자기장 감지
interface MagneticRepository {
    fun startCalibration(): Flow<CalibrationState>
    fun startScan(): Flow<MagneticReading>
    fun stopScan()
    fun setSensitivity(sensitivity: EmfSensitivity)
}

sealed interface CalibrationState {
    data object NotStarted : CalibrationState
    data class InProgress(val progress: Float) : CalibrationState
    data class Completed(val baseline: Float, val noiseFloor: Float) : CalibrationState
    data class Failed(val reason: String) : CalibrationState
}

enum class EmfSensitivity(val thresholdMicroTesla: Float) {
    SENSITIVE(3f),
    NORMAL(8f),
    STABLE(20f),
}

// 리포트 저장소
interface ReportRepository {
    fun getAll(): Flow<List<ScanReport>>
    suspend fun getById(id: String): ScanReport?
    suspend fun save(report: ScanReport)
    suspend fun delete(id: String)
    suspend fun getRecentReports(limit: Int): List<ScanReport>
}
```

### 8.2 UseCase 인터페이스

```kotlin
// Quick Scan
class RunQuickScanUseCase @Inject constructor(
    private val wifiScanRepository: WifiScanRepository,
    private val calculateRiskUseCase: CalculateRiskUseCase,
) {
    operator fun invoke(): Flow<QuickScanState>
}

sealed interface QuickScanState {
    data object Idle : QuickScanState
    data class Scanning(val progress: Float, val phase: String) : QuickScanState
    data class Complete(val report: ScanReport) : QuickScanState
    data class Error(val error: ScanError) : QuickScanState
}

// Full Scan
class RunFullScanUseCase @Inject constructor(
    private val wifiScanRepository: WifiScanRepository,
    private val lensDetectionRepository: LensDetectionRepository,
    private val irDetectionRepository: IrDetectionRepository,
    private val magneticRepository: MagneticRepository,
    private val calculateRiskUseCase: CalculateRiskUseCase,
    private val reportRepository: ReportRepository,
) {
    operator fun invoke(): Flow<FullScanState>
}

sealed interface FullScanState {
    data object Idle : FullScanState
    data class Layer1(val progress: Float) : FullScanState
    data class Layer2(val progress: Float) : FullScanState
    data class Layer3(val progress: Float) : FullScanState
    data class CrossValidating(val progress: Float) : FullScanState
    data class Complete(val report: ScanReport) : FullScanState
    data class Error(val error: ScanError) : FullScanState
}

// 위험도 산출
class CalculateRiskUseCase @Inject constructor(
    private val crossValidator: CrossValidator,
) {
    operator fun invoke(
        wifiResult: ScanResult?,
        lensResult: ScanResult?,
        irResult: ScanResult?,
        magneticResult: ScanResult?,
        context: ScanContext,
    ): ScanReport
}

// 리포트 내보내기
class ExportReportUseCase @Inject constructor(
    private val reportRepository: ReportRepository,
    private val pdfGenerator: PdfGenerator,
) {
    suspend operator fun invoke(reportId: String): Result<File>
}
```

### 8.3 ViewModel 상태 정의

```kotlin
// 홈 화면 상태
data class HomeUiState(
    val lastReport: ScanReport? = null,
    val isScanning: Boolean = false,
    val permissionsGranted: Boolean = false,
    val wifiConnected: Boolean = false,
)

// 스캔 화면 상태
data class ScanUiState(
    val scanMode: ScanMode = ScanMode.QUICK,
    val scanPhase: ScanPhase = ScanPhase.IDLE,
    val progress: Float = 0f,                         // 0.0 ~ 1.0
    val currentLayer: String = "",                     // "Wi-Fi 스캔 중..."
    val discoveredDevices: List<NetworkDevice> = emptyList(),
    val report: ScanReport? = null,
    val error: ScanError? = null,
)

enum class ScanPhase { IDLE, SCANNING, ANALYZING, COMPLETE, ERROR }

// 렌즈 감지 화면 상태
data class LensUiState(
    val mode: LensMode = LensMode.RETROREFLECTION,
    val isDetecting: Boolean = false,
    val retroPoints: List<RetroreflectionPoint> = emptyList(),
    val irPoints: List<IrPoint> = emptyList(),
    val isFlashOn: Boolean = false,
    val isDarkEnough: Boolean = true,                  // IR 모드용
    val userGuideMessage: String = "",
    val error: ScanError? = null,
)

enum class LensMode { RETROREFLECTION, IR }

// 자기장 화면 상태
data class MagneticUiState(
    val calibrationState: CalibrationState = CalibrationState.NotStarted,
    val isScanning: Boolean = false,
    val currentReading: MagneticReading? = null,
    val readingHistory: List<MagneticReading> = emptyList(),  // 그래프용 (최근 100개)
    val maxDelta: Float = 0f,
    val sensitivity: EmfSensitivity = EmfSensitivity.NORMAL,
    val error: ScanError? = null,
)

// 리포트 화면 상태
data class ReportUiState(
    val reports: List<ScanReport> = emptyList(),
    val selectedReport: ScanReport? = null,
    val isExporting: Boolean = false,
    val exportResult: ExportResult? = null,
    val error: ScanError? = null,
)

sealed interface ExportResult {
    data class Success(val filePath: String) : ExportResult
    data class Failure(val message: String) : ExportResult
}
```

---

## 9. 에러 처리 설계

### 9.1 에러 분류 체계

```kotlin
sealed class ScanError(
    val code: String,
    val messageKo: String,
    val severity: ErrorSeverity,
) {
    // 권한 관련 (P: Permission)
    data object WifiPermissionDenied : ScanError(
        "P001", "Wi-Fi 스캔 권한이 필요합니다", ErrorSeverity.BLOCKING
    )
    data object LocationPermissionDenied : ScanError(
        "P002", "위치 권한이 필요합니다 (Wi-Fi 스캔용)", ErrorSeverity.BLOCKING
    )
    data object CameraPermissionDenied : ScanError(
        "P003", "카메라 권한이 필요합니다 (렌즈 감지용)", ErrorSeverity.BLOCKING
    )

    // 하드웨어 관련 (H: Hardware)
    data object WifiNotConnected : ScanError(
        "H001", "Wi-Fi에 연결되어 있지 않습니다", ErrorSeverity.RECOVERABLE
    )
    data object FlashNotAvailable : ScanError(
        "H002", "플래시를 사용할 수 없습니다", ErrorSeverity.RECOVERABLE
    )
    data object MagneticSensorNotAvailable : ScanError(
        "H003", "자기장 센서를 사용할 수 없습니다", ErrorSeverity.RECOVERABLE
    )
    data object CameraInitFailed : ScanError(
        "H004", "카메라 초기화에 실패했습니다", ErrorSeverity.RECOVERABLE
    )

    // 네트워크 관련 (N: Network)
    data object ArpReadFailed : ScanError(
        "N001", "네트워크 기기 목록을 읽을 수 없습니다", ErrorSeverity.DEGRADED
    )
    data object PortScanTimeout : ScanError(
        "N002", "포트 스캔 시간이 초과되었습니다", ErrorSeverity.DEGRADED
    )
    data object MdnsDiscoveryFailed : ScanError(
        "N003", "서비스 탐색에 실패했습니다", ErrorSeverity.DEGRADED
    )

    // 분석 관련 (A: Analysis)
    data object CalibrationFailed : ScanError(
        "A001", "캘리브레이션에 실패했습니다. 다시 시도해주세요", ErrorSeverity.RECOVERABLE
    )
    data object FrameProcessingFailed : ScanError(
        "A002", "프레임 분석 중 오류가 발생했습니다", ErrorSeverity.DEGRADED
    )

    // 저장 관련 (S: Storage)
    data object ReportSaveFailed : ScanError(
        "S001", "리포트 저장에 실패했습니다", ErrorSeverity.RECOVERABLE
    )
    data object PdfExportFailed : ScanError(
        "S002", "PDF 내보내기에 실패했습니다", ErrorSeverity.RECOVERABLE
    )
}

enum class ErrorSeverity {
    BLOCKING,      // 스캔 불가 (권한 없음 등)
    RECOVERABLE,   // 재시도 가능
    DEGRADED,      // 일부 기능 저하, 나머지 레이어로 계속 진행
}
```

### 9.2 에러 코드 체계

| 접두사 | 범주 | 범위 |
|--------|------|------|
| P | Permission (권한) | P001 ~ P099 |
| H | Hardware (하드웨어) | H001 ~ H099 |
| N | Network (네트워크) | N001 ~ N099 |
| A | Analysis (분석) | A001 ~ A099 |
| S | Storage (저장) | S001 ~ S099 |

### 9.3 에러 처리 전략

```
BLOCKING 에러:
  → 스캔 중단
  → 권한 요청 다이얼로그 표시
  → 설정 앱으로 이동 유도

RECOVERABLE 에러:
  → 사용자에게 안내 메시지 표시
  → "다시 시도" 버튼 제공
  → 최대 2회 자동 재시도

DEGRADED 에러:
  → 실패한 레이어를 SKIPPED 처리
  → 나머지 레이어로 계속 진행
  → 결과 화면에 "일부 기능이 제한되었습니다" 안내
  → 가중치 자동 재조정
```

---

## 10. 상태 관리

### 10.1 ViewModel 상태 머신 - ScanViewModel

```
                  ┌──────┐
                  │ IDLE │
                  └──┬───┘
                     │ startScan()
                     ▼
              ┌──────────────┐
              │   SCANNING   │
              │              │
              │ Layer1 진행  │──── cancelScan() ───► IDLE
              │ Layer2 진행  │
              │ Layer3 진행  │──── 에러(DEGRADED) ──► SCANNING (다음 레이어)
              │              │
              │ 교차 검증    │──── 에러(BLOCKING) ──► ERROR
              └──────┬───────┘
                     │ 완료
                     ▼
              ┌──────────────┐
              │   COMPLETE   │
              │              │
              │ ScanReport   │──── newScan() ────► IDLE
              │ 표시         │──── savePdf() ────► COMPLETE (PDF 저장)
              └──────────────┘

              ┌──────────────┐
              │    ERROR     │
              │              │──── retry() ──────► SCANNING
              │ ScanError    │──── dismiss() ────► IDLE
              └──────────────┘
```

### 10.2 MagneticViewModel 상태 머신

```
         ┌──────────────┐
         │  NOT_STARTED  │
         └──────┬───────┘
                │ startCalibration()
                ▼
         ┌──────────────┐
         │ CALIBRATING   │
         │              │──── 실패 ──► NOT_STARTED (에러 메시지)
         │ 3초 수집      │
         └──────┬───────┘
                │ 완료
                ▼
         ┌──────────────┐
         │  CALIBRATED   │
         └──────┬───────┘
                │ startScan()
                ▼
         ┌──────────────┐
         │   SCANNING    │
         │              │──── stopScan() ──► CALIBRATED
         │ 실시간 20Hz   │
         │ 그래프 갱신   │──── 센서 에러 ──► ERROR
         └──────────────┘

         ┌──────────────┐
         │    ERROR      │──── recalibrate() ──► NOT_STARTED
         └──────────────┘
```

### 10.3 UI State 전이 규칙

| 현재 상태 | 이벤트 | 다음 상태 | 부수효과 |
|-----------|--------|-----------|---------|
| IDLE | startQuickScan | SCANNING | 진행률 UI 표시 |
| IDLE | startFullScan | SCANNING | 진행률 UI 표시 |
| SCANNING | progress update | SCANNING | 진행률/단계 갱신 |
| SCANNING | cancelScan | IDLE | 리소스 해제 |
| SCANNING | error(BLOCKING) | ERROR | 에러 다이얼로그 |
| SCANNING | error(DEGRADED) | SCANNING | 토스트 + 다음 레이어 |
| SCANNING | complete | COMPLETE | 결과 화면 전환 |
| COMPLETE | newScan | IDLE | 결과 초기화 |
| ERROR | retry | SCANNING | 재시도 |
| ERROR | dismiss | IDLE | 에러 초기화 |

---

## 11. 스레딩 모델

### 11.1 Coroutine Dispatcher 전략

```kotlin
// AppModule.kt 에서 제공
@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main
}
```

| 작업 | Dispatcher | 이유 |
|------|------------|------|
| ARP 파일 읽기 | IO | 파일 I/O |
| mDNS 서비스 탐색 | IO | 네트워크 I/O |
| 포트 스캔 (TCP connect) | IO | 네트워크 I/O, 병렬 처리 |
| OUI DB 매칭 | Default | CPU 바운드 |
| 카메라 프레임 분석 | Default | CPU 바운드 (이미지 처리) |
| 자기장 센서 읽기 | Main | SensorManager 콜백 (Main 필수) |
| 교차 검증 계산 | Default | CPU 바운드 |
| Room DB 쿼리 | IO | DB I/O |
| PDF 생성 | IO | 파일 쓰기 |
| UI 상태 업데이트 | Main | Compose recomposition |

### 11.2 센서 데이터 Flow 파이프라인

```
[자기장 센서]                    [카메라 프레임]
     │                                │
     ▼ (Main - SensorManager)         ▼ (IO - CameraX ImageAnalysis)
callbackFlow {                   callbackFlow {
  sensorManager.register()         imageAnalysis.setAnalyzer()
  awaitClose { unregister() }      awaitClose { unbind() }
}                                }
     │                                │
     ▼ .flowOn(Main)                  ▼ .flowOn(IO)
     │                                │
     ▼ .map { 전처리 }               ▼ .map { 프레임 분석 }
     │   .flowOn(Default)             │   .flowOn(Default)
     │                                │
     ▼ .conflate()                    ▼ .conflate()
     │  (뒤처진 프레임 버림)           │  (최신 분석 결과만)
     │                                │
     ▼ .onEach { UI 업데이트 }        ▼ .onEach { UI 업데이트 }
     │   .flowOn(Main)                │   .flowOn(Main)
     │                                │
     ▼ ViewModel에서 collect           ▼ ViewModel에서 collect
```

### 11.3 병렬 실행 패턴 (Full Scan)

```kotlin
// RunFullScanUseCase 내부
suspend fun execute(): Flow<FullScanState> = flow {
    // Layer 1: Wi-Fi 스캔
    emit(FullScanState.Layer1(0f))
    val wifiResult = withContext(ioDispatcher) {
        wifiScanRepository.scanNetwork().last()
    }

    // Layer 2 + Layer 3: 병렬 실행
    emit(FullScanState.Layer2(0f))
    val (lensResult, magneticResult) = coroutineScope {
        val lensDeferred = async(defaultDispatcher) {
            lensDetectionRepository.detect(durationMs = 10_000)
        }
        val magneticDeferred = async(defaultDispatcher) {
            magneticRepository.scan(durationMs = 15_000)
        }
        lensDeferred.await() to magneticDeferred.await()
    }

    // 교차 검증
    emit(FullScanState.CrossValidating(0.9f))
    val report = withContext(defaultDispatcher) {
        calculateRiskUseCase(wifiResult, lensResult, null, magneticResult, context)
    }

    emit(FullScanState.Complete(report))
}
```

---

## 12. 메모리 관리

### 12.1 카메라 프레임 버퍼 관리

```
전략: 단일 프레임 처리 (conflate 패턴)

  CameraX ImageAnalysis
       │
       ▼ STRATEGY_KEEP_ONLY_LATEST
       │  (최신 프레임만 유지, 분석 중 다음 프레임 도착 시 버림)
       │
       ▼ ImageProxy
       │  분석 완료 후 반드시 close() 호출
       │
       ▼ 분석 결과만 Flow로 전달 (프레임 자체는 전달하지 않음)
```

**핵심 규칙**:
- `ImageProxy.close()` 호출 누락 방지 → `use {}` 블록 활용
- 프레임을 ViewModel이나 UI에 직접 전달하지 않음
- 분석 결과 (RetroreflectionPoint 등) 경량 data class만 전달

```kotlin
// LensDetector.kt 내부
imageAnalysis.setAnalyzer(executor) { imageProxy ->
    imageProxy.use { proxy ->  // 자동 close 보장
        val frame = proxy.toGrayscale()
        val points = analyzeFrame(frame)
        _pointsFlow.emit(points)  // 경량 결과만 전달
    }
}
```

### 12.2 센서 데이터 윈도우 관리

```
자기장 데이터:
  ┌─ 이동 평균 윈도우: CircularBuffer(size=10)
  │  10개 샘플만 유지, 가장 오래된 것부터 덮어쓰기
  │
  ├─ 그래프 표시용: CircularBuffer(size=100)
  │  최근 5초 (20Hz * 5초 = 100개)
  │
  └─ 리포트 저장용: 스캔 전체 기록
     최대 6000개 (20Hz * 300초 = 5분)
     메모리: ~6000 * 40byte = ~240KB

카메라 분석:
  ┌─ 포인트 추적 윈도우: Map<PointId, TrackedPoint>
  │  최대 50개 포인트 추적
  │  2초 이상 미갱신 포인트 자동 제거
  │
  └─ 분석 결과 캐시: 최신 1개 프레임 결과만 유지
```

### 12.3 메모리 제한

| 컴포넌트 | 최대 메모리 | 관리 방식 |
|----------|-----------|----------|
| OUI DB (JSON) | ~500KB | 앱 시작 시 1회 로드, 싱글톤 |
| 카메라 프레임 | ~1.5MB | 단일 프레임, 즉시 해제 |
| 자기장 히스토리 | ~240KB | CircularBuffer, 고정 크기 |
| 포인트 트래커 | ~10KB | 최대 50개, TTL 2초 |
| Room DB 캐시 | ~2MB | 최근 10건 리포트 |
| PDF 생성 버퍼 | ~5MB | 생성 후 즉시 해제 |

---

## 13. DI (의존성 주입) 설계

### 13.1 Hilt 모듈 구조

```
@HiltAndroidApp
SearCamApp
     │
     ▼
┌─────────────────────────────────────────────┐
│            SingletonComponent                │
│                                              │
│  AppModule                                   │
│  ├─ @Provides Context                        │
│  ├─ @Provides SharedPreferences              │
│  ├─ @Provides CoroutineDispatchers           │
│  └─ @Provides CrossValidator                 │
│                                              │
│  SensorModule                                │
│  ├─ @Provides SensorManager                  │
│  ├─ @Provides WifiManager                    │
│  ├─ @Provides ConnectivityManager            │
│  ├─ @Provides NsdManager                     │
│  ├─ @Binds WifiScanRepository                │
│  ├─ @Binds MagneticRepository                │
│  ├─ @Binds LensDetectionRepository           │
│  └─ @Binds IrDetectionRepository             │
│                                              │
│  DatabaseModule                              │
│  ├─ @Provides AppDatabase (Room)             │
│  ├─ @Provides ReportDao                      │
│  └─ @Binds ReportRepository                  │
│                                              │
│  AnalysisModule                              │
│  ├─ @Provides OuiDatabase                    │
│  ├─ @Provides RiskCalculator                 │
│  └─ @Provides NoiseFilter                    │
│                                              │
└─────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────┐
│          ViewModelComponent                  │
│                                              │
│  (자동 바인딩 — @HiltViewModel)               │
│  ├─ HomeViewModel                            │
│  ├─ ScanViewModel                            │
│  ├─ LensViewModel                            │
│  ├─ MagneticViewModel                        │
│  ├─ ReportViewModel                          │
│  └─ ChecklistViewModel                       │
│                                              │
└─────────────────────────────────────────────┘
```

### 13.2 스코프 정의

| 스코프 | 대상 | 생명주기 |
|--------|------|---------|
| `@Singleton` | OuiDatabase, AppDatabase, SharedPreferences, Dispatchers | 앱 전체 |
| `@Singleton` | CrossValidator, RiskCalculator, NoiseFilter | 앱 전체 |
| `@Singleton` | Repository 구현체 (WifiScanner, MagneticSensor 등) | 앱 전체 |
| `@ViewModelScoped` | UseCase 인스턴스 | ViewModel 생명주기 |
| (스코프 없음) | 센서 콜백, Flow 수집기 | 호출 시 생성/해제 |

### 13.3 Qualifier 정의

```kotlin
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher
```

### 13.4 모듈 상세 코드 구조

```kotlin
// AppModule.kt
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences =
        context.getSharedPreferences("searcam_prefs", Context.MODE_PRIVATE)

    @Provides
    @Singleton
    fun provideCrossValidator(
        riskCalculator: RiskCalculator,
    ): CrossValidator = CrossValidator(riskCalculator)
}

// SensorModule.kt
@Module
@InstallIn(SingletonComponent::class)
abstract class SensorModule {

    @Binds
    @Singleton
    abstract fun bindWifiScanRepository(
        impl: WifiScannerImpl
    ): WifiScanRepository

    @Binds
    @Singleton
    abstract fun bindMagneticRepository(
        impl: MagneticSensorImpl
    ): MagneticRepository

    @Binds
    @Singleton
    abstract fun bindLensDetectionRepository(
        impl: LensDetectorImpl
    ): LensDetectionRepository

    @Binds
    @Singleton
    abstract fun bindIrDetectionRepository(
        impl: IrDetectorImpl
    ): IrDetectionRepository

    companion object {
        @Provides
        fun provideSensorManager(
            @ApplicationContext context: Context
        ): SensorManager =
            context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        @Provides
        fun provideWifiManager(
            @ApplicationContext context: Context
        ): WifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        @Provides
        fun provideConnectivityManager(
            @ApplicationContext context: Context
        ): ConnectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        @Provides
        fun provideNsdManager(
            @ApplicationContext context: Context
        ): NsdManager =
            context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }
}

// DatabaseModule.kt
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "searcam_db"
    ).build()

    @Provides
    fun provideReportDao(db: AppDatabase): ReportDao = db.reportDao()
}
```

---

## 14. 설정 관리

### 14.1 사용자 설정 항목

| 키 | 타입 | 기본값 | 설명 | 유효 범위 |
|----|------|--------|------|----------|
| `emf_sensitivity` | String | `"NORMAL"` | 자기장 감도 | SENSITIVE, NORMAL, STABLE |
| `sound_enabled` | Boolean | `true` | 경고음 활성화 | true/false |
| `vibration_enabled` | Boolean | `true` | 진동 알림 활성화 | true/false |
| `auto_save_report` | Boolean | `true` | 스캔 결과 자동 저장 | true/false |
| `scan_timeout_sec` | Int | `30` | Quick Scan 타임아웃 (초) | 15~60 |
| `full_scan_timeout_sec` | Int | `180` | Full Scan 타임아웃 (초) | 60~600 |
| `port_scan_timeout_ms` | Int | `1000` | 포트 스캔 타임아웃 (ms) | 500~3000 |
| `port_scan_parallel` | Int | `10` | 포트 스캔 동시 연결 수 | 5~20 |
| `retro_flash_verify_interval_sec` | Int | `5` | 플래시 검증 주기 (초) | 3~10 |
| `magnetic_sample_rate_hz` | Int | `20` | 자기장 샘플링 레이트 | 10~50 |
| `show_disclaimer` | Boolean | `true` | 면책 조항 표시 | true/false |
| `dark_mode` | String | `"SYSTEM"` | 다크 모드 설정 | SYSTEM, LIGHT, DARK |
| `language` | String | `"ko"` | 앱 언어 | ko, en |
| `onboarding_complete` | Boolean | `false` | 온보딩 완료 여부 | true/false |
| `report_storage_limit` | Int | `10` | 리포트 저장 한도 (무료) | 10 (프리미엄: 무제한) |

### 14.2 SharedPreferences 키 상수

```kotlin
object PrefsKeys {
    const val PREFS_NAME = "searcam_prefs"

    // 감도
    const val EMF_SENSITIVITY = "emf_sensitivity"

    // 알림
    const val SOUND_ENABLED = "sound_enabled"
    const val VIBRATION_ENABLED = "vibration_enabled"

    // 스캔
    const val AUTO_SAVE_REPORT = "auto_save_report"
    const val SCAN_TIMEOUT_SEC = "scan_timeout_sec"
    const val FULL_SCAN_TIMEOUT_SEC = "full_scan_timeout_sec"
    const val PORT_SCAN_TIMEOUT_MS = "port_scan_timeout_ms"
    const val PORT_SCAN_PARALLEL = "port_scan_parallel"
    const val RETRO_FLASH_VERIFY_INTERVAL_SEC = "retro_flash_verify_interval_sec"
    const val MAGNETIC_SAMPLE_RATE_HZ = "magnetic_sample_rate_hz"

    // UI
    const val SHOW_DISCLAIMER = "show_disclaimer"
    const val DARK_MODE = "dark_mode"
    const val LANGUAGE = "language"
    const val ONBOARDING_COMPLETE = "onboarding_complete"

    // 제한
    const val REPORT_STORAGE_LIMIT = "report_storage_limit"
}
```

### 14.3 설정 접근 패턴

```kotlin
class UserPreferences @Inject constructor(
    private val prefs: SharedPreferences,
) {
    val emfSensitivity: EmfSensitivity
        get() = EmfSensitivity.valueOf(
            prefs.getString(PrefsKeys.EMF_SENSITIVITY, "NORMAL") ?: "NORMAL"
        )

    val soundEnabled: Boolean
        get() = prefs.getBoolean(PrefsKeys.SOUND_ENABLED, true)

    val vibrationEnabled: Boolean
        get() = prefs.getBoolean(PrefsKeys.VIBRATION_ENABLED, true)

    val scanTimeoutSec: Int
        get() = prefs.getInt(PrefsKeys.SCAN_TIMEOUT_SEC, 30)
            .coerceIn(15, 60)

    // 설정 변경 시 새 객체 반환 (불변 패턴)
    fun updateEmfSensitivity(sensitivity: EmfSensitivity) {
        prefs.edit()
            .putString(PrefsKeys.EMF_SENSITIVITY, sensitivity.name)
            .apply()
    }

    // Flow로 변경 감지
    fun observeSettings(): Flow<UserSettings> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(currentSettings())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        send(currentSettings())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private fun currentSettings(): UserSettings = UserSettings(
        emfSensitivity = emfSensitivity,
        soundEnabled = soundEnabled,
        vibrationEnabled = vibrationEnabled,
        scanTimeoutSec = scanTimeoutSec,
        // ...
    )
}

data class UserSettings(
    val emfSensitivity: EmfSensitivity,
    val soundEnabled: Boolean,
    val vibrationEnabled: Boolean,
    val scanTimeoutSec: Int,
    val fullScanTimeoutSec: Int = 180,
    val portScanTimeoutMs: Int = 1000,
    val portScanParallel: Int = 10,
    val retroFlashVerifyIntervalSec: Int = 5,
    val magneticSampleRateHz: Int = 20,
    val showDisclaimer: Boolean = true,
    val darkMode: String = "SYSTEM",
    val language: String = "ko",
    val onboardingComplete: Boolean = false,
    val reportStorageLimit: Int = 10,
)
```

---

## 15. Room DB 스키마

### 15.1 테이블 설계

```kotlin
@Entity(tableName = "scan_reports")
data class ReportEntity(
    @PrimaryKey
    val id: String,                    // UUID

    @ColumnInfo(name = "mode")
    val mode: String,                  // ScanMode.name

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,               // epoch millis

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,

    @ColumnInfo(name = "overall_risk")
    val overallRisk: Int,              // 0~100

    @ColumnInfo(name = "risk_level")
    val riskLevel: String,             // RiskLevel.name

    @ColumnInfo(name = "wifi_score")
    val wifiScore: Int?,

    @ColumnInfo(name = "lens_score")
    val lensScore: Int?,

    @ColumnInfo(name = "ir_score")
    val irScore: Int?,

    @ColumnInfo(name = "magnetic_score")
    val magneticScore: Int?,

    @ColumnInfo(name = "correction_factor")
    val correctionFactor: Float,

    @ColumnInfo(name = "location_note")
    val locationNote: String,

    @ColumnInfo(name = "devices_json")
    val devicesJson: String,           // JSON 직렬화

    @ColumnInfo(name = "findings_json")
    val findingsJson: String,          // JSON 직렬화

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)
```

### 15.2 DAO 인터페이스

```kotlin
@Dao
interface ReportDao {

    @Query("SELECT * FROM scan_reports ORDER BY timestamp DESC")
    fun getAll(): Flow<List<ReportEntity>>

    @Query("SELECT * FROM scan_reports WHERE id = :id")
    suspend fun getById(id: String): ReportEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: ReportEntity)

    @Delete
    suspend fun delete(report: ReportEntity)

    @Query("SELECT * FROM scan_reports ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<ReportEntity>

    @Query("SELECT COUNT(*) FROM scan_reports")
    suspend fun getCount(): Int

    @Query("DELETE FROM scan_reports WHERE id IN (SELECT id FROM scan_reports ORDER BY timestamp ASC LIMIT :count)")
    suspend fun deleteOldest(count: Int)
}
```

---

## 16. 권한 관리

### 16.1 필요 권한 목록

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.VIBRATE" />
```

### 16.2 권한 ↔ 기능 매핑

| 권한 | 필요 기능 | 거부 시 대응 |
|------|----------|-------------|
| FINE_LOCATION | Wi-Fi 스캔 (Android 8+) | Layer 1 스킵, Layer 2+3만 실행 |
| ACCESS_WIFI_STATE | Wi-Fi 연결 확인 | Layer 1 스킵 |
| CAMERA | 렌즈 감지, IR 감지 | Layer 2 스킵 |
| VIBRATE | 진동 알림 | 소리만 사용 |
| INTERNET | 포트 스캔 | 포트 스캔 스킵 |

### 16.3 권한 요청 흐름

```
앱 시작
  │
  ▼ 온보딩 (최초 1회)
  │ "SearCam이 왜 이 권한을 필요로 하는지" 설명
  │
  ▼ 권한 요청 순서:
  1. 위치 (Wi-Fi 스캔용) — 안내 문구 표시
  2. 카메라 (렌즈 감지용) — 안내 문구 표시
  │
  ▼ 거부 시:
  │ 해당 레이어 자동 스킵
  │ "일부 기능이 제한됩니다" 안내
  │ 설정에서 변경 가능하다고 고지
```

---

## 17. 네비게이션 구조

### 17.1 Navigation Graph

```
NavHost(startDestination = "home")
  │
  ├─ "home"              → HomeScreen
  │
  ├─ "scan/{mode}"       → QuickScanScreen / FullScanScreen
  │   argument: mode (ScanMode)
  │
  ├─ "scan/result/{id}"  → ScanResultScreen
  │   argument: reportId (String)
  │
  ├─ "lens/{mode}"       → LensFinderScreen / IrCameraScreen
  │   argument: mode (LensMode)
  │
  ├─ "magnetic"          → MagneticScreen
  │
  ├─ "reports"           → ReportListScreen
  │
  ├─ "reports/{id}"      → ReportDetailScreen
  │   argument: reportId (String)
  │
  ├─ "checklist/{type}"  → ChecklistScreen
  │   argument: type (accommodation / bathroom)
  │
  ├─ "settings"          → SettingsScreen
  │
  └─ "onboarding"        → OnboardingScreen
```

### 17.2 Bottom Navigation

```
┌──────────────────────────────────┐
│  홈  │  리포트  │ 체크리스트 │ 설정 │
└──────────────────────────────────┘
  │       │           │          │
  ▼       ▼           ▼          ▼
 home   reports    checklist   settings
         │ select      │
         ▼             ▼
       reports/{id}  checklist/{type}
```

---

## 18. 테스트 전략

### 18.1 테스트 피라미드

```
          /\
         /  \          UI 테스트 (Espresso/Compose Test)
        / UI  \        10% — 핵심 플로우만
       /------\
      /        \       통합 테스트
     / Integr.  \      20% — UseCase + Repository
    /------------\
   /              \    단위 테스트
  / Unit Tests     \   70% — 알고리즘, ViewModel, Model
 /------------------\
```

### 18.2 테스트 대상 우선순위

| 우선순위 | 대상 | 테스트 유형 |
|---------|------|-----------|
| P0 | CrossValidator (교차 검증) | 단위 테스트 |
| P0 | RiskCalculator (위험도 산출) | 단위 테스트 |
| P0 | OuiDatabase (OUI 매칭) | 단위 테스트 |
| P1 | ScanViewModel (상태 전이) | 단위 테스트 |
| P1 | NoiseFilter (노이즈 필터) | 단위 테스트 |
| P1 | RunQuickScanUseCase | 통합 테스트 |
| P1 | RunFullScanUseCase | 통합 테스트 |
| P2 | Quick Scan 전체 플로우 | UI 테스트 |
| P2 | 결과 화면 표시 정합성 | UI 테스트 |

---

*본 문서는 project-plan.md v3.1 기반으로 작성되었으며, 구현 진행에 따라 갱신됩니다.*
*작성일: 2026-04-03 | SearCam TDD v1.0*
