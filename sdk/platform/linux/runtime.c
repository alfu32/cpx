#include "cplus_platform.h"

long platform_write_stdout(const char* buffer, unsigned long length) {
#if defined(__x86_64__)
    register long first __asm__("rdi") = 1;
    register const char* second __asm__("rsi") = buffer;
    register unsigned long third __asm__("rdx") = length;
    register long number __asm__("rax") = 1;
    __asm__ volatile("syscall" : "+a"(number) : "D"(first), "S"(second), "d"(third) : "rcx", "r11", "memory");
    return number;
#elif defined(__aarch64__)
    register long first __asm__("x0") = 1;
    register const char* second __asm__("x1") = buffer;
    register unsigned long third __asm__("x2") = length;
    register long number __asm__("x8") = 64;
    __asm__ volatile("svc 0" : "+r"(number) : "r"(first), "r"(second), "r"(third) : "memory");
    return number;
#else
    (void)buffer; (void)length;
    return -38;
#endif
}

int platform_process_exit(int status) {
#if defined(__x86_64__)
    register long code __asm__("rdi") = status;
    register long number __asm__("rax") = 60;
    __asm__ volatile("syscall" : "+a"(number) : "D"(code) : "rcx", "r11", "memory");
#elif defined(__aarch64__)
    register long code __asm__("x0") = status;
    register long number __asm__("x8") = 93;
    __asm__ volatile("svc 0" : "+r"(number) : "r"(code) : "memory");
#else
    (void)status;
#endif
    for (;;) { }
    return status;
}
