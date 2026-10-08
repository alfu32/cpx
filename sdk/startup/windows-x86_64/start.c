int __cplus_windows_start(void);
int platform_process_exit(int status);

void mainCRTStartup(void) {
    int status = __cplus_windows_start();
    platform_process_exit(status);
    for (;;) { }
}
