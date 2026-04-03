# SearCam - 몰래카메라 탐지 앱

## 프로젝트 개요
스마트폰만으로 30초 안에 몰래카메라를 1차 스크리닝하는 Android 앱
- GitHub: https://github.com/saintgo7/saas-searCam

## 기술 스택
- Language: Kotlin
- UI: Jetpack Compose
- Architecture: MVVM + Clean Architecture
- DI: Hilt
- DB: Room
- Async: Coroutines + Flow
- Min SDK: API 26 (Android 8.0)
- Target SDK: API 34

## 프로젝트 구조
```
saas-searCam/
├── CLAUDE.md           # 이 파일
├── DESIGN.md           # 디자인 시스템
├── docs/               # 프로젝트 문서 29건
│   ├── 01-PRD.md
│   ├── 02-TRD.md
│   ├── 03-TDD.md
│   └── ... (04~28)
│   └── book/           # 기술 서적 원고
│       ├── chapters/   # 챕터별 md 파일
│       ├── build.sh
│       └── Makefile
└── app/                # Android 앱 소스 (구현 시)
```

## 문서 참조 가이드
| 질문 | 참조 문서 |
|------|----------|
| 기획/요구사항 | docs/01-PRD.md |
| 기술 요구사항 | docs/02-TRD.md |
| 설계 상세 | docs/03-TDD.md |
| 아키텍처 | docs/04-system-architecture.md |
| 데이터 플로우 | docs/05-data-flow.md |
| API 설계 | docs/06-api-design.md |
| DB 스키마 | docs/07-db-schema.md |
| 에러 처리 | docs/08-error-handling.md |
| UI/UX | docs/09-ui-ux-spec.md |
| 보안 | docs/14-security-design.md |
| 테스트 | docs/18-test-strategy.md |
| CI/CD | docs/19-cicd-pipeline.md |
| 리스크 | docs/16-risk-matrix.md |

## 모델 사용 전략 (CRITICAL)
| 작업 유형 | 모델 | 이유 |
|----------|------|------|
| 계획/설계/아키텍처 리뷰 | **opus** | 복잡한 판단 필요 |
| 코드 구현/테스트 작성 | **sonnet** | 빠른 반복 |
| 탐색/검색/간단한 질문 | **sonnet** | 속도 |
| 보안/법적 검토 | **opus** | 높은 정확도 필요 |
| 책 챕터 작성 | **sonnet** | 대량 생성 |

## 에이전트 전략
```
새 기능:   planner(opus) → tdd-guide(opus) → 구현(sonnet) → code-reviewer(opus)
버그 수정: investigate → fix(sonnet) → verify(sonnet)
문서:     doc-updater(sonnet)
보안:     security-reviewer(opus)
책 작성:  searcam-book 커맨드 (sonnet)
```

## 개발 규칙

### 코딩 스타일
- 불변성(Immutability) 준수: 객체 뮤테이션 금지
- 파일 크기: 800줄 이하
- 함수 크기: 50줄 이하
- 에러 처리: docs/08-error-handling.md 참조
- 에러 코드 체계: E1xxx(센서), E2xxx(네트워크), E3xxx(권한)

### 탐지 레이어 구조
```
Layer 1: Wi-Fi 네트워크 스캔 (가중치 50%)
Layer 2: 렌즈 감지 - Retroreflection + IR (가중치 35%)
Layer 3: 자기장(EMF) 감지 (가중치 15%)
→ 교차 검증 엔진 → 위험도 0~100
```

### Clean Architecture 레이어
```
ui/ (Compose, ViewModel)
  ↓ (단방향)
domain/ (UseCase, Repository 인터페이스, Model)
  ↓ (단방향)
data/ (Repository 구현, Sensor, Room, Network)
```

### Book 작업 규칙
- 코드 변경 시 해당 챕터 md도 함께 업데이트
- docs/book/chapters/chXX-*.md 형식으로 저장
- /searcam-book 커맨드 사용

### 커밋 메시지 형식
```
feat: 새 기능
fix: 버그 수정
refactor: 리팩토링 (기능 변경 없음)
docs: 문서 변경
test: 테스트 추가/수정
chore: 빌드/설정 변경
book: 책 챕터 추가/수정
```

## Design System
Always read DESIGN.md before making any visual or UI decisions.
All font choices, colors, spacing, and aesthetic direction are defined there.
Do not deviate without explicit user approval.
In QA mode, flag any code that doesn't match DESIGN.md.

## 보안 체크리스트 (커밋 전 필수)
- [ ] 하드코딩된 시크릿 없음
- [ ] 사용자 입력 검증 완료
- [ ] 네트워크 스캔은 같은 Wi-Fi 네트워크 내로 제한
- [ ] 카메라 프레임 데이터 메모리에서만 처리 (저장 금지)
- [ ] 권한 최소화 원칙 준수
