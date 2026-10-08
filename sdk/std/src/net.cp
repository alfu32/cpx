/// Portable blocking sockets, address conversion, and hostname resolution.
import { int64_t, uint8_t, uint16_t, uint32_t, uint64_t } from c.stdint;

pub enum std_net_family_t {
    STD_NET_FAMILY_ANY = 0,
    STD_NET_FAMILY_IPV4 = 4,
    STD_NET_FAMILY_IPV6 = 6
};

pub enum std_net_socket_kind_t {
    STD_NET_SOCKET_STREAM = 1,
    STD_NET_SOCKET_DATAGRAM = 2
};

pub enum std_net_shutdown_t {
    STD_NET_SHUTDOWN_RECEIVE = 0,
    STD_NET_SHUTDOWN_SEND = 1,
    STD_NET_SHUTDOWN_BOTH = 2
};

/// Stable status values shared with the platform abstraction layer.
pub enum std_net_status_t {
    STD_NET_OK = 0,
    STD_NET_INVALID_ARGUMENT = -2,
    STD_NET_NOT_FOUND = -3,
    STD_NET_ACCESS_DENIED = -4,
    STD_NET_IO_ERROR = -5,
    STD_NET_UNSUPPORTED = -6,
    STD_NET_BUFFER_TOO_SMALL = -7,
    STD_NET_NETWORK_ERROR = -8
};

/// Binary internet address. The ABI is 28 bytes with four-byte alignment.
pub struct std_net_address_t {
    uint32_t family;
    uint16_t port;
    uint16_t reserved;
    uint8_t address[16];
    uint32_t scope_id;
};

/// Opaque platform socket handle. Negative values represent stable PAL errors.
pub typedef int64_t std_net_socket_t;

pub std_net_socket_t std_net_open(std_net_family_t family, std_net_socket_kind_t kind);
pub int std_net_bind(std_net_socket_t socket, const std_net_address_t* address);
pub int std_net_listen(std_net_socket_t socket, int backlog);
pub std_net_socket_t std_net_accept(std_net_socket_t socket, std_net_address_t* peer);
pub int std_net_connect(std_net_socket_t socket, const std_net_address_t* address);
pub int std_net_get_address(std_net_socket_t socket, int peer, std_net_address_t* address);
pub int64_t std_net_send(std_net_socket_t socket, const void* buffer, uint64_t length);
pub int64_t std_net_receive(std_net_socket_t socket, void* buffer, uint64_t capacity);
pub int64_t std_net_send_to(
    std_net_socket_t socket,
    const void* buffer,
    uint64_t length,
    const std_net_address_t* destination
);
pub int64_t std_net_receive_from(
    std_net_socket_t socket,
    void* buffer,
    uint64_t capacity,
    std_net_address_t* source
);
pub int std_net_shutdown(std_net_socket_t socket, std_net_shutdown_t direction);
pub int std_net_close(std_net_socket_t socket);

pub int std_net_parse_address(
    std_net_family_t family,
    const char* text,
    std_net_address_t* address
);
pub int64_t std_net_format_address(
    const std_net_address_t* address,
    char* output,
    uint64_t capacity
);
pub int std_net_resolve(
    const char* hostname,
    std_net_family_t family,
    uint16_t port,
    std_net_address_t* addresses,
    uint64_t capacity,
    uint64_t* count
);
