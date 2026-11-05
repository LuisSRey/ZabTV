#!/bin/sh
# Perf check de dispositivo para TVPlayer (Task 15, spec §6.7).
# Requiere: adb en PATH, un dispositivo/fire stick conectado y el APK instalado.
# Uso: sh scripts/perf_check.sh [package=com.tvplayer.app]
# Guarda la tabla final en docs/perf_results/<fecha>.md (plantilla en README).
set -eu

PKG="${1:-com.tvplayer.app}"
OUT="docs/perf_results/$(date +%F).md"
ACTIVITY="$PKG/.MainActivity"

echo "== cold start =="
am_start=$(adb shell am start -W "$ACTIVITY" | grep -E "TotalTime|WaitTime" || true)
echo "$am_start"

echo "== meminfo a 5 min (PSS) =="
sleep 300
adb shell dumpsys meminfo "$PKG" | grep -E "TOTAL|Native Heap|Dalvik Heap" || true

echo "== gfxinfo 60 s (jank) =="
adb shell dumpsys gfxinfo "$PKG" reset > /dev/null
sleep 60
adb shell dumpsys gfxinfo "$PKG" | grep -A5 "Janky frames" || true

echo "== ANRs durante la ventana =="
adb logcat -b events -d | grep -i anr || echo "sin ANR"

{
  echo "# perf $(date -Iseconds) — $PKG"
  echo
  echo '```'
  echo "$am_start"
  echo '```'
} > "$OUT"
echo "tabla base en $OUT (completar a mano con meminfo/gfxinfo)"
