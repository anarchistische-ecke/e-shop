import {
  buildRoleSets,
  canWrite,
  hasAnyAllowedRole,
  isInventoryPath,
  resolveAccountabilityRoleKind,
  resolveAdminRoleSets,
} from '../../storefront-ops-access-policy.js';

const BODY_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

function normalizeBaseUrl(value) {
  return typeof value === 'string' ? value.replace(/\/+$/, '') : '';
}

function accountabilityUserId(accountability) {
  const user = accountability?.user;
  if (!user) {
    return '';
  }
  if (typeof user === 'object') {
    return user.id ? String(user.id) : '';
  }
  return String(user);
}

function resolvePreviewBaseUrl(env) {
  return normalizeBaseUrl(env.STOREFRONT_OPS_PREVIEW_BASE_URL || env.STOREFRONT_PUBLIC_URL || '');
}

async function resolveDirectusRoleDetails(accountability, context) {
  const roleId = accountability?.role ? String(accountability.role) : '';
  if (!roleId || !context?.database) {
    return { id: roleId, name: '' };
  }

  try {
    const row = await context.database('directus_roles')
      .select('id', 'name')
      .where({ id: roleId })
      .first();
    return {
      id: row?.id || roleId,
      name: row?.name || '',
    };
  } catch {
    return { id: roleId, name: '' };
  }
}

async function resolveDirectusUserDetails(accountability, context) {
  const user = accountability?.user;
  if (user && typeof user === 'object') {
    return {
      email: user.email || '',
      externalId: user.external_identifier || user.externalIdentifier || '',
    };
  }

  if (!user || !context?.database) {
    return { email: '', externalId: '' };
  }

  try {
    const row = await context.database('directus_users')
      .select('email', 'external_identifier')
      .where({ id: String(user) })
      .first();
    return {
      email: row?.email || '',
      externalId: row?.external_identifier || '',
    };
  } catch {
    return { email: '', externalId: '' };
  }
}

async function sendAccessProfile(req, res, context, roleSets) {
  if (!req.accountability?.user) {
    res.status(401).json({ error: 'Требуется авторизация.' });
    return;
  }

  const directusUser = await resolveDirectusUserDetails(req.accountability, context);
  const directusRole = await resolveDirectusRoleDetails(req.accountability, context);
  const roleKind = resolveAccountabilityRoleKind(req.accountability, roleSets, directusRole.name);
  const previewBaseUrl = resolvePreviewBaseUrl(context.env);

  res.json({
    data: {
      id: accountabilityUserId(req.accountability),
      email: directusUser.email,
      external_identifier: directusUser.externalId,
      roleKind,
      role: {
        id: directusRole.id,
        name: directusRole.name,
        admin_access: Boolean(req.accountability?.admin),
        kind: roleKind,
      },
      preview: {
        baseUrl: previewBaseUrl,
      },
    },
  });
}

async function forwardJson(req, res, context, targetPath, namespace = 'catalogue') {
  const { env } = context;
  const accountability = req.accountability;
  const backendBaseUrl = normalizeBaseUrl(env.STOREFRONT_OPS_BACKEND_URL);
  const backendToken = env.STOREFRONT_OPS_BACKEND_TOKEN || '';

  if (!backendBaseUrl || !backendToken) {
    res.status(500).json({
      error: 'Bridge для управления витриной не настроен. Укажите STOREFRONT_OPS_BACKEND_URL и STOREFRONT_OPS_BACKEND_TOKEN.',
    });
    return;
  }

  const directusUser = await resolveDirectusUserDetails(accountability, context);
  const url = new URL(`${backendBaseUrl}/internal/directus/${namespace}${targetPath}`);
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
    'X-Directus-User-Id': accountabilityUserId(accountability),
    'X-Directus-User-Email': directusUser.email,
    'X-Directus-User-External-Id': directusUser.externalId,
    'X-Directus-User-Role': accountability?.role ? String(accountability.role) : '',
    'X-Directus-User-Roles': accountability?.role ? String(accountability.role) : '',
  });

  if (accountability?.admin && accountability?.user) {
    headers.set('X-Directus-User-Role', 'admin');
  }

  if (req.accountability?.admin) {
    headers.set('X-Directus-User-Roles', 'admin');
  }

  const method = String(req.method || '').toUpperCase();
  const requestContentType = String(req.headers['content-type'] || '');

  const fetchOptions = {
    method: req.method,
    headers,
  };

  if (BODY_METHODS.has(method)) {
    if (requestContentType.includes('multipart/form-data')) {
      headers.set('Content-Type', requestContentType);
      fetchOptions.body = req;
      fetchOptions.duplex = 'half';
    } else if (requestContentType.includes('application/json') || !requestContentType) {
      headers.set('Content-Type', 'application/json');
      fetchOptions.body = JSON.stringify(req.body ?? {});
    } else if (req.body !== undefined && req.body !== null) {
      headers.set('Content-Type', requestContentType);
      fetchOptions.body = req.body;
    }
  }

  const response = await fetch(url, fetchOptions);

  const contentType = response.headers.get('content-type') || '';
  const payload = contentType.includes('application/json')
    ? await response.json()
    : Buffer.from(await response.arrayBuffer());

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

  router.get('/access-profile', async (req, res) => {
    try {
      await sendAccessProfile(req, res, context, roleSets);
    } catch (error) {
      res.status(500).json({
        error: error instanceof Error ? error.message : 'Не удалось определить профиль доступа Directus.',
      });
    }
  });

  router.all('/*', async (req, res) => {
    if (!req.accountability?.user) {
      res.status(401).json({ error: 'Требуется авторизация.' });
      return;
    }

    const path = String(req.path || '');
    const isAdminRoute = path === '/admin' || path.startsWith('/admin/');
    const adminTargetPath = isAdminRoute ? path.replace(/^\/admin/, '') || '/' : path;
    const adminAllowedRoles = isAdminRoute ? resolveAdminRoleSets(path, roleSets, req.method) : [];

    if (isAdminRoute && !hasAnyAllowedRole(req.accountability, adminAllowedRoles)) {
      res.status(403).json({ error: 'У этой роли Directus нет доступа к административному разделу витрины.' });
      return;
    }

    if (isAdminRoute) {
      try {
        await forwardJson(req, res, context, adminTargetPath, 'admin');
      } catch (error) {
        res.status(502).json({
          error: error instanceof Error ? error.message : 'Не удалось обратиться к административному bridge в бэкенде.',
        });
      }
      return;
    }

    const inventoryRoute = isInventoryPath(path);
    const allowedRoleSets = inventoryRoute
      ? [roleSets.admin, roleSets.inventory, roleSets.content]
      : [roleSets.admin, roleSets.catalogue, roleSets.content];

    if (canWrite(req.method) && !hasAnyAllowedRole(req.accountability, allowedRoleSets)) {
      res.status(403).json({ error: 'У этой роли Directus нет прав на управление данным разделом каталога.' });
      return;
    }

    if (!canWrite(req.method) && !hasAnyAllowedRole(req.accountability, allowedRoleSets)) {
      res.status(403).json({ error: 'У этой роли Directus нет доступа к операциям управления витриной.' });
      return;
    }

    try {
      await forwardJson(req, res, context, path, 'catalogue');
    } catch (error) {
      res.status(502).json({
        error: error instanceof Error ? error.message : 'Не удалось обратиться к bridge каталога в бэкенде.',
      });
    }
  });
  },
};
