#ifndef CPLUS_SDK_SETJMP_H
#define CPLUS_SDK_SETJMP_H
#if defined(__linux__) && defined(__aarch64__)
typedef unsigned long jmp_buf[21];
#define CPLUS_SETJMP_LINUX_AARCH64 1
#elif defined(__linux__) && defined(__x86_64__)
typedef unsigned long jmp_buf[8];
#define CPLUS_SETJMP_LINUX_X86_64 1
#else
#error "C+ SDK setjmp is unavailable for this target"
#endif
int setjmp(jmp_buf context);
void longjmp(jmp_buf context, int value);
#endif
