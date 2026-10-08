#ifndef CPLUS_SDK_COMPLEX_H
#define CPLUS_SDK_COMPLEX_H

#define complex _Complex
#define _Complex_I ((const float _Complex)__builtin_complex(0.0F, 1.0F))
#define I _Complex_I

#define CMPLXF(real_value, imaginary_value) \
    __builtin_complex((float)(real_value), (float)(imaginary_value))
#define CMPLX(real_value, imaginary_value) \
    __builtin_complex((double)(real_value), (double)(imaginary_value))
#define CMPLXL(real_value, imaginary_value) \
    __builtin_complex((long double)(real_value), (long double)(imaginary_value))

#define __CPLUS_COMPLEX_UNARY(name, suffix, type) type name##suffix(type value);
#define __CPLUS_COMPLEX_BINARY(name, suffix, type) type name##suffix(type left, type right);
#define __CPLUS_COMPLEX_REAL_RESULT(name, suffix, real_type, complex_type) \
    real_type name##suffix(complex_type value);

#define __CPLUS_COMPLEX_DECLARE(type, suffix, real_type) \
    __CPLUS_COMPLEX_UNARY(cacos, suffix, type) \
    __CPLUS_COMPLEX_UNARY(casin, suffix, type) \
    __CPLUS_COMPLEX_UNARY(catan, suffix, type) \
    __CPLUS_COMPLEX_UNARY(ccos, suffix, type) \
    __CPLUS_COMPLEX_UNARY(csin, suffix, type) \
    __CPLUS_COMPLEX_UNARY(ctan, suffix, type) \
    __CPLUS_COMPLEX_UNARY(cacosh, suffix, type) \
    __CPLUS_COMPLEX_UNARY(casinh, suffix, type) \
    __CPLUS_COMPLEX_UNARY(catanh, suffix, type) \
    __CPLUS_COMPLEX_UNARY(ccosh, suffix, type) \
    __CPLUS_COMPLEX_UNARY(csinh, suffix, type) \
    __CPLUS_COMPLEX_UNARY(ctanh, suffix, type) \
    __CPLUS_COMPLEX_UNARY(cexp, suffix, type) \
    __CPLUS_COMPLEX_UNARY(clog, suffix, type) \
    __CPLUS_COMPLEX_UNARY(csqrt, suffix, type) \
    __CPLUS_COMPLEX_UNARY(conj, suffix, type) \
    __CPLUS_COMPLEX_UNARY(cproj, suffix, type) \
    __CPLUS_COMPLEX_BINARY(cpow, suffix, type) \
    __CPLUS_COMPLEX_REAL_RESULT(cabs, suffix, real_type, type) \
    __CPLUS_COMPLEX_REAL_RESULT(carg, suffix, real_type, type) \
    __CPLUS_COMPLEX_REAL_RESULT(cimag, suffix, real_type, type) \
    __CPLUS_COMPLEX_REAL_RESULT(creal, suffix, real_type, type)

__CPLUS_COMPLEX_DECLARE(float complex, f, float)
__CPLUS_COMPLEX_DECLARE(double complex, , double)
__CPLUS_COMPLEX_DECLARE(long double complex, l, long double)

#undef __CPLUS_COMPLEX_DECLARE
#undef __CPLUS_COMPLEX_REAL_RESULT
#undef __CPLUS_COMPLEX_BINARY
#undef __CPLUS_COMPLEX_UNARY

#endif
