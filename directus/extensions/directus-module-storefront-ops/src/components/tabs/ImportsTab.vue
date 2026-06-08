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
            <h2>Обновление остатков</h2>
            <p class="detail-subtitle">Файл сопоставляется по артикулу. Изменяется только остаток варианта.</p>
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
                <h3>Правила импорта</h3>
                <p>Артикул: Номенклатура.Артикул · остаток: Склад 21 Век АТРИУМ + Склад 21 Век ИП.</p>
              </div>
            </div>
            <div class="form-grid form-grid-two">
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
            <button
              class="button button-secondary"
              type="button"
              :disabled="isSubmitting || !importState.dryRun?.job || importState.dryRun.job.invalidRows"
              @click="commitImport"
            >
              Обновить остатки
            </button>
          </div>
        </form>

        <section v-if="importState.dryRun" class="section-block">
          <div class="section-head">
            <div>
              <h3>Результат проверки</h3>
              <p>{{ importState.dryRun.job.validRows }} валидных · {{ importState.dryRun.job.invalidRows }} ошибок</p>
            </div>
            <button
              class="button button-secondary"
              type="button"
              :disabled="!importState.dryRun.report?.notUpdatedRows"
              @click="openImportNotUpdatedModal"
            >
              Не обновлены: {{ importState.dryRun.report?.notUpdatedRows || 0 }}
            </button>
          </div>

          <div class="metrics-row compact-metrics">
            <article class="metric-card">
              <span>Найдены</span>
              <strong>{{ importState.dryRun.report?.matchedRows || 0 }}</strong>
            </article>
            <article class="metric-card">
              <span>Изменятся</span>
              <strong>{{ importState.dryRun.report?.changedRows || 0 }}</strong>
            </article>
            <article class="metric-card">
              <span>Без изменений</span>
              <strong>{{ importState.dryRun.report?.unchangedRows || 0 }}</strong>
            </article>
            <article class="metric-card">
              <span>Пропущены</span>
              <strong>{{ importState.dryRun.report?.skippedRows || 0 }}</strong>
            </article>
            <article class="metric-card">
              <span>Ошибки</span>
              <strong>{{ importState.dryRun.report?.invalidRows || 0 }}</strong>
            </article>
          </div>

          <div class="card-list">
            <article v-for="row in importState.dryRun.rows.slice(0, 50)" :key="row.id" class="list-card">
              <div class="list-card-head">
                <strong>Строка {{ row.rowNumber }} · {{ row.sku || 'без SKU' }}</strong>
                <span class="pill" :class="row.valid ? 'pill-positive' : 'pill-muted'">{{ row.valid ? 'OK' : 'Ошибка' }}</span>
              </div>
              <p class="list-card-slug">{{ row.productName || row.errorMessage || `${row.stockQuantity ?? 0} шт.` }}</p>
              <div v-if="row.errorMessage" class="list-card-meta">
                <span>{{ row.errorMessage }}</span>
              </div>
              <div v-else class="list-card-meta">
                <span>{{ row.stockQuantity ?? 0 }} шт.</span>
              </div>
            </article>
          </div>
        </section>

        <div v-if="importState.notUpdatedModalOpen" class="ops-modal-backdrop" @click.self="closeImportNotUpdatedModal">
          <section class="ops-modal">
            <header class="section-head">
              <div>
                <h3>Не обновлены</h3>
                <p>{{ importState.dryRun?.report?.notUpdatedRows || 0 }} вариантов каталога не найдены в файле.</p>
              </div>
              <button class="button button-secondary button-small" type="button" @click="closeImportNotUpdatedModal">
                Закрыть
              </button>
            </header>

            <div class="sticky-actions">
              <button class="button button-secondary" type="button" @click="downloadImportNotUpdated('txt')">
                Скачать TXT
              </button>
              <button class="button button-primary" type="button" @click="downloadImportNotUpdated('xlsx')">
                Скачать Excel
              </button>
            </div>

            <div class="card-list card-list-compact import-modal-list">
              <article
                v-for="variant in importState.dryRun?.report?.notUpdatedVariants || []"
                :key="variant.variantId"
                class="list-card"
              >
                <div class="list-card-head">
                  <strong>{{ variant.sku }}</strong>
                  <span class="pill" :class="variant.productActive ? 'pill-positive' : 'pill-muted'">
                    {{ variant.productActive ? 'Активен' : 'Неактивен' }}
                  </span>
                </div>
                <p class="list-card-slug">{{ variant.productName || variant.productSlug || variant.variantName }}</p>
                <div class="list-card-meta">
                  <span>{{ variant.variantName }}</span>
                  <span>{{ variant.currentStock }} шт.</span>
                </div>
              </article>
            </div>
          </section>
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
