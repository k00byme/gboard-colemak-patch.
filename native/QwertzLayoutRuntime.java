package app.gboard.colemak;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.SparseArray;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

/** Runtime bridge called when Gboard creates a KeyMappingDef from layout XML. */
public final class QwertzLayoutRuntime {
    private static final String TAG = "QwertzCustomLayout";
    private static final String PREFERENCES_SUFFIX = "_preferences";
    private static final String STATUS_KEY = "qwertz_runtime_status";
    private static final String DEFAULT_LAYOUT = "qwertzuiopasdfghjklyxcvbnm";

    private static final int[] VIEW_IDS = {
            0x7f0b0039, 0x7f0b003a, 0x7f0b003b, 0x7f0b003c, 0x7f0b003e,
            0x7f0b0040, 0x7f0b0042, 0x7f0b0043, 0x7f0b0044, 0x7f0b0045,
            0x7f0b0024, 0x7f0b0025, 0x7f0b0026, 0x7f0b0027, 0x7f0b0029,
            0x7f0b002b, 0x7f0b002d, 0x7f0b002e, 0x7f0b002f,
            0x7f0b000e, 0x7f0b000f, 0x7f0b0011, 0x7f0b0013, 0x7f0b0015,
            0x7f0b0017, 0x7f0b0018,
    };
    private static final int SPLIT_MIDDLE_VIEW_ID = 0x7f0b002a;
    private static final int SPLIT_BOTTOM_VIEW_ID = 0x7f0b0014;

    // SoftKeyDef.f is the key resource ID in Gboard 18.5.4.
    private static final int[] LOWERCASE_KEY_IDS = {
            0x7f0b198e, 0x7f0b19fe, 0x7f0b1905, 0x7f0b1992, 0x7f0b19c5,
            0x7f0b1a0d, 0x7f0b19e6, 0x7f0b1934, 0x7f0b1971, 0x7f0b1987,
            0x7f0b18d4, 0x7f0b199d, 0x7f0b18f7, 0x7f0b191b, 0x7f0b191f,
            0x7f0b192e, 0x7f0b1945, 0x7f0b1949, 0x7f0b1951,
            0x7f0b1a06, 0x7f0b1a03, 0x7f0b18e9, 0x7f0b19fb, 0x7f0b18e4,
            0x7f0b1965, 0x7f0b195e,
    };
    private static final int[] UPPERCASE_KEY_IDS = {
            0x7f0b1877, 0x7f0b18a5, 0x7f0b1813, 0x7f0b1878, 0x7f0b1886,
            0x7f0b18ad, 0x7f0b1890, 0x7f0b1838, 0x7f0b1860, 0x7f0b1875,
            0x7f0b17f7, 0x7f0b187d, 0x7f0b180f, 0x7f0b1826, 0x7f0b1828,
            0x7f0b1833, 0x7f0b1849, 0x7f0b184b, 0x7f0b184f,
            0x7f0b18a9, 0x7f0b18a8, 0x7f0b1808, 0x7f0b18a3, 0x7f0b1806,
            0x7f0b1857, 0x7f0b1853,
    };

    private QwertzLayoutRuntime() { }

    public static void apply(Object keyMappingDef) {
        Application application = null;
        int sparseArrays = 0;
        int resolvedEntries = 0;
        int changedGroups = 0;
        int inspectedFields = 0;
        StringBuilder structure = new StringBuilder();
        try {
            application = currentApplication();
            String[] rows = readRows(application);
            if (!validRows(rows)) {
                saveStatus(application, "Configuração inválida; usando layout original.");
                return;
            }
            if (keyMappingDef == null) {
                saveStatus(application, "Gboard chamou o runtime sem KeyMappingDef.");
                return;
            }

            // Include inherited fields: the mapping container may keep its arrays in a base class.
            Class<?> type = keyMappingDef.getClass();
            while (type != null && type != Object.class) {
                for (Field field : type.getDeclaredFields()) {
                    inspectedFields++;
                    if (Modifier.isStatic(field.getModifiers()) ||
                            !SparseArray.class.isAssignableFrom(field.getType())) {
                        continue;
                    }
                    field.setAccessible(true);
                    Object value = field.get(keyMappingDef);
                    if (!(value instanceof SparseArray)) continue;
                    sparseArrays++;
                    SparseArray<?> mappings = (SparseArray<?>) value;
                    resolvedEntries += countResolvableEntries(mappings);
                    if (structure.length() < 1800) {
                        structure.append(describeSparseArray(field.getName(), mappings));
                    }
                    if (remapIfStock(mappings, LOWERCASE_KEY_IDS, rows) ||
                            remapIfStock(mappings, UPPERCASE_KEY_IDS, rows)) {
                        changedGroups++;
                    }
                }
                type = type.getSuperclass();
            }

            String status;
            if (changedGroups > 0) {
                status = "APLICADO: " + changedGroups + " grupo(s) de teclas; " +
                        "sequências " + rows[0] + " / " + rows[1] + " / " + rows[2];
            } else if (sparseArrays == 0) {
                status = "NÃO APLICADO: nenhum SparseArray encontrado no KeyMappingDef (" +
                        inspectedFields + " campos inspecionados).";
            } else if (resolvedEntries == 0) {
                status = "NÃO APLICADO: " + sparseArrays + " SparseArray(s), mas nenhum " +
                        "SoftKeyDef reconhecido pelos IDs desta versão. Estrutura: " +
                        structure.toString();
            } else {
                status = "NÃO APLICADO: " + sparseArrays + " SparseArray(s) e " +
                        resolvedEntries + " entradas reconhecidas, mas a estrutura não " +
                        "corresponde ao QWERTZ esperado.";
            }
            saveStatus(application, status);
            Log.i(TAG, status);
        } catch (Throwable throwable) {
            String status = "ERRO no runtime: " + throwable.getClass().getSimpleName() +
                    (throwable.getMessage() == null ? "" : " - " + throwable.getMessage());
            if (application == null) {
                try { application = currentApplication(); } catch (Throwable ignored) { }
            }
            if (application != null) saveStatus(application, status);
            Log.w(TAG, status, throwable);
        }
    }

    private static void saveStatus(Application application, String status) {
        try {
            application.getSharedPreferences(
                    application.getPackageName() + PREFERENCES_SUFFIX, Context.MODE_PRIVATE)
                    .edit().putString(STATUS_KEY, status).apply();
        } catch (Throwable ignored) {
            Log.w(TAG, "Could not persist QWERTZ runtime status: " + status);
        }
    }

    private static int countResolvableEntries(SparseArray<?> mappings) {
        int count = 0;
        for (int i = 0; i < mappings.size(); i++) {
            if (resolveSoftKeyId(mappings.valueAt(i)) != 0) count++;
        }
        return count;
    }

    private static boolean remapIfStock(SparseArray<?> mappings, int[] keyIds, String[] rows)
            throws Exception {
        if (mappings.size() < VIEW_IDS.length) return false;

        Map<Integer, Object> definitionsById = new HashMap<>();
        for (int i = 0; i < mappings.size(); i++) {
            Object mappingEntry = mappings.valueAt(i);
            int keyId = resolveSoftKeyId(mappingEntry);
            if (keyId != 0) definitionsById.put(keyId, mappingEntry);
        }
        if (definitionsById.isEmpty()) return false;

        // Refuse to touch non-QWERTZ layouts or an unexpected mapping structure.
        for (int i = 0; i < VIEW_IDS.length; i++) {
            Object current = mappings.get(VIEW_IDS[i]);
            if (current == null || resolveSoftKeyId(current) != keyIds[i]) return false;
        }

        String allLetters = rows[0] + rows[1] + rows[2];
        Object[] reordered = new Object[VIEW_IDS.length];
        for (int i = 0; i < VIEW_IDS.length; i++) {
            int sourceIndex = DEFAULT_LAYOUT.indexOf(allLetters.charAt(i));
            if (sourceIndex < 0) return false;
            Object definition = definitionsById.get(keyIds[sourceIndex]);
            if (definition == null) return false;
            reordered[i] = definition;
        }

        @SuppressWarnings("unchecked")
        SparseArray<Object> writable = (SparseArray<Object>) mappings;
        for (int i = 0; i < VIEW_IDS.length; i++) {
            writable.put(VIEW_IDS[i], reordered[i]);
        }

        Object splitMiddle = definitionsById.get(
                keyIds[DEFAULT_LAYOUT.indexOf(rows[1].charAt(4))]);
        Object splitBottom = definitionsById.get(
                keyIds[DEFAULT_LAYOUT.indexOf(rows[2].charAt(3))]);
        if (splitMiddle != null) writable.put(SPLIT_MIDDLE_VIEW_ID, splitMiddle);
        if (splitBottom != null) writable.put(SPLIT_BOTTOM_VIEW_ID, splitBottom);
        return true;
    }

    /**
     * Find a known Gboard SoftKeyDef resource ID by inspecting a small wrapper graph.
     * Gboard stores SoftKeyDef objects inside obfuscated mapping wrappers; relying on
     * one guessed wrapper field name made the old runtime silently skip the remap.
     */
    private static int resolveSoftKeyId(Object mappingEntry) {
        return findKnownId(mappingEntry, 0, new IdentityHashMap<Object, Boolean>());
    }

    private static int findKnownId(Object value, int depth, IdentityHashMap<Object, Boolean> visited) {
        if (value == null || depth > 9 || visited.containsKey(value)) return 0;
        visited.put(value, Boolean.TRUE);

        if (value instanceof Integer) {
            int id = ((Integer) value).intValue();
            return isKnownSoftKeyId(id) ? id : 0;
        }

        Class<?> valueType = value.getClass();
        String className = valueType.getName();
        if (className.equals("com.google.android.libraries.inputmethod.metadata.SoftKeyDef")) {
            int direct = readSoftKeyIdField(value);
            if (direct != 0) return direct;
        }

        if (value instanceof SparseArray) {
            SparseArray<?> array = (SparseArray<?>) value;
            for (int i = 0; i < array.size() && i < 64; i++) {
                int found = findKnownId(array.valueAt(i), depth + 1, visited);
                if (found != 0) return found;
            }
            return 0;
        }

        if (value instanceof Map) {
            int scanned = 0;
            for (Object nested : ((Map<?, ?>) value).values()) {
                if (scanned++ >= 64) break;
                int found = findKnownId(nested, depth + 1, visited);
                if (found != 0) return found;
            }
            return 0;
        }

        if (value instanceof Iterable) {
            int scanned = 0;
            for (Object nested : (Iterable<?>) value) {
                if (scanned++ >= 64) break;
                int found = findKnownId(nested, depth + 1, visited);
                if (found != 0) return found;
            }
            return 0;
        }

        if (valueType.isArray()) {
            if (!valueType.getComponentType().isPrimitive()) {
                Object[] values = (Object[]) value;
                for (int i = 0; i < values.length && i < 64; i++) {
                    int found = findKnownId(values[i], depth + 1, visited);
                    if (found != 0) return found;
                }
            }
            return 0;
        }

        if (value instanceof CharSequence || value instanceof Class ||
                value instanceof ClassLoader || value instanceof Context) return 0;

        Class<?> current = valueType;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                try {
                    field.setAccessible(true);
                    if (field.getType() == Integer.TYPE) {
                        int id = field.getInt(value);
                        if (isKnownSoftKeyId(id)) return id;
                    } else if (field.getType() == Integer.class) {
                        Object boxed = field.get(value);
                        if (boxed instanceof Integer && isKnownSoftKeyId(((Integer) boxed).intValue())) {
                            return ((Integer) boxed).intValue();
                        }
                    } else if (!field.getType().isPrimitive()) {
                        Object nested = field.get(value);
                        int found = findKnownId(nested, depth + 1, visited);
                        if (found != 0) return found;
                    }
                } catch (Throwable ignored) {
                    // Continue through other fields; some framework internals are hidden.
                }
            }
            current = current.getSuperclass();
        }
        return 0;
    }

    private static int readSoftKeyIdField(Object value) {
        Class<?> current = value.getClass();
        while (current != null && current != Object.class) {
            try {
                Field keyId = current.getDeclaredField("f");
                keyId.setAccessible(true);
                if (keyId.getType() == Integer.TYPE) {
                    int id = keyId.getInt(value);
                    return isKnownSoftKeyId(id) ? id : 0;
                }
            } catch (Throwable ignored) {
                // Fall back to scanning the other integer fields below.
            }
            current = current.getSuperclass();
        }
        return 0;
    }

    private static String describeSparseArray(String fieldName, SparseArray<?> array) {
        StringBuilder result = new StringBuilder();
        result.append(fieldName).append("(size=").append(array.size()).append(", keys=");
        result.append("[");
        for (int i = 0; i < array.size() && i < 6; i++) {
            if (i > 0) result.append(",");
            result.append("0x").append(Integer.toHexString(array.keyAt(i)));
        }
        result.append("], values=[");
        for (int i = 0; i < array.size() && i < 3; i++) {
            if (i > 0) result.append(",");
            Object value = array.valueAt(i);
            result.append(value == null ? "null" : describeObject(value));
        }
        result.append("]); ");
        return result.toString();
    }

    private static String describeObject(Object value) {
        StringBuilder result = new StringBuilder();
        Class<?> type = value.getClass();
        result.append(type.getName()).append("{");
        int count = 0;
        Class<?> current = type;
        while (current != null && current != Object.class && count < 10) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                if (count >= 10) break;
                if (count++ > 0) result.append(",");
                result.append(field.getName()).append(":").append(field.getType().getSimpleName());
                if (field.getType() == Integer.TYPE) {
                    try {
                        field.setAccessible(true);
                        result.append("=0x").append(Integer.toHexString(field.getInt(value)));
                    } catch (Throwable ignored) {
                        result.append("=?");
                    }
                }
            }
            current = current.getSuperclass();
        }
        result.append("}");
        return result.toString();
    }

    private static boolean isKnownSoftKeyId(int id) {
        for (int known : LOWERCASE_KEY_IDS) if (known == id) return true;
        for (int known : UPPERCASE_KEY_IDS) if (known == id) return true;
        return false;
    }

    private static String[] readRows(Application application) {
        SharedPreferences preferences = application.getSharedPreferences(
                application.getPackageName() + PREFERENCES_SUFFIX, Context.MODE_PRIVATE);
        return new String[] {
                preferences.getString("qwertz_top_row", "qwertzuiop").toLowerCase(Locale.ROOT),
                preferences.getString("qwertz_middle_row", "asdfghjkl").toLowerCase(Locale.ROOT),
                preferences.getString("qwertz_bottom_row", "yxcvbnm").toLowerCase(Locale.ROOT),
        };
    }

    private static Application currentApplication() throws Exception {
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Method currentApplication = activityThread.getDeclaredMethod("currentApplication");
        currentApplication.setAccessible(true);
        Object application = currentApplication.invoke(null);
        if (!(application instanceof Application)) {
            throw new IllegalStateException("Gboard Application context is unavailable");
        }
        return (Application) application;
    }

    private static boolean validRows(String[] rows) {
        if (rows == null || rows.length != 3 || rows[0] == null || rows[1] == null ||
                rows[2] == null || rows[0].length() != 10 || rows[1].length() != 9 ||
                rows[2].length() != 7) return false;
        boolean[] seen = new boolean[26];
        for (String row : rows) {
            for (int i = 0; i < row.length(); i++) {
                char letter = row.charAt(i);
                if (letter < 'a' || letter > 'z') return false;
                int index = letter - 'a';
                if (seen[index]) return false;
                seen[index] = true;
            }
        }
        for (boolean present : seen) if (!present) return false;
        return true;
    }
}
