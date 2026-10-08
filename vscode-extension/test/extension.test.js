const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const Module = require('node:module');
const test = require('node:test');

const root = path.resolve(__dirname, '..');

test('extension manifest declares C+ language and LSP client', () => {
  const manifest = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'));
  assert.equal(manifest.main, './dist/extension.js');
  assert.equal(manifest.contributes.languages[0].id, 'cplus');
  assert.deepEqual(manifest.contributes.languages[0].extensions, ['.cp']);
  assert.ok(manifest.contributes.commands.some((command) => command.command === 'cplus.runMain'));
  assert.equal(
    manifest.contributes.configuration.properties['cplus.server.jarPath'].default,
    '${workspaceFolder}/cli/build/libs/cplus-cli-0.1.0-SNAPSHOT-all.jar'
  );
  assert.equal(manifest.contributes.configuration.properties['cplus.server.sdkManifest'].default, '');
  assert.equal(manifest.devDependencies['vscode-languageclient'], '^9.0.1');
});

test('language configuration and TextMate grammar are valid JSON', () => {
  const configuration = JSON.parse(fs.readFileSync(path.join(root, 'language-configuration.json'), 'utf8'));
  const grammar = JSON.parse(fs.readFileSync(path.join(root, 'syntaxes', 'cplus.tmLanguage.json'), 'utf8'));
  assert.equal(configuration.comments.lineComment, '//');
  assert.equal(grammar.scopeName, 'source.cplus');
  assert.ok(grammar.repository.cpx);
});

function loadExtension(vscode, LanguageClient = class {}) {
  const originalLoad = Module._load;
  Module._load = function (request, parent, isMain) {
    if (request === 'vscode') return vscode;
    if (request === 'vscode-languageclient/node') return { LanguageClient };
    return originalLoad.call(this, request, parent, isMain);
  };
  try {
    delete require.cache[require.resolve('../extension.js')];
    return require('../extension.js');
  } finally {
    Module._load = originalLoad;
  }
}

function createVscode(workspace, settings, activeFile) {
  const terminals = [];
  const errors = [];
  const vscode = {
    workspace: {
      workspaceFolders: workspace ? [{ uri: { fsPath: workspace } }] : [],
      textDocuments: [],
      getConfiguration: () => ({ get: (key, fallback) => settings[key] ?? fallback })
    },
    window: {
      activeTextEditor: activeFile ? { document: { fileName: activeFile } } : undefined,
      showErrorMessage: (message) => errors.push(message),
      createTerminal: (options) => {
        const terminal = { options, shown: false, show() { this.shown = true; } };
        terminals.push(terminal);
        return terminal;
      }
    },
    commands: { registerCommand: () => ({ dispose() {} }) }
  };
  return { vscode, terminals, errors };
}

test('LSP configuration invokes the configured Java executable and CLI JAR with normalized paths', () => {
  const workspace = fs.mkdtempSync(path.join(os.tmpdir(), 'cplus extension workspace '));
  const jar = path.join(workspace, 'build output', 'cplus cli.jar');
  fs.mkdirSync(path.dirname(jar), { recursive: true });
  fs.writeFileSync(jar, 'test jar');
  const java = path.join(workspace, 'Java Runtime', 'bin', 'java');
  const sdkManifest = path.join(workspace, 'sdk files', 'sdk.toml');
  const cwd = path.join(workspace, 'server working directory');
  const { vscode, errors } = createVscode(workspace, {
    'server.jarPath': '${workspaceFolder}/build output/cplus cli.jar',
    'server.sdkManifest': '${workspaceFolder}/sdk files/sdk.toml',
    'server.javaPath': java,
    'server.args': ['lsp'],
    'server.cwd': '${workspaceFolder}/server working directory'
  });

  const server = loadExtension(vscode).configuration();

  assert.deepEqual(server, {
    command: java,
    args: [`-Dcplus.sdk.manifest=${sdkManifest}`, '-jar', jar, 'lsp'],
    cwd
  });
  assert.deepEqual(errors, []);
});

test('Run Main passes only the entry source to the configured CLI JAR', async () => {
  const workspace = fs.mkdtempSync(path.join(os.tmpdir(), 'cplus extension run '));
  const main = path.join(workspace, 'src files', 'main file.cp');
  const helper = path.join(workspace, 'src files', 'helper file.cp');
  const jar = path.join(workspace, 'build output', 'cplus cli.jar');
  fs.mkdirSync(path.dirname(main), { recursive: true });
  fs.mkdirSync(path.dirname(jar), { recursive: true });
  fs.writeFileSync(main, 'import { value } from "./helper file.cp"; int main() { return value(); }');
  fs.writeFileSync(helper, 'pub int value() { return 0; }');
  fs.writeFileSync(jar, 'test jar');
  const java = path.join(workspace, 'Java Runtime', 'bin', 'java');
  const cwd = path.join(workspace, 'project root');
  const { vscode, terminals, errors } = createVscode(workspace, {
    'server.jarPath': '${workspaceFolder}/build output/cplus cli.jar',
    'server.sdkManifest': path.join(root, '..', 'sdk', 'manifest', 'sdk.toml'),
    'server.javaPath': java,
    'server.cwd': '${workspaceFolder}/project root',
    'run.mainSource': '${file}'
  }, main);

  await loadExtension(vscode).runMain();

  assert.deepEqual(errors, []);
  assert.equal(terminals.length, 1);
  assert.deepEqual(terminals[0].options, {
    name: 'C+ Run',
    shellPath: java,
    shellArgs: [`-Dcplus.sdk.manifest=${path.resolve(root, '..', 'sdk', 'manifest', 'sdk.toml')}`, '-jar', jar, 'run', main],
    cwd
  });
  assert.equal(terminals[0].shown, true);
});

const cliJar = path.resolve(root, '..', 'cli', 'build', 'libs', 'cplus-cli-0.1.0-SNAPSHOT-all.jar');

test('configured LSP command starts the selected CLI JAR outside the repository cwd', {
  skip: !fs.existsSync(cliJar) && 'build the CLI fat JAR to enable this integration test'
}, () => {
  const workspace = fs.mkdtempSync(path.join(os.tmpdir(), 'cplus extension lsp integration '));
  const sdkManifest = path.resolve(root, '..', 'sdk', 'manifest', 'sdk.toml');
  const { vscode } = createVscode(workspace, {
    'server.jarPath': cliJar,
    'server.sdkManifest': sdkManifest,
    'server.javaPath': 'java',
    'server.args': ['lsp'],
    'server.cwd': '${workspaceFolder}'
  });
  const server = loadExtension(vscode).configuration();
  const requests = [
    '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}',
    '{"jsonrpc":"2.0","id":2,"method":"shutdown","params":null}',
    '{"jsonrpc":"2.0","method":"exit"}'
  ].map((body) => `Content-Length: ${Buffer.byteLength(body)}\r\n\r\n${body}`).join('');
  const process = spawnSync(server.command, server.args, {
    cwd: server.cwd,
    input: requests,
    encoding: 'utf8'
  });

  assert.equal(process.status, 0, process.stderr);
  assert.match(process.stdout, /"serverInfo"/);
});

test('Run Main command executes the configured CLI and resolves imported modules', {
  skip: !fs.existsSync(cliJar) && 'build the CLI fat JAR to enable this integration test'
}, async () => {
  const workspace = fs.mkdtempSync(path.join(os.tmpdir(), 'cplus extension cli integration '));
  const main = path.join(workspace, 'source files', 'main file.cp');
  const helper = path.join(workspace, 'source files', 'helper file.cp');
  fs.mkdirSync(path.dirname(main), { recursive: true });
  fs.writeFileSync(main, 'import { add } from "./helper file.cp"; int main() { return add(7, 5); }');
  fs.writeFileSync(helper, 'pub int add(int left, int right) { return left + right; }');
  const { vscode, terminals, errors } = createVscode(workspace, {
    'server.jarPath': cliJar,
    'server.sdkManifest': path.resolve(root, '..', 'sdk', 'manifest', 'sdk.toml'),
    'server.javaPath': 'java',
    'server.cwd': '${workspaceFolder}',
    'run.mainSource': '${file}'
  }, main);

  await loadExtension(vscode).runMain();
  assert.deepEqual(errors, []);
  const terminal = terminals[0].options;
  const process = spawnSync(terminal.shellPath, terminal.shellArgs, { cwd: terminal.cwd, encoding: 'utf8' });

  assert.equal(process.status, 12, process.stderr);
  assert.match(process.stdout, /built .*main file/);
});
