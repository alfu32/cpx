#include "cplus_platform.h"

typedef long long time_t;
typedef long long clock_t;

time_t time(time_t* result) {
    long long nanoseconds = platform_clock_wall_nanoseconds();
    time_t seconds = nanoseconds < 0 ? (time_t)-1 : (time_t)(nanoseconds / 1000000000LL);
    if (result) *result = seconds;
    return seconds;
}

clock_t clock(void) {
    return (clock_t)platform_clock_process_cpu_nanoseconds();
}
