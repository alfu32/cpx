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

#define CPLUS_DEFINE_COMPLEX_EXP_ROOT(suffix, real_type, complex_type, exponential, logarithm, logarithm_one_plus, \
                                     hypotenuse, absolute, square_root, sine, cosine, angle, copy_sign, \
                                     is_nan, is_inf, is_finite, infinity) \
    static real_type cplus_complex_exp_product_##suffix(real_type exponent, real_type factor) { \
        real_type scale; \
        if (factor == (real_type)0) return copy_sign((real_type)0, factor); \
        scale = exponential(exponent); \
        if (is_inf(scale) && is_finite(exponent)) { \
            return copy_sign(exponential(exponent + logarithm(absolute(factor))), factor); \
        } \
        return scale * factor; \
    } \
    complex_type cexp##suffix(complex_type value) { \
        real_type real_part = __real__ value; \
        real_type imaginary_part = __imag__ value; \
        real_type cosine_value; \
        real_type sine_value; \
        if (is_nan(imaginary_part)) { \
            if (is_inf(real_part) && real_part < (real_type)0) \
                return __builtin_complex((real_type)0, (real_type)0); \
            if (is_inf(real_part) && real_part > (real_type)0) \
                return __builtin_complex((real_type)(infinity), (real_type)(NAN)); \
            if (is_nan(real_part)) return __builtin_complex((real_type)(NAN), (real_type)(NAN)); \
            return __builtin_complex((real_type)(NAN), (real_type)(NAN)); \
        } \
        if (is_inf(imaginary_part)) { \
            if (is_inf(real_part) && real_part < (real_type)0) \
                return __builtin_complex((real_type)0, (real_type)0); \
            if (is_inf(real_part) && real_part > (real_type)0) \
                return __builtin_complex((real_type)(infinity), (real_type)(NAN)); \
            return __builtin_complex((real_type)(NAN), (real_type)(NAN)); \
        } \
        if (is_nan(real_part)) { \
            if (imaginary_part == (real_type)0) \
                return __builtin_complex((real_type)(NAN), imaginary_part); \
            return __builtin_complex((real_type)(NAN), (real_type)(NAN)); \
        } \
        cosine_value = cosine(imaginary_part); \
        sine_value = sine(imaginary_part); \
        if (is_inf(real_part)) { \
            if (real_part > (real_type)0) { \
                real_part = cosine_value == (real_type)0 ? copy_sign((real_type)0, cosine_value) \
                    : copy_sign((real_type)(infinity), cosine_value); \
                imaginary_part = sine_value == (real_type)0 ? copy_sign((real_type)0, sine_value) \
                    : copy_sign((real_type)(infinity), sine_value); \
            } else { \
                real_part = copy_sign((real_type)0, cosine_value); \
                imaginary_part = copy_sign((real_type)0, sine_value); \
            } \
            return __builtin_complex(real_part, imaginary_part); \
        } \
        return __builtin_complex( \
            cplus_complex_exp_product_##suffix(real_part, cosine_value), \
            cplus_complex_exp_product_##suffix(real_part, sine_value)); \
    } \
    static real_type cplus_complex_log_magnitude_##suffix(real_type real_part, real_type imaginary_part) { \
        real_type absolute_real = absolute(real_part); \
        real_type absolute_imaginary = absolute(imaginary_part); \
        real_type larger; \
        real_type smaller; \
        real_type ratio; \
        if (is_inf(absolute_real) || is_inf(absolute_imaginary)) return (real_type)(infinity); \
        if (is_nan(absolute_real) || is_nan(absolute_imaginary)) return (real_type)(NAN); \
        larger = absolute_real > absolute_imaginary ? absolute_real : absolute_imaginary; \
        smaller = absolute_real > absolute_imaginary ? absolute_imaginary : absolute_real; \
        if (larger == (real_type)0) return -(real_type)(infinity); \
        ratio = smaller / larger; \
        return logarithm(larger) + (real_type)0.5 * logarithm_one_plus(ratio * ratio); \
    } \
    complex_type clog##suffix(complex_type value) { \
        real_type real_part = __real__ value; \
        real_type imaginary_part = __imag__ value; \
        if (is_inf(real_part) || is_inf(imaginary_part)) { \
            return __builtin_complex((real_type)(infinity), angle(imaginary_part, real_part)); \
        } \
        if (is_nan(real_part) || is_nan(imaginary_part)) \
            return __builtin_complex((real_type)(NAN), (real_type)(NAN)); \
        return __builtin_complex( \
            cplus_complex_log_magnitude_##suffix(real_part, imaginary_part), \
            angle(imaginary_part, real_part)); \
    } \
    complex_type cpow##suffix(complex_type base, complex_type exponent) { \
        real_type base_real = __real__ base; \
        real_type base_imaginary = __imag__ base; \
        real_type exponent_real = __real__ exponent; \
        real_type exponent_imaginary = __imag__ exponent; \
        if (exponent_real == (real_type)0 && exponent_imaginary == (real_type)0) \
            return __builtin_complex((real_type)1, (real_type)0); \
        if (base_real == (real_type)1 && base_imaginary == (real_type)0) \
            return __builtin_complex((real_type)1, (real_type)0); \
        return cexp##suffix(exponent * clog##suffix(base)); \
    } \
    complex_type csqrt##suffix(complex_type value) { \
        real_type real_part = __real__ value; \
        real_type imaginary_part = __imag__ value; \
        real_type absolute_real; \
        real_type absolute_imaginary; \
        real_type scale; \
        real_type scaled_real; \
        real_type scaled_imaginary; \
        real_type scaled_magnitude; \
        real_type root_scale; \
        real_type result_real; \
        real_type result_imaginary; \
        if (is_inf(imaginary_part)) \
            return __builtin_complex((real_type)(infinity), copy_sign((real_type)(infinity), imaginary_part)); \
        if (is_inf(real_part)) { \
            if (real_part > (real_type)0) { \
                result_imaginary = is_nan(imaginary_part) ? (real_type)(NAN) \
                    : copy_sign((real_type)0, imaginary_part); \
                return __builtin_complex((real_type)(infinity), result_imaginary); \
            } \
            if (is_nan(imaginary_part)) \
                return __builtin_complex((real_type)(NAN), copy_sign((real_type)(infinity), imaginary_part)); \
            return __builtin_complex((real_type)0, copy_sign((real_type)(infinity), imaginary_part)); \
        } \
        if (is_nan(real_part) || is_nan(imaginary_part)) \
            return __builtin_complex((real_type)(NAN), (real_type)(NAN)); \
        if (imaginary_part == (real_type)0) { \
            if (real_part < (real_type)0) \
                return __builtin_complex((real_type)0, copy_sign(square_root(-real_part), imaginary_part)); \
            result_real = real_part == (real_type)0 ? (real_type)0 : square_root(real_part); \
            return __builtin_complex(result_real, imaginary_part); \
        } \
        absolute_real = absolute(real_part); \
        absolute_imaginary = absolute(imaginary_part); \
        scale = absolute_real > absolute_imaginary ? absolute_real : absolute_imaginary; \
        scaled_real = real_part / scale; \
        scaled_imaginary = imaginary_part / scale; \
        scaled_magnitude = hypotenuse(scaled_real, scaled_imaginary); \
        root_scale = square_root(scale); \
        if (real_part >= (real_type)0) { \
            result_real = root_scale * square_root((scaled_magnitude + scaled_real) * (real_type)0.5); \
            result_imaginary = imaginary_part / ((real_type)2 * result_real); \
        } else { \
            result_imaginary = copy_sign( \
                root_scale * square_root((scaled_magnitude - scaled_real) * (real_type)0.5), imaginary_part); \
            result_real = absolute_imaginary / ((real_type)2 * absolute(result_imaginary)); \
        } \
        return __builtin_complex(result_real, result_imaginary); \
    }

#define CPLUS_DEFINE_COMPLEX_TRIG_HYPER(suffix, real_type, complex_type, sine, cosine, tangent, \
                                        hyperbolic_sine, hyperbolic_cosine, hyperbolic_tangent, \
                                        inverse_sine, inverse_cosine, inverse_tangent, \
                                        inverse_hyperbolic_sine, inverse_hyperbolic_cosine, \
                                        inverse_hyperbolic_tangent, exponential, logarithm, square_root, \
                                        absolute, copy_sign, pi) \
    complex_type csin##suffix(complex_type value) { \
        real_type x = __real__ value, y = __imag__ value; \
        return __builtin_complex(sine(x) * hyperbolic_cosine(y), cosine(x) * hyperbolic_sine(y)); \
    } \
    complex_type ccos##suffix(complex_type value) { \
        real_type x = __real__ value, y = __imag__ value; \
        return __builtin_complex(cosine(x) * hyperbolic_cosine(y), -sine(x) * hyperbolic_sine(y)); \
    } \
    complex_type ctan##suffix(complex_type value) { \
        real_type x = __real__ value, y = __imag__ value; \
        real_type decay = exponential((real_type)-2 * absolute(y)); \
        real_type denominator = (real_type)1 + (real_type)2 * cosine((real_type)2 * x) * decay + decay * decay; \
        return __builtin_complex((real_type)2 * sine((real_type)2 * x) * decay / denominator, \
            copy_sign(((real_type)1 - decay * decay) / denominator, y)); \
    } \
    complex_type csinh##suffix(complex_type value) { \
        real_type x = __real__ value, y = __imag__ value; \
        return __builtin_complex(hyperbolic_sine(x) * cosine(y), hyperbolic_cosine(x) * sine(y)); \
    } \
    complex_type ccosh##suffix(complex_type value) { \
        real_type x = __real__ value, y = __imag__ value; \
        return __builtin_complex(hyperbolic_cosine(x) * cosine(y), hyperbolic_sine(x) * sine(y)); \
    } \
    complex_type ctanh##suffix(complex_type value) { \
        real_type x = __real__ value, y = __imag__ value; \
        real_type decay = exponential((real_type)-2 * absolute(x)); \
        real_type denominator = (real_type)1 + (real_type)2 * cosine((real_type)2 * y) * decay + decay * decay; \
        return __builtin_complex(copy_sign(((real_type)1 - decay * decay) / denominator, x), \
            (real_type)2 * sine((real_type)2 * y) * decay / denominator); \
    } \
    complex_type casin##suffix(complex_type value) { \
        complex_type rotated = __builtin_complex(-__imag__ value, __real__ value); \
        complex_type result = casinh##suffix(rotated); \
        return __builtin_complex(__imag__ result, -__real__ result); \
    } \
    complex_type cacos##suffix(complex_type value) { \
        complex_type result = inverse_sine(value); \
        return __builtin_complex((real_type)(pi / (real_type)2) - __real__ result, -__imag__ result); \
    } \
    complex_type catan##suffix(complex_type value) { \
        complex_type iz = __builtin_complex(-__imag__ value, __real__ value); \
        complex_type first = clog##suffix(__builtin_complex((real_type)1 - __real__ iz, -__imag__ iz)); \
        complex_type second = clog##suffix(__builtin_complex((real_type)1 + __real__ iz, __imag__ iz)); \
        return __builtin_complex((__imag__ second - __imag__ first) / (real_type)2, \
                                 (__real__ first - __real__ second) / (real_type)2); \
    } \
    complex_type casinh##suffix(complex_type value) { \
        complex_type result = clog##suffix(value + csqrt##suffix(value * value + (real_type)1)); \
        return result; \
    } \
    complex_type cacosh##suffix(complex_type value) { \
        complex_type plus = csqrt##suffix(value + (real_type)1); \
        complex_type minus = csqrt##suffix(value - (real_type)1); \
        return clog##suffix(value + plus * minus); \
    } \
    complex_type catanh##suffix(complex_type value) { \
        complex_type positive = clog##suffix((real_type)1 + value); \
        complex_type negative = clog##suffix((real_type)1 - value); \
        return __builtin_complex((__real__ positive - __real__ negative) / (real_type)2, \
                                 (__imag__ positive - __imag__ negative) / (real_type)2); \
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

CPLUS_DEFINE_COMPLEX_EXP_ROOT(f, float, float _Complex, expf, logf, log1pf, hypotf, fabsf, sqrtf, sinf, cosf,
                              atan2f, copysignf, __builtin_isnan, __builtin_isinf, __builtin_isfinite, INFINITY)
CPLUS_DEFINE_COMPLEX_EXP_ROOT(, double, double _Complex, exp, log, log1p, hypot, fabs, sqrt, sin, cos,
                              atan2, copysign, __builtin_isnan, __builtin_isinf, __builtin_isfinite, HUGE_VAL)
CPLUS_DEFINE_COMPLEX_EXP_ROOT(l, long double, long double _Complex, expl, logl, log1pl, hypotl, fabsl, sqrtl, sinl,
                              cosl, atan2l, copysignl, __builtin_isnan, __builtin_isinf, __builtin_isfinite, HUGE_VALL)

CPLUS_DEFINE_COMPLEX_TRIG_HYPER(f, float, float _Complex, sinf, cosf, tanf, sinhf, coshf, tanhf,
    asinf, acosf, atanf, asinhf, acoshf, atanhf, expf, logf, sqrtf, fabsf, copysignf, 3.14159265358979323846F)
CPLUS_DEFINE_COMPLEX_TRIG_HYPER(, double, double _Complex, sin, cos, tan, sinh, cosh, tanh,
    asin, acos, atan, asinh, acosh, atanh, exp, log, sqrt, fabs, copysign, 3.14159265358979323846)
CPLUS_DEFINE_COMPLEX_TRIG_HYPER(l, long double, long double _Complex, sinl, cosl, tanl, sinhl, coshl, tanhl,
    asinl, acosl, atanl, asinhl, acoshl, atanhl, expl, logl, sqrtl, fabsl, copysignl,
    3.141592653589793238462643383279502884L)

#undef CPLUS_DEFINE_COMPLEX_TRIG_HYPER
#undef CPLUS_DEFINE_COMPLEX_EXP_ROOT
#undef CPLUS_DEFINE_COMPLEX_COMPONENTS
#undef CPLUS_DEFINE_COMPLEX_DIVIDE
#undef CPLUS_DEFINE_COMPLEX_MULTIPLY
