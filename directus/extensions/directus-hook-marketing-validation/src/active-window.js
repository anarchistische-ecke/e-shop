const WINDOW_COLLECTIONS = new Set(['campaign', 'banner']);

function hasOwn(value, key) {
  return Object.prototype.hasOwnProperty.call(value || {}, key);
}

function parseOptionalDate(value, field) {
  if (value === null || value === undefined || value === '') return null;
  const timestamp = Date.parse(String(value));
  if (!Number.isFinite(timestamp)) {
    return {
      error: `Поле «${field}» должно содержать корректную дату и время UTC`,
    };
  }
  return { timestamp };
}

export function activeWindowError(payload, existing = {}) {
  const activeFrom = hasOwn(payload, 'active_from')
    ? payload.active_from
    : existing.active_from;
  const activeTo = hasOwn(payload, 'active_to')
    ? payload.active_to
    : existing.active_to;

  const from = parseOptionalDate(activeFrom, 'Начало показа');
  const to = parseOptionalDate(activeTo, 'Окончание показа');

  if (from?.error) return from.error;
  if (to?.error) return to.error;
  if (from?.timestamp !== undefined
      && from?.timestamp !== null
      && to?.timestamp !== undefined
      && to?.timestamp !== null
      && to.timestamp <= from.timestamp) {
    return 'Окончание показа должно быть позже начала показа';
  }
  return '';
}

export function isWindowCollection(collection) {
  return WINDOW_COLLECTIONS.has(collection);
}
