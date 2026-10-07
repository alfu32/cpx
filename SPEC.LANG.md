# C+ Language Specification

## 1. Status and scope

This document defines the core semantics of the C+ programming language.

C+ is a C-family language designed to preserve direct interoperability with C while adding structured compile-time generation, methods, generic programming, lexical closures, module imports, source-aware transcoding, and related language facilities.

A conforming C+ implementation SHALL translate a valid C+ compilation unit into semantically equivalent C source and SHALL preserve sufficient source-origin information to map generated C constructs and diagnostics back to their originating C+ constructs.

The terms **MUST**, **MUST NOT**, **SHALL**, **SHALL NOT**, **SHOULD**, **SHOULD NOT**, and **MAY** are normative.

---

# 2. Design principles

C+ follows these principles:

1. Valid C constructs remain representable in C+ unless explicitly superseded by C+ syntax.
2. C+ source is parsed into a structured program representation before transcoding.
3. Compile-time metaprogramming operates on readable C+ source templates rather than C-preprocessor-style textual macros.
4. Generic programming is expressed through compile-time functions rather than through a separate template language.
5. Compile-time generated source is recursively expanded until no applicable compile-time invocation remains.
6. Compile-time type introspection occurs only after the structural type universe has stabilized.
7. Generated source SHALL retain provenance linking it to the original source and compile-time expansion chain.
8. Methods are syntactic members of structures but are lowered to ordinary C functions.
9. Modules, packages, imports, C headers, and C source imports participate in the semantic symbol model.
10. The final emitted program SHALL contain only constructs representable by the selected C target dialect.

---

# 3. Terminology

## 3.1 Compilation unit

A **compilation unit** is one C+ source file together with its declared package, imports, declarations, and executable definitions.

## 3.2 Package

A **package** is a named namespace containing one or more compilation units.

A package name participates in the canonical identity of exported symbols.

Example:

```c
package collections;
```

A public structure:

```c
pub struct list_t {
}
```

has a logical qualified name equivalent to:

```text
collections.list_t
```

Its eventual C symbol MAY be encoded as:

```text
collections__list_t
```

The exact C name encoding is implementation-defined but MUST be deterministic and collision-free within the resulting C program.

## 3.3 Declaration

A **declaration** introduces a named language entity.

Declarations include at least:

- structures,
- unions,
- enums,
- type aliases,
- variables,
- constants,
- functions,
- methods,
- compile-time functions,
- imports,
- package declarations.

## 3.4 Runtime entity

A **runtime entity** is an entity represented in the generated C program.

Examples include:

- variables,
- structures,
- functions,
- enum values,
- runtime constants.

## 3.5 Compile-time entity

A **compile-time entity** exists during C+ compilation.

Examples include:

- a compile-time function,
- a type value,
- an identifier value,
- a CPX value,
- compile-time reflection information.

A compile-time entity need not exist in emitted C.

## 3.6 CPX

A **CPX**, or **C+ Expression**, is a C+ source template evaluated within a compile-time lexical environment.

A CPX consists of:

1. literal C+ syntax;
2. references to compile-time bindings;
3. optionally, nested CPX invocation sites.

A CPX is not defined as an arbitrary byte string.

Its source representation is textual and human-readable, but its semantics are governed by C+ syntax and compile-time bindings.

A conforming compiler SHALL parse or otherwise structurally interpret a CPX before incorporating it into the program.

## 3.7 CPX invocation

A **CPX invocation** is the compile-time invocation of a function whose result is CPX or another compile-time language entity used to construct source.

## 3.8 CPX expansion

**CPX expansion** is the process of evaluating a CPX invocation and replacing or augmenting the invocation site according to the CPX result.

## 3.9 CPX instance

A **CPX instance** is one logical execution of a compile-time invocation.

Multiple CPX instances MAY originate from the same lexical invocation site.

## 3.10 Structural compile-time phase

The **structural compile-time phase** is the compilation phase during which compile-time execution MAY generate or alter declarations participating in the program's declaration and type universe.

## 3.11 Type-universe stabilization

The **type universe is stable** when structural compile-time evaluation can produce no additional type declarations or declarations affecting type structure.

## 3.12 Reflective compile-time phase

The **reflective compile-time phase** occurs after type-universe stabilization.

During this phase, complete type introspection is available.

## 3.13 Lowering

**Lowering** is the transformation of a valid C+ construct into one or more simpler C+ constructs or C constructs.

## 3.14 C-subset AST

The **C-subset AST** is the internal program representation after all C+-specific constructs have been removed.

Every node in the C-subset AST SHALL be representable in the target C dialect.

## 3.15 Origin

An **origin** describes the source provenance of a syntax or semantic entity.

Origins SHALL survive compile-time expansion and lowering.

---

# 4. Program processing model

A C+ implementation SHALL conceptually perform the following phases.

```text
1. Lexical analysis
2. Parsing
3. Declaration catalogue construction
4. Package and import resolution
5. Structural compile-time expansion
6. Structural fixed-point detection
7. Type-universe stabilization
8. Full type resolution
9. Reflective compile-time expansion
10. Semantic analysis
11. C+ lowering
12. Hoisting and declaration synthesis
13. Header and forward-declaration synthesis
14. Dependency/include collection
15. Symbol naming and prefixing
16. C-subset validation
17. C emission
18. Source-map emission
```

An implementation MAY combine or reorder internal implementation steps provided that observable semantics remain equivalent.

---

# 5. Declaration catalogue

Before CPX expansion requiring user-defined compile-time functions occurs, the compiler SHALL construct a **declaration catalogue**.

The catalogue contains sufficient information to identify:

- package declarations;
- imports;
- named types;
- functions;
- methods;
- compile-time functions;
- global declarations;
- externally imported declarations.

A declaration need not be completely type-resolved in order to enter the catalogue.

The catalogue SHALL establish at minimum:

```text
declaration identity
declaration name
declaration kind
lexical scope
source origin
parameter list where applicable
compile-time/runtime classification
```

This permits a compile-time function to be invoked before its textual definition where ordinary C+ declaration-order rules permit such use.

---

# 6. Structures and methods

## 6.1 Method declaration

A function declaration appearing directly inside a structure body is a **method declaration**.

Example:

```c
struct vector_t {
    float x;
    float y;

    float length(self) {
        return sqrt(self.x * self.x + self.y * self.y);
    }
}
```

## 6.2 Instance method

A structure method whose parameter list contains the distinguished receiver parameter `self` in receiver position is an **instance method**.

The receiver is semantically associated with the enclosing structure type.

Example:

```c
float length(self)
```

For:

```c
vector_t v;
v.length();
```

the invocation SHALL resolve to the `length` method belonging to `vector_t`.

A valid lowering is conceptually equivalent to:

```c
vector_t__length(&v);
```

The precise emitted C name is implementation-defined.

### 6.2.1 Pointer receivers

An instance method MAY declare its receiver as `self*`. This is an explicit
pointer receiver declaration. The receiver type is a pointer to the enclosing
structure, and the receiver remains associated with that structure's method
set.

Within a pointer-receiver body, member access through the receiver uses pointer
member syntax:

```c
struct counter_t {
    int value;

    void increment(self*) {
        self->value = self->value + 1;
    }

    int read(self*) {
        return self->value;
    }
};
```

Pointer receivers are invoked with the same member-call syntax as other
instance methods. A pointer expression is passed directly to the lowered
method; an addressable structure value MAY be lowered by taking its address.

```c
int main() {
    counter_t counter;
    counter.value = 41;
    counter_t* pointer = &counter;
    pointer.increment();
    return pointer.read();
}
```

The declaration `self*` is valid only in the receiver position of a method
parameter list. Both `self` and `self*` classify the method as `INSTANCE`; the
pointer form additionally determines the receiver type exposed to semantic
analysis and the body. The spelling of the source member operator does not
select the receiver representation; semantic receiver type does.

## 6.3 Static method

A method declared inside a structure that has no `self` receiver is a **static method**.

Example:

```c
struct vector_t {
    vector_t zero() {
        return vector_t { 0, 0 };
    }
}
```

Invocation:

```c
vector_t.zero();
```

A valid lowering is equivalent to:

```c
vector_t__zero();
```

## 6.4 Method invocation syntax

Instance methods use:

```text
expression . method(arguments)
```

Static methods use:

```text
type-name . method(arguments)
```

The parser SHALL NOT be required to distinguish these forms purely syntactically.

Semantic resolution SHALL determine whether the expression to the left of `.` denotes:

- a runtime value,
- a type,
- a package,
- another member-bearing entity.

## 6.5 Member conflict

If a member name simultaneously identifies a field and method and the syntax is ambiguous, invocation syntax SHALL determine the interpretation.

Example:

```c
x.foo
```

selects a value/member access.

```c
x.foo()
```

selects a callable member.

If both interpretations remain semantically valid and indistinguishable, the program is ill-formed and SHALL produce an ambiguity diagnostic.

---

# 7. Compile-time functions

## 7.1 Definition

A **compile-time function** is a function whose execution occurs during C+ compilation.

A compile-time function is declared using `comptime`.

Example:

```c
comptime cpx optional(type T) {
    ...
}
```

## 7.2 Permitted results

A compile-time function MAY produce:

- scalar compile-time values;
- type values;
- identifier values;
- expression values;
- CPX values;
- declarations;
- generated structures;
- generated unions;
- generated enums;
- generated functions;
- generated methods;
- generated aliases;
- generated statements;
- generated imports or dependency declarations where permitted.

The canonical mechanism for generating C+ source structure is CPX.

## 7.3 Compile-time side effects

Compile-time functions SHALL NOT implicitly modify unrelated syntax.

Any structural modification SHALL occur through defined CPX expansion or compiler-provided compile-time context operations.

Compiler implementations SHALL ensure deterministic output for deterministic compile-time inputs.

---

# 8. CPX source templates

## 8.1 General rule

A CPX source template is written using normal C+ syntax.

Example:

```c
comptime cpx optional(type T) {
    return {
        struct optional_{T}_t {
            bool valid;
            T value;
        };
    };
}
```

The contents of the returned block are parsed according to C+ syntax under CPX template rules.

## 8.2 Direct binding interpolation

A compile-time binding used as a complete syntactic token requires no special interpolation marker where its syntactic category is unambiguous.

Example:

```c
T value;
```

If `T` is declared as:

```c
type T
```

then `T` denotes a compile-time type value and is inserted in type position.

`T` is not a string containing a type name.

## 8.3 Identifier composition

When a compile-time value forms only part of an identifier, explicit interpolation SHALL be used.

Example:

```c
optional_{T}_t
```

For `T = int`, the resulting identifier is:

```c
optional_int_t
```

A bare identifier containing the character sequence `T` SHALL NOT be interpreted as interpolation merely because a compile-time binding named `T` exists.

Thus:

```c
Temporary
```

remains the identifier `Temporary`.

This rule eliminates lexical ambiguity.

## 8.4 Interpolation categories

Compile-time values SHALL retain semantic kind.

At minimum, implementations SHALL distinguish:

```text
type
identifier
integer
floating value
boolean
string
expression
statement
declaration
CPX
```

Interpolation MUST be valid for the syntactic context in which it occurs.

For example, a declaration-valued binding cannot be inserted where an ordinary expression is required unless an explicit conversion defined by the language exists.

Direct binding interpolation SHALL be recognized only at a complete lexical
token boundary. Binding-looking text inside string literals, character
literals, line comments, and block comments SHALL remain literal text. Explicit
`{binding}` interpolation is required for identifier composition; a value that
cannot be rendered as one identifier component SHALL produce a compile-time
interpolation diagnostic rather than being passed to a later parser or C
backend phase.

Typed scalar literals SHALL be canonicalized for specialization identity while
retaining their semantic category for rendering. Lists are recursively typed;
their element categories participate in the canonical specialization value.

## 8.5 CPX categories

A CPX has a syntactic result category.

At minimum:

```text
cpx<unit>
cpx<decl>
cpx<member>
cpx<stmt>
cpx<expr>
cpx<type>
```

A CPX call site SHALL accept only categories valid in its syntactic position.

Example:

```text
global scope     accepts declaration/unit CPX
struct body      accepts member/declaration CPX
block scope      accepts statement/declaration CPX as permitted
expression slot  accepts expression CPX
type slot        accepts type CPX
```

A mismatch is a compile-time error.

Structural `cpx<type>` and `cpx<member>` results MAY be introduced through a
declaration-context invocation and are processed before type-universe
stabilization. Their generated declarations remain subject to ordinary C+
declaration and collision rules.

---

# 9. Generic programming

## 9.1 Generic mechanism

C+ generics are defined through compile-time functions.

C+ need not provide an independent template subsystem.

Example:

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

Invocation:

```c
pair(int, float);
```

produces a declaration equivalent to:

```c
struct pair_int_float_t {
    int first;
    float second;
};
```

## 9.2 Generic type generation

A compile-time invocation used in a type/declaration context MAY generate:

- struct types;
- union types;
- enum types;
- aliases;
- other valid C+ type declarations.

## 9.3 Generic function generation

A compile-time function MAY generate ordinary functions or methods.

Example conceptually:

```c
comptime cpx<decl> make_max(type T) {
    return {
        T max_{T}(T a, T b) {
            return a > b ? a : b;
        }
    };
}
```

## 9.4 Specialization identity

Two generic invocations with equivalent canonical compile-time arguments SHALL refer to the same specialization where the generated declaration is declared specialization-stable.

The implementation SHALL use semantic argument identity rather than source spelling alone.

Thus aliases referring to the same canonical type MAY resolve to the same specialization unless the language construct explicitly preserves alias identity.

---

# 10. CPX expansion model

## 10.1 Recursive expansion

A CPX result MAY contain further CPX invocations.

Example:

```c
comptime cpx<decl> container(type T) {
    return {
        struct container_{T}_t {
            T value;

            generate_hash(T);
            generate_equality(T);
        };
    };
}
```

After `container(int)` expands, the generated `generate_hash(int)` and `generate_equality(int)` calls remain CPX invocation sites.

They SHALL subsequently be evaluated when their dependencies are satisfiable.

## 10.2 Fixed point

CPX expansion SHALL continue until a fixed point is reached for the current compilation phase.

A phase has reached a fixed point when evaluating all currently eligible CPX invocations produces no additional applicable CPX invocation or structural change for that phase.

## 10.3 Cycles

A CPX expansion cycle SHALL be diagnosed.

Example:

```text
A → B → C → A
```

Implementations SHALL NOT rely solely on a maximum recursion depth to detect deterministic cycles.

A maximum expansion depth MAY additionally be enforced to protect the compiler from dynamically growing expansion graphs.

---

# 11. CPX instance identity

## 11.1 Lexical call site

Every CPX invocation has a lexical call-site identity associated with its syntax node.

## 11.2 Dynamic compile-time execution

A lexical invocation site MAY execute multiple times.

Example:

```c
comptime for(type T : types) {
    make_serializer(T);
}
```

The source contains one `make_serializer` call site but potentially many CPX instances.

## 11.3 Expansion identity

A CPX instance SHALL therefore be identified by more than source file and character offset.

Its logical identity SHALL include at least:

```text
compile-time declaration identity
lexical call-site identity
parent expansion identity, if any
evaluation/specialization key
```

Conceptually:

```text
ExpansionId =
    DeclarationId
    + CallSiteNodeId
    + ParentExpansionId
    + EvaluationKey
```

## 11.4 Evaluation key

The compiler SHOULD derive an evaluation key automatically from canonical compile-time arguments.

For:

```c
make_serializer(User)
make_serializer(Account)
```

the instances are distinct even if produced by the same loop call site.

Explicit user keys MAY be introduced in future language extensions but SHALL NOT be required for ordinary deterministic generic expansion.

---

# 12. Hygiene

## 12.1 Generated local symbols

Local declarations introduced inside a CPX expansion SHALL be hygienic by default.

A generated local variable SHALL NOT accidentally capture or collide with a same-named variable at the invocation site.

Example:

```c
comptime cpx<stmt> twice(expr x) {
    return {
        {
            int tmp = x;
            use(tmp);
            use(tmp);
        }
    };
}
```

If the caller already declares:

```c
int tmp;
```

the generated `tmp` SHALL remain logically distinct.

## 12.2 Exported/injected names

Names deliberately introduced into the surrounding namespace are not hygienically hidden.

Generated type, function, method, field, or explicitly exported declaration names participate in ordinary name-collision rules.

## 12.3 Duplicate generated names

If two independent expansions attempt to introduce the same canonical declaration name into the same namespace:

- if they describe the same specialization and are semantically identical, the implementation MAY deduplicate them;
- otherwise the program is ill-formed and SHALL produce a duplicate declaration diagnostic.

---

# 13. Scope-sensitive CPX expansion

A CPX invocation executes within a **compile-time context**.

The context includes at minimum:

```text
current package
current compilation unit
current lexical scope
current containing type, if any
current containing function, if any
available declarations
compile target information
source origin
parent CPX instance
```

A compile-time API MAY additionally expose controlled operations for:

```text
adding a declaration
hoisting a declaration
registering cleanup
requesting an include
requesting an import
querying symbol information
```

A CPX SHALL NOT mutate compiler state outside operations defined by the language or implementation interface.

---

# 14. Inner functions and lexical capture

## 14.1 Inner function

A function declared inside an executable scope is an **inner function**.

An inner function MAY reference variables in enclosing lexical scopes.

Example:

```c
void foo() {
    int x = 10;

    int add(int y) {
        return x + y;
    }

    print(add(4));
}
```

## 14.2 Capture lowering

Captured variables SHALL be represented during lowering by an implementation-defined closure environment.

A valid lowering is conceptually equivalent to:

```c
typedef struct {
    int* x;
} foo__add__env_t;

static int foo__add(foo__add__env_t* env, int y) {
    return *env->x + y;
}
```

with an environment value created in the enclosing scope.

## 14.3 Capture semantics

Unless explicitly specified otherwise by a future ownership rule:

- references to mutable enclosing variables are captured by reference;
- values requiring ownership transfer SHALL obey C+ ownership semantics;
- the compiler SHALL reject a closure whose generated lifetime would outlive an invalid captured reference.

## 14.4 CPX interpretation

Inner-function lowering MAY be implemented internally as CPX expansion but its observable semantics are language-defined and SHALL NOT depend on implementation-specific CPX text.

---

# 15. Deferred execution

## 15.1 `defer`

A `defer` statement registers an action to execute when its enclosing lexical scope exits.

Example:

```c
{
    FILE* f = fopen(...);
    defer fclose(f);

    process(f);
}
```

## 15.2 Exit paths

Deferred actions SHALL execute on every language-defined normal exit path from the scope, including:

- natural fallthrough;
- `return`;
- `break` where the deferred scope is exited;
- `continue` where the deferred scope is exited.

Behavior for abnormal process termination or non-language control transfer such as raw `longjmp` is implementation-defined unless separately specified.

## 15.3 Order

Multiple deferred actions in the same scope SHALL execute in reverse registration order.

## 15.4 Lowering

`defer` MAY be implemented using CPX or AST transformation.

The compiler SHALL ensure semantically equivalent cleanup code is inserted before every applicable scope exit.

---

# 16. String templates

A string template is a compile-time or lowered expression that combines literal string segments with runtime or compile-time expressions.

Its source representation SHALL remain readable and syntax-highlightable C+.

String-template lowering MAY generate:

- concatenation expressions;
- formatting calls;
- generated helper functions;
- local temporary storage.

Any generated helper function that captures local state follows inner-function capture semantics.

String-template syntax SHALL define interpolation boundaries explicitly where lexical ambiguity would otherwise occur.

A template implementation SHALL NOT depend on C-preprocessor textual concatenation.

---

# 17. Structural compile-time phase

## 17.1 Purpose

The structural compile-time phase generates declarations that affect the program's type and declaration universe.

Examples include compile-time generation of:

- structures;
- unions;
- enums;
- aliases;
- function signatures;
- methods;
- generic specializations;
- package-visible declarations.

## 17.2 Iteration

The compiler SHALL repeatedly:

```text
1. identify structurally eligible CPX invocations;
2. evaluate them;
3. incorporate generated declarations;
4. update the declaration catalogue;
5. resolve newly satisfiable references;
6. repeat.
```

## 17.3 Structural stability

Structural processing terminates when no eligible operation creates, removes, or modifies a declaration affecting structural type information.

At that point the compiler declares the type universe stable.

---

# 18. Type-universe barrier

Complete compile-time type introspection SHALL NOT be assumed valid before the type universe is stable.

Before stabilization, only partial introspection explicitly documented as early-safe MAY be performed.

After stabilization, the compiler SHALL make complete type metadata available.

The barrier prevents introspection results from changing merely because another structural generic expansion occurred later.

---

# 19. Compile-time type introspection

After type-universe stabilization, compile-time code MAY query language type information.

The reflective API SHOULD support at least conceptual operations equivalent to:

```text
name_of(T)
size_of(T)
align_of(T)
kind_of(T)
fields_of(T)
methods_of(T)
parameters_of(F)
return_type_of(F)
element_type_of(T)
is_pointer(T)
is_struct(T)
is_enum(T)
is_function(T)
```

Returned reflection entities SHALL themselves be structured compile-time values rather than unstructured strings.

For example a reflected field SHOULD expose data corresponding to:

```text
name
type
visibility
origin
attributes
offset where known
```

---

# 20. Reflective compile-time generation

Reflective CPX executes after structural stabilization.

By default, reflective CPX:

- MAY inspect complete type information;
- MAY generate executable functions;
- MAY generate statements;
- MAY generate constant data;
- MAY generate metadata;
- SHALL NOT introduce new structural types that invalidate the stabilized type universe.

If a future C+ version permits reflection-driven structural generation, that feature SHALL define an additional stabilization cycle explicitly.

A compiler SHALL NOT silently reopen the stabilized type universe.

---

# 21. Imports

## 21.1 Import declaration

An import is a language declaration.

Supported conceptual forms include:

```c
import foo;
import foo as bar;
import { a, b, c } from foo;
import { add } from ./module_helpers.cp;
import { fs } from stdlib/io;
import { fs as fs1 } from "some/ref.cp";
import { Point } from ./geometry.cp;
import { Point as Position } from "some/lib/ref.cp";
import geometry as geo;
```

An import target without a leading relative-path marker is a logical module or
package name. Logical package segments MAY be separated by `/`, as in
`stdlib/io`, and dotted module names remain supported for compatibility.

A target beginning with `./` or `../`, or a quoted target naming a `.cp` file,
is a source-path import. A source-path import SHALL be resolved relative to the
importing compilation unit. The compiler SHALL preserve the imported file's
module identity when resolving its declarations.

## 21.2 Import semantics

An import introduces bindings from another compilation unit or package into the current semantic environment.

Selective imports SHALL support every public top-level declaration kind that
may be named by an import, including type aliases (`typedef`), structures,
unions, and enumerations as well as functions and values. Importing a type
creates a type-name binding usable under the imported name in every language
context that accepts that type, including declarations, fields, function
parameters and results, pointers, arrays, casts, and type queries. An `as`
alias SHALL rename the type binding in those contexts just as it renames a
value binding.

For distinct modules, placing a source file in the same compilation graph or
package SHALL NOT by itself make its declarations available by unqualified
name in another module. A declaration is visible across module boundaries only
through a selective import or a module import and qualified name. A module
import alias SHALL permit qualified access to the target module's public type
and value declarations. Private declarations SHALL NOT be importable. These
rules apply equally to source-path imports and logical package imports.

Imports SHALL participate in:

- name resolution;
- completion;
- navigation;
- reference discovery;
- diagnostics;
- dependency calculation.

## 21.3 Aliased import

```c
import foo as bar;
```

binds the imported module or package to local name `bar`.

## 21.4 Selective import

```c
import { a, b } from foo;
import { fs as fs1 } from "some/ref.cp";
```

introduces only the selected public entities into the destination scope.
Each selective name MAY be followed by `as localName`; references in the
destination scope then use `localName`.

Type import example:

```c
import { Point as Position } from "./geometry.cp";

Position origin;
Position *cursor;
```

An aliased module import may qualify the same public type:

```c
import geometry as geo;

geo.Point origin;
```

The imported declaration's kind SHALL be resolved from the target module's
public declaration catalogue; a type name SHALL NOT be treated as a failed
function import. The compiler SHALL diagnose missing or non-public names and
conflicting local bindings. Source type imports SHALL behave consistently
whether the target module exports only types or also exports functions and
values.

Name collisions are compile-time errors unless explicitly resolved through qualification or aliasing.

## 21.5 Cyclic imports

Package/module import cycles MAY exist only if all declarations necessary to resolve the cycle can be catalogued without requiring execution of an unresolved compile-time dependency.

A compile-time dependency cycle across imports that cannot reach a fixed point SHALL be diagnosed.

---

# 22. Importing C

## 22.1 C header import

C+ SHALL permit importing declarations from C headers.

A C import SHALL have two effects:

1. make imported C declarations available to the C+ semantic model;
2. ensure required C dependencies are represented in generated output.

The implementation SHALL provide built-in adapters for the standard C header
modules `c.stdio`, `c.stddef`, `c.stdlib`, `c.math`, `c.string`, `c.ctype`,
`c.time`, `c.stdint`, and `c.stdarg`. Their imported declarations SHALL retain
their C spelling and SHALL cause the corresponding system header to be emitted
(`stdio.h`, `stddef.h`, `stdlib.h`, `math.h`, `string.h`, `ctype.h`, `time.h`,
`stdint.h`, or `stdarg.h`). Implementations MAY add configured header adapters.
An imported symbol that is not declared by the selected adapter SHALL produce a
diagnostic; the compiler SHALL NOT guess a foreign signature.

## 22.2 Foreign symbols

Imported C declarations SHALL be represented as foreign symbols.

Examples include:

- foreign types;
- foreign functions;
- foreign globals;
- enum constants;
- macros where the implementation can safely represent them.

## 22.3 Unsupported C preprocessor constructs

A C construct that cannot be represented semantically SHALL either:

- be preserved as an opaque C dependency where safe;
- or produce a diagnostic when referenced by C+.

The compiler SHALL NOT invent semantic information for an unresolved macro.

## 22.4 C source import

An implementation MAY allow direct C source imports.

Such imports SHALL preserve declarations required for semantic analysis while ensuring that C implementation definitions are compiled exactly once in the resulting build graph.

---

# 23. Symbol identity and C symbol generation

Every declared symbol SHALL possess a stable semantic identity distinct from its emitted C spelling.

Conceptually:

```text
Symbol {
    sourceName
    qualifiedName
    semanticId
    emittedCName
}
```

Example:

```text
sourceName:
    push

qualifiedName:
    collections.list_t.push

possible emittedCName:
    collections__list_t__push
```

Semantic references SHALL use symbol identity rather than emitted names.

C naming and prefixing SHALL occur only after semantic resolution or be semantically equivalent to doing so.

---

# 24. Hoisting

Some C+ constructs require declarations to move to a C-valid scope.

Examples include:

- inner functions;
- generated closure environment structures;
- generated helper functions;
- generic specializations;
- generated static support declarations.

Hoisting SHALL preserve semantic scope while relocating emitted representation.

A hoisted construct SHALL retain an origin chain pointing to the source construct that caused its generation.

---

# 25. Forward declarations

The compiler SHALL synthesize forward declarations where required by C declaration-order rules.

A synthesized forward declaration is not considered an independent source declaration.

Its origin SHALL refer to the definition from which it was generated.

Forward declarations SHALL be deduplicated.

---

# 26. Header generation

Where C+ compilation produces separate C headers and C implementation files, public declarations SHALL be derived structurally from resolved declarations.

Header generation SHALL NOT depend on extracting or copying arbitrary source lines.

For a public function definition:

```text
FunctionDefinition
```

the compiler may derive:

```text
FunctionDeclaration
```

for header emission.

For a structure definition requiring an incomplete declaration, the compiler MAY synthesize:

```c
typedef struct foo_t foo_t;
```

or the appropriate target-C equivalent.

---

# 27. Include and dependency generation

Includes SHALL be represented semantically before text emission.

Conceptually:

```text
Dependency
    SystemInclude
    LocalInclude
    ModuleDependency
    ForeignLibrary
```

Compiler passes MAY register dependencies.

Examples:

```text
use of uint64_t
    → require <stdint.h>

use of malloc
    → require <stdlib.h>
```

Before emission, dependencies SHALL be:

- deduplicated;
- ordered deterministically;
- grouped according to implementation formatting rules.

---

# 28. Source provenance

## 28.1 Source span

Each parsed source node SHALL retain its original source span.

A span identifies at least:

```text
source file
start offset
end offset
start line
start column
end line
end column
```

## 28.2 Origin chain

Generated nodes SHALL preserve causal provenance.

An origin model SHALL be capable of representing conceptually:

```text
Direct(span)

GeneratedFrom(origin)

Expanded(
    invocationOrigin,
    compileTimeDefinitionOrigin,
    parentExpansionOrigin
)

Synthetic(parentOrigin)
```

## 28.3 Generated source mapping

During C emission, the compiler SHALL produce mappings from generated ranges to source origins.

Mappings MAY be generated at token, expression, statement, declaration, or another sufficiently precise granularity.

Compiler diagnostics referring to generated C SHOULD be translated back to the most relevant originating C+ location.

## 28.4 Multiple origins

A generated construct MAY depend on multiple original source regions.

For example a generic specialization may derive from:

- the generic CPX definition;
- the invocation site;
- the type argument declaration.

The compiler SHALL preserve sufficient origin information to report all relevant locations when necessary.

---

# 29. CPX and source provenance

Every CPX instance SHALL carry:

```text
definition origin
invocation origin
parent CPX origin
evaluation key
```

A node produced by a CPX SHALL inherit this expansion chain.

Nested CPX expansion extends rather than replaces the chain.

This allows diagnostics to report information conceptually such as:

```text
error in generated declaration
generated by make_serializer(User)
invoked here
inside generate_model(User)
defined here
```

---

# 30. Lowering model

C+ lowering MAY proceed through multiple intermediate C+ forms.

A transformation is not required to produce immediately valid C.

Example:

```c
obj.method(x)
```

may first resolve to an explicit method-reference representation and later lower to:

```c
type__method(&obj, x)
```

Similarly, a structure method may first be extracted from its containing structure while other C+ features remain.

For a pointer receiver, method lowering preserves the pointer representation:

```c
counter_t__increment(pointer);
```

and the lowered method body may use native C pointer-member access:

```c
void counter_t__increment(counter_t* self) {
    self->value = self->value + 1;
}
```

Each lowering phase SHALL establish documented invariants.

Typical invariants include:

```text
after CPX expansion:
    no eligible CPX invocation remains

after method lowering:
    no runtime methods remain structurally nested in structs

after closure lowering:
    no capturing inner function remains

after ownership lowering:
    no unsupported ownership syntax remains

before C emission:
    every node is part of the target C subset
```

---

# 31. Final C-subset validation

Before C emission, the implementation SHALL validate that no unsupported C+ construct remains.

At minimum the following SHALL have been eliminated or lowered:

- CPX invocation nodes;
- compile-time functions requiring execution;
- structure methods;
- inner functions with lexical capture;
- `defer`;
- C+-specific generic constructs;
- package-qualified references not representable directly in C;
- C+-specific interpolation syntax;
- unresolved imports;
- unresolved type introspection expressions.

Failure to eliminate such a node is a compiler error, not valid output.

---

# 32. Name lookup

Name lookup SHALL distinguish namespaces or semantic kinds sufficiently to resolve:

- runtime values;
- types;
- packages/modules;
- compile-time variables;
- compile-time functions;
- ordinary functions;
- structure members.

Within CPX templates, compile-time bindings take precedence only when the syntax position permits interpolation.

A normal source identifier SHALL NOT be replaced simply because a compile-time variable of the same character sequence exists outside the applicable template lexical scope.

---

# 33. CPX name conflicts

Within a CPX template:

```c
T value;
```

where `T` is a compile-time type parameter refers to the compile-time binding.

To refer deliberately to a source-level entity shadowed by a compile-time binding, the language SHALL require explicit qualification.

Implementations SHALL NOT resolve such conflicts based on textual replacement order.

---

# 34. Parsing and semantic ambiguity

The parser MAY initially represent constructs whose interpretation depends on semantic knowledge without prematurely selecting an incorrect meaning.

C-derived constructs such as:

```c
foo * bar;
```

may depend on whether `foo` denotes a type.

The parser MAY:

- retain an unresolved syntactic form;
- construct candidate representations;
- defer classification to symbol resolution.

The language semantics SHALL be determined from resolved symbol meaning, not parser rule ordering.

---

# 35. CPX versus runtime function calls

A function invocation is compile-time if and only if semantic resolution selects a compile-time function or language-defined compile-time operation.

Syntactic appearance alone SHALL NOT determine compile-time execution.

Therefore:

```c
foo(x);
```

may represent either:

```text
runtime function invocation
```

or:

```text
compile-time CPX invocation
```

depending on resolved `foo`.

This classification SHALL be known before lowering.

---

# 36. Compile-time argument evaluation

Arguments to a compile-time function SHALL be evaluated according to their declared compile-time parameter kinds.

For:

```c
comptime cpx pair(type T, type R)
```

the invocation:

```c
pair(int, User);
```

passes semantic type values.

For an expression parameter:

```c
comptime cpx twice(expr e)
```

the argument SHALL be represented as an expression entity, preserving source structure and scope information.

For a value parameter such as:

```c
comptime cpx array(type T, int N)
```

`N` SHALL be compile-time evaluable.

---

# 37. Referential safety of generated expressions

An expression-valued CPX that references invocation-site runtime variables SHALL retain those references using semantic identity rather than unqualified textual substitution.

This prevents generated code from accidentally binding to a different variable after hoisting or rewriting.

When a reference cannot remain valid after transformation, the compiler SHALL synthesize an appropriate capture mechanism or report an error.

---

# 38. Compile-time execution order

Compile-time execution SHALL be dependency-driven rather than defined solely by source order.

An invocation becomes eligible when:

- its compile-time function is catalogued;
- its arguments can be resolved;
- all required compile-time information is available;
- the current compilation phase permits the required operation.

Among otherwise independent eligible invocations, order SHALL NOT affect observable semantics.

If different execution orders produce different externally visible programs, the source program is ill-formed unless the language construct explicitly specifies ordering.

---

# 39. Determinism

Given:

- identical source;
- identical imported source/declarations;
- identical compiler-visible target configuration;
- identical declared compile-time inputs;

a conforming implementation SHOULD produce semantically identical expanded C+ and generated C.

Compile-time functions SHALL NOT implicitly depend on nondeterministic state unless such access is explicitly provided and declared by the language implementation.

---

# 40. Formatting and IDE representation

CPX contents are C+ source and SHALL be eligible for normal C+ tooling.

A conforming language service SHOULD provide within CPX templates:

- syntax highlighting;
- formatting;
- bracket matching;
- diagnostics;
- completion;
- type information;
- symbol navigation where resolvable.

Compile-time parameters SHOULD receive semantic highlighting distinct from unrelated source identifiers.

Generated CPX expansions MAY be exposed by an IDE as virtual/generated source, but the programmer SHALL NOT be required to edit collapsed single-line macro output.

---

# 41. Language-server model

The C+ language server SHOULD use the same authoritative syntax and semantic model as the compiler.

It SHALL be capable of representing:

- imported C+ declarations;
- generated declarations;
- generic specializations;
- imported C symbols;
- methods;
- compile-time symbols;
- CPX call sites;
- CPX expansion origins.

Go-to-definition on a generated entity SHOULD lead to the most appropriate originating declaration, with expansion details available where useful.

---

# 42. TextMate and editor lexical highlighting

An editor MAY use a TextMate grammar for immediate lexical highlighting.

TextMate highlighting is non-authoritative.

Semantic interpretation SHALL come from the C+ parser and language server.

TextMate rules SHALL NOT define language semantics.

---

# 43. C interoperability

Ordinary C declarations and ABI-compatible C constructs SHALL retain their C representation wherever C+ features do not require lowering.

C+ MUST NOT rename externally imported C symbols unless explicitly aliased at the C+ semantic level while retaining the required external C linkage name.

Foreign function calling semantics SHALL follow the selected C ABI.

---

## 43.1 ABI and layout queries

C+ provides compiler-owned layout queries whose result type is `size_t`:

```cplus
sizeof(T)
alignof(T)
offsetof(T, field)
layoutof(T)
```

`sizeof` and `alignof` accept either a type or an expression. `offsetof` accepts
an aggregate type and a declared field. `layoutof` is the scalar source-level
view of the target's structured ABI layout; compiler APIs expose the complete
field offsets, sizes, and alignment. The compiler MUST validate the type and
field against the selected target before C emission and MUST diagnose unknown
or unsupported layout requests rather than guessing.

## 43.2 ABI identity and platform contracts

Every callable declaration has an ABI identity. The supported identities are
`abi.c`, `abi.system`, `abi.cplus`, `abi.intrinsic`, and `abi.runtime`.
Equivalent machine calling conventions do not erase this semantic distinction.
Foreign and exported declarations retain their source name separately from
their linker name and library. Target-specific storage requests such as
thread-local storage, explicit alignment, packed layout, and no-return are
validated against the target descriptor. A `thread_local` object declaration
requests target TLS storage; when it is public, the generated C header and
definition SHALL carry the corresponding target C TLS storage specifier. The
request SHALL be rejected when the selected target does not advertise TLS.

An exported declaration MAY provide an explicit linker name with
`@export_name("...")`. The source-level declaration remains the name used by
C+ semantic lookup, while the supplied name is used by generated C callers and
public headers. The external name SHALL be unique within the emitted linkage
unit and SHALL not alter foreign-import names.

Variadic declarations retain their variadic parameter boundary through
semantic analysis, C lowering, and public header generation. A generated
prototype for a declaration with an ellipsis SHALL include `...`; a C+ call
site SHALL obey the selected C ABI's fixed-parameter and variadic-argument
rules.

## 43.3 Runtime and standard-library profiles

The selected runtime profile is one of `freestanding`, `cplus`, or `system`;
the selected libc profile is one of `none`, `c17`, or `c23`. A self-hosted C+
profile owns startup, compiler support, memory primitives, and termination
semantics. Normal termination runs registered normal handlers in reverse
registration order and flushes streams through the libc layer. Quick exit runs
only quick handlers, while immediate exit and abort skip normal cleanup.

The SDK's native `std` API is independent of host libc types. C compatibility
headers are generated/delivered views over the same semantic declarations;
unsupported target facilities MUST be reported through a capability or error
result and MUST NOT silently fall back to host headers or libraries.

## 43.4 C-compatible type qualifiers and declarators

C+ declarations SHALL preserve the C-compatible type qualifiers `const` and
`volatile`. The `restrict` qualifier MAY be used where the selected C dialect
supports it. Qualifiers before a base type apply to that base type; qualifiers
following a pointer declarator apply to that pointer declarator.

The parser and semantic model SHALL retain qualifier placement through lowering
so that generated C preserves the corresponding declarator semantics. Qualifiers
do not change the storage size or alignment of the qualified type.

Pointer depth and pointer qualifiers SHALL be represented structurally rather
than embedded in a guessed type name. Array dimensions remain declarator data
associated with the declared identifier. Unsupported declarator forms, including
function-return-pointer suffixes not covered by the current declarator model,
SHALL produce a stable diagnostic rather than silently changing type meaning.

Foreign typedefs SHALL retain both their external C spelling and their known
underlying semantic type when the selected header catalogue provides it. ABI
layout queries SHALL use the underlying type while C emission SHALL continue to
use the external typedef spelling.

Function-pointer declarators SHALL be represented as callable types wrapped in
the required pointer depth. A declaration such as:

```cplus
int apply(int (*callback)(int value), int value) {
    return callback(value);
}
```

SHALL preserve the callback signature through semantic checking and lower to a
C function-pointer declarator. A named C+ function MAY be passed to a matching
function-pointer parameter using the selected C ABI's function-to-pointer
conversion. Calling a function-pointer value SHALL validate its argument count,
argument types, and return type exactly as a direct call does.

Object pointers MAY be incremented or decremented by an integer and compatible
object pointers MAY be subtracted, producing `ptrdiff_t`. Pointer arithmetic on
function pointers or incompatible object pointers SHALL be diagnosed. Arrays
used in value expressions SHALL provide the corresponding element-pointer
behavior required by indexing and pointer arithmetic.

Initializers SHALL be type-checked against the declared object type before C
emission. Global and local initializer mismatches SHALL produce a semantic
diagnostic. Explicit casts MAY convert supported primitive and aggregate pointer
types and SHALL retain their target declarator in generated C.

The native filesystem API MUST accept UTF-8 paths whose separators are `/`.
Portable C+ source MUST NOT select Windows separators, drive spelling, or a
host character encoding. The selected PAL adapter owns conversion to the
target OS path representation and encoding API; Java/Kotlin compiler tooling
may use native host paths internally, but serialized and logical path
identities MUST remain slash-normalized.

# 44. Conflict rules summary

The following rules are normative.

### 44.1 Compile-time binding versus literal identifier

A standalone CPX token matching a compile-time binding resolves to that binding when the syntactic category accepts it.

Identifier substring interpolation requires explicit `{...}` syntax.

### 44.2 Static versus instance method

A method with `self` is an instance method.

A method with `self*` is also an instance method and has a pointer to the
enclosing structure as its receiver.

A method without `self` is static.

### 44.3 Type versus runtime expression on left side of `.`

Semantic resolution determines whether the left operand is a type or value.

### 44.4 Imported symbol collision

Two unqualified imported symbols with the same name are an error unless explicitly aliased or qualified.

### 44.5 Generated declaration collision

Distinct incompatible generated declarations with the same canonical name are an error.

### 44.6 CPX scope collision

Generated local names are hygienic by default.

Explicitly exported names participate in ordinary collision rules.

### 44.7 Structural generation after stabilization

Reflective compile-time code SHALL NOT silently modify the stabilized structural type universe.

### 44.8 CPX recursion

Recursive expansion that does not reach a fixed point is an error.

### 44.9 C+ construct remaining at C emission

Any non-lowered C+-only construct is a compiler error.

### 44.10 Ambiguous syntax requiring semantic type knowledge

Semantic resolution decides; parser alternative order SHALL NOT define program semantics.

### 44.11 Generic specialization collision

Semantically equivalent generic invocations MAY reuse one specialization.

Different specializations SHALL receive distinct semantic identities and emitted names.

### 44.12 Generated helper symbol collision

Compiler-generated helper symbols SHALL use hygienic identities and SHALL NOT collide with user source identifiers.

---

# 45. Canonical example

Source:

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

Conceptual expansion:

```c
struct optional_int_t {
    bool valid;
    int value;

    int get(self) {
        return self.value;
    }

    optional_int_t empty() {
        return optional_int_t {
            .valid = false
        };
    }
};
```

Method lowering:

```c
struct optional_int_t {
    bool valid;
    int value;
};

int optional_int_t__get(optional_int_t* self) {
    return self->value;
}

optional_int_t optional_int_t__empty() {
    return (optional_int_t) {
        .valid = false
    };
}
```

Runtime call lowering:

```c
optional_int_t value = optional_int_t__empty();

if (value.valid) {
    printf("%d\n", optional_int_t__get(&value));
}
```

`defer` lowering:

```c
int main() {
    optional_int_t value = optional_int_t__empty();

    if (value.valid) {
        printf("%d\n", optional_int_t__get(&value));
    }

    printf("leaving main\n");
    return 0;
}
```

Each generated declaration and expression retains an origin chain to:

- its original source;
- the `optional(int)` CPX invocation where applicable;
- the `optional` compile-time definition where applicable.

---

# 46. Formal conceptual model

The core language pipeline can be summarized as:

```text
C+ source
    │
    ▼
Parser
    │
    ▼
C+ syntax tree / AST
    │
    ▼
Declaration catalogue
    │
    ▼
Package/import resolution
    │
    ▼
Structural CPX dependency graph
    │
    ▼
Structural expansion to fixed point
    │
    ▼
Stable type universe
    │
    ▼
Type resolution
    │
    ▼
Reflective CPX
    │
    ▼
Resolved C+ AST
    │
    ├── methods
    ├── inner functions
    ├── defer
    ├── interpolation
    ├── generic specializations
    ├── package symbols
    └── generated declarations
    │
    ▼
Lowering pipeline
    │
    ▼
C-subset AST
    │
    ├── synthesized declarations
    ├── dependencies/includes
    ├── stable emitted names
    └── origin metadata
    │
    ▼
C emitter
   / \
  /   \
C source   source map
```

---

# 47. Core semantic definition of CPX

The normative definition of CPX is:

> A CPX is a typed C+ source template evaluated in a compile-time lexical environment. Literal portions of a CPX are interpreted as C+ syntax. References to compile-time bindings interpolate semantic compile-time entities according to their declared kinds and syntactic context. A CPX may contain nested compile-time invocation sites. CPX expansion recursively produces C+ syntax until the applicable compile-time expansion phase reaches a fixed point.

This definition distinguishes CPX from the C preprocessor:

- CPX preserves normal multiline C+ syntax.
- CPX participates in parsing and semantic analysis.
- Compile-time parameters are typed entities rather than raw token strings.
- Generated local identifiers are hygienic.
- Generated declarations participate in the semantic symbol system.
- CPX preserves expansion provenance.
- CPX expansion may be recursively evaluated.
- CPX may generate declarations, statements, expressions, types, methods, imports, or compilation-unit fragments according to its declared category.

---

# 48. Core semantic definition of C+ generic programming

The normative definition is:

> C+ generic programming is compile-time specialization performed by compile-time functions operating on semantic compile-time values, especially type values, and normally producing CPX. Generic type, enum, method, or function specialization is therefore not a separate template subsystem but an application of CPX expansion.

---

# 49. Core semantic definition of introspection

The normative definition is:

> Full compile-time type introspection operates on the stabilized C+ type universe and exposes semantic type entities rather than textual type representations. Full introspection SHALL become available only after structural compile-time expansion has reached its fixed point.

---

# 50. Core semantic definition of transcoding

The normative definition is:

> C+ transcoding is a provenance-preserving transformation from the resolved C+ program representation to a C-subset program representation, followed by deterministic C source emission. Transcoding MAY consist of multiple lowering passes, and intermediate results MAY remain valid C+ until all C+-specific constructs have been eliminated.

---

# 51. Required compiler invariants

A conforming implementation SHALL maintain the following invariants.

### After parsing

Every source construct has a source origin.

### After catalogue construction

Every discoverable top-level declaration has a stable semantic identity.

### After structural CPX stabilization

No eligible structural CPX invocation can alter the type/declaration universe.

### After type-universe stabilization

Complete type introspection produces stable results.

### After reflective compile-time evaluation

No unresolved required compile-time operation remains.

### After semantic resolution

Every runtime identifier reference resolves to a semantic symbol or has produced a diagnostic.

### After lowering

No unsupported C+-specific runtime construct remains.

### Before C emission

The entire emitted program belongs to the target C subset.

### During C emission

Generated source ranges are associated with originating C+ source information wherever meaningful.

---

# 52. Final language model

C+ therefore consists of four conceptually separate systems:

```text
1. C-compatible runtime language

2. Compile-time language
   ├── compile-time functions
   ├── type values
   ├── reflection
   └── compile-time control flow

3. CPX template language
   ├── ordinary readable C+ syntax
   ├── lexical compile-time bindings
   ├── typed interpolation
   ├── hygienic generation
   └── recursive expansion

4. C transcoding model
   ├── semantic lowering
   ├── hoisting
   ├── closure transformation
   ├── method transformation
   ├── declaration/header synthesis
   ├── include dependency synthesis
   ├── name generation
   └── source mapping
```

These systems SHALL share the same authoritative symbol, type, scope, and source-origin model.

The C preprocessor MAY still be available for foreign C interoperability, but it is not the semantic foundation of C+ generic or compile-time programming.

The defining distinction is:

> C macros substitute preprocessing tokens.  
> C+ CPX expands semantically bound, readable C+ source templates.

And the defining compilation property is:

> C+ resolves compile-time structure first, stabilizes the type universe, performs semantic and reflective processing, then progressively lowers the resulting program until only valid C remains.
