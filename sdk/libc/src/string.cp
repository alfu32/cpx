/// C string compatibility façade over std memory/string primitives.
long strlen(char* text) {
    return std_string_length(text);
}

int strcmp(char* left, char* right) {
    return std_string_equal(left, right) ? 0 : 1;
}
