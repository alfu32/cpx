#include "cplus_platform.h"

static long cplus_normalize_linux_result(long result) {
    long error;
    if (result >= 0) return result;
    error = -result;
    if (error == 2) return CPLUS_PAL_NOT_FOUND;
    if (error == 13) return CPLUS_PAL_ACCESS_DENIED;
    if (error == 22) return CPLUS_PAL_INVALID_ARGUMENT;
    if (error == 38) return CPLUS_PAL_UNSUPPORTED;
    return CPLUS_PAL_IO_ERROR;
}

#if defined(__x86_64__)
static long cplus_linux_syscall1(long number, long first) {
    register long result __asm__("rax") = number;
    __asm__ volatile("syscall" : "+a"(result) : "D"(first) : "rcx", "r11", "memory");
    return result;
}

static long cplus_linux_syscall2(long number, long first, long second) {
    register long result __asm__("rax") = number;
    __asm__ volatile("syscall" : "+a"(result) : "D"(first), "S"(second) : "rcx", "r11", "memory");
    return result;
}

static long cplus_linux_syscall3(long number, long first, long second, long third) {
    register long result __asm__("rax") = number;
    __asm__ volatile("syscall" : "+a"(result) : "D"(first), "S"(second), "d"(third) : "rcx", "r11", "memory");
    return result;
}

static long cplus_linux_syscall4(long number, long first, long second, long third, long fourth) {
    register long result __asm__("rax") = number;
    register long fourth_register __asm__("r10") = fourth;
    __asm__ volatile("syscall" : "+a"(result) : "D"(first), "S"(second), "d"(third), "r"(fourth_register) : "rcx", "r11", "memory");
    return result;
}

static long cplus_linux_syscall6(long number, long first, long second, long third, long fourth, long fifth, long sixth) {
    register long result __asm__("rax") = number;
    register long fourth_register __asm__("r10") = fourth;
    register long fifth_register __asm__("r8") = fifth;
    register long sixth_register __asm__("r9") = sixth;
    __asm__ volatile("syscall" : "+a"(result) : "D"(first), "S"(second), "d"(third), "r"(fourth_register), "r"(fifth_register), "r"(sixth_register) : "rcx", "r11", "memory");
    return result;
}
#elif defined(__aarch64__)
static long cplus_linux_syscall1(long number, long first) {
    register long result __asm__("x0") = first;
    register long syscall_number __asm__("x8") = number;
    __asm__ volatile("svc 0" : "+r"(result) : "r"(syscall_number) : "memory");
    return result;
}

static long cplus_linux_syscall2(long number, long first, long second) {
    register long result __asm__("x0") = first;
    register long second_register __asm__("x1") = second;
    register long syscall_number __asm__("x8") = number;
    __asm__ volatile("svc 0" : "+r"(result) : "r"(second_register), "r"(syscall_number) : "memory");
    return result;
}

static long cplus_linux_syscall3(long number, long first, long second, long third) {
    register long result __asm__("x0") = first;
    register long second_register __asm__("x1") = second;
    register long third_register __asm__("x2") = third;
    register long syscall_number __asm__("x8") = number;
    __asm__ volatile("svc 0" : "+r"(result) : "r"(second_register), "r"(third_register), "r"(syscall_number) : "memory");
    return result;
}

static long cplus_linux_syscall4(long number, long first, long second, long third, long fourth) {
    register long result __asm__("x0") = first;
    register long second_register __asm__("x1") = second;
    register long third_register __asm__("x2") = third;
    register long fourth_register __asm__("x3") = fourth;
    register long syscall_number __asm__("x8") = number;
    __asm__ volatile("svc 0" : "+r"(result) : "r"(second_register), "r"(third_register), "r"(fourth_register), "r"(syscall_number) : "memory");
    return result;
}

static long cplus_linux_syscall6(long number, long first, long second, long third, long fourth, long fifth, long sixth) {
    register long result __asm__("x0") = first;
    register long second_register __asm__("x1") = second;
    register long third_register __asm__("x2") = third;
    register long fourth_register __asm__("x3") = fourth;
    register long fifth_register __asm__("x4") = fifth;
    register long sixth_register __asm__("x5") = sixth;
    register long syscall_number __asm__("x8") = number;
    __asm__ volatile("svc 0" : "+r"(result) : "r"(second_register), "r"(third_register), "r"(fourth_register), "r"(fifth_register), "r"(sixth_register), "r"(syscall_number) : "memory");
    return result;
}
#endif

void* platform_page_allocate(unsigned long long page_count) {
    unsigned long long bytes;
    if (page_count == 0 || page_count > 0x7fffffffffffffffULL / CPLUS_PAL_PAGE_SIZE) return (void*)0;
    bytes = page_count * CPLUS_PAL_PAGE_SIZE;
#if defined(__x86_64__)
    {
        long result = cplus_linux_syscall6(9, 0, (long)bytes, 3, 0x22, -1, 0);
        return result < 0 ? (void*)0 : (void*)result;
    }
#elif defined(__aarch64__)
    {
        long result = cplus_linux_syscall6(222, 0, (long)bytes, 3, 0x22, -1, 0);
        return result < 0 ? (void*)0 : (void*)result;
    }
#else
    (void)bytes;
    return (void*)0;
#endif
}

int platform_page_release(void* address, unsigned long long page_count) {
    unsigned long long bytes;
    if (!address || page_count == 0 || page_count > 0x7fffffffffffffffULL / CPLUS_PAL_PAGE_SIZE) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    bytes = page_count * CPLUS_PAL_PAGE_SIZE;
#if defined(__x86_64__)
    return (int)cplus_normalize_linux_result(cplus_linux_syscall2(11, (long)address, (long)bytes));
#elif defined(__aarch64__)
    return (int)cplus_normalize_linux_result(cplus_linux_syscall2(215, (long)address, (long)bytes));
#else
    (void)bytes;
    return (int)CPLUS_PAL_UNSUPPORTED;
#endif
}

long platform_write_stdout(const char* buffer, unsigned long length) {
#if defined(__x86_64__)
    register long first __asm__("rdi") = 1;
    register const char* second __asm__("rsi") = buffer;
    register unsigned long third __asm__("rdx") = length;
    register long number __asm__("rax") = 1;
    __asm__ volatile("syscall" : "+a"(number) : "D"(first), "S"(second), "d"(third) : "rcx", "r11", "memory");
    return number;
#elif defined(__aarch64__)
    return cplus_linux_syscall3(64, 1, (long)buffer, (long)length);
#else
    (void)buffer; (void)length;
    return -38;
#endif
}

long long platform_file_open(const char* path, unsigned long long mode) {
    long access;
    long flags = 0;
    if (!path || (mode & (CPLUS_FILE_READ | CPLUS_FILE_WRITE)) == 0) return CPLUS_PAL_INVALID_ARGUMENT;
    access = (mode & CPLUS_FILE_WRITE) ? ((mode & CPLUS_FILE_READ) ? 2 : 1) : 0;
    if (access == 2) flags |= 2;
    else if (access == 1) flags |= 1;
    if (mode & CPLUS_FILE_CREATE) flags |= 64;
    if (mode & CPLUS_FILE_TRUNCATE) flags |= 512;
#if defined(__x86_64__)
    return cplus_normalize_linux_result(cplus_linux_syscall4(257, -100, (long)path, flags, 0666));
#elif defined(__aarch64__)
    return cplus_normalize_linux_result(cplus_linux_syscall4(56, -100, (long)path, flags, 0666));
#else
    (void)flags;
    return CPLUS_PAL_UNSUPPORTED;
#endif
}

long long platform_file_read(long long handle, void* buffer, unsigned long long length) {
    if (!buffer && length != 0) return CPLUS_PAL_INVALID_ARGUMENT;
#if defined(__x86_64__)
    if (length > 0x7fffffffffffffffULL) return CPLUS_PAL_INVALID_ARGUMENT;
    return cplus_normalize_linux_result(cplus_linux_syscall3(0, (long)handle, (long)buffer, (long)length));
#elif defined(__aarch64__)
    return cplus_normalize_linux_result(cplus_linux_syscall3(63, handle, (long)buffer, (long)length));
#else
    (void)handle; (void)buffer; (void)length;
    return CPLUS_PAL_UNSUPPORTED;
#endif
}

long long platform_file_write(long long handle, const void* buffer, unsigned long long length) {
    if (!buffer && length != 0) return CPLUS_PAL_INVALID_ARGUMENT;
#if defined(__x86_64__)
    if (length > 0x7fffffffffffffffULL) return CPLUS_PAL_INVALID_ARGUMENT;
    return cplus_normalize_linux_result(cplus_linux_syscall3(1, (long)handle, (long)buffer, (long)length));
#elif defined(__aarch64__)
    return cplus_normalize_linux_result(cplus_linux_syscall3(64, handle, (long)buffer, (long)length));
#else
    (void)handle; (void)buffer; (void)length;
    return CPLUS_PAL_UNSUPPORTED;
#endif
}

int platform_file_close(long long handle) {
#if defined(__x86_64__)
    return (int)cplus_normalize_linux_result(cplus_linux_syscall1(3, (long)handle));
#elif defined(__aarch64__)
    return (int)cplus_normalize_linux_result(cplus_linux_syscall1(57, (long)handle));
#else
    (void)handle;
    return (int)CPLUS_PAL_UNSUPPORTED;
#endif
}

int platform_file_rename(const char* source, const char* target) {
    if (!source || !target) return (int)CPLUS_PAL_INVALID_ARGUMENT;
#if defined(__x86_64__)
    return (int)cplus_normalize_linux_result(cplus_linux_syscall4(264, -100, (long)source, -100, (long)target));
#elif defined(__aarch64__)
    return (int)cplus_normalize_linux_result(cplus_linux_syscall4(276, -100, (long)source, -100, (long)target));
#else
    return (int)CPLUS_PAL_UNSUPPORTED;
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
