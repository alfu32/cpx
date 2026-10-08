#include "cplus_platform.h"

/* Keep public thread operations independent of native thread handle types. */
long long std_thread_create(cplus_thread_entry_t entry, void* context) {
    return platform_thread_create(entry, context);
}

int std_thread_join(long long thread, void** result) {
    return platform_thread_join(thread, result);
}

long long std_thread_current_id(void) {
    return platform_thread_current_id();
}

int std_thread_yield(void) {
    return platform_thread_yield();
}
