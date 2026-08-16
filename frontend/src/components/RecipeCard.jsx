/* eslint-disable react/prop-types -- this project doesn't use PropTypes, see AuthContext.jsx */
import { useState } from 'react'
import { motion } from 'framer-motion'
import { Quote, Users, ShoppingBag, Check, Bookmark, Loader2 } from 'lucide-react'

const SOURCE_LABELS = {
  local: 'Harvest recipe',
  themealdb: 'TheMealDB',
  generated: 'Chef-generated',
}

/**
 * Renders a single recipe as an editorial recipe card, including why it was chosen.
 * `onToggleSave` is optional - pass it (plus `saved`) to show a bookmark toggle in the
 * corner; omit it entirely (e.g. on the Saved Recipes page, which uses its own remove
 * action) to render without one.
 */
function RecipeCard({ recipe, onToggleSave, saved = false, savePending = false }) {
  const [checked, setChecked] = useState(() => new Set())
  if (!recipe) return null
  const sourceLabel = SOURCE_LABELS[recipe.source] || recipe.source

  const toggleIngredient = (index) => {
    setChecked((prev) => {
      const next = new Set(prev)
      next.has(index) ? next.delete(index) : next.add(index)
      return next
    })
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1] }}
      className="relative mt-3 bg-paper-50 border border-ink-700/10 rounded-sheet overflow-hidden shadow-soft hover:shadow-lift transition-shadow duration-300 ease-quiet"
    >
      <div className="h-1 bg-gradient-to-r from-brick-500 via-brick-400 to-gold-300" />

      {onToggleSave && (
        <button
          type="button"
          onClick={onToggleSave}
          disabled={savePending}
          aria-pressed={saved}
          aria-label={saved ? 'Remove from saved recipes' : 'Save this recipe'}
          className={`absolute top-4 right-4 sm:right-6 z-10 flex-shrink-0 w-9 h-9 rounded-full flex items-center justify-center border transition-colors duration-150 disabled:opacity-50 ${
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
        </button>
      )}

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
    </motion.div>
  )
}

export default RecipeCard
