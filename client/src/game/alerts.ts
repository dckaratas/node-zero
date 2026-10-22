import type { AlertRule } from '../protocol/messages';

/**
 * Alert presentation (slice 7d, ADR-0018 AJ). The server evaluates the rules; the client only decides
 * what is loud. Acknowledging is the player's own business, so it lives here: an ack silences one firing,
 * and the next firing of the same rule (a new firingSinceTick) is loud again.
 */
export type AlertStatus = 'ok' | 'pending' | 'firing';

export function alertStatus(rule: AlertRule): AlertStatus {
  if (rule.firingSinceTick >= 0) return 'firing';
  if (rule.pendingSinceTick >= 0) return 'pending';
  return 'ok';
}

/** "cpu_utilization > 0.85 for 10 s" */
export function describeRule(rule: AlertRule): string {
  const wait = rule.forS > 0 ? ` for ${rule.forS} s` : '';
  return `${rule.metric} ${rule.op === 'ABOVE' ? '>' : '<'} ${rule.threshold}${wait}`;
}

/** One firing of one rule; acknowledging it does not silence the rule's next firing. */
export function firingKey(rule: AlertRule): string {
  return `${rule.id}@${rule.firingSinceTick}`;
}

export function unacknowledged(rules: readonly AlertRule[], acked: ReadonlySet<string>): AlertRule[] {
  return rules.filter((r) => alertStatus(r) === 'firing' && !acked.has(firingKey(r)));
}

/** Which map node a metric belongs to, for the red pulse; request-level metrics belong to the gateway. */
const NODE_OF_METRIC: Readonly<Record<string, string>> = {
  request_rate: 'gateway',
  latency_avg: 'gateway',
  latency_p99: 'gateway',
  error_rate: 'gateway',
  traffic_heatmap: 'gateway',
  cpu_utilization: 'cpu',
  tomcat_threads_busy: 'threads',
  hikaricp_connections_active: 'db_pool',
  hikaricp_connections_pending: 'db_pool',
  slow_queries: 'db_pool',
  disk_usage_ratio: 'disk',
  disk_fill_rate: 'disk',
  disk_forecast: 'disk',
  db_cpu_utilization: 'db_cpu',
  db_io_utilization: 'db_io',
};

export function nodeOfMetric(metric: string): string | undefined {
  return NODE_OF_METRIC[metric];
}

export function firingNodes(rules: readonly AlertRule[]): Set<string> {
  const nodes = new Set<string>();
  for (const r of rules) {
    const node = nodeOfMetric(r.metric);
    if (alertStatus(r) === 'firing' && node !== undefined) nodes.add(node);
  }
  return nodes;
}
