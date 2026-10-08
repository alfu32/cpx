/// Uniform PAL ABI used by runtime and native SDK services.
pub long long platform_read_stdin(void* buffer, unsigned long long capacity);
pub long long platform_write_stdout(char* buffer, unsigned long long length);
pub long long platform_write_stderr(char* buffer, unsigned long long length);
pub int platform_process_exit(int status);

pub int cplus_platform_api_version() {
    return 3;
}
