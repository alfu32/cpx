const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
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
  assert.equal(manifest.devDependencies['vscode-languageclient'], '^9.0.1');
});

test('language configuration and TextMate grammar are valid JSON', () => {
  const configuration = JSON.parse(fs.readFileSync(path.join(root, 'language-configuration.json'), 'utf8'));
  const grammar = JSON.parse(fs.readFileSync(path.join(root, 'syntaxes', 'cplus.tmLanguage.json'), 'utf8'));
  assert.equal(configuration.comments.lineComment, '//');
  assert.equal(grammar.scopeName, 'source.cplus');
  assert.ok(grammar.repository.cpx);
});
