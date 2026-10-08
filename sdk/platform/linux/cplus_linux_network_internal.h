#ifndef CPLUS_LINUX_NETWORK_INTERNAL_H
#define CPLUS_LINUX_NETWORK_INTERNAL_H

#include "cplus_platform.h"

/* Deterministic resolver seam used by the Linux loopback DNS fixture. The
   public resolver obtains nameservers from /etc/resolv.conf and uses 2000 ms. */
int __cplus_linux_network_resolve_with_nameservers(
    const char* ascii_hostname,
    unsigned int family,
    unsigned short port,
    const cplus_socket_address_t* nameservers,
    unsigned int nameserver_count,
    unsigned int timeout_milliseconds,
    cplus_socket_address_t* addresses,
    unsigned long long capacity,
    unsigned long long* count);

int __cplus_linux_network_parse_resolv_conf(
    const char* contents,
    unsigned int length,
    cplus_socket_address_t nameservers[3],
    unsigned int* nameserver_count);

#endif
