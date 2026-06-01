import { motion } from 'framer-motion'

export default function LoadingScreen() {
  return (
    <div style={{
      position: 'fixed', inset: 0,
      display: 'flex', flexDirection: 'column',
      alignItems: 'center', justifyContent: 'center',
      background: 'var(--color-bg)',
      zIndex: 9999,
    }}>
      <motion.div
        initial={{ opacity: 0, scale: 0.8 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ duration: 0.4 }}
        style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 'var(--space-6)' }}
      >
        <motion.div
          animate={{
            boxShadow: [
              '0 0 20px rgba(245, 158, 11, 0.2)',
              '0 0 40px rgba(245, 158, 11, 0.4)',
              '0 0 20px rgba(245, 158, 11, 0.2)',
            ],
          }}
          transition={{ duration: 2, repeat: Infinity }}
          style={{
            width: 64, height: 64, borderRadius: 'var(--radius-lg)',
            background: 'linear-gradient(135deg, var(--color-accent), #d97706)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: '28px', fontWeight: 800, color: 'var(--color-text-inverse)',
            fontFamily: 'var(--font-family)',
          }}
        >
          LT
        </motion.div>
        <div style={{ textAlign: 'center' }}>
          <div style={{
            fontSize: 'var(--font-size-xl)', fontWeight: 'var(--font-weight-bold)',
            color: 'var(--color-text)', marginBottom: 'var(--space-2)',
          }}>
            Lari Traders
          </div>
          <div style={{ color: 'var(--color-text-muted)', fontSize: 'var(--font-size-sm)' }}>
            Loading...
          </div>
        </div>
        <div className="spinner spinner-lg" />
      </motion.div>
    </div>
  )
}
