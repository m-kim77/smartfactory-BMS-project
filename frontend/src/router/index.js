import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '../stores/auth.js';

const routes = [
  { path: '/login', component: () => import('../views/Login.vue'), meta: { public: true } },
  { path: '/signup', component: () => import('../views/Signup.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('../layouts/AppLayout.vue'),
    children: [
      { path: '', component: () => import('../views/Dashboard.vue') },
      { path: 'vehicles', component: () => import('../views/VehicleList.vue') },
      { path: 'vehicles/:carId', component: () => import('../views/VehicleDetail.vue'), props: true },
      { path: 'alerts', component: () => import('../views/Alerts.vue') },
      { path: 'reports', component: () => import('../views/Reports.vue') },
      { path: 'users', component: () => import('../views/Users.vue'), meta: { adminOnly: true } },
      { path: 'settings', component: () => import('../views/Settings.vue'), meta: { adminOnly: true } },
    ],
  },
];

const router = createRouter({ history: createWebHistory(), routes });

router.beforeEach((to) => {
  const auth = useAuthStore();
  if (!to.meta.public && !auth.isLoggedIn) return { path: '/login' };
  if (to.meta.adminOnly && !auth.isAdmin) return { path: '/' };
  if ((to.path === '/login' || to.path === '/signup') && auth.isLoggedIn) return { path: '/' };
});

export default router;
