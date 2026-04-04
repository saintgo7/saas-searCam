#!/usr/bin/env bash
# ──────────────────────────────────────────────────────
# SearCam Build Entrypoint
#
# Commands:
#   test     - unit tests only (default, no device needed)
#   build    - compile debug APK
#   release  - compile release APK
#   check    - lint + compile check (no APK)
#   shell    - interactive bash
# ──────────────────────────────────────────────────────

set -euo pipefail

CYAN='\033[0;36m'; GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'
info()    { echo -e "${CYAN}[searcam]${NC} $*"; }
success() { echo -e "${GREEN}[searcam]${NC} $*"; }
error()   { echo -e "${RED}[searcam]${NC} $*" >&2; }

# ── Generate gradlew if not present ──
if [ ! -f "./gradlew" ]; then
    info "gradlew not found — generating wrapper with gradle ${GRADLE_VERSION}..."
    gradle wrapper --gradle-version "${GRADLE_VERSION}" --distribution-type bin
    success "gradlew generated."
fi

chmod +x ./gradlew

CMD="${1:-test}"

case "$CMD" in
  test)
    info "Running unit tests..."
    ./gradlew testDebugUnitTest --no-daemon --stacktrace 2>&1
    success "Unit tests complete."
    ;;

  build)
    info "Building debug APK..."
    ./gradlew assembleDebug --no-daemon --stacktrace 2>&1
    success "Debug APK: app/build/outputs/apk/debug/app-debug.apk"
    ;;

  release)
    info "Building release APK..."
    ./gradlew assembleRelease --no-daemon --stacktrace 2>&1
    success "Release APK: app/build/outputs/apk/release/"
    ;;

  check)
    info "Running compile check + lint..."
    ./gradlew compileDebugKotlin lintDebug --no-daemon 2>&1
    success "Check complete."
    ;;

  shell)
    exec bash
    ;;

  *)
    error "Unknown command: $CMD"
    echo "Usage: docker compose run build [test|build|release|check|shell]"
    exit 1
    ;;
esac
