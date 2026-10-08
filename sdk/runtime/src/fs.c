#include "cplus_platform.h"
#include <stddef.h>

/* Native std.fs entry points remain target-independent. The selected PAL
   supplies path conversion, OS handles, and error normalization. */
struct std_file_metadata_t {
    unsigned long long size_bytes;
    long long modified_seconds_utc;
    unsigned int modified_nanoseconds;
    unsigned int kind;
    unsigned int reserved0;
    unsigned int reserved1;
};

_Static_assert(sizeof(struct std_file_metadata_t) == sizeof(cplus_file_metadata_t), "std/PAL metadata size mismatch");

long long std_fs_open(const char* path, unsigned long long mode) {
    return platform_file_open(path, mode);
}

unsigned long long std_fs_mode_read(void) {
    return CPLUS_FILE_READ;
}

unsigned long long std_fs_mode_write(void) {
    return CPLUS_FILE_WRITE;
}

unsigned long long std_fs_mode_create(void) {
    return CPLUS_FILE_CREATE;
}

unsigned long long std_fs_mode_truncate(void) {
    return CPLUS_FILE_TRUNCATE;
}

long long std_fs_error_invalid_argument(void) {
    return CPLUS_PAL_INVALID_ARGUMENT;
}

long long std_fs_error_buffer_too_small(void) {
    return CPLUS_PAL_BUFFER_TOO_SMALL;
}

ptrdiff_t std_fs_read(long long handle, void* buffer, size_t size) {
    if (size > 0x7fffffffffffffffULL) return (ptrdiff_t)CPLUS_PAL_INVALID_ARGUMENT;
    return (ptrdiff_t)platform_file_read(handle, buffer, (cplus_file_size_t)size);
}

ptrdiff_t std_fs_write(long long handle, const void* buffer, size_t size) {
    if (size > 0x7fffffffffffffffULL) return (ptrdiff_t)CPLUS_PAL_INVALID_ARGUMENT;
    return (ptrdiff_t)platform_file_write(handle, buffer, (cplus_file_size_t)size);
}

int std_fs_close(long long handle) {
    return platform_file_close(handle);
}

int std_fs_rename(const char* source, const char* target) {
    return platform_file_rename(source, target);
}

long long std_fs_seek(long long handle, long long offset, unsigned int origin) {
    return platform_file_seek(handle, offset, origin);
}

int std_fs_metadata(const char* path, struct std_file_metadata_t* metadata) {
    cplus_file_metadata_t native_metadata;
    int result;
    if (!metadata) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    result = platform_file_metadata(path, &native_metadata);
    if (result < 0) return result;
    metadata->size_bytes = native_metadata.size_bytes;
    metadata->modified_seconds_utc = native_metadata.modified_seconds_utc;
    metadata->modified_nanoseconds = native_metadata.modified_nanoseconds;
    metadata->kind = native_metadata.kind;
    metadata->reserved0 = native_metadata.reserved0;
    metadata->reserved1 = native_metadata.reserved1;
    return 0;
}

int std_fs_create_directory(const char* path) {
    return platform_directory_create(path);
}

int std_fs_remove_file(const char* path) {
    return platform_file_remove(path);
}

int std_fs_remove_directory(const char* path) {
    return platform_directory_remove(path);
}

long long std_fs_directory_open(const char* path) {
    return platform_directory_open(path);
}

ptrdiff_t std_fs_directory_read(long long handle, char* utf8_name, size_t capacity) {
    return (ptrdiff_t)platform_directory_read(handle, utf8_name, (cplus_file_size_t)capacity);
}

int std_fs_directory_close(long long handle) {
    return platform_directory_close(handle);
}
