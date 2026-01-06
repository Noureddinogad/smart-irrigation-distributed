import React, { useEffect, useRef, useState } from "react";
import {
  Bell,
  AlertTriangle,
  Info,
  XCircle,
  Pause,
  Play,
  Trash2,
  Wifi,
  WifiOff,
} from "lucide-react";
import { api } from "../services/irrigationApi"; // adjust path if needed

type AlertDTO = {
  id: number;
  type: string;
  message: string;
  createdUtc?: string; // ignored in UI
  severity?: string;
};

const DEVICE_ID = "esp32-01";

function kind(a: AlertDTO) {
  const t = String(a.type || "").toLowerCase();
  if (t.includes("err")) return "error";
  if (t.includes("warn")) return "warning";
  if (t.includes("info")) return "info";
  return "neutral";
}

function iconFor(a: AlertDTO) {
  const k = kind(a);
  if (k === "warning") return <AlertTriangle className="w-5 h-5 text-amber-600" />;
  if (k === "info") return <Info className="w-5 h-5 text-blue-600" />;
  if (k === "error") return <XCircle className="w-5 h-5 text-red-600" />;
  return <Bell className="w-5 h-5 text-gray-600" />;
}

function dotClass(k: string) {
  if (k === "error") return "bg-red-500";
  if (k === "warning") return "bg-amber-500";
  if (k === "info") return "bg-blue-500";
  return "bg-gray-300";
}

function pillClass(k: string) {
  if (k === "error") return "bg-red-50 text-red-700 border-red-200";
  if (k === "warning") return "bg-amber-50 text-amber-800 border-amber-200";
  if (k === "info") return "bg-blue-50 text-blue-700 border-blue-200";
  return "bg-gray-50 text-gray-700 border-gray-200";
}

export default function Alerts() {
  const [liveEnabled, setLiveEnabled] = useState(true);

  const [alerts, setAlerts] = useState<AlertDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState("");

  const [streamStatus, setStreamStatus] = useState<"CONNECTING" | "LIVE" | "RETRYING" | "OFF">(
    "CONNECTING"
  );

  const sseRef = useRef<EventSource | null>(null);
  const lastIdRef = useRef<number>(0);

  function closeSse() {
    try {
      sseRef.current?.close();
    } catch (_) {}
    sseRef.current = null;
  }

  function startSse() {
    if (!liveEnabled) {
      setStreamStatus("OFF");
      return;
    }

    closeSse();
    setErr("");
    setStreamStatus("CONNECTING");

    const url = api.alertsStreamUrl(DEVICE_ID, lastIdRef.current || 0);
    const es = new EventSource(url);
    sseRef.current = es;

    es.addEventListener("connected", () => setStreamStatus("LIVE"));

    es.addEventListener("alert", (ev: MessageEvent) => {
      try {
        const a = JSON.parse(ev.data) as AlertDTO;
        if (a?.id != null && a.id > (lastIdRef.current || 0)) lastIdRef.current = a.id;

        setAlerts((prev) => {
          const next = [a, ...prev];
          const seen = new Set<number>();
          const out: AlertDTO[] = [];
          for (const x of next) {
            if (x?.id == null) continue;
            if (seen.has(x.id)) continue;
            seen.add(x.id);
            out.push(x);
            if (out.length >= 25) break; // keep 25
          }
          return out;
        });

        setStreamStatus("LIVE");
      } catch (e) {
        console.error("SSE parse error", e);
      }
    });

    es.onerror = () => {
      setStreamStatus("RETRYING");
      setErr("Stream interrupted (auto-retrying)...");
    };
  }

  async function loadInitial() {
    setLoading(true);
    setErr("");
    try {
      const summary = await api.getSummary(DEVICE_ID);
      const list: AlertDTO[] = Array.isArray(summary?.alerts) ? summary.alerts : [];
      setAlerts(list);

      const maxId = list.reduce((m, a) => (a?.id != null && a.id > m ? a.id : m), 0);
      lastIdRef.current = maxId;

      startSse();
    } catch (e: any) {
      setErr(e.message || String(e));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadInitial();
    return () => closeSse();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!liveEnabled) {
      closeSse();
      setStreamStatus("OFF");
      return;
    }
    startSse();
    return () => {};
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [liveEnabled]);

  const liveBadge =
    streamStatus === "LIVE"
      ? "bg-emerald-50 text-emerald-700 border-emerald-200"
      : streamStatus === "RETRYING"
      ? "bg-amber-50 text-amber-800 border-amber-200"
      : streamStatus === "CONNECTING"
      ? "bg-gray-50 text-gray-700 border-gray-200"
      : "bg-gray-50 text-gray-700 border-gray-200";

  const liveIcon =
    streamStatus === "LIVE" ? (
      <Wifi className="w-4 h-4" />
    ) : streamStatus === "OFF" ? (
      <WifiOff className="w-4 h-4" />
    ) : (
      <Wifi className="w-4 h-4" />
    );

  return (
    <div className="space-y-5 pb-24">
      {/* PREMIUM HEADER */}
      <div className="rounded-3xl border border-gray-100 bg-gradient-to-br from-emerald-50 to-white shadow-sm overflow-hidden">
        <div className="p-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-11 h-11 rounded-2xl bg-white/80 backdrop-blur border border-emerald-100 flex items-center justify-center shadow-sm">
              <Bell className="w-5 h-5 text-emerald-700" />
            </div>
            <div>
              <div className="text-base font-extrabold text-gray-900 leading-tight">Notifications</div>
              <div className="text-xs text-gray-500">
                Live alerts for <span className="font-mono text-gray-700">{DEVICE_ID}</span>
              </div>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <span
              className={`text-[11px] px-2.5 py-1 rounded-full border font-semibold inline-flex items-center gap-2 ${liveBadge}`}
              title="Stream status"
            >
              {liveIcon}
              {streamStatus}
            </span>

            <button
              onClick={() => setAlerts([])}
              className="h-10 px-3 rounded-2xl border border-gray-200 bg-white/80 hover:bg-white text-gray-800 text-xs font-semibold inline-flex items-center gap-2 shadow-sm"
              title="Clear local list (does not delete server data)"
            >
              <Trash2 className="w-4 h-4" />
              Clear
            </button>

            <button
              onClick={() => setLiveEnabled((v) => !v)}
              className={`h-10 px-3 rounded-2xl border text-xs font-semibold inline-flex items-center gap-2 shadow-sm ${
                liveEnabled
                  ? "border-emerald-200 bg-emerald-600 text-white hover:bg-emerald-700"
                  : "border-gray-200 bg-white/80 text-gray-800 hover:bg-white"
              }`}
              title={liveEnabled ? "Pause live stream" : "Resume live stream"}
            >
              {liveEnabled ? <Pause className="w-4 h-4" /> : <Play className="w-4 h-4" />}
              {liveEnabled ? "Pause" : "Resume"}
            </button>
          </div>
        </div>

        {err ? (
          <div className="px-4 pb-4">
            <div className="rounded-2xl border border-amber-100 bg-amber-50 text-amber-900 text-xs p-3">
              {err}
            </div>
          </div>
        ) : null}
      </div>

      {/* LIST CONTAINER */}
      <div className="rounded-3xl border border-gray-100 bg-white shadow-sm overflow-hidden">
        <div className="p-4 flex items-center justify-between border-b border-gray-100">
          <div className="text-xs font-extrabold text-gray-500 uppercase tracking-wider">
            Recent alerts
          </div>
          <div className="text-xs text-gray-500">{alerts.length}</div>
        </div>

        {loading ? (
          <div className="p-4 text-sm text-gray-500">Loading…</div>
        ) : alerts.length === 0 ? (
          <div className="p-6 text-sm text-gray-500">
            No alerts right now.
            <div className="text-xs text-gray-400 mt-1">When the RMI brain generates alerts, they appear here instantly.</div>
          </div>
        ) : (
          <div className="p-3 space-y-2">
            {alerts.map((a) => {
              const k = kind(a);
              return (
                <div
                  key={a.id}
                  className="group rounded-2xl border border-gray-100 bg-white hover:bg-gray-50/60 transition-colors p-3 flex items-start gap-3"
                >
                  <div className="mt-1 flex items-center gap-2">
                    <span className={`w-2.5 h-2.5 rounded-full ${dotClass(k)}`} />
                    <div className="w-10 h-10 rounded-2xl bg-gray-50 border border-gray-100 flex items-center justify-center">
                      {iconFor(a)}
                    </div>
                  </div>

                  <div className="flex-1 min-w-0">
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <div className="text-sm font-semibold text-gray-900 leading-snug">
                          {a.message}
                        </div>
                        {/* no dates / no timeago */}
                        <div className="text-[11px] text-gray-400 mt-1 font-mono">
                          ID #{a.id}
                        </div>
                      </div>

                      <span className={`text-[11px] px-2.5 py-1 rounded-full border font-semibold ${pillClass(k)}`}>
                        {String(a.type || "alert").toUpperCase()}
                      </span>
                    </div>

                    {/* subtle bottom accent */}
                    <div className="mt-3 h-1.5 rounded-full bg-gray-100 overflow-hidden">
                      <div
                        className={`h-full ${k === "error" ? "bg-red-500/70" : k === "warning" ? "bg-amber-500/70" : k === "info" ? "bg-blue-500/70" : "bg-gray-300/70"}`}
                        style={{ width: "38%" }}
                      />
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
