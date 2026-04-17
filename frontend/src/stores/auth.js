import { defineStore } from 'pinia';
import { api } from '../composables/api.js';

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('bms_token') || '',
    user: JSON.parse(localStorage.getItem('bms_user') || 'null'),
  }),
  getters: {
    isLoggedIn: (s) => !!s.token,
    isAdmin: (s) => s.user?.role === 'admin',
    allowedFactoryIds: (s) => s.user?.allowed_factory_ids || [],
    hasFactoryAccess: (s) => s.user?.role === 'admin' || (s.user?.allowed_factory_ids?.length > 0),
  },
  actions: {
    _persistUser(user) {
      this.user = user;
      localStorage.setItem('bms_user', JSON.stringify(user));
    },
    async login(email, password) {
      const res = await api.post('/auth/login', { email, password });
      this.token = res.token;
      this._persistUser(res.user);
      localStorage.setItem('bms_token', res.token);
    },
    async signup(email, password, name) {
      const res = await api.post('/auth/signup', { email, password, name });
      this.token = res.token;
      this._persistUser(res.user);
      localStorage.setItem('bms_token', res.token);
    },
    async refreshMe() {
      // 권한이 다른 탭에서 변경됐을 때 즉시 반영하기 위해 호출
      if (!this.token) return;
      try {
        const res = await api.get('/auth/me');
        this._persistUser(res.user);
      } catch {}
    },
    logout() {
      this.token = '';
      this.user = null;
      localStorage.removeItem('bms_token');
      localStorage.removeItem('bms_user');
    },
  },
});
