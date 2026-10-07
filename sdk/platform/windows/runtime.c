#include "cplus_platform.h"

typedef void* __cplus_handle;
typedef unsigned long __cplus_dword;
typedef int __cplus_bool;

__declspec(dllimport) __cplus_handle __stdcall GetStdHandle(__cplus_dword kind);
__declspec(dllimport) __cplus_bool __stdcall WriteFile(
    __cplus_handle handle,
    const void* buffer,
    __cplus_dword length,
    __cplus_dword* written,
    void* overlapped
);
__declspec(dllimport) __declspec(noreturn) void __stdcall ExitProcess(__cplus_dword code);

long platform_write_stdout(const char* buffer, unsigned long length) {
    __cplus_dword written = 0;
    __cplus_handle handle = GetStdHandle((__cplus_dword)-11);
    return WriteFile(handle, buffer, (__cplus_dword)length, &written, (void*)0) ? (long)written : -1;
}

int platform_process_exit(int status) {
    ExitProcess((__cplus_dword)status);
    return status;
}
