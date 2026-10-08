#include "cplus_platform.h"

char** __cplus_environment;

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
typedef struct __cplus_startup_info_w {
    __cplus_dword size;
    unsigned short* reserved;
    unsigned short* desktop;
    unsigned short* title;
    __cplus_dword x;
    __cplus_dword y;
    __cplus_dword x_size;
    __cplus_dword y_size;
    __cplus_dword x_count_chars;
    __cplus_dword y_count_chars;
    __cplus_dword fill_attribute;
    __cplus_dword flags;
    unsigned short show_window;
    unsigned short reserved_size;
    unsigned char* reserved_data;
    __cplus_handle standard_input;
    __cplus_handle standard_output;
    __cplus_handle standard_error;
} __cplus_startup_info_w;
typedef struct __cplus_process_information {
    __cplus_handle process;
    __cplus_handle thread;
    __cplus_dword process_id;
    __cplus_dword thread_id;
} __cplus_process_information;

__declspec(dllimport) __cplus_handle __stdcall GetStdHandle(__cplus_dword kind);
__declspec(dllimport) __cplus_dword __stdcall GetCurrentProcessId(void);
__declspec(dllimport) __cplus_handle __stdcall GetCurrentProcess(void);
__declspec(dllimport) void __stdcall GetSystemTimeAsFileTime(__cplus_filetime* time);
__declspec(dllimport) __cplus_bool __stdcall QueryPerformanceCounter(long long* counter);
__declspec(dllimport) __cplus_bool __stdcall QueryPerformanceFrequency(long long* frequency);
__declspec(dllimport) __cplus_bool __stdcall GetProcessTimes(
    __cplus_handle process,
    __cplus_filetime* creation_time,
    __cplus_filetime* exit_time,
    __cplus_filetime* kernel_time,
    __cplus_filetime* user_time);
__declspec(dllimport) unsigned short* __stdcall GetCommandLineW(void);
__declspec(dllimport) unsigned short* __stdcall GetEnvironmentStringsW(void);
__declspec(dllimport) __cplus_bool __stdcall FreeEnvironmentStringsW(unsigned short* environment);
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
__declspec(dllimport) __cplus_bool __stdcall CreateProcessW(
    const unsigned short* application_name,
    unsigned short* command_line,
    void* process_attributes,
    void* thread_attributes,
    __cplus_bool inherit_handles,
    __cplus_dword creation_flags,
    void* environment,
    const unsigned short* current_directory,
    __cplus_startup_info_w* startup_info,
    __cplus_process_information* process_information
);
__declspec(dllimport) __cplus_dword __stdcall WaitForSingleObject(__cplus_handle handle, __cplus_dword milliseconds);
__declspec(dllimport) __cplus_bool __stdcall GetExitCodeProcess(__cplus_handle process, __cplus_dword* exit_code);
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
#define __CPLUS_STARTF_USESTDHANDLES 0x100UL
#define __CPLUS_WAIT_OBJECT_0 0UL
#define __CPLUS_INFINITE 0xffffffffUL
#define __CPLUS_MAX_ARGUMENTS 4096
#define __CPLUS_MAX_COMMAND_LINE 32767
#define __CPLUS_ERROR_HANDLE_EOF 38UL
#define __CPLUS_ERROR_BROKEN_PIPE 109UL

static long cplus_normalize_windows_error(void) {
    __cplus_dword error = GetLastError();
    if (error == 2 || error == 3) return CPLUS_PAL_NOT_FOUND;
    if (error == 5) return CPLUS_PAL_ACCESS_DENIED;
    if (error == 6 || error == 87) return CPLUS_PAL_INVALID_ARGUMENT;
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
    if (length <= 0 || length > __CPLUS_MAX_COMMAND_LINE) return (unsigned short*)0;
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

static unsigned short* cplus_windows_utf8(const char* text) {
    int length;
    int converted;
    unsigned short* result;
    if (!text) return (unsigned short*)0;
    length = MultiByteToWideChar(__CPLUS_CP_UTF8, __CPLUS_MB_ERR_INVALID_CHARS, text, -1, (unsigned short*)0, 0);
    if (length <= 0 || length > __CPLUS_MAX_COMMAND_LINE) return (unsigned short*)0;
    result = (unsigned short*)HeapAlloc(GetProcessHeap(), 0, (unsigned long long)length * 2ULL);
    if (!result) return (unsigned short*)0;
    converted = MultiByteToWideChar(__CPLUS_CP_UTF8, __CPLUS_MB_ERR_INVALID_CHARS, text, -1, result, length);
    if (converted <= 0) {
        HeapFree(GetProcessHeap(), 0, result);
        return (unsigned short*)0;
    }
    return result;
}

static int cplus_windows_append_command_character(unsigned short* command, int* length, unsigned short value) {
    if (*length >= __CPLUS_MAX_COMMAND_LINE - 1) return 0;
    command[(*length)++] = value;
    return 1;
}

static int cplus_windows_append_backslashes(unsigned short* command, int* length, unsigned int count) {
    while (count-- > 0) {
        if (!cplus_windows_append_command_character(command, length, (unsigned short)'\\')) return 0;
    }
    return 1;
}

static int cplus_windows_append_quoted_argument(unsigned short* command, int* length, const char* argument) {
    unsigned short* wide = cplus_windows_utf8(argument);
    int index = 0;
    int success = 1;
    if (!wide) return 0;
    if (*length > 0) success = cplus_windows_append_command_character(command, length, (unsigned short)' ');
    if (success) success = cplus_windows_append_command_character(command, length, (unsigned short)'"');
    while (success && wide[index] != 0) {
        unsigned int backslashes = 0;
        while (wide[index] == (unsigned short)'\\') {
            backslashes++;
            index++;
        }
        if (wide[index] == (unsigned short)'"') {
            success = cplus_windows_append_backslashes(command, length, backslashes * 2U + 1U);
            if (success) success = cplus_windows_append_command_character(command, length, (unsigned short)'"');
            index++;
        } else if (wide[index] == 0) {
            success = cplus_windows_append_backslashes(command, length, backslashes * 2U);
        } else {
            success = cplus_windows_append_backslashes(command, length, backslashes);
            if (success) success = cplus_windows_append_command_character(command, length, wide[index++]);
        }
    }
    if (success) success = cplus_windows_append_command_character(command, length, (unsigned short)'"');
    cplus_windows_free_path(wide);
    return success;
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

static long long cplus_windows_standard_read(__cplus_dword stream, void* buffer, unsigned long long capacity) {
    __cplus_dword read_count = 0;
    __cplus_handle handle;
    if ((!buffer && capacity != 0)) return CPLUS_PAL_INVALID_ARGUMENT;
    if (capacity == 0) return 0;
    handle = GetStdHandle(stream);
    if (!handle || handle == __CPLUS_INVALID_HANDLE) return CPLUS_PAL_IO_ERROR;
    if (capacity > 0xffffffffULL) capacity = 0xffffffffULL;
    if (ReadFile(handle, buffer, (__cplus_dword)capacity, &read_count, (void*)0)) return (long long)read_count;
    {
        __cplus_dword error = GetLastError();
        if (error == __CPLUS_ERROR_HANDLE_EOF || error == __CPLUS_ERROR_BROKEN_PIPE) return 0;
        return (long long)cplus_normalize_windows_error();
    }
}

static long long cplus_windows_standard_write(__cplus_dword stream, const char* buffer, unsigned long long length) {
    __cplus_dword written = 0;
    __cplus_handle handle;
    if (!buffer && length != 0) return CPLUS_PAL_INVALID_ARGUMENT;
    if (length == 0) return 0;
    handle = GetStdHandle(stream);
    if (!handle || handle == __CPLUS_INVALID_HANDLE) return CPLUS_PAL_IO_ERROR;
    if (length > 0xffffffffULL) length = 0xffffffffULL;
    return WriteFile(handle, buffer, (__cplus_dword)length, &written, (void*)0)
        ? (long long)written : (long long)cplus_normalize_windows_error();
}

long long platform_read_stdin(void* buffer, unsigned long long capacity) {
    return cplus_windows_standard_read((__cplus_dword)-10, buffer, capacity);
}

long long platform_write_stdout(const char* buffer, unsigned long long length) {
    return cplus_windows_standard_write((__cplus_dword)-11, buffer, length);
}

long long platform_write_stderr(const char* buffer, unsigned long long length) {
    return cplus_windows_standard_write((__cplus_dword)-12, buffer, length);
}

static int cplus_windows_is_space(unsigned short value) {
    return value == (unsigned short)' ' || value == (unsigned short)'\t';
}

static int cplus_windows_build_arguments(
    const unsigned short* command_line,
    char*** arguments_out
) {
    unsigned short* argument_buffer;
    char** arguments;
    char* utf8_storage;
    unsigned long long storage_offset = 0;
    int argc = 0;
    int index = 0;
    if (!command_line || !arguments_out) return -1;
    argument_buffer = (unsigned short*)HeapAlloc(
        GetProcessHeap(), 0, ((unsigned long long)__CPLUS_MAX_COMMAND_LINE + 1ULL) * 2ULL);
    arguments = (char**)HeapAlloc(
        GetProcessHeap(), 0, ((unsigned long long)__CPLUS_MAX_ARGUMENTS + 1ULL) * sizeof(char*));
    utf8_storage = (char*)HeapAlloc(
        GetProcessHeap(), 0, (unsigned long long)__CPLUS_MAX_COMMAND_LINE * 3ULL + 1ULL);
    if (!argument_buffer || !arguments || !utf8_storage) return -1;
    while (command_line[index]) {
        int argument_length = 0;
        int in_quotes = 0;
        int required;
        int converted;
        while (cplus_windows_is_space(command_line[index])) index++;
        if (!command_line[index]) break;
        if (argc >= __CPLUS_MAX_ARGUMENTS) return -1;
        while (command_line[index]) {
            int backslashes = 0;
            while (command_line[index] == (unsigned short)'\\') {
                backslashes++;
                index++;
            }
            if (command_line[index] == (unsigned short)'"') {
                int slash_index;
                for (slash_index = 0; slash_index < backslashes / 2; slash_index++) {
                    argument_buffer[argument_length++] = (unsigned short)'\\';
                }
                if ((backslashes & 1) != 0) {
                    argument_buffer[argument_length++] = (unsigned short)'"';
                    index++;
                } else if (in_quotes && command_line[index + 1] == (unsigned short)'"') {
                    argument_buffer[argument_length++] = (unsigned short)'"';
                    index += 2;
                } else {
                    in_quotes = !in_quotes;
                    index++;
                }
                continue;
            }
            while (backslashes-- > 0) argument_buffer[argument_length++] = (unsigned short)'\\';
            if (!command_line[index] || (!in_quotes && cplus_windows_is_space(command_line[index]))) break;
            argument_buffer[argument_length++] = command_line[index++];
        }
        required = argument_length == 0 ? 0 : WideCharToMultiByte(
            __CPLUS_CP_UTF8,
            __CPLUS_WC_ERR_INVALID_CHARS,
            argument_buffer,
            argument_length,
            (char*)0,
            0,
            (const char*)0,
            (int*)0);
        if ((argument_length != 0 && required <= 0) || storage_offset + (unsigned long long)required + 1ULL >
                (unsigned long long)__CPLUS_MAX_COMMAND_LINE * 3ULL + 1ULL) return -1;
        arguments[argc++] = utf8_storage + storage_offset;
        if (required != 0) {
            converted = WideCharToMultiByte(
                __CPLUS_CP_UTF8,
                __CPLUS_WC_ERR_INVALID_CHARS,
                argument_buffer,
                argument_length,
                utf8_storage + storage_offset,
                required,
                (const char*)0,
                (int*)0);
            if (converted != required) return -1;
        }
        utf8_storage[storage_offset + (unsigned long long)required] = 0;
        storage_offset += (unsigned long long)required + 1ULL;
        while (cplus_windows_is_space(command_line[index])) index++;
    }
    arguments[argc] = (char*)0;
    *arguments_out = arguments;
    return argc;
}

static char** cplus_windows_build_environment(unsigned short* native_environment) {
    const unsigned short* cursor = native_environment;
    unsigned long long entry_count = 0;
    unsigned long long storage_units = 0;
    char** environment;
    char* utf8_storage;
    unsigned long long storage_offset = 0;
    unsigned long long entry_index = 0;
    if (!native_environment) return (char**)0;
    while (*cursor) {
        unsigned long long length = 0;
        while (cursor[length]) length++;
        if (storage_units > 0x7fffffffffffffffULL - length - 1ULL) return (char**)0;
        storage_units += length + 1ULL;
        entry_count++;
        cursor += length + 1ULL;
    }
    if (storage_units > 0x7fffffffffffffffULL / 3ULL || entry_count > 0x7fffffffffffffffULL / sizeof(char*) - 1ULL) {
        return (char**)0;
    }
    environment = (char**)HeapAlloc(
        GetProcessHeap(), 0, (entry_count + 1ULL) * sizeof(char*));
    utf8_storage = (char*)HeapAlloc(GetProcessHeap(), 0, storage_units * 3ULL + 1ULL);
    if (!environment || !utf8_storage) return (char**)0;
    cursor = native_environment;
    while (*cursor) {
        unsigned long long length = 0;
        int required;
        int converted;
        while (cursor[length]) length++;
        if (length > 0x7fffffffULL) return (char**)0;
        required = WideCharToMultiByte(
            __CPLUS_CP_UTF8,
            __CPLUS_WC_ERR_INVALID_CHARS,
            cursor,
            (int)length,
            (char*)0,
            0,
            (const char*)0,
            (int*)0);
        if (required <= 0 || storage_offset + (unsigned long long)required + 1ULL > storage_units * 3ULL + 1ULL) {
            return (char**)0;
        }
        environment[entry_index++] = utf8_storage + storage_offset;
        converted = WideCharToMultiByte(
            __CPLUS_CP_UTF8,
            __CPLUS_WC_ERR_INVALID_CHARS,
            cursor,
            (int)length,
            utf8_storage + storage_offset,
            required,
            (const char*)0,
            (int*)0);
        if (converted != required) return (char**)0;
        utf8_storage[storage_offset + (unsigned long long)required] = 0;
        storage_offset += (unsigned long long)required + 1ULL;
        cursor += length + 1ULL;
    }
    environment[entry_index] = (char*)0;
    return environment;
}

extern int __cplus_start(int argc, char** argv, char** environment);

int __cplus_windows_start(void) {
    unsigned short* native_environment = GetEnvironmentStringsW();
    char** arguments = (char**)0;
    char** environment;
    int argc;
    int status;
    if (!native_environment) return 127;
    argc = cplus_windows_build_arguments(GetCommandLineW(), &arguments);
    environment = cplus_windows_build_environment(native_environment);
    FreeEnvironmentStringsW(native_environment);
    if (argc < 0 || !arguments || !environment) return 127;
    status = __cplus_start(argc, arguments, environment);
    return status;
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

long long platform_process_id(void) {
    return (long long)GetCurrentProcessId();
}

long long platform_process_spawn(const char* executable, const char* const* arguments) {
    unsigned short* wide_executable;
    unsigned short* command_line;
    int command_length = 0;
    unsigned int argument_count = 0;
    __cplus_startup_info_w startup_info;
    __cplus_process_information process_information;
    int error;
    if (!executable || executable[0] == '\0' || (arguments && !arguments[0])) {
        return CPLUS_PAL_INVALID_ARGUMENT;
    }
    wide_executable = cplus_windows_path(executable);
    if (!wide_executable) return CPLUS_PAL_INVALID_ARGUMENT;
    command_line = (unsigned short*)HeapAlloc(
        GetProcessHeap(), 0, (unsigned long long)__CPLUS_MAX_COMMAND_LINE * 2ULL);
    if (!command_line) {
        cplus_windows_free_path(wide_executable);
        return CPLUS_PAL_IO_ERROR;
    }
    if (!arguments) {
        if (!cplus_windows_append_quoted_argument(command_line, &command_length, executable)) {
            cplus_windows_free_path(command_line);
            cplus_windows_free_path(wide_executable);
            return CPLUS_PAL_INVALID_ARGUMENT;
        }
    } else {
        while (arguments[argument_count]) {
            if (argument_count >= __CPLUS_MAX_ARGUMENTS ||
                !cplus_windows_append_quoted_argument(command_line, &command_length, arguments[argument_count])) {
                cplus_windows_free_path(command_line);
                cplus_windows_free_path(wide_executable);
                return CPLUS_PAL_INVALID_ARGUMENT;
            }
            argument_count++;
        }
    }
    command_line[command_length] = 0;
    startup_info.size = sizeof(startup_info);
    startup_info.reserved = (unsigned short*)0;
    startup_info.desktop = (unsigned short*)0;
    startup_info.title = (unsigned short*)0;
    startup_info.x = 0;
    startup_info.y = 0;
    startup_info.x_size = 0;
    startup_info.y_size = 0;
    startup_info.x_count_chars = 0;
    startup_info.y_count_chars = 0;
    startup_info.fill_attribute = 0;
    startup_info.flags = __CPLUS_STARTF_USESTDHANDLES;
    startup_info.show_window = 0;
    startup_info.reserved_size = 0;
    startup_info.reserved_data = (unsigned char*)0;
    startup_info.standard_input = GetStdHandle((__cplus_dword)-10);
    startup_info.standard_output = GetStdHandle((__cplus_dword)-11);
    startup_info.standard_error = GetStdHandle((__cplus_dword)-12);
    if (!CreateProcessW(
            wide_executable,
            command_line,
            (void*)0,
            (void*)0,
            1,
            0,
            (void*)0,
            (const unsigned short*)0,
            &startup_info,
            &process_information)) {
        error = (int)cplus_normalize_windows_error();
        cplus_windows_free_path(command_line);
        cplus_windows_free_path(wide_executable);
        return error;
    }
    CloseHandle(process_information.thread);
    cplus_windows_free_path(command_line);
    cplus_windows_free_path(wide_executable);
    return (long long)process_information.process;
}

int platform_process_wait(long long process, int* exit_status) {
    __cplus_handle process_handle = (__cplus_handle)process;
    __cplus_dword wait_result;
    __cplus_dword status;
    if (process <= 0 || process_handle == __CPLUS_INVALID_HANDLE || !exit_status) {
        return (int)CPLUS_PAL_INVALID_ARGUMENT;
    }
    wait_result = WaitForSingleObject(process_handle, __CPLUS_INFINITE);
    if (wait_result != __CPLUS_WAIT_OBJECT_0) return (int)cplus_normalize_windows_error();
    if (!GetExitCodeProcess(process_handle, &status)) return (int)cplus_normalize_windows_error();
    if (!CloseHandle(process_handle)) return (int)cplus_normalize_windows_error();
    *exit_status = (int)status;
    return 0;
}

static unsigned long long cplus_windows_filetime_ticks(__cplus_filetime time) {
    return ((unsigned long long)time.high << 32) | (unsigned long long)time.low;
}

long long platform_clock_wall_nanoseconds(void) {
    static const unsigned long long windows_epoch_ticks = 116444736000000000ULL;
    __cplus_filetime current;
    unsigned long long ticks;
    unsigned long long delta;
    GetSystemTimeAsFileTime(&current);
    ticks = cplus_windows_filetime_ticks(current);
    if (ticks < windows_epoch_ticks) return CPLUS_PAL_UNSUPPORTED;
    delta = ticks - windows_epoch_ticks;
    if (delta > 0x7fffffffffffffffULL / 100ULL) return CPLUS_PAL_IO_ERROR;
    return (long long)(delta * 100ULL);
}

long long platform_clock_monotonic_nanoseconds(void) {
    long long counter;
    long long frequency;
    long long seconds;
    long long remainder;
    long long fraction;
    long long base;
    if (!QueryPerformanceCounter(&counter) || !QueryPerformanceFrequency(&frequency)) {
        return cplus_normalize_windows_error();
    }
    if (counter < 0 || frequency <= 0 || frequency > 0x7fffffffffffffffLL / 1000000000LL) {
        return CPLUS_PAL_IO_ERROR;
    }
    seconds = counter / frequency;
    remainder = counter % frequency;
    if (seconds > 0x7fffffffffffffffLL / 1000000000LL) return CPLUS_PAL_IO_ERROR;
    fraction = remainder * 1000000000LL / frequency;
    base = seconds * 1000000000LL;
    if (base > 0x7fffffffffffffffLL - fraction) return CPLUS_PAL_IO_ERROR;
    return base + fraction;
}

long long platform_clock_process_cpu_nanoseconds(void) {
    __cplus_filetime creation;
    __cplus_filetime exit_time;
    __cplus_filetime kernel;
    __cplus_filetime user;
    unsigned long long kernel_ticks;
    unsigned long long user_ticks;
    if (!GetProcessTimes(GetCurrentProcess(), &creation, &exit_time, &kernel, &user)) {
        return cplus_normalize_windows_error();
    }
    kernel_ticks = cplus_windows_filetime_ticks(kernel);
    user_ticks = cplus_windows_filetime_ticks(user);
    if (kernel_ticks > 0xffffffffffffffffULL - user_ticks) return CPLUS_PAL_IO_ERROR;
    if (kernel_ticks + user_ticks > 0x7fffffffffffffffULL / 100ULL) return CPLUS_PAL_IO_ERROR;
    return (long long)((kernel_ticks + user_ticks) * 100ULL);
}

long long platform_clock_ticks(void) {
    return platform_clock_monotonic_nanoseconds();
}
