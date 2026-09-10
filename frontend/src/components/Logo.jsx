import { Link } from 'react-router-dom'

/* The only place the logo asset is referenced. Swap the file here when a
   transparent PNG/SVG replaces the current JPG.
   multiply blending drops the JPG's white box on tinted surfaces. */
function Logo({ to = '/', size = 34 }) {
  return (
    <Link to={to} className="logo" aria-label="QCare — home">
      <img
        src="/logo.jpg"
        alt=""
        width={size}
        height={size}
        className="logo__mark"
      />
      <span className="logo__word">QCare</span>
    </Link>
  )
}

export default Logo
