#include <math.h>
#include <complex.h>

/*
 * GCC and Clang lower C complex multiplication/division to these compiler
 * helper ABIs. The C+ runtime owns the definitions so self-hosted links do
 * not depend on a host libgcc or compiler-rt archive.
 */
#define CPLUS_DEFINE_COMPLEX_MULTIPLY(symbol, real_type, complex_type, is_nan, is_inf, copy_sign, infinity) \
    complex_type symbol(real_type a, real_type b, real_type c, real_type d) { \
        real_type ac = a * c; \
        real_type bd = b * d; \
        real_type ad = a * d; \
        real_type bc = b * c; \
        real_type real_part = ac - bd; \
        real_type imaginary_part = ad + bc; \
        if (is_nan(real_part) && is_nan(imaginary_part)) { \
            int recalculate = 0; \
            if (is_inf(a) || is_inf(b)) { \
                a = copy_sign(is_inf(a) ? (real_type)1 : (real_type)0, a); \
                b = copy_sign(is_inf(b) ? (real_type)1 : (real_type)0, b); \
                if (is_nan(c)) c = copy_sign((real_type)0, c); \
                if (is_nan(d)) d = copy_sign((real_type)0, d); \
                recalculate = 1; \
            } \
            if (is_inf(c) || is_inf(d)) { \
                c = copy_sign(is_inf(c) ? (real_type)1 : (real_type)0, c); \
                d = copy_sign(is_inf(d) ? (real_type)1 : (real_type)0, d); \
                if (is_nan(a)) a = copy_sign((real_type)0, a); \
                if (is_nan(b)) b = copy_sign((real_type)0, b); \
                recalculate = 1; \
            } \
            if (!recalculate && (is_inf(ac) || is_inf(bd) || is_inf(ad) || is_inf(bc))) { \
                if (is_nan(a)) a = copy_sign((real_type)0, a); \
                if (is_nan(b)) b = copy_sign((real_type)0, b); \
                if (is_nan(c)) c = copy_sign((real_type)0, c); \
                if (is_nan(d)) d = copy_sign((real_type)0, d); \
                recalculate = 1; \
            } \
            if (recalculate) { \
                real_part = (infinity) * (a * c - b * d); \
                imaginary_part = (infinity) * (a * d + b * c); \
            } \
        } \
        return __builtin_complex(real_part, imaginary_part); \
    }

#define CPLUS_DEFINE_COMPLEX_DIVIDE(symbol, real_type, complex_type, is_nan, is_inf, is_finite, copy_sign, \
                                    absolute, maximum, log_binary, scale_binary, infinity) \
    complex_type symbol(real_type a, real_type b, real_type c, real_type d) { \
        int scale_exponent = 0; \
        real_type log_scale = log_binary(maximum(absolute(c), absolute(d))); \
        real_type denominator; \
        real_type real_part; \
        real_type imaginary_part; \
        if (is_finite(log_scale)) { \
            scale_exponent = (int)log_scale; \
            c = scale_binary(c, -scale_exponent); \
            d = scale_binary(d, -scale_exponent); \
        } \
        denominator = c * c + d * d; \
        real_part = scale_binary((a * c + b * d) / denominator, -scale_exponent); \
        imaginary_part = scale_binary((b * c - a * d) / denominator, -scale_exponent); \
        if (is_nan(real_part) && is_nan(imaginary_part)) { \
            if (denominator == (real_type)0 && (!is_nan(a) || !is_nan(b))) { \
                real_part = copy_sign((infinity), c) * a; \
                imaginary_part = copy_sign((infinity), c) * b; \
            } else if ((is_inf(a) || is_inf(b)) && is_finite(c) && is_finite(d)) { \
                a = copy_sign(is_inf(a) ? (real_type)1 : (real_type)0, a); \
                b = copy_sign(is_inf(b) ? (real_type)1 : (real_type)0, b); \
                real_part = (infinity) * (a * c + b * d); \
                imaginary_part = (infinity) * (b * c - a * d); \
            } else if (is_inf(log_scale) && log_scale > (real_type)0 && is_finite(a) && is_finite(b)) { \
                c = copy_sign(is_inf(c) ? (real_type)1 : (real_type)0, c); \
                d = copy_sign(is_inf(d) ? (real_type)1 : (real_type)0, d); \
                real_part = (real_type)0 * (a * c + b * d); \
                imaginary_part = (real_type)0 * (b * c - a * d); \
            } \
        } \
        return __builtin_complex(real_part, imaginary_part); \
    }

#define CPLUS_DEFINE_COMPLEX_COMPONENTS(suffix, real_type, complex_type, magnitude, argument, copy_sign, infinity) \
    real_type creal##suffix(complex_type value) { return __real__ value; } \
    real_type cimag##suffix(complex_type value) { return __imag__ value; } \
    real_type cabs##suffix(complex_type value) { return magnitude(__real__ value, __imag__ value); } \
    real_type carg##suffix(complex_type value) { return argument(__imag__ value, __real__ value); } \
    complex_type conj##suffix(complex_type value) { \
        return __builtin_complex(__real__ value, -__imag__ value); \
    } \
    complex_type cproj##suffix(complex_type value) { \
        real_type real_part = __real__ value; \
        real_type imaginary_part = __imag__ value; \
        if (__builtin_isinf(real_part) || __builtin_isinf(imaginary_part)) { \
            return __builtin_complex((real_type)(infinity), copy_sign((real_type)0, imaginary_part)); \
        } \
        return value; \
    }

CPLUS_DEFINE_COMPLEX_MULTIPLY(__mulsc3, float, float _Complex, __builtin_isnan, __builtin_isinf, copysignf, INFINITY)
CPLUS_DEFINE_COMPLEX_MULTIPLY(__muldc3, double, double _Complex, __builtin_isnan, __builtin_isinf, copysign, HUGE_VAL)
CPLUS_DEFINE_COMPLEX_MULTIPLY(__mulxc3, long double, long double _Complex, __builtin_isnan, __builtin_isinf, copysignl, HUGE_VALL)

CPLUS_DEFINE_COMPLEX_DIVIDE(__divsc3, float, float _Complex, __builtin_isnan, __builtin_isinf, __builtin_isfinite,
                            copysignf, fabsf, fmaxf, logbf, scalbnf, INFINITY)
CPLUS_DEFINE_COMPLEX_DIVIDE(__divdc3, double, double _Complex, __builtin_isnan, __builtin_isinf, __builtin_isfinite,
                            copysign, fabs, fmax, logb, scalbn, HUGE_VAL)
CPLUS_DEFINE_COMPLEX_DIVIDE(__divxc3, long double, long double _Complex, __builtin_isnan, __builtin_isinf,
                            __builtin_isfinite, copysignl, fabsl, fmaxl, logbl, scalbnl, HUGE_VALL)

CPLUS_DEFINE_COMPLEX_COMPONENTS(f, float, float _Complex, hypotf, atan2f, copysignf, INFINITY)
CPLUS_DEFINE_COMPLEX_COMPONENTS(, double, double _Complex, hypot, atan2, copysign, HUGE_VAL)
CPLUS_DEFINE_COMPLEX_COMPONENTS(l, long double, long double _Complex, hypotl, atan2l, copysignl, HUGE_VALL)

#undef CPLUS_DEFINE_COMPLEX_COMPONENTS
#undef CPLUS_DEFINE_COMPLEX_DIVIDE
#undef CPLUS_DEFINE_COMPLEX_MULTIPLY
