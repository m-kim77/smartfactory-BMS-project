<script setup>
import { ref, computed } from 'vue';
import { useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { useAuthStore } from '../stores/auth.js';
import loginImage from '../assets/login_image.png';

const { t } = useI18n();
const email = ref('');
const password = ref('');
const passwordConfirm = ref('');
const name = ref('');
const showPassword = ref(false);
const showPasswordConfirm = ref(false);
const loading = ref(false);
const err = ref('');
const auth = useAuthStore();
const router = useRouter();

const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const emailError = computed(() => {
  if (!email.value) return '';
  return emailRegex.test(email.value) ? '' : t('signup.emailInvalid');
});
const passwordError = computed(() => {
  if (!password.value) return '';
  return password.value.length <= 6 ? t('signup.passwordTooShort') : '';
});
const passwordConfirmError = computed(() => {
  if (!passwordConfirm.value) return '';
  return password.value === passwordConfirm.value ? '' : t('signup.passwordMismatch');
});
const canSubmit = computed(() =>
  !!name.value && !!email.value && !!password.value && !!passwordConfirm.value
  && !emailError.value && !passwordError.value && !passwordConfirmError.value
);

async function submit() {
  err.value = '';
  if (!canSubmit.value) return;
  loading.value = true;
  try {
    await auth.signup(email.value, password.value, name.value);
    router.push('/');
  } catch (e) {
    err.value = e.message;
  } finally { loading.value = false; }
}
</script>

<template>
  <div class="min-h-screen flex bg-white dark:bg-slate-900">
    <!-- 좌측 이미지 -->
    <div class="hidden md:block md:w-1/2 lg:w-3/5 relative">
      <img :src="loginImage" alt="Smart Factory" class="absolute inset-0 w-full h-full object-cover" />
      <div class="absolute top-6 left-6 z-10">
        <span class="text-2xl font-black tracking-tight text-white drop-shadow-lg">Ever<span class="text-blue-300">Nex</span></span>
      </div>
      <div class="absolute inset-0 bg-gradient-to-tr from-slate-900/40 to-transparent"></div>
      <div class="absolute bottom-10 left-10 right-10 text-white">
        <div class="text-xs uppercase tracking-widest font-semibold opacity-90">EverNex</div>
        <h2 class="text-3xl font-bold mt-2">{{ t('app.title') }}</h2>
        <p class="text-sm opacity-90 mt-1">{{ t('app.subtitle') }}</p>
      </div>
    </div>

    <!-- 우측 회원가입 폼 -->
    <div class="w-full md:w-1/2 lg:w-2/5 flex items-center justify-center p-6 md:p-10">
      <div class="w-full max-w-sm">
        <div class="md:hidden mb-6 text-center">
          <span class="text-2xl font-black tracking-tight text-hyundai-500">Ever<span class="text-slate-700 dark:text-slate-200">Nex</span></span>
        </div>
        <h1 class="text-2xl font-bold mb-1">{{ t('signup.title') }}</h1>
        <p class="text-sm text-slate-500 dark:text-slate-400 mb-6">{{ t('signup.subtitle') }}</p>

        <form @submit.prevent="submit" class="space-y-4">
          <!-- 이름 -->
          <div>
            <label class="text-sm font-medium">{{ t('signup.name') }}</label>
            <div class="relative mt-1">
              <span class="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none">
                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.501 20.118a7.5 7.5 0 0114.998 0A17.933 17.933 0 0112 21.75c-2.676 0-5.216-.584-7.499-1.632z" />
                </svg>
              </span>
              <input v-model="name" type="text" autocomplete="name" class="input !pl-10" required maxlength="50" />
            </div>
          </div>

          <!-- 이메일 -->
          <div>
            <label class="text-sm font-medium">{{ t('signup.email') }}</label>
            <div class="relative mt-1">
              <span class="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none">
                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M21.75 6.75v10.5a2.25 2.25 0 01-2.25 2.25h-15a2.25 2.25 0 01-2.25-2.25V6.75m19.5 0A2.25 2.25 0 0019.5 4.5h-15a2.25 2.25 0 00-2.25 2.25m19.5 0v.243a2.25 2.25 0 01-1.07 1.916l-7.5 4.615a2.25 2.25 0 01-2.36 0L3.32 8.91a2.25 2.25 0 01-1.07-1.916V6.75" />
                </svg>
              </span>
              <input
                v-model="email"
                type="email"
                autocomplete="username"
                class="input !pl-10"
                :class="{ 'border-red-500 focus:border-red-500 focus:ring-red-200': emailError }"
                required
              />
            </div>
            <p v-if="emailError" class="text-xs text-red-600 dark:text-red-400 mt-1">{{ emailError }}</p>
          </div>

          <!-- 비밀번호 -->
          <div>
            <label class="text-sm font-medium">{{ t('signup.password') }}</label>
            <div class="relative mt-1">
              <span class="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none">
                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M16.5 10.5V6.75a4.5 4.5 0 10-9 0v3.75m-.75 11.25h10.5a2.25 2.25 0 002.25-2.25v-6.75a2.25 2.25 0 00-2.25-2.25H6.75a2.25 2.25 0 00-2.25 2.25v6.75a2.25 2.25 0 002.25 2.25z" />
                </svg>
              </span>
              <input
                v-model="password"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="new-password"
                class="input !pl-10 !pr-10"
                :class="{ 'border-red-500 focus:border-red-500 focus:ring-red-200': passwordError }"
                required
              />
              <button
                type="button"
                @click="showPassword = !showPassword"
                class="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200"
                :aria-label="showPassword ? t('signup.hidePassword') : t('signup.showPassword')"
              >
                <svg v-if="showPassword" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M3.98 8.223A10.477 10.477 0 001.934 12C3.226 16.338 7.244 19.5 12 19.5c.993 0 1.953-.138 2.863-.395M6.228 6.228A10.45 10.45 0 0112 4.5c4.756 0 8.773 3.162 10.065 7.498a10.523 10.523 0 01-4.293 5.774M6.228 6.228L3 3m3.228 3.228l3.65 3.65m7.244 7.244L21 21m-3.228-3.228l-3.65-3.65m0 0a3 3 0 10-4.243-4.243m4.242 4.242L9.88 9.88" />
                </svg>
                <svg v-else xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178z" />
                  <path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                </svg>
              </button>
            </div>
            <p v-if="passwordError" class="text-xs text-red-600 dark:text-red-400 mt-1">{{ passwordError }}</p>
            <p v-else class="text-xs text-slate-500 dark:text-slate-400 mt-1">{{ t('signup.passwordHint') }}</p>
          </div>

          <!-- 비밀번호 확인 -->
          <div>
            <label class="text-sm font-medium">{{ t('signup.passwordConfirm') }}</label>
            <div class="relative mt-1">
              <span class="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none">
                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M16.5 10.5V6.75a4.5 4.5 0 10-9 0v3.75m-.75 11.25h10.5a2.25 2.25 0 002.25-2.25v-6.75a2.25 2.25 0 00-2.25-2.25H6.75a2.25 2.25 0 00-2.25 2.25v6.75a2.25 2.25 0 002.25 2.25z" />
                </svg>
              </span>
              <input
                v-model="passwordConfirm"
                :type="showPasswordConfirm ? 'text' : 'password'"
                autocomplete="new-password"
                class="input !pl-10 !pr-10"
                :class="{ 'border-red-500 focus:border-red-500 focus:ring-red-200': passwordConfirmError }"
                required
              />
              <button
                type="button"
                @click="showPasswordConfirm = !showPasswordConfirm"
                class="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200"
                :aria-label="showPasswordConfirm ? t('signup.hidePassword') : t('signup.showPassword')"
              >
                <svg v-if="showPasswordConfirm" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M3.98 8.223A10.477 10.477 0 001.934 12C3.226 16.338 7.244 19.5 12 19.5c.993 0 1.953-.138 2.863-.395M6.228 6.228A10.45 10.45 0 0112 4.5c4.756 0 8.773 3.162 10.065 7.498a10.523 10.523 0 01-4.293 5.774M6.228 6.228L3 3m3.228 3.228l3.65 3.65m7.244 7.244L21 21m-3.228-3.228l-3.65-3.65m0 0a3 3 0 10-4.243-4.243m4.242 4.242L9.88 9.88" />
                </svg>
                <svg v-else xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178z" />
                  <path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                </svg>
              </button>
            </div>
            <p v-if="passwordConfirmError" class="text-xs text-red-600 dark:text-red-400 mt-1">{{ passwordConfirmError }}</p>
            <p v-else-if="passwordConfirm && password === passwordConfirm" class="text-xs text-emerald-600 dark:text-emerald-400 mt-1">{{ t('signup.passwordMatch') }}</p>
          </div>

          <div v-if="err" class="text-sm text-red-600 dark:text-red-400">{{ err }}</div>
          <button class="btn-primary w-full" :disabled="loading || !canSubmit">
            {{ loading ? t('signup.submitting') : t('signup.submit') }}
          </button>
        </form>

        <div class="mt-6 pt-4 border-t border-slate-200 dark:border-slate-700 text-xs text-slate-500 dark:text-slate-400 text-center">
          {{ t('signup.hasAccount') }}
          <router-link to="/login" class="text-hyundai-500 dark:text-hyundai-300 font-semibold ml-1 hover:underline">
            {{ t('signup.loginLink') }}
          </router-link>
        </div>
      </div>
    </div>
  </div>
</template>
