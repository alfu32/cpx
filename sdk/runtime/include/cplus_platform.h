#ifndef CPLUS_PLATFORM_H
#define CPLUS_PLATFORM_H

/* Uniform PAL ABI consumed by the C+ runtime and native std/libc facades. */
#define CPLUS_PAL_API_VERSION 2

#define CPLUS_FILE_READ 0x0001ULL
#define CPLUS_FILE_WRITE 0x0002ULL
#define CPLUS_FILE_CREATE 0x0004ULL
#define CPLUS_FILE_TRUNCATE 0x0008ULL

/* PAL calls return non-negative values on success and these stable negatives
   for failures. Target adapters must not leak errno/GetLastError values. */
#define CPLUS_PAL_INVALID_ARGUMENT (-2L)
#define CPLUS_PAL_NOT_FOUND (-3L)
#define CPLUS_PAL_ACCESS_DENIED (-4L)
#define CPLUS_PAL_IO_ERROR (-5L)
#define CPLUS_PAL_UNSUPPORTED (-6L)

long platform_write_stdout(const char* buffer, unsigned long length);
int platform_process_exit(int status);
typedef long long cplus_file_handle_t;
typedef long long cplus_file_result_t;
typedef unsigned long long cplus_file_size_t;
typedef unsigned long long cplus_file_mode_t;

cplus_file_result_t platform_file_open(const char* path, cplus_file_mode_t mode);
cplus_file_result_t platform_file_read(cplus_file_handle_t handle, void* buffer, cplus_file_size_t length);
cplus_file_result_t platform_file_write(cplus_file_handle_t handle, const void* buffer, cplus_file_size_t length);
int platform_file_close(cplus_file_handle_t handle);
int platform_file_rename(const char* source, const char* target);

#endif
