/// C allocation façade. errno conversion is kept at the libc boundary.
extern void* __cplus_alloc(long long size);
extern void* __cplus_calloc(long long count, long long size);
extern void* __cplus_realloc(void* value, long long size);
extern void* __cplus_alloc_aligned(long long alignment, long long size);
extern void __cplus_free(void* value);

void* malloc(long long size) { return __cplus_alloc(size); }
void* calloc(long long count, long long size) { return __cplus_calloc(count, size); }
void* realloc(void* value, long long size) { return __cplus_realloc(value, size); }
void* aligned_alloc(long long alignment, long long size) { return __cplus_alloc_aligned(alignment, size); }
void free(void* value) { __cplus_free(value); }
