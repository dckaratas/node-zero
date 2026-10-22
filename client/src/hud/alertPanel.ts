import { alertStatus, describeRule } from '../game/alerts';
import type { View } from '../game/store';
import type { AlertOp, ClientAction } from '../protocol/messages';

/**
 * Alert rules (slice 7d, ADR-0018 AJ): a list with each rule's state and a small form. Only metrics the
 * player can currently measure are offered, because the server refuses the rest (an alert can only watch
 * what a dashboard could show).
 */
export class AlertPanel {
  private readonly list = document.createElement('ul');
  private readonly form = document.createElement('form');
  private readonly metric = document.createElement('select');
  private readonly op = document.createElement('select');
  private readonly threshold = document.createElement('input');
  private readonly forS = document.createElement('input');
  private readonly hint = document.createElement('p');
  private listKey = '';
  private metricsKey: string | undefined;

  constructor(host: HTMLElement, private readonly send: (action: ClientAction) => boolean) {
    this.list.className = 'alert-rules';
    this.form.className = 'alert-form';
    this.metric.setAttribute('aria-label', 'Metric');
    this.op.setAttribute('aria-label', 'Condition');
    this.op.append(new Option('>', 'ABOVE'), new Option('<', 'BELOW'));
    this.threshold.type = 'number';
    this.threshold.step = 'any';
    this.threshold.required = true;
    this.threshold.setAttribute('aria-label', 'Threshold');
    this.threshold.placeholder = 'threshold';
    this.forS.type = 'number';
    this.forS.min = '0';
    this.forS.max = '600';
    this.forS.value = '10';
    this.forS.setAttribute('aria-label', 'For seconds');
    const add = document.createElement('button');
    add.type = 'submit';
    add.textContent = 'Add rule';
    const forLabel = document.createElement('label');
    forLabel.append('for ', this.forS, ' s');
    this.form.append(this.metric, this.op, this.threshold, forLabel, add);
    this.form.onsubmit = (e) => {
      e.preventDefault();
      const threshold = Number(this.threshold.value);
      const forS = Number(this.forS.value);
      if (!this.metric.value || !Number.isFinite(threshold) || !Number.isFinite(forS)) return;
      this.send({ type: 'set_alert', metric: this.metric.value, op: this.op.value as AlertOp, threshold, forS });
    };
    this.hint.className = 'hint';
    this.hint.textContent = 'Install Prometheus to alert on metrics.';
    host.append(this.list, this.form, this.hint);
  }

  /** "Alert on this" from the inspector: pick the metric and put the cursor in the threshold. */
  prefill(metric: string): void {
    this.metric.value = metric;
    this.form.scrollIntoView({ block: 'nearest' });
    this.threshold.focus();
  }

  render(view: View | undefined): void {
    if (view === undefined) return;
    const metrics = Object.keys(view.metrics);
    const metricsKey = metrics.join('|');
    if (metricsKey !== this.metricsKey) {
      this.metricsKey = metricsKey;
      const selected = this.metric.value;
      this.metric.replaceChildren(...metrics.map((m) => new Option(m, m, false, m === selected)));
      this.form.hidden = metrics.length === 0;
      this.hint.hidden = metrics.length > 0;
    }

    const key = view.alerts.map((r) => `${r.id}:${alertStatus(r)}`).join('|');
    if (key === this.listKey) return;
    this.listKey = key;
    this.list.replaceChildren(...view.alerts.map((r) => {
      const li = document.createElement('li');
      const status = alertStatus(r);
      li.dataset['status'] = status;
      const state = document.createElement('span');
      state.className = 'state';
      state.textContent = status;
      const text = document.createElement('code');
      text.textContent = describeRule(r);
      const remove = document.createElement('button');
      remove.type = 'button';
      remove.textContent = 'Delete';
      remove.setAttribute('aria-label', `Delete rule ${describeRule(r)}`);
      remove.onclick = () => this.send({ type: 'remove_alert', rule: r.id });
      li.append(state, ' ', text, ' ', remove);
      return li;
    }));
  }
}
