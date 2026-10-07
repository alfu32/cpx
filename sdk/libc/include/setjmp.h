#ifndef CPLUS_SDK_SETJMP_H
#define CPLUS_SDK_SETJMP_H
typedef long jmp_buf[8];
int setjmp(jmp_buf context);
void longjmp(jmp_buf context, int value);
#endif
