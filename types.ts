export interface SensorData {
  id: string;
  temperature: number; // Celsius
  humidity: number; // %
  waterLevel: number; // %
  soilMoisture: number; // %
  timestamp: string;
}

export interface SystemState {
  isAutoMode: boolean;
  isPumpActive: boolean;
  alertsEnabled: boolean;
  lastSync: string;
}

export interface User {
  id: string;
  username: string;
  token: string;
}

export interface Alert {
  id: string;
  type: 'warning' | 'info' | 'critical';
  message: string;
  timestamp: string;
  read: boolean;
}