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

    <template #detail>
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

        <form id="storefront-ops-brand-form" class="editor-form brand-editor" @submit.prevent="submitBrand">
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
        </form>

        <div class="sticky-actions detail-footer-actions">
          <button class="button button-primary" type="submit" form="storefront-ops-brand-form" :disabled="isSubmitting">
            {{ brandForm.id ? 'Сохранить бренд' : 'Создать бренд' }}
          </button>
          <button class="button button-secondary" type="button" :disabled="isSubmitting" @click="resetBrandEditor">
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
