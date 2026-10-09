#include "cplus_platform.h"
#include "cplus_runtime.h"
#include "cplus_test_runtime.h"
#include <float.h>
#include <math.h>

#ifndef FLT_MANT_DIG
#define FLT_MANT_DIG 24
#endif
#ifndef LDBL_MANT_DIG
#define LDBL_MANT_DIG __LDBL_MANT_DIG__
#endif

enum {
    CPLUS_TEST_KIND_UNKNOWN = 0,
    CPLUS_TEST_KIND_SIGNED_INTEGER = 1,
    CPLUS_TEST_KIND_UNSIGNED_INTEGER = 2,
    CPLUS_TEST_KIND_PLAIN_INTEGER = 3,
    CPLUS_TEST_KIND_BOOLEAN = 4,
    CPLUS_TEST_KIND_FLOAT = 5,
    CPLUS_TEST_KIND_DOUBLE = 6,
    CPLUS_TEST_KIND_LONG_DOUBLE = 7,
    CPLUS_TEST_KIND_COMPLEX_FLOAT = 8,
    CPLUS_TEST_KIND_COMPLEX_DOUBLE = 9,
    CPLUS_TEST_KIND_COMPLEX_LONG_DOUBLE = 10,
    CPLUS_TEST_KIND_POINTER = 11
};

typedef struct {
    char buffer[512];
    unsigned long used;
    int failed;
} __cplus_test_output;

static long long __cplus_test_result_handle = -1;
static unsigned long long __cplus_test_sequence;
static unsigned long long __cplus_test_passed;
static unsigned long long __cplus_test_failed;
static int __cplus_test_io_failed;
static const char* __cplus_test_fixture_identity;

static void __cplus_test_flush(__cplus_test_output* output) {
    unsigned long long written = 0;
    while (!output->failed && written < output->used) {
        long long result = platform_write_stdout(output->buffer + written, output->used - written);
        if (result <= 0) {
            output->failed = 1;
            break;
        }
        written += (unsigned long long)result;
    }
    output->used = 0;
}

static void __cplus_test_char(__cplus_test_output* output, char value) {
    if (output->failed) return;
    if (output->used == sizeof(output->buffer)) __cplus_test_flush(output);
    if (!output->failed) output->buffer[output->used++] = value;
}

static void __cplus_test_text(__cplus_test_output* output, const char* value) {
    unsigned long index = 0;
    if (!value) value = "(null)";
    while (value[index] != 0) __cplus_test_char(output, value[index++]);
}

static void __cplus_test_escaped(__cplus_test_output* output, const char* value) {
    static const char hex[] = "0123456789ABCDEF";
    unsigned long index = 0;
    if (!value) value = "(null)";
    while (value[index] != 0) {
        unsigned char byte = (unsigned char)value[index++];
        if (byte == '\n') __cplus_test_text(output, "\\n");
        else if (byte == '\r') __cplus_test_text(output, "\\r");
        else if (byte == '\t') __cplus_test_text(output, "\\t");
        else if (byte < 0x20U || byte == 0x7fU) {
            __cplus_test_text(output, "\\x");
            __cplus_test_char(output, hex[(byte >> 4) & 15U]);
            __cplus_test_char(output, hex[byte & 15U]);
        } else __cplus_test_char(output, (char)byte);
    }
}

static void __cplus_test_unsigned_decimal(__cplus_test_output* output, unsigned long long value) {
    char digits[32];
    unsigned int count = 0;
    if (value == 0) digits[count++] = '0';
    while (value != 0) {
        digits[count++] = (char)('0' + value % 10ULL);
        value /= 10ULL;
    }
    while (count > 0) __cplus_test_char(output, digits[--count]);
}

static void __cplus_test_signed_decimal(__cplus_test_output* output, long long value) {
    unsigned long long magnitude;
    if (value < 0) {
        __cplus_test_char(output, '-');
        magnitude = (unsigned long long)(-(value + 1LL)) + 1ULL;
    } else magnitude = (unsigned long long)value;
    __cplus_test_unsigned_decimal(output, magnitude);
}

static int __cplus_test_integer_value(__cplus_test_output* output, const void* address,
                                      unsigned long long size, int kind) {
    unsigned char magnitude[16];
    char digits[48];
    unsigned int little_endian;
    unsigned long long index;
    unsigned int digit_count = 0;
    int negative = 0;
    int is_signed = kind == CPLUS_TEST_KIND_SIGNED_INTEGER;
    unsigned long long remaining;
    if (!address || size == 0 || size > sizeof(magnitude)) return -1;
    {
        const unsigned int marker = 1U;
        little_endian = *(const unsigned char*)&marker == 1U;
    }
    for (index = 0; index < size; index++) {
        unsigned long long source_index = little_endian ? index : size - index - 1ULL;
        magnitude[index] = ((const unsigned char*)address)[source_index];
    }
    if (kind == CPLUS_TEST_KIND_PLAIN_INTEGER) is_signed = ((char)-1) < (char)0;
    if (is_signed && (magnitude[size - 1ULL] & 0x80U) != 0) {
        negative = 1;
        for (index = 0; index < size; index++) magnitude[index] = (unsigned char)~magnitude[index];
        for (index = 0; index < size; index++) {
            magnitude[index] = (unsigned char)(magnitude[index] + 1U);
            if (magnitude[index] != 0) break;
        }
    }
    remaining = 1;
    for (index = 0; index < size; index++) if (magnitude[index] != 0) remaining = 0;
    if (remaining) {
        __cplus_test_char(output, '0');
        return 0;
    }
    while (!remaining) {
        unsigned int carry = 0;
        for (index = size; index > 0; index--) {
            unsigned int position = (unsigned int)(index - 1ULL);
            unsigned int current = carry * 256U + magnitude[position];
            magnitude[position] = (unsigned char)(current / 10U);
            carry = current % 10U;
        }
        digits[digit_count++] = (char)('0' + carry);
        remaining = 1;
        for (index = 0; index < size; index++) if (magnitude[index] != 0) remaining = 0;
    }
    if (negative) __cplus_test_char(output, '-');
    while (digit_count > 0) __cplus_test_char(output, digits[--digit_count]);
    return output->failed ? -1 : 0;
}

static void __cplus_test_exponent(__cplus_test_output* output, int exponent) {
    __cplus_test_char(output, exponent < 0 ? '-' : '+');
    __cplus_test_unsigned_decimal(output, (unsigned long long)(exponent < 0 ? -exponent : exponent));
}

static int __cplus_test_hex_float(__cplus_test_output* output, long double value, int precision) {
    long double magnitude = value < 0.0L ? -value : value;
    long double fraction;
    int exponent = 0;
    int digit_count = (precision - 1 + 3) / 4;
    int index;
    if (cplus_math_isnanl(value)) {
        if (cplus_math_signbitl(value)) __cplus_test_char(output, '-');
        __cplus_test_text(output, "nan");
        return 0;
    }
    if (cplus_math_isinfl(value)) {
        if (cplus_math_signbitl(value)) __cplus_test_char(output, '-');
        __cplus_test_text(output, "inf");
        return 0;
    }
    if (magnitude == 0.0L) {
        if (cplus_math_signbitl(value)) __cplus_test_char(output, '-');
        __cplus_test_text(output, "0x0p+0");
        return 0;
    }
    if (cplus_math_signbitl(value)) __cplus_test_char(output, '-');
    fraction = frexpl(magnitude, &exponent) * 2.0L;
    exponent--;
    __cplus_test_text(output, "0x1");
    if (digit_count > 0) __cplus_test_char(output, '.');
    fraction -= 1.0L;
    for (index = 0; index < digit_count; index++) {
        int digit;
        fraction *= 16.0L;
        digit = (int)fraction;
        if (digit < 0) digit = 0;
        if (digit > 15) digit = 15;
        __cplus_test_char(output, "0123456789abcdef"[digit]);
        fraction -= (long double)digit;
    }
    __cplus_test_char(output, 'p');
    __cplus_test_exponent(output, exponent);
    return output->failed ? -1 : 0;
}

static int __cplus_test_float_value(__cplus_test_output* output, const void* address,
                                    unsigned long long size, int kind) {
    if (!address) return -1;
    if (kind == CPLUS_TEST_KIND_FLOAT && size == sizeof(float))
        return __cplus_test_hex_float(output, (long double)*(const float*)address, FLT_MANT_DIG);
    if (kind == CPLUS_TEST_KIND_DOUBLE && size == sizeof(double))
        return __cplus_test_hex_float(output, (long double)*(const double*)address, DBL_MANT_DIG);
    if (kind == CPLUS_TEST_KIND_LONG_DOUBLE && size == sizeof(long double))
        return __cplus_test_hex_float(output, *(const long double*)address, LDBL_MANT_DIG);
    return -1;
}

static int __cplus_test_complex_value(__cplus_test_output* output, const void* address,
                                      unsigned long long size, int kind) {
    unsigned long long component_size;
    int component_kind;
    union { long double alignment; unsigned char bytes[sizeof(long double)]; } real_storage;
    union { long double alignment; unsigned char bytes[sizeof(long double)]; } imaginary_storage;
    unsigned long long index;
    if (!address) return -1;
    if (kind == CPLUS_TEST_KIND_COMPLEX_FLOAT) { component_size = sizeof(float); component_kind = CPLUS_TEST_KIND_FLOAT; }
    else if (kind == CPLUS_TEST_KIND_COMPLEX_DOUBLE) { component_size = sizeof(double); component_kind = CPLUS_TEST_KIND_DOUBLE; }
    else if (kind == CPLUS_TEST_KIND_COMPLEX_LONG_DOUBLE) { component_size = sizeof(long double); component_kind = CPLUS_TEST_KIND_LONG_DOUBLE; }
    else return -1;
    if (size != component_size * 2ULL || component_size > sizeof(real_storage.bytes)) return -1;
    for (index = 0; index < component_size; index++) {
        real_storage.bytes[index] = ((const unsigned char*)address)[index];
        imaginary_storage.bytes[index] = ((const unsigned char*)address)[component_size + index];
    }
    __cplus_test_char(output, '(');
    if (__cplus_test_float_value(output, real_storage.bytes, component_size, component_kind) != 0) return -1;
    __cplus_test_text(output, ", ");
    if (__cplus_test_float_value(output, imaginary_storage.bytes, component_size, component_kind) != 0) return -1;
    __cplus_test_char(output, ')');
    return output->failed ? -1 : 0;
}

static int __cplus_test_pointer_value(__cplus_test_output* output, const void* address,
                                      unsigned long long size, int is_null) {
    static const char hex[] = "0123456789abcdef";
    const unsigned char* bytes = (const unsigned char*)address;
    unsigned long long index;
    unsigned int little_endian;
    if (is_null) {
        __cplus_test_text(output, "null");
        return 0;
    }
    if (!address || size == 0 || size > 16ULL) return -1;
    {
        const unsigned int marker = 1U;
        little_endian = *(const unsigned char*)&marker == 1U;
    }
    __cplus_test_text(output, "0x");
    for (index = 0; index < size; index++) {
        unsigned long long byte_index = little_endian ? size - index - 1ULL : index;
        unsigned char byte = bytes[byte_index];
        __cplus_test_char(output, hex[(byte >> 4) & 15U]);
        __cplus_test_char(output, hex[byte & 15U]);
    }
    return output->failed ? -1 : 0;
}

static int __cplus_test_value(__cplus_test_output* output, const void* value,
                              unsigned long long size, int kind, int is_null_pointer) {
    if (kind >= CPLUS_TEST_KIND_SIGNED_INTEGER && kind <= CPLUS_TEST_KIND_PLAIN_INTEGER)
        return __cplus_test_integer_value(output, value, size, kind);
    if (kind == CPLUS_TEST_KIND_BOOLEAN) {
        if (!value || size != sizeof(_Bool)) return -1;
        __cplus_test_text(output, *(_Bool*)value ? "true" : "false");
        return 0;
    }
    if (kind >= CPLUS_TEST_KIND_FLOAT && kind <= CPLUS_TEST_KIND_LONG_DOUBLE)
        return __cplus_test_float_value(output, value, size, kind);
    if (kind >= CPLUS_TEST_KIND_COMPLEX_FLOAT && kind <= CPLUS_TEST_KIND_COMPLEX_LONG_DOUBLE)
        return __cplus_test_complex_value(output, value, size, kind);
    if (kind == CPLUS_TEST_KIND_POINTER)
        return __cplus_test_pointer_value(output, value, size, is_null_pointer);
    return -1;
}

typedef struct {
    char data[4096];
    unsigned long used;
    int failed;
} __cplus_test_record;

static void __cplus_test_record_char(__cplus_test_record* record, char value) {
    if (record->used >= sizeof(record->data)) { record->failed = 1; return; }
    record->data[record->used++] = value;
}

static void __cplus_test_record_text(__cplus_test_record* record, const char* value) {
    unsigned long index = 0;
    if (!value) { record->failed = 1; return; }
    while (value[index] != 0) __cplus_test_record_char(record, value[index++]);
}

static void __cplus_test_record_decimal(__cplus_test_record* record, unsigned long long value) {
    char digits[32];
    unsigned int count = 0;
    if (value == 0) digits[count++] = '0';
    while (value != 0) { digits[count++] = (char)('0' + value % 10ULL); value /= 10ULL; }
    while (count > 0) __cplus_test_record_char(record, digits[--count]);
}

static void __cplus_test_record_identity(__cplus_test_record* record, const char* identity) {
    static const char hex[] = "0123456789ABCDEF";
    unsigned long index = 0;
    if (!identity) { record->failed = 1; return; }
    while (identity[index] != 0) {
        unsigned char byte = (unsigned char)identity[index++];
        if ((byte >= 'a' && byte <= 'z') || (byte >= 'A' && byte <= 'Z') ||
            (byte >= '0' && byte <= '9') || byte == '_' || byte == '-' || byte == '.' || byte == '/' || byte == ':') {
            __cplus_test_record_char(record, (char)byte);
        } else {
            __cplus_test_record_char(record, '%');
            __cplus_test_record_char(record, hex[(byte >> 4) & 15U]);
            __cplus_test_record_char(record, hex[byte & 15U]);
        }
    }
}

static int __cplus_test_write_record(__cplus_test_record* record) {
    unsigned long long written = 0;
    if (record->failed || __cplus_test_result_handle < 0) return -1;
    while (written < record->used) {
        long long result = platform_file_write(__cplus_test_result_handle,
            record->data + written, (unsigned long long)record->used - written);
        if (result <= 0) return -1;
        written += (unsigned long long)result;
    }
    return 0;
}

static void __cplus_test_record_begin(void) {
    __cplus_test_record record = { { 0 }, 0, 0 };
    __cplus_test_record_text(&record, "CPLUS-TEST\t1\tBEGIN\t");
    __cplus_test_record_identity(&record, __cplus_test_fixture_identity);
    __cplus_test_record_char(&record, '\n');
    if (__cplus_test_write_record(&record) != 0) __cplus_test_io_failed = 1;
}

int __cplus_test_begin(const char* fixture_identity, const char* result_path) {
    if (__cplus_test_result_handle >= 0 || !fixture_identity || !result_path) return -1;
    __cplus_test_result_handle = platform_file_open(result_path,
        CPLUS_FILE_WRITE | CPLUS_FILE_CREATE | CPLUS_FILE_TRUNCATE);
    if (__cplus_test_result_handle < 0) {
        __cplus_test_io_failed = 1;
        return -1;
    }
    __cplus_test_fixture_identity = fixture_identity;
    __cplus_test_sequence = 0;
    __cplus_test_passed = 0;
    __cplus_test_failed = 0;
    __cplus_test_io_failed = 0;
    __cplus_test_record_begin();
    return __cplus_test_io_failed ? -1 : 0;
}

static int __cplus_test_record_assertion(int passed) {
    __cplus_test_record record = { { 0 }, 0, 0 };
    __cplus_test_record_text(&record, "CPLUS-TEST\t1\tASSERT\t");
    __cplus_test_record_decimal(&record, ++__cplus_test_sequence);
    __cplus_test_record_char(&record, '\t');
    __cplus_test_record_text(&record, passed ? "PASS\n" : "FAIL\n");
    if (passed) __cplus_test_passed++;
    else __cplus_test_failed++;
    if (__cplus_test_write_record(&record) != 0) __cplus_test_io_failed = 1;
    return __cplus_test_io_failed ? -1 : 0;
}

void __cplus_test_report_truth(const char* description, const char* expression,
                                      const void* value, const char* type_name,
                                      unsigned long long value_size, int value_kind,
                                      int is_null_pointer, int passed) {
    __cplus_test_output output = { { 0 }, 0, 0 };
    int format_result;
    __cplus_test_text(&output, "---- ");
    __cplus_test_escaped(&output, description);
    __cplus_test_text(&output, " ----------------\n---- ");
    __cplus_test_escaped(&output, expression);
    __cplus_test_text(&output, "\n---- evaluated: ");
    __cplus_test_text(&output, type_name);
    __cplus_test_char(&output, ' ');
    format_result = __cplus_test_value(&output, value, value_size, value_kind, is_null_pointer);
    __cplus_test_text(&output, "\nresult: ");
    __cplus_test_text(&output, passed ? "SUCCESS\n" : "FAIL\n");
    __cplus_test_flush(&output);
    if (format_result != 0 || output.failed) __cplus_test_io_failed = 1;
    __cplus_test_record_assertion(passed);
}

void __cplus_test_report_equality(const char* description,
                                         const char* expected_expression,
                                         const char* actual_expression,
                                         const void* expected_value,
                                         const char* expected_type,
                                         unsigned long long expected_size,
                                         int expected_kind,
                                         int expected_is_null_pointer,
                                         const void* actual_value,
                                         const char* actual_type,
                                         unsigned long long actual_size,
                                         int actual_kind,
                                         int actual_is_null_pointer,
                                         int passed) {
    __cplus_test_output output = { { 0 }, 0, 0 };
    int expected_result;
    int actual_result;
    __cplus_test_text(&output, "    - ");
    __cplus_test_escaped(&output, description);
    __cplus_test_text(&output, " ----------------\n    - expected expression: ");
    __cplus_test_escaped(&output, expected_expression);
    __cplus_test_text(&output, "\n    - expected value: ");
    __cplus_test_text(&output, expected_type);
    __cplus_test_char(&output, ' ');
    expected_result = __cplus_test_value(&output, expected_value, expected_size, expected_kind, expected_is_null_pointer);
    __cplus_test_text(&output, "\n    - evaluated expression: ");
    __cplus_test_escaped(&output, actual_expression);
    __cplus_test_text(&output, "\n    - evaluated value: ");
    __cplus_test_text(&output, actual_type);
    __cplus_test_char(&output, ' ');
    actual_result = __cplus_test_value(&output, actual_value, actual_size, actual_kind, actual_is_null_pointer);
    __cplus_test_text(&output, "\nresult: ");
    __cplus_test_text(&output, passed ? "SUCCESS\n" : "FAIL\n");
    __cplus_test_flush(&output);
    if (expected_result != 0 || actual_result != 0 || output.failed) __cplus_test_io_failed = 1;
    __cplus_test_record_assertion(passed);
}

int __cplus_test_finish(void) {
    __cplus_test_record record = { { 0 }, 0, 0 };
    int close_result = 0;
    if (__cplus_test_result_handle < 0) return -1;
    __cplus_test_record_text(&record, "CPLUS-TEST\t1\tCOMPLETE\t");
    __cplus_test_record_decimal(&record, __cplus_test_passed);
    __cplus_test_record_char(&record, '\t');
    __cplus_test_record_decimal(&record, __cplus_test_failed);
    __cplus_test_record_char(&record, '\t');
    __cplus_test_record_decimal(&record, __cplus_test_passed + __cplus_test_failed);
    __cplus_test_record_char(&record, '\n');
    if (__cplus_test_write_record(&record) != 0) __cplus_test_io_failed = 1;
    close_result = platform_file_close(__cplus_test_result_handle);
    __cplus_test_result_handle = -1;
    if (close_result != 0) __cplus_test_io_failed = 1;
    return __cplus_test_io_failed ? -1 : 0;
}
