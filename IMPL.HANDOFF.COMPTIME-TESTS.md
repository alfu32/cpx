# Imported comptime functions and source tests — Luna 6 Medium handoff

## Start here

Implement the R13/R14 queue in [IMPL.PLAN.md](IMPL.PLAN.md), one terminal task
at a time. That file is the only status/evidence ledger. This handoff supplies
execution instructions, design boundaries and regression inputs; it does not
claim that either feature is complete. R0–R12 record prior accepted scope.
Progress checkpoint, 2026-10-09: R13.1.1 (batch parse boundary), R13.1.2
(provider identity across same-basename paths), R13.1.3 (typed comptime export
bindings), and R13.1.4 (selective and qualified invocation binding) are DONE.
R13.2.1 (imported expansion into the client type universe) is also DONE. Resume
at R13.2.2.
The active counters and all acceptance evidence remain authoritative in
`IMPL.PLAN.md`.

The required outcomes are:

- Import a public comptime definition before client CPX expansion, so its
  generated declarations enter the client's type universe.
- Parse top-level test blocks with unquoted descriptions containing spaces.
- Support exactly four assertions: `assert(expr)`, `assert(description, expr)`,
  `assertEquals(expected, actual)`, `assertEquals(description, expected, actual)`.
- Run explicit file lists through `cplus test` / installed `c+ test`, with
  file/fixture/assertion output, honest totals and a failing exit status when
  tests or execution fail.

## Working procedure

1. Read `AGENTS.md`, `IMPL.PLAN.RULES.md`, this handoff, the current dashboard,
   the selected leaf and its cited specification sections. LS means
   `SPEC.LANG.md`; TS means `SPEC.TECH.md`; SDK means `SPEC.STDLIB.md`.
2. Run `git status --short`. At planning time, `sdk/libc/include/stdio.h` and
   `vscode-extension/package-lock.json` contain user changes. Preserve them;
   inspect overlaps and never commit them incidentally.
3. Select the next dependency-ready leaf and mark just that leaf DOING. Update
   both dashboards and ancestor states. Do not mark a leaf DONE for writing
   a contract, stub, test which is skipped, or implementation without evidence.
4. Read the real entry points and adjacent tests before editing. Add focused
   regression coverage for the leaf; implement its deliverable using shared
   compiler representations. Use `rg` to audit every consumer of changed nodes.
5. Run its verification command. New test-class names in the plan are proposed
   files to create, not existing suites. A no-matching-tests failure is not a
   pass; record actual tests executed, not just command exit status.
6. Record commands/results and relevant target/compiler evidence in the leaf.
   Update all ancestors, both dashboards and `SPEC.COVERAGE.md`. Commit completed
   stages using Conventional Commits with REQUEST, IMPLEMENTATION and
   NOT IMPLEMENTED sections. Stage only scoped files.
7. Move to the next leaf. If an independent missing requirement is discovered,
   split the smallest relevant leaf, add traceability/dependencies/acceptance,
   recalculate denominators, then continue. Do not hide work outside the ledger.

Do not replay completed R13 leaves or R10/R11; use the dashboard for the current
dependency-ready task and progress counter.

## Specification entry points and fixed decisions

Read LS §7.4, §21.6 and §53; TS §27.1, §57.1 and §80; SDK §103 in full.

| Concern | Decision |
| --- | --- |
| Public comptime | Retained declaration record; bind before expansion; erase only runtime representation |
| Visibility | Selective, selective-as and module-qualified binding; no implicit workspace-wide names |
| Scope | Provider definition bindings; client argument bindings; client insertion scope |
| Identity | Resolved provider/declaration identity, never basename or alias alone |
| Cycles | Catalogue first, schedule to workspace fixed point, bounded diagnostics for unresolved evaluation cycles |
| Fixtures | Top-level unquoted single-line descriptions; ordinary statement blocks; declaration order |
| Assertions | All four forms; optional semicolon only under LS §53.2's narrow rule |
| Default description | Condition source for `assert(expr)`; `expectedSource == actualSource` for two-argument equality |
| Evaluation | Each operand once; description, expected, actual order; failed assertions continue |
| Equality | Existing typed `==`; exact floating comparison, pointer identity, no deep/string-content equality |
| Ordinary builds | Parse/type-check tests; emit and execute no test code |
| Test products | One product per explicit root; one fresh process per fixture; user main remains callable |
| Totals | Count executed assertions; crashes/build errors are separate errors, never fake assertion failures |
| CLI | Ordered explicit roots, shell glob expansion, normal SDK/build configuration, 30s fixture timeout default |
| Output | `:::` files/final report, `...` fixtures, `----` assertion details, original stdout/stderr preserved |
| Result transport | Private versioned record file; never parse user stdout for success |
| Exit codes | 0 clean completion; 1 assertions/build/execution failed; 2 usage/setup failure |

The user explicitly approved both equality forms and both truth-assertion forms.
The failure, equality, isolation and optional-semicolon rules are the planning
defaults now specified for implementation. Do not silently change them.
Native Linux and Windows acceptance is required; Windows runs after local
feature work. No new Windows filesystem API, syscall layer or host-libc choice
is needed: reuse the SDK/PAL. This is not a request for a VS Code Test Explorer,
parallel runner, watch mode, recursive test discovery or a new public test library.

## Repository map and observed defects

Production prefixes are `language-core/src/main/kotlin/cplus/core/`,
`semantic/src/main/kotlin/cplus/semantic/`,
`comptime/src/main/kotlin/cplus/comptime/`,
`compiler/src/main/kotlin/cplus/compiler/`,
`c-backend/src/main/kotlin/cplus/backend/` and
`cli/src/main/kotlin/cplus/cli/`. Tests use matching `src/test/kotlin` prefixes.
The extension source is `vscode-extension/extension.js`, not `src/extension.ts`.

### Import/CPX ordering

- `Compiler.kt`: `frontend(source, headerEnvironment)` currently parses,
  provisionally analyzes and calls `context.cpxExpander.expand` per file.
  `compileWorkspace` builds `ModuleGraph` only after these expansions. Moving
  just final semantic import checking cannot fix the missing expansion binding.
- `CpxExpansion.kt`: `expand` populates `definitions` from the current program's
  `SyntaxComptimeFunction` nodes, keyed by name. Returned syntax filters out
  those definitions. `taskFor` builds expansion identity using `definition.name`.
  Both visibility and identity need real imported declaration records.
- `Semantics.kt`: `resolveImportedFunctions` handles ordinary functions, types,
  enum values and extension names. It does not retain erased comptime exports.
  Do not suppress SEM404 globally or register compile-time functions as fake
  runtime functions to quiet diagnostics.
- `Parser.kt`: `parseDeclaration` recognizes a top-level CPX invocation only
  for identifier immediately followed by `(`. Qualified `boxes.box(int)` needs
  explicit syntax/binding support. Preserve member/runtime call behavior.
- `ModuleGraph.kt`: current IDs use `path.nameWithoutExtension`; normalization
  drops path prefixes. Same-basename providers need canonical identities wired
  through graph and semantic ownership, not another private CPX-only namespace.
- `IncrementalCompiler.kt` caches full `FrontendUnit` values including expansion.
  Reusing parsed syntax is different from reusing an expansion whose imported
  environment changed. Inspect invalidation before trusting a cached frontend.
- `ImportIndex.kt`: export kinds currently omit comptime functions; validated
  expansion syntax may replace parsed syntax. Keep pre-expansion public
  definitions alongside generated exports and never execute providers to scan
  a workspace for completion candidates.
- Check all disk, text, incremental and LSP-overlay entry paths. Catalogue
  construction must precede expansion in each, including cycles and generated
  imports. Do not concatenate provider definitions into the client as a fix.

### Fixture pipeline

- No top-level `test` command exists in `Main.kt`; `libc test` is a different
  SDK conformance command. Keep both paths separate.
- `Parser.kt`, `Syntax.kt`, `Ast.kt`, `AstBuilder.kt` own the one language model.
  Introduce fixture and assertion nodes there, not a CLI text scanner.
- Search all sealed node consumers: `Semantics.kt`, `AstArena.kt`,
  `CpxExpansion.kt` fingerprint/reorigin/hygiene, `AstRewrite.kt`,
  `ClosureLowering.kt`, `SdkMetadata.kt`, `CBackend.kt`, CLI `AstPrinter`,
  reference collection and semantic tokens. Fixture bodies cannot disappear
  silently in any mode. Interim unsupported-node diagnostics are explicit;
  final normal-mode erasure occurs only after validation.
- `CompilerOptions` has no test mode. `IncrementalCacheKey` contains options;
  add mode/selection consistently to every relevant cache and request path.
- `runProgram` builds then uses `ProcessBuilder(...).inheritIO()` and managed
  cleanup. Extract reusable orchestration as needed; do not merge independent
  test roots into one program with duplicate mains.
- `RuntimeHelperCatalogue.kt` and `RuntimeLinker.kt` own helper inclusion.
  Provide the SDK test reporter through those contracts and existing stdio/file
  services, not arbitrary C printf formats assembled from Kotlin type names.
- A false assertion continues. Do not lower to C `assert`, `abort`, early
  return or `exit`, which would skip assertions/deferred actions and corrupt
  totals. Normal completion must be recorded after fixture defers finish.
- Temporary/result paths must be valid on Windows; avoid names such as
  `Path.of("<test>")`. Runtime paths use the existing slash-path abstraction.

### Concrete internal protocol starting point

Keep the record format deliberately small. Use UTF-8/ASCII tab-separated lines:

```text
CPXT1<TAB>fixture-id
A<TAB>1<TAB>PASS
A<TAB>2<TAB>FAIL
END<TAB>1<TAB>1<TAB>2
```

`<TAB>` means one literal tab. The first record identifies the selected fixture;
each A record increments a contiguous sequence beginning at 1. END contains
passed, failed, total. Use nonnegative checked 64-bit counters and reject
overflow. Limit individual records to 4096 bytes, reject records after END,
and flush every completed record. Identity is an opaque compiler-generated
ASCII ID, not description text. The CLI validates against its fixture metadata.
An incomplete trailing record preserves earlier validated A records but is an
execution error. Both exit zero and valid END are required for normal completion.
The wrapper exits zero after normal fixture completion even if A records contain
FAIL; the parent returns the failing test-command status. A nonzero child exit
means execution error, so a false assertion must not set that child exit status.

This is an internal contract; freeze it in matching helper/parser tests. If
changed, update both sides and TS §80.3 together. User output is independent,
and forged-looking lines printed by fixture code have no control-channel effect.

## Execution order

Complete these small subtrees in order; select leaves in numeric/dependency
order inside each. This table is a route, not a second progress ledger.

| Sequence | Tasks | Observable result |
| --- | --- | --- |
| 1 | R13.1.1–R13.1.4 | Parsed catalogue, provider identity and import bindings |
| 2 | R13.2.1 | Exact imported box program executes with exit 42 |
| 3 | R13.2.2–R13.2.4 | Correct scope, cycles, cache and source origins |
| 4 | R13.3.1–R13.4.1 | Incremental/LSP parity and Linux imported-CPX product evidence |
| 5 | R14.1.1–R14.1.4 | Fixture grammar and all four typed assertion forms |
| 6 | R14.2.1–R14.2.4 | One fixture runs through generated C/runtime with source maps |
| 7 | R14.3.1.1–R14.3.3.2 | Multi-file CLI execution, reports and accurate failures |
| 8 | R14.4.1.1–R14.4.3.1 | Editor/docs, combined CPX fixtures and Linux packaged evidence |
| 9 | R13.4.2, then R14.4.3.2 | Final native Windows acceptance |

An unavailable final platform leaves its task open; it does not block local
work whose dependencies are otherwise accepted. Use an isolated checkout on
the already-authorized Windows VM and preserve its existing development clone.
Use native-host test targets; classify any unavailable cross-target compiler
tests explicitly rather than describing a partially passing full suite as green.

## Minimum regression inputs

Use the exact LS §21.6 provider/client as the first regression. The program's
successful result is **exit 42**. A test harness asserting exit zero for that
literal main is wrong. For a conventional zero-exit regression, explicitly
change the return to `item.value - 42` and keep the literal case too.

Next, retain the provider and add a source-test client:

```c
import { box } from "./box.cp";
box(int);

test generated box stores the expected value {
    struct box_int_t item;
    item.value = 42;
    assert(item.value)
    assert("nonzero value", item.value)
    assertEquals(42, item.value)
    assertEquals("stored value", 42, item.value)
}

int main() { return 0; }
```

The runner executes four assertions and reports 4 passed / 0 failed / 4 total;
it does not invoke user main. Ordinary `run` executes main and reports no test
output. Introduce `assertEquals(41, item.value)` to check one failure followed
by later assertions, with exit 1. Use a counter-incrementing function to prove
single evaluation rather than checking only emitted source strings.

Add a second root with its own main, two fixtures, a loop, user stdout/stderr
and an empty fixture. Assert exact per-fixture/file/final totals. Add native
process fixtures for early exit, crash and timeout; the next fixture must still
run and errors must remain separate from assertion failures. The plan's other
negative/scope/cache/origin cases are required as well, not optional polish.

## Counting and completion

At the initial planning checkpoint: 129 accepted baseline leaves + 12 R13 leaves +
20 R14 leaves = 161 total. R13 has four children containing 4/4/2/2 leaves;
R14 has four children containing 4/4/6/6 leaves. The two six-leaf branches each
have three two-leaf children, preserving bounded branching. All new statuses
were TODO. R13.1.1 through R13.1.4 and R13.2.1 are now accepted, so the current
counter is 134/161 while 27 new leaves remain open. No planning-only task is counted as
implementation.

On completion, check all dependency edges, mandatory acceptance criteria,
origin tests and platform evidence before declaring 161/161 and 15/15. If the
plan changes, recompute these values instead of copying this initial snapshot.
Historical foundation 146/146 is a different metric and is not added to 161.

## Copyable execution prompt

```text
Implement the remaining R13/R14 leaves in IMPL.PLAN.md using
IMPL.HANDOFF.COMPTIME-TESTS.md. Read repository instructions first. Resume at
R13.2.2 (unless the dashboard records later progress); do not redo accepted tasks.
Support imported public comptime expansion and all four assertion forms in
source fixtures, with the specified cplus test reports. Keep one principal
DOING leaf, preserve user changes, add meaningful regression evidence, commit
completed stages and update both dashboards/coverage honestly. Work locally
through the Linux product gates, then validate native Windows. Do not mark
missing/skipped acceptance as DONE or stop at parser-only support.
```
