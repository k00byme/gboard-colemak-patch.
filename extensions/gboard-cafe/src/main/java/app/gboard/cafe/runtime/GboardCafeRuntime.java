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
    private static final WeakHashMap<View, BoardEffectDrawable> BOARD_EFFECTS = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> PENDING_LAYOUT = new WeakHashMap<>();
    private static WeakReference<View> lastKeyboard = new WeakReference<>(null);
    private static volatile long keyTouchEvents;
    private static volatile long boardTouchEvents;
    private static volatile long layoutCallbacks;

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
            keyTouchEvents++;
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

    /** Injected at the start of SoftKeyboardView.dispatchTouchEvent(MotionEvent). */
    public static void onBoardTouch(View keyboard, MotionEvent event) {
        try {
            if (keyboard == null || event == null) return;
            boardTouchEvents++;
            SharedPreferences p = prefs(keyboard.getContext());
            if (!p.getBoolean("gboard_cafe_enabled", false)) return;
            String effect = p.getString("gboard_cafe_board_effect", "none");
            if (TextUtils.isEmpty(effect) || "none".equals(effect)) return;

            int action = event.getActionMasked();
            BoardEffectDrawable drawable = BOARD_EFFECTS.get(keyboard);
            int pointer = Math.max(0, Math.min(event.getActionIndex(), event.getPointerCount() - 1));
            float x = event.getX(pointer);
            float y = event.getY(pointer);

            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                if (drawable != null) {
                    try { keyboard.getOverlay().remove(drawable); } catch (Throwable ignored) {}
                }
                int color;
                try { color = Color.parseColor(p.getString("gboard_cafe_color", "#FFCFB08E")); }
                catch (Throwable ignored) { color = 0xFFCFB08E; }
                int duration = clamp(p.getInt("gboard_cafe_duration", 260), 80, 1200);
                drawable = new BoardEffectDrawable(keyboard, effect, color, duration);
                BOARD_EFFECTS.put(keyboard, drawable);
                drawable.setBounds(0, 0, Math.max(1, keyboard.getWidth()), Math.max(1, keyboard.getHeight()));
                keyboard.getOverlay().add(drawable);
                drawable.start(x, y);
                keyboard.postInvalidateOnAnimation();
            } else if (drawable != null && action == MotionEvent.ACTION_MOVE) {
                drawable.move(x, y);
                keyboard.postInvalidateOnAnimation();
            } else if (drawable != null && (action == MotionEvent.ACTION_UP ||
                    action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL)) {
                drawable.release();
                keyboard.postInvalidateOnAnimation();
            }
        } catch (Throwable ignored) {
            // Never consume or alter Gboard's real touch event.
        }
    }

    /**
     * Called after Gboard lays out the keyboard. Saved changes are applied on the
     * next UI-queue turn, never while Gboard is executing its own onLayout method.
     */
    public static void afterLayout(View keyboard) {
        try {
            if (!(keyboard instanceof ViewGroup)) return;
            layoutCallbacks++;
            lastKeyboard = new WeakReference<>(keyboard);
            SharedPreferences p = prefs(keyboard.getContext());
            String original = p.getString("gboard_cafe_original_rows", null);
            if (TextUtils.isEmpty(original)) {
                JSONArray captured = captureRows((ViewGroup) keyboard);
                if (isPrimaryLayout(captured)) {
                    String encoded = captured.toString();
                    p.edit().putString("gboard_cafe_original_rows", encoded)
                            .putString("gboard_cafe_custom_rows", encoded).apply();
                }
            } else if (hasCustomization(p)) {
                scheduleApply(keyboard);
            }
        } catch (Throwable ignored) {}
    }

    private static boolean isPrimaryLayout(JSONArray rows) {
        if (rows == null || rows.length() < 3) return false;
        int total = 0;
        for (int i = 0; i < rows.length(); i++) {
            JSONArray row = rows.optJSONArray(i);
            if (row == null || row.length() < 2) return false;
            total += row.length();
        }
        return total >= 20;
    }

    private static boolean hasCustomization(SharedPreferences p) {
        try {
            String original = p.getString("gboard_cafe_original_rows", null);
            String custom = p.getString("gboard_cafe_custom_rows", null);
            Set<String> hidden = p.getStringSet("gboard_cafe_hidden_keys", Collections.<String>emptySet());
            return (original != null && custom != null && !original.equals(custom))
                    || (hidden != null && !hidden.isEmpty());
        } catch (Throwable ignored) { return false; }
    }

    private static void scheduleApply(final View keyboard) {
        try {
            if (keyboard == null || PENDING_LAYOUT.containsKey(keyboard)) return;
            PENDING_LAYOUT.put(keyboard, Boolean.TRUE);
            keyboard.post(() -> {
                PENDING_LAYOUT.remove(keyboard);
                try {
                    if (keyboard instanceof ViewGroup && layoutNeedsApply((ViewGroup) keyboard)) {
                        applySavedLayout((ViewGroup) keyboard);
                        keyboard.requestLayout();
                        keyboard.invalidate();
                    }
                } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}
    }

    private static boolean layoutNeedsApply(ViewGroup root) {
        try {
            SharedPreferences p = prefs(root.getContext());
            JSONArray desired = parseRows(p.getString("gboard_cafe_custom_rows", null));
            if (desired == null) return false;
            JSONArray current = captureRows(root);
            if (current.length() != desired.length()) return true;
            for (int i = 0; i < desired.length(); i++) {
                JSONArray want = desired.optJSONArray(i);
                JSONArray have = current.optJSONArray(i);
                if (want == null || have == null || !want.toString().equals(have.toString())) return true;
            }
            Set<String> hidden = p.getStringSet("gboard_cafe_hidden_keys", Collections.<String>emptySet());
            return hasVisibilityMismatch(root, hidden == null ? Collections.<String>emptySet() : hidden);
        } catch (Throwable ignored) { return false; }
    }

    private static boolean hasVisibilityMismatch(ViewGroup group, Set<String> hidden) {
        try {
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (isKey(child)) {
                    int expected = hidden.contains(label(child)) ? View.GONE : View.VISIBLE;
                    if (child.getVisibility() != expected) return true;
                } else if (child instanceof ViewGroup &&
                        hasVisibilityMismatch((ViewGroup) child, hidden)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
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
            if (view != null) scheduleApply(view);
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
            if (onlyKeys && labels.size() >= 2 && new HashSet<>(labels).size() == labels.size()) {
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

            ArrayList<String> rowLabels = new ArrayList<>();
            for (View key : keys) rowLabels.add(label(key));
            boolean stableLabels = new HashSet<>(rowLabels).size() == rowLabels.size();

            // captureRowsRecursive skips duplicate labels; keep the exact same rule
            // here so a skipped row can never shift the index of later rows.
            if (onlyKeys && keys.size() >= 2 && stableLabels && rowIndex[0] < rows.length()) {
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


    /** Snapshot used by the optional Diagnostics patch. */
    public static String getDiagnostics(Context context) {
        try {
            SharedPreferences p = prefs(context);
            String[][] rows = getRows(context);
            int rowCount = rows == null ? 0 : rows.length;
            int keyCount = 0;
            if (rows != null) {
                for (String[] row : rows) if (row != null) keyCount += row.length;
            }
            String[] hidden = getHiddenKeys(context);
            StringBuilder report = new StringBuilder();
            report.append("Gboard Cafe diagnostics\\n\\n");
            report.append("Package: ").append(context.getPackageName()).append('\\n');
            report.append("Key effects enabled: ")
                    .append(p.getBoolean("gboard_cafe_enabled", false)).append('\\n');
            report.append("Key effect: ")
                    .append(p.getString("gboard_cafe_effect", "ripple")).append('\\n');
            report.append("Board effect: ")
                    .append(p.getString("gboard_cafe_board_effect", "none")).append('\\n');
            report.append("Color: ")
                    .append(p.getString("gboard_cafe_color", "#CFB08E")).append('\\n');
            report.append("Duration: ")
                    .append(p.getInt("gboard_cafe_duration", 260)).append(" ms\\n");
            report.append("Captured rows: ").append(rowCount).append('\\n');
            report.append("Captured keys: ").append(keyCount).append('\\n');
            report.append("Hidden keys: ").append(hidden == null ? 0 : hidden.length).append('\\n');
            report.append("Key touch hooks: ").append(keyTouchEvents).append('\\n');
            report.append("Board touch hooks: ").append(boardTouchEvents).append('\\n');
            report.append("Layout callbacks: ").append(layoutCallbacks).append('\\n');
            try {
                android.content.pm.PackageInfo info = context.getPackageManager()
                        .getPackageInfo(context.getPackageName(), 0);
                report.append("App version: ").append(info.versionName).append('\\n');
            } catch (Throwable ignored) {}
            return report.toString();
        } catch (Throwable error) {
            return "Gboard Cafe diagnostics unavailable: "
                    + error.getClass().getName() + ": " + error.getMessage();
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class BoardEffectDrawable extends Drawable {
        private final View host;
        private final String effect;
        private final int color;
        private final int duration;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float x;
        private float y;
        private long startedAt;
        private long releasedAt;
        private boolean released;
        private boolean removalPosted;

        BoardEffectDrawable(View host, String effect, int color, int duration) {
            this.host = host;
            this.effect = effect;
            this.color = color;
            this.duration = duration;
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        void start(float x, float y) {
            this.x = x; this.y = y;
            startedAt = SystemClock.uptimeMillis();
            released = false; removalPosted = false;
        }
        void move(float x, float y) { this.x = x; this.y = y; }
        void release() {
            if (!released) {
                released = true;
                releasedAt = SystemClock.uptimeMillis();
            }
        }

        @Override public void draw(Canvas canvas) {
            try {
                long now = SystemClock.uptimeMillis();
                float progress = Math.min(1f, Math.max(0f, (now - startedAt) / (float) duration));
                float fade = released ? Math.max(0f, 1f - (now - releasedAt) / 220f) : 1f;
                float diagonal = (float) Math.hypot(host.getWidth(), host.getHeight());
                float reach = Math.max(24f, diagonal * 0.48f) * progress;
                paint.setColor(color);
                paint.setAlpha((int) (220f * fade));
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.4f);

                switch (effect) {
                    case "ring":
                        canvas.drawCircle(x, y, 8f + reach, paint);
                        canvas.drawCircle(x, y, 4f + reach * 0.62f, paint);
                        canvas.drawCircle(x, y, 2f + reach * 0.28f, paint);
                        break;
                    case "outline":
                        rect.set(2f + reach * 0.05f, 2f + reach * 0.05f,
                                host.getWidth() - 2f - reach * 0.05f,
                                host.getHeight() - 2f - reach * 0.05f);
                        canvas.drawRoundRect(rect, 18f, 18f, paint);
                        break;
                    case "spark":
                    case "sparkSpin":
                    case "firecracker": {
                        int count = "firecracker".equals(effect) ? 14 : 9;
                        float rotation = "sparkSpin".equals(effect) ? progress * 2.4f : 0f;
                        paint.setStrokeWidth("firecracker".equals(effect) ? 3.1f : 2.2f);
                        for (int i = 0; i < count; i++) {
                            double angle = (Math.PI * 2d * i / count) + rotation;
                            float dx = (float) Math.cos(angle);
                            float dy = (float) Math.sin(angle);
                            float inner = 5f + reach * 0.16f;
                            float outer = 9f + reach * (0.58f + (i % 3) * 0.08f);
                            canvas.drawLine(x + dx * inner, y + dy * inner,
                                    x + dx * outer, y + dy * outer, paint);
                            if ("firecracker".equals(effect)) {
                                canvas.drawCircle(x + dx * outer, y + dy * outer, 2.4f, paint);
                            }
                        }
                        break;
                    }
                    case "blob":
                    case "blobSpread":
                    case "paintGun": {
                        paint.setStyle(Paint.Style.FILL);
                        float radius = "blob".equals(effect) ? 18f + progress * 18f :
                                ("blobSpread".equals(effect) ? 12f + reach * 0.42f : 9f + reach * 0.18f);
                        paint.setAlpha((int) (("paintGun".equals(effect) ? 105f : 80f) * fade));
                        canvas.drawCircle(x, y, radius, paint);
                        int count = "paintGun".equals(effect) ? 11 : 8;
                        for (int i = 0; i < count; i++) {
                            double angle = (Math.PI * 2d * i / count) + (i * 0.17d);
                            float spread = radius * ("paintGun".equals(effect) ? 1.45f : 0.72f);
                            float dx = (float) Math.cos(angle);
                            float dy = (float) Math.sin(angle);
                            float dot = radius * (0.20f + (i % 3) * 0.05f);
                            canvas.drawCircle(x + dx * spread, y + dy * spread, dot, paint);
                            if ("paintGun".equals(effect) && i % 2 == 0) {
                                paint.setStyle(Paint.Style.STROKE);
                                canvas.drawCircle(x + dx * spread * 1.3f,
                                        y + dy * spread * 1.3f, dot * 1.6f, paint);
                                paint.setStyle(Paint.Style.FILL);
                            }
                        }
                        break;
                    }
                    default: break;
                }

                if (released && fade <= 0f) postRemoval();
                else if ((!released && progress < 1f) || (released && fade > 0f)) {
                    host.postInvalidateOnAnimation();
                }
            } catch (Throwable ignored) { postRemoval(); }
        }

        private void postRemoval() {
            if (removalPosted) return;
            removalPosted = true;
            host.post(() -> {
                try { host.getOverlay().remove(this); } catch (Throwable ignored) {}
                BOARD_EFFECTS.remove(host);
            });
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) {
            paint.setColorFilter(filter); invalidateSelf();
        }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
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
                    case "blob":
                    case "blobSpread": {
                        paint.setStyle(Paint.Style.FILL);
                        paint.setAlpha((int) ("blob".equals(effect) ? 150 * fade : 105 * fade));
                        float spread = 5f + progress * ("blob".equals(effect) ? 10f : 24f);
                        canvas.drawCircle(x, y, spread * 0.78f, paint);
                        for (int i = 0; i < 7; i++) {
                            double angle = i * Math.PI * 2d / 7d;
                            float dx = (float) Math.cos(angle);
                            float dy = (float) Math.sin(angle);
                            canvas.drawCircle(x + dx * spread * 0.72f,
                                    y + dy * spread * 0.72f, spread * 0.43f, paint);
                        }
                        break;
                    }
                    case "footprint": {
                        paint.setStyle(Paint.Style.FILL);
                        paint.setAlpha((int) (145 * fade));
                        float size = 3f + 4f * progress;
                        canvas.drawOval(x - size * 0.8f, y - size * 1.8f,
                                x + size * 0.8f, y + size * 1.8f, paint);
                        for (int i = 0; i < 4; i++) {
                            float toe = size * (i - 1.5f) * 0.72f;
                            canvas.drawCircle(x + toe, y - size * 2.3f, size * 0.35f, paint);
                        }
                        break;
                    }
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
                } else if ((!released && progress < 1f) || (released && fade > 0f)) {
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
