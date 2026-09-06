#!/usr/bin/env node
// Capture a new, versioned local test run. Raw logs remain local; share only reviewed summaries.
import fs from 'node:fs';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { spawn, execFileSync } from 'node:child_process';

const version = process.argv[2];
if (!version || !/^[a-zA-Z0-9][a-zA-Z0-9-]{0,79}$/.test(version)) {
  throw new Error('Usage: node scripts/capture-atlas-evidence.mjs <new-run-version>');
}
const root = process.cwd();
const local = path.join(root, 'target/atlas-evidence', version);
const shared = path.join(root, 'docs/samples/evidence', version);
if (fs.existsSync(local) || fs.existsSync(shared)) throw new Error('Run version already exists; use a new version.');
fs.mkdirSync(local, { recursive: true });
fs.mkdirSync(shared, { recursive: true });
const git = (...args) => execFileSync('git', args, { cwd: root, encoding: 'utf8' }).trim();
const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');
const sourceCommit = git('rev-parse', 'HEAD');
const sourceChanges = git('status', '--porcelain', '--', 'src', 'pom.xml', 'frontend/src', 'frontend/package.json');
const originalPaths = git('ls-files', 'docs/samples', 'docs/assets').split('\n').filter(Boolean);
const historical = originalPaths.map(file => {
  const original = execFileSync('git', ['show', `${sourceCommit}:${file}`], { cwd: root });
  const current = fs.readFileSync(path.join(root, file));
  if (!original.equals(current)) throw new Error(`Historical artifact changed: ${file}`);
  return { path: file, bytes: current.length, sha256: sha256(current) };
});
fs.writeFileSync(path.join(shared, 'historical-manifest.json'), JSON.stringify({
  sourceCommit, capturedAt: new Date().toISOString(), interpretation: 'Original tracked samples and assets; unchanged bytes do not validate their claims.', files: historical
}, null, 2) + '\n');

const tests = ['ExcelImportWorkflowTest', 'ManualTaskWorkflowTest', 'DecisionEngineTest',
  'ReleaseFlowProgressionServiceTest', 'AutoExecutionServiceTest', 'AnsibleExecutionAdapterTest',
  'ExecutionTargetResolverTest', 'ExternalExecutionMonitorServiceTest', 'TaskExecutionHistoryServiceTest',
  'AgentBoundaryGuardTest'];
const reports = path.join(local, 'surefire-reports');
const args = ['-B', '-ntp', 'test', `-Dtest=${tests.join(',')}`];
const stdoutPath = path.join(local, 'stdout.txt');
const stderrPath = path.join(local, 'stderr.txt');
const stdout = fs.openSync(stdoutPath, 'wx');
const stderr = fs.openSync(stderrPath, 'wx');
const startedAt = new Date().toISOString();
console.log(`Running ${tests.length} selected test classes; preserving raw output in target/atlas-evidence/${version}.`);
const exitCode = await new Promise((resolve, reject) => {
  // Maven's .cmd launcher needs the Windows shell; all arguments above are locally constructed.
  const child = spawn(process.platform === 'win32' ? 'mvn.cmd' : 'mvn', args,
    { cwd: root, stdio: ['ignore', stdout, stderr], shell: process.platform === 'win32' });
  child.on('error', reject);
  child.on('exit', code => resolve(code ?? 1));
}).finally(() => { fs.closeSync(stdout); fs.closeSync(stderr); });
// Surefire writes its default report directory. Copy only selected, freshly written
// reports to this immutable run directory; never aggregate a previous test run.
const defaultReports = path.join(root, 'target/surefire-reports');
fs.mkdirSync(reports, { recursive: true });
if (fs.existsSync(defaultReports)) {
  for (const name of fs.readdirSync(defaultReports)) {
    const selected = tests.some(test => name.endsWith(`.${test}.xml`));
    const file = path.join(defaultReports, name);
    if (selected && name.startsWith('TEST-') && fs.statSync(file).mtimeMs >= Date.parse(startedAt)) {
      fs.copyFileSync(file, path.join(reports, name), fs.constants.COPYFILE_EXCL);
    }
  }
}
const names = fs.existsSync(reports) ? fs.readdirSync(reports).filter(n => /^TEST-.*\.xml$/.test(n)).sort() : [];
const suites = names.map(name => {
  const xml = fs.readFileSync(path.join(reports, name), 'utf8');
  const header = xml.match(/<testsuite\s[^>]+>/)?.[0] ?? '';
  const attr = key => header.match(new RegExp(`\\b${key}="([^"]*)"`))?.[1];
  return { name: attr('name'), tests: Number(attr('tests')), failures: Number(attr('failures')),
    errors: Number(attr('errors')), skipped: Number(attr('skipped')), sha256: sha256(xml) };
});
const totals = ['tests', 'failures', 'errors', 'skipped'].reduce((acc, key) =>
  ({ ...acc, [key]: suites.reduce((sum, suite) => sum + suite[key], 0) }), {});
const complete = tests.every(name => suites.some(suite => suite.name?.endsWith(`.${name}`))) &&
  suites.every(suite => Number.isFinite(suite.tests) && suite.tests > 0);
const result = { version, sourceCommit, sourceChanges: sourceChanges || null, startedAt,
  finishedAt: new Date().toISOString(), command: `mvn -B -ntp test -Dtest=${tests.join(',')}`,
  archivedReports: `target/atlas-evidence/${version}/surefire-reports`, exitCode,
  status: exitCode === 0 && complete && totals.failures === 0 && totals.errors === 0 && totals.skipped === 0 ? 'passed' : 'failed-or-incomplete',
  scope: 'Selected local automated tests using synthetic fixtures and mocked external calls; not external deployment, UI UAT, production acceptance or benefit measurement.',
  totals, suites, rawArtifacts: [stdoutPath, stderrPath].map(file => ({
    path: path.relative(root, file), sha256: sha256(fs.readFileSync(file)), distribution: 'local-only; review before sharing'
  })) };
fs.writeFileSync(path.join(shared, 'test-results.json'), JSON.stringify(result, null, 2) + '\n');
console.log(JSON.stringify({ status: result.status, ...totals, summary: path.relative(root, path.join(shared, 'test-results.json')) }));
process.exitCode = result.status === 'passed' ? 0 : 1;
