#!/usr/bin/env bash
# Diagnostics belong to this fresh CI emulator, never to a user's device.
set -u
stage="${1:-core}"
case "$stage" in core|tcp) ;; *) echo "Unknown acceptance stage: $stage" >&2; exit 2 ;; esac
diagnostics=app/build/acceptance-diagnostics
mkdir -p "$diagnostics"
# Host-only observer: never reconnect, restart or kill the emulator.
emulator_pids="$(pgrep -f '[/]emulator/qemu/.*qemu-system|[/]emulator/emulator([[:space:]]|$)' || true)"
printf '%s\n' "$emulator_pids" > "$diagnostics/emulator-pids.txt"
(
  while :; do
    date -u +'%Y-%m-%dT%H:%M:%S.%NZ'
    for pid in $emulator_pids; do
      if [ -r "/proc/$pid/stat" ]; then
        cat "/proc/$pid/stat"
      else
        printf 'EMULATOR_PID_MISSING=%s\n' "$pid"
      fi
    done
    sleep 1
  done
) > "$diagnostics/emulator-lifecycle.txt" 2>&1 &
observer_pid=$!
# Capture the host crash stack without changing Android or the renderer.
debugger_pids=""
if [ "$stage" = core ] && [ "${TRACE_EMULATOR:-0}" = 1 ]; then
  for pid in $emulator_pids; do
    timeout --signal=TERM --kill-after=5s 420s sudo -n gdb --batch --nx -p "$pid" \
      -ex "set pagination off" -ex "set confirm off" \
      -ex "set debuginfod enabled off" \
      -ex "handle SIGPIPE nostop noprint pass" \
      -ex "handle SIGSEGV stop print pass" \
      -ex "shell touch $diagnostics/debugger-attached-$pid" \
      -ex "continue" -ex "bt 40" -ex "thread apply all bt 20" -ex "detach" \
      > "$diagnostics/emulator-backtrace-$pid.txt" 2>&1 &
    debugger_pids="$debugger_pids $!"
    for attempt in $(seq 1 15); do
      [ -f "$diagnostics/debugger-attached-$pid" ] && break
      sleep 1
    done
    if [ ! -f "$diagnostics/debugger-attached-$pid" ]; then
      echo "Debugger failed to attach to emulator $pid"
      cat "$diagnostics/emulator-backtrace-$pid.txt"
    fi
  done
fi
cleanup_observers() {
  kill "$observer_pid" 2>/dev/null || true
  wait "$observer_pid" 2>/dev/null || true
  for debugger_pid in $debugger_pids; do
    kill "$debugger_pid" 2>/dev/null || true
    wait "$debugger_pid" 2>/dev/null || true
  done
}
trap cleanup_observers EXIT
boundary() {
  local label="$1" status="${2:-NA}" adb_status
  {
    printf '\nBOUNDARY=%s UTC=%s COMMAND_EXIT=%s\n' "$label" "$(date -u +'%Y-%m-%dT%H:%M:%S.%NZ')" "$status"
    for pid in $emulator_pids; do
      if [ -r "/proc/$pid/stat" ]; then
        cat "/proc/$pid/stat"
      else
        printf 'EMULATOR_PID_MISSING=%s\n' "$pid"
      fi
    done
    timeout 5s adb get-state
    adb_status=$?
    printf 'ADB_STATE_EXIT=%s\n' "$adb_status"
  } >> "$diagnostics/boundaries.txt" 2>&1
  printf 'Acceptance boundary: %s (exit %s)\n' "$label" "$status"
}
boundary script-start
free -m > app/build/acceptance-diagnostics/memory-before.txt
adb logcat -c
adb logcat -v threadtime > app/build/acceptance-diagnostics/logcat.txt 2>&1 &
log_pid=$!
# Instrumentation saves screenshots after presented frames. Avoid an independent
# screencap process racing graphics-surface destruction during Activity/process restore.
# Core and process restoration share an emulator; TCP has its own fresh CI job.
# Gradle builds the APKs before startup; no UTP teardown runs between stages.
result=0
adb install -r app/build/outputs/apk/debug/app-debug.apk || result=1
if [ "$result" -eq 0 ]; then
  adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk || result=1
fi
if [ "$result" -eq 0 ] && [ "$stage" = core ]; then
  boundary core-start
  timeout 300s adb shell am instrument -w -e class mx.youteachtk.epsonrasimulator.LocalProjectUiAcceptanceTest mx.youteachtk.epsonrasimulator.test/androidx.test.runner.AndroidJUnitRunner > app/build/acceptance-diagnostics/core-ui.txt 2>&1
  core_status=$?
  boundary core-finished "$core_status"
  [ "$core_status" -eq 0 ] || result=1
  sed '/EVIDENCE_PNG=/d' app/build/acceptance-diagnostics/core-ui.txt
  if ! grep -q 'OK (1 test)' app/build/acceptance-diagnostics/core-ui.txt || ! grep -q 'CORE_UI_VERIFIED' app/build/acceptance-diagnostics/core-ui.txt; then
    sed '/EVIDENCE_PNG=/d' app/build/acceptance-diagnostics/core-ui.txt
    result=1
  fi
fi
if [ "$result" -eq 0 ] && [ "$stage" = core ]; then
  # Preserve core evidence before the process boundary that can disconnect the emulator.
  boundary evidence-pull-start
  timeout 15s adb pull /sdcard/Android/data/mx.youteachtk.epsonrasimulator/files/acceptance app/build/acceptance-diagnostics/core-steps > "$diagnostics/core-pull.txt" 2>&1
  pull_status=$?
  boundary evidence-pull-finished "$pull_status"
  ps -eo pid,ppid,stat,rss,comm > app/build/acceptance-diagnostics/processes-before-restore.txt
  boundary force-stop-start
  timeout 10s adb shell am force-stop mx.youteachtk.epsonrasimulator > "$diagnostics/force-stop.txt" 2>&1
  stop_status=$?
  boundary force-stop-finished "$stop_status"
  [ "$stop_status" -eq 0 ] || result=1
  boundary restore-start
  timeout 90s adb shell am instrument -w -e verifyProcessRestore true -e class mx.youteachtk.epsonrasimulator.LocalProjectProcessRestoreTest mx.youteachtk.epsonrasimulator.test/androidx.test.runner.AndroidJUnitRunner > app/build/acceptance-diagnostics/process-restore.txt 2>&1
  restore_status=$?
  boundary restore-finished "$restore_status"
  [ "$restore_status" -eq 0 ] || result=1
  cat app/build/acceptance-diagnostics/process-restore.txt
  if ! grep -q 'OK (1 test)' app/build/acceptance-diagnostics/process-restore.txt || ! grep -q 'PROCESS_RESTORE_VERIFIED' app/build/acceptance-diagnostics/process-restore.txt; then
    cat app/build/acceptance-diagnostics/process-restore.txt
    result=1
  fi
fi
if [ "$result" -eq 0 ] && [ "$stage" = tcp ]; then
  adb shell screenrecord --time-limit 180 /sdcard/tcp-preview.mp4 > app/build/acceptance-diagnostics/screenrecord.txt 2>&1 &
  record_pid=$!
  adb shell am instrument -w -e class mx.youteachtk.epsonrasimulator.TcpPreviewUiAcceptanceTest mx.youteachtk.epsonrasimulator.test/androidx.test.runner.AndroidJUnitRunner > app/build/acceptance-diagnostics/tcp-preview.txt 2>&1
  if ! grep -q 'OK (1 test)' app/build/acceptance-diagnostics/tcp-preview.txt || ! grep -q 'TCP_PREVIEW_VERIFIED' app/build/acceptance-diagnostics/tcp-preview.txt; then
    sed '/EVIDENCE_PNG=/d' app/build/acceptance-diagnostics/tcp-preview.txt
    result=1
  fi
  wait "$record_pid" || true
  adb pull /sdcard/tcp-preview.mp4 app/build/acceptance-diagnostics/tcp-preview.mp4 >/dev/null 2>&1 || true
fi
# Reconstruct screenshots already delivered while the test process was alive.
python3 .github/scripts/decode-acceptance-evidence.py app/build/acceptance-diagnostics || result=1
timeout 15s adb pull /sdcard/Android/data/mx.youteachtk.epsonrasimulator/files/acceptance app/build/acceptance-diagnostics/steps >/dev/null 2>&1 || true
kill "$log_pid" 2>/dev/null || true
wait "$log_pid" 2>/dev/null || true
ps -eo pid,ppid,stat,rss,comm > app/build/acceptance-diagnostics/processes-after.txt
free -m > app/build/acceptance-diagnostics/memory-after.txt
sudo dmesg --ctime > app/build/acceptance-diagnostics/kernel.txt 2>&1 || true
timeout 5s adb devices -l > app/build/acceptance-diagnostics/devices-after.txt 2>&1 || true
# Crashpad/minidump evidence on this disposable CI host. Do not copy AVDs or adb keys.
mkdir -p "$diagnostics/emulator-crashes"
timeout 15s find /tmp -maxdepth 5 -type f \
  \( -name '*.dmp' -o -name 'emu-crash*' \) -size -20M -print \
  > "$diagnostics/crash-files.txt" 2> "$diagnostics/crash-scan-errors.txt" || true
while IFS= read -r crash_file; do
  [ -f "$crash_file" ] || continue
  timeout 5s cp --parents -- "$crash_file" "$diagnostics/emulator-crashes/" || true
done < "$diagnostics/crash-files.txt"
timeout 10s coredumpctl --no-pager list > "$diagnostics/host-coredumps.txt" 2>&1 || true
boundary script-finished "$result"
cat "$diagnostics/boundaries.txt"
for trace in "$diagnostics"/emulator-backtrace-*.txt; do
  [ -f "$trace" ] || continue
  printf '\nHOST EMULATOR BACKTRACE: %s\n' "$trace"
  cat "$trace"
done
printf '\nHOST KERNEL CRASH EVIDENCE\n'
grep -Ei "segfault|qemu|oom|killed process" "$diagnostics/kernel.txt" || true
printf '\nHOST COREDUMP INVENTORY\n'
cat "$diagnostics/host-coredumps.txt" "$diagnostics/crash-files.txt"
exit "$result"
