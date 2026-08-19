/* eslint-disable react/prop-types -- this project doesn't use PropTypes, see AuthContext.jsx */
import { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { ArrowRight, ChefHat, CalendarDays, ShoppingBasket, Bookmark } from 'lucide-react'
import { useAuth } from '../contexts/AuthContext'
import { getPantryItems, getSavedRecipes, getShowcaseRecipe } from '../services/api'

const capabilities = [
  {
    index: '01',
    title: 'Tell it what you have',
    description: 'List what\u2019s in your fridge or pantry, in plain language. No inventory to maintain.',
  },
  {
    index: '02',
    title: 'Get a real recipe',
    description: 'Harvest grounds every suggestion in an actual recipe \u2014 never an invented list of steps.',
  },
  {
    index: '03',
    title: 'Ask it anything mid-cook',
    description: 'Sauce split? Bread too dense? Ask Chef Brain directly and keep cooking.',
  },
]

function timeOfDayGreeting() {
  const hour = new Date().getHours()
  if (hour < 5) return 'Still up'
  if (hour < 12) return 'Good morning'
  if (hour < 17) return 'Good afternoon'
  if (hour < 21) return 'Good evening'
  return 'Good evening'
}

function firstName(fullName) {
  return fullName?.trim().split(' ')[0] || 'there'
}

function QuickActionCard({ to, icon: Icon, title, description, badge, index }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.45, delay: 0.1 + index * 0.06, ease: [0.22, 1, 0.36, 1] }}
    >
      <Link
        to={to}
        className="group card flex items-start gap-4 hover:shadow-lift hover:-translate-y-0.5 transition-all duration-300 ease-quiet h-full"
      >
        <span className="flex-shrink-0 w-11 h-11 rounded-full bg-paper-200 text-brick-500 flex items-center justify-center group-hover:bg-brick-50 transition-colors">
          <Icon className="w-5 h-5" strokeWidth={1.75} />
        </span>
        <span className="min-w-0 flex-1">
          <span className="flex items-center justify-between gap-2">
            <span className="font-display text-lg font-semibold text-ink-800">{title}</span>
            <ArrowRight
              className="w-4 h-4 text-ink-400 flex-shrink-0 -translate-x-1 opacity-0 group-hover:translate-x-0 group-hover:opacity-100 transition-all duration-200"
              strokeWidth={1.75}
            />
          </span>
          <span className="block text-sm text-ink-500 mt-1 leading-relaxed">{description}</span>
          {badge && (
            <span className="inline-block mt-3 text-xs font-medium text-moss-500 bg-moss-50 border border-moss-100 rounded-full px-2.5 py-1">
              {badge}
            </span>
          )}
        </span>
      </Link>
    </motion.div>
  )
}

function AuthenticatedHome({ userName }) {
  const [pantryCount, setPantryCount] = useState(null)
  const [savedCount, setSavedCount] = useState(null)

  useEffect(() => {
    let cancelled = false
    getPantryItems()
      .then((res) => !cancelled && setPantryCount(res.data.length))
      .catch(() => {})
    getSavedRecipes()
      .then((res) => !cancelled && setSavedCount(res.data.length))
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [])

  const actions = [
    {
      to: '/chef',
      icon: ChefHat,
      title: 'Chef Brain',
      description: 'Tell it what you have, or what you\u2019re craving.',
      badge: null,
    },
    {
      to: '/meal-plan',
      icon: CalendarDays,
      title: 'Meal Plan',
      description: 'A pantry-aware plan for the week ahead.',
      badge: null,
    },
    {
      to: '/pantry',
      icon: ShoppingBasket,
      title: 'Pantry',
      description: 'What Chef Brain knows you have on hand.',
      badge: pantryCount === null ? null : `${pantryCount} item${pantryCount === 1 ? '' : 's'}`,
    },
    {
      to: '/saved',
      icon: Bookmark,
      title: 'Saved Recipes',
      description: 'Everything you\u2019ve bookmarked, in one place.',
      badge: savedCount === null ? null : `${savedCount} saved`,
    },
  ]

  return (
    <div className="min-h-[calc(100vh-4rem)]">
      <section className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 pt-16 pb-12 sm:pt-20">
        <motion.span
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, ease: [0.22, 1, 0.36, 1] }}
          className="eyebrow inline-block"
        >
          Welcome back
        </motion.span>
        <motion.h1
          initial={{ opacity: 0, y: 14 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.55, delay: 0.05, ease: [0.22, 1, 0.36, 1] }}
          className="mt-3 font-display text-4xl sm:text-5xl font-semibold text-ink-800 tracking-tight"
        >
          {timeOfDayGreeting()}, <span className="italic text-brick-500">{firstName(userName)}.</span>
        </motion.h1>
        <motion.p
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.12, ease: [0.22, 1, 0.36, 1] }}
          className="mt-3 text-ink-500 max-w-md"
        >
          Where would you like to start?
        </motion.p>
      </section>

      <section className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 pb-20">
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {actions.map((action, index) => (
            <QuickActionCard key={action.to} index={index} {...action} />
          ))}
        </div>
      </section>
    </div>
  )
}

function HeroShowcase() {
  const [showcase, setShowcase] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    getShowcaseRecipe()
      .then((res) => {
        if (!cancelled && res.status === 200 && res.data?.imageUrl) setShowcase(res.data)
      })
      .catch(() => {})
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [])

  // Never render a broken/empty frame - if there's no real photo, the hero
  // simply stays single-column rather than showing a fake placeholder.
  if (!loading && !showcase) return null

  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.96 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ duration: 0.8, delay: 0.25, ease: [0.22, 1, 0.36, 1] }}
      className="relative hidden lg:block"
    >
      <div className="pointer-events-none absolute -z-10 -bottom-4 -right-4 w-full h-full rounded-sheet border border-brick-300/50" />
      <div className="relative aspect-[3/4] rounded-sheet overflow-hidden border border-ink-700/10 shadow-lift bg-paper-200">
        {loading || !showcase ? (
          <div className="w-full h-full animate-pulse bg-paper-300/70" />
        ) : (
          <>
            <img
              src={showcase.imageUrl}
              alt={showcase.title}
              className="w-full h-full object-cover"
            />
            <div className="absolute inset-0 bg-gradient-to-t from-ink-900/75 via-ink-900/5 to-transparent" />
            <div className="absolute bottom-0 left-0 right-0 p-5">
              <span className="text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-300">
                From the kitchen
              </span>
              <p className="font-display text-lg font-semibold text-paper-50 mt-1 leading-snug">
                {showcase.title}
              </p>
            </div>
          </>
        )}
      </div>
    </motion.div>
  )
}

function Home() {
  const { isAuthenticated, user } = useAuth()

  if (isAuthenticated) {
    return <AuthenticatedHome userName={user?.name} />
  }

  return (
    <div className="min-h-[calc(100vh-4rem)]">
      {/* Hero */}
      <section className="relative max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 pt-16 pb-20 sm:pt-24 sm:pb-28 overflow-hidden">
        <motion.div
          initial={{ opacity: 0, scale: 0.85 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 1.2, ease: [0.22, 1, 0.36, 1] }}
          className="pointer-events-none absolute -top-24 -right-24 w-64 sm:w-96 h-64 sm:h-96 rounded-full bg-brick-100/40 blur-3xl -z-10"
        />
        <div className="grid grid-cols-1 lg:grid-cols-[1.1fr_0.8fr] gap-12 lg:gap-16 items-center">
          <div>
            <motion.span
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.5, ease: [0.22, 1, 0.36, 1] }}
              className="eyebrow inline-block"
            >
              Harvest
            </motion.span>
            <h1 className="mt-4 font-display text-5xl sm:text-6xl lg:text-[4.25rem] font-semibold text-ink-800 leading-[1.05] tracking-tight overflow-hidden">
              <motion.span
                className="block"
                initial={{ y: '100%' }}
                animate={{ y: 0 }}
                transition={{ duration: 0.6, delay: 0.1, ease: [0.22, 1, 0.36, 1] }}
              >
                Cook with what
              </motion.span>
              <motion.span
                className="block"
                initial={{ y: '100%' }}
                animate={{ y: 0 }}
                transition={{ duration: 0.6, delay: 0.22, ease: [0.22, 1, 0.36, 1] }}
              >
                you <span className="italic text-brick-500">already have.</span>
              </motion.span>
            </h1>

            <motion.div
              initial={{ opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.32, ease: [0.22, 1, 0.36, 1] }}
              className="mt-6 max-w-md"
            >
              <p className="text-lg text-ink-600 leading-relaxed">
                No shopping list required. Harvest turns the ingredients already in
                your kitchen into a recipe worth cooking tonight.
              </p>
              <div className="mt-6 flex flex-wrap items-center gap-4">
                <Link to="/signup" className="btn-primary">
                  Start cooking
                  <ArrowRight className="w-4 h-4 ml-2" strokeWidth={1.75} />
                </Link>
                <Link to="/login" className="text-sm font-medium text-ink-600 hover:text-ink-800 transition-colors">
                  Already have an account?
                </Link>
              </div>
            </motion.div>
          </div>

          <HeroShowcase />
        </div>
      </section>

      {/* Capabilities */}
      <section className="border-t border-ink-700/10">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-16 sm:py-20">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-x-8 gap-y-12">
            {capabilities.map((item, index) => (
              <motion.div
                key={item.title}
                initial={{ opacity: 0, y: 16 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true, margin: '-60px' }}
                transition={{ duration: 0.5, delay: index * 0.08, ease: [0.22, 1, 0.36, 1] }}
                whileHover={{ y: -4 }}
                className="card-flat transition-transform duration-300 ease-quiet"
              >
                <span className="font-display text-sm text-brick-500/70">{item.index}</span>
                <h3 className="font-display text-xl font-semibold text-ink-800 mt-2 mb-2">
                  {item.title}
                </h3>
                <p className="text-sm text-ink-500 leading-relaxed">
                  {item.description}
                </p>
              </motion.div>
            ))}
          </div>
        </div>
      </section>
    </div>
  )
}

export default Home
