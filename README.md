# C+

**C+** is a systems programming language built as an extension of C.

It keeps C's data model, native interoperability, predictable runtime behavior, and ability to target ordinary C toolchains, while adding language-level features that would otherwise require preprocessor macros, code generators, compiler extensions, or manually written boilerplate.

C+ source files use the `.cp` extension.

```c
struct point_t {
    int x;
    int y;

    int sum(self) {
        return self.x + self.y;
    }

    point_t zero() {
        return point_t {
            .x = 0,
            .y = 0
        };
    }
};

int main() {
    point_t point = {
        .x = 3,
        .y = 7
    };

    return point.sum();
}
```

C+ transpiles to C and can then be compiled using an ordinary C compiler.

---

# Getting started

[Getting started](GETTING_started.md)

---


# Language features

## C-compatible programming model

C+ retains the familiar C programming model:

- structs;
- unions;
- enums;
- pointers;
- arrays;
- functions;
- function pointers;
- globals and local variables;
- C expressions and operators;
- direct C interoperability;
- predictable native data layout.

C+ features are progressively lowered until only ordinary C remains.

---

# Methods

Functions declared inside a struct are methods.

```c
struct counter_t {
    int value;

    int get(self) {
        return self.value;
    }

    void increment(self*) {
        self->value++;
    }

    counter_t create() {
        return counter_t {
            .value = 0
        };
    }
};
```

A method containing `self` is an **instance method**:

```c
counter_t counter;

counter.increment();
int value = counter.get();
```

A method without `self` is a **static method**:

```c
counter_t counter = counter_t.create();
```

Methods are lowered to normal C functions.

Conceptually:

```c
counter.get()
```

becomes:

```c
counter_t__get(&counter)
```

and:

```c
counter_t.create()
```

becomes:

```c
counter_t__create()
```

---

# Compile-time programming

C+ provides `comptime` functions for compile-time generation.

Compile-time functions operate on semantic C+ values such as:

- types;
- identifiers;
- expressions;
- statements;
- declarations;
- compile-time values;
- CPX source templates.

Compile-time generation replaces many traditional uses of:

- C preprocessor macros;
- templates;
- source generators;
- procedural macros;
- derive systems.

---

# CPX — C+ source expressions

A **CPX** is a readable C+ source template returned by a compile-time function.

Unlike C macros, CPX code remains normal multiline C+ code.

```c
comptime cpx<decl> optional(type T) {
    return {
        struct optional_{T}_t {
            bool valid;
            T value;
        };
    };
}
```

Instantiation:

```c
optional(int);
optional(float);
```

generates declarations equivalent to:

```c
struct optional_int_t {
    bool valid;
    int value;
};

struct optional_float_t {
    bool valid;
    float value;
};
```

Compile-time parameters are semantic values rather than untyped strings.

Here:

```c
type T
```

means that `T` is an actual compile-time type.

Inside CPX:

```c
T value;
```

uses the type directly.

When interpolation occurs inside an identifier, explicit composition is used:

```c
optional_{T}_t
```

This avoids C-style token concatenation such as:

```c
optional_##T##_t
```

---

# Generic programming

Generics are built on compile-time functions rather than on a separate template system.

A generic struct:

```c
comptime cpx<decl> pair(type T, type R) {
    return {
        struct pair_{T}_{R}_t {
            T first;
            R second;
        };
    };
}
```

can be specialized with:

```c
pair(int, float);
```

Compile-time functions may generate:

- structs;
- unions;
- enums;
- functions;
- methods;
- aliases;
- statements;
- expressions.

Equivalent specializations are reused by the compiler.

---

# Recursive compile-time expansion

Generated CPX may itself contain additional compile-time invocations.

The compiler expands compile-time constructs iteratively until the current compile-time phase reaches a fixed point.

This allows generated declarations to trigger further generation without requiring external source-generation passes.

Expansion cycles are detected and diagnosed.

---

# Compile-time reflection

After structural compile-time generation has stabilized, C+ exposes compile-time type introspection.

Compile-time code can inspect information such as:

- type name;
- type kind;
- fields;
- field types;
- methods;
- method signatures;
- size;
- alignment;
- layout information.

Reflection operates on semantic compiler entities rather than textual declarations.

This makes patterns such as serializers, converters, bindings, comparison functions, or generated utility methods expressible directly in C+.

---

# Inner functions and lexical capture

Functions may be declared inside another function and reference variables from the surrounding scope.

```c
int calculate() {
    int base = 10;

    int add(int value) {
        return base + value;
    }

    return add(5);
}
```

The compiler performs capture analysis and lowers captured state to an explicit closure environment in generated C.

---

# `defer`

`defer` registers an action that executes when the current lexical scope exits.

```c
FILE* file = fopen("data.txt", "r");
defer fclose(file);

process(file);
```

Deferred actions execute in reverse registration order.

They are applied to applicable exits including:

- normal fallthrough;
- `return`;
- `break`;
- `continue`.

The compiler lowers `defer` into explicit C control flow.

---

# String templates

C+ supports string interpolation while preserving ordinary source syntax.

Interpolated expressions are lowered before C emission.

String-template implementation may generate formatting calls or compiler-generated helpers where necessary.

---

# Packages and imports

C+ provides language-level packages and imports.

```c
package application;
```

Import an entire module:

```c
import collections;
```

Import with an alias:

```c
import collections as col;
```

Import selected declarations:

```c
import { list_t, map_t } from collections;
```

Imports participate directly in:

- name resolution;
- completion;
- diagnostics;
- navigation;
- dependency analysis.

---

# C interoperability

C declarations can be imported into the C+ semantic model.

Imported C functions and types behave as normal foreign symbols for:

- type checking;
- completion;
- hover;
- go-to-definition;
- generated C dependencies.

C source files can also be supplied to a build and compiled together with generated C+ output.

---

# Native ABI declarations

C+ supports explicit foreign ABI declarations.

Conceptually:

```c
@abi("c")
extern int puts(borrowed char* text);
```

External symbol names may be specified independently:

```c
@abi("c")
@link_name("native_function")
extern int call_native(int value);
```

Platform libraries may also be declared explicitly.

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

---

# C+ SDK and standard runtime

C+ includes a source-based SDK containing:

- the native C+ standard library;
- a C-compatible libc implementation;
- runtime support;
- target ABI descriptions;
- platform adapters;
- startup support;
- compiler intrinsics;
- generated C compatibility headers.

The SDK is both a **development SDK** and the source of the program's runtime implementation.

A pure C+ program therefore does not inherently require an external libc.

---

# Runtime profiles

C+ supports three runtime modes.

## C+ runtime

The normal hosted mode:

```text
--runtime=cplus
```

Uses the C+ standard library, libc implementation, runtime, and platform abstraction layer.

## System runtime

```text
--runtime=system
```

Uses the target system's conventional C runtime where required.

This is useful for interoperability and migration.

## Freestanding

```text
--runtime=freestanding
```

Provides the minimum compiler/runtime environment without assuming a hosted operating system.

This is intended for:

- kernels;
- embedded environments;
- custom operating systems;
- low-level runtime development.

---

# Platform support

The runtime architecture separates portable library code from the operating-system ABI.

```text
C+ application
      ↓
std / libc
      ↓
C+ runtime
      ↓
platform abstraction layer
      ↓
operating system
```

Target-specific implementations may use:

- Linux kernel syscalls;
- Windows system APIs;
- Darwin-supported system interfaces.

Most standard-library code remains platform-independent C+.

## Self-hosted product baseline

The `cplus` runtime owns startup, compiler support, and the PAL. You do not
select glibc, musl, MinGW, MSVCRT, or UCRT for a C+ standard-library build.
The CLI selects a target C driver automatically:

```text
cplus build main.cp --target linux-x86_64
cplus build main.cp --target windows-x86_64
```

Linux products are static ELF executables with direct PAL syscall adapters;
Windows products are PE/COFF executables with only declared Windows system-DLL
imports and no C runtime dependency. The validated Windows x86_64 compiler
profile is GCC/UCRT64; MSVC's binary64 `long double` does not match the current
GNU x87 target ABI. Extensionless Windows output paths receive an `.exe`
suffix, while generated C keeps the source stem. Use `cplus audit product
--target windows-x86_64` to verify a Windows product.

The current target/compiler support boundary and ABI upgrade rules are in
[COMPATIBILITY.md](COMPATIBILITY.md).

---

# Compiler intrinsics

Low-level operations that cannot be expressed portably in C are represented as compiler intrinsics.

These include facilities such as:

- system calls;
- atomics;
- memory fences;
- context save/restore;
- traps;
- CPU pause/relax;
- bit operations;
- varargs support.

Normal application code generally does not need to use these directly.

---

# Target-aware compile-time code

The compiler exposes target information to compile-time code.

Available information includes concepts such as:

```text
target.os
target.arch
target.vendor
target.abi
target.object_format
target.endian
target.pointer_bits
target.word_bits
target.runtime_profile
```

Example:

```c
comptime if (target.os == OS.LINUX) {
    ...
} else if (target.os == OS.WINDOWS) {
    ...
}
```

Target selection therefore does not require C preprocessor conditionals.

---

# Source mapping

Every C+ AST node carries source provenance.

Generated declarations and expressions retain their origin through:

- CPX expansion;
- generic specialization;
- method lowering;
- closure lowering;
- `defer` rewriting;
- hoisting;
- generated forward declarations.

The C emitter produces mappings from generated C ranges back to the original C+ source.

Compiler diagnostics from the downstream C compiler can therefore be mapped back to their originating `.cp` location.

---

# Generated C

C+ uses a structured C backend.

Compilation follows approximately:

```text
C+ source
    ↓
parser
    ↓
C+ AST
    ↓
semantic analysis
    ↓
CPX / comptime expansion
    ↓
type stabilization
    ↓
lowering
    ↓
C AST
    ↓
C source
```

The backend generates:

- C source;
- optional headers;
- required includes;
- forward declarations;
- symbol names;
- source mappings.

Generated C can be inspected directly.

---

# CLI

The `cplus` command provides the compiler/transcoder interface.

```text
C+ CLI transcoder
usage: cplus <command> <source.cp> [other.cp ...] [--sdk <manifest>] [--c-source <file>] [--library <name-or-path>] [--include-dir <dir>] [--output <file>] [--header <file>]
```

Commands:

```text
transcode   translate one C+ source file to C
emit-c      alias for transcode
check       parse and semantically validate one source file
ast         print the normalized AST
expand      print the post-CPX normalized AST
build       transcode and compile one source file with cc
run         build and execute one source file
lsp         serve compiler diagnostics over stdio JSON-RPC
```

---

# Transcode

Translate C+ into C:

```bash
cplus transcode hello.cp
```

Specify the output file:

```bash
cplus transcode hello.cp --output hello.c
```

Generate a header where applicable:

```bash
cplus transcode library.cp \
    --output library.c \
    --header library.h
```

`emit-c` is an alias:

```bash
cplus emit-c hello.cp --output hello.c
```

---

# Check

Parse and semantically validate C+ without building an executable:

```bash
cplus check hello.cp
```

Multiple source files may be supplied where the compilation requires them:

```bash
cplus check main.cp collections.cp model.cp
```

---

# Inspect the AST

Print the normalized C+ AST:

```bash
cplus ast hello.cp
```

This is useful when developing:

- parser features;
- syntax;
- semantic analysis;
- compiler transformations.

---

# Inspect CPX expansion

Print the normalized program after CPX expansion:

```bash
cplus expand hello.cp
```

This is particularly useful for inspecting:

- generic specializations;
- generated declarations;
- nested CPX expansion;
- compile-time transformations.

It shows the C+ representation before normal C lowering.

---

# Build

Transcode and compile through the configured C compiler:

```bash
cplus build hello.cp
```

With additional C+ sources:

```bash
cplus build main.cp model.cp utilities.cp
```

With a C implementation source:

```bash
cplus build main.cp \
    --c-source native.c
```

With external libraries:

```bash
cplus build main.cp \
    --library m
```

or:

```bash
cplus build main.cp \
    --library /path/to/library.a
```

Additional include directories:

```bash
cplus build main.cp \
    --include-dir ./native/include
```

---

# Run

Build and immediately execute:

```bash
cplus run hello.cp
```

This is useful for examples, tests, and small programs.

---

# SDK selection

Select an explicit SDK manifest:

```bash
cplus build main.cp \
    --sdk /path/to/sdk.toml
```

The SDK manifest provides the compiler with the standard library, runtime, target ABI information, and platform components required for the selected build.

---

# C source interoperability

Compile additional C implementation files with the generated C+ program:

```bash
cplus build main.cp \
    --c-source native.c
```

Multiple C source dependencies may be supplied according to the CLI's argument handling.

Imported declarations remain visible to the C+ semantic model while implementation units are compiled exactly once.

---

# Libraries

Link a named system library:

```bash
cplus build main.cp \
    --library m
```

or provide a library path:

```bash
cplus build main.cp \
    --library ./libnative.a
```

---

# Include directories

Add directories used by imported C declarations:

```bash
cplus build main.cp \
    --include-dir ./include
```

---

# Language server

Start the C+ language server over stdio:

```bash
cplus lsp
```

The language server uses the same compiler front-end and semantic model as the compiler.

It provides compiler-backed functionality such as:

- diagnostics;
- semantic tokens;
- completion;
- hover;
- go-to-definition;
- references;
- imported C symbol information.

The VS Code extension can use TextMate for immediate lexical coloring while the LSP provides semantic language intelligence.

---

# Typical development workflow

Check source:

```bash
cplus check main.cp
```

Inspect generated compile-time structure:

```bash
cplus expand main.cp
```

Inspect normalized AST when required:

```bash
cplus ast main.cp
```

Inspect generated C:

```bash
cplus emit-c main.cp --output main.c
```

Build:

```bash
cplus build main.cp
```

Run:

```bash
cplus run main.cp
```

---

# Example

```c
package example;

import { printf } from c.stdio;

comptime cpx<decl> optional(type T) {
    return {
        struct optional_{T}_t {
            bool valid;
            T value;

            T get(self) {
                return self.value;
            }

            optional_{T}_t empty() {
                return optional_{T}_t {
                    .valid = false
                };
            }
        };
    };
}

optional(int);

int main() {
    optional_int_t value = optional_int_t.empty();

    defer printf("leaving main\n");

    if (value.valid) {
        printf("%d\n", value.get());
    }

    return 0;
}
```

Build and run:

```bash
cplus run example.cp
```

Conceptually, C+ expands the generic declaration, resolves methods and semantics, lowers C+-specific constructs, and generates ordinary C for the selected toolchain.

---

# Design summary

C+ is intended to make common systems-programming patterns expressible directly in the language without giving up C interoperability.

Its central features are:

```text
C-compatible data model
+
methods
+
compile-time functions
+
readable CPX source generation
+
generic specialization
+
compile-time reflection
+
inner functions and closures
+
defer
+
packages and imports
+
native C interoperability
+
source-based SDK/runtime
+
portable C backend
```

The result remains ordinary native code built through the C ecosystem, while the developer works with a substantially more expressive language.


# Getting started

[Getting started](GETTING_started.md)
