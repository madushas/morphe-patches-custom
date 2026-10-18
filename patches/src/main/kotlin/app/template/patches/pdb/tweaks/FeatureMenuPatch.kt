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

/** One row in the "Morphe Extras" category. */
private data class Toggle(
    val key: String,
    val title: String,
    val summary: String,
    val default: Boolean,
)

private val TOGGLES = listOf(
    Toggle("morphe_force_premium", "Force Pro (all entitlements)",
        "Unlocks every Pdb Pro feature and hides paywalls.", true),
    Toggle("morphe_no_ads", "Block all ads",
        "Disables every ad SDK and the in-app ad loader.", true),
    Toggle("morphe_privacy_receipts", "Disable IRL read receipts",
        "The other person never sees that you opened their IRL.", true),
    Toggle("morphe_privacy_see_more", "Ignore IRL \u201csee more\u201d",
        "Stop IRL view tracking when you expand a card.", true),
    Toggle("morphe_remember_auth", "Stay signed in",
        "Remember the Google sign-in so you are not asked again (Pdb's own switch).", true),
    Toggle("morphe_show_full_error", "Show full error messages",
        "Surface the complete error toast instead of a generic one (debug aid).", false),
    Toggle("morphe_im_connect_toast", "Stream connect toast",
        "Toast when the realtime (IM) socket connects (debug aid).", false),
    Toggle("morphe_auto_coin", "Auto-consume coins",
        "Automatically use a purchased coin when needed.", true),
    Toggle("morphe_force_english", "Force English",
        "Force the app UI to English regardless of system locale.", false),
    Toggle("morphe_new_post_component", "New post component",
        "Use Pdb's redesigned post composer.", false),
    Toggle("morphe_advanced_logging", "Verbose file logging",
        "Raise the app's file logger to Info level.", false),
    Toggle("morphe_global_pause", "Pause all Morphe extras",
        "Stop writing any values; leave the app untouched on next launch.", false),
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
            for (t in TOGGLES) {
                category.appendChild(
                    doc.el(
                        "SwitchPreference",
                        "android:key" to t.key,
                        "app:title" to t.title,
                        "app:summary" to t.summary,
                        "app:defaultValue" to t.default.toString(),
                    ),
                )
            }
            root.appendChild(category)

            val toolsCategory = doc.el("PreferenceCategory", "app:title" to "Morphe \u2014 Pdb Extras tools")
            val tools = doc.el(
                "Preference",
                "android:key" to "morphe_tools",
                "app:title" to "Extras tools",
                "app:summary" to "Export / import settings \u00b7 diagnostics \u00b7 presets",
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
