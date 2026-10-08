package cplus.compiler

import java.nio.file.Files
import java.util.concurrent.TimeUnit
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
            "platform_socket_receive_from", "platform_socket_shutdown", "platform_socket_close",
            "platform_network_parse_address", "platform_network_format_address", "platform_network_resolve"
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

    @Test
    fun linuxExecutesTcpAndUdpLoopbackThroughTheFreestandingPal() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val manifest = requireNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val target = TargetInfo(targetTriple = "linux-x86_64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val planResult = RuntimeLinker.plan(resolution, target)
        assertTrue(planResult.isSuccessful, planResult.diagnostics.joinToString())
        val plan = requireNotNull(planResult.plan)
        val directory = Files.createTempDirectory("cplus-runtime-network")
        val source = directory.resolve("network_test.c").also {
            Files.writeString(it, """
                #include "cplus_platform.h"

                static int send_all(long long socket, const char* bytes, unsigned long long length) {
                    unsigned long long sent = 0;
                    while (sent < length) {
                        long long result = platform_socket_send(socket, bytes + sent, length - sent);
                        if (result <= 0) return 1;
                        sent += (unsigned long long)result;
                    }
                    return 0;
                }

                static int receive_all(long long socket, char* bytes, unsigned long long length) {
                    unsigned long long received = 0;
                    while (received < length) {
                        long long result = platform_socket_receive(socket, bytes + received, length - received);
                        if (result <= 0) return 1;
                        received += (unsigned long long)result;
                    }
                    return 0;
                }

                static int bytes_equal(const char* left, const char* right, unsigned long long length) {
                    unsigned long long index;
                    for (index = 0; index < length; index++) if (left[index] != right[index]) return 0;
                    return 1;
                }

                static cplus_socket_address_t loopback_address(void) {
                    cplus_socket_address_t address = {0};
                    address.family = CPLUS_SOCKET_IPV4;
                    address.address[0] = 127;
                    address.address[3] = 1;
                    return address;
                }

                int main(int argc, char** argv) {
                    cplus_socket_address_t bind_address = loopback_address();
                    cplus_socket_address_t server_address;
                    cplus_socket_address_t client_address;
                    cplus_socket_address_t peer_address;
                    cplus_socket_address_t source_address;
                    cplus_socket_address_t invalid_address = loopback_address();
                    cplus_socket_address_t ipv6_bind = {0};
                    cplus_socket_address_t ipv6_server_address;
                    cplus_socket_address_t ipv6_peer_address;
                    cplus_socket_address_t ipv6_client_address;
                    long long listener;
                    long long client;
                    long long accepted;
                    long long udp_server;
                    long long udp_client;
                    long long ipv6_listener;
                    long long ipv6_client;
                    long long ipv6_accepted;
                    long long probe;
                    long long bad_socket;
                    long long refused_client;
                    long long result;
                    char receive_buffer[32];
                    const char request[] = "tcp-request";
                    const char response[] = "tcp-response";
                    const char datagram[] = "udp-ping";
                    const char acknowledgement[] = "udp-ack";
                    (void)argc;
                    (void)argv;

                    if (CPLUS_PAL_API_VERSION != 4) return 1;
                    if (platform_socket_open(0, CPLUS_SOCKET_STREAM) != CPLUS_PAL_UNSUPPORTED ||
                        platform_socket_open(CPLUS_SOCKET_IPV4, 0) != CPLUS_PAL_UNSUPPORTED ||
                        platform_socket_bind(-1, &bind_address) != CPLUS_PAL_INVALID_ARGUMENT ||
                        platform_socket_listen(-1, 1) != CPLUS_PAL_INVALID_ARGUMENT ||
                        platform_socket_listen(0, -1) != CPLUS_PAL_INVALID_ARGUMENT ||
                        platform_socket_connect(-1, &bind_address) != CPLUS_PAL_INVALID_ARGUMENT ||
                        platform_socket_get_address(-1, 0, &server_address) != CPLUS_PAL_INVALID_ARGUMENT ||
                        platform_socket_get_address(0, 2, &server_address) != CPLUS_PAL_INVALID_ARGUMENT ||
                        platform_socket_send(-1, (const void*)0, 0) != CPLUS_PAL_INVALID_ARGUMENT ||
                        platform_socket_send_to(-1, (const void*)0, 0, &bind_address) != CPLUS_PAL_INVALID_ARGUMENT ||
                        platform_socket_receive_from(-1, (void*)0, 0, (cplus_socket_address_t*)0) != CPLUS_PAL_INVALID_ARGUMENT ||
                        platform_socket_shutdown(-1, CPLUS_SOCKET_SHUTDOWN_BOTH) != CPLUS_PAL_INVALID_ARGUMENT ||
                        platform_socket_close(-1) != CPLUS_PAL_INVALID_ARGUMENT) return 2;
                    invalid_address.reserved = 1;
                    bad_socket = platform_socket_open(CPLUS_SOCKET_IPV4, CPLUS_SOCKET_STREAM);
                    if (bad_socket < 0 || platform_socket_bind(bad_socket, &invalid_address) != CPLUS_PAL_INVALID_ARGUMENT ||
                        platform_socket_close(bad_socket) != 0) return 3;
                    invalid_address = loopback_address();
                    invalid_address.address[4] = 1;
                    bad_socket = platform_socket_open(CPLUS_SOCKET_IPV4, CPLUS_SOCKET_STREAM);
                    if (bad_socket < 0 || platform_socket_bind(bad_socket, &invalid_address) != CPLUS_PAL_INVALID_ARGUMENT ||
                        platform_socket_close(bad_socket) != 0) return 21;

                    listener = platform_socket_open(CPLUS_SOCKET_IPV4, CPLUS_SOCKET_STREAM);
                    if (listener < 0 || platform_socket_bind(listener, &bind_address) != 0 ||
                        platform_socket_get_address(listener, 0, &server_address) != 0 ||
                        server_address.port == 0 || platform_socket_listen(listener, 4) != 0) return 4;
                    client = platform_socket_open(CPLUS_SOCKET_IPV4, CPLUS_SOCKET_STREAM);
                    if (client < 0 || platform_socket_connect(client, &server_address) != 0) return 5;
                    accepted = platform_socket_accept(listener, &peer_address);
                    if (accepted < 0 || peer_address.family != CPLUS_SOCKET_IPV4 ||
                        platform_socket_get_address(client, 0, &client_address) != 0 ||
                        peer_address.port != client_address.port) return 6;
                    if (platform_socket_send(client, (const void*)0, 1) != CPLUS_PAL_INVALID_ARGUMENT ||
                        platform_socket_send(client, request, 0x80000000ULL) != CPLUS_PAL_INVALID_ARGUMENT ||
                        platform_socket_receive(client, (void*)0, 1) != CPLUS_PAL_INVALID_ARGUMENT ||
                        platform_socket_receive(client, (void*)0, 0) != 0) return 27;
                    if (send_all(client, request, sizeof(request) - 1) != 0 ||
                        receive_all(accepted, receive_buffer, sizeof(request) - 1) != 0 ||
                        !bytes_equal(receive_buffer, request, sizeof(request) - 1)) return 7;
                    if (send_all(accepted, response, sizeof(response) - 1) != 0 ||
                        receive_all(client, receive_buffer, sizeof(response) - 1) != 0 ||
                        !bytes_equal(receive_buffer, response, sizeof(response) - 1)) return 8;
                    if (platform_socket_shutdown(client, CPLUS_SOCKET_SHUTDOWN_SEND) != 0 ||
                        platform_socket_receive(accepted, receive_buffer, sizeof(receive_buffer)) != 0) return 9;
                    if (platform_socket_close(client) != 0 || platform_socket_close(accepted) != 0 ||
                        platform_socket_close(listener) != 0) return 10;

                    udp_server = platform_socket_open(CPLUS_SOCKET_IPV4, CPLUS_SOCKET_DATAGRAM);
                    if (udp_server < 0 || platform_socket_bind(udp_server, &bind_address) != 0 ||
                        platform_socket_get_address(udp_server, 0, &server_address) != 0 || server_address.port == 0) return 11;
                    udp_client = platform_socket_open(CPLUS_SOCKET_IPV4, CPLUS_SOCKET_DATAGRAM);
                    if (udp_client < 0) return 12;
                    result = platform_socket_send_to(udp_client, datagram, sizeof(datagram) - 1, &server_address);
                    if (result != (long long)(sizeof(datagram) - 1)) return 13;
                    result = platform_socket_receive_from(udp_server, receive_buffer, sizeof(receive_buffer), &source_address);
                    if (result != (long long)(sizeof(datagram) - 1) || source_address.family != CPLUS_SOCKET_IPV4 ||
                        source_address.port == 0 || !bytes_equal(receive_buffer, datagram, sizeof(datagram) - 1)) return 14;
                    result = platform_socket_send_to(udp_server, acknowledgement, sizeof(acknowledgement) - 1, &source_address);
                    if (result != (long long)(sizeof(acknowledgement) - 1)) return 15;
                    result = platform_socket_receive_from(udp_client, receive_buffer, sizeof(receive_buffer), &source_address);
                    if (result != (long long)(sizeof(acknowledgement) - 1) ||
                        !bytes_equal(receive_buffer, acknowledgement, sizeof(acknowledgement) - 1)) return 16;
                    if (platform_socket_close(udp_client) != 0 || platform_socket_close(udp_server) != 0) return 17;

                    ipv6_bind.family = CPLUS_SOCKET_IPV6;
                    ipv6_bind.address[15] = 1;
                    ipv6_listener = platform_socket_open(CPLUS_SOCKET_IPV6, CPLUS_SOCKET_STREAM);
                    if (ipv6_listener < 0 || platform_socket_bind(ipv6_listener, &ipv6_bind) != 0 ||
                        platform_socket_get_address(ipv6_listener, 0, &ipv6_server_address) != 0 ||
                        ipv6_server_address.family != CPLUS_SOCKET_IPV6 || ipv6_server_address.port == 0 ||
                        platform_socket_listen(ipv6_listener, 2) != 0) return 22;
                    ipv6_client = platform_socket_open(CPLUS_SOCKET_IPV6, CPLUS_SOCKET_STREAM);
                    if (ipv6_client < 0 || platform_socket_connect(ipv6_client, &ipv6_server_address) != 0) return 23;
                    ipv6_accepted = platform_socket_accept(ipv6_listener, &ipv6_peer_address);
                    if (ipv6_accepted < 0 || ipv6_peer_address.family != CPLUS_SOCKET_IPV6 ||
                        platform_socket_get_address(ipv6_client, 0, &ipv6_client_address) != 0 ||
                        ipv6_peer_address.port != ipv6_client_address.port ||
                        send_all(ipv6_client, request, sizeof(request) - 1) != 0 ||
                        receive_all(ipv6_accepted, receive_buffer, sizeof(request) - 1) != 0 ||
                        !bytes_equal(receive_buffer, request, sizeof(request) - 1)) return 24;
                    if (platform_socket_close(ipv6_client) != 0 || platform_socket_close(ipv6_accepted) != 0 ||
                        platform_socket_close(ipv6_listener) != 0) return 25;

                    probe = platform_socket_open(CPLUS_SOCKET_IPV4, CPLUS_SOCKET_STREAM);
                    if (probe < 0 || platform_socket_bind(probe, &bind_address) != 0 ||
                        platform_socket_get_address(probe, 0, &server_address) != 0 ||
                        platform_socket_close(probe) != 0) return 18;
                    refused_client = platform_socket_open(CPLUS_SOCKET_IPV4, CPLUS_SOCKET_STREAM);
                    if (refused_client < 0) return 19;
                    result = platform_socket_connect(refused_client, &server_address);
                    if (result != CPLUS_PAL_NETWORK_ERROR || platform_socket_close(refused_client) != 0) return 20;
                    return 0;
                }
            """.trimIndent())
        }
        val executable = directory.resolve("network_test")
        try {
            val compile = ProcessBuilder(
                listOf("cc", "-std=c17") + plan.compilerFlags +
                    listOf("-I", resolution.layout.libcInclude.toString(), "-I", resolution.layout.runtimeInclude.toString()) +
                    listOf(source.toString()) + plan.runtimeSources.map { it.toString() } +
                    plan.startupSources.map { it.toString() } + plan.linkerFlags + listOf("-o", executable.toString())
            ).redirectErrorStream(true).start()
            val compileOutput = compile.inputStream.bufferedReader().readText()
            assertEquals(0, compile.waitFor(), compileOutput)
            val undefined = ProcessBuilder("nm", "-u", executable.toString()).start()
            val undefinedOutput = undefined.inputStream.bufferedReader().readText()
            assertEquals(0, undefined.waitFor(), undefinedOutput)
            assertTrue(undefinedOutput.isBlank(), undefinedOutput)
            val run = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
            if (!run.waitFor(20, TimeUnit.SECONDS)) {
                run.destroyForcibly()
                run.waitFor(2, TimeUnit.SECONDS)
                throw AssertionError("network fixture timed out; artifacts at $directory")
            }
            val runOutput = run.inputStream.bufferedReader().readText()
            assertEquals(0, run.exitValue(), "network fixture failed with output '$runOutput'; artifacts at $directory")
        } finally {
            Files.deleteIfExists(executable)
            Files.deleteIfExists(source)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun windowsFreestandingProductLoadsWinsockDynamically() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val crossToolsAvailable = listOf("x86_64-w64-mingw32-gcc", "x86_64-w64-mingw32-objdump").all { tool ->
            runCatching { ProcessBuilder(tool, "--version").start().waitFor() == 0 }.getOrDefault(false)
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(crossToolsAvailable)
        val manifest = requireNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val target = TargetInfo(targetTriple = "windows-x86_64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val planResult = RuntimeLinker.plan(resolution, target)
        assertTrue(planResult.isSuccessful, planResult.diagnostics.joinToString())
        val directory = Files.createTempDirectory("cplus-runtime-windows-network")
        val windowsNetworkSource = Files.readString(resolution.layout.platformSource.resolve("network.c"))
        listOf("GetAddrInfoW", "FreeAddrInfoW", "wide_hostname[ascii_length]").forEach {
            assertTrue(it in windowsNetworkSource, "Windows resolver source is missing $it")
        }
        val source = directory.resolve("windows_network_test.c").also {
            Files.writeString(it, """
                #include "cplus_platform.h"
                __declspec(dllimport) __declspec(noreturn) void __stdcall ExitProcess(unsigned long status);

                void mainCRTStartup(void) {
                    unsigned long long count = 0;
                    int status = platform_socket_open(0, CPLUS_SOCKET_STREAM) == CPLUS_PAL_UNSUPPORTED &&
                        platform_network_resolve((const char*)0, CPLUS_SOCKET_ANY_FAMILY, 0,
                            (cplus_socket_address_t*)0, 0, &count) == CPLUS_PAL_INVALID_ARGUMENT ? 0 : 1;
                    ExitProcess((unsigned long)status);
                }
            """.trimIndent())
        }
        val executable = directory.resolve("windows_network_test.exe")
        try {
            val compile = ProcessBuilder(
                listOf(
                    "x86_64-w64-mingw32-gcc", "-std=c17", "-nostdlib", "-nodefaultlibs",
                    "-nostartfiles", "-ffreestanding", "-fno-builtin", "-fno-stack-protector",
                    "-Wall", "-Wextra", "-Werror",
                    "-Wl,--entry,mainCRTStartup", "-Wl,--subsystem,console",
                    "-I", resolution.layout.runtimeInclude.toString(), source.toString(),
                    resolution.layout.platformSource.resolve("network.c").toString(),
                    resolution.layout.runtimeSource.resolve("net_address.c").toString(), "-lkernel32",
                    "-o", executable.toString()
                )
            ).redirectErrorStream(true).start()
            val compileOutput = compile.inputStream.bufferedReader().readText()
            assertEquals(0, compile.waitFor(), compileOutput)

            val inspect = ProcessBuilder("x86_64-w64-mingw32-objdump", "-p", executable.toString())
                .redirectErrorStream(true)
                .start()
            val importTable = inspect.inputStream.bufferedReader().readText()
            assertEquals(0, inspect.waitFor(), importTable)
            val normalizedImports = importTable.lowercase()
            assertTrue("kernel32.dll" in normalizedImports, importTable)
            assertTrue("loadlibraryexw" in normalizedImports, importTable)
            assertTrue("getprocaddress" in normalizedImports, importTable)
            assertTrue("initonceexecuteonce" in normalizedImports, importTable)
            assertTrue("heapalloc" in normalizedImports, importTable)
            assertTrue("heapfree" in normalizedImports, importTable)
            assertTrue("getaddrinfow" !in normalizedImports, importTable)
            assertTrue("freeaddrinfow" !in normalizedImports, importTable)
            assertTrue("ws2_32.dll" !in normalizedImports, importTable)
        } finally {
            Files.deleteIfExists(executable)
            Files.deleteIfExists(source)
            Files.deleteIfExists(directory)
        }
    }
}
