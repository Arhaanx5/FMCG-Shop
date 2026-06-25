import { AnimatePresence, motion } from 'framer-motion'
import { X } from 'lucide-react'

export default function Modal({ isOpen, onClose, title, children, wide, xl }) {
  if (!isOpen) return null

  // Tailwind size configurations
  const widthClass = xl ? 'max-w-5xl' : wide ? 'max-w-2xl' : 'max-w-lg'

  return (
    <AnimatePresence>
      {isOpen && (
        <motion.div
          className="fixed inset-0 bg-slate-950/70 backdrop-blur-sm flex items-center justify-center z-[500] p-4"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={onClose}
        >
          <motion.div
            className={`bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-theme shadow-2xl w-full p-6 md:p-8 overflow-y-auto max-h-[90vh] ${widthClass}`}
            initial={{ opacity: 0, scale: 0.96, y: 12 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.96, y: 12 }}
            transition={{ type: 'spring', stiffness: 380, damping: 30 }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between mb-5 pb-4 border-b border-slate-100 dark:border-slate-700">
              <h2 className="text-lg md:text-xl font-bold text-slate-900 dark:text-slate-50">{title}</h2>
              <button
                className="p-1 rounded-theme-sm text-slate-400 dark:text-slate-500 hover:text-slate-600 dark:hover:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-700/60 transition-colors duration-150"
                onClick={onClose}
              >
                <X size={20} />
              </button>
            </div>
            <div>{children}</div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
