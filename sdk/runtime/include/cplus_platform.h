#ifndef CPLUS_PLATFORM_H
#define CPLUS_PLATFORM_H

/* Uniform PAL ABI consumed by the C+ runtime and native std/libc facades. */
#define CPLUS_PAL_API_VERSION 3

#define CPLUS_FILE_READ 0x0001ULL
#define CPLUS_FILE_WRITE 0x0002ULL
#define CPLUS_FILE_CREATE 0x0004ULL
#define CPLUS_FILE_TRUNCATE 0x0008ULL
#define CPLUS_FILE_KIND_REGULAR 1U
#define CPLUS_FILE_KIND_DIRECTORY 2U
#define CPLUS_FILE_KIND_OTHER 3U
#define CPLUS_SEEK_BEGIN 0U
#define CPLUS_SEEK_CURRENT 1U
#define CPLUS_SEEK_END 2U

/* PAL calls return non-negative values on success and these stable negatives
   for failures. Target adapters must not leak errno/GetLastError values. */
#define CPLUS_PAL_INVALID_ARGUMENT (-2L)
#define CPLUS_PAL_NOT_FOUND (-3L)
#define CPLUS_PAL_ACCESS_DENIED (-4L)
#define CPLUS_PAL_IO_ERROR (-5L)
#define CPLUS_PAL_UNSUPPORTED (-6L)
#define CPLUS_PAL_BUFFER_TOO_SMALL (-7L)

typedef long long cplus_process_handle_t;

long platform_write_stdout(const char* buffer, unsigned long length);
int platform_process_exit(int status);
long long platform_process_id(void);
cplus_process_handle_t platform_process_spawn(
    const char* executable,
    const char* const* arguments);
int platform_process_wait(cplus_process_handle_t process, int* exit_status);
long long platform_clock_ticks(void);
typedef long long cplus_file_handle_t;
typedef long long cplus_file_result_t;
typedef unsigned long long cplus_file_size_t;
typedef unsigned long long cplus_file_mode_t;

typedef struct cplus_file_metadata_t {
    unsigned long long size_bytes;
    long long modified_seconds_utc;
    unsigned int modified_nanoseconds;
    unsigned int kind;
    unsigned int reserved0;
    unsigned int reserved1;
} cplus_file_metadata_t;

#define CPLUS_PAL_PAGE_SIZE 4096ULL

void* platform_page_allocate(unsigned long long page_count);
int platform_page_release(void* address, unsigned long long page_count);

cplus_file_result_t platform_file_open(const char* path, cplus_file_mode_t mode);
cplus_file_result_t platform_file_read(cplus_file_handle_t handle, void* buffer, cplus_file_size_t length);
cplus_file_result_t platform_file_write(cplus_file_handle_t handle, const void* buffer, cplus_file_size_t length);
int platform_file_close(cplus_file_handle_t handle);
int platform_file_rename(const char* source, const char* target);
long long platform_file_seek(cplus_file_handle_t handle, long long offset, unsigned int origin);
int platform_file_metadata(const char* path, cplus_file_metadata_t* metadata);
int platform_directory_create(const char* path);
int platform_file_remove(const char* path);
int platform_directory_remove(const char* path);
cplus_file_result_t platform_directory_open(const char* path);
long long platform_directory_read(cplus_file_handle_t handle, char* utf8_name, cplus_file_size_t capacity);
int platform_directory_close(cplus_file_handle_t handle);

#endif
