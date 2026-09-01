/* eslint-disable react/prop-types -- this project doesn't use PropTypes, see AuthContext.jsx */
import { useState, useRef, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import { Quote, Users, ShoppingBag, Check, Bookmark, Loader2, ChefHat, Sparkle, Share2 } from 'lucide-react'

const SOURCE_LABELS = {
  local: 'Harvest recipe',
  themealdb: 'TheMealDB',
  generated: 'Chef-generated',
}

// Small burst of sparkles fired from the bookmark button the instant a recipe is saved.
const BURST_PARTICLES = Array.from({ length: 6 }).map((_, i) => {
  const angle = (i / 6) * Math.PI * 2 - Math.PI / 2
  const radius = 26
  return {
    x: Math.cos(angle) * radius,
    y: Math.sin(angle) * radius,
    delay: i * 0.02,
    tone: i % 2 === 0 ? 'text-gold-400' : 'text-brick-400',
  }
})

/**
 * Renders a single recipe as an editorial recipe card, including why it was chosen.
 * `onToggleSave` is optional - pass it (plus `saved`) to show a bookmark toggle in the
 * corner; omit it entirely (e.g. on the Saved Recipes page, which uses its own remove
 * action) to render without one.
 */
function RecipeCard({ recipe, onToggleSave, saved = false, savePending = false }) {
  const [checked, setChecked] = useState(() => new Set())
  const [burst, setBurst] = useState(false)
  const [imageFailed, setImageFailed] = useState(false)
  const [shareState, setShareState] = useState('idle') // 'idle' | 'copied'
  const wasSavedRef = useRef(saved)
  const navigate = useNavigate()

  useEffect(() => {
    if (saved && !wasSavedRef.current) {
      setBurst(true)
      const timer = setTimeout(() => setBurst(false), 550)
      wasSavedRef.current = saved
      return () => clearTimeout(timer)
    }
    wasSavedRef.current = saved
  }, [saved])

  if (!recipe) return null
  const sourceLabel = SOURCE_LABELS[recipe.source] || recipe.source

  const toggleIngredient = (index) => {
    setChecked((prev) => {
      const next = new Set(prev)
      next.has(index) ? next.delete(index) : next.add(index)
      return next
    })
  }

  const startCooking = () => {
    navigate('/cook', { state: { recipe } })
  }

  // Native share sheet where available (mobile browsers, some desktop); falls back to
  // copying a plain-text version to the clipboard everywhere else. No backend involved -
  // this is purely "hand the person their own recipe data in a portable form".
  const shareRecipe = async () => {
    const lines = [
      recipe.title,
      recipe.description || '',
      '',
      'Ingredients:',
      ...(recipe.ingredients || []).map((i) => `- ${i}`),
      '',
      'Steps:',
      ...(recipe.steps || []).map((s, i) => `${i + 1}. ${s}`),
    ]
    const text = lines.join('\n')

    if (navigator.share) {
      try {
        await navigator.share({ title: recipe.title, text })
      } catch {
        // Person dismissed the native share sheet - not an error worth surfacing.
      }
      return
    }

    try {
      await navigator.clipboard.writeText(text)
      setShareState('copied')
      setTimeout(() => setShareState('idle'), 1800)
    } catch {
      // Clipboard write can fail (permissions, insecure context) - fail quietly rather than
      // showing an error banner for what's a nice-to-have convenience action.
    }
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1] }}
      className="relative mt-3 bg-paper-50 border border-ink-700/10 rounded-sheet overflow-hidden shadow-soft hover:shadow-lift transition-shadow duration-300 ease-quiet"
    >
      {recipe.imageUrl && !imageFailed ? (
        <div className="relative w-full aspect-[16/9] overflow-hidden bg-paper-200">
          <img
            src={recipe.imageUrl}
            alt={recipe.title}
            onError={() => setImageFailed(true)}
            loading="lazy"
            className="w-full h-full object-cover"
          />
          <div className="absolute inset-0 bg-gradient-to-t from-ink-900/55 via-ink-900/0 to-transparent" />
          <div className="absolute bottom-0 left-0 right-0 h-1 bg-gradient-to-r from-brick-500 via-brick-400 to-gold-300" />
        </div>
      ) : (
        <div className="h-1 bg-gradient-to-r from-brick-500 via-brick-400 to-gold-300" />
      )}

      <div className="absolute top-4 right-4 sm:right-6 z-10 flex items-center gap-2">
        {onToggleSave && (
          <>
            <AnimatePresence>
              {burst && (
                <motion.div className="absolute inset-0 pointer-events-none">
                  {BURST_PARTICLES.map((p, i) => (
                    <motion.span
                      key={i}
                      initial={{ x: 0, y: 0, opacity: 1, scale: 0.4 }}
                      animate={{ x: p.x, y: p.y, opacity: 0, scale: 1 }}
                      exit={{ opacity: 0 }}
                      transition={{ duration: 0.5, delay: p.delay, ease: 'easeOut' }}
                      className={`absolute top-1/2 left-1/2 ${p.tone}`}
                    >
                      <Sparkle className="w-3 h-3 -translate-x-1/2 -translate-y-1/2" fill="currentColor" strokeWidth={0} />
                    </motion.span>
                  ))}
                </motion.div>
              )}
            </AnimatePresence>
            <motion.button
              type="button"
              onClick={onToggleSave}
              disabled={savePending}
              aria-pressed={saved}
              aria-label={saved ? 'Remove from saved recipes' : 'Save this recipe'}
              animate={burst ? { scale: [1, 1.28, 1] } : { scale: 1 }}
              transition={{ duration: 0.4, ease: [0.34, 1.56, 0.64, 1] }}
              className={`flex-shrink-0 w-9 h-9 rounded-full flex items-center justify-center border transition-colors duration-150 disabled:opacity-50 ${
                saved
                  ? 'bg-brick-500 border-brick-500 text-paper-50'
                  : 'bg-paper-50/90 border-ink-700/15 text-ink-500 hover:text-brick-500 hover:border-brick-300'
              }`}
            >
              {savePending ? (
                <Loader2 className="w-4 h-4 animate-spin" strokeWidth={2} />
              ) : (
                <Bookmark className="w-4 h-4" strokeWidth={2} fill={saved ? 'currentColor' : 'none'} />
              )}
            </motion.button>
          </>
        )}
        <button
          type="button"
          onClick={shareRecipe}
          aria-label={shareState === 'copied' ? 'Recipe copied to clipboard' : 'Share this recipe'}
          className="flex-shrink-0 w-9 h-9 rounded-full flex items-center justify-center border bg-paper-50/90 border-ink-700/15 text-ink-500 hover:text-brick-500 hover:border-brick-300 transition-colors duration-150"
        >
          {shareState === 'copied' ? (
            <Check className="w-4 h-4" strokeWidth={2} />
          ) : (
            <Share2 className="w-4 h-4" strokeWidth={1.75} />
          )}
        </button>
      </div>

      <div className="px-5 sm:px-7 pt-6 pb-5">
        <div className="flex items-start justify-between gap-3 flex-wrap pr-10">
          <h3 className="font-display text-2xl sm:text-[1.75rem] font-semibold text-ink-800 leading-[1.15] tracking-tight">
            {recipe.title}
          </h3>
          {sourceLabel && (
            <span className="flex-shrink-0 eyebrow text-[10px] mt-1.5">{sourceLabel}</span>
          )}
        </div>

        {recipe.description && (
          <p className="text-sm text-ink-500 mt-2.5 leading-relaxed max-w-lg">{recipe.description}</p>
        )}

        {recipe.servings && (
          <div className="flex items-center gap-1.5 mt-4 text-xs font-medium text-ink-500">
            <Users className="w-3.5 h-3.5 text-brick-400" strokeWidth={1.75} />
            Serves {recipe.servings}
          </div>
        )}
      </div>

      {recipe.rationale && (
        <div className="mx-5 sm:mx-7 mb-6 flex gap-3 rounded-sheet bg-gold-100/40 border border-gold-300/40 px-4 py-3.5">
          <Quote className="w-4 h-4 text-gold-500 flex-shrink-0 mt-0.5" strokeWidth={2} fill="currentColor" fillOpacity={0.15} />
          <p className="text-sm text-ink-700 italic leading-relaxed">{recipe.rationale}</p>
        </div>
      )}

      {recipe.missingIngredients?.length > 0 && (
        <div className="mx-5 sm:mx-7 mb-6 flex flex-wrap items-center gap-2">
          <span className="flex items-center gap-1.5 text-xs font-semibold text-brick-600 uppercase tracking-wide">
            <ShoppingBag className="w-3.5 h-3.5" strokeWidth={1.75} />
            Pick up
          </span>
          {recipe.missingIngredients.map((item, i) => (
            <span key={i} className="text-xs font-medium text-brick-700 bg-brick-50 border border-brick-100 rounded-full px-2.5 py-1">
              {item}
            </span>
          ))}
        </div>
      )}

      <div className="px-5 sm:px-7 pb-6 grid grid-cols-1 sm:grid-cols-[1fr_1.5fr] gap-6 sm:gap-8">
        {recipe.ingredients?.length > 0 && (
          <div>
            <h4 className="eyebrow mb-3">Ingredients</h4>
            <ul className="space-y-2">
              {recipe.ingredients.map((ingredient, index) => {
                const isChecked = checked.has(index)
                return (
                  <li key={index}>
                    <button
                      type="button"
                      onClick={() => toggleIngredient(index)}
                      aria-pressed={isChecked}
                      className="group flex items-start gap-2.5 text-left w-full"
                    >
                      <span
                        className={`mt-0.5 flex-shrink-0 w-4 h-4 rounded-full border flex items-center justify-center transition-colors duration-150 ${
                          isChecked ? 'bg-moss-500 border-moss-500' : 'border-ink-700/25 group-hover:border-brick-400'
                        }`}
                      >
                        {isChecked && <Check className="w-2.5 h-2.5 text-paper-50" strokeWidth={3} />}
                      </span>
                      <span
                        className={`text-sm leading-snug transition-colors duration-150 ${
                          isChecked ? 'text-ink-400 line-through' : 'text-ink-700'
                        }`}
                      >
                        {ingredient}
                      </span>
                    </button>
                  </li>
                )
              })}
            </ul>
          </div>
        )}

        {recipe.steps?.length > 0 && (
          <div>
            <h4 className="eyebrow mb-3">Method</h4>
            <ol className="relative">
              <span className="absolute left-[13px] top-2 bottom-2 w-px bg-ink-700/10" />
              {recipe.steps.map((step, index) => (
                <li key={index} className="relative flex gap-3.5 pb-4 last:pb-0">
                  <span className="relative z-10 flex-shrink-0 w-[26px] h-[26px] rounded-full bg-paper-100 border border-brick-300 text-brick-600 font-display text-[13px] font-semibold flex items-center justify-center">
                    {index + 1}
                  </span>
                  <span className="text-sm text-ink-700 leading-relaxed pt-0.5">{step}</span>
                </li>
              ))}
            </ol>
          </div>
        )}
      </div>

      {recipe.notes && (
        <div className="mx-5 sm:mx-7 mb-6 border-t border-dashed border-ink-700/15 pt-4">
          <p className="text-xs text-ink-500 italic leading-relaxed">
            <span className="not-italic font-semibold text-ink-600">Note — </span>
            {recipe.notes}
          </p>
        </div>
      )}

      {recipe.steps?.length > 0 && (
        <div className="mx-5 sm:mx-7 mb-6">
          <button
            type="button"
            onClick={startCooking}
            className="w-full flex items-center justify-center gap-2 text-sm font-semibold text-paper-50 bg-ink-800 hover:bg-ink-700 rounded-sheet py-3 transition-colors"
          >
            <ChefHat className="w-4 h-4" strokeWidth={1.75} />
            Start Cooking
          </button>
        </div>
      )}
    </motion.div>
  )
}

export default RecipeCard
