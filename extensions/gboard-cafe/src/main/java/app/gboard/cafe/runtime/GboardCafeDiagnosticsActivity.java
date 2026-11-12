package app.gboard.cafe.runtime;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

public final class GboardCafeDiagnosticsActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        TextView report = new TextView(this);
        report.setText(GboardCafeRuntime.getDiagnostics(this));
        report.setTextColor(Color.BLACK);
        report.setTextSize(14f);
        report.setTextIsSelectable(true);
        int pad = Math.round(18f * getResources().getDisplayMetrics().density);
        report.setPadding(pad, pad, pad, pad);
        scroll.addView(report, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
    }
}
