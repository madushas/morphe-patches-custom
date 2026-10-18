package app.template.extension.pdb;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Tools host for the "Extras tools" row injected into Pdb's Tweaks screen.
 *
 * <p>The row is a plain {@code Preference} carrying an {@code <intent>}, because that
 * is the only navigation Pdb's Tweaks host fragment supports: it implements neither
 * {@code OnPreferenceStartScreenCallback} (nested screens) nor
 * {@code OnPreferenceStartFragmentCallback} ({@code android:fragment} rows), and both
 * were confirmed inert on device. An intent-carrying row is handled by
 * {@code Preference.performClick()} in the platform, so no host support is needed.
 *
 * <p>The screen is built from plain framework widgets rather than an
 * {@code AlertDialog}: the app's {@code alertDialogTheme} layout has no list panel, so
 * {@code AlertDialog.Builder#setItems} renders nothing there (device-confirmed).
 */
public final class PdbExtrasActivity extends Activity {

    private static final String[] ACTIONS = {"export", "import", "diagnostics", "preset", "reset"};

    private static final String[] LABELS = {
        "Export settings to clipboard (JSON)",
        "Import settings from clipboard (JSON)",
        "Diagnostics (copy report to clipboard)",
        "Preset: maximum privacy",
        "Reset all Morphe values",
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // The app's theme draws edge to edge, which would put our title under the
        // status bar. Let the decor inset the content instead.
        try {
            getWindow().setDecorFitsSystemWindows(true);
        } catch (Throwable ignored) {
        }
        try {
            setContentView(buildScreen());
        } catch (Throwable t) {
            PdbTools.toast(this, "Morphe Extras: " + t);
            finish();
        }
    }

    private View buildScreen() {
        final int pad = Math.round(16 * getResources().getDisplayMetrics().density);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Morphe \u2014 Pdb Extras");
        title.setTextSize(20);
        title.setPadding(0, 0, 0, pad / 2);
        content.addView(title);

        TextView status = new TextView(this);
        status.setText(PdbTools.summary(this));
        status.setTextSize(13);
        status.setPadding(0, 0, 0, pad);
        content.addView(status);

        for (int i = 0; i < LABELS.length; i++) {
            final String action = ACTIONS[i];
            Button button = new Button(this);
            button.setText(LABELS[i]);
            button.setAllCaps(false);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    run(action);
                }
            });
            content.addView(button, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        Button close = new Button(this);
        close.setText("Close");
        close.setAllCaps(false);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        content.addView(close, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        return scroll;
    }

    /** Every action needs a relaunch to reach the app, which reads prefs once at start. */
    private void run(String action) {
        if ("export".equals(action)) {
            PdbTools.copyToClipboard(this, PdbTools.exportJson(this));
            PdbTools.toast(this, "Morphe Extras: settings copied as JSON");

        } else if ("import".equals(action)) {
            String json = PdbTools.readClipboard(this);
            int restored = json == null ? 0 : PdbTools.importJson(this, json);
            PdbTools.toast(this, restored == 0
                ? "Morphe Extras: no importable Morphe JSON on the clipboard"
                : "Morphe Extras: " + restored + " values imported \u2014 relaunch Pdb");

        } else if ("diagnostics".equals(action)) {
            PdbTools.copyToClipboard(this, PdbTools.diagnostics(this));
            PdbTools.toast(this, "Morphe Extras: diagnostics copied ("
                + PdbSettings.ALL_KEYS.length + " Morphe / "
                + PdbSettings.PDB_KEY_ORDER.length + " Pdb keys)");

        } else if ("preset".equals(action)) {
            PdbTools.applyPrivacyPreset(this);
            PdbTools.toast(this, "Morphe Extras: maximum-privacy preset applied \u2014 relaunch Pdb");

        } else if ("reset".equals(action)) {
            PdbTools.reset(this);
            PdbTools.toast(this, "Morphe Extras: reset to defaults \u2014 relaunch Pdb");
        }
    }
}
