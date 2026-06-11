import { expect, test } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const localEnv = readEnvFile(path.resolve(__dirname, '../../../.env'));
const DIRECTUS_BASE_URL =
  process.env.DIRECTUS_BASE_URL ||
  localEnv.DIRECTUS_PUBLIC_URL ||
  'http://localhost:8055';
const DIRECTUS_ADMIN_EMAIL =
  process.env.DIRECTUS_ADMIN_EMAIL ||
  localEnv.DIRECTUS_ADMIN_EMAIL ||
  'directus-admin@example.com';
const DIRECTUS_ADMIN_PASSWORD =
  process.env.DIRECTUS_ADMIN_PASSWORD ||
  localEnv.DIRECTUS_ADMIN_PASSWORD ||
  'Admin123!';

const VIEWPORTS = [
  { name: 'desktop-wide', width: 1920, height: 1080 },
  { name: 'desktop-constrained', width: 1366, height: 768 },
  { name: 'tablet', width: 768, height: 1024 },
  { name: 'mobile', width: 390, height: 844 },
];

const categories = [
  {
    id: 'cat-bedroom',
    name: 'Постельное белье премиум',
    slug: 'postelnoe-bele-premium',
    fullPath: 'Дом / Спальня / Постельное белье премиум',
    depth: 2,
    imageUrl: '',
    isActive: true,
  },
  {
    id: 'cat-throws',
    name: 'Пледы и покрывала',
    slug: 'pledy-i-pokryvala',
    fullPath: 'Дом / Текстиль / Пледы и покрывала',
    depth: 2,
    imageUrl: '',
    isActive: true,
  },
];

const brandOptions = [{ id: 'brand-kazanova', name: 'KAZANOV.A', slug: 'kazanova' }];

const productSummaries = [
  {
    id: 'prod-dolce',
    name: '"Dolce Fiore Cotton" Тачки графит жемчуг',
    slug: 'dolce-fiore-cotton-tachki-grafit-zhemchug',
    description: 'Комплект с длинным названием для проверки переносов в карточках и формах.',
    isActive: true,
    brand: brandOptions[0],
    categories: [categories[0]],
    variantCount: 1,
    totalStock: 0,
    primaryImageUrl: '',
    overlay: { exists: true, status: 'published' },
  },
  {
    id: 'prod-rabbit',
    name: 'Rabbit Fur Collection Бежевый платина',
    slug: 'rabbit-fur-collection-bezheviy-platina',
    description: 'Позиция для длинных списков.',
    isActive: true,
    brand: brandOptions[0],
    categories: [categories[1]],
    variantCount: 2,
    totalStock: 200,
    primaryImageUrl: '',
    overlay: { exists: false, status: 'draft' },
  },
];

const productDetail = {
  item: {
    ...productSummaries[0],
    createdAt: '2026-06-01T09:00:00.000Z',
    updatedAt: '2026-06-10T17:30:00.000Z',
    specifications: [
      {
        title: 'Материал',
        description: 'Длинная секция характеристик',
        items: [
          { label: 'Состав', value: '100% хлопок сатин высокого качества' },
          { label: 'Плотность', value: 'Премиальная плотность для круглогодичного использования' },
        ],
      },
    ],
    variants: [
      {
        id: 'variant-dolce-15',
        sku: 'DOLCE-FIORE-COTTON-TACHKI-GRAFIT-ZHEMCHUG-15',
        name: '1,5',
        price: { amount: 1_050_000, currency: 'RUB' },
        stock: 0,
        weightGrossG: 1200,
        lengthMm: 380,
        widthMm: 280,
        heightMm: 90,
      },
    ],
    images: [],
  },
  brandOptions,
  categoryOptions: categories,
  overlay: { exists: true, status: 'published', key: productSummaries[0].slug },
};

const orderSummary = {
  id: 'order-scroll-regression',
  receiptEmail: 'incmet@mail.ru',
  status: 'PENDING',
  totalAmount: { amount: 550_000, currency: 'RUB' },
  managerEmail: 'lillia.viktorovna@yandex.ru',
  orderDate: '2026-06-10T18:32:00.000Z',
  archivedAt: null,
};

const orderDetail = {
  order: {
    ...orderSummary,
    contactName: 'Инна Метелица',
    contactPhone: '+7 900 000-00-00',
    homeAddress: 'Краснодар, улица с очень длинным названием, дом 15, квартира 99',
    paymentSummary: {
      status: 'PENDING',
      providerPaymentId: '31b8bdeb-000f-5001-9000-1b6f14cfd7aa',
      amount: { amount: 550_000, currency: 'RUB' },
      refundableAmount: { amount: 550_000, currency: 'RUB' },
      receiptRegistration: 'Формируется в YooKassa',
      refunds: [
        {
          id: 'refund-1',
          refundId: 'rfnd-long-id-for-layout-check',
          status: 'PENDING',
          amount: { amount: 100_000, currency: 'RUB' },
          refundDate: '2026-06-10T19:00:00.000Z',
          items: [{ id: 'order-item-1' }],
        },
      ],
    },
    items: Array.from({ length: 8 }, (_, index) => ({
      id: `order-item-${index + 1}`,
      productName: `${productSummaries[index % productSummaries.length].name} строка ${index + 1}`,
      variantName: `Размер ${index + 1}`,
      variantId: `variant-${index + 1}`,
      sku: `SKU-LONG-${index + 1}-STORE-FRONT-OPS-SCROLL-CHECK`,
      quantity: index + 1,
      unitPrice: { amount: 105_000 + index * 10_000, currency: 'RUB' },
    })),
  },
  shipment: {
    carrier: 'CDEK',
    trackingNumber: 'TRACK-STORE-FRONT-OPS-LONG-VALUE-1234567890',
  },
  rmaRequests: [
    {
      id: 'rma-1',
      rmaNumber: 'RMA-STORE-FRONT-OPS-0001',
      status: 'REQUESTED',
      reason: 'Проверка длинного текста причины возврата для адаптивной сетки.',
      managerComment: 'Комментарий менеджера с переносами.',
    },
  ],
  history: Array.from({ length: 10 }, (_, index) => ({
    id: `history-${index + 1}`,
    previousStatus: index === 0 ? '' : 'PENDING',
    nextStatus: index % 2 ? 'PAID' : 'PENDING',
    actor: index % 2 ? 'manager@example.com' : 'system',
    note: `Событие истории заказа ${index + 1} с длинным описанием для проверки переносов.`,
    createdAt: `2026-06-10T${String(8 + index).padStart(2, '0')}:00:00.000Z`,
  })),
};

const homePage = {
  id: 'page-home',
  slug: 'home',
  status: 'published',
  title: 'Домашний текстиль для уютного дома',
  summary: 'Редакционная витрина с hero-блоком, сервисными преимуществами, популярными разделами и CMS-подборками.',
  seo_title: 'Домашний текстиль для уютного дома',
  seo_description: 'Домашний текстиль для уютного дома: доставка по России, честные условия покупки и собственное производство.',
};

const homeSections = [
  {
    id: 'section-hero',
    page: homePage.id,
    status: 'published',
    internal_name: 'Главная — hero',
    section_type: 'hero',
    sort: 1,
    title: 'Обновите спальню без лишней суеты',
    body: 'Большой редакционный блок с длинным текстом, ссылками и выбором товара.',
    primary_cta_label: 'Смотреть каталог',
    primary_cta_url: '/catalog',
    secondary_cta_label: 'Новинки',
    secondary_cta_url: '/new',
    layout_variant: 'media_right',
    style_variant: 'accent',
  },
  {
    id: 'section-products',
    page: homePage.id,
    status: 'published',
    internal_name: 'Главная — бестселлеры',
    section_type: 'product_reference_list',
    sort: 2,
    title: 'Бестселлеры недели',
    body: 'Секция с повторяемыми карточками товаров.',
    layout_variant: 'cards',
    style_variant: 'default',
  },
  {
    id: 'section-categories',
    page: homePage.id,
    status: 'published',
    internal_name: 'Главная — основные разделы',
    section_type: 'category_reference_list',
    sort: 3,
    title: 'Основные разделы',
    body: 'Быстрый вход в популярные разделы.',
    layout_variant: 'cards',
    style_variant: 'default',
  },
];

const homeItems = [
  {
    id: 'hero-featured-product',
    page_section: 'section-hero',
    status: 'published',
    reference_kind: 'product_slug',
    reference_key: productSummaries[0].slug,
    title: 'Выбор недели',
    sort: 1,
  },
  ...productSummaries.map((product, index) => ({
    id: `home-product-${index + 1}`,
    page_section: 'section-products',
    status: 'published',
    reference_kind: 'product_slug',
    reference_key: product.slug,
    title: product.name,
    label: 'Товар',
    description: `Описание карточки товара ${index + 1} с длинным текстом для проверки переносов.`,
    sort: index + 1,
  })),
  ...categories.map((category, index) => ({
    id: `home-category-${index + 1}`,
    page_section: 'section-categories',
    status: 'published',
    reference_kind: 'category_slug',
    reference_key: category.slug,
    title: category.name,
    label: 'Категория',
    description: `Описание категории ${index + 1}.`,
    sort: index + 1,
  })),
];

test.use({ baseURL: DIRECTUS_BASE_URL });

for (const viewport of VIEWPORTS) {
  test.describe(`Storefront Ops layout at ${viewport.name}`, () => {
    test.use({ viewport });

    test.beforeEach(async ({ page }) => {
      await installStorefrontOpsMocks(page);
      await login(page);
      await page.evaluate(() => localStorage.removeItem('storefront-ops.workspace-state')).catch(() => null);
    });

    test('home detail scrolls without overflow or action overlap', async ({ page }) => {
      await openStorefrontOps(page, 'tab=home');
      await expect(page.locator('.detail-header h2', { hasText: 'Контент главной страницы' })).toBeVisible();
      await expectScrollContainer(page, scrollOwnerSelector(viewport, '.detail-card > .editor-form'));
      await expect(page.locator('.detail-footer-actions button', { hasText: 'Сохранить главную' })).toBeVisible();
      await expectNoHorizontalOverflow(page);
      await expectNoActionOverlap(page);
    });

    test('product variants stay contained and vertically reachable', async ({ page }) => {
      await openStorefrontOps(page, 'tab=products&product=prod-dolce&panel=variants');
      await expect(page.locator('.panel-variants')).toBeVisible();
      await expectScrollContainer(page, scrollOwnerSelector(viewport, '.panel-variants'));
      await expect(page.locator('.panel-variants .button-primary', { hasText: /Добавить вариант|Сохранить вариант/ })).toBeVisible();
      await expectNoHorizontalOverflow(page);
      await expectNoActionOverlap(page);
    });

    test('orders detail scrolls on desktop and remains contained', async ({ page }) => {
      await openStorefrontOps(page, 'tab=orders');
      await page.getByRole('button', { name: /incmet@mail\.ru/ }).click();
      await expect(page.locator('.orders-detail-content')).toBeVisible();
      await expectScrollContainer(page, scrollOwnerSelector(viewport, '.orders-detail-content'));
      await expect(page.locator('.orders-detail-content', { hasText: 'История статусов' })).toBeVisible();
      await expectNoHorizontalOverflow(page);
      await expectNoActionOverlap(page);
    });
  });
}

function readEnvFile(filePath) {
  if (!fs.existsSync(filePath)) {
    return {};
  }
  return Object.fromEntries(
    fs.readFileSync(filePath, 'utf8')
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter((line) => line && !line.startsWith('#') && line.includes('='))
      .map((line) => {
        const index = line.indexOf('=');
        const key = line.slice(0, index).trim();
        const value = line.slice(index + 1).trim().replace(/^['"]|['"]$/g, '');
        return [key, value];
      })
  );
}

async function login(page) {
  await page.goto('/admin/login');
  const emailInput = page.locator('input[type="email"], input[name="email"], input[autocomplete="username"]').first();
  try {
    await emailInput.waitFor({ state: 'visible', timeout: 10_000 });
  } catch {
    return;
  }
  await emailInput.fill(DIRECTUS_ADMIN_EMAIL);
  const passwordInput = page.locator('input[type="password"], input[name="password"], input[autocomplete="current-password"]').first();
  await passwordInput.fill(DIRECTUS_ADMIN_PASSWORD);
  await Promise.all([
    page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 30_000 }).catch(() => null),
    passwordInput.press('Enter'),
  ]);
}

async function openStorefrontOps(page, query) {
  await page.goto(`/admin/storefront-ops?${query}`);
  await page.waitForSelector('.storefront-ops.workspace', { timeout: 30_000 });
}

function scrollOwnerSelector(viewport, desktopSelector) {
  return viewport.width >= 720 ? desktopSelector : '.storefront-ops.workspace';
}

async function expectScrollContainer(page, selector) {
  const locator = page.locator(selector).first();
  await expect(locator).toBeVisible();
  const before = await locator.evaluate((element) => ({
    clientHeight: element.clientHeight,
    scrollHeight: element.scrollHeight,
    scrollTop: element.scrollTop,
  }));
  expect(before.scrollHeight, `${selector} should have overflow content`).toBeGreaterThan(before.clientHeight + 8);
  await locator.evaluate((element) => {
    element.scrollTop = element.scrollHeight;
  });
  const after = await locator.evaluate((element) => element.scrollTop);
  expect(after, `${selector} should accept vertical scroll`).toBeGreaterThan(before.scrollTop);
}

async function expectNoHorizontalOverflow(page) {
  const result = await page.evaluate(() => {
    const visible = (element) => {
      const rect = element.getBoundingClientRect();
      const style = window.getComputedStyle(element);
      return rect.width > 0 && rect.height > 0 && style.display !== 'none' && style.visibility !== 'hidden';
    };
    const root = document.querySelector('.storefront-ops.workspace');
    const docOverflow = Math.max(
      document.documentElement.scrollWidth - document.documentElement.clientWidth,
      document.body.scrollWidth - window.innerWidth
    );
    const rootOverflow = root ? root.scrollWidth - root.clientWidth : 0;
    const detailOverflow = Array.from(document.querySelectorAll(
      '.storefront-ops .workspace-list, .storefront-ops .workspace-detail, .storefront-ops .detail-card, .storefront-ops .detail-content, .storefront-ops .editor-form'
    ))
      .filter(visible)
      .map((element) => ({
        className: element.className,
        overflow: Math.round(element.scrollWidth - element.clientWidth),
      }))
      .filter((entry) => entry.overflow > 2);
    const rootRect = root?.getBoundingClientRect();
    const spills = rootRect
      ? Array.from(document.querySelectorAll(
          '.storefront-ops .workspace-list :is(.list-card, .form-grid, .ops-field, input, select, textarea, button), .storefront-ops .workspace-detail :is(.detail-card, .detail-content, .editor-form, .section-block, .selector-card, .merch-card, .variant-card, .media-card, .metric-card, .form-grid, .ops-field, input, select, textarea, button)'
        ))
          .filter(visible)
          .map((element) => {
            const rect = element.getBoundingClientRect();
            return {
              tag: element.tagName.toLowerCase(),
              className: element.className,
              left: Math.round(rect.left - rootRect.left),
              right: Math.round(rect.right - rootRect.right),
            };
          })
          .filter((entry) => entry.left < -2 || entry.right > 2)
          .slice(0, 8)
      : [];
    return { docOverflow, rootOverflow, detailOverflow, spills };
  });

  expect(result.docOverflow, JSON.stringify(result)).toBeLessThanOrEqual(2);
  expect(result.rootOverflow, JSON.stringify(result)).toBeLessThanOrEqual(2);
  expect(result.detailOverflow, JSON.stringify(result)).toEqual([]);
  expect(result.spills, JSON.stringify(result)).toEqual([]);
}

async function expectNoActionOverlap(page) {
  const overlaps = await page.evaluate(() => {
    const visible = (element) => {
      const rect = element.getBoundingClientRect();
      const style = window.getComputedStyle(element);
      return rect.width > 0 && rect.height > 0 && style.display !== 'none' && style.visibility !== 'hidden';
    };
    const intersects = (a, b) => (
      a.left < b.right &&
      a.right > b.left &&
      a.top < b.bottom &&
      a.bottom > b.top
    );
    const actions = Array.from(document.querySelectorAll('.storefront-ops .sticky-actions')).filter(visible);
    const fields = Array.from(document.querySelectorAll('.storefront-ops input, .storefront-ops select, .storefront-ops textarea')).filter(visible);
    return actions.flatMap((action) => {
      const actionRect = action.getBoundingClientRect();
      return fields
        .filter((field) => !action.contains(field) && intersects(actionRect, field.getBoundingClientRect()))
        .map((field) => ({
          action: action.className,
          field: field.outerHTML.slice(0, 120),
        }));
    }).slice(0, 8);
  });

  expect(overlaps, JSON.stringify(overlaps)).toEqual([]);
}

async function installStorefrontOpsMocks(page) {
  await page.route('**/storefront-ops-bridge/**', async (route) => {
    const url = new URL(route.request().url());
    const bridgePath = url.pathname.split('/storefront-ops-bridge')[1] || '';

    if (bridgePath === '/access-profile') {
      return json(route, {
        id: 'directus-admin',
        email: DIRECTUS_ADMIN_EMAIL,
        roleKind: 'admin',
        roleId: 'admin',
        roleName: 'Administrator',
        roleAdminAccess: true,
        preview: { baseUrl: 'http://localhost:3000' },
      });
    }
    if (bridgePath === '/workspace/summary') {
      return json(route, {
        productCount: productSummaries.length,
        categoryCount: categories.length,
        brandCount: brandOptions.length,
        inventoryCount: 185,
        orderCount: 1,
      });
    }
    if (bridgePath === '/workspace/products/prod-dolce') {
      return json(route, productDetail);
    }
    if (bridgePath === '/workspace/products') {
      return json(route, {
        items: productSummaries,
        brandOptions,
        categoryOptions: categories,
        overlayReadFailed: false,
      });
    }
    if (bridgePath === '/workspace/categories') {
      return json(route, {
        items: categories,
        parentOptions: categories,
        overlayReadFailed: false,
      });
    }
    if (bridgePath === '/workspace/brands') {
      return json(route, { items: brandOptions });
    }
    if (bridgePath === '/workspace/inventory') {
      return json(route, { items: [] });
    }
    if (bridgePath === '/admin/orders/order-scroll-regression') {
      return json(route, orderDetail);
    }
    if (bridgePath === '/admin/orders') {
      return json(route, { items: [orderSummary] });
    }
    if (bridgePath === '/admin/promotions/active') {
      return json(route, []);
    }
    if (bridgePath.startsWith('/admin/')) {
      return json(route, { items: [] });
    }
    return json(route, {});
  });

  await page.route('**/items/**', async (route) => {
    const url = new URL(route.request().url());
    const pathname = url.pathname;
    if (pathname.endsWith('/items/page')) {
      return json(route, { data: [homePage] });
    }
    if (pathname.endsWith('/items/page_sections')) {
      return json(route, { data: homeSections });
    }
    if (pathname.endsWith('/items/page_section_items')) {
      return json(route, { data: homeItems });
    }
    if (pathname.endsWith('/items/site_settings')) {
      return json(route, {
        data: {
          announcement_banner: {
            id: 'banner-main',
            status: 'published',
            internal_name: 'Баннер в шапке',
            short_text: 'Новые условия доставки опубликованы',
          },
        },
      });
    }
    if (pathname.endsWith('/items/storefront_collection') || pathname.endsWith('/items/storefront_collection_item')) {
      return json(route, { data: [] });
    }
    return route.fallback();
  });
}

async function json(route, payload) {
  return route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(payload),
  });
}
