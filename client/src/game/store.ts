import type { Phase, Snapshot } from '../protocol/messages';

/** A complete view: snapshot deltas merged onto what the client already knows. */
export interface View {
  tick: number;
  credits: number;
  wave: number;
  phase: Phase;
  shield: number;
  metrics: Readonly<Record<string, number>>;
  signals: readonly string[];
  insights: readonly string[];
  counters: readonly string[];
}

/**
 * Latest two views from the server and a smooth sample between them (slice 5 decision P).
 * The server sends at most snapshotHz; drawing at 60 fps from the last view alone would make
 * gauges jump 5 times a second. Instead the client shows the move from the previous view to the
 * latest one over one snapshot interval: everything is one interval (~200 ms) behind, never guessed.
 * Discrete fields (phase, credits, lists) switch at once; only numbers that drift are blended.
 */
export class GameStore {
  private previous: View | undefined;
  private latest: View | undefined;
  private latestAt = 0;

  constructor(private intervalMs: number) {}

  setSnapshotRate(hz: number): void {
    this.intervalMs = 1000 / hz;
  }

  /** Forget everything, e.g. after a reconnect opened a new session. */
  reset(): void {
    this.previous = undefined;
    this.latest = undefined;
  }

  apply(s: Snapshot, nowMs: number): View {
    const base = this.latest;
    const view: View = {
      tick: s.tick,
      credits: s.credits,
      wave: s.wave,
      phase: s.phase,
      shield: s.shield,
      metrics: s.metrics,
      signals: s.signals,
      insights: s.insights ?? base?.insights ?? [],
      counters: s.counters ?? base?.counters ?? [],
    };
    this.previous = base ?? view;
    this.latest = view;
    this.latestAt = nowMs;
    return view;
  }

  /** The latest view without blending. */
  current(): View | undefined {
    return this.latest;
  }

  sample(nowMs: number): View | undefined {
    const to = this.latest;
    const from = this.previous;
    if (to === undefined || from === undefined) return to;
    const t = Math.min(1, Math.max(0, (nowMs - this.latestAt) / this.intervalMs));
    if (t === 1 || from === to) return to;
    const metrics: Record<string, number> = {};
    for (const [name, value] of Object.entries(to.metrics)) {
      const start = from.metrics[name];
      // A metric that just became visible appears at its value instead of sweeping up from zero.
      metrics[name] = start === undefined ? value : lerp(start, value, t);
    }
    return { ...to, shield: lerp(from.shield, to.shield, t), metrics };
  }
}

function lerp(a: number, b: number, t: number): number {
  return a + (b - a) * t;
}
