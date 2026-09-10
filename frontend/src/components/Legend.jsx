import Mark from './Mark'

/* The legend that governs every mark on the screen. Borrowed from a dental
   chart, where no symbol means anything until the legend defines it. */
function Legend({ items = ['live', 'wait', 'miss', 'done'] }) {
  return (
    <div className="legend">
      <span className="legend__title">Legend</span>
      {items.map((status) => (
        <Mark key={status} status={status} />
      ))}
    </div>
  )
}

export default Legend
