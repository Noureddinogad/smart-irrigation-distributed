import React from 'react';
import { Droplets, Thermometer, Wind, Activity, CloudRain } from 'lucide-react';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { CHART_DATA } from '../constants';
import { SensorData } from '../types';

interface DashboardProps {
  data: SensorData;
}

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
        {React.cloneElement(icon as React.ReactElement, { className: `w-6 h-6 ${colorClass.replace('bg-', 'text-')}` })}
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

const Dashboard: React.FC<DashboardProps> = ({ data }) => {
  return (
    <div className="space-y-6 pb-20">
      <div className="bg-white p-4 rounded-xl shadow-sm border border-gray-100">
        <div className="flex justify-between items-center mb-2">
            <h2 className="text-lg font-semibold text-gray-800">Status Overview</h2>
            <span className="text-xs text-gray-400 font-mono">
                {data.timestamp.toLocaleTimeString('en-US', { hour12: false })}
            </span>
        </div>
        <p className="text-xs text-gray-500 mb-4">Live Updates from Sensors</p>
        
        <div className="grid grid-cols-2 gap-4">
          <StatCard
            icon={<Droplets />}
            label="Soil Moisture"
            value={data.moisture.toFixed(0)}
            unit="%"
            status="Optimal"
            statusColor="bg-blue-100 text-blue-600"
            colorClass="bg-blue-500 text-blue-600"
          />
          <StatCard
            icon={<Thermometer />}
            label="Temperature"
            value={data.temperature.toFixed(1)}
            unit="°C"
            status="Normal"
            statusColor="bg-orange-100 text-orange-600"
            colorClass="bg-orange-500 text-orange-600"
          />
          <StatCard
            icon={<Wind />}
            label="Humidity"
            value={data.humidity.toFixed(0)}
            unit="%"
            status="Good"
            statusColor="bg-teal-100 text-teal-600"
            colorClass="bg-teal-500 text-teal-600"
          />
          <StatCard
            icon={<CloudRain />}
            label="Rain Sensor"
            value={data.isRaining ? "Raining" : "Dry"}
            status={data.isRaining ? "Wet" : "Clear"}
            statusColor={data.isRaining ? "bg-indigo-100 text-indigo-600" : "bg-gray-100 text-gray-600"}
            colorClass="bg-indigo-500 text-indigo-600"
          />
          <StatCard
            icon={<Activity />}
            label="Water Level"
            value={data.waterLevel.toFixed(0)}
            unit="%"
            status="Ok"
            statusColor="bg-purple-100 text-purple-600"
            colorClass="bg-purple-500 text-purple-600"
          />
        </div>
      </div>

      <div className="bg-white p-5 rounded-xl shadow-sm border border-gray-100">
        <h3 className="text-gray-800 font-semibold mb-4">Moisture Trends (Last 24h)</h3>
        <div className="h-48 w-full">
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={CHART_DATA}>
              <defs>
                <linearGradient id="colorMoisture" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.1}/>
                  <stop offset="95%" stopColor="#3b82f6" stopOpacity={0}/>
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f3f4f6" />
              <XAxis 
                dataKey="time" 
                axisLine={false} 
                tickLine={false} 
                tick={{fontSize: 12, fill: '#9ca3af'}} 
                dy={10}
              />
              <YAxis 
                hide 
                domain={[0, 100]} 
              />
              <Tooltip 
                contentStyle={{ borderRadius: '8px', border: 'none', boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)' }}
                cursor={{ stroke: '#3b82f6', strokeWidth: 2 }}
              />
              <Area 
                type="monotone" 
                dataKey="moisture" 
                stroke="#3b82f6" 
                strokeWidth={3}
                fillOpacity={1} 
                fill="url(#colorMoisture)" 
              />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      </div>
    </div>
  );
};

export default Dashboard;