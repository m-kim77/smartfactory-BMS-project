<script setup>
import { ref, computed, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import { api } from '../composables/api.js';
import { useAuthStore } from '../stores/auth.js';
import { useLabels } from '../composables/labels.js';

const { t } = useI18n();
const labels = useLabels();
const auth = useAuthStore();
const items = ref([]);
const allFactories = ref([]);
const loading = ref(false);
const err = ref('');
const search = ref('');

// 공장 권한 편집 모달 상태
const editingUser = ref(null);
const editingFactoryIds = ref([]);
const savingFactories = ref(false);

async function load() {
  loading.value = true;
  err.value = '';
  try {
    const [usersRes, facRes] = await Promise.all([
      api.get('/users'),
      api.get('/settings/factories/all'),
    ]);
    items.value = usersRes.items || [];
    allFactories.value = facRes.items || [];
  } catch (e) {
    err.value = e.message;
  } finally {
    loading.value = false;
  }
}

const filtered = computed(() => {
  const q = search.value.trim().toLowerCase();
  if (!q) return items.value;
  return items.value.filter(u =>
    u.name.toLowerCase().includes(q) || u.email.toLowerCase().includes(q)
  );
});

async function changeRole(user, newRole) {
  const msgKey = newRole === 'admin' ? 'users.confirmPromote' : 'users.confirmDemote';
  if (!confirm(t(msgKey, { name: labels.userName(user.name) }))) return;
  try {
    await api.put(`/users/${user.user_id}/role`, { role: newRole });
    await load();
  } catch (e) {
    alert(e.message);
  }
}

async function removeUser(user) {
  if (!confirm(t('users.confirmDelete', { name: labels.userName(user.name) }))) return;
  try {
    await api.del(`/users/${user.user_id}`);
    await load();
  } catch (e) {
    alert(e.message);
  }
}

async function openFactoryEditor(user) {
  editingUser.value = user;
  editingFactoryIds.value = [];
  try {
    const res = await api.get(`/users/${user.user_id}/factories`);
    editingFactoryIds.value = [...(res.factory_ids || [])];
  } catch (e) {
    alert(e.message);
    editingUser.value = null;
  }
}

function toggleFactoryInEditor(fid) {
  const idx = editingFactoryIds.value.indexOf(fid);
  if (idx >= 0) editingFactoryIds.value.splice(idx, 1);
  else editingFactoryIds.value.push(fid);
}

function selectAllFactories() {
  editingFactoryIds.value = allFactories.value.map(f => f.factory_id);
}
function clearAllFactoriesEditor() {
  editingFactoryIds.value = [];
}

async function saveFactoryAccess() {
  if (!editingUser.value) return;
  savingFactories.value = true;
  const editedUserId = editingUser.value.user_id;
  try {
    await api.put(`/users/${editedUserId}/factories`, {
      factory_ids: editingFactoryIds.value,
    });
    editingUser.value = null;
    await load();
    // 본인 권한 변경 시 즉시 반영 (admin이 admin을 편집할 일은 없지만 안전망)
    if (editedUserId === auth.user?.user_id) {
      await auth.refreshMe();
    }
  } catch (e) {
    alert(e.message);
  } finally {
    savingFactories.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-4 flex-wrap gap-3">
      <div>
        <h1 class="text-2xl font-bold">{{ t('users.title') }}</h1>
        <p class="text-sm text-slate-500 dark:text-slate-400">{{ t('users.subtitle') }}</p>
      </div>
      <div class="text-sm text-slate-500 dark:text-slate-400">
        {{ t('users.resultCount', { n: filtered.length }) }}
      </div>
    </div>

    <div class="card p-4 mb-4">
      <input v-model="search" :placeholder="t('users.searchPlaceholder')" class="input max-w-md" />
    </div>

    <div v-if="err" class="card p-4 mb-4 text-red-600 dark:text-red-400">{{ err }}</div>

    <div class="card overflow-hidden">
      <table class="w-full text-sm">
        <thead class="text-left text-slate-500 dark:text-slate-400 border-b border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800/40">
          <tr>
            <th class="px-4 py-3">{{ t('users.name') }}</th>
            <th class="px-4 py-3">{{ t('users.email') }}</th>
            <th class="px-4 py-3">{{ t('users.role') }}</th>
            <th class="px-4 py-3">{{ t('users.factoryAccess') }}</th>
            <th class="px-4 py-3">{{ t('users.joinedAt') }}</th>
            <th class="px-4 py-3 text-right">{{ t('users.actions') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="6" class="px-4 py-8 text-center text-slate-400">{{ t('common.loading') }}</td>
          </tr>
          <tr v-else-if="!filtered.length">
            <td colspan="6" class="px-4 py-8 text-center text-slate-400">{{ t('users.noUsers') }}</td>
          </tr>
          <tr v-for="u in filtered" :key="u.user_id" class="border-b border-slate-100 dark:border-slate-700 last:border-0">
            <td class="px-4 py-3 font-medium">{{ labels.userName(u.name) }}</td>
            <td class="px-4 py-3 text-slate-600 dark:text-slate-300">{{ u.email }}</td>
            <td class="px-4 py-3">
              <span :class="u.role === 'admin' ? 'badge-blue' : 'badge-gray'">
                {{ u.role === 'admin' ? t('common.admin') : t('common.operator') }}
              </span>
            </td>
            <td class="px-4 py-3">
              <span v-if="u.role === 'admin'" class="text-xs text-slate-500 italic">
                {{ t('users.factoryAccessAll') }}
              </span>
              <button v-else
                      class="text-xs px-2 py-1 rounded border transition"
                      :class="u.factory_count === 0
                        ? 'border-amber-300 bg-amber-50 text-amber-700 hover:bg-amber-100'
                        : 'border-slate-300 bg-white text-slate-700 hover:border-hyundai-400'"
                      @click="openFactoryEditor(u)">
                {{ u.factory_count === 0
                   ? t('users.factoryAccessEmpty')
                   : t('users.factoryAccessCount', { n: u.factory_count, total: allFactories.length }) }}
              </button>
            </td>
            <td class="px-4 py-3 text-xs text-slate-500 dark:text-slate-400">{{ u.created_at }}</td>
            <td class="px-4 py-3">
              <div class="flex justify-end gap-2">
                <span v-if="u.user_id === auth.user?.user_id"
                      class="text-xs text-slate-400 italic px-2 py-1">{{ t('users.self') }}</span>
                <template v-else>
                  <button v-if="u.role === 'operator'"
                          class="btn-primary text-xs px-3 py-1.5"
                          @click="changeRole(u, 'admin')">{{ t('users.promote') }}</button>
                  <button v-else
                          class="btn-secondary text-xs px-3 py-1.5"
                          @click="changeRole(u, 'operator')">{{ t('users.demote') }}</button>
                  <button class="btn-danger text-xs px-3 py-1.5"
                          @click="removeUser(u)">{{ t('users.delete') }}</button>
                </template>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 공장 권한 편집 모달 -->
    <div v-if="editingUser"
         class="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4"
         @click.self="editingUser = null">
      <div class="bg-white dark:bg-slate-800 rounded-lg shadow-xl max-w-md w-full p-6">
        <h2 class="text-lg font-bold mb-1">{{ t('users.editFactoryAccess') }}</h2>
        <p class="text-sm text-slate-500 mb-4">{{ labels.userName(editingUser.name) }} ({{ editingUser.email }})</p>

        <div class="flex gap-2 mb-3">
          <button class="text-xs px-3 py-1 rounded border border-slate-300 hover:bg-slate-50"
                  @click="selectAllFactories">{{ t('users.selectAll') }}</button>
          <button class="text-xs px-3 py-1 rounded border border-slate-300 hover:bg-slate-50"
                  @click="clearAllFactoriesEditor">{{ t('users.clearAll') }}</button>
        </div>

        <div class="space-y-2 max-h-72 overflow-y-auto mb-4">
          <label v-for="f in allFactories" :key="f.factory_id"
                 class="flex items-center gap-3 px-3 py-2 rounded border cursor-pointer transition"
                 :class="editingFactoryIds.includes(f.factory_id)
                   ? 'border-hyundai-400 bg-hyundai-50 dark:bg-hyundai-900/20'
                   : 'border-slate-200 hover:border-slate-300'">
            <input type="checkbox"
                   :checked="editingFactoryIds.includes(f.factory_id)"
                   @change="toggleFactoryInEditor(f.factory_id)" />
            <div class="flex-1">
              <div class="text-sm font-medium">{{ labels.factory(f.factory_name) }}</div>
              <div class="text-xs text-slate-500">{{ labels.brand(f.brand) }} · {{ labels.region(f.region) }}</div>
            </div>
          </label>
        </div>

        <div class="flex justify-end gap-2">
          <button class="btn-secondary text-sm px-4 py-2"
                  :disabled="savingFactories"
                  @click="editingUser = null">{{ t('common.cancel') }}</button>
          <button class="btn-primary text-sm px-4 py-2"
                  :disabled="savingFactories"
                  @click="saveFactoryAccess">
            {{ savingFactories ? t('users.saving') : t('users.saveFactoryAccess') }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
