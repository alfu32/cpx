import { std_mem_set } from ./mem.cp;

/// Bootstrap allocator for profiles that provide a page allocator later.
/// The fixed arena is deliberately deterministic and is replaced by the PAL
/// implementation in hosted/production SDK packages.
static char std_bootstrap_heap[65536];
static long std_bootstrap_offset;

pub void* std_alloc(long size) {
    long start = std_bootstrap_offset;
    if (size == 0) size = 1;
    if (start + size > 65536) return (void*) 0;
    std_bootstrap_offset = start + size;
    return (void*) &std_bootstrap_heap[start];
}

pub void* std_calloc(long count, long size) {
    void* result = std_alloc(count * size);
    if (result != (void*) 0) std_mem_set(result, 0, count * size);
    return result;
}

pub void std_free(void* value) {
    /* The bootstrap arena is reclaimed with the process. */
    return;
}
