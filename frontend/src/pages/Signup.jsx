import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { motion } from 'framer-motion'
import { Eye, EyeOff, ArrowRight } from 'lucide-react'
import { useAuth } from '../contexts/AuthContext'
import { getErrorMessage } from '../services/api'

function Signup() {
  const { signup } = useAuth()
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const navigate = useNavigate()

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm()

  const onSubmit = async (data) => {
    setError('')
    setIsSubmitting(true)
    try {
      await signup(data.name, data.email, data.password)
      navigate('/login', { replace: true })
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="min-h-[calc(100vh-4rem)] flex items-center justify-center px-4 py-16">
      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, ease: [0.22, 1, 0.36, 1] }}
        className="w-full max-w-sm"
      >
        <div className="mb-8 text-center">
          <h1 className="font-display text-3xl font-semibold text-ink-800 mb-1.5">
            Create your account
          </h1>
          <p className="text-sm text-ink-500">Start cooking with what you have</p>
        </div>

        {error && (
          <div className="px-3.5 py-3 bg-brick-50 border border-brick-200 rounded-sheet text-brick-600 text-sm mb-6">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div>
            <label htmlFor="name" className="block text-sm font-medium text-ink-700 mb-1.5">
              Full name
            </label>
            <input
              id="name"
              type="text"
              placeholder="Jane Doe"
              className={`input-field ${errors.name ? 'border-brick-300' : ''}`}
              {...register('name', {
                required: 'Name is required',
                minLength: { value: 2, message: 'Name must be at least 2 characters' },
              })}
            />
            {errors.name && <p className="mt-1 text-xs text-brick-500">{errors.name.message}</p>}
          </div>

          <div>
            <label htmlFor="email" className="block text-sm font-medium text-ink-700 mb-1.5">
              Email
            </label>
            <input
              id="email"
              type="email"
              placeholder="you@example.com"
              className={`input-field ${errors.email ? 'border-brick-300' : ''}`}
              {...register('email', {
                required: 'Email is required',
                pattern: {
                  value: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
                  message: 'Please enter a valid email',
                },
              })}
            />
            {errors.email && <p className="mt-1 text-xs text-brick-500">{errors.email.message}</p>}
          </div>

          <div>
            <label htmlFor="password" className="block text-sm font-medium text-ink-700 mb-1.5">
              Password
            </label>
            <div className="relative">
              <input
                id="password"
                type={showPassword ? 'text' : 'password'}
                placeholder="Create a password"
                className={`input-field pr-11 ${errors.password ? 'border-brick-300' : ''}`}
                {...register('password', {
                  required: 'Password is required',
                  minLength: { value: 6, message: 'Password must be at least 6 characters' },
                })}
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                aria-label={showPassword ? 'Hide password' : 'Show password'}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-ink-400 hover:text-ink-600 transition-colors"
              >
                {showPassword ? <EyeOff className="w-4.5 h-4.5" strokeWidth={1.75} /> : <Eye className="w-4.5 h-4.5" strokeWidth={1.75} />}
              </button>
            </div>
            {errors.password && <p className="mt-1 text-xs text-brick-500">{errors.password.message}</p>}
          </div>

          <div>
            <label htmlFor="confirmPassword" className="block text-sm font-medium text-ink-700 mb-1.5">
              Confirm password
            </label>
            <input
              id="confirmPassword"
              type={showPassword ? 'text' : 'password'}
              placeholder="Confirm your password"
              className={`input-field ${errors.confirmPassword ? 'border-brick-300' : ''}`}
              {...register('confirmPassword', {
                required: 'Please confirm your password',
                validate: (value) => value === watch('password') || 'Passwords do not match',
              })}
            />
            {errors.confirmPassword && (
              <p className="mt-1 text-xs text-brick-500">{errors.confirmPassword.message}</p>
            )}
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            className="btn-primary w-full mt-2 disabled:opacity-60 disabled:cursor-not-allowed"
          >
            {isSubmitting ? (
              <span>Creating account…</span>
            ) : (
              <span className="flex items-center gap-2">
                Create account
                <ArrowRight className="w-4 h-4" strokeWidth={1.75} />
              </span>
            )}
          </button>
        </form>

        <p className="mt-8 text-center text-sm text-ink-500">
          Already have an account?{' '}
          <Link to="/login" className="font-medium text-brick-500 hover:text-brick-600 transition-colors">
            Log in
          </Link>
        </p>
      </motion.div>
    </div>
  )
}

export default Signup
