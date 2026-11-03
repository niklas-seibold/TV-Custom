#!/usr/bin/env bash
# Puts the TV back to stock. Usage: ./uninstall.sh <TV-IP>
IP="${1:?Usage: ./uninstall.sh <TV-IP>}"
A="adb -s $IP:5555"
adb connect "$IP:5555"
$A shell settings put secure enabled_accessibility_services '""'
for p in com.amazon.kso com.amazon.device.crashmanager com.amazon.client.metrics; do $A shell pm enable "$p" || true; done
$A uninstall dev.cleanhome
echo "Back to stock."
