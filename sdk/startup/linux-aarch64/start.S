.text
.global _start
.type _start, %function
_start:
    mov x29, xzr
    mov x30, xzr
    bl __cplus_linux_initialize_main_tls
    cbnz x0, .Lexit
    ldr x0, [sp]
    add x1, sp, #8
    add x2, sp, #16
    add x2, x2, x0, lsl #3
    bl __cplus_start
    bl platform_process_exit
    b .
.Lexit:
    bl platform_process_exit
    b .
.size _start, .-_start
