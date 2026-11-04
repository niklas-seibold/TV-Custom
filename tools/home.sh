#!/system/bin/sh
# CleanHome helper, runs with adb-shell rights. Started automatically by CleanHome (or from iSH).
D=/sdcard/Android/data/dev.cleanhome/files
CMD=$D/.cmd

# Requests from the app + heartbeat
( while true; do
    touch "$D/.alive"
    if [ -f "$CMD" ]; then
      c=$(cat "$CMD"); rm -f "$CMD"
      case "$c" in
        hdmi1)  input keyevent 243 ;;
        inputs) input keyevent 178 ;;
      esac
    fi
    sleep 0.3
  done ) &

# Home button. getevent buffers its output when piped, so read a few events at a time instead.
while true; do
  ev=$(getevent -qlc 3 2>/dev/null)
  if echo "$ev" | grep -qE "KEY_HOME(PAGE)? +UP"; then
    sleep 0.15
    am start -n dev.cleanhome/.MainActivity -f 0x10020000 >/dev/null 2>&1
  fi
done
