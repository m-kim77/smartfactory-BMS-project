<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue';

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  options: { type: Array, default: () => [] },
  allLabel: { type: String, default: '전체' },
  searchPlaceholder: { type: String, default: '검색…' },
  searchable: { type: Boolean, default: false },
  labelFn: { type: Function, default: null },
});
const labelOf = (v) => (props.labelFn ? props.labelFn(v) : String(v));
const emit = defineEmits(['update:modelValue']);

const open = ref(false);
const search = ref('');
const wrapperRef = ref(null);

const filteredOptions = computed(() => {
  if (!props.searchable || !search.value) return props.options;
  const q = search.value.toLowerCase();
  return props.options.filter(o => labelOf(o).toLowerCase().includes(q));
});

function toggle(opt) {
  const s = new Set(props.modelValue);
  if (s.has(opt)) s.delete(opt);
  else s.add(opt);
  emit('update:modelValue', [...s]);
}
function clearAll() { emit('update:modelValue', []); }

const displayText = computed(() => {
  if (!props.modelValue.length) return props.allLabel;
  if (props.modelValue.length === 1) return labelOf(props.modelValue[0]);
  return `${props.modelValue.length}개 선택됨`;
});

function onDocClick(e) {
  if (wrapperRef.value && !wrapperRef.value.contains(e.target)) open.value = false;
}
onMounted(() => document.addEventListener('click', onDocClick));
onBeforeUnmount(() => document.removeEventListener('click', onDocClick));
</script>

<template>
  <div ref="wrapperRef" class="relative">
    <button type="button"
            class="input w-full text-left flex items-center justify-between gap-2"
            @click.stop="open = !open">
      <span class="truncate" :class="modelValue.length ? '' : 'text-slate-400 dark:text-slate-500'">
        {{ displayText }}
      </span>
      <span class="text-xs text-slate-400 shrink-0">▾</span>
    </button>
    <div v-if="open"
         class="absolute z-30 mt-1 left-0 right-0 max-h-64 overflow-y-auto rounded-lg border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-800 shadow-lg">
      <div v-if="searchable" class="p-2 border-b border-slate-100 dark:border-slate-700 sticky top-0 bg-white dark:bg-slate-800 z-10">
        <input v-model="search" type="text" class="input w-full text-xs" :placeholder="searchPlaceholder" @click.stop />
      </div>
      <button type="button"
              class="w-full text-left px-3 py-1.5 text-sm hover:bg-slate-50 dark:hover:bg-slate-700 border-b border-slate-100 dark:border-slate-700 text-slate-500 dark:text-slate-400"
              @click="clearAll">
        ✕ {{ allLabel }}
      </button>
      <label v-for="opt in filteredOptions" :key="opt"
             class="flex items-center gap-2 px-3 py-1.5 text-sm hover:bg-slate-50 dark:hover:bg-slate-700 cursor-pointer">
        <input type="checkbox" :checked="modelValue.includes(opt)"
               @change="toggle(opt)" class="accent-hyundai-500" />
        <span class="truncate">{{ labelOf(opt) }}</span>
      </label>
      <div v-if="searchable && !filteredOptions.length" class="px-3 py-2 text-xs text-slate-400">
        결과 없음
      </div>
    </div>
  </div>
</template>
