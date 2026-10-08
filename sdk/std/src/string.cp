/// C-independent byte-string helpers. Strings are UTF-8 byte sequences.
import { usize } from std.core;

pub usize std_string_length(const char* text) {
    usize length = 0;
    while (text[length] != 0) length = length + 1;
    return length;
}

pub int std_string_equal(const char* left, const char* right) {
    usize index = 0;
    while (left[index] != 0 && right[index] != 0) {
        if (left[index] != right[index]) return 0;
        index = index + 1;
    }
    return left[index] == right[index];
}

pub int std_string_compare(const char* left, const char* right) {
    usize index = 0;
    unsigned char* a = (unsigned char*) left;
    unsigned char* b = (unsigned char*) right;
    while (a[index] != 0 && a[index] == b[index]) index = index + 1;
    if (a[index] < b[index]) return -1;
    if (a[index] > b[index]) return 1;
    return 0;
}

pub char* std_string_copy(char* destination, const char* source) {
    usize index = 0;
    while ((destination[index] = source[index]) != 0) index = index + 1;
    return destination;
}

pub char* std_string_append(char* destination, const char* source) {
    usize offset = std_string_length(destination);
    std_string_copy(destination + offset, source);
    return destination;
}
