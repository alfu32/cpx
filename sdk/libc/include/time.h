#ifndef CPLUS_SDK_TIME_H
#define CPLUS_SDK_TIME_H
typedef long long time_t;
typedef long long clock_t;
time_t time(time_t* result);
clock_t clock(void);
#endif
