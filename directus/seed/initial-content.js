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
      { migration_key: 'initial:footer_service:bonuses', label: 'Бонусы и лояльность', url: '/info/bonuses', sort: 4 },
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
        title: 'Бесплатная доставка',
        body: '<p>Мы осуществляем бесплатную доставку по всей России при заказе от 5 000 ₽.</p>',
      },
      {
        migration_key: 'initial:page:about:shopping',
        internal_name: 'О компании — как покупать',
        section_type: 'rich_text',
        sort: 6,
        title: 'Интернет-магазин и мобильное приложение Постельное Белье-ЮГ',
        body: htmlOrderedList([
          'Оформите заказ не выходя из дома через сайт.',
          'Платите удобным для вас способом — онлайн или при получении.',
          'Воспользуйтесь системой быстрых платежей (СБП) или оплатите заказ частями без процентов и переплат.',
          'Выберите удобный способ получения: пункты выдачи, курьерская доставка, доставка из магазина день в день или самовывоз.',
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
    summary: 'Условия доставки по России, пункты выдачи, сроки, бесплатная доставка и правила возврата.',
    seo_title: 'Доставка и самовывоз',
    seo_description:
      'Условия доставки по России, пункты выдачи, сроки, бесплатная доставка и правила возврата.',
    sections: [
      {
        migration_key: 'initial:page:delivery:hero',
        internal_name: 'Доставка — интро',
        section_type: 'hero',
        sort: 1,
        eyebrow: 'Сервис',
        title: 'Бесплатная доставка при оформлении заказа от 5000 ₽',
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
            title: 'Бесплатно от 5000 ₽',
            description:
              'Если сумма корзины от 5000 ₽, доставка в большинство городов бесплатна. Для удалённых регионов действует фиксированная доплата, мы покажем её перед оплатой.',
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
    summary: 'Безопасная оплата картой, СБП и при получении, а также правила чеков и возвратов.',
    seo_title: 'Способы оплаты',
    seo_description:
      'Безопасная оплата картой, СБП и при получении, а также правила чеков и возвратов.',
    sections: [
      {
        migration_key: 'initial:page:payment:hero',
        internal_name: 'Оплата — интро',
        section_type: 'hero',
        sort: 1,
        eyebrow: 'Сервис',
        title: 'Удобная оплата',
        body: paragraphs(
          'Мы поддерживаем безопасные способы оплаты и сразу показываем итоговую стоимость заказа.'
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
            title: 'СБП и рассрочка',
            description:
              'Оплачивайте через СБП без комиссии или выбирайте оплату частями у партнёров-банков. Условия рассрочки показываем перед подтверждением.',
            sort: 2,
          },
        ],
      },
      {
        migration_key: 'initial:page:payment:receipt',
        internal_name: 'Оплата — при получении',
        section_type: 'rich_text',
        sort: 3,
        title: 'Оплата при получении',
        body:
          paragraphs(
            'В пунктах выдачи и у курьеров доступна оплата картой или наличными, если поддерживается оператором доставки.',
            'На некоторых локациях доступна только предоплата — это будет указано при оформлении.'
          ) +
          htmlList([
            'Чеки отправляем на e-mail и в личный кабинет.',
            'При отмене заказа возврат оформляется тем же способом оплаты.',
            'Вопросы по оплате: postel-yug@yandex.ru или +7 961 466-88-33.',
          ]),
      },
    ],
  },
  {
    path: '/info/bonuses',
    slug: 'bonuses',
    title: 'Бонусы и лояльность',
    nav_label: 'Бонусы',
    template: 'content',
    summary: 'Как начисляются бонусы за покупки, сколько они действуют и как использовать их при следующем заказе.',
    seo_title: 'Бонусы и лояльность',
    seo_description:
      'Как начисляются бонусы за покупки, сколько они действуют и как использовать их при следующем заказе.',
    sections: [
      {
        migration_key: 'initial:page:bonuses:hero',
        internal_name: 'Бонусы — интро',
        section_type: 'hero',
        sort: 1,
        eyebrow: 'Лояльность',
        title: 'Бонусы за покупки',
        body: paragraphs(
          'Лояльность простая: авторизуйтесь, копите баллы и оплачивайте ими до 20% стоимости следующего заказа.'
        ),
        style_variant: 'quiet',
        layout_variant: 'contained',
      },
      {
        migration_key: 'initial:page:bonuses:rules',
        internal_name: 'Бонусы — правила',
        section_type: 'rich_text',
        sort: 2,
        title: 'Как это работает',
        body: htmlList([
          '1 бонус = 1 рубль. Начисляем после подтверждения доставки.',
          'Бонусы действуют 12 месяцев с момента начисления.',
          'Не суммируются с промокодами, если в условиях акции указано иначе.',
          'Баланс и история начислений доступны в личном кабинете.',
        ]),
      },
      {
        migration_key: 'initial:page:bonuses:extras',
        internal_name: 'Бонусы — дополнительные сценарии',
        section_type: 'feature_list',
        sort: 3,
        title: 'Дополнительные сценарии',
        layout_variant: 'cards',
        items: [
          {
            migration_key: 'initial:page:bonuses:extra',
            title: 'Дополнительные бонусы',
            description:
              'Дарим за отзывы с фото, участие в опросах и рекомендации друзьям. Размер поощрения зависит от активности.',
            sort: 1,
          },
          {
            migration_key: 'initial:page:bonuses:return',
            title: 'Возвраты',
            description:
              'При возврате товара списанные бонусы возвращаются на счёт после обработки заявки.',
            sort: 2,
          },
        ],
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
    summary: 'Короткие ответы на частые вопросы о доставке, оплате, бонусах и возвратах.',
    seo_title: 'Частые вопросы',
    seo_description: 'Короткие ответы на частые вопросы о доставке, оплате, бонусах и возвратах.',
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
    migration_key: 'initial:faq:free-delivery',
    question: 'Когда доставка бесплатная?',
    answer:
      '<p>Если сумма корзины от 5000 ₽, доставка в большинство городов бесплатна. Для удалённых регионов действует фиксированная доплата, которая показывается перед оплатой.</p>',
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
    migration_key: 'initial:faq:payment-on-delivery',
    question: 'Можно ли оплатить заказ при получении?',
    answer:
      '<p>В пунктах выдачи и у курьеров доступна оплата картой или наличными, если это поддерживает оператор доставки. На некоторых локациях доступна только предоплата — это указывается при оформлении.</p>',
    category: 'payment',
    is_featured: true,
    sort: 3,
  },
  {
    migration_key: 'initial:faq:bonuses',
    question: 'Как работают бонусы?',
    answer:
      '<p>1 бонус = 1 рубль. Бонусы начисляются после подтверждения доставки, действуют 12 месяцев и могут покрыть до 20% следующего заказа.</p>',
    category: 'bonuses',
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
