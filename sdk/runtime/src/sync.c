#include "cplus_platform.h"

_Static_assert(sizeof(int) == 4, "PAL synchronization state requires 32-bit int");

static int cplus_sync_word_is_valid(const volatile int* word) {
    return word && (((unsigned long long)(const void*)word & 3ULL) == 0);
}

int platform_mutex_init(volatile int* state) {
    if (!cplus_sync_word_is_valid(state)) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    __atomic_store_n(state, 0, __ATOMIC_RELAXED);
    return 0;
}

int platform_mutex_lock(volatile int* state) {
    int expected = 0;
    if (!cplus_sync_word_is_valid(state)) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    if (__atomic_compare_exchange_n(state, &expected, 1, 0, __ATOMIC_ACQUIRE, __ATOMIC_RELAXED)) return 0;
    for (;;) {
        int current = __atomic_load_n(state, __ATOMIC_RELAXED);
        int previous;
        int wait_result;
        if (current == 0) {
            expected = 0;
            if (__atomic_compare_exchange_n(state, &expected, 1, 0, __ATOMIC_ACQUIRE, __ATOMIC_RELAXED)) return 0;
            current = expected;
        }
        if (current != 1 && current != 2) return (int)CPLUS_PAL_IO_ERROR;
        previous = __atomic_exchange_n(state, 2, __ATOMIC_ACQUIRE);
        if (previous == 0) return 0;
        if (previous != 1 && previous != 2) return (int)CPLUS_PAL_IO_ERROR;
        wait_result = platform_atomic_wait32(state, 2);
        if (wait_result < 0) return wait_result;
    }
}

int platform_mutex_unlock(volatile int* state) {
    int current;
    int previous;
    if (!cplus_sync_word_is_valid(state)) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    current = __atomic_load_n(state, __ATOMIC_RELAXED);
    if (current == 0) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    if (current != 1 && current != 2) return (int)CPLUS_PAL_IO_ERROR;
    previous = __atomic_exchange_n(state, 0, __ATOMIC_RELEASE);
    if (previous == 2) return platform_atomic_wake32(state, 1);
    return previous == 1 ? 0 : (int)CPLUS_PAL_IO_ERROR;
}

int platform_condition_init(volatile int* sequence) {
    if (!cplus_sync_word_is_valid(sequence)) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    __atomic_store_n(sequence, 0, __ATOMIC_RELAXED);
    return 0;
}

int platform_condition_wait(volatile int* sequence, volatile int* mutex_state) {
    int observed;
    int result;
    int lock_result;
    int mutex_status;
    if (!cplus_sync_word_is_valid(sequence) || !cplus_sync_word_is_valid(mutex_state)) {
        return (int)CPLUS_PAL_INVALID_ARGUMENT;
    }
    mutex_status = __atomic_load_n(mutex_state, __ATOMIC_RELAXED);
    if (mutex_status != 1 && mutex_status != 2) {
        return (int)CPLUS_PAL_INVALID_ARGUMENT;
    }
    observed = __atomic_load_n(sequence, __ATOMIC_ACQUIRE);
    result = platform_mutex_unlock(mutex_state);
    if (result < 0) {
        lock_result = platform_mutex_lock(mutex_state);
        return lock_result < 0 ? lock_result : result;
    }
    while (__atomic_load_n(sequence, __ATOMIC_ACQUIRE) == observed) {
        result = platform_atomic_wait32(sequence, observed);
        if (result < 0) {
            lock_result = platform_mutex_lock(mutex_state);
            return lock_result < 0 ? lock_result : result;
        }
    }
    return platform_mutex_lock(mutex_state);
}

int platform_condition_signal(volatile int* sequence) {
    if (!cplus_sync_word_is_valid(sequence)) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    __atomic_add_fetch(sequence, 1, __ATOMIC_RELEASE);
    return platform_atomic_wake32(sequence, 1);
}

int platform_condition_broadcast(volatile int* sequence) {
    if (!cplus_sync_word_is_valid(sequence)) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    __atomic_add_fetch(sequence, 1, __ATOMIC_RELEASE);
    return platform_atomic_wake32(sequence, 0xffffffffU);
}

int platform_semaphore_init(volatile int* count, int initial_count) {
    if (!cplus_sync_word_is_valid(count) || initial_count < 0) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    __atomic_store_n(count, initial_count, __ATOMIC_RELAXED);
    return 0;
}

int platform_semaphore_wait(volatile int* count) {
    if (!cplus_sync_word_is_valid(count)) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    for (;;) {
        int available = __atomic_load_n(count, __ATOMIC_ACQUIRE);
        if (available < 0) return (int)CPLUS_PAL_IO_ERROR;
        if (available == 0) {
            int result = platform_atomic_wait32(count, 0);
            if (result < 0) return result;
            continue;
        }
        if (__atomic_compare_exchange_n(count, &available, available - 1, 0, __ATOMIC_ACQUIRE, __ATOMIC_RELAXED)) {
            return 0;
        }
    }
}

int platform_semaphore_post(volatile int* count) {
    if (!cplus_sync_word_is_valid(count)) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    for (;;) {
        int available = __atomic_load_n(count, __ATOMIC_RELAXED);
        if (available < 0) return (int)CPLUS_PAL_IO_ERROR;
        if (available == 0x7fffffff) return (int)CPLUS_PAL_INVALID_ARGUMENT;
        if (__atomic_compare_exchange_n(count, &available, available + 1, 0, __ATOMIC_RELEASE, __ATOMIC_RELAXED)) {
            return platform_atomic_wake32(count, 1);
        }
    }
}

int platform_once_init(volatile int* state) {
    if (!cplus_sync_word_is_valid(state)) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    __atomic_store_n(state, 0, __ATOMIC_RELAXED);
    return 0;
}

int platform_once_enter(volatile int* state) {
    if (!cplus_sync_word_is_valid(state)) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    for (;;) {
        int current = __atomic_load_n(state, __ATOMIC_ACQUIRE);
        if (current == 2) return 1;
        if (current == 0) {
            int expected = 0;
            if (__atomic_compare_exchange_n(state, &expected, 1, 0, __ATOMIC_ACQUIRE, __ATOMIC_RELAXED)) return 0;
            continue;
        }
        if (current != 1) return (int)CPLUS_PAL_IO_ERROR;
        {
            int result = platform_atomic_wait32(state, 1);
            if (result < 0) return result;
        }
    }
}

int platform_once_complete(volatile int* state) {
    int expected = 1;
    if (!cplus_sync_word_is_valid(state)) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    if (!__atomic_compare_exchange_n(state, &expected, 2, 0, __ATOMIC_RELEASE, __ATOMIC_RELAXED)) {
        return (int)CPLUS_PAL_INVALID_ARGUMENT;
    }
    return platform_atomic_wake32(state, 0xffffffffU);
}
