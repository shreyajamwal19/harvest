/* eslint-disable react/prop-types -- this project doesn't use PropTypes, see AuthContext.jsx */
import { useState, useRef, useEffect, useCallback } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { ArrowUp, Quote, Users, ShoppingBag, Check } from 'lucide-react'
import { chefChat, getErrorMessage } from '../services/api'

const SOURCE_LABELS = {
  local: 'Harvest recipe',
  themealdb: 'TheMealDB',
  generated: 'Chef-generated',
}

/** Renders a single recipe response as an editorial recipe card, including why it was chosen. */
function RecipeCard({ recipe }) {
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
      className="mt-3 bg-paper-50 border border-ink-700/10 rounded-sheet overflow-hidden shadow-soft hover:shadow-lift transition-shadow duration-300 ease-quiet"
    >
      <div className="h-1 bg-gradient-to-r from-brick-500 via-brick-400 to-gold-300" />

      <div className="px-5 sm:px-7 pt-6 pb-5">
        <div className="flex items-start justify-between gap-3 flex-wrap">
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
            What&apos;s in your kitchen today?
          </h1>
        </div>
        <span aria-hidden="true" className="hidden sm:flex w-9 h-9 rounded-full bg-gradient-to-br from-brick-400 to-brick-600 items-center justify-center">
          <span className="w-2 h-2 rounded-full bg-paper-50/90" />
        </span>
      </div>

      <div className="flex-1 flex flex-col gap-4 mb-6 pb-20 min-h-[280px]">
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
    </div>
  )
}

export default Chef
