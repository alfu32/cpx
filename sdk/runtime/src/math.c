#include <math.h>
#include <errno.h>

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
