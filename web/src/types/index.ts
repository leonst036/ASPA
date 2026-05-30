export interface ServerMetricsRecord {
  timestamp: number;
  tps: number;
  mspt: number;
  cpuUsage: number;
  ramUsedMb: number;
  ramMaxMb: number;
  onlinePlayers: number;
  loadedChunks: number;
  entityCounts: Record<string, number>;
  gcCountDelta: number;
  gcTimeDeltaMs: number;
  avgPing: number;
  maxPing: number;
  chunksPerWorld: Record<string, number>;
  entitiesPerWorld: Record<string, number>;
}

export interface CorrelatedFactor {
  factor: string; // "CHUNK_LOAD_VELOCITY", "ENTITY_COUNT", "PLAYER_COUNT", "RAM_SPIKE"
  value: number;
  correlationStrength: number; // 0.0 to 1.0
  description: string;
}

export interface PerformanceAnomaly {
  timestamp: number;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  tps: number;
  mspt: number;
  correlatedFactors: CorrelatedFactor[];
}

export interface CountryDistribution {
  countryCode: string;
  countryName: string;
  count: number;
}

export interface RetentionReport {
  newPlayers: number;
  returningPlayers: number;
  retentionRateD1: number;
  retentionRateW1: number;
  averagePlaytimeMs: number;
  geographicDistribution: CountryDistribution[];
  punchcardMatrix: number[][]; // 7x24 matrix
  averagePing?: number;
}

export interface PlayerSessionRecord {
  sessionId: string;
  uuid: string;
  username: string;
  ipAddress: string;
  countryCode: string;
  loginMs: number;
  logoutMs: number;
  playtimeMs: number;
  averagePing: number;
  worldPlaytimes: Record<string, number>;
}

export interface PlayerProfile {
  uuid: string;
  username: string;
  firstLoginMs: number;
  lastLoginMs: number;
  totalPlaytimeMs: number;
  averagePing: number;
  countryCode: string;
  sessions: PlayerSessionRecord[];
  activityPunchcard: number[][]; // 7x24 matrix
}

export interface ForecastResult {
  nextPeakTimeMs: number;
  predictedPlayerCount: number;
  confidenceInterval: number; // 0.0 to 1.0
  growthTrend: number; // Slope/percentage growth
}

export interface SystemStatus {
  status: string;
  version: string;
  uptimeMs: number;
  databaseDriver: string;
  totalPlayersTracked: number;
  totalSessionsTracked: number;
}

export interface PterodactylResources {
  state: string; // "running", "offline", "starting", "stopping"
  isSuspended: boolean;
  cpuAbsolute: number; // percentage
  ramBytes: number;
  ramMaxBytes: number;
  diskBytes: number;
  diskMaxBytes: number;
  networkRxBytes: number;
  networkTxBytes: number;
}

export interface PterodactylBackup {
  uuid: string;
  name: string;
  ignoredFiles: string[];
  sha255Hash?: string;
  bytes: number;
  isSuccessful: boolean;
  isLocked: boolean;
  createdAt: string;
  completedAt: string | null;
}

export interface PterodactylStatus {
  enabled: boolean;
  configured: boolean;
  resources?: PterodactylResources;
}

export interface PlayerInventorySlot {
  slot: number;
  material: string;
  amount: number;
  displayName?: string;
  lore?: string[];
  enchantments?: Record<string, number>;
  durability?: number;
  customModelData?: number;
}

export interface PlayerInventorySnapshot {
  uuid: string;
  username: string;
  online: boolean;
  fetchedAtMs: number;
  unavailableReason?: string;
  inventory: Array<PlayerInventorySlot | null>; // 36 slots
  armor: Array<PlayerInventorySlot | null>; // 4 slots (helmet, chest, legs, boots)
  offhand: PlayerInventorySlot | null;
  enderChest: Array<PlayerInventorySlot | null>; // 27 slots
}

