#include <ctype.h>
#include <errno.h>
#include <locale.h>
#include <math.h>
#include <signal.h>
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
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
    char string[8] = "a";
    char overlap[8] = "abcd";
    wchar_t wide[8];
    char* end;
    unsigned char* zeroed;
    unsigned long long index;
    void* memory;
    void* aligned;

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
    memory = malloc(32);
    zeroed = (unsigned char*)calloc(8, 8);
    aligned = aligned_alloc(64, 64);
    if (!memory || !zeroed || !aligned || ((unsigned long long)aligned % 64ULL) != 0) return 12;
    for (index = 0; index < 64; index++) {
        if (zeroed[index] != 0) return 13;
        zeroed[index] = (unsigned char)index;
    }
    zeroed = (unsigned char*)realloc(zeroed, 128);
    if (!zeroed) return 14;
    for (index = 0; index < 64; index++) if (zeroed[index] != (unsigned char)index) return 15;
    errno = 0;
    if (calloc((size_t)-1, 2) != 0 || errno != ENOMEM) return 16;
    if (strcat(string, "z") != string || strcmp(string, "az") != 0 || strncmp(string, "az", 2) != 0) return 17;
    memmove(overlap + 1, overlap, 3);
    if (memcmp(overlap, "aabc", 4) != 0 || strlen(string) != 2) return 18;
    if (strtol("0x2a", &end, 0) != 42 || *end != 0) return 19;
    if (strtod("3.5", &end) != 3.5 || *end != 0) return 20;
    free(memory);
    free(zeroed);
    free(aligned);
    return 0;
}
