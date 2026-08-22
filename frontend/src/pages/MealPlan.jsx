/* eslint-disable react/prop-types -- this project doesn't use PropTypes, see AuthContext.jsx */
import { useState, useEffect, useRef, useCallback } from 'react'
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
  Shuffle,
  Bookmark,
  BookmarkX,
  Loader2,
} from 'lucide-react'
import {
  chefChat,
  getPantryItems,
  getSavedRecipes,
  saveRecipe,
  unsaveRecipe,
  regenerateMealPlanDay,
  getErrorMessage,
} from '../services/api'
import { CATEGORY_META, CATEGORY_ORDER } from '../utils/pantryCategories'
import Toast from '../components/Toast'

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

/** Horizontal week strip - a thumbnail per day, active one highlighted, tap to jump to it. */
function WeekStrip({ days, activeIndex, onSelect }) {
  if (days.length <= 1) return null
  return (
    <div className="flex gap-3 overflow-x-auto pb-2 mb-5 -mx-1 px-1">
      {days.map((day, index) => {
        const recipe = day.recipe
        const active = index === activeIndex
        return (
          <button
            key={index}
            onClick={() => onSelect(index)}
            className="flex-shrink-0 flex flex-col items-center gap-1.5 w-14 group"
          >
            <span
              className={`relative w-12 h-12 rounded-full overflow-hidden border-2 transition-colors ${
                active ? 'border-brick-500' : 'border-transparent group-hover:border-ink-700/15'
              }`}
            >
              {recipe?.imageUrl ? (
                <img src={recipe.imageUrl} alt="" className="w-full h-full object-cover" />
              ) : (
                <span className="w-full h-full flex items-center justify-center bg-paper-200">
                  <ChefHat className="w-4 h-4 text-moss-400" strokeWidth={1.75} />
                </span>
              )}
            </span>
            <span
              className={`text-[10px] font-semibold uppercase tracking-wide ${
                active ? 'text-brick-500' : 'text-ink-400'
              }`}
            >
              {day.dayLabel?.replace('Day ', 'D') || `D${index + 1}`}
            </span>
          </button>
        )
      })}
    </div>
  )
}

function DayCard({
  day,
  index,
  expanded,
  onToggle,
  innerRef,
  onSwap,
  swapping,
  saved,
  savePending,
  onToggleSave,
}) {
  const recipe = day.recipe
  const navigate = useNavigate()
  const [imageFailed, setImageFailed] = useState(false)
  const hasImage = recipe?.imageUrl && !imageFailed

  return (
    <motion.div
      ref={innerRef}
      layout
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.25, delay: index * 0.04 }}
      className="card !p-0 overflow-hidden scroll-mt-24"
    >
      <div className="flex items-center gap-1">
        <button
          onClick={onToggle}
          className="flex-1 min-w-0 flex items-center gap-3 px-4 sm:px-5 py-3.5 text-left"
        >
          {swapping ? (
            <span className="flex-shrink-0 w-12 h-12 rounded-full bg-paper-200 flex items-center justify-center">
              <Loader2 className="w-4 h-4 text-brick-400 animate-spin" strokeWidth={1.75} />
            </span>
          ) : hasImage ? (
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
          <span className="min-w-0 flex-1">
            <span className="block text-xs font-semibold uppercase tracking-[0.14em] text-moss-400">
              {day.dayLabel}
            </span>
            <span className="block font-display text-lg font-semibold text-ink-800 truncate">
              {swapping ? 'Finding something else\u2026' : recipe?.title || 'Untitled dish'}
            </span>
          </span>
          <ChevronDown
            className={`flex-shrink-0 w-4 h-4 text-ink-400 transition-transform ${expanded ? 'rotate-180' : ''}`}
            strokeWidth={1.75}
          />
        </button>
      </div>

      <AnimatePresence initial={false}>
        {expanded && recipe && !swapping && (
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

              <div className="flex items-center gap-2 pt-1">
                {recipe.steps?.length > 0 && (
                  <button
                    type="button"
                    onClick={() => navigate('/cook', { state: { recipe } })}
                    className="flex-1 flex items-center justify-center gap-2 text-sm font-semibold text-paper-50 bg-ink-800 hover:bg-ink-700 rounded-sheet py-3 transition-colors"
                  >
                    <ChefHat className="w-4 h-4" strokeWidth={1.75} />
                    Start Cooking
                  </button>
                )}
                <button
                  type="button"
                  onClick={onToggleSave}
                  disabled={savePending}
                  aria-pressed={saved}
                  aria-label={saved ? 'Remove from saved recipes' : 'Save this recipe'}
                  className={`flex-shrink-0 w-11 h-11 rounded-sheet flex items-center justify-center border transition-colors disabled:opacity-50 ${
                    saved
                      ? 'bg-brick-500 border-brick-500 text-paper-50'
                      : 'bg-paper-50 border-ink-700/15 text-ink-500 hover:text-brick-500 hover:border-brick-300'
                  }`}
                >
                  {savePending ? (
                    <Loader2 className="w-4 h-4 animate-spin" strokeWidth={2} />
                  ) : (
                    <Bookmark className="w-4 h-4" strokeWidth={2} fill={saved ? 'currentColor' : 'none'} />
                  )}
                </button>
                <button
                  type="button"
                  onClick={onSwap}
                  aria-label="Swap this day for a different recipe"
                  className="flex-shrink-0 w-11 h-11 rounded-sheet flex items-center justify-center border border-ink-700/15 bg-paper-50 text-ink-500 hover:text-moss-500 hover:border-moss-300 transition-colors"
                >
                  <Shuffle className="w-4 h-4" strokeWidth={1.75} />
                </button>
              </div>
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
  const [swappingIndex, setSwappingIndex] = useState(-1)

  const [shoppingList, setShoppingList] = useState(null)
  const [checked, setChecked] = useState({})
  const [listLoading, setListLoading] = useState(false)
  const [listError, setListError] = useState('')
  const resultsRef = useRef(null)
  const dayRefs = useRef({})

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
    let cancelled = false
    getPantryItems()
      .then((res) => !cancelled && setPantryCount(res.data.length))
      .catch(() => {})
    getSavedRecipes()
      .then((res) => {
        if (cancelled) return
        setSavedByTitle(new Map(res.data.map((s) => [s.recipe.title?.trim().toLowerCase(), s.id])))
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    if (planLoading) {
      resultsRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }
  }, [planLoading])

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

  const handleSwapDay = async (index) => {
    if (!plan || swappingIndex !== -1) return
    setSwappingIndex(index)
    try {
      const excludeTitles = plan.map((d) => d.recipe?.title).filter(Boolean)
      const response = await regenerateMealPlanDay({ excludeTitles, mealType })
      setPlan((prev) => prev.map((d, i) => (i === index ? { ...d, recipe: response.data.recipe } : d)))
    } catch (err) {
      showToast({
        tone: 'neutral',
        icon: <Shuffle className="w-4 h-4 text-paper-50" strokeWidth={2} />,
        title: getErrorMessage(err, "Couldn't find a different recipe right now"),
      })
    } finally {
      setSwappingIndex(-1)
    }
  }

  const handleToggleSave = async (recipe) => {
    const key = recipe?.title?.trim().toLowerCase()
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
        })
      }
    } catch (err) {
      showToast({
        tone: 'neutral',
        icon: <BookmarkX className="w-4 h-4 text-paper-50" strokeWidth={2} />,
        title: getErrorMessage(err, "Couldn't update saved recipes"),
      })
    } finally {
      setPendingTitles((prev) => {
        const next = new Set(prev)
        next.delete(key)
        return next
      })
    }
  }

  const jumpToDay = (index) => {
    setExpandedDay(index)
    requestAnimationFrame(() => {
      dayRefs.current[index]?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    })
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
              <motion.button
                key={d}
                whileTap={{ scale: 0.94 }}
                onClick={() => setDays(d)}
                className={`flex-1 sm:flex-none sm:w-14 py-2.5 rounded-sheet text-sm font-semibold transition-colors ${
                  days === d
                    ? 'bg-moss-400 text-white'
                    : 'bg-paper-100 text-ink-600 hover:bg-paper-200'
                }`}
              >
                {d}
              </motion.button>
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
                <motion.button
                  key={opt.label}
                  whileTap={{ scale: 0.96 }}
                  onClick={() => setMealType(opt.value)}
                  className={`flex items-center justify-center gap-1.5 px-3 py-2.5 rounded-sheet text-sm font-medium transition-colors ${
                    active
                      ? 'bg-moss-400 text-white'
                      : 'bg-paper-100 text-ink-600 hover:bg-paper-200'
                  }`}
                >
                  <Icon className="w-3.5 h-3.5 flex-shrink-0" strokeWidth={1.75} />
                  {opt.label}
                </motion.button>
              )
            })}
          </div>
        </div>

        <motion.button
          whileTap={{ scale: 0.98 }}
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
        </motion.button>

        {pantryCount !== null && (
          <p className="flex items-center justify-center gap-1.5 text-xs text-ink-400">
            <ShoppingBasket className="w-3.5 h-3.5" strokeWidth={1.75} />
            {pantryCount > 0
              ? `Using ${pantryCount} pantry item${pantryCount === 1 ? '' : 's'} to guide this plan`
              : 'Your pantry is empty - add a few items for a more tailored plan'}
          </p>
        )}
      </div>

      <div ref={resultsRef} className="scroll-mt-24">
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
            <WeekStrip days={plan} activeIndex={expandedDay} onSelect={jumpToDay} />

            <div className="space-y-3 mb-6">
              {plan.map((day, index) => {
                const key = day.recipe?.title?.trim().toLowerCase()
                return (
                  <DayCard
                    key={index}
                    day={day}
                    index={index}
                    expanded={expandedDay === index}
                    onToggle={() => setExpandedDay(expandedDay === index ? -1 : index)}
                    innerRef={(el) => {
                      dayRefs.current[index] = el
                    }}
                    onSwap={() => handleSwapDay(index)}
                    swapping={swappingIndex === index}
                    saved={savedByTitle.has(key)}
                    savePending={pendingTitles.has(key)}
                    onToggleSave={() => handleToggleSave(day.recipe)}
                  />
                )
              })}
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

      <Toast toast={toast} onDismiss={() => setToast(null)} />
    </div>
  )
}

export default MealPlan
