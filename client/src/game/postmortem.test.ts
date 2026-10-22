import { describe, expect, it } from 'vitest';
import type { Postmortem } from '../protocol/messages';
import { Review } from './postmortem';

function pm(chosen: string | null = null, correct: string | null = null, wave = 2): Postmortem {
  return {
    wave, questionKey: 'pm.log_storm.q', chosen, correct,
    options: [{ id: 'a', key: 'pm.a' }, { id: 'b', key: 'pm.b' }],
  };
}

describe('Review', () => {
  it('asks while the question is open', () => {
    expect(new Review().stage(pm())).toEqual({ kind: 'question' });
    expect(new Review().stage(null)).toEqual({ kind: 'none' });
  });

  it('holds the choice until the server confirms it, then shows the result', () => {
    const review = new Review();
    review.sent(2, 'a');

    expect(review.stage(pm())).toEqual({ kind: 'sent', option: 'a' });
    expect(review.stage(pm('a', 'b'))).toEqual({ kind: 'result', chosen: 'a', correct: 'b', right: false });
  });

  it('lets the player send again after a rejection', () => {
    const review = new Review();
    review.sent(2, 'a');
    review.rejected();

    expect(review.stage(pm())).toEqual({ kind: 'question' });
  });

  it('keeps the result until closed, and only for that wave', () => {
    const review = new Review();
    expect(review.stage(pm('b', 'b'))).toMatchObject({ kind: 'result', right: true });

    review.close(2);
    expect(review.stage(pm('b', 'b'))).toEqual({ kind: 'none' });
    expect(review.stage(pm(null, null, 3))).toEqual({ kind: 'question' });
  });
});
