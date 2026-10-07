#include "cplus_platform.h"

/* Native std.fs entry points remain target-independent. The selected PAL
   supplies path conversion, OS handles, and error normalization. */
long long std_fs_open(const char* path, unsigned long long mode) {
    return platform_file_open(path, mode);
}

long long std_fs_read(long long handle, void* buffer, long long size) {
    if (size < 0) return CPLUS_PAL_INVALID_ARGUMENT;
    return platform_file_read(handle, buffer, (unsigned long long)size);
}

long long std_fs_write(long long handle, const void* buffer, long long size) {
    if (size < 0) return CPLUS_PAL_INVALID_ARGUMENT;
    return platform_file_write(handle, buffer, (unsigned long long)size);
}

int std_fs_close(long long handle) {
    return platform_file_close(handle);
}

int std_fs_rename(const char* source, const char* target) {
    return platform_file_rename(source, target);
}
