import React, { useState } from 'react';
import { Layout } from './components/Layout';
import { LoginScreen } from './components/LoginScreen';
import { DashboardScreen } from './components/DashboardScreen';
import { ControlScreen } from './components/ControlScreen';
import { HistoryScreen } from './components/HistoryScreen';
import { AlertsScreen } from './components/AlertsScreen';

function App() {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [activeTab, setActiveTab] = useState('dashboard');

  const handleLogin = () => {
    setIsAuthenticated(true);
  };

  const handleLogout = () => {
    setIsAuthenticated(false);
    setActiveTab('dashboard');
  };

  if (!isAuthenticated) {
    return <LoginScreen onLogin={handleLogin} />;
  }

  const renderScreen = () => {
    switch (activeTab) {
      case 'dashboard': return <DashboardScreen />;
      case 'control': return <ControlScreen />;
      case 'history': return <HistoryScreen />;
      case 'alerts': return <AlertsScreen />;
      default: return <DashboardScreen />;
    }
  };

  return (
    <Layout 
      activeTab={activeTab} 
      onTabChange={setActiveTab}
      onLogout={handleLogout}
    >
      {renderScreen()}
    </Layout>
  );
}

export default App;