import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { ChefHat, AlertCircle } from 'lucide-react'
import { getCookingHistory, getErrorMessage } from '../services/api'

function formatCookedDate(value) {
  try {
    return new Date(value).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
  } catch {
    return ''
  }
}

function capitalize(text) {
  return text ? text.charAt(0).toUpperCase() + text.slice(1) : text
}

function History() {
  const [entries, setEntries] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    getCookingHistory()
      .then((res) => {
        if (!cancelled) setEntries(res.data)
      })
      .catch((err) => {
        if (!cancelled) setError(getErrorMessage(err, "Couldn't load your cooking history."))
      })
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <div className="max-w-2xl mx-auto px-4 sm:px-6 py-10">
      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, ease: [0.22, 1, 0.36, 1] }}
        className="mb-8"
      >
        <h1 className="font-display text-2xl font-semibold text-ink-800 mb-1.5">Cooking history</h1>
        <p className="text-sm text-ink-500">Every dish you've marked as cooked, most recent first.</p>
      </motion.div>

      {error && (
        <div className="flex items-center gap-2 text-sm text-brick-600 bg-brick-50 border border-brick-200 rounded-sheet px-4 py-2.5 mb-6">
          <AlertCircle className="w-4 h-4 flex-shrink-0" strokeWidth={1.75} />
          {error}
        </div>
      )}

      {entries === null ? (
        <div className="space-y-2">
          {[0, 1, 2, 3].map((i) => (
            <div key={i} className="h-14 bg-paper-200/70 rounded-sheet animate-pulse" />
          ))}
        </div>
      ) : entries.length === 0 ? (
        <div className="text-center py-16">
          <div className="w-12 h-12 bg-paper-200 rounded-sheet flex items-center justify-center mx-auto mb-4">
            <ChefHat className="w-5 h-5 text-ink-400" strokeWidth={1.75} />
          </div>
          <h3 className="font-display text-lg font-semibold text-ink-700 mb-1.5">Nothing cooked yet</h3>
          <p className="text-sm text-ink-500 max-w-xs mx-auto">
            Finish a recipe in Cook Mode and it'll show up here.
          </p>
        </div>
      ) : (
        <ul className="space-y-2">
          {entries.map((entry, index) => (
            <motion.li
              key={entry.id}
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.25, delay: Math.min(index * 0.03, 0.3), ease: [0.22, 1, 0.36, 1] }}
              className="flex items-center gap-3 px-4 py-3 bg-paper-50 border border-ink-700/10 rounded-sheet"
            >
              <span className="flex-shrink-0 w-8 h-8 rounded-full bg-moss-100 text-moss-600 flex items-center justify-center">
                <ChefHat className="w-3.5 h-3.5" strokeWidth={1.75} />
              </span>
              <span className="flex-1 text-sm font-medium text-ink-800 truncate">
                {capitalize(entry.recipeTitle)}
              </span>
              <span className="flex-shrink-0 text-xs text-ink-400">{formatCookedDate(entry.cookedAt)}</span>
            </motion.li>
          ))}
        </ul>
      )}
    </div>
  )
}

export default History
