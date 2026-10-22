import type { CatalogItem, ClientAction, InstalledCounter } from '../protocol/messages';
import type { View } from './store';

/** A purchase sent to the server and not yet confirmed by a snapshot. */
export interface PendingOrder {
  counter: string;
  credits: number;
}

/**
 * Optimistic purchases (slice 5b, decision Y). A click shows the card as queued and the credits as
 * already spent; the server stays the authority: the order is confirmed when a snapshot lists the
 * counter and rolled back when a `rejected` message names it. During active pause the server holds
 * commands until resume, so an order can stay queued for a while; that is exactly when this matters.
 */
export class Orders {
  private readonly pending = new Map<string, PendingOrder>();
  private readonly rejections = new Map<string, string>();

  place(item: CatalogItem): void {
    this.pending.set(item.id, { counter: item.id, credits: item.credits });
    this.rejections.delete(item.id);
  }

  isPending(counter: string): boolean {
    return this.pending.has(counter);
  }

  /** Why the server refused the last order for this counter, until the player tries again. */
  rejection(counter: string): string | undefined {
    return this.rejections.get(counter);
  }

  /** Drops orders the server has confirmed. */
  confirm(installed: readonly InstalledCounter[]): void {
    for (const c of installed) this.pending.delete(c.id);
  }

  /** Rolls back the order a `rejected` message is about; other rejections are not purchases. */
  reject(action: ClientAction, reason: string): boolean {
    if (action.type !== 'apply_counter' || !this.pending.delete(action.counter)) return false;
    this.rejections.set(action.counter, reason);
    return true;
  }

  /** Credits as the player will have them once the queued orders go through. */
  projectedCredits(view: View): number {
    let credits = view.credits;
    for (const order of this.pending.values()) credits -= order.credits;
    return credits;
  }

  hasPending(): boolean {
    return this.pending.size > 0;
  }

  /** New session: nothing sent before belongs to it. */
  clear(): void {
    this.pending.clear();
    this.rejections.clear();
  }
}

export type CardState =
  | { kind: 'installed' }
  | { kind: 'building'; secondsLeft: number }
  | { kind: 'queued' }
  | { kind: 'locked'; needs: string[] }
  | { kind: 'unaffordable' }
  | { kind: 'no-crew' }
  | { kind: 'available' };

/** What a counter card shows, from the server's view plus the client's queued orders. */
export function cardState(item: CatalogItem, view: View, orders: Orders, tickHz: number): CardState {
  const installed = view.counters.find((c) => c.id === item.id);
  if (installed !== undefined) {
    return installed.readyTick > view.tick
      ? { kind: 'building', secondsLeft: (installed.readyTick - view.tick) / tickHz }
      : { kind: 'installed' };
  }
  if (orders.isPending(item.id)) return { kind: 'queued' };
  const owned = new Set(view.counters.map((c) => c.id));
  const needs = item.requiresCounters.filter((id) => !owned.has(id));
  if (needs.length > 0) return { kind: 'locked', needs };
  if (item.credits > orders.projectedCredits(view)) return { kind: 'unaffordable' };
  if (item.toil > view.crew) return { kind: 'no-crew' };
  return { kind: 'available' };
}
