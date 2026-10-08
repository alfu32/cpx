/// Version-four blocking IPv4/IPv6 socket and TCP/UDP transport ABI.
import { int64_t, uint8_t, uint16_t, uint32_t, uint64_t } from c.stdint;

pub struct cplus_socket_address_t {
    uint32_t family;
    uint16_t port;
    uint16_t reserved;
    uint8_t address[16];
    uint32_t scope_id;
};

pub int64_t platform_socket_open(uint32_t family, uint32_t kind);
pub int platform_socket_bind(int64_t socket, const cplus_socket_address_t* address);
pub int platform_socket_listen(int64_t socket, int backlog);
pub int64_t platform_socket_accept(int64_t socket, cplus_socket_address_t* peer);
pub int platform_socket_connect(int64_t socket, const cplus_socket_address_t* address);
pub int platform_socket_get_address(int64_t socket, int peer, cplus_socket_address_t* address);
pub int64_t platform_socket_send(int64_t socket, const void* buffer, uint64_t length);
pub int64_t platform_socket_receive(int64_t socket, void* buffer, uint64_t capacity);
pub int64_t platform_socket_send_to(int64_t socket, const void* buffer, uint64_t length, const cplus_socket_address_t* destination);
pub int64_t platform_socket_receive_from(int64_t socket, void* buffer, uint64_t capacity, cplus_socket_address_t* source);
pub int platform_socket_shutdown(int64_t socket, uint32_t direction);
pub int platform_socket_close(int64_t socket);
pub int platform_network_parse_address(uint32_t family, const char* text, cplus_socket_address_t* address);
pub int64_t platform_network_format_address(const cplus_socket_address_t* address, char* output, uint64_t capacity);
pub int platform_network_resolve(
    const char* hostname,
    uint32_t family,
    uint16_t port,
    cplus_socket_address_t* addresses,
    uint64_t capacity,
    uint64_t* count);
