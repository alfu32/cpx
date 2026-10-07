/// Explicit UTF-8 text façade. It intentionally exposes bytes, not wchar_t.
int std_text_is_ascii(char* text) {
    long index = 0;
    while (text[index] != 0) {
        if ((unsigned) text[index] > 127) return 0;
        index = index + 1;
    }
    return 1;
}

pub long std_text_byte_length(char* text) {
    long length = 0;
    while (text[length] != 0) length = length + 1;
    return length;
}

pub int std_text_is_empty(char* text) {
    return text[0] == 0;
}

pub int std_text_has_ascii_prefix(char* text, char* prefix) {
    long index = 0;
    while (prefix[index] != 0) {
        if (text[index] != prefix[index]) return 0;
        index = index + 1;
    }
    return 1;
}
