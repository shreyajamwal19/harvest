/* eslint-disable react/prop-types -- this project doesn't use PropTypes, see AuthContext.jsx */
import { useState, useEffect, useCallback, useMemo } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Plus, X, AlertCircle, Clock, Trash2, ShoppingBasket, Minus } from 'lucide-react'
import {
  getPantryItems,
  addPantryItem,
  removePantryItem,
  updatePantryItemQuantity,
  clearPantry,
  getErrorMessage,
} from '../services/api'
import Loading from '../components/Loading'
import { CATEGORY_META, CATEGORY_ORDER } from '../utils/pantryCategories'

function formatQuantity(item) {
  if (item.quantity == null) return null
  const amount = Number.isInteger(item.quantity) ? item.quantity : item.quantity.toFixed(2)
  return item.unit ? `${amount} ${item.unit}` : `${amount}`
}

function capitalize(text) {
  return text.charAt(0).toUpperCase() + text.slice(1)
}

function QuantityStepper({ item, onChange, disabled }) {
  const step = Number.isInteger(item.quantity) ? 1 : 0.5
  return (
    <span className="flex-shrink-0 flex items-center gap-1 bg-paper-200 rounded-full p-0.5">
      <button
        type="button"
        onClick={() => onChange(item.id, Math.max(0, item.quantity - step))}
        disabled={disabled}
        aria-label={`Decrease ${item.ingredientName} quantity`}
        className="w-5 h-5 rounded-full flex items-center justify-center text-ink-500 hover:bg-paper-50 hover:text-brick-500 transition-colors disabled:opacity-40"
      >
        <Minus className="w-3 h-3" strokeWidth={2} />
      </button>
      <span className="text-xs font-medium text-ink-600 min-w-[2.5rem] text-center tabular-nums">
        {formatQuantity(item)}
      </span>
      <button
        type="button"
        onClick={() => onChange(item.id, item.quantity + step)}
        disabled={disabled}
        aria-label={`Increase ${item.ingredientName} quantity`}
        className="w-5 h-5 rounded-full flex items-center justify-center text-ink-500 hover:bg-paper-50 hover:text-moss-500 transition-colors disabled:opacity-40"
      >
        <Plus className="w-3 h-3" strokeWidth={2} />
      </button>
    </span>
  )
}

function PantryItemRow({ item, onRemove, onQuantityChange, removing }) {
  return (
    <motion.li
      layout
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, x: -8 }}
      transition={{ duration: 0.2, ease: [0.22, 1, 0.36, 1] }}
      className="flex items-center justify-between gap-3 px-4 py-3 bg-paper-50 border border-ink-700/10 rounded-sheet"
    >
      <div className="flex items-center gap-2 min-w-0 flex-wrap">
        <span className="text-sm font-medium text-ink-800 truncate">
          {capitalize(item.ingredientName)}
        </span>
        {item.expiringSoon && (
          <span className="flex-shrink-0 inline-flex items-center gap-1 text-xs font-medium text-brick-400 bg-brick-50 border border-brick-100 rounded-full px-2 py-0.5">
            <Clock className="w-3 h-3" />
            Expiring soon
          </span>
        )}
      </div>
      <div className="flex items-center gap-2 flex-shrink-0">
        {item.quantity != null ? (
          <QuantityStepper item={item} onChange={onQuantityChange} disabled={removing} />
        ) : (
          <span className="text-xs text-ink-400 italic">no amount set</span>
        )}
        <button
          onClick={() => onRemove(item.id)}
          disabled={removing}
          aria-label={`Remove ${item.ingredientName}`}
          className="p-1.5 rounded-sheet text-ink-400 hover:text-brick-400 hover:bg-brick-50 transition-colors disabled:opacity-40"
        >
          <X className="w-4 h-4" strokeWidth={1.75} />
        </button>
      </div>
    </motion.li>
  )
}

function Pantry() {
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [removingId, setRemovingId] = useState(null)

  const [name, setName] = useState('')
  const [quantity, setQuantity] = useState('')
  const [unit, setUnit] = useState('')
  const [adding, setAdding] = useState(false)
  const [addError, setAddError] = useState('')

  const loadItems = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const response = await getPantryItems()
      setItems(response.data)
    } catch (err) {
      setError(getErrorMessage(err, 'Could not load your pantry. Please try again.'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadItems()
  }, [loadItems])

  const grouped = useMemo(() => {
    const map = {}
    for (const item of items) {
      const key = CATEGORY_META[item.category] ? item.category : 'OTHER'
      if (!map[key]) map[key] = []
      map[key].push(item)
    }
    return map
  }, [items])

  const expiringItems = useMemo(() => items.filter((i) => i.expiringSoon), [items])

  const handleAdd = async (e) => {
    e.preventDefault()
    const trimmed = name.trim()
    if (!trimmed || adding) return
    setAdding(true)
    setAddError('')
    try {
      const response = await addPantryItem({
        ingredientName: trimmed,
        quantity: quantity.trim() ? Number(quantity) : undefined,
        unit: unit.trim() || undefined,
      })
      setItems((prev) => {
        const withoutExisting = prev.filter((i) => i.id !== response.data.id)
        return [...withoutExisting, response.data]
      })
      setName('')
      setQuantity('')
      setUnit('')
    } catch (err) {
      setAddError(getErrorMessage(err, "Couldn't add that item. Please try again."))
    } finally {
      setAdding(false)
    }
  }

  const handleRemove = async (itemId) => {
    setRemovingId(itemId)
    const previous = items
    setItems((prev) => prev.filter((i) => i.id !== itemId))
    try {
      await removePantryItem(itemId)
    } catch (err) {
      setItems(previous)
      setError(getErrorMessage(err, "Couldn't remove that item. Please try again."))
    } finally {
      setRemovingId(null)
    }
  }

  const handleQuantityChange = async (itemId, newQuantity) => {
    const previous = items
    if (newQuantity <= 0) {
      setItems((prev) => prev.filter((i) => i.id !== itemId))
    } else {
      setItems((prev) => prev.map((i) => (i.id === itemId ? { ...i, quantity: newQuantity } : i)))
    }
    try {
      const response = await updatePantryItemQuantity(itemId, newQuantity)
      if (response.data) {
        setItems((prev) => prev.map((i) => (i.id === itemId ? response.data : i)))
      }
    } catch (err) {
      setItems(previous)
      setError(getErrorMessage(err, "Couldn't update that quantity. Please try again."))
    }
  }

  const handleClearAll = async () => {
    if (items.length === 0) return
    if (!window.confirm('Clear your entire pantry? This cannot be undone.')) return
    const previous = items
    setItems([])
    try {
      await clearPantry()
    } catch (err) {
      setItems(previous)
      setError(getErrorMessage(err, "Couldn't clear your pantry. Please try again."))
    }
  }

  return (
    <div className="min-h-[calc(100vh-4rem)] max-w-3xl mx-auto px-4 sm:px-6 py-10">
      <div className="flex items-start justify-between gap-3 mb-6">
        <div>
          <span className="eyebrow">Kitchen</span>
          <h1 className="font-display text-3xl font-semibold text-ink-800 tracking-tight mt-1">
            My Pantry
          </h1>
          <p className="text-sm text-ink-500 mt-1">
            What Chef Brain knows you have on hand
            {items.length > 0 && (
              <span className="text-ink-400"> &middot; {items.length} item{items.length === 1 ? '' : 's'}</span>
            )}
            .
          </p>
        </div>
        {items.length > 0 && (
          <button
            onClick={handleClearAll}
            className="flex-shrink-0 flex items-center gap-1.5 text-xs font-medium text-brick-400 hover:text-brick-500 hover:bg-brick-50 rounded-sheet px-3 py-2 transition-colors"
          >
            <Trash2 className="w-3.5 h-3.5" strokeWidth={1.75} />
            Clear all
          </button>
        )}
      </div>

      {expiringItems.length > 0 && (
        <motion.div
          initial={{ opacity: 0, y: -6 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3 }}
          className="mb-6 rounded-sheet border border-brick-200 bg-brick-50 px-4 py-3.5"
        >
          <div className="flex items-center gap-2 mb-2.5">
            <Clock className="w-4 h-4 text-brick-500" strokeWidth={1.75} />
            <span className="text-xs font-semibold uppercase tracking-[0.14em] text-brick-600">
              Use these soon
            </span>
          </div>
          <div className="flex flex-wrap gap-2">
            {expiringItems.map((item) => (
              <span
                key={item.id}
                className="inline-flex items-center gap-1.5 text-sm text-ink-700 bg-paper-50 border border-brick-100 rounded-full pl-3 pr-1.5 py-1"
              >
                {capitalize(item.ingredientName)}
                <button
                  onClick={() => handleRemove(item.id)}
                  aria-label={`Remove ${item.ingredientName}`}
                  className="w-4 h-4 rounded-full flex items-center justify-center text-ink-400 hover:text-brick-500 hover:bg-brick-50 transition-colors"
                >
                  <X className="w-2.5 h-2.5" strokeWidth={2} />
                </button>
              </span>
            ))}
          </div>
        </motion.div>
      )}

      {/* Quick add form */}
      <form onSubmit={handleAdd} className="card mb-6 !p-4">
        <div className="flex flex-col sm:flex-row gap-2">
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Add an ingredient, e.g. eggs"
            className="input-field flex-1"
            disabled={adding}
          />
          <div className="flex gap-2">
            <input
              type="number"
              min="0"
              step="any"
              value={quantity}
              onChange={(e) => setQuantity(e.target.value)}
              placeholder="Qty"
              className="input-field w-20"
              disabled={adding}
            />
            <input
              type="text"
              value={unit}
              onChange={(e) => setUnit(e.target.value)}
              placeholder="Unit"
              className="input-field w-24"
              disabled={adding}
            />
            <button
              type="submit"
              disabled={adding || !name.trim()}
              className="btn-primary px-4 disabled:opacity-60 disabled:cursor-not-allowed"
              aria-label="Add item"
            >
              <Plus className="w-4 h-4" strokeWidth={1.75} />
            </button>
          </div>
        </div>
        {addError && (
          <p className="mt-2 text-xs text-brick-400">{addError}</p>
        )}
      </form>

      {error && (
        <div className="flex items-center gap-2 text-sm text-brick-400 bg-brick-50 border border-brick-100 rounded-sheet px-4 py-2.5 mb-4">
          <AlertCircle className="w-4 h-4 flex-shrink-0" strokeWidth={1.75} />
          {error}
        </div>
      )}

      {loading ? (
        <Loading />
      ) : items.length === 0 ? (
        <div className="card text-center py-12">
          <div className="w-12 h-12 bg-paper-200 rounded-sheet flex items-center justify-center mx-auto mb-4">
            <ShoppingBasket className="w-6 h-6 text-moss-400" strokeWidth={1.75} />
          </div>
          <p className="text-ink-700 font-medium mb-1">Your pantry is empty</p>
          <p className="text-sm text-ink-500 max-w-sm mx-auto">
            Add what you have above, or just tell Chef Brain in chat -
            &ldquo;I bought eggs and spinach&rdquo; works too, and it&apos;ll show up here.
          </p>
        </div>
      ) : (
        <div className="space-y-6">
          {CATEGORY_ORDER.filter((cat) => grouped[cat]?.length).map((cat) => {
            const { label, icon: Icon } = CATEGORY_META[cat]
            return (
              <div key={cat}>
                <div className="flex items-center gap-2 mb-2">
                  <Icon className="w-4 h-4 text-moss-400" strokeWidth={1.75} />
                  <h2 className="text-xs font-semibold uppercase tracking-[0.14em] text-ink-500">
                    {label}
                  </h2>
                  <span className="text-xs text-ink-400">({grouped[cat].length})</span>
                </div>
                <ul className="space-y-2">
                  <AnimatePresence initial={false}>
                    {grouped[cat].map((item) => (
                      <PantryItemRow
                        key={item.id}
                        item={item}
                        onRemove={handleRemove}
                        onQuantityChange={handleQuantityChange}
                        removing={removingId === item.id}
                      />
                    ))}
                  </AnimatePresence>
                </ul>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

export default Pantry
