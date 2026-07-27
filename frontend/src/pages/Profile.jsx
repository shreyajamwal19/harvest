import { motion } from 'framer-motion'
import { useNavigate } from 'react-router-dom'
import { User, Mail, LogOut } from 'lucide-react'
import { useAuth } from '../contexts/AuthContext'

function Profile() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = async () => {
    await logout()
    navigate('/login')
  }

  return (
    <div className="min-h-[calc(100vh-4rem)] flex items-center justify-center px-4 py-12">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5 }}
        className="w-full max-w-md"
      >
        <div className="card">
          <div className="text-center mb-8">
            <div className="w-16 h-16 bg-sage-100 rounded-2xl flex items-center justify-center mx-auto mb-4">
              <User className="w-8 h-8 text-sage-600" />
            </div>
            <h1 className="font-display text-2xl font-bold text-sage-900 mb-1">
              {user?.name}
            </h1>
            <p className="text-sm text-sage-500 flex items-center justify-center gap-1.5">
              <Mail className="w-4 h-4" />
              {user?.email}
            </p>
          </div>

          <button
            onClick={handleLogout}
            className="btn-primary w-full flex items-center justify-center gap-2"
          >
            <LogOut className="w-4 h-4" />
            Log out
          </button>
        </div>
      </motion.div>
    </div>
  )
}

export default Profile
