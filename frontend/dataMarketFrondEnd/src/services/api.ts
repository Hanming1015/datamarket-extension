import axios from 'axios';

const API_BASE_URL = 'http://localhost:8080';

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
});

// Add request interceptor to attach token
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Add response interceptor to handle token expiry
api.interceptors.response.use(
  (response) => response,
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
    api.post('/api/user/account/login', { username, password }),

  register: (username: string, password: string, name: string, email: string, organization: string, role: string) =>
    api.post('/api/user/account/register', { username, password, name, email, organization, role }),

  getInfo: () => api.get('/api/user/account/info'),
};

export const datasetApi = {
  list: () => api.get('/api/datasets/list'),
  add: (dataset: any) => api.post('/api/datasets/add', dataset),
  update: (dataset: any) => api.put('/api/datasets/update', dataset),
  remove: (id: string) => api.delete(`/api/datasets/remove/${id}`),
};

export const pricingConfigApi = {
  add: (config: any) => api.post('/user/dataset/pricingconfig/add', config),
  getByDataset: (datasetId: string) => api.get(`/user/dataset/pricingconfig/get/${datasetId}`),
  update: (config: any) => api.put('/user/dataset/pricingconfig/put', config),
  remove: (id: string) => api.delete(`/user/dataset/pricingconfig/delete/${id}`),
};

export default api;
