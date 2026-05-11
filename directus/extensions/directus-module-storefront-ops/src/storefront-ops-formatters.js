export function filterCollection(items, query, getters) {
  if (!query) {
    return items || [];
  }
  const normalized = query.toLowerCase();
  return (items || []).filter((item) => {
    return getters.some((getter) => {
      const value = getter(item);
      return value && String(value).toLowerCase().includes(normalized);
    });
  });
}

export function formatCategoryLabel(option) {
  const depth = Number(option.depth || 0);
  const prefix = depth > 0 ? `${'· '.repeat(depth)}` : '';
  return `${prefix}${option.name}`;
}

export function overlayStatusLabel(status) {
  const value = String(status || '').toLowerCase();
  if (value === 'published') return 'Опубликован';
  if (value === 'in_review') return 'На проверке';
  if (value === 'archived') return 'В архиве';
  if (value === 'draft') return 'Черновик';
  return 'Без статуса';
}

export function formatMoney(price) {
  if (!price?.amount && price?.amount !== 0) {
    return 'Цена не задана';
  }
  const currency = price.currency || 'RUB';
  return `${price.amount} ${currency}`;
}

export function moneyMinorAmount(price) {
  const amount = Number(price?.amount ?? 0);
  return Number.isFinite(amount) ? amount : 0;
}

export function formatMinorMoney(amount, currency = 'RUB') {
  if (amount === null || amount === undefined || amount === '') {
    return 'Не задано';
  }
  const numeric = Number(amount);
  if (!Number.isFinite(numeric)) {
    return 'Не задано';
  }
  return `${(numeric / 100).toLocaleString('ru-RU')} ${currency}`;
}

export function formatDateTime(value) {
  if (!value) {
    return 'Не задано';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return 'Не задано';
  }
  return new Intl.DateTimeFormat('ru-RU', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(date);
}

export function toIsoDateTime(value) {
  if (!value) {
    return null;
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

export function toDatetimeLocal(value) {
  if (!value) {
    return '';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '';
  }
  const offset = date.getTimezoneOffset();
  const localDate = new Date(date.getTime() - offset * 60 * 1000);
  return localDate.toISOString().slice(0, 16);
}

export function compactParams(params) {
  return Object.fromEntries(
    Object.entries(params || {}).filter(([, value]) => value !== null && value !== undefined && String(value).trim() !== '')
  );
}

export function formatPercent(value) {
  const numeric = Number(value || 0) * 100;
  return `${numeric.toFixed(1)}%`;
}

export function cloneSpecifications(value) {
  return (Array.isArray(value) ? value : []).map((section) => ({
    title: String(section?.title || ''),
    description: String(section?.description || ''),
    items: (Array.isArray(section?.items) ? section.items : []).map((item) => ({
      label: String(item?.label || ''),
      value: String(item?.value || ''),
    })),
  }));
}

export function normalizeSpecificationsForPayload(value) {
  return (Array.isArray(value) ? value : [])
    .map((section) => ({
      title: normalizeNullableText(section?.title),
      description: normalizeNullableText(section?.description),
      items: (Array.isArray(section?.items) ? section.items : [])
        .map((item) => ({
          label: normalizeNullableText(item?.label),
          value: normalizeNullableText(item?.value),
        }))
        .filter((item) => item.label && item.value),
    }))
    .filter((section) => section.title || section.description || section.items.length);
}

export function normalizeNullableNumber(value) {
  if (value === '' || value === null || value === undefined) {
    return null;
  }
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : null;
}

export function minorToMajor(value) {
  const numeric = normalizeNullableNumber(value);
  return numeric === null ? null : numeric / 100;
}

export function majorToMinor(value) {
  const numeric = normalizeNullableNumber(value);
  return numeric === null ? null : Math.round(numeric * 100);
}

export function normalizeNullableText(value) {
  const trimmed = String(value || '').trim();
  return trimmed ? trimmed : null;
}

export function nextIdempotencyKey() {
  return crypto.randomUUID();
}

