# SearCam 테스트 전략서

> 버전: v1.0
> 작성일: 2026-04-03
> 대상: Phase 1 Android MVP

---

## 1. 테스트 철학

SearCam은 사용자의 **물리적 안전**과 직결되는 앱이다. 오탐(false positive)은 불편을, 미탐(false negative)은 위험을 초래한다. 따라서 테스트는 "동작한다"가 아닌 **"정확하게 동작한다"**를 증명해야 한다.

### 1.1 테스트 피라미드

```
          ┌─────────┐
          │  E2E    │  10% (핵심 사용자 플로우)
          │ (UI)    │
         ─┼─────────┼─
         │Integration│  20% (센서→분석→결과 파이프라인)
         │           │
        ─┼───────────┼─
        │    Unit     │  70% (Domain + Data 레이어)
        │             │
        └─────────────┘
```

| 레벨 | 비율 | 실행 환경 | 실행 시간 목표 |
|------|------|----------|---------------|
| Unit | 70% | JVM (Robolectric 포함) | < 30초 |
| Integration | 20% | Android Instrumented | < 2분 |
| E2E | 10% | 실기기 / 에뮬레이터 | < 5분 |

### 1.2 커버리지 목표

| 레이어 | 최소 커버리지 | 목표 커버리지 |
|--------|-------------|-------------|
| domain/ | 90% | 95% |
| data/analysis/ | 85% | 90% |
| data/sensor/ | 70% | 80% |
| data/local/ | 80% | 85% |
| ui/ (ViewModel) | 80% | 85% |
| ui/ (Screen) | 60% | 70% |
| **전체** | **80%** | **85%** |

---

## 2. 단위 테스트 계획

### 2.1 Domain 레이어

#### 2.1.1 RunQuickScanUseCase

| # | 테스트 케이스 | 입력 | 기대 결과 |
|---|-------------|------|----------|
| 1 | Wi-Fi 연결 상태에서 정상 스캔 | Wi-Fi ON, 기기 목록 | ScanResult 반환 |
| 2 | Wi-Fi 미연결 시 스킵 처리 | Wi-Fi OFF | Layer1 스킵, 결과에 미실행 표기 |
| 3 | 빈 네트워크 (기기 0대) | Wi-Fi ON, ARP 비어있음 | 안전 결과 반환 |
| 4 | 타임아웃 발생 시 | 30초 초과 | TimeoutException 또는 부분 결과 |
| 5 | 권한 미부여 시 | LOCATION 미승인 | PermissionDeniedException |
| 6 | 스캔 중 취소 | 사용자 취소 | Job 취소, 부분 결과 반환 |
| 7 | 의심 기기 1대 발견 | Hikvision MAC 기기 | 위험도 40+ 결과 |
| 8 | 의심 기기 여러 대 발견 | 카메라 MAC 3대 | 최고 위험도 기기 기준 결과 |
| 9 | 안전 기기만 발견 | Apple/Samsung MAC | 위험도 0~19 |
| 10 | 스캔 진행률 콜백 | 중간 상태 | 0%→50%→100% Flow emit |

#### 2.1.2 RunFullScanUseCase

| # | 테스트 케이스 | 입력 | 기대 결과 |
|---|-------------|------|----------|
| 1 | 3개 레이어 모두 실행 | Wi-Fi ON, 센서 정상 | 3개 레이어 결과 포함 |
| 2 | Wi-Fi 미연결 시 2개 레이어만 | Wi-Fi OFF | Layer2+3만 실행, 가중치 재조정 |
| 3 | 밝은 환경 IR 불가 시 | 조도 높음 | Stage A만 실행, Stage B 스킵 |
| 4 | 모든 레이어 음성 | 이상 없음 | 종합 위험도 0~19 (안전) |
| 5 | 2개 레이어 양성 교차 | Wi-Fi + EMF 양성 | 보정계수 1.2 적용 |
| 6 | 3개 모두 양성 | 전부 양성 | 보정계수 1.5 적용, 80+ |
| 7 | EMF만 단독 양성 | 자기장만 이상 | 보정계수 0.5, 주의 등급 |
| 8 | 스캔 중간 취소 | 사용자 취소 | 완료된 레이어까지 결과 반환 |
| 9 | 센서 초기화 실패 | 자력계 없는 기기 | Layer3 스킵, 나머지 정상 |
| 10 | 전체 소요 시간 측정 | 정상 환경 | 3분 이내 완료 |

#### 2.1.3 RiskCalculator

| # | 테스트 케이스 | 입력 | 기대 결과 |
|---|-------------|------|----------|
| 1 | 카메라 MAC + RTSP 포트 | OUI 일치 + 554 개방 | 70+ (위험) |
| 2 | 안전 MAC + 포트 닫힘 | Apple MAC, 포트 무응답 | 0~5 (안전) |
| 3 | 카메라 MAC + 포트 닫힘 | OUI 일치, 포트 무응답 | 40~50 (주의) |
| 4 | 불명 MAC + RTSP 개방 | OUI 미등록, 554 개방 | 30~45 (주의) |
| 5 | Layer1 점수 계산 정확성 | MAC 40 + RTSP 30 + mDNS 25 | min(100, 95) = 95 |
| 6 | Layer2 점수 (렌즈 포인트) | 크기 3px, 원형도 0.9, 2초 고정 | 70+ |
| 7 | Layer3 점수 (자기장) | delta 25 uT | 50 (주의) |
| 8 | 종합 위험도 공식 검증 | W1*L1 + W2*L2 + W3*L3 | 정확한 합산 |
| 9 | 보정 계수 적용 | 2개 레이어 양성 | x1.2 반영 |
| 10 | 경계값 테스트 | 점수 19, 20, 39, 40, 59, 60, 79, 80 | 각 등급 정확 매핑 |
| 11 | 0점 하한 테스트 | 감점이 많아 음수 | max(0, 합산) |
| 12 | 100점 상한 테스트 | 모든 지표 만점 | min(100, 합산) |

#### 2.1.4 CrossValidator

| # | 테스트 케이스 | 입력 | 기대 결과 |
|---|-------------|------|----------|
| 1 | 기본 가중치 적용 | Wi-Fi ON | W1=0.50, W2=0.35, W3=0.15 |
| 2 | Wi-Fi 미연결 가중치 | Wi-Fi OFF | W1=0, W2=0.75, W3=0.25 |
| 3 | 밝은 환경 가중치 | IR 불가 | W2=0.30 (Stage A만) |
| 4 | Wi-Fi 없음 + 밝은 환경 | 둘 다 | W1=0, W2=0.80, W3=0.20 |
| 5 | 1개 레이어 양성 보정 | L1만 양성 | x0.7 |
| 6 | 2개 레이어 양성 보정 | L1+L3 양성 | x1.2 |
| 7 | 3개 모두 양성 보정 | 전부 양성 | x1.5 |
| 8 | Wi-Fi+IR 동시+같은 방향 | 방향 일치 | x1.4 |
| 9 | Wi-Fi만 양성 나머지 음성 | L1만 | x0.9 |
| 10 | EMF만 양성 나머지 음성 | L3만 | x0.5 |

#### 2.1.5 NoiseFilter

| # | 테스트 케이스 | 입력 | 기대 결과 |
|---|-------------|------|----------|
| 1 | noise_floor 이하 신호 제거 | delta < noise_floor | 무시 (필터링) |
| 2 | 급변 신호 제거 | 0.3초 내 50+ uT 변화 | 자체 간섭 무시 |
| 3 | 이동 평균 적용 | 10개 윈도우 데이터 | 평활화된 값 |
| 4 | 정상 신호 통과 | 유효 범위 delta | 필터 통과 |
| 5 | 캘리브레이션 baseline 계산 | 60 samples | mean(sqrt(x^2+y^2+z^2)) |
| 6 | noise_floor 계산 | 60 samples | std_dev * 2 |
| 7 | 연속 노이즈 시퀀스 | 10개 연속 급변 | 전부 필터링 |
| 8 | 경계값 (noise_floor 정확히) | delta == noise_floor | 통과 처리 |
| 9 | 빈 입력 | 데이터 0개 | 빈 결과 반환 |
| 10 | 단일 축 이상 | x축만 급변 | magnitude 기반 정상 판정 |

#### 2.1.6 ExportReportUseCase

| # | 테스트 케이스 | 입력 | 기대 결과 |
|---|-------------|------|----------|
| 1 | 정상 PDF 생성 | ScanReport | PDF 바이트 배열 반환 |
| 2 | 빈 리포트 PDF | 결과 없는 리포트 | "결과 없음" PDF 생성 |
| 3 | 한글 텍스트 인코딩 | 한국어 결과 | 한글 깨짐 없음 |
| 4 | 위험도 색상 포함 | 위험 등급 | 색상 코드 포함된 PDF |
| 5 | 기기 목록 포함 | 7대 기기 | 전체 목록 표시 |

### 2.2 Data 레이어

#### 2.2.1 WifiScanner

| # | 테스트 케이스 | 입력 | 기대 결과 |
|---|-------------|------|----------|
| 1 | ARP 테이블 파싱 | /proc/net/arp 내용 | IP+MAC 목록 |
| 2 | ARP 빈 테이블 | 빈 파일 | 빈 목록 |
| 3 | mDNS 서비스 탐색 | _rtsp._tcp 응답 | RTSP 서비스 기기 |
| 4 | mDNS 타임아웃 | 응답 없음 | 빈 결과, 에러 없음 |
| 5 | SSDP M-SEARCH 응답 | UPnP 기기 응답 | 기기 목록 |
| 6 | Wi-Fi 미연결 시 | 네트워크 없음 | 즉시 빈 결과 반환 |
| 7 | 중복 기기 제거 | 같은 IP 중복 발견 | 1개로 합침 |
| 8 | 잘못된 MAC 포맷 | "XX:XX" | 파싱 에러 처리 |
| 9 | 대량 기기 (50대+) | 대규모 네트워크 | 성능 저하 없이 처리 |
| 10 | 스캔 취소 처리 | Job 취소 | 리소스 정리 |

#### 2.2.2 MagneticSensor

| # | 테스트 케이스 | 입력 | 기대 결과 |
|---|-------------|------|----------|
| 1 | 캘리브레이션 성공 | 3초 안정 데이터 | baseline + noise_floor 설정 |
| 2 | 센서 미지원 기기 | TYPE_MAGNETIC_FIELD 없음 | SensorUnavailableException |
| 3 | 20Hz 데이터 스트림 | 센서 이벤트 | Flow<MagneticReading> emit |
| 4 | magnitude 계산 | x=30, y=40, z=0 | sqrt(2500) = 50 |
| 5 | delta 계산 | baseline=45, magnitude=60 | delta=15 |
| 6 | 등급 판정 (정상) | delta < 5 | 점수 0 |
| 7 | 등급 판정 (관심) | delta 5~15 | 점수 20 |
| 8 | 등급 판정 (주의) | delta 15~30 | 점수 50 |
| 9 | 등급 판정 (의심) | delta 30~50 | 점수 75 |
| 10 | 등급 판정 (강한 의심) | delta > 50 | 점수 95 |
| 11 | 감도 설정 변경 | 민감/보통/안정 | 임계값 변경 확인 |
| 12 | 리소스 해제 | onCleared | 리스너 해제 |

#### 2.2.3 OuiDatabase

| # | 테스트 케이스 | 입력 | 기대 결과 |
|---|-------------|------|----------|
| 1 | 카메라 제조사 매칭 | "28:57:BE" | Hikvision, risk=0.95 |
| 2 | 안전 제조사 매칭 | Apple MAC | risk=0.05 |
| 3 | 미등록 MAC | 없는 OUI | null (미등록) |
| 4 | 대소문자 무관 매칭 | "28:57:be" | Hikvision 매칭 |
| 5 | 하이픈 구분자 | "28-57-BE" | 정상 파싱 |
| 6 | 잘못된 MAC 포맷 | "ZZZZZZ" | null 반환 |
| 7 | 전체 DB 로드 성능 | 앱 시작 | < 100ms |
| 8 | 제조사별 risk_weight 검증 | ip_camera 타입 | 0.9 이상 |
| 9 | smart_camera 타입 | Wyze | risk=0.80 |
| 10 | consumer 타입 | Samsung | risk=0.05 |

#### 2.2.4 PortScanner

| # | 테스트 케이스 | 입력 | 기대 결과 |
|---|-------------|------|----------|
| 1 | RTSP 포트 개방 | 554 응답 | isOpen=true, +30점 |
| 2 | HTTP 포트 개방 | 80 응답 | isOpen=true, +15점 |
| 3 | ONVIF 포트 개방 | 3702 응답 | isOpen=true, +20점 |
| 4 | 모든 포트 닫힘 | 무응답 | 전부 isOpen=false |
| 5 | 연결 타임아웃 | 2초 무응답 | 포트 닫힘 처리 |
| 6 | 동시 다중 포트 스캔 | 4개 포트 | 병렬 실행 < 5초 |
| 7 | 유효하지 않은 IP | "999.999.999.999" | InvalidAddressException |
| 8 | 포트 번호 범위 검증 | 0, 65536 | IllegalArgumentException |
| 9 | 네트워크 타임아웃 | Wi-Fi 끊김 | IOException 처리 |
| 10 | 스캔 취소 처리 | Job 취소 | 소켓 닫기 |

#### 2.2.5 LensDetector

| # | 테스트 케이스 | 입력 | 기대 결과 |
|---|-------------|------|----------|
| 1 | 고휘도 포인트 추출 | 프레임 비트맵 | 상위 0.1% 포인트 목록 |
| 2 | 포인트 크기 필터 | 1~10px 포인트 | 통과 |
| 3 | 큰 반사 필터 | 15px+ 포인트 | 제거 |
| 4 | 원형도 검사 | circularity > 0.8 | 렌즈 의심 |
| 5 | 위치 안정성 (5프레임) | 동일 좌표 지속 | 의심 승격 |
| 6 | 플래시 OFF 소실 검증 | OFF 시 포인트 소실 | 렌즈 가능성 상승 |
| 7 | 이동 포인트 제외 | 프레임간 이동 | 반사 잔상으로 제외 |
| 8 | 시간축 분석 (2초) | 2초간 지속 포인트 | 의심 포인트 확정 |
| 9 | 깜빡이는 포인트 제외 | ON/OFF 반복 | LED로 판정, 제외 |
| 10 | 점수 산출 | 복합 조건 충족 | 정확한 점수 반환 |

#### 2.2.6 IrDetector

| # | 테스트 케이스 | 입력 | 기대 결과 |
|---|-------------|------|----------|
| 1 | 환경 밝기 체크 | 조도 높음 | "방을 어둡게 해주세요" |
| 2 | IR 포인트 감지 | 보라색/흰색 고휘도 | IR LED 의심 |
| 3 | 3초 지속 확인 | 지속 발광 | 카메라 의심 |
| 4 | 깜빡임 제외 | 간헐적 발광 | 일반 LED 판정 |
| 5 | 이동 포인트 제외 | 좌표 변경 | 반사 판정 |

---

## 3. 통합 테스트 계획

### 3.1 센서 -> 분석 -> 결과 파이프라인

| # | 테스트 시나리오 | 검증 항목 |
|---|---------------|----------|
| 1 | Quick Scan 전체 파이프라인 | WifiScanner → OuiDatabase → PortScanner → RiskCalculator → ScanResult |
| 2 | Full Scan 전체 파이프라인 | 3개 레이어 → CrossValidator → RiskCalculator → ScanResult |
| 3 | Wi-Fi OFF 시 Full Scan | Layer2+3만 실행, 가중치 재조정 확인 |
| 4 | 센서 데이터 → ViewModel → UI State | Flow 데이터가 UI State로 정확히 변환 |
| 5 | 에러 전파 | 센서 에러 → ViewModel 에러 상태 → UI 에러 표시 |

### 3.2 Room DB CRUD

| # | 테스트 시나리오 | 검증 항목 |
|---|---------------|----------|
| 1 | 리포트 저장 | ScanReport → ReportEntity 변환 → DB insert |
| 2 | 리포트 조회 (최신순) | 날짜 역순 정렬 확인 |
| 3 | 리포트 상세 조회 | ID로 조회 → 전체 데이터 복원 |
| 4 | 리포트 삭제 | delete 후 조회 시 없음 |
| 5 | 10건 제한 (무료) | 11번째 저장 시 가장 오래된 것 교체 |
| 6 | DB 마이그레이션 | 스키마 변경 시 데이터 유지 |
| 7 | 대량 데이터 성능 | 100건 저장 후 조회 < 100ms |

### 3.3 DI 구성 검증

| # | 테스트 시나리오 | 검증 항목 |
|---|---------------|----------|
| 1 | AppModule 주입 | 앱 전역 의존성 정상 주입 |
| 2 | SensorModule 주입 | WifiScanner, MagneticSensor 등 주입 |
| 3 | DatabaseModule 주입 | Room DB, DAO 주입 |
| 4 | ViewModel 주입 | UseCase → ViewModel 주입 체인 |
| 5 | Singleton 보장 | OuiDatabase, AppDatabase 단일 인스턴스 |

---

## 4. UI 테스트 계획 (Compose Testing)

### 4.1 테스트 환경

```kotlin
@get:Rule
val composeTestRule = createComposeRule()
```

### 4.2 화면별 테스트 시나리오

#### HomeScreen

| # | 시나리오 | 검증 |
|---|---------|------|
| 1 | 초기 렌더링 | Quick Scan 버튼, Full Scan/IR/EMF 버튼 표시 |
| 2 | Quick Scan 버튼 탭 | 스캔 화면으로 전환 |
| 3 | 마지막 스캔 카드 표시 | 이전 결과가 있으면 카드 표시 |
| 4 | 마지막 스캔 없음 | 카드 미표시 또는 안내 문구 |
| 5 | 네비게이션 바 탭 | 각 탭 이동 확인 |

#### QuickScanScreen

| # | 시나리오 | 검증 |
|---|---------|------|
| 1 | 스캔 진행 표시 | 프로그레스 바 + 단계 표시 |
| 2 | 발견 기기 수 실시간 업데이트 | 기기 발견 시 카운트 증가 |
| 3 | 스캔 완료 → 결과 표시 | 위험도 + 기기 목록 |
| 4 | 취소 버튼 | 스캔 중단, 부분 결과 표시 |

#### ScanResultScreen

| # | 시나리오 | 검증 |
|---|---------|------|
| 1 | 안전 등급 표시 | 초록색 게이지, 안전 메시지 |
| 2 | 위험 등급 표시 | 빨간색 게이지, 경고 메시지 |
| 3 | 기기 목록 표시 | 기기명, MAC, 위험도 |
| 4 | 리포트 저장 버튼 | 탭 시 DB 저장 + 토스트 |
| 5 | Full Scan 유도 | Quick Scan 후 Full Scan 안내 표시 |

#### MagneticScreen

| # | 시나리오 | 검증 |
|---|---------|------|
| 1 | 실시간 그래프 | 자기장 값 그래프 업데이트 |
| 2 | 위험 시 색상 변경 | delta 증가 시 빨간색 |
| 3 | 캘리브레이션 안내 | 첫 3초 "대기" 안내 |
| 4 | 감도 설정 변경 | 민감/보통/안정 전환 |

#### ReportListScreen / ReportDetailScreen

| # | 시나리오 | 검증 |
|---|---------|------|
| 1 | 리포트 목록 표시 | 날짜, 위험도, 장소 표시 |
| 2 | 빈 목록 | "아직 스캔 기록이 없습니다" |
| 3 | 리포트 탭 → 상세 | 상세 화면 전환 |
| 4 | PDF 내보내기 버튼 | PDF 생성 + 공유 인텐트 |

---

## 5. E2E 테스트 계획

### 5.1 Quick Scan 전체 플로우

```
[앱 실행] → [홈 화면] → [Quick Scan 탭]
  → [권한 허용] → [스캔 진행 30초]
  → [결과 화면: 위험도 + 기기 목록]
  → [리포트 저장] → [리포트 목록에서 확인]
```

**검증 항목**:
- 앱 시작 ~ 결과 표시 40초 이내
- 스캔 도중 화면 회전 시 상태 유지
- 백그라운드 전환 후 복귀 시 스캔 계속
- 결과 정확성 (알려진 기기 수 일치)

### 5.2 Full Scan 전체 플로우

```
[앱 실행] → [홈 화면] → [Full Scan 탭]
  → [권한 허용 (Wi-Fi + 카메라 + 위치)]
  → [Layer1 진행] → [Layer2 진행 (플래시 ON)]
  → [Layer3 진행 (자기장)] → [교차 검증]
  → [종합 결과 화면] → [리포트 저장]
```

**검증 항목**:
- 3개 레이어 순차 실행 확인
- 각 레이어 진행률 표시
- 교차 검증 보정 계수 반영
- 근거 기반 결과 설명 표시

### 5.3 리포트 저장/조회/PDF 플로우

```
[스캔 완료] → [리포트 저장]
  → [리포트 목록] → [리포트 탭]
  → [상세 화면] → [PDF 내보내기]
  → [공유 시트 표시]
```

**검증 항목**:
- 저장 후 목록에 즉시 표시
- 상세 내용 원본과 일치
- PDF 파일 생성 성공
- PDF 내 한글 정상 표시

---

## 6. 실기기 테스트 매트릭스

### 6.1 테스트 대상 카메라

| # | 카메라 종류 | 모델 (예시) | 탐지 방식 | 기대 탐지율 |
|---|-----------|-----------|----------|-----------|
| 1 | Wi-Fi IP 카메라 | Hikvision DS-2CD | Wi-Fi + OUI + RTSP | 85% |
| 2 | Wi-Fi 스마트 카메라 | Wyze Cam v3 | Wi-Fi + OUI + HTTP | 80% |
| 3 | Wi-Fi AP 내장 카메라 | 소형 AP 카메라 | SSID 스캔 | 75% |
| 4 | IR LED 야간 카메라 | IR 소형 카메라 A | IR 감지 (암실) | 75% |
| 5 | IR LED 야간 카메라 | IR 소형 카메라 B | IR 감지 (암실) | 70% |
| 6 | 핀홀 카메라 (유선) | 1mm 핀홀 | EMF + 렌즈 | 40% |

### 6.2 테스트 환경

| # | 환경 | 설명 | 핵심 시나리오 |
|---|------|------|-------------|
| 1 | 숙소형 | 침실 + 욕실, Wi-Fi 있음 | 연기감지기, 시계, USB 충전기에 카메라 배치 |
| 2 | 화장실형 | 공중화장실 모사, Wi-Fi 없음 | 거울, 환풍구, 칸막이 나사에 카메라 배치 |
| 3 | 사무실형 | 회의실, Wi-Fi 있음 | 모니터 베젤, 액자, 스프링클러에 카메라 배치 |

### 6.3 테스트 시나리오 (15개+)

| # | 시나리오 | 환경 | 카메라 | 기대 결과 |
|---|---------|------|--------|----------|
| 1 | Wi-Fi 카메라 1대, 같은 네트워크 | 숙소 | Hikvision | Quick Scan 탐지 |
| 2 | Wi-Fi 카메라 2대 + 안전 기기 5대 | 숙소 | Hikvision + Wyze | 2대 모두 식별 |
| 3 | Wi-Fi 카메라, 다른 네트워크 | 숙소 | Hikvision (별도 AP) | 미탐지, 한계 안내 |
| 4 | IR 카메라, 암실 조건 | 숙소 욕실 | IR 카메라 A | Stage B에서 탐지 |
| 5 | IR 카메라, 밝은 환경 | 숙소 | IR 카메라 A | 미탐지, IR 안내 |
| 6 | 렌즈 노출 카메라, 밝은 환경 | 숙소 | 핀홀 (렌즈 노출) | Stage A에서 탐지 |
| 7 | 전원 OFF 카메라, 렌즈 노출 | 숙소 | 소형 카메라 | Stage A 렌즈 반사 탐지 시도 |
| 8 | 거울 오탐 테스트 | 화장실 | 없음 (거울만) | 오탐 발생하지 않음 |
| 9 | 금속 물체 오탐 (EMF) | 사무실 | 없음 (금속 선반) | 오탐 필터링 확인 |
| 10 | Wi-Fi 없는 환경 Full Scan | 화장실 | IR 카메라 B | Layer2+3만 실행, 탐지 |
| 11 | 대규모 네트워크 (30+ 기기) | 사무실 | Wyze 1대 | 30초 내 완료, 정확 식별 |
| 12 | 연기감지기 위장 카메라 | 숙소 | 위장 카메라 | 렌즈 반사 탐지 |
| 13 | USB 충전기 위장 카메라 | 숙소 | 위장 카메라 | Wi-Fi + EMF 탐지 |
| 14 | 복수 카메라 (3대) 동시 탐지 | 숙소 | 3종 혼합 | 전부 식별 |
| 15 | 배터리+SD 카메라 (오프라인) | 화장실 | 오프라인 카메라 | 미탐지, 체크리스트 안내 |
| 16 | 스마트폰 기종별 센서 차이 | 전체 | 동일 카메라 | 기종별 결과 비교 |

### 6.4 테스트 대상 기기 (Android)

| # | 기기 | Android 버전 | 센서 특이사항 |
|---|------|-------------|-------------|
| 1 | Samsung Galaxy S24 | Android 14 | 기준 기기 |
| 2 | Samsung Galaxy A54 | Android 14 | 중급 기기 |
| 3 | Google Pixel 8 | Android 14 | 순정 Android |
| 4 | Samsung Galaxy S21 | Android 13 | 구형 플래그십 |
| 5 | Xiaomi Redmi Note 13 | Android 13 | 해외 중저가 |

---

## 7. 성능 테스트 계획

### 7.1 성능 지표

| 항목 | 목표 | 측정 방법 |
|------|------|----------|
| 앱 시작 시간 (콜드) | < 2초 | Firebase Performance |
| Quick Scan 소요 시간 | < 30초 | 로그 타임스탬프 |
| Full Scan 소요 시간 | < 3분 | 로그 타임스탬프 |
| 메모리 사용량 (스캔 중) | < 150MB | Android Profiler |
| 배터리 소모 (Full Scan 1회) | < 3% | Battery Historian |
| 프레임 드롭 (자기장 그래프) | < 5% 프레임 | GPU Profiler |
| OUI DB 로드 | < 100ms | 로그 타임스탬프 |
| Room DB 쿼리 | < 50ms | 로그 타임스탬프 |

### 7.2 스트레스 테스트

| 항목 | 시나리오 | 합격 기준 |
|------|---------|----------|
| 연속 스캔 | Quick Scan 10회 연속 | 메모리 누수 없음, 일관된 시간 |
| 대규모 네트워크 | 50대+ 기기 환경 | 30초 내 완료, OOM 없음 |
| 장시간 EMF | 10분 연속 측정 | 메모리 안정, 배터리 < 10% |
| 대량 리포트 | 100건 저장 후 목록 | 스크롤 버벅임 없음 |

---

## 8. Mock/Fake 전략

### 8.1 Mock 대상

| 의존성 | Mock 방식 | 사용 위치 |
|--------|----------|----------|
| SensorManager | Fake (FakeMagneticSensor) | Unit 테스트 |
| WifiManager | Mock (Mockito) | Unit 테스트 |
| NsdManager | Fake (FakeNsdManager) | Unit 테스트 |
| CameraX | Mock | Unit 테스트 |
| Room DB | In-Memory DB | Integration 테스트 |
| Network I/O | Mock Server (MockWebServer) | Integration 테스트 |
| /proc/net/arp | 파일 Mock | Unit 테스트 |

### 8.2 Fake 구현

```kotlin
// FakeMagneticSensor.kt
class FakeMagneticSensor : MagneticSensorPort {
    private val readings = MutableSharedFlow<MagneticReading>()

    suspend fun emit(reading: MagneticReading) {
        readings.emit(reading)
    }

    override fun observe(): Flow<MagneticReading> = readings

    fun emitCalibrationData(baseline: Float = 45f, count: Int = 60) {
        // 캘리브레이션용 안정 데이터 생성
    }

    fun emitAnomaly(delta: Float) {
        // 이상 신호 생성
    }
}
```

```kotlin
// FakeWifiScanner.kt
class FakeWifiScanner : WifiScannerPort {
    var devices: List<NetworkDevice> = emptyList()
    var shouldFail: Boolean = false

    override suspend fun scan(): Result<List<NetworkDevice>> {
        if (shouldFail) return Result.failure(IOException("Scan failed"))
        return Result.success(devices)
    }
}
```

### 8.3 테스트 데이터 팩토리

```kotlin
object TestDataFactory {
    fun createHikvisionDevice() = NetworkDevice(
        ip = "192.168.1.45",
        mac = "28:57:BE:AA:BB:CC",
        vendor = "Hikvision",
        type = DeviceType.IP_CAMERA,
        openPorts = listOf(554, 80),
        riskScore = 85
    )

    fun createSafeDevice() = NetworkDevice(
        ip = "192.168.1.10",
        mac = "AA:BB:CC:DD:EE:FF",
        vendor = "Apple",
        type = DeviceType.CONSUMER,
        openPorts = emptyList(),
        riskScore = 5
    )

    fun createMagneticReading(delta: Float = 0f) = MagneticReading(
        x = 30f, y = 40f, z = 0f,
        magnitude = 50f + delta,
        timestamp = System.currentTimeMillis()
    )
}
```

---

## 9. 테스트 실행 전략

### 9.1 개발 중

- **저장 시**: ktlint 자동 포맷
- **커밋 전**: `./gradlew test` (Unit 테스트)
- **PR 생성 시**: 전체 테스트 스위트 (CI)

### 9.2 CI 파이프라인

```
PR → lint → unit test → instrumented test → build → 커버리지 리포트
```

### 9.3 릴리스 전

- 전체 테스트 스위트 통과
- 실기기 테스트 매트릭스 80% 이상 통과
- 커버리지 80% 이상
- 성능 테스트 전 항목 합격

---

## 10. 테스트 도구

| 도구 | 용도 |
|------|------|
| JUnit 5 | 단위 테스트 프레임워크 |
| Mockito-Kotlin | Mock 생성 |
| Turbine | Flow 테스트 |
| Truth | Assertion 라이브러리 |
| Robolectric | JVM에서 Android 테스트 |
| Compose Testing | UI 컴포넌트 테스트 |
| Espresso | Instrumented UI 테스트 |
| Room Testing | In-Memory DB 테스트 |
| JaCoCo | 코드 커버리지 리포트 |
| Android Benchmark | 성능 벤치마크 |

---

*본 테스트 전략서는 Phase 1 Android MVP 기준이며, Phase 2 (iOS) 추가 시 업데이트됩니다.*
*버전 v1.0 -- 2026-04-03*
