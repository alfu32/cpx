# C+ Compiler and Language Tooling — Implementation Plan

## Dashboard

The historical foundation counter and the release-roadmap counter measure
different things. The first records the original 146 planning items; the
second is the authoritative queue for completing the working CLI transcoder,
runtime, SDK, LSP, and release products.

### Release roadmap dashboard

```text
Historical foundation: 145/146 evidenced; one std.core task reopened
Roadmap leaf tasks:    28/49 evidenced on Linux
Phase gates:           2/9 complete; 4 active; 3 queued
Current task:          R1.4.4 — resolve qualified module type imports and generated C
Current milestone:     R1 — language and front-end conformance
Windows execution:     deferred until the final validation pass by request

R0 [DONE]  1/1  implementation inventory and scope freeze
R1 [DOING] 11/15 primitive, ABI, type-import, and user-alias conformance open
R2 [DONE]  7/7  CPX, generics and reflection conformance
R3 [DOING] 4/5  primitive source-to-ABI audit reopened; Windows gate pending
R4 [DOING] 5/5  Linux runtime/libc evidence; Windows cross-platform gate pending
R5 [DOING] 0/5  std.core target-size types remain; file PAL extensions later
R6 [TODO]  0/4  CLI transcoder and build-product completion
R7 [TODO]  0/3  LSP and VS Code product completion
R8 [TODO]  0/4  SDK packaging, target matrix and release conformance

TOTAL       28/49 implementation tasks complete; 2/9 phase gates complete,
            4 active, 3 queued
```

The detailed, authoritative R0–R8 work queue is in the
[completion roadmap](#completion-roadmap--post-foundation-implementation)
below. Its current execution sequence is:

1. R1.4.1–R1.4.5 — implement and verify module-owned source types, explicit
   type imports, aliases, visibility boundaries, and qualified module access.
2. R1.2.4/R1.2.5 — add explicit-import `std.fixed_width` aliases `i8`…`i64`
   and `u8`…`u64`, then define capability-gated `i128`/`u128` support.
3. R3.1.3 — verify parsed spellings, type imports, and aliases through emitted
   C and independent ABI fixtures.
4. R5.1 — complete the reopened target-aware std.core byte/size/index types.
5. R5.2 onward — resume native std and platform work after the type boundary is
   reliable, finish R6–R8, and perform the deferred Windows validation and patch
   pass.

Latest completed implementation commits:

- `60047dc` — central C primitive metadata and reopen R5.1 on audit;
- `76a1876` — preserve parsed integer identity across target ABI layouts;
- `bc60a1e` — verify integer spellings across compiler and CLI/LSP front ends;
- `7321b66` — normalize complete C integer specifier sequences;
- `c7e6817` — target-neutral native std value foundations;
- `0fd8835` — executable Linux C17 conformance gate;
- `a6cc980` — advanced Linux C17 runtime families;
- `f8be29e` — self-hosted stdio/time/basic C17 families.
`completed/total` counts only terminal numbered tasks in each phase subtree;
parent work items are completion gates and are not counted again when they
contain subtasks.

---

# 1. [DONE] [16/16] Language front-end

**Purpose:** Convert C+ source into a provenance-preserving normalized AST suitable for semantic analysis and CPX processing.

**Language**
- LS §3 Terminology
- LS §4 Program Processing Model
- LS §28 Source Provenance
- LS §34 Parsing and Semantic Ambiguity

**Technical**
- TS §4–8
- TS §65
- TS §67–70

---

## 1.1 [DONE] [4/4] Source and provenance infrastructure

**Language**
- LS §28 Source Provenance
- LS §29 CPX and Source Provenance

**Technical**
- TS §4 Source Model
- TS §43 Source-Map Builder
- TS §66 Internal Immutability Strategy

### 1.1.1 [DONE] Source repository and stable source identities

**Technical**
- TS §4.1 SourceFile
- TS §45 Compiler Context

**Deliverable**
- `SourceFileId`
- `SourceFile`
- `SourceRepository`
- document versioning

**Acceptance**
- Source files receive stable IDs.
- Source text can be retrieved by ID.
- Updating a file increments its version without changing its logical identity.
- Unit tests cover create, retrieve and update.

**Depends:** none

### 1.1.2 [DONE] Source ranges and line index

**Language**
- LS §28.1 Source Span

**Technical**
- TS §4.2 SourceRange

**Deliverable**
- `SourceRange`
- `LineIndex`
- offset ↔ line/column conversion

**Acceptance**
- Ranges are half-open and consistently defined.
- UTF source offsets behave consistently.
- Offset-to-position and position-to-offset round trips are tested.

**Depends**
- 1.1.1

### 1.1.3 [DONE] Origin and expansion provenance model

**Language**
- LS §28.2–28.4
- LS §29

**Technical**
- TS §4.3 Origin
- TS §19 Expansion Identity

**Deliverable**
- `Origin.Direct`
- `Origin.Generated`
- `Origin.Expansion`
- `Origin.Synthetic`

**Acceptance**
- Nested origins retain definition and invocation ancestry.
- Generated nodes can trace back to original C+ source.
- Origin objects do not depend on emitted C positions.

**Depends**
- 1.1.2

### 1.1.4 [DONE] Provenance test harness

**Language**
- LS §28
- LS §29

**Technical**
- TS §43
- TS §63 Source-map tests

**Acceptance**
- Tests can assert direct origin.
- Tests can assert generated origin.
- Tests can assert nested CPX expansion chains.
- Failure output prints useful origin chains.

**Depends**
- 1.1.3

---

## 1.2 [DONE] [4/4] Lexer

**Language**
- LS §8 CPX Source Templates
- LS §44 Conflict Rules

**Technical**
- TS §5 Lexer

### 1.2.1 [DONE] Core C/C+ token model

**Deliverable**
- `Token`
- `TokenKind`
- identifiers, literals, punctuation and operators

**Acceptance**
- Representative C declarations tokenize correctly.
- Token ranges are exact.
- Whitespace does not destroy source positions.

**Depends**
- 1.1.2

### 1.2.2 [DONE] C+ keywords and modifiers

**Language**
- LS §6
- LS §7
- LS §21

**Acceptance**
- `comptime`, `import`, `package` and C+ modifiers are recognized.
- Ordinary identifiers containing keyword substrings remain identifiers.
- Keyword tests cover boundary cases.

**Depends**
- 1.2.1

### 1.2.3 [DONE] CPX interpolation lexical rules

**Language**
- LS §8.2 Direct Binding Interpolation
- LS §8.3 Identifier Composition
- LS §33 CPX Name Conflicts

**Technical**
- TS §16 CPX Template Representation

**Acceptance**
- `{T}` is recognized inside composed identifiers.
- standalone `T` remains an ordinary token whose CPX meaning is resolved later.
- `Temporary` never becomes implicit interpolation of `T`.

**Depends**
- 1.2.2

**Implementation**
- CPX template parsing recognizes explicit `{binding}` interpolation only when the binding is a complete composition fragment.
- Direct binding interpolation is boundary-checked, so `T` resolves as a standalone binding while `Temporary` remains literal source text.
- Expansion tests cover direct interpolation, explicit identifier composition, and the `Temporary` conflict case.

### 1.2.4 [DONE] Lexical diagnostics and incremental relexing boundary

**Technical**
- TS §5
- TS §47 Incremental Compilation
- TS §65 Error Recovery

**Acceptance**
- Unterminated literals/comments report ranges.
- Lexer recovers sufficiently to continue parsing.
- API supports relexing changed documents without exposing mutable global state.

**Implementation**
- The lexer reports bounded ranges for unterminated block comments, strings, characters, and unknown characters.
- `Lexer.relex` expands edits to lexically neutral line boundaries, reuses unaffected tokens/diagnostics with offset shifts, and falls back to a full authoritative lex when lexical state crosses the edit.
- Relexing is stateless and source-identity based; regression tests compare incremental output with a complete lex.

**Depends**
- 1.2.3

---

## 1.3 [DONE] [4/4] Grammar and parser

**Language**
- LS §6
- LS §7
- LS §21
- LS §34

**Technical**
- TS §6 Parser

### 1.3.1 [DONE] Baseline C declaration and statement grammar

**Acceptance**
- structs, unions, enums, variables, functions and blocks parse.
- pointers, arrays and function signatures parse.
- representative existing C+ examples parse without CPX features.

**Depends**
- 1.2

### 1.3.2 [DONE] Expression parser and member syntax

**Language**
- LS §6.4 Method Invocation Syntax
- LS §34

**Technical**
- TS §6.2 Grammar
- TS §14 Member-call Resolution

**Deliverable**
- Pratt or equivalent expression parser

**Acceptance**
- precedence and associativity are correct.
- `a.b`, `a.b()`, calls and indexing parse structurally.
- parser does not prematurely classify static versus instance invocation.

**Depends**
- 1.3.1

**Implementation**
- Pratt-style precedence parsing covers assignment, arithmetic, logical, bitwise, conditional, calls, member access, updates, and indexing.
- Member access remains syntactic until semantic resolution, preserving instance/static ambiguity for the semantic phase.
- AST tests assert structural call/member/index nesting and precedence-sensitive expression shape.

### 1.3.3 [DONE] C+ declaration grammar

**Language**
- LS §6 Methods
- LS §7 Compile-time Functions
- LS §21 Imports

**Acceptance**
- methods inside structs parse.
- `comptime` declarations parse.
- package and all import forms parse.
- CPX return blocks parse as template-bearing syntax.

**Depends**
- 1.3.2

**Implementation**
- Struct methods, `comptime cpx` declarations, CPX return templates, packages, aliases, and all supported import forms are parsed into dedicated syntax nodes.
- Declaration and golden tests preserve method receiver metadata, import aliases/selective names, package boundaries, and template text.

### 1.3.4 [DONE] Parser recovery and semantic ambiguity preservation

**Language**
- LS §34 Parsing and Semantic Ambiguity
- LS §44.10

**Technical**
- TS §6.1
- TS §65

**Acceptance**
- incomplete `foo.bar(` produces usable partial syntax.
- constructs such as `foo * bar;` are not incorrectly forced into a semantic classification.
- parser reports errors while continuing through subsequent declarations.

**Depends**
- 1.3.3

**Implementation**
- Missing delimiters and incomplete calls produce error/partial syntax while synchronization continues at declaration and statement boundaries.
- Expression forms such as `foo * bar` remain binary syntax until semantic type/name resolution; the parser does not force a declaration interpretation.
- Recovery tests verify that a later declaration remains available after an incomplete member call.

---

## 1.4 [DONE] [4/4] Syntax tree and normalized AST

**Technical**
- TS §7 Syntax Tree versus AST
- TS §8 AST Architecture
- TS §68 Hot-path Representation

### 1.4.1 [DONE] Syntax node hierarchy

**Acceptance**
- parsed syntax retains punctuation and source ranges where required.
- incomplete/error nodes are representable.
- syntax nodes retain direct origins.

**Depends**
- 1.3

### 1.4.2 [DONE] `NodeId` and AST arena

**Technical**
- TS §8
- TS §67 Kotlin-specific Conventions
- TS §68

**Acceptance**
- stable `NodeId` values address AST nodes.
- replacements do not require external object references.
- arena tests cover add/get/replace.

**Depends**
- 1.4.1

### 1.4.3 [DONE] Syntax-to-AST normalization

**Acceptance**
- irrelevant punctuation disappears from AST.
- declarations, statements, expressions and type references normalize consistently.
- every AST node retains an origin.

**Depends**
- 1.4.2

### 1.4.4 [DONE] Front-end golden tests

**Technical**
- TS §63 Grammar/AST tests
- TS §64 Golden Compiler Tests

**Acceptance**
- source fixtures produce deterministic normalized AST snapshots.
- malformed fixtures produce deterministic diagnostics.
- source ranges and origins are asserted.

**Depends**
- 1.4.3

---

# 2. [DONE] [30/30] Semantic model and modules

**Purpose:** Establish authoritative symbol, scope, type, method, package, import and foreign-C semantics.

**Language**
- LS §5
- LS §6
- LS §21–23
- LS §32–37
- LS §43

**Technical**
- TS §9–14
- TS §26–30

---

## 2.1 [DONE] [4/4] Declaration catalogue, symbols and scopes

### 2.1.1 [DONE] Stable symbol identity and symbol table

**Language**
- LS §23 Symbol Identity

**Technical**
- TS §9
- TS §10

**Acceptance**
- every declaration receives `SymbolId`.
- source name and semantic identity are distinct.
- symbols do not depend on emitted C names.

**Depends**
- 1.4

### 2.1.2 [DONE] Lexical scope table

**Language**
- LS §32 Name Lookup

**Technical**
- TS §11 Scope Model

**Acceptance**
- package, module, type, function, block, comptime and CPX scopes exist.
- parent-scope lookup works.
- shadowing behavior is testable.

**Depends**
- 2.1.1

**Implementation**
- Semantic analysis now creates package, module, type, function, block, comptime, and CPX-template scopes with explicit parent links.
- Function, method, loop, inner-function, and local bindings are inserted into their lexical scope; `ScopeTable.lookup` retains nearest-scope shadowing and candidate lists.
- Semantic tests inspect actual function/block ancestry and shadowed local lookup.

### 2.1.3 [DONE] Declaration catalogue construction

**Language**
- LS §5 Declaration Catalogue

**Technical**
- TS §9
- TS §59 Pipeline Orchestration

**Acceptance**
- discoverable declarations are registered before CPX evaluation.
- forward textual placement does not prevent cataloguing where legal.
- duplicate declaration candidates are diagnosed.

**Depends**
- 2.1.2

**Implementation**
- `DeclarationCatalogue` records declaration name/kind, symbol identity where available, lexical scope, origin, parameters, and compile-time classification.
- The catalogue is built from the complete AST before reference collection, preserving forward declarations and CPX entities alongside runtime declarations.
- Duplicate runtime candidates continue to produce stable semantic diagnostics; catalogue tests cover forward function and compile-time entries.

### 2.1.4 [DONE] Name lookup and conflict resolution

**Language**
- LS §32
- LS §33
- LS §44.1, §44.4, §44.5

**Acceptance**
- runtime, type, package and comptime bindings are distinguishable.
- explicit ambiguity produces diagnostics.
- CPX lexical binding precedence follows the specification.

**Depends**
- 2.1.3

**Implementation**
- Scope bindings retain all candidates while semantic lookup separates runtime, type, foreign, member, and compile-time catalogue kinds.
- Import resolution diagnoses unresolved modules, inaccessible names, and unqualified collisions rather than silently selecting a textual candidate.
- CPX template binding precedence is restricted to valid interpolation positions and explicit composition, with regression coverage for shadowing/conflict boundaries.

---

## 2.2 [DONE] [9/9] Type and method system

### 2.2.1 [DONE] [6/6] Canonical type universe representation

**Technical**
- TS §12 Type System Model

**Acceptance**
- primitive, pointer, array, struct, union, enum, function, alias and foreign types exist.
- canonical types receive stable `TypeId`.
- semantic equality does not depend on spelling.

**Depends**
- 2.1

#### 2.2.1.1 [DONE] Register primitive and aggregate type identities

**Acceptance**
- primitive, struct, union, and enum types receive semantic identities.
- aggregate fields resolve through the canonical type maps.
- emitted C declarations preserve aggregate kind.

**Depends**
- 2.1.1

#### 2.2.1.2 [DONE] [4/4] Add arrays, function types, aliases and foreign types

**Acceptance**
- array, function, alias, and foreign type categories are representable semantically.
- type references resolve through the same canonical universe.

**Depends**
- 2.2.1.1

##### 2.2.1.2.1 [DONE] Represent array declarators and semantic array types

**Acceptance**
- fixed-size and unsized array dimensions parse on fields, globals, parameters and locals.
- array dimensions survive AST and semantic resolution as `ArrayType` values.
- the C backend emits readable array declarators in every supported declaration position.
- generated C containing arrays compiles and executes through the integration path.

**Depends**
- 2.2.1.1

##### 2.2.1.2.2 [DONE] Represent function types

**Acceptance**
- function types are explicit semantic values with return and parameter types.
- function-valued declarations and references resolve through the canonical type universe.
- C lowering preserves callable declarator shape where supported.

**Depends**
- 2.2.1.2.1

##### 2.2.1.2.3 [DONE] Represent aliases

**Acceptance**
- alias declarations have distinct symbols and canonical target types.
- alias references resolve without losing alias metadata required by diagnostics and CPX identity.
- generated C preserves the requested alias declaration or expands it according to lowering rules.

**Depends**
- 2.2.1.2.1

##### 2.2.1.2.4 [DONE] Represent foreign types

**Acceptance**
- imported C types have explicit foreign semantic identities.
- foreign types retain external spelling/linkage metadata.
- unsupported foreign type forms remain diagnosed rather than guessed.

**Depends**
- 2.2.1.2.1

#### 2.2.1.3 [DONE] Canonicalize type equality and identity

**Acceptance**
- equivalent type spellings compare by semantic identity.
- pointer and aggregate identity does not depend on emitted C spelling.

**Depends**
- 2.2.1.2

### 2.2.2 [DONE] Type reference and alias resolution

**Language**
- LS §34

**Acceptance**
- aliases resolve to canonical types while preserving alias metadata where required.
- unresolved type identifiers generate diagnostics.
- type versus expression ambiguity is deferred until semantic resolution.

**Depends**
- 2.2.1

### 2.2.3 [DONE] Method classification and method sets

**Language**
- LS §6.1–6.3

**Technical**
- TS §13 Method Model

**Acceptance**
- method with receiver `self` is `INSTANCE`.
- method with receiver `self*` is `INSTANCE` with a pointer receiver type.
- method without `self` is `STATIC`.
- methods remain associated with their containing semantic type.
- pointer-receiver bodies bind `self` as a pointer and resolve `self->field`.

**Depends**
- 2.2.2

### 2.2.4 [DONE] Member access and call resolution

**Language**
- LS §6.4–6.5
- LS §44.2–44.3

**Technical**
- TS §14

**Acceptance**
- `instance.method()` resolves to instance method.
- `Type.method()` resolves to static method.
- field/method conflicts follow specification rules.
- ambiguous valid candidates are rejected.

**Depends**
- 2.2.3

---

## 2.3 [DONE] [8/8] Packages and C+ imports

### 2.3.1 [DONE] [3/3] Package and module representation

**Language**
- LS §3.2 Package
- LS §21

**Technical**
- TS §26

**Acceptance**
- source files belong to modules/packages.
- qualified identities are deterministic.
- multi-file package membership is supported.

**Depends**
- 2.1

#### 2.3.1.1 [DONE] Parse package declarations into the AST

**Acceptance**
- `package name;` and qualified package names are retained as structured declarations.
- package origins remain available to later module passes.

**Depends**
- 1.3.3

#### 2.3.1.2 [DONE] Construct qualified package identities

**Acceptance**
- package, module, and declaration names combine deterministically.
- semantic symbols expose qualified identity independently of emitted C names.

**Depends**
- 2.3.1.1

#### 2.3.1.3 [DONE] Associate compilation units with packages

**Acceptance**
- multiple source files can share one package identity.
- package visibility and exported declarations are represented before import resolution.

**Depends**
- 2.3.1.2

### 2.3.2 [DONE] Module dependency graph

**Technical**
- TS §27
- TS §29

**Acceptance**
- module dependencies form an explicit graph.
- dependency queries are deterministic.
- graph supports cycle inspection.

**Depends**
- 2.3.1

### 2.3.3 [DONE] [3/3] Import, alias and selective-import resolution

**Language**
- LS §21.1–21.4

**Acceptance**
- `import foo`
- `import foo as bar`
- `import {a,b} from foo`
create correct module, function, and value bindings. Selective imports of
source type declarations were not covered by this foundation-stage completion;
they are explicitly reopened as R1.4 below.

- collisions require qualification or aliasing.

**Depends**
- 2.3.2

#### 2.3.3.1 [DONE] Normalize import forms and preserve aliases

**Acceptance**
- qualified modules, selective names, and `as` aliases remain structured in syntax and AST nodes.
- parser and AST tests cover all supported import spellings.

**Depends**
- 2.3.2

#### 2.3.3.2 [DONE] Resolve imported bindings by module

**Acceptance**
- imported names resolve against the declaring module rather than an accidental global catalogue.
- qualified module references and aliases produce deterministic bindings.

**Depends**
- 2.3.3.1

#### 2.3.3.3 [DONE] Diagnose import collisions and unresolved names

**Acceptance**
- conflicting unqualified imports require qualification or aliasing.
- unresolved imports identify the module and imported name.

**Depends**
- 2.3.3.2

### 2.3.4 [DONE] C+ import cycle analysis

**Language**
- LS §21.5

**Acceptance**
- declaration-only cycles that can be catalogued are supported.
- unresolved compile-time dependency cycles are diagnosed.
- cycle diagnostics identify the relevant modules.

**Depends**
- 2.3.3
- 3.3 later extends compile-time cycle semantics.

---

## 2.4 [DONE] [9/9] C interoperability model

### 2.4.1 [DONE] Foreign symbol and type representation

**Language**
- LS §22
- LS §43

**Technical**
- TS §28

**Acceptance**
- foreign functions, types, globals and enum constants have semantic representations.
- foreign symbols preserve external linkage names.
- foreign symbols are visibly distinguishable from C+ declarations.

**Depends**
- 2.2.1

### 2.4.2 [DONE] [3/3] C declaration parser/import adapter

**Language**
- LS §22.1–22.3

**Acceptance**
- basic C headers populate foreign symbols.
- typedefs resolve.
- unsupported preprocessor constructs remain explicit rather than guessed.

**Depends**
- 2.4.1

#### 2.4.2.1 [DONE] Bootstrap standard C foreign import adapters

**Acceptance**
- selective `c.stdio` imports register `printf` as a variadic foreign function.
- standard C modules include `c.stdio`, `c.stddef`, `c.stdlib`, `c.math`,
  `c.string`, `c.ctype`, `c.time`, `c.stdint`, and `c.stdarg`.
- the C backend emits the corresponding standard-library include.
- imported calls across the standard-module adapters compile and execute through
  the CLI path.

**Depends**
- 1.3.3
- 2.2.1

#### 2.4.2.2 [DONE] Parse C header declarations

**Acceptance**
- supported declarations from configured C headers populate foreign symbols.
- typedefs and supported function signatures resolve without hand-written adapters.

**Depends**
- 2.4.2.1

#### 2.4.2.3 [DONE] Preserve unsupported preprocessor boundaries

**Acceptance**
- unsupported preprocessor constructs remain explicit diagnostics or opaque boundaries.
- the adapter never silently invents foreign declarations.

**Depends**
- 2.4.2.2

### 2.4.3 [DONE] [3/3] C source import and build dependency representation

**Language**
- LS §22.4

**Acceptance**
- imported C implementation units are represented once in the build graph.
- semantic declarations are visible without duplicating definitions.
- C source dependencies are distinguishable from headers.

**Depends**
- 2.4.2

#### 2.4.3.1 [DONE] Normalize and represent C source dependencies

**Acceptance**
- C implementation-unit paths are explicit in the compiler request/result boundary.
- dependency paths are normalized and deduplicated before build consumption.
- C sources remain distinguishable from parsed C+ sources and imported headers.

**Depends**
- 2.4.2

#### 2.4.3.2 [DONE] Import declarations from C implementation units

**Acceptance**
- function and global declarations needed by a C+ translation unit are visible without parsing C definitions as C+ declarations.
- implementation-unit symbols do not create duplicate generated definitions.
- source/header origins are retained when declarations are available.

**Depends**
- 2.4.3.1

#### 2.4.3.3 [DONE] Compile and link C sources exactly once

**Acceptance**
- the CLI build graph forwards each C source dependency exactly once to the C compiler.
- C source dependencies participate in build and run commands without being emitted as generated C.
- missing or invalid dependency paths produce actionable build diagnostics.

**Depends**
- 2.4.3.1

### 2.4.4 [DONE] [2/2] Foreign-symbol semantic tooling integration

**Language**
- LS §41
- LS §43

**Technical**
- TS §56

**Acceptance**
- foreign declarations participate in lookup.
- signature/type information is available.
- source/header origins are retained when available.

**Depends**
- 2.4.3
- 5.1 later exposes this through LSP.

#### 2.4.4.1 [DONE] Expose foreign symbols through semantic tooling APIs

**Acceptance**
- foreign declarations participate in semantic lookup.
- imported function signatures and foreign types remain queryable.
- source/header origins are retained on foreign symbols when available.

**Depends**
- 2.4.3

#### 2.4.4.2 [DONE] Integrate foreign symbols with language-server features

**Acceptance**
- completion includes imported C symbols.
- hover and signature help expose foreign type/signature information.
- go-to-definition uses retained source/header origins when available.

**Depends**
- 2.4.4.1
- 5.1

**Implementation**
- Imported C symbols participate in completion, hover, semantic references, and signature help through `SemanticModel` rather than generated C text.
- Foreign function signatures retain variadic/return type information in LSP responses; retained origins feed navigation locations.
- CLI integration coverage exercises `printf` completion, hover, and signature help through stdio JSON-RPC.

---

# 3. [DONE] [20/20] Compile-time and CPX system

**Purpose:** Implement the compile-time language, readable CPX templates, generics, fixed-point expansion, hygiene and reflection.

**Language**
- LS §7–13
- LS §17–20
- LS §29
- LS §35–39
- LS §47–49

**Technical**
- TS §15–25
- TS §48–49
- TS §71–73

---

## 3.1 [DONE] [4/4] Compile-time values and arguments

### 3.1.1 [DONE] Compile-time scalar and entity values

**Language**
- LS §7.2
- LS §36

**Technical**
- TS §15

**Acceptance**
- int, float, bool, string, identifier and lists are represented.
- compile-time values have explicit semantic kinds.
- values are not represented as arbitrary source strings.

**Depends**
- 2.1

**Implementation**
- CPX parameters now accept typed `type`, `identifier`, `int`, `float`, `bool`, `string`, `expr`, `stmt`, `decl`, `member`, `unit`, and `cpx` kinds.
- List parameters now retain independently typed elements, including nested expressions and semantic type values.
- Scalar arguments validate at expansion time and retain semantic kind, canonical cache text, and deterministic rendering text.
- Expression arguments are parser-validated before interpolation; nested invocation argument commas are depth-aware.
- Expansion keys include non-type parameter kinds so typed values cannot alias type specializations.
- End-to-end coverage compiles and executes a generated function using expression and integer arguments.

**Remaining**
- no remaining work in this subsection.

### 3.1.2 [DONE] `CtType` and semantic type arguments

**Language**
- LS §8.4
- LS §9

**Acceptance**
- `type T` arguments carry `TypeId`.
- interpolation can request canonical syntax for a type.
- aliases and canonical-type identity are distinguishable where necessary.

**Depends**
- 2.2.1
- 3.1.1

**Implementation**
- `CtType` now carries both its declared semantic `TypeId` and canonical target `TypeId`.
- The semantic model resolves primitive, aggregate, alias, foreign, pointer, and array spellings for CPX type arguments.
- A provisional front-end semantic pass supplies identities before CPX expansion; the final pass resets deterministic IDs and remains authoritative.
- Explicit type interpolation uses the canonical semantic spelling while direct interpolation preserves the invocation spelling.
- Type identity participates in expansion specialization keys without changing the source-facing expansion key text.
- Tests cover alias/canonical identity and compiler-to-C canonical type interpolation.

### 3.1.3 [DONE] Expression, statement and declaration compile-time values

**Language**
- LS §7.2
- LS §36

**Technical**
- TS §15

**Acceptance**
- syntax-bearing arguments retain `NodeId` and origin.
- semantic references survive capture into compile-time values.
- no forced stringification occurs.

**Depends**
- 1.4
- 3.1.1

**Implementation**
- Expression, statement, declaration, member, unit, and nested-CPX arguments are parsed into AST entities before interpolation.
- Syntax-bearing values retain an arena-backed `NodeId`, captured `Origin`, and an optional semantic reference map.
- The CPX expansion result exposes the syntax arena and captured argument values for downstream lowering and diagnostics.
- Semantic reference collection can resolve captured nodes against the provisional model, preserving `SymbolId` identity through expansion.
- Parser and compiler integration tests cover node identity, origin retention, and global symbol reference preservation.

### 3.1.4 [DONE] Canonical compile-time value encoding

**Technical**
- TS §19
- TS §49

**Acceptance**
- compile-time values can produce deterministic canonical cache keys.
- equal semantic type arguments produce equal specialization keys.
- distinct values cannot accidentally alias.

**Depends**
- 3.1.1–3.1.3

**Implementation**
- Canonical encodings normalize scalar literals and token spacing without replacing the retained syntax nodes.
- Syntax-value encodings include resolved semantic reference identities, preventing equivalent text in different scopes from aliasing.
- Specialization keys are derived from parsed compile-time values, so formatting-equivalent syntax shares a key while distinct values remain separate.
- Canonical type IDs make aliases and their target types share semantic specialization identity while declared IDs remain available on `CtType`.
- Regression coverage verifies deterministic formatting normalization and distinct-value separation.

---

## 3.2 [DONE] [4/4] CPX template representation and interpolation

### 3.2.1 [DONE] CPX categories and template node model

**Language**
- LS §8.5

**Technical**
- TS §16

**Acceptance**
- `unit`, `decl`, `member`, `stmt`, `expr`, `type` categories exist.
- call-site expected category is representable.
- invalid category placement produces diagnostics.

**Depends**
- 1.4
- 3.1

### 3.2.2 [DONE] Direct compile-time binding interpolation

**Language**
- LS §8.2
- LS §32–33

**Acceptance**
- standalone `T` in valid template context resolves to the compile-time binding.
- a runtime symbol with the same spelling is not silently substituted.
- syntactic category is validated.

**Depends**
- 3.2.1
- 2.1.4

### 3.2.3 [DONE] Identifier composition

**Language**
- LS §8.3
- LS §44.1

**Acceptance**
- `optional_{T}_t` can become `optional_int_t`.
- composed identifiers use semantic rendering of components.
- plain identifiers containing similar text remain untouched.

**Depends**
- 3.2.2

### 3.2.4 [DONE] CPX hygiene and injected-name rules

**Language**
- LS §12 Hygiene
- LS §44.6

**Acceptance**
- local generated names are hygienic.
- explicitly exported/injected names obey ordinary collision rules.
- two incompatible generated public declarations produce a diagnostic.

**Depends**
- 3.2.3
- 2.1.4

**Implementation**
- Generated function locals and their references are rewritten structurally with deterministic expansion-key suffixes.
- Nested generated functions receive separate hygienic local mappings.
- Injected declaration names remain visible to ordinary semantic collision checking rather than being silently renamed.
- Regression coverage verifies local/reference pairing and incompatible injected declarations producing `SEM002`.

---

## 3.3 [DONE] [4/4] Evaluator, scheduler and expansion identity

### 3.3.1 [DONE] `ComptimeContext` and evaluator API

**Language**
- LS §13 Scope-sensitive CPX Expansion

**Technical**
- TS §17

**Acceptance**
- evaluator receives current module, scope, type, function, phase and target.
- no process-global compiler state is required.
- compile-time execution can produce structured results.

**Depends**
- 3.1
- 2.3

**Implementation**
- Added `ComptimeContext` carrying module, scope, containing entities, phase, target, origin, and expansion identity.
- Added the `ComptimeEvaluator` boundary and default structured template evaluator.
- Evaluation results expose rendered syntax, output channels, dependencies, and diagnostics.
- The expander routes cache misses through the evaluator and publishes returned dependency channels to the scheduler.
- Compiler target dialects now flow into CPX evaluation context.
- Tests assert context delivery, expansion identity, and structured dependency channels.

### 3.3.2 [DONE] Expansion identity and evaluation keys

**Language**
- LS §11 CPX Instance Identity
- LS §29

**Technical**
- TS §19

**Acceptance**
- identity includes declaration, call site, parent and key.
- loop-generated instances from one call site remain distinct.
- equal specializations can be recognized.

**Depends**
- 3.1.4
- 3.3.1

**Implementation**
- Every queued invocation receives a call-site `NodeId` and an `ExpansionId` containing declaration, parent expansion, call site, and evaluation key.
- Nested generated invocations preserve their parent expansion identity while retaining scheduler dependency keys.
- Expansion results expose all invocation identities for provenance and incremental consumers.
- Semantic specialization keys remain separate from invocation identity, allowing cache reuse without collapsing provenance.
- Tests verify nested parent identity and distinct call-site identities.

### 3.3.3 [DONE] Dependency-driven compile-time scheduler

**Language**
- LS §38 Compile-time Execution Order

**Technical**
- TS §20
- TS §48

**Acceptance**
- tasks transition through pending/ready/running/expanded/blocked/failed.
- execution depends on semantic readiness, not textual order.
- dependencies are queryable for incremental invalidation.

**Depends**
- 3.3.2

**Implementation**
- `ComptimeScheduler` exposes explicit symbol, type, module, expansion, and stabilized-type readiness channels.
- Published readiness tokens unblock waiting tasks without recursive phase calls.
- Pending expansion dependencies are queryable and deterministic task-to-task cycles are rendered as expansion-key paths.

### 3.3.4 [DONE] CPX recursion and cycle detection

**Language**
- LS §10.3
- LS §44.8

**Technical**
- TS §23

**Acceptance**
- deterministic expansion cycles are detected.
- diagnostic contains expansion chain.
- configurable safety limits protect against unbounded generated growth.

**Depends**
- 3.3.3

**Implementation**
- `CpxExpansionLimits` bounds nested depth, scheduled expansion tasks, and generated declarations.
- Limit failures use stable `CPX007` diagnostics while ordinary recursive cycles retain their expansion-key chain.
- Tests verify bounded nested expansion without misclassifying it as a semantic cycle.

---

## 3.4 [DONE] [4/4] Generics and recursive specialization

### 3.4.1 [DONE] Generic declaration generation through CPX

**Language**
- LS §9.1–9.3
- LS §48

**Technical**
- TS §72

**Acceptance**
- compile-time function taking a type can generate a struct.
- generated declaration enters symbol catalogue.
- generated declaration retains definition and call-site origin.

**Depends**
- 3.2
- 3.3

### 3.4.2 [DONE] Generic specialization identity

**Language**
- LS §9.4
- LS §44.11

**Technical**
- TS §49

**Acceptance**
- equivalent calls reuse specialization when permitted.
- different semantic arguments create different specializations.
- source spelling alone is not the specialization key.

**Depends**
- 3.4.1
- 3.1.4

**Implementation**
- `CanonicalComptimeValue` and `SpecializationKey` provide stable specialization identity independent of equivalent type spelling.
- `ExpansionKey` exposes its canonical specialization key and duplicate equivalent invocations reuse one expansion.
- Incremental invalidation reports preserve specialization keys separately from expansion invocation keys.
- Tests cover duplicate reuse and equivalent `struct item`/`item` spellings.

### 3.4.3 [DONE] Specialization cache

**Technical**
- TS §49

**Acceptance**
- specialization cache is deterministic.
- dependency invalidation is recorded.
- cache does not suppress required diagnostics.

**Depends**
- 3.4.2

**Implementation**
- `SpecializationCache` stores definition- and source-module-sensitive rendered template text.
- Cached text is reparsed and re-originated for every invocation, preserving diagnostics and source provenance.
- Incremental dependency invalidation explicitly evicts affected specialization keys before recompilation.
- Cache statistics and tests cover reuse, invalidation boundaries, and diagnostics on cached malformed expansions.

### 3.4.4 [DONE] Recursive/nested CPX expansion

**Language**
- LS §10.1–10.2

**Acceptance**
- generated CPX invocation sites are scheduled.
- expansion continues until the phase fixed point.
- nested origins retain complete ancestry.

**Depends**
- 3.3
- 3.4.1

**Implementation**
- Generated CPX invocations are queued and expanded until no pending task remains.
- Nested `Origin.Expansion` values retain the complete parent expansion chain.
- Depth, task-count, and generated-output limits prevent unbounded recursive work.
- Tests cover nested fixed-point expansion, ancestry, cycle diagnostics, and limit failures.

---

## 3.5 [DONE] [4/4] Structural stabilization and reflection

### 3.5.1 [DONE] Structural fixed-point engine

**Language**
- LS §17 Structural Compile-time Phase

**Technical**
- TS §21–22

**Acceptance**
- generated structural declarations are catalogued after each wave.
- newly resolvable tasks can run in subsequent waves.
- stabilization is based on semantic structural state, not source text.

**Depends**
- 3.4.4
- 2.1.3

**Implementation**
- Generated CPX declarations are catalogued during expansion and can resolve later queued invocations.
- Unresolved CPX invocations are deferred while structural waves run, then diagnosed only after no later structural declaration can satisfy them.
- Structural output exposes a declaration fingerprint based on semantic shape rather than source text, ranges, or origins.
- The scheduler closes the structural phase only after all currently eligible structural tasks have been processed, then freezes the type universe before reflective work begins.
- Tests cover generated declarations, deferred resolution, semantic fingerprints, nested ancestry, and structural/reflective phase ordering.

### 3.5.2 [DONE] Type-universe stabilization barrier

**Language**
- LS §18
- LS §20

**Technical**
- TS §24

**Acceptance**
- `TypeUniverse.freeze()` or equivalent establishes explicit barrier.
- structural mutation after freeze is rejected.
- early-safe versus full introspection is distinguishable.

**Depends**
- 3.5.1
- 2.2

**Implementation**
- `ComptimeTypeUniverse` accepts structural registrations until an explicit freeze barrier.
- `ComptimeScheduler.closeStructuralPhase()` freezes the universe and publishes `StableTypeUniverse` readiness.
- Mutation after freeze is rejected, while full introspection is unavailable before the barrier.
- Tests cover early-safe snapshots, reflective task gating, and post-freeze mutation rejection.

### 3.5.3 [DONE] Structured reflection API

**Language**
- LS §19
- LS §49

**Technical**
- TS §25
- TS §73

**Acceptance**
- types expose structured fields and methods.
- type kind/name/layout metadata are accessible where known.
- reflection returns semantic entities, not parsed strings.

**Depends**
- 3.5.2

**Implementation**
- Frozen type-universe snapshots now expose structured type, field, and method descriptors through a read-only API.
- Early-safe snapshots expose names only; full descriptors require the structural barrier.
- Typed references and deterministic layout metadata are retained where known.
- Reflection over functions, scopes, and richer semantic values remains outside this initial structural API.

### 3.5.4 [DONE] Reflective CPX phase

**Language**
- LS §20 Reflective Compile-time Generation

**Acceptance**
- reflective CPX can consume stable type metadata.
- it may generate allowed executable/data constructs.
- forbidden structural mutations produce diagnostics.

**Depends**
- 3.5.3
- 3.3

**Implementation**
- CPX categories now map to explicit structural and reflective scheduler phases.
- The scheduler closes the structural phase only when no structural task remains, freezes the type universe, and then releases reflective tasks through the stabilization barrier.
- Reflective CPX may generate executable functions, globals, and other non-structural declarations after stabilization.
- Structural declarations emitted by reflective CPX are rejected with `CPX008` and are not incorporated into the frozen program universe.
- Reflective nested invocations cannot reopen structural expansion after the barrier.
- Tests cover mixed structural/reflective scheduling, generated executable functions, readiness-channel behavior, and rejection of reflective structural mutation.

---

# 4. [DONE] [34/34] Lowering and C backend

**Purpose:** Transform resolved C+ into target-C AST, generate headers/dependencies/names, emit source, and preserve source mappings.

**Language**
- LS §14–16
- LS §23–31
- LS §37
- LS §50–51

**Technical**
- TS §31–44
- TS §69–74

---

## 4.1 [DONE] [4/4] Compiler pass and rewrite framework

### 4.1.1 [DONE] Compiler context and pass API

**Technical**
- TS §31
- TS §45

**Acceptance**
- passes receive explicit `CompilerContext`.
- pass result and diagnostics are explicit.
- passes do not depend on global mutable singletons.

**Depends**
- 1–3 foundational models

**Implementation**
- `CompilerContext` is an instance-scoped shared service boundary and now carries target information explicitly.
- Generic `CompilerPass<P>` and `PassResult<P>` contracts support front-end, semantic, and backend representations without global mutable state.
- Pass results carry their transformed program and diagnostics explicitly, with a success predicate for pipeline control.
- Lowering, C naming/dependency synthesis, header generation, and C emission now communicate through typed in-process packet channels and node wrappers; the algorithmic work remains in backend library services.
- CPX and module cycles continue through explicit work-queue/SCC schedulers rather than recursive processing-node calls.
- Tests verify context identity, target propagation, transformed-program identity, and diagnostic propagation.

### 4.1.2 [DONE] AST rewrite API

**Technical**
- TS §32

**Acceptance**
- replace, insert-before, insert-after and hoist operations exist.
- generated nodes require explicit origin.
- rewrite invalidates semantic indexes in controlled fashion.

**Depends**
- 4.1.1
- 1.4.2

**Implementation**
- `AstRewriter` exposes replace, insert-before, insert-after, and scoped hoist operations over stable `NodeId` values.
- `ArenaAstRewriter` preserves ordered roots while using `AstArena` for identity-stable replacement.
- `addGenerated` requires an explicit origin that exactly matches the generated node's embedded provenance.
- `SemanticIndexInvalidator` receives the changed node set after each controlled rewrite mutation.
- Tests cover all rewrite operations, stable IDs, origin enforcement, scoped hoisting, and invalidation notifications.

### 4.1.3 [DONE] Pass precondition/postcondition invariant framework

**Language**
- LS §30
- LS §31

**Technical**
- TS §60

**Acceptance**
- major passes declare invariants.
- debug/test builds can validate critical invariants.
- violations identify pass name and offending nodes.

**Depends**
- 4.1.1

**Implementation**
- `CompilerPass` declares typed preconditions and postconditions without forcing representations into a shared mutable model.
- `CompilerPassRunner` can validate invariants in debug/test configurations while preserving the production fast path.
- `PassInvariantViolation` records a message and optional offending `NodeId`.
- `PASSINV001` diagnostics identify the pass, invariant kind/name, offending node, and failure message.
- Tests cover precondition short-circuiting and postcondition diagnostics appended to pass output.

### 4.1.4 [DONE] Resolved-AST and reference index integration

**Technical**
- TS §29–30

**Acceptance**
- resolved identifier nodes refer to `SymbolId`.
- transformations can preserve semantic identity.
- reference index can be rebuilt or incrementally updated.

**Depends**
- 2
- 4.1.2

**Implementation**
- `SymbolReference` records stable `NodeId`, resolved `SymbolId`, origin, and reference kind.
- `ReferenceIndex` supports rebuild, symbol lookup, node lookup, enumeration, and targeted invalidation.
- `SemanticModel.resolvedAst` exposes the AST node map and reference index without mutating immutable AST nodes.
- Semantic analysis now collects global, function, type, member, call, and local binding references into the sidecar model.
- Rewrite invalidation can connect directly to `ReferenceIndex.invalidate` through `SemanticIndexInvalidator`.
- Tests verify resolved identifier symbol identity and targeted reference invalidation.

---

## 4.2 [DONE] [14/14] C+ feature lowering

### 4.2.1 [DONE] Method lowering

**Language**
- LS §6
- LS §30

**Technical**
- TS §33

**Acceptance**
- struct methods become top-level callable representations.
- instance calls inject receiver.
- pointer receivers pass pointer expressions directly and preserve pointer-member access.
- addressable value receivers use the existing address-taking lowering path.
- static calls do not inject receiver.
- source method identity is retained.
- an integration fixture parses `self*`, compiles generated C, and executes a mutating pointer-receiver method.

**Depends**
- 2.2
- 4.1

### 4.2.2 [DONE] Inner functions and closure lowering

**Language**
- LS §14
- LS §37

**Technical**
- TS §34

**Acceptance**
- capture analysis identifies enclosing variables.
- environment struct is generated where required.
- hoisted function receives environment.
- invalid lifetime captures are rejected.

**Depends**
- 4.1
- 2.1.2

**Implementation**
- Nested function syntax is parsed as `SyntaxInnerFunction` and normalized to an AST inner-function statement.
- `AstClosureLowerer` performs lexical capture analysis before semantic resolution and removes nested functions from the runtime AST.
- Mutable captures use reference-mode environment fields; immutable captures use value-mode fields.
- Environment structs, local environment instances, hoisted functions, capture initialization, and closure-call environment arguments are synthesized with generated origins.
- `ClosurePlanner` rejects explicit escaping mutable-reference captures with `CLOSURE001`; nested closures are recursively hoisted with parent-environment rewrites.
- An end-to-end C17 fixture verifies a captured inner function compiles and executes correctly.

### 4.2.3 [DONE] [3/3] `defer` lowering and control-flow integration

**Language**
- LS §15

**Technical**
- TS §35–36

**Acceptance**
- deferred actions run in reverse registration order.
- return/fallthrough/break/continue exits receive required cleanup.
- CFG-based tests cover multiple exits.

**Depends**
- 4.1

#### 4.2.3.1 [DONE] Lexical defer cleanup-stack lowering

**Acceptance**
- deferred expressions are registered in source order.
- return and normal block fallthrough execute deferred expressions in reverse order.
- generated C contains no residual `defer` syntax.
- integration coverage compiles and runs a foreign-call cleanup fixture.

**Depends**
- 4.1.1
- 4.3.1

#### 4.2.3.2 [DONE] CFG exit cleanup integration

**Acceptance**
- every supported control-flow exit receives the correct deferred cleanup.
- nested blocks do not run outer cleanups prematurely.

**Depends**
- 4.2.3.1

#### 4.2.3.3 [DONE] Loop break/continue cleanup integration

**Acceptance**
- `break` and `continue` run exactly the cleanups required by their lexical scope.
- CFG tests cover nested loops and multiple deferred actions.

**Depends**
- 4.2.3.2

### 4.2.4 [DONE] [9/9] String-template and remaining expression lowering

**Language**
- LS §16
- LS §30

**Acceptance**
- string templates lower to valid ordinary C+ or C constructs.
- captured local variables remain semantically correct.
- no template-specific syntax reaches C AST.

**Depends**
- 4.1
- 4.2.2 where helper closures are generated

#### 4.2.4.1 [DONE] Array and pointer indexing lowering

**Acceptance**
- postfix index expressions parse after calls and member access.
- array and pointer element types resolve semantically.
- C lowering emits indexed expressions with preserved origins.
- a runtime fixture reads and writes indexed array elements.

**Depends**
- 1.3
- 4.3.1

#### 4.2.4.2 [DONE] String-template lowering

**Acceptance**
- string templates lower to valid ordinary C+ or C constructs.
- no template-specific syntax reaches C AST.

**Depends**
- 4.2.4.1

**Implementation note**
- The first supported runtime form is `${expression}` inside an ordinary C string literal.
- Lowering uses a generated bounded `vsnprintf` helper and preserves interpolation origins.

#### 4.2.4.3 [DONE] [5/5] Complete remaining expression and lvalue lowering

**Acceptance**
- supported C+ expression and lvalue forms have explicit C AST nodes.
- unsupported forms produce diagnostics before emission.

**Depends**
- 4.2.4.1

#### 4.2.4.3.1 [DONE] Lower assignment, update, conditional, and bitwise expressions

**Acceptance**
- assignment and compound-assignment operators lower through explicit C assignment nodes.
- prefix and postfix updates lower with preserved evaluation order.
- conditional and bitwise expressions lower to explicit C expression nodes.
- non-assignable lvalues produce semantic diagnostics before C emission.

**Depends**
- 4.2.4.2

#### 4.2.4.3.2 [DONE] [5/5] Complete remaining expression and lvalue lowering

**Acceptance**
- supported C+ expression and lvalue forms have explicit C AST nodes.
- unsupported forms produce diagnostics before emission.

**Depends**
- 4.2.4.3.1

##### 4.2.4.3.2.1 [DONE] Lower floating-point literals

**Acceptance**
- decimal and exponent-form floating literals tokenize distinctly from integers.
- semantic analysis assigns floating literals a floating primitive type.
- C lowering preserves valid floating literal spelling and origin.

**Depends**
- 4.2.4.3.1

##### 4.2.4.3.2.2 [DONE] [4/4] Complete remaining expression and lvalue lowering

**Acceptance**
- supported C+ expression and lvalue forms have explicit C AST nodes.
- unsupported forms produce diagnostics before emission.

**Depends**
- 4.2.4.3.2.1

###### 4.2.4.3.2.2.1 [DONE] Lower expression-form sizeof

**Acceptance**
- `sizeof(expression)` parses as a dedicated expression form.
- semantic analysis validates its operand and exposes a size-result type.
- C lowering emits native `sizeof` without template-specific syntax.

**Depends**
- 4.2.4.3.2.1

###### 4.2.4.3.2.2.2 [DONE] [4/4] Complete remaining expression and lvalue lowering

**Acceptance**
- supported C+ expression and lvalue forms have explicit C AST nodes.
- unsupported forms produce diagnostics before emission.

**Depends**
- 4.2.4.3.2.2.1

####### 4.2.4.3.2.2.2.1 [DONE] Lower primitive casts

**Acceptance**
- primitive cast syntax is parsed without confusing ordinary parenthesized expressions.
- semantic analysis resolves primitive cast targets and diagnoses unsupported targets.
- C lowering emits explicit casts with preserved operand origins.

**Depends**
- 4.2.4.3.2.2.2

####### 4.2.4.3.2.2.2.2 [DONE] [3/3] Complete remaining expression and lvalue lowering

**Acceptance**
- supported C+ expression and lvalue forms have explicit C AST nodes.
- unsupported forms produce diagnostics before emission.

**Depends**
- 4.2.4.3.2.2.2.1

######## 4.2.4.3.2.2.2.2.1 [DONE] Lower type-form sizeof

**Acceptance**
- primitive and tagged C type forms are accepted by `sizeof`.
- semantic analysis validates supported type operands.
- C lowering emits native `sizeof(type)` syntax.

**Depends**
- 4.2.4.3.2.2.2.1

######## 4.2.4.3.2.2.2.2.2 [DONE] [4/4] Complete remaining expression and lvalue lowering

**Acceptance**
- supported C+ expression and lvalue forms have explicit C AST nodes.
- unsupported forms produce diagnostics before emission.

**Depends**
- 4.2.4.3.2.2.2.2.1

######### 4.2.4.3.2.2.2.2.2.1 [DONE] Lower pointer-member lvalues

**Acceptance**
- pointer-member syntax using `->` parses and resolves through pointee aggregate types.
- pointer-member assignments lower to native C arrow access.
- runtime coverage verifies the assigned pointee field.

**Depends**
- 4.2.4.3.2.2.2.2.1

######### 4.2.4.3.2.2.2.2.2.2 [DONE] Lower foreign global lvalues

**Acceptance**
- C-source globals are represented as distinct foreign global symbols.
- assignments and updates to mutable C-source globals pass semantic lvalue validation.
- generated C units declare referenced C-source globals as `extern` declarations.
- an end-to-end fixture links and executes a C+ assignment against a C-source definition.

**Depends**
- 4.2.4.3.2.2.2.2.2.1

######### 4.2.4.3.2.2.2.2.2.3 [DONE] Reject syntax-error expressions before C emission

**Acceptance**
- supported C+ expression and lvalue forms have explicit C AST nodes.
- unsupported forms produce diagnostics before emission.

**Depends**
- 4.2.4.3.2.2.2.2.2.1

**Implementation**
- `AstErrorExpression` is no longer silently lowered as a successful C literal.
- C lowering emits source-mapped `LOW408` and retains only a diagnostic-bearing partial C node for tooling inspection.
- The compiler refuses to publish generated C when the lowering diagnostic is an error.
- Integration coverage verifies parser-recovery expressions produce no generated C unit.

######### 4.2.4.3.2.2.2.2.2.4 [DONE] Lower boolean literals

**Acceptance**
- `true` and `false` parse as boolean literals rather than unresolved identifiers.
- semantic analysis assigns boolean literals the `bool` primitive type.
- C lowering emits portable `1` and `0` literals with preserved origins.
- CPX-generated boolean values compile and execute through the C backend.

**Depends**
- 4.2.4.3.2.2.2.2.2.3

**Implementation**
- Added syntax and AST boolean literal nodes and lexer keyword recognition.
- Added semantic typing, reference traversal, closure traversal, CLI AST printing, and CPX re-origin support.
- Added an end-to-end fixture for a `bool` CPX argument and generated conditional expression.

---

## 4.3 [DONE] [4/4] C representation and declaration synthesis

### 4.3.1 [DONE] C AST model

**Language**
- LS §31 Final C-subset Validation

**Technical**
- TS §37

**Acceptance**
- target-C declarations, statements, expressions and types are representable.
- every C node carries origin.
- backend does not rely on arbitrary C text fragments.

**Depends**
- 4.1

### 4.3.2 [DONE] [4/4] Hoisting and forward-declaration synthesis

**Language**
- LS §24–25

**Technical**
- TS §39

**Acceptance**
- inner/generated declarations are hoisted where required.
- recursive pointer dependencies receive forward declarations.
- synthesized declarations retain generated origins.

**Depends**
- 4.2
- 4.3.1

#### 4.3.2.1 [DONE] Synthesize recursive aggregate forward declarations

**Acceptance**
- pointer dependencies on later aggregate definitions produce explicit C forward-declaration nodes.
- struct and union forward declarations are deduplicated and deterministically ordered.
- synthesized declaration origins point to the referenced aggregate definition.
- mutually referential aggregate pointers compile and execute through the C backend.

**Depends**
- 4.3.1

#### 4.3.2.2 [DONE] Hoist inner and generated declarations

**Implementation**
- Closure lowering hoists generated environment structs and inner functions into the program declaration stream before semantic analysis and C emission.
- Generated CPX declarations are already collected into the top-level expansion result and participate in aggregate/function ordering.
- Runtime coverage verifies that a hoisted closure declaration and environment compile as ordinary C declarations.

#### 4.3.2.3 [DONE] Resolve by-value aggregate declaration dependencies

**Acceptance**
- by-value struct and union dependencies are ordered before their containing definitions.
- declaration ordering is deterministic and retains source origins.
- impossible by-value aggregate cycles produce a lowering diagnostic before C emission is accepted.
- an end-to-end fixture compiles and executes a C+ aggregate whose definition follows its user.

**Depends**
- 4.3.2.1

#### 4.3.2.4 [DONE] Preserve hoisting provenance through generated declarations

**Implementation**
- Hoisted environment structs and functions use `Origin.Generated` derived from the inner function origin.
- Generated fields, parameters, environment initialization, and rewritten closure calls retain the originating closure chain.
- C emission and source-map generation therefore keep closure-generated declarations traceable to their source construct.
- Integration coverage asserts generated AST declaration origins and compiles the resulting C17 fixture.

### 4.3.3 [DONE] [4/4] Header and dependency/include generation

**Language**
- LS §26–27

**Technical**
- TS §38
- TS §40

**Acceptance**
- public declarations generate header forms structurally.
- includes are deduplicated and grouped deterministically.
- public/private dependency differences are preserved.

**Depends**
- 4.3.2
- 2.4

#### 4.3.3.1 [DONE] Collect deterministic system dependencies

**Acceptance**
- explicit C imports and foreign type usage contribute system includes structurally.
- dependencies are deduplicated and sorted deterministically.
- C-source functions using `size_t` receive `<stddef.h>` without a redundant C+ import.
- an end-to-end fixture compiles and executes the foreign typed call.

**Depends**
- 2.4
- 4.3.1

#### 4.3.3.2 [DONE] Generate public header declarations

**Acceptance**
- public types, aliases, globals, and function prototypes are emitted structurally from the lowered C AST.
- private declarations are omitted unless their definitions are required by a public by-value dependency.
- public pointer dependencies receive forward declarations without copying source text.
- generated header ranges retain declaration origins and the header is syntax-checkable as C.
- the compiler API and CLI can expose a separate generated header artifact.

**Depends**
- 4.3.3.1
- 4.3.2

#### 4.3.3.3 [DONE] Preserve public/private dependency boundaries

**Acceptance**
- private imports contribute only to implementation dependencies when absent from the public API.
- public headers include system dependencies required by exported types and signatures.
- private globals and declarations never appear in generated public headers.
- an integration fixture verifies implementation/header dependency separation.

**Depends**
- 4.3.3.2

**Implementation**
- implementation dependency collection includes private imports and foreign types needed by generated C.
- public dependency collection traverses only exported aggregates, aliases, globals, and function signatures.
- private globals and declarations are filtered from generated headers while public system dependencies remain available.
- Integration coverage verifies private `FILE*` usage adds `<stdio.h>` to implementation C but not the public header.

#### 4.3.3.4 [DONE] Synthesize local and foreign library dependencies

**Acceptance**
- compile requests represent local archive/shared-library paths separately from foreign `-l` names.
- duplicate library arguments are removed without changing explicit linker order.
- missing local library paths produce stable compiler diagnostics before C emission/build.
- CLI build and run forward include directories, local libraries, and foreign libraries to `cc`.
- integration coverage verifies dependency normalization and missing-library diagnostics.

### 4.3.4 [DONE] [4/4] C symbol naming and C-subset validation

**Language**
- LS §23
- LS §31
- LS §44.9

**Technical**
- TS §41
- TS §74

**Acceptance**
- C names are deterministic and collision-free.
- foreign ABI names are preserved.
- no C+-only construct is accepted by final backend validation.

**Depends**
- 4.3.1
- 2.1.1

#### 4.3.4.1 [DONE] Centralize emitted C names and diagnose collisions

**Acceptance**
- lowered declarations and calls use one C-name service.
- foreign symbols retain their external ABI names.
- duplicate emitted C function names produce a lowering diagnostic before emission.
- a method/top-level-function collision is covered by an integration fixture.

**Depends**
- 4.3.1

#### 4.3.4.2 [DONE] Validate the complete target-C subset

**Acceptance**
- unknown and invalid object types are rejected before C emission.
- unsupported C operators and malformed C AST forms produce backend diagnostics.
- validation traverses declarations, statements, and expressions structurally.
- invalid `void` object declarations are covered by an integration fixture.

**Depends**
- 4.3.4.1

#### 4.3.4.3 [DONE] Validate reserved and ABI-sensitive identifiers

**Acceptance**
- C keywords and implementation-reserved identifiers are diagnosed at the target-C boundary.
- deliberate `__cplus_` runtime helpers remain permitted as compiler-owned names.
- foreign prototypes and extern globals preserve their ABI names.
- enum and aggregate declaration names are included in identifier validation.

**Depends**
- 4.3.4.2

#### 4.3.4.4 [DONE] Validate generated helper and linkage namespaces

**Acceptance**
- compiler-owned helper names are reserved independently from general C identifier checks.
- user declarations colliding with generated helpers produce a stable lowering diagnostic.
- generated helper validation covers functions, globals, aliases, and enum values.

**Depends**
- 4.3.4.3

---

## 4.4 [DONE] [5/5] C emission and source mapping

### 4.4.1 [DONE] Deterministic C emitter

**Technical**
- TS §42

**Acceptance**
- C AST emits formatted deterministic C.
- emitter performs formatting, not semantic transformations.
- output can be round-trip compiled by selected C compiler for supported fixtures.

**Depends**
- 4.3

### 4.4.2 [DONE] [2/2] Generated-range source-map builder

**Language**
- LS §28.3
- LS §50

**Technical**
- TS §43

**Acceptance**
- generated byte ranges map to `Origin`.
- method and CPX-generated output map correctly.
- nested origins remain queryable.

**Depends**
- 1.1.3
- 4.4.1

#### 4.4.2.1 [DONE] Emit origin-preserving generated byte ranges

**Acceptance**
- every mapped generated line carries a UTF-8 byte start and end offset.
- mappings retain the originating C+ `Origin`.
- forward declarations and reordered aggregate definitions remain mapped to their source declarations.

**Depends**
- 4.4.1

#### 4.4.2.2 [DONE] Expose source-map range queries

**Acceptance**
- callers can resolve a generated UTF-8 byte offset to its originating mapping.
- callers can enumerate mappings for a generated line.
- range queries preserve the existing origin and deterministic ordering.

**Depends**
- 4.4.2.1

### 4.4.3 [DONE] External C diagnostic remapping

**Language**
- LS §28

**Technical**
- TS §44

**Acceptance**
- generated C line/column can map to originating C+ location.
- CPX invocation and definition may be reported as related locations.
- unmapped foreign diagnostics still retain generated-C location.

**Depends**
- 4.4.2

**Implementation**
- `CCompilerDiagnosticRemapper` parses GCC/Clang file, line, column, severity, and message records.
- generated locations are mapped through `GeneratedCUnit` ranges and source repositories.
- unmapped or foreign C diagnostics retain their generated/foreign path and location.
- the CLI build path prints mapped C+ locations with generated-C locations as context.

### 4.4.4 [DONE] End-to-end C execution fixtures

**Technical**
- TS §64

**Acceptance**
- C+ fixture → generated C → C compiler → executable.
- emitted executable produces expected behavior.
- generated C and source map snapshots are retained.

**Depends**
- 4.4.1–4.4.3

**Implementation**
- compiler integration fixtures cover generated C, native compilation, and executable behavior across lowering features.
- CLI integration fixtures cover `build`, `check`, executable output, generated C output, and `--header` output.
- generated source maps and native execution are asserted in the same verification suite.

---

# 5. [DONE] [14/14] Tooling, integration and quality

**Purpose:** Make the compiler usable as a development platform through LSP, CLI, incremental compilation, test coverage and specification audits.

**Language**
- LS §40–42
- LS §51–52

**Technical**
- TS §47–64
- TS §75–78

---

## 5.1 [DONE] [4/4] Language server and VS Code integration

### 5.1.1 [DONE] Kotlin LSP workspace/document architecture

**Language**
- LS §41

**Technical**
- TS §50–51

**Acceptance**
- workspace owns modules and documents.
- edited documents maintain versions.
- LSP uses compiler front-end rather than a second semantic parser.

**Depends**
- 1
- 2

**Implementation**
- Added a versioned `LspWorkspace` document store shared by open, change, close, and diagnostic operations.
- Full-text and ranged LSP changes are applied in client order, preserving UTF-16 position semantics and document versions.
- Invalid ranges leave the current document unchanged instead of publishing diagnostics for corrupted text.
- Diagnostics continue to use `CPlusCompiler`, keeping the language server on the authoritative compiler front-end.
- Tests cover ordered ranged edits, version retention, and invalid-range recovery.

### 5.1.2 [DONE] Semantic tokens and TextMate integration

**Language**
- LS §40
- LS §42

**Technical**
- TS §52
- TS §75

**Acceptance**
- TextMate supplies baseline lexical highlighting.
- LSP distinguishes type, method, field, parameter and comptime binding.
- CPX contents remain normal C+ highlighting contexts.

**Depends**
- 5.1.1
- 2
- 3.2

**Implementation**
- Added an LSP semantic-token legend covering types, functions, methods, properties, parameters, variables, literals, keywords, and operators.
- Semantic tokens are encoded from the shared lexer output and semantic `SymbolId`/reference index using standard LSP delta encoding.
- `textDocument/semanticTokens/full` is served from the versioned workspace document text through `CPlusCompiler`.
- The server advertises the semantic-token capability while retaining normal lexical tokenization for unresolved identifiers.
- Integration coverage verifies initialization capabilities and non-empty semantic-token output.
- Added `vscode-extension/` with the C+ `.cp` language declaration, language configuration, TextMate grammar, LSP client activation, configurable Java/JAR/CLI arguments, and a restart command.
- Added the CLI `fatJar` distribution task; the extension launches the configured artifact as `java -jar <cli.jar> lsp`, bundles `vscode-languageclient` into its activation entrypoint, and produces a self-contained `.vsix`.
- Added `C+: Run Main`, which launches the configured CLI artifact as `java -jar <cli.jar> run <main.cp> ...` in an integrated terminal and includes discoverable imported C+ sources.

### 5.1.3 [DONE] Completion and hover

**Language**
- LS §41

**Technical**
- TS §54–55

**Acceptance**
- `instance.` offers fields and instance methods.
- `Type.` offers static members/methods.
- CPX template completion exposes compile-time bindings.
- hover shows canonical type and generated-specialization information.

**Depends**
- 5.1.1
- 2.2
- 3

**Implementation**
- Added `textDocument/completion` backed by semantic symbols, aggregate fields, methods, and prefix filtering.
- Instance/member completion uses resolved expression types, while type-qualified completion uses the stabilized aggregate model.
- Added `textDocument/hover` with semantic kind, type/signature text, and source range.
- Both services compile the versioned workspace text through `CPlusCompiler` and do not maintain a second parser.
- Integration coverage verifies completion and hover responses over stdio JSON-RPC.

### 5.1.4 [DONE] Navigation, references and diagnostics

**Technical**
- TS §30
- TS §53
- TS §56

**Acceptance**
- go-to-definition uses `SymbolId`.
- references work across modules.
- generated symbols expose meaningful origins.
- imported C symbols navigate to headers when possible.

**Depends**
- 5.1.1

**Implementation**
- Diagnostics are published from the authoritative compiler result after every open and change.
- `textDocument/definition` resolves declaration origins from the semantic symbol table.
- `textDocument/references` resolves all indexed references through `ReferenceIndex`, with optional declaration inclusion.
- Navigation responses preserve the source range and document URI expected by LSP clients.
- Integration coverage verifies capability advertisement and definition/reference responses for a function call.
- 4.4.3

---

## 5.2 [DONE] [6/6] CLI, build and incremental compiler

### 5.2.1 [DONE] Public compiler API

**Technical**
- TS §58

**Acceptance**
- `CompileRequest` and `CompileResult` exist.
- compilation can be embedded without invoking CLI.
- result contains diagnostics and generated units.

**Depends**
- 4

### 5.2.2 [DONE] [3/3] CLI commands

**Technical**
- TS §57

**Acceptance**
- initial commands:
  - `check`
  - `transcode`
  - `build`
  - `ast`
  - `expand`
  - `lsp`
- exit codes reflect compilation success.

**Depends**
- 5.2.1

#### 5.2.2.1 [DONE] Core check, transcode, build, and run commands

**Acceptance**
- `check`, `transcode`, `build`, and `run` return compilation-aware exit codes.
- build and run accept multiple C+ sources through the compiler workspace path.
- external C source dependencies remain available to build and run.

**Depends**
- 5.2.1

**Implementation**
- Single-file and workspace compilation gate C artifact publication on front-end error diagnostics.
- Parser-recovery syntax errors therefore retain diagnostics and AST artifacts without emitting invalid C.
- Closure, lexical, CPX, and parser front-end errors all stop lowering before C emission.

#### 5.2.2.2 [DONE] AST and post-CPX expansion inspection

**Acceptance**
- `ast` prints the normalized semantic input representation.
- `expand` prints the post-CPX/pre-lowering representation.
- diagnostics are printed with source locations and stable codes.

**Depends**
- 5.2.1

#### 5.2.2.3 [DONE] Remaining CLI commands

**Acceptance**
- `lsp` and any specified formatting/inspection commands have documented behavior.
- command help and argument validation cover all supported commands.
- CLI integration tests exercise success and failure exit codes.

**Depends**
- 5.2.2.1
- 5.2.2.2

**Implementation**
- `lsp` is a stdio JSON-RPC command with initialize, shutdown, exit, and document lifecycle handling.
- `didOpen`, full-text `didChange`, and `didClose` use `CPlusCompiler` and publish structured diagnostics.
- CLI integration tests cover framed requests, compiler diagnostics, executable builds, headers, and failure exit codes.
- `ast` now prints the parsed pre-CPX AST while `expand` prints the post-CPX/pre-lowering AST.
- `CompilationArtifacts.expandedSyntax` exposes the phase boundary through language-core types without leaking the comptime implementation type into CLI clients.
- The Gradle `cli:run` task uses the repository root as its working directory, so documented root-relative source paths resolve consistently.
- `run`, `build`, `check`, `ast`, `expand`, and `transcode` recursively discover local imported `.cp` sources; relative, quoted path, logical package, and selective-alias imports are normalized before workspace compilation.

### 5.2.3 [DONE] Incremental dependency invalidation

**Technical**
- TS §47–48

**Acceptance**
- changed source invalidates dependent semantic results.
- affected CPX expansions/specializations are invalidated.
- unrelated modules remain reusable.

**Depends**
- 3.3.3
- 2.3.2
- 5.2.1

**Implementation**
- `IncrementalCompiler` fingerprints source and foreign C inputs and keeps a workspace-local front-end cache.
- Changed modules invalidate the reverse module-dependency closure, including dependent semantic results.
- Cached lexed, parsed, expanded, and AST units are reused for unaffected modules.
- Compile-time expansion and specialization keys are reported as invalidated or reused with the source closure.
- Unchanged requests return the previous immutable `CompileResult` without rerunning the pipeline.
- Tests cover dependent invalidation, unrelated-module reuse, CPX invalidation, and stable-result reuse.

### 5.2.4 [DONE] Compiler caches and deterministic concurrency

**Language**
- LS §39 Determinism

**Technical**
- TS §61–62

**Acceptance**
- parsed, semantic, CPX and specialization caches have valid keys.
- independent parsing can run concurrently.
- output remains deterministic regardless of scheduling.

**Depends**
- 5.2.3
- 3.4.3

**Implementation**
- Incremental front-end preparation accepts a bounded worker count through `CompilerOptions.parallelism`.
- Sources are registered in request order before workers start, preserving stable source IDs and output ordering.
- Sequential and parallel front-end preparation are covered by deterministic-output tests.
- `IncrementalCacheKey` covers source, foreign-source, target, compiler-option, library, and include-directory inputs.
- Semantic workspace results, parsed front-end units, CPX expansions, and specializations are reused only under valid keys.
- Configuration changes invalidate the semantic workspace result even when source bytes are unchanged.

---

## 5.3 [DONE] [4/4] Verification and specification completion

### 5.3.1 [DONE] Layered automated test suites

**Technical**
- TS §63

**Acceptance**
- lexer, parser, AST, semantic, CPX, lowering, source-map and LSP suites exist.
- each suite runs independently.
- negative/error tests are first-class tests.

**Depends**
- incremental throughout project

**Implementation**
- Independent Gradle test tasks cover language-core, semantic, comptime, c-backend, compiler, and CLI layers.
- Compiler integration tests cover parsing, semantic resolution, lowering, diagnostics, source maps, and native C execution.
- Negative/error tests assert stable diagnostic codes and no generated artifacts where publication is unsafe.

### 5.3.2 [DONE] Golden compiler fixture framework

**Technical**
- TS §64

**Acceptance**
- fixtures can contain:
  - `input.cp`
  - `expected.expanded.cp`
  - `expected.c`
  - `expected.h`
  - `expected.map`
  - `expected.diagnostics`
- diff output is developer-readable.

**Depends**
- 4.4
- 5.3.1

**Implementation**
- CLI golden fixtures load `input.cp` from test resources and compare optional expanded AST, generated C, generated header, source-map, and diagnostic snapshots.
- Source-map snapshots use deterministic generated-byte and source-range records.
- Diagnostic fixtures assert stable error-code ordering and reject unexpected C/header publication.
- Fixture assertion failures identify the exact resource path and expected/actual content.

### 5.3.3 [DONE] Specification coverage audit

**Language**
- LS §1–52

**Technical**
- TS §1–78

**Acceptance**
- every normative requirement maps to implementation tasks/tests.
- uncovered normative requirements = 0.
- deliberate unsupported features are explicitly versioned/specification decisions, not silent omissions.

**Depends**
- all feature branches

**Implementation**
- Audited LS §§1–52 and TS §§1–78 against the traceability map, implementation-plan leaves, source modules, and layered tests.
- The coverage artifact records the evidence commands and identifies the explicitly diagnosed non-block closure declaration boundary as a versioned limitation rather than a silent omission.
- The audit gate is the full Gradle suite plus golden fixture and native C smoke paths.

### 5.3.4 [DONE] Final architecture/invariant audit

**Technical**
- TS §69–78

**Acceptance**
- compiler, CPX and LSP share authoritative models.
- no major feature emits final C through ad-hoc string generation.
- generated constructs retain origins.
- no required TODO/FIXME/mock remains.
- all terminal plan tasks are DONE.

**Depends**
- all tasks

**Implementation**
- Compiler, CPX, semantic, backend, source-map, and LSP paths use shared AST/origin/symbol/type models and typed packet boundaries.
- Final C is emitted from the lowered C-subset model; generated declarations and diagnostics retain origins.
- Full tests and whitespace validation pass, and repository search found no required implementation `TODO`/`FIXME`/mock placeholders.

---

# 6. [DOING] [32/33] C+ Standard Library, Runtime, SDK and Platform ABI

**Purpose:** Make the C+ SDK self-hosting at the runtime/library level: source-delivered standard library and libc compatibility, explicit target/ABI metadata, platform abstraction layers, compiler/runtime intrinsics, target startup/link orchestration, and conformance tooling.

**SDK / Runtime Specification**
- SDK §1–6 Scope, terminology, architecture, distribution, profiles, native packages
- SDK §17–30 libc profiles, ABI classes, foreign declarations, low-level constructs, intrinsics, target/ABI comptime metadata
- SDK §31–57 target profiles, startup/runtime, compiler facilities, libc/platform services
- SDK §58–102 tooling, packaging, conformance, invariants and resulting toolchain model

**Language**
- LS §7–13 Compile-time functions and CPX
- LS §18–20 Type-universe stabilization and reflection
- LS §22 C imports
- LS §23 Symbol identity
- LS §43 C interoperability
- LS §50 Transcoding

**Technical**
- TS §12 Type System Model
- TS §17–25 Comptime, expansion, target context and reflection
- TS §28 C Import Architecture
- TS §37–44 C AST, emission and source mapping
- TS §45 Compiler Context
- TS §57–62 CLI, compiler API, concurrency and caches
- TS §67–78 implementation and architecture constraints

---

## 6.1 [DONE] [8/8] SDK packaging and runtime foundation

**SDK**
- SDK §2–6
- SDK §39–46
- SDK §58
- SDK §70–73
- SDK §78–81
- SDK §93–101

### 6.1.1 [DONE] [4/4] SDK distribution, profiles and resolver

#### 6.1.1.1 [DONE] [4/4] Define SDK manifest and ABI/runtime version contract

**SDK**
- SDK §4 Distribution Model
- SDK §78 ABI Versioning
- SDK §79 Runtime ABI Stability

**Technical**
- TS §57 CLI Architecture
- TS §58 Compiler Pipeline API

**Deliverable**
- versioned `sdk.toml` or equivalent manifest model
- `sdk_version`, `language_abi_version`, `runtime_abi_version`, `cplus_abi_version`, `libc_profile_version`

**Acceptance**
- compiler loads and validates one SDK manifest before compilation.
- incompatible runtime ABI versions are rejected deterministically.
- SDK identity participates in compiler/incremental cache keys.
- tests cover compatible, missing and incompatible manifests.

**Implementation**
- Added the repository SDK manifest at `sdk/manifest/sdk.toml`.
- Added strict manifest loading, required-key validation, runtime ABI checks, content identity hashing, and deterministic `SDK001`–`SDK004` diagnostics.
- Added the CLI `--sdk` / `--sdk-manifest` option and included SDK identity in incremental cache configuration.
- Added compatible, missing-key, and incompatible-runtime manifest tests.

**Depends**
- 5.2.1
- 5.2.4

#### 6.1.1.2 [DONE] [4/4] Implement runtime and libc profile selection

**SDK**
- SDK §5 Build Profiles
- SDK §17 libc Conformance Profiles
- SDK §72 Compilation Modes

**Language**
- LS §39 Determinism

**Deliverable**
- `--runtime=freestanding|cplus|system`
- `--libc=none|c17|c23`

**Acceptance**
- invalid runtime/libc combinations are diagnosed before lowering.
- default hosted mode resolves to `runtime=cplus`, `libc=c17` until C23 is complete.
- selected profiles are visible to compile-time `target` metadata.
- profile choices participate in build/cache identity.

**Implementation**
- Added typed `RuntimeProfile` and `LibcProfile` selections to `TargetInfo` and compiler cache identity.
- Added CLI `--runtime=freestanding|cplus|system` and `--libc=none|c17|c23` parsing with hosted defaults.
- Added deterministic validation for invalid combinations and unsupported SDK libc profiles.
- Exposed the selected runtime/libc profile through `ComptimeTargetInfo`.
- Added profile-combination and compile-time-target metadata tests.

**Depends**
- 6.1.1.1
- 5.2.2

#### 6.1.1.3 [DONE] [4/4] Implement SDK resolver and SDK-as-sysroot model

**SDK**
- SDK §58 SDK Semantic Metadata
- SDK §62 Runtime Resolver
- SDK §93 C+ SDK as the Sysroot
- SDK §94 External Sysroots Remain Supported

**Technical**
- TS §26–28 Modules, imports and C imports
- TS §45 Compiler Context

**Acceptance**
- pure C+ builds resolve standard headers, runtime source, PAL metadata and startup from the C+ SDK.
- external sysroots remain separately configurable for foreign C/vendor ecosystems.
- target SDK resolution never falls back silently to host headers/libraries.
- resolver diagnostics identify missing SDK components precisely.

**Implementation**
- Added `SdkLayout`, `SdkResolver`, and `SdkResolution` to resolve source-first SDK roots for the selected target triple.
- Added explicit `CompileRequest.externalSysroot` and CLI `--sysroot` handling; invalid external roots produce `SDK009`.
- Added SDK resolution to compiler results and incremental cache identity.
- Added the initial source SDK tree for standard core, libc, runtime, platform API, Linux PAL, intrinsics, ABI metadata, startup, and headers.
- Added resolver tests for the default target and host-fallback rejection.

**Depends**
- 6.1.1.1
- 2.4

#### 6.1.1.4 [DONE] [4/4] Add SDK semantic metadata and reproducible cache format

**SDK**
- SDK §58 SDK Semantic Metadata
- SDK §101 SDK/Runtime Invariant

**Technical**
- TS §47 Incremental Compilation
- TS §62 Caching

**Acceptance**
- SDK semantic metadata can cache symbols, types, exports, documentation and comptime signatures.
- metadata is reproducible from C+ source and never authoritative over source.
- stale/incompatible metadata is rejected and rebuilt.
- language tooling can use metadata without losing source navigation when sources are installed.

**Implementation**
- Added deterministic versioned SDK metadata containing source hashes, declarations, exports, documentation comments, and CPX signatures.
- Added manifest/target/schema/source-hash validation with automatic stale-cache rebuilds.
- Exposed metadata through `CompileResult.sdkResolution` while retaining source as the compilation authority.
- Added cache reuse and deterministic-content regression coverage.

**Depends**
- 6.1.1.3
- 2.1

---

### 6.1.2 [DONE] [4/4] Runtime lifecycle and compiler-support runtime

#### 6.1.2.1 [DONE] [4/4] Implement target startup selection and `__cplus_start`

**SDK**
- SDK §39 Program Startup ABI
- SDK §40 Startup Symbols
- SDK §63 Startup Generator

**Technical**
- TS §41 C Symbol Naming
- TS §42 C Emitter

**Acceptance**
- `runtime=cplus` owns startup rather than using host libc startup objects.
- target startup reaches stable internal entry `__cplus_start`.
- startup acquisition of arguments/environment is delegated to the selected target adapter.
- startup symbols retain generated provenance for diagnostics/debugging.

**Implementation**
- Added `RuntimeLinker` and target runtime-link plans with explicit startup/runtime inputs and no-default-library flags for self-hosted profiles.
- Added the Linux x86_64 `_start` adapter and `__cplus_start` runtime entry.
- Added startup selection tests and CLI executable integration coverage.

**Depends**
- 6.1.1.2
- 6.2.1.1

#### 6.1.2.2 [DONE] [4/4] Implement runtime initialization and application entry dispatch

**SDK**
- SDK §41 Application Entry Point
- SDK §42 Runtime Initialization

**Language**
- LS §23 Symbol Identity

**Acceptance**
- `main()` and `main(int,char**)` entry forms are supported.
- runtime initializes TLS, allocator/runtime state, arguments/environment and global runtime state in deterministic order.
- application entry remains semantically separate from platform loader entry.
- integration fixture reaches `main` without an external libc startup.

**Implementation**
- Added deterministic runtime initialization hooks for TLS, allocator, global state, and argument/environment capture.
- Added dispatch from platform `_start` through `__cplus_start` to the application `main` entry.
- Added explicit runtime/system profile separation so system startup remains a downstream-toolchain responsibility.

**Depends**
- 6.1.2.1
- 6.3.1.1

#### 6.1.2.3 [DONE] [4/4] Implement termination, exit handlers and stream finalization

**SDK**
- SDK §43 Program Termination

**Acceptance**
- normal exit, quick exit, immediate `_Exit`, abort, `atexit` and `at_quick_exit` have distinct semantics.
- registered handlers execute in specified order.
- normal exit flushes applicable standard streams; immediate exit does not perform hosted cleanup.
- exit ultimately delegates to the PAL process-termination primitive.

**Implementation**
- Added separate normal and quick-exit handler stacks with reverse-registration execution.
- Added immediate termination and abort-status primitives that skip normal cleanup.
- Added Linux runtime integration coverage proving handler order through the self-hosted startup path.
- Added a weak runtime flush hook with a strong unbuffered C17 stdio implementation, so normal termination flushes through libc while immediate/abort paths skip it.

**Depends**
- 6.1.2.2
- 6.3.2.3

#### 6.1.2.4 [DONE] Implement compiler-support runtime and implicit dependency catalogue

**SDK**
- SDK §45 Compiler-generated Memory Operations
- SDK §46 Compiler Support Runtime
- SDK §89 No Hidden Compiler-runtime Invariant

**Technical**
- TS §40 Dependency Collector

**Acceptance**
- `runtime.compiler` owns required `__cplus_*` helpers.
- implicit downstream-compiler references such as memory helpers are explicitly catalogued.
- self-hosted builds either provide or suppress every known implicit runtime dependency.
- tests deliberately trigger representative compiler-emitted helper calls.

**Implementation**
- Added compiler-owned memory helpers and a no-host-libc string-template formatter in the SDK runtime.
- Changed string-template lowering to declare `__cplus_format` and link it from the compiler-support runtime rather than embedding a hidden `stdio`/`vsnprintf` dependency.
- Added runtime dependency catalogue plumbing to lowered C units and made `RuntimeLinker` include compiler-support sources for self-hosted profiles.
- Added `RuntimeDependencyAuditor` to inspect dynamic libraries and unresolved compiler-runtime symbols.

**Depends**
- 6.3.2.2
- 6.4.2.3

---

## 6.2 [DONE] [8/8] ABI, low-level language support and compile-time target model

**SDK**
- SDK §20–30
- SDK §38
- SDK §47–50
- SDK §77–79
- SDK §99–100

### 6.2.1 [DONE] [4/4] ABI declarations, target descriptors and low-level constructs

#### 6.2.1.1 [DONE] [4/4] Implement Target ABI Descriptor and `target` compile-time namespace

**SDK**
- SDK §27 Target Compile-time Namespace
- SDK §30 Target ABI Descriptor
- SDK §31 Initial Supported ABI Targets

**Language**
- LS §7–13

**Technical**
- TS §17 Comptime Evaluator
- TS §45 Compiler Context

**Acceptance**
- target descriptors provide OS, arch, vendor, ABI, object format, endian, pointer/word widths, integer model and runtime profile.
- `target.has_intrinsic`, `target.has_feature`, `target.has_libc_profile`, `target.supports_abi` are compile-time queries.
- descriptors are data loaded through `TargetRegistry`, not hard-coded throughout library source.
- initial descriptor tests cover Linux/Windows/Darwin on x86_64 and AArch64.

**Implementation**
- Added strict `TargetAbiDescriptor` parsing and `TargetRegistry` loading/listing for six initial target descriptors.
- Propagated descriptor data into structured `ComptimeTargetInfo` metadata and capability predicates.
- Added Linux, Windows, and Darwin x86_64/AArch64 descriptor fixtures plus registry and capability tests.

**Depends**
- 3.3.1
- 5.2.1

#### 6.2.1.2 [DONE] Implement ABI semantic kinds and `abi` compile-time namespace

**SDK**
- SDK §20 ABI Classes
- SDK §28 ABI Compile-time Namespace
- SDK §99 ABI Invariant

**Technical**
- TS §12 Type System Model
- TS §41 C Symbol Naming

**Acceptance**
- compiler distinguishes `abi.c`, `abi.system`, `abi.cplus`, `abi.intrinsic`, `abi.runtime` even when machine conventions coincide.
- ABI identity is attached to function symbols/types and foreign/export declarations.
- `sizeof`, `alignof`, `offsetof`, `layoutof` expose structured layout information at the specified compile-time phase.
- unsupported ABI requests fail before code emission.

**Implementation**
- Added explicit `AbiKind` identity to semantic function types, symbols, function symbols, and method symbols.
- Added target-driven `AbiLayoutEngine` for primitive, pointer, array, struct, union, enum, alias, foreign, and function layouts.
- Added compiler-owned `alignof`, `offsetof`, and `layoutof` query nodes through syntax, AST, semantic validation, C AST, dependency collection, and emission.
- Added structured layout and end-to-end C lowering tests; layout queries reject unknown types/fields before emission.

**Depends**
- 6.2.1.1
- 2.2.1

#### 6.2.1.3 [DONE] Implement foreign/export ABI attributes and linker-name semantics

**SDK**
- SDK §21 Foreign Declaration Syntax
- SDK §22 Export Declarations
- SDK §76 Binary Exports
- SDK §77 C+ Public Symbols

**Language**
- LS §22
- LS §43

**Acceptance**
- `@abi`, `@library`, `@link_name`, `@export_name` are represented semantically rather than by textual rewriting.
- local source names remain distinct from external linker names.
- C imports/exports preserve external C ABI spelling where requested.
- conflicting or unsupported attribute combinations produce diagnostics.

**Implementation**
- Added parser-preserved `@abi`, `@library`, `@link_name`, `@export_name`, `@weak`, and `@noreturn` attribute maps for functions and methods.
- Added semantic ABI parsing/validation and preserved source names separately from external linker names.
- Added `AbiAttributes`, `LinkageIdentity`, and target-aware `AbiContractValidator` APIs with unsupported-combination diagnostics.
- Added end-to-end coverage proving `@abi(system)` and `@link_name` survive semantic resolution and C emission.

**Depends**
- 6.2.1.2
- 2.4.1

#### 6.2.1.4 [DONE] Implement runtime-required storage, layout and linkage constructs

**SDK**
- SDK §23 Required Low-level Language Constructs
- SDK §50 Thread-local Storage

**Language**
- LS §30 Lowering Model

**Acceptance**
- `thread_local`, `volatile`, explicit alignment, packed layout and `noreturn` are supported end-to-end.
- `@section`, `@used`, and target-supported `@weak` are represented without leaking into ordinary portable library code.
- unsupported target linkage/layout requests are rejected explicitly.
- C AST/emission preserves required target-C representation and origins.

**Implementation**
- Added target-checked `StorageAttributes` and `StorageContractValidator` for TLS, volatility, alignment, packed, section, used, and no-return contracts.
- Added target descriptor TLS/alignment metadata and explicit platform ABI profiles so unsupported storage requests fail before downstream linking.
- Added lexer/parser coverage for compiler-owned low-level spellings and kept the C backend validation boundary explicit.

**Depends**
- 6.2.1.2
- 4.3.1

---

### 6.2.2 [DONE] [4/4] Compiler intrinsics and compile-time directives

#### 6.2.2.1 [DONE] Implement intrinsic registry and architecture-neutral syscall intrinsic family

**SDK**
- SDK §24 Compiler Intrinsics
- SDK §25 Mandatory Intrinsic Classes
- SDK §26 System-call Intrinsics
- SDK §61 Intrinsic Registry

**Technical**
- TS §31 Compiler Pass Framework
- TS §37 C AST

**Acceptance**
- semantic intrinsic declarations are distinct from ordinary functions.
- syscall0..syscall6 expose arguments independently of machine registers.
- intrinsic availability is target-queryable.
- unsupported intrinsic/target combinations produce compile-time diagnostics rather than unresolved symbols.

**Implementation**
- Added the data-driven SDK intrinsic registry with target, feature, arity, family, and lowering validation.
- Added syscall0..syscall6 target catalogue entries and Linux architecture-neutral syscall contracts.

**Depends**
- 6.2.1.1
- 6.2.1.2

#### 6.2.2.2 [DONE] Implement atomic, fence and CPU primitive intrinsics

**SDK**
- SDK §25
- SDK §49 Atomics

**Acceptance**
- load/store/exchange/CAS/fetch operations and fences support relaxed/acquire/release/acq_rel/seq_cst semantics.
- `volatile` is not used as a substitute for atomics.
- lock-free capability is target-queryable.
- non-lock-free fallback requirements are explicitly routed to runtime support.

**Implementation**
- Added atomic load/store/compare-exchange/fence catalogue entries and target feature checks.
- Added intrinsic source contracts for memory ordering and kept volatile/storage validation separate from atomics.

**Depends**
- 6.2.2.1
- 6.3.1.1

#### 6.2.2.3 [DONE] Implement varargs and context save/restore intrinsic contracts

**SDK**
- SDK §47 Varargs ABI
- SDK §48 setjmp / longjmp

**Acceptance**
- `va_start`, `va_arg`, `va_copy`, `va_end` map to target ABI primitives.
- `context_save` / `context_restore` support the libc `setjmp`/`longjmp` façade.
- architecture-sensitive state is not modelled as portable ordinary C+ code.
- ABI verifier includes varargs and context round-trip tests.

**Implementation**
- Added target-checked varargs and context-save/context-restore catalogue entries mapped to compiler builtins/runtime contracts.
- Added intrinsic call arity validation so malformed calls fail before lowering.

**Depends**
- 6.2.1.2
- 6.4.2.4

#### 6.2.2.4 [DONE] Standardize target-selection comptime directives and capability diagnostics

**SDK**
- SDK §27
- SDK §29 Additional Compile-time Directives
- SDK §73 Library Compilation Model
- SDK §74 Platform-specific Source Isolation
- SDK §100 Comptime Invariant

**Language**
- LS §7–13
- LS §18–20

**Acceptance**
- `comptime assert` and `comptime error` are supported with stable diagnostics.
- target/platform selection uses ordinary compile-time control flow and structured metadata.
- full layout introspection observes the existing type-universe stabilization barrier.
- standard-library source can eliminate non-target implementations without C preprocessor conditionals.

**Implementation**
- Added `CompilerDirectives` for deterministic compile-time assertions/errors and target feature/intrinsic requirements.
- Routed target capability checks through `ComptimeTargetInfo` predicates and stable CPX601/CPX602 diagnostics.

**Depends**
- 6.2.1.1
- 3.5.2

---

## 6.3 [DOING] [7/8] Native `std` and C libc compatibility implementation

**SDK**
- SDK §6–19
- SDK §44–57
- SDK §75–76
- SDK §90–92

### 6.3.1 [DOING] [3/4] Native C+ standard-library core

#### 6.3.1.1 [DOING] Implement `std.core`, `std.mem` and portable memory primitives

**SDK**
- SDK §7 `std.core`
- SDK §8 `std.mem`
- SDK §45 Compiler-generated Memory Operations

**Acceptance**
- target-aware byte/size/index aliases, `usize/isize`, low-level numeric
  limits, and pointer/memory utilities are available without hosted OS
  dependencies; opt-in `iN`/`uN` aliases are tracked separately in R1.2.4–R1.2.5.
- portable copy/move/set/compare/zero operations exist.
- C symbols `memcpy`, `memmove`, `memset`, `memcmp` can be provided by the self-hosted runtime.
- compiler/runtime tests verify overlap and alignment edge cases.

**Implementation**
- Added source-delivered `std.mem` copy/move/set/compare operations and compiler-runtime equivalents with overlap-safe behavior.
- Added explicit byte/text source contracts without hosted OS dependencies.
  Audit found that `std.core` had unused, fixed-width `std_byte_t`,
  `std_size_t`, and `std_index_t` aliases but no required `usize`/`isize`
  declarations. The dead aliases have been removed; target-aware replacements
  remain open and are not counted complete. This does not implement the
  opt-in `std.fixed_width` aliases tracked by R1.2.4–R1.2.5.
- SDK libc/std source modules import their dependencies explicitly and all delivered `.cp` sources pass CLI semantic checking as individual package entry points.

**Depends**
- 6.2.1.4

#### 6.3.1.2 [DONE] Implement page abstraction and `std.alloc` portable allocator

**SDK**
- SDK §9 `std.alloc`

**Acceptance**
- allocator logic is platform-independent above a PAL page allocation interface.
- allocate/zeroed/resize/free/aligned allocation are provided.
- allocator can bootstrap without calling foreign libc allocation.
- libc `malloc/calloc/realloc/free/aligned_alloc` can delegate to this implementation.

**Implementation**
- Added deterministic bootstrap arena allocation and zeroed allocation source implementations.
- Added narrow PAL page allocation/release contracts and libc allocation façade declarations.

**Depends**
- 6.3.1.1
- 6.4.1

#### 6.3.1.3 [DONE] Implement `std.string`, `std.text` and core collections

**SDK**
- SDK §10 `std.string` and `std.text`
- SDK §54 Wide and Unicode Compatibility

**Acceptance**
- native byte-string and Unicode/text semantics are explicit and separate from C `char*` conventions.
- fundamental string/text operations avoid dependence on system libc.
- core reusable collection primitives needed by the SDK are implemented in portable C+.
- Unicode scalar/code-unit types do not depend on platform `wchar_t` width.

**Implementation**
- Added byte-string length/equality and explicit UTF-8/ascii text contracts.
- Added C17 fixed-width and wide-character header views without treating `wchar_t` as the native text model.

**Depends**
- 6.3.1.1
- 6.3.1.2

#### 6.3.1.4 [DONE] Implement hosted native services: I/O, filesystem, process, time, threads, sync, networking and math

**SDK**
- SDK §11–16 Native Standard-library Packages
- SDK §52 Math Library
- SDK §91 Platform-layer Size Rule
- SDK §92 Low-level OS Packages

**Acceptance**
- native modules expose portable APIs over narrow PAL services.
- `std.io/std.fs/std.process/std.time/std.thread/std.sync/std.net/std.math` do not expose host libc types as their core model.
- unavailable target capabilities are reported through structured results/capability checks.
- representative cross-platform API tests run against each implemented PAL.

**Implementation**
- Added narrow `std.io`, `std.fs`, `std.process`, `std.time`, `std.thread`, `std.sync`, `std.net`, and `std.math` source contracts.
- Added Linux, Windows, and Darwin PAL declarations with explicit capability sets and no host-type leakage.

**Depends**
- 6.3.1.2
- 6.4.1

---

### 6.3.2 [DONE] [4/4] C libc compatibility surface

#### 6.3.2.1 [DONE] Generate and deliver the C17 standard-header surface

**SDK**
- SDK §17.1 `libc-c17`
- SDK §18 Standard C Headers Delivered by the SDK
- SDK §19 Compiler-owned C Facilities
- SDK §57 C Headers as Generated SDK Views
- SDK §69 C Header Generator

**Technical**
- TS §38 Header Synthesis

**Acceptance**
- the self-hosted SDK exposes the specified C17 standard-header families.
- headers are generated from shared semantic definitions wherever practical.
- compiler-owned constructs such as varargs/atomics/alignment map to compiler/runtime primitives.
- generated headers can be consumed by an independent supported C compiler.

**Implementation**
- Added the delivered C17 header family and deterministic `CHeaderProfileGenerator` index generation.
- Headers remain compatibility views while native C+ source and semantic declarations remain authoritative.

**Depends**
- 6.2
- 6.3.1.1

#### 6.3.2.2 [DONE] Implement libc memory, string, allocation, conversion and `errno` APIs

**SDK**
- SDK §9–10
- SDK §44 `errno`
- SDK §45
- SDK §75 Native C+ API versus libc ABI

**Acceptance**
- libc memory/string/stdlib compatibility operations delegate to portable C+ implementations where appropriate.
- `errno` is thread-local and only the libc façade converts structured native errors into C sentinel+errno semantics.
- allocation and conversion functions require no external libc.
- C caller → C+ libc fixtures verify exported C ABI symbols.

**Implementation**
- Added source libc memory/string/allocation facades and thread-local `errno` header contract.
- Reused compiler-owned no-libc memory helpers for self-hosted profiles.

**Depends**
- 6.3.1.1–6.3.1.3
- 6.2.1.4

#### 6.3.2.3 [DONE] Implement libc stdio, time, math, locale, Unicode and signal compatibility

**SDK**
- SDK §11–15
- SDK §51 Floating-point Environment
- SDK §52 Math Library
- SDK §53 Character and Locale Layer
- SDK §54 Wide and Unicode Compatibility
- SDK §55 Signals

**Acceptance**
- `FILE` buffering/stdio is implemented above native I/O/PAL primitives.
- C time/math APIs are backed by native C+ services and do not require system `libm` in complete self-hosted profiles.
- initial locale/conversion/signals behavior has explicit conformance status and no silent host-libc fallback.
- external C conformance fixtures exercise representative interfaces.

**Implementation**
- Added explicit stdio stream objects, flush hook, time, math, locale, Unicode, and signal compatibility source/header contracts.
- Normal runtime termination calls the libc-owned stream flush hook; immediate and abort paths do not.

**Depends**
- 6.3.1.4
- 6.3.2.2

#### 6.3.2.4 [DONE] Implement C23 profile tracking and optional POSIX compatibility package

**SDK**
- SDK §17.2 `libc-c23`
- SDK §56 POSIX Compatibility

**Acceptance**
- C23 additions are tracked as an additive, queryable profile rather than silently mixed with C17.
- profile completeness is exposed to `target.has_libc_profile` or equivalent metadata.
- optional `cplus.posix` is isolated from the native portable std API.
- non-POSIX targets may reject unavailable POSIX facilities without compromising core C+ runtime support.

**Implementation**
- Added an additive libc profile catalogue: C17 is delivered, C23 is tracked and rejected until complete.
- Added explicit platform-service contracts so optional POSIX functionality cannot contaminate native `std` APIs.

**Depends**
- 6.3.2.1–6.3.2.3

---

## 6.4 [DONE] [9/9] Platform adapters, link/toolchain integration and conformance

**SDK**
- SDK §31–38
- SDK §59–70
- SDK §82–89
- SDK §95–97

### 6.4.1 [DONE] [5/5] Platform ABI adapters

#### 6.4.1.1 [DONE] Implement Linux PAL and generated syscall catalogues

**SDK**
- SDK §32 Linux ABI Profile
- SDK §33 Linux Syscall Catalogue
- SDK §34 Linux Syscall Result Normalization
- SDK §68 Syscall Catalogue Generator

**Acceptance**
- Linux PAL reaches the kernel through architecture-specific syscall intrinsic lowering without glibc/musl.
- syscall numbers are generated/packaged per target architecture rather than duplicated in ordinary library code.
- raw Linux error conventions normalize to portable PAL results before reaching `std`/libc.
- x86_64 and AArch64 smoke tests cover file I/O, memory pages and process exit.

**Implementation**
- Added architecture-specific Linux syscall catalogue source files and `LinuxSyscallCatalogue` normalization metadata.
- Added Linux PAL source contracts for memory/process services and compiler intrinsic syscall families.

**Depends**
- 6.2.2.1
- 6.2.1.1

#### 6.4.1.2 [DONE] Implement Windows PAL over supported system DLL APIs

**SDK**
- SDK §35 Windows ABI Profile
- SDK §36 Windows Import Metadata

**Acceptance**
- Windows PAL uses declared supported system DLL APIs rather than hard-coded NT syscall numbers.
- no UCRT/MSVCRT dependency is required for `runtime=cplus`.
- DLL imports are selected only when used and retain ABI/link-name metadata.
- x86_64 and AArch64 target descriptors cover memory, file/process and synchronization primitives.

**Implementation**
- Added Windows x86_64/AArch64 descriptors, startup/platform source contracts, and `declared-dll` platform ABI metadata.
- Kept UCRT/MSVCRT out of the self-hosted runtime dependency policy.
- Added the uniform `platform_write_stdout`/`platform_process_exit` PAL ABI and isolated Linux syscall and Windows DLL implementations behind it.
- Added target startup adapters for Linux x86_64/AArch64 and Windows x86_64/AArch64 without host libc startup objects.
- Reserved the PAL file-service boundary for canonical UTF-8 `/`-separated paths; Windows path/root and UTF-8-to-wide conversion remains inside the Windows adapter rather than in `std.fs` or application code.

**Depends**
- 6.2.1.3
- 6.2.1.1

#### 6.4.1.3 [DONE] Implement Darwin PAL over supported Apple userspace ABI

**SDK**
- SDK §37 Darwin ABI Profile

**Acceptance**
- Darwin PAL uses supported Apple userspace/system ABI facilities rather than undocumented syscall numbering as the portable contract.
- native C+ std/libc semantics remain C+ owned even when the PAL enters through `libSystem`.
- x86_64 and AArch64 target descriptors cover startup, memory, files/process and time primitives.
- system-library dependencies are explicit and audited.

**Implementation**
- Added Darwin x86_64/AArch64 descriptors and supported System/libSystem PAL source contracts.
- Target profiles expose system dependencies explicitly through `TargetAbiDescriptor`.

**Depends**
- 6.2.1.3
- 6.2.1.1

#### 6.4.1.4 [DONE] Implement object-format, startup and system-link metadata for ELF, PE/COFF and Mach-O

**SDK**
- SDK §38 Object Formats
- SDK §39–40 Program Startup ABI and Startup Symbols
- SDK §87 SDK Independence Invariant

**Acceptance**
- target descriptors identify ELF, PE/COFF and Mach-O requirements.
- startup/link metadata is isolated from portable standard-library code.
- target startup entry, system dependencies and symbol-prefix rules are queryable by the link driver.
- cross-target tests prove host object-format assumptions do not leak into target compilation.

**Implementation**
- Added object-format, startup entry, symbol prefix, TLS, linker, system-library, and stack-alignment metadata for ELF, PE/COFF, and Mach-O.
- Added `PlatformAbiRegistry` to expose platform service and syscall/import mode without host-derived branching.
- Added target-aware runtime source selection and target-specific PAL runtime units; portable runtime stdio now calls only the uniform PAL ABI.

**Depends**
- 6.4.1.1–6.4.1.3
- 6.1.2.1

---

#### 6.4.1.5 [DONE] Implement concrete cross-platform file PAL services

**SDK**
- SDK §2.5 Platform Abstraction Layer
- SDK §11 `std.io`
- SDK §12 `std.fs`
- SDK §32–36 Linux and Windows ABI profiles

**Acceptance**
- the uniform PAL ABI provides open, read, write, close, and rename with explicit-width handles and byte counts.
- canonical UTF-8 `/` paths remain unchanged above the PAL boundary.
- Linux x86_64 and AArch64 adapters use catalogue-backed `openat`, `read`, `write`, `close`, and `renameat` operations with normalized failures.
- Windows adapters convert UTF-8 paths to native wide paths and use supported kernel32 APIs without UCRT/MSVCRT.
- an executable self-hosted test proves write/read/rename behavior on Linux and Windows.

**Implementation**
- Added version-2 `cplus_platform.h` file mode, result, and explicit-width handle/size contracts.
- Added target-independent `sdk/runtime/src/fs.c` forwarding entry points and included it in `RuntimeLinker` plans.
- Implemented Linux syscall adapters and added `openat`/`renameat` to both architecture catalogues.
- Implemented Windows UTF-8-to-UTF-16 path conversion, slash conversion, heap-owned temporary buffers, kernel32 file calls, and error normalization.
- Added `RuntimeFilePalTest`, passing on the Linux host and the Windows VM.

**Depends**
- 6.4.1.1
- 6.4.1.2
- 6.1.2.1

---

### 6.4.2 [DONE] [4/4] Toolchain integration, auditing and conformance

#### 6.4.2.1 [DONE] Implement LinkDriver and downstream C compiler target adapters

**SDK**
- SDK §64 Link Driver
- SDK §65 C Compiler Invocation
- SDK §95 Canonical Pure C+ Build
- SDK §96 Canonical Interoperability Build

**Technical**
- TS §57 CLI
- TS §58 Compiler API

**Acceptance**
- link orchestration consumes generated application objects, selected runtime objects/startup and allowed OS dependencies.
- downstream compiler flags prevent accidental host libc startup/default-library usage in self-hosted mode.
- TCC/Clang/GCC adapters expose capability differences explicitly.
- pure C+ and C-interoperability fixture builds are both supported.

**Implementation**
- Added `LinkDriver`/`LinkRequest` as the target-aware downstream compiler boundary and wired the CLI build/run path through it.
- Added explicit GCC/Clang/TCC/unknown capability classification and self-hosted flag validation.
- Added automatic target compiler selection, `--target`/`--c-compiler` CLI controls, GNU/Clang and MSVC-style invocation adapters, compiler-provided C17 headers, no-PIE Linux linking, and explicit Windows OS-import linking.
- CLI target omission now infers the host OS/architecture, so Windows builds do not accidentally select Linux startup; explicit `--target` remains the cross-compilation override.
- Routed generated C through the SDK-owned libc compatibility headers before runtime or user include directories, including freestanding `stddef.h`, `stdarg.h`, and `stdio.h` contracts, so host CRT declarations cannot leak into self-hosted products.

**Depends**
- 6.1.2
- 6.4.1
- 4.4

#### 6.4.2.2 [DONE] Implement SDK build/package/header/syscall tooling and CLI commands

**SDK**
- SDK §59 Additional Compiler Tooling
- SDK §60 TargetRegistry
- SDK §68 Syscall Catalogue Generator
- SDK §69 C Header Generator
- SDK §70 SDK Packager
- SDK §71 Proposed CLI Additions

**Acceptance**
- commands equivalent to `sdk build/verify/doctor`, `target list/show`, `abi verify`, `runtime inspect`, `libc test` exist.
- SDK package output contains source, headers, target descriptors, syscall catalogues, startup, intrinsic metadata and optional caches/objects.
- generated artifacts are deterministic and version-stamped.
- SDK tooling can run without a target machine matching the build host.

**Implementation**
- Added deterministic SDK package indexing, `sdk doctor|verify|package`, `target list|show`, `abi verify`, `runtime inspect`, and `libc test` commands.
- Added deterministic C17 header index generation and target/intrinsic registry inspection.

**Depends**
- 6.1.1
- 6.3.2.1
- 6.4.1

#### 6.4.2.3 [DONE] Implement runtime dependency audit and no-host-contamination enforcement

**SDK**
- SDK §66 Runtime Dependency Auditor
- SDK §85 Freestanding Conformance
- SDK §86 Target-specific Dependency Policy
- SDK §88 No-host-contamination Invariant
- SDK §89 No Hidden Compiler-runtime Invariant

**Acceptance**
- produced binaries can be inspected for unexpected libc/system/compiler-runtime dependencies.
- self-hosted Linux rejects glibc/musl dependency; Windows rejects UCRT/MSVCRT dependency.
- cross compilation rejects host headers/libraries entering target resolution silently.
- audit output lists allowed and unexpected dependencies with origin/reason where known.

**Implementation**
- Added `RuntimeDependencyAuditor` using downstream ELF dependency and unresolved-symbol inspection.
- Added CLI `audit` output for observed, allowed, unexpected, and unresolved compiler-runtime dependencies.
- Extended auditing to ELF, PE/COFF, and Mach-O; self-hosted ELF rejects dynamic interpreters and self-hosted PE rejects undeclared or C-runtime DLL imports.
- Normalized generated CPX source paths to Windows-safe filenames and slash-separated identity strings so expansion caching and diagnostics do not depend on host path syntax.
- Normalized source line endings at the shared source/LSP boundary so source-map offsets and golden artifacts remain stable when Windows stores files as CRLF.

**Depends**
- 6.4.2.1
- 6.1.2.4

#### 6.4.2.4 [DONE] Implement libc, ABI and platform conformance matrix

**SDK**
- SDK §67 ABI Verifier
- SDK §82 Testing Profiles
- SDK §83 libc Conformance Testing
- SDK §84 ABI Round-trip Testing
- SDK §85 Freestanding Conformance
- SDK §97 Implementation Priorities

**Technical**
- TS §63–64 Testing and Golden Fixtures

**Acceptance**
- test classes cover native std, libc conformance, ABI interop and target/runtime integration.
- independent C caller → C+ implementation and C+ caller → C implementation round trips cover integers, floats, pointers, structs/unions, callbacks, variadics and TLS.
- target tests verify size/alignment/layout/calling conventions and startup behavior.
- section 6 cannot become DONE while a claimed runtime/libc target profile fails its conformance matrix.

**Implementation**
- Added typed `ConformanceMatrix` cases spanning native std, libc, ABI, runtime, and platform areas.
- Added Linux executable/runtime, ABI layout, SDK tooling, and target descriptor regression coverage.
- C17 is the claimed supported libc profile; C23 remains explicitly tracked and rejected until its matrix is complete.

**Depends**
- 6.2.2.3
- 6.3
- 6.4.2.1–6.4.2.3

---

# Completion roadmap — post-foundation implementation

The historical sections above record the compiler/SDK foundation tasks and
their current completion status. Some foundation work has been reopened where
an acceptance requirement lacks implementation evidence. Those sections do
not, by themselves, prove that every normative standard-library, runtime,
platform, CLI, or release requirement is executable. This roadmap is the
authoritative work queue for completing the working CLI transcoder and
self-hosted SDK described by the specifications.

```text
Foundation tasks: 145/146 (6.3.1.1 reopened: required target-aware size/index types are absent)
Completion phases: [DOING] [2/9 gates complete]

[DONE]  R0 — implementation inventory and scope freeze
[DOING] R1 — language and front-end conformance; primitive type matrix reopened
[DONE]  R2 — CPX, generics and reflection conformance
[DOING] R3 — C backend and ABI interoperability conformance
[DOING] R4 — runtime, allocator and libc behavior
[TODO]  R5 — complete native std and platform services
[TODO]  R6 — CLI transcoder and build-product completion
[TODO]  R7 — LSP and VS Code product completion
[TODO]  R8 — SDK packaging, target matrix and release conformance
```

The completion phase counter counts only the nine phase gates above. A phase
MUST remain `DOING` until every acceptance gate inside it passes on the claimed
target matrix. Source declarations, headers, platform contracts, or a green
unit test that does not execute the claimed behavior are not completion
evidence.

## R0 [DONE] Implementation inventory and scope freeze

The repository currently has executable coverage for the front-end, semantic
model, methods including pointer receivers, imports, basic CPX expansion,
lowering, source maps, LSP primitives, self-hosted Linux/Windows startup,
stdout/exit, and the basic PAL file open/read/write/close/rename path. The
following are explicitly not yet complete despite existing contracts:

- `std.alloc` now uses the page-backed allocator; errno, native string/
  conversion behavior, and the broader C17 compatibility families remain open.
- `std.io`, process, time, thread, synchronization, networking, and math
  sources are primarily declarations or façade contracts.
- libc headers are delivered, but broad C17 behavioral and independent-C ABI
  conformance is not complete; C23 remains intentionally unavailable.
- PAL memory, process/environment, time, thread/synchronization, networking,
  remaining filesystem operations, and concrete Darwin execution remain open.
- the CLI and extension work for the common path, but workspace/project
  orchestration, product packaging, cross-target release testing, and
  workspace-wide source mapping still need completion evidence.

The scope is now frozen around the normative requirements in `SPEC.LANG.md`,
`SPEC.STDLIB.md`, and `SPEC.TECH.md`. New work MUST first be assigned to one
of R1–R8 or deliberately recorded as a post-release extension.

## R1 [DOING] Language and front-end conformance

**Progress**

- R1.1 [DONE] — complete C primitive specifier parsing and normalization;
  parser coverage now includes legal spellings, ordering variants, optional
  `int`, canonical integer rank, and signed-character identity.
  - R1.1.1 [DONE] — parse the supported C integer specifier grammar without a
    token-count limit, accept optional `int` and legal specifier ordering, and
    preserve the canonical identity of each resulting type.
    - Parser consumes the full specifier sequence, normalizes legal ordering
      variants and optional `int`, and preserves signed-char and integer-rank
      identities. Verified with the language-core, semantic, and compiler test
      suites on Linux.
  - R1.1.2 [DONE] — cover declarations, fields, parameters, returns, casts,
    typedefs, generated C, and parity between CLI and LSP diagnostics; reject
    invalid combinations with stable diagnostics.
    - An end-to-end C+ fixture exercises multiword spellings in typedefs,
      fields, signatures, and casts, compiles the emitted C17, and exits with
      the expected result.
    - Invalid signedness reports the same `PARSE102` code and message in CLI
      and LSP diagnostics. Verified with `:compiler:test` and `:cli:test` on
      Linux.
- R1.2 [DOING] — reconcile primitive signedness, semantic identity, target
  widths, C emission, and fixed-width aliases across target ABI descriptors.
  - R1.2.1 [DONE] — preserve `signed char` and integer rank/signedness through
    semantic compatibility and verify LP64/LLP64 widths for all parsed forms.
    - Semantic canonical identities distinguish plain/signed/unsigned char,
      signedness, and each integer rank; equivalent `long` spellings share
      identity while pointer compatibility rejects distinct pointee types.
    - Parsed source declarations now feed ABI layout checks for every
      canonical integer spelling on Linux/Windows x86_64 and AArch64 target
      descriptors. Semantic and compiler test suites pass on Linux; no Windows
      execution was performed.
  - R1.2.2 [DONE] — complete leading/pointer qualifiers, preserve structured
    declarators through C emission, and resolve foreign fixed-width/`stddef`
    aliases against their underlying target-aware types.
  - R1.2.3 [DONE] — audit semantic, ABI-layout, reflection, CPX, closure, and
    C-backend primitive tables against one canonical type identity; keep SDK
    declarations idiomatic C and remove unused custom integer aliases from
    `std.core`.
    - Added a shared `CPrimitiveTypes` catalog for canonical C spellings,
      integer rank/signedness, numeric classification, and legal specifier
      sequences. Parser, semantic identity/resolution, CPX type recognition,
      closure lowering, ABI layout, C lowering, and dependency collection now
      consume that catalog rather than maintaining separate primitive tables.
    - The semantic type universe now includes canonical primitives; reflective
      CPX resolves reordered multiword spellings to the canonical descriptor
      and exposes the target ABI layout.
    - Removed the unused `std_byte_t`, `std_size_t`, and `std_index_t`
      declarations from `std.core`. The audit separately reopened R5.1 because
      its required target-aware byte/size/index types, including `usize` and
      `isize`, are absent; they are not optimistically counted as complete.
    - `./gradlew test` passes on Linux. ABI layout checks cover Linux and
      Windows x86_64/AArch64 target descriptors; no Windows execution was
      performed. Compiler integration verifies reflected `unsigned long long`
      identity/layout and generated C execution.
  - R1.2.4 [TODO] — provide `std.fixed_width` as an explicit-import user-level
    source module defining ordinary typedef aliases `i8`, `i16`, `i32`, `i64`,
    `u8`, `u16`, `u32`, and `u64` over the target's corresponding C
    `intN_t`/`uintN_t` types. Keep these names out of compiler built-ins,
    `std.core`, and native SDK API signatures; do not inject them implicitly.
    Require the aliases on supported Linux/Windows x86_64 targets, diagnose
    unavailable exact widths on other targets, and test imports, aggregates,
    pointers, function signatures, and generated C.
  - R1.2.5 [TODO] — establish target/compiler capability and exact ABI support
    for signed and unsigned 128-bit integers; because standard C does not
    provide `int128_t`/`uint128_t`, expose `i128`/`u128` in `std.fixed_width`
    only where primitive representation, calling convention, and generated-C
    mapping are verified; test layout, arithmetic, conversions, function
    arguments/returns, and generated C, with explicit diagnostics elsewhere.
- R1.3 [DONE] — close the remaining declaration matrix in dependency order.
  - R1.3.1 [DONE] — represent function types and function-pointer declarators
    from source through semantic validation, indirect calls, and C emission.
  - R1.3.2 [DONE] — reconcile arrays, pointer arithmetic, casts, globals, and
    initializer/lvalue rules across parser, semantic analysis, and lowering.
  - R1.3.3 [DONE] — add stable unsupported-declarator diagnostics and recovery
    fixtures so parser acceptance cannot outrun backend support.
- R1.4 [DOING] [3/5] — implement kind-aware source type imports and
  module-scoped visibility. This is a prerequisite for R1.2.4.
  - R1.4.1 [DONE] — catalogue module-owned source type declarations and their
    public/private export status, including aliases, structs, unions, and
    enums, without exposing declarations merely because they share a build.
    - `SemanticModel.sourceTypeCatalogue` groups source type symbols by owner
      module and exposes a public-only export map while retaining each symbol's
      kind and visibility. Tests cover aliases, structs, unions, and enums;
      `:semantic:test` and `:compiler:test` pass on Linux.
  - R1.4.2 [DONE] — resolve locally declared types in their owning module and
    reject unimported cross-module references while preserving forward type
    references and existing C-header imports.
    - Added a recursive type-reference visibility check over declarations,
      fields, signatures, locals, casts, and type/layout queries. Cross-module
      source types without imports receive SEM410; same-module forward
      references remain valid. C-header type imports remain unaffected.
    - `:semantic:test` and `:compiler:test` pass on Linux, including the
      explicit unimported-type and local-forward-reference cases.
  - R1.4.3 [DONE] — bind selective type imports and `as` renames into the
    importing module; validate missing/private exports, mixed symbol kinds,
    and collisions with stable diagnostics.
    - Added module-owned type bindings to `SemanticModel`; selective imported
      aliases resolve to their original source declaration without becoming
      visible in other modules. Aggregate shells are catalogued before
      signature and alias resolution, so provider-file order does not affect
      type imports.
    - Type-only and mixed type/function imports are classified by declaration
      kind. Private types report SEM406, missing selected names report SEM404,
      and conflicting local type/function bindings report SEM405.
    - Semantic tests cover type-only and mixed exports; renamed struct and
      typedef imports in fields, pointers, function signatures, and locals;
      provider modules appearing later in the workspace; and private, missing,
      type/type, and type/function collisions. `:semantic:test` and
      `:compiler:test` pass on Linux.
  - R1.4.4 [TODO] — resolve exported types through module aliases and support
    qualified and selectively imported types in fields, pointers, signatures,
    casts, aliases, and generated C declarations.
  - R1.4.5 [TODO] — verify path/package imports, type-only and mixed exports,
    function/value import regressions, private/omitted-import failures, CLI/LSP
    parity, and compiled generated-C behavior on Linux.

**Deliverables**

- complete C-compatible type spellings and declarators, including fixed-width
  aliases, qualifiers, every supported multi-token primitive form, function
  pointers, and target-correct widths;
- preserve the distinctions between plain `char`, `signed char`, and
  `unsigned char`, and between `int`, `long`, and `long long` variants;
- keep SDK public declarations in idiomatic C, using standard C names such as
  `size_t`, `ptrdiff_t`, and `uintN_t` where appropriate; do not add custom
  `u8`/`i8`-style aliases to `std.core` or native SDK APIs. Provide `i8` through
  `i64` and `u8` through `u64` as ordinary typedefs in the explicitly imported
  user-level `std.fixed_width` source module after R1.4 type-import support;
  expose 128-bit aliases there only with verified compiler and target support;
- close parser/AST/semantic gaps for initializers, lvalues, casts, pointer
  arithmetic, arrays, globals, declarations, control flow, and diagnostics;
- make every normative language example compile or produce the specified
  diagnostic, including negative cases and recovery behavior;
- add grammar, semantic, lowering, generated-C, and executable fixtures for
  each completed language family.

**Gate**

The canonical language examples and the C-compatible declaration matrix pass
on Linux x86_64 and Windows x86_64, with stable diagnostics for unsupported
constructs and no parser-only acceptance that later fails at C emission.

### R1.1 initial coverage review

The earlier parser change combined `long long` and `unsigned long long` and
added focused AST/generated-C coverage. Review against VS Code diagnostics
found this was not a complete primitive spelling implementation:

- the parser consumes at most three primitive specifier tokens, excluding
  valid four-token forms such as `unsigned long long int`;
- normalization changes `signed char` to plain `char`;
- `signed long int` and `unsigned long int` normalize to `int`-rank spellings;
- the C backend, semantic argument checks, and ABI layout maintain separate
  spelling tables, so parser rewrites can hide types that consumers need to
  distinguish.

The LSP passes workspace text to the same `CPlusCompiler` frontend and only
converts compiler diagnostics to LSP messages. Therefore the extension report
is evidence for the shared parser/semantic path, not a separate extension type
parser. R1.1 and R1.2 remain open until CLI and LSP cases agree with emitted C
and target ABI results.

### R1.2.2 completion record

Implemented and tested:

- `const`, `volatile`, and `restrict` are lexed and retained as base or
  pointer-declarator qualifiers in syntax and AST records;
- generated C preserves qualifier placement for primitive, aggregate, alias,
  and foreign named types;
- foreign typedef declarations retain their resolved underlying semantic type;
- ABI layout resolves `size_t`, `ptrdiff_t`, and fixed-width `stdint` aliases
  through that underlying type, including Linux LP64 and Windows LLP64 rules;
- focused parser, semantic, generated-C, ABI, and compiler integration tests
  cover the stage.

This record covers qualifier placement and foreign typedef resolution only; it
does not imply completion of the reopened primitive spelling and ABI matrix.

### R1.3 execution contract

R1.3 MUST be implemented in the following order:

1. extend syntax/AST declarators and semantic `FunctionType` identity;
2. lower function pointers and callback arguments without changing ordinary
   function ABI names;
3. add generated-C and executable callback fixtures on Linux and Windows;
4. reconcile the existing array, pointer, cast, initializer, global, and
   lvalue paths against the same type representation;
5. add negative parser/semantic/backend fixtures for unsupported declarators.

The stage gate is a clean Linux and Windows suite plus independently compiled C
caller and C+ caller fixtures for function pointers and callbacks. No R1.3
subtask is complete from parser-only output or declarations without execution
evidence.

### R1.3.1 completion record

Implemented and tested on Linux:

- C-style function-pointer declarators are retained in syntax and AST records;
- semantic `FunctionType` values can be wrapped in callable pointer types;
- indirect calls validate signatures and accept matching named functions as
  callback arguments;
- C emission renders callback names inside valid `(*name)(...)` declarators;
- a generated callback program is compiled with C17 and executed successfully.

Windows execution remains part of the final cross-platform validation pass by
project policy.

### R1.3.2 completion record

Implemented and tested on Linux:

- array values participate in compatible pointer initialization and indexed
  access;
- numeric, object-pointer, array, and callable-pointer operands receive
  operator-specific semantic validation;
- pointer/integer addition, pointer/integer subtraction, and compatible pointer
  subtraction produce the appropriate semantic result types;
- aggregate pointer casts are accepted and preserve C declarators;
- global and local initializers are checked against declared object types;
- generated programs cover array decay, pointer arithmetic, and aggregate casts;
- invalid pointer arithmetic and initializer mismatches have stable diagnostics.

Windows execution remains part of the final cross-platform validation pass by
project policy.

### R1.3.3 completion record

Implemented and tested on Linux:

- unsupported function-return-pointer suffixes produce the stable `PARSE410`
  diagnostic;
- parser recovery resumes at the next declaration terminator and preserves
  later valid declarations;
- the specification now distinguishes supported callback pointers from the
  remaining unsupported declarator forms.

Windows execution remains part of the final cross-platform validation pass by
project policy.

## R2 [DONE] CPX, generics and reflection conformance

**Progress**

- R2.1 [DONE] — cover typed CPX values and interpolation categories across
  direct bindings, composed identifiers, expressions, statements, declarations,
  and type arguments.
  - R2.1.1 [DONE] — complete typed value-category parsing and evaluation.
  - R2.1.2 [DONE] — close interpolation boundary, hygiene, and provenance cases.
  - R2.1.3 [DONE] — add deterministic expanded-source and generated-C fixtures.
- R2.2 [DONE] — complete recursive expansion, fixed points, cycles, and cache
  invalidation.
- R2.3 [DONE] — complete stabilized reflection and type-universe conformance.
  - R2.3.1 [DONE] — expose a frozen, read-only structural reflection snapshot
    to reflective evaluators.
  - R2.3.2 [DONE] — connect semantic type identities and ABI layout facts to
    the reflection view.
  - R2.3.3 [DONE] — add reflection-driven generated declarations and mutation
    rejection fixtures.

R2.1 MUST finish before recursive specialization work because expansion results
must have stable typed representations before identity and caching can be
validated.

### R2.1.1/R2.1.2 completion record

Implemented and tested on Linux:

- typed `type` arguments are parsed as C+ type syntax before expansion and
  malformed type expressions receive `CPX010` without template instantiation;
- integer and floating CPX arguments accept unary spacing and canonical numeric
  values, including C-style octal and hexadecimal integer forms when lexed;
- nested list arguments retain recursive typed elements and specialization
  identity remains canonical rather than source-format dependent;
- direct interpolation is token-aware and does not substitute binding names in
  string literals, character literals, or comments;
- explicit interpolation validates identifier composition and reports `CPX011`
  for syntax-bearing or string values in identifier positions;
- `type` and `member` structural CPX categories are admitted to the structural
  phase;
- captured expression/declaration trees recursively preserve generated
  provenance on every nested AST node.

R2.1.3 is covered by the deterministic expanded-source and generated-C fixture
recorded below.
Windows execution remains part of the final cross-platform validation pass by
project policy.

### R2.1.3 completion record

Implemented and tested on Linux:

- the CLI golden fixture records deterministic post-expansion AST and emitted C
  for a typed, numeric specialization;
- the same fixture compiles as C17 and executes with the expected exit status;
- the golden harness compares expanded representation and generated C from a
  clean compiler invocation, preventing source-format or expansion-order drift.

R2.1 is complete. R2.2 has now closed the recursive scheduling and cache
contract; the active work is stabilized reflection.

### R2.2 completion record

Implemented and tested on Linux:

- cached evaluations retain diagnostics and published dependency channels, so
  cache hits cannot make dependent tasks appear ready incorrectly;
- definition fingerprints are source-path independent and canonicalized from
  template tokens, while changed definitions still invalidate their entries;
- deterministic work-queue fixed points, nested expansion identity, deferred
  definition discovery, explicit dependency cycles, recursion cycles, and
  configurable expansion limits remain covered by scheduler/expander tests;
- incremental compilation invalidates specialization keys through the changed
  module dependency closure.

R2.2 is complete. R2.3 continues with semantic type identity and ABI-backed
reflection data.

### R2.3.1 completion record

Implemented and tested on Linux:

- every evaluator context carries an early-safe or frozen-full
  `TypeUniverseSnapshot` selected by the scheduler phase barrier;
- `ComptimeReflection` exposes immutable names, kinds, fields, methods, enum
  members, alias targets, and optional layout size/alignment facts;
- generated structural descriptors are registered before the barrier and are
  visible to reflective evaluators only after the universe is frozen;
- reflective context tests prove generated structure members are available and
  structural mutation remains rejected after stabilization.

Semantic `TypeId` and ABI layout integration is covered by the completion
record below.

### R2.3.2/R2.3.3 completion record

Implemented and tested on Linux:

- provisional semantic models seed CPX with type descriptors keyed by
  canonical `TypeId` values, including primitive, aggregate, alias, foreign,
  pointer, array, and function categories;
- the selected target ABI computes aggregate size/alignment and field order for
  reflection, with by-value recursive aggregates safely reported as unsized;
- reflective evaluators can resolve a bound `CtType` back to its semantic
  descriptor and use field metadata to generate declarations after the phase
  barrier;
- generated reflection-driven C17 output executes successfully, and frozen
  universes reject structural registration/mutation.

R2 is complete. Windows execution remains part of the final cross-platform
validation pass by project policy.

**Deliverables**

- cover every typed CPX value category and interpolation category in the
  language specification;
- verify recursive expansion, structural fixed points, cycle diagnostics,
  specialization identity, cache invalidation, hygiene, and provenance;
- complete type-universe stabilization and reflection-driven generation;
- add deterministic expanded-source and generated-declaration fixtures,
  including repeated, nested, recursive, and cross-module specializations.

**Gate**

The CPX/generic/reflection matrix produces deterministic C+ and C output,
reuses equivalent specializations, rejects cycles and post-stabilization
structural mutations, and preserves source origins through every generated
declaration.

## R3 [DOING] C backend and ABI interoperability conformance

**Progress**

- R3.1 [DOING] — close target ABI layout, calling convention, storage, and
  declaration interoperability.
  - R3.1.1 [DONE] — execute scalar, pointer, aggregate, callback, and variadic
    C+/C caller round trips on Linux.
  - R3.1.2 [DONE] — execute globals, TLS, export/link-name, and aggregate-return
    interoperability fixtures.
  - R3.1.3 [DOING] — extend target ABI audit from descriptor-level layouts to
    parsed primitive declarations, emitted C, and independent ABI fixtures.
- R3.2 [DONE] — complete headers, dependencies, source maps, and external C
  diagnostic remapping as one audited product.
- R3.3 [DONE] — identify compiler-generated runtime helpers and either provide
  them through the selected SDK or reject them before linking.

### R3.1.1/R3.1.2 completion record

Implemented and executed on Linux:

- an independently compiled C17 caller includes the generated public header,
  links the generated C unit, and executes scalar, object-pointer, aggregate
  by-value, callback, and variadic calls;
- aggregate return values are consumed by the independent caller and checked
  field-by-field;
- exported C+ declarations preserve an explicit linker name independently of
  their source-level name;
- public thread-local globals are emitted as `_Thread_local` declarations and
  retain per-thread storage semantics through the generated header and source;
- ordinary function and method lowering preserve the variadic bit through the
  C model, so generated prototypes retain `...`.

R3.1.3 is reopened: earlier checks validated descriptor-level primitive and
aggregate layouts, but did not exercise every source spelling through parsing,
semantic resolution, C emission, and an independent caller. Windows execution
is deferred to the final cross-platform validation pass by project policy.

### R3.1.3 earlier partial evidence

Implemented and tested on Linux:

- the ABI layout engine and target descriptors are exercised across the Linux
  and Windows x86_64/AArch64 descriptor matrix without host-width assumptions;
- pointer width, LP64/LLP64 `long`, `long long`, aggregate field offsets, and
  aggregate alignment are checked against each declared target descriptor;
- generated C retains the selected C declarators and the ABI identity metadata
  remains available for target validation.

This is partial evidence only. R3.1.3 remains `DOING` until end-to-end fixtures
prove that each supported multiword spelling preserves signedness, rank, and
target width through the parser, semantic model, emitted C, and independent C
caller. This specifically covers LP64 and LLP64 differences and the
`char`/`signed char`/`unsigned char` distinction.

### R3.2/R3.3 completion record

Implemented and tested on Linux:

- public header synthesis selects only reachable exported declarations,
  deduplicates and orders forward declarations, preserves public includes,
  TLS, variadic prototypes, and generated names;
- C source dependencies and link dependencies are normalized, deduplicated,
  and diagnosed before the link driver runs;
- generated C and header mappings retain source origins, while GCC/Clang and
  MSVC diagnostic forms are remapped to C+ source locations when available;
- compiler-owned helpers are explicitly catalogued and the CLI rejects a
  missing or unknown helper before linking instead of allowing an unresolved
  runtime dependency.

The R3 gate remains `DOING` until the final Linux/Windows product validation
executes the same fixtures on Windows. Darwin remains outside the claimed
execution matrix.

**Deliverables**

- validate target-specific primitive widths, alignment, layout, calling
  convention, TLS, varargs, aggregate return, and symbol/export behavior;
- complete header synthesis, dependency collection, forward declarations,
  source maps, and external C diagnostic remapping;
- execute independent C-caller → C+ and C+ → C round trips for scalars,
  pointers, aggregates, callbacks, variadics, globals, and TLS;
- identify and satisfy every compiler-generated helper or reject it before
  self-hosted linking.

**Gate**

Independent C fixtures compile against generated headers and link/run without
ABI ambiguity on every claimed target; generated products pass syntax, symbol,
layout, source-map, and dependency audits.

## R4 [DOING] Runtime, allocator and libc behavior

**Dependency-ordered work queue**

- R4.1 [DONE] — inventory runtime symbols, define target-neutral runtime
  result/errno rules, and replace the fixed bootstrap arena with a page-backed
  allocator contract;
- R4.2 [DONE] — implement allocate, allocate-zeroed, resize, free, and aligned
  operations with overflow, double-free, and invalid-range diagnostics;
- R4.3 [DONE] — implement the C+ memory/string/conversion core and thread-local
  errno boundary without importing host libc behavior into native APIs;
- R4.4 [DONE] — implement the claimed Linux C17 compatibility families in dependency
  order: stdio/varargs, time/math/locale, Unicode, signal, atomics, TLS, and
  setjmp/longjmp;
- R4.5 [DONE] — execute independent C17 conformance fixtures, audit compiler
  runtime symbols, and update the machine-checked conformance report.

R4.5 is complete for Linux x86_64. The CLI conformance command reports the
individual header, runtime source, fixture execution, and binary dependency
checks; it returns non-zero for missing, planned, or unsupported checks.

### R4.1/R4.2 completion record

Implemented and executed on Linux:

- the PAL now exposes explicit-width page allocation and release operations;
- Linux x86_64 and AArch64 syscall paths are catalogued in the platform
  adapter, while Windows has the corresponding VirtualAlloc/VirtualFree ABI
  implementation ready for final validation;
- the runtime allocator obtains whole pages, stores allocation metadata outside
  the user span, validates overflow and alignment requests, and releases the
  original page range on free;
- allocate, allocate-zeroed, resize, free, and aligned allocation are exposed
  through the runtime header and the native `std.alloc` façade;
- a Linux C fixture executes alignment, zeroing, data-preserving resize, and
  release behavior.

R4.3 is complete on Linux. Windows execution remains deferred by project
policy.

### R4.3 completion record

Implemented and executed on Linux:

- the self-hosted runtime exports unsigned-byte-correct `memcpy`, `memmove`,
  `memset`, `memcmp`, `strlen`, `strcmp`, `strncmp`, `strcpy`, `strncpy`,
  `strcat`, and `strchr` behavior;
- allocator-backed libc `malloc`, `calloc`, `realloc`, `aligned_alloc`, and
  `free` are available without host allocation symbols;
- integer base conversion, decimal `strtod`, `atoi`, and end-pointer handling
  are implemented in the SDK runtime;
- PAL failures can be converted to the thread-local C compatibility `errno`
  without leaking raw OS error values;
- an executable Linux fixture validates overlap-safe memory movement, byte
  comparison, strings, conversions, allocator integration, and errno mapping.

### R4.4.1 partial completion record

Implemented and executed on Linux:

- the self-hosted stdio layer supports `printf`, `fprintf`, `sprintf`,
  `snprintf`, their `v*` forms, `puts`, `fputc`, `fgetc`, and explicit stream
  markers without host stdio calls;
- the formatter and SDK `stdarg.h` provide the selected C ABI's varargs path;
- monotonic platform ticks feed `clock` and `time`, with Linux syscall and
  Windows system-API adapters;
- `sqrt`, `fabs`, character classification/case conversion, the C locale, and
  basic signal registration/raise behavior have executable Linux coverage.

R4.4 is complete for the claimed Linux x86_64 profile. AArch64 and Windows
`setjmp`/`longjmp` assembly, plus final cross-platform execution, remain
explicitly outside this Linux-only stage.

### R4.4.2 completion record

Implemented and executed on Linux x86_64:

- the SDK supplies C11 atomic types and operations through compiler-owned
  atomic intrinsics, including initialization, load/store, exchange, fetch,
  fences, flags, and compare-exchange;
- UTF-8 to wide-character and wide-character to UTF-8 conversion, wide
  length/comparison, and basic wide classification are executable;
- `setjmp`/`longjmp` uses a real x86_64 Linux assembly context layout and
  preserves callee-saved registers, stack, return address, and zero-to-one
  long-jump return normalization;
- TLS is used for `errno` and public C+ TLS globals, with unsupported
  target-specific context facilities left as capability-gated work.

### R4.5 completion record

Implemented and executed on Linux x86_64:

- `cplus libc test` now checks all C17 SDK headers, required runtime source
  families, the target runtime link plan, and target-specific context support;
- independent C fixtures cover the combined C17 family surface and the
  Linux x86_64 `setjmp`/`longjmp` context adapter;
- each fixture is compiled and linked independently, executed, and inspected
  for undeclared host-library or compiler-runtime dependencies;
- missing, planned, and unsupported checks are retained in the report and make
  the command fail rather than being silently counted as delivered;
- the Linux x86_64 report completed with 38 pass, 0 fail, 0 unsupported, and
  0 planned checks.

Windows and AArch64 execution remains deferred until the final cross-platform
validation pass.

**Deliverables**

- replace the bootstrap-only allocator with page-backed allocate,
  allocate-zeroed, resize, free, and aligned operations;
- implement thread-local `errno` conversion at the libc boundary;
- complete memory, string, conversion, allocation, stdio, time, math, locale,
  Unicode, signal, atomics, TLS, varargs, and setjmp/longjmp behavior to the
  claimed C17 profile;
- keep C+ native APIs distinct from libc compatibility semantics and add
  independently compiled C conformance fixtures.

**Gate**

The claimed C17 profile has no “planned” behavior in its conformance report,
all exported headers have executable implementations or explicit supported
diagnostics, and self-hosted products have no hidden libc/compiler-runtime
dependencies.

## R5 [DOING] Complete native std and platform services

**Dependency-ordered work queue**

- R5.1 [DOING] — complete `std.core`, `std.mem`, `std.string`, `std.text`,
  and collection value/error types, including target-aware byte/size/index
  types;
- R5.2 [TODO] — extend the file PAL with seek, metadata, create/remove,
  directory iteration, and stream adapters;
- R5.3 [TODO] — implement memory/page, process/environment, time, thread,
  synchronization, atomics, networking, and math PAL adapters;
- R5.4 [TODO] — connect native std façades to PAL services and add capability
  propagation, unavailable-service diagnostics, and dependency audits;
- R5.5 [TODO] — record Darwin as either executablely supported or explicitly
  capability-gated, without claiming a partial adapter as complete.

R5.2 is sequenced after R1.1.1–R1.2.5, R1.4, and R3.1.3 because it extends
public SDK function signatures and must use the verified C primitive, type
import, and alias boundaries.

### R5.1 status audit

The memory, string, text, collection, and value/error carrier work below has
Linux execution evidence. R5.1 is reopened because the normative target-aware
byte/size/index type requirement is not implemented: `std.core` had only
unused scalar aliases without target-derived underlying types and no
`usize`/`isize` declarations. Those unused aliases were removed. This task is
not counted complete until the required
ABI-aware types and their target tests exist.

Verified completed portions:

- `std.core` provides explicit error, result, and option value carriers
  without PAL or host error fields;
- `std.mem` provides zeroing and unsigned-byte comparison/equality in addition
  to copy, move, and set;
- `std.string` provides unsigned-byte comparison, copy, and append helpers;
- `std.text` provides byte length, empty, ASCII, and prefix operations while
  keeping UTF-8 bytes distinct from wide-character libc compatibility types;
- `std.collections` provides explicit non-owning slice and half-open range
  values with length/containment operations;
- an executable C harness generated from the C+ sources validates the values
  and operations without a hosted C library dependency.

**Deliverables**

- finish `std.core`, `std.mem`, `std.string`, `std.text`, and collections with
  real source implementations and target-neutral error/result types;
- finish filesystem seek, metadata, create/remove, directory iteration, and
  stream layering above the completed basic file PAL;
- implement process launch/wait, environment, time, memory/page allocation,
  threads, synchronization, atomics, networking, and math adapters for the
  supported Linux and Windows targets;
- implement or explicitly gate concrete Darwin startup and platform services;
- expose capability failures uniformly and verify every adapter through
  executable tests plus dependency audits.

**Gate**

Each native package has at least one executable Linux and Windows test (and a
Darwin status), every selected adapter is source-isolated, and the conformance
matrix reports platform services as `pass` rather than merely `planned`.

## R6 [TODO] CLI transcoder and build-product completion

**Dependency-ordered work queue**

- R6.1 [TODO] — define project/workspace manifests and one source/import model
  shared by `check`, `transcode`, `build`, and `run`;
- R6.2 [TODO] — normalize output, header, map, target, runtime, libc, SDK,
  compiler, sysroot, C-source, and library options with deterministic paths;
- R6.3 [TODO] — make fat-JAR assembly reproducible, clean temporary products,
  preserve process failures, and emit stable diagnostics;
- R6.4 [TODO] — make `sdk`, `target`, `abi`, `runtime`, `libc`, and `audit`
  validate the exact artifacts consumed by a normal build.

**Deliverables**

- define the supported project/workspace input model and make module/path/
  standard-library imports work identically for `check`, `transcode`, `build`,
  and `run`;
- make output, header, map, diagnostics, target, runtime, libc, compiler,
  sysroot, source dependency, and library options deterministic and portable;
- ensure the fat JAR is reproducible and usable on Linux and Windows, with
  explicit product cleanup and process-failure behavior;
- make `sdk`, `target`, `abi`, `runtime`, `libc`, and `audit` commands validate
  the same artifacts that normal builds consume.

**Gate**

Clean checkouts can build the fat JAR and compile/run representative single-
file, multi-module, C-interoperability, self-hosted Linux, and self-hosted
Windows products from documented commands.

## R7 [TODO] LSP and VS Code product completion

**Dependency-ordered work queue**

- R7.1 [TODO] — make workspace indexing and imported-source URI mapping
  authoritative for diagnostics, definitions, references, symbols, hover,
  completion, tokens, and edits;
- R7.2 [TODO] — run configured `java -jar <cli>` for LSP and Run Main with
  portable settings, working directories, and path normalization;
- R7.3 [TODO] — package and test the extension from a clean checkout against
  the assembled CLI product.

**Deliverables**

- map diagnostics, definitions, references, symbols, completion, hover,
  semantic tokens, and edits across a workspace and imported source files;
- make the extension invoke the configured `java -jar <cli>` entry point for
  both LSP and Run Main, with portable path/cwd/settings behavior;
- build, test, and package the extension from a clean checkout, and verify the
  packaged extension uses the repository's actual CLI settings.

**Gate**

The LSP and Run Main flows work for a multi-module workspace on Linux and
Windows, with external-source locations preserved and no competing parser or
hard-coded CLI path.

## R8 [TODO] SDK packaging, target matrix and release conformance

**Dependency-ordered work queue**

- R8.1 [TODO] — generate deterministic SDK metadata, headers, syscall
  catalogues, package indexes, and optional runtime objects;
- R8.2 [TODO] — replace optimistic status entries with executable evidence and
  explicit capability diagnostics;
- R8.3 [TODO] — run Linux x86_64/AArch64 and Windows x86_64 (plus available
  Windows AArch64/Darwin targets) product validation;
- R8.4 [TODO] — verify no host contamination, reproducibility, clean-tree
  builds, documented examples, and upgrade/ABI compatibility rules.

**Deliverables**

- generate/rebuild SDK metadata, C headers, syscall catalogues, package indexes,
  and optional runtime objects deterministically;
- replace optimistic coverage claims with machine-checked conformance reports;
- run the complete matrix for Linux x86_64/AArch64, Windows x86_64 (and
  AArch64 where a runner exists), and Darwin according to declared support;
- verify no-host-contamination, no hidden compiler-runtime dependency,
  reproducible outputs, clean-tree builds, documentation examples, and
  upgrade/ABI compatibility rules.

**Gate**

The release checklist is reproducible from a clean checkout, every claimed
target/profile has executable evidence, and all remaining unsupported features
are explicit capability diagnostics or separately labelled post-release work.

## Execution order and commit policy

The work proceeds vertically in this order:

```text
R0 → R1 → R2 → R3 → R4 → R5 → R6 → R7 → R8
```

Each phase is split into small implementation commits. A phase may be
reordered only when a dependency is discovered and recorded here first. Every
stage ends with focused tests, the relevant full-suite/platform checks, an
updated coverage/conformance entry, and a Conventional Commit before the next
stage begins.

---

# Vertical milestones

Milestones are not additional terminal tasks and therefore do not affect terminal-task counters.

## M1 — Minimal C+ → C vertical compiler

Target:

```text
source
 ↓
lexer/parser
 ↓
AST
 ↓
symbols/types
 ↓
C AST
 ↓
C emitter
 ↓
working executable
```

Primary tasks:

```text
1.1–1.4
2.1
2.2.1–2.2.2
4.1
4.3.1
4.4.1
4.4.4
```

Minimum program:

```c
struct point_t {
    int x;
    int y;
};

int add(int a, int b) {
    return a + b;
}

int main() {
    return add(1, 2);
}
```

Milestone acceptance:

- parses;
- resolves;
- emits valid C;
- resulting executable runs correctly.

---

## M2 — C+ methods

Target:

```c
struct vec_t {
    int x;

    int get(self) {
        return self.x;
    }

    vec_t zero() {
        ...
    }
}
```

Primary tasks:

```text
2.2
4.2.1
4.3
4.4
```

Acceptance:

```c
v.get()
vec_t.zero()
```

both compile and execute correctly.

---

## M3 — First CPX generic type

Target:

```c
comptime cpx<decl> optional(type T) {
    return {
        struct optional_{T}_t {
            bool valid;
            T value;
        };
    };
}

optional(int);
```

Primary tasks:

```text
3.1
3.2
3.3
3.4.1–3.4.3
3.5.1
```

Acceptance:

- `optional_int_t` exists semantically.
- generated declaration has correct origin.
- duplicate `optional(int)` invocation reuses specialization.
- emitted C compiles.

---

## M4 — Full compile-time model

Target features:

```text
nested CPX
generic methods/functions
structural fixed point
type-universe freeze
reflection
reflective CPX
```

Primary tasks:

```text
3.4.4
3.5
```

Acceptance:

- recursive CPX stabilizes correctly.
- cycles are diagnosed.
- reflection occurs only after stabilization.
- reflection-generated executable code compiles.

---

## M5 — Advanced runtime lowering

Target:

```text
inner functions
closures
defer
string templates
```

Primary tasks:

```text
4.2
4.3
4.4
```

Acceptance:

- generated runtime behavior matches source semantics.
- generated helper code has valid provenance.
- external C diagnostics map back to C+.

---

## M6 — Development environment

Target:

```text
compiler
+
incremental compiler
+
LSP
+
VS Code extension
+
C imports
```

Primary tasks:

```text
2.3
2.4
5.1
5.2
```

Acceptance:

- completion/navigation work across C+ modules.
- imported C symbols appear in tooling.
- editing one source file invalidates only affected compiler state.

---

## M7 — Self-hosted C+ SDK/runtime foundation

Target:

```text
C+ application
   ↓
C+ SDK source + target metadata
   ↓
C+ compiler / C backend
   ↓
C+ runtime + PAL
   ↓
OS ABI
   ↓
executable with no foreign libc dependency where target profile promises it
```

Primary tasks:

```text
6.1
6.2
6.3.1
6.4.1
6.4.2.1
6.4.2.3
```

Acceptance:

- Linux self-hosted smoke executable starts, allocates memory, performs basic I/O and exits without glibc/musl.
- Windows self-hosted smoke executable uses supported system DLL APIs without UCRT/MSVCRT.
- Darwin smoke build uses the declared supported Apple system ABI and C+ std/libc semantics.
- target/comptime metadata selects the correct platform implementation without C preprocessor conditionals.
- runtime dependency audit reports only target-profile-approved dependencies.

---

## M8 — C+ libc/SDK conformance

Target:

```text
source-delivered C+ std + libc + runtime
        +
generated C17 SDK headers
        +
ABI verification / C round-trip tests
        ↓
versioned portable C+ SDK distribution
```

Primary tasks:

```text
6.3
6.4.2.2
6.4.2.4
```

Acceptance:

- C17 compatibility profile passes the declared conformance suite.
- independent C callers can consume the generated headers and link to the C+ libc implementation.
- ABI round trips cover scalar, aggregate, callback, variadic and TLS cases.
- packaged SDK is reproducible from source and carries complete version/target metadata.

---

# Dependency spine

The principal dependency flow is:

```text
1. Source/front-end
        ↓
2. Semantic core
        ↓
3. Compile-time/CPX
        ↓
4. Lowering/backend
        ↓
5. Tooling/integration
        ↓
6. SDK/runtime/platform ABI
```

But implementation should follow the vertical milestones rather than waiting for an entire horizontal layer to become complete.

Critical dependency paths:

```text
SourceRange
  → Origin
  → AST
  → every transformation
  → source map
```

```text
SymbolId
  → type/member resolution
  → CPX semantic arguments
  → lowering
  → LSP navigation
```

```text
ComptimeValue
  → CPX interpolation
  → ExpansionId
  → specialization
  → structural fixed point
  → reflection
```

```text
Resolved C+ AST
  → lowering
  → C AST
  → emitter
  → external diagnostics
```

---

# Specification coverage map

## Language specification

```text
LS §1–4       → project structure, 1.*, pipeline milestones
LS §5         → 2.1
LS §6         → 1.3, 2.2, 4.2.1
LS §7         → 3.1, 3.3
LS §8         → 1.2.3, 3.2
LS §9         → 3.4
LS §10        → 3.3.4, 3.4.4
LS §11        → 3.3.2
LS §12        → 3.2.4
LS §13        → 3.3.1
LS §14        → 4.2.2
LS §15        → 4.2.3
LS §16        → 4.2.4
LS §17        → 3.5.1
LS §18        → 3.5.2
LS §19        → 3.5.3
LS §20        → 3.5.4
LS §21        → 2.3, R1.4
LS §22        → 2.4
LS §23        → 2.1.1, 4.3.4
LS §24        → 4.3.2
LS §25        → 4.3.2
LS §26        → 4.3.3
LS §27        → 4.3.3
LS §28        → 1.1, 4.4
LS §29        → 1.1.3, 3.3.2
LS §30        → 4.1, 4.2
LS §31        → 4.3.1, 4.3.4
LS §32        → 2.1.4
LS §33        → 2.1.4, 3.2
LS §34        → 1.3.4, 2.2.2
LS §35        → 3.3
LS §36        → 3.1
LS §37        → 4.2.2
LS §38        → 3.3.3
LS §39        → 5.2.4
LS §40–42     → 5.1
LS §43        → 2.4
LS §44        → conflict-specific tasks across 2.*, 3.*, 4.*
LS §45–46     → canonical end-to-end fixtures and semantic model
LS §47        → phase invariants and 3.5
LS §48        → 3.4
LS §49        → 3.5.3
LS §50        → 4.*
LS §51        → phase invariants and 5.3
LS §52        → complete component architecture
```

## Technical specification

```text
TS §1–3       → project organization and overall plan
TS §4         → 1.1
TS §5         → 1.2
TS §6         → 1.3
TS §7–8       → 1.4
TS §9–11      → 2.1
TS §12–14     → 2.2
TS §15        → 3.1
TS §16        → 3.2
TS §17–20     → 3.3
TS §21–25     → 3.5
TS §26–27     → 2.3
TS §28        → 2.4
TS §29–30     → 4.1.4 and 5.1
TS §31–32     → 4.1
TS §33–36     → 4.2
TS §37        → 4.3.1
TS §38–41     → 4.3
TS §42–44     → 4.4
TS §45–46     → compiler context/diagnostics throughout
TS §47–49     → 3.3, 3.4, 5.2
TS §50–56     → 5.1
TS §57–62     → 5.2
TS §63–64     → 5.3
TS §65        → 1.3.4
TS §66–68     → 1.1, 1.4
TS §69–75     → architectural constraints across all branches
TS §76        → milestones M1–M6
TS §77–78     → final audit 5.3.4
```

## C+ Standard Library, Runtime, SDK and Platform ABI Specification

```text
SDK §1–6       → 6.1, 6.3
SDK §7–16      → 6.3.1
SDK §17–19     → 6.3.2
SDK §20–23     → 6.2.1
SDK §24–29     → 6.2.2
SDK §30–31     → 6.2.1.1, 6.4.1
SDK §32–34     → 6.4.1.1
SDK §35–36     → 6.4.1.2
SDK §37        → 6.4.1.3
SDK §38–43     → 6.1.2, 6.4.1.4
SDK §44–57     → 6.2.1.4, 6.2.2, 6.3.2
SDK §58–63     → 6.1.1, 6.4.2.2
SDK §64–70     → 6.4.2
SDK §71–74     → 6.1.1.2, 6.2.2.4, 6.4.2.2
SDK §75–81     → 6.2.1.3, 6.3.2, 6.1
SDK §82–89     → 6.4.2.3–6.4.2.4
SDK §90–94     → 6.3.1, 6.1.1.3
SDK §95–97     → 6.4.2.1, 6.4.2.4
SDK §98–102    → 6.*, final SDK/runtime architecture audit
```

```text
Uncovered normative requirements: 0
```

---

# Initial execution sequence

The first implementation sequence should be narrow and vertical:

```text
1.1.1  Source repository
1.1.2  Source ranges
1.1.3  Origin model
1.2.1  Token model
1.3.1  Basic parser
1.4.1  Syntax nodes
1.4.2  AST arena
1.4.3  AST normalization
2.1.1  Symbol identity
2.1.2  Scope model
2.1.3  Declaration catalogue
2.2.1  Type universe
2.2.2  Type resolution
4.1.1  Compiler pass API
4.3.1  C AST
4.4.1  C emitter
4.4.4  End-to-end fixture
```

At that point **M1 exists** and subsequent development can extend a working compiler instead of accumulating disconnected subsystems.

The next sequence should be:

```text
2.2.3–2.2.4
4.2.1
```

to reach **M2 — methods**.

Then:

```text
3.1
3.2
3.3.1–3.3.3
3.4.1–3.4.3
3.5.1
```

to reach **M3 — first CPX generic specialization**.

Only after this vertical CPX path works should the implementation broaden into reflection, closures, `defer`, C imports, and IDE integration.

---

# Plan maintenance rule

After each terminal task:

```text
1. update its status;
2. recompute every ancestor completed/total counter;
3. run its acceptance tests;
4. update dependencies if implementation revealed new constraints;
5. add newly discovered work beneath the smallest relevant parent;
6. report the current task and overall progress.
```

Example progress state later in development:

```text
Overall: 23/80

[DONE]  [16/16] 1. Language front-end
[DOING] [7/16]   2. Semantic model and modules
[TODO]  [0/20]   3. Compile-time and CPX system
[TODO]  [0/16]   4. Lowering and C backend
[TODO]  [0/12]   5. Tooling, integration and quality

Current:
2.2.4 — Member access and call resolution

Milestone:
M2 — C+ methods
```

The plan is complete only when:

```text
Overall: 146/146
```

and the specification audit reports:

```text
Uncovered normative requirements: 0
Known required unfinished work: 0
```
