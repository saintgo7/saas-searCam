# ──────────────────────────────────────
# SearCam 기술 서적 빌드 Makefile
# ──────────────────────────────────────
#
# 사용법:
#   make          - DOCX + PDF 모두 생성
#   make md       - 챕터 병합 (combined.md)
#   make docx     - Word 문서 생성
#   make pdf      - PDF 생성 (xelatex 필요)
#   make clean    - 출력 파일 삭제
#   make check    - 의존성 확인
#   make count    - 분량 통계
#
# 의존성:
#   - pandoc 3.0+
#   - xelatex (texlive-xetex)
#   - Noto Sans KR, JetBrains Mono 폰트

SHELL := /bin/bash

# ──────────────────────────────────────
# 경로
# ──────────────────────────────────────
BOOK_DIR    := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))
CHAPTERS_DIR := $(BOOK_DIR)chapters
OUTPUT_DIR   := $(BOOK_DIR)output
METADATA     := $(BOOK_DIR)metadata.yaml
BUILD_SCRIPT := $(BOOK_DIR)build.sh

# ──────────────────────────────────────
# 챕터 파일 목록 (순서 유지)
# ──────────────────────────────────────
CHAPTERS := \
	$(CHAPTERS_DIR)/ch01-problem-discovery.md \
	$(CHAPTERS_DIR)/ch02-user-research.md \
	$(CHAPTERS_DIR)/ch03-product-requirements.md \
	$(CHAPTERS_DIR)/ch04-revenue-model.md \
	$(CHAPTERS_DIR)/ch05-technical-requirements.md \
	$(CHAPTERS_DIR)/ch06-system-architecture.md \
	$(CHAPTERS_DIR)/ch07-technical-design.md \
	$(CHAPTERS_DIR)/ch08-api-database.md \
	$(CHAPTERS_DIR)/ch09-security-privacy.md \
	$(CHAPTERS_DIR)/ch10-project-setup.md \
	$(CHAPTERS_DIR)/ch11-wifi-scan.md \
	$(CHAPTERS_DIR)/ch12-lens-detection.md \
	$(CHAPTERS_DIR)/ch13-emf-detection.md \
	$(CHAPTERS_DIR)/ch14-cross-validation.md \
	$(CHAPTERS_DIR)/ch15-ui-ux.md \
	$(CHAPTERS_DIR)/ch16-report-checklist.md \
	$(CHAPTERS_DIR)/ch17-test-strategy.md \
	$(CHAPTERS_DIR)/ch18-cicd-pipeline.md \
	$(CHAPTERS_DIR)/ch19-release-deploy.md \
	$(CHAPTERS_DIR)/ch20-monitoring.md \
	$(CHAPTERS_DIR)/ch21-marketing.md \
	$(CHAPTERS_DIR)/ch22-analytics.md \
	$(CHAPTERS_DIR)/ch23-accessibility-i18n.md \
	$(CHAPTERS_DIR)/ch24-partnership.md \
	$(CHAPTERS_DIR)/appendix-a-error-handling.md \
	$(CHAPTERS_DIR)/appendix-b-performance.md \
	$(CHAPTERS_DIR)/appendix-c-oui-database.md \
	$(CHAPTERS_DIR)/appendix-d-risk-matrix.md \
	$(CHAPTERS_DIR)/appendix-e-legal-compliance.md

# 실제 존재하는 챕터만 필터링
EXISTING_CHAPTERS := $(wildcard $(CHAPTERS))

# 출력물
COMBINED := $(OUTPUT_DIR)/combined.md
DOCX     := $(OUTPUT_DIR)/book.docx
PDF      := $(OUTPUT_DIR)/book.pdf

# ──────────────────────────────────────
# Pandoc 옵션
# ──────────────────────────────────────
PANDOC_COMMON := \
	--metadata-file=$(METADATA) \
	--toc \
	--toc-depth=3 \
	--number-sections

PANDOC_PDF := \
	$(PANDOC_COMMON) \
	--pdf-engine=xelatex \
	-V mainfont="Noto Sans KR" \
	-V sansfont="Noto Sans KR" \
	-V monofont="JetBrains Mono" \
	-V fontsize=11pt \
	-V geometry:margin=25mm \
	-V linestretch=1.5

PANDOC_DOCX := $(PANDOC_COMMON)

# reference.docx가 있으면 스타일 적용
ifneq ($(wildcard $(BOOK_DIR)templates/reference.docx),)
PANDOC_DOCX += --reference-doc=$(BOOK_DIR)templates/reference.docx
endif

# ──────────────────────────────────────
# 기본 타깃
# ──────────────────────────────────────
.PHONY: all md docx pdf clean check count help

all: docx pdf

help:
	@echo "SearCam 기술 서적 빌드"
	@echo ""
	@echo "타깃:"
	@echo "  make          DOCX + PDF 모두 생성"
	@echo "  make md       챕터 병합 (combined.md)"
	@echo "  make docx     Word 문서 생성"
	@echo "  make pdf      PDF 생성 (xelatex 필요)"
	@echo "  make clean    출력 파일 삭제"
	@echo "  make check    의존성 확인"
	@echo "  make count    분량 통계"

# ──────────────────────────────────────
# 빌드 규칙
# ──────────────────────────────────────

$(OUTPUT_DIR):
	@mkdir -p $(OUTPUT_DIR)

# 챕터 병합
md: $(COMBINED)

$(COMBINED): $(BOOK_DIR)README.md $(EXISTING_CHAPTERS) $(METADATA) | $(OUTPUT_DIR)
	@echo "[INFO] 챕터 파일 병합 중..."
	@cat $(BOOK_DIR)README.md > $(COMBINED)
	@echo -e "\n\n\\\\newpage\n\n" >> $(COMBINED)
	@for f in $(EXISTING_CHAPTERS); do \
		cat "$$f" >> $(COMBINED); \
		echo -e "\n\n\\\\newpage\n\n" >> $(COMBINED); \
	done
	@echo "[INFO] 병합 완료: $(COMBINED) ($(words $(EXISTING_CHAPTERS))개 챕터)"

# DOCX 생성
docx: $(DOCX)

$(DOCX): $(COMBINED) $(METADATA) | $(OUTPUT_DIR)
	@echo "[INFO] DOCX 생성 중..."
	pandoc $(COMBINED) $(PANDOC_DOCX) -o $(DOCX)
	@echo "[INFO] DOCX 생성 완료: $(DOCX)"

# PDF 생성
pdf: $(PDF)

$(PDF): $(COMBINED) $(METADATA) | $(OUTPUT_DIR)
	@echo "[INFO] PDF 생성 중 (xelatex)..."
	pandoc $(COMBINED) $(PANDOC_PDF) -o $(PDF)
	@echo "[INFO] PDF 생성 완료: $(PDF)"

# ──────────────────────────────────────
# 유틸리티
# ──────────────────────────────────────

clean:
	@echo "[INFO] 출력 파일 정리 중..."
	rm -rf $(OUTPUT_DIR)
	@echo "[INFO] 정리 완료"

check:
	@echo "의존성 확인:"
	@printf "  pandoc:   "; command -v pandoc >/dev/null 2>&1 && pandoc --version | head -1 || echo "미설치"
	@printf "  xelatex:  "; command -v xelatex >/dev/null 2>&1 && xelatex --version | head -1 || echo "미설치"
	@echo ""
	@echo "폰트 확인:"
	@printf "  Noto Sans KR:   "; fc-list 2>/dev/null | grep -q "Noto Sans KR" && echo "설치됨" || echo "미설치"
	@printf "  JetBrains Mono: "; fc-list 2>/dev/null | grep -q "JetBrains Mono" && echo "설치됨" || echo "미설치"
	@echo ""
	@echo "챕터 현황:"
	@echo "  정의된 챕터: $(words $(CHAPTERS))개"
	@echo "  존재하는 챕터: $(words $(EXISTING_CHAPTERS))개"
	@echo "  미작성 챕터: $$(( $(words $(CHAPTERS)) - $(words $(EXISTING_CHAPTERS)) ))개"

count:
	@echo "분량 통계:"
	@if [ -f "$(COMBINED)" ]; then \
		words=$$(wc -w < "$(COMBINED)"); \
		lines=$$(wc -l < "$(COMBINED)"); \
		chars=$$(wc -c < "$(COMBINED)"); \
		echo "  단어 수: $${words}"; \
		echo "  줄 수:   $${lines}"; \
		echo "  문자 수: $${chars}"; \
		echo "  추정 페이지: $$(( lines / 35 ))페이지 (35줄/페이지 기준)"; \
	else \
		echo "  combined.md가 없습니다. 먼저 'make md'를 실행하세요."; \
	fi
	@echo ""
	@echo "챕터별 분량:"
	@for f in $(EXISTING_CHAPTERS); do \
		lines=$$(wc -l < "$$f" 2>/dev/null || echo 0); \
		printf "  %-40s %s줄\n" "$$(basename $$f)" "$$lines"; \
	done
