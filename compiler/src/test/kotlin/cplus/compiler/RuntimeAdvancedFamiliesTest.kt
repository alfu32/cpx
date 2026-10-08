package cplus.compiler

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class RuntimeAdvancedFamiliesTest {
    @Test
    fun linuxX8664ProvidesAtomicsUtf8WideConversionsAndSetjmp() {
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val directory = Files.createTempDirectory("cplus-advanced-families-e2e")
        val source = directory.resolve("advanced_families_test.c").also {
            Files.writeString(it, """
                #include <stdatomic.h>
                #include <setjmp.h>
                #include <wchar.h>
                #include <wctype.h>
                static jmp_buf context;
                static void jump_now(void) { longjmp(context, 7); }
                int main(void) {
                    atomic_int counter;
                    wchar_t wide[8];
                    char text[16];
                    atomic_init(&counter, 1);
                    if (atomic_fetch_add(&counter, 2) != 1 || atomic_load(&counter) != 3) return 1;
                    if (mbstowcs(wide, "A\342\202\254", 8) != 2 || wide[0] != 'A' || wide[1] != 0x20ac) return 2;
                    if (wcstombs(text, wide, sizeof(text)) != 4 || text[0] != 'A' || text[1] != (char)0xe2 || text[2] != (char)0x82 || text[3] != (char)0xac) return 3;
                    if (!iswalpha(wide[0]) || !iswdigit('7') || !iswspace(' ')) return 4;
                    if (setjmp(context) == 0) jump_now();
                    return atomic_load(&counter) == 3 ? 0 : 5;
                }
            """.trimIndent())
        }
        val executable = directory.resolve("advanced_families_test")
        val compile = ProcessBuilder(
            "cc", "-std=c17", "-I", root.resolve("libc/include").toString(),
            "-I", root.resolve("runtime/include").toString(), source.toString(),
            root.resolve("runtime/src/wide.c").toString(),
            root.resolve("runtime/src/wctype.c").toString(),
            root.resolve("runtime/src/setjmp-x86_64.S").toString(), "-o", executable.toString()
        ).redirectErrorStream(true).start()
        val output = compile.inputStream.bufferedReader().readText()
        assertEquals(0, compile.waitFor(), output)
        assertEquals(0, ProcessBuilder(executable.toString()).redirectErrorStream(true).start().waitFor())
    }

    @Test
    fun linuxAarch64LongjmpRestoresAapcs64CalleeSavedRegistersWhenQemuIsAvailable() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val runner = C17TargetRunner.commandPrefix("linux-aarch64")
        assumeTrue(runner != null, "AArch64 QEMU user-mode runner is unavailable")
        assumeTrue(runCCompiler("clang"), "Clang is unavailable")

        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val target = TargetInfo(targetTriple = "linux-aarch64")
        val manifest = requireNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val plan = requireNotNull(RuntimeLinker.plan(resolution, target).plan)
        val directory = Files.createTempDirectory("cplus-aarch64-setjmp-registers")
        val source = directory.resolve("setjmp_registers.c")
        val assembly = directory.resolve("setjmp_registers.S")
        val executable = directory.resolve("setjmp_registers")
        Files.writeString(source, """
            #include <setjmp.h>
            int cplus_test_context_registers(jmp_buf context);
            int main(void) {
                jmp_buf context;
                return cplus_test_context_registers(context);
            }
        """.trimIndent())
        Files.writeString(assembly, """
            .text
            .global cplus_test_context_registers
            .type cplus_test_context_registers, %function
            cplus_test_context_registers:
                stp x29, x30, [sp, #-176]!
                stp x19, x20, [sp, #16]
                stp x21, x22, [sp, #32]
                stp x23, x24, [sp, #48]
                stp x25, x26, [sp, #64]
                stp x27, x28, [sp, #80]
                stp d8, d9, [sp, #96]
                stp d10, d11, [sp, #112]
                stp d12, d13, [sp, #128]
                stp d14, d15, [sp, #144]
                str x0, [sp, #160]

                mov x19, #0x1919
                mov x20, #0x2020
                mov x21, #0x2121
                mov x22, #0x2222
                mov x23, #0x2323
                mov x24, #0x2424
                mov x25, #0x2525
                mov x26, #0x2626
                mov x27, #0x2727
                mov x28, #0x2828
                mov x29, #0x2929
                fmov d8, x19
                fmov d9, x20
                fmov d10, x21
                fmov d11, x22
                fmov d12, x23
                fmov d13, x24
                fmov d14, x25
                fmov d15, x26
                ldr x0, [sp, #160]
                bl setjmp
                cbnz w0, .Lresume

                mov x19, #0
                mov x20, #0
                mov x21, #0
                mov x22, #0
                mov x23, #0
                mov x24, #0
                mov x25, #0
                mov x26, #0
                mov x27, #0
                mov x28, #0
                mov x29, #0
                movi v8.2d, #0
                movi v9.2d, #0
                movi v10.2d, #0
                movi v11.2d, #0
                movi v12.2d, #0
                movi v13.2d, #0
                movi v14.2d, #0
                movi v15.2d, #0
                ldr x0, [sp, #160]
                mov x1, #7
                bl longjmp

            .Lresume:
                cmp w0, #7
                b.ne .Lfailure
                mov x12, #0x1919
                cmp x19, x12
                b.ne .Lfailure
                mov x12, #0x2020
                cmp x20, x12
                b.ne .Lfailure
                mov x12, #0x2121
                cmp x21, x12
                b.ne .Lfailure
                mov x12, #0x2222
                cmp x22, x12
                b.ne .Lfailure
                mov x12, #0x2323
                cmp x23, x12
                b.ne .Lfailure
                mov x12, #0x2424
                cmp x24, x12
                b.ne .Lfailure
                mov x12, #0x2525
                cmp x25, x12
                b.ne .Lfailure
                mov x12, #0x2626
                cmp x26, x12
                b.ne .Lfailure
                mov x12, #0x2727
                cmp x27, x12
                b.ne .Lfailure
                mov x12, #0x2828
                cmp x28, x12
                b.ne .Lfailure
                mov x12, #0x2929
                cmp x29, x12
                b.ne .Lfailure
                fmov x11, d8
                mov x12, #0x1919
                cmp x11, x12
                b.ne .Lfailure
                fmov x11, d9
                mov x12, #0x2020
                cmp x11, x12
                b.ne .Lfailure
                fmov x11, d10
                mov x12, #0x2121
                cmp x11, x12
                b.ne .Lfailure
                fmov x11, d11
                mov x12, #0x2222
                cmp x11, x12
                b.ne .Lfailure
                fmov x11, d12
                mov x12, #0x2323
                cmp x11, x12
                b.ne .Lfailure
                fmov x11, d13
                mov x12, #0x2424
                cmp x11, x12
                b.ne .Lfailure
                fmov x11, d14
                mov x12, #0x2525
                cmp x11, x12
                b.ne .Lfailure
                fmov x11, d15
                mov x12, #0x2626
                cmp x11, x12
                b.ne .Lfailure
                mov w0, #0
                b .Lrestore

            .Lfailure:
                mov w0, #1
            .Lrestore:
                ldp d8, d9, [sp, #96]
                ldp d10, d11, [sp, #112]
                ldp d12, d13, [sp, #128]
                ldp d14, d15, [sp, #144]
                ldp x19, x20, [sp, #16]
                ldp x21, x22, [sp, #32]
                ldp x23, x24, [sp, #48]
                ldp x25, x26, [sp, #64]
                ldp x27, x28, [sp, #80]
                ldp x29, x30, [sp], #176
                ret
            .size cplus_test_context_registers, .-cplus_test_context_registers
            .section .note.GNU-stack,"",%progbits
        """.trimIndent())

        try {
            val link = LinkDriver.link(
                LinkRequest(source, executable, target, resolution, sourceDependencies = listOf(assembly)),
                plan
            )
            assertTrue(link.isSuccessful, link.output)
            val descriptor = resolution.targetDescriptor
                ?: requireNotNull(TargetRegistry.load(resolution.layout.abiDescriptor).descriptor)
            val audit = RuntimeDependencyAuditor.inspect(executable, descriptor, target.buildProfile)
            assertTrue(audit.isSuccessful, audit.diagnostics.joinToString())
            val process = ProcessBuilder(runner!! + executable.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.waitFor(), output)
        } finally {
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    private fun runCCompiler(compiler: String): Boolean = runCatching {
        ProcessBuilder(compiler, "--version").start().waitFor() == 0
    }.getOrDefault(false)
}
