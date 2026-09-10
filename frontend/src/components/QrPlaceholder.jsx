/* A stand-in for the real QR code, drawn as geometry so it reads as a code
   rather than as a grey box. Phase 2 replaces this with a generated QR for the
   actual ticket. The module pattern is deterministic, not random, so it does
   not flicker between renders. */

const SIZE = 21

function isFinder(x, y) {
  const inBox = (ox, oy) =>
    x >= ox && x < ox + 7 && y >= oy && y < oy + 7 &&
    !(x > ox && x < ox + 6 && y > oy && y < oy + 6 &&
      !(x > ox + 1 && x < ox + 5 && y > oy + 1 && y < oy + 5))

  return inBox(0, 0) || inBox(SIZE - 7, 0) || inBox(0, SIZE - 7)
}

function inFinderZone(x, y) {
  return (
    (x < 8 && y < 8) || (x >= SIZE - 8 && y < 8) || (x < 8 && y >= SIZE - 8)
  )
}

function QrPlaceholder({ code = '' }) {
  const seed = [...code].reduce((a, c) => a + c.charCodeAt(0), 7)
  const modules = []

  for (let y = 0; y < SIZE; y += 1) {
    for (let x = 0; x < SIZE; x += 1) {
      const on = isFinder(x, y)
        ? true
        : inFinderZone(x, y)
          ? false
          : ((x * 7 + y * 13 + seed) * 2654435761) % 11 > 5

      if (on) modules.push(<rect key={`${x}-${y}`} x={x} y={y} width="1" height="1" />)
    }
  }

  return (
    <svg
      className="qr"
      viewBox={`0 0 ${SIZE} ${SIZE}`}
      role="img"
      aria-label={`QR code placeholder for ticket ${code}`}
      shapeRendering="crispEdges"
    >
      <g fill="var(--ink)">{modules}</g>
    </svg>
  )
}

export default QrPlaceholder
