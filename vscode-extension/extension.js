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

function configuration() {
  const settings = vscode.workspace.getConfiguration('cplus');
  const workspace = workspaceDirectory();
  const configuredJarPath = settings.get('server.jarPath', '');
  const javaPath = settings.get('server.javaPath', 'java');
  const args = settings.get('server.args', ['lsp']);
  const configuredCwd = settings.get('server.cwd', '${workspaceFolder}');
  const cwd = path.resolve(expandWorkspaceVariable(configuredCwd, workspace));
  const jarPath = path.resolve(expandWorkspaceVariable(configuredJarPath, workspace));
  if (!configuredJarPath) {
    vscode.window.showErrorMessage('C+ language server JAR is not configured. Set cplus.server.jarPath.');
    return undefined;
  }
  if (!fs.existsSync(jarPath)) {
    vscode.window.showErrorMessage(`C+ language server JAR was not found: ${jarPath}`);
    return undefined;
  }
  return {
    command: javaPath,
    args: ['-jar', jarPath, ...(Array.isArray(args) ? args.map(String) : ['lsp'])],
    cwd
  };
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

function activate(context) {
  context.subscriptions.push(
    vscode.commands.registerCommand('cplus.restartServer', restartClient)
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
