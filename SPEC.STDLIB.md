# C+ Standard Library, Runtime, SDK and Platform ABI Specification

## 1. Scope

This document specifies the C+ Standard Library and its associated SDK/runtime architecture.

The C+ Standard Library SHALL be implemented primarily in C+ source and SHALL serve simultaneously as:

- the source-level C+ SDK;
- the C-compatible standard library implementation;
- the C+ runtime;
- the compiler support runtime;
- the platform abstraction layer;
- the source of target-specific runtime implementations.

A pure C+ program SHALL NOT require an external libc implementation where the selected target profile supports the C+ self-hosted runtime.

The system libc, such as glibc, musl, MSVCRT/UCRT, SHALL therefore not be a mandatory runtime dependency of C+ programs.

The operating-system ABI remains an unavoidable dependency.

The architectural model is:

```text
C+ application
      │
      ▼
C+ standard library
      │
      ├── native C+ API
      └── C libc compatibility API
      │
      ▼
C+ runtime
      │
      ▼
platform abstraction layer
      │
      ├── Linux kernel ABI
      ├── Windows system API
      └── Darwin system ABI
      │
      ▼
operating system
```

---

# 2. Terminology

## 2.1 C+ SDK

The **C+ SDK** is the complete compiler-visible development environment distributed with the C+ toolchain.

It contains:

```text
standard-library C+ source
libc-compatible C+ source
runtime C+ source
platform ABI declarations
target descriptions
compiler intrinsic declarations
generated C compatibility headers
semantic module metadata
optional precompiled caches
optional precompiled target objects
```

## 2.2 Standard Library

The **C+ Standard Library**, hereafter `std`, is the preferred native C+ programming API.

Examples include:

```text
std.fixed_width
std.core
std.mem
std.string
std.io
std.fs
std.process
std.time
std.thread
std.sync
std.math
std.env
std.net
```

It is not constrained to the historical C library API.

## 2.3 C Compatibility Library

The **C Compatibility Library**, hereafter `libc`, implements the standardized C library interface and exports conventional C ABI symbols such as:

```text
malloc
free
memcpy
strlen
printf
fopen
time
```

Its implementation MAY delegate to `std`.

For example:

```text
printf
   ↓
libc stdio implementation
   ↓
std.io
   ↓
platform I/O
```

## 2.4 Runtime

The **C+ Runtime**, hereafter `rt`, contains facilities required for program execution independently of application-level standard-library usage.

Examples include:

```text
process startup
process termination
TLS initialization
compiler-generated helper functions
panic/abort support
closure runtime helpers
stack protection support
low-level memory primitives
```

## 2.5 Platform Abstraction Layer

The **Platform Abstraction Layer**, or PAL, is the narrow interface between portable C+ runtime code and the host operating system.

The PAL SHALL be kept substantially smaller than the standard library.

The baseline PAL SHALL expose a uniform, versioned C ABI to both the C+
runtime and native SDK services. The baseline operations are:

```c
long long platform_read_stdin(void* buffer, unsigned long long capacity);
long long platform_write_stdout(const char* buffer, unsigned long long length);
long long platform_write_stderr(const char* buffer, unsigned long long length);
int platform_process_exit(int status);
```

Reads and writes MAY be partial. A zero-length request SHALL return zero, and
standard-input EOF SHALL return zero. Negative results SHALL use the stable
PAL error values. The C stdio façade SHALL route `stdin`, `stdout`, and
`stderr` operations to their corresponding channels; it SHALL NOT silently
route `fprintf(stderr, ...)` to stdout or report EOF for unread input.

The version-2 file-service extension SHALL expose the following additional
operations. The explicit-width handle and size types are required because
Linux uses LP64 while Windows uses LLP64:

```c
typedef long long cplus_file_handle_t;
typedef long long cplus_file_result_t;
typedef unsigned long long cplus_file_size_t;
typedef unsigned long long cplus_file_mode_t;

#define CPLUS_FILE_READ     0x0001ULL
#define CPLUS_FILE_WRITE    0x0002ULL
#define CPLUS_FILE_CREATE   0x0004ULL
#define CPLUS_FILE_TRUNCATE 0x0008ULL

cplus_file_result_t platform_file_open(
    const char* path, cplus_file_mode_t mode);
cplus_file_result_t platform_file_read(
    cplus_file_handle_t handle, void* buffer, cplus_file_size_t length);
cplus_file_result_t platform_file_write(
    cplus_file_handle_t handle, const void* buffer, cplus_file_size_t length);
int platform_file_close(cplus_file_handle_t handle);
int platform_file_rename(const char* source, const char* target);
```

File paths entering this ABI SHALL be canonical UTF-8 strings with `/`
separators. Successful `open` returns a non-negative opaque handle; successful
`read` and `write` return the number of bytes transferred and MAY be partial;
successful `close` and `rename` return zero. Every target adapter SHALL map
its native failure space to the shared negative values
`CPLUS_PAL_INVALID_ARGUMENT`, `CPLUS_PAL_NOT_FOUND`,
`CPLUS_PAL_ACCESS_DENIED`, `CPLUS_PAL_IO_ERROR`, or
`CPLUS_PAL_UNSUPPORTED`. Native `errno`, `GetLastError`, and raw syscall
numbers SHALL NOT cross this boundary.

PAL version 3 SHALL preserve every version-2 operation and add the following
filesystem services. The metadata record uses fixed-width fields and has a
stable 32-byte layout on supported targets:

```c
typedef struct cplus_file_metadata_t {
    unsigned long long size_bytes;
    long long modified_seconds_utc;
    unsigned int modified_nanoseconds;
    unsigned int kind;
    unsigned int reserved0;
    unsigned int reserved1;
} cplus_file_metadata_t;

#define CPLUS_FILE_KIND_REGULAR   1U
#define CPLUS_FILE_KIND_DIRECTORY 2U
#define CPLUS_FILE_KIND_OTHER     3U
#define CPLUS_PAL_BUFFER_TOO_SMALL (-7L)

#define CPLUS_SEEK_BEGIN   0U
#define CPLUS_SEEK_CURRENT 1U
#define CPLUS_SEEK_END     2U

long long platform_file_seek(
    cplus_file_handle_t handle, long long offset, unsigned int origin);
int platform_file_metadata(
    const char* path, cplus_file_metadata_t* metadata);
int platform_directory_create(const char* path);
int platform_file_remove(const char* path);
int platform_directory_remove(const char* path);
cplus_file_result_t platform_directory_open(const char* path);
long long platform_directory_read(
    cplus_file_handle_t handle, char* utf8_name, cplus_file_size_t capacity);
int platform_directory_close(cplus_file_handle_t handle);
```

`platform_file_seek` SHALL return the resulting non-negative absolute byte
offset or a stable negative PAL error. Metadata lookup SHALL follow symbolic
links and report size, UTC modification seconds since 1970-01-01, nanoseconds
in `[0, 999999999]`, and one of the declared kinds; reserved fields SHALL be
zero. Directory creation SHALL create one directory level and SHALL NOT create
missing parents. File removal SHALL remove a file or symbolic link but SHALL
NOT remove a directory; directory removal SHALL succeed only for an empty
directory.

Directory iteration SHALL omit `.` and `..`. A successful read returns the
UTF-8 name length excluding its terminating NUL; zero means end-of-directory,
and a negative result means failure. Capacity includes space for the NUL. If
the next name does not fit, the adapter SHALL return
`CPLUS_PAL_BUFFER_TOO_SMALL` without consuming that entry. Names that cannot
be represented as valid UTF-8 SHALL return `CPLUS_PAL_UNSUPPORTED`. Directory
handles are opaque and SHALL be closed with `platform_directory_close`.

PAL version 4 SHALL preserve every version-3 operation and add the clock
services specified in §14, thread/synchronization services specified in §15,
and socket transport services specified in §16. `CPLUS_PAL_API_VERSION` SHALL
be 4.

Portable runtime and standard-library code SHALL call these PAL operations and
SHALL NOT contain Linux syscall instructions, Windows DLL declarations, host
libc includes, or host libc symbol references. Those details belong only to
the selected target adapter. New OS-dependent facilities SHALL be added as
PAL operations or versioned shims so that `std` and the runtime retain one
source-level API across targets.

## 2.6 Platform ABI Adapter

A **Platform ABI Adapter** implements the PAL for one target family.

Examples:

```text
Linux syscall adapter
Windows Win32/NT user-mode adapter
Darwin system adapter
```

An adapter MAY implement an operation with direct kernel syscalls, documented
system-DLL imports, or another supported OS primitive. The choice SHALL be
invisible to its PAL callers and SHALL be recorded in target ABI metadata.

## 2.7 Target ABI Descriptor

A **Target ABI Descriptor**, or TAD, is compiler-readable metadata describing a compilation target.

---

# 3. Architectural objective

The implementation SHALL maximize this relationship:

```text
platform-independent code
─────────────────────────
platform-specific code
```

The overwhelming majority of:

```text
allocator logic
stdio buffering
string algorithms
formatting
sorting
conversion
containers
error handling
math helpers
text processing
```

SHALL be written once in C+.

Only functionality inherently dependent upon the operating system or processor SHALL reside below the PAL.

For example:

```text
malloc()
   │
   ▼
portable allocator
   │
   ▼
os_allocate_pages()
   │
   ├── Linux mmap
   ├── Windows VirtualAlloc
   └── Darwin mmap/system API
```

---

# 4. Distribution model

The C+ SDK SHALL be usable as source.

A canonical SDK layout is:

```text
sdk/
├── manifest/
│   └── sdk.toml
│
├── std/
│   └── src/
│
├── libc/
│   ├── src/
│   └── include/
│
├── runtime/
│   └── src/
│
├── platform/
│   ├── api/
│   ├── linux/
│   ├── windows/
│   └── darwin/
│
├── abi/
│   ├── linux-x86_64.toml
│   ├── linux-aarch64.toml
│   ├── windows-x86_64.toml
│   ├── windows-aarch64.toml
│   ├── darwin-x86_64.toml
│   └── darwin-aarch64.toml
│
├── intrinsics/
│   └── intrinsics.cp
│
├── startup/
│   └── ...
│
├── metadata/
│   └── ...
│
└── cache/
    └── optional precompiled artifacts
```

Precompiled objects MAY be provided as an optimization.

They SHALL NOT be the canonical definition of the library.

The canonical definition is the source and ABI metadata.

---

# 5. Build profiles

The toolchain SHALL define three runtime profiles.

## 5.1 `freestanding`

```text
--runtime=freestanding
```

Provides:

```text
language primitives
compiler runtime
memory primitives
no hosted I/O requirement
no process/environment assumptions
```

This profile is appropriate for:

```text
kernels
embedded environments
boot code
custom operating systems
restricted runtime environments
```

No OS PAL is required unless explicitly imported.

## 5.2 `cplus`

```text
--runtime=cplus
```

This is the default hosted profile.

It uses:

```text
C+ std
C+ libc
C+ runtime
C+ platform adapter
```

It SHALL NOT require a foreign libc.

## 5.3 `system`

```text
--runtime=system
```

Allows the generated program to use the target system libc/runtime.

This exists primarily for:

```text
compatibility
incremental migration
platform debugging
interop testing
```

It SHALL NOT be the default for pure C+ applications.

---

# 6. Native standard-library packages

The following packages form the initial native C+ standard library.

```text
std.fixed_width
std.core
std.mem
std.alloc
std.string
std.text
std.io
std.fs
std.env
std.process
std.time
std.math
std.thread
std.sync
std.net
std.random
std.collections
```

This package API is the preferred interface for C+ applications.

The C compatibility library is a compatibility façade over the same runtime where practical.

`std.fixed_width` is an optional source module for application-level naming
convenience. It exports `i8`, `i16`, `i32`, `i64`, `u8`, `u16`, `u32`, and
`u64` as ordinary typedef aliases of the corresponding exact-width C integer
types. Programs MUST import these names explicitly; they are not compiler
built-ins and are not injected into `std.core` or native SDK signatures.
The module also exports `i128` and `u128` only for the `linux-x86_64` target,
whose SDK ABI descriptor advertises the `int128` feature. The selected GCC- or
Clang-compatible C compiler MUST pass the C17 width/alignment capability probe
at link time. Other targets MUST omit these aliases from generated C and MUST
report `SEM411` if they are used; Windows and AArch64 remain unsupported until
their compiler and calling-convention ABIs are verified.

---

# 7. `std.core`

`std.core` provides foundational definitions requiring no hosted environment.

It SHALL include:

```text
usize / isize
pointer utilities
result/error primitives
option-like primitives where defined
byte operations
compiler-visible target metadata
basic comparison utilities
low-level numeric limits
```

No operating-system dependency is permitted.

`std.core` SHALL provide `usize` as an ordinary source alias of C `size_t` and
`isize` as an ordinary source alias of C `ptrdiff_t`. Their width and alignment
SHALL follow the selected target ABI; they SHALL NOT be replaced with a
fixed-width integer or inferred from the compiler host. Raw byte APIs SHALL use
`unsigned char` so byte values do not depend on plain-`char` signedness; the
module does not add a separate byte typedef. The module SHALL also provide
pointer/null/equality/offset/distance utilities, signed and unsigned size
comparisons, target-width queries, and `usize`/`isize` minimum and maximum
queries. Pointer distance is defined only for pointers into the same array
object (or one past it). The value carriers contain status and value data
only; PAL handles, raw syscall numbers, and host `errno` values are not part of
their representation. The unused `std_byte_t`, `std_size_t`, and
`std_index_t` aliases are not part of the API.

`std.core` SHALL NOT add custom `i8`/`i16`/`i32`/`i64`, `u8`/`u16`/`u32`/`u64`,
or 128-bit aliases to compiler built-ins or native SDK API signatures. Programs
that want concise fixed-width names MAY explicitly import the user-level
`std.fixed_width` source module. That module defines ordinary aliases over
the target's exact-width integer types (`i8` through `i64` over `int8_t`
through `int64_t`, and `u8` through `u64` over `uint8_t` through `uint64_t`);
it is not implicitly imported. The `i128` and `u128` aliases are available only
on `linux-x86_64`, where the compiler, generated-C mapping, and ABI support
have been verified. The implementation maps them to the C compiler extension
`__int128` and `unsigned __int128`, not to nonexistent standard `int128_t` or
`uint128_t` typedefs. Unsupported widths MUST produce `SEM411` rather than
silently changing width or signedness.

---

# 8. `std.mem`

`std.mem` SHALL provide:

```text
copy
move
set
compare
zero
alignment helpers
memory spans
raw-memory views
```

Its low-level implementation SHALL supply the compiler-visible equivalents of:

```text
memcpy
memmove
memset
memcmp
```

where required by generated C or the downstream C compiler.

Memory operation lengths and indexes SHALL use `usize`; memory data SHALL be
read and written as unsigned bytes.

The module SHALL provide checked size alignment-up and alignment predicates,
an unsigned-byte span view, and a raw untyped-memory view. Alignment-up SHALL
require a nonzero power-of-two alignment and SHALL return `std_usize_max()` on
invalid alignment or arithmetic overflow. An empty span/view has zero length;
an out-of-range span lookup returns null and does not dereference memory.

The native implementation also provides zeroing and unsigned-byte equality and
comparison. It SHALL preserve byte values independently of the signedness of
the target C `char` type.

---

# 9. `std.alloc`

The allocation architecture SHALL be:

```text
user allocator API
      │
      ▼
portable allocator
      │
      ▼
page allocator
      │
      ▼
PAL virtual-memory API
```

Public allocation operations SHALL include equivalents of:

```text
allocate
allocate_zeroed
resize
free
aligned allocation
```

The page allocator SHALL receive an explicit unsigned page count and return a
page-aligned region or null on failure. The PAL SHALL normalize operating
system allocation failures to the C+ allocation failure contract and SHALL not
expose host `errno` or OS error numbers through the native API.

An allocation request of zero bytes MAY produce a unique minimum allocation,
but it SHALL remain safe to pass to `free`. Alignment requests SHALL be powers
of two and at least pointer alignment; invalid alignment and arithmetic
overflow SHALL fail without allocating. `resize` SHALL preserve the prefix of
the old allocation up to the smaller old/new size and SHALL release the old
region only after a replacement region has been obtained. `free(null)` SHALL
be a no-op, and a successful release SHALL return the pages to the PAL.

The initial SDK runtime implements these operations in `allocator.c` over the
uniform PAL page ABI. The native C+ façade delegates to those runtime symbols;
it SHALL NOT use a fixed-size bootstrap arena in a claimed production profile.

The libc façade SHALL provide:

```text
malloc
calloc
realloc
free
aligned_alloc
```

---

# 10. `std.string` and `std.text`

`std.string` provides byte/string storage and manipulation.

`std.text` provides higher-level text and Unicode facilities.

The target-neutral baseline provides byte length, empty-text, ASCII, and
prefix operations. UTF-8 byte storage remains distinct from the C17 wide
character compatibility layer; target-specific encoding conversion belongs at
the documented PAL/libc boundary.

String/text byte lengths and traversal indexes SHALL use `usize`. Collection
slice lengths SHALL use `usize`, range endpoints and tested indexes SHALL use
`isize`, and a non-negative range length SHALL be returned as `usize`.

The initial `std.collections` value layer provides explicit non-owning slices
and half-open ranges. These values do not imply ownership or hidden allocation;
allocation policy remains the responsibility of the caller or selected
allocator.

This distinction prevents libc's historical null-terminated byte-string semantics from constraining native C+ text APIs.

The libc layer SHALL independently expose conventional:

```text
strlen
strcmp
strncmp
strcpy
strncpy
strcat
strchr
strstr
strtok
strerror
```

and related C-standard operations.

The self-hosted C17 profile provides bounded and unbounded formatted output,
the corresponding `va_list` entry points, basic character classification and
case conversion, C-locale selection, clock/time queries, and basic signal
registration. Stream operations that require filesystem-backed `FILE` state
remain layered on the native filesystem/stream stage and SHALL report an
explicit capability failure until that stage is selected.

The claimed Linux C17 profile also supplies UTF-8/wide conversion and
comparison, basic wide classification, compiler-intrinsic atomic operations,
thread-local `errno`, and an x86_64 context-switch implementation for
`setjmp`/`longjmp`. Targets without a corresponding context-switch adapter
SHALL report the facility as unavailable rather than link an unresolved
declaration.

---

# 11. `std.io`

`std.io` SHALL provide portable streams.

The fundamental PAL interface SHALL resemble:

```c
struct os_handle_t {
    usize value;
};

isize os_read(os_handle_t handle, mut void* dst, usize count);
isize os_write(os_handle_t handle, borrowed void* src, usize count);
void os_close(os_handle_t handle);
```

The PAL standard-channel operations are specified in §2.5.

File streams use the PAL file operations (version-2 operations are preserved
by version 3). `std.io` may layer
buffering and formatting over `platform_file_read`, `platform_file_write`, and
`platform_file_close`; it SHALL not expose target-specific descriptor or
HANDLE types.

The implementation of buffering, formatting, stream state and textual conversion SHALL reside above this PAL interface.

The initial C+ `std.io` file-stream façade SHALL provide open, read, write,
seek, close, and open-state operations over `std.fs`. It SHALL be unbuffered,
allow partial reads/writes, report end-of-file as a zero-byte read, and return
stable PAL errors for invalid operations. Its stored handle SHALL remain an
opaque signed 64-bit C+ value; it SHALL NOT expose a file descriptor, Windows
`HANDLE`, or target-specific stream structure.

The façade SHALL expose `std_file_stream_open(path, readable, writable,
create, truncate)`, `std_file_stream_is_open`, `std_file_stream_read`,
`std_file_stream_write`, `std_file_stream_seek`, and `std_file_stream_close`.
Open SHALL return a closed stream on failure. Read/write SHALL return an
`isize` byte count or a stable negative PAL error; seek SHALL return the new
absolute offset or an error. Close SHALL invalidate the stream only when the
underlying close succeeds. `std.fs` SHALL provide named mode accessors matching
the `CPLUS_FILE_*` flags and named error accessors for invalid-argument and
buffer-too-small results so C+ callers need not duplicate numeric constants.

The libc layer SHALL expose `FILE` and C stdio functions.

---

# 12. `std.fs`

`std.fs` SHALL provide portable filesystem operations.

At minimum:

```text
open
close
read
write
seek
metadata
create
remove
rename
directory iteration
path manipulation
```

The first concrete native façade operations are:

```c
#include <stddef.h>
#include <stdint.h>

int64_t std_fs_open(const char* path, uint64_t mode);
ptrdiff_t std_fs_read(int64_t handle, void* buffer, size_t size);
ptrdiff_t std_fs_write(int64_t handle, const void* buffer, size_t size);
int64_t std_fs_seek(int64_t handle, int64_t offset, uint32_t origin);
int std_fs_metadata(const char* path, struct std_file_metadata_t* metadata);
int std_fs_create_directory(const char* path);
int std_fs_remove_file(const char* path);
int std_fs_remove_directory(const char* path);
int64_t std_fs_directory_open(const char* path);
ptrdiff_t std_fs_directory_read(int64_t handle, char* name, size_t capacity);
int std_fs_directory_close(int64_t handle);
int std_fs_close(int64_t handle);
int std_fs_rename(const char* source, const char* target);
```

These functions are target-independent forwarding entry points. Negative
results SHALL use the stable PAL error values and SHALL NOT expose native OS
error numbers. `std_file_metadata_t` SHALL have the same field order and
32-byte layout as `cplus_file_metadata_t`.

The version-3 `std.fs` façade SHALL expose the PAL seek, metadata, create,
remove, and directory-iteration semantics without exposing native handles or
error values. File stream adapters in `std.io` SHALL initially be unbuffered
forwarders over `std.fs`; buffering and formatted conversion remain separate
layers and MAY be added without changing the PAL ABI.

Native C+ path semantics SHALL be independent from libc `char*` filename semantics.

The portable C+ filesystem path representation SHALL be a UTF-8 string using `/`
as its separator. Native `std.fs` and the C+ libc façade SHALL accept this
representation on every target; application code SHALL NOT need to select
Windows drive separators, UNC spelling, POSIX separators, or a host encoding.

The PAL SHALL translate the canonical path at the OS boundary. On Windows this
includes separator/root interpretation and conversion to the native wide
character API representation where required. On Unix-like targets the PAL MAY
pass the UTF-8 byte representation directly to the supported OS interface.
This translation SHALL remain below `std.fs` and SHALL NOT leak host path
syntax into portable C+ code.

Platform encoding conversion belongs in the PAL.

---

# 13. `std.process`

This module SHALL expose:

```text
process identity
process launch
process wait
exit
environment
command-line arguments
standard streams
```

OS-specific process creation is implemented by the PAL.

The target-independent native façade SHALL expose the same operations without
publishing an OS process type:

```c
long long std_process_id(void);
unsigned long long std_process_argument_count(void);
const char* std_process_argument(unsigned long long index);
const char* const* std_process_environment(void);
long long std_process_spawn(const char* executable, const char* const* arguments);
int std_process_wait(long long process, int* exit_status);
int std_process_exit(int status);
long long std_process_stdin_read(void* buffer, unsigned long long capacity);
long long std_process_stdout_write(const char* buffer, unsigned long long length);
long long std_process_stderr_write(const char* buffer, unsigned long long length);
```

These entry points SHALL forward to the corresponding PAL operations without
retaining argument, environment, or I/O buffers, and SHALL preserve PAL return
values and borrowed-view lifetimes. The process ID SHALL be positive on
success. Standard-channel operations SHALL use the same byte counts and stable
negative PAL errors as the underlying PAL; a zero-length operation SHALL
preserve the PAL's zero-length behavior.

The process PAL SHALL expose process identity, spawn, wait, and termination
through the uniform C ABI:

```c
typedef long long cplus_process_handle_t;

long long platform_process_id(void);
unsigned long long platform_process_argument_count(void);
const char* platform_process_argument(unsigned long long index);
const char* const* platform_process_environment(void);
cplus_process_handle_t platform_process_spawn(
    const char* executable,
    const char* const* arguments);
int platform_process_wait(cplus_process_handle_t process, int* exit_status);
int platform_process_exit(int status);
```

`arguments`, when non-null, SHALL be a null-terminated UTF-8 vector whose
first element is the child `argv[0]`; when null, the PAL SHALL use
`executable` as `argv[0]`. The child SHALL inherit the current process
environment and standard input, output, and error streams. The PAL SHALL NOT
invoke a shell or parse a command string. Argument storage is borrowed only
for the duration of spawn and SHALL NOT be retained by the PAL.

The current-process argument count SHALL exclude the terminating null entry;
an out-of-range argument lookup SHALL return null. The environment accessor
SHALL return a null-terminated vector of `NAME=value` byte strings whose
storage remains valid for the process lifetime. Unix adapters SHALL preserve
the OS-provided bytes without lossy conversion; Windows adapters SHALL encode
the native UTF-16 command-line and environment data as UTF-8. C+ text
operations that require valid UTF-8 SHALL validate these byte strings before
decoding them. Windows startup SHALL apply the platform command-line quoting
rules. If Windows command-line or environment text cannot be converted to
valid UTF-8, or required startup storage cannot be allocated, startup SHALL
exit with status 127 before calling `main`.

Spawn SHALL return an opaque non-negative process handle on success or a
stable negative PAL error on failure. It SHALL report executable lookup or
permission failures synchronously, including failures that occur while
starting the child. Wait SHALL write the child's exit status and return zero
on success; a successful wait consumes/reaps the process handle. Normal Linux
exit codes are reported in the range 0–255; signal termination is reported as
128 plus the signal number. Windows exit status is preserved in the C `int`
`exit_status` out-parameter. A failed wait SHALL return a stable negative PAL error and SHALL
NOT expose an OS process type or native error number. Process identity SHALL
be a positive target process identifier represented in the fixed-width return
type.

---

# 14. `std.time`

The native time API SHALL distinguish:

```text
wall-clock time
monotonic time
duration
calendar representation
```

Platform clocks SHALL be normalized above the PAL.

The libc compatibility layer SHALL implement C time APIs using this service.

PAL version 4 SHALL expose three independent clocks in signed 64-bit
nanoseconds:

```c
long long platform_clock_wall_nanoseconds(void);
long long platform_clock_monotonic_nanoseconds(void);
long long platform_clock_process_cpu_nanoseconds(void);
```

Successful values SHALL be non-negative. Wall time is UTC nanoseconds since
1970-01-01; monotonic time is elapsed nanoseconds from an unspecified steady
origin and SHALL NOT move backwards due to wall-clock adjustment; process CPU
time is the current process's accumulated user plus kernel CPU nanoseconds and
excludes child processes. Negative values SHALL be stable PAL errors.

Adapters SHALL reject a wall time before the Unix epoch as unsupported because
negative results are reserved for errors. Conversion overflow or malformed
native clock data SHALL return `CPLUS_PAL_IO_ERROR`; adapters SHALL NOT wrap or
saturate. The C `time()` façade SHALL return whole wall-clock seconds, while
`clock()` SHALL return process CPU nanoseconds with `CLOCKS_PER_SEC` equal to
1,000,000,000. Both SHALL return `(time_t)-1` or `(clock_t)-1` on PAL failure.

The native façade SHALL expose the three PAL clocks without changing their
nanosecond units or stable negative errors:

```c
typedef struct std_duration_t {
    long long nanoseconds;
} std_duration_t;

long long std_time_wall_nanoseconds(void);
long long std_time_monotonic_nanoseconds(void);
long long std_time_process_cpu_nanoseconds(void);
int std_time_duration_from_nanoseconds(long long value, std_duration_t* result);
int std_time_duration_from_milliseconds(long long value, std_duration_t* result);
int std_time_duration_from_seconds(long long value, std_duration_t* result);
int std_time_duration_add(
    const std_duration_t* left, const std_duration_t* right, std_duration_t* result);
int std_time_duration_subtract(
    const std_duration_t* left, const std_duration_t* right, std_duration_t* result);
int std_time_duration_compare(
    const std_duration_t* left, const std_duration_t* right, int* ordering);
int std_time_duration_get_nanoseconds(const std_duration_t* duration, long long* nanoseconds);

typedef struct std_calendar_time_t {
    long long year;
    unsigned int month;
    unsigned int day;
    unsigned int hour;
    unsigned int minute;
    unsigned int second;
    unsigned int nanosecond;
} std_calendar_time_t;

int std_time_calendar_from_unix_timestamp(
    long long unix_seconds, unsigned int nanosecond, std_calendar_time_t* result);
int std_time_calendar_to_unix_timestamp(
    const std_calendar_time_t* calendar, long long* unix_seconds, unsigned int* nanosecond);
```

`std_duration_t` SHALL be an eight-byte signed nanosecond quantity. Duration
values MAY be negative. Conversion, addition, and subtraction SHALL check
signed 64-bit overflow and SHALL leave the output unchanged on failure. These
operations SHALL return zero on success, one for a null required pointer, and
two for arithmetic overflow. The status accessors
`std_time_status_invalid_argument()` and `std_time_status_overflow()` SHALL
return one and two respectively. Comparison SHALL set `ordering` to -1, 0, or 1
and return the same pointer-validation status. Nanosecond construction cannot
overflow its representation but SHALL still validate the output pointer.

The calendar type SHALL have a 32-byte size and 8-byte alignment. It SHALL use
the proleptic Gregorian calendar with astronomical year numbering (including
year zero), UTC, months 1–12, days 1–31 as valid for the selected month, hours
0–23, minutes and seconds 0–59, and nanoseconds 0–999,999,999. Leap seconds
are not represented. Conversion from a timestamp SHALL accept every signed
64-bit Unix second value and preserve the supplied fractional nanoseconds.
Conversion to a timestamp SHALL accept only calendar values whose resulting
Unix seconds fit in signed 64 bits. Both conversions SHALL return zero on
success, one for null pointers or invalid fields, and two for timestamp range
overflow; on failure, output objects and scalars SHALL remain unchanged.

---

# 15. `std.thread` and `std.sync`

The thread runtime SHALL provide:

```text
thread creation
join
yield
thread-local storage
mutex
condition variable
semaphore
once initialization
atomic wait/wake where available
```

The version-4 PAL thread ABI SHALL expose:

```c
typedef long long cplus_thread_handle_t;
typedef void* (*cplus_thread_entry_t)(void* context);

cplus_thread_handle_t platform_thread_create(
    cplus_thread_entry_t entry, void* context);
int platform_thread_join(cplus_thread_handle_t thread, void** result);
long long platform_thread_current_id(void);
int platform_thread_yield(void);
```

The public `std.thread` C+ module SHALL expose the same lifecycle through
target-independent declarations:

```c
long long std_thread_create(void* (*entry)(void* context), void* context);
int std_thread_join(long long thread, void** result);
long long std_thread_current_id(void);
int std_thread_yield(void);
```

`std_thread_create` SHALL preserve the PAL callback, context, opaque positive
64-bit handle, and stable negative error contract. `std_thread_join` SHALL
forward the optional result pointer and PAL status unchanged. Current-thread
identity and yield SHALL preserve the PAL's positive identity and zero-success
semantics. The façade SHALL not expose native OS handle types or add a host
thread-library dependency.

The public `std.sync` module SHALL provide typed objects and operations over
the version-4 state-word PAL:

```c
struct std_mutex_t { volatile int state; };
struct std_condition_t { volatile int sequence; };
struct std_semaphore_t { volatile int count; };
struct std_once_t { volatile int state; };

int std_mutex_init(std_mutex_t* mutex);
int std_mutex_lock(std_mutex_t* mutex);
int std_mutex_unlock(std_mutex_t* mutex);
int std_condition_init(std_condition_t* condition);
int std_condition_wait(std_condition_t* condition, std_mutex_t* mutex);
int std_condition_signal(std_condition_t* condition);
int std_condition_broadcast(std_condition_t* condition);
int std_semaphore_init(std_semaphore_t* semaphore, int initial_count);
int std_semaphore_wait(std_semaphore_t* semaphore);
int std_semaphore_post(std_semaphore_t* semaphore);
int std_once_init(std_once_t* once);
int std_once_enter(std_once_t* once);
int std_once_complete(std_once_t* once);
```

Each object SHALL contain exactly one four-byte, four-byte-aligned state word
at offset zero and SHALL be initialized before concurrent use. The typed
façade SHALL forward to the matching PAL operation and preserve its status.
Condition wait SHALL require the mutex to be held and return with it reacquired;
semaphore counts SHALL remain within `[0, INT_MAX]`. `std_once_enter` SHALL
return zero to the initializer, one if initialization is complete, or a
negative PAL error. `std_once_complete` SHALL publish completion and wake
waiters. The once operation is non-recursive; cancellation recovery is not
provided if an initializer exits without completion.

The public `std.atomic` module SHALL expose integer atomics and wait/wake over
the target's native aligned 32-bit integer operations:

```c
typedef enum {
    STD_MEMORY_ORDER_RELAXED = 0,
    STD_MEMORY_ORDER_CONSUME = 1,
    STD_MEMORY_ORDER_ACQUIRE = 2,
    STD_MEMORY_ORDER_RELEASE = 3,
    STD_MEMORY_ORDER_ACQ_REL = 4,
    STD_MEMORY_ORDER_SEQ_CST = 5
} std_memory_order_t;

struct std_atomic_int_t { volatile int value; };

int std_atomic_init_int(std_atomic_int_t* object, int value);
int std_atomic_load_int(std_atomic_int_t* object, std_memory_order_t order, int* result);
int std_atomic_store_int(std_atomic_int_t* object, int value, std_memory_order_t order);
int std_atomic_exchange_int(std_atomic_int_t* object, int value, std_memory_order_t order, int* previous);
int std_atomic_compare_exchange_int(
    std_atomic_int_t* object, int* expected, int desired,
    std_memory_order_t order, int* exchanged);
int std_atomic_fetch_add_int(std_atomic_int_t* object, int value, std_memory_order_t order, int* previous);
int std_atomic_fetch_sub_int(std_atomic_int_t* object, int value, std_memory_order_t order, int* previous);
int std_atomic_fetch_and_int(std_atomic_int_t* object, int value, std_memory_order_t order, int* previous);
int std_atomic_fetch_or_int(std_atomic_int_t* object, int value, std_memory_order_t order, int* previous);
int std_atomic_fetch_xor_int(std_atomic_int_t* object, int value, std_memory_order_t order, int* previous);
int std_atomic_thread_fence(std_memory_order_t order);
int std_atomic_wait_int(std_atomic_int_t* object, int expected, std_memory_order_t order);
int std_atomic_wake_int(std_atomic_int_t* object, unsigned int count);
```

`std_atomic_int_t` SHALL contain one four-byte state word at offset zero and
SHALL be initialized before concurrent use. Operations SHALL return zero on
success and the stable invalid-argument PAL status for null/misaligned objects,
missing outputs, or invalid memory orders; output arguments SHALL remain
unchanged on validation failure. Load orders are relaxed, consume, acquire, or
sequentially consistent. Store orders are relaxed, release, or sequentially
consistent. Read-modify-write operations accept all six orders. Compare-exchange
is strong; its failure order is derived as the requested order, except release
uses relaxed and acquire-release uses acquire. A failure SHALL update
`expected` with the observed value and set `exchanged` to zero; success sets it
to one.

The façade SHALL dispatch each explicit memory order to the corresponding
compiler atomic primitive with that order as a compile-time constant; it SHALL
NOT silently strengthen a runtime-selected order. Integer load/store/exchange,
compare-exchange, arithmetic/bitwise fetch operations, and fences SHALL not
introduce an OS service or a `libatomic` dependency for the supported 32-bit
type. Atomic wait SHALL repeat an ordered load and block only while the value
equals `expected`; wake SHALL use the PAL wait/wake service, where zero wakes
no waiters and `UINT_MAX` requests wake-all. Wait/wake SHALL preserve stable PAL
errors.

Creation SHALL start `entry(context)` on a runtime-managed thread and return an
opaque positive handle, or a stable negative PAL error. A successful join
SHALL wait for termination, optionally write the entry's return value to
`result`, release the handle's resources, and return zero. A handle SHALL be
joined at most once. Current-thread identity SHALL be positive; yielding SHALL
return zero when the request is accepted. Invalid arguments and native
failures SHALL use the stable PAL error values.

Every runtime-managed thread SHALL enter with its target TLS image initialized
and runtime TLS attachment completed before invoking user code. This includes
zero-initialized and explicitly initialized thread-local objects and the
thread-local C `errno`. Linux adapters SHALL provide independent static TLS
for each thread while using kernel clone/futex services; Windows adapters
SHALL use OS-managed TLS with native thread creation/wait services. Neither
adapter SHALL depend on pthreads or a host C runtime.

The version-4 PAL SHALL also expose 32-bit, four-byte-aligned state-word
operations for mutexes, condition variables, semaphores, once initialization,
and atomic wait/wake:

```c
int platform_mutex_init(volatile int* state);
int platform_mutex_lock(volatile int* state);
int platform_mutex_unlock(volatile int* state);
int platform_condition_init(volatile int* sequence);
int platform_condition_wait(volatile int* sequence, volatile int* mutex_state);
int platform_condition_signal(volatile int* sequence);
int platform_condition_broadcast(volatile int* sequence);
int platform_semaphore_init(volatile int* count, int initial_count);
int platform_semaphore_wait(volatile int* count);
int platform_semaphore_post(volatile int* count);
int platform_once_init(volatile int* state);
int platform_once_enter(volatile int* state);
int platform_once_complete(volatile int* state);
int platform_atomic_wait32(volatile int* address, int expected);
int platform_atomic_wake32(volatile int* address, unsigned int count);
```

Mutex, condition, semaphore, and once state words SHALL be initialized before
concurrent use. Mutex state zero means unlocked. A condition wait SHALL be
called with its mutex held and SHALL return with that mutex reacquired;
signal/broadcast SHALL advance the sequence before waking waiters. On a native
wait error, condition wait SHALL attempt to reacquire the mutex before
returning the error; if reacquisition itself fails, that error is returned and
mutex ownership is not guaranteed. Semaphore counts SHALL remain in
`[0, INT_MAX]`. Once state zero means uninitialized,
one means initialization in progress, and two means complete; `once_enter`
returns zero to the initializer, one when already complete, or a negative PAL
error. `once_complete` SHALL publish the completed state and wake waiters.

Atomic wait SHALL block only while the aligned 32-bit value equals `expected`;
it MAY return spuriously after a wake. Wake count zero wakes none and
`UINT_MAX` requests wake-all. These operations provide waiting only; ordinary
atomic memory operations and memory ordering remain compiler/runtime
intrinsics. A successful atomic wake SHALL return zero on every target; the
number of awakened waiters is intentionally not observable. Linux SHALL use
private futex operations. Windows SHALL use
`WaitOnAddress`/`WakeByAddress*` and therefore requires Windows 8 or newer for
the native synchronization profile. Invalid pointers/alignment, overflow, and
native failures SHALL map to stable PAL errors.

Once initialization is not recursive: an initializer SHALL NOT call
`once_enter` again for the same state before completing it. The API does not
provide cancellation or recovery when an initializer terminates without
calling `once_complete`.

Native synchronization SHALL use the cheapest supported platform primitive.

Examples include conceptually:

```text
Linux futex
Windows WaitOnAddress / synchronization APIs
Darwin supported synchronization primitives
```

The implementation SHALL NOT require pthreads on non-POSIX targets.

---

# 16. `std.net`

Networking is part of the native C+ standard library but not the ISO libc compatibility contract.

The version-four PAL socket surface SHALL use this target-independent address
record and constants:

```c
typedef long long cplus_socket_handle_t;

typedef struct cplus_socket_address_t {
    unsigned int family;       /* 4 for IPv4, 6 for IPv6 */
    unsigned short port;       /* host byte order */
    unsigned short reserved;   /* must be zero */
    unsigned char address[16]; /* network-order address bytes */
    unsigned int scope_id;     /* IPv6 scope; zero for IPv4 */
} cplus_socket_address_t;

#define CPLUS_SOCKET_IPV4 4U
#define CPLUS_SOCKET_IPV6 6U
#define CPLUS_SOCKET_STREAM 1U
#define CPLUS_SOCKET_DATAGRAM 2U
#define CPLUS_SOCKET_SHUTDOWN_RECEIVE 0U
#define CPLUS_SOCKET_SHUTDOWN_SEND 1U
#define CPLUS_SOCKET_SHUTDOWN_BOTH 2U
#define CPLUS_PAL_NETWORK_ERROR (-8L)

cplus_socket_handle_t platform_socket_open(unsigned int family, unsigned int kind);
int platform_socket_bind(cplus_socket_handle_t socket, const cplus_socket_address_t* address);
int platform_socket_listen(cplus_socket_handle_t socket, int backlog);
cplus_socket_handle_t platform_socket_accept(cplus_socket_handle_t socket, cplus_socket_address_t* peer);
int platform_socket_connect(cplus_socket_handle_t socket, const cplus_socket_address_t* address);
int platform_socket_get_address(cplus_socket_handle_t socket, int peer, cplus_socket_address_t* address);
long long platform_socket_send(cplus_socket_handle_t socket, const void* buffer, unsigned long long length);
long long platform_socket_receive(cplus_socket_handle_t socket, void* buffer, unsigned long long capacity);
long long platform_socket_send_to(cplus_socket_handle_t socket, const void* buffer, unsigned long long length, const cplus_socket_address_t* destination);
long long platform_socket_receive_from(cplus_socket_handle_t socket, void* buffer, unsigned long long capacity, cplus_socket_address_t* source);
int platform_socket_shutdown(cplus_socket_handle_t socket, unsigned int direction);
int platform_socket_close(cplus_socket_handle_t socket);
```

Family is one of IPv4 or IPv6 and kind is stream or datagram; the adapter SHALL
select the native protocol. The record has a 28-byte, four-byte-aligned ABI on
all supported targets. IPv4 uses the first four address bytes and SHALL zero
the remaining twelve bytes and `scope_id`; IPv6 uses all sixteen address
bytes. The port is an unsigned host-order number. `reserved` SHALL be zero.

Socket operations SHALL be blocking. `accept` MAY omit the peer address;
`get_address` uses `peer == 0` for the local address and `peer == 1` for the
remote address. `send`/`receive` operate on connected streams, while
`send_to`/`receive_from` operate on datagrams; a transfer MAY be partial and
SHALL accept at most `INT_MAX` bytes per call. A zero-length send returns zero.
A zero-capacity stream receive returns zero without waiting; with nonzero
capacity, a zero-byte stream receive indicates orderly peer shutdown. A
zero-byte datagram receive may represent an empty datagram, and its source
address remains valid. A buffer SHALL
be non-null when its transfer length is nonzero; the `receive_from` source
address MAY be null. Linux stream sends SHALL suppress SIGPIPE-style process
termination and report the failure through the PAL result. Shutdown direction
values select receive, send, or both. Successful lifecycle/control operations
return zero; open/accept return a non-negative opaque handle, and transfer
operations return the byte count.

Invalid arguments, invalid handles, and malformed binary addresses SHALL
return `CPLUS_PAL_INVALID_ARGUMENT`; permission failures SHALL return
`CPLUS_PAL_ACCESS_DENIED`. Unsupported families/kinds SHALL return
`CPLUS_PAL_UNSUPPORTED`. Other native network failures SHALL return
`CPLUS_PAL_NETWORK_ERROR`; raw errno, WSA errors, and native socket values
SHALL NOT cross the PAL boundary. Linux adapters SHALL use
target-catalogued syscalls and close-on-exec sockets. Windows adapters SHALL
use documented Winsock APIs without requiring a host C runtime or an
unconditional `ws2_32` link dependency; Winsock MAY be loaded and initialized
on first socket use. Address text conversion and DNS resolution are specified
as a separate capability layer and are not prerequisites for this binary
address/socket ABI.

`std.net` SHALL build portable TCP and UDP APIs over this PAL. Platform socket
APIs SHALL remain below the PAL; programs that do not use networking SHALL
NOT acquire a Winsock dependency merely because the SDK provides it.

### 16.1 Address text and DNS resolution

The PAL SHALL provide locale-independent address conversion and hostname
resolution through these additive version-four declarations:

```c
int platform_network_parse_address(
    unsigned int family,
    const char* text,
    cplus_socket_address_t* address);
long long platform_network_format_address(
    const cplus_socket_address_t* address,
    char* output,
    unsigned long long capacity);
int platform_network_resolve(
    const char* hostname,
    unsigned int family,
    unsigned short port,
    cplus_socket_address_t* addresses,
    unsigned long long capacity,
    unsigned long long* count);
```

Address parsing SHALL accept an explicit IPv4 or IPv6 family and a bare
address literal (without URI brackets or a port). IPv4 text SHALL contain
exactly four decimal octets in the range 0 through 255, with no leading zero
unless the octet is exactly `0`. IPv6 text SHALL follow
the accepted literal forms in RFC 4291, including `::` compression and an
optional dotted-decimal IPv4 tail. An IPv6 scope suffix MAY be supplied as
`%` followed by an unsigned decimal scope identifier; named interfaces and URI
zone escaping are outside this PAL operation. Parsing SHALL set the family,
zero the port and reserved fields, and preserve the scope identifier. Invalid
text SHALL return `CPLUS_PAL_INVALID_ARGUMENT`.

Address formatting SHALL accept the binary address record and return the
number of output bytes excluding the terminating NUL. The caller SHALL provide
capacity for the NUL byte. A short output buffer SHALL return
`CPLUS_PAL_BUFFER_TOO_SMALL` without modifying the output. IPv4 SHALL use
dotted-decimal text. IPv6 SHALL use the canonical lowercase form, longest-zero
run compression, and leftmost tie-breaking specified by RFC 5952; a nonzero
scope identifier SHALL be appended as `%` plus decimal digits. IPv4-mapped
IPv6 addresses SHALL use the RFC 5952 mixed form, with the low-order 32 bits
rendered as dotted-decimal IPv4; other IPv6 addresses SHALL use hexadecimal
groups. Formatting SHALL not include a port or brackets and SHALL not allocate
memory.

Hostname input and all PAL text buffers SHALL be UTF-8, independent of the
Windows active code page. A hostname SHALL be a fully qualified DNS name; a
final root dot MAY be omitted and SHALL not cause search-suffix expansion.
ASCII labels SHALL use DNS hostname syntax. A non-ASCII label SHALL be a valid,
NFC-normalized IDNA2008 U-label supplied by the caller; the PAL SHALL encode it
to its DNS A-label form and SHALL reject malformed UTF-8 and labels that
exceed DNS wire limits. The PAL is not required to normalize Unicode or
provide Unicode-table-based IDNA validity checking.

Resolution family SHALL be IPv4, IPv6, or zero for both. Successful results
SHALL be copied into caller-owned `cplus_socket_address_t` elements, with the
requested host-order port and zero reserved fields; no native resolver list or
allocator ownership SHALL escape the PAL. Duplicate address records SHALL be
removed. Result order is unspecified. `count` SHALL be required and report the
number of unique results required or produced. If `capacity` is too small, the
PAL MAY write the first `capacity` results and SHALL return
`CPLUS_PAL_BUFFER_TOO_SMALL`; no result memory is retained by the PAL. A null
result array is valid only when capacity is zero. A numeric address SHALL be
resolved without a DNS transaction when it matches the requested family.

The Linux resolver SHALL read up to three numeric `nameserver` entries from
`/etc/resolv.conf`, derive DNS transaction identifiers from kernel entropy,
use an ephemeral source port, query A and/or AAAA records, verify the response
question and source, follow at most eight CNAME indirections per queried
family, and use TCP when a UDP reply is truncated (RFC 1035 and RFC 7766). Each
DNS query, including a UDP-to-TCP retry, SHALL use a two-second absolute
deadline. A response producing more than 256 unique addresses or exceeding the
CNAME bound SHALL fail with `CPLUS_PAL_NETWORK_ERROR`; unavailable kernel
entropy SHALL fail rather than fall back to predictable transaction IDs.
Native resolver, socket, and DNS response codes SHALL be translated to stable
PAL results. The Windows resolver SHALL convert the common A-label name to an
absolute UTF-16 spelling (including its terminal root dot) and call the
Unicode Winsock resolver API via the dynamically loaded `ws2_32.dll` module.
It SHALL copy and release its native result list before returning. Neither
resolver SHALL require a host C runtime or expose native error codes. A name
with no address result SHALL return `CPLUS_PAL_NOT_FOUND`; unsupported families
SHALL return `CPLUS_PAL_UNSUPPORTED`; other resolver or transport failures SHALL return
`CPLUS_PAL_NETWORK_ERROR`. DNSSEC validation and search-list expansion are
outside this API contract.

### 16.2 Public C+ façade

The SDK SHALL expose the network services through `std.net`, without requiring
applications to import platform APIs. Its public declarations SHALL include
the following fixed-width types and stable values:

```c
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

pub struct std_net_address_t {
    uint32_t family;
    uint16_t port;
    uint16_t reserved;
    uint8_t address[16];
    uint32_t scope_id;
};

pub typedef int64_t std_net_socket_t;
```

`std_net_address_t` SHALL match the PAL address record: 28 bytes, four-byte
alignment, and member offsets 0, 4, 6, 8, and 24 on every supported target.
`std_net_socket_t` SHALL be a 64-bit opaque handle. `STD_NET_FAMILY_ANY` is
valid only for resolution; socket creation SHALL require IPv4 or IPv6.

The public blocking operations SHALL be `std_net_open`, `std_net_bind`,
`std_net_listen`, `std_net_accept`, `std_net_connect`,
`std_net_get_address`, `std_net_send`, `std_net_receive`, `std_net_send_to`,
`std_net_receive_from`, `std_net_shutdown`, and `std_net_close`, with arguments
and results matching their corresponding `platform_socket_*` operations and
using `std_net_address_t` and `std_net_socket_t` in place of PAL types. The
address/name operations SHALL be `std_net_parse_address`,
`std_net_format_address`, and `std_net_resolve`, matching the
`platform_network_*` contracts in §16.1 and copying all results into
caller-owned storage.

Operations returning status SHALL use the stable `std_net_status_t` values,
never native OS error numbers. `std_net_open` and `std_net_accept` SHALL return
a non-negative socket handle on success and a stable negative status on
failure; transfer and formatting operations SHALL return their documented
non-negative byte count on success or a stable negative status. The façade
SHALL preserve the PAL's argument validation, buffer-preservation, blocking,
partial-transfer, and optional-pointer rules. It SHALL not introduce socket
dependencies into programs that do not reference `std.net`.

---

# 17. libc conformance profiles

The C compatibility layer SHALL use explicitly versioned profiles.

## 17.1 `libc-c17`

The mandatory first complete compatibility profile SHALL target ISO C17 hosted-library semantics.

## 17.2 `libc-c23`

C23 additions SHALL be delivered as an additive compatibility profile.

A target MAY advertise:

```text
libc.c17 = complete
libc.c23 = partial|complete
```

The compiler SHALL make profile availability queryable at compile time.

---

# 18. Standard C headers delivered by the SDK

The C+ SDK SHALL supply its own standard headers for self-hosted compilation.

The C17 compatibility profile SHALL cover the standard header families:

```text
assert.h
complex.h
ctype.h
errno.h
fenv.h
float.h
inttypes.h
iso646.h
limits.h
locale.h
math.h
setjmp.h
signal.h
stdalign.h
stdarg.h
stdatomic.h
stdbool.h
stddef.h
stdint.h
stdio.h
stdlib.h
stdnoreturn.h
string.h
tgmath.h
threads.h
time.h
uchar.h
wchar.h
wctype.h
```

Some of these headers are primarily compiler interfaces rather than runtime-library implementations.

They SHALL therefore be generated or supplied jointly by the compiler and SDK.

---

# 19. Compiler-owned C facilities

The following facilities SHALL be considered compiler/runtime contracts rather than ordinary library algorithms:

```text
stdarg / varargs
atomic operations
noreturn semantics
alignment
type/layout operators
setjmp context primitives
floating-point environment primitives where architecture-sensitive
complex ABI representation where compiler-dependent
```

The SDK headers exposing these features SHALL map to C+ compiler intrinsics where appropriate.

---

# 20. ABI classes

C+ SHALL distinguish five ABI classes.

## 20.1 C ABI

Designation:

```text
abi.c
```

Used for:

```text
foreign C functions
C standard-library exports
C-compatible generated interfaces
```

## 20.2 System ABI

Designation:

```text
abi.system
```

Represents the platform-supported user-mode system calling convention.

Examples:

```text
Windows system DLL calls
Darwin system-library calls
```

On some targets this is equivalent to the ordinary C ABI.

It remains semantically distinct.

## 20.3 C+ ABI

Designation:

```text
abi.cplus
```

Used for C+ symbols not explicitly exported through the C ABI.

C+ methods, generic specializations and package symbols may use deterministic C+ name mangling.

The first C+ ABI implementation SHALL still lower to target-C-compatible functions.

C+ ABI stability SHALL be versioned.

## 20.4 Intrinsic ABI

Designation:

```text
abi.intrinsic
```

Compiler-only operations.

These are not externally linkable ABI symbols.

## 20.5 Runtime ABI

Designation:

```text
abi.runtime
```

Reserved internal ABI between generated C+ code and the C+ runtime.

Runtime symbols SHALL use the reserved namespace:

```text
__cplus_*
```

Applications SHALL NOT define symbols in this namespace.

---

# 21. Foreign declaration syntax

Foreign C typedefs exposed by the SDK SHALL preserve their external typedef
names while carrying their known underlying type into ABI and layout queries.
For example, `int32_t` SHALL have the layout of the selected catalogue's
32-bit signed integer and `size_t` SHALL follow the target C integer model.
Opaque implementation-defined types MAY remain layout-opaque until a target
profile supplies a concrete definition.

C+ source declarations that cross the foreign boundary SHALL support the C
qualifiers `const` and `volatile`; `restrict` MAY be used when accepted by the
selected C dialect. Qualifiers and pointer declarators SHALL remain distinct in
the generated declaration, and qualifier-only differences SHALL not alter ABI
size or alignment.

Function-pointer callback parameters SHALL retain their complete signature
through foreign declarations and generated headers. Callback ABI tests SHALL
cover both a C+ caller passing a C+ function and an independently compiled C
caller invoking a generated C+ callback entry point.

C+ SHALL support ABI-qualified foreign declarations.

Canonical conceptual form:

```c
@abi("c")
extern int puts(borrowed char* text);
```

A renamed symbol:

```c
@abi("c")
@link_name("some_c_symbol")
extern int some_function(int value);
```

A system-library declaration:

```c
@abi("system")
@library("kernel32")
@link_name("VirtualAlloc")
extern void* virtual_alloc(
    void* address,
    usize size,
    u32 allocation_type,
    u32 protection
);
```

The source-level local name and external linker name SHALL remain distinct semantic properties.

---

# 22. Export declarations

C+ SHALL support explicit externally visible symbol names.

Example:

```c
@abi("c")
@export_name("my_function")
pub int function(int value) {
    ...
}
```

`@export_name` SHALL affect linker-visible identity only.

It SHALL NOT alter source-level symbol identity.

---

# 23. Required low-level language constructs

The following constructs become required by the C+ runtime architecture.

## 23.1 `thread_local`

Example:

```c
thread_local int errno;
```

The compiler SHALL lower this to the target TLS mechanism.

## 23.2 `volatile`

C-compatible volatile memory semantics SHALL be available.

`volatile` SHALL NOT substitute for atomic synchronization.

## 23.3 Explicit alignment

```c
@align(64)
struct cache_line_t {
    ...
};
```

## 23.4 Packed layout

```c
@packed
struct wire_header_t {
    ...
};
```

Packed semantics SHALL define field alignment and padding precisely.

## 23.5 `noreturn`

```c
@noreturn
void abort();
```

## 23.6 Section placement

The runtime MAY use:

```c
@section(".name")
```

for target-specific startup/runtime structures.

## 23.7 Retention

```c
@used
```

prevents intentional runtime symbols or metadata from being removed as unreachable.

## 23.8 Weak linkage

Where supported:

```c
@weak
```

MAY describe a weak exported/imported symbol.

Unsupported targets SHALL reject it rather than silently changing semantics.

---

# 24. Compiler intrinsics

Intrinsic declarations SHALL use explicit compiler identity.

Conceptually:

```c
@intrinsic("syscall3")
priv isize syscall3(
    usize number,
    usize a0,
    usize a1,
    usize a2
);
```

Intrinsic bodies SHALL NOT be written in ordinary C+.

The compiler backend is responsible for lowering them.

---

# 25. Mandatory intrinsic classes

The initial C+ compiler SHALL support intrinsic classes sufficient for the self-hosted runtime.

They include:

```text
system-call transition
atomic load/store
atomic exchange
compare-and-exchange
memory fence
CPU relax/pause
trap
unreachable
byte swap
bit scan/count operations
varargs operations
context save/restore
return-address/frame primitives only where runtime requires them
```

Not every target must implement every optional intrinsic.

Capability is queryable through target metadata.

---

# 26. System-call intrinsics

System calls SHALL be represented independently of machine registers.

The compiler-level conceptual interface is:

```text
syscall0(number)
syscall1(number, a0)
syscall2(number, a0, a1)
...
syscall6(number, a0, a1, a2, a3, a4, a5)
```

The result SHALL preserve enough information for the OS adapter to interpret kernel failure conventions.

Machine register mapping belongs exclusively to target lowering.

---

# 27. Target compile-time namespace

The compiler SHALL expose an immutable compile-time namespace named `target`.

Required fields:

```text
target.os
target.arch
target.vendor
target.abi
target.object_format
target.endian
target.pointer_bits
target.word_bits
target.c_integer_model
target.runtime_profile
```

Required capability queries:

```text
target.has_intrinsic(name)
target.has_feature(name)
target.has_libc_profile(name)
target.has_service(name)
target.supports_abi(name)
```

`target.has_service(name)` SHALL query the selected C+ runtime's structured
platform-service capabilities. Canonical service names are `memory`, `file`,
`process`, `time`, `threads`, `sync`, `atomics`, `socket-transport`, and
`dns`. A true result means the selected SDK provides that service adapter for
the target; it does not promise success for an individual OS request or
external resource. Unsupported services SHALL be reported through the stable
compile-time capability diagnostic, and runtime calls that cannot be provided
SHALL return `CPLUS_PAL_UNSUPPORTED`.

Example:

```c
comptime if (target.os == OS.LINUX) {
    ...
}
```

---

# 28. ABI compile-time namespace

The compiler SHALL expose an `abi` compile-time namespace.

It SHALL provide structured target ABI information such as:

```text
abi.c
abi.system
abi.cplus
abi.pointer_size
abi.pointer_alignment
abi.max_natural_alignment
```

Layout-sensitive compile-time operations SHALL include:

```text
sizeof(T)
alignof(T)
offsetof(T, field)
layoutof(T)
```

`layoutof` SHALL return structured metadata rather than textual output.

---

# 29. Additional compile-time directives

The runtime requires several standardized compile-time operations.

## 29.1 `comptime assert`

```c
comptime assert(condition, "message");
```

Compilation fails if the condition is false.

## 29.2 `comptime error`

```c
comptime error("unsupported target");
```

Unconditionally emits a compile-time diagnostic.

## 29.3 Capability selection

Library code SHALL select implementations using ordinary compile-time control flow:

```c
comptime if (target.os == OS.LINUX) {
    ...
} else if (target.os == OS.WINDOWS) {
    ...
} else {
    comptime error("unsupported operating system");
}
```

Service-dependent source SHALL query `target.has_service(name)` before
selecting a platform adapter. The target service set SHALL be derived from
verified SDK adapters, not inferred from the host running the compiler or from
the OS name alone. No C preprocessor conditionals are required.

## 29.4 Type-layout introspection

Full layout queries become available after structural CPX stabilization, following the existing type-universe barrier.

Target primitive ABI metadata is available earlier.

---

# 30. Target ABI Descriptor

Each supported target SHALL have a Target ABI Descriptor containing at least:

```text
target triple
OS
architecture
vendor
object format
endianness
pointer width
fundamental C type widths
fundamental C type alignments
calling-convention identity
stack alignment
symbol-prefix rules
TLS model
linker format
startup entry requirements
system-library dependencies
available compiler intrinsics
```

A TAD is data, not executable compiler logic.

---

# 31. Initial supported ABI targets

The initial SDK SHALL be designed for these target families:

```text
Linux x86_64
Linux AArch64

Windows x86_64
Windows AArch64

Darwin x86_64
Darwin AArch64
```

Additional architectures are extensions.

The architecture SHALL not assume x86 semantics.

---

# 32. Linux ABI profile

Linux hosted C+ SHALL use the Linux kernel ABI directly for the PAL where practical.

No glibc or musl runtime dependency is required.

The runtime architecture is:

```text
std / libc
   ↓
Linux PAL
   ↓
syscall intrinsics
   ↓
Linux kernel
```

Syscall numbers SHALL be target-architecture data.

They SHALL NOT be duplicated throughout ordinary library source.

---

# 33. Linux syscall catalogue

The SDK SHALL contain generated or verified syscall metadata per supported architecture.

Example conceptual layout:

```text
platform/linux/syscalls/
    x86_64.cp
    aarch64.cp
```

The source of truth SHOULD be generated from official kernel UAPI definitions where build tooling permits.

Generated data SHALL be committed or packaged so cross-compilation does not require a local Linux kernel source tree.

The concrete Linux file adapter uses `openat`, `read`, `write`, `close`, and
`renameat` with `AT_FDCWD`. The x86_64 and AArch64 syscall numbers are kept in
the SDK catalogue files, and raw negative kernel results are normalized before
being returned to `std.fs`.

---

# 34. Linux syscall result normalization

Linux syscall-specific error conventions SHALL be normalized inside the Linux PAL.

Higher-level `std` code SHALL NOT inspect architecture-specific raw syscall failure values.

The flow is:

```text
kernel result
    ↓
Linux PAL normalization
    ↓
portable Result / error
    ↓
libc wrapper if applicable
    ↓
errno + C return convention
```

---

# 35. Windows ABI profile

Windows SHALL NOT depend on hard-coded NT syscall numbers as its stable platform contract.

The PAL SHALL primarily call documented Windows user-mode APIs.

The architecture is:

```text
std / libc
   ↓
Windows PAL
   ↓
system DLL imports
   ↓
Windows
```

Typical DLL dependencies MAY include:

```text
kernel32
ntdll where an explicitly supported contract requires it
ws2_32
advapi32
bcrypt
```

Dependencies SHALL be included only when used.

The concrete Windows file adapter uses documented `kernel32` APIs: UTF-8 paths
are converted with `MultiByteToWideChar`, `/` separators are translated to
native `\\` separators, and file operations use `CreateFileW`, `ReadFile`,
`WriteFile`, `CloseHandle`, and `MoveFileExW`. Temporary UTF-16 path storage is
obtained from the process heap. This adapter does not require UCRT or MSVCRT.

---

# 36. Windows import metadata

The SDK SHALL contain compiler-visible foreign declarations for the Windows APIs used by the PAL.

It SHALL not require application source to include `windows.h`.

Example:

```c
@abi("system")
@library("kernel32")
@link_name("VirtualFree")
extern bool virtual_free(
    void* address,
    usize size,
    u32 free_type
);
```

The linker driver SHALL resolve the appropriate DLL/import-library mechanism.

---

# 37. Darwin ABI profile

Darwin SHALL use supported Apple userspace ABI facilities.

The default runtime SHALL NOT depend on undocumented syscall numbering as its portable contract.

The architecture is:

```text
std / libc
   ↓
Darwin PAL
   ↓
supported Darwin/libSystem interfaces
   ↓
kernel/framework services
```

The C+ implementation of libc remains the C+ compatibility implementation even when the lowest-level PAL enters the operating system through a platform system library.

This distinction is deliberate:

```text
C+ libc implementation
≠
zero operating-system library dependencies
```

---

# 38. Object formats

The SDK/compiler SHALL understand these initial object formats:

```text
ELF      Linux
PE/COFF  Windows
Mach-O   Darwin
```

Object-format-specific behavior SHALL not leak into ordinary standard-library code.

---

# 39. Program startup ABI

The C+ runtime SHALL own program startup when using `--runtime=cplus`.

The conceptual lifecycle is:

```text
OS loader
   ↓
target startup entry
   ↓
__cplus_runtime_init
   ↓
initialize TLS
initialize allocator/runtime state
decode arguments/environment
initialize C+/libc global state
   ↓
application main
   ↓
execute exit handlers
flush streams
runtime finalization
   ↓
platform process exit
```

---

# 40. Startup symbols

The stable internal runtime entry SHALL be:

```text
__cplus_start
```

Target adapters MAY additionally require conventional platform entry symbols.

Those names SHALL be generated by the target startup component rather than exposed as application API.

---

# 41. Application entry point

The normal C+-hosted entry point SHALL support:

```c
int main();
```

and:

```c
int main(int argc, char** argv);
```

The runtime MAY provide richer native C+ application-entry APIs in addition to these C-compatible forms.

---

# 42. Runtime initialization

`__cplus_runtime_init` SHALL establish all state required before ordinary C+ execution.

Initialization order SHALL be deterministic.

Package/module initialization, if introduced, SHALL occur after core runtime initialization and before `main`.

---

# 43. Program termination

The runtime SHALL distinguish:

```text
normal exit
quick exit
immediate process exit
abort
```

The libc layer SHALL provide corresponding C semantics for:

```text
exit
quick_exit
_Exit
abort
atexit
at_quick_exit
```

---

# 44. `errno`

The C compatibility runtime SHALL provide thread-local `errno`.

Conceptually:

```c
thread_local int errno;
```

Native C+ `std` APIs SHOULD prefer structured error results rather than implicit `errno`.

Thus:

```text
PAL
 ↓
Result<Value, OsError>
 ↓
std
```

and only the libc façade converts this to:

```text
-1 / null / failure sentinel
+
errno
```

---

# 45. Compiler-generated memory operations

The compiler and downstream C compiler MAY lower ordinary operations into standard memory symbols.

The C+ runtime SHALL therefore guarantee availability of at least:

```text
memcpy
memmove
memset
memcmp
```

in self-hosted builds.

The runtime build audit SHALL detect unresolved implicit compiler-library dependencies.

---

# 46. Compiler support runtime

The SDK SHALL provide a compiler-support module:

```text
runtime.compiler
```

It owns runtime helpers not expressible as normal inline generated code.

Reserved symbols use:

```text
__cplus_*
```

Compiler-generated references SHALL be versioned through the runtime ABI.

---

# 47. Varargs ABI

C+ SHALL support C ABI variadic functions for libc compatibility and C interoperation.

Required compiler primitives correspond conceptually to:

```text
va_start
va_arg
va_copy
va_end
```

Their implementation is target ABI dependent.

They SHALL not be implemented as ordinary portable C+ functions.

---

# 48. `setjmp` / `longjmp`

Register-context operations SHALL be implemented through compiler/runtime intrinsics.

Conceptual primitives:

```text
context_save
context_restore
```

The C compatibility layer SHALL build:

```text
setjmp
longjmp
```

on top of these primitives according to the target ABI.

---

# 49. Atomics

Atomic operations SHALL be language/compiler primitives rather than ordinary libc algorithms.

They SHALL support at least:

```text
relaxed
acquire
release
acq_rel
seq_cst
```

for:

```text
load
store
exchange
compare_exchange
fetch arithmetic/bitwise operations
fence
```

`stdatomic.h` is a compatibility view over these primitives.

---

# 50. Thread-local storage

`thread_local` SHALL be lowered according to the target's TLS ABI.

The compiler/runtime SHALL own TLS setup necessary for runtime-managed threads.

Foreign threads entering C+ MAY require a runtime attachment mechanism if target TLS initialization cannot be assumed.

---

# 51. Floating-point environment

The C17 `fenv` compatibility layer requires architecture-specific support.

The implementation MAY use compiler intrinsics or platform support.

Native C+ math code SHALL not implicitly depend on mutable floating-point-environment state unless required by the selected profile.

Until a target advertises a mutable C17 floating-point environment, the active
profile SHALL use round-to-nearest, ties-to-even for `rint` and `nearbyint`.
The `round` and `lround` families SHALL round halfway values away from zero,
independent of that environment.

---

# 52. Math library

The SDK SHALL contain a self-hosted `std.math` and a C17-compatible
`<math.h>` implementation. The real-math surface SHALL include the complete
C17 `<math.h>` declarations, macros, constants, and behavior for `float`,
`double`, and `long double`, including the corresponding `f` and `l` function
variants. This includes classification and comparison macros; rounding and
integer conversion; decomposition, scaling, and adjacent-value manipulation;
remainder; power, roots, and magnitude; exponential and logarithmic;
trigonometric and hyperbolic; error and gamma; positive-difference, extrema,
and fused-multiply-add operations.

The `<math.h>` header SHALL expose `float_t` and `double_t` according to
`FLT_EVAL_METHOD`; the currently supported targets define
`FLT_EVAL_METHOD` as zero, so these types are `float` and `double`. The SDK
math-error policy is errno-only: `MATH_ERRNO` and `MATH_ERREXCEPT` SHALL have
their C17 values, `math_errhandling` SHALL equal `MATH_ERRNO`, and required
domain, pole, and range errors SHALL update the thread-local `errno` as
specified by C17. This policy does not claim floating-point exception support
or IEC 60559 / Annex F conformance.

The SDK `nan`, `nanf`, and `nanl` functions SHALL return quiet NaNs. For a
non-null tag, the SDK SHALL compute its 64-bit FNV-1a hash over the tag's bytes
and place the low available payload bits into the result: 22 bits for
binary32, 51 for binary64, 62 for x87 extended precision, and 64 for
binary128. This deterministic tag-to-payload mapping is the SDK's
implementation-defined interpretation of the C `NAN(tag)` form; it does not
imply support for signaling NaNs or payload preservation by other operations.

The SDK `fmod` family SHALL use a quotient truncated toward zero, returning a
remainder with the dividend's sign and magnitude less than the divisor's
magnitude. The `remainder` and `remquo` families SHALL use the nearest integer
quotient, resolving exact halfway cases to an even quotient; an exact zero
remainder SHALL retain the dividend's sign. `remquo` SHALL store the signed
low three bits of that rounded quotient. A zero divisor or infinite dividend
with non-NaN operands SHALL set `errno` to `EDOM` and return a quiet NaN;
`remquo` SHALL store zero in its quotient output for that case. A NaN operand
SHALL produce a NaN result without changing `errno`.

The `fabs` family SHALL clear the input sign. `sqrt` SHALL preserve signed
zero, return positive infinity for positive infinity, and report a negative
nonzero input as a domain error (`EDOM`) with a quiet NaN result. `cbrt` SHALL
preserve the signs of zero, infinity, and finite results. For `pow`, a zero
exponent or positive-one base SHALL produce one, including when the other
operand is a NaN; a negative finite base with a non-integral exponent SHALL
report `EDOM` and return a quiet NaN. A zero base with a negative exponent
SHALL report `ERANGE` and return signed infinity when the base is negative
and the exponent is an odd integer, otherwise positive infinity. `hypot`
SHALL return a nonnegative result, with infinity taking precedence over a NaN
operand.

The `exp`, `exp2`, and `expm1` families SHALL preserve their C17 special-value
behavior; finite overflow and underflow-to-zero SHALL set `errno` to `ERANGE`.
The `log`, `log10`, and `log2` families SHALL return negative infinity with
`ERANGE` for zero and a quiet NaN with `EDOM` for negative nonzero inputs.
`log1p` SHALL apply those pole/domain results at `-1` and values below `-1`,
respectively, and SHALL preserve signed zero. `logb` SHALL return the binary
exponent for finite nonzero inputs, positive infinity for either infinity,
and negative infinity with `ERANGE` for zero.

The `sin`, `cos`, and `tan` families SHALL produce a NaN result for NaN inputs
and report infinite arguments as domain errors (`EDOM`) with a quiet NaN result.
The `asin` and `acos` families SHALL report finite or infinite arguments
outside `[-1, 1]` as domain errors (`EDOM`) with a quiet NaN result; `atan`
SHALL map signed infinities to signed π/2. `atan2` SHALL preserve the
ordinate's signed zero when the abscissa is positive and return signed π when
the ordinate is a signed zero and the abscissa is negative. The hyperbolic
families SHALL preserve signed zero and their C17 infinity limits;
`acosh(x)` for `x < 1` and `atanh(x)` for `|x| > 1` SHALL report `EDOM` and
return a quiet NaN, while `atanh(±1)` SHALL report a pole (`ERANGE`) and
return signed infinity. These requirements do not claim IEC 60559 / Annex F
accuracy or floating-point exception behavior.

The `erf` family SHALL be odd, preserve signed zero, and approach signed one
for signed infinities. The `erfc` family SHALL return zero for positive
infinity and two for negative infinity; finite positive-tail underflow SHALL
set `errno` to `ERANGE`. The `lgamma` family SHALL return positive infinity
with `ERANGE` at zero and negative integer poles, and report negative infinity
as a domain error (`EDOM`) with a quiet NaN result. The `tgamma` family SHALL
return signed infinity with `ERANGE` at signed-zero poles, report negative
integer arguments and negative infinity as domain errors (`EDOM`) with a
quiet NaN result, and set `ERANGE` when a finite result overflows or
underflows. NaN inputs SHALL produce NaN results without being converted into
domain errors.

The `fdim` family SHALL return positive zero when `x <= y` and otherwise
return `x - y`; a finite subtraction overflow SHALL set `errno` to `ERANGE`.
For `fmax` and `fmin`, a single NaN operand SHALL yield the numeric operand,
while two NaN operands SHALL yield a NaN result. When both operands are zero,
`fmax` SHALL prefer positive zero and `fmin` SHALL prefer negative zero.
The `fma` family SHALL compute the exact product-plus-addend before a single
rounding to the result type. Finite overflow or inexact underflow SHALL set
`errno` to `ERANGE`; invalid infinity/zero combinations and opposite-signed
infinite product/addend combinations SHALL set `errno` to `EDOM` and return a
NaN result. The active profile's rounding mode is round-to-nearest, ties-to-even.

The SDK SHALL expose the supported real operations through `std.math` with
explicit C+ declarations and types. It SHALL preserve the selected target's
floating formats and ABI, including `long double`, and SHALL NOT silently
substitute a lower-precision type. The C17 profile SHALL NOT claim IEC 60559 /
Annex F conformance unless that additional behavior has its own verified
conformance gate.

`std.math` SHALL provide linkable `std_math_*` runtime entry points for all
171 C17 real-valued `<math.h>` function declarations, preserving each
function's C signature, target ABI, result behavior, and `errno` policy.
Declaration-only entry points that fail at link time are non-conforming. The
runtime façade SHALL dispatch to the SDK's bundled math implementation and
SHALL NOT require the host system's `libm`. The existing
`std_math_abs(double)` compatibility alias SHALL behave like `std_math_fabs`.

Implementations MAY use portable algorithms, architecture intrinsics, or
hardware floating-point instructions. A complete self-hosted profile SHALL
NOT require the host system `libm`; conformance binaries SHALL be checked for
undeclared host math-library and compiler-runtime dependencies.

## 52.1 Complex and type-generic math

Where the target advertises the C17 complex profile, the SDK SHALL provide the
standard `<complex.h>` types, macros, and float/double/long-double complex
function families, including construction, projection, component, argument,
and complex arithmetic/transcendental operations. The complex representation
and calling convention SHALL match the selected target ABI; complex values
SHALL NOT be modeled as ordinary C+ records.

The C17 `<tgmath.h>` header SHALL provide the standard type-generic math
macros for the corresponding `<math.h>` and `<complex.h>` operations. Dispatch
SHALL follow C17 argument-type selection, including integer promotion and
real-versus-complex selection, and SHALL preserve the result type and required
single-evaluation behavior. Neither header may rely on a host `libm` that is
absent from the self-hosted profile. These two headers are part of the C17
profile claim only after their independent C17 fixtures and dependency audits
pass.

---

# 53. Character and locale layer

Native C+ text handling SHALL not depend on process-global C locale.

The libc profile SHALL nevertheless implement C locale semantics.

Initial implementation MAY support:

```text
C locale
UTF-aware C+ native text APIs
```

before additional locale databases are delivered.

Locale conformance status SHALL be explicit.

---

# 54. Wide and Unicode compatibility

The libc compatibility profile SHALL supply:

```text
wchar
wctype
uchar
multibyte conversion
```

according to the selected C profile.

Native C+ text APIs SHOULD use explicit Unicode scalar/code-unit types rather than inheriting platform `wchar_t` differences.

---

# 55. Signals

C signal compatibility is part of the hosted libc profile.

Platform signal/event semantics vary substantially.

The libc implementation SHALL expose the standard C abstraction while the PAL implements target-specific behavior.

Native C+ programs SHOULD prefer structured platform-independent interruption/concurrency APIs where available.

---

# 56. POSIX compatibility

POSIX is not part of the mandatory cross-platform C+ libc profile.

A separate optional package SHALL provide:

```text
cplus.posix
```

on compatible targets.

Potential generated headers include:

```text
unistd.h
dirent.h
sys/socket.h
sys/stat.h
pthread.h
```

This layer SHALL NOT define the native C+ cross-platform API.

On non-POSIX systems it may be unavailable or deliberately partial.

---

# 57. C headers as generated SDK views

C headers shipped by the C+ SDK SHOULD be generated from the same semantic definitions used by the C+ library.

The architecture is:

```text
C+ semantic declarations
        │
        ├── C+ module metadata
        ├── generated C header
        └── runtime implementation
```

This prevents the C and C+ API declarations from drifting apart.

---

# 58. SDK semantic metadata

The SDK build SHALL optionally generate compiled semantic metadata.

For example:

```text
*.cpm
```

Such metadata MAY contain:

```text
symbols
types
package exports
documentation
generic/comptime signatures
source index
ABI metadata
```

It is a cache.

It SHALL remain reproducible from C+ source.

---

# 59. Additional compiler tooling

The compiler/toolchain SHALL gain the following components.

```text
TargetRegistry
AbiDescriptorLoader
IntrinsicRegistry
RuntimeResolver
SdkResolver
CHeaderGenerator
StartupGenerator
LinkDriver
RuntimeDependencyAuditor
AbiVerifier
SyscallCatalogueGenerator
SdkPackager
```

---

# 60. `TargetRegistry`

The target registry maps a target triple to:

```text
Target ABI Descriptor
platform adapter
startup implementation
intrinsic lowering
object format
linker strategy
```

Target resolution occurs before structural compile-time execution so `target.*` metadata is available.

---

# 61. `IntrinsicRegistry`

The intrinsic registry SHALL map semantic intrinsic names to backend implementations.

Conceptually:

```text
"syscall3"
    ├── linux-x86_64 implementation
    └── linux-aarch64 implementation

"atomic.compare_exchange"
    ├── x86_64 implementation
    └── aarch64 implementation
```

Unsupported mappings SHALL cause compile-time diagnostics.

---

# 62. Runtime resolver

The runtime resolver determines which runtime source modules are required by a program.

It SHALL support dead inclusion avoidance.

For example, a program not using networking SHALL not acquire `std.net` platform dependencies merely because they exist in the SDK.

---

# 63. Startup generator

The startup generator selects or produces the target entry-point code.

It SHALL understand:

```text
OS loader contract
object format
entry symbol
argument/environment acquisition
TLS initialization
runtime init
termination
```

Startup code MAY contain a very small amount of compiler-generated assembly where unavoidable.

---

# 64. Link driver

The C+ toolchain SHALL own final link orchestration.

The link driver receives semantic dependencies instead of requiring users to manually determine runtime libraries.

Conceptually:

```text
generated application objects
+
selected C+ runtime objects
+
target startup
+
required system imports
        ↓
LinkDriver
        ↓
executable/library
```

---

# 65. C compiler invocation

While the primary backend emits C, the C+ driver SHALL configure downstream compilers appropriately.

Self-hosted builds MAY require flags equivalent in intent to:

```text
freestanding compilation
disable implicit host libc startup
use C+ SDK headers
use C+ runtime startup
avoid host default libraries
```

The exact flags are compiler-specific and belong to the toolchain adapter.

---

# 66. Runtime dependency auditor

After linking, the toolchain SHOULD inspect the produced executable.

For a self-hosted C+ profile it SHALL be able to verify:

```text
no unexpected libc dependency
no unresolved compiler-runtime symbol
only allowed OS/system dependencies
```

Violations SHALL be reported.

For self-hosted output, dependency inspection SHALL fail with a diagnostic if
the required target-format or unresolved-symbol inspection tools are
unavailable or fail. It SHALL report the observed and allowed system
dependencies, reject undeclared host/compiler-runtime dependencies, and
verify that optional platform-service imports not referenced by the program
are not retained in the executable.

---

# 67. ABI verifier

The ABI verifier SHALL test:

```text
type sizes
type alignments
struct layouts
calling conventions
varargs behavior
TLS behavior
export names
foreign-call round trips
```

Tests SHALL compare generated C+/C boundaries with independently compiled C fixtures.

---

# 68. Syscall catalogue generator

The Linux tooling SHALL provide a generator capable of transforming target kernel ABI definitions into C+ syscall metadata.

The generated result SHALL be deterministic and versioned.

Application builds SHALL use the packaged catalogue and SHALL NOT depend on a host kernel header tree.

---

# 69. C header generator

The SDK tooling SHALL generate standard C headers from semantic SDK descriptions wherever practical.

Manual target-specific fragments MAY exist only where C compiler intrinsic syntax requires them.

Generated headers SHALL include a toolchain/version provenance marker.

---

# 70. SDK packager

The SDK packager SHALL produce a versioned distributable containing:

```text
C+ sources
generated standard C headers
target ABI descriptors
syscall catalogues
startup sources
intrinsic metadata
semantic metadata/cache
optional precompiled runtime objects
license metadata
```

---

# 71. Proposed CLI additions

The C+ toolchain SHOULD expose commands equivalent to:

```text
cplus sdk build
cplus sdk verify
cplus sdk doctor

cplus target list
cplus target show <triple>

cplus runtime inspect <binary>

cplus abi verify <target>

cplus libc test <target>
```

Existing:

```text
cplus build
cplus transcode
cplus check
```

SHALL automatically resolve the appropriate SDK/runtime.

---

# 72. Compilation modes

The toolchain SHALL support:

```text
--runtime=cplus
--runtime=system
--runtime=freestanding
```

and compatibility selection such as:

```text
--libc=c17
--libc=c23
--libc=none
```

The default hosted configuration SHOULD be:

```text
--runtime=cplus
--libc=c17
```

until the C23 profile is complete.

---

# 73. Library compilation model

Because the standard library is source, ordinary modules MAY be specialized during compilation.

This includes:

```text
target-specific CPX selection
generic specialization
removal of unavailable platform branches
inlining
dead-code elimination by downstream compiler
```

A single source SDK can therefore serve multiple targets.

---

# 74. Platform-specific source isolation

Ordinary library source SHALL NOT accumulate widespread target conditionals.

Bad architecture:

```c
void foo() {
    comptime if (target.os == ...) {
       ...
    } else if (...) {
       ...
    }
}
```

throughout every subsystem.

Preferred architecture:

```text
std.fs
   ↓
platform.api.fs
       │
       ├── linux.fs
       ├── windows.fs
       └── darwin.fs
```

Filesystem APIs use one portable path representation at the `std.fs` boundary:
UTF-8 text with `/` separators. The platform adapter owns conversion to the
target OS path and encoding ABI; Windows may translate the canonical path to
its native wide-character file APIs, while Unix-like adapters may use the
UTF-8 bytes directly. Java/Kotlin compiler and tooling code SHALL keep using
`java.nio.file.Path` for host filesystem access and SHALL normalize only
serialized/logical path identities to `/`.

Compile-time target selection occurs primarily at component boundaries.

---

# 75. Native C+ API versus libc ABI

The native API and libc compatibility API SHALL remain separate semantic layers.

Example:

```text
std.fs.open(path)
    → Result<File, Error>
```

versus:

```text
fopen(path, mode)
    → FILE*
    → null + errno on failure
```

The latter MAY delegate to the former.

The native API SHALL not be forced to reproduce historical libc design limitations.

The C compatibility layer SHALL define `errno` as thread-local storage and
shall translate PAL failures to the documented C error constants at the libc
boundary. Native `std` operations SHOULD return structured errors instead of
mutating `errno`; a PAL result SHALL never expose a raw syscall number or
`GetLastError` value to either layer.

The C compatibility layer SHALL define `errno` as thread-local storage and
shall translate PAL failures to the documented C error constants at the libc
boundary. Native `std` operations SHOULD return structured errors instead of
mutating `errno`; a PAL result SHALL never expose a raw syscall number or
`GetLastError` value to either layer.

---

# 76. Binary exports

When building a C+ libc library for consumption by C software, it SHALL export conventional C symbol names.

Examples:

```text
malloc
free
printf
fopen
memcpy
strlen
```

When statically embedded into a pure C+ executable, those symbols MAY still exist because downstream-generated C can reference them.

---

# 77. C+ public symbols

Native C+ public symbols use semantic package identities.

Example:

```text
std.alloc.allocate
```

The emitted name may conceptually become:

```text
std__alloc__allocate
```

The exact C+ mangling format SHALL be versioned by the C+ ABI.

Foreign callers SHOULD use explicit `@abi("c") @export_name(...)` interfaces when a stable C ABI is required.

---

# 78. ABI versioning

The SDK manifest SHALL declare:

```text
language_abi_version
runtime_abi_version
cplus_abi_version
libc_profile_version
sdk_version
```

The compiler SHALL reject incompatible runtime ABI versions.

---

# 79. Runtime ABI stability

The `__cplus_*` runtime ABI is compiler-facing.

It MAY evolve between major ABI versions.

Applications SHALL NOT directly depend on these symbols unless explicitly documented as public.

---

# 80. Source provenance

Standard-library source SHALL participate in ordinary C+ source mapping.

A compiler diagnostic originating from generated runtime/library code SHOULD distinguish:

```text
application origin
CPX expansion origin
standard-library source origin
generated C origin
```

The compiler SHALL not treat SDK code as opaque binaries when source is available.

---

# 81. Debugging

Self-hosted runtime builds SHOULD preserve sufficient debug information to step from application C+ into C+ standard-library source.

Generated C may remain available as an implementation/debugging view.

---

# 82. Testing profiles

The SDK SHALL maintain at least four test classes.

```text
native C+ std tests
libc conformance tests
ABI interoperability tests
target/runtime integration tests
```

---

# 83. libc conformance testing

The libc test suite SHALL test both source behavior and C ABI behavior.

Tests SHALL include C programs compiled independently against C+ SDK headers and linked against the C+ libc implementation.

This prevents passing tests merely because both caller and library share the same compiler bug.

The CLI command `cplus libc test` SHALL execute the SDK's independent C17
fixtures for the selected target and print a machine-readable status line for
each header, runtime source, fixture, and runtime-dependency audit. The command
MUST return success only when every check is `pass`; unsupported target
facilities and planned checks MUST remain visible and MUST produce a non-zero
result.

---

# 84. ABI round-trip testing

For each supported target:

```text
C caller → C+ implementation
C+ caller → C implementation
C+ caller → C+ library through C ABI
```

shall be tested for representative:

```text
integers
floating point
pointers
structures
unions
callbacks
variadic functions
TLS
```

---

# 85. Freestanding conformance

A freestanding test binary SHALL be linkable without:

```text
glibc
musl
UCRT/MSVCRT
system libc startup objects
```

where the target runtime profile claims self-hosted support.

Any unexpected dependency is a test failure.

---

# 86. Target-specific dependency policy

The allowed lowest-level dependencies are:

### Linux self-hosted

```text
kernel ABI
dynamic loader only when producing dynamically linked executables
```

No libc is required.

### Windows self-hosted

```text
documented Windows system DLL ABI
```

No Microsoft C runtime is required.

### Darwin self-hosted

```text
supported Apple userspace/system ABI
```

The PAL MAY depend on `libSystem` or equivalent supported platform facilities.

The C+ standard library nevertheless remains the semantic libc implementation presented to the application.

---

# 87. SDK independence invariant

The absence of a foreign libc SHALL NOT imply absence of all target metadata.

A target may still require:

```text
linker conventions
object-format definitions
system DLL/library metadata
Darwin SDK/link stubs
kernel ABI tables
```

These are considered platform ABI material, not libc.

---

# 88. No-host-contamination invariant

Cross-compilation SHALL not accidentally use the host machine's libc headers or runtime libraries.

When:

```text
host = Linux x86_64
target = Windows AArch64
```

all SDK/runtime resolution SHALL use target metadata.

Host libraries must not enter the generated program unless explicitly requested.

---

# 89. No hidden compiler-runtime invariant

A self-hosted runtime build SHALL identify every symbol potentially emitted implicitly by the downstream C compiler.

If a compiler can introduce calls such as:

```text
memcpy
memset
stack-check helpers
integer arithmetic helpers
```

the C+ runtime or toolchain configuration SHALL explicitly satisfy or prevent those dependencies.

---

# 90. Standard-library implementation rule

The default rule is:

> If an operation can be implemented portably in C+, it SHALL be implemented in C+.

Platform source or compiler intrinsics are justified only when the operation inherently depends on:

```text
OS ABI
processor instruction
calling convention
object format
loader/runtime contract
```

---

# 91. Platform-layer size rule

The PAL SHOULD remain intentionally narrow.

The cross-platform standard library SHALL not simply reproduce every native operating-system API.

Native platform APIs MAY additionally be exposed under explicit low-level packages such as:

```text
os.linux
os.windows
os.darwin
```

Use of those packages intentionally sacrifices source portability.

---

# 92. Low-level OS packages

The SDK MAY expose:

```text
os.linux.syscall
os.windows.api
os.darwin.api
```

These APIs are for:

```text
runtime implementation
systems programming
platform-specific applications
```

They are not the portable `std` API.

---

# 93. C+ SDK as the sysroot

For a pure C+ build, the C+ SDK effectively becomes the compiler sysroot.

It supplies:

```text
standard C headers
runtime source
standard-library source
platform ABI metadata
startup
compiler builtins
link metadata
```

Therefore:

```text
traditional external libc sysroot
```

is unnecessary for targets with a complete C+ self-hosted profile.

---

# 94. External sysroots remain supported

External sysroots remain necessary/useful for:

```text
third-party C libraries
foreign SDKs
vendor APIs
system frameworks
existing native ecosystems
```

The C+ SDK SHALL therefore complement rather than eliminate the existing target-sysroot mechanism.

---

# 95. Canonical pure C+ build

A pure C+ application follows:

```text
application .cp
     │
     ├── std C+ sources
     ├── libc C+ sources where required
     └── runtime C+ sources
     │
     ▼
C+ semantic compilation
     │
     ▼
target-selected C+ AST
     │
     ▼
C backend
     │
     ├── application.c
     ├── runtime.c
     ├── generated standard headers
     └── optional intrinsic assembly/object
     │
     ▼
target C compiler/assembler
     │
     ▼
C+ link driver
     │
     ├── target startup
     └── allowed OS ABI dependencies
     │
     ▼
executable
```

No external libc participates in this pipeline.

---

# 96. Canonical interoperability build

A C consumer may use:

```text
generated libc headers
        +
C+ libc implementation
```

and link normally through the platform C ABI.

A C+ application may simultaneously import existing C headers through the C import subsystem.

Thus the C+ SDK is bidirectional:

```text
C → C+ runtime
C+ → C ecosystem
```

---

# 97. Implementation priorities

The runtime should be implemented in dependency order:

```text
target/ABI metadata
        ↓
intrinsics
        ↓
startup + process exit
        ↓
memory primitives
        ↓
page allocation
        ↓
allocator
        ↓
file I/O
        ↓
stdio / strings
        ↓
time / environment
        ↓
threads / synchronization
        ↓
math / locale / advanced libc
        ↓
networking and extended std
```

This ordering is architectural rather than normative application API behavior.

---

# 98. Core invariant

The defining invariant is:

> The C+ SDK contains both the compile-time interface and the source implementation of the C+ standard runtime. A pure C+ application depends on the C+ SDK and the target operating-system ABI, not on a separately supplied libc.

---

# 99. ABI invariant

The defining ABI rule is:

> C+, C compatibility, compiler runtime, and operating-system ABIs are separate contracts. They SHALL be represented separately even when a particular target happens to use identical machine calling conventions for more than one of them.

---

# 100. Comptime invariant

The defining compile-time rule is:

> Platform selection, ABI capability selection and SDK specialization use the ordinary C+ compile-time system. Platform metadata is compiler-provided structured data; no C preprocessor is required to select runtime implementations.

---

# 101. SDK/runtime invariant

The defining SDK rule is:

> Standard-library source is simultaneously developer-visible SDK material and compilable runtime implementation. Precompiled metadata or objects are caches and deployment optimizations, never the authoritative definition of the library.

---

# 102. Resulting toolchain model

The complete C+ stack therefore becomes:

```text
                    C+ SDK
        ┌─────────────┼─────────────┐
        │             │             │
      std           libc          runtime
        │             │             │
        └─────────────┼─────────────┘
                      │
                     PAL
                      │
          ┌───────────┼───────────┐
          │           │           │
        Linux       Windows     Darwin
          │           │           │
        kernel       system      system
         ABI          ABI         ABI

                      ▲
                      │
                C+ compiler
        ┌─────────────┼─────────────┐
        │             │             │
      parser       comptime      semantic
        │             │             │
        └─────────────┼─────────────┘
                      │
                  C backend
                      │
                 target C
                      │
                linker driver
                      │
                 executable
```

This architecture makes the C+ SDK a complete development environment, runtime source distribution, C compatibility implementation, and target-platform abstraction without making an external libc part of the fundamental C+ execution model.
