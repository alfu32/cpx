const vscode = require('vscode');
const fs = require('fs');
const path = require('path');
const { LanguageClient } = require('vscode-languageclient/node');

let client;

function workspaceDirectory() {
  return vscode.workspace.workspaceFolders?.[0]?.uri.fsPath;
}

function expandWorkspaceVariable(value, workspace) {
  return value.replaceAll('${workspaceFolder}', workspace || '');
}

function expandFileVariable(value) {
  const activeFile = vscode.window.activeTextEditor?.document.fileName || '';
  return value.replaceAll('${file}', activeFile);
}

function cliConfiguration(cliArguments) {
  const settings = vscode.workspace.getConfiguration('cplus');
  const workspace = workspaceDirectory();
  const configuredJarPath = settings.get('server.jarPath', '');
  const javaPath = settings.get('server.javaPath', 'java');
  const configuredCwd = settings.get('server.cwd', '${workspaceFolder}');
  const cwd = path.resolve(expandWorkspaceVariable(configuredCwd, workspace));
  const jarPath = path.resolve(expandWorkspaceVariable(configuredJarPath, workspace));
  if (!configuredJarPath) {
    vscode.window.showErrorMessage('C+ CLI JAR is not configured. Set cplus.server.jarPath.');
    return undefined;
  }
  if (!fs.existsSync(jarPath)) {
    vscode.window.showErrorMessage(`C+ language server JAR was not found: ${jarPath}`);
    return undefined;
  }
  return {
    command: javaPath,
    args: ['-jar', jarPath, ...cliArguments],
    cwd
  };
}

function configuration() {
  const settings = vscode.workspace.getConfiguration('cplus');
  const args = settings.get('server.args', ['lsp']);
  return cliConfiguration(Array.isArray(args) ? args.map(String) : ['lsp']);
}

function createClient(server) {
  return new LanguageClient(
    'cplusLanguageServer',
    'C+ Language Server',
    {
      command: server.command,
      args: server.args,
      options: {
        cwd: server.cwd,
        env: { ...process.env }
      }
    },
    {
      documentSelector: [{ scheme: 'file', language: 'cplus' }],
      synchronize: {
        configurationSection: 'cplus'
      },
      outputChannelName: 'C+ Language Server',
      revealOutputChannelOn: 4
    }
  );
}

async function startClient() {
  const server = configuration();
  if (!server) return;
  client = createClient(server);
  try {
    await client.start();
  } catch (error) {
    client = undefined;
    vscode.window.showErrorMessage(`C+ language server failed to start: ${error.message}`);
  }
}

async function restartClient() {
  if (client) {
    await client.stop();
  }
  await startClient();
}

async function importedSources(entryPoint) {
  const sources = [];
  const visited = new Set();

  async function visit(sourcePath) {
    const normalized = path.resolve(sourcePath);
    if (visited.has(normalized) || !fs.existsSync(normalized)) return;
    visited.add(normalized);
    sources.push(normalized);

    const text = fs.readFileSync(normalized, 'utf8');
    const imports = [...text.matchAll(/\bfrom\s+(?:"([^"]+)"|([^\s;]+))/g)]
      .map((match) => match[1] || match[2])
      .filter(Boolean);
    for (const moduleReference of imports) {
      const isPathImport = moduleReference.startsWith('.') ||
        path.isAbsolute(moduleReference) ||
        moduleReference.endsWith('.cp');
      if (isPathImport) {
        const pathCandidates = [
          path.resolve(path.dirname(normalized), moduleReference),
          path.resolve(workspaceDirectory() || process.cwd(), moduleReference)
        ];
        const importedPath = pathCandidates.find((candidate) => fs.existsSync(candidate));
        if (importedPath) await visit(importedPath);
        continue;
      }
      const moduleName = moduleReference
        .split('/')
        .pop()
        .split('.')
        .pop();
      const sibling = path.join(path.dirname(normalized), `${moduleName}.cp`);
      if (fs.existsSync(sibling)) {
        await visit(sibling);
        continue;
      }
      const matches = await vscode.workspace.findFiles(`**/${moduleName}.cp`, '**/{node_modules,build,dist}/**', 1);
      if (matches.length > 0) await visit(matches[0].fsPath);
    }
  }

  await visit(entryPoint);
  return sources;
}

async function runMain() {
  const settings = vscode.workspace.getConfiguration('cplus');
  const configuredSource = expandFileVariable(
    expandWorkspaceVariable(settings.get('run.mainSource', '${file}'), workspaceDirectory())
  );
  const entryPoint = path.resolve(configuredSource);
  if (!configuredSource || !fs.existsSync(entryPoint)) {
    vscode.window.showErrorMessage(`C+ main source was not found: ${entryPoint}`);
    return;
  }

  const document = vscode.workspace.textDocuments.find((candidate) =>
    path.resolve(candidate.fileName) === entryPoint
  );
  if (document?.isDirty && !(await document.save())) {
    vscode.window.showErrorMessage(`C+ main source could not be saved: ${entryPoint}`);
    return;
  }

  const server = cliConfiguration(['run', ...(await importedSources(entryPoint))]);
  if (!server) return;
  const terminal = vscode.window.createTerminal({
    name: 'C+ Run',
    shellPath: server.command,
    shellArgs: server.args,
    cwd: server.cwd
  });
  terminal.show(true);
}

function activate(context) {
  context.subscriptions.push(
    vscode.commands.registerCommand('cplus.restartServer', restartClient),
    vscode.commands.registerCommand('cplus.runMain', runMain)
  );
  return startClient();
}

function deactivate() {
  return client ? client.stop() : undefined;
}

module.exports = {
  activate,
  deactivate
};
