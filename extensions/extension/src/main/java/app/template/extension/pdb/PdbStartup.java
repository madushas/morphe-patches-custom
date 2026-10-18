package app.template.extension.pdb;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Applies the "Morphe Extras" switches to Pdb's own preference store.
 *
 * Called as early as possible (from {@link PdbInitProvider}, which Android
 * instantiates before {@code Application.onCreate()}, and from a hook in
 * {@code pdb.app.App.attachBaseContext(Context)}), so the values are in place
 * before the app reads them.
 *
 * Every switch writes the mapped app preference to an explicit {@code true} or
 * {@code false}, so turning a switch off actually reverts the app setting instead
 * of silently leaving the previous run's value behind.
 */
public final class PdbStartup {

    private static final String TAG = "PdbExtras";
    private static volatile boolean applied = false;

    private PdbStartup() {}

    /** Hooked from {@code pdb.app.App.attachBaseContext(Context)}. */
    public static void onAppCreate(Context context) {
        applyDefaults(context);
    }

    public static synchronized void applyDefaults(Context context) {
        if (applied || context == null) return;
        applied = true;

        try {
            SharedPreferences sp = PdbSettings.prefs(context);

            if (PdbSettings.getBool(sp, PdbSettings.GLOBAL_PAUSE, false)) {
                Log.i(TAG, "global pause set \u2014 leaving app preferences untouched");
                return;
            }

            SharedPreferences.Editor e = sp.edit();

            boolean pro      = PdbSettings.getBool(sp, PdbSettings.FORCE_PREMIUM, true);
            boolean noAds    = PdbSettings.getBool(sp, PdbSettings.NO_ADS, true);
            boolean receipts = PdbSettings.getBool(sp, PdbSettings.PRIVACY_RECEIPTS, true);
            boolean seeMore  = PdbSettings.getBool(sp, PdbSettings.PRIVACY_SEE_MORE, true);
            boolean remember = PdbSettings.getBool(sp, PdbSettings.REMEMBER_AUTH, true);
            boolean fullErr  = PdbSettings.getBool(sp, PdbSettings.SHOW_FULL_ERROR, false);
            boolean imToast  = PdbSettings.getBool(sp, PdbSettings.IM_CONNECT_TOAST, false);
            boolean coin     = PdbSettings.getBool(sp, PdbSettings.AUTO_COIN, true);
            boolean english  = PdbSettings.getBool(sp, PdbSettings.FORCE_ENGLISH, false);
            boolean newPost  = PdbSettings.getBool(sp, PdbSettings.NEW_POST, false);
            boolean verbose  = PdbSettings.getBool(sp, PdbSettings.ADVANCED_LOGGING, false);

            // Premium + ads: drive the app's own developer backdoor / ad kill-switch.
            e.putBoolean(PdbSettings.PDB_MOCK_PRO, pro);
            e.putBoolean(PdbSettings.PDB_AD_OFF, noAds);

            // Privacy.
            e.putBoolean(PdbSettings.PDB_NO_READ, receipts);
            e.putBoolean(PdbSettings.PDB_IGNORE_SEE, seeMore);

            // Session + diagnostics.
            e.putBoolean(PdbSettings.PDB_REMEMBER_AUTH, remember);
            e.putBoolean(PdbSettings.PDB_SHOW_ERROR, fullErr);
            e.putBoolean(PdbSettings.PDB_IM_TOAST, imToast);

            // Quality of life.
            e.putBoolean(PdbSettings.PDB_AUTO_COIN, coin);
            e.putBoolean(PdbSettings.PDB_FORCE_EN, english);
            e.putBoolean(PdbSettings.PDB_NEW_POST, newPost);

            // Verbose logging: "4" is the app's own "info" level; removing the key
            // restores the app default when the switch is off.
            if (verbose) {
                e.putString(PdbSettings.PDB_LOG_LEVEL, "4");
            } else {
                e.remove(PdbSettings.PDB_LOG_LEVEL);
            }

            e.apply();

            Log.i(
                TAG,
                "extras applied: mockPro=" + pro
                    + " adOff=" + noAds
                    + " receipts=" + receipts
                    + " seeMore=" + seeMore
                    + " rememberAuth=" + remember
                    + " fullError=" + fullErr
                    + " imToast=" + imToast
                    + " autoCoin=" + coin
                    + " english=" + english
                    + " newPost=" + newPost
                    + " verbose=" + verbose
            );
        } catch (Throwable t) {
            Log.w(TAG, "applyDefaults failed", t);
        }
    }
}
