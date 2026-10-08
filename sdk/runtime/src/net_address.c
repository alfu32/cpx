#include "cplus_platform.h"
#include "cplus_network_internal.h"

#define __CPLUS_NETWORK_MAX_TEXT 1024U
#define __CPLUS_NETWORK_MAX_DNS_NAME 253U
#define __CPLUS_NETWORK_MAX_DNS_LABEL 63U
#define __CPLUS_NETWORK_UINT_MAX 0xffffffffU
#define __CPLUS_NETWORK_ULL_MAX 0xffffffffffffffffULL

static unsigned int cplus_network_text_length(const char* text, unsigned int limit) {
    unsigned int length = 0;
    if (!text) return __CPLUS_NETWORK_UINT_MAX;
    while (length < limit && text[length] != '\0') length++;
    return length == limit ? __CPLUS_NETWORK_UINT_MAX : length;
}

static int cplus_network_decimal_digit(char value) {
    return value >= '0' && value <= '9' ? value - '0' : -1;
}

static int cplus_network_hex_digit(char value) {
    if (value >= '0' && value <= '9') return value - '0';
    if (value >= 'a' && value <= 'f') return value - 'a' + 10;
    if (value >= 'A' && value <= 'F') return value - 'A' + 10;
    return -1;
}

static int cplus_network_parse_ipv4_bytes(
    const char* text,
    unsigned int length,
    unsigned char address[4]) {
    unsigned int cursor = 0;
    unsigned int part;
    for (part = 0; part < 4; part++) {
        unsigned int value = 0;
        unsigned int digits = 0;
        unsigned int start = cursor;
        while (cursor < length && text[cursor] >= '0' && text[cursor] <= '9') {
            value = value * 10U + (unsigned int)(text[cursor] - '0');
            digits++;
            if (value > 255U || digits > 3U) return 0;
            cursor++;
        }
        if (digits == 0 || (digits > 1 && text[start] == '0')) return 0;
        address[part] = (unsigned char)value;
        if (part < 3) {
            if (cursor >= length || text[cursor] != '.') return 0;
            cursor++;
        } else if (cursor != length) {
            return 0;
        }
    }
    return 1;
}

static int cplus_network_parse_ipv6_bytes(
    const char* text,
    unsigned int length,
    unsigned char address[16]) {
    unsigned short words[8];
    unsigned int count = 0;
    unsigned int cursor = 0;
    int compression = -1;
    unsigned int index;
    if (length == 0) return 0;
    if (text[0] == ':') {
        if (length < 2 || text[1] != ':') return 0;
        compression = 0;
        cursor = 2;
        if (cursor == length) {
            for (index = 0; index < 16; index++) address[index] = 0;
            return 1;
        }
    }
    while (cursor < length) {
        unsigned int token_start = cursor;
        unsigned int token_end;
        unsigned int dot = __CPLUS_NETWORK_UINT_MAX;
        unsigned int token_index;
        while (cursor < length && text[cursor] != ':') {
            if (text[cursor] == '.') dot = cursor;
            cursor++;
        }
        token_end = cursor;
        if (token_end == token_start) return 0;
        if (dot != __CPLUS_NETWORK_UINT_MAX) {
            unsigned char ipv4[4];
            if (token_end != length || count > 6U ||
                !cplus_network_parse_ipv4_bytes(text + token_start, token_end - token_start, ipv4)) {
                return 0;
            }
            words[count++] = (unsigned short)(((unsigned int)ipv4[0] << 8) | ipv4[1]);
            words[count++] = (unsigned short)(((unsigned int)ipv4[2] << 8) | ipv4[3]);
            break;
        }
        if (count >= 8U || token_end - token_start > 4U) return 0;
        {
            unsigned int value = 0;
            for (token_index = token_start; token_index < token_end; token_index++) {
                int digit = cplus_network_hex_digit(text[token_index]);
                if (digit < 0) return 0;
                value = (value << 4) | (unsigned int)digit;
            }
            words[count++] = (unsigned short)value;
        }
        if (cursor == length) break;
        if (cursor + 1U < length && text[cursor + 1U] == ':') {
            if (compression >= 0) return 0;
            compression = (int)count;
            cursor += 2U;
            if (cursor == length) break;
        } else {
            cursor++;
            if (cursor == length) return 0;
        }
    }
    if (compression < 0) {
        if (count != 8U) return 0;
    } else {
        unsigned int gap;
        if (count >= 8U) return 0;
        gap = 8U - count;
        for (index = count; index > (unsigned int)compression; index--) {
            words[index + gap - 1U] = words[index - 1U];
        }
        for (index = (unsigned int)compression; index < (unsigned int)compression + gap; index++) {
            words[index] = 0;
        }
        count = 8U;
    }
    if (count != 8U) return 0;
    for (index = 0; index < 8; index++) {
        address[index * 2U] = (unsigned char)(words[index] >> 8);
        address[index * 2U + 1U] = (unsigned char)words[index];
    }
    return 1;
}

int platform_network_parse_address(
    unsigned int family,
    const char* text,
    cplus_socket_address_t* address) {
    cplus_socket_address_t parsed = {0};
    unsigned int length;
    unsigned int address_length;
    unsigned int index;
    unsigned int scope = 0;
    const char* literal;
    if (!text || !address) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    if (family != CPLUS_SOCKET_IPV4 && family != CPLUS_SOCKET_IPV6) return (int)CPLUS_PAL_UNSUPPORTED;
    length = cplus_network_text_length(text, 64U);
    if (length == __CPLUS_NETWORK_UINT_MAX || length == 0) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    literal = text;
    address_length = length;
    for (index = 0; index < length; index++) {
        if (text[index] == '%') {
            unsigned int scope_index;
            if (family != CPLUS_SOCKET_IPV6 || index == 0 || index + 1U >= length) {
                return (int)CPLUS_PAL_INVALID_ARGUMENT;
            }
            for (scope_index = index + 1U; scope_index < length; scope_index++) {
                int digit = cplus_network_decimal_digit(text[scope_index]);
                if (digit < 0 || scope > (__CPLUS_NETWORK_UINT_MAX - (unsigned int)digit) / 10U) {
                    return (int)CPLUS_PAL_INVALID_ARGUMENT;
                }
                scope = scope * 10U + (unsigned int)digit;
            }
            address_length = index;
            break;
        }
    }
    parsed.family = family;
    parsed.port = 0;
    parsed.reserved = 0;
    parsed.scope_id = scope;
    if (family == CPLUS_SOCKET_IPV4) {
        if (!cplus_network_parse_ipv4_bytes(literal, address_length, parsed.address)) {
            return (int)CPLUS_PAL_INVALID_ARGUMENT;
        }
    } else if (!cplus_network_parse_ipv6_bytes(literal, address_length, parsed.address)) {
        return (int)CPLUS_PAL_INVALID_ARGUMENT;
    }
    *address = parsed;
    return 0;
}

static void cplus_network_append_char(char output[64], unsigned int* length, char value) {
    if (*length < 63U) output[(*length)++] = value;
}

static void cplus_network_append_decimal(char output[64], unsigned int* length, unsigned int value) {
    char digits[10];
    unsigned int count = 0;
    do {
        digits[count++] = (char)('0' + value % 10U);
        value /= 10U;
    } while (value != 0 && count < 10U);
    while (count > 0) cplus_network_append_char(output, length, digits[--count]);
}

static void cplus_network_append_hex(char output[64], unsigned int* length, unsigned int value) {
    static const char digits[] = "0123456789abcdef";
    unsigned int shift = 12U;
    int started = 0;
    for (;;) {
        unsigned int digit = (value >> shift) & 15U;
        if (digit != 0 || started || shift == 0U) {
            cplus_network_append_char(output, length, digits[digit]);
            started = 1;
        }
        if (shift == 0U) break;
        shift -= 4U;
    }
}

long long platform_network_format_address(
    const cplus_socket_address_t* address,
    char* output,
    unsigned long long capacity) {
    char formatted[64];
    unsigned int length = 0;
    unsigned int index;
    if (!address || !output) return CPLUS_PAL_INVALID_ARGUMENT;
    if (address->reserved != 0) return CPLUS_PAL_INVALID_ARGUMENT;
    if (address->family == CPLUS_SOCKET_IPV4) {
        if (address->scope_id != 0) return CPLUS_PAL_INVALID_ARGUMENT;
        for (index = 4; index < 16; index++) {
            if (address->address[index] != 0) return CPLUS_PAL_INVALID_ARGUMENT;
        }
        for (index = 0; index < 4; index++) {
            if (index != 0) cplus_network_append_char(formatted, &length, '.');
            cplus_network_append_decimal(formatted, &length, address->address[index]);
        }
    } else if (address->family == CPLUS_SOCKET_IPV6) {
        unsigned short words[8];
        unsigned int best_start = 8U;
        unsigned int best_length = 0;
        index = 0;
        while (index < 8U) {
            words[index] = (unsigned short)(((unsigned int)address->address[index * 2U] << 8) |
                address->address[index * 2U + 1U]);
            index++;
        }
        if (words[0] == 0 && words[1] == 0 && words[2] == 0 && words[3] == 0 &&
            words[4] == 0 && words[5] == 0xffffU) {
            cplus_network_append_char(formatted, &length, ':');
            cplus_network_append_char(formatted, &length, ':');
            cplus_network_append_hex(formatted, &length, 0xffffU);
            cplus_network_append_char(formatted, &length, ':');
            for (index = 12; index < 16; index++) {
                if (index != 12) cplus_network_append_char(formatted, &length, '.');
                cplus_network_append_decimal(formatted, &length, address->address[index]);
            }
        } else {
            index = 0;
            while (index < 8U) {
                unsigned int start;
                unsigned int run_length;
                if (words[index] != 0) {
                    index++;
                    continue;
                }
                start = index;
                while (index < 8U && words[index] == 0) index++;
                run_length = index - start;
                if (run_length >= 2U && run_length > best_length) {
                    best_start = start;
                    best_length = run_length;
                }
            }
            index = 0;
            while (index < 8U) {
                if (index == best_start) {
                    cplus_network_append_char(formatted, &length, ':');
                    cplus_network_append_char(formatted, &length, ':');
                    index += best_length;
                    continue;
                }
                if (length > 0 && formatted[length - 1U] != ':') {
                    cplus_network_append_char(formatted, &length, ':');
                }
                cplus_network_append_hex(formatted, &length, words[index]);
                index++;
            }
        }
        if (address->scope_id != 0) {
            cplus_network_append_char(formatted, &length, '%');
            cplus_network_append_decimal(formatted, &length, address->scope_id);
        }
    } else {
        return CPLUS_PAL_UNSUPPORTED;
    }
    if (capacity <= (unsigned long long)length) return CPLUS_PAL_BUFFER_TOO_SMALL;
    for (index = 0; index < length; index++) output[index] = formatted[index];
    output[length] = '\0';
    return (long long)length;
}

static int cplus_network_utf8_decode(
    const unsigned char* input,
    unsigned int length,
    unsigned int* cursor,
    unsigned int* codepoint) {
    unsigned int index = *cursor;
    unsigned char first;
    unsigned int value;
    unsigned int continuation;
    if (index >= length) return 0;
    first = input[index++];
    if (first < 0x80U) {
        *codepoint = first;
        *cursor = index;
        return 1;
    }
    if (first >= 0xc2U && first <= 0xdfU) {
        value = first & 0x1fU;
        continuation = 1U;
    } else if (first >= 0xe0U && first <= 0xefU) {
        value = first & 0x0fU;
        continuation = 2U;
    } else if (first >= 0xf0U && first <= 0xf4U) {
        value = first & 0x07U;
        continuation = 3U;
    } else {
        return 0;
    }
    if (continuation > length - index) return 0;
    while (continuation > 0) {
        unsigned char next = input[index++];
        if ((next & 0xc0U) != 0x80U) return 0;
        value = (value << 6) | (next & 0x3fU);
        continuation--;
    }
    if ((first <= 0xdfU && value < 0x80U) ||
        (first >= 0xe0U && first <= 0xefU && value < 0x800U) ||
        (first >= 0xf0U && value < 0x10000U) ||
        (value >= 0xd800U && value <= 0xdfffU) || value > 0x10ffffU) {
        return 0;
    }
    *codepoint = value;
    *cursor = index;
    return 1;
}

static char cplus_network_punycode_digit(unsigned int value) {
    return value < 26U ? (char)('a' + value) : (char)('0' + value - 26U);
}

static unsigned int cplus_network_punycode_adapt(
    unsigned long long delta,
    unsigned int points,
    int first_time) {
    unsigned int bias = 0;
    unsigned long long quotient;
    delta = first_time ? delta / 700U : delta / 2U;
    delta += delta / points;
    quotient = delta;
    while (quotient > 455U) {
        quotient /= 35U;
        bias += 36U;
    }
    return bias + (unsigned int)((36U * quotient) / (quotient + 38U));
}

static int cplus_network_encode_label(
    const unsigned int* codepoints,
    unsigned int count,
    char output[64],
    unsigned int* output_length) {
    unsigned int basic = 0;
    unsigned int index;
    unsigned int length = 0;
    int non_ascii = 0;
    if (count == 0 || count > 253U) return 0;
    if (codepoints[0] == '-' || codepoints[count - 1U] == '-') return 0;
    for (index = 0; index < count; index++) {
        unsigned int codepoint = codepoints[index];
        if (codepoint < 0x80U) {
            char value = (char)codepoint;
            if (!((value >= 'a' && value <= 'z') || (value >= 'A' && value <= 'Z') ||
                  (value >= '0' && value <= '9') || value == '-')) return 0;
            basic++;
        } else {
            non_ascii = 1;
        }
    }
    if (!non_ascii) {
        if (count > __CPLUS_NETWORK_MAX_DNS_LABEL) return 0;
        for (index = 0; index < count; index++) {
            char value = (char)codepoints[index];
            output[index] = value >= 'A' && value <= 'Z' ? (char)(value + ('a' - 'A')) : value;
        }
        *output_length = count;
        return 1;
    }

    output[length++] = 'x';
    output[length++] = 'n';
    output[length++] = '-';
    output[length++] = '-';
    for (index = 0; index < count; index++) {
        if (codepoints[index] < 0x80U) {
            char value = (char)codepoints[index];
            if (length >= __CPLUS_NETWORK_MAX_DNS_LABEL) return 0;
            output[length++] = value >= 'A' && value <= 'Z' ? (char)(value + ('a' - 'A')) : value;
        }
    }
    if (basic > 0) {
        if (length >= __CPLUS_NETWORK_MAX_DNS_LABEL) return 0;
        output[length++] = '-';
    }
    {
        unsigned int handled = basic;
        unsigned int n = 128U;
        unsigned int bias = 72U;
        unsigned long long delta = 0;
        while (handled < count) {
            unsigned int minimum = __CPLUS_NETWORK_UINT_MAX;
            unsigned int codepoint_index;
            for (codepoint_index = 0; codepoint_index < count; codepoint_index++) {
                if (codepoints[codepoint_index] >= n && codepoints[codepoint_index] < minimum) {
                    minimum = codepoints[codepoint_index];
                }
            }
            if (minimum == __CPLUS_NETWORK_UINT_MAX ||
                (unsigned long long)(minimum - n) > (__CPLUS_NETWORK_ULL_MAX - delta) / (handled + 1U)) {
                return 0;
            }
            delta += (unsigned long long)(minimum - n) * (handled + 1U);
            n = minimum;
            for (codepoint_index = 0; codepoint_index < count; codepoint_index++) {
                unsigned int value = codepoints[codepoint_index];
                if (value < n) {
                    if (delta == __CPLUS_NETWORK_ULL_MAX) return 0;
                    delta++;
                } else if (value == n) {
                    unsigned long long quotient = delta;
                    unsigned int k = 36U;
                    for (;;) {
                        unsigned int threshold;
                        unsigned int digit;
                        if (k <= bias) threshold = 1U;
                        else if (k >= bias + 26U) threshold = 26U;
                        else threshold = k - bias;
                        if (quotient < threshold) break;
                        digit = threshold + (unsigned int)((quotient - threshold) % (36U - threshold));
                        if (length >= __CPLUS_NETWORK_MAX_DNS_LABEL) return 0;
                        output[length++] = cplus_network_punycode_digit(digit);
                        quotient = (quotient - threshold) / (36U - threshold);
                        if (k > __CPLUS_NETWORK_UINT_MAX - 36U) return 0;
                        k += 36U;
                    }
                    if (length >= __CPLUS_NETWORK_MAX_DNS_LABEL) return 0;
                    output[length++] = cplus_network_punycode_digit((unsigned int)quotient);
                    bias = cplus_network_punycode_adapt(delta, handled + 1U, handled == basic);
                    delta = 0;
                    handled++;
                }
            }
            if (delta == __CPLUS_NETWORK_ULL_MAX || n == __CPLUS_NETWORK_UINT_MAX) return 0;
            delta++;
            n++;
        }
    }
    if (length > __CPLUS_NETWORK_MAX_DNS_LABEL) return 0;
    *output_length = length;
    return 1;
}

long long __cplus_network_hostname_to_ascii(
    const char* hostname,
    char* output,
    unsigned long long capacity) {
    char ascii_name[__CPLUS_NETWORK_MAX_DNS_NAME + 1U];
    unsigned int name_length;
    unsigned int effective_length;
    unsigned int cursor = 0;
    unsigned int output_length = 0;
    if (!hostname || !output) return CPLUS_PAL_INVALID_ARGUMENT;
    name_length = cplus_network_text_length(hostname, __CPLUS_NETWORK_MAX_TEXT);
    if (name_length == __CPLUS_NETWORK_UINT_MAX || name_length == 0) return CPLUS_PAL_INVALID_ARGUMENT;
    effective_length = name_length;
    if (hostname[effective_length - 1U] == '.') effective_length--;
    if (effective_length == 0 || (effective_length > 0 && hostname[effective_length - 1U] == '.')) {
        return CPLUS_PAL_INVALID_ARGUMENT;
    }
    while (cursor < effective_length) {
        unsigned int codepoints[256];
        unsigned int codepoint_count = 0;
        unsigned int label_start = cursor;
        unsigned int label_end;
        unsigned int label_output_length = 0;
        char encoded_label[64];
        while (cursor < effective_length && hostname[cursor] != '.') cursor++;
        label_end = cursor;
        {
            unsigned int decoder = label_start;
            while (decoder < label_end) {
                unsigned int codepoint;
                if (codepoint_count >= 256U ||
                    !cplus_network_utf8_decode((const unsigned char*)hostname, label_end, &decoder, &codepoint)) {
                    return CPLUS_PAL_INVALID_ARGUMENT;
                }
                codepoints[codepoint_count++] = codepoint;
            }
        }
        if (!cplus_network_encode_label(codepoints, codepoint_count, encoded_label, &label_output_length) ||
            output_length + (output_length > 0 ? 1U : 0U) + label_output_length >
                __CPLUS_NETWORK_MAX_DNS_NAME) {
            return CPLUS_PAL_INVALID_ARGUMENT;
        }
        if (output_length > 0) ascii_name[output_length++] = '.';
        for (cursor = 0; cursor < label_output_length; cursor++) {
            ascii_name[output_length++] = encoded_label[cursor];
        }
        if (label_end < effective_length) cursor = label_end + 1U;
        else cursor = label_end;
    }
    if (output_length == 0 || output_length > __CPLUS_NETWORK_MAX_DNS_NAME) {
        return CPLUS_PAL_INVALID_ARGUMENT;
    }
    if (capacity <= (unsigned long long)output_length) return CPLUS_PAL_BUFFER_TOO_SMALL;
    for (cursor = 0; cursor < output_length; cursor++) output[cursor] = ascii_name[cursor];
    output[output_length] = '\0';
    return (long long)output_length;
}
