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

---

# 13. Method model

Methods are declared inside structs but stored semantically as callable symbols associated with an owning type.

```kotlin
data class MethodSymbol(
    val symbol: SymbolId,
    val owner: TypeId,
    val receiver: ReceiverKind,
    val functionType: TypeId,
    val receiverType: TypeId
)
```

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
produce explicit scope bindings for selective aliases.

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

The bootstrap adapter SHALL catalogue the standard C modules `c.stdio`,
`c.stddef`, `c.stdlib`, `c.math`, `c.string`, `c.ctype`, `c.time`, `c.stdint`,
and `c.stdarg`, mapping each module to its corresponding system header. It
SHALL reject an imported symbol absent from the selected catalogue rather than
creating an untyped or guessed foreign declaration. Additional header
catalogues MAY be supplied through the `CImportService` configuration.

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
unresolved compiler-runtime dependencies.

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
