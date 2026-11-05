#!/bin/sh
# Aceptación de seguridad F8 para TVPlayer (Task 16).
# Uso: sh scripts/acceptance.sh [apk=app/build/outputs/apk/release/app-release-unsigned.apk]
# Requiere: apkanalyzer en PATH (cmdline-tools) + unzip + grep.
# Los checks de dex usan descriptores literales: el framework nunca se ofusca,
# así que su ausencia es prueba de no-uso; CleartextGate lleva keepnames en
# proguard-rules.pro para ser auditable por nombre.
set -eu

APK="${1:-app/build/outputs/apk/release/app-release-unsigned.apk}"
FAIL=0

pass() { echo "PASS: $1"; }
fail() { echo "FAIL: $1"; FAIL=1; }

[ -f "$APK" ] || { echo "FAIL: no existe $APK (corre :app:assembleRelease primero)"; exit 1; }
# apkanalyzer puede ser apkanalyzer.bat (git-bash no lo ve con command -v).
if ! command -v apkanalyzer >/dev/null 2>&1; then
  _sdk="${ANDROID_SDK_ROOT:-}"
  _user="${USERNAME:-${USER:-luisr}}"
  for cand in "$_sdk/cmdline-tools/latest/bin/apkanalyzer" \
              "$_sdk/cmdline-tools/latest/bin/apkanalyzer.bat" \
              "/c/Users/$_user/AppData/Local/Android/Sdk/cmdline-tools/latest/bin/apkanalyzer.bat"; do
    if [ -x "$cand" ] || [ -f "$cand" ]; then
      apkanalyzer() { "$cand" "$@"; }
      break
    fi
  done
fi
command -v apkanalyzer >/dev/null 2>&1 || [ "$(type -t apkanalyzer)" = "function" ] || { echo "FAIL: apkanalyzer no en PATH"; exit 1; }

echo "== 1. Permisos: INTERNET + librería WorkManager, nada más =="
# WorkManager inyecta estos 5 (ver Task 16 ledger); cualquier otro es fallo.
ALLOWED="android.permission.INTERNET android.permission.FOREGROUND_SERVICE android.permission.ACCESS_NETWORK_STATE android.permission.WAKE_LOCK android.permission.RECEIVE_BOOT_COMPLETED com.tvplayer.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
perms=$(apkanalyzer manifest permissions "$APK" | grep "^android.permission\|^com.tvplayer" || true)
echo "$perms"
echo "$perms" | while read -r p; do
  case " $ALLOWED " in
    *" $p "*) ;;
    *) echo "FAIL: permiso no permitido: $p"; exit 3;;
  esac
done
# subshell del while no propaga exit con set -e en todos los sh: doble check
extra=$(echo "$perms" | grep -vxF -e "android.permission.INTERNET" -e "android.permission.FOREGROUND_SERVICE" -e "android.permission.ACCESS_NETWORK_STATE" -e "android.permission.WAKE_LOCK" -e "android.permission.RECEIVE_BOOT_COMPLETED" -e "com.tvplayer.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" || true)
[ -z "$extra" ] && pass "permisos dentro de la lista cerrada" || { fail "permisos extra: $extra"; }

echo "== 2. Manifest: sin cleartext estático, allowBackup=false =="
manifest=$(apkanalyzer manifest print "$APK")
echo "$manifest" | grep -q 'usesCleartextTraffic' && fail "usesCleartextTraffic presente (el gate es runtime)" || pass "sin usesCleartextTraffic"
echo "$manifest" | grep -q 'networkSecurityConfig' && fail "networkSecurityConfig presente" || pass "sin networkSecurityConfig"
echo "$manifest" | grep -q 'allowBackup="true"' && fail "allowBackup=true" || pass "allowBackup=false"

echo "== 3. Exported: ningún componente PROPIO salvo MainActivity (launcher) =="
own_exported=$(echo "$manifest" | grep -B2 'android:exported="true"' | grep 'android:name="com.tvplayer.app' | grep -v "MainActivity" || true)
[ -z "$own_exported" ] && pass "solo MainActivity exportado (librerías: diff manual)" || fail "exportado propio: $own_exported"

echo "== 4. Dex: sin WebView/DexClassLoader; con CleartextGate =="
dexdump=$(unzip -p "$APK" 'classes*.dex')
for bad in 'Landroid/webkit/WebView;' 'Ldalvik/system/DexClassLoader;' 'Ldalvik/system/PathClassLoader;'; do
  echo "$dexdump" | grep -qF "$bad" && fail "referencia prohibida: $bad" || pass "ausente: $bad"
done
echo "$dexdump" | grep -qF 'Lcom/tvplayer/app/data/net/CleartextGate;' && pass "CleartextGate en el dex" || fail "CleartextGate ausente"

echo "== 5. SHA-256 =="
(sha256sum "$APK" 2>/dev/null || shasum -a 256 "$APK")

if [ "$FAIL" -eq 0 ]; then echo "ACCEPTANCE: TODO PASS"; else echo "ACCEPTANCE: HAY FALLOS"; fi
exit "$FAIL"
