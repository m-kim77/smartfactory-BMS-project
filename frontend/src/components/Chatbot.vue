<script setup>
import { ref, nextTick, computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRouter } from 'vue-router';
import { api } from '../composables/api.js';
import { useLabels } from '../composables/labels.js';
import characterImg from '../assets/characterLLM.png';

const { t, locale } = useI18n();
const labels = useLabels();
const router = useRouter();
const open = ref(false);
const expanded = ref(false);
const message = ref('');
const loading = ref(false);
const imgOk = ref(true);
const history = ref([]); // { role, content, context }
const scroller = ref(null);
const sessionId = Math.random().toString(36).slice(2);
const reportCreating = ref(false);
const reportMsg = ref('');

const chips = computed(() => [
  t('chat.chips.inspecting'),
  t('chat.chips.shipWaiting'),
  t('chat.chips.warningSummary'),
  t('chat.chips.batteryStatus'),
]);

function onImgError() { imgOk.value = false; }

async function send(text) {
  const q = (text ?? message.value).trim();
  if (!q || loading.value) return;
  history.value.push({ role: 'user', content: q });
  message.value = '';
  loading.value = true;
  await nextTick();
  scroller.value?.scrollTo({ top: 99999 });
  try {
    const r = await api.post('/chat', { message: q, session_id: sessionId, locale: locale.value });
    history.value.push({ role: 'assistant', content: r.answer, context: r.context });
  } catch (e) {
    history.value.push({ role: 'assistant', content: e.message, detail: e.detail, error: true });
  } finally {
    loading.value = false;
    await nextTick();
    scroller.value?.scrollTo({ top: 99999, behavior: 'smooth' });
  }
}

function copyMsg(text) {
  navigator.clipboard?.writeText(text);
}

function extractCarIds(text) {
  return Array.from(new Set((text.match(/VH-\d{8}-\d{4}/g) || [])));
}

function extractTableRows(ctx) {
  if (!ctx) return null;
  if (ctx.filteredCars?.length) return ctx.filteredCars;
  return null;
}

const showTableFor = ref({});
function toggleTable(i) { showTableFor.value[i] = !showTableFor.value[i]; }

const canCreateReport = computed(() => {
  // user/assistant 모두 1건 이상 있어야 의미 있음 (최소 1 Q&A)
  const hasUser = history.value.some(m => m.role === 'user' && m.content?.trim());
  const hasAssistant = history.value.some(m => m.role === 'assistant' && !m.error && m.content?.trim());
  return hasUser && hasAssistant && !loading.value && !reportCreating.value;
});

async function createReport() {
  if (!canCreateReport.value) return;
  reportCreating.value = true;
  reportMsg.value = '';
  try {
    const messages = history.value
      .filter(m => (m.role === 'user' || m.role === 'assistant') && !m.error && m.content?.trim())
      .map(m => {
        const item = { role: m.role, content: m.content };
        // Text-to-SQL 모드 assistant 응답에는 실행된 SQL/근거를 함께 리포트에 보존
        if (m.role === 'assistant' && m.context?.mode === 'text_to_sql' && m.context?.sql) {
          item.sql = m.context.sql;
          if (m.context.reasoning) item.reasoning = m.context.reasoning;
          if (typeof m.context.rows_count === 'number') item.rows_count = m.context.rows_count;
          if (Array.isArray(m.context.rows) && m.context.rows.length) item.rows = m.context.rows;
        }
        return item;
      });
    const r = await api.post('/chat/report', {
      messages,
      session_id: sessionId,
      locale: locale.value,
    });
    reportMsg.value = t('chat.reportCreated');
    setTimeout(() => {
      reportMsg.value = '';
      open.value = false;
      router.push('/reports');
    }, 800);
  } catch (e) {
    reportMsg.value = t('chat.reportFailed') + ': ' + (e.message || '');
    setTimeout(() => (reportMsg.value = ''), 4000);
  } finally {
    reportCreating.value = false;
  }
}
</script>

<template>
  <div>
    <button v-if="!open" class="fixed right-6 bottom-6 w-16 h-16 rounded-full shadow-xl overflow-hidden border-4 border-white dark:border-slate-700 z-40"
            style="background: #1A4388" @click="open = true" :title="t('chat.openTitle')">
      <img v-if="imgOk" :src="characterImg" @error="onImgError" class="w-full h-full object-cover" />
      <svg v-else viewBox="0 0 24 24" fill="none" class="w-full h-full p-3" stroke="white" stroke-width="2">
        <rect x="4" y="7" width="16" height="12" rx="2" />
        <circle cx="9" cy="13" r="1.5" fill="white" /><circle cx="15" cy="13" r="1.5" fill="white" />
        <path d="M12 3v4M9 19v2M15 19v2" /></svg>
    </button>

    <div v-if="open"
         class="fixed card flex flex-col z-40 overflow-hidden transition-all duration-200 bg-white dark:bg-slate-800"
         :class="expanded
           ? 'right-6 bottom-6 w-[min(900px,calc(100vw-3rem))] h-[min(85vh,900px)]'
           : 'right-6 bottom-6 w-[380px] h-[600px]'">
      <div class="bg-hyundai-500 text-white p-4 flex items-center gap-3">
        <div class="w-10 h-10 rounded-full bg-white/20 overflow-hidden flex items-center justify-center">
          <img v-if="imgOk" :src="characterImg" @error="onImgError" class="w-full h-full object-cover" />
          <span v-else class="text-lg">🤖</span>
        </div>
        <div class="flex-1">
          <div class="font-semibold">{{ t('chat.title') }}</div>
          <div class="text-xs text-hyundai-100">{{ t('chat.subtitle') }}</div>
        </div>
        <button class="p-1.5 rounded hover:bg-white/10 transition"
                :title="expanded ? t('chat.collapse') : t('chat.expand')"
                @click="expanded = !expanded">
          <svg v-if="!expanded" xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24"
               fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="15 3 21 3 21 9" />
            <polyline points="9 21 3 21 3 15" />
            <line x1="21" y1="3" x2="14" y2="10" />
            <line x1="3" y1="21" x2="10" y2="14" />
          </svg>
          <svg v-else xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24"
               fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="4 14 10 14 10 20" />
            <polyline points="20 10 14 10 14 4" />
            <line x1="14" y1="10" x2="21" y2="3" />
            <line x1="3" y1="21" x2="10" y2="14" />
          </svg>
        </button>
        <button class="text-xl leading-none px-1" :title="t('chat.closeTitle')" @click="open = false">×</button>
      </div>

      <div ref="scroller" class="flex-1 overflow-auto p-4 space-y-3 bg-slate-50 dark:bg-slate-900">
        <div v-if="!history.length" class="text-center text-sm text-slate-500 dark:text-slate-400 mt-4">
          <div class="font-semibold mb-2">{{ t('chat.greeting') }}</div>
          <div class="flex flex-wrap gap-2 justify-center">
            <button v-for="c in chips" :key="c" class="btn-secondary text-xs" @click="send(c)">{{ c }}</button>
          </div>
        </div>
        <div v-for="(m, i) in history" :key="i" :class="m.role === 'user' ? 'text-right' : ''">
          <div class="inline-block max-w-[90%] text-left">
            <div class="rounded-xl px-3 py-2 text-sm whitespace-pre-wrap"
                 :class="m.role === 'user'
                   ? 'bg-hyundai-500 text-white'
                   : (m.error
                     ? 'bg-red-50 dark:bg-red-900/30 text-red-700 dark:text-red-300 border border-red-200 dark:border-red-800'
                     : 'bg-white dark:bg-slate-800 text-slate-800 dark:text-slate-100 border border-slate-200 dark:border-slate-700')">
              {{ m.content }}
              <div v-if="m.error && m.detail" class="mt-1.5 pt-1.5 border-t border-red-200 dark:border-red-800 text-[11px] text-red-600 dark:text-red-400 font-mono whitespace-pre-wrap break-all">
                {{ m.detail }}
              </div>
            </div>
            <div v-if="m.role === 'assistant' && !m.error" class="flex gap-2 mt-1 text-xs text-slate-500 dark:text-slate-400">
              <button class="hover:underline" @click="copyMsg(m.content)">📋 {{ t('chat.copy') }}</button>
              <button v-if="extractTableRows(m.context)" class="hover:underline" @click="toggleTable(i)">📊 {{ t('chat.asTable') }}</button>
            </div>
            <div v-if="m.role === 'assistant' && extractCarIds(m.content).length" class="flex flex-wrap gap-1 mt-1">
              <router-link v-for="id in extractCarIds(m.content)" :key="id" :to="`/vehicles/${id}`"
                           class="text-[10px] px-2 py-0.5 rounded-full bg-hyundai-50 dark:bg-hyundai-900/30 text-hyundai-500 dark:text-hyundai-300 hover:bg-hyundai-100 dark:hover:bg-hyundai-900/50">
                🚗 {{ id }}
              </router-link>
            </div>
            <div v-if="showTableFor[i]" class="mt-2 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg overflow-hidden">
              <table class="text-xs w-full">
                <thead class="bg-slate-100 dark:bg-slate-900"><tr><th class="p-1">{{ t('chat.tableVehicle') }}</th><th class="p-1">{{ t('chat.tableModel') }}</th><th class="p-1">{{ t('chat.tableStatus') }}</th><th class="p-1">{{ t('chat.tableCountry') }}</th></tr></thead>
                <tbody>
                  <tr v-for="r in extractTableRows(m.context)" :key="r.car_id" class="border-t border-slate-200 dark:border-slate-700">
                    <td class="p-1 font-mono">{{ r.car_id }}</td>
                    <td class="p-1">{{ labels.model(r.model_name) }}</td>
                    <td class="p-1">{{ t(`status.${r.current_status}`, r.current_status) }}</td>
                    <td class="p-1">{{ labels.country(r.destination_country) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
        <div v-if="loading" class="text-xs text-slate-500 dark:text-slate-400">{{ t('chat.generating') }}</div>
      </div>

      <div class="p-3 border-t border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800">
        <div v-if="canCreateReport || reportCreating || reportMsg" class="flex items-center justify-between gap-2 mb-2">
          <button class="text-xs px-3 py-1.5 rounded-lg bg-emerald-50 dark:bg-emerald-900/20 text-emerald-700 dark:text-emerald-300 hover:bg-emerald-100 dark:hover:bg-emerald-900/40 font-medium transition disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-1.5"
                  :disabled="!canCreateReport"
                  @click="createReport">
            <span v-if="reportCreating">⏳</span>
            <span v-else>📝</span>
            {{ reportCreating ? t('chat.reportCreating') : t('chat.createReport') }}
          </button>
          <span v-if="reportMsg" class="text-xs"
                :class="reportMsg.startsWith(t('chat.reportFailed')) ? 'text-red-600' : 'text-emerald-600'">
            {{ reportMsg }}
          </span>
        </div>
        <form class="flex gap-2" @submit.prevent="send()">
          <input v-model="message" class="input flex-1" :placeholder="t('chat.placeholder')" />
          <button class="btn-primary" :disabled="loading || !message.trim()">{{ t('chat.send') }}</button>
        </form>
      </div>
    </div>
  </div>
</template>
