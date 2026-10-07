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
# Keep one externally owned emulator across all three instrumentation processes.
# Gradle builds the APKs before startup; no UTP teardown runs between stages.
result=0
adb install -r app/build/outputs/apk/debug/app-debug.apk || result=1
if [ "$result" -eq 0 ]; then
  adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk || result=1
fi
if [ "$result" -eq 0 ]; then
  adb shell am instrument -w -e class mx.youteachtk.epsonrasimulator.LocalProjectUiAcceptanceTest mx.youteachtk.epsonrasimulator.test/androidx.test.runner.AndroidJUnitRunner > app/build/acceptance-diagnostics/core-ui.txt 2>&1
  if ! grep -q 'OK (1 test)' app/build/acceptance-diagnostics/core-ui.txt || ! grep -q 'CORE_UI_VERIFIED' app/build/acceptance-diagnostics/core-ui.txt; then
    cat app/build/acceptance-diagnostics/core-ui.txt
    result=1
  fi
fi
if [ "$result" -eq 0 ]; then
  adb shell am force-stop mx.youteachtk.epsonrasimulator
  adb shell am instrument -w -e verifyProcessRestore true -e class mx.youteachtk.epsonrasimulator.LocalProjectProcessRestoreTest mx.youteachtk.epsonrasimulator.test/androidx.test.runner.AndroidJUnitRunner > app/build/acceptance-diagnostics/process-restore.txt 2>&1
  if ! grep -q 'OK (1 test)' app/build/acceptance-diagnostics/process-restore.txt || ! grep -q 'PROCESS_RESTORE_VERIFIED' app/build/acceptance-diagnostics/process-restore.txt; then
    cat app/build/acceptance-diagnostics/process-restore.txt
    result=1
  fi
fi
if [ "$result" -eq 0 ]; then
  adb shell screenrecord --time-limit 180 /sdcard/tcp-preview.mp4 > app/build/acceptance-diagnostics/screenrecord.txt 2>&1 &
  record_pid=$!
  adb shell am instrument -w -e class mx.youteachtk.epsonrasimulator.TcpPreviewUiAcceptanceTest mx.youteachtk.epsonrasimulator.test/androidx.test.runner.AndroidJUnitRunner > app/build/acceptance-diagnostics/tcp-preview.txt 2>&1
  if ! grep -q 'OK (1 test)' app/build/acceptance-diagnostics/tcp-preview.txt || ! grep -q 'TCP_PREVIEW_VERIFIED' app/build/acceptance-diagnostics/tcp-preview.txt; then
    cat app/build/acceptance-diagnostics/tcp-preview.txt
    result=1
  fi
  wait "$record_pid" || true
  adb pull /sdcard/tcp-preview.mp4 app/build/acceptance-diagnostics/tcp-preview.mp4 >/dev/null 2>&1 || true
fi
adb pull /sdcard/Android/data/mx.youteachtk.epsonrasimulator/files/acceptance app/build/acceptance-diagnostics/steps >/dev/null 2>&1 || true
kill "$log_pid" "$screen_pid" 2>/dev/null || true
wait "$log_pid" "$screen_pid" 2>/dev/null || true
free -m > app/build/acceptance-diagnostics/memory-after.txt
sudo dmesg --ctime > app/build/acceptance-diagnostics/kernel.txt 2>&1 || true
adb devices -l > app/build/acceptance-diagnostics/devices-after.txt 2>&1 || true
exit "$result"
