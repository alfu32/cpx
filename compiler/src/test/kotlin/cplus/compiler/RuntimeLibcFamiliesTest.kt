package cplus.compiler

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue

class RuntimeLibcFamiliesTest {
    @Test
    fun linuxC17FamiliesProvideFormattingClockMathClassificationLocaleAndSignals() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val directory = Files.createTempDirectory("cplus-libc-families-e2e")
        val source = directory.resolve("libc_families_test.c").also {
            Files.writeString(it, """
                #include <ctype.h>
                #include <locale.h>
                #include <math.h>
                #include <signal.h>
                #include <stdio.h>
                #include <time.h>
                static int seen;
                static void handler(int value) { seen = value; }
                int main(void) {
                    char text[32];
                    if (snprintf(text, sizeof(text), "%d:%s", 7, "ok") != 4) return 1;
                    if (text[0] != '7' || text[1] != ':' || text[2] != 'o' || text[3] != 'k' || text[4] != 0) return 2;
                    if (sqrt(9.0) != 3.0 || fabs(-2.5) != 2.5) return 3;
                    if (!isalpha('a') || !isdigit('4') || !isspace(' ') || tolower('A') != 'a') return 4;
                    if (setlocale(LC_ALL, "C") == (char*)0) return 5;
                    if (signal(2, handler) == (signal_handler)-1 || raise(2) != 0 || seen != 2) return 6;
                    if (clock() < 0 || time((time_t*)0) < 0) return 7;
                    return 0;
                }
            """.trimIndent())
        }
        val executable = directory.resolve("libc_families_test")
        val compile = ProcessBuilder(
            "cc", "-std=c17", "-I", root.resolve("libc/include").toString(),
            "-I", root.resolve("runtime/include").toString(), source.toString(),
            root.resolve("runtime/src/stdio.c").toString(),
            root.resolve("runtime/src/format.c").toString(),
            root.resolve("runtime/src/time.c").toString(),
            root.resolve("runtime/src/math.c").toString(),
            root.resolve("runtime/src/ctype.c").toString(),
            root.resolve("runtime/src/locale.c").toString(),
            root.resolve("runtime/src/signal.c").toString(),
            root.resolve("platform/linux/runtime.c").toString(), "-o", executable.toString()
        ).redirectErrorStream(true).start()
        val output = compile.inputStream.bufferedReader().readText()
        assertEquals(0, compile.waitFor(), output)
        assertEquals(0, ProcessBuilder(executable.toString()).redirectErrorStream(true).start().waitFor())
    }
}
