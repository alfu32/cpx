#include "cplus_platform.h"
#include "cplus_network_internal.h"
#include "cplus_linux_network_internal.h"

#define CPLUS_DNS_MAX_SERVERS 3U
#define CPLUS_DNS_MAX_CNAME_HOPS 8U
#define CPLUS_DNS_MAX_ADDRESSES 256U
#define CPLUS_DNS_MAX_RECORDS 256U
#define CPLUS_DNS_MESSAGE_CAPACITY 4096U
#define CPLUS_DNS_NAME_CAPACITY 254U
#define CPLUS_DNS_DEFAULT_TIMEOUT_MS 2000U
#define CPLUS_DNS_UDP_PORT 53U
#define CPLUS_DNS_NETWORK_ERROR CPLUS_PAL_NETWORK_ERROR
#define CPLUS_DNS_ULL_MAX 0xffffffffffffffffULL

typedef struct cplus_dns_sockaddr_ipv4 {
    unsigned short family;
    unsigned short port;
    unsigned char address[4];
    unsigned char zero[8];
} cplus_dns_sockaddr_ipv4;

typedef struct cplus_dns_sockaddr_ipv6 {
    unsigned short family;
    unsigned short port;
    unsigned int flowinfo;
    unsigned char address[16];
    unsigned int scope_id;
} cplus_dns_sockaddr_ipv6;

typedef union cplus_dns_sockaddr {
    unsigned long long alignment;
    unsigned char bytes[128];
    cplus_dns_sockaddr_ipv4 ipv4;
    cplus_dns_sockaddr_ipv6 ipv6;
} cplus_dns_sockaddr;

typedef struct cplus_dns_pollfd {
    int descriptor;
    short events;
    short returned_events;
} cplus_dns_pollfd;

typedef struct cplus_dns_record {
    char owner[CPLUS_DNS_NAME_CAPACITY];
    char target[CPLUS_DNS_NAME_CAPACITY];
    unsigned short type;
    unsigned short address_length;
    unsigned char address[16];
} cplus_dns_record;

_Static_assert(sizeof(cplus_dns_sockaddr_ipv4) == 16, "Linux DNS IPv4 address ABI");
_Static_assert(sizeof(cplus_dns_sockaddr_ipv6) == 28, "Linux DNS IPv6 address ABI");

#if defined(__x86_64__)
static long cplus_dns_syscall1(long number, long first) {
    register long result __asm__("rax") = number;
    __asm__ volatile("syscall" : "+a"(result) : "D"(first) : "rcx", "r11", "memory");
    return result;
}

static long cplus_dns_syscall3(long number, long first, long second, long third) {
    register long result __asm__("rax") = number;
    __asm__ volatile("syscall" : "+a"(result) : "D"(first), "S"(second), "d"(third) : "rcx", "r11", "memory");
    return result;
}

static long cplus_dns_syscall5(long number, long first, long second, long third, long fourth, long fifth) {
    register long result __asm__("rax") = number;
    register long fourth_register __asm__("r10") = fourth;
    register long fifth_register __asm__("r8") = fifth;
    __asm__ volatile("syscall" : "+a"(result) : "D"(first), "S"(second), "d"(third),
        "r"(fourth_register), "r"(fifth_register) : "rcx", "r11", "memory");
    return result;
}

static long cplus_dns_syscall6(long number, long first, long second, long third, long fourth, long fifth, long sixth) {
    register long result __asm__("rax") = number;
    register long fourth_register __asm__("r10") = fourth;
    register long fifth_register __asm__("r8") = fifth;
    register long sixth_register __asm__("r9") = sixth;
    __asm__ volatile("syscall" : "+a"(result) : "D"(first), "S"(second), "d"(third),
        "r"(fourth_register), "r"(fifth_register), "r"(sixth_register) : "rcx", "r11", "memory");
    return result;
}
#define CPLUS_DNS_NR_SOCKET 41
#define CPLUS_DNS_NR_CONNECT 42
#define CPLUS_DNS_NR_SENDTO 44
#define CPLUS_DNS_NR_RECVFROM 45
#define CPLUS_DNS_NR_CLOSE 3
#define CPLUS_DNS_NR_POLL 7
#define CPLUS_DNS_NR_GETRANDOM 318
#define CPLUS_DNS_NR_GETSOCKOPT 55
#elif defined(__aarch64__)
static long cplus_dns_syscall1(long number, long first) {
    register long result __asm__("x0") = first;
    register long syscall_number __asm__("x8") = number;
    __asm__ volatile("svc 0" : "+r"(result) : "r"(syscall_number) : "memory");
    return result;
}

static long cplus_dns_syscall3(long number, long first, long second, long third) {
    register long result __asm__("x0") = first;
    register long second_register __asm__("x1") = second;
    register long third_register __asm__("x2") = third;
    register long syscall_number __asm__("x8") = number;
    __asm__ volatile("svc 0" : "+r"(result) : "r"(second_register), "r"(third_register),
        "r"(syscall_number) : "memory");
    return result;
}

static long cplus_dns_syscall5(long number, long first, long second, long third, long fourth, long fifth) {
    register long result __asm__("x0") = first;
    register long second_register __asm__("x1") = second;
    register long third_register __asm__("x2") = third;
    register long fourth_register __asm__("x3") = fourth;
    register long fifth_register __asm__("x4") = fifth;
    register long syscall_number __asm__("x8") = number;
    __asm__ volatile("svc 0" : "+r"(result) : "r"(second_register), "r"(third_register),
        "r"(fourth_register), "r"(fifth_register), "r"(syscall_number) : "memory");
    return result;
}

static long cplus_dns_syscall6(long number, long first, long second, long third, long fourth, long fifth, long sixth) {
    register long result __asm__("x0") = first;
    register long second_register __asm__("x1") = second;
    register long third_register __asm__("x2") = third;
    register long fourth_register __asm__("x3") = fourth;
    register long fifth_register __asm__("x4") = fifth;
    register long sixth_register __asm__("x5") = sixth;
    register long syscall_number __asm__("x8") = number;
    __asm__ volatile("svc 0" : "+r"(result) : "r"(second_register), "r"(third_register),
        "r"(fourth_register), "r"(fifth_register), "r"(sixth_register), "r"(syscall_number) : "memory");
    return result;
}
#define CPLUS_DNS_NR_SOCKET 198
#define CPLUS_DNS_NR_CONNECT 203
#define CPLUS_DNS_NR_SENDTO 206
#define CPLUS_DNS_NR_RECVFROM 207
#define CPLUS_DNS_NR_CLOSE 57
#define CPLUS_DNS_NR_PPOLL 73
#define CPLUS_DNS_NR_GETRANDOM 278
#define CPLUS_DNS_NR_GETSOCKOPT 209
#else
#error "Linux DNS resolver supports x86_64 and AArch64 only"
#endif

#define CPLUS_DNS_AF_INET 2U
#define CPLUS_DNS_AF_INET6 10U
#define CPLUS_DNS_SOCK_STREAM 1U
#define CPLUS_DNS_SOCK_DGRAM 2U
#define CPLUS_DNS_SOCK_NONBLOCK 0x800U
#define CPLUS_DNS_SOCK_CLOEXEC 0x80000U
#define CPLUS_DNS_MSG_NOSIGNAL 0x4000U
#define CPLUS_DNS_MSG_TRUNC 0x20U
#define CPLUS_DNS_GRND_NONBLOCK 1U
#define CPLUS_DNS_INTERNAL_RESULT_LIMIT 1001
#define CPLUS_DNS_SOL_SOCKET 1
#define CPLUS_DNS_SO_ERROR 4
#define CPLUS_DNS_POLLIN 0x001
#define CPLUS_DNS_POLLOUT 0x004
#define CPLUS_DNS_POLLERR 0x008
#define CPLUS_DNS_POLLHUP 0x010
#define CPLUS_DNS_POLLNVAL 0x020

static long cplus_dns_poll_one(cplus_dns_pollfd* item, long timeout_milliseconds) {
#if defined(__x86_64__)
    return cplus_dns_syscall3(CPLUS_DNS_NR_POLL, (long)item, 1, timeout_milliseconds);
#elif defined(__aarch64__)
    struct cplus_dns_timespec {
        long long seconds;
        long long nanoseconds;
    } timeout;
    timeout.seconds = timeout_milliseconds / 1000L;
    timeout.nanoseconds = (timeout_milliseconds % 1000L) * 1000000L;
    return cplus_dns_syscall5(CPLUS_DNS_NR_PPOLL, (long)item, 1, (long)&timeout, 0, 0);
#endif
}

static unsigned short cplus_dns_swap_port(unsigned short port) {
    return (unsigned short)((port << 8) | (port >> 8));
}

static unsigned short cplus_dns_read_u16(const unsigned char* bytes) {
    return (unsigned short)(((unsigned int)bytes[0] << 8) | bytes[1]);
}

static void cplus_dns_write_u16(unsigned char* bytes, unsigned int value) {
    bytes[0] = (unsigned char)(value >> 8);
    bytes[1] = (unsigned char)value;
}

static int cplus_dns_encode_address(
    const cplus_socket_address_t* source,
    cplus_dns_sockaddr* destination,
    unsigned int* length) {
    unsigned int index;
    if (!source || !destination || !length || source->reserved != 0) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    if (source->family == CPLUS_SOCKET_IPV4) {
        if (source->scope_id != 0) return (int)CPLUS_PAL_INVALID_ARGUMENT;
        for (index = 4; index < 16; index++) {
            if (source->address[index] != 0) return (int)CPLUS_PAL_INVALID_ARGUMENT;
        }
        destination->ipv4.family = CPLUS_DNS_AF_INET;
        destination->ipv4.port = cplus_dns_swap_port(source->port);
        for (index = 0; index < 4; index++) destination->ipv4.address[index] = source->address[index];
        for (index = 0; index < 8; index++) destination->ipv4.zero[index] = 0;
        *length = 16;
        return 0;
    }
    if (source->family == CPLUS_SOCKET_IPV6) {
        destination->ipv6.family = CPLUS_DNS_AF_INET6;
        destination->ipv6.port = cplus_dns_swap_port(source->port);
        destination->ipv6.flowinfo = 0;
        for (index = 0; index < 16; index++) destination->ipv6.address[index] = source->address[index];
        destination->ipv6.scope_id = source->scope_id;
        *length = 28;
        return 0;
    }
    return (int)CPLUS_PAL_UNSUPPORTED;
}

static int cplus_dns_decode_address(
    const cplus_dns_sockaddr* source,
    unsigned int length,
    cplus_socket_address_t* destination) {
    unsigned int index;
    if (!source || !destination) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    *destination = (cplus_socket_address_t){0};
    if (source->ipv4.family == CPLUS_DNS_AF_INET && length >= 16) {
        destination->family = CPLUS_SOCKET_IPV4;
        destination->port = cplus_dns_swap_port(source->ipv4.port);
        for (index = 0; index < 4; index++) destination->address[index] = source->ipv4.address[index];
        return 0;
    }
    if (source->ipv6.family == CPLUS_DNS_AF_INET6 && length >= 28) {
        destination->family = CPLUS_SOCKET_IPV6;
        destination->port = cplus_dns_swap_port(source->ipv6.port);
        for (index = 0; index < 16; index++) destination->address[index] = source->ipv6.address[index];
        destination->scope_id = source->ipv6.scope_id;
        return 0;
    }
    return (int)CPLUS_PAL_UNSUPPORTED;
}

static long long cplus_dns_now(void) {
    return platform_clock_monotonic_nanoseconds();
}

static long long cplus_dns_deadline(unsigned int timeout_milliseconds) {
    long long now = cplus_dns_now();
    unsigned long long duration = (unsigned long long)timeout_milliseconds * 1000000ULL;
    if (now < 0 || duration > (unsigned long long)(0x7fffffffffffffffLL - now)) {
        return CPLUS_PAL_NETWORK_ERROR;
    }
    return now + (long long)duration;
}

static int cplus_dns_wait(int descriptor, short events, long long deadline) {
    cplus_dns_pollfd item;
    for (;;) {
        long long now = cplus_dns_now();
        long long remaining;
        long long milliseconds;
        long result;
        if (now < 0 || now >= deadline) return (int)CPLUS_PAL_NETWORK_ERROR;
        remaining = deadline - now;
        milliseconds = (remaining + 999999LL) / 1000000LL;
        if (milliseconds > 0x7fffffffLL) milliseconds = 0x7fffffffLL;
        item.descriptor = descriptor;
        item.events = events;
        item.returned_events = 0;
        result = cplus_dns_poll_one(&item, (long)milliseconds);
        if (result == 0) return (int)CPLUS_PAL_NETWORK_ERROR;
        if (result < 0) return (int)CPLUS_PAL_NETWORK_ERROR;
        now = cplus_dns_now();
        if (now < 0 || now >= deadline) return (int)CPLUS_PAL_NETWORK_ERROR;
        if (item.returned_events & CPLUS_DNS_POLLNVAL) return (int)CPLUS_PAL_INVALID_ARGUMENT;
        if (item.returned_events & (events | CPLUS_DNS_POLLERR | CPLUS_DNS_POLLHUP)) return 0;
    }
}

static int cplus_dns_socket(unsigned int family, unsigned int type) {
    long native_family;
    long result;
    if (family == CPLUS_SOCKET_IPV4) native_family = CPLUS_DNS_AF_INET;
    else if (family == CPLUS_SOCKET_IPV6) native_family = CPLUS_DNS_AF_INET6;
    else return (int)CPLUS_PAL_UNSUPPORTED;
    result = cplus_dns_syscall3(CPLUS_DNS_NR_SOCKET, native_family,
        (long)(type | CPLUS_DNS_SOCK_NONBLOCK | CPLUS_DNS_SOCK_CLOEXEC), 0);
    if (result < 0) {
        if (-result == 1 || -result == 13) return (int)CPLUS_PAL_ACCESS_DENIED;
        if (-result == 97 || -result == 93 || -result == 94 || -result == 95) return (int)CPLUS_PAL_UNSUPPORTED;
        return (int)CPLUS_PAL_NETWORK_ERROR;
    }
    return (int)result;
}

static void cplus_dns_close(int descriptor) {
    if (descriptor >= 0) cplus_dns_syscall1(CPLUS_DNS_NR_CLOSE, descriptor);
}

static int cplus_dns_address_equal(
    const cplus_socket_address_t* left,
    const cplus_socket_address_t* right,
    int compare_port) {
    unsigned int length;
    unsigned int index;
    if (left->family != right->family || left->scope_id != right->scope_id ||
        (compare_port && left->port != right->port)) return 0;
    if (left->family == CPLUS_SOCKET_IPV4) length = 4;
    else if (left->family == CPLUS_SOCKET_IPV6) length = 16;
    else return 0;
    for (index = 0; index < length; index++) {
        if (left->address[index] != right->address[index]) return 0;
    }
    return 1;
}

static int cplus_dns_random_id(unsigned char id[2]) {
    unsigned int obtained = 0;
    while (obtained < 2U) {
        long result = cplus_dns_syscall3(CPLUS_DNS_NR_GETRANDOM,
            (long)(id + obtained), (long)(2U - obtained), CPLUS_DNS_GRND_NONBLOCK);
        if (result <= 0) return (int)CPLUS_PAL_NETWORK_ERROR;
        if ((unsigned long)result > 2U - obtained) return (int)CPLUS_PAL_NETWORK_ERROR;
        obtained += (unsigned int)result;
    }
    return 0;
}

static int cplus_dns_build_query(
    const char* hostname,
    unsigned short query_type,
    unsigned char transaction_id[2],
    unsigned char query[512],
    unsigned int* query_length) {
    unsigned int cursor = 0;
    unsigned int input = 0;
    unsigned int label_start = 0;
    unsigned int index;
    int random_result;
    if (!hostname || !query_length || (query_type != 1U && query_type != 28U)) {
        return (int)CPLUS_PAL_INVALID_ARGUMENT;
    }
    random_result = cplus_dns_random_id(transaction_id);
    if (random_result < 0) return random_result;
    query[0] = transaction_id[0];
    query[1] = transaction_id[1];
    query[2] = 0x01;
    query[3] = 0x00;
    query[4] = 0;
    query[5] = 1;
    query[6] = 0;
    query[7] = 0;
    query[8] = 0;
    query[9] = 0;
    query[10] = 0;
    query[11] = 0;
    cursor = 12U;
    while (hostname[input] != '\0') {
        if (hostname[input] == '.') {
            unsigned int label_length = input - label_start;
            if (label_length == 0 || label_length > 63U || cursor + label_length + 1U > 267U) {
                return (int)CPLUS_PAL_INVALID_ARGUMENT;
            }
            query[cursor++] = (unsigned char)label_length;
            for (index = label_start; index < input; index++) {
                unsigned char value = (unsigned char)hostname[index];
                if (!((value >= 'a' && value <= 'z') || (value >= '0' && value <= '9') || value == '-')) {
                    return (int)CPLUS_PAL_INVALID_ARGUMENT;
                }
                query[cursor++] = value;
            }
            label_start = input + 1U;
        }
        input++;
        if (input >= 254U) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    }
    if (input == label_start) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    {
        unsigned int label_length = input - label_start;
        if (label_length == 0 || label_length > 63U || cursor + label_length + 1U > 267U) {
            return (int)CPLUS_PAL_INVALID_ARGUMENT;
        }
        query[cursor++] = (unsigned char)label_length;
        for (index = label_start; index < input; index++) {
            unsigned char value = (unsigned char)hostname[index];
            if (!((value >= 'a' && value <= 'z') || (value >= '0' && value <= '9') || value == '-')) {
                return (int)CPLUS_PAL_INVALID_ARGUMENT;
            }
            query[cursor++] = value;
        }
    }
    query[cursor++] = 0;
    cplus_dns_write_u16(query + cursor, query_type);
    cursor += 2U;
    query[cursor++] = 0;
    query[cursor++] = 1;
    *query_length = cursor;
    return 0;
}

static int cplus_dns_send_all(int descriptor, const unsigned char* buffer, unsigned int length, long long deadline) {
    unsigned int offset = 0;
    while (offset < length) {
        long long now = cplus_dns_now();
        long result;
        if (now < 0 || now >= deadline) return (int)CPLUS_PAL_NETWORK_ERROR;
        result = cplus_dns_syscall6(CPLUS_DNS_NR_SENDTO, descriptor,
            (long)(buffer + offset), (long)(length - offset), CPLUS_DNS_MSG_NOSIGNAL, 0, 0);
        if (result > 0) {
            offset += (unsigned int)result;
            continue;
        }
        if (result == 0) return (int)CPLUS_PAL_NETWORK_ERROR;
        if (-result != 11 && -result != 4) return (int)CPLUS_PAL_NETWORK_ERROR;
        if (cplus_dns_wait(descriptor, CPLUS_DNS_POLLOUT, deadline) < 0) return (int)CPLUS_PAL_NETWORK_ERROR;
    }
    return 0;
}

static int cplus_dns_receive_all(int descriptor, unsigned char* buffer, unsigned int length, long long deadline) {
    unsigned int offset = 0;
    while (offset < length) {
        long long now = cplus_dns_now();
        long result;
        if (now < 0 || now >= deadline) return (int)CPLUS_PAL_NETWORK_ERROR;
        result = cplus_dns_syscall6(CPLUS_DNS_NR_RECVFROM, descriptor,
            (long)(buffer + offset), (long)(length - offset), 0, 0, 0);
        if (result > 0) {
            offset += (unsigned int)result;
            continue;
        }
        if (result == 0) return (int)CPLUS_PAL_NETWORK_ERROR;
        if (-result != 11 && -result != 4) return (int)CPLUS_PAL_NETWORK_ERROR;
        if (cplus_dns_wait(descriptor, CPLUS_DNS_POLLIN, deadline) < 0) return (int)CPLUS_PAL_NETWORK_ERROR;
    }
    return 0;
}

static int cplus_dns_udp_exchange(
    const cplus_socket_address_t* server,
    const unsigned char* query,
    unsigned int query_length,
    unsigned char* response,
    unsigned int response_capacity,
    unsigned int* response_length,
    long long deadline) {
    cplus_dns_sockaddr native_server;
    cplus_dns_sockaddr native_source;
    cplus_socket_address_t source;
    unsigned int server_length;
    unsigned int source_length = sizeof(native_source);
    int descriptor = cplus_dns_socket(server->family, CPLUS_DNS_SOCK_DGRAM);
    int status;
    long result;
    if (descriptor < 0) return descriptor;
    status = cplus_dns_encode_address(server, &native_server, &server_length);
    if (status < 0) {
        cplus_dns_close(descriptor);
        return status;
    }
    for (;;) {
        result = cplus_dns_syscall6(CPLUS_DNS_NR_SENDTO, descriptor, (long)query,
            (long)query_length, CPLUS_DNS_MSG_NOSIGNAL, (long)&native_server, (long)server_length);
        if (result == (long)query_length) break;
        if (result >= 0) {
            cplus_dns_close(descriptor);
            return (int)CPLUS_PAL_NETWORK_ERROR;
        }
        if (-result != 11 && -result != 4) {
            cplus_dns_close(descriptor);
            return (int)CPLUS_PAL_NETWORK_ERROR;
        }
        status = cplus_dns_wait(descriptor, CPLUS_DNS_POLLOUT, deadline);
        if (status < 0) {
            cplus_dns_close(descriptor);
            return status;
        }
    }
    status = cplus_dns_wait(descriptor, CPLUS_DNS_POLLIN, deadline);
    if (status < 0) {
        cplus_dns_close(descriptor);
        return status;
    }
    result = cplus_dns_syscall6(CPLUS_DNS_NR_RECVFROM, descriptor, (long)response,
        (long)response_capacity, CPLUS_DNS_MSG_TRUNC,
        (long)&native_source, (long)&source_length);
    cplus_dns_close(descriptor);
    if (result < 0 || (unsigned long)result > response_capacity) return (int)CPLUS_PAL_NETWORK_ERROR;
    status = cplus_dns_decode_address(&native_source, source_length, &source);
    if (status < 0 || !cplus_dns_address_equal(server, &source, 1)) return (int)CPLUS_PAL_NETWORK_ERROR;
    *response_length = (unsigned int)result;
    return 0;
}

static int cplus_dns_tcp_exchange(
    const cplus_socket_address_t* server,
    const unsigned char* query,
    unsigned int query_length,
    unsigned char* response,
    unsigned int response_capacity,
    unsigned int* response_length,
    long long deadline) {
    cplus_dns_sockaddr native_server;
    unsigned int server_length;
    unsigned char framed_query[514];
    unsigned char prefix[2];
    unsigned int message_length;
    int descriptor;
    int status;
    long result;
    int socket_type = CPLUS_DNS_SOCK_STREAM;
    if (query_length > 512U) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    status = cplus_dns_encode_address(server, &native_server, &server_length);
    if (status < 0) return status;
    descriptor = cplus_dns_socket(server->family, (unsigned int)socket_type);
    if (descriptor < 0) return descriptor;
    result = cplus_dns_syscall3(CPLUS_DNS_NR_CONNECT, descriptor,
        (long)&native_server, (long)server_length);
    if (result < 0 && -result != 115 && -result != 4) {
        cplus_dns_close(descriptor);
        return (int)CPLUS_PAL_NETWORK_ERROR;
    }
    if (result < 0) {
        status = cplus_dns_wait(descriptor, CPLUS_DNS_POLLOUT, deadline);
        if (status < 0) {
            cplus_dns_close(descriptor);
            return status;
        }
        {
            int socket_error = 0;
            unsigned int option_length = sizeof(socket_error);
            result = cplus_dns_syscall5(CPLUS_DNS_NR_GETSOCKOPT, descriptor,
                CPLUS_DNS_SOL_SOCKET, CPLUS_DNS_SO_ERROR,
                (long)&socket_error, (long)&option_length);
            if (result < 0 || option_length != sizeof(socket_error) || socket_error != 0) {
                cplus_dns_close(descriptor);
                return (int)CPLUS_PAL_NETWORK_ERROR;
            }
        }
    }
    cplus_dns_write_u16(framed_query, query_length);
    {
        unsigned int index;
        for (index = 0; index < query_length; index++) framed_query[index + 2U] = query[index];
    }
    status = cplus_dns_send_all(descriptor, framed_query, query_length + 2U, deadline);
    if (status == 0) status = cplus_dns_receive_all(descriptor, prefix, 2U, deadline);
    if (status < 0) {
        cplus_dns_close(descriptor);
        return status;
    }
    message_length = cplus_dns_read_u16(prefix);
    if (message_length < 12U || message_length > response_capacity) {
        cplus_dns_close(descriptor);
        return (int)CPLUS_PAL_NETWORK_ERROR;
    }
    status = cplus_dns_receive_all(descriptor, response, message_length, deadline);
    cplus_dns_close(descriptor);
    if (status < 0) return status;
    *response_length = message_length;
    return 0;
}

static int cplus_dns_decode_name(
    const unsigned char* message,
    unsigned int message_length,
    unsigned int* cursor,
    char output[CPLUS_DNS_NAME_CAPACITY]) {
    unsigned int input;
    unsigned int output_length = 0;
    unsigned int jumps = 0;
    int jumped = 0;
    if (!message || !cursor || !output || *cursor >= message_length) return (int)CPLUS_PAL_NETWORK_ERROR;
    input = *cursor;
    for (;;) {
        unsigned int label_length;
        unsigned int index;
        if (input >= message_length) return (int)CPLUS_PAL_NETWORK_ERROR;
        label_length = message[input++];
        if ((label_length & 0xc0U) == 0xc0U) {
            unsigned int target;
            if (input >= message_length || ++jumps > 16U) return (int)CPLUS_PAL_NETWORK_ERROR;
            target = ((label_length & 0x3fU) << 8) | message[input++];
            if (target >= input - 2U || target >= message_length) return (int)CPLUS_PAL_NETWORK_ERROR;
            if (!jumped) {
                *cursor = input;
                jumped = 1;
            }
            input = target;
            continue;
        }
        if ((label_length & 0xc0U) != 0) return (int)CPLUS_PAL_NETWORK_ERROR;
        if (label_length == 0) {
            if (!jumped) *cursor = input;
            output[output_length] = '\0';
            return 0;
        }
        if (label_length > 63U || label_length > message_length - input ||
            output_length + label_length + (output_length > 0 ? 1U : 0U) > 253U) {
            return (int)CPLUS_PAL_NETWORK_ERROR;
        }
        if (output_length > 0) output[output_length++] = '.';
        for (index = 0; index < label_length; index++) {
            unsigned char value = message[input++];
            if (value < 0x21U || value > 0x7eU || value == '.') return (int)CPLUS_PAL_NETWORK_ERROR;
            output[output_length++] = value >= 'A' && value <= 'Z'
                ? (char)(value + ('a' - 'A')) : (char)value;
        }
    }
}

static int cplus_dns_text_equal(const char* left, const char* right) {
    unsigned int index = 0;
    while (left[index] != '\0' && right[index] != '\0') {
        unsigned char l = (unsigned char)left[index];
        unsigned char r = (unsigned char)right[index];
        if (l >= 'A' && l <= 'Z') l = (unsigned char)(l + ('a' - 'A'));
        if (r >= 'A' && r <= 'Z') r = (unsigned char)(r + ('a' - 'A'));
        if (l != r) return 0;
        index++;
    }
    return left[index] == '\0' && right[index] == '\0';
}

static int cplus_dns_parse_response(
    const unsigned char* message,
    unsigned int message_length,
    const unsigned char transaction_id[2],
    const char* query_name,
    unsigned short query_type,
    cplus_dns_record records[CPLUS_DNS_MAX_RECORDS],
    unsigned int* record_count,
    int* truncated) {
    char question_name[CPLUS_DNS_NAME_CAPACITY];
    unsigned int cursor = 12U;
    unsigned int question_count;
    unsigned int answer_count;
    unsigned int flags;
    unsigned int index;
    unsigned int stored = 0;
    if (!message || message_length < 12U || !transaction_id || !query_name ||
        !records || !record_count || !truncated) return (int)CPLUS_PAL_NETWORK_ERROR;
    if (message[0] != transaction_id[0] || message[1] != transaction_id[1]) {
        return (int)CPLUS_PAL_NETWORK_ERROR;
    }
    flags = cplus_dns_read_u16(message + 2U);
    if ((flags & 0x8000U) == 0 || (flags & 0x7800U) != 0) return (int)CPLUS_PAL_NETWORK_ERROR;
    question_count = cplus_dns_read_u16(message + 4U);
    answer_count = cplus_dns_read_u16(message + 6U);
    if (question_count != 1U || answer_count > CPLUS_DNS_MAX_RECORDS) {
        return (int)CPLUS_PAL_NETWORK_ERROR;
    }
    if (cplus_dns_decode_name(message, message_length, &cursor, question_name) < 0 ||
        cursor > message_length || message_length - cursor < 4U ||
        !cplus_dns_text_equal(question_name, query_name) ||
        cplus_dns_read_u16(message + cursor) != query_type ||
        cplus_dns_read_u16(message + cursor + 2U) != 1U) {
        return (int)CPLUS_PAL_NETWORK_ERROR;
    }
    cursor += 4U;
    *record_count = 0;
    *truncated = (flags & 0x0200U) != 0;
    if (*truncated) return 0;
    switch (flags & 0x000fU) {
        case 0:
            break;
        case 3:
            return (int)CPLUS_PAL_NOT_FOUND;
        case 5:
            return (int)CPLUS_PAL_ACCESS_DENIED;
        default:
            return (int)CPLUS_PAL_NETWORK_ERROR;
    }
    for (index = 0; index < answer_count; index++) {
        cplus_dns_record record = {0};
        unsigned int data_length;
        unsigned int data_start;
        unsigned int record_end;
        unsigned short record_class;
        if (cplus_dns_decode_name(message, message_length, &cursor, record.owner) < 0 ||
            cursor > message_length || message_length - cursor < 10U) {
            return (int)CPLUS_PAL_NETWORK_ERROR;
        }
        record.type = cplus_dns_read_u16(message + cursor);
        record_class = cplus_dns_read_u16(message + cursor + 2U);
        data_length = cplus_dns_read_u16(message + cursor + 8U);
        cursor += 10U;
        data_start = cursor;
        if (data_length > message_length - cursor) return (int)CPLUS_PAL_NETWORK_ERROR;
        record_end = cursor + data_length;
        if (record_class == 1U && record.type == 1U) {
            if (data_length != 4U) return (int)CPLUS_PAL_NETWORK_ERROR;
            record.address_length = 4U;
            record.address[0] = message[data_start];
            record.address[1] = message[data_start + 1U];
            record.address[2] = message[data_start + 2U];
            record.address[3] = message[data_start + 3U];
        } else if (record_class == 1U && record.type == 28U) {
            if (data_length != 16U) return (int)CPLUS_PAL_NETWORK_ERROR;
            record.address_length = 16U;
            for (unsigned int byte = 0; byte < 16U; byte++) record.address[byte] = message[data_start + byte];
        } else if (record_class == 1U && record.type == 5U) {
            unsigned int name_cursor = data_start;
            if (cplus_dns_decode_name(message, message_length, &name_cursor, record.target) < 0 ||
                name_cursor > record_end || record.target[0] == '\0') {
                return (int)CPLUS_PAL_NETWORK_ERROR;
            }
        } else {
            cursor = record_end;
            continue;
        }
        cursor = record_end;
        if (stored >= CPLUS_DNS_MAX_RECORDS) return (int)CPLUS_PAL_NETWORK_ERROR;
        records[stored++] = record;
    }
    *record_count = stored;
    return 0;
}

static int cplus_dns_query(
    const cplus_socket_address_t* server,
    const char* query_name,
    unsigned short query_type,
    unsigned int timeout_milliseconds,
    cplus_dns_record records[CPLUS_DNS_MAX_RECORDS],
    unsigned int* record_count) {
    unsigned char query[512];
    unsigned char transaction_id[2];
    unsigned char response[CPLUS_DNS_MESSAGE_CAPACITY];
    unsigned int query_length;
    unsigned int response_length = 0;
    int truncated = 0;
    int status;
    long long deadline;
    deadline = cplus_dns_deadline(timeout_milliseconds);
    if (deadline < 0) return (int)CPLUS_PAL_NETWORK_ERROR;
    status = cplus_dns_build_query(query_name, query_type, transaction_id, query, &query_length);
    if (status < 0) return status;
    status = cplus_dns_udp_exchange(server, query, query_length, response,
        sizeof(response), &response_length, deadline);
    if (status < 0) return status;
    status = cplus_dns_parse_response(response, response_length, transaction_id,
        query_name, query_type, records, record_count, &truncated);
    if (status < 0 || !truncated) return status;
    response_length = 0;
    status = cplus_dns_tcp_exchange(server, query, query_length, response,
        sizeof(response), &response_length, deadline);
    if (status < 0) return status;
    status = cplus_dns_parse_response(response, response_length, transaction_id,
        query_name, query_type, records, record_count, &truncated);
    if (truncated) return (int)CPLUS_PAL_NETWORK_ERROR;
    return status;
}

static int cplus_dns_store_address(
    cplus_socket_address_t results[CPLUS_DNS_MAX_ADDRESSES],
    unsigned int* result_count,
    unsigned int family,
    unsigned short port,
    const unsigned char* bytes,
    unsigned int address_length) {
    cplus_socket_address_t address = {0};
    unsigned int index;
    if (!result_count || !bytes ||
        !((family == CPLUS_SOCKET_IPV4 && address_length == 4U) ||
          (family == CPLUS_SOCKET_IPV6 && address_length == 16U))) {
        return (int)CPLUS_PAL_NETWORK_ERROR;
    }
    address.family = family;
    address.port = port;
    for (index = 0; index < address_length; index++) address.address[index] = bytes[index];
    for (index = 0; index < *result_count; index++) {
        if (cplus_dns_address_equal(&results[index], &address, 1)) return 0;
    }
    if (*result_count >= CPLUS_DNS_MAX_ADDRESSES) return CPLUS_DNS_INTERNAL_RESULT_LIMIT;
    results[(*result_count)++] = address;
    return 0;
}

static int cplus_dns_evaluate_records(
    const cplus_dns_record records[CPLUS_DNS_MAX_RECORDS],
    unsigned int record_count,
    const char* query_name,
    unsigned short query_type,
    unsigned short port,
    cplus_socket_address_t results[CPLUS_DNS_MAX_ADDRESSES],
    unsigned int* result_count,
    char cname_target[CPLUS_DNS_NAME_CAPACITY]) {
    unsigned int index;
    unsigned int family = query_type == 1U ? CPLUS_SOCKET_IPV4 : CPLUS_SOCKET_IPV6;
    unsigned int expected_length = query_type == 1U ? 4U : 16U;
    int found_address = 0;
    int found_cname = 0;
    for (index = 0; index < record_count; index++) {
        if (!cplus_dns_text_equal(records[index].owner, query_name)) continue;
        if (records[index].type == query_type && records[index].address_length == expected_length) {
            int status = cplus_dns_store_address(results, result_count, family, port,
                records[index].address, expected_length);
            if (status != 0) return status;
            found_address = 1;
        } else if (records[index].type == 5U && records[index].target[0] != '\0') {
            if (found_cname && !cplus_dns_text_equal(cname_target, records[index].target)) {
                return (int)CPLUS_PAL_NETWORK_ERROR;
            }
            if (!found_cname) {
                unsigned int character = 0;
                while (records[index].target[character] != '\0' && character + 1U < CPLUS_DNS_NAME_CAPACITY) {
                    cname_target[character] = records[index].target[character];
                    character++;
                }
                if (records[index].target[character] != '\0') return (int)CPLUS_PAL_NETWORK_ERROR;
                cname_target[character] = '\0';
                found_cname = 1;
            }
        }
    }
    if (found_address) return 0;
    if (found_cname) return 1;
    return (int)CPLUS_PAL_NOT_FOUND;
}

static int cplus_dns_query_family(
    const char* hostname,
    unsigned short query_type,
    unsigned short port,
    const cplus_socket_address_t* nameservers,
    unsigned int nameserver_count,
    unsigned int timeout_milliseconds,
    cplus_socket_address_t results[CPLUS_DNS_MAX_ADDRESSES],
    unsigned int* result_count) {
    char current_name[CPLUS_DNS_NAME_CAPACITY];
    char visited[CPLUS_DNS_MAX_CNAME_HOPS + 1U][CPLUS_DNS_NAME_CAPACITY];
    unsigned int visited_count = 1U;
    unsigned int hops = 0;
    unsigned int index;
    for (index = 0; hostname[index] != '\0' && index + 1U < CPLUS_DNS_NAME_CAPACITY; index++) {
        current_name[index] = hostname[index];
    }
    if (hostname[index] != '\0') return (int)CPLUS_PAL_INVALID_ARGUMENT;
    current_name[index] = '\0';
    for (index = 0; current_name[index] != '\0'; index++) {
        visited[0][index] = current_name[index];
    }
    visited[0][index] = '\0';
    for (;;) {
        int saw_network_error = 0;
        int saw_not_found = 0;
        int saw_access_denied = 0;
        int followed_alias = 0;
        unsigned int server_index;
        for (server_index = 0; server_index < nameserver_count; server_index++) {
            cplus_dns_record records[CPLUS_DNS_MAX_RECORDS];
            unsigned int record_count = 0;
            char cname_target[CPLUS_DNS_NAME_CAPACITY] = {0};
            int status = cplus_dns_query(&nameservers[server_index], current_name,
                query_type, timeout_milliseconds, records, &record_count);
            if (status == (int)CPLUS_PAL_NOT_FOUND) {
                saw_not_found = 1;
                continue;
            }
            if (status < 0) {
                if (status == (int)CPLUS_PAL_ACCESS_DENIED) saw_access_denied = 1;
                else saw_network_error = 1;
                continue;
            }
            status = cplus_dns_evaluate_records(records, record_count, current_name,
                query_type, port, results, result_count, cname_target);
            if (status == CPLUS_DNS_INTERNAL_RESULT_LIMIT) return status;
            if (status == 0) return 0;
            if (status == 1) {
                if (hops >= CPLUS_DNS_MAX_CNAME_HOPS) return (int)CPLUS_PAL_NETWORK_ERROR;
                for (index = 0; index < visited_count; index++) {
                    if (cplus_dns_text_equal(visited[index], cname_target)) {
                        return (int)CPLUS_PAL_NETWORK_ERROR;
                    }
                }
                if (visited_count > CPLUS_DNS_MAX_CNAME_HOPS) return (int)CPLUS_PAL_NETWORK_ERROR;
                for (index = 0; cname_target[index] != '\0'; index++) {
                    visited[visited_count][index] = cname_target[index];
                }
                visited[visited_count][index] = '\0';
                visited_count++;
                for (index = 0; cname_target[index] != '\0'; index++) current_name[index] = cname_target[index];
                current_name[index] = '\0';
                hops++;
                followed_alias = 1;
                break;
            }
            saw_not_found = 1;
        }
        if (followed_alias) continue;
        if (saw_network_error) return (int)CPLUS_PAL_NETWORK_ERROR;
        if (saw_access_denied) return (int)CPLUS_PAL_ACCESS_DENIED;
        if (saw_not_found) return (int)CPLUS_PAL_NOT_FOUND;
        return (int)CPLUS_PAL_NETWORK_ERROR;
    }
}

int __cplus_linux_network_resolve_with_nameservers(
    const char* ascii_hostname,
    unsigned int family,
    unsigned short port,
    const cplus_socket_address_t* nameservers,
    unsigned int nameserver_count,
    unsigned int timeout_milliseconds,
    cplus_socket_address_t* addresses,
    unsigned long long capacity,
    unsigned long long* count) {
    cplus_socket_address_t results[CPLUS_DNS_MAX_ADDRESSES];
    unsigned int result_count = 0;
    unsigned int first_type;
    unsigned int last_type;
    unsigned int query_type;
    unsigned int index;
    int saw_network_error = 0;
    int saw_not_found = 0;
    int saw_access_denied = 0;
    if (!ascii_hostname || !count || (capacity > 0 && !addresses) || !nameservers ||
        nameserver_count == 0 || nameserver_count > CPLUS_DNS_MAX_SERVERS || timeout_milliseconds == 0) {
        return (int)CPLUS_PAL_INVALID_ARGUMENT;
    }
    if (family != CPLUS_SOCKET_ANY_FAMILY && family != CPLUS_SOCKET_IPV4 && family != CPLUS_SOCKET_IPV6) {
        return (int)CPLUS_PAL_UNSUPPORTED;
    }
    *count = 0;
    if (family == CPLUS_SOCKET_IPV4) {
        first_type = 1U;
        last_type = 1U;
    } else if (family == CPLUS_SOCKET_IPV6) {
        first_type = 28U;
        last_type = 28U;
    } else {
        first_type = 1U;
        last_type = 28U;
    }
    for (index = 0; index < nameserver_count; index++) {
        if ((nameservers[index].family != CPLUS_SOCKET_IPV4 &&
             nameservers[index].family != CPLUS_SOCKET_IPV6) ||
            nameservers[index].port == 0 || nameservers[index].reserved != 0) {
            return (int)CPLUS_PAL_INVALID_ARGUMENT;
        }
    }
    for (query_type = first_type; query_type <= last_type; query_type = query_type == 1U ? 28U : 29U) {
        int status = cplus_dns_query_family(ascii_hostname, (unsigned short)query_type, port,
            nameservers, nameserver_count, timeout_milliseconds, results, &result_count);
        if (status == CPLUS_DNS_INTERNAL_RESULT_LIMIT) return (int)CPLUS_PAL_NETWORK_ERROR;
        if (status == (int)CPLUS_PAL_NOT_FOUND) saw_not_found = 1;
        else if (status == (int)CPLUS_PAL_ACCESS_DENIED) saw_access_denied = 1;
        else if (status < 0) saw_network_error = 1;
        if (query_type == last_type) break;
    }
    *count = result_count;
    for (index = 0; index < result_count && (unsigned long long)index < capacity; index++) {
        addresses[index] = results[index];
    }
    if (result_count > capacity) return (int)CPLUS_PAL_BUFFER_TOO_SMALL;
    if (result_count > 0) return 0;
    if (saw_network_error) return (int)CPLUS_PAL_NETWORK_ERROR;
    if (saw_access_denied) return (int)CPLUS_PAL_ACCESS_DENIED;
    if (saw_not_found) return (int)CPLUS_PAL_NOT_FOUND;
    return (int)CPLUS_PAL_NETWORK_ERROR;
}

static int cplus_dns_is_space(char value) {
    return value == ' ' || value == '\t' || value == '\r' || value == '\n';
}

int __cplus_linux_network_parse_resolv_conf(
    const char* contents,
    unsigned int length,
    cplus_socket_address_t nameservers[CPLUS_DNS_MAX_SERVERS],
    unsigned int* nameserver_count) {
    unsigned int cursor = 0;
    if (!contents || !nameservers || !nameserver_count) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    *nameserver_count = 0;
    while (cursor < length && *nameserver_count < CPLUS_DNS_MAX_SERVERS) {
        unsigned int line_end = cursor;
        unsigned int token_start;
        unsigned int token_length;
        char token[64];
        cplus_socket_address_t address = {0};
        unsigned int family;
        int parsed;
        while (line_end < length && contents[line_end] != '\n') line_end++;
        while (cursor < line_end && cplus_dns_is_space(contents[cursor])) cursor++;
        if (cursor < line_end && contents[cursor] != '#') {
            static const char directive[] = "nameserver";
            unsigned int directive_index;
            for (directive_index = 0; directive_index < 10U && cursor + directive_index < line_end; directive_index++) {
                if (contents[cursor + directive_index] != directive[directive_index]) break;
            }
            cursor += directive_index;
            if (directive_index == 10U && cursor < line_end && cplus_dns_is_space(contents[cursor])) {
                while (cursor < line_end && cplus_dns_is_space(contents[cursor])) cursor++;
                token_start = cursor;
                while (cursor < line_end && !cplus_dns_is_space(contents[cursor]) && contents[cursor] != '#') cursor++;
                token_length = cursor - token_start;
                if (token_length > 0 && token_length < sizeof(token)) {
                    for (directive_index = 0; directive_index < token_length; directive_index++) {
                        token[directive_index] = contents[token_start + directive_index];
                    }
                    token[token_length] = '\0';
                    family = CPLUS_SOCKET_IPV4;
                    for (directive_index = 0; directive_index < token_length; directive_index++) {
                        if (token[directive_index] == ':') family = CPLUS_SOCKET_IPV6;
                    }
                    parsed = platform_network_parse_address(family, token, &address);
                    if (parsed == 0) {
                        unsigned int existing;
                        address.port = CPLUS_DNS_UDP_PORT;
                        for (existing = 0; existing < *nameserver_count; existing++) {
                            if (cplus_dns_address_equal(&nameservers[existing], &address, 0)) break;
                        }
                        if (existing == *nameserver_count) {
                            nameservers[(*nameserver_count)++] = address;
                        }
                    }
                }
            }
        }
        cursor = line_end + 1U;
    }
    return *nameserver_count > 0 ? 0 : (int)CPLUS_PAL_NETWORK_ERROR;
}

static int cplus_dns_load_nameservers(
    cplus_socket_address_t nameservers[CPLUS_DNS_MAX_SERVERS],
    unsigned int* nameserver_count) {
    char contents[4096];
    unsigned long long used = 0;
    long long file;
    int status = 0;
    if (!nameserver_count) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    *nameserver_count = 0;
    file = platform_file_open("/etc/resolv.conf", CPLUS_FILE_READ);
    if (file < 0) return (int)file;
    while (used < sizeof(contents) - 1U) {
        long long read = platform_file_read(file, contents + used, sizeof(contents) - 1U - used);
        if (read < 0) {
            status = (int)read;
            break;
        }
        if (read == 0) break;
        used += (unsigned long long)read;
    }
    platform_file_close(file);
    if (status < 0) return status;
    return __cplus_linux_network_parse_resolv_conf(contents, (unsigned int)used,
        nameservers, nameserver_count);
}

static int cplus_dns_copy_results(
    const cplus_socket_address_t* source,
    unsigned int result_count,
    cplus_socket_address_t* destination,
    unsigned long long capacity,
    unsigned long long* count) {
    unsigned int index;
    *count = result_count;
    for (index = 0; index < result_count && (unsigned long long)index < capacity; index++) {
        destination[index] = source[index];
    }
    return (unsigned long long)result_count > capacity
        ? (int)CPLUS_PAL_BUFFER_TOO_SMALL : 0;
}

int platform_network_resolve(
    const char* hostname,
    unsigned int family,
    unsigned short port,
    cplus_socket_address_t* addresses,
    unsigned long long capacity,
    unsigned long long* count) {
    cplus_socket_address_t numeric = {0};
    cplus_socket_address_t nameservers[CPLUS_DNS_MAX_SERVERS];
    unsigned int nameserver_count = 0;
    char ascii_hostname[CPLUS_DNS_NAME_CAPACITY];
    int status;
    if (!hostname || !count || (capacity > 0 && !addresses)) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    *count = 0;
    if (family != CPLUS_SOCKET_ANY_FAMILY && family != CPLUS_SOCKET_IPV4 && family != CPLUS_SOCKET_IPV6) {
        return (int)CPLUS_PAL_UNSUPPORTED;
    }
    if (family == CPLUS_SOCKET_IPV4 || family == CPLUS_SOCKET_ANY_FAMILY) {
        status = platform_network_parse_address(CPLUS_SOCKET_IPV4, hostname, &numeric);
        if (status == 0) {
            numeric.port = port;
            return cplus_dns_copy_results(&numeric, 1U, addresses, capacity, count);
        }
    }
    if (family == CPLUS_SOCKET_IPV6 || family == CPLUS_SOCKET_ANY_FAMILY) {
        status = platform_network_parse_address(CPLUS_SOCKET_IPV6, hostname, &numeric);
        if (status == 0) {
            numeric.port = port;
            return cplus_dns_copy_results(&numeric, 1U, addresses, capacity, count);
        }
    }
    status = (int)__cplus_network_hostname_to_ascii(hostname, ascii_hostname, sizeof(ascii_hostname));
    if (status < 0) return status;
    status = cplus_dns_load_nameservers(nameservers, &nameserver_count);
    if (status < 0) return status;
    return __cplus_linux_network_resolve_with_nameservers(ascii_hostname, family, port,
        nameservers, nameserver_count, CPLUS_DNS_DEFAULT_TIMEOUT_MS,
        addresses, capacity, count);
}
