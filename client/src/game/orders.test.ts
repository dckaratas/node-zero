import { describe, expect, it } from 'vitest';
import type { CatalogItem } from '../protocol/messages';
import { cardState, Orders } from './orders';
import type { View } from './store';

function item(id: string, credits: number, requiresCounters: string[] = []): CatalogItem {
  return {
    id, name: id, realTech: id, credits, upkeepPerWave: 0, applyTimeS: 5, requiresCounters,
    requiresInsight: null, grantsInsights: [], tradeoff: '', options: [],
  };
}

function view(credits: number, counters: View['counters'] = [], tick = 100): View {
  return { tick, credits, wave: 0, phase: 'PREPARATION', shield: 1, metrics: {}, signals: [], insights: [], counters,
    postmortem: null };
}

const prometheus = item('prometheus', 200);
const grafana = item('grafana', 150, ['prometheus']);

describe('Orders', () => {
  it('spends credits at once and keeps the card queued until the server confirms', () => {
    const orders = new Orders();
    orders.place(prometheus);

    expect(orders.projectedCredits(view(1000))).toBe(800);
    expect(cardState(prometheus, view(1000), orders, 10)).toEqual({ kind: 'queued' });

    const confirmed = view(800, [{ id: 'prometheus', option: null, readyTick: 150 }]);
    orders.confirm(confirmed.counters);
    expect(orders.projectedCredits(confirmed)).toBe(800);
    expect(cardState(prometheus, confirmed, orders, 10)).toEqual({ kind: 'building', secondsLeft: 5 });
  });

  it('rolls back a rejected order and remembers why', () => {
    const orders = new Orders();
    orders.place(prometheus);

    expect(orders.reject({ type: 'apply_counter', counter: 'prometheus' }, 'only 100 left')).toBe(true);
    expect(orders.projectedCredits(view(1000))).toBe(1000);
    expect(orders.rejection('prometheus')).toBe('only 100 left');
    expect(orders.reject({ type: 'start_wave', wave: 1 }, 'wave 1 is still running')).toBe(false);
  });

  it('counts queued orders against what the player can still afford', () => {
    const orders = new Orders();
    orders.place(item('vertical-scale-cpu', 400));

    expect(cardState(item('expand-disk', 300), view(600), orders, 10)).toEqual({ kind: 'unaffordable' });
    expect(cardState(item('logrotate', 150), view(600), orders, 10)).toEqual({ kind: 'available' });
  });
});

describe('cardState', () => {
  it('locks a counter until the counters it builds on are bought', () => {
    const orders = new Orders();
    expect(cardState(grafana, view(1000), orders, 10)).toEqual({ kind: 'locked', needs: ['prometheus'] });
    const owned = view(800, [{ id: 'prometheus', option: null, readyTick: 150 }]);
    expect(cardState(grafana, owned, orders, 10)).toEqual({ kind: 'available' });
  });

  it('shows a finished build as installed', () => {
    const done = view(800, [{ id: 'prometheus', option: null, readyTick: 50 }]);
    expect(cardState(prometheus, done, new Orders(), 10)).toEqual({ kind: 'installed' });
  });
});
