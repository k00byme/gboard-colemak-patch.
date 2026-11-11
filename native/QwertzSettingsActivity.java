package app.gboard.colemak;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
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
    private static final String KEY_RUNTIME_STATUS = "qwertz_runtime_status";
    private static final String KEY_EFFECTS_ENABLED = "key_effects_enabled";
    private static final String KEY_EFFECT_TYPE = "key_effect_type";

    private Preference runtimeStatusPreference;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("Keyboard Studio");

        getPreferenceManager().setSharedPreferencesName(getPackageName() + "_preferences");
        getPreferenceManager().setSharedPreferencesMode(Context.MODE_PRIVATE);

        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(this);

        PreferenceCategory effects = new PreferenceCategory(this);
        effects.setTitle("Efeitos das teclas");
        screen.addPreference(effects);

        CheckBoxPreference effectsEnabled = new CheckBoxPreference(this);
        effectsEnabled.setKey(KEY_EFFECTS_ENABLED);
        effectsEnabled.setTitle("Ativar efeitos ao tocar");
        effectsEnabled.setSummary("Mostra uma animação visual quando uma tecla é pressionada.");
        effectsEnabled.setDefaultValue(Boolean.TRUE);
        effectsEnabled.setPersistent(true);
        effects.addPreference(effectsEnabled);

        ListPreference effectType = new ListPreference(this);
        effectType.setKey(KEY_EFFECT_TYPE);
        effectType.setTitle("Tipo de efeito");
        effectType.setDialogTitle("Escolher efeito");
        effectType.setEntries(new CharSequence[] {
                "Spark", "Blob", "Blob Spread", "Color Flash",
                "Footprint", "Outline", "Bounce", "Ring Spread", "Firecracker"
        });
        effectType.setEntryValues(new CharSequence[] {
                "spark", "blob", "blob_spread", "flash",
                "footprint", "outline", "bounce", "ring", "firecracker"
        });
        effectType.setDefaultValue("spark");
        effectType.setPersistent(true);
        effectType.setSummary("Escolha a animação mostrada ao tocar nas teclas.");
        effects.addPreference(effectType);

        PreferenceCategory layout = new PreferenceCategory(this);
        layout.setTitle("Organização das teclas");
        screen.addPreference(layout);

        Preference visualEditor = new Preference(this);
        visualEditor.setTitle("Reorganizar visualmente");
        visualEditor.setSummary("Segure e arraste as letras entre posições e fileiras.");
        visualEditor.setPersistent(false);
        visualEditor.setOnPreferenceClickListener(preference -> {
            SharedPreferences prefs = getSharedPreferences(
                    getPackageName() + "_preferences", Context.MODE_PRIVATE);
            KeyRowEditorDialog.show(this, prefs, this::applyAndRestart);
            return true;
        });
        layout.addPreference(visualEditor);

        PreferenceCategory manual = new PreferenceCategory(this);
        manual.setTitle("Editor manual das fileiras");
        screen.addPreference(manual);

        addRow(manual, KEY_TOP, "Fileira superior", "10 letras, sem espaços", "qwertzuiop", 10);
        addRow(manual, KEY_MIDDLE, "Fileira do meio", "9 letras, sem espaços", "asdfghjkl", 9);
        addRow(manual, KEY_BOTTOM, "Fileira inferior", "7 letras, sem espaços", "yxcvbnm", 7);

        Preference apply = new Preference(this);
        apply.setTitle("Aplicar e reiniciar Gboard");
        apply.setSummary("Salva as três fileiras e reinicia o Gboard para reconstruir o layout.");
        apply.setPersistent(false);
        apply.setOnPreferenceClickListener(preference -> {
            applyAndRestart();
            return true;
        });
        manual.addPreference(apply);

        runtimeStatusPreference = new Preference(this);
        runtimeStatusPreference.setTitle("Diagnóstico do layout");
        runtimeStatusPreference.setSelectable(false);
        manual.addPreference(runtimeStatusPreference);

        Preference rule = new Preference(this);
        rule.setTitle("Regra de validação");
        rule.setSummary("Use todas as letras de a a z exatamente uma vez. Depois de editar, toque em Aplicar e reiniciar Gboard.");
        rule.setSelectable(false);
        manual.addPreference(rule);

        setPreferenceScreen(screen);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRuntimeStatus();
    }

    private void refreshRuntimeStatus() {
        if (runtimeStatusPreference == null) return;
        SharedPreferences preferences = getSharedPreferences(
                getPackageName() + "_preferences", Context.MODE_PRIVATE);
        String status = preferences.getString(KEY_RUNTIME_STATUS,
                "Aguardando o Gboard montar o teclado. Abra um campo de texto e volte aqui.");
        runtimeStatusPreference.setSummary(status);
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
        preference.setOnPreferenceChangeListener((item, newValue) -> {
            String value = String.valueOf(newValue).toLowerCase(Locale.ROOT);
            if (!isValidRow(value, expectedLength)) {
                Toast.makeText(QwertzSettingsActivity.this,
                        "Digite " + expectedLength + " letras diferentes, sem espaços.",
                        Toast.LENGTH_LONG).show();
                return false;
            }
            return true;
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
                .putString(KEY_RUNTIME_STATUS,
                        "Configuração salva. Aguardando o Gboard montar o teclado.")
                .commit();
        if (!saved) {
            Toast.makeText(this, "Não foi possível salvar o layout.", Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this,
                "Layout salvo. Reiniciando Gboard para aplicar as alterações.",
                Toast.LENGTH_LONG).show();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            finish();
            android.os.Process.killProcess(android.os.Process.myPid());
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
