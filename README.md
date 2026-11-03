# CleanHome — minimal Fire TV home screen

A tiny launcher for the TCL 55CF630 (Fire TV Edition, Fire OS 7): dark gradient, clock,
and one row of tiles — YouTube · Netflix · Prime Video · Apple TV · PS5 (HDMI 1) · AirPlay · Settings.
Design inspired by [CleanFrame](https://github.com/nthnclrk/CleanFrame) (Samsung Tizen).

Every push to `main` builds `CleanHome.apk` with GitHub Actions and attaches it to the latest Release.

## Install (once, ~5 min)

1. **TV:** Settings → My Fire TV → About → click the top entry 7× → back → Developer Options:
   *ADB debugging* **ON**, *Apps from Unknown Sources* **ON**. Note the IP under About → Network.
2. **Mac:** `brew install android-platform-tools` (Windows: install "SDK Platform Tools" from Google and use the same commands).
3. Download `tools/install.sh` and run: `bash install.sh 192.168.x.x`
   Accept the "Allow USB debugging?" prompt on the TV (tick *Always allow*).
4. For AirPlay, install **AirScreen** from the Amazon Appstore (or just click the AirPlay tile).

## Use

- D-pad to move, OK to open. Apps not yet installed open their Appstore page; sign in inside each app on first open.
- **Menu (≡)** on CleanHome = open the stock Fire TV home for 2 minutes (escape hatch).
- If the PS5 tile can't switch input, it shows a list of inputs — send a photo of it to fix in one round.

## Undo

`bash tools/uninstall.sh 192.168.x.x` — restores everything.

## Notes

- Fire OS sends Home to Amazon's launcher; an accessibility service (“CleanHome Home button”) instantly brings CleanHome back. A brief flash of the stock home is possible.
- Amazon updates can change this behaviour. Turning off automatic updates isn't offered by Fire OS.
- The signing key in `app/` is intentionally committed so CI builds can update in place. It only protects this personal app.
