#ifndef CPLUS_RUNTIME_H
#define CPLUS_RUNTIME_H

typedef void (*__cplus_exit_handler)(void);

int __cplus_runtime_init(int argc, char** argv);
int __cplus_register_exit_handler(__cplus_exit_handler handler);
int __cplus_register_quick_exit_handler(__cplus_exit_handler handler);
void __cplus_run_exit_handlers(void);
void __cplus_run_quick_exit_handlers(void);
int __cplus_terminate_normal(int status);
int __cplus_terminate_quick(int status);
int __cplus_terminate_immediate(int status);
int __cplus_abort_status(void);

#endif
