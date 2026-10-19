package app.template.extension.pdb;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The "Pdb Extras" power-user screen, opened from the "Pdb Extras" section at the
 * top of Pdb's Settings screen (wired by {@link SettingsEntryHook}).
 *
 * <p>The UI is assembled from Pdb's own widgets through {@link PdbUi} (native
 * {@code ActionCell} rows, {@code PBDTextView} labels, {@code content_bg_r8} cards),
 * so it matches the app's typography, colors and dark mode exactly.
 *
 * <p>Sections: power tools (user inspector, DirectChatByID console, hidden screens,
 * icon switcher, deep-link tester), local data reads, hidden text overrides,
 * runtime toggles, identity/transport (MMKV), diagnostics and backup.
 */
public final class PdbExtrasActivity extends Activity {

    private LinearLayout content;
    private TextView result;
    private final List<EditText> textFields = new ArrayList<>();
    private EditText inspectorInput;
    private EditText chatQuery;
    private EditText deeplinkInput;
    private EditText deviceId;
    private EditText imUrl;

    /** label → morphe key for the switch rows. */
    private static final String[][] SWITCH_ROWS = {
        {"Disable IRL read receipts", PdbSettings.PRIVACY_RECEIPTS},
        {"Ignore IRL \u201csee more\u201d", PdbSettings.PRIVACY_SEE_MORE},
        {"Stay signed in", PdbSettings.REMEMBER_AUTH},
        {"Show user IDs on list rows", PdbSettings.ROW_ID_CHIPS},
        {"Show user IDs next to @names", PdbSettings.IDS_EVERYWHERE},
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

    /** Hidden-screens launcher: label → kept activity class (from Pdb's router map). */
    private static final String[][] HIDDEN_SCREENS = {
        {"Profile detail (by id)", PdbNav.PROFILE_DETAIL},
        {"My profile page", PdbNav.USER_PROFILE},
        {"User database", PdbNav.USER_DATABASE},
        {"Profile visitors", PdbNav.USER_VISITOR},
        {"User center", PdbNav.USER_CENTER},
        {"Follows", PdbNav.FOLLOWS},
        {"Personality", PdbNav.PERSONALITY},
        {"Subconscious", PdbNav.SUBCONSCIOUS},
        {"Coins engage", PdbNav.COINS_ENGAGE},
        {"Balance", PdbNav.BALANCE},
        {"Award center", PdbNav.AWARD_CENTER},
        {"Community info", PdbNav.COMMUNITY_INFO},
        {"Verify", PdbNav.VERIFY},
        {"Story editor", PdbNav.STORY_EDITOR},
        {"Profile settings", PdbNav.PROFILE_SETTING},
        {"PDB page manager", PdbNav.DB_PAGE},
        {"Instant chat history", PdbNav.INSTANT_HISTORY},
        {"Community explore", PdbNav.COMMUNITY_EXPLORE},
        {"Boards explore", PdbNav.BOARD_EXPLORE},
        {"AI friend detail", PdbNav.AI_DETAIL},
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = PdbUi.dp(this, 16);
        content.setPadding(pad, pad, pad, pad);

        TextView title = PdbUi.text(this, "Pdb Extras", 22, "gray_01", 0xFF1C1C1E);
        title.setPadding(0, 0, 0, PdbUi.dp(this, 4));
        content.addView(title);

        TextView status = PdbUi.text(this, PdbTools.summary(this), 12, "gray_03", 0xFF8E8E93);
        status.setPadding(0, 0, 0, PdbUi.dp(this, 8));
        content.addView(status);

        powerTools();
        localData();
        textOverrides();
        toggles();
        identity();
        diagnostics();
        resultArea();
        footer();

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        return scroll;
    }

    /* ── sections ── */

    private void powerTools() {
        content.addView(PdbUi.sectionLabel(this, "Power tools"));
        LinearLayout card = PdbUi.card(this);
        content.addView(card, PdbUi.cardParams(this));

        card.addView(row("User inspector \u2014 look up any id / @name", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String input = inspectorInput == null ? "" : inspectorInput.getText().toString();
                runAsync("User inspector", new Work() {
                    @Override
                    public String run() {
                        return PdbDatabase.userById(PdbExtrasActivity.this, input);
                    }
                });
            }
        }));
        card.addView(row("Open profile of entered id", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String id = PdbDatabase.resolveUserId(PdbExtrasActivity.this, input());
                if (id == null) {
                    PdbTools.toast(PdbExtrasActivity.this, "Morphe: enter a user id or @name first");
                    return;
                }
                PdbNav.openProfile(PdbExtrasActivity.this, id);
            }
        }));
        card.addView(row("Direct chat with entered id", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                directChat();
            }
        }));
        card.addView(row("Clear instant-chat override", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PdbTools.prefs(PdbExtrasActivity.this).edit()
                    .remove(PdbSettings.PDB_IM_CHAT_USER)
                    .remove(PdbSettings.INSTANT_CHAT_USER).apply();
                PdbTools.toast(PdbExtrasActivity.this, "Morphe: instant-chat override cleared");
            }
        }));
        card.addView(row("Open instant chat", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PdbNav.openInstantChat(PdbExtrasActivity.this);
            }
        }));
        card.addView(row("Hidden screens launcher (" + HIDDEN_SCREENS.length + ")", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<PdbMenu.Item> items = new ArrayList<>();
                for (final String[] screen : HIDDEN_SCREENS) {
                    items.add(new PdbMenu.Item() {
                        @Override
                        public String label() {
                            return screen[0];
                        }

                        @Override
                        public void run() {
                            PdbNav.start(PdbExtrasActivity.this, screen[1]);
                        }
                    });
                }
                PdbMenu.show(v, items);
            }
        }));
        card.addView(row("Open entered deep link", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String link = deeplinkInput == null ? "" : deeplinkInput.getText().toString();
                if (link.trim().isEmpty()) {
                    PdbTools.toast(PdbExtrasActivity.this, "Morphe: type a pdb:// link first");
                    return;
                }
                PdbNav.openLink(PdbExtrasActivity.this, link);
            }
        }));
        card.addView(row("Switch app icon (MBTI / themes)", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<PdbMenu.Item> items = new ArrayList<>();
                items.add(simpleMenu("Stock icon", new Runnable() {
                    @Override
                    public void run() {
                        PdbIcons.reset(PdbExtrasActivity.this);
                    }
                }));
                for (final String alias : PdbIcons.aliases(PdbExtrasActivity.this)) {
                    items.add(simpleMenu(PdbIcons.pretty(alias), new Runnable() {
                        @Override
                        public void run() {
                            PdbIcons.switchTo(PdbExtrasActivity.this, alias);
                        }
                    }));
                }
                PdbMenu.show(v, items);
            }
        }));

        inspectorInput = PdbUi.field(this, "user id or @username");
        card.addView(inspectorInput);
        card.addView(PdbUi.spacer(this, 4));
        deeplinkInput = PdbUi.field(this, "pdb://app/router?link=\u2026 or any deep link");
        card.addView(deeplinkInput);
    }

    private void localData() {
        content.addView(PdbUi.sectionLabel(this, "Local data (read-only)"));
        LinearLayout card = PdbUi.card(this);
        content.addView(card, PdbUi.cardParams(this));

        card.addView(row("Database stats (files, tables, row counts)", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runAsync("Databases", new Work() {
                    @Override
                    public String run() {
                        return PdbTools.dbStats(PdbExtrasActivity.this);
                    }
                });
            }
        }));
        card.addView(row("Chat statistics", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runAsync("Chat stats", new Work() {
                    @Override
                    public String run() {
                        return PdbDatabase.chatStats(PdbExtrasActivity.this);
                    }
                });
            }
        }));
        card.addView(row("Recent chats", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runAsync("Recent chats", new Work() {
                    @Override
                    public String run() {
                        return PdbTools.chatExport(PdbExtrasActivity.this, null, 50);
                    }
                });
            }
        }));
        card.addView(row("Search chats", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String query = chatQuery == null ? "" : chatQuery.getText().toString();
                runAsync("Chat search", new Work() {
                    @Override
                    public String run() {
                        return PdbTools.chatExport(PdbExtrasActivity.this, query, 50);
                    }
                });
            }
        }));
        card.addView(row("Visitor digest (grouped)", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runAsync("Visitor digest", new Work() {
                    @Override
                    public String run() {
                        return PdbDatabase.visitorDigest(PdbExtrasActivity.this, 50);
                    }
                });
            }
        }));
        card.addView(row("Profile view log (raw)", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runAsync("Profile views", new Work() {
                    @Override
                    public String run() {
                        return PdbTools.visitorLog(PdbExtrasActivity.this, 50);
                    }
                });
            }
        }));
        card.addView(row("Streak radar", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runAsync("Streak radar", new Work() {
                    @Override
                    public String run() {
                        return PdbDatabase.streaks(PdbExtrasActivity.this, 30);
                    }
                });
            }
        }));
        card.addView(row("Unsent drafts recovery", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runAsync("Drafts", new Work() {
                    @Override
                    public String run() {
                        return PdbDatabase.drafts(PdbExtrasActivity.this, 30);
                    }
                });
            }
        }));
        card.addView(row("Backup & share everything (text)", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runAsync("Backup", new Work() {
                    @Override
                    public String run() {
                        return buildBackup();
                    }
                });
            }
        }));

        chatQuery = PdbUi.field(this, "chat search text (empty = latest)");
        card.addView(chatQuery);
    }

    private void textOverrides() {
        content.addView(PdbUi.sectionLabel(this, "Hidden text overrides (empty = off)"));
        LinearLayout card = PdbUi.card(this);
        content.addView(card, PdbUi.cardParams(this));
        for (final String[] rowSpec : TEXT_ROWS) {
            EditText edit = PdbUi.field(this, rowSpec[0]);
            edit.setText(PdbSettings.getString(PdbTools.prefs(this), rowSpec[1], ""));
            edit.setTag(rowSpec[1]);
            textFields.add(edit);
            card.addView(edit);
        }
        card.addView(row("Save text overrides", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                android.content.SharedPreferences.Editor e =
                    PdbTools.prefs(PdbExtrasActivity.this).edit();
                for (EditText edit : textFields) {
                    String key = (String) edit.getTag();
                    String value = edit.getText().toString().trim();
                    if (value.isEmpty()) e.remove(key);
                    else e.putString(key, value);
                }
                e.apply();
                show("Text overrides", "Saved. Relaunch Pdb to apply.", false);
            }
        }));
    }

    private void toggles() {
        content.addView(PdbUi.sectionLabel(this, "Toggles (applied on next launch)"));
        LinearLayout card = PdbUi.card(this);
        content.addView(card, PdbUi.cardParams(this));
        for (final String[] rowSpec : SWITCH_ROWS) {
            card.addView(switchRow(rowSpec[0], rowSpec[1]));
        }
    }

    private void identity() {
        content.addView(PdbUi.sectionLabel(this, "Identity and realtime socket (MMKV)"));
        LinearLayout card = PdbUi.card(this);
        content.addView(card, PdbUi.cardParams(this));

        deviceId = PdbUi.field(this, "XDeviceId value");
        String id = PdbTools.deviceId();
        if (!id.startsWith("(")) deviceId.setText(id);
        card.addView(deviceId);
        card.addView(row("Save device id", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                show("XDeviceId", PdbTools.setDeviceId(deviceId.getText().toString()), false);
            }
        }));
        card.addView(row("Randomize device id", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                show("XDeviceId", PdbTools.randomizeDeviceId(), false);
                deviceId.setText(PdbTools.deviceId());
            }
        }));
        imUrl = PdbUi.field(this, "wss://your-relay/connection/websocket");
        String url = PdbTools.imSocket();
        if (!url.startsWith("(")) imUrl.setText(url);
        card.addView(imUrl);
        card.addView(row("Save IM socket URL", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                show("IM socket", PdbTools.setImSocket(imUrl.getText().toString()), false);
            }
        }));
        card.addView(row("Clear IM socket override", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                show("IM socket", PdbTools.setImSocket(""), false);
                imUrl.setText("");
            }
        }));
        card.addView(row("Fake-logout flag: SET", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                show("Fake logout", PdbTools.setFakeLogout(true), false);
            }
        }));
        card.addView(row("Fake-logout flag: CLEAR", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                show("Fake logout", PdbTools.setFakeLogout(false), false);
            }
        }));
        card.addView(row("Dump MMKV store", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                show("MMKV", PdbTools.mmkvDump(), true);
            }
        }));
    }

    private void diagnostics() {
        content.addView(PdbUi.sectionLabel(this, "Diagnostics and backup"));
        LinearLayout card = PdbUi.card(this);
        content.addView(card, PdbUi.cardParams(this));
        card.addView(row("Export settings to clipboard (JSON)", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                show("Settings JSON", PdbTools.exportJson(PdbExtrasActivity.this), true);
            }
        }));
        card.addView(row("Import settings from clipboard (JSON)", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String json = PdbTools.readClipboard(PdbExtrasActivity.this);
                int restored = json == null ? 0 : PdbTools.importJson(PdbExtrasActivity.this, json);
                show("Import", restored == 0
                    ? "No importable Morphe JSON on the clipboard."
                    : restored + " value(s) imported. Relaunch Pdb to apply.", false);
            }
        }));
        card.addView(row("Diagnostics (Morphe + Pdb + MMKV keys)", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                show("Diagnostics", PdbTools.diagnostics(PdbExtrasActivity.this), true);
            }
        }));
        card.addView(row("Dump every preference key", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                show("Preferences", PdbTools.prefsDump(PdbExtrasActivity.this), true);
            }
        }));
        card.addView(row("Preset: maximum privacy", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PdbTools.applyPrivacyPreset(PdbExtrasActivity.this);
                show("Preset", "Maximum-privacy preset applied. Relaunch Pdb to apply.", false);
            }
        }));
        card.addView(row("Reset all Morphe values", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PdbTools.reset(PdbExtrasActivity.this);
                show("Reset", "Morphe values cleared. Relaunch Pdb to apply.", false);
            }
        }));
    }

    private void resultArea() {
        content.addView(PdbUi.sectionLabel(this, "Result"));
        LinearLayout card = PdbUi.card(this);
        content.addView(card, PdbUi.cardParams(this));
        result = PdbUi.text(this, "(results appear here)", 12, "gray_02", 0xFF3A3A3C);
        result.setTextIsSelectable(true);
        int pad = PdbUi.dp(this, 12);
        result.setPadding(pad, pad, pad, pad);
        ScrollView scroller = new ScrollView(this);
        scroller.addView(result);
        card.addView(scroller, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, PdbUi.dp(this, 220)));
    }

    private void footer() {
        LinearLayout card = PdbUi.card(this);
        content.addView(card, PdbUi.cardParams(this));
        card.addView(row("Open native dev screen (Tweaks)", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PdbNav.start(PdbExtrasActivity.this, PdbNav.TWEAKS);
            }
        }));
        card.addView(row("Close", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        }));
    }

    /* ── row helpers ── */

    private View row(String label, View.OnClickListener onClick) {
        return PdbUi.actionCell(this, label, onClick);
    }

    /** Reads the inspector field and starts a direct chat against that user. */
    private void directChat() {
        String id = PdbDatabase.resolveUserId(PdbExtrasActivity.this, input());
        if (id == null) {
            PdbTools.toast(this, "Morphe: enter a user id or @name first");
            return;
        }
        PdbTools.prefs(this).edit()
            .putString(PdbSettings.PDB_IM_CHAT_USER, id)
            .putString(PdbSettings.INSTANT_CHAT_USER, id).apply();
        PdbTools.toast(this, "Morphe: instant-chat target \u2192 #" + id);
        PdbNav.openInstantChat(this);
    }

    private String input() {
        return inspectorInput == null ? "" : inspectorInput.getText().toString();
    }

    private PdbMenu.Item simpleMenu(final String label, final Runnable action) {
        return new PdbMenu.Item() {
            @Override
            public String label() {
                return label;
            }

            @Override
            public void run() {
                action.run();
            }
        };
    }

    /** One text bundle with everything worth keeping: settings, stats, chats, visitors. */
    private String buildBackup() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Pdb Extras backup — ").append(PdbTools.summary(this)).append(" ===\n\n");
        sb.append("--- Settings JSON ---\n").append(PdbTools.exportJson(this)).append("\n\n");
        sb.append("--- Chat statistics ---\n").append(PdbDatabase.chatStats(this)).append("\n\n");
        sb.append("--- Visitor digest ---\n").append(PdbDatabase.visitorDigest(this, 100)).append("\n\n");
        sb.append("--- Recent chats ---\n").append(PdbTools.chatExport(this, null, 100)).append("\n\n");
        sb.append("--- Drafts ---\n").append(PdbDatabase.drafts(this, 100)).append('\n');
        String text = sb.toString();
        PdbTools.copyToClipboard(this, text);
        return text;
    }

    /* ── widgets ── */

    private View switchRow(String label, final String key) {
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setTextSize(15);
        sw.setAllCaps(false);
        sw.setTextColor(PdbUi.color(this, "gray_01", 0xFF1C1C1E));
        sw.setChecked(PdbSettings.getBool(PdbTools.prefs(this), key, false));
        int padV = PdbUi.dp(this, 10);
        sw.setPadding(PdbUi.dp(this, 14), padV, PdbUi.dp(this, 14), padV);
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                PdbTools.prefs(PdbExtrasActivity.this).edit().putBoolean(key, isChecked).apply();
                PdbTools.toast(PdbExtrasActivity.this,
                    "Morphe Extras: " + (isChecked ? "on" : "off")
                        + (key.equals(PdbSettings.ROW_ID_CHIPS)
                           || key.equals(PdbSettings.IDS_EVERYWHERE) ? "" : " \u2014 relaunch to apply"));
            }
        });
        return sw;
    }

    /* ── async plumbing ── */

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
