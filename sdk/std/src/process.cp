/// Process and standard-channel façade independent of host process types.
pub long long std_process_id(void);
pub unsigned long long std_process_argument_count(void);
pub const char* std_process_argument(unsigned long long index);
pub const char* const* std_process_environment(void);
pub long long std_process_spawn(const char* executable, const char* const* arguments);
pub int std_process_wait(long long process, int* exit_status);
pub int std_process_exit(int status);
pub long long std_process_stdin_read(void* buffer, unsigned long long capacity);
pub long long std_process_stdout_write(const char* buffer, unsigned long long length);
pub long long std_process_stderr_write(const char* buffer, unsigned long long length);
