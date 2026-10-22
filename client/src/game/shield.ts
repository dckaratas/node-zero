import type { Phase } from '../protocol/messages';

/**
 * Shield (error budget) presentation, slice 7a decision AD. The server sends the shield, its burn rate
 * and the time left; this module decides how loud the HUD should be about them.
 */
export type ShieldLevel = 'ok' | 'warn' | 'danger' | 'empty';

/** Warning thresholds: yellow below half, orange below a quarter. */
export const WARN = 0.5;
export const DANGER = 0.25;

export function shieldLevel(shield: number): ShieldLevel {
  if (shield <= 0) return 'empty';
  if (shield < DANGER) return 'danger';
  if (shield < WARN) return 'warn';
  return 'ok';
}

export type ShieldAlert = 'warn' | 'danger' | 'collapse';

interface ShieldState {
  wave: number;
  phase: Phase;
  shield: number;
}

/**
 * The alert to raise when the view moves from {@code before} to {@code after}, or null. Each threshold
 * fires once per wave, on the way down; a wave that ends because the shield broke is a collapse.
 */
export function shieldAlert(before: ShieldState | undefined, after: ShieldState): ShieldAlert | null {
  if (before === undefined || before.wave !== after.wave) return null;
  if (before.phase === 'RUNNING' && after.phase === 'FAILED') return 'collapse';
  if (after.phase !== 'RUNNING') return null;
  if (before.shield >= DANGER && after.shield < DANGER) return 'danger';
  if (before.shield >= WARN && after.shield < WARN) return 'warn';
  return null;
}

/** "burn ×14 · empty in 40 s", or an empty string when the shield is not burning. */
export function burnText(burnRate: number, emptyInS: number): string {
  if (burnRate <= 0) return '';
  const rate = burnRate >= 10 ? burnRate.toFixed(0) : burnRate.toFixed(1);
  return emptyInS >= 0 ? `burn ×${rate} · empty in ${Math.ceil(emptyInS)} s` : `burn ×${rate}`;
}
