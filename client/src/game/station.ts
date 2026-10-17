import type { Axial } from '../map/hex';

/**
 * NODE-01's request pipeline as the map draws it (GDD: Tomcat threads → CPU → HikariCP → DB, logs → disk).
 * Capacities are the player's own configuration (StationLayout.node01 on the server), so showing them
 * is not a fog-of-war leak; what is hidden is the load, which needs monitoring (metrics) to see.
 * Later: send the layout in the welcome message when stations differ.
 */
export interface StationNode {
  id: string;
  label: string;
  hex: Axial;
  /** 0..1 load from the visible metrics, or undefined when the player cannot measure it. */
  load(metrics: Readonly<Record<string, number>>): number | undefined;
}

export interface Link {
  from: string;
  to: string;
}

const MAX_THREADS = 200;
const POOL_SIZE = 10;

function ratio(metric: string, capacity = 1) {
  return (m: Readonly<Record<string, number>>): number | undefined => {
    const v = m[metric];
    return v === undefined ? undefined : Math.min(1, Math.max(0, v / capacity));
  };
}

export const NODES: readonly StationNode[] = [
  { id: 'gateway', label: 'Gateway', hex: { q: 0, r: 0 }, load: () => undefined },
  { id: 'threads', label: 'Tomcat', hex: { q: 1, r: 0 }, load: ratio('tomcat_threads_busy', MAX_THREADS) },
  { id: 'cpu', label: 'CPU', hex: { q: 2, r: -1 }, load: ratio('cpu_utilization') },
  { id: 'pool', label: 'HikariCP', hex: { q: 2, r: 0 }, load: ratio('hikaricp_connections_active', POOL_SIZE) },
  { id: 'disk', label: 'Disk', hex: { q: 1, r: 1 }, load: ratio('disk_usage_ratio') },
  { id: 'db_cpu', label: 'DB CPU', hex: { q: 3, r: -1 }, load: ratio('db_cpu_utilization') },
  { id: 'db_io', label: 'DB IO', hex: { q: 3, r: 0 }, load: ratio('db_io_utilization') },
];

export const LINKS: readonly Link[] = [
  { from: 'gateway', to: 'threads' },
  { from: 'threads', to: 'cpu' },
  { from: 'threads', to: 'pool' },
  { from: 'threads', to: 'disk' },
  { from: 'pool', to: 'db_cpu' },
  { from: 'pool', to: 'db_io' },
];

/** Green below 70 %, amber to 90 %, red above: the usual saturation thresholds on a dashboard. */
export function loadColor(load: number | undefined): number {
  if (load === undefined) return 0x3a4258;
  if (load < 0.7) return 0x2f9e6e;
  if (load < 0.9) return 0xd9a334;
  return 0xd9534f;
}
