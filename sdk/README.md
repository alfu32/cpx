# C+ SDK source tree

This directory is the canonical source SDK used by the compiler. The source
and metadata layout is resolved before compilation; generated objects and host
headers are never the source of truth.

`std/src/fixed_width.cp` provides optional user-level `i8` through `i64` and
`u8` through `u64` aliases. Programs must explicitly import the names they use;
the aliases are ordinary typedefs over `c.stdint` exact-width types and are not
compiler primitives or part of `std.core`.
