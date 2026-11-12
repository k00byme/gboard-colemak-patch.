package app.gboard.cafe.runtime;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class GboardCafeSettingsActivity extends Activity {
    private static final String[] EFFECT_VALUES = {
            "ripple", "flash", "outline", "bounce", "sparks", "paint",
            "blob", "blobSpread", "footprint", "none"
    };
    private static final String[] EFFECT_LABELS = {
            "Ripple", "Flash de cor", "Contorno pulsante", "Bounce",
            "Partículas", "Tinta", "Blob", "Blob expansivo", "Pegadas", "Desativado"
    };
    private static final String[] BOARD_EFFECT_VALUES = {
            "ring", "spark", "sparkSpin", "firecracker", "blob",
            "blobSpread", "outline", "paintGun", "none"
    };
    private static final String[] BOARD_EFFECT_LABELS = {
            "Anéis expansivos", "Faíscas", "Faíscas giratórias", "Fogos",
            "Blob", "Blob expansivo", "Onda de contorno", "Paint gun", "Desativado"
    };

    private SharedPreferences prefs;
    private Switch enabled;
    private Spinner effect;
    private Spinner boardEffect;
    private EditText color;
    private Spinner duration;
    private static final int[] DURATION_VALUES = {80, 120, 180, 260, 350, 500, 700, 900, 1200};
    private static final String[] DURATION_LABELS = {
            "80 ms", "120 ms", "180 ms", "260 ms", "350 ms",
            "500 ms", "700 ms", "900 ms", "1200 ms"
    };
    private String stage = "inicialização";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            stage = "abrindo armazenamento das configurações";
            prefs = getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE);
            stage = "montando a interface";
            buildScreen();
        } catch (Throwable error) {
            Log.e("GboardCafe", "Falha durante " + stage, error);
            showStartupError(error);
        }
    }


    private void showStartupError(Throwable error) {
        try {
            android.widget.ScrollView scroll = new android.widget.ScrollView(this);
            LinearLayout page = new LinearLayout(this);
            page.setOrientation(LinearLayout.VERTICAL);
            page.setPadding(dp(20), dp(24), dp(20), dp(24));
            page.setBackgroundColor(Color.WHITE);

            TextView title = new TextView(this);
            title.setText("Gboard Cafe não conseguiu abrir as configurações.");
            title.setTextColor(Color.BLACK);
            title.setTextSize(19);
            page.addView(title);

            TextView explanation = new TextView(this);
            explanation.setText("Falha em: " + stage + "\n\n"
                    + error.getClass().getName() + "\n"
                    + (error.getMessage() == null ? "(sem mensagem)" : error.getMessage()));
            explanation.setTextColor(0xFFB00020);
            explanation.setTextSize(14);
            explanation.setTextIsSelectable(true);
            explanation.setPadding(0, dp(16), 0, dp(16));
            page.addView(explanation);

            Button copy = button("Copiar diagnóstico");
            copy.setOnClickListener(v -> {
                try {
                    android.content.ClipboardManager clipboard =
                            (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    String report = "Gboard Cafe\nEtapa: " + stage + "\n"
                            + Log.getStackTraceString(error);
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Gboard Cafe", report));
                    android.widget.Toast.makeText(this, "Diagnóstico copiado", android.widget.Toast.LENGTH_SHORT).show();
                } catch (Throwable ignored) {}
            });
            page.addView(copy);

            Button close = button("Fechar");
            close.setOnClickListener(v -> finish());
            page.addView(close);

            scroll.addView(page);
            setContentView(scroll);
        } catch (Throwable fallbackError) {
            TextView fallback = new TextView(this);
            fallback.setText("Falha: " + error.getClass().getSimpleName()
                    + "\nEtapa: " + stage);
            fallback.setTextColor(Color.BLACK);
            fallback.setPadding(32, 32, 32, 32);
            setContentView(fallback);
        }
    }

    private void buildScreen() {
        stage = "criando a tela principal";
        int pad = dp(18);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(pad, pad, pad, pad);
        page.setBackgroundColor(Color.WHITE);

        TextView heading = new TextView(this);
        heading.setText("Gboard Cafe");
        heading.setTextColor(Color.BLACK);
        heading.setTextSize(24);
        heading.setPadding(0, 0, 0, dp(8));
        page.addView(heading);

        TextView intro = new TextView(this);
        intro.setText("Efeitos visuais e organização das teclas.");
        intro.setTextColor(0xFF555555);
        intro.setTextSize(14);
        intro.setPadding(0, 0, 0, dp(16));
        page.addView(intro);

        stage = "criando o controle de ativação";
        enabled = new Switch(this);
        enabled.setText("Ativar efeitos nas teclas e no teclado");
        enabled.setTextColor(Color.BLACK);
        enabled.setChecked(prefs.getBoolean("gboard_cafe_enabled", false));
        page.addView(enabled);

        addLabel(page, "Efeito");
        stage = "criando o seletor de efeitos por tecla";
        effect = new Spinner(this);
        ArrayAdapter<String> keyAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, EFFECT_LABELS);
        keyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        effect.setAdapter(keyAdapter);
        String current = prefs.getString("gboard_cafe_effect", "ripple");
        effect.setSelection(indexOf(EFFECT_VALUES, current));
        page.addView(effect);

        addLabel(page, "Efeito na área inteira do teclado");
        stage = "criando o seletor de efeitos do teclado";
        boardEffect = new Spinner(this);
        ArrayAdapter<String> boardAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, BOARD_EFFECT_LABELS);
        boardAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        boardEffect.setAdapter(boardAdapter);
        String currentBoard = prefs.getString("gboard_cafe_board_effect", "none");
        boardEffect.setSelection(indexOf(BOARD_EFFECT_VALUES, currentBoard));
        page.addView(boardEffect);

        stage = "criando a configuração de cor";
        addLabel(page, "Cor (hexadecimal)");
        color = new EditText(this);
        color.setSingleLine(true);
        color.setHint("#CFB08E");
        color.setText(prefs.getString("gboard_cafe_color", "#CFB08E"));
        page.addView(color);

        stage = "criando o controle de duração";
        addLabel(page, "Duração do efeito");
        duration = new Spinner(this);
        ArrayAdapter<String> durationAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, DURATION_LABELS);
        durationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        duration.setAdapter(durationAdapter);
        duration.setSelection(indexOfDuration(
                clamp(prefs.getInt("gboard_cafe_duration", 260), 80, 1200)));
        page.addView(duration);

        Button save = button("Salvar efeitos");
        save.setOnClickListener(v -> saveSettings());
        page.addView(save);

        stage = "criando os controles de layout";
        addDivider(page);
        addLabel(page, "Editor de teclas");

        Button reorder = button("Reorganizar teclas");
        reorder.setOnClickListener(v -> chooseRow());
        page.addView(reorder);

        Button visibility = button("Mostrar ou ocultar teclas existentes");
        visibility.setOnClickListener(v -> editVisibility());
        page.addView(visibility);

        Button reset = button("Restaurar layout original");
        reset.setOnClickListener(v -> confirmReset());
        page.addView(reset);

        Button close = button("Fechar");
        close.setOnClickListener(v -> finish());
        page.addView(close);

        stage = "finalizando a tela";
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(page);
        setContentView(scroll);
    }

    private void saveSettings() {
        int parsed;
        try {
            parsed = Color.parseColor(color.getText().toString().trim());
        } catch (Throwable error) {
            color.setError("Use uma cor válida, por exemplo #CFB08E");
            return;
        }
        int selected = effect.getSelectedItemPosition();
        String effectValue = EFFECT_VALUES[Math.max(0, Math.min(selected, EFFECT_VALUES.length - 1))];
        int selectedBoard = boardEffect.getSelectedItemPosition();
        String boardEffectValue = BOARD_EFFECT_VALUES[Math.max(0, Math.min(selectedBoard, BOARD_EFFECT_VALUES.length - 1))];
        prefs.edit()
                .putBoolean("gboard_cafe_enabled", enabled.isChecked())
                .putString("gboard_cafe_effect", effectValue)
                .putString("gboard_cafe_board_effect", boardEffectValue)
                .putString("gboard_cafe_color", String.format("#%08X", parsed))
                .putInt("gboard_cafe_duration", DURATION_VALUES[Math.max(0, Math.min(duration.getSelectedItemPosition(), DURATION_VALUES.length - 1))])
                .apply();
        android.widget.Toast.makeText(this, "Configurações salvas", android.widget.Toast.LENGTH_SHORT).show();
    }

    private void chooseRow() {
        String[][] rows = GboardCafeRuntime.getRows(this);
        if (rows.length == 0) {
            message("Layout ainda não detectado",
                    "Abra o teclado em um campo de texto e volte aqui. O Gboard precisa montar o teclado primeiro.");
            return;
        }
        String[] names = new String[rows.length];
        for (int i = 0; i < rows.length; i++) {
            names[i] = "Linha " + (i + 1) + " (" + rows[i].length + " teclas)";
        }
        new AlertDialog.Builder(this).setTitle("Escolher linha")
                .setItems(names, (dialog, which) -> editRow(rows, which))
                .setNegativeButton("Cancelar", null).show();
    }

    private void editRow(String[][] rows, int rowIndex) {
        ArrayList<String> items = new ArrayList<>(Arrays.asList(rows[rowIndex]));
        final int[] selected = {0};
        ListView list = new ListView(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_single_choice, items);
        list.setAdapter(adapter);
        list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        if (!items.isEmpty()) list.setItemChecked(0, true);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(280)));
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        Button up = button("↑");
        Button down = button("↓");
        Button save = button("Salvar");
        actions.addView(up);
        actions.addView(down);
        actions.addView(save);
        panel.addView(actions);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Reordenar linha " + (rowIndex + 1))
                .setView(panel).setNegativeButton("Fechar", null).create();
        list.setOnItemClickListener((parent, view, position, id) -> selected[0] = position);
        up.setOnClickListener(v -> {
            int from = selected[0];
            if (from <= 0) return;
            String item = items.remove(from);
            items.add(from - 1, item);
            selected[0] = from - 1;
            adapter.notifyDataSetChanged();
            list.setItemChecked(selected[0], true);
        });
        down.setOnClickListener(v -> {
            int from = selected[0];
            if (from >= items.size() - 1) return;
            String item = items.remove(from);
            items.add(from + 1, item);
            selected[0] = from + 1;
            adapter.notifyDataSetChanged();
            list.setItemChecked(selected[0], true);
        });
        save.setOnClickListener(v -> {
            rows[rowIndex] = items.toArray(new String[0]);
            GboardCafeRuntime.setRows(this, rows);
            dialog.dismiss();
            android.widget.Toast.makeText(this, "Ordem salva", android.widget.Toast.LENGTH_SHORT).show();
        });
        dialog.show();
    }

    private void editVisibility() {
        String[][] rows = GboardCafeRuntime.getRows(this);
        if (rows.length == 0) {
            message("Layout ainda não detectado", "Abra o teclado em um campo de texto e volte aqui.");
            return;
        }
        ArrayList<String> keys = new ArrayList<>();
        for (String[] row : rows) for (String key : row) if (!keys.contains(key)) keys.add(key);
        Set<String> hidden = new HashSet<>(Arrays.asList(GboardCafeRuntime.getHiddenKeys(this)));
        boolean[] checked = new boolean[keys.size()];
        for (int i = 0; i < keys.size(); i++) checked[i] = !hidden.contains(keys.get(i));

        new AlertDialog.Builder(this).setTitle("Teclas visíveis")
                .setMultiChoiceItems(keys.toArray(new String[0]), checked,
                        (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Salvar", (dialog, which) -> {
                    ArrayList<String> hiddenKeys = new ArrayList<>();
                    for (int i = 0; i < keys.size(); i++) if (!checked[i]) hiddenKeys.add(keys.get(i));
                    GboardCafeRuntime.setHiddenKeys(this, hiddenKeys.toArray(new String[0]));
                }).setNegativeButton("Cancelar", null).show();
    }

    private void confirmReset() {
        new AlertDialog.Builder(this).setTitle("Restaurar layout")
                .setMessage("Voltar à disposição original das teclas?")
                .setPositiveButton("Restaurar", (dialog, which) -> GboardCafeRuntime.resetLayout(this))
                .setNegativeButton("Cancelar", null).show();
    }

    private void message(String title, String body) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(body)
                .setPositiveButton("OK", null).show();
    }

    private void addLabel(LinearLayout parent, String value) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextColor(Color.BLACK);
        label.setTextSize(15);
        label.setPadding(0, dp(14), 0, dp(4));
        parent.addView(label);
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        return button;
    }

    private void addDivider(LinearLayout parent) {
        View line = new View(this);
        line.setBackgroundColor(0xFFDDDDDD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        lp.topMargin = dp(16);
        lp.bottomMargin = dp(8);
        parent.addView(line, lp);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }


    private int indexOfDuration(int value) {
        int best = 0;
        int distance = Integer.MAX_VALUE;
        for (int i = 0; i < DURATION_VALUES.length; i++) {
            int currentDistance = Math.abs(DURATION_VALUES[i] - value);
            if (currentDistance < distance) {
                distance = currentDistance;
                best = i;
            }
        }
        return best;
    }

    private int indexOf(String[] values, String target) {
        for (int i = 0; i < values.length; i++) if (values[i].equals(target)) return i;
        return 0;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
