<template>
  <private-view class="workspace-view" title="Управление витриной">
    <template #actions>
      <v-button secondary @click="copyCurrentLink" :disabled="isRefreshing">
        Копировать ссылку
      </v-button>
      <v-button @click="refreshCurrentTab" :loading="isRefreshing">
        Обновить
      </v-button>
    </template>

    <teleport v-if="navigationTarget" :to="navigationTarget">
      <div class="pane-tabs-carousel pane-tabs-carousel-navigation" :class="tabCarouselClasses">
        <button
          class="pane-tabs-arrow pane-tabs-arrow-left"
          type="button"
          aria-label="Прокрутить разделы влево"
          :disabled="!canScrollTabsLeft"
          @click="scrollTabs('left')"
        >
          ‹
        </button>
        <nav
          ref="tabRail"
          class="pane-tabs pane-tabs-navigation"
          aria-label="Разделы управления витриной"
          @scroll.passive="handleTabRailScroll"
        >
          <button
            v-for="tab in visibleTabs"
            :key="tab.id"
            type="button"
            class="pane-tab"
            :class="{ active: activeTab === tab.id }"
            @click="setActiveTab(tab.id)"
          >
            <span>{{ tab.label }}</span>
            <small>{{ tabCount(tab.id) }}</small>
          </button>
        </nav>
        <button
          class="pane-tabs-arrow pane-tabs-arrow-right"
          type="button"
          aria-label="Прокрутить разделы вправо"
          :disabled="!canScrollTabsRight"
          @click="scrollTabs('right')"
        >
          ›
        </button>
      </div>
    </teleport>

    <div class="workspace storefront-ops">
      <div v-if="!navigationTarget" class="pane-tabs-carousel pane-tabs-carousel-inline" :class="tabCarouselClasses">
        <button
          class="pane-tabs-arrow pane-tabs-arrow-left"
          type="button"
          aria-label="Прокрутить разделы влево"
          :disabled="!canScrollTabsLeft"
          @click="scrollTabs('left')"
        >
          ‹
        </button>
        <nav
          ref="tabRail"
          class="pane-tabs pane-tabs-inline"
          aria-label="Разделы управления витриной"
          @scroll.passive="handleTabRailScroll"
        >
          <button
            v-for="tab in visibleTabs"
            :key="tab.id"
            type="button"
            class="pane-tab"
            :class="{ active: activeTab === tab.id }"
            @click="setActiveTab(tab.id)"
          >
            <span>{{ tab.label }}</span>
            <small>{{ tabCount(tab.id) }}</small>
          </button>
        </nav>
        <button
          class="pane-tabs-arrow pane-tabs-arrow-right"
          type="button"
          aria-label="Прокрутить разделы вправо"
          :disabled="!canScrollTabsRight"
          @click="scrollTabs('right')"
        >
          ›
        </button>
      </div>

      <div v-if="accessState.loaded" class="access-context">
        <strong>{{ accessRoleLabel }}</strong>
        <span>{{ managerAnalyticsNotice }}</span>
      </div>

      <div v-if="pageError" class="status-banner status-banner-error">
        <div class="status-banner-copy">
          <strong>Ошибка</strong>
          <span>{{ pageError }}</span>
        </div>
        <button class="status-banner-close" type="button" aria-label="Закрыть уведомление" @click="clearMessages">
          ×
        </button>
      </div>
      <div
        v-else-if="pageNotice.text"
        class="status-banner"
        :class="pageNotice.type === 'success' ? 'status-banner-success' : 'status-banner-info'"
      >
        <div class="status-banner-copy">
          <strong>{{ pageNotice.type === 'success' ? 'Готово' : 'Информация' }}</strong>
          <span>{{ pageNotice.text }}</span>
        </div>
        <button class="status-banner-close" type="button" aria-label="Закрыть уведомление" @click="clearMessages">
          ×
        </button>
      </div>

      <div v-if="accessState.loaded && !visibleTabs.length" class="empty-state">
        <strong>Нет доступных разделов</strong>
        <span>Проверьте роль пользователя в Directus и Keycloak.</span>
      </div>

      <StorefrontOpsActiveTab
        v-else
        :component="activeTabComponent"
        :view-props="storefrontOpsViewProps"
      />
    </div>
  </private-view>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import './storefront-ops.css';
import StorefrontOpsActiveTab from './components/StorefrontOpsActiveTab.js';
import AlertsTab from './components/tabs/AlertsTab.vue';
import AnalyticsTab from './components/tabs/AnalyticsTab.vue';
import BrandsTab from './components/tabs/BrandsTab.vue';
import CategoriesTab from './components/tabs/CategoriesTab.vue';
import HomeTab from './components/tabs/HomeTab.vue';
import ImportsTab from './components/tabs/ImportsTab.vue';
import InventoryTab from './components/tabs/InventoryTab.vue';
import OrdersTab from './components/tabs/OrdersTab.vue';
import ProductsTab from './components/tabs/ProductsTab.vue';
import PromotionsTab from './components/tabs/PromotionsTab.vue';
import TaxTab from './components/tabs/TaxTab.vue';
import { useStorefrontOpsWorkspace } from './composables/useStorefrontOpsWorkspace.js';

const tabComponents = {
  home: HomeTab,
  products: ProductsTab,
  categories: CategoriesTab,
  brands: BrandsTab,
  inventory: InventoryTab,
  orders: OrdersTab,
  imports: ImportsTab,
  promotions: PromotionsTab,
  tax: TaxTab,
  analytics: AnalyticsTab,
  alerts: AlertsTab,
};

const {
  copyCurrentLink,
  refreshCurrentTab,
  isRefreshing,
  navigationTarget,
  visibleTabs,
  activeTab,
  setActiveTab,
  tabCount,
  accessState,
  accessRoleLabel,
  managerAnalyticsNotice,
  pageError,
  pageNotice,
  clearMessages,
  activeTabComponent,
  storefrontOpsViewProps,
} = useStorefrontOpsWorkspace(tabComponents);

const tabRail = ref(null);
const isTabRailScrollable = ref(false);
const canScrollTabsLeft = ref(false);
const canScrollTabsRight = ref(false);
let tabRailResizeObserver = null;

const tabCarouselClasses = computed(() => ({
  'is-scrollable': isTabRailScrollable.value,
  'can-scroll-left': canScrollTabsLeft.value,
  'can-scroll-right': canScrollTabsRight.value,
}));

function updateTabRailScrollState() {
  const rail = tabRail.value;

  if (!rail) {
    isTabRailScrollable.value = false;
    canScrollTabsLeft.value = false;
    canScrollTabsRight.value = false;
    return;
  }

  const maxScrollLeft = Math.max(0, rail.scrollWidth - rail.clientWidth);
  const scrollLeft = Math.max(0, rail.scrollLeft);

  isTabRailScrollable.value = maxScrollLeft > 2;
  canScrollTabsLeft.value = scrollLeft > 2;
  canScrollTabsRight.value = scrollLeft < maxScrollLeft - 2;
}

function handleTabRailScroll() {
  updateTabRailScrollState();
}

function scrollTabs(direction) {
  const rail = tabRail.value;

  if (!rail) {
    return;
  }

  const distance = Math.max(rail.clientWidth * 0.72, 180);
  rail.scrollBy({
    left: direction === 'left' ? -distance : distance,
    behavior: 'smooth',
  });
}

function disconnectTabRailObserver() {
  tabRailResizeObserver?.disconnect();
  tabRailResizeObserver = null;
}

async function syncTabRailObserver() {
  await nextTick();
  disconnectTabRailObserver();

  const rail = tabRail.value;

  if (!rail) {
    updateTabRailScrollState();
    return;
  }

  if (typeof window !== 'undefined' && 'ResizeObserver' in window) {
    tabRailResizeObserver = new window.ResizeObserver(() => {
      updateTabRailScrollState();
    });
    tabRailResizeObserver.observe(rail);
  }

  updateTabRailScrollState();
}

onMounted(() => {
  syncTabRailObserver();
});

watch([visibleTabs, navigationTarget], () => {
  syncTabRailObserver();
});

onBeforeUnmount(() => {
  disconnectTabRailObserver();
});
</script>
