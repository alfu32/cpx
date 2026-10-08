.text
.globl _start
.type _start, @function
_start:
    xor %rbp, %rbp
    mov (%rsp), %rdi
    lea 8(%rsp), %rsi
    lea 16(%rsp,%rdi,8), %rdx
    call __cplus_start
    mov %eax, %edi
    call platform_process_exit
    hlt
.size _start, .-_start
.section .note.GNU-stack,"",@progbits
