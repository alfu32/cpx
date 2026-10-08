#include <complex.h>
#include <math.h>

_Static_assert(sizeof(float complex) == 2 * sizeof(float), "float complex representation");
_Static_assert(sizeof(double complex) == 2 * sizeof(double), "double complex representation");
_Static_assert(sizeof(long double complex) == 2 * sizeof(long double), "long double complex representation");
_Static_assert(_Alignof(float complex) == _Alignof(float), "float complex alignment");
_Static_assert(_Alignof(double complex) == _Alignof(double), "double complex alignment");
_Static_assert(_Alignof(long double complex) == _Alignof(long double), "long double complex alignment");

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
    return 0;
}
