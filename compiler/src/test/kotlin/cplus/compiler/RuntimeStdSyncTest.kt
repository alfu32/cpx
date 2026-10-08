package cplus.compiler

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class RuntimeStdSyncTest {
    @Test
    fun cplusSyncFacadeExercisesMutexConditionSemaphoreAndOnce() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val manifestPath = SdkManifestLocator.defaultManifestPath()
        val manifest = requireNotNull(SdkManifestLoader.load(manifestPath).manifest)
        val target = TargetInfo(targetTriple = "linux-x86_64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val plan = requireNotNull(RuntimeLinker.plan(resolution, target).plan)
        val root = manifestPath.toAbsolutePath().normalize().parent!!.parent!!
        val directory = Files.createTempDirectory("cplus-std-sync")
        val mainSource = directory.resolve("main.cp").also {
            Files.writeString(it, """
                import {
                    std_condition_t,
                    std_condition_broadcast,
                    std_condition_init,
                    std_condition_signal,
                    std_condition_wait,
                    std_mutex_t,
                    std_mutex_init,
                    std_mutex_lock,
                    std_mutex_unlock,
                    std_once_t,
                    std_once_complete,
                    std_once_enter,
                    std_once_init,
                    std_semaphore_t,
                    std_semaphore_init,
                    std_semaphore_post,
                    std_semaphore_wait
                } from std.sync;
                import {
                    std_thread_create,
                    std_thread_join,
                    std_thread_yield
                } from std.thread;

                std_mutex_t shared_mutex;
                std_condition_t condition;
                std_semaphore_t semaphore;
                std_once_t once_guard;
                static int protected_count;
                static int initializer_count;
                static int condition_waiting;
                static int condition_ready;
                static int semaphore_waiting;

                static void* increment_worker(void* context) {
                    int index;
                    int once_status;
                    once_status = std_once_enter(&once_guard);
                    if (once_status == 0) {
                        initializer_count++;
                        if (std_once_complete(&once_guard) != 0) return (void*)1;
                    } else if (once_status != 1) {
                        return (void*)2;
                    }
                    for (index = 0; index < 1000; index++) {
                        if (std_mutex_lock(&shared_mutex) != 0) return (void*)3;
                        protected_count++;
                        if (std_mutex_unlock(&shared_mutex) != 0) return (void*)4;
                    }
                    return (void*)0;
                }

                static void* condition_worker(void* context) {
                    int status;
                    if (std_mutex_lock(&shared_mutex) != 0) return (void*)1;
                    condition_waiting++;
                    while (!condition_ready) {
                        status = std_condition_wait(&condition, &shared_mutex);
                        if (status != 0) return (void*)2;
                    }
                    if (std_mutex_unlock(&shared_mutex) != 0) return (void*)3;
                    return (void*)0;
                }

                static void* semaphore_worker(void* context) {
                    if (std_mutex_lock(&shared_mutex) != 0) return (void*)1;
                    semaphore_waiting = 1;
                    if (std_mutex_unlock(&shared_mutex) != 0) return (void*)2;
                    if (std_semaphore_wait(&semaphore) != 0) return (void*)3;
                    return (void*)0;
                }

                static int wait_for_condition_waiters(int expected) {
                    int attempt;
                    for (attempt = 0; attempt < 100000; attempt++) {
                        int observed;
                        if (std_mutex_lock(&shared_mutex) != 0) return 1;
                        observed = condition_waiting;
                        if (std_mutex_unlock(&shared_mutex) != 0) return 1;
                        if (observed == expected) return 0;
                        if (std_thread_yield() != 0) return 1;
                    }
                    return 1;
                }

                static int wait_for_semaphore_waiter() {
                    int attempt;
                    for (attempt = 0; attempt < 100000; attempt++) {
                        int observed;
                        if (std_mutex_lock(&shared_mutex) != 0) return 1;
                        observed = semaphore_waiting;
                        if (std_mutex_unlock(&shared_mutex) != 0) return 1;
                        if (observed) return 0;
                        if (std_thread_yield() != 0) return 1;
                    }
                    return 1;
                }

                int main() {
                    long long workers[4];
                    long long condition_threads[2];
                    long long thread;
                    void* result;
                    int index;
                    if (std_mutex_init((std_mutex_t*)0) != -2 ||
                        std_mutex_init(&shared_mutex) != 0 ||
                        std_condition_init(&condition) != 0 ||
                        std_semaphore_init(&semaphore, -1) != -2 ||
                        std_semaphore_init(&semaphore, 1) != 0 ||
                        std_once_init(&once_guard) != 0) return 1;
                    if (std_mutex_unlock(&shared_mutex) != -2 ||
                        std_condition_wait(&condition, &shared_mutex) != -2 ||
                        std_once_complete(&once_guard) != -2) return 2;
                    if (std_semaphore_wait(&semaphore) != 0 ||
                        std_semaphore_post(&semaphore) != 0 ||
                        std_semaphore_wait(&semaphore) != 0 ||
                        std_semaphore_init(&semaphore, 0) != 0) return 3;

                    for (index = 0; index < 4; index++) {
                        workers[index] = std_thread_create(increment_worker, (void*)0);
                        if (workers[index] <= 0) return 4;
                    }
                    for (index = 0; index < 4; index++) {
                        result = (void*)-1;
                        if (std_thread_join(workers[index], &result) != 0 || result != (void*)0) return 5;
                    }
                    if (protected_count != 4000 || initializer_count != 1) return 6;

                    thread = std_thread_create(condition_worker, (void*)0);
                    if (thread <= 0 || wait_for_condition_waiters(1) != 0) return 7;
                    if (std_mutex_lock(&shared_mutex) != 0) return 8;
                    condition_ready = 1;
                    if (std_condition_signal(&condition) != 0 || std_mutex_unlock(&shared_mutex) != 0) return 9;
                    result = (void*)-1;
                    if (std_thread_join(thread, &result) != 0 || result != (void*)0) return 10;

                    if (std_mutex_lock(&shared_mutex) != 0) return 11;
                    condition_waiting = 0;
                    condition_ready = 0;
                    if (std_mutex_unlock(&shared_mutex) != 0) return 12;
                    for (index = 0; index < 2; index++) {
                        condition_threads[index] = std_thread_create(condition_worker, (void*)0);
                        if (condition_threads[index] <= 0) return 13;
                    }
                    if (wait_for_condition_waiters(2) != 0 || std_mutex_lock(&shared_mutex) != 0) return 14;
                    condition_ready = 1;
                    if (std_condition_broadcast(&condition) != 0 || std_mutex_unlock(&shared_mutex) != 0) return 15;
                    for (index = 0; index < 2; index++) {
                        result = (void*)-1;
                        if (std_thread_join(condition_threads[index], &result) != 0 || result != (void*)0) return 16;
                    }

                    if (std_mutex_lock(&shared_mutex) != 0) return 17;
                    semaphore_waiting = 0;
                    if (std_mutex_unlock(&shared_mutex) != 0 || std_semaphore_init(&semaphore, 0) != 0) return 18;
                    thread = std_thread_create(semaphore_worker, (void*)0);
                    if (thread <= 0 || wait_for_semaphore_waiter() != 0 || std_semaphore_post(&semaphore) != 0) return 19;
                    result = (void*)-1;
                    if (std_thread_join(thread, &result) != 0 || result != (void*)0) return 20;
                    return 0;
                }
            """.trimIndent())
        }
        val executable = directory.resolve("std-sync")
        val generatedC = directory.resolve("std-sync.c")
        try {
            val compilation = CPlusCompiler().compile(
                CompileRequest(
                    listOf(root.resolve("std/src/sync.cp"), root.resolve("std/src/thread.cp"), mainSource),
                    target
                )
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

            val process = ProcessBuilder(executable.toString()).start()
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor(2, TimeUnit.SECONDS)
                throw AssertionError("C+ std.sync fixture timed out; artifacts at $directory")
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
    fun stdSyncObjectsAndDeclarationsMatchFourByteStateWordAbiAcrossTargets() {
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val module = root.resolve("std/src/sync.cp")
        val objectFields = mapOf(
            "std_mutex_t" to "state",
            "std_condition_t" to "sequence",
            "std_semaphore_t" to "count",
            "std_once_t" to "state"
        )
        val functions = listOf(
            "std_mutex_init", "std_mutex_lock", "std_mutex_unlock",
            "std_condition_init", "std_condition_wait", "std_condition_signal", "std_condition_broadcast",
            "std_semaphore_init", "std_semaphore_wait", "std_semaphore_post",
            "std_once_init", "std_once_enter", "std_once_complete"
        )
        listOf("linux-x86_64", "linux-aarch64", "windows-x86_64", "windows-aarch64").forEach { targetName ->
            val result = CPlusCompiler().compile(
                CompileRequest(listOf(module), target = TargetInfo(targetTriple = targetName))
            )
            assertTrue(result.isSuccessful, "$targetName: ${result.diagnostics.joinToString()}")
            val model = requireNotNull(result.semanticModel)
            val layouts = AbiLayoutEngine(requireNotNull(TargetRegistry.load(root.resolve("abi/$targetName.toml")).descriptor))
            objectFields.forEach { (typeName, fieldName) ->
                val layout = layouts.layout(model.structs.getValue(typeName))
                assertEquals(4, layout.size, "$targetName $typeName size")
                assertEquals(4, layout.alignment, "$targetName $typeName alignment")
                assertEquals(listOf(0), layout.fields.map { it.offset }, "$targetName $typeName offset")
                assertEquals(listOf(fieldName), layout.fields.map { it.name }, "$targetName $typeName field")
            }
            functions.forEach { name -> assertTrue(name in model.functions, "$targetName missing $name") }
            val generated = result.generatedUnits.joinToString("\n") { it.text }
            functions.forEach { name -> assertTrue(name in generated, "$targetName missing declaration $name") }
        }
    }
}
