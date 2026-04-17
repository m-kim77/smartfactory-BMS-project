<script setup>
import { ref, onMounted, onBeforeUnmount, watch, computed } from 'vue';
import { useRoute } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { api } from '../composables/api.js';
import { statusColor, severityColor, alertStatusColor } from '../composables/status.js';
import { useLabels } from '../composables/labels.js';
import ProcessTimeline from '../components/ProcessTimeline.vue';
import CellHeatmap from '../components/CellHeatmap.vue';
import Gauge from '../components/Gauge.vue';

const { t } = useI18n();
const labels = useLabels();
const route = useRoute();
const data = ref(null);
const err = ref('');
const resolving = ref(false);
let timer = null;

async function load() {
  try { data.value = await api.get(`/vehicles/${route.params.carId}`); err.value = ''; }
  catch (e) { err.value = e.message; }
}

async function resolve() {
  resolving.value = true;
  try { await api.post(`/vehicles/${route.params.carId}/resolve`); await load(); }
  catch (e) { err.value = e.message; }
  finally { resolving.value = false; }
}

const stepStatusMap = computed(() => {
  const map = {};
  (data.value?.steps || []).forEach(s => { map[s.step_name] = s.step_status; });
  return map;
});
function stepStatus(name) { return stepStatusMap.value[name] || 'PENDING'; }

const CELL_T_MIN = 5;
const CELL_T_MAX = 32;

const problemCells = computed(() => {
  const cells = data.value?.cells || [];
  return cells.filter(c => {
    const t = c.cell_temperature;
    return t != null && (t < CELL_T_MIN || t > CELL_T_MAX);
  });
});

const worstCell = computed(() => {
  if (problemCells.value.length === 0) return null;
  return problemCells.value.reduce((worst, c) => {
    const dev = c.cell_temperature < CELL_T_MIN ? CELL_T_MIN - c.cell_temperature : c.cell_temperature - CELL_T_MAX;
    const wd = worst.cell_temperature < CELL_T_MIN ? CELL_T_MIN - worst.cell_temperature : worst.cell_temperature - CELL_T_MAX;
    return dev > wd ? c : worst;
  });
});

const problemCellStatus = computed(() => {
  const s = stepStatus('CELL_TEMPERATURE_CHECK');
  if (s === 'PENDING' || s === 'IN_PROGRESS') return s;
  return problemCells.value.length === 0 ? 'PASS' : 'FAIL';
});

onMounted(() => { load(); timer = setInterval(load, 3000); });
onBeforeUnmount(() => clearInterval(timer));
watch(() => route.params.carId, load);
</script>

<template>
  <div v-if="err" class="card p-5 text-red-600">{{ err }}</div>
  <div v-else-if="!data" class="text-slate-500 dark:text-slate-400">{{ t('common.loading') }}</div>
  <div v-else>
    <router-link to="/vehicles" class="text-sm text-hyundai-500 hover:underline">{{ t('vehicleDetail.back') }}</router-link>
    <div class="flex items-center justify-between mt-2 mb-4 flex-wrap gap-3">
      <div>
        <div class="text-xs text-slate-500 dark:text-slate-400">{{ labels.factory(data.car.factory_name) }} · {{ t('vehicleDetail.exportsTo', { country: labels.country(data.car.destination_country) }) }}</div>
        <h1 class="text-2xl font-bold flex items-center gap-3">
          <span class="font-mono text-hyundai-500">{{ data.car.car_id }}</span>
          <span>{{ labels.model(data.car.model_name) }}</span>
          <span :class="statusColor(data.car.current_status)">{{ t(`status.${data.car.current_status}`) }}</span>
        </h1>
      </div>
      <button v-if="['ANOMALY_DETECTED','QA_MAINTENANCE'].includes(data.car.current_status)"
              class="btn-danger" :disabled="resolving" @click="resolve">
        {{ resolving ? t('vehicleDetail.processing') : t('vehicleDetail.resolveWithReinspect') }}
      </button>
    </div>

    <div v-if="data.measurement" class="grid grid-cols-2 md:grid-cols-5 gap-3 mb-5">
      <Gauge label="SOC" :value="data.measurement.soc" unit="%" :min="0" :max="100" :normal-min="90" :normal-max="100" :status="stepStatus('SOC_CHECK')" />
      <Gauge label="SOH" :value="data.measurement.soh" unit="%" :min="0" :max="100" :normal-min="95" :normal-max="100" :status="stepStatus('SOH_CHECK')" />
      <Gauge label="SOP" :value="data.measurement.sop" unit="%" :min="0" :max="100" :normal-min="90" :normal-max="100" :status="stepStatus('SOP_CHECK')" />
      <Gauge :label="t('vehicleDetail.packAvgVoltage')" :value="data.measurement.avg_voltage" unit="V" :min="300" :max="420" :normal-min="350" :normal-max="400" :status="stepStatus('PACK_VOLTAGE_CHECK')" />
      <div class="p-4 rounded-xl border" :class="{
        'border-emerald-200 bg-emerald-50/40': problemCellStatus === 'PASS',
        'border-red-200 bg-red-50/40': problemCellStatus === 'FAIL',
        'border-hyundai-200 bg-hyundai-50/40': problemCellStatus === 'IN_PROGRESS',
        'border-slate-200 bg-slate-50/40': problemCellStatus === 'PENDING',
      }">
        <div class="flex items-center justify-between">
          <div class="text-sm font-semibold text-slate-600">문제 셀 (온도)</div>
          <span v-if="problemCellStatus === 'PASS'" class="badge-green">PASS</span>
          <span v-else-if="problemCellStatus === 'FAIL'" class="badge-red">FAIL</span>
          <span v-else-if="problemCellStatus === 'IN_PROGRESS'" class="text-[10px] px-2 py-0.5 rounded-full bg-hyundai-50 text-hyundai-500 font-semibold pulse-blue">측정 중</span>
          <span v-else class="text-[10px] px-2 py-0.5 rounded-full bg-slate-100 text-slate-400 font-semibold">대기</span>
        </div>
        <div class="mt-2 flex items-baseline gap-2">
          <div class="text-3xl font-bold" :class="problemCellStatus === 'FAIL' ? 'text-red-600' : (problemCellStatus === 'PASS' ? 'text-emerald-700' : 'text-slate-300')">
            <template v-if="problemCellStatus === 'PENDING'">—</template>
            <template v-else>{{ problemCells.length }}<span class="text-base text-slate-500 ml-1">/ {{ data.cells?.length || 0 }}개</span></template>
          </div>
        </div>
        <div v-if="problemCellStatus === 'FAIL' && worstCell" class="mt-2 text-sm leading-snug">
          <div class="text-slate-600 dark:text-slate-300 break-words">
            <template v-for="(c, i) in problemCells.slice(0, 8)" :key="c.cell_id">
              <span :class="c.cell_id === worstCell.cell_id ? 'text-red-700 font-semibold' : ''">#{{ c.cell_number }}</span><span v-if="i < Math.min(problemCells.length, 8) - 1">, </span>
            </template>
            <span v-if="problemCells.length > 8" class="text-slate-400"> … +{{ problemCells.length - 8 }}</span>
          </div>
        </div>
        <div v-else-if="problemCellStatus === 'PASS'" class="mt-2 text-sm text-emerald-700">
          모든 셀 정상 범위 (5~32℃)
        </div>
      </div>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-2 gap-5">
      <div class="card p-5">
        <h2 class="font-semibold mb-3 flex items-center gap-2">
          <span>{{ t('vehicleDetail.cellStatus') }} ({{ t('vehicleDetail.cellCount', { n: data.cells.length }) }})</span>
          <span v-if="stepStatus('CELL_TEMPERATURE_CHECK') === 'PASS'" class="badge-green">PASS</span>
          <span v-else-if="stepStatus('CELL_TEMPERATURE_CHECK') === 'FAIL'" class="badge-red">FAIL</span>
          <span v-else-if="stepStatus('CELL_TEMPERATURE_CHECK') === 'IN_PROGRESS'" class="text-[10px] px-2 py-0.5 rounded-full bg-hyundai-50 dark:bg-hyundai-900/30 text-hyundai-500 dark:text-hyundai-300 font-semibold pulse-blue">{{ t('vehicleDetail.measuring') }}</span>
          <span v-else class="text-[10px] px-2 py-0.5 rounded-full bg-slate-100 dark:bg-slate-700 text-slate-400 font-semibold">{{ t('vehicleDetail.waiting') }}</span>
        </h2>
        <div v-if="stepStatus('CELL_TEMPERATURE_CHECK') === 'PENDING'" class="text-center py-16 text-slate-400 text-sm">
          {{ t('vehicleDetail.cellTempWaiting') }}
        </div>
        <CellHeatmap v-else :cells="data.cells" :normal-min="5" :normal-max="32" />
      </div>

      <div class="card p-5">
        <h2 class="font-semibold mb-3">{{ t('vehicleDetail.timeline') }}</h2>
        <ProcessTimeline :steps="data.steps" />
      </div>

      <div class="card p-5 lg:col-span-2">
        <h2 class="font-semibold mb-3">{{ t('vehicleDetail.alertHistory') }}</h2>
        <div v-if="!data.alerts.length" class="text-sm text-slate-400">{{ t('vehicleDetail.noAlerts') }}</div>
        <table v-else class="w-full text-sm">
          <thead class="text-left text-slate-500 dark:text-slate-400 border-b border-slate-200 dark:border-slate-700">
            <tr>
              <th class="py-2">{{ t('vehicleDetail.alertOccurredAt') }}</th>
              <th>{{ t('vehicleDetail.alertType') }}</th>
              <th>{{ t('vehicleDetail.alertMessage') }}</th>
              <th>{{ t('vehicleDetail.alertSeverity') }}</th>
              <th>{{ t('vehicleDetail.alertStatus') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="a in data.alerts" :key="a.alert_id" class="border-b border-slate-100 dark:border-slate-700">
              <td class="py-2 text-xs text-slate-500 dark:text-slate-400">{{ a.occurred_at }}</td>
              <td>{{ a.alert_type }}</td>
              <td>{{ labels.alertMessage(a.alert_message) }}</td>
              <td><span :class="severityColor(a.severity)">{{ a.severity }}</span></td>
              <td><span :class="alertStatusColor(a.current_status)">{{ a.current_status }}</span></td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="card p-5 lg:col-span-2">
        <h2 class="font-semibold mb-3">{{ t('vehicleDetail.statusHistory') }}</h2>
        <ul class="space-y-1.5 text-sm">
          <li v-for="h in data.statusHistory" :key="h.car_status_history_id" class="flex gap-3 items-center">
            <span class="text-xs text-slate-400 font-mono w-40">{{ h.changed_at }}</span>
            <span :class="statusColor(h.status)">{{ t(`status.${h.status}`, h.status) }}</span>
            <span class="text-slate-600 dark:text-slate-300">{{ labels.reason(h.reason) || '' }}</span>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>
