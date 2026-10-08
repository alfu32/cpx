# Import discovery and compile-time traits — Luna 6 Medium handoff

## Start here

This is the active implementation runbook. The current leaf is tracked in
`IMPL.PLAN.md` (currently `R10.3.2.1`); consult that file for status and evidence.
[IMPL.PLAN.md](IMPL.PLAN.md) owns task statuses, acceptance evidence and counters;
do not maintain a second status ledger here.

The requested outcomes are:

- Discover actual SDK/project/header exports and offer stdlib/import completion.
- Offer safe auto-import completion edits and unresolved-name quick fixes.
- Define direct extension methods using exactly
  `comptime trait type_identifier { ... }`.

Implement one terminal task at a time. The R10.1.1 foundation and R10.1.2.1–
R10.1.2.3, R10.2 and R10.3.1 are accepted; resume at **R10.3.2.1**, not by restarting
the parser foundation or jumping ahead to the VS Code UI. Do not restart R0–R9
or rewrite the compiler.

Before editing:

1. Read `AGENTS.md` and `IMPL.PLAN.RULES.md` completely, this runbook, the
   current dashboard, and the selected task plus its referenced spec sections.
2. Run `git status --short` and inspect changes in files you will touch.
   At handoff, `sdk/libc/include/stdio.h` has a user-added `coucou` definition.
   Preserve it; do not stage it or treat it as your test fixture.
3. Read the listed entry points and adjacent tests. Use `rg` to find all
   consumers before changing a shared data class or a sealed declaration type.
4. Select a leaf whose dependencies are accepted. Set only that leaf DOING;
   recompute ancestor statuses/counts and update the dashboard's current task.
5. Add a failing regression for its acceptance condition, then implement only
   that leaf's deliverable. Keep unrelated feature work out of the commit.
6. Run focused tests and compilation of affected dependents. At product gates,
   run the full/native/extension acceptance commands specified by the task.
7. Record the actual command, result and relevant platform evidence beneath
   the task. Mark DONE only when every stated criterion passes. A skipped
   platform test is missing evidence, not a pass.
8. Update all affected aggregates and coverage, inspect the staged diff, then
   make a Conventional Commit with REQUEST, IMPLEMENTATION and NOT IMPLEMENTED
   sections. Follow the repository's required message format.

If an apparent leaf requires another independent feature, split it under its
existing ID first, give each new leaf spec references/tests/dependencies, and
recalculate every affected denominator. Do not create invisible follow-up work
after marking the original leaf DONE.

## Contracts already decided

Read LS §6.3.1, §21–22 and §41.1; TS §13.1, §28 and §54.1;
SPEC.STDLIB.md §58. Here LS means SPEC.LANG.md and TS means SPEC.TECH.md.

- `trait` is singular; there are **no angle brackets** around the target.
  This is direct method implementation, not an interface requirement.
- The target is one visible type identifier, including a single-token primitive
  such as `int`. Use a typedef for `unsigned long long` and an import alias
  for a qualified type. Preserve idiomatic C primitive names; do not add SDK
  integer aliases as a workaround for parsing.
- Initial targets: complete struct/union/enum or non-void primitive, directly
  or through a typedef. Initial exclusions: pointer/array/function/incomplete/
  void target types, fields, nested trait blocks, static methods and
  bodyless requirements.
- Every method has a first `self` or `self*` receiver. Reuse ordinary method
  semantics: `self` exposes the target in the body, `self*` exposes a pointer.
  Existing implicit-self aggregate lowering uses pointer-backed storage;
  do not accidentally replace it with a by-value copy.
- A block is private by default; `pub comptime trait T { ... }` exports the
  block's methods. Per-method visibility is not introduced in this version.
  Direct module imports, including selective imports, activate public
  extensions without implicitly importing other type/value names. Activation
  is not transitive. There is no separately named trait to import.
- Native methods and fields retain their conflict rules. Same-module duplicate
  extension names on a canonical receiver are errors; conflicting imported
  providers give an ambiguity at the call, not an import-order winner.
  Aliases share identity; unrelated nominal types from separate modules do not.
- Source/header declarations are the import authority. A binary library cannot
  supply a callable signature by symbol-name inspection alone. It needs a
  header, source module, or validated typed metadata; link configuration is a
  separate concern.
- C preprocessing follows the selected target/compiler/profile, not the host's
  default libc. A compiler driver is a syntax tool, not a requirement to choose
  glibc/MinGW/MSVC/musl as a runtime. Do not undo the SDK/PAL distribution work.
- CLI, LSP and VS Code share discovery and semantic data. No second parser,
  extension-side catalogue, or hand-maintained library function list.
- Do not implement automatic inference of arbitrary C++ APIs, binary ABIs,
  trait interfaces, overload resolution or runtime dispatch as part of this queue.

The block-level visibility and unsupported-target details above are the planned
v1 contract, not claims about current implementation. If a concrete conflict
with existing semantics is found, update the contract and dependent tasks
explicitly before implementing a different behavior.

## Repository map and known traps

In task file references, `module/.../File.kt` expands to the module's package
directory below. New files named by the plan do not yet exist.

| Module | Kotlin production prefix | Existing test entry points |
| --- | --- | --- |
| language-core | `language-core/src/main/kotlin/cplus/core/` | `AstGoldenTest`, `CPrimitiveTypesTest` |
| semantic | `semantic/src/main/kotlin/cplus/semantic/` | `SemanticTypeTest`, `CHeaderFunctionScannerTest`, `ReferenceIndexTest` |
| comptime | `comptime/src/main/kotlin/cplus/comptime/` | `CpxExpansionTest` |
| compiler | `compiler/src/main/kotlin/cplus/compiler/` | `ModuleSourceResolver`, `CompilerIntegrationTest`, `IncrementalCompilerTest`, `SdkManifestTest`, `AstRewriteTest`, `ClosureLoweringTest` |
| c-backend | `c-backend/src/main/kotlin/cplus/backend/` | `CLowererModuleTypeTest`, `CSubsetValidatorTest` |
| cli | `cli/src/main/kotlin/cplus/cli/` | `CliIntegrationTest`, `LspWorkspaceTest`, `CliGoldenFixtureTest` |

Test source paths use `src/test/kotlin/` with the same package. Add focused new
test classes when this keeps the already-large integration classes readable.

### C discovery: start with compiler context, not another whitelist

- `semantic/CHeaderImport.kt`: the default manual C declaration catalogue has
  been removed. The service parses prepared source declarations; configured
  header text remains only for focused semantic fixtures. Unsupported
  declarators must remain explicit diagnostics.
- `semantic/Semantics.kt`: inspect `registerHeaderDeclaration`,
  `foreignTypeFromName`, `foreignTypeModule`, `resolveImportedFunctions`,
  and the import loop. Library-specific branches such as `c.stdio.printf`
  bypass normal declaration typing. Audit both the catalogue and these branches.
  Keep genuinely compiler-owned `va_list`/ABI/intrinsic knowledge separate.
- `compiler/ModuleSourceResolver.kt` is shared by CLI and LSP for entry/import
  closures, live overlays, selected-SDK `std.*` paths, and bounded candidate
  enumeration. The next tasks add the typed export index and its invalidation.
- `compiler/Compiler.kt`: inspect `CompileRequest`, `CompilerContext`,
  `compile`, `compileIncremental`, `compileTextWorkspace` and **every**
  `SemanticAnalyzer(...)` construction, including provisional CPX analysis.
  Request include roots and SDK fields already exist; selected C driver
  currently flows mainly through the build/link side and must reach discovery.
- `compiler/SdkResolver.kt` provides selected layout paths; `SdkManifest.kt`
  owns SDK discovery. Do not duplicate SDK-locator logic inside semantic/core.
- `compiler/CCompilerToolchain.kt` already classifies GCC, Clang, clang-cl,
  MSVC and TCC and selects target/ABI flags. Reuse its decisions and the
  runtime compile include policy instead of constructing host-only flags.
- Preprocessor adapters must return active declarations, original locations,
  macro information/capability diagnostics, and transitive dependencies.
  Preserve line markers. Never implement this by deleting all `#` lines.
  Test driver option support explicitly; do not assume every driver accepts
  GCC options or require a new Clang-only dependency.
- Process handling must drain bounded output/error streams without deadlock,
  time out/cancel cleanly and use a temporary input under a managed directory.
  Paths/headers are arguments/data, never executable shell fragments.
- Build declaration fixtures in temporary SDK/include trees. A user-defined
  function in a header must be discoverable without registering its name.
  For a multi-translation-unit execution fixture use appropriate linkage
  (for example static inline); copying a non-static header definition into
  generated C would create an unrelated duplicate-definition bug.

Recommended boundary, with concrete names adjustable to local conventions:

```text
compiler: immutable HeaderEnvironment
compiler: CHeaderDiscovery / CHeaderPreprocessor -> prepared source + origins/dependencies
semantic: CHeaderImportService -> typed foreign declaration records
semantic: SemanticAnalyzer consumes those records (no filesystem/process ownership)
compiler: ImportIndex consumes public source exports + foreign declaration records
cli: completion / ImportEdits / code actions consume ImportIndex
```

A missing driver must not silently select another ABI. C+ module suggestions
can still work while C discovery reports an unavailable capability. Any new
preprocessing requirement for `check`/`transcode` must be documented; do not
mask it with the old function-name list. A cached result is usable only when
its source/configuration fingerprints still match.

### Shared modules and editor assistance

- `compiler/ModuleSourceResolver.kt` now owns import-closure discovery for CLI
  and LSP, including initialization roots, SDK `std.*` references and live
  overlays. It compiles only the directed closure from the requested entry.
  `c.*` header discovery remains in the compiler header service.
- `cli/LspServer.kt` now obtains roots and optional SDK manifest from initialize
  parameters. Completion serialization is still limited, and code-action
  dispatch/capabilities are missing; those remain separate planned tasks.
- `cli/LspLanguageService.kt`: `CompletionItem` has only label/kind/detail;
  ordinary completion depends on semantic analysis and member lookup assumes
  aggregates. Add shared import context and edits without breaking existing
  member completion or requiring an error-free document.
- `compiler/SdkMetadata.kt` and `IncrementalCompiler.kt`: review fingerprints
  and cache locations before reuse. Do not turn a source cache into the authority
  for SDK availability or require a writable installed SDK.
- Candidate indexing is **not** compilation of every workspace file. Keep the
  entry-point import closure separate or duplicate-`main` diagnostics return.
- Use real exports: `std_fs_open` is from `std.fs`;
  `std_file_stream_open` is from `std.io`. Do not suggest an imaginary
  `fs` export just because an earlier conversation used it illustratively.
- Handle public type imports and aliases as well as function names. Prefer a
  valid existing alias/qualified binding; do not synthesize colliding names.
- Import edits must use shared syntax/token ranges, LSP UTF-16 positions and the
  current document snapshot. For ambiguous/unsafe formatting, return no edit
  rather than rewriting the entire file.
- `vscode-extension/extension.js` is the real client source; there is no
  `src/extension.ts`. Preserve configured Java/JAR/SDK/cwd and Run Main behavior.
  Test server-provided actions through the existing language client before
  adding any new extension command or setting.

### Trait implementation: do not append methods globally to structs

- `language-core/Parser.kt`: `parseDeclaration` routes `comptime` to CPX.
  Add trait dispatch ahead of it and reuse `parseFunction` receiver parsing.
- Add `SyntaxTrait`/`AstTrait` and explicit `AstBuilder` conversion.
  Keep the target name and origins distinct from method names. Do not pretend
  a trait is another `SyntaxStruct`, which would change type identity/layout.
- New sealed declarations require a consumer audit. Search for
  `SyntaxStruct`, `AstStruct`, `SyntaxDeclaration` and `AstDeclaration`.
  The core leaf may add explicit unsupported-trait diagnostics in later
  consumers to keep builds coherent; silent ignoring is never an acceptable
  interim behavior.
- `comptime/CpxExpansion.kt` has declaration fingerprint, structural
  classification, name, reorigin, hygiene, reflection and recursive AST
  handling. Add trait-aware traversal, but do not classify a method block as
  a new data-layout descriptor or bypass the stabilized type universe.
- `compiler/AstRewrite.kt`, `ClosureLowering.kt`, `SdkMetadata.kt`, and
  `cli/Main.kt`'s `AstPrinter` must see trait method bodies.
- `semantic/Semantics.kt`: `MethodSymbol.owner` is currently `StructType`;
  method maps use owner-name strings, registration uses `associate`, and
  calls inspect struct method lists. Generalize the receiver identity and
  centralize lookup before adding extensions. Use module-aware nominal type
  identity and canonical alias resolution, not a plain target spelling.
- Separate native methods from module-scoped extension visibility. A public
  extension's defining module may differ from the target type's module.
  Resolve free names inside its body in the defining module's scope.
- A resolved call must retain its exact method identity and receiver adaptation.
  Backend/tooling must not independently repeat a different name-based lookup.
- `c-backend/CBackend.kt`: `lowerMethod` currently builds a C struct-pointer
  receiver. Scalars/enums need their real C type and typed storage access.
  Emit methods once with module-aware names and preserve original native-method
  behavior. Do not invent a runtime trait object or alter target fields.
- Test `self` separately from `self*`, and pointer values separately from
  addressable non-pointer values. Side-effecting receiver expressions must
  execute once. An explicit pointer receiver cannot silently take a dangling
  address to an invalid temporary.
- Member completion/navigation/reference/rename must query the same visible
  method set as semantic call resolution; private methods cannot leak merely
  because their source is in the compilation graph.

## Execution order

The following list is an execution order, not another status dashboard. Items
already accepted in `IMPL.PLAN.md` remain complete; resume at the current active
leaf rather than restarting the list.
Complete local work first. Leave the two final platform-evidence leaves
unstarted until the final pass; do not block local trait work on an early
Windows test. Their task dependencies permit this order.

1. `R10.1.1.1` — Thread one immutable header environment through compiler entry points.
2. `R10.1.1.2` — Resolve and preprocess real headers with target-aware provenance.
3. `R10.1.1.3.1` — Read function declarators and skip bodies structurally.
4. `R10.1.1.3.2` — Resolve typedef and aggregate declaration dependencies.
5. `R10.1.1.3.3` — Index globals and safely representable header constants.
6. `R10.1.1.3.4` — Audit declaration coverage against the delivered SDK headers.
7. `R10.1.1.4` — Bind discovered symbols and retire function whitelists.
8. `R10.1.2.1` — Unify CLI and LSP source graph discovery. (Accepted.)
9. `R10.1.2.2` — Build a shared typed export inventory. (Accepted.)
10. `R10.1.2.3` — Invalidate discovery and compilation consistently. (Accepted.)
11. `R10.2.1.1` — Identify incomplete import contexts from shared tokens. (Accepted.)
12. `R10.2.1.2` — Serialize provider and export completion through JSON-RPC. (Accepted.)
13. `R10.2.2.1` — Implement one import-edit builder. (Accepted.)
14. `R10.2.2.2` — Offer unimported symbols with additional import edits. (Accepted.)
15. `R10.3.1.1` — Map unresolved-symbol diagnostics to compatible providers. (Accepted.)
16. `R10.3.1.2` — Expose import quick fixes through the LSP server. (Accepted.)
17. `R10.3.2.1` — Verify installed CLI and LSP import parity.
18. `R10.3.2.2` — Exercise suggestions and fixes in the packaged VS Code extension.
19. `R11.1.1.1` — Parse the exact singular trait syntax.
20. `R11.1.1.2` — Traverse trait bodies in ordinary compiler passes.
21. `R11.1.1.3` — Preserve traits through CPX and stabilization.
22. `R11.1.2.1` — Generalize method ownership without changing native methods.
23. `R11.1.2.2` — Resolve targets, receivers and method bodies.
24. `R11.1.2.3` — Enforce extension import activation and collisions.
25. `R11.2.1.1` — Emit one typed C function per extension definition.
26. `R11.2.1.2` — Lower resolved calls with correct receiver storage.
27. `R11.2.1.3` — Verify the complete trait execution and provenance matrix.
28. `R11.2.2.1` — Use visible extension symbols in language tooling.
29. `R11.2.2.2` — Document and highlight the exact trait syntax.
30. `R10.3.2.3` — Close import regression, documentation and platform evidence.
31. `R11.2.2.3` — Close trait regression and packaged cross-platform gates.

Each task's deliverable, exact spec references, dependencies, files and tests
are in the matching R10/R11 section of IMPL.PLAN.md. A parent dependency means
all its terminal descendants must be accepted.

## Acceptance examples to turn into tests

These are **planned fixtures**, not currently runnable product examples.

### Header discovery without changing Kotlin

Create a temporary include root containing `demo/api.h` with a guarded
`static inline int header_answer(void) { return 42; }` declaration/definition.

```c
import { header_answer } from c.demo.api;

int main() {
    return header_answer() - 42;
}
```

Configure the same include root for CLI and LSP. Check and execute it; then
rename/remove the declaration and assert discovery and diagnostics refresh.
Repeat with a transitive include and an inactive conditional branch. Import
a missing symbol to prove that unsupported names are not guessed.

### Import edit round trip

Open source using `std_fs_mode_read()` without an import. Request completion
and separately an unresolved-name quick fix. Assert provider `std.fs`, apply
the returned edits, send a document-change notification, and confirm the
targeted diagnostic disappears. Check/run the saved result using the configured
JAR/SDK. Repeat for a public imported type, aliases and competing providers.

### Existing-type pointer receiver

```c
struct counter_t {
    int value;
};

comptime trait counter_t {
    int read(self) { return self.value; }
    void increment(self*) { self->value = self->value + 1; }
}

int main() {
    counter_t counter;
    counter.value = 41;
    counter_t* pointer = &counter;
    pointer.increment();
    return counter.read() - 42;
}
```

Also test a primitive alias such as `typedef unsigned long long word_t;`
with `comptime trait word_t { ... }`, and an imported type under a selective
alias. Add fixtures for private leakage, duplicate names and imported ambiguity.
Do not stop after this single struct example: the R11 acceptance matrix also
covers unions, enums, CPX origins, layout preservation and tooling.

## Verification and reporting

Use the repository's Gradle wrapper if the selected machine lacks `gradle`.
Focused commands are attached to every leaf. Useful final local commands are:

```text
gradle test :cli:fatJar :cli:installDist
git diff --check
```

From `vscode-extension/`:

```text
npm test
npm run check
xvfb-run -a npm run test:host
```

`test:host` packages the VSIX. Do not claim editor acceptance from a unit test
that only mocks VS Code. Product fixtures must point at the freshly built JAR
and deliberately selected SDK.

The installable product launcher is `cplus`; the user's `c+` spelling may be
a local wrapper. The fat JAR is
`cli/build/libs/cplus-cli-0.1.0-SNAPSHOT-all.jar`. JVM SDK overrides go before
`-jar`; inspect existing help/tests for exact options before scripting them.
An installed distribution contains `sdk/`; do not assume the fat JAR alone
embeds that source tree.

Native Windows checks belong to the final R10.3.2.3/R11.2.2.3 pass.
The user previously authorized the Windows VM, but use the current session's
available access and preserve its worktree. If unavailable, report the exact
missing evidence and keep the affected task open. Do not reuse prior R0–R9
Windows results as proof for new import or trait code.

Counting rules:

- Count only terminal tasks, once; decomposed IDs are parents, not extra work.
- Parent status: all TODO → TODO; all DONE → DONE; otherwise DOING.
- Update the top dashboard, the completion-roadmap summary, ancestors and
  coverage notes together. Planning itself never increases the accepted count.
- Keep historical evidence explicitly historical. New prerequisites or
  unsupported cases require a visible task/spec update before changing scope.
- At a context/session boundary record the active leaf, files changed, last
  command/result, and next concrete action. Do not declare a phase complete
  simply because the current session ends.

## Copyable continuation prompt

```text
Implement the next dependency-ready terminal task in IMPL.PLAN.md R10/R11.
Read AGENTS.md and IMPL.PLAN.RULES.md, then
IMPL.HANDOFF.IMPORTS-TRAITS.md and that task's referenced spec sections.
Resume at R10.3.2.1, the current active leaf in IMPL.PLAN.md. Preserve unrelated user edits,
especially sdk/libc/include/stdio.h. Use comptime trait type_identifier { ... }.
Implement one leaf, add/run its acceptance tests, record evidence, update all
counts honestly, and make a Conventional Commit before proceeding.
Use local tests first; reserve Windows execution for the final named gates.
Do not implement a second LSP parser, a library function whitelist, or named
trait interfaces. If a missing prerequisite appears, decompose/replan it
explicitly before claiming completion.
```
