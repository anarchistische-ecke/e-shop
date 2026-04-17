<template>
  <div class="launcher">
    <header class="launcher-header">
      <div>
        <p class="launcher-kicker">Резервная точка входа</p>
        <h2>Управление витриной</h2>
      </div>
      <button class="launcher-primary" type="button" @click="openTab('products')">
        Открыть рабочее место
      </button>
    </header>

    <div class="launcher-grid">
      <button v-for="tab in tabs" :key="tab.id" type="button" class="launcher-card" @click="openTab(tab.id)">
        <strong>{{ tab.label }}</strong>
        <span>{{ tab.description }}</span>
      </button>
    </div>
  </div>
</template>

<script setup>
const tabs = [
  { id: 'products', label: 'Товары', description: 'Карточки товаров, варианты, медиа, оверлеи' },
  { id: 'categories', label: 'Категории', description: 'Дерево категорий, изображение и оверлей' },
  { id: 'brands', label: 'Бренды', description: 'Справочник брендов и связанный ассортимент' },
  { id: 'inventory', label: 'Остатки', description: 'SKU, корректировки остатков и идемпотентность' },
];

function openTab(tabId) {
  window.location.assign(`/admin/storefront-ops?tab=${encodeURIComponent(tabId)}`);
}
</script>

<style scoped>
.launcher {
  display: grid;
  gap: 16px;
  height: 100%;
  padding: 8px;
}

.launcher-header {
  align-items: start;
  display: flex;
  gap: 12px;
  justify-content: space-between;
}

.launcher-kicker {
  color: var(--theme--foreground-subdued);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  margin: 0 0 6px;
  text-transform: uppercase;
}

.launcher-header h2 {
  margin: 0;
}

.launcher-primary,
.launcher-card {
  appearance: none;
  border-radius: 14px;
  cursor: pointer;
}

.launcher-primary {
  background: var(--theme--primary);
  border: 1px solid var(--theme--primary);
  color: var(--theme--primary-inverse);
  min-height: 42px;
  padding: 10px 14px;
}

.launcher-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.launcher-card {
  align-items: start;
  background: var(--theme--background);
  border: 1px solid var(--theme--border-color);
  color: inherit;
  display: grid;
  gap: 6px;
  min-height: 104px;
  padding: 14px;
  text-align: left;
}

.launcher-card span {
  color: var(--theme--foreground-subdued);
  font-size: 13px;
  line-height: 1.35;
}

@media (max-width: 720px) {
  .launcher-header,
  .launcher-grid {
    grid-template-columns: 1fr;
    display: grid;
  }
}
</style>
