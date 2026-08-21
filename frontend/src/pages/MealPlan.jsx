/* eslint-disable react/prop-types -- this project doesn't use PropTypes, see AuthContext.jsx */
import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import {
  CalendarDays,
  ChevronDown,
  ShoppingCart,
  AlertCircle,
  Sparkles,
  Check,
  RefreshCw,
  ChefHat,
  UtensilsCrossed,
  Coffee,
  Sun,
  Moon,
  ShoppingBasket,
} from 'lucide-react'
import { chefChat, getPantryItems, getErrorMessage } from '../services/api'
import { CATEGORY_META, CATEGORY_ORDER } from '../utils/pantryCategories'

const DAY_OPTIONS = [1, 3, 5, 7]
const MEAL_TYPE_OPTIONS = [
  { value: null, label: 'Any meal', icon: UtensilsCrossed },
  { value: 'breakfast', label: 'Breakfast', icon: Coffee },
  { value: 'lunch', label: 'Lunch', icon: Sun },
  { value: 'dinner', label: 'Dinner', icon: Moon },
]

function buildMealPlanMessage(days, mealType) {
  const mealPart = mealType ? `${mealType} ` : ''
  return `Give me a ${days}-day ${mealPart}meal plan`.replace(/\s+/g, ' ').trim()
}

function DayCard({ day, index, expanded, onToggle }) {
  const recipe = day.recipe
  const navigate = useNavigate()
  const [imageFailed, setImageFailed] = useState(false)
  const hasImage = recipe?.imageUrl && !imageFailed

  return (
    <motion.div
      layout
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.25, delay: index * 0.04 }}
      className="card !p-0 overflow-hidden"
    >
      <button
        onClick={onToggle}
        className="w-full flex items-center gap-3 px-4 sm:px-5 py-3.5 text-left"
      >
        {hasImage ? (
          <img
            src={recipe.imageUrl}
            alt=""
            onError={() => setImageFailed(true)}
            loading="lazy"
            className="flex-shrink-0 w-12 h-12 rounded-full object-cover border border-ink-700/10"
          />
        ) : (
          <span className="flex-shrink-0 w-12 h-12 rounded-full bg-paper-200 flex items-center justify-center">
            <ChefHat className="w-5 h-5 text-moss-400" strokeWidth={1.75} />
          </span>
        )}
        <div className="min-w-0 flex-1">
          <span className="text-xs font-semibold uppercase tracking-[0.14em] text-moss-400">
            {day.dayLabel}
          </span>
          <p className="font-display text-lg font-semibold text-ink-800 truncate">
            {recipe?.title || 'Untitled dish'}
          </p>
        </div>
        <ChevronDown
          className={`w-4 h-4 flex-shrink-0 text-ink-400 transition-transform ${expanded ? 'rotate-180' : ''}`}
          strokeWidth={1.75}
        />
      </button>

      <AnimatePresence initial={false}>
        {expanded && recipe && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.2 }}
            className="overflow-hidden border-t border-ink-700/10"
          >
            {hasImage && (
              <div className="relative w-full aspect-[21/9] overflow-hidden bg-paper-200">
                <img src={recipe.imageUrl} alt={recipe.title} className="w-full h-full object-cover" />
                <div className="absolute inset-0 bg-gradient-to-t from-ink-900/40 via-transparent to-transparent" />
              </div>
            )}
            <div className="px-5 py-4 space-y-4">
              {recipe.description && (
                <p className="text-sm text-ink-600">{recipe.description}</p>
              )}
              {recipe.rationale && (
                <div className="flex items-start gap-2 text-sm text-ink-700 bg-moss-50 border border-moss-100 rounded-sheet px-3 py-2">
                  <Sparkles className="w-4 h-4 mt-0.5 flex-shrink-0 text-moss-400" strokeWidth={1.75} />
                  <span>{recipe.rationale}</span>
                </div>
              )}
              {recipe.missingIngredients?.length > 0 && (
                <p className="text-xs text-brick-400">
                  You&apos;ll need to pick up: {recipe.missingIngredients.join(', ')}
                </p>
              )}
              {recipe.ingredients?.length > 0 && (
                <div>
                  <h4 className="text-xs font-semibold uppercase tracking-[0.14em] text-ink-500 mb-2">
                    Ingredients
                  </h4>
                  <ul className="space-y-1">
                    {recipe.ingredients.map((ing, i) => (
                      <li key={i} className="text-sm text-ink-700 flex gap-2">
                        <span className="text-ink-400">&bull;</span>
                        {ing}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
              {recipe.steps?.length > 0 && (
                <div>
                  <h4 className="text-xs font-semibold uppercase tracking-[0.14em] text-ink-500 mb-2">
                    Steps
                  </h4>
                  <ol className="space-y-2">
                    {recipe.steps.map((step, i) => (
                      <li key={i} className="text-sm text-ink-700 flex gap-3">
                        <span className="flex-shrink-0 w-5 h-5 rounded-full bg-moss-100 text-moss-500 text-xs font-semibold flex items-center justify-center">
                          {i + 1}
                        </span>
                        <span>{step}</span>
                      </li>
                    ))}
                  </ol>
                </div>
              )}
              {recipe.steps?.length > 0 && (
                <button
                  type="button"
                  onClick={() => navigate('/cook', { state: { recipe } })}
                  className="w-full flex items-center justify-center gap-2 text-sm font-semibold text-paper-50 bg-ink-800 hover:bg-ink-700 rounded-sheet py-3 transition-colors"
                >
                  <ChefHat className="w-4 h-4" strokeWidth={1.75} />
                  Start Cooking
                </button>
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  )
}

function DaySkeleton({ index }) {
  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ duration: 0.3, delay: index * 0.06 }}
      className="card !p-0 overflow-hidden"
    >
      <div className="flex items-center gap-3 px-4 sm:px-5 py-3.5">
        <span className="flex-shrink-0 w-12 h-12 rounded-full bg-paper-200 animate-pulse" />
        <div className="min-w-0 flex-1 space-y-2">
          <span className="block h-2.5 w-16 rounded-full bg-paper-200 animate-pulse" />
          <span className="block h-3.5 w-2/3 rounded-full bg-paper-200 animate-pulse" />
        </div>
      </div>
    </motion.div>
  )
}

function ShoppingListView({ categories, checked, onToggleItem }) {
  const totalItems = categories.reduce((sum, c) => sum + c.items.length, 0)
  const checkedCount = Object.values(checked).filter(Boolean).length

  if (totalItems === 0) {
    return (
      <div className="card text-center py-8">
        <p className="text-sm text-ink-600">
          Your pantry already covers everything in this plan - nothing to buy.
        </p>
      </div>
    )
  }

  return (
    <div className="card">
      <div className="flex items-center justify-between mb-4">
        <h3 className="font-display text-lg font-semibold text-ink-800">Grocery List</h3>
        <span className="text-xs text-ink-500">{checkedCount} / {totalItems} checked</span>
      </div>
      <div className="space-y-5">
        {CATEGORY_ORDER.filter((cat) => categories.some((c) => c.category === cat)).map((cat) => {
          const section = categories.find((c) => c.category === cat)
          const { label, icon: Icon } = CATEGORY_META[cat] || CATEGORY_META.OTHER
          return (
            <div key={cat}>
              <div className="flex items-center gap-2 mb-2">
                <Icon className="w-4 h-4 text-moss-400" strokeWidth={1.75} />
                <h4 className="text-xs font-semibold uppercase tracking-[0.14em] text-ink-500">
                  {label}
                </h4>
              </div>
              <ul className="space-y-1.5">
                {section.items.map((item) => {
                  const key = `${cat}:${item}`
                  const isChecked = !!checked[key]
                  return (
                    <li key={key}>
                      <button
                        onClick={() => onToggleItem(key)}
                        className="w-full flex items-center gap-2.5 text-left px-3 py-2 rounded-sheet hover:bg-paper-100 transition-colors"
                      >
                        <span
                          className={`flex-shrink-0 w-4 h-4 rounded-[4px] border flex items-center justify-center transition-colors ${
                            isChecked
                              ? 'bg-moss-400 border-moss-400'
                              : 'border-ink-700/25'
                          }`}
                        >
                          {isChecked && <Check className="w-3 h-3 text-white" strokeWidth={2.5} />}
                        </span>
                        <span className={`text-sm ${isChecked ? 'text-ink-400 line-through' : 'text-ink-700'}`}>
                          {item}
                        </span>
                      </button>
                    </li>
                  )
                })}
              </ul>
            </div>
          )
        })}
      </div>
    </div>
  )
}

function MealPlan() {
  const navigate = useNavigate()
  const [sessionId, setSessionId] = useState(null)
  const [days, setDays] = useState(3)
  const [mealType, setMealType] = useState(null)
  const [pantryCount, setPantryCount] = useState(null)

  const [plan, setPlan] = useState(null)
  const [expandedDay, setExpandedDay] = useState(0)
  const [planLoading, setPlanLoading] = useState(false)
  const [planError, setPlanError] = useState('')

  const [shoppingList, setShoppingList] = useState(null)
  const [checked, setChecked] = useState({})
  const [listLoading, setListLoading] = useState(false)
  const [listError, setListError] = useState('')

  useEffect(() => {
    let cancelled = false
    getPantryItems()
      .then((res) => !cancelled && setPantryCount(res.data.length))
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [])

  const generatePlan = async () => {
    setPlanLoading(true)
    setPlanError('')
    setShoppingList(null)
    setChecked({})
    try {
      const message = buildMealPlanMessage(days, mealType)
      const response = await chefChat({ sessionId, message })
      const data = response.data
      setSessionId(data.sessionId)
      if (data.mealPlan?.days?.length) {
        setPlan(data.mealPlan.days)
        setExpandedDay(0)
      } else {
        setPlan([])
        setPlanError(data.message || "Couldn't put together a meal plan from what's available right now.")
      }
    } catch (err) {
      setPlan(null)
      setPlanError(getErrorMessage(err, 'The Chef Brain had trouble planning that. Please try again.'))
    } finally {
      setPlanLoading(false)
    }
  }

  const generateShoppingList = async () => {
    if (!sessionId) return
    setListLoading(true)
    setListError('')
    try {
      const response = await chefChat({ sessionId, message: 'Generate my grocery list' })
      const data = response.data
      setSessionId(data.sessionId)
      if (data.shoppingList?.categories) {
        setShoppingList(data.shoppingList.categories)
      } else {
        setShoppingList([])
      }
    } catch (err) {
      setListError(getErrorMessage(err, "Couldn't build a grocery list right now. Please try again."))
    } finally {
      setListLoading(false)
    }
  }

  return (
    <div className="min-h-[calc(100vh-4rem)] max-w-3xl mx-auto px-4 sm:px-6 py-10">
      <div className="mb-8">
        <span className="eyebrow">Plan ahead</span>
        <h1 className="font-display text-3xl font-semibold text-ink-800 tracking-tight mt-1">
          Meal Plan
        </h1>
        <p className="text-sm text-ink-500 mt-1">
          A pantry-aware, personalized plan - Chef Brain picks every recipe, avoiding repeats.
        </p>
      </div>

      {/* Controls */}
      <div className="card mb-6 space-y-5">
        <div>
          <span className="text-xs font-semibold uppercase tracking-[0.14em] text-ink-500 block mb-2">
            Days
          </span>
          <div className="flex gap-2">
            {DAY_OPTIONS.map((d) => (
              <button
                key={d}
                onClick={() => setDays(d)}
                className={`flex-1 sm:flex-none sm:w-14 py-2.5 rounded-sheet text-sm font-semibold transition-colors ${
                  days === d
                    ? 'bg-moss-400 text-white'
                    : 'bg-paper-100 text-ink-600 hover:bg-paper-200'
                }`}
              >
                {d}
              </button>
            ))}
          </div>
        </div>
        <div>
          <span className="text-xs font-semibold uppercase tracking-[0.14em] text-ink-500 block mb-2">
            Meal type
          </span>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
            {MEAL_TYPE_OPTIONS.map((opt) => {
              const Icon = opt.icon
              const active = mealType === opt.value
              return (
                <button
                  key={opt.label}
                  onClick={() => setMealType(opt.value)}
                  className={`flex items-center justify-center gap-1.5 px-3 py-2.5 rounded-sheet text-sm font-medium transition-colors ${
                    active
                      ? 'bg-moss-400 text-white'
                      : 'bg-paper-100 text-ink-600 hover:bg-paper-200'
                  }`}
                >
                  <Icon className="w-3.5 h-3.5 flex-shrink-0" strokeWidth={1.75} />
                  {opt.label}
                </button>
              )
            })}
          </div>
        </div>

        <button
          onClick={generatePlan}
          disabled={planLoading}
          className="btn-primary w-full flex items-center justify-center gap-2 disabled:opacity-60 disabled:cursor-not-allowed"
        >
          {planLoading ? (
            <>
              <RefreshCw className="w-4 h-4 animate-spin" strokeWidth={1.75} />
              Planning...
            </>
          ) : (
            <>
              <CalendarDays className="w-4 h-4" strokeWidth={1.75} />
              {plan ? 'Regenerate plan' : 'Generate meal plan'}
            </>
          )}
        </button>

        {pantryCount !== null && (
          <p className="flex items-center justify-center gap-1.5 text-xs text-ink-400">
            <ShoppingBasket className="w-3.5 h-3.5" strokeWidth={1.75} />
            {pantryCount > 0
              ? `Using ${pantryCount} pantry item${pantryCount === 1 ? '' : 's'} to guide this plan`
              : 'Your pantry is empty - add a few items for a more tailored plan'}
          </p>
        )}
      </div>

      {planError && (
        <div className="flex items-center gap-3 text-sm text-brick-400 bg-brick-50 border border-brick-100 rounded-sheet px-4 py-3 mb-6">
          <AlertCircle className="w-4 h-4 flex-shrink-0" strokeWidth={1.75} />
          <span className="flex-1">{planError}</span>
          <button
            onClick={generatePlan}
            className="flex-shrink-0 font-semibold text-brick-500 hover:text-brick-600 transition-colors"
          >
            Try again
          </button>
        </div>
      )}

      {planLoading && (
        <div className="space-y-3 mb-6">
          {Array.from({ length: Math.min(days, 4) }).map((_, i) => (
            <DaySkeleton key={i} index={i} />
          ))}
        </div>
      )}

      {!planLoading && !plan && !planError && (
        <div className="card text-center py-12">
          <div className="w-12 h-12 bg-paper-200 rounded-sheet flex items-center justify-center mx-auto mb-4">
            <CalendarDays className="w-6 h-6 text-moss-400" strokeWidth={1.75} />
          </div>
          <p className="text-ink-700 font-medium mb-1">No plan yet</p>
          <p className="text-sm text-ink-500 max-w-sm mx-auto">
            Pick how many days and what kind of meal above, then generate - Chef Brain will
            build it from your pantry and preferences.
          </p>
        </div>
      )}

      {!planLoading && plan && plan.length > 0 && (
        <>
          <div className="space-y-3 mb-6">
            {plan.map((day, index) => (
              <DayCard
                key={index}
                day={day}
                index={index}
                expanded={expandedDay === index}
                onToggle={() => setExpandedDay(expandedDay === index ? -1 : index)}
              />
            ))}
          </div>

          {!shoppingList && (
            <button
              onClick={generateShoppingList}
              disabled={listLoading}
              className="btn-secondary w-full flex items-center justify-center gap-2 disabled:opacity-60 disabled:cursor-not-allowed mb-6"
            >
              {listLoading ? (
                <>
                  <RefreshCw className="w-4 h-4 animate-spin" strokeWidth={1.75} />
                  Building your list...
                </>
              ) : (
                <>
                  <ShoppingCart className="w-4 h-4" strokeWidth={1.75} />
                  Generate grocery list from this plan
                </>
              )}
            </button>
          )}

          {listError && (
            <div className="flex items-center gap-3 text-sm text-brick-400 bg-brick-50 border border-brick-100 rounded-sheet px-4 py-3 mb-6">
              <AlertCircle className="w-4 h-4 flex-shrink-0" strokeWidth={1.75} />
              <span className="flex-1">{listError}</span>
              <button
                onClick={generateShoppingList}
                className="flex-shrink-0 font-semibold text-brick-500 hover:text-brick-600 transition-colors"
              >
                Try again
              </button>
            </div>
          )}

          {shoppingList && (
            <ShoppingListView
              categories={shoppingList}
              checked={checked}
              onToggleItem={(key) => setChecked((prev) => ({ ...prev, [key]: !prev[key] }))}
            />
          )}
        </>
      )}

      {!planLoading && plan && plan.length === 0 && !planError && (
        <div className="card text-center py-12">
          <div className="w-12 h-12 bg-brick-50 rounded-sheet flex items-center justify-center mx-auto mb-4">
            <ChefHat className="w-6 h-6 text-brick-400" strokeWidth={1.75} />
          </div>
          <p className="text-ink-700 font-medium mb-1">Nothing to plan with yet</p>
          <p className="text-sm text-ink-500 max-w-sm mx-auto mb-4">
            Add a few ingredients to your pantry, or try a different meal type.
          </p>
          <button
            onClick={() => navigate('/pantry')}
            className="text-sm font-semibold text-brick-500 hover:text-brick-600 transition-colors"
          >
            Go to Pantry
          </button>
        </div>
      )}
    </div>
  )
}

export default MealPlan
