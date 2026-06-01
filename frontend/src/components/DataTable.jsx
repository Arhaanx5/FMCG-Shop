import { useState, useMemo } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { ChevronUp, ChevronDown, ChevronLeft, ChevronRight, Search, Package } from 'lucide-react'

export default function DataTable({
  columns,
  data = [],
  searchable = true,
  searchPlaceholder = 'Search...',
  pageSize = 10,
  onRowClick,
  actions,
  emptyMessage = 'No data found',
  emptyIcon,
  loading = false,
}) {
  const [search, setSearch] = useState('')
  const [sortKey, setSortKey] = useState(null)
  const [sortDir, setSortDir] = useState('asc')
  const [currentPage, setCurrentPage] = useState(0)

  const handleSort = (key) => {
    if (!key) return
    if (sortKey === key) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'))
    } else {
      setSortKey(key)
      setSortDir('asc')
    }
  }

  const filteredData = useMemo(() => {
    let result = [...data]
    if (search.trim()) {
      const q = search.toLowerCase()
      result = result.filter((row) =>
        columns.some((col) => {
          const val = col.accessor ? (typeof col.accessor === 'function' ? col.accessor(row) : row[col.accessor]) : ''
          return String(val).toLowerCase().includes(q)
        })
      )
    }
    if (sortKey) {
      const col = columns.find((c) => c.key === sortKey || c.accessor === sortKey)
      if (col) {
        result.sort((a, b) => {
          const aVal = typeof col.accessor === 'function' ? col.accessor(a) : a[col.accessor]
          const bVal = typeof col.accessor === 'function' ? col.accessor(b) : b[col.accessor]
          if (aVal == null) return 1
          if (bVal == null) return -1
          if (typeof aVal === 'number' && typeof bVal === 'number') {
            return sortDir === 'asc' ? aVal - bVal : bVal - aVal
          }
          return sortDir === 'asc'
            ? String(aVal).localeCompare(String(bVal))
            : String(bVal).localeCompare(String(aVal))
        })
      }
    }
    return result
  }, [data, search, sortKey, sortDir, columns])

  const totalPages = Math.max(1, Math.ceil(filteredData.length / pageSize))
  const paginatedData = filteredData.slice(currentPage * pageSize, (currentPage + 1) * pageSize)

  // Reset to page 0 on search
  const handleSearch = (val) => {
    setSearch(val)
    setCurrentPage(0)
  }

  if (loading) {
    return (
      <div className="card" style={{ padding: 'var(--space-8)', textAlign: 'center' }}>
        <div className="spinner spinner-lg" style={{ margin: '0 auto' }} />
        <p className="text-muted mt-4">Loading data...</p>
      </div>
    )
  }

  return (
    <div>
      {searchable && (
        <div className="search-bar" style={{ marginBottom: 'var(--space-4)' }}>
          <Search />
          <input
            className="form-input"
            placeholder={searchPlaceholder}
            value={search}
            onChange={(e) => handleSearch(e.target.value)}
          />
        </div>
      )}

      <div className="table-container">
        <table className="table">
          <thead>
            <tr>
              {columns.map((col) => (
                <th
                  key={col.key || col.accessor}
                  className={col.sortable !== false ? 'sortable' : ''}
                  onClick={() => col.sortable !== false && handleSort(col.key || col.accessor)}
                  style={col.width ? { width: col.width } : {}}
                >
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                    {col.header}
                    {sortKey === (col.key || col.accessor) && (
                      sortDir === 'asc' ? <ChevronUp size={14} /> : <ChevronDown size={14} />
                    )}
                  </span>
                </th>
              ))}
              {actions && <th style={{ width: 100 }}>Actions</th>}
            </tr>
          </thead>
          <tbody>
            {paginatedData.length === 0 ? (
              <tr>
                <td colSpan={columns.length + (actions ? 1 : 0)}>
                  <div className="empty-state" style={{ padding: 'var(--space-10) var(--space-4)' }}>
                    {emptyIcon || <Package />}
                    <p className="empty-state-title">{emptyMessage}</p>
                  </div>
                </td>
              </tr>
            ) : (
              paginatedData.map((row, idx) => (
                <motion.tr
                  key={row.id || idx}
                  initial={{ opacity: 0, y: 4 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.15, delay: Math.min(idx * 0.02, 0.2) }}
                  onClick={() => onRowClick?.(row)}
                  style={onRowClick ? { cursor: 'pointer' } : {}}
                >
                  {columns.map((col) => (
                    <td key={col.key || col.accessor}>
                      {col.render
                        ? col.render(row)
                        : typeof col.accessor === 'function'
                        ? col.accessor(row)
                        : row[col.accessor] ?? '—'}
                    </td>
                  ))}
                  {actions && (
                    <td>
                      <div className="table-actions">{actions(row)}</div>
                    </td>
                  )}
                </motion.tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {filteredData.length > pageSize && (
        <div className="pagination">
          <button
            className="pagination-btn"
            disabled={currentPage === 0}
            onClick={() => setCurrentPage((p) => p - 1)}
          >
            <ChevronLeft size={16} />
          </button>
          {Array.from({ length: Math.min(totalPages, 5) }, (_, i) => {
            let page = i
            if (totalPages > 5) {
              const start = Math.max(0, Math.min(currentPage - 2, totalPages - 5))
              page = start + i
            }
            return (
              <button
                key={page}
                className={`pagination-btn ${currentPage === page ? 'active' : ''}`}
                onClick={() => setCurrentPage(page)}
              >
                {page + 1}
              </button>
            )
          })}
          <span className="pagination-info">
            {currentPage * pageSize + 1}–{Math.min((currentPage + 1) * pageSize, filteredData.length)} of {filteredData.length}
          </span>
          <button
            className="pagination-btn"
            disabled={currentPage >= totalPages - 1}
            onClick={() => setCurrentPage((p) => p + 1)}
          >
            <ChevronRight size={16} />
          </button>
        </div>
      )}
    </div>
  )
}
