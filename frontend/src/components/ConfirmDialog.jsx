import { motion } from 'framer-motion'
import { AlertTriangle } from 'lucide-react'
import Modal from './Modal'

export default function ConfirmDialog({ isOpen, onClose, onConfirm, title, message, confirmLabel = 'Delete', danger = true }) {
  return (
    <Modal isOpen={isOpen} onClose={onClose} title={title || 'Confirm Action'}>
      <div className="flex flex-col items-center text-center gap-4 py-4">
        <motion.div
          initial={{ scale: 0 }}
          animate={{ scale: 1 }}
          transition={{ type: 'spring', stiffness: 300, damping: 20 }}
          className={`w-14 h-14 rounded-full flex items-center justify-center ${
            danger ? 'bg-red-50 dark:bg-red-950/30 text-red-500' : 'bg-amber-50 dark:bg-amber-950/30 text-amber-500'
          }`}
        >
          <AlertTriangle size={28} />
        </motion.div>
        <p className="text-slate-600 dark:text-slate-300 text-sm md:text-base max-w-sm">
          {message || 'Are you sure? This action cannot be undone.'}
        </p>
      </div>
      <div className="flex items-center justify-end gap-3 pt-5 mt-5 border-t border-slate-100 dark:border-slate-700">
        <button
          type="button"
          className="px-4 py-2 text-sm font-medium border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700/60 transition-colors duration-150 rounded-theme-sm cursor-pointer"
          onClick={onClose}
        >
          Cancel
        </button>
        <motion.button
          className={`px-4 py-2 text-sm font-medium text-slate-950 bg-amber-500 hover:bg-amber-600 transition-colors duration-150 rounded-theme-sm cursor-pointer shadow-sm ${
            danger ? 'bg-red-600 hover:bg-red-700 text-white' : ''
          }`}
          onClick={onConfirm}
          whileTap={{ scale: 0.96 }}
        >
          {confirmLabel}
        </motion.button>
      </div>
    </Modal>
  )
}
