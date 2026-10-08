package cplus.compiler

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class RuntimeSyncPalTest {
    @Test
    fun windowsExecutesSynchronizationAndAtomicWaitWakeThroughProductionPal() {
        assumeTrue(System.getProperty("os.name").contains("windows", ignoreCase = true))
        val manifest = requireNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val target = TargetInfo(targetTriple = "windows-x86_64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val plan = requireNotNull(RuntimeLinker.plan(resolution, target).plan)
        val directory = Files.createTempDirectory("cplus-windows-runtime-sync")
        val source = directory.resolve("sync_test.c").also {
            Files.writeString(it, """
                #include "cplus_platform.h"

                static volatile int mutex_state;
                static volatile int once_state;
                static volatile int semaphore_count;
                static volatile int condition_sequence;
                static volatile int condition_waiting;
                static volatile int condition_ready;
                static volatile int atomic_value;
                static volatile int atomic_waiting;
                static volatile int protected_count;
                static volatile int worker_error;

                static void* worker(void* context) {
                    int index;
                    int entered;
                    (void)context;
                    entered = platform_once_enter(&once_state);
                    if (entered == 0) {
                        if (platform_once_complete(&once_state) != 0) {
                            __atomic_store_n(&worker_error, 1, __ATOMIC_RELEASE);
                            return (void*)1;
                        }
                    } else if (entered != 1) {
                        __atomic_store_n(&worker_error, 2, __ATOMIC_RELEASE);
                        return (void*)2;
                    }
                    for (index = 0; index < 500; index++) {
                        if (platform_mutex_lock(&mutex_state) != 0) {
                            __atomic_store_n(&worker_error, 3, __ATOMIC_RELEASE);
                            return (void*)3;
                        }
                        protected_count++;
                        if (platform_mutex_unlock(&mutex_state) != 0) {
                            __atomic_store_n(&worker_error, 4, __ATOMIC_RELEASE);
                            return (void*)4;
                        }
                    }
                    {
                        int wait_result = platform_semaphore_wait(&semaphore_count);
                        if (wait_result != 0) {
                            __atomic_store_n(&worker_error, -wait_result, __ATOMIC_RELEASE);
                            return (void*)5;
                        }
                    }
                    return (void*)0;
                }

                static void* condition_worker(void* context) {
                    (void)context;
                    if (platform_mutex_lock(&mutex_state) != 0) return (void*)1;
                    __atomic_store_n(&condition_waiting, 1, __ATOMIC_RELEASE);
                    while (!__atomic_load_n(&condition_ready, __ATOMIC_ACQUIRE))
                        if (platform_condition_wait(&condition_sequence, &mutex_state) != 0) return (void*)2;
                    return platform_mutex_unlock(&mutex_state) == 0 ? (void*)0 : (void*)3;
                }

                static void* atomic_worker(void* context) {
                    (void)context;
                    __atomic_store_n(&atomic_waiting, 1, __ATOMIC_RELEASE);
                    while (__atomic_load_n(&atomic_value, __ATOMIC_ACQUIRE) == 0)
                        if (platform_atomic_wait32(&atomic_value, 0) < 0) return (void*)1;
                    return (void*)0;
                }

                static int wait_for(volatile int* value, int target) {
                    unsigned int attempt;
                    for (attempt = 0; attempt < 10000000U; attempt++) {
                        if (__atomic_load_n(value, __ATOMIC_ACQUIRE) == target) return 0;
                        if (platform_thread_yield() != 0) return 1;
                    }
                    return 1;
                }

                int main(void) {
                    long long workers[2];
                    long long thread;
                    void* result;
                    int index;
                    if (platform_mutex_init(&mutex_state) != 0 || platform_once_init(&once_state) != 0 ||
                        platform_semaphore_init(&semaphore_count, 0) != 0 ||
                        platform_condition_init(&condition_sequence) != 0) return 1;
                    for (index = 0; index < 2; index++) {
                        workers[index] = platform_thread_create(worker, (void*)0);
                        if (workers[index] <= 0) return 2;
                    }
                    if (wait_for(&protected_count, 1000) != 0)
                        return 80 + __atomic_load_n(&worker_error, __ATOMIC_ACQUIRE);
                    if (once_state != 2) return 32;
                    for (index = 0; index < 2; index++) if (platform_semaphore_post(&semaphore_count) != 0) return 4;
                    for (index = 0; index < 2; index++) {
                        result = (void*)-1;
                        if (platform_thread_join(workers[index], &result) != 0 || result != (void*)0) return 5;
                    }
                    thread = platform_thread_create(condition_worker, (void*)0);
                    if (thread <= 0 || wait_for(&condition_waiting, 1) != 0 || platform_mutex_lock(&mutex_state) != 0) return 6;
                    __atomic_store_n(&condition_ready, 1, __ATOMIC_RELEASE);
                    if (platform_condition_signal(&condition_sequence) != 0 || platform_mutex_unlock(&mutex_state) != 0) return 7;
                    result = (void*)-1;
                    if (platform_thread_join(thread, &result) != 0 || result != (void*)0) return 8;
                    thread = platform_thread_create(atomic_worker, (void*)0);
                    if (thread <= 0 || wait_for(&atomic_waiting, 1) != 0) return 9;
                    __atomic_store_n(&atomic_value, 1, __ATOMIC_RELEASE);
                    if (platform_atomic_wake32(&atomic_value, 1) != 0 ||
                        platform_atomic_wait32(&atomic_value, 0) != 0) return 10;
                    result = (void*)-1;
                    if (platform_thread_join(thread, &result) != 0 || result != (void*)0) return 11;
                    return 0;
                }
            """.trimIndent())
        }
        val executable = directory.resolve("sync_test.exe")
        try {
            val link = LinkDriver.link(LinkRequest(source, executable, target, resolution), plan)
            assertTrue(link.isSuccessful, link.output)
            val descriptor = resolution.targetDescriptor
                ?: requireNotNull(TargetRegistry.load(resolution.layout.abiDescriptor).descriptor)
            val audit = RuntimeDependencyAuditor.inspect(executable, descriptor, target.buildProfile)
            assertTrue(audit.isSuccessful, audit.diagnostics.joinToString())
            val process = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor(2, TimeUnit.SECONDS)
                throw AssertionError("Windows synchronization fixture timed out; artifacts at $directory")
            }
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.exitValue(), "Windows synchronization fixture failed: $output")
        } finally {
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    @Test
    fun linuxExecutesContendedSynchronizationAndAtomicWaitWakeWithoutHostRuntime() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val directory = Files.createTempDirectory("cplus-runtime-sync")
        val source = directory.resolve("sync_test.c").also {
            Files.writeString(it, """
                #include "cplus_platform.h"
                #include "cplus_runtime.h"

                #define WORKER_COUNT 4
                #define INCREMENTS_PER_WORKER 3000

                static volatile int mutex_state;
                static volatile int once_state;
                static volatile int semaphore_count;
                static volatile int condition_sequence;
                static volatile int condition_waiting;
                static volatile int condition_ready;
                static volatile int atomic_value;
                static volatile int atomic_waiting;
                static volatile int atomic_finished;
                static volatile int once_arrivals;
                static volatile int workers_finished;
                static volatile int initializer_count;
                static volatile int protected_count;

                static void* worker(void* context) {
                    int index;
                    int entered;
                    (void)context;
                    __atomic_add_fetch(&once_arrivals, 1, __ATOMIC_ACQ_REL);
                    entered = platform_once_enter(&once_state);
                    if (entered == 0) {
                        while (__atomic_load_n(&once_arrivals, __ATOMIC_ACQUIRE) != WORKER_COUNT) {
                            if (platform_thread_yield() != 0) return (void*)1;
                        }
                        __atomic_add_fetch(&initializer_count, 1, __ATOMIC_RELAXED);
                        if (platform_once_complete(&once_state) != 0) return (void*)2;
                    } else if (entered != 1) {
                        return (void*)3;
                    }
                    for (index = 0; index < INCREMENTS_PER_WORKER; index++) {
                        if (platform_mutex_lock(&mutex_state) != 0) return (void*)4;
                        protected_count++;
                        if (platform_mutex_unlock(&mutex_state) != 0) return (void*)5;
                    }
                    __atomic_add_fetch(&workers_finished, 1, __ATOMIC_RELEASE);
                    if (platform_semaphore_wait(&semaphore_count) != 0) return (void*)6;
                    return (void*)0;
                }

                static void* condition_waiter(void* context) {
                    int result;
                    (void)context;
                    if (platform_mutex_lock(&mutex_state) != 0) return (void*)1;
                    __atomic_add_fetch(&condition_waiting, 1, __ATOMIC_RELEASE);
                    while (!__atomic_load_n(&condition_ready, __ATOMIC_ACQUIRE)) {
                        result = platform_condition_wait(&condition_sequence, &mutex_state);
                        if (result != 0) return (void*)2;
                    }
                    if (!__atomic_load_n(&condition_ready, __ATOMIC_ACQUIRE)) return (void*)3;
                    if (platform_mutex_unlock(&mutex_state) != 0) return (void*)4;
                    return (void*)0;
                }

                static void* atomic_waiter(void* context) {
                    (void)context;
                    __atomic_store_n(&atomic_waiting, 1, __ATOMIC_RELEASE);
                    while (__atomic_load_n(&atomic_value, __ATOMIC_ACQUIRE) == 0) {
                        if (platform_atomic_wait32(&atomic_value, 0) < 0) return (void*)1;
                    }
                    __atomic_store_n(&atomic_finished, 1, __ATOMIC_RELEASE);
                    return (void*)0;
                }

                static int wait_for_value(volatile int* value, int expected) {
                    unsigned int attempts;
                    for (attempts = 0; attempts < 10000000U; attempts++) {
                        if (__atomic_load_n(value, __ATOMIC_ACQUIRE) == expected) return 0;
                        if (platform_thread_yield() != 0) return 1;
                    }
                    return 1;
                }

                int main(int argc, char** argv) {
                    long long workers[WORKER_COUNT];
                    long long condition_threads[2];
                    long long thread;
                    void* result;
                    int index;
                    (void)argc;
                    (void)argv;
                    if (CPLUS_PAL_API_VERSION != 4) return 1;
                    if (platform_mutex_init(&mutex_state) != 0 ||
                        platform_once_init(&once_state) != 0 ||
                        platform_semaphore_init(&semaphore_count, 0) != 0 ||
                        platform_condition_init(&condition_sequence) != 0) return 2;
                    if (platform_mutex_init((volatile int*)0) != CPLUS_PAL_INVALID_ARGUMENT ||
                        platform_atomic_wait32((volatile int*)0, 0) != CPLUS_PAL_INVALID_ARGUMENT ||
                        platform_atomic_wake32((volatile int*)1, 1) != CPLUS_PAL_INVALID_ARGUMENT ||
                        platform_condition_wait(&condition_sequence, &mutex_state) != CPLUS_PAL_INVALID_ARGUMENT ||
                        platform_semaphore_init(&semaphore_count, -1) != CPLUS_PAL_INVALID_ARGUMENT ||
                        platform_once_complete(&once_state) != CPLUS_PAL_INVALID_ARGUMENT ||
                        platform_atomic_wake32(&atomic_value, 0) != 0) return 3;
                    if (platform_semaphore_init(&semaphore_count, 0x7fffffff) != 0 ||
                        platform_semaphore_post(&semaphore_count) != CPLUS_PAL_INVALID_ARGUMENT ||
                        platform_semaphore_init(&semaphore_count, 0) != 0) return 4;

                    for (index = 0; index < WORKER_COUNT; index++) {
                        workers[index] = platform_thread_create(worker, (void*)0);
                        if (workers[index] <= 0) return 5;
                    }
                    if (wait_for_value(&workers_finished, WORKER_COUNT) != 0 ||
                        protected_count != WORKER_COUNT * INCREMENTS_PER_WORKER ||
                        __atomic_load_n(&initializer_count, __ATOMIC_ACQUIRE) != 1 ||
                        __atomic_load_n(&once_state, __ATOMIC_ACQUIRE) != 2) return 6;
                    for (index = 0; index < WORKER_COUNT; index++) {
                        if (platform_semaphore_post(&semaphore_count) != 0) return 7;
                    }
                    for (index = 0; index < WORKER_COUNT; index++) {
                        result = (void*)-1;
                        if (platform_thread_join(workers[index], &result) != 0 || result != (void*)0) return 8;
                    }
                    if (protected_count != WORKER_COUNT * INCREMENTS_PER_WORKER) return 9;

                    thread = platform_thread_create(condition_waiter, (void*)0);
                    if (thread <= 0 || wait_for_value(&condition_waiting, 1) != 0) return 10;
                    if (platform_mutex_lock(&mutex_state) != 0) return 11;
                    __atomic_store_n(&condition_ready, 1, __ATOMIC_RELEASE);
                    if (platform_condition_signal(&condition_sequence) != 0 ||
                        platform_mutex_unlock(&mutex_state) != 0) return 12;
                    result = (void*)-1;
                    if (platform_thread_join(thread, &result) != 0 || result != (void*)0) return 13;

                    __atomic_store_n(&condition_waiting, 0, __ATOMIC_RELAXED);
                    __atomic_store_n(&condition_ready, 0, __ATOMIC_RELAXED);
                    for (index = 0; index < 2; index++) {
                        condition_threads[index] = platform_thread_create(condition_waiter, (void*)0);
                        if (condition_threads[index] <= 0) return 17;
                    }
                    if (wait_for_value(&condition_waiting, 2) != 0 || platform_mutex_lock(&mutex_state) != 0) return 18;
                    __atomic_store_n(&condition_ready, 1, __ATOMIC_RELEASE);
                    if (platform_condition_broadcast(&condition_sequence) != 0 ||
                        platform_mutex_unlock(&mutex_state) != 0) return 19;
                    for (index = 0; index < 2; index++) {
                        result = (void*)-1;
                        if (platform_thread_join(condition_threads[index], &result) != 0 || result != (void*)0) return 20;
                    }

                    thread = platform_thread_create(atomic_waiter, (void*)0);
                    if (thread <= 0 || wait_for_value(&atomic_waiting, 1) != 0) return 14;
                    __atomic_store_n(&atomic_value, 1, __ATOMIC_RELEASE);
                    if (platform_atomic_wake32(&atomic_value, 1) != 0 ||
                        platform_atomic_wait32(&atomic_value, 0) != 0) return 15;
                    result = (void*)-1;
                    if (platform_thread_join(thread, &result) != 0 || result != (void*)0 ||
                        __atomic_load_n(&atomic_finished, __ATOMIC_ACQUIRE) != 1) return 16;
                    return 0;
                }
            """.trimIndent())
        }
        val executable = directory.resolve("sync_test")
        try {
            val compile = ProcessBuilder(
                "cc", "-std=c17", "-ffreestanding", "-fno-builtin", "-fno-stack-protector",
                "-fno-pie", "-nostdlib", "-static", "-Wl,-e,_start",
                "-Wl,-T,${root.resolve("platform/linux/thread-tls.ld")}",
                "-I", root.resolve("libc/include").toString(),
                "-I", root.resolve("runtime/include").toString(),
                "-I", root.resolve("platform/linux").toString(),
                source.toString(), root.resolve("startup/linux-x86_64/start.S").toString(),
                root.resolve("platform/linux/thread-x86_64.S").toString(),
                root.resolve("runtime/src/startup.c").toString(),
                root.resolve("runtime/src/libc_core.c").toString(),
                root.resolve("runtime/src/allocator.c").toString(),
                root.resolve("runtime/src/memory.c").toString(),
                root.resolve("runtime/src/sync.c").toString(),
                root.resolve("platform/linux/runtime.c").toString(),
                "-o", executable.toString()
            ).redirectErrorStream(true).start()
            val compileOutput = compile.inputStream.bufferedReader().readText()
            assertEquals(0, compile.waitFor(), compileOutput)
            val undefinedSymbols = ProcessBuilder("nm", "-u", executable.toString()).start()
            val undefinedOutput = undefinedSymbols.inputStream.bufferedReader().readText()
            assertEquals(0, undefinedSymbols.waitFor(), undefinedOutput)
            assertTrue(undefinedOutput.isBlank(), undefinedOutput)
            val run = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
            if (!run.waitFor(20, TimeUnit.SECONDS)) {
                run.destroyForcibly()
                run.waitFor(2, TimeUnit.SECONDS)
                throw AssertionError("synchronization fixture timed out; artifacts at $directory")
            }
            val runOutput = run.inputStream.bufferedReader().readText()
            assertEquals(0, run.exitValue(), "synchronization fixture failed with output '$runOutput'; artifacts at $directory")
        } finally {
            Files.deleteIfExists(executable)
            Files.deleteIfExists(source)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun synchronizationAbiIsDeclaredAcrossSupportedTargetModels() {
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val api = root.resolve("platform/api/sync.cp")
        val names = listOf(
            "platform_mutex_init", "platform_mutex_lock", "platform_mutex_unlock",
            "platform_condition_init", "platform_condition_wait", "platform_condition_signal",
            "platform_condition_broadcast", "platform_semaphore_init", "platform_semaphore_wait",
            "platform_semaphore_post", "platform_once_init", "platform_once_enter",
            "platform_once_complete", "platform_atomic_wait32", "platform_atomic_wake32"
        )
        listOf("linux-x86_64", "linux-aarch64", "windows-x86_64", "windows-aarch64").forEach { targetName ->
            val result = CPlusCompiler().compile(
                CompileRequest(listOf(api), target = TargetInfo(targetTriple = targetName))
            )
            assertTrue(result.isSuccessful, "$targetName: ${result.diagnostics.joinToString()}")
            val functions = requireNotNull(result.semanticModel).functions
            names.forEach { name -> assertTrue(name in functions, "$targetName missing $name") }
            val layouts = AbiLayoutEngine(requireNotNull(TargetRegistry.load(root.resolve("abi/$targetName.toml")).descriptor))
            assertEquals(8, layouts.layout(functions.getValue("platform_mutex_init").parameters.single().type).size, targetName)
            assertEquals(4, layouts.layout(functions.getValue("platform_atomic_wait32").parameters[1].type).size, targetName)
            assertEquals(4, layouts.layout(functions.getValue("platform_atomic_wake32").parameters[1].type).size, targetName)
            assertTrue(names.all { it in result.generatedUnits.joinToString("\n") { unit -> unit.text } }, targetName)
        }
    }
}
