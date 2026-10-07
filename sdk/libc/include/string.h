#ifndef CPLUS_SDK_STRING_H
#define CPLUS_SDK_STRING_H
#include <stddef.h>
void* memcpy(void* destination, void* source, size_t size);
void* memmove(void* destination, void* source, size_t size);
void* memset(void* destination, int value, size_t size);
int memcmp(void* left, void* right, size_t size);
size_t strlen(char* text);
int strcmp(char* left, char* right);
#endif
