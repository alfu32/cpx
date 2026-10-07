#include <setjmp.h>

static jmp_buf context;

static void jump_now(void) {
    longjmp(context, 7);
}

int main(void) {
    if (setjmp(context) == 0) jump_now();
    return 0;
}
