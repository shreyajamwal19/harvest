/* eslint-disable react/prop-types -- this project doesn't use PropTypes, see AuthContext.jsx */
import { useState, useRef, useEffect, useCallback } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Send, ArrowUp } from 'lucide-react'
import { chefChat, getErrorMessage } from '../services/api'

const SOURCE_LABELS = {
  local: 'Harvest recipe',
  themealdb: 'TheMealDB',
  generated: 'Chef-generated',
}

/** Renders a single recipe response as an editorial recipe card, including why it was chosen. */
function RecipeCard({ recipe }) {
  if (!recipe) return null
  const sourceLabel = SOURCE_LABELS[recipe.source] || recipe.source

  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1] }}
      className="mt-3 bg-paper-50 border border-ink-700/10 rounded-sheet overflow-hidden shadow-soft hover:shadow-lift transition-shadow duration-300 ease-quiet"
    >
      <div className="px-4 sm:px-5 pt-4 sm:pt-5 pb-4 border-b border-ink-700/10">
        <div className="flex items-start justify-between gap-3">
          <h3 className="font-display text-xl font-semibold text-ink-800 leading-snug">
            {recipe.title}
          </h3>
          {sourceLabel && (
            <span className="flex-shrink-0 mt-1 eyebrow text-[10px]">{sourceLabel}</span>
          )}
        </div>
        {recipe.description && (
          <p className="text-sm text-ink-500 mt-2 leading-relaxed">{recipe.description}</p>
        )}
        {recipe.servings && (
          <p className="text-xs text-ink-400 mt-3">Serves {recipe.servings}</p>
        )}
      </div>

      {recipe.rationale && (
        <div className="px-4 sm:px-5 py-3 bg-paper-200/50 border-b border-ink-700/10">
          <p className="text-sm text-ink-600 italic">{recipe.rationale}</p>
        </div>
      )}

      {recipe.missingIngredients?.length > 0 && (
        <div className="px-4 sm:px-5 py-3 bg-brick-50 border-b border-brick-100 text-sm text-brick-600">
          Pick up: {recipe.missingIngredients.join(', ')}
        </div>
      )}

      <div className="px-4 sm:px-5 py-4 sm:py-5 grid grid-cols-1 sm:grid-cols-[1fr_1.4fr] gap-5 sm:gap-6">
        {recipe.ingredients?.length > 0 && (
          <div>
            <h4 className="eyebrow mb-3">Ingredients</h4>
            <ul className="space-y-1.5">
              {recipe.ingredients.map((ingredient, index) => (
                <li key={index} className="text-sm text-ink-700 flex gap-2.5">
                  <span className="text-gold-500 mt-1.5 block w-1 h-1 rounded-full flex-shrink-0" />
                  {ingredient}
                </li>
              ))}
            </ul>
          </div>
        )}

        {recipe.steps?.length > 0 && (
          <div>
            <h4 className="eyebrow mb-3">Method</h4>
            <ol className="space-y-3">
              {recipe.steps.map((step, index) => (
                <li key={index} className="text-sm text-ink-700 flex gap-3">
                  <span className="flex-shrink-0 font-display text-sm text-brick-400">
                    {String(index + 1).padStart(2, '0')}
                  </span>
                  <span className="leading-relaxed">{step}</span>
                </li>
              ))}
            </ol>
          </div>
        )}
      </div>

      {recipe.notes && (
        <p className="px-4 sm:px-5 pb-4 sm:pb-5 text-xs text-ink-500 italic">{recipe.notes}</p>
      )}
    </motion.div>
  )
}

/** One message turn, styled by role and (for the assistant) response type. */
function MessageBubble({ turn }) {
  const isUser = turn.role === 'user'

  if (isUser) {
    return (
      <div className="flex justify-end">
        <div className="max-w-[80%] bg-brick-500 text-paper-50 rounded-sheet rounded-br-sm px-4 py-2.5 text-sm leading-relaxed">
          {turn.content}
        </div>
      </div>
    )
  }

  const dotColor =
    turn.responseType === 'CLARIFYING_QUESTION' ? 'bg-gold-500'
    : turn.responseType === 'HONEST_NON_ANSWER' ? 'bg-ink-700/40'
    : turn.responseType === 'TECHNIQUE_ANSWER' ? 'bg-moss-400'
    : 'bg-brick-400'

  return (
    <div className="flex justify-start">
      <div className="max-w-[85%] w-full flex gap-3">
        <span className={`mt-1.5 w-1.5 h-1.5 rounded-full flex-shrink-0 ${dotColor}`} />
        <div className="flex-1">
          <div className="text-sm text-ink-700 leading-relaxed">{turn.content}</div>
          {turn.recipes?.map((recipe, index) => (
            <RecipeCard key={index} recipe={recipe} />
          ))}
        </div>
      </div>
    </div>
  )
}

function TypingIndicator() {
  return (
    <div className="flex justify-start">
      <div className="border-l-2 border-ink-700/15 pl-4 py-2 w-full max-w-[85%] space-y-2 ml-[18px]">
        <motion.div
          className="h-2.5 rounded-full bg-ink-700/10 w-4/5"
          animate={{ opacity: [0.4, 0.9, 0.4] }}
          transition={{ duration: 1.4, repeat: Infinity, ease: 'easeInOut' }}
        />
        <motion.div
          className="h-2.5 rounded-full bg-ink-700/10 w-2/5"
          animate={{ opacity: [0.4, 0.9, 0.4] }}
          transition={{ duration: 1.4, repeat: Infinity, ease: 'easeInOut', delay: 0.2 }}
        />
      </div>
    </div>
  )
}

function Chef() {
  const [sessionId, setSessionId] = useState(null)
  const [turns, setTurns] = useState([])
  const [input, setInput] = useState('')
  const [isSending, setIsSending] = useState(false)
  const [error, setError] = useState('')
  const scrollAnchorRef = useRef(null)

  useEffect(() => {
    scrollAnchorRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [turns, isSending])

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
            What's in your kitchen today?
          </h1>
        </div>
        <span className="hidden sm:flex w-9 h-9 rounded-full bg-gradient-to-br from-brick-400 to-brick-600 items-center justify-center">
          <span className="w-2 h-2 rounded-full bg-paper-50/90" />
        </span>
      </div>

      <div className="flex-1 flex flex-col gap-4 mb-6 min-h-[280px]">
        {turns.length === 0 && (
          <div className="flex-1 flex items-center justify-center text-center px-6">
            <p className="text-ink-400 text-sm max-w-sm leading-relaxed">
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
              <MessageBubble turn={turn} />
            </motion.div>
          ))}
        </AnimatePresence>

        {isSending && <TypingIndicator />}
        <div ref={scrollAnchorRef} />
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
            {isSending ? <ArrowUp className="w-4 h-4 animate-pulse" strokeWidth={1.75} /> : <Send className="w-4 h-4" strokeWidth={1.75} />}
          </motion.button>
        </div>
      </form>
    </div>
  )
}

export default Chef
