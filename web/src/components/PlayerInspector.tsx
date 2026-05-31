import React, { useState, useEffect } from 'react';
import { 
  PieChart, 
  Pie, 
  Cell, 
  Tooltip, 
  ResponsiveContainer 
} from 'recharts';
import { 
  Search, 
  User, 
  Clock, 
  Activity, 
  Globe, 
  Calendar, 
  Copy, 
  Check, 
  ChevronRight, 
  ExternalLink, 
  Play,
  Package,
  RefreshCw,
  AlertTriangle
} from 'lucide-react';
import { searchPlayers, getPlayerProfile, getPlayerInventory } from '../utils/api';
import type { PlayerProfile, PlayerSessionRecord, PlayerInventorySnapshot, PlayerInventorySlot } from '../types';

const buildHeadFallbacks = (uuid: string, username: string, size: number) => [
  `https://visage.surgeplay.com/face/${size}/${uuid}`,
  `https://crafatar.com/avatars/${uuid}?size=${size}&overlay`,
  `https://minotar.net/helm/${encodeURIComponent(username)}/${size}`,
  `https://api.dicebear.com/7.x/bottts/svg?seed=${encodeURIComponent(username)}`
];

const buildBodyFallbacks = (uuid: string, username: string, size: number) => [
  `https://visage.surgeplay.com/full/${size}/${uuid}`,
  `https://crafatar.com/renders/body/${uuid}?scale=4&overlay=true`,
  `https://minotar.net/body/${encodeURIComponent(username)}/${size}`,
  `https://api.dicebear.com/7.x/bottts/svg?seed=${encodeURIComponent(username)}`
];

const buildItemIconFallbacks = (material: string) => {
  const materialSlug = material.toLowerCase();
  return [
    `https://mc-heads.net/item/${materialSlug}`,
    `https://minecraftitemids.com/item/32/${materialSlug}.png`
  ];
};

const handleImgFallback = (fallbacks: string[]) => (event: React.SyntheticEvent<HTMLImageElement>) => {
  const img = event.currentTarget;
  const currentIndex = Number(img.dataset.fallbackIndex || '0');
  const nextIndex = currentIndex + 1;
  if (nextIndex >= fallbacks.length) return;
  img.dataset.fallbackIndex = String(nextIndex);
  img.src = fallbacks[nextIndex];
};

const formatMaterialLabel = (material: string) => material.toLowerCase().replace(/_/g, ' ');

const buildItemTitle = (slot: PlayerInventorySlot | null) => {
  if (!slot) return 'Empty';
  const parts = [slot.displayName || formatMaterialLabel(slot.material), `x${slot.amount}`];
  if (slot.lore && slot.lore.length > 0) {
    parts.push(slot.lore.join(' | '));
  }
  return parts.join(' - ');
};

export const PlayerInspector: React.FC = () => {
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<{ uuid: string; username: string }[]>([]);
  const [profile, setProfile] = useState<PlayerProfile | null>(null);
  const [loading, setLoading] = useState(false);
  const [copied, setCopied] = useState(false);
  const [inventory, setInventory] = useState<PlayerInventorySnapshot | null>(null);
  const [inventoryLoading, setInventoryLoading] = useState(false);
  const [inventoryError, setInventoryError] = useState<string | null>(null);
  const [inventoryVisible, setInventoryVisible] = useState(false);
  const [enderVisible, setEnderVisible] = useState(false);

  // Trigger search on query change
  useEffect(() => {
    const delayDebounce = setTimeout(async () => {
      if (searchQuery.trim().length >= 2) {
        try {
          const results = await searchPlayers(searchQuery);
          setSearchResults(results);
        } catch (err) {
          console.error(err);
        }
      } else {
        setSearchResults([]);
      }
    }, 200);

    return () => clearTimeout(delayDebounce);
  }, [searchQuery]);

  // Load a player profile
  const handleSelectPlayer = async (uuid: string) => {
    setLoading(true);
    setSearchQuery('');
    setSearchResults([]);
    try {
      const data = await getPlayerProfile(uuid);
      setProfile(data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleCopyUuid = (uuid: string) => {
    navigator.clipboard.writeText(uuid);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const loadInventory = async (uuid: string) => {
    setInventoryLoading(true);
    setInventoryError(null);
    try {
      const data = await getPlayerInventory(uuid);
      setInventory(data);
    } catch (err) {
      console.error(err);
      setInventoryError('Failed to load inventory snapshot.');
    } finally {
      setInventoryLoading(false);
    }
  };

  const formatDuration = (ms: number) => {
    const mins = Math.floor(ms / 60000);
    const hrs = Math.floor(mins / 60);
    if (hrs > 0) return `${hrs}h ${mins % 60}m`;
    return `${mins}m`;
  };

  const getPingColor = (ping: number) => {
    if (ping <= 50) return 'text-emerald-500 bg-emerald-50 dark:bg-emerald-950/20 border-emerald-200';
    if (ping <= 120) return 'text-amber-500 bg-amber-50 dark:bg-amber-950/20 border-amber-200';
    return 'text-rose-500 bg-rose-50 dark:bg-rose-950/20 border-rose-200';
  };

  // World playtime chart preparations
  const prepareWorldData = (sessions: PlayerSessionRecord[]) => {
    const totals: Record<string, number> = {};
    sessions.forEach(s => {
      Object.entries(s.worldPlaytimes || {}).forEach(([world, ms]) => {
        totals[world] = (totals[world] || 0) + ms;
      });
    });

    const colors: Record<string, string> = {
      'lobby': '#0d9488',
      'world': '#2563eb',
      'world_nether': '#ea580c',
      'world_the_end': '#8b5cf6',
      'default': '#64748b'
    };

    return Object.entries(totals).map(([world, ms]) => ({
      name: world === 'world' ? 'Overworld' : world === 'world_nether' ? 'Nether' : world === 'world_the_end' ? 'The End' : world,
      value: Number((ms / (3600 * 1000)).toFixed(2)), // in hours
      rawMs: ms,
      color: colors[world] || colors.default
    })).filter(item => item.value > 0);
  };

  const worldChartData = profile ? prepareWorldData(profile.sessions) : [];

  const daysOfWeek = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

  // Personal punchcard maximum weight for styling
  const maxPersonalWeight = profile?.activityPunchcard
    ? Math.max(...profile.activityPunchcard.map(row => Math.max(...row)))
    : 100;

  const getPersonalPunchcardColor = (val: number) => {
    if (val === 0) return 'bg-slate-100/50 dark:bg-slate-900/40';
    const ratio = val / (maxPersonalWeight || 1);
    if (ratio < 0.3) return 'bg-indigo-500/10 dark:bg-indigo-500/5 text-indigo-400';
    if (ratio < 0.6) return 'bg-indigo-500/30 dark:bg-indigo-500/20 text-indigo-200';
    return 'bg-indigo-500 dark:bg-indigo-650 text-white';
  };

  const renderInventorySlot = (slot: PlayerInventorySlot | null, key: string) => {
    if (!slot) {
      return (
        <div
          key={key}
          className="h-10 w-10 rounded-lg border border-slate-200/60 dark:border-slate-800/50 bg-gradient-to-br from-slate-50/80 via-white/40 to-slate-100/60 dark:from-slate-900/40 dark:via-slate-900/20 dark:to-slate-950/30"
          title="Empty"
        />
      );
    }

    const iconFallbacks = buildItemIconFallbacks(slot.material);

    return (
      <div
        key={key}
        className="group relative h-10 w-10 rounded-lg border border-slate-200/70 dark:border-slate-800/50 bg-white/70 dark:bg-slate-900/40 flex items-center justify-center shadow-[inset_0_0_0_1px_rgba(255,255,255,0.35)] dark:shadow-[inset_0_0_0_1px_rgba(255,255,255,0.06)] transition-all hover:-translate-y-0.5 hover:shadow-md hover:border-plan-blue/40"
        title={buildItemTitle(slot)}
      >
        <img
          src={iconFallbacks[0]}
          data-fallback-index="0"
          alt={slot.displayName || slot.material}
          className="h-7 w-7 object-contain drop-shadow-sm"
          onError={handleImgFallback(iconFallbacks)}
        />
        {slot.amount > 1 && (
          <span className="absolute bottom-1 right-1 text-[9px] font-black text-slate-900 dark:text-white bg-white/85 dark:bg-slate-900/85 rounded px-1 ring-1 ring-black/5 dark:ring-white/10">
            {slot.amount}
          </span>
        )}
      </div>
    );
  };

  const countFilledSlots = (slots: Array<PlayerInventorySlot | null> | null | undefined) => {
    if (!slots) return 0;
    return slots.reduce((sum, slot) => sum + (slot ? 1 : 0), 0);
  };

  const [quickSelects, setQuickSelects] = useState<{ username: string; uuid: string }[]>([]);

  // Load recent players as quick select suggestions on mount
  useEffect(() => {
    const loadQuickSelects = async () => {
      try {
        const results = await searchPlayers('');
        if (results) {
          setQuickSelects(results.slice(0, 4));
        }
      } catch (err) {
        console.error('Failed to load recent players:', err);
      }
    };
    loadQuickSelects();
  }, []);

  useEffect(() => {
    if (!profile) {
      setInventory(null);
      setInventoryVisible(false);
      setEnderVisible(false);
      return;
    }
    setInventory(null);
    setInventoryVisible(false);
    setEnderVisible(false);
  }, [profile?.uuid]);

  const ensureInventoryLoaded = async () => {
    if (!profile) return;
    if (!inventory || inventory.uuid !== profile.uuid) {
      await loadInventory(profile.uuid);
    }
  };

  const handleToggleInventory = async () => {
    if (!inventoryVisible) {
      await ensureInventoryLoaded();
    }
    setInventoryVisible((prev) => !prev);
  };

  const handleToggleEnder = async () => {
    if (!enderVisible) {
      await ensureInventoryLoaded();
    }
    setEnderVisible((prev) => !prev);
  };

  const profileHeadFallbacks = profile ? buildHeadFallbacks(profile.uuid, profile.username, 64) : [];
  const profileBodyFallbacks = profile ? buildBodyFallbacks(profile.uuid, profile.username, 256) : [];
  const filledInventorySlots = inventory ? countFilledSlots(inventory.inventory) : 0;
  const filledArmorSlots = inventory ? countFilledSlots(inventory.armor) + (inventory.offhand ? 1 : 0) : 0;
  const filledEnderSlots = inventory ? countFilledSlots(inventory.enderChest) : 0;
  const inventorySlotTotal = 36;
  const armorSlotTotal = 5;
  const enderSlotTotal = 27;

  return (
    <div className="space-y-6">
      {/* Search Input Bar */}
      <div className="relative max-w-2xl mx-auto z-10">
        <div className="relative">
          <span className="absolute inset-y-0 left-0 pl-3.5 flex items-center text-slate-400 dark:text-slate-500">
            <Search className="w-4 h-4" />
          </span>
          <input
            type="text"
            placeholder="Search players by exact username or full UUID..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-slate-200/50 dark:border-slate-800/40 bg-white/60 dark:bg-slate-900/40 text-slate-800 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-plan-blue/30 focus:border-plan-blue text-xs backdrop-blur-md transition-all shadow-sm placeholder:text-slate-400 dark:placeholder:text-slate-500"
          />
        </div>

        {/* Search Suggestions Dropdown */}
        {searchResults.length > 0 && (
          <div className="absolute top-full left-0 right-0 mt-2 bg-white/90 dark:bg-slate-900/90 border border-slate-200/50 dark:border-slate-800/50 rounded-xl shadow-lg backdrop-blur-md overflow-hidden z-20 max-h-[280px] overflow-y-auto">
            {searchResults.map((player) => {
              const headFallbacks = buildHeadFallbacks(player.uuid, player.username, 32);
              return (
                <button
                  key={player.uuid}
                  onClick={() => handleSelectPlayer(player.uuid)}
                  className="w-full px-4 py-2.5 hover:bg-slate-50 dark:hover:bg-slate-800/50 flex items-center justify-between text-left text-xs border-b border-slate-200/40 dark:border-slate-800/30 last:border-0 transition-colors"
                >
                  <div className="flex items-center gap-2.5">
                    <img
                      src={headFallbacks[0]}
                      data-fallback-index="0"
                      alt="head"
                      className="w-6.5 h-6.5 rounded border border-slate-200/60 dark:border-slate-800/60"
                      onError={handleImgFallback(headFallbacks)}
                    />
                    <div>
                      <div className="font-extrabold text-slate-800 dark:text-slate-200">{player.username}</div>
                      <div className="text-[8px] text-slate-400 dark:text-slate-550 font-mono mt-0.5">{player.uuid}</div>
                    </div>
                  </div>
                  <ChevronRight className="w-3.5 h-3.5 text-slate-400" />
                </button>
              );
            })}
          </div>
        )}
      </div>

      {loading && (
        <div className="flex items-center justify-center min-h-[300px]">
          <div className="flex flex-col items-center gap-2 text-plan-blue animate-pulse">
            <Activity className="w-6 h-6 animate-spin text-plan-blue" />
            <span className="text-[10px] font-bold tracking-widest uppercase text-slate-500">Syncing player session files...</span>
          </div>
        </div>
      )}

      {/* No Player Selected State */}
      {!profile && !loading && (
        <div className="max-w-xl mx-auto text-center p-8 bg-white/60 dark:bg-plan-card/60 border border-slate-200/50 dark:border-slate-800/40 rounded-2xl shadow-sm backdrop-blur-md mt-6 flex flex-col items-center">
          <div className="w-11 h-11 rounded-xl bg-slate-100/50 dark:bg-slate-900/40 border border-slate-200/50 dark:border-slate-800/40 flex items-center justify-center text-slate-400 dark:text-slate-500 mb-4">
            <User className="w-5.5 h-5.5" />
          </div>
          <h3 className="text-xs font-black text-slate-800 dark:text-white uppercase tracking-wider">Select a Player to Inspect</h3>
          <p className="text-[9.5px] text-slate-400 dark:text-slate-500 mt-2 max-w-sm leading-relaxed font-semibold">
            Enter a player's exact username or UUID above, or click one of the quick selection shortcuts to inspect their playtime dimensions, typologies, and IP audit trails.
          </p>

          <div className="mt-6 w-full space-y-2">
            <span className="text-[9px] font-black uppercase tracking-[0.18em] text-slate-400 dark:text-slate-550 block text-left mb-2 pl-1">
              Active Inspector Shortcuts:
            </span>
            <div className="grid grid-cols-2 gap-3">
              {quickSelects.map((qp) => {
                const headFallbacks = buildHeadFallbacks(qp.uuid, qp.username, 32);
                return (
                  <button
                    key={qp.uuid}
                    onClick={() => handleSelectPlayer(qp.uuid)}
                    className="flex items-center gap-2.5 p-3 rounded-xl border border-slate-200/40 dark:border-slate-800/30 bg-slate-50/20 dark:bg-slate-900/20 hover:bg-slate-100/50 dark:hover:bg-slate-800/40 active:scale-[0.98] transition-all text-left shadow-sm group"
                  >
                    <img
                      src={headFallbacks[0]}
                      data-fallback-index="0"
                      alt="head"
                      className="w-6.5 h-6.5 rounded border border-slate-200/50 dark:border-slate-700/50 group-hover:scale-105 transition-transform"
                      onError={handleImgFallback(headFallbacks)}
                    />
                    <div className="min-w-0">
                      <span className="text-xs font-bold text-slate-800 dark:text-slate-200 block group-hover:text-plan-blue transition-colors truncate">
                        {qp.username}
                      </span>
                      <span className="text-[8px] font-bold text-slate-400 dark:text-slate-500 font-mono">
                        {qp.uuid.substring(0, 8)}...
                      </span>
                    </div>
                  </button>
                );
              })}
            </div>
          </div>
        </div>
      )}

      {/* Inspected Player Profile Section */}
      {profile && !loading && (
        <div className="space-y-6">
          {/* Profile Overview Grid Layout */}
          <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
            {/* 3D Skin Render Card */}
            <div className="lg:col-span-1 p-5 bg-white/60 dark:bg-plan-card/60 border border-slate-200/50 dark:border-slate-800/40 shadow-sm rounded-2xl flex flex-col items-center justify-center relative overflow-hidden group min-h-[300px]">
              {/* Background Glow Effect */}
              <div className="absolute inset-0 bg-gradient-to-b from-plan-blue/5 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-500 pointer-events-none" />
              
              <span className="text-[9px] font-black uppercase tracking-[0.18em] text-slate-400 dark:text-slate-500 mb-4 block select-none">
                3D Character Render
              </span>
              
              <div className="relative w-36 h-48 flex items-center justify-center group-hover:scale-[1.03] transition-transform duration-500 ease-out select-none">
                <img
                  src={profileBodyFallbacks[0]}
                  data-fallback-index="0"
                  alt="3D Character Render"
                  className="max-w-full max-h-full object-contain filter drop-shadow-[0_4px_8px_rgba(0,0,0,0.08)] dark:drop-shadow-[0_10px_20px_rgba(0,0,0,0.4)]"
                  onError={handleImgFallback(profileBodyFallbacks)}
                />
              </div>
            </div>

            {/* Profile Overview Card (Takes remaining 3 columns) */}
            <div className="lg:col-span-3 p-5 bg-white/60 dark:bg-plan-card/60 border border-slate-200/50 dark:border-slate-800/40 shadow-sm rounded-2xl flex flex-col justify-between relative overflow-hidden">
              <div className="flex flex-col xl:flex-row xl:items-center justify-between gap-5 pb-5 border-b border-slate-200/50 dark:border-slate-800/40">
                <div className="flex items-center gap-3.5">
                  <img
                    src={profileHeadFallbacks[0]}
                    data-fallback-index="0"
                    alt="skin avatar"
                    className="w-12 h-12 rounded-xl shadow-sm border border-slate-200/50 dark:border-slate-700/50 hover:scale-105 transition-transform"
                    onError={handleImgFallback(profileHeadFallbacks)}
                  />
                  <div className="space-y-1">
                    <div className="flex items-center gap-2 flex-wrap">
                      <h3 className="text-base font-black text-slate-800 dark:text-white tracking-tight">{profile.username}</h3>
                      
                      {/* Pulsing Minecraft Active Status */}
                      {profile.sessions[0] && !profile.sessions[0].logoutMs ? (
                        <span className="flex items-center gap-1.5 px-2.5 py-0.5 rounded-full border border-emerald-500/10 bg-emerald-500/5 text-[8px] text-emerald-500 font-black uppercase tracking-wider animate-pulse">
                          <div className="w-1 h-1 rounded-full bg-emerald-500" />
                          Online Now
                        </span>
                      ) : (
                        <span className="flex items-center gap-1.5 px-2.5 py-0.5 rounded-full border border-slate-200/60 dark:border-slate-800 bg-slate-100/50 dark:bg-slate-900/40 text-[8px] text-slate-400 dark:text-slate-500 font-black uppercase tracking-wider">
                          Offline
                        </span>
                      )}

                      <span className="flex items-center gap-1 px-2.5 py-0.5 rounded-full border border-slate-200/60 dark:border-slate-800 bg-slate-100/40 dark:bg-slate-900/30 text-[8px] text-slate-400 dark:text-slate-500 font-black uppercase tracking-wider">
                        <Globe className="w-3 h-3 text-slate-400" /> {profile.countryCode} Registered
                      </span>
                    </div>
                    <div className="flex items-center gap-1.5 text-[8.5px] text-slate-400 dark:text-slate-500 font-mono font-bold">
                      <span>{profile.uuid}</span>
                      <button
                        onClick={() => handleCopyUuid(profile.uuid)}
                        className="p-1 hover:bg-black/5 dark:hover:bg-white/5 rounded text-slate-400 hover:text-plan-blue transition-colors"
                        title="Copy UUID"
                      >
                        {copied ? <Check className="w-3.5 h-3.5 text-emerald-500" /> : <Copy className="w-3.5 h-3.5" />}
                      </button>
                    </div>
                  </div>
                </div>

                {/* Stats badges inside profile card */}
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 max-w-xl text-xs font-bold text-slate-500 dark:text-slate-400">
                  <div className="text-left md:text-right border-l md:border-l-0 md:border-r border-slate-200/50 dark:border-slate-800/40 pl-3 md:pl-0 md:pr-4">
                    <span className="text-[9px] font-black uppercase tracking-wider text-slate-400 dark:text-slate-500 block">Total Playtime</span>
                    <span className="text-xs font-black text-slate-800 dark:text-white mt-0.5 block">{formatDuration(profile.totalPlaytimeMs)}</span>
                  </div>
                  <div className="text-left md:text-right border-l md:border-l-0 md:border-r border-slate-200/50 dark:border-slate-800/40 pl-3 md:pl-0 md:pr-4">
                    <span className="text-[9px] font-black uppercase tracking-wider text-slate-400 dark:text-slate-500 block">Average Latency</span>
                    <span className={`text-[9px] font-black inline-block px-2 py-0.5 rounded border mt-1 ${getPingColor(profile.averagePing)}`}>
                      {profile.averagePing} ms
                    </span>
                  </div>
                  <div className="text-left md:text-right border-l md:border-l-0 md:border-r border-slate-200/50 dark:border-slate-800/40 pl-3 md:pl-0 md:pr-4">
                    <span className="text-[9px] font-black uppercase tracking-wider text-slate-400 dark:text-slate-500 block">First Joined</span>
                    <span className="text-xs font-bold text-slate-800 dark:text-slate-200 mt-0.5 block">{new Date(profile.firstLoginMs).toLocaleDateString()}</span>
                  </div>
                  <div className="text-left md:text-right border-l border-slate-200/50 dark:border-slate-800/40 pl-3">
                    <span className="text-[9px] font-black uppercase tracking-wider text-slate-400 dark:text-slate-550 block">Last Active</span>
                    <span className="text-xs font-bold text-slate-800 dark:text-slate-200 mt-0.5 block">{new Date(profile.lastLoginMs).toLocaleDateString()}</span>
                  </div>
                </div>
              </div>

            {/* Dimensional play distribution & Specific Login Punchcard */}
            <div className="grid grid-cols-1 lg:grid-cols-5 gap-6 pt-5">
              {/* Playtime dimension (donut pie) */}
              <div className="lg:col-span-2 flex flex-col justify-between">
                <div>
                  <h4 className="text-[10px] font-black uppercase tracking-widest text-slate-450 dark:text-slate-500 mb-4 flex items-center gap-1.5">
                    <Clock className="w-4 h-4 text-slate-400" /> Playtime Dimension Load
                  </h4>

                  {worldChartData.length === 0 ? (
                    <div className="h-[160px] flex items-center justify-center text-xs text-slate-400 italic">
                      No dimension records available.
                    </div>
                  ) : (
                    <div className="flex items-center gap-3">
                      <div className="w-[140px] h-[140px]">
                        <ResponsiveContainer width="100%" height="100%">
                          <PieChart>
                            <Tooltip 
                              formatter={(value) => [`${value} hrs`, 'Playtime']}
                              contentStyle={{ 
                                fontSize: '10px', 
                                borderRadius: '8px', 
                                backgroundColor: '#16181d', 
                                border: '1px solid #334155',
                                color: '#f8fafc'
                              }}
                            />
                            <Pie
                              data={worldChartData}
                              cx="50%"
                              cy="50%"
                              innerRadius={45}
                              outerRadius={60}
                              paddingAngle={3}
                              dataKey="value"
                            >
                              {worldChartData.map((entry, index) => (
                                <Cell key={`cell-${index}`} fill={entry.color} />
                              ))}
                            </Pie>
                          </PieChart>
                        </ResponsiveContainer>
                      </div>

                      {/* Legend */}
                      <div className="flex-1 space-y-2 text-[11px] font-semibold">
                        {worldChartData.map((entry, index) => {
                          const percent = ((entry.rawMs / profile.totalPlaytimeMs) * 100).toFixed(0);
                          return (
                            <div key={index} className="flex items-center justify-between">
                              <div className="flex items-center gap-1.5 text-slate-750 dark:text-slate-350">
                                <div className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: entry.color }} />
                                <span>{entry.name}</span>
                              </div>
                              <span className="font-extrabold text-slate-800 dark:text-white">
                                {percent}% <span className="font-normal text-[10px] text-slate-500">({entry.value}h)</span>
                              </span>
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  )}
                </div>
              </div>

              {/* Personal login punchcard */}
              <div className="lg:col-span-3">
                <h4 className="text-[10px] font-black uppercase tracking-widest text-slate-450 dark:text-slate-500 mb-4 flex items-center gap-1.5">
                  <Calendar className="w-4 h-4 text-slate-400" /> Behavioral Login Punchcard
                </h4>
                
                {/* Specific 7x24 grid representing the player's typical active times */}
                <div className="overflow-x-auto">
                  <div className="min-w-[400px] py-1.5">
                    {/* Hours headers */}
                    <div className="grid grid-cols-[30px_repeat(24,_1fr)] gap-1 mb-1">
                      <div />
                      {Array.from({ length: 24 }).map((_, h) => (
                        <div key={h} className="text-center font-black text-[8px] text-slate-400 dark:text-slate-500 tracking-tighter">
                          {h}
                        </div>
                      ))}
                    </div>

                    <div className="space-y-1">
                      {profile.activityPunchcard.map((row, dIdx) => (
                        <div key={dIdx} className="grid grid-cols-[30px_repeat(24,_1fr)] gap-1 items-center">
                          <span className="text-[10px] font-black text-slate-650 dark:text-slate-350 uppercase">
                            {daysOfWeek[dIdx][0]}
                          </span>
                          {row.map((val, hIdx) => {
                            const size = val === 0 ? 0.35 : 0.5 + (val / (maxPersonalWeight || 1)) * 0.5;

                            return (
                              <div key={hIdx} className="aspect-square flex items-center justify-center animate-pulse-slow" title={`${daysOfWeek[dIdx]} at ${hIdx}:00 — Playtime Weight: ${val}`}>
                                <div 
                                  className={`rounded-full transition-all duration-250 border border-black/5 dark:border-white/5 ${getPersonalPunchcardColor(val)}`}
                                  style={{
                                    width: `${size * 100}%`,
                                    height: `${size * 100}%`
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
              </div>
            </div>
          </div>
        </div>

          {/* Player session log timeline */}
          <div className="bg-white/60 dark:bg-plan-card/60 border border-slate-200/50 dark:border-slate-800/40 shadow-sm rounded-2xl p-5 relative overflow-hidden">
            <h4 className="text-xs font-black text-slate-800 dark:text-white flex items-center gap-2 mb-4 uppercase tracking-wider">
              <Play className="w-4 h-4 text-plan-blue animate-pulse" /> Recent In-Game Session Logs
            </h4>

            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-slate-200/50 dark:border-slate-800 pb-3 text-slate-400 dark:text-slate-500 font-bold uppercase tracking-[0.1em]">
                    <th className="py-2.5 px-3">Session ID</th>
                    <th className="py-2.5 px-3">IP Address</th>
                    <th className="py-2.5 px-3">Login Epoch</th>
                    <th className="py-2.5 px-3">Logout Epoch</th>
                    <th className="py-2.5 px-3">Duration</th>
                    <th className="py-2.5 px-3">Avg Latency</th>
                    <th className="py-2.5 px-3">Type</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100/60 dark:divide-slate-800/40 font-semibold text-slate-650 dark:text-slate-350">
                  {profile.sessions.map((sess) => (
                    <tr key={sess.sessionId} className="hover:bg-slate-50/50 dark:hover:bg-slate-900/10 transition-colors">
                      <td className="py-2.5 px-3 font-mono text-[9px] text-slate-400 dark:text-slate-550">{sess.sessionId.substring(0, 12)}...</td>
                      <td className="py-2.5 px-3 font-mono text-[9.5px] text-slate-500 dark:text-slate-400">{sess.ipAddress}</td>
                      <td className="py-2.5 px-3 text-slate-500 dark:text-slate-450">{new Date(sess.loginMs).toLocaleString()}</td>
                      <td className="py-2.5 px-3 text-slate-500 dark:text-slate-450">
                        {sess.logoutMs ? new Date(sess.logoutMs).toLocaleString() : <span className="text-emerald-500 font-black animate-pulse uppercase text-[8px] tracking-wider bg-emerald-500/5 px-2 py-0.5 rounded-full border border-emerald-500/10">ACTIVE</span>}
                      </td>
                      <td className="py-2.5 px-3 text-slate-855 dark:text-white font-black">{formatDuration(sess.playtimeMs)}</td>
                      <td className="py-2.5 px-3">
                        <span className={`px-2 py-0.5 rounded border text-[8.5px] font-black ${getPingColor(sess.averagePing)}`}>
                          {sess.averagePing} ms
                        </span>
                      </td>
                      <td className="py-2.5 px-3">
                        <span className="inline-flex items-center gap-1 text-[8px] font-black uppercase tracking-wider text-slate-400 dark:text-slate-500 bg-slate-100/50 dark:bg-slate-900/30 border border-slate-200/50 dark:border-slate-800/50 px-2 py-0.5 rounded-md">
                          MC Core <ExternalLink className="w-2.5 h-2.5 text-slate-400" />
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {/* Invsee inventory snapshot */}
          <div className="bg-white/60 dark:bg-plan-card/60 border border-slate-200/50 dark:border-slate-800/40 shadow-sm rounded-2xl p-5 relative overflow-hidden">
            <div className="flex flex-wrap items-start justify-between gap-3 mb-4">
              <div className="space-y-1">
                <h4 className="text-xs font-black text-slate-800 dark:text-white flex items-center gap-2 uppercase tracking-wider">
                  <Package className="w-4 h-4 text-plan-blue" /> Invsee Inventory
                </h4>
                <p className="text-[9px] font-semibold text-slate-400 dark:text-slate-500 uppercase tracking-[0.18em]">
                  Snapshot view for inventory and ender chest state
                </p>
              </div>
              <div className="flex items-center gap-2">
                <button
                  onClick={handleToggleInventory}
                  disabled={inventoryLoading}
                  className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border text-[9px] font-black uppercase tracking-wider transition-colors disabled:opacity-60 ${
                    inventoryVisible
                      ? 'border-plan-blue/50 bg-plan-blue/10 text-plan-blue'
                      : 'border-slate-200/60 dark:border-slate-800/60 bg-white/60 dark:bg-slate-900/40 text-slate-500 dark:text-slate-400 hover:text-plan-blue hover:border-plan-blue/40'
                  }`}
                >
                  {inventoryVisible ? 'Hide Invsee' : 'Show Invsee'}
                </button>
                <button
                  onClick={handleToggleEnder}
                  disabled={inventoryLoading}
                  className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border text-[9px] font-black uppercase tracking-wider transition-colors disabled:opacity-60 ${
                    enderVisible
                      ? 'border-plan-blue/50 bg-plan-blue/10 text-plan-blue'
                      : 'border-slate-200/60 dark:border-slate-800/60 bg-white/60 dark:bg-slate-900/40 text-slate-500 dark:text-slate-400 hover:text-plan-blue hover:border-plan-blue/40'
                  }`}
                >
                  {enderVisible ? 'Hide Ecsee' : 'Show Ecsee'}
                </button>
                <button
                  onClick={() => profile && loadInventory(profile.uuid)}
                  disabled={inventoryLoading}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-slate-200/60 dark:border-slate-800/60 bg-white/60 dark:bg-slate-900/40 text-[9px] font-black uppercase tracking-wider text-slate-500 dark:text-slate-400 hover:text-plan-blue hover:border-plan-blue/40 transition-colors disabled:opacity-60"
                >
                  <RefreshCw className={`w-3 h-3 ${inventoryLoading ? 'animate-spin' : ''}`} /> Refresh
                </button>
              </div>
            </div>

            <div className="flex flex-wrap items-center gap-3 text-[9px] font-black uppercase tracking-wider text-slate-400 dark:text-slate-500 mb-4">
              {inventory ? (
                <>
                  <span className="px-2 py-1 rounded-full border border-slate-200/60 dark:border-slate-800/60 bg-slate-100/60 dark:bg-slate-900/40">
                    Inventory {filledInventorySlots}/{inventorySlotTotal}
                  </span>
                  <span className="px-2 py-1 rounded-full border border-slate-200/60 dark:border-slate-800/60 bg-slate-100/60 dark:bg-slate-900/40">
                    Armor + Offhand {filledArmorSlots}/{armorSlotTotal}
                  </span>
                  <span className="px-2 py-1 rounded-full border border-slate-200/60 dark:border-slate-800/60 bg-slate-100/60 dark:bg-slate-900/40">
                    Ender {filledEnderSlots}/{enderSlotTotal}
                  </span>
                </>
              ) : (
                <span className="px-2 py-1 rounded-full border border-slate-200/60 dark:border-slate-800/60 bg-slate-100/60 dark:bg-slate-900/40">
                  Snapshot not loaded
                </span>
              )}
            </div>

            {inventoryLoading && (
              <div className="flex items-center gap-2 text-[10px] font-bold text-slate-500 dark:text-slate-400">
                <RefreshCw className="w-3.5 h-3.5 animate-spin" /> Pulling latest inventory snapshot...
              </div>
            )}

            {!inventoryLoading && inventoryError && (
              <div className="flex items-center gap-2 text-[10px] font-bold text-rose-500">
                <AlertTriangle className="w-3.5 h-3.5" /> {inventoryError}
              </div>
            )}

            {!inventoryLoading && inventory && inventory.unavailableReason && (
              <div className="flex items-center gap-2 text-[10px] font-bold text-amber-500 mb-4">
                <AlertTriangle className="w-3.5 h-3.5" /> {inventory.unavailableReason}
              </div>
            )}

            {!inventoryLoading && inventory && inventoryVisible && (
              <div className="grid grid-cols-1 xl:grid-cols-[220px_1fr] gap-6">
                <div className="rounded-xl border border-slate-200/60 dark:border-slate-800/50 bg-gradient-to-br from-slate-50/80 via-white/50 to-slate-100/80 dark:from-slate-950/40 dark:via-slate-900/40 dark:to-slate-950/70 p-4">
                  <span className="text-[9px] font-black uppercase tracking-[0.18em] text-slate-400 dark:text-slate-500 block mb-3">
                    Armor + Offhand
                  </span>
                  <div className="grid grid-cols-2 gap-2">
                    {inventory.armor.map((slot, idx) => renderInventorySlot(slot, `armor-${idx}`))}
                    {renderInventorySlot(inventory.offhand, 'offhand')}
                  </div>
                </div>

                <div className="rounded-xl border border-slate-200/60 dark:border-slate-800/50 bg-gradient-to-br from-white/70 via-slate-50/70 to-slate-100/70 dark:from-slate-900/60 dark:via-slate-900/40 dark:to-slate-950/60 p-4 space-y-4">
                  <div>
                    <span className="text-[9px] font-black uppercase tracking-[0.18em] text-slate-400 dark:text-slate-500 block mb-3">
                      Inventory
                    </span>
                    <div className="grid grid-cols-9 gap-1.5">
                      {inventory.inventory.slice(0, 27).map((slot, idx) => renderInventorySlot(slot, `inv-${idx}`))}
                    </div>
                  </div>

                  <div>
                    <span className="text-[9px] font-black uppercase tracking-[0.18em] text-slate-400 dark:text-slate-500 block mb-3">
                      Hotbar
                    </span>
                    <div className="grid grid-cols-9 gap-1.5">
                      {inventory.inventory.slice(27, 36).map((slot, idx) =>
                        renderInventorySlot(slot, `hotbar-${idx}`)
                      )}
                    </div>
                  </div>
                </div>
              </div>
            )}

            {!inventoryLoading && inventory && enderVisible && (
              <div className="mt-6 rounded-xl border border-slate-200/60 dark:border-slate-800/50 bg-gradient-to-br from-slate-50/70 via-white/60 to-slate-100/80 dark:from-slate-950/50 dark:via-slate-900/40 dark:to-slate-950/70 p-4">
                <span className="text-[9px] font-black uppercase tracking-[0.18em] text-slate-400 dark:text-slate-500 block mb-3">
                  Ender Chest
                </span>
                <div className="grid grid-cols-9 gap-1.5">
                  {inventory.enderChest.map((slot, idx) => renderInventorySlot(slot, `ender-${idx}`))}
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};
