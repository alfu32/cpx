package cplus.compiler

import cplus.comptime.ComptimeTargetInfo
import cplus.core.Diagnostic
import cplus.core.DiagnosticSeverity
import java.nio.file.Files
import java.nio.file.Path

data class FloatingAbiDescriptor(
    val format: String,
    val sizeBytes: Int,
    val alignmentBytes: Int,
    val mantissaDigits: Int,
    val minExponent: Int,
    val maxExponent: Int
)

data class TargetAbiDescriptor(
    val targetTriple: String,
    val os: String,
    val architecture: String,
    val vendor: String,
    val abi: String,
    val objectFormat: String,
    val endianness: String,
    val pointerBits: Int,
    val wordBits: Int,
    val cIntegerModel: String,
    val floatingTypes: Map<String, FloatingAbiDescriptor>,
    val stackAlignment: Int,
    val symbolPrefix: String,
    val tlsModel: String,
    val linker: String,
    val startupEntry: String,
    val systemLibraries: Set<String>,
    val features: Set<String>,
    val intrinsics: Set<String>,
    val supportedAbis: Set<String>
) {
    fun toComptimeTarget(target: TargetInfo): ComptimeTargetInfo = ComptimeTargetInfo(
        cDialect = target.cDialect,
        runtimeProfile = target.buildProfile.runtime.name.lowercase(),
        libcProfile = target.buildProfile.libc.name.lowercase(),
        os = os,
        architecture = architecture,
        vendor = vendor,
        abi = abi,
        objectFormat = objectFormat,
        endianness = endianness,
        pointerBits = pointerBits,
        wordBits = wordBits,
        cIntegerModel = cIntegerModel,
        features = features,
        intrinsics = intrinsics,
        supportedAbis = supportedAbis,
        libcProfiles = setOf(target.buildProfile.libc.name.lowercase()),
        services = if (target.buildProfile.runtime == RuntimeProfile.SYSTEM) {
            emptySet()
        } else {
            PlatformAbiRegistry.profile(this).supportedServices
        }
    )
}

data class TargetDescriptorResult(
    val descriptor: TargetAbiDescriptor?,
    val diagnostics: List<Diagnostic>
) {
    val isSuccessful: Boolean
        get() = descriptor != null && diagnostics.none { it.severity == DiagnosticSeverity.ERROR }
}

object TargetRegistry {
    private val scalarPattern = Regex("^([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*\\\"([^\\\"]*)\\\"$")
    private val integerPattern = Regex("^([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(-?[0-9]+)$")
    private val arrayPattern = Regex("^([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*\\[(.*)]$")
    private val floatingTypePrefixes = linkedMapOf(
        "float" to "float",
        "double" to "double",
        "long double" to "long_double"
    )
    private val requiredScalars = listOf(
        "target", "os", "architecture", "vendor", "abi", "object_format", "endianness",
        "c_integer_model", "symbol_prefix", "tls_model", "linker", "startup_entry"
    )
    private val requiredIntegers = listOf("pointer_bits", "word_bits", "stack_alignment") +
        floatingTypePrefixes.values.flatMap { prefix ->
            listOf("${prefix}_size", "${prefix}_alignment", "${prefix}_mant_dig", "${prefix}_min_exp", "${prefix}_max_exp")
        }
    private val requiredFloatingFormats = floatingTypePrefixes.values.map { "${it}_format" }

    fun load(resolution: SdkResolution): TargetDescriptorResult = load(resolution.layout.abiDescriptor)

    fun load(path: Path): TargetDescriptorResult {
        if (!Files.isRegularFile(path)) return failure("target ABI descriptor does not exist: $path", "ABI001")
        val scalars = linkedMapOf<String, String>()
        val integers = linkedMapOf<String, Int>()
        val arrays = linkedMapOf<String, Set<String>>()
        val diagnostics = mutableListOf<Diagnostic>()
        runCatching { Files.readAllLines(path) }.getOrElse { error ->
            return failure("unable to read target ABI descriptor '$path': ${error.message}", "ABI001")
        }.forEachIndexed { index, rawLine ->
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) return@forEachIndexed
            val scalar = scalarPattern.matchEntire(line)
            val integer = integerPattern.matchEntire(line)
            val array = arrayPattern.matchEntire(line)
            when {
                scalar != null -> putUnique(scalars, scalar.groupValues[1], scalar.groupValues[2], index, diagnostics)
                integer != null -> putUnique(integers, integer.groupValues[1], integer.groupValues[2].toInt(), index, diagnostics)
                array != null -> {
                    val values = array.groupValues[2].split(',').map { it.trim().removeSurrounding("\"") }.filter(String::isNotEmpty).toSet()
                    putUnique(arrays, array.groupValues[1], values, index, diagnostics)
                }
                else -> diagnostics += diagnostic("invalid ABI descriptor entry on line ${index + 1}", "ABI002")
            }
        }
        requiredScalars.filterNot(scalars::containsKey).forEach { diagnostics += diagnostic("ABI descriptor is missing '$it'", "ABI003") }
        requiredFloatingFormats.filterNot(scalars::containsKey).forEach { diagnostics += diagnostic("ABI descriptor is missing '$it'", "ABI003") }
        requiredIntegers.filterNot(integers::containsKey).forEach { diagnostics += diagnostic("ABI descriptor is missing '$it'", "ABI003") }
        val floatingTypes = floatingTypePrefixes.map { (typeName, prefix) ->
            val floatingAbi = FloatingAbiDescriptor(
                format = scalars["${prefix}_format"].orEmpty(),
                sizeBytes = integers["${prefix}_size"] ?: 0,
                alignmentBytes = integers["${prefix}_alignment"] ?: 0,
                mantissaDigits = integers["${prefix}_mant_dig"] ?: 0,
                minExponent = integers["${prefix}_min_exp"] ?: 0,
                maxExponent = integers["${prefix}_max_exp"] ?: 0
            )
            if (floatingAbi.format.isNotBlank() &&
                (floatingAbi.sizeBytes <= 0 || floatingAbi.alignmentBytes <= 0 ||
                    floatingAbi.alignmentBytes and (floatingAbi.alignmentBytes - 1) != 0 ||
                    floatingAbi.mantissaDigits <= 0 || floatingAbi.minExponent >= floatingAbi.maxExponent)
            ) {
                diagnostics += diagnostic("ABI descriptor has invalid floating ABI for '$typeName'", "ABI005")
            }
            typeName to floatingAbi
        }.toMap()
        if (diagnostics.isNotEmpty()) return TargetDescriptorResult(null, diagnostics)
        val target = scalars.getValue("target")
        return TargetDescriptorResult(
            TargetAbiDescriptor(
                target,
                scalars.getValue("os"),
                scalars.getValue("architecture"),
                scalars.getValue("vendor"),
                scalars.getValue("abi"),
                scalars.getValue("object_format"),
                scalars.getValue("endianness"),
                integers.getValue("pointer_bits"),
                integers.getValue("word_bits"),
                scalars.getValue("c_integer_model"),
                floatingTypes,
                integers.getValue("stack_alignment"),
                scalars.getValue("symbol_prefix"),
                scalars.getValue("tls_model"),
                scalars.getValue("linker"),
                scalars.getValue("startup_entry"),
                arrays["system_libraries"].orEmpty(),
                arrays["features"].orEmpty(),
                arrays["intrinsics"].orEmpty(),
                arrays["supported_abis"].orEmpty()
            ).also {
                if (it.targetTriple != path.fileName.toString().removeSuffix(".toml")) {
                    diagnostics += diagnostic("ABI descriptor target '${it.targetTriple}' does not match file name", "ABI004")
                }
            },
            diagnostics
        )
    }

    fun list(root: Path): List<Path> = if (!Files.isDirectory(root)) emptyList() else Files.list(root).use { stream ->
        stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".toml") }.sorted().toList()
    }

    private fun <T> putUnique(map: MutableMap<String, T>, key: String, value: T, line: Int, diagnostics: MutableList<Diagnostic>) {
        if (map.putIfAbsent(key, value) != null) diagnostics += diagnostic("duplicate ABI descriptor key '$key' on line ${line + 1}", "ABI002")
    }

    private fun diagnostic(message: String, code: String): Diagnostic = Diagnostic(DiagnosticSeverity.ERROR, message, null, code)
    private fun failure(message: String, code: String): TargetDescriptorResult = TargetDescriptorResult(null, listOf(diagnostic(message, code)))
}
