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
