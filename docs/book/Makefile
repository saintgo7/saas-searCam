# ──────────────────────────────────────
# SearCam 기술 서적 빌드 Makefile
# ──────────────────────────────────────
#
# 한국어 빌드:
#   make          - KO DOCX + PDF 모두 생성
#   make md       - 챕터 병합 (combined.md)
#   make docx     - Word 문서 생성 (한국어)
#   make pdf      - PDF 생성 (한국어)
#
# 영어 빌드:
#   make all-en   - EN DOCX + PDF 모두 생성
#   make en-md    - 영어 챕터 병합 (combined-en.md)
#   make en-docx  - Word 문서 생성 (영어)
#   make en-pdf   - PDF 생성 (영어)
#
# 전체:
#   make both     - 한국어 + 영어 전체 빌드
#   make clean    - 출력 파일 삭제
#   make check    - 의존성 확인
#   make count    - 분량 통계 (한국어)
#   make count-en - 분량 통계 (영어)
#
# 의존성:
#   - pandoc 3.0+
#   - xelatex (texlive-xetex)

SHELL := /bin/bash

# ──────────────────────────────────────
# 경로
# ──────────────────────────────────────
BOOK_DIR       := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))
CHAPTERS_DIR   := $(BOOK_DIR)chapters
CHAPTERS_EN_DIR := $(BOOK_DIR)chapters-en
OUTPUT_DIR     := $(BOOK_DIR)output
METADATA       := $(BOOK_DIR)metadata.yaml
METADATA_EN    := $(BOOK_DIR)metadata-en.yaml
BUILD_SCRIPT   := $(BOOK_DIR)build.sh

# ──────────────────────────────────────
# 챕터 파일 목록 (순서 유지)
# ──────────────────────────────────────
CHAPTERS := \
	$(CHAPTERS_DIR)/ch01-problem-discovery.md \
	$(CHAPTERS_DIR)/ch02-prd.md \
	$(CHAPTERS_DIR)/ch03-trd-architecture.md \
	$(CHAPTERS_DIR)/ch04-system-architecture.md \
	$(CHAPTERS_DIR)/ch05-ui-ux-design.md \
	$(CHAPTERS_DIR)/ch06-security-design.md \
	$(CHAPTERS_DIR)/ch07-test-strategy.md \
	$(CHAPTERS_DIR)/ch08-database-design.md \
	$(CHAPTERS_DIR)/ch09-data-flow.md \
	$(CHAPTERS_DIR)/ch10-project-setup.md \
	$(CHAPTERS_DIR)/ch11-wifi-scan.md \
	$(CHAPTERS_DIR)/ch12-lens-detection.md \
	$(CHAPTERS_DIR)/ch13-emf-detection.md \
	$(CHAPTERS_DIR)/ch14-cross-validation.md \
	$(CHAPTERS_DIR)/ch15-ui-implementation.md \
	$(CHAPTERS_DIR)/ch16-room-implementation.md \
	$(CHAPTERS_DIR)/ch17-pdf-report.md \
	$(CHAPTERS_DIR)/ch18-testing.md \
	$(CHAPTERS_DIR)/ch19-cicd.md \
	$(CHAPTERS_DIR)/ch20-release.md \
	$(CHAPTERS_DIR)/ch21-monitoring.md \
	$(CHAPTERS_DIR)/ch22-legal-privacy.md \
	$(CHAPTERS_DIR)/ch23-revenue-gtm.md \
	$(CHAPTERS_DIR)/ch24-retrospective.md \
	$(CHAPTERS_DIR)/appendix-a-oui-database.md \
	$(CHAPTERS_DIR)/appendix-b-checklist.md \
	$(CHAPTERS_DIR)/appendix-c-error-codes.md \
	$(CHAPTERS_DIR)/appendix-d-api-reference.md \
	$(CHAPTERS_DIR)/appendix-e-changelog.md

# 실제 존재하는 챕터만 필터링
EXISTING_CHAPTERS := $(wildcard $(CHAPTERS))

# 영어 챕터 목록 (같은 파일명, 다른 디렉토리)
CHAPTERS_EN := $(patsubst $(CHAPTERS_DIR)/%,$(CHAPTERS_EN_DIR)/%,$(CHAPTERS))
EXISTING_CHAPTERS_EN := $(wildcard $(CHAPTERS_EN))

# 출력물 (한국어)
COMBINED := $(OUTPUT_DIR)/combined.md
DOCX     := $(OUTPUT_DIR)/book.docx
PDF      := $(OUTPUT_DIR)/book.pdf

# 출력물 (영어)
COMBINED_EN := $(OUTPUT_DIR)/combined-en.md
DOCX_EN     := $(OUTPUT_DIR)/book-en.docx
PDF_EN      := $(OUTPUT_DIR)/book-en.pdf

# ──────────────────────────────────────
# Pandoc 옵션
# ──────────────────────────────────────
PANDOC_COMMON := \
	--metadata-file=$(METADATA) \
	--from markdown-yaml_metadata_block \
	--toc \
	--toc-depth=3 \
	--number-sections

PANDOC_PDF := \
	$(PANDOC_COMMON) \
	--pdf-engine=xelatex \
	-V mainfont="Apple SD Gothic Neo" \
	-V sansfont="Apple SD Gothic Neo" \
	-V monofont="Menlo" \
	-V fontsize=11pt \
	-V geometry:margin=25mm \
	-V linestretch=1.5

PANDOC_DOCX := $(PANDOC_COMMON)

# reference.docx가 있으면 스타일 적용
ifneq ($(wildcard $(BOOK_DIR)templates/reference.docx),)
PANDOC_DOCX += --reference-doc=$(BOOK_DIR)templates/reference.docx
endif

# ──────────────────────────────────────
# 영어 Pandoc 옵션
# ──────────────────────────────────────
PANDOC_EN_COMMON := \
	--metadata-file=$(METADATA_EN) \
	--from markdown-yaml_metadata_block \
	--toc \
	--toc-depth=3 \
	--number-sections

PANDOC_EN_PDF := \
	$(PANDOC_EN_COMMON) \
	--pdf-engine=xelatex \
	-V mainfont="Georgia" \
	-V sansfont="Helvetica Neue" \
	-V monofont="Menlo" \
	-V fontsize=11pt \
	-V geometry:margin=25mm \
	-V linestretch=1.5

PANDOC_EN_DOCX := $(PANDOC_EN_COMMON)

ifneq ($(wildcard $(BOOK_DIR)templates/reference-en.docx),)
PANDOC_EN_DOCX += --reference-doc=$(BOOK_DIR)templates/reference-en.docx
endif

# ──────────────────────────────────────
# 기본 타깃
# ──────────────────────────────────────
.PHONY: all md docx pdf all-en en-md en-docx en-pdf both clean check count count-en help

all: docx pdf

all-en: en-docx en-pdf

both: all all-en

help:
	@echo "SearCam 기술 서적 빌드"
	@echo ""
	@echo "한국어 빌드:"
	@echo "  make          DOCX + PDF 모두 생성 (한국어)"
	@echo "  make md       챕터 병합 (combined.md)"
	@echo "  make docx     Word 문서 생성 (한국어)"
	@echo "  make pdf      PDF 생성 (한국어)"
	@echo ""
	@echo "영어 빌드:"
	@echo "  make all-en   DOCX + PDF 모두 생성 (영어)"
	@echo "  make en-md    챕터 병합 (combined-en.md)"
	@echo "  make en-docx  Word 문서 생성 (영어)"
	@echo "  make en-pdf   PDF 생성 (영어)"
	@echo ""
	@echo "전체:"
	@echo "  make both     한국어 + 영어 전체 빌드"
	@echo "  make clean    출력 파일 삭제"
	@echo "  make check    의존성 확인"
	@echo "  make count    분량 통계 (한국어)"
	@echo "  make count-en 분량 통계 (영어)"

# ──────────────────────────────────────
# 빌드 규칙
# ──────────────────────────────────────

$(OUTPUT_DIR):
	@mkdir -p $(OUTPUT_DIR)

# ──────────────────────────────────────
# 한국어 빌드 규칙
# ──────────────────────────────────────

# 챕터 병합
md: $(COMBINED)

$(COMBINED): $(BOOK_DIR)README.md $(EXISTING_CHAPTERS) $(METADATA) | $(OUTPUT_DIR)
	@echo "[KO] 챕터 파일 병합 중..."
	@cat $(BOOK_DIR)README.md > $(COMBINED)
	@echo -e "\n\n\\\\newpage\n\n" >> $(COMBINED)
	@for f in $(EXISTING_CHAPTERS); do \
		cat "$$f" >> $(COMBINED); \
		echo -e "\n\n\\\\newpage\n\n" >> $(COMBINED); \
	done
	@echo "[KO] 병합 완료: $(COMBINED) ($(words $(EXISTING_CHAPTERS))개 챕터)"

# DOCX 생성
docx: $(DOCX)

$(DOCX): $(COMBINED) $(METADATA) | $(OUTPUT_DIR)
	@echo "[KO] DOCX 생성 중..."
	pandoc $(COMBINED) $(PANDOC_DOCX) -o $(DOCX)
	@echo "[KO] DOCX 생성 완료: $(DOCX)"

# PDF 생성
pdf: $(PDF)

$(PDF): $(COMBINED) $(METADATA) | $(OUTPUT_DIR)
	@echo "[KO] PDF 생성 중 (xelatex)..."
	pandoc $(COMBINED) $(PANDOC_PDF) -o $(PDF)
	@echo "[KO] PDF 생성 완료: $(PDF)"

# ──────────────────────────────────────
# 영어 빌드 규칙
# ──────────────────────────────────────

# 영어 챕터 병합
en-md: $(COMBINED_EN)

$(COMBINED_EN): $(EXISTING_CHAPTERS_EN) $(METADATA_EN) | $(OUTPUT_DIR)
	@echo "[EN] Merging chapters..."
	@if [ -f "$(BOOK_DIR)README-en.md" ]; then \
		cat "$(BOOK_DIR)README-en.md" > $(COMBINED_EN); \
	else \
		cat $(BOOK_DIR)README.md > $(COMBINED_EN); \
	fi
	@echo -e "\n\n\\\\newpage\n\n" >> $(COMBINED_EN)
	@for f in $(EXISTING_CHAPTERS_EN); do \
		cat "$$f" >> $(COMBINED_EN); \
		echo -e "\n\n\\\\newpage\n\n" >> $(COMBINED_EN); \
	done
	@echo "[EN] Merged: $(COMBINED_EN) ($(words $(EXISTING_CHAPTERS_EN)) chapters)"

# 영어 DOCX
en-docx: $(DOCX_EN)

$(DOCX_EN): $(COMBINED_EN) $(METADATA_EN) | $(OUTPUT_DIR)
	@echo "[EN] Generating DOCX..."
	pandoc $(COMBINED_EN) $(PANDOC_EN_DOCX) -o $(DOCX_EN)
	@echo "[EN] DOCX done: $(DOCX_EN)"

# 영어 PDF
en-pdf: $(PDF_EN)

$(PDF_EN): $(COMBINED_EN) $(METADATA_EN) | $(OUTPUT_DIR)
	@echo "[EN] Generating PDF (xelatex)..."
	pandoc $(COMBINED_EN) $(PANDOC_EN_PDF) -o $(PDF_EN)
	@echo "[EN] PDF done: $(PDF_EN)"

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
	@echo "[KO] 분량 통계:"
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

count-en:
	@echo "[EN] Word count:"
	@if [ -f "$(COMBINED_EN)" ]; then \
		words=$$(wc -w < "$(COMBINED_EN)"); \
		lines=$$(wc -l < "$(COMBINED_EN)"); \
		chars=$$(wc -c < "$(COMBINED_EN)"); \
		echo "  Words: $${words}"; \
		echo "  Lines: $${lines}"; \
		echo "  Chars: $${chars}"; \
		echo "  Est. pages: $$(( words / 250 )) pages (250 words/page)"; \
	else \
		echo "  combined-en.md not found. Run 'make en-md' first."; \
	fi
	@echo ""
	@echo "Per-chapter word count:"
	@for f in $(EXISTING_CHAPTERS_EN); do \
		words=$$(wc -w < "$$f" 2>/dev/null || echo 0); \
		printf "  %-40s %s words\n" "$$(basename $$f)" "$$words"; \
	done
