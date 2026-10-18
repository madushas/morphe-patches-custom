package app.template.extension.pdb;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.Iterator;

/**
 * Framework-only helpers behind the "Morphe \u2014 Pdb Extras" tools row.
 *
 * <p>No AndroidX type is referenced on purpose. Pdb's own Tweaks host fragment
 * implements neither {@code OnPreferenceStartScreenCallback} nor
 * {@code OnPreferenceStartFragmentCallback}, so nested {@code PreferenceScreen}s and
 * {@code android:fragment} rows are inert there (verified on device). The tools entry
 * therefore opens {@link PdbExtrasActivity}, a plain {@link android.app.Activity}
 * running a framework dialog.
 *
 * <p>{@code SwitchPreference} rows are unaffected by that limitation: their toggle
 * happens in {@code Preference.onClick()}, before the host is ever consulted.
 */
public final class PdbTools {

    private PdbTools() {}

    /* ── plumbing ── */

    public static SharedPreferences prefs(Context ctx) {
        return PdbSettings.prefs(ctx);
    }

    public static void toast(Context ctx, String message) {
        try {
            Toast.makeText(ctx.getApplicationContext(), message, Toast.LENGTH_LONG).show();
        } catch (Throwable ignored) {
        }
    }

    public static void copyToClipboard(Context ctx, String text) {
        try {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("pdb-extras", text));
        } catch (Throwable ignored) {
        }
    }

    public static String readClipboard(Context ctx) {
        try {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || cm.getPrimaryClip() == null || cm.getPrimaryClip().getItemCount() == 0) {
                return null;
            }
            CharSequence cs = cm.getPrimaryClip().getItemAt(0).coerceToText(ctx);
            return cs == null ? null : cs.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /* ── status ── */

    /** Short header shown at the top of the tools dialog. */
    public static String summary(Context ctx) {
        SharedPreferences sp = prefs(ctx);
        return "Force Pro: " + onOff(PdbSettings.getBool(sp, PdbSettings.FORCE_PREMIUM, true))
            + "    Ads blocked: " + onOff(PdbSettings.getBool(sp, PdbSettings.NO_ADS, true))
            + "    Paused: " + onOff(PdbSettings.getBool(sp, PdbSettings.GLOBAL_PAUSE, false))
            + "\nValues live in " + ctx.getPackageName() + PdbSettings.PREFS_SUFFIX;
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    /** Full {@code key = value} report for every switch, Morphe and Pdb alike. */
    public static String diagnostics(Context ctx) {
        SharedPreferences sp = prefs(ctx);
        StringBuilder sb = new StringBuilder("Morphe \u2014 Pdb Extras\n");
        for (String key : PdbSettings.ALL_KEYS) {
            sb.append("  ").append(key).append(" = ").append(value(sp, key)).append('\n');
        }
        sb.append("Pdb preferences\n");
        for (String key : PdbSettings.PDB_KEY_ORDER) {
            sb.append("  ").append(key).append(" = ").append(value(sp, key)).append('\n');
        }
        return sb.toString();
    }

    private static String value(SharedPreferences sp, String key) {
        Object v = sp.getAll().get(key);
        return v == null ? "(unset)" : String.valueOf(v);
    }

    /* ── actions ── */

    public static String exportJson(Context ctx) {
        try {
            SharedPreferences sp = prefs(ctx);
            JSONObject out = new JSONObject();
            for (String key : PdbSettings.ALL_KEYS) {
                if (sp.contains(key)) out.put(key, PdbSettings.getBool(sp, key, false));
            }
            return out.toString(2);
        } catch (Throwable t) {
            return "{}";
        }
    }

    /** @return how many switches were restored. */
    public static int importJson(Context ctx, String json) {
        int restored = 0;
        try {
            JSONObject in = new JSONObject(json);
            SharedPreferences.Editor e = prefs(ctx).edit();
            for (Iterator<String> keys = in.keys(); keys.hasNext(); ) {
                String key = keys.next();
                if (isMorpheKey(key)) {
                    e.putBoolean(key, in.optBoolean(key, false));
                    restored++;
                }
            }
            e.apply();
        } catch (Throwable ignored) {
        }
        return restored;
    }

    /** Preset: maximum privacy, paying nothing for it. */
    public static void applyPrivacyPreset(Context ctx) {
        prefs(ctx).edit()
            .putBoolean(PdbSettings.PRIVACY_RECEIPTS, true)
            .putBoolean(PdbSettings.PRIVACY_SEE_MORE, true)
            .putBoolean(PdbSettings.NO_ADS, true)
            .putBoolean(PdbSettings.FORCE_PREMIUM, true)
            .putBoolean(PdbSettings.SHOW_FULL_ERROR, false)
            .putBoolean(PdbSettings.IM_CONNECT_TOAST, false)
            .apply();
    }

    /** Drop every Morphe key, so the next launch rewrites the packaged defaults. */
    public static void reset(Context ctx) {
        SharedPreferences.Editor e = prefs(ctx).edit();
        for (String key : PdbSettings.ALL_KEYS) e.remove(key);
        e.apply();
    }

    private static boolean isMorpheKey(String key) {
        for (String known : PdbSettings.ALL_KEYS) {
            if (known.equals(key)) return true;
        }
        return false;
    }
}
