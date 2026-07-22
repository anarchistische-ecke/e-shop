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
          <h2>Аналитика</h2>
          <p>{{ analyticsState.managerRows.length }} менеджеров</p>
        </div>
        <button class="button button-secondary" type="button" :disabled="isTabLoading('analytics')" @click="loadAnalytics({ notify: true })">
          Рассчитать
        </button>
      </div>

      <label v-if="!isManagerRole" class="search-field">
        <span>Менеджер</span>
        <input v-model.trim="analyticsState.manager" type="search" placeholder="Фильтр" @keyup.enter="loadAnalytics" />
      </label>
      <p v-else class="inline-note">
        Для роли менеджера показываются только ваши заказы, ссылки оплаты и комиссия.
      </p>

      <div v-if="isTabLoading('analytics')" class="empty-state">
        <strong>Считаю показатели</strong>
      </div>
      <div v-else-if="!analyticsState.managerRows.length" class="empty-state">
        <strong>Данных нет</strong>
        <span>За выбранный период не найдено заказов менеджера.</span>
      </div>
      <div v-else class="card-list">
        <article v-for="row in analyticsState.managerRows" :key="row.managerSubject" class="list-card">
          <div class="list-card-head">
            <strong>{{ row.managerSubject }}</strong>
            <span class="pill pill-neutral">{{ row.paidOrders }}/{{ row.totalOrders }}</span>
          </div>
          <div class="list-card-meta">
            <span>{{ formatMoney(row.paidAmount) }}</span>
            <span>3% {{ formatMoney(row.commission) }}</span>
          </div>
        </article>
      </div>
    </template>

    <template #detail>
      <section class="detail-card">
        <header class="detail-header">
          <div>
            <p class="detail-kicker">Аналитика</p>
            <h2>Заказы, оплаты и менеджеры</h2>
            <p class="detail-subtitle">Конверсия заказов отделена от конверсии ссылок менеджеров, чтобы показатели не смешивались.</p>
          </div>
        </header>
        <div class="form-grid form-grid-three">
          <label class="ops-field">
            <span>С</span>
            <input v-model="analyticsState.from" type="datetime-local" />
          </label>
          <label class="ops-field">
            <span>По</span>
            <input v-model="analyticsState.to" type="datetime-local" />
          </label>
          <label v-if="!isManagerRole" class="ops-field">
            <span>Менеджер</span>
            <input v-model.trim="analyticsState.manager" type="text" />
          </label>
          <div v-else class="ops-field ops-field-readonly">
            <span>Менеджер</span>
            <strong>Только ваши показатели</strong>
          </div>
        </div>
        <div class="sticky-actions sticky-actions-inline">
          <button class="button button-primary" type="button" :disabled="isSubmitting || isTabLoading('analytics')" @click="loadAnalytics({ notify: true })">
            Обновить аналитику
          </button>
        </div>

        <section class="section-block">
          <div class="section-head">
            <div>
              <h3>Заказ → оплата</h3>
              <p>Доля созданных заказов, которые дошли до успешной оплаты.</p>
            </div>
          </div>
          <div class="metrics-row">
            <article class="metric-card">
              <span>Заказов создано</span>
              <strong>{{ analyticsState.orderConversion?.created || 0 }}</strong>
            </article>
            <article class="metric-card">
              <span>Оплачено</span>
              <strong>{{ analyticsState.orderConversion?.paid || 0 }}</strong>
            </article>
            <article class="metric-card">
              <span>Конверсия заказ → оплата</span>
              <strong>{{ formatPercent(analyticsState.orderConversion?.checkoutToPaidRate) }}</strong>
            </article>
            <article class="metric-card">
              <span>Ожидают оплаты</span>
              <strong>{{ analyticsState.orderConversion?.pending || 0 }}</strong>
            </article>
            <article class="metric-card">
              <span>Отменено</span>
              <strong>{{ analyticsState.orderConversion?.cancelled || 0 }}</strong>
            </article>
            <article class="metric-card">
              <span>Возвраты</span>
              <strong>{{ analyticsState.orderConversion?.refunded || 0 }}</strong>
            </article>
          </div>
        </section>

        <section class="section-block">
          <div class="section-head">
            <div>
              <h3>Ссылки менеджеров</h3>
              <p>Отдельная конверсия отправленных менеджерами ссылок в оплату.</p>
            </div>
          </div>
          <div class="metrics-row">
            <article class="metric-card">
              <span>Ссылок отправлено</span>
              <strong>{{ analyticsState.paymentLinks.sent || 0 }}</strong>
            </article>
            <article class="metric-card">
              <span>Оплачено по ссылкам</span>
              <strong>{{ analyticsState.paymentLinks.paid || 0 }}</strong>
            </article>
            <article class="metric-card">
              <span>Конверсия ссылок</span>
              <strong>{{ formatPercent(analyticsState.paymentLinks.conversionRate) }}</strong>
            </article>
          </div>
        </section>

        <section class="section-block">
          <div class="section-head">
            <div>
              <h3>Yandex Metrica</h3>
              <p>
                {{ analyticsState.metrika?.ready ? 'Готова к отправке подтверждённых оплат' : 'Требует настройки' }}
              </p>
            </div>
          </div>
          <p v-if="!analyticsState.metrika?.ready" class="inline-note">
            Для подтверждённых покупок нужны включённый offline import, counter ID и OAuth token.
          </p>
          <div class="metrics-row">
            <article class="metric-card">
              <span>Offline import</span>
              <strong>{{ analyticsState.metrika?.offlineImportEnabled ? 'Вкл' : 'Выкл' }}</strong>
            </article>
            <article class="metric-card">
              <span>Counter</span>
              <strong>{{ analyticsState.metrika?.counterConfigured ? 'OK' : 'Нет' }}</strong>
            </article>
            <article class="metric-card">
              <span>OAuth</span>
              <strong>{{ analyticsState.metrika?.oauthConfigured ? 'OK' : 'Нет' }}</strong>
            </article>
            <article class="metric-card">
              <span>Pending</span>
              <strong>{{ analyticsState.metrika?.pending || 0 }}</strong>
            </article>
            <article class="metric-card">
              <span>Sent</span>
              <strong>{{ analyticsState.metrika?.sent || 0 }}</strong>
            </article>
            <article class="metric-card">
              <span>Failed</span>
              <strong>{{ analyticsState.metrika?.failed || 0 }}</strong>
            </article>
            <article class="metric-card">
              <span>Skipped</span>
              <strong>{{ analyticsState.metrika?.skipped || 0 }}</strong>
            </article>
          </div>
        </section>

        <section class="section-block">
          <div class="section-head">
            <div>
              <h3>Менеджеры</h3>
              <p>{{ analyticsState.managerRows.length }} строк</p>
            </div>
          </div>
          <div class="card-list">
            <article v-for="row in analyticsState.managerRows" :key="row.managerSubject" class="list-card">
              <div class="list-card-head">
                <strong>{{ row.managerSubject }}</strong>
                <span class="pill pill-neutral">{{ row.paidOrders }} paid</span>
              </div>
              <div class="list-card-meta">
                <span>Заказов {{ row.totalOrders }}</span>
                <span>{{ formatMoney(row.paidAmount) }}</span>
                <span>Комиссия {{ formatMoney(row.commission) }}</span>
              </div>
            </article>
          </div>
        </section>

        <section class="section-block">
          <div class="section-head">
            <div>
              <h3>Ссылки оплаты</h3>
              <p>{{ analyticsState.paymentLinks.rows?.length || 0 }} строк</p>
            </div>
          </div>
          <div class="card-list">
            <article v-for="link in analyticsState.paymentLinks.rows || []" :key="link.id" class="list-card">
              <div class="list-card-head">
                <strong>{{ link.managerSubject || link.managerEmail || 'Менеджер' }}</strong>
                <span class="pill" :class="link.paid ? 'pill-positive' : 'pill-muted'">{{ link.paid ? 'paid' : link.status }}</span>
              </div>
              <p class="list-card-slug">{{ link.orderId }}</p>
              <div class="list-card-meta">
                <span>{{ formatDateTime(link.sentAt || link.createdAt) }}</span>
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
