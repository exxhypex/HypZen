#!/usr/bin/env sh
set -eu

# Do not hardcode secrets in VCS.
# Provide these as environment variables (recommended) or via `keystore.properties` (ignored by git).
#
# Example:
#   export RELEASE_STORE_PASSWORD=...
#   export RELEASE_KEY_ALIAS=...
#   export RELEASE_KEY_PASSWORD=...
#   sh ./sign.sh

: "${RELEASE_STORE_PASSWORD:?Set RELEASE_STORE_PASSWORD}"
: "${RELEASE_KEY_ALIAS:?Set RELEASE_KEY_ALIAS}"
: "${RELEASE_KEY_PASSWORD:?Set RELEASE_KEY_PASSWORD}"

sh ./gradlew :app:assembleRelease \
  -PRELEASE_STORE_PASSWORD="$RELEASE_STORE_PASSWORD" \
  -PRELEASE_KEY_ALIAS="$RELEASE_KEY_ALIAS" \
  -PRELEASE_KEY_PASSWORD="$RELEASE_KEY_PASSWORD"
