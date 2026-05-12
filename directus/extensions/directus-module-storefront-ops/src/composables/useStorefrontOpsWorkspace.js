import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useApi } from '@directus/extensions-sdk';
import { createStorefrontOpsApi } from './storefrontOpsApi.js';
import {
  PRODUCT_DETAIL_TABS,
  STOREFRONT_OPS_TABS,
  createStorefrontOpsLoadingState,
  createStorefrontOpsNavigationCounts,
  isStorefrontOpsMasterDetailTab,
} from '../storefront-ops-tabs.js';
import {
  cloneSpecifications,
  compactParams,
  filterCollection,
  formatCategoryLabel,
  formatDateTime,
  formatMinorMoney,
  formatMoney,
  formatPercent,
  majorToMinor,
  minorToMajor,
  moneyMinorAmount,
  nextIdempotencyKey,
  normalizeNullableNumber,
  normalizeNullableText,
  normalizeSpecificationsForPayload,
  overlayStatusLabel,
  toDatetimeLocal,
  toIsoDateTime,
} from '../storefront-ops-formatters.js';
import {
  canAccessStorefrontOpsTab,
  normalizeRoleToken,
  resolveStorefrontOpsRoleKind,
} from '../../../storefront-ops-access-policy.js';
import { buildStorefrontPreviewUrl } from '../../../storefront-ops-preview.js';

export function useStorefrontOpsWorkspace(tabComponents) {
  const api = useApi();
  const { bridgeRequest, directusRequest } = createStorefrontOpsApi(api);

  const tabs = STOREFRONT_OPS_TABS;
  const productDetailTabs = PRODUCT_DETAIL_TABS;


  const ORDER_STATUS_OPTIONS = [
    'PENDING',
    'PAID',
    'PROCESSING',
    'READY_FOR_PICKUP',
    'SHIPPED',
    'DELIVERED',
    'RECEIVED',
    'CANCELLED',
    'REFUNDED',
  ];
  const PICKER_TARGET_STATUS_OPTIONS = ['PROCESSING', 'READY_FOR_PICKUP', 'SHIPPED'];

  const STORAGE_KEY = 'storefront-ops.workspace-state';
  const HOME_PAGE_SLUG = 'home';
  const LOCAL_CHANGE_NOTICE = 'Изменено. Нажмите «Сохранить», чтобы применить.';
  const HOME_STATUS_OPTIONS = [
    { value: 'draft', label: 'Черновик' },
    { value: 'in_review', label: 'На проверке' },
    { value: 'published', label: 'Опубликовано' },
    { value: 'archived', label: 'В архиве' },
  ];
  const HOME_STYLE_OPTIONS = [
    { value: 'default', label: 'Обычный' },
    { value: 'warm', label: 'Тёплый' },
    { value: 'sage', label: 'Шалфейный' },
    { value: 'quiet', label: 'Спокойный' },
    { value: 'legal', label: 'Юридический' },
    { value: 'accent', label: 'Акцентный' },
  ];
  const HOME_LAYOUT_OPTIONS = [
    { value: 'contained', label: 'Обычный блок' },
    { value: 'full', label: 'На всю ширину' },
    { value: 'media_right', label: 'Медиа справа' },
    { value: 'media_left', label: 'Медиа слева' },
    { value: 'cards', label: 'Карточки' },
    { value: 'rail', label: 'Горизонтальная лента' },
    { value: 'shop_the_look', label: 'Shop the look' },
  ];
  const HOME_SECTION_PRESETS = [
    { value: 'hero', label: 'Первый экран' },
    { value: 'category_reference_list', label: 'Категории' },
    { value: 'product_reference_list', label: 'Товары' },
    { value: 'collection_teaser', label: 'Подборка' },
    { value: 'feature_list', label: 'Преимущества' },
    { value: 'banner_group', label: 'Баннер' },
    { value: 'newsletter_cta', label: 'Подписка' },
    { value: 'rich_text', label: 'Текст' },
  ];
  const STOREFRONT_COLLECTION_MODES = [
    { value: 'manual', label: 'Ручная' },
    { value: 'backend_rule', label: 'Правило backend' },
    { value: 'hybrid', label: 'Гибрид' },
  ];
  const STOREFRONT_COLLECTION_RULE_TYPES = [
    { value: '', label: 'Без правила' },
    { value: 'new', label: 'Новинки' },
    { value: 'bestsellers', label: 'Бестселлеры' },
    { value: 'category', label: 'Категория' },
    { value: 'brand', label: 'Бренд' },
    { value: 'sale', label: 'Распродажа' },
  ];
  const STOREFRONT_COLLECTION_SORT_MODES = [
    { value: 'default', label: 'По умолчанию' },
    { value: 'newest', label: 'Сначала новые' },
    { value: 'oldest', label: 'Сначала старые' },
    { value: 'alphabetical', label: 'По алфавиту' },
    { value: 'price_asc', label: 'Цена ↑' },
    { value: 'price_desc', label: 'Цена ↓' },
  ];
  const HOME_SECTION_TYPE_LABELS = {
    hero: 'Первый экран',
    category_reference_list: 'Категории',
    collection_teaser: 'Подборка',
    product_reference_list: 'Товары',
    feature_list: 'Список преимуществ',
    banner_group: 'Баннеры',
    newsletter_cta: 'Подписка',
    rich_text: 'Текст',
  };

  const activeTab = ref('home');
  const pageError = ref('');
  const pageNotice = reactive({ type: '', text: '' });
  const isSubmitting = ref(false);
  const isRefreshing = ref(false);
  const bootstrapped = ref(false);
  const navigationTarget = ref('');
  const accessState = reactive({
    loaded: false,
    userId: '',
    email: '',
    externalId: '',
    roleKind: '',
    roleId: '',
    roleName: '',
    roleAdminAccess: false,
    previewBaseUrl: '',
  });
  const navigationCounts = reactive(createStorefrontOpsNavigationCounts());

  const loading = reactive(createStorefrontOpsLoadingState());

  const homeState = reactive({
    loaded: false,
    page: null,
    sections: [],
    selectedSectionIndex: 0,
    presetToAdd: 'product_reference_list',
    categoryOptions: [],
    productOptions: [],
    collectionOptions: [],
    collectionItemsById: {},
    categoryQuery: '',
    categoryToAdd: '',
    collectionToAdd: '',
    collectionDraft: null,
    collectionProductQuery: '',
    collectionProductToAdd: '',
    collectionCategoryQuery: '',
    collectionCategoryToAdd: '',
    originalItemIds: [],
    originalCollectionRuleIds: [],
  });

  const homeForm = reactive({
    page: {
      id: '',
      status: 'draft',
      title: '',
      summary: '',
      seoTitle: '',
      seoDescription: '',
    },
    announcementBanner: {
      id: '',
      status: 'draft',
      internalName: 'Баннер в шапке',
      shortText: '',
    },
    sections: [],
  });

  const productState = reactive({
    loaded: false,
    query: '',
    items: [],
    brandOptions: [],
    categoryOptions: [],
    overlayReadFailed: false,
    selectedId: '',
    detail: null,
    isCreating: false,
    panel: 'main',
  });

  const categoryState = reactive({
    loaded: false,
    query: '',
    items: [],
    parentOptions: [],
    overlayReadFailed: false,
    selectedId: '',
    detail: null,
    isCreating: false,
  });

  const brandState = reactive({
    loaded: false,
    query: '',
    items: [],
    selectedId: '',
    detail: null,
    isCreating: false,
  });

  const inventoryState = reactive({
    loaded: false,
    query: '',
    items: [],
    selectedVariantId: '',
  });

  const productForm = reactive({
    id: '',
    name: '',
    slug: '',
    description: '',
    brandId: '',
    categoryIds: [],
    isActive: true,
    specifications: [],
  });

  const productSnapshot = ref('');
  const productCategoryFilter = ref('');

  const variantForm = reactive({
    id: '',
    sku: '',
    name: '',
    amount: 0,
    currency: 'RUB',
    stock: 0,
    weightGrossG: null,
    lengthMm: null,
    widthMm: null,
    heightMm: null,
  });

  const variantSnapshot = ref('');
  const productMediaForm = reactive({ variantId: '' });
  const productMediaFiles = ref([]);

  const categoryForm = reactive({
    id: '',
    name: '',
    slug: '',
    description: '',
    parentId: '',
    position: 0,
    isActive: true,
  });

  const categorySnapshot = ref('');
  const categoryImageFile = ref(null);

  const brandForm = reactive({
    id: '',
    name: '',
    slug: '',
    description: '',
  });

  const brandSnapshot = ref('');

  const inventoryForm = reactive({
    variantId: '',
    delta: 0,
    reason: '',
    idempotencyKey: nextIdempotencyKey(),
  });

  const orderState = reactive({
    loaded: false,
    query: '',
    status: '',
    manager: '',
    archived: 'all',
    from: '',
    to: '',
    items: [],
    selectedId: '',
    selectedFilteredOut: false,
    detail: null,
    nextStatus: '',
    note: '',
    rmaReason: '',
    rmaDesiredResolution: '',
    rmaDecisionForms: {},
    refundForms: {},
  });

  const importState = reactive({
    loaded: false,
    jobs: [],
    selectedJobId: '',
    file: null,
    dryRun: null,
    mapping: {
      sku: 'sku',
      productName: 'product_name',
      productSlug: 'product_slug',
      variantName: 'variant_name',
      brandSlug: 'brand_slug',
      categorySlug: 'category_slug',
      priceAmount: 'price',
      stockQuantity: 'stock',
      currency: 'currency',
    },
  });

  const importMappingFields = [
    { key: 'sku', label: 'SKU' },
    { key: 'productName', label: 'Товар' },
    { key: 'productSlug', label: 'Слаг товара' },
    { key: 'variantName', label: 'Вариант' },
    { key: 'brandSlug', label: 'Бренд' },
    { key: 'categorySlug', label: 'Категория' },
    { key: 'priceAmount', label: 'Цена' },
    { key: 'stockQuantity', label: 'Остаток' },
    { key: 'currency', label: 'Валюта' },
  ];

  const promotionState = reactive({
    loaded: false,
    items: [],
    promoCodes: [],
    selectedId: '',
    selectedPromoCodeId: '',
    mode: 'promotion',
    isCreating: false,
    promoCodeCreating: false,
  });

  const activePromotionState = reactive({
    loaded: false,
    items: [],
  });

  const promotionForm = reactive({
    id: '',
    name: '',
    type: 'PRODUCT_SALE',
    status: 'ACTIVE',
    startsAt: '',
    endsAt: '',
    discountPercent: null,
    discountAmount: null,
    salePriceAmount: null,
    currency: 'RUB',
    thresholdAmount: null,
    description: '',
    targets: [],
  });

  const promoCodeForm = reactive({
    id: '',
    code: '',
    status: 'ACTIVE',
    discountPercent: null,
    discountAmount: null,
    thresholdAmount: null,
    startsAt: '',
    endsAt: '',
    maxRedemptions: null,
    description: '',
  });

  const taxState = reactive({
    loaded: false,
    items: [],
    selectedId: '',
    isCreating: false,
  });

  const taxForm = reactive({
    id: '',
    name: '',
    status: 'ACTIVE',
    taxSystemCode: 1,
    vatCode: 1,
    vatRatePercent: 0,
    active: false,
  });

  const analyticsState = reactive({
    loaded: false,
    from: '',
    to: '',
    manager: '',
    managerRows: [],
    paymentLinks: { sent: 0, paid: 0, conversionRate: 0, rows: [] },
  });

  const alertState = reactive({
    loaded: false,
    threshold: 5,
    rows: [],
  });

  const roleKind = computed(() => resolveRoleKind(accessState));
  const isManagerRole = computed(() => roleKind.value === 'manager');
  const canViewActivePromotions = computed(() => ['admin', 'manager', 'picker', 'content'].includes(roleKind.value));
  const visibleTabs = computed(() => tabs.filter((tab) => canAccessTab(tab.id)));
  const defaultTab = computed(() => visibleTabs.value[0]?.id || 'products');
  const activeTabLabel = computed(() => visibleTabs.value.find((tab) => tab.id === activeTab.value)?.label || 'Раздел');
  const activeTabHasMasterDetail = computed(() => isStorefrontOpsMasterDetailTab(activeTab.value));
  const activeTabComponent = computed(() => tabComponents[activeTab.value] || tabComponents.home);
  const selectedOrder = computed(() => orderState.detail?.order || null);
  const selectedOrderRmaRequests = computed(() => orderState.detail?.rmaRequests || []);
  const orderStatusOptions = computed(() => (
    roleKind.value === 'picker'
      ? PICKER_TARGET_STATUS_OPTIONS
      : ORDER_STATUS_OPTIONS
  ));
  const canClaimSelectedOrder = computed(() => {
    const order = selectedOrder.value;
    return Boolean(order && !isOrderArchived(order) && ['admin', 'manager'].includes(roleKind.value) && !isOrderAssigned(order));
  });
  const canClearSelectedOrder = computed(() => Boolean(
    selectedOrder.value && !isOrderArchived(selectedOrder.value) && roleKind.value === 'admin' && isOrderAssigned(selectedOrder.value)
  ));
  const canArchiveSelectedOrder = computed(() => Boolean(
    selectedOrder.value && !isOrderArchived(selectedOrder.value) && roleKind.value === 'admin'
  ));
  const canRestoreSelectedOrder = computed(() => Boolean(
    selectedOrder.value && isOrderArchived(selectedOrder.value) && roleKind.value === 'admin'
  ));
  const canSubmitSelectedOrderStatus = computed(() => {
    const order = selectedOrder.value;
    if (!order || isOrderArchived(order) || !orderState.nextStatus) {
      return false;
    }
    if (roleKind.value === 'admin') {
      return true;
    }
    if (roleKind.value === 'manager') {
      return isOrderAssignedToCurrentUser(order);
    }
    if (roleKind.value === 'picker') {
      return PICKER_TARGET_STATUS_OPTIONS.includes(orderState.nextStatus);
    }
    return false;
  });
  const canManageSelectedOrderRma = computed(() => {
    const order = selectedOrder.value;
    if (!order || isOrderArchived(order)) {
      return false;
    }
    if (roleKind.value === 'admin') {
      return true;
    }
    return roleKind.value === 'manager' && (!isOrderAssigned(order) || isOrderAssignedToCurrentUser(order));
  });
  const canDecideSelectedOrderRma = computed(() => {
    const order = selectedOrder.value;
    if (!order || isOrderArchived(order)) {
      return false;
    }
    return roleKind.value === 'admin' || (roleKind.value === 'manager' && isOrderAssignedToCurrentUser(order));
  });
  const canRefundSelectedOrder = computed(() => {
    const summary = selectedOrder.value?.paymentSummary;
    const hasPendingRefund = Array.isArray(summary?.refunds)
      ? summary.refunds.some((refund) => refund?.status === 'PENDING')
      : false;
    return Boolean(
      roleKind.value === 'admin' &&
      !isOrderArchived(selectedOrder.value) &&
      summary &&
      summary.status === 'COMPLETED' &&
      !hasPendingRefund &&
      moneyMinorAmount(summary.refundableAmount) > 0
    );
  });
  const accessRoleLabel = computed(() => {
    if (accessState.roleName) {
      return accessState.roleName;
    }
    if (roleKind.value === 'admin') return 'Администратор';
    if (roleKind.value === 'manager') return 'Менеджер';
    if (roleKind.value === 'picker') return 'Сборщик';
    if (roleKind.value === 'content') return 'Контент-менеджер';
    return 'Роль Directus';
  });
  const managerAnalyticsNotice = computed(() => (
    isManagerRole.value
      ? 'Доступны заказы, активные акции и только собственная аналитика.'
      : 'Доступ разделов ограничен ролью пользователя.'
  ));

  const filteredProducts = computed(() => filterCollection(productState.items, productState.query, [
    (item) => item.name,
    (item) => item.slug,
    (item) => item.brand?.name,
    (item) => item.brand?.slug,
  ]));

  const filteredCategories = computed(() => filterCollection(categoryState.items, categoryState.query, [
    (item) => item.name,
    (item) => item.slug,
    (item) => item.fullPath,
  ]));

  const selectedHomeSection = computed(() => {
    if (!homeForm.sections.length) {
      return null;
    }
    const safeIndex = Math.max(0, Math.min(homeState.selectedSectionIndex, homeForm.sections.length - 1));
    return homeForm.sections[safeIndex] || null;
  });
  const selectedHomeSectionList = computed(() => homeForm.sections);
  const selectedHomeSectionPosition = computed(() => {
    if (!selectedHomeSection.value) {
      return '';
    }
    return `${homeState.selectedSectionIndex + 1} из ${homeForm.sections.length}`;
  });
  const homeCategorySection = computed(() => (
    homeForm.sections.find((section) => section.sectionType === 'category_reference_list') || null
  ));
  const homeSelectedCategoryKeys = computed(() => new Set(
    homeForm.sections
      .filter((section) => section.sectionType === 'category_reference_list')
      .flatMap((section) => section.items || [])
      .filter((item) => normalizeHomeReferenceKind(item.referenceKind) === 'category_slug' && item.referenceKey)
      .map((item) => normalizeHomeKey(item.referenceKey))
  ));
  const filteredHomeCategoryOptions = computed(() => {
    const query = homeState.categoryQuery.trim().toLowerCase();
    return (homeState.categoryOptions || [])
      .filter((category) => !homeSelectedCategoryKeys.value.has(normalizeHomeKey(category.slug)))
      .filter((category) => {
        if (!query) {
          return true;
        }
        return [category.name, category.slug, category.fullPath]
          .filter(Boolean)
          .some((value) => String(value).toLowerCase().includes(query));
      });
  });
  const filteredHomeCollectionOptions = computed(() => {
    const selectedKeys = new Set(
      (selectedHomeSection.value?.items || [])
        .filter((item) => normalizeHomeReferenceKind(item.referenceKind) === 'storefront_collection' && item.referenceKey)
        .map((item) => normalizeHomeKey(item.referenceKey))
    );
    return (homeState.collectionOptions || []).filter((collection) => !selectedKeys.has(normalizeHomeKey(collection.key)));
  });
  const filteredCollectionProductOptions = computed(() => {
    const selectedKeys = new Set(
      (homeState.collectionDraft?.rules || [])
        .filter((item) => item.entityKind === 'product')
        .map((item) => normalizeHomeKey(item.entityKey))
    );
    const query = homeState.collectionProductQuery.trim().toLowerCase();
    return (homeState.productOptions || [])
      .filter((product) => !selectedKeys.has(normalizeHomeKey(product.slug)))
      .filter((product) => {
        if (!query) return true;
        return [product.name, product.slug, product.brand?.name, product.brand?.slug]
          .filter(Boolean)
          .some((value) => String(value).toLowerCase().includes(query));
      });
  });
  const filteredCollectionCategoryOptions = computed(() => {
    const selectedKeys = new Set(
      (homeState.collectionDraft?.rules || [])
        .filter((item) => item.entityKind === 'category')
        .map((item) => normalizeHomeKey(item.entityKey))
    );
    const query = homeState.collectionCategoryQuery.trim().toLowerCase();
    return (homeState.categoryOptions || [])
      .filter((category) => !selectedKeys.has(normalizeHomeKey(category.slug)))
      .filter((category) => {
        if (!query) return true;
        return [category.name, category.slug, category.fullPath]
          .filter(Boolean)
          .some((value) => String(value).toLowerCase().includes(query));
      });
  });

  const filteredBrands = computed(() => filterCollection(brandState.items, brandState.query, [
    (item) => item.name,
    (item) => item.slug,
  ]));

  const filteredInventory = computed(() => filterCollection(inventoryState.items, inventoryState.query, [
    (item) => item.productName,
    (item) => item.productSlug,
    (item) => item.variantName,
    (item) => item.sku,
    (item) => item.brand?.name,
    (item) => item.brand?.slug,
  ]));

  const filteredProductCategoryOptions = computed(() => {
    if (!productCategoryFilter.value) {
      return productCategoryOptions.value;
    }
    const query = productCategoryFilter.value.toLowerCase();
    return productCategoryOptions.value.filter((option) => {
      return [option.name, option.slug, option.fullPath]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(query));
    });
  });

  const productBrandOptions = computed(() => productState.brandOptions || []);
  const productCategoryOptions = computed(() => productState.categoryOptions || []);
  const availableParentOptions = computed(() => {
    const currentId = categoryForm.id;
    return (categoryState.parentOptions || []).filter((option) => option.id !== currentId);
  });

  function setProductCategoryFilter(value) {
    productCategoryFilter.value = String(value || '');
  }

  const selectedInventoryRow = computed(() => {
    return inventoryState.items.find((item) => item.variantId === inventoryState.selectedVariantId) || null;
  });

  const productOverlayInfo = computed(() => productState.detail?.item?.overlay || null);
  const categoryOverlayInfo = computed(() => categoryState.detail?.item?.overlay || null);

  const productDirty = computed(() => productSnapshot.value !== serializeProductForm());
  const variantDirty = computed(() => variantSnapshot.value !== serializeVariantForm());
  const categoryDirty = computed(() => categorySnapshot.value !== serializeCategoryForm());
  const brandDirty = computed(() => brandSnapshot.value !== serializeBrandForm());
  const hasUnsavedChanges = computed(() => {
    if (activeTab.value === 'products') {
      return productDirty.value || variantDirty.value;
    }
    if (activeTab.value === 'categories') {
      return categoryDirty.value;
    }
    if (activeTab.value === 'brands') {
      return brandDirty.value;
    }
    return false;
  });

  const activeDetailOpen = computed(() => {
    if (activeTab.value === 'products') {
      return productState.isCreating || Boolean(productState.detail);
    }
    if (activeTab.value === 'categories') {
      return categoryState.isCreating || Boolean(categoryState.detail);
    }
    if (activeTab.value === 'brands') {
      return brandState.isCreating || Boolean(brandState.detail);
    }
    if (activeTab.value === 'inventory') {
      return Boolean(selectedInventoryRow.value);
    }
    if (activeTab.value === 'orders') {
      return Boolean(orderState.detail);
    }
    if (activeTab.value === 'promotions') {
      return Boolean(
        promotionState.selectedId ||
          promotionState.selectedPromoCodeId ||
          promotionState.isCreating ||
          promotionState.promoCodeCreating
      );
    }
    if (activeTab.value === 'tax') {
      return taxState.isCreating || Boolean(taxState.selectedId);
    }
    return true;
  });


  const storefrontOpsViewProps = computed(() => ({
    HOME_SECTION_PRESETS,
    HOME_STATUS_OPTIONS,
    HOME_STYLE_OPTIONS,
    HOME_LAYOUT_OPTIONS,
    STOREFRONT_COLLECTION_MODES,
    STOREFRONT_COLLECTION_RULE_TYPES,
    STOREFRONT_COLLECTION_SORT_MODES,
    productDetailTabs,
    importMappingFields,
    orderStatusOptions: orderStatusOptions.value,
    accessState,
    loading,
    homeState,
    homeForm,
    productState,
    categoryState,
    brandState,
    inventoryState,
    productForm,
    variantForm,
    productMediaForm,
    categoryForm,
    brandForm,
    inventoryForm,
    orderState,
    importState,
    promotionState,
    activePromotionState,
    promotionForm,
    promoCodeForm,
    taxState,
    taxForm,
    analyticsState,
    alertState,
    roleKind: roleKind.value,
    isManagerRole: isManagerRole.value,
    canViewActivePromotions: canViewActivePromotions.value,
    activeDetailOpen: activeDetailOpen.value,
    activeTabHasMasterDetail: activeTabHasMasterDetail.value,
    activeTabLabel: activeTabLabel.value,
    filteredProducts: filteredProducts.value,
    filteredCategories: filteredCategories.value,
    filteredBrands: filteredBrands.value,
    filteredInventory: filteredInventory.value,
    filteredProductCategoryOptions: filteredProductCategoryOptions.value,
    productBrandOptions: productBrandOptions.value,
    productCategoryOptions: productCategoryOptions.value,
    availableParentOptions: availableParentOptions.value,
    selectedInventoryRow: selectedInventoryRow.value,
    productOverlayInfo: productOverlayInfo.value,
    categoryOverlayInfo: categoryOverlayInfo.value,
    productDirty: productDirty.value,
    variantDirty: variantDirty.value,
    categoryDirty: categoryDirty.value,
    brandDirty: brandDirty.value,
    selectedOrder: selectedOrder.value,
    selectedOrderRmaRequests: selectedOrderRmaRequests.value,
    canClaimSelectedOrder: canClaimSelectedOrder.value,
    canClearSelectedOrder: canClearSelectedOrder.value,
    canArchiveSelectedOrder: canArchiveSelectedOrder.value,
    canRestoreSelectedOrder: canRestoreSelectedOrder.value,
    canSubmitSelectedOrderStatus: canSubmitSelectedOrderStatus.value,
    canManageSelectedOrderRma: canManageSelectedOrderRma.value,
    canDecideSelectedOrderRma: canDecideSelectedOrderRma.value,
    canRefundSelectedOrder: canRefundSelectedOrder.value,
    selectedHomeSection: selectedHomeSection.value,
    selectedHomeSectionList: selectedHomeSectionList.value,
    selectedHomeSectionPosition: selectedHomeSectionPosition.value,
    homeCategorySection: homeCategorySection.value,
    filteredHomeCategoryOptions: filteredHomeCategoryOptions.value,
    filteredHomeCollectionOptions: filteredHomeCollectionOptions.value,
    filteredCollectionProductOptions: filteredCollectionProductOptions.value,
    filteredCollectionCategoryOptions: filteredCollectionCategoryOptions.value,
    productCategoryFilter: productCategoryFilter.value,
    productMediaFiles: productMediaFiles.value,
    categoryImageFile: categoryImageFile.value,
    isSubmitting: isSubmitting.value,
    isTabLoading,
    homeStatusLabel,
    homeSectionLabel,
    homeSectionTypeLabel,
    saveHomeContent,
    openStorefrontPreview,
    addHomeSectionPreset,
    selectHomeSection,
    moveHomeSection,
    startCreateProduct,
    overlayStatusLabel,
    selectProduct,
    startCreateCategory,
    selectCategory,
    startCreateBrand,
    selectBrand,
    formatMoney,
    selectInventoryRow,
    loadOrders,
    loadActivePromotions,
    formatMinorMoney,
    orderManagerLabel,
    isOrderArchived,
    orderStatusLabel,
    formatDateTime,
    selectOrder,
    selectImportJob,
    startCreatePromotion,
    selectPromotion,
    startCreatePromoCode,
    selectPromoCode,
    formatPercent,
    loadAnalytics,
    saveLowStockThreshold,
    closeActiveDetail,
    saveHomeCollectionDraft,
    moveHomeItem,
    removeHomeItem,
    homeProductLabel,
    homeProductMeta,
    homeProductImage,
    searchHomeHeroProducts,
    filteredHomeFeaturedProductOptions,
    setHomeHeroFeaturedProduct,
    clearHomeHeroFeaturedProduct,
    homeCategoryLabel,
    homeCategoryMeta,
    homeCategoryImage,
    searchHomeCategories,
    addHomeCategory,
    filteredHomeProductOptions,
    ensureHomeProductOptions,
    searchHomeProducts,
    addHomeProduct,
    homeCollectionLabel,
    homeCollectionMeta,
    addHomeCollection,
    startCreateHomeCollection,
    editHomeCollection,
    cancelHomeCollectionDraft,
    searchHomeCollectionProducts,
    searchHomeCollectionCategories,
    addHomeCollectionProductRule,
    addHomeCollectionCategoryRule,
    removeHomeCollectionRule,
    moveHomeCollectionRule,
    removeHomeSection,
    archiveHomeSection,
    duplicateHomeSection,
    addHomeItem,
    resetProductEditor,
    submitProduct,
    deleteProduct,
    setProductPanel,
    addSpecificationSection,
    removeSpecificationSection,
    addSpecificationItem,
    removeSpecificationItem,
    loadVariantEditor,
    submitVariant,
    resetVariantEditor,
    deleteVariant,
    onProductFilesSelected,
    uploadProductImages,
    deleteProductImage,
    openOrCreateOverlay,
    formatCategoryLabel,
    adjustCategoryPosition,
    onCategoryFileSelected,
    uploadCategoryImage,
    submitCategory,
    resetCategoryEditor,
    deleteCategory,
    submitBrand,
    resetBrandEditor,
    deleteBrand,
    submitInventoryAdjustment,
    resetInventoryEditor,
    refundSelectedOrder,
    selectedRefundLines,
    createRmaRequest,
    rmaStatusLabel,
    decideRmaRequest,
    submitOrderStatus,
    claimOrder,
    clearOrderClaim,
    archiveOrder,
    restoreOrder,
    resetOrderFiltersForSelected,
    onImportFileSelected,
    dryRunImport,
    commitImport,
    addPromotionTarget,
    removePromotionTarget,
    submitPromotion,
    deletePromotion,
    submitPromoCode,
    deletePromoCode,
    startCreateTax,
    selectTax,
    submitTax,
    deleteTax,
    setProductCategoryFilter,
  }));

  function tabCount(tabId) {
    if (tabId === 'home') return homeState.loaded ? homeForm.sections.length : navigationCounts.home;
    if (tabId === 'products') return productState.loaded ? productState.items.length : navigationCounts.products;
    if (tabId === 'categories') return categoryState.loaded ? categoryState.items.length : navigationCounts.categories;
    if (tabId === 'brands') return brandState.loaded ? brandState.items.length : navigationCounts.brands;
    if (tabId === 'inventory') return inventoryState.loaded ? inventoryState.items.length : navigationCounts.inventory;
    if (tabId === 'orders') return orderState.loaded ? orderState.items.length : navigationCounts.orders;
    if (tabId === 'imports') return importState.loaded ? importState.jobs.length : navigationCounts.imports;
    if (tabId === 'promotions') return promotionState.loaded ? promotionState.items.length + promotionState.promoCodes.length : navigationCounts.promotions;
    if (tabId === 'tax') return taxState.loaded ? taxState.items.length : navigationCounts.tax;
    if (tabId === 'analytics') return analyticsState.loaded ? analyticsState.managerRows.length : navigationCounts.analytics;
    if (tabId === 'alerts') return alertState.loaded ? alertState.rows.length : navigationCounts.alerts;
    return 0;
  }

  function isTabLoading(tabId) {
    return loading[tabId];
  }

  function orderManagerLabel(order) {
    return order?.managerEmail || order?.managerSubject || 'Не назначен';
  }

  function isOrderArchived(order) {
    return Boolean(order?.archivedAt);
  }

  function orderStatusLabel(status) {
    const value = String(status || '').trim().toUpperCase();
    if (!value) return '';
    const labels = {
      PENDING: 'Ожидает оплаты',
      PAID: 'Оплачен',
      PROCESSING: 'В обработке',
      READY_FOR_PICKUP: 'Готов к выдаче',
      SHIPPED: 'Отгружен',
      DELIVERED: 'Доставлен',
      RECEIVED: 'Получен',
      COMPLETED: 'Получен',
      CANCELLED: 'Отменен',
      REFUNDED: 'Возвращен',
    };
    return labels[value] || value;
  }

  function rmaStatusLabel(status) {
    const value = String(status || '').trim().toUpperCase();
    if (value === 'REQUESTED') return 'Запрошен';
    if (value === 'APPROVED') return 'Одобрен';
    if (value === 'REJECTED') return 'Отклонен';
    return value || 'Не указан';
  }

  function isOrderAssigned(order) {
    return Boolean(order?.managerEmail || order?.managerSubject || order?.managerDirectusUserId);
  }

  function isOrderAssignedToCurrentUser(order) {
    if (!order) {
      return false;
    }
    const currentUserId = normalizeRoleToken(accessState.userId);
    const currentEmail = normalizeRoleToken(accessState.email);
    const assignedUserId = normalizeRoleToken(order.managerDirectusUserId);
    const assignedEmail = normalizeRoleToken(order.managerEmail);
    const assignedSubject = normalizeRoleToken(order.managerSubject);
    if (currentUserId && assignedUserId && currentUserId === assignedUserId) {
      return true;
    }
    return Boolean(currentEmail && (currentEmail === assignedEmail || currentEmail === assignedSubject));
  }

  function nextAllowedOrderStatus(currentStatus) {
    if (roleKind.value === 'picker') {
      return PICKER_TARGET_STATUS_OPTIONS.includes(currentStatus)
        ? currentStatus
        : PICKER_TARGET_STATUS_OPTIONS[0];
    }
    return currentStatus || ORDER_STATUS_OPTIONS[0];
  }

  function resolveRoleKind(state) {
    return resolveStorefrontOpsRoleKind(state);
  }

  function canAccessTab(tabId) {
    return canAccessStorefrontOpsTab(tabId, roleKind.value, tabs);
  }

  function normalizeActiveTab({ notify = false } = {}) {
    if (canAccessTab(activeTab.value)) {
      return;
    }
    const requestedTab = activeTab.value;
    activeTab.value = defaultTab.value;
    if (notify && requestedTab) {
      setInfo('Раздел скрыт для вашей роли Directus. Открыт доступный раздел.');
    }
  }

  function clearMessages() {
    pageError.value = '';
    pageNotice.type = '';
    pageNotice.text = '';
  }

  function setSuccess(message) {
    pageError.value = '';
    pageNotice.type = 'success';
    pageNotice.text = message;
  }

  function setInfo(message) {
    pageError.value = '';
    pageNotice.type = 'info';
    pageNotice.text = message;
  }

  function setLocalChange(message = LOCAL_CHANGE_NOTICE) {
    setInfo(message);
  }

  function setError(error) {
    pageNotice.type = '';
    pageNotice.text = '';
    pageError.value =
      error?.response?.data?.errors?.[0]?.message ||
      error?.response?.data?.error ||
      error?.response?.data?.message ||
      error?.message ||
      'Произошла непредвиденная ошибка.';
  }

  function normalizeHomeReferenceKind(value) {
    return String(value || '').trim().toLowerCase().replace(/[-\s]+/g, '_');
  }

  function normalizeHomeKey(value) {
    return String(value || '').trim().toLowerCase();
  }

  function normalizeHomeLayoutVariant(value) {
    const normalized = normalizeHomeReferenceKind(value);
    if (normalized === 'split_media' || normalized === 'two_column') {
      return 'media_right';
    }
    if (normalized === 'grid') {
      return 'cards';
    }
    return HOME_LAYOUT_OPTIONS.some((option) => option.value === normalized) ? normalized : 'contained';
  }

  function normalizeHomeStyleVariant(value) {
    const normalized = normalizeHomeReferenceKind(value);
    if (normalized === 'highlight') {
      return 'accent';
    }
    return HOME_STYLE_OPTIONS.some((option) => option.value === normalized) ? normalized : 'default';
  }

  function nextHomeClientId(prefix = 'home') {
    return `${prefix}:${Date.now()}:${Math.random().toString(36).slice(2, 8)}`;
  }

  function extractHomeSectionType(section) {
    return section?.section_type || section?.sectionType || '';
  }

  function homeSectionTypeLabel(sectionType) {
    return HOME_SECTION_TYPE_LABELS[sectionType] || sectionType || 'Секция';
  }

  function directusItemArray(payload) {
    return Array.isArray(payload) ? payload : [];
  }

  function directusItemId(item) {
    return item?.id || '';
  }

  function homeStatusLabel(status) {
    return HOME_STATUS_OPTIONS.find((option) => option.value === status)?.label || overlayStatusLabel(status);
  }

  function createHomeFormItem(item = {}, index = 0) {
    return {
      clientId: nextHomeClientId('item'),
      id: directusItemId(item),
      migrationKey: item.migration_key || '',
      status: item.status || 'draft',
      title: item.title || '',
      description: item.description || '',
      label: item.label || '',
      url: item.url || '',
      referenceKind: item.reference_kind || item.referenceKind || 'none',
      referenceKey: item.reference_key || item.referenceKey || '',
      sort: Number(item.sort ?? index + 1),
    };
  }

  function isHomeHeroFeaturedItem(item = {}) {
    return Boolean(
      normalizeHomeReferenceKind(item.reference_kind || item.referenceKind) === 'product_slug' &&
        (item.reference_key || item.referenceKey)
    );
  }

  function createHomeAnnouncementBannerForm(siteSettings = {}) {
    const banner = siteSettings?.announcement_banner || siteSettings?.announcementBanner || null;
    const bannerObject = banner && typeof banner === 'object' ? banner : null;
    return {
      id: bannerObject?.id || (typeof banner === 'string' ? banner : ''),
      status: bannerObject?.status || 'draft',
      internalName: bannerObject?.internal_name || bannerObject?.internalName || 'Баннер в шапке',
      shortText: bannerObject?.short_text || bannerObject?.shortText || '',
    };
  }

  function createHomeFormSection(section = {}, items = [], index = 0) {
    const sectionType = extractHomeSectionType(section);
    const heroFeaturedItem = sectionType === 'hero'
      ? directusItemArray(items).find(isHomeHeroFeaturedItem)
      : null;
    const normalizedItems = sectionType === 'category_reference_list'
      ? directusItemArray(items).filter((item) => (
          normalizeHomeReferenceKind(item.reference_kind || item.referenceKind) === 'category_slug' &&
          (item.reference_key || item.referenceKey)
        ))
      : sectionType === 'hero'
      ? directusItemArray(items).filter((item) => !isHomeHeroFeaturedItem(item))
      : directusItemArray(items);
    return {
      clientId: nextHomeClientId('section'),
      id: directusItemId(section),
      migrationKey: section.migration_key || '',
      status: section.status || 'draft',
      internalName: section.internal_name || section.internalName || section.title || homeSectionTypeLabel(sectionType),
      sectionType,
      sort: Number(section.sort ?? index + 1),
      anchorId: section.anchor_id || section.anchorId || '',
      eyebrow: section.eyebrow || '',
      title: section.title || '',
      accent: section.accent || '',
      body: section.body || '',
      primaryCtaLabel: section.primary_cta_label || section.primaryCtaLabel || '',
      primaryCtaUrl: section.primary_cta_url || section.primaryCtaUrl || '',
      secondaryCtaLabel: section.secondary_cta_label || section.secondaryCtaLabel || '',
      secondaryCtaUrl: section.secondary_cta_url || section.secondaryCtaUrl || '',
      styleVariant: normalizeHomeStyleVariant(section.style_variant || section.styleVariant || 'default'),
      layoutVariant: normalizeHomeLayoutVariant(section.layout_variant || section.layoutVariant || 'contained'),
      productQuery: '',
      productToAdd: '',
      featuredProductKey: heroFeaturedItem?.reference_key || heroFeaturedItem?.referenceKey || '',
      featuredProductToAdd: '',
      featuredProductQuery: '',
      featuredProductItemId: directusItemId(heroFeaturedItem),
      featuredProductMigrationKey: heroFeaturedItem?.migration_key || '',
      featuredProductStatus: heroFeaturedItem?.status || section.status || 'draft',
      items: normalizedItems.map(createHomeFormItem),
    };
  }

  function resetHomeForm(page, sections, itemsBySection, siteSettings = null) {
    Object.assign(homeForm.page, {
      id: directusItemId(page),
      status: page?.status || 'draft',
      title: page?.title || '',
      summary: page?.summary || '',
      seoTitle: page?.seo_title || page?.seoTitle || '',
      seoDescription: page?.seo_description || page?.seoDescription || '',
    });
    Object.assign(homeForm.announcementBanner, createHomeAnnouncementBannerForm(siteSettings));
    homeForm.sections.splice(
      0,
      homeForm.sections.length,
      ...directusItemArray(sections).map((section, index) =>
        createHomeFormSection(section, itemsBySection.get(section.id) || [], index)
      )
    );
    homeState.originalItemIds = homeForm.sections.flatMap((section) =>
      [
        section.featuredProductItemId,
        ...section.items.map((item) => item.id),
      ].filter(Boolean)
    );
    homeState.selectedSectionIndex = Math.max(0, Math.min(homeState.selectedSectionIndex, homeForm.sections.length - 1));
  }

  function encodeDirectusQuery(value) {
    return encodeURIComponent(String(value));
  }

  function collectHomeReferenceKeys(items, kind) {
    return Array.from(new Set(
      directusItemArray(items)
        .filter((item) => normalizeHomeReferenceKind(item.reference_kind || item.referenceKind) === kind)
        .map((item) => item.reference_key || item.referenceKey)
        .filter(Boolean)
        .map((item) => String(item).trim())
    ));
  }

  function createHomeCollectionForm(collection = {}) {
    return {
      id: directusItemId(collection),
      key: collection.key || '',
      status: collection.status || 'draft',
      title: collection.title || '',
      description: collection.description || '',
      mode: collection.mode || 'hybrid',
      ruleType: collection.rule_type || collection.ruleType || '',
      categoryKey: collection.category_key || collection.categoryKey || '',
      brandKey: collection.brand_key || collection.brandKey || '',
      limit: Number(collection.limit ?? 12),
      sortMode: collection.sort_mode || collection.sortMode || 'default',
      primaryCtaLabel: collection.primary_cta_label || collection.primaryCtaLabel || '',
      primaryCtaUrl: collection.primary_cta_url || collection.primaryCtaUrl || '',
    };
  }

  function createHomeCollectionRuleForm(item = {}, index = 0) {
    return {
      clientId: nextHomeClientId('collection-rule'),
      id: directusItemId(item),
      status: item.status || 'draft',
      storefrontCollection: item.storefront_collection || item.storefrontCollection || '',
      entityKind: item.entity_kind || item.entityKind || 'product',
      entityKey: item.entity_key || item.entityKey || '',
      behavior: item.behavior || 'pin',
      sort: Number(item.sort ?? index + 1),
    };
  }

  async function loadHomeCommerceOptions({ categoryKeys = [], productKeys = [], categoryQuery = '', productQuery = '' } = {}) {
    const [categoriesResponse, productsResponse] = await Promise.all([
      bridgeRequest('/workspace/categories', {
        params: {
          q: categoryQuery || undefined,
          keys: categoryKeys.join(',') || undefined,
          limit: 120,
        },
      }),
      bridgeRequest('/workspace/products', {
        params: {
          q: productQuery || undefined,
          keys: productKeys.join(',') || undefined,
          limit: 120,
        },
      }),
    ]);
    homeState.categoryOptions = categoriesResponse.items || [];
    homeState.productOptions = productsResponse.items || [];
  }

  async function loadHomeCollections() {
    const collections = directusItemArray(await directusRequest(
      '/items/storefront_collection?sort=title,key&limit=-1&fields=*'
    ));
    const collectionIds = collections.map((collection) => collection.id).filter(Boolean);
    const rules = collectionIds.length
      ? directusItemArray(await directusRequest(
          `/items/storefront_collection_item?filter[storefront_collection][_in]=${encodeDirectusQuery(collectionIds.join(','))}&sort=sort,id&limit=-1&fields=*`
        ))
      : [];
    const itemsById = rules.reduce((acc, item) => {
      const key = item.storefront_collection;
      if (!acc[key]) {
        acc[key] = [];
      }
      acc[key].push(createHomeCollectionRuleForm(item));
      return acc;
    }, {});
    homeState.collectionOptions = collections.map(createHomeCollectionForm);
    homeState.collectionItemsById = itemsById;
    homeState.originalCollectionRuleIds = rules.map((rule) => rule.id).filter(Boolean);
  }

  async function loadHomeContent() {
    loading.home = true;
    try {
      const pageRows = directusItemArray(await directusRequest(
        `/items/page?filter[slug][_eq]=${encodeDirectusQuery(HOME_PAGE_SLUG)}&limit=1&fields=*`
      ));
      const page = pageRows[0] || null;
      if (!page?.id) {
        homeState.loaded = true;
        homeState.page = null;
        homeState.sections = [];
        homeForm.sections.splice(0, homeForm.sections.length);
        pageError.value = 'Страница home не найдена в Directus. Запустите импорт начального контента.';
        return;
      }

      const sections = directusItemArray(await directusRequest(
        `/items/page_sections?filter[page][_eq]=${encodeDirectusQuery(page.id)}&sort=sort,id&limit=-1&fields=*`
      ));
      const sectionIds = sections.map((section) => section.id).filter(Boolean);
      const items = sectionIds.length
        ? directusItemArray(await directusRequest(
            `/items/page_section_items?filter[page_section][_in]=${encodeDirectusQuery(sectionIds.join(','))}&sort=sort,id&limit=-1&fields=*`
          ))
        : [];
      const itemsBySection = items.reduce((acc, item) => {
        const key = item.page_section;
        if (!acc.has(key)) {
          acc.set(key, []);
        }
        acc.get(key).push(item);
        return acc;
      }, new Map());

      const categoryKeys = collectHomeReferenceKeys(items, 'category_slug');
      const productKeys = collectHomeReferenceKeys(items, 'product_slug');
      await Promise.all([
        loadHomeCommerceOptions({ categoryKeys, productKeys }),
        loadHomeCollections(),
      ]);
      const siteSettings = await directusRequest(
        '/items/site_settings?fields=*,announcement_banner.*'
      );
      homeState.page = page;
      homeState.sections = sections;
      homeState.loaded = true;
      resetHomeForm(page, sections, itemsBySection, siteSettings);
      navigationCounts.home = homeForm.sections.length;
    } catch (error) {
      setError(error);
    } finally {
      loading.home = false;
    }
  }

  function homeSectionLabel(section) {
    return section.internalName || section.title || homeSectionTypeLabel(section.sectionType);
  }

  function homeCategoryLabel(slug) {
    const normalizedSlug = normalizeHomeKey(slug);
    const category = (homeState.categoryOptions || []).find((candidate) => normalizeHomeKey(candidate.slug) === normalizedSlug);
    return category?.name || slug || 'Категория';
  }

  function homeCategoryMeta(slug) {
    const normalizedSlug = normalizeHomeKey(slug);
    const category = (homeState.categoryOptions || []).find((candidate) => normalizeHomeKey(candidate.slug) === normalizedSlug);
    if (!category) {
      return 'Категория не найдена в backend-каталоге';
    }
    return category.fullPath || category.slug;
  }

  function homeCategoryImage(slug) {
    const normalizedSlug = normalizeHomeKey(slug);
    const category = (homeState.categoryOptions || []).find((candidate) => normalizeHomeKey(candidate.slug) === normalizedSlug);
    return category?.imageUrl || '';
  }

  function homeProductBySlug(slug) {
    const normalizedSlug = normalizeHomeKey(slug);
    return (homeState.productOptions || []).find((candidate) => normalizeHomeKey(candidate.slug) === normalizedSlug) || null;
  }

  function homeProductLabel(slug) {
    const product = homeProductBySlug(slug);
    return product?.name || slug || 'Товар';
  }

  function homeProductMeta(slug) {
    const product = homeProductBySlug(slug);
    if (!product) {
      return 'Товар не найден в backend-каталоге';
    }
    const categoryPath = product.categories?.[0]?.fullPath || product.categories?.[0]?.slug || '';
    return [product.slug, categoryPath, product.isActive ? 'активен' : 'скрыт'].filter(Boolean).join(' · ');
  }

  function homeProductImage(slug) {
    return homeProductBySlug(slug)?.primaryImageUrl || '';
  }

  function homeCollectionByKey(key) {
    const normalizedKey = normalizeHomeKey(key);
    return (homeState.collectionOptions || []).find((candidate) => normalizeHomeKey(candidate.key) === normalizedKey) || null;
  }

  function homeCollectionLabel(key) {
    const collection = homeCollectionByKey(key);
    return collection?.title || key || 'Подборка';
  }

  function homeCollectionMeta(key) {
    const collection = homeCollectionByKey(key);
    if (!collection) {
      return 'Подборка не найдена в Directus';
    }
    return [collection.key, collection.mode, collection.status].filter(Boolean).join(' · ');
  }

  function homeSelectedProductKeys(section) {
    return new Set(
      (section?.items || [])
        .filter((item) => normalizeHomeReferenceKind(item.referenceKind) === 'product_slug' && item.referenceKey)
        .map((item) => normalizeHomeKey(item.referenceKey))
    );
  }

  function filteredHomeProductOptions(section) {
    const selectedKeys = homeSelectedProductKeys(section);
    const query = String(section?.productQuery || '').trim().toLowerCase();
    return (homeState.productOptions || [])
      .filter((product) => !selectedKeys.has(normalizeHomeKey(product.slug)))
      .filter((product) => {
        if (!query) {
          return true;
        }
        const categoryText = (product.categories || [])
          .map((category) => category.fullPath || category.slug || category.name)
          .filter(Boolean)
          .join(' ');
        return [product.name, product.slug, product.brand?.name, product.brand?.slug, categoryText]
          .filter(Boolean)
          .some((value) => String(value).toLowerCase().includes(query));
      });
  }

  async function ensureHomeProductOptions(section) {
    if (!section || filteredHomeProductOptions(section).length) {
      return;
    }
    await searchHomeProducts(section);
  }

  function filteredHomeFeaturedProductOptions(section) {
    const currentKey = normalizeHomeKey(section?.featuredProductKey);
    const query = String(section?.featuredProductQuery || '').trim().toLowerCase();
    return (homeState.productOptions || [])
      .filter((product) => normalizeHomeKey(product.slug) !== currentKey)
      .filter((product) => {
        if (!query) {
          return true;
        }
        const categoryText = (product.categories || [])
          .map((category) => category.fullPath || category.slug || category.name)
          .filter(Boolean)
          .join(' ');
        return [product.name, product.slug, product.brand?.name, product.brand?.slug, categoryText]
          .filter(Boolean)
          .some((value) => String(value).toLowerCase().includes(query));
      });
  }

  async function searchHomeCategories() {
    setInfo('Обновляю список категорий.');
    try {
      const categoryKeys = Array.from(homeSelectedCategoryKeys.value);
      await loadHomeCommerceOptions({
        categoryKeys,
        productKeys: Array.from(collectCurrentHomeProductKeys()),
        categoryQuery: homeState.categoryQuery,
      });
      setInfo('Список категорий обновлён.');
    } catch (error) {
      setError(error);
    }
  }

  async function searchHomeProducts(section) {
    setInfo('Обновляю список товаров.');
    try {
      const productKeys = Array.from(collectCurrentHomeProductKeys(section));
      await loadHomeCommerceOptions({
        categoryKeys: Array.from(homeSelectedCategoryKeys.value),
        productKeys,
        productQuery: section?.productQuery || '',
      });
      setInfo(
        filteredHomeProductOptions(section).length
          ? 'Список товаров обновлён.'
          : 'Товары для добавления не найдены. Измените поиск или проверьте каталог.'
      );
    } catch (error) {
      setError(error);
    }
  }

  async function searchHomeHeroProducts(section) {
    setInfo('Обновляю список товаров для hero.');
    try {
      const productKeys = Array.from(collectCurrentHomeProductKeys(section));
      await loadHomeCommerceOptions({
        categoryKeys: Array.from(homeSelectedCategoryKeys.value),
        productKeys,
        productQuery: section?.featuredProductQuery || '',
      });
      setInfo('Список товаров для hero обновлён.');
    } catch (error) {
      setError(error);
    }
  }

  function collectCurrentHomeProductKeys(extraSection = null) {
    const sections = extraSection ? [...homeForm.sections, extraSection] : homeForm.sections;
    const keys = new Set(
      sections
        .flatMap((section) => section?.items || [])
        .filter((item) => normalizeHomeReferenceKind(item.referenceKind) === 'product_slug' && item.referenceKey)
        .map((item) => normalizeHomeKey(item.referenceKey))
    );
    sections
      .map((section) => normalizeHomeKey(section?.featuredProductKey))
      .filter(Boolean)
      .forEach((key) => keys.add(key));
    return keys;
  }

  function setHomeHeroFeaturedProduct(section) {
    if (!section || !section.featuredProductToAdd) {
      setInfo('Выберите товар для hero.');
      return;
    }
    section.featuredProductKey = section.featuredProductToAdd;
    section.featuredProductStatus = section.status || homeForm.page.status || 'draft';
    section.featuredProductToAdd = '';
    section.featuredProductQuery = '';
    setLocalChange();
  }

  function clearHomeHeroFeaturedProduct(section) {
    if (!section) {
      setInfo('Секция не выбрана.');
      return;
    }
    if (!section.featuredProductKey) {
      setInfo('Товар для hero уже не выбран.');
      return;
    }
    section.featuredProductKey = '';
    section.featuredProductToAdd = '';
    section.featuredProductQuery = '';
    setLocalChange();
  }

  function selectHomeSection(index) {
    if (!homeForm.sections.length) {
      setInfo('Секции главной пока не загружены.');
      return;
    }
    const nextIndex = Math.max(0, Math.min(index, homeForm.sections.length - 1));
    if (nextIndex === homeState.selectedSectionIndex) {
      return;
    }
    homeState.selectedSectionIndex = nextIndex;
  }

  function resequenceHomeSections() {
    homeForm.sections.forEach((section, index) => {
      section.sort = index + 1;
    });
  }

  function moveHomeSection(index, direction) {
    const nextIndex = index + direction;
    if (index < 0 || nextIndex < 0 || nextIndex >= homeForm.sections.length) {
      setInfo('Секция уже находится на границе списка.');
      return;
    }
    const [section] = homeForm.sections.splice(index, 1);
    homeForm.sections.splice(nextIndex, 0, section);
    homeState.selectedSectionIndex = nextIndex;
    resequenceHomeSections();
    setLocalChange();
  }

  function removeHomeSection(index) {
    const section = homeForm.sections[index];
    if (!section || section.id) {
      setInfo(section?.id ? 'Сохранённую секцию можно архивировать, но нельзя удалить из формы.' : 'Секция не выбрана.');
      return;
    }
    homeForm.sections.splice(index, 1);
    homeState.selectedSectionIndex = Math.max(0, Math.min(index, homeForm.sections.length - 1));
    resequenceHomeSections();
    setLocalChange();
  }

  function archiveHomeSection(section) {
    if (!section) {
      setInfo('Секция не выбрана.');
      return;
    }
    section.status = section.status === 'archived' ? 'draft' : 'archived';
    setLocalChange(section.status === 'archived' ? 'Секция будет архивирована после сохранения.' : 'Секция будет возвращена после сохранения.');
  }

  function duplicateHomeSection(section) {
    if (!section) {
      setInfo('Секция не выбрана.');
      return;
    }
    const duplicate = JSON.parse(JSON.stringify(section));
    duplicate.clientId = nextHomeClientId('section');
    duplicate.id = '';
    duplicate.migrationKey = `home:${section.sectionType || 'section'}:${Date.now()}`;
    duplicate.internalName = `${homeSectionLabel(section)} — копия`;
    duplicate.status = section.status === 'published' ? 'draft' : section.status;
    duplicate.items = (duplicate.items || []).map((item, index) => ({
      ...item,
      clientId: nextHomeClientId('item'),
      id: '',
      migrationKey: `${duplicate.migrationKey}:item:${index + 1}`,
      status: item.status === 'published' ? 'draft' : item.status,
    }));
    homeForm.sections.splice(homeState.selectedSectionIndex + 1, 0, duplicate);
    homeState.selectedSectionIndex += 1;
    resequenceHomeSections();
    setLocalChange('Секция продублирована. Нажмите «Сохранить», чтобы применить.');
  }

  function createHomeSectionPreset(sectionType) {
    const label = homeSectionTypeLabel(sectionType);
    const base = {
      clientId: nextHomeClientId('section'),
      id: '',
      migrationKey: `home:${sectionType}:${Date.now()}`,
      status: homeForm.page.status || 'draft',
      internalName: `Главная — ${label.toLowerCase()}`,
      sectionType,
      sort: homeForm.sections.length + 1,
      anchorId: '',
      eyebrow: '',
      title: '',
      accent: '',
      body: '',
      primaryCtaLabel: '',
      primaryCtaUrl: '',
      secondaryCtaLabel: '',
      secondaryCtaUrl: '',
      styleVariant: 'default',
      layoutVariant: 'contained',
      productQuery: '',
      productToAdd: '',
      featuredProductKey: '',
      featuredProductToAdd: '',
      featuredProductQuery: '',
      featuredProductItemId: '',
      featuredProductMigrationKey: '',
      featuredProductStatus: homeForm.page.status || 'draft',
      items: [],
    };
    if (sectionType === 'hero') {
      return { ...base, styleVariant: 'warm', layoutVariant: 'media_right', title: 'Новый первый экран' };
    }
    if (sectionType === 'product_reference_list') {
      return { ...base, layoutVariant: 'cards', title: 'Товарная секция' };
    }
    if (sectionType === 'collection_teaser') {
      return { ...base, layoutVariant: 'rail', title: 'Витринная подборка' };
    }
    if (sectionType === 'category_reference_list' || sectionType === 'feature_list' || sectionType === 'banner_group') {
      return { ...base, layoutVariant: 'cards' };
    }
    return base;
  }

  function addHomeSectionPreset() {
    const section = createHomeSectionPreset(homeState.presetToAdd || 'product_reference_list');
    homeForm.sections.push(section);
    homeState.selectedSectionIndex = homeForm.sections.length - 1;
    resequenceHomeSections();
    setLocalChange('Секция добавлена. Нажмите «Сохранить», чтобы применить.');
  }

  function addHomeCategory(section) {
    if (!section || !homeState.categoryToAdd) {
      setInfo('Выберите категорию для добавления.');
      return;
    }
    const category = (homeState.categoryOptions || []).find((candidate) => candidate.slug === homeState.categoryToAdd);
    if (!category) {
      setInfo('Выбранная категория недоступна. Обновите список категорий.');
      return;
    }
    section.items.push({
      clientId: nextHomeClientId('item'),
      id: '',
      migrationKey: `home:category:${category.slug}`,
      status: section.status || homeForm.page.status || 'draft',
      title: '',
      description: category.description || '',
      label: '',
      url: '',
      referenceKind: 'category_slug',
      referenceKey: category.slug,
      sort: section.items.length + 1,
    });
    homeState.categoryToAdd = '';
    homeState.categoryQuery = '';
    resequenceHomeItems(section);
    setLocalChange();
  }

  function addHomeProduct(section) {
    if (!section || !section.productToAdd) {
      setInfo('Выберите товар для добавления.');
      return;
    }
    const product = homeProductBySlug(section.productToAdd);
    if (!product) {
      setInfo('Выбранный товар недоступен. Обновите список товаров.');
      return;
    }
    section.items.push({
      clientId: nextHomeClientId('item'),
      id: '',
      migrationKey: `home:product:${product.slug}:${Date.now()}`,
      status: section.status || homeForm.page.status || 'draft',
      title: '',
      description: '',
      label: '',
      url: '',
      referenceKind: 'product_slug',
      referenceKey: product.slug,
      sort: section.items.length + 1,
    });
    section.productToAdd = '';
    section.productQuery = '';
    resequenceHomeItems(section);
    setLocalChange();
  }

  function addHomeItem(section) {
    if (!section) {
      setInfo('Секция не выбрана.');
      return;
    }
    section.items.push({
      clientId: nextHomeClientId('item'),
      id: '',
      migrationKey: `home:${section.sectionType || 'section'}:${Date.now()}`,
      status: section.status || homeForm.page.status || 'draft',
      title: '',
      description: '',
      label: '',
      url: '',
      referenceKind: section.sectionType === 'product_reference_list' ? 'product_slug' : 'none',
      referenceKey: '',
      sort: section.items.length + 1,
    });
    setLocalChange();
  }

  function addHomeCollection(section) {
    if (!section || !homeState.collectionToAdd) {
      setInfo('Выберите подборку для добавления.');
      return;
    }
    const collection = homeCollectionByKey(homeState.collectionToAdd);
    if (!collection) {
      setInfo('Выбранная подборка недоступна. Обновите список подборок.');
      return;
    }
    section.items.push({
      clientId: nextHomeClientId('item'),
      id: '',
      migrationKey: `home:collection:${collection.key}:${Date.now()}`,
      status: section.status || homeForm.page.status || 'draft',
      title: '',
      description: '',
      label: '',
      url: '',
      referenceKind: 'storefront_collection',
      referenceKey: collection.key,
      sort: section.items.length + 1,
    });
    homeState.collectionToAdd = '';
    resequenceHomeItems(section);
    setLocalChange();
  }

  function startCreateHomeCollection() {
    homeState.collectionDraft = {
      ...createHomeCollectionForm({
        key: `home-${Date.now()}`,
        title: 'Новая подборка',
        status: 'draft',
        mode: 'hybrid',
        limit: 12,
        sort_mode: 'default',
      }),
      rules: [],
      originalRuleIds: [],
    };
    setInfo('Создан черновик подборки. Заполните поля и сохраните подборку.');
  }

  function editHomeCollection(key) {
    const collection = homeCollectionByKey(key);
    if (!collection) {
      setInfo('Подборка не найдена. Обновите список подборок.');
      return;
    }
    const rules = (homeState.collectionItemsById[collection.id] || []).map((rule) => ({ ...rule }));
    homeState.collectionDraft = {
      ...collection,
      rules,
      originalRuleIds: rules.map((rule) => rule.id).filter(Boolean),
    };
    setInfo(`Открыта подборка «${collection.title || collection.key}».`);
  }

  function cancelHomeCollectionDraft() {
    if (!homeState.collectionDraft) {
      setInfo('Черновик подборки уже закрыт.');
      return;
    }
    homeState.collectionDraft = null;
    homeState.collectionProductToAdd = '';
    homeState.collectionCategoryToAdd = '';
    setInfo('Редактирование подборки закрыто.');
  }

  async function searchHomeCollectionProducts() {
    setInfo('Обновляю список товаров для подборки.');
    try {
      await loadHomeCommerceOptions({
        categoryKeys: Array.from(homeSelectedCategoryKeys.value),
        productKeys: Array.from(collectCurrentHomeProductKeys()),
        productQuery: homeState.collectionProductQuery,
      });
      setInfo('Список товаров для подборки обновлён.');
    } catch (error) {
      setError(error);
    }
  }

  async function searchHomeCollectionCategories() {
    setInfo('Обновляю список категорий для подборки.');
    try {
      await loadHomeCommerceOptions({
        categoryKeys: Array.from(homeSelectedCategoryKeys.value),
        productKeys: Array.from(collectCurrentHomeProductKeys()),
        categoryQuery: homeState.collectionCategoryQuery,
      });
      setInfo('Список категорий для подборки обновлён.');
    } catch (error) {
      setError(error);
    }
  }

  function addHomeCollectionProductRule() {
    if (!homeState.collectionDraft || !homeState.collectionProductToAdd) {
      setInfo(homeState.collectionDraft ? 'Выберите товар для правила подборки.' : 'Откройте или создайте подборку.');
      return;
    }
    homeState.collectionDraft.rules.push(createHomeCollectionRuleForm({
      entity_kind: 'product',
      entity_key: homeState.collectionProductToAdd,
      behavior: 'pin',
      sort: homeState.collectionDraft.rules.length + 1,
      status: homeState.collectionDraft.status || 'draft',
    }));
    homeState.collectionProductToAdd = '';
    resequenceHomeCollectionRules();
    setLocalChange('Правило подборки добавлено. Сохраните подборку, чтобы применить.');
  }

  function addHomeCollectionCategoryRule() {
    if (!homeState.collectionDraft || !homeState.collectionCategoryToAdd) {
      setInfo(homeState.collectionDraft ? 'Выберите категорию для правила подборки.' : 'Откройте или создайте подборку.');
      return;
    }
    homeState.collectionDraft.rules.push(createHomeCollectionRuleForm({
      entity_kind: 'category',
      entity_key: homeState.collectionCategoryToAdd,
      behavior: 'pin',
      sort: homeState.collectionDraft.rules.length + 1,
      status: homeState.collectionDraft.status || 'draft',
    }));
    homeState.collectionCategoryToAdd = '';
    resequenceHomeCollectionRules();
    setLocalChange('Правило подборки добавлено. Сохраните подборку, чтобы применить.');
  }

  function removeHomeCollectionRule(index) {
    if (!homeState.collectionDraft || index < 0) {
      setInfo(homeState.collectionDraft ? 'Правило подборки не выбрано.' : 'Откройте или создайте подборку.');
      return;
    }
    homeState.collectionDraft.rules.splice(index, 1);
    resequenceHomeCollectionRules();
    setLocalChange('Правило подборки убрано. Сохраните подборку, чтобы применить.');
  }

  function moveHomeCollectionRule(index, direction) {
    const rules = homeState.collectionDraft?.rules || [];
    const nextIndex = index + direction;
    if (index < 0 || nextIndex < 0 || nextIndex >= rules.length) {
      setInfo('Правило уже находится на границе списка.');
      return;
    }
    const [rule] = rules.splice(index, 1);
    rules.splice(nextIndex, 0, rule);
    resequenceHomeCollectionRules();
    setLocalChange('Порядок правил изменён. Сохраните подборку, чтобы применить.');
  }

  function resequenceHomeCollectionRules() {
    (homeState.collectionDraft?.rules || []).forEach((rule, index) => {
      rule.sort = index + 1;
    });
  }

  function buildHomeCollectionPayload(collection) {
    return {
      status: collection.status || 'draft',
      key: normalizeNullableText(collection.key),
      title: normalizeNullableText(collection.title) || collection.key,
      description: normalizeNullableText(collection.description),
      mode: collection.mode || 'hybrid',
      rule_type: normalizeNullableText(collection.ruleType),
      category_key: normalizeNullableText(collection.categoryKey),
      brand_key: normalizeNullableText(collection.brandKey),
      limit: Number(collection.limit || 12),
      sort_mode: collection.sortMode || 'default',
      primary_cta_label: normalizeNullableText(collection.primaryCtaLabel),
      primary_cta_url: normalizeNullableText(collection.primaryCtaUrl),
    };
  }

  function buildHomeCollectionRulePayload(rule, collectionId) {
    return {
      status: rule.status || homeState.collectionDraft?.status || 'draft',
      storefront_collection: collectionId,
      entity_kind: rule.entityKind || 'product',
      entity_key: normalizeNullableText(rule.entityKey),
      behavior: rule.behavior || 'pin',
      sort: Number(rule.sort || 0),
    };
  }

  async function saveHomeCollectionDraft() {
    const collection = homeState.collectionDraft;
    if (!collection?.key) {
      pageError.value = 'Укажите ключ подборки.';
      return;
    }
    isSubmitting.value = true;
    clearMessages();
    try {
      resequenceHomeCollectionRules();
      const payload = buildHomeCollectionPayload(collection);
      const collectionRecord = collection.id
        ? await directusRequest(`/items/storefront_collection/${collection.id}`, { method: 'PATCH', data: payload })
        : await directusRequest('/items/storefront_collection', {
            method: 'POST',
            data: { ...payload, migration_key: `home:collection:${collection.key}` },
          });
      collection.id = collectionRecord?.id || collection.id;

      const activeRuleIds = new Set();
      for (const rule of collection.rules || []) {
        if (!rule.entityKey) {
          continue;
        }
        const rulePayload = buildHomeCollectionRulePayload(rule, collection.id);
        const ruleRecord = rule.id
          ? await directusRequest(`/items/storefront_collection_item/${rule.id}`, { method: 'PATCH', data: rulePayload })
          : await directusRequest('/items/storefront_collection_item', { method: 'POST', data: rulePayload });
        rule.id = ruleRecord?.id || rule.id;
        if (rule.id) {
          activeRuleIds.add(String(rule.id));
        }
      }

      const removedRuleIds = (collection.originalRuleIds || []).filter((id) => id && !activeRuleIds.has(String(id)));
      for (const removedId of removedRuleIds) {
        await directusRequest(`/items/storefront_collection_item/${removedId}`, {
          method: 'PATCH',
          data: { status: 'archived', sort: 999 },
        });
      }

      await bridgeRequest('/admin/content/cache/invalidate', {
        method: 'POST',
        data: { scope: 'collection', key: collection.key },
      });
      await loadHomeCollections();
      editHomeCollection(collection.key);
      setSuccess('Подборка сохранена.');
    } catch (error) {
      setError(error);
    } finally {
      isSubmitting.value = false;
    }
  }

  function removeHomeItem(section, index) {
    if (!section || index < 0) {
      setInfo('Элемент секции не выбран.');
      return;
    }
    section.items.splice(index, 1);
    resequenceHomeItems(section);
    setLocalChange();
  }

  function moveHomeItem(section, index, direction) {
    if (!section) {
      setInfo('Секция не выбрана.');
      return;
    }
    const nextIndex = index + direction;
    if (nextIndex < 0 || nextIndex >= section.items.length) {
      setInfo('Элемент уже находится на границе списка.');
      return;
    }
    const [item] = section.items.splice(index, 1);
    section.items.splice(nextIndex, 0, item);
    resequenceHomeItems(section);
    setLocalChange();
  }

  function resequenceHomeItems(section) {
    (section?.items || []).forEach((item, index) => {
      item.sort = index + 1;
    });
  }

  function buildHomeSectionPayload(section) {
    return {
      status: section.status || homeForm.page.status || 'draft',
      internal_name: section.internalName || homeSectionTypeLabel(section.sectionType),
      section_type: section.sectionType,
      sort: Number(section.sort || 0),
      anchor_id: normalizeNullableText(section.anchorId),
      eyebrow: normalizeNullableText(section.eyebrow),
      title: normalizeNullableText(section.title),
      accent: normalizeNullableText(section.accent),
      body: normalizeNullableText(section.body),
      primary_cta_label: normalizeNullableText(section.primaryCtaLabel),
      primary_cta_url: normalizeNullableText(section.primaryCtaUrl),
      secondary_cta_label: normalizeNullableText(section.secondaryCtaLabel),
      secondary_cta_url: normalizeNullableText(section.secondaryCtaUrl),
      style_variant: normalizeHomeStyleVariant(section.styleVariant),
      layout_variant: normalizeHomeLayoutVariant(section.layoutVariant),
    };
  }

  function buildHomeItemPayload(section, item, pageSectionId) {
    return {
      status: item.status || section.status || homeForm.page.status || 'draft',
      page_section: pageSectionId,
      migration_key: normalizeNullableText(item.migrationKey),
      title: normalizeNullableText(item.title),
      description: normalizeNullableText(item.description),
      label: normalizeNullableText(item.label),
      url: normalizeNullableText(item.url),
      reference_kind: item.referenceKind || 'none',
      reference_key: normalizeNullableText(item.referenceKey),
      sort: Number(item.sort || 0),
    };
  }

  function buildHomeHeroFeaturedItem(section) {
    if (section.sectionType !== 'hero' || !section.featuredProductKey) {
      return null;
    }

    return {
      id: section.featuredProductItemId || '',
      migrationKey: section.featuredProductMigrationKey || `home:hero:featured-product:${section.id || section.clientId}`,
      status: section.status || section.featuredProductStatus || homeForm.page.status || 'draft',
      title: '',
      description: '',
      label: 'Выбор недели',
      url: '',
      referenceKind: 'product_slug',
      referenceKey: section.featuredProductKey,
      sort: 0,
    };
  }

  function homeItemsForSave(section) {
    const featuredItem = buildHomeHeroFeaturedItem(section);
    return featuredItem ? [featuredItem, ...(section.items || [])] : (section.items || []);
  }

  function buildHomeAnnouncementBannerPayload() {
    return {
      status: homeForm.announcementBanner.status || 'draft',
      banner_type: 'announcement',
      internal_name: normalizeNullableText(homeForm.announcementBanner.internalName) || 'Баннер в шапке',
      short_text: normalizeNullableText(homeForm.announcementBanner.shortText),
    };
  }

  async function saveHomeAnnouncementBanner() {
    const bannerPayload = buildHomeAnnouncementBannerPayload();
    const shouldPersistBanner = Boolean(
      homeForm.announcementBanner.id ||
        bannerPayload.short_text
    );

    if (!shouldPersistBanner) {
      return;
    }

    const bannerRecord = homeForm.announcementBanner.id
      ? await directusRequest(`/items/banner/${homeForm.announcementBanner.id}`, {
          method: 'PATCH',
          data: bannerPayload,
        })
      : await directusRequest('/items/banner', {
          method: 'POST',
          data: {
            ...bannerPayload,
            migration_key: `site:announcement:${Date.now()}`,
            sort: 1,
          },
        });

    homeForm.announcementBanner.id = bannerRecord?.id || homeForm.announcementBanner.id;
    if (homeForm.announcementBanner.id) {
      await directusRequest('/items/site_settings', {
        method: 'PATCH',
        data: { announcement_banner: homeForm.announcementBanner.id },
      });
    }
  }

  async function saveHomeContent() {
    if (!homeForm.page.id) {
      pageError.value = 'Страница home не загружена.';
      return;
    }
    isSubmitting.value = true;
    clearMessages();
    try {
      resequenceHomeSections();
      await directusRequest(`/items/page/${homeForm.page.id}`, {
        method: 'PATCH',
        data: {
          status: homeForm.page.status || 'draft',
          title: normalizeNullableText(homeForm.page.title),
          summary: normalizeNullableText(homeForm.page.summary),
          seo_title: normalizeNullableText(homeForm.page.seoTitle),
          seo_description: normalizeNullableText(homeForm.page.seoDescription),
        },
      });
      await saveHomeAnnouncementBanner();

      const activeItemIds = new Set();
      for (const section of homeForm.sections) {
        const sectionPayload = buildHomeSectionPayload(section);
        const sectionRecord = section.id
          ? await directusRequest(`/items/page_sections/${section.id}`, { method: 'PATCH', data: sectionPayload })
          : await directusRequest('/items/page_sections', {
              method: 'POST',
              data: { ...sectionPayload, page: homeForm.page.id, migration_key: normalizeNullableText(section.migrationKey) },
            });
        section.id = sectionRecord?.id || section.id;

        for (const item of homeItemsForSave(section)) {
          const itemPayload = buildHomeItemPayload(section, item, section.id);
          const itemRecord = item.id
            ? await directusRequest(`/items/page_section_items/${item.id}`, { method: 'PATCH', data: itemPayload })
            : await directusRequest('/items/page_section_items', { method: 'POST', data: itemPayload });
          item.id = itemRecord?.id || item.id;
          if (section.sectionType === 'hero' && item.referenceKind === 'product_slug' && item.referenceKey === section.featuredProductKey) {
            section.featuredProductItemId = item.id;
            section.featuredProductMigrationKey = item.migrationKey;
          }
          if (item.id) {
            activeItemIds.add(String(item.id));
          }
        }
      }

      const removedIds = homeState.originalItemIds.filter((id) => id && !activeItemIds.has(String(id)));
      for (const removedId of removedIds) {
        await directusRequest(`/items/page_section_items/${removedId}`, {
          method: 'PATCH',
          data: {
            status: 'archived',
            reference_kind: 'none',
            reference_key: null,
            sort: 999,
          },
        });
      }

      await bridgeRequest('/admin/content/cache/invalidate', {
        method: 'POST',
        data: { scope: 'page', slug: HOME_PAGE_SLUG },
      });
      await bridgeRequest('/admin/content/cache/invalidate', {
        method: 'POST',
        data: { scope: 'site_settings' },
      });
      await loadHomeContent();
      setSuccess('Главная обновлена. Если изменения в статусе draft или in_review, они появятся после публикации.');
    } catch (error) {
      setError(error);
    } finally {
      isSubmitting.value = false;
    }
  }

  async function loadAccessProfile() {
    try {
      const profile = await bridgeRequest('/access-profile');
      applyAccessProfile(profile?.data || profile || {});
      return;
    } catch {
      // Older deployed endpoints do not expose the bridge profile yet.
    }

    try {
      const response = await requestAccessProfile([
        'id',
        'email',
        'external_identifier',
        'role.id',
        'role.name',
      ]);
      const user = response?.data?.data || response?.data || {};
      applyAccessProfile(user);
    } catch {
      try {
        const response = await requestAccessProfile([
          'id',
          'email',
          'external_identifier',
          'role',
        ]);
        const user = response?.data?.data || response?.data || {};
        applyAccessProfile(user);
      } catch {
        applyAccessProfile({});
      }
    } finally {
      accessState.loaded = true;
    }
  }

  function requestAccessProfile(fields) {
    return api.request({
      url: '/users/me',
      method: 'GET',
      params: {
        fields: fields.join(','),
      },
    });
  }

  function applyAccessProfile(user) {
    const role = user?.role || '';
    const roleIsObject = role && typeof role === 'object';
    accessState.userId = user?.id || '';
    accessState.email = user?.email || '';
    accessState.externalId = user?.external_identifier || user?.externalIdentifier || '';
    accessState.roleId = roleIsObject ? role.id || '' : role || '';
    accessState.roleName = roleIsObject ? role.name || '' : '';
    accessState.roleKind = user?.roleKind || user?.role_kind || (roleIsObject ? role.kind || '' : '');
    accessState.roleAdminAccess = Boolean(roleIsObject && (role.admin_access || role.adminAccess));
    accessState.previewBaseUrl = user?.preview?.baseUrl || user?.preview?.base_url || user?.storefrontPreviewBaseUrl || '';
  }

  async function loadProducts({ reloadSelected = true } = {}) {
    loading.products = true;
    try {
      const response = await bridgeRequest('/workspace/products');
      productState.items = response.items || [];
      productState.brandOptions = response.brandOptions || [];
      productState.categoryOptions = response.categoryOptions || [];
      productState.overlayReadFailed = Boolean(response.overlayReadFailed);
      productState.loaded = true;
      if (reloadSelected && productState.selectedId) {
        await loadProductDetail(productState.selectedId, { silent: true });
      }
    } catch (error) {
      setError(error);
    } finally {
      loading.products = false;
    }
  }

  async function loadProductDetail(id, { silent = false } = {}) {
    if (!id) {
      return;
    }
    if (!silent) {
      clearMessages();
    }
    loading.products = true;
    try {
      const response = await bridgeRequest(`/workspace/products/${id}`);
      productState.detail = response;
      productState.selectedId = response.item.id;
      productState.isCreating = false;
      productState.brandOptions = response.brandOptions || productState.brandOptions;
      productState.categoryOptions = response.categoryOptions || productState.categoryOptions;
      productState.overlayReadFailed = Boolean(response.overlayReadFailed);
      hydrateProductEditor(response.item);
    } catch (error) {
      setError(error);
    } finally {
      loading.products = false;
    }
  }

  async function loadCategories({ reloadSelected = true } = {}) {
    loading.categories = true;
    try {
      const response = await bridgeRequest('/workspace/categories');
      categoryState.items = response.items || [];
      categoryState.parentOptions = response.parentOptions || [];
      categoryState.overlayReadFailed = Boolean(response.overlayReadFailed);
      categoryState.loaded = true;
      if (reloadSelected && categoryState.selectedId) {
        await loadCategoryDetail(categoryState.selectedId, { silent: true });
      }
    } catch (error) {
      setError(error);
    } finally {
      loading.categories = false;
    }
  }

  async function loadCategoryDetail(id, { silent = false } = {}) {
    if (!id) {
      return;
    }
    if (!silent) {
      clearMessages();
    }
    loading.categories = true;
    try {
      const response = await bridgeRequest(`/workspace/categories/${id}`);
      categoryState.detail = response;
      categoryState.selectedId = response.item.id;
      categoryState.isCreating = false;
      categoryState.parentOptions = response.parentOptions || categoryState.parentOptions;
      categoryState.overlayReadFailed = Boolean(response.overlayReadFailed);
      hydrateCategoryEditor(response.item);
    } catch (error) {
      setError(error);
    } finally {
      loading.categories = false;
    }
  }

  async function loadBrands({ reloadSelected = true } = {}) {
    loading.brands = true;
    try {
      const response = await bridgeRequest('/workspace/brands');
      brandState.items = response.items || [];
      brandState.loaded = true;
      if (reloadSelected && brandState.selectedId) {
        await loadBrandDetail(brandState.selectedId, { silent: true });
      }
    } catch (error) {
      setError(error);
    } finally {
      loading.brands = false;
    }
  }

  async function loadBrandDetail(id, { silent = false } = {}) {
    if (!id) {
      return;
    }
    if (!silent) {
      clearMessages();
    }
    loading.brands = true;
    try {
      const response = await bridgeRequest(`/workspace/brands/${id}`);
      brandState.detail = response;
      brandState.selectedId = response.item.id;
      brandState.isCreating = false;
      hydrateBrandEditor(response.item);
    } catch (error) {
      setError(error);
    } finally {
      loading.brands = false;
    }
  }

  async function loadInventory() {
    loading.inventory = true;
    try {
      const response = await bridgeRequest('/workspace/inventory');
      inventoryState.items = response.items || [];
      inventoryState.loaded = true;
      if (inventoryState.selectedVariantId) {
        const exists = inventoryState.items.some((item) => item.variantId === inventoryState.selectedVariantId);
        if (!exists) {
          inventoryState.selectedVariantId = '';
          resetInventoryEditor({ silent: true });
        }
      }
    } catch (error) {
      setError(error);
    } finally {
      loading.inventory = false;
    }
  }

  async function loadNavigationSummary() {
    try {
      const summary = await bridgeRequest('/workspace/summary');
      navigationCounts.products = Number(summary?.productCount || 0);
      navigationCounts.categories = Number(summary?.categoryCount || 0);
      navigationCounts.brands = Number(summary?.brandCount || 0);
      navigationCounts.inventory = Number(summary?.inventoryCount || 0);
    } catch {
      // Keep current counts if the summary request fails.
    }
  }

  async function loadOrders({ notify = false } = {}) {
    loading.orders = true;
    try {
      const response = await bridgeRequest('/admin/orders', {
        params: compactParams({
          status: orderState.status,
          manager: orderState.manager,
          archived: roleKind.value === 'admin' ? orderState.archived : '',
          from: toIsoDateTime(orderState.from),
          to: toIsoDateTime(orderState.to),
          q: orderState.query,
        }),
      });
      orderState.items = response.items || [];
      orderState.loaded = true;
      navigationCounts.orders = orderState.items.length;
      if (orderState.selectedId) {
        const exists = orderState.items.some((order) => order.id === orderState.selectedId);
        if (exists) {
          orderState.selectedFilteredOut = false;
          await loadOrderDetail(orderState.selectedId, { silent: true });
        } else {
          orderState.selectedFilteredOut = Boolean(orderState.detail);
        }
      }
      if (notify) {
        setInfo('Заказы обновлены.');
      }
    } catch (error) {
      setError(error);
    } finally {
      loading.orders = false;
    }
  }

  async function loadOrderDetail(id, { silent = false } = {}) {
    if (!id) {
      return;
    }
    if (!silent) {
      clearMessages();
    }
    loading.orders = true;
    try {
      const response = await bridgeRequest(`/admin/orders/${id}`);
      orderState.detail = response;
      orderState.selectedId = id;
      orderState.selectedFilteredOut = false;
      orderState.nextStatus = nextAllowedOrderStatus(response?.order?.status || '');
      orderState.note = '';
      hydrateOrderRmaForms(response);
      hydrateOrderRefundForms(response);
    } catch (error) {
      setError(error);
    } finally {
      loading.orders = false;
    }
  }

  async function loadImports() {
    loading.imports = true;
    try {
      importState.jobs = await bridgeRequest('/admin/imports') || [];
      importState.loaded = true;
      navigationCounts.imports = importState.jobs.length;
    } catch (error) {
      setError(error);
    } finally {
      loading.imports = false;
    }
  }

  async function loadPromotions() {
    loading.promotions = true;
    try {
      const [promotions, promoCodes] = await Promise.all([
        bridgeRequest('/admin/promotions'),
        bridgeRequest('/admin/promo-codes'),
      ]);
      promotionState.items = promotions || [];
      promotionState.promoCodes = promoCodes || [];
      promotionState.loaded = true;
      navigationCounts.promotions = promotionState.items.length + promotionState.promoCodes.length;
      if (!promotionForm.id && !promoCodeForm.id) {
        startCreatePromotion({ silent: true });
      }
    } catch (error) {
      setError(error);
    } finally {
      loading.promotions = false;
    }
  }

  async function loadActivePromotions({ notify = false } = {}) {
    if (!canViewActivePromotions.value) {
      activePromotionState.items = [];
      activePromotionState.loaded = true;
      if (notify) {
        setInfo('Активные акции скрыты для вашей роли Directus.');
      }
      return;
    }
    loading.activePromotions = true;
    try {
      activePromotionState.items = await bridgeRequest('/admin/promotions/active') || [];
      activePromotionState.loaded = true;
      if (notify) {
        setInfo('Активные акции обновлены.');
      }
    } catch (error) {
      setError(error);
    } finally {
      loading.activePromotions = false;
    }
  }

  async function loadTaxSettings() {
    loading.tax = true;
    try {
      taxState.items = await bridgeRequest('/admin/tax-settings') || [];
      taxState.loaded = true;
      navigationCounts.tax = taxState.items.length;
      if (!taxForm.id && taxState.items.length) {
        selectTax(taxState.items.find((item) => item.active) || taxState.items[0], { silent: true });
      }
    } catch (error) {
      setError(error);
    } finally {
      loading.tax = false;
    }
  }

  async function loadAnalytics({ notify = false } = {}) {
    loading.analytics = true;
    try {
      const params = compactParams({
        from: toIsoDateTime(analyticsState.from),
        to: toIsoDateTime(analyticsState.to),
        manager: isManagerRole.value ? '' : analyticsState.manager,
      });
      const [managerResponse, paymentLinkResponse] = await Promise.all([
        bridgeRequest('/admin/analytics/managers', { params }),
        bridgeRequest('/admin/analytics/payment-links', { params }),
      ]);
      analyticsState.managerRows = managerResponse.rows || [];
      analyticsState.paymentLinks = paymentLinkResponse || { sent: 0, paid: 0, conversionRate: 0, rows: [] };
      analyticsState.loaded = true;
      navigationCounts.analytics = analyticsState.managerRows.length;
      if (notify) {
        setInfo('Аналитика обновлена.');
      }
    } catch (error) {
      setError(error);
    } finally {
      loading.analytics = false;
    }
  }

  async function loadAlerts() {
    loading.alerts = true;
    try {
      const response = await bridgeRequest('/admin/alerts/low-stock');
      alertState.threshold = Number(response.threshold || 0);
      alertState.rows = response.rows || [];
      alertState.loaded = true;
      navigationCounts.alerts = alertState.rows.length;
    } catch (error) {
      setError(error);
    } finally {
      loading.alerts = false;
    }
  }

  async function ensureActiveTabLoaded() {
    if (!visibleTabs.value.length) {
      return;
    }
    normalizeActiveTab();
    if (activeTab.value === 'home' && !homeState.loaded) {
      await loadHomeContent();
      return;
    }
    if (activeTab.value === 'products' && !productState.loaded) {
      await loadProducts({ reloadSelected: false });
      if (productState.selectedId) {
        await loadProductDetail(productState.selectedId, { silent: true });
      }
      return;
    }
    if (activeTab.value === 'categories' && !categoryState.loaded) {
      await loadCategories({ reloadSelected: false });
      if (categoryState.selectedId) {
        await loadCategoryDetail(categoryState.selectedId, { silent: true });
      }
      return;
    }
    if (activeTab.value === 'brands' && !brandState.loaded) {
      await loadBrands({ reloadSelected: false });
      if (brandState.selectedId) {
        await loadBrandDetail(brandState.selectedId, { silent: true });
      }
      return;
    }
    if (activeTab.value === 'inventory' && !inventoryState.loaded) {
      await loadInventory();
      return;
    }
    if (activeTab.value === 'orders' && !orderState.loaded) {
      await Promise.all([loadOrders(), activePromotionState.loaded ? Promise.resolve() : loadActivePromotions()]);
      return;
    }
    if (activeTab.value === 'imports' && !importState.loaded) {
      await loadImports();
      return;
    }
    if (activeTab.value === 'promotions' && !promotionState.loaded) {
      await loadPromotions();
      return;
    }
    if (activeTab.value === 'tax' && !taxState.loaded) {
      await loadTaxSettings();
      return;
    }
    if (activeTab.value === 'analytics' && !analyticsState.loaded) {
      await loadAnalytics();
      return;
    }
    if (activeTab.value === 'alerts' && !alertState.loaded) {
      await loadAlerts();
    }
  }

  async function setActiveTab(nextTab) {
    if (nextTab === activeTab.value) {
      setInfo('Этот раздел уже открыт.');
      return;
    }
    if (!canAccessTab(nextTab)) {
      setInfo('Раздел скрыт для вашей роли Directus.');
      return;
    }
    if (!confirmDiscardChanges()) {
      setInfo('Переход в другой раздел отменён.');
      return;
    }
    clearMessages();
    activeTab.value = nextTab;
    await ensureActiveTabLoaded();
  }

  function hydrateProductEditor(item) {
    Object.assign(productForm, {
      id: item.id || '',
      name: item.name || '',
      slug: item.slug || '',
      description: item.description || '',
      brandId: item.brand?.id || '',
      categoryIds: Array.isArray(item.categories) ? item.categories.map((entry) => entry.id) : [],
      isActive: item.isActive !== false,
      specifications: cloneSpecifications(item.specifications),
    });
    productSnapshot.value = serializeProductForm();
    resetVariantEditor({ silent: true });
    resetProductMediaEditor();
  }

  function hydrateCategoryEditor(item) {
    Object.assign(categoryForm, {
      id: item.id || '',
      name: item.name || '',
      slug: item.slug || '',
      description: item.description || '',
      parentId: item.parentId || '',
      position: Number(item.position || 0),
      isActive: item.isActive !== false,
    });
    categorySnapshot.value = serializeCategoryForm();
    categoryImageFile.value = null;
  }

  function hydrateBrandEditor(item) {
    Object.assign(brandForm, {
      id: item.id || '',
      name: item.name || '',
      slug: item.slug || '',
      description: item.description || '',
    });
    brandSnapshot.value = serializeBrandForm();
  }

  function resetProductEditor({ silent = false } = {}) {
    productState.detail = null;
    productState.selectedId = '';
    productState.isCreating = true;
    productState.panel = 'main';
    Object.assign(productForm, {
      id: '',
      name: '',
      slug: '',
      description: '',
      brandId: '',
      categoryIds: [],
      isActive: true,
      specifications: [],
    });
    productSnapshot.value = serializeProductForm();
    resetVariantEditor({ silent: true });
    resetProductMediaEditor();
    if (!silent) {
      setInfo('Форма товара сброшена.');
    }
  }

  function resetVariantEditor({ silent = false } = {}) {
    Object.assign(variantForm, {
      id: '',
      sku: '',
      name: '',
      amount: 0,
      currency: 'RUB',
      stock: 0,
      weightGrossG: null,
      lengthMm: null,
      widthMm: null,
      heightMm: null,
    });
    variantSnapshot.value = serializeVariantForm();
    if (!silent) {
      setInfo('Форма варианта сброшена.');
    }
  }

  function resetProductMediaEditor() {
    productMediaForm.variantId = '';
    productMediaFiles.value = [];
  }

  function resetCategoryEditor({ silent = false } = {}) {
    categoryState.detail = null;
    categoryState.selectedId = '';
    categoryState.isCreating = true;
    Object.assign(categoryForm, {
      id: '',
      name: '',
      slug: '',
      description: '',
      parentId: '',
      position: 0,
      isActive: true,
    });
    categorySnapshot.value = serializeCategoryForm();
    categoryImageFile.value = null;
    if (!silent) {
      setInfo('Форма категории сброшена.');
    }
  }

  function adjustCategoryPosition(delta) {
    const current = Number(categoryForm.position || 0);
    const next = current + Number(delta || 0);
    categoryForm.position = Math.max(0, Number.isFinite(next) ? next : 0);
    setLocalChange();
  }

  function resetBrandEditor({ silent = false } = {}) {
    brandState.detail = null;
    brandState.selectedId = '';
    brandState.isCreating = true;
    Object.assign(brandForm, {
      id: '',
      name: '',
      slug: '',
      description: '',
    });
    brandSnapshot.value = serializeBrandForm();
    if (!silent) {
      setInfo('Форма бренда сброшена.');
    }
  }

  function resetInventoryEditor({ silent = false } = {}) {
    inventoryForm.variantId = inventoryState.selectedVariantId || '';
    inventoryForm.delta = 0;
    inventoryForm.reason = '';
    inventoryForm.idempotencyKey = nextIdempotencyKey();
    if (!silent) {
      setInfo('Форма корректировки остатков сброшена.');
    }
  }

  function hydrateOrderRmaForms(detail) {
    const nextForms = {};
    for (const rma of detail?.rmaRequests || []) {
      const existing = orderState.rmaDecisionForms[rma.id] || {};
      nextForms[rma.id] = {
        status: existing.status || (rma.status === 'REJECTED' ? 'REJECTED' : 'APPROVED'),
        comment: existing.comment ?? rma.managerComment ?? '',
      };
    }
    orderState.rmaDecisionForms = nextForms;
  }

  function hydrateOrderRefundForms(detail) {
    const nextForms = {};
    for (const item of detail?.order?.items || []) {
      const existing = orderState.refundForms[item.id] || {};
      nextForms[item.id] = {
        quantity: existing.quantity ?? 0,
        amount: existing.amount ?? null,
      };
    }
    orderState.refundForms = nextForms;
  }

  function startCreateProduct() {
    if (!confirmDiscardChanges()) {
      setInfo('Создание товара отменено: есть несохранённые изменения.');
      return;
    }
    clearMessages();
    resetProductEditor({ silent: true });
    setInfo('Открыта форма нового товара.');
  }

  function startCreateCategory() {
    if (!confirmDiscardChanges()) {
      setInfo('Создание категории отменено: есть несохранённые изменения.');
      return;
    }
    clearMessages();
    resetCategoryEditor({ silent: true });
    setInfo('Открыта форма новой категории.');
  }

  function startCreateBrand() {
    if (!confirmDiscardChanges()) {
      setInfo('Создание бренда отменено: есть несохранённые изменения.');
      return;
    }
    clearMessages();
    resetBrandEditor({ silent: true });
    setInfo('Открыта форма нового бренда.');
  }

  async function selectProduct(id) {
    if (id === productState.selectedId && !productState.isCreating) {
      setInfo('Этот товар уже открыт.');
      return;
    }
    if (!confirmDiscardChanges()) {
      setInfo('Переход к товару отменён: есть несохранённые изменения.');
      return;
    }
    clearMessages();
    productState.panel = 'main';
    await loadProductDetail(id);
  }

  async function selectCategory(id) {
    if (id === categoryState.selectedId && !categoryState.isCreating) {
      setInfo('Эта категория уже открыта.');
      return;
    }
    if (!confirmDiscardChanges()) {
      setInfo('Переход к категории отменён: есть несохранённые изменения.');
      return;
    }
    clearMessages();
    await loadCategoryDetail(id);
  }

  async function selectBrand(id) {
    if (id === brandState.selectedId && !brandState.isCreating) {
      setInfo('Этот бренд уже открыт.');
      return;
    }
    if (!confirmDiscardChanges()) {
      setInfo('Переход к бренду отменён: есть несохранённые изменения.');
      return;
    }
    clearMessages();
    await loadBrandDetail(id);
  }

  function selectInventoryRow(variantId) {
    if (variantId === inventoryState.selectedVariantId) {
      setInfo('Этот вариант уже выбран для корректировки.');
      return;
    }
    inventoryState.selectedVariantId = variantId;
    inventoryForm.variantId = variantId;
    inventoryForm.delta = 0;
    inventoryForm.reason = '';
    inventoryForm.idempotencyKey = nextIdempotencyKey();
    setInfo('Вариант выбран для корректировки остатков.');
  }

  function closeActiveDetail() {
    if (!confirmDiscardChanges()) {
      setInfo('Закрытие карточки отменено.');
      return;
    }
    clearMessages();
    if (activeTab.value === 'products') {
      productState.selectedId = '';
      productState.detail = null;
      productState.isCreating = false;
      productState.panel = 'main';
      Object.assign(productForm, {
        id: '',
        name: '',
        slug: '',
        description: '',
        brandId: '',
        categoryIds: [],
        isActive: true,
        specifications: [],
      });
      productSnapshot.value = serializeProductForm();
      resetVariantEditor({ silent: true });
      resetProductMediaEditor();
      setInfo('Карточка товара закрыта.');
      return;
    }
    if (activeTab.value === 'categories') {
      categoryState.selectedId = '';
      categoryState.detail = null;
      categoryState.isCreating = false;
      Object.assign(categoryForm, {
        id: '',
        name: '',
        slug: '',
        description: '',
        parentId: '',
        position: 0,
        isActive: true,
      });
      categorySnapshot.value = serializeCategoryForm();
      categoryImageFile.value = null;
      setInfo('Карточка категории закрыта.');
      return;
    }
    if (activeTab.value === 'brands') {
      brandState.selectedId = '';
      brandState.detail = null;
      brandState.isCreating = false;
      Object.assign(brandForm, {
        id: '',
        name: '',
        slug: '',
        description: '',
      });
      brandSnapshot.value = serializeBrandForm();
      setInfo('Карточка бренда закрыта.');
      return;
    }
    if (activeTab.value === 'orders') {
      orderState.selectedId = '';
      orderState.selectedFilteredOut = false;
      orderState.detail = null;
      orderState.nextStatus = '';
      orderState.note = '';
      orderState.rmaReason = '';
      orderState.rmaDesiredResolution = '';
      orderState.rmaDecisionForms = {};
      orderState.refundForms = {};
      setInfo('Карточка заказа закрыта.');
      return;
    }
    if (activeTab.value === 'promotions') {
      promotionState.selectedId = '';
      promotionState.selectedPromoCodeId = '';
      promotionState.isCreating = false;
      promotionState.promoCodeCreating = false;
      setInfo('Карточка акции закрыта.');
      return;
    }
    if (activeTab.value === 'tax') {
      taxState.selectedId = '';
      taxState.isCreating = false;
      setInfo('Карточка налогового режима закрыта.');
      return;
    }
    inventoryState.selectedVariantId = '';
    resetInventoryEditor({ silent: true });
    setInfo('Детальная панель закрыта.');
  }

  function setProductPanel(panel) {
    if (productState.panel === panel) {
      setInfo('Этот раздел товара уже открыт.');
      return;
    }
    if ((panel !== 'variants' && variantDirty.value) && !window.confirm('Есть несохранённые изменения варианта. Отбросить их?')) {
      setInfo('Переход между разделами товара отменён.');
      return;
    }
    productState.panel = panel;
    setInfo('Раздел товара открыт.');
  }

  function loadVariantEditor(variant) {
    if (!variant?.id) {
      setInfo('Вариант не выбран.');
      return;
    }
    Object.assign(variantForm, {
      id: variant.id || '',
      sku: variant.sku || '',
      name: variant.name || '',
      amount: Number(variant.price?.amount || 0),
      currency: variant.price?.currency || 'RUB',
      stock: Number(variant.stock || 0),
      weightGrossG: variant.weightGrossG ?? null,
      lengthMm: variant.lengthMm ?? null,
      widthMm: variant.widthMm ?? null,
      heightMm: variant.heightMm ?? null,
    });
    variantSnapshot.value = serializeVariantForm();
    setInfo('Вариант открыт для редактирования.');
  }

  function serializeProductForm() {
    return JSON.stringify({
      id: productForm.id || '',
      name: (productForm.name || '').trim(),
      slug: (productForm.slug || '').trim(),
      description: (productForm.description || '').trim(),
      brandId: productForm.brandId || '',
      categoryIds: [...(productForm.categoryIds || [])].sort(),
      isActive: Boolean(productForm.isActive),
      specifications: normalizeSpecificationsForPayload(productForm.specifications),
    });
  }

  function serializeVariantForm() {
    return JSON.stringify({
      id: variantForm.id || '',
      sku: (variantForm.sku || '').trim(),
      name: (variantForm.name || '').trim(),
      amount: Number(variantForm.amount || 0),
      currency: (variantForm.currency || '').trim().toUpperCase(),
      stock: Number(variantForm.stock || 0),
      weightGrossG: normalizeNullableNumber(variantForm.weightGrossG),
      lengthMm: normalizeNullableNumber(variantForm.lengthMm),
      widthMm: normalizeNullableNumber(variantForm.widthMm),
      heightMm: normalizeNullableNumber(variantForm.heightMm),
    });
  }

  function serializeCategoryForm() {
    return JSON.stringify({
      id: categoryForm.id || '',
      name: (categoryForm.name || '').trim(),
      slug: (categoryForm.slug || '').trim(),
      description: (categoryForm.description || '').trim(),
      parentId: categoryForm.parentId || '',
      position: Number(categoryForm.position || 0),
      isActive: Boolean(categoryForm.isActive),
    });
  }

  function serializeBrandForm() {
    return JSON.stringify({
      id: brandForm.id || '',
      name: (brandForm.name || '').trim(),
      slug: (brandForm.slug || '').trim(),
      description: (brandForm.description || '').trim(),
    });
  }

  function syncEditorSnapshots() {
    productSnapshot.value = serializeProductForm();
    variantSnapshot.value = serializeVariantForm();
    categorySnapshot.value = serializeCategoryForm();
    brandSnapshot.value = serializeBrandForm();
  }

  function confirmDiscardChanges() {
    if (!hasUnsavedChanges.value) {
      return true;
    }
    return window.confirm('Есть несохранённые изменения. Отбросить их и продолжить?');
  }

  function validateProductForm() {
    if (!productForm.name.trim()) return 'Укажите название товара.';
    if (!productForm.slug.trim()) return 'Укажите слаг товара.';
    const incompleteSection = (productForm.specifications || []).find((section) =>
      (section.items || []).some((item) => Boolean(String(item?.label || '').trim()) !== Boolean(String(item?.value || '').trim()))
    );
    if (incompleteSection) {
      return 'У каждого параметра характеристики должны быть заполнены и название, и значение.';
    }
    return null;
  }

  function validateVariantForm() {
    if (!variantForm.sku.trim()) return 'Укажите SKU варианта.';
    if (!variantForm.name.trim()) return 'Укажите название варианта.';
    if (!String(variantForm.currency || '').trim()) return 'Укажите валюту варианта.';
    return null;
  }

  function validateCategoryForm() {
    if (!categoryForm.name.trim()) return 'Укажите название категории.';
    if (!categoryForm.slug.trim()) return 'Укажите слаг категории.';
    if (categoryForm.parentId && categoryForm.parentId === categoryForm.id) {
      return 'Категория не может быть родителем самой себе.';
    }
    return null;
  }

  function validateBrandForm() {
    if (!brandForm.name.trim()) return 'Укажите название бренда.';
    if (!brandForm.slug.trim()) return 'Укажите слаг бренда.';
    return null;
  }

  function validateInventoryForm() {
    if (!inventoryForm.variantId) return 'Сначала выберите вариант.';
    if (!inventoryForm.idempotencyKey.trim()) return 'Укажите ключ идемпотентности.';
    return null;
  }

  async function submitProduct() {
    const validationError = validateProductForm();
    if (validationError) {
      pageError.value = validationError;
      return;
    }

    isSubmitting.value = true;
    clearMessages();
    try {
      const payload = {
        name: productForm.name.trim(),
        slug: productForm.slug.trim(),
        description: normalizeNullableText(productForm.description),
        brand: productBrandOptions.value.find((option) => option.id === productForm.brandId)?.slug || null,
        categories: productCategoryOptions.value
          .filter((option) => (productForm.categoryIds || []).includes(option.id))
          .map((option) => option.slug),
        isActive: Boolean(productForm.isActive),
        specifications: normalizeSpecificationsForPayload(productForm.specifications),
      };
      const path = productForm.id ? `/products/${productForm.id}` : '/products';
      const method = productForm.id ? 'PUT' : 'POST';
      const saved = await bridgeRequest(path, { method, data: payload });
      await loadProducts({ reloadSelected: false });
      await loadNavigationSummary();
      await loadProductDetail(saved.id, { silent: true });
      setSuccess(productForm.id ? 'Товар сохранён.' : 'Товар создан.');
    } catch (error) {
      setError(error);
    } finally {
      isSubmitting.value = false;
    }
  }

  async function deleteProduct() {
    if (!productForm.id) {
      setInfo('Товар не выбран.');
      return;
    }
    if (!window.confirm(`Удалить товар «${productForm.name || productForm.slug}»?`)) {
      setInfo('Удаление товара отменено.');
      return;
    }

    isSubmitting.value = true;
    clearMessages();
    try {
      await bridgeRequest(`/products/${productForm.id}`, { method: 'DELETE' });
      await loadProducts({ reloadSelected: false });
      await loadNavigationSummary();
      productState.selectedId = '';
      productState.detail = null;
      productState.isCreating = false;
      resetVariantEditor({ silent: true });
      resetProductMediaEditor();
      setSuccess('Товар удалён.');
    } catch (error) {
      setError(error);
    } finally {
      isSubmitting.value = false;
    }
  }

  async function submitVariant() {
    const validationError = validateVariantForm();
    if (validationError) {
      pageError.value = validationError;
      return;
    }
    if (!productForm.id) {
      pageError.value = 'Сначала сохраните товар.';
      return;
    }

    isSubmitting.value = true;
    clearMessages();
    try {
      const payload = {
        sku: variantForm.sku.trim(),
        name: variantForm.name.trim(),
        amount: Number(variantForm.amount || 0),
        currency: String(variantForm.currency || 'RUB').trim().toUpperCase(),
        stock: Number(variantForm.stock || 0),
        weightGrossG: normalizeNullableNumber(variantForm.weightGrossG),
        lengthMm: normalizeNullableNumber(variantForm.lengthMm),
        widthMm: normalizeNullableNumber(variantForm.widthMm),
        heightMm: normalizeNullableNumber(variantForm.heightMm),
      };
      const path = variantForm.id
        ? `/products/${productForm.id}/variants/${variantForm.id}`
        : `/products/${productForm.id}/variants`;
      const method = variantForm.id ? 'PUT' : 'POST';
      await bridgeRequest(path, { method, data: payload });
      await loadProducts({ reloadSelected: false });
      await loadNavigationSummary();
      await loadProductDetail(productForm.id, { silent: true });
      resetVariantEditor({ silent: true });
      setSuccess(variantForm.id ? 'Вариант сохранён.' : 'Вариант добавлен.');
    } catch (error) {
      setError(error);
    } finally {
      isSubmitting.value = false;
    }
  }

  async function deleteVariant(variant) {
    if (!productForm.id || !variant?.id) {
      setInfo(productForm.id ? 'Вариант не выбран.' : 'Сначала сохраните товар.');
      return;
    }
    if (!window.confirm(`Удалить вариант «${variant.name || variant.sku}»?`)) {
      setInfo('Удаление варианта отменено.');
      return;
    }

    isSubmitting.value = true;
    clearMessages();
    try {
      await bridgeRequest(`/products/${productForm.id}/variants/${variant.id}`, { method: 'DELETE' });
      await loadProducts({ reloadSelected: false });
      await loadNavigationSummary();
      await loadProductDetail(productForm.id, { silent: true });
      resetVariantEditor({ silent: true });
      setSuccess('Вариант удалён.');
    } catch (error) {
      setError(error);
    } finally {
      isSubmitting.value = false;
    }
  }

  async function submitCategory() {
    const validationError = validateCategoryForm();
    if (validationError) {
      pageError.value = validationError;
      return;
    }

    isSubmitting.value = true;
    clearMessages();
    try {
      const payload = {
        name: categoryForm.name.trim(),
        slug: categoryForm.slug.trim(),
        description: normalizeNullableText(categoryForm.description),
        parentId: categoryForm.parentId || null,
        position: Number(categoryForm.position || 0),
        isActive: Boolean(categoryForm.isActive),
      };
      const path = categoryForm.id ? `/categories/${categoryForm.id}` : '/categories';
      const method = categoryForm.id ? 'PUT' : 'POST';
      const saved = await bridgeRequest(path, { method, data: payload });
      await loadCategories({ reloadSelected: false });
      await loadNavigationSummary();
      await loadCategoryDetail(saved.id, { silent: true });
      setSuccess(categoryForm.id ? 'Категория сохранена.' : 'Категория создана.');
    } catch (error) {
      setError(error);
    } finally {
      isSubmitting.value = false;
    }
  }

  async function deleteCategory() {
    if (!categoryForm.id) {
      setInfo('Категория не выбрана.');
      return;
    }
    if (!window.confirm(`Удалить категорию «${categoryForm.name || categoryForm.slug}»?`)) {
      setInfo('Удаление категории отменено.');
      return;
    }

    isSubmitting.value = true;
    clearMessages();
    try {
      await bridgeRequest(`/categories/${categoryForm.id}`, { method: 'DELETE' });
      await loadCategories({ reloadSelected: false });
      await loadNavigationSummary();
      categoryState.selectedId = '';
      categoryState.detail = null;
      categoryState.isCreating = false;
      categoryImageFile.value = null;
      setSuccess('Категория удалена.');
    } catch (error) {
      setError(error);
    } finally {
      isSubmitting.value = false;
    }
  }

  async function submitBrand() {
    const validationError = validateBrandForm();
    if (validationError) {
      pageError.value = validationError;
      return;
    }

    isSubmitting.value = true;
    clearMessages();
    try {
      const payload = {
        name: brandForm.name.trim(),
        slug: brandForm.slug.trim(),
        description: normalizeNullableText(brandForm.description),
      };
      const path = brandForm.id ? `/brands/${brandForm.id}` : '/brands';
      const method = brandForm.id ? 'PUT' : 'POST';
      const saved = await bridgeRequest(path, { method, data: payload });
      await loadBrands({ reloadSelected: false });
      await loadNavigationSummary();
      await loadBrandDetail(saved.id, { silent: true });
      setSuccess(brandForm.id ? 'Бренд сохранён.' : 'Бренд создан.');
    } catch (error) {
      setError(error);
    } finally {
      isSubmitting.value = false;
    }
  }

  async function deleteBrand() {
    if (!brandForm.id) {
      setInfo('Бренд не выбран.');
      return;
    }
    if (!window.confirm(`Удалить бренд «${brandForm.name || brandForm.slug}»?`)) {
      setInfo('Удаление бренда отменено.');
      return;
    }

    isSubmitting.value = true;
    clearMessages();
    try {
      await bridgeRequest(`/brands/${brandForm.id}`, { method: 'DELETE' });
      await loadBrands({ reloadSelected: false });
      await loadNavigationSummary();
      brandState.selectedId = '';
      brandState.detail = null;
      brandState.isCreating = false;
      setSuccess('Бренд удалён.');
    } catch (error) {
      setError(error);
    } finally {
      isSubmitting.value = false;
    }
  }

  async function submitInventoryAdjustment() {
    const validationError = validateInventoryForm();
    if (validationError) {
      pageError.value = validationError;
      return;
    }

    isSubmitting.value = true;
    clearMessages();
    try {
      const response = await bridgeRequest('/inventory/adjust', {
        method: 'POST',
        params: { idempotencyKey: inventoryForm.idempotencyKey.trim() },
        data: {
          variantId: inventoryForm.variantId,
          delta: Number(inventoryForm.delta || 0),
          reason: normalizeNullableText(inventoryForm.reason),
        },
      });
      await loadInventory();
      if (productState.selectedId) {
        await loadProductDetail(productState.selectedId, { silent: true });
      }
      inventoryForm.delta = 0;
      inventoryForm.reason = '';
      inventoryForm.idempotencyKey = nextIdempotencyKey();
      setSuccess(response?.applied === false ? 'Повторный запрос распознан, остаток не изменён повторно.' : 'Корректировка применена.');
    } catch (error) {
      setError(error);
    } finally {
      isSubmitting.value = false;
    }
  }

  async function selectOrder(id) {
    if (id === orderState.selectedId && orderState.detail) {
      setInfo('Этот заказ уже открыт.');
      return;
    }
    await loadOrderDetail(id);
  }

  async function refundSelectedOrder({ full = false } = {}) {
    if (!orderState.selectedId || !canRefundSelectedOrder.value) {
      pageError.value = 'Возврат оплаты недоступен для этого заказа.';
      return;
    }
    const items = full ? [] : selectedRefundLines();
    if (!full && !items.length) {
      pageError.value = 'Укажите хотя бы одну строку и количество для частичного возврата.';
      return;
    }
    isSubmitting.value = true;
    clearMessages();
    try {
      const response = await bridgeRequest(`/admin/orders/${orderState.selectedId}/refunds`, {
        method: 'POST',
        data: { items },
      });
      orderState.detail = response;
      hydrateOrderRmaForms(response);
      hydrateOrderRefundForms(response);
      await loadOrders();
      setSuccess(full ? 'Создан возврат оставшейся суммы.' : 'Создан частичный возврат.');
    } catch (error) {
      setError(error);
    } finally {
      isSubmitting.value = false;
    }
  }

  function selectedRefundLines() {
    return Object.entries(orderState.refundForms || {})
      .map(([orderItemId, form]) => {
        const quantity = normalizeNullableNumber(form?.quantity);
        const amount = majorToMinor(form?.amount);
        if (!quantity || quantity <= 0) {
          return null;
        }
        return {
          orderItemId,
          quantity,
          amount,
        };
      })
      .filter(Boolean);
  }

  async function createRmaRequest() {
    if (!orderState.selectedId || !canManageSelectedOrderRma.value) {
      pageError.value = 'RMA для этого заказа недоступен.';
      return;
    }
    isSubmitting.value = true;
    clearMessages();
    try {
      await bridgeRequest('/admin/rma-requests', {
        method: 'POST',
        data: {
          orderId: orderState.selectedId,
          reason: normalizeNullableText(orderState.rmaReason),
          desiredResolution: normalizeNullableText(orderState.rmaDesiredResolution),
        },
      });
      orderState.rmaReason = '';
      orderState.rmaDesiredResolution = '';
      await loadOrderDetail(orderState.selectedId, { silent: true });
      setSuccess('RMA запрос создан.');
    } catch (error) {
      setError(error);
    } finally {
      isSubmitting.value = false;
    }
  }

  async function decideRmaRequest(rma) {
    const form = orderState.rmaDecisionForms[rma?.id];
    if (!rma?.id || !form?.status) {
      pageError.value = 'Выберите решение по RMA.';
      return;
    }
    if (!canDecideSelectedOrderRma.value) {
      pageError.value = 'Решение по RMA недоступно для текущей роли.';
      return;
    }
    isSubmitting.value = true;
    clearMessages();
    try {
      await bridgeRequest(`/admin/rma-requests/${rma.id}/decision`, {
        method: 'POST',
        data: {
          status: form.status,
          comment: normalizeNullableText(form.comment),
        },
      });
      await loadOrderDetail(orderState.selectedId, { silent: true });
      setSuccess('Решение по RMA сохранено.');
    } catch (error) {
      setError(error);
    } finally {
      isSubmitting.value = false;
    }
  }

  async function submitOrderStatus() {
    if (!orderState.selectedId || !orderState.nextStatus) {
      pageError.value = 'Выберите заказ и статус.';
      return;
    }
    if (!canSubmitSelectedOrderStatus.value) {
      pageError.value = 'Для текущей роли изменение статуса этого заказа недоступно.';
      return;
    }
    isSubmitting.value = true;
    clearMessages();
    try {
      const response = await bridgeRequest(`/admin/orders/${orderState.selectedId}/status`, {
        method: 'POST',
        data: {
          status: orderState.nextStatus,
          note: normalizeNullableText(orderState.note),
        },
      });
      orderState.detail = response;
      orderState.note = '';
      hydrateOrderRmaForms(response);
      hydrateOrderRefundForms(response);
      await loadOrders();
      setSuccess('Статус заказа обновлён.');
    } catch (error) {
      setError(error);
    } finally {
      isSubmitting.value = false;
    }
  }

  async function claimOrder() {
    if (!orderState.selectedId) {
      pageError.value = 'Выберите заказ.';
      return;
    }
    if (!canClaimSelectedOrder.value) {
      pageError.value = 'Этот заказ уже взят в работу или недоступен для вашей роли.';
      return;
    }
    isSubmitting.value = true;
    clearMessages();
    try {
      orderState.detail = await bridgeRequest(`/admin/orders/${orderState.selectedId}/claim`, { method: 'POST' });
      hydrateOrderRmaForms(orderState.detail);
      hydrateOrderRefundForms(orderState.detail);
      await loadOrders();
      setSuccess('Заказ отмечен как взятый в работу.');
    } catch (error) {
      setError(error);
    } finally {
      isSubmitting.value = false;
    }
  }

  async function clearOrderClaim() {
    if (!orderState.selectedId || !canClearSelectedOrder.value) {
      pageError.value = orderState.selectedId ? 'Снять менеджера с этого заказа недоступно.' : 'Выберите заказ.';
      return;
    }
    isSubmitting.value = true;
    clearMessages();
    try {
      orderState.detail = await bridgeRequest(`/admin/orders/${orderState.selectedId}/unclaim`, { method: 'POST' });
      hydrateOrderRmaForms(orderState.detail);
      hydrateOrderRefundForms(orderState.detail);
      await loadOrders();
      setSuccess('Менеджер снят с заказа.');
    } catch (error) {
      setError(error);
    } finally {
      isSubmitting.value = false;
    }
  }

  async function archiveOrder() {
    const order = selectedOrder.value;
    if (!orderState.selectedId || !canArchiveSelectedOrder.value || !order) {
      pageError.value = orderState.selectedId ? 'Удаление этого заказа недоступно.' : 'Выберите заказ.';
      return;
    }
    if (!window.confirm(`Удалить заказ «${order.receiptEmail || order.id}»? Заказ будет сохранён в архиве.`)) {
      setInfo('Удаление заказа отменено.');
      return;
    }
    const reason = window.prompt('Причина удаления', 'Удалён администратором в Directus') || 'Удалён администратором в Directus';
    isSubmitting.value = true;
    clearMessages();
    try {
      orderState.detail = await bridgeRequest(`/admin/orders/${orderState.selectedId}`, {
        method: 'DELETE',
        data: { reason: normalizeNullableText(reason) },
      });
      hydrateOrderRmaForms(orderState.detail);
      hydrateOrderRefundForms(orderState.detail);
      await loadOrders();
      setSuccess('Заказ перемещён в архив.');
    } catch (error) {
      setError(error);
    } finally {
      isSubmitting.value = false;
    }
  }

  async function restoreOrder() {
    if (!orderState.selectedId || !canRestoreSelectedOrder.value) {
      pageError.value = orderState.selectedId ? 'Восстановление этого заказа недоступно.' : 'Выберите заказ.';
      return;
    }
    isSubmitting.value = true;
    clearMessages();
    try {
      orderState.detail = await bridgeRequest(`/admin/orders/${orderState.selectedId}/restore`, { method: 'POST' });
      hydrateOrderRmaForms(orderState.detail);
      hydrateOrderRefundForms(orderState.detail);
      await loadOrders();
      setSuccess('Заказ восстановлен из архива.');
    } catch (error) {
      setError(error);
    } finally {
      isSubmitting.value = false;
    }
  }

  async function resetOrderFiltersForSelected() {
    orderState.query = '';
    orderState.status = '';
    orderState.manager = '';
    orderState.from = '';
    orderState.to = '';
    orderState.archived = 'all';
    await loadOrders();
    setInfo('Фильтры заказов сброшены.');
  }

  function selectImportJob(jobId) {
    if (importState.selectedJobId === jobId) {
      setInfo('Этот импорт уже выбран.');
      return;
    }
    importState.selectedJobId = jobId;
    const job = importState.jobs.find((entry) => entry.id === jobId);
    if (job && importState.dryRun?.job?.id !== jobId) {
      setInfo(`Выбран импорт ${job.fileName || job.id}. Повторное применение доступно из последнего dry-run.`);
    } else if (job) {
      setInfo(`Выбран импорт ${job.fileName || job.id}.`);
    } else {
      setInfo('Импорт не найден в текущем списке.');
    }
  }

  function onImportFileSelected(event) {
    importState.file = event?.target?.files?.[0] || null;
    importState.dryRun = null;
  }

  async function dryRunImport() {
    if (!importState.file) {
      pageError.value = 'Выберите файл для импорта.';
      return;
    }
    const formData = new FormData();
    formData.append('file', importState.file);
    formData.append('mapping', JSON.stringify(importState.mapping));

    isSubmitting.value = true;
    clearMessages();
    try {
      importState.dryRun = await bridgeRequest('/admin/imports/catalogue/dry-run', {
        method: 'POST',
        data: formData,
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      importState.selectedJobId = importState.dryRun?.job?.id || '';
      await loadImports();
      setSuccess('Dry-run импорта завершён.');
    } catch (error) {
      setError(error);
    } finally {
      isSubmitting.value = false;
    }
  }

  async function commitImport() {
    const jobId = importState.dryRun?.job?.id || importState.selectedJobId;
    if (!jobId) {
      pageError.value = 'Сначала выполните dry-run.';
      return;
    }
    isSubmitting.value = true;
    clearMessages();
    try {
      await bridgeRequest('/admin/imports/catalogue/commit', {
        method: 'POST',
        data: { jobId },
      });
      await Promise.allSettled([loadImports(), loadProducts({ reloadSelected: true }), loadInventory()]);
      setSuccess('Импорт применён к каталогу и остаткам.');
    } catch (error) {
      setError(error);
    } finally {
      isSubmitting.value = false;
    }
  }

  function startCreatePromotion({ silent = false } = {}) {
    promotionState.mode = 'promotion';
    promotionState.selectedId = '';
    promotionState.selectedPromoCodeId = '';
    promotionState.isCreating = true;
    promotionState.promoCodeCreating = false;
    Object.assign(promotionForm, {
      id: '',
      name: '',
      type: 'PRODUCT_SALE',
      status: 'ACTIVE',
      startsAt: '',
      endsAt: '',
      discountPercent: null,
      discountAmount: null,
      salePriceAmount: null,
      currency: 'RUB',
      thresholdAmount: null,
      description: '',
      targets: [],
    });
    if (!silent) {
      setInfo('Открыта форма новой акции.');
    }
  }

  function selectPromotion(promotion) {
    if (promotionState.mode === 'promotion' && promotionState.selectedId === promotion?.id && !promotionState.isCreating) {
      setInfo('Эта акция уже открыта.');
      return;
    }
    promotionState.mode = 'promotion';
    promotionState.selectedId = promotion.id;
    promotionState.selectedPromoCodeId = '';
    promotionState.isCreating = false;
    promotionState.promoCodeCreating = false;
    Object.assign(promotionForm, {
      id: promotion.id || '',
      name: promotion.name || '',
      type: promotion.type || 'PRODUCT_SALE',
      status: promotion.status || 'ACTIVE',
      startsAt: toDatetimeLocal(promotion.startsAt),
      endsAt: toDatetimeLocal(promotion.endsAt),
      discountPercent: promotion.discountPercent ?? null,
      discountAmount: minorToMajor(promotion.discountAmount),
      salePriceAmount: minorToMajor(promotion.salePriceAmount),
      currency: promotion.currency || 'RUB',
      thresholdAmount: null,
      description: promotion.description || '',
      targets: normalizePromotionTargets(promotion.targets),
    });
    setInfo('Акция открыта для редактирования.');
  }

  function startCreatePromoCode({ silent = false } = {}) {
    promotionState.mode = 'promoCode';
    promotionState.selectedPromoCodeId = '';
    promotionState.selectedId = '';
    promotionState.isCreating = false;
    promotionState.promoCodeCreating = true;
    Object.assign(promoCodeForm, {
      id: '',
      code: '',
      status: 'ACTIVE',
      discountPercent: null,
      discountAmount: null,
      thresholdAmount: null,
      startsAt: '',
      endsAt: '',
      maxRedemptions: null,
      description: '',
    });
    if (!silent) {
      setInfo('Открыта форма нового промокода.');
    }
  }

  function selectPromoCode(promoCode) {
    if (promotionState.mode === 'promoCode' && promotionState.selectedPromoCodeId === promoCode?.id && !promotionState.promoCodeCreating) {
      setInfo('Этот промокод уже открыт.');
      return;
    }
    promotionState.mode = 'promoCode';
    promotionState.selectedPromoCodeId = promoCode.id;
    promotionState.selectedId = '';
    promotionState.isCreating = false;
    promotionState.promoCodeCreating = false;
    Object.assign(promoCodeForm, {
      id: promoCode.id || '',
      code: promoCode.code || '',
      status: promoCode.status || 'ACTIVE',
      discountPercent: promoCode.discountPercent ?? null,
      discountAmount: minorToMajor(promoCode.discountAmount),
      thresholdAmount: minorToMajor(promoCode.thresholdAmount),
      startsAt: toDatetimeLocal(promoCode.startsAt),
      endsAt: toDatetimeLocal(promoCode.endsAt),
      maxRedemptions: promoCode.maxRedemptions ?? null,
      description: promoCode.description || '',
    });
    setInfo('Промокод открыт для редактирования.');
  }

  function normalizePromotionTargets(targets = []) {
    return (Array.isArray(targets) ? targets : [])
      .map((target) => ({
        targetKind: target?.targetKind || 'VARIANT',
        targetKey: target?.targetKey || '',
      }))
      .filter((target) => target.targetKind && target.targetKey);
  }

  function addPromotionTarget() {
    promotionForm.targets.push({ targetKind: 'VARIANT', targetKey: '' });
    setLocalChange();
  }

  function removePromotionTarget(index) {
    if (index < 0 || index >= promotionForm.targets.length) {
      setInfo('Цель акции не выбрана.');
      return;
    }
    promotionForm.targets.splice(index, 1);
    setLocalChange();
  }

  async function submitPromotion() {
    if (!promotionForm.name.trim()) {
      pageError.value = 'Укажите название акции.';
      return;
    }
    isSubmitting.value = true;
    clearMessages();
    try {
      const targets = normalizePromotionTargets(promotionForm.targets);
      const payload = {
        name: promotionForm.name.trim(),
        type: 'PRODUCT_SALE',
        status: promotionForm.status,
        startsAt: toIsoDateTime(promotionForm.startsAt),
        endsAt: toIsoDateTime(promotionForm.endsAt),
        discountPercent: normalizeNullableNumber(promotionForm.discountPercent),
        discountAmount: majorToMinor(promotionForm.discountAmount),
        salePriceAmount: majorToMinor(promotionForm.salePriceAmount),
        currency: promotionForm.currency || 'RUB',
        thresholdAmount: null,
        description: normalizeNullableText(promotionForm.description),
        targets,
      };
      const path = promotionForm.id ? `/admin/promotions/${promotionForm.id}` : '/admin/promotions';
      const method = promotionForm.id ? 'PUT' : 'POST';
      const saved = await bridgeRequest(path, { method, data: payload });
      await loadPromotions();
      selectPromotion(saved);
      setSuccess(promotionForm.id ? 'Акция сохранена.' : 'Акция создана.');
    } catch (error) {
      setError(error);
    } finally {
      isSubmitting.value = false;
    }
  }

  async function deletePromotion() {
    if (!promotionForm.id || !window.confirm(`Удалить акцию «${promotionForm.name}»?`)) {
      setInfo(promotionForm.id ? 'Удаление акции отменено.' : 'Акция не выбрана.');
      return;
    }
    isSubmitting.value = true;
    clearMessages();
    try {
      await bridgeRequest(`/admin/promotions/${promotionForm.id}`, { method: 'DELETE' });
      await loadPromotions();
      startCreatePromotion({ silent: true });
      setSuccess('Акция удалена.');
    } catch (error) {
      setError(error);
    } finally {
      isSubmitting.value = false;
    }
  }

  async function submitPromoCode() {
    if (!promoCodeForm.code.trim()) {
      pageError.value = 'Укажите промокод.';
      return;
    }
    isSubmitting.value = true;
    clearMessages();
    try {
      const payload = {
        code: promoCodeForm.code.trim(),
        status: promoCodeForm.status,
        discountPercent: normalizeNullableNumber(promoCodeForm.discountPercent),
        discountAmount: majorToMinor(promoCodeForm.discountAmount),
        thresholdAmount: majorToMinor(promoCodeForm.thresholdAmount),
        startsAt: toIsoDateTime(promoCodeForm.startsAt),
        endsAt: toIsoDateTime(promoCodeForm.endsAt),
        maxRedemptions: normalizeNullableNumber(promoCodeForm.maxRedemptions),
        description: normalizeNullableText(promoCodeForm.description),
      };
      const path = promoCodeForm.id ? `/admin/promo-codes/${promoCodeForm.id}` : '/admin/promo-codes';
      const method = promoCodeForm.id ? 'PUT' : 'POST';
      const saved = await bridgeRequest(path, { method, data: payload });
      await loadPromotions();
      selectPromoCode(saved);
      setSuccess(promoCodeForm.id ? 'Промокод сохранён.' : 'Промокод создан.');
    } catch (error) {
      setError(error);
    } finally {
      isSubmitting.value = false;
    }
  }

  async function deletePromoCode() {
    if (!promoCodeForm.id || !window.confirm(`Удалить промокод «${promoCodeForm.code}»?`)) {
      setInfo(promoCodeForm.id ? 'Удаление промокода отменено.' : 'Промокод не выбран.');
      return;
    }
    isSubmitting.value = true;
    clearMessages();
    try {
      await bridgeRequest(`/admin/promo-codes/${promoCodeForm.id}`, { method: 'DELETE' });
      await loadPromotions();
      startCreatePromoCode({ silent: true });
      setSuccess('Промокод удалён.');
    } catch (error) {
      setError(error);
    } finally {
      isSubmitting.value = false;
    }
  }

  function startCreateTax({ silent = false } = {}) {
    taxState.selectedId = '';
    taxState.isCreating = true;
    Object.assign(taxForm, {
      id: '',
      name: '',
      status: 'ACTIVE',
      taxSystemCode: 1,
      vatCode: 1,
      vatRatePercent: 0,
      active: false,
    });
    if (!silent) {
      setInfo('Открыта форма нового налогового режима.');
    }
  }

  function selectTax(tax, { silent = false } = {}) {
    if (taxState.selectedId === tax?.id && !taxState.isCreating) {
      if (!silent) {
        setInfo('Этот налоговый режим уже открыт.');
      }
      return;
    }
    taxState.selectedId = tax.id;
    taxState.isCreating = false;
    Object.assign(taxForm, {
      id: tax.id || '',
      name: tax.name || '',
      status: tax.status || 'ACTIVE',
      taxSystemCode: Number(tax.taxSystemCode || 1),
      vatCode: Number(tax.vatCode || 1),
      vatRatePercent: Number(tax.vatRatePercent || 0),
      active: Boolean(tax.active),
    });
    if (!silent) {
      setInfo('Налоговый режим открыт для редактирования.');
    }
  }

  async function submitTax() {
    if (!taxForm.name.trim()) {
      pageError.value = 'Укажите название налогового режима.';
      return;
    }
    isSubmitting.value = true;
    clearMessages();
    try {
      const payload = {
        name: taxForm.name.trim(),
        status: taxForm.status,
        taxSystemCode: Number(taxForm.taxSystemCode || 1),
        vatCode: Number(taxForm.vatCode || 1),
        vatRatePercent: Number(taxForm.vatRatePercent || 0),
        active: Boolean(taxForm.active),
      };
      const path = taxForm.id ? `/admin/tax-settings/${taxForm.id}` : '/admin/tax-settings';
      const method = taxForm.id ? 'PUT' : 'POST';
      const saved = await bridgeRequest(path, { method, data: payload });
      await loadTaxSettings();
      selectTax(saved, { silent: true });
      setSuccess(taxForm.id ? 'Налоговый режим сохранён.' : 'Налоговый режим создан.');
    } catch (error) {
      setError(error);
    } finally {
      isSubmitting.value = false;
    }
  }

  async function deleteTax() {
    if (!taxForm.id || !window.confirm(`Удалить налоговый режим «${taxForm.name}»?`)) {
      setInfo(taxForm.id ? 'Удаление налогового режима отменено.' : 'Налоговый режим не выбран.');
      return;
    }
    isSubmitting.value = true;
    clearMessages();
    try {
      await bridgeRequest(`/admin/tax-settings/${taxForm.id}`, { method: 'DELETE' });
      await loadTaxSettings();
      startCreateTax({ silent: true });
      setSuccess('Налоговый режим удалён.');
    } catch (error) {
      setError(error);
    } finally {
      isSubmitting.value = false;
    }
  }

  async function saveLowStockThreshold() {
    isSubmitting.value = true;
    clearMessages();
    try {
      const response = await bridgeRequest('/admin/alerts/low-stock/settings', {
        method: 'PUT',
        data: { threshold: Number(alertState.threshold || 0) },
      });
      alertState.threshold = Number(response.threshold || 0);
      alertState.rows = response.rows || [];
      alertState.loaded = true;
      navigationCounts.alerts = alertState.rows.length;
      setSuccess('Порог низких остатков сохранён.');
    } catch (error) {
      setError(error);
    } finally {
      isSubmitting.value = false;
    }
  }

  function onProductFilesSelected(event) {
    productMediaFiles.value = Array.from(event?.target?.files || []);
  }

  function onCategoryFileSelected(event) {
    categoryImageFile.value = event?.target?.files?.[0] || null;
  }

  async function uploadProductImages() {
    if (!productForm.id) {
      pageError.value = 'Сначала сохраните товар.';
      return;
    }
    if (!productMediaFiles.value.length) {
      pageError.value = 'Выберите хотя бы один файл.';
      return;
    }

    const formData = new FormData();
    productMediaFiles.value.forEach((file) => formData.append('files', file));
    if (productMediaForm.variantId) {
      formData.append('variantId', productMediaForm.variantId);
    }

    isSubmitting.value = true;
    clearMessages();
    try {
      await bridgeRequest(`/products/${productForm.id}/images`, {
        method: 'POST',
        data: formData,
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      await loadProductDetail(productForm.id, { silent: true });
      resetProductMediaEditor();
      setSuccess('Изображения загружены.');
    } catch (error) {
      setError(error);
    } finally {
      isSubmitting.value = false;
    }
  }

  async function deleteProductImage(imageId) {
    if (!productForm.id) {
      pageError.value = 'Сначала сохраните товар.';
      return;
    }
    if (!window.confirm('Удалить это изображение?')) {
      setInfo('Удаление изображения отменено.');
      return;
    }

    isSubmitting.value = true;
    clearMessages();
    try {
      await bridgeRequest(`/products/${productForm.id}/images/${imageId}`, { method: 'DELETE' });
      await loadProductDetail(productForm.id, { silent: true });
      setSuccess('Изображение удалено.');
    } catch (error) {
      setError(error);
    } finally {
      isSubmitting.value = false;
    }
  }

  async function uploadCategoryImage() {
    if (!categoryForm.id) {
      pageError.value = 'Сначала сохраните категорию.';
      return;
    }
    if (!categoryImageFile.value) {
      pageError.value = 'Выберите файл изображения.';
      return;
    }

    const formData = new FormData();
    formData.append('file', categoryImageFile.value);

    isSubmitting.value = true;
    clearMessages();
    try {
      await bridgeRequest(`/categories/${categoryForm.id}/image`, {
        method: 'POST',
        data: formData,
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      await loadCategories({ reloadSelected: false });
      await loadCategoryDetail(categoryForm.id, { silent: true });
      categoryImageFile.value = null;
      setSuccess('Изображение категории обновлено.');
    } catch (error) {
      setError(error);
    } finally {
      isSubmitting.value = false;
    }
  }

  async function openOrCreateOverlay(kind) {
    const overlay = kind === 'product' ? productOverlayInfo.value : categoryOverlayInfo.value;
    const key = kind === 'product' ? productForm.slug.trim() : categoryForm.slug.trim();
    if (!key) {
      pageError.value = 'Сначала сохраните сущность со слагом, затем можно открыть или создать оверлей.';
      return;
    }

    const collection = overlay?.collection || (kind === 'product' ? 'product_overlay' : 'category_overlay');
    const keyField = overlay?.keyField || (kind === 'product' ? 'product_key' : 'category_key');

    isSubmitting.value = true;
    clearMessages();
    try {
      let itemId = overlay?.itemId || null;
      if (!itemId) {
        const response = await api.post(`/items/${collection}`, {
          [keyField]: key,
          status: 'draft',
        });
        itemId = response?.data?.data?.id;
        if (kind === 'product' && productForm.id) {
          await loadProductDetail(productForm.id, { silent: true });
        }
        if (kind === 'category' && categoryForm.id) {
          await loadCategoryDetail(categoryForm.id, { silent: true });
        }
      }
      if (itemId) {
        window.open(`/admin/content/${collection}/${itemId}`, '_blank', 'noopener');
        setInfo('Оверлей открыт в новой вкладке.');
      } else {
        pageError.value = 'Оверлей не удалось открыть: Directus не вернул идентификатор записи.';
      }
    } catch (error) {
      setError(error);
    } finally {
      isSubmitting.value = false;
    }
  }

  function openStorefrontPreview(kind) {
    const previewContext = {
      page: { key: HOME_PAGE_SLUG },
      product: { key: productForm.slug, id: productForm.id },
      category: { key: categoryForm.slug, id: categoryForm.id },
    }[kind] || {};
    const url = buildStorefrontPreviewUrl({
      baseUrl: accessState.previewBaseUrl,
      kind,
      key: previewContext.key,
      id: previewContext.id,
    });

    if (!url) {
      pageError.value = 'Предпросмотр витрины не настроен для этой записи.';
      return;
    }

    window.open(url, '_blank', 'noopener');
    setInfo('Предпросмотр открыт в новой вкладке.');
  }

  async function refreshCurrentTab() {
    isRefreshing.value = true;
    clearMessages();
    try {
      if (activeTab.value === 'home') {
        await loadHomeContent();
      } else if (activeTab.value === 'products') {
        await loadProducts({ reloadSelected: true });
      } else if (activeTab.value === 'categories') {
        await loadCategories({ reloadSelected: true });
      } else if (activeTab.value === 'brands') {
        await loadBrands({ reloadSelected: true });
      } else if (activeTab.value === 'inventory') {
        await loadInventory();
      } else if (activeTab.value === 'orders') {
        await Promise.all([loadOrders(), loadActivePromotions()]);
      } else if (activeTab.value === 'imports') {
        await loadImports();
      } else if (activeTab.value === 'promotions') {
        await loadPromotions();
      } else if (activeTab.value === 'tax') {
        await loadTaxSettings();
      } else if (activeTab.value === 'analytics') {
        await loadAnalytics();
      } else if (activeTab.value === 'alerts') {
        await loadAlerts();
      }
      await loadNavigationSummary();
      setInfo('Раздел обновлён.');
    } finally {
      isRefreshing.value = false;
    }
  }

  async function copyCurrentLink() {
    try {
      await navigator.clipboard.writeText(window.location.href);
      setInfo('Ссылка на текущее рабочее состояние скопирована.');
    } catch (error) {
      setError(error);
    }
  }














  function addSpecificationSection() {
    productForm.specifications.push({
      title: '',
      description: '',
      items: [],
    });
    setLocalChange();
  }

  function removeSpecificationSection(index) {
    if (index < 0 || index >= productForm.specifications.length) {
      setInfo('Секция характеристик не выбрана.');
      return;
    }
    productForm.specifications.splice(index, 1);
    setLocalChange();
  }

  function addSpecificationItem(sectionIndex) {
    const section = productForm.specifications[sectionIndex];
    if (!section) {
      setInfo('Секция характеристик не выбрана.');
      return;
    }
    section.items.push({
      label: '',
      value: '',
    });
    setLocalChange();
  }

  function removeSpecificationItem(sectionIndex, itemIndex) {
    const section = productForm.specifications[sectionIndex];
    if (!section) {
      setInfo('Секция характеристик не выбрана.');
      return;
    }
    if (itemIndex < 0 || itemIndex >= section.items.length) {
      setInfo('Параметр характеристики не выбран.');
      return;
    }
    section.items.splice(itemIndex, 1);
    setLocalChange();
  }






  function persistState() {
    if (!bootstrapped.value) {
      return;
    }
    const payload = {
      activeTab: activeTab.value,
      productId: productState.selectedId || '',
      categoryId: categoryState.selectedId || '',
      brandId: brandState.selectedId || '',
      inventoryVariantId: inventoryState.selectedVariantId || '',
      productPanel: productState.panel,
    };
    localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));

    const params = new URLSearchParams(window.location.search);
    params.set('tab', activeTab.value);
    ['product', 'category', 'brand', 'variant', 'panel'].forEach((key) => params.delete(key));
    if (activeTab.value === 'products') {
      setOrDeleteParam(params, 'product', productState.selectedId);
      setOrDeleteParam(params, 'panel', productState.panel !== 'main' ? productState.panel : '');
    } else if (activeTab.value === 'categories') {
      setOrDeleteParam(params, 'category', categoryState.selectedId);
    } else if (activeTab.value === 'brands') {
      setOrDeleteParam(params, 'brand', brandState.selectedId);
    } else if (activeTab.value === 'inventory') {
      setOrDeleteParam(params, 'variant', inventoryState.selectedVariantId);
    }
    const nextQuery = params.toString();
    const nextUrl = `${window.location.pathname}${nextQuery ? `?${nextQuery}` : ''}`;
    window.history.replaceState({}, '', nextUrl);
  }

  function setOrDeleteParam(params, key, value) {
    if (value) {
      params.set(key, value);
    } else {
      params.delete(key);
    }
  }

  function restoreInitialState() {
    let stored = {};
    try {
      stored = JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}');
    } catch {
      stored = {};
    }

    const params = new URLSearchParams(window.location.search);
    activeTab.value = params.get('tab') || stored.activeTab || 'home';
    productState.selectedId = params.get('product') || stored.productId || '';
    categoryState.selectedId = params.get('category') || stored.categoryId || '';
    brandState.selectedId = params.get('brand') || stored.brandId || '';
    inventoryState.selectedVariantId = params.get('variant') || stored.inventoryVariantId || '';
    productState.panel = params.get('panel') || stored.productPanel || 'main';
    normalizeActiveTab({ notify: Boolean(params.get('tab')) });
  }

  async function mountModuleNavigation() {
    await nextTick();
    navigationTarget.value = '';
  }

  function handleBeforeUnload(event) {
    if (!hasUnsavedChanges.value) {
      return;
    }
    event.preventDefault();
    event.returnValue = '';
  }

  watch(
    () => [activeTab.value, productState.selectedId, categoryState.selectedId, brandState.selectedId, inventoryState.selectedVariantId, productState.panel],
    () => {
      persistState();
    }
  );

  onMounted(async () => {
    syncEditorSnapshots();
    await loadAccessProfile();
    restoreInitialState();
    document.body.classList.add('storefront-ops-view');
    window.addEventListener('beforeunload', handleBeforeUnload);
    await mountModuleNavigation();
    bootstrapped.value = true;
    await Promise.allSettled([loadNavigationSummary(), ensureActiveTabLoaded()]);
    resetInventoryEditor({ silent: true });
  });

  onBeforeUnmount(() => {
    navigationTarget.value = '';
    document.body.classList.remove('storefront-ops-view');
    window.removeEventListener('beforeunload', handleBeforeUnload);
  });

  return {
    copyCurrentLink,
    refreshCurrentTab,
    isRefreshing,
    navigationTarget,
    visibleTabs,
    activeTab,
    setActiveTab,
    tabCount,
    accessState,
    accessRoleLabel,
    managerAnalyticsNotice,
    pageError,
    pageNotice,
    clearMessages,
    activeTabComponent,
    storefrontOpsViewProps,
  };
}
