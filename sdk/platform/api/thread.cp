/// Version-four PAL thread lifecycle and runtime-managed TLS services.
import { int64_t } from c.stdint;

pub int64_t platform_thread_create(void* (*entry)(void* context), void* context);
pub int platform_thread_join(int64_t thread, void** result);
pub long long platform_thread_current_id(void);
pub int platform_thread_yield(void);
