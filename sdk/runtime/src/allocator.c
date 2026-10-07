#include "cplus_platform.h"

/* Page-backed runtime allocator. It deliberately depends only on the PAL and
   the compiler-owned byte primitives, never on host malloc/free. */
#define CPLUS_ALLOC_MAGIC 0x43504c5553414c4cULL

typedef struct {
    unsigned long long magic;
    unsigned long long pages;
    unsigned long long size;
    unsigned long long alignment;
    void* base;
} cplus_allocation_header;

extern void* __cplus_memcpy(void* destination, const void* source, unsigned long size);

static int cplus_power_of_two(unsigned long long value) {
    return value != 0 && (value & (value - 1)) == 0;
}

static unsigned long long cplus_min(unsigned long long left, unsigned long long right) {
    return left < right ? left : right;
}

static unsigned long long cplus_align_up(unsigned long long value, unsigned long long alignment) {
    return (value + alignment - 1) & ~(alignment - 1);
}

void* __cplus_alloc_aligned(unsigned long long alignment, unsigned long long size) {
    unsigned long long overhead;
    unsigned long long total;
    unsigned long long pages;
    void* base;
    unsigned long long address;
    cplus_allocation_header* header;
    if (!cplus_power_of_two(alignment) || alignment < sizeof(void*)) return (void*)0;
    if (size == 0) size = 1;
    overhead = sizeof(cplus_allocation_header) + alignment - 1;
    if (size > 0xffffffffffffffffULL - overhead) return (void*)0;
    total = size + overhead;
    if (total > 0xffffffffffffffffULL - (CPLUS_PAL_PAGE_SIZE - 1)) return (void*)0;
    pages = (total + CPLUS_PAL_PAGE_SIZE - 1) / CPLUS_PAL_PAGE_SIZE;
    base = platform_page_allocate(pages);
    if (!base) return (void*)0;
    address = cplus_align_up((unsigned long long)(unsigned long long)base + sizeof(cplus_allocation_header), alignment);
    header = (cplus_allocation_header*)(address - sizeof(cplus_allocation_header));
    header->magic = CPLUS_ALLOC_MAGIC;
    header->pages = pages;
    header->size = size;
    header->alignment = alignment;
    header->base = base;
    return (void*)address;
}

void* __cplus_alloc(unsigned long long size) {
    return __cplus_alloc_aligned(sizeof(void*) * 2, size);
}

void* __cplus_calloc(unsigned long long count, unsigned long long size) {
    unsigned long long total;
    unsigned char* result;
    unsigned long long index;
    if (count != 0 && size > 0xffffffffffffffffULL / count) return (void*)0;
    total = count * size;
    result = (unsigned char*)__cplus_alloc(total);
    if (!result) return (void*)0;
    for (index = 0; index < total; index++) result[index] = 0;
    return result;
}

void __cplus_free(void* value) {
    cplus_allocation_header* header;
    if (!value) return;
    header = (cplus_allocation_header*)((unsigned long long)value - sizeof(cplus_allocation_header));
    if (header->magic != CPLUS_ALLOC_MAGIC) return;
    header->magic = 0;
    platform_page_release(header->base, header->pages);
}

void* __cplus_realloc(void* value, unsigned long long size) {
    cplus_allocation_header* header;
    void* result;
    unsigned long long copy_size;
    if (!value) return __cplus_alloc(size);
    if (size == 0) {
        __cplus_free(value);
        return (void*)0;
    }
    header = (cplus_allocation_header*)((unsigned long long)value - sizeof(cplus_allocation_header));
    if (header->magic != CPLUS_ALLOC_MAGIC) return (void*)0;
    result = __cplus_alloc_aligned(header->alignment, size);
    if (!result) return (void*)0;
    copy_size = cplus_min(header->size, size);
    __cplus_memcpy(result, value, (unsigned long)copy_size);
    __cplus_free(value);
    return result;
}

