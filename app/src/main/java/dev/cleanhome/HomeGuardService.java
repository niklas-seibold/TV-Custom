package dev.cleanhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

import java.util.Set;

/**
 * Fire OS always sends the Home button to Amazon's launcher. When that launcher's
 * home screen appears, bring CleanHome back to the front.
 */
public class HomeGuardService extends AccessibilityService {
    private Set<String> homeClasses;
    private long lastRedirect;

    @Override
    protected void onServiceConnected() {
        homeClasses = Guard.amazonHomeClasses(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (e.getPackageName() == null || !Guard.AMAZON_HOME_PKG.equals(e.getPackageName().toString())) return;

        long now = System.currentTimeMillis();
        if (now < Guard.pausedUntil || now < Guard.graceUntil) return;

        if (homeClasses == null || homeClasses.isEmpty()) homeClasses = Guard.amazonHomeClasses(this);
        String cls = e.getClassName() == null ? "" : e.getClassName().toString();
        // If we know the home activity, only react to it (so Amazon popups/inputs still work).
        if (!homeClasses.isEmpty() && !homeClasses.contains(cls)) return;

        if (now - lastRedirect < 400) return;
        lastRedirect = now;
        Intent i = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        try {
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onInterrupt() {}
}
