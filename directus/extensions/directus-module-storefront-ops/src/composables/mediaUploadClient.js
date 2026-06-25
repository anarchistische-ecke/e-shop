const TERMINAL_STATUSES = new Set(['READY', 'FAILED', 'ABORTED', 'EXPIRED']);
const ALLOWED_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);
const MAX_FILE_SIZE = 100 * 1024 * 1024;

export function createMediaUploadClient({ bridgeRequest, xhrFactory = () => new XMLHttpRequest() }) {
  async function uploadBatch({
    targetType,
    entityId,
    files,
    onState = () => {},
  }) {
    validateFiles(files);
    const created = await bridgeRequest('/media/uploads', {
      method: 'POST',
      data: {
        targetType,
        entityId,
        files: files.map(({ file, variantId, position }) => ({
          filename: file.name,
          contentType: normalizeContentType(file.type),
          size: file.size,
          variantId: variantId || null,
          position: Number.isInteger(position) ? position : null,
        })),
      },
    });
    const progress = new Map(created.items.map((item) => [item.id, 0]));
    publish(created.items, progress, onState);

    await runWithConcurrency(created.items, 2, async (item, index) => {
      const descriptor = files[index];
      try {
        if (item.uploadMethod === 'MULTIPART') {
          await uploadMultipart(item, descriptor.file, (loaded) => {
            progress.set(item.id, loaded / descriptor.file.size);
            publish(created.items, progress, onState);
          });
        } else {
          await uploadSingle(item, descriptor.file, (loaded) => {
            progress.set(item.id, loaded / descriptor.file.size);
            publish(created.items, progress, onState);
          });
        }
        progress.set(item.id, 1);
        item.status = 'QUEUED';
        publish(created.items, progress, onState);
      } catch (error) {
        try {
          await bridgeRequest(`/media/uploads/${item.id}`, { method: 'DELETE' });
        } catch {
        }
        item.status = 'ABORTED';
        item.error = error?.message || 'Не удалось загрузить файл.';
        publish(created.items, progress, onState);
      }
    });

    let status = await bridgeRequest(`/media/uploads/batches/${created.batchId}`);
    publish(status.items, progress, onState);
    while (!status.items.every((item) => TERMINAL_STATUSES.has(item.status))) {
      await delay(2000);
      status = await bridgeRequest(`/media/uploads/batches/${created.batchId}`);
      publish(status.items, progress, onState);
    }
    return status;
  }

  async function uploadSingle(item, file, onProgress) {
    let signed = { url: item.putUrl, headers: item.putHeaders || {} };
    let lastError;
    for (let attempt = 0; attempt < 3; attempt += 1) {
      if (attempt > 0) {
        signed = await bridgeRequest(`/media/uploads/${item.id}/url`, { method: 'POST' });
      }
      try {
        await uploadBlob(signed.url, file, signed.headers, onProgress);
        await bridgeRequest(`/media/uploads/${item.id}/complete`, { method: 'POST', data: {} });
        return;
      } catch (error) {
        lastError = error;
      }
    }
    throw lastError;
  }

  async function uploadMultipart(item, file, onProgress) {
    const parts = Array.from({ length: item.totalParts }, (_, index) => {
      const partNumber = index + 1;
      const start = index * item.partSize;
      const end = Math.min(file.size, start + item.partSize);
      return { partNumber, blob: file.slice(start, end), size: end - start, loaded: 0 };
    });
    const completed = [];

    await runWithConcurrency(parts, 3, async (part) => {
      let lastError;
      for (let attempt = 0; attempt < 3; attempt += 1) {
        try {
          const signedResponse = await bridgeRequest(`/media/uploads/${item.id}/parts`, {
            method: 'POST',
            data: { partNumbers: [part.partNumber] },
          });
          const signed = signedResponse.parts[0];
          const eTag = await uploadBlob(signed.url, part.blob, signed.headers, (loaded) => {
            part.loaded = loaded;
            onProgress(parts.reduce((sum, current) => sum + current.loaded, 0));
          });
          completed.push({ partNumber: part.partNumber, eTag });
          return;
        } catch (error) {
          part.loaded = 0;
          lastError = error;
        }
      }
      throw lastError;
    });

    completed.sort((left, right) => left.partNumber - right.partNumber);
    await bridgeRequest(`/media/uploads/${item.id}/complete`, {
      method: 'POST',
      data: { parts: completed },
    });
  }

  function uploadBlob(url, blob, headers, onProgress) {
    return new Promise((resolve, reject) => {
      const xhr = xhrFactory();
      xhr.open('PUT', url, true);
      Object.entries(headers || {}).forEach(([name, value]) => xhr.setRequestHeader(name, value));
      xhr.upload.onprogress = (event) => {
        if (event.lengthComputable) onProgress(event.loaded);
      };
      xhr.onerror = () => reject(new Error('Соединение с хранилищем прервано.'));
      xhr.onabort = () => reject(new Error('Загрузка отменена.'));
      xhr.onload = () => {
        if (xhr.status < 200 || xhr.status >= 300) {
          reject(new Error(`Хранилище вернуло HTTP ${xhr.status}.`));
          return;
        }
        resolve(xhr.getResponseHeader('ETag') || '');
      };
      xhr.send(blob);
    });
  }

  return { uploadBatch };
}

export function validateMediaFiles(files) {
  validateFiles(files.map((file) => ({ file })));
}

function validateFiles(files) {
  if (!files.length) {
    throw new Error('Выберите хотя бы один файл.');
  }
  for (const { file } of files) {
    const contentType = normalizeContentType(file.type);
    if (!ALLOWED_TYPES.has(contentType)) {
      throw new Error(`${file.name}: поддерживаются только JPEG, PNG и WebP.`);
    }
    if (file.size > MAX_FILE_SIZE) {
      throw new Error(`${file.name}: размер превышает 100 МБ.`);
    }
  }
}

function normalizeContentType(value) {
  return value === 'image/jpg' ? 'image/jpeg' : String(value || '').toLowerCase();
}

async function runWithConcurrency(items, limit, worker) {
  let nextIndex = 0;
  const runners = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (nextIndex < items.length) {
      const index = nextIndex;
      nextIndex += 1;
      await worker(items[index], index);
    }
  });
  await Promise.all(runners);
}

function publish(items, progress, onState) {
  onState(items.map((item) => ({
    ...item,
    progress: Math.max(0, Math.min(1, progress.get(item.id) || (item.status === 'READY' ? 1 : 0))),
  })));
}

function delay(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}
