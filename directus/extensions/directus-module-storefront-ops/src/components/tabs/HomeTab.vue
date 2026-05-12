<template>
  <StorefrontOpsTabShell
    :active-detail-open="activeDetailOpen"
    :active-tab-has-master-detail="activeTabHasMasterDetail"
    :active-tab-label="activeTabLabel"
    :close-active-detail="closeActiveDetail"
  >
    <template #list>
      <HomeSectionOutline
        v-model:preset="homeState.presetToAdd"
        :sections="homeForm.sections"
        :selected-index="homeState.selectedSectionIndex"
        :presets="HOME_SECTION_PRESETS"
        :loading="isTabLoading('home')"
        :is-submitting="isSubmitting"
        :status-label="homeStatusLabel"
        :section-label="homeSectionLabel"
        :section-type-label="homeSectionTypeLabel"
        :can-preview="Boolean(accessState.previewBaseUrl)"
        @save="saveHomeContent"
        @preview="openStorefrontPreview('page')"
        @add="addHomeSectionPreset"
        @select="selectAndScrollHomeSection"
        @move="moveHomeSection"
      />
    </template>

    <template #detail>
      <section ref="homeDetailCard" class="detail-card">
        <header class="detail-header">
          <div>
            <p class="detail-kicker">Главная</p>
            <h2>Контент главной страницы</h2>
            <p class="detail-subtitle">Hero, категории, подборки и повторяемые карточки редактируются здесь без сырых ключей Directus.</p>
          </div>
          <div class="detail-header-actions">
            <button class="button button-primary" type="button" :disabled="isSubmitting || loading.home" @click="saveHomeContent">
              Сохранить главную
            </button>
          </div>
        </header>

        <div v-if="isTabLoading('home')" class="empty-state">
          <strong>Загружаю контент главной</strong>
        </div>

        <form v-else class="editor-form" @submit.prevent="saveHomeContent">
          <section class="section-block">
            <div class="section-head">
              <div>
                <h3>Страница</h3>
                <p>Общие заголовки и SEO для маршрута /.</p>
              </div>
            </div>
            <div class="form-grid">
              <label class="ops-field">
                <span>Статус</span>
                <select v-model="homeForm.page.status">
                  <option v-for="option in HOME_STATUS_OPTIONS" :key="option.value" :value="option.value">
                    {{ option.label }}
                  </option>
                </select>
              </label>
              <label class="ops-field">
                <span>Заголовок страницы</span>
                <input v-model.trim="homeForm.page.title" type="text" />
              </label>
              <label class="ops-field">
                <span>SEO title</span>
                <input v-model.trim="homeForm.page.seoTitle" type="text" />
              </label>
              <label class="ops-field">
                <span>SEO description</span>
                <textarea v-model.trim="homeForm.page.seoDescription" rows="3"></textarea>
              </label>
              <label class="ops-field full-span">
                <span>Краткое описание</span>
                <textarea v-model.trim="homeForm.page.summary" rows="3"></textarea>
              </label>
            </div>
          </section>

          <section class="section-block">
            <div class="section-head">
              <div>
                <h3>Баннер в шапке</h3>
                <p>Текст верхней плашки сайта. Если текст пустой или статус не опубликован, плашка не показывается.</p>
              </div>
            </div>
            <div class="form-grid">
              <label class="ops-field">
                <span>Статус</span>
                <select v-model="homeForm.announcementBanner.status">
                  <option v-for="option in HOME_STATUS_OPTIONS" :key="option.value" :value="option.value">
                    {{ option.label }}
                  </option>
                </select>
              </label>
              <label class="ops-field">
                <span>Название для редактора</span>
                <input v-model.trim="homeForm.announcementBanner.internalName" type="text" />
              </label>
              <label class="ops-field full-span">
                <span>Текст баннера</span>
                <input v-model.trim="homeForm.announcementBanner.shortText" type="text" placeholder="Например: Новые условия доставки опубликованы" />
              </label>
            </div>
          </section>

          <div v-if="!selectedHomeSection" class="empty-inline">Выберите секцию или добавьте новый блок.</div>

          <section
            v-for="(section, sectionIndex) in selectedHomeSectionList"
            :key="section.clientId || section.id || section.migrationKey || section.sort"
            class="section-block"
            :class="{ 'section-block-active': homeForm.sections[sectionIndex] === selectedHomeSection }"
            :data-home-section-index="sectionIndex"
          >
            <div class="section-head">
              <div>
                <h3>{{ homeSectionLabel(section) }}</h3>
                <p>{{ homeSectionTypeLabel(section.sectionType) }} · {{ homeStatusLabel(section.status) }} · {{ sectionIndex + 1 }} из {{ homeForm.sections.length }}</p>
              </div>
              <div class="detail-header-actions">
                <button class="button button-secondary button-small" type="button" :disabled="sectionIndex === 0" @click="moveHomeSection(sectionIndex, -1)">
                  ↑
                </button>
                <button class="button button-secondary button-small" type="button" :disabled="sectionIndex === homeForm.sections.length - 1" @click="moveHomeSection(sectionIndex, 1)">
                  ↓
                </button>
                <button class="button button-secondary button-small" type="button" @click="selectHomeSection(sectionIndex); duplicateHomeSection(section)">
                  Дублировать
                </button>
                <button class="button button-secondary button-small" type="button" @click="archiveHomeSection(section)">
                  {{ section.status === 'archived' ? 'Вернуть' : 'Архив' }}
                </button>
                <button v-if="!section.id" class="button button-danger button-small" type="button" @click="removeHomeSection(sectionIndex)">
                  Убрать
                </button>
              </div>
            </div>

            <div class="form-grid">
              <label class="ops-field">
                <span>Статус секции</span>
                <select v-model="section.status">
                  <option v-for="option in HOME_STATUS_OPTIONS" :key="option.value" :value="option.value">
                    {{ option.label }}
                  </option>
                </select>
              </label>
              <label class="ops-field">
                <span>Название для редактора</span>
                <input v-model.trim="section.internalName" type="text" />
              </label>
              <label class="ops-field">
                <span>Малый заголовок</span>
                <input v-model.trim="section.eyebrow" type="text" />
              </label>
              <label class="ops-field">
                <span>Заголовок</span>
                <input v-model.trim="section.title" type="text" />
              </label>
              <label class="ops-field">
                <span>Акцент hero</span>
                <input v-model.trim="section.accent" type="text" />
              </label>
              <label class="ops-field">
                <span>Порядок</span>
                <input v-model.number="section.sort" type="number" min="1" />
              </label>
              <label class="ops-field">
                <span>Якорь</span>
                <input v-model.trim="section.anchorId" type="text" placeholder="home-products" />
              </label>
              <label class="ops-field">
                <span>Основная кнопка</span>
                <input v-model.trim="section.primaryCtaLabel" type="text" />
              </label>
              <label class="ops-field">
                <span>Ссылка основной кнопки</span>
                <input v-model.trim="section.primaryCtaUrl" type="text" placeholder="/catalog" />
              </label>
              <label class="ops-field">
                <span>Вторичная кнопка</span>
                <input v-model.trim="section.secondaryCtaLabel" type="text" />
              </label>
              <label class="ops-field">
                <span>Ссылка вторичной кнопки</span>
                <input v-model.trim="section.secondaryCtaUrl" type="text" />
              </label>
              <label class="ops-field">
                <span>Вариант стиля</span>
                <select v-model="section.styleVariant">
                  <option v-for="option in HOME_STYLE_OPTIONS" :key="option.value" :value="option.value">
                    {{ option.label }}
                  </option>
                </select>
              </label>
              <label class="ops-field">
                <span>Вариант макета</span>
                <select v-model="section.layoutVariant">
                  <option v-for="option in HOME_LAYOUT_OPTIONS" :key="option.value" :value="option.value">
                    {{ option.label }}
                  </option>
                </select>
              </label>
              <label class="ops-field full-span">
                <span>Текст блока</span>
                <textarea v-model.trim="section.body" rows="4"></textarea>
              </label>
            </div>

            <section v-if="section.sectionType === 'hero'" class="selector-card">
              <div class="selector-card-head">
                <div>
                  <h3>Товар под фото</h3>
                  <p>Выберите товар для кнопки «Выбор недели» под hero-фотографией.</p>
                </div>
              </div>

              <article v-if="section.featuredProductKey" class="list-card">
                <div class="list-card-head">
                  <strong>{{ homeProductLabel(section.featuredProductKey) }}</strong>
                  <span class="pill pill-neutral">Выбор недели</span>
                </div>
                <img
                  v-if="homeProductImage(section.featuredProductKey)"
                  :src="homeProductImage(section.featuredProductKey)"
                  :alt="homeProductLabel(section.featuredProductKey)"
                  class="home-reference-thumb"
                />
                <p class="list-card-slug">{{ homeProductMeta(section.featuredProductKey) }}</p>
              </article>
              <div v-else class="empty-inline">Товар для hero пока не выбран.</div>

              <div class="form-grid">
                <label class="ops-field">
                  <span>Поиск товара</span>
                  <input v-model.trim="section.featuredProductQuery" type="search" placeholder="Название, бренд, категория" @keyup.enter="searchHomeHeroProducts(section)" />
                </label>
                <label class="ops-field">
                  <span>Выбрать товар</span>
                  <select v-model="section.featuredProductToAdd">
                    <option value="">Выберите товар</option>
                    <option v-for="product in filteredHomeFeaturedProductOptions(section)" :key="product.id" :value="product.slug">
                      {{ product.name }} · {{ product.slug }}
                    </option>
                  </select>
                </label>
                <div class="ops-field">
                  <span>&nbsp;</span>
                  <button class="button button-secondary" type="button" @click="searchHomeHeroProducts(section)">
                    Найти
                  </button>
                </div>
                <div class="ops-field">
                  <span>&nbsp;</span>
                  <button class="button button-secondary" type="button" :disabled="!section.featuredProductToAdd" @click="setHomeHeroFeaturedProduct(section)">
                    Выбрать
                  </button>
                </div>
                <div class="ops-field">
                  <span>&nbsp;</span>
                  <button class="button button-danger" type="button" :disabled="!section.featuredProductKey" @click="clearHomeHeroFeaturedProduct(section)">
                    Убрать
                  </button>
                </div>
              </div>
            </section>

            <section v-if="section.sectionType === 'category_reference_list'" class="selector-card">
              <div class="selector-card-head">
                <div>
                  <h3>Категории на главной</h3>
                  <p>Выберите реальные категории backend-каталога. Публичное название берётся из категории, если поле «Название на карточке» пустое.</p>
                </div>
              </div>

              <div v-if="section.items.length" class="card-list">
                <article v-for="(item, index) in section.items" :key="item.id || item.referenceKey" class="list-card">
                  <div class="list-card-head">
                    <strong>{{ homeCategoryLabel(item.referenceKey) }}</strong>
                    <span class="pill" :class="item.status === 'published' ? 'pill-positive' : 'pill-muted'">
                      {{ homeStatusLabel(item.status) }}
                    </span>
                  </div>
                  <img
                    v-if="homeCategoryImage(item.referenceKey)"
                    :src="homeCategoryImage(item.referenceKey)"
                    :alt="homeCategoryLabel(item.referenceKey)"
                    class="home-reference-thumb"
                  />
                  <p class="list-card-slug">{{ homeCategoryMeta(item.referenceKey) }}</p>
                  <div class="form-grid">
                    <label class="ops-field">
                      <span>Статус карточки</span>
                      <select v-model="item.status">
                        <option v-for="option in HOME_STATUS_OPTIONS" :key="option.value" :value="option.value">
                          {{ option.label }}
                        </option>
                      </select>
                    </label>
                    <label class="ops-field">
                      <span>Название на карточке</span>
                      <input v-model.trim="item.title" type="text" placeholder="Оставьте пустым, чтобы взять из категории" />
                    </label>
                    <label class="ops-field">
                      <span>Метка</span>
                      <input v-model.trim="item.label" type="text" />
                    </label>
                    <label class="ops-field">
                      <span>Описание</span>
                      <textarea v-model.trim="item.description" rows="2"></textarea>
                    </label>
                  </div>
                  <div class="detail-header-actions">
                    <button class="button button-secondary" type="button" :disabled="index === 0" @click="moveHomeItem(section, index, -1)">
                      ↑
                    </button>
                    <button class="button button-secondary" type="button" :disabled="index === section.items.length - 1" @click="moveHomeItem(section, index, 1)">
                      ↓
                    </button>
                    <button class="button button-danger" type="button" @click="removeHomeItem(section, index)">
                      Убрать
                    </button>
                  </div>
                </article>
              </div>
              <div v-else class="empty-inline">Категории пока не выбраны.</div>

              <div class="form-grid">
                <label class="ops-field">
                  <span>Поиск категории</span>
                  <input v-model.trim="homeState.categoryQuery" type="search" placeholder="Название, slug или путь" @keyup.enter="searchHomeCategories" />
                </label>
                <label class="ops-field">
                  <span>Добавить категорию</span>
                  <select v-model="homeState.categoryToAdd">
                    <option value="">Выберите категорию</option>
                    <option v-for="category in filteredHomeCategoryOptions" :key="category.id" :value="category.slug">
                      {{ category.name }} · {{ category.fullPath || category.slug }}
                    </option>
                  </select>
                </label>
                <div class="ops-field">
                  <span>&nbsp;</span>
                  <button class="button button-secondary" type="button" @click="searchHomeCategories">
                    Найти
                  </button>
                </div>
                <div class="ops-field">
                  <span>&nbsp;</span>
                  <button class="button button-secondary" type="button" :disabled="!homeState.categoryToAdd" @click="addHomeCategory(section)">
                    Добавить
                  </button>
                </div>
              </div>
            </section>

            <section v-else-if="section.sectionType === 'product_reference_list'" class="selector-card">
              <div class="selector-card-head">
                <div>
                  <h3>{{ section.layoutVariant === 'shop_the_look' ? 'Shop the look' : 'Товары в секции' }}</h3>
                  <p>Выберите реальные товары backend-каталога. На сайте используются название, цена и фото товара.</p>
                </div>
              </div>
              <div v-if="section.items.length" class="card-list">
                <article v-for="(item, index) in section.items" :key="item.id || item.referenceKey || index" class="list-card">
                  <div class="list-card-head">
                    <strong>{{ homeProductLabel(item.referenceKey) }}</strong>
                    <span class="pill" :class="item.status === 'published' ? 'pill-positive' : 'pill-muted'">
                      {{ homeStatusLabel(item.status) }}
                    </span>
                  </div>
                  <img
                    v-if="homeProductImage(item.referenceKey)"
                    :src="homeProductImage(item.referenceKey)"
                    :alt="homeProductLabel(item.referenceKey)"
                    class="home-reference-thumb"
                  />
                  <p class="list-card-slug">{{ homeProductMeta(item.referenceKey) }}</p>
                  <div class="form-grid">
                    <label class="ops-field">
                      <span>Статус карточки</span>
                      <select v-model="item.status">
                        <option v-for="option in HOME_STATUS_OPTIONS" :key="option.value" :value="option.value">
                          {{ option.label }}
                        </option>
                      </select>
                    </label>
                    <label class="ops-field">
                      <span>Название на карточке</span>
                      <input v-model.trim="item.title" type="text" placeholder="Оставьте пустым, чтобы взять из товара" />
                    </label>
                    <label class="ops-field">
                      <span>Метка</span>
                      <input v-model.trim="item.label" type="text" />
                    </label>
                    <label class="ops-field">
                      <span>Описание</span>
                      <textarea v-model.trim="item.description" rows="2"></textarea>
                    </label>
                  </div>
                  <div class="detail-header-actions">
                    <button class="button button-secondary" type="button" :disabled="index === 0" @click="moveHomeItem(section, index, -1)">
                      ↑
                    </button>
                    <button class="button button-secondary" type="button" :disabled="index === section.items.length - 1" @click="moveHomeItem(section, index, 1)">
                      ↓
                    </button>
                    <button class="button button-danger" type="button" @click="removeHomeItem(section, index)">
                      Убрать
                    </button>
                  </div>
                </article>
              </div>
              <div v-else class="empty-inline">Товары пока не выбраны.</div>

              <div class="form-grid">
                <label class="ops-field">
                  <span>Поиск товара</span>
                  <input v-model.trim="section.productQuery" type="search" placeholder="Название, бренд, категория" @keyup.enter="searchHomeProducts(section)" />
                </label>
                <label class="ops-field">
                  <span>Добавить товар</span>
                  <select v-model="section.productToAdd" @focus="ensureHomeProductOptions(section)">
                    <option value="">Выберите товар</option>
                    <option v-for="product in filteredHomeProductOptions(section)" :key="product.id" :value="product.slug">
                      {{ product.name }} · {{ product.slug }}
                    </option>
                  </select>
                </label>
                <div class="ops-field">
                  <span>&nbsp;</span>
                  <button class="button button-secondary" type="button" @click="searchHomeProducts(section)">
                    Найти
                  </button>
                </div>
                <div class="ops-field">
                  <span>&nbsp;</span>
                  <button class="button button-secondary" type="button" @click="addHomeProduct(section)">
                    Добавить
                  </button>
                </div>
              </div>
            </section>

            <section v-else-if="section.sectionType === 'collection_teaser'" class="selector-card">
              <div class="selector-card-head">
                <div>
                  <h3>Витринные подборки</h3>
                  <p>Подключите reusable-подборки или создайте гибридную подборку с ручными товарами и backend-правилом.</p>
                </div>
                <button class="button button-secondary" type="button" @click="startCreateHomeCollection">
                  Новая подборка
                </button>
              </div>

              <div v-if="section.items.length" class="card-list">
                <article v-for="(item, index) in section.items" :key="item.clientId || item.id || item.referenceKey || index" class="list-card">
                  <div class="list-card-head">
                    <strong>{{ homeCollectionLabel(item.referenceKey) }}</strong>
                    <span class="pill" :class="item.status === 'published' ? 'pill-positive' : 'pill-muted'">
                      {{ homeStatusLabel(item.status) }}
                    </span>
                  </div>
                  <p class="list-card-slug">{{ homeCollectionMeta(item.referenceKey) }}</p>
                  <div class="form-grid">
                    <label class="ops-field">
                      <span>Статус ссылки</span>
                      <select v-model="item.status">
                        <option v-for="option in HOME_STATUS_OPTIONS" :key="option.value" :value="option.value">
                          {{ option.label }}
                        </option>
                      </select>
                    </label>
                    <label class="ops-field">
                      <span>Заголовок секции</span>
                      <input v-model.trim="item.title" type="text" placeholder="Оставьте пустым, чтобы взять из подборки" />
                    </label>
                  </div>
                  <div class="detail-header-actions">
                    <button class="button button-secondary" type="button" :disabled="index === 0" @click="moveHomeItem(section, index, -1)">
                      ↑
                    </button>
                    <button class="button button-secondary" type="button" :disabled="index === section.items.length - 1" @click="moveHomeItem(section, index, 1)">
                      ↓
                    </button>
                    <button class="button button-secondary" type="button" @click="editHomeCollection(item.referenceKey)">
                      Редактировать подборку
                    </button>
                    <button class="button button-danger" type="button" @click="removeHomeItem(section, index)">
                      Убрать
                    </button>
                  </div>
                </article>
              </div>
              <div v-else class="empty-inline">Подборки пока не выбраны.</div>

              <div class="form-grid">
                <label class="ops-field">
                  <span>Добавить подборку</span>
                  <select v-model="homeState.collectionToAdd">
                    <option value="">Выберите подборку</option>
                    <option v-for="collection in filteredHomeCollectionOptions" :key="collection.id || collection.key" :value="collection.key">
                      {{ collection.title || collection.key }} · {{ collection.key }}
                    </option>
                  </select>
                </label>
                <div class="ops-field">
                  <span>&nbsp;</span>
                  <button class="button button-secondary" type="button" :disabled="!homeState.collectionToAdd" @click="addHomeCollection(section)">
                    Добавить
                  </button>
                </div>
              </div>

              <section v-if="homeState.collectionDraft" class="section-block section-block-compact">
                <div class="section-head">
                  <div>
                    <h3>{{ homeState.collectionDraft.id ? 'Редактирование подборки' : 'Новая подборка' }}</h3>
                    <p>{{ homeState.collectionDraft.key }}</p>
                  </div>
                  <div class="detail-header-actions">
                    <button class="button button-primary" type="button" :disabled="isSubmitting" @click="saveHomeCollectionDraft">
                      Сохранить подборку
                    </button>
                    <button class="button button-secondary" type="button" @click="cancelHomeCollectionDraft">
                      Закрыть
                    </button>
                  </div>
                </div>
                <div class="form-grid">
                  <label class="ops-field">
                    <span>Статус</span>
                    <select v-model="homeState.collectionDraft.status">
                      <option v-for="option in HOME_STATUS_OPTIONS" :key="option.value" :value="option.value">
                        {{ option.label }}
                      </option>
                    </select>
                  </label>
                  <label class="ops-field">
                    <span>Ключ</span>
                    <input v-model.trim="homeState.collectionDraft.key" type="text" />
                  </label>
                  <label class="ops-field">
                    <span>Название</span>
                    <input v-model.trim="homeState.collectionDraft.title" type="text" />
                  </label>
                  <label class="ops-field">
                    <span>Режим</span>
                    <select v-model="homeState.collectionDraft.mode">
                      <option v-for="option in STOREFRONT_COLLECTION_MODES" :key="option.value" :value="option.value">
                        {{ option.label }}
                      </option>
                    </select>
                  </label>
                  <label class="ops-field">
                    <span>Правило</span>
                    <select v-model="homeState.collectionDraft.ruleType">
                      <option v-for="option in STOREFRONT_COLLECTION_RULE_TYPES" :key="option.value" :value="option.value">
                        {{ option.label }}
                      </option>
                    </select>
                  </label>
                  <label class="ops-field">
                    <span>Лимит</span>
                    <input v-model.number="homeState.collectionDraft.limit" type="number" min="1" max="48" />
                  </label>
                  <label class="ops-field">
                    <span>Сортировка</span>
                    <select v-model="homeState.collectionDraft.sortMode">
                      <option v-for="option in STOREFRONT_COLLECTION_SORT_MODES" :key="option.value" :value="option.value">
                        {{ option.label }}
                      </option>
                    </select>
                  </label>
                  <label class="ops-field">
                    <span>CTA</span>
                    <input v-model.trim="homeState.collectionDraft.primaryCtaLabel" type="text" />
                  </label>
                  <label class="ops-field">
                    <span>CTA ссылка</span>
                    <input v-model.trim="homeState.collectionDraft.primaryCtaUrl" type="text" />
                  </label>
                  <label class="ops-field full-span">
                    <span>Описание</span>
                    <textarea v-model.trim="homeState.collectionDraft.description" rows="3"></textarea>
                  </label>
                </div>

                <div class="form-grid">
                  <label class="ops-field">
                    <span>Поиск товара</span>
                    <input v-model.trim="homeState.collectionProductQuery" type="search" @keyup.enter="searchHomeCollectionProducts" />
                  </label>
                  <label class="ops-field">
                    <span>Добавить товар</span>
                    <select v-model="homeState.collectionProductToAdd">
                      <option value="">Выберите товар</option>
                      <option v-for="product in filteredCollectionProductOptions" :key="product.id" :value="product.slug">
                        {{ product.name }} · {{ product.slug }}
                      </option>
                    </select>
                  </label>
                  <div class="ops-field">
                    <span>&nbsp;</span>
                    <button class="button button-secondary" type="button" @click="searchHomeCollectionProducts">Найти</button>
                  </div>
                  <div class="ops-field">
                    <span>&nbsp;</span>
                    <button class="button button-secondary" type="button" :disabled="!homeState.collectionProductToAdd" @click="addHomeCollectionProductRule">Добавить</button>
                  </div>
                </div>

                <div class="form-grid">
                  <label class="ops-field">
                    <span>Поиск категории</span>
                    <input v-model.trim="homeState.collectionCategoryQuery" type="search" @keyup.enter="searchHomeCollectionCategories" />
                  </label>
                  <label class="ops-field">
                    <span>Добавить категорию</span>
                    <select v-model="homeState.collectionCategoryToAdd">
                      <option value="">Выберите категорию</option>
                      <option v-for="category in filteredCollectionCategoryOptions" :key="category.id" :value="category.slug">
                        {{ category.name }} · {{ category.fullPath || category.slug }}
                      </option>
                    </select>
                  </label>
                  <div class="ops-field">
                    <span>&nbsp;</span>
                    <button class="button button-secondary" type="button" @click="searchHomeCollectionCategories">Найти</button>
                  </div>
                  <div class="ops-field">
                    <span>&nbsp;</span>
                    <button class="button button-secondary" type="button" :disabled="!homeState.collectionCategoryToAdd" @click="addHomeCollectionCategoryRule">Добавить</button>
                  </div>
                </div>

                <div v-if="homeState.collectionDraft.rules.length" class="card-list">
                  <article v-for="(rule, index) in homeState.collectionDraft.rules" :key="rule.clientId || rule.id || index" class="list-card">
                    <div class="list-card-head">
                      <strong>{{ rule.entityKind === 'category' ? homeCategoryLabel(rule.entityKey) : homeProductLabel(rule.entityKey) }}</strong>
                      <span class="pill pill-neutral">{{ rule.behavior === 'exclude' ? 'Исключить' : 'Закрепить' }}</span>
                    </div>
                    <p class="list-card-slug">{{ rule.entityKind }} · {{ rule.entityKey }}</p>
                    <div class="form-grid">
                      <label class="ops-field">
                        <span>Поведение</span>
                        <select v-model="rule.behavior">
                          <option value="pin">Закрепить</option>
                          <option value="exclude">Исключить</option>
                        </select>
                      </label>
                      <label class="ops-field">
                        <span>Статус</span>
                        <select v-model="rule.status">
                          <option v-for="option in HOME_STATUS_OPTIONS" :key="option.value" :value="option.value">
                            {{ option.label }}
                          </option>
                        </select>
                      </label>
                    </div>
                    <div class="detail-header-actions">
                      <button class="button button-secondary" type="button" :disabled="index === 0" @click="moveHomeCollectionRule(index, -1)">↑</button>
                      <button class="button button-secondary" type="button" :disabled="index === homeState.collectionDraft.rules.length - 1" @click="moveHomeCollectionRule(index, 1)">↓</button>
                      <button class="button button-danger" type="button" @click="removeHomeCollectionRule(index)">Убрать</button>
                    </div>
                  </article>
                </div>
                <div v-else class="empty-inline">В подборке нет ручных правил.</div>
              </section>
            </section>

            <section v-else-if="section.items.length || ['hero', 'feature_list', 'collection_teaser', 'banner_group'].includes(section.sectionType)" class="selector-card">
              <div class="selector-card-head">
                <div>
                  <h3>Элементы секции</h3>
                  <p>Карточки, преимущества и ссылки для этого блока.</p>
                </div>
                <button class="button button-secondary" type="button" @click="addHomeItem(section)">
                  Добавить элемент
                </button>
              </div>
              <div v-if="section.items.length" class="card-list">
                <article v-for="(item, index) in section.items" :key="item.id || item.migrationKey || index" class="list-card">
                  <div class="list-card-head">
                    <strong>{{ item.title || item.label || `Элемент ${index + 1}` }}</strong>
                    <span class="pill pill-neutral">{{ homeStatusLabel(item.status) }}</span>
                  </div>
                  <div class="form-grid">
                    <label class="ops-field">
                      <span>Статус</span>
                      <select v-model="item.status">
                        <option v-for="option in HOME_STATUS_OPTIONS" :key="option.value" :value="option.value">
                          {{ option.label }}
                        </option>
                      </select>
                    </label>
                    <label class="ops-field">
                      <span>Метка</span>
                      <input v-model.trim="item.label" type="text" />
                    </label>
                    <label class="ops-field">
                      <span>Заголовок</span>
                      <input v-model.trim="item.title" type="text" />
                    </label>
                    <label class="ops-field">
                      <span>Ссылка</span>
                      <input v-model.trim="item.url" type="text" />
                    </label>
                    <label class="ops-field full-span">
                      <span>Описание</span>
                      <textarea v-model.trim="item.description" rows="3"></textarea>
                    </label>
                  </div>
                  <div class="detail-header-actions">
                    <button class="button button-secondary" type="button" :disabled="index === 0" @click="moveHomeItem(section, index, -1)">
                      ↑
                    </button>
                    <button class="button button-secondary" type="button" :disabled="index === section.items.length - 1" @click="moveHomeItem(section, index, 1)">
                      ↓
                    </button>
                    <button class="button button-danger" type="button" @click="removeHomeItem(section, index)">
                      Убрать
                    </button>
                  </div>
                </article>
              </div>
              <div v-else class="empty-inline">Элементы пока не добавлены.</div>
            </section>
          </section>

          <div class="sticky-actions home-action-dock">
            <button class="button button-primary" type="submit" :disabled="isSubmitting">
              Сохранить главную
            </button>
          </div>
        </form>
      </section>
    </template>
  </StorefrontOpsTabShell>
</template>

<script setup>
import { nextTick, ref } from 'vue';
import HomeSectionOutline from '../HomeSectionOutline.vue';
import StorefrontOpsTabShell from '../StorefrontOpsTabShell.vue';
import { STOREFRONT_OPS_TAB_PROP_KEYS } from '../../storefront-ops-tab-props.js';

const props = defineProps(STOREFRONT_OPS_TAB_PROP_KEYS);
const homeDetailCard = ref(null);

async function selectAndScrollHomeSection(index) {
  props.selectHomeSection(index);
  await nextTick();
  const container = homeDetailCard.value;
  if (!container) {
    return;
  }
  const target = homeDetailCard.value?.querySelector(`[data-home-section-index="${index}"]`);
  if (!target) {
    return;
  }
  const containerRect = container.getBoundingClientRect();
  const targetRect = target.getBoundingClientRect();
  const offset = targetRect.top - containerRect.top + container.scrollTop - 12;
  container.scrollTo({ top: Math.max(0, offset), behavior: 'smooth' });
}
</script>
