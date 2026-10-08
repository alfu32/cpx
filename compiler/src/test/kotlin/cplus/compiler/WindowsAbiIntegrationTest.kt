package cplus.compiler

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class WindowsAbiIntegrationTest {
    @Test
    fun mingwLlp64PrimitiveAndAggregateAbiRoundTripsUnderWine() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        assumeTrue(commandAvailable("x86_64-w64-mingw32-gcc"), "MinGW x86_64 compiler is not installed")
        assumeTrue(commandAvailable("wine") && commandAvailable("wineboot"), "Wine is not installed")

        val source = """
            pub struct integer_record_t {
                long signed_long;
                unsigned long unsigned_long;
                long long signed_long_long;
                unsigned long long unsigned_long_long;
            };

            pub struct floating_record_t {
                float single_value;
                double double_value;
                long double extended_value;
            };

            pub long round_trip_long(long value) { return value; }
            pub unsigned long round_trip_unsigned_long(unsigned long value) { return value; }
            pub long long round_trip_long_long(long long value) { return value; }
            pub unsigned long long round_trip_unsigned_long_long(unsigned long long value) { return value; }
            pub long double round_trip_long_double(long double value) { return value; }
            pub integer_record_t round_trip_integer_record(integer_record_t value) { return value; }
            pub floating_record_t round_trip_floating_record(floating_record_t value) { return value; }
        """.trimIndent()
        val compilation = CPlusCompiler().compileText(Files.createTempFile("cplus-windows-abi", ".cp"), source)
        assertTrue(compilation.isSuccessful, compilation.diagnostics.joinToString())

        val directory = Files.createTempDirectory("cplus-windows-abi-e2e")
        val header = directory.resolve("abi.h").also { it.writeText(compilation.generatedHeaders.single().text) }
        val generated = directory.resolve("generated.c").also { it.writeText(compilation.generatedUnits.single().text) }
        val caller = directory.resolve("caller.c").also {
            it.writeText(
                """
                    #include <stddef.h>
                    #include "${header.fileName}"

                    _Static_assert(sizeof(long) == 4, "Windows LLP64 long width");
                    _Static_assert(sizeof(long long) == 8, "Windows long long width");
                    _Static_assert(sizeof(struct integer_record_t) == 24, "integer record size");
                    _Static_assert(offsetof(struct integer_record_t, signed_long_long) == 8, "long long offset");
                    _Static_assert(sizeof(long double) == 16 && __LDBL_MANT_DIG__ == 64,
                        "MinGW GNU x87 long double profile");
                    _Static_assert(offsetof(struct floating_record_t, extended_value) == 16,
                        "long double aggregate offset");

                    int main(void) {
                        long signed_long = -2000000000L;
                        unsigned long unsigned_long = 4000000001UL;
                        long long signed_long_long = -((long long)1 << 50);
                        unsigned long long unsigned_long_long = 1ULL << 63;
                        struct integer_record_t integers = {
                            signed_long, unsigned_long, signed_long_long, unsigned_long_long
                        };
                        struct floating_record_t floating = { 1.25f, 2.5, 123456789.125L };

                        if (round_trip_long(signed_long) != signed_long) return 1;
                        if (round_trip_unsigned_long(unsigned_long) != unsigned_long) return 2;
                        if (round_trip_long_long(signed_long_long) != signed_long_long) return 3;
                        if (round_trip_unsigned_long_long(unsigned_long_long) != unsigned_long_long) return 4;
                        if (round_trip_long_double(floating.extended_value) != floating.extended_value) return 5;
                        integers = round_trip_integer_record(integers);
                        if (integers.signed_long != signed_long || integers.unsigned_long != unsigned_long ||
                            integers.signed_long_long != signed_long_long ||
                            integers.unsigned_long_long != unsigned_long_long) return 6;
                        floating = round_trip_floating_record(floating);
                        if (floating.single_value != 1.25f || floating.double_value != 2.5 ||
                            floating.extended_value != 123456789.125L) return 7;
                        return 0;
                    }
                """.trimIndent()
            )
        }

        val executable = directory.resolve("windows-abi.exe")
        val compile = ProcessBuilder(
            "x86_64-w64-mingw32-gcc", "-std=c17", "-Wall", "-Wextra", "-Werror", "-static",
            "-I", directory.toString(), generated.toString(), caller.toString(), "-o", executable.toString()
        ).redirectErrorStream(true).start()
        val compilerOutput = compile.inputStream.bufferedReader().readText()
        assertEquals(0, compile.waitFor(), compilerOutput)

        val prefix = directory.resolve("wine-prefix")
        val log = directory.resolve("wine.log")
        fun wineEnvironment(process: ProcessBuilder): ProcessBuilder = process
            .redirectErrorStream(true)
            .redirectOutput(log.toFile())
            .apply {
                environment()["WINEPREFIX"] = prefix.toString()
                environment()["WINEDLLOVERRIDES"] = "mscoree,mshtml="
                environment()["WINEDEBUG"] = "-all"
            }

        val initialize = wineEnvironment(ProcessBuilder("wineboot", "--init")).start()
        assertTrue(initialize.waitFor(60, TimeUnit.SECONDS), log.readText())
        assertEquals(0, initialize.exitValue(), log.readText())
        val execution = wineEnvironment(ProcessBuilder("wine", executable.toString())).start()
        assertTrue(execution.waitFor(30, TimeUnit.SECONDS), log.readText())
        assertEquals(0, execution.exitValue(), log.readText())
    }

    private fun commandAvailable(command: String): Boolean = runCatching {
        ProcessBuilder(command, "--version").start().waitFor() == 0
    }.getOrDefault(false)
}
