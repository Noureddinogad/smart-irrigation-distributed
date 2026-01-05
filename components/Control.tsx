import React from 'react';
import { Power, Settings, Zap, CloudRain } from 'lucide-react';

interface ControlProps {
  mode: 'auto' | 'manual';
  setMode: (mode: 'auto' | 'manual') => void;
  pumpState: boolean;
  setPumpState: (state: boolean) => void;
  isRaining: boolean;
}

const Control: React.FC<ControlProps> = ({ mode, setMode, pumpState, setPumpState, isRaining }) => {
  const togglePump = () => {
    if (mode === 'manual' && !isRaining) {
      setPumpState(!pumpState);
    }
  };

  return (
    <div className="space-y-6 pb-20">
      {/* Operation Mode Card */}
      <div className="bg-white p-6 rounded-2xl shadow-sm border border-gray-100 text-center">
        <div className="flex items-center justify-center space-x-2 mb-4 text-gray-700 font-semibold text-lg">
          <Settings className="w-5 h-5" />
          <h2>Operation Mode</h2>
        </div>

        <div className="bg-gray-100 p-1 rounded-full flex relative mb-4 max-w-xs mx-auto">
          <div 
            className={`absolute top-1 bottom-1 w-1/2 bg-white rounded-full shadow-sm transition-all duration-300 ease-in-out ${mode === 'auto' ? 'left-1' : 'left-[calc(50%-4px)] translate-x-full'}`}
          />
          <button 
            className={`flex-1 relative z-10 py-2 text-sm font-medium transition-colors ${mode === 'auto' ? 'text-emerald-600' : 'text-gray-500'}`}
            onClick={() => setMode('auto')}
          >
            Auto
          </button>
          <button 
            className={`flex-1 relative z-10 py-2 text-sm font-medium transition-colors ${mode === 'manual' ? 'text-emerald-600' : 'text-gray-500'}`}
            onClick={() => setMode('manual')}
          >
            Manual
          </button>
        </div>

        <p className="text-xs text-gray-400 max-w-xs mx-auto leading-relaxed">
          {mode === 'auto' 
            ? 'System automatically manages watering based on soil moisture levels.'
            : 'Manual mode enabled. You have full control over the pump operation.'}
        </p>
      </div>

      {/* Manual Override Card */}
      <div className={`bg-white p-8 rounded-2xl shadow-sm border border-gray-100 flex flex-col items-center justify-center transition-all ${mode === 'auto' ? 'opacity-60 pointer-events-none' : 'opacity-100'}`}>
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
          disabled={isRaining}
          className={`w-32 h-32 rounded-full flex flex-col items-center justify-center transition-all duration-300 shadow-xl border-4 ${
            isRaining 
              ? 'bg-gray-100 border-gray-200 text-gray-300 cursor-not-allowed'
              : pumpState 
                ? 'bg-emerald-50 border-emerald-500 text-emerald-600 shadow-emerald-100' 
                : 'bg-gray-50 border-gray-200 text-gray-300 hover:border-gray-300'
          }`}
        >
          <Power className={`w-12 h-12 mb-2 ${!isRaining && pumpState ? 'text-emerald-500' : 'text-gray-300'}`} />
          <span className="text-sm font-bold">{pumpState ? 'ON' : 'OFF'}</span>
        </button>
        
        <div className="mt-8 flex items-center space-x-2">
          <div className={`w-3 h-3 rounded-full ${!isRaining && pumpState ? 'bg-emerald-500 animate-pulse' : 'bg-gray-200'}`} />
          <p className="text-sm text-gray-500">
            Pump Status: <span className={!isRaining && pumpState ? 'text-emerald-600 font-medium' : 'text-gray-400'}>{!isRaining && pumpState ? 'Active' : 'Idle'}</span>
          </p>
        </div>
      </div>
    </div>
  );
};

export default Control;