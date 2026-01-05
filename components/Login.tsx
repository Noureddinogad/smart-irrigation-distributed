import React, { useState } from 'react';
import { Leaf } from 'lucide-react';

interface LoginProps {
  onLogin: () => void;
}

const Login: React.FC<LoginProps> = ({ onLogin }) => {
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('.....');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onLogin();
  };

  return (
    <div className="flex flex-col items-center justify-center min-h-screen bg-[#059669] px-4">
      {/* Logo Area */}
      <div className="flex flex-col items-center mb-8">
        <div className="w-20 h-20 bg-emerald-400/30 rounded-full flex items-center justify-center mb-4 backdrop-blur-sm">
          <Leaf className="w-10 h-10 text-white" />
        </div>
        <h1 className="text-3xl font-bold text-white tracking-wide">EcoFlow</h1>
        <p className="text-emerald-100 text-sm mt-1">Smart Irrigation Control</p>
      </div>

      {/* Login Card */}
      <div className="w-full max-w-sm bg-white rounded-xl shadow-2xl p-8">
        <form onSubmit={handleSubmit} className="space-y-6">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Username
            </label>
            <input
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className="w-full px-4 py-3 rounded-lg border border-gray-300 focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500 outline-none transition-all text-gray-700"
              placeholder="Enter username"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Password
            </label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full px-4 py-3 rounded-lg border border-gray-300 focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500 outline-none transition-all text-gray-700"
              placeholder="••••••••"
            />
          </div>

          <button
            type="submit"
            className="w-full bg-[#059669] hover:bg-emerald-700 text-white font-semibold py-3 rounded-lg transition-colors shadow-lg shadow-emerald-200"
          >
            Sign In
          </button>
        </form>
        
        <div className="mt-6 text-center">
          <p className="text-xs text-gray-400">Simulation Mode</p>
        </div>
      </div>
    </div>
  );
};

export default Login;