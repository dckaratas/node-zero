import { describe, expect, it } from 'vitest';
import type { Snapshot } from '../protocol/messages';
import { GameStore } from './store';

function snapshot(tick: number, cpu: number, extra: Partial<Snapshot> = {}): Snapshot {
  return {
    type: 'snapshot', tick, credits: 1000, wave: 0, phase: 'PREPARATION', shield: 1,
    metrics: { cpu_utilization: cpu }, signals: [], ...extra,
  };
}

describe('GameStore', () => {
  it('keeps the lists a delta leaves out', () => {
    const store = new GameStore(200);
    store.apply(snapshot(2, 0.5, { counters: ['prometheus'], insights: [] }), 0);
    const view = store.apply(snapshot(4, 0.6), 200);

    expect(view.counters).toEqual(['prometheus']);
    expect(view.insights).toEqual([]);
  });

  it('blends numbers from the previous view to the latest over one interval', () => {
    const store = new GameStore(200);
    store.apply(snapshot(2, 0.4, { shield: 1 }), 0);
    store.apply(snapshot(4, 0.8, { shield: 0.5 }), 1000);

    expect(store.sample(1000)?.metrics['cpu_utilization']).toBeCloseTo(0.4);
    expect(store.sample(1100)?.metrics['cpu_utilization']).toBeCloseTo(0.6);
    expect(store.sample(1100)?.shield).toBeCloseTo(0.75);
    expect(store.sample(5000)?.metrics['cpu_utilization']).toBeCloseTo(0.8);
  });

  it('switches discrete fields at once', () => {
    const store = new GameStore(200);
    store.apply(snapshot(2, 0.4), 0);
    store.apply(snapshot(4, 0.4, { phase: 'RUNNING', wave: 1, credits: 800 }), 1000);

    expect(store.sample(1000)).toMatchObject({ phase: 'RUNNING', wave: 1, credits: 800, tick: 4 });
  });

  it('shows a newly visible metric at its value, not sweeping from zero', () => {
    const store = new GameStore(200);
    store.apply(snapshot(2, 0.4), 0);
    store.apply(snapshot(4, 0.4, { metrics: { cpu_utilization: 0.4, latency_avg: 30 } }), 1000);

    expect(store.sample(1050)?.metrics['latency_avg']).toBe(30);
  });

  it('has nothing to show before the first snapshot or after a reset', () => {
    const store = new GameStore(200);
    expect(store.sample(0)).toBeUndefined();
    store.apply(snapshot(2, 0.4), 0);
    store.reset();
    expect(store.sample(10)).toBeUndefined();
  });
});
