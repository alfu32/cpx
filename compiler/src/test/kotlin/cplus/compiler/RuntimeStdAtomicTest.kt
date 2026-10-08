package cplus.compiler

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class RuntimeStdAtomicTest {
    @Test
    fun cplusAtomicFacadeHonorsOrdersRmwCasFenceAndWaitWake() {
        val isWindows = System.getProperty("os.name").contains("windows", ignoreCase = true)
        assumeTrue(isWindows || System.getProperty("os.name").contains("linux", ignoreCase = true))
        val manifestPath = SdkManifestLocator.defaultManifestPath()
        val manifest = requireNotNull(SdkManifestLoader.load(manifestPath).manifest)
        val target = TargetInfo(targetTriple = if (isWindows) "windows-x86_64" else "linux-x86_64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val plan = requireNotNull(RuntimeLinker.plan(resolution, target).plan)
        val root = manifestPath.toAbsolutePath().normalize().parent!!.parent!!
        val directory = Files.createTempDirectory("cplus-std-atomic")
        val mainSource = directory.resolve("main.cp").also {
            Files.writeString(it, """
                import {
                    std_atomic_int_t,
                    std_atomic_compare_exchange_int,
                    std_atomic_exchange_int,
                    std_atomic_fetch_add_int,
                    std_atomic_fetch_and_int,
                    std_atomic_fetch_or_int,
                    std_atomic_fetch_sub_int,
                    std_atomic_fetch_xor_int,
                    std_atomic_init_int,
                    std_atomic_load_int,
                    std_atomic_store_int,
                    std_atomic_thread_fence,
                    std_atomic_wait_int,
                    std_atomic_wake_int,
                    std_memory_order_t,
                    STD_MEMORY_ORDER_RELAXED,
                    STD_MEMORY_ORDER_CONSUME,
                    STD_MEMORY_ORDER_ACQUIRE,
                    STD_MEMORY_ORDER_RELEASE,
                    STD_MEMORY_ORDER_ACQ_REL,
                    STD_MEMORY_ORDER_SEQ_CST
                } from std.atomic;
                import {
                    std_thread_create,
                    std_thread_join,
                    std_thread_yield
                } from std.thread;

                static std_atomic_int_t wait_flag;
                static std_atomic_int_t wait_ready;

                static void* increment_worker(void* context) {
                    std_atomic_int_t* counter = (std_atomic_int_t*)context;
                    int previous;
                    int index;
                    for (index = 0; index < 2000; index++) {
                        if (std_atomic_fetch_add_int(
                            counter, 1, STD_MEMORY_ORDER_RELAXED, &previous) != 0) return (void*)1;
                    }
                    return (void*)0;
                }

                static void* atomic_waiter(void* context) {
                    int previous;
                    int value;
                    if (std_atomic_fetch_add_int(
                        &wait_ready, 1, STD_MEMORY_ORDER_RELEASE, &previous) != 0) return (void*)1;
                    if (std_atomic_wait_int(&wait_flag, 0, STD_MEMORY_ORDER_ACQUIRE) != 0) return (void*)2;
                    if (std_atomic_load_int(&wait_flag, STD_MEMORY_ORDER_ACQUIRE, &value) != 0 || value != 1) {
                        return (void*)3;
                    }
                    return (void*)0;
                }

                int main() {
                    std_atomic_int_t counter;
                    long long workers[4];
                    long long waiter;
                    void* thread_result;
                    int previous;
                    int expected;
                    int exchanged;
                    int value;
                    int index;
                    std_atomic_int_t* misaligned;
                    if (std_atomic_init_int((std_atomic_int_t*)0, 1) != -2 ||
                        std_atomic_init_int(&counter, 2) != 0 ||
                        std_atomic_load_int(&counter, STD_MEMORY_ORDER_RELAXED, &value) != 0 || value != 2) return 1;
                    if (std_atomic_load_int(&counter, STD_MEMORY_ORDER_CONSUME, &value) != 0 || value != 2 ||
                        std_atomic_store_int(&counter, 2, STD_MEMORY_ORDER_RELAXED) != 0 ||
                        std_atomic_store_int(&counter, 2, STD_MEMORY_ORDER_SEQ_CST) != 0) return 23;
                    value = 77;
                    if (std_atomic_load_int(&counter, STD_MEMORY_ORDER_RELEASE, &value) != -2 || value != 77 ||
                        std_atomic_load_int(&counter, STD_MEMORY_ORDER_RELAXED, (int*)0) != -2 ||
                        std_atomic_store_int(&counter, 12, STD_MEMORY_ORDER_ACQUIRE) != -2) return 2;
                    misaligned = (std_atomic_int_t*)((unsigned char*)&counter + 1);
                    value = 77;
                    if (std_atomic_load_int(misaligned, STD_MEMORY_ORDER_RELAXED, &value) != -2 || value != 77 ||
                        std_atomic_load_int(&counter, STD_MEMORY_ORDER_RELAXED, &value) != 0 || value != 2) return 20;
                    if (std_atomic_store_int(&counter, 3, STD_MEMORY_ORDER_RELEASE) != 0 ||
                        std_atomic_load_int(&counter, STD_MEMORY_ORDER_ACQUIRE, &value) != 0 || value != 3) return 3;
                    if (std_atomic_exchange_int(&counter, 4, STD_MEMORY_ORDER_ACQ_REL, &previous) != 0 || previous != 3) return 4;
                    expected = 4;
                    exchanged = -1;
                    if (std_atomic_compare_exchange_int(
                        &counter, &expected, 7, STD_MEMORY_ORDER_SEQ_CST, &exchanged) != 0 ||
                        !exchanged || expected != 4) return 5;
                    expected = 4;
                    exchanged = -1;
                    if (std_atomic_compare_exchange_int(
                        &counter, &expected, 9, STD_MEMORY_ORDER_ACQUIRE, &exchanged) != 0 ||
                        exchanged || expected != 7) return 6;
                    if (std_atomic_fetch_add_int(&counter, 2, STD_MEMORY_ORDER_RELAXED, &previous) != 0 || previous != 7 ||
                        std_atomic_fetch_sub_int(&counter, 1, STD_MEMORY_ORDER_RELEASE, &previous) != 0 || previous != 9 ||
                        std_atomic_fetch_and_int(&counter, 14, STD_MEMORY_ORDER_ACQUIRE, &previous) != 0 || previous != 8 ||
                        std_atomic_fetch_or_int(&counter, 4, STD_MEMORY_ORDER_CONSUME, &previous) != 0 || previous != 8 ||
                        std_atomic_fetch_xor_int(&counter, 3, STD_MEMORY_ORDER_SEQ_CST, &previous) != 0 || previous != 12) return 7;
                    if (std_atomic_load_int(&counter, STD_MEMORY_ORDER_SEQ_CST, &value) != 0 || value != 15) return 8;
                    expected = 15;
                    exchanged = -1;
                    if (std_atomic_compare_exchange_int(
                        &counter, &expected, 18, STD_MEMORY_ORDER_RELEASE, &exchanged) != 0 ||
                        !exchanged || expected != 15 ||
                        std_atomic_load_int(&counter, STD_MEMORY_ORDER_ACQUIRE, &value) != 0 || value != 18) return 21;
                    expected = 18;
                    exchanged = 55;
                    if (std_atomic_compare_exchange_int(
                        &counter, &expected, 19, (std_memory_order_t)99, &exchanged) != -2 ||
                        expected != 18 || exchanged != 55) return 22;
                    if (std_atomic_thread_fence(STD_MEMORY_ORDER_RELAXED) != 0 ||
                        std_atomic_thread_fence(STD_MEMORY_ORDER_CONSUME) != 0 ||
                        std_atomic_thread_fence(STD_MEMORY_ORDER_ACQUIRE) != 0 ||
                        std_atomic_thread_fence(STD_MEMORY_ORDER_RELEASE) != 0 ||
                        std_atomic_thread_fence(STD_MEMORY_ORDER_ACQ_REL) != 0 ||
                        std_atomic_thread_fence(STD_MEMORY_ORDER_SEQ_CST) != 0 ||
                        std_atomic_thread_fence((std_memory_order_t)99) != -2) return 9;

                    if (std_atomic_init_int(&counter, 0) != 0) return 10;
                    for (index = 0; index < 4; index++) {
                        workers[index] = std_thread_create(increment_worker, &counter);
                        if (workers[index] <= 0) return 11;
                    }
                    for (index = 0; index < 4; index++) {
                        thread_result = (void*)-1;
                        if (std_thread_join(workers[index], &thread_result) != 0 || thread_result != (void*)0) return 12;
                    }
                    if (std_atomic_load_int(&counter, STD_MEMORY_ORDER_ACQUIRE, &value) != 0 || value != 8000) return 13;

                    if (std_atomic_init_int(&wait_flag, 0) != 0 ||
                        std_atomic_init_int(&wait_ready, 0) != 0 ||
                        std_atomic_wake_int(&wait_flag, 0) != 0 ||
                        std_atomic_wait_int(&wait_flag, 0, STD_MEMORY_ORDER_RELEASE) != -2) return 14;
                    waiter = std_thread_create(atomic_waiter, (void*)0);
                    if (waiter <= 0) return 15;
                    for (index = 0; index < 100000; index++) {
                        if (std_atomic_load_int(&wait_ready, STD_MEMORY_ORDER_ACQUIRE, &value) != 0) return 16;
                        if (value == 1) break;
                        if (std_thread_yield() != 0) return 17;
                    }
                    if (index == 100000 || std_atomic_store_int(&wait_flag, 1, STD_MEMORY_ORDER_RELEASE) != 0 ||
                        std_atomic_wake_int(&wait_flag, 1) != 0) return 18;
                    thread_result = (void*)-1;
                    if (std_thread_join(waiter, &thread_result) != 0 || thread_result != (void*)0) return 19;
                    return 0;
                }
            """.trimIndent())
        }
        val executable = directory.resolve(if (isWindows) "std-atomic.exe" else "std-atomic")
        val generatedC = directory.resolve("std-atomic.c")
        try {
            val compilation = CPlusCompiler().compile(
                CompileRequest(listOf(root.resolve("std/src/atomic.cp"), root.resolve("std/src/thread.cp"), mainSource), target)
            )
            assertTrue(compilation.isSuccessful, compilation.diagnostics.joinToString())
            assertEquals(1, compilation.generatedUnits.size)
            Files.writeString(generatedC, compilation.generatedUnits.single().text)
            val link = LinkDriver.link(LinkRequest(generatedC, executable, target, resolution), plan)
            assertTrue(link.isSuccessful, link.output)

            if (isWindows) {
                val descriptor = resolution.targetDescriptor
                    ?: requireNotNull(TargetRegistry.load(resolution.layout.abiDescriptor).descriptor)
                val audit = RuntimeDependencyAuditor.inspect(executable, descriptor, target.buildProfile)
                assertTrue(audit.isSuccessful, audit.diagnostics.joinToString())
            } else {
                val undefinedSymbols = ProcessBuilder("nm", "-u", executable.toString()).start()
                val undefinedOutput = undefinedSymbols.inputStream.bufferedReader().readText()
                assertEquals(0, undefinedSymbols.waitFor(), undefinedOutput)
                assertTrue(undefinedOutput.isBlank(), undefinedOutput)
            }

            val process = ProcessBuilder(executable.toString()).start()
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor(2, TimeUnit.SECONDS)
                throw AssertionError("C+ std.atomic fixture timed out; artifacts at $directory")
            }
            val stdout = process.inputStream.readBytes().toString(Charsets.UTF_8)
            val stderr = process.errorStream.readBytes().toString(Charsets.UTF_8)
            assertEquals(0, process.exitValue(), "stdout=$stdout stderr=$stderr")
        } finally {
            Files.deleteIfExists(executable)
            Files.deleteIfExists(generatedC)
            Files.deleteIfExists(mainSource)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun stdAtomicIntegerAndMemoryOrderMatchFourTargetAbis() {
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val module = root.resolve("std/src/atomic.cp")
        val functionNames = listOf(
            "std_atomic_init_int", "std_atomic_load_int", "std_atomic_store_int",
            "std_atomic_exchange_int", "std_atomic_compare_exchange_int",
            "std_atomic_fetch_add_int", "std_atomic_fetch_sub_int", "std_atomic_fetch_and_int",
            "std_atomic_fetch_or_int", "std_atomic_fetch_xor_int", "std_atomic_thread_fence",
            "std_atomic_wait_int", "std_atomic_wake_int"
        )
        listOf("linux-x86_64", "linux-aarch64", "windows-x86_64", "windows-aarch64").forEach { targetName ->
            val result = CPlusCompiler().compile(
                CompileRequest(listOf(module), target = TargetInfo(targetTriple = targetName))
            )
            assertTrue(result.isSuccessful, "$targetName: ${result.diagnostics.joinToString()}")
            val model = requireNotNull(result.semanticModel)
            val descriptor = requireNotNull(TargetRegistry.load(root.resolve("abi/$targetName.toml")).descriptor)
            val layouts = AbiLayoutEngine(descriptor)
            val atomic = layouts.layout(model.structs.getValue("std_atomic_int_t"))
            assertEquals(4, atomic.size, "$targetName atomic size")
            assertEquals(4, atomic.alignment, "$targetName atomic alignment")
            assertEquals(listOf(0), atomic.fields.map { it.offset }, "$targetName atomic field offset")
            assertEquals(4, layouts.layout(model.enums.getValue("std_memory_order_t")).size, "$targetName order size")
            assertEquals(4, layouts.layout(model.enums.getValue("std_memory_order_t")).alignment, "$targetName order alignment")
            functionNames.forEach { name -> assertTrue(name in model.functions, "$targetName missing $name") }
            val generated = result.generatedUnits.joinToString("\n") { it.text }
            functionNames.forEach { name -> assertTrue(name in generated, "$targetName missing declaration $name") }
        }
    }
}
