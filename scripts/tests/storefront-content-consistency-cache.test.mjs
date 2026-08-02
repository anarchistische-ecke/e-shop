import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createServer } from 'node:http';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const checkScript = fileURLToPath(
  new URL('../storefront-content-consistency-check.mjs', import.meta.url)
);

test('storefront consistency accepts shared public-page caching and private no-store pages', async () => {
  const fixture = await startFixture({ homeCacheControl: sharedCache(60) });
  try {
    const result = await runCheck(fixture);
    assert.equal(result.code, 0, result.stderr || result.stdout);
    assert.match(result.stdout, /Storefront consistency check passed/);
  } finally {
    await fixture.close();
  }
});

test('storefront consistency rejects the former no-store policy on public pages', async () => {
  const fixture = await startFixture({ homeCacheControl: 'no-store' });
  try {
    const result = await runCheck(fixture);
    assert.notEqual(result.code, 0);
    assert.match(result.stderr, /\/ is not public-cacheable/);
  } finally {
    await fixture.close();
  }
});

async function startFixture({ homeCacheControl }) {
  const api = createServer((request, response) => {
    const url = new URL(request.url || '/', 'http://api.test');
    response.setHeader('content-type', 'application/json');

    if (url.pathname === '/products') {
      response.setHeader('x-page', '0');
      response.setHeader('x-total-count', '0');
      response.setHeader('x-total-pages', '0');
      response.end('[]');
      return;
    }
    if (url.pathname === '/categories') {
      response.end('[]');
      return;
    }
    if (url.pathname === '/content/pages/home') {
      response.end('{"sections":[]}');
      return;
    }

    response.statusCode = 404;
    response.end('{"error":"not found"}');
  });

  const storefront = createServer((request, response) => {
    const url = new URL(request.url || '/', 'http://storefront.test');
    if (url.pathname === '/assets/app.js') {
      response.setHeader('cache-control', 'public, max-age=31536000, immutable');
      response.end('console.log("fixture");');
      return;
    }

    if (['/sitemap.xml', '/cart', '/checkout', '/account'].includes(url.pathname)) {
      response.setHeader('cache-control', 'no-store');
      response.end(url.pathname === '/sitemap.xml' ? '<urlset />' : '<main>Private</main>');
      return;
    }

    if (url.pathname === '/') {
      response.setHeader('cache-control', homeCacheControl);
      response.end('<html><script src="/assets/app.js"></script></html>');
      return;
    }
    if (url.pathname === '/catalog') {
      response.setHeader('cache-control', sharedCache(60));
      response.end('<main>Catalogue</main>');
      return;
    }

    response.statusCode = 404;
    response.end('Not found');
  });

  await Promise.all([listen(api), listen(storefront)]);
  const apiAddress = api.address();
  const storefrontAddress = storefront.address();

  return {
    apiBase: `http://127.0.0.1:${apiAddress.port}`,
    storefrontBase: `http://127.0.0.1:${storefrontAddress.port}`,
    close: () => Promise.all([close(api), close(storefront)]),
  };
}

function runCheck(fixture) {
  return new Promise((resolve, reject) => {
    const child = spawn(process.execPath, [checkScript], {
      env: {
        ...process.env,
        API_BASE: fixture.apiBase,
        STOREFRONT_BASE: fixture.storefrontBase,
      },
      stdio: ['ignore', 'pipe', 'pipe'],
    });

    let stdout = '';
    let stderr = '';
    child.stdout.setEncoding('utf8');
    child.stderr.setEncoding('utf8');
    child.stdout.on('data', (chunk) => {
      stdout += chunk;
    });
    child.stderr.on('data', (chunk) => {
      stderr += chunk;
    });
    child.on('error', reject);
    child.on('close', (code) => resolve({ code, stdout, stderr }));
  });
}

function sharedCache(seconds) {
  return `public, max-age=0, s-maxage=${seconds}, stale-while-revalidate=300`;
}

function listen(server) {
  return new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', resolve);
  });
}

function close(server) {
  return new Promise((resolve, reject) => {
    server.close((error) => (error ? reject(error) : resolve()));
  });
}
