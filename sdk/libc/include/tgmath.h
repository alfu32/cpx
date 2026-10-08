#ifndef CPLUS_SDK_TGMATH_H
#define CPLUS_SDK_TGMATH_H

#include <math.h>
#include <complex.h>

/* The controlling expression is unevaluated by _Generic; call arguments occur once. */
#define __CPLUS_TGMATH_UNARY(value, f, d, l, cf, cd, cl) \
    _Generic((value) + 0, \
        float: f, double: d, long double: l, default: d, \
        float _Complex: cf, double _Complex: cd, long double _Complex: cl)(value)
#define __CPLUS_TGMATH_BINARY(left, right, f, d, l, cf, cd, cl) \
    _Generic((left) + (right), \
        float: f, double: d, long double: l, default: d, \
        float _Complex: cf, double _Complex: cd, long double _Complex: cl)(left, right)
#define __CPLUS_TGMATH_REAL_UNARY(value, f, d, l) \
    _Generic((value) + 0, float: f, double: d, long double: l, default: d)(value)
#define __CPLUS_TGMATH_REAL_BINARY(left, right, f, d, l) \
    _Generic((left) + (right), float: f, double: d, long double: l, default: d)(left, right)
#define __CPLUS_TGMATH_REAL_TERNARY(first, second, third, f, d, l) \
    _Generic((first) + (second) + (third), float: f, double: d, long double: l, default: d)(first, second, third)

/* Functions with both real and complex C17 counterparts. */
#define acos(x) __CPLUS_TGMATH_UNARY(x, acosf, acos, acosl, cacosf, cacos, cacosl)
#define asin(x) __CPLUS_TGMATH_UNARY(x, asinf, asin, asinl, casinf, casin, casinl)
#define atan(x) __CPLUS_TGMATH_UNARY(x, atanf, atan, atanl, catanf, catan, catanl)
#define acosh(x) __CPLUS_TGMATH_UNARY(x, acoshf, acosh, acoshl, cacoshf, cacosh, cacoshl)
#define asinh(x) __CPLUS_TGMATH_UNARY(x, asinhf, asinh, asinhl, casinhf, casinh, casinhl)
#define atanh(x) __CPLUS_TGMATH_UNARY(x, atanhf, atanh, atanhl, catanhf, catanh, catanhl)
#define cos(x) __CPLUS_TGMATH_UNARY(x, cosf, cos, cosl, ccosf, ccos, ccosl)
#define sin(x) __CPLUS_TGMATH_UNARY(x, sinf, sin, sinl, csinf, csin, csinl)
#define tan(x) __CPLUS_TGMATH_UNARY(x, tanf, tan, tanl, ctanf, ctan, ctanl)
#define cosh(x) __CPLUS_TGMATH_UNARY(x, coshf, cosh, coshl, ccoshf, ccosh, ccoshl)
#define sinh(x) __CPLUS_TGMATH_UNARY(x, sinhf, sinh, sinhl, csinhf, csinh, csinhl)
#define tanh(x) __CPLUS_TGMATH_UNARY(x, tanhf, tanh, tanhl, ctanhf, ctanh, ctanhl)
#define exp(x) __CPLUS_TGMATH_UNARY(x, expf, exp, expl, cexpf, cexp, cexpl)
#define log(x) __CPLUS_TGMATH_UNARY(x, logf, log, logl, clogf, clog, clogl)
#define sqrt(x) __CPLUS_TGMATH_UNARY(x, sqrtf, sqrt, sqrtl, csqrtf, csqrt, csqrtl)
#define fabs(x) __CPLUS_TGMATH_UNARY(x, fabsf, fabs, fabsl, cabsf, cabs, cabsl)
#define pow(x, y) __CPLUS_TGMATH_BINARY(x, y, powf, pow, powl, cpowf, cpow, cpowl)

/* Operations whose C17 interface is real-valued only. */
#define exp2(x) __CPLUS_TGMATH_REAL_UNARY(x, exp2f, exp2, exp2l)
#define expm1(x) __CPLUS_TGMATH_REAL_UNARY(x, expm1f, expm1, expm1l)
#define log10(x) __CPLUS_TGMATH_REAL_UNARY(x, log10f, log10, log10l)
#define log1p(x) __CPLUS_TGMATH_REAL_UNARY(x, log1pf, log1p, log1pl)
#define log2(x) __CPLUS_TGMATH_REAL_UNARY(x, log2f, log2, log2l)
#define logb(x) __CPLUS_TGMATH_REAL_UNARY(x, logbf, logb, logbl)
#define cbrt(x) __CPLUS_TGMATH_REAL_UNARY(x, cbrtf, cbrt, cbrtl)
#define erf(x) __CPLUS_TGMATH_REAL_UNARY(x, erff, erf, erfl)
#define erfc(x) __CPLUS_TGMATH_REAL_UNARY(x, erfcf, erfc, erfcl)
#define lgamma(x) __CPLUS_TGMATH_REAL_UNARY(x, lgammaf, lgamma, lgammal)
#define tgamma(x) __CPLUS_TGMATH_REAL_UNARY(x, tgammaf, tgamma, tgammal)
#define ceil(x) __CPLUS_TGMATH_REAL_UNARY(x, ceilf, ceil, ceill)
#define floor(x) __CPLUS_TGMATH_REAL_UNARY(x, floorf, floor, floorl)
#define nearbyint(x) __CPLUS_TGMATH_REAL_UNARY(x, nearbyintf, nearbyint, nearbyintl)
#define rint(x) __CPLUS_TGMATH_REAL_UNARY(x, rintf, rint, rintl)
#define round(x) __CPLUS_TGMATH_REAL_UNARY(x, roundf, round, roundl)
#define trunc(x) __CPLUS_TGMATH_REAL_UNARY(x, truncf, trunc, truncl)

#define atan2(x, y) __CPLUS_TGMATH_REAL_BINARY(x, y, atan2f, atan2, atan2l)
#define fmod(x, y) __CPLUS_TGMATH_REAL_BINARY(x, y, fmodf, fmod, fmodl)
#define remainder(x, y) __CPLUS_TGMATH_REAL_BINARY(x, y, remainderf, remainder, remainderl)
#define hypot(x, y) __CPLUS_TGMATH_REAL_BINARY(x, y, hypotf, hypot, hypotl)
#define copysign(x, y) __CPLUS_TGMATH_REAL_BINARY(x, y, copysignf, copysign, copysignl)
#define nextafter(x, y) __CPLUS_TGMATH_REAL_BINARY(x, y, nextafterf, nextafter, nextafterl)
#define fdim(x, y) __CPLUS_TGMATH_REAL_BINARY(x, y, fdimf, fdim, fdiml)
#define fmax(x, y) __CPLUS_TGMATH_REAL_BINARY(x, y, fmaxf, fmax, fmaxl)
#define fmin(x, y) __CPLUS_TGMATH_REAL_BINARY(x, y, fminf, fmin, fminl)

#define frexp(x, exponent) _Generic((x) + 0, float: frexpf, double: frexp, long double: frexpl, default: frexp)(x, exponent)
#define modf(x, integral) _Generic((x) + 0, float: modff, double: modf, long double: modfl, default: modf)(x, integral)
#define ilogb(x) _Generic((x) + 0, float: ilogbf, double: ilogb, long double: ilogbl, default: ilogb)(x)
#define lrint(x) _Generic((x) + 0, float: lrintf, double: lrint, long double: lrintl, default: lrint)(x)
#define llrint(x) _Generic((x) + 0, float: llrintf, double: llrint, long double: llrintl, default: llrint)(x)
#define lround(x) _Generic((x) + 0, float: lroundf, double: lround, long double: lroundl, default: lround)(x)
#define llround(x) _Generic((x) + 0, float: llroundf, double: llround, long double: llroundl, default: llround)(x)
#define nexttoward(x, direction) _Generic((x) + 0, float: nexttowardf, double: nexttoward, long double: nexttowardl, default: nexttoward)(x, direction)
#define remquo(x, y, quotient) _Generic((x) + (y), float: remquof, double: remquo, long double: remquol, default: remquo)(x, y, quotient)
#define fma(x, y, z) __CPLUS_TGMATH_REAL_TERNARY(x, y, z, fmaf, fma, fmal)
#define ldexp(x, exponent) _Generic((x) + 0, float: ldexpf, double: ldexp, long double: ldexpl, default: ldexp)(x, exponent)
#define scalbn(x, exponent) _Generic((x) + 0, float: scalbnf, double: scalbn, long double: scalbnl, default: scalbn)(x, exponent)
#define scalbln(x, exponent) _Generic((x) + 0, float: scalblnf, double: scalbln, long double: scalblnl, default: scalbln)(x, exponent)

/* Complex-only type-generic functions. */
#define carg(x) _Generic((x), float _Complex: cargf, double _Complex: carg, long double _Complex: cargl)(x)
#define cabs(x) _Generic((x), float _Complex: cabsf, double _Complex: cabs, long double _Complex: cabsl)(x)
#define cimag(x) _Generic((x), float _Complex: cimagf, double _Complex: cimag, long double _Complex: cimagl)(x)
#define conj(x) _Generic((x), float _Complex: conjf, double _Complex: conj, long double _Complex: conjl)(x)
#define cproj(x) _Generic((x), float _Complex: cprojf, double _Complex: cproj, long double _Complex: cprojl)(x)
#define creal(x) _Generic((x), float _Complex: crealf, double _Complex: creal, long double _Complex: creall)(x)

#endif
