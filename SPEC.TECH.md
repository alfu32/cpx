# C+ Technical Architecture

## 1. Purpose

This document defines the technical architecture of the C+ compiler and language tooling.

The implementation language is Kotlin targeting the JVM.

The architecture supports:

- parsing C+ source;
- CPX compile-time expansion;
- generic specialization;
- compile-time reflection;
- semantic analysis;
- type resolution;
- source-preserving AST transformations;
- lowering C+ to C;
- header generation;
- forward declaration generation;
- import and include resolution;
- C interoperability;
- source maps;
- compiler diagnostics;
- LSP integration;
- semantic tokens;
- IDE navigation and completion.

The implementation SHALL maintain a single authoritative syntax and semantic model shared by the compiler, transcoder, and language server.

---

# 2. Architectural overview

The compiler is organized as a pipeline over persistent program representations.

```text
source files
    │
    ▼
lexer
    │
    ▼
parser
    │
    ▼
syntax tree
    │
    ▼
AST builder
    │
    ▼
C+ AST
    │
    ▼
declaration catalogue
    │
    ▼
module/import resolver
    │
    ▼
structural CPX engine
    │
    ▼
stable declaration/type universe
    │
    ▼
type + semantic resolver
    │
    ▼
reflective CPX engine
    │
    ▼
resolved C+ AST
    │
    ▼
lowering pipeline
    │
    ▼
C AST
    │
    ├── header synthesis
    ├── include collection
    ├── forward declarations
    └── symbol naming
    │
    ▼
C emitter
   / \
  /   \
C      source map
```

The LSP consumes the same intermediate structures:

```text
                   C+ AST
                     │
                     ▼
               SemanticModel
               /     |      \
              /      |       \
         completion hover   navigation
             │       │         │
         semantic tokens    references
```

---

# 3. Project/module structure

Recommended Gradle project structure:

```text
cplus/
│
├── language-core/
│   ├── lexer
│   ├── parser
│   ├── syntax
│   ├── ast
│   ├── source
│   └── diagnostics
│
├── semantic/
│   ├── symbols
│   ├── scopes
│   ├── types
│   ├── resolution
│   └── reflection
│
├── comptime/
│   ├── evaluator
│   ├── cpx
│   ├── expansion
│   ├── specialization
│   └── dependencies
│
├── compiler/
│   ├── pipeline
│   ├── lowering
│   ├── imports
│   ├── modules
│   └── configuration
│
├── c-backend/
│   ├── cast
│   ├── lowering
│   ├── dependencies
│   ├── headers
│   ├── names
│   ├── emitter
│   └── sourcemap
│
├── c-import/
│   ├── parser
│   ├── preprocessor
│   └── foreign-symbols
│
├── lsp/
│   ├── server
│   ├── completion
│   ├── hover
│   ├── navigation
│   ├── semantic-tokens
│   └── diagnostics
│
├── cli/
│   └── commands
│
└── test-support/
```

Dependencies SHALL flow primarily downward:

```text
language-core
      │
      ▼
semantic
      │
      ▼
comptime
      │
      ▼
compiler
      │
      ▼
c-backend
```

The LSP may depend on:

```text
language-core
semantic
comptime
compiler
```

but SHALL NOT depend on generated C text for semantic behavior.

---

# 4. Source model

## 4.1 SourceFile

Every parsed file is represented as:

```kotlin
data class SourceFile(
    val id: SourceFileId,
    val path: Path,
    val text: String,
    val version: Long
)
```

`SourceFileId` SHALL be stable within a compilation workspace.

## 4.2 SourceRange

```kotlin
data class SourceRange(
    val file: SourceFileId,
    val startOffset: Int,
    val endOffset: Int
)
```

Line and column information SHOULD be computed from a line index rather than stored redundantly on every node.

```kotlin
class LineIndex(
    val lineStarts: IntArray
)
```

## 4.3 Origin

Generated syntax requires richer provenance than a simple range.

```kotlin
sealed interface Origin {

    data class Direct(
        val range: SourceRange
    ) : Origin

    data class Generated(
        val cause: Origin
    ) : Origin

    data class Expansion(
        val definition: Origin,
        val invocation: Origin,
        val parent: Origin?,
        val key: ExpansionKey
    ) : Origin

    data class Synthetic(
        val parent: Origin?
    ) : Origin
}
```

Every syntax/AST node SHALL carry an `Origin`.

---

# 5. Lexer

The lexer converts source text into tokens.

```kotlin
data class Token(
    val kind: TokenKind,
    val range: SourceRange,
    val text: String
)
```

The lexer SHALL recognize:

- identifiers;
- keywords;
- literals;
- comments;
- punctuation;
- operators;
- CPX interpolation delimiters;
- language-specific modifiers.

The lexer SHALL NOT perform name or type resolution.

For incremental IDE use, tokenization SHOULD support relexing a bounded changed region where practical.

---

# 6. Parser

## 6.1 Parser responsibility

The parser SHALL produce structural syntax without attempting full semantic resolution.

It SHALL handle incomplete source sufficiently well for IDE use.

The parser SHALL retain:

- source ranges;
- tokens;
- incomplete nodes;
- recoverable errors.

## 6.2 Grammar

The grammar SHOULD be defined declaratively in EBNF or PEG-like form.

Recommended:

```text
grammar/cplus.ebnf
```

The grammar is the authoritative syntactic specification.

The parser implementation MAY use generated recursive descent or a custom Kotlin parser generated from the grammar.

Expression parsing MAY use Pratt parsing internally.

## 6.3 Parser output

The parser produces a syntax tree.

```kotlin
sealed interface SyntaxNode {
    val range: SourceRange
    val origin: Origin
}
```

The syntax tree SHOULD remain close to source structure.

Example:

```kotlin
data class FunctionDeclSyntax(
    val modifiers: List<Token>,
    val returnType: TypeSyntax,
    val name: Token,
    val parameters: List<ParameterSyntax>,
    val body: BlockSyntax?,
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxNode
```

---

# 7. Syntax tree versus AST

The syntax tree represents source structure.

The AST represents normalized language meaning.

Pipeline:

```text
source
  ↓
syntax tree
  ↓ normalization
AST
```

For example multiple syntactic forms MAY normalize to one AST representation.

The AST SHOULD eliminate irrelevant punctuation and formatting while retaining source origins.

---

# 8. AST architecture

The AST SHOULD use stable node IDs rather than relying exclusively on object identity.

```kotlin
@JvmInline
value class NodeId(val value: Int)
```

Recommended storage:

```kotlin
class AstArena {
    private val nodes = mutableListOf<AstNode>()

    fun add(node: AstNode): NodeId
    fun get(id: NodeId): AstNode
    fun replace(id: NodeId, node: AstNode)
}
```

An arena model provides:

- stable references;
- lower GC pressure;
- efficient mutation;
- simpler source-map tracking;
- simple cross-reference storage.

## 8.1 AST node hierarchy

```kotlin
sealed interface AstNode {
    val origin: Origin
}
```

Principal node families:

```text
Declaration
Statement
Expression
TypeRef
Pattern
ModuleNode
ImportNode
ComptimeNode
```

---

# 9. Declaration model

Declarations SHALL have stable IDs.

```kotlin
@JvmInline
value class SymbolId(val value: Int)
```

Example:

```kotlin
data class FunctionDecl(
    val symbol: SymbolId,
    val parameters: List<Parameter>,
    val returnType: TypeRef,
    val body: NodeId?,
    override val origin: Origin
) : AstNode
```

The AST SHALL reference declarations through semantic identities once resolution has occurred.

---

# 10. Symbol table

Symbols are maintained independently from emitted C names.

```kotlin
data class Symbol(
    val id: SymbolId,
    val sourceName: String,
    val qualifiedName: QualifiedName,
    val kind: SymbolKind,
    val declaration: NodeId,
    val visibility: Visibility,
    val origin: Origin
)
```

Possible kinds:

```kotlin
enum class SymbolKind {
    PACKAGE,
    MODULE,
    TYPE,
    STRUCT,
    ENUM,
    UNION,
    FUNCTION,
    METHOD,
    FIELD,
    VARIABLE,
    PARAMETER,
    COMPTIME_FUNCTION,
    IMPORT,
    FOREIGN
}
```

The symbol registry SHALL be the authoritative source for identity.

The semantic model SHALL expose a source-type catalogue grouped by owning
module. It SHALL retain each type symbol's kind and visibility and provide a
separate public-export view for aliases, structures, unions, and enums. Import
resolution consumes this catalogue; the fact that a module is present in the
compilation graph SHALL NOT itself grant declaration visibility.

---

# 11. Scope model

Scopes SHALL be explicit compiler objects.

```kotlin
class Scope(
    val id: ScopeId,
    val parent: ScopeId?,
    val owner: SymbolId?,
    val kind: ScopeKind
)
```

Scope kinds include:

```text
PACKAGE
MODULE
TYPE
FUNCTION
BLOCK
COMPTIME
CPX_TEMPLATE
```

Each scope maintains symbol bindings:

```kotlin
Map<String, List<SymbolId>>
```

A list is used because several semantic namespaces or overload candidates MAY share a name.

---

# 12. Type system model

Compile-time type objects SHALL be semantic entities.

```kotlin
sealed interface CType
```

Example categories:

```kotlin
data class PrimitiveType(...)
data class PointerType(...)
data class ArrayType(...)
data class StructType(...)
data class UnionType(...)
data class EnumType(...)
data class FunctionType(...)
data class AliasType(...)
data class ForeignType(...)
```

`FunctionType` MAY be wrapped in `PointerType` to represent a callback value.
The C AST has a dedicated function-pointer declarator representation so the
function name is emitted inside `(*name)(...)` rather than appended after a
flattened type string. Indirect calls resolve their callable signature from the
shared semantic expression-type table and reuse the direct-call argument
validator.

Expression validation SHALL distinguish numeric, scalar, object-pointer, array,
and callable-pointer operands for arithmetic and comparisons. Initializer
validation SHALL run for globals as well as block locals before lowering; the C
backend SHALL not be the first component to discover an incompatible object
type.

Each canonical type SHOULD have a stable:

```kotlin
@JvmInline
value class TypeId(val value: Int)
```

Type equality SHALL rely on semantic identity/canonical form rather than textual spelling.

`language-core` SHALL own one canonical C primitive catalog containing
supported spellings, legal specifier-sequence normalization, integer rank and
signedness, floating category and type identity, and numeric category.
Parsing, semantic resolution and identity, CPX type resolution, closure
lowering, ABI layout, reflection, and C lowering SHALL consume this catalog
rather than maintain independent primitive-name tables. Target-dependent
widths (notably `long`) SHALL be selected from the active ABI descriptor. C
standard typedefs such as `size_t` and `ptrdiff_t` remain distinct from
built-in primitives and SHALL resolve through target C header metadata or the
compiler's explicit target model.

Target ABI descriptors SHALL record the floating format, storage size, and
alignment of `float`, `double`, and `long double`; the target driver SHALL
verify these fields against the selected C compiler before linking. Semantic
analysis and C generation SHALL use the target descriptor. Primitive semantic
identity remains distinct even when two formats share a representation.
`long double` is therefore not a parser-only spelling: the semantic type model,
aggregate layout engine, emitted C, and independent caller checks SHALL all
retain its target ABI. If C17 complex support is advertised, the descriptor
and ABI probe SHALL likewise cover the target's complex representations and
calling conventions.

The semantic model SHALL treat imported `size_t` and `ptrdiff_t` as target-sized
integer typedefs, not as the host's `long` spelling. The C backend SHALL retain
their standard C names in emitted typedefs and signatures. `std.core` builds
`usize`/`isize` on those types, allowing one source module to retain the correct
width under both LP64 and LLP64 descriptors.

The SDK's `std.fixed_width` module SHALL define `i8` through `i64` and `u8`
through `u64` as ordinary source typedefs over the corresponding `intN_t` and
`uintN_t` declarations. These aliases SHALL NOT be added to the primitive
catalog or implicitly injected into another module's type environment.

`i128` and `u128` are a separately capability-gated C compiler extension, not
standard C typedefs. Initially, only `sdk/abi/linux-x86_64.toml` advertises
`int128`; its ABI contract is a 16-byte size and alignment and the target's C
function calling convention. Semantic analysis receives target features from
that descriptor, omits the aliases on other targets, and reports `SEM411` for
unavailable direct or imported 128-bit types. Before linking, the selected
GCC/Clang-compatible driver MUST pass a C17 probe for signed/unsigned width and
alignment. The generated-C ABI is covered by an independent C translation-unit
caller test. No Windows or AArch64 support is implied by this initial boundary.

---

# 13. Method model

Methods are declared inside structs or compile-time trait blocks and stored
semantically as callable symbols associated with a canonical receiver type.

```kotlin
data class MethodSymbol(
    val symbol: Symbol,
    val owner: CType,
    val receiverIdentity: ReceiverIdentity,
    val definingModule: String,
    val receiverKind: ReceiverKind,
    val functionType: FunctionType,
    val receiverType: CType
)
```

`ReceiverIdentity` SHALL use canonical type identity rather than the receiver's
spelling. Typedef aliases therefore share the target identity, while nominal
types with the same spelling in different modules remain distinct. Method
lookup SHALL be centralized in an immutable receiver-keyed registry. Native
methods are always available through their type; extension candidates retain
their defining module and are filtered by the caller's visible extension-module
set. Registering an extension SHALL NOT mutate the target type's native method
list. Duplicate native receiver/name entries SHALL be diagnosed instead of
overwritten.

```kotlin
enum class ReceiverKind {
    INSTANCE,
    STATIC
}
```

A method containing `self` in receiver position is `INSTANCE`.

Otherwise it is `STATIC`.

The parser preserves whether the receiver spelling is `self` or `self*`.
`self*` remains an `INSTANCE` method, but its semantic `receiverType` is a
pointer to the owning structure. The method-body scope binds `self` to that
pointer type, so member access resolves through the pointee aggregate and the
backend can emit `self->field`.

---

## 13.1 Compile-time traits

The parser SHALL accept `comptime trait type_identifier { method definitions }`
and the public form `pub comptime trait type_identifier { ... }`. It SHALL
represent the block explicitly as syntax/AST declarations containing shared
method declarations, target spelling, visibility, and origins. It SHALL NOT
represent the block as a new structure or as a compile-time function to execute.
Existing `comptime cpx` parsing SHALL remain unchanged.

Structural CPX expansion SHALL preserve and traverse trait blocks, including
fingerprinting, hygiene, reorigin, and expansion replay. Traits register callable
members of existing types, not new fields or layout descriptors. Registration
and conflict checks SHALL use the stabilized type catalogue and the existing
structural/reflective phase barrier; a late expansion SHALL NOT bypass that
barrier. All AST visitors and body-rewriting passes SHALL traverse the methods.

The method model SHALL support canonical type identity beyond `StructType`,
while preserving module identity for nominal types and canonicalizing typedefs.
An extension retains its defining module separately from its receiver's module.
A module-scoped lookup SHALL combine native methods with visible local/directly
imported extensions under LS §6.3.1. A global mutation of a structure's method
list SHALL NOT make private or unimported extensions visible everywhere.
Same-name methods SHALL NOT disappear through last-write-wins map insertion.

Resolved calls SHALL identify the exact method symbol and receiver adaptation.
Lowering SHALL emit ordinary uniquely named C functions with explicit receiver
arguments, preserving the existing `self` storage semantics and `self*` pointer
semantics. Receiver evaluation occurs once; primitive receivers require typed
storage/dereference lowering rather than a fabricated C struct. No trait object,
vtable, or layout mutation is introduced. Completion, hover, references,
navigation, reflection where applicable, and source maps consume these same
method identities, including imported, aliased, and primitive target types.

# 14. Member-call resolution

Input AST:

```text
MemberCall(
    receiver,
    name,
    arguments
)
```

Resolution converts it into one of:

```text
ResolvedInstanceMethodCall
ResolvedStaticMethodCall
ResolvedFieldCall
Error
```

Example:

```c
v.length()
```

becomes conceptually:

```kotlin
ResolvedInstanceMethodCall(
    method = vectorLengthSymbol,
    receiver = v,
    arguments = emptyList()
)
```

The C lowering phase later generates:

```c
vector_t__length(&v)
```

For an explicit pointer receiver, lowering passes an already-pointer receiver
without adding another address operation:

```c
counter_t__increment(pointer)
```

The C function receives the owner pointer as its first parameter:

```c
void counter_t__increment(struct counter_t* self);
```

---

# 15. Compile-time value model

Compile-time functions SHALL operate on typed values.

```kotlin
sealed interface ComptimeValue
```

Recommended values:

```kotlin
data class CtInt(...)
data class CtFloat(...)
data class CtBoolean(...)
data class CtString(...)
data class CtIdentifier(...)
data class CtType(val type: TypeId)
data class CtExpression(val node: NodeId)
data class CtStatement(val node: NodeId)
data class CtDeclaration(val node: NodeId)
data class CtCpx(val template: CpxTemplate)
data class CtList(val values: List<ComptimeValue>)
```

A compile-time type parameter therefore stores:

```kotlin
CtType(typeId)
```

rather than:

```text
"int"
```

---

# 16. CPX template representation

At the language level, CPX is readable C+ source text.

Internally it SHALL be parsed into a template representation.

```kotlin
data class CpxTemplate(
    val category: CpxCategory,
    val root: TemplateNode,
    val lexicalScope: ScopeId,
    val origin: Origin
)
```

Categories:

```kotlin
enum class CpxCategory {
    UNIT,
    DECLARATION,
    MEMBER,
    STATEMENT,
    EXPRESSION,
    TYPE
}
```

Template fragments SHALL distinguish:

```text
literal C+ syntax
compile-time binding reference
identifier interpolation
nested CPX invocation
```

The template scanner SHALL be lexical-state aware. Direct binding references
are substituted only when they form complete C+ identifier tokens in code;
strings, character literals, and comments are opaque literal fragments.
Explicit identifier interpolation is validated before evaluation. The
implementation SHALL reject non-composable syntax values with a stable
diagnostic instead of relying on a later parse failure.

`ComptimeValue` implementations SHALL preserve both semantic category and
canonical identity. Scalar numeric spellings may normalize for cache keys,
while list values retain recursively typed child values. Syntax-valued
arguments are stored in the AST arena with the invocation origin applied
recursively to every descendant node.

Example source:

```c
struct optional_{T}_t {
    T value;
}
```

might internally resemble:

```text
StructDeclaration
    name:
        IdentifierTemplate(
            Literal("optional_"),
            Binding(T),
            Literal("_t")
        )

    field:
        type = Binding(T)
        name = "value"
```

---

# 17. CPX evaluator

The evaluator executes compile-time functions.

```kotlin
interface ComptimeEvaluator {

    fun evaluate(
        function: SymbolId,
        arguments: List<ComptimeValue>,
        context: ComptimeContext
    ): ComptimeResult
}
```

Context:

```kotlin
data class ComptimeContext(
    val module: ModuleId,
    val scope: ScopeId,
    val containingType: TypeId?,
    val containingFunction: SymbolId?,
    val phase: ComptimePhase,
    val target: TargetInfo,
    val expansion: ExpansionId?
)
```

---

# 18. CPX expansion result

A CPX expansion MAY affect more than its immediate position.

Therefore the result SHALL support multiple output channels.

```kotlin
data class CpxExpansion(
    val replacement: List<NodeId> = emptyList(),
    val hoistedDeclarations: List<NodeId> = emptyList(),
    val localDeclarations: List<NodeId> = emptyList(),
    val beforeStatements: List<NodeId> = emptyList(),
    val afterStatements: List<NodeId> = emptyList(),
    val dependencies: Set<Dependency> = emptySet()
)
```

This supports:

- inner functions;
- closure environments;
- `defer`;
- generated helper functions;
- generated declarations;
- includes/imports.

---

# 19. Expansion identity

```kotlin
data class ExpansionId(
    val declaration: SymbolId,
    val callSite: NodeId,
    val parent: ExpansionId?,
    val key: ExpansionKey
)
```

`ExpansionKey` SHOULD derive from canonical compile-time argument values.

Example:

```text
make_serializer(User)
```

and:

```text
make_serializer(Account)
```

produce different expansion keys.

Expansion IDs SHALL participate in:

- recursion detection;
- generic specialization caches;
- source mapping;
- generated-name stability.

---

# 20. CPX scheduler

Compile-time expansion is dependency-driven.

Recommended abstraction:

```kotlin
class ComptimeScheduler
```

Each invocation is represented as:

```kotlin
data class ComptimeTask(
    val node: NodeId,
    val function: SymbolId,
    val dependencies: Set<ComptimeDependency>,
    val phase: ComptimePhase,
    val state: TaskState
)
```

States:

```text
PENDING
READY
RUNNING
EXPANDED
BLOCKED
FAILED
```

The scheduler repeatedly executes READY tasks.

A task becomes READY once its dependencies are satisfied.

---

# 21. Compile-time phases

```kotlin
enum class ComptimePhase {
    STRUCTURAL,
    REFLECTIVE
}
```

## Structural phase

May generate:

- types;
- structs;
- unions;
- enums;
- aliases;
- function declarations;
- methods;
- generic specializations.

## Reflective phase

May use full introspection.

By default it SHALL NOT change the stabilized structural type universe.

---

# 22. Structural fixed-point engine

The compiler SHALL iterate structural expansion until stabilization.

Pseudo-code:

```kotlin
while (true) {
    val before = structuralFingerprint(program)

    scheduler.runReadyStructuralTasks()

    catalogue.discoverGeneratedDeclarations()
    resolver.resolveNewSymbols()

    val after = structuralFingerprint(program)

    if (before == after && !scheduler.hasReadyStructuralTasks()) {
        break
    }
}
```

The fingerprint SHOULD be based on semantic declaration identity/state rather than emitted source text.

---

# 23. Compile-time cycle detection

The compiler SHALL track the expansion stack.

```text
ExpansionId A
  ↓
ExpansionId B
  ↓
ExpansionId C
  ↓
ExpansionId A
```

shall result in a cycle diagnostic.

The implementation SHOULD also enforce configurable limits on:

- nested expansion depth;
- total generated declarations;
- total generated AST nodes.

These limits protect the compiler from pathological generation.

---

# 24. Type-universe stabilization

When structural expansion reaches its fixed point, the compiler constructs/finalizes:

```text
canonical types
field layouts where available
method sets
enum members
function signatures
alias resolution
```

The resulting model is:

```kotlin
class TypeUniverse
```

Once frozen:

```kotlin
typeUniverse.freeze()
```

structural mutation through reflective CPX SHALL be rejected.

Each `ComptimeContext` exposes an immutable type-universe snapshot and a
structured reflection view. Structural evaluators receive the early-safe name
view; reflective evaluators receive the full descriptor view only after the
stabilization barrier. The reflection view includes aggregate fields and
methods, enum members, alias targets, and layout size/alignment when the ABI
model has established those facts. Semantic `TypeId` values map to the
canonical descriptors used by the active compilation, so a `CtType` argument
can be reflected without falling back to source spelling. It has no mutation
operations. By-value recursive aggregates are reported as unsized until a
future layout strategy can represent their invalid recursive layout explicitly.

---

# 25. Reflection API

The comptime runtime exposes structured reflection.

Example Kotlin-side service:

```kotlin
interface ReflectionService {

    fun nameOf(type: TypeId): String

    fun fieldsOf(type: TypeId): List<FieldInfo>

    fun methodsOf(type: TypeId): List<MethodInfo>

    fun sizeOf(type: TypeId): Long?

    fun alignmentOf(type: TypeId): Long?

    fun kindOf(type: TypeId): TypeKind
}
```

Reflection results SHALL expose semantic IDs.

Example:

```kotlin
data class FieldInfo(
    val symbol: SymbolId,
    val name: String,
    val type: TypeId,
    val origin: Origin
)
```

---

# 26. Imports and modules

## 26.1 Module model

Each source file is represented by:

```kotlin
data class Module(
    val id: ModuleId,
    val source: SourceFileId,
    val packageId: PackageId,
    val imports: List<ImportDecl>,
    val declarations: List<NodeId>
)
```

## 26.2 Package model

```kotlin
data class Package(
    val id: PackageId,
    val name: QualifiedName,
    val modules: MutableList<ModuleId>
)
```

---

# 27. Import resolver

The import resolver processes:

```c
import foo;
import foo as bar;
import { a, b } from foo;
import { add } from ./module_helpers.cp;
import { fs } from stdlib/io;
import { fs as fs1 } from "some/ref.cp";
```

It SHALL distinguish logical package/module targets from relative source-path
targets, resolve source paths relative to the importing source file, and
produce explicit, kind-aware scope bindings for selective aliases. The
resolver SHALL catalog public declarations by kind, including aliases,
structures, unions, enumerations, functions, and values; bind imported types
into the type-name environment; and bind imported values into the value
environment. A selected type SHALL remain usable through its local alias in
all supported type positions. A module import alias SHALL support qualified
lookup of public types and values. Merely loading a module into the compilation
graph SHALL NOT make its declarations implicitly visible in another module.
Missing, private, ambiguous, or conflicting exports SHALL produce diagnostics
without misclassifying a type as a missing function.

The semantic model SHALL retain module-local selective type bindings and the
target module for each module alias. The C lowerer SHALL translate imported
type spellings to the owning declaration's C name, not the importing alias.
Generated translation units SHALL order typedef aliases before aggregates
that use them, order alias dependencies, and emit required aggregate tag
forward declarations before aliases that refer to those tags.

```kotlin
class ImportResolver
```

The module graph SHOULD be represented as:

```kotlin
class ModuleGraph(
    val dependencies: Map<ModuleId, Set<ModuleId>>
)
```

Cycles SHALL be analyzed before compile-time evaluation requiring imported generated symbols.

## 27.1 Compile-time import binding and workspace expansion

The pre-expansion catalogue SHALL retain public/private compile-time
declarations, their definition syntax, defining module/scope, signature and
origin independently of runtime AST erasure. Selective and qualified bindings
SHALL refer to a canonical declaration identity, not a copied definition under
the import alias. The catalogue SHALL remain available to final import
validation, the import index and the language server.

Compiler orchestration SHALL separate parsing/cataloguing from expansion:

```text
discover source closure -> parse modules -> catalogue and bind imports
 -> schedule structural CPX across modules -> repeat discovery/cataloguing
    for newly generated declarations/imports until stable
 -> close type universe -> reflective work -> final semantics -> lowering
```

Use the shared source resolver, semantic catalogue and scheduler. The compiler
owns filesystem discovery; the comptime module consumes declaration/binding
records and requests dependency work without recursively loading files itself.
Module identity SHALL distinguish resolved provider paths, including providers
with the same basename, while retaining package/import spellings separately.
Every entry path (disk, incremental, text workspace and LSP overlays) SHALL
use the same ordering. Per-file expansion before imports bind is insufficient.

The expansion context SHALL distinguish definition environment, argument
environment and insertion scope. Expansion/specialization caches SHALL include
canonical callable identity, definition/dependency fingerprints, semantic
arguments, target/profile and any relevant scope identity. Aliases share
evaluation identity; separate insertion sites retain their own origins and
hygiene identities. Identically named functions in unrelated modules SHALL
NOT share cache entries. Replaying cached syntax SHALL reconstruct the current
invocation provenance. Incremental invalidation SHALL follow compile-time
dependency edges, including private helpers and generated imports.

Preconditions: participating source snapshots are registered, declarations
have stable identities, and imports bind or have explicit pending/error state.
Postconditions: no ready structural task remains, generated client declarations
are catalogued before stabilization, and erased comptime exports still validate.
Invalid states: unresolved dependency cycles, private/missing/ambiguous imports,
binding collisions and reflective structural mutation. These produce bounded,
origin-aware diagnostics; unresolved imports SHALL NOT cascade into a second
misleading unknown-CPX error for the same failed binding.

---

# 28. C import architecture

C headers and C sources are represented through a foreign-declaration subsystem.

```text
C header
   ↓
C declaration parser
   ↓
Foreign AST
   ↓
ForeignSymbolAdapter
   ↓
C+ symbol/type universe
```

Principal component:

```kotlin
class CImportService
```

Each foreign typedef record SHALL contain its external spelling and, when the
catalogue declares it, the resolved underlying C+ type. This distinction is
required because a typedef such as `size_t` must be emitted as `size_t` while
its layout follows the selected target's `unsigned long` definition. Opaque
foreign types such as `FILE` MAY omit an underlying type and SHALL use the
target pointer-sized fallback only for operations explicitly defined as opaque.

Responsibilities:

- parse C declarations;
- resolve C typedefs;
- expose foreign functions;
- expose foreign types;
- expose enum constants;
- register required include directives;
- preserve external linkage names.

The syntax/AST type record SHALL keep base qualifiers and qualifiers attached to
each pointer declarator separately. Backend type records SHALL preserve that
placement when rendering C. Semantic compatibility and ABI layout SHALL ignore
qualifier-only differences while retaining them for diagnostics and emission.

The adapter SHALL resolve `c.*` providers to header paths in the effective
include environment and derive declarations from those files, as specified in
LS §22.1. A hand-maintained function catalogue is not an authoritative source.
Compiler-owned intrinsic/ABI type knowledge remains separate from discovery
of library symbols; intrinsic handling SHALL NOT fabricate library functions.

The compiler orchestration layer SHALL construct an immutable per-request
header environment from the selected SDK, target/ABI/profile, compiler driver,
include roots, and external sysroot. The same environment SHALL reach full,
incremental, text-workspace, and provisional semantic analysis. Filesystem
lookup and external preprocessing belong in compiler services; the semantic
module consumes declaration records and SHALL NOT depend on the compiler.

Preprocessing SHALL use an adapter for the selected C driver, with the same
target defines and include policy as C compilation. It SHALL retain source
locations and transitive include dependencies, bound process duration/output,
and reject unsupported driver/target combinations explicitly. It SHALL NOT
concatenate conditional branches, invoke a shell with header-supplied text,
or silently search host headers in a self-hosted profile. Unsupported macros
remain opaque dependencies or receive reference diagnostics under LS §22.3;
only safely typed constants/intrinsics may enter the callable/value index.

Foreign declaration records SHALL retain original path/range, provider include,
linkage, qualifiers, declarator structure, and typedef dependencies. Only
top-level declarations are exported; bodies are skipped structurally without
indexing their local variables. Headers remain includes, and C source units
remain separately owned build inputs, preventing duplicate emitted definitions.

Foreign declarations SHALL never be renamed at the ABI boundary.

---

# 29. Semantic model

The language server and compiler query a shared semantic model.

```kotlin
class SemanticModel(
    val symbols: SymbolTable,
    val scopes: ScopeTable,
    val types: TypeUniverse,
    val references: ReferenceIndex,
    val modules: ModuleGraph
)
```

It SHALL answer:

```text
symbol at position
type of expression
declaration of reference
references to declaration
members of type
available symbols in scope
call resolution
generic specialization
origin of generated symbol
```

---

# 30. Reference index

A global reference index supports IDE navigation.

```kotlin
data class SymbolReference(
    val symbol: SymbolId,
    val node: NodeId,
    val origin: Origin,
    val kind: ReferenceKind
)
```

```kotlin
class ReferenceIndex {
    fun referencesTo(symbol: SymbolId): List<SymbolReference>
}
```

This SHALL include references generated through CPX where appropriate.

---

# 31. Lowering framework

All transformations SHOULD implement a common pass interface.

```kotlin
interface CompilerPass {
    val name: String

    fun run(
        program: Program,
        context: CompilerContext
    ): PassResult
}
```

Recommended stages:

```text
CpxExpansionPass
MethodResolutionPass
MethodLoweringPass
ClosureAnalysisPass
ClosureLoweringPass
DeferLoweringPass
TemplateStringLoweringPass
OwnershipLoweringPass
PackageNameLoweringPass
GenericCleanupPass
HoistingPass
ForwardDeclarationPass
DependencyCollectionPass
CSubsetValidationPass
```

---

# 32. AST rewrite framework

AST transformations SHOULD operate through a controlled rewrite API.

```kotlin
interface AstRewriter {

    fun replace(
        old: NodeId,
        new: NodeId
    )

    fun insertBefore(
        anchor: NodeId,
        node: NodeId
    )

    fun insertAfter(
        anchor: NodeId,
        node: NodeId
    )

    fun hoist(
        targetScope: ScopeId,
        node: NodeId
    )
}
```

Every generated node SHALL receive an origin derived from the transformation input.

---

# 33. Method lowering

Given:

```c
struct foo_t {
    int get(self) {
        return self.value;
    }
}
```

semantic representation:

```text
Struct(foo_t)
  Method(get, INSTANCE)
```

lowered representation:

```text
Struct(foo_t)
Function(foo_t__get)
```

Member access:

```c
self.value
```

lowers according to receiver representation:

```c
self->value
```

The method symbol SHALL retain its original semantic identity even if represented as a top-level C function.

For:

```c
struct counter_t {
    int value;

    void increment(self*) {
        self->value = self->value + 1;
    }
};
```

the semantic method descriptor records `receiver = INSTANCE` and
`receiverType = PointerType(counter_t)`. The lowering pass emits a pointer
receiver parameter and retains pointer-member access:

```c
void counter_t__increment(struct counter_t* self) {
    self->value = self->value + 1;
}
```

Parser lookahead must also keep expressions such as `pointer.increment();`
in the expression-statement path; they must not be misclassified as local
function declarations.

---

# 34. Inner-function lowering

The compiler performs capture analysis:

```kotlin
data class Capture(
    val symbol: SymbolId,
    val mode: CaptureMode
)
```

Possible modes:

```text
REFERENCE
VALUE
MOVE
```

A closure transformation generates:

```text
environment struct
hoisted function
environment instance
rewritten invocation
```

All generated artifacts SHALL originate from the inner function declaration.

---

# 35. `defer` lowering

Each lexical block SHALL track registered deferred actions.

```kotlin
data class DeferredAction(
    val statement: NodeId,
    val origin: Origin
)
```

During control-flow rewriting, cleanup is inserted on every applicable exit edge.

The pass SHOULD operate over a control-flow representation rather than performing naïve textual rewriting.

---

# 36. Control-flow graph

Functions SHOULD optionally construct a CFG for analyses requiring control-flow awareness.

```kotlin
class ControlFlowGraph(
    val blocks: List<BasicBlock>
)
```

Uses include:

- defer insertion;
- return analysis;
- unreachable-code diagnostics;
- definite initialization;
- ownership/lifetime checks.

---

# 37. C AST

The backend SHALL use a dedicated C representation rather than directly emitting C text from arbitrary C+ nodes.

```kotlin
sealed interface CNode {
    val origin: Origin
}
```

Examples:

```text
CTranslationUnit
CStructDecl
CFunctionDecl
CFunctionDef
CVariableDecl
CExpression
CStatement
CType
```

This becomes the boundary:

```text
C+ semantics
    ↓
C lowering
    ↓
C AST
    ↓
text emission
```

---

# 38. Header synthesis

Header synthesis operates on C AST declarations and C+ visibility metadata.

```kotlin
class HeaderGenerator
```

It SHALL derive:

- exported type declarations;
- function prototypes;
- forward declarations;
- required public includes.

It SHALL NOT copy arbitrary source text.

---

# 39. Forward-declaration synthesis

A dependency graph between C declarations SHALL determine which forward declarations are required.

Example graph:

```text
foo_t
 └─ references bar_t*

bar_t
 └─ references foo_t*
```

The backend can generate:

```c
typedef struct foo_t foo_t;
typedef struct bar_t bar_t;
```

before concrete definitions.

---

# 40. Dependency collector

Backend requirements are accumulated through:

```kotlin
sealed interface Dependency
```

Examples:

```kotlin
data class SystemInclude(val name: String) : Dependency
data class LocalInclude(val path: String) : Dependency
data class ForeignLibrary(val name: String) : Dependency
```

The collector SHALL deduplicate and deterministically order dependencies.

---

# 41. C symbol naming

C symbol names SHALL be produced centrally.

```kotlin
interface CNameMangler {
    fun nameOf(symbol: SymbolId): String
}
```

Possible mapping:

```text
collections.list_t.push
    ↓
collections__list_t__push
```

Generated helper symbols SHALL include hygienic expansion identifiers where necessary.

Example:

```text
__cplus__closure__42
```

Naming MUST be deterministic.

---

# 42. C emitter

```kotlin
class CEmitter
```

The emitter walks the C AST.

It writes:

```text
generated C source
+
source mappings
```

simultaneously.

Example API:

```kotlin
fun emit(
    unit: CTranslationUnit,
    writer: CodeWriter,
    sourceMap: SourceMapBuilder
)
```

---

# 43. Source-map builder

The writer SHALL track generated offsets.

```kotlin
data class GeneratedRange(
    val start: Int,
    val end: Int
)
```

Mapping:

```kotlin
data class SourceMapping(
    val generated: GeneratedRange,
    val origin: Origin
)
```

Nested origin chains SHALL be retained.

This allows:

```text
generated.c:120
    ↓
optional_int_t__get
    ↓
generated by optional(int)
    ↓
defined in optional<T>
```

---

# 44. External C compiler diagnostics

If GCC, Clang, TCC, or another compiler reports:

```text
generated.c:123:17
```

the C+ toolchain SHALL attempt to map the location back through the generated source map.

The diagnostic adapter SHALL expose:

```text
primary originating C+ location
CPX invocation location
CPX definition location where relevant
generated C location optionally
```

The adapter MUST accept both colon-delimited GCC/Clang diagnostics and
parenthesized MSVC diagnostics, including Windows backslash paths. A diagnostic
that does not map to a generated range SHALL remain visible with its generated
location and a distinct unmapped status; it MUST NOT be discarded.

---

# 45. Compiler context

Shared pipeline state:

```kotlin
class CompilerContext(
    val sources: SourceRepository,
    val ast: AstArena,
    val symbols: SymbolTable,
    val scopes: ScopeTable,
    val types: TypeUniverse,
    val diagnostics: DiagnosticCollector,
    val modules: ModuleGraph,
    val comptime: ComptimeEngine,
    val target: TargetInfo
)
```

Passes SHALL receive this context rather than use process-global mutable state.

---

# 46. Diagnostic system

```kotlin
data class Diagnostic(
    val severity: Severity,
    val message: String,
    val origin: Origin,
    val related: List<RelatedDiagnostic>
)
```

Diagnostics SHALL support multiple locations.

For example:

```text
generated declaration conflicts with existing declaration
    generated here
    from CPX invocation here
    CPX defined here
    previous declaration here
```

---

# 47. Incremental compilation model

The architecture SHOULD support incremental compilation.

Each source file has a version.

Changes invalidate:

```text
syntax tree
AST nodes originating from changed ranges
symbols owned by changed declarations
dependent CPX instances
dependent specializations
semantic references
affected C output
```

Dependency tracking SHOULD be explicit.

---

# 48. Compile-time dependency graph

Each CPX instance records dependencies such as:

```text
type dependency
symbol dependency
module dependency
other CPX instance
target property
reflection barrier
```

Example:

```kotlin
sealed interface ComptimeDependency {

    data class Symbol(
        val symbol: SymbolId
    ) : ComptimeDependency

    data class Type(
        val type: TypeId
    ) : ComptimeDependency

    data class Expansion(
        val expansion: ExpansionId
    ) : ComptimeDependency

    data object StableTypeUniverse : ComptimeDependency
}
```

This graph drives both compilation ordering and invalidation.

---

# 49. Generic specialization cache

Generic CPX expansions SHOULD be cached.

Key:

```kotlin
data class SpecializationKey(
    val declaration: SymbolId,
    val arguments: List<CanonicalComptimeValue>
)
```

Cache:

```kotlin
class SpecializationCache
```

Cache entries SHALL retain the complete evaluation result required by the
scheduler, including diagnostics and published dependency channels, rather
than rendered text alone. Definition fingerprints SHALL be independent of the
source path and SHALL canonicalize template token spelling so formatting-only
edits do not create distinct semantic definitions. Explicit incremental
invalidation removes entries for the changed specialization dependency
closure.

Equivalent calls:

```c
Optional(int)
Optional(int)
```

reuse one specialization where semantics allow.

---

# 50. LSP architecture

The language server SHALL run over the compiler front-end.

Recommended implementation:

```text
cplus-lsp
    │
    ├── workspace manager
    ├── document manager
    ├── parser service
    ├── semantic service
    ├── completion service
    ├── navigation service
    ├── diagnostics service
    └── semantic token service
```

The LSP SHALL NOT invoke full C emission merely to answer editor queries.

---

# 51. Workspace model

```kotlin
class Workspace(
    val packages: PackageRegistry,
    val modules: ModuleGraph,
    val documents: DocumentStore,
    val compiler: IncrementalCompiler
)
```

Each edited document carries an independent version.

Semantic analysis SHOULD operate asynchronously from lexical highlighting.

The server SHALL discover imports from parsed import declarations and compile
the transitive source closure with the selected document. Open documents in the
same connected import component SHALL participate using their in-memory text;
unrelated open documents SHALL remain isolated. Compiler source identities
SHALL map diagnostics and navigation ranges back to the owning document URI and
that document's current text. Diagnostics SHALL be republished to affected open
documents when a connected document changes or closes. Semantic tokens, hover,
completion, navigation, and signature help SHALL select the artifact for the
requested document. The server SHALL provide document symbols and cross-file
references; rename edits SHALL target exact identifier ranges and only C+
sources in the compiled workspace.

The server SHALL discover imports from parsed import declarations and compile
the transitive source closure with the selected document. Open documents in the
same connected import component SHALL participate using their in-memory text;
unrelated open documents SHALL remain isolated. Compiler source identities
SHALL map diagnostics and navigation ranges back to the owning document URI and
that document's current text. Diagnostics SHALL be republished to affected open
documents when a connected document changes or closes. Semantic tokens, hover,
completion, navigation, and signature help SHALL select the artifact for the
requested document. The server SHALL provide document symbols and cross-file
references; rename edits SHALL target exact identifier ranges and only C+
sources in the compiled workspace.

---

# 52. Semantic tokens

Semantic token generation reads resolved symbols.

Examples:

```text
struct declaration    → STRUCT
method declaration    → METHOD
field                 → PROPERTY
parameter             → PARAMETER
comptime parameter    → dedicated modifier
type reference        → TYPE
generated symbol      → optional GENERATED modifier
```

TextMate provides baseline lexical colors.

LSP semantic tokens refine them.

---

# 53. Navigation

`go to definition` SHALL operate on `SymbolId`.

Generated symbols SHOULD expose:

```text
logical declaration
generated specialization
CPX invocation
CPX definition
```

where appropriate.

The IDE MAY expose generated C separately but SHALL not require navigation through emitted C.

---

# 54. Completion

Completion inputs:

```text
current syntax node
current scope
expected syntactic category
resolved receiver type
module imports
compile-time phase where relevant
```

Examples:

```text
instance.
    → fields + instance methods

Type.
    → static methods + static members

package.
    → exported members

inside CPX template
    → compile-time bindings + normal visible symbols
```

---

## 54.1 Discoverable imports

The compiler SHALL discover C declarations from the selected SDK headers and
configured include/source paths. A hand-maintained function-name catalogue
SHALL NOT determine whether a header function exists. Binary libraries supply
link symbols but do not supply callable type signatures without corresponding
headers or source declarations.

A shared import index SHALL expose public C+ module exports, supported C header
declarations, their signatures, and import references. The LSP SHALL use it for
module-path and imported-name completion, suggestions for unimported symbols,
and quick fixes that insert the necessary import. Existing imports and aliases
SHALL be respected; competing providers SHALL be offered as separate choices.
Index contents SHALL reflect source changes, including unsaved C+ documents.
SDK `std.*` imports SHALL resolve identically in CLI and LSP compilation.

The compiler module SHALL own shared project/module discovery and the immutable
export index; neither the VS Code extension nor a second LSP parser owns import
semantics. The resolver SHALL use project roots, workspace folders, the selected
SDK, relative importer paths, and live document overlays, preserving canonical
module identity and terminating cyclic graphs. Indexing an export SHALL NOT
implicitly import it or compile unrelated entry-point files together.

Index/cache identity SHALL include SDK and compiler selection, target ABI and
profile, ordered search roots, source content/overlay versions, and transitive
header dependencies. Header or configuration changes SHALL invalidate compiler
results as well as editor suggestions. Workspace scans SHALL be bounded,
exclude generated/VCS directories, and support request cancellation. Missing
C preprocessing support SHALL leave C+ module assistance available, with an
explicit C discovery diagnostic instead of guessed or stale declarations.

A shared import-edit builder SHALL serve completion and code actions. It SHALL
use source ranges from shared syntax/lexing, avoid overlapping edits, respect
UTF-16 LSP positions and current document versions, and preserve existing aliases.
Code actions SHALL be connected to compatible unresolved-symbol diagnostics;
they SHALL NOT suggest imports for arbitrary parse errors, strings, comments,
or missing receiver members. Header navigation SHALL use the header's URI and
original range, not a range projected into the requesting C+ document.

# 55. Hover

Hover information SHOULD include:

```text
source spelling
canonical type
qualified symbol name
method owner
instance/static classification
generic specialization arguments
generated-via CPX information where applicable
```

---

# 56. C/C+ imported symbol tooling

C-imported symbols SHALL appear in:

- completion;
- hover;
- go-to-definition where source/header is available;
- signature help;
- reference resolution where possible.

The semantic model SHALL mark them as `FOREIGN`.

---

# 57. CLI architecture

The CLI is a thin orchestration layer.

Commands may include:

```text
cplus build
cplus transcode
cplus check
cplus run
cplus format
cplus lsp
cplus ast
cplus expand
```

Useful compiler inspection commands SHOULD include:

```text
cplus ast file.cp
cplus expand file.cp
cplus emit-c file.cp
```

`expand` SHOULD display the post-CPX/pre-lowering C+ representation.

Project builds MAY use a `cplus.toml` manifest with `[project]`, an `entry`
source path, and optional `source_roots`. Multi-module workspaces MAY use a
`cplus.workspace.toml` manifest with `[workspace]`, an `entry`, and optional
`members` directories. Manifest-relative paths SHALL be normalized against the
manifest directory. CLI source commands SHALL share one source-discovery
model: explicitly imported path modules resolve relative to their importer,
project modules resolve within declared roots, and explicit `std.*` imports
resolve from the selected SDK's source tree. `check`, `transcode`, `build`,
and `run` SHALL consume the same discovered source set.

CLI filesystem arguments (`--output`, `--header`, `--map`, `--sdk`, `--sysroot`,
`--c-source`, and `--include-dir`) SHALL be normalized against the invocation
working directory; manifest-owned paths use the manifest directory. Target
triples SHALL be canonicalized before SDK selection. Compiler values SHALL
remain executable names unless they are paths, and library values SHALL remain
link names unless path-shaped. `--map` SHALL emit deterministic generated-C
and source byte ranges with source paths relative to the project root (or the
invocation directory for standalone sources). `run --output` SHALL retain the
native executable at that path; without it, the executable is temporary.
SDK inspection commands SHALL accept an explicit `--sdk` manifest and select
target artifacts using a canonical `--target` triple. `sdk doctor` SHALL verify
the manifest, resolved layout, ABI descriptor, intrinsic catalogue, build
profile, selected runtime link plan, and semantic metadata used by compilation.
`runtime inspect` SHALL report the selected `RuntimeLinker` plan (startup and
runtime sources plus compiler/linker flags), not a directory inventory.
`target`, `abi`, `libc`, and `audit` inspection SHALL resolve descriptors,
headers, and binary format rules from that same SDK root. `libc test` SHALL run
the SDK conformance fixtures for the requested target.

The CLI SHALL remove its temporary `run` product directory after build failure
or process completion and SHALL return the child process exit status. Process-
start failures SHALL be reported as CLI errors. The distributable CLI package
SHALL include the CLI launcher, required JVM artifacts, and the source SDK as
one installable product. The CLI SHALL discover that adjacent SDK by default;
`-Dcplus.sdk.manifest=<path>` SHALL override the bundled SDK for development
and alternate SDK testing. CLI help SHALL identify this JVM option and state
that it precedes `-jar`. The fat JAR and distribution archives SHALL use
reproducible entry ordering and timestamps.

## 57.1 Source test command and report

`cplus test file.cp [other.cp ...]` SHALL compile and run LS §53 fixtures.
`c+ test` uses the same installed launcher behavior. At least one explicit
root file is required; shell-expanded globs supply ordinary file arguments.
The CLI does not expand quoted wildcard patterns. Preserve argument order and
deduplicate normalized absolute root paths, keeping the first occurrence.
Compile each root with its own directed import closure, so independent roots
may each declare `main`. Imported fixtures are not implicitly selected.

Reuse SDK discovery and `--sdk`, `--target`, `--runtime`, `--libc`,
`--c-compiler`, `--sysroot`, `--include-dir`, `--c-source` and `--library`
configuration. `--project`/`--workspace` MAY supply configuration and source
roots, but SHALL NOT silently add a test root. Resolve explicit files against
the invocation directory. `--` ends option parsing. Test products are managed
temporary artifacts; output/header/map options are rejected in this command.
No implicit directory scan, fixture filter, watch mode or parallel execution
is required. A non-runnable target/profile SHALL produce an explicit error.

Execute roots and fixtures sequentially. `--timeout <seconds>` SHALL accept
a positive integer per-fixture limit, defaulting to 30 seconds. A timeout
terminates that child, records an error and proceeds to the next fixture.
The CLI SHALL clean its temporary products after success, failure or
cancellation and never use a shell to invoke compilers or test products.

The standard text report SHALL use these labels (counts are assertion counts):

```text
::: [1/2] tests/box.cp
... [1/2] generated box stores a value
<ordinary fixture stdout, unchanged>
---- value is nonzero ----------------
---- expression: item.value
---- value: 42
---- SUCCESS
---- stored value ----------------
---- expected expression: 42
---- expected value: 42
---- evaluated expression: item.value
---- evaluated value: 42
---- SUCCESS
... asserts passed 2 / failed 0 / total 2; errors 0
<next fixture header, output and footer>
::: asserts passed 2 / failed 1 / total 3; errors 0
<next file header, fixtures and footer>
::: final report
::: tests/box.cp: passed 2 / failed 1 / total 3; errors 0
::: tests/other.cp: passed 1 / failed 0 / total 1; errors 0
::: total: passed 3 / failed 1 / total 4; errors 0
```

`FAIL` replaces `SUCCESS` for a false assertion. Generated descriptions cover
both description-free forms. Print headers before user code and assertion
reports at the point of execution; fixture footers follow process completion.
Preserve stdout and stderr on their respective streams. Cross-stream ordering
is not promised. Add a separating newline where user output has no terminating
newline. Do not prefix, discard, or parse arbitrary user output as test results.
Reporter labels are fixed; paths, descriptions, source text and values are data.

File and final reports SHALL include all requested roots, even files which
failed to compile. A file with no fixtures is explicitly `NO TESTS`, counts
0/0/0; a zero-assertion fixture is `EMPTY`. Neither invents successes. Build or
fixture execution errors are counted separately, once per failing file build
or fixture execution, with their diagnostic reason. They do not increase the
assertion failure total. Partial valid assertion events before a crash remain
counted. Report assertion totals from actual execution, not static syntax.

Exit status: 0 only when all roots completed with no failed assertion or
execution/build error; 1 for any such test/build failure; 2 for invalid CLI
arguments or runner setup failure. User interruption is nonzero, stops further
execution and marks the run incomplete. Zero discovered fixtures is explicitly
reported but is not itself failure. Help SHALL document all four assertion
forms, file selection, timeout, equality and exit behavior.

---

# 58. Compiler pipeline API

Recommended top-level API:

```kotlin
class CPlusCompiler {

    fun compile(
        request: CompileRequest
    ): CompileResult
}
```

```kotlin
data class CompileRequest(
    val sources: List<Path>,
    val target: TargetInfo,
    val options: CompilerOptions,
    val sdkManifest: Path,
    val externalSysroot: Path?
)
```

Result:

```kotlin
data class CompileResult(
    val diagnostics: List<Diagnostic>,
    val generatedUnits: List<GeneratedCUnit>,
    val semanticModel: SemanticModel?,
    val sdkResolution: SdkResolution?
)
```

Before parsing source-dependent phases, the compiler loads the selected SDK
manifest and resolves a source-first `SdkLayout` for the target triple. The
resolver supplies standard-library, libc, runtime, platform, ABI, intrinsic,
and startup roots to the request. An external sysroot is an explicit separate
input; failure to resolve an SDK component is diagnostic and never silently
falls back to host headers or libraries.

The selected runtime/libc profile is part of `TargetInfo`, is included in
incremental cache identity, and is copied into `ComptimeTargetInfo` for target
selection. The repository SDK provides source layouts for Linux and Windows
on x86_64 and AArch64, with C17 as the claimed hosted profile. A target build
resolves its SDK sources and target descriptor directly; it does not select
glibc, musl, MSVCRT, UCRT, or MinGW as a C+ standard-library implementation.

The SDK metadata cache is a deterministic, versioned artifact under the SDK
cache root. It stores source-relative paths, content hashes, declarations,
exports, documentation comments, and CPX signatures. The compiler verifies
schema, manifest identity, target identity, and all source hashes before reuse;
otherwise it rebuilds the metadata from source. Metadata accelerates tooling
and inspection but never replaces source as the authority for compilation.

For `runtime=cplus` and `runtime=freestanding`, `RuntimeLinker` resolves the
target startup adapter, uniform PAL adapter, and compiler-support runtime from
the SDK. Linux startup enters `_start` and reaches the kernel only through the
Linux PAL; Windows startup enters `mainCRTStartup` and reaches documented
system DLL imports only through the Windows PAL. Both paths call
`__cplus_start`, initialize runtime/TLS/allocator state hooks, dispatch the
supported application `main` form, and terminate through the same PAL ABI.
`runtime=system` deliberately leaves startup and default-library selection to
the downstream toolchain.

Termination is represented as separate runtime operations: normal termination
drains the normal handler stack, quick termination drains only the quick stack,
immediate termination skips cleanup, and abort produces the platform abort
status. Stream flushing is attached by the libc/stdio layer rather than being
silently performed by the startup adapter.

`TargetRegistry` loads the target ABI descriptor selected by the SDK layout.
The descriptor is data containing target triple, OS, architecture, vendor,
ABI, object format, endianness, widths, integer model, alignment, symbol and
TLS rules, linker/startup identity, system libraries, features, intrinsics,
and supported ABI classes. `ComptimeTargetInfo` exposes these values through
structured capability predicates; compiler logic does not duplicate target
facts in platform-specific branches.

---

# 59. Pipeline orchestration

Conceptual implementation:

```kotlin
fun compile(): CompileResult {

    parseSources()

    buildDeclarationCatalogue()

    resolveModulesAndImports()

    runStructuralComptime()

    stabilizeTypes()

    resolveTypesAndSymbols()

    runReflectiveComptime()

    performSemanticAnalysis()

    lowerMethods()

    lowerClosures()

    lowerDefer()

    lowerRemainingCPlusFeatures()

    hoistDeclarations()

    createCRepresentation()

    generateForwardDeclarations()

    collectDependencies()

    assignCNames()

    validateCSubset()

    emitC()

    return result()
}
```

Each phase SHALL have explicit preconditions and postconditions.

---

# 60. Phase invariants

## Parse complete

```text
all syntax represented
all nodes have origin
recoverable syntax errors recorded
```

## Catalogue complete

```text
all discoverable declarations registered
stable declaration IDs assigned
```

## Structural CPX complete

```text
no READY structural CPX task remains
type/declaration universe stable
```

## Semantic resolution complete

```text
all required names resolved
all expressions typed
all method calls classified
```

## Reflective CPX complete

```text
no required reflective CPX invocation remains
```

## C+ lowering complete

```text
no C+-only runtime constructs remain
```

## C backend validation complete

```text
all emitted nodes belong to selected C dialect
```

---

# 61. Concurrency

Parsing independent source files MAY occur concurrently.

Semantic operations MAY also run concurrently where dependency graphs prove independence.

Shared mutable tables such as:

```text
SymbolTable
TypeUniverse
AstArena
SpecializationCache
```

SHOULD use compiler-controlled synchronization or phase-local mutation.

Compile-time execution SHOULD remain deterministic regardless of scheduler concurrency.

---

# 62. Caching

Recommended caches:

```text
parsed source cache
AST cache
module catalogue cache
C import cache
generic specialization cache
CPX evaluation cache
reflection cache
generated C unit cache
```

Cache keys MUST include all semantically relevant dependencies.

---

# 63. Testing architecture

Tests SHALL exist at multiple levels.

## Grammar tests

```text
source → syntax
```

## AST tests

```text
syntax → normalized AST
```

## CPX tests

```text
input AST → expanded AST
```

## Semantic tests

```text
AST → symbols/types
```

## Lowering tests

```text
resolved C+ AST → C AST
```

## Snapshot tests

```text
C+ source → expected generated C
```

## Source-map tests

```text
generated C range → expected C+ origin
```

## LSP tests

```text
position → completion/navigation/semantic token
```

---

# 64. Golden compiler tests

A representative compiler test SHALL contain:

```text
input.cp
expected.expanded.cp
expected.c
expected.h
expected.map
expected.diagnostics
```

This is particularly valuable for CPX and lowering.

---

# 65. Error recovery strategy

The parser SHALL preserve partial syntax wherever possible.

Example:

```c
foo.bar(
```

should still produce a call-like syntax node marked incomplete.

The semantic model MAY resolve:

```text
foo
bar
receiver type
method candidates
```

even though `)` is missing.

This is necessary for editor completion.

---

# 66. Internal immutability strategy

Semantic identity objects SHOULD be immutable.

Mutable transformation state SHOULD reside primarily in:

```text
AstArena
ScopeTable
SymbolTable builder
pipeline state
```

After a major phase such as type stabilization, corresponding models SHOULD become read-only.

This makes fixed-point behavior and incremental invalidation easier to reason about.

---

# 67. Kotlin-specific implementation conventions

Prefer:

```text
sealed interfaces
data classes for immutable value objects
@JvmInline value classes for IDs
IntArray / arrays for hot indexed structures
explicit arenas for AST nodes
```

Avoid excessive inheritance-heavy object graphs.

Use semantic IDs rather than Kotlin object references for long-lived compiler relationships.

Examples:

```kotlin
@JvmInline value class NodeId(val value: Int)
@JvmInline value class SymbolId(val value: Int)
@JvmInline value class TypeId(val value: Int)
@JvmInline value class ScopeId(val value: Int)
```

---

# 68. Recommended hot-path representation

For large projects, portions of the AST and indexes SHOULD use compact array-backed structures.

Example:

```kotlin
class NodeTable(
    val kind: IntArray,
    val parent: IntArray,
    val firstChild: IntArray,
    val childCount: IntArray,
    val origin: IntArray
)
```

Higher-level Kotlin AST wrappers MAY expose ergonomic APIs over these storage tables.

This allows compiler ergonomics without forcing every syntax node to become an independently allocated JVM object.

---

# 69. Separation of concerns

The following boundaries SHALL be maintained.

Parser:

```text
understands syntax
does not decide final semantic meaning
```

Semantic resolver:

```text
understands symbols and types
does not emit C
```

CPX engine:

```text
evaluates compile-time transformations
does not directly format final C
```

Lowering:

```text
transforms semantics
does not perform raw textual substitution
```

C backend:

```text
understands C representation
does not reinterpret C+ semantics
```

Emitter:

```text
formats C AST
does not perform semantic resolution
```

LSP:

```text
queries compiler models
does not maintain a competing language model
```

---

# 70. Authoritative compiler representations

The implementation SHALL recognize the following principal representations:

```text
SourceText
    ↓
TokenStream
    ↓
SyntaxTree
    ↓
CPlusAst
    ↓
ResolvedCPlusAst
    ↓
CAst
    ↓
GeneratedC
```

CPX operates primarily between:

```text
CPlusAst
    ↕
CPX templates
```

The semantic model spans:

```text
CPlusAst
ResolvedCPlusAst
```

Source origins span every representation.

---

# 71. Architectural rule for CPX

The most important implementation rule is:

> CPX is textual at the language surface, structured internally, and semantic at expansion time.

Therefore:

```text
CPX source
    ≠ arbitrary string

CPX source
    → parsed template syntax
    → typed binding interpolation
    → C+ AST
```

This preserves the macro-like authoring experience without inheriting C-preprocessor limitations.

---

# 72. Architectural rule for generics

Generic programming SHALL use:

```text
compile-time function
        +
semantic arguments
        +
CPX result
        +
specialization cache
```

rather than an independent generic AST subsystem.

---

# 73. Architectural rule for reflection

Reflection SHALL read the stabilized semantic type model.

It SHALL NOT parse emitted C or inspect textual CPX output.

---

# 74. Architectural rule for C generation

Generated C SHALL be derived from the C AST.

No major compiler feature SHALL be implemented through ad-hoc string concatenation into the final C output.

Exceptions are permitted only for explicitly opaque foreign-source fragments.

---

# 75. Architectural rule for IDE support

The compiler front-end is the language service.

There SHALL NOT be a separate VS Code parser that attempts to reproduce compiler semantics.

The VS Code extension consists primarily of:

```text
TextMate grammar
language configuration
LSP client
```

All intelligent behavior originates from the Kotlin language server.

The extension SHALL launch the configured CLI JAR with the configured Java
executable for both LSP and Run Main. Java executable, JAR, and working
directory settings SHALL be passed as structured process arguments/paths so
spaces are preserved. An optional SDK-manifest setting SHALL be passed as the
`cplus.sdk.manifest` JVM property, allowing project working directories outside
the CLI/SDK repository. Run Main SHALL pass the selected entry source to the
CLI and SHALL rely on CLI source discovery for imports rather than maintaining
a parallel extension-side module parser.

---

# 76. Recommended first implementation milestones

### Stage 1 — Front-end

```text
lexer
parser
syntax tree
AST
source origins
```

### Stage 2 — semantic core

```text
symbols
scopes
types
methods
imports
```

### Stage 3 — basic C transcoding

```text
method lowering
symbol mangling
C AST
C emitter
source mapping
```

### Stage 4 — CPX

```text
comptime functions
typed parameters
CPX templates
expansion scheduler
fixed-point expansion
```

### Stage 5 — generics

```text
type arguments
specialization identities
specialization cache
generated declarations
```

### Stage 6 — advanced transformations

```text
inner functions
closures
defer
string templates
```

### Stage 7 — reflection

```text
stable type universe
reflection API
reflective CPX
```

### Stage 8 — tooling

```text
LSP
semantic tokens
completion
navigation
C import awareness
```

---

# 77. Final component map

```text
┌─────────────────────────────────────────────┐
│                 C+ TOOLCHAIN                │
├─────────────────────────────────────────────┤
│                                             │
│  SourceRepository                           │
│       │                                     │
│       ▼                                     │
│  Lexer                                      │
│       │                                     │
│       ▼                                     │
│  Parser ───────────── SyntaxTree             │
│       │                                     │
│       ▼                                     │
│  AstBuilder                                 │
│       │                                     │
│       ▼                                     │
│  CPlusAst                                   │
│       │                                     │
│       ├──── DeclarationCatalogue            │
│       │                                     │
│       ├──── ModuleGraph / ImportResolver    │
│       │                                     │
│       ├──── SymbolTable                     │
│       │                                     │
│       ├──── ScopeTable                      │
│       │                                     │
│       ├──── TypeUniverse                    │
│       │                                     │
│       └──── ComptimeEngine                  │
│                │                            │
│                ├── CPX parser/template      │
│                ├── evaluator                │
│                ├── scheduler                │
│                ├── specialization cache     │
│                └── reflection               │
│                                             │
│                    │                        │
│                    ▼                        │
│             Resolved C+ AST                 │
│                    │                        │
│                    ▼                        │
│             LoweringPipeline                │
│                    │                        │
│                    ▼                        │
│                  C AST                      │
│             /       |       \               │
│            /        |        \              │
│     HeaderGen  DependencyGen  NameMangler   │
│            \        |        /              │
│             \       |       /               │
│                    ▼                        │
│                 CEmitter                    │
│                  /    \                     │
│                 /      \                    │
│             C Source   SourceMap            │
│                                             │
├─────────────────────────────────────────────┤
│                  TOOLING                    │
│                                             │
│  SemanticModel                              │
│       │                                     │
│       ├── Diagnostics                       │
│       ├── Completion                        │
│       ├── Hover                             │
│       ├── Navigation                        │
│       ├── References                        │
│       └── SemanticTokens                    │
│                                             │
│                    │                        │
│                    ▼                        │
│                Kotlin LSP                   │
│                    │                        │
│                    ▼                        │
│              VS Code extension              │
│             TextMate + LSP client           │
└─────────────────────────────────────────────┘
```

---

# 78. SDK, ABI, runtime and platform architecture

The SDK is a source-first target package. Its manifest, target descriptors,
intrinsic catalogue, C+ sources, C compatibility headers, runtime sources,
startup adapters, and platform contracts are resolved as one immutable build
input. Metadata caches accelerate lookup but remain reproducible projections of
the source tree and are invalidated by source, manifest, target, or schema
identity changes.

`TargetRegistry` is the sole target-fact boundary. A descriptor supplies the
triple, OS, architecture, vendor, ABI, object format, endianness, pointer and
word widths, integer model, alignment, symbol/TLS rules, linker/startup entry,
system libraries, features, intrinsic availability, and supported ABI classes.
`ComptimeTargetInfo` and the ABI layout engine consume this data; portable SDK
source does not duplicate target facts in preprocessor branches.
The compiler additionally projects the selected self-hosted target profile's
implemented platform-service set into `ComptimeTargetInfo`; a system-runtime
profile exposes no C+ adapter services. Darwin remains explicitly
capability-gated until its self-hosted startup and adapters exist. The built-in
`require_service("name");` declaration is checked during CPX expansion,
removed before runtime lowering, and reports unavailable, malformed, or
unknown service requirements as `CPX603`. Self-hosted targets without a
startup adapter fail selection with stable diagnostic `SDK013`.

ABI declarations carry semantic identity independently of their spelling. The
compiler model stores ABI kind, source/linker name, export/library metadata,
weak/no-return/storage attributes, and rejects combinations unsupported by the
selected target. Layout queries are preserved through syntax, semantic
validation, C AST, dependency collection, and emission. `offsetof` therefore
adds `<stddef.h>` structurally rather than relying on textual include rules.

The runtime link plan is explicit. Self-hosted profiles select target startup,
the uniform PAL adapter, runtime initialization, compiler support helpers, and
the platform termination primitive with `-nostdlib`, `-nodefaultlibs`, and
`-nostartfiles` as required. Linux self-hosted links also disable PIE so no
host dynamic loader is inherited. The system profile delegates
startup/default libraries to the downstream C compiler. `LinkDriver` selects a
target-capable C driver automatically and translates the plan for GNU/Clang or
MSVC-style drivers; users do not choose a host libc profile.
`RuntimeDependencyAuditor` checks ELF, PE/COFF, and Mach-O products for
forbidden host libc, undeclared OS imports, dynamic interpreters, and
unresolved compiler-runtime dependencies. Auditing is fail-closed: ELF class
and machine identity, PE/COFF format, and Mach-O headers must be recognized;
required inspection tools and dependency output must be available and valid.
Self-hosted links enable per-function/per-data sections and section garbage
collection (`--gc-sections` or `/OPT:REF`) so unused service adapters do not
pull their platform dependencies into minimal products. The system profile
does not enable this self-hosted dead stripping.

For Windows x86_64 self-hosted products, the currently validated compiler
profile is GCC/UCRT64 and the target descriptor uses the GNU x87
`long double` representation. A driver with the MSVC binary64 `long double`
profile MUST fail ABI validation rather than silently link against an
incompatible descriptor. This is a compiler/ABI restriction, not a dependency
on UCRT as the C+ runtime; produced PE products are checked for forbidden C
runtime imports. Adding a separate MSVC ABI profile requires its own target
descriptor, ABI callers, and native execution evidence.

For a target that advertises C17 complex support, the runtime link plan SHALL
include the C+ definitions for compiler-emitted complex multiply/divide helper
ABIs (currently the GCC/Clang `__mul*3` and `__div*3` families). These
definitions are capability-gated and SHALL NOT be satisfied by an undeclared
host `libgcc`, compiler-rt archive, or system math library; the normal runtime
dependency audit remains authoritative.

Runtime memory is layered as `std.alloc` → compiler-owned allocator →
`platform_page_allocate/release`. The allocator is page-backed and exposes
explicit-width size/alignment operations; its implementation MUST remain free
of host `malloc`, `free`, and libc error-number assumptions. The selected
runtime link plan MUST include the allocator source and exactly one target PAL
implementation, and Linux page allocation MUST use the target syscall ABI
rather than a host libc wrapper.

When the CLI does not receive `--target`, it derives the host target triple
from the host OS and architecture. An explicit target remains authoritative and
is required for cross-compilation; host inference SHALL never select a libc
profile or replace the SDK target adapter.

The intrinsic catalogue is data-driven and target-checked. Syscall, atomic,
varargs, context, and other compiler-owned operations are not ordinary library
functions: availability, arity, feature requirements, and lowering identity
are verified before emission. Platform adapters expose narrow memory, file,
process, time, thread, and synchronization services; Linux uses architecture
catalogued syscalls, Windows uses declared DLL imports, and Darwin uses the
supported System/libSystem userspace ABI. File services receive the canonical
C+ UTF-8 path form with `/` separators; path-root, separator, and encoding
conversion belongs inside the target adapter. Compiler/tooling filesystem
access remains native `java.nio.file.Path` and only serialized/logical path
identities use slash normalization.

The current concrete file-service implementation is split as follows:

```text
sdk/runtime/include/cplus_platform.h  versioned PAL C ABI and error/mode constants
sdk/runtime/src/fs.c                   target-independent std.fs forwarding
sdk/platform/linux/runtime.c           openat/read/write/close/renameat adapters
sdk/platform/windows/runtime.c         UTF-8/UTF-16 kernel32 file adapters
```

The file ABI uses explicit-width C types for handles and byte counts so the
same generated contract is valid under Linux LP64 and Windows LLP64. The
linker includes `fs.c` and exactly one target platform runtime unit in a
self-hosted product.

The process ABI follows the same boundary: process handles use a signed
64-bit opaque carrier, argument vectors are borrowed UTF-8 null-terminated
pointer arrays, and current-process arguments/environment are exposed as
runtime-lifetime UTF-8 views. Linux startup forwards the initial stack's
`argc`, `argv`, and `envp` to common runtime initialization. Windows startup
converts `GetCommandLineW` arguments and the `GetEnvironmentStringsW` block to
runtime-owned UTF-8 vectors before invoking common initialization. The child
inherits the current environment and standard streams, and PAL failures use
the stable CPLUS error set. Standard input uses Linux fd 0 / Windows
`GetStdHandle(STD_INPUT_HANDLE)` and `ReadFile`; stdout/stderr use Linux fds 1
and 2 / their Windows standard handles and `WriteFile`. Linux uses
target-catalogued process syscalls; spawn uses a close-on-exec
error channel so `execve` failures are returned synchronously instead of being
confused with a child exit code. Windows uses `CreateProcessW`, converts
UTF-8 arguments to UTF-16, applies the Windows argument-quoting rules, inherits
the process environment and standard handles, and closes the process handle
after a successful wait. Neither adapter delegates process creation or
waiting to an installed host libc. The target-independent `process.c` façade
forwards identity, argument/environment views, spawn/wait/exit, and
standard-channel operations while preserving PAL errors and borrowed-storage
rules. `RuntimeLinker` includes this façade, the common process startup state,
and exactly one target adapter.

The version-4 PAL time adapter exposes separate UTC wall, steady monotonic,
and current process CPU clocks as checked signed 64-bit nanoseconds. Linux uses
`clock_gettime` with realtime, monotonic, and process-CPU clock IDs; Windows
uses system FILETIME, performance-counter, and current-process user/kernel
FILETIME sources respectively. Overflow and native clock failures map to
stable PAL errors. The C `time()` and `clock()` façades consume wall and
process-CPU time rather than aliasing both to a monotonic timer.
The target-independent `sdk/runtime/src/time.c` also forwards `std.time` clock
values, implements signed overflow-checked nanosecond duration conversion and
arithmetic, and converts between timestamps and the fixed-layout proleptic
Gregorian UTC calendar type without libc or timezone-database dependencies.
C `time()` and `clock()` map negative PAL results to their standard -1
sentinel rather than leaking PAL error values.

The version-4 thread adapter creates runtime-managed threads without pthreads.
Linux uses an architecture-specific raw `clone` entry, `CLONE_SETTLS`, the
child-clear-TID futex join protocol, and a linker-script-described static TLS
image. Freestanding startup allocates and installs the initial thread's TLS
block before entering common runtime initialization; each child receives a
copy of initialized TLS data and zeroed TLS storage, then performs runtime
thread attachment before calling its entry function. Windows uses
`CreateThread`, `WaitForSingleObject`, and loader-managed TLS; its callback
performs the same runtime attachment before user code. Thread-control and stack
storage use the PAL page allocator and are released by join.

Linux startup contributes a one-byte initialized TLS anchor so the linker
retains `.tdata` even when the program's TLS variables are all zero-initialized
and section garbage collection is enabled. The TLS metadata script roots this
anchor and derives the initialized image bounds and alignment from `.tdata`
and `.tbss`; the runtime copies the anchor along with the rest of the image.

The target-independent `sdk/runtime/src/thread.c` façade implements the public
`std.thread` create/join/current-identity/yield declarations by forwarding to
the version-4 PAL. It preserves the 64-bit opaque handle and stable status
values without exposing native thread types. `RuntimeLinker` includes this
façade with the common runtime so C+ callers need no pthread or host C runtime.

Typed `std.sync` declarations are forwarded by
`sdk/runtime/src/sync_std.c` to the shared state-word algorithms in
`sdk/runtime/src/sync.c`. Each public object has the PAL state at offset zero;
the adapter therefore adds no platform-specific object representation or
dependency. `RuntimeLinker` includes both the algorithm and façade sources.

`std.atomic` is implemented by `sdk/runtime/src/atomic.c`: the target C
compiler's atomic builtins implement aligned 32-bit load/store/RMW/fence
operations, while wait/wake delegates to the PAL. The façade switches over
explicit memory-order values so each builtin receives a compile-time constant
order rather than defaulting to sequential consistency. Atomic integers remain
four bytes on every supported ABI; this implementation must link without
`libatomic` on the declared x86_64/AArch64 targets.

Portable synchronization algorithms are shared in `sdk/runtime/src/sync.c`:
32-bit state-word mutexes, sequence-based condition variables, counting
semaphores, and once initialization use compiler atomic intrinsics for their
state transitions and memory ordering. Their blocking edge is the PAL's
32-bit atomic wait/wake pair. Linux maps this pair to private futex syscalls;
Windows maps it to `WaitOnAddress` and the address wake APIs (Windows 8+).
Successful wake returns zero consistently rather than exposing a
platform-specific waiter count. The runtime link plan includes the shared
algorithm source and exactly one target wait/wake adapter; ordinary atomic
load/store/RMW operations do not become OS services.

Socket transport uses a fixed 28-byte binary address record independent of
`sockaddr` layouts. Linux adapters translate that record and use the
architecture's direct socket syscalls, setting close-on-exec on created and
accepted descriptors. Windows adapters translate it to Winsock structures and
resolve Winsock entry points from `ws2_32.dll` on first use through the already
required kernel API; the executable's PE import table therefore does not gain
an unconditional `ws2_32` dependency. Windows initialization SHALL be
thread-safe and process-wide, load the system copy of `ws2_32.dll`, request
Winsock 2.2, and cache only stable PAL status values. A successful startup SHALL
be balanced by `WSACleanup` during process termination. The runtime linker
includes the Windows socket adapter only for Windows targets. Both adapters
expose blocking lifecycle, TCP stream, UDP datagram, local/peer address, and
shutdown operations with stable PAL error results. The address/DNS layer sits
above this binary transport ABI. A shared freestanding runtime source parses
and formats IPv4/IPv6 text, performs UTF-8 validation and IDNA A-label encoding,
and returns canonical text without locale or host-library dependencies,
including mixed dotted-decimal formatting for IPv4-mapped IPv6 addresses. Linux
uses the configured numeric nameservers, kernel-entropy transaction IDs,
ephemeral ports, nonblocking direct socket syscalls, and `poll`/`ppoll`
readiness waits against an absolute two-second query deadline; truncated UDP
messages retry over TCP within that same deadline. CNAME and result counts are bounded by the language
contract. Windows converts the shared A-label to an absolute UTF-16 hostname
and calls dynamically resolved `GetAddrInfoW` after lazy module initialization;
it copies/frees the native result list before returning. Both implementations
expose the same caller-owned address-array ABI and stable PAL error values;
neither leaks native resolver structures or allocator ownership.

The target-independent `sdk/runtime/src/net.c` implements the public
`std.net` façade over these PAL operations. It marshals the public fixed-layout
address record through local PAL records rather than aliasing distinct C
struct types, copies address outputs only after successful operations, and
bounds resolver scratch storage to the PAL's 256-result maximum. On
buffer-too-small resolution it copies the permitted result prefix and reports
the required count; on other errors it preserves caller outputs. The common
facade is included in the self-hosted runtime plan for both OS families, while
the PAL source remains target-selected.

The C backend's ABI gate includes an independently compiled C17 caller fixture.
The fixture MUST consume the generated public header and link against generated
C, exercising scalar and object-pointer parameters, aggregate by-value
parameters and returns, callbacks, variadic declarations, exported linker
names, and public thread-local globals. Variadic metadata and TLS storage
qualifiers MUST survive syntax, AST, semantic, lowered-C, header, and emission
boundaries. A green C+ compilation or a syntax-only header check alone does
not satisfy this gate.

The CLI exposes these boundaries through `sdk doctor|verify|package`,
`target list|show`, `abi verify`, `runtime inspect`, `libc test`, and `audit`.
All package indexes and generated metadata are deterministic and versioned.

# 79. Core architectural invariant

The central invariant of the implementation is:

> Every compiler transformation operates on structured program representations, every generated construct retains provenance, and no feature requires the compiler, transcoder, and language server to maintain separate interpretations of C+.

That invariant governs the parser, CPX system, semantic model, lowering pipeline, C backend, and IDE tooling.

---

# 80. Source test implementation architecture

## 80.1 Shared fixture and assertion model

Use structured syntax/AST nodes for fixture declarations and assertions, with
description/operand ranges and origins. Reuse normal block/expression parsing;
do not extract test bodies with regex or preprocess them into a second source
language. All walkers, fingerprints, CPX reorigin/hygiene, printers, reference
collectors and lowering passes SHALL handle the new nodes explicitly.

The semantic model SHALL record fixture scopes, typed operands, equality
conversions and source expressions. Built-in assertion recognition is confined
to the fixture lexical context in LS §53.2. LSP diagnostics, completion,
navigation and semantic tokens SHALL consume this model without executing
fixtures. The import index SHALL never offer a fixture as an export.

## 80.2 Typed lowering and runtime reporting

Compilation mode and selected root fixture identities SHALL be explicit
request inputs and cache-key fields. After semantic validation, normal modes
erase fixture bodies before runtime lowering. Test mode produces fixture
functions and one entry wrapper per root product, with user `main` remapped by
symbol identity. The wrapper accepts exactly two user arguments: the stable
fixture identity and result-file path. It dispatches exactly one fixture,
opens reporting before invocation, and writes completion only after the fixture
returns. Invalid arguments or identities return a nonzero execution status.
Normal block/return lowering MUST run all registered `defer` actions before
the wrapper can finish reporting. Reuse method/closure/defer lowering.
Generated statements and helper calls SHALL retain fixture/assertion origins.

Lower each assertion through typed temporaries, enforcing description,
expected and actual evaluation order before reporting or comparison. Do not
rely on C function-argument evaluation order. Reports print original operand
values separately from the converted equality result. Use target-correct
integer formatting (including supported 128-bit types), round-trip-capable
real floating formatting including NaN/infinity/signed zero, real/imaginary
components for supported complex values, and hexadecimal addresses or `null`
for pointers. Aliases/enums use underlying value formatting. Never dereference
an arbitrary pointer to format equality operands; explicit descriptions are
valid language strings. Escape embedded control characters in descriptions and
source labels so one label cannot forge report lines. Arbitrary user output
remains unchanged.

The lowering/runtime boundary uses synchronous, typed-address hooks; the
runtime MUST consume each pointed-to value during the call and MUST NOT retain
the address. C lowering declares these test-only signatures:

```c
void __cplus_test_report_truth(const char*, const char*, const void*,
                               const char*, unsigned long long, int, int, int);
void __cplus_test_report_equality(const char*, const char*, const char*,
                                  const void*, const char*, unsigned long long, int, int,
                                  const void*, const char*, unsigned long long, int, int, int);
```

The fields are description; source-expression label(s); pointer to the
original typed temporary; emitted C type spelling; `sizeof` that temporary; value
kind; null-pointer flag; and pass/fail. Value-kind tags are fixed: 0
unsupported, 1 signed integer, 2 unsigned integer, 3 plain-char integer,
4 boolean, 5 float, 6 double, 7 long double, 8/9/10 float/double/long-double
complex, and 11 pointer or function pointer. Equality carries the expected
value tuple, then the actual value tuple, then pass/fail. Each value is evaluated into its own temporary
before this hook is called; equality conversion is applied only to the
comparison, never to the reported original values.

Test reporting helpers SHALL be internal SDK/runtime services using existing
stdio/file/PAL support, included only in test products. The compiler records
their runtime dependencies through the helper catalogue and normal linker.
No host-libc-specific assertion library or new public `std.test` API is required.
Self-hosted Linux and Windows products SHALL retain existing ABI and dependency
audit guarantees. Unsupported runnable profiles fail explicitly.

Preconditions: fixture scopes and operand types are resolved, selected roots
are known and the report helper contract matches the compiler.
Postconditions: C-subset validation sees ordinary functions/statements only;
normal products have no test entry/dependency; test operands execute once.
Invalid states: unresolved assertion types, duplicate entry symbols, leaked
test syntax at C emission or unsupported report value representation.

## 80.3 Child process and result protocol

Build one executable per root import closure, then invoke it once per selected
fixture, passing fixture identity and a private result-file path as arguments.
Each invocation has a fresh process and result file. The generated entry wrapper
must initialize reporting before the fixture and finalize it only after normal
return and deferred actions. Stdout/stderr stay available for user output;
control records SHALL NOT be recovered by scraping human-readable output.

On normal fixture return, the wrapper SHALL finish the completion record and
exit zero even when assertions failed; the CLI derives test failure from those
records. A nonzero child exit is an execution error. This keeps ordinary failed
assertions from also being counted as fixture execution errors.

Use a small versioned UTF-8 append-only record protocol with fixture identity,
monotonic assertion sequence numbers, pass/fail records and a final completion
record containing matching totals. Flush each complete record. Descriptions
and values belong to human output, not this control channel. The CLI validates
version, identity, sequence and totals; malformed/truncated records or a missing
completion record are errors. Valid preceding assertions remain countable.
Exit zero alone never proves successful completion. A protocol writer failure
causes an execution error. This is an internal integrity contract, not a sandbox
against deliberately malicious fixture code.

The current protocol version is 1. Records are newline-terminated UTF-8 TSV:

```text
CPLUS-TEST<TAB>1<TAB>BEGIN<TAB>percent-encoded-fixture-identity
CPLUS-TEST<TAB>1<TAB>ASSERT<TAB>sequence<TAB>PASS-or-FAIL
CPLUS-TEST<TAB>1<TAB>COMPLETE<TAB>passed<TAB>failed<TAB>total
```

Identity encoding leaves ASCII letters, digits, `_`, `-`, `.`, `/` and `:`
unchanged and encodes every other UTF-8 byte as `%HH` with uppercase hex.
Assertion sequences start at 1 and increase by exactly one. The writer emits
each record directly through PAL file writes, handles partial writes, and
reports open/write/close failures as execution errors. It never places user
descriptions or rendered values in the control file.

Children SHALL inherit/drain user output without pipe deadlock; bound protocol
record size and parse it incrementally rather than reading unbounded output
into memory. The timeout/cancellation path reaps the process, attempts cleanup
of spawned descendants where supported and removes only owned temporary files.
Report files are ordinary paths passed as data, including Windows slash paths
and paths containing spaces. Runtime file access uses the existing PAL.

## 80.4 Acceptance and editor integration

Parser/semantic/lowering tests SHALL cover all four assertion forms, recovery,
single evaluation, equality conversions, `defer`, main coexistence and source
maps. CLI golden/execution tests SHALL cover multiple roots/fixtures, output
ordering, loops, empty tests, failing assertions, compilation failure, early
exit, crash, timeout, malformed protocol, paths with spaces and cleanup.
Compiler integration SHALL execute imported-CPX-generated types inside fixtures
and fixtures generated by CPX. Cache tests SHALL alternate normal/test modes.
Editor tests SHALL prove shared diagnostics and navigation inside fixtures and
correct lexical highlighting without running user code. Final release gates
require local Linux and native Windows evidence; unavailable gates remain open.
