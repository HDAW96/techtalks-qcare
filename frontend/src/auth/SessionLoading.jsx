/* Shown by the route guards while the stored token is still being checked —
   rendering either branch before then would briefly flash the wrong one. */
function SessionLoading() {
  return (
    <div className="page">
      <div className="empty" role="status">
        <p>Loading…</p>
      </div>
    </div>
  )
}

export default SessionLoading
