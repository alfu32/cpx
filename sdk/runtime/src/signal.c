typedef void (*__cplus_signal_handler)(int);
static __cplus_signal_handler __cplus_handlers[32];

__cplus_signal_handler signal(int signal_number, __cplus_signal_handler handler) {
    __cplus_signal_handler previous;
    if (signal_number < 0 || signal_number >= 32) return (__cplus_signal_handler)-1;
    previous = __cplus_handlers[signal_number];
    __cplus_handlers[signal_number] = handler;
    return previous;
}

int raise(int signal_number) {
    if (signal_number < 0 || signal_number >= 32 || !__cplus_handlers[signal_number]) return signal_number;
    __cplus_handlers[signal_number](signal_number);
    return 0;
}
