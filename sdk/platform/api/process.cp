/// PAL process and time contracts. Process arguments are borrowed UTF-8 argv
/// entries; a null vector selects executable as argv[0].
int platform_process_exit(int status);
long long platform_process_id(void);
unsigned long long platform_process_argument_count(void);
const char* platform_process_argument(unsigned long long index);
const char* const* platform_process_environment(void);
long long platform_process_spawn(const char* executable, const char* const* arguments);
int platform_process_wait(long long process, int* exit_status);
long long platform_clock_ticks(void);
