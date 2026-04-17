import { defineStore } from 'pinia';
import { api } from '../composables/api.js';
import { useAuthStore } from './auth.js';

export const useFilterStore = defineStore('filters', {
  state: () => ({
    factoryIds: JSON.parse(localStorage.getItem('bms_factory_ids') || '[]'),
    factories: [],
  }),
  getters: {
    factoryIdsQuery: (s) => (s.factoryIds.length ? s.factoryIds.join(',') : ''),
  },
  actions: {
    async loadFactories(force = false) {
      if (this.factories.length && !force) return;
      try {
        // 백엔드가 권한 있는 공장만 반환
        this.factories = (await api.get('/settings/factories')).items || [];
        // localStorage에 권한 잃은 공장 ID가 있으면 정리
        const validIds = new Set(this.factories.map(f => f.factory_id));
        const filtered = this.factoryIds.filter(id => validIds.has(id));
        if (filtered.length !== this.factoryIds.length) {
          this.factoryIds = filtered;
          localStorage.setItem('bms_factory_ids', JSON.stringify(filtered));
        }
      } catch {}
    },
    setFactoryIds(ids) {
      // 권한 있는 ID만 통과 (localStorage 직접 수정 방지)
      const auth = useAuthStore();
      const allowSet = new Set(auth.allowedFactoryIds || []);
      const safe = (ids || []).filter(id => auth.isAdmin || allowSet.has(id));
      this.factoryIds = [...safe];
      localStorage.setItem('bms_factory_ids', JSON.stringify(this.factoryIds));
    },
    toggleFactory(id) {
      const next = this.factoryIds.includes(id)
        ? this.factoryIds.filter(i => i !== id)
        : [...this.factoryIds, id];
      this.setFactoryIds(next);
    },
    clearFactories() {
      this.setFactoryIds([]);
    },
  },
});
