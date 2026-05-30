import React, { useState, useEffect } from 'react';
import { 
  Server, 
  Cpu, 
  HardDrive, 
  Activity, 
  Terminal, 
  Play, 
  Square, 
  RotateCcw, 
  Plus, 
  Trash2, 
  Download, 
  RefreshCw, 
  AlertTriangle, 
  CheckCircle2, 
  Power, 
  Settings, 
  ExternalLink,
  ChevronRight
} from 'lucide-react';
import { 
  getPterodactylStatus, 
  sendPterodactylPower, 
  sendPterodactylCommand, 
  getPterodactylBackups, 
  createPterodactylBackup, 
  deletePterodactylBackup, 
  getPterodactylBackupDownload 
} from '../utils/api';
import type { PterodactylStatus, PterodactylBackup } from '../types';

export const PterodactylDashboard: React.FC = () => {
  const [status, setStatus] = useState<PterodactylStatus | null>(null);
  const [backups, setBackups] = useState<PterodactylBackup[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [command, setCommand] = useState<string>('');
  const [consoleLogs, setConsoleLogs] = useState<string[]>([]);
  const [submittingCommand, setSubmittingCommand] = useState<boolean>(false);
  const [submittingPower, setSubmittingPower] = useState<boolean>(false);
  const [creatingBackup, setCreatingBackup] = useState<boolean>(false);
  const [actionLoading, setActionLoading] = useState<string | null>(null); // Track uuid of backup being downloaded/deleted
  const [activeTab, setActiveTab] = useState<'system' | 'backups' | 'console'>('system');

  // Load Status and Backups
  const loadData = async (silent = false) => {
    if (!silent) setLoading(true);
    setError(null);
    try {
      const statusData = await getPterodactylStatus();
      setStatus(statusData);
      
      if (statusData.enabled) {
        const backupsData = await getPterodactylBackups();
        // Sort backups by created time desc
        backupsData.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
        setBackups(backupsData);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to connect to Pterodactyl API');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
    // Poll resources every 8 seconds if enabled
    const interval = setInterval(() => {
      loadData(true);
    }, 8000);
    return () => clearInterval(interval);
  }, []);

  const handlePowerAction = async (signal: 'start' | 'stop' | 'restart' | 'kill') => {
    setSubmittingPower(true);
    try {
      await sendPterodactylPower(signal);
      setConsoleLogs(prev => [...prev, `[System] Sent power signal: ${signal.toUpperCase()}`]);
      // Immediately request resource update
      setTimeout(() => loadData(true), 1500);
    } catch (err) {
      setError(err instanceof Error ? err.message : `Failed to execute ${signal}`);
    } finally {
      setSubmittingPower(false);
    }
  };

  const handleSendCommand = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!command.trim()) return;

    setSubmittingCommand(true);
    const cmdText = command.trim();
    setCommand('');
    
    // Add command to log
    setConsoleLogs(prev => [...prev, `> ${cmdText}`]);
    
    try {
      await sendPterodactylCommand(cmdText);
      setConsoleLogs(prev => [...prev, `[System] Executed command: ${cmdText}`]);
    } catch (err) {
      setConsoleLogs(prev => [...prev, `[Error] ${err instanceof Error ? err.message : 'Command execution failed'}`]);
    } finally {
      setSubmittingCommand(false);
    }
  };

  const handleCreateBackup = async () => {
    setCreatingBackup(true);
    try {
      await createPterodactylBackup();
      // Reload backups list
      await loadData(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Backup creation failed');
    } finally {
      setCreatingBackup(false);
    }
  };

  const handleDeleteBackup = async (uuid: string) => {
    if (!confirm('Are you sure you want to delete this backup? This action is permanent!')) return;
    setActionLoading(uuid);
    try {
      await deletePterodactylBackup(uuid);
      setBackups(prev => prev.filter(b => b.uuid !== uuid));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Backup deletion failed');
    } finally {
      setActionLoading(null);
    }
  };

  const handleDownloadBackup = async (uuid: string) => {
    setActionLoading(uuid);
    try {
      const res = await getPterodactylBackupDownload(uuid);
      if (res && res.url) {
        window.open(res.url, '_blank');
      } else {
        throw new Error('Download URL empty');
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Download generation failed');
    } finally {
      setActionLoading(null);
    }
  };

  const formatSize = (bytes: number) => {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  const formatDate = (dateStr: string) => {
    try {
      const d = new Date(dateStr);
      return d.toLocaleString();
    } catch (e) {
      return dateStr;
    }
  };

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center py-16 text-plan-cyan animate-pulse">
        <RefreshCw className="w-8 h-8 animate-spin mb-3" />
        <span className="text-xs font-bold uppercase tracking-widest">Loading Pterodactyl Dashboard...</span>
      </div>
    );
  }

  // Not Configured State
  if (status && !status.enabled) {
    return (
      <div className="bg-white dark:bg-plan-card border border-slate-200 dark:border-slate-800/80 rounded-xl p-8 relative overflow-hidden transition-all duration-300">
        <div className="absolute top-0 right-0 w-24 h-24 bg-plan-cyan/5 rounded-bl-full pointer-events-none" />
        
        <div className="flex items-start gap-4 mb-6">
          <div className="w-12 h-12 rounded-xl bg-plan-cyan/10 border border-plan-cyan/20 flex items-center justify-center shrink-0">
            <Settings className="w-6 h-6 text-plan-cyan animate-spin-slow" />
          </div>
          <div>
            <span className="text-[10px] font-black uppercase tracking-widest text-slate-400 dark:text-slate-500">Integration Available</span>
            <h3 className="text-lg font-black text-slate-800 dark:text-white mt-0.5">Pterodactyl Panel API</h3>
            <p className="text-xs text-slate-500 dark:text-slate-450 mt-1 max-w-2xl leading-relaxed">
              Unlock absolute server control. Integrating Pterodactyl with ASPA allows you to trigger on-demand backups, send remote power cycles, execute commands securely, and monitor host hardware metrics directly inside this control panel.
            </p>
          </div>
        </div>

        <div className="p-4 rounded-lg bg-slate-50 dark:bg-slate-900/60 border border-slate-200/60 dark:border-slate-800/60 mb-6">
          <div className="flex gap-2.5 text-amber-500 dark:text-amber-400 text-xs font-bold mb-3">
            <AlertTriangle className="w-4.5 h-4.5 shrink-0" />
            <span>Currently Disabled / Misconfigured</span>
          </div>
          <p className="text-[11px] text-slate-650 dark:text-slate-400 leading-relaxed pl-7">
            ASPA detected that the integration is inactive. To activate this dashboard, please open the plugin's configuration file <code>config.yml</code> inside your Minecraft server, configure your credentials under the <code>pterodactyl</code> section, and reload the plugin using <code>/aspa reload</code>.
          </p>
        </div>

        <h4 className="text-[10px] font-black uppercase tracking-widest text-slate-500 dark:text-slate-400 mb-2 pl-0.5">Example Configuration Setup</h4>
        <pre className="bg-slate-900 dark:bg-slate-950 text-[11px] font-mono text-cyan-400 p-4 rounded-lg border border-slate-800/80 shadow-inner overflow-x-auto select-all leading-relaxed">
{`# Pterodactyl API Integration
pterodactyl:
  enabled: true
  # Base URL of your Pterodactyl panel
  url: "https://panel.myhosting.com"
  # Client API Key (starts with ptlc_...)
  api-token: "ptlc_XyZ123SecureClientKey"
  # The unique alphanumeric identifier for this server (usually 8 characters)
  server-id: "8f4b52c0"`}
        </pre>
      </div>
    );
  }

  const resources = status?.resources;
  const serverState = resources?.state || 'unknown';

  const getStateBadge = () => {
    switch (serverState) {
      case 'running':
        return <span className="bg-emerald-500/10 text-emerald-500 px-2 py-0.5 rounded text-[10px] font-black uppercase tracking-wider border border-emerald-500/20">Running</span>;
      case 'offline':
        return <span className="bg-rose-500/10 text-rose-500 px-2 py-0.5 rounded text-[10px] font-black uppercase tracking-wider border border-rose-500/20">Offline</span>;
      case 'starting':
        return <span className="bg-amber-500/10 text-amber-500 px-2 py-0.5 rounded text-[10px] font-black uppercase tracking-wider border border-amber-500/20 animate-pulse">Starting</span>;
      case 'stopping':
        return <span className="bg-amber-500/10 text-amber-500 px-2 py-0.5 rounded text-[10px] font-black uppercase tracking-wider border border-amber-500/20 animate-pulse">Stopping</span>;
      default:
        return <span className="bg-slate-500/10 text-slate-500 px-2 py-0.5 rounded text-[10px] font-black uppercase tracking-wider border border-slate-500/20">Unknown</span>;
    }
  };

  return (
    <div className="space-y-6">
      
      {/* 1. Header with Global Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <span className="text-[10px] font-black uppercase tracking-widest text-slate-400 dark:text-slate-500">External Management Gateway</span>
          <h2 className="text-xl font-black tracking-tight text-slate-800 dark:text-white mt-0.5 flex items-center gap-2">
            <Server className="w-5.5 h-5.5 text-plan-cyan" />
            <span>Pterodactyl Panel Bridge</span>
            {getStateBadge()}
          </h2>
        </div>
        <div className="flex gap-2">
          <button 
            onClick={() => loadData(false)}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-800 bg-white dark:bg-plan-card text-xs font-bold text-slate-650 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors shadow-sm"
          >
            <RefreshCw className="w-3.5 h-3.5" />
            <span>Refresh</span>
          </button>
        </div>
      </div>

      {error && (
        <div className="p-3.5 rounded-lg bg-red-500/10 border border-red-500/15 flex items-start gap-2.5 text-plan-red text-xs animate-shake">
          <AlertTriangle className="w-4.5 h-4.5 mt-0.5 shrink-0" />
          <div className="font-semibold">
            <span>API Action Failed</span>
            <p className="mt-0.5 opacity-90 font-medium">{error}</p>
          </div>
        </div>
      )}

      {/* 2. Mode Tabs */}
      <div className="flex border-b border-slate-200 dark:border-slate-800/80 gap-6 select-none">
        <button
          onClick={() => setActiveTab('system')}
          className={`pb-3 text-xs font-bold transition-all relative ${activeTab === 'system' ? 'text-plan-cyan font-black border-b-2 border-plan-cyan' : 'text-slate-450 dark:text-slate-500 hover:text-slate-900 dark:hover:text-white'}`}
        >
          <div className="flex items-center gap-2">
            <Activity className="w-4 h-4" />
            <span>Host Performance</span>
          </div>
        </button>
        
        <button
          onClick={() => setActiveTab('backups')}
          className={`pb-3 text-xs font-bold transition-all relative ${activeTab === 'backups' ? 'text-plan-cyan font-black border-b-2 border-plan-cyan' : 'text-slate-450 dark:text-slate-500 hover:text-slate-900 dark:hover:text-white'}`}
        >
          <div className="flex items-center gap-2">
            <HardDrive className="w-4 h-4" />
            <span>Backup Manager</span>
            <span className="bg-slate-100 dark:bg-slate-900 text-slate-500 px-1.5 py-0.2 rounded-full text-[8.5px] font-black">{backups.length}</span>
          </div>
        </button>

        <button
          onClick={() => setActiveTab('console')}
          className={`pb-3 text-xs font-bold transition-all relative ${activeTab === 'console' ? 'text-plan-cyan font-black border-b-2 border-plan-cyan' : 'text-slate-450 dark:text-slate-500 hover:text-slate-900 dark:hover:text-white'}`}
        >
          <div className="flex items-center gap-2">
            <Terminal className="w-4 h-4" />
            <span>Remote Console</span>
          </div>
        </button>
      </div>

      {/* 3. Panel Body */}
      {activeTab === 'system' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Hardware Diagnostics */}
          <div className="lg:col-span-2 space-y-6">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              {/* CPU Card */}
              <div className="bg-white dark:bg-plan-card border border-slate-200 dark:border-slate-800/80 rounded-xl p-5 shadow-sm">
                <div className="flex items-center justify-between mb-3 text-slate-400 dark:text-slate-500">
                  <span className="text-[10px] font-black uppercase tracking-widest">CPU Allocation</span>
                  <Cpu className="w-4.5 h-4.5 text-plan-cyan" />
                </div>
                <div className="flex items-baseline gap-1.5">
                  <span className="text-3xl font-black tracking-tight dark:text-white">
                    {resources ? resources.cpuAbsolute.toFixed(1) : '0.0'}
                  </span>
                  <span className="text-xs font-bold text-slate-450 dark:text-slate-500">% Usage</span>
                </div>
                {/* Visual Bar */}
                <div className="w-full bg-slate-100 dark:bg-slate-900 h-1.5 rounded-full mt-4 overflow-hidden">
                  <div 
                    className="bg-plan-cyan h-full rounded-full transition-all duration-1000"
                    style={{ width: `${Math.min(resources?.cpuAbsolute || 0, 100)}%` }}
                  />
                </div>
              </div>

              {/* Memory Card */}
              <div className="bg-white dark:bg-plan-card border border-slate-200 dark:border-slate-800/80 rounded-xl p-5 shadow-sm">
                <div className="flex items-center justify-between mb-3 text-slate-400 dark:text-slate-500">
                  <span className="text-[10px] font-black uppercase tracking-widest">Memory Ingestion</span>
                  <Activity className="w-4.5 h-4.5 text-indigo-400" />
                </div>
                <div className="flex items-baseline gap-1.5">
                  <span className="text-3xl font-black tracking-tight dark:text-white">
                    {resources ? formatSize(resources.ramBytes) : '0 Bytes'}
                  </span>
                </div>
                {/* Network diagnostics */}
                <div className="flex justify-between items-center text-[10px] font-semibold text-slate-450 dark:text-slate-500 mt-5 border-t border-slate-100 dark:border-slate-900/60 pt-2.5">
                  <span>Disk footprint:</span>
                  <span className="font-extrabold text-slate-700 dark:text-slate-350">{resources ? formatSize(resources.diskBytes) : '0 Bytes'}</span>
                </div>
              </div>
            </div>

            {/* Network Traffic Info Card */}
            <div className="bg-white dark:bg-plan-card border border-slate-200 dark:border-slate-800/80 rounded-xl p-5 shadow-sm">
              <span className="text-[10px] font-black uppercase tracking-widest text-slate-400 dark:text-slate-500 block mb-4">Traffic Statistics</span>
              <div className="grid grid-cols-2 gap-4">
                <div className="p-3 bg-slate-50 dark:bg-slate-900/60 border border-slate-200/50 dark:border-slate-800/40 rounded-lg">
                  <span className="text-[9px] font-black uppercase tracking-wider text-slate-450 dark:text-slate-500 block">Received Data (Rx)</span>
                  <span className="text-base font-extrabold text-slate-800 dark:text-slate-100 mt-1 block">
                    {resources ? formatSize(resources.networkRxBytes) : '0 Bytes'}
                  </span>
                </div>
                <div className="p-3 bg-slate-50 dark:bg-slate-900/60 border border-slate-200/50 dark:border-slate-800/40 rounded-lg">
                  <span className="text-[9px] font-black uppercase tracking-wider text-slate-450 dark:text-slate-500 block">Transmitted Data (Tx)</span>
                  <span className="text-base font-extrabold text-slate-800 dark:text-slate-100 mt-1 block">
                    {resources ? formatSize(resources.networkTxBytes) : '0 Bytes'}
                  </span>
                </div>
              </div>
            </div>
          </div>

          {/* Quick Power Actions & Info */}
          <div className="bg-white dark:bg-plan-card border border-slate-200 dark:border-slate-800/80 rounded-xl p-5 shadow-sm h-full flex flex-col">
            <span className="text-[10px] font-black uppercase tracking-widest text-slate-400 dark:text-slate-500 block mb-4">Remote Power Deck</span>
            
            <div className="grid grid-cols-2 gap-3 mb-6">
              <button
                onClick={() => handlePowerAction('start')}
                disabled={submittingPower || serverState === 'running'}
                className="flex flex-col items-center justify-center p-3.5 rounded-xl border border-emerald-500/10 bg-emerald-500/5 hover:bg-emerald-500/10 disabled:opacity-40 text-emerald-500 transition-all font-bold text-xs gap-1.5 shadow-sm active:scale-[0.98]"
              >
                <Play className="w-5 h-5" />
                <span>Start</span>
              </button>

              <button
                onClick={() => handlePowerAction('restart')}
                disabled={submittingPower || serverState === 'offline'}
                className="flex flex-col items-center justify-center p-3.5 rounded-xl border border-amber-500/10 bg-amber-500/5 hover:bg-amber-500/10 disabled:opacity-40 text-amber-500 transition-all font-bold text-xs gap-1.5 shadow-sm active:scale-[0.98]"
              >
                <RotateCcw className="w-5 h-5" />
                <span>Restart</span>
              </button>

              <button
                onClick={() => handlePowerAction('stop')}
                disabled={submittingPower || serverState === 'offline'}
                className="flex flex-col items-center justify-center p-3.5 rounded-xl border border-rose-500/10 bg-rose-500/5 hover:bg-rose-500/10 disabled:opacity-40 text-rose-500 transition-all font-bold text-xs gap-1.5 shadow-sm active:scale-[0.98]"
              >
                <Square className="w-5 h-5" />
                <span>Stop</span>
              </button>

              <button
                onClick={() => {
                  if (confirm('CAUTION: Killing the server can cause chunk/database corruption. Proceed only if server is completely frozen!')) {
                    handlePowerAction('kill');
                  }
                }}
                disabled={submittingPower || serverState === 'offline'}
                className="flex flex-col items-center justify-center p-3.5 rounded-xl border border-red-650/15 bg-red-650/5 hover:bg-red-650/10 disabled:opacity-40 text-red-650 transition-all font-bold text-xs gap-1.5 shadow-sm active:scale-[0.98]"
              >
                <Power className="w-5 h-5" />
                <span>Kill</span>
              </button>
            </div>

            <div className="flex-grow flex flex-col justify-end">
              <div className="p-3.5 rounded-lg bg-slate-50 dark:bg-slate-900/60 border border-slate-200/50 dark:border-slate-800/40 text-[10.5px] leading-relaxed text-slate-500 dark:text-slate-450">
                <div className="flex items-center gap-1.5 font-bold mb-1.5 text-slate-700 dark:text-slate-300">
                  <Power className="w-3.5 h-3.5" />
                  <span>Remote Node Status</span>
                </div>
                Power commands take about 1-5 seconds to register on your Pterodactyl daemon. Ensure your console configurations match before issuing commands.
              </div>
            </div>
          </div>
        </div>
      )}

      {activeTab === 'backups' && (
        <div className="bg-white dark:bg-plan-card border border-slate-200 dark:border-slate-800/80 rounded-xl p-5 shadow-sm space-y-5">
          <div className="flex flex-col sm:flex-row justify-between sm:items-center gap-4">
            <div>
              <h3 className="text-sm font-black text-slate-800 dark:text-white">Pterodactyl Node Backups</h3>
              <p className="text-xs text-slate-400 mt-0.5">On-demand compressed archives backed up by Pterodactyl daemon.</p>
            </div>
            <button
              onClick={handleCreateBackup}
              disabled={creatingBackup}
              className="flex items-center justify-center gap-1.5 bg-gradient-to-r from-plan-cyan to-plan-blue text-white py-2 px-4 rounded-lg font-bold text-xs uppercase tracking-wider shadow-sm hover:shadow active:scale-[0.98] disabled:opacity-50"
            >
              {creatingBackup ? (
                <>
                  <RefreshCw className="w-4 h-4 animate-spin text-white" />
                  <span>Archiving Node...</span>
                </>
              ) : (
                <>
                  <Plus className="w-4 h-4" />
                  <span>Trigger Backup</span>
                </>
              )}
            </button>
          </div>

          {backups.length === 0 ? (
            <div className="text-center py-12 border-2 border-dashed border-slate-200 dark:border-slate-800 rounded-xl">
              <HardDrive className="w-10 h-10 text-slate-350 dark:text-slate-650 mx-auto mb-2.5" />
              <h4 className="text-xs font-black text-slate-700 dark:text-slate-450 uppercase tracking-wider">No Backups Registered</h4>
              <p className="text-[11px] text-slate-500 dark:text-slate-550 max-w-sm mx-auto mt-1 leading-relaxed">
                We couldn't find any backups for this server ID on your panel. Click "Trigger Backup" above to create your first archive!
              </p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs border-collapse">
                <thead>
                  <tr className="border-b border-slate-200 dark:border-slate-800 text-[10px] font-black uppercase tracking-widest text-slate-400 dark:text-slate-500 select-none">
                    <th className="pb-3 pl-4">Backup Name / Info</th>
                    <th className="pb-3 pl-2">Created Date</th>
                    <th className="pb-3 pr-4 text-center">Status</th>
                    <th className="pb-3 pr-4 text-right">Size</th>
                    <th className="pb-3 pr-4 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-slate-900/50">
                  {backups.map((backup) => (
                    <tr 
                      key={backup.uuid} 
                      className="hover:bg-slate-50/50 dark:hover:bg-slate-900/10 transition-colors group"
                    >
                      <td className="py-3 pl-4 font-bold text-slate-800 dark:text-slate-100 max-w-xs truncate">
                        <span className="block truncate">{backup.name || 'Auto Generated Archive'}</span>
                        <span className="block text-[9.5px] font-semibold text-slate-400 dark:text-slate-500 mt-0.5 truncate">{backup.uuid}</span>
                      </td>
                      <td className="py-3 pl-2 text-slate-600 dark:text-slate-400 font-semibold">
                        {formatDate(backup.createdAt)}
                      </td>
                      <td className="py-3 pr-4 text-center">
                        {backup.isSuccessful ? (
                          <span className="bg-emerald-500/10 text-emerald-500 px-2 py-0.5 rounded text-[9px] font-bold border border-emerald-500/10 select-none">Complete</span>
                        ) : (
                          <span className="bg-amber-500/10 text-amber-500 px-2 py-0.5 rounded text-[9px] font-bold border border-amber-500/10 animate-pulse select-none">Ingesting</span>
                        )}
                      </td>
                      <td className="py-3 pr-4 text-right font-bold text-slate-700 dark:text-slate-300">
                        {formatSize(backup.bytes)}
                      </td>
                      <td className="py-3 pr-4 text-right">
                        <div className="flex justify-end gap-1.5">
                          <button
                            onClick={() => handleDownloadBackup(backup.uuid)}
                            disabled={actionLoading !== null || !backup.isSuccessful}
                            className="p-1.5 rounded-lg border border-slate-200 dark:border-slate-800 bg-white dark:bg-plan-card text-slate-650 dark:text-slate-400 hover:text-plan-cyan dark:hover:text-plan-cyan hover:border-plan-cyan/40 disabled:opacity-40 transition-all shadow-sm"
                            title="Generate Download URL"
                          >
                            {actionLoading === backup.uuid ? (
                              <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                            ) : (
                              <Download className="w-3.5 h-3.5" />
                            )}
                          </button>
                          
                          <button
                            onClick={() => handleDeleteBackup(backup.uuid)}
                            disabled={actionLoading !== null || backup.isLocked}
                            className="p-1.5 rounded-lg border border-rose-100 dark:border-rose-950/40 bg-rose-50/50 dark:bg-rose-950/10 text-rose-500 hover:text-rose-700 hover:bg-rose-50 dark:hover:bg-rose-950/20 disabled:opacity-40 transition-all shadow-sm"
                            title="Delete Backup Permanently"
                          >
                            {actionLoading === backup.uuid ? (
                              <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                            ) : (
                              <Trash2 className="w-3.5 h-3.5" />
                            )}
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {activeTab === 'console' && (
        <div className="bg-white dark:bg-plan-card border border-slate-200 dark:border-slate-800/80 rounded-xl p-5 shadow-sm space-y-4">
          <div>
            <h3 className="text-sm font-black text-slate-800 dark:text-white">Command Sender Console</h3>
            <p className="text-xs text-slate-400 mt-0.5">Send raw minecraft console instructions securely down to Pterodactyl daemon.</p>
          </div>

          {/* Console Log Area */}
          <div className="w-full bg-slate-900 dark:bg-slate-950 rounded-xl p-4 border border-slate-800/80 shadow-inner font-mono text-xs text-slate-300 min-h-64 max-h-96 overflow-y-auto space-y-2 select-text leading-relaxed">
            <span className="text-[10px] text-slate-500 dark:text-slate-500 select-none block border-b border-slate-850 pb-1.5 mb-2.5">
              Secure Terminal Bridge Initialized -- Listening for downstream logs
            </span>
            {consoleLogs.length === 0 ? (
              <span className="text-slate-500 block italic">Terminal console idle. Enter a command below...</span>
            ) : (
              consoleLogs.map((log, idx) => (
                <div 
                  key={idx} 
                  className={log.startsWith('>') ? 'text-cyan-400 font-extrabold' : log.startsWith('[Error]') ? 'text-plan-red' : 'text-slate-350'}
                >
                  {log}
                </div>
              ))
            )}
          </div>

          {/* Input Sender Form */}
          <form onSubmit={handleSendCommand} className="flex gap-2">
            <input
              type="text"
              placeholder="e.g. say Hello World!, list, tps"
              value={command}
              onChange={(e) => setCommand(e.target.value)}
              disabled={submittingCommand}
              className="flex-grow px-3 py-2.5 rounded-lg border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-900 text-slate-800 dark:text-slate-100 text-xs font-mono focus:outline-none focus:ring-2 focus:ring-plan-cyan/40 focus:border-plan-cyan transition-all placeholder:text-slate-400 dark:placeholder:text-slate-650"
            />
            <button
              type="submit"
              disabled={submittingCommand || !command.trim()}
              className="px-5 py-2.5 bg-gradient-to-r from-plan-cyan to-plan-blue text-white rounded-lg text-xs font-bold uppercase tracking-wider shadow-sm hover:shadow active:scale-[0.98] disabled:opacity-50 flex items-center gap-1.5 shrink-0"
            >
              {submittingCommand ? (
                <RefreshCw className="w-3.5 h-3.5 animate-spin" />
              ) : (
                <ChevronRight className="w-4 h-4" />
              )}
              <span>Send</span>
            </button>
          </form>
        </div>
      )}
    </div>
  );
};
