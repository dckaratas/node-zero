import { LANGUAGES, type ContentLanguage } from '../game/language';
import { burnText, shieldLevel } from '../game/shield';
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
  private readonly shieldLabel = el('span', 'shield-label');
  private readonly burn = el('span', 'burn');
  private readonly crew = el('span', 'crew');
  private readonly status = el('span', 'status');
  private readonly notice = el('div', 'notice');
  private readonly startWave = button('Start wave');
  private readonly pause = button('Pause');
  private readonly language = document.createElement('select');
  private readonly metrics = el('dl', 'metrics');
  private readonly signals = el('ul', 'signals');
  /** Where the counter cards go (CounterPanel). */
  readonly countersHost = el('section', 'counters');
  /** Where the alert rules go (AlertPanel). */
  readonly alertsHost = el('section', 'alerts-host');
  /** Where the incident action cards go (ActionPanel). */
  readonly actionsHost = el('section', 'actions-host');
  /** Where the codex goes (CodexPanel); it brings its own heading. */
  readonly codexHost = el('section', 'codex-host');
  private paused = false;
  private nextWave = 1;
  private crewMax = 0;

  constructor(top: HTMLElement, panel: HTMLElement, metricsHost: HTMLElement, send: (action: ClientAction) => void) {
    this.shield.setAttribute('role', 'meter');
    this.shield.setAttribute('aria-label', 'Error budget');
    this.shield.setAttribute('aria-valuemin', '0');
    this.shield.setAttribute('aria-valuemax', '100');
    this.shield.append(this.shieldBar, this.shieldLabel);
    this.crew.title = 'On-call crew left this shift: incident actions and some modules cost crew; a finished wave brings a rested shift.';
    this.language.className = 'language';
    this.language.setAttribute('aria-label', 'Content language');
    this.language.title = 'Language of threats, modules, postmortems and the codex';
    this.language.append(...LANGUAGES.map((l) => new Option(l.label, l.code)));
    top.append(this.wave, this.phase, this.clock, this.shield, this.burn, this.crew, this.credits, this.startWave, this.pause, this.status,
      this.language);
    // Codex above the long module list: closed articles are one line each.
    panel.append(heading('Signals'), this.signals, this.notice, heading('Alerts'), this.alertsHost,
      heading('Incident response'), this.actionsHost, this.codexHost, heading('Modules'), this.countersHost);
    // Metrics float over the map's lower left corner, next to what they measure.
    metricsHost.append(this.metrics);

    this.startWave.onclick = () => send({ type: 'start_wave', wave: this.nextWave });
    this.pause.onclick = () => send(this.paused ? { type: 'resume' } : { type: 'pause' });
  }

  /** Content language picker (slice 6c). The interface itself stays English (ADR-0009). */
  onLanguageChange(listener: (language: ContentLanguage) => void): void {
    this.language.onchange = () => listener(this.language.value === 'tr' ? 'tr' : 'en');
  }

  /** Shows the choice and tags content areas so screen readers pronounce them in the right language. */
  setContentLanguage(language: ContentLanguage): void {
    this.language.value = language;
    this.countersHost.lang = language;
    this.actionsHost.lang = language;
    this.codexHost.lang = language;
  }

  /** @param projectedCredits credits after queued orders, when any are waiting */
  render(view: View | undefined, projectedCredits?: number): void {
    if (view === undefined) return;
    this.wave.textContent = `Wave ${view.wave}`;
    this.phase.textContent = view.phase;
    this.phase.dataset['phase'] = view.phase;
    // 1 real second = 1 game minute; tick = 0.1 s.
    const minutes = Math.floor(view.tick / 10);
    this.clock.textContent = `T+${Math.floor(minutes / 60)}h${String(minutes % 60).padStart(2, '0')}`;
    this.credits.textContent = projectedCredits === undefined || projectedCredits === view.credits
      ? `${view.credits} cr` : `${view.credits} → ${projectedCredits} cr (queued)`;
    const shield = Math.round(view.shield * 100);
    this.shieldBar.style.width = `${shield}%`;
    this.shield.setAttribute('aria-valuenow', String(shield));
    this.shield.title = `Error budget ${shield}%`;
    this.shield.dataset['level'] = shieldLevel(view.shield);
    this.shieldLabel.textContent = `Shield ${shield}%`;
    this.burn.textContent = view.phase === 'RUNNING' ? burnText(view.burnRate, view.shieldEmptyInS) : '';

    // A failed wave is retried; otherwise the next one.
    this.nextWave = view.phase === 'FAILED' ? Math.max(1, view.wave) : view.wave + 1;
    const reviewOpen = view.postmortem !== null && view.postmortem.chosen === null;
    // No game over and no wave after the last (decision AE): a won chapter ends the run.
    const chapter = view.chapter;
    this.startWave.textContent = chapter === null ? `Start wave ${this.nextWave}` : `Chapter ${chapter.number} complete`;
    this.startWave.disabled = view.phase === 'RUNNING' || reviewOpen || chapter !== null;
    this.startWave.title = reviewOpen ? 'Answer the postmortem first' : '';

    this.crew.textContent = this.crewMax > 0 ? `Crew ${view.crew}/${this.crewMax}` : `Crew ${view.crew}`;
    this.crew.dataset['low'] = String(view.crew <= 2);

    this.renderMetrics(view.metrics);
    this.renderSignals(view.signals);
  }

  /** Crew points per shift, from the catalog (slice 7b). */
  setCrewMax(crew: number): void {
    this.crewMax = crew;
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
