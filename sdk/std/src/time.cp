/// Clock values and signed durations use nanoseconds as their common unit.
pub struct std_duration_t {
    long long nanoseconds;
};

pub long long std_time_wall_nanoseconds(void);
pub long long std_time_monotonic_nanoseconds(void);
pub long long std_time_process_cpu_nanoseconds(void);

pub int std_time_duration_from_nanoseconds(long long nanoseconds, std_duration_t* result);
pub int std_time_duration_from_milliseconds(long long milliseconds, std_duration_t* result);
pub int std_time_duration_from_seconds(long long seconds, std_duration_t* result);
pub int std_time_duration_add(
    const std_duration_t* left,
    const std_duration_t* right,
    std_duration_t* result
);
pub int std_time_duration_subtract(
    const std_duration_t* left,
    const std_duration_t* right,
    std_duration_t* result
);
pub int std_time_duration_compare(
    const std_duration_t* left,
    const std_duration_t* right,
    int* ordering
);
pub int std_time_duration_get_nanoseconds(const std_duration_t* duration, long long* nanoseconds);
pub int std_time_status_invalid_argument(void);
pub int std_time_status_overflow(void);
