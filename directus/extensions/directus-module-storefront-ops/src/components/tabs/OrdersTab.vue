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
          <h2>Заказы</h2>
          <p>{{ orderState.items.length }} записей</p>
        </div>
        <button class="button button-secondary" type="button" :disabled="isTabLoading('orders')" @click="loadOrders({ notify: true })">
          Найти
        </button>
      </div>

      <label class="search-field">
        <span>Поиск</span>
        <input v-model.trim="orderState.query" type="search" placeholder="ID, email, токен" @keyup.enter="loadOrders" />
      </label>

      <div class="form-grid">
        <label class="ops-field">
          <span>Статус</span>
          <input v-model.trim="orderState.status" type="text" placeholder="PENDING, PAID" @keyup.enter="loadOrders" />
        </label>
        <label class="ops-field">
          <span>Менеджер</span>
          <input v-model.trim="orderState.manager" type="text" @keyup.enter="loadOrders" />
        </label>
        <label v-if="roleKind === 'admin'" class="ops-field">
          <span>Архив</span>
          <select v-model="orderState.archived" @change="loadOrders">
            <option value="all">Все заказы</option>
            <option value="active">Только активные</option>
            <option value="archived">Только архив</option>
          </select>
        </label>
        <label class="ops-field">
          <span>С</span>
          <input v-model="orderState.from" type="datetime-local" @keyup.enter="loadOrders" />
        </label>
        <label class="ops-field">
          <span>По</span>
          <input v-model="orderState.to" type="datetime-local" @keyup.enter="loadOrders" />
        </label>
      </div>

      <section v-if="canViewActivePromotions" class="section-block section-block-compact">
        <div class="section-head">
          <div>
            <h3>Активные акции</h3>
            <p>{{ activePromotionState.items.length }} доступно для менеджера</p>
          </div>
          <button class="button button-secondary" type="button" :disabled="loading.activePromotions" @click="loadActivePromotions({ notify: true })">
            Обновить
          </button>
        </div>
        <div v-if="loading.activePromotions" class="empty-inline">Загружаю акции.</div>
        <div v-else-if="!activePromotionState.items.length" class="empty-inline">Активных акций сейчас нет.</div>
        <div v-else class="card-list card-list-compact">
          <article v-for="promotion in activePromotionState.items" :key="promotion.id" class="list-card">
            <div class="list-card-head">
              <strong>{{ promotion.name }}</strong>
              <span class="pill pill-positive">{{ promotion.type }}</span>
            </div>
            <div class="list-card-meta">
              <span v-if="promotion.salePriceAmount">Цена {{ formatMinorMoney(promotion.salePriceAmount, promotion.currency) }}</span>
              <span v-if="promotion.discountPercent">{{ promotion.discountPercent }}%</span>
              <span v-if="promotion.thresholdAmount">от {{ formatMinorMoney(promotion.thresholdAmount, promotion.currency) }}</span>
            </div>
          </article>
        </div>
      </section>

      <div v-if="isTabLoading('orders')" class="empty-state">
        <strong>Загружаю заказы</strong>
      </div>
      <div v-else-if="!orderState.items.length" class="empty-state">
        <strong>Заказы не найдены</strong>
        <span>Измените фильтр или период.</span>
      </div>
      <div v-else class="card-list">
        <button
          v-for="order in orderState.items"
          :key="order.id"
          type="button"
          class="list-card"
          :class="{ active: orderState.selectedId === order.id }"
          @click="selectOrder(order.id)"
        >
          <div class="list-card-head">
            <strong>{{ order.receiptEmail || order.id }}</strong>
            <span class="pill" :class="isOrderArchived(order) ? 'pill-muted' : 'pill-neutral'">
              {{ isOrderArchived(order) ? 'В архиве' : orderStatusLabel(order.status) }}
            </span>
          </div>
          <p class="list-card-slug">{{ order.id }}</p>
          <div class="list-card-meta">
            <span>{{ formatMoney(order.totalAmount) }}</span>
            <span>{{ orderManagerLabel(order) }}</span>
            <span>{{ formatDateTime(order.orderDate) }}</span>
            <span v-if="isOrderArchived(order)">Архив {{ formatDateTime(order.archivedAt) }}</span>
          </div>
        </button>
      </div>
    </template>

    <template #detail>
      <div v-if="!orderState.detail" class="empty-detail">
        <strong>Выберите заказ</strong>
        <span>Откройте заказ из списка, чтобы увидеть состав, историю и действия.</span>
      </div>
      <section v-else class="detail-card">
        <header class="detail-header">
          <div>
            <p class="detail-kicker">Заказы</p>
            <h2>{{ orderState.detail.order.receiptEmail || orderState.detail.order.id }}</h2>
            <p class="detail-subtitle">{{ orderState.detail.order.id }}</p>
          </div>
          <div class="detail-header-actions">
            <button
              v-if="canClaimSelectedOrder"
              class="button button-secondary"
              type="button"
              :disabled="isSubmitting"
              @click="claimOrder"
            >
              Взять в работу
            </button>
            <button
              v-if="canClearSelectedOrder"
              class="button button-danger"
              type="button"
              :disabled="isSubmitting"
              @click="clearOrderClaim"
            >
              Снять менеджера
            </button>
            <button
              v-if="canArchiveSelectedOrder"
              class="button button-danger"
              type="button"
              :disabled="isSubmitting"
              @click="archiveOrder"
            >
              Удалить заказ
            </button>
            <button
              v-if="canRestoreSelectedOrder"
              class="button button-secondary"
              type="button"
              :disabled="isSubmitting"
              @click="restoreOrder"
            >
              Восстановить
            </button>
          </div>
        </header>

        <p v-if="orderState.selectedFilteredOut" class="inline-note">
          Выбранный заказ не попадает в текущий список из-за фильтров.
          <button class="button button-secondary button-small" type="button" @click="resetOrderFiltersForSelected">
            Сбросить фильтры
          </button>
        </p>

        <p v-if="isOrderArchived(orderState.detail.order)" class="inline-note">
          Заказ в архиве: {{ orderState.detail.order.archiveReason || 'причина не указана' }}.
          Операции по заказу доступны после восстановления.
        </p>

        <div class="metrics-row">
          <article class="metric-card">
            <span>Статус</span>
            <strong>{{ isOrderArchived(orderState.detail.order) ? 'В архиве' : orderStatusLabel(orderState.detail.order.status) }}</strong>
          </article>
          <article class="metric-card">
            <span>Сумма</span>
            <strong>{{ formatMoney(orderState.detail.order.totalAmount) }}</strong>
          </article>
          <article class="metric-card">
            <span>Менеджер</span>
            <strong>{{ orderManagerLabel(orderState.detail.order) }}</strong>
          </article>
          <article v-if="isOrderArchived(orderState.detail.order)" class="metric-card">
            <span>Архивировал</span>
            <strong>{{ orderState.detail.order.archivedBy || 'Не указано' }}</strong>
          </article>
        </div>

        <section class="section-block">
          <div class="section-head">
            <div>
              <h3>Статус</h3>
              <p>Переход будет записан в аудит истории заказа.</p>
            </div>
          </div>
          <form class="editor-form" @submit.prevent="submitOrderStatus">
            <div class="form-grid">
              <label class="ops-field ops-field-required">
                <span>Новый статус</span>
                <select v-model="orderState.nextStatus">
                  <option v-for="status in orderStatusOptions" :key="status" :value="status">
                    {{ orderStatusLabel(status) }}
                  </option>
                </select>
              </label>
              <label class="ops-field">
                <span>Комментарий</span>
                <input v-model.trim="orderState.note" type="text" />
              </label>
            </div>
            <div class="sticky-actions sticky-actions-inline">
              <button class="button button-primary" type="submit" :disabled="isSubmitting || !canSubmitSelectedOrderStatus">
                Сохранить статус
              </button>
            </div>
            <p v-if="!canSubmitSelectedOrderStatus" class="inline-note">
              Для текущей роли изменение статуса этого заказа недоступно.
            </p>
          </form>
        </section>

        <section class="section-block">
          <div class="section-head">
            <div>
              <h3>Оплата и возвраты</h3>
              <p>{{ orderState.detail.order.paymentSummary ? 'YooKassa · полная предоплата' : 'Платёж ещё не создан' }}</p>
            </div>
            <button
              v-if="canRefundSelectedOrder"
              class="button button-danger"
              type="button"
              :disabled="isSubmitting"
              @click="refundSelectedOrder({ full: true })"
            >
              Вернуть остаток
            </button>
          </div>

          <dl v-if="orderState.detail.order.paymentSummary" class="definition-list">
            <div>
              <dt>Статус</dt>
              <dd>{{ orderState.detail.order.paymentSummary.status }}</dd>
            </div>
            <div>
              <dt>ID платежа</dt>
              <dd>{{ orderState.detail.order.paymentSummary.providerPaymentId || 'Не указан' }}</dd>
            </div>
            <div>
              <dt>Сумма платежа</dt>
              <dd>{{ formatMinorMoney(orderState.detail.order.paymentSummary.amount?.amount, orderState.detail.order.paymentSummary.amount?.currency) }}</dd>
            </div>
            <div>
              <dt>Доступно к возврату</dt>
              <dd>{{ formatMinorMoney(orderState.detail.order.paymentSummary.refundableAmount?.amount, orderState.detail.order.paymentSummary.refundableAmount?.currency) }}</dd>
            </div>
            <div>
              <dt>Чек 54-ФЗ</dt>
              <dd v-if="orderState.detail.order.paymentSummary.receiptUrl">
                <a :href="orderState.detail.order.paymentSummary.receiptUrl" target="_blank" rel="noreferrer">Открыть чек</a>
              </dd>
              <dd v-else>{{ orderState.detail.order.paymentSummary.receiptRegistration || 'Формируется в YooKassa' }}</dd>
            </div>
          </dl>
          <div v-else class="empty-inline">Заказ ожидает создания платёжной сессии.</div>

          <div v-if="orderState.detail.order.paymentSummary?.refunds?.length" class="card-list card-list-compact">
            <article v-for="refund in orderState.detail.order.paymentSummary.refunds" :key="refund.id || refund.refundId" class="list-card">
              <div class="list-card-head">
                <strong>{{ formatMinorMoney(refund.amount?.amount, refund.amount?.currency) }}</strong>
                <span class="pill pill-neutral">{{ refund.status }}</span>
              </div>
              <p class="list-card-slug">{{ refund.refundId }}</p>
              <div class="list-card-meta">
                <span>{{ formatDateTime(refund.refundDate) }}</span>
                <span>{{ refund.items?.length || 0 }} строк</span>
              </div>
            </article>
          </div>

          <form v-if="canRefundSelectedOrder" class="editor-form" @submit.prevent="refundSelectedOrder({ full: false })">
            <div class="section-head section-head-compact">
              <div>
                <h3>Частичный возврат</h3>
                <p>Укажите количество по строкам заказа. Сумма необязательна: если оставить пустой, API рассчитает её по оплаченной цене строки.</p>
              </div>
            </div>
            <div class="card-list card-list-compact">
              <template v-for="item in orderState.detail.order.items || []" :key="`refund-${item.id}`">
                <article v-if="item.id && orderState.refundForms[item.id]" class="list-card">
                  <div class="list-card-head">
                    <strong>{{ item.productName || item.variantName || item.variantId }}</strong>
                    <span class="pill pill-neutral">{{ item.quantity }} шт.</span>
                  </div>
                  <div class="form-grid">
                    <label class="ops-field">
                      <span>Количество к возврату</span>
                      <input v-model.number="orderState.refundForms[item.id].quantity" type="number" min="0" :max="item.quantity" step="1" />
                    </label>
                    <label class="ops-field">
                      <span>Сумма, ₽</span>
                      <input v-model.number="orderState.refundForms[item.id].amount" type="number" min="0" step="0.01" />
                    </label>
                  </div>
                </article>
              </template>
            </div>
            <div class="sticky-actions sticky-actions-inline">
              <button class="button button-danger" type="submit" :disabled="isSubmitting">
                Создать частичный возврат
              </button>
            </div>
          </form>
        </section>

        <section class="section-block">
          <div class="section-head">
            <div>
              <h3>Уведомления</h3>
              <p>{{ orderState.detail.order.receiptEmail || 'Email не указан' }}</p>
            </div>
          </div>
          <dl class="definition-list definition-list-contact">
            <div>
              <dt>Имя</dt>
              <dd>{{ orderState.detail.order.contactName || 'Не указано' }}</dd>
            </div>
            <div>
              <dt>Email</dt>
              <dd>{{ orderState.detail.order.receiptEmail || 'Не указан' }}</dd>
            </div>
            <div>
              <dt>Телефон</dt>
              <dd>{{ orderState.detail.order.contactPhone || 'Не указан' }}</dd>
            </div>
            <div class="definition-list-wide">
              <dt>Адрес</dt>
              <dd>{{ orderState.detail.order.homeAddress || orderState.detail.order.deliveryAddress || 'Не указан' }}</dd>
            </div>
            <div>
              <dt>Отправление</dt>
              <dd v-if="orderState.detail.shipment">
                {{ orderState.detail.shipment.carrier }} · {{ orderState.detail.shipment.trackingNumber }}
              </dd>
              <dd v-else>Не создано</dd>
            </div>
          </dl>
        </section>

        <section v-if="canManageSelectedOrderRma" class="section-block">
          <div class="section-head">
            <div>
              <h3>RMA</h3>
              <p>{{ selectedOrderRmaRequests.length }} запросов</p>
            </div>
          </div>

          <form class="editor-form" @submit.prevent="createRmaRequest">
            <div class="form-grid">
              <label class="ops-field">
                <span>Причина</span>
                <input v-model.trim="orderState.rmaReason" type="text" />
              </label>
              <label class="ops-field">
                <span>Решение клиента</span>
                <input v-model.trim="orderState.rmaDesiredResolution" type="text" />
              </label>
            </div>
            <div class="sticky-actions sticky-actions-inline">
              <button class="button button-secondary" type="submit" :disabled="isSubmitting">
                Создать RMA
              </button>
            </div>
          </form>

          <div v-if="selectedOrderRmaRequests.length" class="card-list card-list-compact">
            <article v-for="rma in selectedOrderRmaRequests" :key="rma.id" class="list-card">
              <div class="list-card-head">
                <strong>{{ rma.rmaNumber }}</strong>
                <span class="pill pill-neutral">{{ rmaStatusLabel(rma.status) }}</span>
              </div>
              <div class="list-card-meta">
                <span>{{ rma.reason || 'Причина не указана' }}</span>
                <span>{{ rma.managerComment || 'Комментарий менеджера не указан' }}</span>
              </div>
              <form v-if="canDecideSelectedOrderRma" class="editor-form rma-decision-form" @submit.prevent="decideRmaRequest(rma)">
                <div class="form-grid">
                  <label class="ops-field ops-field-required">
                    <span>Решение</span>
                    <select v-model="orderState.rmaDecisionForms[rma.id].status">
                      <option value="APPROVED">Одобрить</option>
                      <option value="REJECTED">Отклонить</option>
                    </select>
                  </label>
                  <label class="ops-field">
                    <span>Комментарий</span>
                    <input v-model.trim="orderState.rmaDecisionForms[rma.id].comment" type="text" />
                  </label>
                </div>
                <div class="sticky-actions sticky-actions-inline">
                  <button class="button button-primary" type="submit" :disabled="isSubmitting">
                    Записать решение
                  </button>
                </div>
              </form>
            </article>
          </div>
          <div v-else class="empty-inline">RMA запросов по заказу нет.</div>
        </section>

        <section class="section-block">
          <div class="section-head">
            <div>
              <h3>Состав</h3>
              <p>{{ orderState.detail.order.items?.length || 0 }} строк</p>
            </div>
          </div>
          <div v-if="orderState.detail.order.items?.length" class="card-list">
            <article v-for="item in orderState.detail.order.items" :key="item.id" class="list-card">
              <div class="list-card-head">
                <strong>{{ item.productName || item.variantName || item.variantId }}</strong>
                <span class="pill pill-neutral">{{ item.quantity }} шт.</span>
              </div>
              <p class="list-card-slug">{{ item.sku || item.variantId }}</p>
              <div class="list-card-meta">
                <span>{{ formatMoney(item.unitPrice) }}</span>
              </div>
            </article>
          </div>
        </section>

        <section class="section-block">
          <div class="section-head">
            <div>
              <h3>История статусов</h3>
              <p>{{ orderState.detail.history?.length || 0 }} событий</p>
            </div>
          </div>
          <div v-if="orderState.detail.history?.length" class="card-list">
            <article v-for="event in orderState.detail.history" :key="event.id" class="list-card">
              <div class="list-card-head">
                <strong>{{ orderStatusLabel(event.previousStatus) || 'Создан' }} → {{ orderStatusLabel(event.nextStatus) }}</strong>
                <span class="pill pill-neutral">{{ formatDateTime(event.createdAt) }}</span>
              </div>
              <div class="list-card-meta">
                <span>{{ event.actor || 'system' }}</span>
                <span>{{ event.note || 'Без комментария' }}</span>
              </div>
            </article>
          </div>
          <div v-else class="empty-inline">История пока пуста.</div>
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
