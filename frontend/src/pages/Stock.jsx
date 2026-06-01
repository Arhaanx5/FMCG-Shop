import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { Plus, AlertTriangle, Clock, Package, Edit2, History } from 'lucide-react'
import api from '../services/api'
import DataTable from '../components/DataTable'
import Modal from '../components/Modal'
import SearchSelect from '../components/SearchSelect'
import { useToast } from '../context/ToastContext'
import { useAuth } from '../context/AuthContext'

const emptyForm = { productId: '', batchNumber: '', primaryReceived: '', extraSecondaryReceived: '', buyPriceWithoutTax: '', buyPriceWithTax: '', expiryDate: '', supplierName: '' }

export default function Stock() {
  const { isAdmin, isManager } = useAuth()
  const [stocks, setStocks] = useState([])
  const [products, setProducts] = useState([])
  const [expiring, setExpiring] = useState([])
  const [loading, setLoading] = useState(true)
  const [activeTab, setActiveTab] = useState('overview')
  const [showModal, setShowModal] = useState(false)
  const [form, setForm] = useState({ ...emptyForm })
  const [saving, setSaving] = useState(false)
  // Batches for a selected product
  const [batchProduct, setBatchProduct] = useState(null)
  const [batches, setBatches] = useState([])
  const [batchLoading, setBatchLoading] = useState(false)

  // Adjust stock states
  const [adjustingBatch, setAdjustingBatch] = useState(null)
  const [adjustPrimary, setAdjustPrimary] = useState('')
  const [adjustBuyPrice, setAdjustBuyPrice] = useState('')
  const [adjustBuyPriceWithTax, setAdjustBuyPriceWithTax] = useState('')
  const [adjustReason, setAdjustReason] = useState('')
  const [adjusting, setAdjusting] = useState(false)
  const [validationErrors, setValidationErrors] = useState({})
  const [auditLogs, setAuditLogs] = useState([])
  const [logsLoading, setLogsLoading] = useState(false)

  const clearError = (field) => {
    setValidationErrors(prev => ({ ...prev, [field]: null }))
  }

  const toast = useToast()

  useEffect(() => { loadAll() }, [])

  const loadAuditLogs = async () => {
    setLogsLoading(true)
    try {
      const res = await api.get('/stock/adjustments')
      setAuditLogs(res.data.data || [])
    } catch {
      toast.error('Failed to load stock audit logs')
    } finally {
      setLogsLoading(false)
    }
  }

  useEffect(() => {
    if (activeTab === 'audit' && isAdmin) {
      loadAuditLogs()
    }
  }, [activeTab])

  const handleAdjustPriceChange = (type, value) => {
    if (!adjustingBatch) return
    const gst = Number(adjustingBatch.gstPercent || 0)
    if (type === 'without') {
      const parsed = parseFloat(value)
      if (isNaN(parsed) || !value) {
        setAdjustBuyPrice(value)
        setAdjustBuyPriceWithTax('')
      } else {
        const withTax = (parsed * (1 + gst / 100)).toFixed(2)
        setAdjustBuyPrice(value)
        setAdjustBuyPriceWithTax(withTax)
      }
    } else {
      const parsed = parseFloat(value)
      if (isNaN(parsed) || !value) {
        setAdjustBuyPriceWithTax(value)
        setAdjustBuyPrice('')
      } else {
        const withoutTax = (parsed / (1 + gst / 100)).toFixed(2)
        setAdjustBuyPriceWithTax(value)
        setAdjustBuyPrice(withoutTax)
      }
    }
  }

  const handleAdjustStockSubmit = async (e) => {
    e.preventDefault()
    if (adjustPrimary === null || adjustPrimary === undefined || adjustPrimary === '') {
      toast.error('Specify new remaining quantity in primary units')
      return
    }
    if (!adjustReason.trim()) { toast.error('Specify reason for correction'); return }
    setAdjusting(true)
    try {
      const ratio = adjustingBatch.secondaryPerPrimary || 1
      const computedSecondary = Number(adjustPrimary) * ratio
      await api.put(`/stock/batches/${adjustingBatch.id}/adjust`, {
        newSecondaryRemaining: computedSecondary,
        newBuyPriceWithoutTax: adjustBuyPrice ? Number(adjustBuyPrice) : null,
        reason: adjustReason
      })
      toast.success('Stock corrected successfully!')
      setAdjustingBatch(null)
      setAdjustPrimary('')
      setAdjustBuyPrice('')
      setAdjustBuyPriceWithTax('')
      setAdjustReason('')
      setValidationErrors({})
      if (batchProduct) {
        loadBatches(batchProduct.id, batchProduct.name)
      }
      loadAll()
    } catch (err) {
      if (err.response?.status === 400 && err.response?.data?.data && typeof err.response.data.data === 'object') {
        setValidationErrors(err.response.data.data)
        toast.error('Validation failed. Please correct the highlighted fields.')
      } else {
        toast.error(err.response?.data?.message || 'Failed to correct stock')
      }
    } finally {
      setAdjusting(false)
    }
  }

  const loadAll = async () => {
    setLoading(true)
    try {
      const [sRes, pRes, eRes] = await Promise.all([
        api.get('/stock'),
        api.get('/products?size=500'),
        api.get('/stock/expiring-soon'),
      ])
      setStocks(sRes.data.data || [])
      setProducts(pRes.data.data?.content || pRes.data.data || [])
      setExpiring(eRes.data.data || [])
    } catch { toast.error('Failed to load stock data') }
    finally { setLoading(false) }
  }

  const loadBatches = async (productId, productName) => {
    setBatchProduct({ id: productId, name: productName })
    setBatchLoading(true)
    try {
      const res = await api.get(`/stock/batches/${productId}`)
      setBatches(res.data.data || [])
    } catch { toast.error('Failed to load batches') }
    finally { setBatchLoading(false) }
  }

  const handleReceive = async (e) => {
    e.preventDefault()
    if (!form.productId) { toast.error('Select a product'); return }
    setSaving(true)
    try {
      const payload = {
        ...form,
        primaryReceived: Number(form.primaryReceived || 0),
        extraSecondaryReceived: Number(form.extraSecondaryReceived || 0),
        buyPriceWithoutTax: Number(form.buyPriceWithoutTax || 0),
        buyPriceWithTax: form.buyPriceWithTax ? Number(form.buyPriceWithTax) : null
      }
      await api.post('/stock/receive', payload)
      toast.success('Stock received successfully!')
      setShowModal(false)
      setForm({ ...emptyForm })
      loadAll()
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to receive stock')
    } finally { setSaving(false) }
  }

  const updateField = (key, val) => setForm(f => ({ ...f, [key]: val }))

  const handlePriceChange = (type, value) => {
    const selectedProduct = products.find(p => p.id === form.productId)
    const gst = selectedProduct ? Number(selectedProduct.gstPercent || 0) : 0
    if (type === 'without') {
      const parsed = parseFloat(value)
      if (isNaN(parsed) || !value) {
        setForm(f => ({ ...f, buyPriceWithoutTax: value, buyPriceWithTax: '' }))
      } else {
        const withTax = (parsed * (1 + gst / 100)).toFixed(2)
        setForm(f => ({ ...f, buyPriceWithoutTax: value, buyPriceWithTax: withTax }))
      }
    } else {
      const parsed = parseFloat(value)
      if (isNaN(parsed) || !value) {
        setForm(f => ({ ...f, buyPriceWithTax: value, buyPriceWithoutTax: '' }))
      } else {
        const withoutTax = (parsed / (1 + gst / 100)).toFixed(2)
        setForm(f => ({ ...f, buyPriceWithTax: value, buyPriceWithoutTax: withoutTax }))
      }
    }
  }

  const handleProductChange = (productId) => {
    const selectedProduct = products.find(p => p.id === productId)
    const gst = selectedProduct ? Number(selectedProduct.gstPercent || 0) : 0
    const withoutTax = parseFloat(form.buyPriceWithoutTax)
    if (!isNaN(withoutTax)) {
      const withTax = (withoutTax * (1 + gst / 100)).toFixed(2)
      setForm(f => ({ ...f, productId, buyPriceWithTax: withTax }))
    } else {
      setForm(f => ({ ...f, productId }))
    }
  }

  const stockColumns = [
    { header: 'Product', accessor: 'productName', render: (row) => (
      <div>
        <div className="font-medium">{row.productName}</div>
        {row.brand && <div className="text-xs text-muted">{row.brand}</div>}
      </div>
    )},
    { header: 'Category', accessor: 'category', render: (row) => <span className="badge badge-accent">{row.category}</span> },
    { header: 'Primary', key: 'primary', render: (row) => `${row.totalPrimaryUnits || 0} ${row.primaryUnit || ''}` },
    { header: 'Secondary', key: 'secondary', render: (row) => `${row.totalSecondaryUnits || 0} ${row.secondaryUnit || ''}` },
    { header: 'Open Box', key: 'openBox', render: (row) => row.hasOpenPrimary
      ? <span className="badge badge-info">{row.openPrimaryRemaining} left</span>
      : <span className="text-muted">—</span>
    },
    { header: 'Status', key: 'stockStatus', render: (row) =>
      row.isLowStock
        ? <span className="badge badge-danger"><AlertTriangle size={12} /> Low Stock</span>
        : <span className="badge badge-success">OK</span>
    },
  ]

  const expiringColumns = [
    { header: 'Product', accessor: 'productName', render: (row) => (
      <div>
        <div className="font-medium">{row.productName}</div>
        {row.brand && <div className="text-xs text-muted">{row.brand}</div>}
      </div>
    )},
    { header: 'Batch #', accessor: 'batchNumber' },
    { header: 'Remaining', accessor: 'secondaryRemaining', render: (row) => `${row.secondaryRemaining || 0} ${row.secondaryUnit || ''}` },
    { header: 'Supplier', accessor: 'supplierName' },
    { header: 'Expiry', accessor: 'expiryDate', render: (row) => {
      const d = row.expiryDate ? new Date(row.expiryDate) : null
      const daysLeft = d ? Math.ceil((d - new Date()) / 86400000) : null
      return d ? (
        <div>
          <div>{d.toLocaleDateString('en-IN')}</div>
          <div className={`text-xs ${daysLeft <= 3 ? 'text-danger' : 'text-warning'}`}>
            {daysLeft <= 0 ? 'EXPIRED' : `${daysLeft} days left`}
          </div>
        </div>
      ) : '—'
    }},
  ]

  const batchColumns = [
    { header: 'Batch #', accessor: 'batchNumber' },
    { header: 'Received', key: 'received', render: (row) => `${row.primaryReceived || 0} ${row.primaryUnit || ''} + ${row.secondaryReceived || 0} ${row.secondaryUnit || ''}` },
    { header: 'Remaining', accessor: 'secondaryRemaining', render: (row) => `${row.secondaryRemaining || 0} ${row.secondaryUnit || ''}` },
    { header: 'Buy ₹', accessor: 'buyPriceWithTax', render: (row) => `₹${Number(row.buyPriceWithTax || 0).toLocaleString('en-IN')}` },
    { header: 'Supplier', accessor: 'supplierName' },
    { header: 'Expiry', accessor: 'expiryDate', render: (row) => row.expiryDate ? new Date(row.expiryDate).toLocaleDateString('en-IN') : '—' },
    { header: 'Status', key: 'batchStatus', render: (row) => (
      <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
        {row.exhausted && <span className="badge badge-neutral">Exhausted</span>}
        {row.expiringSoon && <span className="badge badge-warning">Expiring</span>}
        {!row.exhausted && !row.expiringSoon && <span className="badge badge-success">Active</span>}
      </div>
    )},
  ]

  const auditColumns = [
    { header: 'Date', accessor: 'timestamp', render: (row) => row.timestamp ? new Date(row.timestamp).toLocaleString('en-IN') : '—' },
    { header: 'Product', accessor: 'productName', render: (row) => <span className="font-semibold">{row.productName}</span> },
    { header: 'Batch #', accessor: 'batchNumber' },
    { header: 'Old Quantity', accessor: 'oldSecondaryRemaining', render: (row) => `${row.oldSecondaryRemaining} units` },
    { header: 'New Quantity', accessor: 'newSecondaryRemaining', render: (row) => `${row.newSecondaryRemaining} units` },
    { header: 'Changed By', accessor: 'adjustedBy', render: (row) => <span className="badge badge-info">{row.adjustedBy}</span> },
    { header: 'Reason', accessor: 'reason' },
  ]

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h2 className="page-title">Stock Management</h2>
          <p className="page-subtitle">{stocks.length} products in stock</p>
        </div>
        <div className="page-actions">
          <motion.button className="btn btn-primary" onClick={() => setShowModal(true)} whileTap={{ scale: 0.95 }}>
            <Plus size={18} /> Receive Stock
          </motion.button>
        </div>
      </div>

      <div className="tabs">
        <button className={`tab ${activeTab === 'overview' ? 'active' : ''}`} onClick={() => setActiveTab('overview')}>
          <Package size={16} style={{ marginRight: 6, verticalAlign: 'middle' }} /> Stock Overview
        </button>
        <button className={`tab ${activeTab === 'expiring' ? 'active' : ''}`} onClick={() => setActiveTab('expiring')}>
          <Clock size={16} style={{ marginRight: 6, verticalAlign: 'middle' }} /> Expiring Soon ({expiring.length})
        </button>
        {isAdmin && (
          <button className={`tab ${activeTab === 'audit' ? 'active' : ''}`} onClick={() => setActiveTab('audit')}>
            <History size={16} style={{ marginRight: 6, verticalAlign: 'middle' }} /> Stock Audit Logs
          </button>
        )}
      </div>

      {activeTab === 'overview' && (
        <DataTable
          columns={stockColumns}
          data={stocks}
          loading={loading}
          searchPlaceholder="Search stock..."
          emptyMessage="No stock data"
          onRowClick={(row) => row.productId && loadBatches(row.productId, row.productName)}
        />
      )}

      {activeTab === 'expiring' && (
        <DataTable
          columns={expiringColumns}
          data={expiring}
          loading={loading}
          searchable={false}
          emptyMessage="No expiring batches — all good!"
        />
      )}

      {activeTab === 'audit' && isAdmin && (
        <DataTable
          columns={auditColumns}
          data={auditLogs}
          loading={logsLoading}
          searchPlaceholder="Search audit logs..."
          emptyMessage="No adjustment logs found"
        />
      )}

      {/* Batches Modal */}
      <Modal isOpen={!!batchProduct} onClose={() => setBatchProduct(null)} title={`Batches — ${batchProduct?.name || ''}`} xl>
        <DataTable
          columns={batchColumns}
          data={batches}
          loading={batchLoading}
          searchable={false}
          emptyMessage="No batches found"
          pageSize={8}
          actions={(row) => (
            <>
              {(isAdmin || isManager) && (
                <button
                  className="btn btn-ghost btn-icon btn-sm"
                  onClick={() => {
                    setAdjustingBatch(row)
                    const ratio = row.secondaryPerPrimary || 1
                    setAdjustPrimary(Math.floor(row.secondaryRemaining / ratio).toString())
                    setAdjustBuyPrice(row.buyPriceWithoutTax || '')
                    setAdjustBuyPriceWithTax(row.buyPriceWithTax || '')
                    setValidationErrors({})
                  }}
                  title="Correct / Adjust Stock"
                  style={{ color: 'var(--color-accent)' }}
                >
                  <Edit2 size={15} />
                </button>
              )}
            </>
          )}
        />
      </Modal>

      {/* Correct / Adjust Stock Modal */}
      <Modal isOpen={!!adjustingBatch} onClose={() => setAdjustingBatch(null)} title={`Correct Stock Batch — ${adjustingBatch?.batchNumber || ''}`}>
        {adjustingBatch && (
          <form onSubmit={handleAdjustStockSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
            <div className="form-group">
              <label className="form-label">Current Stock</label>
              <input
                className="form-input"
                value={`${Math.floor(adjustingBatch.secondaryRemaining / (adjustingBatch.secondaryPerPrimary || 1))} ${adjustingBatch.primaryUnit || 'Box'} ${adjustingBatch.secondaryRemaining % (adjustingBatch.secondaryPerPrimary || 1) > 0 ? `+ ${adjustingBatch.secondaryRemaining % (adjustingBatch.secondaryPerPrimary || 1)} ${adjustingBatch.secondaryUnit || 'Packs'}` : ''} (${adjustingBatch.secondaryRemaining} Total ${adjustingBatch.secondaryUnit || 'Packs'})`}
                disabled
                style={{ opacity: 0.7 }}
              />
            </div>
            <div className="form-group">
              <label className="form-label">Corrected Quantity ({adjustingBatch.primaryUnit || 'Box'}) *</label>
              <input
                className="form-input"
                type="number"
                min="0"
                value={adjustPrimary}
                onChange={e => {
                  setAdjustPrimary(e.target.value)
                  clearError('newSecondaryRemaining')
                }}
                style={validationErrors.newSecondaryRemaining ? { borderColor: 'var(--color-danger)' } : {}}
                required
              />
              {validationErrors.newSecondaryRemaining && (
                <span className="text-xs" style={{ color: 'var(--color-danger)', marginTop: '4px', display: 'block' }}>
                  {validationErrors.newSecondaryRemaining}
                </span>
              )}
              <span className="text-xs text-muted" style={{ marginTop: 'var(--space-1)', display: 'block' }}>
                Will be updated to {adjustPrimary * (adjustingBatch.secondaryPerPrimary || 1)} total {adjustingBatch.secondaryUnit || 'packs'}.
              </span>
            </div>
            <div className="form-row">
              <div className="form-group">
                <label className="form-label">Corrected Buy Price (no tax) per {adjustingBatch.primaryUnit || 'Box'} ₹</label>
                <input
                  className="form-input"
                  type="number"
                  min="0"
                  step="0.01"
                  value={adjustBuyPrice}
                  onChange={e => {
                    handleAdjustPriceChange('without', e.target.value)
                    clearError('newBuyPriceWithoutTax')
                  }}
                  style={validationErrors.newBuyPriceWithoutTax ? { borderColor: 'var(--color-danger)' } : {}}
                  placeholder="Price without tax..."
                />
                {validationErrors.newBuyPriceWithoutTax && (
                  <span className="text-xs" style={{ color: 'var(--color-danger)', marginTop: '4px', display: 'block' }}>
                    {validationErrors.newBuyPriceWithoutTax}
                  </span>
                )}
              </div>
              <div className="form-group">
                <label className="form-label">Corrected Buy Price (with tax) per {adjustingBatch.primaryUnit || 'Box'} ₹</label>
                <input
                  className="form-input"
                  type="number"
                  min="0"
                  step="0.01"
                  value={adjustBuyPriceWithTax}
                  onChange={e => {
                    handleAdjustPriceChange('with', e.target.value)
                    clearError('newBuyPriceWithoutTax')
                  }}
                  style={validationErrors.newBuyPriceWithoutTax ? { borderColor: 'var(--color-danger)' } : {}}
                  placeholder="Price with tax..."
                />
              </div>
            </div>
            <div className="form-group">
              <label className="form-label">Reason for Correction *</label>
              <textarea
                className="form-textarea"
                value={adjustReason}
                onChange={e => {
                  setAdjustReason(e.target.value)
                  clearError('reason')
                }}
                style={validationErrors.reason ? { borderColor: 'var(--color-danger)' } : {}}
                placeholder="Specify why stock is being modified (e.g. Typo in entry, damage, sample return)..."
                required
                rows={3}
              />
              {validationErrors.reason && (
                <span className="text-xs" style={{ color: 'var(--color-danger)', marginTop: '4px', display: 'block' }}>
                  {validationErrors.reason}
                </span>
              )}
            </div>
            <div className="form-actions">
              <button type="button" className="btn btn-secondary" onClick={() => setAdjustingBatch(null)}>Cancel</button>
              <button type="submit" className="btn btn-primary" disabled={adjusting}>
                {adjusting ? 'Correcting...' : 'Submit Correction'}
              </button>
            </div>
          </form>
        )}
      </Modal>

      {/* Receive Stock Modal */}
      <Modal isOpen={showModal} onClose={() => setShowModal(false)} title="Receive Stock" wide>
        <form onSubmit={handleReceive} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          <div className="form-group">
            <label className="form-label">Product *</label>
            <SearchSelect
              options={products.filter(p => p.active !== false)}
              value={form.productId}
              onChange={handleProductChange}
              labelKey="name" valueKey="id"
              placeholder="Select product..."
            />
          </div>
          <div className="form-row">
            <div className="form-group">
              <label className="form-label">Batch Number *</label>
              <input className="form-input" value={form.batchNumber} onChange={e => updateField('batchNumber', e.target.value)} required minLength={2} placeholder="e.g. B-2024-001" />
            </div>
            <div className="form-group">
              <label className="form-label">Supplier *</label>
              <input className="form-input" value={form.supplierName} onChange={e => updateField('supplierName', e.target.value)} required placeholder="Supplier name" />
            </div>
          </div>
          <div className="form-row-4">
            <div className="form-group">
              <label className="form-label">Primary Units</label>
              <input 
                className="form-input" 
                type="number" 
                min="0" 
                value={form.primaryReceived} 
                onChange={e => updateField('primaryReceived', e.target.value)} 
              />
            </div>
            <div className="form-group">
              <label className="form-label">Extra Secondary</label>
              <input 
                className="form-input" 
                type="number" 
                min="0" 
                value={form.extraSecondaryReceived} 
                onChange={e => updateField('extraSecondaryReceived', e.target.value)} 
              />
            </div>
            <div className="form-group">
              <label className="form-label">Buy Price (no tax) ₹ *</label>
              <input className="form-input" type="number" min="0" step="0.01" value={form.buyPriceWithoutTax} onChange={e => handlePriceChange('without', e.target.value)} required />
            </div>
            <div className="form-group">
              <label className="form-label">Buy Price (with tax) ₹</label>
              <input className="form-input" type="number" min="0" step="0.01" value={form.buyPriceWithTax || ''} onChange={e => handlePriceChange('with', e.target.value)} />
            </div>
          </div>
          <div className="form-group">
            <label className="form-label">Expiry Date *</label>
            <input className="form-input" type="date" value={form.expiryDate} onChange={e => updateField('expiryDate', e.target.value)} required />
          </div>
          <div className="form-actions">
            <button type="button" className="btn btn-secondary" onClick={() => setShowModal(false)}>Cancel</button>
            <motion.button type="submit" className="btn btn-primary" disabled={saving} whileTap={{ scale: 0.95 }}>
              {saving ? 'Receiving...' : 'Receive Stock'}
            </motion.button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
