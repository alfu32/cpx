package cplus.compiler

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuntimeNetworkAddressTest {
    @Test
    fun freestandingAddressAndHostnameCodecsPassCanonicalAndBoundaryVectors() {
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val directory = Files.createTempDirectory("cplus-network-address")
        val source = directory.resolve("address_test.c").also {
            Files.writeString(it, """
                #include "cplus_platform.h"
                #include "cplus_network_internal.h"

                static int equal_text(const char* left, const char* right) {
                    unsigned int index = 0;
                    while (left[index] != '\0' && right[index] != '\0') {
                        if (left[index] != right[index]) return 0;
                        index++;
                    }
                    return left[index] == right[index];
                }

                static int roundtrip(unsigned int family, const char* input, const char* expected) {
                    cplus_socket_address_t address = {0};
                    char output[80];
                    long long length;
                    unsigned int expected_length = 0;
                    if (platform_network_parse_address(family, input, &address) != 0) return 1;
                    address.port = 443;
                    length = platform_network_format_address(&address, output, sizeof(output));
                    while (expected[expected_length] != '\0') expected_length++;
                    if (length != (long long)expected_length) return 2;
                    return equal_text(output, expected) ? 0 : 3;
                }

                static int invalid_address(unsigned int family, const char* text) {
                    cplus_socket_address_t address = {0};
                    return platform_network_parse_address(family, text, &address) == CPLUS_PAL_INVALID_ARGUMENT;
                }

                static int hostname_equals(const char* input, const char* expected) {
                    char output[256];
                    long long length = __cplus_network_hostname_to_ascii(input, output, sizeof(output));
                    unsigned int expected_length = 0;
                    while (expected[expected_length] != '\0') expected_length++;
                    return length == (long long)expected_length && equal_text(output, expected);
                }

                int main(void) {
                    cplus_socket_address_t address = {0};
                    char output[8] = {'X', 'Y', 'Z', 0, 0, 0, 0, 0};
                    char hostname_output[8] = {'Q', 'R', 'S', 0, 0, 0, 0, 0};
                    char boundary_name[256];
                    char boundary_output[254];
                    char overlong_label[65];
                    unsigned int lengths[4] = {63, 63, 63, 61};
                    unsigned int index;
                    unsigned int label;

                    if (roundtrip(CPLUS_SOCKET_IPV4, "192.0.2.1", "192.0.2.1") != 0) return 1;
                    if (roundtrip(CPLUS_SOCKET_IPV6, "2001:0DB8:0:0:0:0:2:1", "2001:db8::2:1") != 0) return 2;
                    if (roundtrip(CPLUS_SOCKET_IPV6, "::", "::") != 0) return 3;
                    if (roundtrip(CPLUS_SOCKET_IPV6, "2001:0:0:1:0:0:1:1", "2001::1:0:0:1:1") != 0) return 4;
                    if (roundtrip(CPLUS_SOCKET_IPV6, "::ffff:192.0.2.1", "::ffff:192.0.2.1") != 0) return 5;
                    if (roundtrip(CPLUS_SOCKET_IPV6, "fe80::1%42", "fe80::1%42") != 0) return 6;
                    if (platform_network_parse_address(CPLUS_SOCKET_IPV4, "192.0.2.1", &address) != 0 ||
                        address.port != 0 || address.reserved != 0 || address.scope_id != 0 ||
                        address.address[0] != 192 || address.address[3] != 1) return 7;
                    if (platform_network_parse_address(CPLUS_SOCKET_IPV6, "fe80::1%42", &address) != 0 ||
                        address.scope_id != 42 || address.port != 0 || address.reserved != 0) return 8;
                    if (!invalid_address(CPLUS_SOCKET_IPV4, "192.168.001.1") ||
                        !invalid_address(CPLUS_SOCKET_IPV4, "256.0.0.1") ||
                        !invalid_address(CPLUS_SOCKET_IPV4, "192.0.2") ||
                        !invalid_address(CPLUS_SOCKET_IPV6, "1::2::3") ||
                        !invalid_address(CPLUS_SOCKET_IPV6, "fe80::1%4294967296") ||
                        !invalid_address(CPLUS_SOCKET_IPV6, "fe80::1%eth0")) return 9;
                    if (platform_network_parse_address(CPLUS_SOCKET_IPV4, "192.0.2.1", &address) != 0) return 10;
                    if (platform_network_format_address(&address, output, 2) != CPLUS_PAL_BUFFER_TOO_SMALL ||
                        output[0] != 'X' || output[1] != 'Y' || output[2] != 'Z') return 11;

                    if (!hostname_equals("B\303\274cher.Example.", "xn--bcher-kva.example") ||
                        !hostname_equals("EXAMPLE.COM", "example.com")) return 12;
                    if (__cplus_network_hostname_to_ascii("bad\300\257.example", hostname_output,
                        sizeof(hostname_output)) != CPLUS_PAL_INVALID_ARGUMENT) return 13;
                    if (__cplus_network_hostname_to_ascii("example.com", hostname_output, 4) !=
                        CPLUS_PAL_BUFFER_TOO_SMALL || hostname_output[0] != 'Q' ||
                        hostname_output[1] != 'R' || hostname_output[2] != 'S') return 14;
                    if (__cplus_network_hostname_to_ascii("a..example", hostname_output,
                        sizeof(hostname_output)) != CPLUS_PAL_INVALID_ARGUMENT) return 15;
                    for (index = 0; index < 64; index++) overlong_label[index] = 'a';
                    overlong_label[64] = '\0';
                    if (__cplus_network_hostname_to_ascii(overlong_label, hostname_output,
                        sizeof(hostname_output)) != CPLUS_PAL_INVALID_ARGUMENT) return 18;

                    index = 0;
                    for (label = 0; label < 4; label++) {
                        unsigned int offset;
                        if (label != 0) boundary_name[index++] = '.';
                        for (offset = 0; offset < lengths[label]; offset++) boundary_name[index++] = 'a';
                    }
                    boundary_name[index] = '\0';
                    if (index != 253 || __cplus_network_hostname_to_ascii(boundary_name,
                        boundary_output, sizeof(boundary_output)) != 253) return 16;
                    lengths[3] = 62;
                    index = 0;
                    for (label = 0; label < 4; label++) {
                        unsigned int offset;
                        if (label != 0) boundary_name[index++] = '.';
                        for (offset = 0; offset < lengths[label]; offset++) boundary_name[index++] = 'a';
                    }
                    boundary_name[index] = '\0';
                    if (index != 254 || __cplus_network_hostname_to_ascii(boundary_name,
                        boundary_output, sizeof(boundary_output)) != CPLUS_PAL_INVALID_ARGUMENT) return 17;
                    return 0;
                }
            """.trimIndent())
        }
        val objectFile = directory.resolve("net_address.o")
        val executable = directory.resolve("address_test")
        try {
            val compileObject = ProcessBuilder(
                "cc", "-std=c17", "-Wall", "-Wextra", "-Werror", "-ffreestanding", "-fno-builtin",
                "-I", root.resolve("runtime/include").toString(), "-c",
                root.resolve("runtime/src/net_address.c").toString(), "-o", objectFile.toString()
            ).redirectErrorStream(true).start()
            val objectOutput = compileObject.inputStream.bufferedReader().readText()
            assertEquals(0, compileObject.waitFor(), objectOutput)

            val undefinedSymbols = ProcessBuilder("nm", "-u", objectFile.toString()).start()
            val undefinedOutput = undefinedSymbols.inputStream.bufferedReader().readText()
            assertEquals(0, undefinedSymbols.waitFor(), undefinedOutput)
            assertTrue(undefinedOutput.isBlank(), "codec must not import host-runtime symbols: $undefinedOutput")

            val compile = ProcessBuilder(
                "cc", "-std=c17", "-Wall", "-Wextra", "-Werror",
                "-I", root.resolve("runtime/include").toString(), source.toString(),
                root.resolve("runtime/src/net_address.c").toString(), "-o", executable.toString()
            ).redirectErrorStream(true).start()
            val compileOutput = compile.inputStream.bufferedReader().readText()
            assertEquals(0, compile.waitFor(), compileOutput)

            val run = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
            val runOutput = run.inputStream.bufferedReader().readText()
            assertEquals(0, run.waitFor(), "codec fixture failed with output '$runOutput'; artifacts at $directory")
        } finally {
            Files.deleteIfExists(executable)
            Files.deleteIfExists(objectFile)
            Files.deleteIfExists(source)
            Files.deleteIfExists(directory)
        }
    }
}
