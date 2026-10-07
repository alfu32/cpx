const vscode = require('vscode');
const path = require('path');
const { LanguageClient } = require('vscode-languageclient/node');

let client;

function workspaceDirectory() {
  return vscode.workspace.workspaceFolders?.[0]?.uri.fsPath;
}

function configuration() {
  const settings = vscode.workspace.getConfiguration('cplus');
  const command = settings.get('server.command', 'cplus');
  const args = settings.get('server.args', ['lsp']);
  const configuredCwd = settings.get('server.cwd', '');
  const cwd = configuredCwd ? path.resolve(configuredCwd) : workspaceDirectory();
  return {
    command,
    args: Array.isArray(args) ? args.map(String) : ['lsp'],
    cwd
  };
}

function createClient() {
  const server = configuration();
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
  client = createClient();
  await client.start();
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
