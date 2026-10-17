import type { View } from '../game/store';
import type { ConnectionStatus } from '../net/connection';
import type { ClientAction } from '../protocol/messages';

/**
 * Text UI in plain DOM (slice 5 decision Q): screen readers, text selection and, from slice 6, i18n.
 * Elements are built once; each frame only updates text and attributes.
 */
export class Hud {
  private readonly wave = el('span', 'wave');
  private readonly phase = el('span', 'phase');
  private readonly clock = el('span', 'clock');
  private readonly credits = el('span', 'credits');
  private readonly shieldBar = el('div', 'shield-bar');
  private readonly shield = el('div', 'shield');
  private readonly status = el('span', 'status');
  private readonly notice = el('div', 'notice');
  private readonly startWave = button('Start wave');
  private readonly pause = button('Pause');
  private readonly metrics = el('dl', 'metrics');
  private readonly signals = el('ul', 'signals');
  private paused = false;
  private nextWave = 1;

  constructor(top: HTMLElement, panel: HTMLElement, send: (action: ClientAction) => void) {
    this.shield.setAttribute('role', 'meter');
    this.shield.setAttribute('aria-label', 'Error budget');
    this.shield.setAttribute('aria-valuemin', '0');
    this.shield.setAttribute('aria-valuemax', '100');
    this.shield.append(this.shieldBar);
    top.append(this.wave, this.phase, this.clock, this.shield, this.credits, this.startWave, this.pause, this.status);
    panel.append(heading('Signals'), this.signals, heading('Metrics'), this.metrics, this.notice);

    this.startWave.onclick = () => send({ type: 'start_wave', wave: this.nextWave });
    this.pause.onclick = () => send(this.paused ? { type: 'resume' } : { type: 'pause' });
  }

  render(view: View | undefined): void {
    if (view === undefined) return;
    this.wave.textContent = `Wave ${view.wave}`;
    this.phase.textContent = view.phase;
    this.phase.dataset['phase'] = view.phase;
    // 1 real second = 1 game minute; tick = 0.1 s.
    const minutes = Math.floor(view.tick / 10);
    this.clock.textContent = `T+${Math.floor(minutes / 60)}h${String(minutes % 60).padStart(2, '0')}`;
    this.credits.textContent = `${view.credits} cr`;
    const shield = Math.round(view.shield * 100);
    this.shieldBar.style.width = `${shield}%`;
    this.shield.setAttribute('aria-valuenow', String(shield));
    this.shield.title = `Error budget ${shield}%`;

    // A failed wave is retried; otherwise the next one.
    this.nextWave = view.phase === 'FAILED' ? Math.max(1, view.wave) : view.wave + 1;
    this.startWave.textContent = `Start wave ${this.nextWave}`;
    this.startWave.disabled = view.phase === 'RUNNING';

    this.renderMetrics(view.metrics);
    this.renderSignals(view.signals);
  }

  setPaused(paused: boolean): void {
    this.paused = paused;
    this.pause.textContent = paused ? 'Resume' : 'Pause';
    this.pause.setAttribute('aria-pressed', String(paused));
  }

  setStatus(status: ConnectionStatus): void {
    this.status.textContent = status === 'open' ? '● online' : status === 'connecting' ? '○ connecting…' : '○ offline';
    this.status.dataset['status'] = status;
  }

  showNotice(text: string): void {
    this.notice.textContent = text;
  }

  private renderMetrics(metrics: Readonly<Record<string, number>>): void {
    const names = Object.keys(metrics);
    if (names.length === 0) {
      const hint = text('dd', 'No monitoring installed. Buy Prometheus to see inside the station.');
      hint.className = 'hint';
      this.metrics.replaceChildren(hint);
      return;
    }
    if (this.metrics.childElementCount !== names.length * 2) {
      this.metrics.replaceChildren(...names.flatMap((n) => [text('dt', n), text('dd', '')]));
    }
    names.forEach((name, i) => {
      const dd = this.metrics.children[i * 2 + 1];
      if (dd) dd.textContent = format(metrics[name] ?? 0);
    });
  }

  private renderSignals(signals: readonly string[]): void {
    const key = signals.join('|');
    if (this.signals.dataset['key'] === key) return;
    this.signals.dataset['key'] = key;
    this.signals.replaceChildren(...(signals.length ? signals.map((s) => text('li', s)) : [text('li', 'none')]));
  }
}

function format(v: number): string {
  if (Math.abs(v) >= 100) return v.toFixed(0);
  if (Math.abs(v) >= 1) return v.toFixed(1);
  return v.toFixed(3);
}

function el(tag: string, className: string): HTMLElement {
  const e = document.createElement(tag);
  e.className = className;
  return e;
}

function text(tag: string, content: string): HTMLElement {
  const e = document.createElement(tag);
  e.textContent = content;
  return e;
}

function heading(content: string): HTMLElement {
  return text('h2', content);
}

function button(label: string): HTMLButtonElement {
  const b = document.createElement('button');
  b.type = 'button';
  b.textContent = label;
  return b;
}
