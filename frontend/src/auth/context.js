import { createContext } from 'react'

/* Kept apart from the provider component so that file exports only a
   component — otherwise Vite's fast refresh cannot hot-reload it. */
export const AuthContext = createContext(null)
