import { useId } from 'react'

/* A field on a form sheet: a ruled line with a label above it, not a floating
   box. `error` names the problem; `note` explains the field when it needs it. */
function Cell({ label, optional, note, error, type = 'text', ...rest }) {
  const id = useId()
  const noteId = `${id}-note`

  return (
    <div className="cell">
      <label className="cell__label" htmlFor={id}>
        <span>{label}</span>
        {optional && <span className="cell__optional">optional</span>}
      </label>
      <input
        id={id}
        type={type}
        className="cell__input"
        aria-invalid={error ? 'true' : undefined}
        aria-describedby={note || error ? noteId : undefined}
        {...rest}
      />
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
