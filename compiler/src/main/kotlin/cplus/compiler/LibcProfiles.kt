package cplus.compiler

import java.nio.file.Files
import java.nio.file.Path

data class LibcHeaderStatus(val header: String, val profile: LibcProfile, val status: String)

object LibcProfileCatalogue {
    private val c17Headers = listOf(
        "assert.h", "complex.h", "ctype.h", "errno.h", "float.h", "limits.h", "locale.h", "math.h",
        "setjmp.h", "signal.h", "stdarg.h", "stdbool.h", "stddef.h", "stdint.h", "stdio.h",
        "stdlib.h", "string.h", "tgmath.h", "time.h", "wchar.h", "wctype.h"
    )

    fun statuses(includeRoot: Path): List<LibcHeaderStatus> = c17Headers.map { header ->
        LibcHeaderStatus(header, LibcProfile.C17, if (Files.isRegularFile(includeRoot.resolve(header))) "delivered" else "planned"
        )
    }

    fun supports(profile: LibcProfile, includeRoot: Path): Boolean = when (profile) {
        LibcProfile.NONE -> true
        LibcProfile.C17 -> statuses(includeRoot).all { it.status == "delivered" }
        LibcProfile.C23 -> false
    }
}
