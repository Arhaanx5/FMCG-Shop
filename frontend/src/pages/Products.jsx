import { useState, useEffect, useCallback } from 'react'
import { motion } from 'framer-motion'
import { Plus, Edit2, Trash2, AlertTriangle } from 'lucide-react'
import api from '../services/api'
import DataTable from '../components/DataTable'
import Modal from '../components/Modal'
import ConfirmDialog from '../components/ConfirmDialog'
import Pagination from '../components/Pagination'
import { useToast } from '../context/ToastContext'

const CATEGORIES = ['CHIPS', 'SNACKS', 'BEVERAGES', 'CIGARETTES', 'BISCUITS', 'NAMKEEN', 'OTHER']

const emptyForm = {
  name: '', brand: '', category: 'SNACKS', otherCategoryDetail: '', gstPercent: '0', cessPercent: '0', isCessApplicable: false,
  primaryUnit: 'BOX', secondaryUnit: 'LADI', customSecondaryUnit: '', secondaryPerPrimary: '1',
  canSellPrimary: true, canSellSecondary: true,
  buyPriceWithoutTax: '', buyPriceWithTax: '',
  sellPricePrimary: '', sellPriceSecondary: '',
  sellPricePrimaryExcl: '', sellPricePrimaryIncl: '',
  sellPriceSecondaryExcl: '', sellPriceSecondaryIncl: '',
  lowStockAlert: '10', lowStockUnit: 'SECONDARY',
}

export default function Products() {
  const [isMobile, setIsMobile] = useState(window.innerWidth < 768)

  useEffect(() => {
    const handleResize = () => setIsMobile(window.innerWidth < 768)
    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  const [products, setProducts] = useState([])
  const [loading, setLoading] = useState(true)
  const [activeTab, setActiveTab] = useState('ALL')
  const [showModal, setShowModal] = useState(false)
  const [editingId, setEditingId] = useState(null)
  const [form, setForm] = useState({ ...emptyForm })
  const [saving, setSaving] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState(null)
  // Server-side pagination
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [searchQuery, setSearchQuery] = useState('')
  const PAGE_SIZE = 25
  const toast = useToast()

  const loadProducts = useCallback(async (pg = page, search = searchQuery, category = activeTab) => {
    setLoading(true)
    try {
      const params = new URLSearchParams()
      params.set('page', pg)
      params.set('size', PAGE_SIZE)
      if (search && search.trim()) params.set('search', search.trim())
      const res = await api.get(`/products?${params.toString()}`)
      const pageData = res.data.data
      let content = pageData?.content || []
      // Apply category filter client-side (categories not many, safe to filter)
      if (category !== 'ALL') {
        if (category.startsWith('OTHER:')) {
          const customCat = category.replace('OTHER:', '').toLowerCase()
          content = content.filter(p => p.category === 'OTHER' && p.otherCategoryDetail?.trim().toLowerCase() === customCat)
        } else {
          content = content.filter(p => p.category === category)
        }
      }
      setProducts(content)
      setTotalPages(pageData?.totalPages || 0)
      setTotalElements(pageData?.totalElements || 0)
    } catch { toast.error('Failed to load products') }
    finally { setLoading(false) }
  }, [page, searchQuery, activeTab])

  useEffect(() => { loadProducts(0, searchQuery, activeTab) }, [activeTab])
  useEffect(() => { loadProducts(page, searchQuery, activeTab) }, [page])



  const openCreate = () => {
    setForm({
      ...emptyForm,
      buyPriceWithTax: '',
      isCessApplicable: false,
      otherCategoryDetail: '',
      customSecondaryUnit: '',
      sellPricePrimaryExcl: '',
      sellPricePrimaryIncl: '',
      sellPriceSecondaryExcl: '',
      sellPriceSecondaryIncl: ''
    })
    setEditingId(null)
    setShowModal(true)
  }

  const openEdit = (product) => {
    const buyPriceWithoutTax = product.buyPriceWithoutTax || ''
    const gst = product.gstPercent || 0
    const cess = product.cessPercent || 0
    const buyPriceWithTax = buyPriceWithoutTax ? (Number(buyPriceWithoutTax) * (1 + (gst + cess) / 100)).toFixed(2) : ''

    const isStandardUnit = ['LADI', 'PACK', 'BOTTLE'].includes(product.secondaryUnit)
    const secondaryUnitVal = isStandardUnit ? (product.secondaryUnit || 'LADI') : 'OTHER'
    const customSecondaryUnitVal = isStandardUnit ? '' : (product.secondaryUnit || '')

    const sellPricePrimaryIncl = product.sellPricePrimary !== undefined ? product.sellPricePrimary.toString() : ''
    const sellPricePrimaryExcl = sellPricePrimaryIncl ? (Number(sellPricePrimaryIncl) / (1 + (gst + cess) / 100)).toFixed(2) : ''

    const sellPriceSecondaryIncl = product.sellPriceSecondary !== undefined ? product.sellPriceSecondary.toString() : ''
    const sellPriceSecondaryExcl = sellPriceSecondaryIncl ? (Number(sellPriceSecondaryIncl) / (1 + (gst + cess) / 100)).toFixed(2) : ''

    setForm({
      name: product.name || '', brand: product.brand || '', category: product.category || 'SNACKS',
      otherCategoryDetail: product.otherCategoryDetail || '',
      gstPercent: product.gstPercent !== undefined ? product.gstPercent.toString() : '0',
      cessPercent: product.cessPercent !== undefined ? product.cessPercent.toString() : '0',
      primaryUnit: product.primaryUnit || 'BOX',
      secondaryUnit: secondaryUnitVal,
      customSecondaryUnit: customSecondaryUnitVal,
      secondaryPerPrimary: product.secondaryPerPrimary !== undefined ? product.secondaryPerPrimary.toString() : '1',
      canSellPrimary: product.canSellPrimary ?? true, canSellSecondary: product.canSellSecondary ?? true,
      buyPriceWithoutTax,
      buyPriceWithTax,
      sellPricePrimary: product.sellPricePrimary || '', sellPriceSecondary: product.sellPriceSecondary || '',
      sellPricePrimaryExcl,
      sellPricePrimaryIncl,
      sellPriceSecondaryExcl,
      sellPriceSecondaryIncl,
      lowStockAlert: product.lowStockAlert !== undefined ? product.lowStockAlert.toString() : '10',
      lowStockUnit: product.lowStockUnit || 'SECONDARY',
      isCessApplicable: cess > 0,
    })
    setEditingId(product.id)
    setShowModal(true)
  }

  const handlePriceChange = (type, value) => {
    const gst = Number(form.gstPercent || 0)
    const cess = Number(form.cessPercent || 0)
    const taxRate = 1 + (gst + cess) / 100
    if (type === 'without') {
      const parsed = parseFloat(value)
      if (isNaN(parsed) || !value) {
        setForm(f => ({ ...f, buyPriceWithoutTax: value, buyPriceWithTax: '' }))
      } else {
        const withTax = (parsed * taxRate).toFixed(2)
        setForm(f => ({ ...f, buyPriceWithoutTax: value, buyPriceWithTax: withTax }))
      }
    } else {
      const parsed = parseFloat(value)
      if (isNaN(parsed) || !value) {
        setForm(f => ({ ...f, buyPriceWithTax: value, buyPriceWithoutTax: '' }))
      } else {
        const withoutTax = (parsed / taxRate).toFixed(2)
        setForm(f => ({ ...f, buyPriceWithTax: value, buyPriceWithoutTax: withoutTax }))
      }
    }
  }

  const handleSellPriceChange = (unitType, fieldType, value) => {
    const gst = Number(form.gstPercent || 0)
    const cess = Number(form.cessPercent || 0)
    const taxRate = 1 + (gst + cess) / 100

    if (unitType === 'primary') {
      if (fieldType === 'excl') {
        const parsed = parseFloat(value)
        if (isNaN(parsed) || !value) {
          setForm(f => ({ ...f, sellPricePrimaryExcl: value, sellPricePrimaryIncl: '' }))
        } else {
          const incl = (parsed * taxRate).toFixed(2)
          setForm(f => ({ ...f, sellPricePrimaryExcl: value, sellPricePrimaryIncl: incl }))
        }
      } else {
        const parsed = parseFloat(value)
        if (isNaN(parsed) || !value) {
          setForm(f => ({ ...f, sellPricePrimaryIncl: value, sellPricePrimaryExcl: '' }))
        } else {
          const excl = (parsed / taxRate).toFixed(2)
          setForm(f => ({ ...f, sellPricePrimaryIncl: value, sellPricePrimaryExcl: excl }))
        }
      }
    } else {
      if (fieldType === 'excl') {
        const parsed = parseFloat(value)
        if (isNaN(parsed) || !value) {
          setForm(f => ({ ...f, sellPriceSecondaryExcl: value, sellPriceSecondaryIncl: '' }))
        } else {
          const incl = (parsed * taxRate).toFixed(2)
          setForm(f => ({ ...f, sellPriceSecondaryExcl: value, sellPriceSecondaryIncl: incl }))
        }
      } else {
        const parsed = parseFloat(value)
        if (isNaN(parsed) || !value) {
          setForm(f => ({ ...f, sellPriceSecondaryIncl: value, sellPriceSecondaryExcl: '' }))
        } else {
          const excl = (parsed / taxRate).toFixed(2)
          setForm(f => ({ ...f, sellPriceSecondaryIncl: value, sellPriceSecondaryExcl: excl }))
        }
      }
    }
  }

  const handleGstChange = (gstValue) => {
    const gst = Number(gstValue || 0)
    const cess = Number(form.cessPercent || 0)
    const taxRate = 1 + (gst + cess) / 100
    const withoutTax = parseFloat(form.buyPriceWithoutTax)
    let newBuyPriceWithTax = form.buyPriceWithTax
    if (!isNaN(withoutTax)) {
      newBuyPriceWithTax = (withoutTax * taxRate).toFixed(2)
    }

    const sellPriIncl = parseFloat(form.sellPricePrimaryIncl)
    const sellPriExcl = !isNaN(sellPriIncl) ? (sellPriIncl / taxRate).toFixed(2) : ''

    const sellSecIncl = parseFloat(form.sellPriceSecondaryIncl)
    const sellSecExcl = !isNaN(sellSecIncl) ? (sellSecIncl / taxRate).toFixed(2) : ''

    setForm(f => ({
      ...f,
      gstPercent: gstValue,
      buyPriceWithTax: newBuyPriceWithTax,
      sellPricePrimaryExcl: sellPriExcl,
      sellPriceSecondaryExcl: sellSecExcl
    }))
  }

  const handleCessChange = (cessValue) => {
    const cess = Number(cessValue || 0)
    const gst = Number(form.gstPercent || 0)
    const taxRate = 1 + (gst + cess) / 100
    const withoutTax = parseFloat(form.buyPriceWithoutTax)
    let newBuyPriceWithTax = form.buyPriceWithTax
    if (!isNaN(withoutTax)) {
      newBuyPriceWithTax = (withoutTax * taxRate).toFixed(2)
    }

    const sellPriIncl = parseFloat(form.sellPricePrimaryIncl)
    const sellPriExcl = !isNaN(sellPriIncl) ? (sellPriIncl / taxRate).toFixed(2) : ''

    const sellSecIncl = parseFloat(form.sellPriceSecondaryIncl)
    const sellSecExcl = !isNaN(sellSecIncl) ? (sellSecIncl / taxRate).toFixed(2) : ''

    setForm(f => ({
      ...f,
      cessPercent: cessValue,
      buyPriceWithTax: newBuyPriceWithTax,
      sellPricePrimaryExcl: sellPriExcl,
      sellPriceSecondaryExcl: sellSecExcl
    }))
  }

  const handleCessApplicableChange = (applicable) => {
    const gst = Number(form.gstPercent || 0)
    const newCess = applicable ? (Number(form.cessPercent) > 0 ? Number(form.cessPercent) : 12) : 0
    const taxRate = 1 + (gst + newCess) / 100
    const withoutTax = parseFloat(form.buyPriceWithoutTax)
    let newBuyPriceWithTax = form.buyPriceWithTax
    if (!isNaN(withoutTax)) {
      newBuyPriceWithTax = (withoutTax * taxRate).toFixed(2)
    }

    const sellPriIncl = parseFloat(form.sellPricePrimaryIncl)
    const sellPriExcl = !isNaN(sellPriIncl) ? (sellPriIncl / taxRate).toFixed(2) : ''

    const sellSecIncl = parseFloat(form.sellPriceSecondaryIncl)
    const sellSecExcl = !isNaN(sellSecIncl) ? (sellSecIncl / taxRate).toFixed(2) : ''

    setForm(f => ({
      ...f,
      isCessApplicable: applicable,
      cessPercent: newCess,
      buyPriceWithTax: newBuyPriceWithTax,
      sellPricePrimaryExcl: sellPriExcl,
      sellPriceSecondaryExcl: sellSecExcl
    }))
  }

  const handleSave = async (e) => {
    e.preventDefault()
    setSaving(true)
    try {
      const payload = {
        ...form,
        gstPercent: Number(form.gstPercent || 0),
        cessPercent: form.isCessApplicable ? Number(form.cessPercent || 0) : 0,
        secondaryPerPrimary: Number(form.secondaryPerPrimary || 1),
        buyPriceWithoutTax: form.buyPriceWithoutTax !== '' ? Number(form.buyPriceWithoutTax) : null,
        buyPriceWithTax: form.buyPriceWithTax !== '' ? Number(form.buyPriceWithTax) : null,
        sellPricePrimary: form.sellPricePrimaryIncl !== '' ? Number(form.sellPricePrimaryIncl) : 0,
        sellPriceSecondary: form.sellPriceSecondaryIncl !== '' ? Number(form.sellPriceSecondaryIncl) : 0,
        lowStockAlert: form.lowStockAlert !== '' ? Number(form.lowStockAlert) : 0,
        secondaryUnit: form.secondaryUnit === 'OTHER' ? (form.customSecondaryUnit || 'OTHER').toUpperCase().trim() : form.secondaryUnit,
      }
      if (editingId) {
        await api.put(`/products/${editingId}`, payload)
        toast.success('Product updated!')
      } else {
        await api.post('/products', payload)
        toast.success('Product created!')
      }
      setShowModal(false)
      loadProducts(0, searchQuery, activeTab)
    } catch (err) {
      toast.error(err.response?.data?.message || 'Save failed')
    } finally { setSaving(false) }
  }

  const handleDelete = async () => {
    try {
      await api.delete(`/products/${deleteTarget}`)
      toast.success('Product deactivated')
      setDeleteTarget(null)
      loadProducts(0, searchQuery, activeTab)
    } catch { toast.error('Delete failed') }
  }

  const columns = [
    {
      header: 'Name', accessor: 'name', render: (row) => (
        <div>
          <div style={{ fontWeight: 'var(--font-weight-medium)' }}>{row.name}</div>
          {isMobile && row.brand && (
            <div className="text-xs text-muted" style={{ marginTop: '2px' }}>
              {row.brand}
            </div>
          )}
        </div>
      )
    },
    { header: 'Brand', accessor: 'brand' },
    {
      header: 'Category', accessor: 'category', render: (row) => (
        <span className="badge badge-accent">
          {row.category === 'OTHER' && row.otherCategoryDetail ? row.otherCategoryDetail : row.category}
        </span>
      )
    },
    { header: 'Buy ₹', accessor: 'buyPriceWithoutTax', render: (row) => `₹${Number(row.buyPriceWithoutTax || 0).toLocaleString('en-IN')}` },
    { header: 'Sell (P)', accessor: 'sellPricePrimary', render: (row) => `₹${Number(row.sellPricePrimary || 0).toLocaleString('en-IN')}` },
    { header: 'Sell (S)', accessor: 'sellPriceSecondary', render: (row) => `₹${Number(row.sellPriceSecondary || 0).toLocaleString('en-IN')}` },
    {
      header: 'Stock', accessor: 'totalSecondaryUnits', render: (row) => {
        const totalSec = row.totalSecondaryUnits ?? 0
        const ratio = row.secondaryPerPrimary || 1
        const priUnit = row.primaryUnit || 'BOX'
        const secUnit = row.secondaryUnit || 'LADI'
        
        let displayStock = ''
        if (ratio > 1) {
          const pri = Math.floor(totalSec / ratio)
          const sec = totalSec % ratio
          if (pri > 0 && sec > 0) {
            displayStock = `${pri} ${priUnit} + ${sec} ${secUnit}`
          } else if (pri > 0) {
            displayStock = `${pri} ${priUnit}`
          } else {
            displayStock = `${sec} ${secUnit}`
          }
        } else {
          displayStock = `${totalSec} ${secUnit}`
        }

        return (
          <div 
            className="stock-tooltip-container" 
            style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', cursor: ratio > 1 ? 'help' : 'default' }}
          >
            <span>{displayStock}</span>
            {row.isLowStock && <AlertTriangle size={14} style={{ color: 'var(--color-danger)' }} />}
            {ratio > 1 && (
              <div className="stock-tooltip-box">
                <div style={{
                  fontWeight: '600',
                  fontSize: '11px',
                  color: 'rgba(255, 255, 255, 0.7)',
                  marginBottom: '8px',
                  borderBottom: '1px solid rgba(255, 255, 255, 0.1)',
                  paddingBottom: '4px',
                  textAlign: 'left'
                }}>
                  Stock Breakdown
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', textAlign: 'left' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '11px', whiteSpace: 'nowrap' }}>
                    <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: 'var(--color-info)' }} />
                    <span style={{ color: 'rgba(255, 255, 255, 0.7)', minWidth: '90px' }}>Primary:</span>
                    <span style={{ fontWeight: '750', color: '#fff' }}>{Math.floor(totalSec / ratio)} {priUnit}</span>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '11px', whiteSpace: 'nowrap' }}>
                    <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: 'var(--color-warning)' }} />
                    <span style={{ color: 'rgba(255, 255, 255, 0.7)', minWidth: '90px' }}>Secondary:</span>
                    <span style={{ fontWeight: '750', color: '#fff' }}>{totalSec % ratio} {secUnit}</span>
                  </div>
                  <div style={{ 
                    display: 'flex', 
                    alignItems: 'center', 
                    gap: '8px', 
                    fontSize: '11px', 
                    whiteSpace: 'nowrap',
                    borderTop: '1px dashed rgba(255, 255, 255, 0.1)',
                    paddingTop: '6px',
                    marginTop: '2px'
                  }}>
                    <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: 'var(--color-success)' }} />
                    <span style={{ color: 'rgba(255, 255, 255, 0.7)', minWidth: '90px' }}>Total Stock:</span>
                    <span style={{ fontWeight: '800', color: '#fff' }}>{totalSec} {secUnit}</span>
                  </div>
                </div>
                <div className="stock-tooltip-arrow" />
              </div>
            )}
          </div>
        )
      }
    },
    { header: 'GST', accessor: 'gstPercent', render: (row) => `${row.gstPercent}%` },
    { header: 'Cess', accessor: 'cessPercent', render: (row) => row.cessPercent > 0 ? `${row.cessPercent}%` : <span className="text-muted">—</span> },
  ].filter(col => {
    if (isMobile) {
      return !['brand', 'category', 'buyPriceWithoutTax', 'sellPricePrimary', 'gstPercent', 'cessPercent'].includes(col.accessor)
    }
    return true
  })

  const updateField = (key, val) => setForm(f => ({ ...f, [key]: val }))

  return (
    <div className="page-container">
      <style>{`
        .stock-tooltip-container {
          position: relative;
        }
        .stock-tooltip-container .stock-tooltip-box {
          position: absolute;
          bottom: 125%;
          left: 50%;
          transform: translateX(-50%) translateY(10px);
          background: rgba(15, 23, 42, 0.95);
          backdrop-filter: blur(8px);
          border: 1px solid rgba(255, 255, 255, 0.1);
          border-radius: var(--radius-md);
          padding: 8px 12px;
          color: #fff;
          font-size: 11px;
          font-weight: 600;
          white-space: nowrap;
          opacity: 0;
          visibility: hidden;
          transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
          z-index: 100;
          box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.3), 0 8px 10px -6px rgba(0, 0, 0, 0.3);
          pointer-events: none;
        }
        .stock-tooltip-container:hover .stock-tooltip-box {
          opacity: 1;
          visibility: visible;
          transform: translateX(-50%) translateY(0);
        }
        .stock-tooltip-arrow {
          position: absolute;
          top: 100%;
          left: 50%;
          transform: translateX(-50%);
          border-width: 6px;
          border-style: solid;
          border-color: rgba(15, 23, 42, 0.95) transparent transparent transparent;
        }
        /* First row tooltip positioning (show downwards to prevent table header cut-off) */
        tbody tr:first-child .stock-tooltip-box {
          bottom: auto !important;
          top: 125% !important;
          transform: translateX(-50%) translateY(-10px) !important;
        }
        tbody tr:first-child .stock-tooltip-container:hover .stock-tooltip-box {
          opacity: 1 !important;
          visibility: visible !important;
          transform: translateX(-50%) translateY(0) !important;
          bottom: auto !important;
          top: 125% !important;
        }
        tbody tr:first-child .stock-tooltip-arrow {
          top: auto !important;
          bottom: 100% !important;
          border-color: transparent transparent rgba(15, 23, 42, 0.95) transparent !important;
        }
      `}</style>
      <div className="page-header">
        <div>
          <h2 className="page-title">Products</h2>
          <p className="page-subtitle">{totalElements} products total</p>
        </div>
        <div className="page-actions">
          <motion.button className="btn btn-primary" onClick={openCreate} whileTap={{ scale: 0.95 }}>
            <Plus size={18} /> Add Product
          </motion.button>
        </div>
      </div>

      {/* Search + Category filter */}
      <div style={{ display: 'flex', gap: 'var(--space-3)', alignItems: 'center', marginBottom: 'var(--space-6)', flexWrap: 'wrap' }}>
        <input
          className="form-input"
          placeholder="Search products by name or brand..."
          value={searchQuery}
          onChange={e => {
            const q = e.target.value
            setSearchQuery(q)
            setPage(0)
            loadProducts(0, q, activeTab)
          }}
          style={{ maxWidth: '280px', height: '38px' }}
        />
        <span style={{ fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)', fontWeight: 'var(--font-weight-semibold)' }}>
          Category:
        </span>
        <select
          className="form-select"
          value={activeTab}
          onChange={(e) => setActiveTab(e.target.value)}
          style={{ width: '220px', height: '38px', padding: '0 32px 0 12px', fontSize: 'var(--font-size-sm)', borderRadius: 'var(--radius-md)', flexShrink: 0, maxWidth: '100%' }}
        >
          <option value="ALL">All Categories</option>
          {CATEGORIES.filter(c => c !== 'OTHER').map(c => (
            <option key={c} value={c}>{c.charAt(0) + c.slice(1).toLowerCase()}</option>
          ))}
          {/* Dynamically extract all unique custom categories entered in OTHER */}
          {Array.from(new Set(products
            .filter(p => p.category === 'OTHER' && p.otherCategoryDetail)
            .map(p => p.otherCategoryDetail.trim())
          )).map(customCat => (
            <option key={customCat} value={`OTHER:${customCat}`}>Other - {customCat}</option>
          ))}
          <option value="OTHER">Other (General)</option>
        </select>
      </div>

      <DataTable
        columns={columns}
        data={products}
        loading={loading}
        searchable={false}
        emptyMessage="No products found"
        actions={(row) => (
          <>
            <button className="btn btn-ghost btn-icon btn-sm" onClick={() => openEdit(row)} title="Edit"><Edit2 size={15} /></button>
            <button className="btn btn-ghost btn-icon btn-sm" onClick={() => setDeleteTarget(row.id)} title="Delete" style={{ color: 'var(--color-danger)' }}><Trash2 size={15} /></button>
          </>
        )}
      />
      <Pagination
        page={page}
        totalPages={totalPages}
        totalElements={totalElements}
        pageSize={PAGE_SIZE}
        onPageChange={(p) => setPage(p)}
      />

      {/* Create/Edit Modal */}
      <Modal isOpen={showModal} onClose={() => setShowModal(false)} title={editingId ? 'Edit Product' : 'Add Product'} wide>
        <form onSubmit={handleSave} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          <div className="form-row">
            <div className="form-group">
              <label className="form-label">Product Name *</label>
              <input className="form-input" value={form.name} onChange={e => updateField('name', e.target.value)} required minLength={2} placeholder="e.g. Lays Classic" />
            </div>
            <div className="form-group">
              <label className="form-label">Brand</label>
              <input className="form-input" value={form.brand} onChange={e => updateField('brand', e.target.value)} placeholder="e.g. PepsiCo" />
            </div>
          </div>
          <div className="form-row-4">
            <div className="form-group">
              <label className="form-label">Category *</label>
              <select className="form-select" value={form.category} onChange={e => updateField('category', e.target.value)}>
                {CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
              </select>
              {form.category === 'OTHER' && (
                <input
                  className="form-input"
                  style={{ marginTop: 'var(--space-2)' }}
                  value={form.otherCategoryDetail}
                  onChange={e => updateField('otherCategoryDetail', e.target.value)}
                  placeholder="Specify Category (e.g. Soaps)"
                  required
                />
              )}
            </div>
            <div className="form-group">
              <label className="form-label">GST % *</label>
              <input className="form-input" type="number" min="0" max="40" step="0.01" value={form.gstPercent} onChange={e => handleGstChange(e.target.value)} required />
            </div>
            <div className="form-group">
              <label className="form-label">&nbsp;</label>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
                <label style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', cursor: 'pointer', userSelect: 'none', height: '38px', margin: 0, fontSize: 'var(--font-size-sm)' }}>
                  <input
                    type="checkbox"
                    checked={form.isCessApplicable || false}
                    onChange={e => handleCessApplicableChange(e.target.checked)}
                    style={{ width: 16, height: 16, cursor: 'pointer', accentColor: 'var(--color-primary)' }}
                  />
                  Cess Applicable
                </label>
                {form.isCessApplicable && (
                  <input
                    className="form-input"
                    type="number"
                    min="0"
                    max="100"
                    step="0.01"
                    value={form.cessPercent}
                    onChange={e => handleCessChange(e.target.value)}
                    placeholder="Cess %"
                    required
                  />
                )}
              </div>
            </div>
            <div className="form-group">
              <label className="form-label">Low Stock Alert</label>
              <input className="form-input" type="number" min="0" value={form.lowStockAlert} onChange={e => updateField('lowStockAlert', e.target.value)} />
            </div>
          </div>
          <div className="form-row-3">
            <div className="form-group">
              <label className="form-label">Primary Unit *</label>
              <select className="form-select" value={form.primaryUnit} onChange={e => updateField('primaryUnit', e.target.value)} required>
                <option value="BOX">BOX</option>
                <option value="CRATE">CRATE</option>
              </select>
            </div>
            <div className="form-group">
              <label className="form-label">Secondary Unit *</label>
              <select className="form-select" value={form.secondaryUnit} onChange={e => updateField('secondaryUnit', e.target.value)} required>
                <option value="LADI">LADI</option>
                <option value="PACK">PACKET / PACK</option>
                <option value="BOTTLE">BOTTLE</option>
                <option value="OTHER">OTHER (Custom)</option>
              </select>
              {form.secondaryUnit === 'OTHER' && (
                <input
                  className="form-input"
                  style={{ marginTop: 'var(--space-2)' }}
                  value={form.customSecondaryUnit || ''}
                  onChange={e => updateField('customSecondaryUnit', e.target.value.toUpperCase())}
                  placeholder="Specify Unit (e.g. PCS, TIN)"
                  required
                />
              )}
            </div>
            <div className="form-group">
              <label className="form-label">Secondary per Primary *</label>
              <input className="form-input" type="number" min="1" value={form.secondaryPerPrimary} onChange={e => updateField('secondaryPerPrimary', e.target.value)} required />
            </div>
          </div>
          <div className="form-row">
            <div className="form-group">
              <label className="form-label">Buy (Excl. Tax) ₹ *</label>
              <input className="form-input" type="number" min="0" step="0.01" value={form.buyPriceWithoutTax} onChange={e => handlePriceChange('without', e.target.value)} required />
            </div>
            <div className="form-group">
              <label className="form-label">Buy (Incl. Tax) ₹</label>
              <input className="form-input" type="number" min="0" step="0.01" value={form.buyPriceWithTax || ''} onChange={e => handlePriceChange('with', e.target.value)} />
            </div>
          </div>
          <div className="form-row">
            <div className="form-group">
              <label className="form-label">Sell (Primary - {form.primaryUnit}) (Excl. Tax) ₹</label>
              <input className="form-input" type="number" min="0" step="0.01" value={form.sellPricePrimaryExcl} onChange={e => handleSellPriceChange('primary', 'excl', e.target.value)} />
            </div>
            <div className="form-group">
              <label className="form-label">Sell (Primary - {form.primaryUnit}) (Incl. Tax) ₹ *</label>
              <input className="form-input" type="number" min="0" step="0.01" value={form.sellPricePrimaryIncl} onChange={e => handleSellPriceChange('primary', 'incl', e.target.value)} required />
            </div>
          </div>
          <div className="form-row">
            <div className="form-group">
              <label className="form-label">Sell (Secondary - {form.secondaryUnit === 'OTHER' ? (form.customSecondaryUnit || 'OTHER') : form.secondaryUnit}) (Excl. Tax) ₹</label>
              <input className="form-input" type="number" min="0" step="0.01" value={form.sellPriceSecondaryExcl} onChange={e => handleSellPriceChange('secondary', 'excl', e.target.value)} />
            </div>
            <div className="form-group">
              <label className="form-label">Sell (Secondary - {form.secondaryUnit === 'OTHER' ? (form.customSecondaryUnit || 'OTHER') : form.secondaryUnit}) (Incl. Tax) ₹ *</label>
              <input className="form-input" type="number" min="0" step="0.01" value={form.sellPriceSecondaryIncl} onChange={e => handleSellPriceChange('secondary', 'incl', e.target.value)} required />
            </div>
          </div>
          <div className="form-actions">
            <button type="button" className="btn btn-secondary" onClick={() => setShowModal(false)}>Cancel</button>
            <motion.button type="submit" className="btn btn-primary" disabled={saving} whileTap={{ scale: 0.95 }}>
              {saving ? 'Saving...' : editingId ? 'Update Product' : 'Create Product'}
            </motion.button>
          </div>
        </form>
      </Modal>

      <ConfirmDialog isOpen={!!deleteTarget} onClose={() => setDeleteTarget(null)} onConfirm={handleDelete}
        title="Deactivate Product" message="This will deactivate the product. It won't appear in billing." confirmLabel="Deactivate" />
    </div>
  )
}
