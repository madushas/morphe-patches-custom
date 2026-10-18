package app.template.extension.pdb;

import android.content.SharedPreferences;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Reflection access to Pdb's MMKV store.
 *
 * <p>Three of the app's knobs live outside {@code SharedPreferences}, in the MMKV file
 * {@code files/mmkv/pdb}:
 * <ul>
 *   <li>{@code XDeviceId} — the device identity the app sends with its API calls,</li>
 *   <li>{@code chatCentrifugoWsUrl} — the realtime (IM) socket URL,</li>
 *   <li>{@code isFakeLogout<account>} — the app's own "logged out but keep the
 *       remembered Google auth" flag.</li>
 * </ul>
 *
 * <p>The MMKV class name is kept ({@code com.tencent.mmkv.MMKV}) but its methods are
 * R8-renamed, so the instance is resolved by <em>signature</em> — the single
 * {@code static MMKV <name>(String id)} method — rather than by name. Nothing else
 * needs reflecting: MMKV implements {@link SharedPreferences}, whose method names
 * cannot be renamed, so the standard preference API is safe to use directly.
 *
 * <p>The instance id ({@code "pdb"}) and root directory ({@code filesDir/mmkv}) were
 * read from the app itself: it initialises MMKV with
 * {@code filesDir + "/mmkv"} and then calls {@code mmkvWithID("pdb")}.
 */
public final class PdbMmkv {

    /** MMKV instance id used by the app. */
    public static final String MMKV_ID = "pdb";

    public static final String KEY_DEVICE_ID = "XDeviceId";
    public static final String KEY_IM_SOCKET = "chatCentrifugoWsUrl";
    public static final String KEY_FAKE_LOGOUT_PREFIX = "isFakeLogout";

    private static final String MMKV_CLASS = "com.tencent.mmkv.MMKV";

    private static volatile boolean resolved = false;
    private static volatile SharedPreferences store = null;

    private PdbMmkv() {}

    /**
     * @return the app's MMKV store, or {@code null} when MMKV is not initialised yet
     *     (it is initialised from the app's own start-up path) or when the class is
     *     missing.
     */
    public static SharedPreferences store() {
        if (!resolved) resolve();
        return store;
    }

    private static synchronized void resolve() {
        resolved = true;
        try {
            Class<?> mmkv = Class.forName(MMKV_CLASS);
            Method withId = findWithId(mmkv, mmkv.getDeclaredMethods());
            if (withId == null) withId = findWithId(mmkv, mmkv.getMethods());
            if (withId == null) return;

            Object instance = withId.invoke(null, MMKV_ID);
            if (instance instanceof SharedPreferences) store = (SharedPreferences) instance;
        } catch (Throwable ignored) {
            store = null;
        }
    }

    /** {@code static MMKV mmkvWithID(String)} — the only static String→MMKV method. */
    private static Method findWithId(Class<?> mmkv, Method[] methods) {
        if (methods == null) return null;
        for (Method m : methods) {
            if (!Modifier.isStatic(m.getModifiers())) continue;
            Class<?>[] params = m.getParameterTypes();
            if (params.length == 1
                && params[0] == String.class
                && mmkv.isAssignableFrom(m.getReturnType())) {
                return m;
            }
        }
        return null;
    }

    public static boolean available() {
        return store() != null;
    }

    public static String getString(String key) {
        SharedPreferences sp = store();
        if (sp == null) return null;
        try {
            return sp.getString(key, null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean getBool(String key, boolean def) {
        SharedPreferences sp = store();
        if (sp == null) return def;
        try {
            return sp.getBoolean(key, def);
        } catch (Throwable ignored) {
            return def;
        }
    }

    public static boolean putString(String key, String value) {
        SharedPreferences sp = store();
        if (sp == null) return false;
        try {
            // MMKV's SharedPreferences write-through: apply() is a no-op because the
            // value is persisted by putString() itself.
            sp.edit().putString(key, value).apply();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean putBool(String key, boolean value) {
        SharedPreferences sp = store();
        if (sp == null) return false;
        try {
            sp.edit().putBoolean(key, value).apply();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean remove(String key) {
        SharedPreferences sp = store();
        if (sp == null) return false;
        try {
            sp.edit().remove(key).apply();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Keys currently present in the store, sorted. */
    public static java.util.List<String> keys() {
        java.util.List<String> out = new java.util.ArrayList<>();
        SharedPreferences sp = store();
        if (sp == null) return out;
        try {
            out.addAll(sp.getAll().keySet());
        } catch (Throwable ignored) {
        }
        java.util.Collections.sort(out);
        return out;
    }

    /** {@code isFakeLogout<account>} — the app suffixes the flag with the account id. */
    public static java.util.List<String> fakeLogoutKeys() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String key : keys()) {
            if (key.startsWith(KEY_FAKE_LOGOUT_PREFIX)) out.add(key);
        }
        return out;
    }

    /** Random-looking device id in the shape the app uses (a UUID string). */
    public static String newDeviceId() {
        return java.util.UUID.randomUUID().toString();
    }
}
