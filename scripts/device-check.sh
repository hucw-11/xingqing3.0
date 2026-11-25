#!/usr/bin/env bash
set -euo pipefail
collect() {
  mkdir -p screenshots
  adb pull /sdcard/Android/data/cn.campus.schedule/files/. screenshots/ || true
  adb logcat -d -s AndroidRuntime:E > screenshots/crashes.txt || true
}
trap collect EXIT
gradle --no-daemon connectedDebugAndroidTest
