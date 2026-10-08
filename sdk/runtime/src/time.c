#include "cplus_platform.h"

typedef long long time_t;
typedef long long clock_t;
typedef struct std_duration_t {
    long long nanoseconds;
} std_duration_t;
struct std_calendar_time_t {
    long long year;
    unsigned int month;
    unsigned int day;
    unsigned int hour;
    unsigned int minute;
    unsigned int second;
    unsigned int nanosecond;
};

#define __CPLUS_TIME_INVALID_ARGUMENT 1
#define __CPLUS_TIME_OVERFLOW 2
#define __CPLUS_TIME_MAX 9223372036854775807LL
#define __CPLUS_TIME_MIN (-9223372036854775807LL - 1LL)

_Static_assert(sizeof(std_duration_t) == 8, "std.duration ABI width");

_Static_assert(sizeof(struct std_calendar_time_t) == 32, "std.calendar_time ABI width");
_Static_assert(__builtin_offsetof(struct std_calendar_time_t, nanosecond) == 28, "calendar nanosecond offset");

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

static int __cplus_time_is_gregorian_leap_year(long long year) {
    return year % 4LL == 0 && (year % 100LL != 0 || year % 400LL == 0);
}

static unsigned int __cplus_time_days_in_month(long long year, unsigned int month) {
    static const unsigned char month_days[12] = {
        31U, 28U, 31U, 30U, 31U, 30U, 31U, 31U, 30U, 31U, 30U, 31U
    };
    if (month == 2U && __cplus_time_is_gregorian_leap_year(year)) return 29U;
    return month >= 1U && month <= 12U ? month_days[month - 1U] : 0U;
}

static void __cplus_time_civil_from_days(
    long long unix_days,
    long long* year,
    unsigned int* month,
    unsigned int* day) {
    long long shifted_days = unix_days + 719468LL;
    long long era = shifted_days >= 0
        ? shifted_days / 146097LL
        : (shifted_days - 146096LL) / 146097LL;
    long long day_of_era = shifted_days - era * 146097LL;
    long long year_of_era = (day_of_era - day_of_era / 1460LL + day_of_era / 36524LL -
        day_of_era / 146096LL) / 365LL;
    long long civil_year = year_of_era + era * 400LL;
    long long day_of_year = day_of_era - (365LL * year_of_era + year_of_era / 4LL - year_of_era / 100LL);
    long long month_prime = (5LL * day_of_year + 2LL) / 153LL;
    long long civil_day = day_of_year - (153LL * month_prime + 2LL) / 5LL + 1LL;
    long long civil_month = month_prime + (month_prime < 10LL ? 3LL : -9LL);
    civil_year += civil_month <= 2LL;
    *year = civil_year;
    *month = (unsigned int)civil_month;
    *day = (unsigned int)civil_day;
}

static long long __cplus_time_days_from_civil(long long year, unsigned int month, unsigned int day) {
    long long adjusted_year = year - (month <= 2U);
    long long era = adjusted_year >= 0
        ? adjusted_year / 400LL
        : (adjusted_year - 399LL) / 400LL;
    long long year_of_era = adjusted_year - era * 400LL;
    long long month_prime = (long long)month + (month > 2U ? -3LL : 9LL);
    long long day_of_year = (153LL * month_prime + 2LL) / 5LL + (long long)day - 1LL;
    long long day_of_era = year_of_era * 365LL + year_of_era / 4LL - year_of_era / 100LL + day_of_year;
    return era * 146097LL + day_of_era - 719468LL;
}

int std_time_calendar_from_unix_timestamp(
    long long unix_seconds,
    unsigned int nanosecond,
    struct std_calendar_time_t* result) {
    struct std_calendar_time_t calendar;
    long long unix_days;
    long long seconds_of_day;
    if (!result || nanosecond >= 1000000000U) return __CPLUS_TIME_INVALID_ARGUMENT;
    unix_days = unix_seconds / 86400LL;
    seconds_of_day = unix_seconds % 86400LL;
    if (seconds_of_day < 0) {
        unix_days--;
        seconds_of_day += 86400LL;
    }
    __cplus_time_civil_from_days(unix_days, &calendar.year, &calendar.month, &calendar.day);
    calendar.hour = (unsigned int)(seconds_of_day / 3600LL);
    calendar.minute = (unsigned int)((seconds_of_day % 3600LL) / 60LL);
    calendar.second = (unsigned int)(seconds_of_day % 60LL);
    calendar.nanosecond = nanosecond;
    *result = calendar;
    return 0;
}

int std_time_calendar_to_unix_timestamp(
    const struct std_calendar_time_t* calendar,
    long long* unix_seconds,
    unsigned int* nanosecond) {
    long long unix_days;
    long long seconds_of_day;
    long long seconds;
    long long minimum_floor_day;
    long long minimum_floor_seconds;
    if (!calendar || !unix_seconds || !nanosecond) return __CPLUS_TIME_INVALID_ARGUMENT;
    if (calendar->year < -300000000000LL || calendar->year > 300000000000LL) {
        return __CPLUS_TIME_OVERFLOW;
    }
    if (calendar->month < 1U || calendar->month > 12U || calendar->day < 1U ||
        calendar->day > __cplus_time_days_in_month(calendar->year, calendar->month) ||
        calendar->hour > 23U || calendar->minute > 59U || calendar->second > 59U ||
        calendar->nanosecond >= 1000000000U) return __CPLUS_TIME_INVALID_ARGUMENT;
    unix_days = __cplus_time_days_from_civil(calendar->year, calendar->month, calendar->day);
    seconds_of_day = (long long)calendar->hour * 3600LL +
        (long long)calendar->minute * 60LL + calendar->second;
    minimum_floor_day = __CPLUS_TIME_MIN / 86400LL - 1LL;
    minimum_floor_seconds = 86400LL + __CPLUS_TIME_MIN % 86400LL;
    if (unix_days < minimum_floor_day || unix_days > __CPLUS_TIME_MAX / 86400LL) {
        return __CPLUS_TIME_OVERFLOW;
    }
    if (unix_days == minimum_floor_day) {
        if (seconds_of_day < minimum_floor_seconds) return __CPLUS_TIME_OVERFLOW;
        seconds = __CPLUS_TIME_MIN + (seconds_of_day - minimum_floor_seconds);
    } else {
        seconds = unix_days * 86400LL;
        if (seconds < __CPLUS_TIME_MIN || seconds > __CPLUS_TIME_MAX - seconds_of_day) {
            return __CPLUS_TIME_OVERFLOW;
        }
        seconds += seconds_of_day;
    }
    *unix_seconds = seconds;
    *nanosecond = calendar->nanosecond;
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
