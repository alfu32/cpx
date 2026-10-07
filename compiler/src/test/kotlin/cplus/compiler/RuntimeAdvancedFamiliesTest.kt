package cplus.compiler

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class RuntimeAdvancedFamiliesTest {
    @Test
    fun linuxX8664ProvidesAtomicsUtf8WideConversionsAndSetjmp() {
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val directory = Files.createTempDirectory("cplus-advanced-families-e2e")
        val source = directory.resolve("advanced_families_test.c").also {
            Files.writeString(it, """
                #include <stdatomic.h>
                #include <setjmp.h>
                #include <wchar.h>
                #include <wctype.h>
                static jmp_buf context;
                static void jump_now(void) { longjmp(context, 7); }
                int main(void) {
                    atomic_int counter;
                    wchar_t wide[8];
                    char text[16];
                    atomic_init(&counter, 1);
                    if (atomic_fetch_add(&counter, 2) != 1 || atomic_load(&counter) != 3) return 1;
                    if (mbstowcs(wide, "A\342\202\254", 8) != 2 || wide[0] != 'A' || wide[1] != 0x20ac) return 2;
                    if (wcstombs(text, wide, sizeof(text)) != 4 || text[0] != 'A' || text[1] != (char)0xe2 || text[2] != (char)0x82 || text[3] != (char)0xac) return 3;
                    if (!iswalpha(wide[0]) || !iswdigit('7') || !iswspace(' ')) return 4;
                    if (setjmp(context) == 0) jump_now();
                    return atomic_load(&counter) == 3 ? 0 : 5;
                }
            """.trimIndent())
        }
        val executable = directory.resolve("advanced_families_test")
        val compile = ProcessBuilder(
            "cc", "-std=c17", "-I", root.resolve("libc/include").toString(),
            "-I", root.resolve("runtime/include").toString(), source.toString(),
            root.resolve("runtime/src/wide.c").toString(),
            root.resolve("runtime/src/wctype.c").toString(),
            root.resolve("runtime/src/setjmp-x86_64.S").toString(), "-o", executable.toString()
        ).redirectErrorStream(true).start()
        val output = compile.inputStream.bufferedReader().readText()
        assertEquals(0, compile.waitFor(), output)
        assertEquals(0, ProcessBuilder(executable.toString()).redirectErrorStream(true).start().waitFor())
    }
}
