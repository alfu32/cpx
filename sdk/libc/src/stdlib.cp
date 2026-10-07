import { std_alloc, std_calloc, std_free } from ../../std/src/alloc.cp;

/// C allocation façade. errno conversion is kept at the libc boundary.
void* malloc(long size) {
    return std_alloc(size);
}

void* calloc(long count, long size) {
    return std_calloc(count, size);
}

void free(void* value) {
    std_free(value);
}
