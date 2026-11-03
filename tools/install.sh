#!/usr/bin/env bash
# One-shot installer for CleanHome on a Fire TV (Mac / Linux).
# Usage: ./install.sh <TV-IP-address> [path/to/CleanHome.apk]
set -euo pipefail
IP="${1:?Usage: ./install.sh <TV-IP> [CleanHome.apk]}"
APK="${2:-CleanHome.apk}"
URL="https://github.com/niklas-seibold/TV-Custom/releases/latest/download/CleanHome.apk"
PKG=dev.cleanhome
SVC="$PKG/$PKG.HomeGuardService"

command -v adb >/dev/null || { echo "adb missing. Mac: brew install android-platform-tools"; exit 1; }

if [ ! -f "$APK" ]; then
  echo "Downloading CleanHome.apk ..."
  curl -fL -o "$APK" "$URL" || { echo "Download failed. Download CleanHome.apk from the GitHub Releases page and pass its path."; exit 1; }
fi

echo "Connecting to $IP — press 'Allow' on the TV if asked (tick 'Always allow')."
adb connect "$IP:5555"
adb -s "$IP:5555" wait-for-device
A="adb -s $IP:5555"

echo "Installing ..."
$A install -r "$APK"

echo "Enabling the Home-button guard ..."
CUR=$($A shell settings get secure enabled_accessibility_services | tr -d '\r')
if [ -z "$CUR" ] || [ "$CUR" = "null" ]; then NEW="$SVC"
elif echo "$CUR" | grep -q "$SVC"; then NEW="$CUR"
else NEW="$CUR:$SVC"; fi
$A shell settings put secure enabled_accessibility_services "$NEW"
$A shell settings put secure accessibility_enabled 1

echo "Turning off ad tracking + disabling Amazon ad/telemetry packages (failures are harmless) ..."
$A shell settings put secure limit_ad_tracking 1 || true
for p in com.amazon.kso com.amazon.device.crashmanager com.amazon.client.metrics; do
  $A shell pm disable-user --user 0 "$p" >/dev/null 2>&1 && echo "  disabled $p" || echo "  skipped $p"
done

$A shell am start -n "$PKG/.MainActivity" >/dev/null
echo "Done. Press Home on the remote to test."
