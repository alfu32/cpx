package cplus.compiler

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue

class RuntimeAllocatorTest {
    @Test
    fun linuxPageAllocatorSupportsZeroedAlignedResizeAndRelease() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val directory = Files.createTempDirectory("cplus-allocator-e2e")
        val source = directory.resolve("allocator_test.c").also {
            Files.writeString(it, """
                #include "cplus_platform.h"
                extern void* __cplus_alloc(unsigned long long size);
                extern void* __cplus_calloc(unsigned long long count, unsigned long long size);
                extern void* __cplus_realloc(void* value, unsigned long long size);
                extern void* __cplus_alloc_aligned(unsigned long long alignment, unsigned long long size);
                extern void __cplus_free(void* value);
                int main(void) {
                    unsigned char* value = (unsigned char*)__cplus_alloc(32);
                    unsigned char* zeroed = (unsigned char*)__cplus_calloc(8, 4);
                    unsigned char* aligned = (unsigned char*)__cplus_alloc_aligned(64, 17);
                    unsigned char* zero_size = (unsigned char*)__cplus_alloc(0);
                    void* page;
                    unsigned long long index;
                    if (!value || !zeroed || !aligned || !zero_size || ((unsigned long long)aligned % 64ULL) != 0) return 1;
                    if (platform_page_allocate(0) != (void*)0 || platform_page_allocate(~0ULL) != (void*)0) return 2;
                    if (platform_page_release((void*)0, 0) != CPLUS_PAL_INVALID_ARGUMENT) return 3;
                    page = platform_page_allocate(1);
                    if (!page || platform_page_release(page, 0) != CPLUS_PAL_INVALID_ARGUMENT) return 4;
                    if (platform_page_release(page, 1) != 0) return 5;
                    if (__cplus_alloc_aligned(3, 32) != (void*)0 ||
                        __cplus_alloc_aligned(sizeof(void*) / 2, 32) != (void*)0 ||
                        __cplus_alloc_aligned(1ULL << 63, 1ULL << 63) != (void*)0) return 6;
                    if (__cplus_alloc(~0ULL) != (void*)0 || __cplus_calloc(~0ULL, 2) != (void*)0) return 7;
                    for (index = 0; index < 32; index++) value[index] = (unsigned char)index;
                    for (index = 0; index < 32; index++) if (zeroed[index] != 0) return 8;
                    value = (unsigned char*)__cplus_realloc(value, 128);
                    if (!value) return 9;
                    for (index = 0; index < 32; index++) if (value[index] != (unsigned char)index) return 10;
                    __cplus_free(value);
                    __cplus_free(zeroed);
                    __cplus_free(aligned);
                    __cplus_free(zero_size);
                    zero_size = (unsigned char*)__cplus_alloc(1);
                    if (!zero_size || __cplus_realloc(zero_size, 0) != (void*)0) return 11;
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
