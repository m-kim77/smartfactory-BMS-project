import { defineStore } from 'pinia';
import { api } from '../composables/api.js';

export const useAlertsStore = defineStore('alerts', {
  state: () => ({
    unresolvedCount: 0,
    timer: null,
  }),
  getters: {
    badgeText: (s) => (s.unresolvedCount > 99 ? '99+' : String(s.unresolvedCount)),
    hasUnresolved: (s) => s.unresolvedCount > 0,
  },
  actions: {
    async fetchCount() {
      try {
        const res = await api.get('/alerts/unresolved-count');
        this.unresolvedCount = res.count || 0;
      } catch {}
    },
    startPolling(intervalMs = 5000) {
      if (this.timer) return;
      this.fetchCount();
      this.timer = setInterval(() => this.fetchCount(), intervalMs);
    },
    stopPolling() {
      if (this.timer) { clearInterval(this.timer); this.timer = null; }
    },
  },
});
