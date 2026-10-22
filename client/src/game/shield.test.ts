import { describe, expect, it } from 'vitest';
import { burnText, shieldAlert, shieldLevel } from './shield';

const running = (shield: number, wave = 3) => ({ wave, phase: 'RUNNING' as const, shield });

describe('shield', () => {
  it('grows louder as the budget runs out', () => {
    expect(shieldLevel(1)).toBe('ok');
    expect(shieldLevel(0.5)).toBe('ok');
    expect(shieldLevel(0.49)).toBe('warn');
    expect(shieldLevel(0.2)).toBe('danger');
    expect(shieldLevel(0)).toBe('empty');
  });

  it('warns once on the way down through each threshold', () => {
    expect(shieldAlert(running(0.6), running(0.45))).toBe('warn');
    expect(shieldAlert(running(0.45), running(0.4))).toBeNull();
    expect(shieldAlert(running(0.3), running(0.2))).toBe('danger');
    expect(shieldAlert(running(0.6), running(0.1))).toBe('danger');
    expect(shieldAlert(undefined, running(0.1))).toBeNull();
    expect(shieldAlert(running(0.9, 2), running(0.1, 3))).toBeNull(); // a new wave starts fresh
  });

  it('calls a wave that failed while running a collapse', () => {
    expect(shieldAlert(running(0.05), { wave: 3, phase: 'FAILED', shield: 0 })).toBe('collapse');
    expect(shieldAlert(running(0.5), { wave: 3, phase: 'COMPLETE', shield: 0.5 })).toBeNull();
  });

  it('says how fast it burns and when it breaks', () => {
    expect(burnText(0, -1)).toBe('');
    expect(burnText(80.8, 1.38)).toBe('burn ×81 · empty in 2 s');
    expect(burnText(2.345, 39.2)).toBe('burn ×2.3 · empty in 40 s');
  });
});
