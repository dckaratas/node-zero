import { cardState, type CardState, type Orders } from '../game/orders';
import type { View } from '../game/store';
import type { Catalog, CatalogItem } from '../protocol/messages';

interface Card {
  item: CatalogItem;
  root: HTMLElement;
  status: HTMLElement;
  button: HTMLButtonElement;
  option: () => string | undefined;
  /** Last rendered state, so a frame only touches the DOM when something changed. */
  key: string;
}

/**
 * Counter cards (slice 5b). Counters gated by an insight are not in the catalog until the player has
 * it (decision W, filtered on the server); when one appears after the first load it is marked as
 * newly discovered. Buying is optimistic (decision Y, see {@link Orders}).
 */
export class CounterPanel {
  private readonly list = document.createElement('div');
  private cards: Card[] = [];
  private known: Set<string> | undefined;
  private names = new Map<string, string>();

  constructor(host: HTMLElement, private readonly orders: Orders, private readonly buy: (item: CatalogItem, option?: string) => void,
    private readonly tickHz: () => number) {
    this.list.className = 'cards';
    host.append(this.list);
  }

  /** New session: forget what was discovered. */
  reset(): void {
    this.known = undefined;
    this.cards = [];
    this.list.replaceChildren();
  }

  setCatalog(catalog: Catalog): void {
    const discovered = this.known === undefined ? new Set<string>()
      : new Set(catalog.counters.map((c) => c.id).filter((id) => !this.known?.has(id)));
    this.known = new Set(catalog.counters.map((c) => c.id));
    this.names = new Map(catalog.counters.map((c) => [c.id, c.name]));
    this.cards = catalog.counters.map((item) => this.card(item, discovered.has(item.id)));
    this.list.replaceChildren(...this.cards.map((c) => c.root));
  }

  render(view: View | undefined): void {
    if (view === undefined) return;
    for (const card of this.cards) {
      const state = cardState(card.item, view, this.orders, this.tickHz());
      const rejection = this.orders.rejection(card.item.id);
      const key = `${state.kind}|${state.kind === 'building' ? Math.ceil(state.secondsLeft) : ''}|${rejection ?? ''}`;
      if (key === card.key) continue;
      card.key = key;
      card.root.dataset['state'] = state.kind;
      card.status.textContent = rejection !== undefined && state.kind !== 'queued' ? `Rejected: ${rejection}` : this.describe(state);
      card.button.disabled = state.kind !== 'available';
      card.button.hidden = state.kind === 'installed' || state.kind === 'building';
    }
  }

  private describe(state: CardState): string {
    switch (state.kind) {
      case 'installed': return 'Online';
      case 'building': return `Building… ${Math.ceil(state.secondsLeft)} s`;
      case 'queued': return 'Queued: waiting for the station';
      case 'locked': return `Needs ${state.needs.map((id) => this.names.get(id) ?? id).join(', ')}`;
      case 'unaffordable': return 'Not enough credits';
      case 'available': return '';
    }
  }

  private card(item: CatalogItem, isNew: boolean): Card {
    const root = document.createElement('article');
    root.className = 'card';
    const title = document.createElement('h3');
    title.textContent = item.name;
    if (isNew) {
      const badge = document.createElement('span');
      badge.className = 'badge';
      badge.textContent = 'new';
      badge.title = 'Discovered through diagnosis';
      title.append(' ', badge);
    }
    const tech = paragraph('tech', item.realTech);
    const cost = paragraph('cost', `${item.credits} cr · upkeep ${item.upkeepPerWave}/wave · ${item.applyTimeS} s to build`);
    const tradeoff = paragraph('tradeoff', item.tradeoff);
    root.append(title, tech, cost, tradeoff);

    let option: () => string | undefined = () => undefined;
    if (item.options.length > 0) {
      const group = document.createElement('fieldset');
      const legend = document.createElement('legend');
      legend.textContent = 'How';
      group.append(legend);
      item.options.forEach((o, i) => {
        const label = document.createElement('label');
        const radio = document.createElement('input');
        radio.type = 'radio';
        radio.name = `option-${item.id}`;
        radio.value = o.id;
        radio.checked = i === 0;
        label.title = o.tradeoff;
        label.append(radio, ` ${o.name}${o.applyTimeS === null ? '' : ` (${o.applyTimeS} s)`}`);
        group.append(label);
      });
      root.append(group);
      option = () => group.querySelector<HTMLInputElement>('input:checked')?.value;
    }

    const status = paragraph('status', '');
    status.setAttribute('aria-live', 'polite');
    const button = document.createElement('button');
    button.type = 'button';
    button.textContent = `Install (${item.credits} cr)`;
    button.onclick = () => this.buy(item, option());
    root.append(status, button);
    return { item, root, status, button, option, key: '' };
  }
}

function paragraph(className: string, content: string): HTMLElement {
  const p = document.createElement('p');
  p.className = className;
  p.textContent = content;
  return p;
}
