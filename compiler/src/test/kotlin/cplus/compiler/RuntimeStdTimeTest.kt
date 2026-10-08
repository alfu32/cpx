package cplus.compiler

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class RuntimeStdTimeTest {
    @Test
    fun cplusStdTimeExposesClocksAndCheckedNanosecondDurations() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val manifestPath = SdkManifestLocator.defaultManifestPath()
        val manifest = requireNotNull(SdkManifestLoader.load(manifestPath).manifest)
        val target = TargetInfo(targetTriple = "linux-x86_64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val plan = requireNotNull(RuntimeLinker.plan(resolution, target).plan)
        val root = manifestPath.toAbsolutePath().normalize().parent!!.parent!!
        val directory = Files.createTempDirectory("cplus-std-time")
        val mainSource = directory.resolve("main.cp").also {
            Files.writeString(it, """
                import {
                    std_calendar_time_t,
                    std_duration_t,
                    std_time_duration_add,
                    std_time_duration_compare,
                    std_time_duration_from_milliseconds,
                    std_time_duration_from_nanoseconds,
                    std_time_duration_from_seconds,
                    std_time_duration_get_nanoseconds,
                    std_time_duration_subtract,
                    std_time_calendar_from_unix_timestamp,
                    std_time_calendar_to_unix_timestamp,
                    std_time_monotonic_nanoseconds,
                    std_time_process_cpu_nanoseconds,
                    std_time_status_invalid_argument,
                    std_time_status_overflow,
                    std_time_wall_nanoseconds
                } from std.time;

                int main() {
                    long long wall = std_time_wall_nanoseconds();
                    long long monotonic_before = std_time_monotonic_nanoseconds();
                    long long monotonic_after = std_time_monotonic_nanoseconds();
                    long long cpu = std_time_process_cpu_nanoseconds();
                    std_duration_t seconds;
                    std_duration_t milliseconds;
                    std_duration_t sum;
                    std_duration_t difference;
                    std_duration_t negative_seconds;
                    std_duration_t maximum;
                    std_duration_t one;
                    std_duration_t minimum;
                    std_duration_t unchanged;
                    std_calendar_time_t epoch;
                    std_calendar_time_t leap_day;
                    std_calendar_time_t pre_epoch;
                    std_calendar_time_t invalid_calendar;
                    std_calendar_time_t unchanged_calendar;
                    std_calendar_time_t maximum_calendar;
                    std_calendar_time_t minimum_calendar;
                    long long nanoseconds = 0;
                    long long unix_seconds = 77;
                    unsigned int fractional_nanoseconds = 88;
                    int ordering = 0;
                    if (wall < 0 || monotonic_before < 0 || monotonic_after < monotonic_before || cpu < 0) return 1;
                    if (std_time_duration_from_seconds(3, &seconds) != 0 || seconds.nanoseconds != 3000000000) return 2;
                    if (std_time_duration_from_milliseconds(-2, &milliseconds) != 0 ||
                        milliseconds.nanoseconds != -2000000) return 3;
                    if (std_time_duration_add(&seconds, &milliseconds, &sum) != 0 ||
                        sum.nanoseconds != 2998000000) return 4;
                    if (std_time_duration_subtract(&seconds, &milliseconds, &difference) != 0 ||
                        difference.nanoseconds != 3002000000) return 5;
                    if (std_time_duration_from_seconds(-2, &negative_seconds) != 0 ||
                        negative_seconds.nanoseconds != -2000000000) return 13;
                    if (std_time_duration_compare(&seconds, &milliseconds, &ordering) != 0 || ordering != 1) return 6;
                    if (std_time_duration_get_nanoseconds(&difference, &nanoseconds) != 0 ||
                        nanoseconds != 3002000000) return 7;
                    if (std_time_duration_from_nanoseconds(9223372036854775807, &maximum) != 0 ||
                        std_time_duration_from_nanoseconds(1, &one) != 0 ||
                        std_time_duration_from_nanoseconds((-9223372036854775807 - 1), &minimum) != 0) return 8;
                    unchanged.nanoseconds = 77;
                    if (std_time_duration_from_seconds(9223372036854775807, &unchanged) !=
                            std_time_status_overflow() || unchanged.nanoseconds != 77) return 9;
                    if (std_time_duration_from_milliseconds(9223372036855, &unchanged) !=
                            std_time_status_overflow() || unchanged.nanoseconds != 77) return 17;
                    if (std_time_duration_add(&maximum, &one, &unchanged) != std_time_status_overflow() ||
                        unchanged.nanoseconds != 77) return 10;
                    if (std_time_duration_subtract(&minimum, &one, &unchanged) != std_time_status_overflow() ||
                        unchanged.nanoseconds != 77) return 11;
                    if (std_time_duration_from_nanoseconds(1, (std_duration_t*)0) !=
                            std_time_status_invalid_argument() ||
                        std_time_duration_add((std_duration_t*)0, &one, &unchanged) !=
                            std_time_status_invalid_argument() ||
                        std_time_duration_compare(&one, &one, (int*)0) !=
                            std_time_status_invalid_argument()) return 12;
                    if (std_time_calendar_from_unix_timestamp(0, 123456789, &epoch) != 0 ||
                        epoch.year != 1970 || epoch.month != 1 || epoch.day != 1 || epoch.hour != 0 ||
                        epoch.minute != 0 || epoch.second != 0 || epoch.nanosecond != 123456789) return 18;
                    if (std_time_calendar_to_unix_timestamp(&epoch, &unix_seconds, &fractional_nanoseconds) != 0 ||
                        unix_seconds != 0 || fractional_nanoseconds != 123456789) return 19;
                    if (std_time_calendar_from_unix_timestamp(951782400, 42, &leap_day) != 0 ||
                        leap_day.year != 2000 || leap_day.month != 2 || leap_day.day != 29 ||
                        leap_day.hour != 0 || leap_day.minute != 0 || leap_day.second != 0 ||
                        leap_day.nanosecond != 42) return 20;
                    if (std_time_calendar_to_unix_timestamp(&leap_day, &unix_seconds, &fractional_nanoseconds) != 0 ||
                        unix_seconds != 951782400 || fractional_nanoseconds != 42) return 21;
                    if (std_time_calendar_from_unix_timestamp(-1, 999999999, &pre_epoch) != 0 ||
                        pre_epoch.year != 1969 || pre_epoch.month != 12 || pre_epoch.day != 31 ||
                        pre_epoch.hour != 23 || pre_epoch.minute != 59 || pre_epoch.second != 59 ||
                        pre_epoch.nanosecond != 999999999) return 22;
                    if (std_time_calendar_to_unix_timestamp(&pre_epoch, &unix_seconds, &fractional_nanoseconds) != 0 ||
                        unix_seconds != -1 || fractional_nanoseconds != 999999999) return 23;
                    invalid_calendar.year = 0;
                    invalid_calendar.month = 2;
                    invalid_calendar.day = 29;
                    invalid_calendar.hour = 12;
                    invalid_calendar.minute = 34;
                    invalid_calendar.second = 56;
                    invalid_calendar.nanosecond = 789;
                    if (std_time_calendar_to_unix_timestamp(&invalid_calendar, &unix_seconds,
                            &fractional_nanoseconds) != 0 ||
                        std_time_calendar_from_unix_timestamp(unix_seconds, fractional_nanoseconds,
                            &unchanged_calendar) != 0 || unchanged_calendar.year != 0 ||
                        unchanged_calendar.month != 2 || unchanged_calendar.day != 29 ||
                        unchanged_calendar.hour != 12 || unchanged_calendar.minute != 34 ||
                        unchanged_calendar.second != 56 || unchanged_calendar.nanosecond != 789) return 30;
                    if (std_time_calendar_from_unix_timestamp(9223372036854775807, 999999999,
                            &maximum_calendar) != 0 ||
                        std_time_calendar_to_unix_timestamp(&maximum_calendar, &unix_seconds,
                            &fractional_nanoseconds) != 0 || unix_seconds != 9223372036854775807 ||
                        fractional_nanoseconds != 999999999) return 24;
                    if (std_time_calendar_from_unix_timestamp((-9223372036854775807 - 1), 0,
                            &minimum_calendar) != 0 ||
                        std_time_calendar_to_unix_timestamp(&minimum_calendar, &unix_seconds,
                            &fractional_nanoseconds) != 0 || unix_seconds != (-9223372036854775807 - 1) ||
                        fractional_nanoseconds != 0) return 25;
                    invalid_calendar = leap_day;
                    invalid_calendar.year = 1900;
                    unix_seconds = 77;
                    fractional_nanoseconds = 88;
                    if (std_time_calendar_to_unix_timestamp(&invalid_calendar, &unix_seconds,
                            &fractional_nanoseconds) != std_time_status_invalid_argument() ||
                        unix_seconds != 77 || fractional_nanoseconds != 88) return 26;
                    invalid_calendar = leap_day;
                    invalid_calendar.hour = 24;
                    if (std_time_calendar_to_unix_timestamp(&invalid_calendar, &unix_seconds,
                            &fractional_nanoseconds) != std_time_status_invalid_argument()) return 27;
                    invalid_calendar.year = 300000000000;
                    invalid_calendar.month = 1;
                    invalid_calendar.day = 1;
                    invalid_calendar.hour = 0;
                    invalid_calendar.minute = 0;
                    invalid_calendar.second = 0;
                    invalid_calendar.nanosecond = 0;
                    unix_seconds = 77;
                    fractional_nanoseconds = 88;
                    if (std_time_calendar_to_unix_timestamp(&invalid_calendar, &unix_seconds,
                            &fractional_nanoseconds) != std_time_status_overflow() ||
                        unix_seconds != 77 || fractional_nanoseconds != 88) return 31;
                    unchanged_calendar.year = 42;
                    if (std_time_calendar_from_unix_timestamp(0, 1000000000, &unchanged_calendar) !=
                            std_time_status_invalid_argument() || unchanged_calendar.year != 42) return 28;
                    if (std_time_calendar_to_unix_timestamp((const std_calendar_time_t*)0, &unix_seconds,
                            &fractional_nanoseconds) != std_time_status_invalid_argument() ||
                        std_time_calendar_to_unix_timestamp(&epoch, (long long*)0,
                            &fractional_nanoseconds) != std_time_status_invalid_argument()) return 29;
                    return 0;
                }
            """.trimIndent())
        }
        val generatedC = directory.resolve("std-time.c")
        val executable = directory.resolve("std-time")
        try {
            val compilation = CPlusCompiler().compile(
                CompileRequest(listOf(root.resolve("std/src/time.cp"), mainSource), target)
            )
            assertTrue(compilation.isSuccessful, compilation.diagnostics.joinToString())
            assertEquals(1, compilation.generatedUnits.size)
            Files.writeString(generatedC, compilation.generatedUnits.single().text)

            val link = LinkDriver.link(LinkRequest(generatedC, executable, target, resolution), plan)
            assertTrue(link.isSuccessful, link.output)
            val undefinedSymbols = ProcessBuilder("nm", "-u", executable.toString()).start()
            val undefinedOutput = undefinedSymbols.inputStream.bufferedReader().readText()
            assertEquals(0, undefinedSymbols.waitFor(), undefinedOutput)
            assertTrue(undefinedOutput.isBlank(), undefinedOutput)
            assertEquals(0, ProcessBuilder(executable.toString()).start().waitFor())
        } finally {
            Files.deleteIfExists(executable)
            Files.deleteIfExists(generatedC)
            Files.deleteIfExists(mainSource)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun cTimeAndClockMapNegativePalResultsToMinusOne() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val manifestPath = SdkManifestLocator.defaultManifestPath()
        val root = manifestPath.toAbsolutePath().normalize().parent!!.parent!!
        val directory = Files.createTempDirectory("cplus-time-errors")
        val source = directory.resolve("time_error_test.c").also {
            Files.writeString(it, """
                #include "cplus_platform.h"

                long long time(long long* result);
                long long clock(void);

                long long platform_clock_wall_nanoseconds(void) { return CPLUS_PAL_IO_ERROR; }
                long long platform_clock_process_cpu_nanoseconds(void) { return CPLUS_PAL_NOT_FOUND; }
                long long platform_clock_monotonic_nanoseconds(void) { return CPLUS_PAL_UNSUPPORTED; }

                int main(void) {
                    long long stored = 0;
                    if (time(&stored) != -1 || stored != -1) return 1;
                    if (time((long long*)0) != -1) return 2;
                    if (clock() != -1) return 3;
                    return 0;
                }
            """.trimIndent())
        }
        val executable = directory.resolve("time_error_test")
        try {
            val compile = ProcessBuilder(
                "cc", "-std=c17", "-Wall", "-Wextra", "-Werror", "-fno-builtin",
                "-I", root.resolve("runtime/include").toString(), source.toString(),
                root.resolve("runtime/src/time.c").toString(), "-o", executable.toString()
            ).redirectErrorStream(true).start()
            val compileOutput = compile.inputStream.bufferedReader().readText()
            assertEquals(0, compile.waitFor(), compileOutput)
            val process = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.waitFor(), output)
        } finally {
            Files.deleteIfExists(executable)
            Files.deleteIfExists(source)
            Files.deleteIfExists(directory)
        }
    }
}
