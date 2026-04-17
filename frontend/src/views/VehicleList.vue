<script setup>
import { ref, reactive, onMounted, watch, computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { api, qs } from '../composables/api.js';
import { STATUS_ORDER, statusColor } from '../composables/status.js';
import { useFilterStore } from '../stores/filters.js';
import { useAuthStore } from '../stores/auth.js';
import { useLabels } from '../composables/labels.js';
import FactoryFilter from '../components/FactoryFilter.vue';
import MultiSelect from '../components/MultiSelect.vue';

const { t } = useI18n();
const labels = useLabels();
const items = ref([]);
const models = ref([]);
const countries = ref([]);
const loading = ref(false);
const filterStore = useFilterStore();
const auth = useAuthStore();

const filters = reactive({
  car_id: '',
  model: [],
  status: [],
  country: [],
  date_field: 'updated_at',
  date_from: '',
  date_to: '',
  match_mode: 'and',
});

const DATE_FIELD_OPTIONS = computed(() => [
  { value: 'updated_at', label: t('vehicleList.dateFields.updated_at') },
  { value: 'production_date', label: t('vehicleList.dateFields.production_date') },
  { value: 'created_at', label: t('vehicleList.dateFields.created_at') },
]);

const hasColumnFilter = computed(() =>
  !!(filters.car_id || filters.model.length || filters.status.length || filters.country.length || filters.date_from || filters.date_to)
);

async function loadFacets() {
  try {
    const r = await api.get('/vehicles/facets');
    models.value = r.models || [];
    countries.value = r.countries || [];
  } catch {}
}

async function load() {
  loading.value = true;
  try {
    const d = await api.get(`/vehicles${qs({
      car_id: filters.car_id,
      model: filters.model.join(','),
      status: filters.status.join(','),
      country: filters.country.join(','),
      factory_ids: filterStore.factoryIdsQuery,
      date_field: filters.date_field,
      date_from: filters.date_from,
      date_to: filters.date_to,
      match_mode: filters.match_mode,
    })}`);
    items.value = d.items;
  } finally { loading.value = false; }
}

function clearColumnFilters() {
  filters.car_id = '';
  filters.model = [];
  filters.status = [];
  filters.country = [];
  filters.date_from = '';
  filters.date_to = '';
  filters.date_field = 'updated_at';
  filters.match_mode = 'and';
}
function clearAll() { clearColumnFilters(); filterStore.clearFactories(); }

onMounted(() => { loadFacets(); load(); });

let debounce;
function scheduleLoad() { clearTimeout(debounce); debounce = setTimeout(load, 250); }

watch(() => filters.car_id, scheduleLoad);
watch(
  () => [
    filters.model.slice().join(','),
    filters.status.slice().join(','),
    filters.country.slice().join(','),
    filters.date_field, filters.date_from, filters.date_to, filters.match_mode,
    filterStore.factoryIds.slice().join(','),
  ],
  load
);

// ───── Admin: create / edit / delete ─────
const showForm = ref(false);
const formMode = ref('create');
const formCarId = ref('');
const formSaving = ref(false);
const formError = ref('');
const allowedCountries = ref([]);
const form = reactive({ model_name: '', destination_country: '', factory_id: null });

async function loadAllowedCountries() {
  try {
    const r = await api.get('/settings/countries');
    allowedCountries.value = (r.items || []).filter(c => c.is_allowed).map(c => c.country_name);
  } catch { allowedCountries.value = []; }
}

async function openCreate() {
  formMode.value = 'create';
  formCarId.value = '';
  formError.value = '';
  form.model_name = '';
  form.destination_country = '';
  form.factory_id = null;
  await Promise.all([filterStore.loadFactories(), loadAllowedCountries()]);
  if (!form.factory_id && filterStore.factories.length) form.factory_id = filterStore.factories[0].factory_id;
  showForm.value = true;
}

async function openEdit(car) {
  formMode.value = 'edit';
  formCarId.value = car.car_id;
  formError.value = '';
  form.model_name = car.model_name || '';
  form.destination_country = car.destination_country || '';
  form.factory_id = car.factory_id || null;
  await Promise.all([filterStore.loadFactories(), loadAllowedCountries()]);
  showForm.value = true;
}

function closeForm() { showForm.value = false; }

async function submitForm() {
  formError.value = '';
  if (!form.model_name.trim()) { formError.value = t('vehicleList.modelRequired'); return; }
  if (!form.destination_country.trim()) { formError.value = t('vehicleList.countryRequired'); return; }
  if (!form.factory_id) { formError.value = t('vehicleList.factoryRequired'); return; }
  formSaving.value = true;
  try {
    if (formMode.value === 'create') {
      const r = await api.post('/vehicles', {
        model_name: form.model_name.trim(),
        destination_country: form.destination_country.trim(),
        factory_id: form.factory_id,
      });
      showForm.value = false;
      await load();
      await loadFacets();
      alert(t('vehicleList.createdCarId', { carId: r.car_id }));
    } else {
      await api.put(`/vehicles/${formCarId.value}`, {
        model_name: form.model_name.trim(),
        destination_country: form.destination_country.trim(),
        factory_id: form.factory_id,
      });
      showForm.value = false;
      await load();
    }
  } catch (e) {
    formError.value = e.message || t('vehicleList.saveFailed');
  } finally {
    formSaving.value = false;
  }
}

async function removeVehicle(car) {
  if (!confirm(t('vehicleList.confirmDelete', { carId: car.car_id }))) return;
  try {
    await api.del(`/vehicles/${car.car_id}`);
    await load();
  } catch (e) {
    alert(e.message || t('common.deleteFailed'));
  }
}
</script>

<template>
  <div>
    <div v-if="!auth.isAdmin && (auth.user?.allowed_factory_ids?.length || 0) === 0"
         class="card p-4 mb-4 border-amber-300 bg-amber-50 dark:bg-amber-900/20 text-amber-800 dark:text-amber-200">
      {{ t('common.noFactoryAccess') }}
    </div>
    <div class="flex items-center justify-between mb-5 gap-4">
      <div class="flex items-center gap-4 flex-wrap">
        <h1 class="text-2xl font-bold shrink-0">{{ t('vehicleList.title') }}</h1>
        <FactoryFilter />
      </div>
      <div class="flex gap-2 shrink-0">
        <button v-if="auth.isAdmin" class="btn-primary text-sm" @click="openCreate">
          + {{ t('vehicleList.create') }}
        </button>
        <button v-if="hasColumnFilter || filterStore.factoryIds.length"
                class="btn-secondary text-sm" @click="clearAll">{{ t('common.clearFilters') }}</button>
        <button class="btn-secondary text-sm" @click="load">{{ t('common.refresh') }}</button>
      </div>
    </div>

    <div class="card p-4 mb-4">
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-3">
        <div>
          <label class="text-xs text-slate-500 dark:text-slate-400 block mb-1">{{ t('vehicleList.carId') }}</label>
          <input v-model="filters.car_id" class="input w-full" :placeholder="t('vehicleList.searchCarId')" />
        </div>
        <div>
          <label class="text-xs text-slate-500 dark:text-slate-400 block mb-1">{{ t('vehicleList.model') }}</label>
          <MultiSelect v-model="filters.model" :options="models" :all-label="t('common.all')" :label-fn="labels.model" :search-placeholder="t('vehicleList.searchModel')" searchable />
        </div>
        <div>
          <label class="text-xs text-slate-500 dark:text-slate-400 block mb-1">{{ t('vehicleList.status') }}</label>
          <MultiSelect v-model="filters.status" :options="STATUS_ORDER" :all-label="t('common.all')" :label-fn="(v) => t(`status.${v}`)" />
        </div>
        <div>
          <label class="text-xs text-slate-500 dark:text-slate-400 block mb-1">{{ t('vehicleList.country') }}</label>
          <MultiSelect v-model="filters.country" :options="countries" :all-label="t('common.all')" :label-fn="labels.country" searchable />
        </div>
      </div>

      <div class="grid grid-cols-1 md:grid-cols-3 gap-3 mt-3 pt-3 border-t border-slate-100 dark:border-slate-700">
        <div>
          <label class="text-xs text-slate-500 dark:text-slate-400 block mb-1">{{ t('vehicleList.dateBasis') }}</label>
          <select v-model="filters.date_field" class="input w-full">
            <option v-for="o in DATE_FIELD_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</option>
          </select>
        </div>
        <div>
          <label class="text-xs text-slate-500 dark:text-slate-400 block mb-1">{{ t('common.start') }}</label>
          <input v-model="filters.date_from" type="datetime-local" class="input w-full" />
        </div>
        <div>
          <label class="text-xs text-slate-500 dark:text-slate-400 block mb-1">{{ t('common.end') }}</label>
          <input v-model="filters.date_to" type="datetime-local" class="input w-full" />
        </div>
      </div>

      <div class="flex items-center justify-between mt-3 pt-3 border-t border-slate-100 dark:border-slate-700 text-xs gap-3 flex-wrap">
        <div class="flex items-center gap-4">
          <span class="text-slate-500 dark:text-slate-400">
            {{ hasColumnFilter ? t('vehicleList.filterApplied') : t('vehicleList.noFilter') }}
          </span>
          <div class="flex items-center gap-3">
            <span class="text-slate-600 dark:text-slate-300 font-medium">{{ t('common.matchMode') }}:</span>
            <label class="flex items-center gap-1.5 cursor-pointer select-none">
              <input type="radio" v-model="filters.match_mode" value="and" class="accent-hyundai-500" />
              <span>{{ t('common.matchModeAnd') }}</span>
            </label>
            <label class="flex items-center gap-1.5 cursor-pointer select-none">
              <input type="radio" v-model="filters.match_mode" value="or" class="accent-hyundai-500" />
              <span>{{ t('common.matchModeOr') }}</span>
            </label>
          </div>
        </div>
        <span class="text-slate-500 dark:text-slate-400">{{ t('vehicleList.resultCount', { n: items.length }) }} {{ items.length >= 500 ? t('vehicleList.maxLimit') : '' }}</span>
      </div>
    </div>

    <div class="card overflow-hidden">
      <table class="w-full text-sm">
        <thead class="bg-slate-50 dark:bg-slate-900 border-b border-slate-200 dark:border-slate-700">
          <tr class="text-left">
            <th class="px-4 py-3 font-semibold">{{ t('vehicleList.carId') }}</th>
            <th class="px-4 py-3 font-semibold">{{ t('vehicleList.model') }}</th>
            <th class="px-4 py-3 font-semibold">{{ t('vehicleList.status') }}</th>
            <th class="px-4 py-3 font-semibold">{{ t('vehicleList.factory') }}</th>
            <th class="px-4 py-3 font-semibold">{{ t('vehicleList.country') }}</th>
            <th class="px-4 py-3 font-semibold">{{ t('vehicleList.updatedAt') }}</th>
            <th v-if="auth.isAdmin" class="px-4 py-3 font-semibold w-32">{{ t('vehicleList.actions') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!items.length"><td :colspan="auth.isAdmin ? 7 : 6" class="px-4 py-8 text-center text-slate-400">{{ loading ? t('common.loading') : t('common.noData') }}</td></tr>
          <tr v-for="c in items" :key="c.car_id"
              class="border-b border-slate-100 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-700/50 cursor-pointer"
              @click="$router.push(`/vehicles/${c.car_id}`)">
            <td class="px-4 py-3 font-mono text-hyundai-500 dark:text-hyundai-300">{{ c.car_id }}</td>
            <td class="px-4 py-3">{{ labels.model(c.model_name) }}</td>
            <td class="px-4 py-3"><span :class="statusColor(c.current_status)">{{ t(`status.${c.current_status}`) }}</span></td>
            <td class="px-4 py-3">{{ labels.factory(c.factory_name) || '-' }}</td>
            <td class="px-4 py-3">{{ labels.country(c.destination_country) }}</td>
            <td class="px-4 py-3 text-slate-500 dark:text-slate-400 text-xs">{{ c.current_status_updated_at }}</td>
            <td v-if="auth.isAdmin" class="px-4 py-3" @click.stop>
              <div class="flex gap-1">
                <button class="btn-secondary text-xs" @click.stop="openEdit(c)">{{ t('vehicleList.edit') }}</button>
                <button class="btn-danger text-xs" @click.stop="removeVehicle(c)">{{ t('vehicleList.delete') }}</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="showForm" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4" @click.self="closeForm">
      <div class="card w-full max-w-xl p-6 max-h-[90vh] overflow-y-auto">
        <div class="flex items-center justify-between mb-4">
          <h2 class="text-lg font-bold">{{ formMode === 'create' ? t('vehicleList.createTitle') : t('vehicleList.editTitle') }}</h2>
          <button class="text-slate-400 hover:text-slate-700 dark:hover:text-slate-200" @click="closeForm">✕</button>
        </div>
        <div v-if="formMode === 'edit'" class="mb-3 text-xs font-mono text-slate-500 dark:text-slate-400">{{ formCarId }}</div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
          <div>
            <label class="text-xs text-slate-500 dark:text-slate-400 block mb-1">{{ t('vehicleList.model') }}</label>
            <input v-model="form.model_name" class="input w-full" list="form-model-options" :placeholder="t('vehicleList.searchModel')" />
            <datalist id="form-model-options">
              <option v-for="m in models" :key="m" :value="m">{{ labels.model(m) }}</option>
            </datalist>
          </div>
          <div>
            <label class="text-xs text-slate-500 dark:text-slate-400 block mb-1">{{ t('vehicleList.country') }}</label>
            <select v-model="form.destination_country" class="input w-full">
              <option value="">{{ t('common.all') }}</option>
              <option v-for="c in allowedCountries" :key="c" :value="c">{{ labels.country(c) }}</option>
            </select>
          </div>
          <div class="md:col-span-2">
            <label class="text-xs text-slate-500 dark:text-slate-400 block mb-1">{{ t('vehicleList.factory') }}</label>
            <select v-model="form.factory_id" class="input w-full">
              <option :value="null">—</option>
              <option v-for="f in filterStore.factories" :key="f.factory_id" :value="f.factory_id">{{ labels.factory(f.factory_name) }}</option>
            </select>
          </div>
        </div>

        <div v-if="formError" class="mt-3 text-sm text-red-600 dark:text-red-400">{{ formError }}</div>

        <div class="flex justify-end gap-2 mt-5">
          <button class="btn-secondary" @click="closeForm" :disabled="formSaving">{{ t('vehicleList.cancel') }}</button>
          <button class="btn-primary" @click="submitForm" :disabled="formSaving">
            {{ formSaving ? t('vehicleList.saving') : t('vehicleList.save') }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
