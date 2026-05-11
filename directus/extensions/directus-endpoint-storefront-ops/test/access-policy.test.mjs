import test from 'node:test';
import assert from 'node:assert/strict';

import {
  buildRoleSets,
  hasAnyAllowedRole,
  isInventoryPath,
  resolveAdminRoleSets,
} from '../../storefront-ops-access-policy.js';

test('catalogue and inventory bridge paths use different role sets', () => {
  const roleSets = buildRoleSets({
    STOREFRONT_OPS_ADMIN_ROLE_IDS: 'admin-role',
    STOREFRONT_OPS_CATALOGUE_ROLE_IDS: 'catalogue-role',
    STOREFRONT_OPS_INVENTORY_ROLE_IDS: 'inventory-role',
    STOREFRONT_OPS_CONTENT_ROLE_IDS: 'content-role',
  });

  assert.equal(isInventoryPath('/products/product-1/variants'), true);
  assert.equal(isInventoryPath('/products/product-1/images'), false);
  assert.equal(
    hasAnyAllowedRole({ user: 'user-1', role: 'catalogue-role' }, [roleSets.admin, roleSets.catalogue, roleSets.content]),
    true
  );
  assert.equal(
    hasAnyAllowedRole({ user: 'user-1', role: 'catalogue-role' }, [roleSets.admin, roleSets.inventory, roleSets.content]),
    false
  );
});

test('admin-only order actions do not allow manager bridge roles', () => {
  const roleSets = buildRoleSets({
    STOREFRONT_OPS_ADMIN_ROLE_IDS: 'admin-role',
    STOREFRONT_OPS_MANAGER_ROLE_IDS: 'manager-role',
    STOREFRONT_OPS_PICKER_ROLE_IDS: 'picker-role',
  });
  const allowed = resolveAdminRoleSets('/admin/orders/11111111-1111-4111-8111-111111111111/refunds', roleSets, 'POST');

  assert.equal(hasAnyAllowedRole({ user: 'user-1', role: 'admin-role' }, allowed), true);
  assert.equal(hasAnyAllowedRole({ user: 'user-1', role: 'manager-role' }, allowed), false);
});
