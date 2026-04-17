import { createI18n } from 'vue-i18n';
import ko from './locales/ko.js';
import en from './locales/en.js';

const saved = localStorage.getItem('bms_locale') || 'ko';

export const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: saved,
  fallbackLocale: 'ko',
  messages: { ko, en },
});

export function setI18nLocale(locale) {
  i18n.global.locale.value = locale;
  localStorage.setItem('bms_locale', locale);
}
