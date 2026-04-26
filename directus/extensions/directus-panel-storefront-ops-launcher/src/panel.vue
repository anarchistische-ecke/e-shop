<template>
  <div class="launcher">
    <header class="launcher-header">
      <div>
        <p class="launcher-kicker">Резервная точка входа</p>
        <h2>Управление витриной</h2>
      </div>
      <button class="launcher-primary" type="button" :disabled="!visibleTabs.length" @click="openTab(defaultTab)">
        Открыть рабочее место
      </button>
    </header>

    <div class="launcher-grid">
      <button v-for="tab in visibleTabs" :key="tab.id" type="button" class="launcher-card" @click="openTab(tab.id)">
        <strong>{{ tab.label }}</strong>
        <span>{{ tab.description }}</span>
      </button>
    </div>
    <p v-if="accessState.loaded && !visibleTabs.length" class="launcher-empty">
      Для текущей роли нет доступных разделов Storefront Ops.
    </p>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive } from 'vue';
import { useApi } from '@directus/extensions-sdk';

const api = useApi();

const tabs = [
  { id: 'products', label: 'Товары', description: 'Карточки товаров, варианты, медиа, оверлеи' },
  { id: 'categories', label: 'Категории', description: 'Дерево категорий, изображение и оверлей' },
  { id: 'brands', label: 'Бренды', description: 'Справочник брендов и связанный ассортимент' },
  { id: 'inventory', label: 'Остатки', description: 'SKU, корректировки остатков и идемпотентность' },
  { id: 'orders', label: 'Заказы', description: 'Фильтры, статус, история и очередь оплаты' },
  { id: 'imports', label: 'Импорт', description: 'Dry-run, проверка строк и история загрузок' },
  { id: 'promotions', label: 'Акции', description: 'Скидки, sale price и промокоды' },
  { id: 'tax', label: 'Налоги', description: 'СНО, НДС и активный налоговый режим' },
  { id: 'analytics', label: 'Аналитика', description: 'Менеджеры, ссылки оплаты и комиссия' },
  { id: 'alerts', label: 'Алерты', description: 'Низкие остатки и глобальный порог' },
];

const ROLE_IDS = {
  admin: [
    'admin',
    'administrator',
    'администратор',
    'администратор cms',
    '4c4cc8d0-9b7f-4d56-84d2-1d64f5f10001',
  ],
  manager: ['manager', 'менеджер', '4c4cc8d0-9b7f-4d56-84d2-1d64f5f10006'],
  picker: ['picker', 'сборщик', '4c4cc8d0-9b7f-4d56-84d2-1d64f5f10007'],
  content: [
    'content_manager',
    'content-manager',
    'контент-менеджер',
    '4c4cc8d0-9b7f-4d56-84d2-1d64f5f10008',
    '4c4cc8d0-9b7f-4d56-84d2-1d64f5f10002',
    '4c4cc8d0-9b7f-4d56-84d2-1d64f5f10004',
    '4c4cc8d0-9b7f-4d56-84d2-1d64f5f10005',
  ],
};

const TAB_ACCESS = {
  products: ['admin', 'content'],
  categories: ['admin', 'content'],
  brands: ['admin', 'content'],
  inventory: ['admin', 'content'],
  orders: ['admin', 'manager', 'picker'],
  imports: ['admin', 'content'],
  promotions: ['admin', 'content'],
  tax: ['admin'],
  analytics: ['admin', 'manager'],
  alerts: ['admin', 'content'],
};

const accessState = reactive({
  loaded: false,
  roleId: '',
  roleName: '',
  roleAdminAccess: false,
});

const roleKind = computed(() => resolveRoleKind(accessState));
const visibleTabs = computed(() => (
  accessState.loaded
    ? tabs.filter((tab) => (TAB_ACCESS[tab.id] || ['admin']).includes(roleKind.value))
    : []
));
const defaultTab = computed(() => visibleTabs.value[0]?.id || 'products');

function normalizeRoleToken(value) {
  return String(value || '').trim().toLowerCase();
}

function roleMatches(tokens, roleKey) {
  return (ROLE_IDS[roleKey] || []).some((entry) => tokens.has(normalizeRoleToken(entry)));
}

function resolveRoleKind(state) {
  const tokens = new Set([
    normalizeRoleToken(state.roleId),
    normalizeRoleToken(state.roleName),
  ].filter(Boolean));
  if (state.roleAdminAccess || roleMatches(tokens, 'admin')) return 'admin';
  if (roleMatches(tokens, 'manager')) return 'manager';
  if (roleMatches(tokens, 'picker')) return 'picker';
  if (roleMatches(tokens, 'content')) return 'content';
  return 'unknown';
}

async function loadAccessProfile() {
  try {
    const response = await requestAccessProfile(['role.id', 'role.name']);
    const user = response?.data?.data || response?.data || {};
    applyAccessProfile(user);
  } catch {
    try {
      const response = await requestAccessProfile(['role']);
      const user = response?.data?.data || response?.data || {};
      applyAccessProfile(user);
    } catch {
      applyAccessProfile({});
    }
  } finally {
    accessState.loaded = true;
  }
}

function requestAccessProfile(fields) {
  return api.request({
    url: '/users/me',
    method: 'GET',
    params: {
      fields: fields.join(','),
    },
  });
}

function applyAccessProfile(user) {
  const role = user?.role || '';
  const roleIsObject = role && typeof role === 'object';
  accessState.roleId = roleIsObject ? role.id || '' : role || '';
  accessState.roleName = roleIsObject ? role.name || '' : '';
  accessState.roleAdminAccess = Boolean(roleIsObject && (role.admin_access || role.adminAccess));
}

function openTab(tabId) {
  window.location.assign(`/admin/storefront-ops?tab=${encodeURIComponent(tabId)}`);
}

onMounted(loadAccessProfile);
</script>

<style scoped>
.launcher {
  display: grid;
  gap: 16px;
  height: 100%;
  padding: 8px;
}

.launcher-header {
  align-items: start;
  display: flex;
  gap: 12px;
  justify-content: space-between;
}

.launcher-kicker {
  color: var(--theme--foreground-subdued);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  margin: 0 0 6px;
  text-transform: uppercase;
}

.launcher-header h2 {
  margin: 0;
}

.launcher-primary,
.launcher-card {
  appearance: none;
  border-radius: 14px;
  cursor: pointer;
}

.launcher-primary {
  background: var(--theme--primary);
  border: 1px solid var(--theme--primary);
  color: var(--theme--primary-inverse);
  min-height: 42px;
  padding: 10px 14px;
}

.launcher-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.launcher-card {
  align-items: start;
  background: var(--theme--background);
  border: 1px solid var(--theme--border-color);
  color: inherit;
  display: grid;
  gap: 6px;
  min-height: 104px;
  padding: 14px;
  text-align: left;
}

.launcher-card span {
  color: var(--theme--foreground-subdued);
  font-size: 13px;
  line-height: 1.35;
}

.launcher-empty {
  color: var(--theme--foreground-subdued);
  margin: 0;
}

@media (max-width: 720px) {
  .launcher-header,
  .launcher-grid {
    grid-template-columns: 1fr;
    display: grid;
  }
}
</style>
