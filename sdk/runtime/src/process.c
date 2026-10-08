#include "cplus_platform.h"

/* Native std.process remains target-independent. The selected PAL owns
   process handles, startup context, and standard-channel operations. */
long long std_process_id(void) {
    return platform_process_id();
}

unsigned long long std_process_argument_count(void) {
    return platform_process_argument_count();
}

const char* std_process_argument(unsigned long long index) {
    return platform_process_argument(index);
}

const char* const* std_process_environment(void) {
    return platform_process_environment();
}

long long std_process_spawn(const char* executable, const char* const* arguments) {
    return platform_process_spawn(executable, arguments);
}

int std_process_wait(long long process, int* exit_status) {
    return platform_process_wait(process, exit_status);
}

int std_process_exit(int status) {
    return platform_process_exit(status);
}

long long std_process_stdin_read(void* buffer, unsigned long long capacity) {
    return platform_read_stdin(buffer, capacity);
}

long long std_process_stdout_write(const char* buffer, unsigned long long length) {
    return platform_write_stdout(buffer, length);
}

long long std_process_stderr_write(const char* buffer, unsigned long long length) {
    return platform_write_stderr(buffer, length);
}
