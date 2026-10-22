import { describe, expect, it } from 'vitest';
import { initialLanguage } from './language';

describe('initialLanguage', () => {
  it('keeps the player\'s stored choice', () => {
    expect(initialLanguage('en', ['tr-TR'])).toBe('en');
    expect(initialLanguage('tr', ['en-US'])).toBe('tr');
  });

  it('starts Turkish browsers in Turkish, everyone else in English', () => {
    expect(initialLanguage(null, ['tr-TR', 'en'])).toBe('tr');
    expect(initialLanguage(null, ['de-DE', 'TR'])).toBe('tr');
    expect(initialLanguage(null, ['en-GB'])).toBe('en');
    expect(initialLanguage(null, [])).toBe('en');
  });

  it('ignores a stored value it does not know', () => {
    expect(initialLanguage('fr', ['en-US'])).toBe('en');
  });
});
