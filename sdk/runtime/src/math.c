#include <math.h>
#include <errno.h>
#include <limits.h>

double fabs(double value) { return value < 0.0 ? -value : value; }

double sqrt(double value) {
    double estimate;
    int iteration;
    if (value < 0.0) {
        errno = EDOM;
        return 0.0 / 0.0;
    }
    if (value == 0.0) return 0.0;
    estimate = value > 1.0 ? value : 1.0;
    for (iteration = 0; iteration < 32; iteration++) estimate = (estimate + value / estimate) * 0.5;
    return estimate;
}


#if !defined(CPLUS_LONG_DOUBLE_FORMAT)
#if defined(__LDBL_MANT_DIG__) && __LDBL_MANT_DIG__ == 53
#define CPLUS_LONG_DOUBLE_FORMAT 1
#elif defined(__LDBL_MANT_DIG__) && __LDBL_MANT_DIG__ == 64
#define CPLUS_LONG_DOUBLE_FORMAT 2
#elif defined(__LDBL_MANT_DIG__) && __LDBL_MANT_DIG__ == 113
#define CPLUS_LONG_DOUBLE_FORMAT 3
#else
#error unsupported long double format for C+ math classification
#endif
#endif

#ifndef CPLUS_LONG_BITS
#if defined(__SIZEOF_LONG__)
#define CPLUS_LONG_BITS (__SIZEOF_LONG__ * 8)
#elif defined(_WIN32) || defined(_WIN64)
#define CPLUS_LONG_BITS 32
#else
#error unsupported C long width for math conversions
#endif
#endif

_Static_assert(sizeof(long) * CHAR_BIT == CPLUS_LONG_BITS, "target C long width disagrees with SDK ABI");
_Static_assert(sizeof(long long) * CHAR_BIT == 64, "SDK math conversions require 64-bit long long");
#if CPLUS_LONG_BITS == 32
_Static_assert(LONG_MAX == 2147483647L, "LLP64 LONG_MAX");
_Static_assert(LONG_MIN == (-2147483647L - 1L), "LLP64 LONG_MIN");
#elif CPLUS_LONG_BITS == 64
_Static_assert(LONG_MAX == 9223372036854775807L, "LP64 LONG_MAX");
_Static_assert(LONG_MIN == (-9223372036854775807L - 1L), "LP64 LONG_MIN");
#endif

static int cplus_math_classify_binary32(const unsigned char* bytes) {
    unsigned int exponent = ((unsigned int)(bytes[3] & 0x7f) << 1) | (bytes[2] >> 7);
    int fraction_zero = (bytes[2] & 0x7f) == 0 && bytes[1] == 0 && bytes[0] == 0;
    if (exponent == 0xff) return fraction_zero ? FP_INFINITE : FP_NAN;
    if (exponent == 0) return fraction_zero ? FP_ZERO : FP_SUBNORMAL;
    return FP_NORMAL;
}

static int cplus_math_classify_binary64(const unsigned char* bytes) {
    unsigned int exponent = ((unsigned int)(bytes[7] & 0x7f) << 4) | (bytes[6] >> 4);
    int fraction_zero = (bytes[6] & 0x0f) == 0 && bytes[5] == 0 && bytes[4] == 0 &&
        bytes[3] == 0 && bytes[2] == 0 && bytes[1] == 0 && bytes[0] == 0;
    if (exponent == 0x7ff) return fraction_zero ? FP_INFINITE : FP_NAN;
    if (exponent == 0) return fraction_zero ? FP_ZERO : FP_SUBNORMAL;
    return FP_NORMAL;
}

#if CPLUS_LONG_DOUBLE_FORMAT == 2
static int cplus_math_classify_x87(const unsigned char* bytes) {
    unsigned int exponent = ((unsigned int)(bytes[9] & 0x7f) << 8) | bytes[8];
    int significand_zero = bytes[7] == 0 && bytes[6] == 0 && bytes[5] == 0 && bytes[4] == 0 &&
        bytes[3] == 0 && bytes[2] == 0 && bytes[1] == 0 && bytes[0] == 0;
    int infinity_significand = bytes[7] == 0x80 && bytes[6] == 0 && bytes[5] == 0 && bytes[4] == 0 &&
        bytes[3] == 0 && bytes[2] == 0 && bytes[1] == 0 && bytes[0] == 0;
    if (exponent == 0x7fff) return infinity_significand ? FP_INFINITE : FP_NAN;
    if (exponent == 0) {
        if (significand_zero) return FP_ZERO;
        return (bytes[7] & 0x80) != 0 ? FP_NORMAL : FP_SUBNORMAL;
    }
    return (bytes[7] & 0x80) != 0 ? FP_NORMAL : FP_NAN;
}
#endif

#if CPLUS_LONG_DOUBLE_FORMAT == 3
static int cplus_math_classify_binary128(const unsigned char* bytes) {
    unsigned int exponent = ((unsigned int)(bytes[15] & 0x7f) << 8) | bytes[14];
    int fraction_zero = bytes[13] == 0 && bytes[12] == 0 && bytes[11] == 0 &&
        bytes[10] == 0 && bytes[9] == 0 && bytes[8] == 0 && bytes[7] == 0 && bytes[6] == 0 &&
        bytes[5] == 0 && bytes[4] == 0 && bytes[3] == 0 && bytes[2] == 0 && bytes[1] == 0 && bytes[0] == 0;
    if (exponent == 0x7fff) return fraction_zero ? FP_INFINITE : FP_NAN;
    if (exponent == 0) return fraction_zero ? FP_ZERO : FP_SUBNORMAL;
    return FP_NORMAL;
}
#endif

static int cplus_math_classify_float(float value) {
    return cplus_math_classify_binary32((const unsigned char*)&value);
}

static int cplus_math_classify_double(double value) {
    return cplus_math_classify_binary64((const unsigned char*)&value);
}

static int cplus_math_classify_long_double(long double value) {
    const unsigned char* bytes = (const unsigned char*)&value;
#if CPLUS_LONG_DOUBLE_FORMAT == 1
    return cplus_math_classify_binary64(bytes);
#elif CPLUS_LONG_DOUBLE_FORMAT == 2
    return cplus_math_classify_x87(bytes);
#elif CPLUS_LONG_DOUBLE_FORMAT == 3
    return cplus_math_classify_binary128(bytes);
#else
#error invalid CPLUS_LONG_DOUBLE_FORMAT
#endif
}

static int cplus_math_sign_float(float value) {
    return (((const unsigned char*)&value)[3] & 0x80) != 0;
}

static int cplus_math_sign_double(double value) {
    return (((const unsigned char*)&value)[7] & 0x80) != 0;
}

static int cplus_math_sign_long_double(long double value) {
    const unsigned char* bytes = (const unsigned char*)&value;
#if CPLUS_LONG_DOUBLE_FORMAT == 1
    return (bytes[7] & 0x80) != 0;
#elif CPLUS_LONG_DOUBLE_FORMAT == 2
    return (bytes[9] & 0x80) != 0;
#elif CPLUS_LONG_DOUBLE_FORMAT == 3
    return (bytes[15] & 0x80) != 0;
#else
#error invalid CPLUS_LONG_DOUBLE_FORMAT
#endif
}

static void cplus_math_clear_low_bits(unsigned char* bytes, unsigned int bit_count) {
    unsigned int whole_bytes = bit_count / 8;
    unsigned int remaining_bits = bit_count % 8;
    unsigned int index;
    for (index = 0; index < whole_bytes; index++) bytes[index] = 0;
    if (remaining_bits != 0) bytes[whole_bytes] &= (unsigned char)(0xffu << remaining_bits);
}

static void cplus_math_set_signed_zero(unsigned char* bytes, unsigned int sign_byte) {
    unsigned int index;
    for (index = 0; index < sign_byte; index++) bytes[index] = 0;
    bytes[sign_byte] &= 0x80;
}

static void cplus_math_truncate_representation(
    unsigned char* bytes,
    unsigned int exponent,
    int exponent_bias,
    unsigned int fraction_bits,
    unsigned int sign_byte
) {
    int unbiased_exponent = exponent == 0 ? 1 - exponent_bias : (int)exponent - exponent_bias;
    if (unbiased_exponent < 0) {
        cplus_math_set_signed_zero(bytes, sign_byte);
    } else if ((unsigned int)unbiased_exponent < fraction_bits) {
        cplus_math_clear_low_bits(bytes, fraction_bits - (unsigned int)unbiased_exponent);
    }
}

static int cplus_math_raw_bit(const unsigned char* bytes, unsigned int bit_index) {
    return (bytes[bit_index / 8] & (unsigned char)(1u << (bit_index % 8))) != 0;
}

static int cplus_math_is_odd_representation(
    const unsigned char* bytes,
    unsigned int exponent,
    int exponent_bias,
    unsigned int fraction_bits,
    int explicit_integer_bit
) {
    int unbiased_exponent;
    unsigned int bit_index;
    if (exponent == 0) return 0;
    unbiased_exponent = (int)exponent - exponent_bias;
    if (unbiased_exponent < 0 || (unsigned int)unbiased_exponent > fraction_bits) return 0;
    bit_index = fraction_bits - (unsigned int)unbiased_exponent;
    if (!explicit_integer_bit && bit_index == fraction_bits) return 1;
    return cplus_math_raw_bit(bytes, bit_index);
}

static float cplus_math_truncate_float(float value) {
    unsigned char* bytes = (unsigned char*)&value;
    unsigned int exponent;
    int kind = cplus_math_classify_float(value);
    if (kind != FP_NORMAL && kind != FP_SUBNORMAL) return value;
    exponent = ((unsigned int)(bytes[3] & 0x7f) << 1) | (bytes[2] >> 7);
    cplus_math_truncate_representation(bytes, exponent, 127, 23, 3);
    return value;
}

static double cplus_math_truncate_double(double value) {
    unsigned char* bytes = (unsigned char*)&value;
    unsigned int exponent;
    int kind = cplus_math_classify_double(value);
    if (kind != FP_NORMAL && kind != FP_SUBNORMAL) return value;
    exponent = ((unsigned int)(bytes[7] & 0x7f) << 4) | (bytes[6] >> 4);
    cplus_math_truncate_representation(bytes, exponent, 1023, 52, 7);
    return value;
}

static long double cplus_math_truncate_long_double(long double value) {
    unsigned char* bytes = (unsigned char*)&value;
    unsigned int exponent;
    int kind = cplus_math_classify_long_double(value);
    if (kind != FP_NORMAL && kind != FP_SUBNORMAL) return value;
#if CPLUS_LONG_DOUBLE_FORMAT == 1
    exponent = ((unsigned int)(bytes[7] & 0x7f) << 4) | (bytes[6] >> 4);
    cplus_math_truncate_representation(bytes, exponent, 1023, 52, 7);
#elif CPLUS_LONG_DOUBLE_FORMAT == 2
    exponent = ((unsigned int)(bytes[9] & 0x7f) << 8) | bytes[8];
    cplus_math_truncate_representation(bytes, exponent, 16383, 63, 9);
#elif CPLUS_LONG_DOUBLE_FORMAT == 3
    exponent = ((unsigned int)(bytes[15] & 0x7f) << 8) | bytes[14];
    cplus_math_truncate_representation(bytes, exponent, 16383, 112, 15);
#endif
    return value;
}

static int cplus_math_is_odd_float(float value) {
    const unsigned char* bytes = (const unsigned char*)&value;
    unsigned int exponent = ((unsigned int)(bytes[3] & 0x7f) << 1) | (bytes[2] >> 7);
    return cplus_math_is_odd_representation(bytes, exponent, 127, 23, 0);
}

static int cplus_math_is_odd_double(double value) {
    const unsigned char* bytes = (const unsigned char*)&value;
    unsigned int exponent = ((unsigned int)(bytes[7] & 0x7f) << 4) | (bytes[6] >> 4);
    return cplus_math_is_odd_representation(bytes, exponent, 1023, 52, 0);
}

static int cplus_math_is_odd_long_double(long double value) {
    const unsigned char* bytes = (const unsigned char*)&value;
#if CPLUS_LONG_DOUBLE_FORMAT == 1
    unsigned int exponent = ((unsigned int)(bytes[7] & 0x7f) << 4) | (bytes[6] >> 4);
    return cplus_math_is_odd_representation(bytes, exponent, 1023, 52, 0);
#elif CPLUS_LONG_DOUBLE_FORMAT == 2
    unsigned int exponent = ((unsigned int)(bytes[9] & 0x7f) << 8) | bytes[8];
    return cplus_math_is_odd_representation(bytes, exponent, 16383, 63, 1);
#elif CPLUS_LONG_DOUBLE_FORMAT == 3
    unsigned int exponent = ((unsigned int)(bytes[15] & 0x7f) << 8) | bytes[14];
    return cplus_math_is_odd_representation(bytes, exponent, 16383, 112, 0);
#endif
}

int cplus_math_fpclassifyf(float value) {
    return cplus_math_classify_float(value);
}

int cplus_math_isfinitef(float value) {
    int kind = cplus_math_classify_float(value);
    return kind != FP_INFINITE && kind != FP_NAN;
}

int cplus_math_isinff(float value) {
    return cplus_math_classify_float(value) == FP_INFINITE;
}

int cplus_math_isnanf(float value) {
    return cplus_math_classify_float(value) == FP_NAN;
}

int cplus_math_isnormalf(float value) {
    return cplus_math_classify_float(value) == FP_NORMAL;
}

int cplus_math_signbitf(float value) {
    return cplus_math_sign_float(value);
}

int cplus_math_fpclassify(double value) {
    return cplus_math_classify_double(value);
}

int cplus_math_isfinite(double value) {
    int kind = cplus_math_classify_double(value);
    return kind != FP_INFINITE && kind != FP_NAN;
}

int cplus_math_isinf(double value) {
    return cplus_math_classify_double(value) == FP_INFINITE;
}

int cplus_math_isnan(double value) {
    return cplus_math_classify_double(value) == FP_NAN;
}

int cplus_math_isnormal(double value) {
    return cplus_math_classify_double(value) == FP_NORMAL;
}

int cplus_math_signbit(double value) {
    return cplus_math_sign_double(value);
}

int cplus_math_fpclassifyl(long double value) {
    return cplus_math_classify_long_double(value);
}

int cplus_math_isfinitel(long double value) {
    int kind = cplus_math_classify_long_double(value);
    return kind != FP_INFINITE && kind != FP_NAN;
}

int cplus_math_isinfl(long double value) {
    return cplus_math_classify_long_double(value) == FP_INFINITE;
}

int cplus_math_isnanl(long double value) {
    return cplus_math_classify_long_double(value) == FP_NAN;
}

int cplus_math_isnormall(long double value) {
    return cplus_math_classify_long_double(value) == FP_NORMAL;
}

int cplus_math_signbitl(long double value) {
    return cplus_math_sign_long_double(value);
}

#define CPLUS_MATH_DEFINE_COMPARISONS(suffix, type, classifier) \
    int cplus_math_isgreater##suffix(type left, type right) { \
        return classifier(left) != FP_NAN && classifier(right) != FP_NAN && left > right; \
    } \
    int cplus_math_isgreaterequal##suffix(type left, type right) { \
        return classifier(left) != FP_NAN && classifier(right) != FP_NAN && left >= right; \
    } \
    int cplus_math_isless##suffix(type left, type right) { \
        return classifier(left) != FP_NAN && classifier(right) != FP_NAN && left < right; \
    } \
    int cplus_math_islessequal##suffix(type left, type right) { \
        return classifier(left) != FP_NAN && classifier(right) != FP_NAN && left <= right; \
    } \
    int cplus_math_islessgreater##suffix(type left, type right) { \
        return classifier(left) != FP_NAN && classifier(right) != FP_NAN && (left < right || left > right); \
    } \
    int cplus_math_isunordered##suffix(type left, type right) { \
        return classifier(left) == FP_NAN || classifier(right) == FP_NAN; \
    }

CPLUS_MATH_DEFINE_COMPARISONS(f, float, cplus_math_classify_float)
CPLUS_MATH_DEFINE_COMPARISONS(, double, cplus_math_classify_double)
CPLUS_MATH_DEFINE_COMPARISONS(l, long double, cplus_math_classify_long_double)

#undef CPLUS_MATH_DEFINE_COMPARISONS

#define CPLUS_MATH_DEFINE_ROUNDING(suffix, type, classifier, truncate_value, is_odd) \
    type trunc##suffix(type value) { \
        return truncate_value(value); \
    } \
    type ceil##suffix(type value) { \
        int kind = classifier(value); \
        type integral; \
        if (kind == FP_NAN || kind == FP_INFINITE || kind == FP_ZERO) return value; \
        integral = truncate_value(value); \
        return value > integral ? integral + (type)1 : integral; \
    } \
    type floor##suffix(type value) { \
        int kind = classifier(value); \
        type integral; \
        if (kind == FP_NAN || kind == FP_INFINITE || kind == FP_ZERO) return value; \
        integral = truncate_value(value); \
        return value < integral ? integral - (type)1 : integral; \
    } \
    type round##suffix(type value) { \
        int kind = classifier(value); \
        type integral; \
        type fraction; \
        if (kind == FP_NAN || kind == FP_INFINITE || kind == FP_ZERO) return value; \
        integral = truncate_value(value); \
        fraction = value - integral; \
        if (fraction >= (type)0.5) return integral + (type)1; \
        if (fraction <= (type)-0.5) return integral - (type)1; \
        return integral; \
    } \
    type rint##suffix(type value) { \
        int kind = classifier(value); \
        type integral; \
        type fraction; \
        if (kind == FP_NAN || kind == FP_INFINITE || kind == FP_ZERO) return value; \
        integral = truncate_value(value); \
        fraction = value - integral; \
        if (fraction > (type)0.5) return integral + (type)1; \
        if (fraction < (type)-0.5) return integral - (type)1; \
        if (fraction == (type)0.5 && is_odd(integral)) return integral + (type)1; \
        if (fraction == (type)-0.5 && is_odd(integral)) return integral - (type)1; \
        return integral; \
    } \
    type nearbyint##suffix(type value) { \
        return rint##suffix(value); \
    }

CPLUS_MATH_DEFINE_ROUNDING(f, float, cplus_math_classify_float, cplus_math_truncate_float, cplus_math_is_odd_float)
CPLUS_MATH_DEFINE_ROUNDING(, double, cplus_math_classify_double, cplus_math_truncate_double, cplus_math_is_odd_double)
CPLUS_MATH_DEFINE_ROUNDING(l, long double, cplus_math_classify_long_double, cplus_math_truncate_long_double, cplus_math_is_odd_long_double)

#undef CPLUS_MATH_DEFINE_ROUNDING

static long cplus_math_round_to_long(long double value) {
    int kind = cplus_math_classify_long_double(value);
    if (kind == FP_NAN || kind == FP_INFINITE) {
        errno = EDOM;
        return LONG_MIN;
    }
#if CPLUS_LONG_BITS == 32
    if (value < -2147483648.0L || value >= 2147483648.0L) {
#elif CPLUS_LONG_BITS == 64
    if (value < -9223372036854775808.0L || value >= 9223372036854775808.0L) {
#else
#error unsupported C long width for math conversions
#endif
        errno = EDOM;
        return LONG_MIN;
    }
    return (long)value;
}

static long long cplus_math_round_to_long_long(long double value) {
    int kind = cplus_math_classify_long_double(value);
    if (kind == FP_NAN || kind == FP_INFINITE ||
        value < -9223372036854775808.0L || value >= 9223372036854775808.0L) {
        errno = EDOM;
        return LLONG_MIN;
    }
    return (long long)value;
}

#define CPLUS_MATH_DEFINE_INTEGER_ROUNDING(suffix, type, round_even, round_away) \
    long lrint##suffix(type value) { \
        return cplus_math_round_to_long((long double)round_even(value)); \
    } \
    long long llrint##suffix(type value) { \
        return cplus_math_round_to_long_long((long double)round_even(value)); \
    } \
    long lround##suffix(type value) { \
        return cplus_math_round_to_long((long double)round_away(value)); \
    } \
    long long llround##suffix(type value) { \
        return cplus_math_round_to_long_long((long double)round_away(value)); \
    }

CPLUS_MATH_DEFINE_INTEGER_ROUNDING(f, float, rintf, roundf)
CPLUS_MATH_DEFINE_INTEGER_ROUNDING(, double, rint, round)
CPLUS_MATH_DEFINE_INTEGER_ROUNDING(l, long double, rintl, roundl)

#undef CPLUS_MATH_DEFINE_INTEGER_ROUNDING
