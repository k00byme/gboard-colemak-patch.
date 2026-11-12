package app.gboard.cafe.runtime;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.util.Base64;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.view.inputmethod.InputConnection;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Runtime bridge. Every method called from injected Gboard code is fail-safe:
 * exceptions are contained here and are never allowed to escape into the IME.
 */
public final class GboardCafeRuntime {
    private static final String KEY_CLASS =
            "com.google.android.libraries.inputmethod.widgets.SoftKeyView";
    private static final String KEY_ID_PREFIX = "@gboardcafe:key:";
    private static final String CUSTOM_KEY_PREFIX = "@gboardcafe:custom:";
    private static final WeakHashMap<View, String> STABLE_KEY_IDS = new WeakHashMap<>();
    private static final WeakHashMap<View, EffectDrawable> EFFECTS = new WeakHashMap<>();
    private static final WeakHashMap<View, BoardEffectDrawable> BOARD_EFFECTS = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> PENDING_LAYOUT = new WeakHashMap<>();
    private static WeakReference<View> lastKeyboard = new WeakReference<>(null);
    private static volatile long keyTouchEvents;
    private static volatile long boardTouchEvents;
    private static volatile long layoutCallbacks;
    private static volatile String lastLayoutStatus = "Aguardando o primeiro layout do teclado";
    private static volatile int lastAppliedKeyCount;
    private static volatile int lastAppliedRowCount;

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
            if (event.getActionMasked() != MotionEvent.ACTION_DOWN
                    && event.getActionMasked() != MotionEvent.ACTION_POINTER_DOWN) return;

            SharedPreferences p = prefs(key.getContext());
            if (!p.getBoolean("gboard_cafe_enabled", false)) return;
            String effect = p.getString("gboard_cafe_effect", "ripple");
            if (TextUtils.isEmpty(effect) || "none".equals(effect)) return;

            EffectDrawable previous = EFFECTS.remove(key);
            if (previous != null) previous.removeNow();
            int color = readEffectColor(p);
            int duration = clamp(p.getInt("gboard_cafe_duration", 520), 220, 1200);
            EffectDrawable drawable = new EffectDrawable(key, effect, color, duration);
            EFFECTS.put(key, drawable);
            drawable.setBounds(0, 0, Math.max(1, key.getWidth()), Math.max(1, key.getHeight()));
            key.getOverlay().add(drawable);
            drawable.start(event.getX(), event.getY());
            key.postInvalidateOnAnimation();
        } catch (Throwable ignored) {
            // A Cafe effect must never interfere with Gboard's input handling.
        }
    }

    /**
     * Layout fallback called from SoftKeyboardView.dispatchTouchEvent.
     * The editor must not depend only on onLayout: Gboard can reuse an already
     * measured keyboard view when the IME becomes active again.
     */
    public static void onKeyboardTouch(View keyboard, MotionEvent event) {
        try {
            if (keyboard == null || event == null) return;
            int action = event.getActionMasked();
            if (action != MotionEvent.ACTION_DOWN
                    && action != MotionEvent.ACTION_POINTER_DOWN) return;

            lastKeyboard = new WeakReference<>(keyboard);
            SharedPreferences p = prefs(keyboard.getContext());
            if (!hasCustomization(p)) {
                lastLayoutStatus = "Nenhuma personalização de layout salva";
                return;
            }

            // The current root is now known to be active. Reapply after the
            // current dispatch completes so Gboard can finish processing its touch.
            PENDING_LAYOUT.remove(keyboard);
            scheduleApply(keyboard);
        } catch (Throwable error) {
            lastLayoutStatus = "Hook de toque/layout falhou: "
                    + error.getClass().getSimpleName() + ": " + error.getMessage();
            android.util.Log.e("GboardCafe", lastLayoutStatus, error);
        }
    }

    /** Injected at the start of SoftKeyboardView.dispatchTouchEvent(MotionEvent). */
    public static void onBoardTouch(View keyboard, MotionEvent event) {
        try {
            if (keyboard == null || event == null) return;
            boardTouchEvents++;
            if (event.getActionMasked() != MotionEvent.ACTION_DOWN
                    && event.getActionMasked() != MotionEvent.ACTION_POINTER_DOWN) return;

            SharedPreferences p = prefs(keyboard.getContext());
            if (!p.getBoolean("gboard_cafe_enabled", false)) return;
            String effect = p.getString("gboard_cafe_board_effect", "none");
            if (TextUtils.isEmpty(effect) || "none".equals(effect)) return;

            BoardEffectDrawable previous = BOARD_EFFECTS.remove(keyboard);
            if (previous != null) previous.removeNow();
            int pointer = Math.max(0, Math.min(event.getActionIndex(), event.getPointerCount() - 1));
            int color = readEffectColor(p);
            int duration = clamp(p.getInt("gboard_cafe_duration", 520), 220, 1200);
            BoardEffectDrawable drawable = new BoardEffectDrawable(
                    keyboard, effect, color, duration);
            BOARD_EFFECTS.put(keyboard, drawable);
            drawable.setBounds(0, 0, Math.max(1, keyboard.getWidth()),
                    Math.max(1, keyboard.getHeight()));
            keyboard.getOverlay().add(drawable);
            drawable.start(event.getX(pointer), event.getY(pointer));
            keyboard.postInvalidateOnAnimation();
        } catch (Throwable ignored) {
            // Never consume or alter Gboard's real touch event.
        }
    }

    private static int readEffectColor(SharedPreferences p) {
        try {
            return Color.parseColor(p.getString("gboard_cafe_color", "#FFCFB08E"));
        } catch (Throwable ignored) {
            return 0xFFCFB08E;
        }
    }

    /** Immediately removes active overlays after the user disables or changes effects. */
    public static void clearActiveEffects() {
        try {
            ArrayList<View> keys = new ArrayList<>(EFFECTS.keySet());
            for (View key : keys) {
                EffectDrawable drawable = EFFECTS.remove(key);
                if (drawable != null) drawable.removeNow();
            }
            ArrayList<View> boards = new ArrayList<>(BOARD_EFFECTS.keySet());
            for (View board : boards) {
                BoardEffectDrawable drawable = BOARD_EFFECTS.remove(board);
                if (drawable != null) drawable.removeNow();
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Called after Gboard lays out the keyboard. Capture the original text layout
     * once, then reapply the saved arrangement on the next UI-queue turn.
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
            } else {
                migrateLegacyLayout(p, (ViewGroup) keyboard);
                JSONArray baseline = parseRows(p.getString("gboard_cafe_original_rows", null));
                if (baseline != null) assignStableIds((ViewGroup) keyboard, baseline);
                if (hasCustomization(p)) scheduleApply(keyboard);
            }
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

    private static boolean isKey(View view) {
        if (view == null) return false;
        try {
            Object tag = view.getTag();
            if (tag instanceof String && ((String) tag).startsWith(CUSTOM_KEY_PREFIX)) return true;
            Class<?> type = view.getClass();
            while (type != null) {
                if (KEY_CLASS.equals(type.getName())) return true;
                type = type.getSuperclass();
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static String rawLabel(View view) {
        try {
            Object tag = view.getTag();
            if (tag instanceof String && ((String) tag).startsWith(CUSTOM_KEY_PREFIX)) {
                return (String) tag;
            }
            CharSequence description = view.getContentDescription();
            if (!TextUtils.isEmpty(description)) return description.toString();
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
        return view.getClass().getSimpleName() + "@"
                + Integer.toHexString(System.identityHashCode(view));
    }

    private static String label(View view) {
        try {
            Object tag = view.getTag();
            if (tag instanceof String && ((String) tag).startsWith(CUSTOM_KEY_PREFIX)) {
                return (String) tag;
            }
            String stable = STABLE_KEY_IDS.get(view);
            if (!TextUtils.isEmpty(stable)) return stable;
        } catch (Throwable ignored) {}
        return rawLabel(view);
    }

    public static String createCustomKeyToken(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        try {
            String value = text.trim();
            String encoded = Base64.encodeToString(
                    value.getBytes(StandardCharsets.UTF_8),
                    Base64.NO_WRAP | Base64.URL_SAFE);
            return CUSTOM_KEY_PREFIX + java.util.UUID.randomUUID().toString() + ":" + encoded;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String getDisplayLabel(String token) {
        if (TextUtils.isEmpty(token)) return "";
        if (token.startsWith(CUSTOM_KEY_PREFIX)) {
            try {
                int separator = token.indexOf(':', CUSTOM_KEY_PREFIX.length());
                String encoded = separator < 0
                        ? token.substring(CUSTOM_KEY_PREFIX.length())
                        : token.substring(separator + 1);
                byte[] decoded = Base64.decode(encoded, Base64.NO_WRAP | Base64.URL_SAFE);
                return new String(decoded, StandardCharsets.UTF_8);
            } catch (Throwable ignored) { return "?"; }
        }
        if (token.startsWith(KEY_ID_PREFIX)) {
            int rowSeparator = token.indexOf(':', KEY_ID_PREFIX.length());
            int occurrenceSeparator = rowSeparator < 0 ? -1 : token.indexOf(':', rowSeparator + 1);
            if (occurrenceSeparator >= 0) {
                try {
                    byte[] decoded = Base64.decode(
                            token.substring(occurrenceSeparator + 1),
                            Base64.NO_WRAP | Base64.URL_SAFE);
                    return new String(decoded, StandardCharsets.UTF_8);
                } catch (Throwable ignored) {}
            }
        }
        return token;
    }

    private static String makeStableKeyId(
            int originalRow, int occurrence, String originalLabel) {
        String encoded = Base64.encodeToString(
                originalLabel.getBytes(StandardCharsets.UTF_8),
                Base64.NO_WRAP | Base64.URL_SAFE);
        return KEY_ID_PREFIX + originalRow + ":" + occurrence + ":" + encoded;
    }

    private static int stableOccurrenceIndex(String token) {
        if (TextUtils.isEmpty(token) || !token.startsWith(KEY_ID_PREFIX)) return -1;
        try {
            int rowStart = KEY_ID_PREFIX.length();
            int rowEnd = token.indexOf(':', rowStart);
            int occurrenceEnd = rowEnd < 0 ? -1 : token.indexOf(':', rowEnd + 1);
            if (occurrenceEnd < 0) return -1;
            return Integer.parseInt(token.substring(rowEnd + 1, occurrenceEnd));
        } catch (Throwable ignored) { return -1; }
    }

    private static int stableRowIndex(String token) {
        if (TextUtils.isEmpty(token) || !token.startsWith(KEY_ID_PREFIX)) return -1;
        try {
            int start = KEY_ID_PREFIX.length();
            int end = token.indexOf(':', start);
            if (end < 0) return -1;
            return Integer.parseInt(token.substring(start, end));
        } catch (Throwable ignored) { return -1; }
    }

    private static boolean isCustomToken(String token) {
        return token != null && token.startsWith(CUSTOM_KEY_PREFIX);
    }

    private static void assignStableIds(ViewGroup root, JSONArray originalRows) {
        try {
            ArrayList<ViewGroup> groups = discoverKeyRows(root);
            Set<String> expectedIds = new HashSet<>();
            for (int row = 0; row < originalRows.length(); row++) {
                JSONArray values = originalRows.optJSONArray(row);
                if (values == null) continue;
                for (int i = 0; i < values.length(); i++) {
                    expectedIds.add(values.optString(i, ""));
                }
            }

            for (int rowIndex = 0;
                    rowIndex < groups.size() && rowIndex < originalRows.length();
                    rowIndex++) {
                JSONArray original = originalRows.optJSONArray(rowIndex);
                if (original == null) continue;
                ViewGroup group = groups.get(rowIndex);
                Map<String, Integer> occurrences = new HashMap<>();

                for (int i = 0; i < group.getChildCount(); i++) {
                    View key = group.getChildAt(i);
                    if (!isKey(key)) continue;
                    String shownId = label(key);
                    if (isCustomToken(shownId)) continue;

                    // Count every occurrence, including Views whose stable ID was
                    // already known. Otherwise a mix of reused/recreated duplicate
                    // key Views can assign the same occurrence twice.
                    String raw = rawLabel(key);
                    int occurrence = occurrences.containsKey(raw)
                            ? occurrences.get(raw) : 0;
                    occurrences.put(raw, occurrence + 1);

                    String currentId = STABLE_KEY_IDS.get(key);
                    if (!TextUtils.isEmpty(currentId)
                            && expectedIds.contains(currentId)
                            && getDisplayLabel(currentId).equals(raw)
                            && stableRowIndex(currentId) == rowIndex
                            && stableOccurrenceIndex(currentId) == occurrence) {
                        continue;
                    }
                    // A View can be recycled for a different SoftKeyDef. Never
                    // trust its WeakHashMap ID without confirming the current label.
                    STABLE_KEY_IDS.remove(key);

                    for (int j = 0; j < original.length(); j++) {
                        String token = original.optString(j, "");
                        if (getDisplayLabel(token).equals(raw)
                                && stableRowIndex(token) == rowIndex
                                && stableOccurrenceIndex(token) == occurrence) {
                            STABLE_KEY_IDS.put(key, token);
                            break;
                        }
                    }
                }
            }

            // Fallback for a recreated Gboard view whose parent row was rearranged:
            // only use a global match when that label is unique in the baseline.
            Map<String, String> unique = new HashMap<>();
            Set<String> ambiguous = new HashSet<>();
            for (int i = 0; i < originalRows.length(); i++) {
                JSONArray row = originalRows.optJSONArray(i);
                if (row == null) continue;
                for (int j = 0; j < row.length(); j++) {
                    String token = row.optString(j, "");
                    String raw = getDisplayLabel(token);
                    if (unique.containsKey(raw)) ambiguous.add(raw);
                    else unique.put(raw, token);
                }
            }
            for (ViewGroup group : groups) {
                for (int i = 0; i < group.getChildCount(); i++) {
                    View key = group.getChildAt(i);
                    if (!isKey(key) || isCustomToken(label(key))) continue;
                    String currentId = STABLE_KEY_IDS.get(key);
                    if (!TextUtils.isEmpty(currentId) && expectedIds.contains(currentId)) continue;
                    String raw = rawLabel(key);
                    String token = unique.get(raw);
                    if (token != null && !ambiguous.contains(raw)) {
                        STABLE_KEY_IDS.put(key, token);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

        private static InputConnection findInputConnection(Context context) {
        try {
            Context current = context;
            for (int depth = 0; depth < 12 && current != null; depth++) {
                if (current instanceof InputMethodService) {
                    return ((InputMethodService) current).getCurrentInputConnection();
                }
                if (current instanceof ContextWrapper) {
                    current = ((ContextWrapper) current).getBaseContext();
                } else {
                    break;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static View createCustomKeyView(ViewGroup parent, String token) {
        String value = getDisplayLabel(token);
        TextView key = new TextView(parent.getContext());
        key.setTag(token);
        key.setText(value);
        key.setTextColor(Color.WHITE);
        key.setTextSize(15f);
        key.setGravity(Gravity.CENTER);
        key.setMaxLines(1);
        key.setPadding(dp(parent, 6), dp(parent, 4), dp(parent, 6), dp(parent, 4));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFF3A3F4A);
        background.setCornerRadius(dp(parent, 7));
        background.setStroke(dp(parent, 1), 0xFF636C7C);
        key.setBackground(background);
        key.setClickable(true);
        key.setFocusable(false);
        key.setOnClickListener(v -> {
            InputConnection connection = findInputConnection(v.getContext());
            if (connection != null) connection.commitText(value, 1);
        });
        return key;
    }

    private static int dp(View view, float value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

        private static JSONArray captureRows(ViewGroup root) {
        JSONArray result = new JSONArray();
        ArrayList<ViewGroup> groups = discoverKeyRows(root);
        for (int rowIndex = 0; rowIndex < groups.size(); rowIndex++) {
            ViewGroup group = groups.get(rowIndex);
            JSONArray row = new JSONArray();
            Map<String, Integer> occurrences = new HashMap<>();
            for (int i = 0; i < group.getChildCount(); i++) {
                View key = group.getChildAt(i);
                if (!isKey(key) || key.getVisibility() != View.VISIBLE) continue;
                String id = label(key);
                if (!isCustomToken(id) && !id.startsWith(KEY_ID_PREFIX)) {
                    String raw = rawLabel(key);
                    int occurrence = occurrences.containsKey(raw) ? occurrences.get(raw) : 0;
                    occurrences.put(raw, occurrence + 1);
                    id = makeStableKeyId(rowIndex, occurrence, raw);
                    STABLE_KEY_IDS.put(key, id);
                }
                row.put(id);
            }
            result.put(row);
        }
        return result;
    }

    private static ArrayList<ViewGroup> discoverKeyRows(ViewGroup root) {
        ArrayList<ViewGroup> result = new ArrayList<>();
        discoverKeyRowsRecursive(root, result);
        return result;
    }

    private static void discoverKeyRowsRecursive(ViewGroup group, ArrayList<ViewGroup> result) {
        try {
            int count = group.getChildCount();
            boolean onlyKeys = count > 0;
            for (int i = 0; i < count; i++) {
                if (!isKey(group.getChildAt(i))) {
                    onlyKeys = false;
                    break;
                }
            }
            if (onlyKeys) {
                result.add(group);
                return;
            }
            for (int i = 0; i < count; i++) {
                View child = group.getChildAt(i);
                if (child instanceof ViewGroup && !isKey(child)) {
                    discoverKeyRowsRecursive((ViewGroup) child, result);
                }
            }
        } catch (Throwable ignored) {}
    }

    private static JSONArray parseRows(String encoded) {
        if (TextUtils.isEmpty(encoded)) return null;
        try { return new JSONArray(encoded); }
        catch (Throwable ignored) { return null; }
    }

    private static JSONArray convertLegacyRows(JSONArray legacy, JSONArray original) {
        JSONArray converted = new JSONArray();
        if (legacy == null || original == null) return converted;
        for (int rowIndex = 0; rowIndex < legacy.length(); rowIndex++) {
            JSONArray oldRow = legacy.optJSONArray(rowIndex);
            JSONArray baseRow = original.optJSONArray(rowIndex);
            JSONArray newRow = new JSONArray();
            Set<String> used = new HashSet<>();
            if (oldRow != null) {
                for (int i = 0; i < oldRow.length(); i++) {
                    String oldLabel = oldRow.optString(i, "");
                    String match = null;
                    if (baseRow != null) {
                        for (int j = 0; j < baseRow.length(); j++) {
                            String candidate = baseRow.optString(j, "");
                            if (!used.contains(candidate) && getDisplayLabel(candidate).equals(oldLabel)) {
                                match = candidate;
                                break;
                            }
                        }
                    }
                    if (match == null) {
                        for (int r = 0; r < original.length() && match == null; r++) {
                            JSONArray candidates = original.optJSONArray(r);
                            if (candidates == null) continue;
                            for (int j = 0; j < candidates.length(); j++) {
                                String candidate = candidates.optString(j, "");
                                if (!used.contains(candidate) && getDisplayLabel(candidate).equals(oldLabel)) {
                                    match = candidate;
                                    break;
                                }
                            }
                        }
                    }
                    if (match != null) {
                        used.add(match);
                        newRow.put(match);
                    }
                }
            }
            converted.put(newRow);
        }
        return converted;
    }

    private static void migrateLegacyLayout(SharedPreferences p, ViewGroup root) {
        try {
            JSONArray storedOriginal = parseRows(
                    p.getString("gboard_cafe_original_rows", null));
            if (storedOriginal == null || storedOriginal.length() == 0) return;

            boolean originalIsStable = true;
            for (int i = 0; i < storedOriginal.length() && originalIsStable; i++) {
                JSONArray row = storedOriginal.optJSONArray(i);
                if (row == null) continue;
                for (int j = 0; j < row.length(); j++) {
                    String token = row.optString(j, "");
                    if (!token.startsWith(KEY_ID_PREFIX)) {
                        originalIsStable = false;
                        break;
                    }
                }
            }

            JSONArray stableOriginal;
            if (originalIsStable) {
                stableOriginal = storedOriginal;
            } else {
                stableOriginal = new JSONArray();
                for (int rowIndex = 0; rowIndex < storedOriginal.length(); rowIndex++) {
                    JSONArray oldRow = storedOriginal.optJSONArray(rowIndex);
                    JSONArray newRow = new JSONArray();
                    Map<String, Integer> occurrences = new HashMap<>();
                    if (oldRow != null) {
                        for (int i = 0; i < oldRow.length(); i++) {
                            String raw = oldRow.optString(i, "");
                            int occurrence = occurrences.containsKey(raw)
                                    ? occurrences.get(raw) : 0;
                            occurrences.put(raw, occurrence + 1);
                            newRow.put(makeStableKeyId(rowIndex, occurrence, raw));
                        }
                    }
                    stableOriginal.put(newRow);
                }
            }

            JSONArray storedCustom = parseRows(
                    p.getString("gboard_cafe_custom_rows", null));
            JSONArray stableCustom;
            if (storedCustom == null) {
                stableCustom = new JSONArray(stableOriginal.toString());
            } else {
                boolean customIsStable = true;
                for (int i = 0; i < storedCustom.length() && customIsStable; i++) {
                    JSONArray row = storedCustom.optJSONArray(i);
                    if (row == null) continue;
                    for (int j = 0; j < row.length(); j++) {
                        String token = row.optString(j, "");
                        if (!token.startsWith(KEY_ID_PREFIX)
                                && !token.startsWith(CUSTOM_KEY_PREFIX)) {
                            customIsStable = false;
                            break;
                        }
                    }
                }
                stableCustom = customIsStable
                        ? storedCustom
                        : convertLegacyRows(storedCustom, stableOriginal);
            }

            // The old editor persisted hidden keys by their display label. Translate
            // those exclusions into the new stable-token model so an upgrade does
            // not silently bring previously removed keys back.
            Set<String> legacyHidden = p.getStringSet(
                    "gboard_cafe_hidden_keys", Collections.<String>emptySet());
            SharedPreferences.Editor editor = p.edit()
                    .putString("gboard_cafe_original_rows", stableOriginal.toString())
                    .putString("gboard_cafe_custom_rows", stableCustom.toString());
            if (legacyHidden != null && !legacyHidden.isEmpty()) {
                JSONArray filtered = new JSONArray();
                for (int i = 0; i < stableCustom.length(); i++) {
                    JSONArray row = stableCustom.optJSONArray(i);
                    JSONArray next = new JSONArray();
                    if (row != null) {
                        for (int j = 0; j < row.length(); j++) {
                            String token = row.optString(j, "");
                            if (!legacyHidden.contains(token)
                                    && !legacyHidden.contains(getDisplayLabel(token))) {
                                next.put(token);
                            }
                        }
                    }
                    filtered.put(next);
                }
                editor.putString("gboard_cafe_custom_rows", filtered.toString())
                        .remove("gboard_cafe_hidden_keys");
            }
            editor.apply();
            assignStableIds(root, stableOriginal);
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

    private static boolean matchesOriginalKeySet(ViewGroup root, JSONArray original) {
        try {
            if (original == null || original.length() == 0) return false;
            ArrayList<ViewGroup> groups = discoverKeyRows(root);
            if (groups.size() != original.length()) return false;
            assignStableIds(root, original);

            Set<String> expected = new HashSet<>();
            for (int rowIndex = 0; rowIndex < original.length(); rowIndex++) {
                JSONArray row = original.optJSONArray(rowIndex);
                if (row == null) return false;
                for (int i = 0; i < row.length(); i++) {
                    String id = row.optString(i, "");
                    if (TextUtils.isEmpty(id) || !id.startsWith(KEY_ID_PREFIX)
                            || !expected.add(id)) return false;
                }
            }

            Set<String> actual = new HashSet<>();
            for (ViewGroup group : groups) {
                for (int i = 0; i < group.getChildCount(); i++) {
                    View key = group.getChildAt(i);
                    if (!isKey(key) || isCustomToken(label(key))) continue;
                    String id = label(key);
                    // Unknown/unmatched keys mean this is another Gboard page or
                    // a changed layout. Never apply a saved QWERTY arrangement there.
                    if (TextUtils.isEmpty(id) || !id.startsWith(KEY_ID_PREFIX)
                            || !actual.add(id)) return false;
                }
            }
            return expected.equals(actual);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasCustomization(SharedPreferences p) {
        try {
            String original = p.getString("gboard_cafe_original_rows", null);
            String custom = p.getString("gboard_cafe_custom_rows", null);
            return original != null && custom != null && !original.equals(custom);
        } catch (Throwable ignored) { return false; }
    }

    private static void scheduleApply(final View keyboard) {
        try {
            if (keyboard == null || PENDING_LAYOUT.containsKey(keyboard)) return;
            PENDING_LAYOUT.put(keyboard, Boolean.TRUE);
            keyboard.post(() -> {
                PENDING_LAYOUT.remove(keyboard);
                try {
                    if (keyboard instanceof ViewGroup) {
                        SharedPreferences p = prefs(keyboard.getContext());
                        migrateLegacyLayout(p, (ViewGroup) keyboard);
                        JSONArray original = parseRows(p.getString("gboard_cafe_original_rows", null));
                        if (original != null) assignStableIds((ViewGroup) keyboard, original);
                        if (layoutNeedsApply((ViewGroup) keyboard)) {
                            applySavedLayout((ViewGroup) keyboard);
                            keyboard.requestLayout();
                            keyboard.invalidate();
                        }
                    }
                } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}
    }

    private static boolean layoutNeedsApply(ViewGroup root) {
        try {
            SharedPreferences p = prefs(root.getContext());
            JSONArray original = parseRows(p.getString("gboard_cafe_original_rows", null));
            JSONArray desired = parseRows(p.getString("gboard_cafe_custom_rows", null));
            if (desired == null) desired = original;
            if (desired == null || !matchesOriginalKeySet(root, original)) return false;
            JSONArray current = captureRows(root);
            if (current.length() != desired.length()) return true;
            for (int i = 0; i < desired.length(); i++) {
                JSONArray want = desired.optJSONArray(i);
                JSONArray have = current.optJSONArray(i);
                if (want == null || have == null || !want.toString().equals(have.toString())) return true;
            }
            return false;
        } catch (Throwable ignored) { return false; }
    }

    private static void applySavedLayout(ViewGroup root) {
        try {
            SharedPreferences p = prefs(root.getContext());
            migrateLegacyLayout(p, root);
            JSONArray original = parseRows(p.getString("gboard_cafe_original_rows", null));
            JSONArray desired = parseRows(p.getString("gboard_cafe_custom_rows", null));
            if (desired == null) desired = original;
            if (desired == null) return;
            assignStableIds(root, original == null ? desired : original);

            ArrayList<ViewGroup> groups = discoverKeyRows(root);
            if (groups.size() != desired.length() || !matchesOriginalKeySet(root, original)) return;

            Map<String, View> views = new HashMap<>();
            Map<String, ViewGroup> originalParents = new HashMap<>();
            Map<String, ViewGroup.LayoutParams> originalParams = new HashMap<>();
            Map<String, String> customText = new HashMap<>();
            ArrayList<View> allViews = new ArrayList<>();

            for (int rowIndex = 0; rowIndex < groups.size(); rowIndex++) {
                ViewGroup group = groups.get(rowIndex);
                for (int i = 0; i < group.getChildCount(); i++) {
                    View key = group.getChildAt(i);
                    if (!isKey(key)) continue;
                    String id = label(key);
                    if (TextUtils.isEmpty(id)) continue;
                    if (!views.containsKey(id)) {
                        views.put(id, key);
                        originalParents.put(id, group);
                        originalParams.put(id, key.getLayoutParams());
                        if (isCustomToken(id)) customText.put(id, getDisplayLabel(id));
                        allViews.add(key);
                    }
                }
            }

            // Remove old children first so cross-row moves and ordering are deterministic.
            for (View key : allViews) {
                ViewGroup parent = originalParents.get(label(key));
                if (parent != null) parent.removeView(key);
            }

            Set<String> placed = new HashSet<>();
            ArrayList<ArrayList<View>> activeByRow = new ArrayList<>();
            for (int i = 0; i < groups.size(); i++) activeByRow.add(new ArrayList<>());

            for (int rowIndex = 0; rowIndex < desired.length(); rowIndex++) {
                JSONArray wantedRow = desired.optJSONArray(rowIndex);
                if (wantedRow == null) continue;
                ViewGroup target = groups.get(rowIndex);
                for (int col = 0; col < wantedRow.length(); col++) {
                    String id = wantedRow.optString(col, "");
                    if (TextUtils.isEmpty(id) || !placed.add(id)) continue;
                    View key = views.get(id);
                    if (key == null && isCustomToken(id)) {
                        key = createCustomKeyView(target, id);
                        views.put(id, key);
                        originalParents.put(id, target);
                        originalParams.put(id, null);
                        customText.put(id, getDisplayLabel(id));
                    }
                    if (key == null) continue;
                    ViewGroup.LayoutParams params = layoutParamsForRow(target, key, originalParams.get(id));
                    key.setVisibility(View.VISIBLE);
                    target.addView(key, params);
                    activeByRow.get(rowIndex).add(key);
                }
            }

            // Omitted original keys are deletions. Keep them attached but GONE so
            // the Gboard hierarchy can still restore them without recreating views.
            for (String id : views.keySet()) {
                if (placed.contains(id) || isCustomToken(id)) continue;
                View key = views.get(id);
                int homeRow = stableRowIndex(id);
                if (homeRow < 0 || homeRow >= groups.size()) homeRow = 0;
                ViewGroup home = groups.get(homeRow);
                key.setVisibility(View.GONE);
                ViewGroup.LayoutParams params = layoutParamsForRow(home, key, originalParams.get(id));
                home.addView(key, params);
            }

            for (int rowIndex = 0; rowIndex < groups.size(); rowIndex++) {
                rebalanceRow(groups.get(rowIndex), activeByRow.get(rowIndex));
            }
        } catch (Throwable ignored) {}
    }

    private static ViewGroup.LayoutParams layoutParamsForRow(
            ViewGroup target, View key, ViewGroup.LayoutParams original) {
        try {
            if (target instanceof LinearLayout) {
                LinearLayout.LayoutParams params;
                if (original instanceof LinearLayout.LayoutParams) {
                    params = new LinearLayout.LayoutParams((LinearLayout.LayoutParams) original);
                } else if (key.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                    params = new LinearLayout.LayoutParams(
                            (LinearLayout.LayoutParams) key.getLayoutParams());
                } else {
                    params = new LinearLayout.LayoutParams(0,
                            ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                }
                float weight = params.weight > 0f ? params.weight
                        : (params.width > 0 ? Math.max(1, params.width) : 1f);
                params.width = 0;
                params.weight = weight;
                return params;
            }
            if (original != null) return original;
            return new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        } catch (Throwable ignored) {
            return new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private static void rebalanceRow(ViewGroup group, ArrayList<View> active) {
        try {
            if (!(group instanceof LinearLayout)) {
                group.requestLayout();
                return;
            }
            float total = 0f;
            for (View key : active) {
                ViewGroup.LayoutParams raw = key.getLayoutParams();
                if (raw instanceof LinearLayout.LayoutParams) {
                    LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) raw;
                    if (lp.weight <= 0f) lp.weight = 1f;
                    lp.width = 0;
                    total += lp.weight;
                    key.setLayoutParams(lp);
                }
            }
            // A zero weight sum lets LinearLayout recalculate from visible children.
            // Keeping the previous explicit sum after deleting every key would leave
            // invisible weight reserved and create a blank strip in the row.
            ((LinearLayout) group).setWeightSum(total);
            group.requestLayout();
        } catch (Throwable ignored) {}
    }

    private static void refreshKeyboard() {
        try {
            final View view = lastKeyboard.get();
            if (view != null) scheduleApply(view);
        } catch (Throwable ignored) {}
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
                if (row == null) return new String[0][];
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
            Set<String> seen = new HashSet<>();
            for (String[] row : rows) {
                if (row == null) return;
                JSONArray inner = new JSONArray();
                for (String key : row) {
                    if (TextUtils.isEmpty(key) || !seen.add(key)) return;
                    inner.put(key);
                }
                outer.put(inner);
            }
            prefs(context).edit().putString("gboard_cafe_custom_rows", outer.toString()).apply();
            refreshKeyboard();
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
            report.append("Gboard Cafe diagnostics\n\n");
            report.append("Package: ").append(context.getPackageName()).append('\n');
            report.append("Key effects enabled: ")
                    .append(p.getBoolean("gboard_cafe_enabled", false)).append('\n');
            report.append("Key effect: ")
                    .append(p.getString("gboard_cafe_effect", "ripple")).append('\n');
            report.append("Board effect: ")
                    .append(p.getString("gboard_cafe_board_effect", "none")).append('\n');
            report.append("Color: ")
                    .append(p.getString("gboard_cafe_color", "#CFB08E")).append('\n');
            report.append("Duration: ")
                    .append(p.getInt("gboard_cafe_duration", 260)).append(" ms\n");
            report.append("Captured rows: ").append(rowCount).append('\n');
            report.append("Captured keys: ").append(keyCount).append('\n');
            report.append("Hidden keys: ").append(hidden == null ? 0 : hidden.length).append('\n');
            report.append("Key touch hooks: ").append(keyTouchEvents).append('\n');
            report.append("Board touch hooks: ").append(boardTouchEvents).append('\n');
            report.append("Layout callbacks: ").append(layoutCallbacks).append('\n');
            report.append("Last layout status: ").append(lastLayoutStatus).append('\n');
            report.append("Last applied keys: ").append(lastAppliedKeyCount).append('\n');
            report.append("Last applied rows: ").append(lastAppliedRowCount).append('\n');
            report.append("Root: ").append(lastKeyboard.get() == null
                    ? "nenhum teclado ativo" : lastKeyboard.get().getClass().getName()).append('\n');
            try {
                android.content.pm.PackageInfo info = context.getPackageManager()
                        .getPackageInfo(context.getPackageName(), 0);
                report.append("App version: ").append(info.versionName).append('\n');
            } catch (Throwable ignored) {}
            return report.toString();
        } catch (Throwable error) {
            return "Gboard Cafe diagnostics unavailable: "
                    + error.getClass().getName() + ": " + error.getMessage();
        }
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class BoardEffectDrawable extends Drawable {
        private static final long FADE_START_RATIO = 62L;
        private final View host;
        private final String effect;
        private final int color;
        private final int duration;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float x;
        private float y;
        private long startedAt;
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
            this.x = x;
            this.y = y;
            startedAt = SystemClock.uptimeMillis();
            removalPosted = false;
            host.postDelayed(() -> postRemoval(), duration + 40L);
        }

        @Override public void draw(Canvas canvas) {
            try {
                long elapsed = Math.max(0L, SystemClock.uptimeMillis() - startedAt);
                if (elapsed >= duration) {
                    postRemoval();
                    return;
                }
                float progress = clampFloat(elapsed / (float) duration, 0f, 1f);
                float eased = 1f - (float) Math.pow(1f - progress, 3d);
                float fadeStart = duration * (FADE_START_RATIO / 100f);
                float fade = elapsed <= fadeStart ? 1f
                        : clampFloat(1f - (elapsed - fadeStart) / (duration - fadeStart), 0f, 1f);
                float diagonal = (float) Math.hypot(host.getWidth(), host.getHeight());
                float reach = Math.max(26f, diagonal * 0.48f) * eased;
                paint.setColor(color);
                paint.setAlpha((int) (220f * fade));
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.4f);

                switch (effect) {
                    case "ring":
                        canvas.drawCircle(x, y, 5f + reach, paint);
                        canvas.drawCircle(x, y, 3f + reach * 0.66f, paint);
                        canvas.drawCircle(x, y, 1f + reach * 0.30f, paint);
                        break;
                    case "outline":
                        paint.setStrokeWidth(2f);
                        float inset = 2f + (1f - eased) * 7f;
                        rect.set(inset, inset, Math.max(inset, host.getWidth() - inset),
                                Math.max(inset, host.getHeight() - inset));
                        canvas.drawRoundRect(rect, 18f, 18f, paint);
                        break;
                    case "spark":
                    case "sparkSpin":
                    case "firecracker": {
                        int count = "firecracker".equals(effect) ? 12 : 8;
                        float rotation = "sparkSpin".equals(effect) ? eased * 2.8f : 0f;
                        paint.setStrokeWidth("firecracker".equals(effect) ? 2.6f : 1.9f);
                        for (int i = 0; i < count; i++) {
                            double angle = (Math.PI * 2d * i / count) + rotation;
                            float dx = (float) Math.cos(angle);
                            float dy = (float) Math.sin(angle);
                            float inner = 4f + reach * 0.12f;
                            float outer = 8f + reach * (0.54f + (i % 3) * 0.07f);
                            canvas.drawLine(x + dx * inner, y + dy * inner,
                                    x + dx * outer, y + dy * outer, paint);
                            if ("firecracker".equals(effect)) {
                                canvas.drawCircle(x + dx * outer, y + dy * outer,
                                        1.7f + (i % 2), paint);
                            }
                        }
                        break;
                    }
                    case "blob":
                    case "blobSpread":
                    case "paintGun": {
                        paint.setStyle(Paint.Style.FILL);
                        float radius = "blob".equals(effect) ? 12f + eased * 21f
                                : ("blobSpread".equals(effect) ? 8f + reach * 0.34f
                                : 7f + reach * 0.17f);
                        paint.setAlpha((int) (("paintGun".equals(effect) ? 100f : 78f) * fade));
                        canvas.drawCircle(x, y, radius, paint);
                        int count = "paintGun".equals(effect) ? 9 : 7;
                        for (int i = 0; i < count; i++) {
                            double angle = (Math.PI * 2d * i / count) + (i * 0.13d);
                            float spread = radius * ("paintGun".equals(effect) ? 1.35f : 0.72f);
                            float dx = (float) Math.cos(angle);
                            float dy = (float) Math.sin(angle);
                            float dot = radius * (0.18f + (i % 3) * 0.045f);
                            canvas.drawCircle(x + dx * spread, y + dy * spread, dot, paint);
                        }
                        break;
                    }
                    default:
                        break;
                }
                host.postInvalidateOnAnimation();
            } catch (Throwable ignored) {
                postRemoval();
            }
        }

        void removeNow() {
            postRemoval();
        }

        private void postRemoval() {
            if (removalPosted) return;
            removalPosted = true;
            host.post(() -> {
                try { host.getOverlay().remove(this); } catch (Throwable ignored) {}
                if (BOARD_EFFECTS.get(host) == this) BOARD_EFFECTS.remove(host);
            });
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) {
            paint.setColorFilter(filter);
            invalidateSelf();
        }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static final class EffectDrawable extends Drawable {
        private static final long FADE_START_RATIO = 58L;
        private final View host;
        private final String effect;
        private final int color;
        private final int duration;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();
        private float x;
        private float y;
        private long startedAt;
        private boolean removalPosted;

        EffectDrawable(View host, String effect, int color, int duration) {
            this.host = host;
            this.effect = effect;
            this.color = color;
            this.duration = duration;
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        void start(float x, float y) {
            this.x = x;
            this.y = y;
            startedAt = SystemClock.uptimeMillis();
            removalPosted = false;
            host.postDelayed(() -> postRemoval(), duration + 40L);
        }

        @Override public void draw(Canvas canvas) {
            try {
                long elapsed = Math.max(0L, SystemClock.uptimeMillis() - startedAt);
                if (elapsed >= duration) {
                    postRemoval();
                    return;
                }
                float progress = clampFloat(elapsed / (float) duration, 0f, 1f);
                float eased = 1f - (float) Math.pow(1f - progress, 3d);
                float fadeStart = duration * (FADE_START_RATIO / 100f);
                float fade = elapsed <= fadeStart ? 1f
                        : clampFloat(1f - (elapsed - fadeStart) / (duration - fadeStart), 0f, 1f);
                float minSide = Math.max(1f, Math.min(host.getWidth(), host.getHeight()));
                float maxRadius = minSide * 0.62f;
                float radius = 3f + maxRadius * eased;
                paint.setColor(color);
                paint.setAlpha((int) (230f * fade));
                paint.setStyle(Paint.Style.FILL);

                switch (effect) {
                    case "flash":
                        paint.setAlpha((int) (90f * fade));
                        canvas.drawRoundRect(1, 1, Math.max(1, host.getWidth() - 1),
                                Math.max(1, host.getHeight() - 1), 9, 9, paint);
                        break;
                    case "outline":
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(2.2f);
                        bounds.set(1.5f, 1.5f,
                                Math.max(1.5f, host.getWidth() - 1.5f),
                                Math.max(1.5f, host.getHeight() - 1.5f));
                        canvas.drawRoundRect(bounds, 8, 8, paint);
                        break;
                    case "bounce":
                        canvas.save();
                        float pulse = (float) Math.sin(Math.PI * progress);
                        float scale = 1f - 0.075f * pulse;
                        canvas.scale(scale, scale, host.getWidth() / 2f, host.getHeight() / 2f);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(2.2f);
                        paint.setAlpha((int) (220f * fade));
                        canvas.drawRoundRect(2, 2, Math.max(2, host.getWidth() - 2),
                                Math.max(2, host.getHeight() - 2), 8, 8, paint);
                        canvas.restore();
                        break;
                    case "sparks":
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(1.8f);
                        paint.setAlpha((int) (230f * fade));
                        for (int i = 0; i < 6; i++) {
                            double angle = i * Math.PI / 3d;
                            float dx = (float) Math.cos(angle);
                            float dy = (float) Math.sin(angle);
                            float end = 4f + radius * (0.78f + (i % 2) * 0.14f);
                            canvas.drawLine(x + dx * 3, y + dy * 3,
                                    x + dx * end, y + dy * end, paint);
                            canvas.drawCircle(x + dx * end, y + dy * end, 1.5f, paint);
                        }
                        break;
                    case "paint":
                        paint.setAlpha((int) (82f * fade));
                        canvas.drawCircle(x, y, radius, paint);
                        break;
                    case "blob":
                    case "blobSpread": {
                        paint.setStyle(Paint.Style.FILL);
                        paint.setAlpha((int) (("blob".equals(effect) ? 140f : 96f) * fade));
                        float spread = 4f + eased * ("blob".equals(effect) ? 13f : 21f);
                        canvas.drawCircle(x, y, spread * 0.82f, paint);
                        for (int i = 0; i < 6; i++) {
                            double angle = i * Math.PI * 2d / 6d;
                            float dx = (float) Math.cos(angle);
                            float dy = (float) Math.sin(angle);
                            canvas.drawCircle(x + dx * spread * 0.66f,
                                    y + dy * spread * 0.66f, spread * 0.36f, paint);
                        }
                        break;
                    }
                    case "footprint": {
                        paint.setStyle(Paint.Style.FILL);
                        paint.setAlpha((int) (135f * fade));
                        float size = 2.7f + 3.4f * eased;
                        canvas.drawOval(x - size * 0.72f, y - size * 1.55f,
                                x + size * 0.72f, y + size * 1.55f, paint);
                        for (int i = 0; i < 3; i++) {
                            float toe = size * (i - 1) * 0.78f;
                            canvas.drawCircle(x + toe, y - size * 1.9f, size * 0.30f, paint);
                        }
                        break;
                    }
                    case "ripple":
                    default:
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(2.0f);
                        paint.setAlpha((int) (230f * fade));
                        canvas.drawCircle(x, y, 3f + radius, paint);
                        paint.setStrokeWidth(1.1f);
                        paint.setAlpha((int) (130f * fade));
                        canvas.drawCircle(x, y, 2f + radius * 0.58f, paint);
                        break;
                }
                host.postInvalidateOnAnimation();
            } catch (Throwable ignored) {
                postRemoval();
            }
        }

        void removeNow() { postRemoval(); }

        private void postRemoval() {
            if (removalPosted) return;
            removalPosted = true;
            host.post(() -> {
                try { host.getOverlay().remove(this); } catch (Throwable ignored) {}
                if (EFFECTS.get(host) == this) EFFECTS.remove(host);
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

        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

}
