import { describe, expect, it } from 'vitest';
import type { CatalogItem, InstalledCounter } from '../protocol/messages';
import { nodeMarks, queueDots, redDots } from './mapFeedback';
import { NODE01 } from './station';
import type { View } from './store';

function item(id: string, nodes: string[], applyTimeS = 10): CatalogItem {
  return {
    id, name: id, realTech: id, credits: 0, upkeepPerWave: 0, toil: 0, applyTimeS, requiresCounters: [],
    requiresInsight: null, grantsInsights: [], tradeoff: '', options: [], removable: true, setting: null, nodes,
  };
}

function view(counters: InstalledCounter[], tick: number, appDown = false): View {
  return { tick, credits: 0, wave: 0, phase: 'PREPARATION', shield: 1, burnRate: 0, shieldEmptyInS: -1, metrics: {},
    signals: [], insights: [], counters, postmortem: null, codex: [], results: [], chapter: null, crew: 8, actions: [],
    alerts: [], station: { ...NODE01, appDown } };
}

const counter = (id: string, readyTick: number, removedTick = -1): InstalledCounter =>
  ({ id, option: null, readyTick, removedTick, setting: null });

const items = [item('cpu-up', ['cpu']), item('index', ['db_cpu', 'db_io']), item('prometheus', [])];

describe('nodeMarks', () => {
  it('counts modules per target node and shows build progress from the apply time', () => {
    const marks = nodeMarks(view([counter('cpu-up', 200), counter('index', 50), counter('prometheus', 300)], 150),
      items, 10);
    expect(marks.get('cpu')).toEqual({ modules: 1, progress: { ratio: 0.5, removing: false }, down: false });
    expect(marks.get('db_io')).toEqual({ modules: 1, progress: null, down: false });
    expect(marks.has('gateway')).toBe(false); // monitoring has no target
  });

  it('shows removal progress and drops a module once it is gone', () => {
    expect(nodeMarks(view([counter('cpu-up', 50, 200)], 175), items, 10).get('cpu')?.progress)
      .toEqual({ ratio: 0.75, removing: true });
    expect(nodeMarks(view([counter('cpu-up', 50, 200)], 200), items, 10).has('cpu')).toBe(false);
  });

  it('marks the app nodes down while the app is stopped', () => {
    const marks = nodeMarks(view([], 0, true), items, 10);
    expect([...marks].filter(([, m]) => m.down).map(([id]) => id)).toEqual(['threads', 'cpu']);
  });
});

describe('queue and failure dots', () => {
  it('stacks measured waiting requests, capped', () => {
    expect([undefined, 0.3, 2.4, 40].map(queueDots)).toEqual([0, 0, 2, 8]);
  });

  it('reddens flow in proportion to failures but never hides them', () => {
    expect([undefined, 0.005, 0.02, 0.5, 1].map((r) => redDots(r, 6))).toEqual([0, 0, 1, 3, 6]);
  });
});
