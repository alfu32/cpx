/// PAL process and time contracts. Process arguments are borrowed UTF-8 argv
/// entries; a null vector selects executable as argv[0].
int platform_process_exit(int status);
long long platform_process_id(void);
long long platform_process_spawn(const char* executable, const char* const* arguments);
int platform_process_wait(long long process, int* exit_status);
long long platform_clock_ticks(void);
