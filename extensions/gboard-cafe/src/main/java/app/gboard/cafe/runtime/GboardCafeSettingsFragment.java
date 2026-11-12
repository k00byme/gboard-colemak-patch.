package app.gboard.cafe.runtime;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;

import java.util.ArrayList;
import java.util.Arrays;

public final class GboardCafeSettingsFragment extends PreferenceFragmentCompat {
    private static final String[] VALUES = {"ripple","flash","outline","bounce","sparks","paint","none"};
    private static final String[] LABELS = {"Ripple","Flash de cor","Contorno pulsante","Bounce","Partículas","Tinta","Desativado"};

    @Override public void onCreatePreferences(Bundle state, String rootKey) {
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(requireContext());
        setPreferenceScreen(screen);

        PreferenceCategory effects = new PreferenceCategory(requireContext());
        effects.setTitle("Efeitos das teclas"); screen.addPreference(effects);

        SwitchPreferenceCompat enabled = new SwitchPreferenceCompat(requireContext());
        enabled.setKey("gboard_cafe_enabled"); enabled.setTitle("Ativar efeitos");
        enabled.setDefaultValue(Boolean.TRUE); effects.addPreference(enabled);

        ListPreference effect = new ListPreference(requireContext());
        effect.setKey("gboard_cafe_effect"); effect.setTitle("Efeito ao pressionar");
        effect.setEntries(LABELS); effect.setEntryValues(VALUES); effect.setDefaultValue("ripple");
        effects.addPreference(effect);

        EditTextPreference color = new EditTextPreference(requireContext());
        color.setKey("gboard_cafe_color"); color.setTitle("Cor do efeito");
        color.setDialogTitle("Cor hexadecimal"); color.setDefaultValue("#CFB08E");
        color.setSummary("Formato #RRGGBB ou #AARRGGBB"); effects.addPreference(color);

        SeekBarPreference duration = new SeekBarPreference(requireContext());
        duration.setKey("gboard_cafe_duration"); duration.setTitle("Duração da animação");
        duration.setMin(80); duration.setMax(1200); duration.setDefaultValue(260);
        duration.setShowSeekBarValue(true); effects.addPreference(duration);

        PreferenceCategory layout = new PreferenceCategory(requireContext());
        layout.setTitle("Layout do teclado"); screen.addPreference(layout);

        Preference reorder = new Preference(requireContext());
        reorder.setTitle("Reorganizar teclas");
        reorder.setSummary("Mover teclas para cima ou para baixo dentro da própria linha");
        reorder.setOnPreferenceClickListener(p -> { chooseRow(); return true; });
        layout.addPreference(reorder);

        Preference visibility = new Preference(requireContext());
        visibility.setTitle("Adicionar ou remover teclas");
        visibility.setSummary("Mostrar novamente ou ocultar teclas existentes");
        visibility.setOnPreferenceClickListener(p -> { editVisibility(); return true; });
        layout.addPreference(visibility);

        Preference reset = new Preference(requireContext());
        reset.setTitle("Restaurar layout original");
        reset.setOnPreferenceClickListener(p -> {
            GboardCafeRuntime.resetLayout(requireContext()); return true;
        });
        layout.addPreference(reset);
    }

    private void chooseRow() {
        String[][] rows = GboardCafeRuntime.getRows(requireContext());
        if (rows.length == 0) {
            new AlertDialog.Builder(requireContext()).setTitle("Layout não detectado")
                    .setMessage("Abra o teclado uma vez, digite em um campo e volte às configurações.")
                    .setPositiveButton("OK", null).show(); return;
        }
        String[] names = new String[rows.length];
        for (int i=0;i<rows.length;i++) names[i]="Linha "+(i+1)+" ("+rows[i].length+" teclas)";
        new AlertDialog.Builder(requireContext()).setTitle("Escolher linha")
                .setItems(names,(d,which)->editRow(rows,which)).setNegativeButton("Cancelar",null).show();
    }

    private void editRow(String[][] rows, int rowIndex) {
        ArrayList<String> items = new ArrayList<>(Arrays.asList(rows[rowIndex]));
        int[] selected = {0};
        ListView list = new ListView(requireContext());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_single_choice, items);
        list.setAdapter(adapter); list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        list.setItemChecked(0,true);
        list.setOnItemClickListener((parent,view,position,id)->selected[0]=position);
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setTitle("Reordenar linha "+(rowIndex+1))
                .setView(list).setNeutralButton("↑",null).setNegativeButton("↓",null)
                .setPositiveButton("Salvar",null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                int from=selected[0]; if(from<=0)return;
                String value=items.remove(from); items.add(from-1,value); selected[0]=from-1;
                adapter.notifyDataSetChanged(); list.setItemChecked(selected[0],true);
            });
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                int from=selected[0]; if(from>=items.size()-1)return;
                String value=items.remove(from); items.add(from+1,value); selected[0]=from+1;
                adapter.notifyDataSetChanged(); list.setItemChecked(selected[0],true);
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                rows[rowIndex]=items.toArray(new String[0]);
                GboardCafeRuntime.setRows(requireContext(),rows); dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void editVisibility() {
        String[][] rows=GboardCafeRuntime.getRows(requireContext());
        if(rows.length==0){
            new AlertDialog.Builder(requireContext()).setTitle("Layout não detectado")
                    .setMessage("Abra o teclado uma vez e volte às configurações.")
                    .setPositiveButton("OK",null).show(); return;
        }
        ArrayList<String> keys=new ArrayList<>();
        for(String[] row:rows)for(String key:row)if(!keys.contains(key))keys.add(key);
        String[] hidden=GboardCafeRuntime.getHiddenKeys(requireContext());
        boolean[] checked=new boolean[keys.size()];
        for(int i=0;i<keys.size();i++)checked[i]=!Arrays.asList(hidden).contains(keys.get(i));
        new AlertDialog.Builder(requireContext()).setTitle("Teclas visíveis")
                .setMultiChoiceItems(keys.toArray(new String[0]),checked,(d,w,on)->checked[w]=on)
                .setPositiveButton("Salvar",(d,w)->{
                    ArrayList<String> result=new ArrayList<>();
                    for(int i=0;i<keys.size();i++)if(!checked[i])result.add(keys.get(i));
                    GboardCafeRuntime.setHiddenKeys(requireContext(),result.toArray(new String[0]));
                }).setNegativeButton("Cancelar",null).show();
    }
}
