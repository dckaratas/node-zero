import { actionState, type ActionState } from '../game/actions';
import type { View } from '../game/store';
import type { Catalog, CatalogAction } from '../protocol/messages';

interface Card {
  action: CatalogAction;
  root: HTMLElement;
  status: HTMLElement;
  buttons: HTMLButtonElement[];
  key: string;
}

/**
 * Incident actions (slice 7b, ADR-0018 AF/AH): one button per way of doing it, because an incident is
 * no time for a two-step form. Each costs crew and cools down; the card says which it is.
 */
export class ActionPanel {
  private readonly list = document.createElement('div');
  private cards: Card[] = [];

  constructor(host: HTMLElement, private readonly run: (action: string, option?: string) => void,
    private readonly tickHz: () => number) {
    this.list.className = 'cards actions';
    host.append(this.list);
  }

  reset(): void {
    this.cards = [];
    this.list.replaceChildren();
  }

  setCatalog(catalog: Catalog): void {
    this.cards = catalog.actions.map((a) => this.card(a));
    this.list.replaceChildren(...this.cards.map((c) => c.root));
  }

  render(view: View | undefined): void {
    if (view === undefined) return;
    for (const card of this.cards) {
      const state = actionState(card.action, view, this.tickHz());
      const key = `${state.kind}|${'secondsLeft' in state ? Math.ceil(state.secondsLeft) : ''}`;
      if (key === card.key) continue;
      card.key = key;
      card.root.dataset['state'] = state.kind;
      card.status.textContent = describe(state);
      for (const b of card.buttons) b.disabled = state.kind !== 'ready';
    }
  }

  private card(action: CatalogAction): Card {
    const root = document.createElement('article');
    root.className = 'card action';
    root.append(text('h3', action.name), text('p', action.realTech, 'tech'),
      text('p', `crew ${action.toil} · cooldown ${action.cooldownS} s`, 'cost'), text('p', action.detail, 'tradeoff'));
    const status = text('p', '', 'status');
    status.setAttribute('aria-live', 'polite');

    const ways = action.options.length > 0 ? action.options : [{ id: '', name: 'Run', detail: '' }];
    const buttons = ways.map((way) => {
      const b = document.createElement('button');
      b.type = 'button';
      b.textContent = way.name;
      if (way.detail) b.title = way.detail;
      b.onclick = () => this.run(action.id, way.id === '' ? undefined : way.id);
      return b;
    });
    const row = document.createElement('div');
    row.className = 'ways';
    row.append(...buttons);
    root.append(status, row);
    return { action, root, status, buttons, key: '' };
  }
}

function describe(state: ActionState): string {
  switch (state.kind) {
    case 'running': return `In progress… ${Math.ceil(state.secondsLeft)} s`;
    case 'cooling': return `Cooling down: ${Math.ceil(state.secondsLeft)} s`;
    case 'no-crew': return 'Crew too tired: wait for the next shift';
    case 'ready': return '';
  }
}

function text(tag: string, content: string, className?: string): HTMLElement {
  const e = document.createElement(tag);
  e.textContent = content;
  if (className !== undefined) e.className = className;
  return e;
}
