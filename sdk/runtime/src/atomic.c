#include "cplus_platform.h"

_Static_assert(sizeof(int) == 4, "std.atomic integer requires a 32-bit int");

typedef struct cplus_atomic_int {
    volatile int value;
} cplus_atomic_int;

enum {
    CPLUS_MEMORY_ORDER_RELAXED = 0,
    CPLUS_MEMORY_ORDER_CONSUME = 1,
    CPLUS_MEMORY_ORDER_ACQUIRE = 2,
    CPLUS_MEMORY_ORDER_RELEASE = 3,
    CPLUS_MEMORY_ORDER_ACQ_REL = 4,
    CPLUS_MEMORY_ORDER_SEQ_CST = 5
};

static int cplus_atomic_address_is_valid(const void* address) {
    return address && (((unsigned long long)address & 3ULL) == 0);
}

static int cplus_atomic_load_order_is_valid(int order) {
    return order == CPLUS_MEMORY_ORDER_RELAXED ||
        order == CPLUS_MEMORY_ORDER_CONSUME ||
        order == CPLUS_MEMORY_ORDER_ACQUIRE ||
        order == CPLUS_MEMORY_ORDER_SEQ_CST;
}

static int cplus_atomic_store_order_is_valid(int order) {
    return order == CPLUS_MEMORY_ORDER_RELAXED ||
        order == CPLUS_MEMORY_ORDER_RELEASE ||
        order == CPLUS_MEMORY_ORDER_SEQ_CST;
}

static int cplus_atomic_rmw_order_is_valid(int order) {
    return order >= CPLUS_MEMORY_ORDER_RELAXED &&
        order <= CPLUS_MEMORY_ORDER_SEQ_CST;
}

int std_atomic_init_int(void* opaque, int value) {
    cplus_atomic_int* object = (cplus_atomic_int*)opaque;
    if (!cplus_atomic_address_is_valid(object)) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    __atomic_store_n(&object->value, value, __ATOMIC_RELAXED);
    return 0;
}

int std_atomic_load_int(void* opaque, int order, int* result) {
    cplus_atomic_int* object = (cplus_atomic_int*)opaque;
    int value;
    if (!cplus_atomic_address_is_valid(object) || !result || !cplus_atomic_load_order_is_valid(order)) {
        return (int)CPLUS_PAL_INVALID_ARGUMENT;
    }
    switch (order) {
        case CPLUS_MEMORY_ORDER_RELAXED: value = __atomic_load_n(&object->value, __ATOMIC_RELAXED); break;
        case CPLUS_MEMORY_ORDER_CONSUME: value = __atomic_load_n(&object->value, __ATOMIC_CONSUME); break;
        case CPLUS_MEMORY_ORDER_ACQUIRE: value = __atomic_load_n(&object->value, __ATOMIC_ACQUIRE); break;
        default: value = __atomic_load_n(&object->value, __ATOMIC_SEQ_CST); break;
    }
    *result = value;
    return 0;
}

int std_atomic_store_int(void* opaque, int value, int order) {
    cplus_atomic_int* object = (cplus_atomic_int*)opaque;
    if (!cplus_atomic_address_is_valid(object) || !cplus_atomic_store_order_is_valid(order)) {
        return (int)CPLUS_PAL_INVALID_ARGUMENT;
    }
    switch (order) {
        case CPLUS_MEMORY_ORDER_RELAXED: __atomic_store_n(&object->value, value, __ATOMIC_RELAXED); break;
        case CPLUS_MEMORY_ORDER_RELEASE: __atomic_store_n(&object->value, value, __ATOMIC_RELEASE); break;
        default: __atomic_store_n(&object->value, value, __ATOMIC_SEQ_CST); break;
    }
    return 0;
}

int std_atomic_exchange_int(void* opaque, int value, int order, int* previous) {
    cplus_atomic_int* object = (cplus_atomic_int*)opaque;
    int old_value;
    if (!cplus_atomic_address_is_valid(object) || !previous || !cplus_atomic_rmw_order_is_valid(order)) {
        return (int)CPLUS_PAL_INVALID_ARGUMENT;
    }
    switch (order) {
        case CPLUS_MEMORY_ORDER_RELAXED: old_value = __atomic_exchange_n(&object->value, value, __ATOMIC_RELAXED); break;
        case CPLUS_MEMORY_ORDER_CONSUME: old_value = __atomic_exchange_n(&object->value, value, __ATOMIC_CONSUME); break;
        case CPLUS_MEMORY_ORDER_ACQUIRE: old_value = __atomic_exchange_n(&object->value, value, __ATOMIC_ACQUIRE); break;
        case CPLUS_MEMORY_ORDER_RELEASE: old_value = __atomic_exchange_n(&object->value, value, __ATOMIC_RELEASE); break;
        case CPLUS_MEMORY_ORDER_ACQ_REL: old_value = __atomic_exchange_n(&object->value, value, __ATOMIC_ACQ_REL); break;
        default: old_value = __atomic_exchange_n(&object->value, value, __ATOMIC_SEQ_CST); break;
    }
    *previous = old_value;
    return 0;
}

int std_atomic_compare_exchange_int(
    void* opaque,
    int* expected,
    int desired,
    int order,
    int* exchanged) {
    cplus_atomic_int* object = (cplus_atomic_int*)opaque;
    int success;
    if (!cplus_atomic_address_is_valid(object) || !expected || !exchanged ||
        !cplus_atomic_rmw_order_is_valid(order)) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    switch (order) {
        case CPLUS_MEMORY_ORDER_RELAXED:
            success = __atomic_compare_exchange_n(&object->value, expected, desired, 0, __ATOMIC_RELAXED, __ATOMIC_RELAXED);
            break;
        case CPLUS_MEMORY_ORDER_CONSUME:
            success = __atomic_compare_exchange_n(&object->value, expected, desired, 0, __ATOMIC_CONSUME, __ATOMIC_CONSUME);
            break;
        case CPLUS_MEMORY_ORDER_ACQUIRE:
            success = __atomic_compare_exchange_n(&object->value, expected, desired, 0, __ATOMIC_ACQUIRE, __ATOMIC_ACQUIRE);
            break;
        case CPLUS_MEMORY_ORDER_RELEASE:
            success = __atomic_compare_exchange_n(&object->value, expected, desired, 0, __ATOMIC_RELEASE, __ATOMIC_RELAXED);
            break;
        case CPLUS_MEMORY_ORDER_ACQ_REL:
            success = __atomic_compare_exchange_n(&object->value, expected, desired, 0, __ATOMIC_ACQ_REL, __ATOMIC_ACQUIRE);
            break;
        default:
            success = __atomic_compare_exchange_n(&object->value, expected, desired, 0, __ATOMIC_SEQ_CST, __ATOMIC_SEQ_CST);
            break;
    }
    *exchanged = success;
    return 0;
}

int std_atomic_fetch_add_int(void* opaque, int value, int order, int* previous) {
    cplus_atomic_int* object = (cplus_atomic_int*)opaque;
    int old_value;
    if (!cplus_atomic_address_is_valid(object) || !previous || !cplus_atomic_rmw_order_is_valid(order)) {
        return (int)CPLUS_PAL_INVALID_ARGUMENT;
    }
    switch (order) {
        case CPLUS_MEMORY_ORDER_RELAXED: old_value = __atomic_fetch_add(&object->value, value, __ATOMIC_RELAXED); break;
        case CPLUS_MEMORY_ORDER_CONSUME: old_value = __atomic_fetch_add(&object->value, value, __ATOMIC_CONSUME); break;
        case CPLUS_MEMORY_ORDER_ACQUIRE: old_value = __atomic_fetch_add(&object->value, value, __ATOMIC_ACQUIRE); break;
        case CPLUS_MEMORY_ORDER_RELEASE: old_value = __atomic_fetch_add(&object->value, value, __ATOMIC_RELEASE); break;
        case CPLUS_MEMORY_ORDER_ACQ_REL: old_value = __atomic_fetch_add(&object->value, value, __ATOMIC_ACQ_REL); break;
        default: old_value = __atomic_fetch_add(&object->value, value, __ATOMIC_SEQ_CST); break;
    }
    *previous = old_value;
    return 0;
}

int std_atomic_fetch_sub_int(void* opaque, int value, int order, int* previous) {
    cplus_atomic_int* object = (cplus_atomic_int*)opaque;
    int old_value;
    if (!cplus_atomic_address_is_valid(object) || !previous || !cplus_atomic_rmw_order_is_valid(order)) {
        return (int)CPLUS_PAL_INVALID_ARGUMENT;
    }
    switch (order) {
        case CPLUS_MEMORY_ORDER_RELAXED: old_value = __atomic_fetch_sub(&object->value, value, __ATOMIC_RELAXED); break;
        case CPLUS_MEMORY_ORDER_CONSUME: old_value = __atomic_fetch_sub(&object->value, value, __ATOMIC_CONSUME); break;
        case CPLUS_MEMORY_ORDER_ACQUIRE: old_value = __atomic_fetch_sub(&object->value, value, __ATOMIC_ACQUIRE); break;
        case CPLUS_MEMORY_ORDER_RELEASE: old_value = __atomic_fetch_sub(&object->value, value, __ATOMIC_RELEASE); break;
        case CPLUS_MEMORY_ORDER_ACQ_REL: old_value = __atomic_fetch_sub(&object->value, value, __ATOMIC_ACQ_REL); break;
        default: old_value = __atomic_fetch_sub(&object->value, value, __ATOMIC_SEQ_CST); break;
    }
    *previous = old_value;
    return 0;
}

int std_atomic_fetch_and_int(void* opaque, int value, int order, int* previous) {
    cplus_atomic_int* object = (cplus_atomic_int*)opaque;
    int old_value;
    if (!cplus_atomic_address_is_valid(object) || !previous || !cplus_atomic_rmw_order_is_valid(order)) {
        return (int)CPLUS_PAL_INVALID_ARGUMENT;
    }
    switch (order) {
        case CPLUS_MEMORY_ORDER_RELAXED: old_value = __atomic_fetch_and(&object->value, value, __ATOMIC_RELAXED); break;
        case CPLUS_MEMORY_ORDER_CONSUME: old_value = __atomic_fetch_and(&object->value, value, __ATOMIC_CONSUME); break;
        case CPLUS_MEMORY_ORDER_ACQUIRE: old_value = __atomic_fetch_and(&object->value, value, __ATOMIC_ACQUIRE); break;
        case CPLUS_MEMORY_ORDER_RELEASE: old_value = __atomic_fetch_and(&object->value, value, __ATOMIC_RELEASE); break;
        case CPLUS_MEMORY_ORDER_ACQ_REL: old_value = __atomic_fetch_and(&object->value, value, __ATOMIC_ACQ_REL); break;
        default: old_value = __atomic_fetch_and(&object->value, value, __ATOMIC_SEQ_CST); break;
    }
    *previous = old_value;
    return 0;
}

int std_atomic_fetch_or_int(void* opaque, int value, int order, int* previous) {
    cplus_atomic_int* object = (cplus_atomic_int*)opaque;
    int old_value;
    if (!cplus_atomic_address_is_valid(object) || !previous || !cplus_atomic_rmw_order_is_valid(order)) {
        return (int)CPLUS_PAL_INVALID_ARGUMENT;
    }
    switch (order) {
        case CPLUS_MEMORY_ORDER_RELAXED: old_value = __atomic_fetch_or(&object->value, value, __ATOMIC_RELAXED); break;
        case CPLUS_MEMORY_ORDER_CONSUME: old_value = __atomic_fetch_or(&object->value, value, __ATOMIC_CONSUME); break;
        case CPLUS_MEMORY_ORDER_ACQUIRE: old_value = __atomic_fetch_or(&object->value, value, __ATOMIC_ACQUIRE); break;
        case CPLUS_MEMORY_ORDER_RELEASE: old_value = __atomic_fetch_or(&object->value, value, __ATOMIC_RELEASE); break;
        case CPLUS_MEMORY_ORDER_ACQ_REL: old_value = __atomic_fetch_or(&object->value, value, __ATOMIC_ACQ_REL); break;
        default: old_value = __atomic_fetch_or(&object->value, value, __ATOMIC_SEQ_CST); break;
    }
    *previous = old_value;
    return 0;
}

int std_atomic_fetch_xor_int(void* opaque, int value, int order, int* previous) {
    cplus_atomic_int* object = (cplus_atomic_int*)opaque;
    int old_value;
    if (!cplus_atomic_address_is_valid(object) || !previous || !cplus_atomic_rmw_order_is_valid(order)) {
        return (int)CPLUS_PAL_INVALID_ARGUMENT;
    }
    switch (order) {
        case CPLUS_MEMORY_ORDER_RELAXED: old_value = __atomic_fetch_xor(&object->value, value, __ATOMIC_RELAXED); break;
        case CPLUS_MEMORY_ORDER_CONSUME: old_value = __atomic_fetch_xor(&object->value, value, __ATOMIC_CONSUME); break;
        case CPLUS_MEMORY_ORDER_ACQUIRE: old_value = __atomic_fetch_xor(&object->value, value, __ATOMIC_ACQUIRE); break;
        case CPLUS_MEMORY_ORDER_RELEASE: old_value = __atomic_fetch_xor(&object->value, value, __ATOMIC_RELEASE); break;
        case CPLUS_MEMORY_ORDER_ACQ_REL: old_value = __atomic_fetch_xor(&object->value, value, __ATOMIC_ACQ_REL); break;
        default: old_value = __atomic_fetch_xor(&object->value, value, __ATOMIC_SEQ_CST); break;
    }
    *previous = old_value;
    return 0;
}

int std_atomic_thread_fence(int order) {
    switch (order) {
        case CPLUS_MEMORY_ORDER_RELAXED: __atomic_thread_fence(__ATOMIC_RELAXED); break;
        case CPLUS_MEMORY_ORDER_CONSUME: __atomic_thread_fence(__ATOMIC_CONSUME); break;
        case CPLUS_MEMORY_ORDER_ACQUIRE: __atomic_thread_fence(__ATOMIC_ACQUIRE); break;
        case CPLUS_MEMORY_ORDER_RELEASE: __atomic_thread_fence(__ATOMIC_RELEASE); break;
        case CPLUS_MEMORY_ORDER_ACQ_REL: __atomic_thread_fence(__ATOMIC_ACQ_REL); break;
        case CPLUS_MEMORY_ORDER_SEQ_CST: __atomic_thread_fence(__ATOMIC_SEQ_CST); break;
        default: return (int)CPLUS_PAL_INVALID_ARGUMENT;
    }
    return 0;
}

int std_atomic_wait_int(void* opaque, int expected, int order) {
    cplus_atomic_int* object = (cplus_atomic_int*)opaque;
    if (!cplus_atomic_address_is_valid(object) || !cplus_atomic_load_order_is_valid(order)) {
        return (int)CPLUS_PAL_INVALID_ARGUMENT;
    }
    for (;;) {
        int current;
        int wait_result;
        switch (order) {
            case CPLUS_MEMORY_ORDER_RELAXED: current = __atomic_load_n(&object->value, __ATOMIC_RELAXED); break;
            case CPLUS_MEMORY_ORDER_CONSUME: current = __atomic_load_n(&object->value, __ATOMIC_CONSUME); break;
            case CPLUS_MEMORY_ORDER_ACQUIRE: current = __atomic_load_n(&object->value, __ATOMIC_ACQUIRE); break;
            default: current = __atomic_load_n(&object->value, __ATOMIC_SEQ_CST); break;
        }
        if (current != expected) return 0;
        wait_result = platform_atomic_wait32(&object->value, expected);
        if (wait_result < 0) return wait_result;
    }
}

int std_atomic_wake_int(void* opaque, unsigned int count) {
    cplus_atomic_int* object = (cplus_atomic_int*)opaque;
    if (!cplus_atomic_address_is_valid(object)) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    return platform_atomic_wake32(&object->value, count);
}
