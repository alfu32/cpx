package cplus.compiler

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuntimeStdNetTest {
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
