import type { ChapterSummary, WaveResult } from '../protocol/messages';

/** Average shield that earns the second star; ChapterScore.SHIELD_STAR on the server. */
const SHIELD_STAR = 0.5;

/**
 * The chapter summary (slice 7a, decision AE): shown once when the last wave is won and reviewed,
 * again only if a later result changes it. One star for finishing, one for the shield, one for root causes.
 */
export class ChapterDialog {
  private readonly dialog = document.createElement('dialog');
  private shown = '';

  constructor() {
    this.dialog.className = 'chapter';
    this.dialog.setAttribute('aria-labelledby', 'chapter-title');
    document.body.append(this.dialog);
  }

  render(chapter: ChapterSummary | null, results: readonly WaveResult[]): void {
    if (chapter === null) return;
    const key = JSON.stringify(chapter);
    if (key === this.shown) return;
    this.shown = key;
    this.dialog.replaceChildren(...content(chapter, results, () => this.dialog.close()));
    if (!this.dialog.open) this.dialog.showModal();
  }
}

function content(chapter: ChapterSummary, results: readonly WaveResult[], close: () => void): HTMLElement[] {
  const title = text('h2', `Chapter ${chapter.number} complete`);
  title.id = 'chapter-title';
  const stars = text('p', '★'.repeat(chapter.stars) + '☆'.repeat(3 - chapter.stars));
  stars.className = 'stars';
  stars.setAttribute('aria-label', `${chapter.stars} of 3 stars`);

  const table = document.createElement('table');
  const head = document.createElement('tr');
  head.append(...['Wave', 'Shield left', 'Credits', 'Root cause'].map((h) => text('th', h)));
  table.append(head);
  for (const r of results) {
    const row = document.createElement('tr');
    const verdict = r.postmortemRight === null ? '—' : r.postmortemRight ? 'right' : 'missed';
    row.append(text('td', String(r.wave)), text('td', `${Math.round(r.shield * 100)}%`),
      text('td', r.earned > 0 ? `+${r.earned}` : String(r.earned)), text('td', verdict));
    table.append(row);
  }

  const shield = Math.round(chapter.averageShield * 100);
  const allRight = chapter.postmortemsRight === chapter.postmortems;
  const notes = document.createElement('ul');
  notes.append(
    text('li', '★ Chapter finished'),
    text('li', `${chapter.averageShield >= SHIELD_STAR ? '★' : '☆'} Average shield ${shield}% (50% earns a star)`),
    text('li', `${allRight ? '★' : '☆'} Root causes ${chapter.postmortemsRight}/${chapter.postmortems}`));

  const back = text('button', 'Back to the station') as HTMLButtonElement;
  back.type = 'button';
  back.onclick = close;
  return [title, stars, table, notes, back];
}

function text(tag: string, content: string): HTMLElement {
  const e = document.createElement(tag);
  e.textContent = content;
  return e;
}
