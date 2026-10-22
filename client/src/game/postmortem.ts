import type { Postmortem } from '../protocol/messages';

export type ReviewStage =
  | { kind: 'none' }
  | { kind: 'question' }
  | { kind: 'sent'; option: string }
  | { kind: 'result'; chosen: string; correct: string; right: boolean };

/**
 * Where the postmortem dialog stands (slice 6a, decision Z). The server holds the truth: a question
 * is open until a snapshot carries the player's choice and, only then, the correct answer.
 * `sent` covers the gap between clicking and that snapshot (or a rejection), so the choice cannot be
 * sent twice. A result stays on screen until the player closes it.
 */
export class Review {
  private sentFor: number | undefined;
  private sentOption: string | undefined;
  private closedWave: number | undefined;

  stage(pm: Postmortem | null): ReviewStage {
    if (pm === null) return { kind: 'none' };
    if (pm.chosen !== null && pm.correct !== null) {
      if (this.closedWave === pm.wave) return { kind: 'none' };
      return { kind: 'result', chosen: pm.chosen, correct: pm.correct, right: pm.chosen === pm.correct };
    }
    if (this.sentFor === pm.wave && this.sentOption !== undefined) return { kind: 'sent', option: this.sentOption };
    return { kind: 'question' };
  }

  sent(wave: number, option: string): void {
    this.sentFor = wave;
    this.sentOption = option;
  }

  /** The server refused the answer: let the player try to send it again. */
  rejected(): void {
    this.sentFor = undefined;
    this.sentOption = undefined;
  }

  close(wave: number): void {
    this.closedWave = wave;
  }

  reset(): void {
    this.sentFor = undefined;
    this.sentOption = undefined;
    this.closedWave = undefined;
  }
}
