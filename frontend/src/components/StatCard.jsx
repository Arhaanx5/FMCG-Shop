import { useEffect, useRef, useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { useOutletContext } from 'react-router-dom'

export default function StatCard({ icon, label, value, prefix = '', suffix = '', color = 'var(--color-accent)', delay = 0, description }) {
  const [displayValue, setDisplayValue] = useState(0)
  const numericValue = typeof value === 'number' ? value : parseFloat(value) || 0
  const hasAnimated = useRef(false)
  const [isHovered, setIsHovered] = useState(false)

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
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      style={{ position: 'relative' }}
    >
      <AnimatePresence>
        {isHovered && description && (
          <motion.div
            initial={{ opacity: 0, y: 10, scale: 0.95, x: '-50%' }}
            animate={{ opacity: 1, y: 0, scale: 1, x: '-50%' }}
            exit={{ opacity: 0, y: 10, scale: 0.95, x: '-50%' }}
            transition={{ duration: 0.2 }}
            style={{
              position: 'absolute',
              bottom: '100%',
              left: '50%',
              marginBottom: '12px',
              background: 'var(--color-dropdown-bg)',
              border: '1.5px solid var(--color-accent)',
              borderRadius: '12px',
              padding: '12px 16px',
              boxShadow: 'var(--shadow-xl)',
              zIndex: 9999,
              width: 'max-content',
              minWidth: '220px',
              pointerEvents: 'none'
            }}
          >
            <div style={{ fontSize: '10px', fontWeight: '700', color: 'var(--color-text-muted)', marginBottom: '8px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              Collections Breakdown
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
              {description.split('|').map((part, index) => {
                const [labelName, amountVal] = part.split(':').map(s => s.trim())
                let dotColor = 'var(--color-success)' // Cash
                if (labelName.toLowerCase().includes('upi')) dotColor = 'var(--color-info)' // UPI
                if (labelName.toLowerCase().includes('udhar') || labelName.toLowerCase().includes('recovery')) dotColor = 'var(--color-warning)' // Udhar Recovery
                
                return (
                  <div key={index} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '16px', fontSize: '12px', color: 'var(--color-text)' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: dotColor, display: 'inline-block', boxShadow: `0 0 6px ${dotColor}` }} />
                      <span style={{ color: 'var(--color-text-secondary)', fontWeight: '500' }}>{labelName}</span>
                    </div>
                    <span style={{ fontWeight: '700', color: 'var(--color-text)' }}>{amountVal}</span>
                  </div>
                )
              })}
            </div>
            {/* Small arrow pointing down */}
            <svg 
              width="14" 
              height="7" 
              viewBox="0 0 14 7" 
              style={{
                position: 'absolute',
                top: '100%',
                left: '50%',
                transform: 'translateX(-50%)',
                display: 'block',
                overflow: 'visible'
              }}
            >
              <polygon 
                points="0,0 7,7 14,0" 
                fill="var(--color-dropdown-bg)" 
                stroke="var(--color-accent)" 
                strokeWidth="1.5"
              />
              <line 
                x1="0" 
                y1="0" 
                x2="14" 
                y2="0" 
                stroke="var(--color-dropdown-bg)" 
                strokeWidth="2" 
              />
            </svg>
          </motion.div>
        )}
      </AnimatePresence>

      <div className={iconClass} style={iconStyle}>
        {icon}
      </div>
      <div className="stat-card-content">
        <div className="stat-card-value" style={fontStyle}>
          {fullText}
        </div>
        <div className="stat-card-label">{label}</div>
        {description && (
          <div className="stat-card-desc" style={{ fontSize: '9px', color: 'var(--color-accent)', marginTop: '4px', display: 'flex', alignItems: 'center', gap: '4px', fontWeight: '600' }}>
            <span style={{ width: '4px', height: '4px', borderRadius: '50%', background: 'var(--color-accent)', display: 'inline-block' }} className="pulse-dot" />
            Hover for breakdown
          </div>
        )}
      </div>
    </motion.div>
  )
}
