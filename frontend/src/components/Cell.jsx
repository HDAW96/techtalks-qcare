import { useId } from 'react'

/* A field on a form sheet: a ruled line with a label above it, not a floating
   box. `error` names the problem; `note` explains the field when it needs it.

   Pass `options` and the field becomes a chooser instead of a typed line. It
   is the same component because it is the same field: the label, the note and
   the error behave identically, and keeping them in one place is what stops
   the two kinds drifting apart. */
function Cell({ label, optional, note, error, type = 'text', options, placeholder, ...rest }) {
  const id = useId()
  const noteId = `${id}-note`

  const shared = {
    id,
    className: 'cell__input',
    'aria-invalid': error ? 'true' : undefined,
    'aria-describedby': note || error ? noteId : undefined,
    ...rest,
  }

  return (
    <div className="cell">
      <label className="cell__label" htmlFor={id}>
        <span>{label}</span>
        {optional && <span className="cell__optional">optional</span>}
      </label>
      {options ? (
        <select {...shared}>
          {/* An empty first option, so nothing is chosen by accident merely
              because it happened to be first in the list. */}
          <option value="">{placeholder ?? 'Choose…'}</option>
          {options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      ) : (
        <input type={type} placeholder={placeholder} {...shared} />
      )}
      {(note || error) && (
        <p
          id={noteId}
          className={`cell__note${error ? ' cell__note--error' : ''}`}
        >
          {error || note}
        </p>
      )}
    </div>
  )
}

export default Cell
