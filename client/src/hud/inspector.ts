import { alertStatus, describeRule, nodeOfMetric } from '../game/alerts';
import { sparkline, type LoadHistory } from '../game/history';
import { nodeById, type Reading, type StationNode } from '../game/station';
import type { View } from '../game/store';

const SPARK_W = 260;
const SPARK_H = 40;
const SVG = 'http://www.w3.org/2000/svg';

/**
 * The node inspector (slice 8a, decisions AN/AQ): the USE method for the picked node, its last minute of
 * load, and the alert rules that watch it. A reading the player cannot measure says which monitoring
 * module would show it; one that no exporter provides says so, which is a lesson of its own.
 */
export class Inspector {
  private readonly root = document.createElement('section');
  private readonly title = document.createElement('h2');
  private readonly capacity = document.createElement('p');
  private readonly rows = document.createElement('dl');
  private readonly spark = document.createElementNS(SVG, 'svg');
  private readonly alerts = document.createElement('ul');
  private readonly alertOn = document.createElement('button');
  private node: StationNode | undefined;
  private key = '';

  constructor(host: HTMLElement, private readonly history: LoadHistory, private readonly names: () => Map<string, string>,
    private readonly prefillAlert: (metric: string) => void, private readonly close: () => void) {
    this.root.className = 'inspector';
    this.root.hidden = true;
    this.root.setAttribute('aria-live', 'polite');
    const head = document.createElement('div');
    head.className = 'head';
    const x = document.createElement('button');
    x.type = 'button';
    x.textContent = 'Close';
    x.onclick = () => this.close();
    head.append(this.title, x);
    this.capacity.className = 'capacity';
    this.spark.setAttribute('viewBox', `0 0 ${SPARK_W} ${SPARK_H}`);
    this.spark.setAttribute('role', 'img');
    this.spark.classList.add('spark');
    this.alerts.className = 'node-alerts';
    this.alertOn.type = 'button';
    this.root.append(head, this.capacity, this.rows, this.spark, this.alerts, this.alertOn);
    host.prepend(this.root);
  }

  show(nodeId: string | null): void {
    this.node = nodeId === null ? undefined : nodeById(nodeId);
    this.root.hidden = this.node === undefined;
    this.key = '';
    if (this.node !== undefined) this.root.scrollIntoView({ block: 'nearest' });
  }

  render(view: View | undefined): void {
    const node = this.node;
    if (node === undefined || view === undefined) return;
    const readings: [string, Reading | null][] =
      [['Utilization', node.use.utilization], ['Saturation', node.use.saturation], ['Errors', node.use.errors]];
    const texts = readings.map(([, r]) => this.text(r, view));
    const myAlerts = view.alerts.filter((r) => nodeOfMetric(r.metric) === node.id);
    const key = `${node.id}|${texts.join('|')}|${myAlerts.map((r) => `${r.id}:${alertStatus(r)}`).join(',')}|${view.tick}`;
    if (key === this.key) return;
    this.key = key;

    this.title.textContent = node.label;
    this.capacity.textContent = node.capacity(view.station);
    this.rows.replaceChildren(...readings.flatMap(([label], i) => {
      const dt = document.createElement('dt');
      dt.textContent = label;
      const dd = document.createElement('dd');
      dd.textContent = texts[i] ?? '';
      return [dt, dd];
    }));

    const lines = sparkline(this.history.of(node.id), SPARK_W, SPARK_H);
    this.spark.setAttribute('aria-label', `${node.label} load over the last minute`);
    this.spark.replaceChildren(...lines.map((points) => {
      const line = document.createElementNS(SVG, 'polyline');
      line.setAttribute('points', points);
      return line;
    }));

    this.alerts.replaceChildren(...myAlerts.map((r) => {
      const li = document.createElement('li');
      li.dataset['status'] = alertStatus(r);
      li.textContent = `${alertStatus(r)} · ${describeRule(r)}`;
      return li;
    }));

    const metric = node.use.utilization?.metric;
    const measurable = metric !== undefined && view.metrics[metric] !== undefined;
    this.alertOn.hidden = !measurable;
    if (metric !== undefined) {
      this.alertOn.textContent = `Alert on ${metric}`;
      this.alertOn.onclick = () => this.prefillAlert(metric);
    }
  }

  private text(reading: Reading | null, view: View): string {
    if (reading === null) return 'not exported';
    const value = view.metrics[reading.metric];
    if (value !== undefined) return reading.format(value, view.station);
    const module = this.names().get(this.revealer(reading.metric)) ?? 'monitoring';
    return `? needs ${module}`;
  }

  /** Panels (latency, errors, slow queries) need the dashboard on top of the exporter. */
  private revealer(metric: string): string {
    if (['latency_p99', 'latency_avg', 'error_rate', 'slow_queries', 'disk_forecast'].includes(metric)) return 'grafana';
    return this.node?.reveals ?? 'prometheus';
  }
}
