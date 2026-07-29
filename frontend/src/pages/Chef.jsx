/* eslint-disable react/prop-types -- this project doesn't use PropTypes, see AuthContext.jsx */
import { useState, useRef, useEffect, useCallback } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import {
  ChefHat,
  Send,
  AlertCircle,
  HelpCircle,
  MessageCircleQuestion,
  Sparkles,
  ShoppingBasket,
} from 'lucide-react'
import { chefChat, getErrorMessage } from '../services/api'

const SOURCE_LABELS = {
  local: 'Harvest recipe',
  themealdb: 'TheMealDB',
  generated: 'Chef-generated',
}

/** Renders a single recipe response as a structured card, including why it was chosen. */
function RecipeCard({ recipe }) {
  if (!recipe) return null
  const sourceLabel = SOURCE_LABELS[recipe.source] || recipe.source

  return (
    <div className="card mt-2">
      <div className="flex items-start justify-between gap-2 mb-1">
        <h3 className="font-display text-lg font-semibold text-sage-900">{recipe.title}</h3>
        {sourceLabel && (
          <span className="flex-shrink-0 text-[10px] uppercase tracking-wide font-semibold text-sage-500 bg-cream-100 border border-cream-200 rounded-full px-2 py-1">
            {sourceLabel}
          </span>
        )}
      </div>

      {recipe.description && (
        <p className="text-sm text-sage-600 mb-3">{recipe.description}</p>
      )}

      {recipe.rationale && (
        <div className="flex items-start gap-2 text-sm text-sage-700 bg-sage-50 border border-sage-100 rounded-lg px-3 py-2 mb-3">
          <Sparkles className="w-4 h-4 mt-0.5 flex-shrink-0 text-sage-500" />
          <span>{recipe.rationale}</span>
        </div>
      )}

      {recipe.missingIngredients?.length > 0 && (
        <div className="flex items-start gap-2 text-sm text-terracotta-700 bg-terracotta-50 border border-terracotta-100 rounded-lg px-3 py-2 mb-3">
          <ShoppingBasket className="w-4 h-4 mt-0.5 flex-shrink-0" />
          <span>You&apos;ll need to pick up: {recipe.missingIngredients.join(', ')}</span>
        </div>
      )}

      {recipe.servings && (
        <p className="text-xs font-medium text-sage-500 mb-4">Serves {recipe.servings}</p>
      )}

      {recipe.ingredients?.length > 0 && (
        <div className="mb-4">
          <h4 className="text-xs font-semibold uppercase tracking-wide text-sage-500 mb-2">
            Ingredients
          </h4>
          <ul className="space-y-1">
            {recipe.ingredients.map((ingredient, index) => (
              <li key={index} className="text-sm text-sage-800 flex gap-2">
                <span className="text-sage-400">&bull;</span>
                {ingredient}
              </li>
            ))}
          </ul>
        </div>
      )}

      {recipe.steps?.length > 0 && (
        <div className="mb-2">
          <h4 className="text-xs font-semibold uppercase tracking-wide text-sage-500 mb-2">
            Steps
          </h4>
          <ol className="space-y-2">
            {recipe.steps.map((step, index) => (
              <li key={index} className="text-sm text-sage-800 flex gap-3">
                <span className="flex-shrink-0 w-5 h-5 rounded-full bg-sage-100 text-sage-700 text-xs font-semibold flex items-center justify-center">
                  {index + 1}
                </span>
                <span>{step}</span>
              </li>
            ))}
          </ol>
        </div>
      )}

      {recipe.notes && (
        <p className="text-xs text-sage-500 italic mt-3 pt-3 border-t border-cream-200">
          {recipe.notes}
        </p>
      )}
    </div>
  )
}

/** One message bubble, styled by role and (for the assistant) response type. */
function MessageBubble({ turn }) {
  const isUser = turn.role === 'user'

  if (isUser) {
    return (
      <div className="flex justify-end">
        <div className="max-w-[80%] bg-sage-600 text-white rounded-2xl rounded-br-sm px-4 py-2.5 text-sm">
          {turn.content}
        </div>
      </div>
    )
  }

  const isClarifying = turn.responseType === 'CLARIFYING_QUESTION'
  const isNonAnswer = turn.responseType === 'HONEST_NON_ANSWER'
  const isTechnique = turn.responseType === 'TECHNIQUE_ANSWER'

  return (
    <div className="flex justify-start">
      <div className="max-w-[85%] w-full">
        <div
          className={`rounded-2xl rounded-bl-sm px-4 py-2.5 text-sm flex items-start gap-2 ${
            isClarifying
              ? 'bg-terracotta-50 text-terracotta-800 border border-terracotta-200'
              : isNonAnswer
                ? 'bg-cream-200 text-sage-700 border border-cream-300'
                : isTechnique
                  ? 'bg-sage-50 text-sage-800 border border-sage-200'
                  : 'bg-white text-sage-800 border border-cream-200'
          }`}
        >
          {isClarifying && <MessageCircleQuestion className="w-4 h-4 mt-0.5 flex-shrink-0" />}
          {isNonAnswer && <HelpCircle className="w-4 h-4 mt-0.5 flex-shrink-0" />}
          {isTechnique && <Sparkles className="w-4 h-4 mt-0.5 flex-shrink-0" />}
          <span>{turn.content}</span>
        </div>
        {turn.recipes?.map((recipe, index) => (
          <RecipeCard key={index} recipe={recipe} />
        ))}
      </div>
    </div>
  )
}

function TypingIndicator() {
  return (
    <div className="flex justify-start">
      <div className="bg-white border border-cream-200 rounded-2xl rounded-bl-sm px-4 py-3 flex gap-1">
        {[0, 1, 2].map((i) => (
          <motion.span
            key={i}
            className="w-1.5 h-1.5 bg-sage-400 rounded-full"
            animate={{ opacity: [0.3, 1, 0.3] }}
            transition={{ duration: 1, repeat: Infinity, delay: i * 0.15 }}
          />
        ))}
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
    <div className="min-h-[calc(100vh-4rem)] flex flex-col max-w-3xl mx-auto px-4 sm:px-6 py-8">
      <div className="flex items-center gap-3 mb-6">
        <div className="w-10 h-10 bg-sage-600 rounded-xl flex items-center justify-center">
          <ChefHat className="w-5 h-5 text-white" />
        </div>
        <div>
          <h1 className="font-display text-xl font-bold text-sage-900">Chef Brain</h1>
          <p className="text-xs text-sage-500">Tell me what you have, or what you&apos;re trying to do.</p>
        </div>
      </div>

      <div className="flex-1 flex flex-col gap-3 mb-4 min-h-[300px]">
        {turns.length === 0 && (
          <div className="flex-1 flex items-center justify-center text-center px-8">
            <p className="text-sage-400 text-sm">
              Try something like &ldquo;I have eggs, spinach and rice&rdquo;, &ldquo;I want
              authentic ramen&rdquo;, or &ldquo;my sauce split&rdquo;.
            </p>
          </div>
        )}

        <AnimatePresence initial={false}>
          {turns.map((turn, index) => (
            <motion.div
              key={index}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.25 }}
            >
              <MessageBubble turn={turn} />
            </motion.div>
          ))}
        </AnimatePresence>

        {isSending && <TypingIndicator />}
        <div ref={scrollAnchorRef} />
      </div>

      {error && (
        <div className="flex items-center gap-2 text-sm text-terracotta-700 bg-terracotta-50 border border-terracotta-200 rounded-xl px-4 py-2.5 mb-3">
          <AlertCircle className="w-4 h-4 flex-shrink-0" />
          {error}
        </div>
      )}

      <form onSubmit={handleSubmit} className="flex items-end gap-2">
        <textarea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault()
              handleSubmit(e)
            }
          }}
          placeholder="What's in your kitchen today?"
          rows={1}
          className="input-field resize-none flex-1"
          disabled={isSending}
        />
        <button
          type="submit"
          className="btn-primary px-4 py-3"
          disabled={isSending || !input.trim()}
          aria-label="Send message"
        >
          <Send className="w-4 h-4" />
        </button>
      </form>
    </div>
  )
}

export default Chef
