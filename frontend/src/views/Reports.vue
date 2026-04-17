<script setup>
import { ref, onMounted, computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { api } from '../composables/api.js';

const { t } = useI18n();
const items = ref([]);
const selected = ref(null);
const loading = ref(false);
const detailLoading = ref(false);
const err = ref('');

async function load() {
  loading.value = true;
  try {
    const r = await api.get('/reports');
    items.value = r.items || [];
    err.value = '';
  } catch (e) { err.value = e.message; }
  finally { loading.value = false; }
}

async function open(id) {
  detailLoading.value = true;
  try {
    selected.value = await api.get(`/reports/${id}`);
  } catch (e) { err.value = e.message; selected.value = null; }
  finally { detailLoading.value = false; }
}

async function remove(id) {
  if (!confirm(t('reports.confirmDelete'))) return;
  try {
    await api.del(`/reports/${id}`);
    if (selected.value?.report_id === id) selected.value = null;
    await load();
  } catch (e) { err.value = e.message; }
}

function fmtDate(d) {
  if (!d) return '';
  return d.replace('T', ' ').slice(0, 16);
}

function modeLabel(m) {
  return m === 'text_to_sql' ? 'Text-to-SQL' : 'RAG-lite';
}

const sortedItems = computed(() => [...items.value].sort((a, b) => (b.created_at || '').localeCompare(a.created_at || '')));

// Text-to-SQL 모드 대화에서 실행된 SQL 목록 (assistant 메시지에 sql 필드가 있는 것만)
const sqlQueries = computed(() => {
  const msgs = selected.value?.content?.messages || [];
  const out = [];
  msgs.forEach((m, idx) => {
    if (m.role === 'assistant' && m.sql) {
      out.push({ idx, sql: m.sql, reasoning: m.reasoning, rows_count: m.rows_count, rows: m.rows, chart: chartFromRows(m.rows) });
    }
  });
  return out;
});

// 집계형 결과(라벨 + 숫자 2컬럼)를 막대 차트 데이터로 변환
function chartFromRows(rows) {
  if (!Array.isArray(rows) || rows.length < 2 || rows.length > 30) return null;
  const keys = Object.keys(rows[0] || {});
  if (keys.length < 2) return null;
  // 첫 숫자 컬럼을 값으로, 나머지 중 문자/다른 걸 라벨로
  let valueKey = keys.find(k => rows.every(r => typeof r[k] === 'number'));
  if (!valueKey) return null;
  const labelKey = keys.find(k => k !== valueKey) || keys[0];
  const points = rows.map(r => ({ label: String(r[labelKey] ?? ''), value: Number(r[valueKey]) }))
    .filter(p => Number.isFinite(p.value));
  if (points.length < 2) return null;
  const max = Math.max(...points.map(p => p.value));
  if (max <= 0) return null;
  return { labelKey, valueKey, points, max };
}

onMounted(load);

function downloadMd() {
  if (!selected.value) return;
  const r = selected.value;
  const c = r.content || {};
  let md = `# ${r.title}\n\n`;
  md += `- ${t('reports.fieldDate')}: ${fmtDate(r.created_at)}\n`;
  md += `- ${t('reports.fieldMode')}: ${modeLabel(r.llm_mode)}\n`;
  md += `- ${t('reports.fieldModel')}: ${r.llm_model || '-'}\n`;
  md += `- ${t('reports.fieldMsgCount')}: ${r.message_count}\n\n`;
  if (c.summary) md += `## ${t('reports.sectionSummary')}\n\n${c.summary}\n\n`;
  if (c.key_findings?.length) {
    md += `## ${t('reports.sectionFindings')}\n\n`;
    c.key_findings.forEach(f => { md += `- ${f}\n`; });
    md += '\n';
  }
  if (c.action_items?.length) {
    md += `## ${t('reports.sectionActions')}\n\n`;
    c.action_items.forEach(a => { md += `- [ ] ${a}\n`; });
    md += '\n';
  }
  if (c.data_points?.length) {
    md += `## ${t('reports.sectionData')}\n\n`;
    c.data_points.forEach(d => { md += `- ${d}\n`; });
    md += '\n';
  }
  if (r.car_ids?.length) {
    md += `## ${t('reports.sectionCars')}\n\n${r.car_ids.join(', ')}\n\n`;
  }
  // Text-to-SQL 모드에서 실행된 SQL 쿼리 섹션
  const sqls = (c.messages || []).filter(m => m.role === 'assistant' && m.sql);
  if (sqls.length) {
    md += `## ${t('reports.sectionSql')}\n\n`;
    sqls.forEach((m, i) => {
      md += `**[${i + 1}]`;
      if (typeof m.rows_count === 'number') md += ` (${m.rows_count} rows)`;
      md += `**\n\n`;
      if (m.reasoning) md += `> ${m.reasoning}\n\n`;
      md += '```sql\n' + m.sql + '\n```\n\n';
    });
  }
  if (c.messages?.length) {
    md += `## ${t('reports.sectionConversation')}\n\n`;
    c.messages.forEach((m, i) => {
      md += `**[${i + 1}] ${m.role === 'user' ? 'Q' : 'A'}:**\n${m.content}\n\n`;
      if (m.role === 'assistant' && m.sql) {
        md += '```sql\n' + m.sql + '\n```\n\n';
      }
    });
  }
  const blob = new Blob([md], { type: 'text/markdown;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `report_${r.report_id}_${(r.title || 'untitled').replace(/[^\w가-힣-]+/g, '_').slice(0, 40)}.md`;
  a.click();
  URL.revokeObjectURL(url);
}
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-4">
      <h1 class="text-2xl font-bold">{{ t('reports.title') }}</h1>
      <button class="btn-secondary text-sm" @click="load">{{ t('common.refresh') }}</button>
    </div>

    <div v-if="err" class="card p-3 mb-3 text-red-600 text-sm">{{ err }}</div>

    <div class="grid grid-cols-1 lg:grid-cols-12 gap-5">
      <!-- 목록 -->
      <div class="lg:col-span-5 card p-4">
        <h2 class="font-semibold mb-3 text-sm text-slate-600 dark:text-slate-300">
          {{ t('reports.listTitle') }} ({{ sortedItems.length }})
        </h2>
        <div v-if="loading" class="text-sm text-slate-400">{{ t('common.loading') }}</div>
        <div v-else-if="!sortedItems.length" class="text-sm text-slate-400 text-center py-8">
          {{ t('reports.empty') }}
        </div>
        <ul v-else class="space-y-2 max-h-[calc(100vh-220px)] overflow-auto pr-1">
          <li v-for="r in sortedItems" :key="r.report_id"
              class="p-3 rounded-lg border cursor-pointer transition"
              :class="selected?.report_id === r.report_id
                ? 'border-hyundai-400 bg-hyundai-50 dark:bg-hyundai-900/20'
                : 'border-slate-200 dark:border-slate-700 hover:border-hyundai-300 hover:bg-slate-50 dark:hover:bg-slate-800'"
              @click="open(r.report_id)">
            <div class="flex items-start justify-between gap-2">
              <div class="flex-1 min-w-0">
                <div class="text-sm font-semibold truncate">{{ r.title }}</div>
                <div class="text-xs text-slate-500 dark:text-slate-400 mt-0.5 line-clamp-2">
                  {{ r.summary || t('reports.noSummary') }}
                </div>
              </div>
              <span class="text-[10px] px-2 py-0.5 rounded-full font-semibold shrink-0"
                    :class="r.llm_mode === 'text_to_sql'
                      ? 'bg-purple-100 dark:bg-purple-900/30 text-purple-700 dark:text-purple-300'
                      : 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300'">
                {{ modeLabel(r.llm_mode) }}
              </span>
            </div>
            <div class="flex items-center justify-between mt-2 text-[11px] text-slate-400">
              <span>{{ fmtDate(r.created_at) }}</span>
              <span>💬 {{ r.message_count }} · 🚗 {{ r.car_ids?.length || 0 }}</span>
            </div>
          </li>
        </ul>
      </div>

      <!-- 상세 -->
      <div class="lg:col-span-7 card p-5">
        <div v-if="!selected && !detailLoading" class="text-center text-sm text-slate-400 py-16">
          {{ t('reports.selectHint') }}
        </div>
        <div v-else-if="detailLoading" class="text-sm text-slate-400">{{ t('common.loading') }}</div>
        <div v-else>
          <div class="flex items-start justify-between mb-3 gap-2">
            <h2 class="text-xl font-bold flex-1">{{ selected.title }}</h2>
            <div class="flex gap-1 shrink-0">
              <button class="btn-secondary text-xs" @click="downloadMd">⬇ {{ t('reports.download') }}</button>
              <button class="px-2 py-1 rounded text-xs bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-300 hover:bg-red-100 dark:hover:bg-red-900/40 transition"
                      @click="remove(selected.report_id)">🗑 {{ t('reports.delete') }}</button>
            </div>
          </div>

          <!-- 메타데이터 -->
          <div class="grid grid-cols-2 md:grid-cols-4 gap-2 text-xs mb-4 p-3 bg-slate-50 dark:bg-slate-800 rounded-lg">
            <div>
              <div class="text-slate-400">{{ t('reports.fieldDate') }}</div>
              <div class="font-medium">{{ fmtDate(selected.created_at) }}</div>
            </div>
            <div>
              <div class="text-slate-400">{{ t('reports.fieldMode') }}</div>
              <div class="font-medium">{{ modeLabel(selected.llm_mode) }}</div>
            </div>
            <div>
              <div class="text-slate-400">{{ t('reports.fieldModel') }}</div>
              <div class="font-medium truncate">{{ selected.llm_model || '-' }}</div>
            </div>
            <div>
              <div class="text-slate-400">{{ t('reports.fieldMsgCount') }}</div>
              <div class="font-medium">{{ selected.message_count }}</div>
            </div>
          </div>

          <div v-if="selected.content?._llm_error" class="mb-3 p-2 bg-amber-50 dark:bg-amber-900/20 text-amber-700 dark:text-amber-300 text-xs rounded">
            ⚠ {{ t('reports.summaryFailed') }}: {{ selected.content._llm_error }}
          </div>

          <!-- 요약 -->
          <section v-if="selected.content?.summary" class="mb-4">
            <h3 class="text-sm font-bold mb-2 text-hyundai-600 dark:text-hyundai-300">
              📋 {{ t('reports.sectionSummary') }}
            </h3>
            <p class="text-sm leading-relaxed whitespace-pre-wrap">{{ selected.content.summary }}</p>
          </section>

          <!-- 핵심 발견 -->
          <section v-if="selected.content?.key_findings?.length" class="mb-4">
            <h3 class="text-sm font-bold mb-2 text-hyundai-600 dark:text-hyundai-300">
              🔍 {{ t('reports.sectionFindings') }}
            </h3>
            <ul class="space-y-1.5 text-sm">
              <li v-for="(f, i) in selected.content.key_findings" :key="i"
                  class="flex gap-2">
                <span class="text-hyundai-500 shrink-0">•</span>
                <span>{{ f }}</span>
              </li>
            </ul>
          </section>

          <!-- 액션 아이템 -->
          <section v-if="selected.content?.action_items?.length" class="mb-4">
            <h3 class="text-sm font-bold mb-2 text-hyundai-600 dark:text-hyundai-300">
              ✅ {{ t('reports.sectionActions') }}
            </h3>
            <ul class="space-y-1.5 text-sm">
              <li v-for="(a, i) in selected.content.action_items" :key="i"
                  class="flex gap-2 items-start">
                <span class="text-emerald-500 shrink-0 mt-0.5">☐</span>
                <span>{{ a }}</span>
              </li>
            </ul>
          </section>

          <!-- 인용 데이터 -->
          <section v-if="selected.content?.data_points?.length" class="mb-4">
            <h3 class="text-sm font-bold mb-2 text-hyundai-600 dark:text-hyundai-300">
              📊 {{ t('reports.sectionData') }}
            </h3>
            <ul class="space-y-1 text-xs">
              <li v-for="(d, i) in selected.content.data_points" :key="i"
                  class="font-mono bg-slate-50 dark:bg-slate-800 px-2 py-1 rounded">
                {{ d }}
              </li>
            </ul>
          </section>

          <!-- 차량 ID 링크 -->
          <section v-if="selected.car_ids?.length" class="mb-4">
            <h3 class="text-sm font-bold mb-2 text-hyundai-600 dark:text-hyundai-300">
              🚗 {{ t('reports.sectionCars') }}
            </h3>
            <div class="flex flex-wrap gap-1">
              <router-link v-for="id in selected.car_ids" :key="id" :to="`/vehicles/${id}`"
                           class="text-[11px] px-2 py-1 rounded-full bg-hyundai-50 dark:bg-hyundai-900/30 text-hyundai-600 dark:text-hyundai-300 hover:bg-hyundai-100 dark:hover:bg-hyundai-900/50 font-mono">
                {{ id }}
              </router-link>
            </div>
          </section>

          <!-- 실행된 SQL (Text-to-SQL 모드일 때만) -->
          <section v-if="sqlQueries.length" class="mb-4">
            <h3 class="text-sm font-bold mb-2 text-hyundai-600 dark:text-hyundai-300">
              🗄️ {{ t('reports.sectionSql') }} ({{ sqlQueries.length }})
            </h3>
            <div class="space-y-2">
              <div v-for="(q, i) in sqlQueries" :key="i"
                   class="rounded-lg border border-slate-200 dark:border-slate-700 overflow-hidden">
                <div class="flex items-center justify-between px-2 py-1 bg-slate-100 dark:bg-slate-800 text-[11px] text-slate-600 dark:text-slate-400">
                  <span class="font-semibold">Q{{ i + 1 }}</span>
                  <span v-if="typeof q.rows_count === 'number'">{{ q.rows_count }} rows</span>
                </div>
                <div v-if="q.reasoning"
                     class="px-2 py-1 text-[11px] text-slate-500 dark:text-slate-400 italic border-b border-slate-200 dark:border-slate-700">
                  {{ q.reasoning }}
                </div>
                <div v-if="q.chart" class="p-3 border-b border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900">
                  <div class="text-[11px] text-slate-500 dark:text-slate-400 mb-2">
                    📊 {{ q.chart.labelKey }} × {{ q.chart.valueKey }}
                  </div>
                  <div class="space-y-1">
                    <div v-for="(p, j) in q.chart.points" :key="j" class="flex items-center gap-2 text-xs">
                      <div class="w-24 truncate text-right text-slate-600 dark:text-slate-300 shrink-0">{{ p.label }}</div>
                      <div class="flex-1 h-5 bg-slate-100 dark:bg-slate-800 rounded overflow-hidden relative">
                        <div class="h-full bg-hyundai-500 dark:bg-hyundai-400 rounded transition-all"
                             :style="{ width: ((p.value / q.chart.max) * 100) + '%' }"></div>
                      </div>
                      <div class="w-12 text-right font-mono text-slate-700 dark:text-slate-200 shrink-0">{{ p.value }}</div>
                    </div>
                  </div>
                </div>
                <pre class="p-2 text-xs font-mono whitespace-pre-wrap break-all bg-slate-50 dark:bg-slate-900 text-slate-800 dark:text-slate-200">{{ q.sql }}</pre>
              </div>
            </div>
          </section>

          <!-- 원본 대화 (접을 수 있게) -->
          <section v-if="selected.content?.messages?.length">
            <details class="text-sm">
              <summary class="cursor-pointer font-bold text-hyundai-600 dark:text-hyundai-300 hover:underline">
                💬 {{ t('reports.sectionConversation') }} ({{ selected.content.messages.length }})
              </summary>
              <div class="mt-3 space-y-2">
                <div v-for="(m, i) in selected.content.messages" :key="i"
                     class="p-2.5 rounded-lg text-sm whitespace-pre-wrap"
                     :class="m.role === 'user'
                       ? 'bg-hyundai-50 dark:bg-hyundai-900/20 border-l-4 border-hyundai-400'
                       : 'bg-slate-50 dark:bg-slate-800 border-l-4 border-slate-300 dark:border-slate-600'">
                  <div class="text-[10px] font-semibold text-slate-500 dark:text-slate-400 mb-1 uppercase">
                    {{ m.role === 'user' ? t('reports.you') : t('reports.assistant') }} #{{ i + 1 }}
                  </div>
                  {{ m.content }}
                  <pre v-if="m.role === 'assistant' && m.sql"
                       class="mt-2 p-2 text-xs font-mono whitespace-pre-wrap break-all rounded bg-slate-100 dark:bg-slate-900 text-slate-800 dark:text-slate-200 border border-slate-200 dark:border-slate-700">{{ m.sql }}</pre>
                </div>
              </div>
            </details>
          </section>
        </div>
      </div>
    </div>
  </div>
</template>
