# 26. 성능 및 배터리 최적화 계획서

> 버전: v1.0
> 작성일: 2026-04-03
> 관련 문서: project-plan.md (v3.1)

---

## 1. 성능 목표 (구체적 수치)

### 1.1 핵심 성능 KPI

| 항목 | 목표 | 측정 방법 | 허용 범위 |
|------|------|----------|----------|
| 앱 콜드 스타트 | **< 2초** | `Displayed` 로그 시간 측정 | 저사양: < 3초 |
| Quick Scan 완료 | **< 30초** | 스캔 시작~결과 표시 | 네트워크 상태 따라 +5초 허용 |
| Full Scan 완료 | **< 3분** | 3개 레이어 순차 완료 기준 | 최대 4분 |
| 카메라 프레임 처리 | **30fps 유지** | CameraX 프레임 분석 콜백 | 최소 24fps |
| 메모리 사용 (peak) | **< 150MB** | Android Profiler heap dump | 저사양: < 120MB |
| APK 크기 | **< 30MB** | 빌드 산출물 크기 | AAB 기준 < 25MB |
| 배터리 소모 (Quick Scan) | **< 2%** | Battery Historian 측정 | - |
| 배터리 소모 (Full Scan) | **< 5%** | Battery Historian 측정 | - |
| UI 프레임 드롭 | **< 5%** | `FrameMetricsAggregator` | jank 프레임 비율 |
| ANR 발생률 | **0건** | Play Console ANR 보고 | - |
| 크래시율 | **< 0.1%** | Firebase Crashlytics | - |

### 1.2 기기별 성능 기준

| 기기 등급 | 대표 기기 | 콜드 스타트 | Quick Scan | Full Scan | 메모리 |
|----------|----------|-----------|-----------|----------|--------|
| 고사양 | Pixel 8, Galaxy S24 | < 1.5초 | < 25초 | < 2.5분 | < 150MB |
| 중사양 | Galaxy A54, Pixel 6a | < 2초 | < 30초 | < 3분 | < 130MB |
| 저사양 | Galaxy A13, Redmi Note 12 | < 3초 | < 40초 | < 4분 | < 100MB |

### 1.3 성능 목표 달성 전략 요약

```
콜드 스타트 < 2초:
  ├── Hilt 초기화 지연 (lazy injection)
  ├── OUI DB 로딩 지연 (스캔 시작 시 로드)
  ├── Splash Screen API 활용 (초기화 마스킹)
  └── 불필요한 라이브러리 초기화 제거

Quick Scan < 30초:
  ├── ARP + mDNS 병렬 실행 (5초)
  ├── OUI 매칭 (1초, in-memory)
  ├── 포트 스캔 병렬화 (20초, 의심 기기만)
  └── 결과 집계 (1초)

Full Scan < 3분:
  ├── Quick Scan 결과 재사용 (30초)
  ├── 렌즈 감지 (60초, 사용자 가이드)
  ├── IR 감지 (30초, 암실 조건)
  └── 자기장 스캔 (30초)
```

---

## 2. CPU 최적화

### 2.1 센서 데이터 처리 최적화

#### 2.1.1 자기장 센서 (20Hz EMF)

자기장 센서는 20Hz(50ms 간격)로 데이터를 수집하며, 이동 평균 필터를 적용한다.

```
현재 처리 파이프라인:
  20Hz 원시 데이터 → 3축 크기 계산 → 이동 평균 → 노이즈 필터 → 임계값 판정

최적화 전략:
  1. 이동 평균 윈도우 최적화
     ├── 기본: window = 10 (0.5초 분량)
     ├── 링 버퍼 사용 (배열 재할당 방지)
     └── 매 샘플마다 전체 재계산 대신 증분 계산(incremental)

  2. 크기(magnitude) 계산 최적화
     ├── sqrt(x² + y² + z²) → 비교 목적이면 제곱합만으로 충분
     ├── 임계값도 제곱 형태로 미리 변환하여 sqrt 연산 제거
     └── 최종 사용자 표시 시에만 sqrt 적용

  3. 배치 처리
     ├── 매 샘플(50ms)마다 UI 업데이트 대신 200ms 단위 배치
     ├── UI 스레드 부하 75% 감소 (20회/초 → 5회/초)
     └── 실시간 그래프는 5fps로 충분 (체감 차이 없음)
```

```kotlin
// 증분 이동 평균 구현 (의사코드)
class IncrementalMovingAverage(private val windowSize: Int) {
    private val buffer = FloatArray(windowSize)
    private var sum = 0f
    private var index = 0
    private var count = 0

    fun add(value: Float): Float {
        val oldest = buffer[index]
        buffer[index] = value
        sum = sum - oldest + value
        index = (index + 1) % windowSize
        count = minOf(count + 1, windowSize)
        return sum / count
    }
}
```

#### 2.1.2 카메라 프레임 분석 (렌즈 감지)

```
현재: 30fps × 1080p = 초당 30프레임 전체 분석
문제: CPU 과부하 + 발열

최적화 전략:
  1. 해상도 다운스케일
     ├── 1080p → 720p 다운스케일 (프레임 크기 56% 감소)
     ├── 렌즈 반사 포인트는 1~10px → 720p에서도 감지 가능
     └── ImageAnalysis.setTargetResolution(1280, 720)

  2. 선택적 프레임 분석
     ├── 30fps 중 매 2번째 프레임만 분석 (실효 15fps)
     ├── 의심 포인트 발견 시 → 일시적으로 30fps 전체 분석 전환
     ├── 2초간 포인트 미발견 시 → 다시 15fps로 복귀
     └── 적응형 프레임 레이트로 CPU 평균 50% 절감

  3. ROI(Region of Interest) 분석
     ├── 전체 프레임 분석 대신 관심 영역만 분석
     ├── 최초 전체 스캔 → 고휘도 영역 식별 → 해당 영역만 추적
     └── 분석 면적 70% 감소

  4. GPU 오프로딩 (Phase 2)
     ├── RenderScript 또는 Vulkan Compute로 고휘도 포인트 추출
     ├── 그레이스케일 변환 → GPU 커널로 처리
     └── CPU 부하 80% 감소 (GPU에 위임)
```

#### 2.1.3 포트 스캔 최적화

```
현재: 의심 기기 N개 × 포트 6개 = 순차 연결 시도
문제: 기기당 타임아웃 5초 × 6포트 = 최대 30초/기기

최적화 전략:
  1. 코루틴 기반 병렬 스캔
     ├── Dispatchers.IO (64 스레드 기본)
     ├── 기기별 6포트 동시 스캔 (기기 내 병렬)
     ├── 최대 4기기 동시 스캔 (기기 간 병렬)
     └── 동시 연결 수 제한: 최대 24개 (4 × 6)

  2. 타임아웃 단축
     ├── 로컬 네트워크: 연결 타임아웃 2초 (5초 → 2초)
     ├── 첫 응답 수신 후 판정: 데이터 타임아웃 1초
     └── 연결 실패 시 즉시 다음 포트로 이동

  3. 우선순위 기반 스캔
     ├── RTSP(554) 먼저 스캔 → 양성이면 나머지 스킵 가능
     ├── OUI 매칭된 기기 우선 스캔
     └── 안전 벤더(Apple, Samsung 등)는 포트 스캔 스킵
```

### 2.2 백그라운드 스레드 관리

```
스레드 할당 전략:

  Dispatchers.Main (UI 스레드):
    ├── Compose UI 렌더링
    ├── 사용자 인터랙션 처리
    └── 결과 표시 업데이트 (5fps 배치)

  Dispatchers.Default (CPU 바운드):
    ├── 자기장 데이터 처리 (이동 평균, 필터)
    ├── 카메라 프레임 분석 (고휘도 포인트 추출)
    ├── OUI 매칭 (인메모리 해시맵 조회)
    ├── 교차 검증 위험도 계산
    └── 최대 CPU 코어 수만큼 병렬 처리

  Dispatchers.IO (I/O 바운드):
    ├── ARP 테이블 파일 읽기 (/proc/net/arp)
    ├── mDNS/SSDP 네트워크 요청
    ├── 포트 스캔 (TCP 소켓 연결)
    ├── Room DB 읽기/쓰기
    ├── PDF 파일 생성
    └── OUI JSON 파일 최초 로딩

  사용 금지:
    ├── Dispatchers.Unconfined (예측 불가 동작)
    └── newSingleThreadContext (스레드 낭비)
```

### 2.3 코루틴 스코프 관리

```
ViewModel 스코프:
  ├── viewModelScope 사용 (화면 이탈 시 자동 취소)
  ├── 스캔 진행 중 화면 이탈 → 코루틴 취소 → 리소스 해제
  └── SupervisorJob으로 한 작업 실패가 전체에 영향 안 주도록

서비스 스코프 (포그라운드 서비스):
  ├── Full Scan 중 앱 백그라운드 전환 시
  ├── ForegroundService + 알림으로 사용자에게 진행 안내
  └── 서비스 스코프는 서비스 수명주기에 연동
```

---

## 3. 메모리 최적화

### 3.1 카메라 프레임 버퍼 풀링

```
문제:
  CameraX 프레임 분석 시 매 프레임 ImageProxy 생성
  30fps × 720p 프레임 = 초당 ~27MB 할당
  GC 빈번 발생 → 프레임 드롭

해결: 프레임 버퍼 풀링
  1. 분석용 ByteBuffer 풀 (3개 사전 할당)
     ├── 풀에서 버퍼 대여 → 분석 → 풀에 반납
     ├── 새 할당 없이 재사용 (GC 압박 제거)
     └── ImageProxy.close() 호출 타이밍 엄수

  2. 분석 결과 캐싱
     ├── 이전 프레임 의심 포인트 좌표 캐싱 (8바이트/포인트)
     ├── 시간축 분석용 링 버퍼 (60프레임 = 2초)
     └── 캐시 크기: 최대 ~5KB (무시 가능)

  3. ImageProxy 즉시 반환
     ├── 분석 완료 즉시 close() 호출 (지연 금지)
     ├── close() 미호출 시 CameraX 파이프라인 블로킹
     └── try-finally로 보장
```

### 3.2 OUI 데이터베이스 로딩 전략

```
문제:
  OUI DB JSON 파일 크기: ~500KB
  파싱 후 인메모리 HashMap: ~2MB
  콜드 스타트 시 로딩하면 시작 지연

해결: Lazy Loading + 캐싱
  1. 앱 시작 시: OUI DB 로딩하지 않음 (콜드 스타트 영향 제거)

  2. 첫 Quick Scan 시작 시:
     ├── 백그라운드 코루틴으로 JSON 파싱
     ├── HashMap<String, OuiEntry> 구조로 변환
     │   key = "28:57:BE" (OUI prefix)
     │   value = OuiEntry(vendor, type, risk)
     ├── 파싱 완료까지 ARP/mDNS 먼저 실행 (병렬)
     └── 로딩 시간: ~200ms (중사양 기기 기준)

  3. 메모리 상주:
     ├── 한번 로딩 후 Application 스코프에 캐싱
     ├── 앱 종료까지 유지 (재파싱 불필요)
     └── 메모리 압박 시 WeakReference로 자동 해제 + 재로딩

  4. 최적화된 데이터 구조:
     ├── JSON → Binary Format 전환 고려 (Phase 2)
     ├── Protocol Buffers 또는 FlatBuffers
     └── 파싱 시간 50% 감소, 메모리 30% 감소
```

### 3.3 비트맵 재활용

```
렌즈 감지 UI에서 카메라 프리뷰 위 오버레이 비트맵 사용:
  ├── 의심 포인트 표시용 Bitmap (720 × 480)
  ├── 매 프레임 새 Bitmap 생성 금지
  ├── Canvas 기반 재사용 (clearRect → drawCircle)
  └── Bitmap.recycle() 확실한 호출

Compose에서의 이미지 최적화:
  ├── BitmapFactory.Options.inSampleSize로 축소 로딩
  ├── 체크리스트 이미지: 원본 대신 썸네일 사용
  └── Coil 라이브러리 캐싱 활용 (Phase 2)
```

### 3.4 메모리 누수 방지 체크리스트

```
[개발 시 필수 확인]

  □ SensorManager 리스너 해제
    ├── onPause() 또는 onCleared()에서 unregisterListener()
    └── 미해제 시: Activity 레퍼런스 유지 → 누수

  □ CameraX UseCase 해제
    ├── ProcessCameraProvider.unbindAll()
    └── ImageAnalysis.Analyzer 참조 해제

  □ Flow 컬렉션 스코프
    ├── collectAsStateWithLifecycle() 사용 (Compose)
    ├── repeatOnLifecycle(STARTED) 사용 (Fragment)
    └── launchWhenResumed 대신 위 방식 사용

  □ 코루틴 취소
    ├── viewModelScope 사용 시 자동 취소
    ├── 커스텀 스코프 사용 시 onCleared()에서 cancel()
    └── Job 참조 보관 + 명시적 취소

  □ Context 참조
    ├── Activity Context를 장기 보관 금지
    ├── Application Context만 DI로 주입
    └── ViewModel에 Activity/Fragment 참조 금지

  □ Callback/Listener 해제
    ├── NsdManager.DiscoveryListener 해제
    ├── ConnectivityManager.NetworkCallback 해제
    └── BroadcastReceiver 해제
```

---

## 4. 배터리 최적화

### 4.1 센서 폴링 주기 최적화

```
자기장 센서:
  ├── 기본 모드: SENSOR_DELAY_GAME (20ms ≈ 50Hz)
  │   → 과도함. 실제 필요: 20Hz (50ms)
  ├── 최적화: SENSOR_DELAY_UI (66ms ≈ 15Hz) + 보간
  │   → 배터리 소모 40% 감소
  ├── 높은 정밀도 필요 시: SENSOR_DELAY_GAME 유지
  └── 사용자 설정에서 선택 가능

적응형 폴링:
  ├── 이상 신호 없을 때: 10Hz (100ms)
  ├── 약한 신호 감지 시: 20Hz (50ms)
  ├── 강한 의심 시: 50Hz (20ms, 최대 정밀도)
  └── 평균 배터리 소모 50% 감소 (대부분 정상 구간)
```

### 4.2 플래시 사용 시간 제한

```
플래시는 배터리 소모의 주범:
  ├── LED 플래시: ~1W 소모
  ├── 3분 연속 사용 시: 배터리 ~1.5% 소모

제한 전략:
  1. 자동 타이머
     ├── 렌즈 감지 모드: 기본 60초 제한 (연장 가능)
     ├── 30초 경과 시 "연장하시겠습니까?" 안내
     └── 최대 3분 (배터리 경고 후 자동 OFF)

  2. 간헐적 플래시
     ├── 연속 ON 대신 200ms ON / 50ms OFF 패턴
     ├── 인간 눈에는 연속으로 보임 (50Hz 이상)
     ├── 배터리 소모 20% 감소
     └── 의심 포인트 검증 시에만 연속 ON

  3. 배터리 잔량 체크
     ├── 배터리 < 20%: 플래시 사용 자제 안내
     ├── 배터리 < 10%: 플래시 모드 비활성화
     └── 사용자에게 충전 후 스캔 권장
```

### 4.3 화면 밝기 관리

```
IR 감지 모드 (암실):
  ├── 화면 밝기 자동 최저로 조절 (IR 간섭 방지 + 배터리 절약)
  ├── 스캔 종료 후 원래 밝기 복원
  └── OLED 기기에서 효과 극대화

일반 스캔 모드:
  ├── 시스템 밝기 유지 (사용자 설정 존중)
  ├── 화면 꺼짐 방지: FLAG_KEEP_SCREEN_ON
  └── 스캔 완료 후 플래그 해제
```

### 4.4 백그라운드 작업 최소화

```
정책:
  ├── 백그라운드에서 스캔 실행하지 않음 (배터리 소모 방지)
  ├── 앱 전환 시 센서 일시정지 (onPause)
  ├── Full Scan 중 앱 전환 시:
  │   ├── ForegroundService로 전환
  │   ├── 알림으로 진행 상태 표시
  │   └── 센서 데이터 수집만 유지, UI 업데이트 중단
  └── 스캔 미진행 시 모든 센서 해제

Wake Lock 정책:
  ├── 스캔 중에만 PARTIAL_WAKE_LOCK 획득
  ├── 스캔 완료 즉시 해제
  ├── 타임아웃 설정: 최대 5분 (안전장치)
  └── 정상 종료 시 onDestroy()에서 해제 보장
```

### 4.5 배터리 소모량 추정 (스캔 타입별)

| 스캔 타입 | 소요 시간 | 주요 소모원 | 예상 배터리 소모 |
|----------|----------|-----------|----------------|
| Quick Scan | 30초 | Wi-Fi + CPU | ~0.5% |
| Full Scan | 3분 | Wi-Fi + 카메라 + 플래시 + CPU | ~3~5% |
| 렌즈 찾기 | 1~3분 | 카메라 + 플래시 | ~2~4% |
| IR Only | 1~2분 | 전면 카메라 | ~1~2% |
| EMF Only | 1~3분 | 자기장 센서 + CPU | ~0.5~1% |

```
소모 상세 분석 (Full Scan 3분 기준):
  Wi-Fi 스캔 (30초):    ~0.3%
  카메라 + 프레임 분석:  ~1.5%
  플래시 (60초):         ~1.0%
  자기장 센서 (60초):    ~0.2%
  CPU 연산:             ~0.5%
  화면 ON:              ~0.5%
  ──────────────────────────
  합계:                 ~4.0%
```

---

## 5. 네트워크 최적화

### 5.1 포트 스캔 병렬화

```
동시 연결 수 제한:
  ├── 최대 동시 TCP 연결: 24개 (4기기 × 6포트)
  ├── Semaphore(24)로 제한
  ├── 과도한 연결은 라우터 부하 + 네트워크 불안정 유발
  └── 호텔/에어비앤비 라우터는 보통 저사양

타임아웃 설정:
  ├── 연결 타임아웃: 2초 (로컬 네트워크)
  ├── 읽기 타임아웃: 1초 (배너 그래빙)
  ├── 전체 기기 스캔 타임아웃: 20초
  └── 타임아웃 초과 시 해당 포트 닫힘으로 판정
```

### 5.2 mDNS/SSDP 최적화

```
mDNS (NsdManager):
  ├── 탐색 시간: 최대 5초 (대부분 2초 내 응답)
  ├── 서비스 타입별 순차 탐색 대신 병렬 탐색
  │   ├── _rtsp._tcp (카메라 스트리밍)
  │   ├── _http._tcp (웹 인터페이스)
  │   └── _onvif._tcp (IP 카메라)
  └── 결과 수신 즉시 처리 (대기 없음)

SSDP (UPnP):
  ├── M-SEARCH 멀티캐스트 1회 발송
  ├── 응답 대기: 3초
  ├── UDP이므로 재전송 1회 (신뢰성)
  └── 응답 파싱은 Dispatchers.Default에서 처리
```

---

## 6. 저사양 기기 대응

### 6.1 기기 등급 자동 판별

```
앱 시작 시 기기 등급 자동 분류:

  val tier = when {
      Runtime.getRuntime().availableProcessors() >= 8
        && memoryInfo.totalMem > 6GB  → HIGH
      Runtime.getRuntime().availableProcessors() >= 4
        && memoryInfo.totalMem > 3GB  → MEDIUM
      else                            → LOW
  }
```

### 6.2 등급별 적응형 설정

| 설정 항목 | HIGH | MEDIUM | LOW |
|----------|------|--------|-----|
| 카메라 분석 해상도 | 720p | 720p | 480p |
| 프레임 분석 fps | 15fps | 10fps | 5fps |
| EMF 폴링 주기 | 20Hz | 15Hz | 10Hz |
| 포트 스캔 동시 연결 | 24 | 16 | 8 |
| 실시간 그래프 | 60fps 렌더링 | 30fps | 15fps |
| 카메라 오버레이 | 실시간 | 0.5초 딜레이 | 1초 딜레이 |
| 이동 평균 윈도우 | 10 | 8 | 5 |

### 6.3 기능 제한 모드

```
LOW 등급 기기에서 자동 적용:
  ├── 렌즈 감지: 720p → 480p 다운스케일
  ├── 실시간 그래프: 단순 막대 그래프로 대체
  ├── 동시 포트 스캔: 8개로 제한
  ├── 카메라 오버레이 애니메이션: 단순화
  └── 사용자에게 "저사양 모드" 안내 (설정에서 해제 가능)

메모리 부족 대응 (< 50MB 가용):
  ├── OUI DB 축소 로딩 (카메라 제조사만, 화이트리스트 생략)
  ├── 카메라 버퍼 풀: 3개 → 2개
  ├── 리포트 이미지 해상도 축소
  └── onTrimMemory() 콜백에서 캐시 해제
```

---

## 7. 프로파일링 도구 및 방법

### 7.1 Android Profiler

```
사용 시점:
  ├── 매 Sprint 종료 시 성능 프로파일링 (필수)
  ├── 새 기능 추가 후 회귀 확인
  └── 사용자 성능 이슈 제보 시

측정 항목:
  CPU Profiler:
    ├── Method Trace: 핫 함수 식별
    ├── System Trace: 스레드 스케줄링 확인
    └── 목표: 메인 스레드 CPU 사용 < 30% (스캔 중)

  Memory Profiler:
    ├── Heap Dump: 메모리 누수 탐지
    ├── Allocation Tracking: 과도한 할당 식별
    └── 목표: peak 150MB 미만, GC < 초당 2회

  Energy Profiler:
    ├── 센서 사용 시간 모니터링
    ├── Wake Lock 확인
    └── 네트워크 활동 패턴 분석
```

### 7.2 LeakCanary

```
설정:
  ├── debugImplementation 'com.squareup.leakcanary:leakcanary-android:2.x'
  ├── 디버그 빌드에서만 활성화
  ├── Activity, Fragment, ViewModel, Service 누수 자동 감지
  └── CI에서 LeakCanary 인스트루먼트 테스트 실행

주요 감시 대상:
  ├── ScanViewModel (센서 리스너 포함)
  ├── LensFinderScreen (CameraX 바인딩)
  ├── MagneticScreen (SensorManager 리스너)
  └── WifiScanner (NsdManager 리스너)
```

### 7.3 StrictMode

```
디버그 빌드에서 활성화:

  StrictMode.setThreadPolicy(
    ThreadPolicy.Builder()
      .detectDiskReads()       // 메인 스레드 디스크 읽기
      .detectDiskWrites()      // 메인 스레드 디스크 쓰기
      .detectNetwork()         // 메인 스레드 네트워크
      .detectCustomSlowCalls() // 커스텀 느린 호출
      .penaltyLog()
      .build()
  )

  StrictMode.setVmPolicy(
    VmPolicy.Builder()
      .detectLeakedClosableObjects()  // 닫히지 않은 리소스
      .detectActivityLeaks()          // Activity 누수
      .penaltyLog()
      .build()
  )
```

### 7.4 Firebase Performance Monitoring (Phase 2)

```
프로덕션 성능 모니터링:
  ├── 콜드 스타트 시간 자동 수집
  ├── 커스텀 Trace:
  │   ├── quick_scan_duration
  │   ├── full_scan_duration
  │   ├── lens_detection_duration
  │   ├── port_scan_duration
  │   └── oui_db_load_duration
  ├── 기기별 / OS 버전별 / 지역별 성능 분석
  └── 성능 저하 시 알림
```

---

## 8. 성능 회귀 테스트 전략

### 8.1 자동화된 성능 테스트

```
CI/CD에서 매 빌드 실행:

  1. 콜드 스타트 벤치마크
     ├── Macrobenchmark 라이브러리 사용
     ├── 5회 측정 → 중앙값 기록
     ├── 기준: < 2초 (실패 시 빌드 경고)
     └── 기기: Pixel 6a 에뮬레이터

  2. 프레임 레이트 테스트
     ├── 렌즈 감지 화면 10초 실행
     ├── FrameMetricsAggregator로 jank 프레임 측정
     ├── 기준: jank < 5% (실패 시 빌드 경고)
     └── 실 기기 테스트 (Farm 또는 수동)

  3. 메모리 벤치마크
     ├── Full Scan 시뮬레이션 실행
     ├── peak 메모리 측정
     ├── 기준: < 150MB (실패 시 빌드 블로킹)
     └── GC 횟수 기록
```

### 8.2 성능 예산 (Performance Budget)

```
매 PR 검사 항목:

  | 항목 | 예산 | 초과 시 |
  |------|------|---------|
  | APK 크기 증가 | < 500KB | PR 코멘트 경고 |
  | 새 의존성 추가 | 사전 승인 필요 | PR 블로킹 |
  | 콜드 스타트 증가 | < 100ms | PR 코멘트 경고 |
  | 메모리 peak 증가 | < 10MB | PR 블로킹 |
```

### 8.3 릴리스 전 성능 체크리스트

```
  □ 콜드 스타트 < 2초 (저사양 < 3초)
  □ Quick Scan < 30초
  □ Full Scan < 3분
  □ 메모리 peak < 150MB
  □ ANR 0건 (테스트 세션 기준)
  □ LeakCanary 누수 0건
  □ StrictMode 위반 0건 (릴리스 빌드)
  □ APK 크기 < 30MB
  □ Battery Historian: Full Scan 5% 이하
  □ jank 프레임 < 5%
```

---

*본 문서는 project-plan.md v3.1의 기술 아키텍처를 기반으로 작성되었습니다.*
*Phase 1 (Android MVP) 개발 시 본 최적화 계획을 병행 적용합니다.*
