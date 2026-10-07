/// Target-neutral scalar aliases and value/error carriers used by std.*.
/// These types contain no handles, errno values, or operating-system fields.
typedef unsigned char std_byte_t;
typedef unsigned long long std_size_t;
typedef long long std_index_t;

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

pub int cplus_std_core_version() {
    return 2;
}
