---
title: "SearCam 스킬 사용 가이드"
subtitle: "Claude Code 슬래시 커맨드 & 자동화 도구 완전 매뉴얼"
author: "SearCam 개발팀"
date: "2026-04-04"
lang: ko
mainfont: "Apple SD Gothic Neo"
sansfont: "Apple SD Gothic Neo"
monofont: "Menlo"
fontsize: 11pt
geometry: margin=25mm
linestretch: 1.5
toc: true
toc-depth: 3
numbersections: true
colorlinks: true
linkcolor: blue
urlcolor: blue
---

\newpage

# 개요

SearCam 프로젝트는 Claude Code 슬래시 커맨드(스킬)와 자동화 도구를 통해 **개발 → 문서화 → 책 출판**의 전 과정을 자동화합니다.

## 전체 구성

| 도구 | 종류 | 역할 |
|------|------|------|
| `/searcam-dev` | 슬래시 커맨드 | 개발 전체 사이클 자동화 |
| `/searcam-book` | 슬래시 커맨드 | 기술 서적 챕터 작성/업데이트 |
| `/searcam-scan` | 슬래시 커맨드 | 프로젝트 현황 빠른 스캔 |
| `/searcam-screenshot` | 슬래시 커맨드 | 개발 화면 캡처 → 챕터 삽입 |
| `capture.sh` | 셸 스크립트 | 스크린샷 직접 캡처 |
| `build.sh` / `Makefile` | 빌드 스크립트 | MD → DOCX → PDF 변환 |
| PostToolUse Hook | Claude Code 훅 | 챕터 수정 시 책 자동 재빌드 |

## 파일 위치

```
~/.claude/commands/
├── searcam-dev.md          # /searcam-dev 스킬 정의
├── searcam-book.md         # /searcam-book 스킬 정의
├── searcam-scan.md         # /searcam-scan 스킬 정의
└── searcam-screenshot.md   # /searcam-screenshot 스킬 정의

/Users/saint/01_DEV/saas-searCam/docs/book/
├── chapters/               # 챕터 md 파일 (ch01 ~ ch24 + appendix)
├── assets/images/          # 이미지 자산
│   ├── screenshots/        # 자동 캡처 이미지 (chXX별 분류)
│   ├── diagrams/           # 아키텍처 다이어그램
│   └── ui-mockups/         # UI 목업
├── output/                 # 빌드 결과물
│   ├── combined.md         # 챕터 통합본
│   ├── book.docx           # Word 문서
│   └── book.pdf            # PDF
├── build.sh                # 빌드 스크립트
├── Makefile                # make 빌드 규칙
├── capture.sh              # 스크린샷 캡처 도구
└── metadata.yaml           # 책 메타데이터 (제목/저자/폰트)
```

\newpage

# /searcam-dev — 개발 워크플로우 자동화

## 개요

기능 요청 한 줄로 **계획 → 설계 → TDD → 구현 → 코드리뷰 → 보안검토 → 문서 → 책 업데이트** 전 과정을 멀티 에이전트로 자동화합니다.

**사용 모델**: Opus (계획/설계/리뷰) + Sonnet (구현/테스트/문서)

## 사용법

Claude Code 채팅창에 입력:

```
/searcam-dev [구현할 기능 설명]
```

### 예시

```
/searcam-dev Wi-Fi 스캔 기능 구현 — Layer 1 탐지 로직
/searcam-dev 렌즈 감지 Retroreflection 알고리즘 추가
/searcam-dev HomeScreen UI Compose로 구현
/searcam-dev 교차 검증 엔진 — 세 레이어 가중치 합산
```

## 에이전트 파이프라인

| 단계 | 에이전트 | 모델 | 수행 내용 |
|------|---------|------|-----------|
| 1. 계획 | planner | **Opus** | 구현 파일 목록, 의존성 분석 |
| 2. 설계 | architect | **Opus** | 인터페이스 설계, 아키텍처 결정 |
| 3. TDD | tdd-guide | **Opus** | 테스트 케이스 설계 (RED 단계) |
| 4. 구현 | implementation | **Sonnet** | 실제 코드 작성 (GREEN 단계) |
| 5. 테스트 | test-writer | **Sonnet** | 단위/통합 테스트 작성 |
| 6. 리뷰 | code-reviewer | **Opus** | 코드 품질/패턴 검토 |
| 7. 보안 | security-reviewer | **Opus** | 보안 취약점 검토 |
| 8. 문서 | doc-updater | **Sonnet** | docs/ 문서 업데이트 |
| 9. 책 | book-writer | **Sonnet** | 해당 챕터 자동 업데이트 |

## 중요 규칙

- **Phase 1(계획) 완료 후 반드시 사용자 승인** — 승인 전 코드 작성 금지
- CRITICAL/HIGH 코드리뷰 이슈는 즉시 수정 후 재리뷰
- 커버리지 목표: **80% 이상**

## 탐지 레이어별 구현 주의사항

### Layer 1: Wi-Fi 스캔 (가중치 50%)

```
- 동일 Wi-Fi 네트워크 내로 스캔 제한
- /proc/net/arp 권한 확인 필수
- 결과는 도메인 모델로 변환 후 반환
```

### Layer 2: 렌즈 감지 (가중치 35%)

```
- 카메라 프레임: 30fps, 720p 다운스케일
- Retroreflection + IR 이중 검출
- 카메라 프레임 데이터는 메모리에서만 처리 (저장 금지)
```

### Layer 3: EMF 감지 (가중치 15%)

```
- 자기장 센서: 20Hz 폴링
- 이동 평균 윈도우 = 10
- 배경 노이즈 기준선 설정 필요
```

## 커밋 전 체크리스트

```
[ ] 모든 테스트 통과 (build + test 실행 결과 첨부)
[ ] 커버리지 80% 이상
[ ] 보안 체크리스트 통과
[ ] 관련 book 챕터 업데이트됨
[ ] 하드코딩된 시크릿 없음
```

\newpage

# /searcam-book — 챕터 작성/업데이트

## 개요

SearCam 기술 서적의 특정 챕터를 새로 작성하거나, 코드 변경 후 챕터를 동기화합니다. **Sonnet 모델**로 빠르게 대량 생성합니다.

## 사용법

```
/searcam-book [챕터 번호 또는 주제] [추가 지시사항]
```

### 예시

```
/searcam-book Ch11 Wi-Fi 스캔 구현 챕터 작성
/searcam-book Ch15 UI 구현 챕터 업데이트 — HomeScreen 추가됨
/searcam-book Ch06 아키텍처 챕터에 시스템 다이어그램 추가
/searcam-book 전체 챕터 목록 현황 확인
```

## 챕터 매핑표

| 챕터 | 파일명 | 참조 문서 |
|------|--------|-----------|
| Ch01 | ch01-problem-discovery.md | project-plan.md |
| Ch02 | ch02-user-research.md | 10-user-research.md |
| Ch03 | ch03-product-requirements.md | 01-PRD.md |
| Ch04 | ch04-revenue-model.md | 24-revenue-model.md |
| Ch05 | ch05-technical-requirements.md | 02-TRD.md |
| Ch06 | ch06-system-architecture.md | 04-system-architecture.md |
| Ch07 | ch07-technical-design.md | 03-TDD.md |
| Ch08 | ch08-api-database.md | 06-api-design.md, 07-db-schema.md |
| Ch09 | ch09-security-privacy.md | 14-security-design.md |
| Ch10 | ch10-project-setup.md | 02-TRD.md (기술 스택) |
| Ch11 | ch11-wifi-scan.md | 03-TDD.md (Layer 1) |
| Ch12 | ch12-lens-detection.md | 03-TDD.md (Layer 2) |
| Ch13 | ch13-emf-detection.md | 03-TDD.md (Layer 3) |
| Ch14 | ch14-cross-validation.md | 03-TDD.md (교차 검증) |
| Ch15 | ch15-ui-ux.md | 09-ui-ux-spec.md |
| Ch16 | ch16-report-checklist.md | 28-checklist-design.md |
| Ch17 | ch17-test-strategy.md | 18-test-strategy.md |
| Ch18 | ch18-cicd-pipeline.md | 19-cicd-pipeline.md |
| Ch19 | ch19-release-deploy.md | 20-release-plan.md |
| Ch20 | ch20-monitoring.md | 21-monitoring.md |
| Ch21 | ch21-marketing.md | 22-gtm-strategy.md |
| Ch22 | ch22-analytics.md | 23-analytics-framework.md |
| Ch23 | ch23-accessibility-i18n.md | 12-accessibility.md |
| Ch24 | ch24-partnership.md | 25-partnership-strategy.md |
| App-A | appendix-a-error-handling.md | 08-error-handling.md |
| App-B | appendix-b-performance.md | — |
| App-C | appendix-c-oui-database.md | — |
| App-D | appendix-d-risk-matrix.md | 16-risk-matrix.md |
| App-E | appendix-e-legal-compliance.md | 15-legal-compliance.md |

## 챕터 저장 위치

```
/Users/saint/01_DEV/saas-searCam/docs/book/chapters/chXX-파일명.md
```

## 챕터 작성 스타일

- **언어**: 한국어, 독자에게 말하는 구어체 존댓말
- **구조**: 비유 → 기술 설명 순서
- **코드**: Kotlin 코드 블록에 한국어 주석
- **분량**: 챕터당 5,000~8,000자 (30~50페이지)

## 챕터 표준 구조

```markdown
# Ch[번호]: [제목]

> **이 장에서 배울 것**: [한 줄 요약]

## 도입
[왜 이 장이 중요한가]

## [주요 개념]
[비유 → 기술 설명]

### SearCam 적용 사례
[실제 코드 예시]

## 실습 과제

## 핵심 정리

## 다음 장 예고
```

\newpage

# /searcam-scan — 프로젝트 현황 스캔

## 개요

프로젝트의 구현 진도, 문서 현황, 책 챕터 상태를 한눈에 파악합니다. **Sonnet 모델**로 빠르게 실행됩니다.

## 사용법

```
/searcam-scan              # 전체 현황 보고
/searcam-scan book         # 책 챕터 현황만
/searcam-scan impl         # 구현 현황만
```

## 스캔 항목

| 항목 | 내용 |
|------|------|
| Git 상태 | 최근 커밋 5개, 브랜치, 변경 파일 |
| 구현 현황 | app/ 디렉토리 구조 (미시작/진행/완료) |
| 문서 현황 | docs/ 파일 목록과 최종 수정일 |
| 책 현황 | 존재하는 챕터 목록 (29개 중 완료 수) |
| TODO/FIXME | 코드 내 미완성 항목 수 |
| 테스트 현황 | 테스트 파일 수, 마지막 실행 결과 |

## 출력 예시

```
## SearCam 프로젝트 현황 보고
날짜: 2026-04-04

### 구현 진도
| 기능                | 상태       |
|---------------------|------------|
| Layer 1: Wi-Fi 스캔 | 진행 중    |
| Layer 2: 렌즈 감지  | 미시작     |
| Layer 3: EMF 감지   | 미시작     |
| 교차 검증 엔진      | 미시작     |
| UI (Compose)        | 미시작     |

### 책 챕터 진도
완료: 1/29챕터 (Ch01)

### 다음 권장 작업
1. Ch11 Wi-Fi 스캔 챕터 작성
2. Layer 1 구현 시작
```

\newpage

# /searcam-screenshot — 개발 화면 캡처

## 개요

개발 중인 화면을 캡처하고 자동으로 해당 챕터에 마크다운 이미지로 삽입합니다.

## 사용법

```
/searcam-screenshot [챕터] [캡처 내용] [방식]
```

### 예시

```
/searcam-screenshot ch11에서 Wi-Fi 스캔 결과 캡처해줘
/searcam-screenshot 에뮬레이터 홈화면 캡처해서 ch15에 넣어줘
/searcam-screenshot 지금 터미널 결과 캡처해서 ch17 테스트 챕터에 추가해줘
/searcam-screenshot 캡처 목록 보여줘
```

## 캡처 방식

| 방식 | 설명 | 언제 사용 |
|------|------|-----------|
| 전체 화면 | 현재 모니터 전체 | 코드 에디터, 전체 화면 결과 |
| 영역 선택 | 드래그로 선택 | 특정 UI 컴포넌트 |
| 창 캡처 | 클릭한 창만 | 특정 앱 창 |
| Android 에뮬레이터 | adb screencap | 에뮬레이터/실제 기기 화면 |

## 이미지 저장 위치

```
docs/book/assets/images/
├── screenshots/chXX/       # 자동 캡처 (YYYY-MM-DD_HH-MM-SS_설명.png)
├── diagrams/               # 아키텍처 다이어그램 (수동)
└── ui-mockups/             # UI 목업/와이어프레임 (수동)
```

## 챕터 삽입 마크다운 형식

```markdown
![Wi-Fi 스캔 결과 화면](../assets/images/screenshots/ch11/2026-04-04_wifi-scan-result.png)
> *그림 11.1: Quick Scan 완료 후 결과 화면 — Hikvision 카메라 1대 탐지됨*
```

## 이미지 캡션 컨벤션

```
그림 [챕터번호].[순서]: [설명] — [추가 맥락]
예: 그림 11.3: ARP 테이블 조회 결과 — 같은 네트워크의 7개 기기 발견
```

\newpage

# capture.sh — 스크린샷 직접 캡처

## 개요

`/searcam-screenshot` 스킬 없이 터미널에서 직접 실행하는 스크린샷 도구입니다.

## 사용법

```bash
cd /Users/saint/01_DEV/saas-searCam/docs/book

# 기본 형식
./capture.sh <챕터> <설명> [옵션]
```

## 옵션

| 옵션 | 동작 | 사용 예 |
|------|------|---------|
| (없음) | 전체 화면 즉시 캡처 | `./capture.sh ch11 "wifi-scan-result"` |
| `--region` | 드래그로 영역 선택 | `./capture.sh ch11 "scan-button" --region` |
| `--window` | 창 클릭으로 선택 | `./capture.sh ch12 "lens-code" --window` |
| `--emulator` | Android 에뮬레이터 | `./capture.sh ch15 "home-screen" --emulator` |

## 특수 명령

```bash
# 5초 카운트다운 후 자동 캡처 (화면 준비 시간)
./capture.sh auto ch15 "compose-preview"

# 캡처된 이미지 전체 목록
./capture.sh list
```

## 실행 흐름

1. 챕터 디렉토리 자동 생성: `assets/images/screenshots/chXX/`
2. 타임스탬프 파일명 생성: `YYYY-MM-DD_HH-MM-SS_설명.png`
3. 캡처 실행 (선택 모드에 따라)
4. ImageMagick 설치 시 자동 최적화 (`-quality 90`)
5. 캡처 로그 기록: `assets/images/capture-log.md`
6. 마크다운 스니펫 출력 (바로 복사해서 챕터에 붙여넣기)
7. 챕터 파일 자동 삽입 여부 묻기 (y/N)

## 출력 예시

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
챕터에 삽입할 마크다운:

![wifi-scan-result](../assets/images/screenshots/ch11/2026-04-04_10-30-00_wifi-scan-result.png)

또는 캡션 포함:

![wifi-scan-result](../assets/images/screenshots/ch11/2026-04-04_10-30-00_wifi-scan-result.png)
> *그림 X.X: wifi-scan-result*
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

## 전제 조건

| 기능 | 필요 도구 | 확인 방법 |
|------|-----------|-----------|
| macOS 전체/영역/창 캡처 | `screencapture` (macOS 기본) | 자동 사용 |
| Linux 캡처 | `scrot` | `sudo apt install scrot` |
| Android 에뮬레이터 | `adb` | Android SDK PATH 설정 |
| 이미지 최적화 | `ImageMagick` | `brew install imagemagick` |

\newpage

# build.sh / Makefile — 책 빌드

## 개요

챕터 MD 파일들을 합쳐 **DOCX(Word)** 와 **PDF** 를 생성합니다.
PostToolUse Hook에 의해 자동 실행되지만, 수동으로도 사용할 수 있습니다.

## 전제 조건

```bash
# pandoc 설치 확인
pandoc --version

# xelatex 설치 확인 (PDF 생성용)
xelatex --version

# 없으면 설치
brew install pandoc
brew install --cask mactex  # 또는 basictex
```

## make 명령어

```bash
cd /Users/saint/01_DEV/saas-searCam/docs/book
```

| 명령어 | 동작 |
|--------|------|
| `make` | DOCX + PDF 모두 생성 |
| `make md` | 챕터 병합만 (combined.md 생성) |
| `make docx` | Word 문서만 생성 |
| `make pdf` | PDF만 생성 (xelatex 필요) |
| `make clean` | output/ 디렉토리 삭제 |
| `make check` | pandoc/xelatex 설치 확인 |
| `make count` | 분량 통계 (단어/줄/페이지 수) |

## build.sh 명령어

```bash
./build.sh [md|docx|pdf|all|clean]
```

| 인수 | 동작 |
|------|------|
| `md` | 챕터 병합 → combined.md |
| `docx` | DOCX 생성 |
| `pdf` | PDF 생성 |
| `all` | DOCX + PDF |
| `clean` | output/ 삭제 |
| (없음) | 기본값: all |

## 빌드 결과물

```
docs/book/output/
├── combined.md    # 모든 챕터 통합 단일 파일
├── book.docx      # Microsoft Word 형식
└── book.pdf       # PDF (Apple SD Gothic Neo 폰트)
```

## 폰트 설정

```yaml
# metadata.yaml
mainfont: "Apple SD Gothic Neo"   # 본문 (macOS 기본 내장)
sansfont: "Apple SD Gothic Neo"   # 제목
monofont: "Menlo"                 # 코드 블록 (macOS 기본 내장)
```

> **참고**: emoji 문자 (✅, ❌, 🔴)는 폰트 미지원 경고가 출력되지만 PDF 생성에는 영향 없음.

## 챕터 순서

```
ch01 ~ ch24 → appendix-a ~ appendix-e
(총 29개 챕터, 순서는 build.sh CHAPTER_FILES 배열로 관리)
```

\newpage

# PostToolUse Hook — 자동 책 재빌드

## 개요

Claude Code가 파일을 편집/저장할 때 자동으로 책을 재빌드하는 훅입니다.

- **설정 위치**: `~/.claude/settings.json`
- **이벤트**: `PostToolUse` (Edit 또는 Write 도구 사용 후)
- **트리거 조건**: 수정된 파일이 다음 경로에 해당할 때
  - `docs/book/chapters/*.md` (챕터 파일)
  - `docs/book/assets/images/**` (이미지 파일)

## 동작 방식

```
챕터 파일 편집 감지
       ↓
파일 경로 패턴 매칭
       ↓ (매칭됨)
make all 실행
       ↓
combined.md + book.docx + book.pdf 재생성
```

## 설정 내용 (settings.json)

```json
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Edit|Write",
        "hooks": [
          {
            "type": "command",
            "command": "jq -r '.tool_input.file_path // \"\"' | { read -r f; echo \"$f\" | grep -qE 'docs/book/(chapters/.*\\.md|assets/images/)' && cd /Users/saint/01_DEV/saas-searCam/docs/book && make all 2>&1 | tail -10; } 2>/dev/null || true",
            "timeout": 120000
          }
        ]
      }
    ]
  }
}
```

## 타임아웃

- **120초(2분)**: PDF 빌드(xelatex)가 오래 걸릴 수 있으므로 충분한 여유 시간 설정
- 빌드 결과의 마지막 10줄이 Claude Code 컨텍스트에 표시됨

## 비활성화 방법

임시로 비활성화하려면:

1. `/hooks` 메뉴에서 해당 훅 비활성화
2. 또는 `settings.json`에서 해당 블록 삭제

\newpage

# 전체 워크플로우 예시

## 시나리오: Wi-Fi 스캔 기능 구현부터 책 챕터까지

### 1단계: 프로젝트 현황 파악

```
/searcam-scan
```

현재 구현 진도, 미작성 챕터 확인

---

### 2단계: 개발 시작

```
/searcam-dev Wi-Fi 스캔 기능 구현 — Layer 1 탐지 로직
             ARP 테이블 파싱 + OUI 데이터베이스 조회
```

자동으로: 계획 → 설계 → TDD → 구현 → 리뷰 → 보안검토 진행

> 계획 단계에서 반드시 승인 후 진행

---

### 3단계: 에뮬레이터 화면 캡처

구현 완료 후 에뮬레이터에서 테스트 실행 상태 캡처:

```
/searcam-screenshot 에뮬레이터에서 Wi-Fi 스캔 결과 화면 캡처해서 ch11에 넣어줘
```

또는 터미널 직접:

```bash
./capture.sh ch11 "wifi-scan-result" --emulator
```

---

### 4단계: 챕터 작성

```
/searcam-book Ch11 Wi-Fi 스캔 구현 챕터 작성
             — WifiScanUseCase, ArpTableParser 코드 포함
```

챕터 파일 저장 시 **PostToolUse Hook이 자동으로 make all 실행** → 책 3종 재빌드

---

### 5단계: 결과 확인

```bash
# 분량 통계 확인
make count

# 빌드 결과물 확인
ls -la output/
# → combined.md, book.docx, book.pdf
```

---

### 6단계: 커밋

```bash
git add -A
git commit -m "feat: Wi-Fi 스캔 Layer 1 구현

- WifiScanUseCase: ARP 테이블 파싱
- OUI 데이터베이스 조회 (Hikvision, Dahua 등)
- book: Ch11 Wi-Fi 스캔 챕터 추가"
```

\newpage

# 빠른 참조 카드

## 슬래시 커맨드

```
/searcam-dev [기능 설명]       개발 전체 사이클 자동화
/searcam-book [Ch번호] [설명]  챕터 작성/업데이트
/searcam-scan                  전체 프로젝트 현황
/searcam-scan book             챕터 현황만
/searcam-scan impl             구현 현황만
/searcam-screenshot [지시]     화면 캡처 → 챕터 삽입
```

## 터미널 명령어

```bash
# 스크린샷 캡처
./capture.sh ch11 "설명"               # 전체 화면
./capture.sh ch11 "설명" --region      # 영역 선택
./capture.sh ch11 "설명" --window      # 창 선택
./capture.sh ch11 "설명" --emulator    # Android 에뮬레이터
./capture.sh auto ch11 "설명"          # 5초 후 자동 캡처
./capture.sh list                       # 캡처 목록

# 빌드
make all          # DOCX + PDF 생성
make md           # 챕터 병합만
make docx         # Word만
make pdf          # PDF만
make clean        # 출력 삭제
make check        # 의존성 확인
make count        # 분량 통계
```

## 주요 경로

```
~/.claude/commands/                    슬래시 커맨드 정의
~/.claude/settings.json               훅 설정
docs/book/chapters/                   챕터 MD 파일
docs/book/assets/images/screenshots/  캡처 이미지
docs/book/output/                     빌드 결과물
docs/book/metadata.yaml               책 메타데이터
```

## 모델 사용 전략

| 작업 유형 | 모델 | 이유 |
|----------|------|------|
| 계획/설계/아키텍처 리뷰 | Opus | 복잡한 판단 필요 |
| 코드 구현/테스트 작성 | Sonnet | 빠른 반복 |
| 챕터 작성/문서화 | Sonnet | 대량 생성 |
| 보안/법적 검토 | Opus | 높은 정확도 필요 |

## 챕터 파일명 규칙

```
chXX-파일명.md               (ch01 ~ ch24)
appendix-a-파일명.md         (appendix-a ~ appendix-e)
```

## 이미지 파일명 규칙

```
screenshots/chXX/YYYY-MM-DD_HH-MM-SS_설명.png
diagrams/chXX_설명.png
ui-mockups/chXX_화면명.png
```
