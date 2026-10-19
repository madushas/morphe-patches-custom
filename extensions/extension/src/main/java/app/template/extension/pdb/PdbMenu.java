package app.template.extension.pdb;

import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;

import java.util.List;

/**
 * A lightweight power-menu popup for long-presses on app rows (user rows, chat rows).
 *
 * <p>The app's own {@code AlertDialog} theme renders no list panel (device-confirmed),
 * so options are drawn as native {@code ActionCell} rows inside a {@link PopupWindow}
 * instead. Used by the injected row hooks (copy ID, open profile, direct chat…).
 */
public final class PdbMenu {

    /** One menu entry. */
    public interface Item {
        String label();

        void run();
    }

    private PdbMenu() {}

    public static void show(final View anchor, final List<Item> items) {
        if (anchor == null || items == null || items.isEmpty()) return;
        try {
            final android.content.Context ctx = anchor.getContext();
            final PopupWindow[] holder = new PopupWindow[1];

            LinearLayout box = new LinearLayout(ctx);
            box.setOrientation(LinearLayout.VERTICAL);

            ScrollView scroll = new ScrollView(ctx);
            scroll.addView(box);

            for (final Item item : items) {
                box.addView(PdbUi.actionCell(ctx, item.label(), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (holder[0] != null) {
                            try {
                                holder[0].dismiss();
                            } catch (Throwable ignored) {
                            }
                        }
                        try {
                            item.run();
                        } catch (Throwable ignored) {
                        }
                    }
                }), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }

            int pad = PdbUi.dp(ctx, 2);
            LinearLayout outer = new LinearLayout(ctx);
            outer.setOrientation(LinearLayout.VERTICAL);
            outer.setPadding(pad, pad, pad, pad);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(PdbUi.color(ctx, "content_bg", 0xFFF2F2F7));
            bg.setCornerRadius(PdbUi.dp(ctx, 12));
            outer.setBackground(bg);
            outer.addView(scroll);

            PopupWindow popup = new PopupWindow(
                outer, PdbUi.dp(ctx, 260), ViewGroup.LayoutParams.WRAP_CONTENT, true);
            holder[0] = popup;
            popup.setOutsideTouchable(true);
            popup.setBackgroundDrawable(new GradientDrawable());
            popup.showAtLocation(anchor, Gravity.CENTER, 0, 0);
        } catch (Throwable ignored) {
            // A failed popup must never break the host row.
        }
    }
}
