#include <complex.h>
#include <math.h>

_Static_assert(sizeof(float complex) == 2 * sizeof(float), "float complex representation");
_Static_assert(sizeof(double complex) == 2 * sizeof(double), "double complex representation");
_Static_assert(sizeof(long double complex) == 2 * sizeof(long double), "long double complex representation");
_Static_assert(_Alignof(float complex) == _Alignof(float), "float complex alignment");
_Static_assert(_Alignof(double complex) == _Alignof(double), "double complex alignment");
_Static_assert(_Alignof(long double complex) == _Alignof(long double), "long double complex alignment");

#define CPLUS_CHECK_COMPLEX_EXP_ROOTS(tag, real_type, complex_type, exp_function, log_function, pow_function, \
                                     sqrt_function, exp_real, log_real, sin_real, cos_real, atan2_real, \
                                     acos_real, fabs_real, large_value, overflow_input, tolerance) \
    static int cplus_check_complex_roots_##tag(void) { \
        real_type pi = acos_real((real_type)-1); \
        complex_type value = __builtin_complex((real_type)3, (real_type)4); \
        complex_type result = exp_function(__builtin_complex((real_type)0, (real_type)0)); \
        if (__real__ result != (real_type)1 || __imag__ result != (real_type)0 || __builtin_signbit(__imag__ result)) return 1; \
        result = exp_function(__builtin_complex((real_type)0.5, (real_type)-0.75)); \
        if (fabs_real(__real__ result - exp_real((real_type)0.5) * cos_real((real_type)-0.75)) > (tolerance) || \
            fabs_real(__imag__ result - exp_real((real_type)0.5) * sin_real((real_type)-0.75)) > (tolerance)) return 2; \
        result = log_function(value); \
        if (fabs_real(__real__ result - log_real((real_type)5)) > (tolerance) || \
            fabs_real(__imag__ result - atan2_real((real_type)4, (real_type)3)) > (tolerance)) return 3; \
        result = pow_function(__builtin_complex((real_type)1, (real_type)1), __builtin_complex((real_type)2, (real_type)0)); \
        if (fabs_real(__real__ result) > (tolerance) || fabs_real(__imag__ result - (real_type)2) > (tolerance)) return 4; \
        result = pow_function(__builtin_complex((real_type)0, (real_type)0), __builtin_complex((real_type)0, (real_type)0)); \
        if (__real__ result != (real_type)1 || __imag__ result != (real_type)0) return 5; \
        result = sqrt_function(value); \
        if (fabs_real(__real__ result - (real_type)2) > (tolerance) || fabs_real(__imag__ result - (real_type)1) > (tolerance)) return 6; \
        result = sqrt_function(__builtin_complex((real_type)-4, (real_type)-0.0)); \
        if (__real__ result != (real_type)0 || __builtin_signbit(__real__ result) || \
            __imag__ result != (real_type)-2 || !__builtin_signbit(__imag__ result)) return 7; \
        result = log_function(__builtin_complex((real_type)-0.0, (real_type)-0.0)); \
        if (!__builtin_isinf(__real__ result) || __real__ result > (real_type)0 || \
            fabs_real(__imag__ result + pi) > (tolerance)) return 8; \
        result = exp_function(__builtin_complex((real_type)(INFINITY), (real_type)0)); \
        if (!__builtin_isinf(__real__ result) || __real__ result < (real_type)0 || \
            __imag__ result != (real_type)0 || __builtin_signbit(__imag__ result)) return 9; \
        result = exp_function(__builtin_complex((real_type)1, (real_type)(INFINITY))); \
        if (!__builtin_isnan(__real__ result) || !__builtin_isnan(__imag__ result)) return 10; \
        result = log_function(__builtin_complex((real_type)(INFINITY), (real_type)(NAN))); \
        if (!__builtin_isinf(__real__ result) || !__builtin_isnan(__imag__ result)) return 11; \
        result = sqrt_function(__builtin_complex((real_type)2, (real_type)(INFINITY))); \
        if (!__builtin_isinf(__real__ result) || !__builtin_isinf(__imag__ result)) return 12; \
        result = sqrt_function(__builtin_complex((real_type)(NAN), (real_type)2)); \
        if (!__builtin_isnan(__real__ result) || !__builtin_isnan(__imag__ result)) return 13; \
        result = exp_function(__builtin_complex((real_type)(overflow_input), acos_real((real_type)0))); \
        if (!__builtin_isfinite(__real__ result) || __real__ result == (real_type)0) return 14; \
        result = log_function(__builtin_complex((real_type)(large_value), (real_type)(large_value))); \
        if (!__builtin_isfinite(__real__ result)) return 15; \
        return 0; \
    }

CPLUS_CHECK_COMPLEX_EXP_ROOTS(float, float, float complex, cexpf, clogf, cpowf, csqrtf,
    expf, logf, sinf, cosf, atan2f, acosf, fabsf, 3.0e38F, 100.0F, 2.0e-4F)
CPLUS_CHECK_COMPLEX_EXP_ROOTS(double, double, double complex, cexp, clog, cpow, csqrt,
    exp, log, sin, cos, atan2, acos, fabs, 1.3e308, 710.0, 1.0e-12)
CPLUS_CHECK_COMPLEX_EXP_ROOTS(long_double, long double, long double complex, cexpl, clogl, cpowl, csqrtl,
    expl, logl, sinl, cosl, atan2l, acosl, fabsl, 1.0e4932L, 11000.0L, 1.0e-15L)

#undef CPLUS_CHECK_COMPLEX_EXP_ROOTS

int main(void) {
    float complex single = CMPLXF(1.25F, -2.5F);
    double complex double_value = CMPLX(3.125, -4.5);
    long double complex extended = CMPLXL(5.75L, -6.875L);
    double complex product = CMPLX(1.0, 2.0) * CMPLX(3.0, 4.0);
    double complex quotient = CMPLX(1.0, 2.0) / CMPLX(3.0, 4.0);
    double complex sum = CMPLX(1.0, 2.0) + CMPLX(3.0, 4.0);
    double complex difference = CMPLX(3.0, 4.0) - CMPLX(1.0, 2.0);
    if (single != CMPLXF(1.25F, -2.5F)) return 1;
    if (double_value != CMPLX(3.125, -4.5)) return 2;
    if (extended != CMPLXL(5.75L, -6.875L)) return 3;
    if (I != CMPLXF(0.0F, 1.0F)) return 4;
    if (product != CMPLX(-5.0, 10.0)) return 5;
    if (quotient != CMPLX(0.44, 0.08)) return 6;
    if (sum != CMPLX(4.0, 6.0)) return 7;
    if (difference != CMPLX(2.0, 2.0)) return 8;
    if (!(sum && difference) || !(!CMPLX(0.0, 0.0))) return 9;
    if (crealf(single) != 1.25F || cimagf(single) != -2.5F || cabsf(CMPLXF(3.0F, 4.0F)) != 5.0F) return 10;
    if (creal(double_value) != 3.125 || cimag(double_value) != -4.5 || cabs(CMPLX(3.0, 4.0)) != 5.0) return 11;
    if (creall(extended) != 5.75L || cimagl(extended) != -6.875L || cabsl(CMPLXL(3.0L, 4.0L)) != 5.0L) return 12;
    if (cargf(CMPLXF(1.0F, 1.0F)) != atan2f(1.0F, 1.0F)) return 13;
    if (carg(CMPLX(1.0, 1.0)) != atan2(1.0, 1.0)) return 14;
    if (cargl(CMPLXL(1.0L, 1.0L)) != atan2l(1.0L, 1.0L)) return 15;
    if (!signbit(cimag(CMPLX(1.0, -0.0)))) return 16;
    if (!signbit(cimag(conj(CMPLX(1.0, 0.0))))) return 17;
    if (!signbit(cimagf(conjf(CMPLXF(1.0F, 0.0F))))) return 18;
    if (!signbit(cimagl(conjl(CMPLXL(1.0L, 0.0L))))) return 19;
    double complex projected = cproj(CMPLX(-INFINITY, -0.0));
    if (!isinf(creal(projected)) || signbit(creal(projected)) || cimag(projected) != 0.0 || !signbit(cimag(projected))) return 20;
    float complex projected_float = cprojf(CMPLXF(NAN, -INFINITY));
    if (!isinf(crealf(projected_float)) || signbit(crealf(projected_float)) || cimagf(projected_float) != 0.0F || !signbit(cimagf(projected_float))) return 21;
    long double complex projected_extended = cprojl(CMPLXL(2.0L, INFINITY));
    if (!isinf(creall(projected_extended)) || signbit(creall(projected_extended)) || cimagl(projected_extended) != 0.0L || signbit(cimagl(projected_extended))) return 22;
    double complex finite_projection = cproj(CMPLX(-0.0, -0.0));
    if (!signbit(creal(finite_projection)) || !signbit(cimag(finite_projection))) return 23;
    if (!isinf(cabs(CMPLX(INFINITY, NAN)))) return 24;
    double pi = acos(-1.0);
    if (carg(CMPLX(-1.0, 0.0)) != pi || carg(CMPLX(-1.0, -0.0)) != -pi) return 25;
    if (!isnan(carg(CMPLX(1.0, NAN)))) return 26;
    double complex nan_projection = cproj(CMPLX(NAN, -2.0));
    if (!isnan(creal(nan_projection)) || cimag(nan_projection) != -2.0) return 27;
    double large_component = 0x1.fffffffffffffp+1022;
    double large_magnitude = cabs(CMPLX(large_component, large_component));
    if (!isfinite(large_magnitude) || large_magnitude <= large_component) return 28;
    if (cplus_check_complex_roots_float() != 0) return 30 + cplus_check_complex_roots_float();
    if (cplus_check_complex_roots_double() != 0) return 50 + cplus_check_complex_roots_double();
    if (cplus_check_complex_roots_long_double() != 0) return 70 + cplus_check_complex_roots_long_double();
    return 0;
}
