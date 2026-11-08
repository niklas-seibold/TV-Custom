# Handoff: CleanHome Fire TV launcher + HDMI ARC audio problem

You are picking up an ongoing project. Read this fully before acting. Talk to the user in English.

## Your environment
- You run on the user's Mac, on the same Wi‑Fi as the TV. `adb` is installed (`brew install android-platform-tools`).
- Connect: `adb connect 192.168.178.77:5555` (the user must accept "Allow USB debugging?" on the TV the first time; tick "Always allow").
- Repo: https://github.com/niklas-seibold/TV-Custom (public, branch `main`). Clone it.
- Be token‑efficient: the user is on a €20/month plan. Batch steps, avoid long explorations, prefer one decisive test over many guesses.

## The TV
- TCL 55CF630, **Fire TV Edition** (Fire OS 7.7.1.7, Android 9 / API 28, MediaTek SoC). Device name "Felix's 2nd TV".
- HDMI inputs: `com.mediatek.tvinput/.hdmi.HDMIInputService/HW4` … `HW7` (HW4 = HDMI 1 = PS5). A CEC child input `.../HDMI100004` exists.
- Amazon's HDMI viewer: `com.amazon.tv.inputpreference.service/com.amazon.tv.inputpreference.player.PassthroughPlayerActivity` (needs `com.amazon.tv.permission.TUNE_INPUT`, which is **signature‑only, not grantable**).
- Stock home: `com.amazon.tv.launcher/.ui.HomeActivity_vNext` (a **protected package**: cannot be disabled; `cmd package set-home-activity` "succeeds" but Fire OS ignores it).

## What we built: CleanHome (repo root)
A minimal Android launcher app (Java, no AndroidX), package `dev.cleanhome`, built by GitHub Actions on every push to `main` (`.github/workflows/build.yml`) and published as a Release asset:
`https://github.com/niklas-seibold/TV-Custom/releases/latest/download/CleanHome.apk`
The user installs updates on the TV with the **Downloader** app (same URL). The APK is signed with a committed fixed key (`app/cleanhome.keystore`) so updates install over the top. **Bump `versionCode` in `app/build.gradle` on every change.** You can also install directly: `adb install -r CleanHome.apk`.

Key files (`app/src/main/java/dev/cleanhome/`):
- `MainActivity.java`: the whole UI. Tiles: YouTube, Netflix, Prime Video, Apple TV, PS5 (HDMI 1, PlayStation logo), Settings. Top‑left text pills: **Boys night** (red neon + "GAME ON"), **Girls night** (pink + `FlowerView` flower outlines), **Background** (inline picker: Dark/Aurora/Sunset/Ocean gradients, Nature = Bing photo of the day, Photos ›: user's private photos with cinematic ColorMatrix + vignette, gear card = delete mode). Football pill under the clock (`Football.java`, ESPN public scoreboard for CL/WC/Euros + German channel rule of thumb). Screensaver after 4 min idle. Secret code ↑↑↓↓←→←→ = Disco Mode. **Menu (≡)** opens the stock Amazon home for 2 minutes (escape hatch; the user wants to keep the Amazon home accessible).
- `HomeGuardService.java`: AccessibilityService. Fire OS sends Home to Amazon's launcher; this service sees the `HomeActivity_vNext` window and starts CleanHome. Android's app‑switch protection delays that start by several seconds. `onKeyEvent` Home interception does **not** work: Amazon never delivers KEYCODE_HOME to accessibility services. The "snapshot cover" overlay was tried and removed (the user hated it).
- `Guard.java`: shared state (pause, grace window, allowed classes).
- `SelfSetup.java` / `LocalAdb.java`: ADB client (adblib) for the app to run shell commands on itself. **Fire OS refuses every on‑device ADB connection** (127.0.0.1, ::1, LAN IPv4/IPv6 all closed with 0 bytes or reset). So the app cannot get shell rights by itself.
- `tools/home.sh` (also `app/src/main/assets/home.sh`): **shell helper** run via adb (`nohup sh /data/local/tmp/home.sh &`). It reads Home presses with `getevent -qlc 3` and `am start`s CleanHome, and executes app requests written to `/sdcard/Android/data/dev.cleanhome/files/.cmd` (`hdmi1` → `input keyevent 243`, which **works** for switching to the PS5). It writes a heartbeat `.alive`. It dies when the TV loses power (unplugged several times a day), and must be restarted from iSH or the Mac.
- Accessibility service enabling (already done): `settings put secure enabled_accessibility_services dev.cleanhome/dev.cleanhome.HomeGuardService` + `accessibility_enabled 1`.
- User photos live only on the TV in `/sdcard/Android/data/dev.cleanhome/files/` (never commit them; the repo is public).

Known limits (accepted by the user for now): Home has a short delay without the helper; the PS5 tile only switches inputs while the helper runs, otherwise it falls back to Live TV. Raspberry Pi as a permanent helper host was declined "for now". Grantable development permissions (e.g. `WRITE_SECURE_SETTINGS`) persist across reboots and are a valid tool.

## THE NEW TASK: reliable TV audio to Yamaha HS8 with TV‑remote volume
Setup:
- Sources on the TV: built‑in Fire TV apps and PS5 (HDMI 1).
- Output: two active studio monitors **Yamaha HS8** (XLR / 6.3 mm TRS inputs, no remote volume).
- Adapter: **FeinTech VMA00102** HDMI ARC audio extractor (TV's ARC HDMI port → analog out to the HS8).

Problem: it works only sometimes. After power‑on the user must dance through settings (Display & Sounds → Audio output: TV speakers off; ARC mode: Auto (only one that worked); Digital audio format: PCM; HDMI CEC device control: toggle ARC/eARC on/off/on, wait). Otherwise the volume keys show **"Volume control buttons are not supported for your current audio output device"**. Currently it **does not work at all**, even after the dance. Optical/TOSLINK was rejected (fixed level, no remote volume).

Goal: a rock‑solid, plug‑and‑play result where the TV remote controls the HS8 volume. Ideally a one‑click **"Fix sound"** button in CleanHome (and/or an automatic fix at boot via `BootReceiver`) using whatever settings/commands work.

Suggested plan (keep it short):
1. `adb shell dumpsys hdmi_control`: is the FeinTech seen as a CEC Audio System (logical addr 5)? ARC state? System Audio Mode?
2. `adb shell dumpsys audio`: current output devices (HDMI_ARC, SPDIF, line/headphone?) and volume control mode (fixed vs variable). Check whether the TV has a 3.5 mm headphone/line out: that would be the simplest reliable path (TV‑controlled variable level, no CEC).
3. Snapshot `settings list global|secure|system` in broken vs working state and diff them, to find the Amazon/MediaTek keys behind "ARC mode", "HDMI CEC device control", "eARC".
4. Test toggling candidate keys (e.g. `hdmi_control_enabled`, system‑audio / ARC keys) via `adb shell settings put …` until volume control works; also test power‑cycling the FeinTech while the TV is on (handshake ordering).
5. If a settings sequence works: add `<uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS"/>` to the manifest, have the user grant it once (`adb shell pm grant dev.cleanhome android.permission.WRITE_SECURE_SETTINGS`, persists across reboots), and implement the "Fix sound" pill + auto‑run on boot.
6. If CEC/ARC with this extractor is fundamentally unreliable, recommend hardware honestly (e.g. headphone/line‑out cable + ground‑loop isolator if the TV has the jack; or an ARC streamer/preamp with variable line out such as WiiM Ultra).

Report findings plainly, say what is verified vs. guessed, and don't push changes the user didn't ask for.
