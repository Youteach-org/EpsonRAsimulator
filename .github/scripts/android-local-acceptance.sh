#!/usr/bin/env bash
# Diagnostics belong to this fresh CI emulator, never to a user's device.
set -u
mkdir -p app/build/acceptance-diagnostics
free -m > app/build/acceptance-diagnostics/memory-before.txt
adb logcat -c
adb logcat -v threadtime > app/build/acceptance-diagnostics/logcat.txt 2>&1 &
log_pid=$!
(
  for frame in $(seq 1 200); do
    if ! adb get-state >/dev/null 2>&1; then break; fi
    adb exec-out screencap -p > "app/build/acceptance-diagnostics/screen-${frame}.png" 2>/dev/null || break
    adb pull /sdcard/Android/data/mx.youteachtk.epsonrasimulator/files/acceptance app/build/acceptance-diagnostics/steps >/dev/null 2>&1 || true
    sleep 3
  done
) &
screen_pid=$!
gradle connectedDebugAndroidTest --stacktrace
result=$?
adb pull /sdcard/Android/data/mx.youteachtk.epsonrasimulator/files/acceptance app/build/acceptance-diagnostics/steps >/dev/null 2>&1 || true
kill "$log_pid" "$screen_pid" 2>/dev/null || true
wait "$log_pid" "$screen_pid" 2>/dev/null || true
free -m > app/build/acceptance-diagnostics/memory-after.txt
sudo dmesg --ctime > app/build/acceptance-diagnostics/kernel.txt 2>&1 || true
adb devices -l > app/build/acceptance-diagnostics/devices-after.txt 2>&1 || true
exit "$result"
