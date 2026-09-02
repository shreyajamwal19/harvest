/* eslint-disable react/prop-types -- this project doesn't use PropTypes, see AuthContext.jsx */
import { useState, useEffect, useMemo } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Sparkles, X, AlertCircle, Trash2, RotateCcw } from 'lucide-react'
import { getPreferences, deletePreference, resetPreferences, getErrorMessage } from '../services/api'
import { preferenceCategoryLabel } from '../utils/preferenceCategories'

function PreferenceChip({ preference, onRemove, removing }) {
  return (
    <motion.li
      layout
      initial={{ opacity: 0, scale: 0.9 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 0.9 }}
      transition={{ duration: 0.2, ease: [0.22, 1, 0.36, 1] }}
      className="flex items-center gap-2 bg-paper-100 border border-ink-700/10 rounded-full pl-3.5 pr-1.5 py-1.5"
    >
      <span className="text-sm text-ink-700 capitalize">{preference.value}</span>
      {/* A thin confidence bar rather than a raw number - "how sure Harvest is" reads more
          naturally as a fill level than a percentage that invites over-interpretation. */}
      <span className="w-8 h-1 rounded-full bg-ink-700/10 overflow-hidden" aria-hidden="true">
        <span
          className="block h-full bg-moss-400 rounded-full"
          style={{ width: `${Math.round(preference.confidence * 100)}%` }}
        />
      </span>
      <button
        type="button"
        onClick={() => onRemove(preference.id)}
        disabled={removing}
        aria-label={`Forget "${preference.value}"`}
        className="flex-shrink-0 w-5 h-5 rounded-full flex items-center justify-center text-ink-400 hover:text-brick-500 hover:bg-brick-50 transition-colors disabled:opacity-40"
      >
        <X className="w-3 h-3" strokeWidth={2} />
      </button>
    </motion.li>
  )
}

function Settings() {
  const [preferences, setPreferences] = useState(null)
  const [error, setError] = useState('')
  const [removingId, setRemovingId] = useState(null)
  const [resetting, setResetting] = useState(false)

  useEffect(() => {
    let cancelled = false
    getPreferences()
      .then((res) => {
        if (!cancelled) setPreferences(res.data)
      })
      .catch((err) => {
        if (!cancelled) setError(getErrorMessage(err, "Couldn't load your preferences."))
      })
    return () => {
      cancelled = true
    }
  }, [])

  const grouped = useMemo(() => {
    if (!preferences) return []
    const byCategory = new Map()
    for (const pref of preferences) {
      if (!byCategory.has(pref.category)) byCategory.set(pref.category, [])
      byCategory.get(pref.category).push(pref)
    }
    return [...byCategory.entries()].sort((a, b) => b[1].length - a[1].length)
  }, [preferences])

  const handleRemove = async (preferenceId) => {
    setRemovingId(preferenceId)
    const previous = preferences
    setPreferences((prev) => prev.filter((p) => p.id !== preferenceId))
    try {
      await deletePreference(preferenceId)
    } catch (err) {
      setPreferences(previous)
      setError(getErrorMessage(err, "Couldn't forget that preference. Please try again."))
    } finally {
      setRemovingId(null)
    }
  }

  const handleResetAll = async () => {
    if (!preferences || preferences.length === 0) return
    if (!window.confirm('Forget everything Harvest has learned about you? This cannot be undone.')) return
    setResetting(true)
    const previous = preferences
    setPreferences([])
    try {
      await resetPreferences()
    } catch (err) {
      setPreferences(previous)
      setError(getErrorMessage(err, "Couldn't reset your preferences. Please try again."))
    } finally {
      setResetting(false)
    }
  }

  return (
    <div className="max-w-2xl mx-auto px-4 sm:px-6 py-10">
      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, ease: [0.22, 1, 0.36, 1] }}
        className="mb-8"
      >
        <h1 className="font-display text-2xl font-semibold text-ink-800 mb-1.5">What Harvest knows</h1>
        <p className="text-sm text-ink-500 max-w-md">
          Everything Chef Brain has picked up from your conversations - the same things you could
          ask it to "forget" in chat, just easier to see all at once.
        </p>
      </motion.div>

      {error && (
        <div className="flex items-center gap-2 text-sm text-brick-600 bg-brick-50 border border-brick-200 rounded-sheet px-4 py-2.5 mb-6">
          <AlertCircle className="w-4 h-4 flex-shrink-0" strokeWidth={1.75} />
          {error}
        </div>
      )}

      {preferences === null ? (
        <div className="space-y-3">
          {[0, 1, 2].map((i) => (
            <div key={i} className="h-16 bg-paper-200/70 rounded-sheet animate-pulse" />
          ))}
        </div>
      ) : preferences.length === 0 ? (
        <div className="text-center py-16">
          <div className="w-12 h-12 bg-paper-200 rounded-sheet flex items-center justify-center mx-auto mb-4">
            <Sparkles className="w-5 h-5 text-ink-400" strokeWidth={1.75} />
          </div>
          <h3 className="font-display text-lg font-semibold text-ink-700 mb-1.5">Nothing learned yet</h3>
          <p className="text-sm text-ink-500 max-w-xs mx-auto">
            Chat with Chef Brain a little and it'll start picking up your tastes automatically.
          </p>
        </div>
      ) : (
        <>
          <div className="space-y-6 mb-8">
            {grouped.map(([category, items]) => (
              <motion.div
                key={category}
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.3, ease: [0.22, 1, 0.36, 1] }}
              >
                <h2 className="text-xs font-semibold uppercase tracking-[0.08em] text-ink-500 mb-2.5">
                  {preferenceCategoryLabel(category)}
                </h2>
                <ul className="flex flex-wrap gap-2">
                  <AnimatePresence>
                    {items.map((pref) => (
                      <PreferenceChip
                        key={pref.id}
                        preference={pref}
                        onRemove={handleRemove}
                        removing={removingId === pref.id}
                      />
                    ))}
                  </AnimatePresence>
                </ul>
              </motion.div>
            ))}
          </div>

          <div className="pt-6 border-t border-ink-700/10">
            <button
              type="button"
              onClick={handleResetAll}
              disabled={resetting}
              className="flex items-center gap-1.5 text-xs font-medium text-brick-400 hover:text-brick-500 hover:bg-brick-50 rounded-sheet px-3 py-2 transition-colors disabled:opacity-50"
            >
              {resetting ? (
                <RotateCcw className="w-3.5 h-3.5 animate-spin" strokeWidth={1.75} />
              ) : (
                <Trash2 className="w-3.5 h-3.5" strokeWidth={1.75} />
              )}
              Forget everything
            </button>
          </div>
        </>
      )}
    </div>
  )
}

export default Settings
