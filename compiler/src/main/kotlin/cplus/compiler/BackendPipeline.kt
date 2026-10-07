package cplus.compiler

import cplus.backend.CEmitter
import cplus.backend.CHeaderGenerator
import cplus.backend.CLowerer
import cplus.backend.GeneratedCUnit
import cplus.backend.LoweredCResult
import cplus.core.AstProgram
import cplus.semantic.SemanticModel

/**
 * A named in-process channel carrying packets between compiler processing
 * nodes. Channels are intentionally non-recursive: a node pops its inputs,
 * invokes a library service, and pushes its outputs.
 */
internal class CompilerPacketChannel<T>(val name: String) {
    private val packets = ArrayDeque<T>()

    fun push(packet: T) {
        packets.addLast(packet)
    }

    fun pop(): T? = packets.removeFirstOrNull()

    fun peek(): T? = packets.firstOrNull()

    fun requirePacket(): T = pop() ?: error("channel '$name' did not contain a packet")

    val isEmpty: Boolean
        get() = packets.isEmpty()
}

internal data class BackendPipelineResult(
    val lowered: LoweredCResult,
    val generated: GeneratedCUnit?,
    val header: GeneratedCUnit?
)

/**
 * Executes the lower-to-C backend as explicit processing nodes.
 *
 * The node functions contain only channel movement and service invocation;
 * lowering, dependency collection, header synthesis, and emission remain in
 * their respective backend libraries. The duplicated lowerer packets are an
 * explicit fan-out so naming/emission and header/dependency synthesis can
 * consume independently without sharing mutable queue state.
 */
internal class BackendProcessingPipeline(
    private val lowerer: CLowerer,
    private val emitter: CEmitter,
    private val headerGenerator: CHeaderGenerator = CHeaderGenerator()
) {
    private data class Channels(
        val ast: CompilerPacketChannel<AstProgram> = CompilerPacketChannel("ast"),
        val semantic: CompilerPacketChannel<SemanticModel> = CompilerPacketChannel("semantic"),
        val cAst: CompilerPacketChannel<LoweredCResult> = CompilerPacketChannel("cAst"),
        val cAstForHeaders: CompilerPacketChannel<LoweredCResult> = CompilerPacketChannel("cAstForHeaders"),
        val namedCAst: CompilerPacketChannel<LoweredCResult> = CompilerPacketChannel("namedCAst"),
        val headers: CompilerPacketChannel<GeneratedCUnit> = CompilerPacketChannel("headers"),
        val sourceMap: CompilerPacketChannel<GeneratedCUnit> = CompilerPacketChannel("sourceMap")
    )

    fun run(program: AstProgram, semantic: SemanticModel): BackendPipelineResult {
        val channels = Channels()
        channels.ast.push(program)
        channels.semantic.push(semantic)

        lowerCplus(channels)
        val lowered = channels.namedCAst.peek() ?: error("channel 'namedCAst' did not contain a packet")
        val headerLowered = channels.cAstForHeaders.requirePacket()
        if (lowered.diagnostics.any { it.severity == cplus.core.DiagnosticSeverity.ERROR }) {
            return BackendPipelineResult(lowered, null, null)
        }

        channels.cAstForHeaders.push(headerLowered)
        buildCSubsetAstAndDependencies(channels)
        emitCAndSourceMap(channels)
        return BackendPipelineResult(
            lowered,
            channels.sourceMap.requirePacket(),
            channels.headers.requirePacket()
        )
    }

    /** Processing node: lower C+ AST and fan out the resulting C-subset packet. */
    private fun lowerCplus(channels: Channels) {
        val program = channels.ast.requirePacket()
        val semantic = channels.semantic.requirePacket()
        val lowered = lowerer.lower(program)
        channels.cAst.push(lowered)
        channels.cAstForHeaders.push(lowered)
        nameCSymbols(channels)
    }

    /** Processing node: materialize the named C AST channel. */
    private fun nameCSymbols(channels: Channels) {
        channels.namedCAst.push(channels.cAst.requirePacket())
    }

    /** Processing node: build public headers and dependency artifacts. */
    private fun buildCSubsetAstAndDependencies(channels: Channels) {
        val lowered = channels.cAstForHeaders.requirePacket()
        channels.headers.push(headerGenerator.generate(lowered.unit))
    }

    /** Processing node: emit the named C AST and source map. */
    private fun emitCAndSourceMap(channels: Channels) {
        val lowered = channels.namedCAst.requirePacket()
        channels.sourceMap.push(emitter.emit(lowered.unit))
    }

}
