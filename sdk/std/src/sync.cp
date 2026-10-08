/// Typed synchronization objects backed by one aligned 32-bit PAL state word.
pub struct std_mutex_t {
    volatile int state;
};

pub struct std_condition_t {
    volatile int sequence;
};

pub struct std_semaphore_t {
    volatile int count;
};

pub struct std_once_t {
    volatile int state;
};

pub int std_mutex_init(std_mutex_t* mutex);
pub int std_mutex_lock(std_mutex_t* mutex);
pub int std_mutex_unlock(std_mutex_t* mutex);

pub int std_condition_init(std_condition_t* condition);
pub int std_condition_wait(std_condition_t* condition, std_mutex_t* mutex);
pub int std_condition_signal(std_condition_t* condition);
pub int std_condition_broadcast(std_condition_t* condition);

pub int std_semaphore_init(std_semaphore_t* semaphore, int initial_count);
pub int std_semaphore_wait(std_semaphore_t* semaphore);
pub int std_semaphore_post(std_semaphore_t* semaphore);

pub int std_once_init(std_once_t* once);
pub int std_once_enter(std_once_t* once);
pub int std_once_complete(std_once_t* once);
