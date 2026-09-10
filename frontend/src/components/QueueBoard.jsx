import Mark from './Mark'

/* The waiting list. Rank is encoded in the type itself: the front of the queue
   is set larger and wider and it narrows as it recedes, so the order is
   readable before any numeral is. `--rank-lift` runs 1 at the front to 0 at
   the back and drives size, width and weight together. */
function QueueBoard({ entries }) {
  const last = Math.max(entries.length - 1, 1)

  return (
    <ol className="board">
      {entries.map((entry, i) => {
        const lift = 1 - i / last

        return (
          <li
            key={entry.ticket}
            className={[
              'qrow',
              entry.status === 'live' ? 'qrow--live' : '',
              entry.you ? 'qrow--you' : '',
            ]
              .filter(Boolean)
              .join(' ')}
            style={{ '--rank-lift': lift.toFixed(3) }}
          >
            <span className="qrow__num">{entry.ticket}</span>
            <span className="qrow__name">
              {entry.name}
              {entry.you && <span className="qrow__you-tag"> — your ticket</span>}
            </span>
            <span className="qrow__eta">
              <Mark status={entry.status} label={entry.eta} />
            </span>
          </li>
        )
      })}
    </ol>
  )
}

export default QueueBoard
