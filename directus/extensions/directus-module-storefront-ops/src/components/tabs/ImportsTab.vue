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
          <h2>Импорт</h2>
          <p>{{ importState.jobs.length }} запусков</p>
        </div>
      </div>

      <div v-if="isTabLoading('imports')" class="empty-state">
        <strong>Загружаю историю</strong>
      </div>
      <div v-else-if="!importState.jobs.length" class="empty-state">
        <strong>История пуста</strong>
        <span>Запустите dry-run для Excel или CSV файла.</span>
      </div>
      <div v-else class="card-list">
        <button
          v-for="job in importState.jobs"
          :key="job.id"
          type="button"
          class="list-card"
          :class="{ active: importState.selectedJobId === job.id }"
          @click="selectImportJob(job.id)"
        >
          <div class="list-card-head">
            <strong>{{ job.fileName || job.id }}</strong>
            <span class="pill" :class="job.invalidRows ? 'pill-muted' : 'pill-positive'">{{ job.status }}</span>
          </div>
          <div class="list-card-meta">
            <span>{{ job.validRows }} валидных</span>
            <span>{{ job.invalidRows }} ошибок</span>
            <span>{{ formatDateTime(job.createdAt) }}</span>
          </div>
        </button>
      </div>
    </template>

    <template #detail>
      <section class="detail-card">
        <header class="detail-header">
          <div>
            <p class="detail-kicker">Импорт</p>
            <h2>Excel / CSV загрузка</h2>
            <p class="detail-subtitle">Dry-run проверяет строки до применения к каталогу и остаткам.</p>
          </div>
        </header>

        <form class="editor-form" @submit.prevent="dryRunImport">
          <label class="ops-field ops-field-required">
            <span>Файл</span>
            <input type="file" accept=".xlsx,.xls,.csv" @change="onImportFileSelected" />
          </label>

          <section class="selector-card">
            <div class="selector-card-head">
              <div>
                <h3>Маппинг колонок</h3>
                <p>По умолчанию используются sku, product_name, product_slug, variant_name, price, stock, currency.</p>
              </div>
            </div>
            <div class="form-grid form-grid-three">
              <label v-for="field in importMappingFields" :key="field.key" class="ops-field">
                <span>{{ field.label }}</span>
                <input v-model.trim="importState.mapping[field.key]" type="text" />
              </label>
            </div>
          </section>

          <div class="sticky-actions">
            <button class="button button-primary" type="submit" :disabled="isSubmitting || !importState.file">
              Проверить файл
            </button>
            <button class="button button-secondary" type="button" :disabled="isSubmitting || !importState.dryRun?.job" @click="commitImport">
              Применить импорт
            </button>
          </div>
        </form>

        <section v-if="importState.dryRun" class="section-block">
          <div class="section-head">
            <div>
              <h3>Результат проверки</h3>
              <p>{{ importState.dryRun.job.validRows }} валидных · {{ importState.dryRun.job.invalidRows }} ошибок</p>
            </div>
          </div>
          <div class="card-list">
            <article v-for="row in importState.dryRun.rows.slice(0, 50)" :key="row.id" class="list-card">
              <div class="list-card-head">
                <strong>Строка {{ row.rowNumber }} · {{ row.sku || 'без SKU' }}</strong>
                <span class="pill" :class="row.valid ? 'pill-positive' : 'pill-muted'">{{ row.valid ? 'OK' : 'Ошибка' }}</span>
              </div>
              <p class="list-card-slug">{{ row.productName || row.errorMessage }}</p>
              <div v-if="row.errorMessage" class="list-card-meta">
                <span>{{ row.errorMessage }}</span>
              </div>
            </article>
          </div>
        </section>
      </section>
    </template>
  </StorefrontOpsTabShell>
</template>

<script setup>
import StorefrontOpsTabShell from '../StorefrontOpsTabShell.vue';
import { STOREFRONT_OPS_TAB_PROP_KEYS } from '../../storefront-ops-tab-props.js';

defineProps(STOREFRONT_OPS_TAB_PROP_KEYS);
</script>
