#ifndef CPLUS_SDK_SETJMP_H
#define CPLUS_SDK_SETJMP_H
#if defined(__linux__) && defined(__aarch64__)
typedef unsigned long jmp_buf[21];
#define CPLUS_SETJMP_LINUX_AARCH64 1
#elif defined(__linux__) && defined(__x86_64__)
typedef unsigned long jmp_buf[8];
#define CPLUS_SETJMP_LINUX_X86_64 1
#elif defined(_WIN32) && (defined(_M_X64) || defined(__x86_64__))
typedef unsigned long long jmp_buf[32];
#define CPLUS_SETJMP_WINDOWS_X86_64 1
#else
#error "C+ SDK setjmp is unavailable for this target"
#endif
#if defined(__GNUC__) || defined(__clang__)
#define CPLUS_SETJMP_RETURNS_TWICE __attribute__((returns_twice))
#define CPLUS_LONGJMP_NORETURN __attribute__((noreturn))
#else
#define CPLUS_SETJMP_RETURNS_TWICE
#define CPLUS_LONGJMP_NORETURN
#endif
int setjmp(jmp_buf context) CPLUS_SETJMP_RETURNS_TWICE;
void longjmp(jmp_buf context, int value) CPLUS_LONGJMP_NORETURN;
#undef CPLUS_SETJMP_RETURNS_TWICE
#undef CPLUS_LONGJMP_NORETURN
#endif
