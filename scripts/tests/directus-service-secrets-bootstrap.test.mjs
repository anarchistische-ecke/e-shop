import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const rootDir = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const scriptPath = resolve(rootDir, 'scripts/directus-service-secrets-bootstrap.sh');
const secretKeys = [
  'DIRECTUS_STATIC_TOKEN',
  'DIRECTUS_PREVIEW_TOKEN',
  'CMS_PREVIEW_SECRET',
];

function runBootstrap(envFile) {
  return spawnSync('bash', [scriptPath, '--env-file', envFile], {
    cwd: rootDir,
    encoding: 'utf8',
  });
}

function readAssignments(envFile) {
  return Object.fromEntries(
    readFileSync(envFile, 'utf8')
      .split(/\r?\n/)
      .filter((line) => line && !line.startsWith('#'))
      .map((line) => {
        const separator = line.indexOf('=');
        return [line.slice(0, separator), line.slice(separator + 1)];
      })
  );
}

test('service-secret bootstrap provisions missing values and preserves them on rerun', () => {
  const testDir = mkdtempSync(resolve(tmpdir(), 'directus-service-secrets-'));
  const envFile = resolve(testDir, '.env');
  writeFileSync(
    envFile,
    [
      '# existing deployment configuration',
      'DIRECTUS_STATIC_TOKEN=',
      'DIRECTUS_PREVIEW_TOKEN=',
      'DIRECTUS_PREVIEW_TOKEN=',
      'CMS_PREVIEW_SECRET=',
      'UNCHANGED_VALUE=keep-me',
      '',
    ].join('\n'),
    { mode: 0o644 }
  );

  const firstRun = runBootstrap(envFile);
  assert.equal(firstRun.status, 0, firstRun.stderr || firstRun.stdout);

  const firstAssignments = readAssignments(envFile);
  for (const key of secretKeys) {
    assert.match(firstAssignments[key], /^[0-9a-f]{64}$/);
    assert.equal(
      readFileSync(envFile, 'utf8').split(`${key}=`).length - 1,
      1,
      `${key} should have one assignment`
    );
  }
  assert.equal(new Set(secretKeys.map((key) => firstAssignments[key])).size, 3);
  assert.equal(firstAssignments.UNCHANGED_VALUE, 'keep-me');
  assert.equal(statSync(envFile).mode & 0o077, 0);

  const secondRun = runBootstrap(envFile);
  assert.equal(secondRun.status, 0, secondRun.stderr || secondRun.stdout);
  assert.deepEqual(readAssignments(envFile), firstAssignments);
});

test('production deploy runs Marketing V2 provisioners inside Directus', () => {
  const deploySource = readFileSync(resolve(rootDir, 'scripts/deploy-stack.sh'), 'utf8');

  assert.doesNotMatch(
    deploySource,
    /DIRECTUS_BASE_URL="http:\/\/127\.0\.0\.1:8055"\s+\\\s+node/
  );
  for (const provisioner of [
    'directus-marketing-v2-presets.js',
    'directus-marketing-v2-flows.js',
  ]) {
    assert.match(
      deploySource,
      new RegExp(
        String.raw`compose exec -T[\s\S]*?directus \\\n  node /opt/directus-deploy/scripts/${provisioner.replace('.', String.raw`\.`)}`
      )
    );
  }
});

test('production content migration uses container runtimes and mandatory apply backup', () => {
  const source = readFileSync(
    resolve(rootDir, 'scripts/directus-marketing-v2-migrate-production.sh'),
    'utf8'
  );

  assert.match(source, /STOREFRONT_CONTAINER:\/app\/public\/legal/);
  assert.match(
    source,
    /node \/opt\/directus-deploy\/scripts\/directus-marketing-v2-migrate\.js/
  );
  assert.match(source, /label=com\.docker\.compose\.project=\$\{CURRENT_LIVE_PROJECT\}/);
  assert.match(source, /label=com\.docker\.compose\.service=\$\{service_name\}/);
  assert.match(source, /CURRENT_LIVE_RELEASE_ID:-.*current_checkout_sha/);
  assert.match(source, /load_env_file "\$ENV_FILE"[\s\S]*runtime_set_defaults/);
  assert.match(source, /if \[\[ "\$MODE" == "apply" \]\]; then[\s\S]*directus-db-backup\.sh/);
  assert.match(source, /migration_args\+=\(--assert-idempotent\)/);
  assert.doesNotMatch(source, /\nnode "\$ROOT_DIR\/scripts\/directus-marketing-v2-migrate\.js"/);
});
