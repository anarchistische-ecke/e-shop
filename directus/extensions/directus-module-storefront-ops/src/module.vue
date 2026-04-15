<template>
  <div class="storefront-ops">
    <header class="hero">
      <div>
        <p class="eyebrow">Backend-owned catalogue</p>
        <h1>Storefront Ops</h1>
        <p class="lede">
          Manage products, categories, brands, variants, and inventory through the backend bridge while keeping
          Directus as the storefront CMS and operator shell.
        </p>
      </div>
      <div class="hero-actions">
        <button class="primary" type="button" @click="refreshAll" :disabled="isLoading">Refresh</button>
      </div>
    </header>

    <section class="panel">
      <div class="panel-header">
        <h2>Overview</h2>
        <p>Directus-native content stays in the standard collections. Use this module for backend-owned commerce data.</p>
      </div>
      <div class="stats">
        <article class="stat"><span>Products</span><strong>{{ products.length }}</strong></article>
        <article class="stat"><span>Categories</span><strong>{{ categories.length }}</strong></article>
        <article class="stat"><span>Brands</span><strong>{{ brands.length }}</strong></article>
      </div>
      <div class="links">
        <a href="#products">Products</a>
        <a href="#categories">Categories</a>
        <a href="#brands">Brands</a>
        <a href="#inventory">Inventory</a>
      </div>
      <p v-if="errorMessage" class="error">{{ errorMessage }}</p>
    </section>

    <section id="products" class="panel">
      <div class="panel-header">
        <div>
          <h2>Products</h2>
          <p>Create or update backend products and variants. Published storefront overlays remain in Directus collections.</p>
        </div>
        <input v-model.trim="productSearch" type="search" placeholder="Search products by name or slug" />
      </div>

      <div class="grid">
        <div class="list">
          <button
            v-for="product in filteredProducts"
            :key="product.id"
            type="button"
            class="list-item"
            :class="{ active: selectedProduct?.id === product.id }"
            @click="selectProduct(product)"
          >
            <span>{{ product.name }}</span>
            <small>{{ product.slug }}</small>
          </button>
        </div>

        <div class="editor">
          <form class="form" @submit.prevent="submitProduct">
            <h3>{{ productForm.id ? 'Edit product' : 'Create product' }}</h3>
            <label>
              <span>Name</span>
              <input v-model.trim="productForm.name" required />
            </label>
            <label>
              <span>Slug</span>
              <input v-model.trim="productForm.slug" required />
            </label>
            <label>
              <span>Description</span>
              <textarea v-model="productForm.description" rows="4" />
            </label>
            <label>
              <span>Brand slug</span>
              <input v-model.trim="productForm.brand" />
            </label>
            <label>
              <span>Category slugs (comma-separated)</span>
              <input v-model="productForm.categoriesCsv" />
            </label>
            <label class="checkbox">
              <input v-model="productForm.isActive" type="checkbox" />
              <span>Active on storefront</span>
            </label>
            <div class="actions">
              <button class="primary" type="submit" :disabled="isSubmitting">{{ productForm.id ? 'Save product' : 'Create product' }}</button>
              <button type="button" @click="resetProductForm">Reset</button>
              <button v-if="productForm.id" type="button" class="danger" :disabled="isSubmitting" @click="deleteProduct">
                Delete
              </button>
            </div>
          </form>

          <div v-if="selectedProduct" class="subpanel">
            <div class="subpanel-header">
              <h3>Variants</h3>
              <p>{{ selectedProduct.name }}</p>
            </div>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>SKU</th>
                    <th>Price</th>
                    <th>Stock</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="variant in selectedProduct.variants || []" :key="variant.id">
                    <td>{{ variant.name }}</td>
                    <td>{{ variant.sku }}</td>
                    <td>{{ variant.price?.amount || 0 }} {{ variant.price?.currency || 'RUB' }}</td>
                    <td>{{ variant.stock }}</td>
                    <td><button type="button" @click="loadVariantForm(variant)">Edit</button></td>
                  </tr>
                  <tr v-if="!selectedProduct.variants?.length">
                    <td colspan="5">No variants yet.</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <form class="form compact" @submit.prevent="submitVariant">
              <h4>{{ variantForm.id ? 'Edit variant' : 'Add variant' }}</h4>
              <label><span>SKU</span><input v-model.trim="variantForm.sku" :disabled="Boolean(variantForm.id)" required /></label>
              <label><span>Name</span><input v-model.trim="variantForm.name" required /></label>
              <label><span>Price amount</span><input v-model.number="variantForm.amount" type="number" min="0" required /></label>
              <label><span>Currency</span><input v-model.trim="variantForm.currency" required /></label>
              <label><span>Stock</span><input v-model.number="variantForm.stock" type="number" required /></label>
              <div class="actions">
                <button class="primary" type="submit" :disabled="isSubmitting || !selectedProduct?.id">{{ variantForm.id ? 'Save variant' : 'Add variant' }}</button>
                <button type="button" @click="resetVariantForm">Reset</button>
              </div>
            </form>
          </div>
        </div>
      </div>
    </section>

    <section id="categories" class="panel">
      <div class="panel-header">
        <div>
          <h2>Categories</h2>
          <p>Manage backend taxonomy here. Trees, slugs, and active flags remain backend-owned.</p>
        </div>
      </div>
      <div class="grid">
        <div class="list">
          <button
            v-for="category in categories"
            :key="category.id"
            type="button"
            class="list-item"
            :class="{ active: selectedCategory?.id === category.id }"
            @click="selectCategory(category)"
          >
            <span>{{ category.name }}</span>
            <small>{{ category.slug }}</small>
          </button>
        </div>
        <form class="form" @submit.prevent="submitCategory">
          <h3>{{ categoryForm.id ? 'Edit category' : 'Create category' }}</h3>
          <label><span>Name</span><input v-model.trim="categoryForm.name" required /></label>
          <label><span>Slug</span><input v-model.trim="categoryForm.slug" required /></label>
          <label><span>Description</span><textarea v-model="categoryForm.description" rows="3" /></label>
          <label><span>Parent category ID</span><input v-model.trim="categoryForm.parentId" /></label>
          <label><span>Position</span><input v-model.number="categoryForm.position" type="number" min="0" /></label>
          <label class="checkbox"><input v-model="categoryForm.isActive" type="checkbox" /><span>Active on storefront</span></label>
          <div class="actions">
            <button class="primary" type="submit" :disabled="isSubmitting">{{ categoryForm.id ? 'Save category' : 'Create category' }}</button>
            <button type="button" @click="resetCategoryForm">Reset</button>
            <button v-if="categoryForm.id" type="button" class="danger" :disabled="isSubmitting" @click="deleteCategory">
              Delete
            </button>
          </div>
        </form>
      </div>
    </section>

    <section id="brands" class="panel">
      <div class="panel-header">
        <div>
          <h2>Brands</h2>
          <p>Backend-owned brand catalogue. Use Directus CMS collections for editorial brand storytelling, not source-of-truth brand rows.</p>
        </div>
      </div>
      <div class="grid">
        <div class="list">
          <button
            v-for="brand in brands"
            :key="brand.id"
            type="button"
            class="list-item"
            :class="{ active: selectedBrand?.id === brand.id }"
            @click="selectBrand(brand)"
          >
            <span>{{ brand.name }}</span>
            <small>{{ brand.slug }}</small>
          </button>
        </div>
        <form class="form" @submit.prevent="submitBrand">
          <h3>{{ brandForm.id ? 'Edit brand' : 'Create brand' }}</h3>
          <label><span>Name</span><input v-model.trim="brandForm.name" required /></label>
          <label><span>Slug</span><input v-model.trim="brandForm.slug" required /></label>
          <label><span>Description</span><textarea v-model="brandForm.description" rows="3" /></label>
          <div class="actions">
            <button class="primary" type="submit" :disabled="isSubmitting">{{ brandForm.id ? 'Save brand' : 'Create brand' }}</button>
            <button type="button" @click="resetBrandForm">Reset</button>
            <button v-if="brandForm.id" type="button" class="danger" :disabled="isSubmitting" @click="deleteBrand">
              Delete
            </button>
          </div>
        </form>
      </div>
    </section>

    <section id="inventory" class="panel">
      <div class="panel-header">
        <div>
          <h2>Inventory Adjustment</h2>
          <p>Use a dedicated idempotency key for every stock change.</p>
        </div>
      </div>
      <form class="form compact" @submit.prevent="submitInventoryAdjustment">
        <label><span>Variant ID</span><input v-model.trim="inventoryForm.variantId" required /></label>
        <label><span>Delta</span><input v-model.number="inventoryForm.delta" type="number" required /></label>
        <label><span>Reason</span><input v-model.trim="inventoryForm.reason" /></label>
        <label><span>Idempotency key</span><input v-model.trim="inventoryForm.idempotencyKey" required /></label>
        <div class="actions">
          <button class="primary" type="submit" :disabled="isSubmitting">Apply adjustment</button>
          <button type="button" @click="resetInventoryForm">New key</button>
        </div>
      </form>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useApi } from '@directus/extensions-sdk';

const api = useApi();

const errorMessage = ref('');
const isLoading = ref(false);
const isSubmitting = ref(false);
const productSearch = ref('');

const products = ref([]);
const categories = ref([]);
const brands = ref([]);

const selectedProduct = ref(null);
const selectedCategory = ref(null);
const selectedBrand = ref(null);

const productForm = reactive({
  id: '',
  name: '',
  slug: '',
  description: '',
  brand: '',
  categoriesCsv: '',
  isActive: true,
});

const variantForm = reactive({
  id: '',
  sku: '',
  name: '',
  amount: 0,
  currency: 'RUB',
  stock: 0,
});

const categoryForm = reactive({
  id: '',
  name: '',
  slug: '',
  description: '',
  parentId: '',
  position: 0,
  isActive: true,
});

const brandForm = reactive({
  id: '',
  name: '',
  slug: '',
  description: '',
});

const inventoryForm = reactive({
  variantId: '',
  delta: 0,
  reason: '',
  idempotencyKey: crypto.randomUUID(),
});

const filteredProducts = computed(() => {
  const query = productSearch.value.trim().toLowerCase();
  if (!query) {
    return products.value;
  }
  return products.value.filter((product) => {
    return [product.name, product.slug].some((value) => String(value || '').toLowerCase().includes(query));
  });
});

async function request(path, options = {}) {
  const response = await api.request({
    url: `/storefront-ops-bridge${path}`,
    method: options.method || 'GET',
    data: options.data,
    params: options.params,
  });
  return response.data;
}

function setError(error) {
  errorMessage.value = error?.response?.data?.error || error?.response?.data?.message || error?.message || 'Unexpected error';
}

function clearError() {
  errorMessage.value = '';
}

function resetProductForm() {
  Object.assign(productForm, {
    id: '',
    name: '',
    slug: '',
    description: '',
    brand: '',
    categoriesCsv: '',
    isActive: true,
  });
}

function resetVariantForm() {
  Object.assign(variantForm, {
    id: '',
    sku: '',
    name: '',
    amount: 0,
    currency: 'RUB',
    stock: 0,
  });
}

function resetCategoryForm() {
  Object.assign(categoryForm, {
    id: '',
    name: '',
    slug: '',
    description: '',
    parentId: '',
    position: 0,
    isActive: true,
  });
}

function resetBrandForm() {
  Object.assign(brandForm, {
    id: '',
    name: '',
    slug: '',
    description: '',
  });
}

function resetInventoryForm() {
  inventoryForm.variantId = '';
  inventoryForm.delta = 0;
  inventoryForm.reason = '';
  inventoryForm.idempotencyKey = crypto.randomUUID();
}

function loadProductForm(product) {
  selectedProduct.value = product;
  Object.assign(productForm, {
    id: product.id,
    name: product.name || '',
    slug: product.slug || '',
    description: product.description || '',
    brand: product.brand || '',
    categoriesCsv: Array.isArray(product.categories) ? product.categories.map((entry) => entry.slug).join(', ') : '',
    isActive: product.isActive !== false,
  });
  resetVariantForm();
}

function loadVariantForm(variant) {
  Object.assign(variantForm, {
    id: variant.id,
    sku: variant.sku || '',
    name: variant.name || '',
    amount: Number(variant.price?.amount || 0),
    currency: variant.price?.currency || 'RUB',
    stock: Number(variant.stock || 0),
  });
}

function selectProduct(product) {
  loadProductForm(product);
}

function selectCategory(category) {
  selectedCategory.value = category;
  Object.assign(categoryForm, {
    id: category.id,
    name: category.name || '',
    slug: category.slug || '',
    description: category.description || '',
    parentId: category.parentId || '',
    position: Number(category.position || 0),
    isActive: category.isActive !== false,
  });
}

function selectBrand(brand) {
  selectedBrand.value = brand;
  Object.assign(brandForm, {
    id: brand.id,
    name: brand.name || '',
    slug: brand.slug || '',
    description: brand.description || '',
  });
}

async function refreshAll() {
  isLoading.value = true;
  clearError();
  try {
    const [nextProducts, nextCategories, nextBrands] = await Promise.all([
      request('/products', { params: { includeInactive: true } }),
      request('/categories'),
      request('/brands'),
    ]);
    products.value = Array.isArray(nextProducts) ? nextProducts : [];
    categories.value = Array.isArray(nextCategories) ? nextCategories : [];
    brands.value = Array.isArray(nextBrands) ? nextBrands : [];
    if (selectedProduct.value?.id) {
      const fresh = products.value.find((entry) => entry.id === selectedProduct.value.id);
      if (fresh) loadProductForm(fresh);
    }
    if (selectedCategory.value?.id) {
      const fresh = categories.value.find((entry) => entry.id === selectedCategory.value.id);
      if (fresh) selectCategory(fresh);
    }
    if (selectedBrand.value?.id) {
      const fresh = brands.value.find((entry) => entry.id === selectedBrand.value.id);
      if (fresh) selectBrand(fresh);
    }
  } catch (error) {
    setError(error);
  } finally {
    isLoading.value = false;
  }
}

async function submitProduct() {
  isSubmitting.value = true;
  clearError();
  try {
    const payload = {
      name: productForm.name,
      slug: productForm.slug,
      description: productForm.description,
      brand: productForm.brand || null,
      categories: productForm.categoriesCsv
        .split(',')
        .map((entry) => entry.trim())
        .filter(Boolean),
      isActive: Boolean(productForm.isActive),
    };
    const path = productForm.id ? `/products/${productForm.id}` : '/products';
    const method = productForm.id ? 'PUT' : 'POST';
    const saved = await request(path, { method, data: payload });
    await refreshAll();
    if (saved?.id) {
      const fresh = products.value.find((entry) => entry.id === saved.id);
      if (fresh) loadProductForm(fresh);
    } else {
      resetProductForm();
    }
  } catch (error) {
    setError(error);
  } finally {
    isSubmitting.value = false;
  }
}

async function deleteProduct() {
  if (!productForm.id || !window.confirm(`Delete product "${productForm.name || productForm.slug}"?`)) {
    return;
  }

  isSubmitting.value = true;
  clearError();
  try {
    await request(`/products/${productForm.id}`, { method: 'DELETE' });
    selectedProduct.value = null;
    resetProductForm();
    resetVariantForm();
    await refreshAll();
  } catch (error) {
    setError(error);
  } finally {
    isSubmitting.value = false;
  }
}

async function submitVariant() {
  if (!selectedProduct.value?.id) return;
  isSubmitting.value = true;
  clearError();
  try {
    const payload = {
      sku: variantForm.sku,
      name: variantForm.name,
      amount: Number(variantForm.amount || 0),
      currency: variantForm.currency || 'RUB',
      stock: Number(variantForm.stock || 0),
    };
    const path = variantForm.id
      ? `/products/${selectedProduct.value.id}/variants/${variantForm.id}`
      : `/products/${selectedProduct.value.id}/variants`;
    const method = variantForm.id ? 'PUT' : 'POST';
    await request(path, { method, data: payload });
    await refreshAll();
    resetVariantForm();
  } catch (error) {
    setError(error);
  } finally {
    isSubmitting.value = false;
  }
}

async function submitCategory() {
  isSubmitting.value = true;
  clearError();
  try {
    const payload = {
      name: categoryForm.name,
      slug: categoryForm.slug,
      description: categoryForm.description,
      parentId: categoryForm.parentId || null,
      position: Number(categoryForm.position || 0),
      isActive: Boolean(categoryForm.isActive),
    };
    const path = categoryForm.id ? `/categories/${categoryForm.id}` : '/categories';
    const method = categoryForm.id ? 'PUT' : 'POST';
    const saved = await request(path, { method, data: payload });
    await refreshAll();
    if (saved?.id) {
      const fresh = categories.value.find((entry) => entry.id === saved.id);
      if (fresh) selectCategory(fresh);
    } else {
      resetCategoryForm();
    }
  } catch (error) {
    setError(error);
  } finally {
    isSubmitting.value = false;
  }
}

async function deleteCategory() {
  if (!categoryForm.id || !window.confirm(`Delete category "${categoryForm.name || categoryForm.slug}"?`)) {
    return;
  }

  isSubmitting.value = true;
  clearError();
  try {
    await request(`/categories/${categoryForm.id}`, { method: 'DELETE' });
    selectedCategory.value = null;
    resetCategoryForm();
    await refreshAll();
  } catch (error) {
    setError(error);
  } finally {
    isSubmitting.value = false;
  }
}

async function submitBrand() {
  isSubmitting.value = true;
  clearError();
  try {
    const payload = {
      name: brandForm.name,
      slug: brandForm.slug,
      description: brandForm.description,
    };
    const path = brandForm.id ? `/brands/${brandForm.id}` : '/brands';
    const method = brandForm.id ? 'PUT' : 'POST';
    const saved = await request(path, { method, data: payload });
    await refreshAll();
    if (saved?.id) {
      const fresh = brands.value.find((entry) => entry.id === saved.id);
      if (fresh) selectBrand(fresh);
    } else {
      resetBrandForm();
    }
  } catch (error) {
    setError(error);
  } finally {
    isSubmitting.value = false;
  }
}

async function deleteBrand() {
  if (!brandForm.id || !window.confirm(`Delete brand "${brandForm.name || brandForm.slug}"?`)) {
    return;
  }

  isSubmitting.value = true;
  clearError();
  try {
    await request(`/brands/${brandForm.id}`, { method: 'DELETE' });
    selectedBrand.value = null;
    resetBrandForm();
    await refreshAll();
  } catch (error) {
    setError(error);
  } finally {
    isSubmitting.value = false;
  }
}

async function submitInventoryAdjustment() {
  isSubmitting.value = true;
  clearError();
  try {
    await request('/inventory/adjust', {
      method: 'POST',
      params: { idempotencyKey: inventoryForm.idempotencyKey },
      data: {
        variantId: inventoryForm.variantId,
        delta: Number(inventoryForm.delta || 0),
        reason: inventoryForm.reason,
      },
    });
    resetInventoryForm();
    await refreshAll();
  } catch (error) {
    setError(error);
  } finally {
    isSubmitting.value = false;
  }
}

onMounted(() => {
  refreshAll();
});
</script>

<style scoped>
.storefront-ops {
  display: grid;
  gap: 20px;
  padding: 20px;
}

.hero,
.panel {
  background: var(--theme--background-subdued);
  border: 1px solid var(--theme--border-color);
  border-radius: 16px;
  padding: 20px;
}

.hero {
  display: flex;
  justify-content: space-between;
  gap: 20px;
}

.eyebrow {
  color: var(--theme--foreground-subdued);
  font-size: 12px;
  letter-spacing: 0.18em;
  margin: 0 0 8px;
  text-transform: uppercase;
}

.lede {
  color: var(--theme--foreground-subdued);
  margin: 8px 0 0;
  max-width: 60rem;
}

.hero-actions,
.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 16px;
}

.stats {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
}

.stat {
  background: var(--theme--background);
  border: 1px solid var(--theme--border-color-subdued);
  border-radius: 12px;
  padding: 16px;
}

.stat span {
  color: var(--theme--foreground-subdued);
  display: block;
  margin-bottom: 6px;
}

.stat strong {
  font-size: 1.5rem;
}

.links {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 14px;
}

.grid {
  display: grid;
  gap: 16px;
  grid-template-columns: minmax(220px, 300px) minmax(0, 1fr);
}

.list {
  display: grid;
  gap: 8px;
  max-height: 520px;
  overflow: auto;
}

.list-item {
  align-items: flex-start;
  background: var(--theme--background);
  border: 1px solid var(--theme--border-color-subdued);
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px;
  text-align: left;
}

.list-item.active {
  border-color: var(--theme--primary);
  box-shadow: 0 0 0 1px var(--theme--primary);
}

.list-item small {
  color: var(--theme--foreground-subdued);
}

.editor,
.subpanel {
  display: grid;
  gap: 16px;
}

.form {
  display: grid;
  gap: 12px;
}

.form label {
  display: grid;
  gap: 6px;
}

.form span {
  color: var(--theme--foreground-subdued);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.form input,
.form textarea {
  background: var(--theme--background);
  border: 1px solid var(--theme--border-color);
  border-radius: 10px;
  color: inherit;
  padding: 10px 12px;
}

.checkbox {
  align-items: center;
  display: flex !important;
  gap: 10px;
}

.checkbox span {
  color: inherit;
  font-size: 14px;
  font-weight: 500;
  letter-spacing: 0;
  text-transform: none;
}

.compact {
  border-top: 1px solid var(--theme--border-color-subdued);
  padding-top: 16px;
}

.subpanel-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.table-wrap {
  overflow: auto;
}

table {
  border-collapse: collapse;
  width: 100%;
}

th,
td {
  border-bottom: 1px solid var(--theme--border-color-subdued);
  padding: 10px 8px;
  text-align: left;
}

button {
  background: var(--theme--background);
  border: 1px solid var(--theme--border-color);
  border-radius: 10px;
  cursor: pointer;
  padding: 10px 14px;
}

button.primary {
  background: var(--theme--primary);
  border-color: var(--theme--primary);
  color: var(--theme--primary-inverse);
}

button.danger {
  border-color: var(--theme--danger);
  color: var(--theme--danger);
}

.error {
  color: var(--theme--danger);
  margin-top: 12px;
}

@media (max-width: 960px) {
  .hero,
  .panel-header,
  .grid {
    grid-template-columns: 1fr;
    display: grid;
  }
}
</style>
