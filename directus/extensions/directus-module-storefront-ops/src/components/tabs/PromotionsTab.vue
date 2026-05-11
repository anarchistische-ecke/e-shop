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
          <h2>Акции</h2>
          <p>{{ promotionState.items.length }} акций · {{ promotionState.promoCodes.length }} кодов</p>
        </div>
        <button class="button button-primary" type="button" @click="startCreatePromotion">
          Новая акция
        </button>
      </div>

      <div v-if="isTabLoading('promotions')" class="empty-state">
        <strong>Загружаю акции</strong>
      </div>
      <div v-else class="card-list">
        <button
          v-for="promotion in promotionState.items"
          :key="promotion.id"
          type="button"
          class="list-card"
          :class="{ active: promotionState.selectedId === promotion.id && promotionState.mode === 'promotion' }"
          @click="selectPromotion(promotion)"
        >
          <div class="list-card-head">
            <strong>{{ promotion.name }}</strong>
            <span class="pill" :class="promotion.activeNow ? 'pill-positive' : 'pill-muted'">{{ promotion.status }}</span>
          </div>
          <p class="list-card-slug">{{ promotion.type }}</p>
          <div class="list-card-meta">
            <span>{{ promotion.startsAt ? formatDateTime(promotion.startsAt) : 'Без начала' }}</span>
            <span>{{ promotion.endsAt ? formatDateTime(promotion.endsAt) : 'Без окончания' }}</span>
          </div>
        </button>
        <button
          v-for="promoCode in promotionState.promoCodes"
          :key="promoCode.id"
          type="button"
          class="list-card"
          :class="{ active: promotionState.selectedPromoCodeId === promoCode.id && promotionState.mode === 'promoCode' }"
          @click="selectPromoCode(promoCode)"
        >
          <div class="list-card-head">
            <strong>{{ promoCode.code }}</strong>
            <span class="pill" :class="promoCode.activeNow ? 'pill-positive' : 'pill-muted'">{{ promoCode.status }}</span>
          </div>
          <p class="list-card-slug">Промокод</p>
          <div class="list-card-meta">
            <span>{{ promoCode.redemptionCount }} использований</span>
            <span>{{ promoCode.maxRedemptions || 'Без лимита' }}</span>
          </div>
        </button>
      </div>
    </template>

    <template #detail>
      <section class="detail-card">
        <header class="detail-header">
          <div>
            <p class="detail-kicker">Акции</p>
            <h2>{{ promotionState.mode === 'promoCode' ? 'Промокод' : 'Акция' }}</h2>
            <p class="detail-subtitle">Directus редактирует только backend-акции. Пороговые скидки DSC-01 фиксированы в API.</p>
          </div>
          <div class="detail-header-actions">
            <button class="button button-secondary" type="button" @click="startCreatePromotion">
              Акция
            </button>
            <button class="button button-secondary" type="button" @click="startCreatePromoCode">
              Промокод
            </button>
          </div>
        </header>

        <form v-if="promotionState.mode !== 'promoCode'" class="editor-form" @submit.prevent="submitPromotion">
          <div class="form-grid">
            <label class="ops-field ops-field-required">
              <span>Название</span>
              <input v-model.trim="promotionForm.name" type="text" />
            </label>
            <div class="inline-note">
              Тип акции: PRODUCT_SALE. Скидки по сумме корзины: 50 000 ₽ → 5%, 65 000 ₽ → 7%, 100 000 ₽ → 10%.
            </div>
          </div>
          <div class="form-grid form-grid-three">
            <label class="ops-field">
              <span>Статус</span>
              <select v-model="promotionForm.status">
                <option value="ACTIVE">ACTIVE</option>
                <option value="DRAFT">DRAFT</option>
                <option value="INACTIVE">INACTIVE</option>
              </select>
            </label>
            <label class="ops-field">
              <span>Начало</span>
              <input v-model="promotionForm.startsAt" type="datetime-local" />
            </label>
            <label class="ops-field">
              <span>Окончание</span>
              <input v-model="promotionForm.endsAt" type="datetime-local" />
            </label>
          </div>
          <div class="form-grid">
            <label class="ops-field">
              <span>Цена распродажи, ₽</span>
              <input v-model.number="promotionForm.salePriceAmount" type="number" min="0" step="0.01" />
            </label>
            <label class="ops-field">
              <span>Скидка, %</span>
              <input v-model.number="promotionForm.discountPercent" type="number" min="0" max="100" step="1" />
            </label>
          </div>
          <section class="selector-card">
            <div class="selector-card-head">
              <div>
                <h3>Цели распродажи</h3>
                <p>Можно указать несколько товаров, вариантов, категорий или брендов.</p>
              </div>
              <button class="button button-secondary" type="button" @click="addPromotionTarget">
                Добавить цель
              </button>
            </div>
            <div v-if="promotionForm.targets.length" class="spec-item-list">
              <div
                v-for="(target, targetIndex) in promotionForm.targets"
                :key="`promotion-target-${targetIndex}`"
                class="spec-item-row"
              >
                <label class="ops-field">
                  <span>Тип цели</span>
                  <select v-model="target.targetKind">
                    <option value="VARIANT">VARIANT</option>
                    <option value="PRODUCT">PRODUCT</option>
                    <option value="BRAND">BRAND</option>
                    <option value="CATEGORY">CATEGORY</option>
                  </select>
                </label>
                <label class="ops-field">
                  <span>Ключ цели</span>
                  <input v-model.trim="target.targetKey" type="text" placeholder="sku, slug или UUID" />
                </label>
                <button
                  class="button button-danger button-small spec-item-remove"
                  type="button"
                  @click="removePromotionTarget(targetIndex)"
                >
                  Удалить
                </button>
              </div>
            </div>
            <div v-else class="empty-inline">Без целей акция применяется ко всем товарам.</div>
          </section>
          <label class="ops-field">
            <span>Описание</span>
            <textarea v-model="promotionForm.description" rows="4" />
          </label>
          <div class="sticky-actions">
            <button class="button button-primary" type="submit" :disabled="isSubmitting">
              {{ promotionForm.id ? 'Сохранить акцию' : 'Создать акцию' }}
            </button>
            <button v-if="promotionForm.id" class="button button-danger" type="button" :disabled="isSubmitting" @click="deletePromotion">
              Удалить
            </button>
          </div>
        </form>

        <form v-else class="editor-form" @submit.prevent="submitPromoCode">
          <div class="form-grid">
            <label class="ops-field ops-field-required">
              <span>Код</span>
              <input v-model.trim="promoCodeForm.code" type="text" />
            </label>
            <label class="ops-field">
              <span>Статус</span>
              <select v-model="promoCodeForm.status">
                <option value="ACTIVE">ACTIVE</option>
                <option value="INACTIVE">INACTIVE</option>
              </select>
            </label>
          </div>
          <div class="form-grid form-grid-three">
            <label class="ops-field">
              <span>Скидка, %</span>
              <input v-model.number="promoCodeForm.discountPercent" type="number" min="0" max="100" step="1" />
            </label>
            <label class="ops-field">
              <span>Скидка суммой, ₽</span>
              <input v-model.number="promoCodeForm.discountAmount" type="number" min="0" step="0.01" />
            </label>
            <label class="ops-field">
              <span>Порог, ₽</span>
              <input v-model.number="promoCodeForm.thresholdAmount" type="number" min="0" step="0.01" />
            </label>
          </div>
          <div class="form-grid form-grid-three">
            <label class="ops-field">
              <span>Начало</span>
              <input v-model="promoCodeForm.startsAt" type="datetime-local" />
            </label>
            <label class="ops-field">
              <span>Окончание</span>
              <input v-model="promoCodeForm.endsAt" type="datetime-local" />
            </label>
            <label class="ops-field">
              <span>Лимит</span>
              <input v-model.number="promoCodeForm.maxRedemptions" type="number" min="0" step="1" />
            </label>
          </div>
          <label class="ops-field">
            <span>Описание</span>
            <textarea v-model="promoCodeForm.description" rows="4" />
          </label>
          <div class="sticky-actions">
            <button class="button button-primary" type="submit" :disabled="isSubmitting">
              {{ promoCodeForm.id ? 'Сохранить промокод' : 'Создать промокод' }}
            </button>
            <button v-if="promoCodeForm.id" class="button button-danger" type="button" :disabled="isSubmitting" @click="deletePromoCode">
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
