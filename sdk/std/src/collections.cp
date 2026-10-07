/// Small target-neutral collection value types. Ownership remains explicit:
/// these are views and ranges, not hidden heap allocations.
struct std_slice_t {
    void* data;
    unsigned long long length;
};

struct std_range_t {
    long long start;
    long long end;
};

pub std_slice_t std_slice_empty() {
    std_slice_t result;
    result.data = (void*)0;
    result.length = 0;
    return result;
}

pub std_slice_t std_slice_of(void* data, unsigned long long length) {
    std_slice_t result;
    result.data = data;
    result.length = length;
    return result;
}

pub int std_slice_is_empty(std_slice_t slice) {
    return slice.length == 0;
}

pub std_range_t std_range(long long start, long long end) {
    std_range_t result;
    result.start = start;
    result.end = end;
    return result;
}

pub long long std_range_length(std_range_t range) {
    return range.end > range.start ? range.end - range.start : 0;
}

pub int std_range_contains(std_range_t range, long long value) {
    return value >= range.start && value < range.end;
}
