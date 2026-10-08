.text
.globl cplus_linux_clone_thread
.type cplus_linux_clone_thread, @function
cplus_linux_clone_thread:
    /* C ABI: flags, stack, tid, TLS, entry, context. */
    push %r12
    push %r13
    mov %r8, %r12
    mov %r9, %r13
    mov %rdx, %r10
    mov %rcx, %r8
    mov $56, %eax
    syscall
    test %rax, %rax
    jz .Lchild
    pop %r13
    pop %r12
    ret
.Lchild:
    mov %r13, %rdi
    call *%r12
    xor %edi, %edi
    mov $60, %eax
    syscall
    ud2
.size cplus_linux_clone_thread, .-cplus_linux_clone_thread
.section .note.GNU-stack,"",@progbits
