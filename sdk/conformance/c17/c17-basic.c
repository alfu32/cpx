#include <ctype.h>
#include <locale.h>
#include <math.h>
#include <signal.h>
#include <stdatomic.h>
#include <stdio.h>
#include <time.h>
#include <wchar.h>
#include <wctype.h>

static int signal_value;

static void record_signal(int value) {
    signal_value = value;
}

int main(void) {
    atomic_int counter;
    char formatted[32];
    char text[16];
    wchar_t wide[8];

    atomic_init(&counter, 1);
    if (atomic_fetch_add(&counter, 2) != 1 || atomic_load(&counter) != 3) return 1;
    if (snprintf(formatted, sizeof(formatted), "%d:%s", 7, "ok") != 4) return 2;
    if (formatted[0] != '7' || formatted[1] != ':' || formatted[2] != 'o' || formatted[3] != 'k') return 3;
    if (sqrt(9.0) != 3.0 || fabs(-2.5) != 2.5) return 4;
    if (!isalpha('a') || !isdigit('4') || !isspace(' ') || tolower('A') != 'a') return 5;
    if (setlocale(LC_ALL, "C") == (char*)0) return 6;
    if (signal(2, record_signal) == (signal_handler)-1 || raise(2) != 0 || signal_value != 2) return 7;
    if (clock() < 0 || time((time_t*)0) < 0) return 8;
    if (mbstowcs(wide, "A\342\202\254", 8) != 2 || wide[0] != 'A' || wide[1] != 0x20ac) return 9;
    if (wcstombs(text, wide, sizeof(text)) != 4 || text[0] != 'A' || text[1] != (char)0xe2 || text[2] != (char)0x82 || text[3] != (char)0xac) return 10;
    if (!iswalpha(wide[0]) || !iswdigit('7') || !iswspace(' ')) return 11;
    return 0;
}
