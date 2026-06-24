import { useState, useEffect } from 'react';
import { Shield, ShoppingCart, FileCheck, DollarSign, Menu, X, LogOut } from 'lucide-react';
import ConsentManagement from './pages/ConsentManagement';
import DataMarket from './pages/DataMarket';
import AuditLog from './pages/AuditLog';
import Billing from './pages/Billing';
import DatasetManagement from './pages/DatasetManagement';
import Login from './pages/Login';
import Register from './pages/Register';
import { authApi } from './services/api';
import { Database } from 'lucide-react';

type Page = 'consent' | 'market' | 'audit' | 'billing' | 'dataset';
type AuthPage = 'login' | 'register';

function App() {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [user, setUser] = useState<any>(null);
  const [authPage, setAuthPage] = useState<AuthPage>('login');
  const [currentPage, setCurrentPage] = useState<Page>('consent');
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  useEffect(() => {
    // Check if user is already logged in
    const checkAuth = async () => {
      const token = localStorage.getItem('token');
      if (token) {
        try {
          const response = await authApi.getInfo();
          console.log('🟢 [Token Verified] Fetched user info from backend:', response.data);
          setUser(response.data);
          setCurrentPage(response.data.role === 'owner' ? 'dataset' : 'market');
          setIsAuthenticated(true);
        } catch (error) {
          console.error('🔴 [Token Invalid] Failed to fetch user info:', error);
          handleLogout();
        }
      }
    };
    checkAuth();
  }, []);

  const handleLoginSuccess = (token: string, userData: any) => {
    console.log('🟢 [Login Success] User data received:', userData);
    setIsAuthenticated(true);
    setUser(userData);
    setCurrentPage(userData?.role === 'owner' ? 'dataset' : 'market');
  };

  const handleLogout = () => {
    localStorage.removeItem('token');
    setIsAuthenticated(false);
    setUser(null);
  };

  const handleRegisterSuccess = () => {
    setAuthPage('login');
  };

  if (!isAuthenticated) {
    return authPage === 'login' ? (
      <Login
        onLoginSuccess={handleLoginSuccess}
        onSwitchToRegister={() => setAuthPage('register')}
      />
    ) : (
      <Register
        onRegisterSuccess={handleRegisterSuccess}
        onSwitchToLogin={() => setAuthPage('login')}
      />
    );
  }

  const navigation = [
    { id: 'dataset' as Page, name: 'Dataset Management', icon: Database, description: 'Manage Datasets' },
    { id: 'consent' as Page, name: 'Consent Management', icon: Shield, description: 'Data Owner View' },
    { id: 'market' as Page, name: 'Data Market', icon: ShoppingCart, description: 'Consumer View' },
    { id: 'audit' as Page, name: 'Audit Log', icon: FileCheck, description: 'Activity History' },
    { id: 'billing' as Page, name: 'Billing', icon: DollarSign, description: 'Usage & Revenue' }
  ];

  const renderPage = () => {
    switch (currentPage) {
      case 'dataset':
        return <DatasetManagement user={user} />;
      case 'consent':
        return <ConsentManagement user={user} />;
      case 'market':
        return <DataMarket user={user} />;
      case 'audit':
        return <AuditLog user={user} />;
      case 'billing':
        return <Billing user={user} />;
      default:
        return <ConsentManagement user={user} />;
    }
  };

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col">
      <nav className="bg-white border-b border-gray-200 sticky top-0 z-40 shadow-sm">
        <div className="max-w-[90rem] mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16">
            <div className="flex items-center min-w-fit">
              <div className="flex items-center gap-2 lg:gap-3 cursor-pointer" onClick={() => setCurrentPage('consent')}>
                <div className="w-8 h-8 lg:w-10 lg:h-10 bg-gradient-to-br from-blue-600 to-purple-600 rounded-lg flex items-center justify-center flex-shrink-0">
                  <Shield className="w-5 h-5 lg:w-6 lg:h-6 text-white" />
                </div>
                <div className="whitespace-nowrap">
                  <h1 className="text-lg lg:text-xl font-bold text-gray-900 leading-tight">ConsentHub</h1>
                  <p className="text-[10px] lg:text-xs text-gray-600 leading-tight hidden sm:block">Data Sharing Platform</p>
                </div>
              </div>
            </div>

            <div className="hidden lg:flex items-center gap-1 lg:gap-2">
              {navigation.map((item) => {
                const Icon = item.icon;
                const isActive = currentPage === item.id;
                return (
                  <button
                    key={item.id}
                    onClick={() => setCurrentPage(item.id)}
                    className={`flex items-center gap-1.5 px-3 py-2 rounded-lg transition-all ${
                      isActive
                        ? 'bg-blue-50 text-blue-700 font-semibold'
                        : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900'
                    }`}
                  >
                    <Icon className="w-4 h-4 flex-shrink-0" />
                    <span className="text-sm whitespace-nowrap">{item.name}</span>
                  </button>
                );
              })}
            </div>

            <div className="flex items-center gap-3 md:gap-4 pl-4">
              <div className="hidden md:flex items-center text-sm whitespace-nowrap">
                <span className="text-gray-600">Welcome, <span className="font-semibold text-gray-900">{user?.name || user?.username}</span></span>
              </div>
              <button
                onClick={handleLogout}
                className="flex items-center gap-1.5 px-3 py-2 text-red-600 hover:bg-red-50 rounded-lg transition-colors whitespace-nowrap"
              >
                <LogOut className="w-4 h-4" />
                <span className="text-sm font-medium">Logout</span>
              </button>
              <div className="lg:hidden flex items-center">
                <button
                  onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
                  className="p-2 rounded-lg text-gray-600 hover:bg-gray-100"
                >
                  {mobileMenuOpen ? <X className="w-6 h-6" /> : <Menu className="w-6 h-6" />}
                </button>
              </div>
            </div>
          </div>
        </div>

        {mobileMenuOpen && (
          <div className="md:hidden border-t border-gray-200 bg-white">
            <div className="px-4 py-3 space-y-2">
              {navigation.map((item) => {
                const Icon = item.icon;
                const isActive = currentPage === item.id;
                return (
                  <button
                    key={item.id}
                    onClick={() => {
                      setCurrentPage(item.id);
                      setMobileMenuOpen(false);
                    }}
                    className={`w-full flex items-center gap-3 px-4 py-3 rounded-lg transition-all ${isActive
                        ? 'bg-blue-50 text-blue-700 font-semibold'
                        : 'text-gray-700 hover:bg-gray-100'
                      }`}
                  >
                    <Icon className="w-5 h-5" />
                    <div className="text-left">
                      <p className="text-sm">{item.name}</p>
                      <p className="text-xs text-gray-500">{item.description}</p>
                    </div>
                  </button>
                );
              })}
            </div>
          </div>
        )}
      </nav>

      <main className="max-w-[90rem] mx-auto px-4 sm:px-6 lg:px-8 py-8 flex-grow w-full">
        {renderPage()}
      </main>

      <footer className="bg-white border-t border-gray-200 mt-auto">
        <div className="max-w-[90rem] mx-auto px-4 sm:px-6 lg:px-8 py-6">
          <div className="flex flex-col md:flex-row justify-between items-center gap-4">
            <div className="text-sm text-gray-600">
              <span className="font-semibold">ConsentHub</span> - Consent-Driven Data Sharing Platform
            </div>
            <div className="flex items-center gap-4 text-xs text-gray-500">
              <span className="bg-green-100 text-green-700 px-2 py-1 rounded-full font-medium">GDPR Compliant</span>
              <span className="bg-blue-100 text-blue-700 px-2 py-1 rounded-full font-medium">Secure</span>
              <span className="bg-purple-100 text-purple-700 px-2 py-1 rounded-full font-medium">Transparent</span>
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
}

export default App;
