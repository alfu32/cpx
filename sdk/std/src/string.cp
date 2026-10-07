/// C-independent byte-string helpers. Strings are UTF-8 byte sequences.
long std_string_length(char* text) {
    long length = 0;
    while (text[length] != 0) length = length + 1;
    return length;
}

int std_string_equal(char* left, char* right) {
    long index = 0;
    while (left[index] != 0 && right[index] != 0) {
        if (left[index] != right[index]) return 0;
        index = index + 1;
    }
    return left[index] == right[index];
}
