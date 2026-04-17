<script setup>
import { onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import { useFilterStore } from '../stores/filters.js';
import { useLabels } from '../composables/labels.js';

const { t } = useI18n();
const filterStore = useFilterStore();
const labels = useLabels();
onMounted(() => filterStore.loadFactories());
</script>

<template>
  <div class="flex items-center gap-1.5 flex-wrap">
    <button
      class="px-2.5 py-1 rounded-lg text-xs font-medium transition"
      :class="!filterStore.factoryIds.length
        ? 'bg-hyundai-500 text-white'
        : 'bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-200 hover:bg-slate-200 dark:hover:bg-slate-600'"
      @click="filterStore.clearFactories()">{{ t('common.all') }}</button>
    <button
      v-for="f in filterStore.factories"
      :key="f.factory_id"
      class="px-2.5 py-1 rounded-lg text-xs font-medium transition"
      :class="filterStore.factoryIds.includes(f.factory_id)
        ? 'bg-hyundai-500 text-white'
        : 'bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-200 hover:bg-slate-200 dark:hover:bg-slate-600'"
      @click="filterStore.toggleFactory(f.factory_id)">
      {{ labels.factory(f.factory_name) }}
      <span class="text-[10px] opacity-70 ml-0.5">{{ labels.brand(f.brand) }}</span>
    </button>
  </div>
</template>
