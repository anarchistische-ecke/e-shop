import { HeadObjectCommand, PutObjectCommand, S3Client } from '@aws-sdk/client-s3';
import sharp from 'sharp';
import { Agent, setGlobalDispatcher } from 'undici';
import { derivativeKey, loadDerivativeConfig } from './config.mjs';

const derivativeConfig = loadDerivativeConfig();

function readEnv(name, fallback = '') {
  return process.env[name] || fallback;
}

const apiBase = readEnv('API_BASE', 'http://localhost:8080').replace(/\/+$/, '');
const apiToken = readEnv('API_TOKEN');
const productId = readEnv('PRODUCT_ID').trim();
const bucket = readEnv('MEDIA_DERIVATIVES_BUCKET', readEnv('YANDEX_STORAGE_BUCKET', ''));
const endpoint = readEnv('YANDEX_STORAGE_ENDPOINT', 'https://storage.yandexcloud.net');
const accessKeyId = readEnv('YANDEX_STORAGE_KEY');
const secretAccessKey = readEnv('YANDEX_STORAGE_SECRET');
const pathPrefix = readEnv('MEDIA_DERIVATIVES_PATH_PREFIX', 'media').replace(/^\/+|\/+$/g, '') || 'media';
const dryRun = process.argv.includes('--dry-run') || readEnv('DRY_RUN') === 'true';
const fetchTimeoutMs = Number(readEnv('FETCH_TIMEOUT_MS', '600000'));
const fetchAttempts = Math.max(1, Number(readEnv('FETCH_ATTEMPTS', '3')));

setGlobalDispatcher(new Agent({
  bodyTimeout: fetchTimeoutMs,
  headersTimeout: Math.min(fetchTimeoutMs, 120000),
  connect: {
    timeout: 30000,
  },
}));

if (!bucket || !accessKeyId || !secretAccessKey) {
  throw new Error('Set MEDIA_DERIVATIVES_BUCKET or YANDEX_STORAGE_BUCKET, plus YANDEX_STORAGE_KEY and YANDEX_STORAGE_SECRET.');
}

const s3 = new S3Client({
  region: readEnv('YANDEX_STORAGE_REGION', 'ru-central1'),
  endpoint,
  credentials: { accessKeyId, secretAccessKey },
});

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function fetchWithRetry(url, options = {}, label = url) {
  let lastError;
  for (let attempt = 1; attempt <= fetchAttempts; attempt += 1) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), fetchTimeoutMs);
    try {
      const response = await fetch(url, {
        ...options,
        signal: controller.signal,
      });
      clearTimeout(timeout);
      return response;
    } catch (error) {
      clearTimeout(timeout);
      lastError = error;
      const message = error?.cause?.code || error?.message || String(error);
      console.warn(`[warn] ${label} failed attempt ${attempt}/${fetchAttempts}: ${message}`);
      if (attempt < fetchAttempts) {
        await sleep(1000 * attempt);
      }
    }
  }
  throw lastError;
}

async function fetchJson(path) {
  console.log(`fetching ${path}`);
  const response = await fetchWithRetry(`${apiBase}${path}`, {
    headers: apiToken ? { Authorization: `Bearer ${apiToken}` } : {},
  }, path);
  if (!response.ok) {
    throw new Error(`Failed to fetch ${path}: ${response.status} ${response.statusText}`);
  }
  return response.json();
}

function extractObjectKey(url = '') {
  const value = String(url || '').trim();
  if (!value) return '';
  let pathname = value;
  try {
    pathname = new URL(value).pathname;
  } catch {
  }
  for (const marker of ['/products/', '/categories/']) {
    const index = pathname.indexOf(marker);
    if (index >= 0) {
      return pathname.slice(index + 1).replace(/^\/+/, '');
    }
  }
  return '';
}

async function objectExists(key) {
  try {
    await s3.send(new HeadObjectCommand({ Bucket: bucket, Key: key }));
    return true;
  } catch {
    return false;
  }
}

async function uploadDerivative({ key, body, contentType }) {
  if (dryRun) {
    console.log(`[dry-run] upload s3://${bucket}/${key}`);
    return;
  }
  if (await objectExists(key)) {
    console.log(`skip existing s3://${bucket}/${key}`);
    return;
  }
  await s3.send(new PutObjectCommand({
    Bucket: bucket,
    Key: key,
    Body: body,
    ContentType: contentType,
    CacheControl: derivativeConfig.cacheControl,
    ACL: 'public-read',
  }));
  console.log(`uploaded s3://${bucket}/${key}`);
}

async function processImage(image) {
  const originalUrl = image.url;
  const objectKey = image.objectKey || extractObjectKey(originalUrl);
  if (!originalUrl || !objectKey) {
    return;
  }

  console.log(`processing ${objectKey}`);
  const response = await fetchWithRetry(originalUrl, {}, objectKey);
  if (!response.ok) {
    throw new Error(`Failed to download ${originalUrl}: ${response.status}`);
  }
  const input = Buffer.from(await response.arrayBuffer());
  await sharp(input).metadata();

  // The API advertises every configured width. Keep every key present even when
  // withoutEnlargement leaves a small source at its original pixel dimensions.
  for (const width of derivativeConfig.widths) {
    for (const [format, config] of Object.entries(derivativeConfig.formats)) {
      const pipeline = sharp(input).rotate().resize({ width, withoutEnlargement: true });
      const body = await pipeline[format]({ quality: config.quality }).toBuffer();
      await uploadDerivative({
        key: derivativeKey(pathPrefix, objectKey, width, format),
        body,
        contentType: config.contentType,
      });
    }
  }
}

function collectImages({ products, categories }) {
  const images = new Map();
  for (const product of products) {
    for (const image of Array.isArray(product.images) ? product.images : []) {
      const url = image?.originalUrl || image?.url || image?.media?.originalUrl;
      const objectKey = image?.objectKey || extractObjectKey(url);
      if (url && objectKey) {
        images.set(objectKey, { url, objectKey });
      }
    }
  }
  for (const category of categories) {
    const url = category?.imageUrl || category?.media?.originalUrl;
    const objectKey = extractObjectKey(url);
    if (url && objectKey) {
      images.set(objectKey, { url, objectKey });
    }
  }
  return Array.from(images.values());
}

const [products, categories] = productId
  ? [[await fetchJson(`/products/${encodeURIComponent(productId)}`)], []]
  : await Promise.all([
      fetchJson('/products?includeInactive=true'),
      fetchJson('/categories'),
    ]);
const images = collectImages({ products, categories });
console.log(`processing ${images.length} source images`);

for (const image of images) {
  await processImage(image);
}
