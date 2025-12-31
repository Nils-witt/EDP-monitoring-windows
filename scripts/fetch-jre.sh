#!/usr/bin/env bash
set -euo pipefail

# fetch-jre.sh
# Download and extract a Windows x64 JRE for Java 21 into the project's `jre/` directory.
# Uses the Adoptium API to resolve the latest Temurin JRE binary.
#
# Usage:
#   chmod +x scripts/fetch-jre.sh
#   scripts/fetch-jre.sh
#
# Optional environment variables:
#   JAVA_VERSION (default: 21)
#   TARGET_DIR   (default: ./jre)
#   ARCH         (default: x64)

JAVA_VERSION=${JAVA_VERSION:-21}
ARCH=${ARCH:-x64}
TARGET_DIR=${TARGET_DIR:-jre}
TMPDIR=$(mktemp -d)
trap 'rm -rf "$TMPDIR"' EXIT

# If the JRE already exists (and contains java.exe) we are idempotent and exit 0
if [ -f "$TARGET_DIR/bin/java.exe" ]; then
  echo "JRE already present at $TARGET_DIR/bin/java.exe — nothing to do."
  exit 0
fi

API_URL="https://api.adoptium.net/v3/binary/latest/${JAVA_VERSION}/ga/windows/${ARCH}/jre/hotspot/normal/eclipse"

echo "Downloading Temurin JRE ${JAVA_VERSION} for windows/${ARCH} via Adoptium API..."

ZIP_PATH="$TMPDIR/jre.zip"

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required but not installed. Aborting." >&2
  exit 2
fi

if ! command -v unzip >/dev/null 2>&1; then
  echo "unzip is required but not installed. Aborting." >&2
  exit 2
fi

curl -fL -o "$ZIP_PATH" "$API_URL"

echo "Downloaded to $ZIP_PATH; extracting..."

unzip -q "$ZIP_PATH" -d "$TMPDIR"

# Find the top-level directory produced by the archive (the JDK/JRE folder)
SRC_DIR=""
for d in "$TMPDIR"/*; do
  if [ -d "$d" ] && [ -d "$d/bin" ]; then
    SRC_DIR="$d"
    break
  fi
done

if [ -z "$SRC_DIR" ]; then
  echo "Failed to locate JRE directory inside the archive" >&2
  ls -la "$TMPDIR"
  exit 3
fi

echo "Located JRE dir: $SRC_DIR"

# Ensure target dir exists and is empty (or prompt to overwrite)
if [ -d "$TARGET_DIR" ] && [ "$(ls -A "$TARGET_DIR")" ]; then
  echo "Target directory '$TARGET_DIR' already exists and is not empty."
  echo "If it contains a jre/bin/java.exe the script would have exited earlier. Aborting to avoid overwrite." >&2
  exit 4
fi

mkdir -p "$TARGET_DIR"

# Move contents of the extracted jre/jdk folder into the target directory
# We want the final layout to be <TARGET_DIR>/bin/java.exe
shopt -s dotglob || true
mv "$SRC_DIR"/* "$TARGET_DIR"/

# Ensure executables are readable
chmod -R u+rx "$TARGET_DIR"

# Quick check
if [ ! -f "$TARGET_DIR/bin/java.exe" ]; then
  echo "Warning: java executable not found at $TARGET_DIR/bin/java.exe" >&2
  echo "Listing $TARGET_DIR:" >&2
  ls -la "$TARGET_DIR"
  exit 5
fi

echo "JRE has been installed to '$TARGET_DIR'."

echo "Done. You can now build the project; Launch4j will use $TARGET_DIR as the bundled JRE."
