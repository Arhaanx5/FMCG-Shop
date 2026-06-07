import { ChevronLeft, ChevronRight } from 'lucide-react'

/**
 * Reusable server-side pagination component.
 * Props:
 *   page          - current 0-indexed page number
 *   totalPages    - total number of pages from server
 *   totalElements - total record count from server
 *   pageSize      - records per page (for display)
 *   onPageChange  - callback(newPage: number)
 */
export default function Pagination({ page, totalPages, totalElements, pageSize, onPageChange }) {
  if (!totalPages || totalPages <= 1) return null

  const from = page * pageSize + 1
  const to = Math.min((page + 1) * pageSize, totalElements)

  // Build visible page numbers (max 5 shown, centered around current)
  const getPageNumbers = () => {
    const maxVisible = 5
    if (totalPages <= maxVisible) {
      return Array.from({ length: totalPages }, (_, i) => i)
    }
    const start = Math.max(0, Math.min(page - 2, totalPages - maxVisible))
    return Array.from({ length: maxVisible }, (_, i) => start + i)
  }

  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      flexWrap: 'wrap',
      gap: 'var(--space-3)',
      marginTop: 'var(--space-5)',
      padding: 'var(--space-3) 0',
    }}>
      {/* Record count */}
      <span style={{
        fontSize: 'var(--font-size-sm)',
        color: 'var(--color-text-muted)',
      }}>
        Showing <strong style={{ color: 'var(--color-text)' }}>{from}–{to}</strong> of{' '}
        <strong style={{ color: 'var(--color-text)' }}>{totalElements}</strong> records
      </span>

      {/* Page buttons */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-1)' }}>
        {/* Previous */}
        <button
          className="btn btn-ghost btn-icon btn-sm"
          disabled={page === 0}
          onClick={() => onPageChange(page - 1)}
          title="Previous page"
          style={{ borderRadius: 'var(--radius-md)' }}
        >
          <ChevronLeft size={16} />
        </button>

        {/* Page numbers */}
        {getPageNumbers().map((p) => (
          <button
            key={p}
            onClick={() => onPageChange(p)}
            style={{
              minWidth: '32px',
              height: '32px',
              padding: '0 var(--space-2)',
              fontSize: 'var(--font-size-sm)',
              fontWeight: page === p ? 'var(--font-weight-bold)' : 'var(--font-weight-normal)',
              borderRadius: 'var(--radius-md)',
              border: `1px solid ${page === p ? 'var(--color-accent)' : 'var(--color-border)'}`,
              background: page === p ? 'var(--color-accent)' : 'var(--color-surface)',
              color: page === p ? 'var(--color-text-inverse)' : 'var(--color-text-secondary)',
              cursor: 'pointer',
              transition: 'all var(--transition-fast)',
            }}
          >
            {p + 1}
          </button>
        ))}

        {/* Next */}
        <button
          className="btn btn-ghost btn-icon btn-sm"
          disabled={page >= totalPages - 1}
          onClick={() => onPageChange(page + 1)}
          title="Next page"
          style={{ borderRadius: 'var(--radius-md)' }}
        >
          <ChevronRight size={16} />
        </button>
      </div>
    </div>
  )
}
