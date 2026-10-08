#include <complex.h>

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
    return 0;
}
