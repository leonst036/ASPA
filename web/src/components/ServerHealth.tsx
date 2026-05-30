import React, { useEffect, useState, useCallback } from 'react';
import { 
  ResponsiveContainer, 
  AreaChart, 
  Area, 
  LineChart, 
  Line, 
  XAxis, 
  YAxis, 
  CartesianGrid, 
  Tooltip, 
  Legend 
} from 'recharts';
import { 
  Activity, 
  Cpu, 
  Database, 
  Users, 
  Layers, 
  AlertOctagon, 
  TrendingUp, 
  RefreshCw, 
  Flame, 
  ChevronRight, 
  Binary,
  Wifi,
  Timer
} from 'lucide-react';
import { getServerMetrics, getPerformanceAnomalies } from '../utils/api';
import type { ServerMetricsRecord, PerformanceAnomaly } from '../types';

export const ServerHealth: React.FC = () => {
  const [metrics, setMetrics] = useState<ServerMetricsRecord[]>([]);
  const [anomalies, setAnomalies] = useState<PerformanceAnomaly[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<'perf' | 'ram' | 'entities' | 'network'>('perf');
  const [worldView, setWorldView] = useState<'chunks' | 'entities'>('chunks');

  const fetchData = useCallback(async (isSilent = false) => {
    if (!isSilent) setLoading(true);
    else setRefreshing(true);
    setError(null);

    try {
      const [metricsData, anomaliesData] = await Promise.all([
        getServerMetrics(10), // fetch last 10 minutes (60 points)
        getPerformanceAnomalies()
      ]);
      setMetrics(metricsData);
      setAnomalies(anomaliesData);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch server health data.');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  // Poll metrics every 10 seconds to make it truly live-updating!
  useEffect(() => {
    fetchData();
    const interval = setInterval(() => {
      fetchData(true);
    }, 10000);
    return () => clearInterval(interval);
  }, [fetchData]);

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="flex flex-col items-center gap-3 text-brand-600 dark:text-dark-300">
          <RefreshCw className="w-8 h-8 animate-spin" />
          <span className="text-sm font-medium tracking-wide">Loading real-time hardware metrics...</span>
        </div>
      </div>
    );
  }

  if (error && metrics.length === 0) {
    return (
      <div className="p-6 bg-red-50 dark:bg-red-950/20 border border-red-200 dark:border-red-900/50 rounded-2xl text-red-600 dark:text-red-400">
        <h3 className="font-semibold text-lg flex items-center gap-2">
          <AlertOctagon className="w-5 h-5" /> Ingestion Error
        </h3>
        <p className="mt-2 text-sm opacity-95">{error}</p>
        <button 
          onClick={() => fetchData()} 
          className="mt-4 px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-xl text-xs font-semibold tracking-wider transition-all"
        >
          Retry Connection
        </button>
      </div>
    );
  }

  // Get current metrics (last item in history)
  const current = metrics[metrics.length - 1] || {
    tps: 20.0,
    mspt: 15.0,
    cpuUsage: 10.0,
    ramUsedMb: 4000,
    ramMaxMb: 8192,
    onlinePlayers: 0,
    loadedChunks: 0,
    entityCounts: { monsters: 0, animals: 0, tileEntities: 0 },
    gcCountDelta: 0,
    gcTimeDeltaMs: 0,
    avgPing: 0.0,
    maxPing: 0.0,
    chunksPerWorld: {},
    entitiesPerWorld: {}
  };

  // Tooltip custom style for modern glassmorphism look
  const isDark = document.documentElement.classList.contains('dark');
  const tooltipStyle = {
    backgroundColor: isDark ? 'rgba(26, 29, 36, 0.85)' : 'rgba(255, 255, 255, 0.85)',
    backdropFilter: 'blur(12px)',
    WebkitBackdropFilter: 'blur(12px)',
    borderRadius: '12px',
    border: isDark ? '1px solid rgba(255, 255, 255, 0.08)' : '1px solid rgba(0, 0, 0, 0.08)',
    fontSize: '11px',
    color: isDark ? '#f8fafc' : '#1e293b',
    boxShadow: '0 10px 25px -5px rgba(0,0,0,0.1), 0 8px 10px -6px rgba(0,0,0,0.04)',
    padding: '8px 12px'
  };

  const currentEntities = Object.values(current.entityCounts || {}).reduce((a, b) => a + b, 0);

  // Dynamically extract world names from metrics
  const worldNames = Array.from(new Set(
    metrics.flatMap(m => m.chunksPerWorld ? Object.keys(m.chunksPerWorld) : [])
  )).sort();

  const WORLD_COLORS = ['#06b6d4', '#f43f5e', '#8b5cf6', '#10b981', '#f59e0b', '#3b82f6'];

  // Dynamic threshold-based styles for unified card visual states

  const getTpsStyle = (tps: number) => {
    if (tps >= 19.5) return {
      border: 'border-slate-200 dark:border-slate-800/80 hover:border-emerald-500/50 dark:hover:border-emerald-500/50',
      icon: 'text-emerald-500 bg-emerald-500/10 dark:bg-emerald-500/5',
      text: 'text-emerald-500'
    };
    if (tps >= 18.0) return {
      border: 'border-amber-300 dark:border-amber-900/60 hover:border-amber-500 dark:hover:border-amber-500',
      icon: 'text-amber-500 bg-amber-500/10 dark:bg-amber-500/5',
      text: 'text-amber-600 dark:text-amber-400'
    };
    return {
      border: 'border-rose-350 dark:border-rose-950/60 hover:border-rose-500 dark:hover:border-rose-500',
      icon: 'text-rose-500 bg-rose-500/10 dark:bg-rose-500/5 animate-pulse',
      text: 'text-rose-500 font-extrabold'
    };
  };

  const getMsptStyle = (mspt: number) => {
    if (mspt <= 30) return {
      border: 'border-slate-200 dark:border-slate-800/80 hover:border-plan-cyan/50 dark:hover:border-plan-cyan/50',
      icon: 'text-plan-cyan bg-plan-cyan/10 dark:bg-plan-cyan/5',
      text: 'text-plan-cyan'
    };
    if (mspt <= 50) return {
      border: 'border-amber-300 dark:border-amber-900/60 hover:border-amber-500 dark:hover:border-amber-500',
      icon: 'text-amber-500 bg-amber-500/10 dark:bg-amber-500/5',
      text: 'text-amber-600 dark:text-amber-400'
    };
    return {
      border: 'border-rose-350 dark:border-rose-950/60 hover:border-rose-500 dark:hover:border-rose-500',
      icon: 'text-rose-500 bg-rose-500/10 dark:bg-rose-500/5',
      text: 'text-rose-500 font-extrabold'
    };
  };

  const getCpuStyle = (cpu: number) => {
    if (cpu <= 70) return {
      border: 'border-slate-200 dark:border-slate-800/80 hover:border-plan-blue/50 dark:hover:border-plan-blue/50',
      icon: 'text-plan-blue bg-plan-blue/10 dark:bg-plan-blue/5',
      text: 'text-slate-800 dark:text-white',
      bar: 'bg-plan-blue'
    };
    if (cpu <= 90) return {
      border: 'border-amber-300 dark:border-amber-900/60 hover:border-amber-500 dark:hover:border-amber-500',
      icon: 'text-amber-500 bg-amber-500/10 dark:bg-amber-500/5',
      text: 'text-amber-600 dark:text-amber-400',
      bar: 'bg-amber-500'
    };
    return {
      border: 'border-rose-350 dark:border-rose-950/60 hover:border-rose-500 dark:hover:border-rose-500',
      icon: 'text-rose-500 bg-rose-500/10 dark:bg-rose-500/5',
      text: 'text-rose-500 font-extrabold',
      bar: 'bg-rose-500'
    };
  };

  const getRamStyle = (ramPct: number) => {
    if (ramPct <= 80) return {
      border: 'border-slate-200 dark:border-slate-800/80 hover:border-plan-purple/50 dark:hover:border-plan-purple/50',
      icon: 'text-plan-purple bg-plan-purple/10 dark:bg-plan-purple/5',
      text: 'text-slate-800 dark:text-white',
      bar: 'bg-plan-purple'
    };
    if (ramPct <= 95) return {
      border: 'border-amber-300 dark:border-amber-900/60 hover:border-amber-500 dark:hover:border-amber-500',
      icon: 'text-amber-500 bg-amber-500/10 dark:bg-amber-500/5',
      text: 'text-amber-600 dark:text-amber-400',
      bar: 'bg-amber-500'
    };
    return {
      border: 'border-rose-350 dark:border-rose-950/60 hover:border-rose-500 dark:hover:border-rose-500',
      icon: 'text-rose-500 bg-rose-500/10 dark:bg-rose-500/5',
      text: 'text-rose-500 font-extrabold',
      bar: 'bg-rose-500'
    };
  };

  const getGcStyle = (pause: number) => {
    if (pause <= 100) return {
      border: 'border-slate-200 dark:border-slate-800/80 hover:border-amber-600/50 dark:hover:border-amber-600/50',
      icon: 'text-amber-600 bg-amber-600/10 dark:bg-amber-600/5',
      text: 'text-slate-800 dark:text-white'
    };
    if (pause <= 300) return {
      border: 'border-amber-300 dark:border-amber-900/60 hover:border-amber-500 dark:hover:border-amber-500',
      icon: 'text-amber-500 bg-amber-500/10 dark:bg-amber-500/5',
      text: 'text-amber-600 dark:text-amber-400'
    };
    return {
      border: 'border-rose-350 dark:border-rose-950/60 hover:border-rose-500 dark:hover:border-rose-500',
      icon: 'text-rose-500 bg-rose-500/10 dark:bg-rose-500/5',
      text: 'text-rose-500 font-extrabold'
    };
  };

  const getNetworkStyle = (ping: number) => {
    if (ping <= 80) return {
      border: 'border-slate-200 dark:border-slate-800/80 hover:border-sky-500/50 dark:hover:border-sky-500/50',
      icon: 'text-sky-500 bg-sky-500/10 dark:bg-sky-500/5',
      text: 'text-slate-800 dark:text-white'
    };
    if (ping <= 180) return {
      border: 'border-amber-300 dark:border-amber-900/60 hover:border-amber-500 dark:hover:border-amber-500',
      icon: 'text-amber-500 bg-amber-500/10 dark:bg-amber-500/5',
      text: 'text-amber-600 dark:text-amber-400'
    };
    return {
      border: 'border-rose-350 dark:border-rose-950/60 hover:border-rose-500 dark:hover:border-rose-500',
      icon: 'text-rose-500 bg-rose-500/10 dark:bg-rose-500/5',
      text: 'text-rose-500 font-extrabold'
    };
  };

  const formatTime = (ts: number) => {
    const d = new Date(ts);
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`;
  };

  const ramPercentage = (current.ramUsedMb / current.ramMaxMb) * 100;

  // Active status color configurations
  const tpsCard = getTpsStyle(current.tps);
  const msptCard = getMsptStyle(current.mspt);
  const cpuCard = getCpuStyle(current.cpuUsage);
  const ramCard = getRamStyle(ramPercentage);
  const gcCard = getGcStyle(current.gcTimeDeltaMs);
  const netCard = getNetworkStyle(current.avgPing);

  return (
    <div className="space-y-8">
      {/* Real-time Header */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h2 className="text-xl font-extrabold tracking-tight text-slate-800 dark:text-white flex items-center gap-2">
            <Activity className="w-5.5 h-5.5 text-plan-cyan" /> Server Health Dashboard
          </h2>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
            Real-time processing tick speeds, garbage collector memory buffers, chunk maps, and correlated performance spikes.
          </p>
        </div>
        <div className="flex items-center gap-3">
          {refreshing && (
            <span className="text-[10px] text-slate-500 dark:text-slate-400 flex items-center gap-1.5 font-bold uppercase tracking-wider animate-pulse">
              <RefreshCw className="w-3.5 h-3.5 animate-spin text-plan-cyan" /> Ingesting metrics...
            </span>
          )}
          <button
            onClick={() => fetchData(true)}
            disabled={refreshing}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-800/80 bg-white dark:bg-slate-900 text-slate-700 dark:text-slate-330 text-xs font-bold shadow-sm hover:bg-slate-50 dark:hover:bg-slate-800 active:scale-[0.98] transition-all"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${refreshing ? 'animate-spin' : ''}`} />
            Refresh
          </button>
        </div>
      </div>

      {/* 1. System Core Performance Metrics */}
      <div className="space-y-3.5">
        <span className="text-[9px] font-black uppercase tracking-[0.18em] text-slate-400 dark:text-slate-500 pl-1 block">
          System Core Performance
        </span>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
          {/* TPS Card */}
          <div className={`flex items-center p-5 rounded-2xl bg-white/60 dark:bg-plan-card/60 border border-slate-200/50 dark:border-slate-800/40 hover:bg-white dark:hover:bg-plan-card/85 transition-all duration-300 hover:shadow-sm ${tpsCard.border}`}>
            <div className={`w-10 h-10 rounded-xl flex items-center justify-center shrink-0 transition-colors ${tpsCard.icon}`}>
              <Activity className="w-5 h-5" />
            </div>
            <div className="ml-4 flex-1 min-w-0">
              <span className="text-[9px] font-bold text-slate-400 dark:text-slate-500 uppercase tracking-wider block">Ticks Per Second</span>
              <span className={`text-xl font-black block mt-0.5 ${tpsCard.text}`}>
                {current.tps.toFixed(2)}
              </span>
              <span className="text-[8px] text-slate-500 dark:text-slate-500 font-bold block mt-0.5">Target: 20.00 TPS</span>
            </div>
          </div>

          {/* MSPT Card */}
          <div className={`flex items-center p-5 rounded-2xl bg-white/60 dark:bg-plan-card/60 border border-slate-200/50 dark:border-slate-800/40 hover:bg-white dark:hover:bg-plan-card/85 transition-all duration-300 hover:shadow-sm ${msptCard.border}`}>
            <div className={`w-10 h-10 rounded-xl flex items-center justify-center shrink-0 transition-colors ${msptCard.icon}`}>
              <Flame className="w-5 h-5" />
            </div>
            <div className="ml-4 flex-1 min-w-0">
              <span className="text-[9px] font-bold text-slate-400 dark:text-slate-500 uppercase tracking-wider block">Tick Duration</span>
              <span className={`text-xl font-black block mt-0.5 ${msptCard.text}`}>
                {current.mspt.toFixed(1)} <span className="text-xs font-semibold text-slate-400 dark:text-slate-500">ms</span>
              </span>
              <span className="text-[8px] text-slate-500 dark:text-slate-500 font-bold block mt-0.5">Alert Level: &gt;50 ms</span>
            </div>
          </div>

          {/* CPU Card */}
          <div className={`flex flex-col p-5 rounded-2xl bg-white/60 dark:bg-plan-card/60 border border-slate-200/50 dark:border-slate-800/40 hover:bg-white dark:hover:bg-plan-card/85 transition-all duration-300 hover:shadow-sm ${cpuCard.border}`}>
            <div className="flex items-center w-full">
              <div className={`w-10 h-10 rounded-xl flex items-center justify-center shrink-0 transition-colors ${cpuCard.icon}`}>
                <Cpu className="w-5 h-5" />
              </div>
              <div className="ml-4 flex-1 min-w-0">
                <span className="text-[9px] font-bold text-slate-400 dark:text-slate-500 uppercase tracking-wider block">CPU Utilization</span>
                <span className={`text-xl font-black block mt-0.5 ${cpuCard.text}`}>
                  {current.cpuUsage.toFixed(1)}%
                </span>
              </div>
            </div>
            <div className="w-full mt-3">
              <div className="h-1 w-full bg-slate-100 dark:bg-slate-800/80 rounded-full overflow-hidden">
                <div 
                  className={`h-full rounded-full transition-all duration-500 ${cpuCard.bar}`} 
                  style={{ width: `${Math.min(current.cpuUsage, 100)}%` }}
                />
              </div>
            </div>
          </div>

          {/* RAM Card */}
          <div className={`flex flex-col p-5 rounded-2xl bg-white/60 dark:bg-plan-card/60 border border-slate-200/50 dark:border-slate-800/40 hover:bg-white dark:hover:bg-plan-card/85 transition-all duration-300 hover:shadow-sm ${ramCard.border}`}>
            <div className="flex items-center w-full">
              <div className={`w-10 h-10 rounded-xl flex items-center justify-center shrink-0 transition-colors ${ramCard.icon}`}>
                <Database className="w-5 h-5" />
              </div>
              <div className="ml-4 flex-1 min-w-0">
                <span className="text-[9px] font-bold text-slate-400 dark:text-slate-500 uppercase tracking-wider block">Memory Allocation</span>
                <span className={`text-xl font-black block mt-0.5 ${ramCard.text}`}>
                  {(current.ramUsedMb / 1024).toFixed(1)}
                  <span className="text-xs font-semibold text-slate-400 dark:text-slate-500">/{(current.ramMaxMb / 1024).toFixed(0)}G</span>
                </span>
              </div>
            </div>
            <div className="w-full mt-3">
              <div className="h-1 w-full bg-slate-100 dark:bg-slate-800/80 rounded-full overflow-hidden">
                <div 
                  className={`h-full rounded-full transition-all duration-500 ${ramCard.bar}`} 
                  style={{ width: `${Math.min(ramPercentage, 100)}%` }}
                />
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* 2. World & Network Status Metrics */}
      <div className="space-y-3.5">
        <span className="text-[9px] font-black uppercase tracking-[0.18em] text-slate-400 dark:text-slate-500 pl-1 block">
          World & Network Status
        </span>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
          {/* GC Card */}
          <div className={`flex flex-col p-5 rounded-2xl bg-white/60 dark:bg-plan-card/60 border border-slate-200/50 dark:border-slate-800/40 hover:bg-white dark:hover:bg-plan-card/85 transition-all duration-300 hover:shadow-sm ${gcCard.border}`}>
            <div className="flex items-center w-full">
              <div className={`w-10 h-10 rounded-xl flex items-center justify-center shrink-0 transition-colors ${gcCard.icon}`}>
                <Timer className="w-5 h-5" />
              </div>
              <div className="ml-4 flex-1 min-w-0">
                <span className="text-[9px] font-bold text-slate-400 dark:text-slate-500 uppercase tracking-wider block">Garbage Collector</span>
                <span className={`text-xl font-black block mt-0.5 ${gcCard.text}`}>
                  {current.gcTimeDeltaMs || 0} <span className="text-xs font-semibold text-slate-400 dark:text-slate-500">ms</span>
                </span>
              </div>
            </div>
            <span className="text-[8px] text-slate-500 dark:text-slate-500 font-bold block mt-3.5">
              Collections Delta: {current.gcCountDelta || 0}
            </span>
          </div>

          {/* Network Ping Card */}
          <div className={`flex flex-col p-5 rounded-2xl bg-white/60 dark:bg-plan-card/60 border border-slate-200/50 dark:border-slate-800/40 hover:bg-white dark:hover:bg-plan-card/85 transition-all duration-300 hover:shadow-sm ${netCard.border}`}>
            <div className="flex items-center w-full">
              <div className={`w-10 h-10 rounded-xl flex items-center justify-center shrink-0 transition-colors ${netCard.icon}`}>
                <Wifi className="w-5 h-5" />
              </div>
              <div className="ml-4 flex-1 min-w-0">
                <span className="text-[9px] font-bold text-slate-400 dark:text-slate-500 uppercase tracking-wider block">Average Latency</span>
                <span className={`text-xl font-black block mt-0.5 ${netCard.text}`}>
                  {(current.avgPing || 0).toFixed(0)} <span className="text-xs font-semibold text-slate-400 dark:text-slate-500">ms</span>
                </span>
              </div>
            </div>
            <span className="text-[8px] text-slate-500 dark:text-slate-500 font-bold block mt-3.5">
              Current Peak Latency: {(current.maxPing || 0).toFixed(0)} ms
            </span>
          </div>

          {/* Chunks Card */}
          <div className="flex items-center p-5 rounded-2xl bg-white/60 dark:bg-plan-card/60 border border-slate-200/50 dark:border-slate-800/40 hover:bg-white dark:hover:bg-plan-card/85 transition-all duration-300 hover:shadow-sm hover:border-plan-blue/30 dark:hover:border-plan-blue/30">
            <div className="w-10 h-10 rounded-xl bg-slate-100 dark:bg-slate-900 text-slate-500 dark:text-slate-400 flex items-center justify-center shrink-0">
              <Layers className="w-5 h-5" />
            </div>
            <div className="ml-4 flex-1 min-w-0">
              <span className="text-[9px] font-bold text-slate-400 dark:text-slate-500 uppercase tracking-wider block">Loaded Chunks</span>
              <span className="text-xl font-black text-slate-800 dark:text-white block mt-0.5">
                {current.loadedChunks}
              </span>
              <span className="text-[8px] text-slate-500 dark:text-slate-500 font-bold block mt-0.5">Active Memory Map</span>
            </div>
          </div>

          {/* Entities Card */}
          <div className="flex items-center p-5 rounded-2xl bg-white/60 dark:bg-plan-card/60 border border-slate-200/50 dark:border-slate-800/40 hover:bg-white dark:hover:bg-plan-card/85 transition-all duration-300 hover:shadow-sm hover:border-plan-blue/30 dark:hover:border-plan-blue/30">
            <div className="w-10 h-10 rounded-xl bg-slate-100 dark:bg-slate-900 text-slate-500 dark:text-slate-400 flex items-center justify-center shrink-0">
              <Users className="w-5 h-5" />
            </div>
            <div className="ml-4 flex-1 min-w-0">
              <span className="text-[9px] font-bold text-slate-400 dark:text-slate-500 uppercase tracking-wider block">Active Entities</span>
              <span className="text-xl font-black text-slate-800 dark:text-white block mt-0.5">
                {currentEntities}
              </span>
              <span className="text-[8px] text-slate-500 dark:text-slate-500 font-bold block mt-0.5">All Simulation Worlds</span>
            </div>
          </div>
        </div>
      </div>

      {/* Main Charts & Controls */}
      <div className="bg-white/60 dark:bg-plan-card/60 border border-slate-200/50 dark:border-slate-800/40 shadow-sm rounded-2xl p-5 relative overflow-hidden">
        {/* Chart Nav Tabs */}
        <div className="flex border-b border-slate-200/50 dark:border-slate-800/40 pb-3 mb-5 justify-between items-center flex-wrap sm:flex-nowrap gap-4 w-full">
          <div className="flex overflow-x-auto scrollbar-none bg-slate-100/50 dark:bg-slate-900/60 p-1 rounded-xl border border-slate-200/40 dark:border-slate-800/40 w-full sm:w-auto">
            <div className="flex flex-nowrap gap-1 min-w-max">
              <button
                onClick={() => setActiveTab('perf')}
                className={`px-4 py-2 rounded-lg text-xs font-bold tracking-wide transition-all whitespace-nowrap ${
                  activeTab === 'perf'
                    ? 'bg-white dark:bg-slate-800 text-plan-blue dark:text-cyan-400 shadow-sm font-black'
                    : 'text-slate-500 dark:text-slate-450 hover:text-slate-900 dark:hover:text-white'
                }`}
              >
                Performance (TPS / MSPT)
              </button>
              <button
                onClick={() => setActiveTab('ram')}
                className={`px-4 py-2 rounded-lg text-xs font-bold tracking-wide transition-all whitespace-nowrap ${
                  activeTab === 'ram'
                    ? 'bg-white dark:bg-slate-800 text-plan-blue dark:text-cyan-400 shadow-sm font-black'
                    : 'text-slate-500 dark:text-slate-450 hover:text-slate-900 dark:hover:text-white'
                }`}
              >
                GC & Memory Buffer
              </button>
              <button
                onClick={() => setActiveTab('entities')}
                className={`px-4 py-2 rounded-lg text-xs font-bold tracking-wide transition-all whitespace-nowrap ${
                  activeTab === 'entities'
                    ? 'bg-white dark:bg-slate-800 text-plan-blue dark:text-cyan-400 shadow-sm font-black'
                    : 'text-slate-500 dark:text-slate-450 hover:text-slate-900 dark:hover:text-white'
                }`}
              >
                World Chunks & Entities
              </button>
              <button
                onClick={() => setActiveTab('network')}
                className={`px-4 py-2 rounded-lg text-xs font-bold tracking-wide transition-all whitespace-nowrap ${
                  activeTab === 'network'
                    ? 'bg-white dark:bg-slate-800 text-plan-blue dark:text-cyan-400 shadow-sm font-black'
                    : 'text-slate-500 dark:text-slate-450 hover:text-slate-900 dark:hover:text-white'
                }`}
              >
                Network Latency (Pings)
              </button>
            </div>
          </div>
          <div className="text-[9.5px] text-slate-500 dark:text-slate-500 font-bold uppercase tracking-wider flex items-center gap-1.5 whitespace-nowrap select-none">
            <Binary className="w-3.5 h-3.5 text-plan-blue" /> Resolution: 10-Second Pulses
          </div>
        </div>

        {/* Charts Rendering */}
        <div className="h-[320px] w-full">
          {activeTab === 'perf' && (
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={metrics} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                <defs>
                  <linearGradient id="colorTps" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#10b981" stopOpacity={0.15}/>
                    <stop offset="95%" stopColor="#10b981" stopOpacity={0}/>
                  </linearGradient>
                  <linearGradient id="colorMspt" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#06b6d4" stopOpacity={0.15}/>
                    <stop offset="95%" stopColor="#06b6d4" stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" strokeOpacity={0.3} className="dark:hidden" />
                <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" strokeOpacity={0.08} className="hidden dark:block" />
                <XAxis 
                  dataKey="timestamp" 
                  tickFormatter={formatTime} 
                  tick={{ fontSize: 9, fontWeight: 'bold' }} 
                  stroke="#64748b" 
                />
                <YAxis 
                  yAxisId="left" 
                  domain={[10, 21]} 
                  tick={{ fontSize: 9, fontWeight: 'bold' }} 
                  stroke="#10b981" 
                  label={{ value: 'TPS', angle: -90, position: 'insideLeft', style: { fontSize: 10, fill: '#10b981', fontWeight: 'bold', tracking: '0.1em' } }}
                />
                <YAxis 
                  yAxisId="right" 
                  orientation="right" 
                  domain={[0, 'auto']} 
                  tick={{ fontSize: 9, fontWeight: 'bold' }} 
                  stroke="#06b6d4"
                  label={{ value: 'MSPT (ms)', angle: 90, position: 'insideRight', style: { fontSize: 10, fill: '#06b6d4', fontWeight: 'bold', tracking: '0.1em' } }}
                />
                <Tooltip 
                  labelFormatter={(t) => `Time: ${new Date(t).toLocaleTimeString()}`}
                  contentStyle={tooltipStyle}
                  itemStyle={{ padding: '1px 0', color: isDark ? '#cbd5e1' : '#334155' }}
                />
                <Legend wrapperStyle={{ fontSize: '10px', paddingTop: '10px', fontWeight: 'bold' }} />
                <Area 
                  yAxisId="left" 
                  type="monotone" 
                  dataKey="tps" 
                  stroke="#10b981" 
                  strokeWidth={2}
                  fillOpacity={1} 
                  fill="url(#colorTps)" 
                  name="Ticks Per Second (TPS)"
                />
                <Area 
                  yAxisId="right" 
                  type="monotone" 
                  dataKey="mspt" 
                  stroke="#06b6d4" 
                  strokeWidth={2}
                  fillOpacity={1} 
                  fill="url(#colorMspt)" 
                  name="Millisecond Per Tick (MSPT)"
                />
              </AreaChart>
            </ResponsiveContainer>
          )}

          {activeTab === 'ram' && (
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={metrics} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                <defs>
                  <linearGradient id="colorRam" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#4f46e5" stopOpacity={0.15}/>
                    <stop offset="95%" stopColor="#4f46e5" stopOpacity={0}/>
                  </linearGradient>
                  <linearGradient id="colorGc" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#f59e0b" stopOpacity={0.15}/>
                    <stop offset="95%" stopColor="#f59e0b" stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" strokeOpacity={0.3} className="dark:hidden" />
                <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" strokeOpacity={0.08} className="hidden dark:block" />
                <XAxis 
                  dataKey="timestamp" 
                  tickFormatter={formatTime} 
                  tick={{ fontSize: 9, fontWeight: 'bold' }} 
                  stroke="#64748b" 
                />
                <YAxis 
                  yAxisId="left"
                  domain={[0, 'auto']} 
                  tickFormatter={(v) => `${(v/1024).toFixed(1)}G`}
                  tick={{ fontSize: 9, fontWeight: 'bold' }} 
                  stroke="#4f46e5" 
                  label={{ value: 'Memory Buffers', angle: -90, position: 'insideLeft', style: { fontSize: 10, fill: '#4f46e5', fontWeight: 'bold' } }}
                />
                <YAxis 
                  yAxisId="right"
                  orientation="right"
                  domain={[0, 'auto']} 
                  tickFormatter={(v) => `${v}ms`}
                  tick={{ fontSize: 9, fontWeight: 'bold' }} 
                  stroke="#f59e0b" 
                  label={{ value: 'GC Pause Delta', angle: 90, position: 'insideRight', style: { fontSize: 10, fill: '#f59e0b', fontWeight: 'bold' } }}
                />
                <Tooltip 
                  labelFormatter={(t) => `Time: ${new Date(t).toLocaleTimeString()}`}
                  contentStyle={tooltipStyle}
                  itemStyle={{ padding: '1px 0', color: isDark ? '#cbd5e1' : '#334155' }}
                />
                <Legend wrapperStyle={{ fontSize: '10px', paddingTop: '10px', fontWeight: 'bold' }} />
                <Area 
                  yAxisId="left"
                  type="monotone" 
                  dataKey="ramUsedMb" 
                  stroke="#4f46e5" 
                  strokeWidth={2}
                  fillOpacity={1} 
                  fill="url(#colorRam)" 
                  name="Allocated RAM (MB)"
                />
                <Area 
                  yAxisId="right"
                  type="monotone" 
                  dataKey="gcTimeDeltaMs" 
                  stroke="#f59e0b" 
                  strokeWidth={2}
                  fillOpacity={1} 
                  fill="url(#colorGc)" 
                  name="GC Collection Pause Time (ms)"
                />
              </AreaChart>
            </ResponsiveContainer>
          )}

          {activeTab === 'entities' && (
            <div className="flex flex-col h-full w-full">
              {worldNames.length === 0 ? (
                <div className="flex flex-col items-center justify-center h-full min-h-[260px] text-center border border-dashed border-slate-200 dark:border-slate-800 rounded-xl bg-slate-50/40 dark:bg-slate-900/10 p-5">
                  <div className="w-10 h-10 rounded-full bg-slate-100 dark:bg-slate-800 flex items-center justify-center text-slate-400 dark:text-slate-550 mb-3 border border-slate-200 dark:border-slate-700">
                    <Layers className="w-5 h-5" />
                  </div>
                  <h4 className="text-xs font-bold text-slate-800 dark:text-slate-200 uppercase tracking-wide">No World Telemetry</h4>
                  <p className="text-[10px] text-slate-500 dark:text-slate-400 mt-1 max-w-xs font-medium">
                    World breakdown analytics will automatically populate once chunk and entity telemetry data starts streaming.
                  </p>
                </div>
              ) : (
                <>
                  {/* World View Sub-toggle */}
                  <div className="flex justify-between items-center mb-2">
                    <div className="flex bg-slate-100 dark:bg-slate-900/60 p-0.5 rounded-lg border border-slate-200/40 dark:border-slate-800/40 self-start">
                      <button
                        onClick={() => setWorldView('chunks')}
                        className={`px-3 py-1 rounded-md text-[9px] font-black tracking-wider uppercase transition-all ${
                          worldView === 'chunks'
                            ? 'bg-white dark:bg-slate-800 text-plan-cyan shadow-sm font-bold'
                            : 'text-slate-500 hover:text-slate-700 dark:hover:text-slate-350'
                        }`}
                      >
                        Chunks Breakdown
                      </button>
                      <button
                        onClick={() => setWorldView('entities')}
                        className={`px-3 py-1 rounded-md text-[9px] font-black tracking-wider uppercase transition-all ${
                          worldView === 'entities'
                            ? 'bg-white dark:bg-slate-800 text-plan-cyan shadow-sm font-bold'
                            : 'text-slate-500 hover:text-slate-700 dark:hover:text-slate-350'
                        }`}
                      >
                        Entities Breakdown
                      </button>
                    </div>
                  </div>
                  
                  <div className="flex-grow h-[260px] w-full">
                    {worldView === 'chunks' ? (
                      <ResponsiveContainer width="100%" height="100%">
                        <AreaChart data={metrics} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                          <defs>
                            {WORLD_COLORS.map((color, idx) => (
                              <linearGradient key={idx} id={`colorWorldChunks-${idx}`} x1="0" y1="0" x2="0" y2="1">
                                <stop offset="5%" stopColor={color} stopOpacity={0.2}/>
                                <stop offset="95%" stopColor={color} stopOpacity={0}/>
                              </linearGradient>
                            ))}
                          </defs>
                          <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" strokeOpacity={0.5} className="dark:hidden" />
                          <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" strokeOpacity={0.15} className="hidden dark:block" />
                          <XAxis 
                            dataKey="timestamp" 
                            tickFormatter={formatTime} 
                            tick={{ fontSize: 9, fontWeight: 'bold' }} 
                            stroke="#64748b" 
                          />
                          <YAxis 
                            tick={{ fontSize: 9, fontWeight: 'bold' }} 
                            stroke="#64748b" 
                            label={{ value: 'Loaded Chunks', angle: -90, position: 'insideLeft', style: { fontSize: 10, fill: '#64748b', fontWeight: 'bold' } }}
                          />
                          <Tooltip 
                            labelFormatter={(t) => `Time: ${new Date(t).toLocaleTimeString()}`}
                            contentStyle={tooltipStyle}
                            itemStyle={{ padding: '1px 0', color: isDark ? '#cbd5e1' : '#334155' }}
                          />
                          <Legend wrapperStyle={{ fontSize: '9px', paddingTop: '5px', fontWeight: 'bold' }} />
                          {worldNames.map((worldName, idx) => (
                            <Area 
                              key={worldName} 
                              type="monotone" 
                              dataKey={`chunksPerWorld.${worldName}`} 
                              stackId="1"
                              stroke={WORLD_COLORS[idx % WORLD_COLORS.length]} 
                              fill={`url(#colorWorldChunks-${idx % WORLD_COLORS.length})`}
                              name={`Chunks: ${worldName}`} 
                            />
                          ))}
                        </AreaChart>
                      </ResponsiveContainer>
                    ) : (
                      <ResponsiveContainer width="100%" height="100%">
                        <LineChart data={metrics} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                          <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" strokeOpacity={0.5} className="dark:hidden" />
                          <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" strokeOpacity={0.15} className="hidden dark:block" />
                          <XAxis 
                            dataKey="timestamp" 
                            tickFormatter={formatTime} 
                            tick={{ fontSize: 9, fontWeight: 'bold' }} 
                            stroke="#64748b" 
                          />
                          <YAxis 
                            tick={{ fontSize: 9, fontWeight: 'bold' }} 
                            stroke="#64748b" 
                            label={{ value: 'Entities Count', angle: -90, position: 'insideLeft', style: { fontSize: 10, fill: '#64748b', fontWeight: 'bold' } }}
                          />
                          <Tooltip 
                            labelFormatter={(t) => `Time: ${new Date(t).toLocaleTimeString()}`}
                            contentStyle={tooltipStyle}
                            itemStyle={{ padding: '1px 0', color: isDark ? '#cbd5e1' : '#334155' }}
                          />
                          <Legend wrapperStyle={{ fontSize: '9px', paddingTop: '5px', fontWeight: 'bold' }} />
                          {worldNames.map((worldName, idx) => (
                            <Line 
                              key={worldName} 
                              type="monotone" 
                              dataKey={`entitiesPerWorld.${worldName}`} 
                              stroke={WORLD_COLORS[idx % WORLD_COLORS.length]} 
                              strokeWidth={2}
                              dot={false}
                              name={`Entities: ${worldName}`} 
                            />
                          ))}
                        </LineChart>
                      </ResponsiveContainer>
                    )}
                  </div>
                </>
              )}
            </div>
          )}

          {activeTab === 'network' && (
            metrics.every(m => !m.avgPing || m.avgPing === 0) ? (
              <div className="flex flex-col items-center justify-center h-full min-h-[260px] text-center border border-dashed border-slate-200 dark:border-slate-800 rounded-xl bg-slate-50/40 dark:bg-slate-900/10 p-5">
                <div className="w-10 h-10 rounded-full bg-slate-100 dark:bg-slate-800 flex items-center justify-center text-slate-400 dark:text-slate-550 mb-3 border border-slate-200 dark:border-slate-700">
                  <Wifi className="w-5 h-5 text-sky-500 animate-pulse" />
                </div>
                <h4 className="text-xs font-bold text-slate-800 dark:text-slate-200 uppercase tracking-wide">No Latency Reports</h4>
                <p className="text-[10px] text-slate-500 dark:text-slate-400 mt-1 max-w-xs font-medium">
                  Player ping analytics are dynamically recorded and graphed only when players are actively connected to the server.
                </p>
              </div>
            ) : (
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={metrics} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                  <defs>
                    <linearGradient id="colorAvgPing" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#38bdf8" stopOpacity={0.25}/>
                      <stop offset="95%" stopColor="#38bdf8" stopOpacity={0}/>
                    </linearGradient>
                    <linearGradient id="colorMaxPing" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#c084fc" stopOpacity={0.15}/>
                      <stop offset="95%" stopColor="#c084fc" stopOpacity={0}/>
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" strokeOpacity={0.5} className="dark:hidden" />
                  <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" strokeOpacity={0.15} className="hidden dark:block" />
                  <XAxis 
                    dataKey="timestamp" 
                    tickFormatter={formatTime} 
                    tick={{ fontSize: 9, fontWeight: 'bold' }} 
                    stroke="#64748b" 
                  />
                  <YAxis 
                    domain={[0, 'auto']} 
                    tickFormatter={(v) => `${v}ms`}
                    tick={{ fontSize: 9, fontWeight: 'bold' }} 
                    stroke="#38bdf8" 
                    label={{ value: 'Ping Latency (ms)', angle: -90, position: 'insideLeft', style: { fontSize: 10, fill: '#38bdf8', fontWeight: 'bold' } }}
                  />
                  <Tooltip 
                    labelFormatter={(t) => `Time: ${new Date(t).toLocaleTimeString()}`}
                    contentStyle={tooltipStyle}
                    itemStyle={{ padding: '1px 0', color: isDark ? '#cbd5e1' : '#334155' }}
                  />
                  <Legend wrapperStyle={{ fontSize: '10px', paddingTop: '10px', fontWeight: 'bold' }} />
                  <Area 
                    type="monotone" 
                    dataKey="avgPing" 
                    stroke="#38bdf8" 
                    strokeWidth={2.5}
                    fillOpacity={1} 
                    fill="url(#colorAvgPing)" 
                    name="Average Player Ping"
                  />
                  <Area 
                    type="monotone" 
                    dataKey="maxPing" 
                    stroke="#c084fc" 
                    strokeWidth={2}
                    fillOpacity={1} 
                    fill="url(#colorMaxPing)" 
                    name="Peak Player Ping"
                  />
                </AreaChart>
              </ResponsiveContainer>
            )
          )}
        </div>
      </div>

      {/* Anomalies and Alerts Section */}
      <div className="bg-white/60 dark:bg-plan-card/60 border border-slate-200/50 dark:border-slate-800/40 shadow-sm rounded-2xl p-5 relative overflow-hidden">
        <h3 className="text-xs font-black text-slate-800 dark:text-white flex items-center gap-2 mb-4 uppercase tracking-wider">
          <AlertOctagon className="w-4 h-4 text-plan-red" /> Correlated Lag Anomalies & Triggers
        </h3>
        
        {anomalies.length === 0 ? (
          <div className="flex flex-col items-center justify-center p-8 border border-dashed border-slate-200 dark:border-slate-800/60 rounded-2xl text-center bg-slate-50/20 dark:bg-slate-900/5">
            <span className="w-8 h-8 rounded-full bg-emerald-500/10 flex items-center justify-center text-emerald-500 mb-3 text-xs font-black border border-emerald-500/20 select-none">
              ✓
            </span>
            <h4 className="text-[10px] font-bold text-slate-800 dark:text-slate-200 uppercase tracking-wide">No latency spikes detected</h4>
            <p className="text-[9px] text-slate-400 dark:text-slate-500 mt-1 max-w-sm font-semibold leading-relaxed">
              All logged MSPT intervals reside below the critical z-score threshold (2.5x standard deviation delta).
            </p>
          </div>
        ) : (
          <div className="space-y-3">
            {anomalies.map((anomaly, idx) => {
              const severityStyles: Record<string, string> = {
                LOW: 'border-l-2 border-l-plan-blue bg-slate-50/40 dark:bg-slate-900/10 border border-slate-200/40 dark:border-slate-800/40',
                MEDIUM: 'border-l-2 border-l-plan-yellow bg-slate-50/40 dark:bg-slate-900/10 border border-slate-200/40 dark:border-slate-800/40',
                HIGH: 'border-l-2 border-l-orange-500 bg-slate-50/40 dark:bg-slate-900/10 border border-slate-200/40 dark:border-slate-800/40',
                CRITICAL: 'border-l-2 border-l-plan-red bg-slate-50/40 dark:bg-slate-900/10 border border-slate-200/40 dark:border-slate-800/40'
              };

              const severityBadge: Record<string, string> = {
                LOW: 'bg-plan-blue/5 text-plan-blue border-plan-blue/10',
                MEDIUM: 'bg-plan-yellow/5 text-plan-yellow border-plan-yellow/10',
                HIGH: 'bg-orange-500/5 text-orange-500 border-orange-500/10',
                CRITICAL: 'bg-plan-red/5 text-plan-red border-plan-red/10'
              };

              return (
                <div 
                  key={idx} 
                  className={`p-4 rounded-xl flex flex-col md:flex-row md:items-center justify-between gap-4 transition-all duration-200 ${severityStyles[anomaly.severity] || 'bg-slate-50'}`}
                >
                  <div className="space-y-1.5 flex-1">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className={`text-[8px] font-black uppercase px-2 py-0.5 rounded-full border ${severityBadge[anomaly.severity]}`}>
                        {anomaly.severity} Severity
                      </span>
                      <span className="text-[9px] text-slate-450 dark:text-slate-500 font-bold">
                        {new Date(anomaly.timestamp).toLocaleString()}
                      </span>
                    </div>
                    <p className="text-xs font-semibold text-slate-700 dark:text-slate-205">
                      Tick rate slumped to <span className="text-plan-red font-black">{anomaly.tps.toFixed(2)} TPS</span> with latency spiking at <span className="text-plan-red font-black">{anomaly.mspt.toFixed(1)} ms MSPT</span>.
                    </p>

                    {/* Factors */}
                    {anomaly.correlatedFactors && anomaly.correlatedFactors.length > 0 && (
                      <div className="pt-2.5 space-y-1">
                        <span className="text-[8px] font-black uppercase tracking-wider text-slate-400 dark:text-slate-500 block mb-1.5">
                          Correlated Lag Factors:
                        </span>
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                          {anomaly.correlatedFactors.map((factor, fIdx) => (
                            <div key={fIdx} className="flex items-start gap-2 text-xs bg-slate-50/80 dark:bg-black/20 p-2 rounded-lg border border-slate-200/50 dark:border-slate-800/40">
                              <ChevronRight className="w-3.5 h-3.5 text-slate-400 mt-0.5 shrink-0" />
                              <div className="min-w-0">
                                <div className="flex items-center gap-2 font-bold text-slate-650 dark:text-slate-350">
                                  <span>{factor.factor} ({factor.value})</span>
                                  <span className="bg-slate-100 dark:bg-slate-800 text-[8px] px-1.5 py-0.2 rounded font-mono text-slate-450 dark:text-slate-500 font-bold shrink-0">
                                    Strength: {(factor.correlationStrength * 100).toFixed(0)}%
                                  </span>
                                </div>
                                <span className="text-slate-450 dark:text-slate-500 text-[9.5px] block mt-0.5 leading-tight font-medium">
                                  {factor.description}
                                </span>
                              </div>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>

                  <div className="shrink-0 flex flex-col items-start md:items-end text-xs font-bold text-slate-400 dark:text-slate-500">
                    <span className="flex items-center gap-1.5">
                      <TrendingUp className="w-3.5 h-3.5 text-plan-yellow" />
                      Z-Score Trigger
                    </span>
                    <span className="text-[9.5px] mt-0.5 font-black text-slate-650 dark:text-slate-400">
                      Standard Delta: +{(anomaly.mspt / 20).toFixed(1)}σ
                    </span>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
};
