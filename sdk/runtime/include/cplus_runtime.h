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
void __cplus_flush_streams(void);
void* __cplus_memcpy(void* destination, const void* source, unsigned long size);
void* __cplus_memmove(void* destination, const void* source, unsigned long size);
void* __cplus_memset(void* destination, int value, unsigned long size);
int __cplus_memcmp(const void* left, const void* right, unsigned long size);
void* __cplus_alloc(unsigned long long size);
void* __cplus_calloc(unsigned long long count, unsigned long long size);
void* __cplus_realloc(void* value, unsigned long long size);
void* __cplus_alloc_aligned(unsigned long long alignment, unsigned long long size);
void __cplus_free(void* value);

#endif
