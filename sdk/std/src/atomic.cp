/// Four-byte integer atomics with explicit memory order and PAL wait/wake.
pub enum std_memory_order_t {
    STD_MEMORY_ORDER_RELAXED = 0,
    STD_MEMORY_ORDER_CONSUME = 1,
    STD_MEMORY_ORDER_ACQUIRE = 2,
    STD_MEMORY_ORDER_RELEASE = 3,
    STD_MEMORY_ORDER_ACQ_REL = 4,
    STD_MEMORY_ORDER_SEQ_CST = 5
};

pub struct std_atomic_int_t {
    volatile int value;
};

pub int std_atomic_init_int(std_atomic_int_t* object, int value);
pub int std_atomic_load_int(std_atomic_int_t* object, std_memory_order_t order, int* result);
pub int std_atomic_store_int(std_atomic_int_t* object, int value, std_memory_order_t order);
pub int std_atomic_exchange_int(
    std_atomic_int_t* object,
    int value,
    std_memory_order_t order,
    int* previous
);
pub int std_atomic_compare_exchange_int(
    std_atomic_int_t* object,
    int* expected,
    int desired,
    std_memory_order_t order,
    int* exchanged
);
pub int std_atomic_fetch_add_int(
    std_atomic_int_t* object,
    int value,
    std_memory_order_t order,
    int* previous
);
pub int std_atomic_fetch_sub_int(
    std_atomic_int_t* object,
    int value,
    std_memory_order_t order,
    int* previous
);
pub int std_atomic_fetch_and_int(
    std_atomic_int_t* object,
    int value,
    std_memory_order_t order,
    int* previous
);
pub int std_atomic_fetch_or_int(
    std_atomic_int_t* object,
    int value,
    std_memory_order_t order,
    int* previous
);
pub int std_atomic_fetch_xor_int(
    std_atomic_int_t* object,
    int value,
    std_memory_order_t order,
    int* previous
);
pub int std_atomic_thread_fence(std_memory_order_t order);
pub int std_atomic_wait_int(
    std_atomic_int_t* object,
    int expected,
    std_memory_order_t order
);
pub int std_atomic_wake_int(std_atomic_int_t* object, unsigned int count);
