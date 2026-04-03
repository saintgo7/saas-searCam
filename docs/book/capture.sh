#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────
# capture.sh — SearCam Book 스크린샷 자동 캡처 & 챕터 삽입
#
# 사용법:
#   ./capture.sh <챕터> <설명> [옵션]
#
# 예시:
#   ./capture.sh ch11 "wifi-scan-result"          # 전체 화면 캡처
#   ./capture.sh ch11 "emulator-home" --emulator  # Android 에뮬레이터
#   ./capture.sh ch11 "scan-button" --region      # 영역 선택
#   ./capture.sh ch15 "home-screen" --window      # 활성 창만
#   ./capture.sh list                              # 전체 캡처 목록
# ──────────────────────────────────────────────────────────────

set -euo pipefail

BOOK_DIR="$(cd "$(dirname "$0")" && pwd)"
IMAGES_DIR="${BOOK_DIR}/assets/images/screenshots"
CHAPTERS_DIR="${BOOK_DIR}/chapters"
LOG_FILE="${BOOK_DIR}/assets/images/capture-log.md"
TIMESTAMP=$(date '+%Y-%m-%d_%H-%M-%S')

# ──────────────────────────────────────
# 색상
# ──────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

info()    { echo -e "${GREEN}[CAPTURE]${NC} $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC} $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*" >&2; exit 1; }
heading() { echo -e "\n${BLUE}═══ $* ═══${NC}"; }

# ──────────────────────────────────────
# 목록 보기
# ──────────────────────────────────────
cmd_list() {
  heading "SearCam Book 캡처된 스크린샷"
  if [ -f "${LOG_FILE}" ]; then
    cat "${LOG_FILE}"
  else
    echo "캡처된 이미지가 없습니다."
  fi
  echo ""
  echo "총 이미지 수: $(find "${IMAGES_DIR}" -name "*.png" 2>/dev/null | wc -l | tr -d ' ')개"
}

# ──────────────────────────────────────
# 메인 캡처 함수
# ──────────────────────────────────────
cmd_capture() {
  local chapter="${1:-}"
  local desc="${2:-screenshot}"
  local mode="${3:---full}"

  [[ -z "$chapter" ]] && error "챕터 필수. 예: ./capture.sh ch11 '설명'"

  # 챕터 디렉토리 생성
  local chapter_dir="${IMAGES_DIR}/${chapter}"
  mkdir -p "${chapter_dir}"

  # 파일명 생성
  local safe_desc
  safe_desc=$(echo "$desc" | tr ' ' '-' | tr '[:upper:]' '[:lower:]' | sed 's/[^a-z0-9가-힣-]//g')
  local filename="${TIMESTAMP}_${safe_desc}.png"
  local filepath="${chapter_dir}/${filename}"
  local rel_path="assets/images/screenshots/${chapter}/${filename}"

  heading "스크린샷 캡처"
  info "챕터: ${chapter}"
  info "설명: ${desc}"
  info "모드: ${mode}"
  info "저장: ${filepath}"

  # ── 캡처 실행 ──
  case "${mode}" in
    --emulator)
      capture_emulator "${filepath}"
      ;;
    --region)
      capture_region "${filepath}"
      ;;
    --window)
      capture_window "${filepath}"
      ;;
    --full|*)
      capture_full "${filepath}"
      ;;
  esac

  info "캡처 완료: ${filepath}"

  # ── 최적화 (ImageMagick 있는 경우) ──
  if command -v convert >/dev/null 2>&1; then
    convert "${filepath}" -strip -quality 90 "${filepath}"
    info "이미지 최적화 완료"
  fi

  # ── 파일 크기 ──
  local size
  size=$(du -sh "${filepath}" 2>/dev/null | cut -f1)
  info "파일 크기: ${size}"

  # ── 로그 기록 ──
  log_capture "${chapter}" "${desc}" "${rel_path}" "${mode}"

  # ── 마크다운 스니펫 출력 ──
  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "📋 챕터에 삽입할 마크다운:"
  echo ""
  echo "![${desc}](../${rel_path})"
  echo ""
  echo "또는 캡션 포함:"
  echo ""
  echo "![${desc}](../${rel_path})"
  echo "> *그림 X.X: ${desc}*"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

  # ── 챕터 파일에 자동 삽입 여부 확인 ──
  local chapter_file="${CHAPTERS_DIR}/${chapter}-*.md"
  local found
  found=$(ls ${chapter_file} 2>/dev/null | head -1 || echo "")

  if [[ -n "${found}" ]]; then
    echo ""
    echo -n "챕터 파일 끝에 이미지를 자동 삽입할까요? [y/N] "
    read -r answer
    if [[ "${answer}" =~ ^[Yy]$ ]]; then
      insert_into_chapter "${found}" "${desc}" "${rel_path}"
    fi
  fi
}

# ──────────────────────────────────────
# 플랫폼별 캡처 방식
# ──────────────────────────────────────
capture_full() {
  local out="$1"
  if [[ "$(uname)" == "Darwin" ]]; then
    screencapture -x "${out}"
  else
    # Linux (scrot fallback)
    command -v scrot >/dev/null 2>&1 || error "scrot 필요: sudo apt install scrot"
    scrot "${out}"
  fi
}

capture_region() {
  local out="$1"
  info "드래그로 캡처할 영역을 선택하세요..."
  if [[ "$(uname)" == "Darwin" ]]; then
    screencapture -s -x "${out}"
  else
    command -v scrot >/dev/null 2>&1 || error "scrot 필요: sudo apt install scrot"
    scrot -s "${out}"
  fi
}

capture_window() {
  local out="$1"
  info "캡처할 창을 클릭하세요..."
  if [[ "$(uname)" == "Darwin" ]]; then
    screencapture -w -x "${out}"
  else
    command -v scrot >/dev/null 2>&1 || error "scrot 필요"
    scrot -u "${out}"
  fi
}

capture_emulator() {
  local out="$1"
  # Android 에뮬레이터/디바이스 캡처
  if ! command -v adb >/dev/null 2>&1; then
    error "adb를 찾을 수 없습니다. Android SDK PATH를 확인하세요."
  fi

  local devices
  devices=$(adb devices | grep -v "List of devices" | grep -v "^$" | head -1 | awk '{print $1}')
  [[ -z "${devices}" ]] && error "연결된 Android 디바이스/에뮬레이터가 없습니다."

  info "디바이스: ${devices}"
  adb -s "${devices}" shell screencap -p /sdcard/searcam_cap.png
  adb -s "${devices}" pull /sdcard/searcam_cap.png "${out}"
  adb -s "${devices}" shell rm /sdcard/searcam_cap.png
}

# ──────────────────────────────────────
# 로그 기록
# ──────────────────────────────────────
log_capture() {
  local chapter="$1" desc="$2" rel_path="$3" mode="$4"

  # 로그 파일 초기화 (없으면)
  if [[ ! -f "${LOG_FILE}" ]]; then
    cat > "${LOG_FILE}" << 'LOGEOF'
# SearCam Book — 캡처 로그

| 날짜 | 챕터 | 설명 | 모드 | 경로 |
|------|------|------|------|------|
LOGEOF
  fi

  local date_str
  date_str=$(date '+%Y-%m-%d %H:%M')
  echo "| ${date_str} | ${chapter} | ${desc} | ${mode} | \`${rel_path}\` |" >> "${LOG_FILE}"
}

# ──────────────────────────────────────
# 챕터 파일에 삽입
# ──────────────────────────────────────
insert_into_chapter() {
  local chapter_file="$1" desc="$2" rel_path="$3"

  cat >> "${chapter_file}" << MDEOF

---

![${desc}](../${rel_path})
> *${desc}*

MDEOF

  info "이미지가 ${chapter_file} 에 삽입되었습니다."
}

# ──────────────────────────────────────
# 일괄 캡처 모드 (개발 중 자동 호출용)
# ──────────────────────────────────────
cmd_auto() {
  # 5초 카운트다운 후 전체 화면 캡처
  local chapter="$1"
  local desc="$2"
  echo "5초 후 자동 캡처됩니다. 캡처할 화면을 준비하세요..."
  for i in 5 4 3 2 1; do
    echo -ne "\r${YELLOW}${i}초...${NC}  "
    sleep 1
  done
  echo -e "\r${GREEN}캡처!${NC}     "
  cmd_capture "${chapter}" "${desc}" "--full"
}

# ──────────────────────────────────────
# 진입점
# ──────────────────────────────────────
main() {
  local cmd="${1:-help}"

  case "${cmd}" in
    list)
      cmd_list
      ;;
    auto)
      [[ $# -lt 3 ]] && error "사용법: ./capture.sh auto <챕터> <설명>"
      cmd_auto "$2" "$3"
      ;;
    help|--help|-h)
      echo "사용법: ./capture.sh <챕터> <설명> [--full|--region|--window|--emulator]"
      echo "        ./capture.sh list"
      echo "        ./capture.sh auto <챕터> <설명>"
      ;;
    *)
      # 첫 인수가 챕터명인 경우
      if [[ $# -ge 2 ]]; then
        cmd_capture "$1" "$2" "${3:---full}"
      else
        echo "사용법: ./capture.sh <챕터> <설명> [옵션]"
        exit 1
      fi
      ;;
  esac
}

main "$@"
