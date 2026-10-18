package app.template.extension.pdb;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Shared helpers for the Pdb Morphe extension.
 *
 * Pdb stores all of its preferences in {@code <packageName>_preferences}, which is
 * the same file its built-in Tweaks screen and its {@code isProUser()} gate read.
 * Writing here is therefore equivalent to toggling the app's own developer switches.
 */
public final class PdbSettings {

    public static final String PREFS_SUFFIX = "_preferences";

    /* ── Extension feature keys (injected into res/xml/root_preferences.xml) ── */
    public static final String FORCE_PREMIUM    = "morphe_force_premium";
    public static final String NO_ADS           = "morphe_no_ads";
    public static final String PRIVACY_RECEIPTS = "morphe_privacy_receipts";
    public static final String PRIVACY_SEE_MORE = "morphe_privacy_see_more";
    public static final String REMEMBER_AUTH    = "morphe_remember_auth";
    public static final String SHOW_FULL_ERROR  = "morphe_show_full_error";
    public static final String IM_CONNECT_TOAST = "morphe_im_connect_toast";
    public static final String AUTO_COIN        = "morphe_auto_coin";
    public static final String FORCE_ENGLISH    = "morphe_force_english";
    public static final String NEW_POST         = "morphe_new_post_component";
    public static final String ADVANCED_LOGGING = "morphe_advanced_logging";
    public static final String GLOBAL_PAUSE     = "morphe_global_pause";

    public static final String[] ALL_KEYS = {
        FORCE_PREMIUM, NO_ADS, PRIVACY_RECEIPTS, PRIVACY_SEE_MORE, REMEMBER_AUTH,
        SHOW_FULL_ERROR, IM_CONNECT_TOAST, AUTO_COIN, FORCE_ENGLISH, NEW_POST,
        ADVANCED_LOGGING, GLOBAL_PAUSE,
    };

    /* ── Pdb's own preference keys that we drive ── */
    public static final String PDB_MOCK_PRO     = "mockIsProUser";
    public static final String PDB_AD_OFF       = "isADLibDisabled";
    public static final String PDB_NO_READ      = "disableIRLMarkRead";
    public static final String PDB_IGNORE_SEE   = "ignoreIRLMore";
    public static final String PDB_REMEMBER_AUTH = "rememberGoogleAuth";
    public static final String PDB_SHOW_ERROR   = "showFullError";
    public static final String PDB_IM_TOAST     = "imConnectToast";
    public static final String PDB_AUTO_COIN    = "autoConsumePDBCoin";
    public static final String PDB_FORCE_EN     = "forceEnglish";
    public static final String PDB_NEW_POST     = "useNewPostComponent";
    public static final String PDB_LOG_LEVEL    = "fileLogLevel";

    /** Pdb's own preference keys, in the order shown by the diagnostics action. */
    public static final String[] PDB_KEY_ORDER = {
        PDB_MOCK_PRO, PDB_AD_OFF, PDB_NO_READ, PDB_IGNORE_SEE, PDB_REMEMBER_AUTH,
        PDB_SHOW_ERROR, PDB_IM_TOAST, PDB_AUTO_COIN, PDB_FORCE_EN, PDB_NEW_POST,
        PDB_LOG_LEVEL, GLOBAL_PAUSE,
    };

    public static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(ctx.getPackageName() + PREFS_SUFFIX, Context.MODE_PRIVATE);
    }

    public static boolean getBool(SharedPreferences sp, String key, boolean def) {
        try {
            return sp.getBoolean(key, def);
        } catch (Throwable ignored) {
            return def;
        }
    }

    private PdbSettings() {}
}
