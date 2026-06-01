import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { Plus, Edit2, Trash2, AlertTriangle } from 'lucide-react'
import api from '../services/api'
import DataTable from '../components/DataTable'
import Modal from '../components/Modal'
import ConfirmDialog from '../components/ConfirmDialog'
import { useToast } from '../context/ToastContext'

const CATEGORIES = ['SNACKS', 'BEVERAGES', 'CIGARETTES', 'BISCUITS', 'NAMKEEN', 'OTHER']

const emptyForm = {
  name: '', brand: '', category: 'SNACKS', otherCategoryDetail: '', gstPercent: '0', cessPercent: '0', isCessApplicable: false,
  primaryUnit: 'BOX', secondaryUnit: 'LADI', customSecondaryUnit: '', secondaryPerPrimary: '1',
  canSellPrimary: true, canSellSecondary: true,
  buyPriceWithoutTax: '', buyPriceWithTax: '', sellPricePrimary: '', sellPriceSecondary: '',
  lowStockAlert: '10', lowStockUnit: 'SECONDARY',
}

export default function Products() {
  const [products, setProducts] = useState([])
  const [loading, setLoading] = useState(true)
  const [activeTab, setActiveTab] = useState('ALL')
  const [showModal, setShowModal] = useState(false)
  const [editingId, setEditingId] = useState(null)
  const [form, setForm] = useState({ ...emptyForm })
  const [saving, setSaving] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState(null)
  const toast = useToast()

  useEffect(() => { loadProducts() }, [])

  const loadProducts = async () => {
    setLoading(true)
    try {
      const res = await api.get('/products?size=500')
      setProducts(res.data.data?.content || res.data.data || [])
    } catch { toast.error('Failed to load products') }
    finally { setLoading(false) }
  }

  const filtered = activeTab === 'ALL' 
    ? products 
    : activeTab.startsWith('OTHER:')
    ? products.filter(p => p.category === 'OTHER' && p.otherCategoryDetail && p.otherCategoryDetail.trim().toLowerCase() === activeTab.replace('OTHER:', '').toLowerCase())
    : products.filter(p => p.category === activeTab)

  const openCreate = () => {
    setForm({ ...emptyForm, buyPriceWithTax: '', isCessApplicable: false, otherCategoryDetail: '', customSecondaryUnit: '' })
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

  const handleGstChange = (gstValue) => {
    const gst = Number(gstValue || 0)
    const cess = Number(form.cessPercent || 0)
    const withoutTax = parseFloat(form.buyPriceWithoutTax)
    if (!isNaN(withoutTax)) {
      const withTax = (withoutTax * (1 + (gst + cess) / 100)).toFixed(2)
      setForm(f => ({ ...f, gstPercent: gstValue, buyPriceWithTax: withTax }))
    } else {
      setForm(f => ({ ...f, gstPercent: gstValue }))
    }
  }

  const handleCessChange = (cessValue) => {
    const cess = Number(cessValue || 0)
    const gst = Number(form.gstPercent || 0)
    const withoutTax = parseFloat(form.buyPriceWithoutTax)
    if (!isNaN(withoutTax)) {
      const withTax = (withoutTax * (1 + (gst + cess) / 100)).toFixed(2)
      setForm(f => ({ ...f, cessPercent: cessValue, buyPriceWithTax: withTax }))
    } else {
      setForm(f => ({ ...f, cessPercent: cessValue }))
    }
  }

  const handleCessApplicableChange = (applicable) => {
    const withoutTax = parseFloat(form.buyPriceWithoutTax)
    const gst = Number(form.gstPercent || 0)
    const newCess = applicable ? (Number(form.cessPercent) > 0 ? Number(form.cessPercent) : 12) : 0 // Default to 12% if turned on (standard for beverages), or keep existing
    
    if (!isNaN(withoutTax)) {
      const withTax = (withoutTax * (1 + (gst + newCess) / 100)).toFixed(2)
      setForm(f => ({ ...f, isCessApplicable: applicable, cessPercent: newCess, buyPriceWithTax: withTax }))
    } else {
      setForm(f => ({ ...f, isCessApplicable: applicable, cessPercent: newCess }))
    }
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
        sellPricePrimary: form.sellPricePrimary !== '' ? Number(form.sellPricePrimary) : 0,
        sellPriceSecondary: form.sellPriceSecondary !== '' ? Number(form.sellPriceSecondary) : 0,
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
      loadProducts()
    } catch (err) {
      toast.error(err.response?.data?.message || 'Save failed')
    } finally { setSaving(false) }
  }

  const handleDelete = async () => {
    try {
      await api.delete(`/products/${deleteTarget}`)
      toast.success('Product deactivated')
      setDeleteTarget(null)
      loadProducts()
    } catch { toast.error('Delete failed') }
  }

  const columns = [
    { header: 'Name', accessor: 'name', render: (row) => (
      <div>
        <div style={{ fontWeight: 'var(--font-weight-medium)' }}>{row.name}</div>
        {row.productCode && <div className="text-xs text-muted">{row.productCode}</div>}
      </div>
    )},
    { header: 'Brand', accessor: 'brand' },
    { header: 'Category', accessor: 'category', render: (row) => (
      <span className="badge badge-accent">
        {row.category === 'OTHER' && row.otherCategoryDetail ? row.otherCategoryDetail : row.category}
      </span>
    )},
    { header: 'Buy ₹', accessor: 'buyPriceWithoutTax', render: (row) => `₹${Number(row.buyPriceWithoutTax || 0).toLocaleString('en-IN')}` },
    { header: 'Sell (P)', accessor: 'sellPricePrimary', render: (row) => `₹${Number(row.sellPricePrimary || 0).toLocaleString('en-IN')}` },
    { header: 'Sell (S)', accessor: 'sellPriceSecondary', render: (row) => `₹${Number(row.sellPriceSecondary || 0).toLocaleString('en-IN')}` },
    { header: 'Stock', accessor: 'totalSecondaryUnits', render: (row) => (
      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
        <span>{row.totalSecondaryUnits ?? 0} {row.secondaryUnit}</span>
        {row.isLowStock && <AlertTriangle size={14} style={{ color: 'var(--color-danger)' }} />}
      </div>
    )},
    { header: 'GST', accessor: 'gstPercent', render: (row) => `${row.gstPercent}%` },
    { header: 'Cess', accessor: 'cessPercent', render: (row) => row.cessPercent > 0 ? `${row.cessPercent}%` : <span className="text-muted">—</span> },
  ]

  const updateField = (key, val) => setForm(f => ({ ...f, [key]: val }))

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h2 className="page-title">Products</h2>
          <p className="page-subtitle">{products.length} products total</p>
        </div>
        <div className="page-actions">
          <motion.button className="btn btn-primary" onClick={openCreate} whileTap={{ scale: 0.95 }}>
            <Plus size={18} /> Add Product
          </motion.button>
        </div>
      </div>

      {/* Category filter dropdown - responsive and overflow-safe */}
      <div style={{ display: 'flex', gap: 'var(--space-3)', alignItems: 'center', marginBottom: 'var(--space-6)', flexWrap: 'wrap' }}>
        <span style={{ fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)', fontWeight: 'var(--font-weight-semibold)' }}>
          Filter Category:
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
        data={filtered}
        loading={loading}
        searchPlaceholder="Search products by name, brand, or code..."
        emptyMessage="No products found"
        actions={(row) => (
          <>
            <button className="btn btn-ghost btn-icon btn-sm" onClick={() => openEdit(row)} title="Edit"><Edit2 size={15} /></button>
            <button className="btn btn-ghost btn-icon btn-sm" onClick={() => setDeleteTarget(row.id)} title="Delete" style={{ color: 'var(--color-danger)' }}><Trash2 size={15} /></button>
          </>
        )}
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
          <div className="form-row-4">
            <div className="form-group">
              <label className="form-label">Buy (Excl. Tax) ₹ *</label>
              <input className="form-input" type="number" min="0" step="0.01" value={form.buyPriceWithoutTax} onChange={e => handlePriceChange('without', e.target.value)} required />
            </div>
            <div className="form-group">
              <label className="form-label">Buy (Incl. Tax) ₹</label>
              <input className="form-input" type="number" min="0" step="0.01" value={form.buyPriceWithTax || ''} onChange={e => handlePriceChange('with', e.target.value)} />
            </div>
            <div className="form-group">
              <label className="form-label">Sell (Primary) ₹ *</label>
              <input className="form-input" type="number" min="0" step="0.01" value={form.sellPricePrimary} onChange={e => updateField('sellPricePrimary', e.target.value)} required />
            </div>
            <div className="form-group">
              <label className="form-label">Sell (Secondary) ₹ *</label>
              <input className="form-input" type="number" min="0" step="0.01" value={form.sellPriceSecondary} onChange={e => updateField('sellPriceSecondary', e.target.value)} required />
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
