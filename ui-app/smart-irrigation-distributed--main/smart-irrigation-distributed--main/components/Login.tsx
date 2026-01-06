import React, { useState } from "react";
import { Leaf, AlertTriangle } from "lucide-react";

interface LoginProps {
  onLogin: (user: { username: string; role: "ADMIN" | "VIEWER" }) => void;
}

/**
 * TEMPORARY (SIMULATION MODE)
 * Later this will be replaced by Spring Security + JWT
 */
const VALID_USERS = [
  { username: "admin", password: "admin123", role: "ADMIN" as const },
  { username: "viewer", password: "viewer123", role: "VIEWER" as const },
];

const Login: React.FC<LoginProps> = ({ onLogin }) => {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError("");

    const user = VALID_USERS.find(
      (u) => u.username === username && u.password === password
    );

    if (!user) {
      setError("Invalid username or password");
      return;
    }

    // success
    onLogin({ username: user.username, role: user.role });
  };

  return (
    <div className="flex flex-col items-center justify-center min-h-screen bg-[#059669] px-4">
      {/* Logo */}
      <div className="flex flex-col items-center mb-8">
        <div className="w-20 h-20 bg-emerald-400/30 rounded-full flex items-center justify-center mb-4 backdrop-blur-sm">
          <Leaf className="w-10 h-10 text-white" />
        </div>
        <h1 className="text-3xl font-bold text-white tracking-wide">EcoFlow</h1>
        <p className="text-emerald-100 text-sm mt-1">
          Smart Irrigation Control
        </p>
      </div>

      {/* Card */}
      <div className="w-full max-w-sm bg-white rounded-xl shadow-2xl p-8">
        <form onSubmit={handleSubmit} className="space-y-6">
          {error && (
            <div className="flex items-center gap-2 bg-red-50 border border-red-200 text-red-700 px-3 py-2 rounded-lg text-sm">
              <AlertTriangle className="w-4 h-4" />
              {error}
            </div>
          )}

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Username
            </label>
            <input
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className="w-full px-4 py-3 rounded-lg border border-gray-300 focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500 outline-none"
              placeholder="admin / viewer"
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
              className="w-full px-4 py-3 rounded-lg border border-gray-300 focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500 outline-none"
              placeholder="admin123 / viewer123"
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
          <p className="text-xs text-gray-400">
            Simulation Mode (no backend auth yet)
          </p>
        </div>
      </div>
    </div>
  );
};

export default Login;
