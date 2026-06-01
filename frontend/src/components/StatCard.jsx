import { useEffect, useRef, useState } from 'react'
import { motion } from 'framer-motion'

export default function StatCard({ icon, label, value, prefix = '', suffix = '', color = 'var(--color-accent)', delay = 0 }) {
  const [displayValue, setDisplayValue] = useState(0)
  const numericValue = typeof value === 'number' ? value : parseFloat(value) || 0
  const hasAnimated = useRef(false)

  useEffect(() => {
    const duration = 1000
    const startTime = Date.now()
    const startValue = displayValue
    const change = numericValue - startValue
    
    const timer = setInterval(() => {
      const elapsed = Date.now() - startTime
      const progress = Math.min(elapsed / duration, 1)
      // Ease out cubic
      const eased = 1 - Math.pow(1 - progress, 3)
      setDisplayValue(Math.round(startValue + change * eased))
      if (progress >= 1) clearInterval(timer)
    }, 16)
    return () => clearInterval(timer)
  }, [numericValue])

  const bgColor = color.replace(')', ', 0.12)').replace('rgb', 'rgba').replace('var(', '')
  const softBg = `rgba(${color === 'var(--color-accent)' ? '245, 158, 11' : color === 'var(--color-success)' ? '16, 185, 129' : color === 'var(--color-danger)' ? '239, 68, 68' : color === 'var(--color-info)' ? '59, 130, 246' : '245, 158, 11'}, 0.12)`

  return (
    <motion.div
      className="stat-card"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, delay: delay * 0.1 }}
    >
      <div className="stat-card-icon" style={{ background: softBg, color }}>
        {icon}
      </div>
      <div className="stat-card-content">
        <div className="stat-card-value">
          {prefix}{displayValue.toLocaleString('en-IN')}{suffix}
        </div>
        <div className="stat-card-label">{label}</div>
      </div>
    </motion.div>
  )
}
