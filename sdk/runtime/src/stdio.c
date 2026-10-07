/* Minimal C17 stdio compatibility for the self-hosted Linux profile. */
typedef __builtin_va_list __cplus_va_list;
extern const char* __cplus_vformat(const char* format, __cplus_va_list arguments);

static unsigned long __cplus_length(const char* text) {
    unsigned long length = 0;
    while (text[length] != 0) length++;
    return length;
}

static long __cplus_write(long fd, const char* buffer, unsigned long length) {
#if defined(__x86_64__)
    long result;
    register long first __asm__("rdi") = fd;
    register const char* second __asm__("rsi") = buffer;
    register unsigned long third __asm__("rdx") = length;
    register long number __asm__("rax") = 1;
    __asm__ volatile("syscall" : "+a"(number), "=r"(result) : "D"(first), "S"(second), "d"(third) : "rcx", "r11", "memory");
    return number;
#else
    (void) fd; (void) buffer; (void) length;
    return -38;
#endif
}

int printf(const char* format, ...) {
    __cplus_va_list arguments;
    __builtin_va_start(arguments, format);
    const char* text = __cplus_vformat(format, arguments);
    __builtin_va_end(arguments);
    __cplus_write(1, text, __cplus_length(text));
    return (int)__cplus_length(text);
}

int puts(const char* text) {
    __cplus_write(1, text, __cplus_length(text));
    __cplus_write(1, "\n", 1);
    return 0;
}

int fflush(void* stream) { (void)stream; return 0; }

/* The self-hosted stdio profile is unbuffered; retain an explicit libc hook. */
void __cplus_flush_streams(void) { }
