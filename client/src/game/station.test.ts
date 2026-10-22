import { describe, expect, it } from 'vitest';
import { hexDistance, hexToPixel } from '../map/hex';
import { LINKS, loadColor, NODE01, NODES, type StationNode } from './station';

function node(id: string): StationNode {
  const n = NODES.find((x) => x.id === id);
  if (n === undefined) throw new Error(`unknown node ${id}`);
  return n;
}

describe('station map', () => {
  it('hides load the player cannot measure (fog of war)', () => {
    expect(node('cpu').load({}, NODE01)).toBeUndefined();
    expect(loadColor(undefined)).toBe(0x3a4258);
  });

  it('turns metrics into 0..1 load against the capacity the server reports', () => {
    expect(node('threads').load({ tomcat_threads_busy: 50 }, NODE01)).toBeCloseTo(0.25);
    expect(node('threads').load({ tomcat_threads_busy: 50 }, { ...NODE01, threads: 400 })).toBeCloseTo(0.125);
    expect(node('db_pool').load({ hikaricp_connections_active: 10 }, NODE01)).toBe(1);
    expect(node('cpu').load({ cpu_utilization: 1.4 }, NODE01)).toBe(1);
  });

  it('says which exporter measures each part of USE, and admits what nobody exports', () => {
    expect(node('db_pool').use.saturation?.metric).toBe('hikaricp_connections_pending');
    expect(node('threads').use.saturation).toBeNull();
    expect(node('db_cpu').reveals).toBe('postgres-exporter');
    expect(node('threads').use.utilization?.format(150, NODE01)).toBe('150 of 200 busy');
  });

  it('links only neighbouring hexes, so every pipe is one step long', () => {
    for (const link of LINKS) {
      expect(hexDistance(node(link.from).hex, node(link.to).hex), `${link.from}→${link.to}`).toBe(1);
    }
  });

  it('places hexes on a pointy-top grid', () => {
    expect(hexToPixel({ q: 1, r: 0 }, 10).x).toBeCloseTo(17.32, 2);
    expect(hexToPixel({ q: 0, r: 1 }, 10)).toMatchObject({ y: 15 });
  });
});
