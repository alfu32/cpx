#ifndef CPLUS_SDK_WCHAR_H
#define CPLUS_SDK_WCHAR_H
typedef int wchar_t;
typedef long wint_t;
#include <stddef.h>
size_t wcslen(const wchar_t* text);
int wcscmp(const wchar_t* left, const wchar_t* right);
size_t mbstowcs(wchar_t* destination, const char* source, size_t limit);
size_t wcstombs(char* destination, const wchar_t* source, size_t limit);
#endif
