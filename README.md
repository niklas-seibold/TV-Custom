# CleanHome — minimal Fire TV home screen

A tiny launcher for the TCL 55CF630 (Fire TV Edition, Fire OS 7): dark gradient, clock,
and one row of tiles — YouTube · Netflix · Prime Video · Apple TV · PS5 (HDMI 1) · AirPlay · Settings.
Design inspired by [CleanFrame](https://github.com/nthnclrk/CleanFrame) (Samsung Tizen).

Every push to `main` builds `CleanHome.apk` with GitHub Actions and attaches it to the latest Release.

## Install — on the TV only (no computer)

1. Settings → My Fire TV → About → click the top entry **7×** → back → **Developer Options** → **ADB debugging ON**.
2. Install **Downloader** (by AFTVnews) from the Amazon Appstore.
3. Developer Options → **Install unknown apps** → **Downloader ON**.
4. Open Downloader, type `https://github.com/niklas-seibold/TV-Custom/releases/latest/download/CleanHome.apk` → Go → **Install** → **Open**.
5. CleanHome asks to finish setup → **Set up** → on the "Allow USB debugging?" prompt tick **Always allow** → **OK**.
6. Press Home on the remote to test. For AirPlay, click the AirPlay tile and install **AirScreen**.

Updating later: same link in Downloader, Install again (settings are kept).

Alternative with a computer: `bash tools/install.sh <TV-IP>`.

## Use

- D-pad to move, OK to open. Apps not yet installed open their Appstore page; sign in inside each app on first open.
- **Menu (≡)** on CleanHome = open the stock Fire TV home for 2 minutes (escape hatch).
- If the PS5 tile can't switch input, it shows a list of inputs — send a photo of it to fix in one round.

## Undo

`bash tools/uninstall.sh 192.168.x.x` — restores everything.

## Optional: shell helper (instant Home + real HDMI 1)

Fire OS blocks apps from catching Home and from switching HDMI inputs. The helper in `tools/home.sh`
runs with adb-shell rights and does both. It stops when the TV loses power; start it again from iSH:

```
wget -O home.sh https://raw.githubusercontent.com/niklas-seibold/TV-Custom/main/tools/home.sh
adb push home.sh /data/local/tmp/
adb shell "nohup sh /data/local/tmp/home.sh >/dev/null 2>&1 &"
```

Without the helper, Home works via the accessibility redirect (short delay), and the PS5 tile uses
Amazon's HDMI player if `com.amazon.tv.permission.TUNE_INPUT` could be granted, otherwise Live TV.

## Features

Boys night / Girls night modes · backgrounds (Dark, Aurora, Sunset, Ocean, daily Nature photo, your own
private photos with a cinematic grade) · screensaver after 4 min · football match button
(Champions League / World Cup / Euros) · secret code ↑↑↓↓←→←→ for Disco Mode · Menu (≡) opens the
stock Fire TV home for 2 minutes.

Photos are private: copy them to `/sdcard/Android/data/dev.cleanhome/files/` with `adb push`; they are never in this repo.

## Notes

- Fire OS sends Home to Amazon's launcher; an accessibility service (“CleanHome Home button”) instantly brings CleanHome back. A brief flash of the stock home is possible.
- Amazon updates can change this behaviour. Turning off automatic updates isn't offered by Fire OS.
- The signing key in `app/` is intentionally committed so CI builds can update in place. It only protects this personal app.
