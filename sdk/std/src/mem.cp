/// Portable byte and memory operations used by std and libc facades.
import { usize, std_usize_max } from std.core;

pub struct std_memory_span_t {
    unsigned char* data;
    usize length;
};

pub struct std_raw_memory_t {
    void* data;
    usize size;
};

pub int std_mem_is_aligned(usize address, usize alignment) {
    return alignment != 0 && address % alignment == 0;
}

pub usize std_mem_align_up(usize value, usize alignment) {
    if (alignment == 0 || (alignment & (alignment - 1)) != 0) return std_usize_max();
    usize padding = alignment - 1;
    if (value > std_usize_max() - padding) return std_usize_max();
    return (value + padding) & ~padding;
}

pub std_memory_span_t std_memory_span_empty() {
    std_memory_span_t result;
    result.data = (unsigned char*)0;
    result.length = 0;
    return result;
}

pub std_memory_span_t std_memory_span(void* data, usize length) {
    std_memory_span_t result;
    result.data = (unsigned char*)data;
    result.length = length;
    return result;
}

pub int std_memory_span_is_empty(std_memory_span_t span) {
    return span.length == 0;
}

pub void* std_memory_span_at(std_memory_span_t span, usize index) {
    if (index >= span.length) return (void*)0;
    return span.data + index;
}

pub std_raw_memory_t std_raw_memory_view(void* data, usize size) {
    std_raw_memory_t result;
    result.data = data;
    result.size = size;
    return result;
}

pub int std_raw_memory_is_empty(std_raw_memory_t view) {
    return view.size == 0;
}

pub std_memory_span_t std_raw_memory_as_bytes(std_raw_memory_t view) {
    return std_memory_span(view.data, view.size);
}

pub void* std_mem_copy(void* destination, void* source, usize size) {
    usize index = 0;
    unsigned char* target = (unsigned char*) destination;
    unsigned char* origin = (unsigned char*) source;
    while (index < size) {
        target[index] = origin[index];
        index = index + 1;
    }
    return destination;
}

pub void* std_mem_move(void* destination, void* source, usize size) {
    usize index = 0;
    unsigned char* target = (unsigned char*) destination;
    unsigned char* origin = (unsigned char*) source;
    if (target < origin) {
        while (index < size) {
            target[index] = origin[index];
            index = index + 1;
        }
    } else {
        index = size;
        while (index > 0) {
            index = index - 1;
            target[index] = origin[index];
        }
    }
    return destination;
}

pub void* std_mem_set(void* destination, int value, usize size) {
    usize index = 0;
    unsigned char* target = (unsigned char*) destination;
    while (index < size) {
        target[index] = (unsigned char) value;
        index = index + 1;
    }
    return destination;
}

pub void* std_mem_zero(void* destination, usize size) {
    return std_mem_set(destination, 0, size);
}

pub int std_mem_compare(void* left, void* right, usize size) {
    usize index = 0;
    unsigned char* a = (unsigned char*) left;
    unsigned char* b = (unsigned char*) right;
    while (index < size) {
        if (a[index] < b[index]) return -1;
        if (a[index] > b[index]) return 1;
        index = index + 1;
    }
    return 0;
}

pub int std_mem_equal(void* left, void* right, usize size) {
    return std_mem_compare(left, right, size) == 0;
}
