import { describe, expect, it } from 'vitest';
import type { Briefing, Phase } from '../protocol/messages';
import { started, upcoming } from './briefing';
import { NODE01 } from './station';
import type { View } from './store';

function view(wave: number, phase: Phase, briefing: Briefing | null): View {
  return { tick: 0, credits: 0, wave, phase, shield: 1, burnRate: 0, shieldEmptyInS: -1, metrics: {}, signals: [],
    insights: [], counters: [], postmortem: null, codex: [], results: [], chapter: null, crew: 8, actions: [],
    alerts: [], station: NODE01, briefing };
}

const deploy: Briefing = { wave: 2, eventKey: 'briefing.deploy', comms: ['comms.festival_teaser'] };

describe('briefing', () => {
  it('shows the next wave while preparing, never a stale one', () => {
    expect(upcoming(view(1, 'COMPLETE', deploy))).toBe(deploy);
    expect(upcoming(view(2, 'RUNNING', deploy))).toBe(deploy); // the teaser stays in sight during its wave
    expect(upcoming(view(2, 'RUNNING', { ...deploy, comms: [] }))).toBeNull();
    expect(upcoming(view(2, 'COMPLETE', deploy))).toBeNull(); // the chapter has no wave left
  });

  it('flashes once, in the snapshot where the wave starts', () => {
    const before = view(1, 'COMPLETE', deploy);
    const now = view(2, 'RUNNING', deploy);
    expect(started(before, now)).toBe(deploy);
    expect(started(now, view(2, 'RUNNING', deploy))).toBeNull();
  });
});
