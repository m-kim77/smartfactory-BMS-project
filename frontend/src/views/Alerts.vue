<script setup>
import { ref, reactive, onMounted, watch, computed } from 'vue';
import { useRoute } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { api, qs } from '../composables/api.js';
import { severityColor, alertStatusColor } from '../composables/status.js';
import { useFilterStore } from '../stores/filters.js';
import { useAuthStore } from '../stores/auth.js';
import { useLabels } from '../composables/labels.js';
import FactoryFilter from '../components/FactoryFilter.vue';
import MultiSelect from '../components/MultiSelect.vue';

const { t } = useI18n();
const labels = useLabels();
const route = useRoute();
const items = ref([]);
const alertTypes = ref([]);
const models = ref([]);
const countries = ref([]);
const loading = ref(false);
const filterStore = useFilterStore();
const auth = useAuthStore();

const filters = reactive({
  car_id: '',
  model: [],
  alert_type: [],
  country: [],
  status: [],
  severity: [],
  date_field: 'occurred_at',
  date_from: '',
  date_to: '',
  match_mode: 'and',
});

const SEVERITY_OPTIONS = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const STATUS_OPTIONS = ['OPEN', 'ACKNOWLEDGED', 'RESOLVED'];

const DATE_FIELD_OPTIONS = computed(() => [
  { value: 'occurred_at', label: t('alerts.dateFields.occurred_at') },
  { value: 'resolved_at', label: t('alerts.dateFields.resolved_at') },
]);

const hasColumnFilter = computed(() =>
  !!(filters.car_id || filters.model.length || filters.alert_type.length || filters.country.length
    || filters.status.length || filters.severity.length || filters.date_from || filters.date_to)
);

const sortKey = ref('occurred_at');
const sortDir = ref('desc');

function toggleSort(key) {
  if (sortKey.value === key) {
    sortDir.value = sortDir.value === 'asc' ? 'desc' : 'asc';
  } else {
    sortKey.value = key;
    sortDir.value = 'desc';
  }
}

const sortedItems = computed(() => {
  const arr = items.value.slice();
  const k = sortKey.value;
  const sign = sortDir.value === 'asc' ? 1 : -1;
  arr.sort((a, b) => {
    const av = a[k] ?? '';
    const bv = b[k] ?? '';
    if (av < bv) return -1 * sign;
    if (av > bv) return 1 * sign;
    return 0;
  });
  return arr;
});

function sortIcon(key) {
  if (sortKey.value !== key) return '↕';
  return sortDir.value === 'asc' ? '↑' : '↓';
}

async function loadFacets() {
  try {
    const r = await api.get('/alerts/facets');
    alertTypes.value = r.alert_types || [];
    models.value = r.models || [];
    countries.value = r.countries || [];
  } catch {}
}

const selected = ref(new Set());
function isSelected(id) { return selected.value.has(id); }
function toggleSelect(id) {
  const s = new Set(selected.value);
  if (s.has(id)) s.delete(id); else s.add(id);
  selected.value = s;
}
const allSelected = computed(() =>
  sortedItems.value.length > 0 && sortedItems.value.every(a => selected.value.has(a.alert_id))
);
function toggleAll() {
  if (allSelected.value) selected.value = new Set();
  else selected.value = new Set(sortedItems.value.map(a => a.alert_id));
}
async function deleteSelected() {
  const ids = [...selected.value];
  if (!ids.length) return;
  if (!confirm(t('alerts.confirmDelete', { n: ids.length }))) return;
  try {
    await api.post('/alerts/bulk-delete', { alert_ids: ids });
    selected.value = new Set();
    await load();
  } catch (e) {
    alert(e.message || t('common.deleteFailed'));
  }
}

async function load() {
  loading.value = true;
  try {
    items.value = (await api.get(`/alerts${qs({
      car_id: filters.car_id,
      model: filters.model.join(','),
      alert_type: filters.alert_type.join(','),
      country: filters.country.join(','),
      status: filters.status.join(','),
      severity: filters.severity.join(','),
      factory_ids: filterStore.factoryIdsQuery,
      date_field: filters.date_field,
      date_from: filters.date_from,
      date_to: filters.date_to,
      match_mode: filters.match_mode,
    })}`)).items;
    const visible = new Set(items.value.map(a => a.alert_id));
    const next = new Set();
    for (const id of selected.value) if (visible.has(id)) next.add(id);
    selected.value = next;
  } finally { loading.value = false; }
}

async function resolve(id) {
  if (!confirm(t('alerts.confirmResolve'))) return;
  await api.post(`/alerts/${id}/resolve`);
  await load();
}
async function ack(id) {
  await api.post(`/alerts/${id}/acknowledge`);
  await load();
}

function clearColumnFilters() {
  filters.car_id = '';
  filters.model = [];
  filters.alert_type = [];
  filters.country = [];
  filters.status = [];
  filters.severity = [];
  filters.date_from = '';
  filters.date_to = '';
  filters.date_field = 'occurred_at';
  filters.match_mode = 'and';
}
function clearAll() { clearColumnFilters(); filterStore.clearFactories(); }

onMounted(() => {
  if (route.query.status) filters.status = [String(route.query.status)];
  loadFacets();
  load();
});

let debounce;
function scheduleLoad() { clearTimeout(debounce); debounce = setTimeout(load, 250); }

watch(() => filters.car_id, scheduleLoad);
watch(
  () => [
    filters.model.slice().join(','),
    filters.alert_type.slice().join(','),
    filters.country.slice().join(','),
    filters.status.slice().join(','),
    filters.severity.slice().join(','),
    filters.date_field, filters.date_from, filters.date_to, filters.match_mode,
    filterStore.factoryIds.slice().join(','),
  ],
  load
);
</script>

<template>
  <div>
    <div v-if="!auth.isAdmin && (auth.user?.allowed_factory_ids?.length || 0) === 0"
         class="card p-4 mb-4 border-amber-300 bg-amber-50 dark:bg-amber-900/20 text-amber-800 dark:text-amber-200">
      {{ t('common.noFactoryAccess') }}
    </div>
    <div class="flex items-center justify-between mb-5 gap-4">
      <div class="flex items-center gap-4 flex-wrap">
        <h1 class="text-2xl font-bold shrink-0">{{ t('alerts.title') }}</h1>
        <FactoryFilter />
      </div>
      <div class="flex gap-2 shrink-0">
        <button v-if="auth.isAdmin && selected.size"
                class="btn-danger text-sm" @click="deleteSelected">
          {{ t('alerts.delete') }} ({{ selected.size }})
        </button>
        <button v-if="hasColumnFilter || filterStore.factoryIds.length"
                class="btn-secondary text-sm" @click="clearAll">{{ t('common.clearFilters') }}</button>
        <button class="btn-secondary text-sm" @click="load">{{ t('common.refresh') }}</button>
      </div>
    </div>

    <div class="card p-4 mb-4">
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-3">
        <div>
          <label class="text-xs text-slate-500 dark:text-slate-400 block mb-1">{{ t('alerts.carId') }}</label>
          <input v-model="filters.car_id" class="input w-full" :placeholder="t('alerts.searchCarId')" />
        </div>
        <div>
          <label class="text-xs text-slate-500 dark:text-slate-400 block mb-1">{{ t('alerts.model') }}</label>
          <MultiSelect v-model="filters.model" :options="models" :all-label="t('common.all')" :label-fn="labels.model" :search-placeholder="t('alerts.searchModel')" searchable />
        </div>
        <div>
          <label class="text-xs text-slate-500 dark:text-slate-400 block mb-1">{{ t('alerts.alertType') }}</label>
          <MultiSelect v-model="filters.alert_type" :options="alertTypes" :all-label="t('common.all')" searchable />
        </div>
        <div>
          <label class="text-xs text-slate-500 dark:text-slate-400 block mb-1">{{ t('alerts.country') }}</label>
          <MultiSelect v-model="filters.country" :options="countries" :all-label="t('common.all')" :label-fn="labels.country" searchable />
        </div>
        <div>
          <label class="text-xs text-slate-500 dark:text-slate-400 block mb-1">{{ t('alerts.status') }}</label>
          <MultiSelect v-model="filters.status" :options="STATUS_OPTIONS" :all-label="t('alerts.allStatus')" />
        </div>
        <div>
          <label class="text-xs text-slate-500 dark:text-slate-400 block mb-1">{{ t('alerts.severity') }}</label>
          <MultiSelect v-model="filters.severity" :options="SEVERITY_OPTIONS" :all-label="t('alerts.allSeverity')" />
        </div>
      </div>

      <div class="grid grid-cols-1 md:grid-cols-3 gap-3 mt-3 pt-3 border-t border-slate-100 dark:border-slate-700">
        <div>
          <label class="text-xs text-slate-500 dark:text-slate-400 block mb-1">{{ t('alerts.dateBasis') }}</label>
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
            {{ hasColumnFilter ? t('alerts.filterApplied') : t('alerts.noFilter') }}
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
        <span class="text-slate-500 dark:text-slate-400">{{ t('alerts.resultCount', { n: items.length }) }} {{ items.length >= 500 ? t('alerts.maxLimit') : '' }}</span>
      </div>
    </div>

    <div class="card overflow-hidden">
      <table class="w-full text-sm">
        <thead class="bg-slate-50 dark:bg-slate-900 border-b border-slate-200 dark:border-slate-700">
          <tr class="text-left">
            <th v-if="auth.isAdmin" class="px-3 py-3 w-10">
              <input type="checkbox" :checked="allSelected" @click.stop.prevent="toggleAll" class="accent-hyundai-500" />
            </th>
            <th class="px-4 py-3 cursor-pointer select-none hover:bg-slate-100 dark:hover:bg-slate-800"
                @click="toggleSort('occurred_at')">
              {{ t('alerts.occurredAt') }}
              <span class="ml-1 text-xs" :class="sortKey === 'occurred_at' ? 'text-hyundai-500' : 'text-slate-400'">{{ sortIcon('occurred_at') }}</span>
            </th>
            <th class="px-4 py-3 cursor-pointer select-none hover:bg-slate-100 dark:hover:bg-slate-800"
                @click="toggleSort('car_id')">
              {{ t('alerts.vehicle') }}
              <span class="ml-1 text-xs" :class="sortKey === 'car_id' ? 'text-hyundai-500' : 'text-slate-400'">{{ sortIcon('car_id') }}</span>
            </th>
            <th class="px-4 py-3">{{ t('alerts.type') }}</th>
            <th class="px-4 py-3">{{ t('alerts.message') }}</th>
            <th class="px-4 py-3">{{ t('alerts.severity') }}</th>
            <th class="px-4 py-3">{{ t('alerts.status') }}</th>
            <th class="px-4 py-3">{{ t('alerts.action') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!sortedItems.length"><td :colspan="auth.isAdmin ? 8 : 7" class="px-4 py-8 text-center text-slate-400">{{ loading ? t('common.loading') : t('alerts.noAlerts') }}</td></tr>
          <tr v-for="a in sortedItems" :key="a.alert_id"
              class="border-b border-slate-100 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-700/50"
              :class="[auth.isAdmin ? 'cursor-pointer' : '', isSelected(a.alert_id) ? 'bg-hyundai-50 dark:bg-hyundai-900/20' : '']"
              @click="auth.isAdmin && toggleSelect(a.alert_id)">
            <td v-if="auth.isAdmin" class="px-3 py-3 w-10">
              <input type="checkbox" :checked="isSelected(a.alert_id)" @click.stop.prevent="toggleSelect(a.alert_id)" class="accent-hyundai-500" />
            </td>
            <td class="px-4 py-3 text-xs text-slate-500 dark:text-slate-400">{{ a.occurred_at }}</td>
            <td class="px-4 py-3">
              <router-link :to="`/vehicles/${a.car_id}`" class="font-mono text-hyundai-500 dark:text-hyundai-300 hover:underline" @click.stop>{{ a.car_id }}</router-link>
              <div class="text-xs text-slate-500 dark:text-slate-400">{{ labels.model(a.model_name) }}</div>
            </td>
            <td class="px-4 py-3">{{ a.alert_type }}</td>
            <td class="px-4 py-3">{{ labels.alertMessage(a.alert_message) }}</td>
            <td class="px-4 py-3"><span :class="severityColor(a.severity)">{{ a.severity }}</span></td>
            <td class="px-4 py-3"><span :class="alertStatusColor(a.current_status)">{{ a.current_status }}</span></td>
            <td class="px-4 py-3 flex gap-2">
              <button v-if="a.current_status === 'OPEN'" class="btn-secondary text-xs" @click.stop="ack(a.alert_id)">{{ t('alerts.ack') }}</button>
              <button v-if="a.current_status !== 'RESOLVED'" class="btn-danger text-xs" @click.stop="resolve(a.alert_id)">{{ t('alerts.resolve') }}</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
