/* eslint-disable react/prop-types -- this project doesn't use PropTypes, see AuthContext.jsx */
import { useState, useEffect } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { LogOut, ShoppingBasket, Bookmark, ArrowUpRight } from 'lucide-react'
import { useAuth } from '../contexts/AuthContext'
import { getPantryItems, getSavedRecipes } from '../services/api'

function initials(name) {
  if (!name) return '?'
  return name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join('')
}

function StatCard({ to, icon: Icon, label, value, loading }) {
  return (
    <Link
      to={to}
      className="group card flex items-center gap-4 hover:shadow-lift hover:-translate-y-0.5 transition-all duration-300 ease-quiet"
    >
      <span className="flex-shrink-0 w-11 h-11 rounded-full bg-paper-200 text-brick-500 flex items-center justify-center group-hover:bg-brick-50 transition-colors">
        <Icon className="w-5 h-5" strokeWidth={1.75} />
      </span>
      <span className="min-w-0 flex-1 text-left">
        <span className="block font-display text-2xl font-semibold text-ink-800 leading-none">
          {loading ? (
            <span className="inline-block w-6 h-6 rounded-full border-2 border-ink-700/15 border-t-brick-400 animate-spin align-middle" />
          ) : (
            value
          )}
        </span>
        <span className="block text-xs text-ink-500 mt-1.5">{label}</span>
      </span>
      <ArrowUpRight
        className="w-4 h-4 text-ink-400 flex-shrink-0 opacity-0 group-hover:opacity-100 transition-opacity"
        strokeWidth={1.75}
      />
    </Link>
  )
}

function Profile() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const [pantryCount, setPantryCount] = useState(null)
  const [savedCount, setSavedCount] = useState(null)

  useEffect(() => {
    let cancelled = false
    getPantryItems()
      .then((res) => !cancelled && setPantryCount(res.data.length))
      .catch(() => !cancelled && setPantryCount(0))
    getSavedRecipes()
      .then((res) => !cancelled && setSavedCount(res.data.length))
      .catch(() => !cancelled && setSavedCount(0))
    return () => {
      cancelled = true
    }
  }, [])

  const handleLogout = async () => {
    await logout()
    navigate('/login')
  }

  return (
    <div className="min-h-[calc(100vh-4rem)] max-w-md mx-auto px-4 sm:px-6 py-16">
      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, ease: [0.22, 1, 0.36, 1] }}
        className="text-center mb-8"
      >
        <div className="w-16 h-16 mx-auto mb-5 rounded-full bg-brick-500 text-paper-50 font-display text-xl font-semibold flex items-center justify-center">
          {initials(user?.name)}
        </div>
        <h1 className="font-display text-2xl font-semibold text-ink-800 mb-1">
          {user?.name}
        </h1>
        <p className="text-sm text-ink-500">{user?.email}</p>
      </motion.div>

      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, delay: 0.08, ease: [0.22, 1, 0.36, 1] }}
        className="space-y-3 mb-8"
      >
        <StatCard
          to="/pantry"
          icon={ShoppingBasket}
          label="Pantry items"
          value={pantryCount}
          loading={pantryCount === null}
        />
        <StatCard
          to="/saved"
          icon={Bookmark}
          label="Saved recipes"
          value={savedCount}
          loading={savedCount === null}
        />
      </motion.div>

      <motion.button
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, delay: 0.14, ease: [0.22, 1, 0.36, 1] }}
        onClick={handleLogout}
        className="btn-secondary w-full flex items-center justify-center gap-2"
      >
        <LogOut className="w-4 h-4" strokeWidth={1.75} />
        Log out
      </motion.button>
    </div>
  )
}

export default Profile
