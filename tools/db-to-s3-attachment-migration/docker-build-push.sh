#!/usr/bin/env bash

# Usage: ./docker-build-push.sh <tag>

# Prerequisites:
#   - sbt dbToS3AttachmentMigration/proguard
#   - docker login

set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <tag>"
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR_DIR="$SCRIPT_DIR/target/scala-2.13"
IMAGE_NAME="orbeon/db-to-s3-attachment-migration"
TAG="$1"

if ! compgen -G "$JAR_DIR/db-to-s3-attachment-migration-*-shrunk.jar" >/dev/null; then
  echo "Error: JAR not found. Run 'sbt dbToS3AttachmentMigration/proguard' first."
  exit 1
fi

docker build -t "${IMAGE_NAME}:${TAG}" "$SCRIPT_DIR"
docker push "${IMAGE_NAME}:${TAG}"
