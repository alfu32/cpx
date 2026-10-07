package cplus.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibcProfileTest {
    @Test
    fun c17HeaderSurfaceIsDeliveredAndC23RemainsAdditive() {
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val include = root.resolve("libc/include")
        val profile = CHeaderProfileGenerator.inspect(include)
        assertEquals(LibcProfile.C17, profile.profile)
        assertTrue(LibcProfileCatalogue.supports(LibcProfile.C17, include))
        assertTrue(profile.headers.containsAll(listOf("stddef.h", "stdint.h", "stdio.h", "string.h")))
        assertTrue(!LibcProfileCatalogue.supports(LibcProfile.C23, include))
    }
}
