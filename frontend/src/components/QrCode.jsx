import { useEffect, useState } from 'react'
import QRCode from 'qrcode'

/* A real, scannable QR code for `value`, drawn in the browser (the project's
   "client-side QR generation"). The server only hands over the text; nothing
   about the image is stored anywhere. */
function QrCode({ value, label = 'QR code' }) {
  const [image, setImage] = useState({ value: null, src: null })

  useEffect(() => {
    if (!value) return undefined
    let cancelled = false
    QRCode.toDataURL(value, { margin: 1, width: 360, errorCorrectionLevel: 'M' })
      .then((src) => {
        if (!cancelled) setImage({ value, src })
      })
      .catch(() => {
        if (!cancelled) setImage({ value, src: null })
      })
    return () => {
      cancelled = true
    }
  }, [value])

  // Drawn for this exact value, so an old code never shows under a new one.
  if (!value || image.value !== value || !image.src) {
    return <div className="qr qr--empty" aria-hidden="true" />
  }
  return <img className="qr" src={image.src} alt={label} />
}

export default QrCode
