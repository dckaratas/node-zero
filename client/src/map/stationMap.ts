import { Application, Container, Graphics, Text } from 'pixi.js';
import type { View } from '../game/store';
import { queueDots, redDots, type NodeMarks } from '../game/mapFeedback';
import { LINKS, loadColor, NODE01, NODES, type StationNode } from '../game/station';
import { hexCorners, hexToPixel, type Point } from './hex';

/** Grid spacing; hexes are drawn smaller so the pipes between them show. */
const HEX_SIZE = 72;
const DRAWN_SIZE = 54;
const FLOW_DOTS = 6;
const DOWN_COLOR = 0x2a2f3d;

interface NodeSprite {
  node: StationNode;
  centre: Point;
  hex: Graphics;
  value: Text;
  /** Badge, progress ring and queue stack (slice 8b). */
  marks: Graphics;
  badge: Text;
}

/**
 * The station drawn with PixiJS (slice 5 decision Q: canvas for the map, DOM for text-heavy UI).
 * Hexes are redrawn each frame from the interpolated view; with seven nodes that is cheaper than
 * diffing. Request flow is shown as dots moving along the pipes, faster with more traffic, and only
 * when the request rate is measurable; a share of them turns red with the measured error rate (slice 8b).
 */
export class StationMap {
  private readonly app = new Application();
  private readonly world = new Container();
  private readonly pipes = new Graphics();
  private readonly flow = new Graphics();
  private readonly sprites: NodeSprite[] = [];
  private flowPhase = 0;
  private alertPhase = 0;
  /** Invisible DOM buttons over the hexes (decision AS): mouse, touch, keyboard and screen readers alike. */
  private readonly buttons = new Map<string, HTMLButtonElement>();
  private selected: string | null = null;
  private onSelect: (id: string | null) => void = () => undefined;

  async mount(host: HTMLElement): Promise<void> {
    await this.app.init({ resizeTo: host, background: 0x0b1020, antialias: true, autoDensity: true,
      resolution: window.devicePixelRatio });
    host.appendChild(this.app.canvas);
    const overlay = document.createElement('div');
    overlay.className = 'map-nodes';
    host.appendChild(overlay);
    this.app.stage.addChild(this.world);
    this.world.addChild(this.pipes, this.flow);

    for (const node of NODES) {
      const centre = hexToPixel(node.hex, HEX_SIZE);
      const hex = new Graphics();
      const label = new Text({ text: node.label, style: { fill: 0xe6ecff, fontSize: 14, fontFamily: 'system-ui' } });
      const value = new Text({ text: '?', style: { fill: 0xe6ecff, fontSize: 18, fontWeight: 'bold', fontFamily: 'system-ui' } });
      label.anchor.set(0.5);
      value.anchor.set(0.5);
      label.position.set(centre.x, centre.y - 12);
      value.position.set(centre.x, centre.y + 12);
      const marks = new Graphics();
      const badge = new Text({ text: '', style: { fill: 0xffffff, fontSize: 12, fontWeight: 'bold', fontFamily: 'system-ui' } });
      badge.anchor.set(0.5);
      badge.position.set(centre.x + DRAWN_SIZE * 0.62, centre.y - DRAWN_SIZE * 0.72);
      this.world.addChild(hex, label, value, marks, badge);
      this.sprites.push({ node, centre, hex, value, marks, badge });
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'map-node';
      button.textContent = node.label;
      button.setAttribute('aria-pressed', 'false');
      button.onclick = () => this.select(this.selected === node.id ? null : node.id);
      overlay.append(button);
      this.buttons.set(node.id, button);
    }
    this.drawPipes();
    this.centre();
    this.app.renderer.on('resize', () => this.centre());
    // resizeTo only listens to window resizes; the host also changes when a scrollbar appears.
    new ResizeObserver(() => this.app.resize()).observe(host);
  }

  /**
   * Called every frame with the interpolated view.
   * @param alerting nodes with a firing alert rule (slice 7d): their outline pulses red
   * @param marks badges, progress rings and DOWN per node (slice 8b)
   */
  render(view: View | undefined, deltaMs: number, alerting: ReadonlySet<string> = new Set(),
    marks: ReadonlyMap<string, NodeMarks> = new Map()): void {
    const metrics = view?.metrics ?? {};
    this.alertPhase = (this.alertPhase + deltaMs / 600) % (2 * Math.PI);
    const pulse = 0.55 + 0.45 * Math.sin(this.alertPhase);
    for (const s of this.sprites) {
      const load = s.node.load(metrics, view?.station ?? NODE01);
      const outline = alerting.has(s.node.id)
        ? { width: 5, color: 0xff4d4f, alpha: pulse }
        : s.node.id === this.selected
          ? { width: 4, color: 0xffffff, alpha: 0.95 }
          : { width: 2, color: 0x8fa3d9, alpha: 0.6 };
      const m = marks.get(s.node.id);
      const down = m?.down === true;
      s.hex.clear()
        .poly(hexCorners(s.centre, DRAWN_SIZE)).fill({ color: down ? DOWN_COLOR : loadColor(load) })
        .stroke(outline);
      s.value.text = down ? 'DOWN'
        : load === undefined ? (s.node.id === 'gateway' ? '' : '?') : `${Math.round(load * 100)}%`;
      this.drawMarks(s, m, s.node.id === 'db_pool' ? queueDots(metrics['hikaricp_connections_pending']) : 0);
    }
    this.drawFlow(metrics['request_rate'], metrics['error_rate'], deltaMs);
  }

  /** The player picked a node (or {@code null} to close the inspector). */
  onNodeSelected(listener: (id: string | null) => void): void {
    this.onSelect = listener;
  }

  select(id: string | null): void {
    this.selected = id;
    for (const [nodeId, button] of this.buttons) button.setAttribute('aria-pressed', String(nodeId === id));
    this.onSelect(id);
  }

  private drawPipes(): void {
    for (const link of LINKS) {
      const a = this.centreOf(link.from);
      const b = this.centreOf(link.to);
      this.pipes.moveTo(a.x, a.y).lineTo(b.x, b.y);
    }
    this.pipes.stroke({ width: 6, color: 0x26304a });
  }

  private drawMarks(s: NodeSprite, m: NodeMarks | undefined, queued: number): void {
    s.marks.clear();
    const { x, y } = s.centre;
    if (m?.progress != null) {
      // A ring around the hex fills as the build (cyan) or the removal (orange) goes on.
      const r = DRAWN_SIZE + 6;
      const start = -Math.PI / 2;
      s.marks.circle(x, y, r).stroke({ width: 4, color: 0x26304a });
      s.marks.moveTo(x, y - r).arc(x, y, r, start, start + 2 * Math.PI * m.progress.ratio)
        .stroke({ width: 4, color: m.progress.removing ? 0xff9f43 : 0x7fd4ff });
    }
    const modules = m?.modules ?? 0;
    s.badge.text = modules > 0 ? String(modules) : '';
    if (modules > 0) {
      s.marks.circle(s.badge.x, s.badge.y, 10).fill({ color: 0x5b6cff }).stroke({ width: 2, color: 0x0b1020 });
    }
    // Requests waiting for a connection pile up under the pool.
    for (let i = 0; i < queued; i++) s.marks.rect(x - DRAWN_SIZE * 0.5 + i * 7, y + DRAWN_SIZE * 0.55, 5, 8);
    if (queued > 0) s.marks.fill({ color: 0xffd166 });
  }

  private drawFlow(requestRate: number | undefined, errorRate: number | undefined, deltaMs: number): void {
    this.flow.clear();
    if (requestRate === undefined || requestRate <= 0) return;
    // Pipe crossings per second grow with traffic but stay readable (log scale).
    this.flowPhase = (this.flowPhase + (deltaMs / 1000) * 0.25 * Math.log10(1 + requestRate)) % 1;
    const red = redDots(errorRate, FLOW_DOTS);
    for (const link of LINKS) {
      const a = this.centreOf(link.from);
      const b = this.centreOf(link.to);
      for (let i = 0; i < FLOW_DOTS; i++) {
        const t = (this.flowPhase + i / FLOW_DOTS) % 1;
        // Spread the red dots along the pipe instead of sending them as one train.
        const failed = (i * red) % FLOW_DOTS < red;
        this.flow.circle(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, 3).fill({ color: failed ? 0xff4d4f : 0x7fd4ff });
      }
    }
  }

  private centreOf(id: string): Point {
    const s = this.sprites.find((x) => x.node.id === id);
    if (s === undefined) throw new Error(`unknown node ${id}`);
    return s.centre;
  }

  private centre(): void {
    // Fit the station into the canvas with a margin, never enlarging past 1:1.
    const bounds = this.world.getLocalBounds();
    const { width, height } = this.app.screen;
    const scale = Math.min(1, (width * 0.9) / bounds.width, (height * 0.9) / bounds.height);
    this.world.scale.set(scale);
    this.world.position.set(
      width / 2 - (bounds.x + bounds.width / 2) * scale,
      height / 2 - (bounds.y + bounds.height / 2) * scale);
    // Keep the DOM buttons over their hexes.
    const size = DRAWN_SIZE * 1.6 * scale;
    for (const s of this.sprites) {
      const b = this.buttons.get(s.node.id);
      if (b === undefined) continue;
      b.style.width = `${size}px`;
      b.style.height = `${size}px`;
      b.style.left = `${this.world.position.x + s.centre.x * scale - size / 2}px`;
      b.style.top = `${this.world.position.y + s.centre.y * scale - size / 2}px`;
    }
  }

  get ticker() {
    return this.app.ticker;
  }
}
