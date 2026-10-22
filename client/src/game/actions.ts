import type { CatalogAction } from '../protocol/messages';
import type { View } from './store';

export type ActionState =
  | { kind: 'running'; secondsLeft: number }
  | { kind: 'cooling'; secondsLeft: number }
  | { kind: 'no-crew' }
  | { kind: 'ready' };

/**
 * What an incident action card shows (slice 7b). The server stays the authority on crew and cooldown;
 * this only greys out buttons that would be rejected anyway. Running wins over cooling: while the
 * downtime or cold JVM lasts, that is what the player needs to see.
 */
export function actionState(action: CatalogAction, view: View, tickHz: number): ActionState {
  const run = view.actions.find((a) => a.id === action.id);
  if (run !== undefined && view.tick < run.endTick) {
    return { kind: 'running', secondsLeft: (run.endTick - view.tick) / tickHz };
  }
  if (run !== undefined && view.tick < run.readyTick) {
    return { kind: 'cooling', secondsLeft: (run.readyTick - view.tick) / tickHz };
  }
  if (action.toil > view.crew) return { kind: 'no-crew' };
  return { kind: 'ready' };
}
