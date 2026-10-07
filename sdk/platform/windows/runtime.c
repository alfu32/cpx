#include "cplus_platform.h"

typedef void* __cplus_handle;
typedef unsigned long __cplus_dword;
typedef int __cplus_bool;

__declspec(dllimport) __cplus_handle __stdcall GetStdHandle(__cplus_dword kind);
__declspec(dllimport) __cplus_handle __stdcall GetProcessHeap(void);
__declspec(dllimport) void* __stdcall HeapAlloc(__cplus_handle heap, __cplus_dword flags, unsigned long long bytes);
__declspec(dllimport) __cplus_bool __stdcall HeapFree(__cplus_handle heap, __cplus_dword flags, void* memory);
__declspec(dllimport) void* __stdcall VirtualAlloc(void* address, unsigned long long size, __cplus_dword allocation_type, __cplus_dword protection);
__declspec(dllimport) __cplus_bool __stdcall VirtualFree(void* address, unsigned long long size, __cplus_dword free_type);
__declspec(dllimport) int __stdcall MultiByteToWideChar(
    __cplus_dword code_page,
    __cplus_dword flags,
    const char* source,
    int source_length,
    unsigned short* destination,
    int destination_length
);
__declspec(dllimport) __cplus_handle __stdcall CreateFileW(
    const unsigned short* path,
    __cplus_dword desired_access,
    __cplus_dword share_mode,
    void* security_attributes,
    __cplus_dword creation_disposition,
    __cplus_dword flags_and_attributes,
    __cplus_handle template_file
);
__declspec(dllimport) __cplus_bool __stdcall WriteFile(
    __cplus_handle handle,
    const void* buffer,
    __cplus_dword length,
    __cplus_dword* written,
    void* overlapped
);
__declspec(dllimport) __cplus_bool __stdcall ReadFile(
    __cplus_handle handle,
    void* buffer,
    __cplus_dword length,
    __cplus_dword* read,
    void* overlapped
);
__declspec(dllimport) __cplus_bool __stdcall CloseHandle(__cplus_handle handle);
__declspec(dllimport) __cplus_bool __stdcall MoveFileExW(
    const unsigned short* existing_path,
    const unsigned short* new_path,
    __cplus_dword flags
);
__declspec(dllimport) __cplus_dword __stdcall GetLastError(void);
__declspec(dllimport) __declspec(noreturn) void __stdcall ExitProcess(__cplus_dword code);

#define __CPLUS_CP_UTF8 65001UL
#define __CPLUS_MB_ERR_INVALID_CHARS 8UL
#define __CPLUS_GENERIC_READ 0x80000000UL
#define __CPLUS_GENERIC_WRITE 0x40000000UL
#define __CPLUS_FILE_SHARE_ALL 7UL
#define __CPLUS_CREATE_ALWAYS 2UL
#define __CPLUS_OPEN_EXISTING 3UL
#define __CPLUS_OPEN_ALWAYS 4UL
#define __CPLUS_TRUNCATE_EXISTING 5UL
#define __CPLUS_FILE_ATTRIBUTE_NORMAL 0x80UL
#define __CPLUS_MOVEFILE_REPLACE_EXISTING 1UL
#define __CPLUS_MEM_COMMIT 0x1000UL
#define __CPLUS_MEM_RESERVE 0x2000UL
#define __CPLUS_MEM_RELEASE 0x8000UL
#define __CPLUS_PAGE_READWRITE 4UL
#define __CPLUS_INVALID_HANDLE ((void*)(-1))

static long cplus_normalize_windows_error(void) {
    __cplus_dword error = GetLastError();
    if (error == 2 || error == 3) return CPLUS_PAL_NOT_FOUND;
    if (error == 5) return CPLUS_PAL_ACCESS_DENIED;
    if (error == 87) return CPLUS_PAL_INVALID_ARGUMENT;
    return CPLUS_PAL_IO_ERROR;
}

static unsigned short* cplus_windows_path(const char* path) {
    int length;
    int converted;
    int index;
    __cplus_handle heap;
    unsigned short* result;
    if (!path) return (unsigned short*)0;
    length = MultiByteToWideChar(__CPLUS_CP_UTF8, __CPLUS_MB_ERR_INVALID_CHARS, path, -1, (unsigned short*)0, 0);
    if (length <= 0) return (unsigned short*)0;
    heap = GetProcessHeap();
    if (!heap) return (unsigned short*)0;
    result = (unsigned short*)HeapAlloc(heap, 0, (unsigned long long)length * 2ULL);
    if (!result) return (unsigned short*)0;
    converted = MultiByteToWideChar(__CPLUS_CP_UTF8, __CPLUS_MB_ERR_INVALID_CHARS, path, -1, result, length);
    if (converted <= 0) {
        HeapFree(heap, 0, result);
        return (unsigned short*)0;
    }
    for (index = 0; index < converted; index++) {
        if (result[index] == (unsigned short)'/') result[index] = (unsigned short)'\\';
    }
    return result;
}

static void cplus_windows_free_path(unsigned short* path) {
    if (path) HeapFree(GetProcessHeap(), 0, path);
}

void* platform_page_allocate(unsigned long long page_count) {
    unsigned long long bytes;
    if (page_count == 0 || page_count > 0xffffffffffffffffULL / CPLUS_PAL_PAGE_SIZE) return (void*)0;
    bytes = page_count * CPLUS_PAL_PAGE_SIZE;
    return VirtualAlloc((void*)0, bytes, __CPLUS_MEM_COMMIT | __CPLUS_MEM_RESERVE, __CPLUS_PAGE_READWRITE);
}

int platform_page_release(void* address, unsigned long long page_count) {
    (void)page_count;
    if (!address) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    return VirtualFree(address, 0, __CPLUS_MEM_RELEASE) ? 0 : (int)cplus_normalize_windows_error();
}

long platform_write_stdout(const char* buffer, unsigned long length) {
    __cplus_dword written = 0;
    __cplus_handle handle = GetStdHandle((__cplus_dword)-11);
    return WriteFile(handle, buffer, (__cplus_dword)length, &written, (void*)0) ? (long)written : CPLUS_PAL_IO_ERROR;
}

long long platform_file_open(const char* path, unsigned long long mode) {
    unsigned long access = 0;
    unsigned long creation;
    unsigned short* wide_path;
    __cplus_handle handle;
    if (!path || (mode & (CPLUS_FILE_READ | CPLUS_FILE_WRITE)) == 0) return CPLUS_PAL_INVALID_ARGUMENT;
    if (mode & CPLUS_FILE_READ) access |= __CPLUS_GENERIC_READ;
    if (mode & CPLUS_FILE_WRITE) access |= __CPLUS_GENERIC_WRITE;
    if ((mode & CPLUS_FILE_CREATE) && (mode & CPLUS_FILE_TRUNCATE)) creation = __CPLUS_CREATE_ALWAYS;
    else if (mode & CPLUS_FILE_CREATE) creation = __CPLUS_OPEN_ALWAYS;
    else if (mode & CPLUS_FILE_TRUNCATE) creation = __CPLUS_TRUNCATE_EXISTING;
    else creation = __CPLUS_OPEN_EXISTING;
    wide_path = cplus_windows_path(path);
    if (!wide_path) return CPLUS_PAL_INVALID_ARGUMENT;
    handle = CreateFileW(
        wide_path,
        access,
        __CPLUS_FILE_SHARE_ALL,
        (void*)0,
        creation,
        __CPLUS_FILE_ATTRIBUTE_NORMAL,
        (void*)0
    );
    cplus_windows_free_path(wide_path);
    return handle == __CPLUS_INVALID_HANDLE ? cplus_normalize_windows_error() : (long long)handle;
}

long long platform_file_read(long long handle, void* buffer, unsigned long long length) {
    __cplus_dword count = 0;
    if ((void*)handle == __CPLUS_INVALID_HANDLE || (!buffer && length != 0) || length > 0xffffffffULL) return CPLUS_PAL_INVALID_ARGUMENT;
    return ReadFile((__cplus_handle)handle, buffer, (__cplus_dword)length, &count, (void*)0)
        ? (long)count : cplus_normalize_windows_error();
}

long long platform_file_write(long long handle, const void* buffer, unsigned long long length) {
    __cplus_dword count = 0;
    if ((void*)handle == __CPLUS_INVALID_HANDLE || (!buffer && length != 0) || length > 0xffffffffULL) return CPLUS_PAL_INVALID_ARGUMENT;
    return WriteFile((__cplus_handle)handle, buffer, (__cplus_dword)length, &count, (void*)0)
        ? (long)count : cplus_normalize_windows_error();
}

int platform_file_close(long long handle) {
    if ((void*)handle == __CPLUS_INVALID_HANDLE) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    return CloseHandle((__cplus_handle)handle) ? 0 : (int)cplus_normalize_windows_error();
}

int platform_file_rename(const char* source, const char* target) {
    unsigned short* wide_source = cplus_windows_path(source);
    unsigned short* wide_target;
    int result;
    if (!wide_source) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    wide_target = cplus_windows_path(target);
    if (!wide_target) {
        cplus_windows_free_path(wide_source);
        return (int)CPLUS_PAL_INVALID_ARGUMENT;
    }
    result = MoveFileExW(wide_source, wide_target, __CPLUS_MOVEFILE_REPLACE_EXISTING)
        ? 0 : (int)cplus_normalize_windows_error();
    cplus_windows_free_path(wide_source);
    cplus_windows_free_path(wide_target);
    return result;
}

int platform_process_exit(int status) {
    ExitProcess((__cplus_dword)status);
    return status;
}
