// Pointy-top hexagons in axial coordinates (q, r). Reference: redblobgames.com/grids/hexagons.

export interface Axial {
  q: number;
  r: number;
}

export interface Point {
  x: number;
  y: number;
}

const SQRT3 = Math.sqrt(3);

/** Centre of a hex whose corners are `size` away from it. */
export function hexToPixel({ q, r }: Axial, size: number): Point {
  return { x: size * SQRT3 * (q + r / 2), y: size * 1.5 * r };
}

/** Corner points, clockwise from the top, flattened for Graphics.poly. */
export function hexCorners(centre: Point, size: number): number[] {
  const points: number[] = [];
  for (let i = 0; i < 6; i++) {
    const angle = (Math.PI / 180) * (60 * i - 90);
    points.push(centre.x + size * Math.cos(angle), centre.y + size * Math.sin(angle));
  }
  return points;
}

export function hexDistance(a: Axial, b: Axial): number {
  const dq = a.q - b.q;
  const dr = a.r - b.r;
  return (Math.abs(dq) + Math.abs(dr) + Math.abs(dq + dr)) / 2;
}
