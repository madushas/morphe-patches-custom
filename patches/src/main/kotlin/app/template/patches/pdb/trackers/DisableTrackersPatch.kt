package app.template.patches.pdb.trackers

import app.morphe.patcher.patch.resourcePatch
import app.template.patches.shared.Constants.PDB_COMPATIBILITY
import org.w3c.dom.Element

private const val MANIFEST = "AndroidManifest.xml"

/** Third-party tracker components that are disabled in the manifest. */
private val TRACKER_COMPONENTS = listOf(
    // Google Analytics for Firebase (measurement) — usage/event ping machine.
    "com.google.android.gms.measurement.AppMeasurementReceiver",
    "com.google.android.gms.measurement.AppMeasurementService",
    "com.google.android.gms.measurement.AppMeasurementJobService",
    // Yandex AppMetrica — device telemetry service + its exported preload provider.
    "io.appmetrica.analytics.internal.PreloadInfoContentProvider",
    "io.appmetrica.analytics.internal.AppMetricaService",
    "io.appmetrica.analytics.IAppMetricaService",
)

/**
 * Crashlytics registers itself through Firebase's component registry via these
 * meta-data entries; removing them stops Crashlytics from ever initialising.
 *
 * `FirebaseInitProvider` is intentionally kept — the app needs core Firebase for
 * the email-verification sign-in flow.
 */
private val CRASHLYTICS_REGISTRARS = listOf(
    "com.google.firebase.components:com.google.firebase.crashlytics.CrashlyticsRegistrar",
    "com.google.firebase.components:com.google.firebase.crashlytics.FirebaseCrashlyticsKtxRegistrar",
    "com.google.firebase.components:com.google.firebase.crashlytics.ndk.CrashlyticsNdkRegistrar",
)

/**
 * Removes third-party telemetry at patch time.
 *
 *  - Google **measurement** receivers/services: disabled, so no usage events or
 *    install referrer pings reach Google.
 *  - **AppMetrica** (Yandex) provider + services: disabled, so the telemetry
 *    service cannot run (its ad sibling is already killed by Remove ads).
 *  - **Crashlytics** registrars: removed from the Firebase component registry.
 *
 * Deliberately untouched: `FirebaseInitProvider` and the Firebase messaging
 * receiver (push notifications are a real feature), and the app's own backend
 * endpoints.
 */
@Suppress("unused")
val pdbDisableTrackersPatch = resourcePatch(
    name = "Disable trackers",
    description = "Disables Google Analytics measurement, Yandex AppMetrica telemetry and " +
        "removes Firebase Crashlytics at the manifest level.",
    default = true,
) {
    compatibleWith(PDB_COMPATIBILITY)

    execute {
        document(MANIFEST).use { doc ->
            // Disable every tracker component (the manifest DOM does not expose
            // namespaced attributes via getAttribute; go through .attributes).
            val elements = doc.getElementsByTagName("*")
            for (i in 0 until elements.length) {
                val el = elements.item(i) as? Element ?: continue
                val name = el.attributes?.getNamedItem("android:name")?.nodeValue ?: continue
                if (name in TRACKER_COMPONENTS) {
                    el.setAttribute("android:enabled", "false")
                    if (el.tagName == "provider") el.setAttribute("android:exported", "false")
                }
            }

            // Unregister Crashlytics from the Firebase component registry.
            val metaDatas = doc.getElementsByTagName("meta-data")
            for (i in metaDatas.length - 1 downTo 0) {
                val el = metaDatas.item(i) as? Element ?: continue
                val name = el.attributes?.getNamedItem("android:name")?.nodeValue ?: continue
                if (name in CRASHLYTICS_REGISTRARS) {
                    el.parentNode?.removeChild(el)
                }
            }
        }
    }
}
