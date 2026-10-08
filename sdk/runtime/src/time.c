#include "cplus_platform.h"

typedef long long time_t;
typedef long long clock_t;
typedef struct std_duration_t {
    long long nanoseconds;
} std_duration_t;

#define __CPLUS_TIME_INVALID_ARGUMENT 1
#define __CPLUS_TIME_OVERFLOW 2
#define __CPLUS_TIME_MAX 9223372036854775807LL
#define __CPLUS_TIME_MIN (-9223372036854775807LL - 1LL)

_Static_assert(sizeof(std_duration_t) == 8, "std.duration ABI width");

long long std_time_wall_nanoseconds(void) {
    return platform_clock_wall_nanoseconds();
}

long long std_time_monotonic_nanoseconds(void) {
    return platform_clock_monotonic_nanoseconds();
}

long long std_time_process_cpu_nanoseconds(void) {
    return platform_clock_process_cpu_nanoseconds();
}

int std_time_status_invalid_argument(void) {
    return __CPLUS_TIME_INVALID_ARGUMENT;
}

int std_time_status_overflow(void) {
    return __CPLUS_TIME_OVERFLOW;
}

int std_time_duration_from_nanoseconds(long long nanoseconds, std_duration_t* result) {
    if (!result) return __CPLUS_TIME_INVALID_ARGUMENT;
    result->nanoseconds = nanoseconds;
    return 0;
}

static int __cplus_time_duration_from_scaled_value(
    long long value,
    long long scale,
    std_duration_t* result) {
    if (!result) return __CPLUS_TIME_INVALID_ARGUMENT;
    if (value > __CPLUS_TIME_MAX / scale || value < __CPLUS_TIME_MIN / scale) {
        return __CPLUS_TIME_OVERFLOW;
    }
    result->nanoseconds = value * scale;
    return 0;
}

int std_time_duration_from_milliseconds(long long milliseconds, std_duration_t* result) {
    return __cplus_time_duration_from_scaled_value(milliseconds, 1000000LL, result);
}

int std_time_duration_from_seconds(long long seconds, std_duration_t* result) {
    return __cplus_time_duration_from_scaled_value(seconds, 1000000000LL, result);
}

int std_time_duration_add(
    const std_duration_t* left,
    const std_duration_t* right,
    std_duration_t* result) {
    long long a;
    long long b;
    if (!left || !right || !result) return __CPLUS_TIME_INVALID_ARGUMENT;
    a = left->nanoseconds;
    b = right->nanoseconds;
    if ((b > 0 && a > __CPLUS_TIME_MAX - b) ||
        (b < 0 && a < __CPLUS_TIME_MIN - b)) return __CPLUS_TIME_OVERFLOW;
    result->nanoseconds = a + b;
    return 0;
}

int std_time_duration_subtract(
    const std_duration_t* left,
    const std_duration_t* right,
    std_duration_t* result) {
    long long a;
    long long b;
    if (!left || !right || !result) return __CPLUS_TIME_INVALID_ARGUMENT;
    a = left->nanoseconds;
    b = right->nanoseconds;
    if ((b < 0 && a > __CPLUS_TIME_MAX + b) ||
        (b > 0 && a < __CPLUS_TIME_MIN + b)) return __CPLUS_TIME_OVERFLOW;
    result->nanoseconds = a - b;
    return 0;
}

int std_time_duration_compare(
    const std_duration_t* left,
    const std_duration_t* right,
    int* ordering) {
    if (!left || !right || !ordering) return __CPLUS_TIME_INVALID_ARGUMENT;
    *ordering = left->nanoseconds < right->nanoseconds ? -1
        : left->nanoseconds > right->nanoseconds ? 1 : 0;
    return 0;
}

int std_time_duration_get_nanoseconds(const std_duration_t* duration, long long* nanoseconds) {
    if (!duration || !nanoseconds) return __CPLUS_TIME_INVALID_ARGUMENT;
    *nanoseconds = duration->nanoseconds;
    return 0;
}

time_t time(time_t* result) {
    long long nanoseconds = platform_clock_wall_nanoseconds();
    time_t seconds = nanoseconds < 0 ? (time_t)-1 : (time_t)(nanoseconds / 1000000000LL);
    if (result) *result = seconds;
    return seconds;
}

clock_t clock(void) {
    long long nanoseconds = platform_clock_process_cpu_nanoseconds();
    return nanoseconds < 0 ? (clock_t)-1 : (clock_t)nanoseconds;
}
