import { NODES } from './station';
import type { View } from './store';

/**
 * The last minute of each node's load, kept from every snapshot so the inspector can draw a trend the
 * moment a node is picked (slice 8a, decision AN). NaN marks a moment the player could not measure it.
 * Client-side only: the server already sent these values once.
 */
export class LoadHistory {
  private readonly series = new Map<string, number[]>();

  constructor(private readonly points: number) {}

  record(view: View): void {
    for (const node of NODES) {
      const values = this.series.get(node.id) ?? [];
      values.push(node.load(view.metrics, view.station) ?? Number.NaN);
      if (values.length > this.points) values.splice(0, values.length - this.points);
      this.series.set(node.id, values);
    }
  }

  of(nodeId: string): readonly number[] {
    return this.series.get(nodeId) ?? [];
  }

  clear(): void {
    this.series.clear();
  }
}

/** SVG polyline points for 0..1 values in a width × height box; gaps (NaN) break the line into segments. */
export function sparkline(values: readonly number[], width: number, height: number): string[] {
  const segments: string[] = [];
  let current: string[] = [];
  const step = values.length > 1 ? width / (values.length - 1) : 0;
  values.forEach((v, i) => {
    if (Number.isNaN(v)) {
      if (current.length > 1) segments.push(current.join(' '));
      current = [];
      return;
    }
    current.push(`${(i * step).toFixed(1)},${(height - v * height).toFixed(1)}`);
  });
  if (current.length > 1) segments.push(current.join(' '));
  return segments;
}
