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

typedef struct __cplus_file_marker { int kind; } __cplus_file_marker;
static __cplus_file_marker __cplus_stdin_marker = { 0 };
static __cplus_file_marker __cplus_stdout_marker = { 1 };
static __cplus_file_marker __cplus_stderr_marker = { 2 };
void* stdin = &__cplus_stdin_marker;
void* stdout = &__cplus_stdout_marker;
void* stderr = &__cplus_stderr_marker;

int vprintf(const char* format, __cplus_va_list arguments) {
    const char* text = __cplus_vformat(format, arguments);
    return (int)__cplus_write(1, text, __cplus_length(text));
}

int vsnprintf(char* buffer, unsigned long size, const char* format, __cplus_va_list arguments) {
    const char* text = __cplus_vformat(format, arguments);
    unsigned long length = __cplus_length(text);
    unsigned long index;
    if (buffer && size > 0) {
        unsigned long limit = length < size - 1 ? length : size - 1;
        for (index = 0; index < limit; index++) buffer[index] = text[index];
        buffer[limit] = 0;
    }
    return (int)length;
}

int vsprintf(char* buffer, const char* format, __cplus_va_list arguments) {
    return vsnprintf(buffer, ~0UL, format, arguments);
}

int vfprintf(void* stream, const char* format, __cplus_va_list arguments) {
    (void)stream;
    return vprintf(format, arguments);
}

int printf(const char* format, ...) {
    __cplus_va_list arguments;
    __cplus_va_start(arguments, format);
    const char* text = __cplus_vformat(format, arguments);
    __cplus_va_end(arguments);
    __cplus_write(1, text, __cplus_length(text));
    return (int)__cplus_length(text);
}

int fprintf(void* stream, const char* format, ...) {
    __cplus_va_list arguments;
    (void)stream;
    __cplus_va_start(arguments, format);
    int result = vprintf(format, arguments);
    __cplus_va_end(arguments);
    return result;
}

int sprintf(char* buffer, const char* format, ...) {
    __cplus_va_list arguments;
    __cplus_va_start(arguments, format);
    int result = vsprintf(buffer, format, arguments);
    __cplus_va_end(arguments);
    return result;
}

int snprintf(char* buffer, unsigned long size, const char* format, ...) {
    __cplus_va_list arguments;
    __cplus_va_start(arguments, format);
    int result = vsnprintf(buffer, size, format, arguments);
    __cplus_va_end(arguments);
    return result;
}

int puts(const char* text) {
    __cplus_write(1, text, __cplus_length(text));
    __cplus_write(1, "\n", 1);
    return 0;
}

int fputc(int value, void* stream) {
    char text[1];
    (void)stream;
    text[0] = (char)value;
    return __cplus_write(1, text, 1) == 1 ? value : -1;
}

int fgetc(void* stream) {
    (void)stream;
    return -1;
}

int fflush(void* stream) { (void)stream; return 0; }

/* The self-hosted stdio profile is unbuffered; retain an explicit libc hook. */
void __cplus_flush_streams(void) { }
