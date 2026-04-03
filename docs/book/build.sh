#!/usr/bin/env bash
#
# build.sh - SearCam 기술 서적 빌드 스크립트
#
# 사용법:
#   ./build.sh [md|docx|pdf|all|clean]
#
# 의존성:
#   - pandoc 3.0+
#   - xelatex (texlive-xetex)
#   - Apple SD Gothic Neo 폰트 (macOS 기본 내장)
#   - Menlo 폰트 (macOS 기본 내장)

set -euo pipefail

# ──────────────────────────────────────
# 경로 설정
# ──────────────────────────────────────
BOOK_DIR="$(cd "$(dirname "$0")" && pwd)"
CHAPTERS_DIR="${BOOK_DIR}/chapters"
OUTPUT_DIR="${BOOK_DIR}/output"
METADATA="${BOOK_DIR}/metadata.yaml"
COMBINED="${OUTPUT_DIR}/combined.md"

# ──────────────────────────────────────
# 챕터 순서 정의
# ──────────────────────────────────────
CHAPTER_FILES=(
  "ch01-problem-discovery.md"
  "ch02-user-research.md"
  "ch03-product-requirements.md"
  "ch04-revenue-model.md"
  "ch05-technical-requirements.md"
  "ch06-system-architecture.md"
  "ch07-technical-design.md"
  "ch08-api-database.md"
  "ch09-security-privacy.md"
  "ch10-project-setup.md"
  "ch11-wifi-scan.md"
  "ch12-lens-detection.md"
  "ch13-emf-detection.md"
  "ch14-cross-validation.md"
  "ch15-ui-ux.md"
  "ch16-report-checklist.md"
  "ch17-test-strategy.md"
  "ch18-cicd-pipeline.md"
  "ch19-release-deploy.md"
  "ch20-monitoring.md"
  "ch21-marketing.md"
  "ch22-analytics.md"
  "ch23-accessibility-i18n.md"
  "ch24-partnership.md"
  "appendix-a-error-handling.md"
  "appendix-b-performance.md"
  "appendix-c-oui-database.md"
  "appendix-d-risk-matrix.md"
  "appendix-e-legal-compliance.md"
)

# ──────────────────────────────────────
# 유틸리티 함수
# ──────────────────────────────────────
info() {
  echo "[INFO] $*"
}

error() {
  echo "[ERROR] $*" >&2
  exit 1
}

check_deps() {
  local missing=()
  command -v pandoc >/dev/null 2>&1 || missing+=("pandoc")
  if [[ "${1:-}" == "pdf" ]]; then
    command -v xelatex >/dev/null 2>&1 || missing+=("xelatex (texlive-xetex)")
  fi
  if [[ ${#missing[@]} -gt 0 ]]; then
    error "필수 도구가 설치되어 있지 않습니다: ${missing[*]}"
  fi
}

ensure_output_dir() {
  mkdir -p "${OUTPUT_DIR}"
}

# ──────────────────────────────────────
# 빌드 단계
# ──────────────────────────────────────

# 1. 챕터 파일들을 하나의 Markdown으로 합치기
build_combined_md() {
  info "챕터 파일 병합 시작..."
  ensure_output_dir

  # README.md를 서문으로 사용
  cat "${BOOK_DIR}/README.md" > "${COMBINED}"
  echo -e "\n\n\\newpage\n\n" >> "${COMBINED}"

  local count=0
  for chapter in "${CHAPTER_FILES[@]}"; do
    local filepath="${CHAPTERS_DIR}/${chapter}"
    if [[ -f "${filepath}" ]]; then
      cat "${filepath}" >> "${COMBINED}"
      echo -e "\n\n\\newpage\n\n" >> "${COMBINED}"
      count=$((count + 1))
    else
      info "건너뜀 (파일 없음): ${chapter}"
    fi
  done

  info "병합 완료: ${count}개 챕터 → ${COMBINED}"
}

# 2. DOCX 생성
build_docx() {
  check_deps "docx"
  build_combined_md

  info "DOCX 생성 중..."
  pandoc "${COMBINED}" \
    --metadata-file="${METADATA}" \
    --toc \
    --toc-depth=3 \
    --number-sections \
    --reference-doc="${BOOK_DIR}/templates/reference.docx" 2>/dev/null \
    || pandoc "${COMBINED}" \
         --metadata-file="${METADATA}" \
         --toc \
         --toc-depth=3 \
         --number-sections \
         -o "${OUTPUT_DIR}/book.docx"

  # reference.docx가 있으면 해당 스타일 적용, 없으면 기본 스타일
  if [[ -f "${BOOK_DIR}/templates/reference.docx" ]]; then
    pandoc "${COMBINED}" \
      --metadata-file="${METADATA}" \
      --toc \
      --toc-depth=3 \
      --number-sections \
      --reference-doc="${BOOK_DIR}/templates/reference.docx" \
      -o "${OUTPUT_DIR}/book.docx"
  else
    pandoc "${COMBINED}" \
      --metadata-file="${METADATA}" \
      --toc \
      --toc-depth=3 \
      --number-sections \
      -o "${OUTPUT_DIR}/book.docx"
  fi

  info "DOCX 생성 완료: ${OUTPUT_DIR}/book.docx"
}

# 3. PDF 생성 (XeLaTeX, 한국어 지원)
build_pdf() {
  check_deps "pdf"
  build_combined_md

  info "PDF 생성 중 (xelatex)..."
  pandoc "${COMBINED}" \
    --metadata-file="${METADATA}" \
    --pdf-engine=xelatex \
    --toc \
    --toc-depth=3 \
    --number-sections \
    -V mainfont="Noto Sans KR" \
    -V sansfont="Noto Sans KR" \
    -V monofont="JetBrains Mono" \
    -V fontsize=11pt \
    -V geometry:margin=25mm \
    -V linestretch=1.5 \
    -o "${OUTPUT_DIR}/book.pdf"

  info "PDF 생성 완료: ${OUTPUT_DIR}/book.pdf"
}

# 4. 정리
clean() {
  info "출력 파일 정리 중..."
  rm -rf "${OUTPUT_DIR}"
  info "정리 완료"
}

# ──────────────────────────────────────
# 메인 진입점
# ──────────────────────────────────────
main() {
  local target="${1:-all}"

  case "${target}" in
    md)
      build_combined_md
      ;;
    docx)
      build_docx
      ;;
    pdf)
      build_pdf
      ;;
    all)
      build_docx
      build_pdf
      info "전체 빌드 완료"
      ;;
    clean)
      clean
      ;;
    *)
      echo "사용법: $0 [md|docx|pdf|all|clean]"
      echo ""
      echo "  md    - 챕터 병합 (combined.md)"
      echo "  docx  - Word 문서 생성"
      echo "  pdf   - PDF 생성 (xelatex 필요)"
      echo "  all   - DOCX + PDF 모두 생성"
      echo "  clean - 출력 파일 삭제"
      exit 1
      ;;
  esac
}

main "$@"
