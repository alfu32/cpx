#ifndef CPLUS_SDK_SIGNAL_H
#define CPLUS_SDK_SIGNAL_H
typedef void (*signal_handler)(int signal);
signal_handler signal(int signal, signal_handler handler);
int raise(int signal);
#endif
