#include "cplus_std_net.h"
#include "cplus_runtime.h"

static void __cplus_net_copy_address_to_pal(
    const struct std_net_address_t* source,
    cplus_socket_address_t* destination) {
    unsigned int index;
    destination->family = source->family;
    destination->port = source->port;
    destination->reserved = source->reserved;
    for (index = 0; index < 16U; index++) destination->address[index] = source->address[index];
    destination->scope_id = source->scope_id;
}

static void __cplus_net_copy_address_from_pal(
    const cplus_socket_address_t* source,
    struct std_net_address_t* destination) {
    unsigned int index;
    destination->family = source->family;
    destination->port = source->port;
    destination->reserved = source->reserved;
    for (index = 0; index < 16U; index++) destination->address[index] = source->address[index];
    destination->scope_id = source->scope_id;
}

std_net_socket_t std_net_open(enum std_net_family_t family, enum std_net_socket_kind_t kind) {
    return platform_socket_open((unsigned int)family, (unsigned int)kind);
}

int std_net_bind(std_net_socket_t socket, const struct std_net_address_t* address) {
    cplus_socket_address_t pal_address;
    if (!address) return CPLUS_PAL_INVALID_ARGUMENT;
    __cplus_net_copy_address_to_pal(address, &pal_address);
    return platform_socket_bind(socket, &pal_address);
}

int std_net_listen(std_net_socket_t socket, int backlog) {
    return platform_socket_listen(socket, backlog);
}

std_net_socket_t std_net_accept(std_net_socket_t socket, struct std_net_address_t* peer) {
    cplus_socket_address_t pal_peer;
    std_net_socket_t accepted = platform_socket_accept(socket, peer ? &pal_peer : (cplus_socket_address_t*)0);
    if (accepted >= 0 && peer) __cplus_net_copy_address_from_pal(&pal_peer, peer);
    return accepted;
}

int std_net_connect(std_net_socket_t socket, const struct std_net_address_t* address) {
    cplus_socket_address_t pal_address;
    if (!address) return CPLUS_PAL_INVALID_ARGUMENT;
    __cplus_net_copy_address_to_pal(address, &pal_address);
    return platform_socket_connect(socket, &pal_address);
}

int std_net_get_address(std_net_socket_t socket, int peer, struct std_net_address_t* address) {
    cplus_socket_address_t pal_address;
    int status;
    if (!address) return CPLUS_PAL_INVALID_ARGUMENT;
    status = platform_socket_get_address(socket, peer, &pal_address);
    if (status == 0) __cplus_net_copy_address_from_pal(&pal_address, address);
    return status;
}

long long std_net_send(std_net_socket_t socket, const void* buffer, unsigned long long length) {
    return platform_socket_send(socket, buffer, length);
}

long long std_net_receive(std_net_socket_t socket, void* buffer, unsigned long long capacity) {
    return platform_socket_receive(socket, buffer, capacity);
}

long long std_net_send_to(
    std_net_socket_t socket,
    const void* buffer,
    unsigned long long length,
    const struct std_net_address_t* destination) {
    cplus_socket_address_t pal_destination;
    if (!destination) return CPLUS_PAL_INVALID_ARGUMENT;
    __cplus_net_copy_address_to_pal(destination, &pal_destination);
    return platform_socket_send_to(socket, buffer, length, &pal_destination);
}

long long std_net_receive_from(
    std_net_socket_t socket,
    void* buffer,
    unsigned long long capacity,
    struct std_net_address_t* source) {
    cplus_socket_address_t pal_source;
    long long received = platform_socket_receive_from(
        socket,
        buffer,
        capacity,
        source ? &pal_source : (cplus_socket_address_t*)0);
    if (received >= 0 && source) __cplus_net_copy_address_from_pal(&pal_source, source);
    return received;
}

int std_net_shutdown(std_net_socket_t socket, enum std_net_shutdown_t direction) {
    return platform_socket_shutdown(socket, (unsigned int)direction);
}

int std_net_close(std_net_socket_t socket) {
    return platform_socket_close(socket);
}

int std_net_parse_address(
    enum std_net_family_t family,
    const char* text,
    struct std_net_address_t* address) {
    cplus_socket_address_t pal_address;
    int status;
    if (!text || !address) return CPLUS_PAL_INVALID_ARGUMENT;
    status = platform_network_parse_address((unsigned int)family, text, &pal_address);
    if (status == 0) __cplus_net_copy_address_from_pal(&pal_address, address);
    return status;
}

long long std_net_format_address(
    const struct std_net_address_t* address,
    char* output,
    unsigned long long capacity) {
    cplus_socket_address_t pal_address;
    if (!address) return CPLUS_PAL_INVALID_ARGUMENT;
    __cplus_net_copy_address_to_pal(address, &pal_address);
    return platform_network_format_address(&pal_address, output, capacity);
}

int std_net_resolve(
    const char* hostname,
    enum std_net_family_t family,
    unsigned short port,
    struct std_net_address_t* addresses,
    unsigned long long capacity,
    unsigned long long* count) {
    cplus_socket_address_t* pal_addresses;
    unsigned long long resolved_count = 0;
    unsigned long long pal_capacity = capacity > 256ULL ? 256ULL : capacity;
    unsigned long long index;
    int status;
    if (!hostname || !count || (capacity > 0 && !addresses)) {
        return CPLUS_PAL_INVALID_ARGUMENT;
    }
    pal_addresses = pal_capacity == 0
        ? (cplus_socket_address_t*)0
        : (cplus_socket_address_t*)__cplus_alloc(
            pal_capacity * (unsigned long long)sizeof(cplus_socket_address_t));
    if (pal_capacity > 0 && !pal_addresses) return CPLUS_PAL_IO_ERROR;
    status = platform_network_resolve(
        hostname,
        (unsigned int)family,
        port,
        pal_addresses,
        pal_capacity,
        &resolved_count);
    if (status == 0 || status == CPLUS_PAL_BUFFER_TOO_SMALL) {
        for (index = 0; index < resolved_count && index < pal_capacity; index++) {
            __cplus_net_copy_address_from_pal(&pal_addresses[index], &addresses[index]);
        }
        *count = resolved_count;
    }
    __cplus_free(pal_addresses);
    return status;
}
