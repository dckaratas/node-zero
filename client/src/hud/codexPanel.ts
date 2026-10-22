import type { Codex } from '../protocol/messages';

/**
 * The codex (slice 6b, ADR-0008): one collapsible article per opened entry, newest first, marked
 * "new" until the player opens it. Uses <details> so it works with keyboard and screen readers
 * without extra code. Articles the player has open stay open when a new entry arrives.
 */
export class CodexPanel {
  private readonly heading = document.createElement('h2');
  private readonly list = document.createElement('div');
  private read = new Set<string>();

  constructor(host: HTMLElement) {
    this.list.className = 'codex';
    host.append(this.heading, this.list);
    this.reset();
  }

  /** New session: nothing opened, nothing read. */
  reset(): void {
    this.read = new Set();
    this.show({ language: 'en', entries: [] });
  }

  show(codex: Codex): void {
    this.heading.textContent = `Codex (${codex.entries.length})`;
    if (codex.entries.length === 0) {
      const hint = document.createElement('p');
      hint.className = 'hint';
      hint.textContent = 'Articles open as you buy modules and review incidents.';
      this.list.replaceChildren(hint);
      return;
    }
    const open = new Set([...this.list.querySelectorAll<HTMLDetailsElement>('details[open]')]
      .map((d) => d.dataset['id']));
    this.list.replaceChildren(...[...codex.entries].reverse().map((entry) => {
      const details = document.createElement('details');
      details.dataset['id'] = entry.id;
      details.open = open.has(entry.id);
      const summary = document.createElement('summary');
      summary.textContent = entry.title;
      if (!this.read.has(entry.id)) {
        const badge = document.createElement('span');
        badge.className = 'badge';
        badge.textContent = 'new';
        summary.append(' ', badge);
      }
      details.addEventListener('toggle', () => {
        if (!details.open) return;
        this.read.add(entry.id);
        summary.querySelector('.badge')?.remove();
      });
      details.append(summary, ...entry.body.split(/\n\s*\n/).map((text) => {
        const p = document.createElement('p');
        p.textContent = text;
        return p;
      }));
      return details;
    }));
  }
}
