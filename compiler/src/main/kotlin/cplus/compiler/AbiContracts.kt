package cplus.compiler

import cplus.semantic.AbiKind

data class AbiAttributes(
    val abi: AbiKind = AbiKind.C,
    val library: String? = null,
    val linkName: String? = null,
    val exportName: String? = null,
    val isWeak: Boolean = false,
    val isNoReturn: Boolean = false
)

data class LinkageIdentity(
    val sourceName: String,
    val externalName: String = sourceName,
    val abi: AbiKind = AbiKind.C,
    val library: String? = null,
    val exported: Boolean = false,
    val weak: Boolean = false
)

object AbiContractValidator {
    fun validate(attributes: AbiAttributes, target: TargetAbiDescriptor): List<String> = buildList {
        if (attributes.abi.name.lowercase() !in target.supportedAbis) {
            add("ABI '${attributes.abi.name.lowercase()}' is not supported by target '${target.targetTriple}'")
        }
        if (attributes.isWeak && attributes.abi == AbiKind.INTRINSIC) {
            add("intrinsic declarations cannot be weak")
        }
        if (attributes.exportName != null && attributes.exportName.isBlank()) {
            add("export name must not be empty")
        }
    }

    fun linkage(sourceName: String, attributes: AbiAttributes): LinkageIdentity = LinkageIdentity(
        sourceName = sourceName,
        externalName = attributes.exportName ?: attributes.linkName ?: sourceName,
        abi = attributes.abi,
        library = attributes.library,
        exported = attributes.exportName != null,
        weak = attributes.isWeak
    )
}
