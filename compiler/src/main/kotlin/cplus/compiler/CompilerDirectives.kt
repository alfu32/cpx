package cplus.compiler

import cplus.comptime.ComptimeTargetInfo
import cplus.core.Diagnostic
import cplus.core.DiagnosticSeverity

object CompilerDirectives {
    fun assertion(condition: Boolean, message: String): Diagnostic? = if (condition) null else Diagnostic(
        DiagnosticSeverity.ERROR,
        "compile-time assertion failed: $message",
        null,
        "CPX601"
    )

    fun error(message: String): Diagnostic = Diagnostic(DiagnosticSeverity.ERROR, message, null, "CPX602")

    fun requireFeature(target: ComptimeTargetInfo, feature: String): Diagnostic? =
        assertion(target.hasFeature(feature), "target does not provide feature '$feature'")

    fun requireIntrinsic(target: ComptimeTargetInfo, intrinsic: String): Diagnostic? =
        assertion(target.hasIntrinsic(intrinsic), "target does not provide intrinsic '$intrinsic'")
}

data class IntrinsicCallContract(val name: String, val argumentCount: Int)

object IntrinsicCallValidator {
    fun validate(call: IntrinsicCallContract, definition: IntrinsicDefinition): String? = when {
        call.name != definition.name -> "intrinsic name mismatch"
        call.argumentCount != definition.arity -> "intrinsic '${call.name}' expects ${definition.arity} argument(s), got ${call.argumentCount}"
        else -> null
    }
}
