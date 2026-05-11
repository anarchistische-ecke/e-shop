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

    <template #detail>
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
  </StorefrontOpsTabShell>
</template>

<script setup>
import StorefrontOpsTabShell from '../StorefrontOpsTabShell.vue';
import { STOREFRONT_OPS_TAB_PROP_KEYS } from '../../storefront-ops-tab-props.js';

defineProps(STOREFRONT_OPS_TAB_PROP_KEYS);
</script>
