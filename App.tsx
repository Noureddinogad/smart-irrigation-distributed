import React, { useState, useEffect } from 'react';
import { LayoutDashboard, Sliders, History as HistoryIcon, Bell, LogOut } from 'lucide-react';
import Login from './components/Login';
import Dashboard from './components/Dashboard';
import Control from './components/Control';
import History from './components/History';
import Alerts from './components/Alerts';
import GeminiInsight from './components/GeminiInsight';
import { Tab, SensorData } from './types';
import { MOCK_ALERTS } from './constants';

function App() {
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [activeTab, setActiveTab] = useState<Tab>('dash');
  
  // Lifted state from Control to App to allow sensor data to affect control logic
  const [mode, setMode] = useState<'auto' | 'manual'>('auto');
  const [pumpState, setPumpState] = useState(false);

  // Simulated Sensor Data
  const [sensorData, setSensorData] = useState<SensorData>({
    moisture: 33,
    temperature: 25.1,
    humidity: 47,
    waterLevel: 78,
    isRaining: false,
    timestamp: new Date()
  });

  // Simulate live data updates
  useEffect(() => {
    if (!isLoggedIn) return;

    const interval = setInterval(() => {
      setSensorData(prev => {
        // Randomly toggle raining status occasionally (10% chance to change state)
        // This ensures the user can eventually see the feature in action
        const shouldToggleRain = Math.random() > 0.9;
        const newRainingState = shouldToggleRain ? !prev.isRaining : prev.isRaining;

        return {
          ...prev,
          moisture: Math.min(100, Math.max(0, prev.moisture + (Math.random() * 2 - 1))),
          temperature: Math.min(40, Math.max(15, prev.temperature + (Math.random() * 0.4 - 0.2))),
          isRaining: newRainingState,
          timestamp: new Date()
        };
      });
    }, 3000);

    return () => clearInterval(interval);
  }, [isLoggedIn]);

  // Safety Logic: Stop pump if raining
  useEffect(() => {
    if (sensorData.isRaining && pumpState) {
      setPumpState(false);
      // In a real app, we might push a new alert here
    }
  }, [sensorData.isRaining, pumpState]);

  if (!isLoggedIn) {
    return <Login onLogin={() => setIsLoggedIn(true)} />;
  }

  const renderContent = () => {
    switch (activeTab) {
      case 'dash':
        return (
            <>
                <Dashboard data={sensorData} />
                <GeminiInsight data={sensorData} />
            </>
        );
      case 'control':
        return (
          <Control 
            mode={mode} 
            setMode={setMode} 
            pumpState={pumpState} 
            setPumpState={setPumpState}
            isRaining={sensorData.isRaining}
          />
        );
      case 'history':
        return <History />;
      case 'alerts':
        return <Alerts />;
      default:
        return <Dashboard data={sensorData} />;
    }
  };

  return (
    <div className="flex flex-col h-screen max-w-md mx-auto bg-gray-50 relative shadow-2xl overflow-hidden">
      {/* Header */}
      <header className="bg-[#059669] px-6 pt-6 pb-4 flex items-center justify-between text-white shadow-md z-10 shrink-0">
        <div>
          <h1 className="text-xl font-bold tracking-tight">EcoFlow</h1>
          <p className="text-xs text-emerald-100 opacity-90">Smart Irrigation Client</p>
        </div>
        <button 
            onClick={() => setIsLoggedIn(false)}
            className="p-2 bg-emerald-700/50 rounded-full hover:bg-emerald-700 transition-colors"
        >
          <LogOut className="w-5 h-5" />
        </button>
      </header>

      {/* Main Content Area - Scrollable */}
      <main className="flex-1 overflow-y-auto p-4 scroll-smooth">
        {renderContent()}
      </main>

      {/* Bottom Navigation */}
      <nav className="bg-white border-t border-gray-100 px-6 py-2 shadow-[0_-4px_6px_-1px_rgba(0,0,0,0.05)] shrink-0 z-20">
        <ul className="flex justify-between items-center">
          <li>
            <button 
              onClick={() => setActiveTab('dash')}
              className={`flex flex-col items-center p-2 rounded-lg transition-colors ${activeTab === 'dash' ? 'text-emerald-600' : 'text-gray-400 hover:text-gray-500'}`}
            >
              <LayoutDashboard className={`w-6 h-6 mb-1 ${activeTab === 'dash' ? 'stroke-[2.5px]' : 'stroke-2'}`} />
              <span className="text-[10px] font-medium">Dash</span>
            </button>
          </li>
          <li>
            <button 
              onClick={() => setActiveTab('control')}
              className={`flex flex-col items-center p-2 rounded-lg transition-colors ${activeTab === 'control' ? 'text-emerald-600' : 'text-gray-400 hover:text-gray-500'}`}
            >
              <Sliders className={`w-6 h-6 mb-1 ${activeTab === 'control' ? 'stroke-[2.5px]' : 'stroke-2'}`} />
              <span className="text-[10px] font-medium">Control</span>
            </button>
          </li>
          <li>
            <button 
              onClick={() => setActiveTab('history')}
              className={`flex flex-col items-center p-2 rounded-lg transition-colors ${activeTab === 'history' ? 'text-emerald-600' : 'text-gray-400 hover:text-gray-500'}`}
            >
              <HistoryIcon className={`w-6 h-6 mb-1 ${activeTab === 'history' ? 'stroke-[2.5px]' : 'stroke-2'}`} />
              <span className="text-[10px] font-medium">History</span>
            </button>
          </li>
          <li>
            <button 
              onClick={() => setActiveTab('alerts')}
              className={`flex flex-col items-center p-2 rounded-lg transition-colors ${activeTab === 'alerts' ? 'text-emerald-600' : 'text-gray-400 hover:text-gray-500'}`}
            >
              <div className="relative">
                <Bell className={`w-6 h-6 mb-1 ${activeTab === 'alerts' ? 'stroke-[2.5px]' : 'stroke-2'}`} />
                {MOCK_ALERTS.length > 0 && (
                    <span className="absolute -top-0.5 -right-0.5 w-2.5 h-2.5 bg-red-500 rounded-full border-2 border-white"></span>
                )}
              </div>
              <span className="text-[10px] font-medium">Alerts</span>
            </button>
          </li>
        </ul>
      </nav>
    </div>
  );
}

export default App;