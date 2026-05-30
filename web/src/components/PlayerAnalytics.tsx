import React, { useEffect, useState, useCallback } from 'react';
import { 
  Users, 
  Clock, 
  MapPin, 
  Calendar, 
  TrendingUp, 
  RefreshCw, 
  Globe, 
  Award, 
  ArrowUpRight 
} from 'lucide-react';
import { getPlayerAnalytics, getForecast } from '../utils/api';
import type { RetentionReport, ForecastResult } from '../types';

export const PlayerAnalytics: React.FC = () => {
  const [analytics, setAnalytics] = useState<RetentionReport | null>(null);
  const [forecast, setForecast] = useState<ForecastResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [hoveredCell, setHoveredCell] = useState<{ day: number; hour: number; val: number } | null>(null);

  const fetchData = useCallback(async (isSilent = false) => {
    if (!isSilent) setLoading(true);
    else setRefreshing(true);
    setError(null);

    try {
      const [analyticsData, forecastData] = await Promise.all([
        getPlayerAnalytics(),
        getForecast()
      ]);
      setAnalytics(analyticsData);
      setForecast(forecastData);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch player analytics.');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="flex flex-col items-center gap-3 text-brand-600 dark:text-dark-300">
          <RefreshCw className="w-8 h-8 animate-spin" />
          <span className="text-sm font-medium tracking-wide">Crunching player session cohort retention data...</span>
        </div>
      </div>
    );
  }

  if (error || !analytics) {
    return (
      <div className="p-6 bg-red-50 dark:bg-red-950/20 border border-red-200 dark:border-red-900/50 rounded-2xl text-red-600 dark:text-red-400">
        <h3 className="font-semibold text-lg">Analysis Calculation Error</h3>
        <p className="mt-2 text-sm opacity-95">{error || 'Data is unavailable'}</p>
        <button 
          onClick={() => fetchData()} 
          className="mt-4 px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-xl text-xs font-semibold tracking-wider transition-all"
        >
          Recalculate Metrics
        </button>
      </div>
    );
  }

  const formatPlaytime = (ms: number) => {
    const totalMinutes = Math.floor(ms / 60000);
    const hours = Math.floor(totalMinutes / 60);
    const minutes = totalMinutes % 60;
    if (hours > 0) return `${hours}h ${minutes}m`;
    return `${minutes}m`;
  };

  const daysOfWeek = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

  // Color mapping for activity punchcard based on intensity weight
  const getPunchcardColor = (val: number, max: number) => {
    if (val === 0) return 'bg-slate-100/50 dark:bg-slate-900/40';
    const ratio = val / max;
    if (ratio < 0.25) return 'bg-indigo-500/10 dark:bg-indigo-500/5 text-indigo-400';
    if (ratio < 0.5) return 'bg-indigo-500/25 dark:bg-indigo-500/15 text-indigo-300';
    if (ratio < 0.75) return 'bg-indigo-500/50 dark:bg-indigo-500/40 text-indigo-100';
    return 'bg-indigo-500 dark:bg-indigo-600 text-white';
  };

  // Find max value in punchcard for scaling circles/colors
  const maxWeight = analytics.punchcardMatrix.length > 0 
    ? Math.max(...analytics.punchcardMatrix.map(row => Math.max(...row))) 
    : 100;

  const geoDist = analytics.geographicDistribution || [];
  const totalGeoSessions = geoDist.reduce((acc, c) => acc + c.count, 0);
  const primaryCountry = geoDist[0] 
    ? `${geoDist[0].countryName} (${((geoDist[0].count / (totalGeoSessions || 1)) * 100).toFixed(0)}%)` 
    : 'None (0%)';
  const secondaryCountry = geoDist[1] 
    ? `${geoDist[1].countryName} (${((geoDist[1].count / (totalGeoSessions || 1)) * 100).toFixed(0)}%)` 
    : 'None (0%)';
  
  const averageLatency = analytics.averagePing !== undefined && analytics.averagePing > 0
    ? `${analytics.averagePing} ms`
    : 'N/A';

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h2 className="text-xl font-extrabold tracking-tight text-slate-800 dark:text-white flex items-center gap-2">
            <Globe className="w-5.5 h-5.5 text-plan-cyan" /> Player Cohorts & Behavior
          </h2>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
            Cohort retention ratios, micro-session aggregate active peak times, and global geographic demographics.
          </p>
        </div>
        <button
          onClick={() => fetchData(true)}
          disabled={refreshing}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 text-slate-700 dark:text-slate-350 text-xs font-bold shadow-sm hover:bg-slate-50 dark:hover:bg-slate-800 active:scale-[0.98] transition-all"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${refreshing ? 'animate-spin' : ''}`} />
          Recalculate
        </button>
      </div>

      {/* Cohort Stats Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-4">
        {/* New Players */}
        <div className="flex items-center p-5 rounded-2xl bg-white/60 dark:bg-plan-card/60 border border-slate-200/50 dark:border-slate-800/40 hover:bg-white dark:hover:bg-plan-card/85 transition-all duration-300 hover:shadow-sm hover:border-plan-blue/30 dark:hover:border-plan-blue/30">
          <div className="w-10 h-10 rounded-xl bg-plan-blue/5 dark:bg-plan-blue/10 text-plan-blue flex items-center justify-center shrink-0">
            <Users className="w-5 h-5" />
          </div>
          <div className="ml-4 flex-1 min-w-0">
            <span className="text-[9px] font-bold text-slate-400 dark:text-slate-500 uppercase tracking-wider block">Acquisition</span>
            <span className="text-xl font-black text-slate-800 dark:text-white block mt-0.5">
              {analytics.newPlayers}
            </span>
            <span className="text-[8px] text-slate-550 dark:text-slate-500 font-bold block mt-0.5">New players (Last 14d)</span>
          </div>
        </div>

        {/* Returning Players */}
        <div className="flex items-center p-5 rounded-2xl bg-white/60 dark:bg-plan-card/60 border border-slate-200/50 dark:border-slate-800/40 hover:bg-white dark:hover:bg-plan-card/85 transition-all duration-300 hover:shadow-sm hover:border-plan-cyan/30 dark:hover:border-plan-cyan/30">
          <div className="w-10 h-10 rounded-xl bg-plan-cyan/5 dark:bg-plan-cyan/10 text-plan-cyan flex items-center justify-center shrink-0">
            <Users className="w-5 h-5" />
          </div>
          <div className="ml-4 flex-1 min-w-0">
            <span className="text-[9px] font-bold text-slate-400 dark:text-slate-500 uppercase tracking-wider block">Retention Base</span>
            <span className="text-xl font-black text-slate-800 dark:text-white block mt-0.5">
              {analytics.returningPlayers}
            </span>
            <span className="text-[8px] text-slate-555 dark:text-slate-500 font-bold block mt-0.5">Active returning players</span>
          </div>
        </div>

        {/* Day 1 Retention */}
        <div className="flex items-center p-5 rounded-2xl bg-white/60 dark:bg-plan-card/60 border border-slate-200/50 dark:border-slate-800/40 hover:bg-white dark:hover:bg-plan-card/85 transition-all duration-300 hover:shadow-sm hover:border-emerald-500/30 dark:hover:border-emerald-500/30">
          <div className="w-10 h-10 rounded-xl bg-emerald-500/5 dark:bg-emerald-500/10 text-emerald-500 flex items-center justify-center shrink-0">
            <Award className="w-5 h-5" />
          </div>
          <div className="ml-4 flex-1 min-w-0">
            <span className="text-[9px] font-bold text-slate-400 dark:text-slate-500 uppercase tracking-wider block">Day 1 Retention</span>
            <span className="text-xl font-black text-emerald-500 block mt-0.5">
              {(analytics.retentionRateD1 * 100).toFixed(1)}%
            </span>
            <span className="text-[8px] text-slate-555 dark:text-slate-500 font-bold block mt-0.5">D1 cohort stickiness</span>
          </div>
        </div>

        {/* Day 7 Retention */}
        <div className="flex items-center p-5 rounded-2xl bg-white/60 dark:bg-plan-card/60 border border-slate-200/50 dark:border-slate-800/40 hover:bg-white dark:hover:bg-plan-card/85 transition-all duration-300 hover:shadow-sm hover:border-indigo-500/30 dark:hover:border-indigo-500/30">
          <div className="w-10 h-10 rounded-xl bg-indigo-500/5 dark:bg-indigo-500/10 text-indigo-500 flex items-center justify-center shrink-0">
            <Award className="w-5 h-5" />
          </div>
          <div className="ml-4 flex-1 min-w-0">
            <span className="text-[9px] font-bold text-slate-400 dark:text-slate-500 uppercase tracking-wider block">Day 7 Retention</span>
            <span className="text-xl font-black text-indigo-500 block mt-0.5">
              {(analytics.retentionRateW1 * 100).toFixed(1)}%
            </span>
            <span className="text-[8px] text-slate-555 dark:text-slate-500 font-bold block mt-0.5">D7 weekly recurring ratio</span>
          </div>
        </div>

        {/* Avg Playtime */}
        <div className="flex items-center p-5 rounded-2xl bg-white/60 dark:bg-plan-card/60 border border-slate-200/50 dark:border-slate-800/40 hover:bg-white dark:hover:bg-plan-card/85 transition-all duration-300 hover:shadow-sm hover:border-plan-green/30 dark:hover:border-plan-green/30">
          <div className="w-10 h-10 rounded-xl bg-plan-green/5 dark:bg-plan-green/10 text-plan-green flex items-center justify-center shrink-0">
            <Clock className="w-5 h-5" />
          </div>
          <div className="ml-4 flex-1 min-w-0">
            <span className="text-[9px] font-bold text-slate-400 dark:text-slate-500 uppercase tracking-wider block">Playtime Density</span>
            <span className="text-xl font-black text-slate-800 dark:text-white block mt-0.5">
              {formatPlaytime(analytics.averagePlaytimeMs)}
            </span>
            <span className="text-[8px] text-slate-555 dark:text-slate-500 font-bold block mt-0.5">Mean session duration</span>
          </div>
        </div>
      </div>

      {/* Forecaster Banner */}
      {forecast && (
        <div className="p-5 bg-gradient-to-r from-slate-900 via-slate-950 to-slate-900 border border-slate-800 rounded-xl shadow-sm relative overflow-hidden">
          <div className="absolute top-0 right-0 w-[40%] h-full bg-plan-cyan/5 blur-[80px] pointer-events-none" />
          <div className="flex flex-col md:flex-row md:items-center justify-between gap-5 relative z-10">
            <div className="space-y-1.5">
              <div className="flex items-center gap-2">
                <span className="bg-plan-cyan/15 text-plan-cyan px-2.5 py-0.5 rounded text-[9px] font-black uppercase tracking-widest border border-plan-cyan/20">
                  Weekly Forecasting Engine
                </span>
                <span className="text-slate-500 text-[10px] font-bold">
                  Regression analysis on 14d session profiles
                </span>
              </div>
              <h3 className="text-base font-black text-white">
                Next Server Load Peak: <span className="text-plan-cyan">{new Date(forecast.nextPeakTimeMs).toLocaleString(undefined, { weekday: 'long', hour: '2-digit', minute: '2-digit' })}</span>
              </h3>
              <p className="text-xs text-slate-400 leading-normal max-w-2xl font-medium">
                Our regression algorithm forecasts peak concurrent player load at <span className="font-extrabold text-white">{forecast.predictedPlayerCount} concurrent players</span> with a confidence interval of <span className="font-extrabold text-white">{(forecast.confidenceInterval * 100).toFixed(0)}%</span>.
              </p>
            </div>

            <div className="shrink-0 flex items-center gap-3">
              <div className="p-3 rounded-lg bg-slate-800/40 border border-slate-700/50 text-white flex flex-col items-center min-w-[90px] shadow-sm">
                <TrendingUp className="w-5 h-5 text-plan-cyan mb-1" />
                <span className="text-sm font-black text-plan-cyan">+{(forecast.growthTrend * 100).toFixed(1)}%</span>
                <span className="text-[8px] font-black uppercase tracking-wider text-slate-400 mt-0.5">WoW Growth</span>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Peak Hour Punchcard Matrix */}
      <div className="p-6 bg-white/60 dark:bg-plan-card/60 border border-slate-200/50 dark:border-slate-800/40 shadow-sm rounded-2xl relative overflow-hidden">
        <div className="flex justify-between items-center mb-5 flex-wrap gap-3">
          <div>
            <h3 className="text-xs font-black text-slate-800 dark:text-white flex items-center gap-2 uppercase tracking-wider">
              <Calendar className="w-4 h-4 text-plan-blue" /> Weekly Server Load punchcard
            </h3>
            <p className="text-[10px] text-slate-400 dark:text-slate-500 mt-1 font-semibold leading-relaxed">
              Hourly aggregate session density weights. Hover cells to reveal details.
            </p>
          </div>

          {/* Punchcard Legend */}
          <div className="flex items-center gap-2 text-[9px] text-slate-450 dark:text-slate-500 font-bold select-none uppercase tracking-wider">
            <span>Low Load</span>
            <div className="w-2 h-2 rounded-full bg-slate-100/50 dark:bg-slate-900/40 border border-slate-200/50 dark:border-slate-800/20" />
            <div className="w-2 h-2 rounded-full bg-indigo-500/10 dark:bg-indigo-500/5" />
            <div className="w-2 h-2 rounded-full bg-indigo-500/25 dark:bg-indigo-500/15" />
            <div className="w-2 h-2 rounded-full bg-indigo-500/50 dark:bg-indigo-500/40" />
            <div className="w-2 h-2 rounded-full bg-indigo-500 dark:bg-indigo-600" />
            <span>Peak Load</span>
          </div>
        </div>

        {/* Punchcard Render Area */}
        <div className="overflow-x-auto">
          <div className="min-w-[700px] py-2">
            {/* Hours Header Row */}
            <div className="grid grid-cols-[50px_repeat(24,_1fr)] gap-1.5 mb-2">
              <div />
              {Array.from({ length: 24 }).map((_, h) => (
                <div key={h} className="text-center font-black text-[9px] text-slate-450 dark:text-slate-500 uppercase tracking-tighter">
                  {String(h).padStart(2, '0')}
                </div>
              ))}
            </div>

            {/* Days Rows */}
            <div className="space-y-1.5">
              {analytics.punchcardMatrix.map((row, dayIdx) => (
                <div key={dayIdx} className="grid grid-cols-[50px_repeat(24,_1fr)] gap-1.5 items-center">
                  <div className="font-bold text-xs text-slate-650 dark:text-slate-350 text-left uppercase pl-1">
                    {daysOfWeek[dayIdx]}
                  </div>
                  {row.map((val, hourIdx) => {
                    // Compute normalized size
                    const normalizedSize = val === 0 ? 0.35 : 0.45 + (val / maxWeight) * 0.55;

                    return (
                      <div 
                        key={hourIdx} 
                        className="aspect-square flex items-center justify-center relative cursor-pointer"
                        onMouseEnter={() => setHoveredCell({ day: dayIdx, hour: hourIdx, val })}
                        onMouseLeave={() => setHoveredCell(null)}
                      >
                        <div 
                          className={`rounded-full transition-all duration-300 border border-black/5 dark:border-white/5 hover:scale-125 hover:shadow-md ${getPunchcardColor(val, maxWeight)}`}
                          style={{
                            width: `${normalizedSize * 100}%`,
                            height: `${normalizedSize * 100}%`
                          }}
                        />
                      </div>
                    );
                  })}
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Hover Details Banner */}
        <div className="mt-4 pt-3.5 border-t border-slate-200 dark:border-slate-800/80 min-h-[38px] flex items-center justify-center text-xs">
          {hoveredCell ? (
            <div className="flex items-center gap-1.5 text-slate-800 dark:text-slate-200 font-semibold">
              <span className="font-extrabold text-plan-cyan uppercase">{daysOfWeek[hoveredCell.day]} at {String(hoveredCell.hour).padStart(2, '0')}:00</span>
              <span>— Aggregate Active Player Load Index:</span>
              <span className="px-2 py-0.5 bg-plan-cyan/15 text-plan-cyan font-black rounded font-mono border border-plan-cyan/20">
                {hoveredCell.val} sessions
              </span>
            </div>
          ) : (
            <span className="text-slate-400 dark:text-slate-500 italic font-medium">
              Hover cells to analyze micro-session loading patterns across the week.
            </span>
          )}
        </div>
      </div>

      {/* Geographic Distribution */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 p-5 bg-white/60 dark:bg-plan-card/60 border border-slate-200/50 dark:border-slate-800/40 shadow-sm rounded-2xl">
          <h3 className="text-xs font-black text-slate-800 dark:text-white flex items-center gap-2 mb-4 uppercase tracking-wider">
            <Globe className="w-4 h-4 text-plan-blue" /> Geographic Player Distribution
          </h3>
          
          <div className="space-y-4">
            {analytics.geographicDistribution.map((country, idx) => {
              const maxCount = analytics.geographicDistribution[0]?.count || 100;
              const ratio = country.count / maxCount;

              return (
                <div key={idx} className="space-y-1.5">
                  <div className="flex justify-between items-center text-xs">
                    <div className="flex items-center gap-2 font-semibold text-slate-650 dark:text-slate-350">
                      <span className="font-mono bg-slate-100/80 dark:bg-slate-900 border border-slate-200/50 dark:border-slate-800/50 px-1.5 py-0.5 rounded text-[8px] text-slate-500 dark:text-slate-450 uppercase font-black">
                        {country.countryCode}
                      </span>
                      {country.countryName}
                    </div>
                    <span className="font-black text-slate-800 dark:text-white">
                      {country.count} <span className="text-[9px] font-normal text-slate-400 dark:text-slate-500">sessions</span>
                    </span>
                  </div>
                  
                  {/* Progress Bar Container */}
                  <div className="h-1 w-full bg-slate-100 dark:bg-slate-800/40 rounded-full overflow-hidden">
                    <div 
                      className="h-full bg-gradient-to-r from-plan-blue to-plan-cyan rounded-full transition-all duration-500"
                      style={{ width: `${ratio * 100}%` }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Summary sidebar info */}
        <div className="p-5 bg-white/60 dark:bg-plan-card/60 border border-slate-200/50 dark:border-slate-800/40 shadow-sm rounded-2xl flex flex-col justify-between">
          <div className="space-y-4">
            <h3 className="text-xs font-black text-slate-800 dark:text-white flex items-center gap-2 uppercase tracking-wider">
              <MapPin className="w-4 h-4 text-plan-blue" /> Regional Demographics
            </h3>
            
            <div className="p-3.5 rounded-xl border border-slate-200/40 dark:border-slate-800/30 bg-slate-50/30 dark:bg-slate-900/10 text-xs leading-relaxed text-slate-500 dark:text-slate-400 font-medium">
              <p className="font-black text-slate-800 dark:text-slate-200 flex items-center gap-1.5 mb-1.5">
                <Globe className="w-3.5 h-3.5 text-emerald-500 animate-pulse" /> Global Reach Analysis
              </p>
              Your server tracks players from <span className="font-extrabold text-plan-blue">{analytics.geographicDistribution.length} sovereign regions</span>. Principal server load resides primarily in Western European and North American gateway networks.
            </div>

            <div className="space-y-3 pt-1 text-xs font-bold text-slate-500 dark:text-slate-400">
              <div className="flex justify-between items-center py-1.5 border-b border-slate-200/60 dark:border-slate-800/30">
                <span className="font-semibold">Primary Country</span>
                <span className="font-black text-slate-800 dark:text-white">{primaryCountry}</span>
              </div>
              <div className="flex justify-between items-center py-1.5 border-b border-slate-200/60 dark:border-slate-800/30">
                <span className="font-semibold">Secondary Country</span>
                <span className="font-black text-slate-800 dark:text-white">{secondaryCountry}</span>
              </div>
              <div className="flex justify-between items-center py-1.5">
                <span className="font-semibold">Mean Gateway Latency</span>
                <span className="font-black text-slate-800 dark:text-white">{averageLatency}</span>
              </div>
            </div>
          </div>

          <div className="pt-4 border-t border-slate-200/60 dark:border-slate-800/35">
            <button className="w-full flex items-center justify-center gap-1.5 py-2.5 rounded-xl bg-white hover:bg-slate-50 dark:bg-slate-900 dark:hover:bg-slate-800 text-xs font-bold text-slate-700 dark:text-slate-350 transition-all border border-slate-200/50 dark:border-slate-800/50 shadow-sm active:scale-[0.98]">
              Export Demographics <ArrowUpRight className="w-3.5 h-3.5 text-slate-400" />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
