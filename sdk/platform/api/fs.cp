/// Uniform PAL filesystem ABI. Paths are canonical UTF-8 strings using `/`.
/// Mode bits and negative failures are defined by cplus_platform.h.
pub long long platform_file_open(char* path, long long mode);
pub long long platform_file_read(long long handle, void* buffer, long long length);
pub long long platform_file_write(long long handle, void* buffer, long long length);
pub int platform_file_close(long long handle);
pub int platform_file_rename(char* source, char* target);
