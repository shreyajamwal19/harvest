import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { ChefHat } from 'lucide-react'
import { getShowcaseRecipe } from '../services/api'

/**
 * Login and Signup were the two most generic-feeling screens in the app - a plain centered
 * card on a blank background, with none of the photo-forward editorial identity the rest of
 * Harvest has (the Home hero, RecipeCard's imagery). This gives them the same real-photo
 * treatment as Home's HeroShowcase, via the same public, unauthenticated, already-rate-limited
 * showcase endpoint - appropriate here since these pages are unauthenticated too.
 *
 * Unlike HeroShowcase (which is an optional bonus element inside a larger hero and simply
 * disappears if no photo loads), this panel is a structural half of a two-column page layout -
 * collapsing it would reflow the whole auth page oddly. So instead of hiding on failure, it
 * falls back to a plain warm panel with just the wordmark - honest (no invented photo) without
 * breaking the layout.
 */
function AuthShowcasePanel({ eyebrow }) {
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

  return (
    <div className="relative hidden lg:block h-full min-h-[calc(100vh-4rem)] overflow-hidden bg-ink-800">
      {loading ? (
        <div className="w-full h-full animate-pulse bg-ink-700/60" />
      ) : showcase ? (
        <motion.img
          key={showcase.imageUrl}
          initial={{ opacity: 0, scale: 1.06 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 1.1, ease: [0.22, 1, 0.36, 1] }}
          src={showcase.imageUrl}
          alt={showcase.title}
          className="w-full h-full object-cover"
        />
      ) : (
        <div className="w-full h-full flex items-center justify-center">
          <ChefHat className="w-8 h-8 text-paper-50/20" strokeWidth={1.5} />
        </div>
      )}

      <div className="absolute inset-0 bg-gradient-to-t from-ink-900/85 via-ink-900/15 to-ink-900/25" />

      <div className="absolute inset-0 flex flex-col justify-between p-10">
        <span className="font-display text-2xl font-semibold text-paper-50 tracking-tight">
          Harvest
        </span>
        {showcase && (
          <motion.div
            key={`${showcase.imageUrl}-caption`}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, delay: 0.3, ease: [0.22, 1, 0.36, 1] }}
          >
            <span className="text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-300">
              {eyebrow}
            </span>
            <p className="font-display text-3xl font-semibold text-paper-50 mt-2 leading-snug max-w-sm">
              {showcase.title}
            </p>
          </motion.div>
        )}
      </div>
    </div>
  )
}

export default AuthShowcasePanel
