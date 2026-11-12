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

import org.json.JSONArray;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Runtime bridge. Every method called from injected Gboard code is fail-safe:
 * exceptions are contained here and are never allowed to escape into the IME.
 */
public final class GboardCafeRuntime {
    private static final String KEY_CLASS =
            "com.google.android.libraries.inputmethod.widgets.SoftKeyView";
    private static final WeakHashMap<View, EffectDrawable> EFFECTS = new WeakHashMap<>();
    private static WeakReference<View> lastKeyboard = new WeakReference<>(null);

    private GboardCafeRuntime() {}

    /*
     * AndroidX PreferenceManager's default file name. Using the conventional name
     * avoids linking extension bytecode to AndroidX preference implementation code.
     */
    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(
                context.getPackageName() + "_preferences", Context.MODE_PRIVATE);
    }

    /** Injected at the start of SoftKeyView.onTouchEvent(MotionEvent). */
    public static void onTouch(View key, MotionEvent event) {
        try {
            if (key == null || event == null) return;
            SharedPreferences p = prefs(key.getContext());
            if (!p.getBoolean("gboard_cafe_enabled", false)) return;

            String effect = p.getString("gboard_cafe_effect", "ripple");
            if (TextUtils.isEmpty(effect) || "none".equals(effect)) return;

            int action = event.getActionMasked();
            EffectDrawable drawable = EFFECTS.get(key);
            if (action == MotionEvent.ACTION_DOWN ||
                    action == MotionEvent.ACTION_POINTER_DOWN) {
                if (drawable != null) {
                    try { key.getOverlay().remove(drawable); } catch (Throwable ignored) {}
                }
                int color;
                try {
                    color = Color.parseColor(p.getString("gboard_cafe_color", "#FFCFB08E"));
                } catch (Throwable ignored) {
                    color = 0xFFCFB08E;
                }
                int duration = clamp(p.getInt("gboard_cafe_duration", 260), 80, 1200);
                drawable = new EffectDrawable(key, effect, color, duration);
                EFFECTS.put(key, drawable);
                drawable.setBounds(0, 0, Math.max(1, key.getWidth()), Math.max(1, key.getHeight()));
                key.getOverlay().add(drawable);
                drawable.start(event.getX(), event.getY());
                key.postInvalidateOnAnimation();
            } else if (drawable != null && action == MotionEvent.ACTION_MOVE) {
                drawable.move(event.getX(), event.getY());
                key.postInvalidateOnAnimation();
            } else if (drawable != null && (action == MotionEvent.ACTION_UP ||
                    action == MotionEvent.ACTION_POINTER_UP ||
                    action == MotionEvent.ACTION_CANCEL)) {
                drawable.release();
                key.postInvalidateOnAnimation();
            }
        } catch (Throwable ignored) {
            // A Cafe problem must never interrupt Gboard's real touch handler.
        }
    }

    /**
     * Injected after SoftKeyboardView.onLayout. It only observes the view tree.
     * It never removes, inserts, hides, or reorders keyboard views during layout.
     */
    public static void afterLayout(View keyboard) {
        try {
            if (!(keyboard instanceof ViewGroup)) return;
            lastKeyboard = new WeakReference<>(keyboard);

            SharedPreferences p = prefs(keyboard.getContext());
            String original = p.getString("gboard_cafe_original_rows", null);
            if (TextUtils.isEmpty(original)) {
                JSONArray captured = captureRows((ViewGroup) keyboard);
                if (captured.length() > 0) {
                    String encoded = captured.toString();
                    p.edit()
                            .putString("gboard_cafe_original_rows", encoded)
                            .putString("gboard_cafe_custom_rows", encoded)
                            .apply();
                }
            }
        } catch (Throwable ignored) {
            // Observation is optional; the keyboard must remain usable.
        }
    }

    public static String[][] getRows(Context context) {
        try {
            SharedPreferences p = prefs(context);
            JSONArray data = parseRows(p.getString("gboard_cafe_custom_rows", null));
            if (data == null) data = parseRows(p.getString("gboard_cafe_original_rows", null));
            if (data == null) return new String[0][];

            String[][] result = new String[data.length()][];
            for (int i = 0; i < data.length(); i++) {
                JSONArray row = data.optJSONArray(i);
                if (row == null || row.length() == 0) return new String[0][];
                result[i] = new String[row.length()];
                for (int j = 0; j < row.length(); j++) {
                    result[i][j] = row.optString(j, "");
                    if (TextUtils.isEmpty(result[i][j])) return new String[0][];
                }
            }
            return result;
        } catch (Throwable ignored) {
            return new String[0][];
        }
    }

    public static void setRows(Context context, String[][] rows) {
        try {
            if (rows == null || rows.length == 0) return;
            JSONArray outer = new JSONArray();
            for (String[] row : rows) {
                if (row == null || row.length == 0) return;
                JSONArray inner = new JSONArray();
                for (String key : row) {
                    if (TextUtils.isEmpty(key)) return;
                    inner.put(key);
                }
                outer.put(inner);
            }
            prefs(context).edit().putString("gboard_cafe_custom_rows", outer.toString()).apply();
            refreshKeyboard();
        } catch (Throwable ignored) {}
    }

    public static String[] getHiddenKeys(Context context) {
        try {
            Set<String> values = prefs(context).getStringSet(
                    "gboard_cafe_hidden_keys", Collections.<String>emptySet());
            return values == null ? new String[0] : values.toArray(new String[0]);
        } catch (Throwable ignored) {
            return new String[0];
        }
    }

    public static void setHiddenKeys(Context context, String[] values) {
        try {
            Set<String> hidden = new HashSet<>();
            if (values != null) {
                for (String value : values) if (!TextUtils.isEmpty(value)) hidden.add(value);
            }
            prefs(context).edit().putStringSet("gboard_cafe_hidden_keys", hidden).apply();
            refreshKeyboard();
        } catch (Throwable ignored) {}
    }

    public static void resetLayout(Context context) {
        try {
            SharedPreferences p = prefs(context);
            String original = p.getString("gboard_cafe_original_rows", null);
            SharedPreferences.Editor editor = p.edit().remove("gboard_cafe_hidden_keys");
            if (TextUtils.isEmpty(original)) editor.remove("gboard_cafe_custom_rows");
            else editor.putString("gboard_cafe_custom_rows", original);
            editor.apply();
            refreshKeyboard();
        } catch (Throwable ignored) {}
    }

    private static void refreshKeyboard() {
        try {
            final View view = lastKeyboard.get();
            if (view == null) return;
            // Apply only after an explicit user save, outside Gboard's onLayout pass.
            view.post(() -> {
                try {
                    if (view instanceof ViewGroup) {
                        applySavedLayout((ViewGroup) view);
                        view.requestLayout();
                        view.invalidate();
                    }
                } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}
    }

    private static boolean isKey(View view) {
        if (view == null) return false;
        try {
            Class<?> type = view.getClass();
            while (type != null) {
                if (KEY_CLASS.equals(type.getName())) return true;
                type = type.getSuperclass();
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static String label(View view) {
        try {
            CharSequence description = view.getContentDescription();
            if (!TextUtils.isEmpty(description)) return description.toString();

            Object tag = view.getTag();
            if (tag instanceof CharSequence && !TextUtils.isEmpty((CharSequence) tag)) {
                return tag.toString();
            }

            if (view instanceof TextView) {
                CharSequence text = ((TextView) view).getText();
                if (!TextUtils.isEmpty(text)) return text.toString();
            }

            int id = view.getId();
            if (id != View.NO_ID) return view.getClass().getSimpleName() + "#" + id;
        } catch (Throwable ignored) {}
        return view.getClass().getSimpleName() + "@" +
                Integer.toHexString(System.identityHashCode(view));
    }

    private static JSONArray captureRows(ViewGroup root) {
        JSONArray result = new JSONArray();
        captureRowsRecursive(root, result);
        return result;
    }

    private static void captureRowsRecursive(ViewGroup group, JSONArray result) {
        try {
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
                if (child instanceof ViewGroup && !isKey(child)) {
                    captureRowsRecursive((ViewGroup) child, result);
                }
            }
        } catch (Throwable ignored) {}
    }

    private static JSONArray parseRows(String encoded) {
        if (TextUtils.isEmpty(encoded)) return null;
        try { return new JSONArray(encoded); }
        catch (Throwable ignored) { return null; }
    }

    private static void applySavedLayout(ViewGroup root) {
        try {
            SharedPreferences p = prefs(root.getContext());
            JSONArray desired = parseRows(p.getString("gboard_cafe_custom_rows", null));
            if (desired == null) desired = parseRows(p.getString("gboard_cafe_original_rows", null));
            if (desired == null) return;
            Set<String> hidden = new HashSet<>(p.getStringSet(
                    "gboard_cafe_hidden_keys", Collections.<String>emptySet()));
            applyRowsRecursive(root, desired, hidden, new int[]{0});
        } catch (Throwable ignored) {}
    }

    private static void applyRowsRecursive(
            ViewGroup group, JSONArray rows, Set<String> hidden, int[] rowIndex) {
        try {
            ArrayList<View> keys = new ArrayList<>();
            boolean onlyKeys = group.getChildCount() > 0;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (isKey(child)) keys.add(child);
                else onlyKeys = false;
            }

            if (onlyKeys && keys.size() >= 2 && rowIndex[0] < rows.length()) {
                JSONArray savedRow = rows.optJSONArray(rowIndex[0]++);
                if (savedRow != null && savedRow.length() == keys.size()) {
                    ArrayList<String> order = new ArrayList<>();
                    for (int i = 0; i < savedRow.length(); i++) order.add(savedRow.optString(i));

                    for (View key : keys) {
                        int visibility = hidden.contains(label(key)) ? View.GONE : View.VISIBLE;
                        if (key.getVisibility() != visibility) key.setVisibility(visibility);
                    }

                    Collections.sort(keys, new Comparator<View>() {
                        @Override public int compare(View first, View second) {
                            int a = order.indexOf(label(first));
                            int b = order.indexOf(label(second));
                            if (a < 0) a = Integer.MAX_VALUE;
                            if (b < 0) b = Integer.MAX_VALUE;
                            return Integer.compare(a, b);
                        }
                    });

                    boolean changed = false;
                    for (int i = 0; i < keys.size(); i++) {
                        if (group.getChildAt(i) != keys.get(i)) {
                            changed = true;
                            break;
                        }
                    }
                    if (changed) {
                        // This runs only after the user saves, on the UI queue,
                        // never from inside Gboard's onLayout implementation.
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
                if (child instanceof ViewGroup && !isKey(child)) {
                    applyRowsRecursive((ViewGroup) child, rows, hidden, rowIndex);
                }
            }
        } catch (Throwable ignored) {}
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class EffectDrawable extends Drawable {
        private final View host;
        private final String effect;
        private final int color;
        private final int duration;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();
        private float x;
        private float y;
        private long startedAt;
        private long releasedAt;
        private boolean released;
        private boolean removalPosted;

        EffectDrawable(View host, String effect, int color, int duration) {
            this.host = host;
            this.effect = effect;
            this.color = color;
            this.duration = duration;
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        void start(float x, float y) {
            this.x = x;
            this.y = y;
            startedAt = SystemClock.uptimeMillis();
            released = false;
            removalPosted = false;
        }

        void move(float x, float y) {
            this.x = x;
            this.y = y;
        }

        void release() {
            if (!released) {
                released = true;
                releasedAt = SystemClock.uptimeMillis();
            }
        }

        @Override public void draw(Canvas canvas) {
            try {
                long now = SystemClock.uptimeMillis();
                float progress = Math.min(1f, (now - startedAt) / (float) duration);
                float fade = released
                        ? Math.max(0f, 1f - (now - releasedAt) / 180f) : 1f;
                float maximum = (float) Math.hypot(host.getWidth(), host.getHeight());

                paint.setColor(color);
                paint.setAlpha((int) (255 * fade));
                paint.setStyle(Paint.Style.FILL);

                switch (effect) {
                    case "flash":
                        paint.setAlpha((int) (72 * fade));
                        canvas.drawRoundRect(0, 0, host.getWidth(), host.getHeight(), 10, 10, paint);
                        break;
                    case "outline":
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(2.5f);
                        bounds.set(1.5f, 1.5f,
                                Math.max(1.5f, host.getWidth() - 1.5f),
                                Math.max(1.5f, host.getHeight() - 1.5f));
                        canvas.drawRoundRect(bounds, 9, 9, paint);
                        break;
                    case "bounce":
                        canvas.save();
                        float scale = 1f - 0.06f * (float) Math.sin(Math.PI * progress);
                        canvas.scale(scale, scale, host.getWidth() / 2f, host.getHeight() / 2f);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(2.2f);
                        canvas.drawRoundRect(2, 2, Math.max(2, host.getWidth() - 2),
                                Math.max(2, host.getHeight() - 2), 9, 9, paint);
                        canvas.restore();
                        break;
                    case "sparks":
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(2f);
                        float radius = 8f + progress * 18f;
                        for (int i = 0; i < 8; i++) {
                            double angle = i * Math.PI / 4d;
                            float dx = (float) Math.cos(angle);
                            float dy = (float) Math.sin(angle);
                            canvas.drawLine(x + dx * 4, y + dy * 4,
                                    x + dx * radius, y + dy * radius, paint);
                            canvas.drawCircle(x + dx * radius, y + dy * radius, 1.8f, paint);
                        }
                        break;
                    case "paint":
                        paint.setAlpha((int) (100 * fade));
                        canvas.drawCircle(x, y, 10f + progress * maximum * 0.42f, paint);
                        break;
                    case "ripple":
                    default:
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(2.2f);
                        canvas.drawCircle(x, y, 4f + progress * maximum * 0.5f, paint);
                        paint.setStrokeWidth(1.2f);
                        canvas.drawCircle(x, y, 2f + progress * maximum * 0.25f, paint);
                        break;
                }

                if (released && fade <= 0f) {
                    postRemoval();
                } else if (!released || fade > 0f) {
                    host.postInvalidateOnAnimation();
                }
            } catch (Throwable ignored) {
                postRemoval();
            }
        }

        private void postRemoval() {
            if (removalPosted) return;
            removalPosted = true;
            host.post(() -> {
                try { host.getOverlay().remove(this); } catch (Throwable ignored) {}
                EFFECTS.remove(host);
            });
        }

        @Override public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override public void setColorFilter(android.graphics.ColorFilter filter) {
            paint.setColorFilter(filter);
            invalidateSelf();
        }

        @Override public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
