#!/usr/bin/env bash
set -euo pipefail
collect() {
  mkdir -p screenshots
  adb pull /data/local/tmp/agenda.png screenshots/ || true
  adb pull /data/local/tmp/timetable.png screenshots/ || true
  adb pull /data/local/tmp/mood-editor.png screenshots/ || true
  adb pull /data/local/tmp/mood-timeline.png screenshots/ || true
  adb pull /data/local/tmp/repeat-editor.png screenshots/ || true
  adb pull /data/local/tmp/multi-day-course.png screenshots/ || true
  adb pull /data/local/tmp/rollover-subtasks.png screenshots/ || true
  adb pull /data/local/tmp/ai-confirmation-card.png screenshots/ || true
  adb logcat -d -s AndroidRuntime:E > screenshots/crashes.txt || true
}
trap collect EXIT
gradle --no-daemon connectedDebugAndroidTest
trap - EXIT
collect
test -s screenshots/agenda.png
test -s screenshots/timetable.png
