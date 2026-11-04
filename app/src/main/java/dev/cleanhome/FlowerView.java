package dev.cleanhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** Outline flowers around the screen edges. bloom: 0..1 entry animation, sway: 0..1 looping. */
public class FlowerView extends View {
    private static final int[] COLORS = {0xFFFF8CC6, 0xFFC9A7FF, 0xFFFFB38A, 0xFFFFD1E8};
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private float bloom = 1f, sway = 0f;
    private float[][] spots; // x, y, size, petals, colorIdx, phase

    public FlowerView(Context c) { this(c, null); }

    public FlowerView(Context c, AttributeSet a) {
        super(c, a);
        stroke.setStyle(Paint.Style.STROKE);
        glow.setStyle(Paint.Style.STROKE);
        setLayerType(LAYER_TYPE_SOFTWARE, null); // needed for shadow glow on strokes
    }

    public void setBloom(float b) { bloom = b; invalidate(); }
    public float getBloom() { return bloom; }
    public void setSway(float s) { sway = s; invalidate(); }
    public float getSway() { return sway; }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float d = getResources().getDisplayMetrics().density;
        java.util.List<float[]> l = new java.util.ArrayList<>();
        java.util.Random r = new java.util.Random(7);
        float step = 150 * d;
        int i = 0;
        // top and bottom edges
        for (float x = 40 * d; x < w; x += step, i++) {
            l.add(new float[]{x, (12 + r.nextInt(30)) * d, (34 + r.nextInt(26)) * d, 5 + r.nextInt(3), i % 4, r.nextFloat()});
            l.add(new float[]{x + step / 2, h - (14 + r.nextInt(30)) * d, (34 + r.nextInt(26)) * d, 5 + r.nextInt(3), (i + 2) % 4, r.nextFloat()});
        }
        // left and right edges
        for (float y = 120 * d; y < h - 100 * d; y += step, i++) {
            l.add(new float[]{(14 + r.nextInt(26)) * d, y, (30 + r.nextInt(24)) * d, 5 + r.nextInt(3), (i + 1) % 4, r.nextFloat()});
            l.add(new float[]{w - (14 + r.nextInt(26)) * d, y + step / 3, (30 + r.nextInt(24)) * d, 5 + r.nextInt(3), (i + 3) % 4, r.nextFloat()});
        }
        // big corner flowers
        l.add(new float[]{0, 0, 90 * d, 8, 0, 0.1f});
        l.add(new float[]{w, h, 100 * d, 8, 1, 0.6f});
        l.add(new float[]{w, 0, 70 * d, 7, 2, 0.3f});
        l.add(new float[]{0, h, 75 * d, 7, 3, 0.8f});
        spots = l.toArray(new float[0][]);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (spots == null) return;
        float d = getResources().getDisplayMetrics().density;
        int n = spots.length;
        for (int i = 0; i < n; i++) {
            float[] s = spots[i];
            float local = Math.max(0f, Math.min(1f, bloom * 1.8f - (i / (float) n) * 0.8f));
            if (local <= 0f) continue;
            float scale = overshoot(local);
            float size = s[2] * scale;
            int petals = (int) s[3];
            int color = COLORS[(int) s[4]];
            float rot = (float) (Math.sin((sway + s[5]) * Math.PI * 2) * 8) + local * 40;

            stroke.setColor(color);
            stroke.setAlpha((int) (200 * local));
            stroke.setStrokeWidth(2f * d);
            glow.setColor(color);
            glow.setAlpha((int) (90 * local));
            glow.setStrokeWidth(6f * d);
            glow.setShadowLayer(10 * d, 0, 0, color);

            c.save();
            c.translate(s[0], s[1]);
            c.rotate(rot);
            for (int p = 0; p < petals; p++) {
                c.save();
                c.rotate(360f / petals * p);
                oval.set(-size * 0.22f, -size, size * 0.22f, -size * 0.18f);
                c.drawOval(oval, glow);
                c.drawOval(oval, stroke);
                c.restore();
            }
            c.drawCircle(0, 0, size * 0.14f, stroke);
            c.restore();
        }
    }

    private static float overshoot(float t) {
        float s = 2.2f;
        t -= 1f;
        return t * t * ((s + 1) * t + s) + 1f;
    }
}
