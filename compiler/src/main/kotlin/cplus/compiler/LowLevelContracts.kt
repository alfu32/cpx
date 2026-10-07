package cplus.compiler

data class StorageAttributes(
    val threadLocal: Boolean = false,
    val volatile: Boolean = false,
    val alignment: Int? = null,
    val packed: Boolean = false,
    val section: String? = null,
    val used: Boolean = false,
    val noReturn: Boolean = false
)

object StorageContractValidator {
    fun validate(attributes: StorageAttributes, descriptor: TargetAbiDescriptor): List<String> = buildList {
        attributes.alignment?.let { alignment ->
            if (alignment <= 0 || alignment and (alignment - 1) != 0) add("alignment must be a positive power of two")
            if (alignment > descriptor.stackAlignment * 16) add("alignment $alignment exceeds target limit")
        }
        if (attributes.threadLocal && descriptor.tlsModel.isBlank()) add("target does not describe TLS")
        if (attributes.section?.contains('"') == true) add("section name contains an invalid quote")
    }
}
