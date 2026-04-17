<script setup>
import { ref, onMounted, onBeforeUnmount, computed, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { api, qs } from '../composables/api.js';
import { statusColor, severityColor } from '../composables/status.js';
import { useLabels } from '../composables/labels.js';
import { useFilterStore } from '../stores/filters.js';
import { useAuthStore } from '../stores/auth.js';
import FactoryFilter from '../components/FactoryFilter.vue';

const { t } = useI18n();
const labels = useLabels();
const stats = ref(null);
const hovered = ref(null);
const pinned = ref(null);
const filterStore = useFilterStore();
const auth = useAuthStore();
let timer = null;

function togglePin(key) {
  pinned.value = pinned.value === key ? null : key;
}

async function load() {
  const query = qs({ factory_ids: filterStore.factoryIdsQuery });
  try { stats.value = await api.get(`/dashboard/stats${query}`); } catch {}
}

onMounted(() => { load(); timer = setInterval(load, 5000); });
onBeforeUnmount(() => clearInterval(timer));

// 공장 필터 변경 시 재조회
watch(() => filterStore.factoryIds.slice().join(','), load);

const PIPELINE_GROUPS = {
  inspecting: {
    labelKey: 'dashboard.inspecting',
    carsKeys: ['arrivalCars', 'inspectingCars', 'reInspWaitCars'],
    countKeys: ['arrival', 'inspecting', 'reInspectionWaiting'],
  },
  shipWaiting: {
    labelKey: 'dashboard.shipWaiting',
    carsKeys: ['qcCompleteCars', 'shipWaitingCars'],
    countKeys: ['qcComplete', 'shipmentWaiting'],
  },
};

function pipelineCount(key) {
  if (!stats.value) return 0;
  return PIPELINE_GROUPS[key].countKeys.reduce((sum, k) => sum + (stats.value[k] || 0), 0);
}

function pipelineCars(key) {
  if (!stats.value) return [];
  return PIPELINE_GROUPS[key].carsKeys.flatMap(k => stats.value[k] || []);
}

const displayInfo = computed(() => {
  const key = pinned.value || hovered.value;
  if (!key || !stats.value) return null;
  if (key === 'anomalies') {
    return { label: t('dashboard.anomalies'), cars: stats.value.anomalyCars || [] };
  }
  if (PIPELINE_GROUPS[key]) {
    return { label: t(PIPELINE_GROUPS[key].labelKey), cars: pipelineCars(key) };
  }
  return null;
});

function fmtTime(isoStr) {
  if (!isoStr) return '';
  return new Date(isoStr.replace(' ', 'T') + 'Z').toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit', hour12: false });
}

const shiftTimeRange = computed(() => {
  if (!stats.value?.shiftStart) return '';
  return `${fmtTime(stats.value.shiftStart)} ~ ${fmtTime(stats.value.shiftEnd)}`;
});

const METRICS = computed(() => [
  { key: 'soc',             label: 'SOC',  unit: '%', stepName: 'SOC_CHECK',              min: 0, max: 100 },
  { key: 'soh',             label: 'SOH',  unit: '%', stepName: 'SOH_CHECK',              min: 0, max: 100 },
  { key: 'avg_temperature', label: t('dashboard.temperature'), unit: '℃', stepName: 'CELL_TEMPERATURE_CHECK', min: 0, max: 50  },
]);

function stepVisible(status) {
  return status === 'PASS' || status === 'FAIL';
}
function metricPct(m, value) {
  if (value == null) return 0;
  return Math.max(0, Math.min(100, ((value - m.min) / (m.max - m.min)) * 100));
}
function barBg(status) {
  if (status === 'PASS') return 'bg-emerald-500';
  if (status === 'FAIL') return 'bg-red-500';
  return 'bg-transparent';
}
function dotBg(status) {
  if (status === 'PASS') return 'bg-emerald-500';
  if (status === 'FAIL') return 'bg-red-500';
  if (status === 'IN_PROGRESS') return 'bg-slate-300 dark:bg-slate-500 animate-pulse';
  return 'bg-slate-300 dark:bg-slate-600';
}
function valueTextColor(status) {
  if (status === 'PASS') return 'text-slate-800 dark:text-slate-100';
  if (status === 'FAIL') return 'text-red-600 dark:text-red-400';
  return 'text-slate-400 dark:text-slate-500';
}
function statusText(status) {
  if (status === 'PASS') return 'PASS';
  if (status === 'FAIL') return 'FAIL';
  if (status === 'IN_PROGRESS') return t('dashboard.metricInspecting');
  return t('dashboard.metricWaiting');
}
function fmtValue(m, value) {
  if (value == null) return '-';
  return `${Number(value).toFixed(1)}${m.unit}`;
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
        <h1 class="text-2xl font-bold shrink-0">{{ t('dashboard.title') }}</h1>
        <FactoryFilter />
      </div>
      <div class="text-right shrink-0">
        <div v-if="stats" class="text-sm font-medium text-hyundai-600 dark:text-hyundai-300">
          {{ t('dashboard.shift') }} {{ shiftTimeRange }}
        </div>
        <div class="text-xs text-slate-400 mt-0.5">{{ t('dashboard.lastUpdated') }}: {{ new Date().toLocaleTimeString() }}</div>
      </div>
    </div>

    <div v-if="stats" class="grid grid-cols-2 md:grid-cols-4 gap-4 mb-4">
      <div class="card p-5">
        <div class="text-sm font-medium text-slate-500 dark:text-slate-400">{{ t('dashboard.totalVehicles') }}</div>
        <div class="text-4xl font-extrabold mt-2 text-slate-900 dark:text-slate-100">{{ stats.total }}</div>
        <div class="text-xs text-slate-400 mt-2">{{ t('dashboard.operating') }}</div>
      </div>
      <div class="card p-5 cursor-pointer transition-colors select-none"
           :class="pinned === 'anomalies' ? 'ring-2 ring-red-400 bg-red-50 dark:bg-red-900/30' : hovered === 'anomalies' ? 'ring-2 ring-red-300' : 'ring-1 ring-red-200 dark:ring-red-900/50'"
           @mouseenter="hovered = 'anomalies'" @mouseleave="hovered = null" @click="togglePin('anomalies')">
        <div class="text-sm font-medium text-red-600 dark:text-red-400">{{ t('dashboard.problemVehicles') }}</div>
        <div class="text-4xl font-extrabold mt-2 text-red-500 dark:text-red-400">{{ stats.anomalies }}</div>
        <div class="text-xs text-red-500 dark:text-red-400 mt-2">{{ t('dashboard.checkImmediately') }}</div>
      </div>
      <router-link :to="{ path: '/alerts', query: { status: 'OPEN' } }"
                   class="card p-5 cursor-pointer transition-colors select-none hover:ring-2 hover:ring-amber-300 block">
        <div class="text-sm font-medium text-amber-600 dark:text-amber-400">{{ t('dashboard.unresolvedLogs') }}</div>
        <div class="text-4xl font-extrabold mt-2 text-amber-600 dark:text-amber-400">{{ stats.openAlerts }}</div>
        <div class="text-xs text-amber-600 dark:text-amber-400 mt-2">{{ t('dashboard.pendingAction') }}</div>
      </router-link>
      <div class="card p-5">
        <div class="text-sm font-medium text-emerald-600 dark:text-emerald-400">{{ t('dashboard.shipped') }}</div>
        <div class="text-4xl font-extrabold mt-2 text-emerald-600 dark:text-emerald-400">{{ stats.shipped }}</div>
        <div class="text-xs text-emerald-600 dark:text-emerald-400 mt-2">{{ t('dashboard.currentShift') }}</div>
      </div>
    </div>

    <div v-if="stats" class="card p-5 mb-6">
      <div class="text-sm font-bold text-slate-800 dark:text-slate-100 mb-4">{{ t('dashboard.shipmentPipeline') }}</div>
      <div class="grid grid-cols-3 gap-4">
        <div class="cursor-pointer transition-colors text-center py-4 rounded-lg select-none"
             :class="pinned === 'inspecting' ? 'bg-blue-50 dark:bg-blue-900/30 ring-2 ring-blue-400' : 'hover:bg-slate-50 dark:hover:bg-slate-700/50'"
             @mouseenter="hovered = 'inspecting'" @mouseleave="hovered = null" @click="togglePin('inspecting')">
          <div class="w-14 h-14 mx-auto rounded-full bg-blue-100 dark:bg-blue-900/50 flex items-center justify-center">
            <svg xmlns="http://www.w3.org/2000/svg" class="w-7 h-7 text-blue-600 dark:text-blue-300" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="9" />
              <path d="M12 7v5l3 2" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </div>
          <div class="text-3xl font-extrabold mt-3 text-slate-900 dark:text-slate-100">{{ pipelineCount('inspecting') }}</div>
          <div class="text-xs text-slate-500 dark:text-slate-400 mt-1">{{ t('dashboard.inspecting') }}</div>
        </div>
        <div class="cursor-pointer transition-colors text-center py-4 rounded-lg select-none"
             :class="pinned === 'shipWaiting' ? 'bg-amber-50 dark:bg-amber-900/30 ring-2 ring-amber-400' : 'hover:bg-slate-50 dark:hover:bg-slate-700/50'"
             @mouseenter="hovered = 'shipWaiting'" @mouseleave="hovered = null" @click="togglePin('shipWaiting')">
          <div class="w-14 h-14 mx-auto rounded-full bg-slate-100 dark:bg-slate-700 flex items-center justify-center">
            <svg xmlns="http://www.w3.org/2000/svg" class="w-7 h-7 text-slate-600 dark:text-slate-300" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
              <rect x="3" y="7" width="16" height="10" rx="2" />
              <line x1="22" y1="11" x2="22" y2="13" stroke-linecap="round" />
            </svg>
          </div>
          <div class="text-3xl font-extrabold mt-3 text-slate-900 dark:text-slate-100">{{ pipelineCount('shipWaiting') }}</div>
          <div class="text-xs text-slate-500 dark:text-slate-400 mt-1">{{ t('dashboard.shipWaiting') }}</div>
        </div>
        <div class="text-center py-4 rounded-lg select-none">
          <div class="w-14 h-14 mx-auto rounded-full bg-emerald-100 dark:bg-emerald-900/50 flex items-center justify-center">
            <svg xmlns="http://www.w3.org/2000/svg" class="w-7 h-7 text-emerald-600 dark:text-emerald-300" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="9" />
              <path d="M8.5 12.5l2.5 2.5 4.5-5" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </div>
          <div class="text-3xl font-extrabold mt-3 text-slate-900 dark:text-slate-100">{{ stats.shipped }}</div>
          <div class="text-xs text-slate-500 dark:text-slate-400 mt-1">{{ t('dashboard.shipped') }}</div>
        </div>
      </div>
    </div>

    <div v-if="stats" class="grid grid-cols-1 lg:grid-cols-4 gap-5">
      <div class="lg:col-span-3">
        <div v-if="displayInfo" class="card p-5">
          <div class="flex items-center gap-2 mb-4">
            <div class="text-base font-bold text-slate-800 dark:text-slate-100">
              {{ t('dashboard.vehicleListOf', { label: displayInfo.label }) }} ({{ displayInfo.cars.length }})
            </div>
            <span v-if="pinned" class="text-xs text-slate-400 bg-slate-100 dark:bg-slate-700 dark:text-slate-300 px-2 py-0.5 rounded-full">
              {{ t('dashboard.pinned') }}
            </span>
          </div>
          <div v-if="!displayInfo.cars.length" class="text-sm text-slate-400 py-8 text-center">{{ t('dashboard.noVehicles') }}</div>
          <div v-else
               class="grid gap-3 max-h-[640px] overflow-y-auto pr-1"
               style="grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));">
            <router-link v-for="c in displayInfo.cars" :key="c.car_id"
                         :to="`/vehicles/${c.car_id}`"
                         class="block p-3.5 rounded-lg border border-slate-200 dark:border-slate-700 hover:border-hyundai-400 hover:shadow-sm transition bg-white dark:bg-slate-800">
              <div class="flex items-start justify-between mb-3 gap-2">
                <div class="min-w-0">
                  <div class="font-bold text-sm truncate">{{ labels.model(c.model_name) }}</div>
                  <div class="text-[11px] font-mono text-slate-500 dark:text-slate-400 truncate mt-0.5">{{ c.car_id }}</div>
                </div>
                <span :class="statusColor(c.current_status)">{{ t(`status.${c.current_status}`) }}</span>
              </div>
              <div class="space-y-2">
                <div v-for="m in METRICS" :key="m.key" class="flex items-center gap-2">
                  <span class="w-1.5 h-1.5 rounded-full flex-shrink-0" :class="dotBg(c.steps?.[m.stepName])"></span>
                  <span class="w-8 text-[11px] font-medium text-slate-500 dark:text-slate-400">{{ m.label }}</span>
                  <div class="flex-1 h-1.5 bg-slate-100 dark:bg-slate-700 rounded-full overflow-hidden">
                    <div class="h-full rounded-full transition-all duration-500"
                         :class="barBg(c.steps?.[m.stepName])"
                         :style="{ width: stepVisible(c.steps?.[m.stepName]) ? metricPct(m, c.measurement?.[m.key]) + '%' : '0%' }"></div>
                  </div>
                  <span class="w-14 text-[11px] font-semibold text-right tabular-nums" :class="valueTextColor(c.steps?.[m.stepName])">
                    {{ stepVisible(c.steps?.[m.stepName]) ? fmtValue(m, c.measurement?.[m.key]) : statusText(c.steps?.[m.stepName]) }}
                  </span>
                </div>
              </div>
            </router-link>
          </div>
        </div>
        <div v-else class="card p-12 text-center">
          <div class="text-sm text-slate-500 dark:text-slate-400">{{ t('dashboard.clickToView') }}</div>
        </div>
      </div>

      <div class="card p-5">
        <div class="flex items-center justify-between mb-3">
          <div class="font-semibold">{{ t('dashboard.recentAlerts') }}</div>
          <span class="badge-red">{{ stats.openAlerts }} {{ t('dashboard.openAlertsSuffix') }}</span>
        </div>
        <div class="space-y-2 max-h-[640px] overflow-auto">
          <div v-if="!stats.recentAlerts.length" class="text-sm text-slate-400">{{ t('dashboard.noAlerts') }}</div>
          <router-link v-for="a in stats.recentAlerts" :key="a.alert_id"
                       :to="`/vehicles/${a.car_id}`"
                       class="block p-3 rounded-lg border border-slate-200 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-700">
            <div class="flex items-center justify-between">
              <span class="text-xs font-mono text-hyundai-500 dark:text-hyundai-300">{{ a.car_id }}</span>
              <span :class="severityColor(a.severity)">{{ a.severity }}</span>
            </div>
            <div class="text-sm mt-1 line-clamp-2">{{ labels.alertMessage(a.alert_message) }}</div>
            <div class="text-xs text-slate-400 mt-1">{{ a.occurred_at }}</div>
          </router-link>
        </div>
      </div>
    </div>
  </div>
</template>
