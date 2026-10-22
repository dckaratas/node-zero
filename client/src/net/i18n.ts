/**
 * Content texts by key (ADR-0009, slice 6 decision AC). The server serves the public keys
 * (postmortem, hints, comms) as one bundle per language; the browser caches it by ETag.
 * An unknown key renders as the key itself, so a missing text is visible instead of blank.
 */
export class Texts {
  private texts: Readonly<Record<string, string>> = {};

  t(key: string): string {
    return this.texts[key] ?? key;
  }

  async load(language: string): Promise<void> {
    const response = await fetch(`/api/i18n/${encodeURIComponent(language)}`);
    if (!response.ok) throw new Error(`i18n: HTTP ${response.status}`);
    const body = (await response.json()) as unknown;
    if (typeof body !== 'object' || body === null) throw new Error('i18n: expected an object');
    const texts: Record<string, string> = {};
    for (const [key, value] of Object.entries(body)) {
      if (typeof value === 'string') texts[key] = value;
    }
    this.texts = texts;
  }
}
