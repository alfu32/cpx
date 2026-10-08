#ifndef CPLUS_STD_NET_H
#define CPLUS_STD_NET_H

#include "cplus_platform.h"

typedef long long std_net_socket_t;

enum std_net_family_t {
    STD_NET_FAMILY_ANY = 0,
    STD_NET_FAMILY_IPV4 = 4,
    STD_NET_FAMILY_IPV6 = 6
};

enum std_net_socket_kind_t {
    STD_NET_SOCKET_STREAM = 1,
    STD_NET_SOCKET_DATAGRAM = 2
};

enum std_net_shutdown_t {
    STD_NET_SHUTDOWN_RECEIVE = 0,
    STD_NET_SHUTDOWN_SEND = 1,
    STD_NET_SHUTDOWN_BOTH = 2
};

enum std_net_status_t {
    STD_NET_OK = 0,
    STD_NET_INVALID_ARGUMENT = -2,
    STD_NET_NOT_FOUND = -3,
    STD_NET_ACCESS_DENIED = -4,
    STD_NET_IO_ERROR = -5,
    STD_NET_UNSUPPORTED = -6,
    STD_NET_BUFFER_TOO_SMALL = -7,
    STD_NET_NETWORK_ERROR = -8
};

struct std_net_address_t {
    unsigned int family;
    unsigned short port;
    unsigned short reserved;
    unsigned char address[16];
    unsigned int scope_id;
};

_Static_assert(sizeof(std_net_socket_t) == 8, "std.net socket handle ABI");
_Static_assert(sizeof(struct std_net_address_t) == 28, "std.net address ABI size");
_Static_assert(_Alignof(struct std_net_address_t) == 4, "std.net address ABI alignment");
_Static_assert(__builtin_offsetof(struct std_net_address_t, family) == 0, "std.net family offset");
_Static_assert(__builtin_offsetof(struct std_net_address_t, port) == 4, "std.net port offset");
_Static_assert(__builtin_offsetof(struct std_net_address_t, reserved) == 6, "std.net reserved offset");
_Static_assert(__builtin_offsetof(struct std_net_address_t, address) == 8, "std.net bytes offset");
_Static_assert(__builtin_offsetof(struct std_net_address_t, scope_id) == 24, "std.net scope offset");

std_net_socket_t std_net_open(enum std_net_family_t family, enum std_net_socket_kind_t kind);
int std_net_bind(std_net_socket_t socket, const struct std_net_address_t* address);
int std_net_listen(std_net_socket_t socket, int backlog);
std_net_socket_t std_net_accept(std_net_socket_t socket, struct std_net_address_t* peer);
int std_net_connect(std_net_socket_t socket, const struct std_net_address_t* address);
int std_net_get_address(std_net_socket_t socket, int peer, struct std_net_address_t* address);
long long std_net_send(std_net_socket_t socket, const void* buffer, unsigned long long length);
long long std_net_receive(std_net_socket_t socket, void* buffer, unsigned long long capacity);
long long std_net_send_to(
    std_net_socket_t socket,
    const void* buffer,
    unsigned long long length,
    const struct std_net_address_t* destination);
long long std_net_receive_from(
    std_net_socket_t socket,
    void* buffer,
    unsigned long long capacity,
    struct std_net_address_t* source);
int std_net_shutdown(std_net_socket_t socket, enum std_net_shutdown_t direction);
int std_net_close(std_net_socket_t socket);
int std_net_parse_address(
    enum std_net_family_t family,
    const char* text,
    struct std_net_address_t* address);
long long std_net_format_address(
    const struct std_net_address_t* address,
    char* output,
    unsigned long long capacity);
int std_net_resolve(
    const char* hostname,
    enum std_net_family_t family,
    unsigned short port,
    struct std_net_address_t* addresses,
    unsigned long long capacity,
    unsigned long long* count);

#endif
