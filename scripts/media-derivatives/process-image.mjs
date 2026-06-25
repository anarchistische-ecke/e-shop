import fs from 'node:fs/promises';
import path from 'node:path';
import sharp from 'sharp';
import { loadDerivativeConfig } from './config.mjs';

const sourcePath = process.argv[2];
const outputDirectory = process.argv[3];
const maxPixels = Number(process.argv[4] || '100000000');

if (!sourcePath || !outputDirectory) {
  throw new Error('Usage: process-image.mjs <source> <output-directory> [max-pixels]');
}

sharp.cache(false);
sharp.concurrency(1);

const sourceHeader = await readHeader(sourcePath);
const signatureFormat = detectSignature(sourceHeader);
if (!signatureFormat) {
  throw new Error('Unsupported image signature. Only JPEG, PNG, and WebP are allowed.');
}

const metadata = await sharp(sourcePath, {
  failOn: 'error',
  limitInputPixels: maxPixels,
  sequentialRead: true,
}).metadata();

const sourceFormat = normalizeFormat(metadata.format);
if (!sourceFormat || sourceFormat !== signatureFormat) {
  throw new Error(`Image signature does not match decoded format (${signatureFormat}/${metadata.format || 'unknown'}).`);
}
if (!metadata.width || !metadata.height) {
  throw new Error('Image dimensions could not be determined.');
}
if (metadata.width * metadata.height > maxPixels) {
  throw new Error(`Image exceeds the ${maxPixels} pixel safety limit.`);
}

await fs.mkdir(outputDirectory, { recursive: true });
const config = loadDerivativeConfig();
const files = [];

for (const width of config.widths) {
  for (const [format, formatConfig] of Object.entries(config.formats)) {
    const filename = `w${width}.${format}`;
    const outputPath = path.join(outputDirectory, filename);
    const pipeline = sharp(sourcePath, {
      failOn: 'error',
      limitInputPixels: maxPixels,
      sequentialRead: true,
    }).rotate().resize({ width, withoutEnlargement: true });
    await pipeline[format]({ quality: Number(formatConfig.quality) }).toFile(outputPath);
    files.push({
      width,
      format,
      contentType: formatConfig.contentType,
      path: outputPath,
    });
  }
}

process.stdout.write(JSON.stringify({
  sourceFormat,
  contentType: sourceContentType(sourceFormat),
  extension: sourceFormat === 'jpeg' ? 'jpg' : sourceFormat,
  width: metadata.width,
  height: metadata.height,
  files,
}));

async function readHeader(filename) {
  const file = await fs.open(filename, 'r');
  try {
    const buffer = Buffer.alloc(16);
    const { bytesRead } = await file.read(buffer, 0, buffer.length, 0);
    return buffer.subarray(0, bytesRead);
  } finally {
    await file.close();
  }
}

function detectSignature(buffer) {
  if (buffer.length >= 3 && buffer[0] === 0xff && buffer[1] === 0xd8 && buffer[2] === 0xff) {
    return 'jpeg';
  }
  if (
    buffer.length >= 8 &&
    buffer.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))
  ) {
    return 'png';
  }
  if (
    buffer.length >= 12 &&
    buffer.subarray(0, 4).toString('ascii') === 'RIFF' &&
    buffer.subarray(8, 12).toString('ascii') === 'WEBP'
  ) {
    return 'webp';
  }
  return '';
}

function normalizeFormat(format) {
  if (format === 'jpg') return 'jpeg';
  return ['jpeg', 'png', 'webp'].includes(format) ? format : '';
}

function sourceContentType(format) {
  if (format === 'jpeg') return 'image/jpeg';
  return `image/${format}`;
}
