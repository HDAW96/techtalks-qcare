/* A status mark: the glyph and the word together.
   The word is always present — state is written into the record, never left
   to colour alone (which also keeps it readable for colour-blind users). */

const LABELS = {
  live: 'In the chair',
  wait: 'Waiting',
  miss: 'Missed',
  done: 'Completed',
}

function Mark({ status, label }) {
  return (
    <span className={`mark mark--${status}`}>
      <span className="mark__glyph" aria-hidden="true" />
      {label ?? LABELS[status]}
    </span>
  )
}

export default Mark
