export const STOREFRONT_OPS_ROLE_DEFAULTS = {
  admin: [
    'admin',
    'administrator',
    'администратор',
    'администратор cms',
    '4c4cc8d0-9b7f-4d56-84d2-1d64f5f10001',
  ],
  catalogue: [
    '4c4cc8d0-9b7f-4d56-84d2-1d64f5f10001',
    '4c4cc8d0-9b7f-4d56-84d2-1d64f5f10004',
  ],
  inventory: [
    '4c4cc8d0-9b7f-4d56-84d2-1d64f5f10001',
    '4c4cc8d0-9b7f-4d56-84d2-1d64f5f10005',
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

export const STOREFRONT_OPS_TAB_ACCESS = {
  home: ['admin', 'content'],
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

export function normalizeRoleToken(value) {
  return String(value || '').trim().toLowerCase();
}

export function parseCsv(value) {
  return String(value || '')
    .split(',')
    .map((entry) => entry.trim())
    .filter(Boolean);
}

function fromEnv(env, key, roleKind) {
  const configured = parseCsv(env?.[key]);
  return configured.length ? configured : STOREFRONT_OPS_ROLE_DEFAULTS[roleKind] || [];
}

export function buildRoleSets(env = {}) {
  return {
    admin: new Set(fromEnv(env, 'STOREFRONT_OPS_ADMIN_ROLE_IDS', 'admin')),
    catalogue: new Set(fromEnv(env, 'STOREFRONT_OPS_CATALOGUE_ROLE_IDS', 'catalogue')),
    inventory: new Set(fromEnv(env, 'STOREFRONT_OPS_INVENTORY_ROLE_IDS', 'inventory')),
    manager: new Set(fromEnv(env, 'STOREFRONT_OPS_MANAGER_ROLE_IDS', 'manager')),
    picker: new Set(fromEnv(env, 'STOREFRONT_OPS_PICKER_ROLE_IDS', 'picker')),
    content: new Set(fromEnv(env, 'STOREFRONT_OPS_CONTENT_ROLE_IDS', 'content')),
  };
}

export function roleSetHas(roleSet, ...tokens) {
  if (!roleSet || roleSet.size === 0) {
    return false;
  }

  const normalizedRoleSet = new Set([...roleSet].map((entry) => normalizeRoleToken(entry)));
  return tokens
    .map((entry) => normalizeRoleToken(entry))
    .filter(Boolean)
    .some((entry) => normalizedRoleSet.has(entry));
}

export function hasAllowedRole(accountability, allowedRoles) {
  if (accountability?.admin) {
    return Boolean(accountability?.user);
  }
  if (!allowedRoles || allowedRoles.size === 0) {
    return Boolean(accountability?.user);
  }
  return Boolean(accountability?.role && allowedRoles.has(String(accountability.role)));
}

export function hasAnyAllowedRole(accountability, allowedRoleSets) {
  const sets = Array.isArray(allowedRoleSets) ? allowedRoleSets : [];
  if (sets.every((entry) => !entry || entry.size === 0)) {
    return Boolean(accountability?.user);
  }
  return sets.some((entry) => hasAllowedRole(accountability, entry));
}

export function resolveStorefrontOpsRoleKind(state = {}, roleSets = null) {
  if (['admin', 'manager', 'picker', 'content'].includes(state.roleKind)) {
    return state.roleKind;
  }

  const sets = roleSets || buildRoleSets();
  const tokens = [
    state.roleId,
    state.roleName,
    state.primaryRole,
    state.roles,
  ];
  if (state.roleAdminAccess || roleSetHas(sets.admin, ...tokens)) {
    return 'admin';
  }
  if (roleSetHas(sets.manager, ...tokens)) {
    return 'manager';
  }
  if (roleSetHas(sets.picker, ...tokens)) {
    return 'picker';
  }
  if (roleSetHas(sets.content, ...tokens)) {
    return 'content';
  }
  return 'unknown';
}

export function resolveAccountabilityRoleKind(accountability, roleSets, roleName = '') {
  return resolveStorefrontOpsRoleKind({
    roleId: accountability?.role ? String(accountability.role) : '',
    roleName,
    roleAdminAccess: Boolean(accountability?.admin),
  }, roleSets);
}

export function canAccessStorefrontOpsTab(tabId, roleKind, knownTabs = []) {
  if (Array.isArray(knownTabs) && knownTabs.length && !knownTabs.some((tab) => tab.id === tabId)) {
    return false;
  }
  const allowedRoles = STOREFRONT_OPS_TAB_ACCESS[tabId] || ['admin'];
  return allowedRoles.includes(roleKind);
}

export function canWrite(method) {
  return ['POST', 'PUT', 'PATCH', 'DELETE'].includes(String(method || '').toUpperCase());
}

export function isInventoryPath(path) {
  const normalizedPath = String(path || '');
  return normalizedPath.includes('/inventory/') || /\/products\/[^/]+\/variants(?:\/|$)/.test(normalizedPath);
}

export function isAdminOnlyOrderAction(path, method) {
  const normalizedPath = String(path || '');
  const normalizedMethod = String(method || '').toUpperCase();
  if (!normalizedPath.startsWith('/admin/orders/')) {
    return false;
  }
  if (normalizedMethod === 'DELETE') {
    return true;
  }
  return /\/admin\/orders\/[^/]+\/(?:restore|unclaim|refunds)(?:\/|$)/.test(normalizedPath);
}

export function resolveAdminRoleSets(path, roleSets, method = 'GET') {
  if (path === '/admin/promotions/active' || path.startsWith('/admin/promotions/active/')) {
    return [roleSets.admin, roleSets.manager, roleSets.picker, roleSets.content];
  }
  if (isAdminOnlyOrderAction(path, method)) {
    return [roleSets.admin];
  }
  if (path.startsWith('/admin/orders')) {
    return [roleSets.admin, roleSets.manager, roleSets.picker];
  }
  if (path.startsWith('/admin/rma-requests') || path.startsWith('/admin/analytics')) {
    return [roleSets.admin, roleSets.manager];
  }
  if (path.startsWith('/admin/tax-settings')) {
    return [roleSets.admin];
  }
  if (
    path.startsWith('/admin/content/cache') ||
    path.startsWith('/admin/content/publish-check') ||
    path.startsWith('/admin/imports') ||
    path.startsWith('/admin/promotions') ||
    path.startsWith('/admin/promo-codes') ||
    path.startsWith('/admin/alerts')
  ) {
    return [roleSets.admin, roleSets.content];
  }
  return [roleSets.admin];
}
