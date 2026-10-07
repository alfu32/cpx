# Repository Guidelines

## Project Structure & Module Organization

This repository currently contains the C+ language and compiler specifications:

- `SPEC.LANG.md` defines language semantics and normative behavior.
- `SPEC.TECH.md` describes the planned Kotlin/JVM compiler architecture.
- `.gitignore` is currently empty; keep generated files out of commits as tooling is added.

There is no implementation, test suite, asset directory, or build configuration yet. When implementation begins, follow the module boundaries proposed in `SPEC.TECH.md` (for example, `language-core/`, `semantic/`, `comptime/`, `compiler/`, `c-backend/`, `lsp/`, and `cli/`). Keep tests beside the relevant module or in a clearly named test-support module.

## Build, Test, and Development Commands

No build or test commands are configured at present. Do not invent a required local command until the Kotlin/Gradle project is added. Once available, document the canonical commands here and keep CI invocations aligned with them. Typical examples may be:

```text
./gradlew build       # compile all modules and run checks
./gradlew test        # run the test suite
./gradlew :cli:run    # run the compiler CLI locally
```

## Coding Style & Naming Conventions

Match the existing Markdown style: ATX headings, concise paragraphs, numbered normative sections where appropriate, and fenced code blocks for examples. Preserve the RFC-like requirement words (`MUST`, `SHOULD`, `MAY`) in specification changes. For the planned Kotlin implementation, use standard Kotlin formatting, four-space indentation, `UpperCamelCase` types, and `lowerCamelCase` functions/properties; use `snake_case` for C+ source examples when the language specification requires C-compatible identifiers.

## Testing Guidelines

There are no tests or coverage thresholds yet. New compiler work should add focused tests for parsing, semantic resolution, lowering, diagnostics, and source maps, plus end-to-end fixtures for behavior described in `SPEC.LANG.md`. Name tests after the behavior they verify, such as `resolvesImportedPackageSymbols`.

## Commit & Pull Request Guidelines

The repository has no commit history, so no established commit convention exists. Use short, imperative subjects (for example, `Define import resolution semantics`) and keep unrelated changes separate. Pull requests should explain the design impact, identify changed specification sections or modules, include tests or a reason none are possible, and include generated-output or diagnostic examples when behavior changes. Link an issue when one exists.

## response guidelines

- always respond in the sum up in the commitizen format

All commits must follow the Commitizen / Conventional Commits standard using the structural layout below:

### Commitizen / Conventional Commits standard
```text
<type>(<scope>): <subject>

<body>
```

#### Field Definitions

* **`<type>`**: Must be one of the following lowercase tokens:
    * `feat`: A new feature or capability.
    * `fix`: A bug fix.
    * `docs`: Documentation changes only.
    * `style`: Changes that do not affect the meaning of the code (white-space, formatting, missing semi-colons, etc).
    * `refactor`: A code change that neither fixes a bug nor adds a feature.
    * `perf`: A code change that improves performance.
    * `test`: Adding missing tests or correcting existing tests.
    * `chore`: Changes to the build process, auxiliary tools, or libraries/dependencies.
* **`<scope>`**: Optional. A noun naming the specific codebase component or module affected, wrapped in parentheses (e.g., `(parser)`, `(auth)`, `(runtime)`).
* **`<subject>`**: A brief, imperative-mood summary of the change. Do not capitalize the first letter. Do not end with a period.
* **`<body>`**: Optional. Separate from the subject with exactly one blank line. Provides the motivation for the change and contrasts it with previous behavior.

additionally the body should be structured as follows:

(REQUEST:)
- summary of what was asked/requested

(IMPLEMENTATION:)
- summary of the solution or answer
implementation details:
- bulleted list of technical/functional modifications or planning steps ( what you print out by default in the summary )

(NOT IMPLEMENTED:)
 - summary of not implemented features/parts of the request
 - features/requests remaining to be implemented/researched
 - eventual steps/tests to be taken by the user before proceeding

#### Examples

```text
fix(editor): persist and reveal mapped compiler diagnostics

REQUEST:
the user has to be able to see error points given by diagnostics by expandable markers in the gutter

IMPLEMENTATION:
  - Diagnostics are persisted on each node and restored with the project.
  - New validation/compilation clears previous diagnostics.
  - Gutter markers now reveal the mapped editor, section, and source line automatically.
  - Nodes with diagnostics show a red warning badge in the diagram.
  - Runtime/override errors without source-map entries are retained and shown as unmapped instead of being discarded.
  - The status bar now shows:
    generated-file:line:column -> node section source-line:column

NOT IMPLEMENTED:
  - colorisation and retrieval of code artifacts
  - research solution through local / embedded small LM.
    - we need CUDA working on this machine otherwise we'll not be able to test
```

```text
fix(compiler): resolve memory leaks on dynamic execution evaluation loops
```


## Security & Configuration Tips

Never commit credentials, tokens, private keys, or machine-specific configuration. Provide safe example configuration with placeholder values and document required environment variables. Review dependency and generated-file changes carefully before committing.


## Specification Changes

Treat `SPEC.LANG.md` as normative and `SPEC.TECH.md` as architectural guidance. Update both when an implementation decision changes language behavior and call out unresolved compatibility or lowering implications in the pull request.
