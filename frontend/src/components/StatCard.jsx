import { useEffect, useRef, useState } from 'react'
import { motion } from 'framer-motion'
import { useOutletContext } from 'react-router-dom'

export default function StatCard({ icon, label, value, prefix = '', suffix = '', color = 'var(--color-accent)', delay = 0, description }) {
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
      style={{ position: 'relative' }}
    >
      <style>{`
        .stat-card:hover {
          z-index: 210;
        }
        .stat-card-desc-tooltip {
          position: absolute;
          bottom: 105%;
          left: 50%;
          transform: translateX(-50%) translateY(10px);
          background: rgba(15, 23, 42, 0.95);
          backdrop-filter: blur(8px);
          border: 1px solid rgba(255, 255, 255, 0.1);
          border-radius: var(--radius-md);
          padding: 8px 12px;
          width: max-content;
          max-width: 280px;
          opacity: 0;
          visibility: hidden;
          transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
          z-index: var(--z-tooltip, 500);
          pointer-events: none;
          box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.3), 0 8px 10px -6px rgba(0, 0, 0, 0.3);
          color: #fff;
        }
        .stat-card:hover .stat-card-desc-tooltip {
          opacity: 1;
          visibility: visible;
          transform: translateX(-50%) translateY(0);
        }
        .stat-card-tooltip-arrow {
          position: absolute;
          top: 100%;
          left: 50%;
          transform: translateX(-50%);
          border-width: 6px;
          border-style: solid;
          border-color: rgba(15, 23, 42, 0.95) transparent transparent transparent;
        }
      `}</style>

      <div className={iconClass} style={iconStyle}>
        {icon}
      </div>
      <div className="stat-card-content">
        <div className="stat-card-value" style={fontStyle}>
          {fullText}
        </div>
        <div className="stat-card-label">{label}</div>
      </div>

      {description && (
        <div className="stat-card-desc-tooltip">
          <div style={{ fontWeight: '600', fontSize: '11px', color: 'rgba(255, 255, 255, 0.7)', marginBottom: '8px', borderBottom: '1px solid rgba(255, 255, 255, 0.1)', paddingBottom: '4px' }}>
            Collection Breakdown
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
            {description.split('|').map((part, index) => {
              const [labelName, amountVal] = part.split(':').map(s => s.trim())
              let dotColor = 'var(--color-success)' // Cash
              if (labelName.toLowerCase().includes('upi')) dotColor = 'var(--color-info)' // UPI
              if (labelName.toLowerCase().includes('udhar') || labelName.toLowerCase().includes('recovery')) dotColor = 'var(--color-warning)' // Udhar Recovery
              
              return (
                <div key={index} style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '11px', whiteSpace: 'nowrap' }}>
                  <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: dotColor }} />
                  <span style={{ color: 'rgba(255, 255, 255, 0.7)', minWidth: '90px' }}>{labelName}:</span>
                  <span style={{ fontWeight: '750', color: '#fff' }}>{amountVal}</span>
                </div>
              )
            })}
          </div>
          <div className="stat-card-tooltip-arrow" />
        </div>
      )}
    </motion.div>
  )
}
