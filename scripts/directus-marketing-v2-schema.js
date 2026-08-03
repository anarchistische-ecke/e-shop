#!/usr/bin/env node

const fs = require('node:fs');
const path = require('node:path');

const ROOT_DIR = path.resolve(__dirname, '..');
const DEFAULT_SNAPSHOT_PATH = path.join(
  ROOT_DIR,
  'directus',
  'schema',
  'schema.snapshot.json'
);
const snapshotPath = path.resolve(
  process.env.DIRECTUS_SCHEMA_SNAPSHOT_PATH || DEFAULT_SNAPSHOT_PATH
);

const STATUS_CHOICES = [
  { text: 'Черновик', value: 'draft' },
  { text: 'На проверке', value: 'in_review' },
  { text: 'Опубликовано', value: 'published' },
  { text: 'В архиве', value: 'archived' },
];
const SAFE_URL_REGEX =
  '^(?:/(?!/)[^\\s]*|#[^\\s]+|https://[^\\s]+|mailto:[^\\s]+|tel:[+0-9()\\s-]+)$';
const INTERNAL_PATH_REGEX = '^/(?!/)[^\\s]*$';

const SECTION_TYPES = [
  ['hero', 'Главный экран'],
  ['rich_text', 'Текст'],
  ['feature_list', 'Карточки преимуществ'],
  ['banner_group', 'Группа баннеров'],
  ['campaign_slot', 'Слот кампаний'],
  ['collection_rail', 'Витринная подборка'],
  ['product_reference_list', 'Товары'],
  ['category_reference_list', 'Категории'],
  ['brand_reference_list', 'Бренды'],
  ['faq', 'FAQ'],
  ['legal_document_list', 'Юридические документы'],
  ['cta', 'Призыв к действию'],
  ['image_banner', 'Баннер с изображением'],
  ['newsletter_cta', 'Подписка'],
  ['collection_teaser', 'Подборка'],
];

const CONSTRAINED_RICH_TEXT_TOOLBAR = [
  'undo',
  'redo',
  'bold',
  'italic',
  'underline',
  'h2',
  'h3',
  'h4',
  'numlist',
  'bullist',
  'removeformat',
  'blockquote',
  'customLink',
  'unlink',
  'hr',
  'fullscreen',
];

function constrainedRichTextOptions() {
  return { toolbar: [...CONSTRAINED_RICH_TEXT_TOOLBAR] };
}

function translation(label) {
  return [{ language: 'ru-RU', translation: label }];
}

function collectionTranslation(singular, plural = singular) {
  return [{
    language: 'ru-RU',
    translation: plural,
    singular,
    plural,
  }];
}

function collection(name, {
  label,
  singular = label,
  icon = 'article',
  note = null,
  group = null,
  hidden = false,
  singleton = false,
  sortField = null,
  versioning = true,
  displayTemplate = null,
  virtual = false,
} = {}) {
  return {
    collection: name,
    meta: {
      accountability: 'all',
      archive_app_filter: !virtual,
      archive_field: virtual ? null : 'status',
      archive_value: virtual ? null : 'archived',
      collapse: 'open',
      collection: name,
      color: null,
      display_template: displayTemplate,
      group,
      hidden,
      icon,
      item_duplication_fields: null,
      note,
      preview_url: null,
      singleton,
      sort: null,
      sort_field: sortField,
      translations: collectionTranslation(singular, label),
      unarchive_value: virtual ? null : 'draft',
      versioning: virtual ? false : versioning,
    },
    schema: virtual ? null : { name },
  };
}

function field(collectionName, name, type, {
  dataType,
  label = name,
  interfaceName = 'input',
  note = null,
  width = 'full',
  required = false,
  readonly = false,
  hidden = false,
  searchable = true,
  sort = null,
  group = null,
  defaultValue = null,
  maxLength = null,
  unique = false,
  indexed = false,
  primary = false,
  autoIncrement = false,
  foreignTable = null,
  foreignColumn = null,
  special = null,
  display = null,
  displayOptions = null,
  options = null,
  conditions = null,
  validation = null,
  validationMessage = null,
  schema = true,
  numericPrecision = null,
  numericScale = null,
} = {}) {
  return {
    collection: collectionName,
    field: name,
    type,
    meta: {
      collection: collectionName,
      conditions,
      display,
      display_options: displayOptions,
      field: name,
      group,
      hidden,
      interface: interfaceName,
      note,
      options,
      readonly,
      required,
      searchable,
      sort,
      special,
      translations: translation(label),
      validation,
      validation_message: validationMessage,
      width,
    },
    schema: schema ? {
      name,
      table: collectionName,
      data_type: dataType || dataTypeFor(type),
      default_value: defaultValue,
      max_length: maxLength,
      numeric_precision: numericPrecisionFor(type, numericPrecision),
      numeric_scale: numericScaleFor(type, numericScale),
      is_nullable: !required && !primary,
      is_unique: unique || primary,
      is_indexed: indexed,
      is_primary_key: primary,
      is_generated: false,
      generation_expression: null,
      has_auto_increment: autoIncrement,
      foreign_key_table: foreignTable,
      foreign_key_column: foreignColumn,
    } : null,
  };
}

function dataTypeFor(type) {
  return {
    alias: null,
    bigInteger: 'bigint',
    boolean: 'boolean',
    date: 'date',
    integer: 'integer',
    json: 'json',
    string: 'character varying',
    text: 'text',
    timestamp: 'timestamp with time zone',
    uuid: 'uuid',
  }[type] || type;
}

function numericPrecisionFor(type, override) {
  if (override !== null) return override;
  if (type === 'integer') return 32;
  if (type === 'bigInteger') return 64;
  return null;
}

function numericScaleFor(type, override) {
  if (override !== null) return override;
  return ['integer', 'bigInteger'].includes(type) ? 0 : null;
}

function idField(collectionName) {
  return field(collectionName, 'id', 'integer', {
    label: 'ID',
    interfaceName: 'numeric',
    hidden: true,
    readonly: true,
    required: true,
    sort: 1,
    primary: true,
    autoIncrement: true,
    defaultValue: `nextval('${collectionName}_id_seq'::regclass)`,
  });
}

function statusField(collectionName, sort = 2) {
  return field(collectionName, 'status', 'string', {
    label: 'Статус',
    interfaceName: 'select-dropdown',
    note: 'Опубликованные записи видны на витрине. Архив не удаляет историю.',
    width: 'half',
    required: true,
    sort,
    defaultValue: 'draft',
    maxLength: 16,
    options: { choices: STATUS_CHOICES },
  });
}

function publishedAtField(collectionName, sort = 3) {
  return field(collectionName, 'published_at', 'timestamp', {
    label: 'Дата публикации',
    interfaceName: 'datetime',
    note: 'UTC. Заполняется автоматически при публикации.',
    width: 'half',
    sort,
  });
}

function aliasField(collectionName, name, {
  label,
  interfaceName,
  note = null,
  sort = null,
  special,
  options = null,
  group = null,
  conditions = null,
} = {}) {
  return field(collectionName, name, 'alias', {
    label,
    interfaceName,
    note,
    sort,
    special,
    options,
    group,
    conditions,
    schema: false,
    searchable: false,
  });
}

function m2oField(collectionName, name, relatedCollection, {
  label,
  note = null,
  sort = null,
  required = false,
  width = 'full',
  group = null,
  conditions = null,
  displayTemplate = null,
} = {}) {
  return field(collectionName, name, 'integer', {
    label,
    interfaceName: 'select-dropdown-m2o',
    note,
    sort,
    required,
    width,
    group,
    conditions,
    foreignTable: relatedCollection,
    foreignColumn: 'id',
    display: 'related-values',
    displayOptions: displayTemplate ? { template: displayTemplate } : null,
  });
}

function relation(manyCollection, manyField, oneCollection, {
  oneField = null,
  sortField = null,
  onDelete = 'SET NULL',
} = {}) {
  return {
    collection: manyCollection,
    field: manyField,
    related_collection: oneCollection,
    meta: {
      junction_field: null,
      many_collection: manyCollection,
      many_field: manyField,
      one_allowed_collections: null,
      one_collection: oneCollection,
      one_collection_field: null,
      one_deselect_action: 'nullify',
      one_field: oneField,
      sort_field: sortField,
    },
    schema: {
      table: manyCollection,
      column: manyField,
      foreign_key_table: oneCollection,
      foreign_key_column: 'id',
      constraint_name: `${manyCollection}_${manyField}_foreign`,
      on_update: 'NO ACTION',
      on_delete: onDelete,
    },
  };
}

function choices(values) {
  return { choices: values.map(([value, text]) => ({ text, value })) };
}

function condition(name, sectionTypes, overrides = {}) {
  return {
    name,
    rule: { section_type: { _in: sectionTypes } },
    ...overrides,
  };
}

function pickerField(collectionName, name, entity, {
  label,
  note,
  sort,
  group = null,
  conditions = null,
  hidden = false,
} = {}) {
  const config = {
    product: {
      endpoint: '/storefront-ops-bridge/workspace/products',
      valueField: 'slug',
      fallbackValueField: 'id',
      labelField: 'name',
    },
    category: {
      endpoint: '/storefront-ops-bridge/workspace/categories',
      valueField: 'slug',
      fallbackValueField: 'id',
      labelField: 'name',
    },
    brand: {
      endpoint: '/storefront-ops-bridge/workspace/brands',
      valueField: 'slug',
      fallbackValueField: 'id',
      labelField: 'name',
    },
    promotion: {
      endpoint: '/storefront-ops-bridge/admin/promotions',
      valueField: 'id',
      labelField: 'name',
    },
    promo_code: {
      endpoint: '/storefront-ops-bridge/admin/promo-codes',
      valueField: 'id',
      labelField: 'code',
    },
  }[entity];

  return field(collectionName, name, 'string', {
    label,
    interfaceName: 'storefront-entity-picker',
    note,
    sort,
    group,
    conditions,
    hidden,
    maxLength: entity === 'promo_code' || entity === 'promotion' ? 64 : 255,
    options: { entity, ...config },
  });
}

function upsertBy(items, keyFn, item) {
  const key = keyFn(item);
  const index = items.findIndex((candidate) => keyFn(candidate) === key);
  if (index === -1) {
    items.push(item);
  } else {
    items[index] = item;
  }
}

function patchField(snapshot, collectionName, fieldName, patcher) {
  const target = snapshot.fields.find(
    (candidate) => candidate.collection === collectionName && candidate.field === fieldName
  );
  if (!target) {
    throw new Error(`Required legacy field ${collectionName}.${fieldName} is absent`);
  }
  patcher(target);
}

function addBaseCollection(snapshot, definition, fields) {
  upsertBy(snapshot.collections, (item) => item.collection, definition);
  fields.forEach((item) => upsertBy(
    snapshot.fields,
    (candidate) => `${candidate.collection}.${candidate.field}`,
    item
  ));
}

function addJunction(snapshot, {
  collectionName,
  label,
  leftCollection,
  leftField,
  rightCollection,
  rightField,
  leftAlias,
  rightAlias = null,
}) {
  addBaseCollection(
    snapshot,
    collection(collectionName, {
      label,
      singular: label,
      icon: 'link',
      note: 'Техническая связь. Редактируется внутри родительской записи.',
      hidden: true,
      versioning: false,
      sortField: 'sort',
    }),
    [
      idField(collectionName),
      statusField(collectionName),
      publishedAtField(collectionName),
      m2oField(collectionName, leftField, leftCollection, {
        label: 'Родитель',
        sort: 4,
        required: true,
      }),
      m2oField(collectionName, rightField, rightCollection, {
        label: 'Связанная запись',
        sort: 5,
        required: true,
      }),
      field(collectionName, 'sort', 'integer', {
        label: 'Порядок',
        interfaceName: 'input',
        sort: 6,
        defaultValue: 0,
      }),
    ]
  );

  upsertBy(snapshot.relations, (item) => `${item.collection}.${item.field}`, relation(
    collectionName,
    leftField,
    leftCollection,
    { oneField: leftAlias, sortField: 'sort', onDelete: 'CASCADE' }
  ));
  upsertBy(snapshot.relations, (item) => `${item.collection}.${item.field}`, relation(
    collectionName,
    rightField,
    rightCollection,
    { oneField: rightAlias, onDelete: 'CASCADE' }
  ));
}

function applyAuthoringMeta(snapshot) {
  // Directus 11.17 rejects folder collections (`schema: null`) in /schema/diff.
  // The schema apply helper provisions those metadata-only folders separately.
  snapshot.collections = snapshot.collections.filter(
    (item) => !['cms_marketing', 'cms_site_content'].includes(item.collection)
  );

  const marketingCollections = new Set(['campaign', 'banner', 'storefront_collection']);
  const siteCollections = new Set([
    'page',
    'navigation',
    'faq',
    'legal_documents',
    'site_settings',
  ]);
  const technicalCollections = new Set([
    'navigation_items',
    'page_sections',
    'page_section_items',
    'page_section_banners',
    'page_section_faqs',
    'page_section_legal_documents',
    'catalogue_overlay_block_item',
    'storefront_collection_item',
    'catalogue_overlay_block',
    'category_overlay',
    'product_overlay',
    'post',
  ]);

  snapshot.collections.forEach((item) => {
    if (!item.meta) return;
    if (marketingCollections.has(item.collection)) item.meta.group = 'cms_marketing';
    if (siteCollections.has(item.collection)) item.meta.group = 'cms_site_content';
    if (technicalCollections.has(item.collection)) item.meta.hidden = true;
  });

  const displayTemplates = {
    page: '{{title}} · {{path}}',
    page_sections: '{{internal_name}} · {{section_type}}',
    page_section_items: '{{title}}{{label}} · {{reference_key}}',
    navigation: '{{title}} · {{placement}}',
    navigation_items: '{{label}} · {{url}}',
    banner: '{{internal_name}} · {{placement}}',
    campaign: '{{internal_name}} · {{status}}',
    faq: '{{question}}',
    legal_documents: '{{title}} · {{document_key}}',
    storefront_collection: '{{title}} · {{key}}',
  };
  snapshot.collections.forEach((item) => {
    if (item.meta && displayTemplates[item.collection]) {
      item.meta.display_template = displayTemplates[item.collection];
    }
  });
  const previewCollections = new Set(['page', 'campaign', 'banner']);
  snapshot.collections.forEach((item) => {
    if (item.meta && previewCollections.has(item.collection)) {
      item.meta.preview_url =
        `/storefront-ops-bridge/cms-preview/${item.collection}/{{id}}?version={{version}}`;
    }
  });

  snapshot.fields.forEach((item) => {
    if (item.field === 'migration_key' && item.meta) {
      item.meta.hidden = true;
      item.meta.readonly = true;
      item.meta.note = 'Технический ключ миграции. Не изменять.';
    }
  });

  patchField(snapshot, 'faq', 'answer', (item) => {
    item.meta.interface = 'input-rich-text-html';
    item.meta.options = constrainedRichTextOptions();
    item.meta.note = 'Используйте заголовки, абзацы, списки и безопасные ссылки.';
  });
  patchField(snapshot, 'legal_documents', 'body_html', (item) => {
    item.meta.interface = 'input-rich-text-html';
    item.meta.options = constrainedRichTextOptions();
    item.meta.note = 'HTML очищается сервером по строгому списку разрешённых элементов.';
  });
  patchField(snapshot, 'page_sections', 'body', (item) => {
    item.meta.interface = 'input-rich-text-html';
    item.meta.options = constrainedRichTextOptions();
    item.meta.note = 'Форматированный текст. Скрипты, стили и небезопасные ссылки удаляются.';
    item.meta.group = 'content_group';
    item.meta.conditions = [
      condition('Текстовые блоки', ['rich_text', 'hero', 'cta', 'image_banner']),
    ];
  });
}

function applyLegacyRelations(snapshot) {
  const aliases = [
    aliasField('page', 'sections', {
      label: 'Блоки страницы',
      interfaceName: 'list-o2m',
      note: 'Добавляйте и перетаскивайте блоки в порядке их отображения.',
      sort: 20,
      special: ['o2m'],
      options: {
        enableCreate: true,
        enableSelect: false,
        template: '{{internal_name}} · {{section_type}}',
      },
    }),
    aliasField('page_sections', 'items', {
      label: 'Карточки и ссылки',
      interfaceName: 'list-o2m',
      note: 'Порядок меняется перетаскиванием.',
      sort: 50,
      special: ['o2m'],
      group: 'references_group',
      options: {
        enableCreate: true,
        enableSelect: false,
        template: '{{title}}{{label}} · {{reference_key}}',
      },
    }),
    aliasField('navigation', 'items', {
      label: 'Пункты меню',
      interfaceName: 'list-o2m',
      note: 'Порядок меняется перетаскиванием.',
      sort: 20,
      special: ['o2m'],
      options: {
        enableCreate: true,
        enableSelect: false,
        template: '{{label}} · {{url}}',
      },
    }),
    aliasField('campaign', 'banners', {
      label: 'Креативы кампании',
      interfaceName: 'list-o2m',
      note: 'Добавьте отдельный креатив для каждого нужного размещения.',
      sort: 30,
      special: ['o2m'],
      options: {
        enableCreate: true,
        enableSelect: true,
        template: '{{internal_name}} · {{placement}}',
      },
    }),
  ];
  aliases.forEach((item) => upsertBy(
    snapshot.fields,
    (candidate) => `${candidate.collection}.${candidate.field}`,
    item
  ));

  const updates = [
    ['page_sections', 'page', 'page', 'sections', 'sort'],
    ['page_section_items', 'page_section', 'page_sections', 'items', 'sort'],
    ['navigation_items', 'navigation', 'navigation', 'items', 'sort'],
    ['banner', 'campaign', 'campaign', 'banners', 'sort'],
  ];
  updates.forEach(([manyCollection, manyField, oneCollection, oneField, sortField]) => {
    upsertBy(snapshot.relations, (item) => `${item.collection}.${item.field}`, relation(
      manyCollection,
      manyField,
      oneCollection,
      { oneField, sortField }
    ));
  });
}

function addCampaign(snapshot) {
  addBaseCollection(
    snapshot,
    collection('campaign', {
      label: 'Кампании',
      singular: 'Кампания',
      icon: 'campaign',
      note: 'Маркетинговая подача. Скидки и право применения остаются в коммерческом модуле.',
      group: 'cms_marketing',
      sortField: 'sort',
      versioning: true,
      displayTemplate: '{{internal_name}} · {{status}}',
    }),
    [
      idField('campaign'),
      statusField('campaign'),
      publishedAtField('campaign'),
      field('campaign', 'internal_name', 'string', {
        label: 'Внутреннее название',
        note: 'Не показывается покупателям.',
        required: true,
        sort: 4,
        maxLength: 255,
      }),
      field('campaign', 'slug', 'string', {
        label: 'Slug промо-страницы',
        note: 'Латиница, цифры и дефисы. Маршрут: /promo/slug.',
        required: true,
        unique: true,
        indexed: true,
        sort: 5,
        maxLength: 160,
        validation: { _regex: '^[a-z0-9]+(?:-[a-z0-9]+)*$' },
        validationMessage: 'Используйте латинские строчные буквы, цифры и дефисы.',
      }),
      field('campaign', 'priority', 'integer', {
        label: 'Приоритет',
        note: 'Большее число показывается раньше.',
        width: 'half',
        sort: 6,
        defaultValue: 0,
      }),
      field('campaign', 'sort', 'integer', {
        label: 'Порядок при равном приоритете',
        width: 'half',
        sort: 7,
        defaultValue: 0,
      }),
      field('campaign', 'active_from', 'timestamp', {
        label: 'Начало показа (UTC)',
        interfaceName: 'datetime',
        note: 'Включительно.',
        width: 'half',
        sort: 8,
      }),
      field('campaign', 'active_to', 'timestamp', {
        label: 'Окончание показа (UTC)',
        interfaceName: 'datetime',
        note: 'Не включительно. Должно быть позже начала; это проверяется при сохранении.',
        width: 'half',
        sort: 9,
      }),
      field('campaign', 'operational_link_type', 'string', {
        label: 'Источник скидки',
        interfaceName: 'select-dropdown',
        note: 'CMS показывает кампанию, но не рассчитывает скидку.',
        required: true,
        width: 'half',
        sort: 10,
        maxLength: 24,
        defaultValue: 'none',
        options: choices([
          ['none', 'Без скидки'],
          ['promotion', 'Акция из коммерческого модуля'],
          ['promo_code', 'Промокод из коммерческого модуля'],
        ]),
      }),
      pickerField('campaign', 'promotion_id', 'promotion', {
        label: 'Акция',
        note: 'Выберите действующую или запланированную акцию. Значение только для чтения на витрине.',
        sort: 11,
        hidden: true,
        conditions: [{
          name: 'Только для акции',
          rule: { operational_link_type: { _eq: 'promotion' } },
          hidden: false,
          required: true,
        }],
      }),
      pickerField('campaign', 'promo_code_id', 'promo_code', {
        label: 'Промокод',
        note: 'Выберите промокод; ввод идентификатора вручную не требуется.',
        sort: 12,
        hidden: true,
        conditions: [{
          name: 'Только для промокода',
          rule: { operational_link_type: { _eq: 'promo_code' } },
          hidden: false,
          required: true,
        }],
      }),
      m2oField('campaign', 'landing_page', 'page', {
        label: 'Посадочная страница',
        note: 'Необязательно. Если не выбрана, /promo/:slug использует креатив кампании.',
        sort: 13,
        displayTemplate: '{{title}} · {{path}}',
      }),
      m2oField('campaign', 'storefront_collection', 'storefront_collection', {
        label: 'Витринная подборка',
        note: 'Необязательная подборка товаров кампании.',
        sort: 14,
        displayTemplate: '{{title}} · {{key}}',
      }),
    ]
  );

  upsertBy(snapshot.relations, (item) => `${item.collection}.${item.field}`, relation(
    'campaign',
    'landing_page',
    'page'
  ));
  upsertBy(snapshot.relations, (item) => `${item.collection}.${item.field}`, relation(
    'campaign',
    'storefront_collection',
    'storefront_collection'
  ));
}

function expandBanner(snapshot) {
  const bannerFields = [
    m2oField('banner', 'campaign', 'campaign', {
      label: 'Кампания',
      note: 'Кампания задаёт общий период и связь с коммерческой акцией.',
      sort: 5,
      displayTemplate: '{{internal_name}}',
    }),
    field('banner', 'placement', 'string', {
      label: 'Размещение',
      interfaceName: 'select-dropdown',
      note: 'Определяет поверхность витрины.',
      required: true,
      sort: 6,
      maxLength: 40,
      defaultValue: 'page_inline',
      options: choices([
        ['sitewide_announcement', 'Объявление на всём сайте'],
        ['home_hero', 'Главный экран главной'],
        ['home_promo', 'Промо на главной'],
        ['home_highlight', 'Выделенная зона главной'],
        ['page_inline', 'Внутри страницы'],
      ]),
    }),
    field('banner', 'priority', 'integer', {
      label: 'Приоритет',
      note: 'Большее число показывается раньше.',
      width: 'half',
      sort: 7,
      defaultValue: 0,
    }),
    field('banner', 'short_text', 'string', {
      label: 'Короткий текст',
      note: 'Обязателен для объявления в шапке.',
      sort: 8,
      maxLength: 240,
      conditions: [{
        name: 'Объявление',
        rule: { placement: { _eq: 'sitewide_announcement' } },
        required: true,
      }],
    }),
    field('banner', 'eyebrow', 'string', {
      label: 'Надзаголовок',
      width: 'half',
      sort: 9,
      maxLength: 120,
    }),
    field('banner', 'title', 'string', {
      label: 'Заголовок',
      required: false,
      sort: 10,
      maxLength: 240,
    }),
    field('banner', 'description', 'text', {
      label: 'Описание',
      interfaceName: 'input-rich-text-html',
      note: 'Разрешены базовое форматирование и безопасные ссылки.',
      options: constrainedRichTextOptions(),
      sort: 11,
    }),
    field('banner', 'image', 'uuid', {
      label: 'Изображение для компьютера',
      interfaceName: 'file-image',
      note: 'Рекомендуется WebP/JPEG, не менее 1600×700.',
      sort: 12,
      display: 'image',
    }),
    field('banner', 'image_alt', 'string', {
      label: 'Описание изображения',
      note: 'Обязательно, если загружено изображение.',
      sort: 13,
      maxLength: 255,
      conditions: [{
        name: 'Изображение загружено',
        rule: { image: { _nnull: true } },
        required: true,
      }],
    }),
    field('banner', 'mobile_image', 'uuid', {
      label: 'Изображение для телефона',
      interfaceName: 'file-image',
      note: 'Необязательно. Рекомендуется не менее 750×900.',
      sort: 14,
      display: 'image',
    }),
    field('banner', 'mobile_image_alt', 'string', {
      label: 'Описание мобильного изображения',
      sort: 15,
      maxLength: 255,
      conditions: [{
        name: 'Мобильное изображение загружено',
        rule: { mobile_image: { _nnull: true } },
        required: true,
      }],
    }),
    field('banner', 'primary_cta_label', 'string', {
      label: 'Основная кнопка',
      width: 'half',
      sort: 16,
      maxLength: 80,
    }),
    field('banner', 'primary_cta_url', 'string', {
      label: 'Ссылка основной кнопки',
      note: 'Внутренний путь, HTTPS, mailto: или tel:.',
      width: 'half',
      sort: 17,
      maxLength: 500,
      validation: { _regex: SAFE_URL_REGEX },
      validationMessage: 'Разрешены внутренние пути, HTTPS, mailto: и tel:.',
    }),
    field('banner', 'secondary_cta_label', 'string', {
      label: 'Дополнительная кнопка',
      width: 'half',
      sort: 18,
      maxLength: 80,
    }),
    field('banner', 'secondary_cta_url', 'string', {
      label: 'Ссылка дополнительной кнопки',
      width: 'half',
      sort: 19,
      maxLength: 500,
      validation: { _regex: SAFE_URL_REGEX },
      validationMessage: 'Разрешены внутренние пути, HTTPS, mailto: и tel:.',
    }),
    field('banner', 'style_variant', 'string', {
      label: 'Оформление',
      interfaceName: 'select-dropdown',
      width: 'half',
      sort: 20,
      maxLength: 24,
      defaultValue: 'default',
      options: choices([
        ['default', 'По умолчанию'],
        ['warm', 'Тёплое'],
        ['sage', 'Шалфей'],
        ['quiet', 'Спокойное'],
        ['accent', 'Акцентное'],
      ]),
    }),
    field('banner', 'layout_variant', 'string', {
      label: 'Макет',
      interfaceName: 'select-dropdown',
      width: 'half',
      sort: 21,
      maxLength: 24,
      defaultValue: 'contained',
      options: choices([
        ['contained', 'В контейнере'],
        ['full', 'На всю ширину'],
        ['media_left', 'Изображение слева'],
        ['media_right', 'Изображение справа'],
        ['cards', 'Карточки'],
      ]),
    }),
    field('banner', 'active_from', 'timestamp', {
      label: 'Начало показа креатива (UTC)',
      interfaceName: 'datetime',
      note: 'Необязательное уточнение периода кампании. Включительно.',
      width: 'half',
      sort: 22,
    }),
    field('banner', 'active_to', 'timestamp', {
      label: 'Окончание показа креатива (UTC)',
      interfaceName: 'datetime',
      note: 'Необязательное уточнение периода кампании. Не включительно и должно быть позже начала.',
      width: 'half',
      sort: 23,
    }),
  ];
  bannerFields.forEach((item) => upsertBy(
    snapshot.fields,
    (candidate) => `${candidate.collection}.${candidate.field}`,
    item
  ));

  patchField(snapshot, 'banner', 'banner_type', (item) => {
    item.meta.options = choices([
      ['announcement', 'Строка объявления'],
      ['hero', 'Главный баннер'],
      ['promo_card', 'Промо-карточка'],
      ['inline', 'Баннер внутри страницы'],
    ]);
    item.meta.note = 'Утверждённый тип креатива.';
  });
  patchField(snapshot, 'banner', 'internal_name', (item) => {
    item.meta.note = 'Внутреннее название, не показывается покупателям.';
    item.meta.required = true;
    item.schema.is_nullable = false;
  });
}

function expandPageBuilder(snapshot) {
  const groupAliases = [
    aliasField('page_sections', 'content_group', {
      label: 'Текст и заголовки',
      interfaceName: 'group-detail',
      sort: 10,
      special: ['group'],
      options: { start: 'open', headerIcon: 'title' },
    }),
    aliasField('page_sections', 'media_group', {
      label: 'Изображения',
      interfaceName: 'group-detail',
      sort: 20,
      special: ['group'],
      options: { start: 'closed', headerIcon: 'image' },
      conditions: [
        condition('Блоки с изображениями', ['hero', 'image_banner', 'cta']),
      ],
    }),
    aliasField('page_sections', 'actions_group', {
      label: 'Кнопки',
      interfaceName: 'group-detail',
      sort: 30,
      special: ['group'],
      options: { start: 'closed', headerIcon: 'ads_click' },
      conditions: [
        condition('Блоки с действиями', ['hero', 'cta', 'image_banner', 'rich_text']),
      ],
    }),
    aliasField('page_sections', 'references_group', {
      label: 'Связанный контент',
      interfaceName: 'group-detail',
      sort: 40,
      special: ['group'],
      options: { start: 'open', headerIcon: 'link' },
      conditions: [
        condition('Блоки со связями', [
          'feature_list',
          'banner_group',
          'campaign_slot',
          'collection_rail',
          'product_reference_list',
          'category_reference_list',
          'brand_reference_list',
          'faq',
          'legal_document_list',
          'collection_teaser',
        ]),
      ],
    }),
  ];
  groupAliases.forEach((item) => upsertBy(
    snapshot.fields,
    (candidate) => `${candidate.collection}.${candidate.field}`,
    item
  ));

  patchField(snapshot, 'page_sections', 'section_type', (item) => {
    item.meta.options = choices(SECTION_TYPES);
    item.meta.note = 'Тип определяет доступные поля и компонент витрины.';
    item.meta.required = true;
  });
  patchField(snapshot, 'page_sections', 'internal_name', (item) => {
    item.meta.note = 'Понятное редактору название блока, например «Летний hero».';
    item.meta.required = true;
  });
  patchField(snapshot, 'page_sections', 'image', (item) => {
    item.meta.group = 'media_group';
    item.meta.note = 'Основное изображение. Рекомендуется не менее 1400 px по ширине.';
  });
  patchField(snapshot, 'page_sections', 'image_alt', (item) => {
    item.meta.group = 'media_group';
    item.meta.conditions = [{
      name: 'Изображение загружено',
      rule: { image: { _nnull: true } },
      required: true,
    }];
    item.meta.note = 'Обязательно при изображении.';
  });
  patchField(snapshot, 'page_sections', 'mobile_image', (item) => {
    item.meta.group = 'media_group';
    item.meta.note = 'Необязательный отдельный кадр для телефонов.';
  });
  patchField(snapshot, 'page_sections', 'mobile_image_alt', (item) => {
    item.meta.group = 'media_group';
    item.meta.conditions = [{
      name: 'Мобильное изображение загружено',
      rule: { mobile_image: { _nnull: true } },
      required: true,
    }];
  });

  for (const name of ['eyebrow', 'title', 'accent']) {
    patchField(snapshot, 'page_sections', name, (item) => {
      item.meta.group = 'content_group';
    });
  }
  for (const name of [
    'primary_cta_label',
    'primary_cta_url',
    'secondary_cta_label',
    'secondary_cta_url',
  ]) {
    patchField(snapshot, 'page_sections', name, (item) => {
      item.meta.group = 'actions_group';
    });
  }
  for (const name of ['primary_cta_url', 'secondary_cta_url']) {
    patchField(snapshot, 'page_sections', name, (item) => {
      item.meta.validation = { _regex: SAFE_URL_REGEX };
      item.meta.validation_message = 'Разрешены внутренние пути, HTTPS, mailto: и tel:.';
    });
  }

  const builderFields = [
    field('page', 'robots', 'string', {
      label: 'Robots',
      interfaceName: 'select-dropdown',
      note: 'Управляет индексацией страницы.',
      sort: 16,
      maxLength: 32,
      defaultValue: 'index,follow',
      options: choices([
        ['index,follow', 'Индексировать и переходить по ссылкам'],
        ['noindex,follow', 'Не индексировать, переходить по ссылкам'],
        ['noindex,nofollow', 'Не индексировать и не переходить'],
      ]),
    }),
    field('page_sections', 'campaign_placement', 'string', {
      label: 'Размещение кампаний',
      interfaceName: 'select-dropdown',
      note: 'Кампании подбираются автоматически по активности и приоритету.',
      sort: 41,
      group: 'references_group',
      maxLength: 40,
      defaultValue: 'home_promo',
      conditions: [
        condition('Слот кампаний', ['campaign_slot'], { required: true }),
      ],
      options: choices([
        ['home_hero', 'Главный экран главной'],
        ['home_promo', 'Промо на главной'],
        ['home_highlight', 'Выделенная зона главной'],
        ['page_inline', 'Внутри страницы'],
      ]),
    }),
    field('page_sections', 'item_limit', 'integer', {
      label: 'Максимум элементов',
      note: 'Для слота кампаний по умолчанию показываются два.',
      width: 'half',
      sort: 42,
      group: 'references_group',
      defaultValue: 2,
      conditions: [
        condition('Слот кампаний', ['campaign_slot'], { required: true }),
      ],
      validation: { _between: [1, 12] },
      validationMessage: 'Укажите число от 1 до 12.',
    }),
    m2oField('page_sections', 'storefront_collection', 'storefront_collection', {
      label: 'Витринная подборка',
      note: 'Подборка выбирается из опубликованных CMS-записей.',
      sort: 43,
      group: 'references_group',
      conditions: [
        condition('Блок подборки', ['collection_rail', 'collection_teaser'], { required: true }),
      ],
      displayTemplate: '{{title}} · {{key}}',
    }),
    pickerField('page_section_items', 'product_key', 'product', {
      label: 'Товар',
      note: 'Сохраняется стабильный slug (или ID, если slug отсутствует).',
      sort: 20,
      conditions: [{
        name: 'Ссылка на товар',
        rule: { reference_kind: { _in: ['product', 'product_slug', 'product_id'] } },
        required: true,
      }],
    }),
    pickerField('page_section_items', 'category_key', 'category', {
      label: 'Категория',
      note: 'Выберите категорию из каталога.',
      sort: 21,
      conditions: [{
        name: 'Ссылка на категорию',
        rule: { reference_kind: { _in: ['category', 'category_slug', 'category_id'] } },
        required: true,
      }],
    }),
    pickerField('page_section_items', 'brand_key', 'brand', {
      label: 'Бренд',
      note: 'Выберите бренд из каталога.',
      sort: 22,
      conditions: [{
        name: 'Ссылка на бренд',
        rule: { reference_kind: { _in: ['brand', 'brand_slug', 'brand_id'] } },
        required: true,
      }],
    }),
    m2oField('page_section_items', 'storefront_collection', 'storefront_collection', {
      label: 'Витринная подборка',
      note: 'Выберите CMS-подборку.',
      sort: 23,
      conditions: [{
        name: 'Ссылка на подборку',
        rule: { reference_kind: { _in: ['collection', 'storefront_collection'] } },
        required: true,
      }],
      displayTemplate: '{{title}} · {{key}}',
    }),
  ];
  builderFields.forEach((item) => upsertBy(
    snapshot.fields,
    (candidate) => `${candidate.collection}.${candidate.field}`,
    item
  ));

  patchField(snapshot, 'page_section_items', 'reference_key', (item) => {
    item.meta.hidden = true;
    item.meta.note = 'Совместимость с предыдущей версией. Заполняется миграцией.';
  });

  upsertBy(snapshot.relations, (item) => `${item.collection}.${item.field}`, relation(
    'page_sections',
    'storefront_collection',
    'storefront_collection'
  ));
  upsertBy(snapshot.relations, (item) => `${item.collection}.${item.field}`, relation(
    'page_section_items',
    'storefront_collection',
    'storefront_collection'
  ));
}

function expandSiteContent(snapshot) {
  const fields = [
    m2oField('site_settings', 'announcement_banner', 'banner', {
      label: 'Объявление в шапке (резерв)',
      note: 'Резервная совместимая связь. При Marketing V2 приоритет имеет активная кампания.',
      sort: 16,
      displayTemplate: '{{internal_name}}',
    }),
    m2oField('navigation_items', 'page', 'page', {
      label: 'Страница сайта',
      note: 'Выберите страницу вместо ручного ввода URL.',
      sort: 10,
      conditions: [{
        name: 'Внутренняя страница',
        rule: { item_type: { _eq: 'page' } },
        required: true,
      }],
      displayTemplate: '{{title}} · {{path}}',
    }),
    field('legal_documents', 'effective_from', 'date', {
      label: 'Действует с',
      interfaceName: 'datetime',
      note: 'Дата вступления редакции в силу.',
      width: 'half',
      sort: 12,
    }),
    field('legal_documents', 'version_label', 'string', {
      label: 'Версия документа',
      note: 'Например: 2026-08.',
      width: 'half',
      sort: 13,
      maxLength: 40,
    }),
    field('legal_documents', 'change_note', 'text', {
      label: 'Комментарий к редакции',
      interfaceName: 'input-multiline',
      note: 'Внутренняя заметка для аудита.',
      sort: 14,
    }),
  ];
  fields.forEach((item) => upsertBy(
    snapshot.fields,
    (candidate) => `${candidate.collection}.${candidate.field}`,
    item
  ));

  upsertBy(snapshot.relations, (item) => `${item.collection}.${item.field}`, relation(
    'site_settings',
    'announcement_banner',
    'banner'
  ));
  upsertBy(snapshot.relations, (item) => `${item.collection}.${item.field}`, relation(
    'navigation_items',
    'page',
    'page'
  ));
}

function addSectionRelations(snapshot) {
  addJunction(snapshot, {
    collectionName: 'page_section_banners',
    label: 'Баннеры секций',
    leftCollection: 'page_sections',
    leftField: 'page_section',
    rightCollection: 'banner',
    rightField: 'banner',
    leftAlias: 'banners',
  });
  addJunction(snapshot, {
    collectionName: 'page_section_faqs',
    label: 'FAQ секций',
    leftCollection: 'page_sections',
    leftField: 'page_section',
    rightCollection: 'faq',
    rightField: 'faq',
    leftAlias: 'faqs',
  });
  addJunction(snapshot, {
    collectionName: 'page_section_legal_documents',
    label: 'Документы секций',
    leftCollection: 'page_sections',
    leftField: 'page_section',
    rightCollection: 'legal_documents',
    rightField: 'legal_document',
    leftAlias: 'legal_documents',
  });

  const aliases = [
    aliasField('page_sections', 'banners', {
      label: 'Баннеры',
      interfaceName: 'list-m2m',
      note: 'Выберите креативы для блока.',
      sort: 44,
      special: ['m2m'],
      group: 'references_group',
      conditions: [condition('Группа баннеров', ['banner_group'], { required: true })],
      options: {
        junctionFieldLocation: 'bottom',
        enableCreate: true,
        enableSelect: true,
        template: '{{banner.internal_name}} · {{banner.placement}}',
      },
    }),
    aliasField('page_sections', 'faqs', {
      label: 'Вопросы FAQ',
      interfaceName: 'list-m2m',
      note: 'Выберите и упорядочьте вопросы.',
      sort: 45,
      special: ['m2m'],
      group: 'references_group',
      conditions: [condition('FAQ', ['faq'], { required: true })],
      options: {
        junctionFieldLocation: 'bottom',
        enableCreate: true,
        enableSelect: true,
        template: '{{faq.question}}',
      },
    }),
    aliasField('page_sections', 'legal_documents', {
      label: 'Юридические документы',
      interfaceName: 'list-m2m',
      note: 'Выберите опубликованные документы для списка.',
      sort: 46,
      special: ['m2m'],
      group: 'references_group',
      conditions: [condition('Список документов', ['legal_document_list'], { required: true })],
      options: {
        junctionFieldLocation: 'bottom',
        enableCreate: false,
        enableSelect: true,
        template: '{{legal_document.title}}',
      },
    }),
  ];
  aliases.forEach((item) => upsertBy(
    snapshot.fields,
    (candidate) => `${candidate.collection}.${candidate.field}`,
    item
  ));
}

function expandStorefrontCollections(snapshot) {
  const fields = [
    pickerField('storefront_collection_item', 'product_key', 'product', {
      label: 'Товар',
      note: 'Выберите товар из каталога.',
      sort: 20,
      conditions: [{
        name: 'Товар',
        rule: { entity_kind: { _eq: 'product' } },
        required: true,
      }],
    }),
    pickerField('storefront_collection_item', 'category_key', 'category', {
      label: 'Категория',
      note: 'Выберите категорию из каталога.',
      sort: 21,
      conditions: [{
        name: 'Категория',
        rule: { entity_kind: { _eq: 'category' } },
        required: true,
      }],
    }),
  ];
  fields.forEach((item) => upsertBy(
    snapshot.fields,
    (candidate) => `${candidate.collection}.${candidate.field}`,
    item
  ));
  const legacyKey = snapshot.fields.find(
    (item) => item.collection === 'storefront_collection_item' && item.field === 'entity_key'
  );
  if (legacyKey?.meta) {
    legacyKey.meta.hidden = true;
    legacyKey.meta.note = 'Совместимость с предыдущей версией. Заполняется миграцией.';
  }
}

function applyRouteAndUrlValidation(snapshot) {
  const safeUrlFields = [
    ['navigation_items', 'url'],
    ['page_section_items', 'url'],
    ['storefront_collection', 'hero_primary_cta_url'],
    ['storefront_collection', 'hero_secondary_cta_url'],
    ['storefront_collection', 'primary_cta_url'],
  ];
  safeUrlFields.forEach(([collectionName, fieldName]) => {
    patchField(snapshot, collectionName, fieldName, (item) => {
      item.meta.validation = { _regex: SAFE_URL_REGEX };
      item.meta.validation_message =
        'Разрешены внутренние пути, HTTPS, mailto: и tel:. Ссылки // запрещены.';
    });
  });

  for (const [collectionName, fieldName] of [
    ['page', 'path'],
    ['legal_documents', 'path'],
  ]) {
    patchField(snapshot, collectionName, fieldName, (item) => {
      item.meta.validation = { _regex: INTERNAL_PATH_REGEX };
      item.meta.validation_message =
        'Путь должен начинаться с одного символа / и не содержать пробелы.';
    });
  }
}

function applyNestedPageSectionEditorCompatibility(snapshot) {
  // Directus 11.17.2 omits alias field groups from list-o2m item drawers.
  // Page sections are authored only through page.sections, so keep the
  // conditional fields top-level until the pinned Directus line is upgraded.
  const groupAliases = new Set([
    'content_group',
    'media_group',
    'actions_group',
    'references_group',
  ]);
  const byName = new Map(
    snapshot.fields
      .filter((item) => item.collection === 'page_sections')
      .map((item) => [item.field, item])
  );

  groupAliases.forEach((fieldName) => {
    const item = byName.get(fieldName);
    if (!item?.meta) return;
    item.meta.hidden = true;
    item.meta.note =
      'Техническая группа скрыта: вложенный редактор Directus 11.17.2 не отображает поля внутри alias-групп.';
  });

  const topLevelSort = {
    eyebrow: 10,
    title: 11,
    accent: 12,
    body: 13,
    image: 20,
    image_alt: 21,
    mobile_image: 22,
    mobile_image_alt: 23,
    primary_cta_label: 30,
    primary_cta_url: 31,
    secondary_cta_label: 32,
    secondary_cta_url: 33,
    campaign_placement: 40,
    item_limit: 41,
    storefront_collection: 42,
    banners: 43,
    faqs: 44,
    legal_documents: 45,
    items: 46,
  };
  Object.entries(topLevelSort).forEach(([fieldName, sort]) => {
    const item = byName.get(fieldName);
    if (!item?.meta) return;
    item.meta.group = null;
    item.meta.sort = sort;
  });

  const visibleFor = (fieldName, label, sectionTypes, { required = false } = {}) => {
    const item = byName.get(fieldName);
    if (!item?.meta) return;
    item.meta.hidden = true;
    item.meta.conditions = [
      condition(label, sectionTypes, {
        hidden: false,
        ...(required ? { required: true } : {}),
      }),
    ];
  };
  const mediaTypes = [
    'hero',
    'image_banner',
    'cta',
    'newsletter_cta',
    'collection_rail',
    'collection_teaser',
    'product_reference_list',
    'category_reference_list',
    'brand_reference_list',
  ];
  const actionTypes = [
    'hero',
    'rich_text',
    'image_banner',
    'cta',
    'newsletter_cta',
    'collection_rail',
    'collection_teaser',
    'product_reference_list',
    'category_reference_list',
    'brand_reference_list',
  ];
  const itemTypes = [
    'hero',
    'feature_list',
    'banner_group',
    'image_banner',
    'cta',
    'newsletter_cta',
    'collection_rail',
    'collection_teaser',
    'product_reference_list',
    'category_reference_list',
    'brand_reference_list',
    'faq',
  ];

  visibleFor('image', 'Блок использует изображение', mediaTypes);
  visibleFor('mobile_image', 'Блок использует мобильное изображение', mediaTypes);
  for (const fieldName of [
    'primary_cta_label',
    'primary_cta_url',
    'secondary_cta_label',
    'secondary_cta_url',
  ]) {
    visibleFor(fieldName, 'Блок поддерживает кнопки', actionTypes);
  }
  visibleFor('campaign_placement', 'Слот кампаний', ['campaign_slot'], { required: true });
  visibleFor('item_limit', 'Слот кампаний', ['campaign_slot'], { required: true });
  visibleFor(
    'storefront_collection',
    'Блок использует витринную подборку',
    ['collection_rail', 'collection_teaser'],
    { required: true }
  );
  visibleFor('banners', 'Группа баннеров', ['banner_group'], { required: true });
  visibleFor('faqs', 'FAQ', ['faq'], { required: true });
  visibleFor(
    'legal_documents',
    'Список юридических документов',
    ['legal_document_list'],
    { required: true }
  );
  visibleFor('items', 'Блок использует карточки или ссылки', itemTypes);

  for (const [fieldName, sourceField, label] of [
    ['image_alt', 'image', 'Описание основного изображения'],
    ['mobile_image_alt', 'mobile_image', 'Описание мобильного изображения'],
  ]) {
    const item = byName.get(fieldName);
    if (!item?.meta) continue;
    item.meta.hidden = true;
    item.meta.conditions = [{
      name: label,
      rule: {
        _and: [
          { section_type: { _in: mediaTypes } },
          { [sourceField]: { _nnull: true } },
        ],
      },
      hidden: false,
      required: true,
    }];
  }
}

function normalize(snapshot) {
  snapshot.fields.forEach((item) => {
    const validation = item.meta?.validation;
    if (!validation || typeof validation !== 'object' || Array.isArray(validation)) return;
    const keys = Object.keys(validation);
    const isBareOperatorFilter =
      keys.length > 0
      && keys.every((key) => key.startsWith('_'))
      && !keys.includes('_and')
      && !keys.includes('_or');
    if (isBareOperatorFilter) {
      item.meta.validation = {
        _and: [{ [item.field]: validation }],
      };
    }
  });

  snapshot.collections.sort((left, right) => left.collection.localeCompare(right.collection));
  snapshot.fields.sort((left, right) => {
    const collectionOrder = left.collection.localeCompare(right.collection);
    if (collectionOrder !== 0) return collectionOrder;
    const leftSort = left.meta?.sort ?? Number.MAX_SAFE_INTEGER;
    const rightSort = right.meta?.sort ?? Number.MAX_SAFE_INTEGER;
    if (leftSort !== rightSort) return leftSort - rightSort;
    return left.field.localeCompare(right.field);
  });
  snapshot.relations.sort((left, right) => {
    const collectionOrder = left.collection.localeCompare(right.collection);
    return collectionOrder !== 0
      ? collectionOrder
      : left.field.localeCompare(right.field);
  });
}

function main() {
  const snapshot = JSON.parse(fs.readFileSync(snapshotPath, 'utf8'));
  snapshot.collections ||= [];
  snapshot.fields ||= [];
  snapshot.relations ||= [];

  addCampaign(snapshot);
  expandBanner(snapshot);
  expandPageBuilder(snapshot);
  expandSiteContent(snapshot);
  addSectionRelations(snapshot);
  expandStorefrontCollections(snapshot);
  applyRouteAndUrlValidation(snapshot);
  applyLegacyRelations(snapshot);
  applyAuthoringMeta(snapshot);
  applyNestedPageSectionEditorCompatibility(snapshot);
  normalize(snapshot);

  fs.writeFileSync(snapshotPath, `${JSON.stringify(snapshot, null, 2)}\n`, 'utf8');
  console.log(`Applied additive Marketing V2 schema to ${snapshotPath}`);
}

main();
