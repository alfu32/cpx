void* memcpy(void* destination, const void* source, long long size) {
    long index = 0;
    unsigned char* target = (unsigned char*) destination;
    const unsigned char* origin = (const unsigned char*) source;
    while (index < size) {
        target[index] = origin[index];
        index = index + 1;
    }
    return destination;
}

void* memmove(void* destination, const void* source, long long size) {
    long long index;
    unsigned char* target = (unsigned char*) destination;
    const unsigned char* origin = (const unsigned char*) source;
    if (target < origin) {
        index = 0;
        while (index < size) {
            target[index] = origin[index];
            index = index + 1;
        }
    } else if (target > origin) {
        index = size;
        while (index > 0) {
            index = index - 1;
            target[index] = origin[index];
        }
    }
    return destination;
}

void* memset(void* destination, int value, long long size) {
    long long index = 0;
    unsigned char* target = (unsigned char*) destination;
    while (index < size) {
        target[index] = (char) value;
        index = index + 1;
    }
    return destination;
}

int memcmp(const void* left, const void* right, long long size) {
    long long index = 0;
    const unsigned char* a = (const unsigned char*) left;
    const unsigned char* b = (const unsigned char*) right;
    while (index < size) {
        if (a[index] < b[index]) return -1;
        if (a[index] > b[index]) return 1;
        index = index + 1;
    }
    return 0;
}
