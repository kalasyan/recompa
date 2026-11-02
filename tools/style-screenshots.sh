#!/usr/bin/env bash
# Opens Recompa once per app style on a connected emulator and saves the library and a port
# page and Settings of each to style-screenshots/. Used by .github/workflows/style-screenshots.yml.
set -euo pipefail

PKG=io.github.kalasyan.recompa
OUT=style-screenshots
mkdir -p "$OUT"

adb shell wm size 1920x1080
adb shell wm density 320
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0

adb install -r app/build/outputs/apk/debug/*.apk
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS

put_pref() { # file, xml
  echo "$2" > "/tmp/$1"
  adb push "/tmp/$1" "/data/local/tmp/$1" >/dev/null
  adb shell run-as "$PKG" mkdir -p shared_prefs
  adb shell run-as "$PKG" cp "/data/local/tmp/$1" "shared_prefs/$1"
}
key() { adb shell input keyevent "$@"; sleep 2; }
tap() { # label: taps the middle of the node with that text or content description
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null
  read -r x1 y1 x2 y2 < <(python3 tools/ui.py find /tmp/ui.xml "$1")
  adb shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
}
shot() { sleep "${2:-4}"; adb exec-out screencap -p > "$OUT/$1.png"; echo "saved $OUT/$1.png"; }

put_pref welcome.xml '<map><boolean name="seen" value="true" /></map>'
first=1
for style in classic shelves list arcade mosaic showcase; do
  adb shell am force-stop "$PKG"
  put_pref user_prefs.xml "<map><string name=\"style\">$style</string><boolean name=\"second_screen\" value=\"false\" /><boolean name=\"asked_notification_permission\" value=\"true\" /></map>"
  adb shell am start -W -n "$PKG/.MainActivity" >/dev/null
  # The first start downloads the catalog and its art.
  if [ "$first" = 1 ]; then sleep 40; first=0; else sleep 15; fi
  key KEYCODE_DPAD_RIGHT
  key KEYCODE_DPAD_RIGHT
  shot "$style-library" 8
  key KEYCODE_DPAD_CENTER
  shot "$style-port" 8
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$PKG/.MainActivity" >/dev/null
  sleep 12
  tap Settings || true
  shot "$style-settings" 4
  key KEYCODE_DPAD_DOWN
  key KEYCODE_DPAD_DOWN
  shot "$style-settings-2" 2
  key KEYCODE_BACK
done
