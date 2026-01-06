import React, { useEffect, useRef, useState } from "react";
import { Power, Settings, Zap, CloudRain } from "lucide-react";
import { api } from "../services/irrigationApi";

const DEVICE_ID = "esp32-01";

function sleep(ms: number) {
  return new Promise((r) => setTimeout(r, ms));
}

export default function Control() {
  const [mode, setMode] = useState<"AUTO" | "MANUAL">("AUTO");
  const [pumpState, setPumpState] = useState(false);
  const [isRaining, setIsRaining] = useState(false);

  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [lastOk, setLastOk] = useState<string>("");

  // Prevent overlapping commands (classic source of “sometimes fails”)
  const inFlightRef = useRef(false);

  async function loadState() {
    const summary = await api.getSummary(DEVICE_ID);
    setMode(summary.mode);
    setPumpState(!!summary.manualPump);
    setIsRaining(!!summary.latest?.raining);
    return summary;
  }

  useEffect(() => {
    (async () => {
      try {
        setError("");
        await loadState();
      } catch (e: any) {
        setError(e.message || String(e));
      }
    })();

    const id = setInterval(() => {
      loadState().catch(() => {});
    }, 5000);

    return () => clearInterval(id);
  }, []);

  async function runCommand(fn: () => Promise<void>, okMsg: string) {
    if (inFlightRef.current) return; // ignore rapid double-clicks
    inFlightRef.current = true;
    setSaving(true);
    setError("");

    try {
      // Try once
      await fn();

      // Verify with server truth (don’t trust local state)
      await loadState();

      setLastOk(okMsg);
    } catch (e1: any) {
      // Retry once if transient (timeout / connection / 5xx)
      const msg = String(e1?.message || e1);
      const looksTransient =
        msg.includes("timed out") ||
        msg.includes("Failed to fetch") ||
        msg.includes("NetworkError") ||
        msg.includes("HTTP 5");

      if (looksTransient) {
        try {
          await sleep(400);
          await fn();
          await loadState();
          setLastOk(okMsg + " (after retry)");
        } catch (e2: any) {
          setError(String(e2?.message || e2));
        }
      } else {
        setError(msg);
      }
    } finally {
      setSaving(false);
      inFlightRef.current = false;
    }
  }

  function changeMode(next: "AUTO" | "MANUAL") {
    runCommand(
      async () => {
        await api.setMode(DEVICE_ID, next);
      },
      `Mode set to ${next}`
    );
  }

  function togglePump() {
    // Hard client rule (still enforced server-side ideally)
    if (mode !== "MANUAL") {
      setError("Pump can only be controlled in MANUAL mode.");
      return;
    }
    if (isRaining) {
      setError("Rain detected. Pump is disabled.");
      return;
    }

    runCommand(
      async () => {
        await api.setManualPump(DEVICE_ID, !pumpState);
      },
      `Pump command sent: ${!pumpState ? "ON" : "OFF"}`
    );
  }

  return (
    <div className="space-y-6 pb-20">
      {/* MODE CARD */}
      <div className="bg-white p-6 rounded-2xl shadow-sm border border-gray-100 text-center">
        <div className="flex items-center justify-center space-x-2 mb-4 text-gray-700 font-semibold text-lg">
          <Settings className="w-5 h-5" />
          <h2>Operation Mode</h2>
        </div>

        <div className="bg-gray-100 p-1 rounded-full flex relative mb-4 max-w-xs mx-auto">
          <div
            className={`absolute top-1 bottom-1 w-1/2 bg-white rounded-full shadow-sm transition-all ${
              mode === "AUTO" ? "left-1" : "left-[calc(50%-4px)] translate-x-full"
            }`}
          />
          <button
            className={`flex-1 relative z-10 py-2 text-sm font-medium ${
              mode === "AUTO" ? "text-emerald-600" : "text-gray-500"
            }`}
            disabled={saving}
            onClick={() => changeMode("AUTO")}
          >
            Auto
          </button>
          <button
            className={`flex-1 relative z-10 py-2 text-sm font-medium ${
              mode === "MANUAL" ? "text-emerald-600" : "text-gray-500"
            }`}
            disabled={saving}
            onClick={() => changeMode("MANUAL")}
          >
            Manual
          </button>
        </div>

        <p className="text-xs text-gray-400">
          {mode === "AUTO"
            ? "System automatically controls irrigation."
            : "Manual mode enabled. You control the pump."}
        </p>
      </div>

      {/* MANUAL PUMP CARD */}
      <div
        className={`bg-white p-8 rounded-2xl shadow-sm border border-gray-100 flex flex-col items-center ${
          mode === "AUTO" ? "opacity-60 pointer-events-none" : ""
        }`}
      >
        <div className="flex items-center space-x-2 mb-8 text-gray-400 font-medium">
          <Zap className="w-5 h-5" />
          <h3>Manual Override</h3>
        </div>

        {isRaining && (
          <div className="mb-6 bg-blue-50 text-blue-600 px-4 py-2 rounded-lg flex items-center text-xs font-medium border border-blue-100 animate-pulse">
            <CloudRain className="w-4 h-4 mr-2" />
            Rain detected. Pump disabled.
          </div>
        )}

        <button
          onClick={togglePump}
          disabled={saving || isRaining}
          className={`w-32 h-32 rounded-full flex flex-col items-center justify-center border-4 transition-all shadow-xl ${
            isRaining
              ? "bg-gray-100 border-gray-200 text-gray-300"
              : pumpState
              ? "bg-emerald-50 border-emerald-500 text-emerald-600"
              : "bg-gray-50 border-gray-200 text-gray-300"
          }`}
        >
          <Power className="w-12 h-12 mb-2" />
          <span className="text-sm font-bold">{pumpState ? "ON" : "OFF"}</span>
          {saving ? <span className="text-[10px] mt-1 opacity-70">sending…</span> : null}
        </button>

        <p className="mt-6 text-sm text-gray-500">
          Pump Status:{" "}
          <span className={pumpState ? "text-emerald-600 font-medium" : ""}>
            {pumpState ? "Active" : "Idle"}
          </span>
        </p>

        {lastOk ? (
          <div className="mt-3 text-xs text-emerald-700 bg-emerald-50 border border-emerald-100 rounded-lg p-2">
            {lastOk}
          </div>
        ) : null}

        {error ? (
          <div className="mt-3 text-xs text-red-700 bg-red-50 border border-red-100 rounded-lg p-2">
            {error}
          </div>
        ) : null}
      </div>
    </div>
  );
}
