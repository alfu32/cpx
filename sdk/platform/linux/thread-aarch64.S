.text
.global cplus_linux_set_thread_pointer
.type cplus_linux_set_thread_pointer, %function
cplus_linux_set_thread_pointer:
    msr tpidr_el0, x0
    ret
.size cplus_linux_set_thread_pointer, .-cplus_linux_set_thread_pointer

.global cplus_linux_clone_thread
.type cplus_linux_clone_thread, %function
cplus_linux_clone_thread:
    /* C ABI: flags, stack, tid, TLS, entry, context. */
    stp x19, x20, [sp, #-32]!
    str x30, [sp, #16]
    mov x19, x4
    mov x20, x5
    mov x4, x2
    mov x8, #220
    svc #0
    cbz x0, .Lchild
    ldr x30, [sp, #16]
    ldp x19, x20, [sp], #32
    ret
.Lchild:
    mov x0, x20
    blr x19
    mov x8, #93
    mov x0, xzr
    svc #0
    b .
.size cplus_linux_clone_thread, .-cplus_linux_clone_thread
