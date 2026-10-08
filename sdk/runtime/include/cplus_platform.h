#ifndef CPLUS_PLATFORM_H
#define CPLUS_PLATFORM_H

/* Uniform PAL ABI consumed by the C+ runtime and native std/libc facades. */
#define CPLUS_PAL_API_VERSION 4

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
#define CPLUS_PAL_NETWORK_ERROR (-8L)

#define CPLUS_SOCKET_IPV4 4U
#define CPLUS_SOCKET_IPV6 6U
#define CPLUS_SOCKET_STREAM 1U
#define CPLUS_SOCKET_DATAGRAM 2U
#define CPLUS_SOCKET_SHUTDOWN_RECEIVE 0U
#define CPLUS_SOCKET_SHUTDOWN_SEND 1U
#define CPLUS_SOCKET_SHUTDOWN_BOTH 2U

typedef long long cplus_process_handle_t;
typedef long long cplus_thread_handle_t;
typedef long long cplus_socket_handle_t;
typedef void* (*cplus_thread_entry_t)(void* context);

typedef struct cplus_socket_address_t {
    unsigned int family;
    unsigned short port;
    unsigned short reserved;
    unsigned char address[16];
    unsigned int scope_id;
} cplus_socket_address_t;

long long platform_read_stdin(void* buffer, unsigned long long capacity);
long long platform_write_stdout(const char* buffer, unsigned long long length);
long long platform_write_stderr(const char* buffer, unsigned long long length);
int platform_process_exit(int status);
long long platform_process_id(void);
unsigned long long platform_process_argument_count(void);
const char* platform_process_argument(unsigned long long index);
const char* const* platform_process_environment(void);
cplus_process_handle_t platform_process_spawn(
    const char* executable,
    const char* const* arguments);
int platform_process_wait(cplus_process_handle_t process, int* exit_status);
cplus_thread_handle_t platform_thread_create(cplus_thread_entry_t entry, void* context);
int platform_thread_join(cplus_thread_handle_t thread, void** result);
long long platform_thread_current_id(void);
int platform_thread_yield(void);
cplus_socket_handle_t platform_socket_open(unsigned int family, unsigned int kind);
int platform_socket_bind(cplus_socket_handle_t socket, const cplus_socket_address_t* address);
int platform_socket_listen(cplus_socket_handle_t socket, int backlog);
cplus_socket_handle_t platform_socket_accept(cplus_socket_handle_t socket, cplus_socket_address_t* peer);
int platform_socket_connect(cplus_socket_handle_t socket, const cplus_socket_address_t* address);
int platform_socket_get_address(cplus_socket_handle_t socket, int peer, cplus_socket_address_t* address);
long long platform_socket_send(cplus_socket_handle_t socket, const void* buffer, unsigned long long length);
long long platform_socket_receive(cplus_socket_handle_t socket, void* buffer, unsigned long long capacity);
long long platform_socket_send_to(cplus_socket_handle_t socket, const void* buffer, unsigned long long length, const cplus_socket_address_t* destination);
long long platform_socket_receive_from(cplus_socket_handle_t socket, void* buffer, unsigned long long capacity, cplus_socket_address_t* source);
int platform_socket_shutdown(cplus_socket_handle_t socket, unsigned int direction);
int platform_socket_close(cplus_socket_handle_t socket);
int platform_mutex_init(volatile int* state);
int platform_mutex_lock(volatile int* state);
int platform_mutex_unlock(volatile int* state);
int platform_condition_init(volatile int* sequence);
int platform_condition_wait(volatile int* sequence, volatile int* mutex_state);
int platform_condition_signal(volatile int* sequence);
int platform_condition_broadcast(volatile int* sequence);
int platform_semaphore_init(volatile int* count, int initial_count);
int platform_semaphore_wait(volatile int* count);
int platform_semaphore_post(volatile int* count);
int platform_once_init(volatile int* state);
int platform_once_enter(volatile int* state);
int platform_once_complete(volatile int* state);
int platform_atomic_wait32(volatile int* address, int expected);
int platform_atomic_wake32(volatile int* address, unsigned int count);
long long platform_clock_wall_nanoseconds(void);
long long platform_clock_monotonic_nanoseconds(void);
long long platform_clock_process_cpu_nanoseconds(void);
/* Compatibility alias for the monotonic nanosecond clock. */
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
