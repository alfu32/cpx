package cplus.compiler

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuntimeNetworkPalTest {
    @Test
    fun socketAddressAndOperationsHaveStableAbiAcrossSupportedTargets() {
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val api = root.resolve("platform/api/network.cp")
        val functions = listOf(
            "platform_socket_open", "platform_socket_bind", "platform_socket_listen",
            "platform_socket_accept", "platform_socket_connect", "platform_socket_get_address",
            "platform_socket_send", "platform_socket_receive", "platform_socket_send_to",
            "platform_socket_receive_from", "platform_socket_shutdown", "platform_socket_close"
        )
        val header = java.nio.file.Files.readString(root.resolve("runtime/include/cplus_platform.h"))
        listOf("CPLUS_PAL_NETWORK_ERROR (-8L)", "CPLUS_SOCKET_IPV4 4U", "CPLUS_SOCKET_IPV6 6U").forEach {
            assertTrue(it in header, "C PAL header is missing $it")
        }
        functions.forEach { name -> assertTrue(name in header, "C PAL header is missing $name") }

        listOf("linux-x86_64", "linux-aarch64", "windows-x86_64", "windows-aarch64").forEach { targetName ->
            val result = CPlusCompiler().compile(
                CompileRequest(listOf(api), target = TargetInfo(targetTriple = targetName))
            )
            assertTrue(result.isSuccessful, "$targetName: ${result.diagnostics.joinToString()}")
            val model = requireNotNull(result.semanticModel)
            val descriptor = requireNotNull(TargetRegistry.load(root.resolve("abi/$targetName.toml")).descriptor)
            val layouts = AbiLayoutEngine(descriptor)
            val address = layouts.layout(model.structs.getValue("cplus_socket_address_t"))
            assertEquals(28, address.size, "$targetName address size")
            assertEquals(4, address.alignment, "$targetName address alignment")
            assertEquals(listOf(0, 4, 6, 8, 24), address.fields.map { it.offset }, "$targetName address offsets")
            assertEquals(8, layouts.layout(model.functions.getValue("platform_socket_open").returnType).size, "$targetName socket handle")
            functions.forEach { name -> assertTrue(name in model.functions, "$targetName missing $name") }
            val generated = result.generatedUnits.joinToString("\n") { it.text }
            functions.forEach { name -> assertTrue(name in generated, "$targetName generated declaration $name") }
        }
    }

    @Test
    fun cPalHeaderHasTheSpecifiedFixedAddressLayout() {
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val directory = Files.createTempDirectory("cplus-network-abi")
        val source = directory.resolve("network_abi.c").also {
            Files.writeString(it, """
                #include "cplus_platform.h"
                _Static_assert(sizeof(cplus_socket_handle_t) == 8, "socket handle width");
                _Static_assert(sizeof(cplus_socket_address_t) == 28, "socket address size");
                _Static_assert(_Alignof(cplus_socket_address_t) == 4, "socket address alignment");
                _Static_assert(__builtin_offsetof(cplus_socket_address_t, family) == 0, "family offset");
                _Static_assert(__builtin_offsetof(cplus_socket_address_t, port) == 4, "port offset");
                _Static_assert(__builtin_offsetof(cplus_socket_address_t, reserved) == 6, "reserved offset");
                _Static_assert(__builtin_offsetof(cplus_socket_address_t, address) == 8, "address bytes offset");
                _Static_assert(__builtin_offsetof(cplus_socket_address_t, scope_id) == 24, "scope offset");
            """.trimIndent())
        }
        try {
            val compile = ProcessBuilder(
                "cc", "-std=c17", "-Wall", "-Wextra", "-Werror", "-ffreestanding", "-fsyntax-only",
                "-I", root.resolve("runtime/include").toString(),
                "-I", root.resolve("libc/include").toString(), source.toString()
            ).redirectErrorStream(true).start()
            val output = compile.inputStream.bufferedReader().readText()
            assertEquals(0, compile.waitFor(), output)
        } finally {
            Files.deleteIfExists(source)
            Files.deleteIfExists(directory)
        }
    }
}
