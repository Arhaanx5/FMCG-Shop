import { motion } from 'framer-motion'
import { AlertTriangle } from 'lucide-react'
import Modal from './Modal'

export default function ConfirmDialog({ isOpen, onClose, onConfirm, title, message, confirmLabel = 'Delete', danger = true }) {
  return (
    <Modal isOpen={isOpen} onClose={onClose} title={title || 'Confirm Action'}>
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center', gap: 'var(--space-4)', padding: 'var(--space-4) 0' }}>
        <motion.div
          initial={{ scale: 0 }}
          animate={{ scale: 1 }}
          transition={{ type: 'spring', stiffness: 300, damping: 20 }}
          style={{
            width: 56, height: 56, borderRadius: 'var(--radius-full)',
            background: danger ? 'var(--color-danger-soft)' : 'var(--color-warning-soft)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}
        >
          <AlertTriangle size={28} color={danger ? 'var(--color-danger)' : 'var(--color-warning)'} />
        </motion.div>
        <p style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--font-size-base)', maxWidth: 360 }}>
          {message || 'Are you sure? This action cannot be undone.'}
        </p>
      </div>
      <div className="form-actions">
        <button className="btn btn-secondary" onClick={onClose}>Cancel</button>
        <motion.button
          className={`btn ${danger ? 'btn-danger' : 'btn-primary'}`}
          onClick={onConfirm}
          whileTap={{ scale: 0.95 }}
        >
          {confirmLabel}
        </motion.button>
      </div>
    </Modal>
  )
}
