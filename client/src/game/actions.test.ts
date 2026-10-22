import { describe, expect, it } from 'vitest';
import type { ActionRun, CatalogAction } from '../protocol/messages';
import { actionState } from './actions';
import type { View } from './store';
import { NODE01 } from './station';

const restart: CatalogAction = {
  id: 'restart-app', name: 'Restart', realTech: 'systemctl restart', detail: '', toil: 2, cooldownS: 30, options: [],
};

function view(tick: number, crew: number, actions: ActionRun[] = []): View {
  return { tick, credits: 1000, wave: 3, phase: 'RUNNING', shield: 1, burnRate: 0, shieldEmptyInS: -1, metrics: {},
    signals: [], insights: [], counters: [], postmortem: null, codex: [], results: [], chapter: null, crew, actions, alerts: [], station: NODE01 };
}

describe('actionState', () => {
  const run: ActionRun = { id: 'restart-app', option: null, startTick: 100, endTick: 400, readyTick: 400 };

  it('is ready with enough crew and no cooldown', () => {
    expect(actionState(restart, view(100, 8), 10)).toEqual({ kind: 'ready' });
  });

  it('shows the running window first, then the cooldown', () => {
    expect(actionState(restart, view(150, 6, [run]), 10)).toEqual({ kind: 'running', secondsLeft: 25 });
    const instant: ActionRun = { ...run, endTick: 100, readyTick: 250 };
    expect(actionState(restart, view(150, 6, [instant]), 10)).toEqual({ kind: 'cooling', secondsLeft: 10 });
    expect(actionState(restart, view(400, 6, [run]), 10)).toEqual({ kind: 'ready' });
  });

  it('needs enough crew', () => {
    expect(actionState(restart, view(100, 1), 10)).toEqual({ kind: 'no-crew' });
  });
});
