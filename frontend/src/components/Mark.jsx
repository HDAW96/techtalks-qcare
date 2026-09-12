/* A status mark: the glyph and the word together.
   The word is always present — state is written into the record, never left
   to colour alone (which also keeps it readable for colour-blind users).
   `status` is one of the four legend marks. Backend statuses are translated
   into marks with STATUS_MARK in constants.js before they reach here. */

const LABELS = {
  live: 'In the chair',
  wait: 'Waiting',
  miss: 'Missed',
  done: 'Completed',
}

function Mark({ status, label }) {
  // A status nobody mapped still shows a word rather than a bare glyph.
  const known = status in LABELS
  return (
    <span className={`mark mark--${known ? status : 'unknown'}`}>
      <span className="mark__glyph" aria-hidden="true" />
      {label ?? (known ? LABELS[status] : 'Unknown')}
    </span>
  )
}

export default Mark
