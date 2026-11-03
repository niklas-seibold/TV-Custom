package dev.cleanhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Shared state between the launcher screen and the Home-button guard. */
final class Guard {
    static final String AMAZON_HOME_PKG = "com.amazon.tv.launcher";

    /** Guard does nothing until this time (escape hatch: Menu button). */
    static volatile long pausedUntil = 0;
    /** Short grace window after we open a system screen (HDMI, Settings) that may live in the Amazon package. */
    static volatile long graceUntil = 0;

    /** Amazon-launcher screens we open on purpose (e.g. the HDMI viewer) — never redirected. */
    static final Set<String> allowed = Collections.synchronizedSet(new HashSet<String>());

    private Guard() {}

    /** Remember the activity that will handle this intent if it lives in Amazon's launcher. */
    static void allowTarget(Context c, Intent i) {
        try {
            ResolveInfo ri = c.getPackageManager().resolveActivity(i, 0);
            if (ri != null && ri.activityInfo != null && AMAZON_HOME_PKG.equals(ri.activityInfo.packageName)) {
                allowed.add(ri.activityInfo.name);
            }
        } catch (Exception ignored) {
        }
    }

    static Intent amazonHomeIntent() {
        return new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(AMAZON_HOME_PKG)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /** Activity class names of the stock Fire TV home screen. */
    static Set<String> amazonHomeClasses(Context c) {
        Set<String> out = new HashSet<>();
        try {
            List<ResolveInfo> list = c.getPackageManager().queryIntentActivities(amazonHomeIntent(), 0);
            for (ResolveInfo ri : list) {
                if (ri.activityInfo != null) out.add(ri.activityInfo.name);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    static void graceFor(long ms) {
        graceUntil = System.currentTimeMillis() + ms;
    }
}
