#include <tgmath.h>

#define CPLUS_TYPE_CHECK(expression, expected_type, message) \
    _Static_assert(_Generic((expression), expected_type: 1, default: 0), message)

CPLUS_TYPE_CHECK(sin(1.0F), float, "float real input selects float result");
CPLUS_TYPE_CHECK(sin(1), double, "integer input promotes to double");
CPLUS_TYPE_CHECK(sin(1.0L), long double, "long double real input selects extended result");
CPLUS_TYPE_CHECK(sin(CMPLXF(1.0F, 2.0F)), float complex, "float complex input selects complex result");
CPLUS_TYPE_CHECK(sin(CMPLX(1.0, 2.0)), double complex, "double complex input selects complex result");
CPLUS_TYPE_CHECK(sin(CMPLXL(1.0L, 2.0L)), long double complex, "extended complex input selects complex result");
CPLUS_TYPE_CHECK(pow(2.0F, 3), float, "binary integer argument preserves float rank");
CPLUS_TYPE_CHECK(pow(2.0F, 3.0), double, "binary common type selects double rank");
CPLUS_TYPE_CHECK(pow(CMPLXF(1.0F, 1.0F), 2.0F), float complex, "complex power selects float complex");
CPLUS_TYPE_CHECK(pow(CMPLXF(1.0F, 1.0F), 2.0), double complex, "mixed complex power promotes rank");
CPLUS_TYPE_CHECK(fabs(CMPLXF(3.0F, 4.0F)), float, "complex magnitude returns corresponding real type");
CPLUS_TYPE_CHECK(cabs(CMPLXL(3.0L, 4.0L)), long double, "generic cabs selects extended precision");
CPLUS_TYPE_CHECK(sqrt(CMPLXF(3.0F, 4.0F)), float complex, "generic sqrt selects complex square root");
CPLUS_TYPE_CHECK(ldexp(1.0F, 4), float, "integer exponent does not promote ldexp value rank");
CPLUS_TYPE_CHECK(fma(1.0F, 2.0F, 3), float, "ternary common type selects float");
CPLUS_TYPE_CHECK(frexp(2.0F, (int*)0), float, "frexp follows its value argument");
CPLUS_TYPE_CHECK(modf(2.0L, (long double*)0), long double, "modf follows its value argument");
CPLUS_TYPE_CHECK(nexttoward(1.0F, 2.0L), float, "nexttoward follows its first argument");
CPLUS_TYPE_CHECK(remainder(1.0F, 2.0), double, "binary real operations use common rank");
CPLUS_TYPE_CHECK(remquo(1.0L, 2, (int*)0), long double, "remquo follows common rank");
CPLUS_TYPE_CHECK(lrint(1.0F), long int, "lrint preserves its integer result type");
CPLUS_TYPE_CHECK(llround(1.0L), long long int, "llround preserves its integer result type");
CPLUS_TYPE_CHECK(carg(CMPLX(1.0, 1.0)), double, "generic carg returns corresponding real type");
CPLUS_TYPE_CHECK(creal(CMPLXL(1.0L, 2.0L)), long double, "generic creal returns corresponding real type");
CPLUS_TYPE_CHECK(conj(CMPLXF(1.0F, 2.0F)), float complex, "generic conj preserves complex type");
CPLUS_TYPE_CHECK(cproj(CMPLX(1.0, 2.0)), double complex, "generic cproj preserves complex type");

static int evaluation_count;

static float next_float(void) {
    ++evaluation_count;
    return 0.5F;
}

int main(void) {
    float complex complex_value = CMPLXF(3.0F, 4.0F);
    float complex generic_input = CMPLXF(0.25F, 0.375F);
    float complex complex_root = sqrt(complex_value);
    float complex complex_exp = exp(CMPLXF(0.0F, 0.0F));
    float selected_float = sin(0.5F);
    double selected_double = sin(1);
    long double selected_extended = cos(0.5L);
    float magnitude = fabs(complex_value);
    float scaled = ldexp(0.5F, 2);
    float powered = pow(2.0F, 3);
    float fused = fma(1.0F, 2.0F, 3);
    float evaluated = sin(next_float());
    float evaluated_power = pow(next_float(), next_float());
    volatile double generic_real;
    volatile float generic_float;
    volatile long double generic_extended;
    volatile float complex generic_complex;
    int exponent = 0;
    int quotient = 0;
    float integral_float = 0.0F;
    double integral_double = 0.0;
    long double integral_extended = 0.0L;

    if (crealf(complex_root) != 2.0F || cimagf(complex_root) != 1.0F) return 1;
    if (complex_exp != CMPLXF(1.0F, 0.0F)) return 2;
    if (fabsf(selected_float - sinf(0.5F)) > 1.0e-6F) return 3;
    if (fabs(selected_double - sin(1.0)) > 1.0e-12) return 4;
    if (fabsl(selected_extended - cosl(0.5L)) > 1.0e-12L) return 5;
    if (magnitude != 5.0F || scaled != 2.0F || powered != 8.0F || fused != 5.0F) return 6;
    if (evaluation_count != 3 || evaluated != sinf(0.5F) || evaluated_power != powf(0.5F, 0.5F)) return 7;
    generic_float = acos(0.5F) + asin(0.5F) + atan(0.5F) + acosh(2.0F) + asinh(0.5F) + atanh(0.5F);
    generic_float += cos(0.5F) + tan(0.5F) + cosh(0.5F) + sinh(0.5F) + tanh(0.5F);
    generic_float += exp2(2.0F) + expm1(0.5F) + log10(10.0F) + log1p(0.5F) + log2(2.0F) + logb(2.0F);
    generic_float += cbrt(8.0F) + erf(0.5F) + erfc(0.5F) + lgamma(2.0F) + tgamma(2.0F);
    generic_float += ceil(0.5F) + floor(0.5F) + nearbyint(0.5F) + rint(0.5F) + round(0.5F) + trunc(0.5F);
    generic_real = atan2(1.0F, 2.0) + fmod(5.0F, 2.0) + remainder(5.0, 2.0F) + hypot(3.0F, 4.0);
    generic_real += copysign(-1.0F, 2.0) + nextafter(1.0F, 2.0) + fdim(3.0F, 2.0);
    generic_real += fmax(1.0F, 2.0) + fmin(1.0F, 2.0);
    generic_float += frexp(8.0F, &exponent) + ldexp(0.5F, 2) + scalbn(0.5F, 2) + scalbln(0.5F, 2L);
    generic_float += modf(3.25F, &integral_float) + (float)ilogb(8.0F);
    generic_real += (double)lrint(1.25F) + (double)llrint(1.25) + (double)lround(1.25F) + (double)llround(1.25L);
    generic_float += nexttoward(1.0F, 2.0L) + remquo(5.0F, 2.0F, &quotient) + fma(1.0F, 2.0F, 3.0F);
    generic_complex = sin(generic_input) + cos(generic_input) + tan(generic_input);
    if (!isfinite(crealf(generic_complex)) || !isfinite(cimagf(generic_complex))) return 84;
    generic_complex += asin(generic_input) + acos(generic_input) + atan(generic_input);
    if (!isfinite(crealf(generic_complex)) || !isfinite(cimagf(generic_complex))) return 85;
    generic_complex += sinh(generic_input) + cosh(generic_input) + tanh(generic_input);
    if (!isfinite(crealf(generic_complex)) || !isfinite(cimagf(generic_complex))) return 86;
    generic_complex += asinh(generic_input) + acosh(generic_input) + atanh(generic_input);
    if (!isfinite(crealf(generic_complex)) || !isfinite(cimagf(generic_complex))) return 87;
    generic_complex += exp(generic_input) + log(generic_input) + sqrt(generic_input);
    if (!isfinite(crealf(generic_complex)) || !isfinite(cimagf(generic_complex))) return 88;
    generic_complex += pow(generic_input, CMPLXF(2.0F, 0.0F)) + conj(generic_input) + cproj(generic_input);
    generic_extended = carg(complex_value) + cimag(complex_value) + creal(complex_value) + cabs(complex_value);
    (void)integral_double;
    (void)integral_extended;
    if (!isfinite(generic_float)) return 81;
    if (!isfinite(generic_real)) return 82;
    if (!isfinite(generic_extended)) return 83;
    if (!isfinite(crealf(generic_complex))) return 84;
    if (!isfinite(cimagf(generic_complex))) return 85;
    return 0;
}
