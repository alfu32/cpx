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
long platform_write_stdout(const char* buffer, unsigned long length);
int platform_process_exit(int status);
```

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

---

# 7. `std.core`

`std.core` provides foundational definitions requiring no hosted environment.

It SHALL include:

```text
fixed-width integer aliases
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

The process/runtime baseline additionally uses the uniform PAL ABI:

```c
long platform_write_stdout(const char* buffer, unsigned long length);
int platform_process_exit(int status);
```

File streams use the version-2 PAL file operations. `std.io` may layer
buffering and formatting over `platform_file_read`, `platform_file_write`, and
`platform_file_close`; it SHALL not expose target-specific descriptor or
HANDLE types.

The implementation of buffering, formatting, stream state and textual conversion SHALL reside above this PAL interface.

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
cplus_file_result_t std_fs_open(const char* path, cplus_file_mode_t mode);
cplus_file_result_t std_fs_read(
    cplus_file_handle_t handle, void* buffer, cplus_file_size_t size);
cplus_file_result_t std_fs_write(
    cplus_file_handle_t handle, const void* buffer, cplus_file_size_t size);
int std_fs_close(cplus_file_handle_t handle);
int std_fs_rename(const char* source, const char* target);
```

These functions are target-independent forwarding entry points. Their current
implementation intentionally covers only open, read, write, close, and rename;
seek, metadata, directory iteration, remove, and stream buffering remain
separate standard-library stages.

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

It SHALL provide portable:

```text
socket
TCP
UDP
address
DNS
basic network stream
```

Platform-specific socket APIs belong below the PAL.

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
target.supports_abi(name)
```

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

No C preprocessor conditionals are required.

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

---

# 52. Math library

The SDK SHALL contain a self-hosted `std.math` and C-compatible `<math.h>` implementation.

The implementation MAY use:

```text
portable algorithms
architecture intrinsics
hardware floating-point instructions
```

It SHALL NOT require the system `libm` in a complete self-hosted profile.

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
