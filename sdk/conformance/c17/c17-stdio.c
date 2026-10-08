#include <stdarg.h>
#include <stdio.h>

static int call_vprintf(const char* format, ...) {
    va_list arguments;
    int result;
    va_start(arguments, format);
    result = vprintf(format, arguments);
    va_end(arguments);
    return result;
}

static int call_vfprintf(FILE* stream, const char* format, ...) {
    va_list arguments;
    int result;
    va_start(arguments, format);
    result = vfprintf(stream, format, arguments);
    va_end(arguments);
    return result;
}

static int call_vsprintf(char* buffer, const char* format, ...) {
    va_list arguments;
    int result;
    va_start(arguments, format);
    result = vsprintf(buffer, format, arguments);
    va_end(arguments);
    return result;
}

static int call_vsnprintf(char* buffer, size_t capacity, const char* format, ...) {
    va_list arguments;
    int result;
    va_start(arguments, format);
    result = vsnprintf(buffer, capacity, format, arguments);
    va_end(arguments);
    return result;
}

int main(void) {
    char formatted[16];
    FILE* invalid_stream = (FILE*)1;

    if (fgetc(stdin) != 'A' || fgetc(stdin) != 255 || fgetc(stdin) != EOF) return 1;
    if (printf("P:%s\n", "ok") != 5) return 2;
    if (call_vprintf("V:%d\n", 8) != 4) return 3;
    if (fprintf(stderr, "F:%d\n", 9) != 4) return 4;
    if (call_vfprintf(stderr, "W:%s\n", "ok") != 5) return 5;
    if (puts("line") != 0 || fputc('>', stdout) != '>') return 6;
    if (sprintf(formatted, "%s", "S") != 1 || formatted[0] != 'S' || formatted[1] != 0) return 7;
    if (call_vsprintf(formatted, "%d", 12) != 2 || formatted[0] != '1' || formatted[1] != '2') return 8;
    if (call_vsnprintf(formatted, sizeof(formatted), "%s", "N") != 1 || formatted[0] != 'N') return 9;
    if (fputc('!', stderr) != '!') return 10;
    if (fflush(stdout) != 0 || fflush(stderr) != 0 || fflush((FILE*)0) != 0) return 11;
    if (fgetc(invalid_stream) != EOF || fputc('?', invalid_stream) != EOF) return 12;
    if (fprintf(invalid_stream, "bad") != -1 || fflush(invalid_stream) != EOF) return 13;
    return 0;
}
