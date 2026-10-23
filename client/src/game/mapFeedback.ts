import type { CatalogItem } from '../protocol/messages';
import type { View } from './store';

/** What the map draws on one node besides its load (slice 8b, decision AP). */
export interface NodeMarks {
  /** Installed modules acting on this node, including those still being built or removed. */
  modules: number;
  /** A build or removal under way: share done, 0..1. */
  progress: { ratio: number; removing: boolean } | null;
  /** The app is stopped, so this node serves nothing. */
  down: boolean;
}

/** The app process: what a restart, a rollback or a CPU swap stops. */
const APP_NODES = new Set(['threads', 'cpu']);
const MAX_QUEUE_DOTS = 8;

/**
 * Badges, progress rings and DOWN per node. Modules without a target (monitoring) are left out: they belong
 * to every node, and a ring on all seven hexes would say nothing. Progress uses the catalog's apply time,
 * because removal takes as long as the build did (slice 7c).
 */
export function nodeMarks(view: View, items: readonly CatalogItem[], tickHz: number): Map<string, NodeMarks> {
  const marks = new Map<string, NodeMarks>();
  const at = (node: string): NodeMarks => {
    let m = marks.get(node);
    if (m === undefined) {
      m = { modules: 0, progress: null, down: false };
      marks.set(node, m);
    }
    return m;
  };
  for (const counter of view.counters) {
    const item = items.find((i) => i.id === counter.id);
    if (item === undefined) continue;
    const removing = counter.removedTick >= 0;
    if (removing && view.tick >= counter.removedTick) continue;
    const option = item.options.find((o) => o.id === counter.option);
    const durationTicks = (option?.applyTimeS ?? item.applyTimeS) * tickHz;
    const endTick = removing ? counter.removedTick : counter.readyTick;
    const inProgress = durationTicks > 0 && view.tick < endTick;
    for (const node of item.nodes) {
      const m = at(node);
      m.modules++;
      if (inProgress && m.progress === null) {
        m.progress = { ratio: Math.min(1, Math.max(0, 1 - (endTick - view.tick) / durationTicks)), removing };
      }
    }
  }
  if (view.station.appDown) for (const node of APP_NODES) at(node).down = true;
  return marks;
}

/**
 * Requests waiting for a pool connection, as a short stack of dots beside HikariCP. Only the measured
 * pending count is drawn: Tomcat's accept queue is not exported, so it stays invisible (decision AQ).
 */
export function queueDots(pending: number | undefined): number {
  if (pending === undefined || pending < 0.5) return 0;
  return Math.min(MAX_QUEUE_DOTS, Math.round(pending));
}

/** How many of {@code total} flow dots are red: at least one once 1 % of requests fail, so failures never hide. */
export function redDots(errorRate: number | undefined, total: number): number {
  if (errorRate === undefined || errorRate < 0.01) return 0;
  return Math.min(total, Math.max(1, Math.round(errorRate * total)));
}
