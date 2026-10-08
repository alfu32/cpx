const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const {
  downloadAndUnzipVSCode,
  resolveCliArgsFromVSCodeExecutablePath,
  runTests
} = require('@vscode/test-electron');

async function main() {
  const extensionRoot = path.resolve(__dirname, '..');
  const repositoryRoot = path.resolve(extensionRoot, '..');
  const extensionManifest = JSON.parse(fs.readFileSync(path.join(extensionRoot, 'package.json'), 'utf8'));
  const cliJar = path.join(repositoryRoot, 'cli', 'build', 'libs', 'cplus-cli-0.1.0-SNAPSHOT-all.jar');
  const sdkManifest = path.join(repositoryRoot, 'sdk', 'manifest', 'sdk.toml');
  const vsixPath = path.join(extensionRoot, `${extensionManifest.name}-${extensionManifest.version}.vsix`);
  if (!fs.existsSync(cliJar)) {
    throw new Error(`C+ CLI fat JAR is missing; build it first: ${cliJar}`);
  }
  if (!fs.existsSync(vsixPath)) {
    throw new Error(`Packaged VSIX is missing; run npm run package first: ${vsixPath}`);
  }

  const temporaryRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'cplus vscode extension host '));
  const workspace = path.join(temporaryRoot, 'workspace');
  const extensionDirectory = path.join(temporaryRoot, 'extensions');
  const userDataDirectory = path.join(temporaryRoot, 'user-data');
  const harnessDirectory = path.join(temporaryRoot, 'test-harness');
  fs.mkdirSync(path.join(workspace, '.vscode'), { recursive: true });
  fs.mkdirSync(extensionDirectory, { recursive: true });
  fs.mkdirSync(userDataDirectory, { recursive: true });
  fs.mkdirSync(harnessDirectory, { recursive: true });
  fs.writeFileSync(path.join(workspace, '.vscode', 'settings.json'), JSON.stringify({
    'cplus.server.jarPath': cliJar,
    'cplus.server.sdkManifest': sdkManifest,
    'cplus.server.javaPath': process.env.JAVA_HOME
      ? path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')
      : 'java',
    'cplus.server.args': ['lsp'],
    'cplus.server.cwd': workspace
  }, null, 2));
  fs.writeFileSync(path.join(harnessDirectory, 'package.json'), JSON.stringify({
    name: 'cplus-extension-host-test-harness',
    publisher: 'cplus-test',
    version: '1.0.0',
    engines: { vscode: '^1.85.0' },
    main: './extension.js'
  }, null, 2));
  fs.writeFileSync(path.join(harnessDirectory, 'extension.js'), 'module.exports = { activate() {} };\n');

  try {
    const vscodeExecutablePath = await downloadAndUnzipVSCode();
    const [cliPath, ...cliArgs] = resolveCliArgsFromVSCodeExecutablePath(vscodeExecutablePath);
    const install = spawnSync(cliPath, [
      ...cliArgs,
      '--extensions-dir', extensionDirectory,
      '--user-data-dir', userDataDirectory,
      '--install-extension', vsixPath,
      '--force'
    ], { encoding: 'utf8', timeout: 120000 });
    if (install.status !== 0) {
      throw new Error(`VS Code could not install the packaged C+ VSIX:\n${install.stdout}\n${install.stderr}`);
    }

    const exitCode = await runTests({
      vscodeExecutablePath,
      extensionDevelopmentPath: harnessDirectory,
      extensionTestsPath: path.join(__dirname, 'extension-host.js'),
      extensionTestsEnv: { CPLUS_TEST_EXTENSIONS_DIR: extensionDirectory },
      launchArgs: [workspace, '--extensions-dir', extensionDirectory, '--user-data-dir', userDataDirectory],
      reuseMachineInstall: true
    });
    if (exitCode !== 0) {
      throw new Error(`VS Code extension host exited with status ${exitCode}`);
    }
  } finally {
    if (path.basename(temporaryRoot).startsWith('cplus vscode extension host ')) {
      fs.rmSync(temporaryRoot, { recursive: true, force: true });
    }
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
