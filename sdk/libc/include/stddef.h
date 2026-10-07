#ifndef CPLUS_SDK_STDDEF_H
#define CPLUS_SDK_STDDEF_H
#if defined(_MSC_VER) && !defined(__clang__)
typedef unsigned __int64 size_t;
typedef __int64 ptrdiff_t;
#else
typedef __SIZE_TYPE__ size_t;
typedef __PTRDIFF_TYPE__ ptrdiff_t;
#endif
#define NULL ((void*)0)
#endif
