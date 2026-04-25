ALTER TABLE customer_order
    ADD COLUMN IF NOT EXISTS manager_directus_user_id TEXT,
    ADD COLUMN IF NOT EXISTS manager_email TEXT,
    ADD COLUMN IF NOT EXISTS manager_claimed_at TIMESTAMPTZ;

UPDATE customer_order co
SET manager_email = link.manager_email
FROM (
    SELECT DISTINCT ON (order_id)
        order_id,
        manager_email
    FROM manager_payment_link
    WHERE manager_email IS NOT NULL
      AND btrim(manager_email) <> ''
    ORDER BY order_id, created_at DESC
) link
WHERE co.id = link.order_id
  AND (co.manager_email IS NULL OR btrim(co.manager_email) = '');

UPDATE customer_order
SET manager_email = manager_subject
WHERE (manager_email IS NULL OR btrim(manager_email) = '')
  AND manager_subject IS NOT NULL
  AND manager_subject ~* '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$';

UPDATE customer_order co
SET manager_subject = claim.actor,
    manager_email = claim.actor,
    manager_claimed_at = claim.created_at
FROM (
    SELECT DISTINCT ON (order_id)
        order_id,
        actor,
        created_at
    FROM order_status_history
    WHERE note = 'claimed'
      AND actor IS NOT NULL
      AND actor ~* '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$'
    ORDER BY order_id, created_at DESC
) claim
WHERE co.id = claim.order_id
  AND (co.manager_subject IS NULL OR btrim(co.manager_subject) = '')
  AND (co.manager_email IS NULL OR btrim(co.manager_email) = '');

UPDATE customer_order
SET manager_claimed_at = order_date
WHERE manager_claimed_at IS NULL
  AND (
      (manager_email IS NOT NULL AND btrim(manager_email) <> '')
      OR (manager_directus_user_id IS NOT NULL AND btrim(manager_directus_user_id) <> '')
  );

CREATE INDEX IF NOT EXISTS idx_customer_order_manager_directus_user_id
    ON customer_order(manager_directus_user_id);

CREATE INDEX IF NOT EXISTS idx_customer_order_manager_email
    ON customer_order(manager_email);
