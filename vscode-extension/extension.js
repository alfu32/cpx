const vscode = require('vscode');
const fs = require('fs');
const path = require('path');
const { execFile } = require('node:child_process');
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
  const configuredSdkManifest = settings.get('server.sdkManifest', '');
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
  const sdkManifest = configuredSdkManifest
    ? path.resolve(expandWorkspaceVariable(configuredSdkManifest, workspace))
    : undefined;
  return {
    command: javaPath,
    args: [...(sdkManifest ? [`-Dcplus.sdk.manifest=${sdkManifest}`] : []), '-jar', jarPath, ...cliArguments],
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
        configurationSection: 'cplus',
        fileEvents: vscode.workspace.createFileSystemWatcher('**/*.{cp,h}')
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

  const server = cliConfiguration(['run', entryPoint]);
  if (!server) return;
  const terminal = vscode.window.createTerminal({
    name: 'C+ Run',
    shellPath: server.command,
    shellArgs: server.args,
    cwd: server.cwd
  });
  terminal.show(true);
}

function showVersion() {
  const server = cliConfiguration(['version']);
  if (!server) return;
  return new Promise((resolve) => {
    execFile(server.command, server.args, { cwd: server.cwd, windowsHide: true }, (error, stdout, stderr) => {
      if (error) {
        vscode.window.showErrorMessage(`C+ CLI version could not be read: ${stderr || error.message}`);
        resolve();
        return;
      }
      const output = vscode.window.createOutputChannel('C+ Version');
      output.appendLine(stdout.trim());
      output.show();
      resolve();
    });
  });
}

function activate(context) {
  context.subscriptions.push(
    vscode.commands.registerCommand('cplus.restartServer', restartClient),
    vscode.commands.registerCommand('cplus.runMain', runMain),
    vscode.commands.registerCommand('cplus.showVersion', showVersion)
  );
  return startClient();
}

function deactivate() {
  return client ? client.stop() : undefined;
}

module.exports = {
  activate,
  deactivate,
  configuration,
  cliConfiguration,
  runMain,
  showVersion
};
