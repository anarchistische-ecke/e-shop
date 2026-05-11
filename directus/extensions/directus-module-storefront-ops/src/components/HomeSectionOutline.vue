<template>
  <div class="pane-header">
    <div>
      <h2>Главная</h2>
      <p>{{ sections.length }} секций</p>
    </div>
    <div class="pane-header-actions">
      <button class="button button-secondary" type="button" :disabled="!canPreview" @click="$emit('preview')">
        Предпросмотр
      </button>
      <button class="button button-primary" type="button" :disabled="isSubmitting || loading" @click="$emit('save')">
        Сохранить
      </button>
    </div>
  </div>

  <div class="home-add-section">
    <label class="ops-field">
      <span>Добавить секцию</span>
      <select v-model="selectedPreset">
        <option v-for="preset in presets" :key="preset.value" :value="preset.value">
          {{ preset.label }}
        </option>
      </select>
    </label>
    <button class="button button-secondary" type="button" @click="$emit('add')">
      Добавить
    </button>
  </div>

  <div v-if="loading" class="empty-state">
    <strong>Загружаю главную</strong>
  </div>
  <div v-else-if="!sections.length" class="empty-state">
    <strong>Секции не найдены</strong>
    <span>Проверьте, что начальный контент Directus импортирован.</span>
  </div>
  <div v-else class="card-list home-outline">
    <article
      v-for="(section, index) in sections"
      :key="section.clientId || section.id || section.migrationKey || section.sort"
      role="button"
      tabindex="0"
      class="list-card"
      :class="{ active: selectedIndex === index }"
      @click="$emit('select', index)"
      @keydown.enter.prevent="$emit('select', index)"
    >
      <div class="list-card-head">
        <strong>{{ sectionLabel(section) }}</strong>
        <span class="pill pill-neutral">{{ sectionTypeLabel(section.sectionType) }}</span>
      </div>
      <p class="list-card-slug">{{ section.title || section.anchorId || 'Без заголовка' }}</p>
      <div class="list-card-meta">
        <span>{{ statusLabel(section.status) }}</span>
        <span>{{ section.items.length }} элементов</span>
        <span>Позиция {{ index + 1 }}</span>
      </div>
      <div class="detail-header-actions home-outline-actions">
        <button class="button button-secondary button-small" type="button" :disabled="index === 0" @click.stop="$emit('move', index, -1)">
          ↑
        </button>
        <button class="button button-secondary button-small" type="button" :disabled="index === sections.length - 1" @click.stop="$emit('move', index, 1)">
          ↓
        </button>
      </div>
    </article>
  </div>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  sections: {
    type: Array,
    default: () => [],
  },
  selectedIndex: {
    type: Number,
    default: 0,
  },
  preset: {
    type: String,
    default: '',
  },
  presets: {
    type: Array,
    default: () => [],
  },
  loading: {
    type: Boolean,
    default: false,
  },
  isSubmitting: {
    type: Boolean,
    default: false,
  },
  canPreview: {
    type: Boolean,
    default: false,
  },
  statusLabel: {
    type: Function,
    required: true,
  },
  sectionLabel: {
    type: Function,
    required: true,
  },
  sectionTypeLabel: {
    type: Function,
    required: true,
  },
});

const emit = defineEmits(['update:preset', 'save', 'preview', 'add', 'select', 'move']);

const selectedPreset = computed({
  get: () => props.preset,
  set: (value) => emit('update:preset', value),
});
</script>

<style scoped>
.pane-header-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}
</style>
