/* eslint-disable react/prop-types -- this project doesn't use PropTypes, see AuthContext.jsx */
import { useState, useEffect } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { motion, AnimatePresence } from 'framer-motion'
import { LogOut, ShoppingBasket, Bookmark, ArrowUpRight, KeyRound, Eye, EyeOff, Check, Sparkles, ChefHat } from 'lucide-react'
import { useAuth } from '../contexts/AuthContext'
import { getPantryItems, getSavedRecipes, changePassword, deleteAccount, getErrorMessage } from '../services/api'

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

function ChangePasswordForm({ onDone }) {
  const [showCurrent, setShowCurrent] = useState(false)
  const [showNew, setShowNew] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm()

  const onSubmit = async (data) => {
    setError('')
    setIsSubmitting(true)
    try {
      await changePassword({ currentPassword: data.currentPassword, newPassword: data.newPassword })
      setSuccess(true)
      reset()
      setTimeout(() => onDone(), 1200)
    } catch (err) {
      setError(getErrorMessage(err, 'Could not update your password'))
    } finally {
      setIsSubmitting(false)
    }
  }

  if (success) {
    return (
      <div className="flex items-center gap-2 text-sm text-moss-600 py-2">
        <Check className="w-4 h-4" strokeWidth={1.75} />
        Password updated.
      </div>
    )
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-3 pt-1">
      {error && (
        <div className="px-3.5 py-2.5 bg-brick-50 border border-brick-200 rounded-sheet text-brick-600 text-sm">
          {error}
        </div>
      )}

      <div>
        <label htmlFor="currentPassword" className="block text-xs font-medium text-ink-600 mb-1">
          Current password
        </label>
        <div className="relative">
          <input
            id="currentPassword"
            type={showCurrent ? 'text' : 'password'}
            autoComplete="current-password"
            className={`input-field pr-11 ${errors.currentPassword ? 'border-brick-300' : ''}`}
            aria-invalid={errors.currentPassword ? 'true' : 'false'}
            {...register('currentPassword', { required: 'Current password is required' })}
          />
          <button
            type="button"
            onClick={() => setShowCurrent(!showCurrent)}
            aria-label={showCurrent ? 'Hide password' : 'Show password'}
            className="absolute right-3 top-1/2 -translate-y-1/2 text-ink-400 hover:text-ink-600 transition-colors"
          >
            {showCurrent ? <EyeOff className="w-4 h-4" strokeWidth={1.75} /> : <Eye className="w-4 h-4" strokeWidth={1.75} />}
          </button>
        </div>
        {errors.currentPassword && (
          <p className="mt-1 text-xs text-brick-500">{errors.currentPassword.message}</p>
        )}
      </div>

      <div>
        <label htmlFor="newPassword" className="block text-xs font-medium text-ink-600 mb-1">
          New password
        </label>
        <div className="relative">
          <input
            id="newPassword"
            type={showNew ? 'text' : 'password'}
            autoComplete="new-password"
            className={`input-field pr-11 ${errors.newPassword ? 'border-brick-300' : ''}`}
            aria-invalid={errors.newPassword ? 'true' : 'false'}
            {...register('newPassword', {
              required: 'New password is required',
              minLength: { value: 6, message: 'Password must be at least 6 characters' },
            })}
          />
          <button
            type="button"
            onClick={() => setShowNew(!showNew)}
            aria-label={showNew ? 'Hide password' : 'Show password'}
            className="absolute right-3 top-1/2 -translate-y-1/2 text-ink-400 hover:text-ink-600 transition-colors"
          >
            {showNew ? <EyeOff className="w-4 h-4" strokeWidth={1.75} /> : <Eye className="w-4 h-4" strokeWidth={1.75} />}
          </button>
        </div>
        {errors.newPassword && <p className="mt-1 text-xs text-brick-500">{errors.newPassword.message}</p>}
      </div>

      <div className="flex gap-2 pt-1">
        <button
          type="submit"
          disabled={isSubmitting}
          className="btn-primary flex-1 text-sm py-2.5 disabled:opacity-60 disabled:cursor-not-allowed"
        >
          {isSubmitting ? 'Updating…' : 'Update password'}
        </button>
        <button type="button" onClick={onDone} className="btn-ghost text-sm">
          Cancel
        </button>
      </div>
    </form>
  )
}

function DeleteAccountForm({ onCancel }) {
  const { logout } = useAuth()
  const navigate = useNavigate()
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')
  const [isDeleting, setIsDeleting] = useState(false)

  const handleDelete = async (e) => {
    e.preventDefault()
    if (!password) {
      setError('Enter your current password to confirm.')
      return
    }
    if (!window.confirm('Delete your account permanently? This removes your pantry, saved recipes, cooking history, and everything Harvest has learned about you. This cannot be undone.')) {
      return
    }
    setError('')
    setIsDeleting(true)
    try {
      await deleteAccount(password)
      await logout()
      navigate('/', { replace: true })
    } catch (err) {
      setError(getErrorMessage(err, 'Could not delete your account'))
      setIsDeleting(false)
    }
  }

  return (
    <form onSubmit={handleDelete} className="space-y-3 pt-1">
      {error && (
        <div className="px-3.5 py-2.5 bg-brick-50 border border-brick-200 rounded-sheet text-brick-600 text-sm">
          {error}
        </div>
      )}
      <div>
        <label htmlFor="deleteConfirmPassword" className="block text-xs font-medium text-ink-600 mb-1">
          Confirm your password to delete your account
        </label>
        <div className="relative">
          <input
            id="deleteConfirmPassword"
            type={showPassword ? 'text' : 'password'}
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="input-field pr-11"
          />
          <button
            type="button"
            onClick={() => setShowPassword(!showPassword)}
            aria-label={showPassword ? 'Hide password' : 'Show password'}
            className="absolute right-3 top-1/2 -translate-y-1/2 text-ink-400 hover:text-ink-600 transition-colors"
          >
            {showPassword ? <EyeOff className="w-4 h-4" strokeWidth={1.75} /> : <Eye className="w-4 h-4" strokeWidth={1.75} />}
          </button>
        </div>
      </div>
      <div className="flex gap-2 pt-1">
        <button
          type="submit"
          disabled={isDeleting}
          className="flex-1 text-sm py-2.5 rounded-xl font-medium bg-brick-500 text-paper-50 hover:bg-brick-600 transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
        >
          {isDeleting ? 'Deleting…' : 'Delete my account'}
        </button>
        <button type="button" onClick={onCancel} className="btn-ghost text-sm">
          Cancel
        </button>
      </div>
    </form>
  )
}

function Profile() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const [pantryCount, setPantryCount] = useState(null)
  const [savedCount, setSavedCount] = useState(null)
  const [showPasswordForm, setShowPasswordForm] = useState(false)
  const [showDeleteForm, setShowDeleteForm] = useState(false)

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

      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, delay: 0.14, ease: [0.22, 1, 0.36, 1] }}
        className="card mb-8"
      >
        <button
          type="button"
          onClick={() => setShowPasswordForm(!showPasswordForm)}
          className="w-full flex items-center gap-3 text-left"
        >
          <span className="flex-shrink-0 w-9 h-9 rounded-full bg-paper-200 text-ink-600 flex items-center justify-center">
            <KeyRound className="w-4 h-4" strokeWidth={1.75} />
          </span>
          <span className="flex-1 text-sm font-medium text-ink-700">Change password</span>
        </button>
        <AnimatePresence initial={false}>
          {showPasswordForm && (
            <motion.div
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: 'auto' }}
              exit={{ opacity: 0, height: 0 }}
              transition={{ duration: 0.25, ease: [0.22, 1, 0.36, 1] }}
              className="overflow-hidden"
            >
              <ChangePasswordForm onDone={() => setShowPasswordForm(false)} />
            </motion.div>
          )}
        </AnimatePresence>
      </motion.div>

      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, delay: 0.17, ease: [0.22, 1, 0.36, 1] }}
        className="card mb-8"
      >
        <Link to="/settings" className="flex items-center gap-3">
          <span className="flex-shrink-0 w-9 h-9 rounded-full bg-paper-200 text-ink-600 flex items-center justify-center">
            <Sparkles className="w-4 h-4" strokeWidth={1.75} />
          </span>
          <span className="flex-1 text-sm font-medium text-ink-700">What Harvest knows about you</span>
          <ArrowUpRight className="w-4 h-4 text-ink-400" strokeWidth={1.75} />
        </Link>
      </motion.div>

      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, delay: 0.2, ease: [0.22, 1, 0.36, 1] }}
        className="card mb-8"
      >
        <Link to="/history" className="flex items-center gap-3">
          <span className="flex-shrink-0 w-9 h-9 rounded-full bg-paper-200 text-ink-600 flex items-center justify-center">
            <ChefHat className="w-4 h-4" strokeWidth={1.75} />
          </span>
          <span className="flex-1 text-sm font-medium text-ink-700">Cooking history</span>
          <ArrowUpRight className="w-4 h-4 text-ink-400" strokeWidth={1.75} />
        </Link>
      </motion.div>

      <motion.button
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, delay: 0.26, ease: [0.22, 1, 0.36, 1] }}
        onClick={handleLogout}
        className="btn-secondary w-full flex items-center justify-center gap-2"
      >
        <LogOut className="w-4 h-4" strokeWidth={1.75} />
        Log out
      </motion.button>

      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, delay: 0.32, ease: [0.22, 1, 0.36, 1] }}
        className="mt-10 pt-6 border-t border-ink-700/10"
      >
        {!showDeleteForm ? (
          <button
            type="button"
            onClick={() => setShowDeleteForm(true)}
            className="text-xs font-medium text-ink-400 hover:text-brick-500 transition-colors"
          >
            Delete account
          </button>
        ) : (
          <DeleteAccountForm onCancel={() => setShowDeleteForm(false)} />
        )}
      </motion.div>
    </div>
  )
}

export default Profile
