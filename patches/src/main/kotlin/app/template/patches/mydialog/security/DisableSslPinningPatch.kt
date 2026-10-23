package app.template.patches.mydialog.security

import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.mydialog.CertificatePinCheck
import app.template.patches.shared.Constants.MYDIALOG_COMPATIBILITY
import app.template.patches.shared.returnEarly

/**
 * MyDialog pins server certificates through `CustomCertificatePinner`:
 * an `EnhancedTrustManager` runs the default system validation and then
 * compares the chain against three baked-in sha256 pins
 * (`CustomCertificatePinner.d(X509Certificate[])`), and an OkHttp interceptor
 * additionally probes stack traces / proxy headers for hooking frameworks.
 *
 * Removing the pin comparison (not the system validation) keeps normal TLS
 * intact — user-signed CAs still need user consent via the standard Android
 * flow — while allowing a MITM proxy for API inspection. This is what makes
 * the deeper feature-discovery work (what does the app actually send?) possible.
 */
@Suppress("unused")
val myDialogDisableSslPinningPatch = bytecodePatch(
    name = "Disable SSL pinning",
    description = "Removes MyDialog's certificate pin comparison while keeping " +
        "standard TLS validation, so HTTPS traffic can be inspected with a " +
        "user-installed CA.",
    default = true,
) {
    compatibleWith(MYDIALOG_COMPATIBILITY)

    execute {
        CertificatePinCheck.method.returnEarly()
    }
}
