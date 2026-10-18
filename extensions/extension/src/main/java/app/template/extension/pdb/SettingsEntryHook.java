package app.template.extension.pdb;

import android.content.Context;
import android.content.Intent;
import android.view.View;

/**
 * Wires the "Pdb Extras" entry row injected into Pdb's Settings screen.
 *
 * <p>The row is an ordinary {@code pdb.app.base.wigets.ActionCell} added to
 * {@code res/layout/fragment_app_setting.xml} with the id {@code morphe_extras_entry}.
 * The patch calls {@link #wire} from the settings fragment's
 * {@code onViewCreated(View, Bundle)} (matched by signature, so obfuscated method and
 * class names do not matter), which looks the row up by id and attaches the listener
 * that opens {@link PdbExtrasActivity}.
 *
 * <p>Everything is guarded: if the row or the id is missing (different Pdb build,
 * different screen), this is a no-op.
 */
public final class SettingsEntryHook {

    public static final String ENTRY_ID = "morphe_extras_entry";

    private SettingsEntryHook() {}

    /** Called from the settings fragment's {@code onViewCreated(View, Bundle)}. */
    public static void wire(View root) {
        try {
            if (root == null) return;
            Context ctx = root.getContext();
            if (ctx == null) return;

            int id = ctx.getResources().getIdentifier(ENTRY_ID, "id", ctx.getPackageName());
            if (id == 0) return;

            View entry = root.findViewById(id);
            if (entry == null) return;

            entry.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        Context c = v.getContext();
                        Intent intent = new Intent();
                        intent.setClassName(c.getPackageName(), PdbExtrasActivity.class.getName());
                        c.startActivity(intent);
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }
}
