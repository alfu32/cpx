#include <stddef.h>
#include <stdint.h>

#if defined(_WIN32)

typedef void* __cplus_tls_callback;

typedef struct __cplus_tls_directory {
    size_t start_address_of_raw_data;
    size_t end_address_of_raw_data;
    size_t address_of_index;
    size_t address_of_callbacks;
    unsigned long size_of_zero_fill;
    unsigned long characteristics;
} __cplus_tls_directory;

_Static_assert(sizeof(void*) == 8, "Windows TLS directory adapter requires a 64-bit target");

#if defined(_MSC_VER)
#pragma section(".tls$AAA", read, write)
#pragma section(".tls$ZZZ", read, write)
#pragma section(".CRT$XLA", read)
#pragma section(".CRT$XLZ", read)
#pragma section(".rdata$T", read)
#define __CPLUS_TLS_ALLOC(section_name) __declspec(allocate(section_name))
#define __CPLUS_TLS_USED
#else
#define __CPLUS_TLS_ALLOC(section_name) __attribute__((section(section_name)))
#define __CPLUS_TLS_USED __attribute__((used))
#endif

unsigned long _tls_index;

__CPLUS_TLS_ALLOC(".tls$AAA") __CPLUS_TLS_USED char _tls_start;
__CPLUS_TLS_ALLOC(".tls$ZZZ") __CPLUS_TLS_USED char _tls_end;
__CPLUS_TLS_ALLOC(".CRT$XLA") __CPLUS_TLS_USED __cplus_tls_callback __xl_a;
__CPLUS_TLS_ALLOC(".CRT$XLZ") __CPLUS_TLS_USED __cplus_tls_callback __xl_z;

__CPLUS_TLS_ALLOC(".rdata$T") __CPLUS_TLS_USED
const __cplus_tls_directory _tls_used = {
    (size_t)&_tls_start,
    (size_t)&_tls_end,
    (size_t)&_tls_index,
    (size_t)(&__xl_a + 1),
    0,
    0
};

#endif
