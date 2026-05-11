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

    <template #detail>
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
  </StorefrontOpsTabShell>
</template>

<script setup>
import StorefrontOpsTabShell from '../StorefrontOpsTabShell.vue';
import { STOREFRONT_OPS_TAB_PROP_KEYS } from '../../storefront-ops-tab-props.js';

defineProps(STOREFRONT_OPS_TAB_PROP_KEYS);
</script>
