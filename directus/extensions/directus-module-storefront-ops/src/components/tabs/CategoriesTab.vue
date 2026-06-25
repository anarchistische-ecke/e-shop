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

    <template #detail>
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

        <form id="storefront-ops-category-form" class="editor-form category-editor" @submit.prevent="submitCategory">
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

            <div class="ops-field">
              <span>Позиция</span>
              <div class="position-control">
                <button
                  class="button button-secondary button-small"
                  type="button"
                  :disabled="isSubmitting || Number(categoryForm.position || 0) <= 0"
                  @click="adjustCategoryPosition(-1)"
                >
                  Раньше
                </button>
                <input
                  v-model.number="categoryForm.position"
                  type="number"
                  min="0"
                  step="1"
                  aria-describedby="category-position-help"
                />
                <button
                  class="button button-secondary button-small"
                  type="button"
                  :disabled="isSubmitting"
                  @click="adjustCategoryPosition(1)"
                >
                  Позже
                </button>
              </div>
              <small id="category-position-help" class="field-help">
                Меньшее число поднимает категорию выше среди категорий того же родителя.
              </small>
            </div>
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
              <div v-if="mediaUploadState.targetType === 'CATEGORY' && mediaUploadState.items.length" class="media-upload-list">
                <article v-for="item in mediaUploadState.items" :key="item.id" class="media-upload-row">
                  <div class="media-upload-main">
                    <strong>{{ item.filename }}</strong>
                    <span>{{ mediaUploadProgressLabel(item) }}</span>
                    <progress
                      v-if="item.status === 'UPLOADING'"
                      :value="Math.round(Number(item.progress || 0) * 100)"
                      max="100"
                    />
                    <small v-if="item.error" class="field-error">{{ item.error }}</small>
                  </div>
                  <button
                    v-if="item.status === 'FAILED'"
                    class="button button-secondary button-small"
                    type="button"
                    @click="retryMediaUpload(item.id)"
                  >
                    Повторить
                  </button>
                  <button
                    v-if="['UPLOADING', 'QUEUED', 'FAILED'].includes(item.status)"
                    class="button button-danger button-small"
                    type="button"
                    @click="abortMediaUpload(item.id)"
                  >
                    Отменить
                  </button>
                </article>
              </div>
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
                <button
                  class="button button-secondary"
                  type="button"
                  :disabled="!accessState.previewBaseUrl || !categoryForm.slug"
                  @click="openStorefrontPreview('category')"
                >
                  Предпросмотр витрины
                </button>
              </div>
            </article>
          </div>
        </form>

        <div class="sticky-actions detail-footer-actions">
          <button class="button button-primary" type="submit" form="storefront-ops-category-form" :disabled="isSubmitting">
            {{ categoryForm.id ? 'Сохранить категорию' : 'Создать категорию' }}
          </button>
          <button class="button button-secondary" type="button" :disabled="isSubmitting" @click="resetCategoryEditor">
            Сбросить
          </button>
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
