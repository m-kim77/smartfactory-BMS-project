const BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:3000/api/v1';

function token() { return localStorage.getItem('bms_token'); }

async function req(method, path, body) {
  const headers = { 'Content-Type': 'application/json' };
  const t = token();
  if (t) headers.Authorization = `Bearer ${t}`;
  const r = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = await r.json().catch(() => ({}));
  if (!r.ok) {
    if (r.status === 401 && !path.startsWith('/auth/')) {
      localStorage.removeItem('bms_token');
      localStorage.removeItem('bms_user');
      if (!location.pathname.endsWith('/login')) location.href = '/login';
    }
    const err = new Error(data.error || `요청 실패 (${r.status})`);
    err.detail = data.detail;
    err.status = r.status;
    throw err;
  }
  return data;
}

export const api = {
  get: (p) => req('GET', p),
  post: (p, b) => req('POST', p, b),
  put: (p, b) => req('PUT', p, b),
  del: (p) => req('DELETE', p),
  base: BASE,
};

export function qs(params) {
  const entries = Object.entries(params || {}).filter(([, v]) => v !== '' && v !== null && v !== undefined);
  return entries.length ? `?${new URLSearchParams(entries).toString()}` : '';
}
