import { HeadObjectCommand, PutObjectCommand, S3Client } from '@aws-sdk/client-s3';
import sharp from 'sharp';
import { Agent, setGlobalDispatcher } from 'undici';

const WIDTHS = [96, 160, 320, 480, 640, 768, 960, 1280, 1600];
const FORMATS = {
  avif: { quality: 64, contentType: 'image/avif' },
  webp: { quality: 68, contentType: 'image/webp' },
  jpeg: { quality: 82, contentType: 'image/jpeg' },
};
const CACHE_CONTROL = 'public, max-age=31536000, immutable';

function readEnv(name, fallback = '') {
  return process.env[name] || fallback;
}

const apiBase = readEnv('API_BASE', 'http://localhost:8080').replace(/\/+$/, '');
const apiToken = readEnv('API_TOKEN');
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

function stripExtension(objectKey) {
  const index = objectKey.lastIndexOf('.');
  if (index <= objectKey.lastIndexOf('/')) return objectKey;
  return objectKey.slice(0, index);
}

function derivativeKey(objectKey, width, format) {
  return `${pathPrefix}/${stripExtension(objectKey)}/w${width}.${format}`;
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
    CacheControl: CACHE_CONTROL,
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
  const metadata = await sharp(input).metadata();
  const maxWidth = metadata.width || WIDTHS[WIDTHS.length - 1];
  const widths = WIDTHS.filter((width) => width <= Math.max(maxWidth, WIDTHS[0]));

  for (const width of widths) {
    for (const [format, config] of Object.entries(FORMATS)) {
      const pipeline = sharp(input).rotate().resize({ width, withoutEnlargement: true });
      const body = await pipeline[format]({ quality: config.quality }).toBuffer();
      await uploadDerivative({
        key: derivativeKey(objectKey, width, format),
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

const [products, categories] = await Promise.all([
  fetchJson('/products?includeInactive=true'),
  fetchJson('/categories'),
]);
const images = collectImages({ products, categories });
console.log(`processing ${images.length} source images`);

for (const image of images) {
  await processImage(image);
}
