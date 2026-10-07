/// Portable byte and memory operations used by std and libc facades.
pub void* std_mem_copy(void* destination, void* source, long size) {
    long index = 0;
    char* target = (char*) destination;
    char* origin = (char*) source;
    while (index < size) {
        target[index] = origin[index];
        index = index + 1;
    }
    return destination;
}

pub void* std_mem_move(void* destination, void* source, long size) {
    long index = 0;
    char* target = (char*) destination;
    char* origin = (char*) source;
    if (target < origin) {
        while (index < size) {
            target[index] = origin[index];
            index = index + 1;
        }
    } else {
        index = size;
        while (index > 0) {
            index = index - 1;
            target[index] = origin[index];
        }
    }
    return destination;
}

pub void* std_mem_set(void* destination, int value, long size) {
    long index = 0;
    char* target = (char*) destination;
    while (index < size) {
        target[index] = (char) value;
        index = index + 1;
    }
    return destination;
}

pub void* std_mem_zero(void* destination, long size) {
    return std_mem_set(destination, 0, size);
}

pub int std_mem_compare(void* left, void* right, long size) {
    long index = 0;
    unsigned char* a = (unsigned char*) left;
    unsigned char* b = (unsigned char*) right;
    while (index < size) {
        if (a[index] < b[index]) return -1;
        if (a[index] > b[index]) return 1;
        index = index + 1;
    }
    return 0;
}

pub int std_mem_equal(void* left, void* right, long size) {
    return std_mem_compare(left, right, size) == 0;
}
