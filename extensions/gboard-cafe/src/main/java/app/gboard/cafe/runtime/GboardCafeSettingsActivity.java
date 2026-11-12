package app.gboard.cafe.runtime;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class GboardCafeSettingsActivity extends Activity {
    private static final int BG = 0xFF0D0F13;
    private static final int CARD = 0xFF171A21;
    private static final int FIELD = 0xFF222630;
    private static final int TEXT = 0xFFF3F4F6;
    private static final int MUTED = 0xFFA8AFBC;
    private static final int ACCENT = 0xFFCFB08E;

    private static final String[] EFFECT_VALUES = {
            "ripple", "flash", "outline", "bounce", "sparks", "paint",
            "blob", "blobSpread", "footprint", "none"
    };
    private static final String[] EFFECT_LABELS = {
            "Ripple", "Flash de cor", "Contorno", "Bounce", "Partículas",
            "Tinta", "Blob", "Blob expansivo", "Pegadas", "Desativado"
    };
    private static final String[] BOARD_EFFECT_VALUES = {
            "ring", "spark", "sparkSpin", "firecracker", "blob",
            "blobSpread", "outline", "paintGun", "none"
    };
    private static final String[] BOARD_EFFECT_LABELS = {
            "Anéis", "Faíscas", "Faíscas giratórias", "Fogos",
            "Blob", "Blob expansivo", "Contorno", "Paint gun", "Desativado"
    };
    private static final String[] PALETTE = {
            "#CFB08E", "#FFFFFF", "#FFD166", "#FF8A65",
            "#EF5350", "#EC407A", "#AB47BC", "#7E57C2",
            "#42A5F5", "#26A69A", "#66BB6A", "#90A4AE",
            "#80CBC4", "#B0BEC5"
    };

    private SharedPreferences prefs;
    private Switch enabled;
    private Spinner keyEffect;
    private Spinner boardEffect;
    private EditText customColor;
    private LinearLayout paletteContainer;
    private String selectedColor = "#CFB08E";
    private String stage = "inicialização";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            getWindow().setStatusBarColor(BG);
            getWindow().setNavigationBarColor(BG);
            getWindow().getDecorView().setSystemUiVisibility(0);
            stage = "abrindo configurações";
            prefs = getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE);
            selectedColor = prefs.getString("gboard_cafe_color", "#CFB08E");
            stage = "montando interface escura";
            buildScreen();
        } catch (Throwable error) {
            showStartupError(error);
        }
    }

    private void showStartupError(Throwable error) {
        try {
            TextView report = new TextView(this);
            report.setText("Gboard Cafe falhou ao abrir.\n\nEtapa: " + stage
                    + "\nErro: " + error.getClass().getName()
                    + "\nMensagem: " + error.getMessage()
                    + "\n\n" + android.util.Log.getStackTraceString(error));
            report.setTextColor(Color.WHITE);
            report.setTextSize(13);
            report.setTextIsSelectable(true);
            report.setPadding(dp(20), dp(24), dp(20), dp(24));
            report.setBackgroundColor(BG);
            ScrollView scroll = new ScrollView(this);
            scroll.addView(report);
            setContentView(scroll);
            android.util.Log.e("GboardCafe", "Settings startup failed", error);
        } catch (Throwable ignored) {
            TextView fallback = new TextView(this);
            fallback.setText("Gboard Cafe: " + error.getClass().getSimpleName());
            fallback.setTextColor(Color.WHITE);
            fallback.setBackgroundColor(BG);
            fallback.setPadding(dp(20), dp(24), dp(20), dp(24));
            setContentView(fallback);
        }
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(22), dp(16), dp(26));
        scroll.addView(page);

        TextView eyebrow = text("PERSONALIZAÇÃO DO TECLADO", 11, MUTED, true);
        eyebrow.setLetterSpacing(0.10f);
        page.addView(eyebrow);

        TextView heading = text("Gboard Cafe", 27, TEXT, true);
        heading.setPadding(0, dp(4), 0, 0);
        page.addView(heading);

        TextView intro = text("Efeitos rápidos e organização das teclas.", 14, MUTED, false);
        intro.setPadding(0, dp(5), 0, dp(18));
        page.addView(intro);

        LinearLayout effectsCard = card();
        page.addView(effectsCard, cardParams());

        addCardTitle(effectsCard, "Efeitos", "Escolha um efeito para as teclas e outro para a superfície inteira.");
        enabled = new Switch(this);
        enabled.setText("Ativar efeitos");
        enabled.setTextColor(TEXT);
        enabled.setTextSize(15);
        enabled.setChecked(prefs.getBoolean("gboard_cafe_enabled", false));
        enabled.setThumbTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{ACCENT, 0xFF858C98}));
        enabled.setTrackTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{0xFF725F4D, 0xFF454A54}));
        effectsCard.addView(enabled, matchWrap());

        addFieldLabel(effectsCard, "Efeito por tecla");
        keyEffect = spinner(EFFECT_LABELS);
        keyEffect.setSelection(indexOf(EFFECT_VALUES,
                prefs.getString("gboard_cafe_effect", "ripple")));
        effectsCard.addView(keyEffect, matchWrap());

        addFieldLabel(effectsCard, "Efeito no teclado inteiro");
        boardEffect = spinner(BOARD_EFFECT_LABELS);
        boardEffect.setSelection(indexOf(BOARD_EFFECT_VALUES,
                prefs.getString("gboard_cafe_board_effect", "none")));
        effectsCard.addView(boardEffect, matchWrap());

        addFieldLabel(effectsCard, "Cor do efeito");
        selectedColor = normalizeColor(selectedColor);
        paletteContainer = new LinearLayout(this);
        paletteContainer.setOrientation(LinearLayout.HORIZONTAL);
        HorizontalScrollView paletteScroll = new HorizontalScrollView(this);
        paletteScroll.setHorizontalScrollBarEnabled(false);
        paletteScroll.addView(paletteContainer);
        effectsCard.addView(paletteScroll, matchWrap());
        rebuildPalette();

        addFieldLabel(effectsCard, "Cor personalizada (opcional)");
        customColor = new EditText(this);
        customColor.setSingleLine(true);
        customColor.setTextColor(TEXT);
        customColor.setHintTextColor(0xFF737B89);
        customColor.setHint("#CFB08E");
        customColor.setText(selectedColor);
        customColor.setSelectAllOnFocus(true);
        customColor.setPadding(dp(12), dp(10), dp(12), dp(10));
        customColor.setBackground(rounded(FIELD, 10, 0xFF343A46, 1));
        effectsCard.addView(customColor, matchWrap());

        TextView timing = text("Animação temporária: cada efeito termina e é removido automaticamente.", 12, MUTED, false);
        timing.setPadding(0, dp(10), 0, 0);
        effectsCard.addView(timing);

        TextView saveEffects = actionButton("Salvar efeitos");
        saveEffects.setOnClickListener(v -> saveSettings());
        LinearLayout.LayoutParams saveParams = matchWrap();
        saveParams.topMargin = dp(14);
        effectsCard.addView(saveEffects, saveParams);

        LinearLayout layoutCard = card();
        page.addView(layoutCard, cardParams());
        addCardTitle(layoutCard, "Organização das teclas",
                "Segure uma tecla para arrastar. Solte em outra linha ou posição. Use + para criar uma tecla de texto.");
        TextView edit = actionButton("Abrir editor visual");
        edit.setOnClickListener(v -> openLayoutEditor());
        layoutCard.addView(edit, matchWrap());

        TextView reset = actionButton("Restaurar teclado original");
        reset.setOnClickListener(v -> confirmReset());
        LinearLayout.LayoutParams resetParams = matchWrap();
        resetParams.topMargin = dp(9);
        layoutCard.addView(reset, resetParams);

        TextView footer = text("Gboard Cafe • efeitos e layout", 11, 0xFF747B87, false);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(6), 0, dp(8));
        page.addView(footer);

        setContentView(scroll);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(rounded(CARD, 18, 0xFF292E38, 1));
        return card;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = matchWrap();
        params.bottomMargin = dp(14);
        return params;
    }

    private void addCardTitle(LinearLayout card, String title, String description) {
        TextView heading = text(title, 18, TEXT, true);
        card.addView(heading);
        TextView detail = text(description, 12, MUTED, false);
        detail.setPadding(0, dp(5), 0, dp(13));
        card.addView(detail);
    }

    private void addFieldLabel(LinearLayout parent, String value) {
        TextView label = text(value, 13, 0xFFD9DDE5, true);
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(13);
        params.bottomMargin = dp(6);
        parent.addView(label, params);
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Spinner spinner(String[] labels) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, 0, labels) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView item = text(getItem(position), 14, TEXT, false);
                item.setGravity(Gravity.CENTER_VERTICAL);
                item.setPadding(dp(12), dp(11), dp(12), dp(11));
                return item;
            }

            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView item = text(getItem(position), 14, TEXT, false);
                item.setGravity(Gravity.CENTER_VERTICAL);
                item.setPadding(dp(14), dp(12), dp(14), dp(12));
                item.setBackgroundColor(FIELD);
                return item;
            }
        };
        spinner.setAdapter(adapter);
        spinner.setPadding(dp(8), dp(3), dp(8), dp(3));
        spinner.setBackground(rounded(FIELD, 10, 0xFF343A46, 1));
        spinner.setPopupBackgroundDrawable(rounded(FIELD, 10, 0xFF343A46, 1));
        return spinner;
    }

    private void rebuildPalette() {
        if (paletteContainer == null) return;
        paletteContainer.removeAllViews();
        for (String hex : PALETTE) {
            TextView swatch = new TextView(this);
            swatch.setTag(hex);
            swatch.setContentDescription("Selecionar cor " + hex);
            swatch.setText("");
            swatch.setBackground(colorSwatch(hex, hex.equalsIgnoreCase(selectedColor)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(38), dp(38));
            params.rightMargin = dp(9);
            swatch.setOnClickListener(v -> {
                selectedColor = hex;
                customColor.setText(hex);
                rebuildPalette();
            });
            paletteContainer.addView(swatch, params);
        }
    }

    private GradientDrawable colorSwatch(String hex, boolean selected) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(Color.parseColor(hex));
        shape.setStroke(dp(selected ? 3 : 1), selected ? Color.WHITE : 0xFF555D6A);
        return shape;
    }

    private GradientDrawable rounded(int fill, int radiusDp, int stroke, int strokeDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) shape.setStroke(dp(strokeDp), stroke);
        return shape;
    }

    private TextView actionButton(String label) {
        TextView button = text(label, 14, TEXT, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(14), dp(13), dp(14), dp(13));
        button.setBackground(rounded(0xFF292F3A, 12, 0xFF424B5A, 1));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String normalizeColor(String value) {
        try {
            int parsed = Color.parseColor(value);
            return String.format("#%06X", parsed & 0xFFFFFF);
        } catch (Throwable ignored) {
            return "#CFB08E";
        }
    }

    private int indexOf(String[] values, String target) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(target)) return i;
        }
        return 0;
    }

    private void saveSettings() {
        int parsed;
        try {
            parsed = Color.parseColor(customColor.getText().toString().trim());
        } catch (Throwable error) {
            customColor.setError("Digite uma cor válida, por exemplo #CFB08E");
            return;
        }
        int keyIndex = Math.max(0, Math.min(keyEffect.getSelectedItemPosition(), EFFECT_VALUES.length - 1));
        int boardIndex = Math.max(0, Math.min(boardEffect.getSelectedItemPosition(), BOARD_EFFECT_VALUES.length - 1));
        prefs.edit()
                .putBoolean("gboard_cafe_enabled", enabled.isChecked())
                .putString("gboard_cafe_effect", EFFECT_VALUES[keyIndex])
                .putString("gboard_cafe_board_effect", BOARD_EFFECT_VALUES[boardIndex])
                .putString("gboard_cafe_color", String.format("#%06X", parsed & 0xFFFFFF))
                .putInt("gboard_cafe_duration", 520)
                .apply();
        selectedColor = String.format("#%06X", parsed & 0xFFFFFF);
        customColor.setText(selectedColor);
        rebuildPalette();
        GboardCafeRuntime.clearActiveEffects();
        Toast.makeText(this, "Efeitos salvos", Toast.LENGTH_SHORT).show();
    }

    private void openLayoutEditor() {
        String[][] saved = GboardCafeRuntime.getRows(this);
        if (saved.length == 0) {
            message("Layout ainda não detectado",
                    "Abra o Gboard em um campo de texto, digite uma vez e volte ao Cafe.");
            return;
        }
        ArrayList<ArrayList<String>> rows = new ArrayList<>();
        for (String[] row : saved) rows.add(new ArrayList<>(Arrays.asList(row)));
        final String[] selected = {null};

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(14), dp(14), dp(12));
        panel.setBackground(rounded(CARD, 18, 0xFF323844, 1));

        TextView title = text("Editor de teclas", 20, TEXT, true);
        panel.addView(title);
        TextView guide = text("Segure e arraste uma tecla para mudar sua posição ou linha. O botão + adiciona uma tecla de texto.", 12, MUTED, false);
        guide.setPadding(0, dp(5), 0, dp(12));
        panel.addView(guide);

        ScrollView rowScroll = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = matchWrap();
        scrollParams.height = dp(370);
        rowScroll.setLayoutParams(scrollParams);
        LinearLayout rowsHost = new LinearLayout(this);
        rowsHost.setOrientation(LinearLayout.VERTICAL);
        rowScroll.addView(rowsHost);
        panel.addView(rowScroll);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(12), 0, 0);
        TextView remove = actionButton("Excluir selecionada");
        TextView save = actionButton("Salvar");
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        actionParams.rightMargin = dp(6);
        actions.addView(remove, actionParams);
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        saveParams.leftMargin = dp(6);
        actions.addView(save, saveParams);
        panel.addView(actions);

        final AlertDialog dialog = new AlertDialog.Builder(this).setView(panel)
                .setNegativeButton("Fechar", null).create();

        Runnable refresh = () -> renderEditorRows(rowsHost, rows, selected, rows, null);
        // Keep the dialog's rows bound to the same model and render callbacks.
        renderEditorRows(rowsHost, rows, selected, rows, refresh);

        remove.setOnClickListener(v -> {
            if (selected[0] == null) {
                Toast.makeText(this, "Selecione uma tecla primeiro", Toast.LENGTH_SHORT).show();
                return;
            }
            boolean removed = removeToken(rows, selected[0]);
            selected[0] = null;
            if (removed) {
                renderEditorRows(rowsHost, rows, selected, rows, refresh);
            }
        });

        save.setOnClickListener(v -> {
            String[][] result = new String[rows.size()][];
            for (int i = 0; i < rows.size(); i++) {
                result[i] = rows.get(i).toArray(new String[0]);
            }
            GboardCafeRuntime.setRows(this, result);
            Toast.makeText(this, "Layout salvo", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(rounded(BG, 18, 0xFF424957, 1));
                dialog.getWindow().setLayout(
                        Math.min(getResources().getDisplayMetrics().widthPixels - dp(28), dp(560)),
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ACCENT);
            }
        });
        dialog.show();
    }

    /**
     * Builds each keyboard row as a horizontal drag target. The model keeps every
     * original row, including empty rows, so removing keys has no artificial cap.
     */
    private void renderEditorRows(
            LinearLayout host,
            ArrayList<ArrayList<String>> rows,
            String[] selected,
            ArrayList<ArrayList<String>> model,
            Runnable redraw) {
        host.removeAllViews();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            final int targetRow = rowIndex;
            LinearLayout rowCard = new LinearLayout(this);
            rowCard.setOrientation(LinearLayout.VERTICAL);
            rowCard.setPadding(dp(10), dp(10), dp(10), dp(10));
            rowCard.setBackground(rounded(0xFF20242D, 12, 0xFF343B48, 1));
            LinearLayout.LayoutParams cardParams = matchWrap();
            cardParams.bottomMargin = dp(9);
            host.addView(rowCard, cardParams);

            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = text("Linha " + (rowIndex + 1), 13, 0xFFDCE1E9, true);
            header.addView(name, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView add = actionButton("+ Adicionar");
            add.setTextSize(12);
            add.setPadding(dp(11), dp(7), dp(11), dp(7));
            add.setOnClickListener(v -> addCustomKey(targetRow, rows, selected, host, redraw));
            header.addView(add);
            rowCard.addView(header);

            HorizontalScrollView keyScroll = new HorizontalScrollView(this);
            keyScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout keyRow = new LinearLayout(this);
            keyRow.setOrientation(LinearLayout.HORIZONTAL);
            keyRow.setGravity(Gravity.CENTER_VERTICAL);
            keyRow.setMinimumHeight(dp(62));
            keyRow.setPadding(0, dp(7), 0, dp(2));
            keyScroll.addView(keyRow);

            ArrayList<String> values = rows.get(rowIndex);
            if (values.isEmpty()) {
                TextView empty = text("Linha vazia — arraste uma tecla para cá", 12, MUTED, false);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(dp(10), dp(8), dp(10), dp(8));
                keyRow.addView(empty);
            } else {
                for (int keyIndex = 0; keyIndex < values.size(); keyIndex++) {
                    final String token = values.get(keyIndex);
                    final int index = keyIndex;
                    TextView tile = text(GboardCafeRuntime.getDisplayLabel(token), 15, TEXT, true);
                    tile.setGravity(Gravity.CENTER);
                    tile.setMinWidth(dp(42));
                    tile.setMinHeight(dp(44));
                    tile.setPadding(dp(10), dp(8), dp(10), dp(8));
                    tile.setBackground(rounded(token.equals(selected[0]) ? 0xFF54483C : 0xFF303642,
                            9, token.equals(selected[0]) ? ACCENT : 0xFF525B6B, token.equals(selected[0]) ? 2 : 1));
                    LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, dp(44));
                    tileParams.rightMargin = dp(5);
                    tile.setOnClickListener(v -> {
                        selected[0] = token;
                        renderEditorRows(host, rows, selected, model, redraw);
                    });
                    tile.setOnLongClickListener(v -> {
                        ClipData data = ClipData.newPlainText("gboard-cafe-key", token);
                        return v.startDragAndDrop(data, new View.DragShadowBuilder(v),
                                token, View.DRAG_FLAG_GLOBAL);
                    });
                    tile.setOnDragListener((v, event) -> handleDrop(
                            event, rows, selected, targetRow, index, host, model, redraw));
                    keyRow.addView(tile, tileParams);
                }
            }

            keyRow.setOnDragListener((v, event) -> handleDrop(
                    event, rows, selected, targetRow, rows.get(targetRow).size(),
                    host, model, redraw));
            rowCard.addView(keyScroll);
            rowCard.setOnDragListener((v, event) -> handleDrop(
                    event, rows, selected, targetRow, rows.get(targetRow).size(),
                    host, model, redraw));
        }
    }

    private boolean handleDrop(
            DragEvent event,
            ArrayList<ArrayList<String>> rows,
            String[] selected,
            int targetRow,
            int targetIndex,
            LinearLayout host,
            ArrayList<ArrayList<String>> model,
            Runnable redraw) {
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return event.getClipDescription() != null;
            case DragEvent.ACTION_DRAG_ENTERED:
                return true;
            case DragEvent.ACTION_DRAG_LOCATION:
            case DragEvent.ACTION_DRAG_EXITED:
                return true;
            case DragEvent.ACTION_DROP:
                Object state = event.getLocalState();
                if (!(state instanceof String)) return false;
                String token = (String) state;
                if (moveToken(rows, token, targetRow, targetIndex)) {
                    selected[0] = token;
                    renderEditorRows(host, rows, selected, model, redraw);
                    return true;
                }
                return false;
            case DragEvent.ACTION_DRAG_ENDED:
                return true;
            default:
                return false;
        }
    }

    private boolean moveToken(
            ArrayList<ArrayList<String>> rows, String token, int targetRow, int targetIndex) {
        if (targetRow < 0 || targetRow >= rows.size()) return false;
        int sourceRow = -1;
        int sourceIndex = -1;
        for (int r = 0; r < rows.size(); r++) {
            int i = rows.get(r).indexOf(token);
            if (i >= 0) {
                sourceRow = r;
                sourceIndex = i;
                break;
            }
        }
        if (sourceRow < 0) return false;
        rows.get(sourceRow).remove(sourceIndex);
        int insertAt = Math.max(0, Math.min(targetIndex, rows.get(targetRow).size()));
        if (sourceRow == targetRow && sourceIndex < insertAt) insertAt--;
        insertAt = Math.max(0, Math.min(insertAt, rows.get(targetRow).size()));
        rows.get(targetRow).add(insertAt, token);
        return true;
    }

    private boolean removeToken(ArrayList<ArrayList<String>> rows, String token) {
        for (ArrayList<String> row : rows) {
            if (row.remove(token)) return true;
        }
        return false;
    }

    private void addCustomKey(
            int rowIndex, ArrayList<ArrayList<String>> rows, String[] selected,
            LinearLayout host, Runnable redraw) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setHint("Ex.: ç, ;, Tab");
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackground(rounded(FIELD, 10, 0xFF414957, 1));
        new AlertDialog.Builder(this)
                .setTitle("Adicionar tecla de texto")
                .setView(input)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Adicionar", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (value.isEmpty()) return;
                    String token = GboardCafeRuntime.createCustomKeyToken(value);
                    if (token == null) {
                        Toast.makeText(this, "Não foi possível criar essa tecla", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    rows.get(rowIndex).add(token);
                    selected[0] = token;
                    renderEditorRows(host, rows, selected, rows, redraw);
                }).show();
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle("Restaurar layout original?")
                .setMessage("Todas as mudanças de posição e as teclas personalizadas serão removidas.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Restaurar", (dialog, which) -> {
                    GboardCafeRuntime.resetLayout(this);
                    Toast.makeText(this, "Layout original restaurado", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void message(String title, String body) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(body)
                .setPositiveButton("OK", null).show();
    }
}
