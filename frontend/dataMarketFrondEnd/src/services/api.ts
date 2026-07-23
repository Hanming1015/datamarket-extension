import axios from 'axios';

// Point straight at the API gateway (single entry for the whole cluster).
// Overridable via Vite env so prod builds can target another host.
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
});

// Attach the JWT the gateway verifies on every non-whitelisted route.
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Every service wraps its payload in the unified Result{code,message,data} envelope.
// Unwrap it here once so pages keep reading `response.data.<field>`; turn a non-success
// business code into a rejection carrying `.response.data.message` (what pages already read).
api.interceptors.response.use(
  (response) => {
    const body = response.data;
    if (body && typeof body === 'object' && 'code' in body && 'data' in body) {
      if (body.code !== 200) {
        return Promise.reject({ response: { status: body.code, data: body } });
      }
      response.data = body.data;
    }
    return response;
  },
  (error) => {
    if (error.response?.status === 401) {
      // Token expired or invalid
      localStorage.removeItem('token');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export const authApi = {
  login: (username: string, password: string) =>
    api.post('/api/auth/login', { username, password }),

  register: (username: string, password: string, name: string, email: string, organization: string, role: string) =>
    api.post('/api/auth/register', { username, password, name, email, organization, role }),

  getInfo: () => api.get('/api/auth/info'),
};

export const datasetApi = {
  // Owner's own datasets (paginated: read `.records`).
  list: () => api.get('/api/datasets/list'),
  // Marketplace: ready-to-share datasets (paginated: read `.records`).
  all: (params?: any) => api.get('/api/datasets/all', { params }),
  get: (id: string) => api.get(`/api/datasets/${id}`),
  add: (dataset: any) => api.post('/api/datasets', dataset),
  update: (dataset: any) => api.put(`/api/datasets/${dataset.id}`, dataset),
  remove: (id: string) => api.delete(`/api/datasets/${id}`),
};

export const pricingConfigApi = {
  // The new dataset-service exposes a single upsert (POST) + read (GET); there is no
  // separate update/delete, so update() also POSTs to the same endpoint.
  add: (config: any) => api.post(`/api/datasets/${config.datasetId}/pricing`, config),
  getByDataset: (datasetId: string) => api.get(`/api/datasets/${datasetId}/pricing`),
  update: (config: any) => api.post(`/api/datasets/${config.datasetId}/pricing`, config),
};

export const consentApi = {
  // owner id comes from the gateway-injected X-User-Id, not the body.
  list: (params?: any) => api.get('/api/consent/rules', { params }),
  create: (rule: any) => api.post('/api/consent/rules', rule),
  revoke: (id: string) => api.put(`/api/consent/rules/${id}/revoke`),
};

export const accessApi = {
  // Submit an access request (triggers the Feign orchestration on the backend).
  create: (payload: any) => api.post('/api/access', payload),
  // My submitted requests (paginated: read `.records`).
  mine: (params?: any) => api.get('/api/access/mine', { params }),
  // Requests awaiting my approval as a dataset owner (paginated: read `.records`).
  pending: (params?: any) => api.get('/api/access/pending', { params }),
  approve: (id: string) => api.put(`/api/access/${id}/approve`),
  reject: (id: string, reason?: string) => api.put(`/api/access/${id}/reject`, { reason }),
  get: (id: string) => api.get(`/api/access/${id}`),
};

export const billingApi = {
  // My billing records (paginated: read `.records`).
  mine: (params?: any) => api.get('/api/billing/mine', { params }),
};

export const auditApi = {
  // Audit entries related to me (paginated: read `.records`).
  mine: (params?: any) => api.get('/api/audit/mine', { params }),
};

export const notificationApi = {
  mine: (params?: any) => api.get('/api/notifications/mine', { params }),
  markRead: (id: string) => api.put(`/api/notifications/${id}/read`),
};

export default api;
