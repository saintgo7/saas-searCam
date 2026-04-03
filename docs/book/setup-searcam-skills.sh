#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────
# setup-searcam-skills.sh
#
# SearCam 프로젝트의 Claude Code 스킬/훅/에이전트 환경을 한 번에 설치합니다.
#
# 사용법:
#   ./docs/book/setup-searcam-skills.sh
#   ./docs/book/setup-searcam-skills.sh --dry-run   # 변경 없이 현황만 출력
#   ./docs/book/setup-searcam-skills.sh --check     # 설치 상태 확인만
# ──────────────────────────────────────────────────────────────

set -euo pipefail

# ─── 경로 ──────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BOOK_DIR="${SCRIPT_DIR}"                          # docs/book/
PROJECT_DIR="$(cd "${BOOK_DIR}/../.." && pwd)"    # saas-searCam/
CLAUDE_DIR="${HOME}/.claude"
COMMANDS_DIR="${CLAUDE_DIR}/commands"
HOOKS_DIR="${CLAUDE_DIR}/hooks"
AGENTS_DIR="${CLAUDE_DIR}/agents"
SETTINGS_FILE="${CLAUDE_DIR}/settings.json"

# ─── 색상 ──────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

ok()      { echo -e "  ${GREEN}✔${NC}  $*"; }
warn()    { echo -e "  ${YELLOW}!${NC}  $*"; }
info()    { echo -e "  ${BLUE}i${NC}  $*"; }
skip()    { echo -e "  ${CYAN}-${NC}  $*"; }
heading() { echo -e "\n${BOLD}${BLUE}── $* ──${NC}"; }
error()   { echo -e "  ${RED}✘${NC}  $*" >&2; }

# ─── 플래그 ────────────────────────────────────────────────────
DRY_RUN=false
CHECK_ONLY=false
for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=true ;;
    --check)   CHECK_ONLY=true ;;
  esac
done

# ─── 헬퍼 ──────────────────────────────────────────────────────
do_copy() {
  local src="$1" dst="$2"
  if $DRY_RUN || $CHECK_ONLY; then
    if [[ -f "$dst" ]]; then skip "이미 존재: $(basename "$dst")";
    else warn "없음: $(basename "$dst")"; fi
    return
  fi
  mkdir -p "$(dirname "$dst")"
  cp "$src" "$dst"
  ok "복사: $(basename "$src") → $dst"
}

do_chmod() {
  local file="$1"
  if $DRY_RUN || $CHECK_ONLY; then return; fi
  chmod +x "$file" 2>/dev/null || true
}

# ──────────────────────────────────────────────────────────────
# 0. 환경 확인
# ──────────────────────────────────────────────────────────────
heading "환경 확인"

command -v pandoc >/dev/null 2>&1 && ok "pandoc: $(pandoc --version | head -1)" || warn "pandoc 미설치 (make docx/pdf 사용 불가) — brew install pandoc"
command -v xelatex >/dev/null 2>&1 && ok "xelatex: $(xelatex --version 2>&1 | head -1)" || warn "xelatex 미설치 (PDF 생성 불가) — brew install --cask mactex"
command -v adb >/dev/null 2>&1 && ok "adb: 설치됨" || warn "adb 미설치 (에뮬레이터 캡처 불가) — Android SDK PATH 설정 필요"
command -v jq >/dev/null 2>&1 && ok "jq: 설치됨" || { error "jq 필수 — brew install jq"; exit 1; }
command -v convert >/dev/null 2>&1 && ok "ImageMagick: 설치됨" || warn "ImageMagick 미설치 (이미지 최적화 불가) — brew install imagemagick"

if $CHECK_ONLY || $DRY_RUN; then
  echo ""
  [[ "$DRY_RUN" == "true" ]] && info "DRY-RUN 모드 — 실제 변경 없음"
fi

# ──────────────────────────────────────────────────────────────
# 1. 디렉토리 구조
# ──────────────────────────────────────────────────────────────
heading "디렉토리 구조"

DIRS=(
  "${BOOK_DIR}/chapters"
  "${BOOK_DIR}/assets/images/screenshots"
  "${BOOK_DIR}/assets/images/diagrams"
  "${BOOK_DIR}/assets/images/ui-mockups"
  "${BOOK_DIR}/output"
  "${BOOK_DIR}/templates"
  "${COMMANDS_DIR}"
  "${HOOKS_DIR}"
  "${AGENTS_DIR}"
)

for d in "${DIRS[@]}"; do
  if [[ -d "$d" ]]; then
    skip "존재: $d"
  else
    if ! $DRY_RUN && ! $CHECK_ONLY; then
      mkdir -p "$d"
      ok "생성: $d"
    else
      warn "없음: $d"
    fi
  fi
done

# ──────────────────────────────────────────────────────────────
# 2. 슬래시 커맨드 (스킬)
# ──────────────────────────────────────────────────────────────
heading "슬래시 커맨드"

SKILL_FILES=(
  "searcam-dev.md"
  "searcam-book.md"
  "searcam-scan.md"
  "searcam-screenshot.md"
)

for skill in "${SKILL_FILES[@]}"; do
  src="${BOOK_DIR}/../../.claude/commands/${skill}"
  # 실제로는 ~/.claude/commands/ 에 직접 있으므로 이미 설치됨 확인
  dst="${COMMANDS_DIR}/${skill}"
  if [[ -f "$dst" ]]; then
    ok "설치됨: /${skill%.md}"
  else
    warn "없음: /${skill%.md} — ${dst}"
    info "  수동 설치: cp <source>/${skill} ${dst}"
  fi
done

# ──────────────────────────────────────────────────────────────
# 3. 빌드 스크립트 실행 권한
# ──────────────────────────────────────────────────────────────
heading "빌드 스크립트"

BUILD_SCRIPTS=(
  "${BOOK_DIR}/build.sh"
  "${BOOK_DIR}/capture.sh"
)

for script in "${BUILD_SCRIPTS[@]}"; do
  if [[ -f "$script" ]]; then
    if [[ -x "$script" ]]; then
      ok "실행 가능: $(basename "$script")"
    else
      if ! $DRY_RUN && ! $CHECK_ONLY; then
        chmod +x "$script"
        ok "실행 권한 부여: $(basename "$script")"
      else
        warn "실행 권한 없음: $(basename "$script")"
      fi
    fi
  else
    warn "없음: $(basename "$script")"
  fi
done

# ──────────────────────────────────────────────────────────────
# 4. Git post-commit 훅
# ──────────────────────────────────────────────────────────────
heading "Git post-commit 훅"

GIT_HOOKS_DIR="${PROJECT_DIR}/.git/hooks"
POST_COMMIT="${GIT_HOOKS_DIR}/post-commit"

if [[ -f "$POST_COMMIT" ]]; then
  ok "설치됨: .git/hooks/post-commit"
  if grep -q "searcam-book" "$POST_COMMIT" 2>/dev/null; then
    ok "내용 확인: SearCam 훅 포함"
  else
    warn "내용 불일치: SearCam 훅 내용 없음"
  fi
else
  if ! $DRY_RUN && ! $CHECK_ONLY; then
    cat > "$POST_COMMIT" << 'HOOKEOF'
#!/bin/bash
# SearCam post-commit hook

CODE_CHANGED=$(git diff --name-only HEAD~1 HEAD 2>/dev/null | grep -E '\.(kt|xml|gradle\.kts)$' | head -1)
BOOK_CHANGED=$(git diff --name-only HEAD~1 HEAD 2>/dev/null | grep 'docs/book/chapters/' | head -1)
IMG_CHANGED=$(git diff --name-only HEAD~1 HEAD 2>/dev/null | grep 'docs/book/assets/images/' | head -1)

if [ -n "$CODE_CHANGED" ] && [ -z "$BOOK_CHANGED" ]; then
    echo ""
    echo "[BOOK]  코드 변경 감지 → book 챕터 업데이트 권장"
    echo "        변경: $CODE_CHANGED"
    echo "        실행: /searcam-book [챕터명]"
fi

if [ -n "$CODE_CHANGED" ] && [ -z "$IMG_CHANGED" ]; then
    echo "[SHOT]  스크린샷 추가 권장"
    echo "        실행: docs/book/capture.sh <챕터> <설명>"
    echo "        또는: /searcam-screenshot [챕터에서 무엇을 캡처할지]"
    echo ""
fi
HOOKEOF
    chmod +x "$POST_COMMIT"
    ok "설치 완료: .git/hooks/post-commit"
  else
    warn "없음: .git/hooks/post-commit"
  fi
fi

# ──────────────────────────────────────────────────────────────
# 5. Claude Code PostToolUse 훅 확인
# ──────────────────────────────────────────────────────────────
heading "Claude Code PostToolUse 훅 (자동 책 재빌드)"

if [[ -f "$SETTINGS_FILE" ]]; then
  if jq -e '.hooks.PostToolUse[] | select(.hooks[].command | contains("make all"))' "$SETTINGS_FILE" >/dev/null 2>&1; then
    ok "설치됨: PostToolUse → make all (챕터/이미지 변경 시 자동 빌드)"
  else
    warn "없음: PostToolUse make all 훅"
    info "  수동 추가 필요: ~/.claude/settings.json 의 hooks.PostToolUse 에 아래 블록 추가"
    cat << 'HOOKJSON'
    {
      "matcher": "Edit|Write",
      "hooks": [{
        "type": "command",
        "command": "jq -r '.tool_input.file_path // \"\"' | { read -r f; echo \"$f\" | grep -qE 'docs/book/(chapters/.*\\.md|assets/images/)' && cd /Users/saint/01_DEV/saas-searCam/docs/book && make all 2>&1 | tail -10; } 2>/dev/null || true",
        "timeout": 120000
      }]
    }
HOOKJSON
  fi
else
  warn "없음: ${SETTINGS_FILE}"
fi

# ──────────────────────────────────────────────────────────────
# 6. 에이전트 확인
# ──────────────────────────────────────────────────────────────
heading "에이전트"

REQUIRED_AGENTS=(
  "planner.md"
  "architect.md"
  "tdd-guide.md"
  "code-reviewer.md"
  "security-reviewer.md"
  "doc-updater.md"
)

for agent in "${REQUIRED_AGENTS[@]}"; do
  if [[ -f "${AGENTS_DIR}/${agent}" ]]; then
    ok "설치됨: ${agent%.md}"
  else
    warn "없음: ${agent%.md} — ${AGENTS_DIR}/${agent}"
  fi
done

# ──────────────────────────────────────────────────────────────
# 7. 현황 요약
# ──────────────────────────────────────────────────────────────
heading "현황 요약"

CHAPTERS_COUNT=$(ls "${BOOK_DIR}/chapters/"*.md 2>/dev/null | wc -l | tr -d ' ')
SCREENSHOTS_COUNT=$(find "${BOOK_DIR}/assets/images/screenshots" -name "*.png" 2>/dev/null | wc -l | tr -d ' ')
OUTPUT_COUNT=$(ls "${BOOK_DIR}/output/"*.{md,docx,pdf} 2>/dev/null | wc -l | tr -d ' ')

info "챕터 작성: ${CHAPTERS_COUNT}/29"
info "스크린샷: ${SCREENSHOTS_COUNT}장"
info "빌드 결과물: ${OUTPUT_COUNT}개 (output/)"

if [[ -f "${BOOK_DIR}/output/book.pdf" ]]; then
  PDF_SIZE=$(du -sh "${BOOK_DIR}/output/book.pdf" | cut -f1)
  ok "book.pdf: ${PDF_SIZE}"
fi
if [[ -f "${BOOK_DIR}/output/book.docx" ]]; then
  DOCX_SIZE=$(du -sh "${BOOK_DIR}/output/book.docx" | cut -f1)
  ok "book.docx: ${DOCX_SIZE}"
fi
if [[ -f "${BOOK_DIR}/output/searcam-skills-guide.docx" ]]; then
  GUIDE_SIZE=$(du -sh "${BOOK_DIR}/output/searcam-skills-guide.docx" | cut -f1)
  ok "searcam-skills-guide.docx: ${GUIDE_SIZE}"
fi

# ──────────────────────────────────────────────────────────────
# 8. 다음 단계 안내
# ──────────────────────────────────────────────────────────────
heading "다음 단계"

echo ""
echo "  슬래시 커맨드:"
echo "    /searcam-dev [기능]      개발 전체 사이클 (계획→구현→리뷰→책)"
echo "    /searcam-book [Ch번호]   챕터 작성/업데이트"
echo "    /searcam-scan            프로젝트 전체 현황"
echo "    /searcam-screenshot      화면 캡처 → 챕터 삽입"
echo ""
echo "  터미널:"
echo "    make all                 DOCX + PDF 빌드"
echo "    make count               분량 통계"
echo "    ./capture.sh ch11 '설명' 스크린샷 캡처"
echo ""
echo "  가이드 문서:"
echo "    ${BOOK_DIR}/output/searcam-skills-guide.docx"
echo ""

if $CHECK_ONLY; then
  echo -e "${CYAN}[CHECK MODE]${NC} 실제 변경 없음. 설치하려면 --check 없이 실행하세요."
elif $DRY_RUN; then
  echo -e "${CYAN}[DRY-RUN MODE]${NC} 실제 변경 없음. 설치하려면 --dry-run 없이 실행하세요."
else
  echo -e "${GREEN}설치/확인 완료.${NC}"
fi
