int __cplus_start(int argc, char** argv);
int platform_process_exit(int status);

void mainCRTStartup(void) {
    int status = __cplus_start(0, (char**)0);
    platform_process_exit(status);
    for (;;) { }
}
