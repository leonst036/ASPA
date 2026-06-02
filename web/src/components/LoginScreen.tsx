import React, { useState, useEffect } from 'react';
import { KeyRound, Server, AlertTriangle, ShieldCheck, RefreshCw, Github, MessageSquare, Globe, User, ShieldAlert } from 'lucide-react';
import { getSystemStatus, setApiUrl, getSetupStatus, setupAdmin, loginUser, setApiToken } from '../utils/api';

interface LoginScreenProps {
  onLoginSuccess: () => void;
}

export const LoginScreen: React.FC<LoginScreenProps> = ({ onLoginSuccess }) => {
  const [setupRequired, setSetupRequired] = useState(false);
  const [checkingSetup, setCheckingSetup] = useState(true);

  // Sign-in state
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [customUrl, setCustomUrl] = useState('');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  useEffect(() => {
    // Check if setup is required on mount
    getSetupStatus()
      .then((data) => {
        setSetupRequired(data.setupRequired);
      })
      .catch((err) => {
        console.error('Error checking setup status:', err);
      })
      .finally(() => {
        setCheckingSetup(false);
      });
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      if (customUrl.trim()) {
        setApiUrl(customUrl.trim());
      } else {
        localStorage.removeItem('aspa_api_url');
      }

      if (setupRequired) {
        // Run setup admin account
        if (!username.trim() || !password) {
          throw new Error('Username and Password are required');
        }
        const res = await setupAdmin(username.trim(), password);
        setApiToken(res.token);
        setSuccess(true);
        setTimeout(() => {
          onLoginSuccess();
        }, 800);
      } else {
        // Log in using username and password
        if (!username.trim() || !password) {
          throw new Error('Username and Password are required');
        }
        const res = await loginUser(username.trim(), password);
        setApiToken(res.token);
        setSuccess(true);
        setTimeout(() => {
          onLoginSuccess();
        }, 800);
      }
    } catch (err) {
      localStorage.removeItem('aspa_api_token');
      setError(
        err instanceof Error
          ? err.message
          : 'Authentication failed. Please verify credentials.'
      );
    } finally {
      setLoading(false);
    }
  };

  if (checkingSetup) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-plan-lightBg dark:bg-plan-bg font-sans transition-colors duration-300">
        <div className="flex flex-col items-center gap-3 text-plan-cyan animate-pulse">
          <RefreshCw className="w-8 h-8 animate-spin" />
          <span className="text-[10px] font-black uppercase tracking-widest">Checking Setup Status...</span>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-plan-lightBg dark:bg-plan-bg font-sans p-4 relative overflow-hidden transition-colors duration-300">

      {/* Background Gradient Orbs */}
      <div className="absolute top-[-20%] left-[-10%] w-[50%] h-[60%] rounded-full bg-plan-cyan/5 dark:bg-plan-cyan/2 blur-[120px]" />
      <div className="absolute bottom-[-20%] right-[-10%] w-[50%] h-[60%] rounded-full bg-plan-blue/5 dark:bg-plan-blue/2 blur-[120px]" />
      <div className="absolute top-[40%] left-[30%] w-[40%] h-[50%] rounded-full bg-indigo-500/5 dark:bg-indigo-500/2 blur-[140px]" />

      {/* Login Box */}
      <div className="w-full max-w-md bg-white dark:bg-plan-card border border-slate-200 dark:border-slate-800/80 shadow-2xl rounded-2xl p-8 relative z-10 transition-all duration-300">
        <div className="flex flex-col items-center mb-6">
          <div className="w-16 h-16 rounded-2xl bg-gradient-to-tr from-plan-cyan to-plan-blue flex items-center justify-center shadow-lg shadow-plan-cyan/10 mb-4 hover:scale-105 transition-transform select-none">
            <img src="/favicon.png" alt="ASPA Logo" className="w-9 h-9 animate-pulse" />
          </div>
          <h1 className="text-2xl font-black tracking-tight text-slate-800 dark:text-white">
            ASPA Analytics
          </h1>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-1.5 text-center font-semibold uppercase tracking-wider">
            {setupRequired ? 'First Launch Configuration' : 'Minecraft Server Analysis Portal'}
          </p>
        </div>

        {error && (
          <div className="mb-5 p-3.5 rounded-xl bg-red-500/10 border border-red-500/15 flex items-start gap-2.5 text-plan-red text-xs animate-shake">
            <AlertTriangle className="w-4.5 h-4.5 shrink-0 mt-0.5" />
            <div className="font-semibold">
              <span>Authentication Error</span>
              <p className="mt-0.5 opacity-90 font-medium">{error}</p>
            </div>
          </div>
        )}

        {success && (
          <div className="mb-5 p-3.5 rounded-xl bg-emerald-500/10 border border-emerald-500/15 flex items-center gap-2.5 text-emerald-500 text-xs">
            <ShieldCheck className="w-4.5 h-4.5 shrink-0 animate-bounce" />
            <div className="font-semibold">
              <span>{setupRequired ? 'Admin Initialized!' : 'Access Granted!'}</span>
              <p className="mt-0.5 opacity-90 font-medium">Establishing secure portal session...</p>
            </div>
          </div>
        )}

        {setupRequired && (
          <div className="mb-6 p-4 rounded-xl bg-indigo-500/10 border border-indigo-500/15 text-xs text-indigo-600 dark:text-indigo-400 font-semibold flex gap-3">
            <ShieldAlert className="w-5 h-5 shrink-0 mt-0.5" />
            <div>
              <p className="font-black text-indigo-700 dark:text-indigo-300 uppercase tracking-wide">First Launch Setup</p>
              <p className="mt-1 font-medium leading-relaxed opacity-90">Please create the primary **Administrator** account. This account will have full access and the ability to provision other accounts with customized view rights.</p>
            </div>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">

          <>
            <div>
              <label className="block text-[10px] font-black uppercase tracking-widest text-slate-500 mb-1.5 pl-0.5">
                {setupRequired ? 'Create Admin Username' : 'Username'}
              </label>
              <div className="relative">
                <span className="absolute inset-y-0 left-0 pl-3 flex items-center text-slate-400 dark:text-slate-500">
                  <User className="w-4 h-4" />
                </span>
                <input
                  type="text"
                  required
                  placeholder={setupRequired ? 'e.g. administrator' : 'Enter username'}
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  disabled={loading || success}
                  className="w-full pl-9 pr-3 py-2.5 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-900 text-slate-800 dark:text-slate-100 text-sm focus:outline-none focus:ring-2 focus:ring-plan-cyan/40 focus:border-plan-cyan transition-all placeholder:text-slate-400 dark:placeholder:text-slate-500"
                />
              </div>
            </div>

            <div>
              <label className="block text-[10px] font-black uppercase tracking-widest text-slate-500 mb-1.5 pl-0.5">
                {setupRequired ? 'Create Password' : 'Password'}
              </label>
              <div className="relative">
                <span className="absolute inset-y-0 left-0 pl-3 flex items-center text-slate-400 dark:text-slate-500">
                  <KeyRound className="w-4 h-4" />
                </span>
                <input
                  type="password"
                  required
                  placeholder={setupRequired ? 'Set administrative password' : 'Enter password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  disabled={loading || success}
                  className="w-full pl-9 pr-3 py-2.5 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-900 text-slate-800 dark:text-slate-100 text-sm focus:outline-none focus:ring-2 focus:ring-plan-cyan/40 focus:border-plan-cyan transition-all placeholder:text-slate-400 dark:placeholder:text-slate-500"
                />
              </div>
            </div>
          </>

          <button
            type="submit"
            disabled={loading || success}
            className="w-full bg-gradient-to-r from-plan-cyan to-plan-blue hover:from-plan-cyan/95 hover:to-plan-blue/95 text-white font-bold text-xs uppercase tracking-widest py-3 px-4 rounded-xl shadow-md shadow-plan-cyan/10 transition-all active:scale-[0.99] flex items-center justify-center gap-2 hover:shadow-lg disabled:opacity-50 mt-2"
          >
            {loading ? (
              <>
                <RefreshCw className="w-4 h-4 animate-spin text-white" />
                <span>{setupRequired ? 'Provisioning Admin...' : 'Securing Session...'}</span>
              </>
            ) : (
              <>
                <span>{setupRequired ? 'Complete Setup' : 'Secure Sign In'}</span>
              </>
            )}
          </button>
        </form>



        <div className="mt-6 pt-4 border-t border-slate-200 dark:border-slate-800/40 flex flex-col items-center gap-3">
          <span className="text-[9px] text-slate-500 dark:text-slate-500 font-bold uppercase tracking-wider block">
            AES/TLS Enforced Handshake
          </span>
          <div className="flex items-center gap-5">
            <a href="https://github.com" target="_blank" rel="noopener noreferrer" className="text-slate-400 hover:text-slate-600 dark:text-slate-550 dark:hover:text-slate-300 transition-colors" title="GitHub">
              <Github className="w-5 h-5" />
            </a>
            <a href="https://discord.com" target="_blank" rel="noopener noreferrer" className="text-slate-400 hover:text-indigo-500 dark:text-slate-550 dark:hover:text-indigo-400 transition-colors" title="Discord">
              <MessageSquare className="w-5 h-5" />
            </a>
            <a href="https://bsky.app" target="_blank" rel="noopener noreferrer" className="text-slate-400 hover:text-sky-500 dark:text-slate-550 dark:hover:text-sky-400 transition-colors" title="Bluesky">
              <Globe className="w-5 h-5" />
            </a>
          </div>
        </div>
      </div>
    </div>
  );
};
