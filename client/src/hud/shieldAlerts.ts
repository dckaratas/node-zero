import type { ShieldAlert } from '../game/shield';

/** How long the full-screen collapse notice stays up before the postmortem may open. */
const COLLAPSE_MS = 3500;
const TOAST_MS = 3000;

/**
 * Loud shield notices (slice 7a, decision AD). Thresholds raise a toast under the header and flash the
 * shield; a broken shield covers the screen for a moment, because the wave has just failed. Nothing
 * pauses the game; the notices only make sure the player cannot miss what happened.
 */
export class ShieldAlerts {
  private readonly toast = document.createElement('div');
  private readonly overlay = document.createElement('div');
  private collapseUntil = 0;
  private toastTimer: ReturnType<typeof setTimeout> | undefined;
  private overlayTimer: ReturnType<typeof setTimeout> | undefined;

  constructor(private readonly shield: () => HTMLElement | null) {
    this.toast.className = 'shield-toast';
    this.toast.setAttribute('role', 'status');
    this.toast.hidden = true;
    this.overlay.className = 'shield-collapse';
    this.overlay.setAttribute('role', 'alert');
    this.overlay.hidden = true;
    this.overlay.onclick = () => this.hideOverlay();
    document.body.append(this.toast, this.overlay);
  }

  show(alert: ShieldAlert, wave: number, nowMs: number): void {
    this.flash();
    if (alert === 'collapse') {
      this.overlay.replaceChildren(text('strong', 'SHIELD DOWN'),
        text('p', `Wave ${wave} failed: the error budget is spent.`),
        text('p', 'Replay the wave when the station is ready.'));
      this.overlay.hidden = false;
      this.collapseUntil = nowMs + COLLAPSE_MS;
      clearTimeout(this.overlayTimer);
      this.overlayTimer = setTimeout(() => this.hideOverlay(), COLLAPSE_MS);
      return;
    }
    this.toast.dataset['level'] = alert;
    this.toast.textContent = alert === 'warn' ? 'Shield below 50%' : 'Shield below 25%: act now';
    this.toast.hidden = false;
    clearTimeout(this.toastTimer);
    this.toastTimer = setTimeout(() => { this.toast.hidden = true; }, TOAST_MS);
  }

  /** True while the collapse notice is up; the postmortem waits for it. */
  blocking(nowMs: number): boolean {
    return !this.overlay.hidden && nowMs < this.collapseUntil;
  }

  private hideOverlay(): void {
    this.overlay.hidden = true;
    this.collapseUntil = 0;
  }

  private flash(): void {
    const shield = this.shield();
    if (shield === null) return;
    shield.classList.remove('flash');
    void shield.offsetWidth; // restart the animation
    shield.classList.add('flash');
  }
}

function text(tag: string, content: string): HTMLElement {
  const e = document.createElement(tag);
  e.textContent = content;
  return e;
}
