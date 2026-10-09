# C+ Compiler and Language Tooling — Implementation Plan

## Dashboard

The historical foundation counter and the release-roadmap counter measure
different things. The first records the original 146 planning items; the
second is the authoritative queue for completing the working CLI transcoder,
runtime, SDK, LSP, and release products.

### Release roadmap dashboard

```text
Historical foundation: 146/146 evidenced; acceptance reconciled with R5 evidence
Roadmap leaf tasks:    127/129 accepted with recorded evidence; 2 TODO
Phase gates:           11/13 complete; 2 active; 0 queued
Current task:          R10.3.2.3 — final native Windows product and trait gate
Current milestone:     discoverable imports, import fixes, and compile-time extension methods
Latest C17 Linux report: 51 pass, 0 fail, 0 unsupported, 0 planned
Latest C17 Windows x86_64 report: 51 pass, 0 fail, 0 unsupported, 0 planned
Windows execution:     native Windows x86_64/UCRT64 GCC validation passes
Windows full-runtime link: native Windows and local MinGW PE products pass
Prior R0–R9 test suites: Linux `gradle test --rerun-tasks` passes (4m10s);
                         clean native Windows VM `gradle test` passes (11m23s)
Windows runtime evidence: allocator, process, stdio channels, clocks, threads/TLS,
                          atomics/synchronization, TCP/UDP/IPv6, resolver,
                          std.math façade, C17 subset, locale/signal/ctype,
                          UTF-8/wide-character conversion

R0 [DONE]  1/1  implementation inventory and scope freeze
R1 [DONE]  15/15 language/front-end leaves; Linux and Windows x86_64 suite pass
R2 [DONE]  7/7  CPX, generics and reflection conformance
R3 [DONE]  6/6  Linux and native Windows primitive source-to-ABI evidence
R4 [DONE]  11/11 runtime/libc leaves; Linux and Windows x86_64 suites pass
R5 [DONE]  41/41 implementation leaves; registered Windows x86_64 service tests pass
R6 [DONE]  4/4  CLI leaves; clean Windows fat-JAR/build/audit/run checks pass
R7 [DONE]  4/4  packaged VSIX host acceptance passes on Linux and Windows
R8 [DONE]  4/4  SDK packaging, target matrix and release conformance
R9 [DONE]  4/4  CLI distribution includes SDK; explicit SDK override and JVM option docs
R10 [DOING] 18/19 import discovery, completion and quick fixes
R11 [DOING] 11/12 compile-time extension methods
R12 [DONE]  1/1 generated CLI and editor build identity

TOTAL       127/129 implementation tasks accepted; 11/13 phase gates complete,
            2 active, 0 queued
```

The Luna 6 Medium execution runbook is
[IMPL.HANDOFF.IMPORTS-TRAITS.md](IMPL.HANDOFF.IMPORTS-TRAITS.md).
R10 adds 19 terminal tasks and R11 adds 12, increasing the prior 97-task
denominator to 128. R12 adds one independently accepted generated-version
task, bringing the current denominator to 129. Their broader existing task IDs
are retained as composites, not double-counted as leaves.
The recorded Linux/Windows results above apply to the prior R0–R9 baseline, not
these features. Eighteen R10 leaves now have recorded Linux evidence; R10 and
R11 are active while their product and implementation gates remain open.

R9 adds four independently accepted distribution leaves to the prior 93-task
roadmap, increasing the denominator to 97. The 10th phase gate records the
joint CLI/SDK install product and its explicit SDK-development override.

R7.4 adds one cross-platform packaged-extension-host acceptance leaf, changing
the roadmap denominator from 92 to 93 without adding completion credit. Final
Linux and native Windows x86_64 host execution both pass (details recorded in
the final validation checkpoint below).

The latest Linux x86_64 C17 report is 51 pass, 0 fail, 0 unsupported, and 0
planned. Per-leaf historical records below retain the report totals measured
when those leaves were accepted; they are not claims about the latest count.

R4.6.2 was decomposed into five independently testable leaves for C+ complex
operators, component/projection functions, exponential/root functions,
trigonometric/hyperbolic functions, and `<tgmath.h>` dispatch. This increases
the denominator from 88 to 92 and R4 from 7 to 11 without adding completion
credit; at R4.6.2.5 acceptance the historical aggregate was 78/92 overall and
11/11 Linux R4 leaves. The current roadmap totals are recorded in the dashboard
above and include subsequently accepted platform work. The complex-function leaves are credited only after their
independent C callers, runtime dependency audits, and full builds pass.

The roadmap denominator increased from 65 to 67 during R5.3.7 planning when
one broad socket work item was decomposed into three verifiable leaves. That
decomposition credited no work; the numerator increased to 43 after R5.3.7.1
passed its ABI acceptance checks, to 44 after R5.3.7.2 passed its Linux
implementation acceptance checks, and to 45 after R5.3.7.3 passed its Windows
source and network-adapter PE import checks. The latter does not claim Windows
runtime execution or a complete Windows runtime image.

R5.3.8 was decomposed into shared address/name codecs, Linux DNS transport,
and Windows resolver integration because each has independent implementation
and acceptance evidence. This increases the denominator from 67 to 69 without
adding completion credit. R5.3.8.1 then passed its executable codec vectors,
freestanding undefined-symbol audit, strict C17 checks for all four target
triples, and C+ API/ABI target checks; commits `9a8532e` and `34d322f` brought
the numerator to 46/69. R5.3.8.2 then passed the local UDP/TCP DNS fixture,
resolver-configuration parsing, timeout/error and source/question checks,
freestanding link audit, both Linux-architecture source checks, and the full
Gradle build; commit `ff39ba6` brought the numerator to 47/69. R5.3.8.3 then
passed strict Windows x86_64/AArch64 source checks, a freestanding PE link and
import audit, and the focused network test plus full Gradle build; commit
`3c1667e` brings the numerator to 48/69. At that checkpoint this was
source/link acceptance only; the final Windows resolver execution and native
PE product evidence are recorded in the final validation checkpoint.

R5.4.1 then passed a compiled C+ parent/child process fixture covering process
identity, argument and environment views, synchronous API errors, inherited
environment and standard channels, child exit status, and façade forwarding;
the linked Linux product has no unresolved host-runtime symbols. Strict C17
source checks pass for Linux/Windows x86_64/AArch64, and the full Gradle build
passes. Commit `f5159ef` brings the numerator to 49/69. Windows process runtime
execution remains deferred; the source checks do not claim Windows behavior.
Commit `f79c654` adds explicit coverage that missing-executable lookup errors
remain synchronous through the public façade.

Audit found SPEC.STDLIB §14 also requires calendar representation, but the
previous R5.4.2 leaf listed only clocks, durations, and C time/clock behavior.
R5.4.2 was split into clock/duration/C-façade acceptance and UTC calendar
conversion, increasing the denominator from 69 to 70 with no automatic credit.
R5.4.2.1 then passed C+ execution, duration overflow and unchanged-output
vectors, injected C time/clock failure checks, the four-target duration layout
audit, strict four-target source checks, and the full Gradle build. Commit
`141f393` brought the numerator to 50/70; at that point calendar
conversion remained open.
R5.4.2.2 then passed executable calendar round trips for the Unix epoch, leap
day, pre-epoch timestamps, astronomical year zero, and both signed 64-bit
second endpoints; invalid dates/nanoseconds and unrepresentable years preserve
outputs. The public layout matches 32 bytes with 8-byte alignment across all
four target descriptors. Strict C17 checks pass for Linux/Windows x86_64/AArch64
and the full Gradle build passes. Commit `cc436ca` brings the numerator to
51/70. Windows runtime execution remains deferred and is not claimed.

R5.4.3 was decomposed into independently testable thread, synchronization,
and atomic API leaves. This increased the roadmap denominator from 70 to 72
without adding completion credit; at decomposition R5 remained 19/28.
R5.4.3.1 then passed a compiled C+ caller test for create/join, callback result
and thread identity, yield, invalid handles, and nullable join results; the
linked Linux executable has no unresolved host-runtime symbols. Public
declarations and 64-bit handle layout passed all four target descriptor checks,
strict warning-as-error C17 source checks passed on Linux/Windows x86_64 and
AArch64, and the full Gradle build passed. Commit `25d2da6` brings the numerator
to 52/72 and R5 to 20/28. Windows runtime execution remains deferred; at that
point R5.4.3.2 became active.
R5.4.3.2 then passed a compiled C+ caller covering contended mutex operations,
condition signal/broadcast and mutex reacquisition, semaphore count and blocked
wait/post behavior, once-only initialization, and invalid-state errors. Typed
objects are four bytes with four-byte alignment and offset-zero state on all
four target descriptors. The linked Linux executable has no unresolved
host-runtime symbols; strict C17 checks pass on Linux/Windows x86_64/AArch64;
the full Gradle build passes. Commit `93a12a4` brings the numerator to 53/72
and R5 to 21/28. Windows runtime execution remains deferred; R5.4.3.3 is now
the active task.
R5.4.3.3 then passed a compiled C+ caller covering all six memory orders,
load/store/exchange, strong compare-exchange success and failure, arithmetic
and bitwise fetch operations, fences, and concurrent atomic wait/wake. The
linked Linux executable has no unresolved host-runtime symbols. Public object
and enum layouts pass Linux/Windows x86_64/AArch64 ABI checks. Warning-as-error
C17 checks pass on all four target compilers; generated assembly for each
target contains no atomic helper or `libatomic` references; the full Gradle
build passes. Commit `6cb217a` brings the numerator to 54/72 and R5 to 22/28.
Windows runtime execution remains deferred, and R5.4.4 is now active.

R5.4.4 was decomposed into public types/API, TCP, UDP, and address/DNS façade
leaves. This increased the roadmap denominator from 72 to 75 and R5 from 28 to
31 without adding completion credit. R5.4.4.1 then passed explicit C+ imports
and calls for every declared API, four-target struct/enum/handle ABI checks,
and the forced focused Gradle test run. Commit `9d14930` brings the numerator
to 55/75 and R5 to 23/31. This closes declaration/ABI evidence only; the
public functions have no implementations yet. R5.4.4.2 is active, while UDP
and address/resolver façade behavior remain queued.
R5.4.4.2 then passed a compiled C+ IPv4 loopback caller for TCP socket
creation, bind/listen/connect/accept, local and peer addresses, bidirectional
stream transfer, shutdown EOF, invalid arguments, unchanged output on failed
address queries, and close. The production-linked executable has no undefined
host-runtime symbols; strict warning-as-error C17 checks pass for Linux and
Windows x86_64/AArch64; the full Gradle build passes. Commit `89eea32` brings
the numerator to 56/75 and R5 to 24/31. Windows runtime execution remains
deferred. R5.4.4.3 is now active; UDP and address/resolver behavior remain
unimplemented.
R5.4.4.3 then passed a production-linked C+ UDP loopback caller covering
empty and non-empty datagrams, source-address copy-out, optional null source,
invalid handle/buffer/length cases, and close. The Linux executable has no
undefined host-runtime symbols; strict warning-as-error C17 checks pass for
the OS-neutral façade on all four Linux/Windows x86_64/AArch64 target triples;
the full Gradle build passes. Commit `48b342d` brings the numerator to 57/75
and R5 to 25/31. Windows runtime execution remains deferred. R5.4.4.4 is now
active; the public address and resolver operations remain declared but have no
runtime forwarding implementation yet.
R5.4.4.4 then passed production-linked C+ IPv4/IPv6 parse and canonical-format
vectors, numeric resolver bypass, caller-owned result copying, short-buffer
preservation, invalid-input output preservation, and required-count reporting.
A deterministic resolver-bridge test covers multiple address results,
capacity truncation, and preserved outputs on PAL lookup/validation errors; the
existing `RuntimeNetworkDnsTest` passes its deterministic local DNS fixture
against the production resolver. Linked public C+ products have no undefined
host-runtime symbols, strict warning-as-error C17 façade checks pass for all
four Linux/Windows x86_64/AArch64 target triples, and the full Gradle build
passes. Commit `9a154ae` brought the numerator to 58/75 and R5 to 26/31 at that
point. Windows runtime execution remains deferred. R5.4.4 is complete at 4/4;
portable math is now active after the Linux long-double ABI prerequisite.

Audit of R5.4.5 found that the two-function math stub cannot meet C17: the
standard real-math surface includes classification, rounding, decomposition,
remainder, power/root, exponential/logarithmic, trigonometric/hyperbolic,
error/gamma, minimum/maximum, and fused-multiply-add families, with
float/double/long-double variants. The roadmap denominator increases from 75
to 85 and R5 from 31 to 41 by decomposing that parent into eleven independently
testable leaves, with no completion credit. A separate audit of the already
advertised C17 headers found that `<complex.h>` and `<tgmath.h>` are absent;
R4.6 adds two leaves, increasing the total from 85 to 87 and R4 from 5/5 to
5/7, also with no completion credit. The audit found `long double` accepted
syntactically but absent from semantic primitive and target ABI modeling; that
gap is now addressed for Linux by R3.1.3. R5.4.5.1 depends on that verified
Linux ABI leaf; Windows LLP64/compiler ABI
validation remains separately open in R3.1.4. Complex math additionally
depends on the real math implementation.

R3.1.3 was split by execution environment so Linux evidence does not stand in
for Windows validation. Its Linux primitive source-to-ABI leaf passed the
integer and floating independent-caller checks, target descriptor layouts,
and selected-compiler probe recorded below. This adds one verified leaf and
one separately tracked Windows leaf: the roadmap changes from 58/87 to 59/88.
At this earlier checkpoint, R3.1.4 remained TODO and received no Windows
completion credit; the completed ABI evidence is tracked in the R3 section and
current dashboard below.

R5.4.5.1 then completed the public real-math declaration stage: the SDK header,
`std.math`, and `c.math` import catalogue now expose all 57 C17 real function
families for float, double, and long double. Exact-name/type catalogue tests,
C+ import/lowering tests, SDK-header syntax checks, and the full build passed.
This moves the roadmap from 59/88 to 60/88 and R5 from 26/41 to 27/41;
classification constants/macros and every implementation family remain open.

R5.4.5.2 then implemented the C17 real classification and comparison macros,
their target-format runtime helpers, the `HUGE_VAL*`, `INFINITY`, `NAN`, `FP_*`,
and `FP_ILOGB*` constants, the `float_t`/`double_t` aliases, and the errno-only
math error indicator policy. `c.math` now explicitly imports `float_t` and
`double_t` as types. Runtime checks execute all three real precisions on Linux,
including signed zero, subnormal, infinity, NaN, unordered comparisons,
single-evaluation behavior, and the `sqrt` domain-error `EDOM` path; a synthetic
binary128 bit-pattern fixture covers zero/subnormal/normal/infinity/NaN. Strict
warning-as-error C17 compilation passes for all four declared ABI targets, the
linked Linux runtime has no undefined host symbols, and the full Gradle build
passes. Commit `3817e8e` moves the roadmap from 60/88 to 61/88 and R5 from
27/41 to 28/41. Windows runtime execution remains deferred and receives no
completion credit.

R5.4.5.3 then implemented the real rounding and integer-conversion families.
The runtime performs bit-level truncation for binary32, binary64, x87, and
binary128; implements floor/ceil, ties-away round, fixed ties-to-even
rint/nearbyint, and checked lrint/lround conversions for `long` and `long
long`. Target ABI flags now carry LP64/LLP64 `long` width, and the SDK limits
header exposes matching long bounds. A production-linked Linux C17 fixture
covers every precision and conversion family, signed zero, tie behavior,
special values, integer-range boundaries, errno preservation/EDOM, and the
no-host-symbol audit. Synthetic binary128 vectors cover representation
truncation/parity; strict warning-as-error compilation and static ABI/range
assertions pass for all four target descriptors; the full Gradle build passes.
This moves the roadmap from 61/88 to 62/88 and R5 from 28/41 to 29/41.
Windows runtime execution remains deferred and receives no completion credit.

R5.4.5.4 then implemented the real decomposition, scaling, sign, NaN, and
adjacent-value families: `ilogb`, `frexp`, `modf`, `ldexp`, `scalbn`,
`scalbln`, `copysign`, `nan`, `nextafter`, and `nexttoward`, including each
float/double/long-double variant. A production-linked Linux C17 fixture checks
normal and subnormal decomposition, special values, signed zero, errno
behavior, extreme `long` exponents, tagged-NaN payload distinction, adjacent
values at zero/infinity/subnormal boundaries, and extended-precision
`nexttoward` direction. The executable has no undefined host symbols. Strict
warning-as-error C17 source checks pass for Linux/Windows x86_64 and AArch64
target ABIs, and the full Gradle build passes. NaN tag payload mapping is now
specified as deterministic FNV-1a. Commit `e2ba767` moves the roadmap from
62/88 to 63/88 and R5 from 29/41 to 30/41. Windows runtime execution remains
deferred and receives no completion credit.

R5.4.5.5 then implemented the real remainder and quotient-remainder functions:
`fmod`, `remainder`, and `remquo`, with all float/double/long-double variants.
The freestanding reduction uses binary scaling and subtraction, so extreme
quotient magnitudes do not require integer conversion or host `libm`; `fmod`
uses truncation semantics, while `remainder` and `remquo` use nearest integer
with ties to even. `remquo` returns the signed low three quotient bits. The
production-linked Linux C17 fixture checks a bounded integer-oracle sweep for
all precisions, signed zero, ties, special values, errno, subnormals, very
large exponent gaps, and quotient bits, then audits the executable for host
symbols. Strict warning-as-error C17 source checks pass for Linux/Windows
x86_64 and AArch64 target ABIs, and the full Gradle build passes. Commit
`ad36e09` moves the roadmap from 63/88 to 64/88 and R5 from 30/41 to 31/41.
Windows runtime execution remains deferred and receives no completion credit.

R5.4.5.6 then implemented `fabs`, `sqrt`, `cbrt`, `pow`, and `hypot` for
float, double, and long double. Roots use exponent normalization and bounded
Newton iterations; `hypot` scales by the larger operand; integer powers use
exponentiation by squaring; and non-integral powers use private range-reduced
logarithm/exponential kernels. Those private kernels support `pow` only and do
not count as the public R5.4.5.7 families. The production-linked Linux C17
fixture covers all precisions, signed zero, subnormal roots and powers,
infinity/NaN behavior, negative-base domain errors, zero-base poles, overflow
and underflow errno, and host-symbol isolation. Strict warning-as-error C17
checks pass for Linux/Windows x86_64 and AArch64 target ABIs, and the full
Gradle build passes. Commit `2290d29` moves the roadmap from 64/88 to 65/88
and R5 from 31/41 to 32/41. Windows runtime execution remains deferred and
receives no completion credit.

R5.4.5.7 then implemented `exp`, `exp2`, `expm1`, `log`, `log10`, `log1p`,
`log2`, and `logb` for float, double, and long double, reusing the private
range-reduced kernels introduced for `pow`. `exp2` preserves exact integer
powers of two, including subnormals; `expm1` and `log1p` use cancellation-
resistant series near zero. The production-linked Linux C17 fixture covers
all variants, exact scaling, small inputs, infinities/NaNs, signed zero,
domain and pole errno, and overflow/underflow. The executable has no undefined
host symbols. Strict warning-as-error C17 checks pass for Linux/Windows
x86_64 and AArch64 target ABIs, and the full Gradle build passes. Commit
`cc860ae` moves the roadmap from 65/88 to 66/88 and R5 from 32/41 to 33/41.
Windows runtime execution remains deferred and receives no completion credit.

R5.4.5.8 then implemented `sin`, `cos`, `tan`, `asin`, `acos`, `atan`,
`atan2`, `sinh`, `cosh`, `tanh`, `asinh`, `acosh`, and `atanh` for float,
double, and long double. The production-linked Linux fixture checks signed
zero, ordinary and reduced large-angle values, inverse-trig quadrants,
hyperbolic values, NaN/infinity handling, domain/pole errno, and representative
float/long-double variants. The executable has no undefined host symbols;
strict warning-as-error C17 checks pass for Linux/Windows x86_64 and AArch64
target formats; the full Gradle build passes. Commit `af1350f` moves the
roadmap from 66/88 to 67/88 and R5 from 33/41 to 34/41. This does not claim
IEC 60559 / Annex F accuracy. Windows runtime execution remains deferred and
receives no completion credit.

R5.4.5.9 then implemented `erf`, `erfc`, `lgamma`, and `tgamma` for float,
double, and long double. The production-linked Linux fixture covers known
values across precisions, error-function tails, signed zero, gamma reflection,
NaN/infinity, gamma poles, and overflow/underflow errno. The executable has no
undefined host symbols; strict warning-as-error C17 checks pass for Linux and
Windows x86_64/AArch64 target formats; the full Gradle build passes. Commit
`bcd78d7` moves the roadmap from 67/88 to 68/88 and R5 from 34/41 to 35/41.
Windows runtime execution remains deferred and receives no completion credit.

R5.4.5.10 then implemented `fdim`, `fmax`, `fmin`, and `fma` for float,
double, and long double. The production-linked Linux C17 fixture covers NaN
selection and signed-zero extrema, exact single-rounding cancellation and
ties-to-even, subnormal boundaries, overflow cancellation, errno cases, and
invalid infinity/zero combinations. The linked executable has no undefined
host symbols; strict warning-as-error C17 checks pass for Linux/Windows
x86_64/AArch64 target formats; the full Gradle build passes. Commit `cb7469e`
moves the roadmap from 68/88 to 69/88 and R5 from 35/41 to 36/41. Windows
runtime execution remains deferred and receives no completion credit.

R5.4.5.11 then connected the public `std.math` declarations to bundled runtime
exports. The production-linked C+ façade test imports and executes representative
operations across real precisions and function families, verifies all 172
declared `std_math_*` names (the 171 C17 function variants plus the existing
`std_math_abs` compatibility alias) are defined by the runtime, and confirms
the executable has no undefined host symbols. All `RuntimeStdMath*` tests pass,
including strict warning-as-error C17 checks for Linux/Windows x86_64 and
AArch64 target formats; the full Gradle build passes. Commit `fa0eed7` moves the
roadmap from 69/88 to 70/88 and R5 from 36/41 to 37/41. Windows runtime
execution remains deferred and receives no completion credit.

R5.4.6 completed in commit `3f6177d`. Target service capabilities now flow
from the selected descriptor/profile into CPX metadata; `require_service`
reports stable available/unavailable, malformed, and unknown cases; Darwin
self-hosting fails with the documented `SDK013`; and the dependency auditor
fails closed for missing inspection tools and malformed ELF, PE/COFF, or
Mach-O products. Linux production-linked minimal-program execution confirms
unused service symbols are discarded and the executable passes dependency
inspection. Focused capability, audit, link-driver, unused-service, and math
facade tests passed, followed by `./gradlew build --no-daemon`. The roadmap
moves from 70/88 to 71/88 and R5 from 37/41 to 38/41. Windows runtime and
real-PE import validation remain deferred with no completion credit.

R5.5 is also evidenced by commit `3f6177d`: Darwin is explicitly
capability-gated rather than represented as a partially implemented runtime.
`PlatformAbiTest`, `TargetDescriptorTest`, and `RuntimeLinkerTest` verify the
empty Darwin service set and stable unsupported-self-host diagnostic; the
technical architecture records this boundary. No Darwin execution is claimed.
This moves the roadmap from 71/88 to 72/88 and R5 from 38/41 to 39/41; R5
remains open for R5.1; R5.2.5 is now accepted with native Windows evidence.

The detailed, authoritative R0–R11 work queue is in the
[completion roadmap](#completion-roadmap--post-foundation-implementation)
below. The current queue starts at R10.1.1.1; the execution runbook above lists
the remaining dependency order. The following sequence is a historical
checkpoint, superseded by the current dashboard and final R0–R9 validation:

1. R5.1 remains open for its complete `std.core`, memory, string, text, and
   collection acceptance. R5.2.5 is now accepted after native Windows file-PAL
   and std.io execution; no remaining R5.2.5 Windows gate is implied.
2. R8.1–R8.3 are accepted. Linux x86_64 runtime/product checks and Linux
   AArch64 QEMU execution/source checks pass; native Windows x86_64 ABI, file
   PAL, CLI, and fat-JAR product checks pass. Windows AArch64 and Darwin are
   unavailable and remain explicitly unclaimed.
3. R8.4 is accepted. R1, R4, R5.1, R6, and R7 retain their separately listed
   open compatibility/conformance gates; acceptance of the release audit does
   not imply those phase gates are complete. The `C17ConformanceRunner` distinguishes
   execution failure from a missing target runner and reports unavailable
   cross-target execution as `unsupported` rather than a pass.
4. R4 is the next open phase gate: execute the existing Linux runtime/libc
   conformance families on native Windows where supported, correct platform
   differences, and keep unsupported profiles explicit. Then continue the
   remaining R5.1, R6, and R7 leaves in dependency order.

Historical implementation commits at that checkpoint:

- `60047dc` — central C primitive metadata and reopen R5.1 on audit;
- `76a1876` — preserve parsed integer identity across target ABI layouts;
- `bc60a1e` — verify integer spellings across compiler and CLI/LSP front ends;
- `7321b66` — normalize complete C integer specifier sequences;
- `c7e6817` — target-neutral native std value foundations;
- `0fd8835` — executable Linux C17 conformance gate;
- `a6cc980` — advanced Linux C17 runtime families;
- `f8be29e` — self-hosted stdio/time/basic C17 families;
- `3578f06` — bind selective source type imports;
- `3043cdd` — resolve qualified and aliased source types;
- `21c7979` — verify source-type import workflows and generated-C execution;
- `d51c9ce` — add capability-gated Linux x86_64 i128/u128 support;
- `9c9ea94` — verify standard integer ranks through an independent Linux C ABI caller;
- `679fa85` — add target-aware std.core and memory APIs (Linux evidence; R5.1 remains open).
- `0548b95` — make fat-JAR output reproducible and clean temporary run products (R6.3).
- `95e18a0` — validate selected SDK artifacts and target-specific CLI inspection plans (R6.4).
- `90740c3` — map cross-file LSP features through connected workspace documents (R7.1).
- `7233e6b` — launch the configured CLI JAR for extension LSP and Run Main flows (R7.2).
- `4efd2f7` — define the version-three filesystem PAL contract (R5.2.1 verified; adapters remain open).
- `da60b85` — add seek and file-metadata adapters (R5.2.2 Linux-verified; Windows execution deferred).
- `b6bc355` — add portable directory PAL services (R5.2.3 Linux-verified; Windows execution deferred).
- `69bf852` — add portable filesystem and unbuffered stream facades (R5.2.4 Linux-verified; Windows execution deferred).
- `2d24aed` — validate page-memory PAL failure cases (R5.3.1 Linux-verified; Windows execution deferred).
- `4d253de` — add portable process spawn and wait adapters (R5.3.2 Linux-verified; Windows execution deferred).
- `5ec70b7` — expose process context and standard streams (R5.3.3 Linux-verified; Windows execution deferred).
- `61326a8` — add checked version-four wall, monotonic, and process-CPU clocks (R5.3.4 Linux-verified; Windows execution deferred).
- `753fc14` — add runtime-managed threads and static TLS (R5.3.5 Linux x86_64-verified; Windows/AArch64 runtime execution deferred).
- `283c355` — implement portable synchronization primitives (R5.3.6 Linux x86_64-verified; Windows/AArch64 runtime execution deferred).
- `9da7c39` — define and verify the portable socket PAL ABI (R5.3.7.1 target-layout verified).
- `480587b` — add Linux IPv4/IPv6 TCP and UDP socket transport (R5.3.7.2 Linux-verified; Windows execution deferred).
- `284ba99` — add lazy Windows Winsock socket transport (R5.3.7.3 source/PE-import verified; runtime execution and full-runtime PE linking deferred).
- `bd26d05` — verify standard-channel error mapping and host-runtime isolation.
- `c39c402` — preserve the target `size_t` ABI in stdio formatting functions.
- `4952b4d` — verify declared stdio channels in the independent C17 report (Linux x86_64).
- `9a8532e` — shared freestanding IPv4/IPv6 and UTF-8 hostname codecs.
- `34d322f` — verify rejection at the DNS label-length boundary.
- `ff39ba6` — implement bounded freestanding Linux DNS resolution.
- `3c1667e` — integrate the Windows Unicode resolver (source/PE-import verified; Windows execution deferred).
- `f5159ef` — implement the native `std.process` façade (Linux C+ execution; Windows execution deferred).
- `f79c654` — verify synchronous not-found errors through `std.process`.
- `141f393` — implement checked `std.time` clocks and durations (calendar conversion remains open).
- `cc436ca` — implement proleptic-Gregorian UTC calendar conversion (Windows execution deferred).
- `e7e480c` — model target floating layouts and verify Linux long-double calls (R3.1.3 Linux leaf only; Windows ABI gate remains open).
- `a8d1470` — declare the C17 real-math function surface (R5.4.5.1).
- `3817e8e` — implement C17 floating classification, comparisons, and constants (R5.4.5.2; Windows runtime execution deferred).
- `7ce3908` — implement portable C17 rounding and checked integer conversions (R5.4.5.3; Windows runtime execution deferred).
- `e2ba767` — implement portable C17 decomposition, scaling, sign, NaN, and adjacent-value functions (R5.4.5.4; Windows runtime execution deferred).
- `ad36e09` — implement C17 remainder and quotient-remainder functions (R5.4.5.5; Windows runtime execution deferred).
- `2290d29` — implement portable C17 power and root functions (R5.4.5.6; Windows runtime execution deferred).
- `cc860ae` — implement C17 exponential and logarithmic function families (R5.4.5.7; Windows runtime execution deferred).
- `af1350f` — implement C17 trigonometric and hyperbolic function families (R5.4.5.8; Windows runtime execution deferred).
- `bcd78d7` — implement C17 error and gamma functions (R5.4.5.9; Windows runtime execution deferred).
- `cb7469e` — implement C17 extrema and exact fused multiply-add families (R5.4.5.10; Windows runtime execution deferred).
- `a6ab00d` — add target-gated C17 complex scalar ABI, SDK header, and independent Linux x86_64 caller coverage (R4.6.1).
- `6af6820` — implement target-gated C+ complex conversions/operators and compiler-owned multiply/divide helper ABIs (R4.6.2.1 Linux x86_64).
- `d721f2b` — implement target-gated C17 complex component and projection functions (R4.6.2.2 Linux x86_64).
- `0571de7` — implement C17 complex exponential, logarithm, power, and square-root functions for all three precisions (R4.6.2.3 Linux x86_64).
- `6c89226` — implement C17 complex circular, inverse, hyperbolic, and inverse-hyperbolic functions for all three precisions (R4.6.2.4 Linux x86_64).
- `37014e0` — implement C17 real/complex type-generic math dispatch and its audited fixture (R4.6.2.5 Linux x86_64).
- `0ca174d` — add project/workspace manifests and SDK-rooted source imports across CLI source commands (R6.1).
- `81aa251` — normalize CLI output, source-map, target, SDK, compiler, sysroot, and native-input options (R6.2).
- `e3058a1` — complete local MinGW freestanding Windows PE linking with emulated TLS and dynamic atomic wait/wake lookup (R8.3 cross-link evidence; native Windows execution pending).
- `b1b7c7c` — add repeatable MinGW PE import auditing to CLI integration tests (R8.3).
- `7ee3d62` — execute initialized thread-local storage in the MinGW PE product under Wine (R8.3 compatibility evidence; native Windows execution pending).
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

## 6.3 [DONE] [8/8] Native `std` and C libc compatibility implementation

**SDK**
- SDK §6–19
- SDK §44–57
- SDK §75–76
- SDK §90–92

### 6.3.1 [DONE] [4/4] Native C+ standard-library core

#### 6.3.1.1 [DONE] Implement `std.core`, `std.mem` and portable memory primitives

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
  The prior audit removed unused fixed-width `std_*` aliases; R5.1 now adds
  target-sized `usize`/`isize` aliases and is not counted complete until its
  entire acceptance gate passes. This remains separate from the opt-in
  `std.fixed_width` aliases tracked by R1.2.4–R1.2.5.
- SDK libc/std source modules import their dependencies explicitly. Source
  modules with imports are compiled with their workspace dependencies; the
  R6.1 acceptance added SDK-rooted `std.*` discovery for standalone CLI
  entry points and project/workspace commands.
- **Acceptance evidence:** R5.1 closes the target-aware value/error/option,
  byte/size/index, memory/pointer, string/text, and collection surface. Its
  Linux x86_64 self-hosted and hosted UBSan fixtures, Linux AArch64 QEMU
  fixture, explicit-import/layout checks across four target descriptors, and
  recorded native Windows x86_64 self-hosted fixture cover this acceptance.
  The forced Linux `./gradlew test --no-daemon --rerun-tasks` run passes.

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

**Implementation and acceptance evidence**
- R5.2 implements the filesystem/stream layer; R5.3 implements process,
  environment, clocks, threads/TLS, synchronization, networking, and DNS PAL
  services; R5.4 implements the public native façades and math surface; R5.5
  explicitly capability-gates Darwin rather than claiming an unimplemented
  adapter. These dependency-ordered leaves include production-linked Linux
  execution, dependency audits, and recorded native Windows x86_64 execution
  for representative service families. The current R4/R5 Windows matrix gates
  remain separate and are not closed by this historical foundation item.
- A forced full Linux Gradle test run passes after the acceptance reconciliation.

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
- Every stdio function declared by the SDK C17 profile is implemented above native I/O/PAL primitives; the initial channel implementation is explicitly unbuffered.
- C time/math APIs are backed by native C+ services and do not require system `libm` in complete self-hosted profiles.
- initial locale/conversion/signals behavior has explicit conformance status and no silent host-libc fallback.
- external C conformance fixtures exercise representative interfaces.

**Implementation**
- Added stdio stream markers, a flush hook, time, math, locale, Unicode, and signal compatibility source/header contracts.
- Normal runtime termination calls the libc-owned stream flush hook; immediate and abort paths do not.
- Reopened after audit found that `fgetc` was a hardcoded EOF stub and stream-directed `fprintf`/`fputc` writes were routed to stdout. R5.3.3 implemented the standard-channel behavior; R4.4/R4.5 then audited every declared `stdio.h` operation and integrated an independent executable fixture into the libc report. Linux acceptance evidence is recorded below; deferred Windows execution is tracked by the target gates.

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
Foundation tasks: 146/146 (6.3.1.1 and 6.3.1.4 accepted against R5 evidence; 6.3.2 is 4/4)
Completion phases: [DOING] [11/13 gates complete; 2 active, 0 queued]
Roadmap leaf tasks: 127/129 accepted; R10.1 (10/10), R10.2 (4/4),
                     R10.3.1 (2/2), R10.3.2 (2/3) accepted/in progress;
                     R11.1.1 (3/3), R11.1.2 (3/3), R11.2.1 (3/3), R11.2.2 (2/3); R10 and R11 active, R12 accepted

[DONE]  R0 — implementation inventory and scope freeze
[DONE]  R1 — language and front-end conformance; primitive type matrix verified
[DONE]  R2 — CPX, generics and reflection conformance
[DONE]  R3 — C backend and ABI interoperability conformance
[DONE]  R4 — runtime, allocator and libc behavior
[DONE]  R5 — complete native std and platform services for claimed profiles
[DONE]  R6 — CLI transcoder and build-product completion
[DONE]  R7 — LSP and VS Code product completion
[DONE]  R8 — SDK packaging, target matrix and release conformance
[DONE]  R9 — CLI distribution includes source SDK with explicit JVM override
[DOING] R10 — import discovery, completion and quick fixes (18/19 leaves)
[DOING] R11 — compile-time extension methods (10/12 leaves)
[DONE]  R12 — generated CLI and editor build identity (1/1 leaf)
```

The completion phase counter counts only the thirteen phase gates above. A phase
with all descendants TODO stays TODO. Once work starts, it MUST remain DOING
until every acceptance gate inside it passes on the claimed target matrix.
The overall roadmap is DOING because it contains both DONE and TODO phases;
the active R10 and R11 phases are represented by their current leaves in the
primary dashboard.
Source declarations, headers, platform contracts, or a green
unit test that does not execute the claimed behavior are not completion
evidence.

### Final Linux/Windows validation checkpoint — 2026-10-08

This checkpoint supersedes earlier notes that explicitly deferred the final
Windows pass. Validation used a clean isolated archive at
`C:\Users\alfu64\Development\cpx-windows-final-d32565d`; the original Windows
clone was not modified. The final host-test harness files were copied into
that isolated archive after the last local harness fix.

- Linux: forced full Gradle tests pass; npm extension tests pass 6/6; package
  and checks pass; the packaged VSIX activates in VS Code 1.141.0 under
  `xvfb-run`, registers commands, and receives CLI `SEM302` diagnostics.
- Windows x86_64: clean-archive `gradlew.bat test --no-daemon` passes; the C17
  report is 51 pass, 0 fail, 0 unsupported, 0 planned; the CLI fat JAR builds,
  checks and runs examples, and builds/audits PE products with only
  `KERNEL32.dll` observed. The C+ optional/stdio product also builds and
  passes its dependency audit. Native Windows npm tests pass 6/6, checks and
  VSIX packaging pass, and the packaged VSIX activates in the actual
  Extension Development Host and delivers CLI diagnostics. Production npm
  audit reports zero vulnerabilities.
- The Windows full Gradle suite executes the registered runtime, ABI, PAL,
  networking, SDK, and toolchain acceptance tests, including the Windows
  filesystem, process/environment/stdio, clock, allocator, thread/TLS,
  synchronization/atomic, TCP/UDP/IPv6/resolver, math façade, and C17 cases.
  This evidence closes the roadmap's registered Windows x86_64 gates; it is
  not blanket evidence for unregistered edge cases or other architectures.
- C17 scope remains the registered 51-check suite, not exhaustive coverage of
  every standard header/function. C23, full POSIX, Darwin execution, and
  Windows AArch64 execution remain outside the claims of this release audit.

The clean Windows archive used the source revision containing the implementation
and product code at `d32565d`; subsequent local commits alter only the VS Code
host-test harness and documentation. The latest harness files were copied into
the isolated archive and its Windows host test passed. No Windows-side source
patch was needed. Full Linux and Windows suite results are retained as the
acceptance evidence for the then-current 9/9 phase dashboard; R9 acceptance is
recorded separately below.

## R0 [DONE] Implementation inventory and scope freeze

> Historical scope snapshot: the limitations listed in this section describe
> the state when R0 was frozen; consult the current dashboard and final
> validation checkpoint for subsequent implementation and acceptance results.

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

## R1 [DONE] Language and front-end conformance

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
- R1.2 [DONE] — reconcile primitive signedness, semantic identity, target
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
      declarations from `std.core`. The audit reopened R5.1 because at that
      point the required target-aware `usize`/`isize` aliases were absent;
      replacement work remains tracked and counted only in R5.1.
    - `./gradlew test` passes on Linux. ABI layout checks cover Linux and
      Windows x86_64/AArch64 target descriptors; no Windows execution was
      performed. Compiler integration verifies reflected `unsigned long long`
      identity/layout and generated C execution.
  - R1.2.4 [DONE] — provide `std.fixed_width` as an explicit-import user-level
    source module defining ordinary typedef aliases `i8`, `i16`, `i32`, `i64`,
    `u8`, `u16`, `u32`, and `u64` over the target's corresponding C
    `intN_t`/`uintN_t` types. Keep these names out of compiler built-ins,
    `std.core`, and native SDK API signatures; do not inject them implicitly.
    - Added `sdk/std/src/fixed_width.cp` with eight public source typedefs over
      explicitly imported `c.stdint` exact-width types. The aliases remain
      outside compiler primitives, `std.core`, and native SDK declarations.
    - Fixed semantic ordering so all C-header imports are registered before
      source aliases, signatures, and aggregate fields resolve. A consumer-first
      workspace now resolves aliases whose provider module imports `c.stdint`;
      this avoids caching unknown targets based on source order.
    - Tests verify explicit-only visibility, C typedef spellings, use in
      aggregate fields, pointers, and function signatures, 1/2/4/8-byte layouts
      across Linux/Windows x86_64 and AArch64 descriptors, and generated C17
      compilation/execution using the SDK `stdint.h`. Unsupported target triples
      fail SDK descriptor resolution (`SDK008`) rather than falling back to a
      different width.
    - The CLI integration now compiles and runs this explicit SDK import without
      a separately supplied source path. R6.1 provides the shared SDK-rooted
      discovery behavior; this historical task's ABI evidence remains scoped to
      the listed targets and does not claim Windows runtime execution.
    - `:language-core:test`, `:semantic:test`, `:c-backend:test`,
      `:compiler:test`, `:cli:test`, and `./gradlew build` pass on Linux.
  - R1.2.5 [DONE] — establish target/compiler capability and exact ABI support
    for signed and unsigned 128-bit integers. Since standard C does not provide
    `int128_t`/`uint128_t`, `std.fixed_width` exposes `i128`/`u128` only for
    the verified Linux x86_64 target/compiler contract.
    - Added canonical `__int128` and `unsigned __int128` primitive identities,
      16-byte Linux x86_64 layout, and an `int128` target capability. Other
      initial target descriptors do not advertise this extension.
    - Semantic analysis omits unsupported aliases from generated C and reports
      SEM411 for unavailable direct or imported 128-bit types. The link driver
      probes the selected GCC/Clang-compatible compiler's C17 width/alignment
      before accepting the advertised ABI.
    - An end-to-end fixture compiles generated C and its public header, then
      links and runs an independent C translation unit exercising signed and
      unsigned arithmetic above 64 bits, conversions, arguments, and returns.
      Layout, unsupported-target diagnostics, compiler probing, and refused
      links are covered by focused tests.
    - `./gradlew build` passes on Linux. Windows/AArch64 execution remains
      deferred; their descriptors conservatively omit `int128` pending the
      final cross-platform validation pass.
- R1.3 [DONE] — close the remaining declaration matrix in dependency order.
  - R1.3.1 [DONE] — represent function types and function-pointer declarators
    from source through semantic validation, indirect calls, and C emission.
  - R1.3.2 [DONE] — reconcile arrays, pointer arithmetic, casts, globals, and
    initializer/lvalue rules across parser, semantic analysis, and lowering.
  - R1.3.3 [DONE] — add stable unsupported-declarator diagnostics and recovery
    fixtures so parser acceptance cannot outrun backend support.
- R1.4 [DONE] [5/5] — implement kind-aware source type imports and
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
  - R1.4.4 [DONE] — resolve exported types through module aliases and support
    qualified and selectively imported types in fields, pointers, signatures,
    casts, aliases, and generated C declarations.
    - The parser now retains `moduleAlias.Type` as a qualified type reference;
      semantic analysis binds it through explicit module aliases and a
      public-only source type catalogue. Qualified and selective names resolve
      in fields, pointers, signatures, locals, casts, aliases, and type/layout
      queries, with missing/private qualified exports diagnosed as SEM404/SEM406.
    - C lowering maps imported local/qualified names to the original exported
      C declaration. C emission orders typedef aliases before aggregate bodies,
      orders alias dependencies, and emits aggregate tag forwards needed by
      aliases. Tests cover the emitted names and declaration order.
    - `:language-core:test`, `:semantic:test`, `:c-backend:test`,
      `:compiler:test`, and the full `./gradlew build` pass on Linux.
  - R1.4.5 [DONE] — verify path/package imports, type-only and mixed exports,
    function/value import regressions, private/omitted-import failures, CLI/LSP
    parity, and compiled generated-C behavior on Linux.
    - An end-to-end compiler fixture covers path and package imports, type-only
      and mixed selective imports, module aliases, renamed and qualified
      structs/typedefs, pointers, and `sizeof`; emitted C compiles as C17 and
      executes with the expected result.
    - The CLI `run` path discovers the imported source through path/package
      references and executes the same source-type behavior. CLI and LSP report
      matching SEM406 private-type and SEM410 omitted-import diagnostics.
    - Existing imported C function/value/type regressions remain green. The
      complete `:compiler:test` and `:cli:test` suites and `./gradlew build`
      pass on Linux. Windows execution remains deferred to the final validation
      pass.

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

## R3 [DONE] C backend and ABI interoperability conformance

**Progress**

- R3.1 [DOING] — close target ABI layout, calling convention, storage, and
  declaration interoperability.
  - R3.1.1 [DONE] — execute scalar, pointer, aggregate, callback, and variadic
    C+/C caller round trips on Linux.
  - R3.1.2 [DONE] — execute globals, TLS, export/link-name, and aggregate-return
    interoperability fixtures.
  - R3.1.3 [DONE] — verify Linux primitive declarations through semantic
    identity, emitted C, target layout, compiler probe, and independent C17
    caller, including `long double`.
  - R3.1.4 [DONE] — verify Windows LLP64 primitive declarations and
    independent caller ABI using the supported Windows x86_64 GNU/UCRT64
    compiler profile; MSVC binary64 `long double` remains a distinct,
    unsupported ABI profile until separately implemented.
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

### R3.1.3 Linux completion record

Implemented and tested on Linux:

- the ABI layout engine and target descriptors are exercised across the Linux
  and Windows x86_64/AArch64 descriptor matrix without host-width assumptions;
- pointer width, LP64/LLP64 `long`, `long long`, aggregate field offsets, and
  aggregate alignment are checked against each declared target descriptor;
- an emitted public header and generated C unit round-trip every supported
  standard integer rank and signedness through an independent Linux C17 caller;
  runtime values exercise signed and unsigned argument/return ABI;
- semantic assertions verify parsed integer declaration identities and keep
  plain/signed/unsigned char and each integer rank distinct;
- generated C retains the selected C declarators; an independent Linux C17
  caller verifies float/double/long-double scalar calls, aggregate field
  layout, and aggregate-by-value calls/returns;
- all six target descriptors carry floating format, storage, alignment,
  precision, and exponent data; target layout tests verify those values;
- the selected Linux x86_64 C compiler passes the descriptor probe, and a
  deliberately mismatched long-double descriptor is rejected.

This completes the Linux-only acceptance scope of R3.1.3. Windows LLP64
execution and compiler-profile selection are validated separately in R3.1.4;
descriptor-only Windows assertions alone are not Windows test evidence.

### R3.1.4 Windows ABI acceptance (DONE)

`WindowsAbiIntegrationTest` compiled generated C+ declarations and an
independent C17 caller using native Windows UCRT64 GCC, statically checked
LLP64 `long`, 64-bit `long long`, and the descriptor's 16-byte/64-bit-mantissa
GNU x87 `long double`, and executed scalar plus integer/floating aggregate
argument/return round trips on Windows. The same fixture also passes locally
with MinGW under Wine. Windows host toolchain discovery now prefers the GNU
profile that matches the declared target descriptor, while the mismatched
MSVC binary64 `long double` profile is rejected rather than silently treated
as ABI-compatible. Native Windows `:compiler:test` and the complete Gradle
build passed. This closes the supported GNU/UCRT64 Windows x86_64 ABI leaf;
it does not claim MSVC ABI support.

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

The R3 gate is complete for the supported Linux and Windows x86_64 profiles.
Darwin remains outside the claimed execution matrix.

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

## R4 [DONE] Runtime, allocator and libc behavior for the registered claimed profiles

**Dependency-ordered work queue**

- R4.1 [DONE] — inventory runtime symbols, define target-neutral runtime
  result/errno rules, and replace the fixed bootstrap arena with a page-backed
  allocator contract;
- R4.2 [DONE] — implement allocate, allocate-zeroed, resize, free, and aligned
  operations with overflow, double-free, and invalid-range diagnostics;
- R4.3 [DONE] — implement the C+ memory/string/conversion core and thread-local
  errno boundary without importing host libc behavior into native APIs;
- R4.4 [DONE] — implement and audit the claimed Linux C17 compatibility
  families in dependency order: stdio/varargs, time/math/locale, Unicode,
  signal, atomics, TLS, and setjmp/longjmp, including every function declared
  by the SDK `stdio.h` surface;
- R4.5 [DONE] — execute independent C17 conformance fixtures, audit compiler
  runtime symbols, and ensure the report exercises every advertised stdio
  operation rather than treating declarations or stubs as complete.
- R4.6 [DONE] [6/6] — complete the C17 complex and type-generic
  math headers; the current 51-pass Linux report covers its registered subset
  and is not evidence that every header family listed in SPEC.STDLIB §18 is
  complete.
  - R4.6.1 [DONE] — add target-described C complex types and the C17
    `<complex.h>` declarations/macros, with semantic, layout, and independent
    C-caller coverage. At initial acceptance the `c17_complex` capability was
    limited to Linux x86_64; other targets rejected these types until separately
    probed. Windows x86_64 GCC/UCRT support is recorded in the addendum below.
    `CPrimitiveTypesTest` verifies legal specifier order and rejects invalid
    combinations; `ComplexAbiIntegrationTest` verifies all three semantic
    identities/layouts, checks unsupported-target diagnostics, exercises the
    `c.complex` import, and round-trips complex parameters/returns through an
    independent C caller. The selected compiler probe and C17 fixture verify
    component-based size/alignment, all construction macros, and `I`; the
    fixture passes dependency audit. At R4.6.1 acceptance the Linux C17 report
    was 46/0/0/0 and `./gradlew build --no-daemon` passed. No Windows or
    AArch64 complex support or execution is claimed.
  - R4.6.2.1 [DONE] — implement C+ complex usual arithmetic conversions,
    supported arithmetic/equality/logical operators, and invalid-operator
    diagnostics. `ComplexAbiIntegrationTest` verifies float/double/long-double
    result selection for mixed complex, real, and integer operands; conditional
    result typing; arithmetic and compound addition; equality/inequality and
    logical truth operations; and rejection of ordered/integer-only complex
    operators, including `%=`. An independent C caller compiled and executed
    through both available Linux C compilers; the linked product passed the
    runtime dependency audit. The C17 runtime helpers provide the GCC/Clang
    multiply/divide ABI entry points and are linked only when `c17_complex` is
    enabled. Strict C17 warning-as-error syntax checks pass under GCC and
    Clang, the Linux C17 report is 47/0/0/0, and `./gradlew build --no-daemon`
    passes. A separate audit test confirms the helper check is unsupported on
    Linux AArch64 where complex capability is not enabled. No Windows or
    AArch64 complex execution is claimed.
    **Depends:** R4.6.1.
  - R4.6.2.2 [DONE] — implement and execute `cabs`, `carg`, `creal`, `cimag`,
    `conj`, and `cproj` for all three complex precisions. The Linux C17 fixture
    covers component extraction, 3-4-5 magnitudes, argument delegation,
    signed-zero branches and conjugation, infinity/NaN projection, finite
    signed-zero preservation, NaN propagation, and overflow-resistant large
    magnitudes. C+ import wrappers were compiled and executed through
    independent callers under both available Linux compilers; all linked
    products passed the runtime dependency audit. The fixture also passes the
    independent C17 runner and dependency audit, strict GCC/Clang C17
    warning-as-error syntax checks pass for the runtime and fixture, and
    `./gradlew build --no-daemon` passes. The Linux C17 report remains
    47/0/0/0 because the existing registered fixture was extended. No Windows
    or AArch64 complex execution is claimed.
    **Depends:** R4.6.1 and R5.4.5.2–R5.4.5.10.
  - R4.6.2.3 [DONE] — implement and execute `cexp`, `clog`, `cpow`, and
    `csqrt` for all three precisions, including branch cuts, signed zeros,
    infinities, and NaNs, with independent C17 fixture and dependency audit.
    Implemented overflow-aware exponential products, scaled log
    magnitude, principal-branch power, and scaled square root for float,
    double, and long double. The executable C17 fixture checks finite values,
    branch cuts and signed zeros, infinities/NaNs, overflow recovery, and large
    finite logarithm inputs across all three precisions. C+ wrappers for all
    twelve precision-specific imports were executed through independent C
    callers under both available Linux compilers; every linked product passed
    the runtime dependency audit. Strict GCC/Clang warning-as-error C17 syntax
    checks pass, the C17 report is 47/0/0/0, the focused ABI integration test
    passes, and `./gradlew build --no-daemon` passes. Implementation commit:
    `0571de7`. No Windows or AArch64 complex execution is claimed.
    **Depends:** R4.6.2.2 and R5.4.5.2–R5.4.5.10.
  - R4.6.2.4 [DONE] — implement and execute the C17 circular, inverse,
    hyperbolic, and inverse-hyperbolic complex function families for all three
    precisions, including their branch behavior. Add independent C17 fixture
    and dependency audit. Implemented all twelve function families for float,
    double, and long double using direct identities and scaled tangent formulas.
    The executable C17 fixture exercises finite trigonometric/hyperbolic
    identities, inverse round trips, branch-cut endpoints, signed-zero input,
    and all precisions. Generated C+ wrappers exercise all 36 imported symbols
    through independent callers compiled and run under both available Linux
    compilers; linked outputs pass runtime dependency inspection. Strict
    warning-as-error syntax checks under GCC and Clang, the 47/0/0/0 C17 gate,
    the focused ABI integration test, and `./gradlew build --no-daemon` pass.
    Implementation commit: `6c89226`. No Windows or AArch64 complex execution
    is claimed.
    **Depends:** R4.6.2.3 and R5.4.5.2–R5.4.5.10.
  - R4.6.2.5 [DONE] — implement the complete `<tgmath.h>` dispatch surface
    across real/complex and integer-promoted arguments, preserving result
    types and single evaluation. Add independent C17 coverage and dependency
    audit for every advertised generic macro. Added all standard generic
    macros for the SDK's C17 `<math.h>` and `<complex.h>` operations, with
    usual arithmetic conversion selection, integer-to-double promotion,
    complex counterparts, typed pointer-output functions, and single-evaluation
    invocation. `c17-tgmath.c` checks result types, mixed real/complex ranks,
    integer arguments, typed results, and argument evaluation count, and calls
    every advertised macro in an executable fixture. The profile and header
    catalogues now include `<tgmath.h>`; its fixture and linked product pass
    the runtime dependency audit. GCC and Clang warning-as-error checks and
    `./gradlew build --no-daemon` pass. The latest C17 report is 51/0/0/0.
    Implementation commit: `37014e0`. No Windows or AArch64 complex/tgmath
    execution is claimed.
    **Depends:** R4.6.2.1–R4.6.2.4 and R5.4.5.2–R5.4.5.10.

  **Native Windows x86_64 validation addendum:** the UCRT64 GCC profile now
  advertises `c17_complex` only after matching the descriptor's size/alignment
  probe and passing independent C17 caller/callee roundtrips for float, double,
  and x87 extended complex scalars. `ComplexAbiIntegrationTest` also passes the
  full generated C+ complex API caller on the native VM, including arithmetic,
  all three precisions, runtime math operations, and PE dependency audit. The
  Windows C17 `complex-types` and `tgmath` fixtures execute and pass their
  dependency audits; the complete report is 51 pass, 0 fail, 0 unsupported,
  0 planned. This evidence is specific to the tested Windows x86_64 GCC/UCRT
  profile; it does not claim MSVC complex ABI, Windows AArch64, or Darwin
  support. This registered C17 suite is not exhaustive of the standard's full
  header/function surface; the native Windows full-suite result closes the
  registered Windows x86_64 R4 gate as recorded in the final checkpoint.

The Linux x86_64 conformance command reports the individual header, runtime
source, fixture execution, and binary dependency checks; it returns non-zero
for missing, planned, or unsupported checks. R4.4/R4.5 were reopened after the
audit found that `fgetc` always returned EOF and `fprintf`/`fputc` ignored
their stream argument. R5.3.3 fixed those behaviors; the independent
`c17-stdio.c` fixture now exercises every function declared by the SDK's
`stdio.h`, and `cplus libc test --target linux-x86_64` currently reports 51
pass, 0 fail, 0 unsupported, and 0 planned, including exact stdin/stdout/stderr
checks, complex type/header checks, and binary dependency audits. The current
registered suite includes `<tgmath.h>` and complex math fixtures, but does not
cover the entire C17 header list. The R4.4/R4.5 Linux leaves pass their
recorded acceptance checks. At this historical checkpoint the R4 phase gate
remained open for the native Windows family matrix and deferred-runtime
families; final closure evidence is recorded above. The shared C17 runner now enables `basic` and `stdio` fixtures for
Windows x86_64. `ConformanceTest.windowsX8664C17AuditExecutesAllEnabledFixtures`
passes on the Windows VM: each enabled fixture executes, stdout/stderr and stdin
contracts match, and each PE product passes the host/compiler-runtime import
audit. The shared `basic` fixture also exercises Windows `malloc`, `calloc`,
`realloc`, `aligned_alloc`, zeroing/alignment, `errno` on allocation overflow,
memory/string operations, and integer/floating conversion. The expanded fixture
passes on both Windows x86_64 and Linux x86_64. The Windows `context` fixture
now passes through a dedicated setjmp/longjmp adapter. Native Windows x86_64
GCC/UCRT also passes the independent C complex ABI caller/callee probe and the
full C+ complex integration suite for float, double, and long double. The
verified Windows descriptor now enables `c17_complex`; complex arithmetic,
complex-type, and tgmath C17 fixtures all execute and pass dependency audits.
The latest native Windows C17 report is 51 pass, 0 fail, 0 unsupported, and
0 planned. The native Windows `RuntimeStdAtomicTest` also executes the C+
atomic facade through concurrent fetch operations, compare/exchange, memory
orders, thread fences, wait/wake, and runtime-managed workers; its PE passes the
dependency audit. `RuntimeLibcFamiliesTest.windowsC17FamiliesExecuteFormattingClockMathClassificationLocaleAndSignals`
also executes C17 formatting, clocks, math classification, ctype, locale, and
signal/raise behavior through the production Windows runtime, plus UTF-8 to
wide-character conversion, reverse conversion, and malformed-sequence
rejection; its PE passes the dependency audit. The Linux counterpart also
passes after explicitly linking the same runtime wide-character adapter. At
this checkpoint, R4 remained open for broader Windows execution evidence;
final native Windows full-suite and C17 report results above close the
registered x86_64 phase gate only.

### R4.1/R4.2 completion record

Implemented and executed on Linux:

- the PAL now exposes explicit-width page allocation and release operations;
- Linux x86_64 and AArch64 syscall paths are catalogued in the platform
  adapter, while Windows uses the corresponding VirtualAlloc/VirtualFree ABI;
  the native Windows C17 basic fixture executes allocation, zeroing, resize,
  alignment, and release paths through it;
- the runtime allocator obtains whole pages, stores allocation metadata outside
  the user span, validates overflow and alignment requests, and releases the
  original page range on free;
- allocate, allocate-zeroed, resize, free, and aligned allocation are exposed
  through the runtime header and the native `std.alloc` façade;
- a Linux C fixture executes alignment, zeroing, data-preserving resize, and
  release behavior.

R4.3 has complete Linux acceptance and focused native Windows execution through
the shared C17 basic fixture. The remaining Windows R4 family validation is
tracked by the phase gate above.

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

- the self-hosted stdio layer has unbuffered formatted-output routines,
  `puts`, `fputc`, and explicit stream markers without host stdio calls;
- at the time of this partial record, `fgetc` returned EOF unconditionally,
  while `fprintf` and `fputc` ignored the selected stream; see the later
  R5.3.3 implementation update below for the corrected standard-channel path;
- the formatter and SDK `stdarg.h` provide the selected C ABI's varargs path;
- monotonic platform ticks feed `clock` and `time`, with Linux syscall and
  Windows system-API adapters;
- `sqrt`, `fabs`, character classification/case conversion, the C locale, and
  basic signal registration/raise behavior have executable Linux coverage.

The standard-channel implementation and its freestanding Linux behavior test
are recorded under R5.3.3 below; the full declared C stdio surface is covered
by the R4.4/R4.5 completion records below. AArch64 `setjmp`/`longjmp` was
subsequently implemented and exercised in the R8.3 Linux matrix; Windows
context execution and final cross-platform validation remain deferred.

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

### R4.5 partial completion record

Implemented and executed on Linux x86_64:

- `cplus libc test` now checks all C17 SDK headers, required runtime source
  families, the target runtime link plan, and target-specific context support;
- independent C fixtures cover the combined C17 family surface and the
  Linux x86_64 `setjmp`/`longjmp` context adapter;
- each fixture is compiled and linked independently, executed, and inspected
  for undeclared host-library or compiler-runtime dependencies;
- missing, planned, and unsupported checks are retained in the report and make
  the command fail rather than being silently counted as delivered;
- at the time of this partial record, the Linux x86_64 report showed 38 pass
  and omitted stream-specific `fprintf`/`fputc` behavior and real `fgetc`; the
  updated 42-pass report and full stdio fixture are recorded below.

### R4.4 Linux implementation completion record

The declared C stdio surface is implemented over PAL-backed standard channels.
`c17-stdio.c` exercises every function in `sdk/libc/include/stdio.h`: all
formatted and varargs output variants, `fgetc`, `fputc`, `puts`, and `fflush`.
It verifies input bytes including `0xff`, EOF, invalid-stream sentinels, and
separate stdout/stderr routing. `c17-basic.c` and `c17-context.c` continue to
cover the other claimed Linux C17 families and setjmp context behavior. The
Linux x86_64 implementation leaf is complete; Windows/AArch64 execution is not
claimed here.

### R4.5 Linux report completion record

`C17ConformanceRunner` now supplies fixture stdin, captures stdout and stderr
independently, checks exact expected channel bytes, and records a separate
stream case. The fixture also passes `RuntimeDependencyAuditor` with no
undeclared host/compiler-runtime dependency. `ConformanceTest` asserts the
stream case is present and passing. Running
`./gradlew :cli:run --no-daemon --args='libc test --target linux-x86_64'`
reports 42 pass, 0 fail, 0 unsupported, and 0 planned; `./gradlew build
--no-daemon` passes. This satisfies the recorded R4.4/R4.5 Linux checks but
does not establish all C17 headers: `<complex.h>` and `<tgmath.h>` remain TODO
under R4.6. The parent R4 phase gate remains `DOING` until R4.6 and deferred
Windows runtime/libc validation pass.

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

## R5 [DONE] Complete native std and platform services for claimed profiles

**Dependency-ordered work queue**

- R5.1 [DONE] — complete `std.core`, `std.mem`, `std.string`, `std.text`,
  and collection value/error types, including target-aware byte/size/index
  types. Acceptance covers Linux x86_64 self-hosted execution and hosted UBSan
  runs, previously recorded Linux AArch64 QEMU execution, and new native Windows
  x86_64 self-hosted execution; `NativeStdPublicSurfaceTest` verifies explicit
  imports/layouts across all four target descriptors, and the Windows PE passes
  `RuntimeDependencyAuditor`.
  **Acceptance evidence:** `NativeStdTest` checks overlap in
  both directions and same-address `std_mem_move`, null-backed span lookup,
  target-sized values, and the byte/text/value operations under each available
  GCC and Clang compiler with undefined-behavior sanitization. `std_mem_move`
  compares integerized addresses instead of applying undefined relational
  pointer comparison to unrelated objects; read-only memory/string/text
  inputs now use `const`. The span contract now explicitly makes null-backed
  spans have no addressable elements. The full foundational fixture now passes
  through the self-hosted Windows runtime as well; the hosted sanitizer subcase
  remains Linux-specific.
- R5.2 [DONE] — extend the file PAL with seek, metadata, create/remove,
  directory iteration, and stream adapters; its parent gate is represented by
  the five numbered leaves below and is not counted separately;
  - R5.2.1 [DONE] — define and declare the version-3 file PAL ABI, portable
    metadata and directory-entry contracts, and stable buffer-too-small error;
  - R5.2.2 [DONE] — implement file seek and path metadata in Linux and Windows
    adapters, with executable Linux checks and target-layout validation;
  - R5.2.3 [DONE] — implement directory create/remove and UTF-8 directory
    iteration in Linux and Windows adapters, with executable Linux checks;
  - R5.2.4 [DONE] — add target-independent `std.fs` wrappers and unbuffered
    file-stream read/write/seek/close adapters without exposing OS handles;
  - R5.2.5 [DONE] — complete the Linux/Windows filesystem-PAL conformance
    matrix, error-normalization and dependency audit, and close platform gaps.
    **Acceptance:** Linux and Windows runtime fixtures cover path/mode/handle
    validation, open/read/write/seek/close/rename, metadata, directory
    iteration and stable error mapping; linked products pass host/runtime
    dependency audits; strict source checks pass for supported target ABIs.
    Linux acceptance evidence: commit `c067dd9` extends
    `RuntimeFilePalTest` with invalid/empty/malformed-UTF-8 paths, unknown
    modes, truncation without write access, missing paths, negative and invalid
    nonnegative handles, zero-length I/O, and dependency inspection. The
    Linux x86_64 production fixture and `RuntimeStdIoTest` pass; the production
    Linux adapter passes warning-as-error syntax checks for x86_64 and
    AArch64; `RuntimeEnvironmentAndStreamsTest` and the full
    `./gradlew build --no-daemon` pass. A suite-discovered regression from
    broadening shared EBADF normalization was fixed by isolating filesystem
    handle mapping from standard-channel errors. Native Windows acceptance:
    the production filesystem PAL fixture, complete `RuntimeFilePalTest`,
    `RuntimeStdIoTest`, and the full compiler suite pass on the Windows VM
    using UCRT64 GCC. The linked native executable exercises canonical slash
    paths, file operations, seek/error normalization, rename, metadata,
    directory iteration, and standard I/O. The Windows runtime plan selects
    the PE TLS directory adapter, and the native freestanding product links
    without host CRT defaults. A
    Linux production-linked fixture now verifies that metadata follows a
    symlink and file removal unlinks the symlink without deleting its target;
    it also passes the runtime dependency audit. A separate Linux fixture
    exercises permission-denied normalization for open, metadata, directory
    open, directory creation, file removal, and rename as an unprivileged user;
    a non-directory path
    component is also checked for `NOT_FOUND` normalization across open,
    metadata, and directory-open operations. The fixture also checks one-level
    directory creation (without synthesizing missing parents) and confirms file
    removal rejects directories while preserving them. Null metadata outputs,
    null directory-read buffers, and invalid directory handles are checked for
    stable argument errors. These additional cases extend Linux evidence only.
    `RuntimeFilePalTest` also repeats
    warning-as-error C17 syntax checks for the production Linux adapter with
    Clang targeting x86_64 and AArch64. An AArch64 QEMU-linked filesystem
    fixture now exercises canonical slash-path write/read, metadata, rename,
    removal, and dependency auditing. It exposed an incorrect AArch64
    `renameat` syscall number (`276`, a different five-argument syscall); the
    adapter and syscall catalogue now use syscall `38`, and the fixture passes.
    This adds Linux/AArch64 evidence only and does not close R5.2.5. A dedicated
    Linux fixture creates a directory entry
    with an invalid UTF-8 byte and
    verifies production directory iteration returns `UNSUPPORTED`; the linked
    fixture also passes the dependency audit. The fixture additionally checks
    invalid seek origins and negative begin offsets on a valid file handle;
    these extend Linux evidence only and do not change R5.2.5 completion status.
- R5.3 [DONE] 12/12 — implement and Linux-execute the remaining PAL services;
  named Windows x86_64 execution evidence is recorded below, while the complete
  Windows target/family gate remains part of R4/R5 final acceptance;
  - R5.3.1 [DONE] — close page-memory PAL failure-path conformance for
    zero/overflow page counts, invalid releases, allocator overflow and invalid
    alignment, preserving the already-tested allocation/libc behavior;
  - R5.3.2 [DONE] — implement the specified process identity/spawn/wait ABI,
    UTF-8 argv, inherited environment/standard streams, synchronous launch
    errors and normalized exit status; Linux and native Windows process paths
    have focused executable coverage;
  - R5.3.3 [DONE] — implement environment and argument access plus portable
    standard-input/output/error service contracts, with native Linux and
    Windows startup/channel execution coverage;
  - R5.3.4 [DONE] — provide distinct wall, monotonic and process-CPU clocks
    with documented nanosecond units, checked overflow behavior, and the
    C `time()`/`clock()` mappings; native Linux and Windows PAL execution passes;
  - R5.3.5 [DONE] — implement thread create/join/current/yield and runtime TLS
    setup without requiring pthreads on Windows; native Linux and Windows
    create/join/TLS execution passes;
  - R5.3.6 [DONE] — implement mutex, condition, semaphore, once and supported
    atomic wait/wake services; ordinary atomic operations remain compiler/runtime
    intrinsics, not OS calls; native Windows synchronization/wait-wake execution
    passes alongside Linux coverage;
  - R5.3.7 [DONE] [3/3] — define and implement the fixed-layout binary
    socket-address ABI and blocking IPv4/IPv6 socket lifecycle, TCP stream, and
    UDP datagram operations with stable errors;
    - **Language:** SPEC.STDLIB §2.5 Platform Abstraction Layer and §16 `std.net`.
    - **Technical:** SPEC.TECH §78 SDK, ABI, runtime and platform architecture.
    - **Acceptance:** the shared C+/C ABI is layout-checked across all four
      supported Linux/Windows x86_64/AArch64 targets; Linux executes TCP and UDP
      loopback transfers from the production freestanding runtime with no host
      runtime dependency; Windows source compiles and the PE import table has no
      unconditional `ws2_32` dependency. Windows runtime execution remains part
      of final validation.
    - R5.3.7.1 [DONE] — declare the binary socket address and operation ABI;
      verify signatures, fixed layout, and target C+ compilation.
      - **Language:** SPEC.STDLIB §16 binary address and socket PAL contract.
      - **Technical:** SPEC.TECH §78 target ABI declarations and layout engine.
      - **Deliverable:** matching C header and C+ platform API declarations.
      - **Acceptance:** address size/alignment/member offsets and all function
        signatures agree across Linux/Windows x86_64/AArch64 ABI descriptors.
    - R5.3.7.2 [DONE] — implement Linux x86_64/AArch64 socket operations with
      direct syscalls, stable errors, and freestanding TCP/UDP loopback tests.
      - **Language:** SPEC.STDLIB §16 blocking lifecycle and transport behavior.
      - **Technical:** SPEC.TECH §78 Linux syscall adapters and runtime isolation.
      - **Deliverable:** Linux socket open/bind/listen/accept/connect/address,
        stream/datagram transfer, shutdown, and close adapters.
      - **Acceptance:** warning-as-error source checks pass for both Linux
        architectures; Linux x86_64 tests TCP and UDP roundtrips, invalid
        arguments/error normalization, and `nm -u` host-runtime isolation.
      - **Depends:** R5.3.7.1.
    - R5.3.7.3 [DONE] — implement Windows Winsock lifecycle and transport
      without an unconditional `ws2_32` import.
      - **Language:** SPEC.STDLIB §16 Windows PAL and stable socket error contract.
      - **Technical:** SPEC.TECH §78 Windows documented API and dependency policy.
      - **Deliverable:** thread-safe lazy Winsock 2.2 initialization, Windows
        socket/address adapters, and process-exit cleanup over dynamically
        resolved documented Winsock entry points.
      - **Acceptance:** warning-as-error Windows x86_64/AArch64 source checks
        pass; a freestanding PE fixture links the production socket adapter and
        imports its kernel loader/initialization APIs without statically
        importing `ws2_32`; `RuntimeLinker` selects the adapter only on Windows.
        Windows execution is now evidenced for IPv4 TCP/UDP and IPv6 TCP/UDP
        loopback through the production runtime, with dependency audit.
        Whole-runtime Windows PE linking is tracked separately by R8.3.
      - **Depends:** R5.3.7.1.
  - R5.3.8 [DONE] [3/3] — implement portable address text conversion and DNS
    resolution with UTF-8 inputs and target-independent result ownership;
    - **Language:** SPEC.STDLIB §16.1 address parsing/formatting and name resolution.
    - **Technical:** SPEC.TECH §78 shared codecs and target resolver adapters.
    - **Acceptance:** IPv4/IPv6 text conversion follows its specified canonical
      forms; valid UTF-8 hostnames resolve to caller-owned, deduplicated binary
      address records; Linux performs bounded DNS exchanges with UDP/TCP
      support and no host-runtime dependency; Windows uses the Unicode system
      resolver without a static `ws2_32` import. Windows execution remains part
      of final validation.
    - R5.3.8.1 [DONE] — implement shared IPv4/IPv6 parse/format and UTF-8
      hostname-to-A-label codecs plus the caller-owned resolver result ABI.
      - **Acceptance evidence:** host-executed RFC 5952 canonical and
        IPv4-mapped mixed-notation vectors; IPv4/IPv6 scope and malformed-input
        cases; UTF-8/Punycode and 63/64-byte label plus 253/254-byte name
        boundaries; freestanding object with no undefined host-runtime
        symbols; strict C17 syntax checks for Linux/Windows x86_64/AArch64; C+
        API and ABI checks across all four target descriptors; and a successful
        full Gradle build.
    - R5.3.8.2 [DONE] — implement configured DNS in the freestanding Linux
      platform adapter with bounded nonblocking UDP queries and TCP fallback.
      - **Acceptance evidence:** the local loopback fixture covers A and AAAA,
        CNAME follow-up, duplicate results, truncated UDP/TCP fallback,
        malformed transaction IDs/questions and source addresses, NXDOMAIN,
        timeout normalization, result-capacity behavior, and numeric-address
        bypass; resolver configuration parsing covers comments, malformed and
        duplicate entries, IPv4/IPv6 servers, and the three-server cap. The
        production-linked fixture has no undefined host-runtime symbols; strict
        warning-as-error C17 source checks pass for Linux x86_64/AArch64; the
        full Gradle build passes. Windows execution remains deferred.
    - R5.3.8.3 [DONE] — integrate the Windows Unicode system resolver through
      dynamically resolved Winsock APIs and copy results into caller storage;
      native Windows `localhost` resolution and ownership checks pass.
      - **Acceptance evidence:** strict Windows x86_64 MinGW and AArch64 Clang
        source checks pass with warnings as errors. The focused
        `RuntimeNetworkPalTest` links the production resolver and shared codec
        into a freestanding x86_64 PE fixture and confirms the required
        kernel32 heap/loader/once imports while excluding static
        `GetAddrInfoW`/`FreeAddrInfoW` and `ws2_32.dll` imports. The fixture
        exercises argument validation and is not run on Windows. The focused
        network test and full Gradle build pass. Successful Windows resolver
        execution remains deferred to final validation; full-runtime PE linking
        remains open in R8.3.
- R5.4 [DOING] — connect native std façades to the verified PAL services;
  - R5.4.1 [DONE] — implement `std.process` identity, spawn/wait, exit,
    arguments, environment and standard-stream APIs;
    - **Acceptance evidence:** the C+ integration fixture compiles and runs a
      parent and spawned child through the public façade. It verifies positive
      process identity; argument count, lookup and out-of-range behavior;
      inherited environment; invalid-argument passthrough; inherited standard
      input/output/error; child exit status and wait behavior; and zero-length
      channel operations. The statically linked Linux executable has no
      undefined host-runtime symbols. The same C+ caller now executes on
      native Windows x86_64, including environment inheritance, child argv,
      child exit status, separate inherited stdout/stderr, and stdin EOF; the
      PE passes `RuntimeDependencyAuditor`. The full Gradle build passes. Strict
      warning-as-error C17 checks pass for the forwarding source on Linux and
      Windows x86_64/AArch64.
    - The first caller compilation exposed and fixed parser lookahead that
      treated `local = functionCall(...)` as an inner-function declaration;
      a focused parser regression test passes.
  - R5.4.2 [DONE] [2/2] — complete `std.time`, including the normative UTC
    calendar representation, over the version-4 clock services;
    - R5.4.2.1 [DONE] — implement wall/monotonic/process-CPU clock forwarding,
      checked signed nanosecond durations, and C `time()`/`clock()` error
      normalization;
      - **Acceptance evidence:** C+ execution verifies all three clock values,
        signed seconds/milliseconds/nanoseconds conversion, checked addition
        and subtraction at signed 64-bit boundaries, comparison, extraction,
        status codes, and unchanged outputs on failures. A failure-injected C
        fixture verifies `time()` and `clock()` return -1 for negative PAL
        results. `std_duration_t` is size/alignment checked across Linux and
        Windows x86_64/AArch64. Strict warning-as-error C17 checks pass for the
        runtime source on all four targets, the static Linux caller has no
        undefined host-runtime symbols, and the full Gradle build passes. The
        C+ fixture now executes natively on Windows x86_64, covering clocks,
        checked duration arithmetic, calendar conversion, and invalid boundary
        behavior; the PE passes `RuntimeDependencyAuditor`.
    - R5.4.2.2 [DONE] — define and implement proleptic-Gregorian UTC calendar
      conversion to/from signed Unix seconds plus nanoseconds;
      - **Acceptance evidence:** the C+ fixture round-trips epoch, leap day,
        pre-epoch timestamp, astronomical year zero, and both signed 64-bit
        second endpoints with fractional nanoseconds. Invalid non-leap dates,
        out-of-range fields/nanoseconds, and unrepresentable years return the
        specified statuses without modifying outputs. `std_calendar_time_t`
        size/alignment/offsets match 32/8/[0,8,12,16,20,24,28] across Linux and
        Windows x86_64/AArch64 descriptors. Strict warning-as-error C17 source
        checks and the full Gradle build pass. Native Windows x86_64 executes
        the same C+ calendar round-trip and boundary fixture through
        `RuntimeStdTimeTest`; its linked product passes the dependency audit.
  - R5.4.3 [DONE] [3/3] — implement public thread, synchronization, and atomic
    APIs over the existing PAL services;
    - R5.4.3.1 [DONE] — implement the `std.thread` façade for thread creation,
      joining, current-thread identity, and yielding with opaque handles;
      - **Acceptance evidence:** `RuntimeStdThreadTest` compiles and executes a
        C+ caller that creates and joins workers, validates callback result,
        distinct positive parent/worker identities, yield, invalid handles, and
        the optional null result pointer. The linked Linux executable has no
        unresolved host-runtime symbols. Public declarations and 64-bit handle
        ABI checks pass for Linux/Windows x86_64 and AArch64 descriptors;
        warning-as-error C17 checks pass for the forwarding source on all four
        targets; the full Gradle build passes. The same C+ thread facade now
        executes natively on Windows x86_64, covering worker creation/join,
        independent identities, yield, callback return, and optional result
        storage; its PE passes `RuntimeDependencyAuditor`.
    - R5.4.3.2 [DONE] — implement typed `std.sync` mutex, condition, semaphore,
      and once wrappers over PAL synchronization state;
      - **Acceptance evidence:** `RuntimeStdSyncTest` compiles and executes a
        C+ caller covering contended mutex updates, condition signal/broadcast
        and mutex reacquisition, semaphore count plus blocked wait/post, once
        initialization, and invalid-state errors. Typed objects are four bytes
        with four-byte alignment and state offset zero across Linux/Windows
        x86_64/AArch64 descriptors. The linked Linux executable has no
        unresolved host-runtime symbols; strict warning-as-error C17 checks pass
        for the forwarding source on all four targets; the full Gradle build
        passes. The same C+ caller now executes on native Windows x86_64,
        covering contended mutex updates, condition signaling and mutex
        reacquisition, semaphore wait/post, once initialization, and invalid
        states. Its PE product passes `RuntimeDependencyAuditor`; focused tests
        pass on both Linux and Windows.
    - R5.4.3.3 [DONE] — expose native atomic load/store/exchange/compare-exchange,
      arithmetic/fence operations, and supported wait/wake services;
      - **Acceptance evidence:** `RuntimeStdAtomicTest` compiles and executes a
        C+ caller covering all six memory orders, load/store/exchange, strong
        compare-exchange success/failure and expected update, arithmetic and
        bitwise fetch operations, fences, and concurrent wait/wake. Its linked
        Linux executable has no unresolved host-runtime symbols. Atomic object
        and memory-order enum ABI checks pass for Linux/Windows x86_64/AArch64.
        The same C+ caller now executes natively on Windows x86_64, including
        concurrent workers and wait/wake; the PE product passes
        `RuntimeDependencyAuditor`.
        Warning-as-error C17 checks pass on all four target compilers, and
        generated assembly contains no `__atomic_*`, `__sync_*`, or AArch64
        atomic-helper calls. The full Linux and Windows test suites pass.
  - R5.4.4 [DONE] [4/4] — implement portable `std.net` address, DNS, socket,
    TCP and UDP APIs with explicit partial/unavailable capability behavior;
    - **Language:** SPEC.STDLIB §16–§16.2 public networking behavior and PAL
      mapping.
    - **Technical:** SPEC.TECH §78 public SDK/runtime boundary, stable ABI and
      target-specific runtime source selection.
    - **Acceptance:** C+ callers exercise the public façade against the
      production-linked PAL on Linux; public type layouts and declarations
      compile for Linux/Windows x86_64/AArch64; strict warning-as-error C17
      checks pass for shared forwarding code on all four targets; production
      Linux executables have no undefined host-runtime symbols. Native Windows
      x86_64 now executes the public TCP and UDP IPv4 loopback façades and the
      resolver façade, with PE dependency audits; it also executes C+ IPv6 TCP
      and UDP loopback through `RuntimeStdNetTest.windowsCplusStdNetFacadesExecuteIpv6TcpAndUdpLoopback`.
      The registered Windows C+ networking matrix passed in the final native
      Windows full-suite run; this does not claim unregistered cases or Windows
      AArch64 execution.
    - R5.4.4.1 [DONE] — define public `std.net` address, socket, family,
      transport, shutdown, and error types/constants plus the façade
      declarations; verify the public ABI before transport implementation.
      - **Acceptance evidence:** `RuntimeStdNetTest` explicitly imports all
        public types, constants, and functions and compiles call sites for the
        complete declared API on Linux/Windows x86_64/AArch64. The address
        layout is 28 bytes/four-byte aligned with offsets `[0,4,6,8,24]`, the
        socket alias is 64 bits, and all four enums are four bytes on every
        target. The forced focused Gradle test run passes. This is
        declaration/ABI acceptance only and does not claim executable socket
        behavior.
    - R5.4.4.2 [DONE] — implement and execute the public TCP stream façade for
      open, bind, listen, accept, connect, local/peer address, send, receive,
      shutdown, and close.
      - **Acceptance evidence:** `RuntimeStdNetTest` compiles and runs a public
        C+ IPv4 loopback caller covering open/bind/listen/connect/accept,
        local/peer addresses, bidirectional stream transfer, shutdown EOF,
        invalid arguments, output preservation on failure, and close. The
        production-linked Linux executable has no undefined host-runtime
        symbols. Strict warning-as-error C17 checks pass for the OS-neutral
        façade on Linux/Windows x86_64 and AArch64; `RuntimeLinkerTest` confirms
        selection on both operating systems. The full Gradle build passes.
        The native Windows VM also executes this C+ TCP loopback fixture,
        including bind/listen/connect/accept, address queries, bidirectional
        transfer, shutdown EOF, invalid arguments, and close; the PE passes
        `RuntimeDependencyAuditor`.
      - **Depends:** R5.4.4.1.
    - R5.4.4.3 [DONE] — implement and execute the public UDP datagram façade
      for open, bind, send-to, receive-from, and close.
      - **Acceptance evidence:** `RuntimeStdNetTest` compiles and runs a public
        C+ caller through the production PAL for IPv4 loopback empty and
        non-empty datagrams, source-address copy-out, optional null source,
        invalid handle/buffer/length cases, and close. The linked Linux
        executable has no undefined host-runtime symbols. Strict
        warning-as-error C17 checks pass for the OS-neutral façade on
        Linux/Windows x86_64 and AArch64; the full Gradle build passes.
        Native Windows x86_64 also executes this C+ UDP loopback fixture,
        covering empty and non-empty datagrams, source-address copy-out,
        optional source storage, and invalid inputs; the PE passes
        `RuntimeDependencyAuditor`.
      - **Depends:** R5.4.4.1.
    - R5.4.4.4 [DONE] — implement the public address parse/format and hostname
      resolution façade over the shared codec and platform resolver.
      - **Acceptance evidence:** `RuntimeStdNetTest` compiles and runs
        production-linked C+ IPv4/IPv6 parse and canonical-format vectors,
        numeric resolver bypass, caller-owned result copying, short-buffer and
        invalid-input output preservation, and required-count reporting. Its
        deterministic resolver-bridge fixture checks multiple result copying,
        capacity truncation, and unchanged outputs on lookup/validation errors.
        `RuntimeNetworkDnsTest` passes its deterministic local DNS fixture
        against the production Linux resolver. Linked public C+ products have
        no undefined host-runtime symbols. Strict warning-as-error C17 checks
        pass for the common façade on Linux/Windows x86_64 and AArch64; the full
        Gradle build passes. The public address/format/numeric-resolver fixture
        also executes on native Windows x86_64, including canonical IPv4/IPv6
        formatting, output-preserving invalid cases, numeric resolution, and
        capacity reporting; its PE passes `RuntimeDependencyAuditor`. Windows
        system-name resolution is separately validated by the native resolver
        fixture.
      - **Depends:** R5.4.4.1 and R5.3.8.
  - R5.4.5 [DONE] [11/11] — implement the specified portable real `std.math` and
    C `<math.h>` surface without requiring a host `libm` dependency.
    - R5.4.5.1 [DONE] — declare all C17 real `<math.h>` functions and
      float/double/long-double `std.math` entry points. **Depends:** R3.1.3.
      **Acceptance evidence:** the C import catalogue test checks the exact
      171 function names and key pointer/integer/long-double signatures;
      compiler integration tests import the variants, compile the complete
      `std.math` module, and syntax-check generated C against the SDK header.
    - R5.4.5.2 [DONE] — implement floating classification/comparison
      functions and C17 classification/comparison macros; expose the real
      constants and scalar aliases, and define errno-only math error handling.
      **Depends:** R5.4.5.1. **Acceptance evidence:** executable Linux C17
      coverage across float/double/x87 long double for classification,
      comparison, signed zero, constants, single evaluation, and `EDOM`; a
      synthetic binary128 representation fixture; strict warning-as-error
      source checks for all four target ABIs; C+ type-import coverage for
      `float_t`/`double_t`; no undefined host symbols; and a passing full build.
      Windows runtime execution is deferred.
    - R5.4.5.3 [DONE] — implement rounding and integer-conversion families.
      **Depends:** R5.4.5.1. **Acceptance evidence:** production-linked Linux
      C17 execution covers all float/double/long-double functions, ties-away
      and ties-to-even behavior, signed zero, non-finite values, and checked
      `long`/`long long` boundaries with errno policy. Synthetic binary128
      vectors cover truncation/parity; strict C17 and ABI-width/range assertions
      pass for all four targets; the linked product has no undefined host
      symbols; the full build passes. Windows runtime execution is deferred.
    - R5.4.5.4 [DONE] — implement decomposition, scaling, sign, NaN, and
      adjacent-value manipulation families. **Depends:** R5.4.5.1.
      **Acceptance evidence:** production-linked Linux C17 execution covers all
      three real precisions, normal/subnormal decomposition, signed zero,
      special values, errno, extreme `long` exponents, deterministic NaN tags,
      adjacent-value and `nexttoward` direction cases. The linked executable
      has no undefined host symbols; strict warning-as-error C17 checks pass
      for Linux/Windows x86_64 and AArch64 target ABIs; the full Gradle build
      passes. Windows runtime execution remains deferred.
    - R5.4.5.5 [DONE] — implement remainder and quotient-remainder families.
      **Depends:** R5.4.5.1. **Acceptance evidence:** production-linked Linux
      C17 execution covers `fmod`, `remainder`, and `remquo` at all three real
      precisions, with a bounded integer-oracle sweep over signs and divisors,
      nearest-even ties, signed zero, NaNs/infinities, domain errno, subnormal
      operands, very large exponent gaps, and signed low-three-bit quotients.
      The executable has no undefined host symbols; strict warning-as-error
      C17 checks pass for Linux/Windows x86_64 and AArch64 target ABIs; the
      full Gradle build passes. Windows runtime execution remains deferred.
    - R5.4.5.6 [DONE] — implement absolute value, power, roots, and hypotenuse
      families. **Depends:** R5.4.5.1. **Acceptance evidence:** production-linked
      Linux C17 execution covers all three real precisions, signed zero,
      subnormal square roots and powers, roots, integer and non-integral power,
      special-value precedence, negative-base domain errors, zero-base poles,
      and overflow/underflow errno. The executable has no undefined host
      symbols; strict warning-as-error C17 checks pass for Linux/Windows
      x86_64 and AArch64 target ABIs; the full Gradle build passes. Private
      log/exp kernels used by `pow` do not complete the public R5.4.5.7 API.
      Windows runtime execution remains deferred.
    - R5.4.5.7 [DONE] — implement exponential and logarithmic families.
      **Depends:** R5.4.5.1. **Acceptance evidence:** production-linked Linux
      C17 execution covers `exp`, `exp2`, `expm1`, `log`, `log10`, `log1p`,
      `log2`, and `logb` at all three real precisions, including exact integer
      scaling to subnormals, cancellation-sensitive small inputs, signed zero,
      infinities/NaNs, domain/pole errno, and range errors. The executable has
      no undefined host symbols; strict warning-as-error C17 checks pass for
      Linux/Windows x86_64 and AArch64 target ABIs; the full Gradle build
      passes. Windows runtime execution remains deferred.
    - R5.4.5.8 [DONE] — implement trigonometric and hyperbolic families.
      **Depends:** R5.4.5.1. **Acceptance evidence:** production-linked Linux
      C17 execution covers signed zero, ordinary and reduced large-angle
      values, inverse-trig quadrants, hyperbolic values, NaN/infinity behavior,
      domain/pole errno, and representative float/long-double variants. The
      executable has no undefined host symbols; strict warning-as-error C17
      checks pass for Linux/Windows x86_64 and AArch64 target formats; the full
      Gradle build passes. Windows runtime execution remains deferred.
    - R5.4.5.9 [DONE] — implement error and gamma families.
      **Depends:** R5.4.5.1. **Acceptance evidence:** production-linked Linux
      C17 execution covers known values across all three real precisions,
      error-function tails, signed zero, gamma reflection, NaN/infinity,
      gamma poles, and overflow/underflow errno. The executable has no
      undefined host symbols; strict warning-as-error C17 checks pass for
      Linux/Windows x86_64 and AArch64 target formats; the full Gradle build
      passes. Windows runtime execution remains deferred.
    - R5.4.5.10 [DONE] — implement positive-difference, extrema, and
      fused-multiply-add families. **Depends:** R5.4.5.1. **Acceptance
      evidence:** production-linked Linux C17 execution covers NaN selection
      and signed-zero extrema, exact single-rounding cancellation and
      ties-to-even, subnormal boundaries, overflow cancellation, errno cases,
      and invalid infinity/zero combinations. The linked executable has no
      undefined host symbols; strict warning-as-error C17 checks pass for
      Linux/Windows x86_64 and AArch64 target formats; the full Gradle build
      passes. Windows runtime execution remains deferred.
    - R5.4.5.11 [DONE] — expose the implemented real operations through the
      self-hosted `std.math` façade and pass the complete real-math runtime,
      C17 fixture, and host-library dependency audits. **Depends:**
      R5.4.5.2–R5.4.5.10. **Acceptance evidence:** `RuntimeStdMathFacadeTest`
      links and executes C+ imports across representative signatures and
      precision variants, checks every declared façade symbol is defined by
      the bundled runtime, and verifies the executable has no undefined host
      symbols. All `RuntimeStdMath*` tests pass, including strict C17 checks
      for Linux/Windows x86_64 and AArch64; the full Gradle build passes.
      Native Windows x86_64 now also passes this integration test: all 172
      public façade symbols are retained and found in the PE product, the C+
      executable runs representative operations across real precisions and
      math families, and the product passes `RuntimeDependencyAuditor`. The
      exhaustive numerical edge-case vectors for R5.4.5.2–R5.4.5.10 remain
      Linux-only; Windows execution does not receive blanket per-function
      edge-case credit.
  - R5.4.6 [DONE] — propagate verified target platform-service capabilities,
    produce stable unavailable-service diagnostics, and ensure unused runtime
    services do not add platform dependencies. **Acceptance:** supported
    services flow from the selected descriptor/profile into CPX target
    metadata, while system-runtime and unsupported target profiles expose no
    C+ services; `require_service("name");` accepts exactly one canonical
    service and reports unavailable, malformed, and unknown requirements with
    stable `CPX603` diagnostics; unsupported self-hosted targets fail with
    stable `SDK013`; the dependency auditor validates ELF class/machine,
    PE/COFF, and Mach-O formats and fails closed on unrecognized products or
    unavailable/invalid inspection output; a production-linked minimal Linux
    program omits unreferenced platform-service symbols, passes the dependency
    audit, and executes successfully. **Evidence:** `CpxExpansionTest`,
    `TargetServiceCapabilityIntegrationTest`, `RuntimeLinkerTest`,
    `RuntimeDependencyAuditorTest`, `RuntimeUnusedServicesTest`,
    `RuntimeStdMathFacadeTest`, `LinkDriverTest`, and the full
    `./gradlew build --no-daemon` pass. Windows runtime execution and real PE
    import audit remain deferred to final Windows validation and receive no
    completion credit. **Commit:** `3f6177d`.
- R5.5 [DONE] — record Darwin as explicitly capability-gated, without
  claiming a partial adapter as complete. `PlatformAbiTest` and
  `TargetDescriptorTest` confirm Darwin exposes no platform services, while
  `RuntimeLinkerTest` verifies stable `SDK013` rejection for self-hosted
  runtime selection. **Commit:** `3f6177d`; Darwin execution is not claimed.

R5.2 implementation is sequenced after R1.1.1–R1.2.5 and R1.4 because it
extends public SDK function signatures and must use the verified C primitive,
type-import, and alias boundaries. R5.2.1 freezes the ABI before adapter work.
R5.2.5 subsequently closed the Windows filesystem execution and audit gate;
the remaining subsystem gates were tracked independently until the final
Windows suite and service evidence recorded above.
The R5.3/R5.4 leaves were tracked independently until the final native Windows
full-suite pass recorded above. That pass closes the registered R5 Windows
x86_64 service gate; it does not claim other Windows architectures or Darwin
execution. Math algorithms belong above the PAL, so R5.4.5 does not add a
platform math service.

R5.3 leaf acceptance evidence is executable, not declaration-only: R5.3.1
checks allocation/zeroing/alignment/reallocation/release and overflow; R5.3.2
checks positive process identity, child argv, inherited environment/standard
streams, synchronous missing-executable errors, wait/reap and exit status;
R5.3.3 checks current-process arguments, environment and standard streams;
R5.3.4 distinguishes wall, monotonic and
CPU clocks; R5.3.5 exercises create/join/TLS; R5.3.6 exercises contended
mutex/condition/semaphore/once and supported atomic wait/wake; R5.3.7 executes
IPv4 TCP and UDP loopback transfers through the binary-address PAL, checks
socket/error behavior, verifies no hidden host-runtime dependency, and executes
Windows x86_64 IPv4 TCP/UDP plus IPv6 TCP/UDP loopback with dependency audits.
R5.3.8.1 executes shared address and
hostname codec vectors; R5.3.8.2 executes Linux DNS behavior and validates
resolver ownership; R5.3.8.3 has strict Windows source and PE-import evidence,
plus successful native Windows `localhost` resolution through the production
facade. Each service also needs stable error mapping and source/dependency
isolation evidence. Remaining Linux AArch64 runtime execution is still open.
R5.3.7 does not include UTF-8
address parsing/formatting or DNS, which remain exclusively in R5.3.8. The
socket and address/DNS work items were each decomposed into independently
testable leaves, so the roadmap denominator is now 69 rather than 65; these
decompositions added no completion credit.

R5.4 leaf acceptance requires C+ caller execution over the corresponding PAL
surface, not just successful parsing or generated declarations. The process,
time, thread/sync and network façades each receive focused behavior tests;
math receives known-value, boundary and exceptional-value tests without a
host `libm`; the capability task checks unavailable-target diagnostics and
proves unused services do not introduce link dependencies.

R5.2.1 acceptance evidence: the normative PAL v3 filesystem contract and C+
declarations agree on stable error codes, operations, and the 32-byte metadata
record. The version-4 ABI preserves those version-3 services. The
`AbiLayoutTest.versionFourPreservesVersionThreeFileMetadataLayout` check covers
metadata size, alignment, member offsets, and declarations against Linux and
Windows x86_64/AArch64 descriptors; `./gradlew build` passes on Linux. The
Windows entries are descriptor-model checks only, not execution evidence.

R5.2.2 acceptance evidence: Linux x86_64 PAL execution covers seek relative to
the current position and end-of-file, path metadata size/kind/timestamp fields,
stable invalid-handle errors, and successful file roundtrip behavior through
`RuntimeFilePalTest`; Linux/AArch64 syscall selection and Windows ABI layouts
remain target-modeled. The Linux and Windows adapter implementations are
present, but Windows execution is explicitly deferred to R5.2.5. The complete
`./gradlew build` passes on Linux.

R5.2.3 acceptance evidence: Linux x86_64 runtime execution creates and removes
a directory and file, enumerates the file, confirms a too-small output buffer
does not consume the entry, reaches end-of-directory, and rejects removal of
a non-empty directory. `RuntimeFilePalTest` and the full `./gradlew build`
pass. Linux/AArch64 syscall selection and Windows adapter code are present but
not executed; Windows execution remains assigned to R5.2.5.

R5.2.4 acceptance evidence: `std.fs` now declares target-size transfer,
metadata, seek, create/remove, and directory wrappers; the C runtime forwards
them using the SDK's `size_t`/`ptrdiff_t` definitions and copies metadata into
the source-level type. `std.io` provides unbuffered open/read/write/seek/close
and state checks over those wrappers. `RuntimeStdIoTest` compiles and links the
C+ modules with the self-hosted Linux runtime and executes file and directory
roundtrips; ABI tests confirm both PAL and std metadata layouts across the four
declared Linux/Windows x86_64/AArch64 descriptors. `./gradlew build` passes on
Linux. At this subtask checkpoint Windows execution had not yet been performed;
the later R5.2.5 acceptance record documents native Windows PAL and std.io
execution.

R5.3.1 acceptance evidence: Linux `RuntimeAllocatorTest` now exercises
zero/overflow page counts, invalid release arguments, successful page release,
allocator size/alignment overflow, invalid alignment, zero-size allocation,
zeroed allocation, data-preserving resize and release. `RuntimeLibcCoreTest`
executes `calloc`, `realloc`, `aligned_alloc`, `free`, zeroing, alignment and
overflow-to-`ENOMEM` behavior through the libc façade. `AbiLayoutTest`
confirms the fixed-width page-count type and declarations on Linux/Windows
x86_64/AArch64 descriptors. The Windows adapter rejects zero-page release
requests. Native Windows `RuntimeAllocatorTest.windowsPageAllocatorRejectsInvalidReleaseAndSupportsAllocationLifecycle`
executes zero/overflow allocation failures, invalid release arguments, a valid
page release, allocator alignment/overflow, zeroing, resizing, and freeing
through the production runtime; the PE product passes its dependency audit.
Linux and Windows focused tests pass.

R5.3.2 acceptance evidence: `RuntimeProcessPalTest` builds a freestanding,
static Linux executable with `-nostdlib` and the production startup/PAL
sources. It verifies positive process identity, startup environment capture and
inheritance, argv delivery, inherited stdout, synchronous invalid/missing
executable errors, child wait/reap, normal exit status, and signal status
normalization. `AbiLayoutTest` verifies the opaque `long long` process handle
and declarations across Linux/Windows x86_64/AArch64 descriptors. Linux x86_64
runtime execution passes; Linux AArch64 adapter syntax compilation passes. The
native Windows VM also passes
`RuntimeProcessPalTest.windowsProcessPalSpawnsWaitsAndNormalizesLaunchFailures`:
it checks process identity, invalid wait/executable inputs, missing-program
normalization, self-spawned argv delivery, inherited environment lookup in the
child, inherited child stdout, and child exit-status propagation; the linked
product passes the dependency audit. The full
`./gradlew build --no-daemon` passes on Linux and Windows.

R5.3.3 acceptance evidence: `RuntimeEnvironmentAndStreamsTest` builds a static
Linux executable from production startup, runtime, libc, allocator, and PAL
sources with `-nostdlib`. It verifies `argc`/`argv`, indexed argument lookup,
inherited `NAME=value` environment access, stdin bytes and EOF, stdout/stderr
routing, zero-length and invalid-buffer contracts, and stable I/O errors after
closing each standard descriptor. `nm -u` confirms no unresolved host-runtime
symbols. `RuntimeProcessPalTest` also passes with the new explicit `envp`
startup ABI. Linux AArch64 runtime/startup source checks and startup assembly
compilation pass; Windows x86_64 adapter/startup sources pass strict MinGW
syntax compilation. The native Windows VM passes
`RuntimeEnvironmentAndStreamsTest.windowsStartupExposesArgumentsEnvironmentAndStandardChannels`:
the production runtime exposes argv and inherited environment, reads stdin,
keeps stdout/stderr separate, and passes a dependency audit. `./gradlew build
--no-daemon` passes on Linux. The independent C17 stdio fixture is registered
in the libc report and audits the declared stdio channel surface; R4.4/R4.5
Linux leaves are complete, while their C17 Windows family matrix remains open.

R5.3.4 acceptance evidence: `RuntimeClockPalTest` builds and executes a
freestanding static Linux x86_64 program against the production PAL and runtime
sources with `-nostdlib`. It checks exact nanosecond conversion, malformed
timespec rejection, signed-64-bit overflow, distinct non-negative wall,
monotonic, and process-CPU readings, monotonic non-regression, CPU-time
progress, `time()` wall seconds, `clock()` process CPU nanoseconds, and the
legacy monotonic alias; `nm -u` confirms no unresolved host-runtime symbols.
`AbiLayoutTest` verifies all three clock results are signed 64-bit values for
Linux/Windows x86_64/AArch64 and checks the version-4 PAL API value. The PAL v4
contract preserves v3 filesystem services. Linux AArch64 runtime syntax and
startup assembly checks pass; Windows x86_64 adapter/runtime sources pass
strict MinGW syntax checking. The full `./gradlew build --no-daemon` passes,
and `cplus libc test --target linux-x86_64` reports 42 pass, 0 fail,
0 unsupported, and 0 planned. The native Windows VM also passes
`RuntimeClockPalTest.windowsProvidesWallMonotonicAndProcessCpuClocksThroughProductionPal`:
the full runtime validates wall/monotonic/process-CPU nanoseconds, `time()`,
`clock()`, and the monotonic compatibility alias, with a successful executable
dependency audit. Linux AArch64 clock execution remains unverified.

R5.3.5 acceptance evidence: `RuntimeThreadPalTest` links and executes a
freestanding static Linux x86_64 program with no host runtime dependencies. It
performs eight create/join cycles; checks current-thread identity, yield,
return-value transfer, invalid inputs, runtime attachment, initialized `.tdata`,
zeroed `.tbss`, independent C `errno`, and parent/child TLS isolation; and
confirms `nm -u` is empty. The self-hosted Linux startup now installs the main
thread's TLS before common runtime initialization. Linux workers use raw clone,
`CLONE_SETTLS`, and child-clear-TID futex joining; the Windows adapter uses
`CreateThread`/wait and loader-managed TLS. `RuntimeLinkerTest` verifies the
architecture helper and Linux TLS linker script are selected. ABI checks cover
all four declared target descriptors. Linux x86_64 execution and the full
`./gradlew build --no-daemon` pass. Linux AArch64 runtime/startup sources pass
strict syntax and assembly checks. The native Windows VM also passes
`RuntimeThreadPalTest.windowsCreatesJoinableThreadsWithIndependentRuntimeTls`:
the full runtime creates and joins a worker, validates runtime attachment,
thread identity, yield, return value, and independent initialized TLS, and
passes the Windows executable dependency audit. Linux AArch64 runtime
execution remains unverified. The C17 report remains 42 pass, 0 fail,
0 unsupported, and 0 planned.

R5.3.6 acceptance evidence: `RuntimeSyncPalTest` links and executes the
production shared synchronization runtime and Linux x86_64 PAL into a
freestanding static binary. It stresses four concurrent workers through
contended mutex updates and once initialization; exercises semaphore wait/post
and overflow, condition signal/broadcast with mutex reacquisition, and atomic
wait/wake; verifies invalid argument/alignment handling; and confirms `nm -u`
reports no host-runtime dependencies. The synchronization C+ API compiles and
its 32-bit state/value and pointer ABI is modeled across Linux/Windows
x86_64/AArch64. Strict warning-as-error C syntax checks pass for Linux x86_64,
Linux AArch64, Windows x86_64, and Windows AArch64. `RuntimeLinkerTest`
confirms the common algorithm source is selected for both Linux and Windows
runtime plans. `./gradlew build --no-daemon` passes, and the C17 report remains
42 pass, 0 fail, 0 unsupported, and 0 planned. The native Windows VM now passes
`RuntimeSyncPalTest.windowsExecutesSynchronizationAndAtomicWaitWakeThroughProductionPal`,
covering contended mutex/once state, semaphore wait/post, condition signaling,
and atomic wait/wake through the full runtime; its product passes dependency
audit. The test exposed that `WaitOnAddress` and wake exports are not present in
the VM's `Kernel32.dll` export table. The Windows resolver now searches both
Kernel32 and KernelBase, preserving dynamic lookup and avoiding static imports.
Linux AArch64 runtime execution remains unverified.

R5.3.7.1 acceptance evidence: `RuntimeNetworkPalTest` compiles the socket C+
API for Linux/Windows x86_64/AArch64 and checks all declared function symbols,
the 64-bit handle, and the 28-byte address record's four-byte alignment and
member offsets `[0, 4, 6, 8, 24]`. An independent C17 syntax check compiles the
runtime header with static assertions for the same address layout. The focused
`./gradlew :compiler:test --tests cplus.compiler.RuntimeNetworkPalTest`
passes. No socket adapter or runtime behavior is included in this leaf.

R5.3.7.2 acceptance evidence: the production freestanding Linux PAL passes
warning-as-error C syntax checks for x86_64 and AArch64. The Linux x86_64
`RuntimeNetworkPalTest` builds and executes a static no-host-runtime fixture
covering IPv4 TCP request/response and orderly shutdown, IPv4 UDP roundtrips,
IPv6 TCP loopback, address conversion, invalid inputs, normalized connection
failure, and zero-capacity stream receive; `nm -u` confirms no unresolved host
symbols. `PlatformAbiTest` verifies the target-specific socket syscall numbers
and that socket transport is advertised only for Linux. The focused network
and ABI tests pass, as do `./gradlew build --no-daemon` and the Linux x86_64
C17 report (42 pass, 0 fail, 0 unsupported, 0 planned). Strict C syntax checks
also pass for Windows x86_64/AArch64 source, but those are not Windows socket
runtime or import-table evidence. Windows runtime execution and Linux AArch64
runtime execution remain deferred and receive no credit from this leaf.

R5.3.7.3 acceptance evidence: Windows x86_64 MinGW and Windows AArch64 Clang
compile the production Windows platform and network sources with warnings as
errors. `RuntimeLinkerTest` confirms `network.c` is selected only for the
Windows runtime plan. `RuntimeNetworkPalTest` links the production network PAL
into a freestanding x86_64 PE fixture and inspects its imports: kernel32 loader
and once-initialization APIs are present, while `ws2_32.dll` is absent. The
native Windows VM now also passes
`RuntimeNetworkPalTest.windowsExecutesTcpLoopbackThroughTheFreestandingPal`:
the complete production runtime binds an ephemeral IPv4 loopback port,
connects, transfers and verifies bytes, and closes all sockets; the executable
passes `RuntimeDependencyAuditor`. The native Windows socket fixture also
executes IPv4 TCP/UDP and IPv6 TCP/UDP loopback through the complete production
runtime and passes dependency audit. Linux AArch64 runtime execution remains
deferred.

R5.3.8.3 acceptance evidence: Windows x86_64 MinGW and Windows AArch64 Clang
compile the production resolver adapter with warnings as errors. The focused
`RuntimeNetworkPalTest` links the Windows adapter and shared address/hostname
codec into a freestanding x86_64 PE image; import inspection confirms the
kernel32 loader, once-initialization, and heap APIs are present, while static
`GetAddrInfoW`, `FreeAddrInfoW`, and `ws2_32.dll` imports are absent. The PE
fixture checks resolver argument validation. The native Windows VM now passes
`RuntimeStdNetTest.windowsStdNetResolverExecutesThroughProductionPal`: the
production C+ facade successfully resolves `localhost`, validates every
returned address family, port, and reserved field, and checks that invalid
arguments preserve caller-owned output state. The linked executable passes the
runtime dependency audit. Together with the existing PE import inspection,
this verifies the successful Windows Unicode system-resolver path without a
static Winsock import. The native Windows socket fixture also executes IPv4
TCP/UDP and IPv6 TCP/UDP loopback through the complete production runtime and
passes dependency audit.

### R5.1 status audit

The memory, string, text, collection, and value/error carrier work below has
Linux x86_64, Linux AArch64 QEMU, and native Windows x86_64 execution evidence.
Target-aware `usize`/`isize` aliases, dependent
collection/byte APIs, and core pointer/limit operations are implemented over
descriptor-modeled `size_t`/`ptrdiff_t`; ABI checks cover the declared Linux
and Windows target models. R5.1 acceptance is complete; R5 remains `DOING` for
the broader Windows native-service matrix.

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

Current-pass evidence: `NativeStdTest` executes the size/index, pointer,
comparison, numeric-limit, alignment, unsigned-byte, span, and raw-view
operations through the self-hosted runtime on Linux and native Windows.
`NativeStdPublicSurfaceTest` verifies explicit imports and layouts across Linux
and Windows x86_64/AArch64 descriptors. The Windows PE passes the dependency
audit; Linux additionally runs the available GCC/Clang UBSan variants. Existing
Linux AArch64 QEMU execution evidence is retained below.

The pointer-utility contract now explicitly limits offsets to the originating
object or one-past position, and requires same-array pointer differences to fit
`isize`; the shared host/AArch64 fixture exercises zero, positive, and negative
distances plus the one-past offset. Focused `NativeStdTest` passes with the host
UBSan runs, Linux AArch64 QEMU execution, and native Windows self-hosted
execution; these checks contribute to the accepted R5.1 suite.

Additional target-matrix evidence: a QEMU-conditional `NativeStdTest` compiles
the same C+ core/memory/string/text/collection modules for Linux AArch64, links
them with the self-hosted runtime, audits the static product for undeclared
dependencies, and executes the full shared conformance fixture. It covers the
error/result/option carriers; normal, empty, reversed, crossing-zero, and
full-endpoint ranges; slices; pointer and width operations; memory alignment,
spans, raw views, and overlapping moves; string copy/append/equality; text
queries; and unsigned-byte operations. This fixture is part of R5.1's accepted
target coverage.

The complete host-side generated-C fixture now also links and runs through
`LinkDriver` and the self-hosted runtime with each available GCC/Clang driver;
each product passes `RuntimeDependencyAuditor`. The sanitizer-backed hosted
compiler runs use the undefined-behavior sanitizer, not evidence for
host-independent linking. The fixture exercises both error/result paths,
present/absent options, empty and reversed ranges/slices/spans, string
equality/copy/append, text empty/prefix queries, and unsigned-byte memory
comparison as well as the previously listed operations. These hosted runs
remain Linux-only; self-hosted runtime execution covers Linux and Windows.

Follow-up audit found that the value-carrier and collection structures were
not exported for named imports and `std_text_is_ascii` was not public; range
length also used a potentially overflowing signed subtraction. Commit
`dbfb1ae` exports those types/operation and computes positive range lengths
without signed overflow. `NativeStdPublicSurfaceTest` compiles explicit imports
and checks field layouts for Linux and Windows target descriptors;
`NativeStdTest` executes ranges crossing zero and the full signed endpoint
range under undefined-behavior sanitization. Focused tests and the full Gradle
build pass. Combined with the native Windows execution, this completes the
R5.1 work item. At this checkpoint, the R5 phase gate remained open for other
Windows service and libc families; see the final validation checkpoint for
closure evidence.

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

## R6 [DONE] [4/4 leaves] CLI transcoder and build-product completion

**Dependency-ordered work queue**

- R6.1 [DONE] — define project/workspace manifests and one source/import model
  shared by `check`, `transcode`, `build`, and `run`, including SDK-rooted
  resolution of explicitly imported `std.*` source modules. `cplus.toml`
  `[project]` and `cplus.workspace.toml` `[workspace]` manifests provide an
  entry source and optional relative source roots/member directories. The
  shared discovery path handles manifest roots, path-relative imports, and
  explicit standard imports from the selected SDK, recursively collecting
  imported source units. CLI tests execute one project via `check`,
  `transcode`, `build`, and `run`, check a workspace manifest, and run an
  explicitly imported `std.fixed_width` module without manually passing its
  source path. `./gradlew :cli:test --tests cplus.cli.CliIntegrationTest
  --no-daemon` and `./gradlew build --no-daemon` pass. Implementation commit:
  `0ca174d`.
- R6.2 [DONE] — normalize output, header, map, target, runtime, libc, SDK,
  compiler, sysroot, C-source, and library options with deterministic paths.
  Filesystem inputs become absolute normalized paths relative to the invocation
  directory; manifest paths remain manifest-relative; target triples are
  lowercased; compiler executable names and named libraries remain names while
  path-shaped values normalize. Output parents are created, `run --output`
  retains its executable, and `--map` writes deterministic generated/source
  byte ranges with project-relative paths, including imported source modules.
  CLI integration covers nested C/header/map outputs, imported source maps,
  uppercase target selection, explicit SDK/runtime/libc/sysroot/native inputs,
  retained run products, and the project/workspace command model. The complete
  `:cli:test` suite and `./gradlew build --no-daemon` pass. Implementation
  commit: `81aa251`.
- R6.3 [DONE] — make fat-JAR assembly reproducible, clean temporary products,
  preserve process failures, and emit stable diagnostics. Two forced fat-JAR
  builds (`:cli:fatJar --rerun-tasks`) produced identical SHA-256
  `e3ff1c247bf8697b0726046706d159fb4053622f3024404e9e97f206e7556557`; the
  packaged CLI passes `check examples/minimal.cp`. Integration coverage verifies
  temporary-product cleanup after successful execution and compilation failure
  and propagates child exit status 23. Focused CLI tests and
  `./gradlew build --no-daemon` pass. Commit: `0548b95`.
- R6.4 [DONE] — make `sdk`, `target`, `abi`, `runtime`, `libc`, and `audit`
  validate the exact artifacts consumed by a normal build. Inspection commands
  accept a selected SDK manifest; `sdk doctor` verifies profile compatibility
  and a target-specific runtime link plan; `runtime inspect` reports the actual
  `RuntimeLinker` startup/runtime inputs and flags; target/ABI/libc commands
  resolve from the selected SDK; and `audit` validates the selected manifest,
  target, and build profile before inspecting a normalized binary path. CLI
  integration covers selected-manifest SDK packaging, malformed manifest
  rejection, Windows target/ABI/runtime-plan selection, and auditing a binary
  built by the CLI. `./gradlew :cli:test --no-daemon` and
  `./gradlew build --no-daemon` pass. The packaged JAR passes
  `check examples/minimal.cp`; Windows-target `sdk doctor` and runtime-plan
  inspection pass; Linux `libc test` reports 51 pass, 0 fail, 0 unsupported,
  and 0 planned. Native Windows VM validation now builds/loads the fat JAR,
  checks `examples/minimal.cp`, builds and audits a Windows PE (only
  `KERNEL32.dll` observed), and runs `examples/import_defer.cp` successfully
  with the expected stdout and exit status. This is a product smoke test, not
  the clean-checkout Windows product smoke at this earlier checkpoint; final
  clean-archive Windows product evidence is recorded above.
  Commit: `95e18a0`.

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

## R7 [DONE] [4/4 leaves] LSP and VS Code product completion

**Dependency-ordered work queue**

- R7.1 [DONE] — make workspace indexing and imported-source URI mapping
  authoritative for diagnostics, definitions, references, symbols, hover,
  completion, tokens, and edits. Parsed import declarations now drive
  transitive source discovery; connected open documents use live buffer text,
  while unrelated documents remain isolated. The requested source artifact is
  selected per LSP request, and compiler source identities map cross-file
  ranges to owning URIs/current text. Open diagnostics refresh across connected
  documents. Document symbols and rename are implemented; rename uses exact
  identifier ranges and edits only C+ workspace sources. `CliIntegrationTest`
  covers cross-file navigation in both directions, diagnostics, symbols,
  hover, completion, semantic tokens, rename, and import-like comments; the
  existing unrelated-workspace isolation test passes. `./gradlew :cli:test
  --no-daemon` and `./gradlew build --no-daemon` pass. Commit: `90740c3`.
- R7.2 [DONE] — run configured `java -jar <cli>` for LSP and Run Main with
  portable settings, working directories, and path normalization. The
  extension passes Java, JAR, entry-source, and cwd values as separate process
  arguments, and optional `cplus.server.sdkManifest` selects the SDK through a
  JVM property so the project cwd need not be inside the CLI repository. Run
  Main delegates source discovery to the CLI and no longer duplicates import
  scanning. `npm test` passes all six tests, including actual fat-JAR LSP
  initialization and multi-module Run Main execution from temporary workspaces
  with spaces in their paths; `npm run check` passes. Commit: `7233e6b`.
- R7.3 [DONE] — package and test the extension from a clean checkout against
  the assembled CLI product. From a clean `git archive` checkout, system
  Gradle built `cplus-cli-0.1.0-SNAPSHOT-all.jar`; `npm ci` installed the
  lockfile, all six `npm test` tests passed (the actual JAR initialized LSP and
  ran a multi-module program), and `npm run check` passed. `npm run package`
  produced `cplus-language-support-0.2.0.vsix`; ZIP integrity and packaged
  manifest entrypoint `./dist/extension.js` were verified, with exactly eight
  expected extension files. `npm audit --omit=dev` reports zero production
  vulnerabilities; npm reports six high advisories in development-only tools.
  Native Windows VM `npm ci`, `npm test` (6/6), `npm run check`, and
  `npm run package` also pass; the generated VSIX contains the expected eight
  files, and `npm audit --omit=dev` reports zero production vulnerabilities.
  At the R7.3 checkpoint, the VM had no VS Code `code` executable, so launching
  the packaged VSIX in an actual Windows editor host was unverified. R7.4 below
  closes that gap. The
  repository wrapper JAR is absent from the clean export, so the installed
  Gradle command was used. Product commit: no source changes required; evidence
  recorded in the plan commit.
- R7.4 [DONE] — install the packaged VSIX into an isolated VS Code profile and
  verify it in the real Extension Development Host on Linux and Windows. The
  host fixture checks package activation, command registration, and a semantic
  `SEM302` diagnostic delivered by the configured CLI JAR for a workspace
  source file. `@vscode/test-electron` downloads a stable editor build on first
  use; the test uses a temporary workspace/profile and removes them afterward.
  Linux acceptance passes with VS Code 1.141.0 under `xvfb-run`; `npm test`,
  `npm run check`, VSIX packaging, and the production dependency audit pass.
  The native Windows run now passes too: latest harness files were copied into
  the isolated clean archive, the packaged extension activated, registered
  commands, delivered CLI diagnostics, and exited successfully. The host test
  used an isolated workspace/profile; the original Windows clone was left
  untouched.
  **Depends:** R7.2–R7.3.

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
hard-coded CLI path; the packaged VSIX is installed and exercised in the editor
host on both operating systems.

## R8 [DONE] [4/4] SDK packaging, target matrix and release conformance

**Dependency-ordered work queue**

- R8.1 [DONE] — make generated SDK metadata, header indexes, and package
  indexes deterministic; verify checked-in headers and syscall catalogues are
  packaged as source inputs, with runtime objects remaining optional. The
  generators now emit explicit LF newlines; SDK package indexing excludes
  cache path segments and its own output (including a custom output inside the
  SDK root). Repeat-generation tests compare exact bytes and CLI integration
  verifies an in-root package index is stable across consecutive runs.
  Focused compiler/CLI tests and `./gradlew build --no-daemon` pass. Commit:
  `0f4486a`. This accepts deterministic artifact handling, not a claim that
  syscall catalogues or runtime objects are newly generated by this stage;
  catalogues remain checked-in source form and runtime objects are optional.
- R8.2 [DONE] — replace optimistic status entries with executable evidence
  and explicit capability diagnostics. Audit of every `ConformanceCase`
  construction found the legacy `ConformanceMatrix.initial` marked Linux
  startup, ABI layout, and std.memory as `pass` from target identity/static
  descriptions only. These entries now remain `planned` until target-specific
  executable evidence exists; OS targets outside the declared platform set
  are `unsupported`. The regression test checks both outcomes. The C17 runner
  continues to report actual fixture compile/run, stream, and dependency-audit
  results. `./gradlew build --no-daemon` passes, including the focused matrix
  and Linux C17 executable-conformance tests. Commits: `34a0d5f`, `82172d8`.
- R8.3 [DONE] — run Linux x86_64/AArch64 and Windows x86_64 (plus available
  Windows AArch64/Darwin targets) product validation, including complete
  self-hosted Windows PE cross-linking. Linux x86_64 C17 executable
  conformance and freestanding runtime fixtures for file I/O/rename,
  TCP/UDP, independent thread TLS, contended sync/atomic wait-wake, libc
  families, and math classification pass. For Linux AArch64,
  Clang strict C17 syntax checks passed for every `sdk/runtime/src/*.c` file
  and Linux PAL C source; both AArch64 startup and thread assembly files also
  assemble. `LinuxAarch64SourceValidationTest` now repeats those warning-as-error
  source checks and assembly steps when Clang is installed. The check exposed
  and removed an unused private formatter helper
  that failed `-Werror`. The CLI/LinkDriver now discovers versioned LLD
  executables for Clang/Linux-AArch64 linking. The actual CLI built
  `examples/module_main.cp` as a static AArch64 ELF product; `readelf` reports
  ELF64/AArch64, `nm -u` is empty, no dynamic section is present, and there is
  no `PT_INTERP` program header. The latter check caught that `-nostdlib` alone
  still let Clang emit a dynamic-loader interpreter path; Linux self-hosted
  products now pass `-static`, and the redundant x86-specific `-no-pie` driver
  flag is omitted for AArch64. On x86_64, LinkDriver forwards `-no-pie` via
  Clang's linker-option form; the Clang-built Linux product links without the
  previous unused-option warning and executes successfully. Regression tests
  check both target link plans and the Clang command construction.
  `CliIntegrationTest` now repeats the Linux Clang x86_64 build/run/dependency
  audit and the Linux AArch64 cross-build checks for ELF machine, TLS, missing
  interpreter/dynamic section, and unresolved symbols when the required tools
  are installed. These cross-link assertions do not claim AArch64 execution.
  Its `PT_TLS` segment has a one-byte initialized image, eight-byte memory image,
  and four-byte alignment, matching the emitted TLS metadata symbols. This
  exposed that `--gc-sections` could remove an empty `.tdata` section required
  by the TLS linker script; Linux startup now retains a minimal initialized
  TLS anchor. Linux x86_64 TLS/C17 regression tests and a fresh module run
  (`Result: 12`, exit 0) pass after that change. AArch64 execution was enabled
  for this validation without a system install by unpacking the static
  `qemu-user-static` package under `/tmp`. The first C17 run exposed that
  Clang's `__atomic_*_n` builtins reject pointers to C `_Atomic` objects; the
  SDK `stdatomic.h` now selects Clang's `__c11_atomic_*` builtins while keeping
  the existing GCC builtin path. The AArch64 C17 run then reported 43 pass,
  0 fail, 5 unsupported; the initial unsupported context result was resolved
  in a later implementation update below. At that stage the basic and stdio
  fixtures executed successfully and passed dependency/stream checks, while
  complex types, tgmath, and complex arithmetic remained capability-gated.
  `ConformanceTest` conditionally repeats the AArch64 C17 run, and the CLI
  integration test now executes the built `examples/module_main.cp` product
  through QEMU and checks `Result: 12` when a user-mode runner is available.
  `RuntimeThreadPalTest` also cross-links and executes an AArch64 thread/TLS
  fixture under QEMU, verifying runtime attachment, independent initialized
  TLS, join results, parent TLS preservation, and a clean dependency audit.
  `RuntimeFilePalTest` executes an AArch64 filesystem roundtrip and dependency
  audit; it found and fixed the Linux AArch64 `renameat` syscall number, with
  both the runtime adapter and syscall catalogue corrected.
  `NativeStdTest` also executes the full foundational C+ core/memory/string/
  text/collection conformance fixture in a linked AArch64 product under QEMU
  with a passing dependency audit; the same fixture is used for the host
  sanitizer and self-hosted runtime checks.
  The AArch64 context gap is now implemented by `setjmp-aarch64.S`, which saves
  x19–x29, SP, LR, and the AAPCS64-preserved d8–d15 registers; the target
  runtime plan and C17 audit select this adapter, and `setjmp.h` declares the
  corresponding 21-word context only on Linux AArch64. The shared C17 context
  fixture now checks both a nonzero longjmp result and zero-to-one
  normalization. Clang assembles the adapter and QEMU executes the linked
  fixture with a clean dependency audit. A dedicated AArch64 ABI stress test
  additionally seeds x19–x29 and d8–d15 with distinct sentinels, overwrites
  them after `setjmp`, and verifies `longjmp` restores every value; its linked
  image also passes the dependency audit. The AArch64 C17 report now records 46
  pass, 0 fail, 3 unsupported; only complex arithmetic, complex types, and
  tgmath remain capability-gated. The shared context fixture is also compiled
  with `-O2` and executed for Linux x86_64 using each available GCC/Clang and
  for Linux AArch64 using Clang/QEMU; all optimized products pass dependency
  audits. `setjmp.h` now declares returns-twice and no-return compiler
  attributes, and the fixture uses a volatile saved return value per the C
  setjmp/longjmp rules. This Linux-only fixture does not claim Windows context
  support; native product validation is recorded below. The prior x86_64 CLI
  run also exposed a missing GNU-stack note in
  setjmp assembly, now fixed. At the point of this cross-build work Windows
  runtime testing was still pending; its subsequent native validation is
  recorded at the end of this R8.3 entry. A
  local MinGW x86_64 full-runtime CLI cross-build now emits a PE executable.
  The Windows runtime plan includes the GCC-compatible emulated-TLS adapter,
  implemented with per-thread FLS storage; its TLS helper links without
  `__emutls_get_address` remaining unresolved. Atomic wait/wake calls now
  resolve `WaitOnAddress`/wake functions dynamically, so older Windows
  releases do not impose those imports and report unsupported if unavailable.
  The resulting PE imports only `KERNEL32.dll`; the import table contains no
  UCRT/MSVCRT, `WaitOnAddress`/wake, `__emutls_get_address`, or `___chkstk_ms`
  dependency. The first full-link attempt exposed MinGW's `___chkstk_ms`
  dependency from a 256-entry automatic socket-address buffer; the resolver
  now allocates that bounded temporary buffer through the C+ runtime allocator.
  `RuntimeLinkerTest` verifies Windows TLS-adapter selection, and
  `CliIntegrationTest.windowsMinGWBuildProducesPeWithoutCrtOrOptionalAtomicImports`
  now repeats the target build and PE import audit when MinGW tools are
  installed. The complete `examples/minimal.cp` PE product links locally.
  The MinGW integration fixture also declares initialized C+ `thread_local`
  storage and executes the resulting PE under Wine 11.0 when Wine is present.
  The fixture passes, exercising process startup, emulated TLS initialization,
  TLS reads, and TLS writes. `WindowsAbiIntegrationTest` additionally checks
  LLP64 integer widths, the selected GNU x87 `long double` profile, and
  independent scalar/aggregate caller round trips under Wine. These are
  Windows GNU-ABI compatibility executions, not native Windows validation.
  The first Wine prefix attempt stalled while
  installing optional components; initializing an isolated prefix with
  `WINEDLLOVERRIDES=mscoree,mshtml=` succeeded. This is Wine compatibility
  execution evidence, not native Windows OS evidence. Native Windows
  validation then passed on the requested VM with UCRT64 GCC: the Windows ABI
  caller, full filesystem PAL and std.io fixtures, complete compiler suite,
  and complete Gradle build all succeeded. CLI no-extension output produced
  and ran the expected `.exe`; CLI build/audit and project-manifest integration
  cases passed natively. Windows output paths are normalized to `.exe` for
  `build` and `run`, while generated C remains `<stem>.c`. Together with Linux
  x86_64/AArch64 validation and PE import audits, this closes R8.3 for the
  available target matrix. Windows AArch64 and Darwin execution remain
  unavailable and are not claimed. The packaged fat JAR was also invoked
  natively as `java -jar ... run examples/module_main.cp`; it built a temporary
  Windows PE, printed `Result: 12`, and returned successfully through
  PowerShell.
- R8.4 [DONE] — verify no host contamination, reproducibility, clean-tree
  builds, documented examples, and upgrade/ABI compatibility rules. A fresh
  Git worktree initially exposed that `.gitignore` excluded the Gradle wrapper
  JAR and properties, making the checked-in wrapper scripts unusable from a
  clone. The ignore rules now preserve those two wrapper inputs. A clean
  worktree passed the full build using installed Gradle 9.2.1 and again with
  the repository wrapper pinned to Gradle 9.3.0. Two forced wrapper fat-JAR
  builds produced the identical SHA-256
  `4a137b490399b116827f6e5d07926ea9c280652a962e8ba6cdb10c2235bdfe68`.
  From that checkout, the packaged CLI checked `examples/minimal.cp` and ran
  `examples/module_main.cp`, printing `Result: 12` and returning the expected
  program exit status. The VS Code extension's six tests passed and
  `npm run package` produced the expected eight-file VSIX. README and
  GETTING_STARTED now point to `COMPATIBILITY.md`, which records the current
  SDK/language/runtime/PAL ABI versions, rebuild requirements, Windows GNU x87
  profile restriction, and unclaimed targets. `SPEC.COVERAGE.md` was aligned
  with the latest Linux and native Windows evidence; Linux/PE dependency audits
  and capability-gated unsupported targets are recorded under R8.2/R8.3. The
  R4 follow-up caught that SDK fixture files were read at test runtime but were
  not Gradle test inputs; every module's test task now fingerprints the SDK source tree
  (excluding caches/build output), and changing the shared C17 fixture reruns
  the Windows/Linux integration test instead of reporting `UP-TO-DATE`. The
  full Linux/Windows Gradle and extension verification passes. R8.4 closes the
  release audit without closing the separate R1, R4, R5.1, R6, or R7 gates.

**Deliverables**

- generate/rebuild semantic metadata, header indexes, package indexes, and
  optional runtime objects deterministically; preserve checked-in headers and
  syscall catalogues as portable source inputs unless a generation requirement
  is separately established;
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

## R9 [DONE] [4/4] Package CLI together with the source SDK

**Dependency-ordered work queue**

- R9.1 [DONE] — produce an installable C+ distribution containing the CLI
  launchers, JVM runtime dependencies, and full source SDK tree. Both
  `:cli:installDist` and `:cli:distZip` pass; archive inspection confirms the
  launcher, dependency JARs, and `sdk/manifest/sdk.toml` are present.
- R9.2 [DONE] — discover the SDK bundled beside the installed CLI by default,
  while allowing `-Dcplus.sdk.manifest=<path>` to override it for SDK
  development. `SdkManifestLocator` checks the CLI class code-source ancestry
  before the working-directory ancestry; integration coverage verifies the
  override takes precedence.
- R9.3 [DONE] — list Java-specific runtime options in CLI help and document
  their position before `-jar`. `cplus --help` names
  `-Dcplus.sdk.manifest=<path>`; README documents the same option and the
  installable distribution commands.
- R9.4 [DONE] — test an installed distribution from a working directory
  outside the installation. The installed launcher scaffolded a project in
  `/tmp`, then `cplus check --project cplus.toml` succeeded without an SDK
  override, reproducing the external-project use case. CLI tests and
  `git diff --check` pass.

**Gate**

The installable distribution runs from an unrelated project directory without
manual SDK configuration; an explicit JVM property selects a development SDK;
the CLI help identifies the JVM option.

## R10 [DOING] [18/19] Discoverable imports and editor fixes

**Language:** LS §21 Imports; §22 Importing C; §41.1 Import assistance.
**Technical:** TS §28 C import architecture; §54.1 Discoverable imports.

All R10.1.1 and R10.1.2.1–R10.1.2.3 leaves are accepted. The implementation runbook is
[IMPL.HANDOFF.IMPORTS-TRAITS.md](IMPL.HANDOFF.IMPORTS-TRAITS.md).
The existing R10/R11 IDs are retained as composites; only terminal children
count. File paths using `...` are expanded in the runbook's repository map.

### R10.1 [DONE] [10/10] Shared discovery foundations

**Language:** LS §21 Imports; §22 Importing C.
**Technical:** TS §27 Import resolver; §28 C import architecture; §47 Incremental compilation model.

#### R10.1.1 [DONE] [7/7] Source-driven C declarations

**Language:** LS §22.1 C header import; §22.2 Foreign symbols; §22.3 Unsupported C preprocessor constructs.
**Technical:** TS §28 C import architecture; §45 Compiler context.

##### R10.1.1.1 [DONE] Thread one immutable header environment through compiler entry points

**Language:** LS §22.1 C header import; §43.2 ABI identity and platform contracts.
**Technical:** TS §28 C import architecture; §45 Compiler context.

**Depends:** R9.
**Files:** `compiler/.../Compiler.kt`, `SdkResolver.kt`, `CCompilerToolchain.kt`; `cli/.../Main.kt`.

**Deliverable:** An immutable request-owned environment carrying selected SDK, target/ABI/profile, ordered include roots, external sysroot, and selected C driver. Preserve SDK override/distribution discovery. Wire full, incremental, text-workspace, and provisional semantic paths without a semantic→compiler dependency.

**Acceptance:** `HeaderEnvironmentTest` verifies the direct and incremental pipelines, text-workspace provisional/final analyzers, independent targets/drivers, normalized include order and external sysroot. The CLI passes its selected C driver into `CompileRequest`; request configuration/cache identity includes it. `./gradlew :compiler:test --tests cplus.compiler.HeaderEnvironmentTest` and `./gradlew :cli:compileKotlin` pass. No header whitelist was removed in this leaf.

**Verify:** `gradle :compiler:test :cli:test` (environment/SDK/request tests).

**Evidence:** Linux, 2026-10-08 — focused `HeaderEnvironmentTest` passes (3/3);
`:cli:compileKotlin` passes. An initial test fixture lacked `pub` on its
cross-module helper; corrected fixture passes. No Windows-specific behavior
was exercised or claimed.

##### R10.1.1.2 [DONE] Resolve and preprocess real headers with target-aware provenance

**Language:** LS §22.1 C header import; §22.3 Unsupported C preprocessor constructs; §28 Source provenance.
**Technical:** TS §28 C import architecture; §44 External C compiler diagnostics.

**Depends:** R10.1.1.1.
**Files:** New `compiler/.../CHeaderDiscovery.kt` and `CHeaderPreprocessor.kt`; existing `CCompilerToolchain.kt` and runtime compile-flag construction.

**Deliverable:** Resolve `c.vendor.api`/`c/vendor/api` to `vendor/api.h`, using the effective compile include order. Adapt the selected compiler's preprocessor; retain original file/line mapping, active macro information, and transitive include dependencies. Bound duration/output and use argument lists, not a shell.

**Acceptance:** Isolated fixtures cover nested headers, include guards, inactive declarations, conflicting search roots, target defines, slash paths and paths with spaces. Missing header/toolchain, timeout, and unsupported profile are distinguishable diagnostics. Self-hosted discovery never silently uses host libc. Unit-test every driver adapter; execute available local drivers, leaving native Windows evidence to R10.3.2.3.

**Verify:** `gradle :compiler:test` (new discovery/preprocessor fixtures).

**Evidence:** Linux, 2026-10-08 — `CHeaderDiscoveryTest` and
`HeaderEnvironmentTest` pass. Real preprocessing ran through each locally
available GCC, Clang, and TCC driver; fixtures verify nested include provenance,
active/inactive conditional branches, macro origin, paths with spaces, ordered
conflicting include roots, unsupported drivers, missing headers, output limits,
and timeout diagnostics. MSVC/clang-cl command adapters are covered without
claiming native execution; native Windows execution remains assigned to
R10.3.2.3.

##### R10.1.1.3 [DONE] [4/4] Parse supported C declarations into structured foreign records

**Language:** LS §22.2 Foreign symbols; §22.3 Unsupported C preprocessor constructs; §43.4 C-compatible type qualifiers and declarators.
**Technical:** TS §28 C import architecture; §30 Reference index.

###### R10.1.1.3.1 [DONE] Read function declarators and skip bodies structurally

**Language:** LS §22.2 Foreign symbols; §43.4 C-compatible type qualifiers and declarators.
**Technical:** TS §28 C import architecture; §30 Reference index.

**Depends:** R10.1.1.2.
**Files:** `semantic/.../CHeaderImport.kt`; shared `language-core` token/type helpers; new foreign-declaration tests.

**Deliverable:** Structured foreign records with provider, origin, linkage and C declarators. Parse prototypes and header definitions, including multiword/qualified types, named/unnamed parameters, nested callback parameters and variadics. Skip bodies structurally, not with a brace regex.

**Acceptance:** Focused fixtures cover multiline signatures, callback nesting, static-inline bodies, comments/strings containing braces and body-local declarations. Only top-level functions are exported; types and source ranges match. Unsupported declarators are recorded explicitly, never flattened into guessed signatures.

**Verify:** `gradle :language-core:test :semantic:test` (function declaration fixtures).

**Evidence:** Linux, 2026-10-08 — `CHeaderFunctionScannerTest` and
`CHeaderImportMathTest` pass, and the full `./gradlew :semantic:test` suite
passes. Function declarations are token-scanned across nested callback
parameters; multiline and multiword types, variadics, and source offsets are
retained. Function bodies are skipped by balanced lexical braces, with fixture
coverage for nested blocks, braces in comments/strings, and body-local function
declarations. No unsupported declarator is guessed as a supported signature.

###### R10.1.1.3.2 [DONE] Resolve typedef and aggregate declaration dependencies

**Language:** LS §22.2 Foreign symbols; §43.1 ABI and layout queries; §43.4 C-compatible type qualifiers and declarators.
**Technical:** TS §12 Type system model; §28 C import architecture.

**Depends:** R10.1.1.3.1.
**Files:** `semantic/.../CHeaderImport.kt`; foreign type resolution helpers and semantic tests.

**Deliverable:** Read typedefs, forward declarations, struct/union/enum declarations and typedef dependency edges. Reuse canonical qualifier/declarator and target-layout representations; do not maintain independent host-size tables.

**Acceptance:** Fixtures cover chained typedefs, tagged/anonymous aggregates, pointer and callback typedefs, enum values, opaque handles and cycles. Selected target layout is respected; incomplete types are usable only in allowed contexts. Unsupported layout forms receive reference diagnostics rather than guessed sizes.

**Verify:** `gradle :semantic:test :compiler:test` (foreign type and ABI regression fixtures).

**Evidence:** Linux, 2026-10-08 — the full `./gradlew :semantic:test` and
`./gradlew :compiler:test` suites pass. Fixtures cover chained typedefs,
tagged/anonymous struct and union declarations, callback pointer typedefs,
enum values, self-referential pointers, forward/opaque types, and cyclic
typedef diagnostics. `AbiLayoutTest` uses selected ABI descriptors to verify
the same C struct as 24 bytes on Linux LP64 and 16 bytes on Windows LLP64.
By-value use of incomplete tags and compiler-specific bit-fields are rejected
rather than assigned guessed layouts. Header source offsets remain stable
after preprocessor-line masking.

###### R10.1.1.3.3 [DONE] Index globals and safely representable header constants

**Language:** LS §22.2 Foreign symbols; §22.3 Unsupported C preprocessor constructs.
**Technical:** TS §28 C import architecture; §54.1 Discoverable imports.

**Depends:** R10.1.1.3.2.
**Files:** `semantic/.../CHeaderImport.kt`; preprocessor macro records from `compiler/.../CHeaderPreprocessor.kt`; semantic constant adapters.

**Deliverable:** Read top-level globals and enum/object-like constants using active preprocessor records and target-aware constant typing. Keep unsupported function-like macros opaque except for explicitly compiler-owned intrinsic handling.

**Acceptance:** Tests cover extern globals, pointer qualifiers, enum arithmetic, simple typed numeric/string constants, inactive/redefined macros and function-body locals. Safe constants retain origins/types; unsupported macro expansion referenced by C+ diagnoses instead of pretending to be a variadic function. Existing SDK EOF/SEEK and varargs behavior is checked explicitly.

**Verify:** `gradle :semantic:test :compiler:test` (global/constant/macro fixtures).

**Evidence:** Linux, 2026-10-08 — full `./gradlew :semantic:test` and
`./gradlew :compiler:test` pass. Token-aware top-level scanning indexes extern
globals and excludes static header state and function-body locals. C preprocessor
events retain final active object-like definitions across `#undef` and
redefinition, and ignore inactive branches; zero-argument function-like macros
remain distinct from object-like constants. Safe numeric, floating, character,
and string literals receive conservative types and retain their replacement,
source path, and line through semantic symbols. Enum constant expressions are
retained. Function-like macros and expression macros are not exposed as callable
symbols or guessed constants. Existing EOF/SEEK static import coverage remains
passing.

###### R10.1.1.3.4 [DONE] Audit declaration coverage against the delivered SDK headers

**Language:** LS §22.1 C header import; §22.3 Unsupported C preprocessor constructs; §28 Source provenance.
**Technical:** TS §28 C import architecture; §63 Testing architecture.

**Depends:** R10.1.1.3.3.
**Files:** New semantic/compiler SDK-header corpus tests; `semantic/.../CHeaderImport.kt`; selected SDK fixtures (do not edit user headers).

**Deliverable:** A reproducible supported/opaque/unsupported declaration inventory for each currently delivered standard header, with original-source locations and explicit reasons for rejected forms.

**Acceptance:** Existing required stdio/math/complex/stdint/stdarg declarations are representable or have the already specified capability diagnostic; new parser limitations cannot silently regress supported imports. Selected-header truth replaces old catalogue expectations. Test a custom header definition such as `coucou` in a temporary SDK and prove body locals are absent; no Kotlin name addition. New unsupported required forms keep this leaf open until supported.

**Verify:** `gradle :semantic:test :compiler:test` (SDK corpus and source-range fixtures).

**Evidence:** Linux, 2026-10-08 — `./gradlew :semantic:test` and
`./gradlew :compiler:test --tests cplus.compiler.CHeaderSdkCorpusTest` pass.
The corpus test preprocesses every delivered SDK `.h` file with the selected
target C driver and emits supported/unsupported declaration and opaque-macro
counts, while the required `c.stdio`, `c.math`, `c.complex`, `c.stdint`, and
`c.stdarg` modules each pass semantic analysis with zero errors. A temporary
`demo/coucou.h` is discovered without a Kotlin catalogue entry; its function
retains the original header path/line and its body-local variable is excluded.
The selected ABI's `__builtin_va_list` is represented using the SDK's existing
opaque pointer contract. This evidence is host-Linux only; native Windows
header-corpus evidence remains at R10.3.2.3.

##### R10.1.1.4 [DONE] Bind discovered symbols and retire function whitelists

**Language:** LS §22.1 C header import; §22.4 C source import; §27 Include and dependency generation.
**Technical:** TS §28 C import architecture; §40 Dependency collector.

**Depends:** R10.1.1.3.
**Files:** `semantic/.../Semantics.kt`, `CHeaderImport.kt`; `compiler/.../Compiler.kt`; `c-backend/.../CDependencyCollector.kt`.

**Deliverable:** Supply discovered records to semantic analysis, remove the default function inventory and name-specific library-function exceptions (including `printf`), and derive generated include dependencies from provider metadata. Keep genuinely compiler-owned ABI/intrinsic mappings explicitly separate. Preserve separately linked C source/library ownership.

**Acceptance:** Add/rename/remove a fixture header function and import it without editing Kotlin. Real `printf` fixed arguments plus variadics are checked. Header definitions are not copied into generated C; an external C source is linked once. A binary-only library cannot manufacture signatures. Missing declarations and missing link definitions fail at the correct stage. Run existing C math/complex/stdint/stdio/compiler regressions; document any unsupported SDK declaration rather than restoring a whitelist.

**Verify:** `gradle :semantic:test :c-backend:test :compiler:test :cli:test` plus a native custom-header compile/run fixture.

**Evidence:** Linux, 2026-10-08 —
`./gradlew :semantic:test :c-backend:test :compiler:test :cli:test` passes.
Normal compilation, incremental single-source, text-workspace, and multi-module
paths preprocess imported `c.*` providers through the request's selected
header environment and pass typed declaration records into semantic analysis.
The default Kotlin C-header declaration table and `printf` special case are
removed; signatures and macro constants come from active header declarations.
`CDependencyCollector` derives include paths from logical provider paths rather
than a list of named modules. SDK headers declare standard `abs`/`labs`/`llabs`
and `SEEK_*` constants that were previously supplied only by the compiler
catalogue. `FILE` self-tag typedefs remain opaque and canonical type traversal
is cycle-safe.

A temporary `c.demo.coucou` header is imported, compiled, and executed without
Kotlin symbol registration. Its body remains in the header and generated C
contains the provider include; an absent header member reports `SEM408`. The
`printf` declaration retains its fixed `char*` parameter and variadic tail.
Existing C-source/link, math, complex, stdint, stdio, backend, and CLI tests pass.
This is Linux-only evidence; native Windows execution remains in R10.3.2.3.

#### R10.1.2 [DONE] [3/3] Shared module resolver and export index

**Language:** LS §21 Imports; §41.1 Import assistance.
**Technical:** TS §27 Import resolver; §47 Incremental compilation model; §54.1 Discoverable imports.

##### R10.1.2.1 [DONE] Unify CLI and LSP source graph discovery

**Language:** LS §21.1 Import declaration; §21.2 Import semantics; §21.5 Cyclic imports.
**Technical:** TS §27 Import resolver; §51 Workspace model.

**Depends:** R10.1.1.1.
**Files:** New `compiler/.../ModuleSourceResolver.kt`; extract relevant `cli/.../Main.kt` project/discovery code; `LspServer.kt` and `LspWorkspace.kt`.

**Deliverable:** One resolver for entry/import closures and a separate bounded list of candidate modules. Honor project/workspace roots, selected SDK `std.*`, relative/logical imports, source overlays, and canonical module identity. Read LSP initialization roots instead of searching only the requesting file's directory.

**Acceptance:** CLI and LSP resolve the same stdlib and source-path providers from a project outside the installation. Unsaved imported modules take precedence over disk. Cycles terminate, same-basename modules stay distinct, and two unrelated files containing `main` are never compiled together merely to index them.

**Verify:** `gradle :compiler:test :cli:test` (resolver and JSON-RPC diagnostics fixtures).

**Evidence:** Linux, 2026-10-08 — `./gradlew :compiler:test :cli:test` passes.
`ModuleSourceResolverTest` verifies project-root logical imports, selected SDK
`std.*` resolution outside the installation, overlay precedence, cycle
termination, same-basename module identity, and bounded candidate discovery.
CLI/LSP integration verifies initialized workspace roots, an unsaved imported
module overlay, and exclusion of an unrelated `main` from the compile closure.
No native Windows execution is claimed; platform acceptance remains assigned
to R10.3.2.3.

##### R10.1.2.2 [DONE] Build a shared typed export inventory

**Language:** LS §21.2 Import semantics; §22.2 Foreign symbols; §41.1 Import assistance.
**Technical:** TS §30 Reference index; §54.1 Discoverable imports.

**Depends:** R10.1.1.4, R10.1.2.1.
**Files:** New `compiler/.../ImportIndex.kt` and tests; existing `CHeaderImport.kt`, `CHeaderPreprocessor.kt`, and `ModuleSourceResolver.kt`.

**Deliverable:** Immutable entries containing stable symbol identity, name/kind/signature/docs, provider/import reference, visibility, source URI/range, and configuration fingerprint. Discover SDK/project source exports through the shared parser and C exports by enumerating configured headers and running the selected target-aware preprocessor/declaration parser. Separate candidate inventory from imported lexical bindings.

**Acceptance:** Public functions, values, enum constants, structures and typedefs appear with correct providers; private declarations do not. Ambiguous providers remain distinct; discovering a symbol never makes it visible without an import. Invalid/unsupported C declarations are not offered as callable functions. CPX-generated exports use validated expansion metadata, not regex guesses or executing arbitrary bodies during a scan.

**Verify:** `gradle :compiler:test :cli:test` (new import-index fixtures).

**Evidence:** Linux, 2026-10-08 — `./gradlew :compiler:test :cli:test` passes.
`ImportIndexTest` verifies public functions, values, enum constants, structures,
fields and typedefs; private declarations are omitted. Provider identities
preserve same-named exports from different modules. SDK `std.*` files and
overlays are indexed without installing or writing into the SDK. Configured C
header roots are enumerated rather than names hard-coded in CLI logic; a real
`stdio.h` declaration (`printf`) is discovered with its header URI/range and
signature. CPX-generated exports are accepted only from a validated expansion
whose source fingerprint matches; scans never execute CPX invocations. The
inventory is a candidate catalogue and does not change semantic bindings.
Native Windows evidence remains assigned to R10.3.2.3.

##### R10.1.2.3 [DONE] Invalidate discovery and compilation consistently

**Language:** LS §39 Determinism; §41.1 Import assistance.
**Technical:** TS §47 Incremental compilation model; §54.1 Discoverable imports; §62 Caching.

**Depends:** R10.1.2.2.
**Files:** `compiler/.../ImportIndex.kt`, `IncrementalCompiler.kt`, `SdkMetadata.kt`; `cli/.../LspServer.kt`, `LspWorkspace.kt`.

**Deliverable:** Content/configuration-keyed cache entries, dependency invalidation, live-buffer overlays and cancellation. Include compiler identity, target/profile, SDK, ordered roots and transitive header hashes. Bound scans and exclude generated/VCS trees; do not require writing into a read-only installed SDK.

**Acceptance:** Editing/removing a header declaration, editing a nested include, changing SDK/target/root order, and changing an unsaved C+ export each refresh both suggestions and semantic diagnostics. Identical requests reuse work; cancelled/stale requests cannot publish older results. Missing C preprocessing leaves C+ completion usable, with an explicit C diagnostic.

**Verify:** `gradle :compiler:test :cli:test` (incremental and workspace invalidation fixtures).

**Evidence:** `./gradlew :compiler:test :cli:test --no-daemon` passes on Linux. Focused
fixtures verify identical-index cache reuse, source/overlay and nested-header invalidation,
watched-file diagnostic refresh, request cancellation, and stale document-version
suppression. Header dependency content is hashed and cache size is bounded.

### R10.2 [DONE] [4/4] Import and auto-import completion

**Language:** LS §41.1 Import assistance.
**Technical:** TS §54 Completion; §54.1 Discoverable imports.

#### R10.2.1 [DONE] [2/2] Provider and selective-name completion

**Language:** LS §21.1 Import declaration; §41.1 Import assistance.
**Technical:** TS §54.1 Discoverable imports.

##### R10.2.1.1 [DONE] Identify incomplete import contexts from shared tokens

**Language:** LS §21.1 Import declaration; §41.1 Import assistance.
**Technical:** TS §5 Lexer; §54 Completion.

**Depends:** R10.1.2.3.
**Files:** `cli/.../LspLanguageService.kt`; shared `language-core` lexer/parser APIs.

**Deliverable:** Import context and replacement-range classification for module references and selective names, including incomplete braces/quotes and alias positions, without a second language parser.

**Acceptance:** Table tests cover dotted/slash std and C providers, quoted/relative paths, multiline imports, incomplete source, UTF-16 positions, and `as` aliases. Comments and ordinary string contents do not trigger import completion. No complete semantic model is required to complete an unfinished import.

**Verify:** `gradle :cli:test` (new context/range tests).

**Evidence:** `./gradlew :cli:test --tests cplus.cli.ImportCompletionContextTest --no-daemon`
passes. Table fixtures cover dotted/slash providers, relative and quoted paths,
multiline/incomplete selective imports, `as` alias positions, comments, ordinary
strings, replacement spans and a non-BMP UTF-16 cursor position.

##### R10.2.1.2 [DONE] Serialize provider and export completion through JSON-RPC

**Language:** LS §41.1 Import assistance.
**Technical:** TS §50 LSP architecture; §54.1 Discoverable imports.

**Depends:** R10.2.1.1.
**Files:** `cli/.../LspLanguageService.kt`, `LspServer.kt`; `CliIntegrationTest.kt`.

**Deliverable:** Completion items with safe replacement edits, symbol kinds, signatures, provider detail and documentation from the shared index; advertise appropriate triggers and retain ordinary member completion.

**Acceptance:** Framed JSON-RPC tests complete `std.`/`c.`/relative module references and names inside `import { ... } from ...`. Results include real SDK symbols such as `std_fs_open` from `std.fs`, not invented friendly names. Applying the completion creates the expected import text; private and duplicate imported names are excluded.

**Verify:** `gradle :cli:test` (protocol completion fixtures).

**Evidence:** `./gradlew :cli:test --no-daemon` passes. A framed protocol fixture
returns real `std.fs` and `c.stdio` providers, `std_fs_open` and `printf` export
items with signature/provider detail and replacement edits, excludes a name
already selected in the import, and completes `./helper.cp` from an unsaved
workspace document. Ordinary semantic completion remains covered by the suite.

#### R10.2.2 [DONE] [2/2] Safe auto-import edits

**Language:** LS §21.3 Aliased import; §21.4 Selective import; §41.1 Import assistance.
**Technical:** TS §54.1 Discoverable imports.

##### R10.2.2.1 [DONE] Implement one import-edit builder

**Language:** LS §21.3 Aliased import; §21.4 Selective import; §41.1 Import assistance.
**Technical:** TS §51 Workspace model; §54.1 Discoverable imports.

**Depends:** R10.2.1.2.
**Files:** New `cli/.../ImportEdits.kt`; shared syntax/import records; focused edit tests.

**Deliverable:** A pure edit builder reused by completion and quick fixes. Prefer merging a compatible selective import; otherwise insert after package/existing imports without moving comments. Reuse aliases/qualified bindings, reject collisions, preserve line endings/BOM, and return non-overlapping version-appropriate edits.

**Acceptance:** Apply edits in tests covering empty files, leading comments, package declarations, CRLF, multiline/trailing-comment imports, existing aliases/module imports, name conflicts, and non-BMP characters. Reapplication is idempotent; formatting/parse ambiguity yields no unsafe edit. Symbol replacement edits never overlap import edits.

**Verify:** `gradle :cli:test` (new `ImportEditsTest`).

**Evidence:** `./gradlew :cli:test --no-daemon` passes. `ImportEditsTest` applies
edits for empty/package files, leading comments, CRLF, BOM and non-BMP text;
checks same-provider merging and idempotence; reuses selective/module aliases;
rejects collisions/malformed imports; and uses a separate import for multiline
or commented lists and quoted paths containing spaces.

##### R10.2.2.2 [DONE] Offer unimported symbols with additional import edits

**Language:** LS §41.1 Import assistance.
**Technical:** TS §54 Completion; §54.1 Discoverable imports.

**Depends:** R10.2.2.1.
**Files:** `cli/.../LspLanguageService.kt`, `LspServer.kt`; import-index and edit-builder APIs.

**Deliverable:** Context-compatible unimported identifier completion with provider labels, replacement text and `additionalTextEdits`. Existing visible symbols retain priority; receiver-member contexts stay separate.

**Acceptance:** Apply a stdlib function completion and a public type completion, then check the edited program successfully. Two providers yield two choices. Aliases do not create duplicate imports; missing members, comments and strings do not receive unrelated global auto-imports. Protocol tests inspect actual serialized edits.

**Verify:** `gradle :cli:test` plus compiler checks of the edited fixtures.

**Evidence:** `./gradlew :compiler:test :cli:test --no-daemon` passes. Protocol
tests exercise real stdlib/C providers, public function and type exports,
competing providers, duplicate-import suppression, and serialized primary plus
additional edits. `ImportEditsTest.mergedFunctionAndTypeImportsPassTheCompiler`
applies merged `std.fs` edits and verifies `cplus check` accepts the result.
Identifier-context tests prevent auto-imports in comments, strings and member
access; export metadata marks aggregate members as non-top-level bindings.

### R10.3 [TODO] [0/5] Quick fixes and product acceptance

**Language:** LS §41.1 Import assistance; §43 C interoperability.
**Technical:** TS §50 LSP architecture; §54.1 Discoverable imports; §63 Testing architecture.

#### R10.3.1 [DONE] [2/2] Import code actions

**Language:** LS §41.1 Import assistance.
**Technical:** TS §50 LSP architecture; §54.1 Discoverable imports.

##### R10.3.1.1 [DONE] Map unresolved-symbol diagnostics to compatible providers

**Language:** LS §21.2 Import semantics; §41.1 Import assistance.
**Technical:** TS §46 Diagnostic system; §54.1 Discoverable imports.

**Depends:** R10.2.2.2.
**Files:** `cli/.../LspLanguageService.kt`; semantic diagnostic/range information; `ImportEdits.kt`.

**Deliverable:** Provider-specific quick-fix candidates for unresolved type/value/callable names at their exact source token, reusing the index and edit builder. Preserve ambiguity as explicit choices.

**Acceptance:** Fixtures cover unresolved calls/types/constants, two providers, aliases, private exports, already resolved symbols, wrong arity/type errors and malformed syntax. Only compatible unresolved-name cases receive fixes; edits preserve bindings and remove the targeted diagnostic after reanalysis.

**Verify:** `gradle :cli:test` (quick-fix logic and apply/recheck fixtures).

**Evidence:** `./gradlew :cli:test --tests cplus.cli.ImportQuickFixTest --no-daemon`
passes. Fixtures map unresolved function/type/value names, retain ambiguous
providers as choices, exclude private exports and SEM303/SEM306 known-call
errors, suppress fixes on malformed source, and apply a callable import edit
that subsequently passes `cplus check`.

##### R10.3.1.2 [DONE] Expose import quick fixes through the LSP server

**Language:** LS §41.1 Import assistance.
**Technical:** TS §50 LSP architecture; §51 Workspace model.

**Depends:** R10.3.1.1.
**Files:** `cli/.../LspServer.kt`, `CliIntegrationTest.kt`; existing language-client integration.

**Deliverable:** Advertise code-action capability and handle `textDocument/codeAction`, request ranges/diagnostics/kind filtering, cancellation and version-safe workspace edits. VS Code remains a thin consumer of server-provided actions.

**Acceptance:** Framed JSON-RPC tests open a failing document, request a fix, apply its edit, send change notification, and observe the unresolved diagnostic disappear. No duplicate action/import on repetition; stale or unsupported requests do not crash the server.

**Verify:** `gradle :cli:test` (end-to-end protocol fixture).

**Evidence:** `./gradlew :cli:test --tests cplus.cli.CliIntegrationTest.lspExposesVersionedImportQuickFixesAndClearsDiagnosticAfterApply --no-daemon` passes. Framed JSON-RPC confirms initialization advertises `quickfix`, a non-overlapping requested range yields no action, and an overlapping unresolved call yields a provider-specific action with a versioned `documentChanges` edit and matching diagnostic. Opening the edited source in a fresh protocol session yields no duplicate fix; the import edit is separately applied and checked by the R10.3.1.1 compiler fixture. Request scheduling rejects stale versions.

#### R10.3.2 [DOING] [2/3] Import discovery release gate

**Language:** LS §22.1 C header import; §41.1 Import assistance.
**Technical:** TS §54.1 Discoverable imports; §63 Testing architecture; §78 SDK, ABI, runtime and platform architecture.

##### R10.3.2.1 [DONE] Verify installed CLI and LSP import parity

**Language:** LS §21 Imports; §22.1 C header import; §41.1 Import assistance.
**Technical:** TS §54.1 Discoverable imports; §57 CLI architecture.

**Depends:** R10.3.1.2.
**Files:** `cli/.../CliIntegrationTest.kt`; compiler integration tests; installDist test fixtures.

**Deliverable:** Outside-repository acceptance fixtures using the installed SDK and an explicit development-SDK override, custom include root, source library, and source-path module with an aliased type.

**Acceptance:** The same edited source checks/runs through CLI and receives equivalent LSP diagnostics. SDK header edits appear without rebuilding Kotlin. Compare generated includes/linking and real header definition URIs. Exercise the fat JAR with its existing supported SDK-selection behavior; do not assume fatJar embeds the source SDK.

**Verify:** `gradle :cli:test :cli:fatJar :cli:installDist`; installed-launcher native run and LSP protocol fixture.

**Evidence:** `./gradlew :cli:test :cli:fatJar :cli:installDist --no-daemon` passes; `./gradlew :compiler:test --tests cplus.compiler.ImportIndexTest --no-daemon` passes, including header dependency fingerprint invalidation. A CLI protocol fixture confirms configured `includeDirectories` reach both compiler header discovery and export indexing, aliased path-imported types compile/run, custom-header declarations resolve, and go-to-definition returns the real header URI. From `/tmp`, the installed launcher resolves the bundled SDK, while the fat JAR resolves the explicit `-Dcplus.sdk.manifest=...` development override; both check the same path-imported module example. The installed launcher's LSP process advertises code actions, publishes clean diagnostics for the imported example and returns an empty action list for its clean document. Linux only; VS Code host and Windows gates remain open.

##### R10.3.2.2 [DONE] Exercise suggestions and fixes in the packaged VS Code extension

**Language:** LS §41.1 Import assistance; §42 TextMate and editor lexical highlighting.
**Technical:** TS §50 LSP architecture; §75 Architectural rule for IDE support.

**Depends:** R10.3.2.1.
**Files:** `vscode-extension/extension.js`, `test/extension.test.js`, `test/extension-host.js`, `test/run-extension-host.js`; settings only if required.

**Deliverable:** Real extension-host tests invoking completion and code-action providers against the configured CLI JAR, applying edits and checking diagnostics. Preserve configured Java/SDK/JAR/cwd and Run Main behavior; do not add an extension-side catalogue.

**Acceptance:** Packaged VSIX exposes stdlib provider/signature choices and an import quick fix; accepting each yields clean diagnostics and a runnable fixture. Existing extension tests, checks and packaging pass; an old activation-only test is not sufficient evidence.

**Verify:** In `vscode-extension`: `npm test`, `npm run check`, `xvfb-run -a npm run test:host` on Linux.

**Evidence:** In `vscode-extension`, `npm test` passes (7 tests), `npm run check` passes, and `xvfb-run -a npm run test:host` passes against the packaged/installed VSIX and configured fat JAR. The real extension host requests an indexed public function completion with an import edit, applies it, retrieves the second function's quick fix from the LSP, applies the versioned workspace edit, and observes all diagnostics clear. Existing configured JAR/Java/SDK/cwd and Run Main tests remain green. Linux only; final Windows packaging/product gate remains R10.3.2.3.

##### R10.3.2.3 [TODO] Close import regression, documentation and platform evidence

**Language:** LS §22 Importing C; §41.1 Import assistance.
**Technical:** TS §54.1 Discoverable imports; §63 Testing architecture; §78 SDK, ABI, runtime and platform architecture.

**Depends:** R10.3.2.2.
**Files:** `README.md`, `SPEC.COVERAGE.md`, `IMPL.PLAN.md`; relevant compiler/CLI/VSIX fixtures.

**Deliverable:** User examples for discovery, explicit include/library configuration, C-driver preprocessing requirements, supported/unsupported forms and SDK overrides. Record actual full-suite/product evidence and any driver/target gaps.

**Acceptance:** Local full Gradle and extension suites pass, followed by native Windows x86_64 checks for new preprocessing paths, slash/space paths, installed CLI and packaged import actions. If Windows or a required driver is unavailable, record that limitation and leave this leaf/phase open; historical R0–R9 evidence does not count for changed paths. Arbitrary binary ABI inference and exhaustive C/C++ preprocessing support are not claimed.

**Verify:** `gradle test :cli:fatJar :cli:installDist`; extension checks/host tests; native Windows equivalents at the final platform pass.

## R11 [DOING] [10/12] Compile-time extension methods

**Language:** LS §6.3.1 Compile-time extension methods; §21 Imports.
**Technical:** TS §13.1 Compile-time traits; §33 Method lowering; §54 Completion.

### R11.1 [DONE] [6/6] Trait declarations and semantic ownership

**Language:** LS §6.3.1 Compile-time extension methods; §18 Type-universe barrier.
**Technical:** TS §13.1 Compile-time traits; §24 Type-universe stabilization; §29 Semantic model.

#### R11.1.1 [DONE] [3/3] Explicit trait syntax and pass traversal

**Language:** LS §6.3.1 Compile-time extension methods; §28 Source provenance.
**Technical:** TS §6 Parser; §8 AST architecture; §13.1 Compile-time traits.

##### R11.1.1.1 [DONE] Parse the exact singular trait syntax

**Language:** LS §6.3.1 Compile-time extension methods.
**Technical:** TS §6 Parser; §7 Syntax tree versus AST; §13.1 Compile-time traits.

**Depends:** R10.1.2.3.
**Files:** `language-core/.../Parser.kt`, `Syntax.kt`, `Ast.kt`, `AstBuilder.kt`; `AstGoldenTest.kt`; explicit declaration consumers in semantic/comptime/CLI.

**Deliverable:** Explicit `SyntaxTrait`/`AstTrait` with target identifier, visibility, methods and origins. Dispatch `comptime trait` before `comptime cpx`; reuse method/receiver parsing. Keep consumer dispatch exhaustive and explicitly reject not-yet-supported trait processing rather than silently dropping blocks.

**Acceptance:** Parse `comptime trait counter_t`, `pub comptime trait int`, `self` and `self*`, with accurate nested ranges. Reject plural/angle-bracket syntax, missing/repeated receiver, fields, static methods, nested blocks and missing bodies with recovery at the next declaration. Existing CPX syntax remains green.

**Verify:** `gradle :language-core:test` and compile affected dependent modules to catch exhaustive `when` sites.

**Evidence:** `./gradlew :language-core:test :comptime:test --no-daemon` passes, as do `./gradlew :compiler:compileKotlin :cli:compileKotlin --no-daemon` and the focused compiler test `./gradlew :compiler:test --tests cplus.compiler.CompilerIntegrationTest.traitDeclarationIsRegisteredBeforeTheBackendTraitLoweringStage --no-daemon`. Parser tests cover private/public blocks, primitive and nominal target spelling, `self`/`self*`, source ranges, plural and angle-bracket rejection, missing/repeated receiver, fields, static methods, nested blocks, missing bodies, and recovery to the following declaration. At the parser leaf the analyzer explicitly rejected the new node; R11.1.2.2 now replaces that temporary rejection with semantic target/method registration. C lowering remains a later R11.2 stage.

##### R11.1.1.2 [DONE] Traverse trait bodies in ordinary compiler passes

**Language:** LS §6.3.1 Compile-time extension methods; §14 Inner functions and lexical capture; §15 Deferred execution; §16 String templates; §28 Source provenance.
**Technical:** TS §13.1 Compile-time traits; §32 AST rewrite framework; §34 Inner-function lowering.

**Depends:** R11.1.1.1.
**Files:** `compiler/.../AstRewrite.kt`, `ClosureLowering.kt`, `SdkMetadata.kt`; `cli/.../Main.kt` (`AstPrinter`); all relevant declaration visitors found with `rg`.

**Deliverable:** Recursive visits for trait methods across rewrite/closure/string/defer preparation, printing, source dependency and metadata collection. Preserve grouping and origins until the designated lowering; enumerate all sealed syntax/AST consumers.

**Acceptance:** Visitor unit tests reach nested method bodies and retain target/origins. Trait blocks round-trip in inspection output; methods are not mistaken for free exported functions or new types. No `else -> ignore` hole hides the new declaration; existing ordinary method regressions remain green.

**Verify:** `gradle :compiler:test :cli:test` (visitor/rewrite/metadata tests).

**Evidence:** `./gradlew :compiler:test --tests cplus.compiler.ClosureLoweringTest --tests cplus.compiler.SdkMetadataTraitTest :cli:test --tests cplus.cli.CliIntegrationTest.astInspectionKeepsTraitMethodsGroupedAndPrintsTheirBodies :semantic:test --tests cplus.semantic.ModuleTypeReferenceTraitTest :c-backend:test --no-daemon` passes. Closure lowering retains the trait/target/origins and hoists a nested closure from a trait method; module type collection visits target, signature, and body types while excluding the pseudo-`self` type; backend runtime/dependency scans descend into trait methods; CLI AST inspection retains grouping and prints method bodies; SDK metadata emits `trait:<target>` and `extension-method:<target>.<method>` declarations and extension-only exports. `AstArena` rewrite operations remain node-kind agnostic (they store/rewrite IDs, not recursively visit declarations); semantic symbols now exist, while resolved-reference/navigation integration remains R11.2.2.1.

##### R11.1.1.3 [DONE] Preserve traits through CPX and stabilization

**Language:** LS §10 CPX expansion model; §12 Hygiene; §17 Structural compile-time phase; §18 Type-universe barrier; §29 CPX and source provenance.
**Technical:** TS §13.1 Compile-time traits; §19 Expansion identity; §24 Type-universe stabilization.

**Depends:** R11.1.1.2.
**Files:** `comptime/.../CpxExpansion.kt`; CPX scheduler/reflection consumers; `CpxExpansionTest.kt`.

**Deliverable:** Trait-aware fingerprinting, expansion, replay, reorigin and hygiene; methods attach only through semantic registration against the stabilized target catalogue. Keep target binding distinct from generated local-name hygiene.

**Acceptance:** CPX-generated trait methods preserve declaration/call-site origin chains and stable identities across replay. Target declarations generated earlier resolve after stabilization; cycles and late forbidden structural mutations diagnose. Traits do not create new layout fields or fake structural type descriptors.

**Verify:** `gradle :comptime:test :compiler:test` (trait expansion and barrier fixtures).

**Evidence:** `./gradlew :comptime:test --tests cplus.comptime.CpxExpansionTest.generatedTraitRetainsOriginsHygieneAndStableExpansionIdentity --tests cplus.comptime.CpxExpansionTest.reflectiveExpansionCannotIntroduceTraitRegistrationAfterTheTypeBarrier --tests cplus.comptime.CpxExpansionTest.structuralFingerprintDistinguishesTraitReceiverStorageForms --tests cplus.comptime.CpxExpansionTest.reflectiveCpxRejectsStructuralDeclarationsButKeepsExecutableDeclarations --no-daemon` and the full `./gradlew :comptime:test :compiler:test --no-daemon` pass. CPX-generated traits retain target/method/receiver expansion origins; method locals and references are hygienically renamed while the target and receiver remain bound; equivalent replays retain expansion keys and structural fingerprint; receiver storage form affects fingerprints. Reflective CPX now classifies trait registration as phase-sensitive, reports `CPX008`, and excludes the trait, while `structuralTypeDescriptor` remains null for traits so no layout type is fabricated.

#### R11.1.2 [DONE] [3/3] Canonical receiver and visibility resolution

**Language:** LS §6.3.1 Compile-time extension methods; §6.5 Member conflict; §21.2 Import semantics.
**Technical:** TS §13.1 Compile-time traits; §14 Member-call resolution; §29 Semantic model.

##### R11.1.2.1 [DONE] Generalize method ownership without changing native methods

**Language:** LS §6 Structures and methods; §23 Symbol identity and C symbol generation.
**Technical:** TS §12 Type system model; §13 Method model; §14 Member-call resolution.

**Depends:** R11.1.1.3.
**Files:** `semantic/.../Semantics.kt` (`MethodSymbol`, `SemanticModel`, method maps); backend/naming/tooling consumers of owner types.

**Deliverable:** Canonical receiver identity independent of `StructType`, with defining-module identity retained separately. A single lookup API supports native methods and future visible extensions; aliases share a receiver key while unrelated same-name nominal types do not.

**Acceptance:** All existing instance/static/pointer struct-method tests pass unchanged. Focused tests distinguish same-spelling types from different modules and equate imported/typedef aliases. Duplicate insertion is detected rather than overwritten by `associate`; no global struct mutation grants extension visibility.

**Verify:** `gradle :semantic:test :compiler:test :c-backend:test :cli:test`.

**Evidence:** `./gradlew :semantic:test --tests cplus.semantic.MethodRegistryTest :compiler:test --tests cplus.compiler.CompilerIntegrationTest.instanceAndStaticMethodsLowerToCallableCFunctions --tests cplus.compiler.CompilerIntegrationTest.pointerReceiversCanReadAndMutateTheUnderlyingObject :c-backend:test :cli:test --no-daemon` and the full `./gradlew :semantic:test :compiler:test :c-backend:test :cli:test --no-daemon` pass. `MethodSymbol` now carries a general `CType` owner, canonical `ReceiverIdentity`, and defining module. `MethodRegistry` centralizes native/extension lookup and direct-module visibility without mutating a type's native method list; duplicate native receiver/name registration is diagnosed as SEM416 instead of being overwritten. Canonical nominal type keys use type identity rather than spelling, so same-spelling nominal types remain distinct while aliases and pointer receiver expressions normalize to the target. C name mangling accepts the generalized owner and preserves existing struct output. Focused tests verify alias identity, distinct nominal IDs, module-scoped extension candidates, duplicate handling, and native owner/module retention.

##### R11.1.2.2 [DONE] Resolve targets, receivers and method bodies

**Language:** LS §6.3.1 Compile-time extension methods; §6.2.1 Pointer receivers; §32 Name lookup.
**Technical:** TS §13.1 Compile-time traits; §14 Member-call resolution; §29 Semantic model.

**Depends:** R11.1.2.1.
**Files:** `semantic/.../Semantics.kt`; semantic reference/type model and call-resolution consumers.

**Deliverable:** Resolve local/imported/aliased struct, union, enum and non-void primitive targets; bind `self`/`self*` in the defining module's body scope and type-check normal parameters/results. Record selected method and receiver adaptation on semantic call results.

**Acceptance:** Positive fixtures cover every target kind and multiword primitives via typedef; negative fixtures cover unknown/private/incomplete/pointer/array/function/void targets, invalid self use, wrong arguments, and nonaddressable pointer receivers. Expressions with side effects are represented for single evaluation. Existing field/function-pointer member-call resolution is unchanged.

**Verify:** `gradle :semantic:test :compiler:test` (trait semantic/reference fixtures).

**Evidence:** `./gradlew :semantic:test :compiler:test --no-daemon` passes, as does `./gradlew :semantic:test --tests cplus.semantic.TraitResolutionTest --no-daemon`. Semantic fixtures resolve local struct/pointer-receiver methods, primitive/value receivers, imported nominal struct targets, multiword typedef aliases, union and enum targets; method bodies bind typed `self` and normal parameters; calls retain selected `MethodSymbol`, receiver expression, and VALUE/ADDRESS/POINTER adaptation. Negative fixtures diagnose void, unknown, pointer, array, function-pointer, private imported, and incomplete foreign targets, incompatible results, argument-count errors, and non-addressable `self*` receivers. Trait symbols retain their defining module and owner type; calls consult the shared `MethodRegistry` with local/direct-import visibility enforced by R11.1.2.3; C emission remains R11.2.

##### R11.1.2.3 [DONE] Enforce extension import activation and collisions

**Language:** LS §6.3.1 Compile-time extension methods; §6.5 Member conflict; §21.2 Import semantics; §21.5 Cyclic imports.
**Technical:** TS §13.1 Compile-time traits; §27 Import resolver; §30 Reference index.

**Depends:** R11.1.2.2.
**Files:** `semantic/.../Semantics.kt`; module/import visibility models; shared import-index export metadata.

**Deliverable:** Module-local default visibility and block-level `pub`; direct imports activate public extension sets without creating extra type/value bindings. No transitive re-export. Deterministic native/field/local duplicate errors and imported-provider ambiguity diagnostics.

**Acceptance:** Multi-module tests cover private leakage, selective/module/aliased imports, two providers, typedef-equivalent receivers, same-name distinct nominal types, cycles and reversed import order. A local duplicate fails at declaration; an ambiguous imported name fails when called, naming both providers. Unrelated extension names remain usable.

**Verify:** `gradle :semantic:test :compiler:test :cli:test` (multi-module visibility fixtures).

**Evidence:** `./gradlew :semantic:test :compiler:test :cli:test --no-daemon` passes after updating the AST-inspection assertion to expect successful semantic acceptance of the now-supported trait declaration. `TraitImportVisibilityTest` verifies direct selective/module/aliased activation without free-function bindings, hidden private/unimported/transitive extensions (including cycles), deterministic imported-provider ambiguity for canonical-identical receivers through typedef aliases, local native/field/duplicate conflicts, and rejection of public extension targets/signatures that expose private types. The model exposes only directly imported modules, and calls resolve only a single visible candidate; ambiguity records no winner. CLI AST inspection continues to keep trait methods grouped with their bodies. R11.2 owns C emission and editor navigation.

### R11.2 [DOING] [4/6] C lowering and editor/product acceptance

**Language:** LS §6.3.1 Compile-time extension methods; §28 Source provenance; §30 Lowering model; §41 Language-server model.
**Technical:** TS §33 Method lowering; §41 C symbol naming; §54 Completion; §63 Testing architecture.

#### R11.2.1 [DONE] [3/3] Lower and execute extension methods

**Language:** LS §6.3.1 Compile-time extension methods; §30 Lowering model; §31 Final C-subset validation.
**Technical:** TS §33 Method lowering; §41 C symbol naming; §43 Source-map builder.

##### R11.2.1.1 [DONE] Emit one typed C function per extension definition

**Language:** LS §6.3.1 Compile-time extension methods; §23 Symbol identity and C symbol generation; §26 Header generation.
**Technical:** TS §33 Method lowering; §38 Header synthesis; §41 C symbol naming.

**Depends:** R11.1.2.3.
**Files:** `c-backend/.../CBackend.kt`; existing symbol-name and C type/dependency emitters.

**Deliverable:** Emit extension definitions/prototypes from selected semantic symbols with receiver types valid for aggregate, enum and primitive targets. Include defining module and canonical receiver identity in stable C names; preserve linkage and origins. Remove trait nodes before C-subset validation.

**Acceptance:** Generated-C tests show exactly one definition across aliases and cyclic imports, distinct names for unrelated providers, necessary cross-module prototypes, and no fabricated struct for `int`/enum receivers. Target field order/size/alignment and existing native-method C names remain unchanged.

**Verify:** `gradle :c-backend:test :compiler:test` (C snapshots and C-subset validation).

**Evidence:** `./gradlew :c-backend:test :compiler:test --no-daemon` passes, as do the focused emission tests. Each resolved trait method is emitted exactly once as a typed C function, with a provider-qualified `__cplus_ext_...` symbol; public methods are included in generated headers. Receiver parameters use pointers to the actual aggregate, enum, or primitive type, not synthetic structs. Tests verify prototype/definition pairs for all three receiver classes, canonical naming through a typedef receiver, and distinct symbols for two providers in cyclically importing modules targeting the same canonical receiver. Native method names and lowering remain unchanged. Resolved call rewriting and implicit scalar `self` storage adaptation are R11.2.1.2.

##### R11.2.1.2 [DONE] Lower resolved calls with correct receiver storage

**Language:** LS §6.3.1 Compile-time extension methods; §6.2.1 Pointer receivers; §37 Referential safety of generated expressions.
**Technical:** TS §14 Member-call resolution; §33 Method lowering.

**Depends:** R11.2.1.1.
**Files:** `c-backend/.../CBackend.kt`; semantic resolved-call/receiver adaptation records.

**Deliverable:** Use the resolved method identity, not a second backend name lookup. Pass existing pointers directly; take an address only where valid. Lower implicit `self` storage access for scalars/aggregates and explicit pointer access for `self*`; materialize supported non-pointer temporaries only under ordinary receiver rules.

**Acceptance:** Runtime fixtures prove mutations reach the original variable where specified, a pointer is not addressed twice, and side-effecting receiver expressions execute exactly once. Invalid pointer-receiver temporaries fail before emission. Field callbacks and native methods still select their original path.

**Evidence:** `./gradlew :semantic:test :c-backend:test :compiler:test --no-daemon` passes. Backend call lowering now uses the semantic `ResolvedMethodCall` for the selected extension symbol and VALUE/ADDRESS/POINTER adaptation; native and qualified calls remain on their existing lowering path. An end-to-end generated-C test compiles and runs: pointer receivers mutate the original aggregate without double-addressing, a pointer-returning side-effect expression runs once, and a primitive value receiver is passed by value. Semantic coverage rejects pointer-receiver and aggregate-value temporaries requiring an invalid address (SEM419). Existing native instance/static method regressions pass. Aggregate `self` remains address-backed, while primitive/enum `self` is a C value parameter.

**Verify:** `gradle :c-backend:test :compiler:test` plus native compile/execute receiver fixtures.

##### R11.2.1.3 [DONE] Verify the complete trait execution and provenance matrix

**Language:** LS §6.3.1 Compile-time extension methods; §28 Source provenance; §29 CPX and source provenance.
**Technical:** TS §33 Method lowering; §43 Source-map builder; §63 Testing architecture.

**Depends:** R11.2.1.2.
**Files:** `compiler/.../CompilerIntegrationTest.kt`, CPX/compiler integration fixtures; new `examples/traits.cp` and isolated multi-module fixtures.

**Deliverable:** End-to-end executable tests for existing/imported structs, union/enum/scalar receivers, imported aliases, pointer mutation, and CPX-generated extensions; source-map and diagnostics assertions.

**Acceptance:** Native C products produce asserted results, not just nonempty output. An error in a trait method maps to its real method declaration/expansion origin. Before/after layout checks match. Cross-module and closure/defer/string-template trait bodies execute correctly. Unsupported cases retain stable diagnostics.

**Verify:** `gradle :comptime:test :semantic:test :c-backend:test :compiler:test :cli:test` with native fixture execution.

**Evidence:** `./gradlew :comptime:test :semantic:test :c-backend:test :compiler:test :cli:test --no-daemon` passes. Native generated-C fixtures execute imported struct typedef-alias, union, and enum extensions across modules; primitive and aggregate/pointer receiver runtime behavior; and CPX-generated extension bodies containing a captured closure, `defer`, and string-template formatting. The CPX fixture asserts one unchanged aggregate definition, successful runtime results, and generated-line mappings whose `Origin.Expansion` retains both definition and invocation ranges. Private/invalid receiver and unsupported target diagnostics remain covered by semantic tests. Platform-specific native Windows execution remains in the final product gate.

#### R11.2.2 [DOING] [2/3] Expose and release extension methods

**Language:** LS §6.3.1 Compile-time extension methods; §40 Formatting and IDE representation; §41 Language-server model; §42 TextMate and editor lexical highlighting.
**Technical:** TS §13.1 Compile-time traits; §53 Navigation; §54 Completion; §63 Testing architecture.

##### R11.2.2.1 [DONE] Use visible extension symbols in language tooling

**Language:** LS §6.3.1 Compile-time extension methods; §41 Language-server model.
**Technical:** TS §13.1 Compile-time traits; §30 Reference index; §53 Navigation; §54 Completion; §55 Hover.

**Depends:** R11.2.1.3, R10.3.1.2.
**Files:** `cli/.../LspLanguageService.kt`, `LspServer.kt`; semantic references and shared import index.

**Deliverable:** Member completion, hover, signature help, definition/references and rename use canonical receiver/method identities and real declaration URIs. Metadata exposes public extension providers without pretending their methods are top-level importable functions.

**Acceptance:** JSON-RPC fixtures cover scalar/struct/imported/alias receivers, self* parameter display, cross-file definition and method rename. Private/unimported extensions do not appear as resolved members; ambiguous providers never resolve by order. Both native and extension methods remain available through the shared lookup.

**Verify:** `gradle :cli:test :compiler:test` (member tooling and apply-rename fixtures).

**Evidence:** `./gradlew :semantic:test :compiler:test :cli:test --no-daemon` passes, including focused `LspTraitMethodsTest` coverage. Member completion calls the shared visibility-aware registry and covers imported alias/struct and primitive receivers; private and unimported extensions stay hidden. Semantic references index trait method bodies and resolved calls by the selected method identity. Definition, references, hover, and cross-file rename navigate to the declaring extension; signature help displays the explicit `self*` receiver type and regular parameters. Native methods remain available from the same registry and existing LSP integration tests remain green.

##### R11.2.2.2 [DONE] Document and highlight the exact trait syntax

**Language:** LS §6.3.1 Compile-time extension methods; §40 Formatting and IDE representation; §42 TextMate and editor lexical highlighting.
**Technical:** TS §13.1 Compile-time traits; §52 Semantic tokens; §75 Architectural rule for IDE support.

**Depends:** R11.2.2.1.
**Files:** `README.md`, `examples/traits.cp`, `vscode-extension/syntaxes/cplus.tmLanguage.json`, extension tests; specification examples.

**Deliverable:** Syntax highlighting and runnable documentation for singular unbracketed traits, local aliases for multiword targets, public/private direct-import activation, pointer receivers and explicit limitations. No named-interface or type-layout promises.

**Acceptance:** All documented positive examples parse/check/run; negative examples assert diagnostics. TextMate and semantic token tests distinguish `trait`, type identifiers and receivers without embedding semantic resolution in the grammar. SDK/import examples use real exported names.

**Verify:** `gradle :cli:test`; in `vscode-extension`: `npm test`, `npm run check`.

**Evidence:** `./gradlew :cli:fatJar --no-daemon` succeeds and the packaged CLI runs `examples/traits.cp` with output `compile-time trait example passed` and exit code 0. The example exercises public/private direct-import activation, a local alias for a multiword typedef, aggregate/enum/union targets, and value/pointer receivers across modules. Existing parser regression `AstGoldenTest.rejectsInvalidTraitFormsAndRecoversAtFollowingDeclarations` verifies diagnostics and recovery for plural/angle-bracket syntax, missing/repeated receivers, fields, static/bodyless methods, and nesting. `npm test` passes 8/8, including TextMate scopes for `trait`, type targets, and `self`; `npm run check` passes; `git diff --check` is clean.

##### R11.2.2.3 [TODO] Close trait regression and packaged cross-platform gates

**Language:** LS §6.3.1 Compile-time extension methods; §41 Language-server model; §51 Required compiler invariants.
**Technical:** TS §13.1 Compile-time traits; §60 Phase invariants; §63 Testing architecture; §78 SDK, ABI, runtime and platform architecture.

**Depends:** R11.2.2.2, R10.3.2.3.
**Files:** Compiler/CLI/VSIX product fixtures; `SPEC.COVERAGE.md`; `IMPL.PLAN.md`.

**Deliverable:** A final evidence ledger with exact commands, results, target/driver versions, commits and any limitations. Packaged VSIX tests call the configured JAR for trait completion/navigation and Run Main.

**Acceptance:** Full local suites and native C trait execution pass, then final native Windows x86_64 product/trait/VSIX checks pass for claimed configurations. Changed paths require fresh evidence. Every R11 leaf is accepted and both dashboards/coverage agree before R11 becomes DONE; unavailable Windows testing leaves this gate open, not optimistically complete.

**Verify:** `gradle test :cli:fatJar :cli:installDist`; packaged VSIX host tests on Linux and native Windows at the final platform pass.

### Planning validation checkpoint — 2026-10-08

This checkpoint validates documentation only. The R10/R11 hierarchy contains
49 task nodes and 31 terminal leaves (R10: 19; R11: 12), all TODO. A read-only
check verified unique IDs, every ancestor subtotal, explicit leaf fields, and
the runbook's complete dependency-respecting execution order. At that planning
checkpoint, totals were 97/128 accepted leaves and 10/12 accepted phase gates.
The subsequent R10.1.1.1 evidence is recorded above.

## R12 [DONE] [1/1] Generated CLI and editor build identity

**Request:** Provide a CLI `version` subcommand, include version metadata in no-argument
help, and expose the same build identity through the VS Code extension.

##### R12.1 [DONE] Generate and propagate build metadata

Gradle generates an untracked `Version` class before CLI compilation and packaging. Its
fields are the full and short Git commit, commit timestamp, build timestamp (both using
`yyyy-MM-dd HH:mm:ss`), and the active milestone codename from `IMPL.PLAN.md`. The CLI
supports `version`, `--version`, and `-V`; ordinary help begins with this metadata. LSP
initialization advertises the generated build fields, and VS Code offers “C+: Show CLI
Version” using the configured Java executable and CLI JAR.

**Evidence:** `./gradlew :compiler:test :cli:test --no-daemon`,
`./gradlew :cli:fatJar --no-daemon`, both `java -jar ... version` and no-argument help,
and the VS Code extension `npm test` pass on Linux.

## Execution order and commit policy

The work proceeds vertically in this order:

```text
R0 → R1 → R2 → R3 → R4 → R5 → R6 → R7 → R8 → R9
   → R10 local work → R11 local work → final R10/R11 platform gates
```

For the new queue, execute R10 in dependency order through R10.3.2.2, then
R11 through R11.2.2.2. Perform the final Windows pass in R10.3.2.3 followed by
R11.2.2.3. Their prerequisites explicitly permit this order. An unstarted
final gate remains TODO while local implementation advances; do not count it
DONE merely because Linux passes. If unavailable at final validation, record
the missing evidence and leave the gate open. The runbook gives the complete
ordered leaf list and resumption instructions.

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
