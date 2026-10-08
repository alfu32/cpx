#ifndef CPLUS_SDK_STDATOMIC_H
#define CPLUS_SDK_STDATOMIC_H

typedef enum {
    memory_order_relaxed = 0,
    memory_order_consume = 1,
    memory_order_acquire = 2,
    memory_order_release = 3,
    memory_order_acq_rel = 4,
    memory_order_seq_cst = 5
} memory_order;

typedef _Atomic _Bool atomic_bool;
typedef _Atomic int atomic_int;
typedef _Atomic unsigned int atomic_uint;
typedef _Atomic long atomic_long;
typedef _Atomic unsigned long atomic_ulong;
typedef _Atomic long long atomic_llong;
typedef _Atomic unsigned long long atomic_ullong;
typedef struct { atomic_bool value; } atomic_flag;

#if defined(__clang__)
/* Clang's __atomic_*_n builtins reject pointers to C _Atomic types. */
#define atomic_init(object, value) __c11_atomic_init((object), (value))
#define atomic_load_explicit(object, order) __c11_atomic_load((object), (order))
#define atomic_load(object) atomic_load_explicit((object), __ATOMIC_SEQ_CST)
#define atomic_store_explicit(object, value, order) __c11_atomic_store((object), (value), (order))
#define atomic_store(object, value) atomic_store_explicit((object), (value), __ATOMIC_SEQ_CST)
#define atomic_exchange_explicit(object, value, order) __c11_atomic_exchange((object), (value), (order))
#define atomic_exchange(object, value) atomic_exchange_explicit((object), (value), __ATOMIC_SEQ_CST)
#define atomic_fetch_add_explicit(object, value, order) __c11_atomic_fetch_add((object), (value), (order))
#define atomic_fetch_add(object, value) atomic_fetch_add_explicit((object), (value), __ATOMIC_SEQ_CST)
#define atomic_fetch_sub_explicit(object, value, order) __c11_atomic_fetch_sub((object), (value), (order))
#define atomic_fetch_sub(object, value) atomic_fetch_sub_explicit((object), (value), __ATOMIC_SEQ_CST)
#define atomic_thread_fence(order) __c11_atomic_thread_fence((order))
#define atomic_signal_fence(order) __c11_atomic_signal_fence((order))
#define atomic_flag_test_and_set_explicit(object, order) __c11_atomic_test_and_set(&(object)->value, (order))
#define atomic_flag_test_and_set(object) atomic_flag_test_and_set_explicit((object), __ATOMIC_SEQ_CST)
#define atomic_flag_clear_explicit(object, order) __c11_atomic_clear(&(object)->value, (order))
#define atomic_flag_clear(object) atomic_flag_clear_explicit((object), __ATOMIC_SEQ_CST)
#define atomic_compare_exchange_strong_explicit(object, expected, desired, success, failure) \
    __c11_atomic_compare_exchange_strong((object), (expected), (desired), (success), (failure))
#define atomic_compare_exchange_strong(object, expected, desired) \
    atomic_compare_exchange_strong_explicit((object), (expected), (desired), __ATOMIC_SEQ_CST, __ATOMIC_SEQ_CST)
#else
#define atomic_init(object, value) __atomic_store_n((object), (value), __ATOMIC_RELAXED)
#define atomic_load_explicit(object, order) __atomic_load_n((object), (order))
#define atomic_load(object) atomic_load_explicit((object), __ATOMIC_SEQ_CST)
#define atomic_store_explicit(object, value, order) __atomic_store_n((object), (value), (order))
#define atomic_store(object, value) atomic_store_explicit((object), (value), __ATOMIC_SEQ_CST)
#define atomic_exchange_explicit(object, value, order) __atomic_exchange_n((object), (value), (order))
#define atomic_exchange(object, value) atomic_exchange_explicit((object), (value), __ATOMIC_SEQ_CST)
#define atomic_fetch_add_explicit(object, value, order) __atomic_fetch_add((object), (value), (order))
#define atomic_fetch_add(object, value) atomic_fetch_add_explicit((object), (value), __ATOMIC_SEQ_CST)
#define atomic_fetch_sub_explicit(object, value, order) __atomic_fetch_sub((object), (value), (order))
#define atomic_fetch_sub(object, value) atomic_fetch_sub_explicit((object), (value), __ATOMIC_SEQ_CST)
#define atomic_thread_fence(order) __atomic_thread_fence((order))
#define atomic_signal_fence(order) __atomic_signal_fence((order))
#define atomic_flag_test_and_set_explicit(object, order) __atomic_test_and_set(&(object)->value, (order))
#define atomic_flag_test_and_set(object) atomic_flag_test_and_set_explicit((object), __ATOMIC_SEQ_CST)
#define atomic_flag_clear_explicit(object, order) __atomic_clear(&(object)->value, (order))
#define atomic_flag_clear(object) atomic_flag_clear_explicit((object), __ATOMIC_SEQ_CST)
#define atomic_compare_exchange_strong_explicit(object, expected, desired, success, failure) \
    __atomic_compare_exchange_n((object), (expected), (desired), 0, (success), (failure))
#define atomic_compare_exchange_strong(object, expected, desired) \
    atomic_compare_exchange_strong_explicit((object), (expected), (desired), __ATOMIC_SEQ_CST, __ATOMIC_SEQ_CST)
#endif

#endif
