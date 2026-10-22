/**
 * Content language (ADR-0009, slice 6c decision AC). The interface stays English; threats, counters,
 * postmortems and the codex come in English or Turkish. The choice is a per-browser convenience, so
 * it lives in localStorage, which may be missing or throw (private mode): every access is guarded.
 */
export type ContentLanguage = 'en' | 'tr';

export const LANGUAGES: readonly { code: ContentLanguage; label: string }[] = [
  { code: 'en', label: 'English' },
  { code: 'tr', label: 'Türkçe' },
];

const KEY = 'nodezero.contentLanguage';

/** A stored choice wins; otherwise Turkish browsers start in Turkish and everyone else in English. */
export function initialLanguage(stored: string | null, browserLanguages: readonly string[]): ContentLanguage {
  if (stored === 'en' || stored === 'tr') return stored;
  return browserLanguages.some((l) => l.toLowerCase().startsWith('tr')) ? 'tr' : 'en';
}

export function loadLanguage(): ContentLanguage {
  let stored: string | null = null;
  try {
    stored = localStorage.getItem(KEY);
  } catch {
    // storage unavailable: fall back to the browser language
  }
  return initialLanguage(stored, navigator.languages);
}

export function saveLanguage(language: ContentLanguage): void {
  try {
    localStorage.setItem(KEY, language);
  } catch {
    // not persisted; the choice still applies to this page
  }
}
