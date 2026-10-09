package cplus.cli

import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal data class FixtureProcessResult(
    val exitCode: Int?,
    val protocol: TestProtocolResult?,
    val timedOut: Boolean = false,
    val error: String? = null,
    val stdoutNeedsSeparator: Boolean = false,
    val stderrNeedsSeparator: Boolean = false
) {
    val isSuccessful: Boolean
        get() = !timedOut && error == null && exitCode == 0 && protocol?.completed == true && protocol.error == null
}

/** Launches one isolated fixture process and owns only its private result file. */
internal class TestProcessRunner(
    private val stdout: OutputStream = System.out,
    private val stderr: OutputStream = System.err
) {
    fun run(
        executable: Path,
        fixtureIdentity: String,
        timeoutSeconds: Long,
        temporaryDirectory: Path
    ): FixtureProcessResult {
        require(timeoutSeconds > 0) { "fixture timeout must be positive" }
        val resultPath = try {
            Files.createDirectories(temporaryDirectory)
            Files.createTempFile(temporaryDirectory, "cplus-test-result-", ".tsv")
        } catch (error: IOException) {
            return FixtureProcessResult(null, null, error = "unable to create fixture result file: ${error.message}")
        }
        var process: Process? = null
        var timedOut = false
        var interrupted = false
        var processError: String? = null
        var outDrain: Thread? = null
        var errDrain: Thread? = null
        val outputError = AtomicReference<String?>(null)
        val stdoutSawBytes = AtomicBoolean(false)
        val stderrSawBytes = AtomicBoolean(false)
        val stdoutLastByte = AtomicInteger(-1)
        val stderrLastByte = AtomicInteger(-1)
        try {
            process = try {
                ProcessBuilder(executable.toAbsolutePath().normalize().toString(), fixtureIdentity, resultPath.toString())
                    .redirectInput(ProcessBuilder.Redirect.INHERIT)
                    .start()
            } catch (error: IOException) {
                processError = "unable to start fixture '${executable.fileName}': ${error.message}"
                null
            }
            if (process != null) {
                outDrain = drain(process.inputStream, stdout, "cplus-test-stdout", outputError, stdoutSawBytes, stdoutLastByte)
                errDrain = drain(process.errorStream, stderr, "cplus-test-stderr", outputError, stderrSawBytes, stderrLastByte)
                try {
                    if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                        timedOut = true
                        terminateTree(process)
                    }
                } catch (_: InterruptedException) {
                    interrupted = true
                    terminateTree(process)
                }
                joinDrain(outDrain)
                joinDrain(errDrain)
            }
            val exitCode = process?.takeIf { !it.isAlive }?.exitValue()
            val protocol = if (Files.isRegularFile(resultPath)) {
                runCatching { Files.newInputStream(resultPath).use { TestResultProtocol.parse(it, fixtureIdentity) } }
                    .getOrElse { TestProtocolResult(emptyList(), false, "unable to read fixture result file: ${it.message}") }
            } else null
            val error = when {
                timedOut -> "fixture timed out after $timeoutSeconds second(s)"
                interrupted -> "fixture execution was interrupted"
                processError != null -> processError
                outputError.get() != null -> outputError.get()
                exitCode == null -> "fixture process did not exit"
                exitCode != 0 -> "fixture exited with status $exitCode"
                protocol == null -> "fixture did not produce a result protocol"
                protocol.error != null -> protocol.error
                else -> null
            }
            return FixtureProcessResult(
                exitCode,
                protocol,
                timedOut,
                error,
                stdoutSawBytes.get() && stdoutLastByte.get() != '\n'.code,
                stderrSawBytes.get() && stderrLastByte.get() != '\n'.code
            )
        } finally {
            if (process?.isAlive == true) terminateTree(process)
            runCatching { Files.deleteIfExists(resultPath) }
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

    private fun drain(
        input: java.io.InputStream,
        output: OutputStream,
        name: String,
        outputError: AtomicReference<String?>,
        sawBytes: AtomicBoolean,
        lastByte: AtomicInteger
    ): Thread =
        Thread({
            var forwarding = true
            try {
                input.use { stream ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        sawBytes.set(true)
                        lastByte.set(buffer[count - 1].toInt() and 0xff)
                        if (forwarding) {
                            try {
                                synchronized(output) {
                                    output.write(buffer, 0, count)
                                    output.flush()
                                }
                            } catch (error: IOException) {
                                outputError.compareAndSet(null, "unable to forward fixture output: ${error.message}")
                                forwarding = false
                            }
                        }
                    }
                }
            } catch (_: IOException) {
                // Process teardown closes pipes; no unbounded buffering is retained.
            }
        }, name).apply { isDaemon = true; start() }

    private fun joinDrain(thread: Thread?) {
        if (thread == null) return
        try {
            thread.join(5_000)
            if (thread.isAlive) thread.interrupt()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun terminateTree(process: Process) {
        val root = process.toHandle()
        runCatching { root.descendants().forEach { child -> child.destroy() } }
        process.destroy()
        try {
            if (!process.waitFor(200, TimeUnit.MILLISECONDS)) {
                runCatching { root.descendants().forEach { child -> child.destroyForcibly() } }
                process.destroyForcibly()
                process.waitFor(5, TimeUnit.SECONDS)
            }
        } catch (_: InterruptedException) {
            runCatching { root.descendants().forEach { child -> child.destroyForcibly() } }
            process.destroyForcibly()
            Thread.currentThread().interrupt()
        }
    }
}
