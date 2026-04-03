# SearCam: 스마트폰 몰래카메라 탐지 앱 개발기

## 부제: 기획부터 출시까지, AI 시대의 모바일 앱 개발 완전 가이드

---

## 1. 책 소개

### 1.1 왜 이 책을 쓰는가

몰래카메라(불법촬영) 문제는 한국 사회에서 심각한 범죄 유형이다.
전문 탐지 장비는 수십만 원에서 수백만 원이며, 일반인이 접근하기 어렵다.
SearCam은 "스마트폰만으로 30초 안에 1차 스크리닝"이라는 목표로 시작되었다.

이 책은 SearCam의 기획부터 출시까지 전 과정을 기술 서적으로 풀어낸다.
29개의 기획 문서(PRD, TRD, TDD, 시스템 아키텍처, 보안 설계 등)를 기반으로
실제 상용 앱이 탄생하기까지의 의사결정, 기술 선택, 실패와 해결을 기록한다.

### 1.2 대상 독자

| 독자 유형 | 이 책에서 얻는 것 |
|-----------|------------------|
| 주니어 모바일 개발자 (1~3년) | 앱 하나를 처음부터 끝까지 만드는 전체 흐름 |
| 사이드 프로젝트 개발자 | 기획서 작성법, 수익 모델, GTM 전략 실전 사례 |
| 스타트업 창업자/PM | 기술 문서 체계, 아키텍처 의사결정 프로세스 |
| CS/SE 전공 대학생 | 소프트웨어 공학 이론의 실전 적용 사례 |
| 시니어 개발자 | 모바일 센서 활용, 보안/프라이버시 설계 패턴 |

### 1.3 사전 지식

- Kotlin/Android 기초 문법 (변수, 함수, 클래스)
- Git 기본 사용법
- REST API 개념
- (권장) Clean Architecture 용어 이해

---

## 2. 전체 구성

### 총 5부 24챕터 + 부록 5개

```
전체 분량 추정: 400~500페이지 (A5 기준)
챕터 평균: 15~20페이지
코드 비중: 전체의 약 30%
다이어그램/표: 챕터당 평균 3~5개
```

---

## 3. Part 1: 기획과 설계 (Why & What)

> 왜 만드는가, 누구를 위해 만드는가, 무엇을 만드는가

### Chapter 01: 문제 발견과 시장 분석

- **기반 문서**: `project-plan.md`, `11-competitor-analysis.md`
- **핵심 내용**:
  - 몰래카메라 범죄 현황과 사회적 배경
  - 기존 탐지 앱 5종 분석 (Hidden Camera Detector, Glint Finder 등)
  - 경쟁 분석 프레임워크: 기능 매트릭스, SWOT
  - SearCam의 차별점: 3-Layer 교차 검증, 근거 기반 결과 설명
  - "없는 것보다 훨씬 나은 1차 스크리닝 도구" 포지셔닝 결정 과정
- **분량**: 18페이지
- **실습**: 경쟁 분석 템플릿 작성

### Chapter 02: 사용자 이해하기

- **기반 문서**: `10-user-research.md`
- **핵심 내용**:
  - 사용자 리서치 방법론 (인터뷰, 설문, 페르소나)
  - 4개 핵심 페르소나: 여행자, 1인 가구 여성, 출장 직장인, 피트니스 이용자
  - 사용자 여정 맵 (Customer Journey Map)
  - 핵심 니즈 도출: "30초 안에 안심"
  - 사용 맥락별 시나리오 (에어비앤비 체크인, 공중화장실, 탈의실)
- **분량**: 15페이지
- **실습**: 페르소나 카드 만들기

### Chapter 03: 제품 요구사항 정의

- **기반 문서**: `01-PRD.md`
- **핵심 내용**:
  - PRD 작성법: 왜 필요한가, 어떤 구조인가
  - 기능 요구사항 vs 비기능 요구사항
  - MoSCoW 우선순위 결정법
  - 현실 기반 목표 설정: 종합 탐지율 70~75%, 오탐율 5~10%
  - "솔직한 한계 안내" 원칙 — 거짓 안심 방지
  - 스코프 관리: MVP에 무엇을 넣고 무엇을 빼는가
- **분량**: 20페이지
- **실습**: PRD 체크리스트 검증

### Chapter 04: 수익 모델과 비즈니스 전략

- **기반 문서**: `24-revenue-model.md`, `22-gtm-strategy.md`
- **핵심 내용**:
  - 안전 앱의 수익화 딜레마: 핵심 기능은 무료여야 한다
  - Freemium 모델 설계: 무료 vs 프리미엄 경계
  - 구독, 광고, B2B 라이선스 비교
  - GTM (Go-to-Market) 전략 개요
  - 단가 설정과 LTV/CAC 시뮬레이션
- **분량**: 16페이지
- **실습**: 수익 모델 캔버스 작성

---

## 4. Part 2: 아키텍처와 설계 (How)

> 어떻게 만들 것인가 — 기술적 청사진

### Chapter 05: 기술 요구사항과 제약

- **기반 문서**: `02-TRD.md`
- **핵심 내용**:
  - TRD(Technical Requirements Document)의 역할
  - 스마트폰에서 되는 것 / 안 되는 것 구분
  - Android vs iOS 플랫폼 제약 비교
  - Wi-Fi CSI 불가, Monitor Mode 불가 등 현실적 한계
  - 기술 스택 선정 근거: Kotlin, Jetpack Compose, Room DB
  - 성능 요구사항: Quick Scan 30초 이내
- **분량**: 18페이지
- **실습**: 기술 제약 매트릭스 작성

### Chapter 06: 시스템 아키텍처 설계

- **기반 문서**: `04-system-architecture.md`, `05-data-flow.md`
- **핵심 내용**:
  - Clean Architecture + MVVM 패턴 선택 이유
  - 레이어 구조: Presentation → Domain → Data
  - 의존성 방향: 단방향, Domain이 외부에 의존하지 않음
  - 데이터 플로우: 센서 입력 → 처리 → 교차 검증 → 결과 표시
  - 3-Layer 탐지 시스템 아키텍처:
    - Layer 1: Wi-Fi 네트워크 스캔
    - Layer 2: 플래시 Retroreflection 렌즈 감지
    - Layer 3: 자기장(EMF) 감지
  - 모듈 간 통신과 이벤트 버스
- **분량**: 22페이지
- **실습**: 아키텍처 다이어그램 그리기

### Chapter 07: 기술 설계서 작성법

- **기반 문서**: `03-TDD.md`
- **핵심 내용**:
  - TDD(Technical Design Document)의 구조와 작성법
  - 설계 결정 기록 (ADR: Architecture Decision Record)
  - 대안 비교와 트레이드오프 문서화
  - 코드 레벨 설계: 클래스 다이어그램, 시퀀스 다이어그램
  - 설계 리뷰 프로세스
- **분량**: 16페이지
- **실습**: ADR 작성 연습

### Chapter 08: API와 데이터베이스 설계

- **기반 문서**: `06-api-design.md`, `07-db-schema.md`
- **핵심 내용**:
  - REST API 설계 원칙과 SearCam API 엔드포인트
  - 로컬 DB 스키마: Room + SQLite
  - 스캔 결과 저장 구조
  - OUI(Organizationally Unique Identifier) 데이터베이스 설계
  - 오프라인 우선(Offline-first) 데이터 전략
  - 데이터 마이그레이션 전략
- **분량**: 18페이지
- **실습**: ER 다이어그램 작성

### Chapter 09: 보안과 개인정보 설계

- **기반 문서**: `14-security-design.md`, `17-privacy-impact.md`
- **핵심 내용**:
  - 보안 설계 원칙: Defense in Depth
  - 네트워크 스캔 데이터의 민감성
  - 개인정보 영향 평가 (PIA)
  - GDPR/개인정보보호법 준수 설계
  - 데이터 최소 수집 원칙
  - 로컬 처리 vs 서버 전송 의사결정
  - 권한(Permission) 최소화 전략
- **분량**: 20페이지
- **실습**: 위협 모델링 워크숍

---

## 5. Part 3: 구현 (Build)

> 설계를 코드로 — 핵심 탐지 엔진부터 UI까지

### Chapter 10: 프로젝트 셋업과 개발 환경

- **기반 문서**: (구현 공통)
- **핵심 내용**:
  - Android 프로젝트 초기 설정 (Gradle, 모듈 구조)
  - 의존성 관리: Version Catalog
  - 코드 스타일과 린터 설정 (ktlint, detekt)
  - Git 브랜치 전략: Trunk-based Development
  - CI 환경 사전 구성
  - 개발/스테이징/프로덕션 환경 분리
- **분량**: 14페이지
- **실습**: 프로젝트 생성부터 첫 빌드까지

### Chapter 11: Layer 1 — Wi-Fi 네트워크 스캔

- **기반 문서**: `project-plan.md` (2.1절), `27-oui-database.md`
- **핵심 내용**:
  - Android Wi-Fi API 개요 (WifiManager, ConnectivityManager)
  - ARP 테이블 기반 기기 탐지
  - mDNS/SSDP 서비스 디스커버리
  - 포트 스캔: RTSP(554), HTTP(80/8080) 등 카메라 특성 포트
  - MAC OUI 매칭: 카메라 제조사 식별
  - OUI 데이터베이스 구축과 업데이트 전략
  - 탐지율 80~90% 달성 과정과 한계
- **분량**: 22페이지
- **실습**: Wi-Fi 스캔 모듈 구현

### Chapter 12: Layer 2 — 플래시 Retroreflection 렌즈 감지

- **기반 문서**: `project-plan.md` (2.2절)
- **핵심 내용**:
  - Retroreflection 원리: 카메라 렌즈의 빛 반사 특성
  - CameraX API를 이용한 플래시 제어
  - 실시간 프레임 분석: 밝은 점(hotspot) 검출 알고리즘
  - 오탐 필터링: 거울, 금속, 유리 반사와의 구분
  - 온디바이스 ML 적용 가능성
  - 밝은 환경/전원 OFF 카메라에서도 작동하는 이유
  - 정확도 60~70% 달성과 개선 방향
- **분량**: 24페이지
- **실습**: CameraX + 프레임 분석 구현

### Chapter 13: Layer 3 — 자기장(EMF) 감지

- **기반 문서**: `project-plan.md` (2.3절)
- **핵심 내용**:
  - 자기장 센서(Magnetometer) 원리
  - Android SensorManager API
  - 기저 자기장 측정과 이상치 탐지
  - EMF 감지의 높은 오탐률(40~60%)과 대응
  - 캘리브레이션 프로세스
  - 사용자 가이드: 천천히 이동하며 스캔
- **분량**: 16페이지
- **실습**: EMF 센서 모듈 구현

### Chapter 14: 교차 검증 엔진 구현

- **기반 문서**: `project-plan.md` (3절)
- **핵심 내용**:
  - 왜 단일 센서로는 부족한가: 각 Layer의 한계
  - 교차 검증 알고리즘 설계
  - 신뢰도 점수 계산: 가중 평균, 베이지안 접근
  - 위험 등급 판정: 안전/주의/위험
  - 근거 기반 결과 설명 생성
  - 종합 탐지율 70~75% 달성 과정
  - 오탐율 5~10% 이하로 낮추는 튜닝
- **분량**: 20페이지
- **실습**: 교차 검증 엔진 통합 테스트

### Chapter 15: UI/UX 구현

- **기반 문서**: `09-ui-ux-spec.md`
- **핵심 내용**:
  - Jetpack Compose 기반 UI 구현
  - 스캔 화면: 실시간 진행률, 애니메이션
  - 결과 화면: 위험 등급 시각화, 근거 설명 카드
  - 다크 모드 / 라이트 모드
  - 접근성(a11y) 고려 사항
  - Material Design 3 적용
  - 사용자 테스트 피드백 반영 과정
- **분량**: 18페이지
- **실습**: Compose UI 컴포넌트 구현

### Chapter 16: 리포트와 체크리스트 구현

- **기반 문서**: `28-checklist-design.md`
- **핵심 내용**:
  - 수동 체크리스트: 전문가 가이드라인 기반
  - 스캔 결과 리포트 생성
  - PDF 리포트 내보내기
  - 스캔 히스토리 관리
  - 장소별 맞춤 체크리스트 (숙소/화장실/탈의실)
  - 리포트의 법적 효력 고지
- **분량**: 14페이지
- **실습**: 리포트 생성 모듈 구현

---

## 6. Part 4: 품질과 배포 (Ship)

> 완성된 코드를 제품으로 — 테스트, 빌드, 배포

### Chapter 17: 테스트 전략과 실전

- **기반 문서**: `18-test-strategy.md`
- **핵심 내용**:
  - 테스트 피라미드: Unit → Integration → UI → E2E
  - 센서 모킹 전략: 실제 센서 없이 테스트하기
  - JUnit 5 + Mockk 활용
  - Compose UI 테스트
  - 코드 커버리지 목표: 80% 이상
  - 실제 몰래카메라로 테스트하기 (윤리적 테스트 환경 구축)
  - 성능 테스트: 30초 이내 완료 검증
- **분량**: 20페이지
- **실습**: 교차 검증 엔진 단위 테스트 작성

### Chapter 18: CI/CD 파이프라인

- **기반 문서**: `19-cicd-pipeline.md`
- **핵심 내용**:
  - GitHub Actions 기반 CI 구축
  - 빌드 → 린트 → 테스트 → APK 생성 자동화
  - 코드 서명과 키 관리
  - 환경별 빌드 변형 (debug/staging/release)
  - Fastlane 활용 배포 자동화
  - 빌드 시간 최적화
- **분량**: 16페이지
- **실습**: GitHub Actions 워크플로우 작성

### Chapter 19: 릴리스와 Play Store 배포

- **기반 문서**: `20-release-plan.md`
- **핵심 내용**:
  - Google Play Store 등록 절차
  - 앱 메타데이터: 설명, 스크린샷, 프라이버시 정책
  - 단계적 출시(Staged Rollout) 전략
  - 버전 관리와 릴리스 노트
  - Play Store 정책 준수: 권한 사용 정당화
  - 리젝 사유 대응 경험 공유
- **분량**: 16페이지
- **실습**: Play Store 리스팅 체크리스트

### Chapter 20: 모니터링과 운영

- **기반 문서**: `21-monitoring.md`
- **핵심 내용**:
  - 크래시 모니터링: Firebase Crashlytics
  - 성능 모니터링: Firebase Performance
  - 사용자 행동 분석: Firebase Analytics
  - 서버 사이드 모니터링 (API 서버 운영 시)
  - 알림과 대응 프로세스
  - SLA 정의와 인시던트 관리
- **분량**: 16페이지
- **실습**: Crashlytics 대시보드 구성

---

## 7. Part 5: 성장 (Grow)

> 출시 이후 — 사용자 획득, 데이터 기반 개선, 확장

### Chapter 21: 마케팅과 사용자 획득

- **기반 문서**: `22-gtm-strategy.md`
- **핵심 내용**:
  - ASO(App Store Optimization) 전략
  - 콘텐츠 마케팅: 몰래카메라 예방 가이드
  - SNS 마케팅과 인플루언서 협업
  - PR 전략: 언론 보도 접근법
  - 사용자 리뷰 관리
  - 유료 광고: Google Ads, Meta Ads
  - CAC(Customer Acquisition Cost) 최적화
- **분량**: 18페이지
- **실습**: ASO 키워드 리서치

### Chapter 22: 데이터 분석과 개선

- **기반 문서**: `23-analytics-framework.md`
- **핵심 내용**:
  - 핵심 지표(KPI) 정의: DAU, 스캔 완료율, 전환율
  - 이벤트 트래킹 설계
  - A/B 테스트 프레임워크
  - 퍼널 분석: 설치 → 온보딩 → 첫 스캔 → 반복 사용
  - 데이터 기반 의사결정 프로세스
  - 프라이버시 보존 분석 (차등 개인정보보호)
- **분량**: 16페이지
- **실습**: 이벤트 트래킹 계획 수립

### Chapter 23: 접근성과 국제화

- **기반 문서**: `12-accessibility.md`, `13-i18n.md`
- **핵심 내용**:
  - 접근성(Accessibility) 설계 원칙: WCAG 2.1
  - TalkBack 지원, 고대비 모드
  - 국제화(i18n) 아키텍처: strings.xml 관리
  - 지역화(l10n) 전략: 한국어 → 영어 → 일본어
  - RTL 언어 지원 고려
  - 문화적 맥락 차이 반영
- **분량**: 14페이지
- **실습**: 다국어 리소스 구성

### Chapter 24: 파트너십과 확장

- **기반 문서**: `25-partnership-strategy.md`
- **핵심 내용**:
  - B2B 파트너십: 숙박 플랫폼(에어비앤비, 야놀자 등)
  - 보안 업체와의 협업 모델
  - SDK/API 제공 전략
  - 플랫폼 확장: iOS, 웨어러블
  - 기능 확장: AI 기반 탐지, IoT 센서 연동
  - Exit 전략과 장기 비전
- **분량**: 16페이지
- **실습**: 파트너십 제안서 템플릿

---

## 8. 부록

### 부록 A: 에러 처리 가이드

- **기반 문서**: `08-error-handling.md`
- **내용**: 에러 코드 체계, 사용자 메시지 가이드라인, 센서 오류 대응, 네트워크 오류 복구

### 부록 B: 성능 최적화 레퍼런스

- **기반 문서**: `26-performance-optimization.md`
- **내용**: 메모리 최적화, 배터리 소모 최소화, 스캔 속도 튜닝, ProGuard/R8 최적화

### 부록 C: OUI 데이터베이스 구축

- **기반 문서**: `27-oui-database.md`
- **내용**: IEEE OUI 데이터 수집, 카메라 제조사 필터링, 데이터베이스 갱신 자동화, 검색 최적화

### 부록 D: 리스크 매트릭스

- **기반 문서**: `16-risk-matrix.md`
- **내용**: 기술 리스크, 비즈니스 리스크, 법적 리스크, 대응 계획, 리스크 등급 평가

### 부록 E: 법적 준수 가이드

- **기반 문서**: `15-legal-compliance.md`
- **내용**: 개인정보보호법, 통신비밀보호법, GDPR, 앱 권한 사용 정당화, 면책 조항

---

## 9. 챕터 템플릿

각 챕터는 다음 구조를 따른다:

```markdown
# Chapter XX: 챕터 제목

> "한 줄 핵심 메시지"

## 이 챕터에서 배우는 것
- 학습 목표 1
- 학습 목표 2
- 학습 목표 3

## XX.1 도입
- 왜 이 주제가 중요한가 (일상 비유로 시작)
- 실제 SearCam에서 마주한 문제 상황

## XX.2 ~ XX.N 본문
- 개념 설명 (비유 → 기술 설명)
- SearCam 사례 적용
- 코드 예제 (실행 가능한 스니펫)
- 다이어그램/표

## XX.N+1 실습
- 단계별 실습 가이드
- 기대 결과
- 트러블슈팅 가이드

## XX.N+2 정리
- 핵심 요약 (3~5줄)
- 체크리스트: 이 챕터를 마치면 할 수 있는 것
- 다음 챕터 예고

## 참고 자료
- 기반 기획 문서 링크
- 외부 참고 문헌
```

---

## 10. 제작 파이프라인

### 10.1 도구

| 도구 | 용도 |
|------|------|
| Markdown | 원고 작성 포맷 |
| Pandoc | MD → DOCX/PDF 변환 |
| Mermaid | 다이어그램 (아키텍처, 플로우차트) |
| PlantUML | 시퀀스/클래스 다이어그램 |
| draw.io | 복잡한 시스템 다이어그램 |
| Git | 원고 버전 관리 |
| GitHub | 원고 리뷰 (PR 기반) |

### 10.2 빌드 프로세스

```
chapters/*.md
    ↓ concat (build.sh)
combined.md
    ↓ pandoc + metadata.yaml
    ├── output/book.docx  (Word)
    └── output/book.pdf   (PDF, LaTeX 경유)
```

### 10.3 한국어 PDF 생성

```bash
pandoc combined.md \
  --metadata-file=metadata.yaml \
  --pdf-engine=xelatex \
  --template=templates/book.tex \
  -o output/book.pdf
```

- `xelatex`: 한국어 유니코드 지원
- `Noto Sans KR`: 본문 한글 폰트
- `JetBrains Mono`: 코드 블록 폰트

### 10.4 품질 관리

- 맞춤법 검사: 한국어 맞춤법 검사기 (hanspell)
- 코드 검증: 모든 코드 스니펫은 실제 컴파일/실행 확인
- 리뷰: PR 기반 챕터별 리뷰
- 용어 통일: 용어집(glossary) 관리

---

## 11. 기획 문서 → 챕터 매핑

| 기획 문서 | 챕터 |
|-----------|------|
| `project-plan.md` | Ch01, Ch11~Ch14 |
| `01-PRD.md` | Ch03 |
| `02-TRD.md` | Ch05 |
| `03-TDD.md` | Ch07 |
| `04-system-architecture.md` | Ch06 |
| `05-data-flow.md` | Ch06 |
| `06-api-design.md` | Ch08 |
| `07-db-schema.md` | Ch08 |
| `08-error-handling.md` | 부록 A |
| `09-ui-ux-spec.md` | Ch15 |
| `10-user-research.md` | Ch02 |
| `11-competitor-analysis.md` | Ch01 |
| `12-accessibility.md` | Ch23 |
| `13-i18n.md` | Ch23 |
| `14-security-design.md` | Ch09 |
| `15-legal-compliance.md` | 부록 E |
| `16-risk-matrix.md` | 부록 D |
| `17-privacy-impact.md` | Ch09 |
| `18-test-strategy.md` | Ch17 |
| `19-cicd-pipeline.md` | Ch18 |
| `20-release-plan.md` | Ch19 |
| `21-monitoring.md` | Ch20 |
| `22-gtm-strategy.md` | Ch04, Ch21 |
| `23-analytics-framework.md` | Ch22 |
| `24-revenue-model.md` | Ch04 |
| `25-partnership-strategy.md` | Ch24 |
| `26-performance-optimization.md` | 부록 B |
| `27-oui-database.md` | Ch11, 부록 C |
| `28-checklist-design.md` | Ch16 |

> 29개 기획 문서 전부가 최소 1개 이상의 챕터/부록에 매핑된다.

---

## 12. 일정 추정

### 12.1 전체 일정

| 단계 | 기간 | 산출물 |
|------|------|--------|
| 1단계: 기획 | 2주 | 00-book-plan.md 확정, 챕터 아웃라인 |
| 2단계: Part 1~2 초고 | 4주 | Ch01~Ch09 초고 (9챕터) |
| 3단계: Part 3 초고 | 4주 | Ch10~Ch16 초고 (7챕터) |
| 4단계: Part 4~5 초고 | 3주 | Ch17~Ch24 초고 (8챕터) |
| 5단계: 부록 | 1주 | 부록 A~E 초고 |
| 6단계: 1차 리뷰 | 2주 | 전체 리뷰, 피드백 반영 |
| 7단계: 코드 검증 | 2주 | 모든 코드 스니펫 검증 |
| 8단계: 최종 편집 | 2주 | 교정, 용어 통일, 디자인 |
| **합계** | **20주 (약 5개월)** | **400~500페이지** |

### 12.2 분량 추정

| 구분 | 챕터 수 | 예상 페이지 |
|------|---------|------------|
| Part 1: 기획과 설계 | 4 | 69 |
| Part 2: 아키텍처와 설계 | 5 | 94 |
| Part 3: 구현 | 7 | 128 |
| Part 4: 품질과 배포 | 4 | 68 |
| Part 5: 성장 | 4 | 64 |
| 부록 | 5 | 50 |
| 서문/목차/색인 | - | 27 |
| **합계** | **24 + 5** | **~500페이지** |

---

## 13. 기술적 차별점

이 책이 다른 모바일 앱 개발 서적과 다른 점:

1. **실제 상용 앱의 전체 과정**: 이론이 아닌 실전 기록
2. **29개 기획 문서 기반**: 소프트웨어 공학 문서의 실전 사례
3. **센서 프로그래밍**: Wi-Fi, 카메라, 자기장 센서 실전 활용
4. **보안/프라이버시 중심**: 민감한 데이터를 다루는 앱의 설계 원칙
5. **솔직한 한계 기록**: 성공만이 아닌 실패와 타협의 기록
6. **비즈니스 + 기술 통합**: 수익 모델부터 CI/CD까지 한 권에

---

## 14. 파일 구조

```
docs/book/
├── 00-book-plan.md          ← 이 문서
├── SUMMARY.md               ← GitBook 호환 목차
├── README.md                ← 책 소개 (이 책에 대하여)
├── metadata.yaml            ← Pandoc 메타데이터
├── build.sh                 ← 빌드 스크립트
├── Makefile                 ← 빌드 자동화
├── chapters/
│   ├── ch01-problem-discovery.md
│   ├── ch02-user-research.md
│   ├── ch03-product-requirements.md
│   ├── ch04-revenue-model.md
│   ├── ch05-technical-requirements.md
│   ├── ch06-system-architecture.md
│   ├── ch07-technical-design.md
│   ├── ch08-api-database.md
│   ├── ch09-security-privacy.md
│   ├── ch10-project-setup.md
│   ├── ch11-wifi-scan.md
│   ├── ch12-lens-detection.md
│   ├── ch13-emf-detection.md
│   ├── ch14-cross-validation.md
│   ├── ch15-ui-ux.md
│   ├── ch16-report-checklist.md
│   ├── ch17-test-strategy.md
│   ├── ch18-cicd-pipeline.md
│   ├── ch19-release-deploy.md
│   ├── ch20-monitoring.md
│   ├── ch21-marketing.md
│   ├── ch22-analytics.md
│   ├── ch23-accessibility-i18n.md
│   ├── ch24-partnership.md
│   ├── appendix-a-error-handling.md
│   ├── appendix-b-performance.md
│   ├── appendix-c-oui-database.md
│   ├── appendix-d-risk-matrix.md
│   └── appendix-e-legal-compliance.md
└── output/
    ├── book.docx
    └── book.pdf
```
