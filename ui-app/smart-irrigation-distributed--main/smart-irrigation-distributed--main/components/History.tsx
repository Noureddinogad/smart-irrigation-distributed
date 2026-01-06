import React, { useEffect, useMemo, useState } from "react";
import { Clock, Droplets, Thermometer, Waves, CloudRain, RefreshCw } from "lucide-react";
import { api } from "../services/irrigationApi"; // adjust path if different

type HistoryRow = {
  createdUtc: string;
  soil: number | null;
  waterTank: number | null;
  tempC: number | null;
  humidity: number | null;
  raining: boolean | null;
  pump: boolean | null;
};

const DEVICE_ID = "esp32-01";

function fmtTime(iso: string) {
  try {
    const d = new Date(iso);
    // no full date, just time like 20:23:28
    return d.toLocaleTimeString("en-US", { hour12: false });
  } catch {
    return iso;
  }
}

export default function History() {
  const [rows, setRows] = useState<HistoryRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState("");

  const [hours, setHours] = useState(6); // last 6h
  const [limit, setLimit] = useState(200);

  async function load() {
    setErr("");
    setLoading(true);
    try {
      const to = new Date().toISOString();
      const from = new Date(Date.now() - hours * 60 * 60 * 1000).toISOString();

      const data = await api.getHistory(DEVICE_ID, from, to, limit);
      const list = Array.isArray(data) ? data : [];
      setRows(list as HistoryRow[]);
    } catch (e: any) {
      setRows([]);
      setErr(e?.message || String(e));
    } finally {
      setLoading(false);
    }
  }

  // initial + when hours/limit change
  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hours, limit]);

  // auto refresh every 10s
  useEffect(() => {
    const id = window.setInterval(() => load(), 10000);
    return () => window.clearInterval(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hours, limit]);

  // newest first (assume backend returns newest first; if not, we reverse safely)
  const list = useMemo(() => {
    if (!rows?.length) return [];
    // if createdUtc increases, make newest first
    const a = rows[0]?.createdUtc;
    const b = rows[rows.length - 1]?.createdUtc;
    if (a && b && new Date(a).getTime() < new Date(b).getTime()) {
      return [...rows].reverse();
    }
    return rows;
  }, [rows]);

  return (
    <div className="space-y-4 pb-24">
      {/* Header */}
      <div className="flex items-center justify-between px-1">
        <div className="flex items-center space-x-2">
          <Clock className="w-5 h-5 text-emerald-700" />
          <h2 className="text-lg font-bold text-gray-800">Data Logs</h2>
        </div>

        <button
          onClick={load}
          className="inline-flex items-center gap-2 text-xs font-semibold px-3 py-2 rounded-xl border border-gray-200 bg-white hover:bg-gray-50"
          title="Refresh"
        >
          <RefreshCw className={`w-4 h-4 ${loading ? "animate-spin" : ""}`} />
          Refresh
        </button>
      </div>

      {/* Filters */}
      <div className="bg-white p-3 rounded-2xl shadow-sm border border-gray-100 flex items-center justify-between gap-3">
        <div className="text-xs text-gray-500 font-semibold">Window</div>

        <div className="flex gap-2">
          {[1, 3, 6, 12, 24].map((h) => (
            <button
              key={h}
              onClick={() => setHours(h)}
              className={`text-xs font-semibold px-3 py-2 rounded-xl border transition ${
                hours === h
                  ? "border-emerald-200 bg-emerald-50 text-emerald-700"
                  : "border-gray-200 bg-white text-gray-700 hover:bg-gray-50"
              }`}
            >
              {h}h
            </button>
          ))}
        </div>

        <div className="flex items-center gap-2">
          <span className="text-xs text-gray-500 font-semibold">Limit</span>
          <select
            value={limit}
            onChange={(e) => setLimit(Number(e.target.value))}
            className="text-xs font-semibold px-3 py-2 rounded-xl border border-gray-200 bg-white"
          >
            {[50, 100, 200, 300, 500].map((n) => (
              <option key={n} value={n}>
                {n}
              </option>
            ))}
          </select>
        </div>
      </div>

      {err ? (
        <div className="bg-amber-50 border border-amber-100 text-amber-900 rounded-2xl p-3 text-xs">
          {err}
        </div>
      ) : null}

      {loading && (
        <div className="text-sm text-gray-500 px-1">Loading…</div>
      )}

      {!loading && list.length === 0 && !err && (
        <div className="text-sm text-gray-500 px-1">
          No history data in the selected window.
        </div>
      )}

      {/* List */}
      <div className="space-y-3">
        {list.map((r, idx) => {
          const rain = !!r.raining;
          const pump = !!r.pump;

          return (
            <div
              key={`${r.createdUtc}-${idx}`}
              className="bg-white p-4 rounded-2xl shadow-sm border border-gray-100"
            >
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-xs text-gray-400 font-mono">
                    {fmtTime(r.createdUtc)}
                  </p>

                  <div className="mt-2 flex flex-wrap items-center gap-x-6 gap-y-2">
                    <div className="flex items-center space-x-2">
                      <Droplets className="w-4 h-4 text-blue-500" />
                      <span className="text-gray-800 font-semibold">
                        Soil: {r.soil ?? "—"}%
                      </span>
                    </div>

                    <div className="flex items-center space-x-2">
                      <Waves className="w-4 h-4 text-purple-500" />
                      <span className="text-gray-800 font-semibold">
                        Tank: {r.waterTank ?? "—"}%
                      </span>
                    </div>

                    <div className="flex items-center space-x-2">
                      <Thermometer className="w-4 h-4 text-orange-500" />
                      <span className="text-gray-800 font-semibold">
                        Temp: {r.tempC ?? "—"}°C
                      </span>
                    </div>

                    <div className="flex items-center space-x-2">
                      <span className="w-2 h-2 rounded-full bg-teal-500" />
                      <span className="text-gray-800 font-semibold">
                        Hum: {r.humidity ?? "—"}%
                      </span>
                    </div>
                  </div>
                </div>

                <div className="flex flex-col items-end gap-2">
                  <span
                    className={`text-[11px] px-2.5 py-1 rounded-full border font-semibold ${
                      rain
                        ? "bg-indigo-50 text-indigo-700 border-indigo-200"
                        : "bg-gray-50 text-gray-600 border-gray-200"
                    }`}
                  >
                    {rain ? (
                      <span className="inline-flex items-center gap-1">
                        <CloudRain className="w-3.5 h-3.5" /> RAIN
                      </span>
                    ) : (
                      "DRY"
                    )}
                  </span>

                  <span
                    className={`text-[11px] px-2.5 py-1 rounded-full border font-semibold ${
                      pump
                        ? "bg-emerald-50 text-emerald-700 border-emerald-200"
                        : "bg-gray-50 text-gray-600 border-gray-200"
                    }`}
                  >
                    Pump: {pump ? "ON" : "OFF"}
                  </span>
                </div>
              </div>

              {/* subtle bar */}
              <div className="mt-3 h-2 rounded-full bg-gray-100 overflow-hidden">
                <div
                  className="h-full bg-blue-500/60"
                  style={{ width: `${Math.max(0, Math.min(100, Number(r.soil ?? 0)))}%` }}
                  title="Soil %"
                />
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
