/* eslint-disable react/prop-types -- this project doesn't use PropTypes, see AuthContext.jsx */
import { useState, useMemo } from 'react'
import { useLocation, Link } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import {
  ChevronLeft,
  ChevronRight,
  X,
  Check,
  ChefHat,
  ListChecks,
  PartyPopper,
} from 'lucide-react'
import { markRecipeCooked } from '../services/api'

function ProgressDots({ total, current }) {
  return (
    <div className="flex items-center gap-1.5">
      {Array.from({ length: total }).map((_, i) => (
        <span
          key={i}
          className={`h-1.5 rounded-full transition-all duration-200 ${
            i === current ? 'w-6 bg-brick-500' : i < current ? 'w-1.5 bg-brick-300' : 'w-1.5 bg-ink-700/15'
          }`}
        />
      ))}
    </div>
  )
}

function CookMode() {
  const location = useLocation()
  const recipe = location.state?.recipe

  const [stepIndex, setStepIndex] = useState(0)
  const [ingredientsOpen, setIngredientsOpen] = useState(false)
  const [checkedIngredients, setCheckedIngredients] = useState(() => new Set())
  const [finished, setFinished] = useState(false)
  const [markingCooked, setMarkingCooked] = useState(false)

  const steps = useMemo(() => recipe?.steps || [], [recipe])
  const isLastStep = stepIndex === steps.length - 1

  if (!recipe || steps.length === 0) {
    return (
      <div className="min-h-[calc(100vh-4rem)] max-w-lg mx-auto px-4 sm:px-6 py-16 text-center">
        <div className="w-12 h-12 bg-paper-200 rounded-sheet flex items-center justify-center mx-auto mb-4">
          <ChefHat className="w-6 h-6 text-ink-400" strokeWidth={1.75} />
        </div>
        <p className="text-ink-700 font-medium mb-1">No recipe to cook</p>
        <p className="text-sm text-ink-500 mb-6">
          Cooking Mode opens from a recipe&apos;s &ldquo;Start Cooking&rdquo; button - it
          doesn&apos;t work from a direct link or a page refresh.
        </p>
        <Link to="/chef" className="btn-primary inline-flex">
          Back to Chef Brain
        </Link>
      </div>
    )
  }

  const toggleIngredient = (index) => {
    setCheckedIngredients((prev) => {
      const next = new Set(prev)
      next.has(index) ? next.delete(index) : next.add(index)
      return next
    })
  }

  const goNext = () => {
    if (isLastStep) return
    setStepIndex((i) => Math.min(i + 1, steps.length - 1))
  }
  const goPrev = () => setStepIndex((i) => Math.max(i - 1, 0))

  const finish = async () => {
    setMarkingCooked(true)
    try {
      await markRecipeCooked(recipe.title)
    } catch {
      // Not marking history is a quiet personalization miss, not something worth
      // surfacing mid-completion - the person still finished cooking either way.
    } finally {
      setMarkingCooked(false)
      setFinished(true)
    }
  }

  if (finished) {
    return (
      <div className="min-h-[calc(100vh-4rem)] max-w-lg mx-auto px-4 sm:px-6 py-16 text-center flex flex-col items-center">
        <div className="relative mb-5">
          <motion.span
            className="absolute inset-0 rounded-full bg-moss-300/40"
            initial={{ opacity: 0.6, scale: 1 }}
            animate={{ opacity: 0, scale: 1.9 }}
            transition={{ duration: 1.4, ease: [0.22, 1, 0.36, 1] }}
          />
          <motion.div
            initial={{ opacity: 0, scale: 0.7 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ duration: 0.5, ease: [0.22, 1, 0.36, 1] }}
            className="relative w-14 h-14 bg-moss-100 rounded-full flex items-center justify-center"
          >
            <PartyPopper className="w-7 h-7 text-moss-500" strokeWidth={1.75} />
          </motion.div>
        </div>
        <motion.h1
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.45, delay: 0.15, ease: [0.22, 1, 0.36, 1] }}
          className="font-display text-2xl font-semibold text-ink-800 mb-2"
        >
          {recipe.title} - done!
        </motion.h1>
        <motion.p
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.45, delay: 0.22, ease: [0.22, 1, 0.36, 1] }}
          className="text-sm text-ink-500 mb-8 max-w-xs"
        >
          Nicely done. Chef Brain will remember this so it can mix things up next time.
        </motion.p>
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.45, delay: 0.3, ease: [0.22, 1, 0.36, 1] }}
          className="flex flex-col sm:flex-row gap-3 w-full sm:w-auto"
        >
          <Link to="/chef" className="btn-primary">
            Back to Chef Brain
          </Link>
          <Link to="/saved" className="btn-secondary">
            Saved Recipes
          </Link>
        </motion.div>
      </div>
    )
  }

  return (
    <div className="min-h-[calc(100vh-4rem)] max-w-lg mx-auto px-4 sm:px-6 py-8 flex flex-col">
      <div className="flex items-center justify-between gap-3 mb-6">
        <div className="min-w-0">
          <span className="eyebrow">Step {stepIndex + 1} of {steps.length}</span>
          <h1 className="font-display text-xl font-semibold text-ink-800 truncate mt-0.5">
            {recipe.title}
          </h1>
        </div>
        <Link
          to="/chef"
          aria-label="Exit cooking mode"
          className="flex-shrink-0 p-2 rounded-full text-ink-400 hover:text-ink-700 hover:bg-paper-200 transition-colors"
        >
          <X className="w-5 h-5" strokeWidth={1.75} />
        </Link>
      </div>

      <ProgressDots total={steps.length} current={stepIndex} />

      {recipe.ingredients?.length > 0 && (
        <div className="mt-5">
          <button
            onClick={() => setIngredientsOpen((v) => !v)}
            className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.14em] text-ink-500 hover:text-ink-700 transition-colors"
          >
            <ListChecks className="w-3.5 h-3.5" strokeWidth={1.75} />
            Ingredients ({checkedIngredients.size}/{recipe.ingredients.length})
          </button>
          <AnimatePresence initial={false}>
            {ingredientsOpen && (
              <motion.ul
                initial={{ height: 0, opacity: 0 }}
                animate={{ height: 'auto', opacity: 1 }}
                exit={{ height: 0, opacity: 0 }}
                className="overflow-hidden mt-3 space-y-1.5"
              >
                {recipe.ingredients.map((ing, i) => {
                  const isChecked = checkedIngredients.has(i)
                  return (
                    <li key={i}>
                      <button
                        onClick={() => toggleIngredient(i)}
                        className="w-full flex items-center gap-2.5 text-left px-3 py-2 rounded-sheet hover:bg-paper-100 transition-colors"
                      >
                        <span
                          className={`flex-shrink-0 w-4 h-4 rounded-full border flex items-center justify-center transition-colors ${
                            isChecked ? 'bg-moss-500 border-moss-500' : 'border-ink-700/25'
                          }`}
                        >
                          {isChecked && <Check className="w-2.5 h-2.5 text-paper-50" strokeWidth={3} />}
                        </span>
                        <span className={`text-sm ${isChecked ? 'text-ink-400 line-through' : 'text-ink-700'}`}>
                          {ing}
                        </span>
                      </button>
                    </li>
                  )
                })}
              </motion.ul>
            )}
          </AnimatePresence>
        </div>
      )}

      <div className="flex-1 flex items-center justify-center py-10">
        <AnimatePresence mode="wait">
          <motion.p
            key={stepIndex}
            initial={{ opacity: 0, x: 16 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: -16 }}
            transition={{ duration: 0.22, ease: [0.22, 1, 0.36, 1] }}
            className="font-display text-2xl sm:text-3xl text-ink-800 leading-snug text-center"
          >
            {steps[stepIndex]}
          </motion.p>
        </AnimatePresence>
      </div>

      <div className="flex items-center gap-3">
        <button
          onClick={goPrev}
          disabled={stepIndex === 0}
          className="flex items-center justify-center gap-1.5 flex-1 py-3 rounded-sheet text-sm font-medium text-ink-600 bg-paper-100 hover:bg-paper-200 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
        >
          <ChevronLeft className="w-4 h-4" strokeWidth={1.75} />
          Back
        </button>
        {isLastStep ? (
          <button
            onClick={finish}
            disabled={markingCooked}
            className="btn-primary flex-[2] flex items-center justify-center gap-2 disabled:opacity-60"
          >
            <Check className="w-4 h-4" strokeWidth={1.75} />
            Finish Cooking
          </button>
        ) : (
          <button
            onClick={goNext}
            className="btn-primary flex-[2] flex items-center justify-center gap-1.5"
          >
            Next
            <ChevronRight className="w-4 h-4" strokeWidth={1.75} />
          </button>
        )}
      </div>
    </div>
  )
}

export default CookMode
