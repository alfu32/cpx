/// Uniform PAL ABI used by runtime and native SDK services.
pub long platform_write_stdout(char* buffer, long length);
pub int platform_process_exit(int status);

pub int cplus_platform_api_version() {
    return 2;
}
