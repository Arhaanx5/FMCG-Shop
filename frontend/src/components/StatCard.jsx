import { useEffect, useRef, useState } from 'react'
import { motion } from 'framer-motion'
import { useOutletContext } from 'react-router-dom'

export default function StatCard({ icon, label, value, prefix = '', suffix = '', color = 'var(--color-accent)', delay = 0 }) {
  const [displayValue, setDisplayValue] = useState(0)
  const numericValue = typeof value === 'number' ? value : parseFloat(value) || 0
  const hasAnimated = useRef(false)

  // Safe retrieval of uiTheme from router context
  let uiTheme = 'modern'
  try {
    const context = useOutletContext()
    if (context && context[0]) {
      uiTheme = context[0]
    }
  } catch (e) {
    // Fallback if not in a routing context
  }

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

  const softBg = `rgba(${color === 'var(--color-accent)' ? '245, 158, 11' : color === 'var(--color-success)' ? '16, 185, 129' : color === 'var(--color-danger)' ? '239, 68, 68' : color === 'var(--color-info)' ? '59, 130, 246' : '245, 158, 11'}, 0.12)`

  const isModern = uiTheme === 'modern'
  const isCyber = uiTheme === 'cyber'
  const isNeon = uiTheme === 'neon'
  const isCustomTheme = isModern || isCyber || isNeon

  let iconClass = 'stat-card-icon'
  let iconStyle = { background: softBg, color }

  if (isCustomTheme) {
    iconStyle = {} // Styles come from CSS gradients
    if (isCyber) {
      if (color === 'var(--color-accent)') iconClass += ' stat-card-icon-cyber'
      else if (color === 'var(--color-info)') iconClass += ' stat-card-icon-info'
      else if (color === 'var(--color-success)') iconClass += ' stat-card-icon-success'
      else if (color === 'var(--color-danger)') iconClass += ' stat-card-icon-danger'
      else iconClass += ' stat-card-icon-cyber'
    } else if (isNeon) {
      if (color === 'var(--color-accent)') iconClass += ' stat-card-icon-neon'
      else if (color === 'var(--color-info)') iconClass += ' stat-card-icon-info'
      else if (color === 'var(--color-success)') iconClass += ' stat-card-icon-success'
      else if (color === 'var(--color-danger)') iconClass += ' stat-card-icon-danger'
      else iconClass += ' stat-card-icon-neon'
    } else {
      // modern
      if (color === 'var(--color-accent)') iconClass += ' stat-card-icon-accent'
      else if (color === 'var(--color-info)') iconClass += ' stat-card-icon-info'
      else if (color === 'var(--color-success)') iconClass += ' stat-card-icon-success'
      else if (color === 'var(--color-danger)') iconClass += ' stat-card-icon-danger'
      else iconClass += ' stat-card-icon-accent'
    }
  }

  const fullText = `${prefix}${displayValue.toLocaleString('en-IN')}${suffix}`
  const fontStyle = {}
  if (fullText.length >= 11) {
    fontStyle.fontSize = 'var(--font-size-sm)' // 13px
  } else if (fullText.length === 10) {
    fontStyle.fontSize = 'var(--font-size-base)' // 14px
  } else if (fullText.length === 9) {
    fontStyle.fontSize = '1.05rem' // ~16.8px
  } else if (fullText.length === 8) {
    fontStyle.fontSize = '1.15rem' // ~18.4px
  }

  return (
    <motion.div
      className={`stat-card ${uiTheme !== 'classic' ? 'card-lift' : ''}`}
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, delay: delay * 0.1 }}
    >
      <div className={iconClass} style={iconStyle}>
        {icon}
      </div>
      <div className="stat-card-content">
        <div className="stat-card-value" style={fontStyle}>
          {fullText}
        </div>
        <div className="stat-card-label">{label}</div>
      </div>
    </motion.div>
  )
}
