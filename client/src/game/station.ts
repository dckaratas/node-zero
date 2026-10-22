import type { Axial } from '../map/hex';
import type { StationCapacity } from '../protocol/messages';

/**
 * NODE-01's request pipeline as the map draws it (GDD: Tomcat threads → CPU → HikariCP → DB, logs → disk).
 * Capacities come from the server (slice 8a, decision AR): they are the player's own hardware, so showing
 * them is no fog-of-war leak, and structural counters change them. What is hidden is the load.
 */
export interface StationNode {
  id: string;
  label: string;
  hex: Axial;
  /** The monitoring module (counter id) that makes this node measurable. */
  reveals: string;
  capacity(station: StationCapacity): string;
  /** 0..1 load from the visible metrics, or undefined when the player cannot measure it. */
  load(metrics: Readonly<Record<string, number>>, station: StationCapacity): number | undefined;
  /** USE method (decision AN): one reading each, or null when no exporter measures it on this node. */
  use: { utilization: Reading | null; saturation: Reading | null; errors: Reading | null };
}

export interface Reading {
  metric: string;
  format(value: number, station: StationCapacity): string;
}

export interface Link {
  from: string;
  to: string;
}

/** NODE-01 as built (StationLayout.node01); used until the first snapshot arrives. */
export const NODE01: StationCapacity = { cpuCores: 4, threads: 200, dbPool: 10, diskMb: 100, dbCpuCores: 4, dbIoDisks: 1 };

const clamp = (v: number): number => Math.min(1, Math.max(0, v));
const percent = (v: number): string => `${Math.round(v * 100)}%`;

function ratio(metric: string, capacity: (s: StationCapacity) => number = () => 1) {
  return (m: Readonly<Record<string, number>>, s: StationCapacity): number | undefined => {
    const v = m[metric];
    return v === undefined ? undefined : clamp(v / capacity(s));
  };
}

const reading = (metric: string, format: (v: number, s: StationCapacity) => string): Reading => ({ metric, format });

export const NODES: readonly StationNode[] = [
  {
    id: 'gateway', label: 'Gateway', hex: { q: 0, r: 0 }, reveals: 'prometheus',
    capacity: () => 'Nginx in front of the app',
    load: () => undefined,
    use: {
      utilization: reading('request_rate', (v) => `${Math.round(v)} rps arriving`),
      saturation: reading('latency_p99', (v) => `${Math.round(v)} ms p99`),
      errors: reading('error_rate', (v) => `${(v * 100).toFixed(1)}% failing`),
    },
  },
  {
    id: 'threads', label: 'Tomcat', hex: { q: 1, r: 0 }, reveals: 'prometheus',
    capacity: (s) => `${s.threads} threads`,
    load: ratio('tomcat_threads_busy', (s) => s.threads),
    use: {
      utilization: reading('tomcat_threads_busy', (v, s) => `${Math.round(v)} of ${s.threads} busy`),
      saturation: null, // the accept queue is not exported: a saturated Tomcat looks merely "busy"
      errors: null,
    },
  },
  {
    id: 'cpu', label: 'CPU', hex: { q: 2, r: -1 }, reveals: 'prometheus',
    capacity: (s) => `${s.cpuCores} cores`,
    load: ratio('cpu_utilization'),
    use: { utilization: reading('cpu_utilization', percent), saturation: null, errors: null },
  },
  {
    id: 'db_pool', label: 'HikariCP', hex: { q: 2, r: 0 }, reveals: 'prometheus',
    capacity: (s) => `${s.dbPool} connections`,
    load: ratio('hikaricp_connections_active', (s) => s.dbPool),
    use: {
      utilization: reading('hikaricp_connections_active', (v, s) => `${v.toFixed(1)} of ${s.dbPool} in use`),
      saturation: reading('hikaricp_connections_pending', (v) => `${v.toFixed(1)} waiting`),
      errors: null,
    },
  },
  {
    id: 'disk', label: 'Disk', hex: { q: 1, r: 1 }, reveals: 'prometheus',
    capacity: (s) => `${s.diskMb} MB`,
    load: ratio('disk_usage_ratio'),
    use: {
      utilization: reading('disk_usage_ratio', percent),
      saturation: reading('disk_fill_rate', (v) => `${v >= 0 ? '+' : ''}${v.toFixed(2)} MB/s`),
      errors: null,
    },
  },
  {
    id: 'db_cpu', label: 'DB CPU', hex: { q: 3, r: -1 }, reveals: 'postgres-exporter',
    capacity: (s) => `${s.dbCpuCores} cores`,
    load: ratio('db_cpu_utilization'),
    use: {
      utilization: reading('db_cpu_utilization', percent),
      saturation: reading('slow_queries', (v) => `${Math.round(v)} ms per query`),
      errors: null,
    },
  },
  {
    id: 'db_io', label: 'DB IO', hex: { q: 3, r: 0 }, reveals: 'postgres-exporter',
    capacity: (s) => `${s.dbIoDisks} volume`,
    load: ratio('db_io_utilization'),
    use: { utilization: reading('db_io_utilization', percent), saturation: null, errors: null },
  },
];

export const LINKS: readonly Link[] = [
  { from: 'gateway', to: 'threads' },
  { from: 'threads', to: 'cpu' },
  { from: 'threads', to: 'db_pool' },
  { from: 'threads', to: 'disk' },
  { from: 'db_pool', to: 'db_cpu' },
  { from: 'db_pool', to: 'db_io' },
];

export function nodeById(id: string): StationNode | undefined {
  return NODES.find((n) => n.id === id);
}

/** Green below 70 %, amber to 90 %, red above: the usual saturation thresholds on a dashboard. */
export function loadColor(load: number | undefined): number {
  if (load === undefined) return 0x3a4258;
  if (load < 0.7) return 0x2f9e6e;
  if (load < 0.9) return 0xd9a334;
  return 0xd9534f;
}
