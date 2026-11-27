#!/usr/bin/env sh
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
GRADLE_VERSION="8.4"
GRADLE_CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/gradle-${GRADLE_VERSION}-bin"
ZIP_FILE="/tmp/gradle-${GRADLE_VERSION}-bin.zip"

if ! ls -d "${GRADLE_CACHE}"/*/gradle-"${GRADLE_VERSION}" >/dev/null 2>&1; then
  mkdir -p "${GRADLE_CACHE}" /tmp
  curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o "${ZIP_FILE}"
  TMP_DIR=$(mktemp -d)
  unzip -q "${ZIP_FILE}" -d "${TMP_DIR}"
  HASH_DIR="${GRADLE_CACHE}/manual_dist"
  mkdir -p "${HASH_DIR}"
  mv "${TMP_DIR}/gradle-${GRADLE_VERSION}" "${HASH_DIR}/"
  rm -rf "${TMP_DIR}" "${ZIP_FILE}"
fi

GRADLE_BIN=$(find "${GRADLE_CACHE}" -maxdepth 3 -path "*/gradle-${GRADLE_VERSION}/bin/gradle" | head -n 1)
exec "${GRADLE_BIN}" "$@"
