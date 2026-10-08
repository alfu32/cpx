/// Target-neutral value/error carriers used by std.*.
/// These types contain no handles, errno values, or operating-system fields.
import { size_t, ptrdiff_t } from c.stddef;

pub typedef size_t usize;
pub typedef ptrdiff_t isize;

struct std_error_t {
    int code;
};

struct std_result_t {
    int success;
    long long value;
    std_error_t error;
};

struct std_option_t {
    int present;
    long long value;
};

pub std_error_t std_error_ok() {
    std_error_t result;
    result.code = 0;
    return result;
}

pub std_error_t std_error_from_code(int code) {
    std_error_t result;
    result.code = code;
    return result;
}

pub int std_error_is_ok(std_error_t error) {
    return error.code == 0;
}

pub std_result_t std_result_ok(long long value) {
    std_result_t result;
    result.success = 1;
    result.value = value;
    result.error = std_error_ok();
    return result;
}

pub std_result_t std_result_error(int code) {
    std_result_t result;
    result.success = 0;
    result.value = 0;
    result.error = std_error_from_code(code);
    return result;
}

pub int std_result_is_ok(std_result_t result) {
    return result.success;
}

pub std_option_t std_option_none() {
    std_option_t result;
    result.present = 0;
    result.value = 0;
    return result;
}

pub std_option_t std_option_some(long long value) {
    std_option_t result;
    result.present = 1;
    result.value = value;
    return result;
}

pub int std_option_is_some(std_option_t option) {
    return option.present;
}

pub usize std_usize_max() {
    return (usize)-1;
}

pub isize std_isize_max() {
    return (isize)(std_usize_max() >> 1);
}

pub isize std_isize_min() {
    return -std_isize_max() - 1;
}

pub usize std_size_width_bits() {
    return sizeof(usize) * 8;
}

pub usize std_pointer_width_bits() {
    return sizeof(void*) * 8;
}

pub int std_usize_compare(usize left, usize right) {
    if (left < right) return -1;
    if (left > right) return 1;
    return 0;
}

pub int std_isize_compare(isize left, isize right) {
    if (left < right) return -1;
    if (left > right) return 1;
    return 0;
}

pub int std_byte_compare(unsigned char left, unsigned char right) {
    if (left < right) return -1;
    if (left > right) return 1;
    return 0;
}

pub int std_pointer_is_null(void* pointer) {
    return pointer == (void*)0;
}

pub int std_pointer_equal(void* left, void* right) {
    return left == right;
}

pub void* std_pointer_offset(void* pointer, usize byte_offset) {
    return ((unsigned char*)pointer) + byte_offset;
}

pub isize std_pointer_distance(void* left, void* right) {
    return (isize)(((unsigned char*)right) - ((unsigned char*)left));
}

pub int cplus_std_core_version() {
    return 3;
}
