/// Version-four state-word synchronization and atomic wait/wake services.
/// Successful operations return zero, except once_enter returns zero for the
/// initializer and one when initialization has already completed.
pub int platform_mutex_init(volatile int* state);
pub int platform_mutex_lock(volatile int* state);
pub int platform_mutex_unlock(volatile int* state);
pub int platform_condition_init(volatile int* sequence);
pub int platform_condition_wait(volatile int* sequence, volatile int* mutex_state);
pub int platform_condition_signal(volatile int* sequence);
pub int platform_condition_broadcast(volatile int* sequence);
pub int platform_semaphore_init(volatile int* count, int initial_count);
pub int platform_semaphore_wait(volatile int* count);
pub int platform_semaphore_post(volatile int* count);
pub int platform_once_init(volatile int* state);
pub int platform_once_enter(volatile int* state);
pub int platform_once_complete(volatile int* state);
pub int platform_atomic_wait32(volatile int* address, int expected);
pub int platform_atomic_wake32(volatile int* address, unsigned int count);
