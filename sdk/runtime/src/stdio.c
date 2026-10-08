/* Minimal C17 stdio compatibility for the self-hosted profile. */
#include "cplus_platform.h"
#include <stddef.h>
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
extern long long platform_read_stdin(void* buffer, unsigned long long capacity);
extern long long platform_write_stdout(const char* buffer, unsigned long long length);
extern long long platform_write_stderr(const char* buffer, unsigned long long length);

static size_t __cplus_length(const char* text) {
    size_t length = 0;
    while (text[length] != 0) length++;
    return length;
}

typedef struct FILE { int kind; } FILE;
static FILE __cplus_stdin_marker = { 0 };
static FILE __cplus_stdout_marker = { 1 };
static FILE __cplus_stderr_marker = { 2 };
FILE* stdin = &__cplus_stdin_marker;
FILE* stdout = &__cplus_stdout_marker;
FILE* stderr = &__cplus_stderr_marker;

static long long __cplus_write(FILE* stream, const char* buffer, unsigned long long length) {
    unsigned long long written_total = 0;
    long long (*write_channel)(const char*, unsigned long long);
    if (stream == stdout) write_channel = platform_write_stdout;
    else if (stream == stderr) write_channel = platform_write_stderr;
    else {
        return CPLUS_PAL_INVALID_ARGUMENT;
    }
    while (written_total < length) {
        long long result = write_channel(buffer + written_total, length - written_total);
        if (result < 0) {
            return result;
        }
        if (result == 0) {
            return CPLUS_PAL_IO_ERROR;
        }
        written_total += (unsigned long long)result;
    }
    return (long long)written_total;
}

static int __cplus_stdio_result(long long result) {
    if (result < 0 || result > 0x7fffffffLL) return -1;
    return (int)result;
}

int vprintf(const char* format, __cplus_va_list arguments) {
    const char* text = __cplus_vformat(format, arguments);
    return __cplus_stdio_result(__cplus_write(stdout, text, __cplus_length(text)));
}

int vsnprintf(char* buffer, size_t size, const char* format, __cplus_va_list arguments) {
    const char* text = __cplus_vformat(format, arguments);
    size_t length = __cplus_length(text);
    size_t index;
    if (buffer && size > 0) {
        size_t limit = length < size - 1 ? length : size - 1;
        for (index = 0; index < limit; index++) buffer[index] = text[index];
        buffer[limit] = 0;
    }
    return (int)length;
}

int vsprintf(char* buffer, const char* format, __cplus_va_list arguments) {
    return vsnprintf(buffer, (size_t)-1, format, arguments);
}

int vfprintf(FILE* stream, const char* format, __cplus_va_list arguments) {
    const char* text = __cplus_vformat(format, arguments);
    return __cplus_stdio_result(__cplus_write(stream, text, __cplus_length(text)));
}

int printf(const char* format, ...) {
    __cplus_va_list arguments;
    __cplus_va_start(arguments, format);
    const char* text = __cplus_vformat(format, arguments);
    __cplus_va_end(arguments);
    return __cplus_stdio_result(__cplus_write(stdout, text, __cplus_length(text)));
}

int fprintf(FILE* stream, const char* format, ...) {
    __cplus_va_list arguments;
    __cplus_va_start(arguments, format);
    int result = vfprintf(stream, format, arguments);
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

int snprintf(char* buffer, size_t size, const char* format, ...) {
    __cplus_va_list arguments;
    __cplus_va_start(arguments, format);
    int result = vsnprintf(buffer, size, format, arguments);
    __cplus_va_end(arguments);
    return result;
}

int puts(const char* text) {
    unsigned long long length = __cplus_length(text);
    if (__cplus_write(stdout, text, length) < 0 || __cplus_write(stdout, "\n", 1) < 0) return -1;
    return 0;
}

int fputc(int value, FILE* stream) {
    char text[1];
    text[0] = (char)value;
    return __cplus_write(stream, text, 1) == 1 ? (unsigned char)value : -1;
}

int fgetc(FILE* stream) {
    unsigned char value;
    long long result;
    if (stream != stdin) {
        return -1;
    }
    result = platform_read_stdin(&value, 1);
    if (result < 0) {
        return -1;
    }
    return result == 0 ? -1 : (int)value;
}

int fflush(FILE* stream) {
    if (stream && stream != stdout && stream != stderr && stream != stdin) {
        return -1;
    }
    return 0;
}

/* The self-hosted stdio profile is unbuffered; retain an explicit libc hook. */
void __cplus_flush_streams(void) { }
