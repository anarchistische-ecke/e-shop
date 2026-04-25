<template>
  <private-view class="workspace-view" title="Управление витриной">
    <template #actions>
      <v-button secondary @click="copyCurrentLink" :disabled="isRefreshing">
        Копировать ссылку
      </v-button>
      <v-button @click="refreshCurrentTab" :loading="isRefreshing">
        Обновить
      </v-button>
    </template>

    <teleport v-if="navigationTarget" :to="navigationTarget">
      <nav class="pane-tabs pane-tabs-navigation" aria-label="Разделы управления витриной">
        <button
          v-for="tab in visibleTabs"
          :key="tab.id"
          type="button"
          class="pane-tab"
          :class="{ active: activeTab === tab.id }"
          @click="setActiveTab(tab.id)"
        >
          <span>{{ tab.label }}</span>
          <small>{{ tabCount(tab.id) }}</small>
        </button>
      </nav>
    </teleport>

    <div class="workspace">
      <nav v-if="!navigationTarget" class="pane-tabs pane-tabs-inline" aria-label="Разделы управления витриной">
        <button
          v-for="tab in visibleTabs"
          :key="tab.id"
          type="button"
          class="pane-tab"
          :class="{ active: activeTab === tab.id }"
          @click="setActiveTab(tab.id)"
        >
          <span>{{ tab.label }}</span>
          <small>{{ tabCount(tab.id) }}</small>
        </button>
      </nav>

      <div v-if="accessState.loaded" class="access-context">
        <strong>{{ accessRoleLabel }}</strong>
        <span>{{ managerAnalyticsNotice }}</span>
      </div>

      <div v-if="pageError" class="status-banner status-banner-error">
        <strong>Ошибка</strong>
        <span>{{ pageError }}</span>
      </div>
      <div
        v-else-if="pageNotice.text"
        class="status-banner"
        :class="pageNotice.type === 'success' ? 'status-banner-success' : 'status-banner-info'"
      >
        <strong>{{ pageNotice.type === 'success' ? 'Готово' : 'Информация' }}</strong>
        <span>{{ pageNotice.text }}</span>
      </div>

      <div v-if="accessState.loaded && !visibleTabs.length" class="empty-state">
        <strong>Нет доступных разделов</strong>
        <span>Проверьте роль пользователя в Directus и Keycloak.</span>
      </div>

      <section v-else class="workspace-shell" :class="{ 'detail-open': activeDetailOpen }">
        <aside class="workspace-list surface-panel">
        <template v-if="activeTab === 'products'">
          <div class="pane-header">
            <div>
              <h2>Товары</h2>
              <p>{{ productState.items.length }} позиций</p>
            </div>
            <button class="button button-primary" type="button" @click="startCreateProduct">
              Новый товар
            </button>
          </div>

          <label class="search-field">
            <span>Поиск</span>
            <input v-model.trim="productState.query" type="search" placeholder="Название, слаг, бренд" />
          </label>

          <p v-if="productState.overlayReadFailed" class="inline-note">
            Статусы витринных оверлеев временно недоступны. Каталогом всё ещё можно управлять.
          </p>

          <div v-if="isTabLoading('products')" class="empty-state">
            <strong>Загружаю товары</strong>
          </div>
          <div v-else-if="!filteredProducts.length" class="empty-state">
            <strong>Товары не найдены</strong>
            <span>Измените поиск или создайте новую позицию.</span>
          </div>
          <div v-else class="card-list">
            <button
              v-for="product in filteredProducts"
              :key="product.id"
              type="button"
              class="list-card"
              :class="{ active: productState.selectedId === product.id && !productState.isCreating }"
              @click="selectProduct(product.id)"
            >
              <div class="list-card-head">
                <strong>{{ product.name }}</strong>
                <span class="pill" :class="product.isActive ? 'pill-positive' : 'pill-muted'">
                  {{ product.isActive ? 'Активен' : 'Скрыт' }}
                </span>
              </div>
              <p class="list-card-slug">{{ product.slug }}</p>
              <div class="list-card-meta">
                <span>{{ product.brand?.name || 'Без бренда' }}</span>
                <span>{{ product.variantCount }} вариантов</span>
                <span>{{ product.totalStock }} шт.</span>
              </div>
              <div v-if="product.overlay" class="list-card-footer">
                <span class="overlay-chip">{{ overlayStatusLabel(product.overlay.status) }}</span>
              </div>
            </button>
          </div>
        </template>

        <template v-else-if="activeTab === 'categories'">
          <div class="pane-header">
            <div>
              <h2>Категории</h2>
              <p>{{ categoryState.items.length }} узлов</p>
            </div>
            <button class="button button-primary" type="button" @click="startCreateCategory">
              Новая категория
            </button>
          </div>

          <label class="search-field">
            <span>Поиск</span>
            <input v-model.trim="categoryState.query" type="search" placeholder="Название, слаг, путь" />
          </label>

          <p v-if="categoryState.overlayReadFailed" class="inline-note">
            Оверлеи категорий сейчас не читаются, но дерево каталога можно править.
          </p>

          <div v-if="isTabLoading('categories')" class="empty-state">
            <strong>Загружаю категории</strong>
          </div>
          <div v-else-if="!filteredCategories.length" class="empty-state">
            <strong>Категории не найдены</strong>
            <span>Попробуйте другой запрос или создайте новую категорию.</span>
          </div>
          <div v-else class="card-list">
            <button
              v-for="category in filteredCategories"
              :key="category.id"
              type="button"
              class="list-card"
              :class="{ active: categoryState.selectedId === category.id && !categoryState.isCreating }"
              @click="selectCategory(category.id)"
            >
              <div class="list-card-head">
                <strong>{{ category.name }}</strong>
                <span class="pill" :class="category.isActive ? 'pill-positive' : 'pill-muted'">
                  {{ category.isActive ? 'Активна' : 'Скрыта' }}
                </span>
              </div>
              <p class="list-card-slug">{{ category.fullPath || category.slug }}</p>
              <div class="list-card-meta">
                <span>Уровень {{ category.depth + 1 }}</span>
                <span>Позиция {{ category.position }}</span>
              </div>
              <div v-if="category.overlay" class="list-card-footer">
                <span class="overlay-chip">{{ overlayStatusLabel(category.overlay.status) }}</span>
              </div>
            </button>
          </div>
        </template>

        <template v-else-if="activeTab === 'brands'">
          <div class="pane-header">
            <div>
              <h2>Бренды</h2>
              <p>{{ brandState.items.length }} записей</p>
            </div>
            <button class="button button-primary" type="button" @click="startCreateBrand">
              Новый бренд
            </button>
          </div>

          <label class="search-field">
            <span>Поиск</span>
            <input v-model.trim="brandState.query" type="search" placeholder="Название или слаг" />
          </label>

          <div v-if="isTabLoading('brands')" class="empty-state">
            <strong>Загружаю бренды</strong>
          </div>
          <div v-else-if="!filteredBrands.length" class="empty-state">
            <strong>Бренды не найдены</strong>
            <span>Измените поиск или создайте новую запись.</span>
          </div>
          <div v-else class="card-list">
            <button
              v-for="brand in filteredBrands"
              :key="brand.id"
              type="button"
              class="list-card"
              :class="{ active: brandState.selectedId === brand.id && !brandState.isCreating }"
              @click="selectBrand(brand.id)"
            >
              <div class="list-card-head">
                <strong>{{ brand.name }}</strong>
                <span class="pill pill-neutral">{{ brand.productCount }} товаров</span>
              </div>
              <p class="list-card-slug">{{ brand.slug }}</p>
            </button>
          </div>
        </template>

        <template v-else-if="activeTab === 'inventory'">
          <div class="pane-header">
            <div>
              <h2>Остатки</h2>
              <p>{{ inventoryState.items.length }} вариантов</p>
            </div>
          </div>

          <label class="search-field">
            <span>Поиск</span>
            <input v-model.trim="inventoryState.query" type="search" placeholder="SKU, товар, бренд" />
          </label>

          <div v-if="isTabLoading('inventory')" class="empty-state">
            <strong>Загружаю остатки</strong>
          </div>
          <div v-else-if="!filteredInventory.length" class="empty-state">
            <strong>Ничего не найдено</strong>
            <span>Уточните SKU, товар или бренд.</span>
          </div>
          <div v-else class="card-list">
            <button
              v-for="item in filteredInventory"
              :key="item.variantId"
              type="button"
              class="list-card"
              :class="{ active: inventoryState.selectedVariantId === item.variantId }"
              @click="selectInventoryRow(item.variantId)"
            >
              <div class="list-card-head">
                <strong>{{ item.variantName }}</strong>
                <span class="pill" :class="item.productIsActive ? 'pill-positive' : 'pill-muted'">
                  {{ item.stock }} шт.
                </span>
              </div>
              <p class="list-card-slug">{{ item.productName }} · {{ item.sku }}</p>
              <div class="list-card-meta">
                <span>{{ item.brand?.name || 'Без бренда' }}</span>
                <span>{{ formatMoney(item.price) }}</span>
              </div>
            </button>
          </div>
        </template>

        <template v-else-if="activeTab === 'orders'">
          <div class="pane-header">
            <div>
              <h2>Заказы</h2>
              <p>{{ orderState.items.length }} записей</p>
            </div>
            <button class="button button-secondary" type="button" @click="loadOrders">
              Найти
            </button>
          </div>

          <label class="search-field">
            <span>Поиск</span>
            <input v-model.trim="orderState.query" type="search" placeholder="ID, email, токен" @keyup.enter="loadOrders" />
          </label>

          <div class="form-grid">
            <label class="ops-field">
              <span>Статус</span>
              <input v-model.trim="orderState.status" type="text" placeholder="PENDING, PAID" @keyup.enter="loadOrders" />
            </label>
            <label class="ops-field">
              <span>Менеджер</span>
              <input v-model.trim="orderState.manager" type="text" @keyup.enter="loadOrders" />
            </label>
            <label class="ops-field">
              <span>С</span>
              <input v-model="orderState.from" type="datetime-local" @keyup.enter="loadOrders" />
            </label>
            <label class="ops-field">
              <span>По</span>
              <input v-model="orderState.to" type="datetime-local" @keyup.enter="loadOrders" />
            </label>
          </div>

          <section v-if="canViewActivePromotions" class="section-block section-block-compact">
            <div class="section-head">
              <div>
                <h3>Активные акции</h3>
                <p>{{ activePromotionState.items.length }} доступно для менеджера</p>
              </div>
              <button class="button button-secondary" type="button" @click="loadActivePromotions">
                Обновить
              </button>
            </div>
            <div v-if="loading.activePromotions" class="empty-inline">Загружаю акции.</div>
            <div v-else-if="!activePromotionState.items.length" class="empty-inline">Активных акций сейчас нет.</div>
            <div v-else class="card-list card-list-compact">
              <article v-for="promotion in activePromotionState.items" :key="promotion.id" class="list-card">
                <div class="list-card-head">
                  <strong>{{ promotion.name }}</strong>
                  <span class="pill pill-positive">{{ promotion.type }}</span>
                </div>
                <div class="list-card-meta">
                  <span v-if="promotion.salePriceAmount">Цена {{ formatMinorMoney(promotion.salePriceAmount, promotion.currency) }}</span>
                  <span v-if="promotion.discountPercent">{{ promotion.discountPercent }}%</span>
                  <span v-if="promotion.thresholdAmount">от {{ formatMinorMoney(promotion.thresholdAmount, promotion.currency) }}</span>
                </div>
              </article>
            </div>
          </section>

          <div v-if="isTabLoading('orders')" class="empty-state">
            <strong>Загружаю заказы</strong>
          </div>
          <div v-else-if="!orderState.items.length" class="empty-state">
            <strong>Заказы не найдены</strong>
            <span>Измените фильтр или период.</span>
          </div>
          <div v-else class="card-list">
            <button
              v-for="order in orderState.items"
              :key="order.id"
              type="button"
              class="list-card"
              :class="{ active: orderState.selectedId === order.id }"
              @click="selectOrder(order.id)"
            >
              <div class="list-card-head">
                <strong>{{ order.receiptEmail || order.id }}</strong>
                <span class="pill pill-neutral">{{ orderStatusLabel(order.status) }}</span>
              </div>
              <p class="list-card-slug">{{ order.id }}</p>
              <div class="list-card-meta">
                <span>{{ formatMoney(order.totalAmount) }}</span>
                <span>{{ orderManagerLabel(order) }}</span>
                <span>{{ formatDateTime(order.orderDate) }}</span>
              </div>
            </button>
          </div>
        </template>

        <template v-else-if="activeTab === 'imports'">
          <div class="pane-header">
            <div>
              <h2>Импорт</h2>
              <p>{{ importState.jobs.length }} запусков</p>
            </div>
          </div>

          <div v-if="isTabLoading('imports')" class="empty-state">
            <strong>Загружаю историю</strong>
          </div>
          <div v-else-if="!importState.jobs.length" class="empty-state">
            <strong>История пуста</strong>
            <span>Запустите dry-run для Excel или CSV файла.</span>
          </div>
          <div v-else class="card-list">
            <button
              v-for="job in importState.jobs"
              :key="job.id"
              type="button"
              class="list-card"
              :class="{ active: importState.selectedJobId === job.id }"
              @click="selectImportJob(job.id)"
            >
              <div class="list-card-head">
                <strong>{{ job.fileName || job.id }}</strong>
                <span class="pill" :class="job.invalidRows ? 'pill-muted' : 'pill-positive'">{{ job.status }}</span>
              </div>
              <div class="list-card-meta">
                <span>{{ job.validRows }} валидных</span>
                <span>{{ job.invalidRows }} ошибок</span>
                <span>{{ formatDateTime(job.createdAt) }}</span>
              </div>
            </button>
          </div>
        </template>

        <template v-else-if="activeTab === 'promotions'">
          <div class="pane-header">
            <div>
              <h2>Акции</h2>
              <p>{{ promotionState.items.length }} акций · {{ promotionState.promoCodes.length }} кодов</p>
            </div>
            <button class="button button-primary" type="button" @click="startCreatePromotion">
              Новая акция
            </button>
          </div>

          <div v-if="isTabLoading('promotions')" class="empty-state">
            <strong>Загружаю акции</strong>
          </div>
          <div v-else class="card-list">
            <button
              v-for="promotion in promotionState.items"
              :key="promotion.id"
              type="button"
              class="list-card"
              :class="{ active: promotionState.selectedId === promotion.id && promotionState.mode === 'promotion' }"
              @click="selectPromotion(promotion)"
            >
              <div class="list-card-head">
                <strong>{{ promotion.name }}</strong>
                <span class="pill" :class="promotion.activeNow ? 'pill-positive' : 'pill-muted'">{{ promotion.status }}</span>
              </div>
              <p class="list-card-slug">{{ promotion.type }}</p>
              <div class="list-card-meta">
                <span>{{ promotion.startsAt ? formatDateTime(promotion.startsAt) : 'Без начала' }}</span>
                <span>{{ promotion.endsAt ? formatDateTime(promotion.endsAt) : 'Без окончания' }}</span>
              </div>
            </button>
            <button
              v-for="promoCode in promotionState.promoCodes"
              :key="promoCode.id"
              type="button"
              class="list-card"
              :class="{ active: promotionState.selectedPromoCodeId === promoCode.id && promotionState.mode === 'promoCode' }"
              @click="selectPromoCode(promoCode)"
            >
              <div class="list-card-head">
                <strong>{{ promoCode.code }}</strong>
                <span class="pill" :class="promoCode.activeNow ? 'pill-positive' : 'pill-muted'">{{ promoCode.status }}</span>
              </div>
              <p class="list-card-slug">Промокод</p>
              <div class="list-card-meta">
                <span>{{ promoCode.redemptionCount }} использований</span>
                <span>{{ promoCode.maxRedemptions || 'Без лимита' }}</span>
              </div>
            </button>
          </div>
        </template>

        <template v-else-if="activeTab === 'tax'">
          <div class="pane-header">
            <div>
              <h2>Налоги</h2>
              <p>{{ taxState.items.length }} режимов</p>
            </div>
            <button class="button button-primary" type="button" @click="startCreateTax">
              Новый режим
            </button>
          </div>

          <div v-if="isTabLoading('tax')" class="empty-state">
            <strong>Загружаю настройки</strong>
          </div>
          <div v-else class="card-list">
            <button
              v-for="tax in taxState.items"
              :key="tax.id"
              type="button"
              class="list-card"
              :class="{ active: taxState.selectedId === tax.id }"
              @click="selectTax(tax)"
            >
              <div class="list-card-head">
                <strong>{{ tax.name }}</strong>
                <span class="pill" :class="tax.active ? 'pill-positive' : 'pill-muted'">{{ tax.active ? 'Активен' : tax.status }}</span>
              </div>
              <div class="list-card-meta">
                <span>СНО {{ tax.taxSystemCode }}</span>
                <span>НДС {{ tax.vatCode }}</span>
                <span>{{ tax.vatRatePercent ?? 0 }}%</span>
              </div>
            </button>
          </div>
        </template>

        <template v-else-if="activeTab === 'analytics'">
          <div class="pane-header">
            <div>
              <h2>Аналитика</h2>
              <p>{{ analyticsState.managerRows.length }} менеджеров</p>
            </div>
            <button class="button button-secondary" type="button" @click="loadAnalytics">
              Рассчитать
            </button>
          </div>

          <label v-if="!isManagerRole" class="search-field">
            <span>Менеджер</span>
            <input v-model.trim="analyticsState.manager" type="search" placeholder="Фильтр" @keyup.enter="loadAnalytics" />
          </label>
          <p v-else class="inline-note">
            Для роли менеджера показываются только ваши заказы, ссылки оплаты и комиссия.
          </p>

          <div v-if="isTabLoading('analytics')" class="empty-state">
            <strong>Считаю показатели</strong>
          </div>
          <div v-else-if="!analyticsState.managerRows.length" class="empty-state">
            <strong>Данных нет</strong>
            <span>За выбранный период не найдено заказов менеджера.</span>
          </div>
          <div v-else class="card-list">
            <article v-for="row in analyticsState.managerRows" :key="row.managerSubject" class="list-card">
              <div class="list-card-head">
                <strong>{{ row.managerSubject }}</strong>
                <span class="pill pill-neutral">{{ row.paidOrders }}/{{ row.totalOrders }}</span>
              </div>
              <div class="list-card-meta">
                <span>{{ formatMoney(row.paidAmount) }}</span>
                <span>3% {{ formatMoney(row.commission) }}</span>
              </div>
            </article>
          </div>
        </template>

        <template v-else-if="activeTab === 'alerts'">
          <div class="pane-header">
            <div>
              <h2>Алерты</h2>
              <p>{{ alertState.rows.length }} SKU ниже порога</p>
            </div>
          </div>

          <label class="ops-field">
            <span>Глобальный порог</span>
            <input v-model.number="alertState.threshold" type="number" min="0" step="1" />
          </label>
          <button class="button button-secondary" type="button" :disabled="isSubmitting" @click="saveLowStockThreshold">
            Сохранить порог
          </button>

          <div v-if="isTabLoading('alerts')" class="empty-state">
            <strong>Проверяю остатки</strong>
          </div>
          <div v-else-if="!alertState.rows.length" class="empty-state">
            <strong>Алертов нет</strong>
            <span>Все SKU выше текущего порога.</span>
          </div>
          <div v-else class="card-list">
            <article v-for="row in alertState.rows" :key="row.variantId" class="list-card">
              <div class="list-card-head">
                <strong>{{ row.productName }}</strong>
                <span class="pill pill-muted">{{ row.stock }} шт.</span>
              </div>
              <p class="list-card-slug">{{ row.variantName }} · {{ row.sku }}</p>
            </article>
          </div>
        </template>
        </aside>

        <main class="workspace-detail">
        <template v-if="activeTab === 'products'">
          <div v-if="!productState.isCreating && !productState.detail" class="empty-detail">
            <strong>Выберите товар</strong>
            <span>Откройте карточку из списка или создайте новую позицию.</span>
          </div>
          <section v-else class="detail-card">
            <header class="detail-header">
              <div>
                <p class="detail-kicker">Товары</p>
                <h2>{{ productState.isCreating ? 'Новый товар' : productState.detail?.item?.name }}</h2>
                <p v-if="productState.detail?.item?.slug" class="detail-subtitle">{{ productState.detail.item.slug }}</p>
              </div>
              <div class="detail-header-actions">
                <button
                  v-if="productForm.id"
                  class="button button-danger"
                  type="button"
                  :disabled="isSubmitting"
                  @click="deleteProduct"
                >
                  Удалить
                </button>
              </div>
            </header>

            <nav class="subtabs" aria-label="Разделы товара">
              <button
                v-for="tab in productDetailTabs"
                :key="tab.id"
                type="button"
                class="subtab"
                :class="{ active: productState.panel === tab.id }"
                @click="setProductPanel(tab.id)"
              >
                {{ tab.label }}
              </button>
            </nav>

            <div v-if="productState.panel === 'main'" class="detail-content panel-main">
              <form class="editor-form" @submit.prevent="submitProduct">
                <div v-if="productForm.id" class="metrics-row">
                  <article class="metric-card">
                    <span>ID товара</span>
                    <strong class="metric-code">{{ productForm.id }}</strong>
                  </article>
                  <article class="metric-card">
                    <span>Создан</span>
                    <strong>{{ formatDateTime(productState.detail?.item?.createdAt) }}</strong>
                  </article>
                  <article class="metric-card">
                    <span>Обновлён</span>
                    <strong>{{ formatDateTime(productState.detail?.item?.updatedAt) }}</strong>
                  </article>
                </div>

                <div class="form-grid">
                  <label class="ops-field ops-field-required">
                    <span>Название</span>
                    <input v-model.trim="productForm.name" type="text" />
                  </label>

                  <label class="ops-field ops-field-required">
                    <span>Слаг</span>
                    <input v-model.trim="productForm.slug" type="text" />
                  </label>
                </div>

                <div class="form-grid">
                  <label class="ops-field">
                    <span>Бренд</span>
                    <select v-model="productForm.brandId">
                      <option value="">Без бренда</option>
                      <option v-for="brand in productBrandOptions" :key="brand.id" :value="brand.id">
                        {{ brand.name }}
                      </option>
                    </select>
                  </label>

                  <label class="ops-field ops-field-boolean">
                    <span>Витрина</span>
                    <label class="ops-toggle">
                      <input v-model="productForm.isActive" type="checkbox" />
                      <span>{{ productForm.isActive ? 'Показывать товар' : 'Скрыть товар' }}</span>
                    </label>
                  </label>
                </div>

                <label class="ops-field">
                  <span>Описание</span>
                  <textarea v-model="productForm.description" rows="6" />
                </label>

                <section class="selector-card">
                  <div class="selector-card-head">
                    <div>
                      <h3>Технические характеристики</h3>
                      <p>Секции и параметры товара, которые хранятся в backend product.specifications.</p>
                    </div>
                    <button class="button button-secondary" type="button" @click="addSpecificationSection">
                      Добавить секцию
                    </button>
                  </div>

                  <div v-if="productForm.specifications.length" class="spec-section-list">
                    <article
                      v-for="(section, sectionIndex) in productForm.specifications"
                      :key="`spec-section-${sectionIndex}`"
                      class="spec-section-card"
                    >
                      <div class="spec-section-head">
                        <div>
                          <strong>Секция {{ sectionIndex + 1 }}</strong>
                          <p>{{ section.items.length }} параметров</p>
                        </div>
                        <button
                          class="button button-danger button-small"
                          type="button"
                          @click="removeSpecificationSection(sectionIndex)"
                        >
                          Удалить секцию
                        </button>
                      </div>

                      <div class="form-grid">
                        <label class="ops-field">
                          <span>Заголовок секции</span>
                          <input v-model.trim="section.title" type="text" />
                        </label>

                        <label class="ops-field">
                          <span>Описание секции</span>
                          <input v-model.trim="section.description" type="text" />
                        </label>
                      </div>

                      <div class="spec-items">
                        <div class="spec-items-head">
                          <strong>Параметры</strong>
                          <button
                            class="button button-secondary button-small"
                            type="button"
                            @click="addSpecificationItem(sectionIndex)"
                          >
                            Добавить параметр
                          </button>
                        </div>

                        <div v-if="section.items.length" class="spec-item-list">
                          <div
                            v-for="(item, itemIndex) in section.items"
                            :key="`spec-item-${sectionIndex}-${itemIndex}`"
                            class="spec-item-row"
                          >
                            <label class="ops-field ops-field-required">
                              <span>Параметр</span>
                              <input v-model.trim="item.label" type="text" />
                            </label>

                            <label class="ops-field ops-field-required">
                              <span>Значение</span>
                              <input v-model.trim="item.value" type="text" />
                            </label>

                            <button
                              class="button button-danger button-small spec-item-remove"
                              type="button"
                              @click="removeSpecificationItem(sectionIndex, itemIndex)"
                            >
                              Удалить
                            </button>
                          </div>
                        </div>
                        <div v-else class="empty-inline">В секции пока нет параметров.</div>
                      </div>
                    </article>
                  </div>
                  <div v-else class="empty-inline">
                    Характеристики ещё не заданы. Добавьте секцию, если товару нужны технические параметры.
                  </div>
                </section>

                <section class="selector-card">
                  <div class="selector-card-head">
                    <div>
                      <h3>Категории</h3>
                      <p>Выберите разделы, в которых товар должен отображаться.</p>
                    </div>
                    <span class="pill pill-neutral">{{ productForm.categoryIds.length }}</span>
                  </div>

                  <label class="ops-field">
                    <span>Фильтр категорий</span>
                    <input v-model.trim="productCategoryFilter" type="search" placeholder="Поиск по дереву" />
                  </label>

                  <div v-if="filteredProductCategoryOptions.length" class="choice-list">
                    <label v-for="option in filteredProductCategoryOptions" :key="option.id" class="choice-item">
                      <input v-model="productForm.categoryIds" type="checkbox" :value="option.id" />
                      <div>
                        <strong>{{ formatCategoryLabel(option) }}</strong>
                        <small>{{ option.fullPath || option.slug }}</small>
                      </div>
                    </label>
                  </div>
                  <div v-else class="empty-inline">Нет категорий под текущий фильтр.</div>
                </section>

                <div class="sticky-actions">
                  <button class="button button-primary" type="submit" :disabled="isSubmitting">
                    {{ productForm.id ? 'Сохранить товар' : 'Создать товар' }}
                  </button>
                  <button class="button button-secondary" type="button" :disabled="isSubmitting" @click="resetProductEditor">
                    Сбросить
                  </button>
                </div>
              </form>
            </div>

            <div v-else-if="productState.panel === 'variants'" class="detail-content panel-variants">
              <div v-if="!productForm.id" class="empty-inline">
                Сначала сохраните товар, затем добавляйте варианты.
              </div>
              <template v-else>
                <div class="section-block">
                  <div class="section-head">
                    <div>
                      <h3>Варианты</h3>
                      <p>{{ productState.detail?.item?.variants?.length || 0 }} записей</p>
                    </div>
                    <button class="button button-secondary" type="button" @click="resetVariantEditor">
                      Новый вариант
                    </button>
                  </div>

                  <div v-if="productState.detail?.item?.variants?.length" class="variant-list">
                    <article v-for="variant in productState.detail.item.variants" :key="variant.id" class="variant-card">
                      <div>
                        <strong>{{ variant.name }}</strong>
                        <p>{{ variant.sku }}</p>
                      </div>
                      <div class="variant-card-meta">
                        <span>{{ formatMoney(variant.price) }}</span>
                        <span>{{ variant.stock }} шт.</span>
                      </div>
                      <button class="button button-secondary" type="button" @click="loadVariantEditor(variant)">
                        Изменить
                      </button>
                      <button class="button button-danger" type="button" :disabled="isSubmitting" @click="deleteVariant(variant)">
                        Удалить
                      </button>
                    </article>
                  </div>
                  <div v-else class="empty-inline">У товара пока нет вариантов.</div>
                </div>

                <form class="editor-form" @submit.prevent="submitVariant">
                  <div class="form-grid">
                    <label class="ops-field ops-field-required">
                      <span>SKU</span>
                      <input v-model.trim="variantForm.sku" type="text" :disabled="Boolean(variantForm.id)" />
                    </label>

                    <label class="ops-field ops-field-required">
                      <span>Название варианта</span>
                      <input v-model.trim="variantForm.name" type="text" />
                    </label>
                  </div>

                  <div class="form-grid">
                    <label class="ops-field ops-field-required">
                      <span>Цена</span>
                      <input v-model.number="variantForm.amount" type="number" min="0" step="1" />
                    </label>

                    <label class="ops-field ops-field-required">
                      <span>Валюта</span>
                      <input v-model.trim="variantForm.currency" type="text" maxlength="3" />
                    </label>
                  </div>

                  <div class="form-grid">
                    <label class="ops-field ops-field-required">
                      <span>Остаток</span>
                      <input v-model.number="variantForm.stock" type="number" step="1" />
                    </label>

                    <label class="ops-field">
                      <span>Вес, г</span>
                      <input v-model.number="variantForm.weightGrossG" type="number" min="0" step="1" />
                    </label>
                  </div>

                  <div class="form-grid form-grid-three">
                    <label class="ops-field">
                      <span>Длина, мм</span>
                      <input v-model.number="variantForm.lengthMm" type="number" min="0" step="1" />
                    </label>

                    <label class="ops-field">
                      <span>Ширина, мм</span>
                      <input v-model.number="variantForm.widthMm" type="number" min="0" step="1" />
                    </label>

                    <label class="ops-field">
                      <span>Высота, мм</span>
                      <input v-model.number="variantForm.heightMm" type="number" min="0" step="1" />
                    </label>
                  </div>

                  <div class="sticky-actions">
                    <button class="button button-primary" type="submit" :disabled="isSubmitting">
                      {{ variantForm.id ? 'Сохранить вариант' : 'Добавить вариант' }}
                    </button>
                    <button class="button button-secondary" type="button" :disabled="isSubmitting" @click="resetVariantEditor">
                      Сбросить
                    </button>
                  </div>
                </form>
              </template>
            </div>

            <div v-else-if="productState.panel === 'media'" class="detail-content panel-media">
              <div v-if="!productForm.id" class="empty-inline">
                Сначала сохраните товар, затем загружайте медиа.
              </div>
              <template v-else>
                <div class="section-block">
                  <div class="section-head">
                    <div>
                      <h3>Текущие изображения</h3>
                      <p>{{ productState.detail?.item?.images?.length || 0 }} файлов</p>
                    </div>
                  </div>

                  <div v-if="productState.detail?.item?.images?.length" class="media-grid">
                    <article v-for="image in productState.detail.item.images" :key="image.id" class="media-card">
                      <img class="media-preview" :src="image.url" :alt="`Изображение ${image.id}`" />
                      <div class="media-card-body">
                        <div class="media-card-meta">
                          <span>Позиция {{ image.position }}</span>
                          <span v-if="image.variantId">Вариант</span>
                          <span v-else>Общий актив</span>
                        </div>
                        <p class="media-card-url">{{ image.url }}</p>
                      </div>
                      <button class="button button-danger" type="button" :disabled="isSubmitting" @click="deleteProductImage(image.id)">
                        Удалить
                      </button>
                    </article>
                  </div>
                  <div v-else class="empty-inline">Изображения ещё не загружены.</div>
                </div>

                <form class="editor-form" @submit.prevent="uploadProductImages">
                  <div class="form-grid">
                    <label class="ops-field ops-field-required">
                      <span>Файлы</span>
                      <input type="file" multiple accept="image/*" @change="onProductFilesSelected" />
                    </label>

                    <label class="ops-field">
                      <span>Привязать к варианту</span>
                      <select v-model="productMediaForm.variantId">
                        <option value="">Без привязки</option>
                        <option v-for="variant in productState.detail?.item?.variants || []" :key="variant.id" :value="variant.id">
                          {{ variant.name }} · {{ variant.sku }}
                        </option>
                      </select>
                    </label>
                  </div>

                  <div class="sticky-actions">
                    <button class="button button-primary" type="submit" :disabled="isSubmitting || !productMediaFiles.length">
                      Загрузить изображения
                    </button>
                  </div>
                </form>
              </template>
            </div>

            <div v-else class="detail-content panel-merch">
              <div v-if="productState.overlayReadFailed" class="inline-note">
                Статус оверлея сейчас не читается из CMS. Попробуйте обновить раздел позже.
              </div>

              <article class="merch-card">
                <div class="merch-card-head">
                  <div>
                    <h3>Витринный оверлей</h3>
                    <p>Редакционный слой для маркетинговых заголовков, badge и контентных блоков.</p>
                  </div>
                  <span
                    v-if="productOverlayInfo"
                    class="pill"
                    :class="productOverlayInfo.exists ? 'pill-positive' : 'pill-neutral'"
                  >
                    {{ productOverlayInfo.exists ? overlayStatusLabel(productOverlayInfo.status) : 'Не создан' }}
                  </span>
                </div>

                <dl class="definition-list">
                  <div>
                    <dt>Ключ</dt>
                    <dd>{{ productOverlayInfo?.key || productForm.slug || 'Сначала сохраните слаг товара' }}</dd>
                  </div>
                  <div>
                    <dt>Маркетинговый заголовок</dt>
                    <dd>{{ productOverlayInfo?.marketingTitle || 'Не задан' }}</dd>
                  </div>
                  <div>
                    <dt>Badge</dt>
                    <dd>{{ productOverlayInfo?.badgeText || 'Не задан' }}</dd>
                  </div>
                  <div>
                    <dt>Подборки</dt>
                    <dd>{{ productOverlayInfo?.linkedCollectionKeys?.length ? productOverlayInfo.linkedCollectionKeys.join(', ') : 'Нет связей' }}</dd>
                  </div>
                </dl>

                <div class="sticky-actions">
                  <button class="button button-primary" type="button" :disabled="isSubmitting" @click="openOrCreateOverlay('product')">
                    {{ productOverlayInfo?.exists ? 'Открыть оверлей' : 'Создать оверлей' }}
                  </button>
                </div>
              </article>
            </div>
          </section>
        </template>

        <template v-else-if="activeTab === 'categories'">
          <div v-if="!categoryState.isCreating && !categoryState.detail" class="empty-detail">
            <strong>Выберите категорию</strong>
            <span>Откройте узел дерева слева или создайте новую категорию.</span>
          </div>
          <section v-else class="detail-card">
            <header class="detail-header">
              <div>
                <p class="detail-kicker">Категории</p>
                <h2>{{ categoryState.isCreating ? 'Новая категория' : categoryState.detail?.item?.name }}</h2>
                <p v-if="categoryState.detail?.item?.fullPath" class="detail-subtitle">{{ categoryState.detail.item.fullPath }}</p>
              </div>
              <div class="detail-header-actions">
                <button
                  v-if="categoryForm.id"
                  class="button button-danger"
                  type="button"
                  :disabled="isSubmitting"
                  @click="deleteCategory"
                >
                  Удалить
                </button>
              </div>
            </header>

            <div v-if="categoryState.overlayReadFailed" class="inline-note">
              Оверлей категории сейчас не читается. Таксономию можно редактировать без ограничений.
            </div>

            <form class="editor-form category-editor" @submit.prevent="submitCategory">
              <div v-if="categoryForm.id || categoryState.detail?.item?.fullPath" class="metrics-row compact-metrics">
                <article class="metric-card">
                  <span>Путь</span>
                  <strong>{{ categoryState.detail?.item?.fullPath || categoryForm.slug || 'Не задан' }}</strong>
                </article>
                <article class="metric-card">
                  <span>Уровень</span>
                  <strong>{{ Number(categoryState.detail?.item?.depth || 0) + 1 }}</strong>
                </article>
                <article class="metric-card">
                  <span>Позиция</span>
                  <strong>{{ categoryForm.position }}</strong>
                </article>
              </div>

              <div class="form-grid">
                <label class="ops-field ops-field-required">
                  <span>Название</span>
                  <input v-model.trim="categoryForm.name" type="text" />
                </label>

                <label class="ops-field ops-field-required">
                  <span>Слаг</span>
                  <input v-model.trim="categoryForm.slug" type="text" />
                </label>
              </div>

              <div class="form-grid">
                <label class="ops-field">
                  <span>Родительская категория</span>
                  <select v-model="categoryForm.parentId">
                    <option value="">Без родителя</option>
                    <option v-for="option in availableParentOptions" :key="option.id" :value="option.id">
                      {{ formatCategoryLabel(option) }}
                    </option>
                  </select>
                </label>

                <label class="ops-field">
                  <span>Позиция</span>
                  <input v-model.number="categoryForm.position" type="number" min="0" step="1" />
                </label>
              </div>

              <label class="ops-field ops-field-boolean">
                <span>Витрина</span>
                <label class="ops-toggle">
                  <input v-model="categoryForm.isActive" type="checkbox" />
                  <span>{{ categoryForm.isActive ? 'Категория видна' : 'Категория скрыта' }}</span>
                </label>
              </label>

              <label class="ops-field">
                <span>Описание</span>
                <textarea v-model="categoryForm.description" rows="5" />
              </label>

              <div class="context-grid">
                <section class="media-inline-panel">
                  <div>
                    <h3>Изображение категории</h3>
                    <p>Файл хранится в каталожных медиа и не зависит от файловой библиотеки CMS.</p>
                  </div>
                  <div v-if="categoryState.detail?.item?.imageUrl" class="category-image-preview">
                    <img :src="categoryState.detail.item.imageUrl" :alt="categoryState.detail.item.name" />
                  </div>
                  <label class="ops-field">
                    <span>Файл изображения</span>
                    <input type="file" accept="image/*" @change="onCategoryFileSelected" />
                  </label>
                  <button
                    class="button button-secondary"
                    type="button"
                    :disabled="isSubmitting || !categoryImageFile"
                    @click="uploadCategoryImage"
                  >
                    Загрузить изображение
                  </button>
                </section>

                <article class="merch-card">
                  <div class="merch-card-head">
                    <div>
                      <h3>Витринный оверлей</h3>
                      <p>Маркетинговый слой для hero, badge и редакционных блоков категории.</p>
                    </div>
                    <span
                      v-if="categoryOverlayInfo"
                      class="pill"
                      :class="categoryOverlayInfo.exists ? 'pill-positive' : 'pill-neutral'"
                    >
                      {{ categoryOverlayInfo.exists ? overlayStatusLabel(categoryOverlayInfo.status) : 'Не создан' }}
                    </span>
                  </div>

                  <dl class="definition-list">
                    <div>
                      <dt>Ключ</dt>
                      <dd>{{ categoryOverlayInfo?.key || categoryForm.slug || 'Сначала сохраните слаг категории' }}</dd>
                    </div>
                    <div>
                      <dt>Маркетинговый заголовок</dt>
                      <dd>{{ categoryOverlayInfo?.marketingTitle || 'Не задан' }}</dd>
                    </div>
                    <div>
                      <dt>Badge</dt>
                      <dd>{{ categoryOverlayInfo?.badgeText || 'Не задан' }}</dd>
                    </div>
                  </dl>

                  <div class="sticky-actions sticky-actions-inline">
                    <button class="button button-primary" type="button" :disabled="isSubmitting" @click="openOrCreateOverlay('category')">
                      {{ categoryOverlayInfo?.exists ? 'Открыть оверлей' : 'Создать оверлей' }}
                    </button>
                  </div>
                </article>
              </div>

              <div class="sticky-actions">
                <button class="button button-primary" type="submit" :disabled="isSubmitting">
                  {{ categoryForm.id ? 'Сохранить категорию' : 'Создать категорию' }}
                </button>
                <button class="button button-secondary" type="button" :disabled="isSubmitting" @click="resetCategoryEditor">
                  Сбросить
                </button>
              </div>
            </form>
          </section>
        </template>

        <template v-else-if="activeTab === 'brands'">
          <div v-if="!brandState.isCreating && !brandState.detail" class="empty-detail">
            <strong>Выберите бренд</strong>
            <span>Откройте карточку слева или создайте новую запись.</span>
          </div>
          <section v-else class="detail-card">
            <header class="detail-header">
              <div>
                <p class="detail-kicker">Бренды</p>
                <h2>{{ brandState.isCreating ? 'Новый бренд' : brandState.detail?.item?.name }}</h2>
                <p v-if="brandState.detail?.item?.slug" class="detail-subtitle">{{ brandState.detail.item.slug }}</p>
              </div>
              <div class="detail-header-actions">
                <button
                  v-if="brandForm.id"
                  class="button button-danger"
                  type="button"
                  :disabled="isSubmitting"
                  @click="deleteBrand"
                >
                  Удалить
                </button>
              </div>
            </header>

            <form class="editor-form brand-editor" @submit.prevent="submitBrand">
              <div class="metrics-row">
                <article class="metric-card">
                  <span>Товаров у бренда</span>
                  <strong>{{ brandState.detail?.item?.productCount || 0 }}</strong>
                </article>
              </div>

              <div class="form-grid">
                <label class="ops-field ops-field-required">
                  <span>Название</span>
                  <input v-model.trim="brandForm.name" type="text" />
                </label>

                <label class="ops-field ops-field-required">
                  <span>Слаг</span>
                  <input v-model.trim="brandForm.slug" type="text" />
                </label>
              </div>

              <label class="ops-field">
                <span>Описание</span>
                <textarea v-model="brandForm.description" rows="6" />
              </label>

              <div class="sticky-actions">
                <button class="button button-primary" type="submit" :disabled="isSubmitting">
                  {{ brandForm.id ? 'Сохранить бренд' : 'Создать бренд' }}
                </button>
                <button class="button button-secondary" type="button" :disabled="isSubmitting" @click="resetBrandEditor">
                  Сбросить
                </button>
              </div>
            </form>
          </section>
        </template>

        <template v-else-if="activeTab === 'inventory'">
          <div v-if="!selectedInventoryRow" class="empty-detail">
            <strong>Выберите вариант</strong>
            <span>Откройте SKU слева, чтобы скорректировать остаток.</span>
          </div>
          <section v-else class="detail-card">
            <header class="detail-header">
              <div>
                <p class="detail-kicker">Остатки</p>
                <h2>{{ selectedInventoryRow.variantName }}</h2>
                <p class="detail-subtitle">{{ selectedInventoryRow.productName }} · {{ selectedInventoryRow.sku }}</p>
              </div>
            </header>

            <div class="metrics-row">
              <article class="metric-card">
                <span>Текущий остаток</span>
                <strong>{{ selectedInventoryRow.stock }}</strong>
              </article>
              <article class="metric-card">
                <span>Цена</span>
                <strong>{{ formatMoney(selectedInventoryRow.price) }}</strong>
              </article>
              <article class="metric-card">
                <span>Бренд</span>
                <strong>{{ selectedInventoryRow.brand?.name || 'Без бренда' }}</strong>
              </article>
            </div>

            <form class="editor-form inventory-editor" @submit.prevent="submitInventoryAdjustment">
              <div class="form-grid">
                <label class="ops-field ops-field-required">
                  <span>Изменение</span>
                  <input v-model.number="inventoryForm.delta" type="number" step="1" />
                </label>

                <label class="ops-field ops-field-required">
                  <span>Ключ идемпотентности</span>
                  <input v-model.trim="inventoryForm.idempotencyKey" type="text" />
                </label>
              </div>

              <label class="ops-field">
                <span>Причина</span>
                <textarea v-model="inventoryForm.reason" rows="4" />
              </label>

              <div class="sticky-actions">
                <button class="button button-primary" type="submit" :disabled="isSubmitting">
                  Применить корректировку
                </button>
                <button class="button button-secondary" type="button" :disabled="isSubmitting" @click="resetInventoryEditor">
                  Новый ключ
                </button>
              </div>
            </form>
          </section>
        </template>

        <template v-else-if="activeTab === 'orders'">
          <div v-if="!orderState.detail" class="empty-detail">
            <strong>Выберите заказ</strong>
            <span>Откройте заказ из списка, чтобы увидеть состав, историю и действия.</span>
          </div>
          <section v-else class="detail-card">
            <header class="detail-header">
              <div>
                <p class="detail-kicker">Заказы</p>
                <h2>{{ orderState.detail.order.receiptEmail || orderState.detail.order.id }}</h2>
                <p class="detail-subtitle">{{ orderState.detail.order.id }}</p>
              </div>
              <div class="detail-header-actions">
                <button
                  v-if="canClaimSelectedOrder"
                  class="button button-secondary"
                  type="button"
                  :disabled="isSubmitting"
                  @click="claimOrder"
                >
                  Взять в работу
                </button>
                <button
                  v-if="canClearSelectedOrder"
                  class="button button-danger"
                  type="button"
                  :disabled="isSubmitting"
                  @click="clearOrderClaim"
                >
                  Снять менеджера
                </button>
              </div>
            </header>

            <div class="metrics-row">
              <article class="metric-card">
                <span>Статус</span>
                <strong>{{ orderStatusLabel(orderState.detail.order.status) }}</strong>
              </article>
              <article class="metric-card">
                <span>Сумма</span>
                <strong>{{ formatMoney(orderState.detail.order.totalAmount) }}</strong>
              </article>
              <article class="metric-card">
                <span>Менеджер</span>
                <strong>{{ orderManagerLabel(orderState.detail.order) }}</strong>
              </article>
            </div>

            <section class="section-block">
              <div class="section-head">
                <div>
                  <h3>Статус</h3>
                  <p>Переход будет записан в аудит истории заказа.</p>
                </div>
              </div>
              <form class="editor-form" @submit.prevent="submitOrderStatus">
                <div class="form-grid">
                  <label class="ops-field ops-field-required">
                    <span>Новый статус</span>
                    <select v-model="orderState.nextStatus">
                      <option v-for="status in orderStatusOptions" :key="status" :value="status">
                        {{ orderStatusLabel(status) }}
                      </option>
                    </select>
                  </label>
                  <label class="ops-field">
                    <span>Комментарий</span>
                    <input v-model.trim="orderState.note" type="text" />
                  </label>
                </div>
                <div class="sticky-actions sticky-actions-inline">
                  <button class="button button-primary" type="submit" :disabled="isSubmitting || !canSubmitSelectedOrderStatus">
                    Сохранить статус
                  </button>
                </div>
                <p v-if="!canSubmitSelectedOrderStatus" class="inline-note">
                  Для текущей роли изменение статуса этого заказа недоступно.
                </p>
              </form>
            </section>

            <section class="section-block">
              <div class="section-head">
                <div>
                  <h3>Оплата и возвраты</h3>
                  <p>{{ orderState.detail.order.paymentSummary ? 'YooKassa · полная предоплата' : 'Платёж ещё не создан' }}</p>
                </div>
                <button
                  v-if="canRefundSelectedOrder"
                  class="button button-danger"
                  type="button"
                  :disabled="isSubmitting"
                  @click="refundSelectedOrder({ full: true })"
                >
                  Вернуть остаток
                </button>
              </div>

              <dl v-if="orderState.detail.order.paymentSummary" class="definition-list">
                <div>
                  <dt>Статус</dt>
                  <dd>{{ orderState.detail.order.paymentSummary.status }}</dd>
                </div>
                <div>
                  <dt>ID платежа</dt>
                  <dd>{{ orderState.detail.order.paymentSummary.providerPaymentId || 'Не указан' }}</dd>
                </div>
                <div>
                  <dt>Сумма платежа</dt>
                  <dd>{{ formatMinorMoney(orderState.detail.order.paymentSummary.amount?.amount, orderState.detail.order.paymentSummary.amount?.currency) }}</dd>
                </div>
                <div>
                  <dt>Доступно к возврату</dt>
                  <dd>{{ formatMinorMoney(orderState.detail.order.paymentSummary.refundableAmount?.amount, orderState.detail.order.paymentSummary.refundableAmount?.currency) }}</dd>
                </div>
                <div>
                  <dt>Чек 54-ФЗ</dt>
                  <dd v-if="orderState.detail.order.paymentSummary.receiptUrl">
                    <a :href="orderState.detail.order.paymentSummary.receiptUrl" target="_blank" rel="noreferrer">Открыть чек</a>
                  </dd>
                  <dd v-else>{{ orderState.detail.order.paymentSummary.receiptRegistration || 'Формируется в YooKassa' }}</dd>
                </div>
              </dl>
              <div v-else class="empty-inline">Заказ ожидает создания платёжной сессии.</div>

              <div v-if="orderState.detail.order.paymentSummary?.refunds?.length" class="card-list card-list-compact">
                <article v-for="refund in orderState.detail.order.paymentSummary.refunds" :key="refund.id || refund.refundId" class="list-card">
                  <div class="list-card-head">
                    <strong>{{ formatMinorMoney(refund.amount?.amount, refund.amount?.currency) }}</strong>
                    <span class="pill pill-neutral">{{ refund.status }}</span>
                  </div>
                  <p class="list-card-slug">{{ refund.refundId }}</p>
                  <div class="list-card-meta">
                    <span>{{ formatDateTime(refund.refundDate) }}</span>
                    <span>{{ refund.items?.length || 0 }} строк</span>
                  </div>
                </article>
              </div>

              <form v-if="canRefundSelectedOrder" class="editor-form" @submit.prevent="refundSelectedOrder({ full: false })">
                <div class="section-head section-head-compact">
                  <div>
                    <h3>Частичный возврат</h3>
                    <p>Укажите количество по строкам заказа. Сумма необязательна: если оставить пустой, API рассчитает её по оплаченной цене строки.</p>
                  </div>
                </div>
                <div class="card-list card-list-compact">
                  <template v-for="item in orderState.detail.order.items || []" :key="`refund-${item.id}`">
                    <article v-if="item.id && orderState.refundForms[item.id]" class="list-card">
                      <div class="list-card-head">
                        <strong>{{ item.productName || item.variantName || item.variantId }}</strong>
                        <span class="pill pill-neutral">{{ item.quantity }} шт.</span>
                      </div>
                      <div class="form-grid">
                        <label class="ops-field">
                          <span>Количество к возврату</span>
                          <input v-model.number="orderState.refundForms[item.id].quantity" type="number" min="0" :max="item.quantity" step="1" />
                        </label>
                        <label class="ops-field">
                          <span>Сумма, ₽</span>
                          <input v-model.number="orderState.refundForms[item.id].amount" type="number" min="0" step="0.01" />
                        </label>
                      </div>
                    </article>
                  </template>
                </div>
                <div class="sticky-actions sticky-actions-inline">
                  <button class="button button-danger" type="submit" :disabled="isSubmitting">
                    Создать частичный возврат
                  </button>
                </div>
              </form>
            </section>

            <section class="section-block">
              <div class="section-head">
                <div>
                  <h3>Уведомления</h3>
                  <p>{{ orderState.detail.order.receiptEmail || 'Email не указан' }}</p>
                </div>
              </div>
              <dl class="definition-list">
                <div>
                  <dt>Email</dt>
                  <dd>{{ orderState.detail.order.receiptEmail || 'Не указан' }}</dd>
                </div>
                <div>
                  <dt>Отправление</dt>
                  <dd v-if="orderState.detail.shipment">
                    {{ orderState.detail.shipment.carrier }} · {{ orderState.detail.shipment.trackingNumber }}
                  </dd>
                  <dd v-else>Не создано</dd>
                </div>
              </dl>
            </section>

            <section v-if="canManageSelectedOrderRma" class="section-block">
              <div class="section-head">
                <div>
                  <h3>RMA</h3>
                  <p>{{ selectedOrderRmaRequests.length }} запросов</p>
                </div>
              </div>

              <form class="editor-form" @submit.prevent="createRmaRequest">
                <div class="form-grid">
                  <label class="ops-field">
                    <span>Причина</span>
                    <input v-model.trim="orderState.rmaReason" type="text" />
                  </label>
                  <label class="ops-field">
                    <span>Решение клиента</span>
                    <input v-model.trim="orderState.rmaDesiredResolution" type="text" />
                  </label>
                </div>
                <div class="sticky-actions sticky-actions-inline">
                  <button class="button button-secondary" type="submit" :disabled="isSubmitting">
                    Создать RMA
                  </button>
                </div>
              </form>

              <div v-if="selectedOrderRmaRequests.length" class="card-list card-list-compact">
                <article v-for="rma in selectedOrderRmaRequests" :key="rma.id" class="list-card">
                  <div class="list-card-head">
                    <strong>{{ rma.rmaNumber }}</strong>
                    <span class="pill pill-neutral">{{ rmaStatusLabel(rma.status) }}</span>
                  </div>
                  <div class="list-card-meta">
                    <span>{{ rma.reason || 'Причина не указана' }}</span>
                    <span>{{ rma.managerComment || 'Комментарий менеджера не указан' }}</span>
                  </div>
                  <form v-if="canDecideSelectedOrderRma" class="editor-form rma-decision-form" @submit.prevent="decideRmaRequest(rma)">
                    <div class="form-grid">
                      <label class="ops-field ops-field-required">
                        <span>Решение</span>
                        <select v-model="orderState.rmaDecisionForms[rma.id].status">
                          <option value="APPROVED">Одобрить</option>
                          <option value="REJECTED">Отклонить</option>
                        </select>
                      </label>
                      <label class="ops-field">
                        <span>Комментарий</span>
                        <input v-model.trim="orderState.rmaDecisionForms[rma.id].comment" type="text" />
                      </label>
                    </div>
                    <div class="sticky-actions sticky-actions-inline">
                      <button class="button button-primary" type="submit" :disabled="isSubmitting">
                        Записать решение
                      </button>
                    </div>
                  </form>
                </article>
              </div>
              <div v-else class="empty-inline">RMA запросов по заказу нет.</div>
            </section>

            <section class="section-block">
              <div class="section-head">
                <div>
                  <h3>Состав</h3>
                  <p>{{ orderState.detail.order.items?.length || 0 }} строк</p>
                </div>
              </div>
              <div v-if="orderState.detail.order.items?.length" class="card-list">
                <article v-for="item in orderState.detail.order.items" :key="item.id" class="list-card">
                  <div class="list-card-head">
                    <strong>{{ item.productName || item.variantName || item.variantId }}</strong>
                    <span class="pill pill-neutral">{{ item.quantity }} шт.</span>
                  </div>
                  <p class="list-card-slug">{{ item.sku || item.variantId }}</p>
                  <div class="list-card-meta">
                    <span>{{ formatMoney(item.unitPrice) }}</span>
                  </div>
                </article>
              </div>
            </section>

            <section class="section-block">
              <div class="section-head">
                <div>
                  <h3>История статусов</h3>
                  <p>{{ orderState.detail.history?.length || 0 }} событий</p>
                </div>
              </div>
              <div v-if="orderState.detail.history?.length" class="card-list">
                <article v-for="event in orderState.detail.history" :key="event.id" class="list-card">
                  <div class="list-card-head">
                    <strong>{{ orderStatusLabel(event.previousStatus) || 'Создан' }} → {{ orderStatusLabel(event.nextStatus) }}</strong>
                    <span class="pill pill-neutral">{{ formatDateTime(event.createdAt) }}</span>
                  </div>
                  <div class="list-card-meta">
                    <span>{{ event.actor || 'system' }}</span>
                    <span>{{ event.note || 'Без комментария' }}</span>
                  </div>
                </article>
              </div>
              <div v-else class="empty-inline">История пока пуста.</div>
            </section>
          </section>
        </template>

        <template v-else-if="activeTab === 'imports'">
          <section class="detail-card">
            <header class="detail-header">
              <div>
                <p class="detail-kicker">Импорт</p>
                <h2>Excel / CSV загрузка</h2>
                <p class="detail-subtitle">Dry-run проверяет строки до применения к каталогу и остаткам.</p>
              </div>
            </header>

            <form class="editor-form" @submit.prevent="dryRunImport">
              <label class="ops-field ops-field-required">
                <span>Файл</span>
                <input type="file" accept=".xlsx,.xls,.csv" @change="onImportFileSelected" />
              </label>

              <section class="selector-card">
                <div class="selector-card-head">
                  <div>
                    <h3>Маппинг колонок</h3>
                    <p>По умолчанию используются sku, product_name, product_slug, variant_name, price, stock, currency.</p>
                  </div>
                </div>
                <div class="form-grid form-grid-three">
                  <label v-for="field in importMappingFields" :key="field.key" class="ops-field">
                    <span>{{ field.label }}</span>
                    <input v-model.trim="importState.mapping[field.key]" type="text" />
                  </label>
                </div>
              </section>

              <div class="sticky-actions">
                <button class="button button-primary" type="submit" :disabled="isSubmitting || !importState.file">
                  Проверить файл
                </button>
                <button class="button button-secondary" type="button" :disabled="isSubmitting || !importState.dryRun?.job" @click="commitImport">
                  Применить импорт
                </button>
              </div>
            </form>

            <section v-if="importState.dryRun" class="section-block">
              <div class="section-head">
                <div>
                  <h3>Результат проверки</h3>
                  <p>{{ importState.dryRun.job.validRows }} валидных · {{ importState.dryRun.job.invalidRows }} ошибок</p>
                </div>
              </div>
              <div class="card-list">
                <article v-for="row in importState.dryRun.rows.slice(0, 50)" :key="row.id" class="list-card">
                  <div class="list-card-head">
                    <strong>Строка {{ row.rowNumber }} · {{ row.sku || 'без SKU' }}</strong>
                    <span class="pill" :class="row.valid ? 'pill-positive' : 'pill-muted'">{{ row.valid ? 'OK' : 'Ошибка' }}</span>
                  </div>
                  <p class="list-card-slug">{{ row.productName || row.errorMessage }}</p>
                  <div v-if="row.errorMessage" class="list-card-meta">
                    <span>{{ row.errorMessage }}</span>
                  </div>
                </article>
              </div>
            </section>
          </section>
        </template>

        <template v-else-if="activeTab === 'promotions'">
          <section class="detail-card">
            <header class="detail-header">
              <div>
                <p class="detail-kicker">Акции</p>
                <h2>{{ promotionState.mode === 'promoCode' ? 'Промокод' : 'Акция' }}</h2>
                <p class="detail-subtitle">Directus редактирует только backend-акции. Пороговые скидки DSC-01 фиксированы в API.</p>
              </div>
              <div class="detail-header-actions">
                <button class="button button-secondary" type="button" @click="startCreatePromotion">
                  Акция
                </button>
                <button class="button button-secondary" type="button" @click="startCreatePromoCode">
                  Промокод
                </button>
              </div>
            </header>

            <form v-if="promotionState.mode !== 'promoCode'" class="editor-form" @submit.prevent="submitPromotion">
              <div class="form-grid">
                <label class="ops-field ops-field-required">
                  <span>Название</span>
                  <input v-model.trim="promotionForm.name" type="text" />
                </label>
                <div class="inline-note">
                  Тип акции: PRODUCT_SALE. Скидки по сумме корзины: 50 000 ₽ → 5%, 65 000 ₽ → 7%, 100 000 ₽ → 10%.
                </div>
              </div>
              <div class="form-grid form-grid-three">
                <label class="ops-field">
                  <span>Статус</span>
                  <select v-model="promotionForm.status">
                    <option value="ACTIVE">ACTIVE</option>
                    <option value="DRAFT">DRAFT</option>
                    <option value="INACTIVE">INACTIVE</option>
                  </select>
                </label>
                <label class="ops-field">
                  <span>Начало</span>
                  <input v-model="promotionForm.startsAt" type="datetime-local" />
                </label>
                <label class="ops-field">
                  <span>Окончание</span>
                  <input v-model="promotionForm.endsAt" type="datetime-local" />
                </label>
              </div>
              <div class="form-grid">
                <label class="ops-field">
                  <span>Цена распродажи, ₽</span>
                  <input v-model.number="promotionForm.salePriceAmount" type="number" min="0" step="0.01" />
                </label>
                <label class="ops-field">
                  <span>Скидка, %</span>
                  <input v-model.number="promotionForm.discountPercent" type="number" min="0" max="100" step="1" />
                </label>
              </div>
              <section class="selector-card">
                <div class="selector-card-head">
                  <div>
                    <h3>Цели распродажи</h3>
                    <p>Можно указать несколько товаров, вариантов, категорий или брендов.</p>
                  </div>
                  <button class="button button-secondary" type="button" @click="addPromotionTarget">
                    Добавить цель
                  </button>
                </div>
                <div v-if="promotionForm.targets.length" class="spec-item-list">
                  <div
                    v-for="(target, targetIndex) in promotionForm.targets"
                    :key="`promotion-target-${targetIndex}`"
                    class="spec-item-row"
                  >
                    <label class="ops-field">
                      <span>Тип цели</span>
                      <select v-model="target.targetKind">
                        <option value="VARIANT">VARIANT</option>
                        <option value="PRODUCT">PRODUCT</option>
                        <option value="BRAND">BRAND</option>
                        <option value="CATEGORY">CATEGORY</option>
                      </select>
                    </label>
                    <label class="ops-field">
                      <span>Ключ цели</span>
                      <input v-model.trim="target.targetKey" type="text" placeholder="sku, slug или UUID" />
                    </label>
                    <button
                      class="button button-danger button-small spec-item-remove"
                      type="button"
                      @click="removePromotionTarget(targetIndex)"
                    >
                      Удалить
                    </button>
                  </div>
                </div>
                <div v-else class="empty-inline">Без целей акция применяется ко всем товарам.</div>
              </section>
              <label class="ops-field">
                <span>Описание</span>
                <textarea v-model="promotionForm.description" rows="4" />
              </label>
              <div class="sticky-actions">
                <button class="button button-primary" type="submit" :disabled="isSubmitting">
                  {{ promotionForm.id ? 'Сохранить акцию' : 'Создать акцию' }}
                </button>
                <button v-if="promotionForm.id" class="button button-danger" type="button" :disabled="isSubmitting" @click="deletePromotion">
                  Удалить
                </button>
              </div>
            </form>

            <form v-else class="editor-form" @submit.prevent="submitPromoCode">
              <div class="form-grid">
                <label class="ops-field ops-field-required">
                  <span>Код</span>
                  <input v-model.trim="promoCodeForm.code" type="text" />
                </label>
                <label class="ops-field">
                  <span>Статус</span>
                  <select v-model="promoCodeForm.status">
                    <option value="ACTIVE">ACTIVE</option>
                    <option value="INACTIVE">INACTIVE</option>
                  </select>
                </label>
              </div>
              <div class="form-grid form-grid-three">
                <label class="ops-field">
                  <span>Скидка, %</span>
                  <input v-model.number="promoCodeForm.discountPercent" type="number" min="0" max="100" step="1" />
                </label>
                <label class="ops-field">
                  <span>Скидка суммой, ₽</span>
                  <input v-model.number="promoCodeForm.discountAmount" type="number" min="0" step="0.01" />
                </label>
                <label class="ops-field">
                  <span>Порог, ₽</span>
                  <input v-model.number="promoCodeForm.thresholdAmount" type="number" min="0" step="0.01" />
                </label>
              </div>
              <div class="form-grid form-grid-three">
                <label class="ops-field">
                  <span>Начало</span>
                  <input v-model="promoCodeForm.startsAt" type="datetime-local" />
                </label>
                <label class="ops-field">
                  <span>Окончание</span>
                  <input v-model="promoCodeForm.endsAt" type="datetime-local" />
                </label>
                <label class="ops-field">
                  <span>Лимит</span>
                  <input v-model.number="promoCodeForm.maxRedemptions" type="number" min="0" step="1" />
                </label>
              </div>
              <label class="ops-field">
                <span>Описание</span>
                <textarea v-model="promoCodeForm.description" rows="4" />
              </label>
              <div class="sticky-actions">
                <button class="button button-primary" type="submit" :disabled="isSubmitting">
                  {{ promoCodeForm.id ? 'Сохранить промокод' : 'Создать промокод' }}
                </button>
                <button v-if="promoCodeForm.id" class="button button-danger" type="button" :disabled="isSubmitting" @click="deletePromoCode">
                  Удалить
                </button>
              </div>
            </form>
          </section>
        </template>

        <template v-else-if="activeTab === 'tax'">
          <section class="detail-card">
            <header class="detail-header">
              <div>
                <p class="detail-kicker">Налоги</p>
                <h2>{{ taxForm.id ? taxForm.name : 'Новый налоговый режим' }}</h2>
                <p class="detail-subtitle">Активный режим используется при создании чеков YooKassa.</p>
              </div>
            </header>
            <form class="editor-form" @submit.prevent="submitTax">
              <div class="form-grid">
                <label class="ops-field ops-field-required">
                  <span>Название</span>
                  <input v-model.trim="taxForm.name" type="text" />
                </label>
                <label class="ops-field">
                  <span>Статус</span>
                  <select v-model="taxForm.status">
                    <option value="ACTIVE">ACTIVE</option>
                    <option value="INACTIVE">INACTIVE</option>
                  </select>
                </label>
              </div>
              <div class="form-grid form-grid-three">
                <label class="ops-field">
                  <span>Код СНО YooKassa</span>
                  <input v-model.number="taxForm.taxSystemCode" type="number" min="1" step="1" />
                </label>
                <label class="ops-field">
                  <span>Код НДС YooKassa</span>
                  <input v-model.number="taxForm.vatCode" type="number" min="1" step="1" />
                </label>
                <label class="ops-field">
                  <span>Ставка НДС, %</span>
                  <input v-model.number="taxForm.vatRatePercent" type="number" min="0" step="0.001" />
                </label>
              </div>
              <label class="ops-field ops-field-boolean">
                <span>Активность</span>
                <label class="ops-toggle">
                  <input v-model="taxForm.active" type="checkbox" />
                  <span>{{ taxForm.active ? 'Использовать в чеках' : 'Не использовать' }}</span>
                </label>
              </label>
              <div class="sticky-actions">
                <button class="button button-primary" type="submit" :disabled="isSubmitting">
                  {{ taxForm.id ? 'Сохранить режим' : 'Создать режим' }}
                </button>
                <button v-if="taxForm.id" class="button button-danger" type="button" :disabled="isSubmitting" @click="deleteTax">
                  Удалить
                </button>
              </div>
            </form>
          </section>
        </template>

        <template v-else-if="activeTab === 'analytics'">
          <section class="detail-card">
            <header class="detail-header">
              <div>
                <p class="detail-kicker">Аналитика</p>
                <h2>Менеджеры и ссылки оплаты</h2>
                <p class="detail-subtitle">Комиссия менеджера фиксирована на уровне 3% от оплаченной суммы.</p>
              </div>
            </header>
            <div class="form-grid form-grid-three">
              <label class="ops-field">
                <span>С</span>
                <input v-model="analyticsState.from" type="datetime-local" />
              </label>
              <label class="ops-field">
                <span>По</span>
                <input v-model="analyticsState.to" type="datetime-local" />
              </label>
              <label v-if="!isManagerRole" class="ops-field">
                <span>Менеджер</span>
                <input v-model.trim="analyticsState.manager" type="text" />
              </label>
              <div v-else class="ops-field ops-field-readonly">
                <span>Менеджер</span>
                <strong>Только ваши показатели</strong>
              </div>
            </div>
            <div class="sticky-actions sticky-actions-inline">
              <button class="button button-primary" type="button" :disabled="isSubmitting" @click="loadAnalytics">
                Обновить аналитику
              </button>
            </div>

            <div class="metrics-row">
              <article class="metric-card">
                <span>Ссылок отправлено</span>
                <strong>{{ analyticsState.paymentLinks.sent || 0 }}</strong>
              </article>
              <article class="metric-card">
                <span>Оплачено</span>
                <strong>{{ analyticsState.paymentLinks.paid || 0 }}</strong>
              </article>
              <article class="metric-card">
                <span>Конверсия</span>
                <strong>{{ formatPercent(analyticsState.paymentLinks.conversionRate) }}</strong>
              </article>
            </div>

            <section class="section-block">
              <div class="section-head">
                <div>
                  <h3>Менеджеры</h3>
                  <p>{{ analyticsState.managerRows.length }} строк</p>
                </div>
              </div>
              <div class="card-list">
                <article v-for="row in analyticsState.managerRows" :key="row.managerSubject" class="list-card">
                  <div class="list-card-head">
                    <strong>{{ row.managerSubject }}</strong>
                    <span class="pill pill-neutral">{{ row.paidOrders }} paid</span>
                  </div>
                  <div class="list-card-meta">
                    <span>Заказов {{ row.totalOrders }}</span>
                    <span>{{ formatMoney(row.paidAmount) }}</span>
                    <span>Комиссия {{ formatMoney(row.commission) }}</span>
                  </div>
                </article>
              </div>
            </section>

            <section class="section-block">
              <div class="section-head">
                <div>
                  <h3>Ссылки оплаты</h3>
                  <p>{{ analyticsState.paymentLinks.rows?.length || 0 }} строк</p>
                </div>
              </div>
              <div class="card-list">
                <article v-for="link in analyticsState.paymentLinks.rows || []" :key="link.id" class="list-card">
                  <div class="list-card-head">
                    <strong>{{ link.managerSubject || link.managerEmail || 'Менеджер' }}</strong>
                    <span class="pill" :class="link.paid ? 'pill-positive' : 'pill-muted'">{{ link.paid ? 'paid' : link.status }}</span>
                  </div>
                  <p class="list-card-slug">{{ link.orderId }}</p>
                  <div class="list-card-meta">
                    <span>{{ formatDateTime(link.sentAt || link.createdAt) }}</span>
                  </div>
                </article>
              </div>
            </section>
          </section>
        </template>

        <template v-else-if="activeTab === 'alerts'">
          <section class="detail-card">
            <header class="detail-header">
              <div>
                <p class="detail-kicker">Алерты</p>
                <h2>Низкие остатки</h2>
                <p class="detail-subtitle">Список виден администраторам и контент-менеджерам.</p>
              </div>
            </header>
            <div class="metrics-row">
              <article class="metric-card">
                <span>Порог</span>
                <strong>{{ alertState.threshold }}</strong>
              </article>
              <article class="metric-card">
                <span>SKU ниже порога</span>
                <strong>{{ alertState.rows.length }}</strong>
              </article>
            </div>
            <div v-if="alertState.rows.length" class="card-list">
              <article v-for="row in alertState.rows" :key="row.variantId" class="list-card">
                <div class="list-card-head">
                  <strong>{{ row.productName }}</strong>
                  <span class="pill pill-muted">{{ row.stock }} шт.</span>
                </div>
                <p class="list-card-slug">{{ row.productSlug }} · {{ row.variantName }} · {{ row.sku }}</p>
              </article>
            </div>
            <div v-else class="empty-inline">Нет SKU ниже порога.</div>
          </section>
        </template>
        </main>
      </section>
    </div>
  </private-view>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useApi } from '@directus/extensions-sdk';

const api = useApi();

const tabs = [
  { id: 'products', label: 'Товары' },
  { id: 'categories', label: 'Категории' },
  { id: 'brands', label: 'Бренды' },
  { id: 'inventory', label: 'Остатки' },
  { id: 'orders', label: 'Заказы' },
  { id: 'imports', label: 'Импорт' },
  { id: 'promotions', label: 'Акции' },
  { id: 'tax', label: 'Налоги' },
  { id: 'analytics', label: 'Аналитика' },
  { id: 'alerts', label: 'Алерты' },
];

const ROLE_IDS = {
  admin: [
    'admin',
    'administrator',
    'администратор',
    'администратор cms',
    '4c4cc8d0-9b7f-4d56-84d2-1d64f5f10001',
  ],
  manager: ['manager', 'менеджер', '4c4cc8d0-9b7f-4d56-84d2-1d64f5f10006'],
  picker: ['picker', 'сборщик', '4c4cc8d0-9b7f-4d56-84d2-1d64f5f10007'],
  content: [
    'content_manager',
    'content-manager',
    'контент-менеджер',
    '4c4cc8d0-9b7f-4d56-84d2-1d64f5f10008',
    '4c4cc8d0-9b7f-4d56-84d2-1d64f5f10002',
    '4c4cc8d0-9b7f-4d56-84d2-1d64f5f10004',
    '4c4cc8d0-9b7f-4d56-84d2-1d64f5f10005',
  ],
};

const TAB_ACCESS = {
  products: ['admin', 'content'],
  categories: ['admin', 'content'],
  brands: ['admin', 'content'],
  inventory: ['admin', 'content'],
  orders: ['admin', 'manager', 'picker'],
  imports: ['admin', 'content'],
  promotions: ['admin', 'content'],
  tax: ['admin'],
  analytics: ['admin', 'manager'],
  alerts: ['admin', 'content'],
};

const productDetailTabs = [
  { id: 'main', label: 'Основное' },
  { id: 'variants', label: 'Варианты' },
  { id: 'media', label: 'Медиа' },
  { id: 'merch', label: 'Мерчандайзинг' },
];

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

const activeTab = ref('products');
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
});
const navigationCounts = reactive({
  products: 0,
  categories: 0,
  brands: 0,
  inventory: 0,
  orders: 0,
  imports: 0,
  promotions: 0,
  tax: 0,
  analytics: 0,
  alerts: 0,
});

const loading = reactive({
  products: false,
  categories: false,
  brands: false,
  inventory: false,
  orders: false,
  imports: false,
  promotions: false,
  tax: false,
  analytics: false,
  alerts: false,
  activePromotions: false,
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
  from: '',
  to: '',
  items: [],
  selectedId: '',
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
const selectedOrder = computed(() => orderState.detail?.order || null);
const selectedOrderRmaRequests = computed(() => orderState.detail?.rmaRequests || []);
const orderStatusOptions = computed(() => (
  roleKind.value === 'picker'
    ? PICKER_TARGET_STATUS_OPTIONS
    : ORDER_STATUS_OPTIONS
));
const canClaimSelectedOrder = computed(() => {
  const order = selectedOrder.value;
  return Boolean(order && ['admin', 'manager'].includes(roleKind.value) && !isOrderAssigned(order));
});
const canClearSelectedOrder = computed(() => Boolean(
  selectedOrder.value && roleKind.value === 'admin' && isOrderAssigned(selectedOrder.value)
));
const canSubmitSelectedOrderStatus = computed(() => {
  const order = selectedOrder.value;
  if (!order || !orderState.nextStatus) {
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
  if (!order) {
    return false;
  }
  if (roleKind.value === 'admin') {
    return true;
  }
  return roleKind.value === 'manager' && (!isOrderAssigned(order) || isOrderAssignedToCurrentUser(order));
});
const canDecideSelectedOrderRma = computed(() => {
  const order = selectedOrder.value;
  if (!order) {
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
  return true;
});

function tabCount(tabId) {
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

function normalizeRoleToken(value) {
  return String(value || '').trim().toLowerCase();
}

function orderManagerLabel(order) {
  return order?.managerEmail || order?.managerSubject || 'Не назначен';
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

function roleMatches(tokens, roleKey) {
  return (ROLE_IDS[roleKey] || []).some((entry) => tokens.has(normalizeRoleToken(entry)));
}

function resolveRoleKind(state) {
  if (['admin', 'manager', 'picker', 'content'].includes(state.roleKind)) {
    return state.roleKind;
  }

  const tokens = new Set([
    normalizeRoleToken(state.roleId),
    normalizeRoleToken(state.roleName),
  ].filter(Boolean));
  if (state.roleAdminAccess || roleMatches(tokens, 'admin')) {
    return 'admin';
  }
  if (roleMatches(tokens, 'manager')) {
    return 'manager';
  }
  if (roleMatches(tokens, 'picker')) {
    return 'picker';
  }
  if (roleMatches(tokens, 'content')) {
    return 'content';
  }
  return 'unknown';
}

function canAccessTab(tabId) {
  const allowedRoles = TAB_ACCESS[tabId] || ['admin'];
  return allowedRoles.includes(roleKind.value);
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

async function bridgeRequest(path, options = {}) {
  const response = await api.request({
    url: `/storefront-ops-bridge${path}`,
    method: options.method || 'GET',
    params: options.params,
    data: options.data,
    headers: options.headers,
  });
  return response.data;
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
        resetInventoryEditor();
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

async function loadOrders() {
  loading.orders = true;
  try {
    const response = await bridgeRequest('/admin/orders', {
      params: compactParams({
        status: orderState.status,
        manager: orderState.manager,
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
        await loadOrderDetail(orderState.selectedId, { silent: true });
      } else {
        orderState.selectedId = '';
        orderState.detail = null;
      }
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
      startCreatePromotion();
    }
  } catch (error) {
    setError(error);
  } finally {
    loading.promotions = false;
  }
}

async function loadActivePromotions() {
  if (!canViewActivePromotions.value) {
    activePromotionState.items = [];
    activePromotionState.loaded = true;
    return;
  }
  loading.activePromotions = true;
  try {
    activePromotionState.items = await bridgeRequest('/admin/promotions/active') || [];
    activePromotionState.loaded = true;
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
      selectTax(taxState.items.find((item) => item.active) || taxState.items[0]);
    }
  } catch (error) {
    setError(error);
  } finally {
    loading.tax = false;
  }
}

async function loadAnalytics() {
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
    return;
  }
  if (!canAccessTab(nextTab)) {
    setInfo('Раздел скрыт для вашей роли Directus.');
    return;
  }
  if (!confirmDiscardChanges()) {
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
  resetVariantEditor();
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

function resetProductEditor() {
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
  resetVariantEditor();
  resetProductMediaEditor();
}

function resetVariantEditor() {
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
}

function resetProductMediaEditor() {
  productMediaForm.variantId = '';
  productMediaFiles.value = [];
}

function resetCategoryEditor() {
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
}

function resetBrandEditor() {
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
}

function resetInventoryEditor() {
  inventoryForm.variantId = inventoryState.selectedVariantId || '';
  inventoryForm.delta = 0;
  inventoryForm.reason = '';
  inventoryForm.idempotencyKey = nextIdempotencyKey();
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
    return;
  }
  clearMessages();
  resetProductEditor();
}

function startCreateCategory() {
  if (!confirmDiscardChanges()) {
    return;
  }
  clearMessages();
  resetCategoryEditor();
}

function startCreateBrand() {
  if (!confirmDiscardChanges()) {
    return;
  }
  clearMessages();
  resetBrandEditor();
}

async function selectProduct(id) {
  if (id === productState.selectedId && !productState.isCreating) {
    return;
  }
  if (!confirmDiscardChanges()) {
    return;
  }
  clearMessages();
  productState.panel = 'main';
  await loadProductDetail(id);
}

async function selectCategory(id) {
  if (id === categoryState.selectedId && !categoryState.isCreating) {
    return;
  }
  if (!confirmDiscardChanges()) {
    return;
  }
  clearMessages();
  await loadCategoryDetail(id);
}

async function selectBrand(id) {
  if (id === brandState.selectedId && !brandState.isCreating) {
    return;
  }
  if (!confirmDiscardChanges()) {
    return;
  }
  clearMessages();
  await loadBrandDetail(id);
}

function selectInventoryRow(variantId) {
  inventoryState.selectedVariantId = variantId;
  inventoryForm.variantId = variantId;
  inventoryForm.delta = 0;
  inventoryForm.reason = '';
  inventoryForm.idempotencyKey = nextIdempotencyKey();
}

function closeActiveDetail() {
  if (!confirmDiscardChanges()) {
    return;
  }
  if (activeTab.value === 'products') {
    productState.selectedId = '';
    productState.detail = null;
    productState.isCreating = false;
    resetVariantEditor();
    resetProductMediaEditor();
    return;
  }
  if (activeTab.value === 'categories') {
    categoryState.selectedId = '';
    categoryState.detail = null;
    categoryState.isCreating = false;
    categoryImageFile.value = null;
    return;
  }
  if (activeTab.value === 'brands') {
    brandState.selectedId = '';
    brandState.detail = null;
    brandState.isCreating = false;
    return;
  }
  inventoryState.selectedVariantId = '';
  resetInventoryEditor();
}

function setProductPanel(panel) {
  if (productState.panel === panel) {
    return;
  }
  if ((panel !== 'variants' && variantDirty.value) && !window.confirm('Есть несохранённые изменения варианта. Отбросить их?')) {
    return;
  }
  productState.panel = panel;
}

function loadVariantEditor(variant) {
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
  if (!productForm.id) return;
  if (!window.confirm(`Удалить товар «${productForm.name || productForm.slug}»?`)) {
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
    resetVariantEditor();
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
    resetVariantEditor();
    setSuccess(variantForm.id ? 'Вариант сохранён.' : 'Вариант добавлен.');
  } catch (error) {
    setError(error);
  } finally {
    isSubmitting.value = false;
  }
}

async function deleteVariant(variant) {
  if (!productForm.id || !variant?.id) return;
  if (!window.confirm(`Удалить вариант «${variant.name || variant.sku}»?`)) {
    return;
  }

  isSubmitting.value = true;
  clearMessages();
  try {
    await bridgeRequest(`/products/${productForm.id}/variants/${variant.id}`, { method: 'DELETE' });
    await loadProducts({ reloadSelected: false });
    await loadNavigationSummary();
    await loadProductDetail(productForm.id, { silent: true });
    resetVariantEditor();
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
  if (!categoryForm.id) return;
  if (!window.confirm(`Удалить категорию «${categoryForm.name || categoryForm.slug}»?`)) {
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
  if (!brandForm.id) return;
  if (!window.confirm(`Удалить бренд «${brandForm.name || brandForm.slug}»?`)) {
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

function selectImportJob(jobId) {
  importState.selectedJobId = jobId;
  const job = importState.jobs.find((entry) => entry.id === jobId);
  if (job && importState.dryRun?.job?.id !== jobId) {
    setInfo(`Выбран импорт ${job.fileName || job.id}. Повторное применение доступно из последнего dry-run.`);
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

function startCreatePromotion() {
  promotionState.mode = 'promotion';
  promotionState.selectedId = '';
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
}

function selectPromotion(promotion) {
  promotionState.mode = 'promotion';
  promotionState.selectedId = promotion.id;
  promotionState.selectedPromoCodeId = '';
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
}

function startCreatePromoCode() {
  promotionState.mode = 'promoCode';
  promotionState.selectedPromoCodeId = '';
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
}

function selectPromoCode(promoCode) {
  promotionState.mode = 'promoCode';
  promotionState.selectedPromoCodeId = promoCode.id;
  promotionState.selectedId = '';
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
}

function removePromotionTarget(index) {
  promotionForm.targets.splice(index, 1);
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
    return;
  }
  isSubmitting.value = true;
  clearMessages();
  try {
    await bridgeRequest(`/admin/promotions/${promotionForm.id}`, { method: 'DELETE' });
    await loadPromotions();
    startCreatePromotion();
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
    return;
  }
  isSubmitting.value = true;
  clearMessages();
  try {
    await bridgeRequest(`/admin/promo-codes/${promoCodeForm.id}`, { method: 'DELETE' });
    await loadPromotions();
    startCreatePromoCode();
    setSuccess('Промокод удалён.');
  } catch (error) {
    setError(error);
  } finally {
    isSubmitting.value = false;
  }
}

function startCreateTax() {
  taxState.selectedId = '';
  Object.assign(taxForm, {
    id: '',
    name: '',
    status: 'ACTIVE',
    taxSystemCode: 1,
    vatCode: 1,
    vatRatePercent: 0,
    active: false,
  });
}

function selectTax(tax) {
  taxState.selectedId = tax.id;
  Object.assign(taxForm, {
    id: tax.id || '',
    name: tax.name || '',
    status: tax.status || 'ACTIVE',
    taxSystemCode: Number(tax.taxSystemCode || 1),
    vatCode: Number(tax.vatCode || 1),
    vatRatePercent: Number(tax.vatRatePercent || 0),
    active: Boolean(tax.active),
  });
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
    selectTax(saved);
    setSuccess(taxForm.id ? 'Налоговый режим сохранён.' : 'Налоговый режим создан.');
  } catch (error) {
    setError(error);
  } finally {
    isSubmitting.value = false;
  }
}

async function deleteTax() {
  if (!taxForm.id || !window.confirm(`Удалить налоговый режим «${taxForm.name}»?`)) {
    return;
  }
  isSubmitting.value = true;
  clearMessages();
  try {
    await bridgeRequest(`/admin/tax-settings/${taxForm.id}`, { method: 'DELETE' });
    await loadTaxSettings();
    startCreateTax();
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
  if (!productForm.id) return;
  if (!window.confirm('Удалить это изображение?')) {
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
    }
  } catch (error) {
    setError(error);
  } finally {
    isSubmitting.value = false;
  }
}

async function refreshCurrentTab() {
  isRefreshing.value = true;
  clearMessages();
  try {
    if (activeTab.value === 'products') {
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

function filterCollection(items, query, getters) {
  if (!query) {
    return items || [];
  }
  const normalized = query.toLowerCase();
  return (items || []).filter((item) => {
    return getters.some((getter) => {
      const value = getter(item);
      return value && String(value).toLowerCase().includes(normalized);
    });
  });
}

function formatCategoryLabel(option) {
  const depth = Number(option.depth || 0);
  const prefix = depth > 0 ? `${'· '.repeat(depth)}` : '';
  return `${prefix}${option.name}`;
}

function overlayStatusLabel(status) {
  const value = String(status || '').toLowerCase();
  if (value === 'published') return 'Опубликован';
  if (value === 'in_review') return 'На проверке';
  if (value === 'archived') return 'В архиве';
  if (value === 'draft') return 'Черновик';
  return 'Без статуса';
}

function formatMoney(price) {
  if (!price?.amount && price?.amount !== 0) {
    return 'Цена не задана';
  }
  const currency = price.currency || 'RUB';
  return `${price.amount} ${currency}`;
}

function moneyMinorAmount(price) {
  const amount = Number(price?.amount ?? 0);
  return Number.isFinite(amount) ? amount : 0;
}

function formatMinorMoney(amount, currency = 'RUB') {
  if (amount === null || amount === undefined || amount === '') {
    return 'Не задано';
  }
  const numeric = Number(amount);
  if (!Number.isFinite(numeric)) {
    return 'Не задано';
  }
  return `${(numeric / 100).toLocaleString('ru-RU')} ${currency}`;
}

function formatDateTime(value) {
  if (!value) {
    return 'Не задано';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return 'Не задано';
  }
  return new Intl.DateTimeFormat('ru-RU', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(date);
}

function toIsoDateTime(value) {
  if (!value) {
    return null;
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

function toDatetimeLocal(value) {
  if (!value) {
    return '';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '';
  }
  const offset = date.getTimezoneOffset();
  const localDate = new Date(date.getTime() - offset * 60 * 1000);
  return localDate.toISOString().slice(0, 16);
}

function compactParams(params) {
  return Object.fromEntries(
    Object.entries(params || {}).filter(([, value]) => value !== null && value !== undefined && String(value).trim() !== '')
  );
}

function formatPercent(value) {
  const numeric = Number(value || 0) * 100;
  return `${numeric.toFixed(1)}%`;
}

function cloneSpecifications(value) {
  return (Array.isArray(value) ? value : []).map((section) => ({
    title: String(section?.title || ''),
    description: String(section?.description || ''),
    items: (Array.isArray(section?.items) ? section.items : []).map((item) => ({
      label: String(item?.label || ''),
      value: String(item?.value || ''),
    })),
  }));
}

function normalizeSpecificationsForPayload(value) {
  return (Array.isArray(value) ? value : [])
    .map((section) => ({
      title: normalizeNullableText(section?.title),
      description: normalizeNullableText(section?.description),
      items: (Array.isArray(section?.items) ? section.items : [])
        .map((item) => ({
          label: normalizeNullableText(item?.label),
          value: normalizeNullableText(item?.value),
        }))
        .filter((item) => item.label && item.value),
    }))
    .filter((section) => section.title || section.description || section.items.length);
}

function addSpecificationSection() {
  productForm.specifications.push({
    title: '',
    description: '',
    items: [],
  });
}

function removeSpecificationSection(index) {
  productForm.specifications.splice(index, 1);
}

function addSpecificationItem(sectionIndex) {
  const section = productForm.specifications[sectionIndex];
  if (!section) {
    return;
  }
  section.items.push({
    label: '',
    value: '',
  });
}

function removeSpecificationItem(sectionIndex, itemIndex) {
  const section = productForm.specifications[sectionIndex];
  if (!section) {
    return;
  }
  section.items.splice(itemIndex, 1);
}

function normalizeNullableNumber(value) {
  if (value === '' || value === null || value === undefined) {
    return null;
  }
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : null;
}

function minorToMajor(value) {
  const numeric = normalizeNullableNumber(value);
  return numeric === null ? null : numeric / 100;
}

function majorToMinor(value) {
  const numeric = normalizeNullableNumber(value);
  return numeric === null ? null : Math.round(numeric * 100);
}

function normalizeNullableText(value) {
  const trimmed = String(value || '').trim();
  return trimmed ? trimmed : null;
}

function nextIdempotencyKey() {
  return crypto.randomUUID();
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
  activeTab.value = params.get('tab') || stored.activeTab || 'products';
  productState.selectedId = params.get('product') || stored.productId || '';
  categoryState.selectedId = params.get('category') || stored.categoryId || '';
  brandState.selectedId = params.get('brand') || stored.brandId || '';
  inventoryState.selectedVariantId = params.get('variant') || stored.inventoryVariantId || '';
  productState.panel = params.get('panel') || stored.productPanel || 'main';
  normalizeActiveTab({ notify: Boolean(params.get('tab')) });
}

async function mountModuleNavigation() {
  const maxAttempts = 24;
  for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
    await nextTick();
    if (document.querySelector('#module-navigation')) {
      navigationTarget.value = '#module-navigation';
      return;
    }
    await new Promise((resolve) => window.requestAnimationFrame(resolve));
  }
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
  resetInventoryEditor();
});

onBeforeUnmount(() => {
  navigationTarget.value = '';
  document.body.classList.remove('storefront-ops-view');
  window.removeEventListener('beforeunload', handleBeforeUnload);
});
</script>

<style scoped>
.workspace {
  display: grid;
  gap: 14px;
  inline-size: 100%;
  min-block-size: calc(100vh - 54px);
  min-inline-size: 0;
  padding: 0 12px 18px;
}

.status-banner {
  align-items: flex-start;
  background: color-mix(in srgb, var(--theme--primary) 10%, var(--theme--background-subdued));
  border: 1px solid color-mix(in srgb, var(--theme--primary) 24%, var(--theme--border-color));
  border-radius: 14px;
  display: grid;
  gap: 4px;
  padding: 12px 14px;
}

.status-banner-error {
  background: color-mix(in srgb, var(--theme--danger) 9%, var(--theme--background-subdued));
  border-color: color-mix(in srgb, var(--theme--danger) 30%, var(--theme--border-color));
}

.status-banner-success {
  background: color-mix(in srgb, var(--green) 12%, var(--theme--background-subdued));
  border-color: color-mix(in srgb, var(--green) 28%, var(--theme--border-color));
}

.surface-panel,
.detail-card,
.empty-detail {
  background: var(--theme--background-subdued);
  border: 1px solid var(--theme--border-color);
  border-radius: 16px;
}

.pane-tabs {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.pane-tabs-navigation {
  grid-template-columns: 1fr;
  padding: 12px 10px;
}

.pane-tabs-inline {
  grid-template-columns: repeat(auto-fit, minmax(132px, 1fr));
}

.pane-tab {
  align-items: center;
  background: var(--theme--background);
  border: 1px solid var(--theme--border-color);
  border-radius: 14px;
  color: inherit;
  display: flex;
  gap: 10px;
  justify-content: space-between;
  min-height: 50px;
  padding: 10px 12px;
  text-align: left;
}

.pane-tab span {
  font-weight: 700;
}

.pane-tab small {
  color: var(--theme--foreground-subdued);
  font-size: 12px;
}

.pane-tab.active {
  border-color: var(--theme--primary);
  box-shadow: 0 0 0 1px var(--theme--primary);
}

.access-context {
  align-items: center;
  background: var(--theme--background-subdued);
  border: 1px solid var(--theme--border-color);
  border-radius: 12px;
  color: var(--theme--foreground-subdued);
  display: flex;
  gap: 10px;
  justify-content: space-between;
  padding: 10px 12px;
}

.access-context strong {
  color: var(--theme--foreground);
}

.workspace-shell {
  align-items: start;
  display: grid;
  gap: 14px;
  inline-size: 100%;
  min-block-size: 0;
  min-inline-size: 0;
}

.workspace-list {
  align-content: start;
  block-size: 100%;
  display: grid;
  gap: 12px;
  min-block-size: 0;
  min-inline-size: 0;
  overflow: auto;
  padding: 14px;
}

.section-block-compact {
  gap: 8px;
}

.card-list-compact {
  max-block-size: 220px;
  overflow: auto;
}

.workspace-detail {
  align-content: start;
  block-size: 100%;
  display: grid;
  gap: 10px;
  inline-size: 100%;
  min-block-size: 0;
  min-width: 0;
  overflow: hidden;
}

.detail-card {
  inline-size: 100%;
  max-inline-size: none;
}

.pane-header,
.detail-header,
.section-head,
.selector-card-head,
.merch-card-head,
.spec-section-head,
.spec-items-head {
  align-items: flex-start;
  display: flex;
  gap: 12px;
  justify-content: space-between;
}

.pane-header h2,
.detail-header h2,
.section-head h3,
.selector-card h3,
.merch-card h3 {
  margin: 0;
}

.pane-header p,
.detail-header p,
.section-head p,
.selector-card-head p,
.merch-card-head p,
.spec-section-head p,
.spec-items-head p {
  color: var(--theme--foreground-subdued);
  margin: 4px 0 0;
}

.detail-header-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.search-field,
.ops-field {
  display: grid;
  gap: 6px;
}

.search-field span,
.ops-field > span {
  color: var(--theme--foreground-subdued);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.ops-field-required > span::after {
  color: var(--theme--danger);
  content: ' *';
}

.search-field input,
.ops-field input,
.ops-field select,
.ops-field textarea {
  appearance: none;
  background: var(--theme--background);
  border: 1px solid var(--theme--border-color);
  border-radius: 12px;
  color: inherit;
  min-height: 44px;
  padding: 10px 12px;
}

.ops-field textarea {
  min-height: 112px;
  resize: vertical;
}

.ops-field-boolean {
  align-content: start;
}

.ops-toggle {
  align-items: center;
  background: var(--theme--background);
  border: 1px solid var(--theme--border-color);
  border-radius: 12px;
  display: flex;
  gap: 10px;
  min-height: 44px;
  padding: 10px 12px;
}

.ops-toggle span {
  font-size: 14px;
  font-weight: 500;
  letter-spacing: 0;
  text-transform: none;
}

.card-list,
.variant-list,
.choice-list,
.media-grid {
  display: grid;
  gap: 8px;
}

.list-card {
  background: var(--theme--background);
  border: 1px solid var(--theme--border-color-subdued);
  border-radius: 14px;
  color: inherit;
  display: grid;
  gap: 6px;
  padding: 12px;
  text-align: left;
}

.list-card.active {
  border-color: var(--theme--primary);
  box-shadow: 0 0 0 1px var(--theme--primary);
}

.list-card-head {
  align-items: flex-start;
  display: flex;
  gap: 8px;
  justify-content: space-between;
}

.list-card-head strong {
  line-height: 1.3;
}

.list-card-slug,
.detail-subtitle,
.media-card-url {
  color: var(--theme--foreground-subdued);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  margin: 0;
  overflow-wrap: anywhere;
}

.list-card-meta,
.list-card-footer,
.variant-card-meta,
.media-card-meta {
  color: var(--theme--foreground-subdued);
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  font-size: 12px;
}

.pill,
.overlay-chip {
  align-items: center;
  border-radius: 999px;
  display: inline-flex;
  font-size: 11px;
  font-weight: 700;
  min-height: 24px;
  padding: 3px 9px;
}

.pill-positive {
  background: color-mix(in srgb, var(--green) 15%, var(--theme--background));
  color: var(--green);
}

.pill-muted {
  background: var(--theme--background-subdued);
  color: var(--theme--foreground-subdued);
}

.pill-neutral,
.overlay-chip {
  background: color-mix(in srgb, var(--theme--primary) 10%, var(--theme--background));
  color: var(--theme--primary);
}

.detail-card,
.empty-detail {
  block-size: 100%;
  display: grid;
  gap: 14px;
  inline-size: 100%;
  max-inline-size: none;
  min-block-size: 0;
  overflow: auto;
  padding: 14px;
}

.detail-content,
.editor-form,
.section-block,
.merch-card,
.selector-card,
.media-inline-panel,
.context-grid {
  display: grid;
  gap: 14px;
  inline-size: 100%;
  max-inline-size: none;
}

.detail-kicker {
  color: var(--theme--foreground-subdued);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  margin: 0 0 4px;
  text-transform: uppercase;
}

.subtabs {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  inline-size: 100%;
  max-inline-size: min(100%, 980px);
}

.subtab {
  background: var(--theme--background);
  border: 1px solid var(--theme--border-color);
  border-radius: 12px;
  min-height: 42px;
  padding: 9px 12px;
}

.subtab.active {
  border-color: var(--theme--primary);
  box-shadow: 0 0 0 1px var(--theme--primary);
}

.form-grid {
  display: grid;
  gap: 12px;
}

.form-grid-three {
  grid-template-columns: 1fr;
}

.selector-card,
.merch-card,
.media-inline-panel,
.section-block,
.variant-card,
.media-card,
.metric-card {
  background: var(--theme--background);
  border: 1px solid var(--theme--border-color-subdued);
  border-radius: 14px;
  padding: 14px;
}

.choice-item {
  align-items: flex-start;
  background: var(--theme--background-subdued);
  border: 1px solid var(--theme--border-color-subdued);
  border-radius: 12px;
  display: flex;
  gap: 10px;
  min-height: 48px;
  padding: 10px 12px;
}

.choice-item strong {
  display: block;
}

.choice-item small {
  color: var(--theme--foreground-subdued);
}

.metrics-row {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
}

.compact-metrics .metric-card,
.metrics-row .metric-card {
  gap: 4px;
  min-height: auto;
}

.metric-card span {
  color: var(--theme--foreground-subdued);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.metric-card strong {
  font-size: 14px;
  line-height: 1.35;
}

.metric-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  overflow-wrap: anywhere;
}

.variant-card,
.media-card {
  display: grid;
  gap: 10px;
}

.media-preview,
.category-image-preview img {
  background: var(--theme--background-subdued);
  border-radius: 10px;
  display: block;
  height: auto;
  object-fit: cover;
  width: 100%;
}

.media-preview {
  aspect-ratio: 16 / 10;
}

.media-card-body {
  display: grid;
  gap: 8px;
}

.category-image-preview {
  max-width: 320px;
}

.definition-list {
  display: grid;
  gap: 10px;
}

.definition-list div {
  display: grid;
  gap: 3px;
}

.definition-list dt {
  color: var(--theme--foreground-subdued);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.definition-list dd {
  margin: 0;
}

.rma-decision-form {
  border-top: 1px solid var(--theme--border-color-subdued);
  margin-top: 10px;
  padding-top: 10px;
}

.spec-section-list,
.spec-item-list,
.spec-items {
  display: grid;
  gap: 12px;
}

.spec-section-card {
  background: var(--theme--background);
  border: 1px solid var(--theme--border-color-subdued);
  border-radius: 14px;
  display: grid;
  gap: 12px;
  padding: 14px;
}

.spec-item-row {
  align-items: end;
  background: var(--theme--background-subdued);
  border: 1px solid var(--theme--border-color-subdued);
  border-radius: 12px;
  display: grid;
  gap: 10px;
  padding: 10px;
}

.spec-item-remove {
  width: 100%;
}

.sticky-actions {
  background: color-mix(in srgb, var(--theme--background-subdued) 88%, transparent);
  border-top: 1px solid var(--theme--border-color-subdued);
  bottom: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 8px -14px -14px;
  padding: 12px 14px 14px;
  position: sticky;
}

.sticky-actions-inline {
  background: transparent;
  border-top: 0;
  margin: 0;
  padding: 0;
  position: static;
}

.button {
  appearance: none;
  border-radius: 12px;
  cursor: pointer;
  font-weight: 700;
  min-height: 42px;
  padding: 9px 14px;
}

.button-primary {
  background: var(--theme--primary);
  border: 1px solid var(--theme--primary);
  color: var(--theme--primary-inverse);
}

.button-secondary {
  background: var(--theme--background);
  border: 1px solid var(--theme--border-color);
  color: inherit;
}

.button-danger {
  background: var(--theme--background);
  border: 1px solid var(--theme--danger);
  color: var(--theme--danger);
}

.button-small {
  min-height: 36px;
  padding: 7px 12px;
}

.empty-state,
.empty-detail,
.empty-inline,
.inline-note {
  background: var(--theme--background);
  border: 1px dashed var(--theme--border-color);
  border-radius: 14px;
  color: var(--theme--foreground-subdued);
  display: grid;
  gap: 6px;
  padding: 14px;
}

.panel-variants,
.panel-media,
.context-grid {
  align-items: start;
  grid-template-columns: 1fr;
}

.panel-variants > .section-block,
.panel-variants > .editor-form,
.panel-media > .section-block,
.panel-media > .editor-form,
.context-grid > * {
  inline-size: 100%;
  max-inline-size: none;
}

.inventory-editor {
  max-width: none;
}

.panel-main .editor-form,
.category-editor,
.brand-editor,
.inventory-editor,
.panel-variants .editor-form,
.panel-media .editor-form {
  inline-size: 100%;
  max-inline-size: none;
}

.brand-editor .metrics-row {
  grid-template-columns: minmax(0, 260px);
}

@media (min-width: 720px) {
  .subtabs {
    grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  }

  .form-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .form-grid-three {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .variant-list,
  .media-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .spec-item-row {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto;
  }

  .spec-item-remove {
    width: auto;
  }
}

@media (min-width: 1080px) {
  .workspace {
    block-size: calc(100vh - 72px);
    min-block-size: calc(100vh - 72px);
    overflow: hidden;
  }

  .workspace-shell {
    align-items: stretch;
    block-size: 100%;
    grid-template-columns: minmax(304px, 348px) minmax(0, 1fr);
  }

  .workspace-list {
    max-height: none;
    position: static;
  }
}

@media (max-width: 719px) {
  .workspace {
    padding-inline: 8px;
  }
}
</style>

<style>
body.storefront-ops-view #sidebar,
body.storefront-ops-view #sidebar-desktop-outlet,
body.storefront-ops-view #sidebar-mobile-outlet,
body.storefront-ops-view .ai-sidebar-detail,
body.storefront-ops-view .sidebar-toggle {
  display: none !important;
}

body.storefront-ops-view #main-content {
  inline-size: 100% !important;
  max-inline-size: none !important;
  min-inline-size: 0 !important;
}

body.storefront-ops-view #main-content > .main-split {
  grid-template-columns: minmax(0, 1fr) 0px 0px !important;
}

body.storefront-ops-view #main-content .sp-start,
body.storefront-ops-view #main-content .main-content-container {
  inline-size: 100% !important;
  max-inline-size: none !important;
  min-inline-size: 0 !important;
}

body.storefront-ops-view #main-content .sp-end {
  inline-size: 0 !important;
  max-inline-size: 0 !important;
}
</style>
