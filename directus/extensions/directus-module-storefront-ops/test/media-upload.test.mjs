import test from 'node:test';
import assert from 'node:assert/strict';

import {
  createMediaUploadClient,
  validateMediaFiles,
} from '../src/composables/mediaUploadClient.js';

test('media validation accepts exactly 100 MiB and rejects one byte more', () => {
  assert.doesNotThrow(() => validateMediaFiles([
    { name: 'exact.jpg', type: 'image/jpeg', size: 100 * 1024 * 1024 },
  ]));
  assert.throws(() => validateMediaFiles([
    { name: 'large.jpg', type: 'image/jpeg', size: 100 * 1024 * 1024 + 1 },
  ]), /100 МБ/);
});

test('single upload sends object bytes before completing and polling readiness', async () => {
  const requests = [];
  const uploads = [];
  const file = namedBlob(1024, 'single.jpg', 'image/jpeg');
  const client = createMediaUploadClient({
    bridgeRequest: async (path, options = {}) => {
      requests.push({ path, options });
      if (path === '/media/uploads') {
        return {
          batchId: 'batch-1',
          items: [{
            id: 'upload-1',
            batchId: 'batch-1',
            filename: file.name,
            uploadMethod: 'SINGLE',
            status: 'UPLOADING',
            putUrl: 'https://storage.test/single',
            putHeaders: { 'content-type': 'image/jpeg' },
            totalParts: 1,
            partSize: 0,
          }],
        };
      }
      if (path === '/media/uploads/batches/batch-1') {
        return { batchId: 'batch-1', items: [{ id: 'upload-1', filename: file.name, status: 'READY' }] };
      }
      return {};
    },
    xhrFactory: () => new FakeXhr(uploads),
  });

  const result = await client.uploadBatch({
    targetType: 'PRODUCT',
    entityId: 'product-1',
    files: [{ file }],
  });

  assert.equal(result.items[0].status, 'READY');
  assert.equal(uploads.length, 1);
  assert.equal(uploads[0].url, 'https://storage.test/single');
  assert.ok(requests.some((request) => request.path === '/media/uploads/upload-1/complete'));
});

test('upload batch uses sniffed JPEG type for a misnamed WebP file', async () => {
  const requests = [];
  const uploads = [];
  const file = namedBlobFromBytes([0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10], 'IMG_5308.WEBP', 'image/webp');
  const client = createMediaUploadClient({
    bridgeRequest: async (path, options = {}) => {
      requests.push({ path, options });
      if (path === '/media/uploads') {
        return {
          batchId: 'batch-sniffed',
          items: [{
            id: 'upload-sniffed',
            batchId: 'batch-sniffed',
            filename: file.name,
            uploadMethod: 'SINGLE',
            status: 'UPLOADING',
            putUrl: 'https://storage.test/sniffed',
            putHeaders: { 'content-type': options.data.files[0].contentType },
            totalParts: 1,
            partSize: 0,
          }],
        };
      }
      if (path === '/media/uploads/batches/batch-sniffed') {
        return { batchId: 'batch-sniffed', items: [{ id: 'upload-sniffed', filename: file.name, status: 'READY' }] };
      }
      return {};
    },
    xhrFactory: () => new FakeXhr(uploads),
  });

  await client.uploadBatch({
    targetType: 'PRODUCT',
    entityId: 'product-sniffed',
    files: [{ file }],
  });

  const createBatch = requests.find((request) => request.path === '/media/uploads');
  assert.equal(createBatch.options.data.files[0].contentType, 'image/jpeg');
  assert.equal(uploads[0].headers['content-type'], 'image/jpeg');
});

test('multipart upload signs and completes all eight MiB parts', async () => {
  const uploads = [];
  const completed = [];
  const signedParts = [];
  const file = namedBlob(17 * 1024 * 1024, 'large.jpg', 'image/jpeg');
  const client = createMediaUploadClient({
    bridgeRequest: async (path, options = {}) => {
      if (path === '/media/uploads') {
        return {
          batchId: 'batch-2',
          items: [{
            id: 'upload-2',
            batchId: 'batch-2',
            filename: file.name,
            uploadMethod: 'MULTIPART',
            status: 'UPLOADING',
            totalParts: 3,
            partSize: 8 * 1024 * 1024,
          }],
        };
      }
      if (path === '/media/uploads/upload-2/parts') {
        const partNumber = options.data.partNumbers[0];
        signedParts.push(partNumber);
        return { parts: [{ partNumber, url: `https://storage.test/part-${partNumber}`, headers: {} }] };
      }
      if (path === '/media/uploads/upload-2/complete') {
        completed.push(...options.data.parts);
        return {};
      }
      if (path === '/media/uploads/batches/batch-2') {
        return { batchId: 'batch-2', items: [{ id: 'upload-2', filename: file.name, status: 'READY' }] };
      }
      return {};
    },
    xhrFactory: () => new FakeXhr(uploads),
  });

  await client.uploadBatch({
    targetType: 'PRODUCT',
    entityId: 'product-2',
    files: [{ file }],
  });

  assert.deepEqual(signedParts.sort(), [1, 2, 3]);
  assert.deepEqual(completed.map((part) => part.partNumber), [1, 2, 3]);
  assert.equal(uploads.length, 3);
});

test('single upload renews an expired URL and retries without recreating the batch', async () => {
  const uploads = [];
  const requests = [];
  const file = namedBlob(1024, 'renew.jpg', 'image/jpeg');
  let attempt = 0;
  const client = createMediaUploadClient({
    bridgeRequest: async (path, options = {}) => {
      requests.push({ path, options });
      if (path === '/media/uploads') {
        return {
          batchId: 'batch-renew',
          items: [{
            id: 'upload-renew',
            filename: file.name,
            uploadMethod: 'SINGLE',
            status: 'UPLOADING',
            putUrl: 'https://storage.test/expired',
            putHeaders: {},
            totalParts: 1,
            partSize: 0,
          }],
        };
      }
      if (path === '/media/uploads/upload-renew/url') {
        return { url: 'https://storage.test/renewed', headers: {} };
      }
      if (path === '/media/uploads/batches/batch-renew') {
        return { batchId: 'batch-renew', items: [{ id: 'upload-renew', filename: file.name, status: 'READY' }] };
      }
      return {};
    },
    xhrFactory: () => new FakeXhr(uploads, () => {
      attempt += 1;
      return attempt === 1 ? 403 : 200;
    }),
  });

  await client.uploadBatch({
    targetType: 'PRODUCT',
    entityId: 'product-renew',
    files: [{ file }],
  });

  assert.deepEqual(uploads.map((upload) => upload.url), [
    'https://storage.test/expired',
    'https://storage.test/renewed',
  ]);
  assert.equal(requests.filter((request) => request.path === '/media/uploads').length, 1);
});

test('multipart upload re-signs only an interrupted part', async () => {
  const uploads = [];
  const signedParts = [];
  const partAttempts = new Map();
  const file = namedBlob(17 * 1024 * 1024, 'interrupted.jpg', 'image/jpeg');
  const client = createMediaUploadClient({
    bridgeRequest: async (path, options = {}) => {
      if (path === '/media/uploads') {
        return {
          batchId: 'batch-interrupted',
          items: [{
            id: 'upload-interrupted',
            filename: file.name,
            uploadMethod: 'MULTIPART',
            status: 'UPLOADING',
            totalParts: 3,
            partSize: 8 * 1024 * 1024,
          }],
        };
      }
      if (path === '/media/uploads/upload-interrupted/parts') {
        const partNumber = options.data.partNumbers[0];
        signedParts.push(partNumber);
        return {
          parts: [{
            partNumber,
            url: `https://storage.test/interrupted-${partNumber}-${signedParts.length}`,
            headers: {},
          }],
        };
      }
      if (path === '/media/uploads/batches/batch-interrupted') {
        return {
          batchId: 'batch-interrupted',
          items: [{ id: 'upload-interrupted', filename: file.name, status: 'READY' }],
        };
      }
      return {};
    },
    xhrFactory: () => new FakeXhr(uploads, (url) => {
      const part = Number(url.split('-').at(-2));
      const attempts = (partAttempts.get(part) || 0) + 1;
      partAttempts.set(part, attempts);
      return part === 2 && attempts === 1 ? 500 : 200;
    }),
  });

  await client.uploadBatch({
    targetType: 'PRODUCT',
    entityId: 'product-interrupted',
    files: [{ file }],
  });

  assert.equal(signedParts.filter((part) => part === 2).length, 2);
  assert.equal(signedParts.filter((part) => part === 1).length, 1);
  assert.equal(signedParts.filter((part) => part === 3).length, 1);
});

class FakeXhr {
  constructor(uploads, statusResolver = () => 200) {
    this.uploads = uploads;
    this.upload = {};
    this.statusResolver = statusResolver;
    this.status = 0;
    this.headers = {};
  }

  open(method, url) {
    this.method = method;
    this.url = url;
  }

  setRequestHeader(name, value) {
    this.headers[name] = value;
  }

  getResponseHeader(name) {
    return name.toLowerCase() === 'etag' ? `"etag-${this.url.split('-').at(-1)}"` : null;
  }

  send(blob) {
    this.uploads.push({ method: this.method, url: this.url, size: blob.size, headers: this.headers });
    this.upload.onprogress?.({ lengthComputable: true, loaded: blob.size, total: blob.size });
    queueMicrotask(() => {
      this.status = this.statusResolver(this.url);
      this.onload?.();
    });
  }
}

function namedBlob(size, name, type) {
  const blob = new Blob([new Uint8Array(size)], { type });
  Object.defineProperty(blob, 'name', { value: name });
  return blob;
}

function namedBlobFromBytes(bytes, name, type) {
  const blob = new Blob([new Uint8Array(bytes)], { type });
  Object.defineProperty(blob, 'name', { value: name });
  return blob;
}
