# C+ SDK source tree

This directory is the canonical source SDK used by the compiler. The source
and metadata layout is resolved before compilation; generated objects and host
headers are never the source of truth.

`std/src/fixed_width.cp` provides optional user-level `i8` through `i64` and
`u8` through `u64` aliases. Programs must explicitly import the names they use;
the aliases are ordinary typedefs over `c.stdint` exact-width types and are not
compiler primitives or part of `std.core`.
The same module exports `i128` and `u128` only for Linux x86_64; linking checks
that the selected GCC/Clang-compatible C compiler supports the verified
16-byte size/alignment contract. Other target descriptors omit that feature,
and use of these aliases there is diagnosed rather than narrowed.

`std/src/core.cp` exports `usize`/`isize` as aliases of target `size_t` and
`ptrdiff_t`. Memory, string, text, and collection lengths use these target-size
types; raw byte operations use `unsigned char` rather than plain `char`. The
core and memory modules also provide pointer helpers, numeric limits,
alignment-up checks, byte spans, and non-owning raw-memory views.

`std/src/fs.cp` exposes the portable UTF-8 filesystem façade, including
target-sized transfers, metadata, seek, directory iteration, and create/remove
operations. `std/src/io.cp` provides unbuffered file streams layered over that
façade; it stores only an opaque PAL handle and never exposes an OS descriptor
or Windows `HANDLE` type.

`std/src/math.cp` declares the public real-valued `std_math_*` API for all C17
`<math.h>` functions and preserves the selected target's `float`, `double`, and
`long double` signatures. The runtime exports each declaration through the
bundled math implementation, so consumers do not link against a host `libm`.
