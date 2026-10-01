/* Stands in for a section whose screen has not been built yet, so every
   sidebar link opens something instead of an empty page. A later ticket
   replaces its entry in adminSections.jsx and this file stays for the rest. */
function SectionPlaceholder({ title }) {
  return (
    <div className="sheet">
      <div className="sheet__head">
        <h2>{title}</h2>
        <p className="meta meta--right">Coming soon</p>
      </div>
      <div className="sheet__body">
        <p className="lede">This section has not been built yet.</p>
      </div>
    </div>
  )
}

export default SectionPlaceholder