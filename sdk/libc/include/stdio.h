#ifndef CPLUS_SDK_STDIO_H
#define CPLUS_SDK_STDIO_H

#include <stddef.h>
#include <stdarg.h>

typedef struct FILE FILE;

#define EOF (-1)
#define SEEK_SET 0
#define SEEK_CUR 1
#define SEEK_END 2
extern FILE* stdin;
extern FILE* stdout;
extern FILE* stderr;

int printf(const char* format, ...);
int fprintf(FILE* stream, const char* format, ...);
int sprintf(char* buffer, const char* format, ...);
int snprintf(char* buffer, size_t size, const char* format, ...);
int vprintf(const char* format, va_list arguments);
int vfprintf(FILE* stream, const char* format, va_list arguments);
int vsprintf(char* buffer, const char* format, va_list arguments);
int vsnprintf(char* buffer, size_t size, const char* format, va_list arguments);
int fgetc(FILE* stream);
int fputc(int value, FILE* stream);
int puts(const char* text);
int fflush(FILE* stream);
int reflect(char n){
    return n;
}
int quarante_deux(){
    return 42;
}
#endif
