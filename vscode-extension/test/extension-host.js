const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
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

function waitForDiagnostics(uri, predicate, timeoutMs = 30000) {
  return new Promise((resolve, reject) => {
    let timer;
    const finish = () => {
      const diagnostics = vscode.languages.getDiagnostics(uri);
      if (!predicate(diagnostics)) return false;
      clearTimeout(timer);
      subscription.dispose();
      resolve(diagnostics);
      return true;
    };
    const subscription = vscode.languages.onDidChangeDiagnostics((event) => {
      if (event.uris.some((changedUri) => changedUri.toString() === uri.toString())) finish();
    });
    timer = setTimeout(() => {
      subscription.dispose();
      reject(new Error(`Timed out waiting for expected C+ diagnostics for ${uri}`));
    }, timeoutMs);
    finish();
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

  const stdlibPath = path.join(workspace, 'stdlib-completion.cp');
  const stdlibText = 'import std.;\nimport { std_fs_c } from std.fs;\nint main() { return 0; }\n';
  fs.writeFileSync(stdlibPath, stdlibText);
  const stdlibUri = vscode.Uri.file(stdlibPath);
  const stdlibDocument = await vscode.workspace.openTextDocument(stdlibUri);
  await vscode.window.showTextDocument(stdlibDocument);
  const providerItems = await vscode.commands.executeCommand(
    'vscode.executeCompletionItemProvider', stdlibUri, new vscode.Position(0, 'import std.'.length)
  );
  const providers = Array.isArray(providerItems) ? providerItems : providerItems.items;
  assert.ok(providers.some((item) => item.label === 'std.fs'), 'the packaged server should suggest stdlib providers');
  const exportItems = await vscode.commands.executeCommand(
    'vscode.executeCompletionItemProvider', stdlibUri, new vscode.Position(1, 'import { std_fs_c'.length)
  );
  const exports = Array.isArray(exportItems) ? exportItems : exportItems.items;
  const stdlibExport = exports.find((item) => item.label === 'std_fs_close');
  assert.ok(stdlibExport, 'the packaged server should suggest indexed stdlib exports');
  assert.match(stdlibExport.detail, /std_fs_close/);

  fs.writeFileSync(path.join(workspace, 'helper.cp'), [
    'pub int helperFunction() { return 20; }',
    'pub int helperAction() { return 22; }',
    ''
  ].join('\n'));
  const importPath = path.join(workspace, 'import-assistance.cp');
  fs.writeFileSync(importPath, 'int main() { return helperFunction() + helperAction(); }\n');
  const importUri = vscode.Uri.file(importPath);
  const importDocument = await vscode.workspace.openTextDocument(importUri);
  await vscode.window.showTextDocument(importDocument);
  assert.equal(importDocument.languageId, 'cplus');
  await waitForDiagnostics(importUri, (items) => items.some((item) =>
    String(item.code) === 'SEM301' && item.message.includes('helperAction')));

  const completionOffset = importDocument.getText().indexOf('helperFunction') + 'helperFunction'.length;
  const completionPosition = importDocument.positionAt(completionOffset);
  const completions = await vscode.commands.executeCommand(
    'vscode.executeCompletionItemProvider', importUri, completionPosition
  );
  const completionItems = Array.isArray(completions) ? completions : completions.items;
  const completion = completionItems.find((item) => item.label === 'helperFunction');
  assert.ok(completion, 'the language server should suggest the indexed public helper');
  assert.ok(completion.additionalTextEdits?.length, 'the suggestion should carry an auto-import edit');

  const completionEdit = new vscode.WorkspaceEdit();
  for (const edit of completion.additionalTextEdits) {
    completionEdit.replace(importUri, edit.range, edit.newText);
  }
  const completionDiagnostics = waitForDiagnostics(importUri, (items) =>
    items.some((item) => String(item.code) === 'SEM301' && item.message.includes('helperAction')) &&
    !items.some((item) => String(item.code) === 'SEM301' && item.message.includes('helperFunction')));
  assert.ok(await vscode.workspace.applyEdit(completionEdit), 'VS Code should apply the completion import edit');
  await completionDiagnostics;

  const actionDiagnostic = vscode.languages.getDiagnostics(importUri).find((item) =>
    String(item.code) === 'SEM301' && item.message.includes('helperAction'));
  assert.ok(actionDiagnostic, 'the remaining helper should still be unresolved before its quick fix');
  const actions = await vscode.commands.executeCommand(
    'vscode.executeCodeActionProvider', importUri, actionDiagnostic.range, vscode.CodeActionKind.QuickFix.value
  );
  const importAction = actions.find((item) => item.title.includes("Import 'helperAction' from helper"));
  assert.ok(importAction?.edit, 'the server should provide the unresolved-symbol import quick fix');
  const cleanDiagnostics = waitForDiagnostics(importUri, (items) => items.length === 0);
  assert.ok(await vscode.workspace.applyEdit(importAction.edit), 'VS Code should apply the quick-fix workspace edit');
  await cleanDiagnostics;

  const traitTypesPath = path.join(workspace, 'trait-types.cp');
  const traitExtensionsPath = path.join(workspace, 'trait-extensions.cp');
  const traitSourcePath = path.join(workspace, 'trait-consumer.cp');
  fs.writeFileSync(traitTypesPath, 'pub struct point_t { int value; };\n');
  fs.writeFileSync(traitExtensionsPath, [
    'import { point_t } from "./trait-types.cp";',
    'pub comptime trait point_t { int area(self) { return self.value; } }',
    ''
  ].join('\n'));
  const traitText = [
    'import { point_t } from "./trait-types.cp";',
    'import { area } from "./trait-extensions.cp";',
    'int main() { point_t point; point.value = 3; return point.area() == 3 ? 0 : 1; }',
    ''
  ].join('\n');
  fs.writeFileSync(traitSourcePath, traitText);
  const traitUri = vscode.Uri.file(traitSourcePath);
  const traitDiagnostics = waitForDiagnostics(traitUri, (items) => items.length === 0);
  const traitDocument = await vscode.workspace.openTextDocument(traitUri);
  await vscode.window.showTextDocument(traitDocument);
  await traitDiagnostics;

  const traitCallOffset = traitText.indexOf('point.area');
  const traitCompletionPosition = traitDocument.positionAt(traitCallOffset + 'point.'.length);
  const traitCompletions = await vscode.commands.executeCommand(
    'vscode.executeCompletionItemProvider', traitUri, traitCompletionPosition
  );
  const traitCompletionItems = Array.isArray(traitCompletions) ? traitCompletions : traitCompletions.items;
  assert.ok(traitCompletionItems.some((item) => item.label === 'area'),
    'the packaged server should complete an imported compile-time trait method');

  const traitDefinitionPosition = traitDocument.positionAt(traitCallOffset + 'point.'.length + 1);
  const traitDefinitions = await vscode.commands.executeCommand(
    'vscode.executeDefinitionProvider', traitUri, traitDefinitionPosition
  );
  const definitionList = Array.isArray(traitDefinitions) ? traitDefinitions : [traitDefinitions].filter(Boolean);
  assert.ok(definitionList.some((location) =>
    (location.targetUri || location.uri)?.fsPath === traitExtensionsPath),
  'the packaged server should navigate an extension call to its trait method declaration');

  await vscode.commands.executeCommand('cplus.runMain');
  assert.ok(vscode.window.terminals.some((terminal) => terminal.name === 'C+ Run'),
    'Run Main should open its configured CLI terminal');
  const packagedApi = require(path.join(extension.extensionPath, 'dist', 'extension.js'));
  const runConfiguration = packagedApi.cliConfiguration(['run', traitSourcePath]);
  assert.ok(runConfiguration, 'Run Main should resolve the configured CLI JAR');
  const runResult = spawnSync(runConfiguration.command, runConfiguration.args, {
    cwd: runConfiguration.cwd,
    encoding: 'utf8',
    timeout: 120000,
    windowsHide: true
  });
  assert.ifError(runResult.error);
  assert.equal(runResult.status, 0,
    `Run Main's configured CLI invocation should execute the trait consumer: ${runResult.stdout}\n${runResult.stderr}`);

  const sourcePath = path.join(workspace, 'host-diagnostic.cp');
  fs.writeFileSync(sourcePath, 'int main() { return missing_host_test_function(); }\n');
  const sourceUri = vscode.Uri.file(sourcePath);
  const diagnostics = waitForDiagnostic(sourceUri, 'SEM302');
  const document = await vscode.workspace.openTextDocument(sourceUri);
  await vscode.window.showTextDocument(document);

  assert.equal(document.languageId, 'cplus', 'the .cp file should use the C+ language mode');
  await diagnostics;
  console.log('Installed C+ VSIX completed import/trait completion, quick fixes, trait navigation, Run Main, and diagnostic recovery');
}

module.exports = { run };
