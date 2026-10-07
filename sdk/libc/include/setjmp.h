#ifndef CPLUS_SDK_SETJMP_H
#define CPLUS_SDK_SETJMP_H
typedef unsigned long jmp_buf[8];
int setjmp(jmp_buf context);
void longjmp(jmp_buf context, int value);

#define CPLUS_SETJMP_LINUX_X86_64 1
#endif
