#ifndef CPLUS_SDK_STDLIB_H
#define CPLUS_SDK_STDLIB_H
#include <stddef.h>
void* malloc(size_t size);
void* calloc(size_t count, size_t size);
void* realloc(void* value, size_t size);
void* aligned_alloc(size_t alignment, size_t size);
void free(void* value);
long strtol(const char* text, char** end, int base);
unsigned long strtoul(const char* text, char** end, int base);
long long strtoll(const char* text, char** end, int base);
unsigned long long strtoull(const char* text, char** end, int base);
int atoi(const char* text);
double strtod(const char* text, char** end);
void abort(void);
void _Exit(int status);
#endif
