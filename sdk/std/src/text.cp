/// Explicit UTF-8 text façade. It intentionally exposes bytes, not wchar_t.
int std_text_is_ascii(char* text) {
    long index = 0;
    while (text[index] != 0) {
        if ((unsigned) text[index] > 127) return 0;
        index = index + 1;
    }
    return 1;
}
