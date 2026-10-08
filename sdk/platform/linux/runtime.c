#include "cplus_platform.h"

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

long platform_write_stdout(const char* buffer, unsigned long length) {
#if defined(__x86_64__)
    register long first __asm__("rdi") = 1;
    register const char* second __asm__("rsi") = buffer;
    register unsigned long third __asm__("rdx") = length;
    register long number __asm__("rax") = 1;
    __asm__ volatile("syscall" : "+a"(number) : "D"(first), "S"(second), "d"(third) : "rcx", "r11", "memory");
    return number;
#elif defined(__aarch64__)
    return cplus_linux_syscall3(64, 1, (long)buffer, (long)length);
#else
    (void)buffer; (void)length;
    return -38;
#endif
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

long long platform_clock_ticks(void) {
    struct cplus_timespec { long long seconds; long long nanoseconds; } time;
#if defined(__x86_64__)
    register long result __asm__("rax") = 228;
    register long clock __asm__("rdi") = 1;
    register void* value __asm__("rsi") = &time;
    __asm__ volatile("syscall" : "+a"(result) : "D"(clock), "S"(value) : "rcx", "r11", "memory");
#elif defined(__aarch64__)
    register long result __asm__("x0") = 1;
    register long clock __asm__("x8") = 113;
    register void* value __asm__("x1") = &time;
    __asm__ volatile("svc 0" : "+r"(result) : "r"(clock), "r"(value) : "memory");
#else
    return -1;
#endif
    if (result < 0) return -1;
    return time.seconds * 1000000000LL + time.nanoseconds;
}
