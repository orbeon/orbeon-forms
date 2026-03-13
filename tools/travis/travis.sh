#!/usr/bin/env bash
# travis.sh — Build and run Travis CI builds locally using a custom Docker image.
#
# Usage:
#   ./tools/travis/travis.sh build   — Build the custom Docker image (one-time)
#   ./tools/travis/travis.sh run     — Start container and run the build
#   ./tools/travis/travis.sh all     — Build image + run build
#   ./tools/travis/travis.sh shell   — Open interactive shell in running container
#
# Required files on host (in ORBEON_DIR):
#   .github-env  — exports GITHUB_TOKEN
#   .travis-env  — plaintext project secrets (replaces secure: entries in .travis.yml)
#   license.xml  — Orbeon license file

set -e

# =============================================================================
# Configuration (edit these for your environment)
# =============================================================================

ORBEON_DIR="$HOME/.orbeon"
TRAVIS_REPO_SLUG="orbeon/orbeon-forms-pe"
TRAVIS_BRANCH="master"
TRAVIS_COMMIT="HEAD"
TRAVIS_TARGET="orbeon-dist"
DB=""

# =============================================================================
# Derived values (no need to edit)
# =============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
IMAGE_NAME="orbeon-travis-local"
CONTAINER_NAME="travis-local"

# =============================================================================
# Commands
# =============================================================================

cmd_build() {
  echo "Building Docker image '${IMAGE_NAME}' (platform: linux/amd64)..."
  docker build --platform linux/amd64 -t "${IMAGE_NAME}" "${SCRIPT_DIR}"
  echo "Image '${IMAGE_NAME}' built successfully."
}

cmd_run() {
  # Source GitHub credentials
  source "${ORBEON_DIR}/.github-env"

  # Remove any existing container
  docker rm -f "${CONTAINER_NAME}" 2>/dev/null || true

  echo "Starting container '${CONTAINER_NAME}'..."
  docker run --name "${CONTAINER_NAME}" --privileged \
    -v "${ORBEON_DIR}/license.xml:/home/travis/.orbeon/license.xml:ro" \
    -v "${REPO_DIR}/.travis.yml:/home/travis/.travis.yml:ro" \
    -v "${ORBEON_DIR}/.travis-env:/home/travis/.travis-env:ro" \
    -dit "${IMAGE_NAME}" /sbin/init

  # Write environment variables to .bash_profile inside the container
  docker exec "${CONTAINER_NAME}" bash -c "cat >> /home/travis/.bash_profile << 'ENVEOF'
export GITHUB_TOKEN=\"${GITHUB_TOKEN}\"
export TRAVIS_REPO_SLUG=\"${TRAVIS_REPO_SLUG}\"
export TRAVIS_BRANCH=\"${TRAVIS_BRANCH}\"
export TRAVIS_COMMIT=\"${TRAVIS_COMMIT}\"
export TRAVIS_TARGET=\"${TRAVIS_TARGET}\"
export DB=\"${DB}\"
ENVEOF"

  echo "Running build (target: ${TRAVIS_TARGET})..."
  docker exec -u travis "${CONTAINER_NAME}" bash -lc /home/travis/run-build.sh
}

cmd_shell() {
  echo "Opening shell in container '${CONTAINER_NAME}'..."
  docker exec -it -u travis "${CONTAINER_NAME}" bash -l
}

# =============================================================================
# Main
# =============================================================================

case "${1:-}" in
  build)  cmd_build ;;
  run)    cmd_run ;;
  all)    cmd_build && cmd_run ;;
  shell)  cmd_shell ;;
  *)
    echo "Usage: $0 {build|run|all|shell}"
    echo ""
    echo "Commands:"
    echo "  build  — Build the custom Docker image (one-time, slow on arm)"
    echo "  run    — Start container and run the build"
    echo "  all    — Build image + run build"
    echo "  shell  — Open interactive shell in running container (for debugging)"
    echo ""
    echo "Configuration (edit at top of this script):"
    echo "  ORBEON_DIR      = ${ORBEON_DIR}"
    echo "  TRAVIS_TARGET   = ${TRAVIS_TARGET}"
    echo "  TRAVIS_BRANCH   = ${TRAVIS_BRANCH}"
    echo "  TRAVIS_COMMIT   = ${TRAVIS_COMMIT}"
    echo "  DB              = ${DB:-<not set>}"
    ;;
esac
