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

    <template #detail>
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
  </StorefrontOpsTabShell>
</template>

<script setup>
import StorefrontOpsTabShell from '../StorefrontOpsTabShell.vue';
import { STOREFRONT_OPS_TAB_PROP_KEYS } from '../../storefront-ops-tab-props.js';

defineProps(STOREFRONT_OPS_TAB_PROP_KEYS);
</script>
