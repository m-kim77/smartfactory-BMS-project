<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue';
import { RouterLink, RouterView, useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { useAuthStore } from '../stores/auth.js';
import { useAlertsStore } from '../stores/alerts.js';
import { useThemeStore } from '../stores/theme.js';
import { setI18nLocale } from '../i18n/index.js';
import { useLabels } from '../composables/labels.js';
import Chatbot from '../components/Chatbot.vue';

const auth = useAuthStore();
const alerts = useAlertsStore();
const theme = useThemeStore();
const router = useRouter();
const { t, locale } = useI18n();
const labels = useLabels();

const collapsed = ref(localStorage.getItem('bms_sidebar_collapsed') === '1');
function toggleSidebar() {
  collapsed.value = !collapsed.value;
  localStorage.setItem('bms_sidebar_collapsed', collapsed.value ? '1' : '0');
}

function logout() {
  auth.logout();
  router.push('/login');
}

function switchLocale(l) {
  setI18nLocale(l);
  theme.setLocale(l);
  locale.value = l;
}

onMounted(() => { locale.value = theme.locale; alerts.startPolling(5000); });
onBeforeUnmount(() => alerts.stopPolling());
</script>

<template>
  <div class="h-screen flex overflow-hidden">
    <aside class="bg-hyundai-500 dark:bg-slate-900 text-white flex flex-col transition-all duration-200 h-screen shrink-0"
           :class="collapsed ? 'w-16' : 'w-60'">
      <!-- 펼침 헤더 -->
      <div v-if="!collapsed" class="border-b border-hyundai-600 dark:border-slate-700 px-4 py-4">
        <div class="flex items-center justify-between gap-2">
          <span class="text-lg font-black tracking-tight text-white">Ever<span class="text-hyundai-200">Nex</span></span>
          <button class="p-1.5 rounded hover:bg-hyundai-600 dark:hover:bg-slate-700 text-hyundai-200 hover:text-white transition shrink-0"
                  :title="t('common.collapseSidebar')" @click="toggleSidebar">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24"
                 fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="15 18 9 12 15 6" />
            </svg>
          </button>
        </div>
        <div class="text-xs font-semibold text-white mt-3">{{ t('app.title') }}</div>
        <div class="text-xs text-hyundai-200">{{ t('app.subtitle') }}</div>
      </div>

      <!-- 접힘 헤더 -->
      <div v-else class="border-b border-hyundai-600 dark:border-slate-700 px-2 py-3 flex flex-col items-center gap-2">
        <span class="text-xs font-black tracking-tight text-white">EN</span>
        <button class="p-1.5 rounded hover:bg-hyundai-600 dark:hover:bg-slate-700 text-hyundai-200 hover:text-white transition"
                :title="t('common.expandSidebar')" @click="toggleSidebar">
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24"
               fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="9 18 15 12 9 6" />
          </svg>
        </button>
      </div>

      <nav class="flex-1 py-3 space-y-0.5">
        <RouterLink to="/" class="nav-link" :class="collapsed ? 'justify-center px-0' : ''" :title="collapsed ? t('nav.dashboard') : ''">
          <span v-if="!collapsed">{{ t('nav.dashboard') }}</span>
          <span v-else class="text-xs font-semibold">{{ t('nav.collapsed.dashboard') }}</span>
        </RouterLink>
        <RouterLink to="/vehicles" class="nav-link" :class="collapsed ? 'justify-center px-0' : ''" :title="collapsed ? t('nav.vehicles') : ''">
          <span v-if="!collapsed">{{ t('nav.vehicles') }}</span>
          <span v-else class="text-xs font-semibold">{{ t('nav.collapsed.vehicles') }}</span>
        </RouterLink>
        <RouterLink to="/alerts" class="nav-link relative" :class="collapsed ? 'justify-center px-0' : ''" :title="collapsed ? t('nav.alerts') : ''">
          <span v-if="!collapsed" class="flex-1">{{ t('nav.alerts') }}</span>
          <span v-else class="text-xs font-semibold">{{ t('nav.collapsed.alerts') }}</span>
          <span v-if="alerts.hasUnresolved"
                class="inline-flex items-center justify-center min-w-[20px] h-5 px-1.5 rounded-full bg-red-500 text-white text-[11px] font-bold leading-none"
                :class="collapsed ? 'absolute top-1 right-1 min-w-[18px] h-[18px] px-1 text-[10px]' : ''">
            {{ alerts.badgeText }}
          </span>
        </RouterLink>
        <RouterLink to="/reports" class="nav-link" :class="collapsed ? 'justify-center px-0' : ''" :title="collapsed ? t('nav.reports') : ''">
          <span v-if="!collapsed">{{ t('nav.reports') }}</span>
          <span v-else class="text-xs font-semibold">{{ t('nav.collapsed.reports') }}</span>
        </RouterLink>
        <RouterLink v-if="auth.isAdmin" to="/users" class="nav-link" :class="collapsed ? 'justify-center px-0' : ''" :title="collapsed ? t('nav.users') : ''">
          <span v-if="!collapsed">{{ t('nav.users') }}</span>
          <span v-else class="text-xs font-semibold">{{ t('nav.collapsed.users') }}</span>
        </RouterLink>
        <RouterLink v-if="auth.isAdmin" to="/settings" class="nav-link" :class="collapsed ? 'justify-center px-0' : ''" :title="collapsed ? t('nav.settings') : ''">
          <span v-if="!collapsed">{{ t('nav.settings') }}</span>
          <span v-else class="text-xs font-semibold">{{ t('nav.collapsed.settings') }}</span>
        </RouterLink>
      </nav>

      <!-- 테마 & 언어 설정 -->
      <div class="border-t border-hyundai-600 dark:border-slate-700" :class="collapsed ? 'p-2' : 'px-4 py-3'">
        <template v-if="!collapsed">
          <div class="flex items-center justify-between text-xs text-hyundai-100 mb-2">
            <span>{{ theme.dark ? t('common.darkMode') : t('common.lightMode') }}</span>
            <button class="relative w-10 h-5 rounded-full bg-hyundai-700 dark:bg-slate-600 transition"
                    @click="theme.toggleDark()">
              <span class="absolute top-0.5 left-0.5 w-4 h-4 rounded-full bg-white transition-transform"
                    :class="theme.dark ? 'translate-x-5' : ''"></span>
            </button>
          </div>
          <div class="flex items-center gap-1">
            <button class="flex-1 py-1 rounded text-[11px] font-semibold transition"
                    :class="theme.locale === 'ko' ? 'bg-white text-hyundai-700' : 'bg-hyundai-700 dark:bg-slate-700 text-hyundai-100 hover:bg-hyundai-600'"
                    @click="switchLocale('ko')">한국어</button>
            <button class="flex-1 py-1 rounded text-[11px] font-semibold transition"
                    :class="theme.locale === 'en' ? 'bg-white text-hyundai-700' : 'bg-hyundai-700 dark:bg-slate-700 text-hyundai-100 hover:bg-hyundai-600'"
                    @click="switchLocale('en')">EN</button>
          </div>
        </template>
        <template v-else>
          <div class="flex flex-col items-center gap-2">
            <button class="p-1.5 rounded hover:bg-hyundai-600 dark:hover:bg-slate-700 text-hyundai-100 hover:text-white transition"
                    :title="theme.dark ? t('common.lightMode') : t('common.darkMode')"
                    @click="theme.toggleDark()">
              <svg v-if="theme.dark" xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="4" /><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41" />
              </svg>
              <svg v-else xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
              </svg>
            </button>
            <button class="px-1.5 py-1 rounded text-[10px] font-bold bg-hyundai-700 dark:bg-slate-700 text-hyundai-100 hover:bg-hyundai-600 transition"
                    @click="switchLocale(theme.locale === 'ko' ? 'en' : 'ko')">
              {{ theme.locale === 'ko' ? 'KO' : 'EN' }}
            </button>
          </div>
        </template>
      </div>

      <!-- 사용자 & 로그아웃 -->
      <div class="border-t border-hyundai-600 dark:border-slate-700 text-sm" :class="collapsed ? 'p-2' : 'p-4'">
        <div v-if="!collapsed">
          <div class="font-medium truncate">{{ labels.userName(auth.user?.name) }}</div>
          <div class="text-xs text-hyundai-200 mb-2">{{ auth.user?.role === 'admin' ? t('common.admin') : t('common.operator') }}</div>
          <button class="w-full btn bg-hyundai-600 dark:bg-slate-700 text-white hover:bg-hyundai-700 dark:hover:bg-slate-600 text-sm" @click="logout">{{ t('common.logout') }}</button>
        </div>
        <button v-else class="w-full py-2 rounded bg-hyundai-600 dark:bg-slate-700 text-white hover:bg-hyundai-700 dark:hover:bg-slate-600 text-xs font-medium"
                :title="`${labels.userName(auth.user?.name)} · ${t('common.logout')}`" @click="logout">
          OUT
        </button>
      </div>
    </aside>
    <main class="flex-1 p-6 overflow-auto h-screen bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-100 transition-colors">
      <RouterView />
    </main>
    <Chatbot />
  </div>
</template>

<style scoped>
.nav-link {
  @apply flex items-center gap-3 px-5 py-3 text-sm text-hyundai-100 hover:bg-hyundai-600 hover:text-white transition;
}
:global(html.dark) .nav-link {
  @apply hover:bg-slate-700;
}
.nav-link.router-link-exact-active {
  @apply bg-hyundai-700 text-white border-l-4 border-white;
}
:global(html.dark) .nav-link.router-link-exact-active {
  @apply bg-slate-700;
}
.logo-white {
  filter: brightness(0) invert(1);
}
</style>
