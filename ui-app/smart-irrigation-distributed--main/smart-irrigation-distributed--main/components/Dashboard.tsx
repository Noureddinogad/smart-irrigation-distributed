import React, { useEffect, useMemo, useState } from "react";
import { Droplets, Thermometer, Wind, Activity, CloudRain } from "lucide-react";
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from "recharts";
import { api } from "../services/irrigationApi";

// ---- Types that match your Gateway summary shape (based on your working web) ----
type Summary = {
  latest?: {
    soil?: number;
    waterTank?: number;
    tempC?: number | null;
    humidity?: number | null;
    raining?: boolean;
    pump?: boolean;
    createdUtc?: string;
  };
};

const StatCard: React.FC<{
  icon: React.ReactNode;
  label: string;
  value: string;
  unit?: string;
  status: string;
  statusColor: string;
  colorClass: string;
}> = ({ icon, label, value, unit, status, statusColor, colorClass }) => (
  <div className="bg-white p-5 rounded-2xl shadow-sm border border-gray-100 flex flex-col justify-between h-36">
    <div className="flex justify-between items-start">
      <div className={`p-2 rounded-xl ${colorClass} bg-opacity-10`}>
        {React.cloneElement(icon as React.ReactElement, {
          className: `w-6 h-6 ${colorClass.replace("bg-", "text-")}`,
        })}
      </div>
      <span className={`text-xs px-2 py-1 rounded-full ${statusColor} bg-opacity-20 font-medium`}>
        {status}
      </span>
    </div>
    <div>
      <div className="flex items-baseline space-x-1">
        <span className="text-2xl font-bold text-gray-800">{value}</span>
        {unit && <span className="text-sm text-gray-500 font-medium">{unit}</span>}
      </div>
      <p className="text-xs text-gray-400 mt-1">{label}</p>
    </div>
  </div>
);

function clamp(n: number, min: number, max: number) {
  return Math.max(min, Math.min(max, n));
}

export default function Dashboard() {
  const device = "esp32-01"; // you have one device; keep it simple
  const [summary, setSummary] = useState<Summary | null>(null);
  const [error, setError] = useState<string>("");

  const latest = summary?.latest;

  async function load() {
    try {
      setError("");
      const s = await api.getSummary(device);
      setSummary(s);
    } catch (e: any) {
      setError(e.message || String(e));
      setSummary(null);
    }
  }

  useEffect(() => {
    load();
    const id = window.setInterval(load, 5000);
    return () => window.clearInterval(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Build a small chart series from latest only (until we wire History screen).
  // This preserves your chart section without lying with mock data.
  const chartData = useMemo(() => {
    if (!latest?.createdUtc || latest?.soil == null) return [];
    return [
      {
        time: new Date(latest.createdUtc).toLocaleTimeString([], { hour12: false }),
        moisture: clamp(Number(latest.soil) || 0, 0, 100),
      },
    ];
  }, [latest?.createdUtc, latest?.soil]);

  // Map backend -> UI fields
  const moisture = clamp(Number(latest?.soil ?? 0), 0, 100);
  const waterLevel = clamp(Number(latest?.waterTank ?? 0), 0, 100);
  const temperature = latest?.tempC ?? null;
  const humidity = latest?.humidity ?? null;
  const isRaining = !!latest?.raining;

  const timeLabel = latest?.createdUtc
    ? new Date(latest.createdUtc).toLocaleTimeString("en-US", { hour12: false })
    : "--:--:--";

  // Status labels (you can tune later)
  const moistureStatus =
    moisture >= 30 && moisture <= 70 ? ["Optimal", "bg-blue-100 text-blue-600"] : ["Check", "bg-amber-100 text-amber-600"];

  const tempStatus =
    temperature != null && temperature >= 15 && temperature <= 32
      ? ["Normal", "bg-orange-100 text-orange-600"]
      : ["Alert", "bg-red-100 text-red-600"];

  const humidityStatus =
    humidity != null && humidity >= 30 && humidity <= 75 ? ["Good", "bg-teal-100 text-teal-600"] : ["Check", "bg-amber-100 text-amber-600"];

  const rainStatus = isRaining
    ? ["Wet", "bg-indigo-100 text-indigo-600"]
    : ["Clear", "bg-gray-100 text-gray-600"];

  const waterStatus =
    waterLevel >= 20 ? ["Ok", "bg-purple-100 text-purple-600"] : ["Low", "bg-red-100 text-red-600"];

  return (
    <div className="space-y-6 pb-20">
      <div className="bg-white p-4 rounded-xl shadow-sm border border-gray-100">
        <div className="flex justify-between items-center mb-2">
          <h2 className="text-lg font-semibold text-gray-800">Status Overview</h2>
          <span className="text-xs text-gray-400 font-mono">{timeLabel}</span>
        </div>
        <p className="text-xs text-gray-500 mb-4">Live Updates from Sensors</p>

        {error ? (
          <div className="text-sm text-red-600 bg-red-50 border border-red-100 rounded-lg p-3">
            {error}
          </div>
        ) : null}

        <div className="grid grid-cols-2 gap-4">
          <StatCard
            icon={<Droplets />}
            label="Soil Moisture"
            value={String(moisture)}
            unit="%"
            status={moistureStatus[0]}
            statusColor={moistureStatus[1]}
            colorClass="bg-blue-500 text-blue-600"
          />

          <StatCard
            icon={<Thermometer />}
            label="Temperature"
            value={temperature != null ? temperature.toFixed(1) : "—"}
            unit="°C"
            status={tempStatus[0]}
            statusColor={tempStatus[1]}
            colorClass="bg-orange-500 text-orange-600"
          />

          <StatCard
            icon={<Wind />}
            label="Humidity"
            value={humidity != null ? humidity.toFixed(0) : "—"}
            unit="%"
            status={humidityStatus[0]}
            statusColor={humidityStatus[1]}
            colorClass="bg-teal-500 text-teal-600"
          />

          <StatCard
            icon={<CloudRain />}
            label="Rain Sensor"
            value={isRaining ? "Raining" : "Dry"}
            status={rainStatus[0]}
            statusColor={rainStatus[1]}
            colorClass="bg-indigo-500 text-indigo-600"
          />

          <StatCard
            icon={<Activity />}
            label="Water Level"
            value={String(waterLevel)}
            unit="%"
            status={waterStatus[0]}
            statusColor={waterStatus[1]}
            colorClass="bg-purple-500 text-purple-600"
          />
        </div>
      </div>

      {/* Keep your chart section, but stop using mock CHART_DATA */}
      <div className="bg-white p-5 rounded-xl shadow-sm border border-gray-100">
        <h3 className="text-gray-800 font-semibold mb-4">Moisture Trends (Last 24h)</h3>

        {chartData.length === 0 ? (
          <div className="text-sm text-gray-400">No chart data yet. (History screen will provide real series.)</div>
        ) : (
          <div className="h-48 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={chartData}>
                <defs>
                  <linearGradient id="colorMoisture" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.1} />
                    <stop offset="95%" stopColor="#3b82f6" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f3f4f6" />
                <XAxis dataKey="time" axisLine={false} tickLine={false} tick={{ fontSize: 12, fill: "#9ca3af" }} dy={10} />
                <YAxis hide domain={[0, 100]} />
                <Tooltip
                  contentStyle={{
                    borderRadius: "8px",
                    border: "none",
                    boxShadow: "0 4px 6px -1px rgb(0 0 0 / 0.1)",
                  }}
                  cursor={{ stroke: "#3b82f6", strokeWidth: 2 }}
                />
                <Area type="monotone" dataKey="moisture" stroke="#3b82f6" strokeWidth={3} fillOpacity={1} fill="url(#colorMoisture)" />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        )}
      </div>
    </div>
  );
}
