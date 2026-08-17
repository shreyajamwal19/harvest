/* eslint-disable react/prop-types -- this project doesn't use PropTypes, see AuthContext.jsx */
import { AnimatePresence, motion } from 'framer-motion'
import { X } from 'lucide-react'

/**
 * Fixed, bottom-center toast. `toast` is null when nothing should show, or
 * { id, tone: 'success' | 'neutral', icon, title, subtitle?, action? }.
 * `id` must change between calls (e.g. Date.now()) so re-triggering the same
 * message still re-animates in.
 */
function Toast({ toast, onDismiss }) {
  return (
    <div className="fixed inset-x-0 bottom-6 z-[60] flex justify-center px-4 pointer-events-none">
      <AnimatePresence>
        {toast && (
          <motion.div
            key={toast.id}
            initial={{ opacity: 0, y: 28, scale: 0.9 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 14, scale: 0.94, transition: { duration: 0.15 } }}
            transition={{ type: 'spring', stiffness: 420, damping: 28 }}
            className="pointer-events-auto flex items-center gap-3 bg-ink-800 text-paper-50 rounded-full pl-2.5 pr-3 py-2 shadow-lift max-w-[min(24rem,calc(100vw-2rem))]"
          >
            <span
              className={`flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center ${
                toast.tone === 'success' ? 'bg-moss-400' : 'bg-paper-50/15'
              }`}
            >
              {toast.icon}
            </span>
            <div className="min-w-0">
              <p className="text-sm font-medium truncate">{toast.title}</p>
              {toast.subtitle && (
                <p className="text-xs text-paper-50/55 truncate">{toast.subtitle}</p>
              )}
            </div>
            {toast.action}
            <button
              onClick={onDismiss}
              aria-label="Dismiss"
              className="flex-shrink-0 text-paper-50/45 hover:text-paper-50 transition-colors p-0.5"
            >
              <X className="w-3.5 h-3.5" strokeWidth={2} />
            </button>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

export default Toast
