/* eslint-disable react/prop-types -- this project doesn't use PropTypes, see AuthContext.jsx */
import { useState, useRef, useEffect, useCallback } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import { ArrowUp, ChefHat, Bookmark, BookmarkX } from 'lucide-react'
import { chefChat, getSavedRecipes, saveRecipe, unsaveRecipe, getErrorMessage } from '../services/api'
import { useAuth } from '../contexts/AuthContext'
import RecipeCard from '../components/RecipeCard'
import Toast from '../components/Toast'

const RESPONSE_LABELS = {
  CLARIFYING_QUESTION: { text: 'Quick question', dot: 'bg-gold-500' },
  HONEST_NON_ANSWER: { text: 'Heads up', dot: 'bg-ink-700/40' },
  TECHNIQUE_ANSWER: { text: 'Technique', dot: 'bg-moss-400' },
  RECIPE: { text: 'Recipe', dot: 'bg-brick-400' },
}

function initials(name) {
  if (!name) return '?'
  return name.split(' ').filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join('')
}

/** One message turn, styled by role and (for the assistant) response type. */
function MessageBubble({ turn, userName, savedTitles, pendingTitles, onToggleSave }) {
  const isUser = turn.role === 'user'

  if (isUser) {
    return (
      <div className="flex justify-end items-end gap-2.5">
        <div className="max-w-[78%] bg-gradient-to-br from-brick-400 to-brick-600 text-paper-50 rounded-2xl rounded-br-md px-4 py-2.5 text-sm leading-relaxed shadow-soft">
          {turn.content}
        </div>
        <span className="flex-shrink-0 w-7 h-7 rounded-full bg-paper-300 text-ink-700 text-[11px] font-semibold flex items-center justify-center">
          {initials(userName)}
        </span>
      </div>
    )
  }

  const meta = RESPONSE_LABELS[turn.responseType] || RESPONSE_LABELS.RECIPE

  return (
    <div className="flex items-start gap-2.5">
      <span className="flex-shrink-0 w-7 h-7 rounded-full bg-gradient-to-br from-ink-700 to-ink-800 flex items-center justify-center">
        <ChefHat className="w-3.5 h-3.5 text-gold-300" strokeWidth={1.75} />
      </span>
      <div className="max-w-[85%] flex-1 min-w-0">
        <div className="flex items-center gap-1.5 mb-1.5">
          <span className={`w-1.5 h-1.5 rounded-full ${meta.dot}`} />
          <span className="text-[11px] font-semibold uppercase tracking-wide text-ink-400">{meta.text}</span>
        </div>
        <div className="bg-paper-50 border border-ink-700/8 rounded-2xl rounded-tl-md px-4 py-3 text-sm text-ink-700 leading-relaxed shadow-soft">
          {turn.content}
        </div>
        {turn.recipes?.map((recipe, index) => {
          const key = recipe.title?.trim().toLowerCase()
          return (
            <RecipeCard
              key={index}
              recipe={recipe}
              saved={savedTitles.has(key)}
              savePending={pendingTitles.has(key)}
              onToggleSave={() => onToggleSave(recipe)}
            />
          )
        })}
      </div>
    </div>
  )
}

/**
 * Signature loading moment for the one interaction that happens on every single turn: waiting on
 * Chef Brain. A generic three-dot bounce works anywhere - this doesn't. A small line-art pot with
 * steam wisps curling and fading upward, drawn in the same restrained lucide-style stroke used
 * everywhere else in the app (currentColor, 1.75 stroke width, rounded caps), so it reads as part
 * of the design system rather than a decoration bolted on.
 */
function SimmeringPotLoader() {
  const wisp = (delay, xDrift) => ({
    initial: { opacity: 0, y: 0, x: 0, scale: 0.7 },
    animate: {
      opacity: [0, 0.85, 0],
      y: [-2, -15, -24],
      x: [0, xDrift, xDrift * 1.6],
      scale: [0.7, 1, 1.15],
    },
    transition: { duration: 1.8, repeat: Infinity, delay, ease: [0.22, 1, 0.36, 1] },
  })

  return (
    <svg width="40" height="30" viewBox="0 0 40 30" fill="none" aria-hidden="true">
      {/* Steam - three staggered wisps, each drifting a different direction so they never
          overlap identically, the way real steam never repeats the same curl twice. */}
      <motion.circle cx="14" cy="10" r="1.6" className="fill-gold-500/70" {...wisp(0, -3)} />
      <motion.circle cx="20" cy="9" r="1.8" className="fill-brick-300/70" {...wisp(0.5, 2)} />
      <motion.circle cx="26" cy="10" r="1.5" className="fill-gold-500/70" {...wisp(1, -2)} />

      {/* Pot - simple line-art, matches lucide-react's stroke conventions used elsewhere */}
      <path
        d="M10 14h20l-1.5 9a3 3 0 0 1-3 2.5h-11a3 3 0 0 1-3-2.5L10 14Z"
        stroke="currentColor"
        strokeWidth="1.75"
        strokeLinejoin="round"
        className="text-ink-500"
      />
      <path d="M8 14h24" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" className="text-ink-600" />
      <path
        d="M12 14c0-2.5 3-3.5 8-3.5s8 1 8 3.5"
        stroke="currentColor"
        strokeWidth="1.75"
        strokeLinecap="round"
        className="text-ink-400"
      />
    </svg>
  )
}

function TypingIndicator() {
  return (
    <div className="flex items-start gap-2.5">
      <span className="flex-shrink-0 w-7 h-7 rounded-full bg-gradient-to-br from-ink-700 to-ink-800 flex items-center justify-center">
        <ChefHat className="w-3.5 h-3.5 text-gold-300" strokeWidth={1.75} />
      </span>
      <div className="bg-paper-50 border border-ink-700/8 rounded-2xl rounded-tl-md px-4 py-2.5 shadow-soft">
        <SimmeringPotLoader />
      </div>
    </div>
  )
}

function Chef() {
  const { user } = useAuth()
  const location = useLocation()
  const [sessionId, setSessionId] = useState(null)
  const [turns, setTurns] = useState([])
  const [input, setInput] = useState(() => location.state?.prefill || '')
  const [isSending, setIsSending] = useState(false)
  const [error, setError] = useState('')
  const scrollAnchorRef = useRef(null)

  // title (lowercased) -> saved-recipe id, so toggling knows whether to POST or DELETE.
  const [savedByTitle, setSavedByTitle] = useState(new Map())
  const [pendingTitles, setPendingTitles] = useState(new Set())
  const [toast, setToast] = useState(null)
  const toastTimerRef = useRef(null)

  const showToast = useCallback((data) => {
    clearTimeout(toastTimerRef.current)
    setToast({ id: Date.now(), ...data })
    toastTimerRef.current = setTimeout(() => setToast(null), 3200)
  }, [])

  useEffect(() => () => clearTimeout(toastTimerRef.current), [])

  useEffect(() => {
    scrollAnchorRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [turns, isSending])

  useEffect(() => {
    getSavedRecipes()
      .then((response) => {
        const map = new Map(
          response.data.map((s) => [s.recipe.title?.trim().toLowerCase(), s.id])
        )
        setSavedByTitle(map)
      })
      .catch(() => {
        // Not knowing what's saved yet is a cosmetic issue (buttons just start unfilled) -
        // never block the chat experience over it.
      })
  }, [])

  const toggleSave = useCallback(async (recipe) => {
    const key = recipe.title?.trim().toLowerCase()
    if (!key || pendingTitles.has(key)) return
    setPendingTitles((prev) => new Set(prev).add(key))

    try {
      const existingId = savedByTitle.get(key)
      if (existingId) {
        await unsaveRecipe(existingId)
        setSavedByTitle((prev) => {
          const next = new Map(prev)
          next.delete(key)
          return next
        })
        showToast({
          tone: 'neutral',
          icon: <BookmarkX className="w-4 h-4 text-paper-50" strokeWidth={2} />,
          title: 'Removed from saved recipes',
          subtitle: recipe.title,
        })
      } else {
        const response = await saveRecipe(recipe)
        setSavedByTitle((prev) => new Map(prev).set(key, response.data.id))
        showToast({
          tone: 'success',
          icon: <Bookmark className="w-4 h-4 text-paper-50" strokeWidth={2} fill="currentColor" />,
          title: 'Saved to your collection',
          subtitle: recipe.title,
          action: (
            <Link
              to="/saved"
              className="flex-shrink-0 text-xs font-semibold text-gold-300 hover:text-gold-200 transition-colors px-1"
            >
              View
            </Link>
          ),
        })
      }
    } catch {
      // Optimistic-free by design here - state simply doesn't change, so the button stays
      // showing the truthful (unchanged) state rather than lying about success.
      showToast({
        tone: 'neutral',
        icon: <BookmarkX className="w-4 h-4 text-paper-50" strokeWidth={2} />,
        title: "Couldn't update saved recipes",
        subtitle: 'Please try again',
      })
    } finally {
      setPendingTitles((prev) => {
        const next = new Set(prev)
        next.delete(key)
        return next
      })
    }
  }, [savedByTitle, pendingTitles, showToast])

  const sendMessage = useCallback(async (message) => {
    setError('')
    setTurns((prev) => [...prev, { role: 'user', content: message }])
    setIsSending(true)

    try {
      const response = await chefChat({ sessionId, message })
      const data = response.data
      setSessionId(data.sessionId)
      setTurns((prev) => [
        ...prev,
        {
          role: 'assistant',
          content: data.message,
          responseType: data.responseType,
          recipes: data.recipes,
        },
      ])
    } catch (err) {
      setError(getErrorMessage(err, 'The Chef Brain had trouble responding. Please try again.'))
    } finally {
      setIsSending(false)
    }
  }, [sessionId])

  const handleSubmit = (e) => {
    e.preventDefault()
    const trimmed = input.trim()
    if (!trimmed || isSending) return
    setInput('')
    sendMessage(trimmed)
  }

  return (
    <div className="relative min-h-[calc(100vh-4rem)] flex flex-col max-w-3xl mx-auto px-4 sm:px-6 py-10">
      <div
        className="pointer-events-none absolute inset-x-0 top-0 h-72 -z-10 opacity-60"
        style={{ background: 'radial-gradient(60% 100% at 50% 0%, rgba(74,21,24,0.06), transparent 70%)' }}
      />
      <div className="mb-8 pb-6 border-b border-ink-700/10 flex items-center justify-between">
        <div>
          <span className="eyebrow">Chef Brain</span>
          <h1 className="font-display text-2xl font-semibold text-ink-800 mt-1.5">
            What&apos;s in your kitchen today?
          </h1>
        </div>
        <span aria-hidden="true" className="hidden sm:flex w-9 h-9 rounded-full bg-gradient-to-br from-ink-700 to-ink-800 items-center justify-center">
          <ChefHat className="w-4 h-4 text-gold-300" strokeWidth={1.75} />
        </span>
      </div>

      <div className="flex-1 flex flex-col gap-4 mb-6 pb-20 min-h-[280px]">
        {turns.length === 0 && (
          <div className="flex-1 flex flex-col items-center justify-center text-center px-6 gap-4">
            <span className="w-12 h-12 rounded-full bg-gradient-to-br from-ink-700 to-ink-800 flex items-center justify-center">
              <ChefHat className="w-5 h-5 text-gold-300" strokeWidth={1.75} />
            </span>
            <p className="text-ink-500 text-sm max-w-sm leading-relaxed">
              Try &ldquo;I have eggs, spinach and rice&rdquo;, &ldquo;I want authentic
              ramen&rdquo;, or &ldquo;my sauce split.&rdquo;
            </p>
          </div>
        )}

        <AnimatePresence initial={false}>
          {turns.map((turn, index) => (
            <motion.div
              key={index}
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.25, ease: [0.22, 1, 0.36, 1] }}
            >
              <MessageBubble
                turn={turn}
                userName={user?.name}
                savedTitles={new Set(savedByTitle.keys())}
                pendingTitles={pendingTitles}
                onToggleSave={toggleSave}
              />
            </motion.div>
          ))}
        </AnimatePresence>

        {isSending && <TypingIndicator />}
        <div ref={scrollAnchorRef} className="scroll-mb-24" />
      </div>

      {error && (
        <div className="text-sm text-brick-600 bg-brick-50 border border-brick-200 rounded-sheet px-4 py-2.5 mb-3">
          {error}
        </div>
      )}

      <form onSubmit={handleSubmit} className="sticky bottom-4 pb-[env(safe-area-inset-bottom)]">
        <div className="flex items-end gap-2 bg-paper-50 border border-ink-700/12 rounded-full pl-5 pr-1.5 py-1.5 shadow-lift focus-within:border-brick-400 focus-within:ring-2 focus-within:ring-brick-100 transition-all duration-200 ease-quiet">
          <textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            aria-label="Message Chef Brain"
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault()
                handleSubmit(e)
              }
            }}
            placeholder="Tell Chef Brain what you have…"
            rows={1}
            className="flex-1 resize-none bg-transparent border-0 outline-none py-2 text-sm text-ink-800 placeholder-ink-500/70"
            disabled={isSending}
          />
          <motion.button
            type="submit"
            whileHover={{ scale: input.trim() ? 1.05 : 1 }}
            whileTap={{ scale: 0.95 }}
            className="flex-shrink-0 w-10 h-10 rounded-full bg-gradient-to-br from-brick-400 to-brick-600 text-paper-50 flex items-center justify-center disabled:opacity-40 disabled:cursor-not-allowed shadow-soft"
            disabled={isSending || !input.trim()}
            aria-label="Send message"
          >
            {isSending ? (
              <ArrowUp className="w-4 h-4 animate-pulse" strokeWidth={2} />
            ) : (
              <ArrowUp className="w-4 h-4" strokeWidth={2.25} />
            )}
          </motion.button>
        </div>
      </form>

      <Toast toast={toast} onDismiss={() => setToast(null)} />
    </div>
  )
}

export default Chef
