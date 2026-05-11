const PAGE_PATHS = {
  home: '/',
  about: '/about',
  delivery: '/info/delivery',
  payment: '/info/payment',
  production: '/info/production',
  legal: '/info/legal',
};

export function normalizeStorefrontBaseUrl(value) {
  return String(value || '').trim().replace(/\/+$/, '');
}

export function buildStorefrontPreviewUrl({ baseUrl, kind, key, id } = {}) {
  const normalizedBaseUrl = normalizeStorefrontBaseUrl(baseUrl);
  const normalizedKind = String(kind || '').trim().toLowerCase();
  const normalizedKey = String(key || '').trim();
  const normalizedId = String(id || '').trim();
  if (!normalizedBaseUrl || !normalizedKind) {
    return '';
  }

  let pathname = '';
  if (normalizedKind === 'page') {
    pathname = PAGE_PATHS[normalizedKey] || `/info/${encodeURIComponent(normalizedKey)}`;
  } else if (normalizedKind === 'product') {
    const routeId = encodeURIComponent(normalizedId || normalizedKey);
    pathname = normalizedKey
      ? `/product/${routeId}/${encodeURIComponent(normalizedKey)}`
      : `/product/${routeId}`;
  } else if (normalizedKind === 'category') {
    pathname = `/category/${encodeURIComponent(normalizedKey)}`;
  }

  if (!pathname) {
    return '';
  }

  const url = new URL(pathname, `${normalizedBaseUrl}/`);
  url.searchParams.set('cmsPreview', '1');
  return url.toString();
}
