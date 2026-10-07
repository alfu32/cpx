# C+ Specification Coverage Audit

This matrix is the implementation coverage audit for the foundation tasks. A
range is considered **implemented** only when the named boundary is exercised
by focused tests and the claimed runtime behavior is executable-tested. A
source contract, header, metadata entry, or declaration-only façade is marked
**contracted** and does not satisfy a release gate by itself. The post-foundation
completion gates are tracked in `IMPL.PLAN.md` under R1–R8.

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
- Linux and Windows basic PAL file open/read/write/close/rename with canonical
  UTF-8 slash paths, including the `std_fs_*` forwarding façade.

### Contracted but incomplete

- `std.alloc` is a fixed bootstrap arena, not the complete page-backed
  allocator required by the standard-library specification;
- `std.io`, process, time, thread, synchronization, networking, and math
  sources are primarily API contracts or declarations;
- C17 headers are delivered, but broad behavioral libc and independent-C ABI
  conformance is not complete;
- Linux/Windows memory, environment, time, thread/synchronization, networking,
  remaining filesystem services, and Darwin concrete execution are incomplete;
- CLI workspace/product packaging and workspace-wide LSP source mapping need
  release-grade evidence.

### Explicitly not claimed

- C23 profile completeness;
- full POSIX compatibility;
- complete Darwin self-hosted execution;
- release completion merely because the historical foundation counter is
  `146/146`.

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
