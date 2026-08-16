import { useState, useEffect, useCallback } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Bookmark, AlertCircle, X } from 'lucide-react'
import { getSavedRecipes, unsaveRecipe, getErrorMessage } from '../services/api'
import Loading from '../components/Loading'
import RecipeCard from '../components/RecipeCard'

function SavedRecipes() {
  const [saved, setSaved] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [removingId, setRemovingId] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const response = await getSavedRecipes()
      setSaved(response.data)
    } catch (err) {
      setError(getErrorMessage(err, 'Could not load your saved recipes. Please try again.'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const handleRemove = async (id) => {
    setRemovingId(id)
    const previous = saved
    setSaved((prev) => prev.filter((s) => s.id !== id))
    try {
      await unsaveRecipe(id)
    } catch (err) {
      setSaved(previous)
      setError(getErrorMessage(err, "Couldn't remove that recipe. Please try again."))
    } finally {
      setRemovingId(null)
    }
  }

  return (
    <div className="min-h-[calc(100vh-4rem)] max-w-3xl mx-auto px-4 sm:px-6 py-10">
      <div className="mb-8">
        <span className="eyebrow">Your collection</span>
        <h1 className="font-display text-3xl font-semibold text-ink-800 tracking-tight mt-1">
          Saved Recipes
        </h1>
        <p className="text-sm text-ink-500 mt-1">
          Recipes you&apos;ve bookmarked from Chef Brain or a meal plan.
        </p>
      </div>

      {error && (
        <div className="flex items-center gap-2 text-sm text-brick-400 bg-brick-50 border border-brick-100 rounded-sheet px-4 py-2.5 mb-6">
          <AlertCircle className="w-4 h-4 flex-shrink-0" strokeWidth={1.75} />
          {error}
        </div>
      )}

      {loading ? (
        <Loading />
      ) : saved.length === 0 ? (
        <div className="card text-center py-12">
          <div className="w-12 h-12 bg-paper-200 rounded-sheet flex items-center justify-center mx-auto mb-4">
            <Bookmark className="w-6 h-6 text-brick-400" strokeWidth={1.75} />
          </div>
          <p className="text-ink-700 font-medium mb-1">No saved recipes yet</p>
          <p className="text-sm text-ink-500 max-w-sm mx-auto">
            Tap the bookmark on any recipe Chef Brain shows you - in chat or in a meal plan -
            and it&apos;ll show up here.
          </p>
        </div>
      ) : (
        <AnimatePresence initial={false}>
          {saved.map((entry) => (
            <motion.div
              key={entry.id}
              layout
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, x: -8 }}
              transition={{ duration: 0.2 }}
              className="relative"
            >
              <button
                onClick={() => handleRemove(entry.id)}
                disabled={removingId === entry.id}
                aria-label={`Remove ${entry.recipe.title} from saved recipes`}
                className="absolute top-4 right-4 sm:right-6 z-20 flex-shrink-0 w-9 h-9 rounded-full bg-paper-50/90 border border-ink-700/15 text-ink-500 hover:text-brick-500 hover:border-brick-300 flex items-center justify-center transition-colors disabled:opacity-50"
              >
                <X className="w-4 h-4" strokeWidth={1.75} />
              </button>
              <RecipeCard recipe={entry.recipe} />
            </motion.div>
          ))}
        </AnimatePresence>
      )}
    </div>
  )
}

export default SavedRecipes
