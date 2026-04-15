const JSON_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

function normalizeBaseUrl(value) {
  return typeof value === 'string' ? value.replace(/\/+$/, '') : '';
}

function parseCsv(value) {
  return String(value || '')
    .split(',')
    .map((entry) => entry.trim())
    .filter(Boolean);
}

function canWrite(method) {
  return ['POST', 'PUT', 'PATCH', 'DELETE'].includes(String(method || '').toUpperCase());
}

function isInventoryPath(path) {
  const normalizedPath = String(path || '');
  return normalizedPath.includes('/inventory/') || /\/products\/[^/]+\/variants(?:\/|$)/.test(normalizedPath);
}

function buildRoleSets(env) {
  return {
    catalogue: new Set(parseCsv(env.STOREFRONT_OPS_CATALOGUE_ROLE_IDS || '')),
    inventory: new Set(parseCsv(env.STOREFRONT_OPS_INVENTORY_ROLE_IDS || '')),
  };
}

function hasAllowedRole(accountability, allowedRoles) {
  if (!allowedRoles || allowedRoles.size === 0) {
    return Boolean(accountability?.user);
  }
  return Boolean(accountability?.role && allowedRoles.has(String(accountability.role)));
}

function hasAnyAllowedRole(accountability, allowedRoleSets) {
  const sets = Array.isArray(allowedRoleSets) ? allowedRoleSets : [];
  if (sets.every((entry) => !entry || entry.size === 0)) {
    return Boolean(accountability?.user);
  }
  return sets.some((entry) => hasAllowedRole(accountability, entry));
}

async function forwardJson(req, res, context, targetPath) {
  const { env, accountability } = context;
  const backendBaseUrl = normalizeBaseUrl(env.STOREFRONT_OPS_BACKEND_URL);
  const backendToken = env.STOREFRONT_OPS_BACKEND_TOKEN || '';

  if (!backendBaseUrl || !backendToken) {
    res.status(500).json({
      error: 'Storefront Ops bridge is not configured. Set STOREFRONT_OPS_BACKEND_URL and STOREFRONT_OPS_BACKEND_TOKEN.',
    });
    return;
  }

  const url = new URL(`${backendBaseUrl}/internal/directus/catalogue${targetPath}`);
  Object.entries(req.query || {}).forEach(([key, value]) => {
    if (Array.isArray(value)) {
      value.forEach((entry) => url.searchParams.append(key, entry));
      return;
    }
    if (value !== undefined && value !== null) {
      url.searchParams.set(key, value);
    }
  });

  const headers = new Headers({
    'X-Directus-Bridge-Token': backendToken,
    'X-Directus-User-Id': accountability?.user ? String(accountability.user) : '',
    'X-Directus-User-Role': accountability?.role ? String(accountability.role) : '',
    'X-Directus-User-Roles': accountability?.role ? String(accountability.role) : '',
  });

  if (accountability?.admin && accountability?.user) {
    headers.set('X-Directus-User-Role', 'admin');
  }

  if (req.accountability?.admin) {
    headers.set('X-Directus-User-Roles', 'admin');
  }

  if (JSON_METHODS.has(String(req.method || '').toUpperCase())) {
    headers.set('Content-Type', 'application/json');
  }

  if (req.accountability?.user && typeof req.accountability.user === 'object') {
    headers.set('X-Directus-User-Email', req.accountability.user?.email || '');
  }

  const response = await fetch(url, {
    method: req.method,
    headers,
    body: JSON_METHODS.has(String(req.method || '').toUpperCase()) ? JSON.stringify(req.body ?? {}) : undefined,
  });

  const contentType = response.headers.get('content-type') || '';
  const payload = contentType.includes('application/json')
    ? await response.json()
    : await response.text();

  res.status(response.status);
  if (contentType) {
    res.setHeader('Content-Type', contentType);
  }
  res.send(payload);
}

export default {
  id: 'storefront-ops-bridge',
  handler: (router, context) => {
  const roleSets = buildRoleSets(context.env);

  router.get('/health', (_req, res) => {
    res.json({ status: 'ok' });
  });

  router.all('/*', async (req, res) => {
    if (!req.accountability?.user) {
      res.status(401).json({ error: 'Authentication is required.' });
      return;
    }

    const path = String(req.path || '');
    const inventoryRoute = isInventoryPath(path);
    const allowedRoles = inventoryRoute ? roleSets.inventory : roleSets.catalogue;

    if (canWrite(req.method) && !hasAllowedRole(req.accountability, allowedRoles)) {
      res.status(403).json({ error: 'This Directus role is not allowed to manage this catalogue area.' });
      return;
    }

    if (!canWrite(req.method) && !hasAnyAllowedRole(req.accountability, [roleSets.catalogue, roleSets.inventory])) {
      res.status(403).json({ error: 'This Directus role is not allowed to access storefront operations.' });
      return;
    }

    try {
      await forwardJson(req, res, context, path);
    } catch (error) {
      res.status(502).json({
        error: error instanceof Error ? error.message : 'Failed to reach backend catalogue bridge.',
      });
    }
  });
  },
};
