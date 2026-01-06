// src/services/irrigationApi.ts
const API_BASE = "/api";

function withTimeout(ms: number) {
  const ctrl = new AbortController();
  const id = setTimeout(() => ctrl.abort(), ms);
  return { signal: ctrl.signal, cancel: () => clearTimeout(id) };
}

async function httpJson<T>(path: string, init?: RequestInit): Promise<T> {
  const t = withTimeout(6000); // 6s hard timeout
  try {
    const res = await fetch(`${API_BASE}${path}`, {
      signal: t.signal,
      headers: { "Content-Type": "application/json", ...(init?.headers || {}) },
      ...init,
    });

    const text = await res.text();
    const data = text ? safeJson(text) : null;

    if (!res.ok) {
      const msg =
        (data as any)?.error ||
        (data as any)?.message ||
        (typeof text === "string" && text.trim() ? text : "") ||
        `HTTP ${res.status}`;
      throw new Error(msg);
    }

    return data as T;
  } catch (e: any) {
    if (e?.name === "AbortError") throw new Error("Request timed out (6s)");
    throw e;
  } finally {
    t.cancel();
  }
}


function safeJson(t: string) {
  try {
    return JSON.parse(t);
  } catch {
    return null;
  }
}

// === Endpoints your working web uses ===
export const api = {
  getDevices: () => httpJson<string[]>(`/devices`),

  getSummary: (device: string) =>
    httpJson<any>(`/devices/${encodeURIComponent(device)}/summary`),

  getHistory: (device: string, fromUtc: string, toUtc: string, limit = 200) => {
    const qs = new URLSearchParams({
      fromUtc,
      toUtc,
      limit: String(limit),
    });
    return httpJson<any[]>(
      `/devices/${encodeURIComponent(device)}/history?${qs.toString()}`
    );
  },

  setMode: (device: string, mode: "AUTO" | "MANUAL") =>
    httpJson<any>(`/devices/${encodeURIComponent(device)}/mode`, {
      method: "POST",
      body: JSON.stringify({ mode }),
    }),

  setManualPump: (device: string, on: boolean) =>
    httpJson<any>(`/devices/${encodeURIComponent(device)}/manual-pump`, {
      method: "POST",
      body: JSON.stringify({ on }),
    }),

  alertsStreamUrl: (device: string, sinceId = 0) => {
    const qs = new URLSearchParams({ sinceId: String(sinceId || 0) });
    return `${API_BASE}/devices/${encodeURIComponent(device)}/alerts/stream?${qs.toString()}`;
  },
};

export type AlertDTO = {
  id: number;
  type: string; // "warning" | "info" | "error" (or other)
  severity?: string;
  message: string;
  createdUtc?: string;
};

export type SummaryDTO = {
  latest?: { raining?: boolean };
  mode: "AUTO" | "MANUAL";
  manualPump: boolean;
  alerts?: AlertDTO[];
};

// If you already have getSummary(), keep it. Just ensure it returns SummaryDTO-like fields.
export async function getSummary(device: string): Promise<SummaryDTO> {
  return httpJson<SummaryDTO>(`/devices/${encodeURIComponent(device)}/summary`);
}

// SSE: returns the EventSource so caller can close it
export function openAlertsStream(device: string, sinceId: number) {
  const url = `${API_BASE}/devices/${encodeURIComponent(device)}/alerts/stream?sinceId=${sinceId || 0}`;
  return new EventSource(url);
}
