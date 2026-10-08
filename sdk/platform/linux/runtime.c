#include "cplus_platform.h"

char** __cplus_environment;

static long cplus_normalize_linux_result(long result) {
    long error;
    if (result >= 0) return result;
    error = -result;
    if (error == 2) return CPLUS_PAL_NOT_FOUND;
    if (error == 13) return CPLUS_PAL_ACCESS_DENIED;
    if (error == 22) return CPLUS_PAL_INVALID_ARGUMENT;
    if (error == 38) return CPLUS_PAL_UNSUPPORTED;
    return CPLUS_PAL_IO_ERROR;
}

#if defined(__x86_64__)
static long cplus_linux_syscall1(long number, long first) {
    register long result __asm__("rax") = number;
    __asm__ volatile("syscall" : "+a"(result) : "D"(first) : "rcx", "r11", "memory");
    return result;
}

static long cplus_linux_syscall2(long number, long first, long second) {
    register long result __asm__("rax") = number;
    __asm__ volatile("syscall" : "+a"(result) : "D"(first), "S"(second) : "rcx", "r11", "memory");
    return result;
}

static long cplus_linux_syscall3(long number, long first, long second, long third) {
    register long result __asm__("rax") = number;
    __asm__ volatile("syscall" : "+a"(result) : "D"(first), "S"(second), "d"(third) : "rcx", "r11", "memory");
    return result;
}

static long cplus_linux_syscall4(long number, long first, long second, long third, long fourth) {
    register long result __asm__("rax") = number;
    register long fourth_register __asm__("r10") = fourth;
    __asm__ volatile("syscall" : "+a"(result) : "D"(first), "S"(second), "d"(third), "r"(fourth_register) : "rcx", "r11", "memory");
    return result;
}

static long cplus_linux_syscall6(long number, long first, long second, long third, long fourth, long fifth, long sixth) {
    register long result __asm__("rax") = number;
    register long fourth_register __asm__("r10") = fourth;
    register long fifth_register __asm__("r8") = fifth;
    register long sixth_register __asm__("r9") = sixth;
    __asm__ volatile("syscall" : "+a"(result) : "D"(first), "S"(second), "d"(third), "r"(fourth_register), "r"(fifth_register), "r"(sixth_register) : "rcx", "r11", "memory");
    return result;
}
#elif defined(__aarch64__)
static long cplus_linux_syscall1(long number, long first) {
    register long result __asm__("x0") = first;
    register long syscall_number __asm__("x8") = number;
    __asm__ volatile("svc 0" : "+r"(result) : "r"(syscall_number) : "memory");
    return result;
}

static long cplus_linux_syscall2(long number, long first, long second) {
    register long result __asm__("x0") = first;
    register long second_register __asm__("x1") = second;
    register long syscall_number __asm__("x8") = number;
    __asm__ volatile("svc 0" : "+r"(result) : "r"(second_register), "r"(syscall_number) : "memory");
    return result;
}

static long cplus_linux_syscall3(long number, long first, long second, long third) {
    register long result __asm__("x0") = first;
    register long second_register __asm__("x1") = second;
    register long third_register __asm__("x2") = third;
    register long syscall_number __asm__("x8") = number;
    __asm__ volatile("svc 0" : "+r"(result) : "r"(second_register), "r"(third_register), "r"(syscall_number) : "memory");
    return result;
}

static long cplus_linux_syscall4(long number, long first, long second, long third, long fourth) {
    register long result __asm__("x0") = first;
    register long second_register __asm__("x1") = second;
    register long third_register __asm__("x2") = third;
    register long fourth_register __asm__("x3") = fourth;
    register long syscall_number __asm__("x8") = number;
    __asm__ volatile("svc 0" : "+r"(result) : "r"(second_register), "r"(third_register), "r"(fourth_register), "r"(syscall_number) : "memory");
    return result;
}

static long cplus_linux_syscall6(long number, long first, long second, long third, long fourth, long fifth, long sixth) {
    register long result __asm__("x0") = first;
    register long second_register __asm__("x1") = second;
    register long third_register __asm__("x2") = third;
    register long fourth_register __asm__("x3") = fourth;
    register long fifth_register __asm__("x4") = fifth;
    register long sixth_register __asm__("x5") = sixth;
    register long syscall_number __asm__("x8") = number;
    __asm__ volatile("svc 0" : "+r"(result) : "r"(second_register), "r"(third_register), "r"(fourth_register), "r"(fifth_register), "r"(sixth_register), "r"(syscall_number) : "memory");
    return result;
}
#endif

struct cplus_linux_statx_timestamp {
    long long seconds;
    unsigned int nanoseconds;
    int reserved;
};

struct cplus_linux_statx {
    unsigned int mask;
    unsigned int block_size;
    unsigned long long attributes;
    unsigned int link_count;
    unsigned int user_id;
    unsigned int group_id;
    unsigned short mode;
    unsigned short reserved0;
    unsigned long long inode;
    unsigned long long size;
    unsigned long long blocks;
    unsigned long long attributes_mask;
    struct cplus_linux_statx_timestamp access_time;
    struct cplus_linux_statx_timestamp birth_time;
    struct cplus_linux_statx_timestamp change_time;
    struct cplus_linux_statx_timestamp modification_time;
    unsigned int device_major;
    unsigned int device_minor;
    unsigned int filesystem_major;
    unsigned int filesystem_minor;
    unsigned long long mount_id;
    unsigned int direct_io_memory_alignment;
    unsigned int direct_io_offset_alignment;
    unsigned long long reserved1[12];
};

struct cplus_linux_dirent64 {
    unsigned long long inode;
    long long offset;
    unsigned short record_length;
    unsigned char type;
    char name[];
};

_Static_assert(sizeof(struct cplus_linux_statx_timestamp) == 16, "Linux statx timestamp ABI changed");
_Static_assert(sizeof(struct cplus_linux_statx) == 256, "Linux statx ABI changed");

static int cplus_linux_utf8_is_valid(const unsigned char* text, unsigned long long length) {
    unsigned long long index = 0;
    while (index < length) {
        unsigned char first = text[index++];
        unsigned int code_point;
        unsigned int continuation_count;
        unsigned int minimum;
        unsigned int continuation_index;
        if (first <= 0x7f) continue;
        if (first >= 0xc2 && first <= 0xdf) {
            code_point = first & 0x1f;
            continuation_count = 1;
            minimum = 0x80;
        } else if (first >= 0xe0 && first <= 0xef) {
            code_point = first & 0x0f;
            continuation_count = 2;
            minimum = 0x800;
        } else if (first >= 0xf0 && first <= 0xf4) {
            code_point = first & 0x07;
            continuation_count = 3;
            minimum = 0x10000;
        } else {
            return 0;
        }
        if (length - index < continuation_count) return 0;
        for (continuation_index = 0; continuation_index < continuation_count; continuation_index++) {
            unsigned char continuation = text[index++];
            if ((continuation & 0xc0) != 0x80) return 0;
            code_point = (code_point << 6) | (continuation & 0x3f);
        }
        if (code_point < minimum || code_point > 0x10ffff ||
            (code_point >= 0xd800 && code_point <= 0xdfff)) return 0;
    }
    return 1;
}

void* platform_page_allocate(unsigned long long page_count) {
    unsigned long long bytes;
    if (page_count == 0 || page_count > 0x7fffffffffffffffULL / CPLUS_PAL_PAGE_SIZE) return (void*)0;
    bytes = page_count * CPLUS_PAL_PAGE_SIZE;
#if defined(__x86_64__)
    {
        long result = cplus_linux_syscall6(9, 0, (long)bytes, 3, 0x22, -1, 0);
        return result < 0 ? (void*)0 : (void*)result;
    }
#elif defined(__aarch64__)
    {
        long result = cplus_linux_syscall6(222, 0, (long)bytes, 3, 0x22, -1, 0);
        return result < 0 ? (void*)0 : (void*)result;
    }
#else
    (void)bytes;
    return (void*)0;
#endif
}

int platform_page_release(void* address, unsigned long long page_count) {
    unsigned long long bytes;
    if (!address || page_count == 0 || page_count > 0x7fffffffffffffffULL / CPLUS_PAL_PAGE_SIZE) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    bytes = page_count * CPLUS_PAL_PAGE_SIZE;
#if defined(__x86_64__)
    return (int)cplus_normalize_linux_result(cplus_linux_syscall2(11, (long)address, (long)bytes));
#elif defined(__aarch64__)
    return (int)cplus_normalize_linux_result(cplus_linux_syscall2(215, (long)address, (long)bytes));
#else
    (void)bytes;
    return (int)CPLUS_PAL_UNSUPPORTED;
#endif
}

typedef struct cplus_linux_thread_control {
    volatile int tid;
    void* result;
    cplus_thread_entry_t entry;
    void* context;
    void* stack;
    unsigned long long stack_pages;
    void* tls_memory;
    unsigned long long tls_pages;
} cplus_linux_thread_control;

extern unsigned char __cplus_tls_image_start[] __attribute__((weak));
extern unsigned char __cplus_tls_data_size[] __attribute__((weak));
extern unsigned char __cplus_tls_image_end[] __attribute__((weak));
extern unsigned char __cplus_tls_alignment[] __attribute__((weak));
extern long cplus_linux_clone_thread(
    unsigned long flags,
    void* child_stack,
    int* child_tid,
    void* thread_pointer,
    void (*start)(void*),
    void* context) __attribute__((weak));
extern int __cplus_runtime_thread_attach(void) __attribute__((weak));
extern void cplus_linux_set_thread_pointer(void* thread_pointer);

#define CPLUS_LINUX_THREAD_STACK_PAGES 256ULL
/* AArch64's variant-I TLS block begins after its 16-byte TCB. */
#define CPLUS_LINUX_THREAD_TLS_CONTROL_BYTES 16ULL
#define CPLUS_LINUX_CLONE_VM 0x00000100UL
#define CPLUS_LINUX_CLONE_FS 0x00000200UL
#define CPLUS_LINUX_CLONE_FILES 0x00000400UL
#define CPLUS_LINUX_CLONE_SIGHAND 0x00000800UL
#define CPLUS_LINUX_CLONE_THREAD 0x00010000UL
#define CPLUS_LINUX_CLONE_SYSVSEM 0x00040000UL
#define CPLUS_LINUX_CLONE_SETTLS 0x00080000UL
#define CPLUS_LINUX_CLONE_PARENT_SETTID 0x00100000UL
#define CPLUS_LINUX_CLONE_CHILD_CLEARTID 0x00200000UL
#define CPLUS_LINUX_CLONE_CHILD_SETTID 0x01000000UL
#define CPLUS_LINUX_THREAD_CLONE_FLAGS (CPLUS_LINUX_CLONE_VM | CPLUS_LINUX_CLONE_FS | \
    CPLUS_LINUX_CLONE_FILES | CPLUS_LINUX_CLONE_SIGHAND | CPLUS_LINUX_CLONE_THREAD | \
    CPLUS_LINUX_CLONE_SYSVSEM | CPLUS_LINUX_CLONE_SETTLS | \
    CPLUS_LINUX_CLONE_PARENT_SETTID | CPLUS_LINUX_CLONE_CHILD_CLEARTID | \
    CPLUS_LINUX_CLONE_CHILD_SETTID)

static unsigned long long cplus_linux_align_up(unsigned long long value, unsigned long long alignment) {
    if (!alignment || (alignment & (alignment - 1ULL)) != 0 ||
        value > 0xffffffffffffffffULL - (alignment - 1ULL)) return 0;
    return (value + alignment - 1ULL) & ~(alignment - 1ULL);
}

static void cplus_linux_thread_release(cplus_linux_thread_control* thread) {
    if (thread->stack) platform_page_release(thread->stack, thread->stack_pages);
    if (thread->tls_memory) platform_page_release(thread->tls_memory, thread->tls_pages);
    platform_page_release(thread, 1);
}

static void cplus_linux_thread_start(void* context) {
    cplus_linux_thread_control* thread = (cplus_linux_thread_control*)context;
    __cplus_runtime_thread_attach();
    thread->result = thread->entry(thread->context);
    __atomic_thread_fence(__ATOMIC_RELEASE);
#if defined(__x86_64__)
    cplus_linux_syscall1(60, 0);
#elif defined(__aarch64__)
    cplus_linux_syscall1(93, 0);
#endif
    for (;;) { }
}

int __cplus_linux_initialize_main_tls(void) {
    unsigned long long tls_image_start = (unsigned long long)__cplus_tls_image_start;
    unsigned long long tls_data_size = (unsigned long long)__cplus_tls_data_size;
    unsigned long long tls_image_end = (unsigned long long)__cplus_tls_image_end;
    unsigned long long tls_alignment = (unsigned long long)__cplus_tls_alignment;
    unsigned long long tls_size;
    unsigned long long allocation_bytes;
    unsigned long long tls_pages;
    unsigned long long index;
    void* tls_memory;
    unsigned char* tls_image;
    unsigned long long thread_pointer;
#if defined(__x86_64__)
    long result;
#endif
    if (!tls_image_start || !tls_image_end || !tls_alignment) return (int)CPLUS_PAL_UNSUPPORTED;
    if (tls_image_end < tls_image_start || tls_data_size > tls_image_end - tls_image_start ||
        (tls_alignment & (tls_alignment - 1ULL)) != 0) return (int)CPLUS_PAL_IO_ERROR;
    tls_size = cplus_linux_align_up(tls_image_end - tls_image_start, tls_alignment);
    if (!tls_size || tls_size > 0x7fffffffffffffffULL - tls_alignment - CPLUS_LINUX_THREAD_TLS_CONTROL_BYTES) {
        return (int)CPLUS_PAL_IO_ERROR;
    }
    allocation_bytes = tls_size + tls_alignment + CPLUS_LINUX_THREAD_TLS_CONTROL_BYTES;
    tls_pages = (allocation_bytes + CPLUS_PAL_PAGE_SIZE - 1ULL) / CPLUS_PAL_PAGE_SIZE;
    if (!tls_pages || tls_pages > 0x7fffffffffffffffULL / CPLUS_PAL_PAGE_SIZE) return (int)CPLUS_PAL_IO_ERROR;
    tls_memory = platform_page_allocate(tls_pages);
    if (!tls_memory) return (int)CPLUS_PAL_IO_ERROR;
#if defined(__x86_64__)
    thread_pointer = cplus_linux_align_up((unsigned long long)tls_memory + tls_size, tls_alignment);
    tls_image = (unsigned char*)(thread_pointer - tls_size);
    *((void**)thread_pointer) = (void*)thread_pointer;
    result = cplus_linux_syscall2(158, 0x1002, (long)thread_pointer);
    if (result < 0) {
        platform_page_release(tls_memory, tls_pages);
        return (int)cplus_normalize_linux_result(result);
    }
#elif defined(__aarch64__)
    tls_image = (unsigned char*)cplus_linux_align_up(
        (unsigned long long)tls_memory + CPLUS_LINUX_THREAD_TLS_CONTROL_BYTES,
        tls_alignment);
    thread_pointer = (unsigned long long)tls_image - CPLUS_LINUX_THREAD_TLS_CONTROL_BYTES;
    ((unsigned long long*)thread_pointer)[0] = 0;
    ((unsigned long long*)thread_pointer)[1] = thread_pointer;
    cplus_linux_set_thread_pointer((void*)thread_pointer);
#else
    platform_page_release(tls_memory, tls_pages);
    return (int)CPLUS_PAL_UNSUPPORTED;
#endif
    for (index = 0; index < tls_data_size; index++) tls_image[index] = __cplus_tls_image_start[index];
    return 0;
}

long long platform_thread_create(cplus_thread_entry_t entry, void* context) {
    unsigned long long tls_image_start = (unsigned long long)__cplus_tls_image_start;
    unsigned long long tls_data_size = (unsigned long long)__cplus_tls_data_size;
    unsigned long long tls_image_end = (unsigned long long)__cplus_tls_image_end;
    unsigned long long tls_alignment = (unsigned long long)__cplus_tls_alignment;
    unsigned long long tls_size;
    unsigned long long tls_allocation_bytes;
    unsigned long long tls_pages;
    unsigned long long stack_bytes = CPLUS_LINUX_THREAD_STACK_PAGES * CPLUS_PAL_PAGE_SIZE;
    unsigned long long stack_pages = CPLUS_LINUX_THREAD_STACK_PAGES;
    unsigned long long index;
    unsigned long flags = CPLUS_LINUX_THREAD_CLONE_FLAGS;
    cplus_linux_thread_control* thread;
    unsigned char* tls_image;
    unsigned long long thread_pointer;
    long clone_result;
    if (!entry) return CPLUS_PAL_INVALID_ARGUMENT;
    if (!cplus_linux_clone_thread || !__cplus_runtime_thread_attach) return CPLUS_PAL_UNSUPPORTED;
    if (!tls_image_start || !tls_image_end || !tls_alignment) return CPLUS_PAL_UNSUPPORTED;
    if (tls_image_end < tls_image_start || tls_data_size > tls_image_end - tls_image_start ||
        !tls_alignment || (tls_alignment & (tls_alignment - 1ULL)) != 0) return CPLUS_PAL_IO_ERROR;
    tls_size = cplus_linux_align_up(tls_image_end - tls_image_start, tls_alignment);
    if (!tls_size || tls_size > 0x7fffffffffffffffULL - tls_alignment - CPLUS_LINUX_THREAD_TLS_CONTROL_BYTES) {
        return CPLUS_PAL_IO_ERROR;
    }
    tls_allocation_bytes = tls_size + tls_alignment + CPLUS_LINUX_THREAD_TLS_CONTROL_BYTES;
    tls_pages = (tls_allocation_bytes + CPLUS_PAL_PAGE_SIZE - 1ULL) / CPLUS_PAL_PAGE_SIZE;
    if (!tls_pages || tls_pages > 0x7fffffffffffffffULL / CPLUS_PAL_PAGE_SIZE) return CPLUS_PAL_IO_ERROR;

    thread = (cplus_linux_thread_control*)platform_page_allocate(1);
    if (!thread) return CPLUS_PAL_IO_ERROR;
    thread->stack = (void*)0;
    thread->tls_memory = (void*)0;
    thread->stack_pages = stack_pages;
    thread->tls_pages = tls_pages;
    thread->entry = entry;
    thread->context = context;
    thread->result = (void*)0;
    thread->tid = 0;
    thread->stack = platform_page_allocate(stack_pages);
    if (!thread->stack) {
        cplus_linux_thread_release(thread);
        return CPLUS_PAL_IO_ERROR;
    }
    thread->tls_memory = platform_page_allocate(tls_pages);
    if (!thread->tls_memory) {
        cplus_linux_thread_release(thread);
        return CPLUS_PAL_IO_ERROR;
    }
#if defined(__x86_64__)
    thread_pointer = cplus_linux_align_up(
        (unsigned long long)thread->tls_memory + tls_size, tls_alignment);
    tls_image = (unsigned char*)(thread_pointer - tls_size);
    *((void**)thread_pointer) = (void*)thread_pointer;
#elif defined(__aarch64__)
    tls_image = (unsigned char*)cplus_linux_align_up(
        (unsigned long long)thread->tls_memory + CPLUS_LINUX_THREAD_TLS_CONTROL_BYTES,
        tls_alignment);
    thread_pointer = (unsigned long long)tls_image - CPLUS_LINUX_THREAD_TLS_CONTROL_BYTES;
    ((unsigned long long*)thread_pointer)[0] = 0;
    ((unsigned long long*)thread_pointer)[1] = thread_pointer;
#else
    cplus_linux_thread_release(thread);
    return CPLUS_PAL_UNSUPPORTED;
#endif
    for (index = 0; index < tls_data_size; index++) tls_image[index] = __cplus_tls_image_start[index];

    clone_result = cplus_linux_clone_thread(
        flags,
        (unsigned char*)thread->stack + stack_bytes,
        (int*)&thread->tid,
        (void*)thread_pointer,
        cplus_linux_thread_start,
        thread);
    if (clone_result < 0) {
        cplus_linux_thread_release(thread);
        return cplus_normalize_linux_result(clone_result);
    }
    return (long long)(long)thread;
}

int platform_thread_join(long long handle, void** result) {
    cplus_linux_thread_control* thread = (cplus_linux_thread_control*)(long)handle;
    if (handle <= 0 || !thread) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    for (;;) {
        int tid = __atomic_load_n((int*)&thread->tid, __ATOMIC_ACQUIRE);
        long wait_result;
        if (tid == 0) break;
#if defined(__x86_64__)
        wait_result = cplus_linux_syscall6(202, (long)&thread->tid, 0, tid, 0, 0, 0);
#elif defined(__aarch64__)
        wait_result = cplus_linux_syscall6(98, (long)&thread->tid, 0, tid, 0, 0, 0);
#else
        return (int)CPLUS_PAL_UNSUPPORTED;
#endif
        if (wait_result < 0 && wait_result != -4 && wait_result != -11) {
            return (int)cplus_normalize_linux_result(wait_result);
        }
    }
    __atomic_thread_fence(__ATOMIC_ACQUIRE);
    if (result) *result = thread->result;
    cplus_linux_thread_release(thread);
    return 0;
}

long long platform_thread_current_id(void) {
#if defined(__x86_64__)
    return cplus_normalize_linux_result(cplus_linux_syscall1(186, 0));
#elif defined(__aarch64__)
    return cplus_normalize_linux_result(cplus_linux_syscall1(178, 0));
#else
    return CPLUS_PAL_UNSUPPORTED;
#endif
}

int platform_thread_yield(void) {
#if defined(__x86_64__)
    return (int)cplus_normalize_linux_result(cplus_linux_syscall1(24, 0));
#elif defined(__aarch64__)
    return (int)cplus_normalize_linux_result(cplus_linux_syscall1(124, 0));
#else
    return (int)CPLUS_PAL_UNSUPPORTED;
#endif
}

static int cplus_linux_valid_atomic32(const volatile int* address) {
    return address && (((unsigned long long)(const void*)address & 3ULL) == 0);
}

int platform_atomic_wait32(volatile int* address, int expected) {
    long result;
    if (!cplus_linux_valid_atomic32(address)) return (int)CPLUS_PAL_INVALID_ARGUMENT;
#if defined(__x86_64__)
    result = cplus_linux_syscall6(202, (long)address, 128, expected, 0, 0, 0);
#elif defined(__aarch64__)
    result = cplus_linux_syscall6(98, (long)address, 128, expected, 0, 0, 0);
#else
    (void)expected;
    return (int)CPLUS_PAL_UNSUPPORTED;
#endif
    if (result == 0 || result == -4 || result == -11) return 0;
    return result < 0 ? (int)cplus_normalize_linux_result(result) : 0;
}

int platform_atomic_wake32(volatile int* address, unsigned int count) {
    long result;
    unsigned int wake_count;
    if (!cplus_linux_valid_atomic32(address)) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    if (count == 0) return 0;
    wake_count = count > 0x7fffffffU ? 0x7fffffffU : count;
#if defined(__x86_64__)
    result = cplus_linux_syscall6(202, (long)address, 129, wake_count, 0, 0, 0);
#elif defined(__aarch64__)
    result = cplus_linux_syscall6(98, (long)address, 129, wake_count, 0, 0, 0);
#else
    return (int)CPLUS_PAL_UNSUPPORTED;
#endif
    return result < 0 ? (int)cplus_normalize_linux_result(result) : 0;
}

static long cplus_linux_process_fork(void) {
#if defined(__x86_64__)
    return cplus_linux_syscall1(57, 0);
#elif defined(__aarch64__)
    return cplus_linux_syscall6(220, 17, 0, 0, 0, 0, 0);
#else
    return -38;
#endif
}

static long cplus_linux_process_execve(const char* executable, const char* const* arguments, const char* const* environment) {
#if defined(__x86_64__)
    return cplus_linux_syscall3(59, (long)executable, (long)arguments, (long)environment);
#elif defined(__aarch64__)
    return cplus_linux_syscall3(221, (long)executable, (long)arguments, (long)environment);
#else
    (void)executable; (void)arguments; (void)environment;
    return -38;
#endif
}

static long cplus_linux_process_wait4(long process, int* status) {
#if defined(__x86_64__)
    return cplus_linux_syscall4(61, process, (long)status, 0, 0);
#elif defined(__aarch64__)
    return cplus_linux_syscall4(260, process, (long)status, 0, 0);
#else
    (void)process; (void)status;
    return -38;
#endif
}

long long platform_process_id(void) {
#if defined(__x86_64__)
    return cplus_linux_syscall1(39, 0);
#elif defined(__aarch64__)
    return cplus_linux_syscall1(172, 0);
#else
    return CPLUS_PAL_UNSUPPORTED;
#endif
}

long long platform_process_spawn(const char* executable, const char* const* arguments) {
    static const char* const empty_environment[] = { (const char*)0 };
    const char* generated_arguments[2];
    const char* const* child_arguments = arguments;
    const char* const* child_environment = (const char* const*)__cplus_environment;
    int error_pipe[2];
    long result;
    long child;
    int child_error = 0;

    if (!executable || executable[0] == '\0' || (arguments && !arguments[0])) {
        return CPLUS_PAL_INVALID_ARGUMENT;
    }
    if (!child_arguments) {
        generated_arguments[0] = executable;
        generated_arguments[1] = (const char*)0;
        child_arguments = generated_arguments;
    }
    if (!child_environment) child_environment = empty_environment;

#if defined(__x86_64__)
    result = cplus_linux_syscall2(293, (long)error_pipe, 0x80000);
#elif defined(__aarch64__)
    result = cplus_linux_syscall2(59, (long)error_pipe, 0x80000);
#else
    return CPLUS_PAL_UNSUPPORTED;
#endif
    if (result < 0) return cplus_normalize_linux_result(result);

    child = cplus_linux_process_fork();
    if (child < 0) {
        cplus_linux_syscall1(3, error_pipe[0]);
        cplus_linux_syscall1(3, error_pipe[1]);
        return cplus_normalize_linux_result(child);
    }
    if (child == 0) {
        long exec_result;
        int error_number;
        cplus_linux_syscall1(3, error_pipe[0]);
        exec_result = cplus_linux_process_execve(executable, child_arguments, child_environment);
        error_number = (int)-exec_result;
        cplus_linux_syscall3(1, error_pipe[1], (long)&error_number, sizeof(error_number));
#if defined(__x86_64__)
        cplus_linux_syscall1(60, 127);
#elif defined(__aarch64__)
        cplus_linux_syscall1(93, 127);
#endif
        for (;;) { }
    }

    cplus_linux_syscall1(3, error_pipe[1]);
    do {
        result = cplus_linux_syscall3(0, error_pipe[0], (long)&child_error, sizeof(child_error));
    } while (result == -4);
    cplus_linux_syscall1(3, error_pipe[0]);
    if (result == 0) return child;

    if (result == (long)sizeof(child_error)) {
        int ignored_status;
        long waited;
        do {
            waited = cplus_linux_process_wait4(child, &ignored_status);
        } while (waited == -4);
        return cplus_normalize_linux_result(-(long)child_error);
    }

    {
        int ignored_status;
        long waited;
        do {
            waited = cplus_linux_process_wait4(child, &ignored_status);
        } while (waited == -4);
    }
    return result < 0 ? cplus_normalize_linux_result(result) : CPLUS_PAL_IO_ERROR;
}

int platform_process_wait(long long process, int* exit_status) {
    int status;
    long result;
    if (process <= 0 || !exit_status) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    do {
        result = cplus_linux_process_wait4((long)process, &status);
    } while (result == -4);
    result = cplus_normalize_linux_result(result);
    if (result < 0) return (int)result;
    if ((status & 0x7f) == 0) *exit_status = (status >> 8) & 0xff;
    else *exit_status = 128 + (status & 0x7f);
    return 0;
}

static long long cplus_linux_standard_io(long descriptor, void* buffer, unsigned long long length, int reading) {
    long result;
    long syscall_number;
    if ((!buffer && length != 0) || length > 0x7fffffffffffffffULL) return CPLUS_PAL_INVALID_ARGUMENT;
    if (length == 0) return 0;
#if defined(__x86_64__)
    syscall_number = reading ? 0 : 1;
#elif defined(__aarch64__)
    syscall_number = reading ? 63 : 64;
#else
    (void)descriptor;
    (void)reading;
    return CPLUS_PAL_UNSUPPORTED;
#endif
    do {
        result = cplus_linux_syscall3(syscall_number, descriptor, (long)buffer, (long)length);
    } while (result == -4);
    return cplus_normalize_linux_result(result);
}

long long platform_read_stdin(void* buffer, unsigned long long capacity) {
    return cplus_linux_standard_io(0, buffer, capacity, 1);
}

long long platform_write_stdout(const char* buffer, unsigned long long length) {
    return cplus_linux_standard_io(1, (void*)buffer, length, 0);
}

long long platform_write_stderr(const char* buffer, unsigned long long length) {
    return cplus_linux_standard_io(2, (void*)buffer, length, 0);
}

long long platform_file_open(const char* path, unsigned long long mode) {
    long access;
    long flags = 0;
    if (!path || (mode & (CPLUS_FILE_READ | CPLUS_FILE_WRITE)) == 0) return CPLUS_PAL_INVALID_ARGUMENT;
    access = (mode & CPLUS_FILE_WRITE) ? ((mode & CPLUS_FILE_READ) ? 2 : 1) : 0;
    if (access == 2) flags |= 2;
    else if (access == 1) flags |= 1;
    if (mode & CPLUS_FILE_CREATE) flags |= 64;
    if (mode & CPLUS_FILE_TRUNCATE) flags |= 512;
#if defined(__x86_64__)
    return cplus_normalize_linux_result(cplus_linux_syscall4(257, -100, (long)path, flags, 0666));
#elif defined(__aarch64__)
    return cplus_normalize_linux_result(cplus_linux_syscall4(56, -100, (long)path, flags, 0666));
#else
    (void)flags;
    return CPLUS_PAL_UNSUPPORTED;
#endif
}

long long platform_file_read(long long handle, void* buffer, unsigned long long length) {
    if (!buffer && length != 0) return CPLUS_PAL_INVALID_ARGUMENT;
#if defined(__x86_64__)
    if (length > 0x7fffffffffffffffULL) return CPLUS_PAL_INVALID_ARGUMENT;
    return cplus_normalize_linux_result(cplus_linux_syscall3(0, (long)handle, (long)buffer, (long)length));
#elif defined(__aarch64__)
    return cplus_normalize_linux_result(cplus_linux_syscall3(63, handle, (long)buffer, (long)length));
#else
    (void)handle; (void)buffer; (void)length;
    return CPLUS_PAL_UNSUPPORTED;
#endif
}

long long platform_file_write(long long handle, const void* buffer, unsigned long long length) {
    if (!buffer && length != 0) return CPLUS_PAL_INVALID_ARGUMENT;
#if defined(__x86_64__)
    if (length > 0x7fffffffffffffffULL) return CPLUS_PAL_INVALID_ARGUMENT;
    return cplus_normalize_linux_result(cplus_linux_syscall3(1, (long)handle, (long)buffer, (long)length));
#elif defined(__aarch64__)
    return cplus_normalize_linux_result(cplus_linux_syscall3(64, handle, (long)buffer, (long)length));
#else
    (void)handle; (void)buffer; (void)length;
    return CPLUS_PAL_UNSUPPORTED;
#endif
}

int platform_file_close(long long handle) {
#if defined(__x86_64__)
    return (int)cplus_normalize_linux_result(cplus_linux_syscall1(3, (long)handle));
#elif defined(__aarch64__)
    return (int)cplus_normalize_linux_result(cplus_linux_syscall1(57, (long)handle));
#else
    (void)handle;
    return (int)CPLUS_PAL_UNSUPPORTED;
#endif
}

long long platform_file_seek(long long handle, long long offset, unsigned int origin) {
    if (handle < 0 || origin > CPLUS_SEEK_END) return CPLUS_PAL_INVALID_ARGUMENT;
#if defined(__x86_64__)
    return cplus_normalize_linux_result(cplus_linux_syscall3(8, (long)handle, (long)offset, (long)origin));
#elif defined(__aarch64__)
    return cplus_normalize_linux_result(cplus_linux_syscall3(62, (long)handle, (long)offset, (long)origin));
#else
    (void)offset;
    (void)origin;
    return CPLUS_PAL_UNSUPPORTED;
#endif
}

int platform_file_metadata(const char* path, cplus_file_metadata_t* metadata) {
    struct cplus_linux_statx status;
    long result;
    if (!path || !metadata) return (int)CPLUS_PAL_INVALID_ARGUMENT;
#if defined(__x86_64__)
    result = cplus_linux_syscall6(332, -100, (long)path, 0, 0x7ff, (long)&status, 0);
#elif defined(__aarch64__)
    result = cplus_linux_syscall6(291, -100, (long)path, 0, 0x7ff, (long)&status, 0);
#else
    (void)status;
    return (int)CPLUS_PAL_UNSUPPORTED;
#endif
    result = cplus_normalize_linux_result(result);
    if (result < 0) return (int)result;

    metadata->size_bytes = status.size;
    metadata->modified_seconds_utc = status.modification_time.seconds;
    metadata->modified_nanoseconds = status.modification_time.nanoseconds;
    if ((status.mode & 0170000) == 0100000) metadata->kind = CPLUS_FILE_KIND_REGULAR;
    else if ((status.mode & 0170000) == 0040000) metadata->kind = CPLUS_FILE_KIND_DIRECTORY;
    else metadata->kind = CPLUS_FILE_KIND_OTHER;
    metadata->reserved0 = 0;
    metadata->reserved1 = 0;
    return 0;
}

int platform_directory_create(const char* path) {
    if (!path) return (int)CPLUS_PAL_INVALID_ARGUMENT;
#if defined(__x86_64__)
    return (int)cplus_normalize_linux_result(cplus_linux_syscall3(258, -100, (long)path, 0777));
#elif defined(__aarch64__)
    return (int)cplus_normalize_linux_result(cplus_linux_syscall3(34, -100, (long)path, 0777));
#else
    return (int)CPLUS_PAL_UNSUPPORTED;
#endif
}

int platform_file_remove(const char* path) {
    if (!path) return (int)CPLUS_PAL_INVALID_ARGUMENT;
#if defined(__x86_64__)
    return (int)cplus_normalize_linux_result(cplus_linux_syscall3(263, -100, (long)path, 0));
#elif defined(__aarch64__)
    return (int)cplus_normalize_linux_result(cplus_linux_syscall3(35, -100, (long)path, 0));
#else
    return (int)CPLUS_PAL_UNSUPPORTED;
#endif
}

int platform_directory_remove(const char* path) {
    if (!path) return (int)CPLUS_PAL_INVALID_ARGUMENT;
#if defined(__x86_64__)
    return (int)cplus_normalize_linux_result(cplus_linux_syscall3(263, -100, (long)path, 0x200));
#elif defined(__aarch64__)
    return (int)cplus_normalize_linux_result(cplus_linux_syscall3(35, -100, (long)path, 0x200));
#else
    return (int)CPLUS_PAL_UNSUPPORTED;
#endif
}

long long platform_directory_open(const char* path) {
    if (!path) return CPLUS_PAL_INVALID_ARGUMENT;
#if defined(__x86_64__)
    return cplus_normalize_linux_result(cplus_linux_syscall4(257, -100, (long)path, 0x90000, 0));
#elif defined(__aarch64__)
    return cplus_normalize_linux_result(cplus_linux_syscall4(56, -100, (long)path, 0x90000, 0));
#else
    return CPLUS_PAL_UNSUPPORTED;
#endif
}

long long platform_directory_read(long long handle, char* utf8_name, unsigned long long capacity) {
    union {
        long long alignment;
        unsigned char bytes[4096];
    } buffer;
    if (handle < 0 || !utf8_name) return CPLUS_PAL_INVALID_ARGUMENT;
#if defined(__x86_64__) || defined(__aarch64__)
    for (;;) {
        long current_offset;
        long byte_count;
        long next_offset;
        struct cplus_linux_dirent64* entry;
        unsigned long long available_name_bytes;
        unsigned long long name_length = 0;
        long result;

#if defined(__x86_64__)
        current_offset = cplus_linux_syscall3(8, (long)handle, 0, 1);
#else
        current_offset = cplus_linux_syscall3(62, (long)handle, 0, 1);
#endif
        if (current_offset < 0) return cplus_normalize_linux_result(current_offset);

#if defined(__x86_64__)
        byte_count = cplus_linux_syscall3(217, (long)handle, (long)buffer.bytes, sizeof(buffer.bytes));
#else
        byte_count = cplus_linux_syscall3(61, (long)handle, (long)buffer.bytes, sizeof(buffer.bytes));
#endif
        if (byte_count < 0) return cplus_normalize_linux_result(byte_count);
        if (byte_count == 0) return 0;
        if ((unsigned long long)byte_count < 19ULL) return (long long)CPLUS_PAL_IO_ERROR;

        entry = (struct cplus_linux_dirent64*)buffer.bytes;
        if (entry->record_length < 19 || entry->record_length > (unsigned short)byte_count) {
            return (long long)CPLUS_PAL_IO_ERROR;
        }
        available_name_bytes = entry->record_length - 19ULL;
        while (name_length < available_name_bytes && entry->name[name_length] != '\0') name_length++;
        if (name_length == available_name_bytes || entry->offset < 0) return (long long)CPLUS_PAL_IO_ERROR;
        next_offset = (long)entry->offset;
        if (next_offset == current_offset) return (long long)CPLUS_PAL_IO_ERROR;

        if ((name_length == 1 && entry->name[0] == '.') ||
            (name_length == 2 && entry->name[0] == '.' && entry->name[1] == '.')) {
#if defined(__x86_64__)
            result = cplus_linux_syscall3(8, (long)handle, next_offset, 0);
#else
            result = cplus_linux_syscall3(62, (long)handle, next_offset, 0);
#endif
            if (result < 0) return cplus_normalize_linux_result(result);
            continue;
        }

        if (!cplus_linux_utf8_is_valid((const unsigned char*)entry->name, name_length)) {
#if defined(__x86_64__)
            result = cplus_linux_syscall3(8, (long)handle, next_offset, 0);
#else
            result = cplus_linux_syscall3(62, (long)handle, next_offset, 0);
#endif
            if (result < 0) return cplus_normalize_linux_result(result);
            return (long long)CPLUS_PAL_UNSUPPORTED;
        }

        if (capacity <= name_length) {
#if defined(__x86_64__)
            result = cplus_linux_syscall3(8, (long)handle, current_offset, 0);
#else
            result = cplus_linux_syscall3(62, (long)handle, current_offset, 0);
#endif
            if (result < 0) return cplus_normalize_linux_result(result);
            return (long long)CPLUS_PAL_BUFFER_TOO_SMALL;
        }

#if defined(__x86_64__)
        result = cplus_linux_syscall3(8, (long)handle, next_offset, 0);
#else
        result = cplus_linux_syscall3(62, (long)handle, next_offset, 0);
#endif
        if (result < 0) return cplus_normalize_linux_result(result);
        {
            unsigned long long index;
            for (index = 0; index < name_length; index++) utf8_name[index] = entry->name[index];
            utf8_name[name_length] = '\0';
        }
        return (long long)name_length;
    }
#else
    (void)capacity;
    (void)buffer;
    return (long long)CPLUS_PAL_UNSUPPORTED;
#endif
}

int platform_directory_close(long long handle) {
    return platform_file_close(handle);
}

int platform_file_rename(const char* source, const char* target) {
    if (!source || !target) return (int)CPLUS_PAL_INVALID_ARGUMENT;
#if defined(__x86_64__)
    return (int)cplus_normalize_linux_result(cplus_linux_syscall4(264, -100, (long)source, -100, (long)target));
#elif defined(__aarch64__)
    return (int)cplus_normalize_linux_result(cplus_linux_syscall4(276, -100, (long)source, -100, (long)target));
#else
    return (int)CPLUS_PAL_UNSUPPORTED;
#endif
}

int platform_process_exit(int status) {
#if defined(__x86_64__)
    register long code __asm__("rdi") = status;
    register long number __asm__("rax") = 60;
    __asm__ volatile("syscall" : "+a"(number) : "D"(code) : "rcx", "r11", "memory");
#elif defined(__aarch64__)
    register long code __asm__("x0") = status;
    register long number __asm__("x8") = 93;
    __asm__ volatile("svc 0" : "+r"(number) : "r"(code) : "memory");
#else
    (void)status;
#endif
    for (;;) { }
    return status;
}

static long long cplus_linux_timespec_nanoseconds(long long seconds, long long nanoseconds) {
    if (seconds < 0) return CPLUS_PAL_IO_ERROR;
    if (nanoseconds < 0 || nanoseconds >= 1000000000LL) return CPLUS_PAL_IO_ERROR;
    if (seconds > (0x7fffffffffffffffLL - nanoseconds) / 1000000000LL) return CPLUS_PAL_IO_ERROR;
    return seconds * 1000000000LL + nanoseconds;
}

static long long cplus_linux_clock_nanoseconds(long long clock_id) {
    struct cplus_timespec { long long seconds; long long nanoseconds; } time;
    long result;
#if defined(__x86_64__)
    result = cplus_linux_syscall2(228, clock_id, (long)&time);
#elif defined(__aarch64__)
    result = cplus_linux_syscall2(113, clock_id, (long)&time);
#else
    (void)clock_id;
    return CPLUS_PAL_UNSUPPORTED;
#endif
    if (result < 0) return cplus_normalize_linux_result(result);
    if (clock_id == 0 && time.seconds < 0) return CPLUS_PAL_UNSUPPORTED;
    return cplus_linux_timespec_nanoseconds(time.seconds, time.nanoseconds);
}

long long platform_clock_wall_nanoseconds(void) {
    return cplus_linux_clock_nanoseconds(0);
}

long long platform_clock_monotonic_nanoseconds(void) {
    return cplus_linux_clock_nanoseconds(1);
}

long long platform_clock_process_cpu_nanoseconds(void) {
    return cplus_linux_clock_nanoseconds(2);
}

long long platform_clock_ticks(void) {
    return platform_clock_monotonic_nanoseconds();
}

typedef struct cplus_linux_sockaddr_ipv4 {
    unsigned short family;
    unsigned short port;
    unsigned char address[4];
    unsigned char zero[8];
} cplus_linux_sockaddr_ipv4;

typedef struct cplus_linux_sockaddr_ipv6 {
    unsigned short family;
    unsigned short port;
    unsigned int flowinfo;
    unsigned char address[16];
    unsigned int scope_id;
} cplus_linux_sockaddr_ipv6;

typedef union cplus_linux_sockaddr_storage {
    unsigned long long alignment;
    unsigned char bytes[128];
    cplus_linux_sockaddr_ipv4 ipv4;
    cplus_linux_sockaddr_ipv6 ipv6;
} cplus_linux_sockaddr_storage;

_Static_assert(sizeof(cplus_linux_sockaddr_ipv4) == 16, "Linux IPv4 socket address ABI");
_Static_assert(sizeof(cplus_linux_sockaddr_ipv6) == 28, "Linux IPv6 socket address ABI");

static long cplus_linux_normalize_socket_result(long result) {
    long error;
    if (result >= 0) return result;
    error = -result;
    if (error == 1 || error == 13) return CPLUS_PAL_ACCESS_DENIED;
    if (error == 9 || error == 14 || error == 22 || error == 88) {
        return CPLUS_PAL_INVALID_ARGUMENT;
    }
    if (error == 38 || error == 91 || error == 92 || error == 93 ||
        error == 94 || error == 95 || error == 96 || error == 97) return CPLUS_PAL_UNSUPPORTED;
    return CPLUS_PAL_NETWORK_ERROR;
}

static int cplus_linux_socket_handle_is_valid(cplus_socket_handle_t socket) {
    return socket >= 0 && socket <= 0x7fffffffLL;
}

static unsigned short cplus_linux_network_port(unsigned short host_port) {
    return (unsigned short)((host_port << 8) | (host_port >> 8));
}

static int cplus_linux_encode_socket_address(
    const cplus_socket_address_t* source,
    cplus_linux_sockaddr_storage* destination,
    unsigned int* length) {
    unsigned int index;
    if (!source || !destination || !length || source->reserved != 0) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    if (source->family == CPLUS_SOCKET_IPV4) {
        if (source->scope_id != 0) return (int)CPLUS_PAL_INVALID_ARGUMENT;
        for (index = 4; index < 16; index++) {
            if (source->address[index] != 0) return (int)CPLUS_PAL_INVALID_ARGUMENT;
        }
        destination->ipv4.family = 2;
        destination->ipv4.port = cplus_linux_network_port(source->port);
        for (index = 0; index < 4; index++) destination->ipv4.address[index] = source->address[index];
        for (index = 0; index < 8; index++) destination->ipv4.zero[index] = 0;
        *length = 16;
        return 0;
    }
    if (source->family == CPLUS_SOCKET_IPV6) {
        destination->ipv6.family = 10;
        destination->ipv6.port = cplus_linux_network_port(source->port);
        destination->ipv6.flowinfo = 0;
        for (index = 0; index < 16; index++) destination->ipv6.address[index] = source->address[index];
        destination->ipv6.scope_id = source->scope_id;
        *length = 28;
        return 0;
    }
    return (int)CPLUS_PAL_UNSUPPORTED;
}

static int cplus_linux_decode_socket_address(
    const cplus_linux_sockaddr_storage* source,
    unsigned int length,
    cplus_socket_address_t* destination) {
    unsigned int index;
    if (!source || !destination) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    destination->reserved = 0;
    for (index = 0; index < 16; index++) destination->address[index] = 0;
    destination->scope_id = 0;
    if (source->ipv4.family == 2 && length >= 16) {
        destination->family = CPLUS_SOCKET_IPV4;
        destination->port = cplus_linux_network_port(source->ipv4.port);
        for (index = 0; index < 4; index++) destination->address[index] = source->ipv4.address[index];
        return 0;
    }
    if (source->ipv6.family == 10 && length >= 28) {
        destination->family = CPLUS_SOCKET_IPV6;
        destination->port = cplus_linux_network_port(source->ipv6.port);
        for (index = 0; index < 16; index++) destination->address[index] = source->ipv6.address[index];
        destination->scope_id = source->ipv6.scope_id;
        return 0;
    }
    return (int)CPLUS_PAL_UNSUPPORTED;
}

static long long cplus_linux_socket_result(long result) {
    return result < 0 ? cplus_linux_normalize_socket_result(result) : (long long)result;
}

long long platform_socket_open(unsigned int family, unsigned int kind) {
    long domain;
    long type;
    long result;
    if (family == CPLUS_SOCKET_IPV4) domain = 2;
    else if (family == CPLUS_SOCKET_IPV6) domain = 10;
    else return CPLUS_PAL_UNSUPPORTED;
    if (kind == CPLUS_SOCKET_STREAM) type = 1;
    else if (kind == CPLUS_SOCKET_DATAGRAM) type = 2;
    else return CPLUS_PAL_UNSUPPORTED;
#if defined(__x86_64__)
    result = cplus_linux_syscall3(41, domain, type | 0x80000, 0);
#elif defined(__aarch64__)
    result = cplus_linux_syscall3(198, domain, type | 0x80000, 0);
#else
    return CPLUS_PAL_UNSUPPORTED;
#endif
    return cplus_linux_socket_result(result);
}

int platform_socket_bind(cplus_socket_handle_t socket, const cplus_socket_address_t* address) {
    cplus_linux_sockaddr_storage native_address;
    unsigned int length;
    int encode_result;
    long result;
    if (!cplus_linux_socket_handle_is_valid(socket) || !address) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    encode_result = cplus_linux_encode_socket_address(address, &native_address, &length);
    if (encode_result < 0) return encode_result;
#if defined(__x86_64__)
    result = cplus_linux_syscall3(49, (long)socket, (long)&native_address, (long)length);
#elif defined(__aarch64__)
    result = cplus_linux_syscall3(200, (long)socket, (long)&native_address, (long)length);
#else
    return (int)CPLUS_PAL_UNSUPPORTED;
#endif
    return (int)cplus_linux_normalize_socket_result(result);
}

int platform_socket_listen(cplus_socket_handle_t socket, int backlog) {
    long result;
    if (!cplus_linux_socket_handle_is_valid(socket) || backlog < 0) return (int)CPLUS_PAL_INVALID_ARGUMENT;
#if defined(__x86_64__)
    result = cplus_linux_syscall2(50, (long)socket, backlog);
#elif defined(__aarch64__)
    result = cplus_linux_syscall2(201, (long)socket, backlog);
#else
    return (int)CPLUS_PAL_UNSUPPORTED;
#endif
    return (int)cplus_linux_normalize_socket_result(result);
}

long long platform_socket_accept(cplus_socket_handle_t socket, cplus_socket_address_t* peer) {
    cplus_linux_sockaddr_storage native_address;
    unsigned int length = sizeof(native_address);
    long result;
    if (!cplus_linux_socket_handle_is_valid(socket)) return CPLUS_PAL_INVALID_ARGUMENT;
#if defined(__x86_64__)
    result = cplus_linux_syscall4(288, (long)socket, peer ? (long)&native_address : 0,
        peer ? (long)&length : 0, 0x80000);
#elif defined(__aarch64__)
    result = cplus_linux_syscall4(242, (long)socket, peer ? (long)&native_address : 0,
        peer ? (long)&length : 0, 0x80000);
#else
    return CPLUS_PAL_UNSUPPORTED;
#endif
    if (result < 0) return cplus_linux_socket_result(result);
    if (peer) {
        int decode_result = cplus_linux_decode_socket_address(&native_address, length, peer);
        if (decode_result < 0) {
#if defined(__x86_64__)
            cplus_linux_syscall1(3, result);
#elif defined(__aarch64__)
            cplus_linux_syscall1(57, result);
#endif
            return decode_result;
        }
    }
    return (long long)result;
}

int platform_socket_connect(cplus_socket_handle_t socket, const cplus_socket_address_t* address) {
    cplus_linux_sockaddr_storage native_address;
    unsigned int length;
    int encode_result;
    long result;
    if (!cplus_linux_socket_handle_is_valid(socket) || !address) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    encode_result = cplus_linux_encode_socket_address(address, &native_address, &length);
    if (encode_result < 0) return encode_result;
#if defined(__x86_64__)
    result = cplus_linux_syscall3(42, (long)socket, (long)&native_address, (long)length);
#elif defined(__aarch64__)
    result = cplus_linux_syscall3(203, (long)socket, (long)&native_address, (long)length);
#else
    return (int)CPLUS_PAL_UNSUPPORTED;
#endif
    return (int)cplus_linux_normalize_socket_result(result);
}

int platform_socket_get_address(cplus_socket_handle_t socket, int peer, cplus_socket_address_t* address) {
    cplus_linux_sockaddr_storage native_address;
    unsigned int length = sizeof(native_address);
    long result;
    if (!cplus_linux_socket_handle_is_valid(socket) || !address || (peer != 0 && peer != 1)) {
        return (int)CPLUS_PAL_INVALID_ARGUMENT;
    }
#if defined(__x86_64__)
    result = cplus_linux_syscall3(peer ? 52 : 51, (long)socket, (long)&native_address, (long)&length);
#elif defined(__aarch64__)
    result = cplus_linux_syscall3(peer ? 205 : 204, (long)socket, (long)&native_address, (long)&length);
#else
    return (int)CPLUS_PAL_UNSUPPORTED;
#endif
    if (result < 0) return (int)cplus_linux_normalize_socket_result(result);
    return cplus_linux_decode_socket_address(&native_address, length, address);
}

long long platform_socket_send(cplus_socket_handle_t socket, const void* buffer, unsigned long long length) {
    long result;
    if (!cplus_linux_socket_handle_is_valid(socket) || (!buffer && length != 0) || length > 0x7fffffffULL) {
        return CPLUS_PAL_INVALID_ARGUMENT;
    }
#if defined(__x86_64__)
    result = cplus_linux_syscall6(44, (long)socket, (long)buffer, (long)length, 0x4000, 0, 0);
#elif defined(__aarch64__)
    result = cplus_linux_syscall6(206, (long)socket, (long)buffer, (long)length, 0x4000, 0, 0);
#else
    return CPLUS_PAL_UNSUPPORTED;
#endif
    return cplus_linux_socket_result(result);
}

long long platform_socket_receive(cplus_socket_handle_t socket, void* buffer, unsigned long long capacity) {
    long result;
    if (!cplus_linux_socket_handle_is_valid(socket) || (!buffer && capacity != 0) || capacity > 0x7fffffffULL) {
        return CPLUS_PAL_INVALID_ARGUMENT;
    }
    if (capacity == 0) return 0;
#if defined(__x86_64__)
    result = cplus_linux_syscall6(45, (long)socket, (long)buffer, (long)capacity, 0, 0, 0);
#elif defined(__aarch64__)
    result = cplus_linux_syscall6(207, (long)socket, (long)buffer, (long)capacity, 0, 0, 0);
#else
    return CPLUS_PAL_UNSUPPORTED;
#endif
    return cplus_linux_socket_result(result);
}

long long platform_socket_send_to(
    cplus_socket_handle_t socket,
    const void* buffer,
    unsigned long long length,
    const cplus_socket_address_t* destination) {
    cplus_linux_sockaddr_storage native_address;
    unsigned int address_length;
    int encode_result;
    long result;
    if (!cplus_linux_socket_handle_is_valid(socket) || (!buffer && length != 0) ||
        length > 0x7fffffffULL || !destination) return CPLUS_PAL_INVALID_ARGUMENT;
    encode_result = cplus_linux_encode_socket_address(destination, &native_address, &address_length);
    if (encode_result < 0) return encode_result;
#if defined(__x86_64__)
    result = cplus_linux_syscall6(44, (long)socket, (long)buffer, (long)length, 0x4000,
        (long)&native_address, (long)address_length);
#elif defined(__aarch64__)
    result = cplus_linux_syscall6(206, (long)socket, (long)buffer, (long)length, 0x4000,
        (long)&native_address, (long)address_length);
#else
    return CPLUS_PAL_UNSUPPORTED;
#endif
    return cplus_linux_socket_result(result);
}

long long platform_socket_receive_from(
    cplus_socket_handle_t socket,
    void* buffer,
    unsigned long long capacity,
    cplus_socket_address_t* source) {
    cplus_linux_sockaddr_storage native_address;
    unsigned int address_length = sizeof(native_address);
    long result;
    if (!cplus_linux_socket_handle_is_valid(socket) || (!buffer && capacity != 0) || capacity > 0x7fffffffULL) {
        return CPLUS_PAL_INVALID_ARGUMENT;
    }
#if defined(__x86_64__)
    result = cplus_linux_syscall6(45, (long)socket, (long)buffer, (long)capacity, 0,
        source ? (long)&native_address : 0, source ? (long)&address_length : 0);
#elif defined(__aarch64__)
    result = cplus_linux_syscall6(207, (long)socket, (long)buffer, (long)capacity, 0,
        source ? (long)&native_address : 0, source ? (long)&address_length : 0);
#else
    return CPLUS_PAL_UNSUPPORTED;
#endif
    if (result < 0) return cplus_linux_socket_result(result);
    if (source) {
        int decode_result = cplus_linux_decode_socket_address(&native_address, address_length, source);
        if (decode_result < 0) return decode_result;
    }
    return (long long)result;
}

int platform_socket_shutdown(cplus_socket_handle_t socket, unsigned int direction) {
    long result;
    if (!cplus_linux_socket_handle_is_valid(socket) || direction > CPLUS_SOCKET_SHUTDOWN_BOTH) {
        return (int)CPLUS_PAL_INVALID_ARGUMENT;
    }
#if defined(__x86_64__)
    result = cplus_linux_syscall2(48, (long)socket, (long)direction);
#elif defined(__aarch64__)
    result = cplus_linux_syscall2(210, (long)socket, (long)direction);
#else
    return (int)CPLUS_PAL_UNSUPPORTED;
#endif
    return (int)cplus_linux_normalize_socket_result(result);
}

int platform_socket_close(cplus_socket_handle_t socket) {
    long result;
    if (!cplus_linux_socket_handle_is_valid(socket)) return (int)CPLUS_PAL_INVALID_ARGUMENT;
#if defined(__x86_64__)
    result = cplus_linux_syscall1(3, (long)socket);
#elif defined(__aarch64__)
    result = cplus_linux_syscall1(57, (long)socket);
#else
    return (int)CPLUS_PAL_UNSUPPORTED;
#endif
    return (int)cplus_linux_normalize_socket_result(result);
}
