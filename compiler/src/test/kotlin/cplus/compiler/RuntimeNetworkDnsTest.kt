package cplus.compiler

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class RuntimeNetworkDnsTest {
    @Test
    fun linuxResolverUsesBoundedDnsUdpTcpCnameAndResultOwnership() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val manifest = requireNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val target = TargetInfo(targetTriple = "linux-x86_64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val planResult = RuntimeLinker.plan(resolution, target)
        assertTrue(planResult.isSuccessful, planResult.diagnostics.joinToString())
        val plan = requireNotNull(planResult.plan)
        LoopbackDnsServer().use { dns ->
            val directory = Files.createTempDirectory("cplus-runtime-dns")
            val source = directory.resolve("dns_test.c").also {
                Files.writeString(it, """
                    #include "cplus_platform.h"
                    #include "cplus_linux_network_internal.h"

                    static int all_zero(const unsigned char* bytes, unsigned int count) {
                        unsigned int index;
                        for (index = 0; index < count; index++) if (bytes[index] != 0) return 0;
                        return 1;
                    }

                    int main(void) {
                        cplus_socket_address_t server = {0};
                        cplus_socket_address_t results[4];
                        cplus_socket_address_t numeric;
                        cplus_socket_address_t configured[3];
                        const char resolver_config[] =
                            "# resolver fixture\n"
                            "search ignored.test\n"
                            " nameserver 192.0.2.53 # local\n"
                            "nameserver invalid-address\n"
                            "nameserver 2001:db8::53\n"
                            "nameserver 192.0.2.53\n"
                            "nameserver 198.51.100.53\n"
                            "nameserver 203.0.113.53\n";
                        unsigned int configured_count = 0;
                        unsigned long long count = 0;
                        int status;
                        status = __cplus_linux_network_parse_resolv_conf(resolver_config,
                            sizeof(resolver_config) - 1, configured, &configured_count);
                        if (status != 0 || configured_count != 3 ||
                            configured[0].family != CPLUS_SOCKET_IPV4 || configured[0].port != 53 ||
                            configured[0].address[0] != 192 || configured[1].family != CPLUS_SOCKET_IPV6 ||
                            configured[1].port != 53 || configured[1].address[0] != 0x20 ||
                            configured[2].address[0] != 198) return 13;
                        status = __cplus_linux_network_parse_resolv_conf("search example.test\n",
                            20, configured, &configured_count);
                        if (status != CPLUS_PAL_NETWORK_ERROR || configured_count != 0) return 14;
                        server.family = CPLUS_SOCKET_IPV4;
                        server.port = ${dns.port}U;
                        server.address[0] = 127;
                        server.address[3] = 1;

                        status = platform_network_resolve("192.0.2.90", CPLUS_SOCKET_IPV4,
                            443, &numeric, 1, &count);
                        if (status != 0 || count != 1 || numeric.address[0] != 192 ||
                            numeric.address[2] != 2 || numeric.address[3] != 90 ||
                            numeric.port != 443 || numeric.reserved != 0) return 1;

                        status = __cplus_linux_network_resolve_with_nameservers("a.test",
                            CPLUS_SOCKET_IPV4, 8080, &server, 1, 500, results, 4, &count);
                        if (status != 0 || count != 1 || results[0].family != CPLUS_SOCKET_IPV4 ||
                            results[0].port != 8080 || results[0].address[0] != 192 ||
                            results[0].address[2] != 2 || results[0].address[3] != 10 ||
                            results[0].reserved != 0 || results[0].scope_id != 0 ||
                            !all_zero(results[0].address + 4, 12)) return 2;

                        status = __cplus_linux_network_resolve_with_nameservers("both.test",
                            CPLUS_SOCKET_ANY_FAMILY, 80, &server, 1, 500, results, 4, &count);
                        if (status != 0 || count != 2 || results[0].family != CPLUS_SOCKET_IPV4 ||
                            results[1].family != CPLUS_SOCKET_IPV6 || results[0].port != 80 ||
                            results[1].port != 80 || results[1].address[0] != 0x20 ||
                            results[1].address[1] != 0x01 || results[1].address[15] != 0x10) return 3;

                        status = __cplus_linux_network_resolve_with_nameservers("alias.test",
                            CPLUS_SOCKET_IPV4, 53, &server, 1, 500, results, 4, &count);
                        if (status != 0 || count != 1 || results[0].address[3] != 22) return 4;

                        status = __cplus_linux_network_resolve_with_nameservers("dupe.test",
                            CPLUS_SOCKET_IPV4, 53, &server, 1, 500, results, 4, &count);
                        if (status != 0 || count != 1 || results[0].address[3] != 30) return 5;
                        status = __cplus_linux_network_resolve_with_nameservers("dupe.test",
                            CPLUS_SOCKET_IPV4, 53, &server, 1, 500, (cplus_socket_address_t*)0, 0, &count);
                        if (status != CPLUS_PAL_BUFFER_TOO_SMALL || count != 1) return 6;

                        status = __cplus_linux_network_resolve_with_nameservers("tcp.test",
                            CPLUS_SOCKET_IPV4, 53, &server, 1, 1000, results, 4, &count);
                        if (status != 0 || count != 1 || results[0].address[3] != 40) return 7;

                        status = __cplus_linux_network_resolve_with_nameservers("nxdomain.test",
                            CPLUS_SOCKET_IPV4, 53, &server, 1, 500, results, 4, &count);
                        if (status != CPLUS_PAL_NOT_FOUND || count != 0) return 8;
                        status = __cplus_linux_network_resolve_with_nameservers("bad-id.test",
                            CPLUS_SOCKET_IPV4, 53, &server, 1, 500, results, 4, &count);
                        if (status != CPLUS_PAL_NETWORK_ERROR || count != 0) return 9;
                        status = __cplus_linux_network_resolve_with_nameservers("bad-question.test",
                            CPLUS_SOCKET_IPV4, 53, &server, 1, 500, results, 4, &count);
                        if (status != CPLUS_PAL_NETWORK_ERROR || count != 0) return 10;
                        status = __cplus_linux_network_resolve_with_nameservers("wrong-source.test",
                            CPLUS_SOCKET_IPV4, 53, &server, 1, 500, results, 4, &count);
                        if (status != CPLUS_PAL_NETWORK_ERROR || count != 0) return 11;
                        status = __cplus_linux_network_resolve_with_nameservers("timeout.test",
                            CPLUS_SOCKET_IPV4, 53, &server, 1, 100, results, 4, &count);
                        if (status != CPLUS_PAL_NETWORK_ERROR || count != 0) return 12;
                        return 0;
                    }
                """.trimIndent())
            }
            val executable = directory.resolve("dns_test")
            try {
                val compile = ProcessBuilder(
                    listOf("cc", "-std=c17") + plan.compilerFlags +
                        listOf("-I", resolution.layout.libcInclude.toString(),
                            "-I", resolution.layout.runtimeInclude.toString(),
                            "-I", resolution.layout.platformSource.toString()) +
                        listOf(source.toString()) + plan.runtimeSources.map { it.toString() } +
                        plan.startupSources.map { it.toString() } + plan.linkerFlags +
                        listOf("-o", executable.toString())
                ).redirectErrorStream(true).start()
                val compileOutput = compile.inputStream.bufferedReader().readText()
                assertEquals(0, compile.waitFor(), compileOutput)

                val undefined = ProcessBuilder("nm", "-u", executable.toString()).start()
                val undefinedOutput = undefined.inputStream.bufferedReader().readText()
                assertEquals(0, undefined.waitFor(), undefinedOutput)
                assertTrue(undefinedOutput.isBlank(), "DNS runtime must not import host symbols: $undefinedOutput")

                val process = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
                val completed = process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)
                if (!completed) process.destroyForcibly()
                val output = process.inputStream.bufferedReader().readText()
                assertTrue(completed, "DNS fixture exceeded its test watchdog; artifacts at $directory")
                assertEquals(0, process.exitValue(), "DNS fixture failed with output '$output'; artifacts at $directory")
            } finally {
                Files.deleteIfExists(executable)
                Files.deleteIfExists(source)
                Files.deleteIfExists(directory)
            }
        }
    }

    private class LoopbackDnsServer : AutoCloseable {
        private val running = AtomicBoolean(true)
        private val loopback = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
        private val udp = DatagramSocket(0, loopback).apply { soTimeout = 200 }
        private val tcp = ServerSocket(udp.localPort, 8, loopback).apply { soTimeout = 200 }
        private val wrongSource = DatagramSocket(0, loopback)
        val port: Int = udp.localPort
        private val udpThread = Thread(::serveUdp, "cplus-test-dns-udp").apply { isDaemon = true; start() }
        private val tcpThread = Thread(::serveTcp, "cplus-test-dns-tcp").apply { isDaemon = true; start() }

        private data class Query(val packet: ByteArray, val name: String, val type: Int, val questionEnd: Int)

        private fun parseQuery(packet: ByteArray, length: Int): Query? {
            if (length < 17) return null
            var cursor = 12
            val labels = ArrayList<String>()
            while (cursor < length) {
                val size = packet[cursor++].toInt() and 0xff
                if (size == 0) break
                if (size > 63 || cursor + size > length) return null
                labels += String(packet, cursor, size, Charsets.US_ASCII)
                cursor += size
            }
            if (cursor + 4 > length) return null
            val type = ((packet[cursor].toInt() and 0xff) shl 8) or (packet[cursor + 1].toInt() and 0xff)
            return Query(packet.copyOfRange(0, length), labels.joinToString(".").lowercase(), type, cursor + 4)
        }

        private fun encodedName(name: String): ByteArray {
            val output = ArrayList<Byte>()
            for (label in name.split('.')) {
                val bytes = label.toByteArray(Charsets.US_ASCII)
                output += bytes.size.toByte()
                bytes.forEach(output::add)
            }
            output += 0.toByte()
            return output.toByteArray()
        }

        private fun answer(query: Query, overTcp: Boolean): ByteArray? {
            if (query.name == "timeout.test") return null
            val records = ArrayList<ByteArray>()
            var flags = 0x8180
            when (query.name) {
                "nxdomain.test" -> flags = 0x8183
                "tcp.test" -> if (!overTcp) flags = 0x8380 else records += aRecord(40)
                "alias.test" -> if (query.type == 1) records += cnameRecord("real.test")
                "real.test" -> if (query.type == 1) records += aRecord(22)
                "a.test" -> if (query.type == 1) records += aRecord(10)
                "both.test" -> if (query.type == 1) records += aRecord(20) else if (query.type == 28) records += aaaaRecord()
                "dupe.test" -> if (query.type == 1) {
                    records += aRecord(30)
                    records += aRecord(30)
                }
                "bad-id.test" -> records += aRecord(31)
                "bad-question.test" -> records += aRecord(32)
                "wrong-source.test" -> records += aRecord(33)
                else -> flags = 0x8183
            }
            val question = query.packet.copyOfRange(12, query.questionEnd)
            if (query.name == "bad-question.test") {
                val replacement = encodedName("other.test") + byteArrayOf(0, query.type.toByte(), 0, 1)
                return response(query, flags, replacement, records, badId = false)
            }
            return response(query, flags, question, records, badId = query.name == "bad-id.test")
        }

        private fun response(
            query: Query,
            flags: Int,
            question: ByteArray,
            records: List<ByteArray>,
            badId: Boolean
        ): ByteArray {
            val output = java.io.ByteArrayOutputStream()
            val data = DataOutputStream(output)
            val id = ((query.packet[0].toInt() and 0xff) shl 8) or (query.packet[1].toInt() and 0xff)
            data.writeShort(if (badId) id xor 0xffff else id)
            data.writeShort(flags)
            data.writeShort(1)
            data.writeShort(records.size)
            data.writeShort(0)
            data.writeShort(0)
            data.write(question)
            records.forEach { data.write(it) }
            return output.toByteArray()
        }

        private fun aRecord(lastOctet: Int): ByteArray {
            val output = java.io.ByteArrayOutputStream()
            val data = DataOutputStream(output)
            data.writeShort(0xc00c)
            data.writeShort(1)
            data.writeShort(1)
            data.writeInt(60)
            data.writeShort(4)
            data.write(byteArrayOf(192.toByte(), 0, 2, lastOctet.toByte()))
            return output.toByteArray()
        }

        private fun aaaaRecord(): ByteArray {
            val output = java.io.ByteArrayOutputStream()
            val data = DataOutputStream(output)
            data.writeShort(0xc00c)
            data.writeShort(28)
            data.writeShort(1)
            data.writeInt(60)
            data.writeShort(16)
            data.write(intArrayOf(0x20, 0x01, 0x0d, 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x10)
                .map { it.toByte() }.toByteArray())
            return output.toByteArray()
        }

        private fun cnameRecord(target: String): ByteArray {
            val encodedTarget = encodedName(target)
            val output = java.io.ByteArrayOutputStream()
            val data = DataOutputStream(output)
            data.writeShort(0xc00c)
            data.writeShort(5)
            data.writeShort(1)
            data.writeInt(60)
            data.writeShort(encodedTarget.size)
            data.write(encodedTarget)
            return output.toByteArray()
        }

        private fun serveUdp() {
            val buffer = ByteArray(2048)
            while (running.get()) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    udp.receive(packet)
                    val query = parseQuery(packet.data, packet.length) ?: continue
                    val reply = answer(query, overTcp = false) ?: continue
                    val destination = InetAddress.getByAddress(packet.address.address)
                    val response = DatagramPacket(reply, reply.size, destination, packet.port)
                    if (query.name == "wrong-source.test") wrongSource.send(response) else udp.send(response)
                } catch (_: java.net.SocketTimeoutException) {
                    // Periodically observe shutdown.
                } catch (error: SocketException) {
                    if (running.get()) throw error
                }
            }
        }

        private fun serveTcp() {
            while (running.get()) {
                try {
                    tcp.accept().use { client ->
                        val input = DataInputStream(client.getInputStream())
                        val output = DataOutputStream(client.getOutputStream())
                        val length = input.readUnsignedShort()
                        val packet = ByteArray(length)
                        input.readFully(packet)
                        val query = parseQuery(packet, length) ?: continue
                        val reply = answer(query, overTcp = true) ?: continue
                        output.writeShort(reply.size)
                        output.write(reply)
                        output.flush()
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    // Periodically observe shutdown.
                } catch (error: SocketException) {
                    if (running.get()) throw error
                }
            }
        }

        override fun close() {
            running.set(false)
            udp.close()
            tcp.close()
            wrongSource.close()
            udpThread.join(1000)
            tcpThread.join(1000)
        }
    }
}
