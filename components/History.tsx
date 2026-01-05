import React from 'react';
import { Clock, Droplets, Thermometer } from 'lucide-react';
import { MOCK_HISTORY } from '../constants';

const History: React.FC = () => {
  return (
    <div className="space-y-4 pb-20">
      <div className="flex items-center space-x-2 mb-2 px-1">
        <Clock className="w-5 h-5 text-emerald-700" />
        <h2 className="text-lg font-bold text-gray-800">Data Logs</h2>
      </div>

      <div className="space-y-3">
        {MOCK_HISTORY.map((log) => (
          <div key={log.id} className="bg-white p-4 rounded-xl shadow-sm border border-gray-100 flex items-center justify-between">
            <div className="flex-1">
              <p className="text-xs text-gray-400 font-mono mb-2">{log.timestamp}</p>
              <div className="flex items-center space-x-6">
                <div className="flex items-center space-x-2">
                  <Droplets className="w-4 h-4 text-blue-500" />
                  <span className="text-gray-700 font-semibold">{log.moisture}%</span>
                </div>
                <div className="flex items-center space-x-2">
                  <Thermometer className="w-4 h-4 text-orange-500" />
                  <span className="text-gray-700 font-semibold">{log.temperature}°C</span>
                </div>
              </div>
            </div>
            
            <div className="bg-emerald-50 px-3 py-1 rounded-full">
              <span className="text-xs font-medium text-emerald-600">{log.status}</span>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default History;