package cplus.compiler

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.util.concurrent.TimeUnit

class RuntimeStdNetTest {
    @Test
    fun cplusStdNetTcpFacadeRunsThroughProductionPal() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
            System.getProperty("os.name").contains("linux", ignoreCase = true)
        )
        val manifestPath = SdkManifestLocator.defaultManifestPath()
        val manifest = requireNotNull(SdkManifestLoader.load(manifestPath).manifest)
        val target = TargetInfo(targetTriple = "linux-x86_64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val planResult = RuntimeLinker.plan(resolution, target)
        assertTrue(planResult.isSuccessful, planResult.diagnostics.joinToString())
        val plan = requireNotNull(planResult.plan)
        val root = manifestPath.toAbsolutePath().normalize().parent!!.parent!!
        val directory = Files.createTempDirectory("cplus-std-net-tcp")
        val mainSource = directory.resolve("main.cp").also {
            Files.writeString(it, """
                import {
                    std_net_accept,
                    std_net_address_t,
                    std_net_bind,
                    std_net_close,
                    std_net_connect,
                    std_net_get_address,
                    std_net_listen,
                    std_net_open,
                    std_net_receive,
                    std_net_send,
                    std_net_shutdown,
                    std_net_socket_t,
                    STD_NET_FAMILY_ANY,
                    STD_NET_FAMILY_IPV4,
                    STD_NET_INVALID_ARGUMENT,
                    STD_NET_NETWORK_ERROR,
                    STD_NET_SHUTDOWN_SEND,
                    STD_NET_SOCKET_STREAM,
                    STD_NET_UNSUPPORTED
                } from std.net;

                static int bytes_equal(const char* left, const char* right, unsigned int length) {
                    unsigned int index;
                    for (index = 0; index < length; index++) {
                        if (left[index] != right[index]) return 0;
                    }
                    return 1;
                }

                int main() {
                    std_net_address_t loopback;
                    std_net_address_t server_address;
                    std_net_address_t peer_address;
                    std_net_address_t unchanged;
                    std_net_socket_t listener;
                    std_net_socket_t client;
                    std_net_socket_t accepted;
                    char received[32];
                    const char request[] = "facade-request";
                    const char response[] = "facade-response";
                    unsigned int index;

                    loopback.family = STD_NET_FAMILY_IPV4;
                    loopback.port = 0;
                    loopback.reserved = 0;
                    loopback.scope_id = 0;
                    for (index = 0; index < 16; index++) loopback.address[index] = 0;
                    loopback.address[0] = 127;
                    loopback.address[3] = 1;
                    unchanged.family = 77;
                    unchanged.port = 1234;
                    unchanged.reserved = 0;
                    unchanged.scope_id = 99;
                    for (index = 0; index < 16; index++) unchanged.address[index] = 0x5a;

                    if (std_net_open(STD_NET_FAMILY_ANY, STD_NET_SOCKET_STREAM) != STD_NET_UNSUPPORTED) return 1;
                    if (std_net_bind(-1, &loopback) != STD_NET_INVALID_ARGUMENT ||
                        std_net_bind(1, (const std_net_address_t*)0) != STD_NET_INVALID_ARGUMENT ||
                        std_net_get_address(-1, 0, &unchanged) != STD_NET_INVALID_ARGUMENT ||
                        unchanged.family != 77 || unchanged.port != 1234 || unchanged.scope_id != 99) return 2;
                    if (std_net_accept(-1, &unchanged) != STD_NET_INVALID_ARGUMENT ||
                        unchanged.family != 77 || unchanged.port != 1234 || unchanged.scope_id != 99) return 3;

                    listener = std_net_open(STD_NET_FAMILY_IPV4, STD_NET_SOCKET_STREAM);
                    if (listener < 0 || std_net_bind(listener, &loopback) != 0 ||
                        std_net_get_address(listener, 0, &server_address) != 0 ||
                        server_address.family != STD_NET_FAMILY_IPV4 || server_address.port == 0 ||
                        std_net_listen(listener, -1) != STD_NET_INVALID_ARGUMENT ||
                        std_net_listen(listener, 4) != 0) return 4;
                    client = std_net_open(STD_NET_FAMILY_IPV4, STD_NET_SOCKET_STREAM);
                    if (client < 0 || std_net_connect(client, &server_address) != 0) return 5;
                    accepted = std_net_accept(listener, &peer_address);
                    if (accepted < 0 || peer_address.family != STD_NET_FAMILY_IPV4 ||
                        std_net_get_address(client, 0, &server_address) != 0 ||
                        peer_address.port != server_address.port ||
                        std_net_get_address(client, 2, &unchanged) != STD_NET_INVALID_ARGUMENT) return 6;
                    if (std_net_send(client, (const void*)0, 1) != STD_NET_INVALID_ARGUMENT ||
                        std_net_receive(client, (void*)0, 1) != STD_NET_INVALID_ARGUMENT ||
                        std_net_receive(client, (void*)0, 0) != 0) return 7;
                    if (std_net_send(client, request, sizeof(request) - 1) != sizeof(request) - 1 ||
                        std_net_receive(accepted, received, sizeof(received)) != sizeof(request) - 1 ||
                        !bytes_equal(received, request, sizeof(request) - 1)) return 8;
                    if (std_net_send(accepted, response, sizeof(response) - 1) != sizeof(response) - 1 ||
                        std_net_receive(client, received, sizeof(received)) != sizeof(response) - 1 ||
                        !bytes_equal(received, response, sizeof(response) - 1)) return 9;
                    if (std_net_shutdown(client, STD_NET_SHUTDOWN_SEND) != 0 ||
                        std_net_receive(accepted, received, sizeof(received)) != 0) return 10;
                    if (std_net_close(client) != 0 || std_net_close(accepted) != 0 ||
                        std_net_close(listener) != 0) return 11;
                    return 0;
                }
            """.trimIndent())
        }
        val generatedC = directory.resolve("std-net-tcp.c")
        val executable = directory.resolve("std-net-tcp")
        try {
            val compilation = CPlusCompiler().compile(
                CompileRequest(listOf(root.resolve("std/src/net.cp"), mainSource), target)
            )
            assertTrue(compilation.isSuccessful, compilation.diagnostics.joinToString())
            Files.writeString(generatedC, compilation.generatedUnits.single().text)
            val link = LinkDriver.link(LinkRequest(generatedC, executable, target, resolution), plan)
            assertTrue(link.isSuccessful, link.output)

            val undefined = ProcessBuilder("nm", "-u", executable.toString()).start()
            val undefinedOutput = undefined.inputStream.bufferedReader().readText()
            assertEquals(0, undefined.waitFor(), undefinedOutput)
            assertTrue(undefinedOutput.isBlank(), "TCP façade product imports host symbols: $undefinedOutput")

            val process = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor(2, TimeUnit.SECONDS)
                throw AssertionError("C+ std.net TCP fixture timed out; artifacts at $directory")
            }
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.exitValue(), "TCP façade fixture failed with output '$output'")
        } finally {
            Files.deleteIfExists(executable)
            Files.deleteIfExists(generatedC)
            Files.deleteIfExists(mainSource)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun stdNetTcpFacadePassesStrictC17ChecksOnFourTargetCompilers() {
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val source = root.resolve("runtime/src/net.c")
        val cases = listOf(
            listOf("cc"),
            listOf("clang", "--target=aarch64-unknown-linux-gnu"),
            listOf("x86_64-w64-mingw32-gcc"),
            listOf("clang", "--target=aarch64-w64-windows-gnu")
        )
        cases.forEachIndexed { index, compiler ->
            val targetName = listOf("linux-x86_64", "linux-aarch64", "windows-x86_64", "windows-aarch64")[index]
            val process = ProcessBuilder(
                compiler + listOf(
                    "-std=c17", "-Wall", "-Wextra", "-Werror", "-ffreestanding", "-fno-builtin",
                    "-fsyntax-only", "-I", root.resolve("runtime/include").toString(), source.toString()
                )
            ).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.waitFor(), "$targetName: $output")
        }
    }

    @Test
    fun stdNetPublicTypesAndOperationsCompileAcrossFourTargetAbis() {
        val manifestPath = SdkManifestLocator.defaultManifestPath()
        val root = manifestPath.toAbsolutePath().normalize().parent!!.parent!!
        val module = root.resolve("std/src/net.cp")
        val mainSource = Files.createTempFile("std-net-api", ".cp").also {
            Files.writeString(it, """
                import {
                    std_net_accept,
                    std_net_address_t,
                    std_net_bind,
                    std_net_close,
                    std_net_connect,
                    std_net_family_t,
                    std_net_format_address,
                    std_net_get_address,
                    std_net_listen,
                    std_net_open,
                    std_net_parse_address,
                    std_net_receive,
                    std_net_receive_from,
                    std_net_resolve,
                    std_net_send,
                    std_net_send_to,
                    std_net_shutdown,
                    std_net_shutdown_t,
                    std_net_socket_kind_t,
                    std_net_socket_t,
                    std_net_status_t,
                    STD_NET_ACCESS_DENIED,
                    STD_NET_BUFFER_TOO_SMALL,
                    STD_NET_FAMILY_ANY,
                    STD_NET_FAMILY_IPV4,
                    STD_NET_FAMILY_IPV6,
                    STD_NET_INVALID_ARGUMENT,
                    STD_NET_IO_ERROR,
                    STD_NET_NETWORK_ERROR,
                    STD_NET_NOT_FOUND,
                    STD_NET_OK,
                    STD_NET_SHUTDOWN_BOTH,
                    STD_NET_SHUTDOWN_RECEIVE,
                    STD_NET_SHUTDOWN_SEND,
                    STD_NET_SOCKET_DATAGRAM,
                    STD_NET_SOCKET_STREAM,
                    STD_NET_UNSUPPORTED
                } from std.net;

                int check_public_api(std_net_socket_t socket, std_net_address_t* address,
                    const std_net_address_t* constant_address, char* buffer, uint64_t* count) {
                    std_net_socket_t opened;
                    int64_t transferred;
                    opened = std_net_open(STD_NET_FAMILY_IPV4, STD_NET_SOCKET_STREAM);
                    opened = std_net_accept(socket, address);
                    transferred = std_net_send(socket, buffer, 1);
                    transferred = std_net_receive(socket, buffer, 1);
                    transferred = std_net_send_to(socket, buffer, 1, constant_address);
                    transferred = std_net_receive_from(socket, buffer, 1, address);
                    transferred = std_net_format_address(constant_address, buffer, 1);
                    if (std_net_bind(socket, constant_address) != 0) return 1;
                    if (std_net_listen(socket, 1) != 0) return 2;
                    if (std_net_connect(socket, constant_address) != 0) return 3;
                    if (std_net_get_address(socket, 1, address) != 0) return 4;
                    if (std_net_shutdown(socket, STD_NET_SHUTDOWN_BOTH) != 0) return 5;
                    if (std_net_close(socket) != 0) return 6;
                    if (std_net_parse_address(STD_NET_FAMILY_IPV6, "::1", address) != STD_NET_OK) return 7;
                    if (std_net_resolve("localhost", STD_NET_FAMILY_ANY, 80, address, 1, count) != STD_NET_OK) return 8;
                    return (int)(opened + transferred + STD_NET_SOCKET_DATAGRAM +
                        STD_NET_FAMILY_ANY + STD_NET_FAMILY_IPV4 + STD_NET_FAMILY_IPV6 +
                        STD_NET_SHUTDOWN_RECEIVE + STD_NET_SHUTDOWN_SEND + STD_NET_OK +
                        STD_NET_INVALID_ARGUMENT + STD_NET_NOT_FOUND + STD_NET_ACCESS_DENIED +
                        STD_NET_IO_ERROR + STD_NET_UNSUPPORTED + STD_NET_BUFFER_TOO_SMALL +
                        STD_NET_NETWORK_ERROR);
                }

                std_net_socket_kind_t socket_kind(std_net_socket_kind_t value) { return value; }
                std_net_shutdown_t shutdown_direction(std_net_shutdown_t value) { return value; }
                std_net_family_t address_family(std_net_family_t value) { return value; }
                std_net_status_t network_status(std_net_status_t value) { return value; }
            """.trimIndent())
        }

        try {
            listOf("linux-x86_64", "linux-aarch64", "windows-x86_64", "windows-aarch64")
                .forEach { targetName ->
                    val result = CPlusCompiler().compile(
                        CompileRequest(listOf(module, mainSource), target = TargetInfo(targetTriple = targetName))
                    )
                    assertTrue(result.isSuccessful, "$targetName: ${result.diagnostics.joinToString()}")
                    val model = requireNotNull(result.semanticModel)
                    val descriptor = requireNotNull(
                        TargetRegistry.load(root.resolve("abi/$targetName.toml")).descriptor
                    )
                    val layouts = AbiLayoutEngine(descriptor)
                    val address = layouts.layout(model.structs.getValue("std_net_address_t"))
                    assertEquals(28, address.size, "$targetName address size")
                    assertEquals(4, address.alignment, "$targetName address alignment")
                    assertEquals(listOf(0, 4, 6, 8, 24), address.fields.map { it.offset }, "$targetName address offsets")
                    assertEquals(8, layouts.layout(model.aliases.getValue("std_net_socket_t")).size, "$targetName handle width")
                    listOf(
                        "std_net_family_t", "std_net_socket_kind_t", "std_net_shutdown_t", "std_net_status_t"
                    ).forEach { enumName ->
                        assertEquals(4, layouts.layout(model.enums.getValue(enumName)).size, "$targetName $enumName width")
                        assertEquals(4, layouts.layout(model.enums.getValue(enumName)).alignment, "$targetName $enumName alignment")
                    }
                    listOf(
                        "std_net_open", "std_net_bind", "std_net_listen", "std_net_accept",
                        "std_net_connect", "std_net_get_address", "std_net_send", "std_net_receive",
                        "std_net_send_to", "std_net_receive_from", "std_net_shutdown", "std_net_close",
                        "std_net_parse_address", "std_net_format_address", "std_net_resolve"
                    ).forEach { name -> assertTrue(name in model.functions, "$targetName missing $name") }
                    val generated = result.generatedUnits.joinToString("\n") { it.text }
                    assertTrue("std_net_address_t" in generated, "$targetName missing public address declaration")
                    assertTrue("std_net_socket_t" in generated, "$targetName missing public handle alias")
                    assertTrue("std_net_resolve" in generated, "$targetName missing resolver declaration")
                }
        } finally {
            Files.deleteIfExists(mainSource)
        }
    }
}
