#ifndef CPLUS_SDK_MATH_H
#define CPLUS_SDK_MATH_H
#include <float.h>
typedef float float_t;
typedef double double_t;
float acosf(float value);
double acos(double value);
long double acosl(long double value);
float asinf(float value);
double asin(double value);
long double asinl(long double value);
float atanf(float value);
double atan(double value);
long double atanl(long double value);
float acoshf(float value);
double acosh(double value);
long double acoshl(long double value);
float asinhf(float value);
double asinh(double value);
long double asinhl(long double value);
float atanhf(float value);
double atanh(double value);
long double atanhl(long double value);
float cosf(float value);
double cos(double value);
long double cosl(long double value);
float sinf(float value);
double sin(double value);
long double sinl(long double value);
float tanf(float value);
double tan(double value);
long double tanl(long double value);
float coshf(float value);
double cosh(double value);
long double coshl(long double value);
float sinhf(float value);
double sinh(double value);
long double sinhl(long double value);
float tanhf(float value);
double tanh(double value);
long double tanhl(long double value);
float expf(float value);
double exp(double value);
long double expl(long double value);
float exp2f(float value);
double exp2(double value);
long double exp2l(long double value);
float expm1f(float value);
double expm1(double value);
long double expm1l(long double value);
float logf(float value);
double log(double value);
long double logl(long double value);
float log10f(float value);
double log10(double value);
long double log10l(long double value);
float log1pf(float value);
double log1p(double value);
long double log1pl(long double value);
float log2f(float value);
double log2(double value);
long double log2l(long double value);
float logbf(float value);
double logb(double value);
long double logbl(long double value);
float cbrtf(float value);
double cbrt(double value);
long double cbrtl(long double value);
float fabsf(float value);
double fabs(double value);
long double fabsl(long double value);
float sqrtf(float value);
double sqrt(double value);
long double sqrtl(long double value);
float erff(float value);
double erf(double value);
long double erfl(long double value);
float erfcf(float value);
double erfc(double value);
long double erfcl(long double value);
float lgammaf(float value);
double lgamma(double value);
long double lgammal(long double value);
float tgammaf(float value);
double tgamma(double value);
long double tgammal(long double value);
float ceilf(float value);
double ceil(double value);
long double ceill(long double value);
float floorf(float value);
double floor(double value);
long double floorl(long double value);
float nearbyintf(float value);
double nearbyint(double value);
long double nearbyintl(long double value);
float rintf(float value);
double rint(double value);
long double rintl(long double value);
float roundf(float value);
double round(double value);
long double roundl(long double value);
float truncf(float value);
double trunc(double value);
long double truncl(long double value);
float atan2f(float left, float right);
double atan2(double left, double right);
long double atan2l(long double left, long double right);
float fmodf(float left, float right);
double fmod(double left, double right);
long double fmodl(long double left, long double right);
float remainderf(float left, float right);
double remainder(double left, double right);
long double remainderl(long double left, long double right);
float hypotf(float left, float right);
double hypot(double left, double right);
long double hypotl(long double left, long double right);
float powf(float left, float right);
double pow(double left, double right);
long double powl(long double left, long double right);
float copysignf(float left, float right);
double copysign(double left, double right);
long double copysignl(long double left, long double right);
float nextafterf(float left, float right);
double nextafter(double left, double right);
long double nextafterl(long double left, long double right);
float fdimf(float left, float right);
double fdim(double left, double right);
long double fdiml(long double left, long double right);
float fmaxf(float left, float right);
double fmax(double left, double right);
long double fmaxl(long double left, long double right);
float fminf(float left, float right);
double fmin(double left, double right);
long double fminl(long double left, long double right);
float frexpf(float value, int* exponent);
double frexp(double value, int* exponent);
long double frexpl(long double value, int* exponent);
float modff(float value, float* integral);
double modf(double value, double* integral);
long double modfl(long double value, long double* integral);
int ilogbf(float value);
int ilogb(double value);
int ilogbl(long double value);
float ldexpf(float value, int exponent);
double ldexp(double value, int exponent);
long double ldexpl(long double value, int exponent);
float scalbnf(float value, int exponent);
double scalbn(double value, int exponent);
long double scalbnl(long double value, int exponent);
float scalblnf(float value, long int exponent);
double scalbln(double value, long int exponent);
long double scalblnl(long double value, long int exponent);
long int lrintf(float value);
long int lrint(double value);
long int lrintl(long double value);
long long int llrintf(float value);
long long int llrint(double value);
long long int llrintl(long double value);
long int lroundf(float value);
long int lround(double value);
long int lroundl(long double value);
long long int llroundf(float value);
long long int llround(double value);
long long int llroundl(long double value);
float nanf(const char* tag);
double nan(const char* tag);
long double nanl(const char* tag);
float nexttowardf(float value, long double direction);
double nexttoward(double value, long double direction);
long double nexttowardl(long double value, long double direction);
float remquof(float left, float right, int* quotient);
double remquo(double left, double right, int* quotient);
long double remquol(long double left, long double right, int* quotient);
float fmaf(float first, float second, float third);
double fma(double first, double second, double third);
long double fmal(long double first, long double second, long double third);
int cplus_math_fpclassifyf(float value);
int cplus_math_fpclassify(double value);
int cplus_math_fpclassifyl(long double value);
int cplus_math_isfinitef(float value);
int cplus_math_isfinite(double value);
int cplus_math_isfinitel(long double value);
int cplus_math_isinff(float value);
int cplus_math_isinf(double value);
int cplus_math_isinfl(long double value);
int cplus_math_isnanf(float value);
int cplus_math_isnan(double value);
int cplus_math_isnanl(long double value);
int cplus_math_isnormalf(float value);
int cplus_math_isnormal(double value);
int cplus_math_isnormall(long double value);
int cplus_math_signbitf(float value);
int cplus_math_signbit(double value);
int cplus_math_signbitl(long double value);
int cplus_math_isgreaterf(float left, float right);
int cplus_math_isgreater(double left, double right);
int cplus_math_isgreaterl(long double left, long double right);
int cplus_math_isgreaterequalf(float left, float right);
int cplus_math_isgreaterequal(double left, double right);
int cplus_math_isgreaterequall(long double left, long double right);
int cplus_math_islessf(float left, float right);
int cplus_math_isless(double left, double right);
int cplus_math_islessl(long double left, long double right);
int cplus_math_islessequalf(float left, float right);
int cplus_math_islessequal(double left, double right);
int cplus_math_islessequall(long double left, long double right);
int cplus_math_islessgreaterf(float left, float right);
int cplus_math_islessgreater(double left, double right);
int cplus_math_islessgreaterl(long double left, long double right);
int cplus_math_isunorderedf(float left, float right);
int cplus_math_isunordered(double left, double right);
int cplus_math_isunorderedl(long double left, long double right);

#if defined(__clang__) || defined(__GNUC__)
#define HUGE_VALF (__builtin_huge_valf())
#define HUGE_VAL (__builtin_huge_val())
#define HUGE_VALL (__builtin_huge_vall())
#define INFINITY (__builtin_inff())
#define NAN (__builtin_nanf(""))
#else
#define HUGE_VALF (1.0F / 0.0F)
#define HUGE_VAL (1.0 / 0.0)
#define HUGE_VALL (1.0L / 0.0L)
#define INFINITY (1.0F / 0.0F)
#define NAN (0.0F / 0.0F)
#endif
#define FP_ILOGB0 (-2147483647 - 1)
#define FP_ILOGBNAN 2147483647
#define FP_ZERO 0
#define FP_SUBNORMAL 1
#define FP_NORMAL 2
#define FP_INFINITE 3
#define FP_NAN 4
#define MATH_ERRNO 1
#define MATH_ERREXCEPT 2
#define math_errhandling MATH_ERRNO
#define fpclassify(value) _Generic((value), float: cplus_math_fpclassifyf, double: cplus_math_fpclassify, long double: cplus_math_fpclassifyl)(value)
#define isfinite(value) _Generic((value), float: cplus_math_isfinitef, double: cplus_math_isfinite, long double: cplus_math_isfinitel)(value)
#define isinf(value) _Generic((value), float: cplus_math_isinff, double: cplus_math_isinf, long double: cplus_math_isinfl)(value)
#define isnan(value) _Generic((value), float: cplus_math_isnanf, double: cplus_math_isnan, long double: cplus_math_isnanl)(value)
#define isnormal(value) _Generic((value), float: cplus_math_isnormalf, double: cplus_math_isnormal, long double: cplus_math_isnormall)(value)
#define signbit(value) _Generic((value), float: cplus_math_signbitf, double: cplus_math_signbit, long double: cplus_math_signbitl)(value)
#define CPLUS_MATH_BINARY_DISPATCH(left, right, float_fn, double_fn, long_fn) _Generic(((left) + (right)), float: float_fn, double: double_fn, long double: long_fn)
#define isgreater(left, right) CPLUS_MATH_BINARY_DISPATCH(left, right, cplus_math_isgreaterf, cplus_math_isgreater, cplus_math_isgreaterl)(left, right)
#define isgreaterequal(left, right) CPLUS_MATH_BINARY_DISPATCH(left, right, cplus_math_isgreaterequalf, cplus_math_isgreaterequal, cplus_math_isgreaterequall)(left, right)
#define isless(left, right) CPLUS_MATH_BINARY_DISPATCH(left, right, cplus_math_islessf, cplus_math_isless, cplus_math_islessl)(left, right)
#define islessequal(left, right) CPLUS_MATH_BINARY_DISPATCH(left, right, cplus_math_islessequalf, cplus_math_islessequal, cplus_math_islessequall)(left, right)
#define islessgreater(left, right) CPLUS_MATH_BINARY_DISPATCH(left, right, cplus_math_islessgreaterf, cplus_math_islessgreater, cplus_math_islessgreaterl)(left, right)
#define isunordered(left, right) CPLUS_MATH_BINARY_DISPATCH(left, right, cplus_math_isunorderedf, cplus_math_isunordered, cplus_math_isunorderedl)(left, right)
#endif
