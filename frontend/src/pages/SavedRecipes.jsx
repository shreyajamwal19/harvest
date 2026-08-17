/* eslint-disable react/prop-types -- this project doesn't use PropTypes, see AuthContext.jsx */
import { useState, useEffect, useCallback, useMemo, useRef } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Bookmark, BookmarkX, AlertCircle, X, ChevronDown, Search, SearchX } from 'lucide-react'
import { getSavedRecipes, unsaveRecipe, getErrorMessage } from '../services/api'
import Loading from '../components/Loading'
import RecipeCard from '../components/RecipeCard'
import Toast from '../components/Toast'

const SORT_OPTIONS = [
  { value: 'newest', label: 'Newest' },
  { value: 'az', label: 'A–Z' },
]

function formatSavedDate(value) {
  try {
    return new Date(value).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
  } catch {
    return ''
  }
}

function SavedRecipeItem({ entry, expanded, onToggleExpand, onRemove, removing }) {
  const { recipe } = entry
  const summary = recipe.description || `${recipe.ingredients?.length || 0} ingredients · ${recipe.steps?.length || 0} steps`

  return (
    <motion.div
      layout
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, x: -8 }}
      transition={{ duration: 0.2 }}
      className="card !p-0 overflow-hidden mb-3"
    >
      <div className="flex items-center gap-2 pr-3">
        <button
          onClick={onToggleExpand}
          aria-expanded={expanded}
          className="flex-1 min-w-0 flex items-center gap-3 px-5 py-4 text-left"
        >
          <span className="flex-shrink-0 w-9 h-9 rounded-full bg-brick-50 text-brick-500 flex items-center justify-center">
            <Bookmark className="w-4 h-4" strokeWidth={0} fill="currentColor" />
          </span>
          <span className="min-w-0 flex-1">
            <p className="font-display text-base font-semibold text-ink-800 truncate">
              {recipe.title}
            </p>
            <p className="text-xs text-ink-500 truncate">{summary}</p>
          </span>
          <span className="hidden sm:block flex-shrink-0 text-[11px] text-ink-400">
            {formatSavedDate(entry.savedAt)}
          </span>
          <ChevronDown
            className={`flex-shrink-0 w-4 h-4 text-ink-400 transition-transform duration-200 ${expanded ? 'rotate-180' : ''}`}
            strokeWidth={1.75}
          />
        </button>
        <button
          onClick={onRemove}
          disabled={removing}
          aria-label={`Remove ${recipe.title} from saved recipes`}
          className="flex-shrink-0 w-8 h-8 rounded-full text-ink-400 hover:text-brick-500 hover:bg-brick-50 flex items-center justify-center transition-colors disabled:opacity-50"
        >
          <X className="w-4 h-4" strokeWidth={1.75} />
        </button>
      </div>

      <AnimatePresence initial={false}>
        {expanded && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.22, ease: [0.22, 1, 0.36, 1] }}
            className="overflow-hidden border-t border-ink-700/10"
          >
            <div className="[&>div]:mt-0 [&>div]:rounded-none [&>div]:border-0 [&>div]:shadow-none">
              <RecipeCard recipe={recipe} />
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  )
}

function SavedRecipes() {
  const [saved, setSaved] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [removingId, setRemovingId] = useState(null)

  const [query, setQuery] = useState('')
  const [sortMode, setSortMode] = useState('newest')
  const [expandedIds, setExpandedIds] = useState(() => new Set())

  const [toast, setToast] = useState(null)
  const toastTimerRef = useRef(null)
  const showToast = useCallback((data) => {
    clearTimeout(toastTimerRef.current)
    setToast({ id: Date.now(), ...data })
    toastTimerRef.current = setTimeout(() => setToast(null), 3200)
  }, [])
  useEffect(() => () => clearTimeout(toastTimerRef.current), [])

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

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    let list = saved
    if (q) {
      list = list.filter(
        (s) =>
          s.recipe.title?.toLowerCase().includes(q) ||
          s.recipe.description?.toLowerCase().includes(q)
      )
    }
    if (sortMode === 'az') {
      list = [...list].sort((a, b) => (a.recipe.title || '').localeCompare(b.recipe.title || ''))
    }
    return list
  }, [saved, query, sortMode])

  const toggleExpand = (id) => {
    setExpandedIds((prev) => {
      const next = new Set(prev)
      next.has(id) ? next.delete(id) : next.add(id)
      return next
    })
  }

  const handleRemove = async (entry) => {
    setRemovingId(entry.id)
    const previous = saved
    setSaved((prev) => prev.filter((s) => s.id !== entry.id))
    try {
      await unsaveRecipe(entry.id)
      showToast({
        tone: 'neutral',
        icon: <BookmarkX className="w-4 h-4 text-paper-50" strokeWidth={2} />,
        title: 'Removed from saved recipes',
        subtitle: entry.recipe.title,
      })
    } catch (err) {
      setSaved(previous)
      setError(getErrorMessage(err, "Couldn't remove that recipe. Please try again."))
    } finally {
      setRemovingId(null)
    }
  }

  return (
    <div className="min-h-[calc(100vh-4rem)] max-w-3xl mx-auto px-4 sm:px-6 py-10">
      <div className="mb-6">
        <span className="eyebrow">Your collection</span>
        <h1 className="font-display text-3xl font-semibold text-ink-800 tracking-tight mt-1">
          Saved Recipes
        </h1>
        <p className="text-sm text-ink-500 mt-1">
          Recipes you&apos;ve bookmarked from Chef Brain or a meal plan.
        </p>
      </div>

      {saved.length > 0 && (
        <div className="flex flex-col sm:flex-row sm:items-center gap-3 mb-6">
          <div className="relative flex-1">
            <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-ink-400" strokeWidth={1.75} />
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search your saved recipes…"
              className="input-field pl-10"
            />
          </div>
          <div className="flex gap-2 flex-shrink-0">
            {SORT_OPTIONS.map((opt) => (
              <button
                key={opt.value}
                onClick={() => setSortMode(opt.value)}
                className={`px-3.5 py-2 rounded-sheet text-xs font-semibold transition-colors ${
                  sortMode === opt.value
                    ? 'bg-moss-400 text-white'
                    : 'bg-paper-100 text-ink-600 hover:bg-paper-200'
                }`}
              >
                {opt.label}
              </button>
            ))}
          </div>
        </div>
      )}

      {saved.length > 0 && (
        <p className="text-xs text-ink-400 mb-3">
          {filtered.length} of {saved.length} recipe{saved.length === 1 ? '' : 's'}
        </p>
      )}

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
      ) : filtered.length === 0 ? (
        <div className="card text-center py-12">
          <div className="w-12 h-12 bg-paper-200 rounded-sheet flex items-center justify-center mx-auto mb-4">
            <SearchX className="w-6 h-6 text-ink-400" strokeWidth={1.75} />
          </div>
          <p className="text-ink-700 font-medium mb-1">No matches for &ldquo;{query}&rdquo;</p>
          <button
            onClick={() => setQuery('')}
            className="text-sm text-brick-500 font-medium hover:text-brick-600 transition-colors"
          >
            Clear search
          </button>
        </div>
      ) : (
        <AnimatePresence initial={false}>
          {filtered.map((entry) => (
            <SavedRecipeItem
              key={entry.id}
              entry={entry}
              expanded={expandedIds.has(entry.id)}
              onToggleExpand={() => toggleExpand(entry.id)}
              onRemove={() => handleRemove(entry)}
              removing={removingId === entry.id}
            />
          ))}
        </AnimatePresence>
      )}

      <Toast toast={toast} onDismiss={() => setToast(null)} />
    </div>
  )
}

export default SavedRecipes
