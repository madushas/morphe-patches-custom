package app.template.patches.pdb.tweaks

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.template.patches.pdb.AppAttachBaseContextFingerprint
import app.template.patches.shared.Constants.PDB_COMPATIBILITY
import org.w3c.dom.Document
import org.w3c.dom.Element

private const val ROOT_PREFS = "res/xml/root_preferences.xml"
private const val EXT_PREFIX = "Lapp/template/extension/pdb/"
private const val INIT_PROVIDER = "app.template.extension.pdb.PdbInitProvider"

/** One boolean row in the "Morphe Extras" category. */
private data class SwitchRow(
    val key: String,
    val title: String,
    val summary: String,
    val default: Boolean,
)

/** One free-text row in the "Morphe Extras" category. */
private data class TextRow(
    val key: String,
    val title: String,
    val summary: String,
    val dialogTitle: String,
)

/**
 * Boolean rows. Each one maps onto a preference Pdb itself reads, applied at startup
 * by the extension (`PdbStartup`), so the switch and the app setting cannot drift.
 *
 * Premium and ad removal are NOT here on purpose: they are forced by the bytecode
 * patches at patch time (`Unlock Pro`, `Remove ads`) and by two unconditional
 * startup writes, so they stay on with nothing to toggle.
 */
private val SWITCHES = listOf(
    SwitchRow("morphe_privacy_receipts", "Disable IRL read receipts",
        "The other person never sees that you opened their IRL.", true),
    SwitchRow("morphe_privacy_see_more", "Ignore IRL \u201csee more\u201d",
        "Stop IRL view tracking when you expand a card.", true),
    SwitchRow("morphe_remember_auth", "Stay signed in",
        "Remember the Google sign-in so you are not asked again (Pdb's own switch).", true),
    SwitchRow("morphe_force_offer", "Force offer sheet (dev)",
        "forceShowOffer \u2014 makes Pdb show its offer/paywall sheet again.", false),
    SwitchRow("morphe_api_staging", "Use staging backend (dev)",
        "apiIsStaging \u2014 points the app at Pdb's staging API. Separate accounts/data.", false),
    SwitchRow("morphe_show_full_error", "Show full error messages",
        "Surface the complete error toast instead of a generic one (debug aid).", false),
    SwitchRow("morphe_im_connect_toast", "Stream connect toast",
        "Toast when the realtime (IM) socket connects (debug aid).", false),
    SwitchRow("morphe_force_english", "Force English",
        "Force the app UI to English regardless of system locale.", false),
    SwitchRow("morphe_advanced_logging", "Verbose file logging",
        "Raise the app's file logger to Info level.", false),
    SwitchRow("morphe_global_pause", "Pause all Morphe extras",
        "Stop writing any values; leave the app untouched on next launch.", false),
)

/**
 * Free-text rows. These drive Pdb preferences that take a value, none of which the
 * stock UI exposes: an empty value removes the app key again.
 */
private val TEXTS = listOf(
    TextRow("morphe_geo_country", "Feed country override",
        "mockFeedCountryCode \u2014 for example US, GB, DE. Empty = off.",
        "Feed country code"),
    TextRow("morphe_whitelist_host", "Web allow-list host",
        "whiteListHost \u2014 extra host the in-app browser may load. Empty = off.",
        "Allow-list host"),
    TextRow("morphe_bubble_url", "Bubble entry URL",
        "mockBubbleEntranceURL \u2014 repoints the bubble entry. Empty = off.",
        "Bubble entry URL"),
    TextRow("morphe_mock_latlng", "Mock GPS location",
        "mockLatLng \u2014 lat,lng, for example 51.50,-0.12. Empty = off.",
        "Mock location"),
    TextRow("morphe_instant_chat_user", "Force instant-chat partner",
        "debugInstantChatUserId \u2014 user id to be matched with instantly. Empty = off.",
        "Instant chat user id"),
)

private fun Document.el(tag: String, vararg attrs: Pair<String, String>): Element =
    createElement(tag).apply { attrs.forEach { (k, v) -> setAttribute(k, v) } }

private const val TOOLS_ACTIVITY = "app.template.extension.pdb.PdbExtrasActivity"

/**
 * Resource side of the feature menu:
 *  1. Injects a "Morphe — Pdb Extras" category into the app's **existing** Tweaks
 *     screen (`res/xml/root_preferences.xml`), which is already reachable from
 *     Pdb's Settings (route `/tweak/setting`).
 *  2. Adds an "Extras tools" row that opens the extension's own tools screen.
 *  3. Registers the extension's startup [INIT_PROVIDER] and [TOOLS_ACTIVITY].
 *
 * The rows are deliberately flat and plain. Pdb's Tweaks host fragment implements
 * neither `OnPreferenceStartScreenCallback` (nested screens) nor
 * `OnPreferenceStartFragmentCallback` (`android:fragment` rows), and both were
 * confirmed inert on device. Only `SwitchPreference` rows — they toggle inside
 * `Preference.onClick()`, before the host is consulted — and rows carrying an
 * `<intent>`, handled by `Preference.performClick()` in the platform, react.
 */
@Suppress("unused")
val pdbFeatureMenuResourcePatch = resourcePatch(
    name = "Pdb Extras \u2014 resources",
    description = "Injects the Morphe Extras preference menu, the tools entry and the startup provider.",
    default = true,
) {
    compatibleWith(PDB_COMPATIBILITY)

    execute {
        document(ROOT_PREFS).use { doc ->
            val root = doc.documentElement

            val category = doc.el("PreferenceCategory", "app:title" to "Morphe \u2014 Pdb Extras")

            for (s in SWITCHES) {
                category.appendChild(
                    doc.el(
                        "SwitchPreference",
                        "android:key" to s.key,
                        "app:title" to s.title,
                        "app:summary" to s.summary,
                        "app:defaultValue" to s.default.toString(),
                    ),
                )
            }

            for (t in TEXTS) {
                category.appendChild(
                    doc.el(
                        "EditTextPreference",
                        "android:key" to t.key,
                        "app:title" to t.title,
                        "app:summary" to t.summary,
                        "android:dialogTitle" to t.dialogTitle,
                    ),
                )
            }

            root.appendChild(category)

            val toolsCategory = doc.el("PreferenceCategory", "app:title" to "Morphe \u2014 Pdb Extras tools")
            val tools = doc.el(
                "Preference",
                "android:key" to "morphe_tools",
                "app:title" to "Extras tools",
                "app:summary" to "Settings \u00b7 any preference \u00b7 chat data \u00b7 identity + socket",
            )
            tools.appendChild(
                doc.el(
                    "intent",
                    "android:targetPackage" to "pdb.app",
                    "android:targetClass" to TOOLS_ACTIVITY,
                    "android:data" to "pdbx://tools",
                ),
            )
            toolsCategory.appendChild(tools)
            root.appendChild(toolsCategory)
        }

        document("AndroidManifest.xml").use { doc ->
            val application = doc.getElementsByTagName("application").item(0) as Element

            val providers = doc.getElementsByTagName("provider")
            val providerPresent = (0 until providers.length).any {
                (providers.item(it) as? Element)
                    ?.attributes?.getNamedItem("android:name")?.nodeValue == INIT_PROVIDER
            }
            if (!providerPresent) {
                application.appendChild(
                    doc.el(
                        "provider",
                        "android:name" to INIT_PROVIDER,
                        "android:authorities" to "pdb.app.morphe.init",
                        "android:exported" to "false",
                        "android:initOrder" to "100",
                    ),
                )
            }

            val activities = doc.getElementsByTagName("activity")
            val activityPresent = (0 until activities.length).any {
                (activities.item(it) as? Element)
                    ?.attributes?.getNamedItem("android:name")?.nodeValue == TOOLS_ACTIVITY
            }
            if (!activityPresent) {
                application.appendChild(
                    doc.el(
                        "activity",
                        "android:name" to TOOLS_ACTIVITY,
                        "android:exported" to "false",
                        "android:excludeFromRecents" to "true",
                        "android:noHistory" to "true",
                    ),
                )
            }
        }
    }
}

/**
 * Feature menu (bytecode side).
 *
 * Installs a single early hook into `pdb.app.App.attachBaseContext(Context)` that
 * calls the extension's startup routine. That routine applies every "Morphe Extras"
 * toggle to the app's own preference store and force-enables the premium + no-ads
 * backdoors.
 *
 * The preference UI itself is injected by [pdbFeatureMenuResourcePatch] into the
 * app's existing Tweaks screen; the "Extras tools" entry opens
 * `app.template.extension.pdb.PdbExtrasActivity` (bundled in the extension).
 */
@Suppress("unused")
val pdbFeatureMenuPatch = bytecodePatch(
    name = "Pdb Extras feature menu",
    description = "Adds a \u201cMorphe \u2014 Pdb Extras\u201d menu to Pdb's built-in Tweaks " +
        "screen (Settings \u2192 Tweaks) with premium, ad, privacy and power-user " +
        "switches, plus a tools screen (export / import / diagnostics / presets).",
    default = true,
) {
    compatibleWith(PDB_COMPATIBILITY)

    dependsOn(pdbFeatureMenuResourcePatch)

    extendWith("extensions/extension.mpe")

    execute {
        // Earliest hook: right after Application.attachBaseContext() has run.
        // `pdb.app.App.attachBaseContext(Context)` has `.locals 0` + one
        // parameter, so the inline-smali frame is `.registers 2` and `p1` == v1
        // (valid). Injecting at index 1 keeps it after super.attachBaseContext(),
        // so the base context is attached and SharedPreferences are usable.
        AppAttachBaseContextFingerprint.method.addInstructions(
            1,
            "invoke-static {p1}, ${EXT_PREFIX}PdbStartup;->onAppCreate(Landroid/content/Context;)V",
        )
    }
}
