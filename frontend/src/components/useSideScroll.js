import { useEffect, useState } from 'react'

/* On a narrow screen the table scrolls sideways, which nobody discovers by
   accident. So while the table is wider than its box, a line above it says
   so, and a fade on each edge shows which way there is more to see. On a
   desktop the whole table fits and none of this appears. */
export function useSideScroll() {
  // A callback ref, so the effect re-runs when the table first appears.
  const [el, setEl] = useState(null)
  const [edges, setEdges] = useState({ wide: false, left: false, right: false })

  useEffect(() => {
    if (!el) return undefined
    const check = () => {
      const wide = el.scrollWidth > el.clientWidth + 4
      setEdges({
        wide,
        left: wide && el.scrollLeft > 4,
        right: wide && el.scrollLeft + el.clientWidth < el.scrollWidth - 4,
      })
    }
    // ResizeObserver also fires once right away, which covers the first render.
    const observer = new ResizeObserver(check)
    observer.observe(el)
    el.addEventListener('scroll', check, { passive: true })
    return () => {
      observer.disconnect()
      el.removeEventListener('scroll', check)
    }
  }, [el])

  return [setEl, edges]
}
