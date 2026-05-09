const htmlList = (items) => `<ul>${items.map((item) => `<li>${item}</li>`).join('')}</ul>`;
const htmlOrderedList = (items) => `<ol>${items.map((item) => `<li>${item}</li>`).join('')}</ol>`;
const paragraphs = (...items) => items.map((item) => `<p>${item}</p>`).join('');

const siteSettings = {
  site_name: 'Постельное Белье-ЮГ',
  brand_description:
    'Спокойный дом начинается с мягких тканей. Мы подбираем натуральные материалы, чтобы отдых был таким же уютным, как объятия любимого пледа.',
  support_phone: '+7 961 466-88-33',
  support_email: 'postel-yug@yandex.ru',
  legal_entity_short: 'ИП Касьянова И.Л.',
  legal_entity_full: 'Индивидуальный предприниматель Касьянова И.Л.',
  legal_inn: '081407505907',
  legal_ogrnip: '325080000035116',
  legal_address: 'Респ. Калмыкия, г. Элиста, МКР. 10, д. 15, к. 2 кв. 57',
  copyright_start_year: 2015,
  default_seo_title_suffix: 'Постельное Белье-ЮГ',
  default_seo_description:
    'Домашний текстиль для уютного дома: доставка по России, честные условия покупки и собственное производство.',
};

const navigation = [
  {
    key: 'header_main',
    title: 'Полезное',
    placement: 'header',
    description: 'Основные сервисные ссылки в шапке сайта.',
    sort: 1,
    items: [
      { migration_key: 'initial:header_main:about', label: 'О бренде', url: '/about', sort: 1 },
      { migration_key: 'initial:header_main:delivery', label: 'Доставка', url: '/info/delivery', sort: 2 },
      { migration_key: 'initial:header_main:payment', label: 'Оплата', url: '/info/payment', sort: 3 },
      { migration_key: 'initial:header_main:legal', label: 'Документы', url: '/info/legal', sort: 4 },
    ],
  },
  {
    key: 'footer_catalog',
    title: 'Каталог',
    placement: 'footer',
    description: 'Основные ссылки каталога в футере.',
    sort: 1,
    items: [
      { migration_key: 'initial:footer_catalog:all', label: 'Все категории', url: '/catalog', sort: 1 },
      { migration_key: 'initial:footer_catalog:popular', label: 'Бестселлеры', url: '/category/popular', sort: 2 },
      { migration_key: 'initial:footer_catalog:new', label: 'Новинки', url: '/category/new', sort: 3 },
    ],
  },
  {
    key: 'footer_service',
    title: 'Сервис',
    placement: 'footer',
    description: 'Сервисные страницы в футере.',
    sort: 2,
    items: [
      { migration_key: 'initial:footer_service:delivery', label: 'Доставка и самовывоз', url: '/info/delivery', sort: 1 },
      { migration_key: 'initial:footer_service:return', label: 'Доставка и возврат', url: '/usloviya-prodazhi#return', sort: 2 },
      { migration_key: 'initial:footer_service:payment', label: 'Способы оплаты', url: '/info/payment', sort: 3 },
      { migration_key: 'initial:footer_service:promocodes', label: 'Акции и промокоды', url: '/account#promocodes', sort: 4 },
      { migration_key: 'initial:footer_service:production', label: 'Производство', url: '/info/production', sort: 5 },
    ],
  },
  {
    key: 'footer_account',
    title: 'Аккаунт',
    placement: 'footer',
    description: 'Пользовательские ссылки в футере.',
    sort: 3,
    items: [
      { migration_key: 'initial:footer_account:login', label: 'Войти', url: '/login', sort: 1 },
      { migration_key: 'initial:footer_account:account', label: 'Личный кабинет', url: '/account', sort: 2 },
      { migration_key: 'initial:footer_account:cart', label: 'Корзина', url: '/cart', sort: 3 },
    ],
  },
  {
    key: 'footer_legal',
    title: 'Документы',
    placement: 'footer',
    description: 'Юридические документы в футере.',
    sort: 4,
    items: [
      { migration_key: 'initial:footer_legal:hub', label: 'Реквизиты и документы', url: '/info/legal', sort: 1 },
      {
        migration_key: 'initial:footer_legal:privacy',
        label: 'Политика обработки персональных данных',
        url: '/konfidentsialnost-i-zashchita-informatsii',
        sort: 2,
      },
      { migration_key: 'initial:footer_legal:sales', label: 'Условия продажи', url: '/usloviya-prodazhi', sort: 3 },
    ],
  },
];

const pages = [
  {
    path: '/',
    slug: 'home',
    title: 'Домашний текстиль для уютного дома',
    nav_label: 'Главная',
    template: 'home',
    summary:
      'Редакционная витрина с hero-блоком, сервисными преимуществами, популярными разделами и CMS-подборками.',
    seo_title: 'Домашний текстиль для уютного дома',
    seo_description:
      'Домашний текстиль для уютного дома: доставка по России, честные условия покупки и собственное производство.',
    sections: [
      {
        migration_key: 'initial:page:home:hero',
        internal_name: 'Главная — editorial-commerce hero',
        section_type: 'hero',
        sort: 1,
        eyebrow: 'Постельное Белье-ЮГ',
        title: 'Постель, которая остается свежей',
        accent: 'ночь за ночью',
        body: paragraphs(
          'Выберите crisp-перкаль, гладкий сатин или мягкие комплекты для всей кровати.',
          'Редакционные подсказки помогают понять ткань и быстро перейти к покупке без лишних экранов.'
        ),
        primary_cta_label: 'Смотреть бестселлеры',
        primary_cta_url: '/category/kpb',
        secondary_cta_label: 'Найти свою ткань',
        secondary_cta_url: '/catalog?query=ткань',
        style_variant: 'warm',
        layout_variant: 'media_right',
        items: [
          {
            migration_key: 'initial:page:home:hero:shipping',
            label: 'Доставка',
            title: 'Условия доставки',
            description: 'Финальная стоимость и способ доставки видны до оплаты.',
            url: '/info/delivery',
            sort: 1,
          },
          {
            migration_key: 'initial:page:home:hero:payment',
            label: 'Оплата',
            title: 'Карта или СБП',
            description: 'Оплата проходит через защищённую платёжную форму.',
            url: '/info/payment',
            sort: 2,
          },
          {
            migration_key: 'initial:page:home:hero:materials',
            label: 'Материалы',
            title: 'Ткань и пошив под контролем',
            description: 'Собственное производство и понятные материалы.',
            url: '/info/production',
            sort: 3,
          },
        ],
      },
      {
        migration_key: 'initial:page:home:bestsellers',
        internal_name: 'Главная — бестселлеры',
        section_type: 'product_reference_list',
        sort: 2,
        eyebrow: 'С чего начать',
        title: 'Бестселлеры, которые быстро объясняют выбор',
        body:
          '<p>Первые товары на главной дают цену, материал, рейтинг и понятное действие без длинного предварительного рассказа.</p>',
        primary_cta_label: 'Все бестселлеры',
        primary_cta_url: '/category/kpb',
        layout_variant: 'cards',
        items: [
          {
            migration_key: 'initial:page:home:bestsellers:stripe-silver',
            reference_kind: 'product_slug',
            reference_key: 'stripe-muslin серебро платина ',
            sort: 1,
          },
          {
            migration_key: 'initial:page:home:bestsellers:cassete',
            reference_kind: 'product_slug',
            reference_key: 'Жемчуг пепел ',
            sort: 2,
          },
          {
            migration_key: 'initial:page:home:bestsellers:lindor',
            reference_kind: 'product_slug',
            reference_key: 'lindor-jadore Лаванда перламутр',
            sort: 3,
          },
        ],
      },
      {
        migration_key: 'initial:page:home:fabric-guide',
        internal_name: 'Главная — shop by feel',
        section_type: 'feature_list',
        sort: 3,
        eyebrow: 'Найти свою ткань',
        title: 'Выберите по ощущению, а не по названию ткани',
        body:
          '<p>Эти редакционные карточки можно заменить в CMS на реальные описания тканей, температурные подсказки и ссылки на соответствующие коллекции.</p>',
        primary_cta_label: 'Открыть каталог',
        primary_cta_url: '/catalog',
        layout_variant: 'full',
        items: [
          {
            migration_key: 'initial:page:home:fabric-guide:percale',
            label: 'Cool & crisp',
            title: 'Перкаль',
            description: 'Матовая хлопковая ткань для тех, кто любит прохладное, свежее ощущение.',
            sort: 1,
          },
          {
            migration_key: 'initial:page:home:fabric-guide:sateen',
            label: 'Smooth & soft',
            title: 'Сатин',
            description: 'Гладкая поверхность с мягким блеском, которая ощущается плотнее и теплее.',
            sort: 2,
          },
          {
            migration_key: 'initial:page:home:fabric-guide:linen',
            label: 'Relaxed texture',
            title: 'Лен',
            description: 'Живая фактура, свободная посадка и ощущение спальни без лишней парадности.',
            sort: 3,
          },
          {
            migration_key: 'initial:page:home:fabric-guide:bundle',
            label: 'Bundle-ready',
            title: 'Готовый комплект',
            description: 'Простыня, пододеяльник, наволочки и акценты в одной спокойной палитре.',
            sort: 4,
          },
        ],
      },
      {
        migration_key: 'initial:page:home:benefits',
        internal_name: 'Главная — trust row',
        section_type: 'feature_list',
        sort: 4,
        eyebrow: 'Покупать проще',
        title: 'Важные условия видны до оформления',
        body: '<p>Компактный trust-блок закрывает вопросы доставки, возврата, материалов и оплаты.</p>',
        style_variant: 'sage',
        layout_variant: 'full',
        items: [
          {
            migration_key: 'initial:page:home:benefits:delivery',
            title: 'Доставка по России',
            description: 'Финальная стоимость и способ доставки видны до оплаты.',
            sort: 1,
          },
          {
            migration_key: 'initial:page:home:benefits:return',
            title: 'Возврат без скрытых условий',
            description: 'Правила возврата и обмена доступны в документах до покупки.',
            sort: 2,
          },
          {
            migration_key: 'initial:page:home:benefits:production',
            title: 'Контроль ткани и пошива',
            description: 'Собственное производство помогает держать качество стабильным.',
            sort: 3,
          },
          {
            migration_key: 'initial:page:home:benefits:payment',
            title: 'Оплата картой или СБП',
            description: 'Защищённая платёжная форма без сохранения данных карты на сайте.',
            sort: 4,
          },
        ],
      },
      {
        migration_key: 'initial:page:home:reviews',
        internal_name: 'Главная — reviews and UGC placeholders',
        section_type: 'feature_list',
        sort: 5,
        eyebrow: 'Отзывы и proof',
        title: 'Любят за ощущение ткани и спокойный сервис',
        body:
          '<p>Замените эти карточки на реальные выдержки из отзывов, пресс-упоминания, награды или UGC-фото после накопления контента.</p>',
        style_variant: 'quiet',
        layout_variant: 'cards',
        items: [
          {
            migration_key: 'initial:page:home:reviews:sateen',
            label: 'Покупатель, Москва',
            title: 'Сатин плотный, но не жаркий',
            description: 'Комплект быстро расправляется на кровати, цвет спокойный и хорошо держится после стирки.',
            sort: 1,
          },
          {
            migration_key: 'initial:page:home:reviews:bundle',
            label: 'Покупатель, Ростов-на-Дону',
            title: 'Легко собрать спальню целиком',
            description: 'Комплекты и пледы смотрятся вместе, не пришлось отдельно подбирать оттенки.',
            sort: 2,
          },
          {
            migration_key: 'initial:page:home:reviews:service',
            label: 'Покупатель, Краснодар',
            title: 'Перед оплатой все понятно',
            description: 'Условия доставки и оплаты видны до оформления, менеджер быстро подтвердил заказ.',
            sort: 3,
          },
        ],
      },
      {
        migration_key: 'initial:page:home:conversion-cta',
        internal_name: 'Главная — lower funnel CTA',
        section_type: 'newsletter_cta',
        sort: 6,
        eyebrow: 'Готовы выбрать',
        title: 'Соберите кровать из проверенных комплектов',
        body:
          '<p>Начните с бестселлеров, добавьте плед или выберите ткань по ощущению. Финальная стоимость доставки и условия оплаты останутся видимыми до checkout.</p>',
        primary_cta_label: 'Смотреть бестселлеры',
        primary_cta_url: '/category/kpb',
        secondary_cta_label: 'Собрать кровать',
        secondary_cta_url: '/catalog?query=комплект',
        style_variant: 'accent',
        layout_variant: 'contained',
        items: [
          { migration_key: 'initial:page:home:conversion-cta:fabric', title: 'Выбор по ткани', sort: 1 },
          { migration_key: 'initial:page:home:conversion-cta:bundle', title: 'Комплекты и пледы', sort: 2 },
          { migration_key: 'initial:page:home:conversion-cta:service', title: 'Понятная доставка', sort: 3 },
        ],
      },
      {
        migration_key: 'initial:page:home:categories',
        internal_name: 'Главная — browse support',
        section_type: 'category_reference_list',
        sort: 7,
        eyebrow: 'Каталог',
        title: 'Быстрый вход в популярные разделы',
        body: '<p>Этот блок поддерживает покупателей, которые предпочитают browse-сценарий вместо выбора через товарные карточки.</p>',
        layout_variant: 'cards',
        items: [
          {
            migration_key: 'initial:page:home:categories:kazanova-kids',
            label: 'Детская коллекция',
            title: '',
            description: 'Подборка текстиля для детской комнаты.',
            reference_kind: 'category_slug',
            reference_key: 'kazanova-kids',
            sort: 1,
          },
          {
            migration_key: 'initial:page:home:categories:carpets',
            label: 'Для дома',
            title: '',
            description: 'Ковры и мягкие акценты для интерьера.',
            reference_kind: 'category_slug',
            reference_key: 'carpets',
            sort: 2,
          },
          {
            migration_key: 'initial:page:home:categories:blankets',
            label: 'Спальня',
            title: '',
            description: 'Одеяла для комфортного сна.',
            reference_kind: 'category_slug',
            reference_key: 'Blankets',
            sort: 3,
          },
        ],
      },
    ],
  },
  {
    path: '/about',
    slug: 'about',
    title: 'О компании',
    nav_label: 'О компании',
    template: 'content',
    summary: 'История бренда, ценности, собственное производство и ключевые преимущества.',
    seo_title: 'О компании',
    seo_description:
      'Постельное Белье-ЮГ — магазины домашнего текстиля с собственным производством и спокойным сервисом.',
    sections: [
      {
        migration_key: 'initial:page:about:hero',
        internal_name: 'О компании — герой',
        section_type: 'hero',
        sort: 1,
        eyebrow: 'О компании',
        title: 'Дом начинается с мягких тканей и спокойного сна',
        body: paragraphs(
          'Постельное Белье-ЮГ — сеть магазинов уютных товаров для дома, представленная на рынке с 2015 года.',
          'Наша миссия — сделать каждое мгновение, проведённое дома, счастливым и уютным.'
        ),
        style_variant: 'warm',
        layout_variant: 'contained',
      },
      {
        migration_key: 'initial:page:about:stats',
        internal_name: 'О компании — ключевые цифры',
        section_type: 'feature_list',
        sort: 2,
        title: 'Постельное Белье-ЮГ в цифрах',
        body: '<p>Короткие ориентиры, которые помогают быстро понять масштаб бренда.</p>',
        layout_variant: 'cards',
        items: [
          { migration_key: 'initial:page:about:stats:stores', title: '>100', description: 'магазинов', sort: 1 },
          { migration_key: 'initial:page:about:stats:cities', title: '38', description: 'городов присутствия', sort: 2 },
          { migration_key: 'initial:page:about:stats:customers', title: '2 млн', description: 'лояльных покупателей', sort: 3 },
          { migration_key: 'initial:page:about:stats:years', title: '9 лет', description: 'на рынке', sort: 4 },
        ],
      },
      {
        migration_key: 'initial:page:about:production',
        internal_name: 'О компании — производство',
        section_type: 'rich_text',
        sort: 3,
        title: 'Собственное производство и регулярное обновление ассортимента',
        body: paragraphs(
          'Бренд является частью крупного российского текстильного холдинга с современным высокотехнологичным производством.',
          'Новые коллекции разрабатываются и поступают в продажу ежесезонно — 4 раза в год.'
        ),
      },
      {
        migration_key: 'initial:page:about:design',
        internal_name: 'О компании — дизайнерские решения',
        section_type: 'rich_text',
        sort: 4,
        title: 'Эксклюзивные авторские разработки и готовые интерьерные решения',
        body: paragraphs(
          'Ассортимент в магазинах и на сайте представлен по капсульному принципу: товары из разных категорий объединены по стилю, дизайну и палитре.',
          'Дизайны постельного белья уникальны, так как специально создаются художниками бренда.'
        ),
      },
      {
        migration_key: 'initial:page:about:delivery',
        internal_name: 'О компании — доставка',
        section_type: 'rich_text',
        sort: 5,
        title: 'Доставка по России',
        body: '<p>Менеджер согласует удобный способ получения и финальную стоимость доставки после оформления.</p>',
      },
      {
        migration_key: 'initial:page:about:shopping',
        internal_name: 'О компании — как покупать',
        section_type: 'rich_text',
        sort: 6,
        title: 'Интернет-магазин и мобильное приложение Постельное Белье-ЮГ',
        body: htmlOrderedList([
          'Оформите заказ не выходя из дома через сайт.',
          'Оплачивайте товары онлайн через защищённую форму YooKassa.',
          'Воспользуйтесь банковской картой или системой быстрых платежей (СБП).',
          'Менеджер согласует удобный способ получения и финальную стоимость доставки после оформления.',
        ]),
        primary_cta_label: 'Перейти к покупкам',
        primary_cta_url: '/category/popular',
      },
    ],
  },
  {
    path: '/info/delivery',
    slug: 'delivery',
    title: 'Доставка и самовывоз',
    nav_label: 'Доставка',
    template: 'content',
    summary: 'Условия доставки по России, пункты выдачи, сроки и правила возврата.',
    seo_title: 'Доставка и самовывоз',
    seo_description:
      'Условия доставки по России, пункты выдачи, сроки и правила возврата.',
    sections: [
      {
        migration_key: 'initial:page:delivery:hero',
        internal_name: 'Доставка — интро',
        section_type: 'hero',
        sort: 1,
        eyebrow: 'Сервис',
        title: 'Доставка по России с понятными условиями',
        body: paragraphs(
          'Доставляем заказы по России курьером и в пункты выдачи.',
          'Условия рассчитываются автоматически при оформлении.'
        ),
        style_variant: 'quiet',
        layout_variant: 'contained',
      },
      {
        migration_key: 'initial:page:delivery:cards',
        internal_name: 'Доставка — преимущества',
        section_type: 'feature_list',
        sort: 2,
        title: 'Основные условия доставки',
        layout_variant: 'cards',
        items: [
          {
            migration_key: 'initial:page:delivery:free',
            title: 'Стоимость до оплаты',
            description:
              'Финальная стоимость доставки рассчитывается при оформлении заказа и показывается перед оплатой.',
            sort: 1,
          },
          {
            migration_key: 'initial:page:delivery:pickup',
            title: 'Пункты выдачи',
            description:
              'Выбирайте удобный ПВЗ на карте. Хранение — 5–7 дней, продление возможно по запросу. Осмотрите заказ перед выкупом, обмен и возврат — по закону о защите прав потребителей.',
            sort: 2,
          },
        ],
      },
      {
        migration_key: 'initial:page:delivery:terms',
        internal_name: 'Доставка — сроки и условия',
        section_type: 'rich_text',
        sort: 3,
        title: 'Сроки и условия',
        body: htmlList([
          'Срок доставки зависит от региона и выбранного оператора, обычно 2–7 дней.',
          'Курьер свяжется заранее, возможен перенос даты и времени.',
          'Если товар пришёл с дефектом, зафиксируйте акт с курьером и напишите нам — заменим или вернём деньги.',
          'Статус заказа всегда виден в личном кабинете и в письмах на e-mail.',
        ]),
      },
    ],
  },
  {
    path: '/info/payment',
    slug: 'payment',
    title: 'Способы оплаты',
    nav_label: 'Оплата',
    template: 'content',
    summary: 'Безопасная полная предоплата картой и через СБП, онлайн-чеки 54-ФЗ и возвраты тем же способом.',
    seo_title: 'Способы оплаты',
    seo_description:
      'Безопасная полная предоплата картой и через СБП, онлайн-чеки 54-ФЗ и возвраты тем же способом.',
    sections: [
      {
        migration_key: 'initial:page:payment:hero',
        internal_name: 'Оплата — интро',
        section_type: 'hero',
        sort: 1,
        eyebrow: 'Сервис',
        title: 'Удобная оплата',
        body: paragraphs(
          'Заказ оплачивается полной предоплатой товаров через YooKassa. Доставку менеджер согласует отдельно после оформления.'
        ),
        style_variant: 'quiet',
        layout_variant: 'contained',
      },
      {
        migration_key: 'initial:page:payment:cards',
        internal_name: 'Оплата — способы',
        section_type: 'feature_list',
        sort: 2,
        title: 'Основные способы оплаты',
        layout_variant: 'cards',
        items: [
          {
            migration_key: 'initial:page:payment:card',
            title: 'Банковская карта',
            description:
              'Принимаем карты Mir, Visa и MasterCard. Платёж проходит через защищённый шлюз, данные карты не сохраняются на нашей стороне.',
            sort: 1,
          },
          {
            migration_key: 'initial:page:payment:sbp',
            title: 'СБП',
            description:
              'Оплачивайте через Систему быстрых платежей в защищённой форме YooKassa. На сайте доступны только карта и СБП.',
            sort: 2,
          },
        ],
      },
      {
        migration_key: 'initial:page:payment:receipt',
        internal_name: 'Оплата — чеки и возвраты',
        section_type: 'rich_text',
        sort: 3,
        title: 'Онлайн-чеки и возвраты',
        body:
          paragraphs(
            'Онлайн-чеки 54-ФЗ формирует YooKassa и отправляет на e-mail покупателя.',
            'При отмене или согласованном возврате деньги возвращаются тем же способом оплаты.'
          ) +
          htmlList([
            'К оплате на сайте принимаются карта и СБП.',
            'При отмене заказа возврат оформляется тем же способом оплаты.',
            'Вопросы по оплате: postel-yug@yandex.ru или +7 961 466-88-33.',
          ]),
      },
    ],
  },
  {
    path: '/info/production',
    slug: 'production',
    title: 'Собственное производство',
    nav_label: 'Производство',
    template: 'content',
    summary: 'Материалы, пошив, контроль качества и упаковка домашнего текстиля собственного производства.',
    seo_title: 'Собственное производство',
    seo_description:
      'Материалы, пошив, контроль качества и упаковка домашнего текстиля собственного производства.',
    sections: [
      {
        migration_key: 'initial:page:production:hero',
        internal_name: 'Производство — интро',
        section_type: 'hero',
        sort: 1,
        eyebrow: 'Производство',
        title: 'Собственное производство',
        body: paragraphs(
          'Мы сами отбираем ткани, контролируем пошив и упаковку.',
          'Так держим качество и сроки под контролем.'
        ),
        style_variant: 'quiet',
        layout_variant: 'contained',
      },
      {
        migration_key: 'initial:page:production:cards',
        internal_name: 'Производство — материалы и пошив',
        section_type: 'feature_list',
        sort: 2,
        title: 'Как мы работаем с качеством',
        layout_variant: 'cards',
        items: [
          {
            migration_key: 'initial:page:production:materials',
            title: 'Материалы',
            description:
              'Используем хлопок, сатин, поплин и микрофибру надёжных поставщиков. Каждый рулон проходит входной контроль и тесты на стойкость окраса.',
            sort: 1,
          },
          {
            migration_key: 'initial:page:production:sewing',
            title: 'Пошив',
            description:
              'Работаем по стандартам лёгкой промышленности: аккуратные швы, усиленные углы, скрытые молнии. Каждая партия проходит выборочный осмотр.',
            sort: 2,
          },
        ],
      },
      {
        migration_key: 'initial:page:production:eco',
        internal_name: 'Производство — экологичность',
        section_type: 'rich_text',
        sort: 3,
        title: 'Экологичность и упаковка',
        body: htmlList([
          'Используем тканевые и картонные упаковки, пригодные для вторичной переработки.',
          'Оптимизируем крои, чтобы снижать отходы.',
          'Работаем с локальными партнёрами, сокращая логистический след.',
        ]),
      },
    ],
  },
  {
    path: '/info/legal',
    slug: 'legal-info',
    title: 'Юридическая информация',
    nav_label: 'Документы',
    template: 'legal_hub',
    summary:
      'Реквизиты продавца, оферта, политика персональных данных, cookies и другие обязательные документы.',
    seo_title: 'Юридическая информация',
    seo_description:
      'Реквизиты продавца, оферта, политика персональных данных, cookies и другие обязательные документы.',
    sections: [
      {
        migration_key: 'initial:page:legal:hero',
        internal_name: 'Юридический раздел — интро',
        section_type: 'hero',
        sort: 1,
        eyebrow: 'Юридический раздел',
        title: 'Документы и регламенты',
        body: paragraphs(
          'Здесь собраны документы, регулирующие использование сайта, продажи, обработку персональных данных и рекламные коммуникации.'
        ),
        style_variant: 'legal',
        layout_variant: 'contained',
      },
      {
        migration_key: 'initial:page:legal:list',
        internal_name: 'Юридический раздел — список документов',
        section_type: 'legal_documents_list',
        sort: 2,
        title: 'Документы',
        body:
          '<p>Ниже находится стартовый набор документов, импортированных из текущей витрины. Реквизиты продавца хранятся в site_settings и должны выводиться рядом с этим списком на фронтенде.</p>',
        style_variant: 'legal',
        layout_variant: 'cards',
      },
    ],
  },
  {
    path: '/info/faq',
    slug: 'faq',
    title: 'Частые вопросы',
    nav_label: 'FAQ',
    template: 'faq',
    summary: 'Короткие ответы на частые вопросы о доставке, оплате, акциях и возвратах.',
    seo_title: 'Частые вопросы',
    seo_description: 'Короткие ответы на частые вопросы о доставке, оплате, акциях и возвратах.',
    sections: [
      {
        migration_key: 'initial:page:faq:hero',
        internal_name: 'FAQ — интро',
        section_type: 'hero',
        sort: 1,
        eyebrow: 'Поддержка',
        title: 'Частые вопросы',
        body:
          '<p>Подборка стартовых ответов для редакторов и службы поддержки. Источником послужили уже опубликованные тексты на сервисных и юридических страницах.</p>',
        style_variant: 'quiet',
        layout_variant: 'contained',
      },
      {
        migration_key: 'initial:page:faq:list',
        internal_name: 'FAQ — список',
        section_type: 'faq_list',
        sort: 2,
        title: 'Ответы на основные вопросы',
        body: '<p>Фронтенд должен выводить здесь опубликованные FAQ-записи по выбранным категориям или флагу featured.</p>',
        layout_variant: 'cards',
      },
    ],
  },
];

const faq = [
  {
    migration_key: 'initial:faq:delivery-cost',
    question: 'Когда видна стоимость доставки?',
    answer:
      '<p>Финальная стоимость доставки рассчитывается при оформлении заказа и показывается перед оплатой.</p>',
    category: 'delivery',
    is_featured: true,
    sort: 1,
  },
  {
    migration_key: 'initial:faq:delivery-time',
    question: 'Сколько обычно занимает доставка?',
    answer:
      '<p>Срок доставки зависит от региона и выбранного оператора. Обычно это 2–7 дней. Курьер связывается заранее, а статус заказа виден в личном кабинете и письмах на e-mail.</p>',
    category: 'delivery',
    is_featured: true,
    sort: 2,
  },
  {
    migration_key: 'initial:faq:payment-prepayment',
    question: 'Какие способы оплаты доступны?',
    answer:
      '<p>На сайте доступна полная предоплата товаров банковской картой или через СБП в защищённой форме YooKassa. Доставку менеджер согласует отдельно после оформления.</p>',
    category: 'payment',
    is_featured: true,
    sort: 3,
  },
  {
    migration_key: 'initial:faq:promocodes',
    question: 'Как работают акции и промокоды?',
    answer:
      '<p>Скидки по сумме корзины рассчитываются автоматически, а промокод можно ввести в корзине. Если одновременно доступно несколько системных скидок, заказ получит наиболее выгодную.</p>',
    category: 'promotions',
    is_featured: true,
    sort: 4,
  },
  {
    migration_key: 'initial:faq:defect',
    question: 'Что делать, если товар пришёл с дефектом?',
    answer:
      '<p>Зафиксируйте акт с курьером или в пункте выдачи и свяжитесь с поддержкой. Мы заменим товар или оформим возврат денег.</p>',
    category: 'returns',
    is_featured: false,
    sort: 5,
  },
];

const legalDocuments = [
  {
    document_key: 'privacy-policy',
    file_name: 'privacy.html',
    title: 'Политика обработки персональных данных',
    slug: 'konfidentsialnost-i-zashchita-informatsii',
    path: '/konfidentsialnost-i-zashchita-informatsii',
    summary: 'Правила обработки и защиты персональных данных пользователей и покупателей.',
    sort: 1,
  },
  {
    document_key: 'user-agreement',
    file_name: 'user-agreement.html',
    title: 'Пользовательское соглашение',
    slug: 'polzovatelskoe-soglashenie',
    path: '/polzovatelskoe-soglashenie',
    summary: 'Условия использования сайта и ответственность сторон.',
    sort: 2,
  },
  {
    document_key: 'personal-data-consent',
    file_name: 'pd-consent.html',
    title: 'Согласие на обработку персональных данных',
    slug: 'soglasie-na-obrabotku-pd',
    path: '/soglasie-na-obrabotku-pd',
    summary: 'Форма согласия на обработку данных в рамках работы сайта.',
    sort: 3,
  },
  {
    document_key: 'ads-consent',
    file_name: 'ads-consent.html',
    title: 'Согласие на получение рекламы',
    slug: 'soglasie-na-poluchenie-reklamy',
    path: '/soglasie-na-poluchenie-reklamy',
    summary: 'Порядок подписки и отказа от рекламных сообщений.',
    sort: 4,
  },
  {
    document_key: 'cookies-policy',
    file_name: 'cookies.html',
    title: 'Политика в отношении cookie',
    slug: 'cookies',
    path: '/cookies',
    summary: 'Информация об использовании cookie и иных технологий аналитики.',
    sort: 5,
  },
  {
    document_key: 'sales-terms',
    file_name: 'sales-terms.html',
    title: 'Условия продажи (публичная оферта)',
    slug: 'usloviya-prodazhi',
    path: '/usloviya-prodazhi',
    summary: 'Правила оформления заказов, оплаты, доставки и возврата товара.',
    sort: 6,
  },
];

module.exports = {
  seedPrefix: 'initial:',
  siteSettings,
  navigation,
  pages,
  faq,
  legalDocuments,
};
