package app.gboard.cafe.runtime;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import org.json.JSONArray;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

/** Runtime bridge injected into Gboard's own key and keyboard views. */
public final class GboardCafeRuntime {
    private static final String KEY_CLASS = "com.google.android.libraries.inputmethod.widgets.SoftKeyView";
    private static final WeakHashMap<View, EffectDrawable> EFFECTS = new WeakHashMap<>();
    private static WeakReference<View> lastKeyboard = new WeakReference<>(null);

    private GboardCafeRuntime() {}

    private static SharedPreferences prefs(Context c) {
        return PreferenceManager.getDefaultSharedPreferences(c);
    }

    /** Injected at the start of SoftKeyView.onTouchEvent(MotionEvent). */
    public static void onTouch(View key, MotionEvent event) {
        if (key == null || event == null) return;
        SharedPreferences p = prefs(key.getContext());
        if (!p.getBoolean("gboard_cafe_enabled", true)) return;
        String effect = p.getString("gboard_cafe_effect", "ripple");
        if (TextUtils.isEmpty(effect) || "none".equals(effect)) return;

        int action = event.getActionMasked();
        EffectDrawable d = EFFECTS.get(key);
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            if (d != null) key.getOverlay().remove(d);
            int color;
            try { color = Color.parseColor(p.getString("gboard_cafe_color", "#CFB08E")); }
            catch (Exception ignored) { color = 0xFFCFB08E; }
            int duration = Math.max(80, Math.min(1200, p.getInt("gboard_cafe_duration", 260)));
            d = new EffectDrawable(key, effect, color, duration);
            EFFECTS.put(key, d);
            key.getOverlay().add(d);
            d.setBounds(0, 0, Math.max(1, key.getWidth()), Math.max(1, key.getHeight()));
            d.start(event.getX(), event.getY());
            key.postInvalidateOnAnimation();
        } else if (d != null && action == MotionEvent.ACTION_MOVE) {
            d.move(event.getX(), event.getY());
            key.postInvalidateOnAnimation();
        } else if (d != null && (action == MotionEvent.ACTION_UP ||
                action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL)) {
            d.release();
            key.postInvalidateOnAnimation();
        }
    }

    /** Injected after SoftKeyboardView.onLayout; captures and applies key rows. */
    public static void afterLayout(final View keyboard) {
        if (!(keyboard instanceof ViewGroup)) return;
        lastKeyboard = new WeakReference<>(keyboard);
        // Never detach/reinsert child views while Gboard is in its own onLayout pass.
        keyboard.post(new Runnable() {
            @Override public void run() { applySavedLayout(keyboard); }
        });
    }

    private static void applySavedLayout(View keyboard) {
        if (!(keyboard instanceof ViewGroup)) return;
        SharedPreferences p = prefs(keyboard.getContext());
        ViewGroup root = (ViewGroup) keyboard;

        JSONArray original = parseRows(p.getString("gboard_cafe_original_rows", null));
        if (original == null || original.length() == 0) {
            JSONArray captured = captureRows(root);
            if (captured.length() > 0) {
                String encoded = captured.toString();
                p.edit().putString("gboard_cafe_original_rows", encoded)
                        .putString("gboard_cafe_custom_rows", encoded).apply();
            }
        }

        JSONArray desired = parseRows(p.getString("gboard_cafe_custom_rows", null));
        if (desired == null) desired = parseRows(p.getString("gboard_cafe_original_rows", null));
        Set<String> hidden = p.getStringSet("gboard_cafe_hidden_keys", Collections.<String>emptySet());
        if (desired != null) applyRows(root, desired, hidden);
    }

    public static String[][] getRows(Context context) {
        SharedPreferences p = prefs(context);
        JSONArray data = parseRows(p.getString("gboard_cafe_custom_rows", null));
        if (data == null) data = parseRows(p.getString("gboard_cafe_original_rows", null));
        if (data == null) return new String[0][];
        String[][] result = new String[data.length()][];
        for (int i = 0; i < data.length(); i++) {
            JSONArray row = data.optJSONArray(i);
            result[i] = new String[row == null ? 0 : row.length()];
            if (row != null) for (int j = 0; j < row.length(); j++) result[i][j] = row.optString(j, "");
        }
        return result;
    }

    public static void setRows(Context context, String[][] rows) {
        JSONArray outer = new JSONArray();
        if (rows != null) for (String[] row : rows) {
            JSONArray inner = new JSONArray();
            if (row != null) for (String key : row) if (!TextUtils.isEmpty(key)) inner.put(key);
            if (inner.length() > 0) outer.put(inner);
        }
        prefs(context).edit().putString("gboard_cafe_custom_rows", outer.toString()).apply();
        refreshKeyboard();
    }

    public static String[] getHiddenKeys(Context context) {
        Set<String> values = prefs(context).getStringSet("gboard_cafe_hidden_keys", Collections.<String>emptySet());
        return values.toArray(new String[0]);
    }

    public static void setHiddenKeys(Context context, String[] values) {
        Set<String> hidden = new HashSet<>();
        if (values != null) hidden.addAll(Arrays.asList(values));
        prefs(context).edit().putStringSet("gboard_cafe_hidden_keys", hidden).apply();
        refreshKeyboard();
    }

    public static void resetLayout(Context context) {
        SharedPreferences p = prefs(context);
        String original = p.getString("gboard_cafe_original_rows", null);
        SharedPreferences.Editor e = p.edit().remove("gboard_cafe_hidden_keys");
        if (original == null) e.remove("gboard_cafe_custom_rows");
        else e.putString("gboard_cafe_custom_rows", original);
        e.apply();
        refreshKeyboard();
    }

    private static void refreshKeyboard() {
        View view = lastKeyboard.get();
        if (view != null) { view.requestLayout(); view.invalidate(); }
    }

    private static boolean isKey(View v) {
        return v != null && KEY_CLASS.equals(v.getClass().getName());
    }

    private static String label(View v) {
        CharSequence c = v.getContentDescription();
        if (!TextUtils.isEmpty(c)) return c.toString();
        if (v instanceof TextView) {
            c = ((TextView) v).getText();
            if (!TextUtils.isEmpty(c)) return c.toString();
        }
        return v.getClass().getSimpleName() + "#" + v.getId();
    }

    private static JSONArray captureRows(ViewGroup root) {
        JSONArray result = new JSONArray();
        captureRowsRecursive(root, result);
        return result;
    }

    private static void captureRowsRecursive(ViewGroup group, JSONArray result) {
        ArrayList<String> labels = new ArrayList<>();
        boolean onlyKeys = group.getChildCount() > 0;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (isKey(child)) labels.add(label(child));
            else onlyKeys = false;
        }
        if (onlyKeys && labels.size() >= 2) {
            JSONArray row = new JSONArray();
            for (String name : labels) row.put(name);
            result.put(row);
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof ViewGroup) captureRowsRecursive((ViewGroup) child, result);
        }
    }

    private static JSONArray parseRows(String encoded) {
        if (TextUtils.isEmpty(encoded)) return null;
        try { return new JSONArray(encoded); }
        catch (Exception ignored) { return null; }
    }

    private static void applyRows(ViewGroup root, JSONArray rows, Set<String> hidden) {
        applyRowsRecursive(root, rows, hidden, new int[]{0});
    }

    private static void applyRowsRecursive(ViewGroup group, JSONArray rows, Set<String> hidden, int[] index) {
        ArrayList<View> keys = new ArrayList<>();
        boolean onlyKeys = group.getChildCount() > 0;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (isKey(child)) keys.add(child);
            else onlyKeys = false;
        }
        if (onlyKeys && keys.size() >= 2 && index[0] < rows.length()) {
            JSONArray row = rows.optJSONArray(index[0]++);
            if (row != null) {
                final ArrayList<String> order = new ArrayList<>();
                for (int i = 0; i < row.length(); i++) order.add(row.optString(i));
                for (View key : keys) {
                    int visibility = hidden.contains(label(key)) ? View.GONE : View.VISIBLE;
                    if (key.getVisibility() != visibility) key.setVisibility(visibility);
                }
                Collections.sort(keys, new Comparator<View>() {
                    @Override public int compare(View a, View b) {
                        int ai = order.indexOf(label(a)), bi = order.indexOf(label(b));
                        if (ai < 0) ai = Integer.MAX_VALUE;
                        if (bi < 0) bi = Integer.MAX_VALUE;
                        return Integer.compare(ai, bi);
                    }
                });
                boolean changed = false;
                for (int i = 0; i < keys.size(); i++) if (group.getChildAt(i) != keys.get(i)) { changed = true; break; }
                if (changed) {
                    for (View key : keys) group.removeView(key);
                    for (int i = 0; i < keys.size(); i++) {
                        View key = keys.get(i);
                        group.addView(key, i, key.getLayoutParams());
                    }
                }
            }
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof ViewGroup) applyRowsRecursive((ViewGroup) child, rows, hidden, index);
        }
    }

    private static final class EffectDrawable extends Drawable {
        private final View host;
        private final String effect;
        private final int color, duration;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float x, y;
        private long start, release;
        private boolean released;

        EffectDrawable(View host, String effect, int color, int duration) {
            this.host = host; this.effect = effect; this.color = color; this.duration = duration;
            paint.setStrokeCap(Paint.Cap.ROUND);
        }
        void start(float x, float y) { this.x=x; this.y=y; start=SystemClock.uptimeMillis(); released=false; }
        void move(float x, float y) { this.x=x; this.y=y; }
        void release() { if (!released) { released=true; release=SystemClock.uptimeMillis(); } }

        @Override public void draw(Canvas canvas) {
            float progress = Math.min(1f, (SystemClock.uptimeMillis()-start)/(float)duration);
            float fade = released ? Math.max(0f, 1f-(SystemClock.uptimeMillis()-release)/180f) : 1f;
            float max = (float)Math.hypot(host.getWidth(), host.getHeight());
            paint.setColor(color); paint.setAlpha((int)(255*fade)); paint.setStyle(Paint.Style.FILL);
            switch (effect) {
                case "flash":
                    paint.setAlpha((int)(72*fade));
                    canvas.drawRoundRect(0,0,host.getWidth(),host.getHeight(),10,10,paint);
                    break;
                case "outline":
                    paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2.5f);
                    rect.set(1.5f,1.5f,host.getWidth()-1.5f,host.getHeight()-1.5f);
                    canvas.drawRoundRect(rect,9,9,paint);
                    break;
                case "bounce":
                    canvas.save();
                    float scale=1f-0.06f*(float)Math.sin(Math.PI*progress);
                    canvas.scale(scale,scale,host.getWidth()/2f,host.getHeight()/2f);
                    paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2.2f);
                    canvas.drawRoundRect(2,2,host.getWidth()-2,host.getHeight()-2,9,9,paint);
                    canvas.restore();
                    break;
                case "sparks":
                    paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2f);
                    float radius=8f+progress*18f;
                    for(int i=0;i<8;i++){
                        double a=i*Math.PI/4d;
                        float dx=(float)Math.cos(a), dy=(float)Math.sin(a);
                        canvas.drawLine(x+dx*4,y+dy*4,x+dx*radius,y+dy*radius,paint);
                        canvas.drawCircle(x+dx*radius,y+dy*radius,1.8f,paint);
                    }
                    break;
                case "paint":
                    paint.setAlpha((int)(100*fade));
                    canvas.drawCircle(x,y,10f+progress*max*0.42f,paint);
                    break;
                case "ripple":
                default:
                    paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2.2f);
                    canvas.drawCircle(x,y,4f+progress*max*0.5f,paint);
                    paint.setStrokeWidth(1.2f);
                    canvas.drawCircle(x,y,2f+progress*max*0.25f,paint);
                    break;
            }
            if (released && fade <= 0f) {
                host.getOverlay().remove(this);
                EFFECTS.remove(host);
            } else if (released || progress < 1f) host.postInvalidateOnAnimation();
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
