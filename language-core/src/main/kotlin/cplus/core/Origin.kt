package cplus.core

sealed interface Origin {
    val primaryRange: SourceRange?

    data class Direct(
        override val primaryRange: SourceRange
    ) : Origin

    data class Generated(
        val cause: Origin,
        override val primaryRange: SourceRange? = cause.primaryRange
    ) : Origin

    data class Expansion(
        val definition: Origin,
        val invocation: Origin,
        val parent: Origin?,
        val key: String,
        override val primaryRange: SourceRange? = invocation.primaryRange
    ) : Origin

    data class Synthetic(
        val parent: Origin?,
        override val primaryRange: SourceRange? = parent?.primaryRange
    ) : Origin
}
