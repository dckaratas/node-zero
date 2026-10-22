import { describeRule, firingKey, unacknowledged } from '../game/alerts';
import type { AlertRule } from '../protocol/messages';

/**
 * The page that wakes the on-call (slice 7d, decision AJ): a red banner under the header while any rule
 * fires unacknowledged, and a red dot in the tab title so a player in another tab notices too. The game
 * never pauses for it; Ack only quiets this firing.
 */
export class AlertBanner {
  private readonly banner = document.createElement('div');
  private readonly acked = new Set<string>();
  private readonly title = document.title;
  private key = '';

  constructor() {
    this.banner.className = 'alert-banner';
    this.banner.setAttribute('role', 'alert');
    this.banner.hidden = true;
    // In the page flow under the header, so it never covers the shield or the wave controls.
    const header = document.getElementById('hud');
    if (header === null) document.body.prepend(this.banner);
    else header.after(this.banner);
  }

  render(rules: readonly AlertRule[]): void {
    const loud = unacknowledged(rules, this.acked);
    const key = loud.map(firingKey).join('|');
    if (key === this.key) return;
    this.key = key;
    this.banner.hidden = loud.length === 0;
    document.title = loud.length === 0 ? this.title : `🔴 ${this.title}`;
    this.banner.replaceChildren(...loud.map((r) => {
      const row = document.createElement('div');
      const text = document.createElement('strong');
      text.textContent = `FIRING  ${describeRule(r)}`;
      const ack = document.createElement('button');
      ack.type = 'button';
      ack.textContent = 'Ack';
      ack.onclick = () => {
        this.acked.add(firingKey(r));
        this.render(rules);
      };
      row.append(text, ack);
      return row;
    }));
  }

  /** New session: old acks do not carry over. */
  reset(): void {
    this.acked.clear();
    this.key = '';
  }
}
