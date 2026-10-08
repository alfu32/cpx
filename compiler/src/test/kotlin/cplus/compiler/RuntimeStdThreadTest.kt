package cplus.compiler

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class RuntimeStdThreadTest {
    @Test
    fun cplusThreadFacadeCreatesJoinsIdentifiesAndYields() {
        val isWindows = System.getProperty("os.name").contains("windows", ignoreCase = true)
        assumeTrue(isWindows || System.getProperty("os.name").contains("linux", ignoreCase = true))
        val manifestPath = SdkManifestLocator.defaultManifestPath()
        val manifest = requireNotNull(SdkManifestLoader.load(manifestPath).manifest)
        val target = TargetInfo(targetTriple = if (isWindows) "windows-x86_64" else "linux-x86_64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val plan = requireNotNull(RuntimeLinker.plan(resolution, target).plan)
        val root = manifestPath.toAbsolutePath().normalize().parent!!.parent!!
        val directory = Files.createTempDirectory("cplus-std-thread")
        val mainSource = directory.resolve("main.cp").also {
            Files.writeString(it, """
                import {
                    std_thread_create,
                    std_thread_current_id,
                    std_thread_join,
                    std_thread_yield
                } from std.thread;

                struct thread_state {
                    long long parent_id;
                    long long worker_id;
                    int value;
                };

                static void* worker(void* context) {
                    struct thread_state* state = (struct thread_state*)context;
                    state->worker_id = std_thread_current_id();
                    state->value = 42;
                    if (std_thread_yield() != 0) return (void*)1;
                    return context;
                }

                int main() {
                    struct thread_state state;
                    long long thread;
                    void* result = (void*)0;
                    state.parent_id = std_thread_current_id();
                    state.worker_id = 0;
                    state.value = 0;
                    if (state.parent_id <= 0 || std_thread_yield() != 0) return 1;
                    if (std_thread_join(-1, (void**)0) != -2) return 2;
                    thread = std_thread_create(worker, &state);
                    if (thread <= 0) return 3;
                    if (std_thread_join(thread, &result) != 0) return 4;
                    if (result != &state || state.value != 42 || state.worker_id <= 0 ||
                        state.worker_id == state.parent_id) return 5;
                    thread = std_thread_create(worker, &state);
                    if (thread <= 0 || std_thread_join(thread, (void**)0) != 0) return 6;
                    return 0;
                }
            """.trimIndent())
        }
        val executable = directory.resolve(if (isWindows) "std-thread.exe" else "std-thread")
        val generatedC = directory.resolve("std-thread.c")
        try {
            val compilation = CPlusCompiler().compile(
                CompileRequest(listOf(root.resolve("std/src/thread.cp"), mainSource), target)
            )
            assertTrue(compilation.isSuccessful, compilation.diagnostics.joinToString())
            assertEquals(1, compilation.generatedUnits.size)
            Files.writeString(generatedC, compilation.generatedUnits.single().text)

            val link = LinkDriver.link(
                LinkRequest(generatedC, executable, target, resolution),
                plan
            )
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
            val stdout = process.inputStream.readBytes().toString(Charsets.UTF_8)
            val stderr = process.errorStream.readBytes().toString(Charsets.UTF_8)
            assertEquals(0, process.waitFor(), "stdout=$stdout stderr=$stderr")
        } finally {
            Files.deleteIfExists(executable)
            Files.deleteIfExists(generatedC)
            Files.deleteIfExists(mainSource)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun stdThreadFacadeUsesOpaqueSixtyFourBitHandlesAcrossDeclaredTargets() {
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val modules = listOf(root.resolve("std/src/thread.cp"), root.resolve("platform/api/thread.cp"))
        listOf("linux-x86_64", "linux-aarch64", "windows-x86_64", "windows-aarch64").forEach { targetName ->
            val result = CPlusCompiler().compile(
                CompileRequest(modules, target = TargetInfo(targetTriple = targetName))
            )
            assertTrue(result.isSuccessful, "$targetName: ${result.diagnostics.joinToString()}")
            val model = requireNotNull(result.semanticModel)
            val descriptor = requireNotNull(TargetRegistry.load(root.resolve("abi/$targetName.toml")).descriptor)
            val layouts = AbiLayoutEngine(descriptor)
            listOf("std_thread_create", "std_thread_current_id").forEach { name ->
                assertEquals(8, layouts.layout(model.functions.getValue(name).returnType).size, "$targetName $name")
            }
            assertEquals("long long", model.functions.getValue("std_thread_join").parameters.first().type.name)
            assertEquals(8, layouts.layout(model.functions.getValue("std_thread_join").parameters.first().type).size)
            val generated = result.generatedUnits.joinToString("\n") { it.text }
            listOf("std_thread_create", "std_thread_join", "std_thread_current_id", "std_thread_yield").forEach {
                assertTrue(it in generated, "$targetName missing $it")
            }
        }
    }
}
