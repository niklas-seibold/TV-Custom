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
import android.widget.HorizontalScrollView;
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

    static volatile boolean resumed = false;
    private String lastHdmiError;
    private LinearLayout row;
    private int focusIndex = 0;
    private float density;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getExternalFilesDir(null); // creates the folder the private photo is copied into
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
        bgBtn.setOnClickListener(v -> toggleBgStrip(false));
        bgStrip = findViewById(R.id.bgStrip);
        bgStripRow = findViewById(R.id.bgStripRow);
        saver = findViewById(R.id.saver);
        saverImg = findViewById(R.id.saverImg);
        disco = findViewById(R.id.disco);
        idleHandler.postDelayed(idleCheck, 30_000);
        try {
            girls = getPreferences(MODE_PRIVATE).getBoolean("girls", false);
        } catch (Exception ignored) {
        }
        girlsBtn = findViewById(R.id.girlsBtn);
        flowers = findViewById(R.id.flowers);
        vignette = findViewById(R.id.vignette);
        matchBtn = findViewById(R.id.matchBtn);
        matchBtn.setOnClickListener(v -> showMatches());
        nightBtn.setOnClickListener(v -> setMode(!(night && !girls), false));
        girlsBtn.setOnClickListener(v -> setMode(!(night && girls), true));
    }

    private void setMode(boolean on, boolean girlsMode) {
        night = on;
        girls = on && girlsMode;
        getPreferences(MODE_PRIVATE).edit().putBoolean("night", night).putBoolean("girls", girls).apply();
        if (night && !girls) focusIndex = 4; // PS5
        buildRow();
        applyNight(true);
    }

    // ---------------------------------------------------------------- backgrounds

    private static final String[] BG_NAMES = {"Dark", "Aurora", "Sunset", "Ocean", "Nature", "Photo"};
    private static final int[] BG_RES = {R.drawable.bg_gradient, R.drawable.bg_aurora,
            R.drawable.bg_sunset, R.drawable.bg_ocean, R.drawable.bg_gradient, R.drawable.bg_gradient};
    private static final int PHOTO = 5;
    private static final int NATURE = 4;
    private int bgIndex = 0;
    private ImageView bgImage;
    private View scrim;
    private TextView bgBtn;
    private ObjectAnimator kenBurns;

    private void applyBackground() {
        if (bgIndex < 0 || bgIndex >= BG_NAMES.length) bgIndex = 0;
        bgBtn.setText("Background \u00B7 " + BG_NAMES[bgIndex]);
        vignette.setVisibility(View.GONE);
        bgImage.clearColorFilter();
        if (kenBurns != null) kenBurns.cancel();
        if (night) {
            root.setBackgroundResource(girls ? R.drawable.bg_girls : R.drawable.bg_night);
            bgImage.setVisibility(View.GONE);
            scrim.setVisibility(View.GONE);
            return;
        }
        root.setBackgroundResource(BG_RES[bgIndex]);
        if (bgIndex == PHOTO) {
            showPhoto();
            return;
        }
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

    /** Private photo copied to the TV (never on GitHub): Android/data/dev.cleanhome/files/wallpaper.jpg */
    private void showPhoto() {
        File dir = getExternalFilesDir(null);
        List<File> all = photos();
        String chosen = getPreferences(MODE_PRIVATE).getString("photo", null);
        File f = null;
        for (File x : all) if (x.getName().equals(chosen)) f = x;
        if (f == null && !all.isEmpty()) f = all.get(0);
        if (f == null || !f.exists()) {
            bgImage.setVisibility(View.GONE);
            scrim.setVisibility(View.GONE);
            Toast.makeText(this, "No photos yet — copy some to the TV first", Toast.LENGTH_LONG).show();
            return;
        }
        showNature(f);
        // Cinematic grade: lower saturation, more contrast, warm highlights / teal shadows.
        android.graphics.ColorMatrix m = new android.graphics.ColorMatrix();
        m.setSaturation(0.7f);
        float c = 1.18f, t = (1 - c) * 128;
        m.postConcat(new android.graphics.ColorMatrix(new float[]{
                c * 1.06f, 0, 0, 0, t + 6,
                0, c, 0, 0, t,
                0, 0, c * 0.92f, 0, t + 10,
                0, 0, 0, 1, 0}));
        bgImage.setColorFilter(new android.graphics.ColorMatrixColorFilter(m));
        vignette.setVisibility(View.VISIBLE);
    }

    private void showNature(File f) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        o.inSampleSize = 1;
        while (o.outWidth / (o.inSampleSize * 2) >= 1920) o.inSampleSize *= 2;
        o.inJustDecodeBounds = false;
        Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
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

    static byte[] readUrl(String url) throws Exception {
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

    // ---------------------------------------------------------------- background picker (inline)

    private HorizontalScrollView bgStrip;
    private LinearLayout bgStripRow;

    /** Uploaded photos: any image in Android/data/dev.cleanhome/files/ */
    private List<File> photos() {
        List<File> out = new ArrayList<>();
        File dir = getExternalFilesDir(null);
        File[] fs = dir == null ? null : dir.listFiles();
        if (fs != null) for (File f : fs) {
            String n = f.getName().toLowerCase();
            if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".heic") || n.endsWith(".webp")) out.add(f);
        }
        Collections.sort(out, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return out;
    }

    private void toggleBgStrip(boolean photoMode) {
        if (bgStrip.getVisibility() == View.VISIBLE && !photoMode) {
            closeBgStrip();
            return;
        }
        bgStripRow.removeAllViews();
        if (!photoMode) {
            for (int i = 0; i < BG_NAMES.length; i++) {
                final int idx = i;
                Drawable preview = null;
                if (i == NATURE) {
                    File[] c = getCacheDir().listFiles();
                    if (c != null) for (File f : c) if (f.getName().startsWith("nature-")) preview = thumb(f);
                } else if (i == PHOTO) {
                    List<File> ps = photos();
                    if (!ps.isEmpty()) preview = thumb(ps.get(0));
                }
                if (preview == null) preview = getResources().getDrawable(BG_RES[i], getTheme());
                String name = i == PHOTO ? "Photos (" + photos().size() + ") \u203A" : BG_NAMES[i];
                addCard(name, preview, i == bgIndex, v -> {
                    if (idx == PHOTO) { toggleBgStrip(true); return; }
                    chooseBg(idx);
                });
            }
        } else {
            addCard("\u2039 Back", getResources().getDrawable(R.drawable.bg_gradient, getTheme()), false, v -> toggleBgStrip(false));
            String chosen = getPreferences(MODE_PRIVATE).getString("photo", null);
            List<File> ps = photos();
            if (ps.isEmpty()) Toast.makeText(this, "No photos yet — copy some to the TV first", Toast.LENGTH_LONG).show();
            for (int i = 0; i < ps.size(); i++) {
                final File f = ps.get(i);
                addCard("Photo " + (i + 1), thumb(f), f.getName().equals(chosen) && bgIndex == PHOTO, v -> {
                    getPreferences(MODE_PRIVATE).edit().putString("photo", f.getName()).apply();
                    chooseBg(PHOTO);
                });
            }
        }
        bgStrip.setVisibility(View.VISIBLE);
        bgStrip.setAlpha(0f);
        bgStrip.setTranslationY(-dp(12));
        bgStrip.animate().alpha(1f).translationY(0).setDuration(180).start();
        View first = bgStripRow.getChildAt(photoMode ? Math.min(1, bgStripRow.getChildCount() - 1) : Math.max(0, bgIndex));
        if (first != null) ((ViewGroupHolder) first.getTag()).tile.requestFocus();
    }

    private void chooseBg(int idx) {
        bgIndex = idx;
        getPreferences(MODE_PRIVATE).edit().putInt("bg", idx).putBoolean("night", false).apply();
        if (night) {
            night = false;
            buildRow();
            applyNight(false);
        } else {
            applyBackground();
        }
        closeBgStrip();
    }

    private void closeBgStrip() {
        bgStrip.setVisibility(View.GONE);
        bgBtn.requestFocus();
    }

    private Drawable thumb(File f) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            o.inSampleSize = 1;
            while (o.outWidth / (o.inSampleSize * 2) >= 320) o.inSampleSize *= 2;
            o.inJustDecodeBounds = false;
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            return b == null ? null : new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            return null;
        }
    }

    private void addCard(String label, Drawable preview, boolean selected, View.OnClickListener click) {
        int w = dp(150), h = dp(84);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        final FrameLayout card = new FrameLayout(this);
        card.setBackgroundResource(R.drawable.tile_bg);
        card.setForeground(getResources().getDrawable(R.drawable.tile_ring, getTheme()));
        card.setClipToOutline(true);
        card.setFocusable(true);
        card.setClickable(true);
        card.setOnClickListener(click);
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setImageDrawable(preview);
        card.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        final TextView t = new TextView(this);
        t.setText(selected ? "\u2713 " + label : label);
        t.setTextColor(Color.WHITE);
        t.setTextSize(12);
        t.setAlpha(0.7f);
        t.setGravity(Gravity.CENTER);
        card.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(120).start();
            t.setAlpha(has ? 1f : 0.7f);
        });
        col.addView(card, new LinearLayout.LayoutParams(w, h));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(w, -2);
        tl.topMargin = dp(8);
        col.addView(t, tl);
        col.setTag(new ViewGroupHolder(card));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(14);
        bgStripRow.addView(col, lp);
    }

    // ---------------------------------------------------------------- screensaver

    private View saver, disco;
    private ImageView saverImg;
    private long lastInput = System.currentTimeMillis();
    private final android.os.Handler idleHandler = new android.os.Handler();
    private List<File> saverFiles = new ArrayList<>();
    private int saverPos = 0;
    private static final long SAVER_AFTER = 4 * 60_000L;

    private final Runnable idleCheck = new Runnable() {
        @Override
        public void run() {
            if (resumed && saver.getVisibility() != View.VISIBLE && System.currentTimeMillis() - lastInput > SAVER_AFTER) startSaver();
            idleHandler.postDelayed(this, 30_000);
        }
    };

    private final Runnable saverNext = new Runnable() {
        @Override
        public void run() {
            if (saver.getVisibility() != View.VISIBLE) return;
            showSaverImage();
            idleHandler.postDelayed(this, 15_000);
        }
    };

    private void startSaver() {
        saverFiles = photos();
        File[] c = getCacheDir().listFiles();
        if (c != null) for (File f : c) if (f.getName().startsWith("nature-")) saverFiles.add(f);
        saver.setAlpha(0f);
        saver.setVisibility(View.VISIBLE);
        saver.animate().alpha(1f).setDuration(1500).start();
        saverPos = 0;
        idleHandler.post(saverNext);
    }

    private void showSaverImage() {
        if (saverFiles.isEmpty()) {
            saverImg.setImageResource(BG_RES[(saverPos++) % 4]);
        } else {
            File f = saverFiles.get((saverPos++) % saverFiles.size());
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            o.inSampleSize = 1;
            while (o.outWidth / (o.inSampleSize * 2) >= 1920) o.inSampleSize *= 2;
            o.inJustDecodeBounds = false;
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            if (b != null) saverImg.setImageBitmap(b);
        }
        saverImg.setAlpha(0f);
        saverImg.setScaleX(1f);
        saverImg.setScaleY(1f);
        saverImg.animate().alpha(1f).setDuration(1500).start();
        saverImg.animate().scaleX(1.1f).scaleY(1.1f).setDuration(16_000).start();
    }

    private void stopSaver() {
        idleHandler.removeCallbacks(saverNext);
        saver.setVisibility(View.GONE);
        saverImg.setImageDrawable(null);
    }

    // ---------------------------------------------------------------- secret code: up up down down left right left right

    private static final int[] SECRET = {KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT};
    private final int[] lastKeys = new int[SECRET.length];
    private boolean discoOn = false;

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        lastInput = System.currentTimeMillis();
        if (saver.getVisibility() == View.VISIBLE) {
            if (e.getAction() == KeyEvent.ACTION_UP) stopSaver();
            return true; // first press only wakes the screen
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            System.arraycopy(lastKeys, 1, lastKeys, 0, lastKeys.length - 1);
            lastKeys[lastKeys.length - 1] = e.getKeyCode();
            if (java.util.Arrays.equals(lastKeys, SECRET) && !discoOn) {
                java.util.Arrays.fill(lastKeys, 0);
                startDisco();
            }
        }
        return super.dispatchKeyEvent(e); // navigation is never blocked
    }

    private void startDisco() {
        discoOn = true;
        stopNightAnims();
        disco.setVisibility(View.VISIBLE);
        gameOn.setVisibility(View.VISIBLE);
        gameOn.setText("DISCO MODE");
        gameOn.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
        gameOn.setScaleX(1f);
        gameOn.setScaleY(1f);
        ValueAnimator hue = ValueAnimator.ofFloat(0f, 360f * 6);
        hue.setDuration(12_000);
        hue.setInterpolator(new android.view.animation.LinearInterpolator());
        hue.addUpdateListener(a -> {
            float hh = ((float) a.getAnimatedValue()) % 360f;
            int c = Color.HSVToColor(new float[]{hh, 1f, 1f});
            int strobe = ((int) ((float) a.getAnimatedValue() / 45f)) % 2 == 0 ? 0x66 : 0x22;
            disco.setBackgroundColor((strobe << 24) | (c & 0xFFFFFF));
            gameOn.setTextColor(Color.HSVToColor(new float[]{(hh + 180) % 360, 1f, 1f}));
            gameOn.setShadowLayer(40, 0, 0, c);
            gameOn.setRotation((float) Math.sin(Math.toRadians(hh * 4)) * 6);
        });
        hue.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                discoOn = false;
                disco.setVisibility(View.GONE);
                gameOn.setRotation(0);
                for (int i = 0; i < row.getChildCount(); i++) row.getChildAt(i).setRotation(0);
                applyNight(false);
            }
        });
        hue.start();
        for (int i = 0; i < row.getChildCount(); i++) {
            View c = row.getChildAt(i);
            c.animate().rotationBy(720).setStartDelay(i * 120L).setDuration(1400)
                    .setInterpolator(new OvershootInterpolator(1.2f)).start();
            ObjectAnimator jump = ObjectAnimator.ofFloat(c, "translationY", 0, -dp(28), 0);
            jump.setDuration(420);
            jump.setStartDelay(1500 + i * 60L);
            jump.setRepeatCount(20);
            jump.start();
        }
    }

    // ---------------------------------------------------------------- football

    private List<Football.Match> matches = new ArrayList<>();
    private long matchesAt = 0;

    private void refreshMatches() {
        if (System.currentTimeMillis() - matchesAt < 15 * 60_000L) return;
        matchesAt = System.currentTimeMillis();
        new Thread(() -> {
            List<Football.Match> list = Football.today();
            runOnUiThread(() -> {
                matches = list;
                if (list.isEmpty()) {
                    matchBtn.setVisibility(View.GONE);
                    return;
                }
                Football.Match m = list.get(0);
                matchBtn.setText("\u26BD  " + m.home + " \u2013 " + m.away + "  \u00B7  " + (m.live ? "LIVE" : m.time())
                        + "  \u00B7  " + m.channel + (list.size() > 1 ? "  (+" + (list.size() - 1) + ")" : ""));
                matchBtn.setVisibility(View.VISIBLE);
            });
        }).start();
    }

    private void showMatches() {
        StringBuilder sb = new StringBuilder();
        for (Football.Match m : matches) {
            sb.append(m.live ? "LIVE" : m.time()).append("   ").append(m.home).append(" \u2013 ").append(m.away)
                    .append("\n").append(m.comp).append(" \u00B7 ").append(m.channel).append("\n\n");
        }
        sb.append("Channels follow the usual German TV rights and may vary for individual games.");
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle("Football today").setMessage(sb.toString())
                .setNegativeButton("Close", null);
        String prime = installed(APPS[2]);
        if (prime != null) b.setPositiveButton("Open Prime Video", (d, w) -> openApp("Prime Video", prime, APPS[2][1]));
        b.show();
    }

    // ---------------------------------------------------------------- boys night mode

    private boolean night = false;
    private View root;
    private TextView gameOn, nightBtn, girlsBtn, matchBtn;
    private boolean girls = false;
    private FlowerView flowers;
    private View vignette;

    private int accent() { return girls ? 0xFFFF6FB5 : 0xFFFF1A1A; }
    private int accentSoft() { return girls ? 0xFFFFA8D4 : 0xFFFF6B6B; }
    private final List<Animator> nightAnims = new ArrayList<>();

    private void stopNightAnims() {
        for (Animator a : nightAnims) a.cancel();
        nightAnims.clear();
    }

    private void applyNight(boolean animate) {
        stopNightAnims();
        applyBackground();
        boolean boysOn = night && !girls, girlsOn = night && girls;
        nightBtn.setText(boysOn ? "\u25CF  Boys night" : "Boys night");
        nightBtn.setTextColor(boysOn ? 0xFFFF4A4A : Color.WHITE);
        nightBtn.setAlpha(boysOn ? 1f : 0.7f);
        girlsBtn.setText(girlsOn ? "\u25CF  Girls night" : "Girls night");
        girlsBtn.setTextColor(girlsOn ? 0xFFFF8CC6 : Color.WHITE);
        girlsBtn.setAlpha(girlsOn ? 1f : 0.7f);
        gameOn.setText(girls ? "girls night" : "GAME ON");
        gameOn.setTypeface(girls ? Typeface.create("cursive", Typeface.BOLD) : Typeface.create("sans-serif-black", Typeface.ITALIC));
        gameOn.setLetterSpacing(girls ? 0.02f : 0.12f);
        gameOn.setTextColor(accentSoft());
        flowers.setVisibility(girlsOn ? View.VISIBLE : View.GONE);

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
        if (girls) {
            ObjectAnimator bloom = ObjectAnimator.ofFloat(flowers, "bloom", animate ? 0f : 1f, 1f);
            bloom.setDuration(animate ? 1600 : 1);
            ObjectAnimator sway = ObjectAnimator.ofFloat(flowers, "sway", 0f, 1f);
            sway.setDuration(6000);
            sway.setRepeatCount(ValueAnimator.INFINITE);
            sway.setInterpolator(new android.view.animation.LinearInterpolator());
            nightAnims.add(bloom);
            nightAnims.add(sway);
            bloom.start();
            sway.start();
        }
        // Neon pulse that runs forever (glow radius + brightness breathe).
        ValueAnimator pulse = ValueAnimator.ofFloat(0f, 1f);
        pulse.setDuration(1800);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            gameOn.setShadowLayer(25 + 35 * f, 0, 0, accent());
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
            ObjectAnimator flicker = girls
                    ? ObjectAnimator.ofFloat(gameOn, "alpha", 1f, 0.7f, 1f, 0.8f)
                    : ObjectAnimator.ofFloat(gameOn, "alpha", 1f, 0.15f, 1f, 0.3f, 0.9f, 0.2f, 1f, 0.6f);
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
        resumed = false;
        snapshotScreen();
        if (saver != null) stopSaver();
        stopNightAnims();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        // Real screen is up: drop the instant cover (after one frame so there's no flicker).
        root.post(() -> { if (HomeGuardService.instance != null) HomeGuardService.instance.hideCover(); });
        buildRow(); // rebuild so apps installed/updated meanwhile show their artwork
        applyNight(false);
        refreshMatches();
        lastInput = System.currentTimeMillis();
        if (!setupBusy && setupDialog == null && !SelfSetup.guardEnabled(this)) offerSetup();
    }

    private void snapshotScreen() {
        try {
            if (root.getWidth() == 0 || bgStrip.getVisibility() == View.VISIBLE) return;
            Bitmap b = Bitmap.createBitmap(root.getWidth() / 2, root.getHeight() / 2, Bitmap.Config.RGB_565);
            android.graphics.Canvas c = new android.graphics.Canvas(b);
            c.scale(0.5f, 0.5f);
            root.draw(c);
            Guard.snapshot = b;
        } catch (Throwable ignored) {
        }
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
        if (bgStrip != null && bgStrip.getVisibility() == View.VISIBLE) {
            closeBgStrip();
            return;
        }
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
        float tileWdp = Math.min(190f, (widthDp * 0.9f - 48 - gapDp * 5) / (5f + 9f / 16f));
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

        addTile("PS5", getResources().getDrawable(R.drawable.ps_logo, getTheme()), true, null, w, h, gap, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openHdmi1();
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
        tile.setForeground(getResources().getDrawable(!night ? R.drawable.tile_ring
                : girls ? R.drawable.tile_ring_girls : R.drawable.tile_ring_night, getTheme()));
        if (night && Build.VERSION.SDK_INT >= 28) {
            // Red neon glow via coloured elevation shadow.
            tile.setOutlineSpotShadowColor(accent());
            tile.setOutlineAmbientShadowColor(accent());
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
        caption.setTextColor(night ? accentSoft() : Color.WHITE);
        if (night) caption.setShadowLayer(12, 0, 0, accent());
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
            // Amazon's own HDMI player (needs com.amazon.tv.permission.TUNE_INPUT).
            Intent player = new Intent(v).setClassName("com.amazon.tv.inputpreference.service",
                    "com.amazon.tv.inputpreference.player.PassthroughPlayerActivity");
            Guard.graceFor(5000);
            try {
                startActivity(player);
                return;
            } catch (Exception e) {
                lastHdmiError = e.getMessage();
            }
            pickLiveTv(v);
            Guard.allowTarget(this, v);
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
        StringBuilder sb = new StringBuilder("Couldn't switch to HDMI 1 automatically.\n");
        if (lastHdmiError != null) sb.append("\nPlayer: ").append(lastHdmiError).append('\n');
        sb.append("\nInputs found:\n");
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
