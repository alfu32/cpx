package cplus.compiler

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class RuntimeAllocatorTest {
    @Test
    fun linuxPageAllocatorSupportsZeroedAlignedResizeAndRelease() {
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val directory = Files.createTempDirectory("cplus-allocator-e2e")
        val source = directory.resolve("allocator_test.c").also {
            Files.writeString(it, """
                #include <stddef.h>
                extern void* __cplus_alloc(unsigned long long size);
                extern void* __cplus_calloc(unsigned long long count, unsigned long long size);
                extern void* __cplus_realloc(void* value, unsigned long long size);
                extern void* __cplus_alloc_aligned(unsigned long long alignment, unsigned long long size);
                extern void __cplus_free(void* value);
                int main(void) {
                    unsigned char* value = (unsigned char*)__cplus_alloc(32);
                    unsigned char* zeroed = (unsigned char*)__cplus_calloc(8, 4);
                    unsigned char* aligned = (unsigned char*)__cplus_alloc_aligned(64, 17);
                    unsigned long long index;
                    if (!value || !zeroed || !aligned || ((unsigned long long)aligned % 64ULL) != 0) return 1;
                    for (index = 0; index < 32; index++) value[index] = (unsigned char)index;
                    for (index = 0; index < 32; index++) if (zeroed[index] != 0) return 2;
                    value = (unsigned char*)__cplus_realloc(value, 128);
                    if (!value) return 3;
                    for (index = 0; index < 32; index++) if (value[index] != (unsigned char)index) return 4;
                    __cplus_free(value);
                    __cplus_free(zeroed);
                    __cplus_free(aligned);
                    return 0;
                }
            """.trimIndent())
        }
        val executable = directory.resolve("allocator_test")
        val compile = ProcessBuilder(
            "cc", "-std=c17", "-I", root.resolve("runtime/include").toString(),
            source.toString(), root.resolve("runtime/src/allocator.c").toString(),
            root.resolve("runtime/src/memory.c").toString(),
            root.resolve("platform/linux/runtime.c").toString(), "-o", executable.toString()
        ).redirectErrorStream(true).start()
        val output = compile.inputStream.bufferedReader().readText()
        assertEquals(0, compile.waitFor(), output)
        assertEquals(0, ProcessBuilder(executable.toString()).redirectErrorStream(true).start().waitFor())
    }
}
