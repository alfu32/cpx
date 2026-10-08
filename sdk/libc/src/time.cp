extern long long platform_clock_wall_nanoseconds();
extern long long platform_clock_process_cpu_nanoseconds();

long long time(long long* result) {
    long long nanoseconds = platform_clock_wall_nanoseconds();
    if (nanoseconds < 0) {
        if (result != (long long*) 0) *result = -1;
        return -1;
    }
    long long seconds = nanoseconds / 1000000000;
    if (result != (long long*) 0) *result = seconds;
    return seconds;
}

long long clock() { return platform_clock_process_cpu_nanoseconds(); }
