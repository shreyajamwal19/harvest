import { useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { LogOut } from 'lucide-react'
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

function Profile() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = async () => {
    await logout()
    navigate('/login')
  }

  return (
    <div className="min-h-[calc(100vh-4rem)] flex items-center justify-center px-4 py-16">
      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, ease: [0.22, 1, 0.36, 1] }}
        className="w-full max-w-sm text-center"
      >
        <div className="w-16 h-16 mx-auto mb-5 rounded-full bg-brick-500 text-paper-50 font-display text-xl font-semibold flex items-center justify-center">
          {initials(user?.name)}
        </div>
        <h1 className="font-display text-2xl font-semibold text-ink-800 mb-1">
          {user?.name}
        </h1>
        <p className="text-sm text-ink-500 mb-8">{user?.email}</p>

        <button
          onClick={handleLogout}
          className="btn-secondary w-full flex items-center justify-center gap-2"
        >
          <LogOut className="w-4 h-4" strokeWidth={1.75} />
          Log out
        </button>
      </motion.div>
    </div>
  )
}

export default Profile
