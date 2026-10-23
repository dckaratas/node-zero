import { describe, expect, it } from 'vitest';
import { domeLook, incomingDots } from './shieldDome';

describe('domeLook', () => {
  it('only lets requests in while a wave runs, and freezes them on pause', () => {
    expect(domeLook('PREPARATION', 1, false)).toMatchObject({ incoming: false, moving: false, alpha: 0.4 });
    expect(domeLook('RUNNING', 1, false)).toMatchObject({ incoming: true, moving: true, alpha: 0.8 });
    expect(domeLook('RUNNING', 1, true)).toMatchObject({ incoming: true, moving: false });
  });

  it('colours the dome by what is left of the error budget', () => {
    expect([1, 0.4, 0.1, 0].map((s) => domeLook('RUNNING', s, false).color))
      .toEqual([0x7fd4ff, 0xd9a334, 0xd9534f, 0xd9534f]);
    expect(domeLook('FAILED', 0, false).alpha).toBe(0.25);
  });
});

describe('incomingDots', () => {
  it('spreads requests evenly along the way in', () => {
    expect(incomingDots(0.25, 4).map((d) => d.t)).toEqual([0.25, 0.5, 0.75, 0]);
  });

  it('sends each new trip from another direction', () => {
    const first = incomingDots(0.9, 3)[0];
    const next = incomingDots(1.1, 3)[0];
    expect(next?.t).toBeCloseTo(0.1);
    expect(next?.angle).not.toBeCloseTo(first?.angle ?? 0);
  });
});
