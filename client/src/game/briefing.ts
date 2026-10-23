import type { Briefing } from '../protocol/messages';
import type { View } from './store';

/**
 * The briefing the card shows (slice 9c): the next wave while preparing; during a wave only if it carries comms,
 * so an announcement (the festival teaser) stays in sight while its wave runs. Never a wave already over.
 */
export function upcoming(view: View | undefined): Briefing | null {
  const b = view?.briefing ?? null;
  if (view === undefined || b === null) return null;
  if (view.phase === 'RUNNING') return b.wave === view.wave && b.comms.length > 0 ? b : null;
  return b.wave > view.wave ? b : null;
}

/** The briefing to flash as a banner in the snapshot where a wave starts, or null. */
export function started(before: View | undefined, now: View): Briefing | null {
  if (now.phase !== 'RUNNING') return null;
  if (before !== undefined && before.phase === 'RUNNING' && before.wave === now.wave) return null;
  const b = now.briefing;
  return b !== null && b.wave === now.wave ? b : null;
}
