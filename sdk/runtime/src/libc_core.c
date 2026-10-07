#include <stddef.h>
#include "cplus_platform.h"

/* C17 compatibility core. This file is intentionally self-contained and is
   linked from the SDK, so C+ programs do not inherit the host libc. */
_Thread_local int errno;

static void cplus_set_errno(int value) { errno = value; }

int __cplus_set_errno_from_pal(long result) {
    if (result >= 0) {
        errno = 0;
        return 0;
    }
    if (result == CPLUS_PAL_INVALID_ARGUMENT) errno = 22;
    else if (result == CPLUS_PAL_NOT_FOUND) errno = 2;
    else if (result == CPLUS_PAL_ACCESS_DENIED) errno = 13;
    else if (result == CPLUS_PAL_UNSUPPORTED) errno = 38;
    else errno = 5;
    return -1;
}

void* memcpy(void* destination, const void* source, size_t size) {
    unsigned char* target = (unsigned char*)destination;
    const unsigned char* origin = (const unsigned char*)source;
    size_t index;
    for (index = 0; index < size; index++) target[index] = origin[index];
    return destination;
}

void* memmove(void* destination, const void* source, size_t size) {
    unsigned char* target = (unsigned char*)destination;
    const unsigned char* origin = (const unsigned char*)source;
    size_t index;
    if (target < origin) {
        for (index = 0; index < size; index++) target[index] = origin[index];
    } else if (target > origin) {
        index = size;
        while (index > 0) { index--; target[index] = origin[index]; }
    }
    return destination;
}

void* memset(void* destination, int value, size_t size) {
    unsigned char* target = (unsigned char*)destination;
    size_t index;
    for (index = 0; index < size; index++) target[index] = (unsigned char)value;
    return destination;
}

int memcmp(const void* left, const void* right, size_t size) {
    const unsigned char* a = (const unsigned char*)left;
    const unsigned char* b = (const unsigned char*)right;
    size_t index;
    for (index = 0; index < size; index++) {
        if (a[index] < b[index]) return -1;
        if (a[index] > b[index]) return 1;
    }
    return 0;
}

size_t strlen(const char* text) {
    size_t length = 0;
    while (text[length] != 0) length++;
    return length;
}

int strcmp(const char* left, const char* right) {
    size_t index = 0;
    while (left[index] != 0 && left[index] == right[index]) index++;
    return (int)(unsigned char)left[index] - (int)(unsigned char)right[index];
}

int strncmp(const char* left, const char* right, size_t size) {
    size_t index;
    for (index = 0; index < size; index++) {
        unsigned char a = (unsigned char)left[index];
        unsigned char b = (unsigned char)right[index];
        if (a != b || a == 0) return (int)a - (int)b;
    }
    return 0;
}

char* strcpy(char* destination, const char* source) {
    size_t index = 0;
    while ((destination[index] = source[index]) != 0) index++;
    return destination;
}

char* strncpy(char* destination, const char* source, size_t size) {
    size_t index = 0;
    while (index < size && source[index] != 0) {
        destination[index] = source[index];
        index++;
    }
    while (index < size) destination[index++] = 0;
    return destination;
}

char* strcat(char* destination, const char* source) {
    strcpy(destination + strlen(destination), source);
    return destination;
}

char* strchr(const char* text, int value) {
    size_t index = 0;
    while (text[index] != 0) {
        if ((unsigned char)text[index] == (unsigned char)value) return (char*)(text + index);
        index++;
    }
    return value == 0 ? (char*)(text + index) : (char*)0;
}

extern void* __cplus_alloc(unsigned long long size);
extern void* __cplus_calloc(unsigned long long count, unsigned long long size);
extern void* __cplus_realloc(void* value, unsigned long long size);
extern void* __cplus_alloc_aligned(unsigned long long alignment, unsigned long long size);
extern void __cplus_free(void* value);

void* malloc(size_t size) {
    void* result = __cplus_alloc((unsigned long long)size);
    if (!result) cplus_set_errno(12);
    return result;
}

void* calloc(size_t count, size_t size) {
    void* result = __cplus_calloc((unsigned long long)count, (unsigned long long)size);
    if (!result) cplus_set_errno(12);
    return result;
}

void* realloc(void* value, size_t size) {
    void* result = __cplus_realloc(value, (unsigned long long)size);
    if (!result && size != 0) cplus_set_errno(12);
    return result;
}

void* aligned_alloc(size_t alignment, size_t size) {
    void* result = __cplus_alloc_aligned((unsigned long long)alignment, (unsigned long long)size);
    if (!result) cplus_set_errno(12);
    return result;
}

void free(void* value) { __cplus_free(value); }

static int cplus_digit(int value) {
    if (value >= '0' && value <= '9') return value - '0';
    if (value >= 'a' && value <= 'z') return value - 'a' + 10;
    if (value >= 'A' && value <= 'Z') return value - 'A' + 10;
    return -1;
}

static int cplus_space(int value) {
    return value == ' ' || value == '\t' || value == '\n' || value == '\r' || value == '\f' || value == '\v';
}

long strtol(const char* text, char** end, int base) {
    const char* cursor = text;
    unsigned long long value = 0;
    int negative = 0;
    int digit;
    int digits = 0;
    while (cplus_space(*cursor)) cursor++;
    if (*cursor == '+' || *cursor == '-') { negative = *cursor == '-'; cursor++; }
    if (base == 0) {
        base = 10;
        if (cursor[0] == '0') {
            base = cursor[1] == 'x' || cursor[1] == 'X' ? 16 : 8;
            if (base == 16) cursor += 2;
        }
    } else if (base == 16 && cursor[0] == '0' && (cursor[1] == 'x' || cursor[1] == 'X')) {
        cursor += 2;
    }
    while ((digit = cplus_digit(*cursor)) >= 0 && digit < base) {
        value = value * (unsigned long long)base + (unsigned long long)digit;
        cursor++;
        digits++;
    }
    if (end) *end = (char*)(digits == 0 ? text : cursor);
    if (digits == 0) return 0;
    return negative ? -(long)value : (long)value;
}

unsigned long strtoul(const char* text, char** end, int base) {
    return (unsigned long)strtol(text, end, base);
}

long long strtoll(const char* text, char** end, int base) {
    return (long long)strtol(text, end, base);
}

unsigned long long strtoull(const char* text, char** end, int base) {
    return (unsigned long long)strtol(text, end, base);
}

int atoi(const char* text) { return (int)strtol(text, (char**)0, 10); }

double strtod(const char* text, char** end) {
    const char* cursor = text;
    double value = 0.0;
    double scale = 0.1;
    int negative = 0;
    int digits = 0;
    while (cplus_space(*cursor)) cursor++;
    if (*cursor == '+' || *cursor == '-') { negative = *cursor == '-'; cursor++; }
    while (*cursor >= '0' && *cursor <= '9') {
        value = value * 10.0 + (double)(*cursor - '0');
        cursor++;
        digits++;
    }
    if (*cursor == '.') {
        cursor++;
        while (*cursor >= '0' && *cursor <= '9') {
            value += (double)(*cursor - '0') * scale;
            scale *= 0.1;
            cursor++;
            digits++;
        }
    }
    if (end) *end = (char*)(digits == 0 ? text : cursor);
    return negative ? -value : value;
}

void abort(void) {
    platform_process_exit(134);
    for (;;) { }
}

void _Exit(int status) {
    platform_process_exit(status);
    for (;;) { }
}
