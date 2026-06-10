<template>
  <StorefrontOpsTabShell
    :active-detail-open="activeDetailOpen"
    :active-tab-has-master-detail="activeTabHasMasterDetail"
    :active-tab-label="activeTabLabel"
    :close-active-detail="closeActiveDetail"
  >
    <template #list>
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

    <template #detail>
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

        <template v-if="productState.panel === 'main'">
          <div class="detail-content panel-main">
            <form id="storefront-ops-product-form" class="editor-form" @submit.prevent="submitProduct">
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
                  <span>{{ productForm.isActive ? 'Товар виден на витрине' : 'Товар скрыт с витрины' }}</span>
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
                <input :value="productCategoryFilter" @input="setProductCategoryFilter($event.target.value.trim())" type="search" placeholder="Поиск по дереву" />
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
          </form>
          </div>

          <div class="sticky-actions detail-footer-actions">
            <button class="button button-primary" type="submit" form="storefront-ops-product-form" :disabled="isSubmitting">
              {{ productForm.id ? 'Сохранить товар' : 'Создать товар' }}
            </button>
            <button class="button button-secondary" type="button" :disabled="isSubmitting" @click="resetProductEditor">
              Сбросить
            </button>
          </div>
        </template>

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
                  <input
                    v-model.trim="variantForm.sku"
                    type="text"
                    :disabled="Boolean(variantForm.id) && roleKind !== 'admin'"
                  />
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
              <button
                class="button button-secondary"
                type="button"
                :disabled="!accessState.previewBaseUrl || !productForm.slug"
                @click="openStorefrontPreview('product')"
              >
                Предпросмотр витрины
              </button>
            </div>
          </article>
        </div>
      </section>
    </template>
  </StorefrontOpsTabShell>
</template>

<script setup>
import StorefrontOpsTabShell from '../StorefrontOpsTabShell.vue';
import { STOREFRONT_OPS_TAB_PROP_KEYS } from '../../storefront-ops-tab-props.js';

defineProps(STOREFRONT_OPS_TAB_PROP_KEYS);
</script>
