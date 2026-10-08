#ifndef CPLUS_NETWORK_INTERNAL_H
#define CPLUS_NETWORK_INTERNAL_H

#include "cplus_platform.h"

/* Convert a UTF-8 DNS hostname to its ASCII wire-label spelling. The caller
   supplies a NUL-terminated output buffer and owns the returned text. */
long long __cplus_network_hostname_to_ascii(
    const char* hostname,
    char* output,
    unsigned long long capacity);

#endif
