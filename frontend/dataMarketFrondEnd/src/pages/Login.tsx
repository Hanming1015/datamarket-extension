import { useState } from 'react';
import { Shield, Eye, EyeOff, Loader } from 'lucide-react';
import { authApi } from '../services/api';
import { Toast } from '../components/Toast';

interface LoginProps {
  onLoginSuccess: (token: string, user: any) => void;
  onSwitchToRegister: () => void;
}

export default function Login({ onLoginSuccess, onSwitchToRegister }: LoginProps) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [toast, setToast] = useState<{show: boolean, message: string, type: 'success' | 'error' | 'warning' | 'info'}>({ show: false, message: '', type: 'info' });

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!username.trim() || !password.trim()) {
      setToast({ show: true, message: 'Please enter username and password', type: 'warning' });
      return;
    }

    setLoading(true);
    try {
      const response = await authApi.login(username, password);
      const { token, user } = response.data;

      localStorage.setItem('token', token);

      setToast({ show: true, message: 'Login successful!', type: 'success' });
      setTimeout(() => {
        onLoginSuccess(token, user);
      }, 500);
    } catch (error: any) {
      const errorMsg = error.response?.data?.message || 'Login failed. Please check your credentials.';
      setToast({ show: true, message: errorMsg, type: 'error' });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-600 to-purple-600 flex items-center justify-center px-4">
      {toast.show && (
        <Toast 
          message={toast.message} 
          type={toast.type} 
          onClose={() => setToast({ ...toast, show: false })} 
        />
      )}

      <div className="w-full max-w-md">
        <div className="bg-white rounded-2xl shadow-2xl p-8">
          <div className="flex justify-center mb-6">
            <div className="w-16 h-16 bg-gradient-to-br from-blue-600 to-purple-600 rounded-2xl flex items-center justify-center">
              <Shield className="w-8 h-8 text-white" />
            </div>
          </div>

          <h1 className="text-3xl font-bold text-gray-900 text-center mb-2">ConsentHub</h1>
          <p className="text-gray-600 text-center mb-8">Data Sharing Platform</p>

          <form onSubmit={handleLogin} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">Username</label>
              <input
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="Enter your username"
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none transition"
                disabled={loading}
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">Password</label>
              <div className="relative">
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Enter your password"
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none transition pr-10"
                  disabled={loading}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-600 hover:text-gray-900"
                  disabled={loading}
                >
                  {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full bg-gradient-to-r from-blue-600 to-purple-600 text-white py-2 rounded-lg font-semibold hover:shadow-lg transition-all disabled:opacity-70 disabled:cursor-not-allowed flex items-center justify-center gap-2"
            >
              {loading && <Loader className="w-4 h-4 animate-spin" />}
              {loading ? 'Logging in...' : 'Sign In'}
            </button>
          </form>

          <div className="mt-6 pt-6 border-t border-gray-200">
            <p className="text-center text-gray-600 text-sm">
              Don't have an account?{' '}
              <button
                onClick={onSwitchToRegister}
                className="text-blue-600 hover:text-blue-700 font-semibold"
              >
                Sign up here
              </button>
            </p>
          </div>
        </div>

        <div className="mt-6 text-center text-white text-sm">
          <p>Demo credentials available</p>
          <p className="text-gray-200 mt-1">Contact support for access</p>
        </div>
      </div>
    </div>
  );
}
