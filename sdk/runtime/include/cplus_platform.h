#ifndef CPLUS_PLATFORM_H
#define CPLUS_PLATFORM_H

/* Uniform PAL ABI consumed by the C+ runtime and native std/libc facades. */
long platform_write_stdout(const char* buffer, unsigned long length);
int platform_process_exit(int status);

#endif
