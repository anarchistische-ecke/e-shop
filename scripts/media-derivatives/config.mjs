import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const defaultConfigPath = path.resolve(
  scriptDirectory,
  '../../api/src/main/resources/media-derivatives.json'
);

export function loadDerivativeConfig() {
  const configPath = process.env.MEDIA_DERIVATIVES_CONFIG || defaultConfigPath;
  const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
  return {
    widths: config.widths.map(Number),
    formats: config.formats,
    cacheControl: config.cacheControl,
  };
}

export function stripExtension(objectKey) {
  const index = objectKey.lastIndexOf('.');
  if (index <= objectKey.lastIndexOf('/')) return objectKey;
  return objectKey.slice(0, index);
}

export function derivativeKey(pathPrefix, objectKey, width, format) {
  return `${pathPrefix}/${stripExtension(objectKey)}/w${width}.${format}`;
}
