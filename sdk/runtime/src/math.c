#include <math.h>
#include <errno.h>
#include <limits.h>

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

static int cplus_math_highest_set_bit(const unsigned char* bytes, int bit_count) {
    int bit_index;
    for (bit_index = bit_count - 1; bit_index >= 0; bit_index--) {
        if (cplus_math_raw_bit(bytes, (unsigned int)bit_index)) return bit_index;
    }
    return -1;
}

static int cplus_math_ilogb_float(float value) {
    const unsigned char* bytes = (const unsigned char*)&value;
    int kind = cplus_math_classify_float(value);
    unsigned int exponent = ((unsigned int)(bytes[3] & 0x7f) << 1) | (bytes[2] >> 7);
    if (kind == FP_NAN) {
        errno = EDOM;
        return FP_ILOGBNAN;
    }
    if (kind == FP_ZERO) {
        errno = EDOM;
        return FP_ILOGB0;
    }
    if (kind == FP_INFINITE) return INT_MAX;
    if (exponent != 0) return (int)exponent - 127;
    return cplus_math_highest_set_bit(bytes, 23) - 149;
}

static int cplus_math_ilogb_double(double value) {
    const unsigned char* bytes = (const unsigned char*)&value;
    int kind = cplus_math_classify_double(value);
    unsigned int exponent = ((unsigned int)(bytes[7] & 0x7f) << 4) | (bytes[6] >> 4);
    if (kind == FP_NAN) {
        errno = EDOM;
        return FP_ILOGBNAN;
    }
    if (kind == FP_ZERO) {
        errno = EDOM;
        return FP_ILOGB0;
    }
    if (kind == FP_INFINITE) return INT_MAX;
    if (exponent != 0) return (int)exponent - 1023;
    return cplus_math_highest_set_bit(bytes, 52) - 1074;
}

static int cplus_math_ilogb_long_double(long double value) {
    const unsigned char* bytes = (const unsigned char*)&value;
    int kind = cplus_math_classify_long_double(value);
    unsigned int exponent;
    if (kind == FP_NAN) {
        errno = EDOM;
        return FP_ILOGBNAN;
    }
    if (kind == FP_ZERO) {
        errno = EDOM;
        return FP_ILOGB0;
    }
    if (kind == FP_INFINITE) return INT_MAX;
#if CPLUS_LONG_DOUBLE_FORMAT == 1
    exponent = ((unsigned int)(bytes[7] & 0x7f) << 4) | (bytes[6] >> 4);
    if (exponent != 0) return (int)exponent - 1023;
    return cplus_math_highest_set_bit(bytes, 52) - 1074;
#elif CPLUS_LONG_DOUBLE_FORMAT == 2
    exponent = ((unsigned int)(bytes[9] & 0x7f) << 8) | bytes[8];
    if (exponent != 0) return (int)exponent - 16383;
    return cplus_math_highest_set_bit(bytes, 63) - 16445;
#elif CPLUS_LONG_DOUBLE_FORMAT == 3
    exponent = ((unsigned int)(bytes[15] & 0x7f) << 8) | bytes[14];
    if (exponent != 0) return (int)exponent - 16383;
    return cplus_math_highest_set_bit(bytes, 112) - 16494;
#endif
}

int ilogbf(float value) {
    return cplus_math_ilogb_float(value);
}

int ilogb(double value) {
    return cplus_math_ilogb_double(value);
}

int ilogbl(long double value) {
    return cplus_math_ilogb_long_double(value);
}

#define CPLUS_MATH_DEFINE_FREXP(suffix, type, classifier, ilogb_value) \
    type frexp##suffix(type value, int* exponent_output) { \
        int kind = classifier(value); \
        int exponent; \
        if (kind == FP_ZERO || kind == FP_INFINITE || kind == FP_NAN) { \
            if (exponent_output != (int*)0) *exponent_output = 0; \
            return value; \
        } \
        exponent = ilogb_value(value) + 1; \
        if (exponent > 0) { \
            int step; \
            for (step = 0; step < exponent; step++) value *= (type)0.5; \
        } else { \
            int step; \
            for (step = exponent; step < 0; step++) value *= (type)2; \
        } \
        if (exponent_output != (int*)0) *exponent_output = exponent; \
        return value; \
    }

CPLUS_MATH_DEFINE_FREXP(f, float, cplus_math_classify_float, cplus_math_ilogb_float)
CPLUS_MATH_DEFINE_FREXP(, double, cplus_math_classify_double, cplus_math_ilogb_double)
CPLUS_MATH_DEFINE_FREXP(l, long double, cplus_math_classify_long_double, cplus_math_ilogb_long_double)

#undef CPLUS_MATH_DEFINE_FREXP

#define CPLUS_MATH_DEFINE_MODF(suffix, type, classifier, truncate_value, copy_sign) \
    type modf##suffix(type value, type* integral_output) { \
        int kind = classifier(value); \
        type integral; \
        type fraction; \
        if (kind == FP_NAN || kind == FP_INFINITE) { \
            if (integral_output != (type*)0) *integral_output = value; \
            if (kind == FP_INFINITE) return copy_sign((type)0, value); \
            return value; \
        } \
        if (kind == FP_ZERO) { \
            if (integral_output != (type*)0) *integral_output = value; \
            return value; \
        } \
        integral = truncate_value(value); \
        if (integral == value) fraction = copy_sign((type)0, value); \
        else fraction = value - integral; \
        if (integral_output != (type*)0) *integral_output = integral; \
        return fraction; \
    }

static float cplus_math_copysign_float(float magnitude, float sign) {
    unsigned char* magnitude_bytes = (unsigned char*)&magnitude;
    const unsigned char* sign_bytes = (const unsigned char*)&sign;
    magnitude_bytes[3] = (unsigned char)((magnitude_bytes[3] & 0x7f) | (sign_bytes[3] & 0x80));
    return magnitude;
}

static double cplus_math_copysign_double(double magnitude, double sign) {
    unsigned char* magnitude_bytes = (unsigned char*)&magnitude;
    const unsigned char* sign_bytes = (const unsigned char*)&sign;
    magnitude_bytes[7] = (unsigned char)((magnitude_bytes[7] & 0x7f) | (sign_bytes[7] & 0x80));
    return magnitude;
}

static long double cplus_math_copysign_long_double(long double magnitude, long double sign) {
    unsigned char* magnitude_bytes = (unsigned char*)&magnitude;
    const unsigned char* sign_bytes = (const unsigned char*)&sign;
#if CPLUS_LONG_DOUBLE_FORMAT == 1
    magnitude_bytes[7] = (unsigned char)((magnitude_bytes[7] & 0x7f) | (sign_bytes[7] & 0x80));
#elif CPLUS_LONG_DOUBLE_FORMAT == 2
    magnitude_bytes[9] = (unsigned char)((magnitude_bytes[9] & 0x7f) | (sign_bytes[9] & 0x80));
#elif CPLUS_LONG_DOUBLE_FORMAT == 3
    magnitude_bytes[15] = (unsigned char)((magnitude_bytes[15] & 0x7f) | (sign_bytes[15] & 0x80));
#endif
    return magnitude;
}

CPLUS_MATH_DEFINE_MODF(f, float, cplus_math_classify_float, cplus_math_truncate_float, cplus_math_copysign_float)
CPLUS_MATH_DEFINE_MODF(, double, cplus_math_classify_double, cplus_math_truncate_double, cplus_math_copysign_double)
CPLUS_MATH_DEFINE_MODF(l, long double, cplus_math_classify_long_double, cplus_math_truncate_long_double, cplus_math_copysign_long_double)

#undef CPLUS_MATH_DEFINE_MODF

float copysignf(float magnitude, float sign) {
    return cplus_math_copysign_float(magnitude, sign);
}

double copysign(double magnitude, double sign) {
    return cplus_math_copysign_double(magnitude, sign);
}

long double copysignl(long double magnitude, long double sign) {
    return cplus_math_copysign_long_double(magnitude, sign);
}

static unsigned long long cplus_math_nan_payload(const char* tag) {
    unsigned long long hash = 14695981039346656037ULL;
    if (tag == (const char*)0) return hash;
    while (*tag != '\0') {
        hash ^= (unsigned char)*tag++;
        hash *= 1099511628211ULL;
    }
    return hash;
}

static float cplus_math_make_nan_float(const char* tag) {
    float value = 0.0f;
    unsigned char* bytes = (unsigned char*)&value;
    unsigned long long payload = cplus_math_nan_payload(tag) & 0x3fffffULL;
    bytes[0] = (unsigned char)payload;
    bytes[1] = (unsigned char)(payload >> 8);
    bytes[2] = (unsigned char)(0xc0 | ((payload >> 16) & 0x3f));
    bytes[3] = 0x7f;
    return value;
}

static double cplus_math_make_nan_double(const char* tag) {
    double value = 0.0;
    unsigned char* bytes = (unsigned char*)&value;
    unsigned long long payload = cplus_math_nan_payload(tag) & 0x7ffffffffffffULL;
    unsigned int index;
    for (index = 0; index < 6; index++) bytes[index] = (unsigned char)(payload >> (index * 8));
    bytes[6] = (unsigned char)(0xf8 | ((payload >> 48) & 0x07));
    bytes[7] = 0x7f;
    return value;
}

static long double cplus_math_make_nan_long_double(const char* tag) {
    long double value = 0.0L;
    unsigned char* bytes = (unsigned char*)&value;
    unsigned long long payload = cplus_math_nan_payload(tag);
    unsigned int index;
    for (index = 0; index < sizeof(value); index++) bytes[index] = 0;
#if CPLUS_LONG_DOUBLE_FORMAT == 1
    for (index = 0; index < 6; index++) bytes[index] = (unsigned char)(payload >> (index * 8));
    bytes[6] = (unsigned char)(0xf8 | ((payload >> 48) & 0x07));
    bytes[7] = 0x7f;
#elif CPLUS_LONG_DOUBLE_FORMAT == 2
    for (index = 0; index < 7; index++) bytes[index] = (unsigned char)(payload >> (index * 8));
    bytes[7] = (unsigned char)(0xc0 | ((payload >> 56) & 0x3f));
    bytes[8] = 0xff;
    bytes[9] = 0x7f;
#elif CPLUS_LONG_DOUBLE_FORMAT == 3
    for (index = 0; index < 8; index++) bytes[index] = (unsigned char)(payload >> (index * 8));
    bytes[13] = 0x80;
    bytes[14] = 0xff;
    bytes[15] = 0x7f;
#endif
    return value;
}

float nanf(const char* tag) {
    return cplus_math_make_nan_float(tag);
}

double nan(const char* tag) {
    return cplus_math_make_nan_double(tag);
}

long double nanl(const char* tag) {
    return cplus_math_make_nan_long_double(tag);
}

static float cplus_math_scale_float(float value, long exponent) {
    int kind = cplus_math_classify_float(value);
    int current_exponent;
    if (kind == FP_ZERO || kind == FP_INFINITE || kind == FP_NAN) return value;
    current_exponent = cplus_math_ilogb_float(value);
    if (exponent > (long)(127 - current_exponent)) {
        errno = ERANGE;
        return cplus_math_copysign_float(HUGE_VALF, value);
    }
    if (exponent < (long)(-149 - 1 - current_exponent)) {
        errno = ERANGE;
        return cplus_math_copysign_float(0.0f, value);
    }
    while (exponent > 0) {
        value *= 2.0f;
        exponent--;
        if (cplus_math_classify_float(value) == FP_INFINITE) {
            errno = ERANGE;
            return value;
        }
    }
    while (exponent < 0) {
        value *= 0.5f;
        exponent++;
        if (cplus_math_classify_float(value) == FP_ZERO) {
            errno = ERANGE;
            return value;
        }
    }
    return value;
}

static double cplus_math_scale_double(double value, long exponent) {
    int kind = cplus_math_classify_double(value);
    int current_exponent;
    if (kind == FP_ZERO || kind == FP_INFINITE || kind == FP_NAN) return value;
    current_exponent = cplus_math_ilogb_double(value);
    if (exponent > (long)(1023 - current_exponent)) {
        errno = ERANGE;
        return cplus_math_copysign_double(HUGE_VAL, value);
    }
    if (exponent < (long)(-1074 - 1 - current_exponent)) {
        errno = ERANGE;
        return cplus_math_copysign_double(0.0, value);
    }
    while (exponent > 0) {
        value *= 2.0;
        exponent--;
        if (cplus_math_classify_double(value) == FP_INFINITE) {
            errno = ERANGE;
            return value;
        }
    }
    while (exponent < 0) {
        value *= 0.5;
        exponent++;
        if (cplus_math_classify_double(value) == FP_ZERO) {
            errno = ERANGE;
            return value;
        }
    }
    return value;
}

static long double cplus_math_scale_long_double(long double value, long exponent) {
    int kind = cplus_math_classify_long_double(value);
    int current_exponent;
    int maximum_exponent;
    int minimum_subnormal_exponent;
    if (kind == FP_ZERO || kind == FP_INFINITE || kind == FP_NAN) return value;
    current_exponent = cplus_math_ilogb_long_double(value);
#if CPLUS_LONG_DOUBLE_FORMAT == 1
    maximum_exponent = 1023;
    minimum_subnormal_exponent = -1074;
#elif CPLUS_LONG_DOUBLE_FORMAT == 2
    maximum_exponent = 16383;
    minimum_subnormal_exponent = -16445;
#elif CPLUS_LONG_DOUBLE_FORMAT == 3
    maximum_exponent = 16383;
    minimum_subnormal_exponent = -16494;
#endif
    if (exponent > (long)(maximum_exponent - current_exponent)) {
        errno = ERANGE;
        return cplus_math_copysign_long_double(HUGE_VALL, value);
    }
    if (exponent < (long)(minimum_subnormal_exponent - 1 - current_exponent)) {
        errno = ERANGE;
        return cplus_math_copysign_long_double(0.0L, value);
    }
    while (exponent > 0) {
        value *= 2.0L;
        exponent--;
        if (cplus_math_classify_long_double(value) == FP_INFINITE) {
            errno = ERANGE;
            return value;
        }
    }
    while (exponent < 0) {
        value *= 0.5L;
        exponent++;
        if (cplus_math_classify_long_double(value) == FP_ZERO) {
            errno = ERANGE;
            return value;
        }
    }
    return value;
}

#define CPLUS_MATH_DEFINE_SCALING(suffix, type, scale) \
    type ldexp##suffix(type value, int exponent) { \
        return scale(value, (long)exponent); \
    } \
    type scalbn##suffix(type value, int exponent) { \
        return scale(value, (long)exponent); \
    } \
    type scalbln##suffix(type value, long int exponent) { \
        return scale(value, exponent); \
    }

CPLUS_MATH_DEFINE_SCALING(f, float, cplus_math_scale_float)
CPLUS_MATH_DEFINE_SCALING(, double, cplus_math_scale_double)
CPLUS_MATH_DEFINE_SCALING(l, long double, cplus_math_scale_long_double)

#undef CPLUS_MATH_DEFINE_SCALING

#define CPLUS_MATH_DEFINE_REMAINDER(suffix, type, classifier, ilogb_value, scale, copy_sign, sign_value) \
    static type cplus_math_reduce_##suffix(type dividend, type divisor, int* quotient_low) { \
        int shift; \
        int quotient = 0; \
        type scaled_divisor; \
        if (dividend < divisor) { \
            *quotient_low = 0; \
            return dividend; \
        } \
        shift = ilogb_value(dividend) - ilogb_value(divisor); \
        scaled_divisor = scale(divisor, (long)shift); \
        if (scaled_divisor > dividend) { \
            scaled_divisor *= (type)0.5; \
            shift--; \
        } \
        while (shift >= 0) { \
            int subtract = dividend >= scaled_divisor; \
            quotient = ((quotient << 1) | subtract) & 7; \
            if (subtract) dividend -= scaled_divisor; \
            if (shift == 0) break; \
            scaled_divisor *= (type)0.5; \
            shift--; \
        } \
        *quotient_low = quotient; \
        return dividend; \
    } \
    static type cplus_math_remainder_value_##suffix( \
        type dividend, type divisor, int nearest, int* quotient_output) { \
        int dividend_kind = classifier(dividend); \
        int divisor_kind = classifier(divisor); \
        int quotient_low = 0; \
        int round_up = 0; \
        type magnitude; \
        type divisor_magnitude; \
        type result; \
        if (dividend_kind == FP_NAN || divisor_kind == FP_NAN) { \
            if (quotient_output != (int*)0) *quotient_output = 0; \
            return dividend_kind == FP_NAN ? dividend : divisor; \
        } \
        if (dividend_kind == FP_INFINITE || divisor_kind == FP_ZERO) { \
            errno = EDOM; \
            if (quotient_output != (int*)0) *quotient_output = 0; \
            return (type)NAN; \
        } \
        if (dividend_kind == FP_ZERO || divisor_kind == FP_INFINITE) { \
            if (quotient_output != (int*)0) *quotient_output = 0; \
            return dividend; \
        } \
        divisor_magnitude = divisor < (type)0 ? -divisor : divisor; \
        magnitude = cplus_math_reduce_##suffix( \
            dividend < (type)0 ? -dividend : dividend, divisor_magnitude, &quotient_low); \
        if (nearest) { \
            type other_distance = divisor_magnitude - magnitude; \
            if (magnitude > other_distance || \
                (magnitude == other_distance && (quotient_low & 1) != 0)) { \
                magnitude = other_distance; \
                round_up = 1; \
                quotient_low = (quotient_low + 1) & 7; \
            } \
        } \
        if (quotient_output != (int*)0) { \
            int quotient_negative = sign_value(dividend) != sign_value(divisor); \
            *quotient_output = quotient_negative ? -quotient_low : quotient_low; \
        } \
        result = copy_sign(magnitude, dividend); \
        return round_up ? -result : result; \
    } \
    type fmod##suffix(type dividend, type divisor) { \
        return cplus_math_remainder_value_##suffix(dividend, divisor, 0, (int*)0); \
    } \
    type remainder##suffix(type dividend, type divisor) { \
        return cplus_math_remainder_value_##suffix(dividend, divisor, 1, (int*)0); \
    } \
    type remquo##suffix(type dividend, type divisor, int* quotient_output) { \
        return cplus_math_remainder_value_##suffix(dividend, divisor, 1, quotient_output); \
    }

CPLUS_MATH_DEFINE_REMAINDER(
    f, float, cplus_math_classify_float, cplus_math_ilogb_float,
    cplus_math_scale_float, cplus_math_copysign_float, cplus_math_sign_float)
CPLUS_MATH_DEFINE_REMAINDER(
    , double, cplus_math_classify_double, cplus_math_ilogb_double,
    cplus_math_scale_double, cplus_math_copysign_double, cplus_math_sign_double)
CPLUS_MATH_DEFINE_REMAINDER(
    l, long double, cplus_math_classify_long_double, cplus_math_ilogb_long_double,
    cplus_math_scale_long_double, cplus_math_copysign_long_double, cplus_math_sign_long_double)

#undef CPLUS_MATH_DEFINE_REMAINDER

#define CPLUS_MATH_DEFINE_ROOTS(suffix, type, classifier, sign_value, frexp_value, scale_value, copy_sign) \
    type fabs##suffix(type value) { \
        return copy_sign(value, (type)1); \
    } \
    type sqrt##suffix(type value) { \
        int kind = classifier(value); \
        int exponent; \
        int iteration; \
        type mantissa; \
        type estimate; \
        if (kind == FP_NAN) return value; \
        if (kind == FP_ZERO) return value; \
        if (kind == FP_INFINITE) { \
            if (!sign_value(value)) return value; \
            errno = EDOM; \
            return (type)NAN; \
        } \
        if (sign_value(value)) { \
            errno = EDOM; \
            return (type)NAN; \
        } \
        mantissa = frexp_value(value, &exponent); \
        if (exponent % 2 != 0) { \
            mantissa *= (type)2; \
            exponent--; \
        } \
        estimate = (mantissa + (type)1) * (type)0.5; \
        for (iteration = 0; iteration < 16; iteration++) \
            estimate = (estimate + mantissa / estimate) * (type)0.5; \
        return scale_value(estimate, (long)(exponent / 2)); \
    } \
    type cbrt##suffix(type value) { \
        int kind = classifier(value); \
        int exponent; \
        int remainder; \
        int iteration; \
        int negative = sign_value(value); \
        type magnitude; \
        type mantissa; \
        type estimate; \
        if (kind == FP_ZERO || kind == FP_INFINITE || kind == FP_NAN) return value; \
        magnitude = negative ? -value : value; \
        mantissa = frexp_value(magnitude, &exponent); \
        remainder = exponent % 3; \
        if (remainder < 0) remainder += 3; \
        mantissa = scale_value(mantissa, (long)remainder); \
        exponent = (exponent - remainder) / 3; \
        estimate = (mantissa + (type)2) / (type)3; \
        for (iteration = 0; iteration < 16; iteration++) \
            estimate = ((type)2 * estimate + mantissa / (estimate * estimate)) / (type)3; \
        estimate = scale_value(estimate, (long)exponent); \
        return copy_sign(estimate, value); \
    }

CPLUS_MATH_DEFINE_ROOTS(
    f, float, cplus_math_classify_float, cplus_math_sign_float, frexpf,
    cplus_math_scale_float, cplus_math_copysign_float)
CPLUS_MATH_DEFINE_ROOTS(
    , double, cplus_math_classify_double, cplus_math_sign_double, frexp,
    cplus_math_scale_double, cplus_math_copysign_double)
CPLUS_MATH_DEFINE_ROOTS(
    l, long double, cplus_math_classify_long_double, cplus_math_sign_long_double, frexpl,
    cplus_math_scale_long_double, cplus_math_copysign_long_double)

#undef CPLUS_MATH_DEFINE_ROOTS

#define CPLUS_MATH_DEFINE_HYPOT(suffix, type, classifier, absolute_value, sqrt_value, copy_sign) \
    type hypot##suffix(type left, type right) { \
        int left_kind = classifier(left); \
        int right_kind = classifier(right); \
        type larger; \
        type smaller; \
        type ratio; \
        type result; \
        if (left_kind == FP_INFINITE || right_kind == FP_INFINITE) return (type)HUGE_VALL; \
        if (left_kind == FP_NAN || right_kind == FP_NAN) \
            return copy_sign(left_kind == FP_NAN ? left : right, (type)1); \
        larger = absolute_value(left); \
        smaller = absolute_value(right); \
        if (larger < smaller) { \
            type temporary = larger; \
            larger = smaller; \
            smaller = temporary; \
        } \
        if (larger == (type)0) return (type)0; \
        ratio = smaller / larger; \
        result = larger * sqrt_value((type)1 + ratio * ratio); \
        if (classifier(result) == FP_INFINITE) errno = ERANGE; \
        return copy_sign(result, (type)1); \
    }

CPLUS_MATH_DEFINE_HYPOT(f, float, cplus_math_classify_float, fabsf, sqrtf, cplus_math_copysign_float)
CPLUS_MATH_DEFINE_HYPOT(, double, cplus_math_classify_double, fabs, sqrt, cplus_math_copysign_double)
CPLUS_MATH_DEFINE_HYPOT(l, long double, cplus_math_classify_long_double, fabsl, sqrtl, cplus_math_copysign_long_double)

#undef CPLUS_MATH_DEFINE_HYPOT

#define CPLUS_MATH_DEFINE_LOG_EXP(suffix, type, classifier, frexp_value, scale_value, max_exponent, min_subnormal_exponent) \
    static type cplus_math_log_positive_##suffix(type value) { \
        const type ln_two = (type)0x1.62e42fefa39ef35793c7673007e6p-1L; \
        int exponent; \
        int denominator; \
        type mantissa = frexp_value(value, &exponent) * (type)2; \
        type z = (mantissa - (type)1) / (mantissa + (type)1); \
        type z_squared = z * z; \
        type term = z; \
        type sum = z; \
        exponent--; \
        for (denominator = 3; denominator <= 95; denominator += 2) { \
            term *= z_squared; \
            sum += term / (type)denominator; \
        } \
        return (type)2 * sum + (type)exponent * ln_two; \
    } \
    static type cplus_math_exp_##suffix(type value) { \
        const type ln_two = (type)0x1.62e42fefa39ef35793c7673007e6p-1L; \
        int kind = classifier(value); \
        int exponent; \
        int iteration; \
        type quotient; \
        type fraction; \
        type reduced; \
        type term = (type)1; \
        type sum = (type)1; \
        if (kind == FP_NAN) return value; \
        if (kind == FP_INFINITE) return value < (type)0 ? (type)0 : (type)HUGE_VALL; \
        if (value > (type)(max_exponent + 2) * ln_two) { \
            errno = ERANGE; \
            return (type)HUGE_VALL; \
        } \
        if (value < (type)(min_subnormal_exponent - 2) * ln_two) { \
            errno = ERANGE; \
            return (type)0; \
        } \
        quotient = value / ln_two; \
        exponent = (int)quotient; \
        fraction = quotient - (type)exponent; \
        if (fraction > (type)0.5 || \
            (fraction == (type)0.5 && exponent % 2 != 0)) exponent++; \
        else if (fraction < (type)-0.5 || \
            (fraction == (type)-0.5 && exponent % 2 != 0)) exponent--; \
        reduced = value - (type)exponent * ln_two; \
        for (iteration = 1; iteration <= 48; iteration++) { \
            term *= reduced / (type)iteration; \
            sum += term; \
        } \
        sum = scale_value(sum, (long)exponent); \
        if (classifier(sum) == FP_INFINITE || classifier(sum) == FP_ZERO) errno = ERANGE; \
        return sum; \
    }

CPLUS_MATH_DEFINE_LOG_EXP(f, float, cplus_math_classify_float, frexpf, cplus_math_scale_float, 127, -149)
CPLUS_MATH_DEFINE_LOG_EXP(, double, cplus_math_classify_double, frexp, cplus_math_scale_double, 1023, -1074)
#if CPLUS_LONG_DOUBLE_FORMAT == 1
CPLUS_MATH_DEFINE_LOG_EXP(l, long double, cplus_math_classify_long_double, frexpl, cplus_math_scale_long_double, 1023, -1074)
#elif CPLUS_LONG_DOUBLE_FORMAT == 2
CPLUS_MATH_DEFINE_LOG_EXP(l, long double, cplus_math_classify_long_double, frexpl, cplus_math_scale_long_double, 16383, -16445)
#elif CPLUS_LONG_DOUBLE_FORMAT == 3
CPLUS_MATH_DEFINE_LOG_EXP(l, long double, cplus_math_classify_long_double, frexpl, cplus_math_scale_long_double, 16383, -16494)
#endif

#undef CPLUS_MATH_DEFINE_LOG_EXP

#define CPLUS_MATH_DEFINE_POW(suffix, type, classifier, sign_value, truncate_value, is_odd, log_positive, exp_value, copy_sign) \
    static type cplus_math_integer_power_##suffix(type base, type exponent) { \
        int negative_exponent = exponent < (type)0; \
        type remaining = negative_exponent ? -exponent : exponent; \
        type factor = negative_exponent ? (type)1 / base : base; \
        type result = (type)1; \
        while (remaining >= (type)1) { \
            if (is_odd(remaining)) result *= factor; \
            remaining = truncate_value(remaining * (type)0.5); \
            if (remaining >= (type)1) factor *= factor; \
        } \
        if (classifier(result) == FP_INFINITE || classifier(result) == FP_ZERO) errno = ERANGE; \
        return result; \
    } \
    type pow##suffix(type base, type exponent) { \
        int base_kind = classifier(base); \
        int exponent_kind = classifier(exponent); \
        int base_negative = sign_value(base); \
        int integer_exponent; \
        int odd_exponent; \
        type magnitude; \
        type logarithm; \
        type argument; \
        type result; \
        if (exponent_kind == FP_ZERO || base == (type)1) return (type)1; \
        if (exponent_kind == FP_NAN || base_kind == FP_NAN) \
            return exponent_kind == FP_NAN ? exponent : base; \
        magnitude = base_negative ? -base : base; \
        if (exponent_kind == FP_INFINITE) { \
            if (magnitude == (type)1) return (type)1; \
            if ((magnitude > (type)1) == (exponent > (type)0)) return (type)HUGE_VALL; \
            return (type)0; \
        } \
        integer_exponent = truncate_value(exponent) == exponent; \
        odd_exponent = integer_exponent && is_odd(exponent); \
        if (base_kind == FP_INFINITE) { \
            result = exponent > (type)0 ? (type)HUGE_VALL : (type)0; \
            return base_negative && odd_exponent ? -result : result; \
        } \
        if (base_kind == FP_ZERO) { \
            if (exponent < (type)0) { \
                errno = ERANGE; \
                result = (type)HUGE_VALL; \
            } else result = (type)0; \
            return base_negative && odd_exponent ? -result : result; \
        } \
        if (base_negative && !integer_exponent) { \
            errno = EDOM; \
            return (type)NAN; \
        } \
        if (integer_exponent) return cplus_math_integer_power_##suffix(base, exponent); \
        logarithm = log_positive(magnitude); \
        argument = exponent * logarithm; \
        if (classifier(argument) == FP_INFINITE) { \
            errno = ERANGE; \
            return argument < (type)0 ? (type)0 : (type)HUGE_VALL; \
        } \
        result = exp_value(argument); \
        return copy_sign(result, (type)1); \
    }

CPLUS_MATH_DEFINE_POW(
    f, float, cplus_math_classify_float, cplus_math_sign_float, cplus_math_truncate_float,
    cplus_math_is_odd_float, cplus_math_log_positive_f, cplus_math_exp_f, cplus_math_copysign_float)
CPLUS_MATH_DEFINE_POW(
    , double, cplus_math_classify_double, cplus_math_sign_double, cplus_math_truncate_double,
    cplus_math_is_odd_double, cplus_math_log_positive_, cplus_math_exp_, cplus_math_copysign_double)
CPLUS_MATH_DEFINE_POW(
    l, long double, cplus_math_classify_long_double, cplus_math_sign_long_double, cplus_math_truncate_long_double,
    cplus_math_is_odd_long_double, cplus_math_log_positive_l, cplus_math_exp_l, cplus_math_copysign_long_double)

#undef CPLUS_MATH_DEFINE_POW

#define CPLUS_MATH_DEFINE_EXP_LOG_API( \
    suffix, type, classifier, sign_value, frexp_value, truncate_value, ilogb_value, \
    log_positive, exp_value, scale_value, maximum_exponent, minimum_subnormal_exponent) \
    type exp##suffix(type value) { \
        return exp_value(value); \
    } \
    type exp2##suffix(type value) { \
        int kind = classifier(value); \
        if (kind == FP_NAN) return value; \
        if (kind == FP_INFINITE) return sign_value(value) ? (type)0 : (type)HUGE_VALL; \
        if (value > (type)(maximum_exponent + 2)) { \
            errno = ERANGE; \
            return (type)HUGE_VALL; \
        } \
        if (value < (type)(minimum_subnormal_exponent - 2)) { \
            errno = ERANGE; \
            return (type)0; \
        } \
        if (truncate_value(value) == value) return scale_value((type)1, (long)value); \
        return exp_value(value * (type)0x1.62e42fefa39ef35793c7673007e6p-1L); \
    } \
    type expm1##suffix(type value) { \
        int kind = classifier(value); \
        int iteration; \
        type term; \
        type sum; \
        type magnitude; \
        if (kind == FP_NAN) return value; \
        if (kind == FP_INFINITE) return sign_value(value) ? (type)-1 : value; \
        if (value == (type)0) return value; \
        if (value < (type)-80) return (type)-1; \
        magnitude = value < (type)0 ? -value : value; \
        if (magnitude < (type)0.5) { \
            term = value; \
            sum = value; \
            for (iteration = 2; iteration <= 48; iteration++) { \
                term *= value / (type)iteration; \
                sum += term; \
            } \
            return sum; \
        } \
        return exp_value(value) - (type)1; \
    } \
    static type cplus_math_log_argument_##suffix(type value) { \
        int kind = classifier(value); \
        if (kind == FP_NAN) return value; \
        if (kind == FP_INFINITE) { \
            if (!sign_value(value)) return value; \
            errno = EDOM; \
            return (type)NAN; \
        } \
        if (kind == FP_ZERO) { \
            errno = ERANGE; \
            return (type)-HUGE_VALL; \
        } \
        if (sign_value(value)) { \
            errno = EDOM; \
            return (type)NAN; \
        } \
        return log_positive(value); \
    } \
    type log##suffix(type value) { \
        return cplus_math_log_argument_##suffix(value); \
    } \
    type log10##suffix(type value) { \
        if (classifier(value) == FP_NAN || classifier(value) == FP_ZERO || \
            classifier(value) == FP_INFINITE || sign_value(value)) \
            return cplus_math_log_argument_##suffix(value); \
        return log_positive(value) / (type)0x1.26bb1bbb5551582dd4adac5705a6p+1L; \
    } \
    type log2##suffix(type value) { \
        int exponent; \
        type fraction; \
        if (classifier(value) == FP_NAN || classifier(value) == FP_ZERO || \
            classifier(value) == FP_INFINITE || sign_value(value)) \
            return cplus_math_log_argument_##suffix(value); \
        fraction = frexp_value(value, &exponent); \
        if (fraction == (type)0.5) return (type)(exponent - 1); \
        return log_positive(value) / (type)0x1.62e42fefa39ef35793c7673007e6p-1L; \
    } \
    type log1p##suffix(type value) { \
        int kind = classifier(value); \
        int iteration; \
        type z; \
        type z_squared; \
        type term; \
        type sum; \
        type magnitude; \
        if (kind == FP_NAN) return value; \
        if (value == (type)-1) { \
            errno = ERANGE; \
            return (type)-HUGE_VALL; \
        } \
        if (value < (type)-1) { \
            errno = EDOM; \
            return (type)NAN; \
        } \
        if (kind == FP_INFINITE) return value; \
        if (value == (type)0) return value; \
        magnitude = value < (type)0 ? -value : value; \
        if (magnitude >= (type)0.5) return cplus_math_log_argument_##suffix((type)1 + value); \
        z = value / ((type)2 + value); \
        z_squared = z * z; \
        term = z; \
        sum = z; \
        for (iteration = 3; iteration <= 95; iteration += 2) { \
            term *= z_squared; \
            sum += term / (type)iteration; \
        } \
        return (type)2 * sum; \
    } \
    type logb##suffix(type value) { \
        int kind = classifier(value); \
        if (kind == FP_NAN) return value; \
        if (kind == FP_INFINITE) return (type)HUGE_VALL; \
        if (kind == FP_ZERO) { \
            errno = ERANGE; \
            return (type)-HUGE_VALL; \
        } \
        return (type)ilogb_value(value); \
    }

CPLUS_MATH_DEFINE_EXP_LOG_API(
    f, float, cplus_math_classify_float, cplus_math_sign_float, frexpf,
    cplus_math_truncate_float, ilogbf, cplus_math_log_positive_f, cplus_math_exp_f,
    cplus_math_scale_float, 127, -149)
CPLUS_MATH_DEFINE_EXP_LOG_API(
    , double, cplus_math_classify_double, cplus_math_sign_double, frexp,
    cplus_math_truncate_double, ilogb, cplus_math_log_positive_, cplus_math_exp_,
    cplus_math_scale_double, 1023, -1074)
#if CPLUS_LONG_DOUBLE_FORMAT == 1
CPLUS_MATH_DEFINE_EXP_LOG_API(
    l, long double, cplus_math_classify_long_double, cplus_math_sign_long_double, frexpl,
    cplus_math_truncate_long_double, ilogbl, cplus_math_log_positive_l, cplus_math_exp_l,
    cplus_math_scale_long_double, 1023, -1074)
#elif CPLUS_LONG_DOUBLE_FORMAT == 2
CPLUS_MATH_DEFINE_EXP_LOG_API(
    l, long double, cplus_math_classify_long_double, cplus_math_sign_long_double, frexpl,
    cplus_math_truncate_long_double, ilogbl, cplus_math_log_positive_l, cplus_math_exp_l,
    cplus_math_scale_long_double, 16383, -16445)
#elif CPLUS_LONG_DOUBLE_FORMAT == 3
CPLUS_MATH_DEFINE_EXP_LOG_API(
    l, long double, cplus_math_classify_long_double, cplus_math_sign_long_double, frexpl,
    cplus_math_truncate_long_double, ilogbl, cplus_math_log_positive_l, cplus_math_exp_l,
    cplus_math_scale_long_double, 16383, -16494)
#endif

#undef CPLUS_MATH_DEFINE_EXP_LOG_API

#define CPLUS_MATH_DEFINE_TRIG( \
    suffix, type, classifier, sign_value, absolute_value, sqrt_value, copy_sign, reduction_limit) \
    static type cplus_math_sin_series_##suffix(type value) { \
        int iteration; \
        type square = value * value; \
        type term = value; \
        type sum = value; \
        for (iteration = 1; iteration <= 16; iteration++) { \
            term *= -square / ((type)(2 * iteration) * (type)(2 * iteration + 1)); \
            sum += term; \
        } \
        return sum; \
    } \
    static type cplus_math_cos_series_##suffix(type value) { \
        int iteration; \
        type square = value * value; \
        type term = (type)1; \
        type sum = (type)1; \
        for (iteration = 1; iteration <= 16; iteration++) { \
            term *= -square / ((type)(2 * iteration - 1) * (type)(2 * iteration)); \
            sum += term; \
        } \
        return sum; \
    } \
    static void cplus_math_sincos_##suffix(type value, type* sine, type* cosine) { \
        int kind = classifier(value); \
        int quotient = 0; \
        int quadrant; \
        type reduced; \
        type small_sine; \
        type small_cosine; \
        if (kind == FP_NAN) { \
            *sine = value; \
            *cosine = value; \
            return; \
        } \
        if (kind == FP_INFINITE) { \
            errno = EDOM; \
            *sine = (type)NAN; \
            *cosine = (type)NAN; \
            return; \
        } \
        if (kind == FP_ZERO) { \
            *sine = value; \
            *cosine = (type)1; \
            return; \
        } \
        if (absolute_value(value) <= (type)(reduction_limit)) { \
            const type pio2_high = (type)1.57079632673412561417L; \
            const type pio2_low = (type)6.07710050630396597660e-11L; \
            const type pio2_tail = (type)2.02226624879595063154e-21L; \
            type ratio = value / (type)1.570796326794896619231321691639751442L; \
            type fraction; \
            quotient = (int)ratio; \
            fraction = ratio - (type)quotient; \
            if (fraction > (type)0.5 || \
                (fraction == (type)0.5 && quotient % 2 != 0)) quotient++; \
            else if (fraction < (type)-0.5 || \
                (fraction == (type)-0.5 && quotient % 2 != 0)) quotient--; \
            reduced = ((value - (type)quotient * pio2_high) - \
                (type)quotient * pio2_low) - (type)quotient * pio2_tail; \
        } else { \
            reduced = (type)remquol((long double)value, \
                0x1.921fb54442d18469898cc51701b8p+0L, &quotient); \
        } \
        quadrant = quotient % 4; \
        if (quadrant < 0) quadrant += 4; \
        small_sine = cplus_math_sin_series_##suffix(reduced); \
        small_cosine = cplus_math_cos_series_##suffix(reduced); \
        if (quadrant == 0) { \
            *sine = small_sine; \
            *cosine = small_cosine; \
        } else if (quadrant == 1) { \
            *sine = small_cosine; \
            *cosine = -small_sine; \
        } else if (quadrant == 2) { \
            *sine = -small_sine; \
            *cosine = -small_cosine; \
        } else { \
            *sine = -small_cosine; \
            *cosine = small_sine; \
        } \
    } \
    type sin##suffix(type value) { \
        type sine; \
        type cosine; \
        cplus_math_sincos_##suffix(value, &sine, &cosine); \
        return sine; \
    } \
    type cos##suffix(type value) { \
        type sine; \
        type cosine; \
        cplus_math_sincos_##suffix(value, &sine, &cosine); \
        return cosine; \
    } \
    type tan##suffix(type value) { \
        type sine; \
        type cosine; \
        cplus_math_sincos_##suffix(value, &sine, &cosine); \
        if (cosine == (type)0 && classifier(sine) != FP_NAN) { \
            errno = ERANGE; \
            return copy_sign((type)HUGE_VALL, sine); \
        } \
        return sine / cosine; \
    } \
    static type cplus_math_atan_series_##suffix(type value) { \
        int denominator; \
        type square = value * value; \
        type term = value; \
        type sum = value; \
        for (denominator = 3; denominator <= 129; denominator += 2) { \
            term *= -square; \
            sum += term / (type)denominator; \
        } \
        return sum; \
    } \
    static type cplus_math_atan_positive_##suffix(type value) { \
        const type pi_over_four = (type)0x1.921fb54442d18469898cc51701b8p-1L; \
        if (value > (type)2) \
            return (type)0x1.921fb54442d18469898cc51701b8p+0L - \
                cplus_math_atan_series_##suffix((type)1 / value); \
        if (value > (type)0.5) \
            return pi_over_four + cplus_math_atan_series_##suffix( \
                (value - (type)1) / (value + (type)1)); \
        return cplus_math_atan_series_##suffix(value); \
    } \
    type atan##suffix(type value) { \
        int kind = classifier(value); \
        type result; \
        if (kind == FP_NAN) return value; \
        if (kind == FP_INFINITE) \
            return copy_sign((type)0x1.921fb54442d18469898cc51701b8p+0L, value); \
        result = cplus_math_atan_positive_##suffix(absolute_value(value)); \
        return copy_sign(result, value); \
    } \
    type atan2##suffix(type ordinate, type abscissa) { \
        const type pi = (type)0x1.921fb54442d18469898cc51701b8p+1L; \
        const type pi_over_two = (type)0x1.921fb54442d18469898cc51701b8p+0L; \
        int ordinate_kind = classifier(ordinate); \
        int abscissa_kind = classifier(abscissa); \
        int ordinate_negative = sign_value(ordinate); \
        int abscissa_negative = sign_value(abscissa); \
        type ordinate_magnitude; \
        type abscissa_magnitude; \
        type angle; \
        if (ordinate_kind == FP_NAN || abscissa_kind == FP_NAN) \
            return ordinate_kind == FP_NAN ? ordinate : abscissa; \
        if (ordinate_kind == FP_INFINITE && abscissa_kind == FP_INFINITE) { \
            angle = abscissa_negative ? (type)3 * pi / (type)4 : pi / (type)4; \
            return copy_sign(angle, ordinate); \
        } \
        if (ordinate_kind == FP_INFINITE) return copy_sign(pi_over_two, ordinate); \
        if (abscissa_kind == FP_INFINITE) \
            return abscissa_negative ? copy_sign(pi, ordinate) : copy_sign((type)0, ordinate); \
        if (ordinate_kind == FP_ZERO) { \
            if (abscissa_negative) return copy_sign(pi, ordinate); \
            return ordinate; \
        } \
        if (abscissa_kind == FP_ZERO) return copy_sign(pi_over_two, ordinate); \
        ordinate_magnitude = absolute_value(ordinate); \
        abscissa_magnitude = absolute_value(abscissa); \
        if (ordinate_magnitude > abscissa_magnitude) \
            angle = pi_over_two - cplus_math_atan_positive_##suffix( \
                abscissa_magnitude / ordinate_magnitude); \
        else \
            angle = cplus_math_atan_positive_##suffix(ordinate_magnitude / abscissa_magnitude); \
        if (abscissa_negative) angle = pi - angle; \
        return ordinate_negative ? -angle : angle; \
    } \
    type asin##suffix(type value) { \
        int kind = classifier(value); \
        type magnitude; \
        if (kind == FP_NAN) return value; \
        magnitude = absolute_value(value); \
        if (magnitude > (type)1) { \
            errno = EDOM; \
            return (type)NAN; \
        } \
        if (value == (type)1) return (type)0x1.921fb54442d18469898cc51701b8p+0L; \
        if (value == (type)-1) return (type)-0x1.921fb54442d18469898cc51701b8p+0L; \
        if (kind == FP_ZERO) return value; \
        return atan2##suffix(value, sqrt_value(((type)1 - value) * ((type)1 + value))); \
    } \
    type acos##suffix(type value) { \
        int kind = classifier(value); \
        type magnitude; \
        if (kind == FP_NAN) return value; \
        magnitude = absolute_value(value); \
        if (magnitude > (type)1) { \
            errno = EDOM; \
            return (type)NAN; \
        } \
        if (value == (type)1) return (type)0; \
        if (value == (type)-1) return (type)0x1.921fb54442d18469898cc51701b8p+1L; \
        return atan2##suffix(sqrt_value(((type)1 - value) * ((type)1 + value)), value); \
    }

CPLUS_MATH_DEFINE_TRIG(f, float, cplus_math_classify_float, cplus_math_sign_float,
    fabsf, sqrtf, cplus_math_copysign_float, 512.0f)
CPLUS_MATH_DEFINE_TRIG(, double, cplus_math_classify_double, cplus_math_sign_double,
    fabs, sqrt, cplus_math_copysign_double, 0x1p20)
CPLUS_MATH_DEFINE_TRIG(l, long double, cplus_math_classify_long_double, cplus_math_sign_long_double,
    fabsl, sqrtl, cplus_math_copysign_long_double, 0x1p20L)

#undef CPLUS_MATH_DEFINE_TRIG

#define CPLUS_MATH_DEFINE_HYPERBOLIC( \
    suffix, type, classifier, sign_value, absolute_value, exp_value, \
    log_value, log1p_value, sqrt_value, copy_sign) \
    static type cplus_math_sinh_series_##suffix(type value) { \
        int iteration; \
        type square = value * value; \
        type term = value; \
        type sum = value; \
        for (iteration = 1; iteration <= 16; iteration++) { \
            term *= square / ((type)(2 * iteration) * (type)(2 * iteration + 1)); \
            sum += term; \
        } \
        return sum; \
    } \
    static type cplus_math_cosh_series_##suffix(type value) { \
        int iteration; \
        type square = value * value; \
        type term = (type)1; \
        type sum = (type)1; \
        for (iteration = 1; iteration <= 16; iteration++) { \
            term *= square / ((type)(2 * iteration - 1) * (type)(2 * iteration)); \
            sum += term; \
        } \
        return sum; \
    } \
    type sinh##suffix(type value) { \
        int kind = classifier(value); \
        type magnitude; \
        type result; \
        if (kind == FP_NAN || kind == FP_INFINITE || kind == FP_ZERO) return value; \
        magnitude = absolute_value(value); \
        if (magnitude < (type)0.5) return cplus_math_sinh_series_##suffix(value); \
        result = (exp_value(magnitude) - exp_value(-magnitude)) * (type)0.5; \
        if (classifier(result) == FP_INFINITE) errno = ERANGE; \
        return copy_sign(result, value); \
    } \
    type cosh##suffix(type value) { \
        int kind = classifier(value); \
        type magnitude; \
        type result; \
        if (kind == FP_NAN) return value; \
        if (kind == FP_INFINITE) return (type)HUGE_VALL; \
        magnitude = absolute_value(value); \
        if (magnitude < (type)0.5) return cplus_math_cosh_series_##suffix(value); \
        result = (exp_value(magnitude) + exp_value(-magnitude)) * (type)0.5; \
        if (classifier(result) == FP_INFINITE) errno = ERANGE; \
        return result; \
    } \
    type tanh##suffix(type value) { \
        int kind = classifier(value); \
        type magnitude; \
        type exponential; \
        type result; \
        if (kind == FP_NAN) return value; \
        if (kind == FP_INFINITE) return copy_sign((type)1, value); \
        if (kind == FP_ZERO) return value; \
        magnitude = absolute_value(value); \
        if (magnitude > (type)40) return copy_sign((type)1, value); \
        exponential = exp_value((type)-2 * magnitude); \
        result = ((type)1 - exponential) / ((type)1 + exponential); \
        return copy_sign(result, value); \
    } \
    type asinh##suffix(type value) { \
        int kind = classifier(value); \
        type magnitude; \
        type result; \
        if (kind == FP_NAN || kind == FP_INFINITE || kind == FP_ZERO) return value; \
        magnitude = absolute_value(value); \
        if (magnitude > (type)1) { \
            type reciprocal = (type)1 / magnitude; \
            result = log_value(magnitude) + log1p_value( \
                sqrt_value((type)1 + reciprocal * reciprocal)); \
        } else { \
            type square = magnitude * magnitude; \
            result = log1p_value(magnitude + square / \
                ((type)1 + sqrt_value((type)1 + square))); \
        } \
        return copy_sign(result, value); \
    } \
    type acosh##suffix(type value) { \
        int kind = classifier(value); \
        if (kind == FP_NAN) return value; \
        if (sign_value(value) && kind == FP_INFINITE) { \
            errno = EDOM; \
            return (type)NAN; \
        } \
        if (kind == FP_INFINITE) return value; \
        if (value < (type)1) { \
            errno = EDOM; \
            return (type)NAN; \
        } \
        if (value == (type)1) return (type)0; \
        if (value > (type)2) { \
            type reciprocal = (type)1 / value; \
            return log_value(value) + log1p_value( \
                sqrt_value((type)1 - reciprocal * reciprocal)); \
        } \
        { \
            type difference = value - (type)1; \
            return log1p_value(difference + sqrt_value(difference * (value + (type)1))); \
        } \
    } \
    type atanh##suffix(type value) { \
        int kind = classifier(value); \
        type magnitude; \
        if (kind == FP_NAN) return value; \
        if (kind == FP_ZERO) return value; \
        magnitude = absolute_value(value); \
        if (magnitude > (type)1) { \
            errno = EDOM; \
            return (type)NAN; \
        } \
        if (magnitude == (type)1) { \
            errno = ERANGE; \
            return copy_sign((type)HUGE_VALL, value); \
        } \
        if (magnitude < (type)0.5) { \
            int denominator; \
            type term = value; \
            type sum = value; \
            type square = value * value; \
            for (denominator = 3; denominator <= 95; denominator += 2) { \
                term *= square; \
                sum += term / (type)denominator; \
            } \
            return sum; \
        } \
        return ((type)0.5) * (log1p_value(value) - log1p_value(-value)); \
    }

CPLUS_MATH_DEFINE_HYPERBOLIC(
    f, float, cplus_math_classify_float, cplus_math_sign_float, fabsf,
    expf, logf, log1pf, sqrtf, cplus_math_copysign_float)
CPLUS_MATH_DEFINE_HYPERBOLIC(
    , double, cplus_math_classify_double, cplus_math_sign_double, fabs,
    exp, log, log1p, sqrt, cplus_math_copysign_double)
CPLUS_MATH_DEFINE_HYPERBOLIC(
    l, long double, cplus_math_classify_long_double, cplus_math_sign_long_double, fabsl,
    expl, logl, log1pl, sqrtl, cplus_math_copysign_long_double)

#undef CPLUS_MATH_DEFINE_HYPERBOLIC

#define CPLUS_MATH_DEFINE_ERROR_GAMMA( \
    suffix, type, classifier, sign_value, absolute_value, truncate_value, is_odd, \
    exp_value, log_value, sin_value, copy_sign) \
    static type cplus_math_erf_positive_##suffix(type value) { \
        int iteration; \
        type square = value * value; \
        type term = value; \
        type sum = value; \
        const type two_over_sqrt_pi = (type)1.128379167095512573896158903121545172L; \
        for (iteration = 1; iteration <= 128; iteration++) { \
            type previous = sum; \
            term *= -square / (type)iteration; \
            sum += term / (type)(2 * iteration + 1); \
            if (sum == previous) break; \
        } \
        return two_over_sqrt_pi * sum; \
    } \
    static type cplus_math_erfc_positive_##suffix(type value) { \
        int iteration; \
        type square = value * value; \
        type b; \
        type c = (type)HUGE_VALL; \
        type d; \
        type fraction; \
        type log_factor; \
        if (square == (type)0) return (type)1; \
        if (classifier(square) == FP_INFINITE) { \
            errno = ERANGE; \
            return (type)0; \
        } \
        b = square + (type)0.5; \
        d = (type)1 / b; \
        fraction = d; \
        for (iteration = 1; iteration <= 256; iteration++) { \
            type a = -(type)iteration * ((type)iteration - (type)0.5); \
            type delta; \
            b += (type)2; \
            d = a * d + b; \
            if (absolute_value(d) < (type)1e-30L) d = copy_sign((type)1e-30L, d); \
            c = b + a / c; \
            if (absolute_value(c) < (type)1e-30L) c = copy_sign((type)1e-30L, c); \
            d = (type)1 / d; \
            delta = d * c; \
            { \
                type next_fraction = fraction * delta; \
                if (next_fraction == fraction) break; \
                fraction = next_fraction; \
            } \
        } \
        log_factor = -square + log_value(value) - (type)0.572364942924700087071713675676529356L; \
        return exp_value(log_factor) * fraction; \
    } \
    type erf##suffix(type value) { \
        int kind = classifier(value); \
        type magnitude; \
        if (kind == FP_NAN) return value; \
        if (kind == FP_INFINITE) return copy_sign((type)1, value); \
        if (kind == FP_ZERO) return value; \
        magnitude = absolute_value(value); \
        if (magnitude >= (type)10) return copy_sign((type)1, value); \
        if (magnitude < (type)1.5) \
            return copy_sign(cplus_math_erf_positive_##suffix(magnitude), value); \
        return copy_sign((type)1 - cplus_math_erfc_positive_##suffix(magnitude), value); \
    } \
    type erfc##suffix(type value) { \
        int kind = classifier(value); \
        type magnitude; \
        type result; \
        if (kind == FP_NAN) return value; \
        if (kind == FP_INFINITE) return sign_value(value) ? (type)2 : (type)0; \
        magnitude = absolute_value(value); \
        if (sign_value(value) && magnitude >= (type)10) return (type)2; \
        if (magnitude < (type)1.5) result = (type)1 - cplus_math_erf_positive_##suffix(magnitude); \
        else result = cplus_math_erfc_positive_##suffix(magnitude); \
        return sign_value(value) ? (type)2 - result : result; \
    } \
    static type cplus_math_lgamma_positive_##suffix(type value) { \
        static const type coefficients[9] = { \
            (type)0.99999999999980993227684700473478L, \
            (type)676.520368121885098567009190444019L, \
            (type)-1259.13921672240287047156078755283L, \
            (type)771.3234287776530788486528258894L, \
            (type)-176.61502916214059906584551354L, \
            (type)12.507343278686904814458936853L, \
            (type)-0.13857109526572011689554707L, \
            (type)0.000009984369578019570859563L, \
            (type)0.000000150563273514931155834L \
        }; \
        type z; \
        type sum = coefficients[0]; \
        type t; \
        type result; \
        int index; \
        if (value < (type)0.5) \
            return cplus_math_lgamma_positive_##suffix(value + (type)1) - log_value(value); \
        z = value - (type)1; \
        t = z + (type)7.5; \
        for (index = 1; index < 9; index++) sum += coefficients[index] / (z + (type)index); \
        result = (type)0.91893853320467274178032973640561764L + \
            (z + (type)0.5) * log_value(t) - t + log_value(sum); \
        if (classifier(result) == FP_INFINITE) errno = ERANGE; \
        return result; \
    } \
    static type cplus_math_sin_pi_##suffix(type value) { \
        type magnitude = absolute_value(value); \
        type integer = truncate_value(magnitude); \
        type fraction = magnitude - integer; \
        type result; \
        if (fraction > (type)0.5) fraction = (type)1 - fraction; \
        result = sin_value((type)0x1.921fb54442d18469898cc51701b8p+1L * fraction); \
        if (is_odd(integer)) result = -result; \
        return sign_value(value) ? -result : result; \
    } \
    type lgamma##suffix(type value) { \
        int kind = classifier(value); \
        type magnitude; \
        if (kind == FP_NAN) return value; \
        if (kind == FP_INFINITE) { \
            if (!sign_value(value)) return value; \
            errno = EDOM; \
            return (type)NAN; \
        } \
        if (kind == FP_ZERO) { errno = ERANGE; return (type)HUGE_VALL; } \
        if (value == (type)1 || value == (type)2) return (type)0; \
        if (value > (type)0) return cplus_math_lgamma_positive_##suffix(value); \
        magnitude = absolute_value(value); \
        if (truncate_value(magnitude) == magnitude) { errno = ERANGE; return (type)HUGE_VALL; } \
        return (type)1.14472988584940017414342735135305871L - \
            log_value(absolute_value(cplus_math_sin_pi_##suffix(value))) - \
            cplus_math_lgamma_positive_##suffix((type)1 - value); \
    } \
    type tgamma##suffix(type value) { \
        int kind = classifier(value); \
        type magnitude; \
        type log_magnitude; \
        type result; \
        if (kind == FP_NAN) return value; \
        if (kind == FP_INFINITE) { \
            if (!sign_value(value)) return value; \
            errno = EDOM; \
            return (type)NAN; \
        } \
        if (kind == FP_ZERO) { \
            errno = ERANGE; \
            return copy_sign((type)HUGE_VALL, value); \
        } \
        if (value == (type)1 || value == (type)2) return (type)1; \
        if (value > (type)0) log_magnitude = cplus_math_lgamma_positive_##suffix(value); \
        else { \
            magnitude = absolute_value(value); \
            if (truncate_value(magnitude) == magnitude) { errno = EDOM; return (type)NAN; } \
            log_magnitude = (type)1.14472988584940017414342735135305871L - \
                log_value(absolute_value(cplus_math_sin_pi_##suffix(value))) - \
                cplus_math_lgamma_positive_##suffix((type)1 - value); \
        } \
        result = exp_value(log_magnitude); \
        if (value < (type)0) result = copy_sign(result, cplus_math_sin_pi_##suffix(value)); \
        return result; \
    }

CPLUS_MATH_DEFINE_ERROR_GAMMA(
    f, float, cplus_math_classify_float, cplus_math_sign_float, fabsf,
    cplus_math_truncate_float, cplus_math_is_odd_float, expf, logf, sinf,
    cplus_math_copysign_float)
CPLUS_MATH_DEFINE_ERROR_GAMMA(
    , double, cplus_math_classify_double, cplus_math_sign_double, fabs,
    cplus_math_truncate_double, cplus_math_is_odd_double, exp, log, sin,
    cplus_math_copysign_double)
CPLUS_MATH_DEFINE_ERROR_GAMMA(
    l, long double, cplus_math_classify_long_double, cplus_math_sign_long_double, fabsl,
    cplus_math_truncate_long_double, cplus_math_is_odd_long_double, expl, logl, sinl,
    cplus_math_copysign_long_double)

#undef CPLUS_MATH_DEFINE_ERROR_GAMMA

#define CPLUS_MATH_BIG_WORD_COUNT 32
typedef struct cplus_math_big_uint {
    unsigned int words[CPLUS_MATH_BIG_WORD_COUNT];
} cplus_math_big_uint;

static unsigned int cplus_math_big_bit(const cplus_math_big_uint* value, int bit) {
    if (bit < 0 || bit >= CPLUS_MATH_BIG_WORD_COUNT * 32) return 0;
    return (value->words[(unsigned int)bit / 32] >> ((unsigned int)bit % 32)) & 1u;
}

static unsigned int cplus_math_big_bit_length(const cplus_math_big_uint* value) {
    int word;
    for (word = CPLUS_MATH_BIG_WORD_COUNT - 1; word >= 0; word--) {
        unsigned int current = value->words[word];
        unsigned int count = 0;
        if (current == 0) continue;
        while (current != 0) {
            current >>= 1;
            count++;
        }
        return (unsigned int)word * 32 + count;
    }
    return 0;
}

static int cplus_math_big_compare(
    const cplus_math_big_uint* left, const cplus_math_big_uint* right) {
    int word;
    for (word = CPLUS_MATH_BIG_WORD_COUNT - 1; word >= 0; word--) {
        if (left->words[word] > right->words[word]) return 1;
        if (left->words[word] < right->words[word]) return -1;
    }
    return 0;
}

static void cplus_math_big_add(
    cplus_math_big_uint* result, const cplus_math_big_uint* left,
    const cplus_math_big_uint* right) {
    unsigned int word;
    unsigned long long carry = 0;
    for (word = 0; word < CPLUS_MATH_BIG_WORD_COUNT; word++) {
        unsigned long long sum = (unsigned long long)left->words[word] +
            (unsigned long long)right->words[word] + carry;
        result->words[word] = (unsigned int)sum;
        carry = sum >> 32;
    }
}

static void cplus_math_big_subtract(
    cplus_math_big_uint* result, const cplus_math_big_uint* left,
    const cplus_math_big_uint* right) {
    unsigned int word;
    unsigned long long borrow = 0;
    for (word = 0; word < CPLUS_MATH_BIG_WORD_COUNT; word++) {
        unsigned long long minuend = left->words[word];
        unsigned long long subtrahend = (unsigned long long)right->words[word] + borrow;
        result->words[word] = (unsigned int)(minuend - subtrahend);
        borrow = minuend < subtrahend;
    }
}

static void cplus_math_big_shift_left(
    cplus_math_big_uint* result, const unsigned int* source,
    unsigned int source_words, int shift) {
    unsigned int index;
    unsigned int word_shift = (unsigned int)shift / 32;
    unsigned int bit_shift = (unsigned int)shift % 32;
    unsigned long long carry = 0;
    for (index = 0; index < source_words; index++) {
        unsigned long long shifted = ((unsigned long long)source[index] << bit_shift) | carry;
        unsigned int target = index + word_shift;
        if (target < CPLUS_MATH_BIG_WORD_COUNT) result->words[target] = (unsigned int)shifted;
        carry = shifted >> 32;
    }
    if (carry != 0 && source_words + word_shift < CPLUS_MATH_BIG_WORD_COUNT)
        result->words[source_words + word_shift] = (unsigned int)carry;
}

static void cplus_math_big_shift_right(
    cplus_math_big_uint* result, const cplus_math_big_uint* source, int shift) {
    unsigned int bit;
    if (shift < CPLUS_MATH_BIG_WORD_COUNT * 32) {
        for (bit = (unsigned int)shift; bit < CPLUS_MATH_BIG_WORD_COUNT * 32; bit++) {
            if (cplus_math_big_bit(source, (int)bit) != 0) {
                unsigned int target = bit - (unsigned int)shift;
                result->words[target / 32] |= 1u << (target % 32);
            }
        }
    }
}

static void cplus_math_big_increment(cplus_math_big_uint* value) {
    unsigned int word;
    for (word = 0; word < CPLUS_MATH_BIG_WORD_COUNT; word++) {
        value->words[word]++;
        if (value->words[word] != 0) return;
    }
}

static int cplus_math_big_any_below(const cplus_math_big_uint* value, int bit_limit) {
    int bit;
    if (bit_limit > CPLUS_MATH_BIG_WORD_COUNT * 32)
        bit_limit = CPLUS_MATH_BIG_WORD_COUNT * 32;
    for (bit = 0; bit < bit_limit; bit++)
        if (cplus_math_big_bit(value, bit) != 0) return 1;
    return 0;
}

static int cplus_math_big_round_right(
    cplus_math_big_uint* result, const cplus_math_big_uint* source, int shift) {
    int guard;
    int sticky;
    int inexact;
    cplus_math_big_shift_right(result, source, shift);
    if (shift <= 0) return 0;
    guard = (int)cplus_math_big_bit(source, shift - 1);
    sticky = cplus_math_big_any_below(source, shift - 1);
    inexact = guard || sticky;
    if (guard && (sticky || cplus_math_big_bit(source, shift) != 0))
        cplus_math_big_increment(result);
    return inexact;
}

static void cplus_math_multiply_significands(
    unsigned int result[8], const unsigned int left[4], const unsigned int right[4]) {
    unsigned int left_word;
    for (left_word = 0; left_word < 4; left_word++) {
        unsigned int right_word;
        unsigned long long carry = 0;
        for (right_word = 0; right_word < 4; right_word++) {
            unsigned int target = left_word + right_word;
            unsigned long long product = (unsigned long long)left[left_word] * right[right_word] +
                result[target] + carry;
            result[target] = (unsigned int)product;
            carry = product >> 32;
        }
        for (right_word = left_word + 4; carry != 0 && right_word < 8; right_word++) {
            unsigned long long sum = (unsigned long long)result[right_word] + carry;
            result[right_word] = (unsigned int)sum;
            carry = sum >> 32;
        }
    }
}

static void cplus_math_extract_significand(
    unsigned int result[4], long double magnitude, int precision,
    int* exponent, int* bit_length) {
    int index;
    long double fraction = frexpl(magnitude, exponent);
    for (index = 0; index < precision; index++) {
        unsigned int bit_position = (unsigned int)(precision - index - 1);
        fraction *= 2.0L;
        if (fraction >= 1.0L) {
            result[bit_position / 32] |= 1u << (bit_position % 32);
            fraction -= 1.0L;
        }
    }
    *exponent -= precision;
    {
        cplus_math_big_uint temporary = {{0}};
        unsigned int word;
        for (word = 0; word < 4; word++) temporary.words[word] = result[word];
        *bit_length = (int)cplus_math_big_bit_length(&temporary);
    }
}

static int cplus_math_fma_round_exact(
    const unsigned int x[4], const unsigned int y[4], const unsigned int z[4],
    int x_exponent, int y_exponent, int z_exponent, int precision,
    int minimum_normal_exponent, int maximum_exponent, int product_negative,
    int z_negative, unsigned int rounded[4], int* output_exponent,
    int* output_negative) {
    unsigned int product_words[8] = {0};
    int product_exponent = x_exponent + y_exponent;
    int product_bits;
    int z_bits;
    int base_exponent;
    int product_top;
    int z_top;
    cplus_math_big_uint product = {{0}};
    cplus_math_big_uint addend = {{0}};
    cplus_math_big_uint magnitude = {{0}};
    cplus_math_big_uint temporary = {{0}};
    int magnitude_negative;
    int magnitude_bits;
    int top_exponent;
    int inexact = 0;
    unsigned int word;

    cplus_math_multiply_significands(product_words, x, y);
    {
        cplus_math_big_uint product_view = {{0}};
        for (word = 0; word < 8; word++) product_view.words[word] = product_words[word];
        product_bits = (int)cplus_math_big_bit_length(&product_view);
    }
    {
        cplus_math_big_uint z_view = {{0}};
        for (word = 0; word < 4; word++) z_view.words[word] = z[word];
        z_bits = (int)cplus_math_big_bit_length(&z_view);
    }
    product_top = product_exponent + product_bits - 1;
    z_top = z_bits == 0 ? product_top : z_exponent + z_bits - 1;
    if (z_bits == 0) {
        base_exponent = product_exponent;
        cplus_math_big_shift_left(&product, product_words, 8, 0);
        magnitude = product;
        magnitude_negative = product_negative;
    } else {
        int top = product_top > z_top ? product_top : z_top;
        int bottom = product_exponent < z_exponent ? product_exponent : z_exponent;
        int span = top - bottom + 1;
        if (span > CPLUS_MATH_BIG_WORD_COUNT * 32) {
            int product_dominates = product_top > z_top;
            const unsigned int* dominant_words = product_dominates ? product_words : z;
            unsigned int dominant_count = product_dominates ? 8u : 4u;
            int dominant_negative = product_dominates ? product_negative : z_negative;
            base_exponent = (product_dominates ? product_exponent : z_exponent) - 256;
            cplus_math_big_shift_left(&magnitude, dominant_words, dominant_count, 256);
            temporary.words[0] = 1;
            if (dominant_negative == (product_dominates ? z_negative : product_negative))
                cplus_math_big_add(&magnitude, &magnitude, &temporary);
            else
                cplus_math_big_subtract(&magnitude, &magnitude, &temporary);
            magnitude_negative = dominant_negative;
        } else {
            base_exponent = product_exponent < z_exponent ? product_exponent : z_exponent;
            cplus_math_big_shift_left(&product, product_words, 8, product_exponent - base_exponent);
            cplus_math_big_shift_left(&addend, z, 4, z_exponent - base_exponent);
            if (product_negative == z_negative) {
                cplus_math_big_add(&magnitude, &product, &addend);
                magnitude_negative = product_negative;
            } else {
                int comparison = cplus_math_big_compare(&product, &addend);
                if (comparison == 0) {
                    *output_exponent = 0;
                    *output_negative = 0;
                    for (word = 0; word < 4; word++) rounded[word] = 0;
                    return 0;
                }
                if (comparison > 0) {
                    cplus_math_big_subtract(&magnitude, &product, &addend);
                    magnitude_negative = product_negative;
                } else {
                    cplus_math_big_subtract(&magnitude, &addend, &product);
                    magnitude_negative = z_negative;
                }
            }
        }
    }
    magnitude_bits = (int)cplus_math_big_bit_length(&magnitude);
    if (magnitude_bits == 0) {
        *output_exponent = 0;
        *output_negative = 0;
        for (word = 0; word < 4; word++) rounded[word] = 0;
        return 0;
    }
    top_exponent = base_exponent + magnitude_bits - 1;
    *output_negative = magnitude_negative;
    if (top_exponent >= minimum_normal_exponent) {
        int shift = magnitude_bits - precision;
        cplus_math_big_uint result = {{0}};
        if (shift > 0) inexact = cplus_math_big_round_right(&result, &magnitude, shift);
        else cplus_math_big_shift_left(&result, magnitude.words, CPLUS_MATH_BIG_WORD_COUNT, -shift);
        *output_exponent = base_exponent + shift;
        if ((int)cplus_math_big_bit_length(&result) > precision) {
            cplus_math_big_uint shifted = {{0}};
            cplus_math_big_shift_right(&shifted, &result, 1);
            result = shifted;
            (*output_exponent)++;
        }
        if (*output_exponent + precision - 1 > maximum_exponent) {
            errno = ERANGE;
            return 1;
        }
        for (word = 0; word < 4; word++) rounded[word] = result.words[word];
    } else {
        int subnormal_exponent = minimum_normal_exponent - precision + 1;
        int shift = subnormal_exponent - base_exponent;
        cplus_math_big_uint result = {{0}};
        if (shift > 0) inexact = cplus_math_big_round_right(&result, &magnitude, shift);
        else cplus_math_big_shift_left(&result, magnitude.words, CPLUS_MATH_BIG_WORD_COUNT, -shift);
        *output_exponent = subnormal_exponent;
        if (inexact && (int)cplus_math_big_bit_length(&result) < precision) errno = ERANGE;
        for (word = 0; word < 4; word++) rounded[word] = result.words[word];
    }
    *output_negative = magnitude_negative;
    return 0;
}

#define CPLUS_MATH_DEFINE_FMA( \
    suffix, type, classifier, sign_value, absolute_value, frexp_value, scale_value, \
    precision, minimum_exponent, maximum_exponent) \
    type fma##suffix(type left, type right, type addend) { \
        int left_kind = classifier(left); \
        int right_kind = classifier(right); \
        int addend_kind = classifier(addend); \
        int product_negative = sign_value(left) != sign_value(right); \
        unsigned int left_significand[4] = {0}; \
        unsigned int right_significand[4] = {0}; \
        unsigned int addend_significand[4] = {0}; \
        unsigned int rounded[4] = {0}; \
        int left_exponent = 0; \
        int right_exponent = 0; \
        int addend_exponent = 0; \
        int left_bits = 0; \
        int right_bits = 0; \
        int addend_bits = 0; \
        int result_exponent = 0; \
        int result_negative = 0; \
        int result_kind; \
        int rounded_zero = 1; \
        type result = (type)0; \
        unsigned int bit; \
        if (left_kind == FP_NAN || right_kind == FP_NAN || addend_kind == FP_NAN) \
            return left_kind == FP_NAN ? left : (right_kind == FP_NAN ? right : addend); \
        if (left_kind == FP_INFINITE || right_kind == FP_INFINITE) { \
            if ((left_kind == FP_INFINITE && right_kind == FP_ZERO) || \
                (right_kind == FP_INFINITE && left_kind == FP_ZERO)) { errno = EDOM; return (type)NAN; } \
            if (addend_kind == FP_INFINITE && sign_value(addend) != product_negative) { \
                errno = EDOM; return (type)NAN; \
            } \
            return (product_negative ? (type)-HUGE_VALL : (type)HUGE_VALL); \
        } \
        if (addend_kind == FP_INFINITE) return addend; \
        if (left_kind == FP_ZERO || right_kind == FP_ZERO) { \
            if (addend_kind != FP_ZERO) return addend; \
            return product_negative && sign_value(addend) ? (type)-0.0 : (type)0.0; \
        } \
        cplus_math_extract_significand(left_significand, (long double)absolute_value(left), \
            (precision), &left_exponent, &left_bits); \
        cplus_math_extract_significand(right_significand, (long double)absolute_value(right), \
            (precision), &right_exponent, &right_bits); \
        if (addend_kind != FP_ZERO) \
            cplus_math_extract_significand(addend_significand, (long double)absolute_value(addend), \
                (precision), &addend_exponent, &addend_bits); \
        result_kind = cplus_math_fma_round_exact(left_significand, right_significand, \
            addend_significand, left_exponent, right_exponent, addend_exponent, (precision), \
            (minimum_exponent), (maximum_exponent), product_negative, sign_value(addend), \
            rounded, &result_exponent, &result_negative); \
        if (result_kind != 0) return result_negative ? (type)-HUGE_VALL : (type)HUGE_VALL; \
        for (bit = 0; bit < 4u; bit++) if (rounded[bit] != 0) rounded_zero = 0; \
        for (bit = 4u * 32u; bit > 0; bit--) \
            result = result * (type)2 + (type)((rounded[(bit - 1) / 32] >> ((bit - 1) % 32)) & 1u); \
        if (rounded_zero) return result_negative ? (type)-0.0 : (type)0.0; \
        result = scale_value(result, (long)result_exponent); \
        return result_negative ? -result : result; \
    }

CPLUS_MATH_DEFINE_FMA(f, float, cplus_math_classify_float, cplus_math_sign_float,
    fabsf, frexpf, cplus_math_scale_float, 24, -126, 127)
CPLUS_MATH_DEFINE_FMA(, double, cplus_math_classify_double, cplus_math_sign_double,
    fabs, frexp, cplus_math_scale_double, 53, -1022, 1023)
#if CPLUS_LONG_DOUBLE_FORMAT == 1
CPLUS_MATH_DEFINE_FMA(l, long double, cplus_math_classify_long_double, cplus_math_sign_long_double,
    fabsl, frexpl, cplus_math_scale_long_double, 53, -1022, 1023)
#elif CPLUS_LONG_DOUBLE_FORMAT == 2
CPLUS_MATH_DEFINE_FMA(l, long double, cplus_math_classify_long_double, cplus_math_sign_long_double,
    fabsl, frexpl, cplus_math_scale_long_double, 64, -16382, 16383)
#elif CPLUS_LONG_DOUBLE_FORMAT == 3
CPLUS_MATH_DEFINE_FMA(l, long double, cplus_math_classify_long_double, cplus_math_sign_long_double,
    fabsl, frexpl, cplus_math_scale_long_double, 113, -16382, 16383)
#endif

#undef CPLUS_MATH_DEFINE_FMA

#define CPLUS_MATH_DEFINE_EXTREMA(suffix, type, classifier, sign_value) \
    type fdim##suffix(type left, type right) { \
        int left_kind = classifier(left); \
        int right_kind = classifier(right); \
        type result; \
        if (left_kind == FP_NAN) return left; \
        if (right_kind == FP_NAN) return right; \
        if (!(left > right)) return (type)0; \
        result = left - right; \
        if (classifier(result) == FP_INFINITE && left_kind != FP_INFINITE && right_kind != FP_INFINITE) \
            errno = ERANGE; \
        return result; \
    } \
    type fmax##suffix(type left, type right) { \
        int left_kind = classifier(left); \
        int right_kind = classifier(right); \
        if (left_kind == FP_NAN) return right_kind == FP_NAN ? left : right; \
        if (right_kind == FP_NAN) return left; \
        if (left == right) { \
            if (left == (type)0) return sign_value(left) && sign_value(right) ? left : (type)0; \
            return left; \
        } \
        return left > right ? left : right; \
    } \
    type fmin##suffix(type left, type right) { \
        int left_kind = classifier(left); \
        int right_kind = classifier(right); \
        if (left_kind == FP_NAN) return right_kind == FP_NAN ? left : right; \
        if (right_kind == FP_NAN) return left; \
        if (left == right) { \
            if (left == (type)0) return sign_value(left) || sign_value(right) ? (type)-0.0 : left; \
            return left; \
        } \
        return left < right ? left : right; \
    }

CPLUS_MATH_DEFINE_EXTREMA(f, float, cplus_math_classify_float, cplus_math_sign_float)
CPLUS_MATH_DEFINE_EXTREMA(, double, cplus_math_classify_double, cplus_math_sign_double)
CPLUS_MATH_DEFINE_EXTREMA(l, long double, cplus_math_classify_long_double, cplus_math_sign_long_double)

#undef CPLUS_MATH_DEFINE_EXTREMA
#undef CPLUS_MATH_BIG_WORD_COUNT

static void cplus_math_increment_magnitude(unsigned char* bytes, unsigned int sign_byte) {
    unsigned int index;
    for (index = 0; index < sign_byte; index++) {
        bytes[index]++;
        if (bytes[index] != 0) return;
    }
    bytes[sign_byte] = (unsigned char)((bytes[sign_byte] & 0x80) | ((bytes[sign_byte] + 1) & 0x7f));
}

static void cplus_math_decrement_magnitude(unsigned char* bytes, unsigned int sign_byte) {
    unsigned int index;
    for (index = 0; index < sign_byte; index++) {
        if (bytes[index] != 0) {
            bytes[index]--;
            return;
        }
        bytes[index] = 0xff;
    }
    bytes[sign_byte] = (unsigned char)((bytes[sign_byte] & 0x80) | ((bytes[sign_byte] - 1) & 0x7f));
}

static void cplus_math_make_smallest_subnormal(unsigned char* bytes, unsigned int sign_byte, int negative) {
    unsigned int index;
    for (index = 0; index < sign_byte; index++) bytes[index] = 0;
    bytes[0] = 1;
    bytes[sign_byte] = negative ? 0x80 : 0;
}

#if CPLUS_LONG_DOUBLE_FORMAT == 2
static unsigned int cplus_math_x87_exponent(const unsigned char* bytes) {
    return ((unsigned int)(bytes[9] & 0x7f) << 8) | bytes[8];
}

static void cplus_math_set_x87_exponent(unsigned char* bytes, unsigned int exponent) {
    bytes[8] = (unsigned char)(exponent & 0xff);
    bytes[9] = (unsigned char)((bytes[9] & 0x80) | ((exponent >> 8) & 0x7f));
}

static int cplus_math_x87_significand_is_integer_bit(const unsigned char* bytes) {
    unsigned int index;
    if (bytes[7] != 0x80) return 0;
    for (index = 0; index < 7; index++) if (bytes[index] != 0) return 0;
    return 1;
}

static void cplus_math_step_x87(unsigned char* bytes, int increase_magnitude) {
    unsigned int exponent = cplus_math_x87_exponent(bytes);
    unsigned int index;
    if (increase_magnitude) {
        for (index = 0; index < 8; index++) {
            bytes[index]++;
            if (bytes[index] != 0) break;
        }
        if (index == 8) {
            exponent++;
            for (index = 0; index < 7; index++) bytes[index] = 0;
            bytes[7] = 0x80;
            cplus_math_set_x87_exponent(bytes, exponent);
        } else if (exponent == 0 && (bytes[7] & 0x80) != 0) {
            cplus_math_set_x87_exponent(bytes, 1);
        }
        return;
    }

    if (exponent == 0x7fff) {
        for (index = 0; index < 8; index++) bytes[index] = 0xff;
        cplus_math_set_x87_exponent(bytes, 0x7ffe);
        return;
    }
    if (cplus_math_x87_significand_is_integer_bit(bytes)) {
        if (exponent <= 1) {
            exponent = 0;
            for (index = 0; index < 7; index++) bytes[index] = 0xff;
            bytes[7] = 0x7f;
        } else {
            exponent--;
            for (index = 0; index < 8; index++) bytes[index] = 0xff;
        }
        cplus_math_set_x87_exponent(bytes, exponent);
        return;
    }
    for (index = 0; index < 8; index++) {
        if (bytes[index] != 0) {
            bytes[index]--;
            return;
        }
        bytes[index] = 0xff;
    }
}
#endif

static float cplus_math_nextafter_float(float value, long double direction) {
    int value_kind = cplus_math_classify_float(value);
    int direction_kind = cplus_math_classify_long_double(direction);
    int negative;
    int increase_magnitude;
    if (value_kind == FP_NAN) return value;
    if (direction_kind == FP_NAN) return (float)direction;
    if ((long double)value == direction) return (float)direction;
    if (value_kind == FP_ZERO) {
        unsigned char* bytes = (unsigned char*)&value;
        cplus_math_make_smallest_subnormal(bytes, 3, cplus_math_sign_long_double(direction));
        return value;
    }
    negative = cplus_math_sign_float(value);
    increase_magnitude = (((long double)value < direction) != negative);
    if (increase_magnitude) cplus_math_increment_magnitude((unsigned char*)&value, 3);
    else cplus_math_decrement_magnitude((unsigned char*)&value, 3);
    if (value_kind != FP_INFINITE && cplus_math_classify_float(value) == FP_INFINITE) errno = ERANGE;
    return value;
}

static double cplus_math_nextafter_double(double value, long double direction) {
    int value_kind = cplus_math_classify_double(value);
    int direction_kind = cplus_math_classify_long_double(direction);
    int negative;
    int increase_magnitude;
    if (value_kind == FP_NAN) return value;
    if (direction_kind == FP_NAN) return (double)direction;
    if ((long double)value == direction) return (double)direction;
    if (value_kind == FP_ZERO) {
        unsigned char* bytes = (unsigned char*)&value;
        cplus_math_make_smallest_subnormal(bytes, 7, cplus_math_sign_long_double(direction));
        return value;
    }
    negative = cplus_math_sign_double(value);
    increase_magnitude = (((long double)value < direction) != negative);
    if (increase_magnitude) cplus_math_increment_magnitude((unsigned char*)&value, 7);
    else cplus_math_decrement_magnitude((unsigned char*)&value, 7);
    if (value_kind != FP_INFINITE && cplus_math_classify_double(value) == FP_INFINITE) errno = ERANGE;
    return value;
}

static long double cplus_math_nextafter_long_double(long double value, long double direction) {
    int value_kind = cplus_math_classify_long_double(value);
    int direction_kind = cplus_math_classify_long_double(direction);
    int negative;
    int increase_magnitude;
    if (value_kind == FP_NAN) return value;
    if (direction_kind == FP_NAN) return direction;
    if (value == direction) return direction;
    if (value_kind == FP_ZERO) {
        unsigned char* bytes = (unsigned char*)&value;
#if CPLUS_LONG_DOUBLE_FORMAT == 1
        cplus_math_make_smallest_subnormal(bytes, 7, cplus_math_sign_long_double(direction));
#elif CPLUS_LONG_DOUBLE_FORMAT == 2
        cplus_math_make_smallest_subnormal(bytes, 9, cplus_math_sign_long_double(direction));
#elif CPLUS_LONG_DOUBLE_FORMAT == 3
        cplus_math_make_smallest_subnormal(bytes, 15, cplus_math_sign_long_double(direction));
#endif
        return value;
    }
    negative = cplus_math_sign_long_double(value);
    increase_magnitude = ((value < direction) != negative);
#if CPLUS_LONG_DOUBLE_FORMAT == 1
    if (increase_magnitude) cplus_math_increment_magnitude((unsigned char*)&value, 7);
    else cplus_math_decrement_magnitude((unsigned char*)&value, 7);
#elif CPLUS_LONG_DOUBLE_FORMAT == 2
    cplus_math_step_x87((unsigned char*)&value, increase_magnitude);
#elif CPLUS_LONG_DOUBLE_FORMAT == 3
    if (increase_magnitude) cplus_math_increment_magnitude((unsigned char*)&value, 15);
    else cplus_math_decrement_magnitude((unsigned char*)&value, 15);
#endif
    if (value_kind != FP_INFINITE && cplus_math_classify_long_double(value) == FP_INFINITE) errno = ERANGE;
    return value;
}

float nextafterf(float value, float direction) {
    return cplus_math_nextafter_float(value, (long double)direction);
}

double nextafter(double value, double direction) {
    return cplus_math_nextafter_double(value, (long double)direction);
}

long double nextafterl(long double value, long double direction) {
    return cplus_math_nextafter_long_double(value, direction);
}

float nexttowardf(float value, long double direction) {
    return cplus_math_nextafter_float(value, direction);
}

double nexttoward(double value, long double direction) {
    return cplus_math_nextafter_double(value, direction);
}

long double nexttowardl(long double value, long double direction) {
    return cplus_math_nextafter_long_double(value, direction);
}
