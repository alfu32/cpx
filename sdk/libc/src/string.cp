/// C string compatibility façade over std memory/string primitives.
long long strlen(const char* text) {
    long long length = 0;
    while (text[length] != 0) length = length + 1;
    return length;
}

int strcmp(const char* left, const char* right) {
    long long index = 0;
    while (left[index] != 0 && left[index] == right[index]) index = index + 1;
    return (unsigned char) left[index] - (unsigned char) right[index];
}

int strncmp(const char* left, const char* right, long long size) {
    long long index = 0;
    while (index < size && left[index] != 0 && left[index] == right[index]) index = index + 1;
    if (index == size) return 0;
    return (unsigned char) left[index] - (unsigned char) right[index];
}

char* strcpy(char* destination, const char* source) {
    long long index = 0;
    while ((destination[index] = source[index]) != 0) index = index + 1;
    return destination;
}

char* strncpy(char* destination, const char* source, long long size) {
    long long index = 0;
    while (index < size && source[index] != 0) {
        destination[index] = source[index];
        index = index + 1;
    }
    while (index < size) {
        destination[index] = 0;
        index = index + 1;
    }
    return destination;
}

char* strcat(char* destination, const char* source) {
    strcpy(destination + strlen(destination), source);
    return destination;
}

char* strchr(const char* text, int value) {
    long long index = 0;
    while (text[index] != 0) {
        if ((unsigned char) text[index] == (unsigned char) value) return (char*) (text + index);
        index = index + 1;
    }
    return value == 0 ? (char*) (text + index) : (char*) 0;
}
