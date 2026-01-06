import { Alert, LogEntry } from './types';

export const MOCK_ALERTS: Alert[] = [
  {
    id: '1',
    type: 'warning',
    message: 'Water level low (< 20%)',
    timestamp: '03/12/2025 21:48:25'
  },
  {
    id: '2',
    type: 'info',
    message: 'System scheduled auto-watering completed',
    timestamp: '03/12/2025 20:26:45'
  },
  {
    id: '3',
    type: 'error',
    message: 'Pump disconnected',
    timestamp: '02/12/2025 21:50:05'
  }
];

export const MOCK_HISTORY: LogEntry[] = [
  {
    id: '1',
    timestamp: '02/12/2025 • 22:50',
    moisture: 49,
    temperature: 29.4,
    status: 'Wet'
  },
  {
    id: '2',
    timestamp: '02/12/2025 • 23:50',
    moisture: 79,
    temperature: 20.0,
    status: 'Wet'
  },
  {
    id: '3',
    timestamp: '03/12/2025 • 00:50',
    moisture: 48,
    temperature: 22.0,
    status: 'Wet'
  },
  {
    id: '4',
    timestamp: '03/12/2025 • 01:50',
    moisture: 43,
    temperature: 22.6,
    status: 'Wet'
  },
  {
    id: '5',
    timestamp: '03/12/2025 • 02:50',
    moisture: 78,
    temperature: 27.1,
    status: 'Wet'
  }
];

export const CHART_DATA = [
  { time: '00:00', moisture: 45 },
  { time: '04:00', moisture: 42 },
  { time: '08:00', moisture: 40 },
  { time: '12:00', moisture: 35 },
  { time: '16:00', moisture: 60 }, // Watered
  { time: '20:00', moisture: 55 },
  { time: '23:59', moisture: 50 },
];
