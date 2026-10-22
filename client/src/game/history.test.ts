import { describe, expect, it } from 'vitest';
import { LoadHistory, sparkline } from './history';
import { NODE01 } from './station';
import type { View } from './store';

function view(cpu: number | undefined): View {
  return { tick: 0, credits: 0, wave: 0, phase: 'RUNNING', shield: 1, burnRate: 0, shieldEmptyInS: -1,
    metrics: cpu === undefined ? {} : { cpu_utilization: cpu }, signals: [], insights: [], counters: [],
    postmortem: null, codex: [], results: [], chapter: null, crew: 8, actions: [], alerts: [], station: NODE01 };
}

describe('LoadHistory', () => {
  it('keeps the last points per node, with gaps where nothing was measured', () => {
    const history = new LoadHistory(3);
    for (const cpu of [0.1, undefined, 0.5, 0.9]) history.record(view(cpu));
    expect(history.of('cpu')).toEqual([Number.NaN, 0.5, 0.9]);
  });

  it('breaks the trend line at gaps', () => {
    expect(sparkline([0, 1, Number.NaN, 0.5, 0.5], 40, 10)).toEqual(['0.0,10.0 10.0,0.0', '30.0,5.0 40.0,5.0']);
    expect(sparkline([0.5], 40, 10)).toEqual([]);
  });
});
