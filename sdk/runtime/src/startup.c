typedef void (*__cplus_exit_handler)(void);

static __cplus_exit_handler __cplus_exit_handlers[32];
static __cplus_exit_handler __cplus_quick_exit_handlers[32];
static int __cplus_exit_handler_count;
static int __cplus_quick_exit_handler_count;
static int __cplus_runtime_initialized;
static int __cplus_tls_initialized;
static int __cplus_allocator_initialized;
static int __cplus_global_state_initialized;
static int __cplus_argc;
static char** __cplus_argv;

/* GCC-family Windows drivers emit this constructor hook even for C-only units.
   The C+ runtime has no global-constructor table at this layer, so the hook is
   intentionally a no-op and does not pull in a compiler runtime library. */
void __main(void) { }

void __cplus_flush_streams(void);
void platform_process_exit(int status);

int __cplus_runtime_init(int argc, char** argv) {
    __cplus_exit_handler_count = 0;
    __cplus_quick_exit_handler_count = 0;
    __cplus_argc = argc;
    __cplus_argv = argv;
    __cplus_tls_initialized = 1;
    __cplus_allocator_initialized = 1;
    __cplus_global_state_initialized = 1;
    __cplus_runtime_initialized = 1;
    return 0;
}

int __cplus_runtime_is_initialized(void) { return __cplus_runtime_initialized; }
int __cplus_runtime_argc(void) { return __cplus_argc; }
char** __cplus_runtime_argv(void) { return __cplus_argv; }
int __cplus_tls_is_initialized(void) { return __cplus_tls_initialized; }
int __cplus_allocator_is_initialized(void) { return __cplus_allocator_initialized; }
int __cplus_global_state_is_initialized(void) { return __cplus_global_state_initialized; }

int __cplus_register_exit_handler(__cplus_exit_handler handler) {
    if (!handler || __cplus_exit_handler_count >= 32) return -1;
    __cplus_exit_handlers[__cplus_exit_handler_count++] = handler;
    return 0;
}

int __cplus_register_quick_exit_handler(__cplus_exit_handler handler) {
    if (!handler || __cplus_quick_exit_handler_count >= 32) return -1;
    __cplus_quick_exit_handlers[__cplus_quick_exit_handler_count++] = handler;
    return 0;
}

void __cplus_run_exit_handlers(void) {
    while (__cplus_exit_handler_count > 0) {
        __cplus_exit_handler handler = __cplus_exit_handlers[--__cplus_exit_handler_count];
        handler();
    }
}

void __cplus_run_quick_exit_handlers(void) {
    while (__cplus_quick_exit_handler_count > 0) {
        __cplus_exit_handler handler = __cplus_quick_exit_handlers[--__cplus_quick_exit_handler_count];
        handler();
    }
}

int __cplus_terminate_normal(int status) {
    __cplus_run_exit_handlers();
    __cplus_flush_streams();
    return status;
}

int __cplus_terminate_quick(int status) {
    __cplus_run_quick_exit_handlers();
    return status;
}

int __cplus_terminate_immediate(int status) {
    return status;
}

int __cplus_abort_status(void) {
    return 134;
}

/* libc/stdio may replace this hook in a hosted SDK profile. */
#if !defined(CPLUS_RUNTIME_NO_WEAK) && defined(__GNUC__)
__attribute__((weak)) void __cplus_flush_streams(void) { }
#elif !defined(CPLUS_RUNTIME_NO_WEAK)
void __cplus_flush_streams(void) { }
#endif

extern int main(int argc, char** argv);

int __cplus_start(int argc, char** argv) {
    int status;
    if (__cplus_runtime_init(argc, argv) != 0) return 127;
    status = main(argc, argv);
    return __cplus_terminate_normal(status);
}
