import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { MotionConfig } from 'framer-motion'
import { AuthProvider } from './contexts/AuthContext'
import App from './App.jsx'
import './index.css'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    {/* reducedMotion="user" makes every motion.* component in the app respect the OS-level
        prefers-reduced-motion setting automatically. The @media rule in index.css only ever
        covered plain CSS animations/transitions - Framer Motion drives its own animate/repeat
        props imperatively via the Web Animations API, entirely outside that rule, so every
        looping animation in the app (the chat loading steam wisps, progress dots, hover
        micro-interactions, page-load reveals) was previously unaffected by that setting no
        matter what the person's OS was configured to do. */}
    <MotionConfig reducedMotion="user">
      <BrowserRouter>
        <AuthProvider>
          <App />
        </AuthProvider>
      </BrowserRouter>
    </MotionConfig>
  </StrictMode>,
)
