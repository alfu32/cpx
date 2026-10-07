/* Minimal C17 stdio compatibility for the self-hosted profile. */
#if defined(_MSC_VER)
#include <stdarg.h>
typedef va_list __cplus_va_list;
#define __cplus_va_arg va_arg
#define __cplus_va_start va_start
#define __cplus_va_end va_end
#else
typedef __builtin_va_list __cplus_va_list;
#define __cplus_va_arg __builtin_va_arg
#define __cplus_va_start __builtin_va_start
#define __cplus_va_end __builtin_va_end
#endif
extern const char* __cplus_vformat(const char* format, __cplus_va_list arguments);
extern long platform_write_stdout(const char* buffer, unsigned long length);

static unsigned long __cplus_length(const char* text) {
    unsigned long length = 0;
    while (text[length] != 0) length++;
    return length;
}

static long __cplus_write(long fd, const char* buffer, unsigned long length) {
    (void)fd;
    return platform_write_stdout(buffer, length);
}

int printf(const char* format, ...) {
    __cplus_va_list arguments;
    __cplus_va_start(arguments, format);
    const char* text = __cplus_vformat(format, arguments);
    __cplus_va_end(arguments);
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
