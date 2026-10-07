/* Compiler support runtime. These helpers intentionally use no libc API. */
void* __cplus_memcpy(void* destination, const void* source, unsigned long size) {
    unsigned long index = 0;
    unsigned char* target = (unsigned char*) destination;
    const unsigned char* origin = (const unsigned char*) source;
    while (index < size) { target[index] = origin[index]; index++; }
    return destination;
}

void* __cplus_memmove(void* destination, const void* source, unsigned long size) {
    unsigned long index;
    unsigned char* target = (unsigned char*) destination;
    const unsigned char* origin = (const unsigned char*) source;
    if (target < origin) {
        index = 0;
        while (index < size) { target[index] = origin[index]; index++; }
    } else {
        index = size;
        while (index > 0) { index--; target[index] = origin[index]; }
    }
    return destination;
}

void* __cplus_memset(void* destination, int value, unsigned long size) {
    unsigned long index = 0;
    unsigned char* target = (unsigned char*) destination;
    while (index < size) { target[index] = (unsigned char)value; index++; }
    return destination;
}

int __cplus_memcmp(const void* left, const void* right, unsigned long size) {
    unsigned long index = 0;
    const unsigned char* a = (const unsigned char*) left;
    const unsigned char* b = (const unsigned char*) right;
    while (index < size) {
        if (a[index] < b[index]) return -1;
        if (a[index] > b[index]) return 1;
        index++;
    }
    return 0;
}
