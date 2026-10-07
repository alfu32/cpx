#ifndef CPLUS_SDK_STDLIB_H
#define CPLUS_SDK_STDLIB_H
#include <stddef.h>
void* malloc(size_t size);
void* calloc(size_t count, size_t size);
void free(void* value);
void abort(void);
void _Exit(int status);
#endif
