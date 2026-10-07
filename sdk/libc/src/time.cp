extern long long platform_clock_ticks();

long long time(long long* result) {
    long long seconds = platform_clock_ticks() / 1000000000;
    if (result != (long long*) 0) *result = seconds;
    return seconds;
}

long long clock() { return platform_clock_ticks(); }
