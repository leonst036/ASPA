import type { 
  ServerMetricsRecord, 
  PerformanceAnomaly, 
  RetentionReport, 
  PlayerProfile, 
  ForecastResult, 
  SystemStatus,
  PlayerSessionRecord,
  PterodactylStatus,
  PterodactylBackup,
  PterodactylResources,
  PlayerInventorySnapshot
} from '../types';

// Helper to check if we are in development mode (Vite dev server running on 5173)
const isDev = window.location.port === '5173';

// Base URL detection: If in Vite dev server, proxy/connect to Javalin on 8080. Otherwise, use relative path.
export const getBaseUrl = (): string => {
  const savedUrl = localStorage.getItem('aspa_api_url');
  if (savedUrl) return savedUrl;
  if (isDev) {
    return 'http://localhost:8080';
  }
  return ''; // Relative to served site
};

export const getApiToken = (): string => {
  return localStorage.getItem('aspa_api_token') || '';
};

export const setApiToken = (token: string) => {
  localStorage.setItem('aspa_api_token', token);
};

export const setApiUrl = (url: string) => {
  localStorage.setItem('aspa_api_url', url);
};

export const clearAuth = () => {
  localStorage.removeItem('aspa_api_token');
};

// Fetch wrapper with authentication headers
export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
    this.name = 'ApiError';
  }
}

// Fetch wrapper with authentication headers
async function apiFetch<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const baseUrl = getBaseUrl();
  const token = getApiToken();
  const url = `${baseUrl}${endpoint}`;

  const headers = new Headers(options.headers || {});
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
    headers.set('X-API-Token', token); // Support both styles
  }
  headers.set('Content-Type', 'application/json');

  const response = await fetch(url, {
    ...options,
    headers,
  });

  if (response.status === 401 || response.status === 403) {
    throw new ApiError(response.status, 'Unauthorized');
  }

  if (!response.ok) {
    throw new ApiError(response.status, `API Error: ${response.statusText}`);
  }

  if (response.status === 204) {
    return null as unknown as T;
  }

  const text = await response.text();
  return text ? JSON.parse(text) as T : (null as unknown as T);
}

// -------------------------------------------------------------
// Real API Endpoint Implementations
// -------------------------------------------------------------

export const getSystemStatus = async (): Promise<SystemStatus> => {
  return await apiFetch<SystemStatus>('/api/v1/status');
};

export const getServerMetrics = async (minutes = 10): Promise<ServerMetricsRecord[]> => {
  const start = Date.now() - minutes * 60 * 1000;
  const res = await apiFetch<{ history: ServerMetricsRecord[] }>(`/api/v1/metrics/history?start=${start}`);
  return res.history;
};

export const getPerformanceAnomalies = async (): Promise<PerformanceAnomaly[]> => {
  const res = await apiFetch<{ anomalies: PerformanceAnomaly[] }>('/api/v1/analysis/report');
  return res.anomalies;
};

export const getPlayerAnalytics = async (): Promise<RetentionReport> => {
  return await apiFetch<RetentionReport>('/api/v1/players/overview');
};

export const getForecast = async (): Promise<ForecastResult> => {
  const res = await apiFetch<{ forecast: ForecastResult }>('/api/v1/analysis/report');
  return res.forecast;
};

export const searchPlayers = async (query: string): Promise<{ uuid: string; username: string }[]> => {
  return await apiFetch<{ uuid: string; username: string }[]>(`/api/v1/players/search?q=${encodeURIComponent(query)}`);
};

export const getPlayerProfile = async (uuid: string): Promise<PlayerProfile | null> => {
  try {
    return await apiFetch<PlayerProfile>(`/api/v1/players/inspect?uuid=${encodeURIComponent(uuid)}`);
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) {
      return null;
    }
    throw err;
  }
};

export const getPlayerInventory = async (uuid: string): Promise<PlayerInventorySnapshot> => {
  return await apiFetch<PlayerInventorySnapshot>(`/api/v1/players/invsee?uuid=${encodeURIComponent(uuid)}`);
};

// Pterodactyl Client Proxy endpoints
export const getPterodactylStatus = async (): Promise<PterodactylStatus> => {
  return await apiFetch<PterodactylStatus>('/api/v1/pterodactyl/status');
};

export const sendPterodactylPower = async (signal: 'start' | 'stop' | 'restart' | 'kill'): Promise<void> => {
  await apiFetch<void>('/api/v1/pterodactyl/power', {
    method: 'POST',
    body: JSON.stringify({ signal }),
  });
};

export const sendPterodactylCommand = async (command: string): Promise<void> => {
  await apiFetch<void>('/api/v1/pterodactyl/command', {
    method: 'POST',
    body: JSON.stringify({ command }),
  });
};

export const getPterodactylBackups = async (): Promise<PterodactylBackup[]> => {
  return await apiFetch<PterodactylBackup[]>('/api/v1/pterodactyl/backups');
};

export const createPterodactylBackup = async (): Promise<PterodactylBackup> => {
  return await apiFetch<PterodactylBackup>('/api/v1/pterodactyl/backups', {
    method: 'POST',
  });
};

export const deletePterodactylBackup = async (uuid: string): Promise<void> => {
  await apiFetch<void>(`/api/v1/pterodactyl/backups/${uuid}`, {
    method: 'DELETE',
  });
};

export const getPterodactylBackupDownload = async (uuid: string): Promise<{ url: string }> => {
  return await apiFetch<{ url: string }>(`/api/v1/pterodactyl/backups/${uuid}/download`);
};

export const getLongtimeMetrics = async (start: number, end: number, resolution?: string): Promise<ServerMetricsRecord[]> => {
  const params = new URLSearchParams({ start: String(start), end: String(end) });
  if (resolution) params.set('resolution', resolution);
  const res = await apiFetch<{ history: ServerMetricsRecord[] }>(`/api/v1/metrics/longtime?${params}`);
  return res.history;
};

export const getSetupStatus = async (): Promise<{ setupRequired: boolean }> => {
  return await apiFetch<{ setupRequired: boolean }>('/api/v1/setup/status');
};

export const setupAdmin = async (username: string, password: string): Promise<{ token: string; user: any }> => {
  return await apiFetch<{ token: string; user: any }>('/api/v1/setup', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  });
};

export const loginUser = async (username: string, password: string): Promise<{ token: string; user: any }> => {
  return await apiFetch<{ token: string; user: any }>('/api/v1/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  });
};

export const getCurrentUser = async (): Promise<{ username: string; role: string; permissions: string[] }> => {
  return await apiFetch<{ username: string; role: string; permissions: string[] }>('/api/v1/users/me');
};

export const listUsers = async (): Promise<{ username: string; role: string; permissions: string[] }[]> => {
  return await apiFetch<{ username: string; role: string; permissions: string[] }[]>('/api/v1/users');
};

export const saveUser = async (user: { username: string; password?: string; role: string; permissions: string[] }): Promise<void> => {
  await apiFetch<void>('/api/v1/users', {
    method: 'POST',
    body: JSON.stringify(user),
  });
};

export const deleteUser = async (username: string): Promise<void> => {
  await apiFetch<void>(`/api/v1/users/${encodeURIComponent(username)}`, {
    method: 'DELETE',
  });
};

