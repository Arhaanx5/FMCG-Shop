import { useState, useRef, useEffect } from 'react'
import { Search, ChevronDown } from 'lucide-react'

export default function SearchSelect({ options = [], value, onChange, placeholder = 'Search...', labelKey = 'name', valueKey = 'id', renderOption, disabled = false }) {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const ref = useRef(null)

  const selected = options.find((o) => o[valueKey] === value)

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (ref.current && !ref.current.contains(e.target)) setOpen(false)
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const filtered = options.filter((o) =>
    String(o[labelKey] || '').toLowerCase().includes(query.toLowerCase())
  )

  return (
    <div ref={ref} style={{ position: 'relative' }}>
      <div
        className="form-input"
        onClick={() => !disabled && setOpen(!open)}
        style={{
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          cursor: disabled ? 'not-allowed' : 'pointer',
          opacity: disabled ? 0.5 : 1,
        }}
      >
        <span style={{ color: selected ? 'var(--color-text)' : 'var(--color-text-muted)', flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {selected ? (renderOption ? renderOption(selected) : selected[labelKey]) : placeholder}
        </span>
        <ChevronDown size={16} style={{ color: 'var(--color-text-muted)', flexShrink: 0, transform: open ? 'rotate(180deg)' : 'none', transition: 'transform 200ms' }} />
      </div>

      {open && (
        <div style={{
          position: 'absolute', top: '100%', left: 0, right: 0, marginTop: 4,
          background: 'var(--color-surface)', border: '1px solid var(--color-border)',
          borderRadius: 'var(--radius-md)', boxShadow: 'var(--shadow-lg)',
          zIndex: 'var(--z-dropdown)', maxHeight: 260, overflow: 'hidden',
          display: 'flex', flexDirection: 'column',
        }}>
          <div style={{ padding: 'var(--space-2)', borderBottom: '1px solid var(--color-border)' }}>
            <div className="search-bar" style={{ maxWidth: '100%' }}>
              <Search />
              <input
                className="form-input"
                placeholder="Type to search..."
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                autoFocus
                style={{ fontSize: 'var(--font-size-sm)' }}
              />
            </div>
          </div>
          <div style={{ overflowY: 'auto', flex: 1 }}>
            {filtered.length === 0 ? (
              <div style={{ padding: 'var(--space-4)', textAlign: 'center', color: 'var(--color-text-muted)', fontSize: 'var(--font-size-sm)' }}>
                No results found
              </div>
            ) : (
              filtered.map((opt) => (
                <div
                  key={opt[valueKey]}
                  onClick={() => { onChange(opt[valueKey]); setOpen(false); setQuery(''); }}
                  style={{
                    padding: 'var(--space-3) var(--space-4)',
                    cursor: 'pointer',
                    fontSize: 'var(--font-size-base)',
                    color: opt[valueKey] === value ? 'var(--color-accent)' : 'var(--color-text)',
                    background: opt[valueKey] === value ? 'var(--color-accent-soft)' : 'transparent',
                    transition: 'background 150ms',
                  }}
                  onMouseEnter={(e) => { e.currentTarget.style.background = 'var(--color-surface-hover)' }}
                  onMouseLeave={(e) => { e.currentTarget.style.background = opt[valueKey] === value ? 'var(--color-accent-soft)' : 'transparent' }}
                >
                  {renderOption ? renderOption(opt) : opt[labelKey]}
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  )
}
