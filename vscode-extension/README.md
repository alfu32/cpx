# C+ Language Support for Visual Studio Code

This package provides C+ syntax highlighting, language configuration, and an
LSP client for the Kotlin language server in the repository.

## Requirements

- Visual Studio Code 1.85 or newer.
- Java 21 or newer.
- The self-contained CLI JAR built from this repository.

Build the CLI JAR first:

```text
gradle :cli:fatJar
```

The extension starts the server as `java -jar <jar> lsp`. The default JAR path
targets this repository's output. It can be overridden through workspace
settings:

```json
{
  "cplus.server.jarPath": "${workspaceFolder}/cli/build/libs/cplus-cli-0.1.0-SNAPSHOT-all.jar",
  "cplus.server.javaPath": "java",
  "cplus.server.args": [
    "lsp"
  ],
  "cplus.server.cwd": "${workspaceFolder}"
}
```

The server provides diagnostics, semantic tokens, completion, hover,
definition, references, and signature help. Use **C+: Restart Language Server**
after changing server settings.

Use **C+: Run Main** to execute the configured main source through the same
CLI JAR. The command launches `java -jar <jar> run <main.cp> ...` in an
integrated terminal and includes locally discoverable imported `.cp` modules.
Set `cplus.run.mainSource` when the active editor is not the program entry
point.

## Development

```text
npm install
npm test
npm run check
npm run package
```

Install the generated `.vsix` through VS Code's **Extensions: Install from
VSIX...** command.
