/// Native allocation delegates to the compiler/runtime allocator. The C+
/// surface remains independent of host malloc and obtains its pages through
/// the selected PAL.
extern void* __cplus_alloc(long long size);
extern void* __cplus_calloc(long long count, long long size);
extern void* __cplus_realloc(void* value, long long size);
extern void* __cplus_alloc_aligned(long long alignment, long long size);
extern void __cplus_free(void* value);

pub void* std_alloc(long long size) { return __cplus_alloc(size); }
pub void* std_calloc(long long count, long long size) { return __cplus_calloc(count, size); }
pub void* std_realloc(void* value, long long size) { return __cplus_realloc(value, size); }
pub void* std_aligned_alloc(long long alignment, long long size) { return __cplus_alloc_aligned(alignment, size); }
pub void std_free(void* value) { __cplus_free(value); }
