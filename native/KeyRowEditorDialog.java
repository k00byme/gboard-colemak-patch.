package app.gboard.colemak;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Drag-and-drop editor for the three letter rows already consumed by QwertzLayoutRuntime. */
final class KeyRowEditorDialog {
    private static final String[] DEFAULTS = { "qwertzuiop", "asdfghjkl", "yxcvbnm" };
    private static final String[] TITLES = { "Fileira superior", "Fileira do meio", "Fileira inferior" };

    private KeyRowEditorDialog() { }

    static void show(Context context, SharedPreferences preferences, Runnable onSaved) {
        final String[] rows = {
                validOrDefault(preferences.getString("qwertz_top_row", DEFAULTS[0]), 0),
                validOrDefault(preferences.getString("qwertz_middle_row", DEFAULTS[1]), 1),
                validOrDefault(preferences.getString("qwertz_bottom_row", DEFAULTS[2]), 2)
        };

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(context, 16);
        content.setPadding(padding, padding, padding, padding);

        TextView help = new TextView(context);
        help.setText("Segure uma letra e arraste para outra posição. Você também pode trocar letras entre fileiras.");
        help.setTextSize(13f);
        help.setPadding(0, 0, 0, dp(context, 12));
        content.addView(help);

        final Runnable[] render = new Runnable[1];
        render[0] = () -> {
            content.removeViews(1, Math.max(0, content.getChildCount() - 1));
            for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
                final int targetRow = rowIndex;
                TextView title = new TextView(context);
                title.setText(TITLES[targetRow]);
                title.setTextSize(12f);
                title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                title.setPadding(0, dp(context, 8), 0, dp(context, 5));
                content.addView(title);

                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER);
                for (int position = 0; position < rows[targetRow].length(); position++) {
                    final char targetLetter = rows[targetRow].charAt(position);
                    TextView tile = createTile(context, targetLetter);
                    tile.setTag(targetRow + ":" + targetLetter);
                    tile.setOnLongClickListener(view -> {
                        ClipData data = ClipData.newPlainText(
                                "gboard-letter", view.getTag().toString());
                        View.DragShadowBuilder shadow = new View.DragShadowBuilder(view);
                        if (Build.VERSION.SDK_INT >= 24) {
                            return view.startDragAndDrop(data, shadow, null, 0);
                        }
                        return view.startDrag(data, shadow, null, 0);
                    });
                    tile.setOnDragListener((target, event) -> {
                        switch (event.getAction()) {
                            case DragEvent.ACTION_DRAG_STARTED:
                                return true;
                            case DragEvent.ACTION_DRAG_ENTERED:
                                target.setAlpha(0.55f);
                                return true;
                            case DragEvent.ACTION_DRAG_EXITED:
                            case DragEvent.ACTION_DRAG_ENDED:
                                target.setAlpha(1f);
                                return true;
                            case DragEvent.ACTION_DROP:
                                target.setAlpha(1f);
                                try {
                                    String sourceTag = event.getClipData().getItemAt(0)
                                            .getText().toString();
                                    String[] sourceParts = sourceTag.split(":");
                                    String[] targetParts = target.getTag().toString().split(":");
                                    int sourceRow = Integer.parseInt(sourceParts[0]);
                                    char sourceLetter = sourceParts[1].charAt(0);
                                    int destinationRow = Integer.parseInt(targetParts[0]);
                                    char destinationLetter = targetParts[1].charAt(0);
                                    swap(rows, sourceRow, sourceLetter,
                                            destinationRow, destinationLetter);
                                    render[0].run();
                                } catch (Throwable ignored) { }
                                return true;
                            default:
                                return true;
                        }
                    });

                    LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(
                            0, dp(context, 42), 1f);
                    tileParams.leftMargin = dp(context, 1);
                    tileParams.rightMargin = dp(context, 1);
                    row.addView(tile, tileParams);
                }
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowParams.bottomMargin = dp(context, 4);
                content.addView(row, rowParams);
            }
        };
        render[0].run();

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Reorganizar teclas")
                .setView(content)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    if (!validLayout(rows[0], rows[1], rows[2])) {
                        Toast.makeText(context,
                                "O layout precisa conter cada letra de a a z uma única vez.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    boolean saved = preferences.edit()
                            .putString("qwertz_top_row", rows[0])
                            .putString("qwertz_middle_row", rows[1])
                            .putString("qwertz_bottom_row", rows[2])
                            .commit();
                    if (!saved) {
                        Toast.makeText(context, "Não foi possível salvar o layout.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    dialog.dismiss();
                    if (onSaved != null) onSaved.run();
                }));
        dialog.show();
    }

    private static void swap(String[] rows, int sourceRow, char source,
            int targetRow, char target) {
        if (sourceRow < 0 || sourceRow >= rows.length ||
                targetRow < 0 || targetRow >= rows.length) return;
        int sourceIndex = rows[sourceRow].indexOf(source);
        int targetIndex = rows[targetRow].indexOf(target);
        if (sourceIndex < 0 || targetIndex < 0) return;
        char[] sourceChars = rows[sourceRow].toCharArray();
        char[] targetChars = rows[targetRow].toCharArray();
        sourceChars[sourceIndex] = target;
        targetChars[targetIndex] = source;
        rows[sourceRow] = new String(sourceChars);
        rows[targetRow] = new String(targetChars);
    }

    private static TextView createTile(Context context, char letter) {
        TextView tile = new TextView(context);
        tile.setText(String.valueOf(Character.toUpperCase(letter)));
        tile.setTextSize(15f);
        tile.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tile.setGravity(Gravity.CENTER);
        tile.setContentDescription("Segure e arraste a letra " + letter);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(0x227F7F7F);
        shape.setCornerRadius(dp(context, 7));
        shape.setStroke(dp(context, 1), 0x667F7F7F);
        tile.setBackground(shape);
        return tile;
    }

    private static String validOrDefault(String value, int row) {
        String fallback = DEFAULTS[row];
        if (value == null || value.length() != fallback.length()) return fallback;
        boolean[] seen = new boolean[26];
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 'a' || c > 'z' || seen[c - 'a'] || fallback.indexOf(c) < 0) {
                return fallback;
            }
            seen[c - 'a'] = true;
        }
        return value;
    }

    private static boolean validLayout(String top, String middle, String bottom) {
        if (top.length() != 10 || middle.length() != 9 || bottom.length() != 7) return false;
        boolean[] seen = new boolean[26];
        String all = top + middle + bottom;
        for (int i = 0; i < all.length(); i++) {
            char c = all.charAt(i);
            if (c < 'a' || c > 'z' || seen[c - 'a']) return false;
            seen[c - 'a'] = true;
        }
        for (boolean value : seen) if (!value) return false;
        return true;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
