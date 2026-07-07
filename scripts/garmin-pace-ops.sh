#!/usr/bin/env bash
#
# garmin-pace-ops.sh — build and upload GarminPaceCharts debug APKs.
# Mirrors the GTD release flow (AGENTS.md rule 4): versioned APK + zip → onedrive:apk/
#
# Usage:
#   scripts/garmin-pace-ops.sh doctor   # rclone/JDK/SDK checks
#   scripts/garmin-pace-ops.sh release  # ff-only main → test → assembleDebug → zip → upload
#
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ONEDRIVE_DIR="onedrive:apk"
WORKOUTS_JSON="${WORKOUTS_JSON:-$HOME/garmin_pace_charts/progression_a_workouts.json}"
GARMIN_TOKENS_JSON="${GARMIN_TOKENS_JSON:-$HOME/.garminconnect/tokens.json}"
JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"

c_red()  { printf '\033[31m%s\033[0m\n' "$*"; }
c_grn()  { printf '\033[32m%s\033[0m\n' "$*"; }
c_ylw()  { printf '\033[33m%s\033[0m\n' "$*"; }
step()   { printf '\n\033[1m== %s ==\033[0m\n' "$*"; }
fail()   { c_red "✗ $*"; exit 1; }
ok()     { c_grn "✓ $*"; }

require_clean_main() {
  cd "$REPO"
  [ "$(git rev-parse --abbrev-ref HEAD)" = "main" ] || fail "not on main (on $(git rev-parse --abbrev-ref HEAD))"
  if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
    c_red "✗ working tree has modified tracked files:"
    git status --short --untracked-files=no
    fail "Refusing to release from a dirty tree."
  fi
  ok "on main, no modified tracked files"
}

pull_ff() {
  cd "$REPO"
  local before; before="$(git rev-parse HEAD)"
  git fetch origin --quiet
  if ! git merge --ff-only origin/main; then
    fail "git merge --ff-only origin/main failed — resolve manually"
  fi
  local after; after="$(git rev-parse HEAD)"
  if [ "$before" != "$after" ]; then
    ok "fast-forwarded main ($before → $after)"
  else
    ok "already up to date with origin/main"
  fi
}

cmd_doctor() {
  step "Environment"
  [ -x "$JAVA_HOME/bin/javac" ] && ok "JDK 17 at $JAVA_HOME" || c_ylw "JDK 17 not at $JAVA_HOME (set JAVA_HOME)"
  [ -d "$ANDROID_HOME/platforms/android-35" ] && ok "Android SDK 35" || c_ylw "Android SDK 35 missing under $ANDROID_HOME"
  command -v zip >/dev/null && ok "zip installed" || c_ylw "zip not installed (needed for release)"
  command -v rclone >/dev/null && ok "rclone installed" || c_ylw "rclone not found (needed for upload)"
  rclone listremotes 2>/dev/null | grep -q '^onedrive:' && ok "rclone 'onedrive' remote configured" || c_ylw "no 'onedrive:' remote (run: rclone config)"
  [ -f "$WORKOUTS_JSON" ] && ok "workouts JSON at $WORKOUTS_JSON" || c_ylw "workouts JSON missing ($WORKOUTS_JSON)"
  [ -f "$GARMIN_TOKENS_JSON" ] && ok "Garmin tokens at $GARMIN_TOKENS_JSON" || c_ylw "Garmin tokens missing ($GARMIN_TOKENS_JSON)"
}

zip_garmin_tokens() {
  command -v zip >/dev/null || fail "zip not installed"
  [ -f "$GARMIN_TOKENS_JSON" ] || fail "Garmin tokens not found: $GARMIN_TOKENS_JSON"
  mkdir -p artifacts
  local token_zip="artifacts/garmin_tokens.zip"
  rm -f "$token_zip"
  zip -j "$token_zip" "$GARMIN_TOKENS_JSON" >/dev/null || fail "token zip failed"
  ok "zipped tokens.json → $(basename "$token_zip")" >&2
  printf '%s\n' "$token_zip"
}

upload_artifacts() {
  local dest="$1" apk_zip="$2" json_zip="$3" token_zip="${4:-}"
  rclone copyto "$dest" "$ONEDRIVE_DIR/$(basename "$dest")" --progress
  ok "uploaded $(basename "$dest")"
  rclone copyto "$apk_zip" "$ONEDRIVE_DIR/$(basename "$apk_zip")" --progress
  ok "uploaded $(basename "$apk_zip")"
  rclone copyto "$json_zip" "$ONEDRIVE_DIR/$(basename "$json_zip")" --progress
  ok "uploaded $(basename "$json_zip")"
  if [ -n "$token_zip" ] && [ -f "$token_zip" ]; then
    rclone copyto "$token_zip" "$ONEDRIVE_DIR/$(basename "$token_zip")" --progress
    ok "uploaded $(basename "$token_zip")"
  fi
}

zip_workouts_json() {
  command -v zip >/dev/null || fail "zip not installed"
  [ -f "$WORKOUTS_JSON" ] || fail "workouts JSON not found: $WORKOUTS_JSON"
  mkdir -p artifacts
  local json_zip="artifacts/progression_a_workouts.zip"
  rm -f "$json_zip"
  zip -j "$json_zip" "$WORKOUTS_JSON" >/dev/null || fail "json zip failed"
  ok "zipped progression_a_workouts.json → $(basename "$json_zip")" >&2
  printf '%s\n' "$json_zip"
}

cmd_release() {
  step "Pre-flight"
  require_clean_main
  pull_ff

  step "Test + build debug APK"
  cd "$REPO"
  export JAVA_HOME ANDROID_HOME
  ./gradlew test assembleDebug

  local apk
  apk="$(find app/build/outputs/apk/debug -name '*.apk' -print -quit)"
  [ -n "$apk" ] || fail "no APK under app/build/outputs/apk/debug"

  step "Copy to artifacts/"
  mkdir -p artifacts
  local dest="artifacts/$(basename "$apk")"
  cp -f "$apk" "$dest"
  ok "built $(basename "$dest")"

  step "Zip APK"
  command -v zip >/dev/null || fail "zip not installed"
  local zip="artifacts/$(basename "${dest%.apk}").zip"
  rm -f "$zip"
  zip -j "$zip" "$dest" >/dev/null || fail "zip failed"
  ok "zipped $(basename "$zip")"

  step "Zip workouts JSON"
  local json_zip token_zip
  json_zip="$(zip_workouts_json)"

  step "Zip Garmin tokens"
  if [ -f "$GARMIN_TOKENS_JSON" ]; then
    token_zip="$(zip_garmin_tokens)"
  else
    c_ylw "skipping token zip — not found at $GARMIN_TOKENS_JSON"
  fi

  step "Upload to OneDrive"
  command -v rclone >/dev/null || fail "rclone not installed"
  upload_artifacts "$dest" "$zip" "$json_zip" "$token_zip"
}

cmd_upload() {
  step "Upload existing artifacts (no rebuild)"
  command -v rclone >/dev/null || fail "rclone not installed"
  mkdir -p artifacts
  local apk dest zip json_zip token_zip
  apk="$(find artifacts app/build/outputs/apk/debug -name 'garmin-pace-charts-*.apk' 2>/dev/null | head -1)"
  [ -n "$apk" ] || fail "no garmin-pace-charts APK found — run assembleDebug first"
  dest="artifacts/$(basename "$apk")"
  [ "$apk" = "$dest" ] || cp -f "$apk" "$dest"
  zip="artifacts/$(basename "${dest%.apk}").zip"
  rm -f "$zip"
  zip -j "$zip" "$dest" >/dev/null
  json_zip="$(zip_workouts_json)"
  if [ -f "$GARMIN_TOKENS_JSON" ]; then
    token_zip="$(zip_garmin_tokens)"
  else
    c_ylw "skipping token zip — not found at $GARMIN_TOKENS_JSON"
  fi
  upload_artifacts "$dest" "$zip" "$json_zip" "$token_zip"
}

cmd_upload_tokens() {
  step "Zip + upload Garmin tokens only"
  command -v rclone >/dev/null || fail "rclone not installed"
  local token_zip
  token_zip="$(zip_garmin_tokens)"
  rclone copyto "$token_zip" "$ONEDRIVE_DIR/$(basename "$token_zip")" --progress
  ok "uploaded $(basename "$token_zip")"
}

main() {
  case "${1:-doctor}" in
    doctor)  cmd_doctor ;;
    release) cmd_release ;;
    upload)  cmd_upload ;;
    upload-tokens) cmd_upload_tokens ;;
    *) echo "usage: $0 {doctor|release|upload|upload-tokens}"; exit 2 ;;
  esac
}
main "$@"
