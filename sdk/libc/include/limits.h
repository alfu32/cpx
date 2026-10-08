#ifndef CPLUS_SDK_LIMITS_H
#define CPLUS_SDK_LIMITS_H
#define CHAR_BIT 8
#define INT_MAX 2147483647
#define INT_MIN (-2147483647 - 1)
#define LLONG_MAX 9223372036854775807LL
#define LLONG_MIN (-9223372036854775807LL - 1LL)
#define ULLONG_MAX 18446744073709551615ULL
#ifndef CPLUS_LONG_BITS
#if defined(__SIZEOF_LONG__)
#define CPLUS_LONG_BITS (__SIZEOF_LONG__ * CHAR_BIT)
#elif defined(_WIN32) || defined(_WIN64)
#define CPLUS_LONG_BITS 32
#else
#error cannot determine C long width for this compiler target
#endif
#endif
#if CPLUS_LONG_BITS == 32
#define LONG_MAX 2147483647L
#define LONG_MIN (-2147483647L - 1L)
#define ULONG_MAX 4294967295UL
#elif CPLUS_LONG_BITS == 64
#define LONG_MAX 9223372036854775807L
#define LONG_MIN (-9223372036854775807L - 1L)
#define ULONG_MAX 18446744073709551615UL
#else
#error unsupported C long width for this SDK
#endif
#endif
