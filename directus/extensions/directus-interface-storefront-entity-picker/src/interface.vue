<template>
  <div class="entity-picker">
    <div class="entity-picker-search">
      <v-input
        v-model="query"
        :disabled="disabled"
        :placeholder="placeholder"
        @focus="open = true"
      >
        <template #prepend><v-icon name="search" /></template>
        <template v-if="value" #append>
          <v-icon
            class="entity-picker-clear"
            name="close"
            role="button"
            tabindex="0"
            @click.stop="clear"
            @keydown.enter.prevent="clear"
          />
        </template>
      </v-input>
    </div>

    <div
      v-if="selectedLabel"
      class="entity-picker-selected"
      :class="{ invalid: selectedItem?.disabled }"
    >
      <v-icon :name="selectedItem?.disabled ? 'error' : 'check_circle'" />
      <span>{{ selectedLabel }}</span>
      <code>{{ value }}</code>
    </div>
    <p v-if="selectedItem?.disabled" class="entity-picker-validation">
      {{ selectedItem.disabledReason }} Выберите другую запись перед публикацией.
    </p>

    <div v-if="open" class="entity-picker-results">
      <p v-if="loading" class="entity-picker-state">Загружаю варианты…</p>
      <p v-else-if="error" class="entity-picker-state entity-picker-error">{{ error }}</p>
      <button
        v-for="item in filteredItems"
        v-else
        :key="item.value"
        class="entity-picker-option"
        :class="{ selected: item.value === value, disabled: item.disabled }"
        :disabled="item.disabled"
        type="button"
        @click="select(item)"
      >
        <span>
          <strong>{{ item.label }}</strong>
          <small v-if="item.meta">{{ item.meta }}</small>
        </span>
        <code>{{ item.value }}</code>
      </button>
      <p v-if="!loading && !error && filteredItems.length === 0" class="entity-picker-state">
        Ничего не найдено.
      </p>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useApi } from '@directus/extensions-sdk';

const props = defineProps({
  value: { type: [String, Number], default: null },
  disabled: { type: Boolean, default: false },
  entity: { type: String, default: '' },
  endpoint: { type: String, required: true },
  valueField: { type: String, default: 'id' },
  fallbackValueField: { type: String, default: 'id' },
  labelField: { type: String, default: 'name' },
});
const emit = defineEmits(['input']);
const api = useApi();
const items = ref([]);
const query = ref('');
const loading = ref(false);
const error = ref('');
const open = ref(false);

const entityLabels = {
  product: 'товар',
  category: 'категорию',
  brand: 'бренд',
  promotion: 'акцию',
  promo_code: 'промокод',
};
const placeholder = computed(() => `Найти ${entityLabels[props.entity] || 'объект'}…`);
const selectedItem = computed(
  () => items.value.find((item) => item.value === String(props.value || '')) || null
);
const selectedLabel = computed(() => selectedItem.value?.label || '');
const filteredItems = computed(() => {
  const normalized = query.value.trim().toLocaleLowerCase('ru');
  if (!normalized) return items.value.slice(0, 80);
  return items.value
    .filter((item) => `${item.label} ${item.value} ${item.meta}`.toLocaleLowerCase('ru').includes(normalized))
    .slice(0, 80);
});

function unpack(payload) {
  const candidate = payload?.data?.data ?? payload?.data ?? payload;
  if (Array.isArray(candidate)) return candidate;
  if (Array.isArray(candidate?.items)) return candidate.items;
  if (Array.isArray(candidate?.content)) return candidate.content;
  if (Array.isArray(candidate?.promotions)) return candidate.promotions;
  if (Array.isArray(candidate?.promoCodes)) return candidate.promoCodes;
  return [];
}

function normalize(item) {
  const rawValue = item?.[props.valueField] ?? item?.[props.fallbackValueField] ?? item?.id;
  const rawLabel = item?.[props.labelField] ?? item?.name ?? item?.title ?? item?.code ?? rawValue;
  if (rawValue === null || rawValue === undefined || rawValue === '') return null;
  const disabledReason = commerceDisabledReason(item);
  const status = disabledReason || item?.status || '';
  return {
    value: String(rawValue),
    label: String(rawLabel || rawValue),
    meta: [item?.sku, item?.slug, status].filter(Boolean).join(' · '),
    disabled: Boolean(disabledReason),
    disabledReason,
  };
}

function commerceDisabledReason(item) {
  if (!['promotion', 'promo_code'].includes(props.entity)) return '';
  const status = String(item?.status || '').trim().toUpperCase();
  if (status && status !== 'ACTIVE') return `Статус: ${status.toLocaleLowerCase('ru')}.`;

  const endsAt = Date.parse(item?.endsAt || item?.ends_at || '');
  if (Number.isFinite(endsAt) && endsAt <= Date.now()) return 'Срок действия завершен.';

  const maxRedemptions = Number(item?.maxRedemptions ?? item?.max_redemptions);
  const redemptionCount = Number(item?.redemptionCount ?? item?.redemption_count);
  if (
    props.entity === 'promo_code'
    && Number.isFinite(maxRedemptions)
    && maxRedemptions > 0
    && Number.isFinite(redemptionCount)
    && redemptionCount >= maxRedemptions
  ) {
    return 'Лимит использований исчерпан.';
  }
  return '';
}

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const response = await api.request({ url: props.endpoint, method: 'GET' });
    items.value = unpack(response).map(normalize).filter(Boolean);
  } catch (requestError) {
    error.value = requestError?.response?.data?.errors?.[0]?.message
      || 'Не удалось загрузить варианты. Проверьте доступ к Storefront Ops.';
  } finally {
    loading.value = false;
  }
}

function select(item) {
  if (item.disabled) return;
  emit('input', item.value);
  query.value = item.label;
  open.value = false;
}

function clear() {
  emit('input', null);
  query.value = '';
  open.value = true;
}

watch(() => props.endpoint, load);
onMounted(load);
</script>

<style scoped>
.entity-picker {
  display: grid;
  gap: 8px;
  position: relative;
}

.entity-picker-clear {
  cursor: pointer;
}

.entity-picker-selected {
  align-items: center;
  background: var(--theme--primary-background);
  border-radius: var(--theme--border-radius);
  color: var(--theme--foreground);
  display: flex;
  gap: 8px;
  min-height: 38px;
  padding: 8px 12px;
}

.entity-picker-selected .v-icon {
  color: var(--theme--primary);
}

.entity-picker-selected.invalid {
  background: var(--theme--danger-background);
}

.entity-picker-selected.invalid .v-icon,
.entity-picker-validation {
  color: var(--theme--danger);
}

.entity-picker-validation {
  font-size: 12px;
  margin: 0;
}

.entity-picker-selected span {
  flex: 1;
  font-weight: 600;
}

.entity-picker-selected code,
.entity-picker-option code {
  color: var(--theme--foreground-subdued);
  font-size: 11px;
  max-width: 40%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.entity-picker-results {
  background: var(--theme--background);
  border: var(--theme--border-width) solid var(--theme--border-color);
  border-radius: var(--theme--border-radius);
  box-shadow: 0 8px 24px rgb(0 0 0 / 12%);
  display: grid;
  max-height: 320px;
  overflow: auto;
  padding: 6px;
  z-index: 4;
}

.entity-picker-option {
  align-items: center;
  background: transparent;
  border: 0;
  border-radius: var(--theme--border-radius);
  color: var(--theme--foreground);
  cursor: pointer;
  display: flex;
  gap: 12px;
  justify-content: space-between;
  min-height: 48px;
  padding: 8px 10px;
  text-align: left;
}

.entity-picker-option:hover,
.entity-picker-option.selected {
  background: var(--theme--background-accent);
}

.entity-picker-option.disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.entity-picker-option span {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.entity-picker-option small,
.entity-picker-state {
  color: var(--theme--foreground-subdued);
}

.entity-picker-state {
  margin: 0;
  padding: 12px;
}

.entity-picker-error {
  color: var(--theme--danger);
}
</style>
