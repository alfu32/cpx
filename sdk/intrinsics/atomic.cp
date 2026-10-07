/// Atomic operations are compiler-owned and lowered according to memory order.
int atomic_load_int(int* address, int order);
void atomic_store_int(int* address, int value, int order);
int atomic_compare_exchange_int(int* address, int expected, int desired, int order);
void atomic_fence(int order);
