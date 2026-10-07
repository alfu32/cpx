#include <stddef.h>

size_t wcslen(const int* text) {
    size_t length = 0;
    while (text[length] != 0) length++;
    return length;
}

int wcscmp(const int* left, const int* right) {
    size_t index = 0;
    while (left[index] != 0 && left[index] == right[index]) index++;
    return left[index] - right[index];
}

static int cplus_utf8_width(unsigned char value) {
    if (value < 0x80) return 1;
    if ((value & 0xe0) == 0xc0) return 2;
    if ((value & 0xf0) == 0xe0) return 3;
    if ((value & 0xf8) == 0xf0) return 4;
    return 0;
}

size_t mbstowcs(int* destination, const char* source, size_t limit) {
    size_t input = 0;
    size_t output = 0;
    while (source[input] != 0) {
        unsigned char first = (unsigned char)source[input];
        int width = cplus_utf8_width(first);
        unsigned long value = 0;
        int index;
        if (width == 0) return (size_t)-1;
        if (output == limit) return output;
        if (width == 1) value = first;
        else {
            value = first & ((1 << (8 - width - 1)) - 1);
            for (index = 1; index < width; index++) {
                unsigned char part = (unsigned char)source[input + (size_t)index];
                if ((part & 0xc0) != 0x80) return (size_t)-1;
                value = (value << 6) | (part & 0x3f);
            }
        }
        destination[output++] = (int)value;
        input += (size_t)width;
    }
    if (output < limit) destination[output] = 0;
    return output;
}

size_t wcstombs(char* destination, const int* source, size_t limit) {
    size_t input = 0;
    size_t output = 0;
    while (source[input] != 0) {
        unsigned long value = (unsigned long)source[input++];
        size_t width = value < 0x80 ? 1 : value < 0x800 ? 2 : value < 0x10000 ? 3 : 4;
        if (output + width > limit) return output;
        if (width == 1) destination[output++] = (char)value;
        else if (width == 2) {
            destination[output++] = (char)(0xc0 | (value >> 6));
            destination[output++] = (char)(0x80 | (value & 0x3f));
        } else if (width == 3) {
            destination[output++] = (char)(0xe0 | (value >> 12));
            destination[output++] = (char)(0x80 | ((value >> 6) & 0x3f));
            destination[output++] = (char)(0x80 | (value & 0x3f));
        } else {
            destination[output++] = (char)(0xf0 | (value >> 18));
            destination[output++] = (char)(0x80 | ((value >> 12) & 0x3f));
            destination[output++] = (char)(0x80 | ((value >> 6) & 0x3f));
            destination[output++] = (char)(0x80 | (value & 0x3f));
        }
    }
    if (output < limit) destination[output] = 0;
    return output;
}
