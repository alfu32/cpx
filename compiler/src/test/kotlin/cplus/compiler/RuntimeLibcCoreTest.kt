package cplus.compiler

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class RuntimeLibcCoreTest {
    @Test
    fun linuxC17CoreProvidesMemoryStringsAndConversionsWithoutHostLibcCalls() {
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
                    strcat(text, "cd");
                    if (strcmp(text, "abcd") != 0 || strncmp(text, "ab", 2) != 0) return 2;
                    memmove(overlap + 1, overlap, 3);
                    if (memcmp(overlap, "aabc", 4) != 0 || strlen(text) != 4) return 3;
                    if (strtol("0x2a", &end, 0) != 42 || *end != 0) return 4;
                    if (strtod("3.5", &end) != 3.5 || *end != 0) return 5;
                    if (__cplus_set_errno_from_pal(-3) != -1 || errno != ENOENT) return 6;
                    free(memory);
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
