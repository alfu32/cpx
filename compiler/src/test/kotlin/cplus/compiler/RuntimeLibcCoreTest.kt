package cplus.compiler

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue

class RuntimeLibcCoreTest {
    @Test
    fun linuxC17CoreProvidesMemoryStringsAndConversionsWithoutHostLibcCalls() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val directory = Files.createTempDirectory("cplus-libc-core-e2e")
        val source = directory.resolve("libc_core_test.c").also {
            Files.writeString(it, """
                #include <stddef.h>
                #include <errno.h>
                #include <stdlib.h>
                #include <string.h>
                extern int __cplus_set_errno_from_pal(long result);
                int main(void) {
                    char text[32] = "ab";
                    char overlap[8] = "abcd";
                    char* end;
                    void* memory = malloc(16);
                    if (!memory) return 1;
                    unsigned char* zeroed = (unsigned char*)calloc(8, 8);
                    void* aligned = aligned_alloc(64, 64);
                    unsigned long long index;
                    if (!zeroed || !aligned || ((unsigned long long)aligned % 64ULL) != 0) return 7;
                    for (index = 0; index < 64; index++) {
                        if (zeroed[index] != 0) return 8;
                        zeroed[index] = (unsigned char)index;
                    }
                    zeroed = (unsigned char*)realloc(zeroed, 128);
                    if (!zeroed) return 9;
                    for (index = 0; index < 64; index++) if (zeroed[index] != (unsigned char)index) return 10;
                    errno = 0;
                    if (calloc((size_t)-1, 2) != 0 || errno != ENOMEM) return 11;
                    strcat(text, "cd");
                    if (strcmp(text, "abcd") != 0 || strncmp(text, "ab", 2) != 0) return 2;
                    memmove(overlap + 1, overlap, 3);
                    if (memcmp(overlap, "aabc", 4) != 0 || strlen(text) != 4) return 3;
                    if (strtol("0x2a", &end, 0) != 42 || *end != 0) return 4;
                    if (strtod("3.5", &end) != 3.5 || *end != 0) return 5;
                    if (__cplus_set_errno_from_pal(-3) != -1 || errno != ENOENT) return 6;
                    free(memory);
                    free(zeroed);
                    free(aligned);
                    return 0;
                }
            """.trimIndent())
        }
        val executable = directory.resolve("libc_core_test")
        val compile = ProcessBuilder(
            "cc", "-std=c17", "-I", root.resolve("libc/include").toString(),
            "-I", root.resolve("runtime/include").toString(), source.toString(),
            root.resolve("runtime/src/libc_core.c").toString(),
            root.resolve("runtime/src/allocator.c").toString(),
            root.resolve("runtime/src/memory.c").toString(),
            root.resolve("platform/linux/runtime.c").toString(), "-o", executable.toString()
        ).redirectErrorStream(true).start()
        val output = compile.inputStream.bufferedReader().readText()
        assertEquals(0, compile.waitFor(), output)
        assertEquals(0, ProcessBuilder(executable.toString()).redirectErrorStream(true).start().waitFor())
    }
}
