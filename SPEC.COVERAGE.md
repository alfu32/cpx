# C+ Specification Coverage Audit

This matrix is the implementation coverage audit for the foundation tasks. A
range is considered **implemented** only when the named boundary is exercised
by focused tests and the claimed runtime behavior is executable-tested. A
source contract, header, metadata entry, or declaration-only façade is marked
**contracted** and does not satisfy a release gate by itself. The post-foundation
completion gates are tracked in `IMPL.PLAN.md`; R0–R9 record the accepted
baseline, while R10–R11 track the newly specified import-assistance and trait
features. Broad section ranges in the historical matrix below do not imply
implementation of newly added subsections. In particular, LS §6.3.1 and §41.1,
the expanded LS §22.1 contract, and TS §13.1/§54.1 remain pending as listed
below.

## Newly specified requirements — implementation pending

| Requirement | Authoritative implementation tasks | Current evidence |
| --- | --- | --- |
| LS §22.1–22.3; TS §28; source-driven, target-aware C declarations | R10.1.1 and its terminal children | Planned; current catalogue is still hand-maintained |
| LS §21, §41.1; TS §27, §47, §54.1; shared resolution, export index and invalidation; SPEC.STDLIB §58 | R10.1.2 and its terminal children | Planned; CLI/LSP resolution parity and fresh index tests required |
| LS §41.1; TS §54.1; import completion, auto-import edits and quick fixes | R10.2–R10.3 and their terminal children | Planned; protocol, applied-edit, installed-product and packaged-editor tests required |
| LS §6.3.1; TS §13.1; direct compile-time extensions | R11.1–R11.2 and their terminal children | Planned; parser/CPX/semantic/C execution, visibility and tooling tests required |

The detailed execution runbook is
[IMPL.HANDOFF.IMPORTS-TRAITS.md](IMPL.HANDOFF.IMPORTS-TRAITS.md). No new feature
is credited merely because its specification or implementation plan exists.

## Historical foundation evidence

| Specification sections | Implementation boundary | Evidence |
| --- | --- | --- |
| LS §§1–4, 28–31 | source repository, origins, AST, lowering, source maps | `language-core` tests; compiler source-map and golden fixtures |
| LS §§5, 23, 32–36, 44 | declaration catalogue, scopes, symbols, lookup, ambiguity diagnostics | semantic scope/catalogue/reference tests; compiler semantic tests |
| LS §§6, 14, 30, 34 | declarations, Pratt expressions, methods, closures, parser recovery, supported and rejected function-pointer declarators | AST golden tests; method and closure compiler integration tests; function-pointer and `PARSE410` recovery fixtures |
| LS §§7–13, 17–20, 29, 35–38 | typed CPX values, lexical interpolation boundaries, identifier-composition diagnostics, recursive syntax provenance, hygiene, fixed point, phase barrier, cycles, stabilized semantic reflection and ABI layout | `comptime` expansion/scheduler tests; CPX compiler reflection and generated-C integration tests |
| LS §§21–22, 43–43.4 | packages, relative/quoted imports, logical package imports, selective aliases, standard C headers, C sources, foreign symbols, qualified declarators, target-aware foreign typedef layout, function-pointer callbacks, pointer arithmetic, initializer and cast checks | parser golden test; semantic/compiler import tests; qualifier generated-C test; LP64/LLP64 ABI layout test; callback and pointer-arithmetic generated-C compile/execute tests; initializer diagnostic test; CLI module-run test and cross-header C dependency/execution tests |
| LS §§24–27, 39, 47–51 | hoisting, headers, dependencies, incremental compilation, diagnostics | c-backend/compiler/CLI golden and incremental tests |
| LS §§40–42 | workspace edits, semantic tokens, completion, hover, navigation, references, signature help, VS Code client, Run Main command, and TextMate grammar | CLI LSP integration/workspace tests; CLI module-run test; `vscode-extension` package tests and `.vsix` build |
| LS §52 | end-to-end implementation architecture | full Gradle suite and native C smoke tests |
| TS §§1–8, 65–70 | module structure, source model, lexer/parser, AST, recovery, hot-path storage | language-core tests and incremental lexer tests |
| TS §§9–14, 26–30, 56 | symbols, scopes, types, methods, modules, C import adapter, foreign tooling | semantic/compiler tests and foreign-symbol LSP test |
| TS §§15–25, 47–49 | compile-time values, templates, evaluator, scheduler, fixed points, cycles, expansion cache replay and invalidation | `comptime` tests, incremental compiler tests, and the `cpx-deterministic` CLI golden/C17 execution fixture |
| TS §§31–44, 58–62 | compiler pipeline, lowering, C AST, hoisting, headers, emitter, source maps | compiler/c-backend golden and execution tests |
| TS §§45–46, 50–55, 63–64 | diagnostics, CLI, LSP, layered tests, golden fixtures | CLI integration/golden tests and `gradle test` |
| TS §§69–78 | architectural invariants, milestones, verification and final audit | typed backend channels, full suite, `git diff --check` |

## Current status classification

### Implemented and executable

- front-end, semantic model, methods including pointer receivers, imports,
  typed CPX argument parsing, lexical interpolation boundaries, recursive
  syntax provenance, basic CPX expansion, lowering, C emission, source maps,
  incremental cache behavior, multi-token primitive type parsing, structured C qualifiers and
  declarators, function-pointer callbacks, target-aware foreign fixed-width
  typedef layout, pointer arithmetic, aggregate pointer casts, global/local
  initializer checks, LSP primitives, and the
  Linux/Windows self-hosted startup path;
- independent Linux C17 caller interoperability for scalar, object-pointer,
  aggregate, callback, variadic, export/link-name, aggregate-return, and
  public TLS declarations through the generated header;
- Linux and Windows self-hosted stdout/process exit;
- Linux page-backed runtime allocation, zeroing, alignment, resize, and release
  through the uniform PAL page ABI;
- Linux self-hosted C17 core memory/string/conversion behavior and PAL-to-
  thread-local-`errno` conversion without host allocation symbols;
- Linux self-hosted formatted stdio/varargs, clock/time, math, ctype, locale,
  and basic signal behavior through the SDK runtime;
- Linux x86_64 atomics, UTF-8/wide conversion, wide classification, and
  setjmp/longjmp context switching;
- Linux and Windows PAL file open/read/write/seek/close/rename, metadata, and
  directory iteration with canonical UTF-8 slash paths, stable error mapping,
  and the `std_fs_*` forwarding façade; Windows execution is native x86_64.
- independent C17 `basic`, `context`, `stdio`, `complex-types`, and `tgmath`
  fixtures execute on native Windows x86_64 and pass PE dependency audits; the
  latest GCC/UCRT report is 51 pass, 0 fail, 0 unsupported, 0 planned. Windows
  complex scalar ABI is enabled only for a verified compiler/target profile.

### Contracted but incomplete

- Source-driven C/header discovery, shared import suggestions/quick fixes, and
  `comptime trait type_identifier { ... }` extensions are newly specified but
  not implemented; R10/R11 are entirely TODO at this planning checkpoint.
- C17's complete standard-library surface is not claimed: the reported 51
  checks are the registered project conformance suite, not an exhaustive test
  of every C17 header and function.
- Windows executable evidence is native x86_64/UCRT only. Windows AArch64
  runtime execution and Darwin self-hosted execution are not claimed; those
  targets have descriptor, source, link, or explicit capability-gate evidence
  where recorded in `IMPL.PLAN.md`.
- The C23 profile and full POSIX compatibility remain explicitly unavailable.
- Some intentionally versioned C+ language limitations produce stable
  diagnostics rather than implementation behavior; see the limitations below
  and the relevant normative sections.

### Explicitly not claimed

- C23 profile completeness;
- full POSIX compatibility;
- complete Darwin self-hosted execution;
- release completion merely because the historical foundation counter is
  `146/146`.

The baseline roadmap's R0–R9 gates are complete for their explicitly recorded
targets/profiles and distribution checks. The roadmap is now reopened by the
new R10/R11 requirements; no new gate has passed. This does not expand support
claims to the excluded profiles.

## Explicitly diagnosed limitations

- Escaping mutable-reference captures and inner-function declarations that are
  not block statements produce stable diagnostics (`CLOSURE001`/`CLOSURE003`)
  before C emission. They are versioned implementation boundaries, not silent
  acceptance or mock output; nested block closures are lowered recursively.
- LSP navigation currently returns the request document URI for locations in
  the active single-document compilation; a future workspace-wide source URI
  mapper can expose external C-header paths directly.

## Verification commands

```text
gradle --no-daemon -Dorg.gradle.native=false test
git diff --check
gradle --no-daemon -Dorg.gradle.native=false :cli:run --args='transcode examples/optional.cp --output /tmp/cpx-optional.c --header /tmp/cpx-optional.h'
cc -std=c17 -fsyntax-only /tmp/cpx-optional.c
gradle --no-daemon -Dorg.gradle.native=false :cli:fatJar
java -jar cli/build/libs/cplus-cli-0.1.0-SNAPSHOT-all.jar --help
cd vscode-extension && npm test && npm run package
```
