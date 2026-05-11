<template>
  <section
    class="workspace-shell"
    :class="{ 'detail-open': activeDetailOpen, 'mobile-master-detail': activeTabHasMasterDetail }"
  >
    <aside class="workspace-list surface-panel">
      <slot name="list" />
    </aside>

    <main class="workspace-detail">
      <div v-if="activeTabHasMasterDetail && activeDetailOpen" class="mobile-detail-toolbar">
        <button class="button button-secondary" type="button" @click="closeActiveDetail">
          ← К списку
        </button>
        <span>{{ activeTabLabel }}</span>
      </div>
      <slot name="detail" />
    </main>
  </section>
</template>

<script setup>
defineProps({
  activeDetailOpen: { type: Boolean, default: false },
  activeTabHasMasterDetail: { type: Boolean, default: false },
  activeTabLabel: { type: String, default: 'Раздел' },
  closeActiveDetail: { type: Function, required: true },
});
</script>
