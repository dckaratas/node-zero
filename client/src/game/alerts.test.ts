import { describe, expect, it } from 'vitest';
import type { AlertRule } from '../protocol/messages';
import { alertStatus, describeRule, firingKey, firingNodes, unacknowledged } from './alerts';

const rule = (over: Partial<AlertRule> = {}): AlertRule => ({
  id: 'a1', metric: 'cpu_utilization', op: 'ABOVE', threshold: 0.85, forS: 10,
  pendingSinceTick: -1, firingSinceTick: -1, ...over,
});

describe('alerts', () => {
  it('reads the state the server evaluated', () => {
    expect(alertStatus(rule())).toBe('ok');
    expect(alertStatus(rule({ pendingSinceTick: 100 }))).toBe('pending');
    expect(alertStatus(rule({ pendingSinceTick: 100, firingSinceTick: 200 }))).toBe('firing');
  });

  it('reads like a Prometheus rule', () => {
    expect(describeRule(rule())).toBe('cpu_utilization > 0.85 for 10 s');
    expect(describeRule(rule({ op: 'BELOW', threshold: 50, forS: 0, metric: 'request_rate' }))).toBe('request_rate < 50');
  });

  it('an ack silences one firing, not the rule', () => {
    const firing = rule({ pendingSinceTick: 100, firingSinceTick: 200 });
    const acked = new Set([firingKey(firing)]);
    expect(unacknowledged([firing], acked)).toEqual([]);
    const again = rule({ pendingSinceTick: 400, firingSinceTick: 500 });
    expect(unacknowledged([again], acked)).toEqual([again]);
    expect(unacknowledged([rule({ pendingSinceTick: 100 })], new Set())).toEqual([]);
  });

  it('points the map at the node a firing metric belongs to', () => {
    const cpu = rule({ pendingSinceTick: 1, firingSinceTick: 2 });
    const pool = rule({ id: 'a2', metric: 'hikaricp_connections_pending', pendingSinceTick: 1, firingSinceTick: 2 });
    expect([...firingNodes([cpu, pool, rule({ id: 'a3', metric: 'disk_usage_ratio' })])]).toEqual(['cpu', 'db_pool']);
  });
});
