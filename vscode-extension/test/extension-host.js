const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vscode = require('vscode');

function waitForDiagnostic(uri, expectedCode, timeoutMs = 30000) {
  return new Promise((resolve, reject) => {
    let timer;
    const subscription = vscode.languages.onDidChangeDiagnostics((event) => {
      if (!event.uris.some((changedUri) => changedUri.toString() === uri.toString())) return;
      const diagnostic = vscode.languages.getDiagnostics(uri)
        .find((item) => String(item.code) === expectedCode);
      if (!diagnostic) return;
      clearTimeout(timer);
      subscription.dispose();
      resolve(diagnostic);
    });
    timer = setTimeout(() => {
      subscription.dispose();
      reject(new Error(`Timed out waiting for C+ diagnostic ${expectedCode}`));
    }, timeoutMs);
    const existing = vscode.languages.getDiagnostics(uri)
      .find((item) => String(item.code) === expectedCode);
    if (existing) {
      clearTimeout(timer);
      subscription.dispose();
      resolve(existing);
    }
  });
}

async function run() {
  const workspace = vscode.workspace.workspaceFolders?.[0]?.uri.fsPath;
  assert.ok(workspace, 'the extension-host test must open its isolated workspace');

  const extension = vscode.extensions.getExtension('cplus.cplus-language-support');
  assert.ok(extension, 'packaged C+ extension should be installed in the Extension Development Host');
  const installedExtensionsDirectory = process.env.CPLUS_TEST_EXTENSIONS_DIR;
  assert.ok(installedExtensionsDirectory, 'the host test must provide its isolated extension directory');
  const extensionRelativePath = path.relative(installedExtensionsDirectory, extension.extensionPath);
  assert.ok(extensionRelativePath &&
    extensionRelativePath !== '..' &&
    !extensionRelativePath.startsWith(`..${path.sep}`) &&
    !path.isAbsolute(extensionRelativePath),
  `C+ must load from the installed VSIX directory; actual path: ${extension.extensionPath}`);
  await extension.activate();

  const commands = await vscode.commands.getCommands(true);
  assert.ok(commands.includes('cplus.restartServer'), 'restart command should register');
  assert.ok(commands.includes('cplus.runMain'), 'Run Main command should register');

  const sourcePath = path.join(workspace, 'host-diagnostic.cp');
  fs.writeFileSync(sourcePath, 'int main() { return missing_host_test_function(); }\n');
  const sourceUri = vscode.Uri.file(sourcePath);
  const diagnostics = waitForDiagnostic(sourceUri, 'SEM302');
  const document = await vscode.workspace.openTextDocument(sourceUri);
  await vscode.window.showTextDocument(document);

  assert.equal(document.languageId, 'cplus', 'the .cp file should use the C+ language mode');
  await diagnostics;
  console.log('Installed C+ VSIX activated, registered commands, and delivered CLI diagnostics');
}

module.exports = { run };
