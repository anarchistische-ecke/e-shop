#!/usr/bin/env bash
set -euo pipefail

POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-e-shop-postgres}"
POSTGRES_USER="${POSTGRES_USER:-eshopUser}"
POSTGRES_DB="${POSTGRES_DB:-eshop}"

docker exec -i "$POSTGRES_CONTAINER" psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" <<'SQL'
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'product'
    ) THEN
        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'product'
              AND column_name = 'is_active'
        ) THEN
            ALTER TABLE product
                ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;
        END IF;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'product_variant'
    ) THEN
        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'product_variant'
              AND column_name = 'price_amount'
        ) THEN
            ALTER TABLE product_variant
                ADD COLUMN price_amount BIGINT;
        END IF;

        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'product_variant'
              AND column_name = 'amount'
        ) THEN
            UPDATE product_variant
            SET price_amount = amount
            WHERE price_amount IS NULL;
        END IF;

        UPDATE product_variant
        SET price_amount = 0
        WHERE price_amount IS NULL;

        ALTER TABLE product_variant
            ALTER COLUMN price_amount SET NOT NULL;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'product_variant'
              AND column_name = 'price_currency'
        ) THEN
            ALTER TABLE product_variant
                ADD COLUMN price_currency VARCHAR(3);
        END IF;

        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'product_variant'
              AND column_name = 'currency'
        ) THEN
            UPDATE product_variant
            SET price_currency = UPPER(LEFT(currency, 3))
            WHERE price_currency IS NULL;
        END IF;

        UPDATE product_variant
        SET price_currency = 'RUB'
        WHERE price_currency IS NULL;

        ALTER TABLE product_variant
            ALTER COLUMN price_currency TYPE CHAR(3) USING UPPER(LEFT(price_currency, 3));

        ALTER TABLE product_variant
            ALTER COLUMN price_currency SET NOT NULL;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'product_category'
    ) AND EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'product'
          AND column_name = 'category_id'
    ) THEN
        INSERT INTO product_category (product_id, category_id)
        SELECT id, category_id
        FROM product
        WHERE category_id IS NOT NULL
        ON CONFLICT DO NOTHING;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'admin_user'
    ) THEN
        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'admin_user'
              AND column_name = 'role'
        ) THEN
            ALTER TABLE admin_user
                ADD COLUMN role VARCHAR(32);
        END IF;

        UPDATE admin_user
        SET role = 'ADMIN'
        WHERE role IS NULL OR BTRIM(role) = '';

        ALTER TABLE admin_user
            ALTER COLUMN role SET NOT NULL;
    END IF;
END $$;
SQL

echo "Legacy local database schema repair completed."
