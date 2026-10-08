# C+ Version and Compatibility Policy

## Current status

C+ is pre-1.0 software (`0.1.0-SNAPSHOT` in the Gradle build). Until a 1.0
release is declared, source-language, CLI, SDK, and generated-code behavior may
change incompatibly between releases. The repository does not promise that a
binary produced by one SDK/runtime version can be linked against another.

The checked-in SDK manifest currently declares:

| Contract | Current value | Governs |
| --- | --- | --- |
| `sdk_version` | `0.1.0` | SDK package contents and metadata |
| `language_abi_version` | `1` | compiler/runtime assumptions required by generated code |
| `cplus_abi_version` | `1` | C+ public representation and calling conventions |
| `runtime_abi_version` | `1` | runtime entry points and runtime services |
| `libc_profile_version` | `c17-1` | provided C17 compatibility profile |
| `CPLUS_PAL_API_VERSION` | `4` | C runtime-to-platform-adapter contract |

These values are independent compatibility signals; matching one does not
imply that the other contracts match. A toolchain or SDK must use the target
descriptor selected for the product's target triple.

## Change and upgrade rules

- A change to generated-code assumptions or language-level ABI behavior MUST
  increment `language_abi_version` and include compiler/runtime regression
  fixtures.
- A breaking change to public C+ type layout, symbol identity, or calling
  convention MUST increment `cplus_abi_version` and require regenerating client
  objects and headers.
- A breaking runtime entry-point or service change MUST increment
  `runtime_abi_version`. A change to the PAL function signatures, structures,
  constants, or error contract MUST also increment `CPLUS_PAL_API_VERSION`.
- A breaking change to the supplied C library surface or its behavioral
  profile MUST increment `libc_profile_version`.
- `sdk_version` identifies a released SDK package; it MUST change whenever
  that package's contents or metadata change. It is not a substitute for the
  ABI version fields.
- ABI version changes are not binary-compatible upgrades. Rebuild C+ sources
  and C interoperability units against the matching SDK and runtime. Do not
  mix products across operating systems, architectures, or ABI descriptors.
- SDK metadata indexes/caches are derived artifacts. They may be discarded and
  regenerated from the manifest and sources; they are not a stable interchange
  format or an authority over those inputs.

After 1.0, the project SHOULD adopt semantic-versioning guarantees for the
documented stable source and CLI surface. ABI compatibility will continue to
be governed by the explicit ABI version fields rather than package version
alone.

## Supported-product caveats

The validated native execution set is Linux x86_64, Linux AArch64 under QEMU,
and Windows x86_64. Windows native validation uses the GCC/UCRT64 profile and
the declared LLP64/GNU x87 `long double` ABI. The MSVC binary64 `long double`
profile is not currently supported as ABI-compatible. Windows C17 execution
currently reports 46 pass, 0 fail, and 3 unsupported (complex arithmetic,
complex types, and tgmath). Windows AArch64 and Darwin execution are not
claimed. See [SPEC.COVERAGE.md](SPEC.COVERAGE.md) and
the R1–R8 dashboard in [IMPL.PLAN.md](IMPL.PLAN.md) for subsystem-level
evidence and remaining gates.

Self-hosted products do not link against glibc, musl, UCRT, or MSVCRT as their
runtime implementation. A target-compatible C compiler and linker are still
required at build time, and the resulting Windows product imports the declared
system APIs used by the PAL.
