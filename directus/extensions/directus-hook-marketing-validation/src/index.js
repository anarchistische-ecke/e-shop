import { InvalidPayloadError } from '@directus/errors';
import { activeWindowError, isWindowCollection } from './active-window.js';

function assertWindow(payload, existing) {
  const reason = activeWindowError(payload, existing);
  if (reason) {
    throw new InvalidPayloadError({ reason });
  }
}

async function validateCreate(payload, meta) {
  if (isWindowCollection(meta.collection)) {
    assertWindow(payload, {});
  }
  return payload;
}

async function validateUpdate(payload, meta, context) {
  if (!isWindowCollection(meta.collection)) return payload;

  const keys = (Array.isArray(meta.keys) ? meta.keys : [meta.keys])
    .filter((key) => key !== null && key !== undefined);
  if (keys.length === 0) {
    assertWindow(payload, {});
    return payload;
  }

  const rows = await context.database(meta.collection)
    .select('id', 'active_from', 'active_to')
    .whereIn('id', keys);

  for (const row of rows) {
    assertWindow(payload, row);
  }
  return payload;
}

export default ({ filter }) => {
  filter('items.create', validateCreate);
  filter('items.update', validateUpdate);
};
