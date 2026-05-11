export const STOREFRONT_OPS_TABS = [
  { id: 'home', label: 'Главная' },
  { id: 'products', label: 'Товары' },
  { id: 'categories', label: 'Категории' },
  { id: 'brands', label: 'Бренды' },
  { id: 'inventory', label: 'Остатки' },
  { id: 'orders', label: 'Заказы' },
  { id: 'imports', label: 'Импорт' },
  { id: 'promotions', label: 'Акции' },
  { id: 'tax', label: 'Налоги' },
  { id: 'analytics', label: 'Аналитика' },
  { id: 'alerts', label: 'Алерты' },
];

export const PRODUCT_DETAIL_TABS = [
  { id: 'main', label: 'Основное' },
  { id: 'variants', label: 'Варианты' },
  { id: 'media', label: 'Медиа' },
  { id: 'merch', label: 'Мерчандайзинг' },
];

export const STOREFRONT_OPS_MASTER_DETAIL_TABS = [
  'products',
  'categories',
  'brands',
  'inventory',
  'orders',
  'promotions',
  'tax',
];

export const STOREFRONT_OPS_NAVIGATION_COUNT_KEYS = [
  'home',
  'products',
  'categories',
  'brands',
  'inventory',
  'orders',
  'imports',
  'promotions',
  'tax',
  'analytics',
  'alerts',
];

export const STOREFRONT_OPS_LOADING_KEYS = [
  ...STOREFRONT_OPS_NAVIGATION_COUNT_KEYS,
  'activePromotions',
];

export function isStorefrontOpsMasterDetailTab(tabId) {
  return STOREFRONT_OPS_MASTER_DETAIL_TABS.includes(tabId);
}

export function createStorefrontOpsNavigationCounts() {
  return Object.fromEntries(STOREFRONT_OPS_NAVIGATION_COUNT_KEYS.map((key) => [key, 0]));
}

export function createStorefrontOpsLoadingState() {
  return Object.fromEntries(STOREFRONT_OPS_LOADING_KEYS.map((key) => [key, false]));
}
