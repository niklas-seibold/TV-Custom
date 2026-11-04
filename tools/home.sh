#!/system/bin/sh
# CleanHome helper. Runs with adb-shell rights (started once from iSH / a computer).
# - Home button  -> opens CleanHome directly (shell isn't subject to Android's app-switch delay)
# - PS5 tile     -> presses the TV's own "HDMI 1" key (apps aren't allowed to)
# Stops on a full reboot; start again with:  adb shell "nohup sh /data/local/tmp/home.sh >/dev/null 2>&1 &"
# Stop it with:                             adb shell pkill -f home.sh

CMD=/sdcard/Android/data/dev.cleanhome/files/.cmd

# Requests from the app
( while true; do
    if [ -f "$CMD" ]; then
      c=$(cat "$CMD"); rm -f "$CMD"
      case "$c" in
        hdmi1)  input keyevent 243 ;;   # KEYCODE_TV_INPUT_HDMI_1
        inputs) input keyevent 178 ;;   # KEYCODE_TV_INPUT (input list)
      esac
    fi
    sleep 0.3
  done ) &

# Home button
while true; do
  getevent -ql 2>/dev/null | while read -r dev type code val; do
    case "$code" in
      KEY_HOME|KEY_HOMEPAGE)
        if [ "$val" = "UP" ]; then
          sleep 0.15
          am start -n dev.cleanhome/.MainActivity -f 0x10020000 >/dev/null 2>&1
        fi ;;
    esac
  done
  sleep 1
done
