# C+ Compiler and Language Tooling — Implementation Plan

## Dashboard

```text
Overall: 53/100

[DOING] [11/16] 1. Language front-end
[DOING] [26/30] 2. Semantic model and modules
[DOING] [6/20] 3. Compile-time and CPX system
[DOING] [10/22] 4. Lowering and C backend
[DOING] [1/12] 5. Tooling, integration and quality

Current task:
4.2.4.3.2.2 — remaining expression and lvalue lowering

Current milestone:
M5 — Advanced runtime lowering
```

All tasks initially have status `TODO`.

`completed/total` counts terminal tasks in the complete subtree.

---

# 1. [TODO] [0/16] Language front-end

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

## 1.2 [DOING] [2/4] Lexer

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

### 1.2.3 [TODO] CPX interpolation lexical rules

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

### 1.2.4 [TODO] Lexical diagnostics and incremental relexing boundary

**Technical**
- TS §5
- TS §47 Incremental Compilation
- TS §65 Error Recovery

**Acceptance**
- Unterminated literals/comments report ranges.
- Lexer recovers sufficiently to continue parsing.
- API supports relexing changed documents without exposing mutable global state.

**Depends**
- 1.2.3

---

## 1.3 [DOING] [1/4] Grammar and parser

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

### 1.3.2 [TODO] Expression parser and member syntax

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

### 1.3.3 [TODO] C+ declaration grammar

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

### 1.3.4 [TODO] Parser recovery and semantic ambiguity preservation

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

# 2. [DOING] [1/16] Semantic model and modules

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

## 2.1 [DOING] [1/4] Declaration catalogue, symbols and scopes

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

### 2.1.2 [TODO] Lexical scope table

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

### 2.1.3 [TODO] Declaration catalogue construction

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

### 2.1.4 [TODO] Name lookup and conflict resolution

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
- method without `self` is `STATIC`.
- methods remain associated with their containing semantic type.

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

## 2.3 [DOING] [7/8] Packages and C+ imports

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
all create correct bindings.

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

## 2.4 [DOING] [7/9] C interoperability model

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

#### 2.4.2.1 [DONE] Bootstrap `c.stdio` foreign import adapter

**Acceptance**
- selective `c.stdio` imports register `printf` as a variadic foreign function.
- the C backend emits the corresponding standard-library include.
- an imported call compiles and executes through the CLI path.

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
- declarations needed by a C+ translation unit are visible without parsing C definitions as C+ declarations.
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

### 2.4.4 [DOING] [1/2] Foreign-symbol semantic tooling integration

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

#### 2.4.4.2 [TODO] Integrate foreign symbols with language-server features

**Acceptance**
- completion includes imported C symbols.
- hover and signature help expose foreign type/signature information.
- go-to-definition uses retained source/header origins when available.

**Depends**
- 2.4.4.1
- 5.1

---

# 3. [TODO] [0/20] Compile-time and CPX system

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

## 3.1 [TODO] [0/4] Compile-time values and arguments

### 3.1.1 [TODO] Compile-time scalar and entity values

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

### 3.1.2 [TODO] `CtType` and semantic type arguments

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

### 3.1.3 [TODO] Expression, statement and declaration compile-time values

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

### 3.1.4 [TODO] Canonical compile-time value encoding

**Technical**
- TS §19
- TS §49

**Acceptance**
- compile-time values can produce deterministic canonical cache keys.
- equal semantic type arguments produce equal specialization keys.
- distinct values cannot accidentally alias.

**Depends**
- 3.1.1–3.1.3

---

## 3.2 [DOING] [3/4] CPX template representation and interpolation

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

### 3.2.4 [TODO] CPX hygiene and injected-name rules

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

---

## 3.3 [DOING] [1/4] Evaluator, scheduler and expansion identity

### 3.3.1 [TODO] `ComptimeContext` and evaluator API

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

### 3.3.2 [TODO] Expansion identity and evaluation keys

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

---

## 3.4 [DOING] [1/4] Generics and recursive specialization

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

### 3.4.2 [TODO] Generic specialization identity

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

### 3.4.3 [TODO] Specialization cache

**Technical**
- TS §49

**Acceptance**
- specialization cache is deterministic.
- dependency invalidation is recorded.
- cache does not suppress required diagnostics.

**Depends**
- 3.4.2

### 3.4.4 [TODO] Recursive/nested CPX expansion

**Language**
- LS §10.1–10.2

**Acceptance**
- generated CPX invocation sites are scheduled.
- expansion continues until the phase fixed point.
- nested origins retain complete ancestry.

**Depends**
- 3.3
- 3.4.1

---

## 3.5 [TODO] [0/4] Structural stabilization and reflection

### 3.5.1 [TODO] Structural fixed-point engine

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

### 3.5.2 [TODO] Type-universe stabilization barrier

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

### 3.5.3 [TODO] Structured reflection API

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

### 3.5.4 [TODO] Reflective CPX phase

**Language**
- LS §20 Reflective Compile-time Generation

**Acceptance**
- reflective CPX can consume stable type metadata.
- it may generate allowed executable/data constructs.
- forbidden structural mutations produce diagnostics.

**Depends**
- 3.5.3
- 3.3

---

# 4. [DOING] [10/22] Lowering and C backend

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

## 4.1 [TODO] [0/4] Compiler pass and rewrite framework

### 4.1.1 [TODO] Compiler context and pass API

**Technical**
- TS §31
- TS §45

**Acceptance**
- passes receive explicit `CompilerContext`.
- pass result and diagnostics are explicit.
- passes do not depend on global mutable singletons.

**Depends**
- 1–3 foundational models

### 4.1.2 [TODO] AST rewrite API

**Technical**
- TS §32

**Acceptance**
- replace, insert-before, insert-after and hoist operations exist.
- generated nodes require explicit origin.
- rewrite invalidates semantic indexes in controlled fashion.

**Depends**
- 4.1.1
- 1.4.2

### 4.1.3 [TODO] Pass precondition/postcondition invariant framework

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

### 4.1.4 [TODO] Resolved-AST and reference index integration

**Technical**
- TS §29–30

**Acceptance**
- resolved identifier nodes refer to `SymbolId`.
- transformations can preserve semantic identity.
- reference index can be rebuilt or incrementally updated.

**Depends**
- 2
- 4.1.2

---

## 4.2 [DOING] [7/10] C+ feature lowering

### 4.2.1 [DONE] Method lowering

**Language**
- LS §6
- LS §30

**Technical**
- TS §33

**Acceptance**
- struct methods become top-level callable representations.
- instance calls inject receiver.
- static calls do not inject receiver.
- source method identity is retained.

**Depends**
- 2.2
- 4.1

### 4.2.2 [TODO] Inner functions and closure lowering

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

### 4.2.4 [DOING] [4/5] String-template and remaining expression lowering

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

#### 4.2.4.3 [DOING] [1/2] Complete remaining expression and lvalue lowering

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

#### 4.2.4.3.2 [DOING] [1/2] Complete remaining expression and lvalue lowering

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

##### 4.2.4.3.2.2 [TODO] Complete remaining expression and lvalue lowering

**Acceptance**
- supported C+ expression and lvalue forms have explicit C AST nodes.
- unsupported forms produce diagnostics before emission.

**Depends**
- 4.2.4.3.2.1

---

## 4.3 [DOING] [1/4] C representation and declaration synthesis

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

### 4.3.2 [TODO] Hoisting and forward-declaration synthesis

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

### 4.3.3 [TODO] Header and dependency/include generation

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

### 4.3.4 [TODO] C symbol naming and C-subset validation

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

---

## 4.4 [DOING] [1/4] C emission and source mapping

### 4.4.1 [DONE] Deterministic C emitter

**Technical**
- TS §42

**Acceptance**
- C AST emits formatted deterministic C.
- emitter performs formatting, not semantic transformations.
- output can be round-trip compiled by selected C compiler for supported fixtures.

**Depends**
- 4.3

### 4.4.2 [TODO] Generated-range source-map builder

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

### 4.4.3 [TODO] External C diagnostic remapping

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

### 4.4.4 [TODO] End-to-end C execution fixtures

**Technical**
- TS §64

**Acceptance**
- C+ fixture → generated C → C compiler → executable.
- emitted executable produces expected behavior.
- generated C and source map snapshots are retained.

**Depends**
- 4.4.1–4.4.3

---

# 5. [TODO] [0/12] Tooling, integration and quality

**Purpose:** Make the compiler usable as a development platform through LSP, CLI, incremental compilation, test coverage and specification audits.

**Language**
- LS §40–42
- LS §51–52

**Technical**
- TS §47–64
- TS §75–78

---

## 5.1 [TODO] [0/4] Language server and VS Code integration

### 5.1.1 [TODO] Kotlin LSP workspace/document architecture

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

### 5.1.2 [TODO] Semantic tokens and TextMate integration

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

### 5.1.3 [TODO] Completion and hover

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

### 5.1.4 [TODO] Navigation, references and diagnostics

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
- 4.4.3

---

## 5.2 [DOING] [1/4] CLI, build and incremental compiler

### 5.2.1 [DONE] Public compiler API

**Technical**
- TS §58

**Acceptance**
- `CompileRequest` and `CompileResult` exist.
- compilation can be embedded without invoking CLI.
- result contains diagnostics and generated units.

**Depends**
- 4

### 5.2.2 [TODO] CLI commands

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

### 5.2.3 [TODO] Incremental dependency invalidation

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

### 5.2.4 [TODO] Compiler caches and deterministic concurrency

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

---

## 5.3 [TODO] [0/4] Verification and specification completion

### 5.3.1 [TODO] Layered automated test suites

**Technical**
- TS §63

**Acceptance**
- lexer, parser, AST, semantic, CPX, lowering, source-map and LSP suites exist.
- each suite runs independently.
- negative/error tests are first-class tests.

**Depends**
- incremental throughout project

### 5.3.2 [TODO] Golden compiler fixture framework

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

### 5.3.3 [TODO] Specification coverage audit

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

### 5.3.4 [TODO] Final architecture/invariant audit

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

---

# Vertical milestones

Milestones are not additional terminal tasks and therefore do not affect the `0/80` count.

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
LS §21        → 2.3
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
Overall: 80/80
```

and the specification audit reports:

```text
Uncovered normative requirements: 0
Known required unfinished work: 0
```
