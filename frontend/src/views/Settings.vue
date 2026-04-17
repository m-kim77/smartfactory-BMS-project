<script setup>
import { ref, onMounted, computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { api } from '../composables/api.js';
import { useLabels } from '../composables/labels.js';

const { t } = useI18n();
const labels = useLabels();

const settings = ref([]);
const countries = ref([]);
const llmModels = ref([]);
const llmError = ref('');
const saveMsg = ref('');

function describe(s) {
  return t(`settings.descriptions.${s.setting_key}`, s.description || s.setting_key);
}

async function load() {
  settings.value = (await api.get('/settings')).items;
  countries.value = (await api.get('/settings/countries')).items;
  try {
    llmModels.value = (await api.get('/settings/llm/models')).items || [];
    llmError.value = '';
  } catch (e) {
    llmModels.value = [];
    llmError.value = t('settings.llmConnectFailed');
  }
}
onMounted(load);

async function save(key, value) {
  saveMsg.value = '';
  await api.put(`/settings/${key}`, { value });
  saveMsg.value = `${key} ${t('settings.savedSuffix')}`;
  setTimeout(() => (saveMsg.value = ''), 2000);
  await load();
}

async function toggleCountry(c) {
  await api.put(`/settings/countries/${c.country_id}`, { is_allowed: !c.is_allowed });
  await load();
}

const genSetting = computed(() => settings.value.find(s => s.setting_key === 'vehicle_generation_interval_ms'));
const durationSettings = computed(() => settings.value.filter(s => s.setting_key.includes('duration_ms') || s.setting_key.includes('delay_ms')));
const probSettings = computed(() => settings.value.filter(s => s.setting_key.startsWith('prob_')));
const llmSetting = computed(() => settings.value.find(s => s.setting_key === 'llm_model'));
const llmModeSetting = computed(() => settings.value.find(s => s.setting_key === 'llm_mode'));
const llmMaxTokensSetting = computed(() => settings.value.find(s => s.setting_key === 'llm_max_tokens'));
const llmContextAlertsSetting = computed(() => settings.value.find(s => s.setting_key === 'llm_context_alerts'));
const llmContextCarsSetting = computed(() => settings.value.find(s => s.setting_key === 'llm_context_cars'));
const llmAlertMsgMaxSetting = computed(() => settings.value.find(s => s.setting_key === 'llm_alert_msg_max'));
const llmProviderSetting = computed(() => settings.value.find(s => s.setting_key === 'llm_provider'));
const llmBaseUrlSetting = computed(() => settings.value.find(s => s.setting_key === 'llm_base_url'));
const llmOpenAiKeySetting = computed(() => settings.value.find(s => s.setting_key === 'llm_openai_api_key'));
const llmGeminiKeySetting = computed(() => settings.value.find(s => s.setting_key === 'llm_gemini_api_key'));
const currentProvider = computed(() => llmProviderSetting.value?.setting_value || 'lm_studio');

function maskedKey(v) {
  if (!v) return '';
  if (v.length <= 8) return '••••';
  return v.slice(0, 4) + '••••' + v.slice(-4);
}
const shiftSetting = computed(() => settings.value.find(s => s.setting_key === 'shift_duration_min'));
const repairMultSetting = computed(() => settings.value.find(s => s.setting_key === 'repair_duration_multiplier'));
const SHIFT_OPTIONS = [5, 10, 15, 20, 30];

const bulkDurationSec = ref('');

async function applyBulkDuration() {
  const sec = parseFloat(bulkDurationSec.value);
  if (isNaN(sec) || sec <= 0) return;
  const ms = Math.round(sec * 1000);
  for (const s of durationSettings.value) {
    await api.put(`/settings/${s.setting_key}`, { value: ms });
  }
  saveMsg.value = t('settings.allInspectionSaved');
  setTimeout(() => (saveMsg.value = ''), 2000);
  bulkDurationSec.value = '';
  await load();
}

function msToSec(ms) { return (Number(ms) / 1000).toFixed(1); }
function secToMs(sec) { return Math.round(parseFloat(sec) * 1000); }
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-4">
      <h1 class="text-2xl font-bold">{{ t('settings.title') }}</h1>
      <div v-if="saveMsg" class="text-sm text-emerald-600">{{ saveMsg }}</div>
    </div>

    <section class="card p-5 mb-5">
      <h2 class="font-semibold mb-3">{{ t('settings.section1') }}</h2>
      <div v-if="genSetting" class="flex items-center gap-3">
        <label class="text-sm w-48">{{ t('settings.genInterval') }}</label>
        <div class="flex items-center gap-1">
          <input class="input w-28" type="number" min="0.1" step="0.1"
                 :value="msToSec(genSetting.setting_value)"
                 @change="e => save(genSetting.setting_key, secToMs(e.target.value))" />
          <span class="text-xs text-slate-400">{{ t('settings.seconds') }}</span>
        </div>
      </div>
    </section>

    <section class="card p-5 mb-5">
      <div class="flex items-center justify-between mb-3">
        <h2 class="font-semibold">{{ t('settings.section2') }}</h2>
        <div class="flex items-center gap-2">
          <span class="text-xs text-slate-500">{{ t('settings.bulkApply') }}</span>
          <input class="input w-24 text-sm" type="number" min="0.1" step="0.1" :placeholder="t('settings.bulkPlaceholder')"
                 v-model="bulkDurationSec"
                 @keydown.enter="applyBulkDuration" />
          <button class="px-3 py-1.5 rounded-lg bg-hyundai-500 text-white text-xs font-medium hover:bg-hyundai-600 transition"
                  @click="applyBulkDuration">{{ t('settings.apply') }}</button>
        </div>
      </div>
      <div v-if="repairMultSetting" class="flex items-center gap-3 mb-3 pb-3 border-b border-slate-100 dark:border-slate-700">
        <label class="text-sm w-48">{{ t('settings.repairMultiplier') }}</label>
        <input class="input w-28" type="number" min="0.1" step="0.1"
               :value="repairMultSetting.setting_value"
               @change="e => save('repair_duration_multiplier', e.target.value)" />
        <span class="text-xs text-slate-400">{{ t('settings.repairMultSuffix') }}</span>
        <span class="text-xs text-slate-500">{{ t('settings.repairMultHint') }}</span>
      </div>
      <div class="grid md:grid-cols-2 gap-3">
        <div v-for="s in durationSettings" :key="s.setting_key" class="flex items-center gap-3">
          <label class="text-xs text-slate-600 dark:text-slate-300 w-64">{{ describe(s) }}</label>
          <div class="flex items-center gap-1">
            <input class="input w-28" type="number" min="0.1" step="0.1"
                   :value="msToSec(s.setting_value)"
                   @change="e => save(s.setting_key, secToMs(e.target.value))" />
            <span class="text-xs text-slate-400">{{ t('settings.seconds') }}</span>
          </div>
        </div>
      </div>
    </section>

    <section class="card p-5 mb-5">
      <h2 class="font-semibold mb-3">{{ t('settings.section3') }}</h2>
      <div class="space-y-3">
        <div v-for="s in probSettings" :key="s.setting_key" class="flex items-center gap-4">
          <label class="text-xs text-slate-600 dark:text-slate-300 w-48">{{ describe(s) }}</label>
          <input class="flex-1" type="range" min="0" max="100" step="0.1" :value="s.setting_value"
                 @change="e => save(s.setting_key, e.target.value)" />
          <div class="flex items-center gap-1">
            <input class="input w-20 text-sm text-right font-mono" type="number" min="0" max="100" step="0.1"
                   :value="Number(s.setting_value).toFixed(1)"
                   @change="e => save(s.setting_key, Math.min(100, Math.max(0, parseFloat(e.target.value) || 0)).toFixed(1))" />
            <span class="text-xs text-slate-400">{{ t('settings.percent') }}</span>
          </div>
        </div>
      </div>
    </section>

    <section class="card p-5 mb-5">
      <h2 class="font-semibold mb-3">{{ t('settings.section5') }}</h2>
      <div v-if="shiftSetting" class="flex items-center gap-3">
        <label class="text-sm w-48">{{ t('settings.shiftPeriod') }}</label>
        <div class="flex gap-2">
          <button v-for="opt in SHIFT_OPTIONS" :key="opt"
                  class="px-4 py-1.5 rounded-lg border text-sm font-medium transition"
                  :class="Number(shiftSetting.setting_value) === opt
                    ? 'bg-hyundai-500 border-hyundai-500 text-white'
                    : 'bg-white dark:bg-slate-800 border-slate-300 dark:border-slate-600 text-slate-600 dark:text-slate-200 hover:border-hyundai-400'"
                  @click="save('shift_duration_min', opt)">
            {{ opt }}{{ t('settings.minutes') }}
          </button>
        </div>
        <span class="text-xs text-slate-500">{{ t('settings.shiftHint') }}</span>
      </div>
    </section>

    <section class="card p-5 mb-5">
      <h2 class="font-semibold mb-3">{{ t('settings.section6') }}</h2>

      <div v-if="llmProviderSetting" class="flex items-center gap-3 mb-4">
        <label class="text-sm w-48">{{ t('settings.llmProvider') }}</label>
        <select class="input max-w-md" :value="currentProvider"
                @change="e => save('llm_provider', e.target.value)">
          <option value="lm_studio">{{ t('settings.providerLmStudio') }}</option>
          <option value="openai">{{ t('settings.providerOpenAi') }}</option>
          <option value="gemini">{{ t('settings.providerGemini') }}</option>
        </select>
        <span class="text-xs text-slate-500">{{ t('settings.llmProviderHint') }}</span>
      </div>

      <div v-if="currentProvider === 'lm_studio' && llmBaseUrlSetting" class="flex items-center gap-3 mb-4">
        <label class="text-sm w-48">{{ t('settings.llmBaseUrl') }}</label>
        <input class="input max-w-md flex-1" type="text"
               :value="llmBaseUrlSetting.setting_value"
               :placeholder="'http://127.0.0.1:1234'"
               @change="e => save('llm_base_url', e.target.value)" />
        <span class="text-xs text-slate-500">{{ t('settings.llmBaseUrlHint') }}</span>
      </div>

      <div v-if="currentProvider === 'openai' && llmOpenAiKeySetting" class="flex items-center gap-3 mb-4">
        <label class="text-sm w-48">{{ t('settings.llmOpenAiKey') }}</label>
        <input class="input max-w-md flex-1 font-mono" type="password"
               :value="llmOpenAiKeySetting.setting_value"
               placeholder="sk-..."
               @change="e => save('llm_openai_api_key', e.target.value)" />
        <span v-if="llmOpenAiKeySetting.setting_value" class="text-xs text-slate-400 font-mono">{{ maskedKey(llmOpenAiKeySetting.setting_value) }}</span>
      </div>

      <div v-if="currentProvider === 'gemini' && llmGeminiKeySetting" class="flex items-center gap-3 mb-4">
        <label class="text-sm w-48">{{ t('settings.llmGeminiKey') }}</label>
        <input class="input max-w-md flex-1 font-mono" type="password"
               :value="llmGeminiKeySetting.setting_value"
               placeholder="AIza..."
               @change="e => save('llm_gemini_api_key', e.target.value)" />
        <span v-if="llmGeminiKeySetting.setting_value" class="text-xs text-slate-400 font-mono">{{ maskedKey(llmGeminiKeySetting.setting_value) }}</span>
      </div>

      <div v-if="llmError && currentProvider === 'lm_studio'" class="text-sm text-red-600 mb-2">{{ llmError }}</div>

      <div v-if="llmSetting" class="flex items-center gap-3 mb-4">
        <label class="text-sm w-48">{{ t('settings.currentModel') }}</label>
        <select class="input max-w-md" :value="llmSetting.setting_value"
                @change="e => save('llm_model', e.target.value)">
          <option v-if="!llmModels.length" :value="llmSetting.setting_value">
            {{ llmSetting.setting_value }} {{ t('settings.modelManualFallback') }}
          </option>
          <option v-for="m in llmModels" :key="m.id" :value="m.id">{{ m.id }}</option>
        </select>
        <span class="text-xs text-slate-500">{{ describe(llmSetting) }}</span>
      </div>
      <div v-if="llmModeSetting" class="border-t border-slate-200 dark:border-slate-700 pt-4">
        <label class="text-sm font-semibold block mb-2">{{ t('settings.llmMode') }}</label>
        <div class="flex flex-col gap-2">
          <label class="flex items-start gap-2 cursor-pointer">
            <input type="radio" class="mt-1" value="rag_lite"
                   :checked="llmModeSetting.setting_value === 'rag_lite'"
                   @change="save('llm_mode', 'rag_lite')" />
            <div>
              <div class="text-sm font-medium">{{ t('settings.ragLiteTitle') }}</div>
              <div class="text-xs text-slate-500 dark:text-slate-400">{{ t('settings.ragLiteHint') }}</div>
            </div>
          </label>
          <label class="flex items-start gap-2 cursor-pointer">
            <input type="radio" class="mt-1" value="text_to_sql"
                   :checked="llmModeSetting.setting_value === 'text_to_sql'"
                   @change="save('llm_mode', 'text_to_sql')" />
            <div>
              <div class="text-sm font-medium">{{ t('settings.textToSqlTitle') }}</div>
              <div class="text-xs text-slate-500 dark:text-slate-400">{{ t('settings.textToSqlHint') }}</div>
            </div>
          </label>
        </div>
      </div>
    </section>

    <section class="card p-5 mb-5">
      <h2 class="font-semibold mb-1">{{ t('settings.section7') }}</h2>
      <p class="text-xs text-slate-500 dark:text-slate-400 mb-4">{{ t('settings.section7Hint') }}</p>
      <div class="grid md:grid-cols-2 gap-4">
        <div v-if="llmMaxTokensSetting" class="flex flex-col gap-1">
          <label class="text-sm font-medium">{{ t('settings.llmMaxTokensLabel') }}</label>
          <div class="flex items-center gap-2">
            <input class="input w-32" type="number" min="100" max="32000" step="100"
                   :value="llmMaxTokensSetting.setting_value"
                   @change="e => save('llm_max_tokens', e.target.value)" />
            <span class="text-xs text-slate-400">tokens</span>
          </div>
          <span class="text-xs text-slate-500 dark:text-slate-400">{{ t('settings.llmMaxTokensHint') }}</span>
        </div>
        <div v-if="llmContextAlertsSetting" class="flex flex-col gap-1">
          <label class="text-sm font-medium">{{ t('settings.llmContextAlertsLabel') }}</label>
          <div class="flex items-center gap-2">
            <input class="input w-32" type="number" min="1" max="500" step="1"
                   :value="llmContextAlertsSetting.setting_value"
                   @change="e => save('llm_context_alerts', e.target.value)" />
            <span class="text-xs text-slate-400">{{ t('settings.alertCount') || '건' }}</span>
          </div>
          <span class="text-xs text-slate-500 dark:text-slate-400">{{ t('settings.llmContextAlertsHint') }}</span>
        </div>
        <div v-if="llmContextCarsSetting" class="flex flex-col gap-1">
          <label class="text-sm font-medium">{{ t('settings.llmContextCarsLabel') }}</label>
          <div class="flex items-center gap-2">
            <input class="input w-32" type="number" min="1" max="500" step="1"
                   :value="llmContextCarsSetting.setting_value"
                   @change="e => save('llm_context_cars', e.target.value)" />
            <span class="text-xs text-slate-400">{{ t('settings.alertCount') || '건' }}</span>
          </div>
          <span class="text-xs text-slate-500 dark:text-slate-400">{{ t('settings.llmContextCarsHint') }}</span>
        </div>
        <div v-if="llmAlertMsgMaxSetting" class="flex flex-col gap-1">
          <label class="text-sm font-medium">{{ t('settings.llmAlertMsgMaxLabel') }}</label>
          <div class="flex items-center gap-2">
            <input class="input w-32" type="number" min="0" max="2000" step="10"
                   :value="llmAlertMsgMaxSetting.setting_value"
                   @change="e => save('llm_alert_msg_max', e.target.value)" />
            <span class="text-xs text-slate-400">chars</span>
          </div>
          <span class="text-xs text-slate-500 dark:text-slate-400">{{ t('settings.llmAlertMsgMaxHint') }}</span>
        </div>
      </div>
    </section>

    <section class="card p-5">
      <h2 class="font-semibold mb-3">{{ t('settings.section4') }}</h2>
      <div class="grid grid-cols-2 md:grid-cols-4 gap-2">
        <button v-for="c in countries" :key="c.country_id"
                class="flex items-center justify-between p-3 rounded-lg border transition"
                :class="c.is_allowed ? 'bg-hyundai-50 dark:bg-hyundai-900/20 border-hyundai-200 dark:border-hyundai-700 text-hyundai-600 dark:text-hyundai-300' : 'bg-slate-50 dark:bg-slate-800 border-slate-200 dark:border-slate-700 text-slate-400 dark:text-slate-500'"
                @click="toggleCountry(c)">
          <span class="text-sm font-medium">{{ labels.country(c.country_name) }}</span>
          <span class="text-xs">{{ c.is_allowed ? t('settings.countryAllowed') : t('settings.countryBlocked') }}</span>
        </button>
      </div>
    </section>
  </div>
</template>
