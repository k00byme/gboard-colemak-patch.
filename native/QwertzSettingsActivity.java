package app.gboard.colemak;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.text.InputType;
import android.widget.Toast;

import java.util.Locale;

@SuppressWarnings("deprecation")
public final class QwertzSettingsActivity extends PreferenceActivity {
    private static final String KEY_TOP = "qwertz_top_row";
    private static final String KEY_MIDDLE = "qwertz_middle_row";
    private static final String KEY_BOTTOM = "qwertz_bottom_row";
    private static final String PREFERENCES_NAME = "com.google.android.inputmethod.latin_preferences";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("Personalizar QWERTZ");

        getPreferenceManager().setSharedPreferencesName(
                getPackageName() + "_preferences");
        getPreferenceManager().setSharedPreferencesMode(Context.MODE_PRIVATE);

        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(this);
        PreferenceCategory rows = new PreferenceCategory(this);
        rows.setTitle("Ordem das letras");
        screen.addPreference(rows);

        addRow(rows, KEY_TOP, "Fileira superior", "10 letras, sem espaços", "qwertzuiop", 10);
        addRow(rows, KEY_MIDDLE, "Fileira do meio", "9 letras, sem espaços", "asdfghjkl", 9);
        addRow(rows, KEY_BOTTOM, "Fileira inferior", "7 letras, sem espaços", "yxcvbnm", 7);

        Preference apply = new Preference(this);
        apply.setTitle("Aplicar e reiniciar Gboard");
        apply.setSummary("Salva as três fileiras e reinicia o Gboard para reconstruir o layout.");
        apply.setPersistent(false);
        apply.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                applyAndRestart();
                return true;
            }
        });
        rows.addPreference(apply);

        Preference rule = new Preference(this);
        rule.setTitle("Regra de validação");
        rule.setSummary("Use todas as letras de a a z exatamente uma vez. Depois de editar as três fileiras, toque em Aplicar e reiniciar Gboard.");
        rule.setSelectable(false);
        rows.addPreference(rule);

        setPreferenceScreen(screen);
    }

    private void addRow(PreferenceCategory category, String key, String title,
            String description, String defaultValue, int expectedLength) {
        EditTextPreference preference = new EditTextPreference(this);
        preference.setKey(key);
        preference.setTitle(title);
        preference.setDialogTitle(title);
        preference.setDialogMessage(description);
        preference.setDefaultValue(defaultValue);
        preference.setPersistent(true);
        preference.setSummary("Toque para editar a sequência de letras.");
        preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference item, Object newValue) {
                String value = String.valueOf(newValue).toLowerCase(Locale.ROOT);
                if (!isValidRow(value, expectedLength)) {
                    Toast.makeText(QwertzSettingsActivity.this,
                            "Digite " + expectedLength + " letras diferentes, sem espaços.",
                            Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            }
        });
        preference.getEditText().setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        category.addPreference(preference);
    }

    private void applyAndRestart() {
        SharedPreferences preferences = getSharedPreferences(
                getPackageName() + "_preferences", Context.MODE_PRIVATE);
        String top = preferences.getString(KEY_TOP, "qwertzuiop").toLowerCase(Locale.ROOT);
        String middle = preferences.getString(KEY_MIDDLE, "asdfghjkl").toLowerCase(Locale.ROOT);
        String bottom = preferences.getString(KEY_BOTTOM, "yxcvbnm").toLowerCase(Locale.ROOT);

        if (!isValidLayout(top, middle, bottom)) {
            Toast.makeText(this,
                    "As três fileiras precisam conter cada letra de a a z exatamente uma vez.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        boolean saved = preferences.edit()
                .putString(KEY_TOP, top)
                .putString(KEY_MIDDLE, middle)
                .putString(KEY_BOTTOM, bottom)
                .commit();
        if (!saved) {
            Toast.makeText(this, "Não foi possível salvar o layout.", Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this,
                "Layout salvo. Reiniciando Gboard para aplicar as alterações.",
                Toast.LENGTH_LONG).show();

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        }, 900);
    }

    private static boolean isValidLayout(String top, String middle, String bottom) {
        if (!isValidRow(top, 10) || !isValidRow(middle, 9) || !isValidRow(bottom, 7)) {
            return false;
        }
        boolean[] seen = new boolean[26];
        String all = top + middle + bottom;
        for (int i = 0; i < all.length(); i++) {
            int index = all.charAt(i) - 'a';
            if (index < 0 || index >= seen.length || seen[index]) return false;
            seen[index] = true;
        }
        for (boolean value : seen) if (!value) return false;
        return true;
    }

    private static boolean isValidRow(String value, int expectedLength) {
        if (value == null || value.length() != expectedLength) return false;
        boolean[] seen = new boolean[26];
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character < 'a' || character > 'z') return false;
            int index = character - 'a';
            if (seen[index]) return false;
            seen[index] = true;
        }
        return true;
    }
}
