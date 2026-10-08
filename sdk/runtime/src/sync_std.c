#include "cplus_platform.h"

/* Each public synchronization object consists of its state word at offset 0. */
int std_mutex_init(void* mutex) {
    return platform_mutex_init((volatile int*)mutex);
}

int std_mutex_lock(void* mutex) {
    return platform_mutex_lock((volatile int*)mutex);
}

int std_mutex_unlock(void* mutex) {
    return platform_mutex_unlock((volatile int*)mutex);
}

int std_condition_init(void* condition) {
    return platform_condition_init((volatile int*)condition);
}

int std_condition_wait(void* condition, void* mutex) {
    return platform_condition_wait(
        (volatile int*)condition,
        (volatile int*)mutex);
}

int std_condition_signal(void* condition) {
    return platform_condition_signal((volatile int*)condition);
}

int std_condition_broadcast(void* condition) {
    return platform_condition_broadcast((volatile int*)condition);
}

int std_semaphore_init(void* semaphore, int initial_count) {
    return platform_semaphore_init((volatile int*)semaphore, initial_count);
}

int std_semaphore_wait(void* semaphore) {
    return platform_semaphore_wait((volatile int*)semaphore);
}

int std_semaphore_post(void* semaphore) {
    return platform_semaphore_post((volatile int*)semaphore);
}

int std_once_init(void* once) {
    return platform_once_init((volatile int*)once);
}

int std_once_enter(void* once) {
    return platform_once_enter((volatile int*)once);
}

int std_once_complete(void* once) {
    return platform_once_complete((volatile int*)once);
}
