#include "cplus_platform.h"

typedef long long time_t;
typedef long long clock_t;

time_t time(time_t* result) {
    long long ticks = platform_clock_ticks();
    time_t seconds = ticks < 0 ? (time_t)-1 : (time_t)(ticks / 1000000000LL);
    if (result) *result = seconds;
    return seconds;
}

clock_t clock(void) {
    return (clock_t)platform_clock_ticks();
}
