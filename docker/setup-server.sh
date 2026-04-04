#!/usr/bin/env bash
# ──────────────────────────────────────────────────────
# SearCam Build Server Setup
# abada-65에서 최초 1회 실행
#
# 실행:
#   bash setup-server.sh
# ──────────────────────────────────────────────────────

set -euo pipefail

REPO_URL="https://github.com/saintgo7/saas-searCam.git"
PROJECT_DIR="$HOME/saas-searCam"

CYAN='\033[0;36m'; GREEN='\033[0;32m'; NC='\033[0m'
info()    { echo -e "${CYAN}[setup]${NC} $*"; }
success() { echo -e "${GREEN}[setup]${NC} $*"; }

# ── 1. Docker 설치 확인 ──
if ! command -v docker &>/dev/null; then
    info "Docker 설치 중..."
    curl -fsSL https://get.docker.com | sh
    sudo usermod -aG docker "$USER"
    info "Docker 설치 완료. 'newgrp docker' 또는 재로그인 후 다시 실행하세요."
    exit 0
fi
success "Docker: $(docker --version)"

# ── 2. 레포 클론 또는 업데이트 ──
if [ -d "$PROJECT_DIR" ]; then
    info "레포 업데이트 중..."
    git -C "$PROJECT_DIR" pull
else
    info "레포 클론 중..."
    git clone "$REPO_URL" "$PROJECT_DIR"
fi
success "레포 준비 완료: $PROJECT_DIR"

# ── 3. Docker 이미지 빌드 ──
info "Docker 이미지 빌드 중 (최초 약 5~10분 소요)..."
cd "$PROJECT_DIR/docker"
docker compose build

success "빌드 환경 준비 완료."
echo ""
echo "────────────────────────────────────"
echo "사용법:"
echo ""
echo "  cd $PROJECT_DIR/docker"
echo ""
echo "  # 유닛 테스트"
echo "  docker compose run --rm build test"
echo ""
echo "  # 컴파일 오류 체크"
echo "  docker compose run --rm build check"
echo ""
echo "  # 디버그 APK 빌드"
echo "  docker compose run --rm build build"
echo ""
echo "  # 쉘 진입"
echo "  docker compose run --rm build shell"
echo "────────────────────────────────────"
