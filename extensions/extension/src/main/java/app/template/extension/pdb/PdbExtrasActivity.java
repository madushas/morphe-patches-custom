package app.template.extension.pdb;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/**
 * The "Pdb Extras" power-user screen, opened from the entry row at the top of
 * Pdb's Settings screen (wired by {@link SettingsEntryHook}).
 *
 * <p>Built from plain framework widgets: the app's {@code alertDialogTheme} has no
 * list panel (device-confirmed), and its Tweaks host fragment implements no
 * preference navigation, so this screen does not depend on either.
 *
 * <p>Layout, top to bottom:
 * <ul>
 *   <li><b>Status</b> — what is forced at patch time (premium, ads) and pause state.</li>
 *   <li><b>Toggles</b> — the switch rows a user may want to change later (privacy,
 *       session, dev). Written to the app's own preference file; applied on the next
 *       launch by {@link PdbStartup}.</li>
 *   <li><b>Text overrides</b> — the app's hidden value preferences (country, hosts,
 *       mock GPS, instant-chat partner). Empty saves = off.</li>
 *   <li><b>Tools</b> — export/import, diagnostics, quick preference writer, local
 *       chat/profile data, MMKV identity + realtime-socket controls.</li>
 *   <li><b>Native dev screen</b> — opens Pdb's own Tweaks screen, which the app hides
 *       behind a 4-tap-on-the-version-row gesture.</li>
 * </ul>
 */
public final class PdbExtrasActivity extends Activity {

    private LinearLayout content;
    private TextView result;
    private EditText prefKey;
    private EditText prefValue;
    private EditText chatQuery;
    private EditText deviceId;
    private EditText imUrl;

    /** label → morphe key for the switch rows. */
    private static final String[][] SWITCH_ROWS = {
        {"Disable IRL read receipts", PdbSettings.PRIVACY_RECEIPTS},
        {"Ignore IRL \u201csee more\u201d", PdbSettings.PRIVACY_SEE_MORE},
        {"Stay signed in", PdbSettings.REMEMBER_AUTH},
        {"Force offer sheet (dev)", PdbSettings.FORCE_OFFER},
        {"Use staging backend (dev)", PdbSettings.API_STAGING},
        {"Show full error messages", PdbSettings.SHOW_FULL_ERROR},
        {"Stream connect toast", PdbSettings.IM_CONNECT_TOAST},
        {"Force English", PdbSettings.FORCE_ENGLISH},
        {"Verbose file logging", PdbSettings.ADVANCED_LOGGING},
        {"Pause all Morphe extras", PdbSettings.GLOBAL_PAUSE},
    };

    /** hint → morphe key for the text-override rows. */
    private static final String[][] TEXT_ROWS = {
        {"Feed country override, e.g. US (mockFeedCountryCode)", PdbSettings.GEO_COUNTRY},
        {"Web allow-list host (whiteListHost)", PdbSettings.WHITELIST_HOST},
        {"Bubble entry URL (mockBubbleEntranceURL)", PdbSettings.BUBBLE_URL},
        {"Mock GPS location lat,lng (mockLatLng)", PdbSettings.MOCK_LATLNG},
        {"Force instant-chat user id (debugInstantChatUserId)", PdbSettings.INSTANT_CHAT_USER},
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

        /* Toggles */
        section("Toggles (applied on next launch)");
        for (final String[] row : SWITCH_ROWS) {
            content.addView(switchRow(row[0], row[1]), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        /* Text overrides */
        section("Hidden text overrides (empty = off)");
        for (final String[] row : TEXT_ROWS) {
            EditText edit = field(row[0]);
            edit.setText(PdbSettings.getString(PdbTools.prefs(this), row[1], ""));
            edit.setTag(row[1]);
            textFields.add(edit);
        }
        button("Save text overrides", new Runnable() {
            @Override
            public void run() {
                java.util.Set<String> written = new java.util.HashSet<>();
                android.content.SharedPreferences.Editor e = PdbTools.prefs(PdbExtrasActivity.this).edit();
                for (EditText edit : textFields) {
                    String key = (String) edit.getTag();
                    String value = edit.getText().toString().trim();
                    if (value.isEmpty()) e.remove(key);
                    else e.putString(key, value);
                    written.add(key);
                }
                e.apply();
                show("Text overrides", "Saved " + written.size()
                    + " override(s). Relaunch Pdb to apply.", false);
            }
        });

        /* Tools */
        section("Tools");
        toolsButtons();

        /* Identity / transport */
        section("Identity and realtime socket (MMKV)");
        identityControls();

        /* Result */
        section("Result");
        result = new TextView(this);
        result.setTextSize(12);
        result.setTextIsSelectable(true);
        result.setPadding(0, pad / 4, 0, pad / 4);
        ScrollView resultScroll = new ScrollView(this);
        resultScroll.addView(result);
        content.addView(resultScroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            Math.round(220 * getResources().getDisplayMetrics().density)));

        /* Native dev screen */
        section("Pdb's own developer screen");
        button("Open native dev screen (Tweaks)", new Runnable() {
            @Override
            public void run() {
                try {
                    Intent intent = new Intent();
                    intent.setClassName(getPackageName(), "pdb.app.tweak.TweaksActivity");
                    startActivity(intent);
                } catch (Throwable t) {
                    show("Tweaks", "Could not open Pdb's Tweaks screen: " + t, false);
                }
            }
        });

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

    private final java.util.List<EditText> textFields = new java.util.ArrayList<>();

    private void toolsButtons() {
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
        button("Dump every preference key", new Runnable() {
            @Override
            public void run() {
                show("Preferences", PdbTools.prefsDump(PdbExtrasActivity.this), true);
            }
        });

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
    }

    private void identityControls() {
        deviceId = field("XDeviceId value");
        String id = PdbTools.deviceId();
        if (!id.startsWith("(")) deviceId.setText(id);
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
        String url = PdbTools.imSocket();
        if (!url.startsWith("(")) imUrl.setText(url);
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
    }

    /* ── widget helpers ── */

    private View switchRow(String label, final String key) {
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);

        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setTextSize(15);
        sw.setAllCaps(false);
        sw.setChecked(PdbSettings.getBool(PdbTools.prefs(this), key, false));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                PdbTools.prefs(PdbExtrasActivity.this).edit().putBoolean(key, isChecked).apply();
                PdbTools.toast(PdbExtrasActivity.this,
                    "Morphe Extras: " + (isChecked ? "on" : "off") + " \u2014 relaunch Pdb to apply");
            }
        });
        sw.setPadding(0, pad / 4, 0, pad / 4);
        return sw;
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
