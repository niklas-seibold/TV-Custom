package dev.cleanhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.graphics.PixelFormat;
import android.widget.ImageView;
import android.view.accessibility.AccessibilityEvent;

/**
 * Fire OS always sends the Home button to Amazon's launcher. Whenever a window of
 * that launcher appears (except screens we opened on purpose, e.g. HDMI), bring
 * CleanHome back to the front.
 */
public class HomeGuardService extends AccessibilityService {
    static volatile boolean running = false;
    static HomeGuardService instance;
    private long lastRedirect;

    @Override
    protected void onServiceConnected() {
        running = true;
        instance = this;
        Log.i("CleanHome", "guard connected");
    }

    @Override
    public void onDestroy() {
        running = false;
        instance = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        String pkg = e.getPackageName() == null ? "" : e.getPackageName().toString();
        String cls = e.getClassName() == null ? "" : e.getClassName().toString();
        Log.i("CleanHome", "window " + pkg + "/" + cls);
        if (!Guard.AMAZON_HOME_PKG.equals(pkg)) return;

        long now = System.currentTimeMillis();
        if (now < Guard.pausedUntil || now < Guard.graceUntil) return;
        if (Guard.allowed.contains(cls)) return;

        if (now - lastRedirect < 400) return;
        lastRedirect = now;
        Log.i("CleanHome", "redirecting from " + cls);
        try {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        } catch (Exception ex) {
            Log.w("CleanHome", "redirect failed", ex);
        }
    }

    /** Set once intercepting Home proved not to work on this TV; then we fall back to window events. */
    private static volatile boolean keyPathBroken = false;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());

    /** Catch the Home key before Fire OS handles it: instant, and avoids Android's app-switch delay. */
    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        if (keyPathBroken || System.currentTimeMillis() < Guard.pausedUntil) return false;
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            Log.i("CleanHome", "home key intercepted");
            if (MainActivity.resumed) return true; // already on CleanHome
            openHome();
            // Safety net: if CleanHome didn't come up, stop intercepting and let Fire OS go home.
            handler.postDelayed(() -> {
                if (!MainActivity.resumed) {
                    Log.w("CleanHome", "home via key failed; falling back");
                    keyPathBroken = true;
                    performGlobalAction(GLOBAL_ACTION_HOME);
                }
            }, 1200);
        }
        return true;
    }

    private void openHome() {
        try {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (Exception ex) {
            Log.w("CleanHome", "home start failed", ex);
        }
    }

    @Override
    public void onInterrupt() {}
}
