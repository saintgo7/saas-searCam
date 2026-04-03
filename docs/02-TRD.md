# SearCam 기술 요구사항 정의서 (TRD)

> Technical Requirements Document

---

## 1. 문서 정보

| 항목 | 내용 |
|------|------|
| 문서명 | SearCam 기술 요구사항 정의서 (TRD) |
| 버전 | v1.0 |
| 작성일 | 2026-04-03 |
| 기반 문서 | project-plan.md v3.1 |
| 대상 플랫폼 | Android (Phase 1) |
| 상태 | 초안 |

### 변경 이력

| 버전 | 날짜 | 작성자 | 변경 내용 |
|------|------|--------|-----------|
| v1.0 | 2026-04-03 | - | 초기 작성 |

---

## 2. 시스템 개요

### 2.1 시스템 목적

SearCam은 일반 사용자가 스마트폰만으로 숙소, 화장실, 탈의실 등에서 몰래카메라를 30초 이내에 1차 스크리닝할 수 있는 Android 애플리케이션이다. 전문 탐지 장비(RF 스캐너, NLJD 등)를 대체하지 않으며, "없는 것보다 훨씬 나은 1차 스크리닝 도구"를 목표로 한다.

### 2.2 시스템 범위

Phase 1(MVP)의 범위는 다음과 같다.

| 구분 | 포함 | 제외 |
|------|------|------|
| 플랫폼 | Android (Kotlin) | iOS (Phase 2) |
| 탐지 | Wi-Fi 스캔, 플래시 Retroreflection, IR, EMF | LiDAR, ML 시그니처, CSI |
| 교차 검증 | 가중치 기반 합산 | 동적 가중치, AI Fusion |
| 리포트 | 로컬 저장 + PDF 내보내기 | 112 신고 연동 (Phase 2) |
| 네트워크 | 온디바이스 로컬 처리 전용 | 클라우드 연동, Firebase |
| 수익화 | AdMob 배너 + 프리미엄 구독 | 기업용 대시보드 (Phase 3) |

### 2.3 전체 아키텍처 개요

```
┌───────────────────────────────────────────────────────────┐
│                    SearCam Application                     │
├───────────────────────────────────────────────────────────┤
│                                                           │
│  ┌─────────────────── UI Layer ──────────────────────┐   │
│  │  Jetpack Compose Screens                          │   │
│  │  (Home, Scan, Lens, Magnetic, Report, Settings)   │   │
│  └────────────────────┬──────────────────────────────┘   │
│                       │ StateFlow / Event                  │
│  ┌────────────────────┴──────────────────────────────┐   │
│  │  ViewModel Layer (MVVM)                            │   │
│  │  (ScanViewModel, LensViewModel, etc.)              │   │
│  └────────────────────┬──────────────────────────────┘   │
│                       │ UseCase 호출                       │
│  ┌────────────────────┴──────────────────────────────┐   │
│  │  Domain Layer                                      │   │
│  │  ┌──────────────────────────────────────────────┐ │   │
│  │  │ UseCase: QuickScan, FullScan, CalcRisk, etc. │ │   │
│  │  └──────────────────────────────────────────────┘ │   │
│  │  ┌──────────────────────────────────────────────┐ │   │
│  │  │ Model: ScanResult, RiskLevel, NetworkDevice  │ │   │
│  │  └──────────────────────────────────────────────┘ │   │
│  └────────────────────┬──────────────────────────────┘   │
│                       │ Repository Interface               │
│  ┌────────────────────┴──────────────────────────────┐   │
│  │  Data Layer                                        │   │
│  │  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────────┐ │   │
│  │  │Wi-Fi   │ │Lens    │ │EMF     │ │Cross       │ │   │
│  │  │Scanner │ │Detector│ │Sensor  │ │Validator   │ │   │
│  │  └───┬────┘ └───┬────┘ └───┬────┘ └─────┬──────┘ │   │
│  │      │          │          │             │        │   │
│  │  ┌───┴──────────┴──────────┴─────────────┴──────┐ │   │
│  │  │ Hardware Abstraction (Sensors, Camera, Wi-Fi) │ │   │
│  │  └──────────────────────────────────────────────┘ │   │
│  │  ┌──────────────┐  ┌────────────┐                 │   │
│  │  │ Room DB      │  │ OUI JSON   │                 │   │
│  │  │ (Reports)    │  │ (500KB)    │                 │   │
│  │  └──────────────┘  └────────────┘                 │   │
│  └───────────────────────────────────────────────────┘   │
│                                                           │
│  ┌──────────── Infrastructure ───────────────────────┐   │
│  │  Hilt DI / Timber Logging / Coroutines + Flow     │   │
│  └───────────────────────────────────────────────────┘   │
│                                                           │
└───────────────────────────────────────────────────────────┘
```

### 2.4 탐지 파이프라인 개요

3-Layer 탐지 구조를 채택하며, 각 레이어의 결과를 교차 검증 엔진이 종합하여 위험도를 산출한다.

| 레이어 | 탐지 방식 | 기본 가중치 | 센서 |
|--------|-----------|-------------|------|
| Layer 1 | 네트워크 스캔 (ARP + mDNS + OUI + 포트) | 50% | Wi-Fi 모듈 |
| Layer 2 | 렌즈 감지 (플래시 Retroreflection + IR) | 35% | 카메라 + 플래시 |
| Layer 3 | 자기장(EMF) 감지 | 15% | 3축 자력계 |

---

## 3. 하드웨어 요구사항

### 3.1 최소 디바이스 사양

| 항목 | 최소 사양 | 권장 사양 |
|------|-----------|-----------|
| CPU | ARM Cortex-A53 (4코어) | ARM Cortex-A76 이상 (8코어) |
| RAM | 2 GB | 4 GB 이상 |
| 내부 저장소 | 100 MB 여유 공간 | 500 MB 이상 여유 공간 |
| 디스플레이 | 720 x 1280 (HD) | 1080 x 2400 (FHD+) |
| GPU | Adreno 306 / Mali-T720 이상 | Adreno 619 / Mali-G57 이상 |

### 3.2 센서 요구사항

#### 3.2.1 자력계 (Magnetometer)

| 항목 | 요구사항 |
|------|----------|
| 센서 타입 | TYPE_MAGNETIC_FIELD (3축) |
| 측정 범위 | 최소 +/- 2000 uT |
| 해상도 | 0.15 uT 이하 |
| 샘플링 속도 | 20 Hz 이상 (SENSOR_DELAY_GAME, ~50ms) |
| 보정 상태 | ACCURACY_HIGH 권장 (최소 ACCURACY_MEDIUM) |
| 필수 여부 | **선택** (없으면 Layer 3 비활성화) |

#### 3.2.2 카메라

| 항목 | 요구사항 |
|------|----------|
| 후면 카메라 | 필수. 최소 5 MP, 자동 초점 지원 |
| 후면 플래시 | 필수. LED 토치 모드 지원 (FLASH_MODE_TORCH) |
| 전면 카메라 | 선택. IR LED 감지용 (IR 필터가 약한 카메라일수록 유리) |
| 프레임 레이트 | 30 fps 이상 (CameraX Preview/ImageAnalysis) |
| 해상도 (분석용) | 720p (1280x720) 다운스케일 |
| API | CameraX (camera-core, camera-camera2) |

#### 3.2.3 Wi-Fi 모듈

| 항목 | 요구사항 |
|------|----------|
| Wi-Fi 표준 | 802.11 b/g/n (2.4 GHz 필수, 5 GHz 선택) |
| 스캔 기능 | WifiManager.startScan() 지원 |
| ARP 테이블 | /proc/net/arp 읽기 가능 |
| mDNS | NsdManager API 지원 |
| UDP 소켓 | SSDP M-SEARCH 전송 가능 |
| TCP 소켓 | 포트 스캔용 Socket 연결 |

### 3.3 센서 가용성에 따른 동작 모드

| 자력계 | 카메라 | 플래시 | Wi-Fi | 동작 모드 |
|--------|--------|--------|-------|-----------|
| O | O | O | O | 풀 기능 (3-Layer 모두 활성) |
| X | O | O | O | Layer 1 + Layer 2만 (EMF 비활성) |
| O | O | X | O | Layer 1 + Layer 3 (Retroreflection 비활성) |
| O | X | X | O | Layer 1 + Layer 3 (카메라 탐지 비활성) |
| X | X | X | O | Layer 1만 (Quick Scan 전용) |
| O | O | O | X | Layer 2 + Layer 3 (오프라인 모드) |
| X | O | O | X | Layer 2만 (렌즈 감지 전용) |

앱 최초 실행 시 센서 가용성을 검사하고, 사용 불가 센서에 대해 사용자에게 안내 메시지를 표시한다.

### 3.4 Android 기기별 센서 호환성 참고

| 기기 | 자력계 | 후면 플래시 | 전면 IR 감도 | 비고 |
|------|--------|-------------|-------------|------|
| Samsung Galaxy S 시리즈 (S21 이상) | O | O | 보통 | 기준 테스트 기기 |
| Samsung Galaxy A 시리즈 (A24 이상) | O | O | 보통 | 보급형 주력 |
| Google Pixel (6 이상) | O | O | 양호 | IR 필터 약한 편 |
| Xiaomi Redmi 시리즈 | O | O | 기기별 상이 | 자력계 보정 필요한 경우 있음 |
| LG / 구형 보급형 | 일부 미탑재 | O | 약함 | 자력계 미탑재 시 Layer 3 비활성 |

---

## 4. 소프트웨어 요구사항

### 4.1 OS 및 API 레벨

| 항목 | 버전 | 비고 |
|------|------|------|
| 최소 Android 버전 | Android 8.0 (API 26) | 시장 커버리지 95%+ |
| 타겟 Android 버전 | Android 14 (API 34) | Play Store 타겟 API 정책 준수 |
| 컴파일 SDK | API 34 | 최신 API 활용 |

### 4.2 개발 언어 및 프레임워크

| 항목 | 선택 | 버전 |
|------|------|------|
| 언어 | Kotlin | 1.9.x 이상 |
| UI 프레임워크 | Jetpack Compose | BOM 2024.x |
| 아키텍처 패턴 | MVVM + Clean Architecture | - |
| 빌드 시스템 | Gradle (Kotlin DSL) | 8.x |
| AGP (Android Gradle Plugin) | 8.2.x 이상 | - |

### 4.3 핵심 라이브러리 및 의존성

| 라이브러리 | 버전 | 용도 | 라이선스 |
|-----------|------|------|---------|
| Jetpack Compose BOM | 2024.x | 선언형 UI | Apache 2.0 |
| CameraX | 1.3.x | 카메라 제어 (Preview, ImageAnalysis) | Apache 2.0 |
| Hilt | 2.50+ | 의존성 주입 | Apache 2.0 |
| Room | 2.6.x | 로컬 SQLite DB | Apache 2.0 |
| Kotlin Coroutines | 1.8.x | 비동기 처리 | Apache 2.0 |
| Kotlin Flow | 1.8.x | 리액티브 데이터 스트림 | Apache 2.0 |
| MPAndroidChart | 3.1.x | 자기장 실시간 그래프 | Apache 2.0 |
| iText / AndroidPdf | 7.x / 최신 | PDF 리포트 생성 | AGPL (확인 필요) |
| Timber | 5.x | 로깅 | Apache 2.0 |
| Navigation Compose | 2.7.x | 화면 네비게이션 | Apache 2.0 |
| Material3 | 1.2.x | 디자인 시스템 | Apache 2.0 |
| Accompanist Permissions | 최신 | 런타임 권한 처리 | Apache 2.0 |
| AdMob SDK | 최신 | 광고 (비간섭 배너) | Google 약관 |

### 4.4 빌드 환경

| 항목 | 요구사항 |
|------|----------|
| JDK | 17 (LTS) |
| Android Studio | Iguana (2024.x) 이상 |
| Gradle | 8.4 이상 |
| NDK | 불필요 (Phase 1) |
| CMake | 불필요 (Phase 1) |
| CI/CD | GitHub Actions (lint, test, build, deploy) |

### 4.5 Android 권한 목록

| 권한 | 유형 | 용도 | 필수 여부 |
|------|------|------|-----------|
| `ACCESS_FINE_LOCATION` | 위험 (런타임) | Wi-Fi 스캔 시 필수 (Android 8.1+) | 필수 |
| `ACCESS_COARSE_LOCATION` | 위험 (런타임) | 위치 기반 Wi-Fi 스캔 | 필수 |
| `ACCESS_WIFI_STATE` | 일반 | Wi-Fi 상태 확인 | 필수 |
| `CHANGE_WIFI_STATE` | 일반 | Wi-Fi 스캔 트리거 | 필수 |
| `CAMERA` | 위험 (런타임) | 렌즈 감지, IR 감지 | 필수 |
| `INTERNET` | 일반 | OUI DB 업데이트, 광고 | 필수 |
| `ACCESS_NETWORK_STATE` | 일반 | 네트워크 연결 확인 | 필수 |
| `VIBRATE` | 일반 | 탐지 시 진동 알림 | 필수 |
| `FLASHLIGHT` | 일반 | 플래시 제어 (Retroreflection) | 필수 |
| `HIGH_SAMPLING_RATE_SENSORS` | 일반 | 자력계 20Hz+ 샘플링 (Android 12+) | 선택 |
| `WRITE_EXTERNAL_STORAGE` | 위험 (런타임) | PDF 내보내기 (Android 9 이하) | 조건부 |
| `POST_NOTIFICATIONS` | 위험 (런타임) | 스캔 완료 알림 (Android 13+) | 선택 |

---

## 5. 네트워크 요구사항

### 5.1 Wi-Fi 스캔 네트워크 조건

| 조건 | 설명 |
|------|------|
| 연결 상태 | 동일 Wi-Fi 네트워크에 연결되어 있어야 Layer 1 (네트워크 스캔) 활성화 |
| 서브넷 | /24 서브넷 기준 최대 254개 호스트 스캔 |
| 대역 | 2.4 GHz 및 5 GHz 모두 지원 |
| AP 격리 | AP Isolation(클라이언트 격리) 활성화 시 ARP 스캔 제한됨 (mDNS로 부분 대체) |
| 게스트 네트워크 | 숙소 게스트 Wi-Fi에서도 동작해야 함 |
| 공유기 유형 | 일반 가정용 / 숙소용 공유기 (엔터프라이즈 환경은 테스트 범위 외) |

### 5.2 스캔 트래픽 사양

| 프로토콜 | 포트 | 방향 | 트래픽량 (1회 스캔) |
|----------|------|------|---------------------|
| ARP | - | 로컬 | /proc/net/arp 읽기 (트래픽 없음) |
| mDNS | 5353/UDP | 멀티캐스트 | ~2 KB (쿼리) + 응답 |
| SSDP | 1900/UDP | 멀티캐스트 | ~1 KB (M-SEARCH) + 응답 |
| TCP 포트 스캔 | 554, 80, 8080, 8888, 3702, 1935 | 유니캐스트 | 의심 기기당 ~6 SYN 패킷 |
| **합계 (Quick Scan)** | | | **약 10~50 KB** |

### 5.3 네트워크 타임아웃 설정

| 작업 | 타임아웃 | 재시도 |
|------|----------|--------|
| ARP 테이블 읽기 | 2초 | 1회 |
| mDNS 서비스 탐색 | 5초 | 없음 (수집된 결과 사용) |
| SSDP M-SEARCH | 3초 | 없음 |
| TCP 포트 연결 | 1.5초 / 포트 | 없음 |
| 호스트명 조회 (DNS) | 2초 | 없음 |

### 5.4 오프라인 모드 동작

Wi-Fi 미연결 시 앱은 자동으로 오프라인 모드로 전환된다.

| 항목 | 동작 |
|------|------|
| Layer 1 (네트워크) | 비활성화. 사용자에게 "Wi-Fi 미연결 - 네트워크 스캔 건너뜀" 안내 |
| Layer 2 (렌즈) | 정상 동작 (카메라 + 플래시는 Wi-Fi 불필요) |
| Layer 3 (EMF) | 정상 동작 (자력계는 Wi-Fi 불필요) |
| 가중치 재배분 | W1=0, W2=0.75, W3=0.25 (자동 조정) |
| OUI DB | 로컬 내장 DB 사용 (500 KB JSON) |
| 리포트 저장 | Room DB에 로컬 저장 (정상) |
| PDF 내보내기 | 정상 동작 (로컬 처리) |
| 광고 | 미표시 (네트워크 불필요) |

### 5.5 향후 네트워크 요구 (Phase 2 이후)

| 기능 | 프로토콜 | 방화벽 요구 |
|------|----------|-------------|
| OUI DB OTA 업데이트 | HTTPS | 아웃바운드 443 |
| Firebase Analytics (Phase 3) | HTTPS | 아웃바운드 443 |
| 커뮤니티 맵 (Phase 3) | HTTPS/WSS | 아웃바운드 443 |
| 112 신고 연동 (Phase 2) | tel:// intent | 셀룰러 네트워크 |

---

## 6. 성능 요구사항

### 6.1 응답 시간

| 작업 | 목표 | 최대 허용 | 측정 기준 |
|------|------|-----------|-----------|
| 앱 Cold Start | 1.5초 이내 | 3.0초 | 스플래시 → 홈 화면 렌더링 완료 |
| 앱 Warm Start | 0.5초 이내 | 1.0초 | 백그라운드 → 포그라운드 전환 |
| Quick Scan 완료 | 30초 이내 | 45초 | 스캔 시작 → 결과 화면 표시 |
| Full Scan 완료 | 3분 이내 | 5분 | 3-Layer 모두 완료 |
| ARP 테이블 읽기 | 100ms 이내 | 500ms | /proc/net/arp 파싱 |
| mDNS 탐색 | 5초 이내 | 8초 | 서비스 수집 완료 |
| 단일 포트 스캔 | 1.5초 이내 | 3초 | SYN → 응답 또는 타임아웃 |
| 자기장 캘리브레이션 | 3초 | 3초 (고정) | 60 samples @ 20Hz |
| 교차 검증 계산 | 50ms 이내 | 200ms | 3-Layer 결과 수신 → 점수 산출 |
| PDF 생성 | 2초 이내 | 5초 | 리포트 데이터 → PDF 파일 생성 |
| 화면 전환 | 16ms 이내 | 32ms | 프레임 시간 (60fps 기준) |

### 6.2 카메라 및 프레임 처리

| 항목 | 목표 | 최대 허용 |
|------|------|-----------|
| 카메라 프리뷰 FPS | 30 fps | 24 fps 이상 |
| ImageAnalysis 처리 FPS | 15 fps | 10 fps 이상 |
| 렌즈 반사 포인트 추출 지연 | 30ms / 프레임 | 66ms / 프레임 |
| 프레임 드롭율 | 5% 이하 | 10% 이하 |

### 6.3 메모리 사용량

| 상태 | 목표 | 최대 허용 | 측정 방법 |
|------|------|-----------|-----------|
| 유휴 (홈 화면) | 80 MB 이하 | 120 MB | Android Profiler - Java Heap + Native |
| Quick Scan 중 | 120 MB 이하 | 180 MB | Wi-Fi 스캔 + 결과 보유 시 |
| Full Scan 중 | 200 MB 이하 | 280 MB | 카메라 프리뷰 + 분석 포함 |
| 렌즈 감지 모드 | 180 MB 이하 | 250 MB | CameraX + ImageAnalysis |
| OUI DB 로드 | 10 MB 이하 | 15 MB | JSON 파싱 후 메모리 |
| Room DB | 5 MB 이하 | 20 MB | 리포트 100건 기준 |

### 6.4 CPU 사용량

| 상태 | 목표 | 최대 허용 | 기준 기기 |
|------|------|-----------|-----------|
| 유휴 | 1% 이하 | 3% | 미드레인지 (Galaxy A54 급) |
| Quick Scan 중 | 15% 이하 | 25% | 네트워크 스캔 작업 |
| 카메라 분석 중 | 30% 이하 | 45% | 렌즈 감지 프레임 처리 |
| EMF 측정 중 | 10% 이하 | 15% | 20Hz 센서 데이터 처리 |
| Full Scan (피크) | 40% 이하 | 55% | 3-Layer 동시 실행 |

### 6.5 배터리 소모량

| 상태 | 목표 소모율 | 최대 허용 | 비고 |
|------|------------|-----------|------|
| Quick Scan 1회 (30초) | 0.3% 이하 | 0.5% | Wi-Fi 스캔 위주 |
| Full Scan 1회 (3분) | 2% 이하 | 3% | 카메라 + 플래시 + 센서 |
| 렌즈 감지 5분 연속 | 4% 이하 | 6% | 플래시 상시 ON이 주 소모원 |
| EMF 스캔 5분 연속 | 1.5% 이하 | 2.5% | 센서만 사용 (저전력) |
| 백그라운드 대기 | 0% | 0% | 백그라운드 센서 사용 없음 |

배터리 소모 기준은 4,000 mAh 배터리 기기에서 측정한다.

### 6.6 앱 크기

| 항목 | 목표 | 최대 허용 |
|------|------|-----------|
| APK 크기 | 15 MB 이하 | 25 MB |
| 설치 후 크기 | 40 MB 이하 | 60 MB |
| OUI DB (내장) | 500 KB | 1 MB |

---

## 7. 보안 요구사항

### 7.1 데이터 처리 원칙

| 원칙 | 설명 |
|------|------|
| **로컬 처리 우선** | 모든 탐지 로직은 온디바이스에서 실행. 서버 전송 없음 (Phase 1) |
| **개인정보 미수집** | 사용자 식별 정보 수집하지 않음. 계정 시스템 없음 |
| **네트워크 데이터 비저장** | 스캔된 기기의 MAC/IP는 리포트 생성 후 메모리에서 제거 |
| **최소 권한 원칙** | 탐지에 필요한 권한만 요청. 사용 목적을 명확히 안내 |

### 7.2 저장 데이터 보호

| 데이터 | 암호화 | 방식 |
|--------|--------|------|
| Room DB (리포트) | O | Android Keystore + SQLCipher 또는 EncryptedSharedPreferences |
| 설정 값 | O | EncryptedSharedPreferences (AndroidX Security) |
| PDF 리포트 (외부 저장) | X | 사용자 의도적 내보내기이므로 평문 |
| OUI DB (JSON) | X | 공개 데이터이므로 암호화 불필요 |
| 앱 내 캐시 | X | 민감 정보 미포함 |

### 7.3 네트워크 스캔 안전성

| 항목 | 요구사항 |
|------|----------|
| 스캔 방식 | 수동적 스캔(ARP 읽기, mDNS 수신) 우선, 능동적 스캔(포트 스캔) 최소화 |
| 포트 스캔 범위 | 의심 기기에 한정 (OUI 매칭 또는 mDNS 탐지된 기기만) |
| 포트 목록 | 6개 포트로 제한 (554, 80, 8080, 8888, 3702, 1935) |
| 동시 연결 수 | 최대 5개 동시 TCP 연결 (네트워크 부하 방지) |
| 스캔 주기 제한 | 동일 네트워크 재스캔은 최소 30초 간격 |
| 패킷 조작 | 일체 없음. 표준 소켓 API만 사용 |
| 네트워크 간섭 | 라우터/AP에 영향을 주지 않도록 저 트래픽 설계 |

### 7.4 권한 관리

| 권한 | 요청 시점 | 거부 시 동작 |
|------|-----------|-------------|
| 위치 (Wi-Fi 스캔용) | Quick Scan 또는 Full Scan 시작 시 | Layer 1 비활성화, Layer 2+3만 실행 |
| 카메라 | 렌즈 감지 또는 IR 모드 시작 시 | Layer 2 비활성화, Layer 1+3만 실행 |
| 저장소 (Android 9 이하) | PDF 내보내기 시 | PDF 내보내기 불가 안내 |
| 알림 (Android 13+) | 첫 스캔 완료 시 | 알림 미발송 (기능 영향 없음) |

거부된 권한은 기능 설명과 함께 설정 화면으로 안내하되, 강제하지 않는다.

### 7.5 코드 보안

| 항목 | 요구사항 |
|------|----------|
| 난독화 | ProGuard / R8 활성화 (release 빌드) |
| 디버그 정보 | release 빌드에서 Timber 로그 비활성화 |
| 하드코딩 금지 | API 키, 시크릿 등 소스코드에 직접 포함 금지 |
| 의존성 검사 | Dependabot 또는 Snyk으로 취약 라이브러리 모니터링 |
| 서명 | Google Play App Signing 사용 |

---

## 8. 신뢰성 및 가용성

### 8.1 안정성 지표

| 지표 | 목표 | 최대 허용 | 측정 방법 |
|------|------|-----------|-----------|
| 크래시율 (Crash-free sessions) | 99.5% 이상 | 99.0% 이상 | Firebase Crashlytics |
| ANR율 (Application Not Responding) | 0.2% 이하 | 0.5% 이하 | Play Console Vitals |
| 스캔 완료율 | 95% 이상 | 90% 이상 | 스캔 시작 대비 정상 완료 비율 |

### 8.2 장애 시나리오 및 복구 전략

| 장애 시나리오 | 영향 | 복구 전략 |
|--------------|------|-----------|
| Wi-Fi 연결 끊김 (스캔 중) | Layer 1 중단 | 수집된 결과까지만 활용, Layer 2+3 계속 진행 |
| 카메라 접근 실패 | Layer 2 불가 | 에러 메시지 + Layer 1+3만으로 결과 산출 |
| 자력계 센서 오류 | Layer 3 불가 | 에러 로그 + Layer 1+2만으로 결과 산출 |
| Room DB 손상 | 리포트 소실 | DB 재생성 + 사용자에게 데이터 손실 안내 |
| OOM (메모리 부족) | 앱 종료 | 자동 저장점 + 재시작 시 마지막 스캔 복구 시도 |
| 포트 스캔 타임아웃 | 일부 기기 미분석 | 타임아웃 기기는 "미확인"으로 표시 |
| 플래시 과열 (장시간) | 플래시 자동 OFF | 과열 감지 시 플래시 중단 + 사용자 안내 |
| PDF 생성 실패 | 리포트 미생성 | 재시도 1회 + 실패 시 텍스트 리포트 대안 제공 |

### 8.3 데이터 무결성

| 항목 | 전략 |
|------|------|
| 스캔 결과 | 스캔 진행 중 중간 결과를 메모리에 유지, 완료 시 DB에 원자적 저장 |
| 리포트 | Room 트랜잭션으로 일관성 보장 |
| 설정 | EncryptedSharedPreferences 원자적 쓰기 |

### 8.4 오프라인 내구성

앱의 핵심 탐지 기능(Layer 2, Layer 3)은 인터넷 연결 없이도 100% 동작해야 한다. Layer 1은 로컬 Wi-Fi 연결만 필요하며, 인터넷 연결은 불필요하다.

---

## 9. 확장성

### 9.1 새 탐지 레이어 추가 용이성

아키텍처는 새로운 탐지 레이어를 플러그인 방식으로 추가할 수 있도록 설계한다.

```kotlin
// 탐지 레이어 인터페이스
interface DetectionLayer {
    val layerId: String
    val displayName: String
    val defaultWeight: Float

    suspend fun scan(config: ScanConfig): LayerResult
    fun isAvailable(): Boolean  // 센서 가용성 체크
}
```

| 향후 추가 예정 레이어 | 시기 | 의존성 |
|---------------------|------|--------|
| Bluetooth LE 스캔 | Phase 2 | BLE API (API 21+) |
| LiDAR 렌즈 감지 (iOS) | Phase 3 | ARKit (iPhone Pro) |
| ML 자기장 시그니처 분류 | Phase 3 | TFLite Runtime |
| 음향 분석 (초음파) | 미정 | 마이크 + FFT 라이브러리 |

### 9.2 OUI 데이터베이스 업데이트

| 항목 | 설명 |
|------|------|
| 내장 DB | 앱 빌드 시 최신 IEEE OUI 데이터 포함 (약 500 KB) |
| 카메라 벤더 | 300+사 (Hikvision, Dahua, Wyze, Reolink, Hanwha 등) |
| 안전 벤더 | 1000+사 (Apple, Samsung, LG, Sony 등) 화이트리스트 |
| 업데이트 주기 | 앱 업데이트 시 자동 갱신 (Phase 1) |
| OTA 업데이트 | 앱 업데이트 없이 DB만 갱신 (Phase 2 이후) |
| DB 포맷 | JSON (Phase 1) → Protocol Buffers 또는 SQLite (최적화 시) |
| 버전 관리 | DB 내 version 필드로 신규 여부 판단 |

### 9.3 교차 검증 엔진 확장

| Phase | 검증 방식 | 설명 |
|-------|-----------|------|
| Phase 1 | 정적 가중치 합산 | W1=0.50, W2=0.35, W3=0.15 |
| Phase 2 | 동적 가중치 | 환경(밝기, Wi-Fi 유무)에 따라 자동 조정 |
| Phase 3 | AI Fusion | ML 모델이 3-Layer 결과를 종합 판단 |

---

## 10. 호환성 매트릭스

### 10.1 Android 버전별 호환성

| Android 버전 | API | ARP 읽기 | Wi-Fi Scan | mDNS | 자력계 20Hz+ | CameraX | 포트 스캔 | 비고 |
|-------------|-----|----------|------------|------|-------------|---------|----------|------|
| 8.0 (Oreo) | 26 | O | O (throttled) | O | O | O | O | Wi-Fi 스캔 4회/2분 제한 |
| 8.1 (Oreo) | 27 | O | O (throttled) | O | O | O | O | 위치 권한 필수화 |
| 9.0 (Pie) | 28 | O | O (throttled) | O | O | O | O | 백그라운드 위치 제한 |
| 10 (Q) | 29 | O | O (throttled) | O | O | O | O | Scoped Storage 시작 |
| 11 (R) | 30 | O | O (throttled) | O | O | O | O | 포그라운드 위치만 |
| 12 (S) | 31 | O | O (throttled) | O | 권한 필요 | O | O | HIGH_SAMPLING_RATE_SENSORS |
| 12L | 32 | O | O (throttled) | O | 권한 필요 | O | O | 대형 화면 지원 |
| 13 (T) | 33 | O | O (throttled) | O | 권한 필요 | O | O | 알림 권한 필수 |
| 14 (U) | 34 | O | O (throttled) | O | 권한 필요 | O | O | 타겟 SDK 정책 |

**Wi-Fi Scan Throttling 대응**: Android 8.0+에서 `startScan()`은 2분당 4회로 제한된다. Quick Scan에서는 1회 스캔으로 충분하므로 문제없으나, 반복 스캔 시 캐시된 결과를 사용하고 사용자에게 제한 사항을 안내한다.

### 10.2 제조사별 호환성 주의사항

| 제조사 | 주의사항 | 대응 |
|--------|----------|------|
| Samsung | OneUI 배터리 최적화로 백그라운드 센서 제한 가능 | 포그라운드 서비스 사용 또는 배터리 최적화 제외 안내 |
| Xiaomi / MIUI | 자동 시작 관리자가 앱 프로세스 종료 가능 | AutoStart 허용 안내 팝업 |
| Huawei / EMUI | 백그라운드 앱 킬 적극적 | 앱 보호 설정 안내 |
| OPPO / ColorOS | 백그라운드 제한 | 배터리 최적화 제외 안내 |
| Google Pixel | 가장 호환성 높음 | 기준 테스트 기기 |
| LG (레거시) | Android 12 업데이트 미지원 기기 다수 | API 26 최소 지원으로 대응 |

### 10.3 센서별 호환성

| 센서 | 탑재율 (추정) | 미탑재 시 동작 | 대체 수단 |
|------|-------------|---------------|-----------|
| 자력계 (TYPE_MAGNETIC_FIELD) | 85~90% | Layer 3 비활성화, 앱 정상 동작 | 없음 (선택 기능) |
| 후면 카메라 | 99%+ | Layer 2 비활성화 | 없음 |
| 후면 플래시 | 95%+ | Retroreflection 불가, IR만 가능 | 없음 |
| 전면 카메라 | 98%+ | IR 감지 불가 (Stage B 비활성) | 없음 |
| Wi-Fi | 99%+ | Layer 1 비활성화 | 없음 |
| 가속도계 | 99%+ | (현재 미사용) | - |
| 자이로스코프 | 85~90% | (현재 미사용) | - |

---

## 11. 데이터 요구사항

### 11.1 로컬 저장 데이터 목록

| 데이터 | 저장소 | 암호화 | 용도 |
|--------|--------|--------|------|
| 스캔 리포트 | Room DB | O | 스캔 결과 이력 조회 |
| 앱 설정 | EncryptedSharedPreferences | O | 감도, 테마, 언어 등 |
| OUI 데이터베이스 | assets/ (JSON) | X | MAC 제조사 매칭 |
| 체크리스트 진행률 | Room DB | X | 체크리스트 상태 |
| 온보딩 완료 여부 | SharedPreferences | X | 최초 실행 판단 |
| PDF 리포트 | 외부 저장소 (Downloads) | X | 사용자 내보내기 |

### 11.2 Room DB 스키마

#### ScanReport 테이블

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | INTEGER | PK, AUTO_INCREMENT | 리포트 고유 ID |
| scan_type | TEXT | NOT NULL | "quick" / "full" / "lens" / "ir" / "emf" |
| timestamp | INTEGER | NOT NULL | Unix timestamp (ms) |
| overall_score | INTEGER | NOT NULL, 0~100 | 종합 위험도 점수 |
| risk_level | TEXT | NOT NULL | "safe" / "attention" / "caution" / "danger" / "critical" |
| wifi_score | INTEGER | NULLABLE, 0~100 | Layer 1 점수 |
| lens_score | INTEGER | NULLABLE, 0~100 | Layer 2 점수 |
| emf_score | INTEGER | NULLABLE, 0~100 | Layer 3 점수 |
| devices_json | TEXT | NULLABLE | 발견 기기 목록 (JSON) |
| suspect_points_json | TEXT | NULLABLE | 의심 포인트 목록 (JSON) |
| location_name | TEXT | NULLABLE | 사용자 입력 장소명 |
| latitude | REAL | NULLABLE | GPS 위도 |
| longitude | REAL | NULLABLE | GPS 경도 |
| notes | TEXT | NULLABLE | 사용자 메모 |
| is_premium | INTEGER | DEFAULT 0 | 프리미엄 리포트 여부 |

#### ChecklistProgress 테이블

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | INTEGER | PK, AUTO_INCREMENT | 진행 고유 ID |
| checklist_type | TEXT | NOT NULL | "accommodation" / "restroom" |
| report_id | INTEGER | FK → ScanReport.id | 연관 리포트 |
| items_json | TEXT | NOT NULL | 체크 항목 상태 (JSON) |
| completed_at | INTEGER | NULLABLE | 완료 시각 |

### 11.3 저장 용량 추정

| 데이터 | 단건 크기 | 100건 기준 | 1년 추정 (주 2회 사용) |
|--------|----------|-----------|----------------------|
| ScanReport (Quick) | ~2 KB | ~200 KB | ~200 KB |
| ScanReport (Full) | ~5 KB | ~500 KB | ~500 KB |
| ChecklistProgress | ~1 KB | ~100 KB | ~100 KB |
| **Room DB 합계** | | | **약 1 MB** |
| OUI DB (고정) | 500 KB | - | 500 KB |
| PDF 리포트 (외부) | ~100 KB/건 | ~10 MB | ~10 MB |
| **전체 합계** | | | **약 12 MB** |

### 11.4 데이터 생명주기

| 데이터 | 보존 기간 | 삭제 정책 |
|--------|-----------|-----------|
| 스캔 리포트 (무료) | 최근 10건 | FIFO 방식으로 11건째부터 가장 오래된 것 삭제 |
| 스캔 리포트 (프리미엄) | 무제한 | 사용자 수동 삭제만 |
| 체크리스트 진행률 | 리포트와 동일 | 연관 리포트 삭제 시 CASCADE |
| 앱 설정 | 앱 삭제 시까지 | 앱 삭제 시 자동 제거 |
| PDF 리포트 | 사용자 관리 | 앱에서 직접 삭제하지 않음 (외부 저장소) |
| 메모리 내 스캔 데이터 | 스캔 세션 종료 시 | 결과 저장 후 즉시 해제 |
| MAC/IP 주소 (메모리) | 리포트 생성 후 | GC 대상 (명시적 참조 해제) |

---

## 12. 통합 요구사항

### 12.1 외부 시스템 연동 현황

| 외부 시스템 | Phase | 연동 방식 | 설명 |
|------------|-------|-----------|------|
| 112 긴급 신고 | Phase 2 | `tel://112` Intent | 원터치 전화 + GPS 좌표 공유 |
| PDF 생성 라이브러리 | Phase 1 | 로컬 라이브러리 | iText / AndroidPdf로 리포트 PDF 생성 |
| Google Play Billing | Phase 1 | Play Billing Library | 프리미엄 구독 결제 |
| AdMob | Phase 1 | Google Mobile Ads SDK | 비간섭 배너 광고 |
| Firebase Crashlytics | Phase 1 | Firebase SDK | 크래시 리포팅 |
| Firebase Analytics | Phase 3 | Firebase SDK | 사용 패턴 분석 |
| Firebase Firestore | Phase 3 | Firebase SDK | 커뮤니티 위험 장소 맵 |

### 12.2 112 신고 연동 (Phase 2) 상세

| 항목 | 설명 |
|------|------|
| 트리거 | 위험도 80점 이상 시 "112 신고" 버튼 노출 |
| 전화 연결 | `Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))` |
| GPS 좌표 | 사용자 동의 하에 현재 위치 좌표를 클립보드에 복사 |
| 리포트 공유 | PDF 리포트를 Intent.ACTION_SEND로 공유 가능 |
| 자동 전송 | 자동 전송 없음 (사용자 수동 조작만) |

### 12.3 PDF 리포트 사양

| 항목 | 요구사항 |
|------|----------|
| 포맷 | PDF/A-1b (장기 보존 호환) |
| 크기 | A4 (210 x 297 mm) |
| 페이지 수 | 1~2페이지 |
| 내용 | 종합 위험도, 레이어별 점수, 발견 기기 목록, 의심 포인트, 장소 정보, 날짜/시각 |
| 폰트 | 내장 폰트 (한글 지원: Noto Sans KR 또는 시스템 폰트) |
| 로고 | SearCam 로고 상단 |
| 면책 조항 | 리포트 하단에 고정 문구 포함 |

### 12.4 Google Play Billing 연동

| 항목 | 설명 |
|------|------|
| 상품 유형 | 자동 갱신 구독 (월간) |
| 가격 | KRW 2,900 / 월 |
| 무료 체험 | 7일 (선택 사항) |
| 기능 | 무제한 리포트, PDF 내보내기, 광고 제거 |
| 결제 검증 | Google Play Billing Library v6.x (서버 검증은 Phase 2에서 Firebase Functions 추가) |

---

## 13. 규제 준수

### 13.1 개인정보 보호법 (PIPA)

| 조항 | SearCam 준수 방안 |
|------|-------------------|
| 제15조 (개인정보 수집/이용) | 개인정보 수집하지 않음. 계정 시스템 없음, 로컬 처리 전용 |
| 제17조 (개인정보 제3자 제공) | 외부 서버 전송 없음 (Phase 1). AdMob SDK의 광고 ID는 Google 정책 준수 |
| 제21조 (개인정보 파기) | 수집 데이터 없으므로 해당 없음. 로컬 리포트는 사용자가 직접 삭제 |
| 제30조 (개인정보 처리방침) | Play Store 등록 시 개인정보 처리방침 공개 (수집 항목: 없음) |

### 13.2 정보통신망법

| 조항 | SearCam 준수 방안 |
|------|-------------------|
| 제48조 (정보통신망 침해 금지) | 수동적 스캔만 수행 (ARP 읽기, mDNS 수신). 네트워크 패킷 조작/변조 없음 |
| 제49조 (비밀 침해 금지) | 통신 내용 감청/분석 없음. DPI(Deep Packet Inspection) 미수행 |
| 포트 스캔 관련 | TCP SYN 연결 시도는 법적으로 허용되는 수준. 의심 기기에 한정하고, 스캔 범위를 최소화 |

**법적 리스크 완화**: 포트 스캔은 의심 기기(OUI 매칭 또는 mDNS 탐지 기기)에만 제한적으로 수행하며, 무차별 포트 스캔은 하지 않는다. 앱 내 면책 조항에 네트워크 스캔의 목적과 범위를 명시한다.

### 13.3 Google Play Store 개발자 정책

| 정책 | SearCam 준수 방안 |
|------|-------------------|
| 권한 사용 설명 | 각 권한(위치, 카메라)의 사용 목적을 Play Console에 명시 |
| 위치 권한 | Wi-Fi 스캔에 필요한 기술적 이유 설명 (Android OS 요구사항) |
| 카메라 권한 | 렌즈 감지/IR 감지 용도 명시 |
| 백그라운드 위치 | 사용하지 않음 (포그라운드에서만 스캔) |
| 데이터 안전 섹션 | "데이터 수집 없음" 선언, 로컬 처리 명시 |
| 광고 정책 | AdMob 정책 준수, 스캔 중 광고 미표시 |
| 사기성 기능 금지 | "100% 탐지" 미주장, 한계 솔직 고지, 면책 조항 포함 |
| 기기 및 네트워크 악용 | 네트워크 간섭/악용 없음, 수동적 스캔만 수행 |

### 13.4 앱 내 필수 고지 사항

| 고지 항목 | 표시 위치 | 내용 요약 |
|-----------|-----------|-----------|
| 면책 조항 | 온보딩 + 설정 화면 | 전문 장비 대체 불가, 100% 보장 불가 |
| 개인정보 처리방침 | 설정 화면 + Play Store | 수집 항목 없음, 로컬 처리 |
| 이용약관 | 설정 화면 + Play Store | 서비스 범위, 책임 한계 |
| 한계 안내 | 스캔 결과 화면 | 탐지 불가 유형 안내 |
| 오픈소스 라이선스 | 설정 화면 | 사용 라이브러리 라이선스 목록 |

### 13.5 iText 라이선스 검토

iText PDF 라이브러리는 AGPL 라이선스이다. 상용 앱에서 AGPL 준수가 어려울 경우 대안을 검토한다.

| 옵션 | 라이선스 | 비용 | 비고 |
|------|---------|------|------|
| iText (AGPL) | AGPL-3.0 | 무료 | 소스코드 공개 의무 |
| iText (상용) | Commercial | 유료 | 소스 비공개 가능 |
| Android PDF Writer | Apache 2.0 | 무료 | 기능 제한적 |
| Apache PDFBox (Android 포팅) | Apache 2.0 | 무료 | Android 호환 확인 필요 |
| 자체 구현 (Canvas → Bitmap → PDF) | - | 무료 | Android PdfDocument API 활용 |

**권장**: Phase 1에서는 Android 내장 `PdfDocument` API를 사용하여 라이선스 이슈를 회피한다.

---

## 14. 테스트 환경 요구사항

### 14.1 테스트용 카메라 장비

| 장비 | 모델 예시 | 용도 | 수량 |
|------|----------|------|------|
| Wi-Fi IP 카메라 | Hikvision DS-2CD series, Reolink E1 | Layer 1 테스트 | 2대 |
| Wi-Fi AP 내장 소형 카메라 | 자체 핫스팟 카메라 | Layer 1 SSID 탐지 | 1대 |
| IR LED 야간 카메라 | 야간 감시 카메라 (IR 발광) | Layer 2 Stage B 테스트 | 1대 |
| 핀홀 / 위장 카메라 | USB 충전기형, 연기감지기형 | 렌즈 반사 감지 테스트 | 2대 |
| SD카드 녹화 카메라 (오프라인) | 독립형 소형 카메라 | 탐지 한계 확인 | 1대 |
| 일반 거울 | 다양한 크기 | 오탐 테스트 (거울 반사) | 2개 |
| 금속 물체 | 반사 표면 (스테인리스, 크롬) | 오탐 테스트 | 3개 |
| 전자기기 (오탐 소스) | 가전제품, 충전기, 노트북 | Layer 3 오탐 테스트 | 다수 |
| **합계** | | | **약 15개 항목** |

### 14.2 테스트 환경 구성

| 환경 | 구성 | 테스트 목적 |
|------|------|-------------|
| 밝은 실내 | 일반 조명 (300~500 lux) | 렌즈 Retroreflection 기본 테스트 |
| 어두운 실내 | 완전 암실 (~0 lux) | IR LED 감지 테스트 |
| 중간 밝기 | 간접 조명 (50~100 lux) | 복합 환경 테스트 |
| Wi-Fi 환경 | 가정용 공유기 + 테스트 기기 | Layer 1 네트워크 스캔 |
| Wi-Fi 없는 환경 | 비행기 모드 / Wi-Fi OFF | 오프라인 모드 테스트 |
| AP Isolation | 클라이언트 격리 설정 공유기 | ARP 제한 시 대응 테스트 |
| 전자기 간섭 환경 | 가전제품 다수 (냉장고, TV 근처) | EMF 오탐 필터 테스트 |
| 숙소 시뮬레이션 | 실제 호텔/에어비앤비 | 실환경 통합 테스트 |

### 14.3 테스트 디바이스 목록

| 등급 | 기기 | Android 버전 | 비고 |
|------|------|-------------|------|
| 기준 기기 | Google Pixel 7 | Android 14 | CTS 호환성 기준 |
| 플래그십 | Samsung Galaxy S24 | Android 14 | 국내 점유율 1위 |
| 미드레인지 | Samsung Galaxy A54 | Android 14 | 보급형 주력 |
| 보급형 | Samsung Galaxy A24 | Android 13 | 저사양 성능 확인 |
| 최소 사양 | Samsung Galaxy A13 | Android 12 | 최소 지원 경계 테스트 |
| 중국 제조사 | Xiaomi Redmi Note 12 | Android 13 | MIUI 호환성 |
| 최소 OS | (구형 기기) | Android 8.0 | 최소 API 26 동작 확인 |

### 14.4 자동화 테스트 구성

| 테스트 유형 | 도구 | 커버리지 목표 | 범위 |
|------------|------|-------------|------|
| 단위 테스트 | JUnit 5 + MockK | 80% 이상 | Domain, Data (분석 로직) |
| 통합 테스트 | AndroidX Test | 60% 이상 | ViewModel + UseCase 연동 |
| UI 테스트 | Compose Test | 50% 이상 | 핵심 화면 플로우 |
| E2E 테스트 | Maestro 또는 UIAutomator | 핵심 시나리오 5개 | Quick Scan, Full Scan 등 |
| 센서 Mock | 커스텀 Mock | - | 자력계, 카메라 프레임 데이터 |

---

## 15. 운영 요구사항

### 15.1 모니터링

| 항목 | 도구 | 지표 |
|------|------|------|
| 크래시 리포팅 | Firebase Crashlytics | 크래시율, 영향 사용자 수, 스택 트레이스 |
| ANR 모니터링 | Play Console Vitals | ANR율, ANR 발생 위치 |
| 성능 모니터링 | Firebase Performance (Phase 2) | Cold Start 시간, 프레임 드롭 |
| 사용자 피드백 | Play Console 리뷰 | 평점 추이, 핵심 불만 키워드 |

### 15.2 크래시 리포팅 정책

| 항목 | 정책 |
|------|------|
| 수집 데이터 | 스택 트레이스, 기기 모델, OS 버전, 앱 버전 |
| 미수집 데이터 | 사용자 식별 정보, 스캔 결과, 위치 정보 |
| 보존 기간 | Firebase Crashlytics 기본 (90일) |
| 알림 | 새 크래시 이슈 발생 시 이메일/Slack 알림 |
| 대응 SLA | Critical (크래시율 1%+): 24시간 내 핫픽스 |

### 15.3 OTA 업데이트 전략

| 업데이트 유형 | 배포 채널 | 방식 | 주기 |
|-------------|-----------|------|------|
| 앱 업데이트 (코드 변경) | Google Play Store | 단계적 출시 (10% → 50% → 100%) | 2주 단위 |
| OUI DB 업데이트 (Phase 2) | 자체 CDN 또는 Firebase Storage | 인앱 다운로드 | 월 1회 |
| 긴급 핫픽스 | Google Play Store | 즉시 100% 출시 | 필요 시 |

### 15.4 앱 업데이트 단계적 출시 절차

```
1. 내부 테스트 (Internal Track)
   └─ QA 팀 + 자동화 테스트 통과

2. 비공개 테스트 (Closed Testing)
   └─ 베타 테스터 그룹 50~100명
   └─ 3일간 크래시율 및 피드백 모니터링

3. 공개 테스트 (Open Testing) [선택]
   └─ 공개 베타
   └─ 대규모 피드백 수집

4. 프로덕션 출시 (Production)
   └─ 10% 단계적 출시 → 24시간 모니터링
   └─ 크래시율 99.5%+ 유지 확인
   └─ 50% 출시 → 24시간 모니터링
   └─ 100% 출시
```

### 15.5 롤백 기준

| 지표 | 롤백 트리거 |
|------|------------|
| 크래시율 | Crash-free sessions 98% 미만 시 |
| ANR율 | 1% 초과 시 |
| 사용자 평점 | 업데이트 후 평점 0.5점 이상 하락 시 |
| 핵심 기능 장애 | 스캔 완료율 70% 미만 시 |

### 15.6 고객 지원

| 채널 | 응답 목표 | 담당 |
|------|-----------|------|
| Play Store 리뷰 답변 | 48시간 이내 | 운영팀 |
| 이메일 (support@) | 72시간 이내 | 운영팀 |
| FAQ (앱 내) | 즉시 | 앱 내장 |
| 인앱 피드백 | 집계 후 분석 | 개발팀 |

---

## 부록 A: 용어 정의

| 용어 | 정의 |
|------|------|
| ARP | Address Resolution Protocol. IP 주소를 MAC 주소로 변환하는 프로토콜 |
| mDNS | Multicast DNS. 로컬 네트워크에서 서비스를 탐색하는 프로토콜 |
| SSDP | Simple Service Discovery Protocol. UPnP 기기 탐색 프로토콜 |
| OUI | Organizationally Unique Identifier. MAC 주소 앞 3바이트로 제조사 식별 |
| RTSP | Real Time Streaming Protocol. 영상 스트리밍 프로토콜 (포트 554) |
| ONVIF | Open Network Video Interface Forum. IP 카메라 표준 프로토콜 |
| Retroreflection | 역반사. 빛이 들어온 방향으로 되돌아가는 물리적 현상 |
| EMF | Electromagnetic Field. 전자기장 |
| IR | Infrared. 적외선 |
| ANR | Application Not Responding. Android 앱 무응답 상태 |
| OTA | Over-The-Air. 무선 업데이트 |
| TFLite | TensorFlow Lite. 모바일 ML 추론 엔진 |
| LAPD | Laser-Assisted Photography Detection. ACM SenSys 2021 논문의 ToF 기반 렌즈 감지 기법 |

## 부록 B: 참고 문헌

| 번호 | 문헌 | 출처 |
|------|------|------|
| 1 | LAPD: Hidden Camera Detection using Smartphone Time-of-Flight Sensors | ACM SenSys 2021 |
| 2 | IEEE OUI Database | IEEE Standards Association |
| 3 | Android CameraX Documentation | Android Developers |
| 4 | Android SensorManager Documentation | Android Developers |
| 5 | Android Wi-Fi Scanning Restrictions | Android Developers |

---

*본 문서는 project-plan.md v3.1을 기반으로 작성된 기술 요구사항 정의서입니다.*
*Phase 1 (Android MVP) 범위에 한정하며, Phase 2/3 관련 항목은 향후 확장 계획으로 표기합니다.*
*문서 버전: v1.0 -- 2026-04-03*
