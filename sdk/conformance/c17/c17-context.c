#include <setjmp.h>

static jmp_buf context;

static void jump_now(int value) {
    longjmp(context, value);
}

int main(void) {
    volatile int resumed = setjmp(context);
    if (resumed == 0) jump_now(7);
    if (resumed != 7) return 1;

    resumed = setjmp(context);
    if (resumed == 0) jump_now(0);
    if (resumed != 1) return 2;
    return 0;
}
