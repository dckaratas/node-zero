import { upcoming } from '../game/briefing';
import type { View } from '../game/store';
import type { Briefing } from '../protocol/messages';

const BANNER_MS = 4000;
const GLOW_MS = 4000;

/**
 * The next wave, told the way a change calendar would (slice 9c, decisions CA–CC): a card under the header while
 * preparing, a short banner when the wave starts, and a glow when new comms arrive. Texts come by key in the
 * player's language; the root cause is never named.
 */
export class BriefingCard {
  private readonly card = document.createElement('section');
  private readonly banner = document.createElement('div');
  private key = '';
  private comms = '';
  private bannerUntil = 0;
  private glowUntil = 0;

  constructor(private readonly t: (key: string) => string) {
    this.card.className = 'briefing';
    this.card.setAttribute('aria-live', 'polite');
    this.card.hidden = true;
    this.banner.className = 'wave-banner';
    this.banner.setAttribute('role', 'status');
    this.banner.hidden = true;
    const header = document.getElementById('hud');
    if (header === null) document.body.prepend(this.card, this.banner);
    else header.after(this.card, this.banner);
  }

  /** The wave just started: show what it is about for a few seconds. */
  flash(b: Briefing, nowMs: number): void {
    this.banner.textContent = `Wave ${b.wave} · ${this.t(b.eventKey)}`;
    this.banner.hidden = false;
    this.bannerUntil = nowMs + BANNER_MS;
  }

  /** Texts were reloaded in another language. */
  refresh(): void {
    this.key = '';
  }

  render(view: View | undefined, nowMs: number): void {
    if (!this.banner.hidden && nowMs >= this.bannerUntil) this.banner.hidden = true;
    const b = upcoming(view);
    const comms = b === null ? '' : b.comms.join('|');
    if (comms !== '' && comms !== this.comms) this.glowUntil = nowMs + GLOW_MS;
    this.comms = comms;
    const glowing = nowMs < this.glowUntil;
    const key = b === null ? '' : `${b.wave}|${b.eventKey}|${comms}|${glowing}|${view?.phase}`;
    if (key === this.key) return;
    this.key = key;
    this.card.hidden = b === null;
    if (b === null) return;
    this.card.classList.toggle('fresh', glowing);
    const title = document.createElement('strong');
    title.textContent = view?.phase === 'RUNNING' ? `Wave ${b.wave}` : `Next: wave ${b.wave}`;
    const event = document.createElement('p');
    event.textContent = this.t(b.eventKey);
    const messages = b.comms.map((c) => {
      const p = document.createElement('p');
      p.className = 'comms';
      p.textContent = `📡 ${this.t(c)}`;
      return p;
    });
    this.card.replaceChildren(title, event, ...messages);
  }
}
