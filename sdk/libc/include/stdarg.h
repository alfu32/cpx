#ifndef CPLUS_SDK_STDARG_H
#define CPLUS_SDK_STDARG_H
#if defined(_MSC_VER) && !defined(__clang__)
typedef char* va_list;
#define va_start(list, last) __crt_va_start(list, last)
#define va_arg(list, type) __crt_va_arg(list, type)
#define va_copy(destination, source) __crt_va_copy(destination, source)
#define va_end(list) __crt_va_end(list)
#else
typedef __builtin_va_list va_list;
#define va_start(list, last) __builtin_va_start(list, last)
#define va_arg(list, type) __builtin_va_arg(list, type)
#define va_copy(destination, source) __builtin_va_copy(destination, source)
#define va_end(list) __builtin_va_end(list)
#endif
#endif
