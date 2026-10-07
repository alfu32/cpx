/* Small compiler-owned formatter for C+ string templates. No hosted libc. */
typedef __builtin_va_list __cplus_va_list;

static unsigned long __cplus_strlen(const char* text) {
    unsigned long size = 0;
    while (text[size] != 0) size++;
    return size;
}

static void __cplus_put(char* buffer, unsigned long* cursor, char value) {
    if (*cursor < 1023) buffer[(*cursor)++] = value;
}

static void __cplus_text(char* buffer, unsigned long* cursor, const char* text) {
    unsigned long index = 0;
    while (text[index] != 0) __cplus_put(buffer, cursor, text[index++]);
}

static void __cplus_integer(char* buffer, unsigned long* cursor, long value) {
    char digits[32];
    unsigned long count = 0;
    unsigned long magnitude = value < 0 ? (unsigned long)(-value) : (unsigned long)value;
    if (value < 0) __cplus_put(buffer, cursor, '-');
    if (magnitude == 0) digits[count++] = '0';
    while (magnitude != 0) { digits[count++] = (char)('0' + magnitude % 10); magnitude /= 10; }
    while (count > 0) __cplus_put(buffer, cursor, digits[--count]);
}

const char* __cplus_vformat(const char* format, __cplus_va_list arguments) {
    static char buffer[1024];
    unsigned long cursor = 0;
    unsigned long index = 0;
    while (format[index] != 0) {
        if (format[index] != '%') { __cplus_put(buffer, &cursor, format[index++]); continue; }
        index++;
        if (format[index] == '%') { __cplus_put(buffer, &cursor, '%'); index++; }
        else if (format[index] == 's') { __cplus_text(buffer, &cursor, __builtin_va_arg(arguments, char*)); index++; }
        else if (format[index] == 'c') { __cplus_put(buffer, &cursor, (char)__builtin_va_arg(arguments, int)); index++; }
        else if (format[index] == 'p') { __cplus_text(buffer, &cursor, "0x"); __cplus_integer(buffer, &cursor, (long)__builtin_va_arg(arguments, void*)); index++; }
        else { __cplus_integer(buffer, &cursor, (long)__builtin_va_arg(arguments, int)); index++; }
    }
    buffer[cursor] = 0;
    return buffer;
}

const char* __cplus_format(const char* format, ...) {
    __cplus_va_list arguments;
    __builtin_va_start(arguments, format);
    const char* result = __cplus_vformat(format, arguments);
    __builtin_va_end(arguments);
    return result;
}
