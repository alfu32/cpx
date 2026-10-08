#include "cplus_platform.h"
#include "cplus_network_internal.h"

typedef unsigned long long __cplus_windows_native_socket;
typedef long long (__stdcall *__cplus_windows_procedure)(void);

typedef struct __cplus_windows_sockaddr_ipv4 {
    unsigned short family;
    unsigned short port;
    unsigned char address[4];
    unsigned char zero[8];
} __cplus_windows_sockaddr_ipv4;

typedef struct __cplus_windows_sockaddr_ipv6 {
    unsigned short family;
    unsigned short port;
    unsigned int flowinfo;
    unsigned char address[16];
    unsigned int scope_id;
} __cplus_windows_sockaddr_ipv6;

typedef struct __cplus_windows_addrinfo_w {
    int flags;
    int family;
    int socket_type;
    int protocol;
    unsigned long long address_length;
    unsigned short* canonical_name;
    void* address;
    struct __cplus_windows_addrinfo_w* next;
} __cplus_windows_addrinfo_w;

typedef union __cplus_windows_sockaddr_storage {
    unsigned long long alignment;
    unsigned char bytes[128];
    __cplus_windows_sockaddr_ipv4 ipv4;
    __cplus_windows_sockaddr_ipv6 ipv6;
} __cplus_windows_sockaddr_storage;

typedef struct __cplus_windows_init_once {
    void* pointer;
} __cplus_windows_init_once;

typedef int (__stdcall *__cplus_windows_init_callback)(
    __cplus_windows_init_once* once,
    void* parameter,
    void** context);
typedef int (__stdcall *__cplus_wsa_startup_fn)(unsigned short version, void* data);
typedef int (__stdcall *__cplus_wsa_cleanup_fn)(void);
typedef __cplus_windows_native_socket (__stdcall *__cplus_wsa_socket_fn)(int family, int kind, int protocol);
typedef int (__stdcall *__cplus_wsa_bind_fn)(
    __cplus_windows_native_socket socket,
    const void* address,
    int address_length);
typedef int (__stdcall *__cplus_wsa_listen_fn)(__cplus_windows_native_socket socket, int backlog);
typedef __cplus_windows_native_socket (__stdcall *__cplus_wsa_accept_fn)(
    __cplus_windows_native_socket socket,
    void* address,
    int* address_length);
typedef int (__stdcall *__cplus_wsa_connect_fn)(
    __cplus_windows_native_socket socket,
    const void* address,
    int address_length);
typedef int (__stdcall *__cplus_wsa_get_name_fn)(
    __cplus_windows_native_socket socket,
    void* address,
    int* address_length);
typedef int (__stdcall *__cplus_wsa_send_fn)(
    __cplus_windows_native_socket socket,
    const char* buffer,
    int length,
    int flags);
typedef int (__stdcall *__cplus_wsa_receive_fn)(
    __cplus_windows_native_socket socket,
    char* buffer,
    int capacity,
    int flags);
typedef int (__stdcall *__cplus_wsa_send_to_fn)(
    __cplus_windows_native_socket socket,
    const char* buffer,
    int length,
    int flags,
    const void* address,
    int address_length);
typedef int (__stdcall *__cplus_wsa_receive_from_fn)(
    __cplus_windows_native_socket socket,
    char* buffer,
    int capacity,
    int flags,
    void* address,
    int* address_length);
typedef int (__stdcall *__cplus_wsa_shutdown_fn)(__cplus_windows_native_socket socket, int direction);
typedef int (__stdcall *__cplus_wsa_close_fn)(__cplus_windows_native_socket socket);
typedef int (__stdcall *__cplus_wsa_get_error_fn)(void);
typedef int (__stdcall *__cplus_wsa_get_addr_info_w_fn)(
    const unsigned short* node_name,
    const unsigned short* service_name,
    const __cplus_windows_addrinfo_w* hints,
    __cplus_windows_addrinfo_w** results);
typedef void (__stdcall *__cplus_wsa_free_addr_info_w_fn)(__cplus_windows_addrinfo_w* results);

typedef struct __cplus_windows_winsock_api {
    __cplus_wsa_startup_fn startup;
    __cplus_wsa_cleanup_fn cleanup;
    __cplus_wsa_socket_fn socket;
    __cplus_wsa_bind_fn bind;
    __cplus_wsa_listen_fn listen;
    __cplus_wsa_accept_fn accept;
    __cplus_wsa_connect_fn connect;
    __cplus_wsa_get_name_fn get_socket_name;
    __cplus_wsa_get_name_fn get_peer_name;
    __cplus_wsa_send_fn send;
    __cplus_wsa_receive_fn receive;
    __cplus_wsa_send_to_fn send_to;
    __cplus_wsa_receive_from_fn receive_from;
    __cplus_wsa_shutdown_fn shutdown;
    __cplus_wsa_close_fn close;
    __cplus_wsa_get_error_fn get_error;
    __cplus_wsa_get_addr_info_w_fn get_addr_info_w;
    __cplus_wsa_free_addr_info_w_fn free_addr_info_w;
} __cplus_windows_winsock_api;

__declspec(dllimport) void* __stdcall LoadLibraryExW(
    const unsigned short* file_name,
    void* file,
    unsigned long flags);
__declspec(dllimport) __cplus_windows_procedure __stdcall GetProcAddress(
    void* module,
    const char* name);
__declspec(dllimport) int __stdcall InitOnceExecuteOnce(
    __cplus_windows_init_once* once,
    __cplus_windows_init_callback callback,
    void* parameter,
    void** context);
__declspec(dllimport) void* __stdcall GetProcessHeap(void);
__declspec(dllimport) void* __stdcall HeapAlloc(void* heap, unsigned long flags, unsigned long long bytes);
__declspec(dllimport) int __stdcall HeapFree(void* heap, unsigned long flags, void* memory);

_Static_assert(sizeof(__cplus_windows_sockaddr_ipv4) == 16, "Windows IPv4 socket address ABI");
_Static_assert(sizeof(__cplus_windows_sockaddr_ipv6) == 28, "Windows IPv6 socket address ABI");
_Static_assert(sizeof(__cplus_windows_native_socket) == 8, "Windows socket handle ABI");
_Static_assert(sizeof(__cplus_windows_procedure) == 8, "Windows procedure pointer ABI");
_Static_assert(sizeof(__cplus_windows_addrinfo_w) == 48, "Windows ADDRINFOW ABI");
_Static_assert(__builtin_offsetof(__cplus_windows_addrinfo_w, address_length) == 16, "ADDRINFOW length offset");
_Static_assert(__builtin_offsetof(__cplus_windows_addrinfo_w, canonical_name) == 24, "ADDRINFOW canonical-name offset");
_Static_assert(__builtin_offsetof(__cplus_windows_addrinfo_w, address) == 32, "ADDRINFOW address offset");
_Static_assert(__builtin_offsetof(__cplus_windows_addrinfo_w, next) == 40, "ADDRINFOW next offset");

#define __CPLUS_WINDOWS_AF_INET 2
#define __CPLUS_WINDOWS_AF_INET6 23
#define __CPLUS_WINDOWS_SOCK_STREAM 1
#define __CPLUS_WINDOWS_SOCK_DGRAM 2
#define __CPLUS_WINDOWS_SD_RECEIVE 0
#define __CPLUS_WINDOWS_SD_SEND 1
#define __CPLUS_WINDOWS_SD_BOTH 2
#define __CPLUS_WINDOWS_LOAD_LIBRARY_SEARCH_SYSTEM32 0x00000800UL
#define __CPLUS_WINDOWS_INVALID_SOCKET 0xffffffffffffffffULL
#define __CPLUS_WINDOWS_SOCKET_VERSION 0x0202U
#define __CPLUS_WINDOWS_MAX_TRANSFER 0x7fffffffULL
#define __CPLUS_WINDOWS_MAX_RESOLVED_ADDRESSES 256U
#define __CPLUS_WINDOWS_MAX_RESOLVER_NODES 4096U
#define __CPLUS_WINDOWS_AF_UNSPEC 0
#define __CPLUS_WINDOWS_WSA_HOST_NOT_FOUND 11001
#define __CPLUS_WINDOWS_WSA_NO_DATA 11004
#define __CPLUS_WINDOWS_WSA_AF_NO_SUPPORT 10047
/* FARPROC is pointer-sized; retain its full value while assigning a typed signature. */
#define __CPLUS_WINDOWS_FUNCTION(type, procedure) \
    (((union { __cplus_windows_procedure generic; type typed; }){ .generic = (procedure) }).typed)

static __cplus_windows_init_once __cplus_windows_winsock_once;
static __cplus_windows_winsock_api __cplus_windows_winsock;
static long __cplus_windows_winsock_result = CPLUS_PAL_UNSUPPORTED;
static void* __cplus_windows_winsock_module;

static long __cplus_windows_normalize_winsock_error(int error) {
    if (error == 10013) return CPLUS_PAL_ACCESS_DENIED; /* WSAEACCES */
    if (error == 10009 || error == 10014 || error == 10022 || error == 10038) {
        return CPLUS_PAL_INVALID_ARGUMENT; /* WSAEBADF, WSAEFAULT, WSAEINVAL, WSAENOTSOCK */
    }
    if (error == 10041 || error == 10042 || error == 10043 || error == 10044 ||
        error == 10045 || error == 10046 || error == 10047 || error == 10092) {
        return CPLUS_PAL_UNSUPPORTED;
    }
    return CPLUS_PAL_NETWORK_ERROR;
}

static int __stdcall __cplus_windows_initialize_winsock(
    __cplus_windows_init_once* once,
    void* parameter,
    void** context) {
    static const unsigned short module_name[] = {
        'w', 's', '2', '_', '3', '2', '.', 'd', 'l', 'l', 0
    };
    static const char* const symbol_names[] = {
        "WSAStartup", "WSACleanup", "socket", "bind", "listen", "accept",
        "connect", "getsockname", "getpeername", "send", "recv", "sendto",
        "recvfrom", "shutdown", "closesocket", "WSAGetLastError",
        "GetAddrInfoW", "FreeAddrInfoW"
    };
    union {
        unsigned long long alignment;
        unsigned char bytes[512];
    } startup_data;
    __cplus_windows_procedure procedures[18];
    unsigned int index;
    int startup_result;
    (void)once;
    (void)parameter;
    (void)context;

    if (!__cplus_windows_winsock_module) {
        __cplus_windows_winsock_module = LoadLibraryExW(
            module_name,
            (void*)0,
            __CPLUS_WINDOWS_LOAD_LIBRARY_SEARCH_SYSTEM32);
    }
    if (!__cplus_windows_winsock_module) {
        __cplus_windows_winsock_result = CPLUS_PAL_UNSUPPORTED;
        return 1;
    }
    for (index = 0; index < 18; index++) {
        procedures[index] = GetProcAddress(__cplus_windows_winsock_module, symbol_names[index]);
        if (!procedures[index]) {
            __cplus_windows_winsock_result = CPLUS_PAL_UNSUPPORTED;
            return 1;
        }
    }
    __cplus_windows_winsock.startup = __CPLUS_WINDOWS_FUNCTION(__cplus_wsa_startup_fn, procedures[0]);
    __cplus_windows_winsock.cleanup = __CPLUS_WINDOWS_FUNCTION(__cplus_wsa_cleanup_fn, procedures[1]);
    __cplus_windows_winsock.socket = __CPLUS_WINDOWS_FUNCTION(__cplus_wsa_socket_fn, procedures[2]);
    __cplus_windows_winsock.bind = __CPLUS_WINDOWS_FUNCTION(__cplus_wsa_bind_fn, procedures[3]);
    __cplus_windows_winsock.listen = __CPLUS_WINDOWS_FUNCTION(__cplus_wsa_listen_fn, procedures[4]);
    __cplus_windows_winsock.accept = __CPLUS_WINDOWS_FUNCTION(__cplus_wsa_accept_fn, procedures[5]);
    __cplus_windows_winsock.connect = __CPLUS_WINDOWS_FUNCTION(__cplus_wsa_connect_fn, procedures[6]);
    __cplus_windows_winsock.get_socket_name = __CPLUS_WINDOWS_FUNCTION(__cplus_wsa_get_name_fn, procedures[7]);
    __cplus_windows_winsock.get_peer_name = __CPLUS_WINDOWS_FUNCTION(__cplus_wsa_get_name_fn, procedures[8]);
    __cplus_windows_winsock.send = __CPLUS_WINDOWS_FUNCTION(__cplus_wsa_send_fn, procedures[9]);
    __cplus_windows_winsock.receive = __CPLUS_WINDOWS_FUNCTION(__cplus_wsa_receive_fn, procedures[10]);
    __cplus_windows_winsock.send_to = __CPLUS_WINDOWS_FUNCTION(__cplus_wsa_send_to_fn, procedures[11]);
    __cplus_windows_winsock.receive_from = __CPLUS_WINDOWS_FUNCTION(__cplus_wsa_receive_from_fn, procedures[12]);
    __cplus_windows_winsock.shutdown = __CPLUS_WINDOWS_FUNCTION(__cplus_wsa_shutdown_fn, procedures[13]);
    __cplus_windows_winsock.close = __CPLUS_WINDOWS_FUNCTION(__cplus_wsa_close_fn, procedures[14]);
    __cplus_windows_winsock.get_error = __CPLUS_WINDOWS_FUNCTION(__cplus_wsa_get_error_fn, procedures[15]);
    __cplus_windows_winsock.get_addr_info_w =
        __CPLUS_WINDOWS_FUNCTION(__cplus_wsa_get_addr_info_w_fn, procedures[16]);
    __cplus_windows_winsock.free_addr_info_w =
        __CPLUS_WINDOWS_FUNCTION(__cplus_wsa_free_addr_info_w_fn, procedures[17]);

    startup_result = __cplus_windows_winsock.startup(
        __CPLUS_WINDOWS_SOCKET_VERSION,
        &startup_data);
    if (startup_result != 0) {
        __cplus_windows_winsock_result = startup_result == 10092
            ? CPLUS_PAL_UNSUPPORTED : CPLUS_PAL_NETWORK_ERROR;
        return 1;
    }
    __cplus_windows_winsock_result = 0;
    return 1;
}

static long __cplus_windows_ensure_winsock(void) {
    if (!InitOnceExecuteOnce(
            &__cplus_windows_winsock_once,
            __cplus_windows_initialize_winsock,
            (void*)0,
            (void**)0)) {
        return CPLUS_PAL_NETWORK_ERROR;
    }
    return __cplus_windows_winsock_result;
}

void __cplus_windows_network_cleanup(void) {
    if (__cplus_windows_winsock_result == 0) {
        __cplus_windows_winsock_result = CPLUS_PAL_UNSUPPORTED;
        __cplus_windows_winsock.cleanup();
    }
}

static int __cplus_windows_socket_handle_is_valid(cplus_socket_handle_t socket) {
    return socket >= 0 && (unsigned long long)socket != __CPLUS_WINDOWS_INVALID_SOCKET;
}

static unsigned short __cplus_windows_network_port(unsigned short host_port) {
    return (unsigned short)((host_port << 8) | (host_port >> 8));
}

static int __cplus_windows_encode_socket_address(
    const cplus_socket_address_t* source,
    __cplus_windows_sockaddr_storage* destination,
    int* length) {
    unsigned int index;
    if (!source || !destination || !length || source->reserved != 0) {
        return (int)CPLUS_PAL_INVALID_ARGUMENT;
    }
    if (source->family == CPLUS_SOCKET_IPV4) {
        if (source->scope_id != 0) return (int)CPLUS_PAL_INVALID_ARGUMENT;
        for (index = 4; index < 16; index++) {
            if (source->address[index] != 0) return (int)CPLUS_PAL_INVALID_ARGUMENT;
        }
        destination->ipv4.family = __CPLUS_WINDOWS_AF_INET;
        destination->ipv4.port = __cplus_windows_network_port(source->port);
        for (index = 0; index < 4; index++) destination->ipv4.address[index] = source->address[index];
        for (index = 0; index < 8; index++) destination->ipv4.zero[index] = 0;
        *length = 16;
        return 0;
    }
    if (source->family == CPLUS_SOCKET_IPV6) {
        destination->ipv6.family = __CPLUS_WINDOWS_AF_INET6;
        destination->ipv6.port = __cplus_windows_network_port(source->port);
        destination->ipv6.flowinfo = 0;
        for (index = 0; index < 16; index++) destination->ipv6.address[index] = source->address[index];
        destination->ipv6.scope_id = source->scope_id;
        *length = 28;
        return 0;
    }
    return (int)CPLUS_PAL_UNSUPPORTED;
}

static int __cplus_windows_decode_socket_address(
    const __cplus_windows_sockaddr_storage* source,
    int length,
    cplus_socket_address_t* destination) {
    unsigned int index;
    if (!source || !destination) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    destination->reserved = 0;
    for (index = 0; index < 16; index++) destination->address[index] = 0;
    destination->scope_id = 0;
    if (source->ipv4.family == __CPLUS_WINDOWS_AF_INET && length >= 16) {
        destination->family = CPLUS_SOCKET_IPV4;
        destination->port = __cplus_windows_network_port(source->ipv4.port);
        for (index = 0; index < 4; index++) destination->address[index] = source->ipv4.address[index];
        return 0;
    }
    if (source->ipv6.family == __CPLUS_WINDOWS_AF_INET6 && length >= 28) {
        destination->family = CPLUS_SOCKET_IPV6;
        destination->port = __cplus_windows_network_port(source->ipv6.port);
        for (index = 0; index < 16; index++) destination->address[index] = source->ipv6.address[index];
        destination->scope_id = source->ipv6.scope_id;
        return 0;
    }
    return (int)CPLUS_PAL_UNSUPPORTED;
}

static long long __cplus_windows_socket_error(void) {
    return (long long)__cplus_windows_normalize_winsock_error(__cplus_windows_winsock.get_error());
}

static long long __cplus_windows_socket_result(__cplus_windows_native_socket socket) {
    if (socket == __CPLUS_WINDOWS_INVALID_SOCKET) return __cplus_windows_socket_error();
    if (socket > 0x7fffffffffffffffULL) {
        __cplus_windows_winsock.close(socket);
        return CPLUS_PAL_NETWORK_ERROR;
    }
    return (long long)socket;
}

long long platform_socket_open(unsigned int family, unsigned int kind) {
    int native_family;
    int native_kind;
    __cplus_windows_native_socket socket;
    long initialized;
    if (family == CPLUS_SOCKET_IPV4) native_family = __CPLUS_WINDOWS_AF_INET;
    else if (family == CPLUS_SOCKET_IPV6) native_family = __CPLUS_WINDOWS_AF_INET6;
    else return CPLUS_PAL_UNSUPPORTED;
    if (kind == CPLUS_SOCKET_STREAM) native_kind = __CPLUS_WINDOWS_SOCK_STREAM;
    else if (kind == CPLUS_SOCKET_DATAGRAM) native_kind = __CPLUS_WINDOWS_SOCK_DGRAM;
    else return CPLUS_PAL_UNSUPPORTED;
    initialized = __cplus_windows_ensure_winsock();
    if (initialized < 0) return initialized;
    socket = __cplus_windows_winsock.socket(native_family, native_kind, 0);
    return __cplus_windows_socket_result(socket);
}

int platform_socket_bind(cplus_socket_handle_t socket, const cplus_socket_address_t* address) {
    __cplus_windows_sockaddr_storage native_address;
    int address_length;
    int encode_result;
    if (!__cplus_windows_socket_handle_is_valid(socket) || !address) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    if (__cplus_windows_ensure_winsock() < 0) return (int)__cplus_windows_winsock_result;
    encode_result = __cplus_windows_encode_socket_address(address, &native_address, &address_length);
    if (encode_result < 0) return encode_result;
    return __cplus_windows_winsock.bind((unsigned long long)socket, &native_address, address_length) == 0
        ? 0 : (int)__cplus_windows_socket_error();
}

int platform_socket_listen(cplus_socket_handle_t socket, int backlog) {
    if (!__cplus_windows_socket_handle_is_valid(socket) || backlog < 0) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    if (__cplus_windows_ensure_winsock() < 0) return (int)__cplus_windows_winsock_result;
    return __cplus_windows_winsock.listen((unsigned long long)socket, backlog) == 0
        ? 0 : (int)__cplus_windows_socket_error();
}

long long platform_socket_accept(cplus_socket_handle_t socket, cplus_socket_address_t* peer) {
    __cplus_windows_sockaddr_storage native_address;
    int address_length = (int)sizeof(native_address);
    __cplus_windows_native_socket accepted;
    int decode_result;
    if (!__cplus_windows_socket_handle_is_valid(socket)) return CPLUS_PAL_INVALID_ARGUMENT;
    if (__cplus_windows_ensure_winsock() < 0) return __cplus_windows_winsock_result;
    accepted = __cplus_windows_winsock.accept(
        (unsigned long long)socket,
        peer ? &native_address : (void*)0,
        peer ? &address_length : (int*)0);
    if (accepted == __CPLUS_WINDOWS_INVALID_SOCKET) return __cplus_windows_socket_error();
    if (peer) {
        decode_result = __cplus_windows_decode_socket_address(&native_address, address_length, peer);
        if (decode_result < 0) {
            __cplus_windows_winsock.close(accepted);
            return decode_result;
        }
    }
    return __cplus_windows_socket_result(accepted);
}

int platform_socket_connect(cplus_socket_handle_t socket, const cplus_socket_address_t* address) {
    __cplus_windows_sockaddr_storage native_address;
    int address_length;
    int encode_result;
    if (!__cplus_windows_socket_handle_is_valid(socket) || !address) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    if (__cplus_windows_ensure_winsock() < 0) return (int)__cplus_windows_winsock_result;
    encode_result = __cplus_windows_encode_socket_address(address, &native_address, &address_length);
    if (encode_result < 0) return encode_result;
    return __cplus_windows_winsock.connect((unsigned long long)socket, &native_address, address_length) == 0
        ? 0 : (int)__cplus_windows_socket_error();
}

int platform_socket_get_address(cplus_socket_handle_t socket, int peer, cplus_socket_address_t* address) {
    __cplus_windows_sockaddr_storage native_address;
    int address_length = (int)sizeof(native_address);
    int result;
    if (!__cplus_windows_socket_handle_is_valid(socket) || !address || (peer != 0 && peer != 1)) {
        return (int)CPLUS_PAL_INVALID_ARGUMENT;
    }
    if (__cplus_windows_ensure_winsock() < 0) return (int)__cplus_windows_winsock_result;
    result = peer
        ? __cplus_windows_winsock.get_peer_name((unsigned long long)socket, &native_address, &address_length)
        : __cplus_windows_winsock.get_socket_name((unsigned long long)socket, &native_address, &address_length);
    if (result != 0) return (int)__cplus_windows_socket_error();
    return __cplus_windows_decode_socket_address(&native_address, address_length, address);
}

long long platform_socket_send(cplus_socket_handle_t socket, const void* buffer, unsigned long long length) {
    int result;
    if (!__cplus_windows_socket_handle_is_valid(socket) || (!buffer && length != 0) ||
        length > __CPLUS_WINDOWS_MAX_TRANSFER) return CPLUS_PAL_INVALID_ARGUMENT;
    if (length == 0) return 0;
    if (__cplus_windows_ensure_winsock() < 0) return __cplus_windows_winsock_result;
    result = __cplus_windows_winsock.send(
        (unsigned long long)socket,
        (const char*)buffer,
        (int)length,
        0);
    return result == -1 ? __cplus_windows_socket_error() : (long long)result;
}

long long platform_socket_receive(cplus_socket_handle_t socket, void* buffer, unsigned long long capacity) {
    int result;
    if (!__cplus_windows_socket_handle_is_valid(socket) || (!buffer && capacity != 0) ||
        capacity > __CPLUS_WINDOWS_MAX_TRANSFER) return CPLUS_PAL_INVALID_ARGUMENT;
    if (capacity == 0) return 0;
    if (__cplus_windows_ensure_winsock() < 0) return __cplus_windows_winsock_result;
    result = __cplus_windows_winsock.receive(
        (unsigned long long)socket,
        (char*)buffer,
        (int)capacity,
        0);
    return result == -1 ? __cplus_windows_socket_error() : (long long)result;
}

long long platform_socket_send_to(
    cplus_socket_handle_t socket,
    const void* buffer,
    unsigned long long length,
    const cplus_socket_address_t* destination) {
    __cplus_windows_sockaddr_storage native_address;
    static const char empty_payload = 0;
    const char* payload = buffer ? (const char*)buffer : &empty_payload;
    int address_length;
    int encode_result;
    int result;
    if (!__cplus_windows_socket_handle_is_valid(socket) || (!buffer && length != 0) ||
        length > __CPLUS_WINDOWS_MAX_TRANSFER || !destination) return CPLUS_PAL_INVALID_ARGUMENT;
    if (__cplus_windows_ensure_winsock() < 0) return __cplus_windows_winsock_result;
    encode_result = __cplus_windows_encode_socket_address(destination, &native_address, &address_length);
    if (encode_result < 0) return encode_result;
    result = __cplus_windows_winsock.send_to(
        (unsigned long long)socket,
        payload,
        (int)length,
        0,
        &native_address,
        address_length);
    return result == -1 ? __cplus_windows_socket_error() : (long long)result;
}

long long platform_socket_receive_from(
    cplus_socket_handle_t socket,
    void* buffer,
    unsigned long long capacity,
    cplus_socket_address_t* source) {
    __cplus_windows_sockaddr_storage native_address;
    char empty_payload;
    char* payload = buffer ? (char*)buffer : &empty_payload;
    int address_length = (int)sizeof(native_address);
    int result;
    int decode_result;
    if (!__cplus_windows_socket_handle_is_valid(socket) || (!buffer && capacity != 0) ||
        capacity > __CPLUS_WINDOWS_MAX_TRANSFER) return CPLUS_PAL_INVALID_ARGUMENT;
    if (__cplus_windows_ensure_winsock() < 0) return __cplus_windows_winsock_result;
    result = __cplus_windows_winsock.receive_from(
        (unsigned long long)socket,
        payload,
        (int)capacity,
        0,
        source ? &native_address : (void*)0,
        source ? &address_length : (int*)0);
    if (result == -1) return __cplus_windows_socket_error();
    if (source) {
        decode_result = __cplus_windows_decode_socket_address(&native_address, address_length, source);
        if (decode_result < 0) return decode_result;
    }
    return (long long)result;
}

int platform_socket_shutdown(cplus_socket_handle_t socket, unsigned int direction) {
    if (!__cplus_windows_socket_handle_is_valid(socket) || direction > CPLUS_SOCKET_SHUTDOWN_BOTH) {
        return (int)CPLUS_PAL_INVALID_ARGUMENT;
    }
    if (__cplus_windows_ensure_winsock() < 0) return (int)__cplus_windows_winsock_result;
    return __cplus_windows_winsock.shutdown((unsigned long long)socket, (int)direction) == 0
        ? 0 : (int)__cplus_windows_socket_error();
}

int platform_socket_close(cplus_socket_handle_t socket) {
    if (!__cplus_windows_socket_handle_is_valid(socket)) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    if (__cplus_windows_ensure_winsock() < 0) return (int)__cplus_windows_winsock_result;
    return __cplus_windows_winsock.close((unsigned long long)socket) == 0
        ? 0 : (int)__cplus_windows_socket_error();
}

static int __cplus_windows_resolver_error(int error) {
    if (error == __CPLUS_WINDOWS_WSA_HOST_NOT_FOUND || error == __CPLUS_WINDOWS_WSA_NO_DATA) {
        return (int)CPLUS_PAL_NOT_FOUND;
    }
    if (error == __CPLUS_WINDOWS_WSA_AF_NO_SUPPORT) return (int)CPLUS_PAL_UNSUPPORTED;
    if (error == 10022) return (int)CPLUS_PAL_INVALID_ARGUMENT;
    return (int)CPLUS_PAL_NETWORK_ERROR;
}

static int __cplus_windows_resolved_address_equal(
    const cplus_socket_address_t* left,
    const cplus_socket_address_t* right) {
    unsigned int length;
    unsigned int index;
    if (left->family != right->family || left->port != right->port ||
        left->scope_id != right->scope_id) return 0;
    if (left->family == CPLUS_SOCKET_IPV4) length = 4U;
    else if (left->family == CPLUS_SOCKET_IPV6) length = 16U;
    else return 0;
    for (index = 0; index < length; index++) {
        if (left->address[index] != right->address[index]) return 0;
    }
    return 1;
}

static int __cplus_windows_store_resolved_address(
    cplus_socket_address_t results[__CPLUS_WINDOWS_MAX_RESOLVED_ADDRESSES],
    unsigned int* result_count,
    const cplus_socket_address_t* address) {
    unsigned int index;
    for (index = 0; index < *result_count; index++) {
        if (__cplus_windows_resolved_address_equal(&results[index], address)) return 0;
    }
    if (*result_count >= __CPLUS_WINDOWS_MAX_RESOLVED_ADDRESSES) {
        return (int)CPLUS_PAL_NETWORK_ERROR;
    }
    results[(*result_count)++] = *address;
    return 0;
}

static int __cplus_windows_copy_resolved_addresses(
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
    cplus_socket_address_t* results = (void*)0;
    cplus_socket_address_t numeric = {0};
    __cplus_windows_addrinfo_w hints = {0};
    __cplus_windows_addrinfo_w* native_results = (void*)0;
    void* heap = (void*)0;
    unsigned short wide_hostname[256];
    char ascii_hostname[254];
    long long ascii_length;
    unsigned int result_count = 0;
    unsigned int index;
    unsigned int native_count = 0;
    int native_status;
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
            return __cplus_windows_copy_resolved_addresses(&numeric, 1U, addresses, capacity, count);
        }
    }
    if (family == CPLUS_SOCKET_IPV6 || family == CPLUS_SOCKET_ANY_FAMILY) {
        status = platform_network_parse_address(CPLUS_SOCKET_IPV6, hostname, &numeric);
        if (status == 0) {
            numeric.port = port;
            return __cplus_windows_copy_resolved_addresses(&numeric, 1U, addresses, capacity, count);
        }
    }
    ascii_length = __cplus_network_hostname_to_ascii(
        hostname, ascii_hostname, sizeof(ascii_hostname));
    if (ascii_length < 0) return (int)ascii_length;
    for (index = 0; index < (unsigned int)ascii_length; index++) {
        wide_hostname[index] = (unsigned short)(unsigned char)ascii_hostname[index];
    }
    wide_hostname[ascii_length] = (unsigned short)'.';
    wide_hostname[ascii_length + 1] = 0;
    hints.family = family == CPLUS_SOCKET_IPV4 ? __CPLUS_WINDOWS_AF_INET
        : family == CPLUS_SOCKET_IPV6 ? __CPLUS_WINDOWS_AF_INET6 : __CPLUS_WINDOWS_AF_UNSPEC;
    status = (int)__cplus_windows_ensure_winsock();
    if (status < 0) return status;
    heap = GetProcessHeap();
    if (!heap) return (int)CPLUS_PAL_IO_ERROR;
    results = (cplus_socket_address_t*)HeapAlloc(
        heap, 0, (unsigned long long)sizeof(cplus_socket_address_t) *
            __CPLUS_WINDOWS_MAX_RESOLVED_ADDRESSES);
    if (!results) return (int)CPLUS_PAL_IO_ERROR;
    native_status = __cplus_windows_winsock.get_addr_info_w(
        wide_hostname, (const unsigned short*)0, &hints, &native_results);
    if (native_status != 0) {
        if (native_results) __cplus_windows_winsock.free_addr_info_w(native_results);
        HeapFree(heap, 0, results);
        return __cplus_windows_resolver_error(native_status);
    }
    for (__cplus_windows_addrinfo_w* item = native_results; item; item = item->next) {
        cplus_socket_address_t address = {0};
        if (native_count++ >= __CPLUS_WINDOWS_MAX_RESOLVER_NODES) {
            status = (int)CPLUS_PAL_NETWORK_ERROR;
            break;
        }
        if (!item->address) {
            status = (int)CPLUS_PAL_NETWORK_ERROR;
            break;
        }
        if (item->family == __CPLUS_WINDOWS_AF_INET &&
            (family == CPLUS_SOCKET_IPV4 || family == CPLUS_SOCKET_ANY_FAMILY) &&
            item->address_length >= sizeof(__cplus_windows_sockaddr_ipv4)) {
            const __cplus_windows_sockaddr_ipv4* native_address =
                (const __cplus_windows_sockaddr_ipv4*)item->address;
            address.family = CPLUS_SOCKET_IPV4;
            address.port = port;
            for (index = 0; index < 4U; index++) address.address[index] = native_address->address[index];
        } else if (item->family == __CPLUS_WINDOWS_AF_INET6 &&
            (family == CPLUS_SOCKET_IPV6 || family == CPLUS_SOCKET_ANY_FAMILY) &&
            item->address_length >= sizeof(__cplus_windows_sockaddr_ipv6)) {
            const __cplus_windows_sockaddr_ipv6* native_address =
                (const __cplus_windows_sockaddr_ipv6*)item->address;
            address.family = CPLUS_SOCKET_IPV6;
            address.port = port;
            address.scope_id = native_address->scope_id;
            for (index = 0; index < 16U; index++) address.address[index] = native_address->address[index];
        } else {
            continue;
        }
        status = __cplus_windows_store_resolved_address(results, &result_count, &address);
        if (status < 0) break;
    }
    if (native_results) __cplus_windows_winsock.free_addr_info_w(native_results);
    if (status < 0) {
        HeapFree(heap, 0, results);
        return status;
    }
    if (result_count == 0) {
        HeapFree(heap, 0, results);
        return (int)CPLUS_PAL_NOT_FOUND;
    }
    status = __cplus_windows_copy_resolved_addresses(
        results, result_count, addresses, capacity, count);
    HeapFree(heap, 0, results);
    return status;
}
