package app.template.extension.pdb;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import app.template.extension.BuildConfig;

/**
 * The native Soul history screen: every Soul profile you saw or liked, newest
 * first, straight from the local record the "Soul history" patch maintains
 * ({@link PdbUi} ring buffer in the app's MMKV). Unlike the in-app rewind
 * sheet, this list is not bounded by the server's short feed window.
 *
 * <p>Per entry: tap the row opens the profile (pdb://u/{id}); the Chat button
 * opens a 1:1 chat when a channel exists locally ({@link PdbTools#openChat}),
 * falling back to the profile; long-press copies the id.
 */
public final class SoulHistoryActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            PdbMmkv.init(this);
            setContentView(buildScreen());
        } catch (Throwable t) {
            PdbTools.toast(this, "Soul history: " + t);
            finish();
        }
    }

    private View buildScreen() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int pad = PdbUi.dp(this, 16);
        page.setPadding(pad, PdbUi.dp(this, 12), pad, 0);

        TextView title = PdbUi.text(this,
            "Soul history v" + BuildConfig.EXTRAS_VERSION, 22, "gray_01", 0xFF1C1C1E);
        title.setPadding(0, 0, 0, PdbUi.dp(this, 2));
        page.addView(title);

        TextView sub = PdbUi.text(this,
            "Local record \u00b7 newest first \u00b7 tap row = profile \u00b7 "
                + "Chat opens a conversation \u00b7 long-press copies the id",
            12, "gray_03", 0xFF8E8E93);
        sub.setPadding(0, 0, 0, PdbUi.dp(this, 8));
        page.addView(sub);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, PdbUi.dp(this, 4), 0, PdbUi.dp(this, 24));

        java.util.List<PdbUi.SoulRow> rows = PdbUi.soulHistory();
        if (rows.isEmpty()) {
            TextView empty = PdbUi.text(this,
                "No history yet \u2014 swipe the Soul feed and come back.",
                14, "gray_02", 0xFF3A3A3C);
            empty.setPadding(0, PdbUi.dp(this, 16), 0, 0);
            list.addView(empty);
        }
        int shown = 0;
        for (final PdbUi.SoulRow r : rows) {
            if (shown++ >= 200) break;
            list.addView(row(r));
        }

        LinearLayout closeRow = new LinearLayout(this);
        closeRow.setOrientation(LinearLayout.HORIZONTAL);
        closeRow.setGravity(Gravity.CENTER);
        Button close = new Button(this);
        close.setText("Close");
        close.setAllCaps(false);
        close.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = PdbUi.dp(this, 8);
        closeRow.addView(close, lp);
        list.addView(closeRow);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(list);
        page.addView(scroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return page;
    }

    /** One history row: who + when, tap = profile, long-press = copy, Chat btn. */
    private View row(final PdbUi.SoulRow r) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, PdbUi.dp(this, 6), 0, PdbUi.dp(this, 6));

        TextView tv = new TextView(this);
        String when = android.text.format.DateUtils.getRelativeTimeSpanString(r.ts).toString();
        String head = (r.action.equals("like") ? "\uD83E\uDDE1 " : "") + r.name;
        tv.setText(head + "\n#" + r.id + "  \u00b7  " + when);
        tv.setTextSize(15);
        tv.setTypeface(tv.getTypeface(), r.action.equals("like")
            ? Typeface.BOLD : Typeface.NORMAL);
        tv.setTextColor(PdbUi.color(this, "gray_01", 0xFF1C1C1E));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tv.setPadding(PdbUi.dp(this, 14), 0, PdbUi.dp(this, 6), 0);
        row.addView(tv, tp);

        Button chat = new Button(this);
        chat.setText("Chat");
        chat.setAllCaps(false);
        chat.setTextSize(13);
        chat.setOnClickListener(v -> PdbTools.openChat(this, r.id));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.rightMargin = PdbUi.dp(this, 4);
        row.addView(chat, cp);

        row.setOnClickListener(v -> PdbNav.openLink(this, "pdb://u/" + r.id));
        row.setOnLongClickListener(v -> {
            PdbTools.copyToClipboard(this, r.id);
            PdbTools.toast(this, "Copied " + r.id);
            return true;
        });
        return row;
    }
}
