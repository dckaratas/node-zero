import { Application, Container, Graphics, Text } from 'pixi.js';
import type { View } from '../game/store';
import { LINKS, loadColor, NODES, type StationNode } from '../game/station';
import { hexCorners, hexToPixel, type Point } from './hex';

/** Grid spacing; hexes are drawn smaller so the pipes between them show. */
const HEX_SIZE = 72;
const DRAWN_SIZE = 54;

interface NodeSprite {
  node: StationNode;
  centre: Point;
  hex: Graphics;
  value: Text;
}

/**
 * The station drawn with PixiJS (slice 5 decision Q: canvas for the map, DOM for text-heavy UI).
 * Hexes are redrawn each frame from the interpolated view; with seven nodes that is cheaper than
 * diffing. Request flow is shown as dots moving along the pipes, faster with more traffic, and only
 * when the request rate is measurable.
 */
export class StationMap {
  private readonly app = new Application();
  private readonly world = new Container();
  private readonly pipes = new Graphics();
  private readonly flow = new Graphics();
  private readonly sprites: NodeSprite[] = [];
  private flowPhase = 0;

  async mount(host: HTMLElement): Promise<void> {
    await this.app.init({ resizeTo: host, background: 0x0b1020, antialias: true, autoDensity: true,
      resolution: window.devicePixelRatio });
    host.appendChild(this.app.canvas);
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
      this.world.addChild(hex, label, value);
      this.sprites.push({ node, centre, hex, value });
    }
    this.drawPipes();
    this.centre();
    this.app.renderer.on('resize', () => this.centre());
    // resizeTo only listens to window resizes; the host also changes when a scrollbar appears.
    new ResizeObserver(() => this.app.resize()).observe(host);
  }

  /** Called every frame with the interpolated view. */
  render(view: View | undefined, deltaMs: number): void {
    const metrics = view?.metrics ?? {};
    for (const s of this.sprites) {
      const load = s.node.load(metrics);
      s.hex.clear()
        .poly(hexCorners(s.centre, DRAWN_SIZE)).fill({ color: loadColor(load) })
        .stroke({ width: 2, color: 0x8fa3d9, alpha: 0.6 });
      s.value.text = load === undefined ? (s.node.id === 'gateway' ? '' : '?') : `${Math.round(load * 100)}%`;
    }
    this.drawFlow(metrics['request_rate'], deltaMs);
  }

  private drawPipes(): void {
    for (const link of LINKS) {
      const a = this.centreOf(link.from);
      const b = this.centreOf(link.to);
      this.pipes.moveTo(a.x, a.y).lineTo(b.x, b.y);
    }
    this.pipes.stroke({ width: 6, color: 0x26304a });
  }

  private drawFlow(requestRate: number | undefined, deltaMs: number): void {
    this.flow.clear();
    if (requestRate === undefined || requestRate <= 0) return;
    // Pipe crossings per second grow with traffic but stay readable (log scale).
    this.flowPhase = (this.flowPhase + (deltaMs / 1000) * 0.25 * Math.log10(1 + requestRate)) % 1;
    for (const link of LINKS) {
      const a = this.centreOf(link.from);
      const b = this.centreOf(link.to);
      for (let i = 0; i < 3; i++) {
        const t = (this.flowPhase + i / 3) % 1;
        this.flow.circle(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, 3);
      }
    }
    this.flow.fill({ color: 0x7fd4ff });
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
  }

  get ticker() {
    return this.app.ticker;
  }
}
