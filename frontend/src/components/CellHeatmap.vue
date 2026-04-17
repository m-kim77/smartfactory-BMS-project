<script setup>
const props = defineProps({
  cells: { type: Array, default: () => [] },
  normalMin: { type: Number, default: 5 },
  normalMax: { type: Number, default: 32 },
});

function tempColor(t) {
  if (t == null) return '#e2e8f0';
  if (t < props.normalMin) return '#3b82f6';
  if (t < props.normalMax) return '#10b981';
  if (t < 45) return '#f59e0b';
  return '#dc2626';
}

function isAbnormal(t) {
  if (t == null) return false;
  return t < props.normalMin || t >= props.normalMax;
}

function cellLabel(t) {
  if (t == null) return '-';
  if (t < props.normalMin) return '저온';
  if (t < props.normalMax) return '정상';
  if (t < 45) return '경고';
  return '위험';
}
</script>

<template>
  <div>
    <div class="text-sm font-semibold mb-2 text-slate-700 dark:text-slate-200">셀 온도 (10 × 10)</div>
    <div class="grid grid-cols-10 gap-1">
      <div v-for="c in props.cells" :key="c.cell_id"
           class="cell-tile aspect-square rounded flex flex-col items-center justify-center text-white font-semibold shadow-sm"
           :class="{ 'cell-abnormal': isAbnormal(c.cell_temperature) }"
           :style="{ background: tempColor(c.cell_temperature) }"
           :title="`셀 ${c.cell_number} · ${c.cell_temperature?.toFixed?.(1) ?? '-'}℃ (${cellLabel(c.cell_temperature)})`">
        <div class="cell-num opacity-80 leading-none">{{ c.cell_number }}</div>
        <div class="cell-temp leading-tight mt-0.5">{{ c.cell_temperature?.toFixed?.(1) ?? '-' }}</div>
      </div>
    </div>
    <div class="mt-3 text-xs text-slate-500 dark:text-slate-400 flex gap-3 items-center flex-wrap">
      <span class="flex items-center gap-1"><span class="inline-block w-3 h-3 rounded bg-blue-500"></span>저온 (&lt;{{ props.normalMin }}℃)</span>
      <span class="flex items-center gap-1"><span class="inline-block w-3 h-3 rounded bg-emerald-500"></span>정상 ({{ props.normalMin }}~{{ props.normalMax }}℃)</span>
      <span class="flex items-center gap-1"><span class="inline-block w-3 h-3 rounded bg-amber-500"></span>경고 ({{ props.normalMax }}~45℃)</span>
      <span class="flex items-center gap-1"><span class="inline-block w-3 h-3 rounded bg-red-600"></span>위험 (≥45℃)</span>
      <span class="flex items-center gap-1 ml-2 text-amber-600 font-medium">
        <span class="inline-block w-3 h-3 rounded ring-2 ring-amber-400 bg-amber-500 cell-abnormal"></span>
        깜빡임 = 비정상 셀 (재검사 시 정상화)
      </span>
    </div>
  </div>
</template>

<style scoped>
.cell-tile {
  container-type: inline-size;
  position: relative;
}
.cell-num {
  font-size: 8px;
  font-size: 18cqw;
}
.cell-temp {
  font-size: 9px;
  font-size: 26cqw;
}
@keyframes cellAlert {
  0% {
    box-shadow: 0 0 0 0 rgba(251, 146, 60, 0.85),
                0 0 0 0 rgba(251, 146, 60, 0.55);
  }
  70% {
    box-shadow: 0 0 0 6px rgba(251, 146, 60, 0),
                0 0 0 3px rgba(251, 146, 60, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(251, 146, 60, 0),
                0 0 0 0 rgba(251, 146, 60, 0);
  }
}
.cell-abnormal {
  animation: cellAlert 1.4s ease-out infinite;
  z-index: 1;
}
@media (prefers-reduced-motion: reduce) {
  .cell-abnormal {
    animation: none;
    outline: 2px solid rgba(251, 146, 60, 0.9);
    outline-offset: 1px;
  }
}
</style>
