import { shieldLevel } from '../game/shield';
import type { Phase } from '../protocol/messages';

/** How the dome around the station looks: colour by error budget, motion by phase and pause. */
export interface DomeLook {
  color: number;
  /** Edge opacity; the inner band fades from it towards the centre. */
  alpha: number;
  /** Requests fly in from outside only while a wave runs. */
  incoming: boolean;
  /** False while paused: dots and the pulse freeze in place. */
  moving: boolean;
}

const COLORS = { ok: 0x7fd4ff, warn: 0xd9a334, danger: 0xd9534f, empty: 0xd9534f } as const;

export function domeLook(phase: Phase | undefined, shield: number, paused: boolean): DomeLook {
  const running = phase === 'RUNNING';
  const level = shieldLevel(shield);
  return {
    color: COLORS[level],
    alpha: level === 'empty' ? 0.25 : running ? 0.8 : 0.4,
    incoming: running,
    moving: running && !paused,
  };
}

/** One incoming request: where it heads on the dome edge and how far it has come (0 outside, 1 at the edge). */
export interface IncomingDot {
  angle: number;
  t: number;
}

const GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));

/**
 * {@code count} requests spread evenly in time; each new trip takes a fresh direction, so arrivals look scattered
 * without keeping per-dot state. {@code phase} grows without bound (seconds of travel).
 */
export function incomingDots(phase: number, count: number): IncomingDot[] {
  const dots: IncomingDot[] = [];
  for (let i = 0; i < count; i++) {
    const progress = phase + i / count;
    const trip = Math.floor(progress);
    dots.push({ angle: ((i * count + trip) * GOLDEN_ANGLE) % (2 * Math.PI), t: progress - trip });
  }
  return dots;
}
