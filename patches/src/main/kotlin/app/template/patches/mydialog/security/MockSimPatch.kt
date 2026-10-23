package app.template.patches.mydialog.security

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.MYDIALOG_COMPATIBILITY
import app.template.patches.shared.clearBody
import app.template.patches.shared.ensureRegisters

/**
 * The JS bundle refuses to continue without a SIM: `NativeUtilsModule.getIMSIList`
 * reads the subscriber id of both SIM slots, and on a SIM-less device (emulator,
 * Wi-Fi tablet) the error path ends in an "App Error: NO IMSI" dialog that exits
 * the app.
 *
 * This patch replaces the method with a constant answer — a Dialog Sri Lanka
 * MCC/MNC prefix ("41303", the same 5-character prefix the original derives from
 * the real IMSI via `substring(0, 5)`). The success callback shape is preserved
 * (an array of prefixes), so JS behaves exactly as with a SIM.
 *
 * OFF by default: on a phone with a real SIM the real IMSI is the correct answer,
 * and a mock would corrupt connection detection. Enable it for emulator/SIM-less
 * testing (`mydbuild` does this when MYD_MOCK_SIM=1).
 */
@Suppress("unused")
val myDialogMockSimPatch = bytecodePatch(
    name = "Mock SIM identifiers",
    description = "Answers the app's IMSI lookup with a Dialog (41303) prefix on " +
        "SIM-less devices so the app proceeds past 'App Error: NO IMSI'. " +
        "Off by default; harmless to enable, but the real IMSI is preferred.",
    default = false,
) {
    compatibleWith(MYDIALOG_COMPATIBILITY)

    execute {
        val m = app.morphe.patcher.Fingerprint(
            definingClass = "Lnet/omobio/dialogsc/utils/NativeUtilsModule;",
            name = "getIMSIList",
            returnType = "V",
            parameters = listOf(
                "Lcom/facebook/react/bridge/Callback;",
                "Lcom/facebook/react/bridge/Callback;",
            ),
        ).method
        // Instance method: p0=this, p1=success, p2=error. The original body keeps
        // enough local registers, so v0/v1 are valid locals after clearBody().
        m.clearBody()
        m.addInstructions(
            0,
            """
            new-instance v0, Lcom/facebook/react/bridge/WritableNativeArray;
            invoke-direct {v0}, Lcom/facebook/react/bridge/WritableNativeArray;-><init>()V
            const-string v1, "41303"
            invoke-virtual {v0, v1}, Lcom/facebook/react/bridge/WritableNativeArray;->pushString(Ljava/lang/String;)V
            const/4 v1, 0x1
            new-array v1, v1, [Ljava/lang/Object;
            const/4 v2, 0x0
            aput-object v0, v1, v2
            invoke-interface {p1, v1}, Lcom/facebook/react/bridge/Callback;->invoke([Ljava/lang/Object;)V
            return-void
            """.trimIndent(),
        )
    }
}
