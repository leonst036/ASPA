import { useState, useEffect } from 'react';
import { 
  Activity, 
  Globe, 
  Search, 
  Sun, 
  Moon, 
  LogOut, 
  Server, 
  TrendingUp,
  Database, 
  Cpu, 
  CheckCircle2, 
  ShieldCheck,
  Menu,
  X,
  Gauge,
  HardDrive,
  Github,
  MessageSquare,
  BookOpen,
  Users
} from 'lucide-react';
import { LoginScreen } from './components/LoginScreen';
import { ServerHealth } from './components/ServerHealth';
import { PlayerAnalytics } from './components/PlayerAnalytics';
import { PlayerInspector } from './components/PlayerInspector';
import { LongtimeGraphs } from './components/LongtimeGraphs';
import { PterodactylDashboard } from './components/PterodactylDashboard';
import { UserManagement } from './components/UserManagement';
import { getApiToken, clearAuth, getSystemStatus, getPterodactylStatus, getCurrentUser } from './utils/api';
import type { SystemStatus, PterodactylStatus } from './types';

function App() {
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(false);
  const [checkingAuth, setCheckingAuth] = useState<boolean>(true);
  const [activeTab, setActiveTab] = useState<'health' | 'analytics' | 'inspector' | 'longtime' | 'pterodactyl' | 'users'>('health');
  const [isDark, setIsDark] = useState<boolean>(false);
  const [status, setStatus] = useState<SystemStatus | null>(null);
  const [pterodactylStatus, setPterodactylStatus] = useState<PterodactylStatus | null>(null);
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState<boolean>(false);
  const [user, setUser] = useState<{ username: string; role: string; permissions: string[] } | null>(null);

  // Initialize Theme and Auth credentials on Mount
  useEffect(() => {
    // 1. Theme Configuration
    const savedTheme = localStorage.getItem('theme');
    const systemPrefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    const shouldBeDark = savedTheme === 'dark' || (!savedTheme && systemPrefersDark);
    
    setIsDark(shouldBeDark);
    document.body.classList.toggle('dark', shouldBeDark);
    document.documentElement.classList.toggle('dark', shouldBeDark);

    // 2. Authentication Check
    const token = getApiToken();
    if (token) {
      // Validate existing token and retrieve user profile
      getCurrentUser()
        .then((userData) => {
          setUser(userData);
          setIsAuthenticated(true);
          getSystemStatus().then(setStatus).catch(console.error);
        })
        .catch(() => {
          // Token expired or invalid
          clearAuth();
          setIsAuthenticated(false);
        })
        .finally(() => {
          setCheckingAuth(false);
        });
    } else {
      setIsAuthenticated(false);
      setCheckingAuth(false);
    }
  }, []);

  // Fetch status info when authenticated
  useEffect(() => {
    if (isAuthenticated) {
      getCurrentUser()
        .then((userData) => {
          setUser(userData);
          // Set default permitted tab
          const allowed = userData.permissions;
          if (allowed.length > 0 && !allowed.includes(activeTab as any) && activeTab !== 'users') {
            if (allowed.includes('health')) setActiveTab('health');
            else if (allowed.includes('analytics')) setActiveTab('analytics');
            else if (allowed.includes('inspector')) setActiveTab('inspector');
            else if (allowed.includes('longtime')) setActiveTab('longtime');
            else if (allowed.includes('pterodactyl') && pterodactylStatus?.enabled) setActiveTab('pterodactyl');
          }
        })
        .catch(console.error);
      getSystemStatus().then(setStatus).catch(console.error);
      getPterodactylStatus().then(setPterodactylStatus).catch(console.error);
    }
  }, [isAuthenticated]);

  // Update browser tab title dynamically based on navigation
  useEffect(() => {
    if (!isAuthenticated) {
      document.title = "ASPA Analytics - Secure Sign In";
      return;
    }
    const tabNames: Record<string, string> = {
      health: "Server Health",
      analytics: "Player Analytics",
      inspector: "Player Inspector",
      longtime: "Longtime Graphs",
      pterodactyl: "Pterodactyl Panel",
      users: "User Management"
    };
    const currentTabName = tabNames[activeTab] || "Dashboard";
    document.title = `ASPA Analytics - ${currentTabName}`;
  }, [activeTab, isAuthenticated]);

  const toggleTheme = () => {
    const nextDark = !isDark;
    setIsDark(nextDark);
    localStorage.setItem('theme', nextDark ? 'dark' : 'light');
    document.body.classList.toggle('dark', nextDark);
    document.documentElement.classList.toggle('dark', nextDark);
  };

  const handleLogout = () => {
    clearAuth();
    setIsAuthenticated(false);
    setStatus(null);
    setUser(null);
  };

  const handleLoginSuccess = () => {
    getCurrentUser()
      .then((userData) => {
        setUser(userData);
        setIsAuthenticated(true);
        getSystemStatus().then(setStatus).catch(console.error);
        getPterodactylStatus().then(setPterodactylStatus).catch(console.error);
      })
      .catch((err) => {
        console.error("Failed to recover profile after login:", err);
        clearAuth();
        setIsAuthenticated(false);
      });
  };

  const formatUptime = (ms: number) => {
    const totalSecs = Math.floor(ms / 1000);
    const days = Math.floor(totalSecs / (24 * 3600));
    const hours = Math.floor((totalSecs % (24 * 3600)) / 3600);
    const mins = Math.floor((totalSecs % 3600) / 60);
    if (days > 0) return `${days}d ${hours}h ${mins}m`;
    return `${hours}h ${mins}m`;
  };

  if (checkingAuth) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-plan-lightBg dark:bg-plan-bg font-sans transition-colors duration-300">
        <div className="flex flex-col items-center gap-3 text-plan-cyan animate-pulse">
          <div className="w-12 h-12 rounded-2xl bg-plan-cyan/15 flex items-center justify-center border border-plan-cyan/35 shadow-lg shadow-plan-cyan/10">
            <ShieldCheck className="w-7 h-7 text-plan-cyan" />
          </div>
          <span className="text-xs font-bold tracking-widest uppercase">Verifying ASPA Security Token...</span>
        </div>
      </div>
    );
  }

  // Display Login Page if not verified
  if (!isAuthenticated) {
    return <LoginScreen onLoginSuccess={handleLoginSuccess} />;
  }

  const getBreadcrumbTitle = () => {
    switch (activeTab) {
      case 'health':
        return 'Server Metrics > Health Status';
      case 'analytics':
        return 'Demographics > Cohort Retention';
      case 'inspector':
        return 'Query Engine > Profile Inspector';
      case 'longtime':
        return 'Telemetry > Longtime Analytics';
      case 'pterodactyl':
        return 'External Node > Pterodactyl Panel';
      case 'users':
        return 'Identity System > Account Registry';
      default:
        return 'Dashboard';
    }
  };

  const getPageTitle = () => {
    switch (activeTab) {
      case 'health':
        return 'Server Hardware & Lag Metrics';
      case 'analytics':
        return 'Player Demographics & Retention';
      case 'inspector':
        return 'Minecraft Profile Inspector';
      case 'longtime':
        return 'Longtime Performance Graphs';
      case 'pterodactyl':
        return 'Pterodactyl Node Management';
      case 'users':
        return 'Dashboard User Management';
      default:
        return 'Dashboard';
    }
  };

  const NavigationLinks = () => {
    const hasPerm = (perm: string) => {
      if (!user) return false;
      if (user.role === 'ADMIN') return true;
      return user.permissions.includes(perm);
    };

    const isAdmin = user?.role === 'ADMIN';

    return (
      <div className="space-y-1.5 px-3 py-5 flex-1 overflow-y-auto">
        <span className="text-[9px] font-black uppercase tracking-[0.2em] text-slate-400 dark:text-slate-500 pl-4 block mb-4 select-none">
          Analytics Navigation
        </span>
        
        {hasPerm('health') && (
          <button
            onClick={() => {
              setActiveTab('health');
              setIsMobileMenuOpen(false);
            }}
            className={`w-full flex items-center gap-3.5 px-4 py-2.5 rounded-xl text-xs font-bold transition-all text-left relative overflow-hidden group ${
              activeTab === 'health'
                ? 'bg-plan-blue/5 text-plan-blue dark:bg-plan-blue/10 dark:text-white font-extrabold shadow-sm'
                : 'text-slate-500 hover:text-slate-900 hover:bg-slate-100/60 dark:text-slate-450 dark:hover:text-white dark:hover:bg-slate-900/40'
            }`}
          >
            {activeTab === 'health' && (
              <div className="absolute left-0 top-1/4 bottom-1/4 w-1 rounded-r bg-plan-blue" />
            )}
            <Activity className={`w-4 h-4 shrink-0 transition-transform duration-300 group-hover:scale-105 ${activeTab === 'health' ? 'text-plan-blue dark:text-cyan-400' : 'text-slate-400 dark:text-slate-500 group-hover:text-slate-600 dark:group-hover:text-slate-355'}`} />
            <div className="flex-1 min-w-0">
              <span className="block truncate">Server Health</span>
              <span className="block text-[8.5px] opacity-60 font-semibold mt-0.5 truncate">TPS, MSPT & hardware monitors</span>
            </div>
          </button>
        )}

        {hasPerm('analytics') && (
          <button
            onClick={() => {
              setActiveTab('analytics');
              setIsMobileMenuOpen(false);
            }}
            className={`w-full flex items-center gap-3.5 px-4 py-2.5 rounded-xl text-xs font-bold transition-all text-left relative overflow-hidden group ${
              activeTab === 'analytics'
                ? 'bg-plan-blue/5 text-plan-blue dark:bg-plan-blue/10 dark:text-white font-extrabold shadow-sm'
                : 'text-slate-500 hover:text-slate-900 hover:bg-slate-100/60 dark:text-slate-450 dark:hover:text-white dark:hover:bg-slate-900/40'
            }`}
          >
            {activeTab === 'analytics' && (
              <div className="absolute left-0 top-1/4 bottom-1/4 w-1 rounded-r bg-plan-blue" />
            )}
            <Globe className={`w-4 h-4 shrink-0 transition-transform duration-300 group-hover:scale-105 ${activeTab === 'analytics' ? 'text-plan-blue dark:text-cyan-400' : 'text-slate-400 dark:text-slate-500 group-hover:text-slate-600 dark:group-hover:text-slate-355'}`} />
            <div className="flex-1 min-w-0">
              <span className="block truncate">Player Analytics</span>
              <span className="block text-[8.5px] opacity-60 font-semibold mt-0.5 truncate">Retention, punchcards & regional data</span>
            </div>
          </button>
        )}

        {hasPerm('inspector') && (
          <button
            onClick={() => {
              setActiveTab('inspector');
              setIsMobileMenuOpen(false);
            }}
            className={`w-full flex items-center gap-3.5 px-4 py-2.5 rounded-xl text-xs font-bold transition-all text-left relative overflow-hidden group ${
              activeTab === 'inspector'
                ? 'bg-plan-blue/5 text-plan-blue dark:bg-plan-blue/10 dark:text-white font-extrabold shadow-sm'
                : 'text-slate-500 hover:text-slate-900 hover:bg-slate-100/60 dark:text-slate-450 dark:hover:text-white dark:hover:bg-slate-900/40'
            }`}
          >
            {activeTab === 'inspector' && (
              <div className="absolute left-0 top-1/4 bottom-1/4 w-1 rounded-r bg-plan-blue" />
            )}
            <Search className={`w-4 h-4 shrink-0 transition-transform duration-300 group-hover:scale-105 ${activeTab === 'inspector' ? 'text-plan-blue dark:text-cyan-400' : 'text-slate-400 dark:text-slate-500 group-hover:text-slate-600 dark:group-hover:text-slate-355'}`} />
            <div className="flex-1 min-w-0">
              <span className="block truncate">Player Inspector</span>
              <span className="block text-[8.5px] opacity-60 font-semibold mt-0.5 truncate">3D skin renders & session lookup</span>
            </div>
          </button>
        )}

        {hasPerm('longtime') && (
          <button
            onClick={() => {
              setActiveTab('longtime');
              setIsMobileMenuOpen(false);
            }}
            className={`w-full flex items-center gap-3.5 px-4 py-2.5 rounded-xl text-xs font-bold transition-all text-left relative overflow-hidden group ${
              activeTab === 'longtime'
                ? 'bg-plan-blue/5 text-plan-blue dark:bg-plan-blue/10 dark:text-white font-extrabold shadow-sm'
                : 'text-slate-500 hover:text-slate-900 hover:bg-slate-100/60 dark:text-slate-450 dark:hover:text-white dark:hover:bg-slate-900/40'
            }`}
          >
            {activeTab === 'longtime' && (
              <div className="absolute left-0 top-1/4 bottom-1/4 w-1 rounded-r bg-plan-blue" />
            )}
            <TrendingUp className={`w-4 h-4 shrink-0 transition-transform duration-300 group-hover:scale-105 ${activeTab === 'longtime' ? 'text-plan-blue dark:text-cyan-400' : 'text-slate-400 dark:text-slate-500 group-hover:text-slate-600 dark:group-hover:text-slate-355'}`} />
            <div className="flex-1 min-w-0">
              <span className="block truncate">Longtime Graphs</span>
              <span className="block text-[8.5px] opacity-60 font-semibold mt-0.5 truncate">Historical trends & resolutions</span>
            </div>
          </button>
        )}

        {hasPerm('pterodactyl') && pterodactylStatus?.enabled && (
          <button
            onClick={() => {
              setActiveTab('pterodactyl');
              setIsMobileMenuOpen(false);
            }}
            className={`w-full flex items-center gap-3.5 px-4 py-2.5 rounded-xl text-xs font-bold transition-all text-left relative overflow-hidden group ${
              activeTab === 'pterodactyl'
                ? 'bg-plan-blue/5 text-plan-blue dark:bg-plan-blue/10 dark:text-white font-extrabold shadow-sm'
                : 'text-slate-500 hover:text-slate-900 hover:bg-slate-100/60 dark:text-slate-450 dark:hover:text-white dark:hover:bg-slate-900/40'
            }`}
          >
            {activeTab === 'pterodactyl' && (
              <div className="absolute left-0 top-1/4 bottom-1/4 w-1 rounded-r bg-plan-blue" />
            )}
            <HardDrive className={`w-4 h-4 shrink-0 transition-transform duration-300 group-hover:scale-105 ${activeTab === 'pterodactyl' ? 'text-plan-blue dark:text-cyan-400' : 'text-slate-400 dark:text-slate-500 group-hover:text-slate-600 dark:group-hover:text-slate-355'}`} />
            <div className="flex-1 min-w-0">
              <span className="block truncate">Pterodactyl Panel</span>
              <span className="block text-[8.5px] opacity-60 font-semibold mt-0.5 truncate">Remote console, backups & power</span>
            </div>
          </button>
        )}

        {/* User management link for admins */}
        {isAdmin && (
          <button
            onClick={() => {
              setActiveTab('users');
              setIsMobileMenuOpen(false);
            }}
            className={`w-full flex items-center gap-3.5 px-4 py-2.5 rounded-xl text-xs font-bold transition-all text-left relative overflow-hidden group ${
              activeTab === 'users'
                ? 'bg-plan-blue/5 text-plan-blue dark:bg-plan-blue/10 dark:text-white font-extrabold shadow-sm'
                : 'text-slate-500 hover:text-slate-900 hover:bg-slate-100/60 dark:text-slate-450 dark:hover:text-white dark:hover:bg-slate-900/40'
            }`}
          >
            {activeTab === 'users' && (
              <div className="absolute left-0 top-1/4 bottom-1/4 w-1 rounded-r bg-plan-blue" />
            )}
            <Users className={`w-4 h-4 shrink-0 transition-transform duration-300 group-hover:scale-105 ${activeTab === 'users' ? 'text-plan-blue dark:text-cyan-400' : 'text-slate-400 dark:text-slate-500 group-hover:text-slate-600 dark:group-hover:text-slate-355'}`} />
            <div className="flex-1 min-w-0">
              <span className="block truncate">User Accounts</span>
              <span className="block text-[8.5px] opacity-60 font-semibold mt-0.5 truncate">Provision users & set rights</span>
            </div>
          </button>
        )}

        <span className="text-[9px] font-black uppercase tracking-[0.2em] text-slate-400 dark:text-slate-500 pl-4 block mt-6 mb-4 select-none">
          Help & Support
        </span>

        <a
          href="https://github.com"
          target="_blank"
          rel="noopener noreferrer"
          className="w-full flex items-center gap-3.5 px-4 py-2.5 rounded-xl text-xs font-bold text-slate-500 hover:text-slate-900 hover:bg-slate-100/60 dark:text-slate-450 dark:hover:text-white dark:hover:bg-slate-900/40 transition-all text-left group"
        >
          <BookOpen className="w-4 h-4 shrink-0 transition-transform duration-300 group-hover:scale-105 text-slate-400 dark:text-slate-500 group-hover:text-slate-650 dark:group-hover:text-slate-350" />
          <div className="flex-grow min-w-0">
            <span className="block truncate">Documentation</span>
            <span className="block text-[8.5px] opacity-60 font-semibold mt-0.5 truncate">Plugin APIs & developer guides</span>
          </div>
        </a>

        <a
          href="https://discord.com"
          target="_blank"
          rel="noopener noreferrer"
          className="w-full flex items-center gap-3.5 px-4 py-2.5 rounded-xl text-xs font-bold text-slate-500 hover:text-slate-900 hover:bg-slate-100/60 dark:text-slate-450 dark:hover:text-white dark:hover:bg-slate-900/40 transition-all text-left group"
        >
          <MessageSquare className="w-4 h-4 shrink-0 transition-transform duration-300 group-hover:scale-105 text-slate-400 dark:text-slate-500 group-hover:text-slate-650 dark:group-hover:text-slate-350" />
          <div className="flex-grow min-w-0">
            <span className="block truncate">Community & Support</span>
            <span className="block text-[8.5px] opacity-60 font-semibold mt-0.5 truncate">Join Discord and connect</span>
          </div>
        </a>
      </div>
    );
  };

  return (
    <div className="min-h-screen bg-plan-lightBg dark:bg-plan-bg font-sans text-slate-800 dark:text-slate-100 transition-colors duration-300 flex relative overflow-x-hidden">
      
      {/* Background Gradient Blurs (Gives subtle premium styling) */}
      <div className="absolute top-[-30%] left-[20%] w-[60%] h-[70%] rounded-full bg-plan-cyan/5 dark:bg-plan-cyan/2 blur-[160px] pointer-events-none z-0" />
      <div className="absolute bottom-[-20%] right-[-10%] w-[50%] h-[60%] rounded-full bg-plan-blue/5 dark:bg-plan-blue/2 blur-[160px] pointer-events-none z-0" />

      {/* 1. Permanent Left Vertical Sidebar (Desktop View) */}
      <aside className="hidden lg:flex flex-col w-64 bg-white dark:bg-plan-sidebar border-r border-slate-200 dark:border-slate-800/80 text-slate-805 dark:text-slate-100 z-30 shrink-0 select-none shadow-sm dark:shadow-xl h-screen fixed left-0 top-0">
        {/* Sidebar Brand Header */}
        <div className="h-16 flex items-center gap-3 px-6 border-b border-slate-200 dark:border-slate-800/40 bg-slate-50/50 dark:bg-slate-950/40">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-plan-cyan to-plan-blue flex items-center justify-center shadow-md shadow-plan-cyan/20 select-none">
            <img src="/favicon.png" alt="ASPA logo" className="w-5.5 h-5.5 animate-pulse" />
          </div>
          <div>
            <div className="flex items-center gap-1.5">
              <h1 className="text-base font-black tracking-tight text-slate-800 dark:text-white">
                ASPA Analytics
              </h1>
              <span className="bg-plan-cyan/15 text-plan-cyan px-1.5 py-0.2 rounded text-[8px] font-black uppercase tracking-wider border border-plan-cyan/20">
                PLAN
              </span>
            </div>
            <div className="flex items-center gap-1.5 mt-0.5">
              <div className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
              <span className="text-[9px] text-slate-500 dark:text-slate-450 font-bold uppercase tracking-wider">
                {user ? `User: ${user.username}` : 'Ecosystem Online'}
              </span>
            </div>
          </div>
        </div>

        {/* Sidebar Nav Items */}
        <NavigationLinks />

        {/* Sidebar Footer Controls */}
        <div className="p-4 border-t border-slate-200 dark:border-slate-800/85 bg-slate-50/50 dark:bg-slate-950/20 space-y-3">
          <div className="flex items-center justify-between gap-2">
            <button
              onClick={toggleTheme}
              className="flex-1 flex items-center justify-center gap-2 py-2 px-3 rounded-lg border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900/40 text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 hover:text-slate-900 dark:hover:text-white transition-all text-[11px] font-bold shadow-sm"
              title={isDark ? "Switch to Light Mode" : "Switch to Dark Mode"}
            >
              {isDark ? (
                <>
                  <Sun className="w-3.5 h-3.5 text-amber-450" />
                  <span>Light Theme</span>
                </>
              ) : (
                <>
                  <Moon className="w-3.5 h-3.5 text-indigo-400" />
                  <span>Dark Theme</span>
                </>
              )}
            </button>

            <button
              onClick={handleLogout}
              className="py-2 px-3 rounded-lg border border-rose-200 dark:border-rose-900/30 bg-rose-50 dark:bg-rose-950/20 text-rose-600 dark:text-rose-450 hover:text-rose-800 dark:hover:text-rose-200 hover:bg-rose-100 dark:hover:bg-rose-900/40 transition-all flex items-center justify-center shadow-sm"
              title="Logout Session"
            >
              <LogOut className="w-3.5 h-3.5" />
            </button>
          </div>

          <div className="text-center space-y-2">
            <span className="text-[8px] text-slate-400 dark:text-slate-550 font-bold uppercase tracking-widest block">ASPA SYSTEM CORE v1.0.0</span>
            <div className="flex items-center justify-center gap-4 pt-1.5 border-t border-slate-200/50 dark:border-slate-800/40">
              <a href="https://github.com" target="_blank" rel="noopener noreferrer" className="text-slate-400 hover:text-slate-600 dark:text-slate-500 dark:hover:text-slate-300 transition-colors" title="GitHub">
                <Github className="w-4 h-4" />
              </a>
              <a href="https://discord.com" target="_blank" rel="noopener noreferrer" className="text-slate-400 hover:text-indigo-500 dark:text-slate-500 dark:hover:text-indigo-400 transition-colors" title="Discord">
                <MessageSquare className="w-4 h-4" />
              </a>
              <a href="https://bsky.app" target="_blank" rel="noopener noreferrer" className="text-slate-400 hover:text-sky-500 dark:text-slate-500 dark:hover:text-sky-400 transition-colors" title="Bluesky">
                <Globe className="w-4 h-4" />
              </a>
            </div>
          </div>
        </div>
      </aside>

      {/* Spacer to prevent main content from shifting under fixed sidebar */}
      <div className="hidden lg:block w-64 shrink-0" />

      {/* 2. Responsive Mobile Sidebar Drawer Overlay */}
      {isMobileMenuOpen && (
        <div className="lg:hidden fixed inset-0 bg-black/60 backdrop-blur-sm z-40 transition-all duration-300">
          <div className="w-64 h-full bg-white dark:bg-slate-900 text-slate-800 dark:text-slate-100 flex flex-col z-50 relative shadow-2xl animate-slide-right border-r border-slate-200 dark:border-slate-800">
            {/* Mobile Header */}
            <div className="h-16 flex items-center justify-between px-5 border-b border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-950/40">
              <div className="flex items-center gap-2.5">
                <div className="w-8 h-8 rounded-lg bg-gradient-to-tr from-plan-cyan to-plan-blue flex items-center justify-center select-none">
                  <img src="/favicon.png" alt="ASPA logo" className="w-5.5 h-5.5 animate-pulse" />
                </div>
                <div>
                  <h1 className="text-sm font-black tracking-tight text-slate-800 dark:text-white">ASPA Analytics</h1>
                  <span className="text-[8px] text-emerald-600 dark:text-emerald-400 font-bold uppercase tracking-wide">Operational</span>
                </div>
              </div>
              <button 
                onClick={() => setIsMobileMenuOpen(false)}
                className="p-1.5 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-white transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Mobile Nav Links */}
            <NavigationLinks />

            {/* Mobile Controls */}
            <div className="p-4 border-t border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-950/20 space-y-3">
              <button
                onClick={toggleTheme}
                className="w-full flex items-center justify-center gap-2 py-2 px-3 rounded-lg border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900/40 text-slate-650 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 transition-all text-xs font-bold shadow-sm"
              >
                {isDark ? <Sun className="w-4 h-4 text-amber-450" /> : <Moon className="w-4 h-4 text-indigo-400" />}
                <span>Theme Mode</span>
              </button>
              <button
                onClick={handleLogout}
                className="w-full flex items-center justify-center gap-2 py-2 px-3 rounded-lg border border-rose-200 dark:border-rose-900/30 bg-rose-50 dark:bg-rose-950/20 text-rose-600 dark:text-rose-400 hover:bg-rose-100 dark:hover:bg-rose-900/40 transition-all text-xs font-bold shadow-sm"
              >
                <LogOut className="w-4 h-4" />
                <span>Sign Out</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 3. Main Body Scroll Container */}
      <div className="flex-grow flex flex-col min-h-screen relative z-10 w-full overflow-y-auto">
        
        {/* Top Header Navbar */}
        <header className="h-16 border-b border-slate-200/60 dark:border-slate-800/50 bg-white/80 dark:bg-plan-card/85 backdrop-blur-xl flex items-center justify-between px-4 md:px-6 sticky top-0 z-30 transition-all duration-300">
          <div className="flex items-center gap-3">
            {/* Hamburger Button (Mobile View only) */}
            <button
              onClick={() => setIsMobileMenuOpen(true)}
              className="lg:hidden p-2 rounded-lg border border-slate-200 dark:border-slate-800 hover:bg-slate-50 dark:hover:bg-slate-800 text-slate-500 dark:text-slate-450 transition-all active:scale-[0.98]"
            >
              <Menu className="w-4.5 h-4.5" />
            </button>

            {/* Mobile Logo Branding */}
            <div className="lg:hidden w-8 h-8 rounded-lg bg-gradient-to-tr from-plan-cyan to-plan-blue flex items-center justify-center shadow-md shadow-plan-cyan/15 select-none shrink-0">
              <img src="/favicon.png" alt="ASPA logo" className="w-5 h-5 animate-pulse" />
            </div>

            <div>
              <span className="hidden sm:block text-[8px] text-slate-400 dark:text-slate-500 font-bold uppercase tracking-[0.15em] select-none">
                {getBreadcrumbTitle()}
              </span>
              <h2 className="text-xs sm:text-sm font-black text-slate-800 dark:text-white tracking-tight mt-0.5 select-none">
                {getPageTitle()}
              </h2>
            </div>
          </div>

          {/* Center Server Badges (Desktop View) - Cleaner minimalistic style */}
          {status && (
            <div className="hidden md:flex items-center gap-6 text-[9.5px] text-slate-500 dark:text-slate-400 font-bold select-none">
              <div className="flex items-center gap-2">
                <Server className="w-3.5 h-3.5 text-slate-400 dark:text-slate-500" />
                <span className="opacity-80">Uptime:</span>
                <span className="font-extrabold text-slate-750 dark:text-slate-200">{formatUptime(status.uptimeMs)}</span>
              </div>
              
              <div className="h-4 w-px bg-slate-200 dark:bg-slate-800" />

              <div className="flex items-center gap-2">
                <Database className="w-3.5 h-3.5 text-slate-400 dark:text-slate-500" />
                <span className="opacity-80">Database:</span>
                <span className="font-extrabold text-slate-750 dark:text-slate-200 uppercase">{status.databaseDriver}</span>
              </div>

              <div className="h-4 w-px bg-slate-200 dark:bg-slate-800" />

              <div className="flex items-center gap-2">
                <Cpu className="w-3.5 h-3.5 text-slate-400 dark:text-slate-500" />
                <span className="opacity-80">Activity:</span>
                <span className="font-extrabold text-slate-750 dark:text-slate-200">{status.totalPlayersTracked} Tracked Players</span>
              </div>
            </div>
          )}

          {/* Right Mobile Metric / Theme Indicators */}
          <div className="flex items-center gap-2">
            <span className="bg-emerald-500/5 text-emerald-600 dark:text-emerald-450 px-2.5 py-1 rounded-full text-[8.5px] font-black uppercase tracking-wider border border-emerald-500/10 flex items-center gap-1 select-none">
              <div className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" /> Live Link
            </span>
          </div>
        </header>

        {/* Content Wrapper */}
        <main className="flex-grow p-4 md:p-6 lg:p-8 space-y-6 w-full max-w-7xl mx-auto">
          {activeTab === 'health' && user?.permissions.includes('health') && <ServerHealth />}
          {activeTab === 'analytics' && user?.permissions.includes('analytics') && <PlayerAnalytics />}
          {activeTab === 'inspector' && user?.permissions.includes('inspector') && <PlayerInspector />}
          {activeTab === 'longtime' && user?.permissions.includes('longtime') && <LongtimeGraphs />}
          {activeTab === 'pterodactyl' && pterodactylStatus?.enabled && user?.permissions.includes('pterodactyl') && <PterodactylDashboard />}
          {activeTab === 'users' && user?.role === 'ADMIN' && <UserManagement />}
        </main>
      </div>

    </div>
  );
}

export default App;
