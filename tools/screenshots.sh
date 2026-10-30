#!/usr/bin/env bash
# Opens Recompa on a connected emulator and saves the README screenshots to docs/screenshots/.
# Used by .github/workflows/screenshots.yml.
set -euo pipefail

PKG=io.github.kalasyan.recompa
OUT=docs/screenshots
mkdir -p "$OUT"

# A landscape handheld screen, like the AYN Thor's main display.
adb shell wm size 1920x1080
adb shell wm density 320
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0

adb install -r app/build/outputs/apk/debug/*.apk
adb shell appops set "$PKG" REQUEST_INSTALL_PACKAGES allow
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS

# Skip the first-launch dialog.
echo '<map><boolean name="seen" value="true" /></map>' > /tmp/welcome.xml
adb push /tmp/welcome.xml /data/local/tmp/welcome.xml
adb shell run-as "$PKG" mkdir -p shared_prefs
adb shell run-as "$PKG" cp /data/local/tmp/welcome.xml shared_prefs/welcome.xml

key() { adb shell input keyevent "$@"; sleep 2; }
shot() { sleep "${2:-4}"; adb exec-out screencap -p > "$OUT/$1.png"; echo "saved $OUT/$1.png"; }

adb shell am start -W -n "$PKG/.MainActivity"
# Let the catalog and its cover art load.
sleep 30
key KEYCODE_DPAD_RIGHT
key KEYCODE_DPAD_RIGHT
shot library 8

key KEYCODE_BUTTON_R2
key KEYCODE_BUTTON_R2
key KEYCODE_DPAD_RIGHT
shot platform 8

key KEYCODE_DPAD_CENTER
shot port 8

# Screens that show where the newer features live. Each one is optional: if a button can't be
# found (the UI changed), the step is skipped instead of failing the whole run.
ui() {
  for _ in 1 2 3; do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 && break
    sleep 2
  done
  adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null
  python3 tools/ui.py find /tmp/ui.xml "$@"
}
tap() {
  local box
  box=$(ui "$@") || { echo "not found: $*"; return 1; }
  read -r x1 y1 x2 y2 <<<"$box"
  adb shell input tap $(((x1 + x2) / 2)) $(((y1 + y2) / 2))
  sleep 4
}
# Scrolls down until LABEL is fully on screen.
scroll_to() {
  for _ in 1 2 3 4 5 6 7 8; do
    local box
    if box=$(ui "$1") && read -r _ _ _ y2 <<<"$box" && [ "$y2" -lt 1040 ]; then return 0; fi
    adb shell input swipe 960 850 960 400 500
    sleep 2
  done
  echo "not found: $1"
  return 1
}
# Saves a screenshot with an orange frame around the given labels.
marked() {
  local name=$1 box
  shift
  box=$(ui "$@") || { echo "not found: $*"; return 1; }
  shot "$name" 1
  # shellcheck disable=SC2086
  python3 tools/ui.py highlight "$OUT/$name.png" $box
}

# On the port page: link a port installed elsewhere, and send a compatibility report.
marked port-link "I already have it installed" || true
if scroll_to "Report how it runs"; then
  marked report-button "Report how it runs" || true
  tap "Report how it runs" && shot report 2 && key KEYCODE_BACK
fi

# Back in the library: the suggest and settings buttons.
key KEYCODE_BACK
sleep 3
marked toolbar "Suggest a port" "Settings" || true
if tap "Suggest a port"; then
  shot suggest 2
  key KEYCODE_BACK
fi
if tap "Settings"; then
  marked settings "Language" "English" "Deutsch" || true
  key KEYCODE_BACK
fi
