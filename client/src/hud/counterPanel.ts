import { cardState, type CardState, type Orders } from '../game/orders';
import type { View } from '../game/store';
import type { Catalog, CatalogItem, ClientAction } from '../protocol/messages';

interface Card {
  item: CatalogItem;
  root: HTMLElement;
  status: HTMLElement;
  button: HTMLButtonElement;
  option: () => string | undefined;
  remove: HTMLButtonElement;
  setting: HTMLInputElement | null;
  settingRow: HTMLElement | null;
  /** Last rendered state, so a frame only touches the DOM when something changed. */
  key: string;
}

/**
 * Counter cards (slice 5b). Counters gated by an insight are not in the catalog until the player has
 * it (decision W, filtered on the server); when one appears after the first load it is marked as
 * newly discovered. Buying is optimistic (decision Y, see {@link Orders}). An installed counter can be
 * removed (two clicks: there is no refund) and its driving setting changed (slice 7c).
 */
export class CounterPanel {
  private readonly list = document.createElement('div');
  private cards: Card[] = [];
  private known: Set<string> | undefined;
  private names = new Map<string, string>();

  constructor(host: HTMLElement, private readonly orders: Orders, private readonly buy: (item: CatalogItem, option?: string) => void,
    private readonly tickHz: () => number, private readonly send: (action: ClientAction) => boolean) {
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
      const detail = state.kind === 'building' || state.kind === 'removing' ? Math.ceil(state.secondsLeft)
        : state.kind === 'installed' ? state.setting : '';
      const key = `${state.kind}|${detail ?? ''}|${rejection ?? ''}`;
      if (key === card.key) continue;
      card.key = key;
      card.root.dataset['state'] = state.kind;
      card.status.textContent = rejection !== undefined && state.kind !== 'queued' ? `Rejected: ${rejection}` : this.describe(state);
      card.button.disabled = state.kind !== 'available';
      card.button.hidden = state.kind === 'installed' || state.kind === 'building' || state.kind === 'removing';
      card.remove.hidden = state.kind !== 'installed' || !card.item.removable;
      card.remove.textContent = 'Remove';
      delete card.remove.dataset['armed'];
      if (card.settingRow !== null && card.setting !== null) {
        card.settingRow.hidden = state.kind !== 'installed';
        if (state.kind === 'installed' && state.setting !== null && document.activeElement !== card.setting) {
          card.setting.value = String(state.setting);
        }
      }
    }
  }

  private describe(state: CardState): string {
    switch (state.kind) {
      case 'installed': return 'Online';
      case 'building': return `Building… ${Math.ceil(state.secondsLeft)} s`;
      case 'removing': return `Removing… ${Math.ceil(state.secondsLeft)} s`;
      case 'queued': return 'Queued: waiting for the station';
      case 'locked': return `Needs ${state.needs.map((id) => this.names.get(id) ?? id).join(', ')}`;
      case 'unaffordable': return 'Not enough credits';
      case 'no-crew': return 'Crew too tired: wait for the next shift';
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
    const crew = item.toil > 0 ? ` · crew ${item.toil}` : '';
    const cost = paragraph('cost',
      `${item.credits} cr · upkeep ${item.upkeepPerWave}/wave · ${item.applyTimeS} s to build${crew}`);
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

    // Removing has no refund and takes as long as building: ask once more before sending.
    const remove = document.createElement('button');
    remove.type = 'button';
    remove.className = 'remove';
    remove.hidden = true;
    remove.textContent = 'Remove';
    remove.onclick = () => {
      if (remove.dataset['armed'] === undefined) {
        remove.dataset['armed'] = 'true';
        remove.textContent = 'Confirm remove (no refund)';
        return;
      }
      this.send({ type: 'remove_counter', counter: item.id });
    };

    let setting: HTMLInputElement | null = null;
    let settingRow: HTMLElement | null = null;
    if (item.setting !== null) {
      const s = item.setting;
      settingRow = document.createElement('div');
      settingRow.className = 'setting';
      settingRow.hidden = true;
      const label = document.createElement('label');
      const input = document.createElement('input');
      input.type = 'number';
      input.min = String(s.min);
      input.max = String(s.max);
      input.value = String(s.value);
      label.append(`${s.key} `, input);
      const apply = document.createElement('button');
      apply.type = 'button';
      apply.textContent = 'Apply (1 crew)';
      apply.onclick = () => {
        const value = Number(input.value);
        if (Number.isFinite(value)) this.send({ type: 'configure_counter', counter: item.id, value });
      };
      settingRow.append(label, apply);
      setting = input;
    }

    root.append(status, button, ...(settingRow === null ? [] : [settingRow]), remove);
    return { item, root, status, button, option, remove, setting, settingRow, key: '' };
  }
}

function paragraph(className: string, content: string): HTMLElement {
  const p = document.createElement('p');
  p.className = className;
  p.textContent = content;
  return p;
}
