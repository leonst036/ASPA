import React, { useEffect, useState, useCallback, useMemo } from 'react';
import {
  ResponsiveContainer, AreaChart, Area, LineChart, Line,
  XAxis, YAxis, CartesianGrid, Tooltip, Legend, ReferenceLine
} from 'recharts';
import {
  Activity, Cpu, Database, Users, Layers, Wifi,
  TrendingUp, RefreshCw, Clock, Pause, Play, BarChart3
} from 'lucide-react';
import { getLongtimeMetrics } from '../utils/api';
import type { ServerMetricsRecord } from '../types';

// ─── Time Range Presets ──────────────────────────────────────────────
interface TimeRange {
  label: string;
  ms: number;
  resolution: string;
  resolutionLabel: string;
}

const TIME_RANGES: TimeRange[] = [
  { label: '1h', ms: 3600000, resolution: '1m', resolutionLabel: '1min resolution' },
  { label: '6h', ms: 21600000, resolution: '5m', resolutionLabel: '5min resolution' },
  { label: '12h', ms: 43200000, resolution: '10m', resolutionLabel: '10min resolution' },
  { label: '24h', ms: 86400000, resolution: '15m', resolutionLabel: '15min resolution' },
  { label: '3d', ms: 259200000, resolution: '30m', resolutionLabel: '30min resolution' },
  { label: '7d', ms: 604800000, resolution: '1h', resolutionLabel: '1h resolution' },
  { label: '14d', ms: 1209600000, resolution: '2h', resolutionLabel: '2h resolution' },
  { label: '30d', ms: 2592000000, resolution: '6h', resolutionLabel: '6h resolution' },
];

// ─── Timestamp Formatting ────────────────────────────────────────────
const DAY_NAMES = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
const MONTH_NAMES = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

const createTimeFormatter = (rangeMs: number) => (ts: number): string => {
  const d = new Date(ts);
  if (rangeMs <= 86400000) {
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  }
  if (rangeMs <= 604800000) {
    return `${DAY_NAMES[d.getDay()]} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  }
  return `${MONTH_NAMES[d.getMonth()]} ${d.getDate()}`;
};

// ─── Component ───────────────────────────────────────────────────────
export const LongtimeGraphs: React.FC = () => {
  const [metrics, setMetrics] = useState<ServerMetricsRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedRange, setSelectedRange] = useState<number | 'custom'>(3); // index into TIME_RANGES, or 'custom'
  const [customDaysInput, setCustomDaysInput] = useState<number>(5);
  const [customDays, setCustomDays] = useState<number>(5);
  const [autoRefresh, setAutoRefresh] = useState(false);

  const currentRange = useMemo<TimeRange>(() => {
    if (selectedRange === 'custom') {
      const ms = customDays * 24 * 60 * 60 * 1000;

      // Determine resolution based on custom days (align with backend)
      let resolution = '6h';
      let resolutionLabel = '6h resolution';
      if (customDays <= 0.125) { // 3 hours
        resolution = '1m';
        resolutionLabel = '1min resolution';
      } else if (customDays <= 0.5) { // 12 hours
        resolution = '5m';
        resolutionLabel = '5min resolution';
      } else if (customDays <= 3) {
        resolution = '15m';
        resolutionLabel = '15min resolution';
      } else if (customDays <= 14) {
        resolution = '1h';
        resolutionLabel = '1h resolution';
      }

      return {
        label: `${customDays}d`,
        ms,
        resolution,
        resolutionLabel,
      };
    }
    return TIME_RANGES[selectedRange];
  }, [selectedRange, customDays]);

  // Debounce customDaysInput to customDays
  useEffect(() => {
    if (selectedRange !== 'custom') return;
    const timer = setTimeout(() => {
      if (customDaysInput >= 1) {
        setCustomDays(customDaysInput);
      }
    }, 450);
    return () => clearTimeout(timer);
  }, [customDaysInput, selectedRange]);

  const fetchData = useCallback(async (isSilent = false) => {
    if (!isSilent) setLoading(true);
    else setRefreshing(true);
    setError(null);

    try {
      const now = Date.now();
      const start = now - currentRange.ms;
      const data = await getLongtimeMetrics(start, now, currentRange.resolution);
      setMetrics(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch longtime metrics.');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [currentRange]);

  // Initial fetch & re-fetch on range change
  useEffect(() => {
    fetchData();
  }, [fetchData]);

  // Auto-refresh interval
  useEffect(() => {
    if (!autoRefresh) return;
    const interval = setInterval(() => fetchData(true), 60000);
    return () => clearInterval(interval);
  }, [autoRefresh, fetchData]);

  // ─── Computed Values ─────────────────────────────────────────────
  const formatTime = useMemo(() => createTimeFormatter(currentRange.ms), [currentRange.ms]);

  const isDark = document.documentElement.classList.contains('dark');

  const tooltipStyle = useMemo(() => ({
    backgroundColor: isDark ? 'rgba(26, 29, 36, 0.88)' : 'rgba(255, 255, 255, 0.88)',
    backdropFilter: 'blur(16px)',
    WebkitBackdropFilter: 'blur(16px)',
    borderRadius: '12px',
    border: isDark ? '1px solid rgba(255, 255, 255, 0.08)' : '1px solid rgba(0, 0, 0, 0.08)',
    fontSize: '11px',
    color: isDark ? '#f8fafc' : '#1e293b',
    boxShadow: '0 10px 25px -5px rgba(0,0,0,0.15), 0 8px 10px -6px rgba(0,0,0,0.06)',
    padding: '8px 12px',
  }), [isDark]);

  const summaryStats = useMemo(() => {
    if (!metrics.length) return null;
    const avgTps = metrics.reduce((s, m) => s + m.tps, 0) / metrics.length;
    const avgMspt = metrics.reduce((s, m) => s + m.mspt, 0) / metrics.length;
    const peakPlayers = Math.max(...metrics.map(m => m.onlinePlayers));
    const avgCpu = metrics.reduce((s, m) => s + m.cpuUsage, 0) / metrics.length;
    return { avgTps, avgMspt, peakPlayers, avgCpu };
  }, [metrics]);

  const chartData = useMemo(() => {
    return metrics.map(m => ({
      ...m,
      totalEntities: Object.values(m.entityCounts || {}).reduce((a, b) => a + b, 0),
      ramUsedGb: m.ramUsedMb / 1024,
    }));
  }, [metrics]);

  // ─── Loading State ───────────────────────────────────────────────
  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[500px]">
        <div className="flex flex-col items-center gap-3 text-brand-600 dark:text-dark-300">
          <RefreshCw className="w-8 h-8 animate-spin text-plan-cyan" />
          <span className="text-sm font-medium tracking-wide text-slate-500 dark:text-slate-400">Loading longtime metrics...</span>
        </div>
      </div>
    );
  }

  // ─── Error State ─────────────────────────────────────────────────
  if (error && metrics.length === 0) {
    return (
      <div className="p-6 bg-red-50 dark:bg-red-950/20 border border-red-200 dark:border-red-900/50 rounded-2xl text-red-600 dark:text-red-400">
        <h3 className="font-semibold text-lg flex items-center gap-2">
          <BarChart3 className="w-5 h-5" /> Longtime Metrics Error
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

  // ─── Empty State ─────────────────────────────────────────────────
  if (metrics.length === 0) {
    return (
      <div className="space-y-6">
        <Header
          currentRange={currentRange}
          selectedRange={selectedRange}
          setSelectedRange={setSelectedRange}
          customDaysInput={customDaysInput}
          setCustomDaysInput={setCustomDaysInput}
          autoRefresh={autoRefresh}
          setAutoRefresh={setAutoRefresh}
          refreshing={refreshing}
          onRefresh={() => fetchData(true)}
        />
        <div className="flex flex-col items-center justify-center min-h-[300px] border-2 border-dashed border-slate-200 dark:border-slate-800 rounded-2xl bg-slate-50/40 dark:bg-slate-900/10 p-8">
          <div className="w-12 h-12 rounded-full bg-slate-100 dark:bg-slate-800 flex items-center justify-center text-slate-400 dark:text-slate-500 mb-4 border border-slate-200 dark:border-slate-700">
            <BarChart3 className="w-6 h-6" />
          </div>
          <h4 className="text-sm font-bold text-slate-800 dark:text-slate-200 uppercase tracking-wide">No Historical Data</h4>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-2 max-w-sm font-medium text-center">
            Longtime metrics will appear here once the server has been recording data for the selected time range. Try a shorter range or wait for data collection.
          </p>
        </div>
      </div>
    );
  }

  // ─── Main Render ─────────────────────────────────────────────────
  return (
    <div className="space-y-6">
      {/* Header with Time Range Selector */}
      <Header
        currentRange={currentRange}
        selectedRange={selectedRange}
        setSelectedRange={setSelectedRange}
        customDaysInput={customDaysInput}
        setCustomDaysInput={setCustomDaysInput}
        autoRefresh={autoRefresh}
        setAutoRefresh={setAutoRefresh}
        refreshing={refreshing}
        onRefresh={() => fetchData(true)}
      />

      {/* Summary Stats Row */}
      {summaryStats && (
        <div className="space-y-3.5">
          <span className="text-[9px] font-black uppercase tracking-[0.18em] text-slate-400 dark:text-slate-500 pl-1 block">
            Aggregated Statistics — {currentRange.label} Window
          </span>
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            <StatCard
              icon={<Activity className="w-4.5 h-4.5" />}
              label="Average TPS"
              value={summaryStats.avgTps.toFixed(2)}
              suffix="tps"
              color="emerald"
            />
            <StatCard
              icon={<Clock className="w-4.5 h-4.5" />}
              label="Average MSPT"
              value={summaryStats.avgMspt.toFixed(1)}
              suffix="ms"
              color="cyan"
            />
            <StatCard
              icon={<Users className="w-4.5 h-4.5" />}
              label="Peak Players"
              value={String(summaryStats.peakPlayers)}
              color="blue"
            />
            <StatCard
              icon={<Cpu className="w-4.5 h-4.5" />}
              label="Average CPU"
              value={summaryStats.avgCpu.toFixed(1)}
              suffix="%"
              color="amber"
            />
          </div>
        </div>
      )}

      {/* Grafana-Style Chart Grid */}
      <div className="space-y-3.5">
        <span className="text-[9px] font-black uppercase tracking-[0.18em] text-slate-400 dark:text-slate-500 pl-1 block">
          Historical Panels
        </span>

        <div className="space-y-5">
          {/* Panel 1: TPS & MSPT */}
          <ChartPanel
            icon={<Activity className="w-4.5 h-4.5" />}
            title="TPS & MSPT Performance"
            subtitle="Server tick health over time"
            badge={currentRange.resolutionLabel}
            iconColor="text-emerald-500 bg-emerald-500/10 dark:bg-emerald-500/5"
          >
            <ResponsiveContainer width="100%" height={260}>
              <AreaChart data={chartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                <defs>
                  <linearGradient id="lt-tps" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#10b981" stopOpacity={0.2} />
                    <stop offset="95%" stopColor="#10b981" stopOpacity={0} />
                  </linearGradient>
                  <linearGradient id="lt-mspt" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#06b6d4" stopOpacity={0.2} />
                    <stop offset="95%" stopColor="#06b6d4" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" strokeOpacity={0.4} className="dark:hidden" />
                <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" strokeOpacity={0.1} className="hidden dark:block" />
                <XAxis
                  dataKey="timestamp"
                  tickFormatter={formatTime}
                  tick={{ fontSize: 9, fontWeight: 'bold' }}
                  stroke="#64748b"
                />
                <YAxis
                  yAxisId="left"
                  domain={[0, 21]}
                  tick={{ fontSize: 9, fontWeight: 'bold' }}
                  stroke="#10b981"
                  label={{ value: 'TPS', angle: -90, position: 'insideLeft', style: { fontSize: 10, fill: '#10b981', fontWeight: 'bold' } }}
                />
                <YAxis
                  yAxisId="right"
                  orientation="right"
                  domain={[0, 'auto']}
                  tick={{ fontSize: 9, fontWeight: 'bold' }}
                  stroke="#06b6d4"
                  label={{ value: 'MSPT (ms)', angle: 90, position: 'insideRight', style: { fontSize: 10, fill: '#06b6d4', fontWeight: 'bold' } }}
                />
                <Tooltip
                  labelFormatter={(t) => `${new Date(t as number).toLocaleString()}`}
                  contentStyle={tooltipStyle}
                  itemStyle={{ padding: '1px 0', color: isDark ? '#cbd5e1' : '#334155' }}
                />
                <Legend wrapperStyle={{ fontSize: '10px', paddingTop: '10px', fontWeight: 'bold' }} />
                <Area
                  yAxisId="left"
                  type="monotone"
                  dataKey="tps"
                  stroke="#10b981"
                  strokeWidth={1.5}
                  fillOpacity={1}
                  fill="url(#lt-tps)"
                  name="TPS"
                  dot={false}
                  animationDuration={800}
                />
                <Area
                  yAxisId="right"
                  type="monotone"
                  dataKey="mspt"
                  stroke="#06b6d4"
                  strokeWidth={1.5}
                  fillOpacity={1}
                  fill="url(#lt-mspt)"
                  name="MSPT (ms)"
                  dot={false}
                  animationDuration={800}
                />
              </AreaChart>
            </ResponsiveContainer>
          </ChartPanel>

          {/* Panel 2: CPU Utilization */}
          <ChartPanel
            icon={<Cpu className="w-4.5 h-4.5" />}
            title="CPU Utilization"
            subtitle="Process CPU consumption percentage"
            badge={currentRange.resolutionLabel}
            iconColor="text-amber-500 bg-amber-500/10 dark:bg-amber-500/5"
          >
            <ResponsiveContainer width="100%" height={260}>
              <AreaChart data={chartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                <defs>
                  <linearGradient id="lt-cpu" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#f59e0b" stopOpacity={0.2} />
                    <stop offset="95%" stopColor="#f59e0b" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" strokeOpacity={0.4} className="dark:hidden" />
                <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" strokeOpacity={0.1} className="hidden dark:block" />
                <XAxis
                  dataKey="timestamp"
                  tickFormatter={formatTime}
                  tick={{ fontSize: 9, fontWeight: 'bold' }}
                  stroke="#64748b"
                />
                <YAxis
                  domain={[0, 100]}
                  tickFormatter={(v) => `${v}%`}
                  tick={{ fontSize: 9, fontWeight: 'bold' }}
                  stroke="#f59e0b"
                  label={{ value: 'CPU %', angle: -90, position: 'insideLeft', style: { fontSize: 10, fill: '#f59e0b', fontWeight: 'bold' } }}
                />
                <ReferenceLine y={80} stroke="#ef4444" strokeDasharray="6 4" strokeOpacity={0.6} label={{ value: '80% Warning', position: 'insideTopRight', style: { fontSize: 9, fill: '#ef4444', fontWeight: 'bold' } }} />
                <Tooltip
                  labelFormatter={(t) => `${new Date(t as number).toLocaleString()}`}
                  contentStyle={tooltipStyle}
                  itemStyle={{ padding: '1px 0', color: isDark ? '#cbd5e1' : '#334155' }}
                  formatter={(value: number) => [`${value.toFixed(1)}%`, 'CPU Usage']}
                />
                <Legend wrapperStyle={{ fontSize: '10px', paddingTop: '10px', fontWeight: 'bold' }} />
                <Area
                  type="monotone"
                  dataKey="cpuUsage"
                  stroke="#f59e0b"
                  strokeWidth={1.5}
                  fillOpacity={1}
                  fill="url(#lt-cpu)"
                  name="CPU Usage (%)"
                  dot={false}
                  animationDuration={800}
                />
              </AreaChart>
            </ResponsiveContainer>
          </ChartPanel>

          {/* Panel 3: Memory Allocation */}
          <ChartPanel
            icon={<Database className="w-4.5 h-4.5" />}
            title="Memory Allocation"
            subtitle="Heap usage and GC pause overhead"
            badge={currentRange.resolutionLabel}
            iconColor="text-purple-500 bg-purple-500/10 dark:bg-purple-500/5"
          >
            <ResponsiveContainer width="100%" height={260}>
              <AreaChart data={chartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                <defs>
                  <linearGradient id="lt-ram" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#8b5cf6" stopOpacity={0.2} />
                    <stop offset="95%" stopColor="#8b5cf6" stopOpacity={0} />
                  </linearGradient>
                  <linearGradient id="lt-gc" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#f59e0b" stopOpacity={0.12} />
                    <stop offset="95%" stopColor="#f59e0b" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" strokeOpacity={0.4} className="dark:hidden" />
                <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" strokeOpacity={0.1} className="hidden dark:block" />
                <XAxis
                  dataKey="timestamp"
                  tickFormatter={formatTime}
                  tick={{ fontSize: 9, fontWeight: 'bold' }}
                  stroke="#64748b"
                />
                <YAxis
                  yAxisId="left"
                  tickFormatter={(v) => `${(v / 1024).toFixed(1)}G`}
                  tick={{ fontSize: 9, fontWeight: 'bold' }}
                  stroke="#8b5cf6"
                  label={{ value: 'Memory (GB)', angle: -90, position: 'insideLeft', style: { fontSize: 10, fill: '#8b5cf6', fontWeight: 'bold' } }}
                />
                <YAxis
                  yAxisId="right"
                  orientation="right"
                  domain={[0, 'auto']}
                  tickFormatter={(v) => `${v}ms`}
                  tick={{ fontSize: 9, fontWeight: 'bold' }}
                  stroke="#f59e0b"
                  label={{ value: 'GC Pause', angle: 90, position: 'insideRight', style: { fontSize: 10, fill: '#f59e0b', fontWeight: 'bold' } }}
                />
                <Tooltip
                  labelFormatter={(t) => `${new Date(t as number).toLocaleString()}`}
                  contentStyle={tooltipStyle}
                  itemStyle={{ padding: '1px 0', color: isDark ? '#cbd5e1' : '#334155' }}
                />
                <Legend wrapperStyle={{ fontSize: '10px', paddingTop: '10px', fontWeight: 'bold' }} />
                <Area
                  yAxisId="left"
                  type="monotone"
                  dataKey="ramUsedMb"
                  stroke="#8b5cf6"
                  strokeWidth={1.5}
                  fillOpacity={1}
                  fill="url(#lt-ram)"
                  name="RAM Used (MB)"
                  dot={false}
                  animationDuration={800}
                />
                <Area
                  yAxisId="right"
                  type="monotone"
                  dataKey="gcTimeDeltaMs"
                  stroke="#f59e0b"
                  strokeWidth={1.5}
                  fillOpacity={1}
                  fill="url(#lt-gc)"
                  name="GC Pause (ms)"
                  dot={false}
                  animationDuration={800}
                />
              </AreaChart>
            </ResponsiveContainer>
          </ChartPanel>

          {/* Panel 4: Player Count */}
          <ChartPanel
            icon={<Users className="w-4.5 h-4.5" />}
            title="Player Count"
            subtitle="Online players over time"
            badge={currentRange.resolutionLabel}
            iconColor="text-blue-500 bg-blue-500/10 dark:bg-blue-500/5"
          >
            <ResponsiveContainer width="100%" height={260}>
              <AreaChart data={chartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                <defs>
                  <linearGradient id="lt-players" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.2} />
                    <stop offset="95%" stopColor="#3b82f6" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" strokeOpacity={0.4} className="dark:hidden" />
                <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" strokeOpacity={0.1} className="hidden dark:block" />
                <XAxis
                  dataKey="timestamp"
                  tickFormatter={formatTime}
                  tick={{ fontSize: 9, fontWeight: 'bold' }}
                  stroke="#64748b"
                />
                <YAxis
                  domain={[0, 'auto']}
                  allowDecimals={false}
                  tick={{ fontSize: 9, fontWeight: 'bold' }}
                  stroke="#3b82f6"
                  label={{ value: 'Players', angle: -90, position: 'insideLeft', style: { fontSize: 10, fill: '#3b82f6', fontWeight: 'bold' } }}
                />
                <Tooltip
                  labelFormatter={(t) => `${new Date(t as number).toLocaleString()}`}
                  contentStyle={tooltipStyle}
                  itemStyle={{ padding: '1px 0', color: isDark ? '#cbd5e1' : '#334155' }}
                  formatter={(value: number) => [value, 'Players Online']}
                />
                <Legend wrapperStyle={{ fontSize: '10px', paddingTop: '10px', fontWeight: 'bold' }} />
                <Area
                  type="stepAfter"
                  dataKey="onlinePlayers"
                  stroke="#3b82f6"
                  strokeWidth={1.5}
                  fillOpacity={1}
                  fill="url(#lt-players)"
                  name="Online Players"
                  dot={false}
                  animationDuration={800}
                />
              </AreaChart>
            </ResponsiveContainer>
          </ChartPanel>

          {/* Panel 5: Network Latency */}
          <ChartPanel
            icon={<Wifi className="w-4.5 h-4.5" />}
            title="Network Latency"
            subtitle="Average and peak player ping"
            badge={currentRange.resolutionLabel}
            iconColor="text-sky-500 bg-sky-500/10 dark:bg-sky-500/5"
          >
            <ResponsiveContainer width="100%" height={260}>
              <AreaChart data={chartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                <defs>
                  <linearGradient id="lt-avgPing" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#38bdf8" stopOpacity={0.2} />
                    <stop offset="95%" stopColor="#38bdf8" stopOpacity={0} />
                  </linearGradient>
                  <linearGradient id="lt-maxPing" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#c084fc" stopOpacity={0.15} />
                    <stop offset="95%" stopColor="#c084fc" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" strokeOpacity={0.4} className="dark:hidden" />
                <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" strokeOpacity={0.1} className="hidden dark:block" />
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
                  label={{ value: 'Latency (ms)', angle: -90, position: 'insideLeft', style: { fontSize: 10, fill: '#38bdf8', fontWeight: 'bold' } }}
                />
                <Tooltip
                  labelFormatter={(t) => `${new Date(t as number).toLocaleString()}`}
                  contentStyle={tooltipStyle}
                  itemStyle={{ padding: '1px 0', color: isDark ? '#cbd5e1' : '#334155' }}
                />
                <Legend wrapperStyle={{ fontSize: '10px', paddingTop: '10px', fontWeight: 'bold' }} />
                <Area
                  type="monotone"
                  dataKey="avgPing"
                  stroke="#38bdf8"
                  strokeWidth={1.5}
                  fillOpacity={1}
                  fill="url(#lt-avgPing)"
                  name="Avg Ping (ms)"
                  dot={false}
                  animationDuration={800}
                />
                <Area
                  type="monotone"
                  dataKey="maxPing"
                  stroke="#c084fc"
                  strokeWidth={1.5}
                  fillOpacity={1}
                  fill="url(#lt-maxPing)"
                  name="Max Ping (ms)"
                  dot={false}
                  animationDuration={800}
                />
              </AreaChart>
            </ResponsiveContainer>
          </ChartPanel>

          {/* Panel 6: Entities & Chunks */}
          <ChartPanel
            icon={<Layers className="w-4.5 h-4.5" />}
            title="Entities & Chunks"
            subtitle="World simulation load metrics"
            badge={currentRange.resolutionLabel}
            iconColor="text-teal-500 bg-teal-500/10 dark:bg-teal-500/5"
          >
            <ResponsiveContainer width="100%" height={260}>
              <AreaChart data={chartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                <defs>
                  <linearGradient id="lt-chunks" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#14b8a6" stopOpacity={0.2} />
                    <stop offset="95%" stopColor="#14b8a6" stopOpacity={0} />
                  </linearGradient>
                  <linearGradient id="lt-entities" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#fb7185" stopOpacity={0.12} />
                    <stop offset="95%" stopColor="#fb7185" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" strokeOpacity={0.4} className="dark:hidden" />
                <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" strokeOpacity={0.1} className="hidden dark:block" />
                <XAxis
                  dataKey="timestamp"
                  tickFormatter={formatTime}
                  tick={{ fontSize: 9, fontWeight: 'bold' }}
                  stroke="#64748b"
                />
                <YAxis
                  yAxisId="left"
                  domain={[0, 'auto']}
                  tick={{ fontSize: 9, fontWeight: 'bold' }}
                  stroke="#14b8a6"
                  label={{ value: 'Chunks', angle: -90, position: 'insideLeft', style: { fontSize: 10, fill: '#14b8a6', fontWeight: 'bold' } }}
                />
                <YAxis
                  yAxisId="right"
                  orientation="right"
                  domain={[0, 'auto']}
                  tick={{ fontSize: 9, fontWeight: 'bold' }}
                  stroke="#fb7185"
                  label={{ value: 'Entities', angle: 90, position: 'insideRight', style: { fontSize: 10, fill: '#fb7185', fontWeight: 'bold' } }}
                />
                <Tooltip
                  labelFormatter={(t) => `${new Date(t as number).toLocaleString()}`}
                  contentStyle={tooltipStyle}
                  itemStyle={{ padding: '1px 0', color: isDark ? '#cbd5e1' : '#334155' }}
                />
                <Legend wrapperStyle={{ fontSize: '10px', paddingTop: '10px', fontWeight: 'bold' }} />
                <Area
                  yAxisId="left"
                  type="monotone"
                  dataKey="loadedChunks"
                  stroke="#14b8a6"
                  strokeWidth={1.5}
                  fillOpacity={1}
                  fill="url(#lt-chunks)"
                  name="Loaded Chunks"
                  dot={false}
                  animationDuration={800}
                />
                <Line
                  yAxisId="right"
                  type="monotone"
                  dataKey="totalEntities"
                  stroke="#fb7185"
                  strokeWidth={1.5}
                  name="Total Entities"
                  dot={false}
                  animationDuration={800}
                />
              </AreaChart>
            </ResponsiveContainer>
          </ChartPanel>
        </div>
      </div>
    </div>
  );
};

// ─── Sub-Components ──────────────────────────────────────────────────

interface HeaderProps {
  currentRange: TimeRange;
  selectedRange: number | 'custom';
  setSelectedRange: (i: number | 'custom') => void;
  customDaysInput: number;
  setCustomDaysInput: (d: number) => void;
  autoRefresh: boolean;
  setAutoRefresh: (v: boolean) => void;
  refreshing: boolean;
  onRefresh: () => void;
}

const Header: React.FC<HeaderProps> = ({
  currentRange, selectedRange, setSelectedRange,
  customDaysInput, setCustomDaysInput,
  autoRefresh, setAutoRefresh, refreshing, onRefresh,
}) => (
  <div className="space-y-4">
    <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
      <div>
        <h2 className="text-xl font-extrabold tracking-tight text-slate-800 dark:text-white flex items-center gap-2">
          <TrendingUp className="w-5.5 h-5.5 text-plan-cyan" /> Longtime Analytics
        </h2>
        <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
          Historical server performance panels with configurable time windows.
        </p>
      </div>
      <div className="flex items-center gap-2.5">
        {/* Resolution Badge */}
        <span className="hidden sm:flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-plan-cyan/10 dark:bg-plan-cyan/5 border border-plan-cyan/20 dark:border-plan-cyan/10 text-[9.5px] font-bold text-plan-cyan uppercase tracking-wider select-none">
          <Clock className="w-3 h-3" />
          {currentRange.resolutionLabel}
        </span>

        {/* Auto-Refresh Toggle */}
        <button
          onClick={() => setAutoRefresh(!autoRefresh)}
          className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg border text-xs font-bold transition-all ${autoRefresh
              ? 'border-emerald-400/50 dark:border-emerald-600/30 bg-emerald-50 dark:bg-emerald-950/20 text-emerald-600 dark:text-emerald-400'
              : 'border-slate-200 dark:border-slate-800/80 bg-white dark:bg-slate-900 text-slate-500 dark:text-slate-400'
            }`}
          title={autoRefresh ? 'Auto-refresh ON (60s)' : 'Auto-refresh OFF'}
        >
          {autoRefresh ? <Pause className="w-3 h-3" /> : <Play className="w-3 h-3" />}
          <span className="hidden sm:inline">{autoRefresh ? 'Auto: ON' : 'Auto: OFF'}</span>
        </button>

        {/* Manual Refresh */}
        <button
          onClick={onRefresh}
          disabled={refreshing}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-800/80 bg-white dark:bg-slate-900 text-slate-700 dark:text-slate-350 text-xs font-bold shadow-sm hover:bg-slate-50 dark:hover:bg-slate-800 active:scale-[0.98] transition-all"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${refreshing ? 'animate-spin text-plan-cyan' : ''}`} />
          Refresh
        </button>
      </div>
    </div>

    {/* Time Range Selector */}
    <div className="flex flex-col sm:flex-row items-start sm:items-center gap-3">
      <div className="flex overflow-x-auto scrollbar-none bg-slate-100/50 dark:bg-slate-900/60 p-1 rounded-xl border border-slate-200/40 dark:border-slate-800/40 w-full sm:w-auto">
        <div className="flex flex-nowrap gap-1 min-w-max">
          {TIME_RANGES.map((range, idx) => (
            <button
              key={range.label}
              onClick={() => setSelectedRange(idx)}
              className={`px-4 py-2 rounded-lg text-xs font-bold tracking-wide transition-all whitespace-nowrap ${selectedRange === idx
                  ? 'bg-white dark:bg-slate-800 text-plan-blue dark:text-cyan-400 shadow-sm font-black'
                  : 'text-slate-500 dark:text-slate-450 hover:text-slate-900 dark:hover:text-white'
                }`}
            >
              {range.label}
            </button>
          ))}
          <button
            onClick={() => setSelectedRange('custom')}
            className={`px-4 py-2 rounded-lg text-xs font-bold tracking-wide transition-all whitespace-nowrap ${selectedRange === 'custom'
                ? 'bg-white dark:bg-slate-800 text-plan-blue dark:text-cyan-400 shadow-sm font-black'
                : 'text-slate-500 dark:text-slate-450 hover:text-slate-900 dark:hover:text-white'
              }`}
          >
            Custom
          </button>
        </div>
      </div>

      {selectedRange === 'custom' && (
        <div className="flex items-center gap-1.5 bg-slate-100/50 dark:bg-slate-900/60 px-2 py-1 rounded-xl border border-slate-200/40 dark:border-slate-800/40 text-slate-700 dark:text-slate-350 shadow-inner h-9 animate-fade-in shrink-0">
          <button
            type="button"
            onClick={() => setCustomDaysInput(Math.max(1, customDaysInput - 1))}
            className="w-6 h-6 rounded-md flex items-center justify-center bg-white dark:bg-slate-800 border border-slate-200/60 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-750 active:scale-95 transition-all text-xs font-bold select-none text-slate-500 dark:text-slate-400"
          >
            -
          </button>
          <input
            type="number"
            min="1"
            max="365"
            value={customDaysInput || ''}
            onChange={(e) => {
              const val = parseInt(e.target.value, 10);
              if (!isNaN(val)) {
                setCustomDaysInput(Math.max(1, Math.min(365, val)));
              } else {
                setCustomDaysInput(0);
              }
            }}
            onBlur={() => {
              if (!customDaysInput || customDaysInput < 1) setCustomDaysInput(1);
            }}
            className="w-10 bg-transparent border-0 outline-none text-slate-800 dark:text-white text-xs font-black text-center p-0 focus:ring-0 focus:outline-none [appearance:textfield] [&::-webkit-outer-spin-button]:appearance-none [&::-webkit-inner-spin-button]:appearance-none"
          />
          <span className="text-[10px] font-bold text-slate-400 dark:text-slate-500 uppercase select-none pr-1">Days</span>
          <button
            type="button"
            onClick={() => setCustomDaysInput(Math.min(365, customDaysInput + 1))}
            className="w-6 h-6 rounded-md flex items-center justify-center bg-white dark:bg-slate-800 border border-slate-200/60 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-750 active:scale-95 transition-all text-xs font-bold select-none text-slate-500 dark:text-slate-400"
          >
            +
          </button>
        </div>
      )}

      {/* Mobile resolution badge */}
      <span className="flex sm:hidden items-center gap-1.5 px-2.5 py-1 rounded-lg bg-plan-cyan/10 dark:bg-plan-cyan/5 border border-plan-cyan/20 dark:border-plan-cyan/10 text-[9.5px] font-bold text-plan-cyan uppercase tracking-wider select-none">
        <Clock className="w-3 h-3" />
        {currentRange.resolutionLabel}
      </span>
    </div>
  </div>
);

// ─── Stat Card ───────────────────────────────────────────────────────
interface StatCardProps {
  icon: React.ReactNode;
  label: string;
  value: string;
  suffix?: string;
  color: 'emerald' | 'cyan' | 'blue' | 'amber';
}

const COLOR_MAP: Record<string, { icon: string; text: string; border: string }> = {
  emerald: {
    icon: 'text-emerald-500 bg-emerald-500/10 dark:bg-emerald-500/5',
    text: 'text-emerald-600 dark:text-emerald-400',
    border: 'hover:border-emerald-400/30 dark:hover:border-emerald-500/20',
  },
  cyan: {
    icon: 'text-plan-cyan bg-plan-cyan/10 dark:bg-plan-cyan/5',
    text: 'text-plan-cyan',
    border: 'hover:border-plan-cyan/30 dark:hover:border-plan-cyan/20',
  },
  blue: {
    icon: 'text-blue-500 bg-blue-500/10 dark:bg-blue-500/5',
    text: 'text-blue-600 dark:text-blue-400',
    border: 'hover:border-blue-400/30 dark:hover:border-blue-500/20',
  },
  amber: {
    icon: 'text-amber-500 bg-amber-500/10 dark:bg-amber-500/5',
    text: 'text-amber-600 dark:text-amber-400',
    border: 'hover:border-amber-400/30 dark:hover:border-amber-500/20',
  },
};

const StatCard: React.FC<StatCardProps> = ({ icon, label, value, suffix, color }) => {
  const c = COLOR_MAP[color];
  return (
    <div className={`flex items-center p-4 rounded-2xl bg-white/60 dark:bg-plan-card/60 border border-slate-200/50 dark:border-slate-800/40 hover:bg-white dark:hover:bg-plan-card/85 transition-all duration-300 hover:shadow-sm ${c.border}`}>
      <div className={`w-9 h-9 rounded-xl flex items-center justify-center shrink-0 transition-colors ${c.icon}`}>
        {icon}
      </div>
      <div className="ml-3.5 flex-1 min-w-0">
        <span className="text-[9px] font-bold text-slate-400 dark:text-slate-500 uppercase tracking-wider block">{label}</span>
        <span className={`text-lg font-black block mt-0.5 ${c.text}`}>
          {value}
          {suffix && <span className="text-[10px] font-semibold text-slate-400 dark:text-slate-500 ml-0.5">{suffix}</span>}
        </span>
      </div>
    </div>
  );
};

// ─── Chart Panel ─────────────────────────────────────────────────────
interface ChartPanelProps {
  icon: React.ReactNode;
  title: string;
  subtitle: string;
  badge: string;
  iconColor: string;
  children: React.ReactNode;
}

const ChartPanel: React.FC<ChartPanelProps> = ({ icon, title, subtitle, badge, iconColor, children }) => (
  <div className="bg-white/40 dark:bg-plan-card/40 border border-slate-200/30 dark:border-slate-800/30 rounded-2xl p-5 transition-all duration-300 hover:border-plan-cyan/20 dark:hover:border-plan-cyan/15 hover:shadow-md hover:shadow-plan-cyan/[0.03]">
    {/* Panel Header */}
    <div className="flex items-center justify-between mb-4">
      <div className="flex items-center gap-3">
        <div className={`w-8 h-8 rounded-lg flex items-center justify-center shrink-0 ${iconColor}`}>
          {icon}
        </div>
        <div>
          <h3 className="text-sm font-extrabold text-slate-800 dark:text-white tracking-tight">{title}</h3>
          <p className="text-[10px] text-slate-400 dark:text-slate-500 font-medium mt-0.5">{subtitle}</p>
        </div>
      </div>
      <span className="px-2 py-0.5 rounded-md bg-slate-100/80 dark:bg-slate-800/60 text-[8.5px] font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider border border-slate-200/40 dark:border-slate-700/30 select-none">
        {badge}
      </span>
    </div>
    {/* Chart Content */}
    <div className="w-full">
      {children}
    </div>
  </div>
);
