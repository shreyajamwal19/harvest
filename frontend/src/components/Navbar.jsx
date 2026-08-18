import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import { Menu, X, LogOut } from 'lucide-react'
import { useAuth } from '../contexts/AuthContext'

function initials(name) {
  if (!name) return '?'
  return name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join('')
}

function Navbar() {
  const [isOpen, setIsOpen] = useState(false)
  const { user, isAuthenticated, logout } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()

  const isActive = (path) => location.pathname === path

  const handleLogout = async () => {
    await logout()
    navigate('/')
  }

  const navLinks = [
    { path: '/', label: 'Home' },
    ...(isAuthenticated
      ? [
          { path: '/chef', label: 'Chef Brain' },
          { path: '/meal-plan', label: 'Meal Plan' },
          { path: '/pantry', label: 'Pantry' },
          { path: '/saved', label: 'Saved' },
        ]
      : []),
  ]

  return (
    <nav className="sticky top-0 z-50 bg-paper-100/90 backdrop-blur-sm border-b border-ink-700/10">
      <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">
          {/* Wordmark */}
          <Link to="/" className="group flex items-baseline gap-0.5">
            <span className="font-display text-2xl font-semibold text-ink-800 tracking-tight">
              Harvest
            </span>
            <span className="block h-[2px] w-2 bg-brick-500 translate-y-[-4px] transition-all duration-300 ease-quiet group-hover:w-4" />
          </Link>

          {/* Desktop Nav */}
          <div className="hidden md:flex items-center gap-8">
            {navLinks.map((link) => (
              <Link
                key={link.path}
                to={link.path}
                className={`relative py-2 text-sm font-medium transition-colors duration-150 ${
                  isActive(link.path)
                    ? 'text-ink-800'
                    : 'text-ink-500 hover:text-ink-800'
                }`}
              >
                {link.label}
                {isActive(link.path) && (
                  <motion.span
                    layoutId="nav-underline"
                    className="absolute left-0 right-0 -bottom-0.5 h-[2px] bg-brick-500"
                    transition={{ duration: 0.25, ease: [0.22, 1, 0.36, 1] }}
                  />
                )}
              </Link>
            ))}
          </div>

          {/* Desktop Auth */}
          <div className="hidden md:flex items-center gap-2">
            {isAuthenticated ? (
              <div className="flex items-center gap-2">
                <Link
                  to="/profile"
                  className="flex items-center gap-2 pl-1.5 pr-3 py-1.5 rounded-full hover:bg-paper-200 transition-colors"
                >
                  <span className="w-6 h-6 rounded-full bg-brick-500 text-paper-50 text-[11px] font-semibold flex items-center justify-center flex-shrink-0">
                    {initials(user?.name)}
                  </span>
                  <span className="text-sm font-medium text-ink-700">{user?.name}</span>
                </Link>
                <button onClick={handleLogout} className="btn-ghost flex items-center gap-1.5">
                  <LogOut className="w-3.5 h-3.5" strokeWidth={1.75} />
                  Log out
                </button>
              </div>
            ) : (
              <>
                <Link to="/login" className="btn-ghost">
                  Log in
                </Link>
                <Link to="/signup" className="btn-primary text-sm py-2 px-5">
                  Get started
                </Link>
              </>
            )}
          </div>

          {/* Mobile Menu Button */}
          <button
            onClick={() => setIsOpen(!isOpen)}
            aria-label={isOpen ? 'Close menu' : 'Open menu'}
            aria-expanded={isOpen}
            className="md:hidden p-2 -mr-2 rounded-sheet text-ink-600 hover:bg-paper-200 transition-colors"
          >
            {isOpen ? <X className="w-6 h-6" strokeWidth={1.75} /> : <Menu className="w-6 h-6" strokeWidth={1.75} />}
          </button>
        </div>
      </div>

      {/* Mobile Menu */}
      <AnimatePresence>
        {isOpen && (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            transition={{ duration: 0.2, ease: [0.22, 1, 0.36, 1] }}
            className="md:hidden bg-paper-100 border-b border-ink-700/10 overflow-hidden"
          >
            <div className="px-4 py-3 space-y-1">
              {navLinks.map((link) => (
                <Link
                  key={link.path}
                  to={link.path}
                  onClick={() => setIsOpen(false)}
                  className={`block px-2 py-3.5 min-h-[44px] flex items-center text-base font-medium border-b border-ink-700/5 ${
                    isActive(link.path) ? 'text-ink-800' : 'text-ink-500'
                  }`}
                >
                  {link.label}
                </Link>
              ))}
              <div className="pt-3">
                {isAuthenticated ? (
                  <>
                    <Link
                      to="/profile"
                      onClick={() => setIsOpen(false)}
                      className="flex items-center gap-2.5 px-2 py-2.5"
                    >
                      <span className="w-7 h-7 rounded-full bg-brick-500 text-paper-50 text-xs font-semibold flex items-center justify-center flex-shrink-0">
                        {initials(user?.name)}
                      </span>
                      <span className="text-sm font-medium text-ink-700">{user?.name}</span>
                    </Link>
                    <button
                      onClick={() => { handleLogout(); setIsOpen(false); }}
                      className="flex items-center gap-2 w-full px-2 py-2.5 text-sm font-medium text-brick-500"
                    >
                      <LogOut className="w-4 h-4" strokeWidth={1.75} />
                      Log out
                    </button>
                  </>
                ) : (
                  <div className="flex flex-col gap-2 px-2">
                    <Link
                      to="/login"
                      onClick={() => setIsOpen(false)}
                      className="py-2 text-sm font-medium text-ink-700"
                    >
                      Log in
                    </Link>
                    <Link
                      to="/signup"
                      onClick={() => setIsOpen(false)}
                      className="btn-primary text-sm text-center"
                    >
                      Get started
                    </Link>
                  </div>
                )}
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </nav>
  )
}

export default Navbar
