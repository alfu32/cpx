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
    if (single != CMPLXF(1.25F, -2.5F)) return 1;
    if (double_value != CMPLX(3.125, -4.5)) return 2;
    if (extended != CMPLXL(5.75L, -6.875L)) return 3;
    if (I != CMPLXF(0.0F, 1.0F)) return 4;
    return 0;
}
