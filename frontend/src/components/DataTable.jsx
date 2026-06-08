import { useState, useMemo, Children, useRef, useEffect, Fragment } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { ChevronUp, ChevronDown, ChevronLeft, ChevronRight, Search, Package, MoreHorizontal, X } from 'lucide-react'

const flattenChildren = (children) => {
  const result = []
  Children.forEach(children, (child) => {
    if (!child) return
    if (child.type === Fragment || child.type?.toString() === 'Symbol(react.fragment)') {
      result.push(...flattenChildren(child.props.children))
    } else {
      result.push(child)
    }
  })
  return result
}

function RowSpeedDial({ actions }) {
  const [isOpen, setIsOpen] = useState(false)
  const containerRef = useRef(null)

  useEffect(() => {
    if (!isOpen) return
    const handleOutsideClick = (e) => {
      if (containerRef.current && !containerRef.current.contains(e.target)) {
        setIsOpen(false)
      }
    }
    document.addEventListener('mousedown', handleOutsideClick)
    return () => document.removeEventListener('mousedown', handleOutsideClick)
  }, [isOpen])

  const buttons = flattenChildren(actions).filter(Boolean)

  if (buttons.length === 0) return null
  if (buttons.length === 1) {
    return (
      <div className="inline-flex justify-end w-full">
        <div className="flex gap-2 justify-end">{buttons[0]}</div>
      </div>
    )
  }

  return (
    <div
      ref={containerRef}
      className="relative inline-flex items-center justify-end w-full"
      onMouseEnter={() => setIsOpen(true)}
      onMouseLeave={() => setIsOpen(false)}
    >
      <AnimatePresence>
        {isOpen && (
          <motion.div
            initial={{ opacity: 0, x: 15, width: 0 }}
            animate={{ opacity: 1, x: 0, width: 'auto' }}
            exit={{ opacity: 0, x: 15, width: 0 }}
            transition={{ duration: 0.2, ease: 'easeOut' }}
            className="flex items-center gap-1.5 mr-2 overflow-hidden whitespace-nowrap"
          >
            {buttons}
          </motion.div>
        )}
      </AnimatePresence>

      <motion.button
        className={`flex items-center justify-center w-8 h-8 rounded-full border transition-all duration-150 ${
          isOpen
            ? 'bg-slate-100 dark:bg-slate-800 border-slate-200 dark:border-slate-700 text-amber-500'
            : 'border-transparent text-slate-500 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-slate-800/60 hover:text-amber-500 dark:hover:text-amber-400'
        } flex-shrink-0`}
        onClick={(e) => {
          e.stopPropagation()
          setIsOpen(!isOpen)
        }}
        whileTap={{ scale: 0.9 }}
        title={isOpen ? 'Close actions' : 'Show actions'}
      >
        {isOpen ? <X size={15} /> : <MoreHorizontal size={15} />}
      </motion.button>
    </div>
  )
}

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
  stickyColumnCount = 2,
}) {
  const [search, setSearch] = useState('')
  const [sortKey, setSortKey] = useState(null)
  const [sortDir, setSortDir] = useState('asc')
  const [currentPage, setCurrentPage] = useState(0)

  const stickyOffsets = useMemo(() => {
    return columns.map((col) => {
      return {
        width: col.width,
        left: 0,
        isSticky: false
      }
    })
  }, [columns])

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
      <div className="flex flex-col items-center justify-center p-12 border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 rounded-theme shadow-sm">
        <div className="w-10 h-10 border-3 border-t-amber-500 border-slate-200 dark:border-slate-700 rounded-full animate-spin" />
        <p className="text-slate-500 dark:text-slate-400 mt-4 text-sm font-medium">Loading data...</p>
      </div>
    )
  }

  return (
    <div>
      {searchable && (
        <div className="relative w-full max-w-sm mb-4">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 pointer-events-none" />
          <input
            className="w-full pl-10 pr-4 py-2 text-sm bg-slate-100 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-slate-900 dark:text-slate-100 rounded-theme-sm focus:outline-none focus:border-amber-500 focus:ring-1 focus:ring-amber-500 transition-colors duration-150"
            placeholder={searchPlaceholder}
            value={search}
            onChange={(e) => handleSearch(e.target.value)}
          />
        </div>
      )}

      <div className="overflow-x-auto border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 rounded-theme shadow-sm">
        <table className="w-full border-collapse text-left text-sm text-slate-500 dark:text-slate-400">
          <thead>
            <tr className="bg-slate-50 dark:bg-slate-900 border-b border-slate-200 dark:border-slate-700">
              {columns.map((col, colIdx) => {
                const sticky = stickyOffsets[colIdx]
                return (
                  <th
                    key={col.key || col.accessor}
                    className={`px-4 py-3 text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider select-none ${
                      col.sortable !== false ? 'cursor-pointer hover:text-amber-500 transition-colors' : ''
                    } ${
                      sticky.isSticky ? 'sticky z-20 bg-slate-50 dark:bg-slate-900 border-r border-slate-200 dark:border-slate-700' : ''
                    }`}
                    onClick={() => col.sortable !== false && handleSort(col.key || col.accessor)}
                    style={{
                      ...(col.width ? { width: col.width } : {}),
                      ...(sticky.isSticky ? { left: sticky.left, minWidth: sticky.width, maxWidth: sticky.width } : {})
                    }}
                  >
                    <span className="inline-flex items-center gap-1">
                      {col.header}
                      {sortKey === (col.key || col.accessor) && (
                        sortDir === 'asc' ? <ChevronUp size={14} className="text-amber-500" /> : <ChevronDown size={14} className="text-amber-500" />
                      )}
                    </span>
                  </th>
                )
              })}
              {actions && <th className="px-4 py-3 text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider text-right" style={{ width: 120 }}>Actions</th>}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-200 dark:divide-slate-700">
            {paginatedData.length === 0 ? (
              <tr>
                <td colSpan={columns.length + (actions ? 1 : 0)} className="px-4 py-12 text-center">
                  <div className="flex flex-col items-center justify-center text-slate-400 dark:text-slate-500">
                    {emptyIcon || <Package size={40} className="stroke-[1.5] mb-2 opacity-60" />}
                    <p className="font-medium text-slate-600 dark:text-slate-400">{emptyMessage}</p>
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
                  className={`group border-b border-slate-200 dark:border-slate-700 text-slate-900 dark:text-slate-100 hover:bg-slate-50 dark:hover:bg-slate-700/30 transition-colors duration-150 ${
                    onRowClick ? 'cursor-pointer' : ''
                  }`}
                >
                  {columns.map((col, colIdx) => {
                    const sticky = stickyOffsets[colIdx]
                    return (
                      <td
                        key={col.key || col.accessor}
                        className={`px-4 py-3 align-middle whitespace-nowrap ${
                          sticky.isSticky ? 'sticky z-10 bg-white dark:bg-slate-800 group-hover:bg-slate-50 dark:group-hover:bg-slate-700/30 border-r border-slate-200 dark:border-slate-700 transition-colors duration-150' : ''
                        }`}
                        style={sticky.isSticky ? { left: sticky.left, minWidth: sticky.width, maxWidth: sticky.width, overflow: 'hidden', textOverflow: 'ellipsis' } : {}}
                      >
                        {col.render
                          ? col.render(row)
                          : typeof col.accessor === 'function'
                          ? col.accessor(row)
                          : row[col.accessor] ?? '—'}
                      </td>
                    )
                  })}
                  {actions && (
                    <td className="px-4 py-2 align-middle" onClick={(e) => e.stopPropagation()}>
                      <RowSpeedDial actions={actions(row)} />
                    </td>
                  )}
                </motion.tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {filteredData.length > pageSize && (
        <div className="flex items-center justify-center gap-1.5 mt-6">
          <button
            className="inline-flex items-center justify-center w-8 h-8 text-sm font-medium border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-700 dark:text-slate-300 hover:border-amber-500 hover:text-amber-500 rounded-theme-sm transition-colors duration-150 disabled:opacity-30 disabled:cursor-not-allowed"
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
                className={`inline-flex items-center justify-center w-8 h-8 text-sm font-medium border ${
                  currentPage === page
                    ? 'bg-accent border-accent text-inverse font-semibold hover:text-inverse'
                    : 'border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-700 dark:text-slate-300 hover:border-amber-500 hover:text-amber-500'
                } rounded-theme-sm transition-colors duration-150`}
                onClick={() => setCurrentPage(page)}
              >
                {page + 1}
              </button>
            )
          })}
          <span className="text-xs text-slate-500 dark:text-slate-400 mx-2 select-none">
            {currentPage * pageSize + 1}–{Math.min((currentPage + 1) * pageSize, filteredData.length)} of {filteredData.length}
          </span>
          <button
            className="inline-flex items-center justify-center w-8 h-8 text-sm font-medium border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-700 dark:text-slate-300 hover:border-amber-500 hover:text-amber-500 rounded-theme-sm transition-colors duration-150 disabled:opacity-30 disabled:cursor-not-allowed"
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
