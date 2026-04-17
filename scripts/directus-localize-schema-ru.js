#!/usr/bin/env node

const fs = require('fs');
const path = require('path');

const snapshotPath = process.argv[2]
  ? path.resolve(process.cwd(), process.argv[2])
  : path.resolve(__dirname, '../directus/schema/schema.snapshot.json');

const localizedCollections = new Set([
  'site_settings',
  'navigation',
  'navigation_items',
  'page',
  'page_sections',
  'page_section_items',
  'faq',
  'legal_documents',
  'banner',
  'post',
  'product_overlay',
  'category_overlay',
  'catalogue_overlay_block',
  'catalogue_overlay_block_item',
  'storefront_collection',
  'storefront_collection_item',
]);

const collectionLabels = {
  banner: { translation: 'Баннеры', singular: 'Баннер', plural: 'Баннеры' },
  faq: { translation: 'FAQ', singular: 'FAQ', plural: 'FAQ' },
  legal_documents: {
    translation: 'Юридические документы',
    singular: 'Юридический документ',
    plural: 'Юридические документы',
  },
  navigation: {
    translation: 'Навигация',
    singular: 'Группа навигации',
    plural: 'Навигация',
  },
  navigation_items: {
    translation: 'Пункты навигации',
    singular: 'Пункт навигации',
    plural: 'Пункты навигации',
  },
  page: { translation: 'Страницы', singular: 'Страница', plural: 'Страницы' },
  page_sections: {
    translation: 'Секции страниц',
    singular: 'Секция страницы',
    plural: 'Секции страниц',
  },
  page_section_items: {
    translation: 'Элементы секций',
    singular: 'Элемент секции',
    plural: 'Элементы секций',
  },
  post: { translation: 'Публикации', singular: 'Публикация', plural: 'Публикации' },
  site_settings: {
    translation: 'Настройки сайта',
    singular: 'Настройки сайта',
    plural: 'Настройки сайта',
  },
  product_overlay: {
    translation: 'Оверлеи товаров',
    singular: 'Оверлей товара',
    plural: 'Оверлеи товаров',
  },
  category_overlay: {
    translation: 'Оверлеи категорий',
    singular: 'Оверлей категории',
    plural: 'Оверлеи категорий',
  },
  catalogue_overlay_block: {
    translation: 'Блоки каталога',
    singular: 'Блок каталога',
    plural: 'Блоки каталога',
  },
  catalogue_overlay_block_item: {
    translation: 'Элементы блоков каталога',
    singular: 'Элемент блока каталога',
    plural: 'Элементы блоков каталога',
  },
  storefront_collection: {
    translation: 'Витринные подборки',
    singular: 'Витринная подборка',
    plural: 'Витринные подборки',
  },
  storefront_collection_item: {
    translation: 'Элементы витринных подборок',
    singular: 'Элемент витринной подборки',
    plural: 'Элементы витринных подборок',
  },
};

const fieldLabels = {
  id: 'ID',
  status: 'Статус',
  published_at: 'Дата публикации',
  internal_name: 'Служебное название',
  title: 'Заголовок',
  slug: 'Слаг / ЧПУ',
  path: 'URL-путь',
  summary: 'Краткое описание',
  body: 'Текст',
  body_html: 'HTML-контент',
  description: 'Описание',
  question: 'Вопрос',
  answer: 'Ответ',
  sort: 'Порядок',
  key: 'Ключ',
  document_key: 'Ключ документа',
  migration_key: 'Ключ миграции',
  placement: 'Размещение',
  template: 'Шаблон страницы',
  nav_label: 'Подпись в навигации',
  label: 'Подпись',
  url: 'URL',
  navigation: 'Группа навигации',
  item_type: 'Тип ссылки',
  open_in_new_tab: 'Открывать в новой вкладке',
  visibility: 'Видимость',
  page: 'Страница',
  page_section: 'Секция страницы',
  anchor_id: 'Якорь',
  eyebrow: 'Надзаголовок',
  accent: 'Акцент',
  primary_cta_label: 'Текст основной кнопки',
  primary_cta_url: 'Ссылка основной кнопки',
  secondary_cta_label: 'Текст второй кнопки',
  secondary_cta_url: 'Ссылка второй кнопки',
  style_variant: 'Стиль',
  layout_variant: 'Макет',
  image: 'Изображение',
  image_alt: 'Alt-текст изображения',
  mobile_image: 'Мобильное изображение',
  mobile_image_alt: 'Alt-текст мобильного изображения',
  site_name: 'Название сайта',
  brand_description: 'Описание бренда',
  support_phone: 'Телефон поддержки',
  support_email: 'Email поддержки',
  legal_entity_short: 'Краткое название юрлица',
  legal_entity_full: 'Полное название юрлица',
  legal_inn: 'ИНН',
  legal_ogrnip: 'ОГРНИП',
  legal_address: 'Юридический адрес',
  copyright_start_year: 'Год начала копирайта',
  default_seo_title_suffix: 'Суффикс SEO-заголовка по умолчанию',
  default_seo_description: 'SEO-описание по умолчанию',
  default_og_image: 'OG-изображение по умолчанию',
  product_key: 'Ключ товара',
  category_key: 'Ключ категории',
  marketing_title: 'Маркетинговый заголовок',
  marketing_subtitle: 'Маркетинговый подзаголовок',
  intro_body: 'Вступительный текст',
  badge_text: 'Текст бейджа',
  ribbon_text: 'Текст ленты',
  seo_title: 'SEO-заголовок',
  seo_description: 'SEO-описание',
  seo_image: 'SEO-изображение',
  hero_eyebrow: 'Надзаголовок первого экрана',
  hero_title: 'Заголовок первого экрана',
  hero_accent: 'Акцент первого экрана',
  hero_body: 'Текст первого экрана',
  hero_image: 'Изображение первого экрана',
  hero_image_alt: 'Alt-текст изображения первого экрана',
  hero_mobile_image: 'Мобильное изображение первого экрана',
  hero_mobile_image_alt: 'Alt-текст мобильного изображения первого экрана',
  hero_primary_cta_label: 'Текст основной кнопки первого экрана',
  hero_primary_cta_url: 'Ссылка основной кнопки первого экрана',
  hero_secondary_cta_label: 'Текст второй кнопки первого экрана',
  hero_secondary_cta_url: 'Ссылка второй кнопки первого экрана',
  hero_style_variant: 'Стиль первого экрана',
  hero_layout_variant: 'Макет первого экрана',
  linked_collection_keys: 'Ключи связанных подборок',
  owner_kind: 'Тип владельца',
  owner_key: 'Ключ владельца',
  section_type: 'Тип секции',
  block_type: 'Тип блока',
  collection_key: 'Ключ подборки',
  overlay_block: 'Родительский блок',
  reference_kind: 'Тип ссылки',
  reference_key: 'Ключ ссылки',
  behavior: 'Действие',
  storefront_collection: 'Витринная подборка',
  entity_kind: 'Тип сущности',
  entity_key: 'Ключ сущности',
  mode: 'Режим наполнения',
  rule_type: 'Тип правила',
  brand_key: 'Ключ бренда',
  limit: 'Лимит элементов',
  sort_mode: 'Сортировка',
  banner_type: 'Тип баннера',
  category: 'Категория',
  is_featured: 'Показывать как избранное',
};

const noteTranslations = {
  'Announcement bars and promo banners.': 'Плашки объявлений и промо-баннеры.',
  'Reusable FAQ entries.': 'Переиспользуемые записи FAQ.',
  'Legal and compliance documents.': 'Юридические и комплаенс-документы.',
  'Editor-managed navigation groups.': 'Группы навигации, которыми управляют редакторы.',
  'Routable editorial and service pages.': 'Редакционные и сервисные страницы с маршрутами.',
  'Optional editorial posts or news entries.': 'Необязательные редакционные публикации или новости.',
  'Singleton for global storefront settings and SEO defaults.': 'Синглтон с глобальными настройками витрины и SEO по умолчанию.',
  'Editorial storefront overlay keyed to backend products.': 'Редакционный оверлей витрины, привязанный к товарам из бэкенда.',
  'Editorial storefront overlay keyed to backend category trees.': 'Редакционный оверлей витрины, привязанный к деревьям категорий из бэкенда.',
  'Ordered merchandising blocks attached to backend products or categories.': 'Упорядоченные мерчандайзинговые блоки, привязанные к товарам или категориям из бэкенда.',
  'Repeatable cards/links inside a catalogue overlay block.': 'Повторяемые карточки и ссылки внутри блока каталога.',
  'Curated storefront collections resolved from backend rules with CMS pins/exclusions.': 'Витринные подборки, собираемые из правил бэкенда с закреплениями и исключениями из CMS.',
  'Manual pins and exclusions for curated storefront collections.': 'Ручные закрепления и исключения для витринных подборок.',
  'Only published items are public. Move editor-ready content to in_review for publisher approval.': 'Публично доступны только записи со статусом «Опубликовано». Переводите готовый контент в «На проверке», чтобы публикатор мог его утвердить.',
  'Set when content is approved for publication. Manual for now.': 'Заполняется при утверждении публикации. Пока задаётся вручную.',
  'Stable key for rerunnable content imports.': 'Стабильный ключ для повторяемого импорта контента.',
  'HTML body imported from the legacy storefront legal templates.': 'HTML-контент, импортированный из legacy-шаблонов юридических страниц витрины.',
  'Stable identifier such as footer_catalog.': 'Стабильный идентификатор, например `footer_catalog`.',
  'Parent navigation group.': 'Родительская группа навигации.',
  'Optional OG image override for this page.': 'Необязательное OG-изображение для этой страницы.',
  'Parent page section.': 'Родительская секция страницы.',
  'Optional image for this repeatable item.': 'Необязательное изображение для этого повторяемого элемента.',
  'Alt text override for the item image.': 'Пользовательский alt-текст для изображения элемента.',
  'Parent page.': 'Родительская страница.',
  'Primary section image.': 'Основное изображение секции.',
  'Alt text override for the primary section image.': 'Пользовательский alt-текст для основного изображения секции.',
  'Optional mobile-specific section image.': 'Необязательное мобильное изображение секции.',
  'Alt text override for the mobile section image.': 'Пользовательский alt-текст для мобильного изображения секции.',
  'Human-readable storefront name.': 'Человекочитаемое название витрины.',
  'Footer/about brand description.': 'Описание бренда для подвала и страницы о компании.',
  'Fallback OG/Twitter share image.': 'Резервное OG/Twitter-изображение.',
  'Backend product slug/key from the backend catalogue.': 'Ключ или slug товара из бэкенд-каталога.',
  'SEO/share image.': 'SEO/шаринговое изображение.',
  'Primary hero image.': 'Основное изображение первого экрана.',
  'Alt text override for the hero image.': 'Пользовательский alt-текст для изображения первого экрана.',
  'Optional mobile-specific hero image.': 'Необязательное мобильное изображение первого экрана.',
  'Alt text override for the mobile hero image.': 'Пользовательский alt-текст для мобильного изображения первого экрана.',
  'Comma-separated curated collection keys to feature on this entity.': 'Ключи витринных подборок через запятую, которые нужно показать на этой сущности.',
  'Backend category slug/key from the backend catalogue.': 'Ключ или slug категории из бэкенд-каталога.',
  'Whether this block belongs to a product or a category overlay.': 'Определяет, относится ли блок к оверлею товара или категории.',
  'Backend product/category slug that owns this block.': 'Ключ или slug товара/категории из бэкенда, к которым относится этот блок.',
  'Primary block image.': 'Основное изображение блока.',
  'Alt text override for the primary block image.': 'Пользовательский alt-текст для основного изображения блока.',
  'Optional mobile-specific block image.': 'Необязательное мобильное изображение блока.',
  'Alt text override for the mobile block image.': 'Пользовательский alt-текст для мобильного изображения блока.',
  'Storefront collection key when this block is a collection teaser.': 'Ключ витринной подборки, если этот блок является анонсом подборки.',
  'Parent merchandising block.': 'Родительский мерчандайзинговый блок.',
  'Stable key used by the storefront and backend facade.': 'Стабильный ключ, который используют витрина и бэкенд-фасад.',
  'Parent curated collection.': 'Родительская витринная подборка.',
};

const choiceTranslations = {
  status: {
    draft: 'Черновик',
    in_review: 'На проверке',
    published: 'Опубликовано',
    archived: 'В архиве',
  },
  banner_type: {
    announcement: 'Объявление',
    promo: 'Промо',
  },
  placement: {
    header: 'Шапка',
    footer: 'Подвал',
    legal: 'Юридический раздел',
    utility: 'Служебная зона',
  },
  item_type: {
    internal_page: 'Страница сайта',
    internal_path: 'Внутренний путь',
    external_url: 'Внешний URL',
    anchor: 'Якорь',
  },
  visibility: {
    all: 'Все',
    guest_only: 'Только гости',
    authenticated_only: 'Только авторизованные',
  },
  template: {
    home: 'Главная',
    content: 'Контентная',
    legal_hub: 'Юридический раздел',
    faq: 'FAQ',
    landing: 'Лендинг',
  },
  reference_kind: {
    none: 'Нет',
    product_slug: 'Slug товара',
    category_slug: 'Slug категории',
    external_url: 'Внешний URL',
    product: 'Товар',
    category: 'Категория',
    collection: 'Подборка',
    url: 'URL',
  },
  section_type: {
    hero: 'Первый экран',
    rich_text: 'Текстовый блок',
    feature_list: 'Список преимуществ',
    banner_group: 'Группа баннеров',
    newsletter_cta: 'CTA подписки',
    faq_list: 'Список FAQ',
    legal_documents_list: 'Список юридических документов',
    post_list: 'Список публикаций',
    product_reference_list: 'Список товаров',
    category_reference_list: 'Список категорий',
  },
  block_type: {
    hero: 'Первый экран',
    rich_text: 'Текстовый блок',
    feature_list: 'Список преимуществ',
    banner_group: 'Группа баннеров',
    collection_teaser: 'Анонс подборки',
    product_reference_list: 'Список товаров',
    category_reference_list: 'Список категорий',
  },
  style_variant: {
    default: 'По умолчанию',
    warm: 'Тёплый',
    sage: 'Шалфей',
    quiet: 'Спокойный',
    legal: 'Юридический',
  },
  layout_variant: {
    contained: 'По контейнеру',
    full: 'Во всю ширину',
    two_column: 'Две колонки',
    cards: 'Карточки',
  },
  hero_style_variant: {
    default: 'По умолчанию',
    warm: 'Тёплый',
    sage: 'Шалфей',
    quiet: 'Спокойный',
    legal: 'Юридический',
  },
  hero_layout_variant: {
    contained: 'По контейнеру',
    full: 'Во всю ширину',
    two_column: 'Две колонки',
    cards: 'Карточки',
  },
  owner_kind: {
    product: 'Товар',
    category: 'Категория',
  },
  behavior: {
    default: 'По умолчанию',
    pin: 'Закрепить',
    exclude: 'Исключить',
  },
  mode: {
    manual: 'Вручную',
    backend_rule: 'Правило бэкенда',
    hybrid: 'Гибрид',
  },
  rule_type: {
    new: 'Новинки',
    bestsellers: 'Хиты продаж',
    category: 'Категория',
    brand: 'Бренд',
    sale: 'Распродажа',
  },
  sort_mode: {
    default: 'По умолчанию',
    newest: 'Сначала новые',
    oldest: 'Сначала старые',
    alphabetical: 'По алфавиту',
    price_asc: 'Цена: по возрастанию',
    price_desc: 'Цена: по убыванию',
  },
  entity_kind: {
    product: 'Товар',
    category: 'Категория',
  },
};

function upsertFieldTranslation(existing, translation) {
  const safeExisting = Array.isArray(existing) ? existing.filter(Boolean) : [];
  const filtered = safeExisting.filter((entry) => entry.language !== 'ru-RU');
  filtered.push({ language: 'ru-RU', translation });
  return filtered;
}

function upsertCollectionTranslation(existing, translation) {
  const safeExisting = Array.isArray(existing) ? existing.filter(Boolean) : [];
  const filtered = safeExisting.filter((entry) => entry.language !== 'ru-RU');
  filtered.push({ language: 'ru-RU', ...translation });
  return filtered;
}

function localizeNote(note) {
  if (typeof note !== 'string' || note.length === 0) {
    return note;
  }
  return noteTranslations[note] || note;
}

function localizeChoices(fieldKey, options) {
  if (!options || !Array.isArray(options.choices)) {
    return options;
  }
  const fieldChoiceMap = choiceTranslations[fieldKey];
  if (!fieldChoiceMap) {
    return options;
  }
  return {
    ...options,
    choices: options.choices.map((choice) => {
      if (!choice || typeof choice !== 'object') {
        return choice;
      }
      const translatedText = fieldChoiceMap[choice.value];
      return translatedText ? { ...choice, text: translatedText } : choice;
    }),
  };
}

const snapshot = JSON.parse(fs.readFileSync(snapshotPath, 'utf8'));

snapshot.collections = snapshot.collections.map((entry) => {
  if (!localizedCollections.has(entry.collection) || !entry.meta) {
    return entry;
  }

  const localizedMeta = {
    ...entry.meta,
    note: localizeNote(entry.meta.note),
  };

  const translation = collectionLabels[entry.collection];
  if (translation) {
    localizedMeta.translations = upsertCollectionTranslation(entry.meta.translations, translation);
  }

  return {
    ...entry,
    meta: localizedMeta,
  };
});

snapshot.fields = snapshot.fields.map((entry) => {
  if (!localizedCollections.has(entry.collection) || !entry.meta) {
    return entry;
  }

  const localizedMeta = {
    ...entry.meta,
    note: localizeNote(entry.meta.note),
    options: localizeChoices(entry.field, entry.meta.options),
  };

  const translation = fieldLabels[entry.field];
  if (translation) {
    localizedMeta.translations = upsertFieldTranslation(entry.meta.translations, translation);
  }

  return {
    ...entry,
    meta: localizedMeta,
  };
});

fs.writeFileSync(snapshotPath, `${JSON.stringify(snapshot, null, 2)}\n`);
console.log(`Localized Directus schema snapshot to Russian: ${snapshotPath}`);
