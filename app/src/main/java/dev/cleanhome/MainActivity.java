package dev.cleanhome;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.view.animation.OvershootInterpolator;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {

    /** label, then candidate package names (first installed one wins). */
    private static final String[][] APPS = {
            {"YouTube", "com.amazon.firetv.youtube", "com.google.android.youtube.tv"},
            {"Netflix", "com.netflix.ninja"},
            {"Prime Video", "com.amazon.firebat", "com.amazon.avod", "com.amazon.avod.thirdpartyclient"},
            {"Apple TV", "com.apple.atve.amazon.appletv"},
    };
    private static final String AIRSCREEN = "com.ionitech.airscreen";

    private LinearLayout row;
    private int focusIndex = 0;
    private float density;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        row = findViewById(R.id.row);
        density = getResources().getDisplayMetrics().density;
        root = findViewById(R.id.root);
        gameOn = findViewById(R.id.gameOn);
        nightBtn = findViewById(R.id.nightBtn);
        try {
            night = getPreferences(MODE_PRIVATE).getBoolean("night", false);
        } catch (Exception ignored) {
        }
        bgImage = findViewById(R.id.bgImage);
        scrim = findViewById(R.id.scrim);
        bgBtn = findViewById(R.id.bgBtn);
        bgIndex = getPreferences(MODE_PRIVATE).getInt("bg", 0);
        bgBtn.setOnClickListener(v -> {
            bgIndex = (bgIndex + 1) % BG_NAMES.length;
            getPreferences(MODE_PRIVATE).edit().putInt("bg", bgIndex).apply();
            if (night) {
                night = false;
                getPreferences(MODE_PRIVATE).edit().putBoolean("night", false).apply();
                buildRow();
                applyNight(false);
            } else {
                applyBackground();
            }
        });
        nightBtn.setOnClickListener(v -> {
            night = !night;
            getPreferences(MODE_PRIVATE).edit().putBoolean("night", night).apply();
            if (night) focusIndex = 4; // PS5
            buildRow();
            applyNight(true);
        });
    }

    // ---------------------------------------------------------------- backgrounds

    private static final String[] BG_NAMES = {"Dark", "Aurora", "Sunset", "Ocean", "Nature"};
    private static final int[] BG_RES = {R.drawable.bg_gradient, R.drawable.bg_aurora,
            R.drawable.bg_sunset, R.drawable.bg_ocean, R.drawable.bg_gradient};
    private static final int NATURE = 4;
    private int bgIndex = 0;
    private ImageView bgImage;
    private View scrim;
    private TextView bgBtn;
    private ObjectAnimator kenBurns;

    private void applyBackground() {
        if (bgIndex < 0 || bgIndex >= BG_NAMES.length) bgIndex = 0;
        bgBtn.setText("Background: " + BG_NAMES[bgIndex]);
        if (kenBurns != null) kenBurns.cancel();
        if (night) {
            root.setBackgroundResource(R.drawable.bg_night);
            bgImage.setVisibility(View.GONE);
            scrim.setVisibility(View.GONE);
            return;
        }
        root.setBackgroundResource(BG_RES[bgIndex]);
        if (bgIndex != NATURE) {
            bgImage.setVisibility(View.GONE);
            scrim.setVisibility(View.GONE);
            return;
        }
        final File f = new File(getCacheDir(), "nature-" + (System.currentTimeMillis() / 86_400_000L) + ".jpg");
        if (f.exists()) {
            showNature(f);
            return;
        }
        new Thread(() -> {
            boolean ok = downloadNature(f);
            runOnUiThread(() -> {
                if (ok && !night && bgIndex == NATURE) showNature(f);
                else if (!ok) Toast.makeText(this, "Couldn't load today's photo", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void showNature(File f) {
        Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
        if (bmp == null) return;
        bgImage.setImageBitmap(bmp);
        bgImage.setAlpha(0f);
        bgImage.setVisibility(View.VISIBLE);
        scrim.setVisibility(View.VISIBLE);
        bgImage.animate().alpha(1f).setDuration(800).start();
        // Slow "Ken Burns" drift.
        bgImage.setScaleX(1f);
        bgImage.setScaleY(1f);
        android.animation.PropertyValuesHolder sx = android.animation.PropertyValuesHolder.ofFloat("scaleX", 1f, 1.08f);
        android.animation.PropertyValuesHolder sy = android.animation.PropertyValuesHolder.ofFloat("scaleY", 1f, 1.08f);
        kenBurns = ObjectAnimator.ofPropertyValuesHolder(bgImage, sx, sy);
        kenBurns.setDuration(40_000);
        kenBurns.setRepeatMode(ValueAnimator.REVERSE);
        kenBurns.setRepeatCount(ValueAnimator.INFINITE);
        kenBurns.start();
    }

    /** Bing's photo of the day (nearly always a landscape), cached per day. */
    private boolean downloadNature(File out) {
        try {
            String json = new String(readUrl("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=en-US"), "UTF-8");
            String path = new org.json.JSONObject(json).getJSONArray("images").getJSONObject(0).getString("url");
            byte[] img = readUrl(path.startsWith("http") ? path : "https://www.bing.com" + path);
            File[] old = getCacheDir().listFiles();
            if (old != null) for (File o : old) if (o.getName().startsWith("nature-")) o.delete();
            java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
            fos.write(img);
            fos.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] readUrl(String url) throws Exception {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        try (java.io.InputStream in = c.getInputStream()) {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    // ---------------------------------------------------------------- boys night mode

    private boolean night = false;
    private View root;
    private TextView gameOn, nightBtn;
    private final List<Animator> nightAnims = new ArrayList<>();

    private void stopNightAnims() {
        for (Animator a : nightAnims) a.cancel();
        nightAnims.clear();
    }

    private void applyNight(boolean animate) {
        stopNightAnims();
        applyBackground();
        nightBtn.setText(night ? "\u25CF  Boys Night ON" : "Boys Night");
        nightBtn.setTextColor(night ? 0xFFFF4A4A : Color.WHITE);
        nightBtn.setShadowLayer(night ? 18 : 0, 0, 0, 0xFFFF1A1A);

        if (!night) {
            gameOn.setVisibility(View.GONE);
            for (int i = 0; i < row.getChildCount(); i++) {
                View c = row.getChildAt(i);
                c.setScaleX(1f);
                c.setScaleY(1f);
                c.setTranslationY(0);
                c.setAlpha(1f);
            }
            return;
        }

        gameOn.setVisibility(View.VISIBLE);
        // Neon pulse that runs forever (glow radius + brightness breathe).
        ValueAnimator pulse = ValueAnimator.ofFloat(0f, 1f);
        pulse.setDuration(1800);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            gameOn.setShadowLayer(25 + 35 * f, 0, 0, 0xFFFF1A1A);
            gameOn.setAlpha(0.45f + 0.35f * f);
        });

        if (animate) {
            // Slam in, then a neon-tube flicker, then pulse.
            gameOn.setScaleX(2.4f);
            gameOn.setScaleY(2.4f);
            gameOn.setAlpha(0f);
            AnimatorSet slam = new AnimatorSet();
            slam.playTogether(
                    ObjectAnimator.ofFloat(gameOn, "scaleX", 2.4f, 1f),
                    ObjectAnimator.ofFloat(gameOn, "scaleY", 2.4f, 1f),
                    ObjectAnimator.ofFloat(gameOn, "alpha", 0f, 1f));
            slam.setInterpolator(new OvershootInterpolator(1.6f));
            slam.setDuration(520);
            ObjectAnimator flicker = ObjectAnimator.ofFloat(gameOn, "alpha", 1f, 0.15f, 1f, 0.3f, 0.9f, 0.2f, 1f, 0.6f);
            flicker.setDuration(750);
            AnimatorSet seq = new AnimatorSet();
            seq.playSequentially(slam, flicker, pulse);
            nightAnims.add(seq);
            seq.start();

            // Tiles drop in one after another with a bounce.
            for (int i = 0; i < row.getChildCount(); i++) {
                View c = row.getChildAt(i);
                c.setTranslationY(dp(60));
                c.setAlpha(0f);
                c.animate().translationY(0).alpha(1f).setStartDelay(250 + i * 70L).setDuration(450)
                        .setInterpolator(new OvershootInterpolator(2f)).start();
            }
        } else {
            gameOn.setScaleX(1f);
            gameOn.setScaleY(1f);
            nightAnims.add(pulse);
            pulse.start();
        }

        // Subtle staggered "breathing" on every tile.
        for (int i = 0; i < row.getChildCount(); i++) {
            View c = row.getChildAt(i);
            AnimatorSet breathe = new AnimatorSet();
            ObjectAnimator sx = ObjectAnimator.ofFloat(c, "scaleX", 1f, 1.035f);
            ObjectAnimator sy = ObjectAnimator.ofFloat(c, "scaleY", 1f, 1.035f);
            for (ObjectAnimator o : new ObjectAnimator[]{sx, sy}) {
                o.setRepeatMode(ValueAnimator.REVERSE);
                o.setRepeatCount(ValueAnimator.INFINITE);
                o.setDuration(1400);
            }
            breathe.playTogether(sx, sy);
            breathe.setStartDelay((animate ? 900 : 0) + i * 160L);
            nightAnims.add(breathe);
            breathe.start();
        }
    }

    @Override
    protected void onPause() {
        stopNightAnims();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        buildRow(); // rebuild so apps installed/updated meanwhile show their artwork
        applyNight(false);
        if (!setupBusy && setupDialog == null && !SelfSetup.guardEnabled(this)) offerSetup();
    }

    // ---------------------------------------------------------------- one-time setup on the TV

    private boolean setupBusy = false;
    private AlertDialog setupDialog;

    private void offerSetup() {
        setupDialog = new AlertDialog.Builder(this)
                .setTitle("Finish CleanHome setup")
                .setMessage("This makes the Home button open CleanHome and turns off ad tracking.\n\n"
                        + "Press Set up. The TV will then ask \"Allow USB debugging?\" — tick \"Always allow\" and press OK.")
                .setPositiveButton("Set up", (d, w) -> startSetup())
                .setNegativeButton("Later", null)
                .setOnDismissListener(d -> setupDialog = null)
                .show();
    }

    private void startSetup() {
        setupBusy = true;
        final AlertDialog waiting = new AlertDialog.Builder(this)
                .setTitle("Setting up…")
                .setMessage("If the TV asks \"Allow USB debugging?\", tick \"Always allow\" and press OK.")
                .setCancelable(false)
                .show();
        new Thread(() -> {
            final String err = SelfSetup.run(getApplicationContext());
            runOnUiThread(() -> {
                setupBusy = false;
                try { waiting.dismiss(); } catch (Exception ignored) {}
                if (isFinishing()) return;
                if (err == null) {
                    Toast.makeText(this, "All set. Press Home to try it.", Toast.LENGTH_LONG).show();
                } else {
                    setupDialog = new AlertDialog.Builder(this)
                            .setTitle("Setup didn't finish")
                            .setMessage(err)
                            .setPositiveButton("Try again", (d, w) -> startSetup())
                            .setNegativeButton("Open Settings", (d, w) -> {
                                Guard.graceFor(4000);
                                safeStart(new Intent(Settings.ACTION_SETTINGS), "Settings");
                            })
                            .setOnDismissListener(d -> setupDialog = null)
                            .show();
                }
            });
        }).start();
    }

    @Override
    public void onBackPressed() {
        // This is home. Nothing behind it.
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            // Escape hatch: Menu (≡) opens the stock Fire TV home for 2 minutes.
            Guard.pausedUntil = System.currentTimeMillis() + 120_000;
            try {
                startActivity(Guard.amazonHomeIntent());
            } catch (Exception ignored) {
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ---------------------------------------------------------------- tiles

    private int dp(float v) {
        return Math.round(v * density);
    }

    private void buildRow() {
        stopNightAnims();
        row.removeAllViews();

        DisplayMetrics m = getResources().getDisplayMetrics();
        float widthDp = m.widthPixels / density;
        float gapDp = 16;
        // 6 wide tiles + 1 square settings tile (width = tile height) must fit in ~92% of the screen.
        float tileWdp = Math.min(170f, (widthDp * 0.92f - 48 - gapDp * 6) / (6f + 9f / 16f));
        int w = dp(tileWdp), h = dp(tileWdp * 9f / 16f), gap = dp(gapDp);

        final PackageManager pm = getPackageManager();
        for (final String[] app : APPS) {
            final String pkg = installed(app);
            Drawable art = null;
            boolean banner = false;
            if (pkg != null) {
                try {
                    art = pm.getApplicationBanner(pkg);
                    banner = art != null;
                    if (art == null) art = pm.getApplicationIcon(pkg);
                } catch (Exception ignored) {
                }
            }
            addTile(app[0], art, banner, null, w, h, gap, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openApp(app[0], pkg, app[1]);
                }
            });
        }

        addTile("PS5", null, false, "PS5", w, h, gap, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openHdmi1();
            }
        });

        addTile("AirPlay", null, false, "AirPlay", w, h, gap, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openApp("AirPlay (AirScreen)", isInstalled(AIRSCREEN) ? AIRSCREEN : null, AIRSCREEN);
            }
        });

        Drawable gear = null;
        try {
            gear = getResources().getDrawable(R.drawable.ic_settings, getTheme());
        } catch (Exception ignored) {
        }
        addTile("Settings", gear, false, gear == null ? "Settings" : null, h, h, 0, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Guard.graceFor(4000);
                safeStart(new Intent(Settings.ACTION_SETTINGS), "Settings");
            }
        });

        int idx = Math.min(focusIndex, row.getChildCount() - 1);
        View target = row.getChildAt(Math.max(idx, 0));
        if (target != null) ((ViewGroupHolder) target.getTag()).tile.requestFocus();
    }

    /** Holds the focusable part of a tile column. */
    private static final class ViewGroupHolder {
        final View tile;

        ViewGroupHolder(View tile) {
            this.tile = tile;
        }
    }

    private void addTile(String label, Drawable art, boolean isBanner, String glyph,
                         int w, int h, int gapAfter, View.OnClickListener click) {
        final int index = row.getChildCount();

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setClipChildren(false);
        column.setClipToPadding(false);

        final FrameLayout tile = new FrameLayout(this);
        tile.setBackgroundResource(R.drawable.tile_bg);
        tile.setForeground(getResources().getDrawable(night ? R.drawable.tile_ring_night : R.drawable.tile_ring, getTheme()));
        if (night && Build.VERSION.SDK_INT >= 28) {
            // Red neon glow via coloured elevation shadow.
            tile.setOutlineSpotShadowColor(0xFFFF1A1A);
            tile.setOutlineAmbientShadowColor(0xFFFF1A1A);
            tile.setElevation(dp(10));
        }
        tile.setClipToOutline(true);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setOnClickListener(click);

        if (art != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(art);
            FrameLayout.LayoutParams lp;
            if (isBanner) {
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
            } else {
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                int s = Math.round(Math.min(w, h) * 0.5f);
                lp = new FrameLayout.LayoutParams(s, s, Gravity.CENTER);
            }
            tile.addView(iv, lp);
        } else {
            TextView t = new TextView(this);
            t.setText(glyph != null ? glyph : label);
            t.setTextColor(Color.WHITE);
            t.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            t.setTextSize(Math.max(14, h / density * 0.3f));
            t.setGravity(Gravity.CENTER);
            tile.addView(t, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }

        final TextView caption = new TextView(this);
        caption.setText(label);
        caption.setTextColor(night ? 0xFFFF6B6B : Color.WHITE);
        if (night) caption.setShadowLayer(12, 0, 0, 0xFFFF1A1A);
        caption.setAlpha(0.45f);
        caption.setTextSize(13);
        caption.setSingleLine(true);
        caption.setGravity(Gravity.CENTER);

        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                float s = has ? 1.1f : 1f;
                v.animate().scaleX(s).scaleY(s).translationZ(has ? dp(night ? 24 : 8) : 0).setDuration(140).start();
                caption.animate().alpha(has ? 1f : 0.45f).setDuration(140).start();
                if (has) focusIndex = index;
            }
        });

        column.addView(tile, new LinearLayout.LayoutParams(w, h));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(Math.max(w, dp(72)), LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(14);
        column.addView(caption, clp);
        column.setTag(new ViewGroupHolder(tile));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = gapAfter;
        row.addView(column, lp);
    }

    // ---------------------------------------------------------------- launching

    private boolean isInstalled(String pkg) {
        try {
            getPackageManager().getApplicationInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private String installed(String[] app) {
        for (int i = 1; i < app.length; i++) if (isInstalled(app[i])) return app[i];
        return null;
    }

    private void openApp(String label, String pkg, String storePkg) {
        if (pkg != null) {
            PackageManager pm = getPackageManager();
            Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (i == null) i = pm.getLaunchIntentForPackage(pkg);
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (safeStart(i, label)) return;
            }
        }
        // Not installed: open its Amazon Appstore page.
        Intent store = new Intent(Intent.ACTION_VIEW, Uri.parse("amzn://apps/android?p=" + storePkg))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!safeStart(store, null)) {
            Toast.makeText(this, "Install " + label + " from the Appstore", Toast.LENGTH_LONG).show();
        }
    }

    private boolean safeStart(Intent i, String labelForError) {
        try {
            startActivity(i);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            if (labelForError != null) {
                Toast.makeText(this, "Couldn't open " + labelForError, Toast.LENGTH_SHORT).show();
            }
            return false;
        }
    }

    private void openHdmi1() {
        List<TvInputInfo> all = new ArrayList<>();
        try {
            TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
            if (tim != null) all.addAll(tim.getTvInputList());
        } catch (Exception ignored) {
        }

        List<TvInputInfo> hdmi = new ArrayList<>();
        for (TvInputInfo i : all) {
            if (i.getType() == TvInputInfo.TYPE_HDMI && i.getParentId() == null) hdmi.add(i);
        }
        if (hdmi.isEmpty()) {
            for (TvInputInfo i : all) if (i.getType() == TvInputInfo.TYPE_HDMI) hdmi.add(i);
        }

        TvInputInfo pick = null;
        for (TvInputInfo i : hdmi) {
            String label = String.valueOf(i.loadLabel(this));
            if (label.matches("(?i).*hdmi\\s*1\\b.*")) {
                pick = i;
                break;
            }
        }
        if (pick == null && !hdmi.isEmpty()) {
            Collections.sort(hdmi, new Comparator<TvInputInfo>() {
                @Override
                public int compare(TvInputInfo a, TvInputInfo b) {
                    return a.getId().compareTo(b.getId());
                }
            });
            pick = hdmi.get(0);
        }

        if (pick != null) {
            Intent v = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(pick.getId()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            pickLiveTv(v);
            Guard.allowTarget(this, v);
            Guard.graceFor(5000);
            if (safeStart(v, null)) return;
        }
        showInputDiagnostics(all);
    }

    /** Several apps can show an HDMI input (e.g. Freeview, Live TV): always use the TV's own Live TV. */
    private void pickLiveTv(Intent v) {
        try {
            List<android.content.pm.ResolveInfo> list = getPackageManager().queryIntentActivities(v, 0);
            android.content.pm.ResolveInfo best = null;
            for (android.content.pm.ResolveInfo ri : list) {
                String label = String.valueOf(ri.loadLabel(getPackageManager())).toLowerCase();
                if (label.contains("live tv") || label.contains("livetv")) { best = ri; break; }
            }
            if (best == null) for (android.content.pm.ResolveInfo ri : list) {
                if (ri.activityInfo.packageName.startsWith("com.amazon")
                        || ri.activityInfo.packageName.contains("tcl")
                        || ri.activityInfo.packageName.contains("mediatek")) { best = ri; break; }
            }
            if (best != null) v.setClassName(best.activityInfo.packageName, best.activityInfo.name);
        } catch (Exception ignored) {
        }
    }

    /** Shown only if HDMI switching fails: a photo of this tells us how to fix it. */
    private void showInputDiagnostics(List<TvInputInfo> all) {
        StringBuilder sb = new StringBuilder("Couldn't switch to HDMI 1 automatically.\n\nInputs found:\n");
        if (all.isEmpty()) sb.append("(none)\n");
        for (TvInputInfo i : all) {
            sb.append("• ").append(i.loadLabel(this)).append("  type=").append(i.getType())
                    .append("\n   ").append(i.getId()).append('\n');
        }
        new AlertDialog.Builder(this)
                .setTitle("PS5 / HDMI 1")
                .setMessage(sb.toString())
                .setPositiveButton("Open Settings", (d, w) -> {
                    Guard.graceFor(4000);
                    safeStart(new Intent(Settings.ACTION_SETTINGS), "Settings");
                })
                .setNegativeButton("Close", null)
                .show();
    }
}
