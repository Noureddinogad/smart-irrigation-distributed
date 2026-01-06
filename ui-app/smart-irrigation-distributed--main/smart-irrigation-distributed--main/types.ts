export type Tab = 'dash' | 'control' | 'history' | 'alerts';

export interface SensorData {
  moisture: number;
  temperature: number;
  humidity: number;
  waterLevel: number;
  isRaining: boolean;
  timestamp: Date;
}

export interface Alert {
  id: string;
  type: 'warning' | 'info' | 'error';
  message: string;
  timestamp: string;
}

export interface LogEntry {
  id: string;
  timestamp: string;
  moisture: number;
  temperature: number;
  status: 'Wet' | 'Dry' | 'Optimal';
}

export interface User {
  username: string;
}