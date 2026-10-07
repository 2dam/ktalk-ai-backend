import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.jsx'
import { EnglishHintProvider } from './EnglishHint.jsx'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <EnglishHintProvider>
      <App />
    </EnglishHintProvider>
  </StrictMode>,
)

if ('serviceWorker' in navigator && import.meta.env.PROD) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => {})
  })
}
