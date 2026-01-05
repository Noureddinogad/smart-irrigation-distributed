import React, { useState } from 'react';
import { Bell, AlertTriangle, Info, XCircle } from 'lucide-react';
import { MOCK_ALERTS } from '../constants';

const Alerts: React.FC = () => {
  const [pushEnabled, setPushEnabled] = useState(true);

  const getAlertIcon = (type: string) => {
    switch (type) {
      case 'warning': return <AlertTriangle className="w-5 h-5 text-amber-500" />;
      case 'info': return <Info className="w-5 h-5 text-blue-500" />;
      case 'error': return <XCircle className="w-5 h-5 text-red-500" />;
      default: return <Bell className="w-5 h-5 text-gray-500" />;
    }
  };

  const getBorderColor = (type: string) => {
    switch (type) {
      case 'warning': return 'border-l-amber-500';
      case 'info': return 'border-l-blue-500';
      case 'error': return 'border-l-red-500';
      default: return 'border-l-gray-300';
    }
  };

  return (
    <div className="space-y-6 pb-20">
      {/* Notification Toggle */}
      <div className="bg-emerald-50 p-4 rounded-xl flex items-center justify-between border border-emerald-100">
        <div className="flex items-center space-x-3">
          <div className="bg-white p-2 rounded-full shadow-sm">
            <Bell className="w-5 h-5 text-emerald-600" />
          </div>
          <div>
            <h3 className="text-sm font-bold text-gray-800">Push Notifications</h3>
            <p className="text-xs text-gray-500">{pushEnabled ? 'Active' : 'Disabled'}</p>
          </div>
        </div>
        <button 
          onClick={() => setPushEnabled(!pushEnabled)}
          className={`w-12 h-6 rounded-full p-1 transition-colors duration-200 ease-in-out ${pushEnabled ? 'bg-emerald-500' : 'bg-gray-300'}`}
        >
          <div className={`w-4 h-4 bg-white rounded-full shadow-sm transform transition-transform duration-200 ease-in-out ${pushEnabled ? 'translate-x-6' : 'translate-x-0'}`} />
        </button>
      </div>

      <div>
        <h3 className="text-gray-500 font-bold text-xs uppercase tracking-wider mb-3">Recent Alerts</h3>
        <div className="space-y-3">
          {MOCK_ALERTS.map((alert) => (
            <div 
              key={alert.id} 
              className={`bg-white p-4 rounded-xl shadow-sm border border-gray-100 border-l-4 ${getBorderColor(alert.type)} flex items-start space-x-3`}
            >
              <div className="mt-1 flex-shrink-0">
                {getAlertIcon(alert.type)}
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-gray-800 leading-snug">{alert.message}</p>
                <p className="text-xs text-gray-400 mt-1">{alert.timestamp}</p>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

export default Alerts;