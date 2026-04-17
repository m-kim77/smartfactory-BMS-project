import { defineStore } from 'pinia';

export const useThemeStore = defineStore('theme', {
  state: () => ({
    dark: localStorage.getItem('bms_theme') === 'dark',
    locale: localStorage.getItem('bms_locale') || 'ko',
  }),
  actions: {
    applyTheme() {
      const root = document.documentElement;
      if (this.dark) root.classList.add('dark');
      else root.classList.remove('dark');
    },
    toggleDark() {
      this.dark = !this.dark;
      localStorage.setItem('bms_theme', this.dark ? 'dark' : 'light');
      this.applyTheme();
    },
    setLocale(locale) {
      this.locale = locale;
      localStorage.setItem('bms_locale', locale);
    },
    init() {
      this.applyTheme();
    },
  },
});
