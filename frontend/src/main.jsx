import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { LazyMotion } from 'framer-motion'
import App from './App.jsx'
import './index.css'

const loadFeatures = () => import('./features.js').then(res => res.default)

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <LazyMotion features={loadFeatures} strict>
      <App />
    </LazyMotion>
  </StrictMode>,
)
