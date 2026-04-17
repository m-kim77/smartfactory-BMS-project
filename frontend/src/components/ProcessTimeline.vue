<script setup>
import { useI18n } from 'vue-i18n';
import { useLabels } from '../composables/labels.js';
const { t } = useI18n();
const labels = useLabels();
const props = defineProps({ steps: { type: Array, default: () => [] } });

function stepLabel(name) { return t(`vehicleDetail.stepLabels.${name}`, name); }
function iconFor(s) {
  if (s === 'PASS') return '✔';
  if (s === 'FAIL') return '✘';
  if (s === 'IN_PROGRESS') return '●';
  return '○';
}
function colorFor(s, active) {
  if (s === 'PASS') return 'bg-emerald-500 text-white';
  if (s === 'FAIL') return 'bg-red-500 text-white';
  if (s === 'IN_PROGRESS') return active ? 'bg-hyundai-500 text-white pulse-blue' : 'bg-hyundai-500 text-white';
  return 'bg-slate-200 text-slate-500';
}
function isActive(i) {
  return i === props.steps.length - 1 && props.steps[i]?.step_status === 'IN_PROGRESS';
}
</script>

<template>
  <div>
    <div v-if="!props.steps.length" class="text-sm text-slate-400">{{ t('vehicleDetail.noHistory') }}</div>
    <ol v-else class="space-y-2">
      <li v-for="(s, i) in props.steps" :key="i" class="flex items-center gap-3">
        <div :class="'w-8 h-8 rounded-full flex items-center justify-center text-sm font-bold ' + colorFor(s.step_status, isActive(i))">
          {{ iconFor(s.step_status) }}
        </div>
        <div class="flex-1">
          <div class="text-sm font-medium">{{ stepLabel(s.step_name) }}
            <span class="text-xs text-slate-400 ml-2">#{{ s.step_order }}</span>
          </div>
          <div class="text-xs text-slate-500">
            {{ s.started_at || '' }}{{ s.ended_at ? ' → ' + s.ended_at : '' }}
            <span v-if="s.note" class="text-red-600 ml-2">· {{ labels.alertMessage(s.note) }}</span>
          </div>
        </div>
        <span class="text-xs px-2 py-0.5 rounded-full"
              :class="[
                s.step_status === 'PASS' ? 'bg-emerald-100 text-emerald-700' : s.step_status === 'FAIL' ? 'bg-red-100 text-red-700' : s.step_status === 'IN_PROGRESS' ? 'bg-hyundai-50 text-hyundai-500' : 'bg-slate-100 text-slate-500',
                isActive(i) ? 'pulse-blue' : ''
              ]">
          {{ s.step_status }}
        </span>
      </li>
    </ol>
  </div>
</template>
