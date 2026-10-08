#include "cplus_platform.h"

typedef void* __cplus_handle;
typedef unsigned long __cplus_dword;
typedef int __cplus_bool;
typedef struct __cplus_filetime {
    unsigned long low;
    unsigned long high;
} __cplus_filetime;
typedef struct __cplus_by_handle_file_information {
    __cplus_dword attributes;
    __cplus_filetime creation_time;
    __cplus_filetime access_time;
    __cplus_filetime write_time;
    __cplus_dword volume_serial_number;
    __cplus_dword size_high;
    __cplus_dword size_low;
    __cplus_dword link_count;
    __cplus_dword file_index_high;
    __cplus_dword file_index_low;
} __cplus_by_handle_file_information;
typedef struct __cplus_find_data_w {
    __cplus_dword attributes;
    __cplus_filetime creation_time;
    __cplus_filetime access_time;
    __cplus_filetime write_time;
    __cplus_dword size_high;
    __cplus_dword size_low;
    __cplus_dword reserved0;
    __cplus_dword reserved1;
    unsigned short file_name[260];
    unsigned short alternate_file_name[14];
} __cplus_find_data_w;
typedef struct __cplus_directory_iterator {
    __cplus_handle search_handle;
    __cplus_find_data_w current;
    int has_current;
    int finished;
} __cplus_directory_iterator;

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
__declspec(dllimport) __cplus_bool __stdcall GetFileInformationByHandle(
    __cplus_handle handle,
    __cplus_by_handle_file_information* information
);
__declspec(dllimport) __cplus_bool __stdcall SetFilePointerEx(
    __cplus_handle handle,
    long long distance,
    long long* new_position,
    __cplus_dword move_method
);
__declspec(dllimport) __cplus_bool __stdcall CreateDirectoryW(const unsigned short* path, void* security_attributes);
__declspec(dllimport) __cplus_bool __stdcall DeleteFileW(const unsigned short* path);
__declspec(dllimport) __cplus_bool __stdcall RemoveDirectoryW(const unsigned short* path);
__declspec(dllimport) __cplus_handle __stdcall FindFirstFileW(
    const unsigned short* pattern,
    __cplus_find_data_w* find_data
);
__declspec(dllimport) __cplus_bool __stdcall FindNextFileW(
    __cplus_handle search_handle,
    __cplus_find_data_w* find_data
);
__declspec(dllimport) __cplus_bool __stdcall FindClose(__cplus_handle search_handle);
__declspec(dllimport) int __stdcall WideCharToMultiByte(
    __cplus_dword code_page,
    __cplus_dword flags,
    const unsigned short* source,
    int source_length,
    char* destination,
    int destination_length,
    const char* default_character,
    int* used_default_character
);
__declspec(dllimport) __cplus_bool __stdcall MoveFileExW(
    const unsigned short* existing_path,
    const unsigned short* new_path,
    __cplus_dword flags
);
__declspec(dllimport) __cplus_dword __stdcall GetLastError(void);
__declspec(dllimport) unsigned long long __stdcall GetTickCount64(void);
__declspec(dllimport) __declspec(noreturn) void __stdcall ExitProcess(__cplus_dword code);

#define __CPLUS_CP_UTF8 65001UL
#define __CPLUS_MB_ERR_INVALID_CHARS 8UL
#define __CPLUS_WC_ERR_INVALID_CHARS 0x80UL
#define __CPLUS_GENERIC_READ 0x80000000UL
#define __CPLUS_GENERIC_WRITE 0x40000000UL
#define __CPLUS_FILE_SHARE_ALL 7UL
#define __CPLUS_CREATE_ALWAYS 2UL
#define __CPLUS_OPEN_EXISTING 3UL
#define __CPLUS_OPEN_ALWAYS 4UL
#define __CPLUS_TRUNCATE_EXISTING 5UL
#define __CPLUS_FILE_ATTRIBUTE_NORMAL 0x80UL
#define __CPLUS_FILE_ATTRIBUTE_DIRECTORY 0x10UL
#define __CPLUS_FILE_ATTRIBUTE_DEVICE 0x40UL
#define __CPLUS_FILE_FLAG_BACKUP_SEMANTICS 0x02000000UL
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
    if (error == 1 || error == 50) return CPLUS_PAL_UNSUPPORTED;
    if (error == 1113) return CPLUS_PAL_UNSUPPORTED;
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

static unsigned short* cplus_windows_directory_pattern(const char* path) {
    unsigned short* base = cplus_windows_path(path);
    unsigned short* pattern;
    int length = 0;
    int separator;
    int index;
    if (!base) return (unsigned short*)0;
    while (base[length] != 0) length++;
    if (length == 0 || length > 0x7ffffffc) {
        cplus_windows_free_path(base);
        return (unsigned short*)0;
    }
    separator = length > 0 && base[length - 1] != (unsigned short)'\\';
    pattern = (unsigned short*)HeapAlloc(
        GetProcessHeap(),
        0,
        (unsigned long long)(length + separator + 2) * 2ULL
    );
    if (!pattern) {
        cplus_windows_free_path(base);
        return (unsigned short*)0;
    }
    for (index = 0; index < length; index++) pattern[index] = base[index];
    if (separator) pattern[length++] = (unsigned short)'\\';
    pattern[length++] = (unsigned short)'*';
    pattern[length] = 0;
    cplus_windows_free_path(base);
    return pattern;
}

static int cplus_windows_is_dot_name(const unsigned short* name, int length) {
    return (length == 1 && name[0] == (unsigned short)'.') ||
        (length == 2 && name[0] == (unsigned short)'.' && name[1] == (unsigned short)'.');
}

void* platform_page_allocate(unsigned long long page_count) {
    unsigned long long bytes;
    if (page_count == 0 || page_count > 0xffffffffffffffffULL / CPLUS_PAL_PAGE_SIZE) return (void*)0;
    bytes = page_count * CPLUS_PAL_PAGE_SIZE;
    return VirtualAlloc((void*)0, bytes, __CPLUS_MEM_COMMIT | __CPLUS_MEM_RESERVE, __CPLUS_PAGE_READWRITE);
}

int platform_page_release(void* address, unsigned long long page_count) {
    if (!address || page_count == 0) return (int)CPLUS_PAL_INVALID_ARGUMENT;
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

long long platform_file_seek(long long handle, long long offset, unsigned int origin) {
    long long position = 0;
    if ((void*)handle == __CPLUS_INVALID_HANDLE || origin > CPLUS_SEEK_END) return CPLUS_PAL_INVALID_ARGUMENT;
    if (!SetFilePointerEx((__cplus_handle)handle, offset, &position, origin)) {
        return cplus_normalize_windows_error();
    }
    return position < 0 ? CPLUS_PAL_INVALID_ARGUMENT : position;
}

int platform_file_metadata(const char* path, cplus_file_metadata_t* metadata) {
    static const unsigned long long windows_epoch_ticks = 116444736000000000ULL;
    static const unsigned long long ticks_per_second = 10000000ULL;
    unsigned short* wide_path;
    __cplus_handle handle;
    __cplus_by_handle_file_information information;
    unsigned long long file_ticks;
    unsigned long long delta;
    if (!path || !metadata) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    wide_path = cplus_windows_path(path);
    if (!wide_path) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    handle = CreateFileW(
        wide_path,
        0,
        __CPLUS_FILE_SHARE_ALL,
        (void*)0,
        __CPLUS_OPEN_EXISTING,
        __CPLUS_FILE_ATTRIBUTE_NORMAL | __CPLUS_FILE_FLAG_BACKUP_SEMANTICS,
        (void*)0
    );
    cplus_windows_free_path(wide_path);
    if (handle == __CPLUS_INVALID_HANDLE) return (int)cplus_normalize_windows_error();
    if (!GetFileInformationByHandle(handle, &information)) {
        int error = (int)cplus_normalize_windows_error();
        CloseHandle(handle);
        return error;
    }
    CloseHandle(handle);

    file_ticks = ((unsigned long long)information.write_time.high << 32) |
        (unsigned long long)information.write_time.low;
    metadata->size_bytes = ((unsigned long long)information.size_high << 32) |
        (unsigned long long)information.size_low;
    if (file_ticks >= windows_epoch_ticks) {
        delta = file_ticks - windows_epoch_ticks;
        if (delta / ticks_per_second > 0x7fffffffffffffffULL) return (int)CPLUS_PAL_IO_ERROR;
        metadata->modified_seconds_utc = (long long)(delta / ticks_per_second);
        metadata->modified_nanoseconds = (unsigned int)((delta % ticks_per_second) * 100ULL);
    } else {
        delta = windows_epoch_ticks - file_ticks;
        metadata->modified_seconds_utc = -(long long)(delta / ticks_per_second);
        if (delta % ticks_per_second != 0) {
            metadata->modified_seconds_utc -= 1;
            metadata->modified_nanoseconds = (unsigned int)((ticks_per_second - delta % ticks_per_second) * 100ULL);
        } else {
            metadata->modified_nanoseconds = 0;
        }
    }
    if (information.attributes & __CPLUS_FILE_ATTRIBUTE_DIRECTORY) metadata->kind = CPLUS_FILE_KIND_DIRECTORY;
    else if (information.attributes & __CPLUS_FILE_ATTRIBUTE_DEVICE) metadata->kind = CPLUS_FILE_KIND_OTHER;
    else metadata->kind = CPLUS_FILE_KIND_REGULAR;
    metadata->reserved0 = 0;
    metadata->reserved1 = 0;
    return 0;
}

int platform_directory_create(const char* path) {
    unsigned short* wide_path;
    int result;
    if (!path) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    wide_path = cplus_windows_path(path);
    if (!wide_path) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    result = CreateDirectoryW(wide_path, (void*)0) ? 0 : (int)cplus_normalize_windows_error();
    cplus_windows_free_path(wide_path);
    return result;
}

int platform_file_remove(const char* path) {
    unsigned short* wide_path;
    int result;
    if (!path) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    wide_path = cplus_windows_path(path);
    if (!wide_path) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    result = DeleteFileW(wide_path) ? 0 : (int)cplus_normalize_windows_error();
    cplus_windows_free_path(wide_path);
    return result;
}

int platform_directory_remove(const char* path) {
    unsigned short* wide_path;
    int result;
    if (!path) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    wide_path = cplus_windows_path(path);
    if (!wide_path) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    result = RemoveDirectoryW(wide_path) ? 0 : (int)cplus_normalize_windows_error();
    cplus_windows_free_path(wide_path);
    return result;
}

long long platform_directory_open(const char* path) {
    unsigned short* pattern;
    __cplus_directory_iterator* iterator;
    __cplus_handle search_handle;
    int error;
    if (!path) return CPLUS_PAL_INVALID_ARGUMENT;
    pattern = cplus_windows_directory_pattern(path);
    if (!pattern) return CPLUS_PAL_INVALID_ARGUMENT;
    iterator = (__cplus_directory_iterator*)HeapAlloc(
        GetProcessHeap(), 0, (unsigned long long)sizeof(__cplus_directory_iterator)
    );
    if (!iterator) {
        cplus_windows_free_path(pattern);
        return CPLUS_PAL_IO_ERROR;
    }
    search_handle = FindFirstFileW(pattern, &iterator->current);
    if (search_handle == __CPLUS_INVALID_HANDLE) {
        error = (int)cplus_normalize_windows_error();
        cplus_windows_free_path(pattern);
        HeapFree(GetProcessHeap(), 0, iterator);
        return error;
    }
    cplus_windows_free_path(pattern);
    iterator->search_handle = search_handle;
    iterator->has_current = 1;
    iterator->finished = 0;
    return (long long)iterator;
}

long long platform_directory_read(long long handle, char* utf8_name, unsigned long long capacity) {
    __cplus_directory_iterator* iterator = (__cplus_directory_iterator*)handle;
    if (!iterator || (void*)handle == __CPLUS_INVALID_HANDLE || !utf8_name) {
        return (long long)CPLUS_PAL_INVALID_ARGUMENT;
    }
    for (;;) {
        int wide_length = 0;
        int utf8_length;
        int converted;
        int error;
        if (iterator->finished) return 0;
        if (!iterator->has_current) {
            if (!FindNextFileW(iterator->search_handle, &iterator->current)) {
                error = (int)GetLastError();
                if (error == 18) {
                    iterator->finished = 1;
                    return 0;
                }
                return (long long)cplus_normalize_windows_error();
            }
            iterator->has_current = 1;
        }
        while (wide_length < 260 && iterator->current.file_name[wide_length] != 0) wide_length++;
        if (wide_length == 260) return (long long)CPLUS_PAL_IO_ERROR;
        if (cplus_windows_is_dot_name(iterator->current.file_name, wide_length)) {
            iterator->has_current = 0;
            continue;
        }
        utf8_length = WideCharToMultiByte(
            __CPLUS_CP_UTF8,
            __CPLUS_WC_ERR_INVALID_CHARS,
            iterator->current.file_name,
            wide_length,
            (char*)0,
            0,
            (const char*)0,
            (int*)0
        );
        if (utf8_length <= 0) {
            error = (int)GetLastError();
            iterator->has_current = 0;
            if (error == 1113) return (long long)CPLUS_PAL_UNSUPPORTED;
            return (long long)cplus_normalize_windows_error();
        }
        if (capacity <= (unsigned long long)utf8_length) return (long long)CPLUS_PAL_BUFFER_TOO_SMALL;
        converted = WideCharToMultiByte(
            __CPLUS_CP_UTF8,
            __CPLUS_WC_ERR_INVALID_CHARS,
            iterator->current.file_name,
            wide_length,
            utf8_name,
            utf8_length,
            (const char*)0,
            (int*)0
        );
        if (converted <= 0) {
            error = (int)GetLastError();
            iterator->has_current = 0;
            if (error == 1113) return (long long)CPLUS_PAL_UNSUPPORTED;
            return (long long)cplus_normalize_windows_error();
        }
        utf8_name[converted] = '\0';
        iterator->has_current = 0;
        return (long long)converted;
    }
}

int platform_directory_close(long long handle) {
    __cplus_directory_iterator* iterator = (__cplus_directory_iterator*)handle;
    int result;
    if (!iterator || (void*)handle == __CPLUS_INVALID_HANDLE) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    result = FindClose(iterator->search_handle) ? 0 : (int)cplus_normalize_windows_error();
    HeapFree(GetProcessHeap(), 0, iterator);
    return result;
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

long long platform_clock_ticks(void) {
    return (long long)GetTickCount64() * 1000000LL;
}
