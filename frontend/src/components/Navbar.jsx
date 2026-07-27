import { useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import { Menu, X, Leaf, LogOut, User } from 'lucide-react'
import { useAuth } from '../contexts/AuthContext'

function Navbar() {
  const [isOpen, setIsOpen] = useState(false)
  const { user, isAuthenticated, logout } = useAuth()
  const location = useLocation()

  const isActive = (path) => location.pathname === path

  const navLinks = [
    { path: '/', label: 'Home' },
  ]

  return (
    <nav className="sticky top-0 z-50 bg-cream-100/80 backdrop-blur-md border-b border-cream-200">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">
          {/* Logo */}
          <Link to="/" className="flex items-center gap-2 group">
            <div className="w-9 h-9 bg-sage-600 rounded-xl flex items-center justify-center group-hover:bg-sage-700 transition-colors">
              <Leaf className="w-5 h-5 text-white" />
            </div>
            <span className="font-display text-xl font-semibold text-sage-800 tracking-tight">
              Harvest
            </span>
          </Link>

          {/* Desktop Nav */}
          <div className="hidden md:flex items-center gap-1">
            {navLinks.map((link) => (
              <Link
                key={link.path}
                to={link.path}
                className={`px-4 py-2 rounded-xl text-sm font-medium transition-all ${
                  isActive(link.path)
                    ? 'text-sage-800 bg-sage-100'
                    : 'text-sage-600 hover:text-sage-800 hover:bg-cream-200'
                }`}
              >
                {link.label}
              </Link>
            ))}
          </div>

          {/* Desktop Auth */}
          <div className="hidden md:flex items-center gap-3">
            {isAuthenticated ? (
              <div className="flex items-center gap-3">
                <div className="flex items-center gap-2 px-3 py-1.5 bg-cream-200 rounded-xl">
                  <User className="w-4 h-4 text-sage-600" />
                  <span className="text-sm font-medium text-sage-700">{user?.name}</span>
                </div>
                <button
                  onClick={logout}
                  className="flex items-center gap-1.5 px-4 py-2 text-sm font-medium text-terracotta-600 hover:text-terracotta-700 hover:bg-terracotta-50 rounded-xl transition-all"
                >
                  <LogOut className="w-4 h-4" />
                  Log out
                </button>
              </div>
            ) : (
              <>
                <Link
                  to="/login"
                  className="px-4 py-2 text-sm font-medium text-sage-700 hover:text-sage-900 hover:bg-cream-200 rounded-xl transition-all"
                >
                  Log in
                </Link>
                <Link
                  to="/signup"
                  className="btn-primary text-sm py-2"
                >
                  Get started
                </Link>
              </>
            )}
          </div>

          {/* Mobile Menu Button */}
          <button
            onClick={() => setIsOpen(!isOpen)}
            className="md:hidden p-2 rounded-xl text-sage-600 hover:bg-cream-200 transition-colors"
          >
            {isOpen ? <X className="w-6 h-6" /> : <Menu className="w-6 h-6" />}
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
            className="md:hidden bg-cream-100 border-b border-cream-200 overflow-hidden"
          >
            <div className="px-4 py-3 space-y-1">
              {navLinks.map((link) => (
                <Link
                  key={link.path}
                  to={link.path}
                  onClick={() => setIsOpen(false)}
                  className={`block px-4 py-2.5 rounded-xl text-sm font-medium transition-all ${
                    isActive(link.path)
                      ? 'text-sage-800 bg-sage-100'
                      : 'text-sage-600 hover:text-sage-800 hover:bg-cream-200'
                  }`}
                >
                  {link.label}
                </Link>
              ))}
              <div className="pt-3 border-t border-cream-200 mt-3">
                {isAuthenticated ? (
                  <>
                    <div className="flex items-center gap-2 px-4 py-2 mb-2">
                      <User className="w-4 h-4 text-sage-600" />
                      <span className="text-sm font-medium text-sage-700">{user?.name}</span>
                    </div>
                    <button
                      onClick={() => { logout(); setIsOpen(false); }}
                      className="flex items-center gap-2 w-full px-4 py-2.5 text-sm font-medium text-terracotta-600 hover:bg-terracotta-50 rounded-xl transition-all"
                    >
                      <LogOut className="w-4 h-4" />
                      Log out
                    </button>
                  </>
                ) : (
                  <div className="space-y-2">
                    <Link
                      to="/login"
                      onClick={() => setIsOpen(false)}
                      className="block px-4 py-2.5 text-sm font-medium text-sage-700 hover:bg-cream-200 rounded-xl transition-all"
                    >
                      Log in
                    </Link>
                    <Link
                      to="/signup"
                      onClick={() => setIsOpen(false)}
                      className="block btn-primary text-sm text-center"
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
