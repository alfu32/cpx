# C+ Language Support for Visual Studio Code

This package provides C+ syntax highlighting, language configuration, and an
LSP client for the Kotlin language server in the repository.

## Requirements

- Visual Studio Code 1.85 or newer.
- A `cplus` executable on `PATH`, or a configured command that starts the
  repository's LSP server.

The extension starts the server with `cplus lsp` by default. For a checkout of
this repository, use a workspace setting such as:

```json
{
  "cplus.server.command": "gradle",
  "cplus.server.args": [
    "--no-daemon",
    "-Dorg.gradle.native=false",
    "--console=plain",
    "--quiet",
    ":cli:run",
    "--args=lsp"
  ],
  "cplus.server.cwd": "${workspaceFolder}"
}
```

The server provides diagnostics, semantic tokens, completion, hover,
definition, references, and signature help. Use **C+: Restart Language Server**
after changing server settings.

## Development

```text
npm install
npm test
npm run check
npm run package
```

Install the generated `.vsix` through VS Code's **Extensions: Install from
VSIX...** command.
