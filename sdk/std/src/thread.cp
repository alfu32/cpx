/// Runtime-managed thread lifecycle with opaque 64-bit handles.
pub long long std_thread_create(void* (*entry)(void* context), void* context);
pub int std_thread_join(long long thread, void** result);
pub long long std_thread_current_id(void);
pub int std_thread_yield(void);
