package app.template.extension.pdb;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Power-user tools screen for the "Extras tools" row injected into Pdb's Tweaks screen.
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
 *
 * <p>The switchable rows themselves live in Pdb's own Tweaks list (injected by the
 * patch). Everything here is a one-shot action: settings export/import, an arbitrary
 * preference writer, local chat/profile data read out of Pdb's SQLite files, and the
 * MMKV-backed identity / realtime-socket controls.
 */
public final class PdbExtrasActivity extends Activity {

    private LinearLayout content;
    private TextView result;
    private EditText prefKey;
    private EditText prefValue;
    private EditText chatQuery;
    private EditText deviceId;
    private EditText imUrl;

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
            prefill();
        } catch (Throwable t) {
            PdbTools.toast(this, "Morphe Extras: " + t);
            finish();
        }
    }

    /* ── screen construction ── */

    private View buildScreen() {
        final int pad = Math.round(16 * getResources().getDisplayMetrics().density);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Morphe \u2014 Pdb Extras");
        title.setTextSize(20);
        content.addView(title);

        TextView status = new TextView(this);
        status.setText(PdbTools.summary(this));
        status.setTextSize(13);
        status.setPadding(0, pad / 4, 0, pad / 2);
        content.addView(status);

        /* Settings */
        section("Settings");
        button("Export settings to clipboard (JSON)", new Runnable() {
            @Override
            public void run() {
                show("Settings JSON", PdbTools.exportJson(PdbExtrasActivity.this), true);
            }
        });
        button("Import settings from clipboard (JSON)", new Runnable() {
            @Override
            public void run() {
                String json = PdbTools.readClipboard(PdbExtrasActivity.this);
                int restored = json == null ? 0 : PdbTools.importJson(PdbExtrasActivity.this, json);
                show("Import", restored == 0
                    ? "No importable Morphe JSON on the clipboard."
                    : restored + " value(s) imported. Relaunch Pdb to apply.", false);
            }
        });
        button("Diagnostics (Morphe + Pdb + MMKV keys)", new Runnable() {
            @Override
            public void run() {
                show("Diagnostics", PdbTools.diagnostics(PdbExtrasActivity.this), true);
            }
        });
        button("Preset: maximum privacy", new Runnable() {
            @Override
            public void run() {
                PdbTools.applyPrivacyPreset(PdbExtrasActivity.this);
                show("Preset", "Maximum-privacy preset applied. Relaunch Pdb to apply.", false);
            }
        });
        button("Reset all Morphe values", new Runnable() {
            @Override
            public void run() {
                PdbTools.reset(PdbExtrasActivity.this);
                show("Reset", "Morphe values cleared. Relaunch Pdb to apply.", false);
            }
        });

        /* Preferences */
        section("Any preference (what the app's hidden Quick Tweak does)");
        prefKey = field("Preference key, for example mockFeedCountryCode");
        prefValue = field("Value: true / false / number / text");
        button("Set preference", new Runnable() {
            @Override
            public void run() {
                show("Set pref", PdbTools.setPref(
                    PdbExtrasActivity.this,
                    prefKey.getText().toString(),
                    prefValue.getText().toString()), false);
            }
        });
        button("Delete preference key", new Runnable() {
            @Override
            public void run() {
                show("Delete pref", PdbTools.deletePref(
                    PdbExtrasActivity.this, prefKey.getText().toString()), false);
            }
        });
        button("Dump every preference key", new Runnable() {
            @Override
            public void run() {
                show("Preferences", PdbTools.prefsDump(PdbExtrasActivity.this), true);
            }
        });

        /* Local data */
        section("Local data (read-only)");
        button("Database stats (files, tables, row counts)", new Runnable() {
            @Override
            public void run() {
                runAsync("Databases", new Work() {
                    @Override
                    public String run() {
                        return PdbTools.dbStats(PdbExtrasActivity.this);
                    }
                });
            }
        });
        chatQuery = field("Chat search text (leave empty for the latest messages)");
        button("Recent chats", new Runnable() {
            @Override
            public void run() {
                runAsync("Recent chats", new Work() {
                    @Override
                    public String run() {
                        return PdbTools.chatExport(PdbExtrasActivity.this, null, 50);
                    }
                });
            }
        });
        button("Search chats", new Runnable() {
            @Override
            public void run() {
                final String query = chatQuery.getText().toString();
                runAsync("Chat search", new Work() {
                    @Override
                    public String run() {
                        return PdbTools.chatExport(PdbExtrasActivity.this, query, 50);
                    }
                });
            }
        });
        button("Profile view log (local visitor records)", new Runnable() {
            @Override
            public void run() {
                runAsync("Profile views", new Work() {
                    @Override
                    public String run() {
                        return PdbTools.visitorLog(PdbExtrasActivity.this, 50);
                    }
                });
            }
        });

        /* Identity / transport */
        section("Identity and realtime socket (MMKV)");
        deviceId = field("XDeviceId value");
        button("Save device id", new Runnable() {
            @Override
            public void run() {
                show("XDeviceId", PdbTools.setDeviceId(deviceId.getText().toString()), false);
            }
        });
        button("Randomize device id", new Runnable() {
            @Override
            public void run() {
                String outcome = PdbTools.randomizeDeviceId();
                show("XDeviceId", outcome, false);
                deviceId.setText(PdbTools.deviceId());
            }
        });
        imUrl = field("wss://your-relay/connection/websocket");
        button("Save IM socket URL", new Runnable() {
            @Override
            public void run() {
                show("IM socket", PdbTools.setImSocket(imUrl.getText().toString()), false);
            }
        });
        button("Clear IM socket override", new Runnable() {
            @Override
            public void run() {
                String outcome = PdbTools.setImSocket("");
                show("IM socket", outcome, false);
                imUrl.setText("");
            }
        });
        button("Fake-logout flag: SET (keep remembered auth)", new Runnable() {
            @Override
            public void run() {
                show("Fake logout", PdbTools.setFakeLogout(true)
                    + "\nCurrent: " + PdbTools.fakeLogoutState(), false);
            }
        });
        button("Fake-logout flag: CLEAR", new Runnable() {
            @Override
            public void run() {
                show("Fake logout", PdbTools.setFakeLogout(false)
                    + "\nCurrent: " + PdbTools.fakeLogoutState(), false);
            }
        });
        button("Dump MMKV store", new Runnable() {
            @Override
            public void run() {
                show("MMKV", PdbTools.mmkvDump(), true);
            }
        });

        /* Result */
        section("Result");
        result = new TextView(this);
        result.setTextSize(12);
        result.setTextIsSelectable(true);
        result.setPadding(0, pad / 4, 0, pad / 4);
        ScrollView resultScroll = new ScrollView(this);
        resultScroll.addView(result);
        content.addView(resultScroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Math.round(220 * getResources().getDisplayMetrics().density)));

        button("Close", new Runnable() {
            @Override
            public void run() {
                finish();
            }
        });

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        return scroll;
    }

    private void section(String label) {
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        TextView header = new TextView(this);
        header.setText(label);
        header.setTextSize(15);
        header.setPadding(0, pad, 0, pad / 4);
        content.addView(header);
    }

    private void button(String label, final Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTransformationMethod(null); // keep long labels readable
        button.setSingleLine(false);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    action.run();
                } catch (Throwable t) {
                    show("Error", String.valueOf(t), false);
                }
            }
        });
        content.addView(button, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private EditText field(String hint) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_TEXT);
        edit.setTextSize(13);
        content.addView(edit, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return edit;
    }

    private void prefill() {
        String id = PdbTools.deviceId();
        if (id != null && !id.startsWith("(")) deviceId.setText(id);
        String url = PdbTools.imSocket();
        if (url != null && !url.startsWith("(")) imUrl.setText(url);
    }

    /* ── actions ── */

    private interface Work {
        String run();
    }

    /** Runs a possibly slow read (SQLite) off the main thread. */
    private void runAsync(final String label, final Work work) {
        show(label, "working\u2026", false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String text;
                try {
                    text = String.valueOf(work.run());
                } catch (Throwable t) {
                    showResult(label, "failed: " + t, false);
                    return;
                }
                showResult(label, text, false);
            }
        }, "pdb-extras").start();
    }

    private void showResult(final String label, final String text, final boolean copy) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                applyResult(label, text, copy);
            }
        });
    }

    private void show(String label, String text, boolean copy) {
        applyResult(label, text, copy);
    }

    private void applyResult(String label, String text, boolean copy) {
        try {
            if (copy) PdbTools.copyToClipboard(this, text);
            if (result != null) result.setText(label + "\n\n" + text);
            String firstLine = text == null ? "" : text.split("\n", 2)[0];
            PdbTools.toast(this, "Morphe Extras: " + (firstLine.length() > 60
                ? firstLine.substring(0, 60) + "\u2026" : firstLine));
        } catch (Throwable ignored) {
        }
    }
}
