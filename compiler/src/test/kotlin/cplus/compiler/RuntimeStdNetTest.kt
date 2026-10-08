package cplus.compiler

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.util.concurrent.TimeUnit

class RuntimeStdNetTest {
    @Test
    fun windowsStdNetResolverExecutesThroughProductionPal() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
            System.getProperty("os.name").contains("windows", ignoreCase = true)
        )
        val manifestPath = SdkManifestLocator.defaultManifestPath()
        val manifest = requireNotNull(SdkManifestLoader.load(manifestPath).manifest)
        val target = TargetInfo(targetTriple = "windows-x86_64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val plan = requireNotNull(RuntimeLinker.plan(resolution, target).plan)
        val root = manifestPath.toAbsolutePath().normalize().parent!!.parent!!
        val directory = Files.createTempDirectory("cplus-windows-std-net-resolver")
        val source = directory.resolve("main.cp").also {
            Files.writeString(it, """
                import {
                    std_net_address_t,
                    std_net_resolve,
                    STD_NET_FAMILY_ANY,
                    STD_NET_FAMILY_IPV4,
                    STD_NET_FAMILY_IPV6,
                    STD_NET_INVALID_ARGUMENT
                } from std.net;
                import { uint64_t } from c.stdint;

                int main() {
                    std_net_address_t addresses[8];
                    uint64_t count = 0;
                    if (std_net_resolve("localhost", STD_NET_FAMILY_ANY, 43127,
                            addresses, 8, &count) != 0 || count == 0 || count > 8) return 1;
                    for (uint64_t index = 0; index < count; index++) {
                        if ((addresses[index].family != STD_NET_FAMILY_IPV4 &&
                             addresses[index].family != STD_NET_FAMILY_IPV6) || addresses[index].port != 43127 ||
                            addresses[index].reserved != 0) return 2;
                    }
                    count = 91;
                    if (std_net_resolve((const char*)0, STD_NET_FAMILY_IPV4, 80,
                            addresses, 8, &count) != STD_NET_INVALID_ARGUMENT || count != 91) return 3;
                    return 0;
                }
            """.trimIndent())
        }
        val generatedC = directory.resolve("resolver.c")
        val executable = directory.resolve("resolver.exe")
        try {
            val compilation = CPlusCompiler().compile(
                CompileRequest(listOf(root.resolve("std/src/net.cp"), source), target)
            )
            assertTrue(compilation.isSuccessful, compilation.diagnostics.joinToString())
            Files.writeString(generatedC, compilation.generatedUnits.single().text)
            val link = LinkDriver.link(LinkRequest(generatedC, executable, target, resolution), plan)
            assertTrue(link.isSuccessful, link.output)
            val descriptor = resolution.targetDescriptor
                ?: requireNotNull(TargetRegistry.load(resolution.layout.abiDescriptor).descriptor)
            val dependencyAudit = RuntimeDependencyAuditor.inspect(executable, descriptor, target.buildProfile)
            assertTrue(dependencyAudit.isSuccessful, dependencyAudit.diagnostics.joinToString())
            val process = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor(2, TimeUnit.SECONDS)
                throw AssertionError("Windows resolver fixture timed out; artifacts at $directory")
            }
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.exitValue(), "Windows resolver fixture failed with output '$output'")
        } finally {
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

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
    fun cplusStdNetUdpFacadePreservesEmptyDatagramsAndSourceAddresses() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
            System.getProperty("os.name").contains("linux", ignoreCase = true)
        )
        val manifestPath = SdkManifestLocator.defaultManifestPath()
        val manifest = requireNotNull(SdkManifestLoader.load(manifestPath).manifest)
        val target = TargetInfo(targetTriple = "linux-x86_64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val plan = requireNotNull(RuntimeLinker.plan(resolution, target).plan)
        val root = manifestPath.toAbsolutePath().normalize().parent!!.parent!!
        val directory = Files.createTempDirectory("cplus-std-net-udp")
        val mainSource = directory.resolve("main.cp").also {
            Files.writeString(it, """
                import {
                    std_net_address_t,
                    std_net_bind,
                    std_net_close,
                    std_net_get_address,
                    std_net_open,
                    std_net_receive_from,
                    std_net_send_to,
                    std_net_socket_t,
                    STD_NET_FAMILY_IPV4,
                    STD_NET_INVALID_ARGUMENT,
                    STD_NET_SOCKET_DATAGRAM
                } from std.net;
                static int bytes_equal(const char* left, const char* right, unsigned int length) {
                    unsigned int index;
                    for (index = 0; index < length; index++) {
                        if (left[index] != right[index]) return 0;
                    }
                    return 1;
                }

                int main() {
                    std_net_address_t bind_address;
                    std_net_address_t server_address;
                    std_net_address_t source_address;
                    std_net_address_t unchanged;
                    std_net_socket_t server;
                    std_net_socket_t client;
                    char buffer[16];
                    const char empty_marker = 'E';
                    const char payload[] = "udp-payload";
                    const char optional_source[] = "no-source";
                    unsigned int index;

                    bind_address.family = STD_NET_FAMILY_IPV4;
                    bind_address.port = 0;
                    bind_address.reserved = 0;
                    bind_address.scope_id = 0;
                    for (index = 0; index < 16; index++) bind_address.address[index] = 0;
                    bind_address.address[0] = 127;
                    bind_address.address[3] = 1;
                    unchanged.family = 77;
                    unchanged.port = 1234;
                    unchanged.reserved = 0;
                    unchanged.scope_id = 99;
                    for (index = 0; index < 16; index++) unchanged.address[index] = 0x5a;

                    if (std_net_receive_from(-1, buffer, sizeof(buffer), &unchanged) != STD_NET_INVALID_ARGUMENT ||
                        unchanged.family != 77 || unchanged.port != 1234 || unchanged.scope_id != 99 ||
                        std_net_send_to(-1, &empty_marker, 0, &bind_address) != STD_NET_INVALID_ARGUMENT ||
                        std_net_send_to(1, &empty_marker, 0, (const std_net_address_t*)0) != STD_NET_INVALID_ARGUMENT) return 1;
                    server = std_net_open(STD_NET_FAMILY_IPV4, STD_NET_SOCKET_DATAGRAM);
                    if (server < 0 || std_net_bind(server, &bind_address) != 0 ||
                        std_net_get_address(server, 0, &server_address) != 0 || server_address.port == 0) return 2;
                    client = std_net_open(STD_NET_FAMILY_IPV4, STD_NET_SOCKET_DATAGRAM);
                    if (client < 0) return 3;

                    if (std_net_send_to(client, (const void*)0, 1, &server_address) != STD_NET_INVALID_ARGUMENT ||
                        std_net_send_to(client, payload, (unsigned long long)2147483648, &server_address) != STD_NET_INVALID_ARGUMENT ||
                        std_net_receive_from(server, (void*)0, 1, &source_address) != STD_NET_INVALID_ARGUMENT) return 4;
                    if (std_net_send_to(client, &empty_marker, 0, &server_address) != 0 ||
                        std_net_receive_from(server, (void*)0, 0, &source_address) != 0 ||
                        source_address.family != STD_NET_FAMILY_IPV4 || source_address.port == 0) return 5;
                    if (std_net_send_to(client, payload, sizeof(payload) - 1, &server_address) != sizeof(payload) - 1 ||
                        std_net_receive_from(server, buffer, sizeof(buffer), &source_address) != sizeof(payload) - 1 ||
                        !bytes_equal(buffer, payload, sizeof(payload) - 1) ||
                        source_address.family != STD_NET_FAMILY_IPV4 || source_address.port == 0) return 6;
                    if (std_net_send_to(client, optional_source, sizeof(optional_source) - 1, &server_address) !=
                            sizeof(optional_source) - 1 ||
                        std_net_receive_from(server, buffer, sizeof(buffer), (std_net_address_t*)0) !=
                            sizeof(optional_source) - 1 ||
                        !bytes_equal(buffer, optional_source, sizeof(optional_source) - 1)) return 7;
                    if (std_net_close(client) != 0 || std_net_close(server) != 0) return 8;
                    return 0;
                }
            """.trimIndent())
        }
        val generatedC = directory.resolve("std-net-udp.c")
        val executable = directory.resolve("std-net-udp")
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
            assertTrue(undefinedOutput.isBlank(), "UDP façade product imports host symbols: $undefinedOutput")

            val process = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor(2, TimeUnit.SECONDS)
                throw AssertionError("C+ std.net UDP fixture timed out; artifacts at $directory")
            }
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.exitValue(), "UDP façade fixture failed with output '$output'")
        } finally {
            Files.deleteIfExists(executable)
            Files.deleteIfExists(generatedC)
            Files.deleteIfExists(mainSource)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun cplusStdNetAddressFacadeParsesFormatsAndResolvesNumericAddresses() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
            System.getProperty("os.name").contains("linux", ignoreCase = true)
        )
        val manifestPath = SdkManifestLocator.defaultManifestPath()
        val manifest = requireNotNull(SdkManifestLoader.load(manifestPath).manifest)
        val target = TargetInfo(targetTriple = "linux-x86_64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val plan = requireNotNull(RuntimeLinker.plan(resolution, target).plan)
        val root = manifestPath.toAbsolutePath().normalize().parent!!.parent!!
        val directory = Files.createTempDirectory("cplus-std-net-address")
        val mainSource = directory.resolve("main.cp").also {
            Files.writeString(it, """
                import {
                    std_net_address_t,
                    std_net_family_t,
                    std_net_format_address,
                    std_net_parse_address,
                    std_net_resolve,
                    STD_NET_BUFFER_TOO_SMALL,
                    STD_NET_FAMILY_ANY,
                    STD_NET_FAMILY_IPV4,
                    STD_NET_FAMILY_IPV6,
                    STD_NET_INVALID_ARGUMENT,
                    STD_NET_UNSUPPORTED
                } from std.net;
                import { uint64_t } from c.stdint;

                static int equals_text(const char* left, const char* right) {
                    unsigned int index = 0;
                    while (left[index] != '\0' && right[index] != '\0') {
                        if (left[index] != right[index]) return 0;
                        index++;
                    }
                    return left[index] == right[index];
                }

                int main() {
                    std_net_address_t address;
                    std_net_address_t results[2];
                    char formatted[80];
                    char short_buffer[8];
                    uint64_t count = 99;
                    int index;

                    short_buffer[0] = 'Q';
                    short_buffer[1] = 'R';
                    short_buffer[2] = 'S';
                    short_buffer[3] = 'T';
                    short_buffer[4] = 'U';
                    short_buffer[5] = 'V';
                    short_buffer[6] = 'W';
                    short_buffer[7] = 0;

                    if (std_net_parse_address(STD_NET_FAMILY_IPV4, "192.0.2.1", &address) != 0 ||
                        address.family != STD_NET_FAMILY_IPV4 || address.port != 0 ||
                        address.reserved != 0 || address.scope_id != 0 ||
                        address.address[0] != 192 || address.address[1] != 0 ||
                        address.address[2] != 2 || address.address[3] != 1) return 1;
                    if (std_net_format_address(&address, formatted, sizeof(formatted)) != 9) return 20;
                    if (!equals_text(formatted, "192.0.2.1")) return 21;
                    if (std_net_format_address(&address, short_buffer, 4) != STD_NET_BUFFER_TOO_SMALL ||
                        short_buffer[0] != 'Q' || short_buffer[1] != 'R' ||
                        short_buffer[2] != 'S' || short_buffer[3] != 'T') return 3;
                    address.family = 77;
                    address.port = 4321;
                    address.scope_id = 123;
                    for (index = 0; index < 16; index++) address.address[index] = 0x6b;
                    if (std_net_parse_address(STD_NET_FAMILY_IPV4, "256.0.0.1", &address) !=
                            STD_NET_INVALID_ARGUMENT || address.family != 77 || address.port != 4321 ||
                        address.scope_id != 123 || address.address[0] != 0x6b || address.address[15] != 0x6b) return 4;
                    if (std_net_parse_address(STD_NET_FAMILY_IPV6, "2001:0DB8:0:0:0:0:2:1", &address) != 0 ||
                        std_net_format_address(&address, formatted, sizeof(formatted)) != 13 ||
                        !equals_text(formatted, "2001:db8::2:1")) return 5;

                    count = 99;
                    if (std_net_resolve("192.0.2.9", STD_NET_FAMILY_IPV4, 8080, results, 2, &count) != 0 ||
                        count != 1 || results[0].family != STD_NET_FAMILY_IPV4 || results[0].port != 8080 ||
                        results[0].address[0] != 192 || results[0].address[2] != 2 ||
                        results[0].address[3] != 9 || results[0].reserved != 0) return 6;
                    count = 77;
                    if (std_net_resolve("192.0.2.9", STD_NET_FAMILY_IPV4, 8080,
                            (std_net_address_t*)0, 0, &count) != STD_NET_BUFFER_TOO_SMALL || count != 1) return 7;
                    count = 77;
                    results[0].family = 66;
                    if (std_net_resolve("localhost", (std_net_family_t)99, 80, results, 1, &count) !=
                            STD_NET_UNSUPPORTED || count != 77 || results[0].family != 66) return 8;
                    if (std_net_resolve((const char*)0, STD_NET_FAMILY_ANY, 80, results, 1, &count) !=
                            STD_NET_INVALID_ARGUMENT || count != 77 || results[0].family != 66) return 9;
                    if (std_net_resolve("192.0.2.9", STD_NET_FAMILY_IPV4, 80,
                            (std_net_address_t*)0, 1, &count) != STD_NET_INVALID_ARGUMENT ||
                        count != 77 || results[0].family != 66) return 10;
                    if (std_net_resolve("192.0.2.9", STD_NET_FAMILY_IPV4, 80,
                            results, 1, (void*)0) != STD_NET_INVALID_ARGUMENT ||
                        count != 77 || results[0].family != 66) return 11;
                    return 0;
                }
            """.trimIndent())
        }
        val generatedC = directory.resolve("std-net-address.c")
        val executable = directory.resolve("std-net-address")
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
            assertTrue(undefinedOutput.isBlank(), "address façade product imports host symbols: $undefinedOutput")

            val process = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor(2, TimeUnit.SECONDS)
                throw AssertionError("C+ std.net address fixture timed out; artifacts at $directory")
            }
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.exitValue(), "address façade fixture failed with output '$output'")
        } finally {
            Files.deleteIfExists(executable)
            Files.deleteIfExists(generatedC)
            Files.deleteIfExists(mainSource)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun stdNetResolverFacadeCopiesBoundedResultsAndPreservesPalErrors() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
            System.getProperty("os.name").contains("linux", ignoreCase = true)
        )
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val directory = Files.createTempDirectory("cplus-std-net-resolver-bridge")
        val source = directory.resolve("resolver_bridge_test.c").also {
            Files.writeString(it, """
                #include "cplus_std_net.h"
                #include <stdlib.h>

                void* __cplus_alloc(unsigned long long size) { return malloc((size_t)size); }
                void __cplus_free(void* value) { free(value); }

                static int text_equals(const char* left, const char* right) {
                    unsigned int index = 0;
                    while (left[index] != '\0' && right[index] != '\0') {
                        if (left[index] != right[index]) return 0;
                        index++;
                    }
                    return left[index] == right[index];
                }

                int platform_network_resolve(
                    const char* hostname,
                    unsigned int family,
                    unsigned short port,
                    cplus_socket_address_t* addresses,
                    unsigned long long capacity,
                    unsigned long long* count) {
                    cplus_socket_address_t records[2] = {{0}};
                    unsigned int index;
                    if (!hostname || !count || (capacity > 0 && !addresses)) return CPLUS_PAL_INVALID_ARGUMENT;
                    if (family != CPLUS_SOCKET_ANY_FAMILY && family != CPLUS_SOCKET_IPV4 &&
                        family != CPLUS_SOCKET_IPV6) return CPLUS_PAL_UNSUPPORTED;
                    if (!text_equals(hostname, "fixture.test")) return CPLUS_PAL_NOT_FOUND;
                    records[0].family = CPLUS_SOCKET_IPV4;
                    records[0].port = port;
                    records[0].address[0] = 192;
                    records[0].address[2] = 2;
                    records[0].address[3] = 10;
                    records[1].family = CPLUS_SOCKET_IPV6;
                    records[1].port = port;
                    records[1].address[0] = 0x20;
                    records[1].address[1] = 0x01;
                    records[1].address[15] = 0x10;
                    *count = 2;
                    for (index = 0; index < 2 && (unsigned long long)index < capacity; index++) {
                        addresses[index] = records[index];
                    }
                    return capacity < 2 ? CPLUS_PAL_BUFFER_TOO_SMALL : 0;
                }

                int main(void) {
                    struct std_net_address_t addresses[2] = {{0}};
                    unsigned long long count = 99;
                    int status;
                    status = std_net_resolve("fixture.test", STD_NET_FAMILY_ANY, 443, addresses, 2, &count);
                    if (status != 0 || count != 2 || addresses[0].family != STD_NET_FAMILY_IPV4 ||
                        addresses[0].port != 443 || addresses[0].address[3] != 10 ||
                        addresses[1].family != STD_NET_FAMILY_IPV6 || addresses[1].port != 443 ||
                        addresses[1].address[15] != 0x10) return 1;

                    addresses[0].family = 55;
                    addresses[1].family = 66;
                    count = 77;
                    status = std_net_resolve("fixture.test", STD_NET_FAMILY_ANY, 80, addresses, 1, &count);
                    if (status != CPLUS_PAL_BUFFER_TOO_SMALL || count != 2 ||
                        addresses[0].family != STD_NET_FAMILY_IPV4 || addresses[0].port != 80 ||
                        addresses[1].family != 66) return 2;
                    count = 88;
                    status = std_net_resolve("fixture.test", STD_NET_FAMILY_ANY, 80,
                        (struct std_net_address_t*)0, 0, &count);
                    if (status != CPLUS_PAL_BUFFER_TOO_SMALL || count != 2) return 3;

                    addresses[0].family = 99;
                    count = 123;
                    if (std_net_resolve("missing.test", STD_NET_FAMILY_ANY, 80, addresses, 2, &count) !=
                            CPLUS_PAL_NOT_FOUND || count != 123 || addresses[0].family != 99) return 4;
                    if (std_net_resolve("fixture.test", (enum std_net_family_t)99, 80,
                            addresses, 2, &count) != CPLUS_PAL_UNSUPPORTED ||
                        count != 123 || addresses[0].family != 99) return 5;
                    if (std_net_resolve("fixture.test", STD_NET_FAMILY_ANY, 80,
                            (struct std_net_address_t*)0, 2, &count) != CPLUS_PAL_INVALID_ARGUMENT ||
                        count != 123 || addresses[0].family != 99) return 6;
                    return 0;
                }
            """.trimIndent())
        }
        val executable = directory.resolve("resolver-bridge-test")
        try {
            val compile = ProcessBuilder(
                "cc", "-std=c17", "-Wall", "-Wextra", "-Werror", "-ffunction-sections", "-fdata-sections",
                "-Wl,--gc-sections", "-I", root.resolve("runtime/include").toString(),
                root.resolve("runtime/src/net.c").toString(), source.toString(), "-o", executable.toString()
            ).redirectErrorStream(true).start()
            val compileOutput = compile.inputStream.bufferedReader().readText()
            assertEquals(0, compile.waitFor(), compileOutput)
            val process = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.waitFor(), "resolver facade fixture failed with output '$output'")
        } finally {
            Files.deleteIfExists(executable)
            Files.deleteIfExists(source)
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
